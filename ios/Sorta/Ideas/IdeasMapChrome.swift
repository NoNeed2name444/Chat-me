import SwiftUI

/// Map mode's top strip (S05b checklist 3): leading, the "To sort n" chip,
/// which opens the list sheet; trailing, the look chip. Each is the kit's
/// chip, a gutter in from the edges and WardSpace.s under the safe area.
/// When one row can't hold both, as at the largest text sizes, they stack.
/// `list` is nil on two panes, where the Sort screen is beside the map.
struct MapTopStrip: View {
    let look: MapLook
    let choose: (MapLook) -> Void
    var list: (() -> Void)?

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: WardSpace.s) {
                listChip
                Spacer(minLength: 0)
                MapLookChip(look: look, choose: choose)
            }
            VStack(alignment: .leading, spacing: WardSpace.s) {
                listChip
                MapLookChip(look: look, choose: choose)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, WardSpace.gutter)
        .padding(.top, WardSpace.s)
    }

    @ViewBuilder
    private var listChip: some View {
        if let list {
            MapListChip(open: list)
        }
    }
}

/// "To sort" and the count of ideas in no folder, the count in the coral
/// ink, as the Sort screen's title has it. A tap opens the list sheet.
private struct MapListChip: View {
    @EnvironmentObject private var notes: NoteStore
    let open: () -> Void

    var body: some View {
        let count: Int = notes.contents(of: nil).count
        Button(action: open) {
            HStack(spacing: WardSpace.xs) {
                Text(IdeasWords.toSort)
                Text(verbatim: "\(count)")
                    .monospacedDigit()
                    .foregroundStyle(Color.wardPrimaryInk)
            }
            .lineLimit(1)
        }
        .buttonStyle(WardChipButtonStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(IdeasWords.toSortSpoken(count))
        .accessibilityHint(IdeasWords.mapListHint)
        .accessibilityAction { open() }
        .accessibilityIdentifier("map-list-chip")
    }
}

/// The look's name and a chevron: the name on the map and the control in
/// one (S05b checklist 4). A tap opens the menu of the four looks, a check
/// on the chosen one.
struct MapLookChip: View {
    let look: MapLook
    let choose: (MapLook) -> Void

    var body: some View {
        Menu {
            ForEach(MapLook.allCases) { item in
                Button {
                    choose(item)
                } label: {
                    if item == look {
                        Label(item.title, systemImage: "checkmark")
                    } else {
                        Text(item.title)
                    }
                }
                .accessibilityAddTraits(item == look ? .isSelected : [])
                .accessibilityIdentifier(item.identifier)
            }
        } label: {
            HStack(spacing: WardSpace.xs) {
                Text(look.title)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
            }
        }
        .menuStyle(.button)
        .buttonStyle(WardChipButtonStyle())
        .accessibilityLabel(IdeasWords.lookChipSpoken(look.title))
        .accessibilityHint(IdeasWords.lookChipHint)
        .accessibilityIdentifier(MapLook.chipIdentifier)
    }
}

/// The list as a sheet over the map (S05b checklist 5): S05's top level,
/// its rows and folders, a folder opening in the sheet with its row back
/// up; the words open the editor, Choose folder the chooser, as on the Sort
/// screen. Full height, as the link picker: at `.medium` the bar's targets
/// drew under 44 points (S05-result A7). Its own notice, since the sheet
/// covers the map's.
struct MapListSheet: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(\.dismiss) private var dismiss
    @State private var folderId: UUID?
    @State private var routes = IdeasListRoutes()

    var body: some View {
        @Bindable var filing = filing
        NavigationStack {
            IdeasList(folderId: $folderId, actions: routes.actions(filing: filing, notes: notes))
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("map-list")
                .frame(maxWidth: IdeasLayout.column)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    IdeasNoticeHost(notice: $filing.notice, undo: { filing.undo($0, notes: notes) })
                        .frame(maxWidth: IdeasLayout.column)
                }
                .background { WardBackground() }
                .lineSpacing(WardSpace.leading)
                .navigationTitle(IdeasWords.toSort)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(IdeasWords.done) { dismiss() }
                            .buttonStyle(.wardCompact)
                            .accessibilityIdentifier("map-list-done")
                    }
                    .sharedBackgroundVisibility(.hidden)
                }
        }
        .ideasListSheets(routes, folderId: $folderId)
        .presentationDetents([.large])
    }
}
