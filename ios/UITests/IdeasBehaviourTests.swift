import XCTest

/// The Sort screen and the editor, used as a person uses them (S05, A4, A5
/// and A7's targets). Each test starts on a fresh store of its own
/// (`-SortaStore`); the relaunch checks reopen the same one.
///
/// Light or dark is set on the simulator from outside (tools/run_ui_tests.sh).
final class IdeasBehaviourTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: capture

    /// A tap anywhere in the well before Sort it, its leading room too,
    /// puts the caret in the field. Return saves the idea and keeps the
    /// field ready: the next idea is typed without touching the field
    /// again, and lands on top.
    func testReturnSavesAndKeepsFocus() {
        let app = launchIdeas(IdeasStoreName())
        let well = app.descendants(matching: .any)["capture"]
        XCTAssertLessThan(well.frame.minX + 6, app.capture.frame.minX, "the tap below lands in the well's leading room")
        well.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.5)).withOffset(CGVector(dx: 6, dy: 0)).tap()
        XCTAssertEqual(app.capture.value(forKey: "hasKeyboardFocus") as? Bool, true,
                       "a tap in the well's leading room put the caret in the field")
        app.capture.typeText("Paint the shed\n")
        XCTAssertTrue(app.ideaRow("Paint the shed").waitForExistence(timeout: 10), "Return saved the idea")
        XCTAssertEqual(app.capture.value(forKey: "hasKeyboardFocus") as? Bool, true, "the field kept the caret")
        // typed into whatever has the caret: the field, untouched since
        app.typeText("Oil the gate\n")
        XCTAssertTrue(app.ideaRow("Oil the gate").waitForExistence(timeout: 10), "the second idea was saved")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(2))
        XCTAssertEqual(app.ideaRows.firstMatch.label, "Oil the gate", "the newest is on top")
        XCTAssertEqual((app.capture.value as? String).map { $0 == "" || $0 == "What's on your mind?" }, true,
                       "the field is empty again")
    }

    /// Nothing typed, or only spaces: Return saves nothing, and Sort it is off.
    func testAnEmptyFieldSavesNothing() {
        let app = launchIdeas(IdeasStoreName())
        XCTAssertFalse(app.sortIt.isEnabled, "Sort it is off while the field is blank")
        app.capture.tap()
        app.capture.typeText("\n")
        app.capture.tap()
        app.capture.typeText("    \n")
        XCTAssertFalse(app.sortIt.isEnabled, "spaces alone leave Sort it off")
        XCTAssertTrue(app.empty.exists, "still the empty state")
        XCTAssertEqual(app.ideaRows.count, 0, "nothing was saved")
    }

    /// Sort it saves the idea and opens the chooser; Cancel leaves it in
    /// To sort, where it was saved.
    func testSortItSavesAndOpensTheChooser() {
        let app = launchIdeas(IdeasStoreName())
        app.capture.tap()
        app.capture.typeText("Seed swap list")
        XCTAssertTrue(app.sortIt.isEnabled, "Sort it is on once there are words")
        app.sortIt.tap()
        XCTAssertTrue(app.chooserToSort.waitForExistence(timeout: 10), "the chooser opened")
        app.buttons["chooser-cancel"].tap()
        XCTAssertTrue(app.ideaRow("Seed swap list").waitForExistence(timeout: 10), "the idea is in To sort")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
    }

    // MARK: filing

    /// Choosing a folder files the idea and the counts change; Undo puts it
    /// back.
    func testChoosingAFolderFilesItAndUndoPutsItBack() {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.captureIdea("Plant bulbs")
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10))
        app.openChooser(for: "Plant bulbs")
        app.chooserFolder("Garden").tap()
        XCTAssertTrue(app.staticTexts[IdeasText.allSorted].waitForExistence(timeout: 10), "To sort is empty")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(0))
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 1))
        XCTAssertTrue(app.notice.label.contains(IdeasText.movedTo("Garden")), app.notice.label)
        app.tapUndo()
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10), "Undo put it back")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 0))
    }

    /// Filing from the editor's folder chip offers Undo too, once the editor
    /// closes; Undo puts the idea back in To sort.
    func testFilingFromTheEditorOffersUndo() {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.captureIdea("Plant bulbs")
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10))
        app.ideaRow("Plant bulbs").tap()
        XCTAssertTrue(app.buttons["editor-folder"].waitForExistence(timeout: 10), "the editor opened")
        app.buttons["editor-folder"].tap()
        XCTAssertTrue(app.chooserFolder("Garden").waitForExistence(timeout: 10), "the folder chooser is open")
        app.chooserFolder("Garden").tap()
        XCTAssertTrue(app.buttons.matching(identifier: "editor-folder")
            .matching(NSPredicate(format: "label CONTAINS %@", "Garden")).firstMatch.waitForExistence(timeout: 10),
                      "the chip names the new folder")
        app.buttons["editor-done"].tap()
        XCTAssertTrue(app.staticTexts[IdeasText.allSorted].waitForExistence(timeout: 10), "To sort is empty")
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 1))
        XCTAssertTrue(app.notice.waitForExistence(timeout: 10), "the filing offers Undo")
        XCTAssertTrue(app.notice.label.contains(IdeasText.movedTo("Garden")), app.notice.label)
        app.tapUndo()
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10), "Undo put it back")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 0))
    }

    /// New folder in the chooser makes the folder and files the idea in it.
    func testNewFolderFromTheChooserFilesTheIdea() {
        let app = launchIdeas(IdeasStoreName())
        app.captureIdea("Bake rye bread")
        XCTAssertTrue(app.ideaRow("Bake rye bread").waitForExistence(timeout: 10))
        app.openChooser(for: "Bake rye bread")
        app.buttons["chooser-new-folder"].tap()
        XCTAssertTrue(app.nameField.waitForExistence(timeout: 10), "the naming sheet is open")
        app.nameField.typeText("Kitchen")
        app.buttons[IdeasText.create].firstMatch.tap()
        XCTAssertTrue(app.folderRow("Kitchen").waitForExistence(timeout: 10), "the folder was made")
        XCTAssertEqual(app.folderRow("Kitchen").label, IdeasText.folderTitle("Kitchen", 1), "the idea is in it")
        XCTAssertFalse(app.chooserToSort.exists, "the chooser closed")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(0))
    }

    /// The leading swipe opens the chooser too, and To sort takes an idea
    /// out of its folder.
    func testToSortPutsAnIdeaBack() {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.captureIdea("Prune roses")
        let row = app.ideaRow("Prune roses")
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.swipeRight()
        let swiped = app.buttons.matching(NSPredicate(format: "label == %@", IdeasText.chooseFolder))
            .matching(NSPredicate(format: "identifier != %@", "choose-folder")).firstMatch
        if !app.chooserToSort.exists, swiped.waitForExistence(timeout: 5) { swiped.tap() }
        XCTAssertTrue(app.chooserToSort.waitForExistence(timeout: 10), "the swipe opened the chooser")
        app.chooserFolder("Garden").tap()
        XCTAssertTrue(app.folderRow("Garden").waitForExistence(timeout: 10))
        app.folderRow("Garden").tap()
        XCTAssertTrue(app.ideaRow("Prune roses").waitForExistence(timeout: 10), "the idea is in Garden")
        app.openChooser(for: "Prune roses")
        XCTAssertTrue(app.chooserToSort.isSelected == false, "To sort isn't where it is now")
        app.chooserToSort.tap()
        XCTAssertTrue(app.notice.waitForExistence(timeout: 10))
        XCTAssertTrue(app.notice.label.contains(IdeasText.backInToSort), app.notice.label)
        app.upRow.tap()
        XCTAssertTrue(app.ideaRow("Prune roses").waitForExistence(timeout: 10), "back in To sort")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
    }

    // MARK: folders

    /// A folder opens, takes what is captured inside it, holds a folder of
    /// its own with its own ideas, and goes back up.
    func testFoldersOpenNestAndGoBackUp() {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.folderRow("Garden").tap()
        XCTAssertTrue(app.upRow.waitForExistence(timeout: 10), "the folder opened, with a row back up")
        XCTAssertEqual(app.upRow.label, "Back to \(IdeasText.toSort)")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.folderTitle("Garden", 0))
        app.captureIdea("Plant bulbs")
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10), "captured into Garden")
        app.makeFolder("Raised beds")
        app.folderRow("Raised beds").tap()
        XCTAssertTrue(app.upRow.waitForExistence(timeout: 10))
        XCTAssertEqual(app.upRow.label, "Back to Garden")
        app.captureIdea("Top up the soil")
        XCTAssertTrue(app.ideaRow("Top up the soil").waitForExistence(timeout: 10))
        XCTAssertFalse(app.ideaRow("Plant bulbs").exists, "a nested folder shows its own ideas only")
        app.upRow.tap()
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10))
        XCTAssertFalse(app.ideaRow("Top up the soil").exists)
        XCTAssertEqual(app.folderRow("Raised beds").label, IdeasText.folderTitle("Raised beds", 1))
        app.upRow.tap()
        XCTAssertTrue(app.staticTexts[IdeasText.allSorted].waitForExistence(timeout: 10), "nothing went to To sort")
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 2))
    }

    /// A folder is renamed, then deleted, and what was in it moves up a
    /// level.
    func testAFolderIsRenamedAndDeletedAndItsContentsMoveUp() {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.folderRow("Garden").tap()
        XCTAssertTrue(app.upRow.waitForExistence(timeout: 10))
        app.captureIdea("Plant bulbs")
        app.makeFolder("Raised beds")
        app.upRow.tap()

        app.folderRow("Garden").press(forDuration: 1.2)
        app.buttons[IdeasText.renameFolder].firstMatch.tap()
        XCTAssertTrue(app.nameField.waitForExistence(timeout: 10), "the naming sheet is open")
        app.nameField.replaceText(with: "Back garden")
        app.buttons[IdeasText.rename].firstMatch.tap()
        XCTAssertTrue(app.folderRow("Back garden").waitForExistence(timeout: 10), "renamed")
        XCTAssertFalse(app.folderRow("Garden").exists)

        app.folderRow("Back garden").press(forDuration: 1.2)
        app.buttons[IdeasText.deleteFolder].firstMatch.tap()
        // the confirm's own button, once the menu has gone
        let confirm = app.sheets.buttons[IdeasText.deleteFolder]
        if confirm.waitForExistence(timeout: 10) {
            confirm.tap()
        } else {
            let other = app.buttons[IdeasText.deleteFolder].firstMatch
            XCTAssertTrue(other.waitForExistence(timeout: 5), "deleting a folder asks first")
            other.tap()
        }
        XCTAssertTrue(app.folderRow("Raised beds").waitForExistence(timeout: 10))
        XCTAssertTrue(app.ideaRow("Plant bulbs").waitForExistence(timeout: 10), "its idea moved up to To sort")
        XCTAssertTrue(app.folderRow("Raised beds").exists, "its folder moved up to the top")
        XCTAssertFalse(app.folderRow("Back garden").exists, "the folder is gone")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
    }

    /// An idea is deleted from its row's swipe, and Undo brings it back.
    func testAnIdeaIsDeletedAndUndoBringsItBack() {
        let app = launchIdeas(IdeasStoreName())
        app.captureIdea("Oil the gate")
        let row = app.ideaRow("Oil the gate")
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.swipeLeft()
        // a long swipe deletes at once; a short one shows Delete
        let delete = app.buttons[IdeasText.delete].firstMatch
        if row.exists, delete.waitForExistence(timeout: 5) { delete.tap() }
        XCTAssertTrue(app.notice.waitForExistence(timeout: 10))
        XCTAssertTrue(app.notice.label.contains(IdeasText.ideaDeleted), app.notice.label)
        XCTAssertFalse(app.ideaRow("Oil the gate").exists, "deleted")
        app.tapUndo()
        XCTAssertTrue(app.ideaRow("Oil the gate").waitForExistence(timeout: 10), "Undo brought it back")
        XCTAssertEqual(app.ideasTitle.label, IdeasText.toSortTitle(1))
    }

    // MARK: the editor and the store

    /// The editor changes a title, the details and a link; with a filing,
    /// each is there after a relaunch on the same store (A5).
    func testEditsAndFilingsAreThereAfterARelaunch() {
        let store = IdeasStoreName()
        var app = launchIdeas(store)
        app.makeFolder("Garden")
        app.captureIdea("Beta")
        app.captureIdea("Alpha")
        XCTAssertTrue(app.ideaRow("Alpha").waitForExistence(timeout: 10))
        app.openChooser(for: "Beta")
        app.chooserFolder("Garden").tap()
        XCTAssertTrue(app.notice.waitForExistence(timeout: 10))

        app.ideaRow("Alpha").tap()
        let title = app.textViews["editor-title"].exists ? app.textViews["editor-title"] : app.textFields["editor-title"]
        XCTAssertTrue(title.waitForExistence(timeout: 10), "the editor opened")
        title.replaceText(with: "Alpha plan")
        let body = app.textViews["editor-body"]
        body.tap()
        body.typeText("Dig on Monday")
        app.buttons["editor-link"].tap()
        let beta = app.buttons.matching(identifier: "picker-idea").matching(NSPredicate(format: "label == %@", "Beta")).firstMatch
        XCTAssertTrue(beta.waitForExistence(timeout: 10), "the link picker lists the other idea")
        beta.tap()
        XCTAssertTrue(beta.isSelected, "linked")
        app.buttons["picker-done"].tap()
        XCTAssertTrue(app.buttons.matching(identifier: "editor-link-row")
            .matching(NSPredicate(format: "label CONTAINS %@", "Beta")).firstMatch.waitForExistence(timeout: 10),
                      "the link shows in the editor")
        app.buttons["editor-done"].tap()
        XCTAssertTrue(app.ideaRow("Alpha plan").waitForExistence(timeout: 10), "the new title is on its row")
        // the store writes off the main thread: let it land
        sleep(2)
        app.terminate()

        app = launchIdeas(store)
        XCTAssertTrue(app.ideaRow("Alpha plan").waitForExistence(timeout: 10), "the title is kept")
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 1), "the filing is kept")
        app.ideaRow("Alpha plan").tap()
        let reopened = app.textViews["editor-body"]
        XCTAssertTrue(reopened.waitForExistence(timeout: 10))
        XCTAssertEqual(reopened.value as? String, "Dig on Monday", "the details are kept")
        XCTAssertTrue(app.buttons.matching(identifier: "editor-link-row")
            .matching(NSPredicate(format: "label CONTAINS %@", "Beta")).firstMatch.exists, "the link is kept")
    }

    /// A fresh store opens on the empty state, with capture ready to tap.
    func testAFreshStoreShowsTheEmptyState() {
        let app = launchIdeas(IdeasStoreName())
        XCTAssertTrue(app.empty.exists, "the empty state says what to do")
        XCTAssertTrue(app.capture.isHittable, "capture is ready")
        XCTAssertFalse(app.ideasTitle.exists, "no title row on an empty store")
        XCTAssertLessThan(app.empty.frame.maxY, app.windows.firstMatch.frame.height / 3,
                          "the empty state sits in the top third")
    }

    // MARK: the import

    /// A made-up export is imported and its counts shown; a second import
    /// of it adds nothing.
    func testTheImportAddsAndASecondImportAddsNothing() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64()])
        app.more.tap()
        app.buttons[IdeasText.importFromStethoscore].firstMatch.tap()
        let added = app.alerts[IdeasText.importedTitle]
        XCTAssertTrue(added.waitForExistence(timeout: 10), "the import says it added")
        XCTAssertTrue(added.staticTexts[IdeasText.importedBody].exists, "it says what it added")
        added.buttons[IdeasText.done].tap()
        XCTAssertTrue(app.ideaRow("A bell for the bike").waitForExistence(timeout: 10), "the idea in no folder is in To sort")
        XCTAssertEqual(app.folderRow("Garden").label, IdeasText.folderTitle("Garden", 2))
        XCTAssertEqual(app.folderRow("Kitchen").label, IdeasText.folderTitle("Kitchen", 1))

        app.more.tap()
        app.buttons[IdeasText.importFromStethoscore].firstMatch.tap()
        let nothing = app.alerts[IdeasText.nothingNewTitle]
        XCTAssertTrue(nothing.waitForExistence(timeout: 10), "a second import adds nothing")
        nothing.buttons[IdeasText.done].tap()
        XCTAssertEqual(app.ideaRows.count, 1)
        XCTAssertEqual(app.folderRows.count, 2)
    }

    /// A file that isn't a backup, and a newer Stethoscore's backup, are
    /// each refused with their own words, and nothing changes.
    func testTheImportRefusesWithItsOwnWords() throws {
        let cases: [(String, String)] = [
            (MadeUpExport.notABackup, IdeasText.refusedNotBackup),
            (try MadeUpExport.base64(version: 2), IdeasText.refusedNewer),
        ]
        for (file, words) in cases {
            let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", file])
            app.more.tap()
            app.buttons[IdeasText.importFromStethoscore].firstMatch.tap()
            let refused = app.alerts[IdeasText.refusedTitle]
            XCTAssertTrue(refused.waitForExistence(timeout: 10), "the file is refused")
            XCTAssertTrue(refused.staticTexts[words].exists, "with its own words: \(words)")
            refused.buttons[IdeasText.done].tap()
            XCTAssertTrue(app.empty.waitForExistence(timeout: 10), "nothing was added")
            app.terminate()
        }
    }

    // MARK: targets (A7)

    /// Every control on the Sort screen, in the chooser and in the editor is
    /// at least 44 by 44 points, and no two of them overlap.
    func testTargetsAreLargeEnoughAndApart() throws {
        let app = launchIdeas(IdeasStoreName())
        app.makeFolder("Garden")
        app.captureIdea("Plant bulbs before the frost comes")
        app.captureIdea("Oil the gate")
        XCTAssertTrue(app.ideaRow("Oil the gate").waitForExistence(timeout: 10))

        var targets: [(String, CGRect)] = [
            ("the capture well", app.descendants(matching: .any)["capture"].frame),
            ("More", app.more.frame),
        ]
        for i in 0..<app.ideaRows.count { targets.append(("idea row \(i)", app.ideaRows.element(boundBy: i).frame)) }
        let choose = app.buttons.matching(identifier: "choose-folder")
        for i in 0..<choose.count { targets.append(("Choose folder \(i)", choose.element(boundBy: i).frame)) }
        for i in 0..<app.folderRows.count { targets.append(("folder row \(i)", app.folderRows.element(boundBy: i).frame)) }
        assertTargets(targets, "the Sort screen")
        // Sort it sits inside the well: its own size, and the field never
        // reaches it
        assertTargets([("Sort it", app.sortIt.frame)], "the well")
        XCTAssertLessThanOrEqual(app.capture.frame.maxX, app.sortIt.frame.minX, "the field stops before Sort it")

        app.openChooser(for: "Oil the gate")
        assertTargets([("To sort", app.chooserToSort.frame),
                       ("Garden", app.chooserFolder("Garden").frame),
                       ("New folder", app.buttons["chooser-new-folder"].frame),
                       ("Cancel", app.buttons["chooser-cancel"].frame)], "the chooser")
        app.buttons["chooser-cancel"].tap()

        app.ideaRow("Oil the gate").tap()
        XCTAssertTrue(app.buttons["editor-done"].waitForExistence(timeout: 10))
        var editor: [(String, CGRect)] = [
            ("Done", app.buttons["editor-done"].frame),
            ("More", app.buttons["editor-more"].frame),
            ("the folder chip", app.buttons["editor-folder"].frame),
            ("Link to an idea", app.buttons["editor-link"].frame),
        ]
        let segments = app.descendants(matching: .any)["noteMarkdownToggle"].buttons
        for i in 0..<segments.count { editor.append(("segment \(i)", segments.element(boundBy: i).frame)) }
        XCTAssertEqual(segments.count, 2, "Write and Read")
        assertTargets(editor, "the editor")
    }

    private func assertTargets(_ targets: [(String, CGRect)], _ place: String,
                               file: StaticString = #filePath, line: UInt = #line) {
        for (name, frame) in targets {
            XCTAssertGreaterThanOrEqual(frame.width, 44, "\(place): \(name) is \(frame.size)", file: file, line: line)
            XCTAssertGreaterThanOrEqual(frame.height, 44, "\(place): \(name) is \(frame.size)", file: file, line: line)
        }
        for i in targets.indices {
            for j in targets.indices where j > i {
                let overlap = targets[i].1.intersection(targets[j].1)
                XCTAssertTrue(overlap.isNull || overlap.width < 0.5 || overlap.height < 0.5,
                              "\(place): \(targets[i].0) and \(targets[j].0) overlap by \(overlap.size)",
                              file: file, line: line)
            }
        }
    }
}
