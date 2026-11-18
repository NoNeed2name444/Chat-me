import MetalKit
import SwiftUI
import UIKit

// The Fractal look in 3D (S12; the owner's rule of 18 Nov: the Fractal is
// 3D, the flat one never ships): the ideas as clusters in clusters, drawn by
// Metal as the 3D map's Performance look is (FractalMetal), with the map's
// own camera (GraphPerfCamera), on Performance's night sky by day too. All
// ideas is the middle hub, each folder a hub round it with what it holds
// round that, the ideas in no folder round All ideas itself. A drag turns the
// map, a pinch zooms, a tap on a hub flies in to it, a tap on an idea shows
// the map's peek card, and a tap on empty space or Back flies out a level.
// The names are FractalNames3D's, never overlapping and never over a named
// dot, drawn in white over a halo of the sky; VoiceOver reads each. Android's
// IdeasFractal.kt is the same.

/// UI tests only: `-SortaFirstRun` puts the Fractal's first-visit card back, once a launch.
enum FractalFirstRun {
    static let seenKey = "vignette.ideas.fractalSeen"
    static let reset: Void = {
        guard ProcessInfo.processInfo.arguments.contains("-SortaFirstRun") else { return }
        UserDefaults.standard.removeObject(forKey: seenKey)
    }()
}

/// The store as the Fractal's tree, its links and what the filter leaves out.
enum FractalBook {
    /// What the middle hub is called: the map's own word for every idea.
    static let hub = "All ideas"

    /// Top folders oldest first, each folder's folders then its ideas (FractalTree.of), as Android's fractalTree.
    static func tree(_ folders: [NoteFolder], _ notes: [Note]) -> FractalTree {
        func ms(_ d: Date) -> Int64 { Int64((d.timeIntervalSince1970 * 1000).rounded()) }
        let f = folders.map { FractalTree.Folder(id: $0.id.uuidString, parentId: $0.parentId?.uuidString, name: $0.name, created: ms($0.createdAt)) }
        let i = notes.map { note in
            let blank = note.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            return FractalTree.Idea(id: note.id.uuidString, folderId: note.folderId?.uuidString,
                                    title: blank ? "Untitled" : note.title, created: ms(note.createdAt))
        }
        return FractalTree.of(folders: f, ideas: i, hubTitle: hub)
    }

    /// Each node by its store id (upper case, as FractalTree keeps them); All ideas left out.
    static func index(_ tree: FractalTree) -> [String: Int] {
        var out: [String: Int] = [:]
        out.reserveCapacity(tree.count)
        for i in 1..<max(tree.count, 1) { out[tree.ids[i]] = i }
        return out
    }

    /// The store's links between the tree's ideas, as pairs of node indices.
    static func links(_ edges: [(UUID, UUID)], _ index: [String: Int]) -> [Int] {
        var out: [Int] = []
        for (x, y) in edges {
            guard let i = index[x.uuidString], let j = index[y.uuidString] else { continue }
            out.append(i); out.append(j)
        }
        return out
    }

    /// What the filter and the lenses let show of `tree`, by node: an idea
    /// they let through, and a folder with an idea under it that shows. Nil with nothing on.
    static func shown(_ tree: FractalTree, _ index: [String: Int], notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?) -> [Bool]? {
        if filter == .all && lens == nil { return nil }
        let n = tree.count
        var hidden = [Bool](repeating: false, count: n)
        var degree: [UUID: Int] = [:]
        for (x, y) in notes.allEdges() { degree[x, default: 0] += 1; degree[y, default: 0] += 1 }
        var shows = [Int](repeating: 0, count: n)
        for note in notes.notes {
            guard let i = index[note.id.uuidString] else { continue }
            let admitted = filter.admits(kind: note.kind, root: notes.rootFolder(of: note.folderId), degree: degree[note.id] ?? 0)
            let ok = admitted && (lens?.contains(note.id) ?? true)
            hidden[i] = !ok
            shows[i] = ok ? 1 : 0
        }
        // a parent comes before its parts, so one pass from the end adds each cluster up
        var i = n - 1
        while i >= 1 { shows[tree.parent[i]] += shows[i]; i -= 1 }
        for j in 1..<max(1, n) where tree.kind[j] != FractalTree.idea && (shows[j] == 0 || hidden[tree.parent[j]]) {
            hidden[j] = true
        }
        return hidden.map { !$0 }
    }

