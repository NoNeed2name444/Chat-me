import Foundation

// MARK: - Add-ons (S07b): the package checker, and the review before install
//
// The rules of addons/spec/rules.md, in its order, with the codes of
// addons/spec/reasons.json: the same code for the same package as the kit's
// check.py and the Kotlin checker. Each check runs over every entry or
// field before the next starts, and the first failure decides the code. The
// archive is checked from its central directory before anything is
// unpacked. Foundation only.

struct AddonRefusal: Error, Equatable {
    var code: String
}

/// An add-on's files, checked: the parsed manifest and the files by path.
struct AddonPackage {
    var manifest: AddonJSON
    var files: AddonFiles
    var unpacked: Int

    var id: String { manifest["id"]?.string ?? "" }
    var version: String { manifest["version"]?.string ?? "" }
    var permissions: [String] { manifest["permissions"]?.strings ?? [] }
    var hosts: [String] { manifest["hosts"]?.strings ?? [] }
}

enum AddonPackageCheck {
    static let maxZipped = 524_288
    static let maxUnpacked = 1_048_576
    static let maxEntries = 200
    static let maxMain = 262_144
    static let maxRatio = 100
    static let maxNameBytes = 255
    static let maxFolders = 2
    static let maxJSONDepth = 32
    static let maxJSONNumber = 32
    /// The app's own API major version (install.api).
    static let appAPI = 1

    static let rootFiles: Set<String> = ["manifest.json", "README.md", "LICENSE", "LICENSE.txt", "LICENSE.md", "CHANGELOG.md"]
    static let fields: Set<String> = ["format", "formatVersion", "id", "name", "version", "author", "license", "minSorta",
                                      "api", "main", "permissions", "hosts", "contributes"]
    static let manifestPermissions: Set<String> = ["ideas.read", "ideas.read.folder", "ideas.write", "commands", "cards",
                                                   "board.palette", "net", "ai"]
    static let contributes: Set<String> = ["commands", "cardView", "settings", "boardPalette"]
    static let needs = [("commands", "commands"), ("cardView", "cards"), ("boardPalette", "board.palette")]

    private static func refuse(_ code: String) -> AddonRefusal { AddonRefusal(code: code) }

    // MARK: the archive

    /// A `.sortaaddon` file's bytes, checked and unpacked.
    static func archive(_ data: Data) -> Result<AddonPackage, AddonRefusal> {
        do {
            let (names, contents) = try AddonZip.read(data)
            return .success(try package(names: names, data: contents))
        } catch let r as AddonRefusal {
            return .failure(r)
        } catch {
            return .failure(refuse("zip.unreadable"))
        }
    }

    // MARK: the files (rules.md, section 2)

    static func package(names: [String], data: [Data]) throws -> AddonPackage {
        for name in names where !allowedFile(name) { throw refuse("file.type") }
        for name in names where name.utf8.filter({ $0 == 47 }).count > maxFolders { throw refuse("file.depth") }
        var texts: [String] = []
        for bytes in data {
            guard !bytes.contains(0), let text = strictUTF8(bytes) else { throw refuse("file.text") }
            texts.append(text)
        }
        for bytes in data where hasPicture(bytes) { throw refuse("file.picture") }
        guard let mi = names.firstIndex(of: "manifest.json") else { throw refuse("manifest.missing") }
        guard let manifest = strictJSON(texts[mi]), manifest.isObject else { throw refuse("manifest.json") }
        var files: [String: Int] = [:]
        for (i, n) in names.enumerated() { files[n] = i }
        try manifestRules(manifest, files: files, data: data, texts: texts)
        let main = manifest["main"]?.string ?? ""
        return AddonPackage(manifest: manifest, files: AddonFiles(names: names, data: data, main: main),
                            unpacked: data.reduce(0) { $0 + $1.count })
    }

    static func allowedFile(_ name: String) -> Bool {
        if name.hasSuffix("/") { return false }
        let parts = name.split(separator: "/", omittingEmptySubsequences: false)
        if parts.contains(where: { $0.hasPrefix(".") }) { return false }
        if parts.count == 1, rootFiles.contains(name) { return true }
        return name.hasSuffix(".js") || name.hasSuffix(".json")
    }

    /// Strict UTF-8: overlong forms, surrogates and code points past U+10FFFF fail.
    static func strictUTF8(_ d: Data) -> String? {
        var decoder = UTF8()
        var it = d.makeIterator()
        var out = String.UnicodeScalarView()
        while true {
            switch decoder.decode(&it) {
            case let .scalarValue(s): out.append(s)
            case .emptyInput: return String(out)
            case .error: return nil
            }
        }
    }

