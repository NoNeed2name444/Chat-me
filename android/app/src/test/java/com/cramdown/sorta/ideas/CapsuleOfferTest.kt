package com.cramdown.sorta.ideas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capsule's quiet offer (S06d A4), as iOS's CapsuleTests' offer cases: once a month at most, and never before there are ideas. */
class CapsuleOfferTest {
    private val day = 24L * 60 * 60 * 1000
    private val t = 1_793_000_000_000L

    @Test fun dueAMonthAfterTheLatestOfFirstSeenOfferedAndSaved() {
        assertEquals(30 * day, CapsuleOffer.INTERVAL)
        assertFalse("no ideas", CapsuleOffer.due(t + 90 * day, 0, t, null, null))
        assertFalse("never seen with ideas", CapsuleOffer.due(t + 90 * day, 5, null, null, null))
        assertFalse("the first month", CapsuleOffer.due(t + 29 * day, 5, t, null, null))
        assertTrue("a month after the first launch with ideas", CapsuleOffer.due(t + 30 * day, 5, t, null, null))
        assertFalse("offered 10 days ago", CapsuleOffer.due(t + 40 * day, 5, t, t + 30 * day, null))
        assertTrue("offered a month ago", CapsuleOffer.due(t + 60 * day, 5, t, t + 30 * day, null))
        assertFalse("saved 5 days ago", CapsuleOffer.due(t + 65 * day, 5, t, t + 30 * day, t + 60 * day))
        assertTrue("saved a month ago", CapsuleOffer.due(t + 90 * day, 5, t, t + 30 * day, t + 60 * day))
    }

    @Test fun neverTwiceInAMonth() {
        // each launch, as the screen does: due, then marked offered at once
        var offered: Long? = null
        var shown = 0
        for (d in 30L..89L) {
            val now = t + d * day
            if (CapsuleOffer.due(now, 3, t, offered, null)) { shown++; offered = now }
        }
        assertEquals("days 30 and 60", 2, shown)
    }
}