    /// Whether this phone counts as short of memory, as Android's isLowRamDevice: 2 GB or less.
    /// The Fractal then draws the low budget (220 dots).
    static var lowMemory: Bool {
        let bytes = ProcessInfo.processInfo.physicalMemory
        return bytes > 0 && bytes < 3_000_000_000
    }
}

/// One name VoiceOver reads: its node and its box, a touch target each way at least.
struct FractalPart: Identifiable {
    let node: Int
    let id: String
    let name: String
    let hub: Bool
    let frame: CGRect
}

/// What the 3D Fractal keeps between frames: the tree and its layout (made
/// again only when the store changes), the model (and the tier's budget), the
/// sight (FractalSight, the frame's dots and links), the camera and its fly,
/// and the names the last pose placed. A pose that changes works out the
/// frame and places the names again, once; the renderer (FractalMetal) draws
/// only while something moves. `named` and `moving` are what the views read.
@MainActor @Observable final class FractalHost {
    /// Bumped each time the names are placed again: the names layer and VoiceOver read it.
    var named = 0
    var moving = false
    var focus = 0
    @ObservationIgnored private(set) var tree: FractalTree?
    @ObservationIgnored private(set) var model: FractalModel?
    @ObservationIgnored private(set) var sight: FractalSight?
    @ObservationIgnored private(set) var sink: TreeCGSink?
    @ObservationIgnored private(set) var generation = 0
    @ObservationIgnored private(set) var index: [String: Int] = [:]
    @ObservationIgnored private var layout: FractalLayout3D?
    @ObservationIgnored private var linkEnds: [Int] = []
    @ObservationIgnored private var builtNotes: [Note] = []
    @ObservationIgnored private var builtFolders: [NoteFolder] = []
    @ObservationIgnored private var builtFilter: GraphFilter = .all
    @ObservationIgnored private var builtLens: Set<UUID>?
    @ObservationIgnored private var shown: [Bool]?
    @ObservationIgnored private var builtBudget = 0

    @ObservationIgnored private(set) var camera = GraphPerfCamera()
    @ObservationIgnored private(set) var viewProj = GraphPerfMatrix.identity
    @ObservationIgnored private var fly: (from: GraphPerfCamera, to: GraphPerfCamera, start: Double)?
    /// Whether the camera is still at its first view, so a card that comes or grows frames it again.
    @ObservationIgnored private var home = true
    @ObservationIgnored private(set) var size: CGSize = .zero
    /// What the chrome covers at the view's top and foot, and the first visit's card's cover from the foot (points).
    @ObservationIgnored private var top: Float = 0
    @ObservationIgnored private var bottom: Float = 0
    @ObservationIgnored private(set) var cover: Float = 0
    @ObservationIgnored var reduced = false

    @ObservationIgnored private(set) var chosen = -1
    @ObservationIgnored private(set) var linksOf = -1

    @ObservationIgnored private(set) var screen: [FractalScreenNode] = []
    @ObservationIgnored private(set) var names: [TreePlacedName] = []
    @ObservationIgnored private var held: [Int: Int] = [:]
    private let style = TreeNames3D.Style()
    @ObservationIgnored private var pending = false
    @ObservationIgnored private var wantsHome = false
    /// The renderer's view, set once it is made; nil where Metal can't draw, and then every move lands at once.
    @ObservationIgnored weak var metal: MTKView?
    @ObservationIgnored private(set) var frames = 0

