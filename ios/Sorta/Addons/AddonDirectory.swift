import Foundation

// MARK: - Add-ons (S07b): the public directory's client (section 7)
//
// Reads index.json and revoked.json from the directory's index branch (the
// SORTA_ADDONS_INDEX_URL build setting; empty until the lead fills it in).
// Every package is checked against its SHA-256 in the index before it is
// opened. Updates are offered, never applied silently. The daily check runs
// only while an add-on from the directory is installed. Foundation only.

struct AddonIndexEntry: Equatable {
    var id: String
    var version: String
    var sha256: String
    var size: Int
    var permissions: [String]
    var hosts: [String]
    var minSorta: String
    var api: Int
    var file: String
    /// The add-on's name and its author's url (index.json nests each add-on's versions; addons/spec/directory.md).
    var name = ""
    var authorUrl = ""
}

/// One entry of revoked.json: an add-on by `id` (every version, or only
/// `versions`), or every add-on of the author whose url is `author`.
struct AddonRevoked: Equatable {
    var id: String? = nil
    var versions: [String]? = nil
    var author: String? = nil

    func covers(_ a: AddonInstalled) -> Bool {
        if let id { return id == a.id && (versions == nil || versions!.contains(a.version)) }
        if let author { return !a.authorUrl.isEmpty && a.authorUrl == author }
        return false
    }
}

/// A newer listed version of an installed add-on.
struct AddonUpdateOffer: Equatable {
    var id: String
    var from: String
    var to: String
    /// The grants the person is asked for, one by one; empty means one tap updates.
    var newPermissions: [String]
    var oneTap: Bool { newPermissions.isEmpty }
}

final class AddonDirectory {
    static let maxPackage = AddonPackageCheck.maxZipped
    static let maxIndex = 1_048_576
    static let dayMs: Int64 = 86_400_000

    let base: String
    private let fetcher: AddonFetcher
    private let registry: AddonRegistry
    private let appVersion: String
    private let clock: () -> Int64

    /// `base` ends with "/" and holds index.json, revoked.json and dist/.
    init(base: String, fetcher: AddonFetcher, registry: AddonRegistry, appVersion: String,
         clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.base = base.hasSuffix("/") || base.isEmpty ? base : base + "/"
        self.fetcher = fetcher
        self.registry = registry
        self.appVersion = appVersion
        self.clock = clock
    }

    /// The URL from the app's Info.plist (SortaAddonsIndexURL); empty means no directory yet.
    static var configuredBase: String {
        (Bundle.main.object(forInfoDictionaryKey: "SortaAddonsIndexURL") as? String) ?? ""
    }

    // MARK: reading the index

    /// Every listed version, add-on by add-on in the file's order. A field
    /// the app doesn't know is passed over.
    static func parseIndex(_ data: Data) throws -> [AddonIndexEntry] {
        guard let j = try? AddonJSON.parse(data: data), j["format"]?.string == "sorta-addons-index",
              j["formatVersion"]?.integer == 1, let list = j["addons"]?.array else {
            throw AddonRefusal(code: "index.json")
        }
        var out: [AddonIndexEntry] = []
        for a in list {
            guard let id = a["id"]?.string, let versions = a["versions"]?.array, AddonPackageCheck.validId(id) else {
                throw AddonRefusal(code: "index.json")
            }
            let name = a["name"]?.string ?? ""
            let author = a["author"]?["url"]?.string ?? ""
            for e in versions {
                guard let version = e["version"]?.string, let sha = e["sha256"]?.string,
                      sha.utf8.count == 64, let size = e["size"]?.integer, let file = e["file"]?.string,
                      AddonPackageCheck.semver(version) != nil,
                      file == "dist/\(id)-\(version).sortaaddon" else { throw AddonRefusal(code: "index.json") }
                out.append(AddonIndexEntry(id: id, version: version, sha256: sha.lowercased(), size: size,
                                           permissions: e["permissions"]?.strings ?? [], hosts: e["hosts"]?.strings ?? [],
                                           minSorta: e["minSorta"]?.string ?? "0.0.0", api: e["api"]?.integer ?? 1, file: file,
                                           name: name, authorUrl: author))
            }
        }
        return out
    }