    /// `<svg` or `data:image/`, compared after ASCII lowercasing.
    static func hasPicture(_ d: Data) -> Bool {
        let lower = d.map { $0 >= 65 && $0 <= 90 ? $0 + 32 : $0 }
        for needle in [Array("<svg".utf8), Array("data:image/".utf8)] {
            if lower.count >= needle.count {
                var i = 0
                while i + needle.count <= lower.count {
                    if lower[i] == needle[0], Array(lower[i..<(i + needle.count)]) == needle { return true }
                    i += 1
                }
            }
        }
        return false
    }

    // MARK: strict JSON

    /// RFC 8259 JSON with no byte order mark, no key twice in an object, at
    /// most 32 levels of lists and objects, and no number over 32 characters.
    static func strictJSON(_ text: String) -> AddonJSON? {
        var depth = 0, run = 0, inString = false, escaped = false
        for u in text.utf8 {
            if inString {
                if escaped { escaped = false } else if u == 92 { escaped = true } else if u == 34 { inString = false }
                continue
            }
            let numeric = (u >= 48 && u <= 57) || u == 43 || u == 45 || u == 46 || u == 69 || u == 101
            run = numeric ? run + 1 : 0
            if run > maxJSONNumber { return nil }
            if u == 34 { inString = true } else if u == 91 || u == 123 {
                depth += 1
                if depth > maxJSONDepth { return nil }
            } else if u == 93 || u == 125 { depth -= 1 }
        }
        return try? AddonJSON.parse(text, keyTwice: false)
    }

    private static func keys(_ v: AddonJSON?) -> Set<String>? {
        guard let v, case let .object(pairs) = v else { return nil }
        return Set(pairs.map(\.0))
    }

    /// A finite JSON number with no fraction, from low to high.
    static func whole(_ v: AddonJSON?, _ low: Double, _ high: Double) -> Bool {
        guard let n = v?.number, n.isFinite, n == n.rounded() else { return false }
        return n >= low && n <= high
    }

    /// A string a person reads: 1 to `longest` code points, no space at
    /// either end, no control character, and none of Unicode's format
    /// characters or line and paragraph separators (Cf, Zl, Zp).
    static func label(_ v: AddonJSON?, _ longest: Int) -> Bool {
        guard let s = v?.string else { return false }
        let scalars = Array(s.unicodeScalars)
        guard (1...longest).contains(scalars.count), scalars.first != " ", scalars.last != " " else { return false }
        return !scalars.contains { c in
            if c.value < 0x20 || (c.value >= 0x7F && c.value <= 0x9F) { return true }
            switch c.properties.generalCategory {
            case .format, .lineSeparator, .paragraphSeparator: return true
            default: return false
            }
        }
    }

    private static func matches(_ s: String, first: (UInt8) -> Bool, rest: (UInt8) -> Bool, _ length: ClosedRange<Int>) -> Bool {
        let u = Array(s.utf8)
        guard length.contains(u.count), first(u[0]) else { return false }
        return u.dropFirst().allSatisfy(rest)
    }

    private static func lower(_ c: UInt8) -> Bool { c >= 97 && c <= 122 }
    private static func digit(_ c: UInt8) -> Bool { c >= 48 && c <= 57 }
    private static func upper(_ c: UInt8) -> Bool { c >= 65 && c <= 90 }

    /// [a-z0-9][a-z0-9-]{0,31}
    static func commandId(_ v: AddonJSON?) -> Bool {
        guard let s = v?.string else { return false }
        return matches(s, first: { lower($0) || digit($0) }, rest: { lower($0) || digit($0) || $0 == 45 }, 1...32)
    }

    /// [a-z][A-Za-z0-9]{0,31}
    static func settingKey(_ v: AddonJSON?) -> Bool {
        guard let s = v?.string else { return false }
        return matches(s, first: lower, rest: { lower($0) || upper($0) || digit($0) }, 1...32)
    }

    /// #[0-9A-Fa-f]{6}
    static func colour(_ v: AddonJSON?) -> Bool {
        guard let s = v?.string else { return false }
        return matches(s, first: { $0 == 35 }, rest: { digit($0) || ($0 >= 97 && $0 <= 102) || ($0 >= 65 && $0 <= 70) }, 7...7)
    }

    // MARK: the manifest (rules.md, section 3)

