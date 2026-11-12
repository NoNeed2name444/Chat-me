import XCTest
@testable import Sorta

/// The idea store on the device (S04, A3). What one store writes, a new one
/// on the same file reads back, every field equal but the dates, which keep
/// whole seconds (JSONEncoder.sorta's ISO 8601). A write lands whole or not
/// at all. A file the store couldn't read is put aside under a new name
/// before anything is written over it. NoteStore publishes with Combine,
/// which the Linux suites don't have, so these run in the app. Android's
/// IdeaStoreTest asks the same of its store.
@MainActor
final class NoteStoreFileTests: XCTestCase {
    private typealias Sample = MadeUpBackup

    /// The file as the store writes it, read here on its own.
    private struct Filed: Decodable {
        var notes: [Note]
        var folders: [NoteFolder]
    }

    private func filed(_ url: URL) throws -> Filed {
        try JSONDecoder.sorta.decode(Filed.self, from: Data(contentsOf: url))
    }

    /// A date as the file keeps it: a whole second, less than one from it.
    private func assertWholeSecond(_ read: Date, of written: Date, _ what: String,
                                   file: StaticString = #filePath, line: UInt = #line) {
        let seconds = read.timeIntervalSince1970
        XCTAssertEqual(seconds, seconds.rounded(.down), "\(what): \(read) is not a whole second", file: file, line: line)
        XCTAssertLessThan(abs(read.timeIntervalSince(written)), 1, "\(what): \(read) for \(written)", file: file, line: line)
    }

    /// Every field equal, the dates to the whole second.
    private func assertSame(_ read: [Note], _ written: [Note], file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(read.map(\.id), written.map(\.id), "the ideas, in order", file: file, line: line)
        for (a, b) in zip(read, written) {
            var dated = a
            dated.createdAt = b.createdAt
            dated.updatedAt = b.updatedAt
            XCTAssertEqual(dated, b, "\(b.title): every field but the dates", file: file, line: line)
            assertWholeSecond(a.createdAt, of: b.createdAt, "\(b.title) createdAt", file: file, line: line)
            assertWholeSecond(a.updatedAt, of: b.updatedAt, "\(b.title) updatedAt", file: file, line: line)
        }
    }

    private func assertSame(_ read: [NoteFolder], _ written: [NoteFolder], file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(read.map(\.id), written.map(\.id), "the folders, in order", file: file, line: line)
        for (a, b) in zip(read, written) {
            var dated = a
            dated.createdAt = b.createdAt
            XCTAssertEqual(dated, b, "\(b.name): every field but the date", file: file, line: line)
            assertWholeSecond(a.createdAt, of: b.createdAt, "\(b.name) createdAt", file: file, line: line)
        }
    }

    func testWhatOneStoreWritesANewOneReadsBack() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let store = NoteStore(fileURL: url)
        // every field set, the dates part way through a second
        let notes = Sample.notes.map { note -> Note in
            var n = note
            n.createdAt = note.createdAt.addingTimeInterval(0.75)
            n.updatedAt = note.updatedAt.addingTimeInterval(0.25)
            return n
        }
        let folders = Sample.folders.map { f -> NoteFolder in
            var g = f
            g.createdAt = f.createdAt.addingTimeInterval(0.5)
            return g
        }
        XCTAssertEqual(store.restore(notes: notes, folders: folders), notes.count)
        // and the ordinary changes, each saved as it is made
        let shelf = store.createFolder(name: "Shelves", parentId: MadeUpBackup.id(103))
        let made = store.create(title: "Fix the gate latch", body: "Needs a new spring.", kind: .page,
                                folderId: shelf.id, at: (x: 12.5, y: -8))
        store.link(made.id, MadeUpBackup.id(4))
        store.setPosition(MadeUpBackup.id(2), x: 44, y: 55.5)
        store.move(MadeUpBackup.id(4), to: MadeUpBackup.id(102))
        var edited = try XCTUnwrap(store.note(MadeUpBackup.id(1)))
        edited.tags.append("blue?")
        store.update(edited)
        store.waitForSaves()

