# Limits

Sorta keeps add-ons off the thread that draws the screen, and gives each one firm limits, so no add-on can slow Sorta down. These are the numbers. The check enforces the package limits, and the app enforces the rest while your add-on runs.

## The package

| What | Limit |
|---|---|
| The `.sortaaddon` file | 512 KB |
| All files, unpacked | 1 MB |
| Files in the package | 200 |
| The `main` file | 256 KB |
| Folder depth | 2 |
| A file name | 255 bytes |
| Any one file, or the whole package, compared with its packed size | 100 times |

## Running

| What | Limit |
|---|---|
| A command | 500 ms of running time, or 10 seconds in all counting network waits |
| A card view | 20 ms per card, worked out in batches of at most 50 cards and 250 ms |
| A settings handler | 100 ms |
| Running time per add-on | 3 seconds in any minute |
| Memory | 32 MB per add-on |
| JSON in or out of one call | 1 MB |

Time spent waiting for the network doesn't count as running time.

## Changes, storage and the network

| What | Limit |
|---|---|
| Changes in one call | 100, plus at most 20 captures |
| A command's message | 120 characters |
| A card view | 3 lines of at most 80 characters |
| Storage | 256 KB per add-on |
| One network request | 1 MB each way, and 10 seconds |
| The add-on's log | the last 200 lines |

## When an add-on goes over

Sorta stops the call and saves none of its changes. If Sorta stops an add-on for its limits 3 times in 24 hours, it pauses the add-on until the person turns it back on.

If Sorta quits unexpectedly while an add-on is running, it starts next time with add-ons turned off, and that add-on stays paused.

## Keeping inside them

- Work through ideas a page at a time with `sorta.ideas.list` and its `cursor`.
- Keep card views to simple counting and text. Sorta works them out ahead of time, never while drawing.
- Save state you need later with `sorta.storage`, and keep it small.
