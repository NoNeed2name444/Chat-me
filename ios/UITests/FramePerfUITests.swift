import UIKit
import XCTest

/// Sorta's frames on the simulator (perf, 15 Nov: the owner's 120 frames a
/// second on a 120 Hz screen), as Android's FramePerfTest: the map lively,
/// dragged and flown in, the board dragged and pinched, the deck's card
/// carried and an idea typed in. Each phase is timed by the app's frame meter
/// (`-SortaFrameMeter`, FrameMeter, debug builds only): the test posts the
/// Darwin notification `com.cramdown.sorta.frames.begin`, touches, posts
/// `….end`, then reads the meter's lines off its mark and prints them as
/// "SortaFrames <device>-<phase> <measure> frames=… p50=… p95=… p99=…
/// over8.3=…% over16.7=…%", and keeps them as an attachment. The simulator
/// draws on the Mac's GPU at the Mac's pace, so its numbers are read against
/// each other, before and after a change, and by the work a frame takes,
/// never as a phone's. Every phase must time frames, or fails.
///
/// The guards hold each phase to about 4 ms of work a frame, the share of an
/// 8.3 ms refresh the app keeps for itself: the main thread's median pass
/// under `budget`, and with the map the median render-thread step under
/// `budget` times `ciScale`. CI's simulator runs a phone's work four times
/// slower or more (its render thread drives the Mac's GPU through a virtual
/// machine: a render step's median was 6.6 to 9.2 ms there before these
/// fixes, run 43726420703), so a guard fails only on a real regression, never
/// on the runner's pace. Where the app moves on its own (`alone`: the test
/// only waits), the main thread's 95th percentile pass is held under
/// `budget` times `ciScale` too. Where the test drives the app (a drag, a
/// pinch, typing), XCUITest resolves its elements on the app's main thread
/// mid-phase, in passes of 15 to 250 ms that are XCUITest's, not the app's:
/// a few per phase, whose share of the passes swings with the runner's pace
/// (the same editor took 0.3 ms at the 95th percentile in run 43726420703
/// and 55.9 ms in run 43728974615), so there the 95th percentile is logged,
/// never held to a bound.
final class FramePerfUITests: XCTestCase {
    static let budget: Double = 4
    static let ciScale: Double = 4

    override func setUp() {
        continueAfterFailure = false
        Retaps.reset()
    }

