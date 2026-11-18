import Foundation

// MARK: - Embeddings on the phone (S08 A7)
//
// One fixed table, the same file in both apps: Model2Vec potion-base-4M (MIT),
// int8 with one scale per row (ai/embeddings). The tokeniser and pooling
// follow ai/embeddings/reference.py step for step, with no ML runtime, so
// the same text gives the same vector here and on Android. Nothing is
// trained, and vectors never leave the phone.

final class AIEmbeddings {
    static let unk = 1
    static let maxTokens = 512
    static let maxWord = 100

    let rows: Int
    let dims: Int
    private let scales: [Float]
    private let values: [Int8]
    /// Keyed by scalar values, not String, so lookups never match a token
    /// by canonical equivalence (Kotlin's strings do not).
    private let vocab: [[UInt32]: Int]

    init(table: Data, vocab text: String) throws {
        let bytes = [UInt8](table)
        func u32(_ at: Int) -> Int {
            Int(bytes[at]) | Int(bytes[at + 1]) << 8 | Int(bytes[at + 2]) << 16 | Int(bytes[at + 3]) << 24
        }
        guard bytes.count >= 16, bytes[0..<4].elementsEqual(Array("SRTE".utf8)), u32(4) == 1 else { throw AIError.provider(.modelUnavailable) }
        rows = u32(8)
        dims = u32(12)
        guard bytes.count == 16 + rows * 4 + rows * dims else { throw AIError.provider(.modelUnavailable) }
        var scales = [Float](repeating: 0, count: rows)
        for r in 0..<rows { scales[r] = Float(bitPattern: UInt32(truncatingIfNeeded: u32(16 + r * 4))) }
        self.scales = scales
        values = bytes[(16 + rows * 4)...].map { Int8(bitPattern: $0) }
        var lines = text.components(separatedBy: "\n")
        if lines.last == "" { lines.removeLast() }
        guard lines.count == rows else { throw AIError.provider(.modelUnavailable) }
        var map: [[UInt32]: Int] = [:]
        map.reserveCapacity(rows)
        for (i, token) in lines.enumerated() { map[token.unicodeScalars.map(\.value)] = i }
        vocab = map
    }

    /// ai/embeddings/potion-base-4M under `root` (the app's resources, or
    /// the repository's ai/ in the Linux suites).
    static func load(root: URL) throws -> AIEmbeddings {
        let dir = root.appendingPathComponent("potion-base-4M")
        let table = try Data(contentsOf: dir.appendingPathComponent("potion-base-4M-int8.bin"))
        let vocab = try String(contentsOf: dir.appendingPathComponent("vocab.txt"), encoding: .utf8)
        return try AIEmbeddings(table: table, vocab: vocab)
    }

    // MARK: the tokeniser (BERT's uncased normaliser, pre-tokeniser and WordPiece)

    private static func isWhitespace(_ s: Unicode.Scalar) -> Bool { AIText.isSpace(s) }

    private static func isControl(_ s: Unicode.Scalar) -> Bool {
        if s == "\t" || s == "\n" || s == "\r" { return false }
        switch s.properties.generalCategory {
        case .control, .format, .unassigned, .privateUse, .surrogate: return true
        default: return false
        }
    }

    private static func isHan(_ v: UInt32) -> Bool {
        (0x4E00...0x9FFF).contains(v) || (0x3400...0x4DBF).contains(v) || (0x20000...0x2A6DF).contains(v)
            || (0x2A700...0x2B73F).contains(v) || (0x2B740...0x2B81F).contains(v) || (0x2B920...0x2CEAF).contains(v)
            || (0xF900...0xFAFF).contains(v) || (0x2F800...0x2FA1F).contains(v)
    }

    private static func isPunctuation(_ s: Unicode.Scalar) -> Bool {
        let v = s.value
        if (33...47).contains(v) || (58...64).contains(v) || (91...96).contains(v) || (123...126).contains(v) { return true }
        switch s.properties.generalCategory {
        case .connectorPunctuation, .dashPunctuation, .closePunctuation, .finalPunctuation, .initialPunctuation,
             .otherPunctuation, .openPunctuation: return true
        default: return false
        }
    }

    static func normalize(_ text: String) -> [Unicode.Scalar] {
        var cleaned = String.UnicodeScalarView()
        for s in text.unicodeScalars {
            if s.value == 0 || s.value == 0xFFFD || isControl(s) { continue }
            if isWhitespace(s) { cleaned.append(" "); continue }
            if isHan(s.value) { cleaned.append(" "); cleaned.append(s); cleaned.append(" "); continue }
            cleaned.append(s)
        }
        let decomposed = String(cleaned).decomposedStringWithCanonicalMapping
        var stripped = String.UnicodeScalarView()
        for s in decomposed.unicodeScalars where s.properties.generalCategory != .nonspacingMark { stripped.append(s) }
        return Array(String(stripped).lowercased().unicodeScalars)
    }

    static func preTokenize(_ scalars: [Unicode.Scalar]) -> [[UInt32]] {
        var words: [[UInt32]] = []
        var word: [UInt32] = []
        for s in scalars {
            if isWhitespace(s) {
                if !word.isEmpty { words.append(word); word = [] }
            } else if isPunctuation(s) {
                if !word.isEmpty { words.append(word); word = [] }
                words.append([s.value])
            } else {
                word.append(s.value)
            }
        }
        if !word.isEmpty { words.append(word) }
        return words
    }

