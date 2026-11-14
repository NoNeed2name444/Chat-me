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
/// and the naming sheet (:394-406); the list (:204-376) is IdeasList, which
/// Map mode's list sheet shares with the sheets it leads to (IdeasListRoutes). Cut: the bottom bar and FloatingSwitcher,
/// the Board and Space modes (S05b), search, the title pill and IdeasChrome,
/// the dock spacer, "Loose ideas", "Start typing" and WardEmptyState,
/// NoteTone's colours, the kind glyphs, the preview line and "n linked",
/// and every literal size. Changed: capture at the top; To sort first;
/// Choose folder on each row; the move dialog is a sheet (FolderChooser);
/// Undo after each filing and delete; deleting a folder asks first; the
/// import.
struct IdeasView: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    /// Sort or Map, the switch's (IdeasRoot's).
    @Binding var mode: SortaMode
    /// The field's words and the folder being browsed (nil: the top level),
    /// kept by IdeasRoot, so a visit to Map comes back to them.
    @Binding var draft: String
    @Binding var folderId: UUID?
    /// Beside the map, on two panes: no switch.
    var paned = false
    /// The editor, chooser, naming sheet and delete dialog the list opens.
    @State private var routes = IdeasListRoutes()
    @State private var importing = false
    @State private var imported: ImportOutcome?
    @State private var toldReadProblem = false
    /// The list is scrolled away from its top: the hairline under the well shows.
    @State private var scrolled = false
    @FocusState private var capturing: Bool
    /// The field has the caret, held a moment after it lets go: the switch
    /// is folded meanwhile (IdeasFoot).
    @State private var typing = false
    /// To sort shown as cards (S06a), remembered.
    @AppStorage(DeckKeys.sortDeck) private var sortDeck = false
    /// The tide's idea being linked, in the editor's chooser.
    @State private var linking: NoteRef?

    /// The test-only import file (`-SortaImportFile`), opened by Import
    /// from Stethoscore… in place of the system's picker.
    private static let testImport: Data? = {
        #if DEBUG
        return IdeasDesk.importFile(arguments: ProcessInfo.processInfo.arguments)
        #else
        return nil
        #endif
    }()

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
            } else if IdeasDeckRule.shown(folderId: folderId, sortDeck: sortDeck, filing: filing, notes: notes) {
                deck
            } else {
                list
            }
        }
        .frame(maxWidth: IdeasLayout.column)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            IdeasFoot(mode: $mode, typing: typing, letGo: { capturing = false }, switches: !paned)
        }
        // one side of a fold's hinge, the page across it
        .clearOfBand()
        .wardMasthead()
        .background { WardBackground() }
        .lineSpacing(WardSpace.leading)
        .ideasListSheets(routes, folderId: $folderId)
        .fileImporter(isPresented: $importing, allowedContentTypes: [IdeaImportFlow.backupType]) { result in
            if case .success(let url) = result { runImport(url) }
        }
        .alert(imported?.title ?? "", isPresented: importedShown, presenting: imported) { _ in
            Button(IdeasWords.importDone, role: .cancel) {}
        } message: { outcome in
            Text(outcome.message)
        }
        .onAppear(perform: tellReadProblem)
        // S06a: the tide is asked for once a launch; the deck follows To sort
        .onAppear { filing.startTide(notes: notes) }
        .onChange(of: notes.contents(of: nil).map(\.id), initial: true) { _, ids in filing.deck.sync(ids) }
        // the tide's Link to an idea…: the editor's chooser; closed, the idea counts as kept
        .sheet(item: $linking) { ref in
            LinkPickerView(noteID: ref.id)
                .onDisappear {
                    notes.markOpened(ref.id, at: IdeasClock.now)
                    filing.leaveTide(ref.id)
                }
        }
        .task(id: capturing) {
            if capturing {
                typing = true
                return
            }
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            typing = false
        }
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
        routes.choosing = NoteRef(id: idea.id)
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

    /// S05's list (IdeasList), with More at the title row's end; the
    /// sheets, dialogs and Undo it leads to are this screen's.
    private var list: some View {
        IdeasList(folderId: $folderId, scrolled: $scrolled, actions: routes.actions(filing: filing, notes: notes), offer: offer) {
            titleEnd
        }
    }

    /// The title row's end: at the top level with ideas to sort, the list
    /// or cards switch (S06a), then More.
    private var titleEnd: some View {
        HStack(spacing: WardSpace.s) {
            if folderId == nil && !notes.contents(of: nil).isEmpty {
                DeckToggle()
            }
            moreMenu
        }
    }

    // MARK: S06a: "You were saying…", the deck and the low tide

    /// "You were saying…": the marked idea changed last, until it is taken
    /// or put away in this launch; opened, the caret goes where it stopped.
    private var offer: IdeasOffer? {
        guard folderId == nil, !filing.offerDone, let note = IdeasDeckRule.offer(notes) else { return nil }
        return IdeasOffer(note: note, open: {
            filing.offerDone = true
            routes.resuming = note.id
            routes.opening = NoteRef(id: note.id)
        }, dismiss: { filing.offerDone = true })
    }

    /// To sort as cards, or the tide while it is up: the offer and the title
    /// row, then the deck in the list's place. On two panes the folders are
    /// the pane's beside it (IdeasRoot).
    private var deck: some View {
        let tide: [UUID] = IdeasDeckRule.tide(filing, notes: notes)
        let order: [UUID] = IdeasDeckRule.order(filing, notes: notes)
        return VStack(alignment: .leading, spacing: 0) {
            if let offer {
                ResumeOffer(note: offer.note, open: offer.open, dismiss: offer.dismiss)
                    .padding(.horizontal, WardSpace.gutter)
                    .padding(.top, WardSpace.s)
            }
            Group {
                if tide.isEmpty {
                    IdeasTitleRow(current: nil, count: order.count) { titleEnd }
                } else {
                    TideTitle { filing.tide = [] }
                }
            }
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.s)
            if tide.isEmpty {
                DeckArea(order: order, actions: sortActions,
                         paneTargets: paned ? IdeasDeckRule.targets(notes, slots: DeckLayout.paneCap) : nil)
            } else {
                DeckArea(order: tide, actions: tideActions, paneTargets: paned ? [] : nil)
            }
        }
    }

    private var sortActions: DeckActions {
        .sort(file: { id, folder in filing.file(id, in: folder, notes: notes) },
              later: { filing.later($0) },
              choose: { routes.choosing = NoteRef(id: $0) })
    }

    private var tideActions: DeckActions {
        .tide(keep: { id in
                  notes.markOpened(id, at: IdeasClock.now)
                  filing.leaveTide(id)
              },
              link: { linking = NoteRef(id: $0) },
              letGo: { filing.letGo($0, notes: notes) })
    }

    private var moreMenu: some View {
        Menu {
            Button(IdeasWords.importFromStethoscore, systemImage: "square.and.arrow.down") { startImport() }
            Button(IdeasWords.newFolder, systemImage: "folder.badge.plus") { routes.naming = .new(parent: folderId) }
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

    // MARK: the store's notice (IdeasFiling)

    /// The store's file couldn't be read at launch, wholly or in part: said
    /// once, in the notice, which never covers capture.
    private func tellReadProblem() {
        guard !toldReadProblem, let problem = notes.readProblem else { return }
        toldReadProblem = true
        let text: String = problem == .unreadable ? IdeasWords.storeUnreadable : IdeasWords.storePartlyUnreadable
        filing.notice = IdeasNotice(text: text, undo: nil)
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