    static func manifestRules(_ m: AddonJSON, files: [String: Int], data: [Data], texts: [String]) throws {
        guard m["format"]?.string == "sorta-addon" else { throw refuse("format") }
        guard whole(m["formatVersion"], 1, 1) else { throw refuse("formatVersion") }
        guard m.keys.allSatisfy(fields.contains) else { throw refuse("manifest.json") }
        guard let id = m["id"]?.string, validId(id) else { throw refuse("id") }
        if id == "com.cramdown" || id.hasPrefix("com.cramdown.") { throw refuse("id.reserved") }
        guard label(m["name"], 30) else { throw refuse("name") }
        guard let v = m["version"]?.string, semver(v) != nil else { throw refuse("version") }
        guard let ms = m["minSorta"]?.string, semver(ms) != nil else { throw refuse("minSorta") }
        guard whole(m["api"], 1, 99) else { throw refuse("api") }
        try authorRule(m["author"])
        guard let lic = m["license"]?.string, AddonLicenses.osi.contains(lic) else { throw refuse("license") }
        guard let main = m["main"]?.string, main.hasSuffix(".js"), let mainIndex = files[main] else { throw refuse("main.missing") }
        if data[mainIndex].count > maxMain { throw refuse("main.size") }
        guard let list = m["permissions"]?.array, list.allSatisfy({ $0.string.map(manifestPermissions.contains) ?? false }) else {
            throw refuse("permissions")
        }
        let perms = list.compactMap(\.string)
        if Set(perms).count != perms.count { throw refuse("permissions") }
        if perms.contains("ideas.read"), perms.contains("ideas.read.folder") { throw refuse("permissions") }
        try hostsRule(m["hosts"] ?? .array([]), wantsNet: perms.contains("net"))
        let c = m["contributes"] ?? .object([])
        if c.isObject, let commands = c["commands"] { try commandsRule(commands) }
        try contributesRule(c, perms: Set(perms), files: files, texts: texts)
    }

    /// [a-z0-9-]+(\.[a-z0-9-]+)+, 3 to 64 characters.
    static func validId(_ id: String) -> Bool {
        guard (3...64).contains(id.unicodeScalars.count),
              id.utf8.allSatisfy({ lower($0) || digit($0) || $0 == 46 || $0 == 45 }) else { return false }
        let parts = id.split(separator: ".", omittingEmptySubsequences: false)
        return parts.count >= 2 && parts.allSatisfy { !$0.isEmpty }
    }

    static func authorRule(_ author: AddonJSON?) throws {
        guard keys(author) == ["name", "url"], label(author?["name"], 60), let name = author?["name"]?.string,
              !name.contains("@"), let url = author?["url"]?.string, url.hasPrefix("https://"),
              url.unicodeScalars.count <= 200 else { throw refuse("author") }
        let rest = url.unicodeScalars.dropFirst(8)
        let host = rest.prefix { $0 != "/" && $0 != "?" && $0 != "#" }
        guard !host.isEmpty, !host.contains("@"),
              !url.unicodeScalars.contains(where: { $0.value < 0x20 || $0.properties.isWhitespace || (0x1C...0x1F).contains($0.value) })
        else { throw refuse("author") }
    }

    static func hostsRule(_ hosts: AddonJSON, wantsNet: Bool) throws {
        guard let list = hosts.array else { throw refuse("hosts") }
        if wantsNet, list.isEmpty { throw refuse("hosts") }
        if !list.isEmpty, !wantsNet { throw refuse("hosts") }
        for h in list {
            guard let host = h.string, validHost(host) else { throw refuse("hosts") }
        }
        if Set(list.compactMap(\.string)).count != list.count { throw refuse("hosts") }
    }

    /// 2 or more labels of [a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?, at most 253
    /// characters, the last label not all digits.
    static func validHost(_ h: String) -> Bool {
        let parts = h.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count >= 2, h.unicodeScalars.count <= 253 else { return false }
        for p in parts {
            let u = Array(p.utf8)
            guard (1...63).contains(u.count), u.allSatisfy({ lower($0) || digit($0) || $0 == 45 }),
                  u[0] != 45, u[u.count - 1] != 45 else { return false }
        }
        return !(parts.last?.utf8.allSatisfy(digit) ?? true)
    }

    static func commandsRule(_ commands: AddonJSON) throws {
        guard let list = commands.array, (1...5).contains(list.count) else { throw refuse("commands") }
        var ids = Set<String>()
        for c in list {
            guard keys(c) == ["id", "title"], commandId(c["id"]), label(c["title"], 30),
                  ids.insert(c["id"]?.string ?? "").inserted else { throw refuse("commands") }
        }
    }

