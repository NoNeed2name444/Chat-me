import XCTest

/// What the Ideas UI tests share: a launch on a fresh store of its own, the
/// words they look for (S05's ux-writing pass, iOS), and a made-up
/// Stethoscore backup for the import. Nothing here is a real idea, and none
/// of it is medical.

/// The words the screens show that the tests look for, kept in one place so
/// a change of wording is one change here (IdeasWords holds the app's).
enum IdeasText {
    static let sortIt = "Sort it"
    static let toSort = "To sort"
    static let undo = "Undo"
    static let cancel = "Cancel"
    static let create = "Create"
    static let rename = "Rename"
    static let done = "Done"
    static let delete = "Delete"
    static let chooseFolder = "Choose folder"
    static let newFolder = "New folder"
    static let renameFolder = "Rename folder"
    static let deleteFolder = "Delete folder"
    static let importFromStethoscore = "Import from Stethoscore\u{2026}"
    static let allSorted = "All sorted."
    static let ideaDeleted = "Idea deleted."
    static let backInToSort = "Back in To sort."
    static let linkTo = "Link to an idea\u{2026}"
    static let importedTitle = "Ideas imported"
    static let importedBody = "Added 4 ideas, 4 folders and 4 links. 3 ideas keep their place on the 2D map."
    static let nothingNewTitle = "Nothing new to add"
    static let refusedTitle = "Can't import this file"
    static let refusedNotBackup = "This file isn't a Stethoscore backup. Choose another file."
    static let refusedNewer = "This backup is from a newer version of Stethoscore. Check for a Sorta update, then try again."

    static func count(_ n: Int) -> String { n == 1 ? "1 idea" : "\(n) ideas" }
    static func toSortTitle(_ n: Int) -> String { "To sort, \(count(n))" }
    static func folderTitle(_ name: String, _ n: Int) -> String { "\(name), \(count(n))" }
    static func movedTo(_ folder: String) -> String { "Moved to \(folder)." }
}

/// One test's store: a name of its own, so the app opens a new file in its
/// temporary folder (`-SortaStore`, IdeasDesk.storeURL); the same name
/// again reopens that file.
struct IdeasStoreName {
    let name: String = "ui-" + UUID().uuidString.prefix(12).lowercased()
}

extension XCTestCase {
    /// Sorta on the Sort screen, on the store `store`. Notices stay until
    /// the next one (`-SortaNoticesStay`), as under VoiceOver, so a slow
    /// simulator still reaches Undo.
    func launchIdeas(_ store: IdeasStoreName, _ more: [String] = [],
                     file: StaticString = #filePath, line: UInt = #line) -> XCUIApplication {
        let app = XCUIApplication()
        app.keepTraceOffConsole()
        app.launchArguments += ["-SortaStore", store.name, "-SortaNoticesStay"] + more
        app.launch()
        XCTAssertTrue(app.textFields["idea-capture"].waitForExistence(timeout: 30),
                      "the Sort screen is open, with capture", file: file, line: line)
        return app
    }

    /// Keeps a picture of the screen, named, for the renders (A6).
    func keepShot(_ name: String) {
        let shot = uprightShot()
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    /// Imports the made-up export from More (the app launched with
    /// `-SortaImportFile`), keeping a picture of its message as `name`.
    /// Shared by IdeasRenderTests and MapModeRenderTests.
    func importExport(_ app: XCUIApplication, keep name: String? = nil) {
        app.more.tap()
        app.buttons[IdeasText.importFromStethoscore].firstMatch.tap()
        let added = app.alerts[IdeasText.importedTitle]
        XCTAssertTrue(added.waitForExistence(timeout: 10), "the import added the made-up ideas")
        if let name {
            sleep(1)
            keepShot(name)
        }
        added.buttons[IdeasText.done].tap()
        XCTAssertTrue(app.folderRow("Garden").waitForExistence(timeout: 10))
    }

    /// The control is on screen, inside the window across, and at least 44
    /// by 44 points.
    func assertInside(_ element: XCUIElement, _ window: CGRect, _ name: String,
                      file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.exists, "\(name) is on screen", file: file, line: line)
        let frame = element.frame
        XCTAssertGreaterThanOrEqual(frame.minX, window.minX - 0.5, "\(name) isn't cut at the left: \(frame)", file: file, line: line)
        XCTAssertLessThanOrEqual(frame.maxX, window.maxX + 0.5, "\(name) isn't cut at the right: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(Self.points(frame.height), 44, "\(name) is at least 44 tall: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(Self.points(frame.width), 44, "\(name) is at least 44 wide: \(frame)", file: file, line: line)
    }

    /// A length as drawn, without the float noise of a frame read back from
    /// pixels: a 44 point row at y 124.333 on a 3x screen reads 43.99999999999999
    /// (S05c). The smallest real shortfall is a pixel, a third of a point.
    static func points(_ length: CGFloat) -> CGFloat {
        (length * 1_000_000).rounded() / 1_000_000
    }
}

extension XCUIApplication {
    var capture: XCUIElement { textFields["idea-capture"] }
    var sortIt: XCUIElement { buttons["sort-it"] }
    var more: XCUIElement { buttons["more"] }
    var ideasTitle: XCUIElement { descendants(matching: .any).matching(identifier: "ideas-title").firstMatch }
    var notice: XCUIElement { descendants(matching: .any).matching(identifier: "notice").firstMatch }
    var empty: XCUIElement { descendants(matching: .any).matching(identifier: "ideas-empty").firstMatch }
    var upRow: XCUIElement { buttons["up-row"] }
    var ideaRows: XCUIElementQuery { buttons.matching(identifier: "idea-row") }
    var folderRows: XCUIElementQuery { buttons.matching(identifier: "folder-row") }
    var chooserToSort: XCUIElement { buttons["chooser-to-sort"] }
    var nameField: XCUIElement { textFields["nameSheetField"] }