    func testMapFrames() {
        let app = launchPreview([])
        let space = app.otherElements["graph3D"]
        XCTAssertTrue(space.waitForExistence(timeout: 30), "the 3D map didn't open")
        sleep(3)
        phase(app, "map-lively", render: true, alone: true) { sleep(3) }
        phase(app, "map-drag", render: true) {
            let from = space.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.35))
            let to = space.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.45))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.1)
            to.press(forDuration: 0.05, thenDragTo: from, withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        app.terminate()
    }

    /// The design preview flies in to Inguinal's system 1.5 s after the map
    /// opens (GraphPreview): the phase takes the flight whole.
    func testFlyInFrames() {
        let app = launchPreview(["-graphPreviewFly", "Inguinal"])
        XCTAssertTrue(app.otherElements["graph3D"].waitForExistence(timeout: 30), "the 3D map didn't open")
        phase(app, "map-fly", render: true, alone: true) { sleep(4) }
        app.terminate()
    }

    func testBoardFrames() {
        let app = launchPreview(["-graphPreviewBoard"])
        XCTAssertTrue(app.buttons["Look"].waitForExistence(timeout: 30), "the board is on screen, with its Look tool")
        sleep(2)
        let window = app.windows.firstMatch
        phase(app, "board-drag", render: false) {
            let from = window.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.5))
            let to = window.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.6))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.1)
            to.press(forDuration: 0.05, thenDragTo: from, withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        phase(app, "board-pinch", render: false) {
            window.pinch(withScale: 1.6, velocity: 1)
            window.pinch(withScale: 0.6, velocity: -1)
        }
        app.terminate()
    }

    /// The top card carried a little way and let go still, three times: it
    /// springs back each time.
    func testDeckFrames() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaSeed", try DeckSeed.base64(), "-SortaNow", DeckSeed.now,
                                                 "-SortaLastTide", DeckSeed.now, "-SortaDeck", "on", "-SortaFrameMeter"])
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let card = app.descendants(matching: .any)["deck-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the deck")
        sleep(1)
        phase(app, "deck-drag", render: false) {
            for _ in 0..<3 {
                let from = card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
                let to = from.withOffset(CGVector(dx: 40, dy: 30))
                from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.4)
                sleep(1)
            }
        }
        app.terminate()
    }

    func testEditorTyping() {
        let app = launchIdeas(IdeasStoreName(), ["-SortaFrameMeter"])
        app.captureIdea("Paint the shed")
        app.openEditor(from: app.ideaRow("Paint the shed"))
        let body = app.textViews["editor-body"]
        XCTAssertTrue(body.waitForExistence(timeout: 10), "the editor's body")
        body.tap()
        app.clearSlideToTypeIntro(refocus: body)
        sleep(1)
        phase(app, "editor-typing", render: false, close: { app.buttons["editor-done"].tap() }) {
            body.typeText("Green, and the light over the gate before the frost")
        }
        app.terminate()
    }

    // MARK: the phases

    private var phases = 0

    private func launchPreview(_ more: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.keepTraceOffConsole()
        app.launchArguments += ["-graphPreview", "-SortaFrameMeter"] + more
        app.launch()
        return app
    }

    /// Times `during` with the app's frame meter, then reads its lines; with
    /// `render`, the map's render thread must have timed frames too. `close`
    /// puts away what covers the meter's mark (a sheet) before it is read.
    /// `alone`: the test only waits while the app moves on its own.
    private func phase(_ app: XCUIApplication, _ name: String, render: Bool, alone: Bool = false, close: (() -> Void)? = nil,
                       file: StaticString = #filePath, line: UInt = #line, during: () -> Void) {
        phases += 1
        post("com.cramdown.sorta.frames.begin")
        // the notification reaches the app's main thread in its next pass
        usleep(300_000)
        during()
        post("com.cramdown.sorta.frames.end")
        usleep(300_000)
        close?()
        let mark = app.descendants(matching: .any)["frame-meter"].firstMatch
        let wanted = "phase=\(phases) "
        let read = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value BEGINSWITH %@", wanted), object: mark)
        XCTAssertEqual(XCTWaiter.wait(for: [read], timeout: 10), .completed,
                       "the frame meter summed up \(name): \(String(describing: mark.value))", file: file, line: line)
        let report: String = (mark.value as? String) ?? ""
        let device: String = UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "iphone"
        let lines: [String] = report.split(separator: "\n").map { "SortaFrames \(device)-\(name) \($0)" }
        for out in lines { print(out) }
        let kept = XCTAttachment(string: lines.joined(separator: "\n"))
        kept.name = "frames-\(device)-\(name)"
        kept.lifetime = .keepAlways
        add(kept)
        XCTAssertTrue(lines.contains { $0.contains("main-interval frames=") && !$0.contains("frames=0 ") },
                      "\(name): the main thread timed no frame: \(report)", file: file, line: line)
        if render {
            XCTAssertTrue(lines.contains { $0.contains("render-work frames=") && !$0.contains("frames=0 ") },
                          "\(name): the map's render thread timed no frame: \(report)", file: file, line: line)
        }
        let scaled: Double = Self.budget * Self.ciScale
        if let p50 = Self.percentile("p50", of: "main-work", in: lines) {
            XCTAssertLessThanOrEqual(p50, Self.budget, "\(name): the main thread's median pass took \(p50) ms", file: file, line: line)
        }
        if alone, let p95 = Self.percentile("p95", of: "main-work", in: lines) {
            XCTAssertLessThanOrEqual(p95, scaled, "\(name): the main thread's 95th percentile pass took \(p95) ms", file: file, line: line)
        }
        if render, let p50 = Self.percentile("p50", of: "render-work", in: lines) {
            XCTAssertLessThanOrEqual(p50, scaled, "\(name): the map's median render step took \(p50) ms", file: file, line: line)
        }
    }

    /// `key` (p50, p95, p99) of `measure` in the meter's lines; nil when the
    /// measure timed nothing.
    static func percentile(_ key: String, of measure: String, in lines: [String]) -> Double? {
        guard let found = lines.first(where: { $0.contains(" \(measure) frames=") }), !found.contains(" frames=0 ") else { return nil }
        for word in found.split(separator: " ") where word.hasPrefix(key + "=") {
            return Double(word.dropFirst(key.count + 1))
        }
        return nil
    }

    private func post(_ name: String) {
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(name as CFString),
                                             nil, nil, true)
    }
}
