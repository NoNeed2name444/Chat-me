import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The sorting desk: Sorta's first screen (S05, direction A). Capture at the
/// top; under it, at the top level, To sort with its count and the ideas in
/// no folder, newest first, each with Choose folder; then the folders.
/// Inside a folder, a row back up, its ideas and its folders. The person
/// picks every folder: nothing is suggested.
///
/// From Stethoscore's IdeasView (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/IdeasView.swift): `capture()` (:128-134), the folder being
/// browsed and its reset when deleted (:35, 101-104), FolderPrompt (:52-63),
/// the list (:204-244), `upRow` (:258-269), `folderRow` with its menu
/// (:282-321), `noteRow` with its swipes and menu (:323-376), and the naming
/// sheet (:394-406). Cut: the bottom bar and FloatingSwitcher, the Board and
/// Space modes (S05b), search, the title pill and IdeasChrome, the dock
/// spacer, "Loose ideas", "Start typing" and WardEmptyState, NoteTone's
/// colours, the kind glyphs, the preview line and "n linked", and every
/// literal size. Changed: capture at the top; To sort first; Choose folder
/// on each row; the move dialog is a sheet (FolderChooser); Undo after each
/// filing and delete; deleting a folder asks first; the import.
struct IdeasView: View {
    @EnvironmentObject private var notes: NoteStore
    @State private var draft = ""
    /// The folder being browsed, or nil for the top level.
    @State private var folderId: UUID?
    @State private var opening: NoteRef?
    @State private var naming: FolderPrompt?
    /// The idea whose folder is being chosen.
    @State private var choosing: NoteRef?
    @State private var deletingFolder: NoteFolder?
    @State private var importing = false
    @State private var imported: ImportOutcome?
    @State private var notice: IdeasNotice?
    /// A filing made in the editor, offered with Undo when it closes.
    @State private var editorFiling: EditorFiling?
    @State private var toldReadProblem = false
    /// The list is scrolled away from its top: the hairline under the well shows.
    @State private var scrolled = false
    @FocusState private var capturing: Bool
    @Environment(\.dynamicTypeSize) private var typeSize

    /// The test-only import file (`-SortaImportFile`), opened by Import
    /// from Stethoscore… in place of the system's picker.
    private static let testImport: Data? = {
        #if DEBUG
        return IdeasDesk.importFile(arguments: ProcessInfo.processInfo.arguments)
        #else
        return nil
        #endif
    }()

    /// The naming sheet, for a new folder or a renamed one.
    enum FolderPrompt: Identifiable {
        case new(parent: UUID?)
        case rename(NoteFolder)

        var id: String {
            switch self {
            case .new(let parent): return "new-\(parent?.uuidString ?? "top")"
            case .rename(let folder): return "rename-\(folder.id.uuidString)"
            }
        }
    }

    private var showsEmpty: Bool {
        folderId == nil && IdeasDesk.isEmpty(ideas: notes.notes.count, folders: notes.folders.count)
    }