    func ideaRow(_ text: String) -> XCUIElement {
        ideaRows.matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    /// The Choose folder button on the row of this idea.
    func chooseFolder(for text: String) -> XCUIElement {
        buttons.matching(identifier: "choose-folder")
            .matching(NSPredicate(format: "label == %@", "\(IdeasText.chooseFolder) for \(text)")).firstMatch
    }

    func folderRow(_ name: String) -> XCUIElement {
        folderRows.matching(NSPredicate(format: "label BEGINSWITH %@", "\(name), ")).firstMatch
    }

    /// Holds a folder's row, at rest, until its menu shows `item`. On a
    /// slow runner the hold reached the app as a tap: in Chat-me run
    /// 41897740188 the 1.2 s press took 6.7 s to send (2.2 s on the run
    /// before), and its video shows Garden opening, no menu. Only then,
    /// with the up row showing it went in, it goes back up and holds once
    /// more; the menu must still open from a hold.
    func openFolderMenu(_ name: String, showing item: String) {
        let row = folderRow(name)
        XCTAssertTrue(row.waitForExistence(timeout: 10), "\(name)'s row is there")
        _ = row.settledFrame
        row.press(forDuration: 1.2)
        let menuItem = buttons[item].firstMatch
        if !menuItem.waitForExistence(timeout: 5) && upRow.exists {
            XCTContext.runActivity(named: "the hold opened \(name) as a tap; back up, hold again") { _ in
                upRow.tap()
                XCTAssertTrue(row.waitForExistence(timeout: 10), "back beside \(name)")
                _ = row.settledFrame
                row.press(forDuration: 1.2)
            }
        }
        XCTAssertTrue(menuItem.waitForExistence(timeout: 5), "\(name)'s menu is open")
    }

    func chooserFolder(_ name: String) -> XCUIElement {
        buttons.matching(identifier: "chooser-folder").matching(NSPredicate(format: "label == %@", name)).firstMatch
    }

    /// Types an idea into the well and presses Return, then waits for the
    /// well to be ready for the next. Return lets go of the caret and capture
    /// takes it back a moment later (IdeasView.capture): in run 41047476120,
    /// attempt 2, keys typed straight after Return fell in that moment and
    /// only the last, "t", was saved. So the well is at rest first, with the
    /// caret and empty, and the words are checked before Return. On a
    /// stalled runner the first tap after a launch can be lost: in run
    /// 41812442710 the app was busy 27 s after it and never took the caret.
    /// So a tap that brings no caret in 5 s is made once more; the well must
    /// still have the caret, empty, before a key is typed.
    func captureIdea(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        typeDraft(text, file: file, line: line)
        capture.typeText("\n")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: Self.captureReady, object: capture)],
                                      timeout: 10),
                       .completed, "capture took the caret back, empty, after \"\(text)\"", file: file, line: line)
    }

    /// iOS's slide-to-type introduction ("Speed up your typing by sliding
    /// your finger across the letters to compose a word.", Continue) can
    /// take the keyboard's place when a keyboard comes up, and keys sent
    /// then are lost: in Chat-me run 43001371954, attempt 3, "G" of
    /// "Garden" took 31 s to send and found no caret, and the recording
    /// shows the card over the keyboard. Marking it shown in the
    /// simulator's defaults (run_ui_tests.sh) did not hold there, and the
    /// keyboard warm-up had passed before it. So once the keyboard is up,
    /// a card on show is closed with its Continue and the field tapped to
    /// take the caret back. The card is looked for in the app, which hosts
    /// the keyboard, and in SpringBoard.
    @discardableResult
    func clearSlideToTypeIntro(refocus field: XCUIElement) -> Bool {
        guard keyboards.firstMatch.waitForExistence(timeout: 5) else { return false }
        _ = keyboards.firstMatch.settledFrame
        let intro = NSPredicate(format: "label BEGINSWITH %@", "Speed up your typing")
        for owner in [self, XCUIApplication(bundleIdentifier: "com.apple.springboard")] {
            let card = owner.descendants(matching: .any).matching(intro).firstMatch
            guard card.exists else { continue }
            let go = owner.buttons["Continue"].firstMatch
            XCTAssertTrue(go.waitForExistence(timeout: 5), "the slide-to-type card has its Continue")
            go.tap()
            let gone = NSPredicate(format: "exists == false")
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: card)], timeout: 10),
                           .completed, "the slide-to-type card has gone")
            field.tap()
            let focused = NSPredicate(format: "hasKeyboardFocus == true")
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: focused, object: field)], timeout: 10),
                           .completed, "the field has the caret again after the slide-to-type card")
            _ = keyboards.firstMatch.settledFrame
            return true
        }
        return false
    }

    /// The well at rest: it has the caret, and holds nothing.
    static var captureReady: NSPredicate { NSPredicate(format: "hasKeyboardFocus == true AND (value == '' OR value == placeholderValue)") }

    /// Types a draft into the well, with captureIdea's waits, and leaves it
    /// there unsorted: no Return.
    func typeDraft(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let ready = Self.captureReady
        if !(capture.value(forKey: "hasKeyboardFocus") as? Bool ?? false) {
            capture.tap()
            if XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: capture)], timeout: 5) != .completed,
               !(capture.value(forKey: "hasKeyboardFocus") as? Bool ?? false) {
                capture.tap()
            }
        }
        clearSlideToTypeIntro(refocus: capture)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: capture)], timeout: 10),
                       .completed, "capture has the caret and is empty: it holds \(capture.value as? String ?? "nil")",
                       file: file, line: line)
        capture.typeText(text)
        let typed = NSPredicate(format: "value == %@", text)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: typed, object: capture)], timeout: 10),
                       .completed, "capture holds \"\(text)\": it holds \(capture.value as? String ?? "nil")",
                       file: file, line: line)
    }

    /// Makes a folder from More, at the level on screen.
    func makeFolder(_ name: String) {
        more.tap()
        buttons[IdeasText.newFolder].firstMatch.tap()
        XCTAssertTrue(nameField.waitForExistence(timeout: 10), "the naming sheet is open")
        typeName(name)
        buttons[IdeasText.create].firstMatch.tap()
        XCTAssertTrue(folderRow(name).waitForExistence(timeout: 10), "the folder \(name) is listed")
        // the sheet goes and capture takes the caret back, its keyboard
        // rising: a tap on the list before both are at rest did nothing
        // (the up row, Chat-me run 41026251727)
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: nameField)], timeout: 10),
                       .completed, "the naming sheet has gone")
        if keyboards.firstMatch.exists { _ = keyboards.firstMatch.settledFrame }
        _ = folderRow(name).settledFrame
    }

    /// Types a name into the naming sheet's field once the keyboard is up
    /// and the sheet has come to rest, and checks it arrived. The sheet
    /// rises with the keyboard, and keys sent while it moved were lost:
    /// all of them in Chat-me run 40997369293 (the field focused but
    /// empty, Create off), all but the first in run 41014171299 ("G" for
    /// "Garden"). Sent whole, at XCUITest's 60 keys a second, keys were
    /// still dropped on a slow runner with the field focused and at rest:
    /// "Ga" of "Garden" five seconds on (run 41029008128, attempt 3). So
    /// the name goes in a key at a time, and each key must land before the
    /// next is sent; a key that never lands fails here, by its place. A key
    /// has 15 s: in run 41812442710 one took 15 s to send and its check
    /// another 7 s to find the field, and it landed after a 5 s wait.
    func typeName(_ name: String) {
        XCTAssertTrue(keyboards.firstMatch.waitForExistence(timeout: 10), "the keyboard is up")
        _ = keyboards.firstMatch.settledFrame
        _ = nameField.settledFrame
        clearSlideToTypeIntro(refocus: nameField)
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: focused, object: nameField)], timeout: 10),
                       .completed, "the name field has the caret")
        var typed = ""
        for key in name {
            nameField.typeText(String(key))
            typed.append(key)
            let landed = NSPredicate(format: "value == %@", typed)
            let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landed, object: nameField)], timeout: 15)
            XCTAssertEqual(result, .completed,
                           "key \(typed.count) of \"\(name)\" is in the field: it holds \(nameField.value as? String ?? "nil")")
        }
    }

    /// Taps the notice's Undo, at its trailing end: the banner reads as one
    /// element (WardBanner), with Undo, 44 points wide, a WardSpace.m in.
    func tapUndo() {
        XCTAssertTrue(notice.waitForExistence(timeout: 10), "a notice with Undo is on show")
        let frame = notice.frame
        notice.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.width - 34, dy: frame.height / 2)).tap()
    }

    /// Opens the folder chooser from an idea's row.
    func openChooser(for text: String) {
        chooseFolder(for: text).tap()
        XCTAssertTrue(chooserToSort.waitForExistence(timeout: 10), "the folder chooser is open")
    }
}

