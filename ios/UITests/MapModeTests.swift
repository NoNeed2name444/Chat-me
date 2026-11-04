import XCTest

/// Map mode's switch and holder (S05b A4), used as a person uses them, each
/// test on a fresh store of its own: the switch changes modes and back, an
/// empty store's map shows the empty hint and the switch, and on the 2D look
/// the map shows a captured idea and opening it opens S05's editor. The
/// "To sort n" chip opens the list as a sheet, whose words open the editor.
/// A look chosen from the look chip's menu is kept, and a relaunch opens on
/// Sort. Android's MapModeTest asks the same.
final class MapModeTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// The look stored as 2D before launch, through the argument domain
    /// (MapLook.boardKey), as a person's earlier choice would be.
    private let board2D = ["-vignette.ideas.mapBoard", "YES"]

    func testTheSwitchChangesModesAndBack() {
        let app = launchIdeas(IdeasStoreName())
        app.switchMode(to: "mode-map")
        XCTAssertTrue(app.descendants(matching: .any)["map-empty"].waitForExistence(timeout: 10), "the map's empty hint")
        XCTAssertFalse(app.capture.exists, "capture is Sort's")
        app.switchMode(to: "mode-sort")
        XCTAssertTrue(app.capture.waitForExistence(timeout: 10), "back in Sort")
    }

    func testAnEmptyStoresMapShowsTheHintAndTheSwitch() {
        let app = launchIdeas(IdeasStoreName())
        app.switchMode(to: "mode-map")
        let empty = app.descendants(matching: .any)["map-empty"]
        XCTAssertTrue(empty.waitForExistence(timeout: 10), "the empty hint")
        XCTAssertTrue(empty.label.contains(MapText.emptyTitle) && empty.label.contains(MapText.emptyLine),
                      "its words: \(empty.label)")
        XCTAssertTrue(app.descendants(matching: .any)["mode-sort"].firstMatch.isHittable, "the switch back to Sort")
    }

    func testTheMapShowsACapturedIdeaAndOpensItInTheEditor() {
        let app = launchIdeas(IdeasStoreName(), board2D)
        app.capture.tap()
        app.capture.typeText("Paint the shed green\n")
        XCTAssertTrue(app.ideaRow("Paint the shed green").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        let card = app.boardCard("Paint the shed green")
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the idea is on the map")
        card.tap()
        XCTAssertTrue(app.descendants(matching: .any)["editor-title"].waitForExistence(timeout: 10),
                      "opening it opens the editor")
    }

    func testTheListChipOpensTheListAndItsWordsOpenTheEditor() {
        let app = launchIdeas(IdeasStoreName(), board2D)
        app.capture.tap()
        app.capture.typeText("Paint the shed green\n")
        XCTAssertTrue(app.ideaRow("Paint the shed green").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        let chip = app.descendants(matching: .any)["map-list-chip"].firstMatch
        XCTAssertTrue(chip.waitForExistence(timeout: 10), "the To sort chip")
        XCTAssertTrue(chip.label.contains("1"), "its count: \(chip.label)")
        chip.tap()
        XCTAssertTrue(app.descendants(matching: .any)["map-list"].waitForExistence(timeout: 10), "the list sheet")
        let row = app.ideaRow("Paint the shed green")
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the idea's row in it")
        row.tap()
        XCTAssertTrue(app.descendants(matching: .any)["editor-title"].waitForExistence(timeout: 10),
                      "its words open the editor")
    }

    func testARelaunchOpensOnSortWithTheLookKept() {
        let store = IdeasStoreName()
        let app = launchIdeas(store)
        app.capture.tap()
        app.capture.typeText("Paint the shed green\n")
        XCTAssertTrue(app.ideaRow("Paint the shed green").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        app.chooseLook("look-2d")
        XCTAssertTrue(app.boardCard("Paint the shed green").waitForExistence(timeout: 10), "2D: the board")
        app.terminate()
        // launchIdeas waits for capture: Sort, at every launch
        let again = launchIdeas(store)
        again.switchMode(to: "mode-map")
        XCTAssertTrue(again.boardCard("Paint the shed green").waitForExistence(timeout: 10), "2D kept")
        let chip = again.descendants(matching: .any)[MapText.lookChip].firstMatch
        XCTAssertEqual(chip.label, "Theme, 2D", "the chip names the look kept")
        // the look as the other tests find it
        again.chooseLook("look-space")
    }
}

/// Map mode's words the tests look for (IdeasWords holds the app's).
enum MapText {
    static let emptyTitle = "No ideas yet"
    static let emptyLine = "Add ideas in Sort, and they appear here."
    static let lookChip = "map-look"
    static let looks = ["look-space", "look-neuron", "look-2d", "look-performance"]
}

extension XCUIApplication {
    /// Taps the switch's segment `identifier`, opening the switch first when
    /// it is folded (as it is while the keyboard is up).
    func switchMode(to identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        let segment = descendants(matching: .any)[identifier].firstMatch
        if !segment.waitForExistence(timeout: 3) {
            let toggle = descendants(matching: .any)["modeSwitcherToggle"].firstMatch
            XCTAssertTrue(toggle.waitForExistence(timeout: 10), "the switch's folded disc", file: file, line: line)
            toggle.tap()
        }
        XCTAssertTrue(segment.waitForExistence(timeout: 10), "the switch's \(identifier)", file: file, line: line)
        segment.tap()
    }

    /// Opens the look chip's menu, finds all four looks in it, and chooses
    /// the look `identifier`.
    func chooseLook(_ identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        let chip = descendants(matching: .any)[MapText.lookChip].firstMatch
        XCTAssertTrue(chip.waitForExistence(timeout: 10), "the look chip", file: file, line: line)
        chip.tap()
        for look in MapText.looks {
            XCTAssertTrue(descendants(matching: .any)[look].firstMatch.waitForExistence(timeout: 5),
                          "the menu's \(look)", file: file, line: line)
        }
        descendants(matching: .any)[identifier].firstMatch.tap()
    }

    /// The board's card for an idea (the 2D look).
    func boardCard(_ title: String) -> XCUIElement {
        buttons.matching(NSPredicate(format: "label CONTAINS %@", title)).firstMatch
    }
}
