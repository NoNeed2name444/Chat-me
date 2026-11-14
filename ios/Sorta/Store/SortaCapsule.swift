import Foundation

// The capsule (S06d A4-A5, plugins-plan idea 11): every idea and folder in
// one .html file that any browser opens with no script to run, and that
// Sorta opens again. The page is for reading: each folder by its path, each
// idea with its words, kind, when it was caught and what it is linked to,
// and the map's picture. Its data is a JSON block the page never runs:
// every note and folder as the store codes them (Note, NoteFolder), so a
// field such as a note's source comes back as it went. Android's Capsule
// writes and reads the same file; here it is SortaCapsule, since SwiftUI's
// Capsule is a shape the app draws with.

enum SortaCapsule {
    static let format = "sorta-capsule"
    /// The one version there is. A capsule of a later one is refused, as a
    /// newer backup is.
    static let version = 1
    /// The data block's id.
    static let blockID = "sorta-capsule"
    /// The largest file Open a capsule reads, as Android's import
    /// (IdeaStore.MAX_IMPORT_BYTES).
    static let maxBytes = 32 * 1024 * 1024

    /// Why a file was not opened, with nothing changed.
    enum Failure: Error, Equatable {
        /// No data block, or another format, or no version.
        case notACapsule
        /// A later version than this one reads.
        case newerVersion
        /// A block that isn't JSON, or its notes or folders not a list, or a
        /// file that can't be read.
        case unreadable
        /// Over `maxBytes`.
        case tooLarge
    }

    /// What a capsule holds.
    struct Contents {
        var notes: [Note]
        var folders: [NoteFolder]
    }

    /// The map's picture: a PNG drawn at twice `width` by `height` CSS
    /// pixels.
    struct Picture {
        var png: Data
        var width: Int
        var height: Int
    }

    /// The page's words (the S06d writing pass), the same on Android. The
    /// page is a file that leaves the phone, so its words are fixed English,
    /// as Android's are.
    enum Words {
        static let title = "Sorta capsule"
        static let toSort = "To sort"
        static let untitled = "Untitled"
        static let idea = "Idea"
        static let page = "Page"
        static let linkedTo = "Linked to"
        static let emptyFolder = "No ideas here."
        static let picture = "Every idea on the board, joined by its links."
        static let restore = "To bring these ideas back, open this file in Sorta with More, then Open a capsule."
        static func saved(_ date: String) -> String { "Saved \(date)." }
        static func counts(ideas: Int, folders: Int) -> String {
            let i: String = ideas == 1 ? "1 idea" : "\(ideas) ideas"
            switch folders {
            case 0: return "\(i)."
            case 1: return "\(i) in 1 folder."
            default: return "\(i) in \(folders) folders."
            }
        }
        static func caught(_ kind: String, _ date: String) -> String { "\(kind), caught \(date)" }
    }