    /// The tree for the store as it is; lays it out again only when the ideas
    /// or the folders change, makes the model again when the tier's budget
    /// does, and shows again only when the filter or the lenses change.
    /// Writes nothing a view reads, so it can run in a body.
    func prepare(_ notes: NoteStore, filter: GraphFilter, lens: Set<UUID>?, tier: GraphicsTier) -> FractalTree {
        let tree: FractalTree
        var grown = false
        if let built = self.tree, notes.notes == builtNotes, notes.folders == builtFolders {
            tree = built
        } else {
            tree = FractalBook.tree(notes.folders, notes.notes)
            self.tree = tree
            layout = FractalLayout3D(tree)
            index = FractalBook.index(tree)
            linkEnds = FractalBook.links(notes.allEdges(), index)
            sink = TreeCGSink(titles: tree.titles.map(TreeNames3D.label))
            builtNotes = notes.notes
            builtFolders = notes.folders
            grown = true
            generation += 1
            held = [:]
            chosen = -1
            linksOf = -1
        }
        let budget = FractalModel.budgetFor(tier, lowMemory: FractalBook.lowMemory)
        if let layout, grown || budget != builtBudget || model == nil {
            let m = FractalModel(tree, layout, linkEnds: linkEnds, budget: budget)
            model = m
            sight = FractalSight(m)
            builtBudget = budget
            if grown { home = true }
            resight()
            soon(home: grown)
        }
        if grown || filter != builtFilter || lens != builtLens {
            shown = FractalBook.shown(tree, index, notes: notes, filter: filter, lens: lens)
            builtFilter = filter
            builtLens = lens
            if !grown { generation += 1 }
            resight()
            soon(home: false)
        }
        return tree
    }

    /// The sight's filter, focus and chosen dot, as the host's.
    private func resight() {
        guard let sight else { return }
        sight.shown = shown
        sight.focus = focus
        sight.chosen = chosen
        sight.linksOf = linksOf
    }

    /// What a body found (a new tree, a filter) is done just after it, so a body never writes what a view reads.
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
    /// away. When it comes or grows a camera still at its first view frames above it.
    func setCover(_ to: CGFloat) {
        let new = max(0, Float(to))
        if new == cover { return }
        let grows = new > cover
        cover = new
        if grows && home { frameHome() } else { changed() }
    }

    /// All ideas and its hubs, from the first view's slant, framed above the card.
    private func frameHome() {
        guard let model, sized else { return }
        fly = nil
        camera = FractalCamera3D.whole(model, width: width, height: height, insets: framed)
        changed()
    }

    /// The camera moved, the window changed or a part was hidden: the frame and the names worked out again, and a frame asked for.
    private func changed() {
        place()
        metal?.setNeedsDisplay()
    }

    /// The frame for the pose now (FractalSight), then the names: last
    /// frame's spots tried first so a name does not hop.
    func place() {
        guard let model, let sight, let sink, sized else { return }
        resight()
        viewProj = FractalCamera3D.viewProj(model, camera, width: width, height: height)
        sight.step(viewProj, focal: camera.focal(viewHeight: height), width: width, height: height)
        screen = FractalCamera3D.screen(sight)
        let floor = TreeNames3D.floor(bottom: bottom, cover: cover)
        names = FractalNames3D.layout(screen, model.tree, style: style,
                                      measure: { i, bold, size in sink.box(i, bold: bold, size: size) },
                                      width: width, height: height, top: top, bottom: floor, held: held, focus: focus)
        held.removeAll(keepingCapacity: true)
        for p in names { held[p.index] = p.spot }
        named &+= 1
    }

    // MARK: moving

    /// A one-finger drag: the map turns round, 3.2 radians a view's width, as the Tree's.
    func turn(dx: CGFloat, dy: CGFloat) {
        guard sized else { return }
        fly = nil
        home = false
        let k = 3.2 / width
        let pitch = min(max(camera.pitch + Float(dy) * k, -1.2), 1.2) - camera.pitch
        camera.orbit(yaw: -Float(dx) * k, pitch: pitch)
        if moving { moving = false }
        changed()
    }

    /// A pinch: nearer or further, within what the map allows.
    func zoom(by factor: CGFloat) {
        guard let model, factor > 0 else { return }
        fly = nil
        home = false
        camera.zoom(by: Float(factor), limits: FractalCamera3D.limits(model))
        if moving { moving = false }
        changed()
    }

