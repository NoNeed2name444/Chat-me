import CoreGraphics
import XCTest
@testable import Sorta

/// A tree grown again smaller keeps none of the old tree's names (S11, from
/// the Fractal lane's run 44449703457: Let go on an iPad beside the Fractal
/// shrank its tree, and the names layer asked the new sink for a name past its
/// end, stopping the app). Until the new tree is placed nothing names a part
/// past its end, and a part flown in to before it shrank is the whole tree.
@MainActor
final class TreeHostRegrowTests: XCTestCase {
    func testASmallerTreeKeepsNoNamePastItsEnd() async throws {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        var folders: [NoteFolder] = []
        for f in 0..<4 {
            let made = store.createFolder(name: "Shelf \(f + 1)")
            folders.append(made)
            for k in 0..<12 { _ = store.create(title: "Idea \(k + 1) on shelf \(f + 1)", folderId: made.id) }
        }
        // a shelf within the last: numbered breadth first, after every top shelf's ideas
        let inner = store.createFolder(name: "Inner shelf", parentId: folders[3].id)
        for k in 0..<6 { _ = store.create(title: "Idea \(k + 1) on the inner shelf", folderId: inner.id) }

        let host = TreeHost()
        host.resize(CGSize(width: 1194, height: 834), top: 80, bottom: 80)
        var big = NestNodes.build(trunk: "", folders: [], ideas: [])
        try await named(host) { big = host.prepare(store, filter: .all, lens: nil, dark: false, tier: .high) }
        XCTAssertFalse(host.names.isEmpty, "the big tree is named")

        // flown in to the inner shelf, the deepest-numbered branch
        let last = try XCTUnwrap((1..<big.size).last { big.folder[$0] }, "a shelf in the tree")
        XCTAssertEqual(big.titles[last], "Inner shelf")
        try await named(host) { host.go(last) }
        XCTAssertEqual(host.focus, last)

        // Let go: three shelves and their ideas go, so the tree is far smaller
        for note in store.notes where note.folderId == inner.id { store.delete(note.id) }
        store.deleteFolder(inner.id)
        for shelf in folders.dropFirst() {
            for note in store.notes where note.folderId == shelf.id { store.delete(note.id) }
            store.deleteFolder(shelf.id)
        }
        let small = host.prepare(store, filter: .all, lens: nil, dark: false, tier: .high)
        XCTAssertLessThan(small.size, big.size, "the tree shrank")
        XCTAssertGreaterThanOrEqual(host.focus, small.size, "the part flown in to is past the new tree's end")

        // before it is placed: no name, VoiceOver part or tap past the new tree's end
        XCTAssertTrue(host.names.allSatisfy { $0.index < small.size }, "no old name kept")
        XCTAssertTrue(host.parts().allSatisfy { $0.node < small.size }, "no old part for VoiceOver")
        XCTAssertLessThan(host.hit(CGPoint(x: 597, y: 417)), small.size, "a tap lands on the new tree")
        draw(host)

        // placed with the old focus still set, as before the view resets it: taken as the whole tree
        host.place()
        XCTAssertTrue(host.names.allSatisfy { $0.index < small.size }, "every name is the new tree's")
        draw(host)
        // then framed whole, as the host does just after a new tree, and named
        try await named(host) {}
        XCTAssertFalse(host.names.isEmpty, "the small tree is named")
        XCTAssertTrue(host.names.allSatisfy { $0.index < small.size }, "every name is the new tree's")
        XCTAssertFalse(TreeNames3D.overlaps(host.names), "no two names meet")
        draw(host)
    }

    /// Does `act`, then waits, five seconds at most, for the host to place the names (at once, or deferred).
    private func named(_ host: TreeHost, _ act: () -> Void) async throws {
        let before = host.named
        act()
        let start = Date()
        while host.named == before && Date().timeIntervalSince(start) < 5 { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertNotEqual(host.named, before, "the names were placed")
    }

    /// Draws every name as the names layer does, into a small bitmap.
    private func draw(_ host: TreeHost) {
        guard let sink = host.sink,
              let cg = CGContext(data: nil, width: 64, height: 64, bitsPerComponent: 8, bytesPerRow: 256,
                                 space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return XCTFail("a sink and a bitmap")
        }
        sink.cg = cg
        for p in host.names { sink.text(sink.line(p.index, short: p.short), p.x, p.y, p.size, p.bold, 2, 0, 0xFFFFFF, 5) }
        sink.cg = nil
    }
}
