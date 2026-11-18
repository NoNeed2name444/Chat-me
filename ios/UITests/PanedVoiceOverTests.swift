import XCTest

/// VoiceOver on the iPad with the editor open beside the map (5dfed33):
/// the editor is modal within the Sort screen's own container, so VoiceOver
/// leaves the list behind it while the map beside it stays in reach: the
/// board's cards and tools on the 2D look, the bodies and the look chip on
/// Space. Modality is VoiceOver's rule, not the tree's: XCUITest still lists
/// the rows behind a modal element (4 of them, CI, 15 Nov), and shows the
/// modal container as an alert. So the test reads what it can see: one
/// modal, holding the editor and no row, over every row of the list, none
/// of them in reach, and the map outside it. With the editor closed the
/// modal is gone and the rows are in reach again, so the queries tell the
/// two apart. The tree is kept as an attachment each time. iPad only
/// (run_ui_tests.sh); Android's TalkBack keeps the list's pane apart by its
/// own panes.
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
        assertListGone(app, "beside the board", map: card)

        // Space: its bodies and the look chip
        app.chooseLook("look-space")
        XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60), "the 3D map")
        let bodies = app.descendants(matching: .any).matching(identifier: "graphBody")
        XCTAssertTrue(bodies.firstMatch.waitForExistence(timeout: 30), "the map's bodies in the tree")
        let body = bodies.allElementsBoundByIndex.first { $0.isHittable }
        XCTAssertNotNil(body, "a body in reach")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        XCTAssertTrue(chip.exists && chip.isHittable, "the look chip in reach")
        keepTree(app, "paned-tree-space")
        assertListGone(app, "beside Space", map: try XCTUnwrap(body))

        // and with the editor closed, the list is back: the queries see it
        app.buttons["editor-done"].firstMatch.tap()
        XCTAssertTrue(app.ideaRow(ShapesExport.barrel).waitForExistence(timeout: 10), "the list's rows, the editor closed")
        XCTAssertTrue(app.ideaRow(ShapesExport.barrel).isHittable, "and in reach")
        XCTAssertEqual(app.alerts.count, 0, "no modal over the list, the editor closed")
        app.chooseLook("look-2d")
    }

    /// The Sort list behind the editor, out of VoiceOver's reach: the one
    /// modal element holds the editor and none of the list's rows; every row
    /// lies under it and none is in reach; the map is outside it.
    private func assertListGone(_ app: XCUIApplication, _ where_: String, map: XCUIElement,
                                file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(app.alerts.count, 1, "one modal element \(where_)", file: file, line: line)
        let modal = app.alerts.firstMatch
        XCTAssertTrue(modal.buttons["editor-done"].exists, "the modal is the editor \(where_)", file: file, line: line)
        XCTAssertEqual(modal.buttons.matching(identifier: "idea-row").count, 0, "no idea row in the modal \(where_)", file: file, line: line)
        XCTAssertEqual(modal.buttons.matching(identifier: "folder-row").count, 0, "no folder row in the modal \(where_)", file: file, line: line)
        let rows = app.ideaRows.allElementsBoundByIndex + app.folderRows.allElementsBoundByIndex
        for row in rows {
            XCTAssertTrue(modal.frame.contains(row.frame), "the row \(row.label) under the modal \(where_)", file: file, line: line)
            XCTAssertFalse(row.isHittable, "the row \(row.label) out of reach \(where_)", file: file, line: line)
        }
        XCTAssertFalse(modal.frame.intersects(map.frame), "the map outside the modal \(where_)", file: file, line: line)
        XCTAssertFalse(modal.descendants(matching: .any)[MapText.lookChip].exists, "the look chip outside the modal \(where_)", file: file, line: line)
    }

    /// The accessibility tree as XCUITest reads it, kept with the results.
    private func keepTree(_ app: XCUIApplication, _ name: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name
        tree.lifetime = .keepAlways
        add(tree)
    }
}
