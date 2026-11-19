import Foundation

// The 3D Tree's draw lists, places on screen and names (S11), Foundation
// only, so the suite treemodel3d tests them on Linux and TreeMetal only
// uploads and draws them. Android's TreeModel.kt and TreeNames.kt are the
// same: every branch an instance of one curved, tapering tube and every leaf
// an instance of one leaf blade, worked out once per tree and mode, never per
// frame; the Graphics tier sets the meshes' detail and how many instances are
// kept, and the layout's breadth-first order means a cap drops the finest
// twigs and leaves first. The owner's review of 19 Nov ("the tree doesn't
// look realistic neither the leaves"): the bark is a brown leaning a fifth to
// its folder's colour, each leaf a little lighter or darker and turned about
// its stem, seeded by its node so every run, and Android, agree. The lead's
// review of 19 Nov ("a bare winter tree"): a leaf in its top folder's hue
// (TreeColors.hued), and round each idea's leaf a few smaller filler leaves on
// its stalk, drawn only: not ideas, never counted or tapped, after the ideas'.

/// `nodes` grown by `layout`, coloured by `colors` (wood3d and leaf3d), for
/// `tier`. `branches` holds `branchCount` instances of `branchFloats` (base
/// xyz, tip xyz, the curve's control point xyz, base and tip radius, rgb,
/// node), `leaves` `leafCount` ideas' leaves of `leafFloats` (centre xyz, axis
/// xyz, radius, roll about the axis, rgb, node) and then `fillerCount` filler
/// leaves, each marked by its idea's node plus `fillerMark`.
final class TreeModel3D {
    static let branchFloats = 15
    static let leafFloats = 12
    /// Instances kept of each kind: the High tier draws every one a vault this size has, Smooth fewer.
    static let highCap = 30_000
    static let smoothCap = 8_000
    /// A leaf turned about its stem at most this far either way (radians).
    static let roll = 0.7
    /// A leaf lighter or darker by at most half this, and at most this far toward `yellow`.
    static let lightVar = 0.2, yellowMost = 0.15
    static let yellow = 0xB9C24A
    /// How far a trunk's base flares out and over what share of it; a branch's collar likewise.
    static let flare: Float = 0.55, flareSpan: Float = 0.18, collar: Float = 0.18, collarSpan: Float = 0.12
    /// The tube's closing ring's mark (past 1 along), and how far past the tip, by its radius, it closes.
    static let cap: Float = 2, capReach: Float = 0.9
    /// A leaf blade's widest half width, by its half length; how far its edges
    /// rise off the midrib (by its width) and its tip curls down (by its length).
    static let bladeWide = 0.52, fold = 0.3, curl = 0.3
    /// Filler leaves round each idea's leaf, by tier, and at most this many in all.
    static let fillersHigh = 3, fillersSmooth = 2, fillerCapHigh = 6_000, fillerCapSmooth = 1_500
    /// Where on its idea's stalk a filler grows (a share of it from its base), how far round it swings (radians), its size by the leaf's.
    static let fillerAlongLeast = 0.35, fillerAlongSpan = 0.45, fillerSwingLeast = 0.6, fillerSwingSpan = 0.5
    static let fillerSizeLeast = 0.6, fillerSizeSpan = 0.25
    /// A filler a shade darker than a leaf.
    static let fillerShade = 0.86
    /// A filler's node field: its idea's node plus this, so the renderer hides and fades it with its idea but never lights it as chosen.
    static let fillerMark: Float = 0.25
    /// A filler's floor and ceiling on screen, by a leaf's.
    static let fillerFloor: Float = 0.7

    let nodes: NestNodes
    let layout: TreeLayout3D
    let colors: TreeColors
    let tier: GraphicsTier
    let branches: [Float]
    let leaves: [Float]
    let branchCount: Int
    let leafCount: Int
    let fillerCount: Int
    /// Every blade drawn: the ideas' leaves and the fillers, one instanced draw.
    var blades: Int { leafCount + fillerCount }
    /// Whether the tier's cap left any branch or leaf out.
    let capped: Bool
    /// The tube's sides round and rings along, and the leaf blade's rows, for the tier.
    let segments: Int
    let rings: Int
    let bladeRows: Int
    /// The smallest leaf's radius, or a twig's length where there is none: how close the camera may come.
    let smallest: Float
    /// How far the bodies reach from the trunk's line: the crown's shade.
    let crown: Float

