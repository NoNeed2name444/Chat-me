package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The Fractal look (S12): Performance's night sky, lit dots, glows and hair-thin lines, laid out so
// every dot is the hub of a smaller copy of the cluster it sits in. Tap a dot and its cluster opens
// round it; back, a tap on empty space or a pinch out goes up again. The layout is the approved
// concept's (scratchpad concepts/nested-maps.html, makeF) and the frame its Fractal renderer
// (visitF, drawFractal, labelsFractal, hitTest, frameOf, startAnim), with a draw budget and one fix:
// a name is only placed beside a dot that is drawn and visible.
//
// The same code is in Swift (ios/Sorta/Maps/FractalCore.swift); tools/fractal_fixture.json holds a
// tree, the layout the concept's own code made of it, and frames this file made, which both
// platforms' tests read back (FractalCoreTest, FractalCoreTests.swift). Pure Kotlin, no Android.
// The frame allocates nothing (FractalCostTest).

/** The numbers both platforms share. */
object FractalRules {
    /** The seed of the layout's random turns, the concept's. */
    const val SEED = 91
    /** A dot's room, as a share of its hub's spacing: an idea's, and a hub's least. */
    const val LEAF_DOT = 0.045
    const val HUB_DOT = 0.1
    const val HUB_DOT_PARENT = 0.05
    /** A cluster opens once its spacing on screen passes this many points; its dots fade in by [OPEN_FULL]. */
    const val OPEN = 5.0
    const val OPEN_FULL = 16.0
    /** A hub lights (ball, glow, larger name) as its spacing grows from [HUB_FROM] to [HUB_TO] points. */
    const val HUB_FROM = 14.0
    const val HUB_TO = 44.0
    /** The most dots drawn in one frame, however many ideas there are; the last quarter fades out. */
    const val BUDGET = 380
    /** The budget at the Balanced and Low graphics settings (High is [BUDGET]). */
    const val BUDGET_BALANCED = 300
    const val BUDGET_LOW = 220
    /** The most links drawn in one frame. */
    const val LINK_BUDGET = 600
    /** The most names on screen at once. */
    const val NAMES = 120
    /** The least a named dot's alpha may be, so a name never stands without its dot. */
    const val NAMED_ALPHA = 0.6
    /** A named dot's least radius, in points: no name may cover it. */
    const val NAMED_DOT = 2.5
    /** What is outside the part in view dims to. */
    const val DIM = 0.18
    /** A name's text size in points (before the reader's text size): an idea's, and a lit hub's least and most. */
    const val NAME = 12.0
    const val HUB_NAME_MORE = 6.0
    /** A dot's name lies this far past its edge, in points. */
    const val NAME_GAP = 5.0
    /** A cluster's children are named once it spans this many points. */
    const val NAME_ROOM = 64.0
    /** The share of the view the cluster in view fills (concept: 0.5 x 0.84). */
    const val FILL = 0.42
    /** The room kept round the cluster in view, beyond the glass, in points: top, right, bottom, left. */
    const val PAD_TOP = 12.0
    const val PAD_SIDE = 22.0
    const val PAD_BOTTOM = 26.0
    /** How far out and in a pinch can go, against the whole map's view. */
    const val ZOOM_OUT = 0.35
    const val ZOOM_IN = 1e5
    /** Lines: a hub's line to each dot (x1.2) and a link between clusters (x0.55), Performance's base 0.22. */
    const val LINE = 0.22
    const val TREE_LINE = 1.2
    const val LINK_LINE = 0.55
    /** Show links: the chosen idea's own links x3, the rest x0.15 (Performance's). */
    const val LINKS_ON = 3.0
    const val LINKS_OFF = 0.15

    fun smooth(a: Double, b: Double, x: Double): Double {
        var t = (x - a) / (b - a)
        t = if (t < 0) 0.0 else if (t > 1) 1.0 else t
        return t * t * (3 - 2 * t)
    }

    fun clamp(x: Double, a: Double, b: Double): Double = if (x < a) a else if (x > b) b else x

