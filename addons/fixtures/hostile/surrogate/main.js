// A hostile fixture (S07b, repair 1): half of a surrogate pair, which is not
// text, handed to a call. It is refused before it reaches the app.
sorta.commands.on('run', async () => sorta.has('\ud83d'));
