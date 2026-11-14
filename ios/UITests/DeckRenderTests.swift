import XCTest

/// S06a A8 on iOS: the sorting deck, "You were saying…", the low tide and
/// the frost, rendered for real on made-up ideas (DeckSeed) and kept as
/// named screenshots, with each shot's own parts checked: the offer and its
/// Carry on, the card and its targets (44 points at least), Keep for later
/// with Undo, a drag onto a folder, the tide's Let go with Undo and Keep,
/// and a board card at each frost step. CI runs it by day and by night on
/// the iPhone and the iPad (tools/run_ui_tests.sh, the ideas set). None of
/// the ideas is medical.
final class DeckRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    func testDeckRenders() throws {
        let window = try deckAndOffer()
        try tide()
        try frost(paned: window.width >= 700)
    }

    /// The offer on To sort, then the deck from the header's toggle.
    private func deckAndOffer() throws -> CGRect {
        let app = launchIdeas(IdeasStoreName(), ["-SortaSeed", try DeckSeed.base64(marked: true),
                                                 "-SortaNow", DeckSeed.now, "-SortaLastTide", DeckSeed.now,
                                                 "-SortaDeck", "off"])
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        let offer = app.descendants(matching: .any)["resume-offer"].firstMatch
        XCTAssertTrue(offer.waitForExistence(timeout: 10), "To sort offers \"You were saying…\"")
        let carryOn = app.buttons["resume-carry-on"].firstMatch
        XCTAssertTrue(carryOn.exists, "the offer's Carry on")
        XCTAssertTrue(carryOn.label.contains(DeckSeed.gate), "Carry on names the idea: \(carryOn.label)")
        assertTarget(carryOn, "Carry on")
        assertTarget(app.buttons["resume-not-now"].firstMatch, "Not now")
        sleep(1)
        keepShot("deck-1-offer")

        app.buttons["resume-not-now"].firstMatch.tap()
        XCTAssertTrue(waitFor { !offer.exists }, "Not now: the offer goes")
        let toggle = app.buttons["deck-toggle"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 10), "the header's deck toggle")
        assertTarget(toggle, "the deck toggle")
        toggle.tap()
        let card = app.descendants(matching: .any)["deck-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the deck")
        XCTAssertEqual(card.label, DeckSeed.gate, "the top card is the newest idea")
        XCTAssertEqual(card.value as? String, "Unfinished", "the unfinished idea says so")
        let targets = app.descendants(matching: .any).matching(identifier: "deck-target").allElementsBoundByIndex
        XCTAssertGreaterThanOrEqual(targets.count, 3, "folders stand round the card")
        for target in targets { assertTarget(target, "the target \(target.label)") }
        assertTarget(app.descendants(matching: .any)["deck-later"].firstMatch, "the Later well")
        assertTarget(app.buttons["deck-keep-later"].firstMatch, "Keep for later")
        assertTarget(app.buttons["deck-choose"].firstMatch, "Choose folder")
        let pane = app.descendants(matching: .any)["deck-folders"].firstMatch
        if window.width >= 700 {
            XCTAssertTrue(pane.waitForExistence(timeout: 10), "two panes: the folders beside the deck")
        } else {
            XCTAssertFalse(pane.exists, "a phone keeps the folders round the card")
        }
        sleep(1)
        keepShot("deck-2-deck")

        // Keep for later, then Undo puts it back on top
        app.buttons["deck-keep-later"].firstMatch.tap()
        XCTAssertTrue(app.notice.waitForExistence(timeout: 10), "a notice")
        XCTAssertTrue(app.notice.label.contains("Kept for later."), "the notice: \(app.notice.label)")
        XCTAssertTrue(waitFor { card.label != DeckSeed.gate }, "the next card is up")
        app.tapUndo()
        XCTAssertTrue(waitFor { card.label == DeckSeed.gate }, "Undo puts it back on top: \(card.label)")

        // a drag onto Garden files it
        let garden = app.descendants(matching: .any).matching(NSPredicate(format: "identifier == %@ AND label == %@",
                                                                          "deck-target", "File in Garden")).firstMatch
        XCTAssertTrue(garden.waitForExistence(timeout: 10), "the Garden target")
        card.press(forDuration: 0.3, thenDragTo: garden, withVelocity: .slow, thenHoldForDuration: 0.3)
        XCTAssertTrue(waitFor { card.label != DeckSeed.gate }, "dropped on Garden, the card is filed")
        XCTAssertTrue(waitFor { app.notice.exists && app.notice.label.contains("Garden") }, "filed in Garden: \(app.notice.label)")

        // back to the list, as the header's toggle is remembered
        toggle.tap()
        XCTAssertTrue(waitFor { !card.exists }, "the list again")
        app.terminate()
        return window
    }

    /// The low tide: due 8 days after the last, three frosted ideas, the
    /// longest unopened first.
    private func tide() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaSeed", try DeckSeed.base64(frosted: true),
                                                 "-SortaNow", DeckSeed.later, "-SortaLastTide", DeckSeed.before(8)])
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        XCTAssertTrue(app.descendants(matching: .any)["low-tide"].firstMatch.waitForExistence(timeout: 10), "the low tide is up")
        let card = app.descendants(matching: .any)["deck-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the tide's card")
        XCTAssertEqual(card.label, DeckSeed.bread, "the idea left longest comes first")
        for id in ["tide-keep", "tide-link", "tide-let-go"] { assertTarget(app.buttons[id].firstMatch, id) }
        sleep(1)
        keepShot("deck-3-tide")

        app.buttons["tide-let-go"].firstMatch.tap()
        XCTAssertTrue(waitFor { card.label == DeckSeed.tulips }, "let go, the next of the three: \(card.label)")
        app.tapUndo()
        XCTAssertTrue(waitFor { card.label == DeckSeed.bread }, "Undo brings it back: \(card.label)")
        app.buttons["tide-keep"].firstMatch.tap()
        XCTAssertTrue(waitFor { card.label == DeckSeed.tulips }, "kept, the next: \(card.label)")
        app.terminate()
    }

    /// The frost on the board at each step, then in Space.
    private func frost(paned: Bool) throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaSeed", try DeckSeed.base64(frosted: true), "-graphFreeze",
                                                 "-SortaNow", DeckSeed.later, "-SortaLastTide", DeckSeed.before(1)])
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        XCTAssertFalse(app.descendants(matching: .any)["low-tide"].firstMatch.exists, "no tide a day after the last")
        if !paned { app.switchMode(to: "mode-map") }
        app.chooseLook("look-2d")
        let cards = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "board-card"))
        XCTAssertTrue(cards.firstMatch.waitForExistence(timeout: 10), "the board")
        let frosted = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label ENDSWITH %@",
                                                       "board-card", "not opened in a while"))
        // the gate opened 10 days ago (none), the boiler 40 (one), the brushes 100 (two), the rest never (three)
        XCTAssertTrue(waitFor { frosted.count == 6 }, "six board cards are frosted, and say so: \(frosted.count)")
        XCTAssertFalse(cards.matching(NSPredicate(format: "label BEGINSWITH %@", DeckSeed.gate)).firstMatch.label
            .hasSuffix("not opened in a while"), "the gate, opened 10 days ago, is clear")
        sleep(1)
        keepShot("deck-4-frost-board")
        app.chooseLook("look-space")
        XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60), "Space")
        sleep(4)
        keepShot("deck-5-frost-space")
        app.chooseLook("look-2d")
        app.terminate()
    }

    private func assertTarget(_ element: XCUIElement, _ name: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 10), "\(name) is there", file: file, line: line)
        let frame = element.frame
        XCTAssertGreaterThanOrEqual(frame.height, 43.5, "\(name) is 44 points tall: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(frame.width, 43.5, "\(name) is 44 points wide: \(frame)", file: file, line: line)
    }

    private func waitFor(_ timeout: TimeInterval = 10, _ condition: @escaping () -> Bool) -> Bool {
        let met = NSPredicate { _, _ in condition() }
        return XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: met, object: nil)], timeout: timeout) == .completed
    }
}

