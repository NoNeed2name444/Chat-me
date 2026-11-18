import Foundation

// MARK: - The second brain's operations (S08 A5, A6)
//
// Pure logic, the same on Android (AIOperations.kt): each payload builder
// sends section 4's least, and each answer is parsed and checked before
// anything changes. An answer that does not parse or does not check changes
// nothing. Cases: ai/fixtures/model.

enum AIVerdict: String {
    case ok, unreadable, notASlice, badCitation, empty, nothingFits
}

/// One idea as an operation sees it.
struct AIIdea: Equatable {
    var id: String
    var title: String
    var text: String
}

/// The prompts and schemas in ai/prompts, the same bytes in both apps.
struct AIPrompt {
    let name: String
    let instructions: String
    let schema: String

    static let names = ["split", "why", "ask", "sumup"]

    static func load(_ name: String, root: URL) throws -> AIPrompt {
        let dir = root.appendingPathComponent("prompts")
        let text = try String(contentsOf: dir.appendingPathComponent(name + ".txt"), encoding: .utf8)
        let schema = try String(contentsOf: dir.appendingPathComponent(name + ".schema.json"), encoding: .utf8)
        return AIPrompt(name: name, instructions: text, schema: schema)
    }
}

/// What an operation sends: the input, and how many ideas and characters
/// of the person's own text it carries (for the tests and the ledger).
struct AIPayload {
    var input: String
    var ideas: Int
    var characters: Int
}

// MARK: split a capture

enum AISplit {
    static let titleLimit = 60
    static let maxOutput = 1200

    struct Folder: Equatable {
        var id: String
        var name: String
        var words: [String]
    }

    struct Piece: Equatable {
        var text: String
        var title: String
        var folder: String?
    }

    /// More than one sentence: a line break, or ".", "!", "?" or "…"
    /// followed by a space, between two runs of letters or digits.
    static func sentences(_ text: String) -> Int {
        var count = 0
        var ended = true      // the next word starts a sentence
        var inside = false    // a word since the last end
        var stop = false      // a stop mark that ends the sentence if a space follows
        for s in text.unicodeScalars {
            if s.properties.isAlphabetic || s.properties.generalCategory == .decimalNumber {
                if ended { count += 1; ended = false }
                inside = true
                stop = false
            } else if s == "\n" || s == "\r" {
                if inside { ended = true; inside = false }
                stop = false
            } else if s == "." || s == "!" || s == "?" || s == "…" {
                stop = inside
            } else if AIText.isSpace(s) {
                if stop { ended = true; inside = false; stop = false }
            } else {
                stop = false
            }
        }
        return count
    }

    static func needed(_ capture: String) -> Bool { sentences(capture) > 1 }

    /// The capture, and the folders' names with their words. No other idea.
    static func payload(capture: String, folders: [Folder]) -> AIPayload {
        let list = folders.map { f in
            AIJSON.make([("name", .string(f.name)), ("words", .array(f.words.map(AIJSON.string)))])
        }
        let chars = AIText.count(capture) + folders.reduce(0) { $0 + AIText.count($1.name) + $1.words.reduce(0) { $0 + AIText.count($1) } }
        return AIPayload(input: AIJSON.make([("capture", .string(capture)), ("folders", .array(list))]).text,
                         ideas: 0, characters: chars)
    }

    static func check(_ answer: String, capture: String, folders: [Folder]) -> (AIVerdict, [Piece]) {
        if AIText.trim(answer).isEmpty { return (.empty, []) }
        guard let json = AIJSON.answer(answer), let items = json["ideas"]?.array else { return (.unreadable, []) }
        if items.isEmpty { return (.empty, []) }
        var pieces: [Piece] = []
        for item in items {
            guard let text = item["text"]?.string, let rawTitle = item["title"]?.string else { return (.unreadable, []) }
            let folderName = item["folder"]?.string
            if let f = item["folder"], f.string == nil, !f.isNull { return (.unreadable, []) }
            let title = AIText.trim(rawTitle)
            guard !title.isEmpty, AIText.count(title) <= titleLimit else { return (.unreadable, []) }
            let slice = AIText.trim(text)
            guard !slice.isEmpty, AIText.contains(capture, slice) else { return (.notASlice, []) }
            let folder = folderName.flatMap { name in folders.first(where: { $0.name == name })?.id }
            pieces.append(Piece(text: slice, title: title, folder: folder))
        }
        return (.ok, pieces)
    }
}

