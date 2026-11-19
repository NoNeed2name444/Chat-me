package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelLink
import com.cramdown.sorta.map.LabelRoom
import com.cramdown.sorta.map.nest.NestNodes

/**
 * The 3D Tree's names (S11), from where its bodies are on screen. The trunk first, then branches by
 * level (shallower first), then by the leaves under each, then the nearer. A branch's name goes first
 * at its tip, on the side away from the trunk, then out past the crown on its tip's row with a short
 * leader back to the tip, then above the tip, then past the crown a row above and below; a leaf's beside it, away from the trunk; the trunk's under
 * its foot, then beside it. Flown in, or for the trunk's own branches, the old spots follow (on the
 * body, under, above, after, before, a row further under and above). On the whole tree ([focus] 0) the
 * trunk is kept clear and deeper names get only their tip spots, so the first view names the trunk,
 * its branches and only what has clear room. Each name takes the first spot clear of every name and
 * leader placed (halo and gap included), inside the view and off the chrome; a name with no clear spot
 * is not drawn. So no two names ever overlap, at any angle or zoom. The owner's review of 19 Nov ("the
 * text overlaps a lot"): a name first looks for a spot clear of every other branch's leaves too (a
 * branch here is one of the view's own: a top folder, or flown in, one growing from the part), its
 * whole name and then cut to [SHORT] characters. Only the trunk, the part flown in to and the view's
 * own branches may then go over another branch's leaves, again whole and then cut; a lesser name with
 * no clear spot is left out, so fewer names show where they do not fit. The lead's review of 19 Nov:
 * flown in, a name first looks for a spot clear of every leaf, its own branch's too, so it stands
 * beside its branch, a short leader back to the tip ([NEAR_ABOVE], [NEAR_BELOW]), halo kept, rather
 * than over the foliage. iOS's TreeNames3D is the same.
 */
object TreeNames {
    /** The sizes the names keep, in pixels. */
    class Style(
        /** A branch's name, and a leaf's. */
        val bough: Float, val leaf: Float,
        /** The halo round the words, each side. */
        val halo: Float,
        /** Room kept beside and above or below each name. */
        val gapX: Float, val gapY: Float,
        /** Room kept from either side of the view. */
        val edge: Float,
        /** A leaf drawn smaller than this across is too far to name. */
        val leastLeaf: Float,
    )

    /**
     * How far up from the view's foot the names keep: the chrome's foot ([insets]), raised to the
     * first-run card's [cover] while it is up. The cover is measured from the view's foot and already
     * holds the chrome under the card, so the two are not added (that left the first view's big
     * branches no room above the card on the emulators); the first view is framed half a target
     * higher still (Map3DView's framed cover), so the bodies it fits are always above this.
     */
    fun floor(insets: com.cramdown.sorta.map.MapInsets, cover: Float): Float = insets.coveringFoot(cover).bottom

    /** The cost guards: at most [BUDGET] bodies are tried for a name a frame, the first in the order, and at most [MOST] names drawn. */
    const val BUDGET = 380
    const val MOST = 120

    /** Flown in, a name for about every [ROOM_PER_NAME] square dp of the room, at least [FEWEST_FLOWN], at most [MOST_FLOWN]. */
    const val ROOM_PER_NAME = 18_000f
    const val FEWEST_FLOWN = 6
    const val MOST_FLOWN = 30

    /**
     * How many names a flown-in view draws (the lead's review of 18 Nov: 45 names over a wall of leaves
     * was unreadable): one for about every [ROOM_PER_NAME] square dp of the [width] by [height] pixel room
     * the chrome leaves, at [density] pixels a dp. A phone gets about 12 to 14.
     */
    fun flownCap(width: Float, height: Float, density: Float): Int {
        val d = kotlin.math.max(density, 0.1f)
        val dp2 = kotlin.math.max(width, 0f) / d * (kotlin.math.max(height, 0f) / d)
        return (dp2 / ROOM_PER_NAME).toInt().coerceIn(FEWEST_FLOWN, MOST_FLOWN)
    }

    /**
     * Whether part [i] may be named flown in to [focus] (the lead's review of 18 Nov): the part itself,
     * what grows straight from it, and the ideas further in; never a part of another branch, nor a
     * folder deeper in (its ideas speak for it).
     */
    fun nameable(nodes: NestNodes, focus: Int, i: Int): Boolean =
        focus <= 0 || i == focus || (nodes.within(focus, i) && (nodes.parent[i] == focus || !bough(nodes, i)))

