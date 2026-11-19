package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Tree in 3D, grown as a tree (S11, the owner's review of the third pass, 19 Nov: "the tree
// doesn't look realistic neither the leaves . also it's too cramped"). The trunk goes up the y axis.
// - Thickness follows the pipe model (Shinozaki 1964, Leonardo's rule): a branch's area is the sum of
//   the leaves it carries, r0 = ROOT_R * sqrt(leaves / all leaves), never over JOIN of where it
//   joins; it tapers to its biggest child's (at most TIP of its base).
// - Length follows its share of the tree: LEN1 * (leaves / all leaves)^LEN_POW, never over RATIO of
//   its parent's nor under TWIG leaves.
// - Children leave their parent's line round it by the golden angle from a seeded start (3D
//   phyllotaxis, Vogel 1979), the heaviest the most upright: the trunk's branches RISE0 to RISE1 off
//   upright along its top DROP1, a deeper branch SPLAY0 to SPLAY1 off its parent's line along its last
//   TreeShape.DROP (SPREAD more a child after the first, at most DROP_MOST), then lifted
//   (LIFT); a child carrying most of its parent leans less (LEAD of its share squared), so the tree
//   keeps a leader. Two sibling branches are never nearer than GAP: one too near a branch placed
//   before it turns on round its parent, a TURN at a time.
// - An idea is a short stem (STEM of its leaf) at its parent's end (its last SPRAY), SPRAY0 to SPRAY1
//   off the parent's line, its leaf past the stem's tip: a parent's ideas make one spray. A leaf is
//   LEAF_K / sqrt(all leaves) long each side, at most LEAF_MOST, varied by LEAF_VAR.
// - A branch bends: its middle is pulled down and out (BEND of its length), so it leaves its parent
//   flatter and turns up toward its tip, and swayed level to one side (up to SWAY), seeded. Its line is a
//   quadratic curve from base to tip through base + dir * len / 2 + bend; a child joins on the curve
//   and leaves it from the curve's own direction there.
// tools/tree_fixture.mjs keeps the same rules (layout3d), and tree-fixture.json's expect3d holds this
// and iOS's TreeLayout3D.swift to them.

/** The 3D layout's own constants; the seed and a deeper branch's drop are [TreeShape]'s. */
object TreeShape3D {
    /** The golden angle, the turn between one child and the next round their parent. */
    val GOLDEN = Math.PI * (3 - sqrt(5.0))
    private const val DEG = Math.PI / 180
    /** The trunk's base radius (the trunk is 1 long); a branch's is this by the root of its share of the leaves. */
    const val ROOT_R = 0.075
    /** A branch's tip radius at most this much of its base's. */
    const val TIP = 0.85
    /** A child's base radius at most this much of its parent's where it joins. */
    const val JOIN = 0.95
    /** A branch's length: LEN1 by its share of the leaves to LEN_POW, at most RATIO of its parent's. */
    const val LEN1 = 0.95
    const val LEN_POW = 0.45
    const val RATIO = 0.8
    /** The trunk's branches leave it this far off upright, the heaviest the least. */
    const val RISE0 = 40 * DEG
    const val RISE1 = 70 * DEG
    /** A deeper branch leaves its parent's line this far, before its lift. */
    const val SPLAY0 = 30 * DEG
    const val SPLAY1 = 60 * DEG
    /** An idea's stem leaves its parent's line this far, before its lift. */
    const val SPRAY0 = 40 * DEG
    const val SPRAY1 = 75 * DEG
    /** A child carrying most of its parent leans this much less, by its share squared. */
    const val LEAD = 0.6
    /** How far each deeper child is lifted toward the sky before its direction is made whole. */
    const val LIFT = 0.2
    /** How far down the trunk its branches start, down a branch its ideas, and down a deeper branch its
     *  branches: TreeShape.DROP and SPREAD more a child after the first, at most DROP_MOST. The first
     *  child, and an only child, leaves near the tip (a tenth of the way down that drop's reach). */
    const val DROP1 = 0.3
    const val SPRAY = 0.35
    const val SPREAD = 0.03
    const val DROP_MOST = 0.75
    /** Two sibling branches at least this far apart; one too near turns on round its parent a TURN at a
     *  time, TURNS at most, else takes the turn that came nearest. A branch after the first leans at least 1.5 GAP. */
    const val GAP = 18 * DEG
    const val TURN = 10 * DEG
    const val TURNS = 36
    /** A branch's middle pulled down and out, and to one side at most, beside its length. */
    const val BEND = 0.12
    const val SWAY = 0.08
    /** An idea's stem, and a branch's least length, beside a leaf. */
    const val STEM = 0.6
    const val TWIG = 8.0
    /** A leaf's half length: LEAF_K by one over the root of all the leaves, at most LEAF_MOST, varied by LEAF_VAR. */
    const val LEAF_K = 0.75
    const val LEAF_MOST = 0.09
    const val LEAF_VAR = 0.3
    /** A branch's body (its name's and its tap's place) this far along it. */
    const val ANCHOR = 0.6
}

/**
 * Where every branch of [nodes] grows in 3D, in the tree's own units (the trunk is 1 long, from the
 * origin, up y): each node's [base] and unit direction [dir] (x, y, z by node: the line from base to
 * tip), [length], radius at its base ([r0]) and tip ([r1]), and [bend] (its curve's middle beside the
 * line's); its body ([anchor]: a leaf's centre, else [TreeShape3D.ANCHOR] along the curve) and a
 * leaf's radius ([leaf], 0 for a branch); and its subtree's ball ([centre], [reach]), what a fly-in
 * frames. [order] is breadth first, the trunk then each level: a detail cap keeps the big branches.
 */
class TreeLayout3D(val nodes: NestNodes) {
    private val n = nodes.size
    val base = DoubleArray(3 * n); val dir = DoubleArray(3 * n); val length = DoubleArray(n)
    val r0 = DoubleArray(n); val r1 = DoubleArray(n); val bend = DoubleArray(3 * n)
    val anchor = DoubleArray(3 * n); val leaf = DoubleArray(n)
    val centre = DoubleArray(3 * n); val reach = DoubleArray(n)
    val order = IntArray(n)

    private val rnd = NestRandom(TreeShape.SEED)
    private val leafSize = if (n > 0) min(TreeShape3D.LEAF_MOST, TreeShape3D.LEAF_K / sqrt(nodes.weight[0])) else 0.0

    init {
        if (n > 0) {
            place(0, doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY, 1.0)
            bounds()
            breadthFirst()
        }
    }

    /** Node [i]'s point [t] along its curve, into [out] at [o]. */
    fun curveAt(i: Int, t: Double, out: DoubleArray, o: Int = 0) {
        val u = 1 - t; val len = length[i]
        for (a in 0..2) {
            val b = base[3 * i + a]; val d = dir[3 * i + a]
            val c = b + d * len / 2 + bend[3 * i + a]; val e = b + d * len
            out[o + a] = u * u * b + 2 * u * t * c + t * t * e
        }
    }

    /** Node [i]'s curve's direction at [t] (unit), into [out]. */
    fun tangentAt(i: Int, t: Double, out: DoubleArray) {
        val len = length[i]
        for (a in 0..2) {
            val b = base[3 * i + a]; val d = dir[3 * i + a]
            val c = b + d * len / 2 + bend[3 * i + a]; val e = b + d * len
            out[a] = 2 * (1 - t) * (c - b) + 2 * t * (e - c)
        }
        val m = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2])
        for (a in 0..2) out[a] /= m
    }

    private fun pipe(i: Int) = TreeShape3D.ROOT_R * sqrt(nodes.weight[i] / nodes.weight[0])

    /** Node [i] and its subtree; [scale] is how much a parent held under its pipe radius passes to its children. */
    private fun place(i: Int, b: DoubleArray, d0: DoubleArray, u0: DoubleArray, len: Double, rmax: Double, scale: Double) {
        val s3 = TreeShape3D
        for (a in 0..2) { base[3 * i + a] = b[a]; dir[3 * i + a] = d0[a] }
        length[i] = len
        val radius = min(pipe(i) * scale, rmax); val count = nodes.childCount[i]; val k0 = radius / pipe(i)
        r0[i] = radius
        val v = doubleArrayOf(d0[1] * u0[2] - d0[2] * u0[1], d0[2] * u0[0] - d0[0] * u0[2], d0[0] * u0[1] - d0[1] * u0[0])
        val phi0 = rnd.next() * 2 * Math.PI
        if (count == 0 && i > 0) {
            r1[i] = radius * s3.TIP
            val lr = min(leafSize * (1 - s3.LEAF_VAR / 2 + s3.LEAF_VAR * rnd.next()), s3.LEAF_MOST)
            leaf[i] = lr
            for (a in 0..2) anchor[3 * i + a] = b[a] + d0[a] * (len + lr)
            return
        }
        // the bend: down and out from the line's middle (none for an upright line), then a sway to one side
        val pu = doubleArrayOf(-d0[0] * d0[1], 1 - d0[1] * d0[1], -d0[2] * d0[1])
        val pm = sqrt(pu[0] * pu[0] + pu[1] * pu[1] + pu[2] * pu[2])
        val sway = (2 * rnd.next() - 1) * s3.SWAY * len
        // the sway level, square to the line (its across where the line is upright)
        val side = if (pm > 1e-6) doubleArrayOf(-d0[2] / pm, 0.0, d0[0] / pm) else v
        for (a in 0..2) bend[3 * i + a] = (if (pm > 1e-6) -pu[a] / pm * s3.BEND * len else 0.0) + side[a] * sway
        curveAt(i, s3.ANCHOR, anchor, 3 * i)
        if (count == 0) { r1[i] = radius * s3.TIP; return }
        // heaviest first; ties keep their order (a stable sort), as the concept's
        val wp = nodes.weightPow
        val kids = (0 until count).map { nodes.child(i, it) }.sortedByDescending { wp[it] }
        var most = 0.0
        for (c in kids) most = max(most, pipe(c))
        r1[i] = min(most * k0, radius * s3.TIP)
        val trunk = i == 0
        val placed = ArrayList<DoubleArray>()
        val at = DoubleArray(3); val tg = DoubleArray(3)
        for (q in 0 until count) {
            val c = kids[q]; val idea = nodes.childCount[c] == 0
            val rel = if (count == 1) 0.1 else if (q == 0) 0.1 else 0.45 + 0.55 * q / (count - 1)
            val s = if (count == 1) 0.5 else q.toDouble() / (count - 1)
            val drop = if (trunk) s3.DROP1 else if (idea) s3.SPRAY else min(TreeShape.DROP + s3.SPREAD * (count - 1), s3.DROP_MOST)
            val t = clamp(1 - drop * rel.pow(0.85) + (rnd.next() - 0.5) * 0.05, 1 - drop, 1.0)
            curveAt(i, t, at)
            tangentAt(i, t, tg)
            // the parent's frame at t: its across turned square to the curve there
            var ud = u0[0] * tg[0] + u0[1] * tg[1] + u0[2] * tg[2]
            val ut = doubleArrayOf(u0[0] - tg[0] * ud, u0[1] - tg[1] * ud, u0[2] - tg[2] * ud)
            var um = sqrt(ut[0] * ut[0] + ut[1] * ut[1] + ut[2] * ut[2])
            for (a in 0..2) ut[a] /= um
            val vt = doubleArrayOf(tg[1] * ut[2] - tg[2] * ut[1], tg[2] * ut[0] - tg[0] * ut[2], tg[0] * ut[1] - tg[1] * ut[0])
            val share = nodes.weight[c] / nodes.weight[i]
            val lean = (if (trunk) s3.RISE0 + (s3.RISE1 - s3.RISE0) * s else s3.SPLAY0 + (s3.SPLAY1 - s3.SPLAY0) * s) * (1 - s3.LEAD * share * share)
            // a branch after the first leans at least 1.5 GAP off its parent's line, so turning round can clear the first
            val th = if (idea) s3.SPRAY0 + (s3.SPRAY1 - s3.SPRAY0) * s else if (q > 0) max(lean, 1.5 * s3.GAP) else lean
            val ct = cos(th); val st = sin(th)
            val d = DoubleArray(3)
            fun aim(f: Double) {
                val cp = cos(f); val sp = sin(f)
                for (a in 0..2) d[a] = tg[a] * ct + (ut[a] * cp + vt[a] * sp) * st
                if (!trunk) d[1] += s3.LIFT
                val dm = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                for (a in 0..2) d[a] /= dm
            }
            var phi = phi0 + q * s3.GOLDEN
            if (!idea) {
                // turned on round the parent until clear of every sibling branch, else the turn that came nearest
                var best = phi; var bestNear = -1.0
                for (k in 0 until s3.TURNS) {
                    val f = phi + k * s3.TURN; aim(f)
                    var near = Math.PI
                    for (p in placed) near = min(near, acos(clamp(p[0] * d[0] + p[1] * d[1] + p[2] * d[2], -1.0, 1.0)))
                    if (near > bestNear) { bestNear = near; best = f }
                    if (near >= s3.GAP) break
                }
                phi = best
            }
            aim(phi)
            if (!idea) placed.add(d.copyOf())
            val cl = if (idea) leafSize * s3.STEM else max(min(s3.LEN1 * (nodes.weight[c] / nodes.weight[0]).pow(s3.LEN_POW), len * s3.RATIO), leafSize * s3.TWIG)
            ud = ut[0] * d[0] + ut[1] * d[1] + ut[2] * d[2]
            var u = doubleArrayOf(ut[0] - d[0] * ud, ut[1] - d[1] * ud, ut[2] - d[2] * ud)
            um = sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2])
            if (um < 1e-6) {
                ud = vt[0] * d[0] + vt[1] * d[1] + vt[2] * d[2]
                u = doubleArrayOf(vt[0] - d[0] * ud, vt[1] - d[1] * ud, vt[2] - d[2] * ud)
                um = sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2])
            }
            for (a in 0..2) u[a] /= um
            place(c, at.copyOf(), d.copyOf(), u, cl, (radius + (r1[i] - radius) * t) * s3.JOIN, k0)
        }
    }

    private fun bounds() {
        val lo = DoubleArray(3 * n); val hi = DoubleArray(3 * n)
        for (i in 0 until n) for (a in 0..2) {
            val b0 = base[3 * i + a]; val b1 = b0 + dir[3 * i + a] * length[i]; val bc = (b0 + b1) / 2 + bend[3 * i + a]; val r = r0[i]
            lo[3 * i + a] = min(min(b0, b1), bc) - r; hi[3 * i + a] = max(max(b0, b1), bc) + r
            if (leaf[i] > 0) {
                val c = anchor[3 * i + a]
                lo[3 * i + a] = min(lo[3 * i + a], c - leaf[i]); hi[3 * i + a] = max(hi[3 * i + a], c + leaf[i])
            }
        }
        // a parent always comes before its children, so one pass from the end gathers each subtree
        for (i in n - 1 downTo 1) {
            val p = nodes.parent[i]
            for (a in 0..2) { lo[3 * p + a] = min(lo[3 * p + a], lo[3 * i + a]); hi[3 * p + a] = max(hi[3 * p + a], hi[3 * i + a]) }
        }
        for (i in 0 until n) {
            for (a in 0..2) centre[3 * i + a] = (lo[3 * i + a] + hi[3 * i + a]) / 2
            val x = hi[3 * i] - lo[3 * i]; val y = hi[3 * i + 1] - lo[3 * i + 1]; val z = hi[3 * i + 2] - lo[3 * i + 2]
            reach[i] = sqrt(x * x + y * y + z * z) / 2
        }
    }

    private fun breadthFirst() {
        var head = 0; var tail = 0
        order[tail++] = 0
        while (head < tail) {
            val i = order[head++]
            for (k in 0 until nodes.childCount[i]) order[tail++] = nodes.child(i, k)
        }
    }

    private fun clamp(x: Double, a: Double, b: Double) = if (x < a) a else if (x > b) b else x
}
