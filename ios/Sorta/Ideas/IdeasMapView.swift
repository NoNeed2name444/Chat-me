import SwiftUI

/// Map mode (S05b checklist 1): the ideas on a full-screen map in the look
/// chosen (MapLook), with the foot (the notice and the switch) over its
/// bottom. The three 3D looks are night, and the chrome over them is too;
/// 2D is the board on the page, in the phone's own light or dark
/// (Stethoscore's IdeasView: `overMap` :75, `colorScheme` :122). Sheets
/// follow the phone in every look. The map
/// fits inside the safe area the chrome leaves (Graph3DView's fit), while
/// its scene runs edge to edge under it.
///
/// With no ideas, the page's own empty state, with no tools and no night
/// (checklist 8). Opening an idea gives S05's editor; a folder opens in Sort
/// (`openFolder`).
struct IdeasMapView: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Binding var mode: SortaMode
    let openFolder: (UUID?) -> Void
    /// Beside the Sort screen, on two panes: no switch, no list chip, and
    /// the notice is the Sort screen's.
    var paned = false
    @AppStorage(MapLook.boardKey) private var board = false
    @AppStorage(GraphTheme.key) private var themeRaw = GraphTheme.standard.rawValue
    @Environment(\.colorScheme) private var scheme
    /// The idea open in the editor, from the board.
    @State private var opening: NoteRef?
    /// The list sheet is up.
    @State private var listing = false
    /// The list of the looks is open over the map (MapLookChip).
    @State private var choosingLook = false
    /// The top strip's height, so the list of the looks opens under it.
    @State private var stripHeight: CGFloat = 0

    private var look: MapLook { MapLook.stored(board: board, theme: themeRaw) }

    private var empty: Bool { notes.notes.isEmpty }

    private var night: Bool { !empty && look != .twoD }

    var body: some View {
        content
            .mapTextCap()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // night over the 3D looks, as their own chrome is; only the
            // strip and the foot, so every sheet still follows the phone
            .safeAreaInset(edge: .top, spacing: 0) {
                if !empty {
                    MapTopStrip(look: look, openLooks: { choosingLook = true },
                                list: paned ? nil : { listing = true })
                        .environment(\.colorScheme, night ? .dark : scheme)
                        .mapTextCap()
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { stripHeight = $0 }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                IdeasFoot(mode: $mode, notices: !listing && !paned, switches: !paned)
                    .environment(\.colorScheme, night ? .dark : scheme)
                    .mapTextCap()
            }
            // in the view itself, not a Menu or a popover: those closed when
            // the 3D map's scene replaced "Laying out your ideas"
            .overlay(alignment: .topTrailing) {
                if choosingLook && !empty {
                    lookList
                        .mapTextCap()
                }
            }
            // on two panes the last idea can go from the Sort screen while
            // the list is open: it must not come back with the next one
            .onChange(of: empty) { _, now in
                if now { choosingLook = false }
            }
            // the map frames itself in one side of a fold's hinge, never on it
            .clearOfBand()
            // the page under it, as on Android: the 3D scenes paint their own
            // night edge to edge
            .background { WardBackground() }
            .sheet(item: $opening, onDismiss: { filing.offerEditorUndo(notes: notes) }) { ref in
                NoteEditorView(noteID: ref.id, file: { filing.fileFromEditor($0, in: $1, notes: notes) },
                               delete: { filing.deleteIdea($0, notes: notes) })
            }
            .sheet(isPresented: $listing) {
                MapListSheet()
            }
            // S06c: the collide card, the split chooser and the picker, from
            // the map's options, its gestures and the Collide tool
            .ideaShapeSheets(.map)
    }

    /// The list of the looks under the strip, at its trailing end; a tap
    /// anywhere else closes it, as a menu's would.
    private var lookList: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(0.001)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { choosingLook = false }
                .accessibilityHidden(true)
            MapLookList(look: look, choose: { new in
                choosingLook = false
                choose(new)
            }, close: { choosingLook = false })
                .padding(.top, stripHeight)
                .padding(.trailing, WardSpace.gutter)
                .padding(.leading, WardSpace.gutter)
        }
        .environment(\.colorScheme, night ? .dark : scheme)
    }

    /// A new look, as a cut: no morph and no cross-fade between looks
    /// (T0135-review, item 6). 2D keeps the theme, for going back to 3D.
    private func choose(_ new: MapLook) {
        var cut = Transaction()
        cut.disablesAnimations = true
        withTransaction(cut) {
            board = new == .twoD
            if let theme = new.theme { themeRaw = theme.rawValue }
        }
    }

    @ViewBuilder
    private var content: some View {
        if empty {
            WardEmptyState(symbol: "cube.transparent", title: IdeasWords.mapEmptyTitle,
                           message: IdeasWords.mapEmptyLine)
                .frame(maxWidth: IdeasLayout.column)
                .padding(.horizontal, WardSpace.gutter)
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("map-empty")
        } else if look == .twoD {
            IdeaBoardView(open: { opening = NoteRef(id: $0) })
                .wardUnlit()
        } else {
            Graph3DView(open: { opening = NoteRef(id: $0) }, openFolder: openFolder)
                .mapSky()
        }
    }
}

extension View {
    /// Map mode's text grows with the phone's text size up to AX1 and no
    /// further (the owner, 5 Nov: at AX5 the map's text was far too big):
    /// the map, its strip, its foot and the list of the looks. Its sheets,
    /// the list and the editor, follow the phone, as the Sort screen does.
    /// Android caps the map's font scale at the same size
    /// (WardType.accessibilityScale).
    func mapTextCap() -> some View {
        dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }
}
