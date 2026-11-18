import CoreText
import MetalKit
import SwiftUI
import UIKit

// The Tree look (S11; in 3D by the owner's decision of 18 Nov): the ideas as
// one tree, drawn by Metal as the 3D map's Performance look is (TreeMetal,
// with the map's own camera, GraphPerfCamera). The trunk is All ideas, each
// folder a branch and each idea a leaf. A drag turns the tree, a pinch zooms,
// a tap on a branch flies in to it, a tap on a leaf shows the map's peek
// card, and a tap on empty space or Back flies out a level. The names are
// TreeNames3D's, never overlapping, drawn over the tree with a halo of the
// page's colour; VoiceOver reads each. Android's IdeasTree.kt is the same.

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

    /// What the filter and the lenses leave out of `nodes`, by node: an idea
    /// they don't let through, and a folder with ideas under it none of which
    /// shows. Nil with nothing on.
    @MainActor static func hidden(_ nodes: NestNodes, notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?) -> [Bool]? {
        if filter == .all && lens == nil { return nil }
        let n = nodes.size
        var hidden = [Bool](repeating: false, count: n)
        var degree: [UUID: Int] = [:]
        for (x, y) in notes.allEdges() { degree[x, default: 0] += 1; degree[y, default: 0] += 1 }
        var shows = [Int](repeating: 0, count: n)
        for note in notes.notes {
            let i = nodes.index(note.id.uuidString)
            if i <= 0 { continue }
            let admitted = filter.admits(kind: note.kind, root: notes.rootFolder(of: note.folderId), degree: degree[note.id] ?? 0)
            let ok = admitted && (lens?.contains(note.id) ?? true)
            hidden[i] = !ok
            shows[i] = ok ? 1 : 0
        }
        // a parent comes before its children, so one pass from the end adds each subtree up
        var i = n - 1
        while i >= 1 { shows[nodes.parent[i]] += shows[i]; i -= 1 }
        for j in 1..<max(1, n) where nodes.folder[j] && (shows[j] == 0 || hidden[nodes.parent[j]]) {
            hidden[j] = true
        }
        return hidden
    }
}

