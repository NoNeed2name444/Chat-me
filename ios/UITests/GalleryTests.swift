import XCTest

/// The look kit rendered for real: Sorta launched with `-SortaGallery` shows
/// the states gallery (WardStatesGallery), every control at rest, held and
/// disabled. The test keeps a screenshot of each screenful, gallery-1,
/// gallery-2 and so on, scrolling until the gallery's last row is on screen.
///
/// Light or dark is set on the simulator from outside, as for LaunchTests
/// (tools/run_ui_tests.sh), so this test does not set an appearance.
final class GalleryTests: XCTestCase {
    /// More screenfuls than the gallery has on the smallest phone at the
    /// default text size; reaching it means the end was never found.
    private static let mostScreens = 12

    func testGalleryRenders() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-SortaGallery"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "Sorta is open")

        // the gallery's first row: the main button, once in each column
        let save = app.buttons.matching(NSPredicate(format: "label == %@", "Save"))
        XCTAssertTrue(save.firstMatch.waitForExistence(timeout: 30), "the gallery is on screen")
        XCTAssertEqual(save.count, 3, "the main button at rest, held and disabled")
        let enabled = save.matching(NSPredicate(format: "isEnabled == true"))
        XCTAssertEqual(enabled.count, 2, "the main button is on at rest and held")
        XCTAssertEqual(save.matching(NSPredicate(format: "isEnabled == false")).count, 1,
                       "the main button in the disabled column is off")
        XCTAssertTrue(enabled.firstMatch.isHittable, "the main button at rest can be tapped")
        let frame = enabled.firstMatch.frame
        XCTAssertGreaterThanOrEqual(frame.height, 44, "the main button is at least 44 points tall")

        // each chip keeps its word on one line beside its round button, or
        // the round button moves under it: a chip as tall as two lines of
        // text means the word was broken ("Pinne / d", ios run 40816579370)
        let chips = app.buttons.matching(NSPredicate(format: "label == %@", "Pinned"))
        let add = app.buttons.matching(NSPredicate(format: "label == %@", "Add"))
        XCTAssertEqual(chips.count, 6, "the chip at rest, held and disabled, chosen or not")
        XCTAssertEqual(add.count, 6, "the round button at rest, held and disabled, chosen or not")
        let circle = add.firstMatch.frame
        XCTAssertEqual(circle.width, circle.height, accuracy: 1, "the round button is round")
        for i in 0..<chips.count {
            let chip = chips.element(boundBy: i).frame
            XCTAssertLessThanOrEqual(chip.height, circle.height + 1, "chip \(i + 1) is one line tall")
        }

        let gallery = app.scrollViews["gallery"]
        XCTAssertTrue(gallery.exists, "the gallery scrolls")
        let lastTile = app.buttons.matching(identifier: "gallery.lastTile").firstMatch
        let window = app.windows.firstMatch.frame

        var reachedEnd = false
        for screen in 1...Self.mostScreens {
            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = "gallery-\(screen)"
            attachment.lifetime = .keepAlways
            add(attachment)
            if lastTile.exists, lastTile.isHittable, window.contains(lastTile.frame) {
                reachedEnd = true
                break
            }
            gallery.swipeUp(velocity: .slow)
        }
        XCTAssertTrue(reachedEnd, "the gallery's last row came on screen")
        XCTAssertTrue(lastTile.isHittable, "the chosen tile at rest can be tapped")
        XCTAssertGreaterThanOrEqual(lastTile.frame.height, 44, "the tile is at least 44 points tall")
    }
}
