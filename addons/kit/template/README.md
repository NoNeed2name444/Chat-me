# Word count

Counts the words in your ideas. Choose a few ideas and run Count words, or run it with none chosen to count them all. Each idea's card also shows its own count.

## Settings

**Count words in titles** is off at first. Turn it on to count each title along with the idea's text.

## What it asks for

- Read your ideas, to count their words.
- Add one command, Count words.
- Add one line to each idea's card.

It never changes an idea and never goes online.

## Start your own add-on from this one

1. Copy this folder and rename it.
2. In `manifest.json`, change `id`, `name` and `author`. The id stays yours for good, so base it on a name you control, like `io.github.yourname.wordcount`.
3. Change `main.js` and `tests/expect.json` to do what your add-on does.
4. From the kit's folder, run `python3 -I tools/check.py <your folder>`, then `python3 -I tools/pack.py <your folder>`.

The kit's `docs` folder covers each step.

## License

MIT. See `LICENSE`.
