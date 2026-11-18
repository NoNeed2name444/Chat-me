import Foundation

// MARK: - Add-ons (S07b): what an add-on may see and ask for
//
// The snapshot a call reads (S07, section 6), the permission table (section
// 4), and the writes a call hands back. Ids are the store's, as lowercase
// UUID text on both platforms. Foundation only: the Linux suites build it.
// addons/kit/harness/standin.js is the reference these rules follow.

/// One idea as the snapshot holds it. `x` and `y` are its board spot, which
/// the add-on never sees; captures are placed from them.
struct AddonIdea: Equatable {
    var id: String
    var title: String
    var text: String
    var kind: String
    var folder: String?
    var tags: [String]
    var links: [String]
    var createdAt: Int64
    var updatedAt: Int64
    var x: Double = 0
    var y: Double = 0
}

struct AddonFolder: Equatable {
    var id: String
    var name: String
    var parent: String?
    var words: [String]
}

/// An immutable copy of the ideas and folders, taken on the main thread when
/// a call is dispatched (an O(1) copy of the store's arrays, mapped on the
/// add-on's thread).
struct AddonSnapshot {
    var ideas: [AddonIdea]
    var folders: [AddonFolder]

    /// The sample ideas' JSON (addons/conformance/store.json and the kit's).
    static func read(_ json: AddonJSON) -> AddonSnapshot {
        let folders = (json["folders"]?.array ?? []).map { f in
            AddonFolder(id: f["id"]?.string ?? "", name: f["name"]?.string ?? "", parent: f["parent"]?.string,
                        words: f["words"]?.strings ?? [])
        }
        let ideas = (json["ideas"]?.array ?? []).map(idea)
        return AddonSnapshot(ideas: ideas, folders: folders)
    }

    private static func idea(_ i: AddonJSON) -> AddonIdea {
        let id: String = i["id"]?.string ?? ""
        let title: String = i["title"]?.string ?? ""
        let text: String = i["text"]?.string ?? ""
        let kind: String = i["kind"]?.string ?? "idea"
        let tags: [String] = i["tags"]?.strings ?? []
        let links: [String] = i["links"]?.strings ?? []
        let created: Int64 = AddonText.parseISO(i["createdAt"]?.string ?? "") ?? 0
        let updated: Int64 = AddonText.parseISO(i["updatedAt"]?.string ?? "") ?? 0
        return AddonIdea(id: id, title: title, text: text, kind: kind, folder: i["folder"]?.string, tags: tags,
                         links: links, createdAt: created, updatedAt: updated)
    }
}

/// One write, kept on the add-on's thread until the call returns.
enum AddonOp: Equatable {
    case capture(id: String, title: String, body: String, x: Double, y: Double)
    case tag(id: String, tags: [String])
    case untag(id: String, tags: [String])
    case link(String, String)
    case unlink(String, String)
    case file(id: String, folder: String?)
}

/// What applying a call's writes did: ideas added, ideas changed, and the ids
/// it skipped because they had gone since the snapshot.
struct AddonApplied: Equatable {
    var added: [String] = []
    var changed: [String] = []
    var skipped: [String] = []

    var json: AddonJSON {
        .object([("added", AddonJSON.strings(added)), ("changed", AddonJSON.strings(changed)),
                 ("skipped", AddonJSON.strings(skipped))])
    }
}

/// One request in the add-on's activity log: the host and the path, never
/// the query and never a header.
struct AddonActivity: Equatable {
    var host: String
    var path: String
    var status: Int
    var bytes: Int
    var ms: Int

    /// As the conformance cases print it (the time left out: it varies).
    var line: String {
        "activity " + AddonJSON.object([("bytes", .number(Double(bytes))), ("host", .string(host)),
                                        ("path", .string(path)), ("status", .number(Double(status)))]).canonical
    }
}

// MARK: - permissions (addons/conformance/permissions.json)

