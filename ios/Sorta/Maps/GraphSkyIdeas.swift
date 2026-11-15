import Foundation

// S06b: what the Space map and the share sheet work out from the ideas
// alone - a comet's tail by how long it has waited, the settle when it is
// filed, a folder's words and the loose ideas that lean towards it, the
// moons of recent days, and where a shared idea came from. Foundation only,
// so the same rules are tested without a scene (SkyIdeasTests). Android's
// map/SkyIdeas.kt has the same numbers.

/// A comet's tail grows with the time since the idea was caught, in four
/// steps, the longest the tail Space always drew: so it never reaches past
/// what the map already frames and culls by.
nonisolated enum CometTail {
    static let day: TimeInterval = 86_400
    /// Under a day, a week, a month, and older: the share of the full tail.
    static let steps: [(below: TimeInterval, length: Float)] = [(day, 0.35), (7 * day, 0.6), (30 * day, 0.8)]
    static let longest: Float = 1

    /// The share of the full tail for an idea `age` seconds old; one caught
    /// in the future (a clock put back) counts as new.
    static func length(age: TimeInterval) -> Float {
        for step in steps where age < step.below { return step.length }
        return longest
    }

    static func length(created: Date, now: Date) -> Float {
        length(age: now.timeIntervalSince(created))
    }
}

/// Filing a comet: it eases from where it was into its place in the
/// folder's system, over a short time, fast then slow.
nonisolated enum CometSettle {
    static let duration: Double = 0.9

    /// How far along, 0 to 1, after `elapsed` seconds (ease-out cubic).
    static func progress(_ elapsed: Double) -> Float {
        let t: Double = min(max(elapsed / duration, 0), 1)
        let rest: Double = 1 - t
        return Float(1 - rest * rest * rest)
    }

    static func place(from: SIMD3<Float>, to: SIMD3<Float>, elapsed: Double) -> SIMD3<Float> {
        from + (to - from) * progress(elapsed)
    }
}

/// A folder's own words (NoteFolder.words): at most five, each one word.
nonisolated enum FolderWords {
    static let most = 5

    /// The words kept from what was typed: split at anything that is not a
    /// letter or a digit, repeats dropped (case and accents aside), the
    /// first five, each as typed.
    static func parse(_ text: String) -> [String] {
        var kept: [String] = []
        var seen = Set<String>()
        for word in split(text) where kept.count < most {
            if seen.insert(fold(word)).inserted { kept.append(word) }
        }
        return kept
    }

    /// The text's whole words, as typed.
    static func split(_ text: String) -> [String] {
        var words: [String] = []
        var current: String = ""
        for ch in text {
            if ch.isLetter || ch.isNumber {
                current.append(ch)
            } else if !current.isEmpty {
                words.append(current)
                current = ""
            }
        }
        if !current.isEmpty { words.append(current) }
        return words
    }

    /// A word with case and accents set aside: "Café" and "cafe" match.
    static func fold(_ word: String) -> String {
        word.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()
    }

    /// The folded whole words of a text.
    static func folded(_ text: String) -> Set<String> {
        Set(split(text).map(fold))
    }
}

