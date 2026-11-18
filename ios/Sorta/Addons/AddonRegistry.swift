import Foundation

// MARK: - Add-ons (S07b): what is installed, what it may do, and the kill switch
//
// Kept in a folder of its own (the app's Application Support, or a test's
// temporary folder): the installed packages, each add-on's grants, its
// on/off and paused state, its strikes, storage and activity, and the
// safe-start marker. Thread-safe: the main thread and add-on threads use it.

struct AddonInstalled: Equatable {
    var id: String
    var version: String
    var sha256: String
    var permissions: [String]
    var hosts: [String]
    var grants: [String]
    var on = true
    var paused = false
    var revoked = false
    var fromDirectory = false
    /// net: hold each request until the person answers (section 4).
    var askEachTime = false
    /// When each strike happened, in ms.
    var strikes: [Int64] = []
    var settings = "{}"

    var json: AddonJSON {
        .object([
            ("askEachTime", .bool(askEachTime)), ("fromDirectory", .bool(fromDirectory)),
            ("grants", AddonJSON.strings(grants)), ("hosts", AddonJSON.strings(hosts)), ("id", .string(id)),
            ("on", .bool(on)), ("paused", .bool(paused)), ("permissions", AddonJSON.strings(permissions)),
            ("revoked", .bool(revoked)), ("settings", .string(settings)), ("sha256", .string(sha256)),
            ("strikes", .array(strikes.map { .number(Double($0)) })), ("version", .string(version)),
        ])
    }

    init(id: String, version: String, sha256: String, permissions: [String], hosts: [String], grants: [String]) {
        self.id = id
        self.version = version
        self.sha256 = sha256
        self.permissions = permissions
        self.hosts = hosts
        self.grants = grants
    }

    init?(_ j: AddonJSON) {
        guard let id = j["id"]?.string, let version = j["version"]?.string else { return nil }
        self.init(id: id, version: version, sha256: j["sha256"]?.string ?? "", permissions: j["permissions"]?.strings ?? [],
                  hosts: j["hosts"]?.strings ?? [], grants: j["grants"]?.strings ?? [])
        on = j["on"]?.bool ?? true
        paused = j["paused"]?.bool ?? false
        revoked = j["revoked"]?.bool ?? false
        fromDirectory = j["fromDirectory"]?.bool ?? false
        askEachTime = j["askEachTime"]?.bool ?? false
        strikes = (j["strikes"]?.array ?? []).compactMap { $0.number.map { Int64($0) } }
        settings = j["settings"]?.string ?? "{}"
    }
}

/// One logged request, kept 30 days: host and path, never the query or a header.
struct AddonLogged: Equatable {
    var at: Int64
    var activity: AddonActivity
}

final class AddonRegistry {
    static let strikeWindowMs: Int64 = 24 * 3_600_000
    static let strikesToPause = 3
    static let activityDays: Int64 = 30

    let folder: URL
    private let clock: () -> Int64
    private let lock = NSLock()
    private var items: [String: AddonInstalled] = [:]
    private var allOn = true
    private var lastCheck: Int64 = 0
    private var logs: [String: [AddonLogged]] = [:]
    private var stores: [String: AddonStorage] = [:]
    /// Called when a grant is taken away or an add-on is turned off: the pool
    /// stops its running call.
    var onStop: (String) -> Void = { _ in }

    init(folder: URL, clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.folder = folder
        self.clock = clock
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        load()
    }

    private var stateURL: URL { folder.appendingPathComponent("addons.json") }
    private var markerURL: URL { folder.appendingPathComponent("running.marker") }
    private func packageURL(_ id: String) -> URL { folder.appendingPathComponent(id + ".sortaaddon") }
    private func storageURL(_ id: String) -> URL { folder.appendingPathComponent(id + ".storage.json") }

    // MARK: state

    var addons: [AddonInstalled] {
        lock.lock()
        defer { lock.unlock() }
        return items.values.sorted { $0.id < $1.id }
    }

