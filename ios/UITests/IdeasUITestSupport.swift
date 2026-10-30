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
    static let importedTitle = "Ideas imported"
    static let importedBody = "Added 4 ideas, 4 folders and 4 links. 3 ideas keep their place on the map."
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

    func chooserFolder(_ name: String) -> XCUIElement {
        buttons.matching(identifier: "chooser-folder").matching(NSPredicate(format: "label == %@", name)).firstMatch
    }

    /// Types an idea into the well and presses Return.
    func captureIdea(_ text: String) {
        if !(capture.value(forKey: "hasKeyboardFocus") as? Bool ?? false) { capture.tap() }
        capture.typeText(text + "\n")
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
    /// next is sent; a key that never lands fails here, by its place.
    func typeName(_ name: String) {
        XCTAssertTrue(keyboards.firstMatch.waitForExistence(timeout: 10), "the keyboard is up")
        _ = keyboards.firstMatch.settledFrame
        _ = nameField.settledFrame
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: focused, object: nameField)], timeout: 10),
                       .completed, "the name field has the caret")
        var typed = ""
        for key in name {
            nameField.typeText(String(key))
            typed.append(key)
            let landed = NSPredicate(format: "value == %@", typed)
            let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landed, object: nameField)], timeout: 5)
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
    /// Clears a field and types `text` into it. The tap is at the field's
    /// trailing end, so the caret sits after the old words and the deletes
    /// take them (a tap in the middle left it at the start, and the new
    /// words went in front: "Alpha planAlpha", Chat-me run 40983816557).
    func replaceText(with text: String) {
        coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        let old = (value as? String) ?? ""
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 2) + text)
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

    static let manifest = """
    {"app":"Stethoscore","counts":{"cards":0,"files":0,"notes":4,"questions":0,"scheduled":0,"sets":0,"studyDays":0},\
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

    /// The backup as base64, for `-SortaImportFile`; `version` 2 is a newer
    /// Stethoscore's.
    static func base64(version: Int = 1) throws -> String {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("sorta-export-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let parts: [(String, String)] = [
            ("manifest.json", manifest.replacingOccurrences(of: "VERSION", with: "\(version)")),
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

    /// A file that isn't a zip at all.
    static let notABackup: String = Data("a shopping list, not a backup".utf8).base64EncodedString()
}
