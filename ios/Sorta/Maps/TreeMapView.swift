import CoreText
import SwiftUI
import UIKit

// The Tree look (S11): the ideas as one tree on the page, the approved
// concept's. The trunk is All ideas, each folder a bough and each idea a leaf;
// a tap on a branch zooms until it is the whole tree, a tap on empty space or
// Back goes up a level, and a pinch or a drag moves freely. A leaf chosen
// shows the map's peek card. The drawing is TreeScene's, through a Core
// Graphics sink; this file only feeds it the store and the touches, and gives
// VoiceOver each name. Android's IdeasTree.kt is the same.

/// UI tests only: `-SortaFirstRun` puts the Tree's first-visit card back, once a launch.
enum TreeFirstRun {
    static let seenKey = "vignette.ideas.treeSeen"
    static let reset: Void = {
        guard ProcessInfo.processInfo.arguments.contains("-SortaFirstRun") else { return }
        UserDefaults.standard.removeObject(forKey: seenKey)
    }()
}

/// The store as the Tree's nodes, links and what the filter leaves out.
enum TreeBook {
    /// What the trunk is called: the map's own word for every idea.
    static let trunk = "All ideas"

    /// Folders in the folder list's order, so each bough takes its folder's colour.
    static func nodes(_ folders: [NoteFolder], _ notes: [Note]) -> NestNodes {
        let sorted: [NoteFolder] = folders.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        let fIn: [NestFolderIn] = sorted.map { NestFolderIn(id: $0.id.uuidString, parentId: $0.parentId?.uuidString, name: $0.name) }
        let iIn: [NestIdeaIn] = notes.map { note in
            let blank = note.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            let made = Int64((note.createdAt.timeIntervalSince1970 * 1000).rounded())
            return NestIdeaIn(id: note.id.uuidString, folderId: note.folderId?.uuidString,
                              title: blank ? "Untitled" : note.title, createdAt: made)
        }
        return NestNodes.build(trunk: trunk, folders: fIn, ideas: iIn)
    }

    static func links(_ edges: [(UUID, UUID)], _ nodes: NestNodes) -> NestLinks {
        var a: [Int] = [], b: [Int] = []
        for (x, y) in edges {
            let i = nodes.index(x.uuidString), j = nodes.index(y.uuidString)
            if i > 0 && j > 0 { a.append(i); b.append(j) }
        }
        return NestLinks(a: a, b: b)
    }

    /// What the filter and the lenses leave out of `scene`: an idea they don't
    /// let through, and a folder with ideas under it none of which shows.
    static func hide(_ scene: TreeScene, notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?) {
        let nodes = scene.nodes
        let n = nodes.size
        for i in 0..<n { scene.hidden[i] = false }
        if filter == .all && lens == nil { return }
        var degree: [UUID: Int] = [:]
        for (x, y) in notes.allEdges() { degree[x, default: 0] += 1; degree[y, default: 0] += 1 }
        var shows = [Int](repeating: 0, count: n)
        for note in notes.notes {
            let i = nodes.index(note.id.uuidString)
            if i <= 0 { continue }
            let admitted = filter.admits(kind: note.kind, root: notes.rootFolder(of: note.folderId), degree: degree[note.id] ?? 0)
            let ok = admitted && (lens?.contains(note.id) ?? true)
            scene.hidden[i] = !ok
            shows[i] = ok ? 1 : 0
        }
        // a parent comes before its children, so one pass from the end adds each subtree up
        var i = n - 1
        while i >= 1 { shows[nodes.parent[i]] += shows[i]; i -= 1 }
        for j in 1..<max(1, n) where nodes.folder[j] && (shows[j] == 0 || scene.hidden[nodes.parent[j]]) {
            scene.hidden[j] = true
        }
    }
}

/// Draws TreeScene into a Core Graphics context, in points, with nothing
/// made per frame: each name's two lines (upright and bold) are made once,
/// at 100 points, and scaled to the size the scene asks for.
final class TreeCGSink: NestSink {
    var cg: CGContext?
    private var lines: [[CTLine]]
    private(set) var widths: [[Float]]
    private let ascent: [CGFloat], descent: [CGFloat]

