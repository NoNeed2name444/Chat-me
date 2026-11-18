package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelRoom
import com.cramdown.sorta.map.nest.NestNodes

/**
 * The 3D Tree's names (S11), from where its bodies are on screen. Branch names first (the trunk and
 * every part that holds others), then by the leaves under each, then the nearer; each takes the first
 * of its spots (on its branch for a branch, then under, above, after and before its body) that is clear
 * of every name placed, halo and gap included, inside the view and off the chrome; a name with no clear
 * spot is not drawn. So no two names ever overlap, at any angle or zoom. Names may cross branches and
 * leaves: the halo, the page's colour, keeps them readable there.
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

    fun bough(nodes: NestNodes, i: Int): Boolean = i == 0 || nodes.childCount[i] > 0 || nodes.folder[i]

    /**
     * The order names are placed in: the part flown in to ([focus], 0 for the whole tree) and what grows
     * from it before the rest, then branches first, then by weight, then the nearer (bigger on screen).
     */
    fun order(screen: List<ScreenNode>, nodes: NestNodes, focus: Int = 0): List<ScreenNode> =
        screen.filter { it.index in 0 until nodes.size }.sortedWith(
            compareByDescending<ScreenNode> { focus !in 0 until nodes.size || nodes.within(focus, it.index) }
                .thenByDescending { bough(nodes, it.index) }.thenByDescending { nodes.weight[it.index] }
                .thenByDescending { it.pixels }.thenBy { it.index })

    /**
     * The names to draw for [screen], in a view [width] by [height] whose top [top] and bottom [bottom]
     * pixels the chrome covers. [boughs] and [leaves] measure the two kinds' words. [held] is last
     * frame's spot by node, tried first so a name does not hop as the tree turns. [focus] is the part
     * flown in to, whose names come first.
     */
    fun layout(screen: List<ScreenNode>, nodes: NestNodes, boughs: NameMetrics, leaves: NameMetrics, style: Style,
               width: Float, height: Float, top: Float, bottom: Float, held: Map<Int, Int> = emptyMap(), focus: Int = 0): List<PlacedName> {
        val placed = ArrayList<LabelBox>()
        val out = ArrayList<PlacedName>()
        for (node in order(screen, nodes, focus)) {
            val i = node.index
            val isBough = bough(nodes, i)
            if (!isBough && node.pixels < style.leastLeaf) continue
            val metrics = if (isBough) boughs else leaves
            val size = if (isBough) style.bough else style.leaf
            val label = MapNames.label(nodes.titles[i])
            if (label.isEmpty()) continue
            val w = metrics.width(label, size) + 2 * style.halo
            val h = metrics.descent(size) - metrics.ascent(size) + 2 * style.halo
            val below = node.pixels / 2 + h / 2 + style.gapY
            val around = LabelRoom.spots(node.x, node.y, node.pixels, w, h, below, style.gapX)
            val all = if (isBough) listOf(LabelBox(node.x, node.y, w, h)) + around else around
            val spots: List<LabelBox?> = all.mapIndexed { k, b ->
                LabelRoom.inView(b, width, style.edge, slides = k < all.size - 2)?.takeIf { it.y - it.height / 2 >= top && it.y + it.height / 2 <= height - bottom }
            }
            val last = held[i]
            val tried = (if (last != null && last in spots.indices) listOf(spots[last]) + spots.filterIndexed { k, _ -> k != last } else spots).filterNotNull()
            val box = LabelRoom.place(tried, placed, emptyList(), emptyList(), style.gapX, style.gapY) ?: continue
            placed.add(box)
            out.add(PlacedName(i, label, size, box, spots.indexOf(box)))
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
 * [width] by [height] view, those [filter] lets through and in front of the eye, a little past the view
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
            out.add(ScreenNode(i, sx, sy, (size * focal / clip[3]).toFloat(), true, focal / clip[3]))
        }
        return out
    }
}
