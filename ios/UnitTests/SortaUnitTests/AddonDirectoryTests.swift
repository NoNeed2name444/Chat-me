import XCTest
@testable import Sorta

/// S07b A6: the package checker's codes on packages packed at test time, the
/// review before install, and the directory client on fixture copies of the
/// index branch (addons/fixtures/index) with a stand-in fetcher and clock.
/// Android: AddonPackageTest.kt, AddonDirectoryTest.kt. The kit's whole
/// fixture set (addons/fixtures/packages and zips) is S07a's and joins at the
/// rebase.
final class AddonDirectoryTests: XCTestCase {
    private static let base = "https://addons.example.org/index/"

    /// The index branch: its two files as they are, and each listed package packed from its folder.
    private final class Fetcher: AddonFetcher {
        var fetched: [String] = []
        let index: URL
        init(_ index: URL) { self.index = index }

        func get(_ url: String, limit: Int) -> Result<Data, AddonFetchError> {
            fetched.append(url)
            guard url.hasPrefix(AddonDirectoryTests.base) else { return .failure(AddonFetchError(code: "net.failed")) }
            let path = String(url.dropFirst(AddonDirectoryTests.base.count))
            var data: Data?
            if path == "index.json" || path == "revoked.json" {
                data = try? Data(contentsOf: index.appendingPathComponent(path))
            } else if path.hasPrefix("dist/"), path.hasSuffix(".sortaaddon") {
                let name = String(path.dropFirst(5).dropLast(".sortaaddon".count))
                data = AddonZipWriter.folder(index.appendingPathComponent("packages/" + name))
            }
            guard let d = data else { return .failure(AddonFetchError(code: "net.status")) }
            return d.count > limit ? .failure(AddonFetchError(code: "net.size")) : .success(d)
        }
    }

    private final class Rig {
        var clock: Int64 = 5_000_000_000
        let fetcher: Fetcher
        let registry: AddonRegistry
        let folder: URL
        init() throws {
            fetcher = Fetcher(try AddonTestFiles.fixtures("index"))
            folder = try AddonTestFiles.temporaryFolder()
            var now: () -> Int64 = { 0 }
            registry = AddonRegistry(folder: folder, clock: { now() })
            now = { [unowned self] in self.clock }
        }
        deinit { try? FileManager.default.removeItem(at: folder) }

        func directory(_ appVersion: String = "1.0.0") -> AddonDirectory {
            AddonDirectory(base: AddonDirectoryTests.base, fetcher: fetcher, registry: registry, appVersion: appVersion,
                           clock: { [unowned self] in self.clock })
        }

        func entry(_ id: String, _ version: String) throws -> AddonIndexEntry {
            try XCTUnwrap(try directory().index().get().first { $0.id == "io.github.example." + id && $0.version == version })
        }

        func install(_ id: String, _ version: String) throws {
            let (review, bytes) = try directory().review(try entry(id, version)).get()
            _ = try registry.install(review, bytes: bytes, grants: review.permissions.filter { $0 != "net" } + review.hosts.map { "net:" + $0 },
                                     fromDirectory: true)
        }
    }

    private func code<T>(_ r: Result<T, AddonRefusal>) -> String? {
        if case let .failure(f) = r { return f.code }
        return nil
    }

    // MARK: the directory

