# Runtime codes

When a call fails while your add-on runs, Sorta gives a code. The harness prints it on the `load` line or in the `result` line, and the app keeps it in the add-on's activity. The codes are the same in the harness, on iPhone and iPad, and on Android. [Review](review.md) lists the codes `check.py` gives for a package; these are the codes for a running add-on.

## Errors your code can catch

A `sorta` call that fails throws a `SortaError`, and its `code` is one of these. If your code doesn't catch it, the call ends with the same code.

| Code | When |
|---|---|
| `permission` | The call needs a permission the add-on doesn't hold, or `sorta.net.fetch` named a host that isn't granted. The error is a `PermissionError`. A card view or a settings handler that tries to change an idea gets this too: only a command may write. |
| `api.args` | An argument is missing, has the wrong type, or is outside what the call takes. |
| `api.notFound` | The idea or folder isn't one the add-on can see. |
| `api.unknown` | The call isn't in this version of the API. |
| `api.load` | The call was made while the add-on's file was loading. Only `sorta.commands.on`, `sorta.cards.on`, `sorta.settings.on`, `sorta.has` and `console.log` work there. |
| `limit.writes` | The call made more than 100 changes. The error is a `WriteLimitError`, and none of the call's changes are saved. |
| `limit.captures` | The call made more than 20 captures. The error is a `WriteLimitError`, and none of the call's changes are saved. |
| `limit.storage` | `sorta.storage.set` would take the add-on's storage past 256 KB. |
| `limit.json` | One call's JSON, in or out, is larger than 1 MB. |
| `net.off` | This build of Sorta has the network for add-ons turned off. |
| `net.https` | The address doesn't start with `https://`. |
| `net.declined` | The person is asked each time, and said no to this host. |
| `net.redirect` | The reply redirected to a host that isn't granted or isn't `https`, or redirected more than 5 times. |
| `net.size` | The request or the reply is larger than 1 MB. |
| `net.time` | The request took longer than 10 seconds. |
| `net.failed` | The request couldn't be sent, or no reply came. |

## How a call can end

Your code can't catch these. The call ends with the code, and none of its changes are saved.

| Code | When |
|---|---|
| `js.error` | Your code threw an error and didn't catch it. An error you throw with a `code` of your own ends the call with that code. |
| `command.unknown` | The manifest lists the command, and no handler was registered for it with `sorta.commands.on`. |
| `card.none` | The manifest has a card view, and no handler was registered with `sorta.cards.on`. |
| `settings.none` | A setting changed, and no handler was registered with `sorta.settings.on`. |
| `result.shape` | The handler returned something other than what the call takes: a command returns `{ message }` of at most 120 characters or nothing, and a card view returns `{ lines }` of at most 3 lines of at most 80 characters. |
| `result.pending` | The handler's Promise never settled: it waits on something that can't happen. |
| `limit.time` | The call ran past its time on [Limits](limits.md). |
| `limit.memory` | The add-on went past 32 MB. |
| `limit.stack` | The calls went too deep, as in a function that calls itself without end. |
| `limit.json` | The value the handler returned is larger than 1 MB as JSON. |
| `limit.batch` | A batch of cards had more than 50 cards, or its 250 ms ran out before this card's turn. |
| `limit.minute` | The add-on has run for 3 seconds in the last minute, so Sorta didn't start the call. |
| `stopped` | Sorta stopped the call: the add-on was turned off, updated, removed or revoked, or a permission was taken away, while the call ran or waited. |

## When the add-on's file loads

The harness prints these on the `load` line. No handler runs after one of them.

| Code | When |
|---|---|
| `load.syntax` | A file has a syntax error. |
| `load.module` | An `import` names a file that isn't in the package, or one that isn't a `.js` file. |
| `load.pending` | The top level of the main file waits on a Promise that never settles. |
| `file.text` | A file isn't UTF-8 text. |
| `api.load` | The top level made a call that only works inside a handler. |
| `js.error` | The top level threw an error. |
| `limit.time` | The top level ran for more than 500 ms. |
| `limit.memory` | Loading the add-on went past 32 MB. |

## Only in the app

| Code | When |
|---|---|
| `addon.off` | The add-on is turned off, paused or revoked, so Sorta didn't start the call. |
| `addon.missing` | The add-on's package is gone from the phone, or no longer matches what was installed. |
| `engine.busy` | Three add-ons are already running, which is as many as Sorta runs at once. |
| `engine.spent` | The add-on's runtime went over a limit earlier and can't run again. The next call starts a fresh one. |
| `engine.failed` | Sorta couldn't start the engine. |
