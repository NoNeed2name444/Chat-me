import SwiftUI

// S06b in the Space map and around it: which bodies lean and which circle
// (GraphSkyBodies, for GraphUniverseScene), the peek card's lean line and a
// shared idea's source mark, and every word these show or say (SkyWords).
// The rules themselves are GraphSkyIdeas.swift's.

/// The Universe's loose bodies that lean towards a folder whose words they
/// share, and those that circle the first idea shared from the same place,
/// by body index. Worked out from the ideas at each build; nothing is kept.
struct GraphSkyBodies {
    /// A comet's (or far cloud idea's) index to its folder's body index.
    var leanTo: [Int: Int] = [:]
    var circles: [Int: SharedCircle.Place] = [:]

    @MainActor
    static func make(plan: UniversePlan, store: NoteStore, edges: [(UUID, UUID)]) -> GraphSkyBodies {
        var out = GraphSkyBodies()
        var folderBody: [UUID: Int] = [:]
        var loose: [Int] = []
        for (i, body) in plan.bodies.enumerated() {
            switch body.role {
            case .galaxy, .star: folderBody[body.id] = i
            case .comet, .oort: loose.append(i)
            default: break
            }
        }
        guard !loose.isEmpty else { return out }
        var noteByID: [UUID: Note] = [:]
        for note in store.notes { noteByID[note.id] = note }
        let leans: [UUID: Leaning.Lean] = Leaning.leans(notes: lines(store), folders: worded(store), edges: edges)
        var marks: [Int: String] = [:]
        for i in loose {
            let id: UUID = plan.bodies[i].id
            if let lean = leans[id], let j = folderBody[lean.folder] { out.leanTo[i] = j }
            if let mark = noteByID[id]?.sharedFrom { marks[i] = mark }
        }
        out.circles = SharedCircle.groups(marks)
        return out
    }

    /// The lean of one loose idea, for its peek card; nil when it is filed
    /// or shares no folder's words.
    @MainActor
    static func lean(_ id: UUID, store: NoteStore) -> (lean: Leaning.Lean, folder: NoteFolder)? {
        guard let note = store.note(id), note.folderId == nil else { return nil }
        let folders: [Leaning.Folder] = worded(store)
        guard !folders.isEmpty else { return nil }
        var links: [UUID: Int] = [:]
        for (a, b) in store.allEdges() where a == id || b == id {
            if let other = store.note(a == id ? b : a), let folder = other.folderId { links[folder, default: 0] += 1 }
        }
        guard let lean = Leaning.choose(title: note.title, body: note.body, folders: folders, links: links),
              let folder = store.folder(lean.folder) else { return nil }
        return (lean, folder)
    }

    @MainActor
    private static func worded(_ store: NoteStore) -> [Leaning.Folder] {
        store.folders.compactMap { folder in
            folder.words.isEmpty ? nil : Leaning.Folder(id: folder.id, name: folder.name, words: folder.words)
        }
    }

    @MainActor
    private static func lines(_ store: NoteStore) -> [(id: UUID, title: String, body: String, folder: UUID?)] {
        store.notes.map { (id: $0.id, title: $0.title, body: $0.body, folder: $0.folderId) }
    }
}

/// The peek card's lean: "Shares 'garden' with Raised beds", and File there.
struct GraphSkyLean: Equatable {
    let word: String
    let folder: String
}

/// The lean line on a loose idea's peek card, with File there: the one
/// thing a lean offers, a tap, never a drag (S06-common: gestures).
struct GraphLeanRow: View {
    let lean: GraphSkyLean
    let file: () -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: WardSpace.s) {
                line
                Spacer(minLength: 0)
                button
            }
            VStack(alignment: .leading, spacing: WardSpace.s) {
                line
                button
            }
        }
    }

    private var line: some View {
        Label {
            Text(SkyWords.shares(lean.word, with: lean.folder))
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "arrow.up.right")
                .foregroundStyle(Color.wardGlyph)
        }
        .wardFont(.footnote)
        .foregroundStyle(Color.wardInkSecondary)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(SkyWords.shares(lean.word, with: lean.folder))
        .accessibilityIdentifier("graphPeekLean")
    }

    private var button: some View {
        Button(SkyWords.fileThere, systemImage: "folder", action: file)
            .buttonStyle(.wardQuiet)
            .lineLimit(1)
            .accessibilityLabel(SkyWords.fileThereSpoken(lean.folder))
            .accessibilityIdentifier("graphPeekFileThere")
    }
}

