import UIKit
import XCTest

/// S06b rendered for real (A9), each kept as a named screenshot: comets
/// with tails of three ages, a loose idea leaning towards the folder whose
/// word it shares and its card, two ideas shared from the same place
/// circling each other, the moons row and a lit day in Space and on the
/// board, the settle as an idea is filed, the folder's words sheet, the
/// share sheet and the source mark it leaves. CI runs it by day and by
/// night on the iPhone (tools/run_ui_tests.sh, the ideas set). The ideas
/// are made up and not medical; their dates count back from now, so the
/// tails and the moons are the same on any day.
final class SkyIdeasRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    /// Space, still: the tails, the lean and its card, the pair, the moons
    /// and a lit day; then the lit day on the board.
    func testSkyRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try SkyExport.base64(), "-graphFreeze"])
        importExport(app)
        openSpace(app)
        keepShot("sky-1-tails-and-pair")

        // A4: the leaning idea's card says what it shares, and with which folder
        let leaning = body(app, SkyExport.leaning)
        XCTAssertTrue(leaning.waitForExistence(timeout: 20), "the leaning idea on the map")
        leaning.tap()
        let lean = app.descendants(matching: .any)["graphPeekLean"].firstMatch
        XCTAssertTrue(lean.waitForExistence(timeout: 15), "the card's lean line")
        XCTAssertEqual(lean.label, "Shares ‘garden’ with Raised beds")
        let fileThere = app.buttons["graphPeekFileThere"]
        XCTAssertTrue(fileThere.exists, "File there")
        XCTAssertEqual(fileThere.label, "File there, in Raised beds")
        assertTarget(fileThere.frame, "File there")
        sleep(1)
        keepShot("sky-2-lean-card")

        // A8: a shared idea's card names where it came from
        body(app, SkyExport.shared[1]).tap()
        let mark = app.descendants(matching: .any)["sharedFromMark"].firstMatch
        XCTAssertTrue(mark.waitForExistence(timeout: 15), "the card's source mark")
        XCTAssertEqual(mark.label, "Shared from example.org")
        sleep(1)
        keepShot("sky-3-shared-card")

        // A5: the moons, from the strip's Days chip
        let chip = app.descendants(matching: .any)["map-days-chip"].firstMatch
        XCTAssertTrue(chip.waitForExistence(timeout: 10), "the Days chip")
        assertTarget(chip.frame, "Days")
        chip.tap()
        let today = app.buttons["moon-0"]
        XCTAssertTrue(today.waitForExistence(timeout: 10), "today's moon")
        assertTarget(today.frame, "a moon")
        XCTAssertTrue(today.label.hasSuffix(", \(SkyExport.today.count) ideas"), "today's moon is read with its count: \(today.label)")
        XCTAssertTrue(app.buttons["moon-1"].label.hasSuffix(", no ideas"), "yesterday had none")
        sleep(1)
        keepShot("sky-4-moons")

        // A6: today lit, the rest dimmed; Clear ends it
        today.tap()
        XCTAssertTrue(today.isSelected, "today's moon is picked")
        let clear = app.buttons["moons-clear"]
        XCTAssertTrue(clear.waitForExistence(timeout: 5), "Clear, with a day lit")
        sleep(2)
        keepShot("sky-5-lit-day-space")
        app.chooseLook("look-2d")
        XCTAssertTrue(app.boardCard(SkyExport.today[0]).waitForExistence(timeout: 10), "2D: the board")
        XCTAssertTrue(app.buttons["moon-0"].waitForExistence(timeout: 5), "the moons stay open on the board")
        sleep(1)
        keepShot("sky-6-lit-day-board")
        app.buttons["moons-clear"].tap()
        XCTAssertTrue(gone(app.buttons["moons-clear"]), "Clear ends the lit day")
        XCTAssertFalse(app.buttons["moon-0"].isSelected, "no moon picked")
        app.buttons["moons-done"].tap()
        XCTAssertTrue(gone(app.buttons["moon-0"]), "Done closes the moons")
    }

    /// A2: File there, lively, shot as the idea settles into the folder's
    /// system; and A3: the folder's words sheet.
    func testFilingSettlesAndTheWordsSheet() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try SkyExport.base64()])
        importExport(app)
        openSpace(app)
        // lively, the comet is on its way round: a tap can land where it
        // just was, so it is tapped again until its card shows
        let fileThere = app.buttons["graphPeekFileThere"]
        for _ in 0..<6 where !fileThere.exists {
            body(app, SkyExport.leaning).tap()
            _ = fileThere.waitForExistence(timeout: 3)
        }
        XCTAssertTrue(fileThere.exists, "File there, on the leaning comet's card")
        fileThere.tap()
        // on its way into the folder's system (CometSettle: 0.9 s), then there
        keepShot("sky-7-settle")
        keepShot("sky-7-settle-later")
        XCTAssertTrue(app.staticTexts["Moved to Raised beds."].waitForExistence(timeout: 10), "the filing's notice")

        app.switchMode(to: "mode-sort")
        app.folderRow("Garden").tap()
        app.openFolderMenu("Raised beds", showing: "Folder words…")
        app.buttons["Folder words…"].firstMatch.tap()
        let footnote = app.staticTexts["nameSheetFootnote"]
        XCTAssertTrue(footnote.waitForExistence(timeout: 10), "the words sheet's footnote")
        XCTAssertTrue(footnote.label.contains("Saves: garden, soil"), "what a save keeps: \(footnote.label)")
        sleep(1)
        keepShot("sky-8-folder-words")
    }

    /// A7 and A8: a share opens the sheet; a word tapped is left out; the
    /// idea saved in To sort shows where it came from.
    func testShareSheetKeepsTheChosenWords() throws {
        let text = "Bike paths map for the weekend https://www.example.org/paths"
        let app = launchIdeas(IdeasStoreName(), ["-SortaShare", text])
        let sheet = app.descendants(matching: .any)["share-sheet"].firstMatch
        XCTAssertTrue(sheet.waitForExistence(timeout: 15), "the share sheet")
        let save = app.buttons["share-save"]
        assertTarget(save.frame, "Save")
        for word in ["for", "the", "weekend"] {
            let chip = app.buttons[word].firstMatch
            XCTAssertTrue(chip.exists, "the word \(word)")
            assertTarget(chip.frame, word)
            chip.tap()
            XCTAssertEqual(chip.value as? String, "Left out", "\(word) is left out")
        }
        sleep(1)
        keepShot("sky-9-share-sheet")
        save.tap()
        let row = app.ideaRow("Bike paths map https://www.example.org/paths")
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the kept words, a new idea in To sort")
        app.openEditor(from: row)
        let mark = app.descendants(matching: .any)["sharedFromMark"].firstMatch
        XCTAssertTrue(mark.waitForExistence(timeout: 10), "the editor's source mark")
        XCTAssertEqual(mark.label, "Shared from example.org")
        sleep(1)
        keepShot("sky-10-source-mark")
    }

    // MARK: helpers

    private func openSpace(_ app: XCUIApplication) {
        if app.windows.firstMatch.frame.width < 700 { app.switchMode(to: "mode-map") }
        app.chooseLook("look-space")
        let map = app.descendants(matching: .any)["graph3D"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 60), "Space: the 3D map")
        XCTAssertTrue(body(app, SkyExport.leaning).waitForExistence(timeout: 60), "the bodies are laid out")
        // the scene's first frames and its name pills
        sleep(3)
    }

    private func body(_ app: XCUIApplication, _ title: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "graphBody")
            .matching(NSPredicate(format: "label == %@", title)).firstMatch
    }

    private func gone(_ element: XCUIElement) -> Bool {
        let missing = NSPredicate(format: "exists == false")
        return XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: missing, object: element)], timeout: 10) == .completed
    }

    private func assertTarget(_ frame: CGRect, _ name: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertGreaterThanOrEqual(frame.width, 43.5, "\(name) is a target wide: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(frame.height, 43.5, "\(name) is a target tall: \(frame)", file: file, line: line)
    }
}

