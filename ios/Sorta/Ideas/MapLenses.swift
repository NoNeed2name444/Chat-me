import SwiftUI

// The lenses (S06d A1-A3, plugins-plan idea 8), as Android's MapLensRow:
// four chips under Map mode's top strip, each a question asked of every
// idea. A lens on shows only the ideas that pass; two on, only those that
// pass both. Held while Map mode is, never stored, as the map's filter.

extension Note: IdeaLensNote {}

extension IdeaLens {
    /// The chip's name.
    var title: String {
        switch self {
        case .questions: return IdeasWords.lensQuestions
        case .lateNight: return IdeasWords.lensLateNight
        case .unfinished: return IdeasWords.lensUnfinished
        case .alone: return IdeasWords.lensAlone
        }
    }

    /// What a tap on it does while it is off.
    var hint: String {
        switch self {
        case .questions: return IdeasWords.lensQuestionsHint
        case .lateNight: return IdeasWords.lensLateNightHint
        case .unfinished: return IdeasWords.lensUnfinishedHint
        case .alone: return IdeasWords.lensAloneHint
        }
    }

    /// The chip's identifier, as Android's test tag: "map-lens-late-night".
    var identifier: String { "map-lens-\(rawValue)" }

    /// The row's identifier.
    static let rowIdentifier = "map-lens-row"
}

/// The four chips in a row a gutter in from the edges and WardSpace.s under
/// the strip, scrolling sideways where they don't fit. Each is the kit's
/// chip with its chosen state and its count (how many ideas show with it
/// on), the count in the coral ink as the To sort chip's; VoiceOver reads
/// its name and count, that it is selected, and what a tap does. A tap
/// turns it on or off at once, with no movement, so Reduce Motion has
/// nothing to stop.
struct MapLensRow: View {
    @EnvironmentObject private var notes: NoteStore
    @Binding var on: Set<IdeaLens>

    var body: some View {
        let counts: [IdeaLens: Int] = IdeaLens.counts(on, notes: notes.notes, edges: notes.allEdges())
        ScrollView(.horizontal) {
            HStack(spacing: WardSpace.s) {
                ForEach(IdeaLens.allCases) { lens in
                    chip(lens, count: counts[lens] ?? 0)
                }
            }
            .padding(.horizontal, WardSpace.gutter)
        }
        .scrollIndicators(.hidden)
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .padding(.top, WardSpace.s)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(IdeasWords.lenses)
        .accessibilityIdentifier(IdeaLens.rowIdentifier)
    }

    private func chip(_ lens: IdeaLens, count: Int) -> some View {
        let chosen: Bool = on.contains(lens)
        return Button {
            toggle(lens)
        } label: {
            HStack(spacing: WardSpace.xs) {
                Text(lens.title)
                Text(verbatim: "\(count)")
                    .monospacedDigit()
                    .foregroundStyle(Color.wardPrimaryInk)
            }
            .lineLimit(1)
        }
        .buttonStyle(WardChipButtonStyle(on: chosen))
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(chosen ? [.isButton, .isSelected] : [.isButton])
        .accessibilityLabel(IdeasWords.lensSpoken(lens.title, count))
        .accessibilityHint(chosen ? IdeasWords.lensOffHint : lens.hint)
        .accessibilityAction { toggle(lens) }
        .accessibilityIdentifier(lens.identifier)
    }

    private func toggle(_ lens: IdeaLens) {
        if on.contains(lens) { on.remove(lens) } else { on.insert(lens) }
    }
}
