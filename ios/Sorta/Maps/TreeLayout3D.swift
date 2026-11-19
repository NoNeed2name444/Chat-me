import Foundation

// The Tree in 3D, grown as a tree (S11, the owner's review of the third pass, 19 Nov: "the tree
// doesn't look realistic neither the leaves . also it's too cramped"). The trunk goes up the y axis.
// - Thickness follows the pipe model (Shinozaki 1964, Leonardo's rule): a branch's area is the sum of
//   the leaves it carries, r0 = rootR * sqrt(leaves / all leaves), never over `join` of where it
//   joins; it tapers to its biggest child's (at most `tip` of its base).
// - Length follows its share of the tree: len1 * (leaves / all leaves)^lenPow, never over `ratio` of
//   its parent's nor under `twig` leaves.
// - Children leave their parent's line round it by the golden angle from a seeded start (3D
//   phyllotaxis, Vogel 1979), the heaviest the most upright: the trunk's branches rise0 to rise1 off
//   upright along its top drop1, a deeper branch splay0 to splay1 off its parent's line along its last
//   TreeShape.drop (`spread` more a child after the first, at most dropMost), then lifted (`lift`); a
//   child carrying most of its parent leans less (`lead` of its share squared), so the tree keeps a
//   leader. The first child, and an only child, leaves near its parent's tip. Two sibling branches are
//   never nearer than `gap`: one too near a branch placed before it turns on round its parent, a
//   `turn` at a time; a branch after the first leans at least 1.5 `gap` off its parent's line.
// - An idea is a short stem (`stem` of its leaf) at its parent's end (its last `spray`), spray0 to
//   spray1 off the parent's line, its leaf past the stem's tip: a parent's ideas make one spray. A
//   leaf is leafK / sqrt(all leaves) long each side, at most leafMost, varied by leafVar.
// - A branch bends: its middle is pulled down and out (`bend` of its length), so it leaves its parent
//   flatter and turns up toward its tip, and swayed level to one side (up to `sway`), seeded. Its line
//   is a quadratic curve from base to tip through base + dir * len / 2 + bend; a child joins on the
//   curve and leaves it from the curve's own direction there.
// Android's TreeLayout3D.kt is the same line for line; tree-fixture.json's expect3d holds both
// (suite treelayout).

/// The 3D layout's own constants; the seed and a deeper branch's drop are `TreeShape`'s.
enum TreeShape3D {
    /// The golden angle, the turn between one child and the next round their parent.
    static let golden = Double.pi * (3 - 5.0.squareRoot())
    private static let deg = Double.pi / 180
    /// The trunk's base radius (the trunk is 1 long); a branch's is this by the root of its share of the leaves.
    static let rootR = 0.075
    /// A branch's tip radius at most this much of its base's.
    static let tip = 0.85
    /// A child's base radius at most this much of its parent's where it joins.
    static let join = 0.95
    /// A branch's length: len1 by its share of the leaves to lenPow, at most `ratio` of its parent's.
    static let len1 = 0.95, lenPow = 0.45, ratio = 0.8
    /// The trunk's branches leave it this far off upright, the heaviest the least.
    static let rise0 = 40 * deg, rise1 = 70 * deg
    /// A deeper branch leaves its parent's line this far, before its lift.
    static let splay0 = 30 * deg, splay1 = 60 * deg
    /// An idea's stem leaves its parent's line this far, before its lift.
    static let spray0 = 40 * deg, spray1 = 75 * deg
    /// A child carrying most of its parent leans this much less, by its share squared.
    static let lead = 0.6
    /// How far each deeper child is lifted toward the sky before its direction is made whole.
    static let lift = 0.2
    /// How far down the trunk its branches start, down a branch its ideas, and down a deeper branch its
    /// branches: TreeShape.drop and `spread` more a child after the first, at most dropMost. The first
    /// child, and an only child, leaves near the tip (a tenth of the way down that drop's reach).
    static let drop1 = 0.3, spray = 0.35, spread = 0.03, dropMost = 0.75
    /// Two sibling branches at least this far apart; one too near turns on round its parent a `turn` at a
    /// time, `turns` at most, else takes the turn that came nearest. A branch after the first leans at least 1.5 gap.
    static let gap = 18 * deg, turn = 10 * deg
    static let turns = 36
    /// A branch's middle pulled down and out, and level to one side at most, beside its length.
    static let bend = 0.12, sway = 0.08
    /// An idea's stem, and a branch's least length, beside a leaf.
    static let stem = 0.6, twig = 8.0
    /// A leaf's half length: leafK by one over the root of all the leaves, at most leafMost, varied by leafVar.
    static let leafK = 0.75, leafMost = 0.09, leafVar = 0.3
    /// A branch's body (its name's and its tap's place) this far along it.
    static let anchor = 0.6
}

