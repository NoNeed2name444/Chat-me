package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.LabelLink
import com.cramdown.sorta.map.LabelRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

// S05b, packet item 6: the 3D map's names at rest (MapNames.layout, which Map3DView draws), in
// LabelRoomTest's style, on made-up ideas. S03-result.md's step-4 list has two Android rule breaks:
// Performance's and Space's names collided ("Hypokalaemia" with "Loop diuretics",
// android-tablet-performance-light.png), and a Neurons link covered its name ("Unsorted · 2 notes",
// android-tablet-neurons-light.png). The words measure as a plain font would, half their size a
// character, so the boxes are known.
class MapNamesTest {
    private val metrics = object : NameMetrics {
        override fun width(label: String, size: Float) = label.length * size * 0.5f
        override fun ascent(size: Float) = -0.9f * size
        override fun descent(size: Float) = 0.25f * size
    }
    private val rules = MapNames.Rules(largest = 24f, gapX = 8f, gapY = 4f, line = 1f)

    private fun layout(nodes: List<ScreenNode>, titles: List<String>, links: IntArray = IntArray(0), linkHalf: Float = 0f,
                       held: Map<Int, Int> = emptyMap()) = MapNames.layout(nodes, titles, links, linkHalf, held, rules, metrics)

    /** No two names placed print over each other, or closer than the room the rules keep. */
    private fun clearOfEachOther(names: List<PlacedName>) {
        for ((i, a) in names.withIndex()) for (b in names.drop(i + 1)) {
            val apartX = abs(a.box.x - b.box.x) * 2 >= a.box.width + b.box.width + 2 * rules.gapX
            val apartY = abs(a.box.y - b.box.y) * 2 >= a.box.height + b.box.height + 2 * rules.gapY
            assertTrue("${a.label} and ${b.label}: ${a.box} and ${b.box}", apartX || apartY)
        }
    }

    @Test fun aNameWithRoomGoesUnderItsBodyWhereTheMapAlwaysPutIt() {
        val names = layout(listOf(ScreenNode(0, 400f, 500f, 28f)), listOf("Fix the gate latch"))
        assertEquals(1, names.size)
        val name = names.single()
        assertEquals(0, name.spot)
        assertEquals(24f, name.textSize, 0f)
        // the baseline as before: 1.4 sizes past the body's edge
        assertEquals(500f + 14f + 24f * 1.4f, name.baseline(metrics), 1e-3f)
    }

    // the Performance case: two names in a row a hairline apart read as one; the second now goes above its body
    @Test fun twoNamesInARowNeverReadAsOne() {
        val nodes = listOf(ScreenNode(0, 400f, 500f, 28f), ScreenNode(1, 580f, 500f, 28f))
        val titles = listOf("Fix the gate latch", "Buy brushes")
        // under both bodies, the names' ends would be 6 apart: inside a word's room, not the hairline's
        val a = LabelRoom.spots(400f, 500f, 28f, metrics.width(titles[0], 24f), 0.25f * 24 + 0.9f * 24, 39.8f, 12f)[0]
        val b = LabelRoom.spots(580f, 500f, 28f, metrics.width(titles[1], 24f), 0.25f * 24 + 0.9f * 24, 39.8f, 12f)[0]
        assertEquals(6f, (b.x - b.width / 2) - (a.x + a.width / 2), 1e-3f)
        assertTrue("the hairline room let both in", LabelRoom.fits(b, listOf(a), gap = 2f))
        val names = layout(nodes, titles)
        assertEquals("both are named", setOf(0, 1), names.map { it.index }.toSet())
        assertEquals("the first under its body", 0, names.first { it.index == 0 }.spot)
        assertEquals("the second above its own", 1, names.first { it.index == 1 }.spot)
        clearOfEachOther(names)
    }

