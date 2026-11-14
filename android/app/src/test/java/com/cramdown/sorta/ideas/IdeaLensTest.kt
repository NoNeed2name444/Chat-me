package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The lenses (S06d A1), case by case as iOS's IdeaLensTests: the sentence rule, each lens's
 * predicate, two lenses at once and the chips' counts. The ideas are made up, none medical.
 */
class IdeaLensTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun note(id: String, title: String, body: String = "", created: String = "2026-11-02T12:00:00Z") =
        Note(id = id, title = title, body = body, createdAt = at(created))

    @Test fun sentence() {
        val cases = listOf(
            "" to false, "   " to false, "Buy milk" to true, "Buy milk." to false, "Really?" to false, "Wow!" to false,
            "And then…" to false, "And then..." to false, "Buy milk  \n" to true, "Ends with a comma," to true,
            "(see the list)" to true, "Tea? Later" to true, "Two lines.\nAnd a third" to true,
        )
        for ((text, mid) in cases) assertEquals("\"$text\"", mid, IdeaSentence.endsMidSentence(text))
    }

    @Test fun questions() {
        val cases = listOf(
            Triple("Why is the sky blue", "", false), Triple("Why?", "", true), Triple("Plan", "is it? maybe", true),
            Triple("", "", false), Triple("Shed", "Two coats.", false),
        )
        for ((title, body, yes) in cases) assertEquals("$title / $body", yes, IdeaLens.isQuestion(title, body))
    }

    @Test fun unfinished() {
        val cases = listOf(
            Triple("Paint the shed", "", true), Triple("Paint the shed.", "", false), Triple("Done.", "Two coats, after", true),
            Triple("Plan", "Two coats.", false), Triple("", "", false), Triple("x", "   ", true),
        )
        for ((title, body, yes) in cases) assertEquals("$title / $body", yes, IdeaLens.isUnfinished(title, body))
    }

    @Test fun lateNight() {
        val cases = listOf(
            "2026-11-02T22:59:59Z" to false, "2026-11-02T23:00:00Z" to true, "2026-11-03T00:30:00Z" to true,
            "2026-11-03T03:59:59Z" to true, "2026-11-03T04:00:00Z" to false, "2026-11-03T12:00:00Z" to false,
        )
        for ((iso, yes) in cases) assertEquals(iso, yes, IdeaLens.isLateNight(at(iso), utc))
        // the phone's zone decides: 9:30 PM in London is 11:30 PM in Cairo
        assertEquals("Cairo", true, IdeaLens.isLateNight(at("2026-11-14T21:30:00Z"), ZoneId.of("Africa/Cairo")))
        assertEquals("UTC", false, IdeaLens.isLateNight(at("2026-11-14T21:30:00Z"), utc))
    }

    private val shed = note("A", "Paint the shed?", created = "2026-11-02T23:30:00Z")
    private val gate = note("b", "Fix the gate latch.", created = "2026-11-02T10:00:00Z")
    private val seeds = note("C", "Order seeds for", created = "2026-11-03T01:00:00Z")
    private val bread = note("D", "Bake bread?", "Rye or wheat", created = "2026-11-03T09:00:00Z")
    private val notes = listOf(shed, gate, seeds, bread)
    private val edges = listOf("A" to "B")

    @Test fun alone() {
        val linked = IdeaLens.linked(edges)
        assertEquals(listOf(false, false, true, true), notes.map { IdeaLens.ALONE.passes(it, linked, utc) })
    }

    @Test fun passingAndCounts() {
        assertNull("no lens: everything", IdeaLens.passing(emptySet(), notes, edges, utc))
        assertEquals(setOf("A", "D"), IdeaLens.passing(setOf(IdeaLens.QUESTIONS), notes, edges, utc))
        assertEquals(setOf("A", "C"), IdeaLens.passing(setOf(IdeaLens.LATE_NIGHT), notes, edges, utc))
        assertEquals(setOf("C", "D"), IdeaLens.passing(setOf(IdeaLens.UNFINISHED), notes, edges, utc))
        assertEquals(setOf("C", "D"), IdeaLens.passing(setOf(IdeaLens.ALONE), notes, edges, utc))
        // two lenses: only ideas that pass both
        assertEquals(setOf("A"), IdeaLens.passing(setOf(IdeaLens.QUESTIONS, IdeaLens.LATE_NIGHT), notes, edges, utc))
        assertEquals(emptySet<String>(), IdeaLens.passing(setOf(IdeaLens.LATE_NIGHT, IdeaLens.UNFINISHED, IdeaLens.QUESTIONS), notes, edges, utc))
        assertEquals(mapOf(IdeaLens.QUESTIONS to 2, IdeaLens.LATE_NIGHT to 2, IdeaLens.UNFINISHED to 2, IdeaLens.ALONE to 2),
            IdeaLens.counts(emptySet(), notes, edges, utc))
        // with Questions on, each chip counts what shows with it on as well
        assertEquals(mapOf(IdeaLens.QUESTIONS to 2, IdeaLens.LATE_NIGHT to 1, IdeaLens.UNFINISHED to 1, IdeaLens.ALONE to 1),
            IdeaLens.counts(setOf(IdeaLens.QUESTIONS), notes, edges, utc))
        assertEquals(listOf("questions", "late-night", "unfinished", "alone"), IdeaLens.entries.map { it.id })
    }
}
