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

    /// The picture is the board's own drawing (IdeaBoardView, held still),
    /// not a redraw: at the board's size (every card's drawn place inside
    /// it, at twice the CSS pixels), on the day page, and not blank.
    func testThePictureIsTheBoardAtItsSize() throws {
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        store.restore(notes: MadeUpBackup.notes, folders: MadeUpBackup.folders)

        let frame = try XCTUnwrap(CapsuleSave.fitting(store, straight: false), "the board's frame")
        let picture = try XCTUnwrap(CapsuleSave.picture(store, straight: false), "a picture of the board")
        XCTAssertEqual(picture.width, Int(frame.css.width), "as wide as the board's frame")
        XCTAssertEqual(picture.height, Int(frame.css.height), "as tall as the board's frame")
        // every card the board draws, wholly inside the picture
        let card = IdeaBoardStill.card(titleWidth: frame.still.titleWidth)
        XCTAssertGreaterThan(frame.still.titleWidth, 0, "the title's 20 letters measured")
        let drawn = IdeaBoardSpread.places(store.notes.map {
            IdeaBoardSpread.Card(id: $0.id, centre: CGPoint(x: $0.boardX, y: $0.boardY), size: card)
        })
        for note in store.notes {
            let at = try XCTUnwrap(drawn[note.id])
            let x = frame.css.width / 2 + frame.still.pan.width + at.x * frame.still.zoom
            let y = frame.css.height / 2 + frame.still.pan.height + at.y * frame.still.zoom
            let halfW = card.width / 2 * frame.still.zoom, halfH = card.height / 2 * frame.still.zoom
            XCTAssertTrue(x - halfW >= -0.5 && x + halfW <= frame.css.width + 0.5 && y - halfH >= -0.5 && y + halfH <= frame.css.height + 0.5,
                          "\(note.title) at \(x), \(y) in \(frame.css)")
        }

        let image = try XCTUnwrap(UIImage(data: picture.png)?.cgImage, "the PNG reads back")
        XCTAssertEqual(image.width, picture.width * Int(CapsuleSave.pictureScale), "drawn at twice its CSS width")
        XCTAssertEqual(image.height, picture.height * Int(CapsuleSave.pictureScale), "drawn at twice its CSS height")
        let width = image.width, height = image.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drew: Bool = pixels.withUnsafeMutableBytes { bytes in
            guard let context = CGContext(data: bytes.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        XCTAssertTrue(drew, "the pixels read")
        // the corner is the day page
        let page: UInt32 = WardPalette.hex(.background, dark: false)
        let ground = [Int((page >> 16) & 0xFF), Int((page >> 8) & 0xFF), Int(page & 0xFF)]
        let corner = [Int(pixels[0]), Int(pixels[1]), Int(pixels[2])]
        XCTAssertTrue(zip(corner, ground).allSatisfy { abs($0 - $1) <= 2 }, "the ground is the day page: \(corner) vs \(ground)")
        // and the cards and links are on it: not a blank picture
        var marked = 0
        for k in stride(from: 0, to: pixels.count, by: 4) {
            let off = abs(Int(pixels[k]) - ground[0]) + abs(Int(pixels[k + 1]) - ground[1]) + abs(Int(pixels[k + 2]) - ground[2])
            if off > 24 { marked += 1 }
        }
        XCTAssertGreaterThan(marked, width * height / 100, "\(marked) of \(width * height) pixels drawn on the page")
    }
}