/// Where every branch of `nodes` grows in 3D, in the tree's own units (the
/// trunk is 1 long, from the origin, up y): each node's `base` and unit
/// direction `dir` (x, y, z by node: the line from base to tip), `length`,
/// radius at its base (`r0`) and tip (`r1`), and `bend` (its curve's middle
/// beside the line's); its body (`anchor`: a leaf's centre, else
/// `TreeShape3D.anchor` along the curve) and a leaf's radius (`leaf`, 0 for a
/// branch); and its subtree's ball (`centre`, `reach`), what a fly-in frames.
/// `order` is breadth first, the trunk then each level, so a detail cap keeps the big branches.
final class TreeLayout3D {
    let nodes: NestNodes
    private let n: Int
    private(set) var base: [Double], dir: [Double], length: [Double]
    private(set) var r0: [Double], r1: [Double], bend: [Double]
    private(set) var anchor: [Double], leaf: [Double]
    private(set) var centre: [Double], reach: [Double]
    private(set) var order: [Int]
    private var rnd = NestRandom(seed: TreeShape.seed)
    private let leafSize: Double

    init(_ nodes: NestNodes) {
        self.nodes = nodes
        n = nodes.size
        let z = Array(repeating: 0.0, count: n), z3 = Array(repeating: 0.0, count: 3 * n)
        base = z3; dir = z3; length = z; r0 = z; r1 = z; bend = z3; anchor = z3; leaf = z; centre = z3; reach = z
        order = Array(repeating: 0, count: n)
        leafSize = n > 0 ? min(TreeShape3D.leafMost, TreeShape3D.leafK / nodes.weight[0].squareRoot()) : 0
        if n == 0 { return }
        place(0, [0, 0, 0], [0, 1, 0], [1, 0, 0], 1, .infinity, 1)
        bounds()
        breadthFirst()
    }

    /// Node `i`'s point `t` along its curve.
    func curveAt(_ i: Int, _ t: Double) -> [Double] {
        let u = 1 - t, len = length[i]
        var out = [0.0, 0.0, 0.0]
        for a in 0..<3 {
            let b = base[3 * i + a], d = dir[3 * i + a]
            let c = b + d * len / 2 + bend[3 * i + a], e = b + d * len
            out[a] = u * u * b + 2 * u * t * c + t * t * e
        }
        return out
    }

    /// Node `i`'s curve's direction at `t` (unit).
    func tangentAt(_ i: Int, _ t: Double) -> [Double] {
        let len = length[i]
        var out = [0.0, 0.0, 0.0]
        for a in 0..<3 {
            let b = base[3 * i + a], d = dir[3 * i + a]
            let c = b + d * len / 2 + bend[3 * i + a], e = b + d * len
            out[a] = 2 * (1 - t) * (c - b) + 2 * t * (e - c)
        }
        let m = (out[0] * out[0] + out[1] * out[1] + out[2] * out[2]).squareRoot()
        for a in 0..<3 { out[a] /= m }
        return out
    }

    private func pipe(_ i: Int) -> Double { TreeShape3D.rootR * (nodes.weight[i] / nodes.weight[0]).squareRoot() }

