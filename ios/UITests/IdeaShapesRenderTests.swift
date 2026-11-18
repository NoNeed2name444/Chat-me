import XCTest

/// S06c's renders (A7), by day and by night, on the iPhone and the iPad:
/// the ideas like the open one (the editor's strip on a phone; beside the
/// map on two panes, edged on the board and rung on the 3D map), the split
/// chooser, the collide card and a join's result; and, on the 3D map, the
/// ring round a drop target. Android's IdeaShapesRenderTest and
/// IdeaShapesMapTest keep the same.
final class IdeaShapesRenderTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        Retaps.reset()
    }

    func testShapesRender() throws {
        let app = try openBarrel(["-graphFreeze"])
        let window = app.windows.firstMatch.frame
        let paned = window.width >= 700
        _ = app.echoStrip()
        XCTAssertEqual(app.echoChips.count, 3, "the three ideas like it")
        assertInside(app.echoChip(ShapesExport.tomatoes), window, "an idea like it")
        if paned {
            // beside the map: the board edges them, the 3D map rings them
            app.chooseLook("look-2d")
            XCTAssertTrue(app.boardCard(ShapesExport.tomatoes).waitForExistence(timeout: 10), "the board")
            sleep(1)
            keepShot("shapes-1-echoes-board")
            app.chooseLook("look-space")
            XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60),
                          "the 3D map")
            sleep(4)
            keepShot("shapes-2-echoes-space")
            app.chooseLook("look-2d")
        } else {
            sleep(1)
            keepShot("shapes-1-echoes-strip")
        }

        app.editorMore(ShapesText.split)
        let chooser = app.descendants(matching: .any)["split-chooser"].firstMatch
        XCTAssertTrue(chooser.waitForExistence(timeout: 10), "the split chooser")
        let sentence = app.buttons.matching(identifier: "split-sentence")
            .matching(NSPredicate(format: "label == %@", "Catch the rain from the gutter.")).firstMatch
        XCTAssertTrue(sentence.waitForExistence(timeout: 5), "a sentence to choose")
        sentence.tap()
        assertInside(sentence, window, "a sentence")
        assertInside(app.buttons["split-confirm"].firstMatch, window, "Split idea")
        _ = chooser.settledFrame
        keepShot("shapes-3-split-chooser")
        app.buttons["shape-cancel"].firstMatch.tap()
        XCTAssertTrue(chooser.waitForNonExistence(timeout: 10), "Cancel splits nothing")

        app.editorMore(ShapesText.collide)
        app.pick(ShapesExport.train)
        let card = app.descendants(matching: .any)["collide-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the collide card")
        assertInside(app.buttons["collide-save"].firstMatch, window, "Save idea")
        _ = card.settledFrame
        keepShot("shapes-4-collide-card")
        app.buttons["shape-cancel"].firstMatch.tap()
        XCTAssertTrue(card.waitForNonExistence(timeout: 10), "Cancel leaves nothing")

        app.editorMore(ShapesText.join)
        app.pick(ShapesExport.gutter)
        XCTAssertTrue(app.notice(saying: ShapesText.joinDone).waitForExistence(timeout: 10), "the join, with Undo")
        app.waitForBody(ShapesExport.barrelBody + "\n\n" + ShapesExport.gutter + "\n\n" + ShapesExport.gutterBody)
        sleep(1)
        keepShot("shapes-5-join-result")
    }

    /// The 3D map is always night, so this runs once a device.
    func testDropTargetRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try ShapesExport.base64(), "-graphFreeze",
                                                 "-SortaDropPreview", ShapesExport.tomatoes])
        importExport(app)
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        if app.windows.firstMatch.frame.width < 700 {
            app.switchMode(to: "mode-map")
        }
        app.chooseLook("look-space")
        XCTAssertTrue(app.descendants(matching: .any)["graph3D"].firstMatch.waitForExistence(timeout: 60),
                      "the 3D map")
        sleep(4)
        keepShot("shapes-6-drop-target")
    }
}
