import SwiftUI

// S06b around Map mode: the Days chip and the moons row it opens (a night
// for each day, plugins plan idea 7), and the sheet a share opens (idea 10).
// The rules are GraphSkyIdeas.swift's; the words are SkyWords.

/// The strip's Days chip, beside the look chip (MapLookChip): a moon and
/// "Days"; pressed in while the row is open. Space and 2D only.
struct MapDaysChip: View {
    let on: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: WardSpace.xs) {
                Image(systemName: "moonphase.waxing.crescent")
                    .foregroundStyle(Color.wardGlyph)
                    .accessibilityHidden(true)
                Text(SkyWords.days)
                    .lineLimit(1)
            }
        }
        .buttonStyle(WardChipButtonStyle(on: on))
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityHint(SkyWords.daysHint)
        .accessibilityIdentifier("map-days-chip")
    }
}

/// Recent days as moons, newest first, as many as fit and the rest a
/// scroll away (up to MoonDays.most): each as full as that day was busy
/// next to the busiest day shown. Picking one lights its ideas on the map;
/// picking it again, Clear or Done ends it. A raised panel under the strip,
/// as the list of the looks (MapLookList).
struct MoonRow: View {
    let days: [MoonDays.Day]
    let lit: Date?
    let pick: (Date?) -> Void
    let close: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: WardSpace.s) {
            HStack(spacing: WardSpace.s) {
                Text(heading)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("moons-heading")
                if lit != nil {
                    Button(SkyWords.clear) { pick(nil) }
                        .buttonStyle(.wardQuiet)
                        .accessibilityLabel(SkyWords.clearSpoken)
                        .accessibilityIdentifier("moons-clear")
                }
                Button(IdeasWords.done, action: close)
                    .buttonStyle(.wardCompact)
                    .accessibilityIdentifier("moons-done")
            }
            .padding(.horizontal, WardSpace.m)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: WardSpace.xs) {
                    ForEach(Array(days.enumerated()), id: \.element.start) { back, day in
                        moon(day, back: back)
                    }
                }
                .padding(.horizontal, WardSpace.m)
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(SkyWords.recentDays)
        }
        .padding(.vertical, WardSpace.s)
        .frame(maxWidth: GraphPanel.maxWidth)
        .wardRaised(in: RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous), lift: .high,
                    fill: .wardBackground)
        .accessibilityElement(children: .contain)
        .accessibilityAction(.escape) { close() }
        .accessibilityIdentifier("moons-row")
    }

    /// The lit day and its count, else what a moon does.
    private var heading: String {
        guard let lit, let day = days.first(where: { $0.start == lit }) else { return SkyWords.pickADay }
        return SkyWords.moonSpoken(Self.dayName(day.start), day.count)
    }

    static func dayName(_ start: Date) -> String {
        start.formatted(.dateTime.day().month(.wide))
    }

    /// `back`: days before today, 0 for today.
    private func moon(_ day: MoonDays.Day, back: Int) -> some View {
        let on: Bool = lit == day.start
        return Button {
            pick(on ? nil : day.start)
        } label: {
            VStack(spacing: WardSpace.xs) {
                Image(systemName: Self.glyph(day.phase))
                    .wardFont(.title3)
                    .foregroundStyle(day.count == 0 ? Color.wardInkSecondary : Color.wardInk)
                Text(day.start.formatted(.dateTime.day()))
                    .wardFont(.caption.monospacedDigit())
                    .foregroundStyle(on ? Color.wardPrimaryInk : Color.wardInkSecondary)
            }
            .padding(.vertical, WardSpace.xs)
            .frame(minWidth: WardControlSize.target, minHeight: WardControlSize.target)
            .contentShape(Rectangle())
        }
        .buttonStyle(WardItemButtonStyle(chosen: on,
                                         shape: RoundedRectangle(cornerRadius: WardRadius.icon, style: .continuous)))
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
        .accessibilityLabel(SkyWords.moonSpoken(Self.dayName(day.start), day.count))
        .accessibilityHint(SkyWords.moonHint)
        .accessibilityIdentifier("moon-\(back)")
    }

    /// The SF Symbol for a phase: new, crescent, half, gibbous, full.
    static func glyph(_ phase: Int) -> String {
        switch phase {
        case 1: return "moonphase.waxing.crescent"
        case 2: return "moonphase.first.quarter"
        case 3: return "moonphase.waxing.gibbous"
        case 4: return "moonphase.full.moon"
        default: return "moonphase.new.moon"
        }
    }
}

