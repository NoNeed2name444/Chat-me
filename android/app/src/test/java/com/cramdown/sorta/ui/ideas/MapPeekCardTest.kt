package com.cramdown.sorta.ui.ideas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.map.MapTap
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Android's peek card on Map mode's Space (S05b A4), as iOS's UI tests ask of its card: a tap on an
// idea raises it, its Open and a second tap open the editor, its options' Delete and Choose folder
// act on the store, and a tap on empty space lets it go. Each test on a fresh store of made-up ideas
// (IdeasFixture), Sorta launched as SortaRun launches it.
//
// Robolectric can't draw GL: the device reports no OpenGL ES 3, so Map3DView shows its words for that
// and reports itself drawn at once, and IdeasSpace puts its panels up. With nothing drawn, no body is
// on screen for a touch to hit (Map3DView's hit test reads the bodies GL placed), so a tap on a body is
// handed in where that hit test hands it: Map3DView's onOpenNode, on the map's node as MapTapKey, with
// the body's index in the scene. From there it is the app's own path: IdeasSpace's NotesMap makes the
// MapBody, MapTouch.tapOpens decides, and the card is the one the screen shows. What this can't do: a
// hit test on drawn bodies, and a hold on a body (the map hears a hold only with a body hit); the empty
// tap is a real touch, judged by NotesMap's own handler.
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
        tapBody(gate.id)
        run.until { shown(EDITOR_TAG) }
        run.field(s(R.string.editor_title)).assert(hasText(gate.title))
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
}