    /** The spots, in the order tried: a branch's tip, past the crown, above the tip; the trunk's foot and beside it. */
    const val TIP = 0
    const val OUTSIDE = 1
    const val ABOVE_TIP = 2
    /** Past the crown a row above and below the tip's row, a leader back to the tip. */
    const val PAST_ABOVE = 3
    const val PAST_BELOW = 4
    /** Just past the tip a row above and below, a short leader back to it. */
    const val NEAR_ABOVE = 5
    const val NEAR_BELOW = 6
    /** Then past the crown and just past the tip two rows above and below: the last of a branch's tip spots. */
    const val LAST_TIP = 10
    const val FOOT = 0

    fun bough(nodes: NestNodes, i: Int): Boolean = i == 0 || nodes.childCount[i] > 0 || nodes.folder[i]

    /**
     * The order names are placed in: the part flown in to ([focus], 0 for the whole tree) and what grows
     * from it before the rest, then branches first, the shallower first, then by weight, then the nearer
     * (bigger on screen).
     */
    fun order(screen: List<ScreenNode>, nodes: NestNodes, focus: Int = 0): List<ScreenNode> =
        screen.filter { it.index in 0 until nodes.size }.sortedWith(
            compareByDescending<ScreenNode> { focus !in 0 until nodes.size || nodes.within(focus, it.index) }
                .thenByDescending { bough(nodes, it.index) }.thenBy { nodes.depth[it.index] }.thenByDescending { nodes.weight[it.index] }
                .thenByDescending { it.pixels }.thenBy { it.index })

    /**
     * The branch of the view [i] grows on: its ancestor one level under the part flown in to ([focus],
     * 0 for the whole tree, whose branches are the top folders), or [i] itself if it is no deeper. A
     * name keeps clear of every other branch's leaves.
     */
    fun owner(nodes: NestNodes, focus: Int, i: Int): Int {
        val under = (if (focus > 0) nodes.depth[focus] else 0) + 1
        var k = i
        while (nodes.depth[k] > under) k = nodes.parent[k]
        return k
    }

    /** A name cut this short when its whole name finds no clear spot. */
    const val SHORT = 12

    /**
     * The leaves on screen by cell, [cell] across, so a name's box finds the leaves under it at once:
     * [start] by cell into [items], each a [screen] position. Made once a layout.
     */
    private class Foliage(private val screen: List<ScreenNode>, nodes: NestNodes, width: Float, height: Float, private val cell: Float) {
        private val cols = kotlin.math.max(1, kotlin.math.ceil(width / cell).toInt())
        private val rows = kotlin.math.max(1, kotlin.math.ceil(height / cell).toInt())
        private val start = IntArray(cols * rows + 1)
        private val items: IntArray

        init {
            fun each(s: ScreenNode, f: (Int) -> Unit) {
                val r = s.pixels / 2
                val c0 = kotlin.math.floor((s.x - r) / cell).toInt().coerceAtLeast(0); val c1 = kotlin.math.floor((s.x + r) / cell).toInt().coerceAtMost(cols - 1)
                val r0 = kotlin.math.floor((s.y - r) / cell).toInt().coerceAtLeast(0); val r1 = kotlin.math.floor((s.y + r) / cell).toInt().coerceAtMost(rows - 1)
                for (y in r0..r1) for (x in c0..c1) f(y * cols + x)
            }
            val leaf = BooleanArray(screen.size) { !bough(nodes, screen[it].index) }
            for (j in screen.indices) if (leaf[j]) each(screen[j]) { start[it + 1]++ }
            for (k in 1..cols * rows) start[k] += start[k - 1]
            items = IntArray(start[cols * rows])
            val at = start.copyOf()
            for (j in screen.indices) if (leaf[j]) each(screen[j]) { items[at[it]++] = j }
        }

