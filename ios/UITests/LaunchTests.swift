import XCTest

/// Sorta opens on the page colour from edge to edge: bone in light and
/// aubergine in dark, as the Page colour set states them. Each test keeps its
/// screenshot, so a run shows what was on screen.
///
/// Since S05 the first screen is the sorting desk, on a fresh store of the
/// test's own (`-SortaStore`): capture at the top and two lines under it,
/// with the page everywhere else. Only the two top points may move, and only
/// when the capture well (identifier `capture`) covers them: the test checks
/// that it does, then reads the page at the same height in the side margin,
/// half a gutter (8 points) in from the nearer edge (`movedClearOfCapture`).
/// The count, the tolerance, the middle point and the strips don't change
/// (S05, A9).
///
/// In CI run 40714297807, setting XCUIDevice's appearance left the simulator
/// light, so CI sets the simulator's appearance itself before each test
/// (tools/run_ui_tests.sh).
final class LaunchTests: XCTestCase {
    private static let bone = [0xF3, 0xEA, 0xDB]
    private static let aubergine = [0x28, 0x1E, 0x2A]
    /// How far a channel may move when the screen's colours are converted
    /// back to sRGB.
    private static let tolerance = 2
    /// Where the page is read, as fractions of the screen's width and height:
    /// the middle, and near each corner, clear of the status bar and the home
    /// indicator; and in the strips under those bars, where the page shows only
    /// if it ignores the safe area: at the top's middle, and at the bottom,
    /// either side of the home indicator and clear of the corner where the
    /// iPad draws its window's handle.
    private static let points: [(Double, Double)] = [
        (0.5, 0.5), (0.1, 0.1), (0.9, 0.1), (0.1, 0.9), (0.9, 0.9),
        (0.5, 0.005), (0.2, 0.995), (0.8, 0.995),
    ]
    /// The two points the capture well may cover, the only ones that may move.
    private static let topPoints: Set<Int> = [1, 2]
    /// Half the gutter (WardSpace.gutter, 16 points): where a moved point
    /// reads the page, in from the nearer side.
    private static let margin = 8.0

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
        // a fresh store of its own, so the first screen is the empty one
        app.launchArguments += ["-SortaStore", "launch-" + UUID().uuidString.prefix(12).lowercased()]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "Sorta is open")
        let capture = app.descendants(matching: .any).matching(identifier: "capture").firstMatch
        XCTAssertTrue(capture.waitForExistence(timeout: 30), "the Sort screen is open, with capture")
        let well: CGRect = capture.frame
        waitForNoBanner(over: app, name: name)

        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = "\(name) \(UIDevice.current.model)"
        attachment.lifetime = .keepAlways
        add(attachment)

        let page = try SRGBPixels(shot.image)
        let screen: CGSize = shot.image.size
        XCTAssertEqual(Self.points.count, 8, "eight points")
        for (k, point) in Self.points.enumerated() {
            let (fx, fy) = Self.topPoints.contains(k)
                ? Self.movedClearOfCapture(point, well: well, screen: screen, name: name)
                : point
            let seen = page.rgb(atX: Int(Double(page.width) * fx), y: Int(Double(page.height) * fy))
            let off = zip(seen, expected).map { abs($0 - $1) }.max() ?? 0
            XCTAssertLessThanOrEqual(
                off, Self.tolerance,
                "\(name): at \(fx), \(fy) the page is \(hex(seen)), not \(hex(expected))")
        }
    }

    /// A system notification banner, SpringBoard's: a short element at the
    /// top of the screen, named as a notification's short look or as the
    /// one that came on 12 Nov.
    private static var bannerQuery: NSPredicate {
        NSPredicate(format: "identifier BEGINSWITH 'NotificationShortLook' OR label CONTAINS[c] 'Apple Intelligence'")
    }

    /// The screenshot is the whole screen, SpringBoard's banners too: in
    /// Chat-me run 43239659022 a freshly booted simulator's "Ready for Apple
    /// Intelligence" banner covered the top points at y 0.1 of the light
    /// page as it was taken. So a banner over the app is waited out first,
    /// up to 15 s, as its own activity; one still there is swiped up, as a
    /// person would, with 5 s more for it to go. Nothing about the points
    /// changes: if a banner stays, they read it and fail.
    private func waitForNoBanner(over app: XCUIApplication, name: String) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let screen: CGRect = app.frame
        func banner() -> XCUIElement? {
            let found = springboard.descendants(matching: .any).matching(Self.bannerQuery).allElementsBoundByIndex
            return found.first { element in
                guard element.exists else { return false }
                let frame: CGRect = element.frame
                return !frame.isEmpty && frame.intersects(screen)
                    && frame.minY < screen.height * 0.25 && frame.height < screen.height * 0.3
            }
        }
        guard let first = banner() else { return }
        XCTContext.runActivity(named: "\(name): a system banner is over the app (\(first.label)); wait for it to go") { _ in
            func gone(within halves: Int) -> Bool {
                for _ in 0..<halves {
                    usleep(500_000)
                    if banner() == nil { return true }
                }
                return false
            }
            if gone(within: 30) { return }
            if let still = banner() {
                XCTContext.runActivity(named: "\(name): the banner stayed 15 s; swipe it up") { _ in
                    // it may go on its own between the look and the swipe
                    if still.exists { still.swipeUp() }
                }
            }
            if !gone(within: 10) {
                XCTContext.runActivity(named: "\(name): a banner is still over the app; the points read what is there") { _ in }
            }
        }
    }

    /// The rule for a top point: if the capture well's frame covers it, the
    /// test says so, and the point keeps its height and moves across to half
    /// the gutter in from the nearer side; otherwise it stays where it is.
    private static func movedClearOfCapture(_ point: (Double, Double), well: CGRect, screen: CGSize,
                                            name: String) -> (Double, Double) {
        let (fx, fy) = point
        let spot = CGPoint(x: fx * screen.width, y: fy * screen.height)
        guard well.contains(spot) else { return point }
        XCTAssertTrue(well.contains(spot), "\(name): the capture well covers \(fx), \(fy)")
        let x: Double = fx < 0.5 ? margin : Double(screen.width) - margin
        XCTAssertFalse(well.contains(CGPoint(x: x, y: spot.y)),
                       "\(name): the moved point \(x), \(spot.y) is clear of the capture well \(well)")
        XCTContext.runActivity(named: "\(name): \(fx), \(fy) is under the capture well \(well); read at x \(x) points") { _ in }
        return (x / Double(screen.width), fy)
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
