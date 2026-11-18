package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.FractalSight
import com.cramdown.sorta.map.FractalTree
import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelDisc
import com.cramdown.sorta.map.LabelRoom

/**
 * The 3D Fractal's names (S12), placed by the Tree's rules (TreeNames: its sizes, its budget of 380
 * tried and 120 drawn, its foot): the cluster flown in to first, then hubs, then the heavier, then the
 * bigger on screen. Each takes the first of its spots (under, above, after and before its dot, then for
 * a hub a row further under and above) that is clear of every name placed, inside the view and off the
 * chrome, and covers no named dot and no dot drawn as big as its own: a named dot, or one bigger than
 * a name's own, is never hidden by it. A name with no clear spot is not drawn, so no two names ever overlap at any angle or zoom. Only
 * a dot bright and big enough ([FractalSight.nameable]) has a name, so a name never stands without its dot.
 */
object FractalNames {
    /** The dots on screen this frame, for names and taps: the drawn ones, crisp where they may be named. */
    fun screen(sight: FractalSight, focal: Float): List<ScreenNode> {
        val out = ArrayList<ScreenNode>(sight.count)
        for (k in 0 until sight.count) out.add(ScreenNode(sight.node[k], sight.x[k], sight.y[k], 2 * sight.radius[k], sight.nameable[k],
            focal / sight.depth[k]))
        return out
    }

    fun hub(tree: FractalTree, i: Int): Boolean = i == 0 || tree.childCount[i] > 0 || tree.kind[i] != FractalTree.IDEA

    fun order(screen: List<ScreenNode>, tree: FractalTree, focus: Int): List<ScreenNode> =
        screen.filter { it.crisp && it.index in 0 until tree.count }.sortedWith(
            compareByDescending<ScreenNode> { focus !in 0 until tree.count || tree.holds(focus, it.index) }
                .thenByDescending { hub(tree, it.index) }.thenByDescending { tree.weight[it.index] }
                .thenByDescending { it.pixels }.thenBy { it.index })

    /**
     * The names to draw for [screen] in a view [width] by [height] whose top [top] and bottom [bottom]
     * pixels the chrome covers; [hubs] and [ideas] measure the two kinds' words; [held] is last frame's
     * spot by node, tried first so a name does not hop as the map turns.
     */
    fun layout(screen: List<ScreenNode>, tree: FractalTree, hubs: NameMetrics, ideas: NameMetrics, style: TreeNames.Style,
               width: Float, height: Float, top: Float, bottom: Float, held: Map<Int, Int> = emptyMap(), focus: Int = 0): List<PlacedName> {
        val placed = ArrayList<LabelBox>()
        val out = ArrayList<PlacedName>()
        val ordered = order(screen, tree, focus)
        // the dots a name keeps clear of, found by a grid, not one by one
        val dots = DotGrid(ordered, tree.count)
        for (k in 0 until minOf(ordered.size, TreeNames.BUDGET)) {
            if (out.size >= TreeNames.MOST) break
            val node = ordered[k]
            val i = node.index
            val isHub = hub(tree, i)
            // a dot a name already covers is not named: its name would stand off a hidden dot
            val disc = LabelDisc(node.x, node.y, node.pixels / 2, i)
            if (placed.any { LabelRoom.covers(it, disc, style.gapY) }) continue
            if (!isHub && node.pixels < style.leastLeaf) continue
            val metrics = if (isHub) hubs else ideas
            val size = if (isHub) style.bough else style.leaf
            val label = MapNames.label(tree.titles[i])
            if (label.isEmpty()) continue
            val w = metrics.width(label, size) + 2 * style.halo
            val h = metrics.descent(size) - metrics.ascent(size) + 2 * style.halo
            val below = node.pixels / 2 + h / 2 + style.gapY
            val around = LabelRoom.spots(node.x, node.y, node.pixels, w, h, below, style.gapX).mapIndexed { q, b -> b to (q < 2) }
            val all = if (!isHub) around else around +
                listOf(LabelBox(node.x, node.y + below + h + style.gapY, w, h) to true, LabelBox(node.x, node.y - below - h - style.gapY, w, h) to true)
            val spots: List<LabelBox?> = all.map { (b, slides) ->
                LabelRoom.inView(b, width, style.edge, slides)?.takeIf { it.y - it.height / 2 >= top && it.y + it.height / 2 <= height - bottom }
            }
            val last = held[i]
            val tried = (if (last != null && last in spots.indices) listOf(spots[last]) + spots.filterIndexed { q, _ -> q != last } else spots).filterNotNull()
            val box = tried.firstOrNull { b -> LabelRoom.fits(b, placed, style.gapX, style.gapY) && !dots.covered(b, style.gapY, disc.radius) } ?: continue
            placed.add(box)
            dots.named[i] = true
            out.add(PlacedName(i, label, size, box, spots.indexOf(box)))
        }
        return out
    }

    /** The dots that may be named, by cell of [CELL] pixels, so a box asks only the dots near it; [named] as they are. */
    private class DotGrid(nodes: List<ScreenNode>, count: Int) {
        private val cells = HashMap<Long, ArrayList<LabelDisc>>()
        private var most = 0f
        val named = BooleanArray(count)

        init {
            for (n in nodes) {
                val r = n.pixels / 2
                most = maxOf(most, r)
                cells.getOrPut(key(cell(n.x), cell(n.y))) { ArrayList() }.add(LabelDisc(n.x, n.y, r, n.index))
            }
        }

        private fun cell(v: Float) = kotlin.math.floor(v / CELL).toInt()
        private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

        /** Whether [box] comes within [gap] of a named dot or one at least [own] in radius. */
        fun covered(box: LabelBox, gap: Float, own: Float): Boolean {
            val grow = most + gap
            for (cx in cell(box.x - box.width / 2 - grow)..cell(box.x + box.width / 2 + grow))
                for (cy in cell(box.y - box.height / 2 - grow)..cell(box.y + box.height / 2 + grow))
                    if (cells[key(cx, cy)]?.any { (named[it.index] || it.radius >= own - SAME) && LabelRoom.covers(box, it, gap) } == true) return true
            return false
        }
    }

    private const val CELL = 64f
    /** Dots this close in radius (pixels) count as the same size. */
    private const val SAME = 0.5f

    /** Whether any of [names] covers a named dot on [screen], or a dot that may be named as big as its own (none should). */
    fun coversADot(names: List<PlacedName>, screen: List<ScreenNode>): Boolean {
        val named = names.map { it.index }.toSet()
        return names.any { p ->
            val own = (screen.firstOrNull { it.index == p.index }?.pixels ?: 0f) / 2
            screen.any { (it.index in named || it.crisp && it.pixels / 2 >= own - SAME) && LabelRoom.covers(p.box, LabelDisc(it.x, it.y, it.pixels / 2), 0f) }
        }
    }
}
