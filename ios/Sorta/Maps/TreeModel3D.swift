import Foundation

// The 3D Tree's draw lists, places on screen and names (S11), Foundation
// only, so the suite treemodel3d tests them on Linux and TreeMetal only
// uploads and draws them. Android's TreeModel.kt and TreeNames.kt are the
// same: every branch an instance of one tapered tube and every leaf an
// instance of one flattened ball, worked out once per tree and mode, never
// per frame; the Graphics tier sets the meshes' detail and how many
// instances are kept, and the layout's breadth-first order means a cap drops
// the finest twigs and leaves first.

/// `nodes` grown by `layout`, coloured by `colors` (the bark and leaf ramps'
/// full step), for `tier`. `branches` holds `branchCount` instances of
/// `branchFloats` (base xyz, tip xyz, base and tip radius, rgb, node),
/// `leaves` `leafCount` of `leafFloats` (centre xyz, axis xyz, radius, rgb, node).
final class TreeModel3D {
    static let branchFloats = 12
    static let leafFloats = 11
    /// Instances kept of each kind: the High tier draws every one a vault this size has, Smooth fewer.
    static let highCap = 30_000
    static let smoothCap = 8_000

    let nodes: NestNodes
    let layout: TreeLayout3D
    let colors: TreeColors
    let tier: GraphicsTier
    let branches: [Float]
    let leaves: [Float]
    let branchCount: Int
    let leafCount: Int
    /// Whether the tier's cap left any branch or leaf out.
    let capped: Bool
    let segments: Int
    /// The smallest leaf's radius, or a twig's length where there is none: how close the camera may come.
    let smallest: Float

    init(_ nodes: NestNodes, _ layout: TreeLayout3D, colors: TreeColors, tier: GraphicsTier) {
        self.nodes = nodes; self.layout = layout; self.colors = colors; self.tier = tier
        segments = tier == .high ? 12 : 7
        let n = nodes.size
        let cap = tier == .high ? Self.highCap : Self.smoothCap
        let keptBranches = min(n, cap)
        let leafTotal = layout.leaf.reduce(0) { $0 + ($1 > 0 ? 1 : 0) }
        let keptLeaves = min(leafTotal, cap)
        var branches = [Float](repeating: 0, count: keptBranches * Self.branchFloats)
        var leaves = [Float](repeating: 0, count: keptLeaves * Self.leafFloats)
        var b = 0, l = 0
        var least = Double.infinity
        for k in 0..<n {
            let i = layout.order[k]
            let g = nodes.group[i]
            if b < keptBranches {
                let o = b * Self.branchFloats
                for a in 0..<3 {
                    branches[o + a] = Float(layout.base[3 * i + a])
                    branches[o + 3 + a] = Float(layout.base[3 * i + a] + layout.dir[3 * i + a] * layout.length[i])
                }
                branches[o + 6] = Float(layout.r0[i]); branches[o + 7] = Float(layout.r1[i])
                Self.put(&branches, o + 8, colors.barkRamp[g][8])
                branches[o + 11] = Float(i)
                b += 1
            }
            if layout.leaf[i] > 0 {
                least = min(least, layout.leaf[i])
                if l < keptLeaves {
                    let o = l * Self.leafFloats
                    for a in 0..<3 {
                        leaves[o + a] = Float(layout.anchor[3 * i + a]); leaves[o + 3 + a] = Float(layout.dir[3 * i + a])
                    }
                    leaves[o + 6] = Float(layout.leaf[i])
                    Self.put(&leaves, o + 7, colors.leafRamp[g][8])
                    leaves[o + 10] = Float(i)
                    l += 1
                }
            }
        }
        self.branches = branches; self.leaves = leaves
        branchCount = b; leafCount = l
        capped = keptBranches < n || keptLeaves < leafTotal
        smallest = max(Float(least.isFinite ? least : (layout.length.min() ?? 0.1)), 1e-4)
    }

    static func rgb(_ c: Int) -> SIMD3<Float> {
        SIMD3(Float((c >> 16) & 255) / 255, Float((c >> 8) & 255) / 255, Float(c & 255) / 255)
    }

    private static func put(_ out: inout [Float], _ at: Int, _ c: Int) {
        out[at] = Float((c >> 16) & 255) / 255; out[at + 1] = Float((c >> 8) & 255) / 255; out[at + 2] = Float(c & 255) / 255
    }