    /** The concept's mulberry32: the same numbers in Swift, Kotlin and JavaScript. */
    class Random(seed: Int) {
        private var s = seed
        fun next(): Double {
            s += 0x6D2B79F5
            var t = s
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }
}

/**
 * A tree of nodes for the Fractal map: node 0 the hub (All ideas), every other node's parent
 * earlier in the list. Today the store gives hub → folders (nested as they are) → ideas, and the
 * ideas in no folder their own cluster; any deeper tree draws the same way.
 */
class FractalTree(
    /** Each node's parent's index; -1 for the hub. */
    val parent: IntArray,
    /** [HUB], [FOLDER] or [IDEA]. */
    val kind: ByteArray,
    /** The store's id, upper case; "" for the hub and the loose cluster. */
    val ids: Array<String>,
    val titles: Array<String>,
    /** The colour's region: the top folder's place among the top folders, or -1 for none (Performance's palette). */
    val region: IntArray,
) {
    val count: Int = parent.size
    /** Children, heaviest first (ties in list order): node i's are children[childStart[i] until childStart[i + 1]]. */
    val childStart = IntArray(count + 1)
    val children = IntArray(max(count - 1, 0))
    val childCount = IntArray(count)
    val depth = IntArray(count)
    /** Leaves under each node, to the power 0.7: how much room it takes. */
    val weight = DoubleArray(count)
    /** Entry and exit times of a walk: p is an ancestor of q (or q) when tin[p] <= tin[q] < tout[p]. */
    val tin = IntArray(count)
    val tout = IntArray(count)
    /** The nodes in walk order. */
    val order = IntArray(count)

    init {
        require(count >= 1 && parent[0] == -1) { "node 0 is the hub" }
        for (i in 1 until count) require(parent[i] in 0 until i) { "a parent comes before its child" }
        for (i in 1 until count) childCount[parent[i]]++
        for (i in 0 until count) childStart[i + 1] = childStart[i] + childCount[i]
        val fill = IntArray(count)
        for (i in 1 until count) { val p = parent[i]; children[childStart[p] + fill[p]] = i; fill[p]++ }
        for (i in 1 until count) depth[i] = depth[parent[i]] + 1
        val leaves = DoubleArray(count)
        for (i in count - 1 downTo 0) { if (childCount[i] == 0) leaves[i] = 1.0; if (i > 0) leaves[parent[i]] += leaves[i] }
        for (i in 0 until count) weight[i] = leaves[i].pow(0.7)
        // heaviest first, ties kept in list order (a stable sort, as JavaScript's)
        val buffer = IntArray(count)
        for (i in 0 until count) {
            val s = childStart[i]; val n = childCount[i]
            for (k in 0 until n) buffer[k] = children[s + k]
            for (a in 1 until n) {
                val v = buffer[a]; var b = a - 1
                while (b >= 0 && weight[buffer[b]] < weight[v]) { buffer[b + 1] = buffer[b]; b-- }
                buffer[b + 1] = v
            }
            for (k in 0 until n) children[s + k] = buffer[k]
        }
        // the walk, without recursion: a deep tree needs no stack
        var t = 0
        val stack = IntArray(count); val next = IntArray(count)
        var top = 0
        stack[0] = 0; tin[0] = 0; order[t++] = 0
        while (top >= 0) {
            val i = stack[top]
            if (next[i] < childCount[i]) {
                val c = children[childStart[i] + next[i]]; next[i]++
                tin[c] = t; order[t++] = c
                stack[++top] = c
            } else { tout[i] = t; top-- }
        }
    }

    /** Whether [p] is [q] or above it. */
    fun holds(p: Int, q: Int): Boolean = tin[p] <= tin[q] && tin[q] < tout[p]

    fun isHub(i: Int): Boolean = childCount[i] > 0

    companion object {
        const val HUB: Byte = 0
        const val FOLDER: Byte = 1
        const val IDEA: Byte = 2

        /** A folder as the map reads it. */
        class Folder(val id: String, val parentId: String?, val name: String, val created: Long)
        /** An idea as the map reads it. */
        class Idea(val id: String, val folderId: String?, val title: String, val created: Long)

        /**
         * The store's folders and ideas as a tree: the hub, the top folders oldest first, each
         * folder's own folders and then its ideas, oldest first (the id breaks a tie), and the ideas
         * in no folder hanging from the hub itself, after the folders, in the loose colour.
         */
        fun of(folders: List<Folder>, ideas: List<Idea>, hubTitle: String): FractalTree {
            val byId = HashMap<String, Folder>()
            for (f in folders) byId[f.id.uppercase()] = f
            fun parentOf(f: Folder): String? = f.parentId?.uppercase()?.takeIf { it in byId && it != f.id.uppercase() }
            val sub = HashMap<String?, MutableList<Folder>>()
            for (f in folders) sub.getOrPut(parentOf(f)) { mutableListOf() }.add(f)
            val inFolder = HashMap<String?, MutableList<Idea>>()
            for (n in ideas) inFolder.getOrPut(n.folderId?.uppercase()?.takeIf { it in byId }) { mutableListOf() }.add(n)
            val folderOrder = compareBy<Folder>({ it.created }, { it.id.uppercase() })
            val ideaOrder = compareBy<Idea>({ it.created }, { it.id.uppercase() })
            val parents = ArrayList<Int>(); val kinds = ArrayList<Byte>(); val idList = ArrayList<String>()
            val titleList = ArrayList<String>(); val regions = ArrayList<Int>()
            fun add(p: Int, k: Byte, id: String, title: String, region: Int): Int {
                parents.add(p); kinds.add(k); idList.add(id); titleList.add(title); regions.add(region); return parents.size - 1
            }
            add(-1, HUB, "", hubTitle, -1)
            val seen = HashSet<String>()
            fun addFolder(f: Folder, p: Int, region: Int) {
                val key = f.id.uppercase()
                if (!seen.add(key)) return
                val me = add(p, FOLDER, key, f.name, region)
                for (c in sub[key].orEmpty().sortedWith(folderOrder)) addFolder(c, me, region)
                for (n in inFolder[key].orEmpty().sortedWith(ideaOrder)) add(me, IDEA, n.id.uppercase(), n.title, region)
            }
            val tops = sub[null].orEmpty().sortedWith(folderOrder).toMutableList()
            // a folder in a loop of parents has no top: the loop is entered at each of its folders, oldest first
            for (f in folders.sortedWith(folderOrder)) {
                if (parentOf(f) == null) continue
                var p: String? = f.id.uppercase(); val path = HashSet<String>()
                while (p != null && path.add(p)) p = byId[p]?.let { parentOf(it) }
                if (p == f.id.uppercase()) tops.add(f)
            }
            var region = 0
            for (f in tops) if (f.id.uppercase() !in seen) addFolder(f, 0, region++)
            for (n in inFolder[null].orEmpty().sortedWith(ideaOrder)) add(0, IDEA, n.id.uppercase(), n.title, -1)
            return FractalTree(parents.toIntArray(), kinds.toByteArray(), idList.toTypedArray(), titleList.toTypedArray(), regions.toIntArray())
        }
    }
}

/**
 * Each node's colours, packed 0xRRGGBB: Performance's region colours (iOS's GraphPerfScene, :61-71
 * and :180-189), one hue per top folder in turn, the loose grey for the hub and the ideas in no
 * folder. A dot is its hue mixed 0.08 to white per folder level below the top (at most 0.3), a lit
 * hub's ball 0.45 and its glow 0.2.
 */
class FractalTones(tree: FractalTree) {
    val dot = IntArray(tree.count)
    val hub = IntArray(tree.count)
    val glow = IntArray(tree.count)