/// A loose idea leaning towards a folder whose words it shares
/// (plugins plan, idea 3). Nothing is filed and nothing is learnt: the
/// lean is worked out again from the words each time.
nonisolated enum Leaning {
    /// How far of the way to the folder a leaning comet is drawn.
    static let pull: Float = 0.3
    /// The nearest a leaning comet comes to its folder, in the folder's
    /// radii: nearer, the folder's disc would take the comet's taps.
    static let keep: Float = 3

    /// Where a comet at `own` leaning to a folder at `folder`, `size` in
    /// radius, is drawn: `pull` of the way there, but never within `keep`
    /// radii of it, pushed out along the line it came in on (Android's
    /// Leaning.place).
    static func place(own: SIMD3<Float>, folder: SIMD3<Float>, size: Float) -> SIMD3<Float> {
        let pulled: SIMD3<Float> = own + (folder - own) * pull
        let least: Float = max(size, 0) * keep
        let away: SIMD3<Float> = pulled - folder
        let gap: Float = (away * away).sum().squareRoot()
        if gap >= least { return pulled }
        let from: SIMD3<Float> = own - folder
        let way: SIMD3<Float>
        if gap > 1e-5 { way = away / gap }
        else if (from * from).sum() > 1e-10 { way = from / (from * from).sum().squareRoot() }
        else { way = SIMD3<Float>(1, 0, 0) }
        return folder + way * least
    }

    struct Folder {
        let id: UUID
        let name: String
        let words: [String]
    }

    struct Lean: Equatable {
        let folder: UUID
        /// The first of the folder's words the idea shares, as the folder
        /// has it: "garden" in "Shares 'garden' with Raised beds".
        let word: String
        let shared: Int
    }

    /// The folder a loose idea leans to: the one sharing most of its words,
    /// then the one it links to most (`links`, by folder), then by name.
    /// Nil when it shares none.
    static func choose(title: String, body: String, folders: [Folder], links: [UUID: Int]) -> Lean? {
        let mine: Set<String> = FolderWords.folded(title).union(FolderWords.folded(body))
        guard !mine.isEmpty else { return nil }
        var best: (lean: Lean, links: Int, name: String)?
        for folder in folders {
            var shared: Int = 0
            var first: String?
            for word in folder.words where mine.contains(FolderWords.fold(word)) {
                shared += 1
                if first == nil { first = word }
            }
            guard shared > 0, let word = first else { continue }
            let here = (lean: Lean(folder: folder.id, word: word, shared: shared), links: links[folder.id] ?? 0,
                        name: FolderWords.fold(folder.name))
            guard let now = best else {
                best = here
                continue
            }
            if here.lean.shared != now.lean.shared {
                if here.lean.shared > now.lean.shared { best = here }
            } else if here.links != now.links {
                if here.links > now.links { best = here }
            } else if here.name != now.name {
                if here.name < now.name { best = here }
            } else if here.lean.folder.uuidString < now.lean.folder.uuidString {
                best = here
            }
        }
        return best?.lean
    }

    /// Every loose idea's lean. `notes`: each idea's id, title, text and
    /// folder (nil: loose); `edges`: the links. A filed idea never leans.
    static func leans(notes: [(id: UUID, title: String, body: String, folder: UUID?)], folders: [Folder],
                      edges: [(UUID, UUID)]) -> [UUID: Lean] {
        let worded: [Folder] = folders.filter { !$0.words.isEmpty }
        guard !worded.isEmpty else { return [:] }
        var folderOf: [UUID: UUID] = [:]
        for note in notes { if let f = note.folder { folderOf[note.id] = f } }
        var linksTo: [UUID: [UUID: Int]] = [:]
        for (a, b) in edges {
            if let f = folderOf[b] { linksTo[a, default: [:]][f, default: 0] += 1 }
            if let f = folderOf[a] { linksTo[b, default: [:]][f, default: 0] += 1 }
        }
        var out: [UUID: Lean] = [:]
        for note in notes where note.folder == nil {
            if let lean = choose(title: note.title, body: note.body, folders: worded, links: linksTo[note.id] ?? [:]) {
                out[note.id] = lean
            }
        }
        return out
    }
}

