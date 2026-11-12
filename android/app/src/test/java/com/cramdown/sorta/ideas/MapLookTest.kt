package com.cramdown.sorta.ideas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The map's looks (MapLook), as iOS's maplook suite checks them: the menu's order, the look the
// stored values make, and the theme each look draws.
class MapLookTest {
    @Test fun theOrderIsSpaceNeuron2DPerformance() {
        assertEquals(listOf(MapLook.SPACE, MapLook.NEURON, MapLook.TWO_D, MapLook.PERFORMANCE), MapLook.entries)
    }

    @Test fun theIdentifiersAreIosOnes() {
        assertEquals(listOf("look-space", "look-neuron", "look-2d", "look-performance"), MapLook.entries.map { it.identifier })
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
        MapLook.entries.filter { it != MapLook.TWO_D }.forEach { look ->
            assertEquals(look, MapLook.stored(board = false, theme = look.theme!!.rawValue))
        }
    }
}
