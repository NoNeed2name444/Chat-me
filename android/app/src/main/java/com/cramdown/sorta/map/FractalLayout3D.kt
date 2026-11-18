package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The Fractal look in 3D (S12, the owner's rule of 18 Nov: the flat Fractal never ships). The flat
// layout's rules (FractalLayout: seed 91, the spacing a child's cluster gets, its distance, the dot
// sizes) grown into space: All ideas sits at the middle with its parts on a whole sphere round it;
// every other hub's parts lie on a cap that opens away from its own hub, wider the more it holds, so
// each cluster is a smaller copy of the one it sits in. The parts go down the cap heaviest first, each
// a golden angle round from the one before, from a seeded start. The spacing and the dots shrink with
// each level, as the flat layout's do.
//
// tools/fractal_fixture3d.mjs keeps the same rules in JavaScript and writes tools/fractal_fixture3d.json,
// which this file (FractalLayout3DTest) and iOS's FractalLayout3D.swift (FractalLayout3DTests.swift)
// must give back. Pure Kotlin, no Android.

/** The 3D layout's own numbers; the rest are [FractalRules]'. */
object Fractal3DRules {
    /** The turn between one part and the next round their hub. */
    val GOLDEN = PI * (3 - sqrt(5.0))
    /** A cap's half angle: the flat arc's halves, [CAP_LEAST] plus [CAP_EACH] a part, at most [CAP_MOST]. */
    const val CAP_LEAST = 0.65
    const val CAP_EACH = 0.275
    val CAP_MOST = PI - 0.65
    /** How far a part's turn may stray, as a share of its room, as the flat layout's 0.35. */
    const val STRAY = 0.35
    /** A hub's glow reaches this many of its dot's radii: what its cluster's bounds hold. */
    const val GLOW = 3.0
}

/**
 * Where each node of [tree] is in space, in the map's units (All ideas' spacing is 1): its place
 * ([pos], x y z by node), its cluster's [spacing], its dot's radius ([dot]), the way its cluster opens
 * ([out], a unit vector) and its whole cluster's ball ([centre], [reach]), which a fly-in frames.
 */
class FractalLayout3D(val tree: FractalTree) {
    private val n = tree.count
    val pos = DoubleArray(3 * n)
    val spacing = DoubleArray(n)
    val dot = DoubleArray(n)
    val out = DoubleArray(3 * n)
    val centre = DoubleArray(3 * n)
    val reach = DoubleArray(n)

    /** Each node's across (square to [out]) and the first turn of its parts, while they are placed. */
    private val across = DoubleArray(3 * n)
    private val phi0 = DoubleArray(n)

    init {
        place()
        bounds()
    }

    private fun place() {
        val t = tree
        val random = FractalRules.Random(FractalRules.SEED)
        val stackNode = IntArray(n); val stackNext = IntArray(n)
        var top = -1
        fun enter(i: Int, x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double,
                  ux: Double, uy: Double, uz: Double, sp: Double, psp: Double) {
            pos[3 * i] = x; pos[3 * i + 1] = y; pos[3 * i + 2] = z
            out[3 * i] = dx; out[3 * i + 1] = dy; out[3 * i + 2] = dz
            across[3 * i] = ux; across[3 * i + 1] = uy; across[3 * i + 2] = uz
            spacing[i] = sp
            if (t.childCount[i] == 0) return
            dot[i] = max(sp * FractalRules.HUB_DOT, psp * FractalRules.HUB_DOT_PARENT)
            phi0[i] = random.next() * 2 * PI
            top++
            stackNode[top] = i; stackNext[top] = 0
        }
        enter(0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        while (top >= 0) {
            val i = stackNode[top]; val k = stackNext[top]; val count = t.childCount[i]
            if (k >= count) { top--; continue }
            stackNext[top] = k + 1
            val sp = spacing[i]
            val whole = i == 0
            val half = if (whole) PI else min(Fractal3DRules.CAP_MOST, Fractal3DRules.CAP_LEAST + count * Fractal3DRules.CAP_EACH)
            val cap = 1 - cos(half)
            // each part's room: the cap's share, as the angle across a round patch that size
            val room = min(PI, 2 * sqrt(2 * cap / count))
            val c = t.children[t.childStart[i] + k]
            val wmax = t.weight[t.children[t.childStart[i]]]
            val cosT = if (whole && count == 1) 0.0 else 1 - cap * (k + 0.5) / count
            val sinT = sqrt(max(0.0, 1 - cosT * cosT))
            val phi = phi0[i] + k * Fractal3DRules.GOLDEN + (random.next() - 0.5) * room * Fractal3DRules.STRAY
            val dist = if (count > 8) sp * ((if (k % 2 == 1) 0.64 else 0.98) + random.next() * 0.1) else sp * (0.74 + random.next() * 0.28)
            val csp = sp * FractalRules.clamp(0.9 * sin(room / 2), 0.2, 0.42) * (0.62 + 0.38 * sqrt(t.weight[c] / wmax))
            if (t.childCount[c] == 0) dot[c] = sp * FractalRules.LEAF_DOT
            // the hub's frame: out (d), across (u) and v = d x u
            val dx = out[3 * i]; val dy = out[3 * i + 1]; val dz = out[3 * i + 2]
            val ux = across[3 * i]; val uy = across[3 * i + 1]; val uz = across[3 * i + 2]
            val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
            val cp = cos(phi); val sn = sin(phi)
            val ex = dx * cosT + (ux * cp + vx * sn) * sinT
            val ey = dy * cosT + (uy * cp + vy * sn) * sinT
            val ez = dz * cosT + (uz * cp + vz * sn) * sinT
            // the part's across: the hub's, made square to the part's way out (or v's, where u lies along it)
            var along = ux * ex + uy * ey + uz * ez
            var ax = ux - ex * along; var ay = uy - ey * along; var az = uz - ez * along
            var m = sqrt(ax * ax + ay * ay + az * az)
            if (m < 1e-6) {
                along = vx * ex + vy * ey + vz * ez
                ax = vx - ex * along; ay = vy - ey * along; az = vz - ez * along
                m = sqrt(ax * ax + ay * ay + az * az)
            }
            enter(c, pos[3 * i] + ex * dist, pos[3 * i + 1] + ey * dist, pos[3 * i + 2] + ez * dist,
                ex, ey, ez, ax / m, ay / m, az / m, csp, sp)
        }
    }

    /** How far a node's own mark reaches round it: a hub's glow, an idea's dot. */
    fun mark(i: Int): Double = if (tree.childCount[i] > 0) dot[i] * Fractal3DRules.GLOW else dot[i]

    private fun bounds() {
        val lo = DoubleArray(3 * n); val hi = DoubleArray(3 * n)
        for (i in 0 until n) for (a in 0..2) { val r = mark(i); lo[3 * i + a] = pos[3 * i + a] - r; hi[3 * i + a] = pos[3 * i + a] + r }
        // a parent always comes before its parts, so one pass from the end gathers each cluster
        for (i in n - 1 downTo 1) {
            val p = tree.parent[i]
            for (a in 0..2) { lo[3 * p + a] = min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = max(hi[3 * p + a], hi[3 * i + a]) }
        }
        for (i in 0 until 3 * n) centre[i] = (lo[i] + hi[i]) / 2
        // each mark's farthest reach from the middle of every cluster it is in
        for (j in 0 until n) {
            var a = j
            val r = mark(j)
            while (a >= 0) {
                val x = pos[3 * j] - centre[3 * a]; val y = pos[3 * j + 1] - centre[3 * a + 1]; val z = pos[3 * j + 2] - centre[3 * a + 2]
                val e = sqrt(x * x + y * y + z * z) + r
                if (e > reach[a]) reach[a] = e
                a = tree.parent[a]
            }
        }
    }
}
