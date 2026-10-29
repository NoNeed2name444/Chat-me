#if canImport(SwiftUI)
import SwiftUI

// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// Ward/WardStatesGallery.swift), for Sorta's one look.

/// Every Ward control in each of its states, drawn with the real styles
/// (WardControls, PopTileStyle): the states gallery, shot in light and dark.
///
/// Three columns: at rest, held (drawn held through `wardShowsPressed`, with
/// no finger on it) and disabled. A control that can be chosen has two rows,
/// not chosen and chosen, so its six states (WardControlStates) are all on
/// one screen. WardStatesTests checks the same states' colours in both
/// modes on Linux.
struct WardStatesGallery: View {
    let section: WardSection

    /// A column of the gallery: the state its controls are drawn in.
    enum Column: CaseIterable {
        case rest, held, disabled

        var title: LocalizedStringKey {
            switch self {
            case .rest: return "Rest"
            case .held: return "Held"
            case .disabled: return "Disabled"
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("States · \(section.galleryTitle)")
                    .wardSmallCaps()
                    .accessibilityAddTraits(.isHeader)
                    .padding(.bottom, WardSpace.s)
                HStack(spacing: WardSpace.s) {
                    ForEach(Column.allCases, id: \.self) { column in
                        Text(column.title)
                            .wardSmallCaps()
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.bottom, WardSpace.m)

                VStack(spacing: WardSpace.s) {
                    states { Button("Save") {}.buttonStyle(.wardPrimary) }
                    states { Button("Skip") {}.buttonStyle(.wardSecondary) }
                    states { Button("Delete") {}.buttonStyle(.wardDestructive) }
                    states { Button("Done") {}.buttonStyle(.wardCompact) }
                    states { Button("Cancel") {}.buttonStyle(.wardQuiet) }
                }
                .padding(.bottom, WardSpace.l)

                VStack(spacing: WardSpace.s) {
                    states { chipAndCircle(chosen: false) }
                    states { chipAndCircle(chosen: true) }
                }
                .padding(.bottom, WardSpace.l)

                VStack(spacing: WardSpace.s) {
                    states { toggle(on: false) }
                    states { toggle(on: true) }
                    states {
                        WardSegmented(selection: .constant(0), options: [0, 1]) { option in
                            Image(systemName: option == 0 ? "list.bullet" : "square.grid.2x2")
                                .accessibilityLabel(option == 0 ? Text("List") : Text("Grid"))
                        }
                    }
                }
                .padding(.bottom, WardSpace.l)

                VStack(spacing: WardSpace.s) {
                    states { tile(chosen: false) }
                    states { tile(chosen: true) }
                }
            }
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.s)
        }
        .scrollBounceBehavior(.basedOnSize)
        // found by GalleryTests, which scrolls it to its end
        .accessibilityIdentifier("gallery")
    }

    /// One row: the control at rest, held and disabled, a column each.
    private func states<Control: View>(@ViewBuilder _ control: @escaping () -> Control) -> some View {
        HStack(spacing: WardSpace.s) {
            ForEach(Column.allCases, id: \.self) { column in
                control()
                    .environment(\.wardShowsPressed, column == .held)
                    .disabled(column == .disabled)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    /// A chip that is a button and a round button, chosen or not.
    private func chipAndCircle(chosen: Bool) -> some View {
        HStack(spacing: WardSpace.s) {
            Button("Pinned") {}
                .buttonStyle(WardChipButtonStyle(on: chosen))
            Button {} label: {
                Image(systemName: "plus")
            }
            .buttonStyle(WardCircleButtonStyle(on: chosen))
            .accessibilityLabel("Add")
        }
    }

    /// The switch alone, its words for VoiceOver only: a third of the
    /// screen has no room for them beside it. WardToggleStyle draws its
    /// label itself, so labelsHidden left the words in, spilling into the
    /// next column (preview/ward-states-1).
    private func toggle(on: Bool) -> some View {
        Toggle(isOn: .constant(on)) { EmptyView() }
            .accessibilityLabel("Reminders")
            .fixedSize()
    }

    /// A tile on the raised plane, chosen or not, with a label that colours
    /// itself as the app's tiles do: Chart Ink words and a glyph in the
    /// accent, Biro Grey when disabled.
    private func tile(chosen: Bool) -> some View {
        Button {} label: {
            WardStatesTileLabel(title: section.galleryTitle, symbol: section.gallerySymbol)
        }
        .buttonStyle(PopTileStyle(cornerRadius: WardRadius.card, selected: chosen))
        // the chosen tile is the gallery's last row (GalleryTests)
        .accessibilityIdentifier(chosen ? "gallery.lastTile" : "gallery.tile")
    }
}

private struct WardStatesTileLabel: View {
    let title: String
    let symbol: String
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        VStack(spacing: WardSpace.xs) {
            Image(systemName: symbol)
                .wardFont(.title3.weight(.semibold))
                .foregroundStyle(enabled ? Color.wardPrimaryInk : Color.wardDisabledInk)
                .accessibilityHidden(true)
            Text(title)
                .wardFont(.subheadline.weight(.semibold))
                .foregroundStyle(enabled ? Color.wardInk : Color.wardDisabledInk)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .padding(.horizontal, WardSpace.s)
        .frame(maxWidth: .infinity, minHeight: 64)
    }
}

private extension WardSection {
    /// The look's name, for the gallery's heading and its tile.
    var galleryTitle: String {
        switch self {
        case .sorta: return String(localized: "Ideas", comment: "The states gallery's sample tile, named after Sorta's ideas")
        }
    }

    /// The look's glyph, for the gallery's tile.
    var gallerySymbol: String {
        switch self {
        case .sorta: return "lightbulb"
        }
    }
}
#endif
