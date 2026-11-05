import SwiftUI

/// The idea board: every note as a card on a flat canvas that goes on for
/// ever, joined by lines where two notes are connected: gently bowed, or
/// straight, as "Lines" is set (the Look tool here, the 3D map's Look menu,
/// or Settings > Look and feel - GraphLineStyle).
///
/// It is the noding system for idea points - the place to lay thoughts out
/// next to each other and draw the lines between them by hand:
/// - drag the background to move around, pinch to zoom;
/// - drag a card to put it somewhere else (where it lands is kept; let go
///   on another card, it settles in the nearest free place);
/// - long-press a card, then tap another, to connect the two - or to
///   disconnect them if they were already connected. "Connect", in the
///   round tools at the bottom corner, does the same with plain taps, for
///   joining up a lot at once;
/// - double-tap empty space to put a new idea exactly there.
///
/// Cards are tinted, faintly, by the folder they belong to. They stand just
/// off the base; the one being dragged, or starting a connection, rises
/// higher (and, without Reduce Motion, grows a little) and is drawn over
/// every other card; the one starting a connection is ringed in Theatre
/// Blue.
///
/// The canvas runs on under the bottom glass, but its middle - where the
/// board's origin sits - is the middle of the part that is not under glass.
/// It opens, and Recentre returns, with every card in view
/// (IdeaBoardFit), clear of the edges and the tool circles.
struct IdeaBoardView: View {
    @EnvironmentObject private var notes: NoteStore
    let open: (UUID) -> Void

    /// How far the board has been moved, and the move in progress.
    @State private var pan: CGSize = .zero
    @GestureState private var panning: CGSize = .zero
    /// How far it is zoomed, and the pinch in progress.
    @State private var zoom: CGFloat = 1
    @GestureState private var pinching: CGFloat = 1
    /// The card being dragged, and how far, in screen points.
    @State private var dragged: UUID?
    @State private var dragOffset: CGSize = .zero
    /// With Connect on, a tap picks a card instead of opening it.
    @State private var connecting = false
    /// The card a connection starts from, waiting for a second.
    @State private var source: UUID?
    @State private var adding: BoardSpot?
    /// "Lines": Curved (false, a gentle bow) or Straight (GraphLineStyle).
    @AppStorage(SpaceSettings.straightLinesKey) private var straightLines: Bool = false
    /// The canvas's size and how much of it is under glass, for the fit.
    @State private var frame: CGSize = .zero
    @State private var underGlass: CGFloat = 0
    /// The height of the board's safe part, above the foot.
    @State private var safeHeight: CGFloat = 0
    /// Once the board has been dragged or pinched, it stays where it was put.
    @State private var moved = false
    /// Each card's drawn size, in board points, as it lays out (S05c).
    @State private var sizes: [UUID: CGSize] = [:]
    /// The places the cards are drawn at, worked out again only when a
    /// stored place, a card or a size changes.
    @State private var spread = BoardSpreadMemo()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Where a new idea was asked for, in board points.
    struct BoardSpot: Identifiable {
        let id = UUID()
        let x: Double
        let y: Double
    }

    private static let cardWidth: CGFloat = IdeaBoardDrop.card.width
    private static let space = "idea-board"

    private var scale: CGFloat { min(max(zoom * pinching, 0.3), 2.5) }
    private var offset: CGSize {
        CGSize(width: pan.width + panning.width, height: pan.height + panning.height)
    }

