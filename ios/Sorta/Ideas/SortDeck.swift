import Foundation

// The rules under the sorting deck, "You were saying…" and tides (S06a), pure
// and Foundation-only (they build and pass on Linux too), and matching
// Android's ideas/SortDeck.kt case for case.

/// "You were saying…": an idea whose details stop mid-sentence (S06a A4).
enum IdeaStops {
    /// Marks that close a sentence: . ! ? … and their full-width and Arabic forms.
    static let closers: Set<Character> = [".", "!", "?", "…", "。", "！", "？", "؟"]
    /// What may follow a sentence's close and still leave it closed: spaces,
    /// closing quotes and closing brackets.
    static let trailing: Set<Character> = [" ", "\t", "\n", "\r", "\u{00A0}", "\"", "'", "\u{201D}", "\u{2019}",
                                           "\u{00BB}", ")", "]", "}", "\u{300D}", "\u{300F}"]

    /// Where the person stopped, as a UTF-16 offset into `body`: nil when
    /// the text is empty or ends a sentence; else `caret` held inside the
    /// text, or the text's end when no caret is known.
    static func stoppedAt(body: String, caret: Int?) -> Int? {
        guard let last = body.last(where: { !trailing.contains($0) }) else { return nil }
        if closers.contains(last) { return nil }
        let length: Int = body.utf16.count
        guard let caret else { return length }
        return min(max(caret, 0), length)
    }

    /// The idea to offer: the marked one changed most recently (ties: the
    /// first given), or nil when none is marked.
    static func offer<ID>(_ ideas: [(id: ID, stoppedAt: Int?, updatedAt: Date)]) -> ID? {
        var best: (id: ID, updatedAt: Date)?
        for idea in ideas where idea.stoppedAt != nil {
            if best == nil || idea.updatedAt > best!.updatedAt { best = (idea.id, idea.updatedAt) }
        }
        return best?.id
    }
}

/// How deep an idea's frost is (S06a A6): from its last opening, or its
/// making when it was never opened.
enum IdeaFrost {
    /// The days at which each step begins.
    static let steps: [Double] = [30, 90, 180]
    static let day: TimeInterval = 86_400

    /// 0 (none) to 3.
    static func level(openedAt: Date?, createdAt: Date, now: Date) -> Int {
        let days: Double = now.timeIntervalSince(openedAt ?? createdAt) / day
        return steps.filter { days >= $0 }.count
    }
}

/// The deck's order: its top is the card under the thumb (S06a A1).
struct SortDeck<ID: Hashable> {
    private(set) var order: [ID] = []

    var top: ID? { order.first }

    /// Follows To sort (`ids`, its order): the cards still there keep their
    /// order, new ones go on top in To sort's order, and gone ones leave.
    mutating func sync(_ ids: [ID]) {
        let here = Set(ids)
        let known = Set(order)
        let fresh: [ID] = ids.filter { !known.contains($0) }
        order = fresh + order.filter { here.contains($0) }
    }

    /// Kept for later: to the back.
    mutating func later(_ id: ID) {
        guard let at = order.firstIndex(of: id) else { return }
        order.append(order.remove(at: at))
    }

    /// Back on top (Undo of Later).
    mutating func putBack(_ id: ID) {
        order.removeAll { $0 == id }
        order.insert(id, at: 0)
    }
}

/// Where a flick of the card lands (S06a A1). Points are in one space, y
/// down.
enum DeckFlick {
    /// A drag shorter than this puts the card back.
    static let least: Double = 64
    /// A target counts this far outside its frame.
    static let slack: Double = 12
    /// Within this of straight up, the card goes to Later.
    static let upAngle: Double = 35
    /// How far the velocity carries the release point, in seconds.
    static let carry: Double = 0.15

    struct Target<ID> {
        let id: ID
        let minX: Double, minY: Double, maxX: Double, maxY: Double
    }

    enum Outcome<ID: Equatable>: Equatable {
        case back
        case later
        case folder(ID)
    }

    /// The point the card is flung to: where the finger let go, carried on
    /// by its velocity for `carry` seconds.
    static func landing(x: Double, y: Double, vx: Double, vy: Double) -> (x: Double, y: Double) {
        (x + vx * carry, y + vy * carry)
    }

    /// From the card's start (`fromX`, `fromY`) to where it lands.
    static func outcome<ID: Equatable>(fromX: Double, fromY: Double, toX: Double, toY: Double,
                                       targets: [Target<ID>]) -> Outcome<ID> {
        let dx: Double = toX - fromX, dy: Double = toY - fromY
        guard (dx * dx + dy * dy).squareRoot() >= least else { return .back }
        var best: (id: ID, distance: Double)?
        for t in targets {
            let outX: Double = max(t.minX - toX, 0, toX - t.maxX)
            let outY: Double = max(t.minY - toY, 0, toY - t.maxY)
            let out: Double = (outX * outX + outY * outY).squareRoot()
            guard out <= slack else { continue }
            let cx: Double = (t.minX + t.maxX) / 2, cy: Double = (t.minY + t.maxY) / 2
            let distance: Double = ((toX - cx) * (toX - cx) + (toY - cy) * (toY - cy)).squareRoot()
            if best == nil || distance < best!.distance { best = (t.id, distance) }
        }
        if let best { return .folder(best.id) }
        // the angle from straight up (0, -1)
        let angle: Double = atan2(abs(dx), -dy) * 180 / Double.pi
        return dy < 0 && angle <= upAngle ? .later : .back
    }
}

/// The folders round the deck (S06a A1, A2).
enum DeckFolders {
    /// The most-used folders first: most ideas filed directly in them, then
    /// by name, then in the store's order; at most `slots`.
    static func pick<ID>(_ folders: [(id: ID, name: String, count: Int)], slots: Int) -> [ID] {
        let ranked = folders.enumerated().sorted { a, b in
            if a.element.count != b.element.count { return a.element.count > b.element.count }
            let order = a.element.name.localizedStandardCompare(b.element.name)
            if order != .orderedSame { return order == .orderedAscending }
            return a.offset < b.offset
        }
        return ranked.prefix(max(slots, 0)).map(\.element.id)
    }

    /// How many targets fit: `columns` columns of `height`, a target and its
    /// gap being `row` tall, at most `cap`.
    static func slots(height: Double, row: Double, columns: Int, cap: Int) -> Int {
        guard row > 0, height > 0 else { return 0 }
        return min(Int((height / row).rounded(.down)) * max(columns, 0), cap)
    }
}

/// The weekly low tide (S06a A7).
enum LowTide {
    static let week: TimeInterval = 7 * 86_400
    static let size = 3

    /// Due on the first open 7 days or more after the last tide. With none
    /// yet it is not due: the first open starts the clock.
    static func due(last: Date?, now: Date) -> Bool {
        guard let last else { return false }
        return now.timeIntervalSince(last) >= week
    }

    /// The tide: up to three frosted ideas, the longest unopened first
    /// (ties: the first given).
    static func pick<ID>(_ ideas: [(id: ID, openedAt: Date?, createdAt: Date)], now: Date) -> [ID] {
        ideas.enumerated()
            .filter { IdeaFrost.level(openedAt: $0.element.openedAt, createdAt: $0.element.createdAt, now: now) > 0 }
            .sorted { a, b in
                let ta: Date = a.element.openedAt ?? a.element.createdAt
                let tb: Date = b.element.openedAt ?? b.element.createdAt
                return ta != tb ? ta < tb : a.offset < b.offset
            }
            .prefix(size)
            .map(\.element.id)
    }
}
