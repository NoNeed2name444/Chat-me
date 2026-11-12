import Foundation

// The editor's Read view (NoteMarkdown): Markdown into blocks, and
// [[Title]] links into links the view opens.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let text = """
# Plans
A first line
and its second.

- seeds
  - beans
1. dig
> a quote
---
```
let x = 1
```
"""
let blocks = NoteMarkdown.blocks(in: text)
check("blocks: heading, paragraph, bullets, number, quote, rule, code",
      blocks == [.heading(1, "Plans"), .paragraph("A first line and its second."),
                 .bullet(0, "\u{2022}", "seeds"), .bullet(1, "\u{2022}", "beans"),
                 .bullet(0, "1.", "dig"), .quote("a quote"), .rule, .code("let x = 1")], "\(blocks)")
check("blocks: nothing written is no block", NoteMarkdown.blocks(in: " \n\n").isEmpty)
check("blocks: an unclosed code block keeps its lines",
      NoteMarkdown.blocks(in: "```\na\nb") == [.code("a\nb")])
check("blocks: a hash with no space is words", NoteMarkdown.blocks(in: "#tag") == [.paragraph("#tag")])

let linked = NoteMarkdown.wikiLinksAsMarkdown("See [[Holiday plans]] and [[Bike|the bike]].")
check("links: a title becomes a link to it, shown as written",
      linked == "See [Holiday plans](vignette-note:///Holiday%20plans) and [the bike](vignette-note:///Bike).", linked)
check("links: an unclosed [[ is left as typed", NoteMarkdown.wikiLinksAsMarkdown("a [[b") == "a [[b")
let url = URL(string: "vignette-note:///Holiday%20plans")!
check("links: the link names its title again", NoteMarkdown.title(from: url) == "Holiday plans",
      NoteMarkdown.title(from: url))

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