    var body: some View {
        // The canvas runs on under the bottom glass (the capture row and the
        // Library's dock); the tools and the hint stay in the safe area.
        ZStack {
            canvas
                .ignoresSafeArea(.container, edges: .bottom)
        }
        // the safe part's height, read here: the canvas, past the safe area,
        // reads no bottom inset, so its hidden part is its height less this
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { safeHeight = $0 }
        .overlay(alignment: .top) {
            if source != nil || connecting {
                hint
            }
        }
        .ideaTools {
            // Look first and Recentre last, as on the map and on Android
            IdeaBoardLookTool()
            IdeaToolButton(symbol: connectSymbol, label: connectLabel, active: connecting) {
                connecting.toggle()
                source = nil
            }
            IdeaToolButton(symbol: "smallcircle.filled.circle", label: "Recentre") {
                moved = false
                withAnimation(Motion.gentle(.snappy(duration: 0.25))) { fitAll() }
            }
        }
        .sensoryFeedback(.selection, trigger: source)
        .sheet(item: $adding) { spot in
            NameSheet(title: "New idea", prompt: "The idea", initial: "", confirm: "Add") { text in
                _ = notes.create(title: text, kind: .idea, at: (x: spot.x, y: spot.y))
            }
        }
    }

    private var connectSymbol: String { connecting ? "link.circle.fill" : "link" }
    private var connectLabel: String { connecting ? "Stop connecting" : "Connect" }

    private var canvas: some View {
        GeometryReader { geo in
            let size = geo.size
            // the reader runs on under the bottom glass (the foot: the
            // notice and the mode switch); this much is hidden. Not
            // geo.safeAreaInsets.bottom, which is 0 here, past the safe area
            // (S05c: at AX5 the first view put a card under the switch)
            let hidden: CGFloat = safeHeight > 0 ? max(size.height - safeHeight, 0) : geo.safeAreaInsets.bottom
            let midY: CGFloat = Self.middle(of: size, hidden: hidden)
            let drawn: [UUID: CGPoint] = drawnCentres()
            let points = positions(in: size, midY: midY, drawn: drawn)
            let edges = notes.allEdges()
            let lineWidth: CGFloat = max(1, 1.5 * scale)
            let style: GraphLineStyle = GraphLineStyle.inForce(straightLines)
            let lanes: [(lane: Int, lanes: Int)] = Self.lanes(edges)
            ZStack {
                // the board itself: moved by dragging, added to by double-tap
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(panGesture)
                    .onTapGesture(count: 2) { location in
                        ask(at: location, in: size, midY: midY)
                    }

                Canvas { context, _ in
                    let ink: Color = Color.wardInkSecondary
                    let stroke = StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                    for (k, edge) in edges.enumerated() {
                        guard let p = points[edge.0], let q = points[edge.1] else { continue }
                        let line: Path = Self.line(style, from: p, to: q, lane: lanes[k].lane, lanes: lanes[k].lanes)
                        context.stroke(line, with: .color(ink), style: stroke)
                    }
                }
                .allowsHitTesting(false)

                ForEach(notes.notes) { note in
                    // a lifted card is drawn over the flat ones it passes
                    let lifted: Bool = dragged == note.id || source == note.id
                    let layer: Double = lifted ? 1 : 0
                    card(note)
                        // its size before the board's zoom, in board points
                        .onGeometryChange(for: CGSize.self) { $0.size } action: { sizes[note.id] = $0 }
                        .scaleEffect(scale)
                        .position(points[note.id] ?? .zero)
                        .zIndex(layer)
                }

                if notes.notes.isEmpty {
                    // the kit's empty state's gap and message type (WardEmptyState);
                    // a regular glyph, as light as Android's outlined one
                    VStack(spacing: WardSpace.m) {
                        Image(systemName: "hand.tap")
                            .wardFont(.title2.weight(.regular))
                        Text("Double-tap anywhere to put an idea there.")
                            .wardFont(.subheadline)
                            .multilineTextAlignment(.center)
                    }
                    .padding(WardSpace.xl)
                    .foregroundStyle(Color.wardInkSecondary)
                    .allowsHitTesting(false)
                    .position(x: size.width / 2, y: midY)
                }
            }
            .coordinateSpace(.named(Self.space))
            .simultaneousGesture(pinchGesture)
            .clipped()
            .onChange(of: [size.width, size.height, hidden], initial: true) {
                fitFirst(size: size, hidden: hidden)
            }
            // sizes arrive after the first layout, and the places move with them
            .onChange(of: drawn) {
                fitFirst(size: size, hidden: hidden)
            }
        }
    }