enum AddonPermissions {
    static let folderGrant = "ideas.read.folder:"
    static let reads = ["ideas.list", "ideas.get", "ideas.search", "folders.list"]
    static let writes = ["ideas.capture", "ideas.tag", "ideas.untag", "ideas.link", "ideas.unlink", "ideas.file"]
    static let calls = reads + writes + ["commands.on", "cards.on", "settings.get", "settings.on",
                                         "storage.get", "storage.set", "storage.remove", "net.fetch", "console.log"]
    /// While an add-on loads, before its first call, only these answer.
    static let loadCalls = ["commands.on", "cards.on", "settings.on", "console.log"]
    /// The permissions a manifest may ask for, besides net:<host>.
    static let known = ["ideas.read", "ideas.read.folder", "ideas.write", "commands", "cards", "board.palette"]

    static func canRead(_ grants: [String]) -> Bool {
        grants.contains("ideas.read") || grants.contains { $0.hasPrefix(folderGrant) }
    }

    /// Whether a call may run with these grants (`host` for net.fetch).
    static func allows(_ grants: [String], call: String, host: String?, netOn: Bool) -> Bool {
        if reads.contains(call) { return canRead(grants) }
        if writes.contains(call) { return grants.contains("ideas.write") }
        if call == "commands.on" { return grants.contains("commands") }
        if call == "cards.on" { return grants.contains("cards") }
        if call == "net.fetch" { return netOn && host.map { grants.contains("net:" + $0) } == true }
        if call.hasPrefix("ai.") { return false }
        return calls.contains(call)
    }

    /// sorta.has(name): a call or a permission.
    static func has(_ grants: [String], name: String, netOn: Bool) -> Bool {
        if calls.contains(name) {
            if name == "net.fetch" { return netOn && grants.contains { $0.hasPrefix("net:") } }
            return allows(grants, call: name, host: nil, netOn: netOn)
        }
        if name == "net" { return netOn && grants.contains { $0.hasPrefix("net:") } }
        if name.hasPrefix("net:") { return netOn && grants.contains(name) }
        if name == "ideas.read.folder" { return grants.contains { $0.hasPrefix(folderGrant) } }
        if ["ideas.read", "ideas.write", "commands", "cards", "board.palette"].contains(name) { return grants.contains(name) }
        return false
    }
}

// MARK: - the snapshot as one add-on may read it

struct AddonView {
    let folders: Set<String>
    let ideas: [AddonIdea]
    let ids: Set<String>
    let links: [String: Set<String>]

    init(_ snap: AddonSnapshot, grants: [String]) {
        let all = grants.contains("ideas.read")
        var visible = Set<String>()
        if all {
            for f in snap.folders { visible.insert(f.id) }
        } else {
            let known = Set(snap.folders.map(\.id))
            for g in grants where g.hasPrefix(AddonPermissions.folderGrant) {
                let root = String(g.dropFirst(AddonPermissions.folderGrant.count))
                if known.contains(root) { visible.insert(root) }
            }
            var grew = true
            while grew {
                grew = false
                for f in snap.folders where !visible.contains(f.id) {
                    if let p = f.parent, visible.contains(p) { visible.insert(f.id); grew = true }
                }
            }
        }
        let shown = snap.ideas.filter { all || ($0.folder.map { visible.contains($0) } ?? false) }
        let ids = Set(shown.map(\.id))
        var links: [String: Set<String>] = [:]
        for i in shown { links[i.id] = [] }
        for i in shown {
            for l in i.links where ids.contains(l) && l != i.id {
                links[i.id]?.insert(l)
                links[l]?.insert(i.id)
            }
        }
        folders = visible
        ideas = shown
        self.ids = ids
        self.links = links
    }

    func json(_ i: AddonIdea) -> AddonJSON {
        let folder: AddonJSON = i.folder.flatMap { folders.contains($0) ? AddonJSON.string($0) : nil } ?? .null
        return .object([
            ("id", .string(i.id)), ("title", .string(i.title)), ("text", .string(i.text)), ("kind", .string(i.kind)),
            ("folder", folder), ("tags", AddonJSON.strings(i.tags)),
            ("links", AddonJSON.strings((links[i.id] ?? []).sorted(by: AddonText.less))),
            ("createdAt", .string(AddonText.iso(i.createdAt))), ("updatedAt", .string(AddonText.iso(i.updatedAt))),
        ])
    }
}
