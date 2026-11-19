import Foundation

// MARK: - Add-ons (S07b): one call's side of the API
//
// Everything a running add-on asks of Sorta comes here as a name and its
// arguments in JSON, on the add-on's own thread, never the main thread.
// Reads answer from the snapshot taken when the call was dispatched; writes
// are checked against it and kept as plain values until the call returns,
// when the main thread applies them in one hop (NoteStore.applyAddonBatch).
// Section 5's API v1, with addons/kit/harness/standin.js as the reference:
// the conformance cases check both give the same bytes. Foundation only.

/// An add-on's own small storage: key to the JSON text it stored.
final class AddonStorage {
    /// What the add-on sees: what is kept, with this call's own changes.
    private(set) var values: [String: String]
    private var kept: [String: String]
    private var changed = false
    private let saved: ([String: String]) -> Void

    init(_ values: [String: String] = [:], saved: @escaping ([String: String]) -> Void = { _ in }) {
        self.values = values
        self.kept = values
        self.saved = saved
    }

    func set(_ key: String, _ text: String) { values[key] = text; changed = true }
    func remove(_ key: String) { values[key] = nil; changed = true }

    /// The call ended: its changes are kept and saved if it ended well, and
    /// dropped if it was stopped, refused or failed.
    func end(ok: Bool) {
        guard changed else { return }
        changed = false
        if ok {
            kept = values
            saved(kept)
        } else {
            values = kept
        }
    }
}

/// How a call ended, for the app: its result, and the writes to apply when
/// it succeeded.
struct AddonOutcome {
    var envelope: String
    var ok: Bool
    var code: String?
    var batch: [AddonOp]
    var log: [String]
    var activity: [AddonActivity]
}

final class AddonCall {
    struct Setup {
        var snapshot: AddonSnapshot
        var grants: [String]
        /// The command ids the manifest declares.
        var commands: [String]
        /// SORTA_ADDON_NET, and the switch for net in the add-on's settings.
        var netOn: Bool
        /// The add-on's settings values, as canonical JSON text.
        var settings: String = "{}"
        var storage: AddonStorage = AddonStorage()
        var network: AddonNetwork? = nil
        /// Ask each time: called on the add-on's thread, which waits for the
        /// person's answer. Nil when net is granted outright.
        var consent: ((String) -> Bool)? = nil
        var now: Int64
        var makeId: () -> String = { UUID().uuidString.lowercased() }
        /// Where the n-th idea lands on the board, among the others.
        var place: (Int, [(Double, Double)]) -> (Double, Double) = { _, _ in (0, 0) }
    }

    static let maxWrites = 100
    static let maxCaptures = 20
    static let storageLimit = 262_144
    static let netLimit = 1_048_576
    static let netMs = 10_000
    static let redirects = 5
    static let logLines = 200

    let setup: Setup
    let view: AddonView
    /// "load" while the add-on's top level runs, then the call's kind.
    var phase = "load"
    private(set) var batch: [AddonOp] = []
    private(set) var log: [String] = []
    private(set) var activity: [AddonActivity] = []
    private(set) var poisoned: String?
    private var writes = 0
    private var captures = 0
    private var captured = Set<String>()
    private var spots: [(Double, Double)]

    init(_ setup: Setup) {
        self.setup = setup
        view = AddonView(setup.snapshot, grants: setup.grants)
        spots = setup.snapshot.ideas.map { ($0.x, $0.y) }
    }

    private struct Refusal: Error {
        let code: String
        /// What the error says besides its code (net: the refused host).
        var message: String? = nil
    }

    // MARK: the entry point

    /// One API call: JSON text back, "" for undefined, or "!" and a code
    /// (then a space and a message, when there is one).
    func handle(_ name: String, _ argsText: String) -> String {
        do {
            let args = try AddonJSON.parse(argsText).array ?? []
            if name == "has" {
                let ok = args.first?.string.map { AddonPermissions.has(setup.grants, name: $0, netOn: setup.netOn) } ?? false
                return ok ? "true" : "false"
            }
            guard AddonPermissions.calls.contains(name) else { throw Refusal(code: "api.unknown") }
            if phase == "load", !AddonPermissions.loadCalls.contains(name) { throw Refusal(code: "api.load") }
            // net.fetch checks its grant itself, after the URL
            if name != "net.fetch", !AddonPermissions.allows(setup.grants, call: name, host: nil, netOn: setup.netOn) {
                throw Refusal(code: "permission")
            }
            // only a command's writes are applied; a card view or settings handler only reads
            if AddonPermissions.writes.contains(name), phase != "command" { throw Refusal(code: "permission") }
            return try answer(name, args)
        } catch let r as Refusal {
            return "!" + r.code + (r.message.map { " " + $0 } ?? "")
        } catch {
            return "!api.args"
        }
    }

