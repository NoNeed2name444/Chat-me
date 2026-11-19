// Count up: counts until it reaches a number it never reaches.
sorta.commands.on("count", async () => {
  let n = 0;
  while (n >= 0) {
    n = (n + 1) % 1000000;
  }
  return { message: `Counted to ${n}` };
});