extension XCUIElement {
    /// Taps it if it is there to tap.
    func tapIfThere() {
        if exists && isHittable { tap() }
    }

    /// Clears a field and types `text` into it. The tap is at the field's
    /// trailing end, so the caret sits after the old words and the deletes
    /// take them (a tap in the middle left it at the start, and the new
    /// words went in front: "Alpha planAlpha", Chat-me run 40983816557).
    /// The new words go in a key at a time, each landing before the next,
    /// as typeName's: sent whole, the field read "Back gard" 0.18 s after
    /// the keys went (Chat-me run 41877111599, attempt 2), and its video
    /// shows "Back garden" a second later. The first key landing alone
    /// shows the deletes took the old words.
    func replaceText(with text: String) {
        coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        if XCUIApplication().clearSlideToTypeIntro(refocus: self) {
            coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        }
        let old = (value as? String) ?? ""
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 2))
        var typed = ""
        for key in text {
            typeText(String(key))
            typed.append(key)
            let landed = NSPredicate(format: "value == %@", typed)
            let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landed, object: self)], timeout: 15)
            XCTAssertEqual(result, .completed,
                           "key \(typed.count) of \"\(text)\" is in the field: it holds \(value as? String ?? "nil")")
        }
        XCTAssertEqual(value as? String, text, "the field holds only the new words")
    }

    /// The frame once it has stopped moving: a sheet is measured at rest,
    /// not while it slides in (the chooser's Cancel read 0.96 of its 44
    /// points mid-presentation, Chat-me run 40983816557).
    var settledFrame: CGRect {
        var last = frame
        for _ in 0..<20 {
            _ = XCTWaiter.wait(for: [XCTestExpectation(description: "settle")], timeout: 0.25)
            let now = frame
            if now == last { return now }
            last = now
        }
        return last
    }
}

