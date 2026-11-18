package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Tree in 3D (S11, the owner's decision of 18 Nov): the flat layout's weights, lengths, fans and
// drop (TreeLayout), grown round the trunk instead of across the page. The trunk goes up the y axis;
// each branch's children leave it spread round it by the golden angle from a seeded start, the
// heaviest the most upright and the lighter ones further out and lower down; a crown about as wide as
// it is tall (the trunk's branches 35 to 60 degrees off upright, a deeper one 20 to 50 off its
// parent's line, lifted a little toward the sky). A branch tapers to TAPER of its base radius and a child is never thicker than JOIN of where
// it joins. A leaf is a shape past its twig's tip. tools/tree_fixture.mjs keeps the same rules
// (layout3d), and tree-fixture.json's expect3d holds this and iOS's TreeLayout3D.swift to them.

/** The 3D layout's own constants; the rest are [TreeShape]'s. */
object TreeShape3D {
    /** The golden angle, the turn between one child and the next round their parent. */
    val GOLDEN = Math.PI * (3 - sqrt(5.0))
    /** How far each child is lifted toward the sky before its direction is made whole. */
    const val LIFT = 0.1
    /** The trunk's branches leave it this far off upright, the heaviest the least. */
    const val RISE0 = 35 * Math.PI / 180
    const val RISE1 = 60 * Math.PI / 180
    /** A deeper branch leaves its parent's line this far, before its lift. */
    const val SPLAY0 = 20 * Math.PI / 180
    const val SPLAY1 = 50 * Math.PI / 180
    /** A branch's length beside its parent's: the trunk's branches, then deeper ones. */
    const val RATIO1 = 0.85
    const val RATIO2 = 0.72
    /** How far down the trunk its branches start, as a share of it. */
    const val DROP1 = 0.55
    /** Thickness, across beside length: the trunk's and a branch's. */
    const val ROOT_WIDTH = 0.15
    const val WIDTH = 0.1
    /** A leaf's length beside its twig's. */
    const val LEAF = 0.8
    /** A branch's tip radius beside its base's. */
    const val TAPER = 0.6
    /** A child's base radius at most this much of its parent's where it joins. */
    const val JOIN = 0.85
    /** A branch's body (its name's and its tap's place) this far along it. */
    const val ANCHOR = 0.6
}

/**
 * Where every branch of [nodes] grows in 3D, in the tree's own units (the trunk is 1 long, from the
 * origin, up y): each node's [base] and unit direction [dir] (x, y, z by node), [length], radius at
 * its base ([r0]) and tip ([r1]); its body ([anchor]: a leaf's centre, else [TreeShape3D.ANCHOR] along
 * it) and a leaf's radius ([leaf], 0 for a branch); and its subtree's ball ([centre], [reach]), what a
 * fly-in frames. [order] is breadth first, the trunk then each level: a detail cap keeps the big
 * branches.
 */
class TreeLayout3D(val nodes: NestNodes) {
    private val n = nodes.size
    val base = DoubleArray(3 * n); val dir = DoubleArray(3 * n); val length = DoubleArray(n)
    val r0 = DoubleArray(n); val r1 = DoubleArray(n)
    val anchor = DoubleArray(3 * n); val leaf = DoubleArray(n)
    val centre = DoubleArray(3 * n); val reach = DoubleArray(n)
    val order = IntArray(n)

    private val rnd = NestRandom(TreeShape.SEED)

    init {
        if (n > 0) {
            place()
            bounds()
            breadthFirst()
        }
    }

    /** A node half placed: its own draws done, its children placed one by one, each whole subtree before the next. */
    private class Work(val i: Int, val len: Double, val fan: Double) {
        val d = DoubleArray(3); val u = DoubleArray(3); val v = DoubleArray(3)
        var kids = IntArray(0); var next = 0; var tot = 0.0; var wmax = 0.0; var phi0 = 0.0
    }

    private fun place() {
        val work = ArrayList<Work>()
        work.add(start(0, 0.0, 0.0, 0.0, doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), 1.0, TreeShape.ROOT_FAN, Double.POSITIVE_INFINITY))
        while (work.isNotEmpty()) {
            val top = work[work.size - 1]
            if (top.next >= top.kids.size) { work.removeAt(work.size - 1); continue }
            work.add(child(top))
        }
    }

    /** The concept's place() up to its loop: the node's own numbers, its draw and its children's order. */
    private fun start(i: Int, bx: Double, by: Double, bz: Double, d: DoubleArray, u: DoubleArray, len: Double, fan: Double, rmax: Double): Work {
        val w = Work(i, len, fan)
        base[3 * i] = bx; base[3 * i + 1] = by; base[3 * i + 2] = bz
        for (a in 0..2) { dir[3 * i + a] = d[a]; w.d[a] = d[a]; w.u[a] = u[a] }
        length[i] = len
        val radius = min(len * (if (i == 0) TreeShape3D.ROOT_WIDTH else TreeShape3D.WIDTH) / 2, rmax)
        r0[i] = radius; r1[i] = radius * TreeShape3D.TAPER
        w.v[0] = d[1] * u[2] - d[2] * u[1]; w.v[1] = d[2] * u[0] - d[0] * u[2]; w.v[2] = d[0] * u[1] - d[1] * u[0]
        w.phi0 = rnd.next() * 2 * Math.PI
        val count = nodes.childCount[i]
        if (count == 0 && i > 0) {
            val lr = len * TreeShape3D.LEAF * 0.5
            leaf[i] = lr
            for (a in 0..2) anchor[3 * i + a] = base[3 * i + a] + d[a] * (len + lr)
            return w
        }
        for (a in 0..2) anchor[3 * i + a] = base[3 * i + a] + d[a] * len * TreeShape3D.ANCHOR
        if (count == 0) return w
        // heaviest first; ties keep their order (a stable sort), as the concept's
        val wp = nodes.weightPow
        val kids = Array(count) { nodes.child(i, it) }
        w.kids = kids.withIndex().sortedWith(compareByDescending<IndexedValue<Int>> { wp[it.value] }.thenBy { it.index }).map { it.value }.toIntArray()
        for (c in w.kids) { w.tot += wp[c]; if (wp[c] > w.wmax) w.wmax = wp[c] }
        return w
    }

    /** The next child's base, direction, frame, length, fan and radius cap (the loop's body), its own start to come. */
    private fun child(w: Work): Work {
        val wp = nodes.weightPow
        val count = w.kids.size
        val q = w.next++
        val c = w.kids[q]
        val i = w.i; val len = w.len; val fan = w.fan
        val rel = if (count == 1) 0.45 else if (q == 0) 0.1 else 0.45 + 0.55 * q / (count - 1)
        val s = if (count == 1) 0.5 else q.toDouble() / (count - 1); val trunk = i == 0
        val phi = w.phi0 + q * TreeShape3D.GOLDEN
        val th = if (trunk) TreeShape3D.RISE0 + (TreeShape3D.RISE1 - TreeShape3D.RISE0) * s
            else TreeShape3D.SPLAY0 + (TreeShape3D.SPLAY1 - TreeShape3D.SPLAY0) * s
        val ct = cos(th); val st = sin(th); val cp = cos(phi); val sp = sin(phi)
        val d = DoubleArray(3) { a -> w.d[a] * ct + (w.u[a] * cp + w.v[a] * sp) * st }
        if (!trunk) d[1] += TreeShape3D.LIFT
        val dm = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        for (a in 0..2) d[a] /= dm
        val drop = if (trunk) TreeShape3D.DROP1 else TreeShape.DROP
        val t = clamp(1 - drop * rel.pow(0.85) + (rnd.next() - 0.5) * 0.05, 1 - drop, 1.0)
        val cl = len * (if (trunk) TreeShape3D.RATIO1 else TreeShape3D.RATIO2) * (0.5 + 0.5 * sqrt(wp[c] / w.wmax)) * (1 - TreeShape.SHORTEN * rel)
        val cs = clamp(2 * fan * wp[c] / w.tot * 0.9 + 0.3, TreeShape.FAN_MIN, TreeShape.FAN_MAX)
        // the child's own across: the parent's, turned square to the child
        var ud = w.u[0] * d[0] + w.u[1] * d[1] + w.u[2] * d[2]
        val u = DoubleArray(3) { a -> w.u[a] - d[a] * ud }
        var um = sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2])
        if (um < 1e-6) {
            ud = w.v[0] * d[0] + w.v[1] * d[1] + w.v[2] * d[2]
            for (a in 0..2) u[a] = w.v[a] - d[a] * ud
            um = sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2])
        }
        for (a in 0..2) u[a] /= um
        return start(c, base[3 * i] + w.d[0] * len * t, base[3 * i + 1] + w.d[1] * len * t, base[3 * i + 2] + w.d[2] * len * t,
            d, u, cl, cs, r0[i] * (1 - (1 - TreeShape3D.TAPER) * t) * TreeShape3D.JOIN)
    }

    private fun bounds() {
        val lo = DoubleArray(3 * n); val hi = DoubleArray(3 * n)
        for (i in 0 until n) for (a in 0..2) {
            val b0 = base[3 * i + a]; val b1 = b0 + dir[3 * i + a] * length[i]; val r = r0[i]
            lo[3 * i + a] = min(b0, b1) - r; hi[3 * i + a] = max(b0, b1) + r
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
