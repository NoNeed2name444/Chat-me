package com.cramdown.sorta.ui.ideas

import com.cramdown.sorta.ui.ward.WardFold
import com.cramdown.sorta.ui.ward.WardRect
import com.cramdown.sorta.ui.ward.WindowShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Where Sort and Map split (S05b checklist 9), as iOS's IdeasSplit: a phone keeps one screen, a
// broad window gives Sort 40% and at least 320, an upright fold gives each pane its side and the
// hinge between them, a fold lying flat keeps one screen.
class IdeasSplitTest {
    private fun fold(orientation: WardFold.Orientation, bounds: WardRect) =
        WardFold(WardFold.State.halfOpened, orientation, bounds, isSeparating = true, occlusion = WardFold.Occlusion.full)

    @Test fun aPhoneKeepsOneScreen() {
        assertNull(IdeasSplit.of(WindowShape(390.0, 844.0)))
        assertNull(IdeasSplit.of(WindowShape(699.0, 400.0)))
    }

    @Test fun aBroadWindowGivesSortItsShareAndAtLeast320() {
        assertEquals(IdeasSplit(320.0, 0.0), IdeasSplit.of(WindowShape(700.0, 400.0)))
        assertEquals(IdeasSplit(480.0, 0.0), IdeasSplit.of(WindowShape(1200.0, 800.0)))
    }

    @Test fun anUprightFoldGivesEachPaneItsSide() {
        val shape = WindowShape(1200.0, 900.0, fold(WardFold.Orientation.vertical, WardRect(590.0, 0.0, 20.0, 900.0)))
        assertEquals(IdeasSplit(590.0, 20.0), IdeasSplit.of(shape))
    }

    @Test fun aFoldLyingFlatKeepsOneScreen() {
        val shape = WindowShape(900.0, 1200.0, fold(WardFold.Orientation.horizontal, WardRect(0.0, 590.0, 900.0, 20.0)))
        assertNull(IdeasSplit.of(shape))
    }

    @Test fun aSliverOfAPaneKeepsOneScreen() {
        val shape = WindowShape(1200.0, 900.0, fold(WardFold.Orientation.vertical, WardRect(1170.0, 0.0, 0.0, 900.0)))
        assertNull(IdeasSplit.of(shape))
    }
}
