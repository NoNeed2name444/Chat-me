import Combine
import XCTest
@testable import Sorta

/// S07b A5: a call's writes applied through NoteStore as one change, one
/// save and one Undo step; ideas gone since the snapshot skipped and listed;
/// and the apply budget on 2,000 ideas. Made-up ideas, nothing medical.
@MainActor
final class AddonBatchTests: XCTestCase {
    private func text(_ id: UUID) -> String { AddonStoreMap.text(id) }

    func testABatchIsOneChangeAndUndoPutsBackExactly() throws {
        let store = try AddonTestFiles.store(self)
        let garden = store.createFolder(name: "Garden")
        let a = store.create(title: "Plant beans", body: "By the fence")
        let b = store.create(title: "Fix the gate")
        let c = store.create(title: "Paint the shed", folderId: garden.id)
        store.link(a.id, c.id)
        let before = store.notes
        let newId = UUID()
        var changes = 0
        let watch = store.objectWillChange.sink { changes += 1 }
        defer { watch.cancel() }

        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let r = store.applyAddonBatch([
            .tag(id: text(a.id), tags: ["spring", "outside"]),
            .file(id: text(b.id), folder: text(garden.id)),
            .link(text(b.id), text(c.id)),
            .unlink(text(a.id), text(c.id)),
            .capture(id: text(newId), title: "Buy seeds", body: "Runner beans", x: 10, y: 20),
            .tag(id: text(a.id), tags: ["spring"]),
        ], now: now)

        XCTAssertEqual(changes, 1, "one change, so one save")
        XCTAssertEqual(r.applied.added, [text(newId)])
        XCTAssertEqual(r.applied.changed, [text(a.id), text(b.id)].sorted(by: AddonText.less))
        XCTAssertEqual(r.applied.skipped, [])
        XCTAssertEqual(store.note(a.id)?.tags, ["spring", "outside"])
        XCTAssertEqual(store.note(a.id)?.links, [])
        XCTAssertEqual(store.note(a.id)?.updatedAt, now)
        XCTAssertEqual(store.note(c.id)?.updatedAt, before[2].updatedAt, "an untouched idea keeps its time")
        XCTAssertEqual(store.note(b.id)?.folderId, garden.id)
        XCTAssertEqual(store.note(b.id)?.links, [c.id])
        XCTAssertEqual(store.note(newId)?.title, "Buy seeds")
        XCTAssertEqual(store.notes.count, 4)

        let reshape = try XCTUnwrap(r.reshape)
        store.undo(reshape)
        XCTAssertEqual(store.notes, before, "Undo restores the store exactly")
        XCTAssertEqual(changes, 2, "Undo is one step")
    }

    func testIdeasGoneSinceTheSnapshotAreSkippedAndListed() throws {
        let store = try AddonTestFiles.store(self)
        let a = store.create(title: "Kite")
        let gone = UUID()
        let goneFolder = UUID()
        let r = store.applyAddonBatch([
            .tag(id: text(gone), tags: ["x"]),
            .link(text(a.id), text(gone)),
            .file(id: text(a.id), folder: text(goneFolder)),
            .tag(id: text(a.id), tags: ["red"]),
        ])
        XCTAssertEqual(r.applied.skipped, [text(gone), text(goneFolder)].sorted(by: AddonText.less))
        XCTAssertEqual(r.applied.changed, [text(a.id)])
        XCTAssertEqual(store.note(a.id)?.tags, ["red"])
        XCTAssertNil(store.note(a.id)?.folderId)
    }

    func testABatchThatChangesNothingIsNoChange() throws {
        let store = try AddonTestFiles.store(self)
        let a = store.create(title: "Kite")
        var changes = 0
        let watch = store.objectWillChange.sink { changes += 1 }
        defer { watch.cancel() }
        let r = store.applyAddonBatch([.tag(id: text(a.id), tags: ["red"]), .untag(id: text(a.id), tags: ["red"])])
        // as Android (AddonBatchTest): an idea that ends the same did not change, so nothing is saved and
        // there is no Undo step that would undo nothing
        XCTAssertNil(r.reshape, "it ends the same, so there is nothing to undo")
        XCTAssertEqual(r.applied.changed, [], "it ends the same, so it did not change")
        XCTAssertEqual(changes, 0)
        XCTAssertNil(store.applyAddonBatch([]).reshape, "an empty batch is no change at all")
        XCTAssertEqual(changes, 0)
    }

    // MARK: the budget: half a frame at 120 fps

    func testTheLargestBatchAppliesWithinHalfAFrameOnTwoThousandIdeas() throws {
        let store = try AddonTestFiles.store(self)
        var notes: [Note] = []
        for i in 0..<2_000 {
            var n = Note(id: UUID(), title: "Idea \(i)", body: "Made-up words for idea \(i)")
            n.tags = ["t\(i % 7)"]
            n.boardX = Double(i % 50) * 200
            n.boardY = Double(i / 50) * 120
            notes.append(n)
        }
        _ = store.restore(notes: notes, folders: [])
        var times: [Double] = []
        for round in 0..<20 {
            var ops: [AddonOp] = []
            for k in 0..<100 {
                let n = notes[(round * 97 + k * 19) % notes.count]
                ops.append(k % 2 == 0 ? .tag(id: text(n.id), tags: ["r\(round)"]) : .link(text(n.id), text(notes[k].id)))
            }
            for k in 0..<20 { ops.append(.capture(id: text(UUID()), title: "New \(round)-\(k)", body: "", x: 0, y: 0)) }
            let r = store.applyAddonBatch(ops)
            XCTAssertEqual(r.applied.added.count, 20)
            times.append(Double(r.applyNanos) / 1e6)
            // each batch's save, 2,000 ideas written on the store's own queue, is over before the next
            // batch is timed: the budget is the apply's own time, and the save must not compete with it
            store.waitForSaves()
        }
        let median = times.sorted()[10]
        print("apply median of 20 on 2,000 ideas: \(String(format: "%.3f", median)) ms (largest \(String(format: "%.3f", times.max() ?? 0)) ms)")
        XCTAssertLessThanOrEqual(median, 4.2)
    }
}