    init(titles: [String]) {
        let fonts: [CTFont] = [Self.font(700), Self.font(800)]
        var lines: [[CTLine]] = [[], []]
        var widths: [[Float]] = [[], []]
        for b in 0..<2 {
            lines[b].reserveCapacity(titles.count)
            widths[b].reserveCapacity(titles.count)
            for title in titles {
                let attributes: [NSAttributedString.Key: Any] = [
                    NSAttributedString.Key(kCTFontAttributeName as String): fonts[b],
                    NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true,
                ]
                let line = CTLineCreateWithAttributedString(NSAttributedString(string: title, attributes: attributes))
                lines[b].append(line)
                widths[b].append(Float(CTLineGetTypographicBounds(line, nil, nil, nil)))
            }
        }
        self.lines = lines
        self.widths = widths
        ascent = fonts.map { CTFontGetAscent($0) }
        descent = fonts.map { CTFontGetDescent($0) }
    }

    /// Nunito at 100 points and `wght`, falling to Baloo Bhaijaan 2 where it has no glyphs, as WardFont does.
    private static func font(_ weight: CGFloat) -> CTFont {
        let wght = 0x77676874 // 'wght'
        let variation = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
        let attributes: [UIFontDescriptor.AttributeName: Any] = [
            .family: WardFont.family,
            variation: [wght: weight],
            .cascadeList: [UIFontDescriptor(fontAttributes: [.family: WardFont.arabicFamily, variation: [wght: min(800, weight)]])],
        ]
        return UIFont(descriptor: UIFontDescriptor(fontAttributes: attributes), size: 100) as CTFont
    }

    func moveTo(_ x: Float, _ y: Float) { cg?.move(to: CGPoint(x: CGFloat(x), y: CGFloat(y))) }
    func lineTo(_ x: Float, _ y: Float) { cg?.addLine(to: CGPoint(x: CGFloat(x), y: CGFloat(y))) }
    func cubicTo(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float, _ y: Float) {
        cg?.addCurve(to: CGPoint(x: CGFloat(x), y: CGFloat(y)),
                     control1: CGPoint(x: CGFloat(x1), y: CGFloat(y1)),
                     control2: CGPoint(x: CGFloat(x2), y: CGFloat(y2)))
    }
    func close() { cg?.closePath() }

    func fill(_ color: Int, _ alpha: Float) {
        guard let cg else { return }
        cg.setFillColor(red: Self.r(color), green: Self.g(color), blue: Self.b(color), alpha: Self.a(alpha))
        cg.fillPath()
    }

    func stroke(_ color: Int, _ width: Float, _ alpha: Float, _ round: Bool) {
        guard let cg else { return }
        cg.setStrokeColor(red: Self.r(color), green: Self.g(color), blue: Self.b(color), alpha: Self.a(alpha))
        cg.setLineWidth(CGFloat(width))
        cg.setLineCap(round ? .round : .butt)
        cg.setLineJoin(round ? .round : .miter)
        cg.strokePath()
    }

    func text(_ node: Int, _ x: Float, _ y: Float, _ size: Float, _ bold: Bool, _ align: Int, _ ink: Int, _ halo: Int, _ haloWidth: Float) {
        guard let cg else { return }
        let b = bold ? 1 : 0
        let line = lines[b][node]
        let s = CGFloat(size) / 100
        let w = CGFloat(widths[b][node])
        let dx: CGFloat = align == 0 ? 0 : (align == 1 ? -w : -w / 2)
        // the name's middle line on y
        let base: CGFloat = (ascent[b] - descent[b]) / 2
        cg.saveGState()
        cg.translateBy(x: CGFloat(x), y: CGFloat(y))
        cg.scaleBy(x: s, y: s)
        cg.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
        cg.setLineJoin(.round)
        cg.setLineWidth(CGFloat(haloWidth) / s)
        cg.setStrokeColor(red: Self.r(halo), green: Self.g(halo), blue: Self.b(halo), alpha: 1)
        cg.setTextDrawingMode(.stroke)
        cg.textPosition = CGPoint(x: dx, y: base)
        CTLineDraw(line, cg)
        cg.setFillColor(red: Self.r(ink), green: Self.g(ink), blue: Self.b(ink), alpha: 1)
        cg.setTextDrawingMode(.fill)
        cg.textPosition = CGPoint(x: dx, y: base)
        CTLineDraw(line, cg)
        cg.restoreGState()
    }

