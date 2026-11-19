# Getting started

A Sorta add-on is a folder of JavaScript and JSON that adds commands, card lines or settings to Sorta. This page takes you from the Word count template to an add-on you can install and submit.

## What you need

- Python 3.10 or later, for `check.py` and `pack.py`. They use only the standard library.
- A text editor.
- A C compiler, but only for the harness, which runs your add-on on a laptop. Xcode's command line tools, `build-essential` or WSL on Windows all work.

You don't need anything from npm or PyPI, or an account.

## Start from Word count

Copy the template folder and give your copy its own id:

```sh
cp -R kit/template my-addon
```

Then open `my-addon/manifest.json` and change these fields:

- `id`, a reverse-DNS name you control, like `io.github.yourname.tidy-titles`. It never changes after you publish.
- `name`, at most 30 characters. People see it in Sorta.
- `author`, your name and a link to your page. Never an email address.
- `version`, which starts at `1.0.0`.

Word count has one command, one card view and one setting. Keep what you need and delete the rest, from `main.js` and from `contributes` in the manifest. [The manifest](manifest.md) explains each field.

## Write the code

`main.js` runs when someone uses your add-on. It registers handlers, and Sorta calls them:

```js
sorta.commands.on("count", async (event) => {
  const page = await sorta.ideas.list({ limit: 200 });
  return { message: `${page.ideas.length} ideas` };
});
```

Each command id in `main.js` matches one in `contributes.commands`. [The API](api.md) lists every call, and [Permissions](permissions.md) lists what each one needs.

## Check it

```sh
python3 -I kit/tools/check.py my-addon
```

It prints `ok`, your id and your version, or `refused` with a reason code and what to fix. [Review](review.md) lists every code.

## Run it

Build the harness once, then run each command and card view on the kit's sample ideas:

```sh
make -C kit/harness
kit/harness/harness run my-addon --ideas kit/api/sample-ideas.json --command count
```

[Testing](testing.md) covers the harness and `tests/expect.json`.

## Pack it

```sh
python3 -I kit/tools/pack.py my-addon
```

It writes `<id>-<version>.sortaaddon` and prints its SHA-256. Packing the same files always gives the same bytes, so anyone can rebuild your file and compare.

## Install it on your phone

Send the `.sortaaddon` file to your phone and open it with Sorta. Sorta checks it, then shows what it asks for. Nothing runs until you say yes.

## Share it

Submit it to the add-on directory with a pull request that adds your folder. CONTRIBUTING.md in the directory says how, and [Review](review.md) says what the checks look for.