// MARK: the reasons behind suggested links ("say why")

enum AIWhy {
    static let ideaChars = 500
    static let candidateChars = 200
    static let candidates = 5
    static let reasonLimit = 120
    static let maxOutput = 600

    /// The new idea's title and first 500 characters, and up to 5
    /// candidates' titles and first 200 characters, numbered from 1.
    static func payload(idea: AIIdea, candidates all: [AIIdea]) -> (AIPayload, [AIIdea]) {
        let sent = Array(all.prefix(candidates))
        let text = AIText.prefix(idea.text, ideaChars)
        var chars = AIText.count(idea.title) + AIText.count(text)
        let list: [AIJSON] = sent.enumerated().map { k, c in
            let t = AIText.prefix(c.text, candidateChars)
            chars += AIText.count(c.title) + AIText.count(t)
            return .make([("n", .number(Double(k + 1))), ("title", .string(c.title)), ("text", .string(t))])
        }
        let input = AIJSON.make([("idea", .make([("title", .string(idea.title)), ("text", .string(text))])),
                                 ("candidates", .array(list))]).text
        return (AIPayload(input: input, ideas: 1 + sent.count, characters: chars), sent)
    }

    /// Candidate id to reason.
    static func check(_ answer: String, sent: [AIIdea]) -> (AIVerdict, [String: String]) {
        if AIText.trim(answer).isEmpty { return (.empty, [:]) }
        guard let json = AIJSON.answer(answer), let items = json["reasons"]?.array else { return (.unreadable, [:]) }
        var out: [String: String] = [:]
        for item in items {
            guard let n = item["n"]?.int, let raw = item["why"]?.string else { return (.unreadable, [:]) }
            guard n >= 1, n <= sent.count else { return (.badCitation, [:]) }
            let why = AIText.trim(raw)
            guard !why.isEmpty, AIText.count(why) <= reasonLimit else { return (.unreadable, [:]) }
            if out[sent[n - 1].id] == nil { out[sent[n - 1].id] = why }
        }
        return (.ok, out)
    }
}

// MARK: citations, for ask and sum up

enum AICitations {
    /// The numbers in square-bracket markers: "[2]", "[1, 3]".
    static func markers(_ text: String) -> [Int] {
        let s = Array(text.unicodeScalars)
        var out: [Int] = []
        var i = 0
        while i < s.count {
            guard s[i] == "[" else { i += 1; continue }
            var j = i + 1, numbers: [Int] = [], digits = ""
            var good = false
            while j < s.count {
                let c = s[j]
                if ("0"..."9").contains(c) { digits.unicodeScalars.append(c) }
                else if c == "," && !digits.isEmpty { numbers.append(Int(digits) ?? -1); digits = "" }
                else if c == " " { }
                else if c == "]" && !digits.isEmpty { numbers.append(Int(digits) ?? -1); good = true; break }
                else { break }
                j += 1
            }
            if good { out += numbers; i = j + 1 } else { i += 1 }
        }
        return out
    }

    /// The ideas cited, in the order the text first cites them and then the
    /// list; nil when any number is not one of the ideas sent.
    static func resolve(text: String, cites: [Int], sent: [Int: String]) -> [String]? {
        var ids: [String] = []
        for n in markers(text) + cites {
            guard let id = sent[n] else { return nil }
            if !ids.contains(id) { ids.append(id) }
        }
        return ids
    }
}

// MARK: ask your ideas

enum AIAsk {
    static let ideas = 12
    static let onDeviceIdeas = 6
    static let chars = 1000
    static let maxOutput = 800