    private static let continuing: [UInt32] = [0x23, 0x23]

    func wordPiece(_ word: [UInt32]) -> [Int] {
        if word.count > Self.maxWord { return [Self.unk] }
        var ids: [Int] = []
        var start = 0
        while start < word.count {
            var end = word.count
            var found: Int?
            while start < end {
                let piece = start == 0 ? Array(word[start..<end]) : Self.continuing + word[start..<end]
                if let id = vocab[piece] { found = id; break }
                end -= 1
            }
            guard let id = found else { return [Self.unk] }
            ids.append(id)
            start = end
        }
        return ids
    }

    /// The ids pooled: [UNK] dropped, the first 512 kept.
    func tokenIDs(_ text: String) -> [Int] {
        var ids: [Int] = []
        for word in Self.preTokenize(Self.normalize(text)) {
            ids += wordPiece(word)
        }
        return Array(ids.filter { $0 != Self.unk }.prefix(Self.maxTokens))
    }

    /// The mean of the rows, scaled to length 1; nil with no ids.
    func vector(ids: [Int]) -> [Double]? {
        guard !ids.isEmpty else { return nil }
        var total = [Double](repeating: 0, count: dims)
        for id in ids {
            let s = Double(scales[id])
            let base = id * dims
            for d in 0..<dims { total[d] += Double(values[base + d]) * s }
        }
        let n = Double(ids.count)
        let mean = total.map { $0 / n }
        let length = mean.reduce(0) { $0 + $1 * $1 }.squareRoot()
        guard length > 0 else { return nil }
        return mean.map { $0 / length }
    }

    /// A text's vector, or nil when the table does not cover it (then
    /// IdeaEchoes' match stands in).
    func vector(_ text: String) -> [Double]? {
        guard Self.covered(text) else { return nil }
        return vector(ids: tokenIDs(text))
    }

    /// At least half of the text's letters are Latin (U+0041 to U+024F).
    static func covered(_ text: String) -> Bool {
        var letters = 0, latin = 0
        for s in text.unicodeScalars {
            switch s.properties.generalCategory {
            case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter:
                letters += 1
                if (0x41...0x24F).contains(s.value) { latin += 1 }
            default: break
            }
        }
        return letters > 0 && 2 * latin >= letters
    }
}

// MARK: - The index: derived from the ideas, rebuilt off the main thread
//
// It lives in the app's own support folder, is excluded from backups, and
// is never in a capsule (SortaCapsule writes the ideas, not this file).

final class AIIndex {
    struct Entry: Equatable {
        var id: String
        var vector: [Float]?
    }

    let file: URL

    init(file: URL) { self.file = file }

    /// Application Support/ai-index.bin.
    static func standard() -> AIIndex {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return AIIndex(file: dir.appendingPathComponent("ai-index.bin"))
    }

    /// Every idea's vector, from its title and text.
    static func build(_ ideas: [AIIdea], embeddings: AIEmbeddings) throws -> [Entry] {
        try AIThread.requireBackground()
        return ideas.map { idea in
            Entry(id: idea.id, vector: embeddings.vector(idea.title + "\n" + idea.text).map { $0.map(Float.init) })
        }
    }

    /// Rebuilds the file from a snapshot of the store's ideas.
    @discardableResult
    func rebuild(_ ideas: [AIIdea], embeddings: AIEmbeddings) throws -> [Entry] {
        let entries = try Self.build(ideas, embeddings: embeddings)
        var data = Data("SRTI".utf8)
        func put(_ v: UInt32) { withUnsafeBytes(of: v.littleEndian) { data.append(contentsOf: $0) } }
        put(UInt32(entries.count))
        for e in entries {
            let id = Array(e.id.utf8)
            put(UInt32(id.count))
            data.append(contentsOf: id)
            put(UInt32(e.vector?.count ?? 0))
            for f in e.vector ?? [] { put(f.bitPattern) }
        }
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: file, options: .atomic)
        #if os(iOS) || os(macOS)
        var url = file
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
        #endif
        return entries
    }

    func load() -> [Entry]? {
        guard let data = try? Data(contentsOf: file), data.count >= 8, data.prefix(4) == Data("SRTI".utf8) else { return nil }
        let b = [UInt8](data)
        var at = 4
        func get() -> Int? {
            guard at + 4 <= b.count else { return nil }
            defer { at += 4 }
            return Int(b[at]) | Int(b[at + 1]) << 8 | Int(b[at + 2]) << 16 | Int(b[at + 3]) << 24
        }
        guard let count = get() else { return nil }
        var out: [Entry] = []
        for _ in 0..<count {
            guard let n = get(), at + n <= b.count else { return nil }
            let id = String(decoding: b[at..<(at + n)], as: UTF8.self)
            at += n
            guard let dims = get() else { return nil }
            var v: [Float] = []
            for _ in 0..<dims {
                guard let bits = get() else { return nil }
                v.append(Float(bitPattern: UInt32(bits)))
            }
            out.append(Entry(id: id, vector: dims == 0 ? nil : v))
        }
        return out
    }
}