    init {
        for (i in 0 until tree.count) {
            val g = tree.region[i]
            val o = if (g >= 0) 3 * (g % (PALETTE.size / 3)) else -1
            val r = if (o >= 0) PALETTE[o] else LOOSE[0]
            val gr = if (o >= 0) PALETTE[o + 1] else LOOSE[1]
            val b = if (o >= 0) PALETTE[o + 2] else LOOSE[2]
            val down = if (o >= 0) min(0.08 * max(tree.depth[i] - 2, 0), 0.3) else 0.0
            dot[i] = pack(r, gr, b, down); hub[i] = pack(r, gr, b, 0.45); glow[i] = pack(r, gr, b, 0.2)
        }
    }

    companion object {
        /** Twelve hues that read on the night sky. */
        val PALETTE = doubleArrayOf(
            0.36, 0.61, 1.00, 1.00, 0.37, 0.49, 1.00, 0.71, 0.26, 0.25, 0.85, 0.60,
            0.73, 0.55, 1.00, 0.27, 0.84, 0.92, 1.00, 0.53, 0.26, 0.90, 0.42, 1.00,
            0.66, 0.88, 0.35, 1.00, 0.85, 0.35, 0.50, 0.65, 1.00, 1.00, 0.56, 0.72)
        val LOOSE = doubleArrayOf(0.79, 0.82, 0.88)
        /** The night sky, in both light and dark mode, as Performance's: #05070C. */
        const val SKY = 0x05070C
        /** The chosen dot's ring and the names' ink. */
        const val INK = 0xFFFFFF

        private fun byte(c: Double, t: Double): Int = floor((c + (1 - c) * t) * 255 + 0.5).toInt().coerceIn(0, 255)
        fun pack(r: Double, g: Double, b: Double, t: Double): Int = (byte(r, t) shl 16) or (byte(g, t) shl 8) or byte(b, t)
    }
}

/** Where each node sits, in the map's own units (the hub's spacing is 1), and each cluster's bounds. */
class FractalLayout(val tree: FractalTree) {
    private val n = tree.count
    val x = DoubleArray(n)
    val y = DoubleArray(n)
    /** The spacing of the node's own cluster: its children lie about this far from it. */
    val spacing = DoubleArray(n)
    /** The dot's radius. */
    val dot = DoubleArray(n)
    /** The farthest any of its cluster reaches from it. */
    val reach = DoubleArray(n)
    /** The middle and half size of its cluster's box. */
    val boxX = DoubleArray(n)
    val boxY = DoubleArray(n)
    val halfW = DoubleArray(n)
    val halfH = DoubleArray(n)

    init {
        val random = FractalRules.Random(FractalRules.SEED)
        val left = IntArray(n); val right = IntArray(n); val arr = IntArray(n)
        // the concept's place(), as a loop over a stack of frames so a deep tree needs no recursion;
        // each frame lays its children out in the concept's order, drawing the same random numbers
        place(0, 0.0, 0.0, 1.0, -PI / 2, 1.0, random, left, right, arr)
        for (i in 0 until n) {
            var r = dot[i] * 3
            var x0 = x[i] - r; var x1 = x[i] + r; var y0 = y[i] - r; var y1 = y[i] + r
            for (k in tree.tin[i] + 1 until tree.tout[i]) {
                val d = tree.order[k]
                val e = hypot(x[d] - x[i], y[d] - y[i]) + dot[d]
                if (e > r) r = e
                if (x[d] - dot[d] < x0) x0 = x[d] - dot[d]
                if (x[d] + dot[d] > x1) x1 = x[d] + dot[d]
                if (y[d] - dot[d] < y0) y0 = y[d] - dot[d]
                if (y[d] + dot[d] > y1) y1 = y[d] + dot[d]
            }
            reach[i] = r; boxX[i] = (x0 + x1) / 2; boxY[i] = (y0 + y1) / 2
            val hw = (x1 - x0) / 2; val hh = (y1 - y0) / 2; val m = max(hw, hh) * 0.6
            halfW[i] = max(hw, m); halfH[i] = max(hh, m)
        }
    }

