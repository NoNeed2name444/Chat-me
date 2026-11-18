import Foundation

// The Fractal look (S12): Performance's night sky, lit dots, glows and
// hair-thin lines, laid out so every dot is the hub of a smaller copy of the
// cluster it sits in. Tap a dot and its cluster opens round it; back, a tap on
// empty space or a pinch out goes up again. The layout is the approved
// concept's (nested-maps.html, makeF) and the frame its Fractal renderer
// (visitF, drawFractal, labelsFractal, hitTest, frameOf, startAnim), with a
// draw budget and one fix: a name is only placed beside a dot that is drawn
// and visible.
//
// The same code is in Kotlin (android/.../map/Fractal.kt); both platforms'
// tests read tools/fractal_fixture.json, which holds a tree, the layout the
// concept's own code made of it, and frames the Kotlin code recorded
// (FractalCoreTests.swift, FractalCoreTest.kt), so the same data gives the
// same picture on both. Foundation only; the frame allocates nothing.

/// The numbers both platforms share.
nonisolated enum FractalRules {
    /// The seed of the layout's random turns, the concept's.
    static let seed: UInt32 = 91
    /// A dot's room, as a share of its hub's spacing: an idea's, and a hub's least.
    static let leafDot = 0.045
    static let hubDot = 0.1
    static let hubDotParent = 0.05
    /// A cluster opens once its spacing on screen passes this many points;
    /// its dots fade in by `openFull`.
    static let open = 5.0
    static let openFull = 16.0
    /// A hub lights (ball, glow, larger name) as its spacing grows from
    /// `hubFrom` to `hubTo` points.
    static let hubFrom = 14.0
    static let hubTo = 44.0
    /// The most dots drawn in one frame, however many ideas there are; the
    /// last quarter fades out.
    static let budget = 380
    /// The budget at the Balanced and Low graphics settings (High is `budget`).
    static let budgetBalanced = 300
    static let budgetLow = 220
    /// The most links drawn in one frame.
    static let linkBudget = 600
    /// The most names on screen at once.
    static let names = 120
    /// The least a named dot's alpha may be, so a name never stands without its dot.
    static let namedAlpha = 0.6
    /// A named dot's least radius, in points: no name may cover it.
    static let namedDot = 2.5
    /// What is outside the part in view dims to.
    static let dim = 0.18
    /// A name's text size in points, before the reader's text size: an
    /// idea's, and how much more a lit hub's grows.
    static let name = 12.0
    static let hubNameMore = 6.0
    /// A dot's name lies this far past its edge, in points.
    static let nameGap = 5.0
    /// A cluster's children are named once it spans this many points.
    static let nameRoom = 64.0
    /// The share of the view the cluster in view fills (concept: 0.5 x 0.84).
    static let fill = 0.42
    /// The room kept round the cluster in view, beyond the glass, in points.
    static let padTop = 12.0
    static let padSide = 22.0
    static let padBottom = 26.0
    /// How far out and in a pinch can go, against the whole map's view.
    static let zoomOut = 0.35
    static let zoomIn = 1e5
    /// Lines: a hub's line to each dot (x1.2) and a link between clusters
    /// (x0.55), on Performance's base 0.22.
    static let line = 0.22
    static let treeLine = 1.2
    static let linkLine = 0.55
    /// Show links: the chosen idea's own links x3, the rest x0.15 (Performance's).
    static let linksOn = 3.0
    static let linksOff = 0.15

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        var t = (x - a) / (b - a)
        t = t < 0 ? 0 : (t > 1 ? 1 : t)
        return t * t * (3 - 2 * t)
    }

    static func clamp(_ x: Double, _ a: Double, _ b: Double) -> Double {
        x < a ? a : (x > b ? b : x)
    }

    /// The concept's mulberry32: the same numbers in Swift, Kotlin and JavaScript.
    struct Random {
        private var s: UInt32
        init(_ seed: UInt32) { s = seed }
        mutating func next() -> Double {
            s = s &+ 0x6D2B_79F5
            var t = s
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
            return Double(t ^ (t >> 14)) / 4_294_967_296.0
        }
    }
}

