import SwiftUI

// Ideas like this, Collide, Split and Join (S06c; the plugins plan's ideas 4,
// 5 and 12), on the screen: what the editor, the map and the board share
// (IdeaShapes, in the environment from IdeasRoot), the editor's ideas like
// this one, and the three sheets: the one-idea picker (Join with, Collide
// with), the split chooser and the collide card. The rules are IdeaEchoes'
// and IdeaReshape's, the store's NoteStore's reshape section, and every
// change is offered with Undo through IdeasFiling's notice. Android's
// IdeaShapes.kt is the same screens with the same words.

/// Which screen shows a sheet of IdeaShapes': the editor (its More menu, and
/// over it when it is a sheet itself) or Map mode (the map's options, its
/// gestures and the Collide tool).
enum ShapeHost: Equatable {
    case editor, map
}

/// A sheet of IdeaShapes' up: the one-idea picker for an idea (Join with, or
/// Collide with), the split chooser, or the collide card.
struct ShapeSheet: Identifiable, Equatable {
    enum Kind: Equatable {
        case pick(UUID, join: Bool)
        case split(UUID)
        /// `random`: Collide chose the pair, so the card offers another.
        case collide(UUID, UUID, random: Bool)
    }

    let id: UUID
    let kind: Kind
    let host: ShapeHost

    init(_ kind: Kind, host: ShapeHost, id: UUID = UUID()) {
        self.kind = kind
        self.host = host
        self.id = id
    }
}

/// What the editor, the map and the board share: the idea open in the
/// editor (`editing`) and the ideas like it (`glowing`), which the 3D map
/// rings and the board edges; the sheet up; and the actions, each told in
/// the notice with Undo. One for the window (IdeasRoot); views that can be
/// shown without it (the maps' preview) read it as optional, and show
/// nothing new then.
@MainActor @Observable
final class IdeaShapes {
    var editing: UUID?
    var glowing: [UUID] = []
    var sheet: ShapeSheet?
    /// The last pair Collide chose: never the next one, either way round.
    @ObservationIgnored private var last: (UUID, UUID)?

    func glows(_ id: UUID) -> Bool { glowing.contains(id) }

    private func line(_ id: UUID, _ notes: NoteStore) -> String {
        guard let note = notes.note(id) else { return IdeasWords.untitled }
        return IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled
    }

    /// Links the open idea to `other` by hand, with Undo; nothing when none
    /// is open, it is the same idea, or they are linked already.
    func link(_ other: UUID, notes: NoteStore, filing: IdeasFiling) {
        guard let from = editing, other != from, notes.note(other) != nil, !notes.isLinked(from, other) else { return }
        notes.link(from, other)
        glowing.removeAll { $0 == other }
        filing.notice = IdeasNotice(text: IdeasWords.echoesLinked(line(other, notes)), undo: .linked(from, other))
    }

    /// The split chooser for `id`, or, for an idea of one sentence, why not.
    func askSplit(_ id: UUID, from host: ShapeHost, notes: NoteStore, filing: IdeasFiling) {
        guard let note = notes.note(id) else { return }
        if IdeaReshape.sentences(title: note.title, body: note.body).count < 2 {
            filing.notice = IdeasNotice(text: IdeasWords.splitOne, undo: nil)
        } else {
            sheet = ShapeSheet(.split(id), host: host)
        }
    }

    func split(_ id: UUID, after k: Int, notes: NoteStore, filing: IdeasFiling) {
        sheet = nil
        guard let done = notes.split(id, after: k) else { return }
        filing.notice = IdeasNotice(text: IdeasWords.splitDone, undo: .reshaped(done))
    }

    func askJoin(_ id: UUID, from host: ShapeHost, notes: NoteStore) {
        guard notes.note(id) != nil else { return }
        sheet = ShapeSheet(.pick(id, join: true), host: host)
    }

    /// Joins `b` into `a`: `a` keeps its title, folder and place.
    func join(_ a: UUID, _ b: UUID, notes: NoteStore, filing: IdeasFiling) {
        sheet = nil
        guard let done = notes.join(a, with: b) else { return }
        filing.notice = IdeasNotice(text: IdeasWords.joinDone, undo: .reshaped(done))
    }

    /// Joins two ideas pushed together on the map: the older keeps its
    /// title, as the plan says.
    func joinOlderFirst(_ x: UUID, _ y: UUID, notes: NoteStore, filing: IdeasFiling) {
        guard let a = notes.note(x), let b = notes.note(y) else { return }
        if b.createdAt < a.createdAt {
            join(b.id, a.id, notes: notes, filing: filing)
        } else {
            join(a.id, b.id, notes: notes, filing: filing)
        }
    }