    private fun place(root: Int, rx: Double, ry: Double, rsp: Double, rout: Double, rpsp: Double,
                      random: FractalRules.Random, left: IntArray, right: IntArray, arr: IntArray) {
        // frames: node, and the next child to place; the children's order is kept per frame in [arr]
        val t = tree
        val frameNode = IntArray(n); val frameNext = IntArray(n); val frameBase = IntArray(n)
        val angle = DoubleArray(n)
        var top = -1
        var used = 0
        fun enter(i: Int, px: Double, py: Double, sp: Double, out: Double, psp: Double) {
            x[i] = px; y[i] = py; spacing[i] = sp; angle[i] = out
            if (t.childCount[i] > 0) dot[i] = max(sp * FractalRules.HUB_DOT, psp * FractalRules.HUB_DOT_PARENT)
            val count = t.childCount[i]
            if (count == 0) return
            val s = t.childStart[i]
            var nl = 0; var nr = 0
            for (k in 1 until count) if (k % 2 == 1) left[nl++] = t.children[s + k] else right[nr++] = t.children[s + k]
            val base = used
            var w = base
            for (k in nl - 1 downTo 0) arr[w++] = left[k]
            arr[w++] = t.children[s]
            for (k in 0 until nr) arr[w++] = right[k]
            used = w
            top++
            frameNode[top] = i; frameNext[top] = 0; frameBase[top] = base
        }
        enter(root, rx, ry, rsp, rout, rpsp)
        while (top >= 0) {
            val i = frameNode[top]; val k = frameNext[top]; val count = t.childCount[i]
            if (k >= count) { used = frameBase[top]; top--; continue }
            frameNext[top] = k + 1
            val sp = spacing[i]
            val full = i == 0
            val arc = if (full) 2 * PI else min(2 * PI - 1.3, 1.3 + count * 0.55)
            val start = if (full) -PI / 2 - PI * (count - 1) / count else angle[i] - arc / 2
            val slice = arc / count
            val c = arr[frameBase[top] + k]
            val wmax = t.weight[t.children[t.childStart[i]]]
            val ang = start + arc * (k + 0.5) / count + (random.next() - 0.5) * slice * 0.35
            val csp = sp * FractalRules.clamp(0.9 * sin(min(slice, PI) / 2), 0.2, 0.42) * (0.62 + 0.38 * sqrt(t.weight[c] / wmax))
            val dist = if (count > 8) sp * ((if (k % 2 == 1) 0.64 else 0.98) + random.next() * 0.1) else sp * (0.74 + random.next() * 0.28)
            if (t.childCount[c] == 0) dot[c] = sp * FractalRules.LEAF_DOT
            enter(c, x[i] + cos(ang) * dist, y[i] + sin(ang) * dist, csp, ang, sp)
        }
    }
}

/**
 * One frame of the Fractal map, in points: where the camera is, which dots are drawn and how
 * brightly, the lines, the names and where a tap lands. Every array is made once, for the tree;
 * [step] allocates nothing.
 */
class FractalFrame(val layout: FractalLayout, linkEnds: IntArray = IntArray(0)) {
    val tree = layout.tree
    private val n = tree.count

    // the view, in points
    var width = 1.0; private set
    var height = 1.0; private set
    var insetTop = 0.0; private set
    var insetLeft = 0.0; private set
    var insetBottom = 0.0; private set
    var insetRight = 0.0; private set
    /** The reader's text size, as a factor of the standard (the map stops it at AX1, as every map's). */
    var textScale = 1.0
    /** Each node's name's width at 100 points, from the platform's font: the names' room. */
    val nameWidth = DoubleArray(n)
    /** The least half size of a name's or dot's touch box, in points (iOS 22: 44 pt; Android 24: 48 dp). */
    var reach = 22.0

    // the camera: the map point at the view's middle, and points per map unit
    var camX = 0.0
    var camY = 0.0
    var camZ = 1.0
    /** The cluster in view (Back leaves it for its parent) and the one before it, while the camera moves. */
    var focus = 0; private set
    private var focusFrom = 0
    private var focusMix = 1.0

    /** The chosen node (its ring), the idea whose links stand out, and which nodes the filter shows (null: all). */
    var chosen = -1
    var linksOf = -1
    var shown: BooleanArray? = null
    /** The most dots this frame draws: the graphics setting's budget, never above [FractalRules.BUDGET]. */
    var budget = FractalRules.BUDGET
        set(value) { field = value.coerceIn(1, FractalRules.BUDGET) }

    // the camera's glide (a zoom about the one point both views share)
    var gliding = false; private set
    private var glideStart = 0.0
    private var glideTime = 0.0
    private var lz0 = 0.0; private var lz1 = 0.0
    private var g0x = 0.0; private var g0y = 0.0; private var g1x = 0.0; private var g1y = 0.0
    private var fixX = 0.0; private var fixY = 0.0; private var fixQx = 0.0; private var fixQy = 0.0
    private var glideLinear = false

    // per node, this frame
    val sx = DoubleArray(n)
    val sy = DoubleArray(n)
    /** Its cluster's spacing on screen, in points. */
    val onScreen = DoubleArray(n)
    /** How far its cluster has opened (0..1): its dots fade in with it. */
    val open = DoubleArray(n)
    private val stamp = IntArray(n)
    private var frameNo = 0

    // the dots drawn, biggest clusters first
    val drawn = IntArray(FractalRules.BUDGET)
    var drawnCount = 0; private set
    /** Per drawn dot: radius, alpha, how much a lit hub (0..1), its glow's alpha, its line's alpha, ringed. */
    val dotRadius = DoubleArray(FractalRules.BUDGET)
    val dotAlpha = DoubleArray(FractalRules.BUDGET)
    val dotHub = DoubleArray(FractalRules.BUDGET)
    val glowAlpha = DoubleArray(FractalRules.BUDGET)
    val lineAlpha = DoubleArray(FractalRules.BUDGET)
    val named = BooleanArray(FractalRules.BUDGET)
    private val slotOf = IntArray(n)
    private val heap = IntArray(n)
    private val heapKey = DoubleArray(n)
    private var heapSize = 0

    // links: both ends (nodes), and those drawn this frame
    private val linkA: IntArray
    private val linkB: IntArray
    val linkFrom = IntArray(FractalRules.LINK_BUDGET)
    val linkTo = IntArray(FractalRules.LINK_BUDGET)
    /** The link's colour is its first end's cluster's. */
    val linkTone = IntArray(FractalRules.LINK_BUDGET)
    val linkAlpha = DoubleArray(FractalRules.LINK_BUDGET)
    var linkCount = 0; private set