    static func contributesRule(_ c: AddonJSON, perms: Set<String>, files: [String: Int], texts: [String]) throws {
        guard let have = keys(c), have.allSatisfy(contributes.contains) else { throw refuse("contributes") }
        for (field, permission) in needs where have.contains(field) != perms.contains(permission) { throw refuse("contributes") }
        if have.contains("cardView"), !perms.contains("ideas.read"), !perms.contains("ideas.read.folder") {
            throw refuse("cards.read")
        }
        if let view = c["cardView"] {
            guard keys(view) == ["id", "title"], commandId(view["id"]), label(view["title"], 30) else { throw refuse("contributes") }
        }
        func dataFile(_ v: AddonJSON?) throws -> AddonJSON {
            guard let name = v?.string, name.hasSuffix(".json"), let i = files[name], let j = strictJSON(texts[i]) else {
                throw refuse("contributes")
            }
            return j
        }
        if have.contains("settings") { try settingsRule(try dataFile(c["settings"])) }
        if have.contains("boardPalette") { try paletteRule(try dataFile(c["boardPalette"])) }
    }

    static func settingsRule(_ schema: AddonJSON) throws {
        let bad = refuse("contributes")
        guard keys(schema) == ["settings"], let items = schema["settings"]?.array, (1...20).contains(items.count) else { throw bad }
        var seen = Set<String>()
        let common: Set<String> = ["key", "type", "title", "default"]
        for item in items {
            guard let have = keys(item), settingKey(item["key"]), let key = item["key"]?.string else { throw bad }
            let kind = item["type"]?.string
            let extra: String?
            switch kind {
            case "toggle": extra = nil
            case "text": extra = "maxLength"
            case "choice": extra = "choices"
            default: throw bad
            }
            guard common.isSubset(of: have), have.subtracting(common).allSatisfy({ $0 == extra }) else { throw bad }
            guard label(item["title"], 40), seen.insert(key).inserted else { throw bad }
            let def = item["default"]
            if kind == "toggle", def?.bool == nil { throw bad }
            if kind == "text" {
                let longest = item["maxLength"] ?? .number(200)
                guard whole(longest, 1, 200), let text = def?.string,
                      Double(text.unicodeScalars.count) <= (longest.number ?? 0) else { throw bad }
            }
            if kind == "choice" {
                guard let choices = item["choices"]?.array, (2...10).contains(choices.count) else { throw bad }
                for ch in choices {
                    guard keys(ch) == ["value", "title"], settingKey(ch["value"]), label(ch["title"], 40) else { throw bad }
                }
                let values = choices.compactMap { $0["value"]?.string }
                guard Set(values).count == choices.count, let d = def?.string, values.contains(d) else { throw bad }
            }
        }
    }

    static func paletteRule(_ palette: AddonJSON) throws {
        let bad = refuse("contributes")
        guard keys(palette) == ["day", "night"] else { throw bad }
        for time in ["day", "night"] {
            guard let tones = palette[time], keys(tones) == ["page", "card", "cardText", "link", "folders"],
                  ["page", "card", "cardText", "link"].allSatisfy({ colour(tones[$0]) }),
                  let folders = tones["folders"]?.array, folders.count <= 6, folders.allSatisfy({ colour($0) }) else { throw bad }
            for (front, back, least) in [("cardText", "card", 4.5), ("card", "page", 3.0), ("link", "page", 3.0)] {
                if contrast(tones[front]?.string ?? "", tones[back]?.string ?? "") < least { throw bad }
            }
        }
    }

    /// WCAG 2's contrast ratio of two #RRGGBB colours.
    static func contrast(_ one: String, _ two: String) -> Double {
        func luminance(_ colour: String) -> Double {
            let u = Array(colour.utf8)
            var channels: [Double] = []
            for at in [1, 3, 5] {
                let byte = Double(Int(String(decoding: u[at..<(at + 2)], as: UTF8.self), radix: 16) ?? 0)
                let c = byte / 255
                channels.append(c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4))
            }
            return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        }
        let a = luminance(one), b = luminance(two)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    // MARK: semantic versions

    /// N.N.N or N.N.N-P(.P)*; no build metadata. The numbers, then the
    /// pre-release parts (nil for none).
    static func semver(_ s: String) -> (numbers: [Int], pre: [String]?)? {
        var core = Substring(s)
        var pre: [String]?
        if let dash = s.firstIndex(of: "-") {
            core = s[..<dash]
            let ids = s[s.index(after: dash)...].split(separator: ".", omittingEmptySubsequences: false).map(String.init)
            for id in ids {
                let u = Array(id.utf8)
                guard !u.isEmpty, u.allSatisfy({ digit($0) || lower($0) || upper($0) || $0 == 45 }) else { return nil }
                if u.allSatisfy(digit), !(u.count <= 9 && (u.count == 1 || u[0] != 48)) { return nil }
            }
            pre = ids
        }
        let nums = core.split(separator: ".", omittingEmptySubsequences: false)
        guard nums.count == 3 else { return nil }
        var out: [Int] = []
        for n in nums {
            let u = Array(n.utf8)
            guard (1...9).contains(u.count), u.allSatisfy(digit), u.count == 1 || u[0] != 48, let v = Int(n) else { return nil }
            out.append(v)
        }
        return (out, pre)
    }