    /// A tube's `segments` round (cos, sin, along 0 or 1 per vertex), and its triangles.
    static func tube(_ segments: Int) -> (vertices: [Float], indices: [UInt16]) {
        var v = [Float](repeating: 0, count: (segments + 1) * 2 * 3)
        for s in 0...segments {
            for h in 0...1 {
                let a = 2 * Double.pi * Double(s) / Double(segments)
                let o = (s * 2 + h) * 3
                v[o] = Float(cos(a)); v[o + 1] = Float(sin(a)); v[o + 2] = Float(h)
            }
        }
        var idx = [UInt16](repeating: 0, count: segments * 6)
        for s in 0..<segments {
            let a = UInt16(s * 2), b = UInt16(s * 2 + 1), c = UInt16(s * 2 + 2), d = UInt16(s * 2 + 3)
            let o = s * 6
            idx[o] = a; idx[o + 1] = c; idx[o + 2] = b; idx[o + 3] = b; idx[o + 4] = c; idx[o + 5] = d
        }
        return (v, idx)
    }

    /// A unit ball, `rings` from pole to pole and `segments` round (x, y, z per vertex, y the leaf's axis), and its triangles.
    static func ball(rings: Int, segments: Int) -> (vertices: [Float], indices: [UInt16]) {
        var v = [Float](repeating: 0, count: (rings + 1) * (segments + 1) * 3)
        for r in 0...rings {
            for s in 0...segments {
                let t = Double.pi * Double(r) / Double(rings), p = 2 * Double.pi * Double(s) / Double(segments)
                let o = (r * (segments + 1) + s) * 3
                v[o] = Float(sin(t) * cos(p)); v[o + 1] = Float(cos(t)); v[o + 2] = Float(sin(t) * sin(p))
            }
        }
        var idx: [UInt16] = []
        idx.reserveCapacity(rings * segments * 6)
        for r in 0..<rings {
            for s in 0..<segments {
                let a = r * (segments + 1) + s, b = a + segments + 1
                idx += [UInt16(a), UInt16(b), UInt16(a + 1), UInt16(a + 1), UInt16(b), UInt16(b + 1)]
            }
        }
        return (v, idx)
    }

    /// The ball's rings for this tier.
    var rings: Int { segments > 8 ? 8 : 5 }
}

/// One node's body on screen: where, how big (its leaf, or its branch's
/// thickness, in points) and how far in front of the eye.
struct TreeScreenNode: Equatable {
    let index: Int
    let x: Float, y: Float
    let pixels: Float
    let depth: Float
}

/// The Tree's camera moves (S11): the first view, a fly-in and how near and
/// far the camera may go, all as GraphPerfCamera poses.
enum TreeCamera3D {
    /// The first view looks a little down on the tree, as Android's TREE_PITCH.
    static let pitch: Float = 14 * Float.pi / 180
    /// A fly-in fills this much of the room the chrome leaves.
    static let fill: Float = 0.88
    /// A fly's length, as the map's.
    static let seconds: Double = 0.8

    /// The whole tree from the front, every branch end and body inside the window the insets leave.
    static func whole(_ m: TreeModel3D, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float)) -> GraphPerfCamera {
        var c = GraphPerfCamera()
        c.yaw = 0; c.pitch = pitch
        let l = m.layout
        var points: [SIMD3<Float>] = []
        points.reserveCapacity(m.branchCount * 2 + m.leafCount)
        for k in 0..<m.branchCount {
            let o = k * TreeModel3D.branchFloats
            points.append(SIMD3(m.branches[o], m.branches[o + 1], m.branches[o + 2]))
            points.append(SIMD3(m.branches[o + 3], m.branches[o + 4], m.branches[o + 5]))
        }
        for k in 0..<m.leafCount {
            let o = k * TreeModel3D.leafFloats
            points.append(SIMD3(m.leaves[o], m.leaves[o + 1], m.leaves[o + 2]))
        }
        let margin = Float(l.leaf.max() ?? 0)
        if let fit = c.framing(points: points, margin: margin, width: width, height: height, insets: insets, fill: 0.9) { return fit }
        return c.framing(centre: centre(m, 0), radius: Float(l.reach[0]), width: width, height: height, insets: insets, fill: 0.9)
    }

    /// Part `index`'s subtree framed from where the camera looks now.
    static func flyIn(_ m: TreeModel3D, _ index: Int, from camera: GraphPerfCamera, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float)) -> GraphPerfCamera {
        camera.framing(centre: centre(m, index), radius: Float(m.layout.reach[index]), width: width, height: height,
                       insets: insets, fill: fill)
    }

    static func centre(_ m: TreeModel3D, _ i: Int) -> SIMD3<Float> {
        let c = m.layout.centre
        return SIMD3(Float(c[3 * i]), Float(c[3 * i + 1]), Float(c[3 * i + 2]))
    }

    /// How near and far a pinch may take the camera.
    static func limits(_ m: TreeModel3D) -> ClosedRange<Float> {
        let near = m.smallest * 4, far = max(Float(m.layout.reach[0]) * 8, near * 2)
        return near...far
    }

    /// Each node's body through `camera` onto a `width` by `height` view
    /// (points): those not `hidden` and in front of the eye, a little past
    /// the view kept so a name can slide in.
    static func project(_ m: TreeModel3D, _ camera: GraphPerfCamera, width: Float, height: Float, hidden: [Bool]?) -> [TreeScreenNode] {
        let l = m.layout
        let n = m.nodes.size
        let f = camera.focal(viewHeight: height)
        var out: [TreeScreenNode] = []
        out.reserveCapacity(n)
        for i in 0..<n {
            if let hidden, i < hidden.count, hidden[i] { continue }
            let p = SIMD3<Float>(Float(l.anchor[3 * i]), Float(l.anchor[3 * i + 1]), Float(l.anchor[3 * i + 2]))
            let s = camera.project(p, width: width, height: height)
            if s.depth <= 1e-4 { continue }
            if s.x < -width * 0.25 || s.x > width * 1.25 || s.y < -height * 0.25 || s.y > height * 1.25 { continue }
            let size = Float(l.leaf[i] > 0 ? l.leaf[i] * 2 : l.r0[i] * 2)
            out.append(TreeScreenNode(index: i, x: s.x, y: s.y, pixels: size * f / s.depth, depth: s.depth))
        }
        return out
    }
}