    // names: node (-1: a lit dot kept clear), box, size, side (0 after, 1 before, 2 centred), alpha
    val nameNode = IntArray(FractalRules.NAMES)
    val nameX = DoubleArray(FractalRules.NAMES)
    val nameY = DoubleArray(FractalRules.NAMES)
    val nameX0 = DoubleArray(FractalRules.NAMES)
    val nameY0 = DoubleArray(FractalRules.NAMES)
    val nameX1 = DoubleArray(FractalRules.NAMES)
    val nameY1 = DoubleArray(FractalRules.NAMES)
    val nameSize = DoubleArray(FractalRules.NAMES)
    val nameSide = IntArray(FractalRules.NAMES)
    val nameAlpha = DoubleArray(FractalRules.NAMES)
    var nameCount = 0; private set
    private val candidate = IntArray(FractalRules.BUDGET)
    private val candidateKey = DoubleArray(FractalRules.BUDGET)

    init {
        val links = linkEnds.size / 2
        linkA = IntArray(links) { linkEnds[2 * it] }
        linkB = IntArray(links) { linkEnds[2 * it + 1] }
    }

    /** The view's size and the glass over each edge, in points. */
    fun setView(w: Double, h: Double, top: Double, left: Double, bottom: Double, right: Double) {
        width = max(w, 1.0); height = max(h, 1.0)
        insetTop = top; insetLeft = left; insetBottom = bottom; insetRight = right
    }

    /** How far the camera may go out, and in, against the whole map's view. */
    fun zoomOut(): Double = zoomFor(0) * FractalRules.ZOOM_OUT
    fun zoomIn(): Double = zoomFor(0) * FractalRules.ZOOM_IN

    private fun areaW() = max(40.0, width - insetLeft - insetRight - 2 * FractalRules.PAD_SIDE)
    private fun areaH() = max(40.0, height - insetTop - insetBottom - FractalRules.PAD_TOP - FractalRules.PAD_BOTTOM)

    /** The zoom that fits [i]'s cluster in the view. */
    fun zoomFor(i: Int): Double = min(areaW() / layout.halfW[i], areaH() / layout.halfH[i]) * FractalRules.FILL

    /** The camera that frames [i]'s cluster in the room the glass leaves: x, y, zoom. */
    fun frameOf(i: Int, out: DoubleArray) {
        val z = zoomFor(i)
        val dx = insetLeft + FractalRules.PAD_SIDE + areaW() / 2 - width / 2
        val dy = insetTop + FractalRules.PAD_TOP + areaH() / 2 - height / 2
        out[0] = layout.boxX[i] - dx / z; out[1] = layout.boxY[i] - dy / z; out[2] = z
    }

    private val goal = DoubleArray(3)

    /** Opens [i]'s cluster: the camera glides there (at once with [instant], as Reduce Motion asks). Returns the glide's length, in seconds. */
    fun go(i: Int, now: Double, instant: Boolean): Double {
        focusFrom = if (instant) i else focus
        focus = i
        focusMix = if (instant) 1.0 else 0.0
        frameOf(i, goal)
        if (instant) { camX = goal[0]; camY = goal[1]; camZ = goal[2]; gliding = false; return 0.0 }
        return glide(goal[0], goal[1], goal[2], now)
    }

    /** Back up a level; false at the whole map. */
    fun back(now: Double, instant: Boolean): Boolean {
        if (focus <= 0) return false
        go(tree.parent[focus], now, instant)
        return true
    }

    private fun glide(x: Double, y: Double, z: Double, now: Double): Double {
        g0x = camX; g0y = camY; lz0 = ln(camZ); g1x = x; g1y = y; lz1 = ln(z)
        val d = camZ - z
        glideLinear = abs(d) < 1e-3 * camZ
        if (!glideLinear) {
            // the point that stays put: c0 + (P - c0) ... solved as the concept's spiral similarity, with no turn
            fixX = (g0x * camZ - g1x * z) / d; fixY = (g0y * camZ - g1y * z) / d
            fixQx = (fixX - g0x) * camZ; fixQy = (fixY - g0y) * camZ
        }
        glideTime = FractalRules.clamp(0.46 + 0.15 * abs(lz1 - lz0) / ln(2.0), 0.42, 1.25)
        glideStart = now; gliding = true
        return glideTime
    }

    /** Moves the glide on to [now], in seconds. */
    fun advance(now: Double) {
        if (!gliding) { focusMix = 1.0; return }
        var t = (now - glideStart) / glideTime
        if (t >= 1) { t = 1.0; gliding = false }
        if (t < 0) t = 0.0
        val u = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        focusMix = u
        val z = exp(lz0 + (lz1 - lz0) * u)
        camZ = z
        if (glideLinear) { camX = g0x + (g1x - g0x) * u; camY = g0y + (g1y - g0y) * u }
        else { camX = fixX - fixQx / z; camY = fixY - fixQy / z }
        if (!gliding) { camX = g1x; camY = g1y; camZ = exp(lz1); focusMix = 1.0 }
    }

    /** Stops a glide where it is (a finger came down). */
    fun stop() { gliding = false; focusMix = 1.0; focusFrom = focus }

    /** A pinch: zoom by [factor] about the screen point [px], [py]. */
    fun zoom(px: Double, py: Double, factor: Double) {
        stop()
        val wx = camX + (px - width / 2) / camZ; val wy = camY + (py - height / 2) / camZ
        camZ = FractalRules.clamp(camZ * factor, zoomOut(), zoomIn())
        camX = wx - (px - width / 2) / camZ; camY = wy - (py - height / 2) / camZ
    }

    /** A drag, in points. */
    fun pan(dx: Double, dy: Double) { stop(); camX -= dx / camZ; camY -= dy / camZ }