        let reopened = NoteStore(fileURL: url)
        assertSame(reopened.notes, store.notes)
        assertSame(reopened.folders, store.folders)
        XCTAssertEqual(reopened.notes.count, 5)
        XCTAssertEqual(reopened.folders.count, 5)
        XCTAssertEqual(reopened.note(MadeUpBackup.id(3))?.source, Sample.notes[2].source, "a source as written")
        XCTAssertEqual(reopened.path(to: shelf.id).map(\.id), [MadeUpBackup.id(103), shelf.id], "a folder inside a folder")
        XCTAssertTrue(reopened.isLinked(made.id, MadeUpBackup.id(4)), "a link made here")
    }

    func testAWriteLandsWholeOrNotAtAll() throws {
        let folder = try Sample.folder()
        let manager = FileManager.default
        defer {
            try? manager.setAttributes([.posixPermissions: 0o755], ofItemAtPath: folder.path)
            try? manager.removeItem(at: folder)
        }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let store = NoteStore(fileURL: url)
        store.restore(notes: Sample.notes, folders: Sample.folders)
        store.waitForSaves()
        // a write that lands is whole, and leaves nothing beside it
        XCTAssertEqual(try filed(url).notes.map(\.id), Sample.notes.map(\.id))
        XCTAssertEqual(try manager.contentsOfDirectory(atPath: folder.path), ["vignette-notes.json"])
        let before = try Data(contentsOf: url)

        // one that can't land leaves the file as it was
        try manager.setAttributes([.posixPermissions: 0o444], ofItemAtPath: url.path)
        try manager.setAttributes([.posixPermissions: 0o555], ofItemAtPath: folder.path)
        // locked to anyone but root; the simulator runs as the runner's user
        XCTAssertFalse(manager.createFile(atPath: folder.appendingPathComponent("probe").path, contents: Data()),
                       "the locked folder takes a write, so this test proves nothing here")
        let unsaved = store.create(title: "Oil the hinges")
        store.waitForSaves()
        XCTAssertEqual(try Data(contentsOf: url), before, "the file is the one before, byte for byte")
        XCTAssertEqual(NoteStore(fileURL: url).notes.map(\.id), Sample.notes.map(\.id), "and reads whole")
        try manager.setAttributes([.posixPermissions: 0o755], ofItemAtPath: folder.path)
        try manager.setAttributes([.posixPermissions: 0o644], ofItemAtPath: url.path)
        XCTAssertEqual(try manager.contentsOfDirectory(atPath: folder.path), ["vignette-notes.json"], "nothing left half written")

        // the next write lands whole, with what the store held meanwhile
        store.create(title: "Sweep the path")
        store.waitForSaves()
        let after = try filed(url)
        XCTAssertEqual(after.notes.map(\.id), store.notes.map(\.id))
        XCTAssertTrue(after.notes.contains { $0.id == unsaved.id })
        XCTAssertEqual(after.folders.map(\.id), Sample.folders.map(\.id))
    }

    func testAnUnreadableFileIsPutAsideBeforeAnythingIsWritten() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let garbled = Data("{\"notes\": [{\"title\": \"half".utf8)
        try garbled.write(to: url)
        let store = NoteStore(fileURL: url)
        XCTAssertTrue(store.notes.isEmpty)
        XCTAssertTrue(store.folders.isEmpty)
        let aside = try asideCopies(in: folder, named: "notes-unreadable-")
        XCTAssertEqual(aside.count, 1, "put aside under a new name")
        XCTAssertEqual(try aside.first.map { try Data(contentsOf: $0) }, garbled, "as it was")

        store.create(title: "Start again")
        store.waitForSaves()
        XCTAssertEqual(try filed(url).notes.map(\.title), ["Start again"])
        XCTAssertEqual(try aside.first.map { try Data(contentsOf: $0) }, garbled, "the copy outlives the write")
        XCTAssertEqual(try asideCopies(in: folder, named: "notes-unreadable-").count, 1)
    }

    func testAPartlyUnreadableFileKeepsTheRestAndACopy() throws {
        let folder = try Sample.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        // the second idea's title is a number, which this version can't read
        let text = """
        {"folders": [{"id": "\(MadeUpBackup.id(101).uuidString)", "name": "Garden"}],
         "notes": [{"id": "\(MadeUpBackup.id(1).uuidString)", "title": "Paint the shed green"},
                   {"id": "\(MadeUpBackup.id(2).uuidString)", "title": 42}]}
        """
        try Data(text.utf8).write(to: url)
        let store = NoteStore(fileURL: url)
        XCTAssertEqual(store.notes.map(\.id), [MadeUpBackup.id(1)], "the idea it can read")
        XCTAssertEqual(store.folders.map(\.name), ["Garden"])
        let aside = try asideCopies(in: folder, named: "notes-partly-unreadable-")
        XCTAssertEqual(aside.count, 1)
        XCTAssertEqual(try aside.first.map { try Data(contentsOf: $0) }, Data(text.utf8), "the whole file, as it was")
    }

    private func asideCopies(in folder: URL, named prefix: String) throws -> [URL] {
        try FileManager.default.contentsOfDirectory(atPath: folder.path)
            .filter { $0.hasPrefix(prefix) && $0.hasSuffix(".json") }
            .map { folder.appendingPathComponent($0) }
    }
}