    private func arg(_ args: [AddonJSON], _ i: Int) -> AddonJSON { i < args.count ? args[i] : .null }

    private func answer(_ name: String, _ args: [AddonJSON]) throws -> String {
        switch name {
        case "ideas.list": return try list(arg(args, 0)).canonical
        case "ideas.get":
            guard let id = arg(args, 0).string else { throw Refusal(code: "api.args") }
            return view.ideas.first(where: { $0.id == id }).map { view.json($0).canonical } ?? "null"
        case "ideas.search": return try search(arg(args, 0), arg(args, 1)).canonical
        case "folders.list": return folders().canonical
        case "ideas.capture": return try capture(arg(args, 0)).canonical
        case "ideas.tag", "ideas.untag":
            let id = try target(arg(args, 0))
            let tags = try tagList(arg(args, 1))
            try write(name == "ideas.tag" ? .tag(id: id, tags: tags) : .untag(id: id, tags: tags))
            return ""
        case "ideas.link", "ideas.unlink":
            let a = try target(arg(args, 0)), b = try target(arg(args, 1))
            if a == b { throw Refusal(code: "api.args") }
            try write(name == "ideas.link" ? .link(a, b) : .unlink(a, b))
            return ""
        case "ideas.file":
            let id = try target(arg(args, 0))
            let folder = arg(args, 1)
            if !folder.isNull, folder.string == nil { throw Refusal(code: "api.args") }
            if let f = folder.string, !view.folders.contains(f) { throw Refusal(code: "api.notFound") }
            try write(.file(id: id, folder: folder.string))
            return ""
        case "commands.on":
            guard let id = arg(args, 0).string, setup.commands.contains(id) else { throw Refusal(code: "api.args") }
            return ""
        case "cards.on", "settings.on": return ""
        case "settings.get": return setup.settings
        case "storage.get":
            let key = try storageKey(arg(args, 0))
            return setup.storage.values[key] ?? ""
        case "storage.set":
            let key = try storageKey(arg(args, 0))
            guard let text = arg(args, 1).string else { throw Refusal(code: "api.args") }
            var total = 0
            for (k, v) in setup.storage.values where k != key { total += k.utf8.count + v.utf8.count }
            if total + key.utf8.count + text.utf8.count > AddonCall.storageLimit { throw Refusal(code: "limit.storage") }
            setup.storage.set(key, text)
            return ""
        case "storage.remove":
            setup.storage.remove(try storageKey(arg(args, 0)))
            return ""
        case "console.log":
            log.append(arg(args, 0).string ?? "")
            if log.count > AddonCall.logLines { log.removeFirst() }
            return ""
        case "net.fetch": return try fetch(arg(args, 0), arg(args, 1)).canonical
        default: throw Refusal(code: "api.unknown")
        }
    }

    // MARK: reads

    private func list(_ o: AddonJSON) throws -> AddonJSON {
        guard o.isObject else { throw Refusal(code: "api.args") }
        let folder = o["folder"]
        if let f = folder, !f.isNull, f.string == nil { throw Refusal(code: "api.args") }
        let kind = o["kind"]
        if let k = kind, k.string == nil { throw Refusal(code: "api.args") }
        let limit = try whole(o["limit"], default: 50, in: 1...200)
        var from = 0
        if let c = o["cursor"], !c.isNull {
            guard let text = c.string, (1...9).contains(text.count), text.utf8.allSatisfy({ $0 >= 0x30 && $0 <= 0x39 }),
                  let n = Int(text) else { throw Refusal(code: "api.args") }
            from = n
        }
        let found = view.ideas.filter { i in
            (folder == nil || i.folder == folder?.string) && (kind == nil || i.kind == kind?.string)
        }.sorted { $0.createdAt != $1.createdAt ? $0.createdAt < $1.createdAt : AddonText.less($0.id, $1.id) }
        if from > found.count { throw Refusal(code: "api.args") }
        let page = found[from..<min(found.count, from + limit)]
        let next: AddonJSON = from + limit < found.count ? .string(String(from + limit)) : .null
        return .object([("ideas", .array(page.map(view.json))), ("cursor", next)])
    }