    /** After a pinch or a drag: the deepest cluster that now fills the view is the one Back leaves. */
    fun settle() {
        val dx = insetLeft + FractalRules.PAD_SIDE + areaW() / 2 - width / 2
        val dy = insetTop + FractalRules.PAD_TOP + areaH() / 2 - height / 2
        val wx = camX + dx / camZ; val wy = camY + dy / camZ
        var i = 0
        while (true) {
            var best = -1; var bd = Double.MAX_VALUE
            val s = tree.childStart[i]
            for (k in 0 until tree.childCount[i]) {
                val c = tree.children[s + k]
                if (tree.childCount[c] == 0 || !isShown(c)) continue
                if (zoomFor(c) > camZ * 1.3) continue
                val ddx = wx - layout.x[c]; val ddy = wy - layout.y[c]; val dd = ddx * ddx + ddy * ddy
                if (dd > layout.reach[c] * layout.reach[c]) continue
                if (dd < bd) { bd = dd; best = c }
            }
            if (best < 0) break
            i = best
        }
        focus = i; focusFrom = i; focusMix = 1.0
    }

    private fun isShown(i: Int): Boolean { val m = shown; return m == null || m[i] }

    private fun inside(top: Int, i: Int) = top <= 0 || tree.holds(top, i)

    /** How bright a node is: the part in view stays, the rest dims, and fades across while the camera moves. */
    fun dim(i: Int): Double {
        if (focus <= 0 && focusFrom <= 0) return 1.0
        val b = if (inside(focus, i)) 1.0 else FractalRules.DIM
        if (focusMix >= 1) return b
        val a = if (inside(focusFrom, i)) 1.0 else FractalRules.DIM
        return a + (b - a) * focusMix
    }

    /** How much of a lit hub [i] is, by its spacing on screen. */
    fun hubness(i: Int): Double = if (tree.childCount[i] > 0) FractalRules.smooth(FractalRules.HUB_FROM, FractalRules.HUB_TO, onScreen[i]) else 0.0

    /** A hub whose cluster is bigger than the view has been passed through: its dot fades away. */
    fun passed(i: Int): Double {
        if (tree.childCount[i] == 0 || (focus > 0 && tree.holds(focus, i))) return 1.0
        val m = min(width, height)
        return 1 - FractalRules.smooth(0.35 * m, 0.7 * m, onScreen[i])
    }

    /** A dot's radius on screen. */
    fun dotSize(i: Int, h: Double): Double = FractalRules.clamp(layout.dot[i] * camZ, 1.25 + 1.75 * h, 18 + 8 * h)

    private fun stampNode(i: Int): Boolean {
        sx[i] = (layout.x[i] - camX) * camZ + width / 2
        sy[i] = (layout.y[i] - camY) * camZ + height / 2
        stamp[i] = frameNo
        val r = layout.reach[i] * camZ
        if (sx[i] + r < -30 || sx[i] - r > width + 30 || sy[i] + r < -30 || sy[i] - r > height + 30) return false
        onScreen[i] = layout.spacing[i] * camZ
        return true
    }

    private fun push(i: Int, key: Double) {
        var k = heapSize++
        while (k > 0) { val p = (k - 1) shr 1; if (heapKey[p] >= key) break; heap[k] = heap[p]; heapKey[k] = heapKey[p]; k = p }
        heap[k] = i; heapKey[k] = key
    }

    private fun pop(): Int {
        val top = heap[0]; heapSize--
        val last = heap[heapSize]; val lk = heapKey[heapSize]
        var k = 0
        while (true) {
            var c = 2 * k + 1
            if (c >= heapSize) break
            if (c + 1 < heapSize && heapKey[c + 1] > heapKey[c]) c++
            if (heapKey[c] <= lk) break
            heap[k] = heap[c]; heapKey[k] = heapKey[c]; k = c
        }
        if (heapSize > 0) { heap[k] = last; heapKey[k] = lk }
        return top
    }

    /** Works out this frame: the dots, lines, links and names. */
    fun step() {
        frameNo++
        drawnCount = 0; linkCount = 0; nameCount = 0; heapSize = 0
        val budget = budget
        val fadeFrom = budget * 0.75
        if (stampNode(0)) { open[0] = 1.0; push(0, Double.MAX_VALUE) }
        while (heapSize > 0 && drawnCount < budget) {
            val i = pop()
            if (drawnCount > fadeFrom) open[i] *= 1 - FractalRules.smooth(fadeFrom, budget.toDouble(), drawnCount.toDouble())
            slotOf[i] = drawnCount
            drawn[drawnCount++] = i
            val count = tree.childCount[i]
            if (count > 0 && onScreen[i] > FractalRules.OPEN) {
                val ca = FractalRules.smooth(FractalRules.OPEN, FractalRules.OPEN_FULL, onScreen[i])
                val s = tree.childStart[i]
                // heaviest first, and no more than the budget can still take
                val room = budget - drawnCount - heapSize
                var pushed = 0
                for (k in 0 until count) {
                    val c = tree.children[s + k]
                    if (!isShown(c)) continue
                    if (!stampNode(c)) continue
                    if (pushed >= room) continue
                    open[c] = ca
                    push(c, onScreen[c])
                    pushed++
                }
            }
        }
        heapSize = 0
        val lhi = if (focusMix > 0.5) focus else focusFrom
        names(lhi)
        for (k in 0 until drawnCount) {
            val i = drawn[k]
            val h = hubness(i)
            var r = dotSize(i, h)
            val rho = layout.dot[i] * camZ
            val fade = if (h > 0.5) 1.0 else FractalRules.smooth(0.35, 1.25, rho)
            var a = open[i] * fade * dim(i) * passed(i)
            // no name without its mark: a named dot is at least 5 across and 0.6 strong
            if (named[k]) { r = max(r, FractalRules.NAMED_DOT); a = max(a, max(FractalRules.NAMED_ALPHA, open[i] * dim(i) * passed(i))) }
            else if (h < 0.3 && underName(sx[i], sy[i], r)) a = 0.0
            dotRadius[k] = r; dotHub[k] = h; dotAlpha[k] = if (a < 0.02) 0.0 else a
            glowAlpha[k] = if (h < 0.02) 0.0 else h * open[i] * dim(i) * passed(i)
            lineAlpha[k] = 0.0
            if (i != 0) {
                val p = tree.parent[i]
                val dx = sx[i] - sx[p]; val dy = sy[i] - sy[p]
                val la = FractalRules.LINE * FractalRules.TREE_LINE * FractalRules.smooth(2.0, 12.0, sqrt(dx * dx + dy * dy)) * open[i] * dim(i) * passed(p)
                lineAlpha[k] = if (la < 0.01) 0.0 else min(la, 1.0)
            }
        }
        links()
    }

