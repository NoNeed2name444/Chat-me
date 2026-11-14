package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ideas.SortaRun.Companion.id
import com.cramdown.sorta.ui.ward.WindowShapeHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Made-up ideas for S06c's flows and renders: the rain barrel, the newest, shares a rare word with three
 * older ideas (tomatoes, the gutter, the greenhouse), so they are like it; the rest share none. Its words
 * are three sentences. Nothing medical, and the times are fixed.
 */
object ShapesFixture {
    private const val T = 1_793_000_000_000L
    private const val HOUR = 3_600_000L
    val garden = NoteFolder(id = id(401), name = "Garden", createdAt = T)
    val home = NoteFolder(id = id(402), name = "Home", createdAt = T + 1)
    val trips = NoteFolder(id = id(403), name = "Trips", createdAt = T + 2)
    val kitchen = NoteFolder(id = id(404), name = "Kitchen", createdAt = T + 3)
    val barrel = Note(id = id(41), title = "Rain barrel for the greenhouse", body = "Catch the rain from the gutter. Water the tomatoes with it.",
        folderId = garden.id, createdAt = T + 9 * HOUR, updatedAt = T + 9 * HOUR)
    val shed = Note(id = id(42), title = "Paint the shed green", body = "Two coats.", folderId = garden.id, createdAt = T + 6 * HOUR, updatedAt = T + 6 * HOUR)
    val tomatoes = Note(id = id(43), title = "Tomatoes in pots", body = "Cherry ones on the balcony, in the sun.", folderId = garden.id,
        createdAt = T + 5 * HOUR, updatedAt = T + 5 * HOUR)
    val gutter = Note(id = id(44), title = "Fix the gutter", body = "It leaks over the back door.", folderId = home.id,
        createdAt = T + 4 * HOUR, updatedAt = T + 4 * HOUR)
    val greenhouse = Note(id = id(45), title = "Greenhouse heater", body = "A small one, for the frost.", folderId = garden.id,
        createdAt = T + 3 * HOUR, updatedAt = T + 3 * HOUR)
    val bread = Note(id = id(46), title = "Try the seeded bread", folderId = kitchen.id, createdAt = T + 2 * HOUR, updatedAt = T + 2 * HOUR)
    val train = Note(id = id(47), title = "Book the train to the coast", body = "Saturday, early. Bring the kite.", folderId = trips.id,
        createdAt = T + 1 * HOUR, updatedAt = T + 1 * HOUR)
    val folders = listOf(garden, home, trips, kitchen)
    /** On the board where the app puts ideas made one after another, not all on the centre. */
    val notes = IdeaBook(emptyList(), emptyList()).let { book ->
        listOf(barrel, shed, tomatoes, gutter, greenhouse, bread, train).map { note ->
            val spot = book.create(note.title)
            note.copy(boardX = spot.boardX, boardY = spot.boardY)
        }
    }
    /** The ideas like the barrel, best first: each holds one rare word of it, so the newer comes first. */
    val echoes = listOf(tomatoes.id, gutter.id, greenhouse.id)
}

