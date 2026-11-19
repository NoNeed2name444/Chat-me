package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.FractalSight
import com.cramdown.sorta.map.FractalTree
import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelDisc
import com.cramdown.sorta.map.LabelRoom
import kotlin.math.max

/**
 * The 3D Fractal's names (S12), placed by the Tree's rules (TreeNames: its sizes, its budget of 380
 * tried and 120 drawn, its foot): the cluster flown in to first, then hubs, then the heavier, then the
 * bigger on screen. Each takes the first of its spots (under, above, after and before its dot, then for
 * a hub a row further under and above) that is clear of every name placed, inside the view and off the
 * chrome's boxes, covers no named dot and no dot drawn as big as its own (a named dot, or one bigger than
 * a name's own, is never hidden by it), and has no other dot nearer it than its own, so a name is never
 * read as a neighbour's. Where one placed name alone is in the way, it may step to another of its clear
 * spots first. A name with no such spot is not drawn, so no two names ever overlap at any angle or zoom. Only a dot bright and big enough ([FractalSight.nameable]) has a name, so a name never stands
 * without its dot. The names come from the pose alone, never from what was named before, so the same pose
 * names the same. iOS's FractalNames3D is the same.
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
     * pixels the chrome covers; [hubs] and [ideas] measure the two kinds' words; [held] is a spot by node
     * to try first (the map passes none, so names follow the pose alone); [clear] are the chrome's boxes
     * over the view (Back, the tools), which no name goes under.
     */
    fun layout(screen: List<ScreenNode>, tree: FractalTree, hubs: NameMetrics, ideas: NameMetrics, style: TreeNames.Style,
               width: Float, height: Float, top: Float, bottom: Float, held: Map<Int, Int> = emptyMap(), focus: Int = 0,
               clear: List<LabelBox> = emptyList()): List<PlacedName> {
        val placed = ArrayList<LabelBox>(clear.size + 32).apply { addAll(clear) }
        val out = ArrayList<PlacedName>()
        // each placed name's dot and spots, so one in a later name's way can step aside
        val options = ArrayList<Pair<LabelDisc, List<LabelBox?>>>()
        val ordered = order(screen, tree, focus)
        // the dots a name keeps clear of, found by a grid, not one by one
        val dots = DotGrid(ordered, tree.count) { hub(tree, it) }
        // the dots a name could be read as naming (marked)
        val near = DotGrid(screen.filter { marked(tree, it, style.leastLeaf) }, tree.count) { hub(tree, it) }
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
            // a spot that hides no hub's dot first, so a name held from the last pose gives way to a hub turned under it
            val free = tried.filter { b ->
                LabelRoom.fits(b, placed, style.gapX, style.gapY) && !dots.covered(b, style.gapY, disc.radius, i) && !near.nearer(b, disc) { ownPart(tree, i, it) }
            }
            var box = free.firstOrNull { b -> !dots.coversHub(b, style.gapY, i) } ?: free.firstOrNull()
            // no spot is clear: where one name alone is in the way, that name may step to another of its clear
            // spots (one that hides no hub, if its own hid none), so the first name's choice does not cost the next one
            if (box == null) box = stepAside(tried, disc, out, options, placed, clear.size, dots, near, tree, style)
            if (box == null) continue
            placed.add(box)
            dots.named[i] = true
            out.add(PlacedName(i, label, size, box, spots.indexOf(box)))
            options.add(disc to spots)
        }
        return out
    }

    /**
     * The first of [tried] for [disc]'s name that only one placed name is in the way of, where that name can step
     * to another of its clear spots; it is moved there ([out], and [placed] after its [chrome] boxes) and the spot
     * returned. Null where none can.
     */
    private fun stepAside(tried: List<LabelBox>, disc: LabelDisc, out: ArrayList<PlacedName>, options: List<Pair<LabelDisc, List<LabelBox?>>>,
                          placed: ArrayList<LabelBox>, chrome: Int, dots: DotGrid, near: DotGrid, tree: FractalTree,
                          style: TreeNames.Style): LabelBox? {
        val i = disc.index
        val clear = placed.subList(0, chrome)
        for (b in tried) {
            if (!LabelRoom.fits(b, clear, style.gapX, style.gapY) || dots.covered(b, style.gapY, disc.radius, i)
                || near.nearer(b, disc) { ownPart(tree, i, it) }) continue
            val hits = out.indices.filter { !LabelRoom.fits(b, listOf(out[it].box), style.gapX, style.gapY) }
            if (hits.size != 1) continue
            val j = hits[0]; val o = out[j]; val (own, spots) = options[j]
            val hid = dots.coversHub(o.box, style.gapY, o.index)
            for ((q, t) in spots.withIndex()) {
                if (t == null || q == o.spot) continue
                if (!LabelRoom.fits(t, listOf(b), style.gapX, style.gapY)) continue
                if (!out.indices.all { it == j || LabelRoom.fits(t, listOf(out[it].box), style.gapX, style.gapY) }) continue
                if (!LabelRoom.fits(t, clear, style.gapX, style.gapY) || LabelRoom.covers(t, disc, style.gapY)) continue
                if (dots.covered(t, style.gapY, own.radius, o.index) || near.nearer(t, own) { ownPart(tree, o.index, it) }) continue
                if (!hid && dots.coversHub(t, style.gapY, o.index)) continue
                out[j] = o.copy(box = t, spot = q)
                placed[chrome + j] = t
                return b
            }
        }
        return null
    }

    /** The dots that may be named, by cell of [CELL] pixels, so a box asks only the dots near it; [named] as they are. */
    private class DotGrid(nodes: List<ScreenNode>, count: Int, isHub: (Int) -> Boolean) {
        private val cells = HashMap<Long, ArrayList<LabelDisc>>()
        private var most = 0f
        val named = BooleanArray(count)
        private val hubs = BooleanArray(count) { isHub(it) }

        init {
            for (n in nodes) {
                val r = n.pixels / 2
                most = maxOf(most, r)
                cells.getOrPut(key(cell(n.x), cell(n.y))) { ArrayList() }.add(LabelDisc(n.x, n.y, r, n.index))
            }
        }

        private fun cell(v: Float) = kotlin.math.floor(v / CELL).toInt()
        private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

        /**
         * Whether [box] comes within [gap] of a named dot or one at least [own] in radius, but for the named node's
         * own ([not]): a spot stands just [gap] off its own dot, and rounding there must not reject it.
         */
        fun covered(box: LabelBox, gap: Float, own: Float, not: Int): Boolean {
            val grow = most + gap
            for (cx in cell(box.x - box.width / 2 - grow)..cell(box.x + box.width / 2 + grow))
                for (cy in cell(box.y - box.height / 2 - grow)..cell(box.y + box.height / 2 + grow))
                    if (cells[key(cx, cy)]?.any { it.index != not && (named[it.index] || it.radius >= own - SAME) && LabelRoom.covers(box, it, gap) } == true) return true
            return false
        }

        /** Whether a dot other than [own]'s comes nearer [box], edge to edge, than [own]'s does; one [part] says is its own does not count. */
        fun nearer(box: LabelBox, own: LabelDisc, part: (Int) -> Boolean): Boolean {
            val mine = apartFrom(box, own.x, own.y, own.radius)
            val grow = max(mine, 0f) + most
            for (cx in cell(box.x - box.width / 2 - grow)..cell(box.x + box.width / 2 + grow))
                for (cy in cell(box.y - box.height / 2 - grow)..cell(box.y + box.height / 2 + grow))
                    if (cells[key(cx, cy)]?.any { it.index != own.index && !part(it.index) && apartFrom(box, it.x, it.y, it.radius) < mine } == true) return true
            return false
        }

        /** Whether [box] comes within [gap] of a hub's dot other than [own]'s, of any size. */
        fun coversHub(box: LabelBox, gap: Float, own: Int): Boolean {
            val grow = most + gap
            for (cx in cell(box.x - box.width / 2 - grow)..cell(box.x + box.width / 2 + grow))
                for (cy in cell(box.y - box.height / 2 - grow)..cell(box.y + box.height / 2 + grow))
                    if (cells[key(cx, cy)]?.any { it.index != own && hubs[it.index] && LabelRoom.covers(box, it, gap) } == true) return true
            return false
        }
    }

    private const val CELL = 64f
    /** Dots this close in radius (pixels) count as the same size. */
    private const val SAME = 0.5f

    /** How far the disc at [dx], [dy] of radius [r] is from [box], edge to edge (below 0 inside it). */
    fun apartFrom(box: LabelBox, dx: Float, dy: Float, r: Float): Float {
        val ex = max(kotlin.math.abs(dx - box.x) - box.width / 2, 0f); val ey = max(kotlin.math.abs(dy - box.y) - box.height / 2, 0f)
        return kotlin.math.sqrt(ex * ex + ey * ey) - r
    }

    /**
     * The dot nearest each of [names], edge to edge, among [screen]'s [marked] ones,
     * but for the named hub's own parts ([ownPart]): each name's own, by node (-1 where none is drawn).
     * What the rule above keeps true; the tests read it.
     */
    fun nearest(names: List<PlacedName>, screen: List<ScreenNode>, tree: FractalTree, leastLeaf: Float): List<Int> = names.map { p ->
        var best = -1; var bestAt = Float.POSITIVE_INFINITY
        for (s in screen) {
            if (!marked(tree, s, leastLeaf)) continue
            if (s.index != p.index && ownPart(tree, p.index, s.index)) continue
            val at = apartFrom(p.box, s.x, s.y, s.pixels / 2)
            if (at < bestAt || (at == bestAt && s.index == p.index)) { bestAt = at; best = s.index }
        }
        best
    }

    /**
     * Whether a drawn dot is one a name could be read as naming: a hub's, of any size, or an idea's big and
     * bright enough for a name of its own ([leastLeaf] across and [ScreenNode.crisp]). A speck is not.
     */
    fun marked(tree: FractalTree, s: ScreenNode, leastLeaf: Float): Boolean =
        s.index in 0 until tree.count && (hub(tree, s.index) || (s.crisp && s.pixels >= leastLeaf))

    /**
     * Whether dot [d] is part of [i]'s own cluster, so a name of [i] beside it still reads as [i]'s: a hub's
     * name among its own parts is its cluster's. All ideas holds everything, so for it only its ideas are:
     * its name beside a folder's hub would read as that folder's.
     */
    fun ownPart(tree: FractalTree, i: Int, d: Int): Boolean = tree.holds(i, d) && (i != 0 || tree.kind[d] == FractalTree.IDEA)

    /**
     * What a tap at [x], [y] lands on first: the name whose touch target holds it (its box, at least [least]
     * pixels each way, as TalkBack's: [targets]), the nearest for its size, or null (then the dots, as iOS's
     * FractalNames3D.hit).
     */
    fun nameAt(names: List<PlacedName>, x: Float, y: Float, least: Float): Int? {
        var best: Int? = null; var bestScore = Float.MAX_VALUE
        for (p in names) {
            val score = max(kotlin.math.abs(x - p.box.x) * 2 / max(least, p.box.width), kotlin.math.abs(y - p.box.y) * 2 / max(least, p.box.height))
            if (score <= 1f && score < bestScore) { bestScore = score; best = p.index }
        }
        return best
    }

    /** One element TalkBack reads: its node, and its box (centre and size, pixels). */
    data class Target(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * What TalkBack reads (iOS's FractalNames3D.targets is the same): each name, over its words, then each
     * hub drawn in [focus] with no name, over its dot, each at least [least] pixels each way. A hub turned
     * too small or too crowded for a name is still reached.
     */
    fun targets(screen: List<ScreenNode>, names: List<PlacedName>, tree: FractalTree, focus: Int, least: Float): List<Target> {
        val out = ArrayList<Target>()
        val done = HashSet<Int>()
        for (p in names) if (p.index in 0 until tree.count && done.add(p.index))
            out.add(Target(p.index, p.box.x, p.box.y, max(least, p.box.width), max(least, p.box.height)))
        for (s in screen) {
            val i = s.index
            if (i !in 0 until tree.count || !hub(tree, i) || !(focus <= 0 || focus >= tree.count || tree.holds(focus, i)) || !done.add(i)) continue
            val side = max(least, s.pixels)
            out.add(Target(i, s.x, s.y, side, side))
        }
        return out
    }

    /** Whether a part is read as a folder ("Cardiology, folder"): every hub but All ideas, which is read as its name. */
    fun spokenAsFolder(tree: FractalTree, i: Int): Boolean = i > 0 && hub(tree, i)

    /** Whether any of [names] covers a named dot on [screen], or a dot that may be named as big as its own (none should). */
    fun coversADot(names: List<PlacedName>, screen: List<ScreenNode>): Boolean {
        val named = names.map { it.index }.toSet()
        return names.any { p ->
            val own = (screen.firstOrNull { it.index == p.index }?.pixels ?: 0f) / 2
            screen.any { (it.index in named || it.crisp && it.pixels / 2 >= own - SAME) && LabelRoom.covers(p.box, LabelDisc(it.x, it.y, it.pixels / 2), 0f) }
        }
    }
}