    /// The question, and up to 12 ideas found on the phone (6 on the
    /// phone's own model), each its title and up to 1,000 characters.
    static func payload(question: String, found: [AIIdea], onDevice: Bool) -> (AIPayload, [AIIdea]) {
        let sent = Array(found.prefix(onDevice ? onDeviceIdeas : ideas))
        var count = AIText.count(question)
        let list: [AIJSON] = sent.enumerated().map { k, idea in
            let t = AIText.prefix(idea.text, chars)
            count += AIText.count(idea.title) + AIText.count(t)
            return .make([("n", .number(Double(k + 1))), ("title", .string(idea.title)), ("text", .string(t))])
        }
        let input = AIJSON.make([("question", .string(question)), ("ideas", .array(list))]).text
        return (AIPayload(input: input, ideas: sent.count, characters: count), sent)
    }

    struct Answer: Equatable {
        var text: String
        var cites: [String]
    }

    static func check(_ answer: String, sent: [AIIdea]) -> (AIVerdict, Answer?) {
        if AIText.trim(answer).isEmpty { return (.empty, nil) }
        guard let json = AIJSON.answer(answer), let text = json["answer"]?.string, let cites = json["cites"]?.array,
              let fits = json["nothingFits"]?.bool else { return (.unreadable, nil) }
        let numbers = cites.compactMap(\.int)
        guard numbers.count == cites.count else { return (.unreadable, nil) }
        if fits { return (.nothingFits, nil) }
        let body = AIText.trim(text)
        if body.isEmpty { return (.empty, nil) }
        let map = Dictionary(uniqueKeysWithValues: sent.enumerated().map { ($0.offset + 1, $0.element.id) })
        guard let ids = AICitations.resolve(text: body, cites: numbers, sent: map) else { return (.badCitation, nil) }
        return (.ok, Answer(text: body, cites: ids))
    }
}

// MARK: sum up a folder

enum AISumUp {
    static let total = 24_000
    /// One part's characters: the whole on an account; on the phone's own
    /// model, what fits its 4,096-token window with room to answer.
    static let accountPart = 24_000
    static let onDevicePart = 2_400
    static let maxOutput = 800

    /// Numbered ideas (from 1, across the parts) in parts of at most
    /// `partChars` characters, up to 24,000 in all; the last idea that
    /// would pass the total is cut.
    static func parts(_ ideas: [AIIdea], partChars: Int) -> [[(n: Int, idea: AIIdea)]] {
        var left = total
        var parts: [[(n: Int, idea: AIIdea)]] = []
        var part: [(n: Int, idea: AIIdea)] = []
        var size = 0
        for (k, idea) in ideas.enumerated() where left > 0 {
            var one = idea
            let titleChars = AIText.count(one.title)
            if titleChars >= left { break }
            one.text = AIText.prefix(one.text, min(left - titleChars, partChars - titleChars))
            let chars = titleChars + AIText.count(one.text)
            if size + chars > partChars, !part.isEmpty {
                parts.append(part)
                part = []
                size = 0
            }
            part.append((k + 1, one))
            size += chars
            left -= chars
        }
        if !part.isEmpty { parts.append(part) }
        return parts
    }

    static func payload(folder: String, part: [(n: Int, idea: AIIdea)]) -> AIPayload {
        var chars = 0
        let list: [AIJSON] = part.map { n, idea in
            chars += AIText.count(idea.title) + AIText.count(idea.text)
            return .make([("n", .number(Double(n))), ("title", .string(idea.title)), ("text", .string(idea.text))])
        }
        return AIPayload(input: AIJSON.make([("folder", .string(folder)), ("ideas", .array(list))]).text,
                         ideas: part.count, characters: chars)
    }

    static func check(_ answer: String, part: [(n: Int, idea: AIIdea)]) -> (AIVerdict, AIAsk.Answer?) {
        if AIText.trim(answer).isEmpty { return (.empty, nil) }
        guard let json = AIJSON.answer(answer), let text = json["summary"]?.string, let cites = json["cites"]?.array else {
            return (.unreadable, nil)
        }
        let numbers = cites.compactMap(\.int)
        guard numbers.count == cites.count else { return (.unreadable, nil) }
        let body = AIText.trim(text)
        if body.isEmpty { return (.empty, nil) }
        let map = Dictionary(uniqueKeysWithValues: part.map { ($0.n, $0.idea.id) })
        guard let ids = AICitations.resolve(text: body, cites: numbers, sent: map) else { return (.badCitation, nil) }
        return (.ok, AIAsk.Answer(text: body, cites: ids))
    }
}