// S06c A2-A6 on the Sort screen's editor (IdeasView, a phone): the ideas like the open one and a tap that
// links one with Undo; Split…, Join with… and Collide with… from the editor's More, each with Undo; and
// Collide's pair, never the last one, or why there is none. iOS's IdeaShapesUITests asks the same.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdeaShapesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun open(editing: String? = ShapesFixture.barrel.id): IdeasModel {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, ideas = ShapesFixture.notes, folderList = ShapesFixture.folders))
        compose.setContent { SortaTheme { WindowShapeHost { IdeasView(model, start = IdeasStart(editing = editing), undoStays = true) } } }
        compose.waitForIdle()
        return model
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun more(tag: String) {
        compose.onNode(hasContentDescription(s(R.string.sort_more)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.onNode(hasTestTag(tag)).performClick()
        compose.waitForIdle()
    }

    private fun inSheet(tag: String, text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)))

    @Test fun theIdeasLikeItShowAndATapLinksOneWithUndo() {
        val model = open()
        waitFor(IdeaShapeTags.ECHOES)
        val shown = compose.onAllNodes(hasTestTag(IdeaShapeTags.ECHO)).fetchSemanticsNodes()
        assertEquals("the three older ideas with a rare word of it", 3, shown.size)
        compose.onNode(hasTestTag(IdeaShapeTags.ECHO) and hasContentDescription("Tomatoes in pots")).performClick()
        waitForText(s(R.string.echoes_linked, "Tomatoes in pots"))
        assertTrue("linked by hand", model.book.isLinked(ShapesFixture.barrel.id, ShapesFixture.tomatoes.id))
        compose.onNode(hasText(s(R.string.undo_action))).performClick()
        compose.waitUntil(5_000) { !model.book.isLinked(ShapesFixture.barrel.id, ShapesFixture.tomatoes.id) }
    }

    @Test fun collideWithMakesAnIdeaInToSortLinkedToBothAndCancelLeavesNothing() {
        val model = open()
        val before = model.book.notes.size
        more(IdeaShapeTags.MENU_COLLIDE)
        waitFor(IdeaShapeTags.PICKER)
        inSheet(IdeaShapeTags.PICKER, "Book the train to the coast").performClick()
        waitFor(IdeaShapeTags.COLLIDE)
        compose.onNode(hasText(s(R.string.collide_title))).assertExists()
        // Cancel leaves nothing
        inSheet(IdeaShapeTags.COLLIDE, s(R.string.sheet_cancel)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(IdeaShapeTags.COLLIDE)).fetchSemanticsNodes().isEmpty() }
        assertEquals(before, model.book.notes.size)
        // again, and saved
        more(IdeaShapeTags.MENU_COLLIDE)
        inSheet(IdeaShapeTags.PICKER, "Book the train to the coast").performClick()
        waitFor(IdeaShapeTags.COLLIDE_FIELD)
        compose.onNode(hasTestTag(IdeaShapeTags.COLLIDE_FIELD)).performTextInput("A rain barrel at the station")
        compose.onNode(hasTestTag(IdeaShapeTags.COLLIDE_SAVE)).performClick()
        waitForText(s(R.string.collide_saved))
        val made = model.book.notes.single { it.title == "A rain barrel at the station" }
        assertNull("in To sort", made.folderId)
        assertEquals("linked to both", listOf(ShapesFixture.barrel.id, ShapesFixture.train.id), made.links)
        compose.onNode(hasText(s(R.string.undo_action))).performClick()
        compose.waitUntil(5_000) { model.book.notes.size == before }
    }

    @Test fun splitAtTheChosenSentenceAndUndoPutsItBack() {
        val model = open()
        val before = model.book.notes
        more(IdeaShapeTags.MENU_SPLIT)
        waitFor(IdeaShapeTags.SPLIT)
        // the second sentence ends the first idea
        inSheet(IdeaShapeTags.SPLIT, "Catch the rain from the gutter.").performClick()
        // TalkBack hears what the chosen row means (review N4)
        compose.onNode(hasText("Catch the rain from the gutter.") and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.split_ends_here))).assertExists()
        compose.onNode(hasTestTag(IdeaShapeTags.SPLIT_CONFIRM)).performClick()
        waitForText(s(R.string.split_done))
        assertEquals("Catch the rain from the gutter.", model.book.note(ShapesFixture.barrel.id)?.body)
        val second = model.book.notes.single { it.title == "Water the tomatoes with it." }
        assertEquals("the second keeps the folder", ShapesFixture.garden.id, second.folderId)
        compose.onNode(hasText(s(R.string.undo_action))).performClick()
        compose.waitUntil(5_000) { model.book.notes.size == before.size }
        assertEquals("Undo puts it back as one", before.map { it.copy(updatedAt = 0) }, model.book.notes.map { it.copy(updatedAt = 0) })
    }

    @Test fun joinWithJoinsAndUndoSplitsThemBack() {
        val model = open()
        val before = model.book.notes.size
        more(IdeaShapeTags.MENU_JOIN)
        waitFor(IdeaShapeTags.PICKER)
        inSheet(IdeaShapeTags.PICKER, "Fix the gutter").performClick()
        waitForText(s(R.string.join_done))
        val joined = checkNotNull(model.book.note(ShapesFixture.barrel.id))
        assertTrue("texts in order", joined.body.endsWith("Fix the gutter\n\nIt leaks over the back door."))
        assertNull("the second is gone", model.book.note(ShapesFixture.gutter.id))
        compose.onNode(hasText(s(R.string.undo_action))).performClick()
        compose.waitUntil(5_000) { model.book.notes.size == before }
        assertNotNull(model.book.note(ShapesFixture.gutter.id))
    }

    private fun inEditor(text: String) =
        compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).fetchSemanticsNodes().isNotEmpty()

    private fun done() {
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(EDITOR_TAG)).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun afterAJoinTheEditorShowsTheJoinedWordsAndDoneKeepsThem() {
        val model = open()
        more(IdeaShapeTags.MENU_JOIN)
        inSheet(IdeaShapeTags.PICKER, "Fix the gutter").performClick()
        waitForText(s(R.string.join_done))
        compose.waitUntil(5_000) { inEditor("It leaks over the back door.") }
        done()
        val joined = checkNotNull(model.book.note(ShapesFixture.barrel.id))
        assertTrue("Done keeps the joined words: ${joined.body}", joined.body.endsWith("Fix the gutter\n\nIt leaks over the back door."))
    }

    @Test fun afterASplitTheEditorShowsTheFirstHalfAndDoneKeepsIt() {
        val model = open()
        more(IdeaShapeTags.MENU_SPLIT)
        waitFor(IdeaShapeTags.SPLIT)
        inSheet(IdeaShapeTags.SPLIT, "Catch the rain from the gutter.").performClick()
        compose.onNode(hasTestTag(IdeaShapeTags.SPLIT_CONFIRM)).performClick()
        waitForText(s(R.string.split_done))
        compose.waitUntil(5_000) { !inEditor("Water the tomatoes with it.") }
        done()
        assertEquals("Done keeps the first half", "Catch the rain from the gutter.", model.book.note(ShapesFixture.barrel.id)?.body)
        assertEquals("the second half once", 1, model.book.notes.count { "Water the tomatoes with it." in it.title + "\n" + it.body })
    }

    @Test fun collidePicksFarPairsNeverTheLastAndSaysWhenThereAreNone() {
        val book = IdeaBook(ShapesFixture.notes, ShapesFixture.folders)
        val said = ArrayList<String>()
        val shapes = IdeaShapes(book, compose.activity.resources, { message, _ -> said.add(message) }, Random(11))
        var last: Set<String>? = null
        repeat(30) {
            shapes.collideAny()
            val sheet = shapes.sheet as ShapeSheet.Collide
            val a = checkNotNull(book.note(sheet.a)); val b = checkNotNull(book.note(sheet.b))
            assertFalse("different folders", a.folderId == b.folderId)
            assertFalse("not linked", book.allEdges().any { setOf(it.first, it.second) == setOf(a.id, b.id) })
            assertFalse("never the same pair twice in a row", setOf(a.id, b.id) == last)
            last = setOf(a.id, b.id)
        }
        // all in one folder: none is far apart, and it says so
        val one = IdeaBook(ShapesFixture.notes.map { it.copy(folderId = ShapesFixture.garden.id) }, ShapesFixture.folders)
        val alone = IdeaShapes(one, compose.activity.resources, { message, _ -> said.add(message) }, Random(3))
        alone.collideAny()
        assertNull(alone.sheet)
        assertEquals(s(R.string.collide_none), said.last())
    }
}