    /** The nearest drawn node at or above [i]. */
    private fun resolve(i: Int): Int {
        var j = i
        while (stamp[j] != frameNo || (j != 0 && !drawnNow(j))) j = tree.parent[j]
        return j
    }

    private fun drawnNow(j: Int): Boolean { val k = slotOf[j]; return k < drawnCount && drawn[k] == j }

    private fun offscreen(x: Double, y: Double) = x < -20 || x > width + 20 || y < -20 || y > height + 20

    private fun links() {
        for (l in linkA.indices) {
            if (linkCount >= FractalRules.LINK_BUDGET) break
            if (!isShown(linkA[l]) || !isShown(linkB[l])) continue
            val a = resolve(linkA[l]); val b = resolve(linkB[l])
            if (a == b || tree.holds(a, b) || tree.holds(b, a)) continue
            if (offscreen(sx[a], sy[a]) && offscreen(sx[b], sy[b])) continue
            val ex = sx[b] - sx[a]; val ey = sy[b] - sy[a]
            var alpha = FractalRules.LINE * FractalRules.LINK_LINE * FractalRules.smooth(2.0, 12.0, sqrt(ex * ex + ey * ey)) *
                min(dim(a), dim(b)) * passed(a) * passed(b)
            if (linksOf >= 0) alpha *= if (linkA[l] == linksOf || linkB[l] == linksOf) FractalRules.LINKS_ON else FractalRules.LINKS_OFF
            if (alpha < 0.01) continue
            linkFrom[linkCount] = a; linkTo[linkCount] = b; linkTone[linkCount] = a; linkAlpha[linkCount] = min(alpha, 1.0)
            linkCount++
        }
    }

    // MARK: names

    private fun fits(x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        if (x0 < insetLeft + 4 || x1 > width - insetRight - 4 || y0 < insetTop + 2 || y1 > height - insetBottom - 4) return false
        for (k in 0 until nameCount) {
            val gx = if (nameNode[k] < 0) 1.0 else 8.0
            val gy = if (nameNode[k] < 0) 1.0 else 4.0
            if (x0 < nameX1[k] + gx && x1 > nameX0[k] - gx && y0 < nameY1[k] + gy && y1 > nameY0[k] - gy) return false
        }
        return true
    }

    private fun keepClear(x0: Double, y0: Double, x1: Double, y1: Double) {
        if (nameCount >= FractalRules.NAMES) return
        nameX0[nameCount] = x0; nameY0[nameCount] = y0; nameX1[nameCount] = x1; nameY1[nameCount] = y1
        nameNode[nameCount] = -1; nameCount++
    }

    private fun put(i: Int, x: Double, y: Double, x0: Double, y0: Double, x1: Double, y1: Double, size: Double, side: Int, alpha: Double): Boolean {
        if (nameCount >= FractalRules.NAMES || !fits(x0, y0, x1, y1) || !clearOfNamedDots(i, x0, y0, x1, y1)) return false
        val k = nameCount++
        nameNode[k] = i; nameX[k] = x; nameY[k] = y; nameX0[k] = x0; nameY0[k] = y0; nameX1[k] = x1; nameY1[k] = y1
        nameSize[k] = size; nameSide[k] = side; nameAlpha[k] = alpha
        return true
    }

    /**
     * No name hides a named dot: the box keeps off every dot already named, and [i]'s own dot is
     * under no name already placed (each dot taken as [FractalRules.NAMED_DOT] across, as a named one is drawn).
     */
    private fun clearOfNamedDots(i: Int, x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        val r = FractalRules.NAMED_DOT
        for (k in 0 until nameCount) {
            val j = nameNode[k]; if (j < 0 || j == i) continue
            if (x0 < sx[j] + r && x1 > sx[j] - r && y0 < sy[j] + r && y1 > sy[j] - r) return false
            if (sx[i] + r > nameX0[k] && sx[i] - r < nameX1[k] && sy[i] + r > nameY0[k] && sy[i] - r < nameY1[k]) return false
        }
        return true
    }

    /** Whether [i]'s dot shows, so a name beside it has one to name (the tablet's review: every name beside a visible dot). */
    private fun dotShows(i: Int, h: Double): Boolean {
        if (h > 0.5) return true
        return FractalRules.smooth(0.35, 1.25, layout.dot[i] * camZ) >= 0.5
    }

