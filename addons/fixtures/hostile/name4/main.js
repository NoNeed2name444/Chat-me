// A hostile fixture (S07b, repair 1): names outside the first 65,536
// characters, which take four bytes in UTF-8, in every place an add-on can
// put one. They are text like any other, and nothing breaks.
sorta.commands.on('😀', async () => {
  if (sorta.has('😀')) throw new Error('no such permission');
  return { message: '😀' };
});
