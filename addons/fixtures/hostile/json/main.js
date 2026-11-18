// A hostile fixture (S07b, A3): stopped with its code, within its limit.
sorta.commands.on('run', () => JSON.parse('['.repeat(100000) + ']'.repeat(100000)));
