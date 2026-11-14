import XCTest

/// Made-up ideas for S06c's flows and renders, as Android's ShapesFixture
/// (IdeaShapesFlowTest.kt): the rain barrel, the newest, shares a rare word
/// with three older ideas (tomatoes, the gutter, the greenhouse), so they
/// are like it; the rest share none. Its words are three sentences. Written
/// as a Stethoscore backup (MadeUpExport's manifest and zip), for
/// `-SortaImportFile`. Nothing medical, and the times are fixed.
enum ShapesExport {
    static let barrel = "Rain barrel for the greenhouse"
    static let barrelBody = "Catch the rain from the gutter. Water the tomatoes with it."
    static let tomatoes = "Tomatoes in pots"
    static let gutter = "Fix the gutter"
    static let gutterBody = "It leaks over the back door."
    static let greenhouse = "Greenhouse heater"
    static let train = "Book the train to the coast"
    /// The ideas like the barrel.
    static let echoes = [tomatoes, gutter, greenhouse]

    private static func idea(_ n: Int, _ title: String, _ body: String, folder: Int, hour: Int, x: Int, y: Int) -> String {
        let made = String(format: "2026-10-01T%02d:00:00Z", hour)
        return "{\"boardX\":\(x),\"boardY\":\(y),\"body\":\"\(body)\",\"createdAt\":\"\(made)\","
            + "\"folderId\":\"\(MadeUpExport.id(folder))\",\"id\":\"\(MadeUpExport.id(n))\",\"kind\":\"idea\","
            + "\"links\":[],\"tags\":[],\"title\":\"\(title)\",\"updatedAt\":\"\(made)\"}"
    }

    static var notes: String {
        let folders: [String] = [(401, "Garden"), (402, "Home"), (403, "Trips"), (404, "Kitchen")].map { n, name in
            "{\"createdAt\":\"2026-10-01T00:00:00Z\",\"id\":\"\(MadeUpExport.id(n))\",\"name\":\"\(name)\"}"
        }
        let ideas: [String] = [
            idea(41, barrel, barrelBody, folder: 401, hour: 9, x: 0, y: 0),
            idea(42, "Paint the shed green", "Two coats.", folder: 401, hour: 6, x: 230, y: -150),
            idea(43, tomatoes, "Cherry ones on the balcony, in the sun.", folder: 401, hour: 5, x: -230, y: -150),
            idea(44, gutter, gutterBody, folder: 402, hour: 4, x: -230, y: 150),
            idea(45, greenhouse, "A small one, for the frost.", folder: 401, hour: 3, x: 230, y: 150),
            idea(46, "Try the seeded bread", "", folder: 404, hour: 2, x: 0, y: -280),
            idea(47, train, "Saturday, early. Bring the kite.", folder: 403, hour: 1, x: 0, y: 280),
        ]
        return "{\"folders\":[" + folders.joined(separator: ",") + "],\"notes\":[" + ideas.joined(separator: ",") + "]}"
    }

    static func base64() throws -> String {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("sorta-shapes-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let parts: [(String, String)] = [
            ("manifest.json", MadeUpExport.manifest.replacingOccurrences(of: "VERSION", with: "1")
                .replacingOccurrences(of: "NOTES", with: "7")),
            ("notes.json", notes),
        ]
        var files: [(name: String, file: URL)] = []
        for (index, part) in parts.enumerated() {
            let file = folder.appendingPathComponent("part\(index)")
            try Data(part.1.utf8).write(to: file)
            files.append((part.0, file))
        }
        let zip = folder.appendingPathComponent("Stethoscore Backup.stethoscorebackup")
        try MiniZip.write(files: files, to: zip)
        return try Data(contentsOf: zip).base64EncodedString()
    }
}

/// The words S06c's screens show that the tests look for (IdeasWords holds
/// the app's; Android's strings.xml the same).
enum ShapesText {
    static let split = "Split\u{2026}"
    static let join = "Join with\u{2026}"
    static let collide = "Collide with\u{2026}"
    static let collideTitle = "What if both?"
    static let splitDone = "Idea split in two."
    static let joinDone = "Ideas joined."
    static let collideSaved = "New idea in To sort."
    static func linked(_ line: String) -> String { "Linked to \u{201C}\(line)\u{201D}." }
}