    // the Neurons case: a cell's name lay on the tract leaving it; it now goes above, and the other cell's stays under it
    @Test fun aLinksBandNeverRunsUnderAName() {
        val nodes = listOf(ScreenNode(0, 500f, 300f, 140f, perUnit = 150f), ScreenNode(1, 600f, 650f, 140f, perUnit = 150f))
        val titles = listOf("To sort · 2 ideas", "Garden · 4 ideas")
        val links = intArrayOf(0, 1)
        val names = layout(nodes, titles, links, linkHalf = 0.12f)
        assertEquals(setOf(0, 1), names.map { it.index }.toSet())
        assertEquals("off the tract, above its cell", 1, names.first { it.index == 0 }.spot)
        assertEquals("the other cell's under it, clear of the tract", 0, names.first { it.index == 1 }.spot)
        // the tract's band is 0.12 world units each side, 18 pixels here
        val band = LabelLink(500f, 300f, 600f, 650f, half = 0.12f * 150f)
        for (n in names) assertFalse("${n.label} on the tract: ${n.box}", LabelRoom.crosses(n.box, band, rules.gapY))
        clearOfEachOther(names)
        // with no link between them, the first cell's name stays under it
        assertEquals(0, layout(nodes, titles).first { it.index == 0 }.spot)
    }

    // a thin link (Space, Performance) counts as a line of the rules' width: through a name it moves the name too
    @Test fun aThinLinkThroughANameMovesItToo() {
        val nodes = listOf(ScreenNode(0, 400f, 500f, 28f), ScreenNode(1, 400f, 900f, 28f))
        val names = layout(nodes, listOf("Fix the gate latch", "Buy brushes"), intArrayOf(0, 1))
        assertEquals(1, names.first { it.index == 0 }.spot)
        val line = LabelLink(400f, 500f, 400f, 900f, half = rules.line)
        for (n in names) assertFalse("${n.label} on the link: ${n.box}", LabelRoom.crosses(n.box, line, rules.gapY))
    }

    @Test fun aNameKeepsOffAnotherBodyAndWaitsWhereNoPlaceIsClear() {
        // a body just under the first's name: it goes above
        val nodes = listOf(ScreenNode(0, 400f, 500f, 28f), ScreenNode(1, 400f, 545f, 6f))
        val names = layout(nodes, listOf("Fix the gate latch", "Buy brushes"))
        assertEquals(1, names.first { it.index == 0 }.spot)
        // links every way out of the body: no place is clear, and the name waits
        val round = listOf(ScreenNode(0, 400f, 500f, 28f), ScreenNode(1, 400f, 900f, 4f), ScreenNode(2, 400f, 100f, 4f),
            ScreenNode(3, 900f, 500f, 4f), ScreenNode(4, -100f, 500f, 4f))
        val waits = layout(round, listOf("Fix the gate latch", "", "", "", ""), intArrayOf(0, 1, 0, 2, 0, 3, 0, 4))
        assertFalse(waits.any { it.index == 0 })
    }

    // a name keeps last frame's place while that is clear, so it doesn't hop as the map drifts
    @Test fun aNameKeepsTheSpotItHadWhileThatIsClear() {
        val nodes = listOf(ScreenNode(0, 400f, 500f, 28f))
        assertEquals(1, layout(nodes, listOf("Fix the gate latch"), held = mapOf(0 to 1)).single().spot)
        assertEquals(0, layout(nodes, listOf("Fix the gate latch"), held = emptyMap()).single().spot)
    }

    @Test fun onlyCrispBodiesBigEnoughAreNamedAndALongNameIsCut() {
        val names = layout(listOf(ScreenNode(0, 100f, 100f, 28f, crisp = false), ScreenNode(1, 400f, 100f, 4f),
            ScreenNode(2, 800f, 100f, 28f)), listOf("Fix the gate latch", "Buy brushes", "Plant tulips before the frost comes"))
        assertEquals(listOf(2), names.map { it.index })
        assertEquals("Plant tulips before th…", names.single().label)
        assertEquals(MapNames.CUT, names.single().label.length)
    }
}
