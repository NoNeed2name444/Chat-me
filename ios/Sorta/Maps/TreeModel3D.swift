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
    /// How far the bodies reach from the trunk's line: the crown's shade.
    let crown: Float

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
        var most = 1e-4
        for i in 0..<n {
            let x = layout.anchor[3 * i], z = layout.anchor[3 * i + 2]
            most = max(most, (x * x + z * z).squareRoot())
        }
        crown = Float(most)
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

/// The Tree's first view, fitted close (S11, the lead's review of 18 Nov):
/// `framing(points:)` bounds each point on its own, which left the tree a
/// third of the room; `refine` then projects every point through the pose and
/// scales and moves it until their box, `margin` round each, fills `fill` of
/// the window the insets leave on its binding side, centred in it. Android's
/// TreeFit is the same.
enum TreeFit {
    static let rounds = 8
    /// How many yaws round widestYaw tries, every 5 degrees.
    static let yaws = 72

    /// The yaw from which `points` stand widest across (S11): a tree has no
    /// front, so the first view shows its crown spread, its limbs apart rather
    /// than one behind another. Tried from `prefer` outward, a step each way in
    /// turn, so a tie (within a hair) keeps the nearer. Android's TreeFit.widestYaw.
    static func widestYaw(_ points: [SIMD3<Float>], prefer: Float) -> Float {
        var best = prefer, most: Float = -1
        for k in 0..<yaws {
            let step = (k + 1) / 2 * (k % 2 == 1 ? 1 : -1)
            let yaw = prefer + Float(step) * Float(2 * Double.pi / Double(yaws))
            let c = cos(yaw), s = sin(yaw)
            var lo = Float.infinity, hi = -Float.infinity
            for p in points { let x = p.x * c - p.z * s; lo = min(lo, x); hi = max(hi, x) }
            if hi - lo > most * (1 + 1e-4) { most = hi - lo; best = yaw }
        }
        return best
    }

    static func refine(_ points: [SIMD3<Float>], margin: Float, _ pose: GraphPerfCamera, width: Float, height: Float,
                       insets: (top: Float, left: Float, bottom: Float, right: Float), fill: Float) -> GraphPerfCamera {
        guard !points.isEmpty, width > 0, height > 0 else { return pose }
        var c = pose
        let r = c.right, u = c.up, b = c.back
        let f = c.focal(viewHeight: height)
        let w = max(width - insets.left - insets.right, 1), h = max(height - insets.top - insets.bottom, 1)
        // the window's middle from the view's, x right and y up, and the half box to fill
        let wx = insets.left + w / 2 - width / 2, wy = height / 2 - (insets.top + h / 2)
        let hw = w / 2 * fill, hh = h / 2 * fill
        for _ in 0..<rounds {
            guard let box = box(points, margin: margin, eye: c.target + b * c.distance, r: r, u: u, b: b, f: f) else { c.distance *= 1.5; continue }
            let k = max((box.x1 - box.x0) / 2 / hw, (box.y1 - box.y0) / 2 / hh)
            if !(k > 0) || !k.isFinite { continue }
            let cx = (box.x0 + box.x1) / 2, cy = (box.y0 + box.y1) / 2
            let d = c.distance, nd = d * k
            // the box's middle shrinks toward the view's with the distance; then it is moved onto the window's
            // in parts: Xcode's type checker gave up on it as one expression
            let across: Float = (cx * d - wx * nd) / f
            let upward: Float = (cy * d - wy * nd) / f
            c.target += r * across + u * upward
            c.distance = nd
        }
        return c
    }

    /// The points' box on screen from `eye`, in pixels from the view's middle,
    /// y up; nil if one is behind the eye.
    static func box(_ points: [SIMD3<Float>], margin: Float, eye: SIMD3<Float>, r: SIMD3<Float>, u: SIMD3<Float>, b: SIMD3<Float>,
                    f: Float) -> (x0: Float, x1: Float, y0: Float, y1: Float)? {
        var x0 = Float.infinity, x1 = -Float.infinity, y0 = Float.infinity, y1 = -Float.infinity
        for p in points {
            let q = p - eye
            let z = -(q * b).sum()
            if z <= 1e-4 { return nil }
            let k = f / z, sx = (q * r).sum() * k, sy = (q * u).sum() * k, e = margin * k
            x0 = min(x0, sx - e); x1 = max(x1, sx + e); y0 = min(y0, sy - e); y1 = max(y1, sy + e)
        }
        return (x0, x1, y0, y1)
    }
}