/// Text shared to Sorta, waiting in the sheet: from the Share extension
/// (`sorta://share?text=…&from=…`) or the UI tests' `-SortaShare <text>`.
struct ShareIntake: Identifiable, Equatable {
    let id = UUID()
    let text: String
    /// Only the link's host, or the sending app's name (SharedFrom).
    let mark: String?

    init(text: String, app: String?) {
        self.text = text
        mark = SharedFrom.mark(text: text, app: app)
    }

    init?(url: URL) {
        guard url.scheme == "sorta", url.host == "share",
              let parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let items: [URLQueryItem] = parts.queryItems ?? []
        let text: String = items.first { $0.name == "text" }?.value ?? ""
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        self.init(text: text, app: items.first { $0.name == "from" }?.value)
    }

    /// The UI tests' share, in a Debug build.
    static var launched: ShareIntake? {
        #if DEBUG
        guard let text = UserDefaults.standard.string(forKey: "SortaShare"), !text.isEmpty else { return nil }
        return ShareIntake(text: text, app: UserDefaults.standard.string(forKey: "SortaShareFrom"))
        #else
        return nil
        #endif
    }
}

/// What a share keeps: its words as chips, each kept until tapped, saved
/// as a new idea in To sort with where it came from. Nothing is sent
/// anywhere; Cancel keeps nothing.
struct ShareSheet: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss
    let intake: ShareIntake
    @State private var kept: Set<Int>

    init(intake: ShareIntake) {
        self.intake = intake
        _kept = State(initialValue: Set(SharedFrom.words(intake.text).indices))
    }

    private var words: [String] { SharedFrom.words(intake.text) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: WardSpace.m) {
                    Text(SkyWords.shareHint)
                        .wardFont(.subheadline)
                        .foregroundStyle(Color.wardInkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    WordFlow(spacing: WardSpace.s) {
                        ForEach(Array(words.enumerated()), id: \.offset) { k, word in
                            chip(word, at: k)
                        }
                    }
                    if let mark = intake.mark { SharedFromMark(mark: mark) }
                }
                .padding(WardSpace.gutter)
                .frame(maxWidth: IdeasLayout.column, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
            .background { WardBackground() }
            .navigationTitle(SkyWords.shareTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(IdeasWords.cancel) { dismiss() }
                        .buttonStyle(.wardQuiet)
                        .accessibilityIdentifier("share-cancel")
                }
                .sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .confirmationAction) {
                    Button(SkyWords.save, action: save)
                        .buttonStyle(.wardCompact)
                        .disabled(kept.isEmpty)
                        .accessibilityIdentifier("share-save")
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
        .accessibilityIdentifier("share-sheet")
    }

    private func chip(_ word: String, at k: Int) -> some View {
        let on: Bool = kept.contains(k)
        return Button {
            if on { kept.remove(k) } else { kept.insert(k) }
        } label: {
            Text(verbatim: word)
                .strikethrough(!on)
                .lineLimit(1)
        }
        .buttonStyle(WardChipButtonStyle(on: on))
        .accessibilityValue(on ? SkyWords.kept : SkyWords.leftOut)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func save() {
        let text: String = SharedFrom.kept(words, keep: kept)
        guard !text.isEmpty else { return }
        _ = notes.create(title: text, kind: .idea, folderId: nil, sharedFrom: intake.mark)
        dismiss()
    }
}

/// Words laid out as text is: left to right, a new line when one is full.
struct WordFlow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows: [[(Int, CGSize)]] = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        var height: CGFloat = 0
        var widest: CGFloat = 0
        for row in rows {
            let tall: CGFloat = row.map(\.1.height).max() ?? 0
            let wide: CGFloat = row.map(\.1.width).reduce(0, +) + spacing * CGFloat(max(row.count - 1, 0))
            height += tall
            widest = max(widest, wide)
        }
        height += spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y: CGFloat = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x: CGFloat = bounds.minX
            let tall: CGFloat = row.map(\.1.height).max() ?? 0
            for (k, size) in row {
                subviews[k].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += tall + spacing
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [[(Int, CGSize)]] {
        var rows: [[(Int, CGSize)]] = [[]]
        var x: CGFloat = 0
        for k in subviews.indices {
            var size: CGSize = subviews[k].sizeThatFits(.unspecified)
            size.width = min(size.width, width)
            if x > 0 && x + size.width > width {
                rows.append([])
                x = 0
            }
            rows[rows.count - 1].append((k, size))
            x += size.width + spacing
        }
        return rows.filter { !$0.isEmpty }
    }
}
