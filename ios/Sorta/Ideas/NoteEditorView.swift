import SwiftUI

/// One idea, to write in and to join up with the rest.
///
/// Writing `[[Title]]` in the details links to the idea of that title; while
/// typing one, the matching ideas are offered over the keyboard, and the
/// links appear underneath as they are typed. "Link to an idea…" joins two
/// ideas without writing anything. Tapping a linked idea opens it in place,
/// with Back to return. Changes save themselves a moment after typing stops,
/// and again on close.
///
/// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/NoteEditorView.swift:1-133 the holder, loading, saving and
/// the toolbar; :135-186 the form; :222-240 the folder chip; :242-299 the
/// suggestions, links and missing rows; :301-453 saving, following links and
/// [[ completion; :474-529 LinkPickerView; :563-582 Write / Read), in
/// Sorta's kit. Cut: the study `Store`, the kind chip (:206-220), Turn into
/// cards and its alert (:113-117, 455-471, 553-558), the page/idea body
/// heights (:49-51), NoteTone's folder colours and the kind glyphs, and every
/// literal size. Changed: the folder chip opens the folder chooser, not a
/// Picker menu, and files through the Sort screen, which offers Undo; the
/// hint drops its medical example; deleting asks first and then hands the
/// idea to the Sort screen, which offers Undo; a missing title makes an
/// idea, not a page.
struct NoteEditorView: View {
    /// Files the idea in a folder (nil: To sort), from the Sort screen, so
    /// it can offer Undo, as every filing does.
    let file: (UUID, UUID?) -> Void
    /// Deletes the idea, from the Sort screen, so it can offer Undo.
    let delete: (UUID) -> Void
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize

    /// The idea on screen. Following a link swaps it for another, and the
    /// ideas left behind are kept in `trail` for Back.
    @State private var current: UUID
    @State private var trail: [UUID] = []
    @State private var title = ""
    @State private var text = ""
    @State private var folderId: UUID?
    @State private var loaded = false
    /// Read the idea as Markdown instead of editing it; remembered.
    @AppStorage("notesReadAsMarkdown") private var reading = false
    @State private var linking = false
    @State private var choosingFolder = false
    @State private var confirmingDelete = false
    /// Which field has the caret, if any.
    @FocusState private var editing: EditorField?

    enum EditorField: Hashable { case title, body }

    init(noteID: UUID, file: @escaping (UUID, UUID?) -> Void, delete: @escaping (UUID) -> Void) {
        _current = State(initialValue: noteID)
        self.file = file
        self.delete = delete
    }

