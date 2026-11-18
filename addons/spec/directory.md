# The directory's formats

The public `sorta-addons` repository keeps add-on sources on `main`, and its `publish.yml` writes built files to the `index` branch. The app reads two files from that branch over HTTPS, at the address in the build setting `SORTA_ADDONS_INDEX_URL`. This page fixes their formats. `addons/directory/` holds the repository's own files, and `kit/tools/review.py` holds its checks and its build.

## index.json

```json
{
  "format": "sorta-addons-index",
  "formatVersion": 1,
  "builtFrom": "<the main commit the build ran on>",
  "addons": [
    {
      "id": "io.github.example.wordcount",
      "name": "Word count",
      "author": { "name": "Example", "url": "https://github.com/example" },
      "versions": [
        {
          "version": "1.0.0",
          "file": "dist/io.github.example.wordcount-1.0.0.sortaaddon",
          "sha256": "<64 lowercase hex digits>",
          "size": 4096,
          "permissions": ["ideas.read", "commands", "cards"],
          "hosts": [],
          "minSorta": "1.0.0",
          "api": 1,
          "commit": "<the main commit that last changed this version's folder>"
        }
      ]
    }
  ]
}
```

- `addons` is sorted by name, compared case-insensitively, then by id. There are no ratings, counts or ranks.
- `versions` is sorted by semantic-version precedence, oldest first. `name` and `author` come from the newest version.
- `file` is relative to the `index` branch. `sha256` and `size` are the packed file's. The app refuses a download whose SHA-256 differs.
- A version that `revoked.json` covers is left out of the index and of `dist/`. An add-on with no versions left is left out.
- `pack.py` builds each file from the version's folder, so anyone can rebuild it from `main` and compare.
- A later `formatVersion` 1 index may add an optional `signatures` field. Readers ignore fields they don't know.

## revoked.json

```json
{
  "format": "sorta-addons-revoked",
  "formatVersion": 1,
  "revoked": [
    { "id": "io.github.example.wordcount", "versions": ["1.0.0"], "reason": "Sent ideas to an unlisted host.", "date": "2026-11-15" },
    { "author": "https://github.com/example", "reason": "Kept breaking the rules.", "date": "2026-11-15" }
  ]
}
```

- The top level has exactly these three keys. The file on `main` starts with an empty `revoked` list.
- An entry names either an `id`, with an optional non-empty list of `versions`, or an `author` url starting with `https://`.
- Without `versions`, an `id` entry covers every version. An `author` entry covers every add-on whose `author.url` matches exactly.
- `reason` is a label of 1 to 200 characters. `date` is a calendar date written `YYYY-MM-DD`.
- `review.py` checks the file in every pull request that changes it, and again in `publish.yml`, giving `review.revoked` when it breaks these rules.
- `publish.yml` copies the file to the `index` branch unchanged. The app turns off each covered add-on at its next check.

## The repository on main

```
README.md, CONTRIBUTING.md, LICENSE, revoked.json
addons/<id>/<version>/      a submission's source folder
kit/                        Sorta's addons/kit, copied by the lead
engine/                     Sorta's addons/engine, copied by the lead
.github/workflows/          check.yml and publish.yml
.github/ISSUE_TEMPLATE/     report.yml and submit.yml
.github/CODEOWNERS
```

## check.yml

- Runs on `pull_request` to `main`, with `permissions: contents: read`, on `ubuntu-latest`, with no secret.
- Checks out the pull request into `pr/` and the base commit into `trusted/`. Every tool runs from `trusted/`, so a pull request can't change the checks it's judged by.
- Runs `review.py pr`, then `review.py summary` into the step summary, then builds the harness and runs `review.py harness`.

## publish.yml

- Runs on `push` to `main`, with `permissions: contents: write`, on `ubuntu-latest`, with the built-in token only.
- Runs `review.py all`, builds the harness and runs `review.py harness` on every listed version, then `review.py build`.
- Commits `index.json`, `revoked.json` and `dist/` with no checkout of the `index` branch, its parent being the last `index` commit, and pushes to `refs/heads/index` only. When the tree is unchanged, it pushes nothing.

## The review

`review.py` runs four stages and stops after the first stage with a problem, reporting every problem in that stage. Each line it prints is `<code> <path>: <detail>`.

1. Files, by name and first bytes: `review.picture`, then `review.binary`.
2. The package: `check.py`'s rules on the folder packed by `pack.py`, and `review.files`.
3. Its place: `review.folder`, `review.version` and `review.owner`.
4. Its content: `review.paid` in any file, `review.points` in `manifest.json`, `README.md` and `CHANGELOG.md`, `review.host` and `review.line` in `.js` files, and `review.base64` in any file.

In a pull request, a change to a listed version's folder gives `review.version`, and a file in `addons/` outside `addons/<id>/<version>/` gives `review.folder`. Files outside `addons/` and `revoked.json` aren't checked by code, and the step summary lists them for the code owner.

## The harness step

This is where S07a and S07b join. `review.py harness` runs, for each new version:

```
<harness> run <folder> --ideas kit/api/sample-ideas.json --command <id>
<harness> run <folder> --ideas kit/api/sample-ideas.json --card <id>
```

once per command and once for the card view, with `<harness>` built by `make -C kit/harness` at `kit/harness/harness`. Any non-zero exit fails the step. S07b's harness provides that program, and its `ci/public/addons-engine.yml` runs this step.
