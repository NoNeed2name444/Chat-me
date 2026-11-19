import Foundation
import XCTest
@testable import Sorta

/// A store that only holds what it was given: the kit's sample ideas have
/// ids of their own, which NoteStore doesn't keep, and the template only reads.
final class AddonSnapshotCases: AddonCaseStore {
    private var snap: AddonSnapshot

    init(_ snap: AddonSnapshot) { self.snap = snap }

    func snapshot() -> AddonSnapshot { snap }
    func retitle(_ id: String, _ title: String) {}
    func remove(_ id: String) {}
    func apply(_ ops: [AddonOp], now: Int64) -> AddonApplied { AddonApplied() }
    func idea(_ id: String) -> AddonJSON { .null }
}

/// Where the kit and the app join (S07, section 8; S07b A5): the kit's
/// template gives what its own tests/expect.json says, through the real
/// engine, as the harness's `expect` and Android find too; every call of
/// API v1 (addons/kit/api/v1.json) is there; and the Swift checker gives the
/// kit's verdict on every archive of the kit's fixtures
/// (addons/conformance/archives.json, which archives.py packs from
/// addons/fixtures/packages and zips, with the verdict check.py gives).
/// Android: AddonJoinTest.kt and AddonJoinDeviceTest.kt.
@MainActor
final class AddonJoinTests: XCTestCase {
    private func json(_ url: URL) throws -> AddonJSON { try AddonJSON.parse(data: Data(contentsOf: url)) }

    // MARK: the template

    func testTheTemplateGivesWhatItsOwnTestsExpect() throws {
        let kit = try AddonTestFiles.folder("kit")
        let template = kit.appendingPathComponent("template")
        let manifest = try json(template.appendingPathComponent("manifest.json"))
        let expect = try json(template.appendingPathComponent("tests/expect.json"))
        XCTAssertEqual(expect["ideas"]?.string, "api/sample-ideas.json")
        let suite = AddonConformance(dir: kit, addon: template, store: try json(kit.appendingPathComponent("api/sample-ideas.json")))
        let asked: [String] = manifest["permissions"]?.strings ?? []
        let hosts: [String] = manifest["hosts"]?.strings ?? []
        let grants = AddonJSON.strings(asked.filter { !$0.hasPrefix("net") } + hosts.map { "net:" + $0 })
        let ids: [String] = (manifest["contributes"]?["commands"]?.array ?? []).compactMap { $0["id"]?.string }
        let commands = AddonJSON.strings(ids)
        func want(_ c: AddonJSON) -> String {
            "result " + AddonJSON.object([("ok", .bool(true)), ("value", c["reply"] ?? .null)]).canonical
        }
        func got(_ c: AddonJSON) -> String {
            let text = suite.block(c) { AddonSnapshotCases($0) }
            return text.components(separatedBy: "\n").first { $0.hasPrefix("result ") } ?? "no result"
        }
        var ran = 0
        for c in expect["commands"]?.array ?? [] {
            let one = AddonJSON.object([
                ("name", .string("command")), ("kind", .string("command")), ("grants", grants), ("commands", commands),
                ("command", c["command"] ?? .null), ("settings", c["settings"] ?? .object([])),
                ("input", .object([("ideas", c["chosen"] ?? .array([]))])),
            ])
            XCTAssertEqual(got(one), want(c), c.canonical)
            ran += 1
        }
        for c in expect["cards"]?.array ?? [] {
            let one = AddonJSON.object([
                ("name", .string("card")), ("kind", .string("card")), ("grants", grants), ("commands", commands),
                ("card", c["idea"] ?? .null), ("settings", c["settings"] ?? .object([])),
            ])
            XCTAssertEqual(got(one), want(c), c.canonical)
            ran += 1
        }
        XCTAssertEqual(ran, 20, "every case of the template's tests ran")
    }

    // MARK: API v1

    private func apiNames() throws -> [String] {
        let api = try json(try AddonTestFiles.folder("kit").appendingPathComponent("api/v1.json"))
        guard case let .object(pairs)? = api["calls"] else { return [] }
        return pairs.map(\.0)
    }

    func testEveryCallOfAPIV1IsAFunctionInTheEngine() throws {
        let names = try apiNames()
        XCTAssertEqual(names.count, 20)
        let main = "const names = " + AddonJSON.strings(names).canonical + ";\n"
            + "const missing = names.filter((n) => typeof n.split('.').reduce((o, k) => (o == null ? o : o[k]), globalThis) !== 'function');\n"
            + "console.log(names.length + ' calls, missing: ' + (missing.join(' ') || 'none'));\n"
        let one = AddonJSON.object([
            ("name", .string("api")), ("kind", .string("load")),
            ("grants", AddonJSON.strings(["ideas.read", "ideas.write", "commands", "cards"])),
            ("commands", AddonJSON.strings(["case"])),
            ("files", .object([("main.js", .object([("text", .string(main))]))])),
        ])
        let suite = try AddonConformance(dir: try AddonTestFiles.folder("conformance"))
        XCTAssertEqual(suite.block(one) { AddonSnapshotCases($0) }, "== api\nload ok\nlog 20 calls, missing: none\n")
    }

