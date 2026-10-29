import XCTest
@testable import Sorta

/// The import's check (S04, A4; the first half of the owner's gate of
/// 23 Oct): a made-up `.stethoscorebackup` brought in, and every idea, link,
/// folder, folder inside a folder and board position there, each idea's
/// `source` as written, and the counts IdeaImport returns. A second import
/// adds nothing, an idea or folder already here stays as it is, and each
/// refusal changes nothing. Android's IdeaImportTest asks the same of its
/// import.
@MainActor
final class IdeaImportTests: XCTestCase {
    private typealias Sample = MadeUpBackup

    private func id(_ n: Int) -> UUID { Sample.id(n) }

    func testEveryIdeaLinkFolderAndPlaceCarriesOver() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let store = NoteStore(fileURL: url)
        // the other parts can't be read, so an import that read them would fail
        let backup = try Sample.write(Sample.parts(), in: folder)

        let counts = try IdeaImport.merge(backupAt: backup, into: store)
        XCTAssertEqual(counts, IdeaImport.Counts(ideas: 4, folders: 4, links: Sample.links, placed: Sample.placed))
        XCTAssertEqual(counts.links, 4, "1 + 2 + 1 hand-made links; the [[Title]] in a body is not one")
        XCTAssertEqual(counts.placed, 3, "the seed list sits at the centre")

        for written in Sample.notes {
            let read = try XCTUnwrap(store.note(written.id), written.title)
            XCTAssertEqual(read, written, "\(written.title): every field")
            XCTAssertEqual(read.links, written.links, "\(written.title): its links")
            XCTAssertEqual(read.folderId, written.folderId, "\(written.title): its folder")
            XCTAssertEqual(read.boardX, written.boardX, "\(written.title): across the board")
            XCTAssertEqual(read.boardY, written.boardY, "\(written.title): down the board")
            XCTAssertEqual(read.source, written.source, "\(written.title): its source, as written")
        }
        XCTAssertEqual(store.notes.map(\.id), Sample.notes.map(\.id))
        XCTAssertEqual(store.folders, Sample.folders, "every folder")
        XCTAssertEqual(store.path(to: id(102)).map(\.id), [id(101), id(102)], "a folder inside a folder")
        XCTAssertEqual(store.path(to: id(104)).map(\.id), [id(103), id(104)])
        XCTAssertTrue(store.isLinked(id(3), id(2)) && store.isLinked(id(3), id(1)) && store.isLinked(id(2), id(1)))
        XCTAssertEqual(store.note(id(4))?.links, [id(50)], "a link to an idea the backup doesn't hold is kept")
        XCTAssertEqual(store.note(id(2))?.source?.setName, "Spanish verbs")
        XCTAssertEqual(store.note(id(3))?.source?.page, 3)
        XCTAssertNil(store.note(id(1))?.source, "an idea written by hand has none")

