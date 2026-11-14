package com.cramdown.sorta.ui.ideas

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.cramdown.sorta.MainActivity
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.SortaRun.Companion.id
import com.cramdown.sorta.ui.map.MapTap
import com.cramdown.sorta.ui.map.MapTapKey
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// S06b's panels on Android, through the app's own paths (Robolectric draws no GL, so the panels show
// over Map3DView's no-GL words and a tap on a body is handed in where its hit test hands it, as
// MapPeekCardTest does): a folder's words sheet from the row's Folder words action, a leaning idea's
// card with File there, the moons on the 2D board, a share from another app saved with its source,
// and the editor's source mark. Each is kept as build/outputs/roborazzi/sky_<what>.png. Made-up ideas only.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkyChromeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private var shared: ActivityScenario<MainActivity>? = null
    private fun s(id: Int, vararg args: Any) = run.context.getString(id, *args)

    private val garden = NoteFolder(id = id(401), name = "Garden", createdAt = 0)
    private val raisedBeds = NoteFolder(id = id(402), name = "Raised beds", parentId = garden.id, createdAt = 1, words = listOf("garden", "soil"))
    private val leaning = Note(id = id(41), title = "Water the garden path", createdAt = 2)
    private val bike = Note(id = id(42), title = "Bike paths map", sharedFrom = "example.org", createdAt = 3)

    @Before fun record() { System.setProperty("roborazzi.test.record", "true") }

    @After fun close() {
        shared?.close()
        run.close()
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun node(tag: String): SemanticsNodeInteraction = compose.onNode(hasTestTag(tag), useUnmergedTree = true)
    private fun spoken(tag: String) = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

    /** Its touch area at least 48 dp each way, as MapPanelsTouchTargetTest measures it. */
    private fun target(tag: String) {
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val touch = node(tag).fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$tag touches ${touch.width} x ${touch.height} px, under 48 dp", touch.width >= 48 * density - 0.5f && touch.height >= 48 * density - 0.5f)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File("build/outputs/roborazzi/$name.png").path)
    }

    private fun openMap() {
        compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        run.until { compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapTapKey)).fetchSemanticsNodes().isNotEmpty() || shown(BOARD_CARD_TAG) }
    }

    // A3
    @Test fun aFoldersWordsAreSetFromItsRowAndTheSheetSaysWhatItKeeps() {
        IdeaStore(run.store).replaceIdeas(listOf(leaning), listOf(garden))
        run.launch()
        with(run) { folderRow(run.context.resources.getQuantityString(R.plurals.folder_row_spoken, 0, "Garden", 0)).action(s(R.string.folder_words_title)) }
        run.until { shown(NAME_SHEET_TAG) }
        val field = run.field(s(R.string.folder_words_prompt))
        field.performTextClearance()
        field.performTextInput("garden, soil garden compost beds paths seeds")
        compose.waitForIdle()
        assertTrue("the footnote says what a save keeps", run.shows(s(R.string.folder_words_kept, "garden, soil, compost, beds, paths")))
        shot("sky_words_sheet")
        compose.onNode(hasText(s(R.string.sky_save))).performClick()
        run.until { !shown(NAME_SHEET_TAG) }
        run.until { IdeaStore(run.store).folders.first().words == listOf("garden", "soil", "compost", "beds", "paths") }
    }

    // A4
    @Test fun aLeaningIdeasCardSaysWhichWordItSharesAndFilesItThere() {
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        IdeaStore(run.store).replaceIdeas(listOf(leaning), listOf(garden, raisedBeds))
        run.launch()
        openMap()
        val tap: MapTap = checkNotNull(compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapTapKey)).fetchSemanticsNodes().first().config.getOrNull(MapTapKey))
        val index = tap.ids.indexOfFirst { it.equals(leaning.id, ignoreCase = true) }
        assertTrue("the idea is on the map: ${tap.ids}", index >= 0)
        compose.runOnIdle { tap.tap(index) }
        run.until { shown(SkyTags.LEAN) }
        assertEquals(s(R.string.lean_shares, "garden", "Raised beds"), spoken(SkyTags.LEAN))
        assertEquals("Shares ‘garden’ with Raised beds", spoken(SkyTags.LEAN))
        assertEquals(s(R.string.lean_file_spoken, "Raised beds"), spoken(SkyTags.FILE_THERE))
        // in this small window the card meets the strip and its middle scrolls: File there is scrolled to, as a person would
        node(SkyTags.FILE_THERE).performScrollTo()
        target(SkyTags.FILE_THERE)
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        val card = compose.onNode(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNode().boundsInRoot
        assertTrue("the card stays under the strip: $card, $strip", card.top >= strip.bottom)
        shot("sky_lean_card")
        node(SkyTags.FILE_THERE).performClick()
        run.until { IdeaStore(run.store).notes.first { it.id == leaning.id }.folderId == raisedBeds.id }
        run.until { !shown(SkyTags.LEAN) }
    }

    // A5, A6
    @Test fun theMoonsOnTheBoardLightADayAndClearIt() {
        // the app's own clock: a note's createdAt defaults to it (Robolectric's clock is not the wall's)
        val now = Note(id = id(49), title = "now").createdAt
        val day = 86_400_000L
        val zone = java.time.ZoneId.systemDefault()
        val midnight = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val today = (now - midnight).coerceAtLeast(4L)
        val ideas = listOf(
            Note(id = id(51), title = "Water the garden path", createdAt = now - today / 2),
            Note(id = id(52), title = "Bike paths map", createdAt = now - today / 4),
            Note(id = id(53), title = "Night ride route", createdAt = now - today * 3 / 4),
            Note(id = id(54), title = "Oil the gate", createdAt = now - 3 * day),
        )
        IdeasSettings(run.context).setLook(MapLook.TWO_D)
        IdeaStore(run.store).replaceIdeas(ideas, emptyList())
        run.launch()
        openMap()
        run.until { shown(SkyTags.DAYS_CHIP) }
        target(SkyTags.DAYS_CHIP)
        node(SkyTags.DAYS_CHIP).performClick()
        run.until { shown(SkyTags.moon(0)) }
        target(SkyTags.moon(0))
        val todayName = moonDayName(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        assertEquals("$todayName, 3 ideas", spoken(SkyTags.moon(0)))
        assertTrue("yesterday had none: ${spoken(SkyTags.moon(1))}", spoken(SkyTags.moon(1))!!.endsWith(", no ideas"))
        assertFalse("nothing to clear yet", shown(SkyTags.MOONS_CLEAR))
        node(SkyTags.moon(0)).performClick()
        run.until { shown(SkyTags.MOONS_CLEAR) }
        assertTrue("the heading names the lit day", run.shows("$todayName, 3 ideas"))
        target(SkyTags.MOONS_CLEAR)
        shot("sky_moons_board")
        node(SkyTags.MOONS_CLEAR).performClick()
        run.until { !shown(SkyTags.MOONS_CLEAR) }
        node(SkyTags.moon(0)).performClick()
        run.until { shown(SkyTags.MOONS_CLEAR) }
        node(SkyTags.moon(0)).performClick()
        run.until { !shown(SkyTags.MOONS_CLEAR) }
        assertEquals("TalkBack names the row as a pane", s(R.string.moons_title),
            node(SkyTags.MOONS).fetchSemanticsNode().config.getOrNull(SemanticsProperties.PaneTitle))
        node(SkyTags.MOONS_DONE).performClick()
        run.until { !shown(SkyTags.moon(0)) }
        // Back puts the row away and ends the lit day, before it leaves the map
        node(SkyTags.DAYS_CHIP).performClick()
        run.until { shown(SkyTags.moon(0)) }
        node(SkyTags.moon(0)).performClick()
        run.until { shown(SkyTags.MOONS_CLEAR) }
        run.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        run.until { !shown(SkyTags.moon(0)) }
        assertTrue("still on the map", shown(SkyTags.DAYS_CHIP))
    }

    // A7
    @Test fun aShareFromAnotherAppIsSavedInToSortWithItsSource() {
        IdeaStore(run.store).replaceIdeas(emptyList(), emptyList())
        val intent = Intent(run.context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Bike paths map https://www.example.org/paths please")
            .putExtra(MainActivity.STORE_DIR, run.store.path)
        shared = ActivityScenario.launch(intent)
        run.until { shown(SkyTags.SHARE_SHEET) }
        assertEquals(s(R.string.shared_from_spoken, "example.org"), spoken(SkyTags.SHARED_FROM))
        assertEquals(s(R.string.share_word_kept), node(SkyTags.shareWord(4)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        node(SkyTags.shareWord(4)).performClick()
        compose.waitForIdle()
        assertEquals(s(R.string.share_word_out), node(SkyTags.shareWord(4)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        shot("sky_share_sheet")
        node(SkyTags.SHARE_SAVE).performClick()
        run.until { !shown(SkyTags.SHARE_SHEET) }
        run.until { run.ideaCount("Bike paths map https://www.example.org/paths") == 1 }
        val saved = IdeaStore(run.store).notes.single()
        assertEquals("Bike paths map https://www.example.org/paths", saved.title)
        assertEquals("example.org", saved.sharedFrom)
        assertEquals("in To sort", null, saved.folderId)
    }

    // A7: Cancel keeps nothing
    @Test fun aCancelledShareKeepsNothing() {
        IdeaStore(run.store).replaceIdeas(emptyList(), emptyList())
        val intent = Intent(run.context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Night ride route").putExtra(MainActivity.STORE_DIR, run.store.path)
        shared = ActivityScenario.launch(intent)
        run.until { shown(SkyTags.SHARE_SHEET) }
        assertFalse("no link and no app named: no source", shown(SkyTags.SHARED_FROM))
        // Leave all out, then Keep all: one tap each for every word
        fun state(k: Int) = node(SkyTags.shareWord(k)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        compose.onNode(hasTestTag(SkyTags.SHARE_ALL)).assert(hasText(s(R.string.share_leave_all))).performClick()
        compose.waitForIdle()
        assertEquals(List(3) { s(R.string.share_word_out) }, (0..2).map(::state))
        node(SkyTags.SHARE_SAVE).assertIsNotEnabled()
        compose.onNode(hasTestTag(SkyTags.SHARE_ALL)).assert(hasText(s(R.string.share_keep_all))).performClick()
        compose.waitForIdle()
        assertEquals(List(3) { s(R.string.share_word_kept) }, (0..2).map(::state))
        node(SkyTags.SHARE_CANCEL).performClick()
        run.until { !shown(SkyTags.SHARE_SHEET) }
        assertTrue(IdeaStore(run.store).notes.isEmpty())
    }

    // A8
    @Test fun theEditorShowsWhereASharedIdeaCameFrom() {
        IdeaStore(run.store).replaceIdeas(listOf(bike, leaning), emptyList())
        run.launch()
        run.idea(leaning.title).performClick()
        run.until { shown(EDITOR_TAG) }
        assertFalse("an idea caught here has no mark", shown(SkyTags.SHARED_FROM))
        run.relaunch()
        run.idea(bike.title).performClick()
        run.until { shown(EDITOR_TAG) }
        assertEquals("Shared from example.org", spoken(SkyTags.SHARED_FROM))
        shot("sky_editor_mark")
    }
}
