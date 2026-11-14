import XCTest
@testable import Sorta

/// The two stored fields S06a adds, openedAt and stoppedAt: read and written
/// under Android's names, left out when unset (so files from before read and
/// write as they did), and changed only by markOpened and setStopped, which
/// leave the time alone, as Android's NoteMarksTest asks.
@MainActor
final class NoteMarksTests: XCTestCase {
    private let id = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!

    private func decode(_ json: String) throws -> Note {
        try JSONDecoder.sorta.decode(Note.self, from: Data(json.utf8))
    }

    private func encode(_ note: Note) throws -> [String: Any] {
        let data = try JSONEncoder.sorta.encode(note)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testBothRoundTripUnderAndroidsNames() throws {
        let note = try decode(#"{"id":"\#(id.uuidString)","title":"Paint the shed green","createdAt":"2026-10-13T12:30:00Z","openedAt":"2026-11-01T09:00:00Z","stoppedAt":12}"#)
        XCTAssertEqual(note.openedAt, Date(timeIntervalSince1970: 1_793_523_600))
        XCTAssertEqual(note.stoppedAt, 12)
        let out = try encode(note)
        XCTAssertEqual(out["openedAt"] as? String, "2026-11-01T09:00:00Z")
        XCTAssertEqual(out["stoppedAt"] as? Int, 12)
        XCTAssertEqual(try JSONDecoder.sorta.decode(Note.self, from: JSONEncoder.sorta.encode(note)), note)
    }

    func testUnsetIsLeftOut() throws {
        let plain = try decode(#"{"id":"\#(id.uuidString)","title":"Buy brushes","createdAt":"2026-10-13T12:30:00Z"}"#)
        XCTAssertNil(plain.openedAt)
        XCTAssertNil(plain.stoppedAt)
        let out = try encode(plain)
        XCTAssertNil(out["openedAt"])
        XCTAssertNil(out["stoppedAt"])
        // a value that isn't a date reads as never opened, and the idea is kept
        let odd = try decode(#"{"id":"\#(id.uuidString)","title":"x","openedAt":"soon"}"#)
        XCTAssertNil(odd.openedAt)
    }

    func testMarksLeaveTheTimeAloneAndAnOlderCopyLeavesThem() throws {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        let made = store.create(title: "Fix the gate latch", body: "Two coats, then")
        let opened = Date(timeIntervalSince1970: 1_793_523_600)
        store.markOpened(made.id, at: opened)
        store.setStopped(made.id, at: 9)
        let after = try XCTUnwrap(store.note(made.id))
        XCTAssertEqual(after.openedAt, opened)
        XCTAssertEqual(after.stoppedAt, 9)
        XCTAssertEqual(after.updatedAt, made.updatedAt, "the time is left alone")
        // the editor's copy from before the marks, saved with a change: the marks stay
        var older = made
        older.title = "Fix the gate latch today"
        store.update(older)
        XCTAssertEqual(store.note(made.id)?.openedAt, opened)
        XCTAssertEqual(store.note(made.id)?.stoppedAt, 9)
        XCTAssertEqual(store.note(made.id)?.title, "Fix the gate latch today")
        store.setStopped(made.id, at: nil)
        XCTAssertNil(store.note(made.id)?.stoppedAt)
        store.waitForSaves()
        let reread = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        XCTAssertEqual(reread.note(made.id)?.openedAt, opened)
        XCTAssertNil(reread.note(made.id)?.stoppedAt)
    }
}
