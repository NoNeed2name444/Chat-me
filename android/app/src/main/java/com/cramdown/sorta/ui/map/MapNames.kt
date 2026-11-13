package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelDisc
import com.cramdown.sorta.map.LabelLink
import com.cramdown.sorta.map.LabelRoom

/** How a name's words measure at a text size, in pixels: their width, and the font's ascent (negative) and descent. */
interface NameMetrics {
    fun width(label: String, size: Float): Float
    fun ascent(size: Float): Float
    fun descent(size: Float): Float
}

/** A name on the map: whose ([index]), its words, its text size, its box, and which of [LabelRoom.spots] it took. */
data class PlacedName(val index: Int, val label: String, val textSize: Float, val box: LabelBox, val spot: Int) {
    /** Where its words' baseline goes, for words drawn centred on [LabelBox.x]. */
    fun baseline(metrics: NameMetrics): Float = box.y - (metrics.ascent(textSize) + metrics.descent(textSize)) / 2
}

/**
 * The 3D map's names (Map3DView's labels), worked out from where its bodies and links are on screen.
 * A name grows with its body's size on screen, up to [Rules.largest]: no fading, only size. Biggest
 * body first, and a name keeps to LabelRoom's rule: clear of the names placed, of every link and of
 * every other body; under its body, or above, after or before it where that is taken (S03-result.md,
 * step 4's list: Performance's and Space's names collided, and a Neurons link covered its name).
 */
object MapNames {
    /** A name past this many characters is cut, with an ellipsis (S03-result.md: one cut rule is step 4's design work). */
    const val CUT = 23

    /**
     * Performance names only a map of at most this many bodies (iOS's GraphPerfLabelSpots.limit and its
     * updateLabels, GraphPerfMapView.swift:569-576): past it, the proof tour's 100,000 and any vault as
     * big, none. A layout weighs every name against every body and link on screen, on the main thread
     * whenever the bodies move; on the tour's map each main-thread frame took 4 to 16 s on CI's emulator
     * (S05i2, cde6f9a), so the main thread never idled while the map moved. Space and Neurons have
     * no such limit on iOS, so none here: they pass no [layout] limit.
     */
    const val LIMIT = 2_000

    /** The sizes the names keep, in pixels. */
    class Rules(
        /** The largest a name's words grow. */
        val largest: Float,
        /** The room kept beside a name, so two names in a row never read as one. */
        val gapX: Float,
        /** The room kept above and below a name, and round a link or a body. */
        val gapY: Float,
        /** How far a thin link's line reaches each side, where the scene gives its links no width. */
        val line: Float,
    )

    /** Counted and cut by code point, as MapNameShow.cut: an emoji across the cut stays whole (S05j 6c; iOS counts Characters). */
    fun label(title: String): String =
        if (title.codePointCount(0, title.length) > CUT) title.substring(0, title.offsetByCodePoints(0, CUT - 1)) + "…" else title

    /**
     * The names to draw for [nodes], with [titles] by node index, the scene's [links] (pairs of node
     * indices, each a band [linkHalf] world units each side) and [held], the names drawn last frame
     * by index, with the spot each took: those count [LabelRoom.HOLD] times bigger and try that spot
     * first, so a name does not hop as the map drifts. None on a map of more than [limit] bodies.
     */
    fun layout(nodes: List<ScreenNode>, titles: List<String>, links: IntArray, linkHalf: Float, held: Map<Int, Int>,
               rules: Rules, metrics: NameMetrics, limit: Int = Int.MAX_VALUE): List<PlacedName> {
        if (titles.isEmpty() || nodes.isEmpty() || titles.size > limit) return emptyList()
        val at = HashMap<Int, ScreenNode>(nodes.size * 2)
        for (n in nodes) at[n.index] = n
        // every link with both ends on screen, as its band there
        val bands = ArrayList<LabelLink>()
        var k = 0
        while (k + 1 < links.size) {
            val a = at[links[k]]; val b = at[links[k + 1]]
            k += 2
            if (a == null || b == null) continue
            val half = if (linkHalf > 0f) linkHalf * (a.perUnit + b.perUnit) / 2 else 0f
            bands.add(LabelLink(a.x, a.y, b.x, b.y, maxOf(half, rules.line)))
        }
        val discs = nodes.filter { it.pixels >= 1f }.map { LabelDisc(it.x, it.y, it.pixels / 2, it.index) }
        val placed = ArrayList<LabelBox>()
        val out = ArrayList<PlacedName>()
        val order = nodes.filter { it.crisp }.sortedByDescending { if (it.index in held) it.pixels * LabelRoom.HOLD else it.pixels }
        for (node in order) {
            val size = (node.pixels * 0.9f).coerceAtMost(rules.largest)
            if (size < 5f) continue
            val title = titles.getOrNull(node.index) ?: continue
            val label = label(title)
            val ascent = metrics.ascent(size); val descent = metrics.descent(size)
            // under the body as the map has always put it: the baseline 1.4 sizes past its edge
            val below = node.pixels / 2 + size * 1.4f + (ascent + descent) / 2
            val spots = LabelRoom.spots(node.x, node.y, node.pixels, metrics.width(label, size), descent - ascent, below, size * 0.5f)
            val last = held[node.index]
            val tried = if (last != null && last in spots.indices) listOf(spots[last]) + spots.filterIndexed { i, _ -> i != last } else spots
            val box = LabelRoom.place(tried, placed, bands, discs, rules.gapX, rules.gapY, self = node.index) ?: continue
            placed.add(box)
            out.add(PlacedName(node.index, label, size, box, spots.indexOf(box)))
        }
        return out
    }
}
