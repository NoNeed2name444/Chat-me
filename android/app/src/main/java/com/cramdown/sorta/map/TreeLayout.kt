package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Tree look's layout (S11): the approved concept's (nested maps, 18 Nov; tools/tree_fixture.mjs
// keeps it word for word). One rule for the whole and for every part: each branch fans its
// children by weight, the heaviest straight on and the lighter ones further out and lower down,
// from a seeded random draw, so the same tree always grows the same way, on both platforms.
// iOS's TreeLayout.swift is the same, line for line; tree-fixture.json holds both to the concept.

/** The layout's constants: the concept's, for the Tree. */
object TreeShape {
    /** Points along each branch. */
    const val K = 9
    /** A leaf's length beside its twig's. */
    const val LEAF = 0.5
    const val SEED = 11
    const val ROOT_ANGLE = -Math.PI / 2
    const val ROOT_FAN = 0.85
    const val FAN_MIN = 0.45
    const val FAN_MAX = 0.85
    const val SPREAD = 1.05
    const val DROP = 0.42
    const val RATIO = 0.7
    const val SHORTEN = 0.22
    const val WIDTH = 0.075
    const val ROOT_WIDTH = 0.12
    const val BEND = 0.06
}

/**
 * Where every branch of [nodes] grows, in the tree's own units (the trunk is 1 long, from 0, 0,
 * upward): each node's base ([bx], [by]), direction ([angle]), [length] and [width]; its K points
 * ([px], [py]) and their normals ([nx], [ny]); its subtree's round bounds ([bcx], [bcy], [brad]),
 * for culling; and its subtree's box along its own axis ([u0] to [u1] along, [v0] to [v1] across),
 * for framing it.
 */
class TreeLayout(val nodes: NestNodes) {
    private val n = nodes.size
    private val k = TreeShape.K
    val bx = DoubleArray(n); val by = DoubleArray(n); val angle = DoubleArray(n); val length = DoubleArray(n); val width = DoubleArray(n)
    val px = DoubleArray(n * k); val py = DoubleArray(n * k); val nx = DoubleArray(n * k); val ny = DoubleArray(n * k)
    val bcx = DoubleArray(n); val bcy = DoubleArray(n); val brad = DoubleArray(n)
    val u0 = DoubleArray(n); val u1 = DoubleArray(n); val v0 = DoubleArray(n); val v1 = DoubleArray(n)

    private val rnd = NestRandom(TreeShape.SEED)

    init {
        place(TreeShape.ROOT_ANGLE, 0, 0.0, 0.0, 1.0, TreeShape.ROOT_FAN)
        bounds()
    }

    private fun place(a: Double, i: Int, x: Double, y: Double, len: Double, fan: Double) {
        // the concept's recursive place(), as a stack of half-done fans so a deep tree can't run out
        // of the thread's stack: a node's own draws, then each child's in turn, each child's whole
        // subtree placed before the next child is worked out, the order the concept draws in
        val work = ArrayList<WorkItem>()
        work.add(WorkItem(i, x, y, a, len, fan))
        while (work.isNotEmpty()) {
            val top = work[work.size - 1]
            if (!top.started) { start(top); continue }
            if (top.next >= top.arr.size) { work.removeAt(work.size - 1); continue }
            val child = childOf(top)
            work.add(child)
        }
    }

    private class WorkItem(val i: Int, val x: Double, val y: Double, val a: Double, val len: Double, val fan: Double) {
        var started = false
        var arr = IntArray(0)
        var next = 0
        var tot = 0.0
        var wmax = 0.0
        var cum = 0.0
    }

    /** The node's own points, then its children's order (the concept's place(), up to its loop). */
    private fun start(w: WorkItem) {
        w.started = true
        val i = w.i; val x = w.x; val y = w.y; val a = w.a; val len = w.len
        bx[i] = x; by[i] = y; angle[i] = a; length[i] = len; width[i] = len * (if (i == 0) TreeShape.ROOT_WIDTH else TreeShape.WIDTH)
        val dx = cos(a); val dy = sin(a); val qx = -dy; val qy = dx; val o = i * k
        val sign = if (rnd.next() < 0.5) -1.0 else 1.0
        rnd.next()   // the concept draws a phase here for its other looks
        for (j in 0 until k) {
            val u = j.toDouble() / (k - 1)
            val off = sign * TreeShape.BEND * len * 4 * u * (1 - u)
            px[o + j] = x + dx * len * u + qx * off; py[o + j] = y + dy * len * u + qy * off
        }
        for (j in 0 until k) {
            val j0 = if (j > 0) j - 1 else 0; val j1 = if (j < k - 1) j + 1 else k - 1
            val tx = px[o + j1] - px[o + j0]; val ty = py[o + j1] - py[o + j0]
            val m = hypot(tx, ty).let { if (it == 0.0) 1.0 else it }
            nx[o + j] = -ty / m; ny[o + j] = tx / m
        }
        val count = nodes.childCount[i]
        if (count == 0) return
        // the heaviest straight on, the rest out to each side by turns (a stable sort: ties keep their order)
        val kids = Array(count) { nodes.child(i, it) }
        val wp = nodes.weightPow
        val sorted = kids.withIndex().sortedWith(compareByDescending<IndexedValue<Int>> { wp[it.value] }.thenBy { it.index }).map { it.value }
        val left = ArrayList<Int>(); val right = ArrayList<Int>()
        for (q in 1 until count) (if (q % 2 == 1) left else right).add(sorted[q])
        left.reverse()
        val arr = IntArray(count)
        var p = 0
        for (c in left) arr[p++] = c
        arr[p++] = sorted[0]
        for (c in right) arr[p++] = c
        var tot = 0.0; var wmax = 0.0
        for (c in arr) { tot += wp[c]; if (wp[c] > wmax) wmax = wp[c] }
        w.arr = arr; w.tot = tot; w.wmax = wmax
    }

    /** The next child's base, direction, length and fan (the concept's loop body), its own place to come. */
    private fun childOf(w: WorkItem): WorkItem {
        val wp = nodes.weightPow
        val count = w.arr.size
        val c = w.arr[w.next++]
        val fan = w.fan; val len = w.len; val a = w.a; val o = w.i * k
        var o2 = -fan + 2 * fan * (w.cum + wp[c] / 2) / w.tot
        w.cum += wp[c]
        if (count == 1) o2 = (if (rnd.next() < 0.5) -1.0 else 1.0) * fan * 0.45
        val rel = min(1.0, abs(o2) / fan)
        val t = clamp(1 - TreeShape.DROP * rel.pow(0.85) + (rnd.next() - 0.5) * 0.05, 1 - TreeShape.DROP, 1.0)
        val f = t * (k - 1)
        val jj = min(k - 2, floor(f).toInt())
        val fr = f - jj
        val ax = px[o + jj] * (1 - fr) + px[o + jj + 1] * fr; val ay = py[o + jj] * (1 - fr) + py[o + jj + 1] * fr
        val ca = a + o2 * TreeShape.SPREAD + (rnd.next() - 0.5) * 0.08
        val cl = len * TreeShape.RATIO * (0.5 + 0.5 * sqrt(wp[c] / w.wmax)) * (1 - TreeShape.SHORTEN * rel)
        val cs = clamp(2 * fan * wp[c] / w.tot * 0.9 + 0.3, TreeShape.FAN_MIN, TreeShape.FAN_MAX)
        return WorkItem(c, ax, ay, ca, cl, cs)
    }

    /** A leaf's tip, beyond its twig's last point. */
    fun leafTip(d: Int, out: DoubleArray) {
        val o = d * k + k - 1
        val a = atan2(py[o] - py[o - 1], px[o] - px[o - 1]); val ll = length[d] * TreeShape.LEAF
        out[0] = px[o] + cos(a) * ll; out[1] = py[o] + sin(a) * ll
    }

    private fun bounds() {
        val mnx = DoubleArray(n); val mny = DoubleArray(n); val mxx = DoubleArray(n); val mxy = DoubleArray(n)
        val tip = DoubleArray(2)
        val cc = nodes.childCount
        for (i in 0 until n) {
            val w = width[i]
            mnx[i] = Double.POSITIVE_INFINITY; mny[i] = Double.POSITIVE_INFINITY; mxx[i] = Double.NEGATIVE_INFINITY; mxy[i] = Double.NEGATIVE_INFINITY
            for (j in 0 until k) {
                val x = px[i * k + j]; val y = py[i * k + j]
                mnx[i] = min(mnx[i], x - w); mxx[i] = max(mxx[i], x + w); mny[i] = min(mny[i], y - w); mxy[i] = max(mxy[i], y + w)
            }
            if (cc[i] == 0) {
                leafTip(i, tip)
                val e = length[i] * TreeShape.LEAF * 0.3
                mnx[i] = min(mnx[i], tip[0] - e); mxx[i] = max(mxx[i], tip[0] + e); mny[i] = min(mny[i], tip[1] - e); mxy[i] = max(mxy[i], tip[1] + e)
            }
        }
        for (i in n - 1 downTo 1) {
            val p = nodes.parent[i]
            mnx[p] = min(mnx[p], mnx[i]); mny[p] = min(mny[p], mny[i]); mxx[p] = max(mxx[p], mxx[i]); mxy[p] = max(mxy[p], mxy[i])
        }
        for (i in 0 until n) {
            bcx[i] = (mnx[i] + mxx[i]) / 2; bcy[i] = (mny[i] + mxy[i]) / 2; brad[i] = hypot(mxx[i] - mnx[i], mxy[i] - mny[i]) / 2
        }
        for (i in 0 until n) {
            val a = angle[i]; val ca = cos(a); val sa = sin(a); val x0 = bx[i]; val y0 = by[i]
            var a0 = Double.POSITIVE_INFINITY; var a1 = Double.NEGATIVE_INFINITY; var b0 = Double.POSITIVE_INFINITY; var b1 = Double.NEGATIVE_INFINITY
            for (q in nodes.tin[i] until nodes.tout[i]) {
                val d = nodes.order[q]; val wd = width[d]
                for (j in 0..k) {
                    var x: Double; var y: Double; var m2 = wd
                    if (j < k) { x = px[d * k + j]; y = py[d * k + j] } else {
                        if (cc[d] != 0) continue
                        leafTip(d, tip); x = tip[0]; y = tip[1]; m2 = length[d] * TreeShape.LEAF * 0.3
                    }
                    val dx = x - x0; val dy = y - y0; val uu = dx * ca + dy * sa; val vv = -dx * sa + dy * ca
                    if (uu - m2 < a0) a0 = uu - m2
                    if (uu + m2 > a1) a1 = uu + m2
                    if (vv - m2 < b0) b0 = vv - m2
                    if (vv + m2 > b1) b1 = vv + m2
                }
            }
            u0[i] = a0; u1[i] = a1; v0[i] = b0; v1[i] = b1
        }
    }

    private fun clamp(x: Double, a: Double, b: Double) = if (x < a) a else if (x > b) b else x
}