    /// Precedence as semver.org has it: -1, 0 or 1.
    static func compare(_ a: String, _ b: String) -> Int {
        guard let x = semver(a), let y = semver(b) else { return a == b ? 0 : (a < b ? -1 : 1) }
        for (p, q) in zip(x.numbers, y.numbers) where p != q { return p < q ? -1 : 1 }
        switch (x.pre, y.pre) {
        case (nil, nil): return 0
        case (nil, _): return 1
        case (_, nil): return -1
        case let (ps?, qs?):
            for (u, v) in zip(ps, qs) where u != v {
                let un = u.utf8.allSatisfy(digit), vn = v.utf8.allSatisfy(digit)
                if un, vn { return Int(u)! < Int(v)! ? -1 : 1 }
                if un != vn { return un ? -1 : 1 }
                return Array(u.utf8).lexicographicallyPrecedes(Array(v.utf8)) ? -1 : 1
            }
            return ps.count == qs.count ? 0 : (ps.count < qs.count ? -1 : 1)
        }
    }

    /// The rules only the app can apply: its API and its version.
    static func install(_ p: AddonPackage, appVersion: String, appAPI: Int = appAPI) -> AddonRefusal? {
        if (p.manifest["api"]?.number ?? 0) > Double(appAPI) { return refuse("install.api") }
        if compare(p.manifest["minSorta"]?.string ?? "", appVersion) > 0 { return refuse("install.minSorta") }
        return nil
    }
}

/// What the person sees before saying yes: nothing has run.
struct AddonReview {
    var package: AddonPackage
    var manifest: AddonJSON { package.manifest }
    var permissions: [String] { package.permissions }
    var hosts: [String] { package.hosts }
    /// The file's size in bytes, and its SHA-256 in lowercase hex.
    var size: Int
    var sha256: String

    /// From a file's or a link's bytes: the archive checked, unpacked and
    /// checked, then the app's own install rules.
    static func make(_ data: Data, appVersion: String, appAPI: Int = AddonPackageCheck.appAPI) -> Result<AddonReview, AddonRefusal> {
        switch AddonPackageCheck.archive(data) {
        case let .failure(r): return .failure(r)
        case let .success(p):
            if let r = AddonPackageCheck.install(p, appVersion: appVersion, appAPI: appAPI) { return .failure(r) }
            return .success(AddonReview(package: p, size: data.count, sha256: AddonSHA256.hex(data)))
        }
    }

    /// The grants a person may choose from this review: each permission,
    /// net as one grant per declared host, and a folder grant per folder.
    func allows(_ grant: String) -> Bool {
        if grant.hasPrefix("net:") { return permissions.contains("net") && hosts.contains(String(grant.dropFirst(4))) }
        if grant.hasPrefix(AddonPermissions.folderGrant) { return permissions.contains("ideas.read.folder") }
        return grant != "net" && grant != "ideas.read.folder" && permissions.contains(grant)
    }
}

// MARK: - the zip reader (rules.md, section 1)

enum AddonZip {
    struct Entry {
        var raw: [UInt8]
        var name: String
        var flags: Int
        var method: Int
        var crc: UInt32
        var packed: Int
        var size: Int
        var external: Int
        var localAt: Int
    }

    private static let bad = AddonRefusal(code: "zip.unreadable")

    private static func u16(_ d: [UInt8], _ i: Int) throws -> Int {
        guard i >= 0, i + 2 <= d.count else { throw bad }
        return Int(d[i]) | Int(d[i + 1]) << 8
    }

    private static func u32(_ d: [UInt8], _ i: Int) throws -> Int { try u16(d, i) | u16(d, i + 2) << 16 }