    /// Node `i` and its subtree; `scale` is how much a parent held under its pipe radius passes to its children.
    private func place(_ i: Int, _ b: [Double], _ d0: [Double], _ u0: [Double], _ len: Double, _ rmax: Double, _ scale: Double) {
        typealias S = TreeShape3D
        for a in 0..<3 { base[3 * i + a] = b[a]; dir[3 * i + a] = d0[a] }
        length[i] = len
        let radius = min(pipe(i) * scale, rmax), count = nodes.childCount[i], k0 = radius / pipe(i)
        r0[i] = radius
        let v = [d0[1] * u0[2] - d0[2] * u0[1], d0[2] * u0[0] - d0[0] * u0[2], d0[0] * u0[1] - d0[1] * u0[0]]
        let phi0 = rnd.next() * 2 * Double.pi
        if count == 0 && i > 0 {
            r1[i] = radius * S.tip
            let lr = min(leafSize * (1 - S.leafVar / 2 + S.leafVar * rnd.next()), S.leafMost)
            leaf[i] = lr
            for a in 0..<3 { anchor[3 * i + a] = b[a] + d0[a] * (len + lr) }
            return
        }
        // the bend: down and out from the line's middle (none for an upright line), then a sway to one side
        let pu = [-d0[0] * d0[1], 1 - d0[1] * d0[1], -d0[2] * d0[1]]
        let pm = (pu[0] * pu[0] + pu[1] * pu[1] + pu[2] * pu[2]).squareRoot()
        let sway = (2 * rnd.next() - 1) * S.sway * len
        // the sway level, square to the line (its across where the line is upright)
        let side = pm > 1e-6 ? [-d0[2] / pm, 0, d0[0] / pm] : v
        for a in 0..<3 { bend[3 * i + a] = (pm > 1e-6 ? -pu[a] / pm * S.bend * len : 0) + side[a] * sway }
        let mid = curveAt(i, S.anchor)
        for a in 0..<3 { anchor[3 * i + a] = mid[a] }
        if count == 0 { r1[i] = radius * S.tip; return }
        // heaviest first; ties keep their order (a stable sort), as the concept's
        let wp = nodes.weightPow
        let unsorted = (0..<count).map { nodes.child(i, $0) }
        let kids = unsorted.indices.sorted { p, q in
            wp[unsorted[p]] != wp[unsorted[q]] ? wp[unsorted[p]] > wp[unsorted[q]] : p < q
        }.map { unsorted[$0] }
        var most = 0.0
        for c in kids { most = max(most, pipe(c)) }
        r1[i] = min(most * k0, radius * S.tip)
        let trunk = i == 0
        var placed: [[Double]] = []
        for q in 0..<count {
            let c = kids[q], idea = nodes.childCount[c] == 0
            let rel = count == 1 ? 0.1 : (q == 0 ? 0.1 : 0.45 + 0.55 * Double(q) / Double(count - 1))
            let s = count == 1 ? 0.5 : Double(q) / Double(count - 1)
            let drop = trunk ? S.drop1 : (idea ? S.spray : min(TreeShape.drop + S.spread * Double(count - 1), S.dropMost))
            let t = clamp(1 - drop * pow(rel, 0.85) + (rnd.next() - 0.5) * 0.05, 1 - drop, 1)
            let at = curveAt(i, t), tg = tangentAt(i, t)
            // the parent's frame at t: its across turned square to the curve there
            var ud = u0[0] * tg[0] + u0[1] * tg[1] + u0[2] * tg[2]
            var ut = [u0[0] - tg[0] * ud, u0[1] - tg[1] * ud, u0[2] - tg[2] * ud]
            var um = (ut[0] * ut[0] + ut[1] * ut[1] + ut[2] * ut[2]).squareRoot()
            for a in 0..<3 { ut[a] /= um }
            let vt = [tg[1] * ut[2] - tg[2] * ut[1], tg[2] * ut[0] - tg[0] * ut[2], tg[0] * ut[1] - tg[1] * ut[0]]
            let share = nodes.weight[c] / nodes.weight[i]
            let lean = (trunk ? S.rise0 + (S.rise1 - S.rise0) * s : S.splay0 + (S.splay1 - S.splay0) * s) * (1 - S.lead * share * share)
            // a branch after the first leans at least 1.5 gap off its parent's line, so turning round can clear the first
            let th = idea ? S.spray0 + (S.spray1 - S.spray0) * s : (q > 0 ? max(lean, 1.5 * S.gap) : lean)
            let ct = cos(th), st = sin(th)
            func aim(_ f: Double) -> [Double] {
                let cp = cos(f), sp = sin(f)
                var d = (0..<3).map { a in tg[a] * ct + (ut[a] * cp + vt[a] * sp) * st }
                if !trunk { d[1] += S.lift }
                let dm = (d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).squareRoot()
                for a in 0..<3 { d[a] /= dm }
                return d
            }
            var phi = phi0 + Double(q) * S.golden
            if !idea {
                // turned on round the parent until clear of every sibling branch, else the turn that came nearest
                var best = phi, bestNear = -1.0
                for k in 0..<S.turns {
                    let f = phi + Double(k) * S.turn
                    let d = aim(f)
                    var near = Double.pi
                    for p in placed { near = min(near, acos(clamp(p[0] * d[0] + p[1] * d[1] + p[2] * d[2], -1, 1))) }
                    if near > bestNear { bestNear = near; best = f }
                    if near >= S.gap { break }
                }
                phi = best
            }
            let d = aim(phi)
            if !idea { placed.append(d) }
            let cl = idea ? leafSize * S.stem : max(min(S.len1 * pow(nodes.weight[c] / nodes.weight[0], S.lenPow), len * S.ratio), leafSize * S.twig)
            ud = ut[0] * d[0] + ut[1] * d[1] + ut[2] * d[2]
            var u = [ut[0] - d[0] * ud, ut[1] - d[1] * ud, ut[2] - d[2] * ud]
            um = (u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).squareRoot()
            if um < 1e-6 {
                ud = vt[0] * d[0] + vt[1] * d[1] + vt[2] * d[2]
                u = [vt[0] - d[0] * ud, vt[1] - d[1] * ud, vt[2] - d[2] * ud]
                um = (u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).squareRoot()
            }
            for a in 0..<3 { u[a] /= um }
            place(c, at, d, u, cl, (radius + (r1[i] - radius) * t) * S.join, k0)
        }
    }