/// A tree of nodes for the Fractal map: node 0 the hub (All ideas), every
/// other node's parent earlier in the list. Today the store gives hub →
/// folders (nested as they are) → ideas, and the ideas in no folder hanging
/// from the hub; any deeper tree draws the same way.
nonisolated final class FractalTree {
    static let hub: UInt8 = 0
    static let folder: UInt8 = 1
    static let idea: UInt8 = 2

    /// Each node's parent's index; -1 for the hub.
    let parent: [Int]
    /// `hub`, `folder`, `idea` or `loose` (the ideas in no folder).
    let kind: [UInt8]
    /// The store's id, upper case; "" for the hub and the loose cluster.
    let ids: [String]
    let titles: [String]
    /// The colour's region: the top folder's place among the top folders, or
    /// -1 for none (Performance's palette).
    let region: [Int]
    let count: Int
    /// Children, heaviest first (ties in list order): node i's are
    /// children[childStart[i] ..< childStart[i + 1]].
    private(set) var childStart: [Int]
    private(set) var children: [Int]
    private(set) var childCount: [Int]
    private(set) var depth: [Int]
    /// Leaves under each node, to the power 0.7: how much room it takes.
    private(set) var weight: [Double]
    /// Entry and exit times of a walk: p is q or above it when
    /// tin[p] <= tin[q] < tout[p].
    private(set) var tin: [Int]
    private(set) var tout: [Int]
    /// The nodes in walk order.
    private(set) var order: [Int]

    init(parent: [Int], kind: [UInt8], ids: [String], titles: [String], region: [Int]) {
        precondition(!parent.isEmpty && parent[0] == -1, "node 0 is the hub")
        let n = parent.count
        for i in 1..<max(n, 1) where i < n { precondition(parent[i] >= 0 && parent[i] < i, "a parent comes before its child") }
        self.parent = parent
        self.kind = kind
        self.ids = ids
        self.titles = titles
        self.region = region
        count = n
        childCount = [Int](repeating: 0, count: n)
        childStart = [Int](repeating: 0, count: n + 1)
        children = [Int](repeating: 0, count: max(n - 1, 0))
        depth = [Int](repeating: 0, count: n)
        weight = [Double](repeating: 0, count: n)
        tin = [Int](repeating: 0, count: n)
        tout = [Int](repeating: 0, count: n)
        order = [Int](repeating: 0, count: n)
        for i in 1..<n { childCount[parent[i]] += 1 }
        for i in 0..<n { childStart[i + 1] = childStart[i] + childCount[i] }
        var fill = [Int](repeating: 0, count: n)
        for i in 1..<n {
            let p = parent[i]
            children[childStart[p] + fill[p]] = i
            fill[p] += 1
        }
        for i in 1..<n { depth[i] = depth[parent[i]] + 1 }
        var leaves = [Double](repeating: 0, count: n)
        for i in stride(from: n - 1, through: 0, by: -1) {
            if childCount[i] == 0 { leaves[i] = 1 }
            if i > 0 { leaves[parent[i]] += leaves[i] }
        }
        for i in 0..<n { weight[i] = pow(leaves[i], 0.7) }
        // heaviest first, ties kept in list order (a stable sort, as JavaScript's)
        var buffer = [Int](repeating: 0, count: n)
        for i in 0..<n {
            let s = childStart[i], c = childCount[i]
            if c < 2 { continue }
            for k in 0..<c { buffer[k] = children[s + k] }
            for a in 1..<c {
                let v = buffer[a]
                var b = a - 1
                while b >= 0 && weight[buffer[b]] < weight[v] {
                    buffer[b + 1] = buffer[b]
                    b -= 1
                }
                buffer[b + 1] = v
            }
            for k in 0..<c { children[s + k] = buffer[k] }
        }
        // the walk, without recursion: a deep tree needs no stack
        var t = 0
        var stack = [Int](repeating: 0, count: n)
        var next = [Int](repeating: 0, count: n)
        var top = 0
        stack[0] = 0
        tin[0] = 0
        order[t] = 0
        t += 1
        while top >= 0 {
            let i = stack[top]
            if next[i] < childCount[i] {
                let c = children[childStart[i] + next[i]]
                next[i] += 1
                tin[c] = t
                order[t] = c
                t += 1
                top += 1
                stack[top] = c
            } else {
                tout[i] = t
                top -= 1
            }
        }
    }

    /// Whether `p` is `q` or above it.
    func holds(_ p: Int, _ q: Int) -> Bool { tin[p] <= tin[q] && tin[q] < tout[p] }

    /// A folder as the map reads it.
    struct Folder {
        let id: String
        let parentId: String?
        let name: String
        let created: Int64
    }

    /// An idea as the map reads it.
    struct Idea {
        let id: String
        let folderId: String?
        let title: String
        let created: Int64
    }

    /// The store's folders and ideas as a tree: the hub, the top folders
    /// oldest first, each folder's own folders and then its ideas, oldest
    /// first (the id breaks a tie), and the ideas in no folder hanging from
    /// the hub itself, after the folders. As Kotlin's FractalTree.of.
    static func of(folders: [Folder], ideas: [Idea], hubTitle: String) -> FractalTree {
        var byId: [String: Folder] = [:]
        for f in folders { byId[f.id.uppercased()] = f }
        func parentOf(_ f: Folder) -> String? {
            guard let p = f.parentId?.uppercased(), byId[p] != nil, p != f.id.uppercased() else { return nil }
            return p
        }
        var sub: [String: [Folder]] = [:]
        var topLevel: [Folder] = []
        for f in folders {
            if let p = parentOf(f) { sub[p, default: []].append(f) } else { topLevel.append(f) }
        }
        var inFolder: [String: [Idea]] = [:]
        var loose: [Idea] = []
        for n in ideas {
            if let f = n.folderId?.uppercased(), byId[f] != nil { inFolder[f, default: []].append(n) } else { loose.append(n) }
        }
        func folderOrder(_ a: Folder, _ b: Folder) -> Bool {
            a.created != b.created ? a.created < b.created : a.id.uppercased() < b.id.uppercased()
        }
        func ideaOrder(_ a: Idea, _ b: Idea) -> Bool {
            a.created != b.created ? a.created < b.created : a.id.uppercased() < b.id.uppercased()
        }
        var parents: [Int] = [], kinds: [UInt8] = [], idList: [String] = [], titleList: [String] = [], regions: [Int] = []
        func add(_ p: Int, _ k: UInt8, _ id: String, _ title: String, _ region: Int) -> Int {
            parents.append(p)
            kinds.append(k)
            idList.append(id)
            titleList.append(title)
            regions.append(region)
            return parents.count - 1
        }
        _ = add(-1, hub, "", hubTitle, -1)
        var seen = Set<String>()
        func addFolder(_ f: Folder, _ p: Int, _ region: Int) {
            let key = f.id.uppercased()
            guard seen.insert(key).inserted else { return }
            let me = add(p, folder, key, f.name, region)
            for c in (sub[key] ?? []).sorted(by: folderOrder) { addFolder(c, me, region) }
            for n in (inFolder[key] ?? []).sorted(by: ideaOrder) { _ = add(me, idea, n.id.uppercased(), n.title, region) }
        }
        var tops = topLevel.sorted(by: folderOrder)
        // a folder in a loop of parents has no top: the loop is entered at
        // each of its folders, oldest first
        for f in folders.sorted(by: folderOrder) {
            guard parentOf(f) != nil else { continue }
            var p: String? = f.id.uppercased()
            var path = Set<String>()
            while let at = p, path.insert(at).inserted { p = byId[at].flatMap { parentOf($0) } }
            if p == f.id.uppercased() { tops.append(f) }
        }
        var next = 0
        for f in tops where !seen.contains(f.id.uppercased()) {
            addFolder(f, 0, next)
            next += 1
        }
        for n in loose.sorted(by: ideaOrder) { _ = add(0, idea, n.id.uppercased(), n.title, -1) }
        return FractalTree(parent: parents, kind: kinds, ids: idList, titles: titleList, region: regions)
    }
}

