import SwiftUI
import UniformTypeIdentifiers

// The capsule on screen (S06d A4), as Android's CapsuleViews: the monthly
// offer under the capture well, the file the system's save screen writes,
// and the map's picture the page shows, drawn at save time from the
// board's own pieces.

/// The quiet offer: the kit's banner well (WardBanner's inset, glyph and
/// words) with its two actions under the words, at the end, each a full
/// target: Not now, then Save a capsule. At the largest text sizes the two
/// stack.
struct CapsuleOfferCard: View {
    let save: () -> Void
    let later: () -> Void

    static let identifier = "capsule-offer"
    static let saveIdentifier = "capsule-offer-save"
    static let laterIdentifier = "capsule-offer-later"

    var body: some View {
        VStack(alignment: .trailing, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: WardSpace.s) {
                Image(systemName: "archivebox")
                    .foregroundStyle(WardTone.primary.color)
                    .accessibilityHidden(true)
                Text(IdeasWords.capsuleOffer)
                    .wardFont(.subheadline)
                    .foregroundStyle(Color.wardInk)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: WardSpace.m) { actions }
                VStack(alignment: .trailing, spacing: 0) { actions }
            }
        }
        .padding(.horizontal, WardSpace.m)
        .padding(.top, WardSpace.m)
        .padding(.bottom, WardSpace.xs)
        .wardInset(in: RoundedRectangle(cornerRadius: WardRadius.field, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(Self.identifier)
    }

    @ViewBuilder
    private var actions: some View {
        action(IdeasWords.capsuleOfferLater, bold: false, id: Self.laterIdentifier, later)
        action(IdeasWords.capsuleOfferSave, bold: true, id: Self.saveIdentifier, save)
    }

    private func action(_ title: String, bold: Bool, id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(title)
                .wardFont(bold ? .subheadline.weight(.semibold) : .subheadline)
                .foregroundStyle(Color.wardPrimaryInk)
                // the words draw small; the target is 44 by 44
                .frame(minWidth: WardControlSize.target, minHeight: WardControlSize.target)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

/// The capsule as the system's save screen writes it: one `.html` file.
struct CapsuleFile: FileDocument {
    static var readableContentTypes: [UTType] { [.html] }
    var html: String

    init(html: String) {
        self.html = html
    }

    init(configuration: ReadConfiguration) throws {
        html = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(html.utf8))
    }
}

enum CapsuleSave {
    /// The capsule's page for `store` as it is now, with the board's picture
    /// (`picture`) and the phone's time zone.
    @MainActor
    static func html(_ store: NoteStore, straight: Bool, now: Date = Date(), zone: TimeZone = .current) throws -> String {
        try SortaCapsule.html(notes: store.notes, folders: store.folders, edges: store.allEdges(), savedAt: now, zone: zone,
                         picture: picture(store, straight: straight))
    }

    /// The page's picture at most this wide and tall, in CSS pixels; drawn
    /// at twice that.
    static let pictureMax: CGFloat = 960
    static let pictureScale: CGFloat = 2

    /// The board as the person last left it, in day's colours: every card
    /// at its drawn place (IdeaBoardSpread.places, the board's), its
    /// folder's wash and name or its kind, its title on two lines at most;
    /// the links as the board draws them (IdeaLinkShape, Straight or
    /// Curved). Nil with no ideas. As Android's capsulePicture.
    @MainActor
    static func picture(_ store: NoteStore, straight: Bool) -> SortaCapsule.Picture? {
        let notes: [Note] = store.notes
        guard !notes.isEmpty else { return nil }
        let card: CGSize = IdeaBoardDrop.card
        let drawn: [UUID: CGPoint] = IdeaBoardSpread.places(notes.map {
            IdeaBoardSpread.Card(id: $0.id, centre: CGPoint(x: $0.boardX, y: $0.boardY), size: card)
        })
        func at(_ n: Note) -> CGPoint { drawn[n.id] ?? CGPoint(x: n.boardX, y: n.boardY) }
        let margin: CGFloat = WardSpace.xl
        let minX: CGFloat = notes.map { at($0).x }.min()! - card.width / 2 - margin
        let maxX: CGFloat = notes.map { at($0).x }.max()! + card.width / 2 + margin
        let minY: CGFloat = notes.map { at($0).y }.min()! - card.height / 2 - margin
        let maxY: CGFloat = notes.map { at($0).y }.max()! + card.height / 2 + margin
        let fit: CGFloat = min(1, min(pictureMax / (maxX - minX), pictureMax / (maxY - minY)))
        let css = CGSize(width: max(1, ((maxX - minX) * fit).rounded(.down)), height: max(1, ((maxY - minY) * fit).rounded(.down)))
        var places: [UUID: CGPoint] = [:]
        for n in notes { places[n.id] = CGPoint(x: at(n).x - minX, y: at(n).y - minY) }
        let cards: [CapsuleBoardPicture.Card] = notes.map { n in
            CapsuleBoardPicture.Card(id: n.id, centre: places[n.id]!,
                                     caption: store.folder(n.folderId)?.name ?? n.kind.label,
                                     title: n.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? SortaCapsule.Words.untitled : n.title,
                                     tone: NoteTone.color(for: n.folderId, in: store))
        }
        let view = CapsuleBoardPicture(size: CGSize(width: maxX - minX, height: maxY - minY), cards: cards,
                                       edges: store.allEdges(), straight: straight)
            .scaleEffect(fit, anchor: .topLeading)
            .frame(width: css.width, height: css.height, alignment: .topLeading)
            .environment(\.colorScheme, .light)
        let renderer = ImageRenderer(content: view)
        renderer.scale = pictureScale
        renderer.proposedSize = ProposedViewSize(css)
        guard let png = renderer.uiImage?.pngData() else { return nil }
        return SortaCapsule.Picture(png: png, width: Int(css.width), height: Int(css.height))
    }
}

/// The board drawn for the capsule's picture, at board size, in the day
/// palette's colours (WardPalette's light values, so the phone's dark mode
/// never reaches the page's picture).
struct CapsuleBoardPicture: View {
    struct Card: Identifiable {
        let id: UUID
        let centre: CGPoint
        let caption: String
        let title: String
        let tone: Color
    }

    let size: CGSize
    let cards: [Card]
    let edges: [(UUID, UUID)]
    let straight: Bool

    private static func day(_ token: WardToken) -> Color {
        let hex: UInt32 = WardPalette.hex(token, dark: false)
        return Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                     blue: Double(hex & 0xFF) / 255)
    }

    var body: some View {
        let card: CGSize = IdeaBoardDrop.card
        let centres: [UUID: CGPoint] = Dictionary(cards.map { ($0.id, $0.centre) }, uniquingKeysWith: { first, _ in first })
        let lanes: [(lane: Int, lanes: Int)] = IdeaLinkShape.lanes(for: edges.map { $0.0.uuidString + $0.1.uuidString })
        let shape = RoundedRectangle(cornerRadius: WardRadius.card, style: .continuous)
        return ZStack(alignment: .topLeading) {
            Self.day(.background)
            Canvas { context, _ in
                for (k, edge) in edges.enumerated() {
                    guard let p = centres[edge.0], let q = centres[edge.1] else { continue }
                    var path = Path()
                    if straight {
                        let ends = IdeaLinkShape.straightEnds(from: p, to: q, lane: lanes[k].lane, lanes: lanes[k].lanes)
                        path.move(to: ends.0)
                        path.addLine(to: ends.1)
                    } else {
                        path.move(to: p)
                        path.addQuadCurve(to: q, control: IdeaLinkShape.control(from: p, to: q, lane: lanes[k].lane, lanes: lanes[k].lanes))
                    }
                    context.stroke(path, with: .color(Self.day(.inkSecondary)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
            }
            ForEach(cards) { c in
                VStack(alignment: .leading, spacing: WardSpace.xs) {
                    HStack(spacing: WardSpace.xs) {
                        Circle().fill(c.tone).frame(width: 8, height: 8)
                        Text(c.caption)
                            .font(.system(size: 11))
                            .foregroundStyle(Self.day(.inkSecondary))
                            .lineLimit(1)
                    }
                    Text(c.title)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Self.day(.ink))
                        .lineLimit(2)
                }
                .padding(WardSpace.m)
                .frame(width: card.width, height: card.height, alignment: .topLeading)
                .background {
                    ZStack {
                        shape.fill(Self.day(.raised))
                        shape.fill(c.tone.opacity(0.14))
                        shape.strokeBorder(Self.day(.hairline), lineWidth: 1)
                    }
                }
                .position(c.centre)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}