/// A night for each day (plugins plan, idea 7): recent days as moons, each
/// as full as that day was busy next to the busiest day shown.
nonisolated enum MoonDays {
    /// How far back the row scrolls.
    static let most = 60

    struct Day: Equatable {
        let start: Date
        let count: Int
        /// The share of the busiest day shown, 0 to 1.
        let fill: Double
        /// 0 new (no ideas), 1 crescent, 2 half, 3 gibbous, 4 full.
        let phase: Int
    }

    /// The last `count` days to `now`, newest first.
    static func days(created: [Date], now: Date, count: Int, calendar: Calendar) -> [Day] {
        let shown: Int = min(max(count, 1), most)
        let today: Date = calendar.startOfDay(for: now)
        var starts: [Date] = []
        for back in 0..<shown {
            starts.append(calendar.date(byAdding: .day, value: -back, to: today) ?? today)
        }
        var counts = [Int](repeating: 0, count: shown)
        for made in created {
            let day: Date = calendar.startOfDay(for: made)
            let back: Int = calendar.dateComponents([.day], from: day, to: today).day ?? -1
            if back >= 0 && back < shown { counts[back] += 1 }
        }
        let busiest: Int = max(counts.max() ?? 0, 1)
        return starts.enumerated().map { k, start in
            let fill: Double = Double(counts[k]) / Double(busiest)
            return Day(start: start, count: counts[k], fill: fill, phase: phase(count: counts[k], fill: fill))
        }
    }

    static func phase(count: Int, fill: Double) -> Int {
        guard count > 0 else { return 0 }
        return min(max(Int((fill * 3).rounded()), 1), 3)
    }

    /// The ideas caught on the day starting at `start`.
    static func lit(_ notes: [(id: UUID, created: Date)], day start: Date, calendar: Calendar) -> Set<UUID> {
        var out = Set<UUID>()
        for note in notes where calendar.isDate(note.created, inSameDayAs: start) { out.insert(note.id) }
        return out
    }
}

/// Where a shared idea came from (plugins plan, idea 10): the link's host
/// only, else the sending app's name; never the address itself.
nonisolated enum SharedFrom {
    static func mark(text: String, app: String?) -> String? {
        if let host = host(in: text) { return host }
        let name: String = (app ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? nil : String(name.prefix(40))
    }

    /// The first web link's host, without "www.".
    static func host(in text: String) -> String? {
        for piece in text.split(whereSeparator: { $0.isWhitespace }) {
            let word: String = String(piece)
            let lower: String = word.lowercased()
            guard lower.hasPrefix("http://") || lower.hasPrefix("https://") else { continue }
            guard let found = URL(string: word)?.host, !found.isEmpty else { continue }
            var host: String = found.lowercased()
            if host.hasPrefix("www.") { host.removeFirst(4) }
            return host
        }
        return nil
    }

    /// The words of a share, in order, for the sheet to keep or drop; a
    /// link stays one word.
    static func words(_ text: String) -> [String] {
        text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
    }

    /// What the sheet saves: the kept words in their order, a space apart.
    static func kept(_ words: [String], keep: Set<Int>) -> String {
        words.enumerated().filter { keep.contains($0.offset) }.map(\.element).joined(separator: " ")
    }
}

/// Ideas with the same source circle the first of them in Space: a pair or
/// a small group.
nonisolated enum SharedCircle {
    /// The most that circle one idea; more than that stay where they are.
    static let most = 6
    /// Turns a second, lively.
    static let spin: Float = 0.4

    struct Place: Equatable {
        /// The body circled.
        let leader: Int
        let slot: Int
        let of: Int
    }

    /// For each body (by index) with a source mark, the body it circles:
    /// the lowest index with the same mark leads; the next ones circle it.
    static func groups(_ marks: [Int: String]) -> [Int: Place] {
        var byMark: [String: [Int]] = [:]
        for (i, mark) in marks { byMark[FolderWords.fold(mark), default: []].append(i) }
        var out: [Int: Place] = [:]
        for (_, members) in byMark where members.count >= 2 {
            let sorted: [Int] = members.sorted()
            let leader: Int = sorted[0]
            let circling: [Int] = Array(sorted.dropFirst().prefix(most - 1))
            for (k, i) in circling.enumerated() {
                out[i] = Place(leader: leader, slot: k, of: circling.count)
            }
        }
        return out
    }

    /// Where a circling body sits from its leader at `time`, `radius` away.
    static func offset(_ place: Place, radius: Float, time: Double) -> SIMD3<Float> {
        let turn: Float = 2 * Float.pi * Float(place.slot) / Float(max(place.of, 1))
        let angle: Float = turn + Float(time.truncatingRemainder(dividingBy: 10_000)) * spin
        return SIMD3<Float>(cos(angle) * radius, sin(angle) * radius, 0)
    }
}