    func askCollide(_ id: UUID, from host: ShapeHost, notes: NoteStore) {
        guard notes.note(id) != nil else { return }
        sheet = ShapeSheet(.pick(id, join: false), host: host)
    }

    /// The collide card for these two.
    func collide(_ a: UUID, _ b: UUID, from host: ShapeHost, notes: NoteStore) {
        guard a != b, notes.note(a) != nil, notes.note(b) != nil else { return }
        sheet = ShapeSheet(.collide(a, b, random: false), host: host)
    }

    /// Collide: two far-apart ideas at random, never the last pair, or why
    /// there are none. Asked again from the card, the card stays up with
    /// the new pair.
    func collideAny(from host: ShapeHost, notes: NoteStore, filing: IdeasFiling) {
        var edges = Set<[UUID]>()
        for (a, b) in notes.allEdges() {
            edges.insert([a, b])
            edges.insert([b, a])
        }
        let places: [IdeaReshape.Place<UUID>] = notes.notes.map {
            IdeaReshape.Place(id: $0.id, top: notes.rootFolder(of: $0.folderId))
        }
        var random = SystemRandomNumberGenerator()
        guard let pair = IdeaReshape.collide(places, linked: { edges.contains([$0, $1]) }, last: last,
                                             using: &random) else {
            sheet = nil
            filing.notice = IdeasNotice(text: IdeasWords.collideNone, undo: nil)
            return
        }
        last = pair
        var keep = UUID()
        if let up = sheet, case .collide(_, _, true) = up.kind, up.host == host { keep = up.id }
        sheet = ShapeSheet(.collide(pair.0, pair.1, random: true), host: host, id: keep)
    }

    /// Saves what was written on the card: a new idea in To sort linked to
    /// both, with Undo. Nothing written, nothing made.
    func save(_ a: UUID, _ b: UUID, text: String, notes: NoteStore, filing: IdeasFiling) {
        guard let done = notes.collide(a, b, text: text) else { return }
        sheet = nil
        filing.notice = IdeasNotice(text: IdeasWords.collideSaved, undo: .reshaped(done))
    }

    func dismiss() { sheet = nil }
}

extension IdeasNotice {
    /// A notice of IdeaShapes' (a link, a split, a join, a new idea, or why
    /// none): the editor shows it too, since it is made while the editor
    /// covers the foot.
    var fromShapes: Bool {
        switch undo {
        case .some(.linked), .some(.reshaped): return true
        case .none: return IdeasWords.shapeNotices.contains(text)
        default: return false
        }
    }
}

extension IdeasWords {
    /// IdeaShapes' notices with no Undo.
    static var shapeNotices: [String] { [splitOne, collideNone] }
}

// MARK: - the ideas like this one, in the editor

/// Holds the echoes' index between asks: built once per change to the
/// ideas (the ideas it was built from, kept to compare).
@MainActor
final class EchoIndexCache {
    var basis: [Note]?
    var index: IdeaEchoes.Index<UUID>?
}

/// Finds the ideas like the one being written, for the editor's form:
/// asked 300 ms after the last change and handed to the map and the board
/// through `shapes`, so they glow there. On the form itself, not on the
/// section: a Form row that shows nothing is never made, so a section's own
/// task would never run while it is empty, and it starts empty.
struct EchoesFinder: ViewModifier {
    let current: UUID
    let title: String
    let text: String
    @Binding var echoes: [UUID]
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeaShapes.self) private var shapes: IdeaShapes?
    @State private var cache = EchoIndexCache()

    /// What the ask depends on: the words typed, and the ideas (compared
    /// cheaply while the store's array is the same one).
    private struct AskKey: Equatable {
        let current: UUID
        let title: String
        let text: String
        let ideas: [Note]
    }

    private var key: AskKey {
        AskKey(current: current, title: title, text: text, ideas: notes.notes)
    }

    func body(content: Content) -> some View {
        content
            .task(id: key) {
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard !Task.isCancelled else { return }
                find()
            }
            .onAppear { shapes?.editing = current }
            .onChange(of: current) { _, now in
                echoes = []
                shapes?.editing = now
                shapes?.glowing = []
            }
            .onDisappear {
                guard let shapes, shapes.editing == current else { return }
                shapes.editing = nil
                shapes.glowing = []
            }
    }

    private func find() {
        guard let note = notes.note(current) else { return }
        let all: [Note] = notes.notes
        let index: IdeaEchoes.Index<UUID>
        if let kept = cache.index, cache.basis == all {
            index = kept
        } else {
            index = IdeaEchoes.Index(all.map {
                IdeaEchoes.Idea(id: $0.id, text: $0.title + "\n" + $0.body, made: $0.createdAt.timeIntervalSince1970,
                                key: $0.id.uuidString.lowercased())
            })
            cache.index = index
            cache.basis = all
        }
        let connected: Set<UUID> = notes.connections(of: current).union(note.links)
        let found: [UUID] = index.echoes(of: title + "\n" + text, current: current,
                                         made: note.createdAt.timeIntervalSince1970, connected: connected)
        echoes = found
        if let shapes {
            shapes.editing = current
            shapes.glowing = found
        }
    }
}

