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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardTextCap
import com.cramdown.sorta.ui.ward.WardType
import androidx.compose.ui.platform.LocalDensity
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WindowShapeHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
// card, and only its own place is stored. No card is under the map's chrome. The map's text stops at AX1.
// S05m: the three at (0, 0) are drawn round it and the bell below them; a board of one kind keeps the
// bulb but drops "Idea", in TalkBack too; a long title is one line, read whole, and shown whole over
// its card while a mouse is over it or a finger rests on it; a folder's name takes two lines.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoardSpreadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dir: File

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    // a board card by its title: read alone, or followed by its kind or folder (S05m item 4)
    private fun cardIs(title: String) = hasTestTag(BOARD_CARD_TAG) and
        (hasContentDescription(title) or hasContentDescription("$title, ", substring = true))
    private fun card(title: String) = compose.onNode(cardIs(title))
    private fun spoken(title: String) = card(title).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()

    /** A Text's laid-out lines. */
    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }
    private fun titleOf(title: String) = compose.onNode(hasText(title) and hasAnyAncestor(cardIs(title)), useUnmergedTree = true)

    /**
     * A card's frame as drawn. Its node's bounds are its layout's, before the board's zoom (the
     * card's graphicsLayer, about its centre); its title, inside that layer, is drawn the card's
     * padding in from the zoomed card's edge, which gives the zoom.
     */
    private fun frame(title: String): Rect {
        // unclipped: a card at the board's edge is laid out past the window before its zoom
        val node = card(title).fetchSemanticsNode()
        val laid = Rect(node.positionInRoot, node.size.toSize())
        val words = titleOf(title).fetchSemanticsNode().positionInRoot
        val pad = (WardSpace.m * compose.activity.resources.displayMetrics.density).toFloat()
        val zoom = (laid.center.x - words.x) / (laid.width / 2 - pad)
        assertTrue("$title: the board's zoom, $zoom, is in its range", zoom in 0.29f..2.51f)
        val c = laid.center
        val w = laid.width * zoom / 2; val h = laid.height * zoom / 2
        return Rect(c.x - w, c.y - h, c.x + w, c.y + h)
    }

    private fun places(): Map<String, Pair<Double, Double>> = IdeaStore(dir).notes.associate { it.id to (it.boardX to it.boardY) }

    /** The stacked ideas on a fresh store, Sorta shown at [scale] and taken to Map mode's board. */
    private fun showBoard(scale: Float, ideas: List<Note> = IdeasFixture.stacked, folders: List<NoteFolder> = IdeasFixture.stackedFolders) {
        dir = File(compose.activity.cacheDir, "board-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(ideas, folders)
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

    // the owner, 5 Nov: Map mode's text stops at iOS's AX1; below it, the reader's own scale stands
    @Test fun theMapsTextStopsAtAx1() {
        val seen = mutableMapOf<Float, Float>()
        compose.setContent {
            for (scale in listOf(1f, 1.3f, 2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                    WardTextCap { seen[scale] = LocalDensity.current.fontScale }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1f, seen.getValue(1f), 0.001f)
        assertEquals(1.3f, seen.getValue(1.3f), 0.001f)
        assertEquals("at font scale 2.0, the map's is AX1's", WardType.accessibilityScale.toFloat(), seen.getValue(2f), 0.001f)
    }

    // S05m item 2: the three stored at (0, 0) are drawn round it, the boiler, clear, at its place
    // giving the board's (0, 0) on screen; the bell, stored 33 below, is drawn below them
    @Test fun cardsOnOnePointAreDrawnRoundItAndTheBellBelow() {
        showBoard(1f)
        val three = listOf("Seed swap list", "Oil the gate", "Ask about the allotment waiting list").map { frame(it) }
        val boiler = frame(IdeasFixture.boiler.title)
        val d = compose.activity.resources.displayMetrics.density
        // the zoom from the boiler's card: its laid-out height against its drawn one
        val zoom = boiler.height / card(IdeasFixture.boiler.title).fetchSemanticsNode().size.height
        val originX = boiler.center.x; val originY = boiler.center.y + 230 * d * zoom
        val middleX = (three.minOf { it.left } + three.maxOf { it.right }) / 2
        val middleY = (three.minOf { it.top } + three.maxOf { it.bottom }) / 2
        assertEquals("the three round (0, 0) across: $middleX vs $originX", originX, middleX, 2 * d)
        assertEquals("and down: $middleY vs $originY", originY, middleY, 2 * d)
        val bell = frame("A bell for the bike")
        assertTrue("the bell, stored below them, is drawn below them: $bell, $three", bell.top >= three.maxOf { it.bottom } - 0.5f)
        assertPlacesAsSet()
    }

    // S05m item 4: a board of ideas keeps each card's bulb and drops the word "Idea", in TalkBack
    // too: the title, then the folder where it has one
    @Test fun aBoardOfOneKindKeepsTheBulbAndDropsTheWord() {
        showBoard(1f)
        assertEquals("every card's bulb", IdeasFixture.stacked.size,
            compose.onAllNodes(hasTestTag(BOARD_KIND_TAG + "idea"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("no card says Idea", 0,
            compose.onAllNodes(hasText("Idea") and hasAnyAncestor(hasTestTag(BOARD_CARD_TAG)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("TalkBack: the title alone", "A bell for the bike", spoken("A bell for the bike"))
        assertEquals("TalkBack: the title, then the folder", "Seed swap list, Raised beds", spoken("Seed swap list"))
    }

    // S05m item 4: with a page on the board, each card says its kind, as drawn and in TalkBack
    @Test fun aBoardOfBothKindsSaysEachKind() {
        val page = Note(id = SortaRun.id(28), title = "Plot plan for spring", kind = "page", boardX = -330.0, boardY = 150.0,
            createdAt = 1_793_000_000_007L)
        showBoard(1f, IdeasFixture.stacked + page)
        assertTrue("the page's symbol", compose.onAllNodes(hasTestTag(BOARD_KIND_TAG + "page"), useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        assertEquals("an idea says Idea", "A bell for the bike, Idea", spoken("A bell for the bike"))
        assertEquals("the page says Page", "Plot plan for spring, Page", spoken("Plot plan for spring"))
        assertEquals("the kind, then the folder", "Seed swap list, Idea, Raised beds", spoken("Seed swap list"))
        assertTrue("the word drawn", compose.onAllNodes(hasText("Idea") and hasAnyAncestor(cardIs("A bell for the bike")),
            useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    // S05m item 5: the long title is one line, ended by "…", the card as wide as 20 letters of its
    // font; TalkBack reads it whole
    @Test fun aLongTitleIsOneLineAndReadWhole() {
        showBoard(1f)
        val boiler = IdeasFixture.boiler.title
        val lines = layout(titleOf(boiler))
        assertTrue("one line, cut: ${lines.lineCount}", lines.lineCount == 1 && lines.isLineEllipsized(0))
        assertEquals("TalkBack reads the whole title", boiler, spoken(boiler))
        val short = layout(titleOf("Oil the gate"))
        assertFalse("a short title is whole", short.isLineEllipsized(0))
        val d = compose.activity.resources.displayMetrics.density
        val width = card(boiler).fetchSemanticsNode().size.width / d
        assertTrue("the card is at least 150 wide and holds 20 letters, not the whole title: $width", width >= 150 - 0.5 && width < 400)
    }

    private fun peekShown() = shown(BOARD_PEEK_TAG)

    // S05m item 5: a mouse over the card shows the whole title, wrapped, above the cards; leaving hides it
    @OptIn(ExperimentalTestApi::class)
    @Test fun aMouseOverALongTitleShowsItWhole() {
        showBoard(1f)
        val boiler = IdeasFixture.boiler.title
        assertFalse("nothing over the cards at first", peekShown())
        card(boiler).performMouseInput { enter(center) }
        compose.waitForIdle()
        assertTrue("over the card: the whole title", peekShown())
        val peek = compose.onNode(hasTestTag(BOARD_PEEK_TAG))
        peek.assert(hasText(boiler))
        assertTrue("wrapped onto more lines", layout(peek).lineCount > 1)
        val shown = peek.fetchSemanticsNode().boundsInRoot
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("inside the window: $shown", shown.left >= window.left && shown.right <= window.right && shown.top >= window.top && shown.bottom <= window.bottom)
        // the mouse leaves for another card: that card's title instead; then for the tools: none
        card("Oil the gate").performMouseInput { moveTo(center) }
        compose.waitForIdle()
        compose.onNode(hasTestTag(BOARD_PEEK_TAG)).assert(hasText("Oil the gate"))
        compose.onNode(hasContentDescription("Recentre")).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        assertFalse("gone as the mouse leaves the cards", peekShown())
    }

    // S05m item 5: a finger resting shows it until a drag starts, the hold starts a connection, or
    // it lifts; and that tap still opens the idea
    @Test fun aRestingFingerShowsTheWholeTitleUntilItLifts() {
        showBoard(1f)
        val boiler = IdeasFixture.boiler.title
        // a drag starts: gone
        compose.mainClock.autoAdvance = false
        card(boiler).performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("a resting finger: the whole title", peekShown())
        card(boiler).performTouchInput { moveBy(Offset(60f, 0f)); moveBy(Offset(60f, 0f)) }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("gone as the drag starts", peekShown())
        card(boiler).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // the hold that starts a connection: gone
        compose.mainClock.autoAdvance = false
        card("Oil the gate").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("shown on the gate", peekShown())
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse("gone as the hold starts a connection", peekShown())
        card("Oil the gate").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // a tap on the picked card drops the pick
        card("Oil the gate").performTouchInput { click(center) }
        compose.waitForIdle()
        // resting, then lifted: gone, and that tap opens the idea
        val rye = "Try a rye starter"
        compose.mainClock.autoAdvance = false
        card(rye).performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("resting on the rye starter", peekShown())
        compose.mainClock.advanceTimeBy(100)
        card(rye).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("gone as it lifts", peekShown())
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("the tap opens the idea", shown(EDITOR_TAG))
    }

    // S05m item 8: a folder's name takes two lines: Raised beds whole at font scale 2.0
    @Test fun aFoldersNameIsWholeAtFontScaleTwo() {
        showBoard(2f)
        val beds = compose.onNode(hasText("Raised beds") and hasAnyAncestor(cardIs("Seed swap list")), useUnmergedTree = true)
        val lines = layout(beds)
        assertFalse("Raised beds is not cut: ${lines.lineCount} lines", lines.isLineEllipsized(lines.lineCount - 1))
        assertTrue("within two lines", lines.lineCount <= 2)
    }

    /** A folder's name too long for one line wraps to a second, still whole (S05m item 8). */
    @Test fun aLongFoldersNameTakesTwoLinesAtFontScaleTwo() {
        val name = "Raised beds by the greenhouse door"
        showBoard(2f, folders = IdeasFixture.stackedFolders.map { if (it.id == IdeasFixture.raisedBeds.id) it.copy(name = name) else it })
        val beds = compose.onNode(hasText(name) and hasAnyAncestor(cardIs("Seed swap list")), useUnmergedTree = true)
        val lines = layout(beds)
        assertEquals("two lines", 2, lines.lineCount)
        assertFalse("$name is not cut", lines.isLineEllipsized(1))
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