    func testAListedPackageIsCheckedAgainstTheIndexBeforeItIsOpened() throws {
        let r = try Rig()
        let d = r.directory()
        XCTAssertEqual(try d.index().get().count, 8)
        let (review, bytes) = try d.review(try r.entry("wordcount", "1.0.0")).get()
        XCTAssertEqual(review.sha256, AddonSHA256.hex(bytes))
        XCTAssertEqual(code(d.review(try r.entry("badsum", "1.0.0"))), "index.checksum")
        XCTAssertEqual(code(d.review(try r.entry("tagger", "1.5.0"))), "install.minSorta")
        XCTAssertEqual(code(d.review(try r.entry("tagger", "2.0.0"))), "install.api")
        let none = AddonDirectory(base: "", fetcher: r.fetcher, registry: r.registry, appVersion: "1.0.0")
        XCTAssertEqual(code(none.index()), "index.none")
        XCTAssertThrowsError(try AddonDirectory.parseIndex(Data(#"{"formatVersion":2,"addons":[]}"#.utf8)))
    }

    func testUpdatesAreOfferedBySectionSevensRules() throws {
        let r = try Rig()
        let d = r.directory()
        try r.install("wordcount", "1.0.0")
        try r.install("linkcheck", "1.0.0")
        try r.install("tagger", "1.0.0")
        let offers = d.offers(try d.index().get())
        // tagger's newer versions need a newer Sorta or a newer API, so none is offered
        XCTAssertEqual(offers.map(\.id), ["io.github.example.linkcheck", "io.github.example.wordcount"])
        XCTAssertEqual([offers[0].from, offers[0].to], ["1.0.0", "1.2.0"])
        XCTAssertEqual(offers[0].newPermissions, ["ideas.write", "net:cdn.example.com"], "new permissions are asked for one by one")
        XCTAssertFalse(offers[0].oneTap)
        XCTAssertTrue(offers[1].oneTap && offers[1].to == "1.1.0", "the same permissions: one tap")
        XCTAssertEqual(r.registry.addon("io.github.example.wordcount")?.version, "1.0.0", "an offer is never applied on its own")
        try r.install("wordcount", "1.1.0")
        XCTAssertEqual(d.offers(try d.index().get()).map(\.id), ["io.github.example.linkcheck"])
    }

    func testTheDailyCheckRunsOnlyWhileADirectoryAddonIsInstalled() throws {
        let r = try Rig()
        let d = r.directory()
        XCTAssertNil(d.dailyCheck(), "nothing from the directory: no request")
        let own = AddonZipWriter.folder(try AddonTestFiles.fixtures("index/packages/io.github.example.tagger-1.0.0"))
        _ = try r.registry.install(try AddonReview.make(own, appVersion: "1.0.0").get(), bytes: own, grants: [])
        XCTAssertNil(d.dailyCheck(), "an add-on from a file is not one")
        XCTAssertTrue(r.fetcher.fetched.isEmpty)
        try r.install("wordcount", "1.0.0")
        r.fetcher.fetched = []
        XCTAssertEqual(d.dailyCheck()?.map(\.id), ["io.github.example.wordcount"])
        XCTAssertEqual(r.fetcher.fetched, [AddonDirectoryTests.base + "revoked.json", AddonDirectoryTests.base + "index.json"])
        r.clock += AddonDirectory.dayMs - 1
        XCTAssertNil(d.dailyCheck(), "once a day")
        r.clock += 1
        XCTAssertEqual(d.dailyCheck()?.count, 1)
    }

    func testARevokedAddonIsTurnedOff() throws {
        let r = try Rig()
        let d = r.directory()
        try r.install("tagger", "1.0.0")
        try r.install("wordcount", "1.0.0")
        var stopped: [String] = []
        r.registry.onStop = { stopped.append($0) }
        XCTAssertTrue(r.registry.canRun("io.github.example.tagger"))
        _ = d.dailyCheck()
        let tagger = try XCTUnwrap(r.registry.addon("io.github.example.tagger"))
        XCTAssertTrue(tagger.revoked && !tagger.on)
        XCTAssertFalse(r.registry.canRun("io.github.example.tagger"))
        XCTAssertTrue(r.registry.canRun("io.github.example.wordcount"))
        XCTAssertEqual(stopped, ["io.github.example.tagger"])
    }

    // MARK: the package checker

    private func manifest(_ changes: [String: String] = [:]) -> String {
        var fields: [String: String] = [
            "format": #""sorta-addon""#, "formatVersion": "1", "id": #""io.github.example.tests""#, "name": #""Tests""#,
            "version": #""1.0.0""#, "author": #"{"name":"Example","url":"https://github.com/example"}"#, "license": #""MIT""#,
            "minSorta": #""1.0.0""#, "api": "1", "main": #""main.js""#, "permissions": #"["ideas.read","commands"]"#, "hosts": "[]",
            "contributes": #"{"commands":[{"id":"run","title":"Run"}]}"#,
        ]
        for (k, v) in changes { fields[k] = v }
        return "{" + fields.keys.sorted().map { "\"\($0)\":\(fields[$0]!)" }.joined(separator: ",") + "}"
    }

    private func verdict(_ manifest: String? = nil, _ more: [AddonZipWriter.Entry] = []) -> String? {
        let own = [AddonZipWriter.Entry(name: "LICENSE", data: Data("MIT\n".utf8)), AddonZipWriter.Entry(name: "main.js", data: Data("//\n".utf8)),
                   AddonZipWriter.Entry(name: "manifest.json", data: Data((manifest ?? self.manifest()).utf8))]
        return code(AddonPackageCheck.archive(AddonZipWriter.pack(own + more, sorted: false)))
    }

    private func file(_ name: String, _ text: String = "x") -> AddonZipWriter.Entry { AddonZipWriter.Entry(name: name, data: Data(text.utf8)) }

    func testAGoodPackageIsReviewedWithoutRunningAndTheInstallRulesAreTheApps() throws {
        let bytes = AddonZipWriter.pack([file("LICENSE", "MIT\n"), file("main.js", "//\n"),
            file("manifest.json", manifest(["permissions": #"["ideas.read","commands","net"]"#, "hosts": #"["api.example.com"]"#]))])
        let review = try AddonReview.make(bytes, appVersion: "1.0.0").get()
        XCTAssertEqual(review.package.id, "io.github.example.tests")
        XCTAssertEqual(review.permissions, ["ideas.read", "commands", "net"])
        XCTAssertEqual(review.hosts, ["api.example.com"])
        XCTAssertEqual(review.size, bytes.count)
        XCTAssertEqual(review.sha256, AddonSHA256.hex(bytes))
        XCTAssertEqual(AddonSHA256.hex(Data()), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        XCTAssertTrue(review.allows("net:api.example.com") && review.allows("ideas.read"))
        XCTAssertFalse(review.allows("net") || review.allows("net:other.example.org") || review.allows("ideas.write"))

        func refusal(_ changes: [String: String], app: String = "1.0.0") -> String? {
            code(AddonReview.make(AddonZipWriter.pack([file("LICENSE"), file("main.js", "//"), file("manifest.json", manifest(changes))]), appVersion: app))
        }
        XCTAssertEqual(refusal(["api": "2"]), "install.api")
        XCTAssertEqual(refusal(["minSorta": #""1.0.1""#]), "install.minSorta")
        XCTAssertEqual(refusal([:], app: "1.0.0-beta.2"), "install.minSorta")
        XCTAssertNil(refusal(["minSorta": #""1.0.0-rc.1""#]))
        XCTAssertEqual([AddonPackageCheck.compare("1.0.0-alpha", "1.0.0-alpha.1"), AddonPackageCheck.compare("1.0.0-beta.11", "1.0.0-beta.2"),
                        AddonPackageCheck.compare("1.0.0-1", "1.0.0-alpha"), AddonPackageCheck.compare("1.9.0", "1.10.0"),
                        AddonPackageCheck.compare("2.0.0", "2.0.0")], [-1, 1, -1, -1, 0])
    }

    func testTheArchiveAndFileRulesGiveTheirCodes() {
        XCTAssertNil(verdict())
        XCTAssertEqual(code(AddonPackageCheck.archive(Data("not a zip at all, only words".utf8))), "zip.unreadable")
        XCTAssertEqual(code(AddonPackageCheck.archive(Data(count: AddonPackageCheck.maxZipped + 1))), "zip.size")
        XCTAssertEqual(verdict(nil, (1...198).map { file("f\($0).js") }), "zip.entries")
        XCTAssertEqual(verdict(nil, [AddonZipWriter.Entry(name: "a.js", data: Data("x".utf8), flags: 1)]), "zip.encrypted")
        XCTAssertEqual(verdict(nil, [AddonZipWriter.Entry(name: "a.js", data: Data("x".utf8), method: 12)]), "zip.unreadable")
        for path in ["../a.js", "/a.js", "a\\b.js", "C:a.js", "a//b.js", "./a.js", "a b.js", "é.js"] {
            XCTAssertEqual(verdict(nil, [file(path)]), "zip.path", path)
        }
        XCTAssertEqual(verdict(nil, [AddonZipWriter.Entry(name: "a.js", data: Data("main.js".utf8), mode: 0o120777)]), "zip.link")
        XCTAssertEqual(verdict(nil, [file("a.js"), file("a.js")]), "zip.duplicate")
        XCTAssertEqual(verdict(nil, [file("a.js"), file("A.js")]), "zip.case")
        var gap = Data([0])
        gap.append(AddonZipWriter.pack([file("LICENSE"), file("main.js", "//"), file("manifest.json", manifest())]))
        XCTAssertEqual(code(AddonPackageCheck.archive(gap)), "zip.unreadable", "bytes no entry accounts for")

        XCTAssertEqual(verdict(nil, [file("picture.png")]), "file.type")
        XCTAssertEqual(verdict(nil, [file(".hidden.js")]), "file.type")
        XCTAssertEqual(verdict(nil, [file("lib/README.md")]), "file.type")
        XCTAssertEqual(verdict(nil, [file("a/b/c/d.js")]), "file.depth")
        XCTAssertNil(verdict(nil, [file("a/b/d.js"), file("README.md", "Words")]))
        XCTAssertEqual(verdict(nil, [AddonZipWriter.Entry(name: "a.js", data: Data([0x2F, 0x2F, 0]))]), "file.text")
        XCTAssertEqual(verdict(nil, [AddonZipWriter.Entry(name: "a.js", data: Data([0x2F, 0x2F, 0xC0, 0xAF]))]), "file.text")
        XCTAssertEqual(verdict(nil, [file("a.js", "const p = '<SVG width=\"1\">';")]), "file.picture")
        XCTAssertEqual(verdict(nil, [file("a.json", #"{"p":"Data:Image/png;base64,AAAA"}"#)]), "file.picture")
        XCTAssertEqual(code(AddonPackageCheck.archive(AddonZipWriter.pack([file("main.js", "//")]))), "manifest.missing")
        XCTAssertEqual(verdict("[1]"), "manifest.json")
        XCTAssertEqual(verdict("\u{FEFF}" + manifest()), "manifest.json")
        XCTAssertEqual(verdict(manifest().replacingOccurrences(of: "{\"api\":1,", with: "{\"api\":1,\"api\":1,")), "manifest.json")
    }

    func testTheManifestRulesGiveTheirCodes() {
        let cases: [(String, String, String)] = [
            ("format", #""other""#, "format"), ("formatVersion", "2", "formatVersion"), ("surprise", "1", "manifest.json"),
            ("id", #""NoDots""#, "id"), ("id", #""com.cramdown.sorta""#, "id.reserved"),
            ("name", #"" padded""#, "name"), ("name", #""zero​width""#, "name"), ("name", #""line break""#, "name"),
            ("version", #""1.0""#, "version"), ("version", #""1.0.0+build""#, "version"), ("minSorta", #""01.0.0""#, "minSorta"),
            ("api", "1.5", "api"), ("author", #"{"name":"A","url":"http://example.com"}"#, "author"),
            ("license", #""Proprietary""#, "license"), ("main", #""other.js""#, "main.missing"),
            ("permissions", #"["ideas.read","ideas.read","commands"]"#, "permissions"),
            ("permissions", #"["ideas.read","ideas.read.folder","commands"]"#, "permissions"),
            ("permissions", #"["camera","commands"]"#, "permissions"),
            ("hosts", #"["api.example.com"]"#, "hosts"),
            ("contributes", #"{"commands":[{"id":"Run It","title":"Run"}]}"#, "commands"),
            ("contributes", "{}", "contributes"),
            ("contributes", #"{"commands":[{"id":"run","title":"Run"}],"cardView":{"id":"v","title":"V"}}"#, "contributes"),
        ]
        for (key, value, expected) in cases {
            XCTAssertEqual(verdict(manifest([key: value])), expected, "\(key) \(value)")
        }
        func net(_ hosts: String) -> String? { verdict(manifest(["permissions": #"["commands","net"]"#, "hosts": hosts])) }
        XCTAssertEqual(net("[]"), "hosts")
        XCTAssertEqual(net(#"["192.168.0.1"]"#), "hosts")
        XCTAssertEqual(net(#"["*.example.com"]"#), "hosts")
        XCTAssertNil(net(#"["api.example.com"]"#))
        let cards = #"{"commands":[{"id":"run","title":"Run"}],"cardView":{"id":"view","title":"View"}}"#
        XCTAssertEqual(verdict(manifest(["permissions": #"["commands","cards"]"#, "contributes": cards])), "cards.read")
        XCTAssertNil(verdict(manifest(["permissions": #"["ideas.read.folder","commands","cards"]"#, "contributes": cards])))
        XCTAssertEqual(AddonPackageCheck.contrast("#000000", "#FFFFFF"), 21, accuracy: 1e-9)
        XCTAssertEqual(AddonLicenses.osi.count, 141)
    }
}
