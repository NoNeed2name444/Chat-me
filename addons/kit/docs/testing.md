# Testing

Test your add-on on a laptop before you install it or submit it. The kit gives you the same checks the directory runs, and a harness that runs your code under Sorta's limits.

## Check and pack

```sh
python3 -I kit/tools/check.py my-addon
python3 -I kit/tools/pack.py my-addon
python3 -I kit/tools/check.py io.github.you.my-addon-1.0.0.sortaaddon
```

`check.py` takes a folder or a `.sortaaddon` file, and gives both the same verdict. `pack.py` refuses whatever `check.py` refuses, with the same code.

## Run it in the harness

The harness is a small stand-in for Sorta, built from the same engine and the same C code as the app. It reads ideas from a JSON file instead of your phone, and it applies the limits on [Limits](limits.md).

Build it once. It needs a C compiler.

```sh
make -C kit/harness
```

Then run a command or a card view:

```sh
kit/harness/harness run my-addon --ideas kit/api/sample-ideas.json --command count
kit/harness/harness run my-addon --ideas kit/api/sample-ideas.json --card words
```

`kit/api/sample-ideas.json` holds 8 made-up ideas in 3 folders. They include a page, ideas in To sort, an idea with no text, one with no title, Arabic text and an emoji. Copy it and add your own cases.

## Write down what you expect

Put the results your add-on must give in `tests/expect.json`, the way Word count does:

```json
{
  "about": "What Word count must give on the kit's sample ideas.",
  "ideas": "api/sample-ideas.json",
  "commands": [
    { "command": "count", "chosen": [], "settings": { "titles": false },
      "reply": { "message": "65 words in 8 ideas" } }
  ],
  "cards": [
    { "idea": "idea-08", "settings": { "titles": false }, "reply": { "lines": ["1 word"] } }
  ]
}
```

- `chosen` lists the ideas the person had chosen. An id that isn't in the sample ideas stands for an idea that's gone.
- `settings` are the values your add-on gets from `sorta.settings.get()`.
- `reply` is exactly what the command or card view must return.

Then run every case in the file:

```sh
kit/harness/harness expect my-addon --ideas kit/api/sample-ideas.json
```

It prints `ok` or `FAIL` for each case, and what your add-on gave instead. A code in a result is on [Runtime codes](runtime-codes.md).

These runs grant every permission your add-on asks for. Test what happens without one too, with `sorta.has()`.

## Check the API version

If you build on the kit itself, two tools keep the API stable:

```sh
python3 -I kit/tools/api_snapshot.py kit/api/sorta-addon-v1.d.ts --check kit/api/v1.json
python3 -I kit/tools/api_diff.py old.json new.json
```

The first fails when `v1.json` no longer matches the declarations. The second fails on a removed, renamed or narrowed call, and passes an addition.

## The kit's own tests

The kit's tests check every rule against the fixture folders and archive descriptions in `addons/fixtures/`. Those live in Sorta's repository, not in the directory, so run the tests from there:

```sh
python3 -I -B addons/kit/tools/tests/run.py
```

Any skipped test fails the run.
