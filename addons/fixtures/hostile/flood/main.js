// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', () => new Promise(() => {
  (function more() {
    for (let i = 0; i < 1000; i++) Promise.resolve(i).then(() => {});
    Promise.resolve().then(more);
  })();
}));