extension XCTestCase {
    /// Sorta on the shapes' ideas, imported, with the rain barrel open in
    /// the editor: a sheet on a phone, beside the map on two panes.
    func openBarrel(_ more: [String] = [], file: StaticString = #filePath, line: UInt = #line) throws -> XCUIApplication {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try ShapesExport.base64()] + more,
                              file: file, line: line)
        importExport(app)
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        app.folderRow("Garden").tap()
        let row = app.ideaRow(ShapesExport.barrel)
        XCTAssertTrue(row.waitForExistence(timeout: 10), "the barrel in Garden", file: file, line: line)
        app.openEditor(from: row, file: file, line: line)
        return app
    }
}

extension XCUIApplication {
    var echoChips: XCUIElementQuery { buttons.matching(identifier: "idea-echo") }

    func echoChip(_ title: String) -> XCUIElement {
        echoChips.matching(NSPredicate(format: "label == %@", title)).firstMatch
    }

    /// The editor's ideas like this one, scrolled to once if they are
    /// below the screen's edge.
    func echoStrip(file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let strip = descendants(matching: .any).matching(identifier: "idea-echoes").firstMatch
        if !strip.waitForExistence(timeout: 10) {
            descendants(matching: .any).matching(identifier: "editor-title").firstMatch.swipeUp()
        }
        XCTAssertTrue(strip.waitForExistence(timeout: 10), "the ideas like this one", file: file, line: line)
        return strip
    }

    /// One of the editor's More choices.
    func editorMore(_ item: String, file: StaticString = #filePath, line: UInt = #line) {
        let more = buttons["editor-more"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10), "the editor's More", file: file, line: line)
        more.tap()
        let choice = buttons[item].firstMatch
        XCTAssertTrue(choice.waitForExistence(timeout: 5), "More offers \(item)", file: file, line: line)
        choice.tap()
    }

    /// The notice saying `text`.
    func notice(saying text: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: "notice")
            .matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    /// Taps Undo on the notice saying `text` (WardBanner: Undo at its
    /// trailing end, 44 points wide, a WardSpace.m in).
    func undo(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let banner = notice(saying: text)
        XCTAssertTrue(banner.waitForExistence(timeout: 10), "\"\(text)\" with Undo", file: file, line: line)
        let frame = banner.frame
        banner.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.width - 34, dy: frame.height / 2)).tap()
    }

    /// The other idea in the one-idea picker.
    func pick(_ title: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(descendants(matching: .any)["shape-picker"].firstMatch.waitForExistence(timeout: 10),
                      "the picker", file: file, line: line)
        let row = buttons.matching(identifier: "shape-pick").matching(NSPredicate(format: "label BEGINSWITH %@", title)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), "\(title) in the picker", file: file, line: line)
        row.tap()
    }

    /// What the editor's details hold now.
    var editorBody: String { (textViews["editor-body"].firstMatch.value as? String) ?? "" }

    /// Waits until the editor's details read `text`.
    func waitForBody(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let body = textViews["editor-body"].firstMatch
        let reads = NSPredicate(format: "value == %@", text)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: reads, object: body)], timeout: 10),
                       .completed, "the details read \"\(text)\", not \"\(editorBody)\"", file: file, line: line)
    }
}

