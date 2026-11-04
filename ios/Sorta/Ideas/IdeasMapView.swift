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

    private var look: MapLook { MapLook.stored(board: board, theme: themeRaw) }

    private var empty: Bool { notes.notes.isEmpty }

    private var night: Bool { !empty && look != .twoD }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // night over the 3D looks, as their own chrome is; only the
            // strip and the foot, so every sheet still follows the phone
            .safeAreaInset(edge: .top, spacing: 0) {
                if !empty {
                    MapTopStrip(look: look, choose: choose, list: paned ? nil : { listing = true })
                        .environment(\.colorScheme, night ? .dark : scheme)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                IdeasFoot(mode: $mode, notices: !listing && !paned, switches: !paned)
                    .environment(\.colorScheme, night ? .dark : scheme)
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
