# Add-ons

Add-ons are small JavaScript packages that add commands, card lines, settings and board palettes to Sorta. `docs/tasks/S07-addons.md` is the design.

| Folder | What it holds |
|---|---|
| `spec/` | `reasons.json` and `review.json`, every reason code. `rules.md`, the order the checks run in, which the kit, iOS and Android share. `directory.md`, the directory's `index.json` and `revoked.json`. |
| `kit/` | The developer kit: docs, the API declarations and their snapshot, the Word count template and the Python tools. The public directory gets a copy. |
| `fixtures/` | Package folders, archive descriptions, API diff pairs and sample pull requests, each with the verdict it must give. |
| `directory/` | The public `sorta-addons` repository's own files, ready to copy. |

The engine, the kit's harness and the app code come with S07b, in `engine/` and `kit/harness/`.

Run every check on a laptop with Python 3.10 or later and nothing else:

```sh
python3 -I -B addons/kit/tools/tests/run.py
```

No `.zip`, `.sortaaddon`, picture, font or binary is ever committed here. The tests build archives as they run, and a test scans this folder for pictures and binaries.
