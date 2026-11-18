package com.cramdown.sorta.ideas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The map's looks (MapLook), as iOS's maplook suite checks them: the menu's order, the look the
// stored values make, and the theme each look draws.
class MapLookTest {
    @Test fun theOrderIsSpaceNeuronTree2DPerformance() {
        assertEquals(listOf(MapLook.SPACE, MapLook.NEURON, MapLook.TREE, MapLook.TWO_D, MapLook.PERFORMANCE), MapLook.entries)
    }

    @Test fun theIdentifiersAreIosOnes() {
        assertEquals(listOf("look-space", "look-neuron", "look-tree", "look-2d", "look-performance"), MapLook.entries.map { it.identifier })
        assertEquals("map-look", MapLook.CHIP)
        assertEquals("vignette.ideas.mapBoard", MapLook.BOARD_KEY)
    }

    @Test fun nothingStoredIsSpace() {
        assertEquals(MapLook.SPACE, MapLook.stored(board = false, theme = null))
    }

    @Test fun theBoardIs2DWhateverTheTheme() {
        listOf(null, "space", "neurons", "performance").forEach { assertEquals(MapLook.TWO_D, MapLook.stored(board = true, theme = it)) }
    }

    @Test fun eachThemeIsItsLookAndAnUnknownOneTheStandard() {
        assertEquals(listOf(MapLook.SPACE, MapLook.NEURON, MapLook.PERFORMANCE),
            GraphTheme.entries.map { MapLook.stored(board = false, theme = it.rawValue) })
        assertEquals(MapLook.SPACE, MapLook.stored(board = false, theme = "board"))
    }

    @Test fun eachLookDrawsItsThemeAnd2DNone() {
        assertNull(MapLook.TWO_D.theme)
        assertNull(MapLook.TREE.theme)
        MapLook.entries.filter { it != MapLook.TWO_D && it != MapLook.TREE }.forEach { look ->
            assertEquals(look, MapLook.stored(board = false, theme = look.theme!!.rawValue))
        }
    }

    // S11: the Tree is kept under its own key; the board wins over it, and anything else there is no look
    @Test fun aStoredTreeIsTheTreeUnlessTheBoardIsChosen() {
        assertEquals("vignette.ideas.mapFlat", MapLook.FLAT_KEY)
        listOf(null, "space", "neurons", "performance").forEach { assertEquals(MapLook.TREE, MapLook.stored(board = false, theme = it, flat = "tree")) }
        assertEquals(MapLook.TWO_D, MapLook.stored(board = true, theme = "space", flat = "tree"))
        listOf("", "twoD", "space", "Tree", "fern").forEach { assertEquals(MapLook.SPACE, MapLook.stored(board = false, theme = null, flat = it)) }
        assertEquals(MapLook.TREE, MapLook.flatOf("tree"))
        assertNull(MapLook.flatOf("twoD"))
    }
}