extension View {
    /// The editor's form finds the ideas like `current` into `echoes`.
    func findsEchoes(current: UUID, title: String, text: String, into echoes: Binding<[UUID]>) -> some View {
        modifier(EchoesFinder(current: current, title: title, text: text, echoes: echoes))
    }
}

/// The ideas like the one being written, as a section of the editor's form
/// (every layout: it is the plain way to them, and what VoiceOver reaches),
/// as EchoesFinder found them. A tap links one, with Undo.
struct EchoesSection: View {
    let current: UUID
    @Binding var echoes: [UUID]
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(IdeaShapes.self) private var shapes: IdeaShapes?

    var body: some View {
        let shown: [Note] = echoes.compactMap { notes.note($0) }
        if !shown.isEmpty {
            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: WardSpace.s) {
                        ForEach(shown) { other in
                            chip(other)
                        }
                    }
                    // room for the chips' lift
                    .padding(.vertical, WardSpace.xs)
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("idea-echoes")
                .listRowBackground(Color.clear)
                .listRowInsets(PopOutField.rowInsets)
                .listRowSeparator(.hidden)
            } header: {
                WardSectionLabel(IdeasWords.echoesTitle)
            }
        }
    }

    private func chip(_ other: Note) -> some View {
        let line: String = IdeasDesk.firstLine(title: other.title, body: other.body) ?? IdeasWords.untitled
        let short: String = line.count > 40 ? String(line.prefix(40)) + "\u{2026}" : line
        return Button {
            if let shapes {
                shapes.editing = current
                shapes.link(other.id, notes: notes, filing: filing)
            } else if !notes.isLinked(current, other.id) {
                notes.link(current, other.id)
                filing.notice = IdeasNotice(text: IdeasWords.echoesLinked(line), undo: .linked(current, other.id))
            }
            echoes.removeAll { $0 == other.id }
        } label: {
            HStack(spacing: WardSpace.xs) {
                Image(systemName: "link")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
                Text(short)
                    .lineLimit(1)
            }
        }
        .buttonStyle(WardChipButtonStyle())
        .accessibilityLabel(line)
        .accessibilityHint(IdeasWords.echoesLinkHint)
        .accessibilityIdentifier("idea-echo")
    }
}

// MARK: - the sheets

extension View {
    /// The sheets IdeaShapes puts up from `host`: each screen that can ask
    /// for one attaches this once, so one sheet is up at a time, over the
    /// screen that asked.
    func ideaShapeSheets(_ host: ShapeHost) -> some View {
        modifier(IdeaShapeSheetsHost(host: host))
    }
}

private struct IdeaShapeSheetsHost: ViewModifier {
    let host: ShapeHost
    @Environment(IdeaShapes.self) private var shapes: IdeaShapes?

    func body(content: Content) -> some View {
        content
            .sheet(item: shown) { sheet in
                IdeaShapeSheetView(sheet: sheet)
            }
    }

    private var shown: Binding<ShapeSheet?> {
        Binding(get: {
            guard let up = shapes?.sheet, up.host == host else { return nil }
            return up
        }, set: { now in
            guard now == nil, let shapes, shapes.sheet?.host == host else { return }
            shapes.sheet = nil
        })
    }
}

/// One sheet of IdeaShapes', for the ideas it names; a sheet whose idea has
/// gone closes.
private struct IdeaShapeSheetView: View {
    let sheet: ShapeSheet
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(IdeaShapes.self) private var shapes: IdeaShapes?

