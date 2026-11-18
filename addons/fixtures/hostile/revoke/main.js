// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', async () => {
  await sorta.ideas.list();
  for (;;) {}
});