/// Each node's colours, packed 0xRRGGBB: Performance's region colours
/// (GraphPerfScene.swift:61-71 and :180-189), one hue per top folder in turn,
/// the loose grey for the hub and the ideas in no folder. A dot is its hue
/// mixed 0.08 to white per folder level below the top (at most 0.3), a lit
/// hub's ball 0.45 and its glow 0.2. As Kotlin's FractalTones.
nonisolated final class FractalTones {
    let dot: [Int]
    let hub: [Int]
    let glow: [Int]

    /// Twelve hues that read on the night sky (GraphPerfScene.palette).
    static let palette: [Double] = [
        0.36, 0.61, 1.00, 1.00, 0.37, 0.49, 1.00, 0.71, 0.26, 0.25, 0.85, 0.60,
        0.73, 0.55, 1.00, 0.27, 0.84, 0.92, 1.00, 0.53, 0.26, 0.90, 0.42, 1.00,
        0.66, 0.88, 0.35, 1.00, 0.85, 0.35, 0.50, 0.65, 1.00, 1.00, 0.56, 0.72
    ]
    static let loose: [Double] = [0.79, 0.82, 0.88]
    /// The night sky, in both light and dark mode, as Performance's: #05070C.
    static let sky = 0x05070C
    /// The chosen dot's ring and the names' ink.
    static let ink = 0xFFFFFF

    init(_ tree: FractalTree) {
        var dot = [Int](repeating: 0, count: tree.count), hub = dot, glow = dot
        for i in 0..<tree.count {
            let g = tree.region[i]
            let o = g >= 0 ? 3 * (g % (Self.palette.count / 3)) : -1
            let r = o >= 0 ? Self.palette[o] : Self.loose[0]
            let gr = o >= 0 ? Self.palette[o + 1] : Self.loose[1]
            let b = o >= 0 ? Self.palette[o + 2] : Self.loose[2]
            let down = o >= 0 ? min(0.08 * Double(max(tree.depth[i] - 2, 0)), 0.3) : 0
            dot[i] = Self.pack(r, gr, b, down)
            hub[i] = Self.pack(r, gr, b, 0.45)
            glow[i] = Self.pack(r, gr, b, 0.2)
        }
        self.dot = dot
        self.hub = hub
        self.glow = glow
    }

    private static func byte(_ c: Double, _ t: Double) -> Int { min(max(Int(((c + (1 - c) * t) * 255 + 0.5).rounded(.down)), 0), 255) }
    static func pack(_ r: Double, _ g: Double, _ b: Double, _ t: Double) -> Int { byte(r, t) << 16 | byte(g, t) << 8 | byte(b, t) }
}

/// Where each node sits, in the map's own units (the hub's spacing is 1),
/// and each cluster's bounds.
nonisolated final class FractalLayout {
    let tree: FractalTree
    private(set) var x: [Double]
    private(set) var y: [Double]
    /// The spacing of the node's own cluster: its children lie about this far from it.
    private(set) var spacing: [Double]
    /// The dot's radius.
    private(set) var dot: [Double]
    /// The farthest any of its cluster reaches from it.
    private(set) var reach: [Double]
    /// The middle and half size of its cluster's box.
    private(set) var boxX: [Double]
    private(set) var boxY: [Double]
    private(set) var halfW: [Double]
    private(set) var halfH: [Double]

    init(_ tree: FractalTree) {
        self.tree = tree
        let n = tree.count
        let zero = [Double](repeating: 0, count: n)
        x = zero; y = zero; spacing = zero; dot = zero; reach = zero
        boxX = zero; boxY = zero; halfW = zero; halfH = zero
        place()
        for i in 0..<n {
            var r = dot[i] * 3
            var x0 = x[i] - r, x1 = x[i] + r, y0 = y[i] - r, y1 = y[i] + r
            for k in (tree.tin[i] + 1)..<max(tree.tout[i], tree.tin[i] + 1) {
                let d = tree.order[k]
                let e = hypot(x[d] - x[i], y[d] - y[i]) + dot[d]
                if e > r { r = e }
                if x[d] - dot[d] < x0 { x0 = x[d] - dot[d] }
                if x[d] + dot[d] > x1 { x1 = x[d] + dot[d] }
                if y[d] - dot[d] < y0 { y0 = y[d] - dot[d] }
                if y[d] + dot[d] > y1 { y1 = y[d] + dot[d] }
            }
            reach[i] = r
            boxX[i] = (x0 + x1) / 2
            boxY[i] = (y0 + y1) / 2
            let hw = (x1 - x0) / 2, hh = (y1 - y0) / 2, m = max(hw, hh) * 0.6
            halfW[i] = max(hw, m)
            halfH[i] = max(hh, m)
        }
    }

    /// The concept's place(), as a loop over a stack of frames so a deep tree
    /// needs no recursion; each frame lays its children out in the concept's
    /// order, drawing the same random numbers in the same order.
    private func place() {
        let t = tree
        let n = t.count
        var random = FractalRules.Random(FractalRules.seed)
        var left = [Int](repeating: 0, count: n), right = [Int](repeating: 0, count: n), arr = [Int](repeating: 0, count: n)
        var frameNode = [Int](repeating: 0, count: n), frameNext = [Int](repeating: 0, count: n), frameBase = [Int](repeating: 0, count: n)
        var angle = [Double](repeating: 0, count: n)
        var top = -1
        var used = 0
        func enter(_ i: Int, _ px: Double, _ py: Double, _ sp: Double, _ out: Double, _ psp: Double) {
            x[i] = px
            y[i] = py
            spacing[i] = sp
            angle[i] = out
            if t.childCount[i] > 0 { dot[i] = max(sp * FractalRules.hubDot, psp * FractalRules.hubDotParent) }
            let count = t.childCount[i]
            if count == 0 { return }
            let s = t.childStart[i]
            var nl = 0, nr = 0
            for k in 1..<max(count, 1) where k < count {
                if k % 2 == 1 { left[nl] = t.children[s + k]; nl += 1 } else { right[nr] = t.children[s + k]; nr += 1 }
            }
            let base = used
            var w = base
            var k = nl - 1
            while k >= 0 { arr[w] = left[k]; w += 1; k -= 1 }
            arr[w] = t.children[s]
            w += 1
            for k2 in 0..<nr { arr[w] = right[k2]; w += 1 }
            used = w
            top += 1
            frameNode[top] = i
            frameNext[top] = 0
            frameBase[top] = base
        }
        enter(0, 0, 0, 1, -Double.pi / 2, 1)
        while top >= 0 {
            let i = frameNode[top], k = frameNext[top], count = t.childCount[i]
            if k >= count {
                used = frameBase[top]
                top -= 1
                continue
            }
            frameNext[top] = k + 1
            let sp = spacing[i]
            let full = i == 0
            let arc = full ? 2 * Double.pi : min(2 * Double.pi - 1.3, 1.3 + Double(count) * 0.55)
            let start = full ? -Double.pi / 2 - Double.pi * Double(count - 1) / Double(count) : angle[i] - arc / 2
            let slice = arc / Double(count)
            let c = arr[frameBase[top] + k]
            let wmax = t.weight[t.children[t.childStart[i]]]
            let ang = start + arc * (Double(k) + 0.5) / Double(count) + (random.next() - 0.5) * slice * 0.35
            let csp = sp * FractalRules.clamp(0.9 * sin(min(slice, Double.pi) / 2), 0.2, 0.42) * (0.62 + 0.38 * (t.weight[c] / wmax).squareRoot())
            let dist = count > 8 ? sp * ((k % 2 == 1 ? 0.64 : 0.98) + random.next() * 0.1) : sp * (0.74 + random.next() * 0.28)
            if t.childCount[c] == 0 { dot[c] = sp * FractalRules.leafDot }
            enter(c, x[i] + cos(ang) * dist, y[i] + sin(ang) * dist, csp, ang, sp)
        }
    }
}

