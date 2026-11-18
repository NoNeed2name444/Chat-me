// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', () => {
  const kept = [];
  for (;;) kept.push(new Array(100000).fill(kept.length));
});
