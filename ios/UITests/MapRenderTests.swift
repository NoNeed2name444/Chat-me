import XCTest

/// The maps rendered for real, on the design preview's sample ideas
/// (GraphPreview): Space, Neurons, Performance and the 2D board, a
/// screenshot of each, map-space, map-neurons, map-performance and
/// map-board. The 3D maps are always night; the board follows the phone.
///
/// Light or dark is set on the simulator from outside, as for LaunchTests
/// (tools/run_ui_tests.sh), so this test does not set an appearance. The 3D
/// maps are shot still (-graphFreeze), so each device shows the same moment.
final class MapRenderTests: XCTestCase {
    func testEachThemeRenders() throws {
        for theme in ["space", "neurons", "performance"] {
            let app = XCUIApplication()
            app.keepTraceOffConsole()
            // -graphFreeze: the map as Reduce Motion shows it, every link
            // whole and every body at home at its size. GraphSim steps its
            // motion at most 0.05 s a frame, so on a slow simulator the links
            // were caught still growing in (run 40841930118, the iPad's
            // Neurons). Its start went by the map's liveliness before the
            // freeze, so every body stayed at nothing (the Chat-me copies
            // 3d0f11b and 196b905)
            app.launchArguments += ["-graphPreview", "-graphPreviewTheme", theme, "-graphFreeze"]
            app.launch()
            XCTAssertTrue(app.otherElements["graph3D"].waitForExistence(timeout: 60), "the \(theme) map is on screen")
            // the scene is built and drawn
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
