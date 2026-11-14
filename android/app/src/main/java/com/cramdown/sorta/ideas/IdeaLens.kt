package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.Note
import java.time.Instant
import java.time.ZoneId

/**
 * A lens over the map (S06d A1, plugins-plan idea 8): a question asked of every idea, which the map
 * answers by showing only the ideas that pass. Pure; iOS's IdeaLens is the same, case by case.
 * Lenses are held while Map mode is, never stored. [id] names a lens in tests and accessibility ids.
 */
enum class IdeaLens(val id: String) {
    /** The title or the text has a question mark. */
    QUESTIONS("questions"),
    /** Caught from 11 PM up to 4 AM, in the phone's time zone. */
    LATE_NIGHT("late-night"),
    /** The text (or, with no text, the title) stops mid-sentence ([IdeaSentence]). */
    UNFINISHED("unfinished"),
    /** No link either way: neither a hand-made link nor a `[[Title]]`. */
    ALONE("alone");

    /** Whether [note] passes, with [linked] the upper-case ids of every note in a link and [zone] the phone's. */
    fun passes(note: Note, linked: Set<String>, zone: ZoneId): Boolean = when (this) {
        QUESTIONS -> isQuestion(note.title, note.body)
        LATE_NIGHT -> isLateNight(note.createdAt, zone)
        UNFINISHED -> isUnfinished(note.title, note.body)
        ALONE -> note.id.uppercase() !in linked
    }

    companion object {
        /** The hours a late-night idea is caught in: from [LATE_FROM] up to, not including, [LATE_UNTIL]. */
        const val LATE_FROM = 23
        const val LATE_UNTIL = 4

        fun isQuestion(title: String, body: String): Boolean = '?' in title || '?' in body

        /** [createdAt] (ms since 1970) falls from 11 PM up to 4 AM in [zone]. */
        fun isLateNight(createdAt: Long, zone: ZoneId): Boolean {
            val hour = Instant.ofEpochMilli(createdAt).atZone(zone).hour
            return hour >= LATE_FROM || hour < LATE_UNTIL
        }

        /** The text stops mid-sentence; an idea with no text is read by its title. */
        fun isUnfinished(title: String, body: String): Boolean =
            IdeaSentence.endsMidSentence(body.ifBlank { title })

        /** Every note in a link, by upper-case id, from the book's edges (IdeaBook.allEdges). */
        fun linked(edges: List<Pair<String, String>>): Set<String> {
            val out = HashSet<String>(edges.size * 2)
            for ((a, b) in edges) { out.add(a.uppercase()); out.add(b.uppercase()) }
            return out
        }

        /** The upper-case ids of the [notes] that pass every lens [on]; null with none on (everything shows). */
        fun passing(on: Set<IdeaLens>, notes: List<Note>, edges: List<Pair<String, String>>, zone: ZoneId): Set<String>? {
            if (on.isEmpty()) return null
            val linked = linked(edges)
            return notes.filter { n -> on.all { it.passes(n, linked, zone) } }.mapTo(HashSet()) { it.id.uppercase() }
        }

        /**
         * Each lens's chip count: how many notes show with that lens on beside those already [on]
         * (for a lens that is on, how many show now).
         */
        fun counts(on: Set<IdeaLens>, notes: List<Note>, edges: List<Pair<String, String>>, zone: ZoneId): Map<IdeaLens, Int> {
            val linked = linked(edges)
            return entries.associateWith { lens ->
                val with = on + lens
                notes.count { n -> with.all { it.passes(n, linked, zone) } }
            }
        }
    }
}
