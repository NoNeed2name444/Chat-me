package com.cramdown.sorta.map

import kotlin.math.max
import kotlin.math.min

// The Fractal look in 3D (S12): what one frame draws. Clusters open as the camera nears them, the
// biggest on screen first, so the picture is the same at every depth: a hub, its parts round it, and
// their parts opening as they grow. At most the Graphics tier's budget of dots is drawn however many
// ideas there are (380, 300 or 220, FractalRules); the last quarter of them fades out. Every array is
// made once, for the tree, so a frame allocates nothing (FractalSightTest). Pure Kotlin, no Android;
// iOS's FractalSight.swift is the same.

/**
 * The tree, its 3D layout and colours, its links by node ([linkEnds], pairs) and the [budget] of dots a
 * frame draws.
 */
class FractalModel(val tree: FractalTree, val layout: FractalLayout3D, linkEnds: IntArray = IntArray(0),
                   val budget: Int = FractalRules.BUDGET) {
    val tones = FractalTones(tree)
    /** Each node's links: the other ends, [linkStart] to [linkStart] + 1 in [linkOther]. */
    val linkStart = IntArray(tree.count + 1)
    val linkOther: IntArray
    /** The smallest dot: how near the camera may come. */
    val smallest: Float

    init {
        val n = tree.count
        val pairs = linkEnds.size / 2
        for (k in 0 until pairs) {
            val a = linkEnds[2 * k]; val b = linkEnds[2 * k + 1]
            if (a in 0 until n && b in 0 until n && a != b) { linkStart[a + 1]++; linkStart[b + 1]++ }
        }
        for (i in 0 until n) linkStart[i + 1] += linkStart[i]
        linkOther = IntArray(linkStart[n])
        val fill = linkStart.copyOf(n)
        for (k in 0 until pairs) {
            val a = linkEnds[2 * k]; val b = linkEnds[2 * k + 1]
            if (a in 0 until n && b in 0 until n && a != b) { linkOther[fill[a]++] = b; linkOther[fill[b]++] = a }
        }
        var least = Double.POSITIVE_INFINITY
        for (i in 0 until n) if (layout.dot[i] > 0) least = min(least, layout.dot[i])
        smallest = (if (least.isFinite()) least else 0.01).toFloat()
    }

    companion object {
        /** The dots a frame draws at a Graphics tier: High 380, Smooth 300, and 220 on a phone short of memory. */
        fun budgetFor(tier: GraphicsTier, lowMemory: Boolean = false): Int = when {
            lowMemory -> FractalRules.BUDGET_LOW
            tier == GraphicsTier.SMOOTH -> FractalRules.BUDGET_BALANCED
            else -> FractalRules.BUDGET
        }
    }
}

/** One frame of the 3D Fractal: the dots drawn, their lines and links, worked out from the camera. */
class FractalSight(val model: FractalModel) {
    private val tree = model.tree
    private val layout = model.layout
    private val n = tree.count

    /** The most dots this frame draws: the model's budget, never above [FractalRules.BUDGET]. */
    var budget = model.budget.coerceIn(1, FractalRules.BUDGET)
        set(value) { field = value.coerceIn(1, FractalRules.BUDGET) }
    /** The cluster flown in to (0 the whole map): what is outside it dims. */
    var focus = 0
    /** The chosen node (its ring) and the idea whose links stand out (Show links), -1 for none. */
    var chosen = -1
    var linksOf = -1
    /** Which nodes the filter shows, null for all. */
    var shown: BooleanArray? = null

    // the dots drawn, in the order they opened: node, place on screen (pixels) and depth, radius (pixels),
    // brightness, how much a lit hub (0..1), and whether a name may stand beside it
    val node = IntArray(FractalRules.BUDGET)
    val x = FloatArray(FractalRules.BUDGET)
    val y = FloatArray(FractalRules.BUDGET)
    val depth = FloatArray(FractalRules.BUDGET)
    val radius = FloatArray(FractalRules.BUDGET)
    val alpha = FloatArray(FractalRules.BUDGET)
    val hub = FloatArray(FractalRules.BUDGET)
    val nameable = BooleanArray(FractalRules.BUDGET)
    var count = 0; private set

