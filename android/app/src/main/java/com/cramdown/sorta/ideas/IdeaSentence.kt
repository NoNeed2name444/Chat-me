package com.cramdown.sorta.ideas

/**
 * Where a piece of writing stops (S06d A1, the rule S06a's A4 states): text that, trimmed of white
 * space, is not empty and does not end in a closing mark (. ! ? …) ends mid-sentence. iOS's
 * IdeaSentence is the same function.
 */
object IdeaSentence {
    /** The marks that close a sentence. */
    val CLOSERS = charArrayOf('.', '!', '?', '…')

    /** [text] stops mid-sentence: not empty once trimmed, and its last character closes nothing. */
    fun endsMidSentence(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.last() !in CLOSERS
    }
}