    private func bounds() {
        var lo = Array(repeating: 0.0, count: 3 * n), hi = lo
        for i in 0..<n {
            for a in 0..<3 {
                let b0 = base[3 * i + a], b1 = b0 + dir[3 * i + a] * length[i], bc = (b0 + b1) / 2 + bend[3 * i + a], r = r0[i]
                lo[3 * i + a] = min(b0, b1, bc) - r; hi[3 * i + a] = max(b0, b1, bc) + r
                if leaf[i] > 0 {
                    let c = anchor[3 * i + a]
                    lo[3 * i + a] = min(lo[3 * i + a], c - leaf[i]); hi[3 * i + a] = max(hi[3 * i + a], c + leaf[i])
                }
            }
        }
        // a parent always comes before its children, so one pass from the end gathers each subtree
        for i in stride(from: n - 1, through: 1, by: -1) {
            let p = nodes.parent[i]
            for a in 0..<3 { lo[3 * p + a] = min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = max(hi[3 * p + a], hi[3 * i + a]) }
        }
        for i in 0..<n {
            for a in 0..<3 { centre[3 * i + a] = (lo[3 * i + a] + hi[3 * i + a]) / 2 }
            let x = hi[3 * i] - lo[3 * i], y = hi[3 * i + 1] - lo[3 * i + 1], z = hi[3 * i + 2] - lo[3 * i + 2]
            reach[i] = (x * x + y * y + z * z).squareRoot() / 2
        }
    }

    private func breadthFirst() {
        var head = 0, tail = 0
        order[tail] = 0; tail += 1
        while head < tail {
            let i = order[head]; head += 1
            for k in 0..<nodes.childCount[i] { order[tail] = nodes.child(i, k); tail += 1 }
        }
    }

    private func clamp(_ x: Double, _ a: Double, _ b: Double) -> Double { x < a ? a : (x > b ? b : x) }
}