    func addon(_ id: String) -> AddonInstalled? {
        lock.lock()
        defer { lock.unlock() }
        return items[id]
    }

    /// The kill switch for every add-on.
    var addonsOn: Bool {
        get { lock.lock(); defer { lock.unlock() }; return allOn }
        set {
            lock.lock()
            allOn = newValue
            let ids = Array(items.keys)
            saveLocked()
            lock.unlock()
            if !newValue { ids.forEach(onStop) }
        }
    }

    var lastDirectoryCheck: Int64 {
        get { lock.lock(); defer { lock.unlock() }; return lastCheck }
        set { lock.lock(); lastCheck = newValue; saveLocked(); lock.unlock() }
    }

    var hasDirectoryAddon: Bool { addons.contains { $0.fromDirectory } }

    /// Whether a call may start now.
    func canRun(_ id: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard allOn, let a = items[id] else { return false }
        return a.on && !a.paused && !a.revoked
    }

    // MARK: install, update, remove

    /// Installs (or updates) from a review, with the grants the person chose.
    @discardableResult
    func install(_ review: AddonReview, bytes: Data, grants: [String], fromDirectory: Bool = false) throws -> AddonInstalled {
        guard grants.allSatisfy(review.allows) else { throw AddonRefusal(code: "permissions") }
        let p = review.package
        var a = AddonInstalled(id: p.id, version: p.version, sha256: review.sha256, permissions: p.permissions,
                               hosts: p.hosts, grants: grants)
        a.fromDirectory = fromDirectory
        try bytes.write(to: packageURL(p.id), options: .atomic)
        lock.lock()
        if let old = items[p.id] {
            a.on = old.on
            a.askEachTime = old.askEachTime
            a.settings = old.settings
            a.fromDirectory = old.fromDirectory || fromDirectory
        }
        items[p.id] = a
        saveLocked()
        lock.unlock()
        onStop(p.id)
        return a
    }

    func remove(_ id: String) {
        onStop(id)
        lock.lock()
        items[id] = nil
        logs[id] = nil
        stores[id] = nil
        saveLocked()
        lock.unlock()
        try? FileManager.default.removeItem(at: packageURL(id))
        try? FileManager.default.removeItem(at: storageURL(id))
    }

    /// The installed package, checked again as it is read.
    func package(_ id: String) -> AddonPackage? {
        guard let a = addon(id), let d = try? Data(contentsOf: packageURL(id)), AddonSHA256.hex(d) == a.sha256,
              case let .success(p) = AddonPackageCheck.archive(d) else { return nil }
        return p
    }

    // MARK: grants and switches

    func grant(_ id: String, _ g: String) throws {
        lock.lock()
        defer { lock.unlock() }
        guard var a = items[id] else { return }
        let allowed: Bool
        if g.hasPrefix("net:") {
            allowed = a.permissions.contains("net") && a.hosts.contains(String(g.dropFirst(4)))
        } else if g.hasPrefix(AddonPermissions.folderGrant) {
            allowed = a.permissions.contains("ideas.read.folder")
        } else {
            allowed = g != "net" && g != "ideas.read.folder" && a.permissions.contains(g)
        }
        guard allowed else { throw AddonRefusal(code: "permissions") }
        if !a.grants.contains(g) { a.grants.append(g) }
        items[id] = a
        saveLocked()
    }

    /// Takes a grant away; a running call is stopped.
    func revoke(_ id: String, _ g: String) {
        lock.lock()
        items[id]?.grants.removeAll { $0 == g }
        saveLocked()
        lock.unlock()
        onStop(id)
    }

    func setOn(_ id: String, _ on: Bool) {
        lock.lock()
        items[id]?.on = on
        if on { items[id]?.paused = false; items[id]?.strikes = [] }
        saveLocked()
        lock.unlock()
        if !on { onStop(id) }
    }

    func setAskEachTime(_ id: String, _ ask: Bool) {
        lock.lock()
        items[id]?.askEachTime = ask
        saveLocked()
        lock.unlock()
    }