// MARK: suggested links and resurfacing, on the phone only

/// An idea with its place in the index: its vector when the table covers
/// its words, else nil (IdeaEchoes' match stands in).
struct AINeighbour {
    var id: String
    var text: String
    var made: Double
    var vector: [Float]?
}

enum AILinks {
    static let limit = 5
    /// How close in meaning a candidate must be.
    static let close = 0.5

    static func cosine(_ a: [Float], _ b: [Float]) -> Double {
        var s = 0.0
        for k in 0..<min(a.count, b.count) { s += Double(a[k]) * Double(b[k]) }
        return s
    }

    /// Older ideas close in meaning, best first (then newer, then by id).
    static func byMeaning(_ vector: [Float], current: String?, made: Double, ideas: [AINeighbour],
                          except: Set<String>, limit: Int, close: Double = close) -> [String] {
        let scored: [(Double, AINeighbour)] = ideas.compactMap { n in
            guard n.id != current, !except.contains(n.id), n.made <= made, let v = n.vector else { return nil }
            let s = cosine(vector, v)
            return s >= close ? (s, n) : nil
        }
        return scored.sorted { a, b in
            if abs(a.0 - b.0) > 1e-9 { return a.0 > b.0 }
            if a.1.made != b.1.made { return a.1.made > b.1.made }
            return a.1.id < b.1.id
        }.prefix(limit).map(\.1.id)
    }

    /// Candidates for links: by meaning first, then by uncommon shared words
    /// (IdeaEchoes); for a text the table does not cover (Arabic among
    /// them), IdeaEchoes alone.
    static func suggest(text: String, vector: [Float]?, current: String?, made: Double, ideas: [AINeighbour],
                        connected: Set<String> = [], limit: Int = limit) -> [String] {
        var out: [String] = []
        if let vector {
            out = byMeaning(vector, current: current, made: made, ideas: ideas, except: connected, limit: limit)
        }
        let echoes = IdeaEchoes.Index(ideas.map { IdeaEchoes.Idea(id: $0.id, text: $0.text, made: $0.made, key: $0.id) })
            .echoes(of: text, current: current, made: made, connected: connected, limit: limit)
        for id in echoes where out.count < limit && !out.contains(id) { out.append(id) }
        return out
    }
}

enum AIResurface {
    static let limit = 3
    static let day: Double = 86400

    /// Up to 3 older ideas close to the last day's captures (O5: off until
    /// turned on; Tides' deck shows them).
    static func pick(_ ideas: [AINeighbour], now: Double, limit: Int = limit) -> [String] {
        let recent = ideas.filter { $0.made > now - day }
        let older = ideas.filter { $0.made <= now - day }
        var best: [String: Double] = [:]
        for r in recent {
            guard let v = r.vector else { continue }
            for o in older {
                guard let w = o.vector else { continue }
                let s = AILinks.cosine(v, w)
                if s >= AILinks.close, s > (best[o.id] ?? -2) { best[o.id] = s }
            }
        }
        var out = older.filter { best[$0.id] != nil }.sorted { a, b in
            let x = best[a.id]!, y = best[b.id]!
            if abs(x - y) > 1e-9 { return x > y }
            if a.made != b.made { return a.made > b.made }
            return a.id < b.id
        }.prefix(limit).map(\.id)
        // a capture the table does not cover finds its older ideas by words
        let index = IdeaEchoes.Index(older.map { IdeaEchoes.Idea(id: $0.id, text: $0.text, made: $0.made, key: $0.id) })
        for r in recent where r.vector == nil {
            for id in index.echoes(of: r.text, current: nil, made: now, limit: limit)
            where out.count < limit && !out.contains(id) { out.append(id) }
        }
        return out
    }
}