    private fun names(lhi: Int) {
        for (k in 0 until drawnCount) named[k] = false
        // the lit dots are kept clear of names
        for (k in 0 until drawnCount) {
            val i = drawn[k]
            if (tree.childCount[i] == 0 || open[i] < 0.5 || passed(i) < 0.5) continue
            val h = hubness(i); if (h < 0.3) continue
            val r = dotSize(i, h)
            if (r >= 4) keepClear(sx[i] - r, sy[i] - r, sx[i] + r, sy[i] + r)
        }
        val base = if (lhi > 0) tree.depth[lhi] else 0
        var cn = 0
        for (k in 0 until drawnCount) {
            val i = drawn[k]
            if (open[i] < 0.55 || !inside(lhi, i) || passed(i) < 0.5) continue
            val rel = tree.depth[i] - base
            if (rel > 2) continue
            val h = hubness(i)
            if (!dotShows(i, h)) continue
            val ps = if (i == 0) Double.MAX_VALUE else onScreen[tree.parent[i]]
            val key = if (h > 0.3) 1000 + onScreen[i] - rel * 100
                else if (ps >= FractalRules.NAME_ROOM) ps - rel * 100 + (if (tree.childCount[i] > 0) onScreen[i] * 0.01 else 0.0)
                else continue
            // insertion, largest key first, ties in draw order
            var b = cn - 1
            while (b >= 0 && candidateKey[b] < key) { candidate[b + 1] = candidate[b]; candidateKey[b + 1] = candidateKey[b]; b-- }
            candidate[b + 1] = i; candidateKey[b + 1] = key
            cn++
        }
        for (c in 0 until cn) {
            if (nameCount >= FractalRules.NAMES) break
            val i = candidate[c]
            val h = hubness(i)
            val hub = h > 0.3
            val size = if (hub) (FractalRules.NAME + FractalRules.HUB_NAME_MORE * h).roundToInt().toDouble() else FractalRules.NAME
            var alpha = if (hub) 1.0 else 0.92
            if (linksOf >= 0 && tree.kind[i] == FractalTree.IDEA && i != linksOf && !linked(i, linksOf)) alpha *= FractalRules.LINKS_OFF
            if (nameBeside(i, h, size, alpha) || !hub || size <= 13) continue
            nameBeside(i, h, 13.0, alpha)
        }
        // mark the named dots, so each is drawn whatever is near it
        for (k in 0 until nameCount) { val i = nameNode[k]; if (i >= 0 && drawnNow(i)) named[slotOf[i]] = true }
    }

    private fun linked(a: Int, b: Int): Boolean {
        for (l in linkA.indices) if ((linkA[l] == a && linkB[l] == b) || (linkA[l] == b && linkB[l] == a)) return true
        return false
    }

    /** After, before, below, then above its dot. */
    private fun nameBeside(i: Int, h: Double, points: Double, alpha: Double): Boolean {
        val size = points * textScale
        val w = nameWidth[i] * size / 100
        val ht = size * 1.25
        val r = max(dotSize(i, h), 2.5)
        val x = sx[i]; val y = sy[i]; val gx = r + FractalRules.NAME_GAP
        if (put(i, x + gx, y, x + gx, y - ht / 2, x + gx + w, y + ht / 2, size, 0, alpha)) return true
        if (put(i, x - gx, y, x - gx - w, y - ht / 2, x - gx, y + ht / 2, size, 1, alpha)) return true
        var by = y + max(r, 6.0) + 2 + ht / 2
        if (put(i, x, by, x - w / 2, by - ht / 2, x + w / 2, by + ht / 2, size, 2, alpha)) return true
        by = y - max(r, 6.0) - 2 - ht / 2
        return put(i, x, by, x - w / 2, by - ht / 2, x + w / 2, by + ht / 2, size, 2, alpha)
    }

    private fun underName(x: Double, y: Double, r: Double): Boolean {
        for (k in 0 until nameCount) if (nameNode[k] >= 0 && x + r > nameX0[k] && x - r < nameX1[k] && y + r > nameY0[k] + 2 && y - r < nameY1[k] - 2) return true
        return false
    }

    // MARK: touch

    /** The node a tap at [px], [py] lands on, or -1: a name answers anywhere in a box at least [reach] each way of its middle, then the nearest dot. */
    fun hit(px: Double, py: Double): Int {
        var bk = -1; var bd = Double.MAX_VALUE
        for (k in 0 until nameCount) {
            if (nameNode[k] < 0) continue
            val hx = max((nameX1[k] - nameX0[k]) / 2 + 4, reach); val hy = max((nameY1[k] - nameY0[k]) / 2 + 6, reach)
            val ux = abs(px - (nameX0[k] + nameX1[k]) / 2) / hx; val uy = abs(py - (nameY0[k] + nameY1[k]) / 2) / hy
            val dd = max(ux, uy)
            if (dd <= 1 && dd < bd) { bd = dd; bk = k }
        }
        if (bk >= 0) return nameNode[bk]
        var best = -1; var bs = Double.MAX_VALUE
        for (k in 0 until drawnCount) {
            val i = drawn[k]
            if (open[i] < 0.3 || dotAlpha[k] <= 0.0) continue
            val d = hypot(px - sx[i], py - sy[i])
            val thr = max(dotRadius[k] + 6, reach)
            if (d > thr) continue
            val s = d / thr - tree.depth[i] * 0.02
            if (s < bs) { bs = s; best = i }
        }
        return best
    }

    /** What a tap on [i] does: open the cluster it heads, or the one it sits in; -1 when it is open already. */
    fun clusterFor(i: Int): Int {
        val t = if (tree.childCount[i] > 0) i else tree.parent[i]
        return if (t == focus || t < 0) -1 else t
    }
}
