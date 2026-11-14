package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.IdeaJson
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.map.FolderWords
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S06b's two fields: NoteFolder.words and Note.sharedFrom, under the same JSON names as iOS's
 * NoteStore (Keys), each optional, so a file written before them still reads.
 */
class SkyFieldsTest {
    private var clock = 1_000L
    private fun book(folders: List<NoteFolder> = emptyList()) = IdeaBook(emptyList(), folders, now = { clock }, onChange = { _, _ -> })

    @Test fun olderFilesStillRead() {
        assertNull(IdeaJson.note(JSONObject("""{"id":"n1","title":"Old idea"}""")).sharedFrom)
        assertEquals(emptyList<String>(), IdeaJson.folder(JSONObject("""{"id":"f1","name":"Old folder"}""")).words)
    }

    @Test fun theFieldsGoAndComeBackUnderIosNames() {
        val note = Note(id = "n1", title = "Shared", sharedFrom = "example.org")
        val written = IdeaJson.encodeNote(note)
        assertEquals("example.org", written.getString("sharedFrom"))
        assertEquals("example.org", IdeaJson.note(written).sharedFrom)
        val folder = NoteFolder(id = "f1", name = "Raised beds", words = listOf("garden", "soil"))
        val back = IdeaJson.folder(IdeaJson.encodeFolder(folder))
        assertEquals(listOf("garden", "soil"), back.words)
        // cleared, the keys go, as a file written before them
        assertFalse(IdeaJson.encodeNote(IdeaJson.note(written).copy(sharedFrom = null)).has("sharedFrom"))
        assertFalse(IdeaJson.encodeFolder(back.copy(words = emptyList())).has("words"))
    }

    @Test fun aFolderTakesAtMostFiveWordsAndAShareKeepsItsSource() {
        val b = book(listOf(NoteFolder(id = "f1", name = "Raised beds")))
        b.setWords("f1", FolderWords.parse("garden soil compost beds weeds seeds"))
        assertEquals(listOf("garden", "soil", "compost", "beds", "weeds"), b.folders.single().words)
        b.setWords("f1", FolderWords.parse(""))
        assertEquals(emptyList<String>(), b.folders.single().words)
        val shared = b.create("Seed swap", sharedFrom = "example.org")
        assertEquals("example.org", shared.sharedFrom)
        assertNull("To sort", shared.folderId)
    }
}