    private static func r(_ c: Int) -> CGFloat { CGFloat((c >> 16) & 255) / 255 }
    private static func g(_ c: Int) -> CGFloat { CGFloat((c >> 8) & 255) / 255 }
    private static func b(_ c: Int) -> CGFloat { CGFloat(c & 255) / 255 }
    private static func a(_ x: Float) -> CGFloat { CGFloat(min(1, max(0, x))) }
}

/// One name VoiceOver reads: its node and its box.
struct TreePart: Identifiable {
    let node: Int
    let id: String
    let name: String
    let folder: Bool
    let leaf: Bool
    let frame: CGRect
}

/// What the Tree keeps between frames: the scene and its sink, rebuilt only
/// when the store changes, and the counters that redraw the canvas (`tick`)
/// and refresh the names VoiceOver reads (`settled`).
@Observable final class TreeHost {
    var tick = 0
    var settled = 0
    var moving = false
    var focus = 0
    @ObservationIgnored private(set) var scene: TreeScene?
    @ObservationIgnored private(set) var sink: TreeCGSink?
    @ObservationIgnored private(set) var generation = 0
    @ObservationIgnored private var builtNotes: [Note] = []
    @ObservationIgnored private var builtFolders: [NoteFolder] = []
    @ObservationIgnored private var builtFilter: GraphFilter = .all
    @ObservationIgnored private var builtLens: Set<UUID>?
    @ObservationIgnored private var size: CGSize = .zero
    @ObservationIgnored var lastDrag: CGSize = .zero
    @ObservationIgnored var lastMagnification: CGFloat = 1

    /// The scene for the store as it is; grows the tree again only when the
    /// ideas or folders change, and hides again only when the filter or the
    /// lenses do. Never writes what a view reads, so it can run in a body.
    func prepare(_ notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?, dark: Bool, reduced: Bool) -> TreeScene {
        let scene: TreeScene
        if let built = self.scene, notes.notes == builtNotes, notes.folders == builtFolders {
            scene = built
        } else {
            let nodes = TreeBook.nodes(notes.folders, notes.notes)
            scene = TreeScene(nodes, TreeLayout(nodes), TreeBook.links(notes.allEdges(), nodes))
            let sink = TreeCGSink(titles: nodes.titles)
            for i in 0..<nodes.size {
                scene.widths[0][i] = sink.widths[0][i]
                scene.widths[1][i] = sink.widths[1][i]
            }
            if size.width > 0 { place(scene) }
            builtNotes = notes.notes
            builtFolders = notes.folders
            self.scene = scene
            self.sink = sink
            generation += 1
            TreeBook.hide(scene, notes: notes, filter: filter, lens: lens)
            builtFilter = filter
            builtLens = lens
        }
        if filter != builtFilter || lens != builtLens {
            TreeBook.hide(scene, notes: notes, filter: filter, lens: lens)
            builtFilter = filter
            builtLens = lens
            generation += 1
        }
        scene.reduced = reduced
        scene.target = Float(WardControlSize.target)
        if scene.colors.dark != dark { scene.colors = TreeColors(dark: dark) }
        return scene
    }

    func resize(_ to: CGSize) {
        size = to
        if let scene { place(scene) }
        tick += 1
        settled += 1
    }

    /// The view's size, with a target's height kept clear at the foot for the card's edge.
    private func place(_ scene: TreeScene) {
        scene.setView(Float(size.width), Float(size.height), top: 0, bottom: Float(WardControlSize.target))
    }

