// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', async () => {
  for (let i = 0; i < 21; i++) await sorta.ideas.capture({ text: 'Idea ' + i });
});
