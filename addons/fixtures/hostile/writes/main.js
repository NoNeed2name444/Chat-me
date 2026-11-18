// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', async () => {
  for (let i = 0; i < 101; i++) await sorta.ideas.tag('00000000-0000-4000-8000-000000000101', ['t' + i]);
});