    var body: some View {
        // the sheet up now, so "Try another pair" shows the new pair under the same sheet
        let live: ShapeSheet = shapes?.sheet.flatMap { $0.id == sheet.id ? $0 : nil } ?? sheet
        Group {
            switch live.kind {
            case let .pick(id, join):
                IdeaPickerSheet(id: id, title: join ? IdeasWords.shapeJoinTitle : IdeasWords.shapeCollideTitle) { other in
                    guard let shapes else { return }
                    if join {
                        shapes.join(id, other, notes: notes, filing: filing)
                    } else {
                        shapes.collide(id, other, from: live.host, notes: notes)
                    }
                }
            case let .split(id):
                if let note = notes.note(id) {
                    SplitChooser(note: note) { k in
                        shapes?.split(id, after: k, notes: notes, filing: filing)
                    }
                }
            case let .collide(a, b, random):
                if let x = notes.note(a), let y = notes.note(b) {
                    CollideCard(a: x, b: y, another: anotherPair(random, host: live.host)) { text in
                        shapes?.save(a, b, text: text, notes: notes, filing: filing)
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    /// "Try another pair", when Collide chose the pair.
    private func anotherPair(_ random: Bool, host: ShapeHost) -> (() -> Void)? {
        guard random else { return nil }
        let shapes = shapes
        let notes = notes
        let filing = filing
        return { shapes?.collideAny(from: host, notes: notes, filing: filing) }
    }
}

/// One other idea, found by its words (as the link picker), for Join with
/// and Collide with: a tap picks it.
private struct IdeaPickerSheet: View {
    let id: UUID
    let title: String
    let pick: (UUID) -> Void
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                TextField(text: $query, prompt: Text(IdeasWords.pickerSearch).foregroundStyle(Color.wardDisabledInk)) {
                    Text(IdeasWords.pickerSearch)
                }
                .wardFont(.body)
                .foregroundStyle(Color.wardInk)
                .tint(Color.wardPrimaryInk)
                .submitLabel(.search)
                .accessibilityIdentifier("picker-search")
                .wardField()
                .wardWellRow()
                let others: [Note] = notes.search(query).filter { $0.id != id }
                if others.isEmpty && !query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(IdeasWords.pickerNone(query))
                        .wardFont(.body)
                        .foregroundStyle(Color.wardInkSecondary)
                        .wardRowBackground()
                }
                ForEach(others) { note in
                    Button {
                        pick(note.id)
                    } label: {
                        VStack(alignment: .leading, spacing: WardSpace.xs) {
                            Text(IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled)
                                .wardFont(.body)
                                .foregroundStyle(Color.wardInk)
                                .lineLimit(2)
                            Text(notes.folder(note.folderId)?.name ?? IdeasWords.toSort)
                                .wardFont(.footnote)
                                .foregroundStyle(Color.wardInkSecondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("shape-pick")
                    .wardRowBackground()
                }
            }
            .wardForm()
            .accessibilityIdentifier("shape-picker")
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(IdeasWords.cancel) { dismiss() }
                        .buttonStyle(.wardQuiet)
                        .accessibilityIdentifier("shape-cancel")
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
    }
}

/// The split chooser: the idea's sentences, the title's first, each a row;
/// the person taps the last sentence of the first idea and sees both halves
/// under their labels; Split idea splits there. The last sentence can't end
/// the first idea, so it is never a choice.
private struct SplitChooser: View {
    let note: Note
    let split: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var after = 1

    var body: some View {
        let sentences: [IdeaReshape.Sentence] = IdeaReshape.sentences(title: note.title, body: note.body)
        NavigationStack {
            List {
                Section {
                    Text(IdeasWords.splitHint)
                        .wardFont(.subheadline)
                        .foregroundStyle(Color.wardInkSecondary)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                Section {
                    ForEach(Array(sentences.enumerated()), id: \.offset) { i, sentence in
                        if i + 1 <= after { row(sentence.text, k: i + 1, count: sentences.count) }
                    }
                } header: {
                    WardSectionLabel(IdeasWords.splitFirst)
                }
                Section {
                    ForEach(Array(sentences.enumerated()), id: \.offset) { i, sentence in
                        if i + 1 > after && i + 1 < sentences.count {
                            row(sentence.text, k: i + 1, count: sentences.count)
                        } else if i + 1 == sentences.count {
                            // the last sentence can't end the first idea: words, not a row that looks tappable
                            Text(sentence.text)
                                .wardFont(.body)
                                .foregroundStyle(Color.wardInk)
                                .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                                .padding(.horizontal, WardSpace.s)
                                .listRowBackground(Color.clear)
                        }
                    }
                } header: {
                    WardSectionLabel(IdeasWords.splitSecond)
                }
            }
            .wardForm()
            .accessibilityIdentifier("split-chooser")
            .navigationTitle(IdeasWords.splitTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(IdeasWords.cancel) { dismiss() }
                        .buttonStyle(.wardQuiet)
                        .accessibilityIdentifier("shape-cancel")
                }
                .sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .confirmationAction) {
                    Button(IdeasWords.splitConfirm) { split(after) }
                        .buttonStyle(.wardCompact)
                        .accessibilityIdentifier("split-confirm")
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
    }

    /// One sentence: the one the first idea ends with has the ink's edge
    /// and reads as chosen; the last can't be chosen.
    private func row(_ text: String, k: Int, count: Int) -> some View {
        let ends: Bool = k == after
        return Button {
            after = k
        } label: {
            Text(text)
                .wardFont(.body)
                .foregroundStyle(Color.wardInk)
                .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                .padding(.horizontal, WardSpace.s)
                .overlay {
                    if ends {
                        RoundedRectangle(cornerRadius: WardSpace.s, style: .continuous)
                            .strokeBorder(Color.wardPrimaryInk, lineWidth: 2)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(k >= count)
        .accessibilityAddTraits(ends ? [.isSelected] : [])
        // what "selected" means here, for VoiceOver (review N4)
        .accessibilityValue(ends ? IdeasWords.splitEndsHere : "")
        .accessibilityIdentifier("split-sentence")
        .wardRowBackground()
    }
}

/// The collide card: the two ideas face to face, "What if both?" and an
/// empty field. Save idea makes the new idea (in To sort, linked to both);
/// Cancel leaves nothing. When Collide chose the pair, `another` offers
/// another.
private struct CollideCard: View {
    let a: Note
    let b: Note
    let another: (() -> Void)?
    let save: (String) -> Void
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @FocusState private var writing: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: WardSpace.m) {
                    HStack(alignment: .top, spacing: WardSpace.m) {
                        face(a)
                        face(b)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    Text(IdeasWords.collideTitle)
                        .wardFont(.title3)
                        .foregroundStyle(Color.wardInk)
                        .accessibilityAddTraits(.isHeader)
                    TextField(text: $text, prompt: Text(IdeasWords.collideField).foregroundStyle(Color.wardDisabledInk),
                              axis: .vertical) { Text(IdeasWords.collideField) }
                        .lineLimit(5, reservesSpace: true)
                        .wardFont(.body)
                        .foregroundStyle(Color.wardInk)
                        .tint(Color.wardPrimaryInk)
                        .textInputAutocapitalization(.sentences)
                        .focused($writing)
                        .accessibilityIdentifier("collide-field")
                        .wardField()
                    if let another {
                        Button(IdeasWords.collideAnother) {
                            text = ""
                            another()
                        }
                        .buttonStyle(.wardSecondary)
                        .accessibilityIdentifier("collide-another")
                    }
                }
                .padding(.horizontal, WardSpace.gutter)
                .padding(.vertical, WardSpace.m)
                .frame(maxWidth: IdeasLayout.column)
                .frame(maxWidth: .infinity)
            }
            .background { WardBackground() }
            .accessibilityIdentifier("collide-card")
            .navigationTitle(IdeasWords.collideTool)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(IdeasWords.cancel) { dismiss() }
                        .buttonStyle(.wardQuiet)
                        .accessibilityIdentifier("shape-cancel")
                }
                .sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .confirmationAction) {
                    Button(IdeasWords.collideSave) { save(text) }
                        .buttonStyle(.wardCompact)
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .accessibilityIdentifier("collide-save")
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
        // while words are written, a swipe down keeps the card: Cancel is the way out
        .interactiveDismissDisabled(!text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    /// One of the two ideas: its first line, a few of its lines, its folder.
    private func face(_ note: Note) -> some View {
        let rest: String = note.body.trimmingCharacters(in: .whitespacesAndNewlines)
        return VStack(alignment: .leading, spacing: WardSpace.xs) {
            Text(IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled)
                .wardFont(.headline)
                .foregroundStyle(Color.wardInk)
                .lineLimit(3)
            if !rest.isEmpty && !note.title.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(rest)
                    .wardFont(.footnote)
                    .foregroundStyle(Color.wardInk)
                    .lineLimit(4)
            }
            Text(notes.folder(note.folderId)?.name ?? IdeasWords.toSort)
                .wardFont(.footnote)
                .foregroundStyle(Color.wardInkSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .wardCard(padding: WardSpace.m, lift: .low)
        .accessibilityElement(children: .combine)
    }
}
