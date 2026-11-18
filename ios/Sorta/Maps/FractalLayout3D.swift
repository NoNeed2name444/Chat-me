import Foundation

// The Fractal look in 3D (S12, the owner's rule of 18 Nov: the flat Fractal
// never ships). The flat layout's rules (FractalLayout: seed 91, the spacing
// a child's cluster gets, its distance, the dot sizes) grown into space: All
// ideas sits at the middle with its parts on a whole sphere round it; every
// other hub's parts lie on a cap that opens away from its own hub, wider the
// more it holds, so each cluster is a smaller copy of the one it sits in. The
// parts go down the cap heaviest first, each a golden angle round from the
// one before, from a seeded start. The spacing and the dots shrink with each
// level, as the flat layout's do.
//
// tools/fractal_fixture3d.mjs keeps the same rules in JavaScript and writes
// tools/fractal_fixture3d.json, which this file (FractalLayout3DTests.swift)
// and Android's FractalLayout3D.kt (FractalLayout3DTest) must give back.
// Foundation only, so it is tested on Linux.

/// The 3D layout's own numbers; the rest are FractalRules'.
nonisolated enum Fractal3DRules {
    /// The turn between one part and the next round their hub.
    static let golden = Double.pi * (3 - 5.0.squareRoot())
    /// A cap's half angle: capLeast plus capEach a part, at most capMost.
    static let capLeast = 0.65
    static let capEach = 0.275
    static let capMost = Double.pi - 0.65
    /// How far a part's turn may stray, as a share of its room.
    static let stray = 0.35
    /// A hub's glow reaches this many of its dot's radii: what its cluster's bounds hold.
    static let glow = 3.0
}

