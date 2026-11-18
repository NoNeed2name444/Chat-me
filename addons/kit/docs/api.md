# The API

Version 1 of the add-on API is small. Calls that read or change ideas return a Promise, and everything that goes in or out is plain JSON. `kit/api/sorta-addon-v1.d.ts` declares every call with its types, so an editor that reads TypeScript can check your code as you type.

## Version and permissions

| Call | Returns |
|---|---|
| `sorta.api` | `{ major: 1, minor: 0 }` |
| `sorta.has(name)` | Whether a permission is granted, like `has("net")`, or whether this Sorta has a call, like `has("ideas.capture")`. It returns at once. |

## Ideas

Reading needs `ideas.read` or `ideas.read.folder`. With `ideas.read.folder`, your add-on sees only the folder the person chose and the folders inside it.

| Call | What it does |
|---|---|
| `sorta.ideas.list({ folder, kind, limit, cursor })` | One page of ideas. Each option is optional. `folder: null` means the ideas in To sort. `limit` is 50 unless you set it, and at most 200. Pass the page's `cursor` to get the next page. It's `null` on the last page. |
| `sorta.ideas.get(id)` | One idea, or `null` if it's gone or out of reach. |
| `sorta.ideas.search(text, { limit })` | Ideas that match, from Sorta's own search. `limit` is 20 unless you set it, and at most 50. |
| `sorta.folders.list()` | Every folder: `id`, `name`, `parent` and up to five `words`. |

An idea looks like this. Times are ISO 8601 in UTC.

```json
{
  "id": "idea-01",
  "kind": "idea",
  "title": "Basil on the sill",
  "text": "Move the basil to the east window so it gets the morning sun.",
  "tags": ["herbs"],
  "links": ["idea-02"],
  "folder": "folder-herbs",
  "createdAt": "2026-11-01T08:00:00Z",
  "updatedAt": "2026-11-02T09:15:00Z"
}
```

`kind` is `"idea"` or `"page"`. `folder` is `null` while the idea waits in To sort.

## Changing ideas

These calls need `ideas.write`. None of them deletes anything.

| Call | What it does |
|---|---|
| `sorta.ideas.capture({ title, text })` | Adds an idea to To sort and returns `{ id }`. `title` is optional. |
| `sorta.ideas.tag(id, tags)` and `untag(id, tags)` | Adds or removes tags. |
| `sorta.ideas.link(a, b)` and `unlink(a, b)` | Links two ideas, or removes the link. |
| `sorta.ideas.file(id, folder)` | Moves an idea into a folder, or back to To sort with `null`. |

Sorta saves a call's changes together when the call ends, as one Undo step. One call may make at most 100 changes and 20 captures. Past either limit, the call fails with `WriteLimitError` and changes nothing. If an idea is gone by the time Sorta saves, Sorta skips it.

## Commands

```js
sorta.commands.on("count", async (event) => {
  return { message: `${event.ideas.length} ideas chosen` };
});
```

Needs `commands`. The id is one from `contributes.commands`. `event.ideas` holds the ids of the ideas the person had chosen, and it's empty if they chose none. Return `{ message }` of at most 120 characters, and Sorta shows it quietly, or return nothing.

## Card views

```js
sorta.cards.on(async (idea) => {
  return { lines: [`${idea.tags.length} tags`] };
});
```

Needs `cards`, with `ideas.read` or `ideas.read.folder`. Your handler gets only ideas that permission covers. With `ideas.read.folder`, those are the ideas in the granted folder and the folders inside it, and other cards show no line. Return at most 3 lines of at most 80 characters each. Sorta works the lines out ahead of time and draws them in its own type and colours.

## Settings

| Call | What it does |
|---|---|
| `sorta.settings.get()` | Your settings by key, like `{ "titles": false }`. |
| `sorta.settings.on(handler)` | Runs your handler with the new settings after the person changes one. |

[The manifest](manifest.md) describes the settings file.

## Storage

Your add-on's own small store, at most 256 KB in all. It needs no permission.

| Call | What it does |
|---|---|
| `sorta.storage.get(key)` | The value stored under the key, or `null`. |
| `sorta.storage.set(key, value)` | Stores any JSON value. |
| `sorta.storage.remove(key)` | Removes the key. |

## Network

```js
const reply = await sorta.net.fetch("https://api.example.org/v1/rhymes?word=basil");
```

Needs `net`, and only reaches hosts in your manifest's `hosts` that the person granted. The options are `method`, which is `GET`, `POST`, `PUT`, `PATCH` or `DELETE`, `headers` and `body`, which is text. The reply is `{ status, url, headers, body }`.

- HTTPS only. Sorta follows redirects only within your granted hosts.
- Sorta drops `Cookie` from the headers you send and `Set-Cookie` from the reply's, so no cookie reaches your add-on or leaves it.
- No cookies.
- At most 1 MB each way, and 10 seconds.

Write each address in full in your code, like `"https://api.example.org/..."`. The directory's review checks each literal host against your manifest.

## Logging

`console.log(...values)` writes to your add-on's own log on the phone, which keeps the last 200 lines. It never reaches Sorta's own logs.

## Errors

Every error a call gives has a `code`, the same on every phone.

| Error | When |
|---|---|
| `PermissionError` | The call needs a permission your add-on doesn't hold. Its `permission` names it. When `net` refuses a host, its `host` names the host. |
| `WriteLimitError` | One call made more than 100 changes or 20 captures. |
| `SortaError` | Every other error. |

## Not in version 1

No deleting, no hooks that run on every capture, no timers that outlive a call and no files, clipboard, location, camera, notifications or background runs. Your add-on runs only when the person uses something it added.

## Keeping add-ons working

A version 1.x of the API only adds to this page. `kit/api/v1.json` is the machine copy of the declarations, and `kit/tools/api_diff.py` fails any change that removes, renames or narrows a call. A version 2 would ship beside version 1, which keeps working for at least 12 months.