/// A made-up Stethoscore backup, written as Stethoscore writes one: a stored
/// zip (MiniZip, the app's own writer, built into this target) with the
/// manifest first and the ideas in notes.json, ISO 8601 dates. The same four
/// ideas and four folders as the unit tests' MadeUpBackup: two top-level
/// folders, each with one inside; ideas in a folder, in a folder inside one
/// and in none; four hand-made links; three ideas placed on the board.
enum MadeUpExport {
    static func id(_ n: Int) -> String { String(format: "00000000-0000-4000-8000-%012X", n) }
    /// The ideas' titles, in the export's order.
    static let titles = ["Paint the shed green", "Seed swap list", "Try a rye starter", "A bell for the bike"]
    /// Two more ideas with no board place, so at (0, 0), as an import leaves
    /// them (`base64(stacked: true)`, S05c).
    static let placeless = ["Oil the gate", "Ask about the allotment waiting list"]
    /// Each idea's stored board place, as its board card's identifier names
    /// it (`IdeaBoardView`, "board-card x y").
    static let places: [String: CGPoint] = [
        "Paint the shed green": CGPoint(x: 120, y: -40), "Seed swap list": .zero,
        "Try a rye starter": CGPoint(x: -75.5, y: 210.25), "A bell for the bike": CGPoint(x: 0, y: 33),
        "Oil the gate": .zero, "Ask about the allotment waiting list": .zero,
    ]

