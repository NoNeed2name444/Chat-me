# Submitting an add-on

You submit an add-on with a pull request that adds its source folder. Checks run on every pull request, and a person reads the code before it's merged. This page covers the rules, the checks and how long review takes.

## Before you start

Build and test your add-on with the kit, as `kit/docs/getting-started.md` describes. Then run the check on its folder:

```sh
python3 -I kit/tools/check.py my-addon
```

It prints `ok` when the add-on passes. If it prints `refused`, `kit/docs/review.md` explains the code and how to fix it.

## The rules

Every add-on in the directory:

- is free, with no payments, paid versions, donations or tip links;
- has no points, streaks, ranks, badges, levels or leaderboards;
- is open source, under a license on the OSI's list;
- is readable source, with no minified or encoded code;
- carries no pictures, icons or fonts;
- reaches only the hosts it lists, and says in its README what it sends there;
- does what its README says, and nothing else;
- has content that's right for everyone.

## Submit it

1. Fork this repository.
2. Add your folder as `addons/<id>/<version>/`, like `addons/io.github.you.tidy-titles/1.0.0/`. Use the id and version from your manifest.
3. Put in it your `manifest.json`, your `.js` and `.json` files, a `README.md` and a `LICENSE`. A `CHANGELOG.md` is welcome.
4. Open a pull request. Keep it to one add-on version, and change nothing else.

If you can't open a pull request, open an issue and choose Submit an add-on instead. We'll open one for you.

## Update it

Add a new folder with a higher version, like `addons/io.github.you.tidy-titles/1.1.0/`. Never change a version that's listed already. Sorta offers the update to everyone who has your add-on. If you ask for a new permission, Sorta asks each person before it updates.

Only the author already listed for an id can add versions of it, so keep `author.url` the same.

## What the checks look at

The checks run in four steps. Each step reports every problem it finds, with a code, and the next step runs once you fix them. `kit/docs/review.md` says how to fix each one.

| Step | Code | It fails when |
|---|---|---|
| Files | `review.picture` | a file is a picture, an icon, a PDF or a font, by its name or its first bytes, or its text holds `<svg` or `data:image/` |
| Files | `review.binary` | a file is a binary |
| The package | the kit's codes | the kit's check refuses your folder, packed the way `pack.py` packs it |
| The package | `review.files` | there's no `README.md` or no `LICENSE`, or the pull request changes a file outside `addons/` and `revoked.json` |
| Its place | `review.folder` | the folders don't match the manifest's id and version |
| Its place | `review.version` | the version isn't above the last one listed, or a listed version changed |
| Its place | `review.owner` | the id is listed already under another author |
| Its content | `review.paid` | it links to or asks for a payment, a tip or a paid version |
| Its content | `review.points` | its manifest, README or changelog mentions points, streaks, ranks or leaderboards |
| Its content | `review.host` | a `net.fetch` address isn't `https://` or its host isn't in `hosts` |
| Its content | `review.base64` | a file has a base64 run over 1,024 characters |
| Its content | `review.line` | a `.js` file has a line over 500 characters |

Then the harness runs each of your commands and card views on the kit's sample ideas, within Sorta's limits. The run's summary lists your permissions and hosts, and what changed since your last version.

## What a person looks at

A code owner reads every line. They check that the add-on does what its README says, asks only for what it needs and sends nothing it doesn't mention. We merge a pull request only with their approval.

## How long it takes

We aim for a first reply within 7 days. A small add-on with a clear README is quickest to review. Once it's merged, a workflow adds it to the index that Sorta reads.

## Reports and revoking

Anyone can report an add-on with the Report an add-on form. We aim to reply within 7 days. When an add-on breaks the rules, a code owner revokes it with a pull request to `revoked.json`:

```json
{
  "format": "sorta-addons-revoked",
  "formatVersion": 1,
  "revoked": [
    { "id": "io.github.you.tidy-titles", "versions": ["1.0.0"], "reason": "It sent ideas to a host it didn't list.", "date": "2026-11-15" },
    { "author": "https://github.com/someone", "reason": "Kept breaking the rules.", "date": "2026-11-15" }
  ]
}
```

Leave out `versions` to revoke every version of an id. An `author` entry revokes every add-on by that author. The reason is 1 to 200 characters, and the date is a day like `2026-11-15`. A change that breaks this format fails with `review.revoked`.

## License

By submitting, you agree to share your add-on under the license in its folder. This repository's own files are MIT licensed.