        /** Whether the box (centre [x], [y]) covers a leaf outside [owner]'s subtree, or any leaf if [all]. */
        fun covers(x: Float, y: Float, w: Float, h: Float, owner: Int, nodes: NestNodes, all: Boolean = false): Boolean {
            val c0 = kotlin.math.floor((x - w / 2) / cell).toInt().coerceAtLeast(0); val c1 = kotlin.math.floor((x + w / 2) / cell).toInt().coerceAtMost(cols - 1)
            val r0 = kotlin.math.floor((y - h / 2) / cell).toInt().coerceAtLeast(0); val r1 = kotlin.math.floor((y + h / 2) / cell).toInt().coerceAtMost(rows - 1)
            for (ry in r0..r1) for (cx in c0..c1) {
                val k = ry * cols + cx
                for (q in start[k] until start[k + 1]) {
                    val s = screen[items[q]]
                    if (!all && nodes.within(owner, s.index)) continue
                    val dx = kotlin.math.max(kotlin.math.abs(s.x - x) - w / 2, 0f); val dy = kotlin.math.max(kotlin.math.abs(s.y - y) - h / 2, 0f)
                    if (dx * dx + dy * dy < s.pixels * s.pixels / 4) return true
                }
            }
            return false
        }
    }

    /** A spot: its box's centre, whether it slides along its row to stay in, and the tip its leader runs to (NaN for none). */
    private class Spot(val x: Float, val y: Float, val slides: Boolean, val leadX: Float = Float.NaN, val leadY: Float = Float.NaN)

    /**
     * The names to draw for [screen], in a view [width] by [height] whose top [top] and bottom [bottom]
     * pixels the chrome covers. [boughs] and [leaves] measure the two kinds' words. [held] is last
     * frame's spot by node, tried first so a name does not hop as the tree turns. [focus] is the part
     * flown in to, whose names come first; 0 is the whole tree. Flown in, only what [nameable] lets
     * through is named, at most [cap] names. [clear] are the chrome's boxes over the view (the Back
     * button, the round tools): no name or leader goes under one.
     */
    fun layout(screen: List<ScreenNode>, nodes: NestNodes, boughs: NameMetrics, leaves: NameMetrics, style: Style,
               width: Float, height: Float, top: Float, bottom: Float, held: Map<Int, Int> = emptyMap(), focus: Int = 0,
               clear: List<LabelBox> = emptyList(), cap: Int = MOST): List<PlacedName> {
        // a part past the tree's end (one flown in to before the tree shrank) is the whole tree
        @Suppress("NAME_SHADOWING") val focus = if (focus < nodes.size) focus else 0
        // the chrome's boxes are taken before any name
        val placed = ArrayList<LabelBox>(clear.size + 32).apply { addAll(clear) }
        val out = ArrayList<PlacedName>()
        val most = if (focus > 0) minOf(cap, MOST) else MOST
        val whole = focus <= 0
        val trunk = screen.firstOrNull { it.index == 0 }
        // the crown's sides on screen, every body and tip
        var crownL = Float.POSITIVE_INFINITY; var crownR = Float.NEGATIVE_INFINITY
        for (n in screen) {
            crownL = minOf(crownL, n.x - n.pixels / 2); crownR = maxOf(crownR, n.x + n.pixels / 2)
            if (!n.tipX.isNaN()) { crownL = minOf(crownL, n.tipX); crownR = maxOf(crownR, n.tipX) }
        }
        val middle = trunk?.x ?: width / 2
        // what no name may cover: every leader drawn, and on the whole tree the trunk (but for its own name)
        val kept = ArrayList<LabelLink>()
        var stem: LabelLink? = null
        var footX = Float.NaN; var footY = Float.NaN
        if (trunk != null && !trunk.tipX.isNaN()) {
            // the body is ANCHOR up the trunk, so its foot is back past it from the top
            val a = com.cramdown.sorta.map.TreeShape3D.ANCHOR.toFloat()
            footX = (trunk.x - a * trunk.tipX) / (1 - a); footY = (trunk.y - a * trunk.tipY) / (1 - a)
            if (whole) stem = LabelLink(footX, footY, trunk.tipX, trunk.tipY, trunk.pixels / 2)
        }
        val foliage = Foliage(screen, nodes, width, height, 2 * style.bough)
        val ordered = order(screen, nodes, focus)
        for (k in 0 until minOf(ordered.size, BUDGET)) {
            if (out.size >= most) break
            val node = ordered[k]
            val i = node.index
            if (!nameable(nodes, focus, i)) continue
            val isBough = bough(nodes, i)
            if (!isBough && node.pixels < style.leastLeaf) continue
            val metrics = if (isBough) boughs else leaves
            val size = if (isBough) style.bough else style.leaf
            val full = MapNames.label(nodes.titles[i])
            if (full.isEmpty()) continue
            val cut = MapNames.label(nodes.titles[i], SHORT)
            val owner = owner(nodes, focus, i)
            // only the trunk, the part and the view's own branches may go over another branch's leaves
            val first = i == 0 || i == focus || owner == i
            var found: PlacedName? = null
            // flown in, first clear of every leaf (pass 0); then of another branch's (1); then, for the first, over them (2)
            search@ for (pass in (if (whole) 1 else 0)..(if (first) 2 else 1)) for (label in if (cut == full) listOf(full) else listOf(full, cut)) {
                val w = metrics.width(label, size) + 2 * style.halo
                val h = metrics.descent(size) - metrics.ascent(size) + 2 * style.halo
                val spots = spots(node, i, isBough, whole && nodes.depth[i] > 1, w, h, style, middle, crownL, crownR, footX, footY)
                val last = held[i]
                val tried = if (last != null && last in spots.indices) listOf(last) + spots.indices.filter { it != last } else spots.indices.toList()
                for (q in tried) {
                    val sp = spots[q]
                    val box = LabelRoom.inView(LabelBox(sp.x, sp.y, w, h), width, style.edge, sp.slides)
                        ?.takeIf { it.y - it.height / 2 >= top && it.y + it.height / 2 <= height - bottom } ?: continue
                    if (!LabelRoom.fits(box, placed, style.gapX, style.gapY)) continue
                    if (kept.any { LabelRoom.crosses(box, it, style.gapY) }) continue
                    if (i != 0 && stem != null && LabelRoom.crosses(box, stem, style.gapY)) continue
                    // a leader runs from the box's near side to the tip, clear of every name
                    if (!sp.leadX.isNaN() && placed.any { LabelRoom.crosses(it, lead(box, sp.leadX, sp.leadY), style.gapY) }) continue
                    if (pass < 2 && foliage.covers(box.x, box.y, box.width, box.height, owner, nodes, all = pass == 0)) continue
                    found = PlacedName(i, label, size, box, q, sp.leadX, sp.leadY)
                    break@search
                }
            }
            val name = found ?: continue
            placed.add(name.box)
            if (!name.leadX.isNaN()) kept.add(lead(name.box, name.leadX, name.leadY))
            out.add(name)
        }
        return out
    }


