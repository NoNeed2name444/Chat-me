package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WindowShapeHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

// S05c A3: the 2D board draws no card over another. On a fresh store with the places of iOS's
// made-up export (IdeasFixture.stacked: two cards 33 apart, three at (0, 0)), shown on a phone at
// font scale 1 and 2.0, no two cards' frames overlap, and the store's places are as they were set:
// the board spreads the cards on screen only. A card let go of on another settles clear of every
// card, and only its own place is stored. No card is under the map's chrome.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoardSpreadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dir: File

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun card(title: String) = compose.onNode(hasContentDescription("$title, ", substring = true))

    /**
     * A card's frame as drawn. Its node's bounds are its layout's, before the board's zoom (the
     * card's graphicsLayer, about its centre); its title, inside that layer, is drawn the card's
     * padding in from the zoomed card's edge, which gives the zoom.
     */
    private fun frame(title: String): Rect {
        // unclipped: a card at the board's edge is laid out past the window before its zoom
        val node = card(title).fetchSemanticsNode()
        val laid = Rect(node.positionInRoot, node.size.toSize())
        val words = compose.onNode(hasText(title) and hasAnyAncestor(hasContentDescription("$title, ", substring = true)),
            useUnmergedTree = true).fetchSemanticsNode().positionInRoot
        val pad = (WardSpace.m * compose.activity.resources.displayMetrics.density).toFloat()
        val zoom = (laid.center.x - words.x) / (laid.width / 2 - pad)
        assertTrue("$title: the board's zoom, $zoom, is in its range", zoom in 0.29f..2.51f)
        val c = laid.center
        val w = laid.width * zoom / 2; val h = laid.height * zoom / 2
        return Rect(c.x - w, c.y - h, c.x + w, c.y + h)
    }

    private fun places(): Map<String, Pair<Double, Double>> = IdeaStore(dir).notes.associate { it.id to (it.boardX to it.boardY) }

    /** The stacked ideas on a fresh store, Sorta shown at [scale] and taken to Map mode's board. */
    private fun showBoard(scale: Float) {
        dir = File(compose.activity.cacheDir, "board-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(IdeasFixture.stacked, emptyList())
        IdeasSettings(compose.activity).setLook(MapLook.TWO_D)
        val model = IdeasModel.open(IdeaStore(dir))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                SortaTheme(dark = false) { WindowShapeHost { IdeasView(model) } }
            }
        }
        compose.waitForIdle()
        // one screen: the switch's strip, or at the larger sizes its disc, whose tap lists the modes
        if (shown(SortaMode.MAP.identifier)) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        else {
            compose.onNode(hasTestTag(SortaMode.TOGGLE)).performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNode(hasContentDescription(compose.activity.getString(SortaMode.MAP.spoken))).performClick()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("the board is up", shown(MapLook.CHIP))
    }

    private fun assertApart(what: String) {
        val titles = IdeasFixture.stacked.map { it.title }
        val frames = titles.associateWith { frame(it) }
        for (i in titles.indices) for (j in i + 1 until titles.size) {
            val a = frames.getValue(titles[i]); val b = frames.getValue(titles[j])
            // frames that only touch along an edge don't overlap
            val over = a.intersect(b)
            assertTrue("$what: ${titles[i]} $a and ${titles[j]} $b overlap", over.width <= 0.5f || over.height <= 0.5f)
        }
    }

    /**
     * No card under the map's chrome: the look chip, the To sort chip and the mode switch (its
     * strip, or its disc). The first view fits the cards by their own sizes, which at the larger
     * font scales are far taller than the least card (S05c).
     */
    private fun assertClearOfChrome(what: String) {
        val chrome = listOf(MapLook.CHIP, MapTags.LIST_CHIP, SortaMode.TOGGLE, SortaMode.SORT.identifier, SortaMode.MAP.identifier)
            .flatMap { tag -> compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { tag to it.boundsInRoot } }
        assertTrue("$what: the look chip and the switch are found", chrome.any { it.first == MapLook.CHIP } && chrome.size >= 2)
        for (title in IdeasFixture.stacked.map { it.title }) {
            val card = frame(title)
            for ((tag, bounds) in chrome) {
                val over = card.intersect(bounds)
                assertTrue("$what: $title $card is under $tag $bounds", over.width <= 0.5f || over.height <= 0.5f)
            }
        }
    }

    private fun assertPlacesAsSet() {
        assertEquals("the store's places are as they were set",
            IdeasFixture.stacked.associate { it.id to (it.boardX to it.boardY) }, places())
    }

    @Test fun cardsOnOnePlaceAreDrawnApartAtFontScaleOne() {
        showBoard(1f)
        assertApart("font scale 1")
        assertClearOfChrome("font scale 1")
        assertPlacesAsSet()
    }

    @Test fun cardsOnOnePlaceAreDrawnApartAtFontScaleTwo() {
        showBoard(2f)
        assertApart("font scale 2.0")
        assertClearOfChrome("font scale 2.0")
        assertPlacesAsSet()
    }

    @Test fun aCardLetGoOfOnAnotherSettlesClearOfIt() {
        showBoard(1f)
        val bell = IdeasFixture.stacked[3]
        val seed = IdeasFixture.stacked[1]
        val from = frame(bell.title).center
        val onto = frame(seed.title).center
        card(bell.title).performTouchInput {
            val start = center
            swipe(start, start + Offset(onto.x - from.x, onto.y - from.y), durationMillis = 200)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertApart("after the drop")
        val now = places()
        assertNotEquals("the bell's place is where it settled", bell.boardX to bell.boardY, now.getValue(bell.id))
        IdeasFixture.stacked.filter { it.id != bell.id }.forEach {
            assertEquals("${it.title} keeps its place", it.boardX to it.boardY, now.getValue(it.id))
        }
    }
}
