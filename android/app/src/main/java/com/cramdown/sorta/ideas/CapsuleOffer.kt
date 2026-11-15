package com.cramdown.sorta.ideas

/**
 * When Sorta offers a capsule (S06d A4, plugins-plan idea 11): quietly, once a month at most. Pure;
 * iOS's CapsuleOffer is the same. The offer is due when there are ideas and [INTERVAL] has passed
 * since the latest of the first launch with ideas, the last offer and the last capsule saved. Shown
 * once, it is marked offered whatever the person does, so it never comes twice in a month.
 */
object CapsuleOffer {
    /** Thirty days, in milliseconds. */
    const val INTERVAL = 30L * 24 * 60 * 60 * 1000
    const val FIRST_SEEN_KEY = "sorta.capsule.firstSeen"
    const val OFFERED_KEY = "sorta.capsule.lastOffered"
    const val SAVED_KEY = "sorta.capsule.lastSaved"

    /** [now] and the three times in ms since 1970; [firstSeen] null until a launch has seen ideas. */
    fun due(now: Long, ideas: Int, firstSeen: Long?, offered: Long?, saved: Long?): Boolean {
        if (ideas <= 0 || firstSeen == null) return false
        val since = maxOf(firstSeen, offered ?: Long.MIN_VALUE, saved ?: Long.MIN_VALUE)
        return now - since >= INTERVAL
    }
}
