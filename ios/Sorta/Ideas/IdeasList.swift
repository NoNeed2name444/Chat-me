import SwiftUI

/// What a person can do from the list, handed in by the screen that holds it
/// (the Sort screen, and Map mode's list sheet), which owns the sheets,
/// dialogs and Undo these lead to.
struct IdeasListActions {
    /// Opens an idea in the editor.
    let open: (UUID) -> Void
    /// Opens the folder chooser for an idea.
    let chooseFolder: (UUID) -> Void
    /// Deletes an idea, with Undo.
    let delete: (UUID) -> Void
    let renameFolder: (NoteFolder) -> Void
    let newFolderInside: (NoteFolder) -> Void
    /// Asks before deleting a folder.
    let deleteFolder: (NoteFolder) -> Void
}

/// S05's list, shared by the Sort screen and Map mode's list sheet (S05b,
/// question 1): inside a folder a row back up; "To sort" (or the folder's
/// name) with its count, as a header, and `titleEnd` after it; the ideas
/// here, newest first, each with Choose folder; then Folders. A folder row
/// opens that folder in the same list.
///
/// From Stethoscore's IdeasView (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/IdeasView.swift): the list (:204-244), `upRow`
/// (:258-269), `folderRow` with its menu (:282-321) and `noteRow` with its
/// swipes and menu (:323-376), as S05 changed them (IdeasView's note).
struct IdeasList<TitleEnd: View>: View {
    @EnvironmentObject private var notes: NoteStore
    /// The folder being browsed, or nil for the top level.
    @Binding var folderId: UUID?
    /// The list is scrolled away from its top.
    @Binding var scrolled: Bool
    let actions: IdeasListActions
    /// What stands at the title row's end: More, on the Sort screen.
    let titleEnd: () -> TitleEnd
    /// "You were saying…" (S06a), at the top level's top, on the Sort screen.
    var offer: IdeasOffer?
    @Environment(\.dynamicTypeSize) private var typeSize

    init(folderId: Binding<UUID?>, scrolled: Binding<Bool> = .constant(false), actions: IdeasListActions,
         offer: IdeasOffer? = nil, @ViewBuilder titleEnd: @escaping () -> TitleEnd) {
        self._folderId = folderId
        self._scrolled = scrolled
        self.actions = actions
        self.offer = offer
        self.titleEnd = titleEnd
    }

