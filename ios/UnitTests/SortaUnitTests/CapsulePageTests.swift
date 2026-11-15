import XCTest
@testable import Sorta

/// The capsule's page as Save a capsule makes it (S06d A4, A6), in the
/// app: the board's picture drawn (ImageRenderer, day's colours, at most 960
/// CSS pixels across), the page with it as a data URI and every idea in it,
/// and the page opened again through the store. The page is kept as an
/// attachment (capsule-ios.html), for a browser to render. MadeUpBackup's
/// ideas, none medical.
@MainActor
final class CapsulePageTests: XCTestCase {
    func testThePageWithItsPicture() throws {
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        store.restore(notes: MadeUpBackup.notes, folders: MadeUpBackup.folders)

        let picture = try XCTUnwrap(CapsuleSave.picture(store, straight: false), "a picture of the board")
        XCTAssertEqual(picture.png.prefix(4), Data([0x89, 0x50, 0x4E, 0x47]), "a PNG")
        XCTAssertTrue((1...960).contains(picture.width) && (1...960).contains(picture.height), "\(picture.width) x \(picture.height)")
        XCTAssertNil(CapsuleSave.picture(NoteStore(fileURL: folder.appendingPathComponent("empty.json")), straight: false),
                     "no picture with no ideas")

        let html = try CapsuleSave.html(store, straight: false, now: Date(timeIntervalSince1970: 1_794_682_800),
                                        zone: TimeZone(identifier: "UTC")!)
        XCTAssertTrue(html.contains("data:image/png;base64,"), "its picture is in it")
        for note in MadeUpBackup.notes { XCTAssertTrue(html.contains(SortaCapsule.escape(note.title)), "the page names \(note.title)") }
        // nothing to run: one script block, the data, which a browser never runs
        XCTAssertEqual(html.components(separatedBy: "<script").count - 1, 1)
        XCTAssertTrue(html.contains("<script type=\"application/json\" id=\"sorta-capsule\">"))
        let page = XCTAttachment(data: Data(html.utf8), uniformTypeIdentifier: "public.html")
        page.name = "capsule-ios.html"
        page.lifetime = .keepAlways
        add(page)

        // and opened again: every idea and folder
        let fresh = NoteStore(fileURL: folder.appendingPathComponent("opened.json"))
        let counts = try SortaCapsule.merge(Data(html.utf8), into: fresh)
        XCTAssertEqual(counts.ideas, MadeUpBackup.notes.count)
        XCTAssertEqual(counts.folders, MadeUpBackup.folders.count)
        XCTAssertEqual(fresh.notes.map(\.id), store.notes.map(\.id))
        XCTAssertEqual(fresh.notes.map(\.source), store.notes.map(\.source), "each source, as written")
    }
}