    /// Every card in view as the canvas gets (or changes) its size, until
    /// the board has been moved by hand.
    private func fitFirst(size: CGSize, hidden: CGFloat) {
        frame = size
        underGlass = hidden
        guard !moved, size.width > 0, size.height > 0 else { return }
        fitAll()
    }

    /// Zoom and pan so every card is on screen, clear of the edges and the
    /// tool circles on the trailing side (16 + 44 + 16).
    private func fitAll() {
        // the cards as drawn: their spread centres and their own sizes, which
        // at the largest text sizes are far taller than the least card
        let cards: [IdeaBoardSpread.Card] = spreadCards()
        let drawn: [UUID: CGPoint] = spread.places(cards)
        let placed: [IdeaBoardSpread.Card] = cards.filter { drawn[$0.id] != nil }
        let centres: [CGPoint] = placed.compactMap { drawn[$0.id] }
        let margins = IdeaBoardFit.Margins(top: 16, leading: 16, bottom: 16, trailing: 76)
        let card = IdeaBoardDrop.card
        // never so far out that a card is shorter than a target: the
        // shortest card's height, which is never under the least card's
        let shortest: CGFloat = placed.map(\.size.height).min() ?? card.height
        let range = IdeaBoardFit.firstRange(cardHeight: Double(shortest), target: Double(WardControlSize.target))
        let cam = IdeaBoardFit.camera(centres: centres, card: card, view: frame,
                                      hidden: Double(underGlass), margins: margins, zoomRange: range,
                                      sizes: placed.map(\.size))
        zoom = cam.zoom
        pan = CGSize(width: cam.panX, height: cam.panY)
    }

    // MARK: cards