    var body: some View {
        NavigationStack {
            form
                .wardForm()
                // pinned over the foot of the page, except at the
                // accessibility sizes, where the bar would take a fifth of
                // the screen and cover the chip: there it is a row of the
                // page, under the chip (`form`)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if !typeSize.isAccessibilitySize { actionBar }
                }
                // no words in the bar: the title field under it names the
                // idea, and the strings list gives the bar none
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        if !trail.isEmpty {
                            Button {
                                goBack()
                            } label: {
                                Label(IdeasWords.backTo(previousTitle), systemImage: "chevron.backward")
                                    .labelStyle(.iconOnly)
                            }
                            .buttonStyle(.wardCircle)
                            .accessibilityIdentifier("editor-back")
                        }
                    }
                    .sharedBackgroundVisibility(.hidden)
                    ToolbarItem(placement: .confirmationAction) {
                        Button(IdeasWords.done) {
                            commit()
                            dismiss()
                        }
                        .buttonStyle(.wardCompact)
                        .accessibilityIdentifier("editor-done")
                    }
                    .sharedBackgroundVisibility(.hidden)
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button(IdeasWords.deleteIdea, systemImage: "trash", role: .destructive) {
                                confirmingDelete = true
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                        }
                        .menuStyle(.button)
                        .buttonStyle(.wardCircle)
                        .accessibilityLabel(IdeasWords.more)
                        .accessibilityIdentifier("editor-more")
                    }
                    .sharedBackgroundVisibility(.hidden)
                    ToolbarItemGroup(placement: .keyboard) {
                        if editing == .body && !reading && !suggestions.isEmpty {
                            suggestionBar
                        }
                    }
                    .sharedBackgroundVisibility(.hidden)
                }
                .confirmationDialog(IdeasWords.deleteIdeaTitle, isPresented: $confirmingDelete,
                                    titleVisibility: .visible) {
                    Button(IdeasWords.deleteIdea, role: .destructive) {
                        // saved as it stands first, so Undo brings back
                        // what was typed
                        commit()
                        loaded = false
                        delete(current)
                        dismiss()
                    }
                    Button(IdeasWords.cancel, role: .cancel) {}
                } message: {
                    Text(IdeasWords.deleteIdeaBody)
                }
                .sheet(isPresented: $linking) {
                    LinkPickerView(noteID: current)
                }
                .sheet(isPresented: $choosingFolder) {
                    FolderChooser(current: folderId, choose: { fileHere(in: $0) }, makeFolder: { name in
                        fileHere(in: notes.createFolder(name: name, parentId: nil).id)
                    })
                }
        }
        .frame(maxWidth: IdeasLayout.column)
        .presentationDetents([.large])
        .onAppear {
            if !loaded { load() }
        }
        .onDisappear { commit() }
        // saved a moment after typing stops, not on every key
        .task(id: draftKey) {
            guard loaded else { return }
            try? await Task.sleep(nanoseconds: 500_000_000)
            guard !Task.isCancelled else { return }
            commit()
        }
    }

    /// The idea itself: title and folder chip, the details, and its links.
    private var form: some View {
        Form {
            Section {
                // the title is a field you touch: a well pressed into the base
                TextField(text: $title, prompt: Text(IdeasWords.title).foregroundStyle(Color.wardDisabledInk),
                          axis: .vertical) { Text(IdeasWords.title) }
                    .wardFont(.title)
                    .foregroundStyle(Color.wardInk)
                    .tint(Color.wardPrimaryInk)
                    .focused($editing, equals: .title)
                    .accessibilityIdentifier("editor-title")
                    .wardField()
                    .wardWellRow()
                folderChip
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .listRowBackground(Color.clear)
                    .listRowInsets(PopOutField.rowInsets)
                    .listRowSeparator(.hidden)
                if typeSize.isAccessibilitySize {
                    actionControls
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .listRowBackground(Color.clear)
                        .listRowInsets(PopOutField.rowInsets)
                        .listRowSeparator(.hidden)
                }
            }

            Section {
                if reading {
                    NoteMarkdownView(text: text) { name in openNote(named: name) }
                        .frame(minHeight: IdeasLayout.bodyMinHeight, alignment: .topLeading)
                } else {
                    // the details are a reading surface - read, re-read and
                    // edited in place - so they sit flat on their tile
                    TextEditor(text: $text)
                        .wardFont(.body)
                        .foregroundStyle(Color.wardInk)
                        .tint(Color.wardPrimaryInk)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: IdeasLayout.bodyMinHeight)
                        .focused($editing, equals: .body)
                        .accessibilityLabel(IdeasWords.details)
                        .accessibilityIdentifier("editor-body")
                }
            } header: {
                WardSectionLabel(IdeasWords.details)
            } footer: {
                Text(IdeasWords.editorHint)
                    .wardFont(.footnote)
                    .foregroundStyle(Color.wardInkSecondary)
            }
            .wardRowBackground()

            linkedSection
                .wardRowBackground()

            if !backlinks.isEmpty {
                Section {
                    ForEach(backlinks, id: \.self) { id in
                        linkRow(id, how: isHandLinked(id) ? IdeasWords.howLinked : IdeasWords.howMentions)
                    }
                } header: {
                    WardSectionLabel(IdeasWords.linkedHere)
                }
                .wardRowBackground()
            }
        }
    }

    // MARK: the bottom bar and the chip

    /// Write / Read and Link to an idea…: in one line in the bar, or at the
    /// accessibility sizes each on its own line, in the page.
    private var actionControls: some View {
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: WardSpace.s))
            : AnyLayout(HStackLayout(spacing: WardSpace.m))
        return layout {
            NoteModePicker(reading: $reading)
            linkButton
                .accessibilityIdentifier("editor-link")
            if !typeSize.isAccessibilitySize { Spacer(minLength: 0) }
        }
    }

    /// Link to an idea…: at the accessibility sizes its words wrap, so the
    /// whole button stays on the screen.
    @ViewBuilder
    private var linkButton: some View {
        if typeSize.isAccessibilitySize {
            Button(IdeasWords.linkTo, action: startLinking)
                .buttonStyle(WrappingCompactButtonStyle())
        } else {
            Button(IdeasWords.linkTo, action: startLinking)
                .buttonStyle(.wardCompact)
        }
    }

    /// The bar above the home indicator and the keyboard, at the standard
    /// sizes.
    private var actionBar: some View {
        actionControls
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.s)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.wardBackground)
    }

    private func startLinking() {
        commit()
        linking = true
    }

    private var folderName: String {
        notes.folder(folderId)?.name ?? IdeasWords.toSort
    }

    private var folderChip: some View {
        Button {
            choosingFolder = true
        } label: {
            HStack(spacing: WardSpace.xs) {
                Image(systemName: "folder")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
                Text(folderName)
                    .lineLimit(1)
            }
        }
        .buttonStyle(WardChipButtonStyle())
        .accessibilityLabel(IdeasWords.chipSpoken(folderName))
        .accessibilityHint(IdeasWords.chipHint)
        .accessibilityIdentifier("editor-folder")
    }

    /// The ideas a half-typed `[[` could mean, over the keyboard.
    private var suggestionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: WardSpace.s) {
                ForEach(suggestions) { note in
                    Button(note.title) { complete(with: note.title) }
                        .buttonStyle(.wardCompact)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// The ideas this one links to. "Link to an idea…" is only here when
    /// there are none yet; otherwise it is in the bottom bar alone.
    private var linkedSection: some View {
        let out: [UUID] = outgoing
        let missing: [String] = unresolved
        let none: Bool = out.isEmpty && missing.isEmpty
        return Section {
            ForEach(out, id: \.self) { id in
                linkRow(id, how: isHandLinked(id) ? IdeasWords.howLinked : IdeasWords.howMentioned)
            }
            ForEach(missing, id: \.self) { name in
                missingRow(name)
            }
            if none {
                Button {
                    startLinking()
                } label: {
                    Label(IdeasWords.linkTo, systemImage: "link")
                        .foregroundStyle(Color.wardPrimaryInk)
                        .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        } header: {
            WardSectionLabel(IdeasWords.linkedIdeas)
        }
    }

    private func missingRow(_ name: String) -> some View {
        Button {
            makeIdea(named: name)
        } label: {
            HStack(spacing: WardSpace.m) {
                Image(systemName: "plus.circle")
                    .foregroundStyle(Color.wardPrimaryInk)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: WardSpace.xs) {
                    Text(name).wardFont(.body).foregroundStyle(Color.wardInk)
                    Text(IdeasWords.missing)
                        .wardFont(.footnote)
                        .foregroundStyle(Color.wardInkSecondary)
                }
                Spacer(minLength: 0)
            }
            .frame(minHeight: WardControlSize.target)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
    }

    // MARK: loading and saving

    private var draftKey: String {
        [title, text, folderId?.uuidString ?? ""].joined(separator: "\u{0}")
    }

    private var previousTitle: String {
        guard let id = trail.last, let note = notes.note(id) else { return IdeasWords.untitled }
        return IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled
    }

    private func load() {
        guard let note = notes.note(current) else { return }
        title = note.title
        text = note.body
        folderId = note.folderId
        loaded = true
    }

    /// Files the idea on screen through the Sort screen: what was typed is
    /// saved first, so the move keeps it, and the chip follows the store.
    private func fileHere(in folder: UUID?) {
        commit()
        file(current, folder)
        folderId = notes.note(current)?.folderId
    }

    private func commit() {
        guard loaded, var note = notes.note(current) else { return }
        note.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        note.body = text
        note.folderId = folderId
        notes.update(note)
    }

    // MARK: following links

    private func follow(_ id: UUID) {
        guard id != current else { return }
        commit()
        trail.append(current)
        current = id
        load()
    }

    private func goBack() {
        guard let previous = trail.popLast() else { return }
        commit()
        current = previous
        load()
    }

    /// A `[[Title]]` tapped while reading: that idea, or a new idea by that name.
    private func openNote(named name: String) {
        if let id = notes.titleIndex()[name.trimmingCharacters(in: .whitespaces).lowercased()] {
            follow(id)
        } else {
            makeIdea(named: name)
        }
    }

    private func makeIdea(named name: String) {
        let idea = notes.create(title: name, kind: .idea, folderId: folderId)
        follow(idea.id)
    }

    // MARK: what this idea is linked to, as typed

    /// Hand-made links, then the `[[Title]]`s in the text as it is now.
    private var outgoing: [UUID] {
        let explicit: [UUID] = notes.note(current)?.links ?? []
        let wiki: [UUID] = notes.resolve(wikiLinksIn: text)
        let both: [UUID] = explicit + wiki
        var seen = Set<UUID>([current])
        return both.filter { notes.note($0) != nil && seen.insert($0).inserted }
    }

    private var backlinks: [UUID] {
        let shown = Set(outgoing)
        return notes.backlinks(of: current).filter { !shown.contains($0) }
    }

    /// `[[Title]]`s that name no idea yet.
    private var unresolved: [String] {
        let index = notes.titleIndex()
        var seen = Set<String>()
        return NoteStore.wikiTitles(in: text).filter { title in
            index[title.lowercased()] == nil && seen.insert(title.lowercased()).inserted
        }
    }

    private func isHandLinked(_ id: UUID) -> Bool {
        notes.isLinked(current, id)
    }

    private func linkRow(_ id: UUID, how: String) -> some View {
        let note = notes.note(id)
        let named: String = note.map { IdeasDesk.firstLine(title: $0.title, body: "") ?? IdeasWords.untitled }
            ?? IdeasWords.missingIdea
        let hand: Bool = isHandLinked(id)
        return Button {
            follow(id)
        } label: {
            HStack(spacing: WardSpace.m) {
                VStack(alignment: .leading, spacing: WardSpace.xs) {
                    Text(named)
                        .wardFont(.body)
                        .foregroundStyle(Color.wardInk)
                        .lineLimit(2)
                    Text(how)
                        .wardFont(.footnote)
                        .foregroundStyle(Color.wardInkSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward")
                    .wardFont(.footnote.weight(.semibold))
                    .foregroundStyle(Color.wardInkSecondary)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: WardControlSize.target)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityIdentifier("editor-link-row")
        .contextMenu {
            if hand {
                Button(IdeasWords.unlink, systemImage: "xmark", role: .destructive) {
                    notes.unlink(current, id)
                }
            }
        }
        .swipeActions {
            if hand {
                Button(IdeasWords.unlink, systemImage: "xmark", role: .destructive) {
                    notes.unlink(current, id)
                }
            }
        }
    }

    // MARK: [[ completion

    /// Whatever has been typed after an unclosed `[[` at the end of the text.
    private var openFragment: String? {
        guard let start = text.range(of: "[[", options: .backwards) else { return nil }
        let after = text[start.upperBound...]
        if after.contains("]") || after.contains("\n") { return nil }
        return String(after)
    }

    private var suggestions: [Note] {
        guard let fragment = openFragment?.lowercased() else { return [] }
        let matches: [Note] = notes.notes.filter { note in
            let fits: Bool = fragment.isEmpty || note.title.lowercased().contains(fragment)
            return note.id != current && !note.title.isEmpty && fits
        }
        let recent: [Note] = matches.sorted { $0.updatedAt > $1.updatedAt }
        return Array(recent.prefix(8))
    }

    private func complete(with name: String) {
        guard let start = text.range(of: "[[", options: .backwards) else { return }
        let head: String = String(text[..<start.upperBound])
        text = head + name + "]]"
    }
}

/// Every other idea, to join to this one or to part from it. Its search is a
/// field pressed into the base, above the list.
struct LinkPickerView: View {
    let noteID: UUID
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
                let others = notes.search(query).filter { $0.id != noteID }
                if others.isEmpty && !query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(IdeasWords.pickerNone(query))
                        .wardFont(.body)
                        .foregroundStyle(Color.wardInkSecondary)
                        .wardRowBackground()
                }
                ForEach(others) { note in
                    let linked = notes.isLinked(noteID, note.id)
                    Button {
                        if linked { notes.unlink(noteID, note.id) } else { notes.link(noteID, note.id) }
                    } label: {
                        HStack(spacing: WardSpace.m) {
                            Text(IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled)
                                .wardFont(.body)
                                .foregroundStyle(Color.wardInk)
                                .lineLimit(2)
                            Spacer(minLength: WardSpace.s)
                            if linked {
                                Image(systemName: "checkmark")
                                    .wardFont(.body.weight(.semibold))
                                    .foregroundStyle(Color.wardPrimaryInk)
                                    .accessibilityHidden(true)
                            }
                        }
                        .frame(minHeight: WardControlSize.target)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(linked ? [.isSelected] : [])
                    .accessibilityIdentifier("picker-idea")
                    .wardRowBackground()
                }
            }
            .wardForm()
            .navigationTitle(IdeasWords.pickerTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(IdeasWords.done) { dismiss() }
                        .buttonStyle(.wardCompact)
                        .accessibilityIdentifier("picker-done")
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
        // full height: at .medium, iOS draws a sheet's content at 0.96 of
        // its size, which put the bar's 44-point Cancel at 42.25 (Chat-me
        // run 40987053659, measured at rest); the editor, at .large, keeps 44
        .presentationDetents([.large])
    }
}

/// Write or Read: the idea as typed, or laid out as Markdown. Words, never
/// glyphs.
private struct NoteModePicker: View {
    @Binding var reading: Bool

    var body: some View {
        WardSegmented(selection: $reading, options: [false, true]) { read in
            Text(read ? IdeasWords.read : IdeasWords.write)
        }
        .fixedSize()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("noteMarkdownToggle")
    }
}
