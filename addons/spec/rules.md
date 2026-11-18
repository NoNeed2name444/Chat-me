# The checker's rules, in order

The kit's `check.py`, the iOS checker and the Android checker must give the same code for the same package. Each check below runs over every entry or field before the next check starts, and the first failure decides the code. The details that follow a code may differ between platforms. The code may not.

`reasons.json` lists every code. The fixtures in `addons/fixtures/packages/` and `addons/fixtures/zips/` give the expected code for each case, and S07b's checkers run the same fixtures.

## Limits

| Name | Value |
|---|---|
| Packed archive | 524,288 bytes |
| Unpacked total | 1,048,576 bytes |
| Entries | 200 |
| The `main` file | 262,144 bytes |
| Ratio | 100 |
| Entry name | 255 bytes |
| Folder depth | 2, so at most 2 `/` in a file's name |

## 1. The archive

Every step reads only the end record and the central directory, until step 13, which reads the local headers. Step 14 is the first to unpack.

1. `zip.size` if the file is over 524,288 bytes.
2. `zip.unreadable` unless the end record is found. Search backwards from the last possible place, `length - 22`, down to `length - 22 - 65535`. The first `PK\5\6` whose comment length reaches exactly to the end of the file is the record. Then refuse with `zip.unreadable` if:
   - the disk number or the directory's disk number isn't 0, or the entries on this disk differ from the total;
   - the total is `0xFFFF`, the directory size or offset is `0xFFFFFFFF`, or a ZIP64 locator `PK\6\7` sits 20 bytes before the record;
   - the record's comment length isn't 0;
   - the directory's offset plus its size isn't the record's offset.
3. `zip.entries` if the record's entry count is over 200.
4. `zip.unreadable` unless every entry the count promises is a `PK\1\2` header inside the directory, and the entries fill the directory exactly. Then `zip.unreadable` for the first entry whose comment length isn't 0, checked as each entry is read.
5. `zip.encrypted` if an entry's flags have bit 0, 6 or 13 set, which is `flags & 0x2041`, or its method is 99.
6. `zip.unreadable` if an entry's method is neither 0, stored, nor 8, deflated.
7. `zip.path` for an entry name that:
   - is empty or over 255 bytes;
   - has a backslash;
   - starts with `/`;
   - starts with a drive letter, `[A-Za-z]:`;
   - has a `..` part, or an empty or `.` part, after one trailing `/` is dropped;
   - has a byte outside `A-Z a-z 0-9 . _ - /`.
8. `zip.link` if bits 12 to 15 of an entry's external attributes' high 16 bits are `0xA`, a Unix symbolic link.
9. `zip.duplicate` if two entries have the same name.
10. `zip.case` if two names are equal after ASCII lowercasing.
11. `zip.ratio` if an entry's size is over 100 times its packed size. Then `zip.ratio` if all the entries' sizes add up to over 100 times the archive's length. The second rule catches entries that share one compressed stream.
12. `zip.size` if all the entries' sizes add up to over 1,048,576 bytes.
13. `zip.unreadable` before unpacking. Each entry's span runs from its local header to the end of its packed data: 30 bytes, the local name and extra lengths, and the directory's packed size. Refuse if:
    - there's no `PK\3\4` local header where an entry points;
    - two spans overlap;
    - the spans, sorted, don't start at byte 0, leave a gap between two, or end before the central directory starts. So prepended bytes, data descriptors and hidden files all fail.
14. `zip.unreadable` while unpacking, in the directory's order, if:
    - the local header's name or method differs from the directory's;
    - the data runs into the central directory;
    - a stored entry's packed size and size differ;
    - deflated data doesn't end exactly at the entry's size, or doesn't inflate;
    - the CRC-32 doesn't match.

Extra fields are skipped, not read. Times and modes are ignored.

## 2. The files

Names are the entry names, all ASCII after step 7.

