package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelLink
import com.cramdown.sorta.map.LabelRoom
import com.cramdown.sorta.map.nest.NestNodes

/**
 * The 3D Tree's names (S11), from where its bodies are on screen. The trunk first, then branches by
 * level (shallower first), then by the leaves under each, then the nearer. A branch's name goes first
 * at its tip, on the side away from the trunk, then out past the crown on its tip's row with a short
 * leader back to the tip, then above the tip; a leaf's beside it, away from the trunk; the trunk's under
 * its foot, then beside it. Flown in, or for the trunk's own branches, the old spots follow (on the
 * body, under, above, after, before, a row further under and above). On the whole tree ([focus] 0) the
 * trunk is kept clear and deeper names get only their tip spots, so the first view names the trunk,
 * its branches and only what has clear room. Each name takes the first spot clear of every name and
 * leader placed (halo and gap included), inside the view and off the chrome; a name with no clear spot
 * is not drawn. So no two names ever overlap, at any angle or zoom. iOS's TreeNames3D is the same.
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

    /** The spots, in the order tried: a branch's tip, past the crown, above the tip; the trunk's foot and beside it. */
    const val TIP = 0
    const val OUTSIDE = 1
    const val ABOVE_TIP = 2
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

    /** A spot: its box's centre, whether it slides along its row to stay in, and the tip its leader runs to (NaN for none). */
    private class Spot(val x: Float, val y: Float, val slides: Boolean, val leadX: Float = Float.NaN, val leadY: Float = Float.NaN)

    /**
     * The names to draw for [screen], in a view [width] by [height] whose top [top] and bottom [bottom]
     * pixels the chrome covers. [boughs] and [leaves] measure the two kinds' words. [held] is last
     * frame's spot by node, tried first so a name does not hop as the tree turns. [focus] is the part
     * flown in to, whose names come first; 0 is the whole tree.
     */
    fun layout(screen: List<ScreenNode>, nodes: NestNodes, boughs: NameMetrics, leaves: NameMetrics, style: Style,
               width: Float, height: Float, top: Float, bottom: Float, held: Map<Int, Int> = emptyMap(), focus: Int = 0): List<PlacedName> {
        val placed = ArrayList<LabelBox>()
        val out = ArrayList<PlacedName>()
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
        val ordered = order(screen, nodes, focus)
        for (k in 0 until minOf(ordered.size, BUDGET)) {
            if (out.size >= MOST) break
            val node = ordered[k]
            val i = node.index
            val isBough = bough(nodes, i)
            if (!isBough && node.pixels < style.leastLeaf) continue
            val metrics = if (isBough) boughs else leaves
            val size = if (isBough) style.bough else style.leaf
            val label = MapNames.label(nodes.titles[i])
            if (label.isEmpty()) continue
            val w = metrics.width(label, size) + 2 * style.halo
            val h = metrics.descent(size) - metrics.ascent(size) + 2 * style.halo
            val spots = spots(node, i, isBough, whole && nodes.depth[i] > 1, w, h, style, middle, crownL, crownR, footX, footY)
            val boxes: List<LabelBox?> = spots.map { sp ->
                LabelRoom.inView(LabelBox(sp.x, sp.y, w, h), width, style.edge, sp.slides)?.takeIf { it.y - it.height / 2 >= top && it.y + it.height / 2 <= height - bottom }
            }
            val last = held[i]
            val tried = if (last != null && last in boxes.indices) listOf(last) + boxes.indices.filter { it != last } else boxes.indices.toList()
            var at = -1
            for (q in tried) {
                val box = boxes[q] ?: continue
                if (!LabelRoom.fits(box, placed, style.gapX, style.gapY)) continue
                if (kept.any { LabelRoom.crosses(box, it, style.gapY) }) continue
                if (i != 0 && stem != null && LabelRoom.crosses(box, stem, style.gapY)) continue
                val sp = spots[q]
                // a leader runs from the box's near side to the tip, clear of every name
                if (!sp.leadX.isNaN() && placed.any { LabelRoom.crosses(it, lead(box, sp), style.gapY) }) continue
                at = q; break
            }
            if (at < 0) continue
            val box = checkNotNull(boxes[at]); val sp = spots[at]
            placed.add(box)
            if (!sp.leadX.isNaN()) kept.add(lead(box, sp))
            out.add(PlacedName(i, label, size, box, at, sp.leadX, sp.leadY))
        }
        return out
    }

    /** A leader's line, from the box's side nearer the tip to the tip, kept a hair wide. */
    private fun lead(box: LabelBox, sp: Spot): LabelLink {
        val near = if (sp.leadX < box.x) box.x - box.width / 2 else box.x + box.width / 2
        return LabelLink(near, box.y, sp.leadX, sp.leadY, 1f)
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
 * kept so a name can slide in. A leaf's size is its own; a branch's its thickness. [clip] is scratch.
 */
object TreeScreen {
    fun project(model: com.cramdown.sorta.map.TreeModel, viewProj: FloatArray, focal: Float, width: Int, height: Int,
                filter: com.cramdown.sorta.map.MapFilterView?, clip: FloatArray): List<ScreenNode> {
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
            out.add(ScreenNode(i, sx, sy, (size * perUnit).toFloat(), true, perUnit, tx, ty))
        }
        return out
    }
}