/// Draws a nest's names (and, for the flat TreeScene, its shapes) into a Core
/// Graphics context, in points, with nothing made per frame: each name's two
/// lines (upright and bold) are made once, at 100 points, and scaled to the
/// size asked for.
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

    /// Node `node`'s words' width and height at `size` points, bold for a branch.
    func box(_ node: Int, bold: Bool, size: Float) -> (width: Float, height: Float) {
        let b = bold ? 1 : 0
        return (widths[b][node] * size / 100, Float(ascent[b] + descent[b]) * size / 100)
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

/// One name VoiceOver reads: its node and its box, a touch target each way at least.
struct TreePart: Identifiable {
    let node: Int
    let id: String
    let name: String
    let folder: Bool
    let leaf: Bool
    let frame: CGRect
}

/// What the 3D Tree keeps between frames: the tree's draw lists (TreeModel3D,
/// made again only when the store, the mode or the Graphics tier changes),
/// the camera and its fly, each node's state for the renderer, and the names
/// the last pose placed. A pose that changes places the names again, once;
/// the renderer (TreeMetal) draws only while something moves. `named` and
/// `moving` are what the views read.
@MainActor @Observable final class TreeHost {
    /// Bumped each time the names are placed again: the names layer and VoiceOver read it.
    var named = 0
    var moving = false
    var focus = 0
    @ObservationIgnored private(set) var nodes: NestNodes?
    @ObservationIgnored private(set) var model: TreeModel3D?
    @ObservationIgnored private(set) var sink: TreeCGSink?
    @ObservationIgnored private(set) var generation = 0
    @ObservationIgnored private var layout: TreeLayout3D?
    @ObservationIgnored private var links = NestLinks.none
    @ObservationIgnored private var builtNotes: [Note] = []
    @ObservationIgnored private var builtFolders: [NoteFolder] = []
    @ObservationIgnored private var builtFilter: GraphFilter = .all
    @ObservationIgnored private var builtLens: Set<UUID>?
    @ObservationIgnored private var hidden: [Bool]?

    @ObservationIgnored private(set) var camera = GraphPerfCamera()
    @ObservationIgnored private var fly: (from: GraphPerfCamera, to: GraphPerfCamera, start: Double)?
    /// Whether the camera is still at its first view, so a card that comes or grows frames it again.
    @ObservationIgnored private var home = true
    @ObservationIgnored private(set) var size: CGSize = .zero
    /// What the chrome covers at the view's top and foot, and the first visit's card's cover from the foot (points).
    @ObservationIgnored private var top: Float = 0
    @ObservationIgnored private var bottom: Float = 0
    @ObservationIgnored private(set) var cover: Float = 0
    @ObservationIgnored var reduced = false
    @ObservationIgnored var contrast = false

    @ObservationIgnored private(set) var states: [UInt8] = []
    @ObservationIgnored private(set) var stateVersion = 0
    @ObservationIgnored private(set) var chosen = -1
    @ObservationIgnored private(set) var linksOf = -1

    @ObservationIgnored private(set) var screen: [TreeScreenNode] = []
    @ObservationIgnored private(set) var names: [TreePlacedName] = []
    @ObservationIgnored private var held: [Int: Int] = [:]
    private let style = TreeNames3D.Style()
    @ObservationIgnored private var pending = false
    @ObservationIgnored private var wantsHome = false
    /// The renderer's view, set once it is made; nil where Metal can't draw, and then every move lands at once.
    @ObservationIgnored weak var metal: MTKView?
    @ObservationIgnored private(set) var frames = 0

    /// The tree for the store as it is; grows it again only when the ideas,
    /// the folders, the mode or the tier change, and hides again only when the
    /// filter or the lenses do. Writes nothing a view reads, so it can run in a body.
    func prepare(_ notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?, dark: Bool, tier: GraphicsTier) -> NestNodes {
        let nodes: NestNodes
        var grown = false
        if let built = self.nodes, notes.notes == builtNotes, notes.folders == builtFolders {
            nodes = built
        } else {
            nodes = TreeBook.nodes(notes.folders, notes.notes)
            self.nodes = nodes
            layout = TreeLayout3D(nodes)
            links = TreeBook.links(notes.allEdges(), nodes)
            sink = TreeCGSink(titles: nodes.titles.map(TreeNames3D.label))
            builtNotes = notes.notes
            builtFolders = notes.folders
            grown = true
            generation += 1
            held = [:]
            chosen = -1
            linksOf = -1
        }
        if grown || filter != builtFilter || lens != builtLens {
            hidden = TreeBook.hidden(nodes, notes: notes, filter: filter, lens: lens)
            builtFilter = filter
            builtLens = lens
            if !grown { generation += 1 }
            restate()
            soon(home: false)
        }
        if let layout, grown || model?.colors.dark != dark || model?.tier != tier {
            model = TreeModel3D(nodes, layout, colors: TreeColors(dark: dark), tier: tier)
            if grown { home = true }
            soon(home: grown)
        }
        return nodes
    }

    /// What a body found (a new tree, a mode, a filter) is done just after
    /// it, so a body never writes what a view reads.
    private func soon(home frame: Bool) {
        if frame { wantsHome = true }
        if pending { return }
        pending = true
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.pending = false
            if self.wantsHome {
                self.wantsHome = false
                self.frameHome()
            } else {
                self.changed()
            }
        }
    }

    var sized: Bool { size.width > 0 && size.height > 0 }
    private var width: Float { Float(size.width) }
    private var height: Float { Float(size.height) }
    /// Half a touch target: the first view keeps that much more clear of the chrome and the card.
    private var half: Float { Float(WardControlSize.target) / 2 }

    /// The window a fly frames into: the chrome's, and the card's cover while it is up.
    private var framed: (top: Float, left: Float, bottom: Float, right: Float) {
        (top + half, 0, max(bottom, cover > 0 ? cover + half : 0), 0)
    }

    func resize(_ to: CGSize, top: CGFloat, bottom: CGFloat) {
        let grew = to != size || Float(top) != self.top || Float(bottom) != self.bottom
        size = to
        self.top = Float(top)
        self.bottom = Float(bottom)
        if grew && home { frameHome() } else { changed() }
    }

    /// The first visit's card's cover, from the view's foot, 0 once it is put
    /// away. When it comes or grows a camera still at its first view frames
    /// above it; when it goes the camera stays and the next Recentre frames without it.
    func setCover(_ to: CGFloat) {
        let new = max(0, Float(to))
        if new == cover { return }
        let grows = new > cover
        cover = new
        if grows && home { frameHome() } else { changed() }
    }

    /// The whole tree, from the front, framed above the card.
    private func frameHome() {
        guard let model, sized else { return }
        fly = nil
        camera = TreeCamera3D.whole(model, width: width, height: height, insets: framed)
        changed()
    }

    /// The camera moved, the window changed or a part was hidden: the names are placed again and a frame asked for.
    private func changed() {
        place()
        metal?.setNeedsDisplay()
    }

    /// The names for the pose now: each body projected, then the placer's
    /// boxes, last frame's spots tried first so a name does not hop.
    func place() {
        guard let model, let nodes, let sink, sized else { return }
        screen = TreeCamera3D.project(model, camera, width: width, height: height, hidden: hidden)
        let floor = TreeNames3D.floor(bottom: bottom, cover: cover)
        names = TreeNames3D.layout(screen, nodes, style: style, measure: { i, bold, size in sink.box(i, bold: bold, size: size) },
                                   width: width, height: height, top: top, bottom: floor, held: held, focus: focus)
        held.removeAll(keepingCapacity: true)
        for p in names { held[p.index] = p.spot }
        named &+= 1
    }

    // MARK: moving

    /// A one-finger drag: the tree turns round, 3.2 radians a view's width, as Android's.
    func turn(dx: CGFloat, dy: CGFloat) {
        guard sized else { return }
        fly = nil
        home = false
        let k = 3.2 / width
        let pitch = min(max(camera.pitch + Float(dy) * k, -0.35), 1.2) - camera.pitch
        camera.orbit(yaw: -Float(dx) * k, pitch: pitch)
        if moving { moving = false }
        changed()
    }

    /// A pinch: nearer or further, within what the tree allows.
    func zoom(by factor: CGFloat) {
        guard let model, factor > 0 else { return }
        fly = nil
        home = false
        camera.zoom(by: Float(factor), limits: TreeCamera3D.limits(model))
        if moving { moving = false }
        changed()
    }

    /// Flies in to part `i` (0, the whole tree, flies home).
    func go(_ i: Int) {
        guard let model, sized else { return }
        focus = max(0, i)
        let to: GraphPerfCamera
        if i <= 0 {
            home = true
            to = TreeCamera3D.whole(model, width: width, height: height, insets: framed)
        } else {
            home = false
            to = TreeCamera3D.flyIn(model, i, from: camera, width: width, height: height, insets: framed)
        }
        if reduced || metal == nil {
            fly = nil
            camera = to
            moving = false
            changed()
            return
        }
        fly = (camera, to, CACurrentMediaTime())
        moving = true
        if let metal {
            metal.enableSetNeedsDisplay = false
            metal.isPaused = false
        }
    }

    /// The renderer's clock, at the start of each frame: the fly's next step.
    func advance(now: Double) {
        guard let f = fly else { return }
        let t = Float((now - f.start) / TreeCamera3D.seconds)
        camera = GraphPerfCamera.glide(f.from, f.to, t: min(max(t, 0), 1))
        if t >= 1 {
            fly = nil
            camera = f.to
            moving = false
        }
        place()
    }

    /// The renderer drew a frame.
    func drawn(model: TreeModel3D) { frames &+= 1 }

    /// How far the part in focus reaches: the fog's depth.
    var focusReach: Float {
        guard let model else { return 1 }
        return Float(model.layout.reach[min(max(focus, 0), model.layout.reach.count - 1)])
    }

    // MARK: each node's state

    func choose(_ i: Int) {
        chosen = i
        if i < 0 { linksOf = -1 }
        restate()
        changed()
    }

    func showLinks(of i: Int) {
        linksOf = i
        restate()
        changed()
    }

    /// One byte a node for the renderer: hidden, chosen, linked to the chosen, or none.
    private func restate() {
        guard let nodes else { return }
        let n = nodes.size
        if states.count != n { states = [UInt8](repeating: 0, count: n) }
        for i in 0..<n { states[i] = (hidden?[i] ?? false) ? TreeMetalRenderer.hidden : 0 }
        if linksOf > 0 {
            for k in 0..<links.size {
                let a = links.a[k], b = links.b[k]
                if a == linksOf, b < n, states[b] == 0 { states[b] = TreeMetalRenderer.linked }
                if b == linksOf, a < n, states[a] == 0 { states[a] = TreeMetalRenderer.linked }
            }
        }
        if chosen >= 0, chosen < n { states[chosen] = TreeMetalRenderer.chosen }
        stateVersion &+= 1
    }

    /// What a tap at `point` lands on: a name, a body, or -1 for empty space.
    func hit(_ point: CGPoint) -> Int {
        TreeNames3D.hit(screen, names, x: Float(point.x), y: Float(point.y), least: Float(WardControlSize.target))
    }

    /// The names placed, for VoiceOver: each a touch target each way at least.
    func parts() -> [TreePart] {
        guard let nodes else { return [] }
        let t = CGFloat(WardControlSize.target)
        return names.map { p in
            let w = max(t, CGFloat(p.width)), h = max(t, CGFloat(p.height))
            return TreePart(node: p.index, id: nodes.ids[p.index], name: nodes.titles[p.index], folder: nodes.folder[p.index],
                            leaf: nodes.childCount[p.index] == 0,
                            frame: CGRect(x: CGFloat(p.x) - w / 2, y: CGFloat(p.y) - h / 2, width: w, height: h))
        }
    }

    /// UI tests only (`-SortaTreeProbe`): the view's size, the camera's turn,
    /// the part in focus, the card's top, then each name and its box (left,
    /// top, right, bottom, points in the map), a line each.
    func probe() -> String {
        guard let nodes else { return "" }
        var out = "\(Int(size.width)) \(Int(size.height)) \(camera.yaw) \(focus) \(height - cover)"
        for p in names {
            out += "\n\(nodes.titles[p.index])\t\(p.left) \(p.top) \(p.right) \(p.bottom)"
        }
        return out
    }
}

