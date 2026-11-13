package com.cramdown.sorta.ui.map

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S05j repair 3: the tests' spot (MapSpot) keeps off the open peek card. Performance's first view
 * runs its points down to the foot, where its biggest one lay under the card, so a finger held
 * there landed on the card and the map heard no press.
 */
class MapSpotTest {
    // a phone, 1080 x 2400, 48 dp at 2.625 a dp; 300 pixels of chrome at the top, 200 at the foot
    private val width = 1080f
    private val height = 2400f
    private val margin = 126f
    private val high = 300f
    private val low = 200f
    // the card mapmode-chosen-performance-dark-phone.png showed
    private val card = Rect(43f, 1516f, 880f, 2080f)

    private val upper = ScreenNode(1, 540f, 900f, pixels = 20f)
    private val lower = ScreenNode(2, 675.6f, 1729.2f, pixels = 30f)

    private fun pick(nodes: List<ScreenNode>, peek: Rect?) = MapSpot.pick(nodes, width, height, high, low, margin, peek)

    @Test fun withNoCardTheBiggestBodyIsTaken() {
        assertEquals(2, pick(listOf(upper, lower), null)?.index)
    }

    @Test fun aBodyUnderTheCardIsPassedOver() {
        assertEquals(1, pick(listOf(upper, lower), card)?.index)
    }

    @Test fun aBodyWithinTheMarginOfTheCardIsPassedOver() {
        // 100 pixels above the card's top, inside its 126-pixel margin
        val near = ScreenNode(3, 540f, 1416f, pixels = 40f)
        assertEquals(1, pick(listOf(upper, near), card)?.index)
        // and one just past the margin is taken
        val clear = ScreenNode(4, 540f, 1380f, pixels = 40f)
        assertEquals(4, pick(listOf(upper, clear), card)?.index)
    }

    @Test fun theChromeAndSidesStillCount() {
        val side = ScreenNode(5, 100f, 900f, pixels = 50f)
        val top = ScreenNode(6, 540f, 400f, pixels = 50f)
        val foot = ScreenNode(7, 540f, 2100f, pixels = 50f)
        val blurred = ScreenNode(8, 540f, 1000f, pixels = 50f, crisp = false)
        assertEquals(1, pick(listOf(upper, side, top, foot, blurred), null)?.index)
    }

    @Test fun noBodyClearOfTheCardIsNone() {
        assertNull(pick(listOf(lower), card))
    }
}