    private func search(_ text: AddonJSON, _ o: AddonJSON) throws -> AddonJSON {
        guard let t = text.string, o.isObject else { throw Refusal(code: "api.args") }
        let limit = try whole(o["limit"], default: 20, in: 1...50)
        let want = AddonText.words(t.lowercased()).map { Array($0.utf16) }
        let found = view.ideas.filter { i in
            let hay = Array(([i.title, i.text] + i.tags).joined(separator: " ").lowercased().utf16)
            return want.allSatisfy { AddonText.contains(hay, $0) }
        }.sorted { $0.updatedAt != $1.updatedAt ? $0.updatedAt > $1.updatedAt : AddonText.less($0.id, $1.id) }
        return .array(found.prefix(limit).map(view.json))
    }

    private func folders() -> AddonJSON {
        let shown = setup.snapshot.folders.filter { view.folders.contains($0.id) }
            .sorted { $0.name != $1.name ? AddonText.less($0.name, $1.name) : AddonText.less($0.id, $1.id) }
        return .array(shown.map { f in
            let parent: AddonJSON = f.parent.flatMap { view.folders.contains($0) ? AddonJSON.string($0) : nil } ?? .null
            return .object([("id", .string(f.id)), ("name", .string(f.name)), ("parent", parent),
                            ("words", AddonJSON.strings(f.words))])
        })
    }

    private func whole(_ v: AddonJSON?, default d: Int, in range: ClosedRange<Int>) throws -> Int {
        guard let v else { return d }
        guard let n = v.integer, range.contains(n) else { throw Refusal(code: "api.args") }
        return n
    }

    // MARK: writes

    private func target(_ v: AddonJSON) throws -> String {
        guard let id = v.string else { throw Refusal(code: "api.args") }
        guard view.ids.contains(id) || captured.contains(id) else { throw Refusal(code: "api.notFound") }
        return id
    }

    private func tagList(_ v: AddonJSON) throws -> [String] {
        guard let items = v.array, !items.isEmpty else { throw Refusal(code: "api.args") }
        return try items.map { item in
            guard let t = item.string, !AddonText.trim(t).isEmpty else { throw Refusal(code: "api.args") }
            return AddonText.trim(t)
        }
    }

    private func write(_ op: AddonOp) throws {
        if let p = poisoned { throw Refusal(code: p) }
        if case .capture = op {
            if captures >= AddonCall.maxCaptures { poisoned = "limit.captures"; throw Refusal(code: "limit.captures") }
            captures += 1
        } else {
            if writes >= AddonCall.maxWrites { poisoned = "limit.writes"; throw Refusal(code: "limit.writes") }
            writes += 1
        }
        batch.append(op)
    }

    private func capture(_ o: AddonJSON) throws -> AddonJSON {
        guard o.isObject, let text = o["text"]?.string, !AddonText.trim(text).isEmpty else { throw Refusal(code: "api.args") }
        if let t = o["title"], !t.isNull, t.string == nil { throw Refusal(code: "api.args") }
        let title: String, body: String
        if let t = o["title"]?.string, !AddonText.trim(t).isEmpty {
            title = AddonText.trim(t)
            body = AddonText.trim(text)
        } else {
            // the first line feed, unit by unit (a "\r\n" is one Character in Swift)
            let whole = Array(AddonText.trim(text).utf16)
            if let nl = whole.firstIndex(of: 0x0A) {
                title = AddonText.trim(String(decoding: whole[..<nl], as: UTF16.self))
                body = AddonText.trim(String(decoding: whole[(nl + 1)...], as: UTF16.self))
            } else {
                title = String(decoding: whole, as: UTF16.self)
                body = ""
            }
        }
        if let p = poisoned { throw Refusal(code: p) }
        if captures >= AddonCall.maxCaptures { poisoned = "limit.captures"; throw Refusal(code: "limit.captures") }
        let id = setup.makeId()
        let spot = setup.place(spots.count, spots)
        spots.append(spot)
        try write(.capture(id: id, title: title, body: body, x: spot.0, y: spot.1))
        captured.insert(id)
        return .object([("id", .string(id))])
    }