/// A name placed: its node, its words' size and weight, and its box (centre and size, halo in, points).
struct TreePlacedName: Equatable {
    let index: Int
    let size: Float
    let bold: Bool
    let x: Float, y: Float, width: Float, height: Float
    let spot: Int

    var left: Float { x - width / 2 }
    var top: Float { y - height / 2 }
    var right: Float { x + width / 2 }
    var bottom: Float { y + height / 2 }
}

/// The 3D Tree's names (S11), from where its bodies are on screen. Branch
/// names first (the trunk and every part that holds others), then by the
/// leaves under each, then the nearer; each takes the first of its spots (on
/// its branch for a branch, then under, above, after and before its body, and
/// for a branch a row further under and above) that is clear of every name placed, halo and gap included, inside the view
/// and off the chrome; a name with no clear spot is not drawn. So no two
/// names ever overlap, at any angle or zoom. Android's TreeNames is the same.
enum TreeNames3D {
    /// The sizes the names keep, in points.
    struct Style {
        var bough: Float = 13, leaf: Float = 11.5
        var halo: Float = 2.5
        var gapX: Float = 6, gapY: Float = 3
        var edge: Float = 12
        /// A leaf drawn smaller than this across is too far to name.
        var leastLeaf: Float = 5
    }

    /// A name's longest, in characters, as the map's.
    static let cut = 23

    static func label(_ title: String) -> String {
        title.count > cut ? String(title.prefix(cut - 1)) + "…" : title
    }

    static func bough(_ nodes: NestNodes, _ i: Int) -> Bool { i == 0 || nodes.childCount[i] > 0 || nodes.folder[i] }

    /// The cost guards: at most this many bodies are tried for a name each
    /// frame, the first in the order, and at most `most` names are drawn.
    static let budget = 380
    static let most = 120

    /// How far up from the view's foot the names keep: the chrome's foot,
    /// raised to the first-visit card's `cover` while it is up. The cover is
    /// measured from the view's foot and already holds the chrome under the
    /// card, so the two are not added; the first view is framed half a
    /// target higher still. Android's TreeNames.floor is the same.
    static func floor(bottom: Float, cover: Float) -> Float { max(bottom, cover) }

    /// The order names are placed in: the part flown in to (`focus`, 0 for the
    /// whole tree) and what grows from it first, then branches, then by
    /// weight, then the nearer (bigger on screen).
    static func order(_ screen: [TreeScreenNode], _ nodes: NestNodes, focus: Int = 0) -> [TreeScreenNode] {
        let valid = screen.filter { $0.index >= 0 && $0.index < nodes.size }
        let inFocus: (Int) -> Bool = { i in focus < 0 || focus >= nodes.size || nodes.within(focus, i) }
        return valid.sorted { a, b in
            let fa = inFocus(a.index), fb = inFocus(b.index)
            if fa != fb { return fa }
            let ba = bough(nodes, a.index), bb = bough(nodes, b.index)
            if ba != bb { return ba }
            let wa = nodes.weight[a.index], wb = nodes.weight[b.index]
            if wa != wb { return wa > wb }
            if a.pixels != b.pixels { return a.pixels > b.pixels }
            return a.index < b.index
        }
    }

