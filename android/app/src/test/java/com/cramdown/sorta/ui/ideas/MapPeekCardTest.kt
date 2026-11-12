package com.cramdown.sorta.ui.ideas

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.activity.compose.setContent
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.ideas.rawValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTap
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

// Android's peek card on Map mode's Space (S05b A4, S05g), as iOS's UI tests ask of its card: a tap on
// an idea raises it, its Open and a second tap open the editor (a second tap within the double tap's
// 0.30 s is the double tap's own and opens nothing by itself), its Show links turns to All links and
// back, its options' Delete, Choose folder and Rename (through the naming sheet
// MapPanelsTouchTargetTest measures) act on the store, and a tap on empty space lets it go. Each test
// on a fresh store of made-up ideas (IdeasFixture), Sorta launched as SortaRun launches it.
//
// Robolectric can't draw GL: the device reports no OpenGL ES 3, so Map3DView shows its words for that
// and reports itself drawn at once, and IdeasSpace puts its panels up. With nothing drawn, no body is
// on screen for a touch to hit (Map3DView's hit test reads the bodies GL placed), so a tap on a body is
// handed in where that hit test hands it: Map3DView's onTap, on the map's node as MapTapKey, with the
// body's index in the scene. From there it is the app's own path: IdeasSpace's NotesMap makes the
// MapBody, MapSelection decides at the clock's time (SystemClock.uptimeMillis, which these tests move
// on where a second tap must come after the double tap's gap), and the card is the one the screen
// shows. A rename, then Show links, builds the map again with the idea still chosen: the map's
// MapChoiceCue (on MapTapKey too) counts no new tick, pop or ease for it, and the camera stays. What this can't do: a hit test on drawn bodies, and a hold on a body (the map hears a hold
// only with a body hit); the empty tap is a real touch, judged by Map3DView's own gesture, which
// stands under the no-GL words too.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapPeekCardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private val words by lazy { mapPeekWords(run.context.resources) }
    private fun s(id: Int, vararg args: Any) = run.context.getString(id, *args)

    private val gate = IdeasFixture.gate
    private val garden = IdeasFixture.garden

    @After fun close() = run.close()

    private fun shown(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun stored() = IdeaStore(run.store).notes

    /** Sorta on a store holding one idea in To sort and the Garden folder, in Map mode on Space, the map built. */
    private fun launchOnSpace() {
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        IdeaStore(run.store).replaceIdeas(listOf(gate), listOf(garden))
        run.launch()
        compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        // laid out off the main thread, then drawn (at once, with no GL) and its panels up
        run.until { tapper() != null && shown(MapPanelTags.HINT) }
    }

    private val mapNode = SemanticsMatcher.keyIsDefined(MapTapKey)

    private fun tapper(): MapTap? = compose.onAllNodes(mapNode).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(MapTapKey)

    /** A tap on [id]'s body, handed in as Map3DView's hit test hands it on. */
    private fun tapBody(id: String) {
        val tap = checkNotNull(tapper()) { "no map" }
        val index = tap.ids.indexOfFirst { it.equals(id, ignoreCase = true) }
        assertTrue("$id is on the map: ${tap.ids}", index >= 0)
        compose.runOnIdle { tap.tap(index) }
        compose.waitForIdle()
    }

    private fun cardFor(title: String) {
        run.until { shown(MapPanelTags.PEEK) }
        compose.onNode(hasTestTag(MapPanelTags.PEEK_TITLE), useUnmergedTree = true).assert(hasText(title))
        compose.onNode(hasTestTag(MapPanelTags.PEEK)).assertIsDisplayed()
    }

    /** The card's More, then [item] in the options it opens. */
    private fun option(item: String) {
        compose.onNode(hasContentDescription(words.more) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(item) and hasAnyAncestor(hasTestTag(MapPanelTags.MENU))).performClick()
        compose.waitForIdle()
    }

    @Test fun aTapRaisesTheCardAndItsOpenOpensTheEditor() {
        launchOnSpace()
        assertFalse("no card before a tap", shown(MapPanelTags.PEEK))
        tapBody(gate.id)
        cardFor(gate.title)
        assertFalse("the first-run card steps aside", shown(MapPanelTags.HINT))
        compose.onNode(hasTestTag(MapPanelTags.PEEK_OPEN)).performClick()
        run.until { shown(EDITOR_TAG) }
        run.field(s(R.string.editor_title)).assert(hasText(gate.title))
    }

    @Test fun aSecondTapOnTheIdeaOpensIt() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        // past the double tap's gap: a tap of its own on the chosen idea
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        tapBody(gate.id)
        run.until { shown(EDITOR_TAG) }
        run.field(s(R.string.editor_title)).assert(hasText(gate.title))
    }

    @Test fun aQuickSecondTapIsTheDoubleTapsOwnAndOpensNothingByItself() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        tapBody(gate.id)
        compose.waitForIdle()
        assertFalse("no editor from the double tap's second tap", shown(EDITOR_TAG))
        cardFor(gate.title)
    }

    @Test fun itsShowLinksTurnsToAllLinksAndBack() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        val links = hasTestTag(MapPanelTags.PEEK_LINKS)
        compose.onNode(links).assert(hasText(words.showLinks))
        compose.onNode(links).performClick()
        compose.waitForIdle()
        compose.onNode(links).assert(hasText(words.allLinks))
        compose.onNode(links).performClick()
        compose.waitForIdle()
        compose.onNode(links).assert(hasText(words.showLinks))
        cardFor(gate.title)
    }

    // S05j 7c: Show links draws Material's Hub, a node graph as iOS's point.3.connected.trianglepath.dotted,
    // not Link's chain; the legend's Links row draws the same
    @Test fun showLinksDrawsTheNodeGraph() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        val hub = androidx.compose.material.icons.Icons.Rounded.Hub
        assertEquals("Rounded.Hub", hub.name)
        compose.onNode(hasTestTag(MapPanelTags.glyph(hub)), useUnmergedTree = true).assertExists()
        assertTrue("no chain", compose.onAllNodes(hasTestTag("glyph:Rounded.Link"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertSame(hub, legendGlyph("point.3.connected.trianglepath.dotted"))
    }

    // S05j 8b: the link chips' row reaches WardSpace.s into the card's margin each side, its chips padded
    // back, so the last chip scrolls whole to the row's end, not cut at the card's padding (iOS's
    // GraphMapPanels.swift:121-147)
    @Test fun theChipRowReachesIntoTheCardsMargin() {
        val links = (1..6).map { "id$it" to "A linked idea number $it" }
        val scenario = androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val peekWords = mapPeekWords(activity.resources)
                activity.setContent {
                    com.cramdown.sorta.ui.SortaTheme(dark = true) {
                        MapPeekCard(com.cramdown.sorta.ideas.MapPeek.content(com.cramdown.sorta.ideas.MapPeekInput("space", "gasGiant",
                            folder = false, title = "Oil the gate", body = "A line.", links = links), peekWords),
                            folder = false, words = peekWords, open = {}, flyIn = {}, chip = {}, option = {})
                    }
                }
            }
            compose.waitForIdle()
            val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
            val s = com.cramdown.sorta.ui.ward.WardSpace.s.toFloat() * density
            val pad = com.cramdown.sorta.ui.ward.WardFaceMetrics.cardPadding.toFloat() * density
            val card = compose.onNode(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNode().boundsInRoot
            val row = compose.onNode(hasTestTag(MapPanelTags.PEEK_CHIPS), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("the row's start, s into the margin", card.left + pad - s, row.left, 1.5f)
            assertEquals("the row's end, s into the margin", card.right - pad + s, row.right, 1.5f)
            val first = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK_CHIPS)) and androidx.compose.ui.test.hasAnyDescendant(hasText("A linked idea number 1")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("the first chip padded back to the card's padding", card.left + pad, first.left, 1.5f)
            compose.onNode(hasTestTag(MapPanelTags.PEEK_CHIPS), useUnmergedTree = true).performScrollToNode(androidx.compose.ui.test.hasAnyDescendant(hasText("A linked idea number 6")))
            compose.waitForIdle()
            val last = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK_CHIPS)) and androidx.compose.ui.test.hasAnyDescendant(hasText("A linked idea number 6")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("the last chip whole: ${last.left}..${last.right} in ${row.left}..${row.right}",
                last.left >= row.left - 0.5f && last.right <= row.right + 0.5f)
            assertTrue("and inside the card", last.right <= card.right + 0.5f)
        } finally {
            scenario.close()
        }
    }

    @Test fun itsDeleteDeletesTheIdeaFromTheStore() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        option(words.delete)
        run.until { stored().none { it.id == gate.id } }
        assertFalse("the card goes", shown(MapPanelTags.PEEK))
        run.until { shown(IdeasTags.MAP_EMPTY) }
        run.until { run.banner()?.contains(s(R.string.undo_deleted)) == true }
    }

    @Test fun itsChooseFolderFilesTheIdeaInTheStore() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        option(words.moveToFolder)
        assertTrue("the chooser opens", run.chooserShown())
        run.inChooser(garden.name).performClick()
        run.until { !run.chooserShown() }
        run.until { stored().firstOrNull { it.id == gate.id }?.folderId == garden.id }
    }

    @Test fun aTapOnEmptySpaceLetsTheCardGo() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        compose.onNode(mapNode).performTouchInput { click(Offset(centerX, height * 0.3f)) }  // above the card, under the top strip
        run.until { !shown(MapPanelTags.PEEK) }
        assertTrue("the idea stays", stored().any { it.id == gate.id })
    }

    @Test fun itsRenameRenamesTheIdeaThroughTheNamingSheet() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        option(words.rename)
        run.until { shown(NAME_SHEET_TAG) }
        val field = run.field(s(R.string.map_rename_idea_field))
        field.assert(hasText(gate.title))
        field.performTextClearance()
        field.performTextInput("Oil the gate latch")
        compose.onNode(hasText(s(R.string.name_rename)) and hasAnyAncestor(hasTestTag(NAME_SHEET_TAG))).performClick()
        run.until { !shown(NAME_SHEET_TAG) }
        run.until { stored().firstOrNull { it.id == gate.id }?.title == "Oil the gate latch" }
    }

    @Test fun aRebuildWithTheSameChoiceNeitherTicksEasesNorPops() {
        launchOnSpace()
        tapBody(gate.id)
        cardFor(gate.title)
        // the Select: one tick (and the pop the renderer is cued for with it) and one ease
        run.until { tapper()?.cue?.let { it.ticks == 1 && it.eases == 1 } == true }
        val first = checkNotNull(tapper())
        val cue = checkNotNull(first.cue)
        // renamed through its options: the store changes and the map is built again, the idea still chosen
        option(words.rename)
        run.until { shown(NAME_SHEET_TAG) }
        val field = run.field(s(R.string.map_rename_idea_field))
        field.performTextClearance()
        field.performTextInput("Oil the gate latch")
        compose.onNode(hasText(s(R.string.name_rename)) and hasAnyAncestor(hasTestTag(NAME_SHEET_TAG))).performClick()
        cardFor("Oil the gate latch")
        // the map is built again with the new title at its next composition (here Show links'), the idea still chosen
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).performClick()
        run.until { tapper()?.let { it.build != first.build } == true }
        cardFor("Oil the gate latch")
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).assert(hasText(words.allLinks))
        val rebuilt = checkNotNull(tapper())
        assertSame("one cue for the map, kept over its scenes", cue, rebuilt.cue)
        val pose = drawnPose()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("no tick and no pop for the same choice", 1, cue.ticks)
        assertEquals("no ease for the same choice", 1, cue.eases)
        assertEquals("the camera stays", pose, drawnPose())
        // a new choice still cues: empty space lets it go, a tap chooses it again
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        compose.onNode(mapNode).performTouchInput { click(Offset(centerX, height * 0.3f)) }
        run.until { !shown(MapPanelTags.PEEK) }
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        tapBody(gate.id)
        cardFor("Oil the gate latch")
        run.until { cue.ticks == 2 && cue.eases == 2 }
    }

    @Test fun aNewLookChoosesItsOwnBodies() {
        // Space first, then Neuron from the look chip, as a person goes: the tap is handed the new
        // look's bodies, so a cell chooses its folder, never the Space idea at the same index
        val shed = IdeasFixture.shed
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        IdeaStore(run.store).replaceIdeas(listOf(gate, shed), listOf(garden))
        run.launch()
        compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        run.until { tapper() != null && shown(MapPanelTags.HINT) }
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        run.until { shown(MapLook.NEURON.identifier) }
        compose.onNode(hasTestTag(MapLook.NEURON.identifier)).performClick()
        run.until { tapper()?.ids?.any { it.equals(garden.id, ignoreCase = true) } == true }
        tapBody(garden.id)
        cardFor(garden.name)
    }

    @Test fun aNeuronCellsCardFliesInAndAnIdeaInsideHasShowLinks() {
        // a cell's card is a folder's, as iOS's (GraphTouchModel content): Open folder and Fly in, no
        // Show links; flown into, the cell's ideas are on the map, and an idea with a link has Show links
        val shed = IdeasFixture.shed
        IdeasSettings(run.context).setLook(MapLook.NEURON)
        IdeaStore(run.store).replaceIdeas(listOf(gate, shed), listOf(garden))
        run.launch()
        compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        run.until { tapper()?.ids?.any { it.equals(garden.id, ignoreCase = true) } == true }
        tapBody(garden.id)
        cardFor(garden.name)
        assertFalse("a cell's card has no Show links", shown(MapPanelTags.PEEK_LINKS))
        val flyIn = hasText(s(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        compose.onNode(flyIn).performClick()
        run.until { tapper()?.ids?.any { it.equals(shed.id, ignoreCase = true) } == true }
        tapBody(shed.id)
        cardFor(shed.title)
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).assert(hasText(words.showLinks)).performClick()
        compose.waitForIdle()
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).assert(hasText(words.allLinks))
    }

    @Test fun aUniverseFoldersCardFliesInAndOneSingleStyleHasNoFolders() {
        // S05h: under Space's default look the Garden folder is a black hole on the map, its card a
        // folder's with Open folder and Fly in (GraphMapPanels.swift:155-157); Fly in keeps the card
        launchOnSpace()
        tapBody(garden.id)
        cardFor(garden.name)
        assertFalse("a black hole's card has no Show links", shown(MapPanelTags.PEEK_LINKS))
        compose.onNode(hasTestTag(MapPanelTags.PEEK_OPEN)).assert(hasText(words.openFolder))
        val flyIn = hasText(s(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        compose.onNode(flyIn).performClick()
        compose.waitForIdle()
        cardFor(garden.name)
        // an idea's card on the Universe: Show links, never Fly in
        tapBody(gate.id)
        cardFor(gate.title)
        assertFalse("an idea's card has no Fly in", compose.onAllNodes(flyIn).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun underOneSingleStyleNoCardFliesIn() {
        // Look › Bodies › one style: today's Space, one body per idea and no folder bodies, so no card has Fly in
        IdeasSettings(run.context).setNodeStyle(SpaceStyle.SUN.rawValue)
        launchOnSpace()
        assertFalse("no folder on the map", checkNotNull(tapper()).ids.any { it.equals(garden.id, ignoreCase = true) })
        tapBody(gate.id)
        cardFor(gate.title)
        val flyIn = hasText(s(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        assertFalse("no Fly in", compose.onAllNodes(flyIn).fetchSemanticsNodes().isNotEmpty())
    }

    private fun drawnPose(): List<Float> =
        checkNotNull(compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(MapDrawnKey)).pose
}