/// S06c A2-A6 on the iPhone, from the editor's plain ways: the ideas like
/// the open one, and a tap that links one with Undo; Split…, Join with… and
/// Collide with…, each with Undo or Cancel; and Collide from the map's tool.
/// Android's IdeaShapesFlowTest asks the same.
final class IdeaShapesUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        Retaps.reset()
    }

    func testTheIdeasLikeItShowAndATapLinksOneWithUndo() throws {
        let app = try openBarrel()
        _ = app.echoStrip()
        XCTAssertEqual(app.echoChips.count, 3, "the three older ideas with a rare word of it")
        for title in ShapesExport.echoes {
            XCTAssertTrue(app.echoChip(title).exists, "\(title) is like it")
        }
        app.echoChip(ShapesExport.tomatoes).tap()
        XCTAssertTrue(app.notice(saying: ShapesText.linked(ShapesExport.tomatoes)).waitForExistence(timeout: 10),
                      "the link is told, with Undo")
        let fewer = NSPredicate(format: "count == 2")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: fewer, object: app.echoChips)], timeout: 10),
                       .completed, "a linked idea is no longer offered")
        app.undo(ShapesText.linked(ShapesExport.tomatoes))
        let back = NSPredicate(format: "count == 3")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: back, object: app.echoChips)], timeout: 10),
                       .completed, "Undo unlinks it, so it is offered again")
    }

    func testSplitAtTheChosenSentenceAndUndoPutsItBack() throws {
        let app = try openBarrel()
        app.editorMore(ShapesText.split)
        XCTAssertTrue(app.descendants(matching: .any)["split-chooser"].firstMatch.waitForExistence(timeout: 10),
                      "the split chooser")
        // the second sentence ends the first idea
        let sentence = app.buttons.matching(identifier: "split-sentence")
            .matching(NSPredicate(format: "label == %@", "Catch the rain from the gutter.")).firstMatch
        XCTAssertTrue(sentence.waitForExistence(timeout: 5), "the details' first sentence is a choice")
        sentence.tap()
        app.buttons["split-confirm"].firstMatch.tap()
        XCTAssertTrue(app.notice(saying: ShapesText.splitDone).waitForExistence(timeout: 10), "the split is told")
        app.waitForBody("Catch the rain from the gutter.")
        app.undo(ShapesText.splitDone)
        app.waitForBody(ShapesExport.barrelBody)
    }

    func testJoinWithJoinsAndUndoSplitsThemBack() throws {
        let app = try openBarrel()
        app.editorMore(ShapesText.join)
        app.pick(ShapesExport.gutter)
        XCTAssertTrue(app.notice(saying: ShapesText.joinDone).waitForExistence(timeout: 10), "the join is told")
        app.waitForBody(ShapesExport.barrelBody + "\n\n" + ShapesExport.gutter + "\n\n" + ShapesExport.gutterBody)
        app.undo(ShapesText.joinDone)
        app.waitForBody(ShapesExport.barrelBody)
    }

    func testCollideWithCancelLeavesNothingAndSaveMakesAnIdeaInToSort() throws {
        let app = try openBarrel()
        let card = app.descendants(matching: .any)["collide-card"].firstMatch
        app.editorMore(ShapesText.collide)
        app.pick(ShapesExport.train)
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the collide card")
        XCTAssertTrue(app.staticTexts[ShapesText.collideTitle].exists, "\"\(ShapesText.collideTitle)\"")
        XCTAssertFalse(app.buttons["collide-save"].isEnabled, "nothing written, nothing to save")
        // Cancel leaves nothing
        app.buttons["shape-cancel"].firstMatch.tap()
        XCTAssertTrue(card.waitForNonExistence(timeout: 10), "the card went")
        // again, and saved
        app.editorMore(ShapesText.collide)
        app.pick(ShapesExport.train)
        let field = app.descendants(matching: .any)["collide-field"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "the card's field")
        field.tap()
        field.typeText("A rain barrel at the station")
        app.buttons["collide-save"].firstMatch.tap()
        XCTAssertTrue(app.notice(saying: ShapesText.collideSaved).waitForExistence(timeout: 10), "the new idea is told")
        app.buttons["editor-done"].firstMatch.tap()
        // in To sort, at the top
        if app.upRow.waitForExistence(timeout: 5) { app.upRow.tap() }
        XCTAssertTrue(app.ideaRow("A rain barrel at the station").waitForExistence(timeout: 10),
                      "the new idea is in To sort")
    }

    func testTheMapsCollideToolOffersAFarPairAndAnother() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try ShapesExport.base64()])
        importExport(app)
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        app.switchMode(to: "mode-map")
        app.chooseLook("look-2d")
        let tool = app.buttons["collide-tool"].firstMatch
        XCTAssertTrue(tool.waitForExistence(timeout: 10), "the map's Collide tool")
        tool.tap()
        let card = app.descendants(matching: .any)["collide-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the collide card, with a pair")
        let another = app.buttons["collide-another"].firstMatch
        XCTAssertTrue(another.exists, "Collide chose the pair, so it offers another")
        another.tap()
        XCTAssertTrue(card.waitForExistence(timeout: 5), "another pair, on the same card")
        app.buttons["shape-cancel"].firstMatch.tap()
        XCTAssertTrue(card.waitForNonExistence(timeout: 10), "Cancel leaves nothing")
    }
}