/// The deck renders' made-up store, as Android's IdeasFixture and DeckIdeas:
/// three ideas to sort, Garden and Kitchen with Bulbs inside Garden, and four
/// filed ideas, made on 26 Oct 2026. The renders' clock is a day later
/// (`now`), or 200 days later (`later`) for the frost and the tide.
enum DeckSeed {
    static let now = "2026-10-27T07:33:20Z"
    static let later = "2027-05-15T07:33:20Z"
    /// `days` before `later`.
    static func before(_ days: Int) -> String {
        let at = ISO8601DateFormatter().date(from: later)!.addingTimeInterval(-Double(days) * 86_400)
        return ISO8601DateFormatter().string(from: at)
    }

    static let gate = "Fix the gate latch"
    static let bread = "Try the seeded bread"
    static let tulips = "Plant tulips before the frost"

    private static func id(_ n: Int) -> String { String(format: "00000000-0000-4000-8000-%012X", n) }
    private static func made(_ hour: Int) -> String { String(format: "2026-10-26T%02d:33:20Z", 7 + hour) }

    /// The store file, base64: `marked`, the gate left mid-sentence after
    /// "which hinge"; `frosted`, the gate opened 10 days before `later`,
    /// the boiler 40 and the brushes 100.
    static func base64(marked: Bool = false, frosted: Bool = false) throws -> String {
        func opened(_ days: Int) -> Any { frosted ? before(days) : NSNull() }
        let garden = id(201), kitchen = id(202), bulbs = id(203)
        var notes: [[String: Any]] = [
            ["id": id(11), "title": gate, "createdAt": made(9), "boardX": -180, "boardY": -220, "openedAt": opened(10)],
            ["id": id(12), "title": "Ask the landlord when the boiler service is booked, and whether the hall light can be fixed the same day",
             "createdAt": made(8), "boardX": 160, "boardY": -220, "openedAt": opened(40)],
            ["id": id(13), "title": "Buy brushes", "createdAt": made(7), "boardX": -180, "boardY": -60, "openedAt": opened(100)],
            ["id": id(14), "title": "Paint the shed green", "body": "Two coats, after the rain. Check [[Fix the gate latch]] first.",
             "folderId": garden, "links": [id(13)], "createdAt": made(6), "boardX": 160, "boardY": -60],
            ["id": id(15), "title": "Turn the compost", "folderId": garden, "createdAt": made(5), "boardX": -180, "boardY": 100],
            ["id": id(16), "title": tulips, "body": "Near [[Paint the shed green]].", "folderId": bulbs,
             "createdAt": made(4), "boardX": 160, "boardY": 100],
            ["id": id(17), "title": bread, "folderId": kitchen, "createdAt": made(3), "boardX": 0, "boardY": 260],
        ]
        for k in notes.indices {
            notes[k]["updatedAt"] = notes[k]["createdAt"]
            if notes[k]["openedAt"] is NSNull { notes[k]["openedAt"] = nil }
        }
        if marked {
            notes[0]["body"] = "Ask Sam which hinge fits, then"
            notes[0]["stoppedAt"] = 19
        }
        let folders: [[String: Any]] = [
            ["id": garden, "name": "Garden", "createdAt": "2026-10-26T07:33:20Z"],
            ["id": kitchen, "name": "Kitchen", "createdAt": "2026-10-26T07:33:21Z"],
            ["id": bulbs, "name": "Bulbs", "parentId": garden, "createdAt": "2026-10-26T07:33:22Z"],
        ]
        let data = try JSONSerialization.data(withJSONObject: ["notes": notes, "folders": folders], options: [.sortedKeys])
        return data.base64EncodedString()
    }
}