    /// The names to draw for `screen`, in a view `width` by `height` whose top
    /// `top` and bottom `bottom` points the chrome covers. `measure` gives a
    /// node's words' width and height at a size, bold for a branch. `held` is
    /// last time's spot by node, tried first so a name does not hop as the
    /// tree turns. `focus` is the part flown in to, whose names come first.
    static func layout(_ screen: [TreeScreenNode], _ nodes: NestNodes, style: Style,
                       measure: (_ node: Int, _ bold: Bool, _ size: Float) -> (width: Float, height: Float),
                       width: Float, height: Float, top: Float, bottom: Float,
                       held: [Int: Int] = [:], focus: Int = 0) -> [TreePlacedName] {
        var out: [TreePlacedName] = []
        out.reserveCapacity(most)
        for node in order(screen, nodes, focus: focus).prefix(budget) {
            if out.count >= most { break }
            let i = node.index
            let isBough = bough(nodes, i)
            if !isBough && node.pixels < style.leastLeaf { continue }
            if label(nodes.titles[i]).isEmpty { continue }
            let size = isBough ? style.bough : style.leaf
            let words = measure(i, isBough, size)
            let w = words.width + 2 * style.halo, h = words.height + 2 * style.halo
            let below = node.pixels / 2 + h / 2 + style.gapY
            let side = node.pixels / 2 + style.gapX + w / 2
            // under, above, after, before: the first two slide along their row to stay in, the others don't
            var all: [(Float, Float, Bool)] = [(node.x, node.y + below, true), (node.x, node.y - below, true),
                                               (node.x + side, node.y, false), (node.x - side, node.y, false)]
            // a branch also tries its own body first and, in a crowd, a row further under and above
            if isBough {
                all.insert((node.x, node.y, true), at: 0)
                let further = below + h + style.gapY
                all += [(node.x, node.y + further, true), (node.x, node.y - further, true)]
            }
            var spots: [(Float, Float)?] = []
            for s in all {
                spots.append(inView((s.0, s.1), w: w, h: h, width: width, height: height, top: top, bottom: bottom,
                                    edge: style.edge, slides: s.2))
            }
            var tried: [Int] = Array(spots.indices)
            if let last = held[i], last >= 0, last < spots.count { tried.removeAll { $0 == last }; tried.insert(last, at: 0) }
            for k in tried {
                guard let s = spots[k] else { continue }
                if out.allSatisfy({ apart(s, w, h, $0, style) }) {
                    out.append(TreePlacedName(index: i, size: size, bold: isBough, x: s.0, y: s.1, width: w, height: h, spot: k))
                    break
                }
            }
        }
        return out
    }

    /// A spot kept inside the view, `edge` in from either side, and off the
    /// chrome: one under or above its body (`slides`) slides along its row,
    /// one beside it that would leave is dropped.
    private static func inView(_ s: (Float, Float), w: Float, h: Float, width: Float, height: Float,
                               top: Float, bottom: Float, edge: Float, slides: Bool) -> (Float, Float)? {
        var x = s.0
        let lo = edge + w / 2, hi = width - edge - w / 2
        if x < lo || x > hi {
            if !slides { return nil }
            x = lo > hi ? width / 2 : min(max(x, lo), hi)
        }
        if s.1 - h / 2 < top || s.1 + h / 2 > height - bottom { return nil }
        return (x, s.1)
    }

    private static func apart(_ s: (Float, Float), _ w: Float, _ h: Float, _ p: TreePlacedName, _ style: Style) -> Bool {
        abs(s.0 - p.x) * 2 >= w + p.width + 2 * style.gapX || abs(s.1 - p.y) * 2 >= h + p.height + 2 * style.gapY
    }

    /// Whether any two of `names`' boxes overlap (they never should).
    static func overlaps(_ names: [TreePlacedName]) -> Bool {
        for a in names.indices {
            for b in names.indices where b > a {
                let p = names[a], q = names[b]
                if abs(p.x - q.x) * 2 < p.width + q.width && abs(p.y - q.y) * 2 < p.height + q.height { return true }
            }
        }
        return false
    }

    /// What a tap at `x`, `y` lands on: a name's box first, then the body
    /// whose disc (at least `least` across) holds it, the nearest to its
    /// centre for its size; -1 for empty space.
    static func hit(_ screen: [TreeScreenNode], _ names: [TreePlacedName], x: Float, y: Float, least: Float) -> Int {
        for p in names where abs(x - p.x) * 2 <= p.width && abs(y - p.y) * 2 <= p.height { return p.index }
        var best = -1, bestScore = Float.infinity
        for s in screen {
            let r = max(s.pixels, least) / 2
            let d = ((s.x - x) * (s.x - x) + (s.y - y) * (s.y - y)).squareRoot()
            if d <= r, d / r < bestScore { bestScore = d / r; best = s.index }
        }
        return best
    }
}