    func setSettings(_ id: String, _ canonical: String) {
        lock.lock()
        items[id]?.settings = canonical
        saveLocked()
        lock.unlock()
    }

    /// revoked.json: matching add-ons are turned off everywhere ("*" or no
    /// version means every version).
    func applyRevoked(_ list: [(id: String, version: String?)]) {
        var stopped: [String] = []
        lock.lock()
        for r in list {
            guard var a = items[r.id] else { continue }
            if r.version == nil || r.version == "*" || r.version == a.version {
                a.revoked = true
                a.on = false
                items[r.id] = a
                stopped.append(r.id)
            }
        }
        saveLocked()
        lock.unlock()
        stopped.forEach(onStop)
    }

    // MARK: strikes (section 6)

    /// Counts a call's code; 3 strikes within 24 h pause the add-on. True if it paused now.
    @discardableResult
    func strike(_ id: String, code: String?) -> Bool {
        guard let code, AddonLimits.strikes.contains(code) else { return false }
        let now = clock()
        lock.lock()
        defer { lock.unlock() }
        guard var a = items[id] else { return false }
        a.strikes = a.strikes.filter { now - $0 < AddonRegistry.strikeWindowMs } + [now]
        var paused = false
        if a.strikes.count >= AddonRegistry.strikesToPause, !a.paused {
            a.paused = true
            paused = true
        }
        items[id] = a
        saveLocked()
        return paused
    }

    // MARK: safe start (section 6)

    /// Written before each call into the engine.
    func beginCall(_ id: String) {
        try? Data(id.utf8).write(to: markerURL, options: .atomic)
    }

    /// Cleared after it.
    func endCall() {
        try? FileManager.default.removeItem(at: markerURL)
    }

    /// At app start: a marker left behind means a call never ended. Add-ons
    /// start off and the add-on it names is paused. Returns that id.
    @discardableResult
    func startUp() -> String? {
        guard let d = try? Data(contentsOf: markerURL) else { return nil }
        let id = String(decoding: d, as: UTF8.self)
        lock.lock()
        allOn = false
        items[id]?.paused = true
        saveLocked()
        lock.unlock()
        endCall()
        return id
    }

    // MARK: activity (section 9) and storage

    func log(_ id: String, _ activity: [AddonActivity]) {
        guard !activity.isEmpty else { return }
        let now = clock()
        lock.lock()
        let keep = now - AddonRegistry.activityDays * 86_400_000
        logs[id] = (logs[id] ?? []).filter { $0.at >= keep } + activity.map { AddonLogged(at: now, activity: $0) }
        saveLocked()
        lock.unlock()
    }

    func activity(_ id: String) -> [AddonLogged] {
        let keep = clock() - AddonRegistry.activityDays * 86_400_000
        lock.lock()
        defer { lock.unlock() }
        return (logs[id] ?? []).filter { $0.at >= keep }
    }

    func storage(_ id: String) -> AddonStorage {
        lock.lock()
        defer { lock.unlock() }
        if let s = stores[id] { return s }
        var values: [String: String] = [:]
        if let d = try? Data(contentsOf: storageURL(id)), case let .object(pairs)? = try? AddonJSON.parse(data: d) {
            for (k, v) in pairs { values[k] = v.string }
        }
        let url = storageURL(id)
        let s = AddonStorage(values) { all in
            let json = AddonJSON.object(all.sorted { AddonText.less($0.key, $1.key) }.map { ($0.key, .string($0.value)) })
            try? Data(json.canonical.utf8).write(to: url, options: .atomic)
        }
        stores[id] = s
        return s
    }

    // MARK: the file

