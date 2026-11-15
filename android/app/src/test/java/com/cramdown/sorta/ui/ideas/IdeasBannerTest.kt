package com.cramdown.sorta.ui.ideas

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S05h2 repair 3: how long the banner keeps a notice, and the tests' extra that keeps Undo up. */
class IdeasBannerTest {
    @Test fun aNoticeWithUndoStaysLongUnlessTheTestsAsk() {
        assertEquals(SnackbarDuration.Long, IdeasBanner.duration(undo = true, undoStays = false))
        assertEquals(SnackbarDuration.Indefinite, IdeasBanner.duration(undo = true, undoStays = true))
    }

    @Test fun aNoticeWithoutUndoIsNeverKept() {
        assertEquals(SnackbarDuration.Long, IdeasBanner.duration(undo = false, undoStays = false))
        assertEquals(SnackbarDuration.Long, IdeasBanner.duration(undo = false, undoStays = true))
    }

    @Test fun onlyADebuggableBuildHeedsTheExtra() {
        assertTrue(IdeasBanner.undoStays(asked = true, debuggable = true))
        assertFalse("a release build ignores the extra", IdeasBanner.undoStays(asked = true, debuggable = false))
        assertFalse(IdeasBanner.undoStays(asked = false, debuggable = true))
        assertFalse(IdeasBanner.undoStays(asked = false, debuggable = false))
    }
}
