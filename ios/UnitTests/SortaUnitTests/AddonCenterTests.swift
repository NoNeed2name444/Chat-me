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
                        hosts: [String] = ["api.example.com"]) -> Data {
        let manifest = AddonJSON.object([
            ("format", .string("sorta-addon")), ("formatVersion", .number(1)), ("id", .string(addonId)),
            ("name", .string("Tests")), ("version", .string("1.0.0")),
            ("author", .object([("name", .string("Example")), ("url", .string("https://github.com/example"))])),
            ("license", .string("MIT")), ("minSorta", .string("0.1.0")), ("api", .number(1)), ("main", .string("main.js")),
            ("permissions", AddonJSON.strings(permissions)), ("hosts", AddonJSON.strings(hosts)),
            ("contributes", .object([("commands", .array(["go", "spin", "fetch", "has"].map {
                .object([("id", .string($0)), ("title", .string($0))])
            }))])),
        ])
        let main = """
        sorta.commands.on('go', async () => { await sorta.ideas.capture({ text: 'From an add-on' }); return { message: 'ok' }; });
        sorta.commands.on('spin', async () => { while (true) {} });
        sorta.commands.on('fetch', async () => {
          const r = await sorta.net.fetch('https://api.example.com/v1/items?token=q-token-7731',
            { headers: { 'X-Api-Key': 'h-value-5512', Cookie: 'c=planted-7702' } });
          return { message: String(r.status) + ' ' + Object.keys(r.headers).sort().join(',') };
        });
        sorta.commands.on('has', async () => ({ message: String(sorta.has('net')) }));
        """
        return AddonZipWriter.pack([.init(name: "manifest.json", data: Data(manifest.canonical.utf8)),
                                    .init(name: "main.js", data: Data(main.utf8))])
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
        let routes = try AddonJSON.parse(data: Data(contentsOf: AddonTestFiles.fixtures("net/routes.json")))
        let network = AddonRoutesNetwork(routes: routes)
        let center = AddonCenter(store: store, registry: registry, network: network, netOn: netOn)
        addTeardownBlock { center.pool.closeAll() }
        return Rig(store: store, registry: registry, center: center, network: network)
    }

    private func run(_ r: Rig, _ command: String, while doing: (() -> Void)? = nil) -> AddonCenter.Finished? {
        var out: AddonCenter.Finished?
        r.center.run(AddonCenterTests.addonId, kind: "command", command: command, input: "{\"ideas\":[]}") { out = $0 }
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
        let again = AddonRegistry(folder: r.registry.folder)
        XCTAssertEqual(again.startUp(), AddonCenterTests.addonId)
        XCTAssertFalse(again.addonsOn, "add-ons start off")
        XCTAssertEqual(again.addon(AddonCenterTests.addonId)?.paused, true)
        XCTAssertNil(AddonRegistry(folder: r.registry.folder).startUp(), "the marker is cleared")
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