/// Where each node of `tree` is in space, in the map's units (All ideas'
/// spacing is 1): its place (`pos`, x y z by node), its cluster's `spacing`,
/// its dot's radius (`dot`), the way its cluster opens (`out`, a unit
/// vector) and its whole cluster's ball (`centre`, `reach`), which a fly-in frames.
nonisolated final class FractalLayout3D {
    let tree: FractalTree
    private(set) var pos: [Double]
    private(set) var spacing: [Double]
    private(set) var dot: [Double]
    private(set) var out: [Double]
    private(set) var centre: [Double]
    private(set) var reach: [Double]
    private var across: [Double]
    private var phi0: [Double]

    init(_ tree: FractalTree) {
        self.tree = tree
        let n = tree.count
        pos = [Double](repeating: 0, count: 3 * n)
        spacing = [Double](repeating: 0, count: n)
        dot = [Double](repeating: 0, count: n)
        out = [Double](repeating: 0, count: 3 * n)
        centre = [Double](repeating: 0, count: 3 * n)
        reach = [Double](repeating: 0, count: n)
        across = [Double](repeating: 0, count: 3 * n)
        phi0 = [Double](repeating: 0, count: n)
        place()
        bounds()
    }

    private func place() {
        let t = tree
        let n = t.count
        if n == 0 { return }
        var random = FractalRules.Random(FractalRules.seed)
        var stackNode = [Int](repeating: 0, count: n), stackNext = [Int](repeating: 0, count: n)
        var top = -1
        func enter(_ i: Int, _ x: Double, _ y: Double, _ z: Double, _ dx: Double, _ dy: Double, _ dz: Double,
                   _ ux: Double, _ uy: Double, _ uz: Double, _ sp: Double, _ psp: Double) {
            pos[3 * i] = x; pos[3 * i + 1] = y; pos[3 * i + 2] = z
            out[3 * i] = dx; out[3 * i + 1] = dy; out[3 * i + 2] = dz
            across[3 * i] = ux; across[3 * i + 1] = uy; across[3 * i + 2] = uz
            spacing[i] = sp
            if t.childCount[i] == 0 { return }
            dot[i] = max(sp * FractalRules.hubDot, psp * FractalRules.hubDotParent)
            phi0[i] = random.next() * 2 * Double.pi
            top += 1
            stackNode[top] = i; stackNext[top] = 0
        }
        enter(0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1)
        while top >= 0 {
            let i = stackNode[top], k = stackNext[top], count = t.childCount[i]
            if k >= count { top -= 1; continue }
            stackNext[top] = k + 1
            let sp = spacing[i]
            let whole = i == 0
            let half = whole ? Double.pi : min(Fractal3DRules.capMost, Fractal3DRules.capLeast + Double(count) * Fractal3DRules.capEach)
            let cap = 1 - cos(half)
            // each part's room: the cap's share, as the angle across a round patch that size
            let room = min(Double.pi, 2 * (2 * cap / Double(count)).squareRoot())
            let c = t.children[t.childStart[i] + k]
            let wmax = t.weight[t.children[t.childStart[i]]]
            let cosT = whole && count == 1 ? 0.0 : 1 - cap * (Double(k) + 0.5) / Double(count)
            let sinT = max(0.0, 1 - cosT * cosT).squareRoot()
            let phi = phi0[i] + Double(k) * Fractal3DRules.golden + (random.next() - 0.5) * room * Fractal3DRules.stray
            let dist = count > 8 ? sp * ((k % 2 == 1 ? 0.64 : 0.98) + random.next() * 0.1) : sp * (0.74 + random.next() * 0.28)
            let csp = sp * FractalRules.clamp(0.9 * sin(room / 2), 0.2, 0.42) * (0.62 + 0.38 * (t.weight[c] / wmax).squareRoot())
            if t.childCount[c] == 0 { dot[c] = sp * FractalRules.leafDot }
            // the hub's frame: out (d), across (u) and v = d x u
            let dx = out[3 * i], dy = out[3 * i + 1], dz = out[3 * i + 2]
            let ux = across[3 * i], uy = across[3 * i + 1], uz = across[3 * i + 2]
            let vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux
            let cp = cos(phi), sn = sin(phi)
            let ex = dx * cosT + (ux * cp + vx * sn) * sinT
            let ey = dy * cosT + (uy * cp + vy * sn) * sinT
            let ez = dz * cosT + (uz * cp + vz * sn) * sinT
            // the part's across: the hub's, made square to the part's way out (or v's, where u lies along it)
            var along = ux * ex + uy * ey + uz * ez
            var ax = ux - ex * along, ay = uy - ey * along, az = uz - ez * along
            var m = (ax * ax + ay * ay + az * az).squareRoot()
            if m < 1e-6 {
                along = vx * ex + vy * ey + vz * ez
                ax = vx - ex * along; ay = vy - ey * along; az = vz - ez * along
                m = (ax * ax + ay * ay + az * az).squareRoot()
            }
            enter(c, pos[3 * i] + ex * dist, pos[3 * i + 1] + ey * dist, pos[3 * i + 2] + ez * dist,
                  ex, ey, ez, ax / m, ay / m, az / m, csp, sp)
        }
    }

    /// How far a node's own mark reaches round it: a hub's glow, an idea's dot.
    func mark(_ i: Int) -> Double { tree.childCount[i] > 0 ? dot[i] * Fractal3DRules.glow : dot[i] }

    private func bounds() {
        let n = tree.count
        var lo = [Double](repeating: 0, count: 3 * n), hi = [Double](repeating: 0, count: 3 * n)
        for i in 0..<n {
            let r = mark(i)
            for a in 0..<3 { lo[3 * i + a] = pos[3 * i + a] - r; hi[3 * i + a] = pos[3 * i + a] + r }
        }
        // a parent always comes before its parts, so one pass from the end gathers each cluster
        var i = n - 1
        while i >= 1 {
            let p = tree.parent[i]
            for a in 0..<3 { lo[3 * p + a] = min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = max(hi[3 * p + a], hi[3 * i + a]) }
            i -= 1
        }
        for k in 0..<(3 * n) { centre[k] = (lo[k] + hi[k]) / 2 }
        // each mark's farthest reach from the middle of every cluster it is in
        for j in 0..<n {
            var a = j
            let r = mark(j)
            while a >= 0 {
                let x = pos[3 * j] - centre[3 * a], y = pos[3 * j + 1] - centre[3 * a + 1], z = pos[3 * j + 2] - centre[3 * a + 2]
                let e = (x * x + y * y + z * z).squareRoot() + r
                if e > reach[a] { reach[a] = e }
                a = tree.parent[a]
            }
        }
    }
}