    /** A leader's line, from the box's side nearer the tip to the tip, kept a hair wide. */
    private fun lead(box: LabelBox, x: Float, y: Float): LabelLink {
        val near = if (x < box.x) box.x - box.width / 2 else box.x + box.width / 2
        return LabelLink(near, box.y, x, y, 1f)
    }

    /** Where [node]'s name may go, in the order tried; [tipsOnly] leaves out all but its tip's spots. */
    private fun spots(node: ScreenNode, i: Int, isBough: Boolean, tipsOnly: Boolean, w: Float, h: Float, style: Style,
                      middle: Float, crownL: Float, crownR: Float, footX: Float, footY: Float): List<Spot> {
        val out = ArrayList<Spot>(10)
        if (i == 0) {
            if (!footX.isNaN()) {
                out.add(Spot(footX, footY + style.gapY + h / 2, true))
                val beside = node.pixels / 2 + style.gapX + w / 2
                out.add(Spot(footX + beside, footY - h / 2, false)); out.add(Spot(footX - beside, footY - h / 2, false))
            }
        } else {
            val tx = if (node.tipX.isNaN()) node.x else node.tipX
            val ty = if (node.tipY.isNaN()) node.y else node.tipY
            val side = if (tx >= middle) 1f else -1f
            val reach = if (isBough) 0f else node.pixels / 2
            val tip = Spot(tx + side * (reach + style.gapX + w / 2), ty, false)
            out.add(tip)
            if (isBough) {
                val edge = if (side > 0) crownR else crownL
                val x = edge + side * (style.gapX + w / 2)
                // past the crown, on the tip's row, unless that is the tip's own spot
                out.add(if (side * (x - tip.x) > 1f) Spot(x, ty, false, tx, ty) else tip)
                out.add(Spot(tx, ty - style.gapY - h / 2, true))
                // past the crown a row above and a row below, a leader back to the tip: free space for a crowded branch
                val row = h + style.gapY
                out.add(Spot(x, ty - row, false, tx, ty)); out.add(Spot(x, ty + row, false, tx, ty))
                // just past the tip a row above and below, a short leader back to it: beside the branch, off its leaves
                val near = tx + side * (2 * style.gapX + w / 2)
                out.add(Spot(near, ty - row, false, tx, ty)); out.add(Spot(near, ty + row, false, tx, ty))
                // in a crowd, both two rows off
                out.add(Spot(x, ty - 2 * row, false, tx, ty)); out.add(Spot(x, ty + 2 * row, false, tx, ty))
                out.add(Spot(near, ty - 2 * row, false, tx, ty)); out.add(Spot(near, ty + 2 * row, false, tx, ty))
            }
        }
        if (tipsOnly) return out
        val below = node.pixels / 2 + h / 2 + style.gapY
        // under, above, after, before: the first two slide along their row to stay in, the others don't
        val around = LabelRoom.spots(node.x, node.y, node.pixels, w, h, below, style.gapX)
        if (isBough) out.add(Spot(node.x, node.y, true))
        for ((k, b) in around.withIndex()) out.add(Spot(b.x, b.y, k < 2))
        // a branch, in a crowd, a row further under and above
        if (isBough) {
            out.add(Spot(node.x, node.y + below + h + style.gapY, true)); out.add(Spot(node.x, node.y - below - h - style.gapY, true))
        }
        return out
    }