    func testEveryCallOfAPIV1IsOneTheHostAnswers() throws {
        let short = try apiNames().map { $0.hasPrefix("sorta.") ? String($0.dropFirst(6)) : $0 }
        XCTAssertEqual(short.count, 20)
        XCTAssertEqual(short.sorted(), (AddonPermissions.calls + ["has"]).sorted(),
                       "the host's table is the API's calls, and has() beside them")
        let conformance = try AddonTestFiles.folder("conformance")
        let snap = AddonSnapshot.read(try json(conformance.appendingPathComponent("store.json")))
        func call() -> AddonCall {
            let c = AddonCall(AddonCall.Setup(snapshot: snap,
                                              grants: ["ideas.read", "ideas.write", "commands", "cards", "net:api.example.com"],
                                              commands: ["run"], netOn: true, now: 1_793_523_600_000))
            c.phase = "command"
            return c
        }
        for name in short { XCTAssertNotEqual(call().handle(name, "[]"), "!api.unknown", name) }
        XCTAssertEqual(call().handle("ideas.delete", "[]"), "!api.unknown")
    }

    // MARK: the kit's fixtures, as archives

    private struct Archive {
        var name: String
        var data: Data
        var verdict: String
        var reason: String?
        var appSorta: String?
        var appAPI: Int
        var wanted: String { reason ?? verdict }
    }

    private func document() throws -> AddonJSON {
        try json(try AddonTestFiles.folder("conformance").appendingPathComponent("archives.json"))
    }

    private func archives() throws -> [Archive] {
        try (document()["archives"]?.array ?? []).map { a in
            let name = a["name"]?.string ?? "?"
            let packed = try XCTUnwrap(Data(base64Encoded: a["deflated"]?.string ?? ""), name)
            let size = a["size"]?.integer ?? 0
            let data = try AddonInflate.inflate(packed, limit: size)
            XCTAssertEqual(data.count, size, "\(name) unpacks to its size")
            XCTAssertEqual(AddonSHA256.hex(data), a["sha256"]?.string, "\(name) is the bytes archives.py packed")
            return Archive(name: name, data: data, verdict: a["verdict"]?.string ?? "?", reason: a["reason"]?.string,
                           appSorta: a["app"]?["sorta"]?.string, appAPI: a["app"]?["api"]?.integer ?? 1)
        }
    }

    /// "ok", or the code the app refuses the archive with: the checker alone,
    /// or with the install rules of the app the fixture names.
    private func verdict(_ a: Archive, install: Bool) -> String {
        switch AddonPackageCheck.archive(a.data) {
        case let .failure(r): return r.code
        case let .success(p):
            guard install, let sorta = a.appSorta else { return "ok" }
            return AddonPackageCheck.install(p, appVersion: sorta, appAPI: a.appAPI)?.code ?? "ok"
        }
    }

    func testTheFileHoldsEveryFixtureThatCanBeAnArchive() throws {
        let doc = try document(), all = try archives()
        XCTAssertEqual(all.count, 95)
        XCTAssertEqual(doc["count"]?.integer, 95)
        XCTAssertEqual(all.filter { $0.verdict == "refused" }.count, 83)
        XCTAssertEqual(all.filter { $0.verdict == "ok" }.count, 12)
        XCTAssertEqual((doc["archives"]?.array ?? []).filter { $0["from"]?.string == "packages" }.count, 71)
        XCTAssertEqual((doc["archives"]?.array ?? []).filter { $0["from"]?.string == "zips" }.count, 24)
        XCTAssertEqual((doc["leftOut"]?.array ?? []).map { $0["name"]?.string }, ["file-type-fifo"])
        XCTAssertEqual(Set(all.map(\.name)).count, 95, "every name once")
        XCTAssertTrue(all.allSatisfy { ($0.verdict == "refused") == ($0.reason != nil) }, "a refused archive names its reason")
    }

    func testEveryArchiveGetsTheVerdictTheKitsCheckerGives() throws {
        let differ = try archives().compactMap { a -> String? in
            let got = verdict(a, install: true)
            return got == a.wanted ? nil : "\(a.name): \(got), and the kit says \(a.wanted)"
        }
        XCTAssertEqual(differ, [])
    }

    func testTheInstallCodesNeedTheAppAndEveryOtherVerdictIsTheCheckersOwn() throws {
        let all = try archives()
        let withApp = all.filter { $0.appSorta != nil }
        XCTAssertEqual(withApp.map(\.name), ["install-api", "install-min-sorta", "ok-install"])
        XCTAssertEqual(withApp.map { verdict($0, install: true) }, ["install.api", "install.minSorta", "ok"])
        // the package itself is good: only the app it is installed on refuses it
        for a in withApp { XCTAssertEqual(verdict(a, install: false), "ok", a.name) }
        for a in all where a.appSorta == nil { XCTAssertEqual(verdict(a, install: false), a.wanted, a.name) }
    }
}