    /// revoked.json's entries. One that is neither an id nor an https
    /// author, or whose versions are not a non-empty list of texts, is passed
    /// over: a broken entry never widens to "every version".
    static func parseRevoked(_ data: Data) throws -> [AddonRevoked] {
        guard let j = try? AddonJSON.parse(data: data), j["format"]?.string == "sorta-addons-revoked",
              j["formatVersion"]?.integer == 1, let list = j["revoked"]?.array else {
            throw AddonRefusal(code: "index.json")
        }
        return list.compactMap { e in
            let id = e["id"]?.string, author = e["author"]?.string, versions = e["versions"]
            if let id, author == nil {
                guard let versions else { return AddonRevoked(id: id) }
                let listed = (versions.array ?? []).map(\.string)
                if listed.isEmpty || listed.contains(where: { $0 == nil }) { return nil }
                return AddonRevoked(id: id, versions: listed.compactMap { $0 })
            }
            if id == nil, let author, versions == nil, author.hasPrefix("https://") { return AddonRevoked(author: author) }
            return nil
        }
    }

    func index() -> Result<[AddonIndexEntry], AddonRefusal> {
        guard !base.isEmpty else { return .failure(AddonRefusal(code: "index.none")) }
        switch fetcher.get(base + "index.json", limit: AddonDirectory.maxIndex) {
        case let .failure(e): return .failure(AddonRefusal(code: e.code))
        case let .success(d):
            do { return .success(try AddonDirectory.parseIndex(d)) } catch { return .failure(AddonRefusal(code: "index.json")) }
        }
    }

    /// A listed package's bytes, refused unless they match the index's size and SHA-256.
    func download(_ e: AddonIndexEntry) -> Result<Data, AddonRefusal> {
        guard e.size <= AddonDirectory.maxPackage else { return .failure(AddonRefusal(code: "zip.size")) }
        switch fetcher.get(base + e.file, limit: AddonDirectory.maxPackage) {
        case let .failure(f): return .failure(AddonRefusal(code: f.code))
        case let .success(d):
            guard d.count == e.size, AddonSHA256.hex(d) == e.sha256 else { return .failure(AddonRefusal(code: "index.checksum")) }
            return .success(d)
        }
    }

    /// The review of a listed package: downloaded, checksum checked, then checked as any file is.
    func review(_ e: AddonIndexEntry) -> Result<(AddonReview, Data), AddonRefusal> {
        switch download(e) {
        case let .failure(r): return .failure(r)
        case let .success(d):
            switch AddonReview.make(d, appVersion: appVersion) {
            case let .failure(r): return .failure(r)
            case let .success(rv):
                guard rv.package.id == e.id, rv.package.version == e.version else {
                    return .failure(AddonRefusal(code: "index.checksum"))
                }
                return .success((rv, d))
            }
        }
    }

    // MARK: updates

    /// For each installed directory add-on, the newest listed version this
    /// app can run, if it is newer; new permissions are listed one by one.
    func offers(_ entries: [AddonIndexEntry]) -> [AddonUpdateOffer] {
        var out: [AddonUpdateOffer] = []
        for a in registry.addons where a.fromDirectory && !a.revoked {
            let newer = entries.filter {
                $0.id == a.id && AddonPackageCheck.compare($0.version, a.version) > 0 && $0.api <= AddonPackageCheck.appAPI
                    && AddonPackageCheck.compare($0.minSorta, appVersion) <= 0
            }
            guard let best = newer.max(by: { AddonPackageCheck.compare($0.version, $1.version) < 0 }) else { continue }
            let had = Set(a.permissions + a.hosts.map { "net:" + $0 })
            let wants = best.permissions.filter { $0 != "net" } + best.hosts.map { "net:" + $0 }
            out.append(AddonUpdateOffer(id: a.id, from: a.version, to: best.version, newPermissions: wants.filter { !had.contains($0) }))
        }
        return out.sorted { $0.id < $1.id }
    }

    // MARK: the daily check

    /// At most once a day, and only while a directory add-on is installed:
    /// revoked.json turns add-ons off, and the index gives update offers.
    /// nil when it did not run.
    func dailyCheck() -> [AddonUpdateOffer]? {
        let now = clock()
        guard !base.isEmpty, registry.hasDirectoryAddon, now - registry.lastDirectoryCheck >= AddonDirectory.dayMs else {
            return nil
        }
        // the day starts only when both files came and were read: a failed
        // check is tried again at the next chance
        guard case let .success(d) = fetcher.get(base + "revoked.json", limit: AddonDirectory.maxIndex),
              let list = try? AddonDirectory.parseRevoked(d) else { return [] }
        registry.applyRevoked(list)
        guard case let .success(entries) = index() else { return [] }
        registry.lastDirectoryCheck = now
        return offers(entries)
    }
}
