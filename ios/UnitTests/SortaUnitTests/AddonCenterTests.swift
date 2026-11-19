import XCTest
@testable import Sorta

/// S07b A3, A4, A5, A6: an installed add-on run through AddonCenter against
/// NoteStore: grants, a revoke that stops a running call, "ask each time",
/// the activity log, net off, strikes, safe start, the kill switch, the
/// pool and the minute budget. Net goes to the recording stand-in only.
@MainActor
final class AddonCenterTests: XCTestCase {
    static let addonId = "io.github.example.tests"

    static func package(permissions: [String] = ["ideas.read", "ideas.write", "commands", "net"],
                        hosts: [String] = ["api.example.com"], version: String = "1.0.0", says: String = "ok",
                        filler: Int = 0) -> Data {
        let manifest = AddonJSON.object([
            ("format", .string("sorta-addon")), ("formatVersion", .number(1)), ("id", .string(addonId)),
            ("name", .string("Tests")), ("version", .string(version)),
            ("author", .object([("name", .string("Example")), ("url", .string("https://github.com/example"))])),
            ("license", .string("MIT")), ("minSorta", .string("0.1.0")), ("api", .number(1)), ("main", .string("main.js")),
            ("permissions", AddonJSON.strings(permissions)), ("hosts", AddonJSON.strings(hosts)),
            ("contributes", .object([("commands", .array(["go", "spin", "fetch", "has", "store"].map {
                .object([("id", .string($0)), ("title", .string($0))])
            }))])),
        ])
        let main = """
        sorta.commands.on('go', async () => { await sorta.ideas.capture({ text: 'From an add-on' }); return { message: '\(says)' }; });
        sorta.commands.on('store', async (input) => {
          if (input.mode === 'read') return { message: String(await sorta.storage.get('k')) };
          await sorta.storage.set('k', 'from the call that would ' + input.mode);
          while (input.mode === 'spin') {}
          return { message: 'kept' };
        });
        sorta.commands.on('spin', async () => { while (true) {} });
        sorta.commands.on('fetch', async () => {
          const r = await sorta.net.fetch('https://api.example.com/v1/items?token=q-token-7731',
            { headers: { 'X-Api-Key': 'h-value-5512', Cookie: 'c=planted-7702' } });
          return { message: String(r.status) + ' ' + Object.keys(r.headers).sort().join(',') };
        });
        sorta.commands.on('has', async () => ({ message: String(sorta.has('net')) }));
        """
        // files the add-on never runs, to bring the package near its size limit
        let more = (0..<filler).map { n in
            AddonZipWriter.Entry(name: "lib/f\(n).js", data: Data(("// " + String(repeating: "filler words ", count: 5_000) + "\n").utf8))
        }
        return AddonZipWriter.pack([.init(name: "manifest.json", data: Data(manifest.canonical.utf8)),
                                    .init(name: "main.js", data: Data(main.utf8))] + more)
    }

    private struct Rig {
        let store: NoteStore
        let registry: AddonRegistry
        let center: AddonCenter
        let network: AddonRoutesNetwork
    }

    private func rig(netOn: Bool = true, grants: [String] = ["ideas.read", "ideas.write", "commands", "net:api.example.com"],
                     clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) throws -> Rig {
        let store = try AddonTestFiles.store(self)
        let folder = try AddonTestFiles.temporaryFolder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let registry = AddonRegistry(folder: folder, clock: clock)
        let bytes = AddonCenterTests.package()
        let review = try AddonReview.make(bytes, appVersion: "0.1.0").get()
        try registry.install(review, bytes: bytes, grants: grants)
        registry.flush()
        let routes = try AddonJSON.parse(data: Data(contentsOf: AddonTestFiles.fixtures("net/routes.json")))
        let network = AddonRoutesNetwork(routes: routes)
        let center = AddonCenter(store: store, registry: registry, network: network, netOn: netOn)
        addTeardownBlock { center.pool.closeAll() }
        return Rig(store: store, registry: registry, center: center, network: network)
    }

    private func run(_ r: Rig, _ command: String, mode: String = "", while doing: (() -> Void)? = nil) -> AddonCenter.Finished? {
        var out: AddonCenter.Finished?
        r.center.run(AddonCenterTests.addonId, kind: "command", command: command, input: "{\"ideas\":[],\"mode\":\"\(mode)\"}") { out = $0 }
        doing?()
        AddonHeartbeat.spin(until: { out != nil }, seconds: 12)
        return out
    }