/// The names, drawn where the placer put them, each over a halo of the page's
/// colour: one Core Text line a name, made once with the tree (TreeCGSink).
private struct TreeNamesLayer: View {
    let host: TreeHost

    var body: some View {
        let named = host.named
        let names = host.names, sink = host.sink, colors = host.model?.colors
        Canvas { context, _ in
            _ = named
            guard let sink, let colors else { return }
            let ink = colors.ink, halo = colors.sky
            context.withCGContext { cg in
                // a name set out past the crown has a short leader back to its branch's tip, under the words
                let red: CGFloat = CGFloat((ink >> 16) & 255) / 255
                let green: CGFloat = CGFloat((ink >> 8) & 255) / 255
                let blue: CGFloat = CGFloat(ink & 255) / 255
                cg.setStrokeColor(red: red, green: green, blue: blue, alpha: 0.55)
                cg.setLineWidth(1)
                cg.setLineCap(.round)
                let inset = CGFloat(TreeNames3D.Style().halo)
                for p in names {
                    guard let tip = p.lead else { continue }
                    let toLeft = tip.x < p.x
                    let side: CGFloat = CGFloat(toLeft ? p.left : p.right)
                    let near: CGFloat = toLeft ? side + inset : side - inset
                    cg.move(to: CGPoint(x: near, y: CGFloat(p.y)))
                    cg.addLine(to: CGPoint(x: CGFloat(tip.x), y: CGFloat(tip.y)))
                }
                cg.strokePath()
                sink.cg = cg
                for p in names {
                    sink.text(p.index, p.x, p.y, p.size, p.bold, 2, ink, halo, 2 * TreeNames3D.Style().halo)
                }
                sink.cg = nil
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The names VoiceOver reads, each where the pose put it; activating one does what a tap does.
private struct TreeParts: View {
    let host: TreeHost
    let activate: (Int) -> Void

    var body: some View {
        let parts: [TreePart] = host.moving ? [] : (host.named >= 0 ? host.parts() : [])
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

/// UI tests only: the names placed and the pose, as one element's label.
private struct TreeProbe: View {
    let host: TreeHost
    static let on = ProcessInfo.processInfo.arguments.contains("-SortaTreeProbe")

    var body: some View {
        let named = host.named
        Color.clear
            .frame(width: 1, height: 1)
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel(named >= 0 ? host.probe() : "")
            .accessibilityIdentifier("tree-probe")
    }
}

/// Map mode's Tree: the store as a tree in 3D, drawn by Metal (TreeMetal),
/// between the strip and the foot. A drag turns it, a pinch zooms, a tap on
/// a branch flies in to it, a tap on a leaf shows the peek card, and a tap on
/// empty space or Back flies out a level. `open` opens an idea in the editor;
/// `lens` is the lenses' ideas. Android's IdeasTree.kt is the same.
struct TreeMapView: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(\.colorScheme) private var scheme
    @Environment(\.colorSchemeContrast) private var contrast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let open: (UUID) -> Void
    var lens: Set<UUID>? = nil

    @AppStorage(TreeFirstRun.seenKey) private var seen = false
    @State private var host = TreeHost()
    @State private var filter: GraphFilter = .all
    @State private var chosen: UUID?
    @State private var linksShown = false
    @State private var movingNote: GraphReading?
    @State private var lastDrag: CGSize = .zero
    @State private var lastMagnification: CGFloat = 1
    /// The first visit's card's height, 0 while it is not shown.
    @State private var cardHeight: CGFloat = 0

    /// What the chrome covers inside the map: Back's row at the top, the tools' row at the foot.
    private static let topChrome: CGFloat = WardControlSize.target + 2 * WardSpace.gutter
    private static let footChrome: CGFloat = WardControlSize.target + WardSpace.gutter

    var body: some View {
        _ = TreeFirstRun.reset
        let nodes = host.prepare(notes, filter: filter, lens: lens, dark: scheme == .dark, tier: GraphQuality.current.tier)
        let generation = host.generation
        let focus = min(host.focus, nodes.size - 1)
        let sky = host.model?.colors.sky ?? TreeColors(dark: scheme == .dark).sky
        return ZStack {
            TreeMetalView(host: host)
                .accessibilityHidden(true)
            Color.clear
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in activate(host.hit(value.location)) })
                .simultaneousGesture(drag)
                .simultaneousGesture(magnify)
                .accessibilityElement()
                .accessibilityLabel(IdeasWords.treeMapSpoken(nodes.titles[max(0, focus)]))
                .accessibilityIdentifier("tree-map")
                .onGeometryChange(for: CGSize.self) { $0.size } action: {
                    host.resize($0, top: Self.topChrome, bottom: Self.footChrome)
                }
            TreeNamesLayer(host: host)
            TreeParts(host: host, activate: activate)
            if TreeProbe.on {
                TreeProbe(host: host)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .background(Color(treeRGB: sky))
        .overlay(alignment: .topLeading) {
            if focus > 0 {
                IdeaToolButton(symbol: "chevron.backward",
                               label: IdeasWords.treeBackSpoken(nodes.titles[nodes.parent[focus]])) { back() }
                    .accessibilityIdentifier("tree-back")
                    .padding(WardSpace.gutter)
            }
        }
        .overlay(alignment: .bottom) { card }
        .ideaTools(corner: true) { tools }
        .onAppear {
            host.reduced = reduceMotion
            host.contrast = contrast == .increased
        }
        .onChange(of: reduceMotion) { _, now in host.reduced = now }
        .onChange(of: contrast) { _, now in host.contrast = now == .increased; host.place() }
        .onChange(of: coverNow) { _, now in host.setCover(now) }
        .onChange(of: generation) { _, _ in
            // a new tree starts whole: what was chosen is chosen again if it is still there
            let i = chosen.map { nodes.index($0.uuidString) } ?? -1
            if i <= 0 { chosen = nil; linksShown = false }
            host.choose(max(-1, i))
            host.showLinks(of: linksShown && i > 0 ? i : -1)
            host.focus = 0
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

    /// The first visit's card's cover from the map's foot (its height and the gap under it), 0 when it is away.
    private var coverNow: CGFloat {
        !seen && chosen == nil && cardHeight > 0 ? cardHeight + WardSpace.l : 0
    }

    // MARK: the touches

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                let dx = value.translation.width - lastDrag.width, dy = value.translation.height - lastDrag.height
                lastDrag = value.translation
                host.turn(dx: dx, dy: dy)
            }
            .onEnded { _ in lastDrag = .zero }
    }

    private var magnify: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let f = value.magnification / max(0.001, lastMagnification)
                lastMagnification = value.magnification
                host.zoom(by: f)
            }
            .onEnded { _ in lastMagnification = 1 }
    }

    private func pick(_ i: Int) {
        guard let nodes = host.nodes else { return }
        let id: UUID? = i > 0 && !nodes.folder[i] ? UUID(uuidString: nodes.ids[i]) : nil
        chosen = id
        if id == nil { linksShown = false }
        host.choose(id != nil ? i : -1)
    }

    /// A branch flies in, a leaf is chosen, empty space puts the chosen leaf
    /// away or, with none, goes back.
    private func activate(_ i: Int) {
        guard let nodes = host.nodes else { return }
        if !seen { seen = true }
        if i < 0 {
            if chosen != nil { pick(-1) } else { back() }
            return
        }
        if i > 0 && nodes.childCount[i] == 0 && !nodes.folder[i] {
            pick(i)
        } else {
            pick(-1)
            host.go(i)
        }
    }

    private func back() {
        guard let nodes = host.nodes else { return }
        pick(-1)
        let focus = host.focus
        if focus > 0 { host.go(nodes.parent[focus]) }
    }

    private func recentre() {
        pick(-1)
        host.go(0)
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
                                      if let i = host.nodes?.index(other.uuidString), i > 0 { activate(i) }
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
        host.showLinks(of: linksShown ? (host.nodes?.index(id.uuidString) ?? -1) : -1)
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
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { cardHeight = $0 }
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
