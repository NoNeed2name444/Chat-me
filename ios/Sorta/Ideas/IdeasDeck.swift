import SwiftUI

// The sorting deck, "You were saying…", the frost and the low tide (S06a):
// the views over the rules in SortDeck.swift, as Android's ui/ideas/
// IdeasDeck.kt draws them. The deck is To sort shown as cards, a switch
// away from the list; IdeasFiling keeps its order, the folders' frames and
// what this launch has offered.

/// The ideas' clock: now, or, in a test build, `-SortaNow <ISO 8601>`, so
/// the frost and the tide never move a render as the real days pass.
enum IdeasClock {
    static let fixed: Date? = {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard let i = arguments.firstIndex(of: "-SortaNow"), i + 1 < arguments.count else { return nil }
        return ISO8601DateFormatter().date(from: arguments[i + 1])
        #else
        return nil
        #endif
    }()

    static var now: Date { fixed ?? Date() }
}

/// The deck's stored choices, under Android's keys.
enum DeckKeys {
    /// To sort shown as cards (Bool).
    static let sortDeck = "vignette.ideas.sortDeck"
    /// When the last low tide came, in seconds since 1970 (Double).
    static let lastTide = "vignette.ideas.lastTide"

    /// `-SortaDeck on|off` and `-SortaLastTide <ISO date>`, a UI test's: the
    /// two choices set before the screen reads them, so each test starts on
    /// the list (or the deck) and on a known last tide, whatever an earlier
    /// test left. Empty without the arguments (IdeasRoot.storeURL, DEBUG only).
    static func launchDefaults(arguments: [String]) -> [String: Any] {
        func value(after flag: String) -> String? {
            guard let at = arguments.firstIndex(of: flag), at + 1 < arguments.count else { return nil }
            return arguments[at + 1]
        }
        var out: [String: Any] = [:]
        switch value(after: "-SortaDeck") {
        case "on": out[sortDeck] = true
        case "off": out[sortDeck] = false
        default: break
        }
        if let text = value(after: "-SortaLastTide"), let date = ISO8601DateFormatter().date(from: text) {
            out[lastTide] = date.timeIntervalSince1970
        }
        return out
    }
}

/// The deck's measures the kit has no entry for, as Android's DeckLayout.
enum DeckLayout {
    static let cardWidth: CGFloat = 360
    static let cardHeight: CGFloat = 168
    static let wellWidth: CGFloat = 96
    /// A target and the gap under it.
    static let row: CGFloat = WardControlSize.target + WardSpace.s
    /// A target's width in the folders pane.
    static let paneTarget: CGFloat = 200
    /// At most this many targets round the deck on a phone, and in the pane.
    static let phoneCap = 8
    static let paneCap = 12
}

/// The frost's look (A6): a cool wash over a card, deeper by step. The kit
/// has no cool tone, so it is named here, as Android's IdeaFrostLook.
enum IdeaFrostLook {
    /// The ice by day: pale, over bone.
    static let dayIce: UInt32 = 0xD6E8F5
    /// The ice by night: a deep blue that cools a card without lifting it,
    /// so a forgotten idea never outshines the rest, and every ink on it
    /// keeps its contrast.
    static let nightIce: UInt32 = 0x1A3048
    /// The board's wash at each level, 0 to 3, by day and by night.
    static let dayOpacity: [Double] = [0, 0.20, 0.40, 0.60]
    static let nightOpacity: [Double] = [0, 0.30, 0.50, 0.70]
    /// The shell's strength in Space, at each level, 0 to 3.
    static let opacity: [Double] = [0, 0.18, 0.32, 0.46]

    /// The board's wash at `level`, in the scheme it is drawn in.
    static func wash(_ level: Int) -> Color {
        let step: Int = max(0, min(3, level))
        return Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? Color.uiColor(nightIce, alpha: nightOpacity[step])
                : Color.uiColor(dayIce, alpha: dayOpacity[step])
        })
    }
    /// The melt, in seconds (the kit's longest change).
    static let melt: Double = 0.32

    /// The frost on an idea now.
    static func level(_ note: Note) -> Int {
        IdeaFrost.level(openedAt: note.openedAt, createdAt: note.createdAt, now: IdeasClock.now)
    }
}

