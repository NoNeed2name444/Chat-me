import Foundation

// The Tree in 3D (S11, the owner's decision of 18 Nov): the flat layout's
// weights, lengths, fans and drop (TreeLayout), grown round the trunk instead
// of across the page. The trunk goes up the y axis; each branch's children
// leave it spread round it by the golden angle from a seeded start, the
// heaviest the most upright and the lighter ones further out and lower down; a
// crown about as wide as it is tall (the trunk's branches 35 to 60 degrees off
// upright, a deeper one 20 to 50 off its parent's line, lifted a little toward
// the sky). A branch tapers to `taper` of its base
// radius and a child is never thicker than `join` of where it joins. A leaf is
// a shape past its twig's tip. Android's TreeLayout3D.kt is the same line for
// line; tree-fixture.json's expect3d holds both (suite treelayout).

/// The 3D layout's own constants; the rest are `TreeShape`'s.
enum TreeShape3D {
    /// The golden angle, the turn between one child and the next round their parent.
    static let golden = Double.pi * (3 - 5.0.squareRoot())
    /// How far each child is lifted toward the sky before its direction is made whole.
    static let lift = 0.1
    /// The trunk's branches leave it this far off upright, the heaviest the least.
    static let rise0 = 35 * Double.pi / 180, rise1 = 60 * Double.pi / 180
    /// A deeper branch leaves its parent's line this far, before its lift.
    static let splay0 = 20 * Double.pi / 180, splay1 = 50 * Double.pi / 180
    /// A branch's length beside its parent's: the trunk's branches, then deeper ones.
    static let ratio1 = 0.85, ratio2 = 0.72
    /// How far down the trunk its branches start, as a share of it.
    static let drop1 = 0.55
    /// Thickness, across beside length: the trunk's and a branch's.
    static let rootWidth = 0.15, width = 0.1
    /// A leaf's length beside its twig's.
    static let leaf = 0.8
    /// A branch's tip radius beside its base's.
    static let taper = 0.6
    /// A child's base radius at most this much of its parent's where it joins.
    static let join = 0.85
    /// A branch's body (its name's and its tap's place) this far along it.
    static let anchor = 0.6
}

/// Where every branch of `nodes` grows in 3D, in the tree's own units (the
/// trunk is 1 long, from the origin, up y): each node's `base` and unit
/// direction `dir` (x, y, z by node), `length`, radius at its base (`r0`) and
/// tip (`r1`); its body (`anchor`: a leaf's centre, else `TreeShape3D.anchor`
/// along it) and a leaf's radius (`leaf`, 0 for a branch); and its subtree's
/// ball (`centre`, `reach`), what a fly-in frames. `order` is breadth first,
/// the trunk then each level, so a detail cap keeps the big branches.
final class TreeLayout3D {
    let nodes: NestNodes
    private let n: Int
    private(set) var base: [Double], dir: [Double], length: [Double]
    private(set) var r0: [Double], r1: [Double]
    private(set) var anchor: [Double], leaf: [Double]
    private(set) var centre: [Double], reach: [Double]
    private(set) var order: [Int]
    private var rnd = NestRandom(seed: TreeShape.seed)

    /// A node half placed: its own draws done, its children placed one by one, each whole subtree before the next.
    private final class Work {
        let i: Int, len: Double, fan: Double
        var d = [0.0, 0.0, 0.0], u = [0.0, 0.0, 0.0], v = [0.0, 0.0, 0.0]
        var kids: [Int] = []
        var next = 0, tot = 0.0, wmax = 0.0, phi0 = 0.0
        init(_ i: Int, _ len: Double, _ fan: Double) { self.i = i; self.len = len; self.fan = fan }
    }

    init(_ nodes: NestNodes) {
        self.nodes = nodes
        n = nodes.size
        let z = Array(repeating: 0.0, count: n), z3 = Array(repeating: 0.0, count: 3 * n)
        base = z3; dir = z3; length = z; r0 = z; r1 = z; anchor = z3; leaf = z; centre = z3; reach = z
        order = Array(repeating: 0, count: n)
        if n == 0 { return }
        place()
        bounds()
        breadthFirst()
    }

    private func place() {
        var work = [start(0, 0, 0, 0, [0, 1, 0], [1, 0, 0], 1, TreeShape.rootFan, .infinity)]
        while let top = work.last {
            if top.next >= top.kids.count { work.removeLast(); continue }
            work.append(child(top))
        }
    }

