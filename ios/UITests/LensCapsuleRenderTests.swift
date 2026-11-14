import XCTest

/// The lenses and the capsule as a person meets them (S06d A2, A3, A4, A6),
/// each kept as a named screenshot, as Android's MapLensRenderTest and
/// CapsuleRenderTest keep theirs. CI runs both by day and by night on the
/// iPhone and the iPad (tools/run_ui_tests.sh, the ideas set). The ideas
/// are the made-up export's and one typed; none is medical.
final class LensCapsuleRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    /// The typed idea: a question that stops mid-sentence.
    private let baker = "Ask the baker about rye? Maybe"

    /// Map mode's lens row on the 2D board: Unfinished on (the bell, which
    /// has no words, and the baker's question), then Questions too (the
    /// baker's alone, which passes both). Each chip is read by its name and
    /// count, is selected while on, and is at least 44 points; the cards
    /// left out are faded and not read, so VoiceOver finds only those that
    /// pass. The 3D looks' fade is the filter's own path (IdeaLensTests).
    func testLensesRender() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64()])
        importExport(app)
        app.captureIdea(baker)
        XCTAssertTrue(app.ideaRow(baker).waitForExistence(timeout: 10))
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        let paned = window.width >= 700
        if !paned { app.switchMode(to: "mode-map") }
        app.chooseLook("look-2d")
        let titles: [String] = MadeUpExport.titles + [baker]
        for title in titles { XCTAssertTrue(card(app, title).waitForExistence(timeout: 10), "the \(title) card before a lens") }

        let row = app.descendants(matching: .any)["map-lens-row"].firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the lens row under the strip")
        let look = app.descendants(matching: .any)["map-look"].firstMatch
        XCTAssertGreaterThanOrEqual(row.frame.minY, look.frame.maxY - 0.5, "the row is under the strip's chips")
        chip(app, "questions", "Questions", 1, on: false, window)
        chip(app, "unfinished", "Unfinished", 2, on: false, window)
        let late = app.buttons["map-lens-late-night"]
        XCTAssertTrue(late.label.hasPrefix("Late night, "), "Late night read with its count: \(late.label)")
        let alone = app.buttons["map-lens-alone"]
        XCTAssertTrue(alone.label.hasPrefix("Alone, "), "Alone read with its count: \(alone.label)")

        // one lens: the unfinished ideas, the rest faded and unread
        app.buttons["map-lens-unfinished"].tap()
        XCTAssertTrue(waitGone(card(app, "Paint the shed green")), "the shed's card is left out, so not read")
        for title in ["A bell for the bike", baker] { XCTAssertTrue(card(app, title).exists, "\(title) passes Unfinished") }
        for title in ["Seed swap list", "Try a rye starter"] { XCTAssertFalse(card(app, title).exists, "\(title) is left out") }
        chip(app, "unfinished", "Unfinished", 2, on: true, window)
        chip(app, "questions", "Questions", 1, on: false, window)
        sleep(1)
        keepShot("lens-one")

        // two lenses: only the idea that passes both
        app.buttons["map-lens-questions"].tap()
        XCTAssertTrue(waitGone(card(app, "A bell for the bike")), "the bell has no question mark")
        XCTAssertTrue(card(app, baker).exists, "the baker's question passes both")
        chip(app, "questions", "Questions", 1, on: true, window)
        chip(app, "unfinished", "Unfinished", 1, on: true, window)
        XCTAssertFalse(app.descendants(matching: .any)["map-lens-empty"].exists, "no empty line while one passes")
        sleep(1)
        keepShot("lens-two")

        // both off: every card back
        app.buttons["map-lens-questions"].tap()
        app.buttons["map-lens-unfinished"].tap()
        for title in titles { XCTAssertTrue(card(app, title).waitForExistence(timeout: 10), "the \(title) card with no lens") }
    }

    /// The quiet offer under the well (`-SortaCapsuleOffer` shows it at
    /// once): its words, its two actions each at least 44 points; Save a
    /// capsule opens the system's save screen, which Cancel closes; Not
    /// now puts it away; and More has Save a capsule… and Open a capsule….
    func testCapsuleOfferRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(), "-SortaCapsuleOffer"])
        importExport(app)
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        let offer = app.descendants(matching: .any)["capsule-offer"].firstMatch
        XCTAssertTrue(offer.waitForExistence(timeout: 10), "the offer under the well")
        XCTAssertTrue(app.staticTexts[CapsuleText.offer].exists, "its words")
        let save = app.buttons["capsule-offer-save"]
        let later = app.buttons["capsule-offer-later"]
        assertInside(save, window, "Save a capsule")
        assertInside(later, window, "Not now")
        XCTAssertEqual(save.label, "Save a capsule")
        XCTAssertEqual(later.label, "Not now")
        XCTAssertGreaterThanOrEqual(offer.frame.minY, app.capture.frame.maxY, "under the well")
        _ = offer.settledFrame
        sleep(1)
        keepShot("capsule-offer")

        // Save a capsule: the system's save screen, which this test leaves
        save.tap()
        let cancel = app.buttons["Cancel"].firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 20), "the system's save screen")
        sleep(1)
        keepShot("capsule-save-screen")
        cancel.tap()
        XCTAssertTrue(waitGone(cancel), "the save screen closed")
        XCTAssertFalse(offer.exists, "the offer went with Save a capsule")
        XCTAssertFalse(app.staticTexts["Capsule saved."].exists, "nothing saved, so no notice")

        // the More menu offers both
        app.more.tap()
        XCTAssertTrue(app.buttons[CapsuleText.saveMore].firstMatch.waitForExistence(timeout: 10), "More has Save a capsule…")
        XCTAssertTrue(app.buttons[CapsuleText.openMore].firstMatch.exists, "More has Open a capsule…")
        keepShot("capsule-more")
    }

    /// Not now puts the offer away, and leaves the ideas as they were. (That
    /// it doesn't come back within the month is CapsuleOffer's, in
    /// CapsuleTests: a launch here has just seen ideas, so it is never due.)
    func testNotNowPutsTheOfferAway() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64(), "-SortaCapsuleOffer"])
        importExport(app)
        let offer = app.descendants(matching: .any)["capsule-offer"].firstMatch
        XCTAssertTrue(offer.waitForExistence(timeout: 10), "the offer under the well")
        app.buttons["capsule-offer-later"].tap()
        XCTAssertTrue(waitGone(offer), "Not now puts it away")
        XCTAssertTrue(app.folderRow("Garden").exists, "the ideas as they were")
        XCTAssertFalse(app.staticTexts["Capsule saved."].exists, "nothing saved")
    }

    /// An idea's card on the board, not its row beside it on two panes.
    private func card(_ app: XCUIApplication, _ title: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@ AND identifier BEGINSWITH %@", title, "board-card")).firstMatch
    }

    /// A lens chip is read "<name>, <n> idea(s)", is selected while on, and
    /// is inside the window and at least 44 points.
    private func chip(_ app: XCUIApplication, _ id: String, _ name: String, _ count: Int, on: Bool, _ window: CGRect,
                      file: StaticString = #filePath, line: UInt = #line) {
        let chip = app.buttons["map-lens-\(id)"]
        XCTAssertTrue(chip.waitForExistence(timeout: 10), "the \(name) chip", file: file, line: line)
        XCTAssertEqual(chip.label, "\(name), \(IdeasText.count(count))", file: file, line: line)
        XCTAssertEqual(chip.isSelected, on, "\(name) is \(on ? "" : "not ")selected", file: file, line: line)
        let frame = chip.frame
        XCTAssertGreaterThanOrEqual(Self.points(frame.height), 44, "\(name) is 44 tall: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(Self.points(frame.width), 44, "\(name) is 44 wide: \(frame)", file: file, line: line)
    }

    /// Whether `element` has gone within ten seconds.
    private func waitGone(_ element: XCUIElement) -> Bool {
        let start = Date()
        while element.exists && Date().timeIntervalSince(start) < 10 { usleep(200_000) }
        return !element.exists
    }
}

/// The capsule's words the tests look for (IdeasWords).
enum CapsuleText {
    static let offer = "Keep a copy of your ideas. A capsule is one file that opens in any browser, and Sorta can open it again."
    static let saveMore = "Save a capsule\u{2026}"
    static let openMore = "Open a capsule\u{2026}"
}