/// How thin the Tree may be drawn (S11, the lead's review of 18 Nov): the
/// finest twig at least `twigPoints` across and a leaf at least `leafPoints`
/// long on screen, however far, as Android's TreeWeight. Each floor is a
/// radius per unit of depth: a radius of floor x depth projects, at `focal`
/// pixels per unit at depth 1, to half the width, at `scale` pixels a point.
enum TreeWeight {
    static let twigPoints: Float = 1.5
    static let leafPoints: Float = 6

    static func twigFloor(scale: Float, focal: Float) -> Float { twigPoints / 2 * scale / max(focal, 1) }

    static func leafFloor(scale: Float, focal: Float) -> Float { leafPoints / 2 * scale / max(focal, 1) }
    /// Nor is a leaf drawn longer than this: flown in close, leaves would wall the branches off.
    static let leafMostPoints: Float = 22
    static func leafCeiling(scale: Float, focal: Float) -> Float { leafMostPoints / 2 * scale / max(focal, 1) }
}

/// One node's body on screen: where, how big (its leaf, or its branch's
/// thickness, in points) and how far in front of the eye.
struct TreeScreenNode: Equatable {
    let index: Int
    let x: Float, y: Float
    let pixels: Float
    let depth: Float
    /// A branch's tip on screen, where its name goes first; nil for a leaf.
    var tip: SIMD2<Float>? = nil
}

/// Each node's state byte for the renderer (TreeMetal's state buffer):
/// hidden by the filter, chosen, lit as a link of the chosen, faded toward
/// the page (outside the part flown in to), or none, the first that holds.
/// A flown-in part and all under it are never faded: they draw in full
/// colour. Android's TreeState is the same.
enum TreeState {
    static let none: UInt8 = 0
    static let hidden: UInt8 = 1
    static let chosen: UInt8 = 2
    static let linked: UInt8 = 3
    static let faded: UInt8 = 4

    static func of(_ nodes: NestNodes, _ i: Int, hidden gone: Bool, chosen: Int, linked: Bool, focus: Int) -> UInt8 {
        if gone { return hidden }
        if i == chosen { return Self.chosen }
        if linked { return Self.linked }
        if focus > 0, focus < nodes.size, !nodes.within(focus, i) { return faded }
        return none
    }
}

/// The Tree's camera moves (S11): the first view, a fly-in and how near and
/// far the camera may go, all as GraphPerfCamera poses.
enum TreeCamera3D {
    /// The first view looks a little down on the tree from three quarters
    /// round, so its depth shows, as Android's TREE_PITCH and TREE_YAW.
    static let pitch: Float = 20 * Float.pi / 180
    static let yaw: Float = 35 * Float.pi / 180
    /// A fly-in fills this much of the room the chrome leaves.
    static let fill: Float = 0.88
    /// The first view fills this much of it, on its binding side, as Android's TREE_FILL.
    static let wholeFill: Float = 0.9
    /// A fly's length, as the map's.
    static let seconds: Double = 0.8