    /// The concept's place() up to its loop: the node's own numbers, its draw and its children's order.
    private func start(_ i: Int, _ bx: Double, _ by: Double, _ bz: Double, _ d: [Double], _ u: [Double],
                       _ len: Double, _ fan: Double, _ rmax: Double) -> Work {
        let w = Work(i, len, fan)
        base[3 * i] = bx; base[3 * i + 1] = by; base[3 * i + 2] = bz
        for a in 0..<3 { dir[3 * i + a] = d[a]; w.d[a] = d[a]; w.u[a] = u[a] }
        length[i] = len
        let radius = min(len * (i == 0 ? TreeShape3D.rootWidth : TreeShape3D.width) / 2, rmax)
        r0[i] = radius; r1[i] = radius * TreeShape3D.taper
        w.v[0] = d[1] * u[2] - d[2] * u[1]; w.v[1] = d[2] * u[0] - d[0] * u[2]; w.v[2] = d[0] * u[1] - d[1] * u[0]
        w.phi0 = rnd.next() * 2 * Double.pi
        let count = nodes.childCount[i]
        if count == 0 && i > 0 {
            let lr = len * TreeShape3D.leaf * 0.5
            leaf[i] = lr
            for a in 0..<3 { anchor[3 * i + a] = base[3 * i + a] + d[a] * (len + lr) }
            return w
        }
        for a in 0..<3 { anchor[3 * i + a] = base[3 * i + a] + d[a] * len * TreeShape3D.anchor }
        if count == 0 { return w }
        // heaviest first; ties keep their order (a stable sort), as the concept's
        let wp = nodes.weightPow
        let kids = (0..<count).map { nodes.child(i, $0) }
        w.kids = kids.indices.sorted { p, q in
            wp[kids[p]] != wp[kids[q]] ? wp[kids[p]] > wp[kids[q]] : p < q
        }.map { kids[$0] }
        for c in w.kids { w.tot += wp[c]; if wp[c] > w.wmax { w.wmax = wp[c] } }
        return w
    }

    /// The next child's base, direction, frame, length, fan and radius cap (the loop's body), its own start to come.
    private func child(_ w: Work) -> Work {
        let wp = nodes.weightPow
        let count = w.kids.count
        let q = w.next; w.next += 1
        let c = w.kids[q]
        let i = w.i, len = w.len, fan = w.fan
        let rel = count == 1 ? 0.45 : (q == 0 ? 0.1 : 0.45 + 0.55 * Double(q) / Double(count - 1))
        let s = count == 1 ? 0.5 : Double(q) / Double(count - 1), trunk = i == 0
        let phi = w.phi0 + Double(q) * TreeShape3D.golden
        let th = trunk ? TreeShape3D.rise0 + (TreeShape3D.rise1 - TreeShape3D.rise0) * s
            : TreeShape3D.splay0 + (TreeShape3D.splay1 - TreeShape3D.splay0) * s
        let ct = cos(th), st = sin(th), cp = cos(phi), sp = sin(phi)
        var d = (0..<3).map { a in w.d[a] * ct + (w.u[a] * cp + w.v[a] * sp) * st }
        if !trunk { d[1] += TreeShape3D.lift }
        let dm = (d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).squareRoot()
        for a in 0..<3 { d[a] /= dm }
        let drop = trunk ? TreeShape3D.drop1 : TreeShape.drop
        let t = clamp(1 - drop * pow(rel, 0.85) + (rnd.next() - 0.5) * 0.05, 1 - drop, 1)
        let cl = len * (trunk ? TreeShape3D.ratio1 : TreeShape3D.ratio2) * (0.5 + 0.5 * (wp[c] / w.wmax).squareRoot()) * (1 - TreeShape.shorten * rel)
        let cs = clamp(2 * fan * wp[c] / w.tot * 0.9 + 0.3, TreeShape.fanMin, TreeShape.fanMax)
        // the child's own across: the parent's, turned square to the child
        var ud = w.u[0] * d[0] + w.u[1] * d[1] + w.u[2] * d[2]
        var u = (0..<3).map { a in w.u[a] - d[a] * ud }
        var um = (u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).squareRoot()
        if um < 1e-6 {
            ud = w.v[0] * d[0] + w.v[1] * d[1] + w.v[2] * d[2]
            for a in 0..<3 { u[a] = w.v[a] - d[a] * ud }
            um = (u[0] * u[0] + u[1] * u[1] + u[2] * u[2]).squareRoot()
        }
        for a in 0..<3 { u[a] /= um }
        return start(c, base[3 * i] + w.d[0] * len * t, base[3 * i + 1] + w.d[1] * len * t, base[3 * i + 2] + w.d[2] * len * t,
                     d, u, cl, cs, r0[i] * (1 - (1 - TreeShape3D.taper) * t) * TreeShape3D.join)
    }

    private func bounds() {
        var lo = Array(repeating: 0.0, count: 3 * n), hi = lo
        for i in 0..<n {
            for a in 0..<3 {
                let b0 = base[3 * i + a], b1 = b0 + dir[3 * i + a] * length[i], r = r0[i]
                lo[3 * i + a] = min(b0, b1) - r; hi[3 * i + a] = max(b0, b1) + r
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
