import XCTest
@testable import Sorta

/// S06c A3, A5, A6: the store's split, join and collision, their links, and
/// Undo putting back exactly what was there (Android's IdeaBookReshapeTest
/// asks the same of IdeaBook). Made-up ideas, nothing medical.
@MainActor
final class IdeasReshapeStoreTests: XCTestCase {
    private func freshStore() throws -> NoteStore {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        return NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
    }

    func testSplitInTheBodyKeepsTheFolderAndLinksFollowTheWords() throws {
        let store = try freshStore()
        let trips = store.createFolder(name: "Trips")
        let plan = store.create(title: "Holiday plans",
                                body: "Book the train to [[Harbour]]. Pack light.\nAsk [[Neighbour]] to water the beans.",
                                folderId: trips.id)
        let harbour = store.create(title: "Harbour")
        let neighbour = store.create(title: "Neighbour")
        let kite = store.create(title: "Kite")
        let bike = store.create(title: "Bike bell")
        store.link(plan.id, kite.id)
        store.link(bike.id, plan.id)
        let before = store.notes

        let done = try XCTUnwrap(store.split(plan.id, after: 2))
        let first = try XCTUnwrap(store.note(plan.id))
        let secondID = try XCTUnwrap(done.added.first)
        let second = try XCTUnwrap(store.note(secondID))
        XCTAssertEqual(first.title, "Holiday plans")
        XCTAssertEqual(first.body, "Book the train to [[Harbour]].")
        XCTAssertEqual(second.title, "Pack light.", "the next sentence is the second's title")
        XCTAssertEqual(second.body, "Ask [[Neighbour]] to water the beans.")
        XCTAssertEqual(second.folderId, trips.id, "each half keeps the folder")
        XCTAssertEqual(store.notes.firstIndex(of: second), store.notes.firstIndex(of: first).map { $0 + 1 })
        XCTAssertTrue(store.outgoing(of: plan.id).contains(harbour.id), "the first's [[Harbour]] stays with it")
        XCTAssertFalse(store.outgoing(of: plan.id).contains(neighbour.id), "the first lost [[Neighbour]]")
        XCTAssertTrue(store.outgoing(of: secondID).contains(neighbour.id), "the second's [[Neighbour]] went with it")
        XCTAssertEqual(first.links, [kite.id], "the first keeps its hand link")
        XCTAssertTrue(second.links.contains(kite.id), "the second has it too")
        XCTAssertTrue(store.isLinked(bike.id, plan.id) && store.isLinked(bike.id, secondID),
                      "an idea that linked by hand links to both halves")
        XCTAssertEqual(done.result, plan.id)

        store.undo(done)
        XCTAssertEqual(store.notes, before, "Undo puts it back as one, exactly")
    }

    func testSplitInTheTitleKeepsWhatNamedIt() throws {
        let store = try freshStore()
        let shed = store.create(title: "Paint the shed. Then the fence!", body: "Blue for both.")
        let list = store.create(title: "Weekend list", body: "Start with [[Paint the shed. Then the fence!]]")
        let before = store.notes
        let done = try XCTUnwrap(store.split(shed.id, after: 1))
        XCTAssertEqual(store.note(shed.id)?.title, "Paint the shed.")
        let second = try XCTUnwrap(done.added.first.flatMap { store.note($0) })
        XCTAssertEqual(second.title, "Then the fence!")
        XCTAssertEqual(second.body, "Blue for both.")
        XCTAssertTrue(store.isLinked(shed.id, list.id), "the list that named the old title is still linked")
        store.undo(done)
        XCTAssertEqual(store.notes, before, "Undo puts it back exactly")
    }

    func testACutOutsideTheSentencesIsRefused() throws {
        let store = try freshStore()
        let one = store.create(title: "One line only")
        XCTAssertNil(store.split(one.id, after: 1))
        XCTAssertNil(store.split(one.id, after: 0))
        XCTAssertNil(store.split(UUID(), after: 1))
    }

    func testJoinPutsTheTextsInOrderAndMergesLinksOnce() throws {
        let store = try freshStore()
        let garden = store.createFolder(name: "Garden")
        let beans = store.create(title: "Beans", body: "By the fence.", folderId: garden.id)
        let trellis = store.create(title: "Trellis", body: "Bamboo [[Canes]].")
        let rain = store.create(title: "Rain barrel")
        let canes = store.create(title: "Canes")
        let pond = store.create(title: "Pond")
        let bike = store.create(title: "Bike bell")
        let shed = store.create(title: "Shed", body: "See [[Trellis]]")
        store.link(beans.id, rain.id)
        store.link(beans.id, canes.id)
        store.link(trellis.id, rain.id)
        store.link(trellis.id, pond.id)
        store.link(bike.id, trellis.id)
        store.link(bike.id, beans.id)
        let before = store.notes

        let done = try XCTUnwrap(store.join(beans.id, with: trellis.id))
        let joined = try XCTUnwrap(store.note(beans.id))
        XCTAssertNil(store.note(trellis.id), "the second is gone")
        XCTAssertEqual(joined.title, "Beans")
        XCTAssertEqual(joined.body, "By the fence.\n\nTrellis\n\nBamboo [[Canes]].", "texts in order")
        XCTAssertEqual(joined.links, [rain.id, canes.id, pond.id, shed.id],
                       "links merged, none twice; the bike already links here; the shed's [[Trellis]] becomes a hand link")
        XCTAssertFalse(store.notes.contains { $0.links.contains(trellis.id) }, "hand links to the second went with it")
        XCTAssertEqual(joined.folderId, garden.id)

        store.undo(done)
        XCTAssertEqual(store.notes, before, "Undo splits them back exactly")
    }

    func testCollideMakesAnIdeaInToSortLinkedToBoth() throws {
        let store = try freshStore()
        let garden = store.createFolder(name: "Garden")
        let kite = store.create(title: "Kite", folderId: garden.id)
        let bell = store.create(title: "Bike bell")
        let before = store.notes
        XCTAssertNil(store.collide(kite.id, bell.id, text: "  \n "), "nothing written, nothing made")
        let done = try XCTUnwrap(store.collide(kite.id, bell.id, text: "A kite with a bell\nIt rings in the wind."))
        let made = try XCTUnwrap(store.note(done.result))
        XCTAssertEqual(made.title, "A kite with a bell")
        XCTAssertEqual(made.body, "It rings in the wind.")
        XCTAssertNil(made.folderId, "in To sort")
        XCTAssertEqual(made.links, [kite.id, bell.id], "linked to both")
        store.undo(done)
        XCTAssertEqual(store.notes, before, "Undo leaves nothing")
    }
}
