package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.NoteKind
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The idea store on the device (S04, A3), from Stethoscore's StudyStoreTest
 * (reference/stethoscore-aec2af1c/android/app/src/test/java/com/stethoscore/app/data/StudyStoreTest.kt):
 * :32-41 becomes the same check on notes and folders, and :42-57 (its notes part), :58-65, :79-92
 * and :93-100 become tests of this store. :66-71 and :101-114 are in IdeaImportTest; :72-78 tests
 * a study set's kind, which Sorta has none of. Every idea here is made up. iOS's NoteStoreFileTests
 * asks the same of its store.
 */
class IdeaStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
    private val file get() = File(folder.root, IdeaStore.FILE_NAME)

    /** Every field but rawJson, which holds the note as last read. */
    private fun withoutRaw(notes: List<Note>) = notes.map { it.copy(rawJson = "") }
    private fun foldersWithoutRaw(folders: List<NoteFolder>) = folders.map { it.copy(rawJson = "") }

    // :32-41
    @Test fun unknownFieldsAndBothIOSDateFormatsRoundTrip() {
        val note = IdeaJson.note(JSONObject("""{"id":"${id(1)}","title":"Paint the shed green","createdAt":0,"updatedAt":"2026-10-13T12:30:00Z","futureField":{"enabled":true},"colour":0.87}"""))
        assertEquals("seconds from 2001-01-01", 978307200000L, note.createdAt)
        assertEquals("ISO 8601", 1791894600000L, note.updatedAt)
        val roundtrip = IdeaJson.encodeNote(note)
        assertTrue(roundtrip.getJSONObject("futureField").getBoolean("enabled"))
        assertEquals(0.87, roundtrip.getDouble("colour"), 0.0)
        assertEquals(note.copy(rawJson = ""), IdeaJson.note(roundtrip).copy(rawJson = ""))

        val shelf = IdeaJson.folder(JSONObject("""{"id":"${id(101)}","name":"Garden","parentId":null,"createdAt":"2026-10-13T12:30:00Z","pinned":[1,2]}"""))
        assertEquals(1791894600000L, shelf.createdAt)
        assertEquals(null, shelf.parentId)
        val folderTrip = IdeaJson.encodeFolder(shelf)
        assertEquals(2, folderTrip.getJSONArray("pinned").length())
        assertEquals(shelf.copy(rawJson = ""), IdeaJson.folder(folderTrip).copy(rawJson = ""))
        assertEquals("a folder with no name", "Untitled folder", IdeaJson.folder(JSONObject("""{"id":"${id(102)}"}""")).name)

        // and through the store: fields beside the ideas are written back as they came
        file.writeText("""{"notes":[],"noteFolders":[],"laterVersion":{"kept":true}}""")
        val store = IdeaStore(folder.root)
        store.replaceIdeas(listOf(note), listOf(shelf))
        val written = JSONObject(file.readText())
        assertTrue(written.getJSONObject("laterVersion").getBoolean("kept"))
        assertTrue(written.getJSONArray("notes").getJSONObject(0).getJSONObject("futureField").getBoolean("enabled"))
        assertEquals(2, written.getJSONArray("noteFolders").getJSONObject(0).getJSONArray("pinned").length())
    }

    // :42-57, the notes part, with an IdeaBook wired to the store as the app wires it in step 4
    @Test fun whatOneStoreWritesANewOneReadsBack() {
        val store = IdeaStore(folder.root)
        var clock = 1_790_000_000_123L
        var nextId = 0
        val book = IdeaBook(store.notes, store.folders, now = { clock++ }, onChange = store::replaceIdeas,
            makeId = { id(++nextId) })
        val garden = book.createFolder("Garden")
        val beds = book.createFolder("Raised beds", parentId = garden.id)
        val shed = book.create("Paint the shed green", body = "Two coats, after the rain.", folderId = garden.id,
            at = BoardPoint(120.0, -40.0))
        val seeds = book.create("Seed swap list", body = "See [[Paint the shed green]].", kind = NoteKind.PAGE,
            folderId = beds.id, at = BoardPoint(-75.5, 210.25))
        val bell = book.create("A bell for the bike")
        book.link(seeds.id, shed.id)
        book.update(book.note(bell.id)!!.copy(tags = listOf("weekend", "small")))
        book.setPosition(bell.id, 0.0, 33.0)
        assertEquals("each change saved", withoutRaw(book.notes), withoutRaw(store.notes))

        val reopened = IdeaStore(folder.root)
        assertFalse(reopened.loadFailed)
        assertEquals(listOf(shed.id, seeds.id, bell.id), reopened.notes.map { it.id })
        assertEquals("every field but rawJson", withoutRaw(book.notes), withoutRaw(reopened.notes))
        assertEquals(foldersWithoutRaw(book.folders), foldersWithoutRaw(reopened.folders))
        assertEquals("the milliseconds kept", book.note(shed.id)!!.createdAt, reopened.notes[0].createdAt)
        assertEquals(listOf(shed.id), reopened.notes[1].links)
        assertEquals("a folder inside a folder", garden.id, reopened.folders.first { it.id == beds.id }.parentId)
        assertEquals("[[Title]]s as written", "See [[Paint the shed green]].", reopened.notes[1].body)
    }

    @Test fun aWriteLandsWholeOrNotAtAll() {
        val store = IdeaStore(folder.root)
        val kept = listOf(Note(id = id(1), title = "Paint the shed green", createdAt = 1L))
        store.replaceIdeas(kept, emptyList())
        val temporary = File(folder.root, IdeaStore.FILE_NAME + ".tmp")
        assertFalse("nothing left beside it", temporary.exists())
        val before = file.readBytes()

        // a write that can't land leaves the file as it was, whole
        temporary.mkdir(); File(temporary, "keep").writeText("")
        assertThrows(SaveFailed::class.java) {
            store.replaceIdeas(kept + Note(id = id(2), title = "Oil the hinges", createdAt = 2L), emptyList())
        }
        assertArrayEquals("byte for byte", before, file.readBytes())
        assertEquals(listOf(id(1)), IdeaStore(folder.root).notes.map { it.id })

        // a half-written temporary file from an interrupted save is written over, never read
        File(temporary, "keep").delete(); temporary.delete()
        temporary.writeText("""{"notes":[{"id":"${id(9)}","tit""")
        assertEquals(listOf(id(1)), IdeaStore(folder.root).notes.map { it.id })
        store.replaceIdeas(kept + Note(id = id(3), title = "Sweep the path", createdAt = 3L), emptyList())
        assertFalse(temporary.exists())
        assertEquals(listOf(id(1), id(3)), IdeaStore(folder.root).notes.map { it.id })
    }

    // :58-65 and :93-100
    @Test fun anUnreadableFileIsKeptAndNeverWrittenOver() {
        file.writeText("{ truncated")
        val store = IdeaStore(folder.root)
        assertTrue(store.loadFailed)
        assertTrue(store.notes.isEmpty())
        val refused = assertThrows(SaveFailed::class.java) { store.replaceIdeas(listOf(Note(title = "Start again")), emptyList()) }
        assertEquals(SaveFailure.FILE_UNREAD, refused.failure)
        assertTrue("memory as on disk", store.notes.isEmpty())
        assertEquals("{ truncated", file.readText())
        assertEquals(SaveFailure.FILE_UNREAD, assertThrows(SaveFailed::class.java) { store.save() }.failure)
        assertEquals(listOf(IdeaStore.FILE_NAME), folder.root.list()!!.toList())

        // once the file reads, the store saves again
        file.writeText("""{"notes":[{"id":"${id(1)}","title":"Paint the shed green"}]}""")
        store.load()
        assertFalse(store.loadFailed)
        store.replaceIdeas(store.notes + Note(id = id(2), title = "Start again"), emptyList())
        assertEquals(listOf(id(1), id(2)), IdeaStore(folder.root).notes.map { it.id })
    }

    @Test fun ideasThatArentAListAreUnreadable() {
        for (text in listOf("""{"notes":{"id":"x"}}""", """{"notes":[],"noteFolders":"Garden"}""", """{"notes":null}""", "[1,2]")) {
            file.writeText(text)
            val store = IdeaStore(folder.root)
            assertTrue(text, store.loadFailed)
            assertThrows(text, SaveFailed::class.java) { store.replaceIdeas(listOf(Note(title = "Start again")), emptyList()) }
            assertEquals(text, text, file.readText())
        }
        // missing ones are none, and an entry that isn't an object is skipped
        file.writeText("""{"notes":["a",3,null,{"id":"${id(1)}","title":"Paint the shed green"}]}""")
        val store = IdeaStore(folder.root)
        assertFalse(store.loadFailed)
        assertEquals(listOf(id(1)), store.notes.map { it.id })
        assertTrue(store.folders.isEmpty())
    }

    // :79-92
    @Test fun failedSaveLeavesMemoryMatchingDisk() {
        val store = IdeaStore(folder.root)
        val garden = NoteFolder(id = id(101), name = "Garden", createdAt = 1L)
        store.replaceIdeas(listOf(Note(id = id(1), title = "Kept", createdAt = 1L)), listOf(garden))
        // A directory where the temporary file goes makes every write fail, like a full disk.
        File(folder.root, IdeaStore.FILE_NAME + ".tmp").also { it.mkdir(); File(it, "keep").writeText("") }
        val failed = assertThrows(SaveFailed::class.java) {
            store.replaceIdeas(store.notes + Note(id = id(2), title = "Lost", createdAt = 2L), emptyList())
        }
        assertEquals(SaveFailure.NOT_WRITTEN, failed.failure)
        assertEquals(listOf("Kept"), store.notes.map { it.title })
        assertEquals(listOf(garden), store.folders)
        val reopened = IdeaStore(folder.root)
        assertEquals(listOf("Kept"), reopened.notes.map { it.title })
        assertEquals(foldersWithoutRaw(listOf(garden)), foldersWithoutRaw(reopened.folders))
    }
}