    private func storageKey(_ v: AddonJSON) throws -> String {
        guard let key = v.string, (1...100).contains(key.utf16.count) else { throw Refusal(code: "api.args") }
        return key
    }

    // MARK: net

    private static let methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"]

    private func fetch(_ urlValue: AddonJSON, _ o: AddonJSON) throws -> AddonJSON {
        guard setup.netOn else { throw Refusal(code: "net.off") }
        guard let text = urlValue.string, o.isObject, var url = AddonURL(text) else { throw Refusal(code: "api.args") }
        guard url.scheme == "https" else { throw Refusal(code: "net.https") }
        guard setup.grants.contains("net:" + url.host) else { throw Refusal(code: "permission", message: "net:" + url.host) }
        var method = "GET"
        if let m = o["method"] {
            guard let s = m.string, AddonCall.methods.contains(s.uppercased()) else { throw Refusal(code: "api.args") }
            method = s.uppercased()
        }
        var headers: [String: String] = [:]
        if let h = o["headers"] {
            guard case let .object(pairs) = h else { throw Refusal(code: "api.args") }
            for (k, v) in pairs {
                let name = k.lowercased()
                guard let value = v.string, name != "host" else { throw Refusal(code: "api.args") }
                // a cookie is never sent
                if name != "cookie" { headers[name] = value }
            }
        }
        var body = ""
        if let b = o["body"], !b.isNull {
            guard let s = b.string else { throw Refusal(code: "api.args") }
            body = s
        }
        if body.utf8.count > AddonCall.netLimit { throw Refusal(code: "net.size") }
        // ask each time: this thread waits for the person's answer, for each
        // host the request reaches (a redirect to another host is asked about
        // before anything is sent there)
        var asked = Set<String>()
        func consent(_ host: String) throws {
            if let ask = setup.consent, asked.insert(host).inserted, !ask(host) { throw Refusal(code: "net.declined") }
        }
        try consent(url.host)
        guard let network = setup.network else { throw Refusal(code: "net.failed") }
        var spent = 0
        var hop = 0
        while true {
            let started = DispatchTime.now().uptimeNanoseconds
            let reply = network.send(method: method, url: url, headers: headers, body: body,
                                     timeLeftMs: AddonCall.netMs - spent)
            let took = Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000)
            spent += reply.delayMs
            if let failure = reply.failure, failure != "net.size" { throw Refusal(code: failure) }
            if spent > AddonCall.netMs { throw Refusal(code: "net.time") }
            let size = reply.body.utf8.count
            activity.append(AddonActivity(host: url.host, path: url.path, status: reply.status,
                                          bytes: body.utf8.count + size, ms: max(took, reply.delayMs)))
            if reply.failure == "net.size" || size > AddonCall.netLimit { throw Refusal(code: "net.size") }
            if [301, 302, 303, 307, 308].contains(reply.status), let location = reply.headers["location"] {
                if hop >= AddonCall.redirects { throw Refusal(code: "net.redirect") }
                let next = location.hasPrefix("/") ? AddonURL("https://" + url.host + location) : AddonURL(location)
                guard let next, next.scheme == "https", setup.grants.contains("net:" + next.host) else {
                    throw Refusal(code: "net.redirect")
                }
                if reply.status == 303 { method = "GET"; body = "" }
                url = next
                try consent(url.host)
                hop += 1
                continue
            }
            // nor is one passed back
            let shown = reply.headers.filter { !["set-cookie", "cookie"].contains($0.key.lowercased()) }
                .map { ($0.key, AddonJSON.string($0.value)) }
            return .object([("status", .number(Double(reply.status))), ("headers", .object(shown)),
                            ("body", .string(reply.body))])
        }
    }

    /// A card view's input as the add-on may read it: only ideas inside its
    /// read grant. nil when there is none to show, and then no call is made.
    func cardInput(_ ids: [String], batch: Bool) -> String? {
        let shown = ids.compactMap { id in view.ids.contains(id) ? view.ideas.first { $0.id == id } : nil }
        guard let first = shown.first else { return nil }
        return batch ? AddonJSON.array(shown.map(view.json)).canonical : view.json(first).canonical
    }

    // MARK: the end of the call

    /// The call's result as the app takes it: a call that went past its
    /// writes or captures fails with that code, and only an ok call's
    /// writes are applied.
    func outcome(_ envelope: String) -> AddonOutcome {
        var env = envelope
        if let p = poisoned { env = "{\"code\":\"\(p)\",\"message\":\"\(p)\",\"ok\":false}" }
        let parsed = try? AddonJSON.parse(env)
        let ok = parsed?["ok"]?.bool == true
        return AddonOutcome(envelope: env, ok: ok, code: ok ? nil : (parsed?["code"]?.string ?? "js.error"),
                            batch: ok ? batch : [], log: log, activity: activity)
    }
}