        // and it was saved: a new store on the same file has it all
        store.waitForSaves()
        let reopened = NoteStore(fileURL: url)
        XCTAssertEqual(reopened.notes, Sample.notes)
        XCTAssertEqual(reopened.folders, Sample.folders)
    }

    func testASecondImportAddsNothing() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let store = NoteStore(fileURL: url)
        let backup = try Sample.write(Sample.parts(), in: folder)
        XCTAssertEqual(try IdeaImport.merge(backupAt: backup, into: store).ideas, 4)
        store.waitForSaves()
        let saved = try Data(contentsOf: url)

        XCTAssertEqual(try IdeaImport.merge(backupAt: backup, into: store), IdeaImport.Counts())
        store.waitForSaves()
        XCTAssertEqual(store.notes, Sample.notes)
        XCTAssertEqual(store.folders, Sample.folders)
        XCTAssertEqual(try Data(contentsOf: url), saved, "nothing written")
    }

    func testAnIdeaOrFolderAlreadyHereStaysAsItIs() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        let mine = Note(id: id(1), title: "Paint the shed blue", boardX: 9, boardY: 9)
        let bell = Note(id: id(50), title: "Ring the bell twice")
        let kitchen = NoteFolder(id: id(103), name: "Cooking")
        store.restore(notes: [mine, bell], folders: [kitchen])

        let counts = try IdeaImport.merge(backupAt: try Sample.write(Sample.parts(), in: folder), into: store)
        XCTAssertEqual(counts, IdeaImport.Counts(ideas: 3, folders: 3, links: 4, placed: 2),
                       "the shed is here already: its place is not counted, the links of the three added are")
        XCTAssertEqual(store.note(id(1)), mine, "the idea here stays as it is")
        XCTAssertEqual(store.folder(id(103)), kitchen, "and so does the folder")
        XCTAssertEqual(store.notes.count, 5)
        XCTAssertEqual(store.folders.count, 4)
        XCTAssertTrue(store.isLinked(id(4), id(50)), "a link to an idea already here joins it")
        XCTAssertEqual(store.path(to: id(104)).map(\.name), ["Cooking", "Bread"], "an added folder sits in the one here")
    }

    /// Each refusal, with the store, in memory and on disk, as it was.
    func testEachRefusalChangesNothing() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let store = NoteStore(fileURL: url)
        store.restore(notes: [Note(id: id(60), title: "Water the plants", boardX: 1, boardY: 2)],
                      folders: [NoteFolder(id: id(160), name: "Home")])
        store.waitForSaves()
        let notes = store.notes, folders = store.folders
        let saved = try Data(contentsOf: url)

        let notesPart = (LibraryBackup.Name.notes, Sample.notesFile())
        let cases: [(String, URL, LibraryBackup.Failure)] = [
            ("a file that isn't a zip", try write(Data("just some words, not a zip".utf8), in: folder),
             .notABackup),
            ("a zip with no manifest", try Sample.write([notesPart] + Sample.otherParts, in: folder, named: "a.zip"),
             .notABackup),
            ("a manifest that can't be read",
             try Sample.write([(LibraryBackup.Name.manifest, Data("{\"format\": ".utf8)), notesPart], in: folder,
                              named: "b.zip"), .notABackup),
            ("another app's manifest",
             try Sample.write([(LibraryBackup.Name.manifest, Sample.manifest(format: "another-backup")), notesPart],
                              in: folder, named: "c.zip"), .notABackup),
            ("a later version",
             try Sample.write([(LibraryBackup.Name.manifest, Sample.manifest(version: 2)), notesPart], in: folder,
                              named: "d.zip"), .newerVersion),
            ("no notes.json",
             try Sample.write([(LibraryBackup.Name.manifest, Sample.manifest())] + Sample.otherParts, in: folder,
                              named: "e.zip"), .unreadable),
            ("a notes.json that can't be read",
             try Sample.write([(LibraryBackup.Name.manifest, Sample.manifest()),
                               (LibraryBackup.Name.notes, Data("{\"notes\": [".utf8))], in: folder, named: "f.zip"),
             .unreadable),
            ("a notes.json of another shape",
             try Sample.write([(LibraryBackup.Name.manifest, Sample.manifest()),
                               (LibraryBackup.Name.notes, Data("[1, 2, 3]".utf8))], in: folder, named: "g.zip"),
             .unreadable),
            ("no file at all", folder.appendingPathComponent("gone.stethoscorebackup"), .unreadable),
        ]
        for (what, backup, expected) in cases {
            XCTAssertThrowsError(try IdeaImport.merge(backupAt: backup, into: store), what) { error in
                XCTAssertEqual(error as? LibraryBackup.Failure, expected, what)
            }
            store.waitForSaves()
            XCTAssertEqual(store.notes, notes, "\(what): the ideas as they were")
            XCTAssertEqual(store.folders, folders, "\(what): the folders as they were")
            XCTAssertEqual(try Data(contentsOf: url), saved, "\(what): the file as it was")
        }
    }

    private func write(_ data: Data, in folder: URL) throws -> URL {
        let url = folder.appendingPathComponent("file-\(UUID().uuidString).stethoscorebackup")
        try data.write(to: url)
        return url
    }
}
