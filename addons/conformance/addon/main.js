// The conformance add-on (S07b, A2). One command, "case", runs the case named
// in its input and logs what it saw, in canonical JSON, so the harness, the
// iOS app and the Android app can be compared byte for byte.
import { canon } from './canon.js';

const G = '00000000-0000-4000-8000-000000000';
const id = (n) => G + n;
const show = (label, value) => console.log(label + ' ' + canon(value));
// a code, then the message when it says more (net: the refused host)
const fail = (label, e) => console.log(label + ' ' + e.name + ' ' +
  (e.code ? e.code + (e.message && e.message !== e.code ? ' ' + e.message : '') : e.message) +
  (e.permission ? ' needs ' + e.permission + (e.host ? ' host ' + e.host : '') : ''));

async function attempt(label, work) {
  try {
    show(label, await work());
  } catch (e) {
    fail(label, e);
  }
}

console.log('loaded');

const cases = {
  // the engine
  async 'engine.globals'() {
    show('types', [typeof SharedArrayBuffer, typeof Atomics, typeof eval, typeof Function]);
    show('api', sorta.api);
    show('frozen', [Object.isFrozen(sorta), Object.isFrozen(sorta.ideas)]);
  },
  async 'engine.eval.caught'() {
    await attempt('eval', () => eval('1 + 1'));
    await attempt('function', () => Function('return 1')());
    await attempt('ctor', () => (async function () {}).constructor('return 1')());
  },
  async 'engine.eval.thrown'() { return eval('2'); },
  // the engine's stack-trace hooks are closed: no call site, so no function of Sorta's own
  async 'engine.stack'() {
    const sites = [];
    const hook = (error, list) => { for (const s of list) sites.push(typeof s.getFunction()); return ''; };
    show('types', [typeof Error.prepareStackTrace, typeof Error.captureStackTrace, Error.stackTraceLimit]);
    await attempt('assign', () => { Error.prepareStackTrace = hook; return 'set'; });
    await attempt('define', () => { Object.defineProperty(Error, 'prepareStackTrace', { value: hook }); return 'set'; });
    await attempt('limit', () => { Error.stackTraceLimit = 1000; return 'set'; });
    await attempt('capture', () => Error.captureStackTrace({}));
    try { await sorta.ideas.get(7); } catch (e) { show('stack', typeof e.stack); }
    show('sites', sites);
  },
  async 'text.astral'() {
    show('has', sorta.has('\u{1F600}'));
    await attempt('search', async () => (await sorta.ideas.search('\u{1F600}\u{10FFFF}')).length);
    return { message: '\u{1F600} \u{10FFFF}' };
  },
  // half of a surrogate pair is not text: refused going in, and coming out
  async 'text.surrogate.argument'() {
    await attempt('has', () => sorta.has('\ud83d'));
    await attempt('search', () => sorta.ideas.search('a\udc00b'));
    await attempt('tag', () => sorta.ideas.tag(id(101), ['\ud800']));
    await attempt('escaped', async () => (await sorta.ideas.search('\\ud83d')).length);
    await attempt('log', () => console.log('\ud83d'));
  },
  async 'text.surrogate.result'() { return { message: 'half \ud83d' }; },
  async 'engine.strict'() {
    await attempt('assign', () => { sorta.ideas = null; return 'kept'; });
    await attempt('console', () => { console.log = null; return 'kept'; });
  },
  async 'result.null'() {},
  async 'result.message'() { return { message: 'Done: 3 ideas' }; },
  async 'result.shape'() { return { message: 'x'.repeat(121) }; },
  async 'result.extra'() { return { message: 'ok', more: 1 }; },
  async 'js.error'() { throw new Error('boom'); },
  async 'js.string'() { throw 'plain words'; },
  async 'js.async'() { await null; throw new RangeError('later'); },

  // reads
  async 'list.all'() { show('list', await sorta.ideas.list()); },
  async 'list.folder'() {
    show('home', await sorta.ideas.list({ folder: id(903) }));
    show('loose', (await sorta.ideas.list({ folder: null })).ideas.map((i) => i.id));
  },
  async 'list.kind'() { show('pages', (await sorta.ideas.list({ kind: 'page' })).ideas.map((i) => i.title)); },
  async 'list.pages'() {
    const one = await sorta.ideas.list({ limit: 3 });
    show('one', [one.ideas.map((i) => i.title), one.cursor]);
    const two = await sorta.ideas.list({ limit: 3, cursor: one.cursor });
    show('two', [two.ideas.map((i) => i.title), two.cursor]);
    const three = await sorta.ideas.list({ limit: 3, cursor: two.cursor });
    show('three', [three.ideas.map((i) => i.title), three.cursor]);
  },
  async 'list.args'() {
    await attempt('zero', () => sorta.ideas.list({ limit: 0 }));
    await attempt('big', () => sorta.ideas.list({ limit: 201 }));
    await attempt('half', () => sorta.ideas.list({ limit: 1.5 }));
    await attempt('cursor', () => sorta.ideas.list({ cursor: 'abc' }));
    await attempt('past', () => sorta.ideas.list({ cursor: '99' }));
    await attempt('kind', () => sorta.ideas.list({ kind: 3 }));
    await attempt('max', async () => (await sorta.ideas.list({ limit: 200 })).ideas.length);
  },
  async 'get.one'() {
    show('one', await sorta.ideas.get(id(104)));
    show('missing', await sorta.ideas.get('no-such-idea'));
    await attempt('number', () => sorta.ideas.get(104));
  },
  async 'search.words'() {
    show('garden', (await sorta.ideas.search('Garden')).map((i) => i.title));
    show('both', (await sorta.ideas.search('  the   LAUNCH ')).map((i) => i.title));
    show('tea', (await sorta.ideas.search('tea')).map((i) => i.title));
    show('tag', (await sorta.ideas.search('food')).map((i) => i.title));
    show('accent', (await sorta.ideas.search('CRÈME')).map((i) => i.title));
    show('one', (await sorta.ideas.search('a', { limit: 1 })).map((i) => i.title));
    await attempt('big', () => sorta.ideas.search('a', { limit: 51 }));
  },
  async 'folders.list'() { show('folders', await sorta.folders.list()); },
  async 'read.none'() {
    show('has', [sorta.has('ideas.read'), sorta.has('ideas.list'), sorta.has('folders.list')]);
    await attempt('list', () => sorta.ideas.list());
    await attempt('folders', () => sorta.folders.list());
    return sorta.ideas.get(id(101));
  },
  async 'read.folder'() {
    show('has', [sorta.has('ideas.read'), sorta.has('ideas.read.folder'), sorta.has('ideas.list')]);
    show('list', (await sorta.ideas.list()).ideas.map((i) => [i.title, i.folder, i.links]));
    show('folders', await sorta.folders.list());
    show('outside', await sorta.ideas.get(id(101)));
    show('search', (await sorta.ideas.search('garden')).map((i) => i.title));
  },
  async 'snapshot'() {
    show('title', (await sorta.ideas.get(id(101))).title);
    await sorta.ideas.tag(id(101), ['seen']);
  },

  // writes
  async 'capture.lines'() { show('id', await sorta.ideas.capture({ text: '  First line  \n\n body text \n' })); },
  async 'capture.title'() {
    show('id', await sorta.ideas.capture({ title: ' Named ', text: 'Its words' }));
    show('id', await sorta.ideas.capture({ title: '  ', text: 'Only words' }));
  },
  async 'capture.args'() {
    await attempt('blank', () => sorta.ideas.capture({ text: ' \n\t ' }));
    await attempt('none', () => sorta.ideas.capture({}));
    await attempt('title', () => sorta.ideas.capture({ title: 5, text: 'x' }));
    await attempt('string', () => sorta.ideas.capture('just text'));
  },
  async 'tags'() {
    await sorta.ideas.tag(id(101), [' alpha ', 'beta', 'alpha', 'green']);
    await sorta.ideas.untag(id(101), ['green', 'absent']);
    await sorta.ideas.untag(id(102), ['absent']);
    await attempt('empty', () => sorta.ideas.tag(id(101), []));
    await attempt('blank', () => sorta.ideas.tag(id(101), ['  ']));
    await attempt('missing', () => sorta.ideas.tag('nope', ['x']));
  },
  async 'links'() {
    await sorta.ideas.link(id(102), id(105));
    await sorta.ideas.link(id(102), id(101));
    await sorta.ideas.unlink(id(102), id(101));
    await attempt('self', () => sorta.ideas.link(id(105), id(105)));
  },
  async 'file'() {
    await sorta.ideas.file(id(105), id(903));
    await sorta.ideas.file(id(101));
    await sorta.ideas.file(id(102), id(903));
    await attempt('folder', () => sorta.ideas.file(id(105), 'no-such-folder'));
  },
  async 'write.none'() {
    show('has', [sorta.has('ideas.write'), sorta.has('ideas.capture')]);
    await attempt('tag', () => sorta.ideas.tag(id(101), ['x']));
    await attempt('capture', () => sorta.ideas.capture({ text: 'x' }));
  },
  async 'write.outside'() {
    await attempt('tag', () => sorta.ideas.tag(id(101), ['x']));
    await attempt('file', () => sorta.ideas.file(id(104), id(903)));
    await sorta.ideas.tag(id(104), ['inside']);
  },
  async 'write.captured'() {
    const { id: made } = await sorta.ideas.capture({ text: 'New one' });
    await sorta.ideas.tag(made, ['fresh']);
    await sorta.ideas.link(made, id(103));
    await sorta.ideas.file(made, id(901));
  },
  async 'write.gone'() {
    show('seen', (await sorta.ideas.get(id(102))).title);
    await sorta.ideas.tag(id(102), ['late']);
    await sorta.ideas.link(id(103), id(102));
    await sorta.ideas.tag(id(103), ['kept']);
  },
  async 'writes.hundred'() {
    for (let i = 0; i < 98; i++) await sorta.ideas.tag(id(105), ['t' + i]);
    await sorta.ideas.tag(id(102), ['undone']);
    await sorta.ideas.untag(id(102), ['undone']);
  },
  async 'writes.over.caught'() {
    for (let i = 0; i < 100; i++) await sorta.ideas.tag(id(105), ['t' + i]);
    await attempt('101st', () => sorta.ideas.tag(id(105), ['more']));
    await attempt('after', () => sorta.ideas.capture({ text: 'still?' }));
    return { message: 'carried on' };
  },
  async 'captures.twenty'() {
    for (let i = 0; i < 20; i++) await sorta.ideas.capture({ text: 'Idea ' + i });
  },
  async 'captures.over.caught'() {
    for (let i = 0; i < 20; i++) await sorta.ideas.capture({ text: 'Idea ' + i });
    await attempt('21st', () => sorta.ideas.capture({ text: 'one more' }));
  },
  async 'writes.then.error'() {
    await sorta.ideas.tag(id(101), ['x']);
    throw new Error('after writing');
  },

  // own things: storage, settings, console, has
  async 'storage'() {
    show('empty', await sorta.storage.get('k'));
    await sorta.storage.set('k', { b: [1, 'two'], a: null });
    show('kept', await sorta.storage.get('k'));
    await sorta.storage.set('n', 3);
    show('n', await sorta.storage.get('n'));
    await sorta.storage.remove('k');
    show('gone', await sorta.storage.get('k'));
    await attempt('key', () => sorta.storage.set('', 1));
  },
  async 'storage.limit'() {
    await sorta.storage.set('a', 'x'.repeat(200000));
    await attempt('over', () => sorta.storage.set('b', 'y'.repeat(70000)));
    await attempt('replace', () => sorta.storage.set('a', 'z'.repeat(250000)));
  },
  async 'settings.get'() { show('settings', await sorta.settings.get()); },
  async 'has'() {
    show('has', ['ideas.read', 'ideas.write', 'commands', 'cards', 'board.palette', 'net', 'net:api.example.com',
      'net:cdn.example.com', 'net.fetch', 'ai', 'ai.ask', 'storage.get', 'console.log', 'nothing'].map((n) => [n, sorta.has(n)]));
  },
  async 'console'() {
    for (let i = 0; i < 205; i++) console.log('line', i);
    console.log({ b: 1, a: [true, null] }, 'and', 2);
  },
  async 'not.load'() {
    await attempt('commands', () => { sorta.commands.on('late', () => null); return 'ok'; });
  },

  // net
  async 'net.get'() {
    const r = await sorta.net.fetch('https://API.example.com/v1/items?token=q-token-7731#part',
      { headers: { 'X-Api-Key': 'h-value-5512' } });
    show('reply', [r.status, r.headers, JSON.parse(r.body)]);
  },
  async 'net.refused'() {
    await attempt('http', () => sorta.net.fetch('http://api.example.com/v1/items'));
    await attempt('host', () => sorta.net.fetch('https://other.example.org/'));
    await attempt('url', () => sorta.net.fetch('not a url'));
    await attempt('userinfo', () => sorta.net.fetch('https://me\u0040api.example.com/v1/items'));
    await attempt('cookie', () => sorta.net.fetch('https://api.example.com/v1/items', { headers: { Cookie: 'a=1' } }));
    await attempt('method', () => sorta.net.fetch('https://api.example.com/v1/items', { method: 'TRACE' }));
    await attempt('body', () => sorta.net.fetch('https://api.example.com/v1/echo', { method: 'POST', body: 'b'.repeat(1048577) }));
  },
  async 'net.redirects'() {
    await attempt('same', async () => (await sorta.net.fetch('https://api.example.com/v1/old')).body);
    await attempt('granted', async () => (await sorta.net.fetch('https://api.example.com/v1/moved')).body);
    await attempt('other', () => sorta.net.fetch('https://api.example.com/v1/away'));
    await attempt('plain', () => sorta.net.fetch('https://api.example.com/v1/plain'));
    await attempt('loop', () => sorta.net.fetch('https://api.example.com/v1/loop'));
  },
  async 'net.limits'() {
    await attempt('big', () => sorta.net.fetch('https://api.example.com/v1/big'));
    await attempt('slow', () => sorta.net.fetch('https://api.example.com/v1/slow'));
    await attempt('missing', async () => (await sorta.net.fetch('https://api.example.com/v1/none')).status);
  },
  async 'net.post'() {
    const r = await sorta.net.fetch('https://api.example.com/v1/echo', { method: 'post', body: 'héllo' });
    show('reply', [r.status, r.body]);
  },
  async 'net.off'() {
    show('has', [sorta.has('net'), sorta.has('net:api.example.com'), sorta.has('net.fetch')]);
    await attempt('fetch', () => sorta.net.fetch('https://api.example.com/v1/items'));
    await attempt('http', () => sorta.net.fetch('http://api.example.com/v1/items'));
  },
  // ask each time, for each host a request reaches: the redirect to cdn.example.com is asked about too
  async 'net.ask.redirect'() {
    await attempt('other host, declined', async () => (await sorta.net.fetch('https://api.example.com/v1/moved')).status);
    await attempt('same host', async () => (await sorta.net.fetch('https://api.example.com/v1/old')).status);
    await attempt('other host, allowed', async () => (await sorta.net.fetch('https://api.example.com/v1/moved')).status);
  },
  async 'net.ask'() {
    await attempt('first', async () => (await sorta.net.fetch('https://api.example.com/v1/items')).status);
    await attempt('second', async () => (await sorta.net.fetch('https://api.example.com/v1/items')).status);
  },
};

sorta.commands.on('case', async (input) => {
  const run = cases[input.case];
  if (!run) throw new Error('no case ' + input.case);
  return run();
});

// an add-on works with what it was granted
if (sorta.has('cards')) sorta.cards.on(async (idea) => {
  if (idea.title === 'Seed list') {
    try {
      await sorta.ideas.tag(idea.id, ['x']);
    } catch (e) {
      return { lines: [e.name + ' ' + e.code] };
    }
  }
  if (idea.title === 'Tea shop') return { lines: ['a', 'b', 'c', 'd'] };
  if (idea.title === 'Reading list') while (true) {}
  return { lines: [idea.title, idea.tags.join(', '), String(idea.links.length)] };
});

sorta.settings.on(async (values) => {
  if (values.fail) throw new Error('bad settings');
  return { saw: values };
});
