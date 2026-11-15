package com.cramdown.sorta.ideas

/**
 * Where a piece of writing stops: the one rule behind S06a's "You were saying…" ([IdeaStops.stoppedAt])
 * and S06d's Unfinished lens ([IdeaLens]). Text ends mid-sentence when its last character, past white
 * space and closing quotes and brackets, is not a mark that closes a sentence: . ! ? … or their
 * full-width and Arabic forms. Empty text, or white space alone, does not. iOS's IdeaSentence is the
 * same function, case for case.
 */
object IdeaSentence {
    /** The marks that close a sentence. */
    val closers: Set<Int> = ".!?\u2026\u3002\uFF01\uFF1F\u061F".codePoints().toArray().toSet()
    /** What may follow a sentence's close and still leave it closed, besides white space: closing quotes and brackets. */
    val trailing: Set<Int> = "\"'\u201D\u2019\u00BB)]}\u300D\u300F\u00A0".codePoints().toArray().toSet()

    /** [text] stops mid-sentence: something is written, and the last of it closes nothing. */
    fun endsMidSentence(text: String): Boolean {
        val last = text.codePoints().toArray().lastOrNull { !Character.isWhitespace(it) && it !in trailing } ?: return false
        return last !in closers
    }
}
