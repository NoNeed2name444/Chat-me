# Sorta add-on kit

This kit holds the docs and tools for writing, testing and submitting a Sorta add-on. Read the pages in this order:

1. [Getting started](getting-started.md): from the Word count template to an add-on on your phone.
2. [The manifest](manifest.md): every field, the settings file and the board palette.
3. [Permissions](permissions.md): what an add-on can ask for, and what it can never do.
4. [The API](api.md): every call in version 1.
5. [Limits](limits.md): sizes, time and memory.
6. [Review](review.md): every check, with its code and how to fix it.
7. [Testing](testing.md): check, pack and the harness.
8. [Runtime codes](runtime-codes.md): every code a running add-on can get.

The kit's folders:

- `api/` holds the API declarations, their machine copy and the sample ideas.
- `template/` is Word count, an add-on to copy.
- `tools/` holds `check.py`, `pack.py` and the tools behind them. They need Python 3.10 or later and nothing else.
- `harness/` builds a stand-in Sorta that runs add-ons on a laptop.
