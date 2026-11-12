package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

// The Link length is the layout's rest length (S05b task 4, A5): the standard length lays the map
// out as it always has, and a longer one sets linked notes further apart, a shorter one nearer.
class MapRestLengthTest {
    private class N(override val id: String, override val title: String = id, override val links: List<String> = emptyList(),
                    override val tags: List<String> = emptyList()) : NoteLike

    /** A ring of six linked notes and two loose ones. */
    private val notes = (0 until 6).map { N("r$it", links = listOf("r${(it + 1) % 6}")) } + N("loose1") + N("loose2")

    private fun meanLink(g: MapGraph): Float {
        var sum = 0f
        for (e in 0 until g.edgeCount) {
            val i = g.edges[2 * e]; val j = g.edges[2 * e + 1]
            val dx = g.positions[3 * i] - g.positions[3 * j]; val dy = g.positions[3 * i + 1] - g.positions[3 * j + 1]
            val dz = g.positions[3 * i + 2] - g.positions[3 * j + 2]
            sum += sqrt(dx * dx + dy * dy + dz * dz)
        }
        return sum / g.edgeCount
    }

    @Test fun theStandardLengthLaysOutAsBefore() {
        val g = MapGraph.fromNotes(notes)
        assertArrayEquals(g.positions, MapGraph.fromNotes(notes, rest = MapGraph.REST).positions, 0f)
        assertArrayEquals(MapGraph.layout(8, g.edges), MapGraph.layout(8, g.edges, rest = 2f), 0f)
    }

    @Test fun aLongerLengthSetsLinkedNotesFurtherApart() {
        val short = meanLink(MapGraph.fromNotes(notes, rest = MapGraph.REST * 0.6f))
        val standard = meanLink(MapGraph.fromNotes(notes))
        val long = meanLink(MapGraph.fromNotes(notes, rest = MapGraph.REST * 1.8f))
        assertTrue("shorter $short < standard $standard < longer $long", short < standard && standard < long)
    }
}