    /** The links drawn, by slot in the dots above, and their brightness. */
    val linkFrom = IntArray(FractalRules.LINK_BUDGET)
    val linkTo = IntArray(FractalRules.LINK_BUDGET)
    val linkAlpha = FloatArray(FractalRules.LINK_BUDGET)
    var linkCount = 0; private set
    /** How many nodes were looked at this frame: the guard on the frame's cost. */
    var looked = 0; private set
    /** How many were put in the queue this frame. */
    var pushed = 0; private set

    // per node, made once: the frame it was drawn in, its slot, its brightness as it was reached
    private val stamp = IntArray(n)
    private val slot = IntArray(n)
    private val given = FloatArray(n)
    private var frameNo = 0
    private val heap = IntArray(n)
    private val heapKey = FloatArray(n)
    private var heapSize = 0
    private val clip = FloatArray(4)

    /** A node of the drawn dots' slot [k]'s parent's slot, or -1 when it is not drawn. */
    fun parentSlot(k: Int): Int {
        val p = tree.parent[node[k]]
        return if (p >= 0 && stamp[p] == frameNo) slot[p] else -1
    }

    /** The slot node [i] is drawn in this frame, or -1. */
    fun slotOf(i: Int): Int = if (i in 0 until n && stamp[i] == frameNo) slot[i] else -1

    private fun isShown(i: Int): Boolean { val s = shown; return s == null || (i < s.size && s[i]) }

    /**
     * Works out the frame for a camera whose projection times view is [viewProj], [focal] pixels per
     * unit at depth 1, in a view [width] by [height] pixels with [density] pixels a dp (a point on iOS).
     */
    fun step(viewProj: FloatArray, focal: Float, width: Float, height: Float, density: Float) {
        frameNo++
        count = 0; linkCount = 0; heapSize = 0; looked = 0; pushed = 0
        val d = max(density, 0.1f)
        given[0] = 1f
        push(0, Float.MAX_VALUE)
        val fadeFrom = budget * 0.75f
        while (heapSize > 0 && count < budget && looked < LOOK_MOST) {
            val i = pop()
            looked++
            val px = layout.pos[3 * i].toFloat(); val py = layout.pos[3 * i + 1].toFloat(); val pz = layout.pos[3 * i + 2].toFloat()
            Mat4.project(viewProj, px, py, pz, clip)
            val w = clip[3]
            val ahead = w > NEAR
            // its cluster's spacing on screen, in dp: what opens it and lights it
            val onScreen = if (ahead) (layout.spacing[i] * focal / w / d).toFloat() else Float.MAX_VALUE
            if (ahead) {
                val sx = (clip[0] / w * 0.5f + 0.5f) * width
                val sy = (1f - (clip[1] / w * 0.5f + 0.5f)) * height
                val h = if (tree.childCount[i] > 0) FractalRules.smooth(FractalRules.HUB_FROM, FractalRules.HUB_TO, onScreen.toDouble()).toFloat() else 0f
                val r = FractalRules.clamp(layout.dot[i] * focal / w / d, 1.25 + 1.75 * h, 18.0 + 8 * h).toFloat() * d
                val glow = r * (1f + GLOW_REACH * h)
                if (sx + glow >= 0f && sx - glow <= width && sy + glow >= 0f && sy - glow <= height) {
                    var a = given[i]
                    if (focus > 0 && !tree.holds(focus, i)) a *= FractalRules.DIM.toFloat()
                    if (count >= fadeFrom) a *= (budget - count) / (budget - fadeFrom)
                    if (i == chosen) a = max(a, 1f)
                    val k = count++
                    node[k] = i; x[k] = sx; y[k] = sy; depth[k] = w; radius[k] = r; alpha[k] = a; hub[k] = h
                    nameable[k] = a >= FractalRules.NAMED_ALPHA && r >= FractalRules.NAMED_DOT * d
                    stamp[i] = frameNo; slot[i] = k
                }
            }
            // its parts, once its cluster is open enough on screen
            val parts = tree.childCount[i]
            if (parts == 0 || onScreen < FractalRules.OPEN) continue
            val open = FractalRules.smooth(FractalRules.OPEN, FractalRules.OPEN_FULL, onScreen.toDouble()).toFloat()
            val s = tree.childStart[i]
            // heaviest first, and never more than a frame could draw: a folder of 100,000 costs what one of 380 does
            for (q in 0 until min(parts, budget)) {
                if (pushed >= PUSH_MOST) break
                val c = tree.children[s + q]
                if (!isShown(c) || !inView(c, viewProj, focal, width, height)) continue
                given[c] = given[i] * open
                Mat4.project(viewProj, layout.pos[3 * c].toFloat(), layout.pos[3 * c + 1].toFloat(), layout.pos[3 * c + 2].toFloat(), clip)
                push(c, if (clip[3] > NEAR) (layout.spacing[c] * focal / clip[3]).toFloat() else Float.MAX_VALUE / 2)
            }
        }
        links()
    }

