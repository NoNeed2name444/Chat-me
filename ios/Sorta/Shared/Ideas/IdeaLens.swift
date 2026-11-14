import Foundation

/// A lens over the map (S06d A1, plugins-plan idea 8): a question asked of
/// every idea, which the map answers by showing only the ideas that pass.
/// Pure; Android's IdeaLens is the same, case by case. Lenses are held while
/// Map mode is, never stored. `rawValue` names a lens in tests and in
/// accessibility identifiers ("map-lens-late-night").
enum IdeaLens: String, CaseIterable, Identifiable, Hashable {
    /// The title or the text has a question mark.
    case questions
    /// Caught from 11 PM up to 4 AM, in the phone's time zone.
    case lateNight = "late-night"
    /// The text (or, with no text, the title) stops mid-sentence
    /// (IdeaSentence).
    case unfinished
    /// No link either way: neither a hand-made link nor a `[[Title]]`.
    case alone

    var id: String { rawValue }

    /// The hours a late-night idea is caught in: from `lateFrom` up to, not
    /// including, `lateUntil`.
    static let lateFrom: Int = 23
    static let lateUntil: Int = 4

    /// How faint an idea the lenses leave out is drawn, as the filter's
    /// ghost (Android's MapFilterView.GHOST_ALPHA).
    static let fade: Double = 0.3

    static func isQuestion(title: String, body: String) -> Bool {
        title.contains("?") || body.contains("?")
    }

    /// `createdAt` falls from 11 PM up to 4 AM in `zone`.
    static func isLateNight(_ createdAt: Date, in zone: TimeZone) -> Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let hour: Int = calendar.component(.hour, from: createdAt)
        return hour >= lateFrom || hour < lateUntil
    }

    /// The text stops mid-sentence; an idea with no text is read by its
    /// title.
    static func isUnfinished(title: String, body: String) -> Bool {
        let blank: Bool = body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return IdeaSentence.endsMidSentence(blank ? title : body)
    }

    /// Every note in a link, from the store's edges (NoteStore.allEdges).
    static func linked(_ edges: [(UUID, UUID)]) -> Set<UUID> {
        var out = Set<UUID>(minimumCapacity: edges.count * 2)
        for (a, b) in edges {
            out.insert(a)
            out.insert(b)
        }
        return out
    }

    /// Whether a note passes, with `linked` every note in a link and `zone`
    /// the phone's.
    func passes(id: UUID, title: String, body: String, createdAt: Date,
                linked: Set<UUID>, zone: TimeZone) -> Bool {
        switch self {
        case .questions: return Self.isQuestion(title: title, body: body)
        case .lateNight: return Self.isLateNight(createdAt, in: zone)
        case .unfinished: return Self.isUnfinished(title: title, body: body)
        case .alone: return !linked.contains(id)
        }
    }
}

/// What a lens asks of a note: its id, words and when it was caught.
protocol IdeaLensNote {
    var id: UUID { get }
    var title: String { get }
    var body: String { get }
    var createdAt: Date { get }
}

extension IdeaLens {
    func passes<N: IdeaLensNote>(_ note: N, linked: Set<UUID>, zone: TimeZone) -> Bool {
        passes(id: note.id, title: note.title, body: note.body, createdAt: note.createdAt, linked: linked, zone: zone)
    }

    /// The ids of the `notes` that pass every lens `on`; nil with none on
    /// (everything shows).
    static func passing<N: IdeaLensNote>(_ on: Set<IdeaLens>, notes: [N], edges: [(UUID, UUID)],
                                         zone: TimeZone = .current) -> Set<UUID>? {
        guard !on.isEmpty else { return nil }
        let linked: Set<UUID> = linked(edges)
        return Set(notes.filter { n in on.allSatisfy { $0.passes(n, linked: linked, zone: zone) } }.map(\.id))
    }

    /// Each lens's chip count: how many notes show with that lens on beside
    /// those already `on` (for a lens that is on, how many show now).
    static func counts<N: IdeaLensNote>(_ on: Set<IdeaLens>, notes: [N], edges: [(UUID, UUID)],
                                        zone: TimeZone = .current) -> [IdeaLens: Int] {
        let linked: Set<UUID> = linked(edges)
        var out: [IdeaLens: Int] = [:]
        for lens in allCases {
            let with: Set<IdeaLens> = on.union([lens])
            out[lens] = notes.filter { n in with.allSatisfy { $0.passes(n, linked: linked, zone: zone) } }.count
        }
        return out
    }
}
