// The harness's stand-in Sorta: API v1 on sample ideas held in memory
// (S07, sections 4 to 6). The apps answer the same calls in Swift
// (ios/Sorta/Addons) and Kotlin (android/.../addons); the conformance suite
// checks that all three give the same bytes. This file runs in its own
// trusted runtime in the harness, never inside an add-on's.
(function () {
  'use strict';

  const CALLS = ['ideas.list', 'ideas.get', 'ideas.search', 'ideas.capture', 'ideas.tag', 'ideas.untag',
    'ideas.link', 'ideas.unlink', 'ideas.file', 'folders.list', 'commands.on', 'cards.on',
    'settings.get', 'settings.on', 'storage.get', 'storage.set', 'storage.remove', 'net.fetch', 'console.log'];
  const READS = ['ideas.list', 'ideas.get', 'ideas.search', 'folders.list'];
  const WRITES = ['ideas.capture', 'ideas.tag', 'ideas.untag', 'ideas.link', 'ideas.unlink', 'ideas.file'];
  const FOLDER_GRANT = 'ideas.read.folder:';
  // while an add-on loads, before its first call, only these answer
  const LOAD_CALLS = ['commands.on', 'cards.on', 'settings.on', 'console.log'];
  const LIMITS = { writes: 100, captures: 20, storage: 262144, net: 1048576, netMs: 10000, redirects: 5, log: 200 };
  const SPACE = ' \t\n\r\u000b\u000c';

  // ---- small shared rules: the Swift and Kotlin ports follow these ----

  function less(a, b) { return a < b ? -1 : a > b ? 1 : 0; }   // UTF-16 order
  function trim(s) {
    let i = 0, j = s.length;
    while (i < j && SPACE.indexOf(s[i]) >= 0) i++;
    while (j > i && SPACE.indexOf(s[j - 1]) >= 0) j--;
    return s.slice(i, j);
  }
  function words(s) {
    const out = [];
    let cur = '';
    for (const ch of s) {
      if (SPACE.indexOf(ch) >= 0) { if (cur) out.push(cur); cur = ''; } else cur += ch;
    }
    if (cur) out.push(cur);
    return out;
  }
  // UTF-8 bytes; a lone surrogate counts as the 3 bytes of U+FFFD
  function utf8(s) {
    try {
      return unescape(encodeURIComponent(s)).length;
    } catch (e) {
      let n = 0;
      for (const ch of s) {
        const c = ch.codePointAt(0);
        n += c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
      }
      return n;
    }
  }
  function isObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
  function isInt(v) { return typeof v === 'number' && Number.isInteger(v) && Math.abs(v) <= 9007199254740991; }
  function iso(ms) { return new Date(ms).toISOString(); }
  function canon(v) {
    if (v === undefined) return 'null';
    if (v === null || typeof v !== 'object') return JSON.stringify(v);
    if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']';
    return '{' + Object.keys(v).sort(less).filter((k) => v[k] !== undefined)
      .map((k) => JSON.stringify(k) + ':' + canon(v[k])).join(',') + '}';
  }
  class Refusal { constructor(code, message) { this.code = code; this.message = message; } }
  function refuse(code, message) { throw new Refusal(code, message); }

  // ---- permissions (the shared table: addons/conformance/permissions.json) ----

  function canRead(grants) {
    return grants.includes('ideas.read') || grants.some((g) => g.startsWith(FOLDER_GRANT));
  }
  function allows(grants, call, host, netOn) {
    if (READS.includes(call)) return canRead(grants);
    if (WRITES.includes(call)) return grants.includes('ideas.write');
    if (call === 'commands.on') return grants.includes('commands');
    if (call === 'cards.on') return grants.includes('cards');
    if (call === 'net.fetch') return netOn && typeof host === 'string' && grants.includes('net:' + host);
    if (call.startsWith('ai.')) return false;
    return CALLS.includes(call);
  }
  function has(grants, name, netOn) {
    if (CALLS.includes(name)) {
      if (name === 'net.fetch') return netOn && grants.some((g) => g.startsWith('net:'));
      return allows(grants, name, null, netOn);
    }
    if (name === 'net') return netOn && grants.some((g) => g.startsWith('net:'));
    if (name.startsWith('net:')) return netOn && grants.includes(name);
    if (name === 'ideas.read.folder') return grants.some((g) => g.startsWith(FOLDER_GRANT));
    if (['ideas.read', 'ideas.write', 'commands', 'cards', 'board.palette'].includes(name)) return grants.includes(name);
    return false;
  }

  // ---- the snapshot, as the add-on may read it ----

  function view(snap, grants) {
    const all = grants.includes('ideas.read');
    const folders = new Set();
    if (all) {
      for (const f of snap.folders) folders.add(f.id);
    } else {
      const roots = grants.filter((g) => g.startsWith(FOLDER_GRANT)).map((g) => g.slice(FOLDER_GRANT.length));
      const known = new Set(snap.folders.map((f) => f.id));
      for (const r of roots) if (known.has(r)) folders.add(r);
      let grew = true;
      while (grew) {
        grew = false;
        for (const f of snap.folders) {
          if (!folders.has(f.id) && f.parent !== null && folders.has(f.parent)) { folders.add(f.id); grew = true; }
        }
      }
    }
    const ideas = snap.ideas.filter((i) => all || (i.folder !== null && folders.has(i.folder)));
    const ids = new Set(ideas.map((i) => i.id));
    const links = new Map();
    for (const i of ideas) links.set(i.id, new Set());
    for (const i of ideas) {
      for (const l of i.links) {
        if (ids.has(l) && l !== i.id) { links.get(i.id).add(l); links.get(l).add(i.id); }
      }
    }
    return { folders, ideas, ids, links };
  }

  function ideaJSON(v, i) {
    return {
      id: i.id, title: i.title, text: i.text, kind: i.kind,
      folder: i.folder !== null && v.folders.has(i.folder) ? i.folder : null,
      tags: i.tags.slice(), links: [...(v.links.get(i.id) || [])].sort(less),
      createdAt: iso(i.createdAt), updatedAt: iso(i.updatedAt),
    };
  }

  // ---- one call's state ----

  let call = null;   // set by begin()

  function target(id) {
    if (typeof id !== 'string') refuse('api.args');
    if (!call.view.ids.has(id) && !call.captured.has(id)) refuse('api.notFound');
    return id;
  }
  function tagList(tags) {
    if (!Array.isArray(tags) || tags.length === 0) refuse('api.args');
    return tags.map((t) => {
      if (typeof t !== 'string' || trim(t) === '') refuse('api.args');
      return trim(t);
    });
  }
  function write(op) {
    if (call.poisoned) refuse(call.poisoned);
    if (op.op === 'capture') {
      if (call.captures >= LIMITS.captures) { call.poisoned = 'limit.captures'; refuse(call.poisoned); }
      call.captures++;
    } else {
      if (call.writes >= LIMITS.writes) { call.poisoned = 'limit.writes'; refuse(call.poisoned); }
      call.writes++;
    }
    call.batch.push(op);
  }

  const handlers = {
    'ideas.list'([o]) {
      if (!isObject(o)) refuse('api.args');
      const hasFolder = Object.prototype.hasOwnProperty.call(o, 'folder');
      if (hasFolder && o.folder !== null && typeof o.folder !== 'string') refuse('api.args');
      if (o.kind !== undefined && typeof o.kind !== 'string') refuse('api.args');
      const limit = o.limit === undefined ? 50 : o.limit;
      if (!isInt(limit) || limit < 1 || limit > 200) refuse('api.args');
      let from = 0;
      if (o.cursor !== undefined && o.cursor !== null) {
        if (typeof o.cursor !== 'string' || !/^[0-9]{1,9}$/.test(o.cursor)) refuse('api.args');
        from = Number(o.cursor);
      }
      const v = call.view;
      const found = v.ideas.filter((i) => (!hasFolder || i.folder === o.folder) && (o.kind === undefined || i.kind === o.kind))
        .sort((a, b) => a.createdAt - b.createdAt || less(a.id, b.id));
      if (from > found.length) refuse('api.args');
      const page = found.slice(from, from + limit);
      return { ideas: page.map((i) => ideaJSON(v, i)), cursor: from + limit < found.length ? String(from + limit) : null };
    },
    'ideas.get'([id]) {
      if (typeof id !== 'string') refuse('api.args');
      const i = call.view.ideas.find((x) => x.id === id);
      return i ? ideaJSON(call.view, i) : null;
    },
    'ideas.search'([text, o]) {
      if (typeof text !== 'string' || !isObject(o)) refuse('api.args');
      const limit = o.limit === undefined ? 20 : o.limit;
      if (!isInt(limit) || limit < 1 || limit > 50) refuse('api.args');
      const want = words(text.toLowerCase());
      const v = call.view;
      return v.ideas.filter((i) => {
        const hay = [i.title, i.text].concat(i.tags).join(' ').toLowerCase();
        return want.every((w) => hay.includes(w));
      }).sort((a, b) => b.updatedAt - a.updatedAt || less(a.id, b.id)).slice(0, limit).map((i) => ideaJSON(v, i));
    },
    'folders.list'() {
      const v = call.view;
      return call.snapshot.folders.filter((f) => v.folders.has(f.id))
        .sort((a, b) => less(a.name, b.name) || less(a.id, b.id))
        .map((f) => ({ id: f.id, name: f.name, parent: f.parent !== null && v.folders.has(f.parent) ? f.parent : null, words: f.words.slice() }));
    },
    'ideas.capture'([o]) {
      if (!isObject(o) || typeof o.text !== 'string' || trim(o.text) === '') refuse('api.args');
      if (o.title !== undefined && o.title !== null && typeof o.title !== 'string') refuse('api.args');
      let title, body;
      if (typeof o.title === 'string' && trim(o.title) !== '') {
        title = trim(o.title);
        body = trim(o.text);
      } else {
        const t = trim(o.text);
        const at = t.indexOf('\n');
        title = at < 0 ? t : trim(t.slice(0, at));
        body = at < 0 ? '' : trim(t.slice(at + 1));
      }
      if (call.poisoned) refuse(call.poisoned);
      if (call.captures >= LIMITS.captures) { call.poisoned = 'limit.captures'; refuse(call.poisoned); }
      const id = call.nextId();
      write({ op: 'capture', id, title, body });
      call.captured.add(id);
      return { id };
    },
    'ideas.tag'([id, tags]) { const t = target(id); const l = tagList(tags); write({ op: 'tag', id: t, tags: l }); },
    'ideas.untag'([id, tags]) { const t = target(id); const l = tagList(tags); write({ op: 'untag', id: t, tags: l }); },
    'ideas.link'([a, b]) {
      const x = target(a), y = target(b);
      if (x === y) refuse('api.args');
      write({ op: 'link', a: x, b: y });
    },
    'ideas.unlink'([a, b]) {
      const x = target(a), y = target(b);
      if (x === y) refuse('api.args');
      write({ op: 'unlink', a: x, b: y });
    },
    'ideas.file'([id, folder]) {
      const t = target(id);
      if (folder !== null && typeof folder !== 'string') refuse('api.args');
      if (folder !== null && !call.view.folders.has(folder)) refuse('api.notFound');
      write({ op: 'file', id: t, folder });
    },
    'commands.on'([id]) {
      if (typeof id !== 'string' || !call.commands.includes(id)) refuse('api.args');
    },
    'cards.on'() {},
    'settings.get'() { return { raw: call.settings }; },
    'settings.on'() {},
    'storage.get'([key]) {
      if (typeof key !== 'string' || key.length < 1 || key.length > 100) refuse('api.args');
      return Object.prototype.hasOwnProperty.call(call.storage, key) ? { raw: call.storage[key] } : undefined;
    },
    'storage.set'([key, text]) {
      if (typeof key !== 'string' || key.length < 1 || key.length > 100 || typeof text !== 'string') refuse('api.args');
      let total = 0;
      for (const k of Object.keys(call.storage)) if (k !== key) total += utf8(k) + utf8(call.storage[k]);
      if (total + utf8(key) + utf8(text) > LIMITS.storage) refuse('limit.storage');
      call.storage[key] = text;
    },
    'storage.remove'([key]) {
      if (typeof key !== 'string' || key.length < 1 || key.length > 100) refuse('api.args');
      delete call.storage[key];
    },
    'console.log'([line]) {
      call.log.push(typeof line === 'string' ? line : String(line));
      if (call.log.length > LIMITS.log) call.log.shift();
    },
    'net.fetch'([url, o]) { return fetch(url, o); },
  };

  // ---- net, through the recording stand-in network ----

  const URL_RE = /^([A-Za-z][A-Za-z0-9+.-]*):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/;
  function parseUrl(s) {
    const m = URL_RE.exec(s);
    if (!m || m[2].indexOf('@') >= 0 || m[2] === '') return null;
    let host = m[2].toLowerCase();
    if (host.endsWith(':443')) host = host.slice(0, -4);
    return { scheme: m[1].toLowerCase(), host, path: m[3] === '' ? '/' : m[3], query: m[4] || '' };
  }
  const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'];

  function fetch(url, o) {
    if (!call.netOn) refuse('net.off');
    if (typeof url !== 'string' || !isObject(o)) refuse('api.args');
    let u = parseUrl(url);
    if (!u) refuse('api.args');
    if (u.scheme !== 'https') refuse('net.https');
    // a net refusal names the host it refused
    if (!call.grants.includes('net:' + u.host)) refuse('permission', 'net:' + u.host);
    let method = o.method === undefined ? 'GET' : o.method;
    if (typeof method !== 'string' || !METHODS.includes(method.toUpperCase())) refuse('api.args');
    method = method.toUpperCase();
    const headers = {};
    if (o.headers !== undefined) {
      if (!isObject(o.headers)) refuse('api.args');
      for (const k of Object.keys(o.headers)) {
        const name = k.toLowerCase();
        if (typeof o.headers[k] !== 'string' || name === 'host') refuse('api.args');
        // a cookie is never sent
        if (name !== 'cookie') headers[name] = o.headers[k];
      }
    }
    let body = o.body === undefined || o.body === null ? '' : o.body;
    if (typeof body !== 'string') refuse('api.args');
    if (utf8(body) > LIMITS.net) refuse('net.size');
    // ask each time: the request waits for the person's answer
    if (call.ask && call.ask.shift() !== 'yes') refuse('net.declined');
    let spent = 0;
    for (let hop = 0; ; hop++) {
      const reply = call.network(method, u, headers, body);
      spent += reply.delayMs || 0;
      if (spent > LIMITS.netMs) refuse('net.time');
      const size = utf8(reply.body);
      call.activity.push({ bytes: utf8(body) + size, host: u.host, path: u.path, status: reply.status });
      if (size > LIMITS.net) refuse('net.size');
      const location = reply.headers.location;
      if ([301, 302, 303, 307, 308].includes(reply.status) && typeof location === 'string') {
        if (hop >= LIMITS.redirects) refuse('net.redirect');
        const next = location.startsWith('/') ? parseUrl('https://' + u.host + location) : parseUrl(location);
        if (!next || next.scheme !== 'https' || !call.grants.includes('net:' + next.host)) refuse('net.redirect');
        if (reply.status === 303) { method = 'GET'; body = ''; }
        u = next;
        continue;
      }
      // nor is one passed back
      const shown = {};
      for (const k of Object.keys(reply.headers)) {
        if (k.toLowerCase() !== 'set-cookie' && k.toLowerCase() !== 'cookie') shown[k] = reply.headers[k];
      }
      return { status: reply.status, headers: shown, body: reply.body };
    }
  }

  function standInNetwork(routes, recorded) {
    return function (method, u, headers, body) {
      recorded.push({ method, url: u.scheme + '://' + u.host + u.path + u.query, headers, body });
      const r = routes.find((x) => x.method === method && x.url === 'https://' + u.host + u.path);
      if (!r) return { status: 404, headers: {}, body: '', delayMs: 0 };
      let text = r.body || '';
      if (r.bodyRepeat) text = r.bodyRepeat.text.repeat(r.bodyRepeat.count);
      if (r.echo) text = body;
      return { status: r.status, headers: Object.assign({}, r.headers || {}), body: text, delayMs: r.delayMs || 0 };
    };
  }

  // ---- the batch, applied as one change ----

  function apply(store, batch, now) {
    const byId = new Map(store.ideas.map((i) => [i.id, i]));
    const folders = new Set(store.folders.map((f) => f.id));
    const before = new Map();
    const added = [];
    const skipped = new Set();
    function touch(i) { if (!before.has(i.id) && !added.includes(i.id)) before.set(i.id, JSON.stringify(i)); }
    function changed(i) {}
    for (const op of batch) {
      if (op.op === 'capture') {
        const idea = { id: op.id, title: op.title, text: op.body, kind: 'idea', folder: null, tags: [], links: [], createdAt: now, updatedAt: now };
        store.ideas.push(idea);
        byId.set(idea.id, idea);
        added.push(idea.id);
        continue;
      }
      if (op.op === 'link' || op.op === 'unlink') {
        const a = byId.get(op.a), b = byId.get(op.b);
        if (!a || !b) { if (!a) skipped.add(op.a); if (!b) skipped.add(op.b); continue; }
        if (op.op === 'link') {
          if (!a.links.includes(b.id) && !b.links.includes(a.id)) { touch(a); a.links.push(b.id); changed(a); }
        } else {
          if (a.links.includes(b.id)) { touch(a); a.links = a.links.filter((x) => x !== b.id); changed(a); }
          if (b.links.includes(a.id)) { touch(b); b.links = b.links.filter((x) => x !== a.id); changed(b); }
        }
        continue;
      }
      const i = byId.get(op.id);
      if (!i) { skipped.add(op.id); continue; }
      if (op.op === 'tag') {
        const more = op.tags.filter((t, k) => !i.tags.includes(t) && op.tags.indexOf(t) === k);
        if (more.length) { touch(i); i.tags = i.tags.concat(more); changed(i); }
      } else if (op.op === 'untag') {
        if (i.tags.some((t) => op.tags.includes(t))) { touch(i); i.tags = i.tags.filter((t) => !op.tags.includes(t)); changed(i); }
      } else if (op.op === 'file') {
        if (op.folder !== null && !folders.has(op.folder)) { skipped.add(op.folder); continue; }
        if (i.folder !== op.folder) { touch(i); i.folder = op.folder; changed(i); }
      }
    }
    // an idea counts as changed, and gets a new updatedAt, only if it ends up different
    const changedIds = [...before.keys()].filter((id) => JSON.stringify(byId.get(id)) !== before.get(id));
    for (const id of changedIds) byId.get(id).updatedAt = now;
    return { added: added.slice().sort(less), changed: changedIds.sort(less), skipped: [...skipped].sort(less) };
  }

  // ---- the harness's entry points ----

  function readStore(text) {
    const s = JSON.parse(text);
    return {
      folders: s.folders.map((f) => ({ id: f.id, name: f.name, parent: f.parent, words: f.words || [] })),
      ideas: s.ideas.map((i) => ({ id: i.id, title: i.title, text: i.text, kind: i.kind, folder: i.folder,
        tags: i.tags || [], links: i.links || [], createdAt: Date.parse(i.createdAt), updatedAt: Date.parse(i.updatedAt) })),
    };
  }
  function copy(store) { return JSON.parse(JSON.stringify(store)); }

  let store = null;
  let settings = {};
  let routes = [];
  let recorded = [];
  let clock = '2026-11-01T09:00:00.000Z';

  return {
    // the sample ideas, the stand-in network's routes, and the clock
    setup(storeText, routesText, now) {
      store = readStore(storeText);
      routes = routesText ? JSON.parse(routesText).routes : [];
      clock = now;
      return '';
    },
    // a case: {grants, commands, netOff, settings, changeAfterSnapshot, goneAfterSnapshot, now}
    begin(caseText) {
      const c = Object.assign({ now: clock }, JSON.parse(caseText));
      const work = copy(store);
      const snapshot = copy(work);
      for (const ch of c.changeAfterSnapshot || []) {
        const i = work.ideas.find((x) => x.id === ch.id);
        if (i) { i.title = ch.title; i.updatedAt = Date.parse(c.now); }
      }
      for (const id of c.goneAfterSnapshot || []) {
        work.ideas = work.ideas.filter((x) => x.id !== id);
        for (const x of work.ideas) x.links = x.links.filter((l) => l !== id);
      }
      recorded = [];
      let n = 0;
      call = {
        phase: 'load', store: work, snapshot, grants: c.grants || [], commands: c.commands || [],
        netOn: !c.netOff, ask: c.ask ? c.ask.slice() : null, settings: canon(c.settings || {}), storage: {}, log: [], activity: [],
        batch: [], writes: 0, captures: 0, poisoned: null, captured: new Set(),
        now: Date.parse(c.now), input: c.input, card: c.card, cards: c.cards,
        nextId() { n++; return '00000000-0000-4000-8000-' + String(n).padStart(12, '0'); },
        network: standInNetwork(routes, recorded),
      };
      call.view = view(snapshot, call.grants);
      return '';
    },
    // one API call from the add-on: the reply text the host gives back
    handle(name, argsText) {
      try {
        const args = JSON.parse(argsText);
        if (name === 'has') return JSON.stringify(typeof args[0] === 'string' && has(call.grants, args[0], call.netOn));
        const h = handlers[name];
        if (!h) refuse('api.unknown');
        if (call.phase === 'load' && !LOAD_CALLS.includes(name)) refuse('api.load');
        // net.fetch checks its grant itself, after the URL (fetch, above)
        if (name !== 'net.fetch' && !allows(call.grants, name, null, call.netOn)) refuse('permission');
        // only a command's writes are applied; a card view or settings handler only reads
        if (WRITES.includes(name) && call.phase !== 'command') refuse('permission');
        const out = h(args);
        if (out === undefined) return '';
        if (out !== null && out.raw !== undefined) return out.raw;
        return canon(out);
      } catch (e) {
        if (e instanceof Refusal) return '!' + e.code + (e.message === undefined ? '' : ' ' + e.message);
        throw e;
      }
    },
    // the call starts: command, card, cards or settings
    run(kind) { call.phase = kind; return ''; },
    // after the call: the case's lines, the batch applied when the call was ok
    // (no envelope: the add-on only loaded)
    finish(envelopeText) {
      let env = envelopeText;
      if (env === '') return call.log.map((l) => 'log ' + l).join('\n');
      if (call.poisoned) env = '{"code":"' + call.poisoned + '","message":"' + call.poisoned + '","ok":false}';
      const lines = [];
      for (const l of call.log) lines.push('log ' + l);
      for (const a of call.activity) lines.push('activity ' + canon(a));
      lines.push('result ' + env);
      if (JSON.parse(env).ok === true) {
        const done = apply(call.store, call.batch, call.now);
        lines.push('applied ' + canon(done));
        const full = view(call.store, ['ideas.read']);
        for (const id of done.added.concat(done.changed).sort(less)) {
          lines.push('after ' + canon(ideaJSON(full, call.store.ideas.find((i) => i.id === id))));
        }
      } else {
        lines.push('applied none');
      }
      return lines.join('\n');
    },
    // the call's input: a command's {ideas}, a card view's idea as its card
    // shows it (links and all), a batch of those, or the settings' values
    // as the add-on may read it: a card view gets only ideas inside its read
    // grant, and with none to show the call is not made ("!permission")
    input(kind) {
      const seen = (id) => call.view.ids.has(id);
      const one = (id) => ideaJSON(call.view, call.snapshot.ideas.find((x) => x.id === id));
      if (kind === 'card') return seen(call.card) ? canon(one(call.card)) : '!permission';
      if (kind === 'cards') {
        const shown = call.cards.filter(seen);
        return shown.length ? canon(shown.map(one)) : '!permission';
      }
      if (kind === 'settings') return call.settings;
      return canon(call.input || { ideas: [] });
    },
    // one row of the permission table: a call, or a has() name
    allows(rowText) {
      const r = JSON.parse(rowText);
      if (r.has !== undefined) return JSON.stringify(has(r.grants, r.has, r.net !== false));
      return JSON.stringify(allows(r.grants, r.call, r.host === undefined ? null : r.host, r.net !== false));
    },
    recorded() { return canon(recorded); },
    // the files a case replaces or adds, as [name, how, value]
    files(caseText) {
      const f = JSON.parse(caseText).files || {};
      return JSON.stringify(Object.keys(f).sort(less).map((name) => {
        const how = Object.keys(f[name])[0];
        return [name, how, f[name][how]];
      }));
    },
    // `harness run`: the cases from the command line and the manifest. A card view's id
    // means every sample idea, one call each; an idea's id means that idea.
    runCases(text) {
      const o = JSON.parse(text);
      const grants = o.grantsText ? o.grantsText.split(',')
        : o.asked.filter((p) => !p.startsWith('net')).concat(o.hosts.map((h) => 'net:' + h));
      const base = { kind: o.kind, grants, settings: o.settings, commands: o.commandList.map((c) => c.id) };
      if (o.kind === 'command') return JSON.stringify([Object.assign(base, { command: o.command, input: o.input })]);
      const ids = o.card === o.view && !store.ideas.some((i) => i.id === o.card)
        ? store.ideas.map((i) => i.id) : [o.card];
      return JSON.stringify(ids.map((id) => Object.assign({}, base, { card: id })));
    },
  };
})()
