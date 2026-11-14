import XCTest

/// Map mode's switch and holder (S05b A4), used as a person uses them, each
/// test on a fresh store of its own: the switch changes modes and back, an
/// empty store's map shows the empty hint and the switch, and on the 2D look
/// the map shows a captured idea and opening it opens S05's editor. The
/// "To sort n" chip opens the list as a sheet, whose words open the editor.
/// A look chosen from the look chip's menu is kept, and a relaunch opens on
/// Sort. Each of the four looks is one tap on the chip and one in its menu,
/// the chip then naming it; the list sheet lists To sort before the folders
/// and files an idea through the chooser; and a title changed in the editor
/// opened from the map shows on the map. Android's MapModeTest asks the same.
final class MapModeTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
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
        app.clearSlideToTypeIntro(refocus: app.capture)
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
        app.clearSlideToTypeIntro(refocus: app.capture)
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
        app.clearSlideToTypeIntro(refocus: app.capture)
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

    /// Each look is reached by one tap on the look chip and one on its name
    /// in the menu, and the chip then names it (WCAG 2.5.1: no gesture).
    /// Space comes last, the look as the other tests find it.
    func testEachLookIsOneTapFromTheChip() {
        let app = launchIdeas(IdeasStoreName())
        app.captureIdea("Paint the shed green")
        XCTAssertTrue(app.ideaRow("Paint the shed green").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        for (look, name) in [("look-neuron", "Neuron"), ("look-2d", "2D"),
                             ("look-performance", "Performance"), ("look-space", "Space")] {
            app.chooseLook(look)
            let named = NSPredicate(format: "label == %@", "Theme, \(name)")
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: named, object: chip)], timeout: 10),
                           .completed, "the chip names \(name): \(chip.label)")
            if look == "look-2d" {
                XCTAssertTrue(app.boardCard("Paint the shed green").waitForExistence(timeout: 10), "2D: the board")
            } else {
                XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 30),
                              "\(name): the 3D map")
            }
        }
    }

    /// The list sheet lists To sort first, before the folders, and an idea
    /// filed there through the chooser leaves To sort, in the sheet and on
    /// the chip.
    func testTheListSheetListsToSortFirstAndFilesAnIdea() {
        let app = launchIdeas(IdeasStoreName(), board2D)
        app.makeFolder("Garden")
        app.captureIdea("Plant bulbs")
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        let chip = app.descendants(matching: .any)["map-list-chip"].firstMatch
        XCTAssertTrue(chip.waitForExistence(timeout: 10), "the To sort chip")
        chip.tap()
        XCTAssertTrue(app.descendants(matching: .any)["map-list"].waitForExistence(timeout: 10), "the list sheet")
        let row = app.ideaRow("Plant bulbs")
        let garden = app.folderRow("Garden")
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the idea's row")
        XCTAssertTrue(garden.waitForExistence(timeout: 10), "the folder's row")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
        let title = app.ideasTitle.settledFrame
        XCTAssertLessThan(title.maxY, row.frame.minY + 1, "To sort's title heads the list: \(title) \(row.frame)")
        XCTAssertLessThan(row.frame.maxY, garden.frame.minY + 1,
                          "To sort comes before the folders: \(row.frame) \(garden.frame)")

        app.openChooser(for: "Plant bulbs")
        app.chooserFolder("Garden").tap()
        XCTAssertTrue(app.staticTexts[IdeasText.allSorted].waitForExistence(timeout: 10), "To sort is empty")
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: row)], timeout: 10),
                       .completed, "the idea has gone from To sort")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(0))
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 1), "it is in Garden")
        app.buttons["map-list-done"].tap()
        let none = NSPredicate(format: "label == %@", IdeasText.toSortTitle(0))
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: none, object: chip)], timeout: 10),
                       .completed, "the chip counts none to sort: \(chip.label)")
    }

    /// An idea opened from the map, its title changed in the editor and the
    /// editor closed, shows its new title on the map (the 2D board's card).
    func testATitleEditedFromTheMapShowsOnTheMap() {
        let app = launchIdeas(IdeasStoreName(), board2D)
        app.captureIdea("Paint the shed green")
        XCTAssertTrue(app.ideaRow("Paint the shed green").waitForExistence(timeout: 10), "captured")
        app.switchMode(to: "mode-map")
        let card = app.boardCard("Paint the shed green")
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the idea is on the map")
        card.tap()
        XCTAssertTrue(app.descendants(matching: .any)["editor-title"].firstMatch.waitForExistence(timeout: 10),
                      "the editor opened")
        let title = app.textViews["editor-title"].exists ? app.textViews["editor-title"] : app.textFields["editor-title"]
        title.replaceText(with: "Paint the fence blue")
        app.buttons["editor-done"].tap()
        XCTAssertTrue(app.boardCard("Paint the fence blue").waitForExistence(timeout: 10), "the new title is on the map")
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: card)], timeout: 10),
                       .completed, "the old title has gone from the map")
    }
}

/// Map mode's words the tests look for (IdeasWords holds the app's).
enum MapText {
    static let emptyTitle = "No ideas yet"
    static let emptyLine = "Add ideas in Sort, and they appear here."
    static let lookChip = "map-look"
    static let looks = ["look-space", "look-neuron", "look-2d", "look-performance"]
    /// The switch's folded disc, as it is at the accessibility text sizes
    /// and while capture holds the caret; the open strip's fold chevron
    /// carries it too (SortaMode.toggleIdentifier).
    static let switchDisc = "modeSwitcherToggle"
}

extension XCUIApplication {
    /// Taps the switch's segment `identifier`, opening the switch from its
    /// disc first when it is folded: while capture holds the caret
    /// (IdeasFoot's `typing`, IdeasFiling.swift:79-90), as after every
    /// captureIdea, and at the accessibility text sizes. Which one is on show
    /// is read, not guessed from a short wait: the strip's fold chevron
    /// carries the disc's identifier too, so a strip slow to show, found
    /// after a 3 s wait for its segment, had its chevron tapped and was
    /// folded away. Open, the segment and the chevron show together; folded,
    /// only the disc does.
    func switchMode(to identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        let segment = descendants(matching: .any)[identifier].firstMatch
        let toggle = descendants(matching: .any)[MapText.switchDisc].firstMatch
        let shown = NSPredicate { _, _ in segment.exists || toggle.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: shown, object: nil)], timeout: 10),
                       .completed, "the switch, open or folded", file: file, line: line)
        if !segment.exists {
            XCTAssertTrue(toggle.exists, "the switch's folded disc", file: file, line: line)
            XCTContext.runActivity(named: "the switch is folded; open it from its disc") { _ in
                toggle.tap()
            }
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