    /// The names and contents, in the directory's order.
    static func read(_ data: Data) throws -> ([String], [Data]) {
        let d = [UInt8](data)
        if d.count > AddonPackageCheck.maxZipped { throw AddonRefusal(code: "zip.size") }
        // 2: the end record, searched backwards from the last possible place
        guard d.count >= 22 else { throw bad }
        var endAt = -1
        var at = d.count - 22
        let lowest = max(0, d.count - 22 - 0xFFFF)
        while at >= lowest {
            if d[at] == 0x50, d[at + 1] == 0x4B, d[at + 2] == 5, d[at + 3] == 6, at + 22 + (try u16(d, at + 20)) == d.count {
                endAt = at
                break
            }
            at -= 1
        }
        guard endAt >= 0 else { throw bad }
        let disk = try u16(d, endAt + 4), dirDisk = try u16(d, endAt + 6), here = try u16(d, endAt + 8)
        let count = try u16(d, endAt + 10), dirSize = try u32(d, endAt + 12), dirAt = try u32(d, endAt + 16)
        if disk != 0 || dirDisk != 0 || here != count { throw bad }
        if count == 0xFFFF || dirSize == 0xFFFF_FFFF || dirAt == 0xFFFF_FFFF { throw bad }
        if endAt >= 20, d[endAt - 20] == 0x50, d[endAt - 19] == 0x4B, d[endAt - 18] == 6, d[endAt - 17] == 7 { throw bad }
        if try u16(d, endAt + 20) != 0 { throw bad }
        if dirAt + dirSize != endAt { throw bad }
        // 3
        if count > AddonPackageCheck.maxEntries { throw AddonRefusal(code: "zip.entries") }
        // 4: the directory, filled exactly by its entries; no entry comment
        var entries: [Entry] = []
        var p = dirAt
        let stop = dirAt + dirSize
        for _ in 0..<count {
            guard p + 46 <= stop, try u32(d, p) == 0x0201_4B50 else { throw bad }
            let nameLen = try u16(d, p + 28), extraLen = try u16(d, p + 30), commentLen = try u16(d, p + 32)
            let nameAt = p + 46
            let next = nameAt + nameLen + extraLen + commentLen
            if next > stop { throw bad }
            if commentLen != 0 { throw bad }
            let raw = Array(d[nameAt..<(nameAt + nameLen)])
            entries.append(Entry(raw: raw, name: String(String.UnicodeScalarView(raw.map { Unicode.Scalar($0) })),
                                 flags: try u16(d, p + 8), method: try u16(d, p + 10), crc: UInt32(try u32(d, p + 16)),
                                 packed: try u32(d, p + 20), size: try u32(d, p + 24), external: try u32(d, p + 38),
                                 localAt: try u32(d, p + 42)))
            p = next
        }
        if p != stop { throw bad }
        // 5 to 12, each over every entry before the next
        for e in entries where e.flags & 0x2041 != 0 || e.method == 99 { throw AddonRefusal(code: "zip.encrypted") }
        for e in entries where e.method != 0 && e.method != 8 { throw bad }
        for e in entries { try pathRule(e.raw) }
        for e in entries where (e.external >> 16) & 0xF000 == 0xA000 { throw AddonRefusal(code: "zip.link") }
        var seen = Set<String>()
        for e in entries where !seen.insert(e.name).inserted { throw AddonRefusal(code: "zip.duplicate") }
        var folded = Set<String>()
        for e in entries {
            let low = String(String.UnicodeScalarView(e.raw.map { Unicode.Scalar($0 >= 65 && $0 <= 90 ? $0 + 32 : $0) }))
            if !folded.insert(low).inserted { throw AddonRefusal(code: "zip.case") }
        }
        for e in entries where e.size > AddonPackageCheck.maxRatio * e.packed { throw AddonRefusal(code: "zip.ratio") }
        let total = entries.reduce(0) { $0 + $1.size }
        if total > AddonPackageCheck.maxRatio * d.count { throw AddonRefusal(code: "zip.ratio") }
        if total > AddonPackageCheck.maxUnpacked { throw AddonRefusal(code: "zip.size") }
        // 13: every byte before the directory belongs to exactly one entry
        var spans: [(Int, Int)] = []
        for e in entries {
            guard e.localAt + 30 <= dirAt, try u32(d, e.localAt) == 0x0403_4B50 else { throw bad }
            spans.append((e.localAt, e.localAt + 30 + (try u16(d, e.localAt + 26)) + (try u16(d, e.localAt + 28)) + e.packed))
        }
        spans.sort { $0.0 != $1.0 ? $0.0 < $1.0 : $0.1 < $1.1 }
        for i in spans.indices.dropFirst() where spans[i].0 < spans[i - 1].1 { throw bad }
        var reach = 0
        for (start, end) in spans + [(dirAt, dirAt)] {
            if start > reach { throw bad }
            reach = max(reach, end)
        }
        // 14: unpacking, in the directory's order
        var names: [String] = [], contents: [Data] = []
        for e in entries {
            let nameLen = try u16(d, e.localAt + 26), extraLen = try u16(d, e.localAt + 28)
            let start = e.localAt + 30 + nameLen + extraLen
            let end = start + e.packed
            guard e.localAt + 30 + nameLen <= d.count, Array(d[(e.localAt + 30)..<(e.localAt + 30 + nameLen)]) == e.raw,
                  try u16(d, e.localAt + 8) == e.method, end <= dirAt else { throw bad }
            let packed = Data(d[start..<end])
            let content: Data
            if e.method == 0 {
                guard e.packed == e.size else { throw bad }
                content = packed
            } else {
                content = try AddonInflate.inflate(packed, limit: e.size)
                guard content.count == e.size else { throw bad }
            }
            guard AddonCRC32.of(content) == e.crc else { throw bad }
            names.append(e.name)
            contents.append(content)
        }
        return (names, contents)
    }