    /** Whether any of [c]'s cluster's ball can be in the view. */
    private fun inView(c: Int, viewProj: FloatArray, focal: Float, width: Float, height: Float): Boolean {
        Mat4.project(viewProj, layout.centre[3 * c].toFloat(), layout.centre[3 * c + 1].toFloat(), layout.centre[3 * c + 2].toFloat(), clip)
        val w = clip[3]
        val reach = layout.reach[c].toFloat()
        if (w < -reach) return false
        if (w <= NEAR) return true
        val r = reach * focal / w
        val sx = (clip[0] / w * 0.5f + 0.5f) * width
        val sy = (1f - (clip[1] / w * 0.5f + 0.5f)) * height
        return sx + r >= 0f && sx - r <= width && sy + r >= 0f && sy - r <= height
    }

    /** The links between drawn ideas, each once, at most [FractalRules.LINK_BUDGET]: Show links' own bright, the rest faint. */
    private fun links() {
        val base = (FractalRules.LINE * FractalRules.LINK_LINE).toFloat()
        for (k in 0 until count) {
            val i = node[k]
            for (e in model.linkStart[i] until model.linkStart[i + 1]) {
                val j = model.linkOther[e]
                if (stamp[j] != frameNo || slot[j] >= k) continue
                if (linkCount >= FractalRules.LINK_BUDGET) return
                val a = min(alpha[k], alpha[slot[j]]) * base
                val focusK = if (linksOf < 0) 1f else if (i == linksOf || j == linksOf) FractalRules.LINKS_ON.toFloat() else FractalRules.LINKS_OFF.toFloat()
                linkFrom[linkCount] = slot[j]; linkTo[linkCount] = k; linkAlpha[linkCount] = a * focusK
                linkCount++
            }
        }
    }

    private fun push(i: Int, key: Float) {
        pushed++
        var k = heapSize++
        heap[k] = i; heapKey[k] = key
        while (k > 0) {
            val p = (k - 1) / 2
            if (heapKey[p] >= heapKey[k]) break
            swap(p, k); k = p
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        heapSize--
        if (heapSize > 0) {
            heap[0] = heap[heapSize]; heapKey[0] = heapKey[heapSize]
            var k = 0
            while (true) {
                val a = 2 * k + 1; val b = a + 1
                var m = k
                if (a < heapSize && heapKey[a] > heapKey[m]) m = a
                if (b < heapSize && heapKey[b] > heapKey[m]) m = b
                if (m == k) break
                swap(m, k); k = m
            }
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val t = heap[a]; heap[a] = heap[b]; heap[b] = t
        val u = heapKey[a]; heapKey[a] = heapKey[b]; heapKey[b] = u
    }

    companion object {
        /** A hub's glow reaches this many more of its radius when fully lit. */
        const val GLOW_REACH = 1.2f
        /** A place nearer the eye than this (view depth) is not drawn. */
        const val NEAR = 1e-4f
        /** The most nodes a frame looks at, drawn or passed over: the frame's cost however big the tree. */
        const val LOOK_MOST = 4 * FractalRules.BUDGET
        /** The most nodes a frame queues. */
        const val PUSH_MOST = 8 * FractalRules.BUDGET
        /** A tree line's brightness beside its dot's (Performance's folder line, the flat look's 0.22 x 1.2). */
        val TREE_LINE = (FractalRules.LINE * FractalRules.TREE_LINE).toFloat()
    }
}