/// When the deck shows, and in what order: one rule for the Sort screen and
/// the window that puts the folders pane beside it.
enum IdeasDeckRule {
    /// To sort's ids as the deck orders them now.
    @MainActor
    static func order(_ filing: IdeasFiling, notes: NoteStore) -> [UUID] {
        var deck = filing.deck
        deck.sync(notes.contents(of: nil).map(\.id))
        return deck.order
    }

    /// The tide's ideas still in the store.
    @MainActor
    static func tide(_ filing: IdeasFiling, notes: NoteStore) -> [UUID] {
        filing.tide.filter { notes.note($0) != nil }
    }

    /// On To sort's top level: the tide's while it is up, else To sort's when chosen and not empty.
    @MainActor
    static func shown(folderId: UUID?, sortDeck: Bool, filing: IdeasFiling, notes: NoteStore) -> Bool {
        guard folderId == nil else { return false }
        return !tide(filing, notes: notes).isEmpty || (sortDeck && !notes.contents(of: nil).isEmpty)
    }

    /// The most-used folders (ideas filed directly in them), as many as `slots`.
    @MainActor
    static func targets(_ notes: NoteStore, slots: Int) -> [NoteFolder] {
        var counts: [UUID: Int] = [:]
        for note in notes.notes { if let f = note.folderId { counts[f, default: 0] += 1 } }
        let ids = DeckFolders.pick(notes.folders.map { (id: $0.id, name: $0.name, count: counts[$0.id] ?? 0) }, slots: slots)
        return ids.compactMap { notes.folder($0) }
    }

    /// How many targets fit in the folders pane, `size` big.
    static func paneSlots(_ size: CGSize) -> Int {
        let columns = max(1, Int(size.width / (DeckLayout.paneTarget + WardSpace.s)))
        return DeckFolders.slots(height: Double(size.height - WardControlSize.target * 2), row: Double(DeckLayout.row),
                                 columns: columns, cap: DeckLayout.paneCap)
    }

    /// The idea "You were saying…" offers now, or nil.
    @MainActor
    static func offer(_ notes: NoteStore) -> Note? {
        IdeaStops.offer(notes.notes.map { (id: $0.id, stoppedAt: $0.stoppedAt, updatedAt: $0.updatedAt) }).flatMap { notes.note($0) }
    }
}

/// A marked idea's dotted edge (A5), round a row or a card.
struct UnfinishedEdge: ViewModifier {
    let marked: Bool
    var radius: CGFloat = WardRadius.field
    var inset: CGFloat = WardSpace.s

    func body(content: Content) -> some View {
        content.overlay {
            if marked {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(Color.wardInkSecondary, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [1.5, 4]))
                    .padding(inset)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }
}

extension View {
    func unfinishedEdge(_ marked: Bool, radius: CGFloat = WardRadius.field, inset: CGFloat = WardSpace.s) -> some View {
        modifier(UnfinishedEdge(marked: marked, radius: radius, inset: inset))
    }
}

/// List or cards, at To sort's title row's end, beside More.
struct DeckToggle: View {
    @AppStorage(DeckKeys.sortDeck) private var on = false

    var body: some View {
        Button {
            on.toggle()
        } label: {
            Image(systemName: on ? "list.bullet" : "rectangle.stack")
        }
        .buttonStyle(.wardCircle)
        .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        .accessibilityLabel(on ? IdeasWords.showAsList : IdeasWords.showAsCards)
        .accessibilityIdentifier("deck-toggle")
    }
}

/// "You were saying…": the marked idea changed last, at To sort's top until
/// it is taken or put away, once per launch.
struct ResumeOffer: View {
    let note: Note
    let open: () -> Void
    let dismiss: () -> Void

