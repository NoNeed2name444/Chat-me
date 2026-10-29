import XCTest

/// The maps rendered for real, on the design preview's sample ideas
/// (GraphPreview): Space, Neurons, Performance and the 2D board, a
/// screenshot of each, map-space, map-neurons, map-performance and
/// map-board. The 3D maps are always night; the board follows the phone.
///
/// Light or dark is set on the simulator from outside, as for LaunchTests
/// (tools/run_ui_tests.sh), so this test does not set an appearance.
final class MapRenderTests: XCTestCase {
    func testEachThemeRenders() throws {
        for theme in ["space", "neurons", "performance"] {
            let app = XCUIApplication()
            app.keepTraceOffConsole()
            app.launchArguments += ["-graphPreview", "-graphPreviewTheme", theme]
            app.launch()
            XCTAssertTrue(app.otherElements["graph3D"].waitForExistence(timeout: 60), "the \(theme) map is on screen")
            // the bodies settle into place and the first orbits run
            sleep(4)
            keep("map-\(theme)")
            app.terminate()
        }

        let app = XCUIApplication()
        app.keepTraceOffConsole()
        app.launchArguments += ["-graphPreview", "-graphPreviewBoard"]
        app.launch()
        XCTAssertTrue(app.buttons["Look"].waitForExistence(timeout: 30), "the board is on screen, with its Look tool")
        sleep(2)
        keep("map-board")
    }

    private func keep(_ name: String) {
        let shot = uprightShot()
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
