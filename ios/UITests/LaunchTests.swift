import XCTest

/// Sorta opens on the page colour from edge to edge: bone in light and
/// aubergine in dark, as the Page colour set states them. Each test keeps its
/// screenshot, so a run shows what was on screen.
final class LaunchTests: XCTestCase {
    private static let bone = [0xF3, 0xEA, 0xDB]
    private static let aubergine = [0x28, 0x1E, 0x2A]
    /// How far a channel may move when the screen's colours are converted
    /// back to sRGB.
    private static let tolerance = 2
    /// Where the page is read, as fractions of the screen's width and height:
    /// the middle, and near each corner, clear of the status bar and the home
    /// indicator.
    private static let points: [(Double, Double)] = [(0.5, 0.5), (0.1, 0.1), (0.9, 0.1), (0.1, 0.9), (0.9, 0.9)]

    override func tearDown() {
        XCUIDevice.shared.appearance = .light
        super.tearDown()
    }

    func testLightPageIsBone() throws {
        try checkPage(in: .light, named: "light", is: Self.bone)
    }

    func testDarkPageIsAubergine() throws {
        try checkPage(in: .dark, named: "dark", is: Self.aubergine)
    }

    private func checkPage(in appearance: XCUIDevice.Appearance, named name: String, is expected: [Int]) throws {
        XCUIDevice.shared.appearance = appearance
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "Sorta is open")

        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = "\(name) \(UIDevice.current.model)"
        attachment.lifetime = .keepAlways
        add(attachment)

        let page = try SRGBPixels(shot.image)
        for (fx, fy) in Self.points {
            let seen = page.rgb(atX: Int(Double(page.width) * fx), y: Int(Double(page.height) * fy))
            let off = zip(seen, expected).map { abs($0 - $1) }.max() ?? 0
            XCTAssertLessThanOrEqual(
                off, Self.tolerance,
                "\(name): at \(fx), \(fy) the page is \(hex(seen)), not \(hex(expected))")
        }
    }

    private func hex(_ rgb: [Int]) -> String {
        "#" + rgb.map { String(format: "%02X", $0) }.joined()
    }
}

/// A picture redrawn into an 8-bit sRGB bitmap, so a colour reads the way an
/// asset catalog states it, on a Display P3 screen too.
private struct SRGBPixels {
    let width: Int
    let height: Int
    private let bytes: [UInt8]

    init(_ image: UIImage) throws {
        let picture = try XCTUnwrap(image.cgImage, "the screenshot has a bitmap")
        let srgb = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let width = picture.width, height = picture.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: srgb,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(picture, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        XCTAssertTrue(drawn, "the screenshot redraws in sRGB")
        self.width = width
        self.height = height
        self.bytes = bytes
    }

    /// Red, green and blue at a pixel, counted from the top left.
    func rgb(atX x: Int, y: Int) -> [Int] {
        let at = (y * width + x) * 4
        return [Int(bytes[at]), Int(bytes[at + 1]), Int(bytes[at + 2])]
    }
}
