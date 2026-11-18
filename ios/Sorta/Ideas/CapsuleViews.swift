import SwiftUI
import UniformTypeIdentifiers

// The capsule on screen (S06d A4), as Android's CapsuleViews: the monthly
// offer under the capture well, the file the system's save screen writes,
// and the map's picture the page shows, drawn at save time by the board
// itself (IdeaBoardView, held still).

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

    /// The board as the person last left it, in day's colours, drawn by the
    /// board itself (IdeaBoardView, held still): every card at its drawn
    /// place (IdeaBoardSpread, the board's), the links as the board draws
    /// them, the whole board fitted into the picture (`fitting`). Nil with no
    /// ideas. As Android's capsule shot.
    @MainActor
    static func picture(_ store: NoteStore, straight: Bool) -> SortaCapsule.Picture? {
        guard let frame = fitting(store, straight: straight) else { return nil }
        let board = IdeaBoardView(open: { _ in }, still: frame.still)
            .frame(width: frame.css.width, height: frame.css.height)
            .background(Color.wardBackground)
            .environmentObject(store)
            .environment(IdeasFiling())
            .dynamicTypeSize(pictureType)
            .environment(\.colorScheme, .light)
        let renderer = ImageRenderer(content: board)
        renderer.scale = pictureScale
        renderer.proposedSize = ProposedViewSize(frame.css)
        guard let png = renderer.uiImage?.pngData() else { return nil }
        return SortaCapsule.Picture(png: png, width: Int(frame.css.width), height: Int(frame.css.height))
    }

    /// The picture's text size: the default, whatever the phone's, so the
    /// page reads the same from any phone.
    static let pictureType: DynamicTypeSize = .large

    /// The picture's size in CSS pixels and the board held still to fill
    /// it: every card at its drawn place, at the least card the board
    /// spreads by, `WardSpace.xl` in from the edges, zoomed out (never in)
    /// to fit `pictureMax`. Nil with no ideas.
    struct Frame {
        let css: CGSize
        let still: IdeaBoardStill
    }

    @MainActor
    static func fitting(_ store: NoteStore, straight: Bool) -> Frame? {
        let notes: [Note] = store.notes
        guard !notes.isEmpty else { return nil }
        let title: CGFloat = titleWidth()
        let card: CGSize = IdeaBoardStill.card(titleWidth: title)
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
        // the board's origin is at the picture's middle; the pan brings the
        // cards' box there
        let pan = CGSize(width: -(minX + maxX) / 2 * fit, height: -(minY + maxY) / 2 * fit)
        return Frame(css: css, still: IdeaBoardStill(zoom: fit, pan: pan, titleWidth: title,
                                                    straight: GraphLineStyle.inForce(straight).isStraight))
    }

    /// The width of 20 letters of a card's title at the picture's text size,
    /// as the board measures it (IdeaBoardDrop.titleSample).
    @MainActor
    static func titleWidth() -> CGFloat {
        let sample = Text(IdeaBoardDrop.titleSample)
            .wardFont(.subheadline.weight(.medium))
            .lineLimit(1)
            .fixedSize()
            .dynamicTypeSize(pictureType)
        let renderer = ImageRenderer(content: sample)
        renderer.scale = 1
        return renderer.uiImage?.size.width ?? 0
    }
}
