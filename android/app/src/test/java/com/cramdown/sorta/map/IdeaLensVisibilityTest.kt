package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The lenses over the map's filter (S06d A2), as iOS's IdeaLensTests' map cases: a note shows when the
// filter admits it and the lenses pass it, a folder or the home while a member shows, the Universe's
// ghosts and Performance's hubs as under a filter, and the empty line's count. MapFilterTest's bodies.
class IdeaLensVisibilityTest {
    private val N = ThemeBodyKind.NOTE
    private val F = ThemeBodyKind.FOLDER
    private val H = ThemeBodyKind.HOME

    // 0 home; 1 folder A (top), 2 folder B (top), 3 folder A1 inside A; notes:
    // 4 page in A, 5 idea in A1, 6 idea in B (unlinked), 7 page in B, 8 idea in A (moon of 4 in the Universe)
    private val kinds = arrayOf(H, F, F, F, N, N, N, N, N)
    private val pages = booleanArrayOf(false, false, false, false, true, false, false, true, false)
    private val roots = arrayOf<String?>(null, "a", "b", "a", "a", "a", "b", "b", "a")
    private val parents = intArrayOf(-1, 0, 0, 1, 1, 3, 2, 2, 4)
    private val links = intArrayOf(4, 5, 5, 7, 7, 8)
    private val ids = listOf("home", "a", "b", "a1", "n4", "n5", "n6", "n7", "n8")
    private fun bodies(universe: Boolean = false) = MapFilterBodies(kinds, pages, roots, parents, links, universe)
    private fun notes(v: MapFilterView) = (0 until v.shown.size).filter { kinds[it] == N && v.shown[it] && !v.ghost[it] }

    @Test fun aLensKeepsOnlyItsNotesAndTheirFolders() {
        val v = bodies().visibility(MapFilter.All, lens = setOf("N5", "N6"), ids = ids)
        assertEquals(listOf(5, 6), notes(v))
        assertTrue("home, A, B, A1 hold a note that shows", v.shown[0] && v.shown[1] && v.shown[2] && v.shown[3])
        assertEquals(2, v.notes)
        val onlyB = bodies().visibility(MapFilter.All, lens = setOf("N6"), ids = ids)
        assertFalse("A and A1 hold none", onlyB.shown[1] || onlyB.shown[3])
        assertArrayEquals("no link has both ends", booleanArrayOf(false, false, false), onlyB.links)
    }

    @Test fun aLensAndAFilterTogether() {
        val v = bodies().visibility(MapFilter.Pages, lens = setOf("N5", "N7", "N4"), ids = ids)
        assertEquals("pages that pass", listOf(4, 7), notes(v))
        val none = bodies().visibility(MapFilter.Folder("a"), lens = setOf("N6"), ids = ids)
        assertEquals("nothing passes both", emptyList<Int>(), notes(none))
        assertEquals(0, none.notes)
        assertFalse(none.shown.any { it })
    }

    @Test fun anEmptyLensHidesEveryNote() {
        val v = bodies().visibility(MapFilter.All, lens = emptySet(), ids = ids)
        assertFalse(v.shown.any { it })
        assertEquals(0, v.notes)
    }

    @Test fun theUniversesGhostStays() {
        val u = bodies(universe = true).visibility(MapFilter.All, lens = setOf("N8"), ids = ids)
        assertTrue("planet 4 stays faintly for its moon", u.shown[4] && u.ghost[4])
        assertEquals(listOf(8), notes(u))
    }

    @Test fun theEmptyLineCountsTheStoredNotes() {
        val stored = MapFilterNotes.of(listOf("n4", "n5", "n6"), { it == 0 }, { "a" }, listOf("n4" to "n5"))
        assertEquals(3, stored.shown(MapFilter.All))
        assertEquals(1, stored.shown(MapFilter.All, setOf("N6")))
        assertEquals(0, stored.shown(MapFilter.Pages, setOf("N6")))
        assertEquals(1, stored.shown(MapFilter.Linked, setOf("N5", "N6")))
        assertEquals(1, bodies().visibility(MapFilter.All, stored = stored, lens = setOf("N6"), ids = ids).notes)
    }

    // Performance (S05i2's P2 graph): F0 top, F1 in F0, F2 top, F3 in F2 (empty); N0 a page in F1, N1 an idea
    // in F0 linked to N2, N2 a loose idea, N3 an idea in F2
    private val perfGraph = PerfGraph(PerfInput(listOf("N0", "N1", "N2", "N3"), booleanArrayOf(true, false, false, false), intArrayOf(1, 0, -1, 2),
        listOf("F0", "F1", "F2", "F3"), intArrayOf(-1, 0, -1, 2), intArrayOf(1, 2)))

    @Test fun performancesHubsShowWhileANoteUnderThemPasses() {
        val g = perfGraph
        val links = IntArray(2 * g.linkCount) { k -> if (k % 2 == 0) g.edgeA[k / 2] else g.edgeB[k / 2] }
        val bodies = MapFilterBodies.performance(g, links)
        val sceneIds = List(g.nodeCount) { i -> if (i < g.noteCount) g.noteIds[i] else "hub$i" }
        fun shown(filter: MapFilter, lens: Set<String>): List<Boolean> {
            val v = bodies.visibility(filter, lens = lens, ids = sceneIds)
            return (0 until 4).map { v.shown[g.noteOfInput[it]] } + (0 until 4).map { v.shown[g.hubNode(g.inputOfFolder.indexOf(it))] }
        }
        val t = true; val f = false
        assertEquals("N0 alone: F1 and F0 above it", listOf(t, f, f, f, t, t, f, f), shown(MapFilter.All, setOf("N0")))
        assertEquals("N2 is loose: no hub", listOf(f, f, t, f, f, f, f, f), shown(MapFilter.All, setOf("N2")))
        assertEquals("a folder filter with a lens: the folder's hubs that hold a note that passes",
            listOf(f, t, f, f, t, f, f, f), shown(MapFilter.Folder("f0"), setOf("N1", "N3")))
    }
}