// MARK: - URLs, as the add-on host reads them

struct AddonURL: Equatable {
    var scheme: String
    var host: String
    var path: String
    var query: String

    /// scheme://host[:port]/path?query#fragment; no user info, no empty host.
    init?(_ s: String) {
        guard let sep = s.range(of: "://") else { return nil }
        let scheme = String(s[..<sep.lowerBound])
        guard let first = scheme.unicodeScalars.first, first.isASCII, CharacterSet.letters.contains(first),
              scheme.unicodeScalars.allSatisfy({ $0.isASCII && (CharacterSet.alphanumerics.contains($0) || "+.-".unicodeScalars.contains($0)) })
        else { return nil }
        var rest = Substring(s[sep.upperBound...])
        if let hash = rest.firstIndex(of: "#") { rest = rest[..<hash] }
        let authorityEnd = rest.firstIndex(where: { $0 == "/" || $0 == "?" }) ?? rest.endIndex
        let authority = String(rest[..<authorityEnd])
        guard !authority.isEmpty, !authority.contains("@") else { return nil }
        let after = rest[authorityEnd...]
        let queryStart = after.firstIndex(of: "?") ?? after.endIndex
        var host = authority.lowercased()
        if host.hasSuffix(":443") { host.removeLast(4) }
        self.scheme = scheme.lowercased()
        self.host = host
        let path = String(after[..<queryStart])
        self.path = path.isEmpty ? "/" : path
        query = String(after[queryStart...])
    }

    /// What is sent: the fragment is not.
    var text: String { scheme + "://" + host + path + query }
}

/// One response, or why there was none: net.time, net.size or net.failed.
struct AddonNetReply {
    var status: Int = 0
    var headers: [String: String] = [:]
    var body: String = ""
    /// How long it took; the stand-in network's own delay.
    var delayMs: Int = 0
    var failure: String? = nil
}

/// The network an add-on's net.fetch goes out on: one request at a time,
/// redirects not followed (the host follows them itself, within the grants).
protocol AddonNetwork: AnyObject {
    func send(method: String, url: AddonURL, headers: [String: String], body: String, timeLeftMs: Int) -> AddonNetReply
}

/// The recording stand-in network (addons/fixtures/net/routes.json): what
/// the tests and the harness send to, never the internet.
final class AddonRoutesNetwork: AddonNetwork {
    struct Sent: Equatable {
        var method: String
        var url: String
        var headers: [String: String]
        var body: String
    }

    private let routes: [AddonJSON]
    private(set) var sent: [Sent] = []

    init(routes json: AddonJSON) { routes = json["routes"]?.array ?? [] }

    func send(method: String, url: AddonURL, headers: [String: String], body: String, timeLeftMs: Int) -> AddonNetReply {
        sent.append(Sent(method: method, url: url.text, headers: headers, body: body))
        let key = "https://" + url.host + url.path
        guard let r = routes.first(where: { $0["method"]?.string == method && $0["url"]?.string == key }) else {
            return AddonNetReply(status: 404)
        }
        var text = r["body"]?.string ?? ""
        if let rep = r["bodyRepeat"], let unit = rep["text"]?.string, let n = rep["count"]?.integer {
            text = String(repeating: unit, count: n)
        }
        if r["echo"]?.bool == true { text = body }
        var hs: [String: String] = [:]
        if case let .object(pairs)? = r["headers"] { for (k, v) in pairs { hs[k] = v.string ?? "" } }
        return AddonNetReply(status: r["status"]?.integer ?? 200, headers: hs, body: text, delayMs: r["delayMs"]?.integer ?? 0)
    }
}
