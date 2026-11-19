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
///
/// Guards that hold whatever the runner's pace (the review, 19 Nov): the
/// meter's "counts" line (FrameCount) says how many times parts of a page
/// were built in a phase. A slower runner builds them fewer times, never
/// more. Carrying the deck's card builds DeckCardMove on every touch and the
/// card's face at most `deckCardCeiling` times; panning or pinching the
/// board builds its canvas on every touch and its links' lanes at most
/// `boardLanesCeiling` times (BoardLanesMemo). Each has a control, launched
/// with `-SortaFrameUndo` (FrameUndo), where the app undoes its fix and the
/// guard must catch it.
final class FramePerfUITests: XCTestCase {
    static let budget: Double = 4
    static let ciScale: Double = 4
    /// The card's face, built again in three carries of the card: a few
    /// times at most (it was every touch before DeckCardMove).
    static let deckCardCeiling: Int = 6
    /// The board's lanes, worked out again in a pan or a pinch: once at
    /// most, as the links do not change (every pass before BoardLanesMemo).
    static let boardLanesCeiling: Int = 1
    /// The fewest builds that show the touches moved the page at all, so a
    /// count under its ceiling means something.
    static let movedAtLeast: Int = 10

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
        for counts in boardPhases(undo: false) {
            XCTAssertGreaterThanOrEqual(counts.passes, Self.movedAtLeast, "\(counts.name): the board was built only \(counts.passes) times")
            XCTAssertLessThanOrEqual(counts.lanes, Self.boardLanesCeiling,
                                     "\(counts.name): the links' lanes were worked out \(counts.lanes) times in \(counts.passes) passes")
        }
    }

    /// The control for testBoardFrames' guard: the board works its lanes out
    /// on every pass (FrameUndo), as before BoardLanesMemo, and must break it.
    func testBoardFramesUndoneIsCaught() {
        for counts in boardPhases(undo: true) {
            print("SortaFrames control \(counts.name) board-lanes=\(counts.lanes) board-pass=\(counts.passes) ceiling=\(Self.boardLanesCeiling)")
            XCTAssertGreaterThanOrEqual(counts.passes, Self.movedAtLeast, "\(counts.name): the board was built only \(counts.passes) times")
            XCTAssertGreaterThan(counts.lanes, Self.boardLanesCeiling,
                                 "\(counts.name): the guard missed lanes worked out on every pass (\(counts.lanes) in \(counts.passes))")
        }
    }

    /// The board dragged, then pinched: each phase's name, its canvas's
    /// builds and its lanes' workings-out.
    private func boardPhases(undo: Bool) -> [(name: String, passes: Int, lanes: Int)] {
        let app = launchPreview(["-graphPreviewBoard"] + (undo ? ["-SortaFrameUndo"] : []))
        XCTAssertTrue(app.buttons["Look"].waitForExistence(timeout: 30), "the board is on screen, with its Look tool")
        sleep(2)
        let window = app.windows.firstMatch
        let suffix: String = undo ? "-undone" : ""
        var out: [(name: String, passes: Int, lanes: Int)] = []
        let drag: [String] = phase(app, "board-drag" + suffix, render: false, timed: !undo) {
            let from = window.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.5))
            let to = window.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.6))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.1)
            to.press(forDuration: 0.05, thenDragTo: from, withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        out.append(("board-drag" + suffix, Self.count("board-pass", in: drag), Self.count("board-lanes", in: drag)))
        let pinch: [String] = phase(app, "board-pinch" + suffix, render: false, timed: !undo) {
            window.pinch(withScale: 1.6, velocity: 1)
            window.pinch(withScale: 0.6, velocity: -1)
        }
        out.append(("board-pinch" + suffix, Self.count("board-pass", in: pinch), Self.count("board-lanes", in: pinch)))
        app.terminate()
        return out
    }

    /// The top card carried a little way and let go still, three times: it
    /// springs back each time.
    func testDeckFrames() throws {
        let (moves, cards) = try deckPhase(undo: false)
        XCTAssertGreaterThanOrEqual(moves, Self.movedAtLeast, "deck-drag: the card was moved only \(moves) times")
        XCTAssertLessThanOrEqual(cards, Self.deckCardCeiling, "deck-drag: the card's face was built \(cards) times in \(moves) moves")
    }

    /// The control for testDeckFrames' guard: the card reads the drag itself
    /// (FrameUndo), as before DeckCardMove, and must break it.
    func testDeckFramesUndoneIsCaught() throws {
        let (moves, cards) = try deckPhase(undo: true)
        print("SortaFrames control deck-drag-undone deck-card=\(cards) deck-move=\(moves) ceiling=\(Self.deckCardCeiling)")
        XCTAssertGreaterThanOrEqual(moves, Self.movedAtLeast, "deck-drag-undone: the card was moved only \(moves) times")
        XCTAssertGreaterThan(cards, Self.deckCardCeiling, "deck-drag-undone: the guard missed a face built on every move (\(cards) in \(moves))")
    }

    /// Three carries of the deck's top card: DeckCardMove's builds and the
    /// card's own.
    private func deckPhase(undo: Bool) throws -> (moves: Int, cards: Int) {
        let app = launchIdeas(IdeasStoreName(), ["-SortaSeed", try DeckSeed.base64(), "-SortaNow", DeckSeed.now,
                                                 "-SortaLastTide", DeckSeed.now, "-SortaDeck", "on", "-SortaFrameMeter"]
                              + (undo ? ["-SortaFrameUndo"] : []))
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let card = app.descendants(matching: .any)["deck-card"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the deck")
        sleep(1)
        let lines: [String] = phase(app, undo ? "deck-drag-undone" : "deck-drag", render: false, timed: !undo) {
            for _ in 0..<3 {
                let from = card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
                let to = from.withOffset(CGVector(dx: 40, dy: 30))
                from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.4)
                sleep(1)
            }
        }
        app.terminate()
        return (Self.count("deck-move", in: lines), Self.count("deck-card", in: lines))
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
    /// `timed` false (a control, whose app undoes a fix): the lines are
    /// read and printed, and the time guards left to the real phases. The
    /// meter's lines, as printed.
    @discardableResult
    private func phase(_ app: XCUIApplication, _ name: String, render: Bool, alone: Bool = false, timed: Bool = true,
                       close: (() -> Void)? = nil,
                       file: StaticString = #filePath, line: UInt = #line, during: () -> Void) -> [String] {
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
        guard timed else { return lines }
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
        return lines
    }

    /// How many times `name` was built, from the meter's "counts" line; 0
    /// when it was not.
    static func count(_ name: String, in lines: [String]) -> Int {
        guard let found = lines.first(where: { $0.contains(" counts ") }) else { return 0 }
        for word in found.split(separator: " ") where word.hasPrefix(name + "=") {
            return Int(word.dropFirst(name.count + 1)) ?? 0
        }
        return 0
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
