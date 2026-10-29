package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The import's check (S04, A4; the first half of the owner's gate of 23 Oct): a made-up
 * stethoscore-android-v1 export brought in, and every idea, link, folder, folder inside a folder and
 * board position there, each note's unknown fields as written, and the counts importJson returns.
 * A second import adds nothing, and each refusal changes nothing. From Stethoscore's StudyStoreTest
 * (reference/stethoscore-aec2af1c/android/app/src/test/java/com/stethoscore/app/data/StudyStoreTest.kt):
 * :66-71 is [eachRefusalChangesNothing] and :101-114 (a full backup's notes) is
 * [anExportOfAStoresOwnFileCarriesItOver]; the rest is new. None of it is a real idea, and none of it
 * is medical. iOS's IdeaImportTests asks the same of its import.
 */
class IdeaImportTest {
    @get:Rule val folder = TemporaryFolder()
    private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
    private val file get() = File(folder.root, IdeaStore.FILE_NAME)

    /**
     * Four folders, two inside the others; four ideas, linked by hand to each other and to an idea
     * the export doesn't hold (id 50), three off the centre of the board, with fields a later
     * version wrote; and entries that aren't objects, skipped. Beside them, parts no import reads,
     * none of them readable as what they were.
     */
    private val export = """
        {"format":"stethoscore-android-v1","exportedAt":"2026-10-20T09:00:00Z",
         "library":"not a library","reviews":"[}","answerLog":7,"quizProgress":[1,2],
         "noteFolders":[
           {"id":"${id(101)}","name":"Garden","parentId":null,"createdAt":"2026-10-01T08:00:00Z"},
           {"id":"${id(102)}","name":"Raised beds","parentId":"${id(101)}","createdAt":"2026-10-01T08:05:00Z","pinned":true},
           {"id":"${id(103)}","name":"Kitchen","createdAt":0},
           {"id":"${id(104)}","name":"Bread","parentId":"${id(103)}","createdAt":"2026-10-02T10:00:00.250Z"},
           "a folder that isn't one"],
         "notes":[
           {"id":"${id(1)}","title":"Paint the shed green","body":"Two coats, after the rain.","folderId":"${id(101)}","kind":"idea","links":[],"tags":["weekend"],"boardX":120,"boardY":-40,"createdAt":"2026-10-03T07:00:00Z","updatedAt":"2026-10-04T07:00:00Z","colour":"#2E7D32"},
           {"id":"${id(2)}","title":"Seed swap list","body":"Beans and squash. See [[Paint the shed green]].","folderId":"${id(102)}","kind":"idea","links":["${id(1)}"],"tags":[],"boardX":0,"boardY":0,"createdAt":"2026-10-05T07:00:00Z","updatedAt":"2026-10-05T07:00:00Z","source":{"kind":"card","setName":"Spanish verbs"}},
           null,
           {"id":"${id(3)}","title":"Try a rye starter","body":"# Starter\n\nFeed it twice a day.","folderId":"${id(104)}","kind":"page","links":["${id(1)}","${id(2)}"],"tags":["kitchen","slow"],"boardX":-75.5,"boardY":210.25,"createdAt":"2026-10-06T07:00:00.125Z","updatedAt":"2026-10-07T07:00:00Z","futureField":{"tiers":[1,2]}},
           42,
           {"id":"${id(4)}","title":"A bell for the bike","body":"","folderId":null,"kind":"idea","links":["${id(50)}"],"tags":[],"boardX":0,"boardY":33,"createdAt":0,"updatedAt":0}
         ]}
    """.trimIndent()

    private fun withoutRaw(notes: List<Note>) = notes.map { it.copy(rawJson = "") }
    private fun foldersWithoutRaw(folders: List<NoteFolder>) = folders.map { it.copy(rawJson = "") }

    @Test fun everyIdeaLinkFolderAndPlaceCarriesOver() {
        val store = IdeaStore(folder.root)
        val counts = store.importJson(export)
        assertEquals(ImportCounts(ideas = 4, folders = 4, links = 4, placed = 3), counts)

        assertEquals(listOf(id(1), id(2), id(3), id(4)), store.notes.map { it.id })
        val (shed, seeds, rye, bell) = store.notes
        assertEquals(Note(id(1), "Paint the shed green", "Two coats, after the rain.", id(101), "idea", emptyList(),
            listOf("weekend"), 120.0, -40.0, 1_791_010_800_000L, 1_791_097_200_000L), shed.copy(rawJson = "{}"))
        assertEquals(listOf(id(1)), seeds.links)
        assertEquals("[[Title]]s as written", "Beans and squash. See [[Paint the shed green]].", seeds.body)
        assertEquals(id(102), seeds.folderId)
        assertEquals(0.0, seeds.boardX, 0.0); assertEquals(0.0, seeds.boardY, 0.0)
        assertEquals(listOf(id(1), id(2)), rye.links)
        assertEquals("page", rye.kind)
        assertEquals(-75.5, rye.boardX, 0.0); assertEquals(210.25, rye.boardY, 0.0)
        assertEquals("the milliseconds kept", 1_791_270_000_125L, rye.createdAt)
        assertEquals(listOf(id(50)), bell.links)
        assertNull(bell.folderId)
        assertEquals(33.0, bell.boardY, 0.0)
        assertEquals("seconds from 2001-01-01", 978_307_200_000L, bell.createdAt)

        assertEquals(listOf(id(101), id(102), id(103), id(104)), store.folders.map { it.id })
        assertEquals(listOf("Garden", "Raised beds", "Kitchen", "Bread"), store.folders.map { it.name })
        assertEquals(listOf(null, id(101), null, id(103)), store.folders.map { it.parentId })
        assertEquals(1_790_935_200_250L, store.folders[3].createdAt)
        val book = IdeaBook(store.notes, store.folders)
        assertEquals("a folder inside a folder", listOf(id(101), id(102)), book.path(id(102)).map { it.id })
        assertEquals(listOf(id(103), id(104)), book.path(id(104)).map { it.id })
        assertTrue(book.isLinked(id(3), id(1)) && book.isLinked(id(3), id(2)) && book.isLinked(id(1), id(2)))

        // saved once, with each note's and folder's unknown fields as written
        val reopened = IdeaStore(folder.root)
        assertEquals(withoutRaw(store.notes), withoutRaw(reopened.notes))
        assertEquals(foldersWithoutRaw(store.folders), foldersWithoutRaw(reopened.folders))
        val written = JSONObject(file.readText())
        val notes = written.getJSONArray("notes")
        assertEquals(4, notes.length())
        assertEquals("#2E7D32", notes.getJSONObject(0).getString("colour"))
        assertEquals("Spanish verbs", notes.getJSONObject(1).getJSONObject("source").getString("setName"))
        assertEquals(2, notes.getJSONObject(2).getJSONObject("futureField").getJSONArray("tiers").length())
        assertTrue(written.getJSONArray("noteFolders").getJSONObject(1).getBoolean("pinned"))
        // nothing but the ideas was read, so nothing else was written
        assertEquals(setOf("notes", "noteFolders"), written.keys().asSequence().toSet())
    }

    @Test fun aSecondImportAddsNothing() {
        val store = IdeaStore(folder.root)
        assertEquals(4, store.importJson(export).ideas)
        val saved = file.readBytes()
        val notes = store.notes; val folders = store.folders
        file.setLastModified(1_000_000L)
        assertEquals(ImportCounts(), store.importJson(export))
        assertEquals(notes, store.notes)
        assertEquals(folders, store.folders)
        assertArrayEquals("nothing written", saved, file.readBytes())
        assertEquals(1_000_000L, file.lastModified())
    }

    @Test fun anIdeaOrFolderAlreadyHereStaysAsItIs() {
        val store = IdeaStore(folder.root)
        val mine = Note(id = id(1), title = "Paint the shed blue", boardX = 9.0, boardY = 9.0, createdAt = 5L)
        val bellHere = Note(id = id(50), title = "Ring the bell twice", createdAt = 6L)
        val kitchen = NoteFolder(id = id(103), name = "Cooking", createdAt = 7L)
        store.replaceIdeas(listOf(mine, bellHere), listOf(kitchen))
        assertEquals("the shed is here already: its place isn't counted, the links of the three added are",
            ImportCounts(ideas = 3, folders = 3, links = 4, placed = 2), store.importJson(export))
        assertEquals(mine, store.notes.first { it.id == id(1) })
        assertEquals(kitchen, store.folders.first { it.id == id(103) })
        assertEquals(5, store.notes.size)
        assertEquals(4, store.folders.size)
        val book = IdeaBook(store.notes, store.folders)
        assertTrue("a link to an idea already here joins it", book.isLinked(id(4), id(50)))
        assertEquals("an added folder sits in the one here", listOf("Cooking", "Bread"), book.path(id(104)).map { it.name })
    }

    @Test fun missingIdeasAreNoneAndEntriesThatArentObjectsAreSkipped() {
        val store = IdeaStore(folder.root)
        assertEquals(ImportCounts(), store.importJson("""{"format":"stethoscore-android-v1"}"""))
        assertFalse("nothing to add, nothing written", file.exists())
        assertEquals(ImportCounts(), store.importJson("""{"format":"stethoscore-android-v1","notes":[1,"two",null,[]]}"""))
        assertFalse(file.exists())
        assertEquals(ImportCounts(folders = 1),
            store.importJson("""{"format":"stethoscore-android-v1","noteFolders":[{"id":"${id(101)}","name":"Garden"},false]}"""))
        assertEquals(listOf("Garden"), IdeaStore(folder.root).folders.map { it.name })
        assertTrue(store.notes.isEmpty())
    }

    // :101-114, the notes part: an export made of one store's own file
    @Test fun anExportOfAStoresOwnFileCarriesItOver() {
        val source = IdeaStore(folder.newFolder("source"))
        val sourceBook = IdeaBook(source.notes, source.folders, onChange = source::replaceIdeas)
        val garden = sourceBook.createFolder("Garden")
        val shed = sourceBook.create("Paint the shed green", folderId = garden.id)
        val gate = sourceBook.create("Fix the gate latch")
        sourceBook.link(gate.id, shed.id)
        val exported = JSONObject(File(folder.root, "source/" + IdeaStore.FILE_NAME).readText())
            .put("format", IdeaStore.EXPORT_FORMAT).put("library", JSONObject().put("sets", 3))

        val restored = IdeaStore(folder.newFolder("restored"))
        assertEquals(ImportCounts(ideas = 2, folders = 1, links = 1, placed = 1), restored.importJson(exported.toString()))
        assertEquals(withoutRaw(source.notes), withoutRaw(restored.notes))
        assertEquals(foldersWithoutRaw(source.folders), foldersWithoutRaw(restored.folders))
        val reopened = IdeaStore(folder.root.resolve("restored"))
        assertEquals(withoutRaw(source.notes), withoutRaw(reopened.notes))
        assertEquals(1, reopened.folders.size)
    }

    // :66-71, and the rest of the refusals
    @Test fun eachRefusalChangesNothing() {
        val store = IdeaStore(folder.root)
        store.replaceIdeas(listOf(Note(id = id(60), title = "Water the plants", boardX = 1.0, boardY = 2.0, createdAt = 1L)),
            listOf(NoteFolder(id = id(160), name = "Home", createdAt = 1L)))
        val notes = store.notes; val folders = store.folders
        val saved = file.readBytes()

        val v1 = "\"format\":\"stethoscore-android-v1\""
        val ideas = "\"notes\":[{\"id\":\"${id(1)}\",\"title\":\"Paint the shed green\"}]"
        // over 32 MB in UTF-8, though under it in characters
        val padding = "é".repeat(IdeaStore.MAX_IMPORT_BYTES / 2 + 1)
        val cases = listOf(
            "too large" to ("{$v1,$ideas,\"padding\":\"$padding\"}" to ImportRefusal.TOO_LARGE),
            "not JSON" to ("broken" to ImportRefusal.UNREADABLE),
            "half an object" to ("{$v1,$ideas" to ImportRefusal.UNREADABLE),
            "a list, not an object" to ("[{$v1,$ideas}]" to ImportRefusal.UNREADABLE),
            "notes not a list" to ("{$v1,\"notes\":{\"id\":\"${id(1)}\"}}" to ImportRefusal.UNREADABLE),
            "notes null" to ("{$v1,\"notes\":null}" to ImportRefusal.UNREADABLE),
            "noteFolders not a list" to ("{$v1,$ideas,\"noteFolders\":\"Garden\"}" to ImportRefusal.UNREADABLE),
            "no format" to ("{$ideas}" to ImportRefusal.NOT_AN_EXPORT),
            "a format that isn't text" to ("{\"format\":1,$ideas}" to ImportRefusal.NOT_AN_EXPORT),
            "another app's" to ("{\"format\":\"another-export-v1\",$ideas}" to ImportRefusal.NOT_AN_EXPORT),
            "an iOS backup's manifest" to ("{\"format\":\"stethoscore-backup\",\"version\":1,$ideas}" to ImportRefusal.NOT_AN_EXPORT),
            "another version" to ("{\"format\":\"stethoscore-android-v2\",$ideas}" to ImportRefusal.OTHER_VERSION),
        )
        for ((what, case) in cases) {
            val (text, expected) = case
            val refused = assertThrows(what, ImportRefused::class.java) { store.importJson(text) }
            assertEquals(what, expected, refused.refusal)
            assertEquals("$what: the ideas as they were", notes, store.notes)
            assertEquals("$what: the folders as they were", folders, store.folders)
            assertArrayEquals("$what: the file as it was", saved, file.readBytes())
        }
        assertEquals(listOf(id(60)), IdeaStore(folder.root).notes.map { it.id })
    }

    @Test fun anImportThatCantBeSavedChangesNothing() {
        val store = IdeaStore(folder.root)
        store.replaceIdeas(listOf(Note(id = id(60), title = "Water the plants", createdAt = 1L)), emptyList())
        val saved = file.readBytes()
        File(folder.root, IdeaStore.FILE_NAME + ".tmp").also { it.mkdir(); File(it, "keep").writeText("") }
        assertEquals(SaveFailure.NOT_WRITTEN, assertThrows(SaveFailed::class.java) { store.importJson(export) }.failure)
        assertEquals(listOf(id(60)), store.notes.map { it.id })
        assertTrue(store.folders.isEmpty())
        assertArrayEquals(saved, file.readBytes())

        // and over a file it couldn't read, nothing is imported
        val unread = File(folder.newFolder("unread"), IdeaStore.FILE_NAME).also { it.writeText("{ truncated") }
        val blocked = IdeaStore(unread.parentFile)
        assertEquals(SaveFailure.FILE_UNREAD, assertThrows(SaveFailed::class.java) { blocked.importJson(export) }.failure)
        assertTrue(blocked.notes.isEmpty())
        assertEquals("{ truncated", unread.readText())
    }
}
