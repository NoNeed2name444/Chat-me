import SwiftUI

/// The first screen: the sorting desk (IdeasView), on the store opened at
/// launch (IdeasRoot), with Map mode a switch away. Its page is the launch
/// screen's colour, and nothing crosses the screen's middle while the store
/// is empty, so the hand-over from the launch screen is invisible.
///
/// Launched with `-SortaGallery` (GalleryTests), it shows the look kit's
/// states gallery on the page instead: every control at rest, held and
/// disabled, for real renders of the kit. Launched with `-graphPreview`, it
/// shows the maps on sample ideas instead (GraphPreview).
struct RootView: View {
    static let showsGallery = ProcessInfo.processInfo.arguments.contains("-SortaGallery")

    var body: some View {
        if GraphPreview.isOn {
            // the maps on Stethoscore's example ideas, for the UI tests and
            // the renders (GraphPreview); no screen a user reaches opens it
            GraphPreviewRoot()
                .wardLookRoot()
                .wardControls()
                .mapSky()
        } else if Self.showsGallery {
            WardStatesGallery(section: .sorta)
                // the status bar's area solid in the page colour, so the
                // rows scrolled under it don't show through its time
                .wardMasthead()
                .background { WardBackground() }
                .wardLookRoot()
                // the accent as the tint, and the soft switch and progress
                // bar, as at Stethoscore's root (RedPenApp.swift:283)
                .wardControls()
        } else {
            IdeasRoot()
                .wardLookRoot()
                .wardControls()
                .measuringWindow()
        }
    }
}

/// The app's one store, opened once at launch and kept for the life of the
/// window: NoteStore on its file in Application Support, every change saved
/// through it. It holds the mode too, Sort at every launch (S05b checklist
/// 1), and what Sort was showing, so a visit to Map comes back to it; a
/// folder opened on the map opens in Sort. Where the window is broad (700
/// up), or a fold's band stands upright across it, the Sort screen and the
/// map share it, side by side, with no switch (S05b checklist 9). A UI test's `-SortaStore <name>` (IdeasDesk.storeURL) points
/// it at a file of its own in the temporary folder instead, new for a new
/// name; a Release build ignores the argument.
struct IdeasRoot: View {
    @StateObject private var notes = NoteStore(fileURL: IdeasRoot.storeURL)
    @State private var filing = IdeasFiling()
    @State private var mode: SortaMode = .sort
    @State private var draft = ""
    @State private var folderId: UUID?
    /// To sort shown as cards (S06a): on two panes the folders pane takes the map's place.
    @AppStorage(DeckKeys.sortDeck) private var sortDeck = false

    static var storeURL: URL? {
        #if DEBUG
        guard let url = IdeasDesk.storeURL(arguments: ProcessInfo.processInfo.arguments,
                                           temporary: FileManager.default.temporaryDirectory) else { return nil }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                 withIntermediateDirectories: true)
        // the test's made-up ideas, on a store that is new (S06a)
        if let seed = IdeasDesk.seedFile(arguments: ProcessInfo.processInfo.arguments),
           !FileManager.default.fileExists(atPath: url.path) {
            try? seed.write(to: url, options: .atomic)
        }
        for (key, value) in IdeasDesk.deckDefaults(arguments: ProcessInfo.processInfo.arguments) {
            UserDefaults.standard.set(value, forKey: key)
        }
        return url
        #else
        return nil
        #endif
    }

    @Environment(\.windowSpan) private var span
    @Environment(\.devicePosture) private var posture
    /// The window, in window points, for the fold's panes.
    @State private var frame: CGRect = .zero

    var body: some View {
        Group {
            if let split = IdeasSplit(span: span, posture: posture, frame: frame) {
                HStack(spacing: 0) {
                    IdeasView(mode: $mode, draft: $draft, folderId: $folderId, paned: true)
                        .frame(width: split.sort)
                    // the band, where the page shows through
                    if split.gap > 0 {
                        WardBackground().frame(width: split.gap)
                    }
                    // the keyboard is the Sort screen's: the map keeps its
                    // frame, else it refits on every step of the keyboard's
                    // slide, which kept the app from ever going idle (a7054fe's
                    // iPad render, 60 s waiting after the capture field's tap)
                    if IdeasDeckRule.shown(folderId: folderId, sortDeck: sortDeck, filing: filing, notes: notes)
                        && IdeasDeckRule.tide(filing, notes: notes).isEmpty {
                        // the deck's folders beside it (S06a A2); a tap files the top card
                        DeckFolderPane { folder in
                            if let top = IdeasDeckRule.order(filing, notes: notes).first {
                                filing.file(top, in: folder, notes: notes)
                            }
                        }
                        .ignoresSafeArea(.keyboard)
                    } else {
                        IdeasMapView(mode: $mode, openFolder: { folderId = $0 }, paned: true)
                            .ignoresSafeArea(.keyboard)
                    }
                }
                // the two panes keep to their sides of the band themselves
                .preference(key: PostureClaimKey.self, value: true)
            } else {
                switch mode {
                case .sort:
                    IdeasView(mode: $mode, draft: $draft, folderId: $folderId)
                case .map:
                    IdeasMapView(mode: $mode, openFolder: { id in
                        folderId = id
                        mode = .sort
                    })
                }
            }
        }
        .background {
            Color.clear
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { now in
                    if now != frame { frame = now }
                }
        }
        .environmentObject(notes)
        .environment(filing)
    }
}

/// Sort and Map side by side (S05b checklist 9): across an upright fold's
/// band, each on its side; else in a broad window (700 up), the Sort
/// screen 0.4 of the width and at least 320, the map the rest. A band lying
/// flat (tabletop) keeps one screen. Nil when the two don't share.
struct IdeasSplit: Equatable {
    /// The Sort screen's width, the band's, and the map's is what is left.
    let sort: CGFloat
    let gap: CGFloat

    static let share: CGFloat = 0.4
    static let leastSort: CGFloat = 320

    init?(span: WindowSpan, posture: DevicePosture, frame: CGRect) {
        if posture.band != nil {
            guard let panes = posture.panes(in: frame), panes.sideBySide, let map = panes.controls else { return nil }
            sort = panes.reading.width
            gap = max(0, map.minX - panes.reading.maxX)
            return
        }
        guard span.splits, frame.width > 0 else { return nil }
        sort = max(frame.width * Self.share, Self.leastSort)
        gap = 0
    }
}