    var body: some View {
        VStack(spacing: 0) {
            captureStrip
            if showsEmpty {
                // the lines scroll, so at the largest sizes they take the
                // room left under the well rather than squeezing the well
                ScrollView {
                    emptyLines
                }
                .scrollBounceBehavior(.basedOnSize)
                .scrollEdgeEffectHidden(true, for: .top)
            } else {
                list
            }
        }
        .frame(maxWidth: IdeasLayout.column)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            IdeasNoticeHost(notice: $notice, undo: undo)
                .frame(maxWidth: IdeasLayout.column)
        }
        // one side of a fold's hinge, the page across it
        .clearOfBand()
        .wardMasthead()
        .background { WardBackground() }
        .lineSpacing(WardSpace.leading)
        .sheet(item: $opening, onDismiss: offerEditorUndo) { ref in
            NoteEditorView(noteID: ref.id, file: fileFromEditor, delete: deleteIdea)
        }
        .sheet(item: $choosing) { ref in
            FolderChooser(current: notes.note(ref.id)?.folderId,
                          choose: { file(ref.id, in: $0) },
                          makeFolder: { name in
                              let folder = notes.createFolder(name: name, parentId: folderId)
                              file(ref.id, in: folder.id)
                          })
        }
        .sheet(item: $naming) { prompt in
            nameSheet(prompt)
        }
        .confirmationDialog(deletingFolder.map { IdeasWords.deleteFolderTitle($0.name) } ?? "",
                            isPresented: deletingShown, titleVisibility: .visible, presenting: deletingFolder) { folder in
            Button(IdeasWords.deleteFolder, role: .destructive) { notes.deleteFolder(folder.id) }
            Button(IdeasWords.cancel, role: .cancel) {}
        } message: { folder in
            Text(IdeasWords.deleteFolderBody(notes.folder(folder.parentId)?.name ?? IdeasWords.toSort))
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [IdeaImportFlow.backupType]) { result in
            if case .success(let url) = result { runImport(url) }
        }
        .alert(imported?.title ?? "", isPresented: importedShown, presenting: imported) { _ in
            Button(IdeasWords.importDone, role: .cancel) {}
        } message: { outcome in
            Text(outcome.message)
        }
        // the folder being browsed was deleted: back to the top
        .onChange(of: notes.folders) { _, _ in
            if folderId != nil, notes.folder(folderId) == nil { folderId = nil }
        }
        .onAppear(perform: tellReadProblem)
    }

    // MARK: capture

    /// The well in its strip, pinned over the list: the list is cut at the
    /// strip's foot, and a hairline shows there while it is scrolled.
    private var captureStrip: some View {
        CaptureWell(draft: $draft, capturing: $capturing, save: capture, sortIt: captureAndSort)
            // always its whole height: the prompt wraps at the largest sizes
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.s)
            .background(Color.wardBackground)
            .overlay(alignment: .bottom) {
                if scrolled && !showsEmpty {
                    Rectangle()
                        .fill(Color.wardHairline)
                        .frame(height: CGFloat(WardPaper.edgeWidth))
                        .accessibilityHidden(true)
                }
            }
            .zIndex(1)
    }

    /// Saves what is in the field as an idea, straight away, and leaves the
    /// field ready for the next one. At the top level it goes to To sort;
    /// inside a folder, into that folder.
    private func capture() {
        guard let text = IdeasDesk.captured(draft) else { return }
        notes.create(title: text, kind: .idea, folderId: folderId)
        draft = ""
        // Return lets go of the caret just before this runs: it is put
        // back now, and again once the field has let go
        capturing = true
        Task { @MainActor in capturing = true }
    }

    /// Saves the idea as Return does, and opens the folder chooser for it.
    /// Closing the chooser leaves it where it was saved.
    private func captureAndSort() {
        guard let text = IdeasDesk.captured(draft) else { return }
        let idea = notes.create(title: text, kind: .idea, folderId: folderId)
        draft = ""
        capturing = false
        choosing = NoteRef(id: idea.id)
    }

    // MARK: the empty state

    /// A store with nothing in it: two lines under the well, in the top
    /// third, so the middle of the screen is the page. More stays at the
    /// row's end, as on Android, so the import and New folder are there
    /// on the first launch too.
    private var emptyLines: some View {
        HStack(alignment: .top, spacing: WardSpace.m) {
            VStack(alignment: .leading, spacing: WardSpace.xs) {
                Text(IdeasWords.emptyLine1)
                Text(IdeasWords.emptyLine2)
            }
            .wardFont(.subheadline)
            .foregroundStyle(Color.wardInkSecondary)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, WardSpace.m - WardSpace.s)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("ideas-empty")
            moreMenu
        }
        .padding(.horizontal, WardSpace.gutter)
    }

    // MARK: the list

    private var list: some View {
        let current: NoteFolder? = notes.folder(folderId)
        let here: [Note] = notes.contents(of: folderId)
        let subfolders: [NoteFolder] = notes.subfolders(of: folderId)
        return List {
            if let current {
                upRow(current)
                    .wardRowBackground()
            }
            titleRow(current, count: current.map { notes.count(in: $0.id) } ?? here.count)
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
                    ideaRow(note)
                        .wardRowBackground()
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

    /// "To sort" (or the open folder's name) and its count, as a header,
    /// with More at its end.
    private func titleRow(_ current: NoteFolder?, count: Int) -> some View {
        let name: String = current?.name ?? IdeasWords.toSort
        let spoken: String = current == nil ? IdeasWords.toSortSpoken(count) : IdeasWords.folderSpoken(name, count)
        return HStack(alignment: .center, spacing: WardSpace.s) {
            Text(Self.titleText(name, count))
                .wardFont(.title1.monospacedDigit())
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(spoken)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("ideas-title")
            moreMenu
        }
    }

    /// The title in ink and its count in the coral ink (primaryInk), one
    /// line of text, so it wraps as one.
    private static func titleText(_ name: String, _ count: Int) -> AttributedString {
        var title = AttributedString(name)
        title.foregroundColor = Color.wardInk
        var number = AttributedString(" \(count)")
        number.foregroundColor = Color.wardPrimaryInk
        return title + number
    }

    private var moreMenu: some View {
        Menu {
            Button(IdeasWords.importFromStethoscore, systemImage: "square.and.arrow.down") { startImport() }
            Button(IdeasWords.newFolder, systemImage: "folder.badge.plus") { naming = .new(parent: folderId) }
        } label: {
            Image(systemName: "ellipsis")
        }
        .menuStyle(.button)
        .buttonStyle(.wardCircle)
        // the circle is 44 points across and its glyph grows with the
        // text: past AX3 the dots ran out of it (render check of copy
        // 0cf619c), so the glyph stops there, as a bar's glyphs do, and a
        // long press shows the large content viewer instead
        .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        .accessibilityShowsLargeContentViewer {
            Label(IdeasWords.more, systemImage: "ellipsis")
        }
        .accessibilityLabel(IdeasWords.more)
        .accessibilityIdentifier("more")
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
        .accessibilityAction(named: IdeasWords.renameFolder) { naming = .rename(folder) }
        .accessibilityAction(named: IdeasWords.newFolderInside) { naming = .new(parent: folder.id) }
        .accessibilityAction(named: IdeasWords.deleteFolder) { deletingFolder = folder }
        .contextMenu {
            Button(IdeasWords.renameFolder, systemImage: "pencil") { naming = .rename(folder) }
            Button(IdeasWords.newFolderInside, systemImage: "folder.badge.plus") { naming = .new(parent: folder.id) }
            Button(IdeasWords.deleteFolder, systemImage: "trash", role: .destructive) { deletingFolder = folder }
        }
        .swipeActions {
            Button(IdeasWords.deleteFolder, systemImage: "trash", role: .destructive) { deletingFolder = folder }
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
                opening = NoteRef(id: note.id)
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
            .accessibilityHint(IdeasWords.rowHint)
            .accessibilityIdentifier("idea-row")
            Button(IdeasWords.chooseFolder) { choosing = NoteRef(id: note.id) }
                .buttonStyle(.wardCompact)
                .fixedSize(horizontal: !stacked, vertical: false)
                .accessibilityLabel(IdeasWords.chooseFolderFor(line))
                .accessibilityIdentifier("choose-folder")
        }
        .contextMenu {
            Button(IdeasWords.chooseFolder, systemImage: "folder") { choosing = NoteRef(id: note.id) }
            Button(IdeasWords.delete, systemImage: "trash", role: .destructive) { deleteIdea(note.id) }
        }
        .swipeActions(edge: .leading) {
            Button(IdeasWords.chooseFolder, systemImage: "folder") { choosing = NoteRef(id: note.id) }
                .tint(IdeaSwipe.chooseFill)
        }
        .swipeActions(edge: .trailing) {
            Button(IdeasWords.delete, systemImage: "trash", role: .destructive) { deleteIdea(note.id) }
        }
    }

    // MARK: filing, deleting and Undo

    /// Moves an idea into a folder (nil: To sort) and offers Undo.
    private func file(_ id: UUID, in folder: UUID?) {
        guard let idea = notes.note(id), idea.folderId != folder else { return }
        let from: UUID? = idea.folderId
        notes.move(id, to: folder)
        let text: String = notes.folder(folder).map { IdeasWords.movedTo($0.name) } ?? IdeasWords.backInToSort
        notice = IdeasNotice(text: text, undo: .moved(idea: id, from: from))
    }

    /// A filing from the editor's folder chip: the idea moves now, and Undo
    /// is offered when the editor closes, since the editor covers the
    /// notice. Filed again in the same visit, Undo still goes back to where
    /// the idea was before the first move; moved back there, nothing is
    /// offered.
    private func fileFromEditor(_ id: UUID, in folder: UUID?) {
        guard let idea = notes.note(id), idea.folderId != folder else { return }
        let from: UUID? = editorFiling?.idea == id ? editorFiling?.from : idea.folderId
        notes.move(id, to: folder)
        editorFiling = folder == from ? nil : EditorFiling(idea: id, from: from)
    }

    private func offerEditorUndo() {
        guard let filing = editorFiling else { return }
        editorFiling = nil
        // deleted in the editor: its own notice, with its own Undo, stays
        guard let idea = notes.note(filing.idea), idea.folderId != filing.from else { return }
        let text: String = notes.folder(idea.folderId).map { IdeasWords.movedTo($0.name) } ?? IdeasWords.backInToSort
        notice = IdeasNotice(text: text, undo: .moved(idea: filing.idea, from: filing.from))
    }

    private func deleteIdea(_ id: UUID) {
        guard let removal = notes.remove(id) else { return }
        notice = IdeasNotice(text: IdeasWords.ideaDeleted, undo: .deleted(removal))
    }

    private func undo(_ step: IdeasNotice.Undo) {
        switch step {
        case let .moved(idea, from):
            // the folder it came from may have gone since; then To sort
            notes.move(idea, to: notes.folder(from)?.id)
        case let .deleted(removal):
            notes.undo(removal)
        }
    }

    /// The store's file couldn't be read at launch, wholly or in part: said
    /// once, in the notice, which never covers capture.
    private func tellReadProblem() {
        guard !toldReadProblem, let problem = notes.readProblem else { return }
        toldReadProblem = true
        let text: String = problem == .unreadable ? IdeasWords.storeUnreadable : IdeasWords.storePartlyUnreadable
        notice = IdeasNotice(text: text, undo: nil)
    }

    // MARK: folders

    private var deletingShown: Binding<Bool> {
        Binding(get: { deletingFolder != nil }, set: { if !$0 { deletingFolder = nil } })
    }

    @ViewBuilder
    private func nameSheet(_ prompt: FolderPrompt) -> some View {
        switch prompt {
        case .new(let parent):
            NameSheet(title: IdeasWords.newFolder, prompt: IdeasWords.folderName, initial: "",
                      confirm: IdeasWords.create) { name in
                _ = notes.createFolder(name: name, parentId: parent)
            }
        case .rename(let folder):
            NameSheet(title: IdeasWords.renameFolder, prompt: IdeasWords.folderName, initial: folder.name,
                      confirm: IdeasWords.rename) { name in
                notes.renameFolder(folder.id, to: name)
            }
        }
    }

    // MARK: the import

    private var importedShown: Binding<Bool> {
        Binding(get: { imported != nil }, set: { if !$0 { imported = nil } })
    }

    private func startImport() {
        if let data = Self.testImport {
            // the UI tests' file, handed to the call the picker's result makes
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("Sorta test backup.\(IdeaImportFlow.fileExtension)")
            do {
                try data.write(to: url, options: .atomic)
                runImport(url)
            } catch {
                imported = .refused(.unreadable)
            }
        } else {
            importing = true
        }
    }

    /// What the system's file picker hands back, and the tests' file too.
    private func runImport(_ url: URL) {
        imported = IdeaImportFlow.run(url, into: notes)
    }
}

/// The import's two ends: the file type the picker offers, and what one
/// import of a file says.
enum IdeaImportFlow {
    static let fileExtension = "stethoscorebackup"
    /// Stethoscore's backup, declared as an imported type in project.yml.
    static let backupType = UTType(importedAs: "com.cramdown.stethoscore.backup", conformingTo: .zip)

    /// Opens the file (asking for access, as a picked file needs), merges its
    /// ideas (IdeaImport) and says how it went.
    @MainActor
    static func run(_ url: URL, into store: NoteStore) -> ImportOutcome {
        let scoped: Bool = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            return .added(try IdeaImport.merge(backupAt: url, into: store))
        } catch let failure as LibraryBackup.Failure {
            return .refused(failure)
        } catch {
            return .refused(.unreadable)
        }
    }
}