    /** Whether any two of [names]' boxes overlap (they never should). */
    fun overlaps(names: List<PlacedName>): Boolean {
        for (a in names.indices) for (b in a + 1 until names.size) {
            val p = names[a].box; val q = names[b].box
            if (kotlin.math.abs(p.x - q.x) * 2 < p.width + q.width && kotlin.math.abs(p.y - q.y) * 2 < p.height + q.height) return true
        }
        return false
    }
}

/**
 * The Tree's bodies on screen (S11): each node's body ([TreeLayout3D.anchor]) through [viewProj] into a
 * [width] by [height] view, with a branch's tip, those [filter] lets through and in front of the eye, a little past the view
 * kept so a name can slide in. A leaf's size is its own, held between the floor and ceiling it is drawn
 * with at [density] pixels a dp (TreeWeight); a branch's its thickness. [clip] is scratch.
 */
object TreeScreen {
    fun project(model: com.cramdown.sorta.map.TreeModel, viewProj: FloatArray, focal: Float, width: Int, height: Int,
                filter: com.cramdown.sorta.map.MapFilterView?, clip: FloatArray, density: Float): List<ScreenNode> {
        val l = model.layout
        val count = model.nodes.size
        val out = ArrayList<ScreenNode>(count)
        for (i in 0 until count) {
            if (filter != null && !filter.reachable(i)) continue
            com.cramdown.sorta.map.Mat4.project(viewProj, l.anchor[3 * i].toFloat(), l.anchor[3 * i + 1].toFloat(), l.anchor[3 * i + 2].toFloat(), clip)
            if (clip[3] <= 1e-4f) continue
            val sx = (clip[0] / clip[3] * 0.5f + 0.5f) * width
            val sy = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height
            if (sx < -width * 0.25f || sx > width * 1.25f || sy < -height * 0.25f || sy > height * 1.25f) continue
            val size = if (l.leaf[i] > 0) l.leaf[i] * 2 else l.r0[i] * 2
            val perUnit = focal / clip[3]
            var tx = Float.NaN; var ty = Float.NaN
            // a branch's tip: where its name goes first
            if (l.leaf[i] <= 0) {
                val t = l.length[i]
                com.cramdown.sorta.map.Mat4.project(viewProj, (l.base[3 * i] + l.dir[3 * i] * t).toFloat(),
                    (l.base[3 * i + 1] + l.dir[3 * i + 1] * t).toFloat(), (l.base[3 * i + 2] + l.dir[3 * i + 2] * t).toFloat(), clip)
                if (clip[3] > 1e-4f) { tx = (clip[0] / clip[3] * 0.5f + 0.5f) * width; ty = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height }
            }
            var pixels = (size * perUnit).toFloat()
            if (l.leaf[i] > 0) pixels = pixels.coerceIn(TreeWeight.LEAF_DP * density, TreeWeight.LEAF_MOST_DP * density)
            out.add(ScreenNode(i, sx, sy, pixels, true, perUnit, tx, ty))
        }
        return out
    }
}
