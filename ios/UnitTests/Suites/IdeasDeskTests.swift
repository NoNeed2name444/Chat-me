import Foundation

// The Sort screen's rules (IdeasDesk): capture's guard, a row's line, the
// chooser's rows in the store's order, and the UI tests' launch arguments.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

// capture
check("captured: words are kept, trimmed", IdeasDesk.captured("  Paint the shed  \n") == "Paint the shed")
check("captured: an empty field saves nothing", IdeasDesk.captured("") == nil)
check("captured: spaces and returns alone save nothing", IdeasDesk.captured(" \n\t ") == nil)
check("captured: words inside are left as typed", IdeasDesk.captured("a  b") == "a  b")

// a row's line
check("first line: the title's first line", IdeasDesk.firstLine(title: "Seed list\nbeans", body: "x") == "Seed list")
check("first line: a blank title gives the body's first line with words",
      IdeasDesk.firstLine(title: "  ", body: "\n\n  Two coats \nthen dry") == "Two coats")
check("first line: no words at all is nil", IdeasDesk.firstLine(title: "", body: " \n ") == nil)

// the chooser
let garden = UUID(), beds = UUID(), kitchen = UUID()
let outline: [IdeasDesk.Place] = [
    .init(id: garden, name: "Garden", depth: 0, parentId: nil),
    .init(id: beds, name: "Raised beds", depth: 1, parentId: garden),
    .init(id: kitchen, name: "Kitchen", depth: 0, parentId: nil),
]
let rows = IdeasDesk.chooserRows(outline)
check("chooser: None first, the folders in the store's order, New folder last",
      rows == [.none,
               .folder(id: garden, name: "Garden", depth: 0, inside: nil),
               .folder(id: beds, name: "Raised beds", depth: 1, inside: "Garden"),
               .folder(id: kitchen, name: "Kitchen", depth: 0, inside: nil),
               .newFolder], "\(rows)")
check("chooser: with no folders, None and New folder",
      IdeasDesk.chooserRows([]) == [.none, .newFolder])

// the empty state
check("empty: no ideas and no folders", IdeasDesk.isEmpty(ideas: 0, folders: 0))
check("empty: a folder alone is listed, not the empty state", !IdeasDesk.isEmpty(ideas: 0, folders: 1))
check("empty: an idea alone is listed", !IdeasDesk.isEmpty(ideas: 1, folders: 0))

// the launch arguments
let tmp = URL(fileURLWithPath: "/tmp/app", isDirectory: true)
let store = IdeasDesk.storeURL(arguments: ["Sorta", "-SortaStore", "test-1_A"], temporary: tmp)
check("store: a name gives a file in the temporary folder",
      store?.path == "/tmp/app/sorta-ui-stores/test-1_A/vignette-notes.json", "\(String(describing: store))")
check("store: the same name gives the same file",
      IdeasDesk.storeURL(arguments: ["-SortaStore", "test-1_A"], temporary: tmp) == store)
check("store: none without the argument", IdeasDesk.storeURL(arguments: ["Sorta"], temporary: tmp) == nil)
check("store: none with the argument last and no name",
      IdeasDesk.storeURL(arguments: ["-SortaStore"], temporary: tmp) == nil)
check("store: a name that climbs out is refused",
      IdeasDesk.storeURL(arguments: ["-SortaStore", "../x"], temporary: tmp) == nil)
check("store: a path is refused", IdeasDesk.storeURL(arguments: ["-SortaStore", "a/b"], temporary: tmp) == nil)
check("store: a non-ASCII name is refused", !IdeasDesk.isPlainName("caf\u{E9}"))
let bytes = Data([0x50, 0x4B, 0x03, 0x04, 0xFF])
check("import: the file's bytes from base64",
      IdeasDesk.importFile(arguments: ["-SortaImportFile", bytes.base64EncodedString()]) == bytes)
check("import: none without the argument", IdeasDesk.importFile(arguments: []) == nil)
check("import: none when it isn't base64", IdeasDesk.importFile(arguments: ["-SortaImportFile", "%%%"]) == nil)
check("notices: stay only with the argument",
      IdeasDesk.noticesStay(arguments: ["-SortaNoticesStay"]) && !IdeasDesk.noticesStay(arguments: []))

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