/// What one import said: what it added, or why the file was refused.
enum ImportOutcome: Equatable {
    case added(IdeaImport.Counts)
    case refused(LibraryBackup.Failure)

    var title: String {
        switch self {
        case .added(let counts):
            return counts.ideas + counts.folders == 0 ? IdeasWords.nothingNewTitle : IdeasWords.importedTitle
        case .refused: return IdeasWords.refusedTitle
        }
    }

    var message: String {
        switch self {
        case .added(let counts):
            guard counts.ideas + counts.folders > 0 else { return IdeasWords.nothingNewBody }
            let added = IdeasWords.imported(ideas: counts.ideas, folders: counts.folders, links: counts.links)
            return counts.placed > 0 ? added + " " + IdeasWords.placed(counts.placed) : added
        case .refused(.notABackup): return IdeasWords.refusedNotBackup
        case .refused(.newerVersion): return IdeasWords.refusedNewer
        case .refused(.unreadable): return IdeasWords.refusedUnreadable
        }
    }
}

/// The leading swipe's fill: `primary`'s light value (#C4503A) in both
/// modes, so the system's white words on it keep 4.61:1 (T0135-review,
/// contrast table); read from the kit through a light trait collection,
/// never written as a hex.
enum IdeaSwipe {
    static var chooseFill: Color {
        Color(uiColor: UIColor(Color.wardPrimary).resolvedColor(with: UITraitCollection(userInterfaceStyle: .light)))
    }
}

/// A filing from the editor: the idea, and the folder it was in before the
/// editor first moved it (nil: To sort), which Undo puts it back in.
struct EditorFiling: Equatable {
    let idea: UUID
    let from: UUID?
}
