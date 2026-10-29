import UIKit
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
            // the scene is built: wait until it is drawn too. A fixed 4 s
            // caught the iPad's first map, Space, before SceneKit's first
            // frame, with nothing drawn but the tools (Chat-me copy c8186ce)
            let start = Date()
            var lit = Self.lit()
            while lit < Self.drawn && Date().timeIntervalSince(start) < 60 {
                sleep(1)
                lit = Self.lit()
            }
            XCTAssertGreaterThanOrEqual(lit, Self.drawn, "the \(theme) map is drawn, not just its background")
            // the labels and the last bodies settle
            sleep(2)
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

    /// The share of the map's middle (past the top bar, short of the tools)
    /// that is lit, every fourth pixel: no channel of the night's background
    /// passes 40. On copy c8186ce's renders, Performance, the sparest, lit
    /// 1.1% to 1.4% of it; the blank iPad Space, none.
    private static let drawn: Double = 0.003

    private static func lit() -> Double {
        guard let picture = XCUIScreen.main.screenshot().image.cgImage,
              let srgb = CGColorSpace(name: CGColorSpace.sRGB) else { return 0 }
        let width = picture.width, height = picture.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let redrawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: srgb,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(picture, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard redrawn else { return 0 }
        var seen = 0, bright = 0
        for y in stride(from: height * 15 / 100, to: height * 85 / 100, by: 4) {
            for x in stride(from: width * 8 / 100, to: width * 80 / 100, by: 4) {
                let at = (y * width + x) * 4
                seen += 1
                if max(bytes[at], bytes[at + 1], bytes[at + 2]) > 40 { bright += 1 }
            }
        }
        return seen > 0 ? Double(bright) / Double(seen) : 0
    }

    private func keep(_ name: String) {
        let shot = uprightShot()
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