    init(_ nodes: NestNodes, _ layout: TreeLayout3D, colors: TreeColors, tier: GraphicsTier) {
        self.nodes = nodes; self.layout = layout; self.colors = colors; self.tier = tier
        segments = tier == .high ? 12 : 7
        rings = tier == .high ? 6 : 4
        bladeRows = tier == .high ? 7 : 5
        let n = nodes.size
        let cap = tier == .high ? Self.highCap : Self.smoothCap
        let keptBranches = min(n, cap)
        let leafTotal = layout.leaf.reduce(0) { $0 + ($1 > 0 ? 1 : 0) }
        let keptLeaves = min(leafTotal, cap)
        let each = tier == .high ? Self.fillersHigh : Self.fillersSmooth
        let keptFillers = min(keptLeaves * each, tier == .high ? Self.fillerCapHigh : Self.fillerCapSmooth)
        var branches = [Float](repeating: 0, count: keptBranches * Self.branchFloats)
        var leaves = [Float](repeating: 0, count: (keptLeaves + keptFillers) * Self.leafFloats)
        var b = 0, l = 0
        var least = Double.infinity
        for k in 0..<n {
            let i = layout.order[k]
            let g = nodes.group[i]
            if b < keptBranches {
                let o = b * Self.branchFloats
                for a in 0..<3 {
                    let base = layout.base[3 * i + a], run = layout.dir[3 * i + a] * layout.length[i]
                    branches[o + a] = Float(base)
                    branches[o + 3 + a] = Float(base + run)
                    branches[o + 6 + a] = Float(base + run / 2 + layout.bend[3 * i + a])
                }
                branches[o + 9] = Float(layout.r0[i]); branches[o + 10] = Float(layout.r1[i])
                Self.put(&branches, o + 11, colors.wood3d[g])
                branches[o + 14] = Float(i)
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
                    leaves[o + 7] = Float((2 * Self.hash(i, 1) - 1) * Self.roll)
                    // a little lighter or darker, and some a touch toward yellow-green
                    let tone = TreeColors.mix(colors.leaf3d[g], Self.yellow, Self.hash(i, 2) * Self.yellowMost)
                    let light = 1 - Self.lightVar / 2 + Self.lightVar * Self.hash(i, 3)
                    leaves[o + 8] = Float(min(Double((tone >> 16) & 255) / 255 * light, 1))
                    leaves[o + 9] = Float(min(Double((tone >> 8) & 255) / 255 * light, 1))
                    leaves[o + 10] = Float(min(Double(tone & 255) / 255 * light, 1))
                    leaves[o + 11] = Float(i)
                    l += 1
                }
            }
        }
        // the fillers, after every idea's leaf, in the same order: the first ideas' first
        var f = 0
        fill: for q in 0..<l {
            let i = Int(leaves[q * Self.leafFloats + 11])
            for k in 0..<each {
                if f >= keptFillers { break fill }
                Self.filler(i, k, nodes, layout, colors, &leaves, (l + f) * Self.leafFloats)
                f += 1
            }
        }
        self.branches = branches; self.leaves = leaves
        branchCount = b; leafCount = l; fillerCount = f
        capped = keptBranches < n || keptLeaves < leafTotal
        smallest = max(Float(least.isFinite ? least : (layout.length.min() ?? 0.1)), 1e-4)
        var most = 1e-4
        for i in 0..<n {
            let x = layout.anchor[3 * i], z = layout.anchor[3 * i + 2]
            most = max(most, (x * x + z * z).squareRoot())
        }
        crown = Float(most)
    }

    /// What a view pads round each framed point (subtreePoints holds every leaf's tip): a blade's half width.
    var margin: Float { smallest * Float(Self.bladeWide) }

    /// Idea `i`'s `k`th filler leaf into `out` at `o`: on its stalk part way
    /// out, swung round its parent's line to one side and the other in turn,
    /// smaller than its leaf, a shade darker. Android's TreeModel.filler.
    private static func filler(_ i: Int, _ k: Int, _ nodes: NestNodes, _ layout: TreeLayout3D, _ colors: TreeColors,
                               _ out: inout [Float], _ o: Int) {
        let p = nodes.parent[i]
        let lr = layout.leaf[i], len = layout.length[i]
        let t = fillerAlongLeast + fillerAlongSpan * hash(i, 10 + k)
        let swing = (k % 2 == 0 ? 1.0 : -1.0) * (fillerSwingLeast + fillerSwingSpan * hash(i, 20 + k))
        let s = fillerSizeLeast + fillerSizeSpan * hash(i, 30 + k)
        // the turn's axis: the parent's line, or, where the stalk runs along it, any square to the stalk
        let d = [layout.dir[3 * i], layout.dir[3 * i + 1], layout.dir[3 * i + 2]]
        var ax = [layout.dir[3 * p], layout.dir[3 * p + 1], layout.dir[3 * p + 2]]
        let cx = ax[1] * d[2] - ax[2] * d[1], cy = ax[2] * d[0] - ax[0] * d[2], cz = ax[0] * d[1] - ax[1] * d[0]
        if cx * cx + cy * cy + cz * cz < 1e-4 { ax = abs(d[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0] }
        let am = (ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]).squareRoot()
        let kx = ax[0] / am, ky = ax[1] / am, kz = ax[2] / am
        let c = cos(swing), sn = sin(swing), dot = kx * d[0] + ky * d[1] + kz * d[2]
        // Rodrigues: the stalk's line turned about the axis
        let wx = d[0] * c + (ky * d[2] - kz * d[1]) * sn + kx * dot * (1 - c)
        let wy = d[1] * c + (kz * d[0] - kx * d[2]) * sn + ky * dot * (1 - c)
        let wz = d[2] * c + (kx * d[1] - ky * d[0]) * sn + kz * dot * (1 - c)
        let r = lr * s
        out[o] = Float(layout.base[3 * i] + d[0] * len * t + wx * r)
        out[o + 1] = Float(layout.base[3 * i + 1] + d[1] * len * t + wy * r)
        out[o + 2] = Float(layout.base[3 * i + 2] + d[2] * len * t + wz * r)
        out[o + 3] = Float(wx); out[o + 4] = Float(wy); out[o + 5] = Float(wz)
        out[o + 6] = Float(r)
        out[o + 7] = Float((2 * hash(i, 40 + k) - 1) * roll)
        let tone = TreeColors.mix(colors.leaf3d[nodes.group[i]], yellow, hash(i, 50 + k) * yellowMost)
        let light = fillerShade * (1 - lightVar / 2 + lightVar * hash(i, 60 + k))
        out[o + 8] = Float(min(Double((tone >> 16) & 255) / 255 * light, 1))
        out[o + 9] = Float(min(Double((tone >> 8) & 255) / 255 * light, 1))
        out[o + 10] = Float(min(Double(tone & 255) / 255 * light, 1))
        out[o + 11] = Float(i) + fillerMark
    }

    /// Triangles a frame draws: every branch's tube and every blade, the fillers' too, in two instanced draws.
    var triangles: Int { branchCount * segments * (rings + 1) * 2 + blades * (bladeRows - 1) * 4 }

    static func rgb(_ c: Int) -> SIMD3<Float> {
        SIMD3(Float((c >> 16) & 255) / 255, Float((c >> 8) & 255) / 255, Float(c & 255) / 255)
    }

    private static func put(_ out: inout [Float], _ at: Int, _ c: Int) {
        out[at] = Float((c >> 16) & 255) / 255; out[at + 1] = Float((c >> 8) & 255) / 255; out[at + 2] = Float(c & 255) / 255
    }

    /// Node `i`'s seeded number in 0..<1 for `salt`, in 32-bit integer steps, as Android's TreeModel.hash.
    static func hash(_ i: Int, _ salt: Int) -> Double {
        var x = (UInt64(i) &* 2654435761 &+ UInt64(salt) &* 374761393) & 0xffffffff
        x = ((x ^ (x >> 15)) &* 2246822519) & 0xffffffff
        x ^= x >> 13
        return Double(x) / 4294967296.0
    }

    /// How far along a branch ring `k` of `rings` sits: closer together near the base, where the trunk flares.
    static func ringAt(_ k: Int, _ rings: Int) -> Float { Float(pow(Double(k) / Double(rings), 1.4)) }

    /// A tube's `segments` round by `rings` along (cos, sin, along 0 to 1 per
    /// vertex), then one more ring at `cap` along that closes its tip (the
    /// shader draws it as a point just past the tip, so no branch ends in an
    /// open cut), and its triangles.
    static func tube(_ segments: Int, rings: Int) -> (vertices: [Float], indices: [UInt16]) {
        var v = [Float](repeating: 0, count: (segments + 1) * (rings + 2) * 3)
        for r in 0...(rings + 1) {
            for s in 0...segments {
                let a = 2 * Double.pi * Double(s) / Double(segments)
                let o = (r * (segments + 1) + s) * 3
                v[o] = Float(cos(a)); v[o + 1] = Float(sin(a)); v[o + 2] = r > rings ? cap : ringAt(r, rings)
            }
        }
        var idx: [UInt16] = []
        idx.reserveCapacity(segments * (rings + 1) * 6)
        for r in 0...rings {
            for s in 0..<segments {
                let a = r * (segments + 1) + s, c = a + segments + 1
                idx += [UInt16(a), UInt16(a + 1), UInt16(c), UInt16(c), UInt16(a + 1), UInt16(c + 1)]
            }
        }
        return (v, idx)
    }

    /// A leaf blade's half width at `y` from its stem (0) to its tip (1): a pointed oval, widest below its middle.
    static func bladeWidth(_ y: Double) -> Double { bladeWide * pow(sin(Double.pi * y), 0.75) * (1.15 - 0.4 * y) }

    /// A leaf blade, `rows` from stem to tip by three across (edge, midrib,
    /// edge): each vertex its place (x across, y along from -1 at the stem to
    /// 1 at the tip, z off the blade's face) and its normal, six floats;
    /// folded along its midrib and curled down to its tip. Its triangles.
    static func blade(_ rows: Int) -> (vertices: [Float], indices: [UInt16]) {
        var v = [Float](repeating: 0, count: rows * 3 * 6)
        for r in 0..<rows {
            for c in 0...2 {
                let y = Double(r) / Double(rows - 1), w = bladeWidth(y), side = Double(c - 1)
                let o = (r * 3 + c) * 6
                v[o] = Float(side * w); v[o + 1] = Float(2 * y - 1)
                v[o + 2] = Float(fold * abs(side) * w - curl * y * y)
            }
        }
        var idx: [UInt16] = []
        idx.reserveCapacity((rows - 1) * 2 * 6)
        for r in 0..<(rows - 1) {
            for c in 0...1 {
                let a = r * 3 + c, d = a + 3
                idx += [UInt16(a), UInt16(a + 1), UInt16(d), UInt16(d), UInt16(a + 1), UInt16(d + 1)]
            }
        }
        // each vertex's normal: its triangles' faces, by area, turned to the blade's face (+z)
        var nrm = [Double](repeating: 0, count: rows * 3 * 3)
        for t in 0..<(idx.count / 3) {
            let p = (0..<3).map { Int(idx[3 * t + $0]) }
            let e1 = (0..<3).map { Double(v[p[1] * 6 + $0] - v[p[0] * 6 + $0]) }
            let e2 = (0..<3).map { Double(v[p[2] * 6 + $0] - v[p[0] * 6 + $0]) }
            var f = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
            if f[2] < 0 { f = f.map { -$0 } }
            for q in p { for a in 0..<3 { nrm[3 * q + a] += f[a] } }
        }
        for q in 0..<(rows * 3) {
            let m = (nrm[3 * q] * nrm[3 * q] + nrm[3 * q + 1] * nrm[3 * q + 1] + nrm[3 * q + 2] * nrm[3 * q + 2]).squareRoot()
            let o = q * 6
            if m < 1e-12 { v[o + 5] = 1; continue }
            for a in 0..<3 { v[o + 3 + a] = Float(nrm[3 * q + a] / m) }
        }
        return (v, idx)
    }
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
    static let leafPoints: Float = 9

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
    static let wholeFill: Float = 0.96
    /// A fly's length, as the map's.
    static let seconds: Double = 0.8

    /// The whole tree from the front, every branch end and body inside the window the insets leave.
    static func whole(_ m: TreeModel3D, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float)) -> GraphPerfCamera {
        var c = GraphPerfCamera()
        c.pitch = pitch
        let l = m.layout
        // every branch's ends and middle on its curve, every body and every leaf's tip, as Android's TreeFit.points(layout, 0)
        let points = subtreePoints(m, 0)
        // as Android's framer: a blade's half width round each point
        let margin = m.margin
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
        if let first = camera.framing(points: points, margin: m.margin, width: width, height: height, insets: insets, fill: fill) {
            return TreeFit.refine(points, margin: m.margin, first, width: width, height: height, insets: insets, fill: fill)
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

    /// Every branch's ends and middle (on its curve), every body and every leaf's tip of part `index`'s subtree, what a view of it frames.
    static func subtreePoints(_ m: TreeModel3D, _ index: Int) -> [SIMD3<Float>] {
        let l = m.layout, nodes = m.nodes
        var out: [SIMD3<Float>] = []
        out.reserveCapacity((nodes.tout[index] - nodes.tin[index]) * 4)
        for q in nodes.tin[index]..<nodes.tout[index] {
            let i = nodes.order[q]
            for t in [0.0, 0.5, 1.0] {
                let p = l.curveAt(i, t)
                out.append(SIMD3<Float>(Float(p[0]), Float(p[1]), Float(p[2])))
            }
            let x: Double = l.anchor[3 * i], y: Double = l.anchor[3 * i + 1], z: Double = l.anchor[3 * i + 2]
            out.append(SIMD3<Float>(Float(x), Float(y), Float(z)))
            let lr = l.leaf[i]
            if lr > 0 {
                let tx: Double = x + l.dir[3 * i] * lr, ty: Double = y + l.dir[3 * i + 1] * lr, tz: Double = z + l.dir[3 * i + 2] * lr
                out.append(SIMD3<Float>(Float(tx), Float(ty), Float(tz)))
            }
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
    /// Whether the name is cut to `TreeNames3D.shortCut` characters (its whole name found no clear spot).
    var short: Bool = false

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
/// spot is not drawn. So no two names ever overlap, at any angle or zoom. The
/// owner's review of 19 Nov ("the text overlaps a lot"): a branch also tries
/// past the crown a row above and below its tip's row, and a name first looks
/// for a spot clear of every other branch's leaves too (a branch here is one
/// of the view's own: a top folder, or flown in, one growing from the part),
/// its whole name and then cut to `shortCut` characters. Only the trunk, the
/// part flown in to and the view's own branches may then go over another
/// branch's leaves, again whole and then cut; a lesser name with no clear spot
/// is left out, so fewer names show where they do not fit. The lead's review
/// of 19 Nov: flown in, a name first looks for a spot clear of every leaf, its
/// own branch's too, so it stands beside its branch, a short leader back to
/// the tip (nearAboveSpot, nearBelowSpot), halo kept, rather than over the
/// foliage. Android's TreeNames is the same.
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

    /// A name cut this short when its whole name finds no clear spot.
    static let shortCut = 12

    static func label(_ title: String, cut: Int = TreeNames3D.cut) -> String {
        title.count > cut ? String(title.prefix(cut - 1)) + "…" : title
    }

    /// The branch of the view `i` grows on: its ancestor one level under the
    /// part flown in to (`focus`, 0 for the whole tree, whose branches are the
    /// top folders), or `i` itself if it is no deeper. A name keeps clear of
    /// every other branch's leaves. Android's TreeNames.owner.
    static func owner(_ nodes: NestNodes, focus: Int, _ i: Int) -> Int {
        let under = (focus > 0 ? nodes.depth[focus] : 0) + 1
        var k = i
        while nodes.depth[k] > under { k = nodes.parent[k] }
        return k
    }

    /// The leaves on screen by cell, `cell` across, so a name's box finds the
    /// leaves under it at once: `start` by cell into `items`, each a `screen`
    /// position. Made once a layout, as Android's TreeNames.Foliage.
    private struct Foliage {
        let screen: [TreeScreenNode]
        let cell: Float
        let cols: Int, rows: Int
        var start: [Int]
        var items: [Int] = []

        init(_ screen: [TreeScreenNode], _ nodes: NestNodes, width: Float, height: Float, cell: Float) {
            self.screen = screen
            self.cell = cell
            cols = max(1, Int((width / cell).rounded(.up)))
            rows = max(1, Int((height / cell).rounded(.up)))
            start = [Int](repeating: 0, count: cols * rows + 1)
            var leaf = [Bool](repeating: false, count: screen.count)
            for j in screen.indices where screen[j].index >= 0 && screen[j].index < nodes.size {
                leaf[j] = !TreeNames3D.bough(nodes, screen[j].index)
            }
            for j in screen.indices where leaf[j] { each(screen[j]) { start[$0 + 1] += 1 } }
            for k in stride(from: 1, through: cols * rows, by: 1) { start[k] += start[k - 1] }
            items = [Int](repeating: 0, count: start[cols * rows])
            var at = start
            for j in screen.indices where leaf[j] { each(screen[j]) { items[at[$0]] = j; at[$0] += 1 } }
        }

        private func each(_ s: TreeScreenNode, _ f: (Int) -> Void) {
            let r = s.pixels / 2
            let c0 = max(Int(((s.x - r) / cell).rounded(.down)), 0), c1 = min(Int(((s.x + r) / cell).rounded(.down)), cols - 1)
            let r0 = max(Int(((s.y - r) / cell).rounded(.down)), 0), r1 = min(Int(((s.y + r) / cell).rounded(.down)), rows - 1)
            if c0 > c1 || r0 > r1 { return }
            for y in r0...r1 { for x in c0...c1 { f(y * cols + x) } }
        }

        /// Whether the box (centre `x`, `y`) covers a leaf outside `owner`'s subtree, or any leaf if `all`.
        func covers(_ x: Float, _ y: Float, _ w: Float, _ h: Float, owner: Int, _ nodes: NestNodes, all: Bool = false) -> Bool {
            let c0 = max(Int(((x - w / 2) / cell).rounded(.down)), 0), c1 = min(Int(((x + w / 2) / cell).rounded(.down)), cols - 1)
            let r0 = max(Int(((y - h / 2) / cell).rounded(.down)), 0), r1 = min(Int(((y + h / 2) / cell).rounded(.down)), rows - 1)
            if c0 > c1 || r0 > r1 { return false }
            for ry in r0...r1 {
                for cx in c0...c1 {
                    let k = ry * cols + cx
                    for q in start[k]..<start[k + 1] {
                        let s = screen[items[q]]
                        if !all && nodes.within(owner, s.index) { continue }
                        let dx = max(abs(s.x - x) - w / 2, 0), dy = max(abs(s.y - y) - h / 2, 0)
                        if dx * dx + dy * dy < s.pixels * s.pixels / 4 { return true }
                    }
                }
            }
            return false
        }
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
    /// node's words' width and height at a size, whole or cut to `shortCut`
    /// characters (`short`), bold for a branch. `held` is
    /// last time's spot by node, tried first so a name does not hop as the
    /// tree turns. `focus` is the part flown in to, whose names come first;
    /// flown in, only what `nameable` lets through is named, at most `cap`.
    /// `clear` are the chrome's boxes over the view (Back, the round tools): no
    /// name or leader goes under one.
    static func layout(_ screen: [TreeScreenNode], _ nodes: NestNodes, style: Style,
                       measure: (_ node: Int, _ short: Bool, _ bold: Bool, _ size: Float) -> (width: Float, height: Float),
                       width: Float, height: Float, top: Float, bottom: Float,
                       held: [Int: Int] = [:], focus asked: Int = 0, clear: [CGRect] = [], cap: Int = most) -> [TreePlacedName] {
        // a part past the tree's end (one flown in to before the tree shrank) is the whole tree
        let focus = asked < nodes.size ? asked : 0
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
        let foliage = Foliage(screen, nodes, width: width, height: height, cell: 2 * style.bough)
        for node in order(screen, nodes, focus: focus).prefix(budget) {
            if out.count >= limit { break }
            let i = node.index
            if !nameable(nodes, focus: focus, i) { continue }
            let isBough = bough(nodes, i)
            if !isBough && node.pixels < style.leastLeaf { continue }
            let full = label(nodes.titles[i])
            if full.isEmpty { continue }
            let cuts = label(nodes.titles[i], cut: shortCut) != full
            let size = isBough ? style.bough : style.leaf
            let own = owner(nodes, focus: focus, i)
            // only the trunk, the part and the view's own branches may go over another branch's leaves
            let first = i == 0 || i == focus || own == i
            var found: TreePlacedName?
            var line: Segment?
            // flown in, first clear of every leaf (pass 0); then of another branch's (1); then, for the first, over them (2)
            search: for pass in (whole ? 1 : 0)...(first ? 2 : 1) {
                for short in cuts ? [false, true] : [false] {
                    let words = measure(i, short, isBough, size)
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
                        var l: Segment?
                        if let tip = sp.lead {
                            // a leader runs from the box's near side to the tip, clear of every name
                            let near: Float = tip.x < b.0 ? b.0 - w / 2 : b.0 + w / 2
                            let seg = Segment(a: SIMD2<Float>(near, b.1), b: tip, half: 1)
                            if out.contains(where: { seg.crosses($0.x, $0.y, $0.width, $0.height, gap: style.gapY) }) { continue }
                            if blocked.contains(where: { seg.crosses($0.x, $0.y, $0.width, $0.height, gap: style.gapY) }) { continue }
                            l = seg
                        }
                        if pass < 2 && foliage.covers(b.0, b.1, w, h, owner: own, nodes, all: pass == 0) { continue }
                        found = TreePlacedName(index: i, size: size, bold: isBough, x: b.0, y: b.1, width: w, height: h, spot: k,
                                               lead: sp.lead, short: short)
                        line = l
                        break search
                    }
                }
            }
            guard let name = found else { continue }
            if let line { kept.append(line) }
            out.append(name)
        }
        return out
    }

    /// The spots, in the order tried: a branch's tip, past the crown, above the
    /// tip, past the crown a row above and below (a leader back to the tip),
    /// just past the tip a row above and below (a short leader back to it),
    /// then both two rows off; the trunk's foot and beside it.
    static let tipSpot = 0, outsideSpot = 1, aboveTipSpot = 2, pastAboveSpot = 3, pastBelowSpot = 4, footSpot = 0
    static let nearAboveSpot = 5, nearBelowSpot = 6, lastTipSpot = 10

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
                // past the crown a row above and a row below, a leader back to the tip: free space for a crowded branch
                let row = h + style.gapY
                out.append(Spot(x: x, y: t.y - row, slides: false, lead: t))
                out.append(Spot(x: x, y: t.y + row, slides: false, lead: t))
                // just past the tip a row above and below, a short leader back to it: beside the branch, off its leaves
                let near = t.x + side * (2 * style.gapX + w / 2)
                out.append(Spot(x: near, y: t.y - row, slides: false, lead: t))
                out.append(Spot(x: near, y: t.y + row, slides: false, lead: t))
                // in a crowd, both two rows off
                out.append(Spot(x: x, y: t.y - 2 * row, slides: false, lead: t))
                out.append(Spot(x: x, y: t.y + 2 * row, slides: false, lead: t))
                out.append(Spot(x: near, y: t.y - 2 * row, slides: false, lead: t))
                out.append(Spot(x: near, y: t.y + 2 * row, slides: false, lead: t))
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