1. `file.type` for any name that:
   - ends with `/`, a folder entry;
   - has a part starting with `.`;
   - isn't `manifest.json`, `README.md`, `LICENSE`, `LICENSE.txt`, `LICENSE.md` or `CHANGELOG.md` at the top, and doesn't end with `.js` or `.json`.
2. `file.depth` for a name with more than 2 `/`.
3. `file.text` for a file with a NUL byte, then for one that isn't strict UTF-8. Overlong forms, surrogates and code points above U+10FFFF all fail.
4. `file.picture` for a file whose text holds `<svg` or `data:image/`, compared after ASCII lowercasing. Files are checked in the directory's order.
5. `manifest.missing` if there's no `manifest.json` at the top.
6. `manifest.json` if it isn't strict JSON, as described below, or isn't an object.
7. The manifest's fields, in the next section's order.

### Strict JSON

The same parse applies to `manifest.json`, the settings file and the palette file:

- RFC 8259 JSON, decoded from UTF-8. A byte order mark at the start fails.
- A key twice in one object fails.
- `NaN`, `Infinity` and `-Infinity` fail.
- Lists and objects nested more than 32 deep fail. The top value is depth 1. Count `[` and `{` outside strings, so a checker never recurses deeper than 32.
- A number written with more than 32 characters fails, sign, fraction and exponent included.
- No input may crash a checker. Whatever its parser can't read gets the file's code.
- A failure in `manifest.json` gives `manifest.json`. A failure in the settings or palette file gives `contributes`.

A number is whole when it's finite and has no fraction, so `1` and `1.0` are both whole. `true` and `false` are never numbers.

### Labels

A label is a string a person reads. It's 1 to N Unicode code points, not grapheme clusters or UTF-16 units. It doesn't start or end with U+0020, and has no code point from U+0000 to U+001F or U+007F to U+009F. It also has no code point in the Unicode categories Cf (format, like U+202E and U+200B), Zl (U+2028) or Zp (U+2029). Cf grows with each Unicode version, so a checker uses its platform's tables. The fixtures use only code points that have been Cf since Unicode 3.

### Semantic versions

`version` and `minSorta` match:

```
N.N.N or N.N.N-P(.P)*
N is 0, or 1 to 9 digits with no leading zero
P is N, or 1 or more of 0-9 A-Z a-z - with at least one non-digit
```

There's no `+build` part. Precedence follows semver.org: numbers, then a version without a pre-release above one with it, then the pre-release parts one by one. A numeric part is below an alphanumeric one, and more parts win when all before them are equal.

## 3. The manifest

1. `format` unless `format` is the string `"sorta-addon"`.
2. `formatVersion` unless `formatVersion` is whole and equal to 1.
3. `manifest.json` for any key outside `format`, `formatVersion`, `id`, `name`, `version`, `author`, `license`, `minSorta`, `api`, `main`, `permissions`, `hosts` and `contributes`.
4. `id` unless it's a string of 3 to 64 characters matching `[a-z0-9-]+(\.[a-z0-9-]+)+`.
5. `id.reserved` if it's `com.cramdown` or starts with `com.cramdown.`.
6. `name` unless it's a label of at most 30.
7. `version` unless it's a semantic version.
8. `minSorta` unless it's a semantic version.
9. `api` unless it's whole, from 1 to 99.
10. `author` unless:
    - it's an object with exactly the keys `name` and `url`;
    - the name is a label of at most 60 with no `@`;
    - the url is a string starting with `https://`, at most 200 characters, with no whitespace or control character;
    - the host, the part after `https://` and before the first `/`, `?` or `#`, isn't empty and has no `@`.