    private func load() {
        guard let d = try? Data(contentsOf: stateURL), let j = try? AddonJSON.parse(data: d) else { return }
        allOn = j["addonsOn"]?.bool ?? true
        lastCheck = Int64(j["lastCheck"]?.number ?? 0)
        for a in j["addons"]?.array ?? [] { if let i = AddonInstalled(a) { items[i.id] = i } }
        if case let .object(pairs)? = j["activity"] {
            for (id, list) in pairs {
                logs[id] = (list.array ?? []).map { e in
                    AddonLogged(at: Int64(e["at"]?.number ?? 0),
                                activity: AddonActivity(host: e["host"]?.string ?? "", path: e["path"]?.string ?? "",
                                                        status: e["status"]?.integer ?? 0, bytes: e["bytes"]?.integer ?? 0,
                                                        ms: e["ms"]?.integer ?? 0))
                }
            }
        }
    }

    private func saveLocked() {
        let activity = AddonJSON.object(logs.keys.sorted().map { id in
            (id, AddonJSON.array((logs[id] ?? []).map { l in
                .object([("at", .number(Double(l.at))), ("bytes", .number(Double(l.activity.bytes))),
                         ("host", .string(l.activity.host)), ("ms", .number(Double(l.activity.ms))),
                         ("path", .string(l.activity.path)), ("status", .number(Double(l.activity.status)))])
            }))
        })
        let j = AddonJSON.object([
            ("activity", activity), ("addons", .array(items.keys.sorted().compactMap { items[$0]?.json })),
            ("addonsOn", .bool(allOn)), ("lastCheck", .number(Double(lastCheck))),
        ])
        try? Data(j.canonical.utf8).write(to: stateURL, options: .atomic)
    }
}

// MARK: - the runtimes alive at once (section 6)

final class AddonPool {
    static let maxAlive = 3
    static let idleSeconds: TimeInterval = 30
    static let minuteBudgetMs = 3_000.0

    private let lock = NSLock()
    private var runners: [String: AddonRunner] = [:]
    private var used: [String: Date] = [:]
    private var spent: [String: [(at: Date, ms: Double)]] = [:]

    var alive: Int { lock.lock(); defer { lock.unlock() }; return runners.count }

    /// The add-on's runner, made if need be. With 3 alive, the one used
    /// longest ago that is idle is freed first; nil if all 3 are busy.
    func runner(_ id: String, files: AddonFiles, now: Date = Date()) -> AddonRunner? {
        lock.lock()
        defer { lock.unlock() }
        if let r = runners[id] { used[id] = now; return r }
        if runners.count >= AddonPool.maxAlive {
            let idle = runners.filter { $0.value.idleSince != nil }.min { (used[$0.key] ?? .distantPast) < (used[$1.key] ?? .distantPast) }
            guard let (oldId, old) = idle else { return nil }
            old.close()
            runners[oldId] = nil
            used[oldId] = nil
        }
        let r = AddonRunner(id: id, files: files)
        runners[id] = r
        used[id] = now
        return r
    }

    /// Frees each runtime idle for 30 s, with its thread.
    func sweep(now: Date = Date()) {
        lock.lock()
        let old = runners.filter { e in e.value.idleSince.map { now.timeIntervalSince($0) >= AddonPool.idleSeconds } ?? false }
        for (id, r) in old { r.close(); runners[id] = nil; used[id] = nil }
        lock.unlock()
    }

    /// Stops the add-on's running call (a revoke, off, the kill switch).
    func stop(_ id: String) {
        lock.lock()
        let r = runners[id]
        lock.unlock()
        r?.stop()
    }

    func close(_ id: String) {
        lock.lock()
        let r = runners.removeValue(forKey: id)
        used[id] = nil
        lock.unlock()
        r?.close()
    }

    func closeAll() {
        lock.lock()
        let all = runners
        runners = [:]
        used = [:]
        lock.unlock()
        all.values.forEach { $0.close() }
    }

    /// At most 3 s of running time per add-on per minute.
    func withinBudget(_ id: String, now: Date = Date()) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let recent = (spent[id] ?? []).filter { now.timeIntervalSince($0.at) < 60 }
        spent[id] = recent
        return recent.reduce(0) { $0 + $1.ms } < AddonPool.minuteBudgetMs
    }

    func record(_ id: String, ms: Double, now: Date = Date()) {
        lock.lock()
        spent[id, default: []].append((now, ms))
        lock.unlock()
    }
}