    /// Flies in to hub `i` (0, the whole map, flies home).
    func go(_ i: Int) {
        guard let model, sized else { return }
        focus = max(0, i)
        let to: GraphPerfCamera
        if i <= 0 {
            home = true
            to = FractalCamera3D.whole(model, width: width, height: height, insets: framed)
        } else {
            home = false
            to = FractalCamera3D.flyIn(model, i, from: camera, width: width, height: height, insets: framed)
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
        let t = Float((now - f.start) / FractalCamera3D.seconds)
        camera = GraphPerfCamera.glide(f.from, f.to, t: min(max(t, 0), 1))
        if t >= 1 {
            fly = nil
            camera = f.to
            moving = false
        }
        place()
    }

    /// The renderer drew a frame.
    func drawn() { frames &+= 1 }

    // MARK: the chosen idea and its links

    func choose(_ i: Int) {
        chosen = i
        if i < 0 { linksOf = -1 }
        resight()
        changed()
    }

    func showLinks(of i: Int) {
        linksOf = i
        resight()
        changed()
    }

    /// What a tap at `point` lands on: a name, a dot, or -1 for empty space.
    func hit(_ point: CGPoint) -> Int {
        FractalNames3D.hit(screen, names, x: Float(point.x), y: Float(point.y), least: Float(WardControlSize.target))
    }

    /// The names placed, for VoiceOver: each a touch target each way at least.
    func parts() -> [FractalPart] {
        guard let tree else { return [] }
        let t = CGFloat(WardControlSize.target)
        return names.map { p in
            let w = max(t, CGFloat(p.width)), h = max(t, CGFloat(p.height))
            return FractalPart(node: p.index, id: p.index == 0 ? "hub" : tree.ids[p.index], name: tree.titles[p.index],
                               hub: p.index == 0 || tree.childCount[p.index] > 0 || tree.kind[p.index] != FractalTree.idea,
                               frame: CGRect(x: CGFloat(p.x) - w / 2, y: CGFloat(p.y) - h / 2, width: w, height: h))
        }
    }

    /// UI tests only (`-SortaFractalProbe`): the view's size, the camera's
    /// turn, the hub in focus, the card's top, the dots drawn, then each name
    /// and its box (left, top, right, bottom, points in the map), a line each.
    func probe() -> String {
        guard let tree else { return "" }
        var out = "\(Int(size.width)) \(Int(size.height)) \(camera.yaw) \(focus) \(height - cover) \(sight?.count ?? 0)"
        for p in names {
            out += "\n\(tree.titles[p.index])\t\(p.left) \(p.top) \(p.right) \(p.bottom)"
        }
        return out
    }

    /// 0xRRGGBB as a colour's three channels, 0 to 1.
    nonisolated static func rgb(_ c: Int) -> SIMD3<Float> {
        SIMD3(Float((c >> 16) & 255) / 255, Float((c >> 8) & 255) / 255, Float(c & 255) / 255)
    }
}

/// The names, drawn where the placer put them, each in white over a halo of
/// the sky: one Core Text line a name, made once with the tree (TreeCGSink).
private struct FractalNamesLayer: View {
    let host: FractalHost

