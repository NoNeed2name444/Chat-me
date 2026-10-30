package com.cramdown.sorta.ui.ideas

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
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
import java.io.File

// The Sort screen's behaviour (S05, A4 and A5), as a person uses it: Sorta launched on a fresh
// store each time (MainActivity's test-only store extra), and relaunched on the same store to
// show what was kept. The words come from strings.xml, the ux-writing pass's. iOS's UI tests ask
// the same of its screen.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SortScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private fun s(id: Int, vararg args: Any) = run.context.getString(id, *args)
    private fun p(id: Int, n: Int, vararg args: Any) = run.context.resources.getQuantityString(id, n, *args)

    @After fun close() = run.close()

    private fun type(words: String) {
        run.field.performTextInput(words)
        compose.waitForIdle()
    }

    private fun capture(words: String) {
        type(words)
        run.field.performImeAction()
        compose.waitForIdle()
    }

    /** A folder made from the More menu, at the level on show. */
    private fun newFolder(name: String) {
        compose.onNode(hasTestTag(IdeasTags.MORE)).performClick()
        compose.onNodeWithText(s(R.string.more_new_folder)).performClick()
        compose.waitForIdle()
        run.field(s(R.string.name_field)).performTextInput(name)
        compose.onNodeWithText(s(R.string.name_create)).performClick()
        // the sheet goes, and the screen is laid out again with the folder in it
        run.until { compose.onAllNodes(hasContentDescription("$name, ", substring = true) and hasAnyAncestor(hasTestTag(IdeasTags.SCREEN)))
            .fetchSemanticsNodes().isNotEmpty() }
    }

    private fun chooseFor(line: String) {
        compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, line))).performClick()
        compose.waitForIdle()
        assertTrue("the chooser opens", run.chooserShown())
    }

    private fun undo() {
        run.until { run.banner() != null }
        compose.onNodeWithText(s(R.string.undo_action)).performClick()
        compose.waitForIdle()
    }

    // ---- capture ----

    @Test fun returnSavesTheIdeaAndKeepsTheFieldFocused() {
        run.launch()
        capture("Paint the shed green")
        run.idea("Paint the shed green").assertIsDisplayed()
        run.field.assertIsFocused()
        assertEquals("the field is cleared for the next one", "", run.draft())
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 1, 1))
        capture("Buy brushes")
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 2, 2))
        run.field.assertIsFocused()
    }

    @Test fun sortItSavesTheIdeaAndOpensTheChooserAndClosingItLeavesTheIdeaInToSort() {
        run.launch()
        newFolder("Garden")
        type("Fix the gate")
        run.sortIt.assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue("Sort it opens the chooser", run.chooserShown())
        compose.onNode(hasText(s(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(FOLDER_CHOOSER_TAG))).performClick()
        compose.waitForIdle()
        assertFalse("the chooser closes", run.chooserShown())
        run.idea("Fix the gate").assertIsDisplayed()
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 1, 1))
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).assertIsDisplayed()
    }

    @Test fun anEmptyFieldSavesNothing() {
        run.launch()
        run.sortIt.assertIsNotEnabled()
        run.field.performImeAction()
        type("   ")
        run.sortIt.assertIsNotEnabled()
        run.field.performImeAction()
        compose.waitForIdle()
        assertEquals("no idea was made", 0, compose.onAllNodes(hasTestTag(IdeasTags.IDEA_LINE)).fetchSemanticsNodes().size)
        compose.onNode(hasTestTag(IdeasTags.EMPTY)).assertIsDisplayed()
        assertTrue("the store was never written", !File(run.store, IdeaStore.FILE_NAME).exists())
    }

    @Test fun insideAFolderCaptureSavesIntoIt() {
        run.launch()
        newFolder("Garden")
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).performClick()
        compose.waitForIdle()
        capture("Plant tulips")
        run.title.assertContentDescriptionEquals(p(R.plurals.folder_title_spoken, 1, "Garden", 1))
        run.idea("Plant tulips").assertIsDisplayed()
        compose.onNode(hasContentDescription(s(R.string.up_row_spoken, s(R.string.sort_title)))).performClick()
        compose.waitForIdle()
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 0, 0))
        assertEquals("the idea isn't in To sort", 0, run.ideaCount("Plant tulips"))
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).assertIsDisplayed()
    }

    // ---- the chooser ----

    @Test fun choosingAFolderFilesTheIdeaAndUndoPutsItBack() {
        run.launch()
        newFolder("Garden")
        capture("Fix the gate")
        chooseFor("Fix the gate")
        run.inChooser("Garden").performClick()
        compose.waitForIdle()
        assertEquals("the idea left To sort", 0, run.ideaCount("Fix the gate"))
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).assertIsDisplayed()
        run.until { run.banner()?.contains(s(R.string.undo_moved, "Garden")) == true }
        undo()
        run.idea("Fix the gate").assertIsDisplayed()
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).assertIsDisplayed()
    }

    @Test fun newFolderFromTheChooserMakesTheFolderAndFilesTheIdeaInIt() {
        run.launch()
        capture("Fix the gate")
        chooseFor("Fix the gate")
        run.inChooser(s(R.string.chooser_new_folder)).performClick()
        compose.waitForIdle()
        run.field(s(R.string.name_field)).performTextInput("Shed")
        compose.onNodeWithText(s(R.string.name_create)).performClick()
        compose.waitForIdle()
        assertEquals("the idea left To sort", 0, run.ideaCount("Fix the gate"))
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Shed", 1)).assertIsDisplayed()
        run.until { run.banner()?.contains(s(R.string.undo_moved, "Shed")) == true }
    }

    @Test fun toSortInTheChooserTakesTheIdeaOutOfItsFolder() {
        run.launch()
        newFolder("Garden")
        capture("Fix the gate")
        chooseFor("Fix the gate")
        run.inChooser("Garden").performClick()
        compose.waitForIdle()
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).performClick()
        compose.waitForIdle()
        chooseFor("Fix the gate")
        run.inChooser(s(R.string.chooser_to_sort_spoken)).performClick()
        compose.waitForIdle()
        run.until { run.banner()?.contains(s(R.string.undo_to_sort)) == true }
        compose.onNode(hasContentDescription(s(R.string.up_row_spoken, s(R.string.sort_title)))).performClick()
        compose.waitForIdle()
        run.idea("Fix the gate").assertIsDisplayed()
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).assertIsDisplayed()
    }

    // ---- folders and deleting ----

    @Test fun aFolderOpensAndGoesBackUpAndANestedFolderShowsItsOwnIdeas() {
        run.launch()
        newFolder("Garden")
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).performClick()
        compose.waitForIdle()
        capture("Plant tulips")
        newFolder("Raised beds")
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Raised beds", 0)).performClick()
        compose.waitForIdle()
        run.title.assertContentDescriptionEquals(p(R.plurals.folder_title_spoken, 0, "Raised beds", 0))
        assertEquals("the parent's idea isn't here", 0, run.ideaCount("Plant tulips"))
        capture("Line the beds")
        compose.onNode(hasContentDescription(s(R.string.up_row_spoken, "Garden"))).performClick()
        compose.waitForIdle()
        run.title.assertContentDescriptionEquals(p(R.plurals.folder_title_spoken, 1, "Garden", 1))
        run.idea("Plant tulips").assertIsDisplayed()
        assertEquals("the nested idea stays in its folder", 0, run.ideaCount("Line the beds"))
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Raised beds", 1)).assertIsDisplayed()
        compose.onNode(hasContentDescription(s(R.string.up_row_spoken, s(R.string.sort_title)))).performClick()
        compose.waitForIdle()
        run.folderRow(p(R.plurals.folder_row_spoken, 2, "Garden", 2)).assertIsDisplayed()
    }

    @Test fun aFolderIsRenamedAndDeletedAndItsContentsMoveUp() {
        run.launch()
        newFolder("Garden")
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Garden", 0)).performClick()
        compose.waitForIdle()
        capture("Plant tulips")
        newFolder("Raised beds")
        compose.onNode(hasContentDescription(s(R.string.up_row_spoken, s(R.string.sort_title)))).performClick()
        compose.waitForIdle()
        with(run) { folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).action(s(R.string.folder_rename)) }
        val name = run.field(s(R.string.name_field))
        name.performTextClearance()
        name.performTextInput("Yard")
        compose.onNodeWithText(s(R.string.name_rename)).performClick()
        compose.waitForIdle()
        val yard = run.folderRow(p(R.plurals.folder_row_spoken, 1, "Yard", 1))
        yard.assertIsDisplayed()
        with(run) { yard.action(s(R.string.folder_delete)) }
        compose.onNodeWithText(s(R.string.folder_delete_title, "Yard")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.folder_delete_body, s(R.string.sort_title))).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.folder_delete_confirm)).performClick()
        compose.waitForIdle()
        run.idea("Plant tulips").assertIsDisplayed()
        run.folderRow(p(R.plurals.folder_row_spoken, 0, "Raised beds", 0)).assertIsDisplayed()
        assertEquals("the folder is gone", 0, compose.onAllNodes(hasContentDescription("Yard", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun anIdeaSwipedAwayIsDeletedAndUndoBringsItBack() {
        run.launch()
        capture("Fix the gate")
        capture("Buy brushes")
        run.idea("Fix the gate").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        run.until { run.ideaCount("Fix the gate") == 0 }
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 1, 1))
        run.until { run.banner()?.contains(s(R.string.undo_deleted)) == true }
        undo()
        run.idea("Fix the gate").assertIsDisplayed()
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 2, 2))
        run.relaunch()
        run.idea("Fix the gate").assertIsDisplayed()
    }

    @Test fun anIdeaRowOffersChooseFolderAndDeleteToTalkBack() {
        run.launch()
        capture("Fix the gate")
        val row = run.idea("Fix the gate")
        assertEquals(listOf(s(R.string.row_choose_folder), s(R.string.row_delete)), run.actions(row))
        with(run) { row.action(s(R.string.row_choose_folder)) }
        assertTrue("its Choose folder opens the chooser", run.chooserShown())
        compose.onNode(hasText(s(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(FOLDER_CHOOSER_TAG))).performClick()
        compose.waitForIdle()
        with(run) { run.idea("Fix the gate").action(s(R.string.row_delete)) }
        assertEquals("its Delete deletes", 0, run.ideaCount("Fix the gate"))
        undo()
        run.idea("Fix the gate").assertIsDisplayed()
    }

    // ---- the editor, and what is kept ----

    @Test fun theEditorsTitleDetailsAndLinkAreThereAfterARelaunch() {
        run.launch()
        capture("Buy brushes")
        capture("Paint the shed")
        run.idea("Paint the shed").performClick()
        compose.waitForIdle()
        compose.onNode(hasTestTag(EDITOR_TAG)).assertIsDisplayed()
        val title = run.field(s(R.string.editor_title))
        title.performTextClearance()
        title.performTextInput("Paint the shed green")
        run.field(s(R.string.editor_body)).performTextInput("Two coats, after the rain.")
        compose.onNodeWithText(s(R.string.editor_link_to)).performScrollTo().performClick()
        run.until { compose.onAllNodes(hasContentDescription("Buy brushes") and hasAnyAncestor(hasTestTag(LINK_PICKER_TAG))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Buy brushes") and hasAnyAncestor(hasTestTag(LINK_PICKER_TAG))).performClick()
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(LINK_PICKER_TAG))).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.waitForIdle()
        run.idea("Paint the shed green").assertIsDisplayed()

        run.relaunch()
        run.idea("Paint the shed green").performClick()
        compose.waitForIdle()
        run.field(s(R.string.editor_title)).assert(hasText("Paint the shed green"))
        run.field(s(R.string.editor_body)).assert(hasText("Two coats, after the rain."))
        compose.onNode(hasText("Buy brushes") and hasText(s(R.string.editor_how_hand)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).assertExists()
    }

    @Test fun anIdeaCapturedFiledAndEditedIsAllThereAfterARelaunch() {
        run.launch()
        newFolder("Garden")
        capture("Plant tulips")
        chooseFor("Plant tulips")
        run.inChooser("Garden").performClick()
        compose.waitForIdle()
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).performClick()
        compose.waitForIdle()
        run.idea("Plant tulips").performClick()
        compose.waitForIdle()
        run.field(s(R.string.editor_body)).performTextInput("Red ones by the wall.")
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.waitForIdle()

        run.relaunch()
        run.title.assertContentDescriptionEquals(p(R.plurals.sort_title_spoken, 0, 0))
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).performClick()
        compose.waitForIdle()
        run.idea("Plant tulips").performClick()
        compose.waitForIdle()
        run.field(s(R.string.editor_body)).assert(hasText("Red ones by the wall."))
        compose.onNode(hasContentDescription(s(R.string.editor_chip_spoken, "Garden"))).assertIsDisplayed()
    }

    @Test fun aFreshStoreShowsTheEmptyStateWithCaptureReady() {
        run.launch()
        compose.onNode(hasTestTag(IdeasTags.EMPTY)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.empty_line1)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.empty_line2)).assertIsDisplayed()
        assertEquals("no title row on an empty store", 0, compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().size)
        run.field.assertIsDisplayed().assert(hasContentDescription(s(R.string.capture_placeholder)))
        compose.onNode(hasTestTag(IdeasTags.MORE)).assertIsDisplayed()
        // ready: one tap and the next words are an idea
        run.field.performClick()
        run.field.assertIsFocused()
        capture("Fix the gate")
        run.idea("Fix the gate").assertIsDisplayed()
    }

    // ---- the import ----

    @Test fun theImportAddsAMadeUpExportAndSaysWhatAndASecondAddsNothing() {
        val export = run.file("ideas.json", SortaRun.export)
        run.launch(import = export)
        run.until { run.shows(s(R.string.import_done_title)) }
        val added = s(R.string.import_done_body, p(R.plurals.import_ideas, 2, 2), p(R.plurals.import_folders, 1, 1), p(R.plurals.import_links, 1, 1)) +
            " " + p(R.plurals.import_placed, 1, 1)
        compose.onNodeWithText(added).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.import_ok)).performClick()
        compose.waitForIdle()
        run.idea("Buy brushes").assertIsDisplayed()
        run.folderRow(p(R.plurals.folder_row_spoken, 1, "Garden", 1)).assertIsDisplayed()

        run.relaunch(import = export)
        run.until { run.shows(s(R.string.import_nothing_title)) }
        compose.onNodeWithText(s(R.string.import_nothing_body)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.import_ok)).performClick()
        compose.waitForIdle()
        assertEquals("nothing was added twice", 1, run.ideaCount("Buy brushes"))
    }

    @Test fun eachRefusalSaysItsOwnWords() {
        val cases = listOf(
            run.file("other.json", """{"format":"another-app-v3","notes":[]}""") to R.string.import_refused_not_export,
            run.file("newer.json", """{"format":"stethoscore-android-v2","notes":[]}""") to R.string.import_refused_other_version,
            run.file("broken.json", "{\"format\": ") to R.string.import_refused_unreadable,
        )
        for ((i, case) in cases.withIndex()) {
            if (i == 0) run.launch(import = case.first) else run.relaunch(import = case.first)
            run.until { run.shows(s(case.second)) }
            compose.onNodeWithText(s(R.string.import_refused_title)).assertIsDisplayed()
            compose.onNodeWithText(s(R.string.import_ok)).performClick()
            compose.waitForIdle()
        }
        compose.onNode(hasTestTag(IdeasTags.EMPTY)).assertIsDisplayed()
    }

    // ---- the store's file, unread ----

    @Test fun aStoreFileItCantReadIsKeptSaidOnceAndCaptureStillWorks() {
        val file = File(run.store, IdeaStore.FILE_NAME)
        file.writeText("{ not the ideas")
        run.launch()
        run.until { run.banner()?.contains(s(R.string.store_unreadable)) == true }
        capture("Fix the gate")
        run.idea("Fix the gate").assertIsDisplayed()
        assertEquals("the file is kept as it was", "{ not the ideas", file.readText())
        assertFalse("nothing says the change was kept", run.shows(s(R.string.store_save_failed)))
    }
}