/// One frame of the Fractal map, in points: where the camera is, which dots
/// are drawn and how brightly, the lines, the names and where a tap lands.
/// Every array is made once, for the tree; `step()` allocates nothing.
nonisolated final class FractalFrame {
    let layout: FractalLayout
    let tree: FractalTree
    private let n: Int

    // the view, in points
    private(set) var width = 1.0
    private(set) var height = 1.0
    private(set) var insetTop = 0.0
    private(set) var insetLeft = 0.0
    private(set) var insetBottom = 0.0
    private(set) var insetRight = 0.0
    /// The reader's text size, as a factor of the standard (the map stops it
    /// at AX1, as every map's).
    var textScale = 1.0
    /// Each node's name's width at 100 points, from the platform's font.
    var nameWidth: [Double]
    /// The least half size of a name's or dot's touch box, in points (iOS
    /// 22: 44 pt; Android 24: 48 dp).
    var reach = 22.0

    // the camera: the map point at the view's middle, and points per map unit
    var camX = 0.0
    var camY = 0.0
    var camZ = 1.0
    /// The cluster in view (Back leaves it for its parent) and the one before
    /// it, while the camera moves.
    private(set) var focus = 0
    private var focusFrom = 0
    private var focusMix = 1.0

    /// The chosen node (its ring), the idea whose links stand out, and which
    /// nodes the filter shows (nil: all).
    var chosen = -1
    var linksOf = -1
    var shown: [Bool]?
    /// The most dots this frame draws: the graphics setting's budget, never
    /// above `FractalRules.budget`.
    var budget = FractalRules.budget {
        didSet { budget = min(max(budget, 1), FractalRules.budget) }
    }

    // the camera's glide (a zoom about the one point both views share)
    private(set) var gliding = false
    private var glideStart = 0.0
    private var glideTime = 0.0
    private var lz0 = 0.0, lz1 = 0.0
    private var g0x = 0.0, g0y = 0.0, g1x = 0.0, g1y = 0.0
    private var fixX = 0.0, fixY = 0.0, fixQx = 0.0, fixQy = 0.0
    private var glideLinear = false

    // per node, this frame
    private(set) var sx: [Double]
    private(set) var sy: [Double]
    /// Its cluster's spacing on screen, in points.
    private(set) var onScreen: [Double]
    /// How far its cluster has opened (0...1): its dots fade in with it.
    private(set) var open: [Double]
    private var stamp: [Int]
    private var frameNo = 0

    // the dots drawn, biggest clusters first
    private(set) var drawn: [Int]
    private(set) var drawnCount = 0
    /// Per drawn dot: radius, alpha, how much a lit hub (0...1), its glow's
    /// alpha, its line's alpha, and whether it is named.
    private(set) var dotRadius: [Double]
    private(set) var dotAlpha: [Double]
    private(set) var dotHub: [Double]
    private(set) var glowAlpha: [Double]
    private(set) var lineAlpha: [Double]
    private(set) var named: [Bool]
    private var slotOf: [Int]
    private var heap: [Int]
    private var heapKey: [Double]
    private var heapSize = 0

    // links: both ends (nodes), and those drawn this frame
    private let linkA: [Int]
    private let linkB: [Int]
    private(set) var linkFrom: [Int]
    private(set) var linkTo: [Int]
    private(set) var linkAlpha: [Double]
    private(set) var linkCount = 0

    // names: node (-1: a lit dot kept clear), box, size, side (0 after,
    // 1 before, 2 centred), alpha
    private(set) var nameNode: [Int]
    private(set) var nameX: [Double]
    private(set) var nameY: [Double]
    private(set) var nameX0: [Double]
    private(set) var nameY0: [Double]
    private(set) var nameX1: [Double]
    private(set) var nameY1: [Double]
    private(set) var nameSize: [Double]
    private(set) var nameSide: [Int]
    private(set) var nameAlpha: [Double]
    private(set) var nameCount = 0
    private var candidate: [Int]
    private var candidateKey: [Double]

    init(_ layout: FractalLayout, linkEnds: [Int] = []) {
        self.layout = layout
        tree = layout.tree
        n = layout.tree.count
        nameWidth = [Double](repeating: 0, count: n)
        let zeros = [Double](repeating: 0, count: n)
        sx = zeros; sy = zeros; onScreen = zeros; open = zeros
        stamp = [Int](repeating: 0, count: n)
        slotOf = [Int](repeating: 0, count: n)
        heap = [Int](repeating: 0, count: n)
        heapKey = zeros
        let b = FractalRules.budget
        drawn = [Int](repeating: 0, count: b)
        dotRadius = [Double](repeating: 0, count: b)
        dotAlpha = dotRadius; dotHub = dotRadius; glowAlpha = dotRadius; lineAlpha = dotRadius
        named = [Bool](repeating: false, count: b)
        candidate = [Int](repeating: 0, count: b)
        candidateKey = dotRadius
        let links = linkEnds.count / 2
        linkA = (0..<links).map { linkEnds[2 * $0] }
        linkB = (0..<links).map { linkEnds[2 * $0 + 1] }
        let lb = FractalRules.linkBudget
        linkFrom = [Int](repeating: 0, count: lb)
        linkTo = linkFrom
        linkAlpha = [Double](repeating: 0, count: lb)
        let nb = FractalRules.names
        nameNode = [Int](repeating: 0, count: nb)
        nameSide = nameNode
        nameX = [Double](repeating: 0, count: nb)
        nameY = nameX; nameX0 = nameX; nameY0 = nameX; nameX1 = nameX; nameY1 = nameX; nameSize = nameX; nameAlpha = nameX
    }

    /// The view's size and the glass over each edge, in points.
    func setView(width w: Double, height h: Double, top: Double, left: Double, bottom: Double, right: Double) {
        width = max(w, 1)
        height = max(h, 1)
        insetTop = top
        insetLeft = left
        insetBottom = bottom
        insetRight = right
    }

    /// How far the camera may go out, and in, against the whole map's view.
    func zoomOutLimit() -> Double { zoomFor(0) * FractalRules.zoomOut }
    func zoomInLimit() -> Double { zoomFor(0) * FractalRules.zoomIn }

    private func areaW() -> Double { max(40, width - insetLeft - insetRight - 2 * FractalRules.padSide) }
    private func areaH() -> Double { max(40, height - insetTop - insetBottom - FractalRules.padTop - FractalRules.padBottom) }

    /// The zoom that fits `i`'s cluster in the view.
    func zoomFor(_ i: Int) -> Double { min(areaW() / layout.halfW[i], areaH() / layout.halfH[i]) * FractalRules.fill }

    /// The camera that frames `i`'s cluster in the room the glass leaves.
    func frameOf(_ i: Int) -> (x: Double, y: Double, z: Double) {
        let z = zoomFor(i)
        let dx = insetLeft + FractalRules.padSide + areaW() / 2 - width / 2
        let dy = insetTop + FractalRules.padTop + areaH() / 2 - height / 2
        return (layout.boxX[i] - dx / z, layout.boxY[i] - dy / z, z)
    }

    /// Opens `i`'s cluster: the camera glides there (at once with `instant`,
    /// as Reduce Motion asks). Returns the glide's length, in seconds.
    @discardableResult
    func go(_ i: Int, now: Double, instant: Bool) -> Double {
        focusFrom = instant ? i : focus
        focus = i
        focusMix = instant ? 1 : 0
        let goal = frameOf(i)
        if instant {
            camX = goal.x
            camY = goal.y
            camZ = goal.z
            gliding = false
            return 0
        }
        return glide(goal.x, goal.y, goal.z, now)
    }

    /// Back up a level; false at the whole map.
    @discardableResult
    func back(now: Double, instant: Bool) -> Bool {
        guard focus > 0 else { return false }
        go(tree.parent[focus], now: now, instant: instant)
        return true
    }

    private func glide(_ x: Double, _ y: Double, _ z: Double, _ now: Double) -> Double {
        g0x = camX; g0y = camY; lz0 = log(camZ); g1x = x; g1y = y; lz1 = log(z)
        let d = camZ - z
        glideLinear = abs(d) < 1e-3 * camZ
        if !glideLinear {
            fixX = (g0x * camZ - g1x * z) / d
            fixY = (g0y * camZ - g1y * z) / d
            fixQx = (fixX - g0x) * camZ
            fixQy = (fixY - g0y) * camZ
        }
        glideTime = FractalRules.clamp(0.46 + 0.15 * abs(lz1 - lz0) / log(2.0), 0.42, 1.25)
        glideStart = now
        gliding = true
        return glideTime
    }

    /// Moves the glide on to `now`, in seconds.
    func advance(_ now: Double) {
        guard gliding else { focusMix = 1; return }
        var t = (now - glideStart) / glideTime
        if t >= 1 { t = 1; gliding = false }
        if t < 0 { t = 0 }
        let u = t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
        focusMix = u
        let z = exp(lz0 + (lz1 - lz0) * u)
        camZ = z
        if glideLinear {
            camX = g0x + (g1x - g0x) * u
            camY = g0y + (g1y - g0y) * u
        } else {
            camX = fixX - fixQx / z
            camY = fixY - fixQy / z
        }
        if !gliding {
            camX = g1x
            camY = g1y
            camZ = exp(lz1)
            focusMix = 1
        }
    }

    /// Stops a glide where it is (a finger came down).
    func stop() {
        gliding = false
        focusMix = 1
        focusFrom = focus
    }

    /// A pinch: zoom by `factor` about the screen point `px`, `py`.
    func zoom(_ px: Double, _ py: Double, _ factor: Double) {
        stop()
        let wx = camX + (px - width / 2) / camZ, wy = camY + (py - height / 2) / camZ
        camZ = FractalRules.clamp(camZ * factor, zoomOutLimit(), zoomInLimit())
        camX = wx - (px - width / 2) / camZ
        camY = wy - (py - height / 2) / camZ
    }

    /// A drag, in points.
    func pan(_ dx: Double, _ dy: Double) {
        stop()
        camX -= dx / camZ
        camY -= dy / camZ
    }

    /// After a pinch or a drag: the deepest cluster that now fills the view
    /// is the one Back leaves.
    func settle() {
        let dx = insetLeft + FractalRules.padSide + areaW() / 2 - width / 2
        let dy = insetTop + FractalRules.padTop + areaH() / 2 - height / 2
        let wx = camX + dx / camZ, wy = camY + dy / camZ
        var i = 0
        while true {
            var best = -1
            var bd = Double.greatestFiniteMagnitude
            let s = tree.childStart[i]
            for k in 0..<tree.childCount[i] {
                let c = tree.children[s + k]
                if tree.childCount[c] == 0 || !isShown(c) { continue }
                if zoomFor(c) > camZ * 1.3 { continue }
                let ddx = wx - layout.x[c], ddy = wy - layout.y[c], dd = ddx * ddx + ddy * ddy
                if dd > layout.reach[c] * layout.reach[c] { continue }
                if dd < bd { bd = dd; best = c }
            }
            if best < 0 { break }
            i = best
        }
        focus = i
        focusFrom = i
        focusMix = 1
    }

    private func isShown(_ i: Int) -> Bool { shown.map { $0[i] } ?? true }

    private func inside(_ top: Int, _ i: Int) -> Bool { top <= 0 || tree.holds(top, i) }

    /// How bright a node is: the part in view stays, the rest dims, and fades
    /// across while the camera moves.
    func dim(_ i: Int) -> Double {
        if focus <= 0 && focusFrom <= 0 { return 1 }
        let b = inside(focus, i) ? 1 : FractalRules.dim
        if focusMix >= 1 { return b }
        let a = inside(focusFrom, i) ? 1 : FractalRules.dim
        return a + (b - a) * focusMix
    }

    /// How much of a lit hub `i` is, by its spacing on screen.
    func hubness(_ i: Int) -> Double {
        tree.childCount[i] > 0 ? FractalRules.smooth(FractalRules.hubFrom, FractalRules.hubTo, onScreen[i]) : 0
    }

    /// A hub whose cluster is bigger than the view has been passed through:
    /// its dot fades away.
    func passed(_ i: Int) -> Double {
        if tree.childCount[i] == 0 || (focus > 0 && tree.holds(focus, i)) { return 1 }
        let m = min(width, height)
        return 1 - FractalRules.smooth(0.35 * m, 0.7 * m, onScreen[i])
    }

    /// A dot's radius on screen.
    func dotSize(_ i: Int, _ h: Double) -> Double {
        FractalRules.clamp(layout.dot[i] * camZ, 1.25 + 1.75 * h, 18 + 8 * h)
    }

    private func stampNode(_ i: Int) -> Bool {
        sx[i] = (layout.x[i] - camX) * camZ + width / 2
        sy[i] = (layout.y[i] - camY) * camZ + height / 2
        stamp[i] = frameNo
        let r = layout.reach[i] * camZ
        if sx[i] + r < -30 || sx[i] - r > width + 30 || sy[i] + r < -30 || sy[i] - r > height + 30 { return false }
        onScreen[i] = layout.spacing[i] * camZ
        return true
    }

    private func push(_ i: Int, _ key: Double) {
        var k = heapSize
        heapSize += 1
        while k > 0 {
            let p = (k - 1) >> 1
            if heapKey[p] >= key { break }
            heap[k] = heap[p]
            heapKey[k] = heapKey[p]
            k = p
        }
        heap[k] = i
        heapKey[k] = key
    }

    private func pop() -> Int {
        let top = heap[0]
        heapSize -= 1
        let last = heap[heapSize], lk = heapKey[heapSize]
        var k = 0
        while true {
            var c = 2 * k + 1
            if c >= heapSize { break }
            if c + 1 < heapSize && heapKey[c + 1] > heapKey[c] { c += 1 }
            if heapKey[c] <= lk { break }
            heap[k] = heap[c]
            heapKey[k] = heapKey[c]
            k = c
        }
        if heapSize > 0 {
            heap[k] = last
            heapKey[k] = lk
        }
        return top
    }

    /// Works out this frame: the dots, lines, links and names.
    func step() {
        frameNo += 1
        drawnCount = 0
        linkCount = 0
        nameCount = 0
        heapSize = 0
        let budget = self.budget
        let fadeFrom = Double(budget) * 0.75
        if stampNode(0) {
            open[0] = 1
            push(0, Double.greatestFiniteMagnitude)
        }
        while heapSize > 0 && drawnCount < budget {
            let i = pop()
            if Double(drawnCount) > fadeFrom {
                open[i] *= 1 - FractalRules.smooth(fadeFrom, Double(budget), Double(drawnCount))
            }
            slotOf[i] = drawnCount
            drawn[drawnCount] = i
            drawnCount += 1
            let count = tree.childCount[i]
            if count > 0 && onScreen[i] > FractalRules.open {
                let ca = FractalRules.smooth(FractalRules.open, FractalRules.openFull, onScreen[i])
                let s = tree.childStart[i]
                // heaviest first, and no more than the budget can still take
                let room = budget - drawnCount - heapSize
                var pushed = 0
                for k in 0..<count {
                    let c = tree.children[s + k]
                    if !isShown(c) { continue }
                    if !stampNode(c) { continue }
                    if pushed >= room { continue }
                    open[c] = ca
                    push(c, onScreen[c])
                    pushed += 1
                }
            }
        }
        heapSize = 0
        let lhi = focusMix > 0.5 ? focus : focusFrom
        names(lhi)
        for k in 0..<drawnCount {
            let i = drawn[k]
            let h = hubness(i)
            var r = dotSize(i, h)
            let rho = layout.dot[i] * camZ
            let fade = h > 0.5 ? 1 : FractalRules.smooth(0.35, 1.25, rho)
            var a = open[i] * fade * dim(i) * passed(i)
            // no name without its mark: a named dot is at least 5 across and 0.6 strong
            if named[k] {
                r = max(r, FractalRules.namedDot)
                a = max(a, max(FractalRules.namedAlpha, open[i] * dim(i) * passed(i)))
            } else if h < 0.3 && underName(sx[i], sy[i], r) {
                a = 0
            }
            dotRadius[k] = r
            dotHub[k] = h
            dotAlpha[k] = a < 0.02 ? 0 : a
            glowAlpha[k] = h < 0.02 ? 0 : h * open[i] * dim(i) * passed(i)
            lineAlpha[k] = 0
            if i != 0 {
                let p = tree.parent[i]
                let dx = sx[i] - sx[p], dy = sy[i] - sy[p]
                let la = FractalRules.line * FractalRules.treeLine * FractalRules.smooth(2, 12, (dx * dx + dy * dy).squareRoot())
                    * open[i] * dim(i) * passed(p)
                lineAlpha[k] = la < 0.01 ? 0 : min(la, 1)
            }
        }
        links()
    }

    /// The nearest drawn node at or above `i`.
    private func resolve(_ i: Int) -> Int {
        var j = i
        while stamp[j] != frameNo || (j != 0 && !drawnNow(j)) { j = tree.parent[j] }
        return j
    }

    /// Whether `j` is drawn this frame.
    func drawnNow(_ j: Int) -> Bool {
        let k = slotOf[j]
        return k < drawnCount && drawn[k] == j
    }

    /// The slot `j` is drawn in this frame (check `drawnNow` first).
    func slot(_ j: Int) -> Int { slotOf[j] }

    private func offscreen(_ x: Double, _ y: Double) -> Bool { x < -20 || x > width + 20 || y < -20 || y > height + 20 }

    private func links() {
        for l in 0..<linkA.count {
            if linkCount >= FractalRules.linkBudget { break }
            if !isShown(linkA[l]) || !isShown(linkB[l]) { continue }
            let a = resolve(linkA[l]), b = resolve(linkB[l])
            if a == b || tree.holds(a, b) || tree.holds(b, a) { continue }
            if offscreen(sx[a], sy[a]) && offscreen(sx[b], sy[b]) { continue }
            let ex = sx[b] - sx[a], ey = sy[b] - sy[a]
            var alpha = FractalRules.line * FractalRules.linkLine * FractalRules.smooth(2, 12, (ex * ex + ey * ey).squareRoot())
                * min(dim(a), dim(b)) * passed(a) * passed(b)
            if linksOf >= 0 {
                alpha *= (linkA[l] == linksOf || linkB[l] == linksOf) ? FractalRules.linksOn : FractalRules.linksOff
            }
            if alpha < 0.01 { continue }
            linkFrom[linkCount] = a
            linkTo[linkCount] = b
            linkAlpha[linkCount] = min(alpha, 1)
            linkCount += 1
        }
    }

    // MARK: names

    private func fits(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Bool {
        if x0 < insetLeft + 4 || x1 > width - insetRight - 4 || y0 < insetTop + 2 || y1 > height - insetBottom - 4 { return false }
        for k in 0..<nameCount {
            let gx = nameNode[k] < 0 ? 1.0 : 8.0
            let gy = nameNode[k] < 0 ? 1.0 : 4.0
            if x0 < nameX1[k] + gx && x1 > nameX0[k] - gx && y0 < nameY1[k] + gy && y1 > nameY0[k] - gy { return false }
        }
        return true
    }

    private func keepClear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) {
        guard nameCount < FractalRules.names else { return }
        nameX0[nameCount] = x0
        nameY0[nameCount] = y0
        nameX1[nameCount] = x1
        nameY1[nameCount] = y1
        nameNode[nameCount] = -1
        nameCount += 1
    }

    /// No name hides a named dot: the box keeps off every dot already named,
    /// and `i`'s own dot is under no name already placed. As Kotlin's.
    private func clearOfNamedDots(_ i: Int, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Bool {
        let r = FractalRules.namedDot
        for k in 0..<nameCount {
            let j = nameNode[k]
            if j < 0 || j == i { continue }
            if x0 < sx[j] + r && x1 > sx[j] - r && y0 < sy[j] + r && y1 > sy[j] - r { return false }
            if sx[i] + r > nameX0[k] && sx[i] - r < nameX1[k] && sy[i] + r > nameY0[k] && sy[i] - r < nameY1[k] { return false }
        }
        return true
    }

    private func put(_ i: Int, _ x: Double, _ y: Double, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double,
                     _ size: Double, _ side: Int, _ alpha: Double) -> Bool {
        if nameCount >= FractalRules.names || !fits(x0, y0, x1, y1) || !clearOfNamedDots(i, x0, y0, x1, y1) { return false }
        let k = nameCount
        nameCount += 1
        nameNode[k] = i
        nameX[k] = x
        nameY[k] = y
        nameX0[k] = x0
        nameY0[k] = y0
        nameX1[k] = x1
        nameY1[k] = y1
        nameSize[k] = size
        nameSide[k] = side
        nameAlpha[k] = alpha
        return true
    }

    /// Whether `i`'s dot shows, so a name beside it has one to name (the
    /// tablet's review: every name beside a visible dot).
    private func dotShows(_ i: Int, _ h: Double) -> Bool {
        if h > 0.5 { return true }
        return FractalRules.smooth(0.35, 1.25, layout.dot[i] * camZ) >= 0.5
    }

    private func names(_ lhi: Int) {
        for k in 0..<drawnCount { named[k] = false }
        // the lit dots are kept clear of names
        for k in 0..<drawnCount {
            let i = drawn[k]
            if tree.childCount[i] == 0 || open[i] < 0.5 || passed(i) < 0.5 { continue }
            let h = hubness(i)
            if h < 0.3 { continue }
            let r = dotSize(i, h)
            if r >= 4 { keepClear(sx[i] - r, sy[i] - r, sx[i] + r, sy[i] + r) }
        }
        let base = lhi > 0 ? tree.depth[lhi] : 0
        var cn = 0
        for k in 0..<drawnCount {
            let i = drawn[k]
            if open[i] < 0.55 || !inside(lhi, i) || passed(i) < 0.5 { continue }
            let rel = tree.depth[i] - base
            if rel > 2 { continue }
            let h = hubness(i)
            if !dotShows(i, h) { continue }
            let ps = i == 0 ? Double.greatestFiniteMagnitude : onScreen[tree.parent[i]]
            let key: Double
            if h > 0.3 {
                key = 1000 + onScreen[i] - Double(rel) * 100
            } else if ps >= FractalRules.nameRoom {
                key = ps - Double(rel) * 100 + (tree.childCount[i] > 0 ? onScreen[i] * 0.01 : 0)
            } else {
                continue
            }
            // insertion, largest key first, ties in draw order
            var b = cn - 1
            while b >= 0 && candidateKey[b] < key {
                candidate[b + 1] = candidate[b]
                candidateKey[b + 1] = candidateKey[b]
                b -= 1
            }
            candidate[b + 1] = i
            candidateKey[b + 1] = key
            cn += 1
        }
        for c in 0..<cn {
            if nameCount >= FractalRules.names { break }
            let i = candidate[c]
            let h = hubness(i)
            let hub = h > 0.3
            let size = hub ? (FractalRules.name + FractalRules.hubNameMore * h).rounded(.toNearestOrAwayFromZero) : FractalRules.name
            var alpha = hub ? 1 : 0.92
            if linksOf >= 0 && tree.kind[i] == FractalTree.idea && i != linksOf && !linked(i, linksOf) {
                alpha *= FractalRules.linksOff
            }
            if nameBeside(i, h, size, alpha) || !hub || size <= 13 { continue }
            _ = nameBeside(i, h, 13, alpha)
        }
        // mark the named dots, so each is drawn whatever is near it
        for k in 0..<nameCount {
            let i = nameNode[k]
            if i >= 0 && drawnNow(i) { named[slotOf[i]] = true }
        }
    }

    private func linked(_ a: Int, _ b: Int) -> Bool {
        for l in 0..<linkA.count where (linkA[l] == a && linkB[l] == b) || (linkA[l] == b && linkB[l] == a) { return true }
        return false
    }

    /// After, before, below, then above its dot.
    private func nameBeside(_ i: Int, _ h: Double, _ points: Double, _ alpha: Double) -> Bool {
        let size = points * textScale
        let w = nameWidth[i] * size / 100
        let ht = size * 1.25
        let r = max(dotSize(i, h), 2.5)
        let x = sx[i], y = sy[i], gx = r + FractalRules.nameGap
        if put(i, x + gx, y, x + gx, y - ht / 2, x + gx + w, y + ht / 2, size, 0, alpha) { return true }
        if put(i, x - gx, y, x - gx - w, y - ht / 2, x - gx, y + ht / 2, size, 1, alpha) { return true }
        var by = y + max(r, 6) + 2 + ht / 2
        if put(i, x, by, x - w / 2, by - ht / 2, x + w / 2, by + ht / 2, size, 2, alpha) { return true }
        by = y - max(r, 6) - 2 - ht / 2
        return put(i, x, by, x - w / 2, by - ht / 2, x + w / 2, by + ht / 2, size, 2, alpha)
    }

    private func underName(_ x: Double, _ y: Double, _ r: Double) -> Bool {
        for k in 0..<nameCount where nameNode[k] >= 0 && x + r > nameX0[k] && x - r < nameX1[k] && y + r > nameY0[k] + 2 && y - r < nameY1[k] - 2 {
            return true
        }
        return false
    }

    // MARK: touch

    /// The node a tap at `px`, `py` lands on, or -1: a name answers anywhere
    /// in a box at least `reach` each way of its middle, then the nearest dot.
    func hit(_ px: Double, _ py: Double) -> Int {
        var bk = -1
        var bd = Double.greatestFiniteMagnitude
        for k in 0..<nameCount where nameNode[k] >= 0 {
            let hx = max((nameX1[k] - nameX0[k]) / 2 + 4, reach), hy = max((nameY1[k] - nameY0[k]) / 2 + 6, reach)
            let ux = abs(px - (nameX0[k] + nameX1[k]) / 2) / hx, uy = abs(py - (nameY0[k] + nameY1[k]) / 2) / hy
            let dd = max(ux, uy)
            if dd <= 1 && dd < bd { bd = dd; bk = k }
        }
        if bk >= 0 { return nameNode[bk] }
        var best = -1
        var bs = Double.greatestFiniteMagnitude
        for k in 0..<drawnCount {
            let i = drawn[k]
            if open[i] < 0.3 || dotAlpha[k] <= 0 { continue }
            let d = hypot(px - sx[i], py - sy[i])
            let thr = max(dotRadius[k] + 6, reach)
            if d > thr { continue }
            let s = d / thr - Double(tree.depth[i]) * 0.02
            if s < bs { bs = s; best = i }
        }
        return best
    }

    /// What a tap on `i` does: open the cluster it heads, or the one it sits
    /// in; -1 when it is open already.
    func clusterFor(_ i: Int) -> Int {
        let t = tree.childCount[i] > 0 ? i : tree.parent[i]
        return (t == focus || t < 0) ? -1 : t
    }
}
