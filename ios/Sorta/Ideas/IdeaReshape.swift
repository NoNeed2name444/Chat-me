import Foundation

/// Collide, split and join (S06c, the plugins plan's ideas 5 and 12): the
/// rules, with no store and no UI. NoteStore's reshape section and the
/// screens call them; Android's IdeaReshape.kt is the same rule, read from
/// the same cases (UnitTests/Suites/IdeaShapeCases.json).
nonisolated enum IdeaReshape {

    // MARK: collide

    /// One idea as a collision sees it: its id and its top folder (nil: To
    /// sort, which counts as a place of its own).
    struct Place<ID: Hashable> {
        let id: ID
        let top: ID?
    }

    /// Two ideas far apart: different top folders and no link between them,
    /// chosen at random, never `last` again (either way round). Nil when no
    /// other pair is far apart. Up to 200 random tries, then every pair.
    static func collide<ID: Hashable, R: RandomNumberGenerator>(
        _ ideas: [Place<ID>], linked: (ID, ID) -> Bool, last: (ID, ID)?, using random: inout R
    ) -> (ID, ID)? {
        let n = ideas.count
        guard n >= 2 else { return nil }
        func far(_ i: Int, _ j: Int) -> Bool {
            let a = ideas[i], b = ideas[j]
            guard a.top != b.top, !linked(a.id, b.id) else { return false }
            if let last, (last.0 == a.id && last.1 == b.id) || (last.0 == b.id && last.1 == a.id) { return false }
            return true
        }
        for _ in 0..<200 {
            let i = Int.random(in: 0..<n, using: &random)
            let j = Int.random(in: 0..<n, using: &random)
            if i != j, far(i, j) { return (ideas[i].id, ideas[j].id) }
        }
        var pairs: [(Int, Int)] = []
        for i in 0..<n {
            for j in (i + 1)..<n where far(i, j) { pairs.append((i, j)) }
        }
        guard !pairs.isEmpty else { return nil }
        let (i, j) = pairs[Int.random(in: 0..<pairs.count, using: &random)]
        return (ideas[i].id, ideas[j].id)
    }

    // MARK: split

    /// One sentence of an idea: its words, whether it is in the title, and
    /// where it ends in its field (after its closing marks and spaces).
    struct Sentence: Equatable {
        let text: String
        let inTitle: Bool
        let end: Int
    }

    /// The title's sentences, then the body's. A sentence ends after ".",
    /// "!", "?" or "…" (and any closing quote or bracket) before a space or
    /// the end, and at a line break; blank ones are left out.
    static func sentences(title: String, body: String) -> [Sentence] {
        pieces(Array(title), inTitle: true) + pieces(Array(body), inTitle: false)
    }

    private static let enders: Set<Character> = [".", "!", "?", "\u{2026}"]
    private static let closers: Set<Character> = ["\"", "'", "\u{201D}", "\u{2019}", ")", "]"]

    private static func pieces(_ chars: [Character], inTitle: Bool) -> [Sentence] {
        var out: [Sentence] = []
        var start = 0
        var i = 0
        func close(at end: Int) {
            let text = String(chars[start..<end]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { out.append(Sentence(text: text, inTitle: inTitle, end: end)) }
            start = end
        }
        while i < chars.count {
            let c = chars[i]
            if c == "\n" {
                close(at: i + 1)
                i += 1
            } else if enders.contains(c) {
                var j = i + 1
                while j < chars.count, enders.contains(chars[j]) || closers.contains(chars[j]) { j += 1 }
                if j == chars.count || chars[j] == " " || chars[j] == "\t" || chars[j] == "\n" {
                    while j < chars.count, chars[j] == " " || chars[j] == "\t" { j += 1 }
                    close(at: j)
                }
                i = j
            } else {
                i += 1
            }
        }
        if start < chars.count { close(at: chars.count) }
        return out
    }

    /// The two halves' words, cut after sentence `after` (1 up to the count
    /// less one); nil for any other cut.
    struct Halves: Equatable {
        let firstTitle: String
        let firstBody: String
        let secondTitle: String
        let secondBody: String
        /// The cut fell in the title, so the first half's title changed.
        let titleChanged: Bool
        /// The second half's title came from the body.
        let titleFromBody: Bool
    }

    static func cut(title: String, body: String, after k: Int) -> Halves? {
        let all = sentences(title: title, body: body)
        guard k >= 1, k < all.count else { return nil }
        let inTitle: Int = all.filter(\.inTitle).count
        let t = Array(title), b = Array(body)
        if k < inTitle {
            let end = all[k - 1].end
            return Halves(firstTitle: trim(t[0..<end]), firstBody: "",
                          secondTitle: trim(t[end...]), secondBody: trim(b[...]),
                          titleChanged: true, titleFromBody: false)
        }
        let start: Int = k == inTitle ? 0 : all[k - 1].end
        let firstBody: String = k == inTitle ? "" : trim(b[0..<start])
        let next = all[k]
        return Halves(firstTitle: trim(t[...]), firstBody: firstBody,
                      secondTitle: heading(next.text), secondBody: trim(b[next.end...]),
                      titleChanged: false, titleFromBody: true)
    }

    private static func trim(_ s: ArraySlice<Character>) -> String {
        String(s).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// A line made a title: a leading "#", "-", "*", "+" or ">" run and its
    /// space dropped.
    static func heading(_ line: String) -> String {
        var chars = Substring(line)
        let marks: Set<Character> = ["#", "-", "*", "+", ">"]
        let run = chars.prefix { marks.contains($0) }
        if !run.isEmpty, chars.dropFirst(run.count).first == " " {
            chars = chars.dropFirst(run.count)
        }
        return chars.trimmingCharacters(in: .whitespaces)
    }

    /// The hand links of the two halves: both keep the idea's own; the
    /// second also takes the ideas whose hand links named the idea and the
    /// ideas its title names in `[[Title]]`s; when the title changed, the
    /// first takes the ideas whose `[[Old title]]` named it. Each once,
    /// never a half itself.
    static func splitLinks<ID: Hashable>(own: [ID], heldBy: [ID], namedBy: [ID], titleChanged: Bool,
                                         secondTitleNames: [ID], first: ID, second: ID) -> (first: [ID], second: [ID]) {
        let one = unique(own + (titleChanged ? namedBy : []), leaving: [first, second])
        let two = unique(own + heldBy + secondTitleNames, leaving: [first, second])
        return (one, two)
    }

    // MARK: join

    /// The joined idea's words: the first's title (the second's when the
    /// first has none), then its body, the second's title and the second's
    /// body, blank parts left out, a blank line between.
    static func joined(firstTitle: String, firstBody: String, secondTitle: String, secondBody: String)
        -> (title: String, body: String) {
        let a = firstTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = a.isEmpty ? secondTitle.trimmingCharacters(in: .whitespacesAndNewlines) : a
        let parts = [firstBody, a.isEmpty ? "" : secondTitle, secondBody]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return (title, parts.joined(separator: "\n\n"))
    }

    /// The joined idea's hand links: the first's own, then the second's and
    /// the ideas that pointed at the second, leaving out the two, any the
    /// first holds already and any already connected another way
    /// (`connected`: the joined text's `[[Title]]`s and the first's
    /// backlinks). No link twice.
    static func joinLinks<ID: Hashable>(first: ID, second: ID, firstOwn: [ID], secondOwn: [ID],
                                        pointedAtSecond: [ID], connected: Set<ID>) -> [ID] {
        let kept = unique(firstOwn, leaving: [first, second])
        var have = Set(kept)
        var out = kept
        for id in secondOwn + pointedAtSecond where id != first && id != second && !connected.contains(id) {
            if have.insert(id).inserted { out.append(id) }
        }
        return out
    }

    private static func unique<ID: Hashable>(_ ids: [ID], leaving: Set<ID>) -> [ID] {
        var seen = leaving
        return ids.filter { seen.insert($0).inserted }
    }
}
