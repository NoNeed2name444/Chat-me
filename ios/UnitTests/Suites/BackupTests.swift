// A Stethoscore backup, as far as Sorta reads it: the archive written with
// MiniZip and opened again, its manifest first and read, and a file that is
// not a backup, or is a later version's, refused.
//
// The backup checks of Stethoscore's DataIOTests
// (reference/stethoscore-aec2af1c/ios/RedPen/Tests/DataIOTests.swift:327-469)
// that test what Sorta copies, on a made-up sample of ideas: the manifest
// written first (:446-447), the manifest read (:448), the archive opening
// (:459), an Anki file refused (:461-464, with a zip made here in place of
// the Anki fixture) and a later version refused (:465-469). The rest test
// the library's sets, schedules, settings and files, which Sorta leaves out.
// The import itself (IdeaImport) is checked in the app, by
// SortaUnitTests/IdeaImportTests, since NoteStore needs Combine.
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

/// How Stethoscore wrote a backup's parts (LibraryBackup.swift:41-46).
let encoder: JSONEncoder = {
    let e = JSONEncoder()
    e.dateEncodingStrategy = .iso8601
    e.outputFormatting = [.sortedKeys]
    return e
}()

func id(_ n: Int) -> UUID { UUID(uuidString: String(format: "00000000-0000-4000-8000-%012X", n))! }

// a made-up sample: two ideas in a folder inside a folder, one linked to the other
let garden = NoteFolder(id: id(101), name: "Garden", createdAt: Date(timeIntervalSince1970: 1_790_000_000))
let beds = NoteFolder(id: id(102), name: "Raised beds", parentId: garden.id, createdAt: Date(timeIntervalSince1970: 1_790_000_100))
var shed = Note(id: id(1), title: "Paint the shed green", body: "Two coats, after the rain.", folderId: garden.id)
shed.boardX = 120
shed.boardY = -40
var seeds = Note(id: id(2), title: "Seed swap list", body: "Beans, squash, [[Paint the shed green]]", folderId: beds.id)
seeds.links = [shed.id]
let sample = NoteBackup(notes: [shed, seeds], folders: [garden, beds])

let folderURL = FileManager.default.temporaryDirectory.appendingPathComponent("backup-\(UUID().uuidString)", isDirectory: true)
try? FileManager.default.createDirectory(at: folderURL, withIntermediateDirectories: true)

/// A zip of `parts`, each a name inside it and its bytes, written with MiniZip.
func zip(_ parts: [(String, Data)], named name: String) -> URL {
    var entries: [(name: String, file: URL)] = []
    for (index, part) in parts.enumerated() {
        let url = folderURL.appendingPathComponent("\(name)-part\(index)")
        try? part.1.write(to: url)
        entries.append((part.0, url))
    }
    let url = folderURL.appendingPathComponent(name)
    try? MiniZip.write(files: entries, to: url)
    return url
}

var counts = LibraryBackup.Counts()
counts.notes = sample.notes.count
let manifest = LibraryBackup.Manifest(createdAt: Date(timeIntervalSince1970: 1_790_308_800), app: "Test", counts: counts)
let zipURL = zip([
    (LibraryBackup.Name.manifest, (try? encoder.encode(manifest)) ?? Data()),
    (LibraryBackup.Name.notes, (try? encoder.encode(sample)) ?? Data()),
], named: "backup.stethoscorebackup")

var opened: LibraryBackup.Archive?
var openError = ""
do { opened = try LibraryBackup.Archive(url: zipURL) } catch { openError = "\(error)" }
check("archive opens", opened != nil, openError)
let written = (try? Data(contentsOf: zipURL)) ?? Data()
let nameLength = written.count > 30 ? Int(written[26]) | (Int(written[27]) << 8) : 0
let firstName = written.count >= 30 + nameLength ? String(decoding: written[30..<(30 + nameLength)], as: UTF8.self) : ""
check("archive: the manifest is the first entry, so the first bytes say it is a backup",
      firstName == LibraryBackup.Name.manifest, firstName)
check("archive: the manifest is read",
      opened?.manifest.counts.notes == 2 && opened?.manifest.app == "Test" && opened?.manifest == manifest,
      "\(String(describing: opened?.manifest))")

// an Anki package is a zip too, with no manifest in it
let anki = zip([("collection.anki21", Data("SQLite format 3".utf8)), ("media", Data("{}".utf8))], named: "deck.apkg")
var refused: LibraryBackup.Failure?
do { _ = try LibraryBackup.Archive(data: (try? Data(contentsOf: anki)) ?? Data()) }
catch let f as LibraryBackup.Failure { refused = f } catch {}
check("archive: an Anki file is not a backup", refused == .notABackup, "\(String(describing: refused))")

var newer = manifest
newer.version = 99
var tooNew = false
do { try LibraryBackup.check(newer) } catch LibraryBackup.Failure.newerVersion { tooNew = true } catch {}
check("archive: a newer version's backup is refused with a reason", tooNew)
try? FileManager.default.removeItem(at: folderURL)

print(failures.isEmpty ? "\nALL BACKUP TESTS PASS"
                       : "\n\(failures.count) BACKUP TEST FAILURE(S)")
exit(failures.isEmpty ? 0 : 1)
