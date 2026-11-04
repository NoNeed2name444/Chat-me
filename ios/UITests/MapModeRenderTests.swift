import UIKit
import XCTest

/// Map mode rendered for real (S05b A6 and A7), each kept as a named
/// screenshot, as IdeasRenderTests keeps the Sort screen's: the 2D board and
/// Space on the person's own ideas (the made-up export's and two typed
/// ones), and on a phone the list sheet over the map. CI runs it by day and
/// by night on the iPhone and the iPad (tools/run_ui_tests.sh, the ideas
/// set), the appearance set on the simulator from outside. On the iPad the
/// map is the pane beside the Sort screen. The largest text size runs on the
/// iPhone only: the switch, the look menu open and the list sheet. The 3D
/// looks are shot still (-graphFreeze), as MapRenderTests' are. None of the
/// ideas is medical.
final class MapModeRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testMapRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(), "-graphFreeze"])
        importExport(app)
        app.captureIdea("Oil the gate")
        app.captureIdea("Ask about the allotment waiting list")
        XCTAssertTrue(app.ideaRow("Ask about the allotment waiting list").waitForExistence(timeout: 10))
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        let paned = window.width >= 700
        if paned {
            XCTAssertTrue(app.descendants(matching: .any)[MapText.lookChip].waitForExistence(timeout: 10),
                          "two panes: the map beside the Sort screen")
        } else {
            app.switchMode(to: "mode-map")
        }

        app.chooseLook("look-2d")
        XCTAssertTrue(mapCard(app, "Oil the gate").waitForExistence(timeout: 10), "2D: the board, with the idea")
        sleep(1)
        keepShot("mapmode-1-board")

        app.chooseLook("look-space")
        let map = app.descendants(matching: .any)["graph3D"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 60), "Space: the 3D map")
        waitDrawn(app, map, window, "Space")
        // the labels and the last bodies settle
        sleep(2)
        keepShot("mapmode-2-space")

        if !paned {
            let chip = app.descendants(matching: .any)["map-list-chip"].firstMatch
            XCTAssertTrue(chip.waitForExistence(timeout: 10), "the To sort chip")
            chip.tap()
            XCTAssertTrue(app.descendants(matching: .any)["map-list"].waitForExistence(timeout: 10), "the list sheet")
            _ = app.buttons["map-list-done"].settledFrame
            keepShot("mapmode-3-list")
            app.buttons["map-list-done"].tap()
        }
        // Space: the look as the other tests find it
    }

    /// The largest Dynamic Type size (AX5) on a phone: the switch (its disc
    /// and the list of the modes it opens), the map with its chips, the look
    /// menu open and the list sheet, every control inside the window, none
    /// under 44 points and none touching another.
    func testLargestTextSizeOnTheMap() throws {
        let largest = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64()] + largest)
        let window = app.windows.firstMatch.frame
        importExport(app)

        // the switch: at these sizes its folded disc, which opens the modes' list
        let disc = app.descendants(matching: .any)[MapText.switchDisc].firstMatch
        XCTAssertTrue(disc.waitForExistence(timeout: 10), "the switch's disc")
        assertInside(disc, window, "the switch's disc")
        disc.tap()
        let toMap = app.descendants(matching: .any)["mode-map"].firstMatch
        let toSort = app.descendants(matching: .any)["mode-sort"].firstMatch
        XCTAssertTrue(toMap.waitForExistence(timeout: 10), "the switch's list of the modes")
        _ = toMap.settledFrame
        sleep(1)
        keepShot("mapmode-ax5-1-switch")
        assertInside(toSort, window, "Sort in the switch's list")
        assertInside(toMap, window, "Map in the switch's list")
        assertApart([("Sort", toSort.frame), ("Map", toMap.frame)], "the switch's list")
        toMap.tap()

        app.chooseLook("look-2d")
        XCTAssertTrue(app.boardCard("A bell for the bike").waitForExistence(timeout: 10), "2D: the board")
        let look = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        let list = app.descendants(matching: .any)["map-list-chip"].firstMatch
        XCTAssertTrue(list.waitForExistence(timeout: 10), "the To sort chip")
        sleep(1)
        keepShot("mapmode-ax5-2-map")
        assertInside(look, window, "the look chip")
        assertInside(list, window, "the To sort chip")
        assertInside(disc, window, "the switch's disc on the map")
        assertApart([("the look chip", look.frame), ("the To sort chip", list.frame),
                     ("the switch's disc", disc.frame)], "the map's chrome")

        look.tap()
        for item in MapText.looks {
            XCTAssertTrue(app.descendants(matching: .any)[item].firstMatch.waitForExistence(timeout: 5), "the menu's \(item)")
        }
        _ = app.descendants(matching: .any)[MapText.looks[0]].firstMatch.settledFrame
        keepShot("mapmode-ax5-3-look-menu")
        var items: [(String, CGRect)] = []
        for item in MapText.looks {
            let element = app.descendants(matching: .any)[item].firstMatch
            assertInside(element, window, "the menu's \(item)")
            items.append((item, element.frame))
        }
        assertApart(items, "the look menu")
        // 2D again: the menu closes, nothing changes
        app.descendants(matching: .any)["look-2d"].firstMatch.tap()
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: gone, object: app.descendants(matching: .any)["look-space"].firstMatch)], timeout: 10),
                       .completed, "the look menu closed")

        list.tap()
        XCTAssertTrue(app.descendants(matching: .any)["map-list"].waitForExistence(timeout: 10), "the list sheet")
        let done = app.buttons["map-list-done"]
        _ = done.settledFrame
        keepShot("mapmode-ax5-4-list")
        assertInside(done, window, "the sheet's Done")
        let row = app.ideaRow("A bell for the bike")
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the idea in To sort")
        assertInside(row, window, "the idea's row")
        let garden = app.folderRow("Garden")
        XCTAssertTrue(garden.waitForExistence(timeout: 10), "the Garden folder in the list")
        XCTAssertLessThan(app.ideasTitle.frame.maxY, garden.frame.minY + 1,
                          "To sort comes before the folders")
        done.tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: gone, object: app.descendants(matching: .any)["map-list"].firstMatch)], timeout: 10),
                       .completed, "the list sheet closed")
        // Space: the look as the other tests find it
        app.chooseLook("look-space")
    }

    // MARK: helpers

    /// An idea's card on the board, not its row beside it on two panes.
    private func mapCard(_ app: XCUIApplication, _ title: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@ AND identifier != %@ AND identifier != %@",
                                         title, "idea-row", "choose-folder")).firstMatch
    }

    /// Waits up to a minute for the 3D map to be drawn, not just its night:
    /// the share of its middle (past the top strip, short of the foot and
    /// of the map's own tools) that is lit, every fourth pixel, as
    /// MapRenderTests measures it. The map's own count (graphDrawn) is the
    /// design preview's only.
    private func waitDrawn(_ app: XCUIApplication, _ map: XCUIElement, _ window: CGRect, _ name: String,
                           file: StaticString = #filePath, line: UInt = #line) {
        // the map's own frame, never the window's: on a broad window the Sort
        // pane beside it is bright, and would pass for a drawn map
        XCTAssertTrue(map.waitForExistence(timeout: 20), "the \(name) map's view", file: file, line: line)
        let frame = map.frame
        XCTAssertTrue(frame.width >= 100 && frame.height >= 100,
                      "the \(name) map's view has room: \(frame)", file: file, line: line)
        var top = frame.minY + frame.height * 0.15
        let strip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        if strip.exists { top = max(top, strip.frame.maxY + 8) }
        let region = CGRect(x: frame.minX + frame.width * 0.08, y: top,
                            width: frame.width * 0.72, height: max(0, frame.minY + frame.height * 0.85 - top))
        let start = Date()
        var lit = Self.lit(region, window)
        while lit < Self.drawn && Date().timeIntervalSince(start) < 60 {
            sleep(1)
            lit = Self.lit(region, window)
        }
        XCTAssertGreaterThanOrEqual(lit, Self.drawn, "the \(name) map is drawn, not just its night (\(region))",
                                    file: file, line: line)
    }

    /// MapRenderTests' share: on its renders the sparest map lit 1.1%.
    private static let drawn: Double = 0.003

    /// The share of `region` (in the window's points) of the screen that is
    /// lit: some channel past 40, which the night's background never passes.
    private static func lit(_ region: CGRect, _ window: CGRect) -> Double {
        guard window.width > 0, let picture = XCUIScreen.main.screenshot().image.cgImage,
              let srgb = CGColorSpace(name: CGColorSpace.sRGB) else { return 0 }
        let width = picture.width, height = picture.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let redrawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: srgb,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(picture, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard redrawn else { return 0 }
        let scale = CGFloat(width) / window.width
        let box = CGRect(x: region.minX * scale, y: region.minY * scale,
                         width: region.width * scale, height: region.height * scale)
            .intersection(CGRect(x: 0, y: 0, width: width, height: height))
        guard !box.isNull, box.width >= 4, box.height >= 4 else { return 0 }
        var seen = 0, bright = 0
        for y in stride(from: Int(box.minY), to: Int(box.maxY), by: 4) {
            for x in stride(from: Int(box.minX), to: Int(box.maxX), by: 4) {
                let at = (y * width + x) * 4
                seen += 1
                if max(bytes[at], bytes[at + 1], bytes[at + 2]) > 40 { bright += 1 }
            }
        }
        return seen > 0 ? Double(bright) / Double(seen) : 0
    }

    /// No two of the targets overlap (IdeasBehaviourTests' check).
    private func assertApart(_ targets: [(String, CGRect)], _ place: String,
                             file: StaticString = #filePath, line: UInt = #line) {
        for i in targets.indices {
            for j in targets.indices where j > i {
                let overlap = targets[i].1.intersection(targets[j].1)
                XCTAssertTrue(overlap.isNull || overlap.width < 0.5 || overlap.height < 0.5,
                              "\(place): \(targets[i].0) and \(targets[j].0) overlap by \(overlap.size)",
                              file: file, line: line)
            }
        }
    }
}
