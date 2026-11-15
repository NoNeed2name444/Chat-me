import XCTest

/// VoiceOver on the iPad with the editor open beside the map (5dfed33):
/// the editor is modal within the Sort screen's own container, so the list
/// behind it leaves the accessibility tree while the map beside it stays in
/// it and in reach: the board's cards and tools on the 2D look, the bodies
/// and the look chip on Space. With the editor closed, the list's rows are
/// back, so the queries tell the two apart. The tree is kept as an
/// attachment each time. iPad only (run_ui_tests.sh); Android's TalkBack
/// keeps the list's pane apart by its own panes.
final class PanedVoiceOverTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        Retaps.reset()
    }

    func testBesideTheEditorTheMapIsReachedAndTheListIsNot() throws {
        let app = try openBarrel(["-graphFreeze"])
        XCTAssertGreaterThanOrEqual(app.windows.firstMatch.frame.width, 700, "two panes: this test runs on the iPad")
        XCTAssertTrue(app.buttons["editor-done"].waitForExistence(timeout: 10), "the editor, open beside the map")

        // the 2D look: the board's cards and its tools
        app.chooseLook("look-2d")
        let card = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@",
                                                    "board-card", ShapesExport.tomatoes)).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "a board card in the tree")
        XCTAssertTrue(card.isHittable, "a board card in reach")
        let recentre = app.buttons["Recentre"].firstMatch
        XCTAssertTrue(recentre.exists && recentre.isHittable, "the board's Recentre in reach")
        keepTree(app, "paned-tree-board")
        assertListGone(app, "beside the board")

        // Space: its bodies and the look chip
        app.chooseLook("look-space")
        XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60), "the 3D map")
        let bodies = app.descendants(matching: .any).matching(identifier: "graphBody")
        XCTAssertTrue(bodies.firstMatch.waitForExistence(timeout: 30), "the map's bodies in the tree")
        XCTAssertTrue(bodies.allElementsBoundByIndex.contains { $0.isHittable }, "a body in reach")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        XCTAssertTrue(chip.exists && chip.isHittable, "the look chip in reach")
        keepTree(app, "paned-tree-space")
        assertListGone(app, "beside Space")

        // and with the editor closed, the list is back: the queries see it
        app.buttons["editor-done"].firstMatch.tap()
        XCTAssertTrue(app.ideaRow(ShapesExport.barrel).waitForExistence(timeout: 10), "the list's rows, the editor closed")
        XCTAssertTrue(app.ideaRow(ShapesExport.barrel).isHittable, "and in reach")
        app.chooseLook("look-2d")
    }

    /// The Sort list behind the editor: none of its rows in the tree.
    private func assertListGone(_ app: XCUIApplication, _ where_: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(app.ideaRows.count, 0, "no idea row \(where_)", file: file, line: line)
        XCTAssertEqual(app.folderRows.count, 0, "no folder row \(where_)", file: file, line: line)
        XCTAssertFalse(app.ideaRow(ShapesExport.barrel).exists, "not the open idea's row \(where_)", file: file, line: line)
    }

    /// The accessibility tree as XCUITest reads it, kept with the results.
    private func keepTree(_ app: XCUIApplication, _ name: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name
        tree.lifetime = .keepAlways
        add(tree)
    }
}
