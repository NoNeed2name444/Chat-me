# The manifest

`manifest.json` sits at the top of your add-on's folder. It says who made the add-on, which files it runs and what it asks for. Sorta reads it before anything else, and `check.py` applies every rule on this page.

```json
{
  "format": "sorta-addon",
  "formatVersion": 1,
  "id": "io.github.example.wordcount",
  "name": "Word count",
  "version": "1.0.0",
  "author": { "name": "Example", "url": "https://github.com/example" },
  "license": "MIT",
  "minSorta": "1.0.0",
  "api": 1,
  "main": "main.js",
  "permissions": ["ideas.read", "commands", "cards"],
  "hosts": [],
  "contributes": {
    "commands": [{ "id": "count", "title": "Count words" }],
    "cardView": { "id": "words", "title": "Word count" },
    "settings": "settings.json"
  }
}
```

The manifest is one JSON object. Each key appears once, and the check refuses a key the format doesn't define. In the manifest and your other JSON files, lists and objects nest at most 32 deep and a number has at most 32 characters.

## Fields

| Field | Rule |
|---|---|
| `format` | Always `"sorta-addon"`. |
| `formatVersion` | Always `1`. |
| `id` | Your add-on's name for good, in reverse-DNS form like `io.github.you.tidy-titles`. 3 to 64 characters of `a` to `z`, `0` to `9`, `.` and `-`, with at least one dot and no empty part. Ids starting with `com.cramdown` are Sorta's own. |
| `name` | 1 to 30 characters, with no space at either end. No control characters, and no invisible ones like zero-width spaces, direction marks or line separators. The same goes for every title. |
| `version` | A semantic version like `1.2.0` or `1.3.0-beta.1`, with no `+build` part. Each number has at most 9 digits and no leading zero. |
| `author` | An object with exactly `name` and `url`. The name is 1 to 60 characters with no `@`. The url starts with `https://`, has at most 200 characters and no user name. |
| `license` | An SPDX id on the OSI's list, like `MIT`, `Apache-2.0` or `GPL-3.0-only`. One id, not an expression like `MIT OR Apache-2.0`. |
| `minSorta` | The oldest Sorta version your add-on works on, in the same form as `version`. |
| `api` | The API's major version, a whole number. `1` for now. |
| `main` | The `.js` file Sorta runs, at most 256 KB. |
| `permissions` | A list of what the add-on asks for. Each one appears once. [Permissions](permissions.md) lists them. |
| `hosts` | The HTTPS hosts `net` may reach. Required with `net`, and empty or missing without it. |
| `contributes` | What the add-on adds to Sorta. Optional. |

## Hosts

Each host is an exact lowercase name, like `api.example.org`. No `https://`, no path, no port, no `*` and no IP address. Each one appears once. People grant each host on its own.

## Contributes

`contributes` may hold four fields. Each one goes with a permission, and the check refuses one without the other.

| Field | Permission | Rule |
|---|---|---|
| `commands` | `commands` | 1 to 5 commands. Each is `{ "id", "title" }`. The id is 1 to 32 characters of `a` to `z`, `0` to `9` and `-`, starting with a letter or digit. The title is 1 to 30 characters. |
| `cardView` | `cards`, with `ideas.read` or `ideas.read.folder` | One `{ "id", "title" }`, with the same rules as a command. A card view reads ideas, so it needs one of the read permissions too. |
| `settings` | none | The name of a `.json` file in your add-on, described below. |
| `boardPalette` | `board.palette` | The name of a `.json` file in your add-on, described below. |

## The settings file

Sorta draws your settings page from this file. Your code reads the values with `sorta.settings.get()`.

```json
{
  "settings": [
    { "key": "titles", "type": "toggle", "title": "Count words in titles", "default": false },
    { "key": "label", "type": "text", "title": "Label", "default": "Words", "maxLength": 20 },
    { "key": "unit", "type": "choice", "title": "Count", "default": "words",
      "choices": [{ "value": "words", "title": "Words" }, { "value": "letters", "title": "Letters" }] }
  ]
}
```

- 1 to 20 settings, each with its own `key`. A key is 1 to 32 letters and digits, starting with a lowercase letter.
- A title is 1 to 40 characters. Write a toggle's title for what happens when it's on.
- A toggle's default is `true` or `false`.
- A text setting's `maxLength` is 1 to 200, and 200 if you leave it out. Its default is text no longer than that.
- A choice has 2 to 10 choices, each with its own `value` and a `title`. Values follow the rules for keys. The default is one of the values.

## The board palette

A palette recolours Sorta's 2D board. It's data, with no code. It has a `day` set and a `night` set, and each set has exactly these fields:

```json
{
  "day": { "page": "#56636B", "card": "#FFFFFF", "cardText": "#1E1D1A", "link": "#F2D3A0", "folders": ["#B5543A"] },
  "night": { "page": "#0E0D12", "card": "#6E6880", "cardText": "#FFFFFF", "link": "#B9B0D0", "folders": [] }
}
```

Colours are `#RRGGBB`. `folders` holds up to 6 tones. By day and by night, card text on the card needs a contrast of at least 4.5:1, and cards and links on the page need at least 3:1. The check measures each pair the way WCAG does.

## Files

An add-on holds only these files:

- `manifest.json`, `README.md`, `CHANGELOG.md` and one of `LICENSE`, `LICENSE.txt` or `LICENSE.md`, at the top;
- `.js` and `.json` files, at the top or in folders at most two deep.

Every file is UTF-8 text with no NUL byte. Names use only `A` to `Z`, `a` to `z`, `0` to `9`, `.`, `_`, `-` and `/`. No hidden files, no pictures, no fonts and no binaries. A picture written into text counts too: the check refuses an SVG tag or a picture's `data:` address in any file.