/// The made-up ideas S06b's renders import: a garden folder with Raised
/// beds inside it, whose words are "garden" and "soil"; ideas in no folder
/// caught hours, days, weeks and over a month ago; one that shares
/// "garden"; and two shared from example.org.
enum SkyExport {
    static let leaning = "Water the garden path"
    static let shared = ["Bike paths map", "Night ride route"]
    /// The ideas caught today.
    static let today = [leaning] + shared

    static func id(_ n: Int) -> String { MadeUpExport.id(n) }

    static func base64() throws -> String {
        let now = Date()
        let iso = ISO8601DateFormatter()
        func ago(_ hours: Double) -> String { iso.string(from: now.addingTimeInterval(-hours * 3_600)) }
        // today's three spread over the part of today gone by, so all three
        // are today's whenever the test runs
        let hoursToday: Double = now.timeIntervalSince(Calendar.current.startOfDay(for: now)) / 3_600
        func note(_ n: Int, _ title: String, _ hours: Double, folder: Int? = nil, from: String? = nil) -> String {
            var fields: [String] = ["\"body\":\"\"", "\"createdAt\":\"\(ago(hours))\"", "\"id\":\"\(id(n))\"",
                                    "\"kind\":\"idea\"", "\"links\":[]", "\"tags\":[]", "\"title\":\"\(title)\"",
                                    "\"updatedAt\":\"\(ago(hours))\""]
            if let folder { fields.append("\"folderId\":\"\(id(folder))\"") }
            if let from { fields.append("\"sharedFrom\":\"\(from)\"") }
            return "{" + fields.joined(separator: ",") + "}"
        }
        let folders = """
        {"createdAt":"\(ago(2_000))","id":"\(id(101))","name":"Garden"},\
        {"createdAt":"\(ago(2_000))","id":"\(id(102))","name":"Raised beds","parentId":"\(id(101))",\
        "words":["garden","soil"]},\
        {"createdAt":"\(ago(2_000))","id":"\(id(103))","name":"Kitchen"}
        """
        let ideas: [String] = [
            note(1, "Paint the shed green", 1_000, folder: 101),
            note(2, "Seed swap list", 900, folder: 102),
            note(3, "Try a rye starter", 800, folder: 103),
            note(4, leaning, hoursToday * 0.5),
            note(5, shared[0], hoursToday * 0.8, from: "example.org"),
            note(6, shared[1], hoursToday * 0.2, from: "example.org"),
            note(7, "A bell for the bike", 3 * 24),
            note(8, "Fix the shed door", 10 * 24),
            note(9, "Oil the gate", 40 * 24),
        ]
        let json = "{\"folders\":[" + folders + "],\"notes\":[" + ideas.joined(separator: ",") + "]}"
        return try MadeUpExport.base64(notes: json, count: ideas.count)
    }
}
