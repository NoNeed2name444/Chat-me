package com.cramdown.sorta.ui.ideas

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// S05h2 repair 4: on two panes, a change made beside the map reaches it. The book changes in place
// (IdeasModel.version), so a map handed only the book is skipped when it changes; on a phone the
// banner's padding happened to redraw it, on a tablet's two panes nothing did, and Undo left the
// deleted idea off the map. Each test on a fresh store of made-up ideas (IdeasFixture), Sorta
// launched as SortaRun launches it, on Space's Universe; what the map was built from is read off
// MapTapKey's ids, as MapPeekCardTest reads it (Robolectric draws no GL).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdeasMapVersionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private val words by lazy { mapPeekWords(run.context.resources) }
    private fun s(id: Int) = run.context.getString(id)

    private val shed = IdeasFixture.shed

    @After fun close() = run.close()

    private val mapNode = SemanticsMatcher.keyIsDefined(MapTapKey)

    /** The ids of the bodies the map was last built with; empty with no map. */
    private fun onMap(): List<String> =
        compose.onAllNodes(mapNode).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(MapTapKey)?.ids.orEmpty()

    private fun onMap(id: String) = onMap().any { it.equals(id, ignoreCase = true) }

    /** Sorta on two panes, Sort beside the map, the map built on Space from the made-up ideas. */
    private fun launchOnTwoPanes() {
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        IdeaStore(run.store).replaceIdeas(IdeasFixture.notes, IdeasFixture.folders)
        run.launch()
        run.until { onMap(shed.id) }
        assertTrue("Sort's pane is beside the map", compose.onAllNodes(hasTestTag(IdeasTags.FIELD)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun undoBringsADeletedIdeaBackToTheMap() {
        launchOnTwoPanes()
        val tap = checkNotNull(compose.onAllNodes(mapNode).fetchSemanticsNodes().first().config.getOrNull(MapTapKey))
        val index = tap.ids.indexOfFirst { it.equals(shed.id, ignoreCase = true) }
        compose.runOnIdle { tap.tap(index) }
        run.until { compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription(words.more) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(words.delete) and hasAnyAncestor(hasTestTag(MapPanelTags.MENU))).performClick()
        run.until { !onMap(shed.id) && run.banner()?.contains(s(R.string.undo_deleted)) == true }
        compose.onNode(hasText(s(R.string.undo_action)) and hasClickAction()).performClick()
        run.until { IdeaStore(run.store).notes.any { it.id == shed.id } }
        // the store has it again; so must the map, rebuilt from the book as Undo left it
        run.until { onMap(shed.id) }
    }

    @Test fun anIdeaAddedInSortsPaneComesOnTheMap() {
        launchOnTwoPanes()
        val before = onMap().toSet()
        run.field.performTextInput("Sweep the porch")
        compose.waitForIdle()
        run.sortIt.performClick()
        run.until { IdeaStore(run.store).notes.any { it.title == "Sweep the porch" } }
        val added = IdeaStore(run.store).notes.first { it.title == "Sweep the porch" }.id
        run.until { onMap(added) && onMap().toSet().containsAll(before) }
    }
}
