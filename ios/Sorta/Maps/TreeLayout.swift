import Foundation

// The Tree look's layout (S11): the approved concept's (nested maps, 18 Nov;
// tools/tree_fixture.mjs keeps it word for word). Each branch fans its
// children by weight, the heaviest straight on and the lighter ones further
// out and lower down, from a seeded draw, so the same tree always grows the
// same way, on both platforms. Android's TreeLayout.kt is the same line for
// line; tree-fixture.json holds both to the concept (suite treelayout).

/// The layout's constants: the concept's, for the Tree.
enum TreeShape {
    /// Points along each branch.
    static let k = 9
    /// A leaf's length beside its twig's.
    static let leaf = 0.5
    static let seed: UInt32 = 11
    static let rootAngle = -Double.pi / 2
    static let rootFan = 0.85
    static let fanMin = 0.45
    static let fanMax = 0.85
    static let spread = 1.05
    static let drop = 0.42
    static let ratio = 0.7
    static let shorten = 0.22
    static let width = 0.075
    static let rootWidth = 0.12
    static let bend = 0.06
}

/// Where every branch of `nodes` grows, in the tree's own units (the trunk
/// is 1 long, from 0, 0, upward): base, direction, length and width; its K
/// points and their normals; its subtree's round bounds, for culling; and its
/// subtree's box along its own axis, for framing it.
final class TreeLayout {
    let nodes: NestNodes
    private let n: Int
    private let k = TreeShape.k
    private(set) var bx: [Double], by: [Double], angle: [Double], length: [Double], width: [Double]
    private(set) var px: [Double], py: [Double], nx: [Double], ny: [Double]
    private(set) var bcx: [Double], bcy: [Double], brad: [Double]
    private(set) var u0: [Double], u1: [Double], v0: [Double], v1: [Double]
    private var rnd = NestRandom(seed: TreeShape.seed)

    private final class WorkItem {
        let i: Int, x: Double, y: Double, a: Double, len: Double, fan: Double
        var started = false
        var arr: [Int] = []
        var next = 0
        var tot = 0.0
        var wmax = 0.0
        var cum = 0.0
        init(_ i: Int, _ x: Double, _ y: Double, _ a: Double, _ len: Double, _ fan: Double) {
            self.i = i; self.x = x; self.y = y; self.a = a; self.len = len; self.fan = fan
        }
    }

    init(_ nodes: NestNodes) {
        self.nodes = nodes
        n = nodes.size
        let z = Array(repeating: 0.0, count: n), zk = Array(repeating: 0.0, count: n * TreeShape.k)
        bx = z; by = z; angle = z; length = z; width = z
        px = zk; py = zk; nx = zk; ny = zk
        bcx = z; bcy = z; brad = z; u0 = z; u1 = z; v0 = z; v1 = z
        if n == 0 { return }
        place(TreeShape.rootAngle, 0, 0, 0, 1, TreeShape.rootFan)
        bounds()
    }

    // the concept's recursive place(), as a stack of half-done fans: a node's
    // own draws, then each child's whole subtree before the next child's
    private func place(_ a: Double, _ i: Int, _ x: Double, _ y: Double, _ len: Double, _ fan: Double) {
        var work: [WorkItem] = [WorkItem(i, x, y, a, len, fan)]
        while let top = work.last {
            if !top.started { start(top); continue }
            if top.next >= top.arr.count { work.removeLast(); continue }
            work.append(childOf(top))
        }
    }

    private func start(_ w: WorkItem) {
        w.started = true
        let i = w.i, x = w.x, y = w.y, a = w.a, len = w.len
        bx[i] = x; by[i] = y; angle[i] = a; length[i] = len; width[i] = len * (i == 0 ? TreeShape.rootWidth : TreeShape.width)
        let dx = cos(a), dy = sin(a), qx = -dy, qy = dx, o = i * k
        let sign: Double = rnd.next() < 0.5 ? -1 : 1
        _ = rnd.next()   // the concept draws a phase here for its other looks
        for j in 0..<k {
            let u = Double(j) / Double(k - 1)
            let off = sign * TreeShape.bend * len * 4 * u * (1 - u)
            px[o + j] = x + dx * len * u + qx * off; py[o + j] = y + dy * len * u + qy * off
        }
        for j in 0..<k {
            let j0 = j > 0 ? j - 1 : 0, j1 = j < k - 1 ? j + 1 : k - 1
            let tx = px[o + j1] - px[o + j0], ty = py[o + j1] - py[o + j0]
            var m = hypot(tx, ty); if m == 0 { m = 1 }
            nx[o + j] = -ty / m; ny[o + j] = tx / m
        }
        let count = nodes.childCount[i]
        if count == 0 { return }
        // the heaviest straight on, the rest out to each side by turns (ties keep their order)
        let wp = nodes.weightPow
        let kids = (0..<count).map { nodes.child(i, $0) }
        let sorted = kids.indices.sorted { p, q in
            wp[kids[p]] != wp[kids[q]] ? wp[kids[p]] > wp[kids[q]] : p < q
        }.map { kids[$0] }
        var left: [Int] = [], right: [Int] = []
        for q in 1..<count { if q % 2 == 1 { left.append(sorted[q]) } else { right.append(sorted[q]) } }
        left.reverse()
        let arr = left + [sorted[0]] + right
        var tot = 0.0, wmax = 0.0
        for c in arr { tot += wp[c]; if wp[c] > wmax { wmax = wp[c] } }
        w.arr = arr; w.tot = tot; w.wmax = wmax
    }