/// Where a shared idea came from: a glyph and "From example.com", plain
/// text, not a control (the editor, the peek card).
struct SharedFromMark: View {
    let mark: String

    var body: some View {
        Label {
            Text(SkyWords.from(mark))
        } icon: {
            Image(systemName: "link")
                .foregroundStyle(Color.wardGlyph)
        }
        .wardFont(.footnote)
        .foregroundStyle(Color.wardInkSecondary)
        .lineLimit(1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(SkyWords.fromSpoken(mark))
        .accessibilityIdentifier("sharedFromMark")
    }
}

/// Every word S06b shows or says, each keyed in Localizable.xcstrings,
/// after the ux-writing, better-writing and unslop passes (S06b-result).
enum SkyWords {
    // MARK: a folder's words (A3)
    static var folderWords: String { String(localized: "folder.words", defaultValue: "Folder words…") }
    static var folderWordsTitle: String { String(localized: "folder.words.title", defaultValue: "Folder words") }
    static var folderWordsPrompt: String { String(localized: "folder.words.prompt", defaultValue: "Up to 5 words") }
    static var save: String { String(localized: "sky.save", defaultValue: "Save") }
    static var folderWordsNote: String {
        String(localized: "folder.words.note",
               defaultValue: "In Space, ideas in To sort that use one of these words lean towards this folder.")
    }
    static func folderWordsKept(_ list: String) -> String {
        String(localized: "folder.words.kept %@", defaultValue: "Saves: \(list)")
    }
    /// What the words sheet says under the field, as it is typed.
    static func folderWordsFootnote(_ text: String) -> String {
        let kept: [String] = FolderWords.parse(text)
        guard !kept.isEmpty else { return folderWordsNote }
        return folderWordsNote + "\n" + folderWordsKept(kept.joined(separator: ", "))
    }

    // MARK: leaning (A4)
    static func shares(_ word: String, with folder: String) -> String {
        String(localized: "lean.shares %@ %@", defaultValue: "Shares ‘\(word)’ with \(folder)")
    }
    static var fileThere: String { String(localized: "lean.file", defaultValue: "File there") }
    static func fileThereSpoken(_ folder: String) -> String {
        String(localized: "lean.file.spoken %@", defaultValue: "File there, in \(folder)")
    }

    // MARK: the moons (A5, A6)
    static var days: String { String(localized: "moons.chip", defaultValue: "Days") }
    static var daysHint: String { String(localized: "moons.chip.hint", defaultValue: "Shows recent days as moons.") }
    static var recentDays: String { String(localized: "moons.title", defaultValue: "Recent days") }
    static var pickADay: String { String(localized: "moons.pick", defaultValue: "Choose a day to see its ideas.") }
    static var clear: String { String(localized: "moons.clear", defaultValue: "Clear") }
    static var clearSpoken: String { String(localized: "moons.clear.spoken", defaultValue: "Clear the chosen day") }
    static var moonHint: String { String(localized: "moons.moon.hint", defaultValue: "Shows that day’s ideas.") }
    /// "14 November, 3 ideas"; a day with none, "14 November, no ideas".
    static func moonSpoken(_ day: String, _ n: Int) -> String {
        if n == 0 { return String(localized: "moons.moon.none %@", defaultValue: "\(day), no ideas") }
        let count: String = IdeasWords.ideaCount(n)
        return String(localized: "moons.moon.spoken %@ %@", defaultValue: "\(day), \(count)")
    }

    // MARK: sharing (A7, A8)
    static var shareTitle: String { String(localized: "share.title", defaultValue: "New idea") }
    static var shareHint: String {
        String(localized: "share.hint", defaultValue: "Tap a word to leave it out. The idea goes in To sort.")
    }
    static var kept: String { String(localized: "share.word.kept", defaultValue: "Kept") }
    static var leftOut: String { String(localized: "share.word.out", defaultValue: "Left out") }
    static var keepAll: String { String(localized: "share.keepAll", defaultValue: "Keep all") }
    static var leaveAll: String { String(localized: "share.leaveAll", defaultValue: "Leave all out") }
    static func from(_ mark: String) -> String {
        String(localized: "shared.from %@", defaultValue: "From \(mark)")
    }
    static func fromSpoken(_ mark: String) -> String {
        String(localized: "shared.from.spoken %@", defaultValue: "Shared from \(mark)")
    }
}
