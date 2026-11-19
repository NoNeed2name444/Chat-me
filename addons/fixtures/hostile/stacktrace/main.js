// A hostile fixture (S07b, repair 1): every route to a running function of
// Sorta's own, through the engine's stack-trace hooks. None may give one.
const taken = [];
const hook = (error, sites) => {
  for (const site of sites) taken.push(site.getFunction());
  return '';
};
const tried = (work) => { try { work(); } catch (e) { /* refused, which is the point */ } };

tried(() => { Error.prepareStackTrace = hook; });
tried(() => { Object.defineProperty(Error, 'prepareStackTrace', { value: hook, configurable: true }); });
tried(() => { Reflect.set(Error, 'prepareStackTrace', hook); });
tried(() => { TypeError.prepareStackTrace = hook; });
tried(() => { Error.stackTraceLimit = 1000; });
tried(() => { const o = {}; Error.captureStackTrace(o); taken.push(o.stack); });

sorta.commands.on('run', async () => {
  // errors made inside Sorta's own functions, where their frames are
  try { await sorta.ideas.capture({ title: 'x' }); } catch (e) { taken.push(e.stack); }
  try { sorta.commands.on('other', 1); } catch (e) { taken.push(e.stack); }
  try { await sorta.ideas.list({ get limit() { throw new TypeError('inside'); } }); } catch (e) { taken.push(e.stack); }
  try { null.x; } catch (e) { taken.push(e.stack); }
  const got = taken.filter((f) => typeof f === 'function' || (f !== null && typeof f === 'object'));
  if (got.length) throw new Error('captured ' + got.length);
  if (Error.prepareStackTrace !== undefined || Error.captureStackTrace !== undefined) throw new Error('open');
  return { message: 'none' };
});
