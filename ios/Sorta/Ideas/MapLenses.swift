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

/// The four chips a gutter in from the edges and WardSpace.s under the
/// strip, in one line where they fit and on a second where they don't (a
/// phone's width), so no lens is ever out of sight: a row that scrolled hid
/// the fourth chip whole on a narrow phone (S06d review). As Android's
/// FlowRow. Each is the kit's
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
        MapLensFlow(gap: WardSpace.s) {
            ForEach(IdeaLens.allCases) { lens in
                chip(lens, count: counts[lens] ?? 0)
            }
        }
        .padding(.horizontal, WardSpace.gutter)
        .padding(.top, WardSpace.s)
        .frame(maxWidth: .infinity, alignment: .leading)
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

/// Its children left to right at their own size, `gap` apart, starting a
/// new line under the last when the next would pass the width offered.
struct MapLensFlow: Layout {
    var gap: CGFloat

    private func lines(_ width: CGFloat, _ sizes: [CGSize]) -> [[Int]] {
        var out: [[Int]] = [[]]
        var x: CGFloat = 0
        for (i, size) in sizes.enumerated() {
            let start: CGFloat = out[out.count - 1].isEmpty ? 0 : x + gap
            if !out[out.count - 1].isEmpty && start + size.width > width {
                out.append([i])
                x = size.width
            } else {
                out[out.count - 1].append(i)
                x = start + size.width
            }
        }
        return out
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes: [CGSize] = subviews.map { $0.sizeThatFits(.unspecified) }
        let width: CGFloat = proposal.width ?? .infinity
        var height: CGFloat = 0
        var widest: CGFloat = 0
        for (n, line) in lines(width, sizes).enumerated() where !line.isEmpty {
            let w: CGFloat = line.map { sizes[$0].width }.reduce(0, +) + gap * CGFloat(line.count - 1)
            widest = max(widest, w)
            height += (n > 0 ? gap : 0) + (line.map { sizes[$0].height }.max() ?? 0)
        }
        // an unbounded offer (a scroll view's, say) gets the widest line, never infinity
        return CGSize(width: width.isFinite ? width : widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes: [CGSize] = subviews.map { $0.sizeThatFits(.unspecified) }
        var y: CGFloat = bounds.minY
        for line in lines(bounds.width, sizes) where !line.isEmpty {
            let tall: CGFloat = line.map { sizes[$0].height }.max() ?? 0
            var x: CGFloat = bounds.minX
            for i in line {
                subviews[i].place(at: CGPoint(x: x, y: y + (tall - sizes[i].height) / 2), anchor: .topLeading,
                                  proposal: ProposedViewSize(sizes[i]))
                x += sizes[i].width + gap
            }
            y += tall + gap
        }
    }
}