    var sized: Bool { size.width > 0 && size.height > 0 }

    /// The names the last frame placed, for VoiceOver.
    func parts(now: Double) -> [TreePart] {
        guard let scene, sized else { return [] }
        scene.draw(nil, now: now)
        let t = CGFloat(WardControlSize.target)
        var out: [TreePart] = []
        out.reserveCapacity(scene.labelCount)
        for q in 0..<scene.labelCount {
            let i = scene.labelNode(q)
            if i < 0 { continue }
            let box = scene.labelBox(q)
            let w = max(t, CGFloat(box.2 - box.0)), h = max(t, CGFloat(box.3 - box.1))
            let cx = CGFloat(box.0 + box.2) / 2, cy = CGFloat(box.1 + box.3) / 2
            out.append(TreePart(node: i, id: scene.nodes.ids[i], name: scene.nodes.titles[i], folder: scene.nodes.folder[i],
                                leaf: scene.nodes.childCount[i] == 0,
                                frame: CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h)))
        }
        return out
    }
}

/// The frame clock the scene is timed by, in milliseconds.
private func treeNow() -> Double { Date().timeIntervalSinceReferenceDate * 1000 }

/// The canvas: redrawn on each tick, and on each frame while the camera moves.
private struct TreeCanvas: View {
    let host: TreeHost
    let generation: Int
    let landed: () -> Void

    var body: some View {
        let tick = host.tick
        TimelineView(.animation(minimumInterval: nil, paused: !host.moving)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate * 1000
            Canvas { context, _ in
                _ = tick
                _ = generation
                guard let scene = host.scene, let sink = host.sink else { return }
                if scene.animating {
                    scene.step(now)
                    if !scene.animating { DispatchQueue.main.async(execute: landed) }
                }
                context.withCGContext { cg in
                    sink.cg = cg
                    scene.draw(sink, now: now)
                    sink.cg = nil
                }
            }
        }
    }
}

/// The names VoiceOver reads, each where the frame put it; activating one does what a tap does.
private struct TreeParts: View {
    let host: TreeHost
    let activate: (Int) -> Void