    func testACommandCapturesThroughTheStoreAsOneUndoStep() throws {
        let r = try rig()
        let before = r.store.notes
        let f = try XCTUnwrap(run(r, "go"))
        XCTAssertTrue(f.outcome.ok, f.outcome.envelope)
        XCTAssertEqual(f.applied?.added.count, 1)
        XCTAssertTrue(f.prepared, "the batch was worked out on the add-on's thread, and the main thread only took it")
        XCTAssertEqual(r.center.applyTimes.count, 1)
        XCTAssertEqual(r.store.notes.count, before.count + 1)
        XCTAssertEqual(r.store.notes.last?.title, "From an add-on")
        r.store.undo(try XCTUnwrap(f.reshape))
        XCTAssertEqual(r.store.notes, before)
    }

    func testARevokeStopsARunningCallAndNothingIsApplied() throws {
        let r = try rig()
        let started = Date()
        let f = try XCTUnwrap(run(r, "spin") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                r.registry.revoke(AddonCenterTests.addonId, "commands")
            }
        })
        XCTAssertEqual(f.code, "stopped")
        XCTAssertNil(f.applied)
        XCTAssertLessThan(Date().timeIntervalSince(started), 0.45, "stopped before its 500 ms limit")
        XCTAssertFalse(r.registry.addon(AddonCenterTests.addonId)?.grants.contains("commands") ?? true)
    }

    // MARK: a stop, an update, storage (repair 1, M1)

    func testAStopWithNoCallRunningDoesNotStick() throws {
        let r = try rig()
        let id = AddonCenterTests.addonId
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
        XCTAssertEqual(r.center.pool.alive, 1)
        // a grant taken away while the add-on is idle: its runtime goes, and the next call is answered at once
        r.registry.revoke(id, "net:api.example.com")
        XCTAssertEqual(r.center.pool.alive, 0, "the idle runtime is closed")
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok, "not \"stopped\"")
        XCTAssertEqual(try XCTUnwrap(run(r, "fetch")).code, "permission", "and the grant is gone")
        // off and on again
        r.registry.setOn(id, false)
        XCTAssertEqual(r.center.pool.alive, 0)
        r.registry.setOn(id, true)
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
        // the kill switch, off and on
        r.registry.addonsOn = false
        r.registry.addonsOn = true
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
        // revoked by the directory, by its author
        r.registry.applyRevoked([AddonRevoked(author: "https://github.com/example")])
        XCTAssertEqual(r.center.pool.alive, 0)
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).code, "addon.off")
    }

    func testAnUpdateRunsTheNewFilesAtOnce() throws {
        let r = try rig()
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).outcome.envelope, "{\"ok\":true,\"value\":{\"message\":\"ok\"}}")
        let newer = AddonCenterTests.package(version: "1.1.0", says: "from 1.1.0")
        try r.registry.install(try AddonReview.make(newer, appVersion: "0.1.0").get(), bytes: newer,
                               grants: ["ideas.read", "ideas.write", "commands"])
        XCTAssertEqual(r.center.pool.alive, 0, "the old runtime is closed with the update")
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).outcome.envelope, "{\"ok\":true,\"value\":{\"message\":\"from 1.1.0\"}}",
                       "no wait for the sweep, and no \"stopped\"")
    }

    func testACallQueuedBehindAStoppedOneIsStoppedTooAndTheNextOneRuns() throws {
        let r = try rig()
        var codes: [String?] = []
        for _ in 0..<2 {
            r.center.run(AddonCenterTests.addonId, kind: "command", command: "spin", input: "{\"ideas\":[]}") { codes.append($0.code) }
        }
        let started = Date()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { r.registry.revoke(AddonCenterTests.addonId, "net:api.example.com") }
        AddonHeartbeat.spin(until: { codes.count == 2 }, seconds: 12)
        XCTAssertEqual(codes, ["stopped", "stopped"])
        XCTAssertLessThan(Date().timeIntervalSince(started), 0.45, "neither ran to its 500 ms limit")
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
    }

    func testWhatAStoppedCallStoredIsNotKept() throws {
        let r = try rig()
        let f = try XCTUnwrap(run(r, "store", mode: "spin") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { r.registry.revoke(AddonCenterTests.addonId, "net:api.example.com") }
        })
        XCTAssertEqual(f.code, "stopped")
        XCTAssertEqual(try XCTUnwrap(run(r, "store", mode: "read")).outcome.envelope, "{\"ok\":true,\"value\":{\"message\":\"undefined\"}}")
        XCTAssertFalse(FileManager.default.fileExists(atPath: r.registry.folder.appendingPathComponent(AddonCenterTests.addonId + ".storage.json").path))
        XCTAssertTrue(try XCTUnwrap(run(r, "store", mode: "end")).outcome.ok)
        XCTAssertEqual(try XCTUnwrap(run(r, "store", mode: "read")).outcome.envelope,
                       "{\"ok\":true,\"value\":{\"message\":\"from the call that would end\"}}")
    }

    // MARK: the main thread and the registry's files (repair 1, S1)

    /// A package near its size limit, a full activity log, storage in use and
    /// a request to log: all the registry's file work of a call. None of it
    /// is on the main thread, and the main thread's beat never waits on it.
    func testThePackageTheStorageAndTheStateFileAreNeverReadOrWrittenOnTheMainThread() throws {
        let r = try rig()
        let id = AddonCenterTests.addonId
        let big = AddonCenterTests.package(version: "1.1.0", filler: 7)
        XCTAssertTrue((400_000...AddonPackageCheck.maxZipped).contains(big.count), "near the 512 KB limit: \(big.count)")
        try r.registry.install(try AddonReview.make(big, appVersion: "0.1.0").get(), bytes: big,
                               grants: ["ideas.read", "ideas.write", "commands", "net:api.example.com"])
        for _ in 0..<12 {
            r.registry.log(id, (0..<20).map { _ in
                AddonActivity(host: "api.example.com", path: "/" + String(repeating: "p", count: 300), status: 200, bytes: 1, ms: 1)
            })
        }
        r.registry.flush()
        XCTAssertEqual(r.registry.activity(id).count, AddonRegistry.activityMax)
        let lock = NSLock()
        var work: [(what: String, main: Bool, thread: String)] = []
        r.registry.onFileWork = { what in
            lock.lock()
            work.append((what, Thread.isMainThread, Thread.current.name ?? ""))
            lock.unlock()
        }
        let baseline = AddonHeartbeat.baseline()
        let heart = AddonHeartbeat()
        heart.start()
        let started = DispatchTime.now().uptimeNanoseconds
        var returned = 0.0
        let first = try XCTUnwrap(run(r, "store", mode: "end") { returned = Double(DispatchTime.now().uptimeNanoseconds - started) / 1e6 })
        let second = try XCTUnwrap(run(r, "fetch"))
        r.registry.flush()
        heart.stop()
        XCTAssertTrue(first.outcome.ok && second.outcome.ok, first.outcome.envelope + second.outcome.envelope)
        lock.lock()
        let seen = work
        lock.unlock()
        XCTAssertEqual(Set(seen.map(\.what)), ["pack", "storage", "state", "marker"], "all four kinds of file work happened")
        XCTAssertEqual(seen.filter(\.main).map(\.what), [], "none of it on the main thread")
        XCTAssertEqual(Set(seen.filter { $0.what != "state" }.map(\.thread)), ["Sorta add-on " + id], "the add-on's own thread did the rest")
        XCTAssertEqual(seen.filter { $0.what == "pack" }.count, 1, "the package is read and checked once for a runner, not for each call")
        // within a frame at 120 fps, or within what this machine's main thread jitters by with nothing running
        XCTAssertLessThanOrEqual(returned, max(8.3, baseline), "run returned to the main thread in \(returned) ms")
        XCTAssertLessThanOrEqual(heart.largestGapMs, baseline + 50, "main-thread gap \(heart.largestGapMs) ms, baseline \(baseline) ms")
        print("addon files: run returned in \(String(format: "%.3f", returned)) ms, beat \(String(format: "%.1f", heart.largestGapMs)) ms, baseline \(String(format: "%.1f", baseline)) ms")
    }

    func testTheActivityLogIsCappedAndSoIsEachPath() throws {
        let r = try rig()
        let id = AddonCenterTests.addonId
        // the add-on chooses its paths, and how many requests it makes
        for n in 0..<30 {
            r.registry.log(id, (0..<20).map { i in
                AddonActivity(host: "api.example.com", path: "/" + String(repeating: "p", count: 5_000) + "\(n * 20 + i)", status: 200, bytes: 1, ms: 1)
            })
        }
        r.registry.log(id, [AddonActivity(host: "api.example.com", path: "/last", status: 200, bytes: 1, ms: 1)])
        r.registry.flush()
        let kept = AddonRegistry(folder: r.registry.folder).activity(id)
        XCTAssertEqual(kept.count, AddonRegistry.activityMax)
        XCTAssertEqual(kept.last?.activity.path, "/last", "the newest are the ones kept")
        XCTAssertTrue(kept.allSatisfy { $0.activity.path.count <= AddonRegistry.activityPathMax })
        let size = try r.registry.folder.appendingPathComponent("addons.json").resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        XCTAssertLessThan(size, 80_000, "the file stays small")
    }

    func testARunnerMadeForOtherFilesIsReplaced() {
        let pool = AddonPool()
        let files = AddonFiles(names: ["main.js"], data: [Data()], main: "main.js")
        let first = pool.runner("a", stamp: "one", source: { files })
        XCTAssertTrue(first === pool.runner("a", stamp: "one", source: { files }), "the same files: the same runner")
        let second = pool.runner("a", stamp: "two", source: { files })
        XCTAssertTrue(second !== first && second?.stamp == "two")
        XCTAssertEqual(pool.alive, 1)
        pool.closeAll()
    }

    func testNetKeepsNoSecretsAndSendsNoCookie() throws {
        let r = try rig()
        let f = try XCTUnwrap(run(r, "fetch"))
        XCTAssertTrue(f.outcome.ok, f.outcome.envelope)
        XCTAssertTrue(f.outcome.envelope.contains("200 content-type"), f.outcome.envelope)
        XCTAssertFalse(f.outcome.envelope.contains("set-cookie"), "a cookie is never passed back")
        XCTAssertEqual(r.network.sent.count, 1)
        XCTAssertNil(r.network.sent[0].headers["cookie"], "a cookie is never sent")
        XCTAssertEqual(r.network.sent[0].headers["x-api-key"], "h-value-5512")
        let log = r.registry.activity(AddonCenterTests.addonId)
        XCTAssertEqual(log.map(\.activity.host), ["api.example.com"])
        XCTAssertEqual(log.map(\.activity.path), ["/v1/items"])
        r.registry.flush()
        let kept = try String(contentsOf: r.registry.folder.appendingPathComponent("addons.json"), encoding: .utf8)
        for planted in ["q-token-7731", "h-value-5512", "planted-7702", "c-value-4419"] {
            XCTAssertFalse(kept.contains(planted), "\(planted) kept in the activity log")
            XCTAssertFalse(log.map { "\($0.activity)" }.joined().contains(planted))
        }
    }

    func testANetRefusalNamesTheHost() throws {
        let r = try rig(grants: ["ideas.read", "commands"])
        let f = try XCTUnwrap(run(r, "fetch"))
        XCTAssertEqual(f.code, "permission")
        XCTAssertTrue(f.outcome.envelope.contains("net:api.example.com"), f.outcome.envelope)
        XCTAssertEqual(r.network.sent.count, 0)
    }

    func testAskEachTimeHoldsTheRequestUntilAnswered() throws {
        let r = try rig()
        r.registry.setAskEachTime(AddonCenterTests.addonId, true)
        var answer: ((Bool) -> Void)?
        var asked: [String] = []
        r.center.ask = { _, host, reply in asked.append(host); answer = reply }
        var out: AddonCenter.Finished?
        r.center.run(AddonCenterTests.addonId, kind: "command", command: "fetch", input: "{\"ideas\":[]}") { out = $0 }
        AddonHeartbeat.spin(until: { answer != nil }, seconds: 5)
        AddonHeartbeat.spin(until: { false }, seconds: 0.2)
        XCTAssertEqual(asked, ["api.example.com"])
        XCTAssertNil(out, "held while unanswered")
        XCTAssertEqual(r.network.sent.count, 0, "nothing sent before the answer")
        answer?(true)
        AddonHeartbeat.spin(until: { out != nil }, seconds: 5)
        XCTAssertEqual(out?.outcome.ok, true)
        XCTAssertEqual(r.network.sent.count, 1)
    }

    func testWithAddonNetOffThereIsNoNet() throws {
        let r = try rig(netOn: false)
        XCTAssertEqual(try XCTUnwrap(run(r, "has")).outcome.envelope, "{\"ok\":true,\"value\":{\"message\":\"false\"}}")
        XCTAssertEqual(try XCTUnwrap(run(r, "fetch")).code, "net.off")
        XCTAssertEqual(r.network.sent.count, 0)
        XCTAssertTrue(AddonCenter.netFromBundle, "SORTA_ADDON_NET is YES by default")
    }

    func testTheKillSwitchAndTurningOneOff() throws {
        let r = try rig()
        r.registry.addonsOn = false
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).code, "addon.off")
        r.registry.addonsOn = true
        r.registry.setOn(AddonCenterTests.addonId, false)
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).code, "addon.off")
        r.registry.setOn(AddonCenterTests.addonId, true)
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
    }

    func testThreeStrikesInADayPause() throws {
        var now: Int64 = 1_800_000_000_000
        let r = try rig(clock: { now })
        let id = AddonCenterTests.addonId
        XCTAssertFalse(r.registry.strike(id, code: "permission"), "not a limit")
        r.registry.strike(id, code: "limit.time")
        now += 25 * 3_600_000
        r.registry.strike(id, code: "limit.memory")
        r.registry.strike(id, code: "limit.stack")
        XCTAssertTrue(r.registry.canRun(id), "the first strike was more than a day ago")
        XCTAssertTrue(r.registry.strike(id, code: "limit.json"))
        XCTAssertFalse(r.registry.canRun(id))
        XCTAssertEqual(try XCTUnwrap(run(r, "go")).code, "addon.off")
        r.registry.setOn(id, true)
        XCTAssertTrue(r.registry.canRun(id), "the person turns it back on")
    }

    func testARealLimitCountsAsAStrike() throws {
        let r = try rig()
        XCTAssertEqual(try XCTUnwrap(run(r, "spin")).code, "limit.time")
        XCTAssertEqual(r.registry.addon(AddonCenterTests.addonId)?.strikes.count, 1)
    }

    func testSafeStartAfterAPlantedMarker() throws {
        let r = try rig()
        r.registry.beginCall(AddonCenterTests.addonId)
        r.registry.flush()
        let again = AddonRegistry(folder: r.registry.folder)
        XCTAssertEqual(again.startUp(), AddonCenterTests.addonId)
        XCTAssertFalse(again.addonsOn, "add-ons start off")
        XCTAssertEqual(again.addon(AddonCenterTests.addonId)?.paused, true)
        XCTAssertNil(AddonRegistry(folder: r.registry.folder).startUp(), "the marker is cleared")
        let third = AddonRegistry(folder: r.registry.folder)
        XCTAssertFalse(third.addonsOn, "what the marker said was written before it was cleared")
        XCTAssertEqual(third.addon(AddonCenterTests.addonId)?.paused, true)
        r.registry.endCall()
    }

    func testAMarkerIsWrittenOnlyWhileACallRuns() throws {
        let r = try rig()
        XCTAssertTrue(try XCTUnwrap(run(r, "go")).outcome.ok)
        XCTAssertNil(AddonRegistry(folder: r.registry.folder).startUp())
    }

    func testGrantsAreKeptPerAddonAndCheckedAgainstTheManifest() throws {
        let r = try rig()
        let id = AddonCenterTests.addonId
        XCTAssertThrowsError(try r.registry.grant(id, "net:other.example.org"))
        XCTAssertThrowsError(try r.registry.grant(id, "cards"))
        // ideas.read.folder is not asked for
        XCTAssertThrowsError(try r.registry.grant(id, AddonPermissions.folderGrant + "x"))
    }

    func testThePoolKeepsThreeAndFreesTheIdle() {
        let pool = AddonPool()
        let files = AddonFiles(names: ["main.js"], data: [Data()], main: "main.js")
        let t0 = Date()
        for i in 0..<4 { XCTAssertNotNil(pool.runner("a\(i)", files: files, now: t0.addingTimeInterval(Double(i)))) }
        XCTAssertEqual(pool.alive, 3, "at most 3 runtimes")
        pool.sweep(now: t0.addingTimeInterval(10))
        XCTAssertEqual(pool.alive, 3)
        pool.sweep(now: Date().addingTimeInterval(31))
        XCTAssertEqual(pool.alive, 0, "idle 30 s: freed")
    }

    func testTheMinuteBudget() {
        let pool = AddonPool()
        let t0 = Date()
        XCTAssertTrue(pool.withinBudget("a", now: t0))
        pool.record("a", ms: 2_999, now: t0)
        XCTAssertTrue(pool.withinBudget("a", now: t0))
        pool.record("a", ms: 2, now: t0)
        XCTAssertFalse(pool.withinBudget("a", now: t0.addingTimeInterval(30)))
        XCTAssertTrue(pool.withinBudget("b", now: t0))
        XCTAssertTrue(pool.withinBudget("a", now: t0.addingTimeInterval(61)))
    }
}