    private func card(_ note: Note) -> some View {
        let tone = NoteTone.color(for: note.folderId, in: notes)
        let chosen: Bool = source == note.id
        // a card being dragged or starting a connection rises off the board
        let lifted: Bool = dragged == note.id || chosen
        let grow: CGFloat = lifted && !reduceMotion ? 1.04 : 1
        let wash: Color = tone.opacity(0.14)
        let place: String = notes.folder(note.folderId)?.name ?? note.kind.label
        let title: String = note.title.isEmpty ? "Untitled" : note.title
        let shape = RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous)
        return VStack(alignment: .leading, spacing: WardSpace.xs) {
            HStack(spacing: WardSpace.xs) {
                Image(systemName: note.kind.symbol)
                Text(place)
                    .lineLimit(1)
            }
            .wardFont(.caption2)
            .foregroundStyle(Color.wardInkSecondary)
            Text(title)
                .wardFont(.subheadline.weight(.medium))
                .foregroundStyle(Color.wardInk)
                .lineLimit(4)
                .multilineTextAlignment(.leading)
        }
        .padding(WardSpace.m)
        .frame(width: Self.cardWidth, alignment: .leading)
        // the height the first view fits by, so its floor keeps a one-line
        // card a target tall (fitAll's firstRange)
        .frame(minHeight: IdeaBoardDrop.card.height, alignment: .top)
        .background {
            // the folder's faint wash on the card's own relief
            ZStack {
                WardReliefFace(shape: shape, lift: lifted ? .high : .low)
                shape.fill(wash)
            }
        }
        .overlay {
            if chosen { shape.strokeBorder(Color.wardPrimaryInk, lineWidth: 2).transition(.identity) }
        }
        .scaleEffect(grow)
        .animation(reduceMotion ? nil : .snappy(duration: 0.2), value: lifted)
        .contentShape(shape)
        .contentShape(.hoverEffect, shape)
        .hoverEffect(.lift)
        .onTapGesture { tapped(note.id) }
        .onLongPressGesture(minimumDuration: 0.4) { source = note.id }
        .gesture(cardDrag(note))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        // its stored place, which the board may draw it away from (S05c), for
        // the UI tests; VoiceOver doesn't read an identifier
        .accessibilityIdentifier("board-card \(note.boardX) \(note.boardY)")
        // as Android's hold names it, and as both hints say it
        .accessibilityAction(named: "Start a connection") { source = note.id }
    }

    /// A tap opens the card - unless a connection is being made, when it is
    /// the first or second end of one.
    private func tapped(_ id: UUID) {
        if let from = source {
            if from != id {
                if notes.isLinked(from, id) { notes.unlink(from, id) } else { notes.link(from, id) }
            }
            source = nil
        } else if connecting {
            source = id
        } else {
            open(id)
        }
    }

    /// Up at the top while connecting, out of the way of the cards and the
    /// thumb.
    private var hint: some View {
        let words: String = source == nil ? "Tap a card to start a connection."
                                          : "Tap another card to connect or disconnect them."
        // one height with Cancel or without it, so the capsule doesn't jump
        // when a card is picked; Cancel drops the pick, and the Connect tool
        // ends connecting
        return HStack(spacing: WardSpace.s) {
            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                .wardFont(.subheadline)
                .accessibilityHidden(true)
            Text(words)
                .wardFont(.subheadline)
                .foregroundStyle(Color.wardInk)
            if source != nil {
                Button("Cancel") { source = nil }
                    .buttonStyle(.wardQuiet)
            }
        }
        .frame(minHeight: WardControlSize.target)
        .padding(.leading, WardSpace.gutter)
        .padding(.trailing, source == nil ? WardSpace.gutter : WardSpace.xs)
        .padding(.vertical, WardSpace.xs)
        .foregroundStyle(Color.wardPrimaryInk)
        .wardRaised(in: Capsule(), lift: .high)
        .padding(.horizontal, WardSpace.gutter)
        .padding(.top, WardSpace.m)
    }

    // MARK: where things are

    /// The height of the board's middle: the middle of the part of the
    /// canvas not under the bottom glass.
    private static func middle(of size: CGSize, hidden: CGFloat) -> CGFloat {
        let seen: CGFloat = max(size.height - hidden, 0)
        return seen / 2
    }

    /// The cards as the spread takes them: the store's order, each at its
    /// stored centre with its drawn size (at least the card the board fits by).
    private func spreadCards() -> [IdeaBoardSpread.Card] {
        let least: CGSize = IdeaBoardDrop.card
        return notes.notes.map { note in
            let seen: CGSize = sizes[note.id] ?? least
            return IdeaBoardSpread.Card(id: note.id, centre: CGPoint(x: note.boardX, y: note.boardY),
                                        size: CGSize(width: max(seen.width, least.width), height: max(seen.height, least.height)))
        }
    }

    /// Where each card is drawn, in board points: no card over another (S05c).
    private func drawnCentres() -> [UUID: CGPoint] {
        spread.places(spreadCards())
    }

    /// Every card's centre on screen; the board's origin is at the middle
    /// across, and `midY` down.
    private func positions(in size: CGSize, midY: CGFloat, drawn: [UUID: CGPoint]) -> [UUID: CGPoint] {
        var result: [UUID: CGPoint] = [:]
        for note in notes.notes {
            let at: CGPoint = drawn[note.id] ?? CGPoint(x: note.boardX, y: note.boardY)
            var x = at.x
            var y = at.y
            if note.id == dragged {
                x += dragOffset.width / scale
                y += dragOffset.height / scale
            }
            let screenX: CGFloat = size.width / 2 + offset.width + x * scale
            let screenY: CGFloat = midY + offset.height + y * scale
            result[note.id] = CGPoint(x: screenX, y: screenY)
        }
        return result
    }

    /// A double-tap on empty board: ask for the idea, to put it there.
    private func ask(at location: CGPoint, in size: CGSize, midY: CGFloat) {
        let x = (location.x - size.width / 2 - offset.width) / scale
        let y = (location.y - midY - offset.height) / scale
        adding = BoardSpot(x: Double(x), y: Double(y))
    }

    /// Each link's lane among those joining the same two cards.
    private static func lanes(_ edges: [(UUID, UUID)]) -> [(lane: Int, lanes: Int)] {
        let keys: [String] = edges.map { edge in edge.0.uuidString + edge.1.uuidString }
        return IdeaLinkShape.lanes(for: keys)
    }

    /// One connector, as "Lines" is set (IdeaLinkShape): Curved, a gentle
    /// bow - always to the same side of the pair's first card to its second
    /// (allEdges orders each pair), so lines between nearby cards run side
    /// by side instead of on top of each other, and several between one
    /// pair fan out; Straight, card centre to card centre.
    private static func line(_ style: GraphLineStyle, from p: CGPoint, to q: CGPoint,
                             lane: Int, lanes: Int) -> Path {
        var path = Path()
        if style.isStraight {
            let ends: (CGPoint, CGPoint) = IdeaLinkShape.straightEnds(from: p, to: q, lane: lane, lanes: lanes)
            path.move(to: ends.0)
            path.addLine(to: ends.1)
            return path
        }
        let control: CGPoint = IdeaLinkShape.control(from: p, to: q, lane: lane, lanes: lanes)
        path.move(to: p)
        path.addQuadCurve(to: q, control: control)
        return path
    }

    // MARK: gestures

    private var panGesture: some Gesture {
        DragGesture(minimumDistance: 2)
            .updating($panning) { value, state, _ in
                state = value.translation
            }
            .onEnded { value in
                pan.width += value.translation.width
                pan.height += value.translation.height
                moved = true
            }
    }

    private var pinchGesture: some Gesture {
        MagnifyGesture()
            .updating($pinching) { value, state, _ in
                state = value.magnification
            }
            .onEnded { value in
                zoom = min(max(zoom * value.magnification, 0.3), 2.5)
                moved = true
            }
    }

    /// Moves a card; where it is let go is saved. The distance is measured in
    /// the board's own space, so it is right at any zoom.
    private func cardDrag(_ note: Note) -> some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .named(Self.space))
            .onChanged { value in
                dragged = note.id
                dragOffset = value.translation
            }
            .onEnded { value in
                // let go on another card, it settles in the nearest free place,
                // among the cards as they are drawn
                let cards: [IdeaBoardSpread.Card] = spreadCards()
                let drawn: [UUID: CGPoint] = spread.places(cards)
                let from: CGPoint = drawn[note.id] ?? CGPoint(x: note.boardX, y: note.boardY)
                let dropped = CGPoint(x: Double(from.x) + Double(value.translation.width / scale),
                                      y: Double(from.y) + Double(value.translation.height / scale))
                let own: CGSize = cards.first { $0.id == note.id }?.size ?? IdeaBoardDrop.card
                let others: [(CGPoint, CGSize)] = cards.filter { $0.id != note.id }.map { (drawn[$0.id] ?? $0.centre, $0.size) }
                let at: CGPoint = IdeaBoardSpread.settle(dropped, size: own, others: others)
                notes.setPosition(note.id, x: Double(at.x), y: Double(at.y))
                dragged = nil
                dragOffset = .zero
            }
    }
}

/// The board's last spread and what it was worked out from (S05c), so it
/// is worked out again only when a stored place, a card or a size changes,
/// not on every frame of a pan, a pinch or a drag.
final class BoardSpreadMemo {
    private var cards: [IdeaBoardSpread.Card] = []
    private var drawn: [UUID: CGPoint] = [:]

    func places(_ now: [IdeaBoardSpread.Card]) -> [UUID: CGPoint] {
        if now != cards || (drawn.isEmpty && !now.isEmpty) {
            cards = now
            drawn = IdeaBoardSpread.places(now)
        }
        return drawn
    }
}