    /// The date as the page writes it: "14 November 2026 at 9:05 PM".
    static func date(_ date: Date, zone: TimeZone) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = zone
        f.dateFormat = "d MMMM yyyy 'at' h:mm a"
        return f.string(from: date)
    }

    /// The file's name: "Sorta capsule 2026-11-14.html".
    static func fileName(_ date: Date, zone: TimeZone) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = zone
        f.dateFormat = "yyyy-MM-dd"
        return "\(Words.title) \(f.string(from: date)).html"
    }

    /// The data block's shape: its format and version, when it was saved,
    /// and every note and folder as the store writes them.
    struct Payload: Codable {
        var format: String = SortaCapsule.format
        var version: Int = SortaCapsule.version
        var app: String = "Sorta"
        var savedAt: Date
        var notes: [Note]
        var folders: [NoteFolder]
    }

    /// The capsule's data, as the store codes it (JSONEncoder.sorta).
    static func data(notes: [Note], folders: [NoteFolder], savedAt: Date) throws -> Data {
        try JSONEncoder.sorta.encode(Payload(savedAt: savedAt, notes: notes, folders: folders))
    }

    /// JSON that can sit in a script block: nothing in it can close the
    /// block or start a comment.
    static func scriptSafe(_ json: String) -> String {
        var out = ""
        out.reserveCapacity(json.utf16.count)
        for c in json.unicodeScalars {
            switch c {
            case "<": out += "\\u003c"
            case ">": out += "\\u003e"
            case "&": out += "\\u0026"
            case "\u{2028}": out += "\\u2028"
            case "\u{2029}": out += "\\u2029"
            default: out.unicodeScalars.append(c)
            }
        }
        return out
    }

    /// Text as HTML.
    static func escape(_ text: String) -> String {
        var out = ""
        out.reserveCapacity(text.utf16.count)
        for c in text.unicodeScalars {
            switch c {
            case "&": out += "&amp;"
            case "<": out += "&lt;"
            case ">": out += "&gt;"
            case "\"": out += "&quot;"
            case "'": out += "&#39;"
            default: out.unicodeScalars.append(c)
            }
        }
        return out
    }

    /// The page: `notes` under To sort and their folders (`folders`, each by
    /// its path, in Finder's order), each with what `edges`
    /// (NoteStore.allEdges: hand-made links and `[[Title]]`s) link it to;
    /// `picture` the map's; dates in `zone`; then the data block.
    static func html(notes: [Note], folders: [NoteFolder], edges: [(UUID, UUID)], savedAt: Date,
                     zone: TimeZone, picture: Picture?) throws -> String {
        var byID: [UUID: Note] = [:]
        for n in notes where byID[n.id] == nil { byID[n.id] = n }
        let folderIDs = Set(folders.map(\.id))
        var linked: [UUID: [UUID]] = [:]
        for (a, b) in edges {
            linked[a, default: []].append(b)
            linked[b, default: []].append(a)
        }
        func title(_ n: Note) -> String {
            let t = n.title.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? Words.untitled : t
        }
        func inFolder(_ id: UUID?) -> [Note] {
            notes.filter { n in
                let f: UUID? = n.folderId.flatMap { folderIDs.contains($0) ? $0 : nil }
                return f == id
            }
            .sorted { a, b in
                a.createdAt != b.createdAt ? a.createdAt < b.createdAt : a.id.uuidString < b.id.uuidString
            }
        }

        var out = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
        out += "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        out += "<meta name=\"generator\" content=\"Sorta\">\n"
        out += "<title>\(escape(Words.title))</title>\n<style>\n\(style)</style>\n</head>\n<body>\n<main>\n"
        out += "<header>\n<h1>\(escape(Words.title))</h1>\n<p class=\"saved\">"
        out += escape(Words.saved(date(savedAt, zone: zone)) + " " + Words.counts(ideas: notes.count, folders: folders.count))
        out += "</p>\n</header>\n"
        if let picture {
            out += "<figure><img src=\"data:image/png;base64,\(picture.png.base64EncodedString())\""
            out += " width=\"\(picture.width)\" height=\"\(picture.height)\" alt=\"\(escape(Words.picture))\"></figure>\n"
        }
        func section(_ heading: String, _ list: [Note], folder: Bool) {
            out += "<section>\n<h2>\(escape(heading))</h2>\n"
            if list.isEmpty && folder { out += "<p class=\"none\">\(escape(Words.emptyFolder))</p>\n" }
            for n in list {
                let kind: String = n.kind == .page ? Words.page : Words.idea
                out += "<article>\n<h3>\(escape(title(n)))</h3>\n<p class=\"meta\">"
                out += escape(Words.caught(kind, date(n.createdAt, zone: zone))) + "</p>\n"
                let words: String = n.body.trimmingCharacters(in: .whitespacesAndNewlines)
                if !words.isEmpty { out += "<div class=\"words\">\(escape(words))</div>\n" }
                var seen = Set<UUID>()
                let names: [String] = (linked[n.id] ?? []).filter { seen.insert($0).inserted }.compactMap { byID[$0].map(title) }
                if !names.isEmpty {
                    out += "<p class=\"links\">\(escape(Words.linkedTo + " " + names.joined(separator: ", ")))</p>\n"
                }
                out += "</article>\n"
            }
            out += "</section>\n"
        }
        section(Words.toSort, inFolder(nil), folder: false)
        for (folder, path) in outline(folders) { section(path, inFolder(folder.id), folder: true) }
        out += "<footer><p>\(escape(Words.restore))</p></footer>\n</main>\n"
        let json = String(decoding: try data(notes: notes, folders: folders, savedAt: savedAt), as: UTF8.self)
        out += "<script type=\"application/json\" id=\"\(blockID)\">\(scriptSafe(json))</script>\n</body>\n</html>\n"
        return out
    }

    /// Every folder with its path ("Garden › Paths"), parents before
    /// children, each level in Finder's order.
    static func outline(_ folders: [NoteFolder]) -> [(folder: NoteFolder, path: String)] {
        let ids = Set(folders.map(\.id))
        var children: [UUID?: [NoteFolder]] = [:]
        for f in folders {
            let parent: UUID? = f.parentId.flatMap { ids.contains($0) && $0 != f.id ? $0 : nil }
            children[parent, default: []].append(f)
        }
        var out: [(folder: NoteFolder, path: String)] = []
        var seen = Set<UUID>()
        func walk(_ parent: UUID?, _ prefix: String) {
            let level = (children[parent] ?? []).sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
            for f in level where seen.insert(f.id).inserted {
                let path = prefix + f.name
                out.append((f, path))
                walk(f.id, path + " \u{203A} ")
            }
        }
        walk(nil, "")
        // a folder in a loop of parents is shown at the top
        for f in folders where seen.insert(f.id).inserted { out.append((f, f.name)) }
        return out
    }

    /// The notes and folders in a capsule's `text`, checked as a backup is
    /// (LibraryBackup.check): no data block, or another format, or no
    /// version, is `.notACapsule`; a later version `.newerVersion`; a block
    /// that isn't JSON, or its notes or folders there but not a list,
    /// `.unreadable`. A note or folder that can't be read is left out, as
    /// the store's own file reads.
    static func read(_ text: String) throws -> Contents {
        guard let block = block(in: text) else { throw Failure.notACapsule }
        let data = Data(block.utf8)
        guard let any = try? JSONSerialization.jsonObject(with: data), let root = any as? [String: Any] else {
            throw Failure.unreadable
        }
        guard (root["format"] as? String) == format else { throw Failure.notACapsule }
        guard let head = try? decoder.decode(Head.self, from: data), let found = head.version else {
            throw Failure.notACapsule
        }
        guard found <= Double(version) else { throw Failure.newerVersion }
        for key in ["notes", "folders"] where root[key] != nil && !(root[key] is [Any]) { throw Failure.unreadable }
        guard let body = try? decoder.decode(Body.self, from: data) else { throw Failure.unreadable }
        return Contents(notes: (body.notes ?? []).compactMap(\.value), folders: (body.folders ?? []).compactMap(\.value))
    }

    /// The data block's text, or nil.
    static func block(in text: String) -> String? {
        let pattern = "<script\\b[^>]*\\bid\\s*=\\s*[\"']\(blockID)[\"'][^>]*>(.*?)</script\\s*>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }

    /// The version, read as a number: a string or a flag is no version.
    private struct Head: Decodable {
        var version: Double?
        private enum Keys: String, CodingKey { case version }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: Keys.self)
            version = try? c.decodeIfPresent(Double.self, forKey: .version)
        }
    }

    private struct Body: Decodable {
        var notes: [Kept<Note>]?
        var folders: [Kept<NoteFolder>]?
    }

    /// One entry, or nil where it can't be read.
    private struct Kept<T: Decodable>: Decodable {
        var value: T?
        init(from decoder: Decoder) throws { value = try? T(from: decoder) }
    }

    /// The store's decoder, with dates as either platform writes them: whole
    /// seconds (iOS), milliseconds (Android), or a number of seconds or
    /// milliseconds since 1970.
    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { decoder in
            let c = try decoder.singleValueContainer()
            if let text = try? c.decode(String.self) {
                if let date = isoDate(text) { return date }
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "Not a date: \(text)")
            }
            let n = try c.decode(Double.self)
            return Date(timeIntervalSince1970: abs(n) >= 100_000_000_000 ? n / 1000 : n)
        }
        return d
    }()

    /// An ISO 8601 time with or without its fraction of a second.
    static func isoDate(_ text: String) -> Date? {
        let whole = ISO8601DateFormatter()
        whole.formatOptions = [.withInternetDateTime]
        if let date = whole.date(from: text) { return date }
        let fraction = ISO8601DateFormatter()
        fraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fraction.date(from: text)
    }

    /// The page's look: Sorta's bone and aubergine by day and by night, ink
    /// words, the coral on edges only. The same as Android's.
    static let style: String = """
    :root { color-scheme: light dark; --page: #F3EADB; --ink: #281E2A; --soft: #5E545A; --line: #DCD3C7; --accent: #C4503A; }
    @media (prefers-color-scheme: dark) { :root { --page: #281E2A; --ink: #F3EADB; --soft: #B0A7A1; --line: #392F39; --accent: #EE866D; } }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--page); color: var(--ink); font: 17px/1.5 Nunito, ui-rounded, "SF Pro Rounded", system-ui, sans-serif; }
    main { max-width: 44rem; margin: 0 auto; padding: 24px 16px 48px; }
    h1 { font-size: 1.75rem; margin: 0 0 4px; }
    h2 { font-size: 1.25rem; margin: 32px 0 8px; padding-bottom: 4px; border-bottom: 2px solid var(--accent); }
    h3 { font-size: 1.0625rem; margin: 0; }
    .saved, .meta, .links, .none, footer { color: var(--soft); font-size: 0.9375rem; }
    .saved { margin: 0; }
    figure { margin: 24px 0 0; }
    figure img { display: block; max-width: 100%; height: auto; border: 1px solid var(--line); border-radius: 12px; }
    article { padding: 12px 0; border-bottom: 1px solid var(--line); }
    article:last-child { border-bottom: 0; }
    .meta, .links { margin: 2px 0 0; }
    .words { white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 6px; }
    footer { margin-top: 40px; }

    """
}

