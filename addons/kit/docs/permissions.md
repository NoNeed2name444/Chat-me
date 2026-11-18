# Permissions

Your add-on asks for permissions in its manifest, and the person installing it decides what to grant. They can grant all of them, some or none, and take any back later. Plan for each case.

| Permission | What it lets your add-on do |
|---|---|
| `ideas.read` | Read every idea and folder: titles, text, links, tags, folder names and folder words. |
| `ideas.read.folder` | Read one folder and the folders inside it. The person picks the folder when they grant it. |
| `ideas.write` | Capture new ideas, which land in To sort. Tag and untag, link and unlink, and file an idea into a folder. |
| `commands` | Add up to 5 commands to Sorta's command list. |
| `cards` | Add up to 3 short lines to each idea's card. |
| `board.palette` | Recolour the 2D board from a palette file. |
| `net` | Make HTTPS requests to the hosts in `hosts`. The person grants each host on its own. |
| `ai` | Reserved for Sorta's AI features, which a later update defines. |

Ask for `ideas.read` or `ideas.read.folder`, never both. Your settings and your own storage need no permission.

## What an add-on can never do

- Delete an idea or a folder.
- Run in the background, on a timer or on every capture.
- Read files, the clipboard, the camera or the location, or send notifications.
- Draw or colour anything itself. Sorta draws card lines and settings in its own style.

Each call's writes are one Undo step, so the person can take back everything one command did.

## Check before you call

A call that needs a permission your add-on doesn't hold fails with a `PermissionError`. Check first with `sorta.has()`, and say what's missing:

```js
sorta.commands.on("rhyme", async (event) => {
  if (!sorta.has("net")) {
    return { message: "Turn on network access for Rhymes in Settings" };
  }
  // ...
});
```

When the person takes a permission back, it applies at once. A call that's running stops.

## Reading and the network together

If your add-on asks for `ideas.read` or `ideas.read.folder` together with `net`, Sorta tells the person it could send their ideas to those hosts. Say in your README exactly what you send, and to which host.