    /// The whole tree from the front, every branch end and body inside the window the insets leave.
    static func whole(_ m: TreeModel3D, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float)) -> GraphPerfCamera {
        var c = GraphPerfCamera()
        c.pitch = pitch
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
        // as Android's framer: the smallest leaf round each point; the fill's own margin holds a leaf's reach
        let margin = m.smallest
        // from where the crown stands widest, near three quarters round (TreeFit.widestYaw)
        c.yaw = TreeFit.widestYaw(points, prefer: yaw)
        // then fitted close: the tree fills the room the chrome and the card leave (TreeFit)
        if let fit = c.framing(points: points, margin: margin, width: width, height: height, insets: insets, fill: wholeFill) {
            return TreeFit.refine(points, margin: margin, fit, width: width, height: height, insets: insets, fill: wholeFill)
        }
        return c.framing(centre: centre(m, 0), radius: Float(l.reach[0]), width: width, height: height, insets: insets, fill: wholeFill)
    }

    /// Part `index`'s subtree fitted close from where the camera looks now, as
    /// the first view is (TreeFit), so an orbit is kept; its ball where the fit
    /// has no answer. The ball overstated a big part: with the first view
    /// fitted close, flying in to Cardiology came no nearer. Android's
    /// TreeFit.flyIn is the same.
    static func flyIn(_ m: TreeModel3D, _ index: Int, from camera: GraphPerfCamera, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float)) -> GraphPerfCamera {
        let points = subtreePoints(m, index)
        if let first = camera.framing(points: points, margin: m.smallest, width: width, height: height, insets: insets, fill: fill) {
            return TreeFit.refine(points, margin: m.smallest, first, width: width, height: height, insets: insets, fill: fill)
        }
        return camera.framing(centre: centre(m, index), radius: Float(m.layout.reach[index]), width: width, height: height,
                              insets: insets, fill: fill)
    }

    /// Every branch end and body of part `index`'s subtree, what a view of it frames.
    /// The room a fly-in frames (S11): `insets` raised past each of the
    /// chrome's `clear` boxes, under one in the top half, over one in the foot
    /// half. Android's MapClear.around.
    static func around(_ insets: (top: Float, left: Float, bottom: Float, right: Float), clear: [CGRect],
                       height: Float) -> (top: Float, left: Float, bottom: Float, right: Float) {
        var out = insets
        for r in clear {
            let mid = Float(r.midY), low = Float(r.maxY), high = Float(r.minY)
            if mid < height / 2 { out.top = max(out.top, low) } else { out.bottom = max(out.bottom, height - high) }
        }
        return out
    }

    /// Branch `i`'s point `t` along it, in steps a type checker settles at once.
    static func point(_ l: TreeLayout3D, _ i: Int, along t: Double) -> SIMD3<Float> {
        let x: Double = l.base[3 * i] + l.dir[3 * i] * t
        let y: Double = l.base[3 * i + 1] + l.dir[3 * i + 1] * t
        let z: Double = l.base[3 * i + 2] + l.dir[3 * i + 2] * t
        return SIMD3<Float>(Float(x), Float(y), Float(z))
    }

    static func subtreePoints(_ m: TreeModel3D, _ index: Int) -> [SIMD3<Float>] {
        let l = m.layout, nodes = m.nodes
        var out: [SIMD3<Float>] = []
        out.reserveCapacity((nodes.tout[index] - nodes.tin[index]) * 3)
        for q in nodes.tin[index]..<nodes.tout[index] {
            let i = nodes.order[q]
            for t in [0, l.length[i]] { out.append(point(l, i, along: t)) }
            let x: Double = l.anchor[3 * i], y: Double = l.anchor[3 * i + 1], z: Double = l.anchor[3 * i + 2]
            out.append(SIMD3<Float>(Float(x), Float(y), Float(z)))
        }
        return out
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
    /// (points), with a branch's tip: those not `hidden` and in front of the eye, a little past
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
            var tip: SIMD2<Float>?
            // a branch's tip: where its name goes first
            if l.leaf[i] <= 0 {
                let e = point(l, i, along: l.length[i])
                let q = camera.project(e, width: width, height: height)
                if q.depth > 1e-4 { tip = SIMD2(q.x, q.y) }
            }
            var across: Float = size * f / s.depth
            // a leaf is drawn between its floor and ceiling (TreeWeight), so it is measured so too
            if l.leaf[i] > 0 { across = min(max(across, TreeWeight.leafPoints), TreeWeight.leafMostPoints) }
            out.append(TreeScreenNode(index: i, x: s.x, y: s.y, pixels: across, depth: s.depth, tip: tip))
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
    /// Where a short leader from the name's near side runs to (its branch's tip); nil for none.
    var lead: SIMD2<Float>? = nil

    var left: Float { x - width / 2 }
    var top: Float { y - height / 2 }
    var right: Float { x + width / 2 }
    var bottom: Float { y + height / 2 }
}

