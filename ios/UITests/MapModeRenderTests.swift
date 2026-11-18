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
/// looks are shot still (-graphFreeze), as MapRenderTests' are. Space and
/// Neurons are shot at first run, their first-run card up, which covers no
/// body, and every body below the top strip; putting the card away moves
/// none (S05d). None of the ideas is medical.
final class MapModeRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    func testMapRenders() throws {
        // the made-up export with two more ideas that have no board place, at
        // (0, 0) with Seed swap list (S05c)
        // every first-run card back, as on a new install (S05d)
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(stacked: true), "-graphFreeze",
                                                 "-SortaFirstRun"])
        importExport(app)
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
        // the first view's floor keeps every card a target tall (S05b task 5),
        // a one-line card too
        for title in ["Oil the gate", "A bell for the bike"] {
            let card = mapCard(app, title)
            XCTAssertTrue(card.waitForExistence(timeout: 10), "the \(title) card")
            // 88 x 0.5 at the floor is 44 exactly: half a point for the frame's rounding
            XCTAssertGreaterThanOrEqual(card.frame.height, 43.5, "the \(title) card at the first view: \(card.frame)")
        }
        // no card over another, though the export puts three ideas at (0, 0)
        // and A bell for the bike 33 points below them (S05c); the stored
        // places are as the export has them
        let all: [String] = MadeUpExport.titles + MadeUpExport.placeless + [MadeUpExport.long]
        assertBoardApart(app, all)
        assertPlacesKept(app, all)
        // S05m item 2: the three on (0, 0) are drawn round it, and the bell,
        // stored 33 below them, is drawn below them
        let block: [CGRect] = ["Seed swap list", "Oil the gate", "Ask about the allotment waiting list"].map { mapCard(app, $0).frame }
        let bellTop: CGFloat = mapCard(app, "A bell for the bike").frame.minY
        XCTAssertGreaterThanOrEqual(bellTop, block.map(\.maxY).max()! - 0.5,
                                    "the bell is drawn below the three on its point: \(bellTop), \(block)")
        // S05m item 4: a board of ideas only says no "Idea"; the symbol stays,
        // as the identifier names it; a card is read by its title, then its folder
        XCTAssertEqual(mapCard(app, "A bell for the bike").label, "A bell for the bike", "a board of one kind: the title alone")
        XCTAssertEqual(mapCard(app, "Seed swap list").label, "Seed swap list, Raised beds", "the title, then the folder")
        // S05m item 5: the long title is one line, as tall as a short one's
        // card, and read whole
        let long = mapCard(app, MadeUpExport.long)
        XCTAssertEqual(long.label, MadeUpExport.long, "the long title is read whole")
        XCTAssertLessThanOrEqual(long.frame.height, mapCard(app, "A bell for the bike").frame.height + 0.5,
                                 "the long title is one line: \(long.frame)")
        // a card let go of on another settles clear of every card (S05c)
        let bell = mapCard(app, "A bell for the bike")
        let before: CGRect = bell.frame
        let onto = mapCard(app, "Seed swap list").coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        bell.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: onto, withVelocity: 300, thenHoldForDuration: 0.2)
        sleep(1)
        XCTAssertNotEqual(bell.frame, before, "the bell card was let go of on Seed swap list: \(before)")
        assertBoardApart(app, all)
        // only the card let go of is stored where it settled
        assertPlacesKept(app, all, except: "A bell for the bike")

        app.chooseLook("look-space")
        let map = app.descendants(matching: .any)["graph3D"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 60), "Space: the 3D map")
        waitDrawn(app, map, window, "Space")
        // the labels and the last bodies settle
        sleep(2)
        keepShot("mapmode-2-space")
        // the first-run card, in its first form, covers no body of the first
        // view (S05d)
        let hint = app.descendants(matching: .any)["universeHint"].firstMatch
        XCTAssertTrue(hint.waitForExistence(timeout: 10), "Space at first run: the first-run card")
        assertClear(app, of: hint.frame, "Space")
        assertBelowStrip(app, "Space")
        // S05j item 4: the card within a third of the iPhone's height, and the
        // first view centred in the band between the strip's foot and the card
        keepShot("mapmode-first-run-card")
        if !paned {
            XCTAssertLessThanOrEqual(hint.frame.height, window.height / 3 + 0.5,
                                     "the first-run card within a third of the iPhone: \(hint.frame) in \(window)")
        }
        keepShot("mapmode-first-view")
        if !paned { assertCentred(app, above: hint.frame, window, "Space") }
        // S05j item 1: the iPad's two panes with the status bar, for the renders check
        if paned { keepShot("mapmode-panes-bar") }

        if !paned {
            let chip = app.descendants(matching: .any)["map-list-chip"].firstMatch
            XCTAssertTrue(chip.waitForExistence(timeout: 10), "the To sort chip")
            chip.tap()
            XCTAssertTrue(app.descendants(matching: .any)["map-list"].waitForExistence(timeout: 10), "the list sheet")
            _ = app.buttons["map-list-done"].settledFrame
            keepShot("mapmode-3-list")
            app.buttons["map-list-done"].tap()
        }
        // putting the card away moves nothing: every body is where it was,
        // the map still (S05d)
        XCTAssertTrue(hint.waitForExistence(timeout: 10), "the first-run card, the list sheet gone")
        sleep(1)
        let bodiesBefore: [CGRect] = bodyFrames(app)
        hint.buttons["Got it"].tap()
        XCTAssertTrue(gone(hint), "Got it puts the first-run card away")
        sleep(1)
        let bodiesAfter: [CGRect] = bodyFrames(app)
        XCTAssertEqual(bodiesAfter.count, bodiesBefore.count, "the same bodies after Got it")
        for (was, now) in zip(bodiesBefore, bodiesAfter) {
            XCTAssertEqual(now.midX, was.midX, accuracy: 1, "a body moved when the card went: \(was) to \(now)")
            XCTAssertEqual(now.midY, was.midY, accuracy: 1, "a body moved when the card went: \(was) to \(now)")
        }

        // Neurons at first run: its own card, which Space's Got it leaves
        // unseen, clear of every body (S05d A6)
        app.chooseLook("look-neuron")
        // the chip names the look at once, while the scene is still built:
        // the map's own summary names Neuron's once it is (built.theme)
        let summary = app.descendants(matching: .any)["graph3D"].firstMatch
        let built = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Network of ideas"), object: summary)
        // as long as Space's first build is given (60 s, above): on a slow
        // iPad (CI, 5 Nov) Neuron's build ran past 30 s with the chip already
        // on Neuron
        XCTAssertEqual(XCTWaiter().wait(for: [built], timeout: 60), .completed, "Neuron's scene, built: \(summary.label)")
        sleep(2)
        waitDrawn(app, summary, window, "Neurons")
        sleep(2)
        XCTAssertTrue(hint.waitForExistence(timeout: 10), "Neurons at first run: its first-run card")
        assertClear(app, of: hint.frame, "Neurons")
        assertBelowStrip(app, "Neurons")
        keepShot("mapmode-4-neurons")
        // S05j 6b: Performance's first view, framed below the strip with the
        // glass Graph3DView measures (mapInsets)
        app.chooseLook("look-performance")
        let fast = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Performance map of ideas"),
                                             object: app.descendants(matching: .any)["graph3D"].firstMatch)
        _ = XCTWaiter().wait(for: [fast], timeout: 30)
        sleep(3)
        keepShot("mapmode-perf-first-view")
        app.chooseLook("look-space")
        // Space: the look as the other tests find it
    }

    /// The bodies' box (their centres) lies in the middle of the band between
    /// the top strip's foot and the first-run card's top: its middle within 4%
    /// of the window's height of the band's, the gaps above and below it
    /// within 8% of the band (S05j item 4).
    private func assertCentred(_ app: XCUIApplication, above card: CGRect, _ window: CGRect, _ name: String,
                               file: StaticString = #filePath, line: UInt = #line) {
        let look = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        var foot: CGFloat = look.frame.maxY
        let list = app.descendants(matching: .any)["map-list-chip"].firstMatch
        if list.exists { foot = max(foot, list.frame.maxY) }
        // and the lens row's under them (S06d), which the first view frames below too
        let lenses = app.descendants(matching: .any)["map-lens-row"].firstMatch
        if lenses.exists { foot = max(foot, lenses.frame.maxY) }
        let bodies: [CGRect] = bodyFrames(app)
        guard let top = bodies.map(\.midY).min(), let bottom = bodies.map(\.midY).max() else {
            XCTFail("\(name): no bodies to centre", file: file, line: line)
            return
        }
        let band: CGFloat = card.minY - foot
        let off: CGFloat = abs((top + bottom) / 2 - (foot + card.minY) / 2)
        let gaps: CGFloat = abs((top - foot) - (card.minY - bottom))
        XCTAssertLessThanOrEqual(off, window.height * 0.04,
                                 "\(name): the box \(top)-\(bottom) off the band \(foot)-\(card.minY) by \(off)", file: file, line: line)
        XCTAssertLessThanOrEqual(gaps, band * 0.08,
                                 "\(name): the gaps above and below the box differ by \(gaps) in a band of \(band)", file: file, line: line)
    }

    /// Every body the 3D map shows, where it is drawn (its VoiceOver
    /// element's frame, 44 points round its centre), in the map's order,
    /// from one snapshot of the screen.
    private func bodyFrames(_ app: XCUIApplication) -> [CGRect] {
        guard let root = try? app.snapshot() else { return [] }
        var out: [CGRect] = []
        func walk(_ node: XCUIElementSnapshot) {
            if node.identifier == "graphBody", !node.frame.isEmpty { out.append(node.frame) }
            node.children.forEach(walk)
        }
        walk(root)
        return out
    }

    /// The map has bodies on screen, and none of them meets the first-run
    /// card's frame.
    private func assertClear(_ app: XCUIApplication, of card: CGRect, _ name: String,
                             file: StaticString = #filePath, line: UInt = #line) {
        let bodies: [CGRect] = bodyFrames(app)
        XCTAssertFalse(bodies.isEmpty, "\(name): its bodies, on screen", file: file, line: line)
        for body in bodies {
            XCTAssertFalse(body.intersects(card), "\(name): a body under the first-run card: \(body) \(card)",
                           file: file, line: line)
        }
    }

    /// The map has bodies on screen, and each one is drawn below the top
    /// strip's foot, the lower of its chips' (the To sort chip's, on a phone,
    /// and the look chip's): the first view is framed below the strip, not
    /// from the screen's top (S05d: the iPhone's top cells were under both
    /// chips, CI run 42071068784).
    private func assertBelowStrip(_ app: XCUIApplication, _ name: String,
                                  file: StaticString = #filePath, line: UInt = #line) {
        let look = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        XCTAssertTrue(look.waitForExistence(timeout: 10), "\(name): the look chip", file: file, line: line)
        var foot: CGFloat = look.frame.maxY
        let list = app.descendants(matching: .any)["map-list-chip"].firstMatch
        if list.exists { foot = max(foot, list.frame.maxY) }
        // and the lens row's under them (S06d), which the first view frames below too
        let lenses = app.descendants(matching: .any)["map-lens-row"].firstMatch
        if lenses.exists { foot = max(foot, lenses.frame.maxY) }
        let bodies: [CGRect] = bodyFrames(app)
        XCTAssertFalse(bodies.isEmpty, "\(name): its bodies, on screen", file: file, line: line)
        for body in bodies {
            XCTAssertGreaterThanOrEqual(body.minY, foot, "\(name): a body under the top strip: \(body), its foot at \(foot)",
                                        file: file, line: line)
        }
    }

    /// Whether `element` has gone within ten seconds.
    private func gone(_ element: XCUIElement) -> Bool {
        let start = Date()
        while element.exists && Date().timeIntervalSince(start) < 10 { usleep(200_000) }
        return !element.exists
    }

    /// The largest Dynamic Type size (AX5) on a phone: the switch (its disc
    /// and the list of the modes it opens), the map with its chips, the look
    /// menu open and the list sheet, every control inside the window, none
    /// under 44 points and none touching another.
    func testLargestTextSizeOnTheMap() throws {
        let largest = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        // the first-run cards as a new install has them: testMapRenders, run
        // before this on the same simulator, puts Space's away (S05d)
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(), "-SortaFirstRun"] + largest)
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
        // cards grown with the text size are drawn apart too, the stored
        // places kept (S05c)
        assertBoardApart(app, MadeUpExport.titles)
        assertPlacesKept(app, MadeUpExport.titles)
        // S05m item 8: the folder's caption takes two lines, so Raised beds
        // is whole on its card at this size; read whole, as drawn
        XCTAssertEqual(mapCard(app, "Seed swap list").label, "Seed swap list, Raised beds", "the folder's name whole")
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
        // and no card under them: the first view fits the cards by their own
        // sizes, which at AX5 are far taller than the least card (S05c)
        for title in MadeUpExport.titles {
            let card: CGRect = mapCard(app, title).frame
            assertApart([(title, card), ("the look chip", look.frame), ("the To sort chip", list.frame),
                         ("the switch's disc", disc.frame)], "the board under the map's chrome")
            assertInside(mapCard(app, title), window, "the \(title) card")
        }

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
        // S05j 8f: the first-run card at the largest size the map takes (AX1):
        // its top below the strip, its buttons whole and at least 44 points
        let hint = app.descendants(matching: .any)["universeHint"].firstMatch
        XCTAssertTrue(hint.waitForExistence(timeout: 60), "Space at first run: the first-run card")
        sleep(2)
        keepShot("mapmode-ax5-5-first-run-card")
        XCTAssertGreaterThanOrEqual(hint.frame.minY, look.frame.maxY, "the card below the strip: \(hint.frame)")
        assertInside(hint.buttons["Full list"], window, "the card's Full list")
        assertInside(hint.buttons["Got it"], window, "the card's Got it")
        XCTAssertLessThanOrEqual(hint.buttons["Got it"].frame.maxY, hint.frame.maxY + 0.5, "Got it whole inside the card")
    }

    /// Each 3D look's legend, and Space's and Neurons' first-run cards, name
    /// only controls Sorta has (S05f A4): no line names a "+ ›" menu or the
    /// bar. Each legend is opened from the Look tool and read to its last
    /// row; Performance has no first-run card.
    func testLegendsNameSortasControls() throws {
        // every first-run card back, as on a new install (S05d)
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(), "-graphFreeze",
                                                 "-SortaFirstRun"])
        importExport(app)
        XCTAssertTrue(app.ideaRow("A bell for the bike").waitForExistence(timeout: 10), "the made-up export")
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        if app.windows.firstMatch.frame.width < 700 { app.switchMode(to: "mode-map") }

        let looks: [(look: String, title: String, last: String)] = [
            ("look-space", "How your universe is built", "Links: "),
            ("look-neuron", "How your network is built", "Axons and synapses: "),
            ("look-performance", "How Performance is built", "Lines: "),
        ]
        for (look, title, last) in looks {
            app.chooseLook(look)
            XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60),
                          "\(look): the 3D map")
            if look != "look-performance" {
                let hint = app.descendants(matching: .any)["universeHint"].firstMatch
                XCTAssertTrue(hint.waitForExistence(timeout: 30), "\(look): its first-run card")
                assertNamesSortasControls(texts(in: hint), "\(look)'s first-run card")
            }

            let tool = app.buttons["Look"].firstMatch
            XCTAssertTrue(tool.waitForExistence(timeout: 10), "\(look): the Look tool")
            tool.tap()
            let open = app.buttons[title].firstMatch
            XCTAssertTrue(open.waitForExistence(timeout: 5), "\(look): the legend in the Look menu")
            open.tap()
            let heading = app.staticTexts[title].firstMatch
            XCTAssertTrue(heading.waitForExistence(timeout: 10), "\(look): the legend opened")
            _ = heading.settledFrame
            // its first view kept, for the renders check (S05f A6)
            keepShot("mapmode-legend-\(look.dropFirst("look-".count))")

            // read it to its end: a phone's sheet opens at half height, so
            // the first drag grows it and the rest scroll
            var seen: Set<String> = texts(in: app)
            for _ in 0..<10 {
                let from = heading.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                    .withOffset(CGVector(dx: 0, dy: 320))
                from.press(forDuration: 0.05, thenDragTo: from.withOffset(CGVector(dx: 0, dy: -240)))
                sleep(1)
                let now: Set<String> = texts(in: app)
                if now.isSubset(of: seen) { break }
                seen.formUnion(now)
            }
            XCTAssertTrue(seen.contains { $0.hasPrefix(last) }, "\(look): the legend read to its last row, \(last)")
            assertNamesSortasControls(seen, "\(look)'s legend")

            let bar = app.navigationBars.containing(NSPredicate(format: "label == %@", title)).firstMatch
            bar.buttons["Done"].firstMatch.tap()
            XCTAssertTrue(gone(heading), "\(look): Done closes the legend")
        }
        // Space: the look as the other tests find it
        app.chooseLook("look-space")
    }

    // MARK: helpers

    /// Every label and value under `element`, from one snapshot.
    private func texts(in element: XCUIElement) -> Set<String> {
        guard let root = try? element.snapshot() else { return [] }
        var out: Set<String> = []
        func walk(_ node: XCUIElementSnapshot) {
            if !node.label.isEmpty { out.insert(node.label) }
            if let value = node.value as? String, !value.isEmpty { out.insert(value) }
            node.children.forEach(walk)
        }
        walk(root)
        return out
    }

    /// None of `lines` names a "+ ›" menu or the bar, which Sorta has
    /// neither of (S05f item 4).
    private func assertNamesSortasControls(_ lines: Set<String>, _ place: String,
                                           file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(lines.isEmpty, "\(place): its words", file: file, line: line)
        for text in lines {
            XCTAssertFalse(text.contains("+ \u{203A}"), "\(place) names a + menu: \(text)", file: file, line: line)
            XCTAssertFalse(text.contains("the bar"), "\(place) names the bar: \(text)", file: file, line: line)
        }
    }

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

    /// S05m item 5: the whole of a long title over its card, on a board of
    /// both kinds (the stacked export and a page). XCUITest can't hover on
    /// an iPad nor hold a finger while it takes a picture, so the app shows
    /// the title as a resting finger would (`-SortaBoardPeek`) until the
    /// board is moved. Kept as `mapmode-board-whole-title`, by day and by
    /// night on the iPhone and the iPad (tools/run_ui_tests.sh).
    func testWholeTitleOnTheBoard() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(stacked: true, page: true),
                                                 "-SortaBoardPeek", MadeUpExport.long])
        importExport(app)
        XCTAssertTrue(app.ideaRow("Ask about the allotment waiting list").waitForExistence(timeout: 10))
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        if window.width >= 700 {
            XCTAssertTrue(app.descendants(matching: .any)[MapText.lookChip].waitForExistence(timeout: 10),
                          "two panes: the map beside the Sort screen")
        } else {
            app.switchMode(to: "mode-map")
        }
        app.chooseLook("look-2d")
        let peek = app.descendants(matching: .any)["board-peek"].firstMatch
        XCTAssertTrue(peek.waitForExistence(timeout: 10), "the whole title over its card")
        let shown: CGRect = peek.settledFrame
        sleep(1)
        keepShot("mapmode-board-whole-title")
        XCTAssertEqual(peek.label, MadeUpExport.long, "the whole title, wrapped, not cut")
        XCTAssertTrue(window.contains(shown), "inside the window: \(shown)")
        // clear of the tools
        let recentre = app.buttons["Recentre"].firstMatch
        XCTAssertTrue(recentre.waitForExistence(timeout: 10), "the Recentre tool")
        let tools = CGRect(x: recentre.frame.minX, y: recentre.frame.maxY - 3 * 44 - 2 * 16,
                           width: recentre.frame.width, height: 3 * 44 + 2 * 16)
        XCTAssertTrue(shown.intersection(tools).isNull || shown.intersection(tools).width < 0.5,
                      "the whole title clear of the tools: \(shown), \(tools)")
        // a board of both kinds: each card says its kind, and its identifier
        // names its symbol
        XCTAssertEqual(mapCard(app, "A bell for the bike").label, "A bell for the bike, Idea", "both kinds: the kind said")
        XCTAssertEqual(mapCard(app, MadeUpExport.page).label, MadeUpExport.page + ", Page", "the page says Page")
        XCTAssertTrue(mapCard(app, MadeUpExport.page).identifier.hasPrefix("board-card doc.text "), "the page's symbol")
        XCTAssertTrue(mapCard(app, "A bell for the bike").identifier.hasPrefix("board-card lightbulb "), "an idea's symbol")
        // the card itself is one line and read whole
        let long = mapCard(app, MadeUpExport.long)
        XCTAssertEqual(long.label, MadeUpExport.long + ", Idea", "the long card is read whole")
        XCTAssertLessThanOrEqual(long.frame.height, mapCard(app, "A bell for the bike").frame.height + 0.5,
                                 "the long title is one line: \(long.frame)")
        // moving the board puts it away: a drag in the gutter beside the
        // tools, where no card is
        let from = app.windows.firstMatch.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: recentre.frame.maxX + 8, dy: tools.minY - 40))
        from.press(forDuration: 0.05, thenDragTo: from.withOffset(CGVector(dx: -120, dy: 0)))
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: peek)], timeout: 10),
                       .completed, "the whole title goes once the board is moved")
    }

    /// Every board card named is on the board and covers no other: their
    /// frames, as drawn, apart.
    private func assertBoardApart(_ app: XCUIApplication, _ titles: [String],
                                  file: StaticString = #filePath, line: UInt = #line) {
        let frames: [(String, CGRect)] = titles.map { title in
            let card = mapCard(app, title)
            XCTAssertTrue(card.waitForExistence(timeout: 10), "the \(title) card", file: file, line: line)
            return (title, card.frame)
        }
        assertApart(frames, "the 2D board", file: file, line: line)
    }

    /// Each named card's stored place, which its identifier names
    /// ("board-card x y"), is the export's; `moved`'s is not.
    private func assertPlacesKept(_ app: XCUIApplication, _ titles: [String], except moved: String? = nil,
                                  file: StaticString = #filePath, line: UInt = #line) {
        for title in titles {
            guard let p = MadeUpExport.places[title] else { return XCTFail("no place for \(title)", file: file, line: line) }
            let symbol: String = title == MadeUpExport.page ? "doc.text" : "lightbulb"
            let stored = "board-card \(symbol) \(Double(p.x)) \(Double(p.y))"
            let named: String = mapCard(app, title).identifier
            if title == moved {
                XCTAssertTrue(named.hasPrefix("board-card ") && named != stored,
                              "the \(title) card is stored where it settled: \(named)", file: file, line: line)
            } else {
                XCTAssertEqual(named, stored, "the \(title) card's stored place", file: file, line: line)
            }
        }
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
