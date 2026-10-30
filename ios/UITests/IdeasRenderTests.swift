import XCTest

/// The Ideas screens rendered for real (S05, A6 and A7), each kept as a
/// named screenshot: the empty state, the import's message, To sort with
/// ideas, the folder chooser, an open folder and the editor. CI runs it by
/// day and by night on the iPhone and the iPad (tools/run_ui_tests.sh, the
/// ideas set), setting the simulator's appearance from outside, as for
/// LaunchTests. The ideas are the made-up export's and a few typed ones;
/// none is medical.
final class IdeasRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testScreensRender() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try MadeUpExport.base64()])
        walk(app, prefix: "ideas")
    }

    /// The largest Dynamic Type size (AX5) on a phone: To sort and the
    /// editor, with every control inside the window and none under 44
    /// points. CI runs it on the iPhone only.
    func testLargestTextSize() throws {
        let app = launchIdeas(IdeasStoreName(), [
            "-SortaImportFile", try MadeUpExport.base64(),
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ])
        let window = app.windows.firstMatch.frame
        keepShot("ideas-ax5-1-empty")
        assertInside(app.descendants(matching: .any)["capture"], window, "the capture well")
        assertInside(app.sortIt, window, "Sort it")
        XCTAssertLessThan(app.capture.frame.maxY, app.sortIt.frame.minY + 1, "the well stacks: Sort it under the field")

        importExport(app)
        app.captureIdea("Oil the gate before winter, and the shed's hinges too")
        XCTAssertTrue(app.ideaRow("Oil the gate before winter, and the shed's hinges too").waitForExistence(timeout: 10))
        keepShot("ideas-ax5-2-to-sort")
        let choose = app.chooseFolder(for: "Oil the gate before winter, and the shed's hinges too")
        assertInside(choose, window, "Choose folder")
        let row = app.ideaRow("Oil the gate before winter, and the shed's hinges too")
        XCTAssertLessThanOrEqual(row.frame.maxY, choose.frame.minY + 1, "the row stacks: Choose folder under the words")

        row.tap()
        XCTAssertTrue(app.buttons["editor-done"].waitForExistence(timeout: 10), "the editor opened")
        sleep(1)
        keepShot("ideas-ax5-3-editor")
        for id in ["editor-done", "editor-more", "editor-folder", "editor-link"] {
            assertInside(app.buttons[id], window, id)
        }
    }

    // MARK: the walk

    private func walk(_ app: XCUIApplication, prefix: String) {
        sleep(1)
        keepShot("\(prefix)-1-empty")

        importExport(app, keep: "\(prefix)-2-import-message")
        app.captureIdea("Oil the gate")
        app.captureIdea("Ask about the allotment waiting list")
        XCTAssertTrue(app.ideaRow("Ask about the allotment waiting list").waitForExistence(timeout: 10))
        // the keyboard away: its Done ends editing
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        sleep(1)
        keepShot("\(prefix)-3-to-sort")

        app.openChooser(for: "Oil the gate")
        sleep(1)
        keepShot("\(prefix)-4-chooser")
        app.buttons["chooser-cancel"].tap()

        app.folderRow("Garden").tap()
        XCTAssertTrue(app.upRow.waitForExistence(timeout: 10), "Garden opened")
        sleep(1)
        keepShot("\(prefix)-5-folder")
        app.upRow.tap()

        app.ideaRow("A bell for the bike").tap()
        XCTAssertTrue(app.buttons["editor-done"].waitForExistence(timeout: 10), "the editor opened")
        sleep(1)
        keepShot("\(prefix)-6-editor")
        app.buttons["editor-done"].tap()
    }

    private func importExport(_ app: XCUIApplication, keep name: String? = nil) {
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

    private func assertInside(_ element: XCUIElement, _ window: CGRect, _ name: String,
                              file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.exists, "\(name) is on screen", file: file, line: line)
        let frame = element.frame
        XCTAssertGreaterThanOrEqual(frame.minX, window.minX - 0.5, "\(name) isn't cut at the left: \(frame)", file: file, line: line)
        XCTAssertLessThanOrEqual(frame.maxX, window.maxX + 0.5, "\(name) isn't cut at the right: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(frame.height, 44, "\(name) is at least 44 tall: \(frame)", file: file, line: line)
        XCTAssertGreaterThanOrEqual(frame.width, 44, "\(name) is at least 44 wide: \(frame)", file: file, line: line)
    }
}

private extension XCUIElement {
    func tapIfThere() {
        if exists && isHittable { tap() }
    }
}