    var body: some View {
        let parts: [TreePart] = host.moving ? [] : (host.settled >= 0 ? host.parts(now: treeNow()) : [])
        ZStack(alignment: .topLeading) {
            ForEach(parts) { part in
                Color.clear
                    .frame(width: part.frame.width, height: part.frame.height)
                    .offset(x: part.frame.minX, y: part.frame.minY)
                    .allowsHitTesting(false)
                    .accessibilityElement()
                    .accessibilityLabel(part.folder ? IdeasWords.treeFolderSpoken(part.name) : part.name)
                    .accessibilityHint(part.leaf ? IdeasWords.treeIdeaHint : IdeasWords.treeZoomHint)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("tree-part-" + part.id)
                    .accessibilityAction { activate(part.node) }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// Map mode's Tree: the store as a tree on the page, between the strip and
/// the foot. `open` opens an idea in the editor; `lens` is the lenses' ideas.
struct TreeMapView: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let open: (UUID) -> Void
    var lens: Set<UUID>? = nil

    @AppStorage(TreeFirstRun.seenKey) private var seen = false
    @State private var host = TreeHost()
    @State private var filter: GraphFilter = .all
    @State private var chosen: UUID?
    @State private var linksShown = false
    @State private var movingNote: GraphReading?

    var body: some View {
        _ = TreeFirstRun.reset
        let scene = host.prepare(notes, filter: filter, lens: lens, dark: scheme == .dark, reduced: reduceMotion)
        let generation = host.generation
        let focus = min(host.focus, scene.nodes.size - 1)
        return ZStack {
            TreeCanvas(host: host, generation: generation, landed: landed)
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in
                    activate(scene.hitTest(Float(value.location.x), Float(value.location.y)))
                })
                .simultaneousGesture(drag(scene))
                .simultaneousGesture(magnify(scene))
                .accessibilityElement()
                .accessibilityLabel(IdeasWords.treeMapSpoken(scene.nodes.titles[focus]))
                .accessibilityIdentifier("tree-map")
                .onGeometryChange(for: CGSize.self) { $0.size } action: { host.resize($0) }
            TreeParts(host: host, activate: activate)
        }
        .background(Color(treeRGB: scene.colors.sky))
        .overlay(alignment: .topLeading) {
            if focus > 0 {
                IdeaToolButton(symbol: "chevron.backward",
                               label: IdeasWords.treeBackSpoken(scene.nodes.titles[scene.nodes.parent[focus]])) { back() }
                    .accessibilityIdentifier("tree-back")
                    .padding(WardSpace.gutter)
            }
        }
        .overlay(alignment: .bottom) { card }
        .ideaTools(corner: true) { tools }
        .onChange(of: generation) { _, _ in
            // a new tree starts whole: what was chosen is chosen again if it is still there
            let i = chosen.map { scene.nodes.index($0.uuidString) } ?? -1
            if i <= 0 { chosen = nil; linksShown = false }
            scene.chosen = max(-1, i)
            scene.linksOf = linksShown && i > 0 ? i : -1
            host.focus = scene.focus
            host.tick += 1
            host.settled += 1
        }
        .sheet(item: $movingNote) { item in
            FolderChooser(current: notes.note(item.id)?.folderId,
                          choose: { filing.file(item.id, in: $0, notes: notes) },
                          makeFolder: { name in
                              let folder = notes.createFolder(name: name, parentId: nil)
                              filing.file(item.id, in: folder.id, notes: notes)
                          })
        }
    }

    // MARK: the touches

    private func drag(_ scene: TreeScene) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                let d = CGSize(width: value.translation.width - host.lastDrag.width,
                               height: value.translation.height - host.lastDrag.height)
                host.lastDrag = value.translation
                scene.panBy(Float(d.width), Float(d.height))
                host.tick += 1
            }
            .onEnded { _ in
                host.lastDrag = .zero
                rest(scene)
            }
    }

    private func magnify(_ scene: TreeScene) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let f = value.magnification / max(0.001, host.lastMagnification)
                host.lastMagnification = value.magnification
                scene.zoomAbout(Float(value.startLocation.x), Float(value.startLocation.y), Float(f))
                host.tick += 1
            }
            .onEnded { _ in
                host.lastMagnification = 1
                rest(scene)
            }
    }

    /// After a free move, the tree takes the part at the middle as its focus.
    private func rest(_ scene: TreeScene) {
        scene.settle()
        host.focus = scene.focus
        host.tick += 1
        host.settled += 1
    }

    private func pick(_ i: Int) {
        guard let scene = host.scene else { return }
        let id: UUID? = i > 0 && !scene.nodes.folder[i] ? UUID(uuidString: scene.nodes.ids[i]) : nil
        chosen = id
        scene.chosen = id != nil ? i : -1
        if id == nil { linksShown = false; scene.linksOf = -1 }
        host.tick += 1
    }

    /// A branch zooms in, a leaf is chosen and its branch framed, empty space goes back.
    private func activate(_ i: Int) {
        guard let scene = host.scene else { return }
        if !seen { seen = true }
        if i < 0 { back(); return }
        pick(scene.nodes.childCount[i] == 0 ? i : -1)
        _ = scene.activate(i, now: treeNow())
        moved(scene)
    }

    private func back() {
        guard let scene = host.scene else { return }
        pick(-1)
        _ = scene.back(now: treeNow())
        moved(scene)
    }

    private func recentre() {
        guard let scene = host.scene else { return }
        pick(-1)
        scene.go(0, now: treeNow())
        moved(scene)
    }

    /// A zoom started: the frames run until it lands; with Reduce Motion it has already.
    private func moved(_ scene: TreeScene) {
        if scene.animating { host.moving = true } else { landed() }
        host.tick += 1
    }

    private func landed() {
        host.moving = false
        if let scene = host.scene { host.focus = scene.focus }
        host.tick += 1
        host.settled += 1
    }

    // MARK: the card and the tools

    @ViewBuilder
    private var card: some View {
        Group {
            if let id = chosen, let note = notes.note(id) {
                GraphPeekCardView(content: peek(note), folder: false, linksShown: linksShown,
                                  open: { open(id) },
                                  showLinks: { toggleLinks(id) },
                                  flyIn: {},
                                  chip: { other in
                                      if let i = host.scene?.nodes.index(other.uuidString), i > 0 { activate(i) }
                                  },
                                  more: ["Open", "Choose folder", "Delete"],
                                  option: { item in option(item, id) },
                                  source: note.sharedFrom)
                    .id(id)
            } else if !seen {
                firstVisit
            }
        }
        .padding(.horizontal, WardSpace.gutter)
        .padding(.trailing, WardControlSize.target + WardSpace.m)
        .padding(.bottom, WardSpace.l)
    }

    private func peek(_ note: Note) -> GraphPeekContent {
        var input = GraphPeekInput(theme: "tree", role: "", folder: false, title: note.title, page: note.kind == .page)
        input.body = note.body
        var links: [(UUID, String)] = []
        for other in notes.connections(of: note.id) {
            guard let linked = notes.note(other) else { continue }
            links.append((other, linked.title))
        }
        input.links = links
        return GraphPeek.content(input)
    }

    private func toggleLinks(_ id: UUID) {
        linksShown.toggle()
        if let scene = host.scene {
            scene.linksOf = linksShown ? scene.nodes.index(id.uuidString) : -1
        }
        host.tick += 1
        host.settled += 1
    }

    private func option(_ item: String, _ id: UUID) {
        switch item {
        case "Open": open(id)
        case "Choose folder": movingNote = GraphReading(id: id)
        case "Delete":
            pick(-1)
            filing.deleteIdea(id, notes: notes)
        default: break
        }
    }

    private var firstVisit: some View {
        VStack(alignment: .leading, spacing: WardSpace.s) {
            Text(IdeasWords.lookTree)
                .wardFont(.headline)
            Text(IdeasWords.treeLine)
                .wardFont(.subheadline)
                .fixedSize(horizontal: false, vertical: true)
            Text(IdeasWords.treeFirstVisit)
                .wardFont(.subheadline)
                .foregroundStyle(Color.wardInkSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Button("Got it") { seen = true }
                .buttonStyle(.wardCompact)
        }
        .foregroundStyle(Color.wardInk)
        .wardCard(lift: .high)
        .frame(maxWidth: GraphPanel.hintWidth, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tree-first-visit")
    }

    @ViewBuilder
    private var tools: some View {
        Menu {
            Picker("Show", selection: $filter) {
                Text("Everything").tag(GraphFilter.all)
                Text("Pages").tag(GraphFilter.pages)
                Text("Ideas").tag(GraphFilter.ideas)
                Text("Linked ideas").tag(GraphFilter.linked)
            }
            .pickerStyle(.inline)
            let tops: [NoteFolder] = notes.subfolders(of: nil)
            if !tops.isEmpty {
                Picker("Folder", selection: $filter) {
                    ForEach(tops) { folder in
                        Text(folder.name).tag(GraphFilter.folder(folder.id))
                    }
                }
                .pickerStyle(.menu)
            }
        } label: {
            IdeaToolFace(symbol: filter == .all ? "line.3.horizontal.decrease" : "line.3.horizontal.decrease.circle.fill")
        }
        .foregroundStyle(IdeaToolFace.ink(active: filter != .all))
        .ideaToolRelief(active: filter != .all)
        .accessibilityLabel("Filter")

        IdeaToolButton(symbol: "smallcircle.filled.circle", label: "Recentre") { recentre() }
    }
}

extension Color {
    /// A colour from 0xRRGGBB, as TreeColors keeps them.
    init(treeRGB rgb: Int) {
        self.init(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255)
    }
}