/// The 3D Tree's names (S11), from where its bodies are on screen. The trunk
/// first, then branches by level (shallower first), then by the leaves under
/// each, then the nearer. A branch's name goes first at its tip, on the side
/// away from the trunk, then out past the crown on its tip's row with a short
/// leader back to the tip, then above the tip; a leaf's beside it, away from
/// the trunk; the trunk's under its foot, then beside it. Flown in, or for the
/// trunk's own branches, the old spots follow (on the body, under, above,
/// after, before, a row further under and above). On the whole tree (`focus`
/// 0) the trunk is kept clear and deeper names get only their tip spots, so
/// the first view names the trunk, its branches and only what has clear room.
/// Each name takes the first spot clear of every name and leader placed, halo
/// and gap included, inside the view and off the chrome; a name with no clear
/// spot is not drawn. So no two names ever overlap, at any angle or zoom.
/// Android's TreeNames is the same.
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

    /// Flown in, a name for about every `roomPerName` square points of the
    /// room, at least `fewestFlown`, at most `mostFlown`. Android's TreeNames.flownCap.
    static let roomPerName: Float = 18_000
    static let fewestFlown = 6
    static let mostFlown = 30

    /// How many names a flown-in view draws (the lead's review of 18 Nov: 45
    /// names over a wall of leaves was unreadable): one for about every
    /// `roomPerName` square points of the `width` by `height` room the chrome
    /// leaves. A phone gets about 12 to 14.
    static func flownCap(width: Float, height: Float) -> Int {
        let area: Float = max(width, 0) * max(height, 0)
        return min(max(Int(area / roomPerName), fewestFlown), mostFlown)
    }

    /// Whether part `i` may be named flown in to `focus`: the part itself, what
    /// grows straight from it, and the ideas further in; never a part of
    /// another branch, nor a folder deeper in (its ideas speak for it).
    static func nameable(_ nodes: NestNodes, focus: Int, _ i: Int) -> Bool {
        if focus <= 0 || i == focus { return true }
        guard nodes.within(focus, i) else { return false }
        return nodes.parent[i] == focus || !bough(nodes, i)
    }

    /// How far up from the view's foot the names keep: the chrome's foot,
    /// raised to the first-visit card's `cover` while it is up. The cover is
    /// measured from the view's foot and already holds the chrome under the
    /// card, so the two are not added; the first view is framed half a
    /// target higher still. Android's TreeNames.floor is the same.
    static func floor(bottom: Float, cover: Float) -> Float { max(bottom, cover) }

    /// The order names are placed in: the part flown in to (`focus`, 0 for the
    /// whole tree) and what grows from it first, then branches, the shallower
    /// first, then by weight, then the nearer (bigger on screen).
    static func order(_ screen: [TreeScreenNode], _ nodes: NestNodes, focus: Int = 0) -> [TreeScreenNode] {
        let valid = screen.filter { $0.index >= 0 && $0.index < nodes.size }
        let inFocus: (Int) -> Bool = { i in focus < 0 || focus >= nodes.size || nodes.within(focus, i) }
        return valid.sorted { a, b in
            let fa = inFocus(a.index), fb = inFocus(b.index)
            if fa != fb { return fa }
            let ba = bough(nodes, a.index), bb = bough(nodes, b.index)
            if ba != bb { return ba }
            let da = nodes.depth[a.index], db = nodes.depth[b.index]
            if da != db { return da < db }
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
    /// tree turns. `focus` is the part flown in to, whose names come first;
    /// flown in, only what `nameable` lets through is named, at most `cap`.
    /// `clear` are the chrome's boxes over the view (Back, the round tools): no
    /// name or leader goes under one.
    static func layout(_ screen: [TreeScreenNode], _ nodes: NestNodes, style: Style,
                       measure: (_ node: Int, _ bold: Bool, _ size: Float) -> (width: Float, height: Float),
                       width: Float, height: Float, top: Float, bottom: Float,
                       held: [Int: Int] = [:], focus: Int = 0, clear: [CGRect] = [], cap: Int = most) -> [TreePlacedName] {
        var out: [TreePlacedName] = []
        out.reserveCapacity(most)
        let limit = focus > 0 ? min(cap, most) : most
        // the chrome's boxes, taken before any name
        let blocked: [TreePlacedName] = clear.map { r in
            TreePlacedName(index: -1, size: 0, bold: false, x: Float(r.midX), y: Float(r.midY), width: Float(r.width), height: Float(r.height), spot: -1)
        }
        let whole = focus <= 0
        let trunk = screen.first { $0.index == 0 }
        // the crown's sides on screen, every body and tip
        var crownL = Float.infinity, crownR = -Float.infinity
        for n in screen {
            crownL = min(crownL, n.x - n.pixels / 2); crownR = max(crownR, n.x + n.pixels / 2)
            if let t = n.tip { crownL = min(crownL, t.x); crownR = max(crownR, t.x) }
        }
        let middle = trunk?.x ?? width / 2
        // what no name may cover: every leader drawn, and on the whole tree the trunk (but for its own name)
        var kept: [Segment] = []
        var stem: Segment?
        var foot: SIMD2<Float>?
        if let trunk, let t = trunk.tip {
            // the body is `anchor` up the trunk, so its foot is back past it from the top
            let a = Float(TreeShape3D.anchor)
            let body = SIMD2<Float>(trunk.x, trunk.y)
            let f: SIMD2<Float> = (body - t * a) / (1 - a)
            foot = f
            if whole { stem = Segment(a: f, b: t, half: trunk.pixels / 2) }
        }
        for node in order(screen, nodes, focus: focus).prefix(budget) {
            if out.count >= limit { break }
            let i = node.index
            if !nameable(nodes, focus: focus, i) { continue }
            let isBough = bough(nodes, i)
            if !isBough && node.pixels < style.leastLeaf { continue }
            if label(nodes.titles[i]).isEmpty { continue }
            let size = isBough ? style.bough : style.leaf
            let words = measure(i, isBough, size)
            let w = words.width + 2 * style.halo, h = words.height + 2 * style.halo
            let all = spots(node, isBough: isBough, tipsOnly: whole && nodes.depth[i] > 1, w: w, h: h, style: style,
                            middle: middle, crownL: crownL, crownR: crownR, foot: foot)
            var boxes: [(Float, Float)?] = []
            for s in all {
                boxes.append(inView((s.x, s.y), w: w, h: h, width: width, height: height, top: top, bottom: bottom,
                                    edge: style.edge, slides: s.slides))
            }
            var tried: [Int] = Array(boxes.indices)
            if let last = held[i], last >= 0, last < boxes.count { tried.removeAll { $0 == last }; tried.insert(last, at: 0) }
            for k in tried {
                guard let b = boxes[k] else { continue }
                if !out.allSatisfy({ apart(b, w, h, $0, style) }) { continue }
                if !blocked.allSatisfy({ apart(b, w, h, $0, style) }) { continue }
                if kept.contains(where: { $0.crosses(b.0, b.1, w, h, gap: style.gapY) }) { continue }
                if i != 0, let stem, stem.crosses(b.0, b.1, w, h, gap: style.gapY) { continue }
                let sp = all[k]
                var line: Segment?
                if let tip = sp.lead {
                    // a leader runs from the box's near side to the tip, clear of every name
                    let near: Float = tip.x < b.0 ? b.0 - w / 2 : b.0 + w / 2
                    let l = Segment(a: SIMD2<Float>(near, b.1), b: tip, half: 1)
                    if out.contains(where: { l.crosses($0.x, $0.y, $0.width, $0.height, gap: style.gapY) }) { continue }
                    if blocked.contains(where: { l.crosses($0.x, $0.y, $0.width, $0.height, gap: style.gapY) }) { continue }
                    line = l
                }
                if let line { kept.append(line) }
                out.append(TreePlacedName(index: i, size: size, bold: isBough, x: b.0, y: b.1, width: w, height: h, spot: k, lead: sp.lead))
                break
            }
        }
        return out
    }

    /// The spots, in the order tried: a branch's tip, past the crown, above the tip; the trunk's foot and beside it.
    static let tipSpot = 0, outsideSpot = 1, aboveTipSpot = 2, footSpot = 0

    /// A spot: its box's centre, whether it slides along its row to stay in, and the tip its leader runs to.
    private struct Spot {
        let x: Float, y: Float, slides: Bool
        var lead: SIMD2<Float>? = nil
    }

    /// Where `node`'s name may go, in the order tried; `tipsOnly` leaves out all but its tip's spots.
    private static func spots(_ node: TreeScreenNode, isBough: Bool, tipsOnly: Bool, w: Float, h: Float, style: Style,
                              middle: Float, crownL: Float, crownR: Float, foot: SIMD2<Float>?) -> [Spot] {
        var out: [Spot] = []
        out.reserveCapacity(10)
        if node.index == 0 {
            if let f = foot {
                out.append(Spot(x: f.x, y: f.y + style.gapY + h / 2, slides: true))
                let beside = node.pixels / 2 + style.gapX + w / 2
                out.append(Spot(x: f.x + beside, y: f.y - h / 2, slides: false))
                out.append(Spot(x: f.x - beside, y: f.y - h / 2, slides: false))
            }
        } else {
            let t = node.tip ?? SIMD2(node.x, node.y)
            let side: Float = t.x >= middle ? 1 : -1
            let reach = isBough ? 0 : node.pixels / 2
            let tip = Spot(x: t.x + side * (reach + style.gapX + w / 2), y: t.y, slides: false)
            out.append(tip)
            if isBough {
                let edge = side > 0 ? crownR : crownL
                let x = edge + side * (style.gapX + w / 2)
                // past the crown, on the tip's row, unless that is the tip's own spot
                out.append(side * (x - tip.x) > 1 ? Spot(x: x, y: t.y, slides: false, lead: t) : tip)
                out.append(Spot(x: t.x, y: t.y - style.gapY - h / 2, slides: true))
            }
        }
        if tipsOnly { return out }
        let below = node.pixels / 2 + h / 2 + style.gapY
        let side = node.pixels / 2 + style.gapX + w / 2
        if isBough { out.append(Spot(x: node.x, y: node.y, slides: true)) }
        // under, above, after, before: the first two slide along their row to stay in, the others don't
        out += [Spot(x: node.x, y: node.y + below, slides: true), Spot(x: node.x, y: node.y - below, slides: true),
                Spot(x: node.x + side, y: node.y, slides: false), Spot(x: node.x - side, y: node.y, slides: false)]
        // a branch, in a crowd, a row further under and above
        if isBough {
            let further = below + h + style.gapY
            out += [Spot(x: node.x, y: node.y + further, slides: true), Spot(x: node.x, y: node.y - further, slides: true)]
        }
        return out
    }

    /// A line on screen and how far its band reaches each side, as Android's LabelLink.
    struct Segment {
        let a: SIMD2<Float>, b: SIMD2<Float>, half: Float

        /// Whether the band comes within `gap` of the box (centre `x`, `y`): the line clipped to the box
        /// grown by the band and the gap (Liang-Barsky), as Android's LabelRoom.crosses.
        func crosses(_ x: Float, _ y: Float, _ w: Float, _ h: Float, gap: Float) -> Bool {
            let grow = half + gap
            let left = x - w / 2 - grow, right = x + w / 2 + grow, top = y - h / 2 - grow, bottom = y + h / 2 + grow
            let dx = b.x - a.x, dy = b.y - a.y
            var t0: Float = 0, t1: Float = 1
            let p: [Float] = [-dx, dx, -dy, dy], q: [Float] = [a.x - left, right - a.x, a.y - top, bottom - a.y]
            for k in 0..<4 {
                if p[k] == 0 { if q[k] < 0 { return false }; continue }
                let t = q[k] / p[k]
                if p[k] < 0 { if t > t1 { return false }; if t > t0 { t0 = t } } else { if t < t0 { return false }; if t < t1 { t1 = t } }
            }
            return t0 <= t1
        }
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