    var body: some View {
        let named = host.named
        let names = host.names, sink = host.sink
        Canvas { context, _ in
            _ = named
            guard let sink else { return }
            context.withCGContext { cg in
                sink.cg = cg
                for p in names {
                    sink.text(p.index, p.x, p.y, p.size, p.bold, 2, FractalTones.ink, FractalTones.sky, 2 * TreeNames3D.Style().halo)
                }
                sink.cg = nil
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The names VoiceOver reads, each where the pose put it; activating one does what a tap does.
private struct FractalParts: View {
    let host: FractalHost
    let activate: (Int) -> Void

    var body: some View {
        let parts: [FractalPart] = host.moving ? [] : (host.named >= 0 ? host.parts() : [])
        ZStack(alignment: .topLeading) {
            ForEach(parts) { part in
                Color.clear
                    .frame(width: part.frame.width, height: part.frame.height)
                    .offset(x: part.frame.minX, y: part.frame.minY)
                    .allowsHitTesting(false)
                    .accessibilityElement()
                    .accessibilityLabel(part.hub && part.node > 0 ? IdeasWords.fractalFolderSpoken(part.name) : part.name)
                    .accessibilityHint(part.hub ? IdeasWords.fractalZoomHint : IdeasWords.fractalIdeaHint)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("fractal-part-" + part.id)
                    .accessibilityAction { activate(part.node) }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// UI tests only: the names placed and the pose, as one element's label.
private struct FractalProbe: View {
    let host: FractalHost
    static let on = ProcessInfo.processInfo.arguments.contains("-SortaFractalProbe")

    var body: some View {
        let named = host.named
        Color.clear
            .frame(width: 1, height: 1)
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel(named >= 0 ? host.probe() : "")
            .accessibilityIdentifier("fractal-probe")
    }
}

/// Map mode's Fractal: the store as clusters in clusters in 3D, drawn by
/// Metal (FractalMetal), between the strip and the foot. A drag turns it, a
/// pinch zooms, a tap on a hub flies in to it, a tap on an idea shows the
/// peek card, and a tap on empty space or Back flies out a level. `open`
/// opens an idea in the editor; `lens` is the lenses' ideas. Android's
/// IdeasFractal.kt is the same.
struct FractalMapView: View {
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let open: (UUID) -> Void
    var lens: Set<UUID>? = nil

    @AppStorage(FractalFirstRun.seenKey) private var seen = false
    @State private var host = FractalHost()
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
        _ = FractalFirstRun.reset
        let tree = host.prepare(notes, filter: filter, lens: lens, tier: GraphQuality.current.tier)
        let generation = host.generation
        let focus = min(max(host.focus, 0), max(tree.count - 1, 0))
        return ZStack {
            FractalMetalView(host: host)
                .accessibilityHidden(true)
            Color.clear
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in activate(host.hit(value.location)) })
                .simultaneousGesture(drag)
                .simultaneousGesture(magnify)
                .accessibilityElement()
                .accessibilityLabel(IdeasWords.fractalMapSpoken(tree.count > 0 ? tree.titles[focus] : FractalBook.hub))
                .accessibilityIdentifier("fractal-map")
                .onGeometryChange(for: CGSize.self) { $0.size } action: {
                    host.resize($0, top: Self.topChrome, bottom: Self.footChrome)
                }
            FractalNamesLayer(host: host)
            FractalParts(host: host, activate: activate)
            if FractalProbe.on {
                FractalProbe(host: host)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .background(Color(treeRGB: FractalTones.sky))
        .overlay(alignment: .topLeading) {
            if focus > 0 {
                IdeaToolButton(symbol: "chevron.backward",
                               label: IdeasWords.fractalBackSpoken(tree.titles[tree.parent[focus]])) { back() }
                    .accessibilityIdentifier("fractal-back")
                    .padding(WardSpace.gutter)
            }
        }
        .overlay(alignment: .bottom) { card }
        .ideaTools(corner: true) { tools }
        .onAppear { host.reduced = reduceMotion }
        .onChange(of: reduceMotion) { _, now in host.reduced = now }
        .onChange(of: coverNow) { _, now in host.setCover(now) }
        .onChange(of: generation) { _, _ in
            // a new tree starts whole: what was chosen is chosen again if it is still there
            let i = chosen.flatMap { host.index[$0.uuidString] } ?? -1
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
        guard let tree = host.tree else { return }
        let id: UUID? = i > 0 && tree.kind[i] == FractalTree.idea ? UUID(uuidString: tree.ids[i]) : nil
        chosen = id
        if id == nil { linksShown = false }
        host.choose(id != nil ? i : -1)
    }

    /// A hub flies in, an idea is chosen, empty space puts the chosen idea
    /// away or, with none, goes back.
    private func activate(_ i: Int) {
        guard let tree = host.tree else { return }
        if !seen { seen = true }
        if i < 0 || i >= tree.count {
            if chosen != nil { pick(-1) } else { back() }
            return
        }
        if i > 0 && tree.childCount[i] == 0 && tree.kind[i] == FractalTree.idea {
            pick(i)
        } else if i == 0 || tree.childCount[i] > 0 {
            pick(-1)
            host.go(i)
        } else {
            pick(-1)
        }
    }

    private func back() {
        guard let tree = host.tree else { return }
        pick(-1)
        let focus = host.focus
        if focus > 0 { host.go(tree.parent[focus]) }
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
                                      if let i = host.index[other.uuidString] { activate(i) }
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
        var input = GraphPeekInput(theme: "fractal", role: "", folder: false, title: note.title, page: note.kind == .page)
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
        host.showLinks(of: linksShown ? (host.index[id.uuidString] ?? -1) : -1)
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
            Text(IdeasWords.lookFractal)
                .wardFont(.headline)
            Text(IdeasWords.fractalLine)
                .wardFont(.subheadline)
                .fixedSize(horizontal: false, vertical: true)
            Text(IdeasWords.fractalFirstVisit)
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
        .accessibilityIdentifier("fractal-first-visit")
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
