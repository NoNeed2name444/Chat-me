import XCTest
@testable import Sorta

/// S07b A3: the hostile fixtures, each stopped within its limit plus 50 ms
/// with its code and nothing applied, while the main thread's heartbeat
/// keeps within its baseline plus 50 ms; and the engine's threads.
@MainActor
final class AddonHostileTests: XCTestCase {
    private func run(_ names: [String]) throws {
        let suite = try AddonConformance(dir: try AddonTestFiles.fixtures("hostile"))
        let baseline = AddonHeartbeat.baseline()
        for name in names {
            let c = try XCTUnwrap(suite.cases.first { $0["name"]?.string == name }, name)
            let heart = AddonHeartbeat()
            heart.start()
            let (text, run) = suite.blockRun(c) { snap in AddonNoteStoreCases(try! AddonTestFiles.store(self), from: snap) }
            heart.stop()
            let r = try XCTUnwrap(run, "\(name) finished")
            let limit = Double(c["limitMs"]?.integer ?? 0)
            let kind = c["kind"]?.string ?? "command"
            let code = kind == "load" ? (r.loadCode ?? "ok") : (r.outcome.code ?? "ok")
            let ms = kind == "load" ? r.loadMs : r.callMs
            XCTAssertEqual(code, c["expect"]?.string, name)
            XCTAssertLessThanOrEqual(ms, limit + 50, "\(name) stopped within its limit plus 50 ms")
            XCTAssertTrue(text.contains("applied none") || kind == "load", "\(name) applied nothing")
            XCTAssertLessThanOrEqual(heart.largestGapMs, baseline + 50,
                                     "\(name): main-thread gap \(heart.largestGapMs) ms, baseline \(baseline) ms")
            print("hostile \(name) \(code) \(String(format: "%.1f", ms)) ms, beat \(String(format: "%.1f", heart.largestGapMs)) ms (baseline \(String(format: "%.1f", baseline)) ms)")
        }
    }

    func testEveryFixtureIsInAGroup() throws {
        let suite = try AddonConformance(dir: try AddonTestFiles.fixtures("hostile"))
        let named = Set(AddonHostileTests.groups.joined())
        XCTAssertEqual(Set(suite.cases.compactMap { $0["name"]?.string }), named)
    }

    static let groups = [["loop", "memory", "recursion", "json", "huge"],
                         ["flood", "eval", "writes", "captures"],
                         ["revoke", "regex", "card", "toplevel"]]

    func testLoopMemoryRecursionNestingHuge() throws { try run(AddonHostileTests.groups[0]) }
    func testFloodEvalWritesCaptures() throws { try run(AddonHostileTests.groups[1]) }
    func testRevokeRegexCardTopLevel() throws { try run(AddonHostileTests.groups[2]) }

    // MARK: threads

    func testEachRuntimeHasItsOwnFourMegabyteThread() throws {
        let runner = AddonRunner(id: "stack", files: AddonFiles(names: ["main.js"], data: [Data("".utf8)], main: "main.js"))
        defer { runner.close() }
        let (size, main) = runner.onThread { (AddonEngineInfo.currentStackSize, AddonEngineInfo.isMainThread) }
        // pthread_get_stacksize_np inside the add-on's thread: the 4 MB asked for, and the pages the system
        // rounds it up by (4,206,592 bytes on the iOS 26 simulator), so never less
        XCTAssertGreaterThanOrEqual(size, 4 * 1024 * 1024)
        XCTAssertLessThan(size, 5 * 1024 * 1024)
        XCTAssertFalse(main)
        XCTAssertEqual(AddonThread.stackSize, 4 * 1024 * 1024)
        XCTAssertEqual(AddonEngineInfo.version, "0.18.0")
    }

    func testTheMainThreadNeverRunsTheEngine() {
        XCTAssertTrue(Thread.isMainThread)
        XCTAssertEqual(AddonEngineInfo.tryRuntimeHere(), "thread")
    }

    func testARuntimeIsUsedOnlyFromItsOwnThread() {
        let answer = AddonEngineInfo.callFromAnotherThread()
        XCTAssertTrue(answer.contains("\"code\":\"thread\""), answer)
    }
}