    static let manifest = """
    {"app":"Stethoscore","counts":{"cards":0,"files":0,"notes":NOTES,"questions":0,"scheduled":0,"sets":0,"studyDays":0},\
    "createdAt":"2026-09-20T10:00:00Z","format":"stethoscore-backup","version":VERSION}
    """

    static let notes = """
    {"folders":[\
    {"createdAt":"2026-09-01T10:00:00Z","id":"\(id(101))","name":"Garden"},\
    {"createdAt":"2026-09-01T10:01:00Z","id":"\(id(102))","name":"Raised beds","parentId":"\(id(101))"},\
    {"createdAt":"2026-09-01T10:02:00Z","id":"\(id(103))","name":"Kitchen"},\
    {"createdAt":"2026-09-01T10:03:00Z","id":"\(id(104))","name":"Bread","parentId":"\(id(103))"}],\
    "notes":[\
    {"boardX":120,"boardY":-40,"body":"Two coats, after the rain.","createdAt":"2026-09-02T10:00:00Z",\
    "folderId":"\(id(101))","id":"\(id(1))","kind":"idea","links":[],"tags":[],"title":"Paint the shed green",\
    "updatedAt":"2026-09-02T10:00:00Z"},\
    {"boardX":0,"boardY":0,"body":"Beans and squash.","createdAt":"2026-09-03T10:00:00Z",\
    "folderId":"\(id(102))","id":"\(id(2))","kind":"idea","links":["\(id(1))"],"tags":[],"title":"Seed swap list",\
    "updatedAt":"2026-09-03T10:00:00Z"},\
    {"boardX":-75.5,"boardY":210.25,"body":"Feed it twice a day.","createdAt":"2026-09-04T10:00:00Z",\
    "folderId":"\(id(104))","id":"\(id(3))","kind":"idea","links":["\(id(1))","\(id(2))"],"tags":[],\
    "title":"Try a rye starter","updatedAt":"2026-09-04T10:00:00Z"},\
    {"boardX":0,"boardY":33,"body":"","createdAt":"2026-09-05T10:00:00Z",\
    "id":"\(id(4))","kind":"idea","links":["\(id(50))"],"tags":[],"title":"A bell for the bike",\
    "updatedAt":"2026-09-05T10:00:00Z"}]}
    """

    static let placelessNotes = """
    {"body":"","createdAt":"2026-09-06T10:00:00Z","id":"\(id(5))","kind":"idea","links":[],"tags":[],\
    "title":"Oil the gate","updatedAt":"2026-09-06T10:00:00Z"},\
    {"body":"","createdAt":"2026-09-07T10:00:00Z","id":"\(id(6))","kind":"idea","links":[],"tags":[],\
    "title":"Ask about the allotment waiting list","updatedAt":"2026-09-07T10:00:00Z"}
    """

    /// The backup as base64, for `-SortaImportFile`; `version` 2 is a newer
    /// Stethoscore's; `stacked` adds the two `placeless` ideas.
    static func base64(version: Int = 1, stacked: Bool = false) throws -> String {
        let last = "\"updatedAt\":\"2026-09-05T10:00:00Z\"}]}"
        let ideas: String = stacked ? notes.replacingOccurrences(of: last, with: String(last.dropLast(2)) + "," + placelessNotes + "]}") : notes
        precondition(!stacked || ideas != notes, "the stacked export adds its two ideas")
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("sorta-export-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let parts: [(String, String)] = [
            ("manifest.json", manifest.replacingOccurrences(of: "VERSION", with: "\(version)")
                .replacingOccurrences(of: "NOTES", with: stacked ? "6" : "4")),
            ("notes.json", ideas),
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

    /// A file that isn't a zip at all.
    static let notABackup: String = Data("a shopping list, not a backup".utf8).base64EncodedString()
}

/// Not a check: the first keyboard after a cold boot, typed once before the
/// counted tests (tools/run_ui_tests.sh, the ideas set; the iPhone's too since
/// 5 Nov, when its first test's first key stalled). On a
/// freshly booted iPad the first idea typed in the capture field stalled in
/// UIKit's keyboard code in 3 of 5 runs by day (S05c's notes 2 and 8), and the
/// same test by night passed straight after on the same simulator each time.
/// The script runs this first, notes how it went, and counts only the tests
/// after it; every counted test still has to pass by name.
final class KeyboardWarmUp: XCTestCase {
    func testFirstKeyboard() throws {
        let app = launchIdeas(IdeasStoreName())
        app.captureIdea("Warm up the keyboard")
    }
}