    /// Step 7: the entry name's rules.
    static func pathRule(_ raw: [UInt8]) throws {
        let refused = AddonRefusal(code: "zip.path")
        if raw.isEmpty || raw.count > AddonPackageCheck.maxNameBytes { throw refused }
        if raw.contains(92) || raw[0] == 47 { throw refused }
        if raw.count >= 2, raw[1] == 58, (raw[0] >= 65 && raw[0] <= 90) || (raw[0] >= 97 && raw[0] <= 122) { throw refused }
        let body = raw.last == 47 ? Array(raw.dropLast()) : raw
        for part in body.split(separator: 47, omittingEmptySubsequences: false) {
            if part.isEmpty || Array(part) == [46] || Array(part) == [46, 46] { throw refused }
        }
        let ok = raw.allSatisfy { c in
            (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || (c >= 48 && c <= 57) || c == 46 || c == 95 || c == 45 || c == 47
        }
        if !ok { throw refused }
    }
}

// MARK: - raw deflate (RFC 1951), bounded by the entry's stated size

enum AddonInflate {
    private struct Bits {
        let d: [UInt8]
        var pos = 0
        var bit = 0
        mutating func take(_ n: Int) throws -> Int {
            var v = 0
            for k in 0..<n {
                guard pos < d.count else { throw AddonRefusal(code: "zip.unreadable") }
                v |= Int((d[pos] >> UInt8(bit)) & 1) << k
                bit += 1
                if bit == 8 { bit = 0; pos += 1 }
            }
            return v
        }
    }

    private struct Huffman {
        var counts = [Int](repeating: 0, count: 16)
        var symbols: [Int] = []
        init(_ lengths: [Int]) {
            for l in lengths { counts[l] += 1 }
            counts[0] = 0
            var offs = [Int](repeating: 0, count: 16)
            for i in 1..<16 { offs[i] = offs[i - 1] + counts[i - 1] }
            symbols = [Int](repeating: 0, count: lengths.count)
            for (s, l) in lengths.enumerated() where l != 0 { symbols[offs[l]] = s; offs[l] += 1 }
        }
        func decode(_ b: inout Bits) throws -> Int {
            var code = 0, first = 0, index = 0
            for len in 1..<16 {
                code |= try b.take(1)
                let n = counts[len]
                if code - n < first { return symbols[index + code - first] }
                index += n
                first = (first + n) << 1
                code <<= 1
            }
            throw AddonRefusal(code: "zip.unreadable")
        }
    }