11. `license` unless it's one of the ids in `kit/tools/osi-licenses.json`, compared exactly.
12. `main.missing` unless `main` is a string ending in `.js` that names a file in the package.
13. `main.size` if that file is over 262,144 bytes.
14. `permissions` unless it's a list of strings from `ideas.read`, `ideas.read.folder`, `ideas.write`, `commands`, `cards`, `board.palette`, `net` and `ai`. Then `permissions` if one appears twice, then if both `ideas.read` and `ideas.read.folder` appear.
15. `hosts`, where a missing `hosts` counts as `[]`:
    - unless it's a list;
    - if `net` is asked for and the list is empty;
    - if the list isn't empty and `net` isn't asked for;
    - for an entry that isn't a string, or has fewer than 2 dot-separated labels, or is over 253 characters, or has a label not matching `[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?`, or has an all-digit last label;
    - if an entry appears twice.
16. `commands`, only if `contributes` is an object with a `commands` key:
    - unless it's a list of 1 to 5;
    - for a command that isn't an object with exactly `id` and `title`;
    - for an id not matching `[a-z0-9][a-z0-9-]{0,31}`;
    - for a title that isn't a label of at most 30;
    - for an id that appears twice.

    Each command is checked in full before the next.
17. `contributes`, where a missing `contributes` counts as `{}`:
    - unless it's an object;
    - for a key outside `commands`, `cardView`, `settings` and `boardPalette`;
    - if `commands`, `cardView` or `boardPalette` appears without the `commands`, `cards` or `board.palette` permission, or the permission without the field, checked in that order;
    - with the code `cards.read`, not `contributes`, if `cardView` appears and the permissions hold neither `ideas.read` nor `ideas.read.folder`;
    - for a `cardView` that isn't an object with exactly `id` and `title`, with the command rules for each;
    - for a `settings` value that isn't a string ending in `.json` naming a file in the package, then for that file's JSON, then for its rules below;
    - for a `boardPalette` value, the same way, then the palette rules below.

### The settings file

It's an object with exactly the key `settings`, a list of 1 to 20. Each setting in order:

1. It's an object whose `key` matches `[a-z][A-Za-z0-9]{0,31}`.
2. Its `type` is `toggle`, `text` or `choice`. It has `key`, `type`, `title` and `default`, and no other key except `maxLength` for text and `choices` for a choice.
3. Its `title` is a label of at most 40, and its key hasn't appeared before.
4. A toggle's default is `true` or `false`.
5. A text setting's `maxLength`, 200 if missing, is whole from 1 to 200. Its default is a string of at most that many code points.
6. A choice has 2 to 10 choices. Each is an object with exactly `value` and `title`, the value a string matching the key pattern and the title a label of at most 40. The values are unique, and the default is one of them.

Every failure gives `contributes`.

### The palette file

It's an object with exactly `day` and `night`. Each set is an object with exactly `page`, `card`, `cardText`, `link` and `folders`. The first four are strings matching `#[0-9A-Fa-f]{6}`. `folders` is a list of at most 6 such strings.

In each set, the contrast of:

1. `cardText` on `card` is at least 4.5;
2. `card` on `page` is at least 3;
3. `link` on `page` is at least 3.

Contrast is WCAG 2's `(L1 + 0.05) / (L2 + 0.05)` with L1 the lighter. Each channel `c = byte / 255` becomes `c / 12.92` when `c <= 0.04045`, and `((c + 0.055) / 1.055) ^ 2.4` above it. Then `L = 0.2126 R + 0.7152 G + 0.0722 B`. The ratio is compared in double precision with no rounding.

Every failure gives `contributes`, so the order within the file doesn't change the code.

## 4. At install

Only the app runs these, after every rule above passes, because only it knows its own version.

1. `install.api` if `api` is above the app's API major version.
2. `install.minSorta` if `minSorta` has higher precedence than the app's version.

## Where the parity is tested

- `addons/fixtures/packages/*/expect.json`: `{"verdict", "reason", "app"}`, where `app` gives `{"sorta", "api"}` for the install checks.
- `addons/fixtures/zips/*.json`: a description `kit/tools/zipfix.py` builds, with its `expect` inside.
- `addons/kit/tools/tests/` runs both on the kit. S07b runs both on iOS and Android.
