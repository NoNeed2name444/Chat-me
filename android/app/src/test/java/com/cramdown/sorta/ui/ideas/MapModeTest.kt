package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Map mode's switch and holder (S05b A4), as a person uses them, each test on a fresh store:
// the switch changes modes and back, an empty store's map shows the empty hint and the switch,
// and on the 2D look (the board, which Compose draws; Robolectric can't draw the GL map) the map
// shows a captured idea and opening it opens S05's editor, and a title edited there shows on the
// board. The "To sort n" chip opens the list as a sheet, To sort first, whose words open the editor
// and whose Choose folder files an idea through the chooser. Each of the four looks is one tap on
// the look chip and one in its menu away, the chip then naming it. A look chosen from the menu is
// kept, and a relaunch opens on Sort. iOS's UI tests ask the same. The ideas are made up
// (IdeasFixture), on a fresh store.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapModeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private fun s(id: Int, vararg args: Any) = run.context.getString(id, *args)

    @After fun close() = run.close()

    private fun shown(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun switchTo(mode: SortaMode) {
        compose.onNode(hasTestTag(mode.identifier)).performClick()
        compose.waitForIdle()
    }

    private fun capture(words: String) {
        run.field.performTextInput(words)
        run.field.performImeAction()
        compose.waitForIdle()
    }

    /** The keyboard is up after a capture: the switch is its folded disc, which a tap opens. */
    private fun openSwitch() {
        if (!shown(SortaMode.SORT.identifier)) {
            compose.onNode(hasTestTag(SortaMode.TOGGLE)).performClick()
            compose.waitForIdle()
        }
    }

    private fun lookIs2D() {
        IdeasSettings(run.context).setLook(MapLook.TWO_D)
    }

    /** Opens the look chip's menu, finds all four looks in it with [now] checked, and chooses [look]. */
    private fun chooseLook(now: MapLook, look: MapLook) {
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitForIdle()
        MapLook.entries.forEach { assertTrue("the menu's ${it.identifier}", shown(it.identifier)) }
        compose.onNode(hasTestTag(now.identifier)).assertIsSelected()
        compose.onNode(hasTestTag(look.identifier)).performClick()
        compose.waitForIdle()
    }

    private fun p(id: Int, n: Int, vararg args: Any) = run.context.resources.getQuantityString(id, n, *args)

    private fun card(words: String) = compose.onNode(hasContentDescription("$words, ", substring = true))

    @Test fun theSwitchChangesModesAndBack() {
        run.launch()
        assertTrue("Sort at launch", shown(IdeasTags.CAPTURE))
        switchTo(SortaMode.MAP)
        assertFalse("capture is Sort's", shown(IdeasTags.CAPTURE))
        assertTrue("the map's empty hint", shown(IdeasTags.MAP_EMPTY))
        switchTo(SortaMode.SORT)
        assertTrue("back in Sort", shown(IdeasTags.CAPTURE))
    }

    @Test fun anEmptyStoresMapShowsTheHintAndTheSwitch() {
        run.launch()
        switchTo(SortaMode.MAP)
        compose.onNode(hasTestTag(IdeasTags.MAP_EMPTY)).assertIsDisplayed()
        assertTrue("its words", run.shows(s(R.string.map_empty_title)) && run.shows(s(R.string.map_empty_line)))
        compose.onNode(hasTestTag(SortaMode.SORT.identifier)).assertIsDisplayed()
    }

    @Test fun theMapShowsACapturedIdeaAndOpensItInTheEditor() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        val card = card("Paint the shed green")
        card.assertIsDisplayed()
        card.performTouchInput { click() }
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
        assertFalse("the switch is not over the editor", shown(SortaMode.MAP.identifier) || shown(SortaMode.TOGGLE))
    }

    @Test fun theListChipOpensTheListAndItsWordsOpenTheEditor() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        val chip = compose.onNode(hasTestTag(MapTags.LIST_CHIP))
        chip.assertIsDisplayed()
        chip.assert(hasContentDescription(run.context.resources.getQuantityString(R.plurals.sort_title_spoken, 1, 1)))
        chip.performClick()
        compose.waitForIdle()
        assertTrue("the list sheet", shown(MapTags.LIST))
        compose.onNode(hasTestTag(IdeasTags.IDEA_LINE) and hasAnyAncestor(hasTestTag(MapTags.LIST))).performClick()
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
    }

    // Robolectric can't draw the GL map, so this starts on 2D and chooses it again from the menu,
    // where iOS's test chooses 2D from Space; MapLookWordsTest stores each look.
    @Test fun aRelaunchOpensOnSortWithTheLookKept() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        chooseLook(now = MapLook.TWO_D, look = MapLook.TWO_D)
        card("Paint the shed green").assertIsDisplayed()
        run.relaunch()
        assertTrue("Sort at every launch", shown(IdeasTags.CAPTURE))
        switchTo(SortaMode.MAP)
        card("Paint the shed green").assertIsDisplayed()
        compose.onNode(hasTestTag(MapLook.CHIP)).assert(hasContentDescription(s(R.string.look_chip_spoken, s(R.string.look_2d))))
        assertEquals(MapLook.TWO_D, IdeasSettings(run.context).look)
    }

    // S05b A4: each look is reached by a tap on the chip and one in its menu, the chip then naming
    // it. The 3D looks are drawn as Robolectric can (no GL); MapLookWordsTest stores each look.
    @Test fun eachLookIsOneTapOnTheChipAndOneInItsMenuAway() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        var now = MapLook.TWO_D
        for (look in listOf(MapLook.SPACE, MapLook.NEURON, MapLook.TWO_D, MapLook.PERFORMANCE)) {
            chooseLook(now = now, look = look)
            assertFalse("the menu closes on ${look.identifier}", MapLook.entries.any { shown(it.identifier) })
            val name = s(look.title)
            compose.onNode(hasTestTag(MapLook.CHIP)).assertIsDisplayed().assert(hasContentDescription(s(R.string.look_chip_spoken, name)))
            compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(MapLook.CHIP)), useUnmergedTree = true).assertIsDisplayed()
            assertEquals(look, IdeasSettings(run.context).look)
            now = look
        }
    }

    // S05b A4: the list sheet shows To sort first, its ideas, then the folders, and its Choose
    // folder files an idea through the chooser, the idea then gone from To sort.
    @Test fun theListSheetShowsToSortFirstAndFilesAnIdeaThroughTheChooser() {
        lookIs2D()
        IdeaStore(run.store).replaceIdeas(listOf(IdeasFixture.gate, IdeasFixture.compost), listOf(IdeasFixture.garden))
        run.launch()
        switchTo(SortaMode.MAP)
        compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
        compose.waitForIdle()
        val inList = hasAnyAncestor(hasTestTag(MapTags.LIST))
        val title = compose.onNode(hasTestTag(IdeasTags.TITLE) and inList)
        title.assert(hasContentDescription(p(R.plurals.sort_title_spoken, 1, 1)))
        val gate = compose.onNode(hasTestTag(IdeasTags.IDEA_LINE) and hasText(IdeasFixture.gate.title) and inList)
        val garden = compose.onNode(hasContentDescription(p(R.plurals.folder_row_spoken, 1, "Garden", 1)) and inList)
        val t = title.fetchSemanticsNode().boundsInRoot
        val g = gate.fetchSemanticsNode().boundsInRoot
        val f = garden.fetchSemanticsNode().boundsInRoot
        assertTrue("To sort's title, then its idea, then the folder: $t, $g, $f", t.bottom <= g.top + 0.5f && g.bottom <= f.top + 0.5f)

        compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, IdeasFixture.gate.title)) and inList).performClick()
        compose.waitForIdle()
        assertTrue("the chooser opens", run.chooserShown())
        run.inChooser("Garden").performClick()
        run.until { !run.chooserShown() }
        assertTrue("the sheet stays up", shown(MapTags.LIST))
        title.assert(hasContentDescription(p(R.plurals.sort_title_spoken, 0, 0)))
        assertEquals("gone from To sort", 0,
            compose.onAllNodes(hasTestTag(IdeasTags.IDEA_LINE) and hasText(IdeasFixture.gate.title) and inList).fetchSemanticsNodes().size)
        compose.onNode(hasContentDescription(p(R.plurals.folder_row_spoken, 2, "Garden", 2)) and inList).assertIsDisplayed()
        run.until { IdeaStore(run.store).notes.firstOrNull { it.id == IdeasFixture.gate.id }?.folderId == IdeasFixture.garden.id }
    }

    // S05b A4: an idea opened from the board, retitled in the editor and the editor closed, shows
    // its new title on the board.
    @Test fun anIdeaRetitledFromTheBoardShowsItsNewTitleThere() {
        lookIs2D()
        run.launch()
        capture("Paint the shed")
        openSwitch()
        switchTo(SortaMode.MAP)
        card("Paint the shed").performTouchInput { click() }
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
        val title = run.field(s(R.string.editor_title))
        title.performTextClearance()
        title.performTextInput("Paint the shed green")
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.waitForIdle()
        run.until { !shown(EDITOR_TAG) }
        assertFalse("back on the map, not Sort", shown(IdeasTags.CAPTURE))
        card("Paint the shed green").assertIsDisplayed()
        assertEquals("the old title is gone", 0, compose.onAllNodes(hasContentDescription("Paint the shed, ", substring = true)).fetchSemanticsNodes().size)
    }
}
