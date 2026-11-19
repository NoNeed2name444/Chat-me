# Sorta add-ons

This is the directory of add-ons for Sorta, the app for catching ideas and sorting them. Each add-on adds commands, lines on idea cards or a board palette. Every one is free, open source and read by a person before it's listed.

## Install an add-on

Open Sorta's add-on directory in the app and choose an add-on. Sorta shows what it asks for, like reading your ideas or reaching a website. Grant all of it, some or none. Nothing runs until you say yes.

Sorta downloads each add-on from this repository and checks its SHA-256 before opening it. You can turn any add-on off, or take back a permission, at any time.

The directory lists add-ons in alphabetical order, with no ratings, download counts or rankings.

## What add-ons can't do

- Delete your ideas or folders.
- Run in the background, or on their own.
- Reach any website you haven't allowed.
- Charge money or ask for tips.

Each change an add-on makes is one Undo step.

## Report an add-on

If an add-on breaks these rules, misuses your ideas or doesn't work, open an issue and choose Report an add-on. We aim to reply within 7 days.

When an add-on breaks the rules, we add it to `revoked.json`. Sorta turns it off on every phone the next time it checks the directory. An author who keeps breaking the rules has all their add-ons revoked.

## Write an add-on

The developer kit is in `kit/`. Start with `kit/docs/getting-started.md`, which takes you from a working template to your own add-on. CONTRIBUTING.md says how to submit it.

## What's here

- `addons/<id>/<version>/` holds each add-on's source.
- `revoked.json` lists add-ons that are turned off everywhere.
- `kit/` is the developer kit, with its docs and tools.
- `engine/` is the JavaScript engine and the host code the kit's harness builds.

The packed add-ons and `index.json`, which Sorta reads, are on the `index` branch. A workflow builds them from `main` after each merge.

## License

The files in this repository are MIT licensed, as `LICENSE` says. Each add-on has its own license, in its folder.
