import SwiftUI

/// Map mode's top strip (S05b checklist 3): leading, the "To sort n" chip,
/// which opens the list sheet; trailing, the look chip. Each is the kit's
/// chip, a gutter in from the edges and WardSpace.s under the safe area.
/// When one row can't hold both, as at the largest text sizes, they stack.
/// `list` is nil on two panes, where the Sort screen is beside the map.
struct MapTopStrip: View {
    let look: MapLook
    /// Opens the list of the looks (IdeasMapView draws it, MapLookList).
    let openLooks: () -> Void
    var list: (() -> Void)?

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: WardSpace.s) {
                listChip
                Spacer(minLength: 0)
                MapLookChip(look: look, open: openLooks)
            }
            VStack(alignment: .leading, spacing: WardSpace.s) {
                listChip
                MapLookChip(look: look, open: openLooks)
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
/// one (S05b checklist 4). A tap opens the list of the four looks, a check
/// on the chosen one.
///
/// The list is drawn by IdeasMapView over the map (MapLookList), not shown
/// as a Menu or a popover: both closed the moment the 3D map behind them
/// finished laying out ("Laying out your ideas" giving way to the scene;
/// the recordings of 605a19c's and a7054fe's MapModeTests), so a tap in the
/// map's first seconds lost the list. Android's is a DropdownMenu, in its
/// own window the same way.
struct MapLookChip: View {
    let look: MapLook
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            HStack(spacing: WardSpace.xs) {
                Text(look.title)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
            }
        }
        .buttonStyle(WardChipButtonStyle())
        .accessibilityLabel(IdeasWords.lookChipSpoken(look.title))
        .accessibilityHint(IdeasWords.lookChipHint)
        .accessibilityIdentifier(MapLook.chipIdentifier)
    }
}

/// The four looks, one row each, at least a target tall, a check beside the
/// chosen one: what the Menu showed, on a raised panel as the map's others
/// (GraphMapPanels). Modal to VoiceOver, which can close it (escape).
struct MapLookList: View {
    let look: MapLook
    let choose: (MapLook) -> Void
    let close: () -> Void
    /// VoiceOver starts on the chosen look, as in a menu.
    @AccessibilityFocusState private var focused: MapLook?

    var body: some View {
        // its own width where that fits; else the width it is given, its
        // titles wrapping (the largest text sizes)
        ViewThatFits(in: .horizontal) {
            panel.fixedSize(horizontal: true, vertical: false)
            panel.frame(maxWidth: 360)
        }
        .wardRaised(in: RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous), lift: .high,
                    fill: .wardBackground)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape) { close() }
        .accessibilityIdentifier("map-look-list")
        // a moment after it appears, once VoiceOver has seen its rows
        .task {
            try? await Task.sleep(for: .milliseconds(100))
            focused = look
        }
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(MapLook.allCases) { item in
                Button {
                    choose(item)
                } label: {
                    HStack(spacing: WardSpace.s) {
                        Image(systemName: "checkmark")
                            .foregroundStyle(Color.wardPrimaryInk)
                            .opacity(item == look ? 1 : 0)
                            .accessibilityHidden(true)
                        Text(item.title)
                            .wardFont(.body)
                            .foregroundStyle(Color.wardInk)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, WardSpace.m)
                    .frame(minHeight: WardControlSize.target)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(item == look ? .isSelected : [])
                .accessibilityIdentifier(item.identifier)
                .accessibilityFocused($focused, equals: item)
            }
        }
        .padding(.vertical, WardSpace.xs)
        .frame(minWidth: 200)
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
