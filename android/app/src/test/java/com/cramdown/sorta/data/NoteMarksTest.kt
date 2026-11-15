package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.IdeaBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two stored fields S06a adds, openedAt and stoppedAt: read and written under iOS's names,
 * left out when unset (so files from before read and write as they did), and changed only by
 * markOpened and setStopped, which leave the time alone, as iOS's NoteMarksTests asks.
 */
class NoteMarksTest {
    private val id = "00000000-0000-4000-8000-000000000001"

    @Test fun bothRoundTripUnderIOSNames() {
        val note = IdeaJson.note(JSONObject("""{"id":"$id","title":"Paint the shed green","createdAt":"2026-10-13T12:30:00Z","openedAt":"2026-11-01T09:00:00Z","stoppedAt":12}"""))
        assertEquals(1793523600000L, note.openedAt)
        assertEquals(12, note.stoppedAt)
        val out = IdeaJson.encodeNote(note)
        assertEquals("2026-11-01T09:00:00Z", out.getString("openedAt"))
        assertEquals(12, out.getInt("stoppedAt"))
        assertEquals(note.copy(rawJson = ""), IdeaJson.note(out).copy(rawJson = ""))
    }

    @Test fun unsetIsLeftOutAndAClearedMarkGoes() {
        val plain = IdeaJson.note(JSONObject("""{"id":"$id","title":"Buy brushes","createdAt":"2026-10-13T12:30:00Z"}"""))
        assertNull(plain.openedAt)
        assertNull(plain.stoppedAt)
        val out = IdeaJson.encodeNote(plain)
        assertFalse(out.has("openedAt"))
        assertFalse(out.has("stoppedAt"))
        // read with a mark, then cleared: the kept rawJson must not bring it back
        val marked = IdeaJson.note(JSONObject("""{"id":"$id","title":"Buy brushes","createdAt":"2026-10-13T12:30:00Z","stoppedAt":4,"openedAt":"2026-11-01T09:00:00Z"}"""))
        val cleared = IdeaJson.encodeNote(marked.copy(stoppedAt = null, openedAt = null))
        assertFalse(cleared.has("stoppedAt"))
        assertFalse(cleared.has("openedAt"))
        // a value that isn't a date reads as never opened
        assertNull(IdeaJson.note(JSONObject("""{"id":"$id","title":"x","openedAt":"soon"}""")).openedAt)
    }

    @Test fun marksLeaveTheTimeAloneAndAnOlderCopyLeavesThem() {
        var clock = 1_000L
        val book = IdeaBook(listOf(Note(id = id, title = "Fix the gate latch", body = "Two coats, then", createdAt = 500L)), emptyList(), now = { clock })
        clock = 2_000L
        book.markOpened(id)
        book.setStopped(id, 9)
        val after = book.note(id)!!
        assertEquals(2_000L, after.openedAt)
        assertEquals(9, after.stoppedAt)
        assertEquals("the time is left alone", 500L, after.updatedAt)
        // the editor's copy from before the marks, saved with a change: the marks stay
        val older = after.copy(openedAt = null, stoppedAt = null, title = "Fix the gate latch today")
        clock = 3_000L
        book.update(older)
        assertEquals(2_000L, book.note(id)!!.openedAt)
        assertEquals(9, book.note(id)!!.stoppedAt)
        assertEquals(3_000L, book.note(id)!!.updatedAt)
        book.setStopped(id, null)
        assertNull(book.note(id)!!.stoppedAt)
    }
}
