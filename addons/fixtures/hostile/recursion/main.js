// A hostile fixture (S07b, A3): stopped with its code, within its limit.
function deeper(n) { return deeper(n + 1) + 1; }
sorta.commands.on('run', () => deeper(0));
