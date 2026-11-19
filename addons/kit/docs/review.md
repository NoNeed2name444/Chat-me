# Review

Every add-on passes the same checks on a laptop, in the directory and on the phone. Each problem has a code that stays the same everywhere, so you can look it up here. A person from the directory then reads your code before it's listed.

## On your laptop

`check.py` prints the first problem it finds:

```
$ python3 -I kit/tools/check.py my-addon
refused name: name must be 1 to 30 characters, with no space at either end.
```

Fix it and run the check again. It checks in a fixed order, so a later problem shows only after you fix the earlier ones. `addons/spec/rules.md` in the Sorta repository lists that order.

## The archive

Sorta checks these from the zip's list of files before it unpacks anything. `pack.py` never makes most of them, so you'll see them mostly on a file built some other way.

| Code | What's wrong | How to fix it |
|---|---|---|
| `zip.unreadable` | It isn't a zip Sorta can read: no end record, a ZIP64 or split archive, an unknown compression method, a comment, bytes that belong to no file, a damaged entry or a wrong CRC. | Build the file with `pack.py`. |
| `zip.path` | A file name is empty, too long or starts with `/`. Or it has a backslash, a drive letter, a `.` or `..` part, or a character outside `A-Z a-z 0-9 . _ - /`. | Rename the file. |
| `zip.link` | A file is a symbolic link. | Copy the real file in its place. |
| `zip.duplicate` | Two files have the same name. | Build the file with `pack.py`. |
| `zip.case` | Two names differ only in upper and lower case, like `Main.js` and `main.js`. | Rename one of them. |
| `zip.encrypted` | A file is encrypted. | Build the file with `pack.py`. |
| `zip.ratio` | A file, or the whole archive, unpacks to more than 100 times its packed size. | Build the file with `pack.py`, which doesn't compress. |
| `zip.entries` | More than 200 files. | Join files, or remove ones you don't use. |
| `zip.size` | The file is over 512 KB, or the files add up to more than 1 MB. | Make the add-on smaller. |

## The files

| Code | What's wrong | How to fix it |
|---|---|---|
| `file.type` | A file isn't one an add-on may hold, or is hidden. | Keep only `manifest.json`, `.js`, `.json`, `README.md`, a LICENSE file and `CHANGELOG.md`. |
| `file.depth` | A file is more than two folders deep. | Move it up. |
| `file.text` | A file isn't UTF-8, or has a NUL byte. | Save it as UTF-8 text. |
| `file.picture` | A file's text holds an SVG tag or a picture's `data:` address. | Remove the picture. Add-ons carry none, as files or as text. |
| `main.missing` | `main` doesn't name a `.js` file in the add-on. | Point `main` at your code. |
| `main.size` | The `main` file is over 256 KB. | Split it into smaller files, or remove code you don't use. |

## The manifest

| Code | What's wrong |
|---|---|
| `manifest.missing` | No `manifest.json` at the top of the add-on. |
| `manifest.json` | It isn't valid JSON, isn't one object, names a key twice, nests more than 32 deep, has a number over 32 characters long, or has a field the format doesn't define. |
| `format` | `format` isn't `"sorta-addon"`. |
| `formatVersion` | `formatVersion` isn't `1`. |
| `id` | `id` isn't a reverse-DNS name of 3 to 64 characters. |
| `id.reserved` | `id` starts with `com.cramdown`, which is Sorta's own. |
| `name` | `name` isn't 1 to 30 characters, has a control character or an invisible one, or starts or ends with a space. |
| `version` | `version` isn't a semantic version without a `+build` part. |
| `minSorta` | `minSorta` isn't a semantic version without a `+build` part. |
| `api` | `api` isn't a whole number from 1 to 99. |
| `author` | `author` isn't exactly a name and an `https://` url, or its name has an `@`. |
| `license` | `license` isn't one SPDX id on the OSI's list. |
| `permissions` | A permission is unknown or listed twice, or both `ideas.read` and `ideas.read.folder` are there. |
| `hosts` | `hosts` is missing with `net`, given without `net`, or has an entry that isn't an exact lowercase host name. |
| `commands` | `contributes.commands` isn't 1 to 5 commands, each with its own id and a title of 1 to 30 characters. |
| `contributes` | `contributes` has an unknown field, doesn't match the permissions, or has a card view, settings file or palette that breaks its rules. |
| `cards.read` | The add-on has a card view but asks for neither `ideas.read` nor `ideas.read.folder`. |

[The manifest](manifest.md) has every rule in full.

## At install

The phone runs two more checks, because only it knows its own version.

| Code | What's wrong |
|---|---|
| `install.api` | `api` is newer than this Sorta's API. The person needs to update Sorta. |
| `install.minSorta` | `minSorta` is above this Sorta's version. The person needs to update Sorta. |

To try them on a laptop, add the app's version and API:

```sh
python3 -I kit/tools/check.py my-addon --app-version 1.0.0 --app-api 1
```

## In the directory

The directory's checks run on each pull request. They use the codes above and the ones below. They run in four steps and stop after the first step that finds a problem, listing every problem in that step.

1. Files: pictures and binaries.
2. The package: every code above, packed the way `pack.py` packs it, and the files the directory needs.
3. Its place in the directory: the folders, the version and the author.
4. Its content: payments, points, hosts, base64 and long lines.

| Code | What's wrong | How to fix it |
|---|---|---|
| `review.picture` | A file is a picture, an icon, a PDF or a font, by its name or by its first bytes. Or its text holds an SVG tag or a picture's `data:` address. | Remove it. Add-ons carry no pictures. |
| `review.binary` | A file has a NUL byte, isn't UTF-8, or has a binary file's extension like `.wasm`. | Ship source text only. |
| `review.files` | No `README.md`, or no LICENSE file. Or the pull request changes a file outside `addons/` and `revoked.json`. | Add both, and change nothing else. |
| `review.folder` | A file isn't inside `addons/<id>/<version>/`, or the manifest's id or version doesn't match those folders. | Move the folder, or fix the manifest. |
| `review.version` | The version isn't above the last one listed, or the pull request changes a version that's listed already. | Raise the version and add a new folder. |
| `review.owner` | The id is listed already under another author's url. | Choose an id of your own. |
| `review.paid` | The add-on links to or asks for a payment, a tip or a paid version. | Remove it. Add-ons in the directory are free. |
| `review.points` | The manifest, README or changelog mentions points, streaks, ranks, badges, levels or a leaderboard. | Remove them. Sorta has none of these. |
| `review.host` | A `net.fetch` call names a host that isn't in `hosts`, or an address that isn't `https://`. | Add the host to `hosts`, or use `https://`. |
| `review.base64` | A file has a run of base64 longer than 1,024 characters. | Ship readable source and data. |
| `review.line` | A `.js` file has a line longer than 500 characters. | Break the line, and don't ship minified code. |
| `review.revoked` | A change to `revoked.json` doesn't follow its format. | See CONTRIBUTING.md in the directory. |

After the checks pass, the harness runs each command and card view on the kit's sample ideas, within the limits on [Limits](limits.md). Then a person reads the code, for what the checks can't see. Does the add-on do only what its README says? Does it send only what it needs to? Is its content right for everyone?