    private func childOf(_ w: WorkItem) -> WorkItem {
        let wp = nodes.weightPow
        let count = w.arr.count
        let c = w.arr[w.next]; w.next += 1
        let fan = w.fan, len = w.len, a = w.a, o = w.i * k
        var o2 = -fan + 2 * fan * (w.cum + wp[c] / 2) / w.tot
        w.cum += wp[c]
        if count == 1 { o2 = (rnd.next() < 0.5 ? -1.0 : 1.0) * fan * 0.45 }
        let rel = min(1.0, abs(o2) / fan)
        let t = clamp(1 - TreeShape.drop * pow(rel, 0.85) + (rnd.next() - 0.5) * 0.05, 1 - TreeShape.drop, 1)
        let f = t * Double(k - 1)
        let jj = min(k - 2, Int(floor(f)))
        let fr = f - Double(jj)
        let ax = px[o + jj] * (1 - fr) + px[o + jj + 1] * fr, ay = py[o + jj] * (1 - fr) + py[o + jj + 1] * fr
        let ca = a + o2 * TreeShape.spread + (rnd.next() - 0.5) * 0.08
        let cl = len * TreeShape.ratio * (0.5 + 0.5 * sqrt(wp[c] / w.wmax)) * (1 - TreeShape.shorten * rel)
        let cs = clamp(2 * fan * wp[c] / w.tot * 0.9 + 0.3, TreeShape.fanMin, TreeShape.fanMax)
        return WorkItem(c, ax, ay, ca, cl, cs)
    }

    /// A leaf's tip, beyond its twig's last point.
    func leafTip(_ d: Int) -> (Double, Double) {
        let o = d * k + k - 1
        let a = atan2(py[o] - py[o - 1], px[o] - px[o - 1]), ll = length[d] * TreeShape.leaf
        return (px[o] + cos(a) * ll, py[o] + sin(a) * ll)
    }

    private func bounds() {
        var mnx = Array(repeating: Double.infinity, count: n), mny = mnx
        var mxx = Array(repeating: -Double.infinity, count: n), mxy = mxx
        let cc = nodes.childCount
        for i in 0..<n {
            let w = width[i]
            for j in 0..<k {
                let x = px[i * k + j], y = py[i * k + j]
                mnx[i] = min(mnx[i], x - w); mxx[i] = max(mxx[i], x + w); mny[i] = min(mny[i], y - w); mxy[i] = max(mxy[i], y + w)
            }
            if cc[i] == 0 {
                let tip = leafTip(i)
                let e = length[i] * TreeShape.leaf * 0.3
                mnx[i] = min(mnx[i], tip.0 - e); mxx[i] = max(mxx[i], tip.0 + e); mny[i] = min(mny[i], tip.1 - e); mxy[i] = max(mxy[i], tip.1 + e)
            }
        }
        for i in stride(from: n - 1, through: 1, by: -1) {
            let p = nodes.parent[i]
            mnx[p] = min(mnx[p], mnx[i]); mny[p] = min(mny[p], mny[i]); mxx[p] = max(mxx[p], mxx[i]); mxy[p] = max(mxy[p], mxy[i])
        }
        for i in 0..<n {
            bcx[i] = (mnx[i] + mxx[i]) / 2; bcy[i] = (mny[i] + mxy[i]) / 2; brad[i] = hypot(mxx[i] - mnx[i], mxy[i] - mny[i]) / 2
        }
        for i in 0..<n {
            let a = angle[i], ca = cos(a), sa = sin(a), x0 = bx[i], y0 = by[i]
            var a0 = Double.infinity, a1 = -Double.infinity, b0 = Double.infinity, b1 = -Double.infinity
            for q in nodes.tin[i]..<nodes.tout[i] {
                let d = nodes.order[q], wd = width[d]
                for j in 0...k {
                    var x: Double, y: Double, m2 = wd
                    if j < k { x = px[d * k + j]; y = py[d * k + j] } else {
                        if cc[d] != 0 { continue }
                        let tip = leafTip(d); x = tip.0; y = tip.1; m2 = length[d] * TreeShape.leaf * 0.3
                    }
                    let dx = x - x0, dy = y - y0, uu = dx * ca + dy * sa, vv = -dx * sa + dy * ca
                    if uu - m2 < a0 { a0 = uu - m2 }
                    if uu + m2 > a1 { a1 = uu + m2 }
                    if vv - m2 < b0 { b0 = vv - m2 }
                    if vv + m2 > b1 { b1 = vv + m2 }
                }
            }
            u0[i] = a0; u1[i] = a1; v0[i] = b0; v1[i] = b1
        }
    }

    private func clamp(_ x: Double, _ a: Double, _ b: Double) -> Double { x < a ? a : (x > b ? b : x) }
}
