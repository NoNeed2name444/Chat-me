// The add-on API v1 as the add-on sees it (S07, section 5), set up before any
// add-on code runs. It is compiled like add-on code (no eval where it runs)
// and evaluates to one function, which the host calls with its native `call`
// and which returns the host's own entry points. Neither `call` nor those
// entry points is reachable from add-on code. Every builtin used after setup
// is taken here, before an add-on could change it.
//
// embed_prelude.py writes this file into sorta_prelude.h; CI checks the two
// agree.
(function (call) {
  'use strict';
  const freeze = Object.freeze;
  const keys = Object.keys;
  const define = Object.defineProperty;
  const isArray = Array.isArray;
  const stringify = JSON.stringify;
  const parse = JSON.parse;
  const MapC = Map;
  const mapGet = Map.prototype.get;
  const mapSet = Map.prototype.set;
  const PromiseC = Promise;
  const resolve = Promise.resolve.bind(Promise);
  const then = Promise.prototype.then;
  const StringC = String;
  const sort = Array.prototype.sort;
  const push = Array.prototype.push;
  const join = Array.prototype.join;
  const slice = String.prototype.slice;
  const indexOf = String.prototype.indexOf;
  const charCodeAt = String.prototype.charCodeAt;
  const startsWith = String.prototype.startsWith;
  const apply = Reflect.apply;
  const global = globalThis;

  const NAMES = freeze({
    'permission': 'PermissionError',
    'limit.writes': 'WriteLimitError',
    'limit.captures': 'WriteLimitError',
  });

  class SortaError extends Error {
    // the message is the code, or what the host adds (net: the refused host)
    constructor(code, message) {
      super(message === undefined ? code : message);
      define(this, 'code', { value: code, enumerable: true });
      let name = NAMES[code];
      if (!name) {
        if (apply(startsWith, code, ['net.'])) name = 'NetError';
        else if (apply(startsWith, code, ['api.'])) name = 'ArgumentError';
        else if (apply(startsWith, code, ['limit.'])) name = 'LimitError';
        else name = 'SortaError';
      }
      define(this, 'name', { value: name });
    }
  }

  // the permission a refused call needed, for PermissionError.permission
  const NEEDS = freeze({
    '__proto__': null,
    'ideas.list': 'ideas.read', 'ideas.get': 'ideas.read', 'ideas.search': 'ideas.read',
    'folders.list': 'ideas.read',
    'ideas.capture': 'ideas.write', 'ideas.tag': 'ideas.write', 'ideas.untag': 'ideas.write',
    'ideas.link': 'ideas.write', 'ideas.unlink': 'ideas.write', 'ideas.file': 'ideas.write',
    'commands.on': 'commands', 'cards.on': 'cards', 'net.fetch': 'net',
  });

  function refused(name, reply) {
    const space = apply(indexOf, reply, [' ']);
    if (space < 0) return new SortaError(apply(slice, reply, [1]));
    return new SortaError(apply(slice, reply, [1, space]), apply(slice, reply, [space + 1]));
  }

  function now(name, args) {
    const reply = call(name, stringify(args));
    if (apply(charCodeAt, reply, [0]) === 33) {
      const e = refused(name, reply);
      if (e.code === 'permission' && NEEDS[name] !== undefined) {
        define(e, 'permission', { value: NEEDS[name], enumerable: true });
        // a refused host comes as the message "net:<host>"
        if (apply(startsWith, e.message, ['net:'])) {
          define(e, 'host', { value: apply(slice, e.message, [4]), enumerable: true });
        }
      }
      throw e;
    }
    return reply === '' ? undefined : parse(reply);
  }

  function later(name, args) {
    return new PromiseC(function (ok, fail) {
      try { ok(now(name, args)); } catch (e) { fail(e); }
    });
  }

  const orEmpty = (o) => (o === undefined ? {} : o);
  const commands = new MapC();
  let cardHandler = null;
  let settingsHandler = null;

  const sorta = freeze({
    api: freeze({ major: 1, minor: 0 }),
    has: (name) => now('has', [StringC(name)]) === true,
    ideas: freeze({
      list: (options) => later('ideas.list', [orEmpty(options)]),
      get: (id) => later('ideas.get', [id]),
      search: (text, options) => later('ideas.search', [text, orEmpty(options)]),
      capture: (idea) => later('ideas.capture', [idea]),
      tag: (id, tags) => later('ideas.tag', [id, tags]),
      untag: (id, tags) => later('ideas.untag', [id, tags]),
      link: (a, b) => later('ideas.link', [a, b]),
      unlink: (a, b) => later('ideas.unlink', [a, b]),
      file: (id, folder) => later('ideas.file', [id, folder === undefined ? null : folder]),
    }),
    folders: freeze({
      list: () => later('folders.list', []),
    }),
    commands: freeze({
      on: (id, handler) => {
        if (typeof handler !== 'function') throw new SortaError('api.args');
        now('commands.on', [id]);
        apply(mapSet, commands, [StringC(id), handler]);
      },
    }),
    cards: freeze({
      on: (handler) => {
        if (typeof handler !== 'function') throw new SortaError('api.args');
        now('cards.on', []);
        cardHandler = handler;
      },
    }),
    settings: freeze({
      get: () => later('settings.get', []),
      on: (handler) => {
        if (typeof handler !== 'function') throw new SortaError('api.args');
        now('settings.on', []);
        settingsHandler = handler;
      },
    }),
    storage: freeze({
      get: (key) => later('storage.get', [key]),
      // the value goes as its own JSON text, kept and given back as it is
      set: (key, value) => later('storage.set', [key, stringify(value === undefined ? null : value)]),
      remove: (key) => later('storage.remove', [key]),
    }),
    net: freeze({
      fetch: (url, options) => later('net.fetch', [url, orEmpty(options)]),
    }),
  });

  function text(v) {
    if (typeof v === 'string') return v;
    try { const s = stringify(v); return s === undefined ? StringC(v) : s; } catch (e) { return StringC(v); }
  }

  define(global, 'sorta', { value: sorta, enumerable: false, writable: false, configurable: false });
  define(global, 'console', {
    value: freeze({
      log: function () {
        const parts = [];
        for (let i = 0; i < arguments.length; i++) apply(push, parts, [text(arguments[i])]);
        now('console.log', [apply(join, parts, [' '])]);
      },
    }),
    enumerable: false, writable: false, configurable: false,
  });
  delete global.SharedArrayBuffer;
  delete global.Atomics;

  function shaped(kind, v) {
    if (kind === 'command') {
      if (v === undefined || v === null) return null;
      if (typeof v !== 'object' || isArray(v) || keys(v).length !== 1 ||
          typeof v.message !== 'string' || v.message.length > 120) throw new SortaError('result.shape');
      return { message: v.message };
    }
    if (kind === 'card') {
      if (v === undefined || v === null) return null;
      const lines = v && v.lines;
      if (typeof v !== 'object' || !isArray(lines) || lines.length > 3) throw new SortaError('result.shape');
      for (let i = 0; i < lines.length; i++) {
        if (typeof lines[i] !== 'string' || lines[i].length > 80) throw new SortaError('result.shape');
      }
      return { lines: lines };
    }
    return v;
  }

  function dispatch(kind, id, input) {
    const value = parse(input);
    let handler = null;
    if (kind === 'command') {
      handler = apply(mapGet, commands, [id]);
      if (!handler) throw new SortaError('command.unknown');
    } else if (kind === 'card') {
      handler = cardHandler;
      if (!handler) throw new SortaError('card.none');
    } else if (kind === 'settings') {
      handler = settingsHandler;
      if (!handler) throw new SortaError('settings.none');
    } else {
      throw new SortaError('api.args');
    }
    const step = apply(then, resolve(undefined), [() => handler(value)]);
    return apply(then, step, [(v) => shaped(kind, v)]);
  }

  // canonical JSON: keys sorted, no spaces; undefined is null
  function canon(v) {
    if (v === undefined) return 'null';
    if (v !== null && typeof v === 'object' && typeof v.toJSON === 'function') v = v.toJSON();
    if (v === null || typeof v !== 'object') {
      const s = stringify(v);
      return s === undefined ? 'null' : s;
    }
    const out = [];
    if (isArray(v)) {
      for (let i = 0; i < v.length; i++) apply(push, out, [canon(v[i])]);
      return '[' + apply(join, out, [',']) + ']';
    }
    const names = apply(sort, keys(v), []);
    for (let i = 0; i < names.length; i++) {
      const item = v[names[i]];
      if (item === undefined || typeof item === 'function' || typeof item === 'symbol') continue;
      apply(push, out, [stringify(names[i]) + ':' + canon(item)]);
    }
    return '{' + apply(join, out, [',']) + '}';
  }

  function ok(v) { return '{"ok":true,"value":' + canon(v) + '}'; }

  function failed(reason) {
    let code = 'js.error';
    let message;
    if (reason instanceof SortaError) { code = reason.code; message = reason.message; }
    else if (reason !== null && typeof reason === 'object' && 'message' in reason) message = StringC(reason.message);
    else message = text(reason);
    return '{"code":' + stringify(code) + ',"message":' + stringify(message) + ',"ok":false}';
  }

  return [dispatch, ok, failed];
})
