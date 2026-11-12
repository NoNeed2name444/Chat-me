import XCTest
@testable import Sorta

/// What the Sort screen asks of the store beyond S04 (S05): Undo after a
/// delete puts the idea back where it stood with the links to it, and the
/// screen learns when the file couldn't be read at launch, so it can say so
/// once. In the app, since NoteStore publishes with Combine.
@MainActor
final class IdeasStoreTests: XCTestCase {
    private func freshStore() throws -> (NoteStore, URL) {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        return (NoteStore(fileURL: url), url)
    }

    func testUndoAfterADeletePutsTheIdeaBackWithItsLinks() throws {
        let (store, url) = try freshStore()
        let shed = store.create(title: "Paint the shed")
        let gate = store.create(title: "Oil the gate")
        let bike = store.create(title: "A bell for the bike")
        store.link(shed.id, gate.id)
        store.link(bike.id, gate.id)
        let before = store.notes

        let removal = try XCTUnwrap(store.remove(gate.id), "a removal for an idea that is there")
        XCTAssertNil(store.note(gate.id), "deleted")
        XCTAssertFalse(store.note(shed.id)?.links.contains(gate.id) ?? true, "the link to it went with it")

        store.undo(removal)
        XCTAssertEqual(store.notes, before, "back where it stood, with both links to it")
        store.undo(removal)
        XCTAssertEqual(store.notes, before, "a second Undo changes nothing")

        store.waitForSaves()
        XCTAssertEqual(NoteStore(fileURL: url).notes.map(\.id), before.map(\.id), "and saved")
    }

    /// Ideas made one after another go out on the spiral, and each to the
    /// nearest place where its card covers no other on the 2D board (S05b).
    func testIdeasMadeInARowCoverNoCardOnTheBoard() throws {
        let (store, _) = try freshStore()
        let made = (1...12).map { store.create(title: "Idea \($0)") }
        let w = Double(IdeaBoardDrop.tallest.width) + 8, h = Double(IdeaBoardDrop.tallest.height) + 8
        for i in made.indices {
            for j in made.indices where j > i {
                let a = made[i], c = made[j]
                XCTAssertTrue(abs(a.boardX - c.boardX) >= w - 1e-9 || abs(a.boardY - c.boardY) >= h - 1e-9,
                              "\(a.title) at (\(a.boardX), \(a.boardY)) and \(c.title) at (\(c.boardX), \(c.boardY)) are apart")
            }
        }
        XCTAssertEqual(made[0].boardX, 0); XCTAssertEqual(made[0].boardY, 0)
        XCTAssertTrue(made.allSatisfy { ($0.boardX * $0.boardX + $0.boardY * $0.boardY).squareRoot() < 1000 },
                      "the ring stays near the centre")
    }

    func testRemovingAnIdeaThatIsNotThereGivesNothing() throws {
        let (store, _) = try freshStore()
        XCTAssertNil(store.remove(UUID()))
    }

    func testUndoKeepsLinksOnlyFromIdeasStillThere() throws {
        let (store, _) = try freshStore()
        let shed = store.create(title: "Paint the shed")
        let gate = store.create(title: "Oil the gate")
        store.link(shed.id, gate.id)
        let removal = try XCTUnwrap(store.remove(gate.id))
        store.delete(shed.id)
        store.undo(removal)
        XCTAssertEqual(store.notes.map(\.id), [gate.id], "the idea is back, and nothing else")
    }

    func testAFreshFileHasNoReadProblem() throws {
        let (store, _) = try freshStore()
        XCTAssertNil(store.readProblem)
    }

    func testAnUnreadableFileIsReported() throws {
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        try Data("{\"notes\": [{\"title\": \"half".utf8).write(to: url)
        XCTAssertEqual(NoteStore(fileURL: url).readProblem, .unreadable)
    }

    func testAPartlyUnreadableFileIsReported() throws {
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("vignette-notes.json")
        let text = """
        {"notes": [{"id": "\(MadeUpBackup.id(1).uuidString)", "title": "Paint the shed green"},
                   {"id": "\(MadeUpBackup.id(2).uuidString)", "title": 42}]}
        """
        try Data(text.utf8).write(to: url)
        XCTAssertEqual(NoteStore(fileURL: url).readProblem, .partlyUnreadable)
    }

    func testAFolderWithNoNameReadsAsUntitled() throws {
        let json = Data(#"{"id": "00000000-0000-4000-8000-000000000065"}"#.utf8)
        XCTAssertEqual(try JSONDecoder.sorta.decode(NoteFolder.self, from: json).name, "Untitled folder")
    }
}