    var body: some View {
        let current: NoteFolder? = notes.folder(folderId)
        let here: [Note] = notes.contents(of: folderId)
        let subfolders: [NoteFolder] = notes.subfolders(of: folderId)
        List {
            if let current {
                upRow(current)
                    .wardRowBackground()
            }
            if current == nil, let offer {
                ResumeOffer(note: offer.note, open: offer.open, dismiss: offer.dismiss)
                    .listRowBackground(Color.clear)
                    .listRowInsets(WardRowTile.cardInsets)
                    .listRowSeparator(.hidden)
            }
            IdeasTitleRow(current: current, count: current.map { notes.count(in: $0.id) } ?? here.count, end: titleEnd)
                .listRowBackground(Color.clear)
                .listRowInsets(IdeasLayout.lineInsets)
                .listRowSeparator(.hidden)
            if here.isEmpty {
                Text(current == nil ? IdeasWords.allSorted : IdeasWords.folderEmpty)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInkSecondary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(IdeasLayout.lineInsets)
                    .listRowSeparator(.hidden)
            } else {
                ForEach(here) { note in
                    // a marked idea (S06a): the tile's edge dotted
                    ideaRow(note)
                        .listRowBackground(WardRowTile().unfinishedEdge(note.stoppedAt != nil))
                        .listRowInsets(WardRowTile.insets)
                        .listRowSeparator(.hidden)
                }
            }
            if !subfolders.isEmpty {
                WardSectionLabel(IdeasWords.folders)
                    .listRowBackground(Color.clear)
                    .listRowInsets(IdeasLayout.lineInsets)
                    .listRowSeparator(.hidden)
                ForEach(subfolders) { folder in
                    folderRow(folder)
                        .wardRowBackground()
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollEdgeEffectHidden(true, for: .top)
        .environment(\.defaultMinListRowHeight, WardControlSize.button)
        // the tiles stand WardSpace.s in from the cell, so their edges line
        // up with the well's, a gutter in from the column's; the lines of
        // words start there too (IdeasLayout.lineInsets)
        .padding(.horizontal, WardSpace.gutter - WardSpace.s)
        .onScrollGeometryChange(for: Bool.self) { geometry in
            geometry.contentOffset.y + geometry.contentInsets.top > 0
        } action: { _, now in
            if now != scrolled { scrolled = now }
        }
    }

    private func upRow(_ current: NoteFolder) -> some View {
        let parentName: String = notes.folder(current.parentId)?.name ?? IdeasWords.toSort
        return Button {
            folderId = current.parentId
        } label: {
            Label(parentName, systemImage: "chevron.backward")
                .wardFont(.body)
                .foregroundStyle(Color.wardPrimaryInk)
                .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityLabel(IdeasWords.backTo(parentName))
        .accessibilityIdentifier("up-row")
    }

    private func folderRow(_ folder: NoteFolder) -> some View {
        let count: Int = notes.count(in: folder.id)
        return Button {
            folderId = folder.id
        } label: {
            HStack(spacing: WardSpace.m) {
                Image(systemName: "folder")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
                Text(folder.name)
                    .wardFont(.body)
                    .foregroundStyle(Color.wardInk)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: "\(count)")
                    .wardFont(.footnote.monospacedDigit())
                    .foregroundStyle(Color.wardInkSecondary)
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
        .accessibilityLabel(IdeasWords.folderRowSpoken(folder.name, count))
        .accessibilityIdentifier("folder-row")
        .accessibilityAction(named: IdeasWords.renameFolder) { actions.renameFolder(folder) }
        .accessibilityAction(named: IdeasWords.newFolderInside) { actions.newFolderInside(folder) }
        .accessibilityAction(named: IdeasWords.deleteFolder) { actions.deleteFolder(folder) }
        .contextMenu {
            Button(IdeasWords.renameFolder, systemImage: "pencil") { actions.renameFolder(folder) }
            Button(IdeasWords.newFolderInside, systemImage: "folder.badge.plus") { actions.newFolderInside(folder) }
            Button(IdeasWords.deleteFolder, systemImage: "trash", role: .destructive) { actions.deleteFolder(folder) }
        }
        .swipeActions {
            Button(IdeasWords.deleteFolder, systemImage: "trash", role: .destructive) { actions.deleteFolder(folder) }
        }
    }

    private func ideaRow(_ note: Note) -> some View {
        let line: String = IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled
        let whole: String = note.title.isEmpty ? line : note.title
        let stacked: Bool = typeSize.isAccessibilitySize
        let layout = stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: WardSpace.s))
            : AnyLayout(HStackLayout(alignment: .center, spacing: WardSpace.m))
        return layout {
            Button {
                actions.open(note.id)
            } label: {
                Text(line)
                    .wardFont(.body)
                    .foregroundStyle(Color.wardInk)
                    .lineLimit(stacked ? 3 : 2)
                    .truncationMode(.tail)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .hoverEffect(.highlight)
            .accessibilityLabel(whole)
            .accessibilityValue(note.stoppedAt != nil ? IdeasWords.unfinished : "")
            .accessibilityHint(IdeasWords.rowHint)
            .accessibilityIdentifier("idea-row")
            Button(IdeasWords.chooseFolder) { actions.chooseFolder(note.id) }
                .buttonStyle(.wardCompact)
                .fixedSize(horizontal: !stacked, vertical: false)
                .accessibilityLabel(IdeasWords.chooseFolderFor(line))
                .accessibilityIdentifier("choose-folder")
        }
        .contextMenu {
            Button(IdeasWords.chooseFolder, systemImage: "folder") { actions.chooseFolder(note.id) }
            Button(IdeasWords.delete, systemImage: "trash", role: .destructive) { actions.delete(note.id) }
        }
        .swipeActions(edge: .leading) {
            Button(IdeasWords.chooseFolder, systemImage: "folder") { actions.chooseFolder(note.id) }
                .tint(IdeaSwipe.chooseFill)
        }
        .swipeActions(edge: .trailing) {
            Button(IdeasWords.delete, systemImage: "trash", role: .destructive) { actions.delete(note.id) }
        }
    }
}

/// "To sort" (or the open folder's name) and its count, as a header, with
/// `end` at its end: the list's, and the deck's (S06a) above its cards.
struct IdeasTitleRow<End: View>: View {
    let current: NoteFolder?
    let count: Int
    let end: () -> End

    var body: some View {
        let name: String = current?.name ?? IdeasWords.toSort
        let spoken: String = current == nil ? IdeasWords.toSortSpoken(count) : IdeasWords.folderSpoken(name, count)
        HStack(alignment: .center, spacing: WardSpace.s) {
            Text(Self.titleText(name, count))
                .wardFont(.title1.monospacedDigit())
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(spoken)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("ideas-title")
            end()
        }
    }

    /// The title in ink and its count in the coral ink (primaryInk), one
    /// line of text, so it wraps as one.
    static func titleText(_ name: String, _ count: Int) -> AttributedString {
        var title = AttributedString(name)
        title.foregroundColor = Color.wardInk
        var number = AttributedString(" \(count)")
        number.foregroundColor = Color.wardPrimaryInk
        return title + number
    }
}

/// "You were saying…" as the list shows it: the idea, and what taking it
/// and putting it away do.
struct IdeasOffer {
    let note: Note
    let open: () -> Void
    let dismiss: () -> Void
}

extension IdeasList where TitleEnd == EmptyView {
    /// The list with nothing at the title row's end.
    init(folderId: Binding<UUID?>, scrolled: Binding<Bool> = .constant(false), actions: IdeasListActions) {
        self.init(folderId: folderId, scrolled: scrolled, actions: actions) { EmptyView() }
    }
}