    private static let lbase = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115,
                                131, 163, 195, 227, 258]
    private static let lext = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0]
    private static let dbase = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537,
                                2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577]
    private static let dext = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13]
    private static let order = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15]

    static func inflate(_ data: Data, limit: Int) throws -> Data {
        var b = Bits(d: [UInt8](data))
        var out: [UInt8] = []
        out.reserveCapacity(limit)
        var last = 0
        repeat {
            last = try b.take(1)
            let type = try b.take(2)
            switch type {
            case 0:
                if b.bit != 0 { b.bit = 0; b.pos += 1 }
                guard b.pos + 4 <= b.d.count else { throw AddonRefusal(code: "zip.unreadable") }
                let len = Int(b.d[b.pos]) | Int(b.d[b.pos + 1]) << 8
                let nlen = Int(b.d[b.pos + 2]) | Int(b.d[b.pos + 3]) << 8
                guard len == (~nlen & 0xFFFF), b.pos + 4 + len <= b.d.count, out.count + len <= limit else {
                    throw AddonRefusal(code: "zip.unreadable")
                }
                out.append(contentsOf: b.d[(b.pos + 4)..<(b.pos + 4 + len)])
                b.pos += 4 + len
            case 1:
                var ll = [Int](repeating: 8, count: 288)
                for i in 144..<256 { ll[i] = 9 }
                for i in 256..<280 { ll[i] = 7 }
                try block(&b, &out, Huffman(ll), Huffman([Int](repeating: 5, count: 30)), limit)
            case 2:
                let hlit = try b.take(5) + 257, hdist = try b.take(5) + 1, hclen = try b.take(4) + 4
                var cl = [Int](repeating: 0, count: 19)
                for i in 0..<hclen { cl[order[i]] = try b.take(3) }
                let ch = Huffman(cl)
                var lengths: [Int] = []
                while lengths.count < hlit + hdist {
                    let sym = try ch.decode(&b)
                    switch sym {
                    case 0..<16: lengths.append(sym)
                    case 16:
                        guard let prev = lengths.last else { throw AddonRefusal(code: "zip.unreadable") }
                        lengths += [Int](repeating: prev, count: 3 + (try b.take(2)))
                    case 17: lengths += [Int](repeating: 0, count: 3 + (try b.take(3)))
                    default: lengths += [Int](repeating: 0, count: 11 + (try b.take(7)))
                    }
                }
                guard lengths.count == hlit + hdist else { throw AddonRefusal(code: "zip.unreadable") }
                try block(&b, &out, Huffman(Array(lengths[0..<hlit])), Huffman(Array(lengths[hlit...])), limit)
            default:
                throw AddonRefusal(code: "zip.unreadable")
            }
        } while last == 0
        return Data(out)
    }

    private static func block(_ b: inout Bits, _ out: inout [UInt8], _ lit: Huffman, _ dist: Huffman, _ limit: Int) throws {
        while true {
            let sym = try lit.decode(&b)
            if sym < 256 {
                guard out.count < limit else { throw AddonRefusal(code: "zip.unreadable") }
                out.append(UInt8(sym))
            } else if sym == 256 {
                return
            } else {
                let li = sym - 257
                guard li < 29 else { throw AddonRefusal(code: "zip.unreadable") }
                let len = lbase[li] + (try b.take(lext[li]))
                let di = try dist.decode(&b)
                guard di < 30 else { throw AddonRefusal(code: "zip.unreadable") }
                let back = dbase[di] + (try b.take(dext[di]))
                guard back <= out.count, out.count + len <= limit else { throw AddonRefusal(code: "zip.unreadable") }
                let from = out.count - back
                for k in 0..<len { out.append(out[from + k]) }
            }
        }
    }
}

enum AddonCRC32 {
    private static let table: [UInt32] = (0..<256).map { n -> UInt32 in
        var c = UInt32(n)
        for _ in 0..<8 { c = c & 1 != 0 ? 0xEDB8_8320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func of(_ d: Data) -> UInt32 {
        var c: UInt32 = 0xFFFF_FFFF
        for byte in d { c = table[Int((c ^ UInt32(byte)) & 0xFF)] ^ (c >> 8) }
        return c ^ 0xFFFF_FFFF
    }
}

// MARK: - SHA-256 (FIPS 180-4), for the index's checksums and the review

enum AddonSHA256 {
    private static let k: [UInt32] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ]

    static func hex(_ data: Data) -> String {
        var h: [UInt32] = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]
        var m = [UInt8](data)
        let bits = UInt64(m.count) * 8
        m.append(0x80)
        while m.count % 64 != 56 { m.append(0) }
        for s in stride(from: 56, through: 0, by: -8) { m.append(UInt8(truncatingIfNeeded: bits >> UInt64(s))) }
        var w = [UInt32](repeating: 0, count: 64)
        func rotr(_ x: UInt32, _ n: UInt32) -> UInt32 { (x >> n) | (x << (32 - n)) }
        for chunk in stride(from: 0, to: m.count, by: 64) {
            for i in 0..<16 {
                let j = chunk + i * 4
                w[i] = UInt32(m[j]) << 24 | UInt32(m[j + 1]) << 16 | UInt32(m[j + 2]) << 8 | UInt32(m[j + 3])
            }
            for i in 16..<64 {
                let s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3)
                let s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10)
                w[i] = w[i - 16] &+ s0 &+ w[i - 7] &+ s1
            }
            var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7]
            for i in 0..<64 {
                let t1 = hh &+ (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) &+ ((e & f) ^ (~e & g)) &+ k[i] &+ w[i]
                let t2 = (rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) &+ ((a & b) ^ (a & c) ^ (b & c))
                hh = g; g = f; f = e; e = d &+ t1; d = c; c = b; b = a; a = t1 &+ t2
            }
            h[0] = h[0] &+ a; h[1] = h[1] &+ b; h[2] = h[2] &+ c; h[3] = h[3] &+ d
            h[4] = h[4] &+ e; h[5] = h[5] &+ f; h[6] = h[6] &+ g; h[7] = h[7] &+ hh
        }
        return h.map { String(format: "%08x", $0) }.joined()
    }
}