    var body: some View {
        let line: String = IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled
        HStack(alignment: .top, spacing: WardSpace.s) {
            VStack(alignment: .leading, spacing: WardSpace.xs) {
                Text(IdeasWords.offerLabel)
                    .wardFont(.footnote.weight(.semibold))
                    .foregroundStyle(Color.wardInkSecondary)
                    .accessibilityAddTraits(.isHeader)
                Text(line)
                    .wardFont(.body)
                    .foregroundStyle(Color.wardInk)
                    .lineLimit(2)
                if let before = Self.lastWords(note) {
                    Text("\u{2026}" + before)
                        .wardFont(.subheadline)
                        .foregroundStyle(Color.wardInkSecondary)
                        .lineLimit(2)
                        .truncationMode(.head)
                }
                Button(IdeasWords.carryOn, action: open)
                    .buttonStyle(.wardCompact)
                    .fixedSize()
                    .padding(.top, WardSpace.xs)
                    .accessibilityLabel(IdeasWords.carryOnWith(line))
                    .accessibilityIdentifier("resume-carry-on")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: dismiss) {
                Image(systemName: "xmark")
            }
            .buttonStyle(.wardCircle)
            .dynamicTypeSize(...DynamicTypeSize.accessibility3)
            .accessibilityLabel(IdeasWords.notNow)
            .accessibilityIdentifier("resume-not-now")
        }
        .padding(.horizontal, WardSpace.m)
        .padding(.vertical, WardSpace.m)
        .background { WardRowTile().padding(-WardSpace.s) }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resume-offer")
    }

    /// The last words before the caret, as the person left them.
    static func lastWords(_ note: Note) -> String? {
        let utf16 = note.body.utf16
        let stop = min(max(note.stoppedAt ?? utf16.count, 0), utf16.count)
        let end = utf16.index(utf16.startIndex, offsetBy: stop)
        let head = String(note.body[..<(end.samePosition(in: note.body) ?? note.body.endIndex)])
        guard let last = head.split(whereSeparator: \.isNewline).last(where: { !$0.allSatisfy(\.isWhitespace) }) else { return nil }
        return String(last.trimmingCharacters(in: .whitespaces).suffix(80))
    }
}

/// The tide's head: "Low tide" as the title, and its close at the end.
struct TideTitle: View {
    let close: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: WardSpace.s) {
            VStack(alignment: .leading, spacing: WardSpace.xs) {
                Text(IdeasWords.lowTide)
                    .wardFont(.title1)
                    .foregroundStyle(Color.wardInk)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("low-tide")
                Text(IdeasWords.lowTideLine)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInkSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: close) {
                Image(systemName: "xmark")
            }
            .buttonStyle(.wardCircle)
            .dynamicTypeSize(...DynamicTypeSize.accessibility3)
            .accessibilityLabel(IdeasWords.closeLowTide)
        }
    }
}

/// What the deck's card offers: To sort's (file, Later, Choose folder), or
/// the tide's (Keep, Link to an idea…, Let go).
enum DeckActions {
    case sort(file: (UUID, UUID) -> Void, later: (UUID) -> Void, choose: (UUID) -> Void)
    case tide(keep: (UUID) -> Void, link: (UUID) -> Void, letGo: (UUID) -> Void)
}

/// The deck: the top card low in the area, under the thumb, the Later well
/// above it. With `paneTargets` nil (a phone held upright), the most-used
/// folders that fit stand in two columns round the well, at its foot, a
/// thumb's reach above the card; else they are the folders pane's, beside
/// it, only their frames come here (IdeasFiling), and the card and its well
/// stand in the middle. Where there is no room above the card (a phone on
/// its side, the keyboard up), the card stands alone: its buttons and its
/// spoken actions still do everything.
struct DeckArea: View {
    let order: [UUID]
    let actions: DeckActions
    let paneTargets: [NoteFolder]?
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing

    var body: some View {
        GeometryReader { geometry in
            let height = geometry.size.height
            // the card's zone: half the area at most, but never less than the card and the cards under it
            let cardZone = max(min(height * 0.5, DeckLayout.cardHeight + WardSpace.l * 3), DeckLayout.cardHeight + WardSpace.s * 2)
            let above = max(0, height - cardZone - WardSpace.m)
            let roomy: Bool = above >= WardControlSize.target * 2
            let sorting: Bool = { if case .sort = actions { return true } else { return false } }()
            // two panes (paneTargets not nil, empty for the tide): the card and its well in the middle
            let centred: Bool = paneTargets != nil
            let edges = paneTargets == nil && sorting && roomy
            let slots = edges ? DeckFolders.slots(height: Double(above), row: Double(DeckLayout.row), columns: 2, cap: DeckLayout.phoneCap) : 0
            let targets: [NoteFolder] = paneTargets ?? (edges ? IdeasDeckRule.targets(notes, slots: slots) : [])
            if let top = order.first.flatMap({ notes.note($0) }) {
                let behind: Int = min(order.count - 1, 2)
                let card = DeckCard(note: top, behind: behind, actions: actions, targets: sorting ? targets : [])
                    .id(top.id)
                    .padding(.bottom, WardSpace.s * CGFloat(behind))
                if centred {
                    VStack(spacing: WardSpace.l) {
                        Spacer(minLength: 0)
                        if roomy {
                            wells(top: top, targets: [], width: geometry.size.width, height: WardControlSize.target * 2)
                        }
                        card
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    VStack(spacing: WardSpace.m) {
                        if roomy {
                            wells(top: top, targets: edges ? targets : [], width: geometry.size.width, height: above)
                                .frame(height: above, alignment: .top)
                        }
                        Spacer(minLength: 0)
                        card
                    }
                    .frame(maxWidth: .infinity)
                }
            } else {
                Text(IdeasWords.allSorted)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInkSecondary)
                    .padding(.horizontal, WardSpace.s)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, WardSpace.gutter)
        .padding(.bottom, WardSpace.l)
    }

    /// The targets in rows of two at the foot of the room above the card, the
    /// most used nearest it, the well at its top between the two sides; or the well alone (two
    /// panes, and the tide, where it is Keep).
    @ViewBuilder
    private func wells(top: Note, targets: [NoteFolder], width: CGFloat, height: CGFloat) -> some View {
        let well = LaterWell(words: {
            if case .tide = actions { return IdeasWords.keep } else { return IdeasWords.later }
        }()) {
            switch actions {
            case let .sort(_, later, _): later(top.id)
            case let .tide(keep, _, _): keep(top.id)
            }
        }
        if case let .sort(file, _, _) = actions, !targets.isEmpty {
            let sides = max(0, (width - DeckLayout.wellWidth - WardSpace.m * 2) / 2)
            // rows of two, the most used in the row nearest the card
            let rows: [[NoteFolder]] = stride(from: 0, to: targets.count, by: 2).map { Array(targets[$0 ..< min($0 + 2, targets.count)]) }
            ZStack(alignment: .top) {
                well.frame(width: DeckLayout.wellWidth)
                VStack(spacing: WardSpace.s) {
                    Spacer(minLength: 0)
                    ForEach(rows.reversed(), id: \.[0].id) { pair in
                        HStack(alignment: .bottom, spacing: 0) {
                            DeckTarget(folder: pair[0]) { file(top.id, pair[0].id) }
                                .frame(width: sides)
                            Spacer(minLength: 0)
                            if pair.count > 1 {
                                DeckTarget(folder: pair[1]) { file(top.id, pair[1].id) }
                                    .frame(width: sides)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
        } else {
            well.frame(width: DeckLayout.wellWidth)
                .frame(maxWidth: .infinity)
        }
    }
}

/// The well a card is flicked up into: Later (Keep, in the tide); a tap does
/// the same.
struct LaterWell: View {
    let words: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: WardSpace.xs) {
                Image(systemName: "arrow.up")
                Text(words)
                    .wardFont(.footnote)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(Color.wardInkSecondary)
            .padding(WardSpace.s)
            .frame(maxWidth: .infinity, minHeight: WardControlSize.target * 2)
            .overlay {
                RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous)
                    .strokeBorder(Color.wardInkSecondary, style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(words)
        .accessibilityIdentifier("deck-later")
    }
}

/// A folder to flick to, and to tap: its glyph and name, pressed in while a
/// card is over it. Its frame, in the window, goes to IdeasFiling.
struct DeckTarget: View {
    let folder: NoteFolder
    let action: () -> Void
    @Environment(IdeasFiling.self) private var filing

    var body: some View {
        let over: Bool = filing.hoveredTarget == folder.id
        Button(action: action) {
            HStack(spacing: WardSpace.s) {
                Image(systemName: "folder")
                    .foregroundStyle(Color.wardGlyph)
                Text(folder.name)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInk)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, WardSpace.m)
            .padding(.vertical, WardSpace.s)
            .frame(maxWidth: .infinity, minHeight: WardControlSize.target)
            .wardRelief(in: RoundedRectangle(cornerRadius: WardRadius.field, style: .continuous), pressed: over)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
            filing.targetFrames[folder.id] = frame
        }
        .onDisappear { filing.targetFrames[folder.id] = nil }
        .accessibilityLabel(IdeasWords.fileIn(folder.name))
        .accessibilityIdentifier("deck-target")
    }
}

/// The card under the thumb: its first line, and its buttons at its foot. A
/// drag carries it; let go, it files where the finger was flung (DeckFlick),
/// goes to Later (is kept, in the tide), or springs back. Filed or put
/// back, it flies off first, unless Reduce Motion is on. VoiceOver reads its
/// words, then offers each folder's "File in …", Later and Choose folder
/// (the tide's Keep, Link to an idea… and Let go).
struct DeckCard: View {
    let note: Note
    let behind: Int
    let actions: DeckActions
    let targets: [NoteFolder]
    @Environment(IdeasFiling.self) private var filing
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var offset: CGSize = .zero
    @State private var leaving = false

    var body: some View {
        let line: String = IdeasDesk.firstLine(title: note.title, body: note.body) ?? IdeasWords.untitled
        let shape = RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            Text(line)
                .wardFont(.body)
                .foregroundStyle(Color.wardInk)
                .lineLimit(4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel(line)
                .accessibilityValue(note.stoppedAt != nil ? IdeasWords.unfinished : "")
                .accessibilityActions { spokenActions }
                .accessibilityIdentifier("deck-card")
            // the buttons at the card's foot, the room above them where a thumb grabs
            Spacer(minLength: WardSpace.m)
            buttons
        }
        .frame(maxWidth: .infinity, minHeight: DeckLayout.cardHeight - WardSpace.l * 2, alignment: .topLeading)
        .fixedSize(horizontal: false, vertical: true)
        .padding(WardSpace.l)
        .wardRaised(in: shape, lift: .high, fill: .wardRaised)
        .unfinishedEdge(note.stoppedAt != nil, radius: WardRadius.card, inset: 0)
        .scaleEffect(leaving ? 0.6 : 1)
        .opacity(leaving ? 0 : 1)
        .offset(offset)
        .rotationEffect(.degrees(reduceMotion ? 0 : Double(max(-8, min(8, offset.width / 40)))))
        .gesture(drag)
        // the cards under it, a step down each, the top card's size: the deck's depth, not its count
        .background {
            ForEach(Array(stride(from: behind, through: 1, by: -1)), id: \.self) { k in
                Color.clear
                    .wardRaised(in: shape, lift: .low, fill: .wardRaised)
                    .padding(.horizontal, WardSpace.s * CGFloat(k))
                    .offset(y: WardSpace.s * CGFloat(k))
                    .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: DeckLayout.cardWidth)
    }

    @ViewBuilder
    private var buttons: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: WardSpace.s) { buttonRow }
            VStack(alignment: .leading, spacing: WardSpace.xs) { buttonRow }
        }
    }

    @ViewBuilder
    private var buttonRow: some View {
        switch actions {
        case let .sort(_, later, choose):
            Button(IdeasWords.later) { later(note.id) }
                .buttonStyle(.wardCompact).fixedSize()
                .accessibilityIdentifier("deck-keep-later")
            Button(IdeasWords.chooseFolder) { choose(note.id) }
                .buttonStyle(.wardCompact).fixedSize()
                .accessibilityIdentifier("deck-choose")
        case let .tide(keep, link, letGo):
            Button(IdeasWords.keep) { keep(note.id) }
                .buttonStyle(.wardCompact).fixedSize()
                .accessibilityIdentifier("tide-keep")
            Button(IdeasWords.linkTo) { link(note.id) }
                .buttonStyle(.wardCompact).fixedSize()
                .accessibilityIdentifier("tide-link")
            // compact as its neighbours, its word in the danger red, as the rows' Delete; Undo brings the idea back
            Button {
                letGo(note.id)
            } label: {
                Text(IdeasWords.letGo).foregroundStyle(Color.wardDanger)
            }
                .buttonStyle(.wardCompact).fixedSize()
                .accessibilityIdentifier("tide-let-go")
        }
    }

    @ViewBuilder
    private var spokenActions: some View {
        switch actions {
        case let .sort(file, later, choose):
            ForEach(targets) { folder in
                Button(IdeasWords.fileIn(folder.name)) { file(note.id, folder.id) }
            }
            Button(IdeasWords.later) { later(note.id) }
            Button(IdeasWords.chooseFolder) { choose(note.id) }
        case let .tide(keep, link, letGo):
            Button(IdeasWords.keep) { keep(note.id) }
            Button(IdeasWords.linkTo) { link(note.id) }
            Button(IdeasWords.letGo) { letGo(note.id) }
        }
    }

    /// Where the card goes, judged from the finger: from where it took hold
    /// (`start`, in the window) to `translation` past it.
    private func outcome(from start: CGPoint, _ translation: CGSize) -> DeckFlick.Outcome<UUID> {
        var flicks: [DeckFlick.Target<UUID>] = []
        if case .sort = actions {
            for folder in targets {
                if let r = filing.targetFrames[folder.id] {
                    flicks.append(DeckFlick.Target(id: folder.id, minX: Double(r.minX), minY: Double(r.minY),
                                                   maxX: Double(r.maxX), maxY: Double(r.maxY)))
                }
            }
        }
        return DeckFlick.outcome(fromX: Double(start.x), fromY: Double(start.y),
                                 toX: Double(start.x + translation.width), toY: Double(start.y + translation.height),
                                 targets: flicks)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .global)
            .onChanged { value in
                guard !leaving else { return }
                offset = value.translation
                if case let .folder(id) = outcome(from: value.startLocation, value.translation) {
                    filing.hoveredTarget = id
                } else {
                    filing.hoveredTarget = nil
                }
            }
            .onEnded { value in
                filing.hoveredTarget = nil
                guard !leaving else { return }
                let landing = DeckFlick.landing(x: Double(value.translation.width), y: Double(value.translation.height),
                                                vx: Double(value.velocity.width), vy: Double(value.velocity.height))
                let carried = CGSize(width: landing.x, height: landing.y)
                switch outcome(from: value.startLocation, carried) {
                case let .folder(id):
                    leave(to: carried) { if case let .sort(file, _, _) = actions { file(note.id, id) } }
                case .later:
                    leave(to: CGSize(width: carried.width, height: min(carried.height, -DeckLayout.cardHeight * 2))) {
                        switch actions {
                        case let .sort(_, later, _): later(note.id)
                        case let .tide(keep, _, _): keep(note.id)
                        }
                    }
                case .back:
                    withAnimation(reduceMotion ? nil : .snappy(duration: 0.3)) { offset = .zero }
                }
            }
    }

    /// Flies the card off toward `place` and fades it, then does `done`; with
    /// Reduce Motion, does it at once.
    private func leave(to place: CGSize, _ done: @escaping () -> Void) {
        guard !reduceMotion else {
            offset = .zero
            done()
            return
        }
        withAnimation(.easeIn(duration: 0.2)) {
            leaving = true
            offset = place
        } completion: {
            done()
            // the same card still on top (the deck's only one, put back): back in its place
            var still = Transaction()
            still.disablesAnimations = true
            withTransaction(still) {
                leaving = false
                offset = .zero
            }
        }
    }
}

/// The folders pane (A2): beside the deck on two panes, the most-used
/// folders as targets in a grid, in the pane's middle, as the card is in
/// its own; a tap files the deck's top card.
struct DeckFolderPane: View {
    let file: (UUID) -> Void
    @EnvironmentObject private var notes: NoteStore

    var body: some View {
        GeometryReader { geometry in
            let targets = IdeasDeckRule.targets(notes, slots: IdeasDeckRule.paneSlots(geometry.size))
            VStack(alignment: .leading, spacing: WardSpace.s) {
                WardSectionLabel(IdeasWords.folders)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: DeckLayout.paneTarget, maximum: DeckLayout.paneTarget),
                                             spacing: WardSpace.s, alignment: .leading)],
                          alignment: .leading, spacing: WardSpace.s) {
                    ForEach(targets) { folder in
                        DeckTarget(folder: folder) { file(folder.id) }
                    }
                }
            }
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.l)
            // in the middle, level with the card beside it
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .background { WardBackground() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("deck-folders")
    }
}
