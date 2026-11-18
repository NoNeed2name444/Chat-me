import XCTest

/// S11: the Tree look rendered for real, on the made-up export's ideas, each
/// view kept as a named screenshot: the whole tree with the first visit's
/// card, then one level in, the Garden bough tapped through its name as
/// VoiceOver finds it. CI runs it by day and by night on the iPhone and the
/// iPad (tools/run_ui_tests.sh, the ideas set). On the iPad the map is the
/// pane beside the Sort screen.
final class TreeMapRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    func testTreeRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(stacked: true), "-SortaFirstRun"])
        importExport(app)
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        if window.width >= 700 {
            XCTAssertTrue(app.descendants(matching: .any)[MapText.lookChip].waitForExistence(timeout: 10),
                          "two panes: the map beside the Sort screen")
        } else {
            app.switchMode(to: "mode-map")
        }

        app.chooseLook("look-tree")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        let named = NSPredicate(format: "label == %@", "Theme, Tree")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: named, object: chip)], timeout: 10),
                       .completed, "the chip names the Tree: \(chip.label)")
        let map = app.descendants(matching: .any)["tree-map"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 10), "the Tree")
        XCTAssertTrue(map.label.hasPrefix("Tree map of ideas, showing All ideas"), "the tree read whole: \(map.label)")
        let card = app.descendants(matching: .any)["tree-first-visit"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the first visit's card")
        sleep(1)
        keepShot("tree-1-whole")

        // the Garden bough's name, a target each way, read as a folder
        let garden = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Garden, folder")).firstMatch
        XCTAssertTrue(garden.waitForExistence(timeout: 10), "the Garden bough, named")
        XCTAssertGreaterThanOrEqual(garden.frame.width, 43.5, "Garden's target: \(garden.frame)")
        XCTAssertGreaterThanOrEqual(garden.frame.height, 43.5, "Garden's target: \(garden.frame)")
        garden.tap()

        let back = app.descendants(matching: .any)["tree-back"].firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10), "one level in, Back")
        XCTAssertEqual(back.label, "Back to All ideas")
        XCTAssertGreaterThanOrEqual(back.frame.width, 43.5, "Back's target: \(back.frame)")
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: card)], timeout: 10),
                       .completed, "the first visit's card is put away")
        let inGarden = NSPredicate(format: "label BEGINSWITH %@", "Tree map of ideas, showing Garden")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: inGarden, object: map)], timeout: 10),
                       .completed, "Garden is the whole tree: \(map.label)")
        sleep(1)
        keepShot("tree-2-in")

        back.tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: back)], timeout: 10),
                       .completed, "back to the whole tree")
    }
}