extension SortaCapsule {
    /// Opens the capsule at `url` and merges its ideas into `store`, as
    /// Stethoscore's backup is (IdeaImport.merge): through `restore`, so an
    /// idea or folder already here stays as it is and a second open adds
    /// nothing. Throws a SortaCapsule.Failure, with nothing changed.
    @MainActor
    static func merge(capsuleAt url: URL, into store: NoteStore) throws -> IdeaImport.Counts {
        let size: Int = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size <= maxBytes else { throw Failure.tooLarge }
        guard let data = try? Data(contentsOf: url) else { throw Failure.unreadable }
        return try merge(data, into: store)
    }

    @MainActor
    static func merge(_ data: Data, into store: NoteStore) throws -> IdeaImport.Counts {
        guard data.count <= maxBytes else { throw Failure.tooLarge }
        var contents: Contents = try read(String(decoding: data, as: UTF8.self))
        // an id twice in one file comes in once, as on Android
        var seenNotes = Set<UUID>()
        var seenFolders = Set<UUID>()
        contents.notes = contents.notes.filter { seenNotes.insert($0.id).inserted }
        contents.folders = contents.folders.filter { seenFolders.insert($0.id).inserted }
        let haveNotes = Set(store.notes.map(\.id))
        let haveFolders = Set(store.folders.map(\.id))
        let added: [Note] = contents.notes.filter { !haveNotes.contains($0.id) }
        var counts = IdeaImport.Counts()
        counts.folders = contents.folders.filter { !haveFolders.contains($0.id) }.count
        counts.ideas = store.restore(notes: contents.notes, folders: contents.folders)
        counts.links = added.reduce(0) { $0 + $1.links.count }
        counts.placed = added.filter { $0.boardX != 0 || $0.boardY != 0 }.count
        return counts
    }
}
