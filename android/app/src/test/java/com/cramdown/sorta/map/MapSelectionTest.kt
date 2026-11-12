package com.cramdown.sorta.map

import com.cramdown.sorta.map.MapSelectionEffect.Clear
import com.cramdown.sorta.map.MapSelectionEffect.Links
import com.cramdown.sorta.map.MapSelectionEffect.Menu
import com.cramdown.sorta.map.MapSelectionEffect.Open
import com.cramdown.sorta.map.MapSelectionEffect.Select
import com.cramdown.sorta.map.MapSelectionEvent.Chip
import com.cramdown.sorta.map.MapSelectionEvent.Closed
import com.cramdown.sorta.map.MapSelectionEvent.DoubleTap
import com.cramdown.sorta.map.MapSelectionEvent.Gone
import com.cramdown.sorta.map.MapSelectionEvent.Hold
import com.cramdown.sorta.map.MapSelectionEvent.OpenCard
import com.cramdown.sorta.map.MapSelectionEvent.ShowLinks
import com.cramdown.sorta.map.MapSelectionEvent.TapBody
import com.cramdown.sorta.map.MapSelectionEvent.TapEmpty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Touching the map (S05g): what a touch was and what follows from it, as iOS's GraphTouchTests T1
// and T2 check GraphTouchRules and GraphSelection, case for case, plus Android's own: the slop is
// Android's touch slop, the hold times stretch with a longer touch-and-hold setting, and ids match
// whatever their case. The S05b cases that were MapPeekTest's aTouchIsJudgedOnLift are here too.
class MapSelectionTest {
    private val slop = 10f

    private fun judge(onBody: Boolean, held: Double, moved: Float, lifted: Boolean = true, taps: Int = 1, scale: Double = 1.0) =
        MapTouchRules.judge(MapTouch(onBody, held, moved, lifted, taps), slop, scale)

    // T1: what a touch was

    @Test fun aQuickTouchIsATapOnABodyOrOff() {
        assertEquals(MapTouchKind.TAP, judge(true, 0.08, 2f))
        assertEquals("on empty space too: it lets the chosen one go", MapTouchKind.TAP, judge(false, 0.08, 2f))
    }

    @Test fun twoQuickTapsOnABodyAreADoubleTapButNotOnEmptySpace() {
        assertEquals(MapTouchKind.DOUBLE_TAP, judge(true, 0.08, 1f, taps = 2))
        assertEquals(MapTouchKind.TAP, judge(false, 0.08, 1f, taps = 2))
    }

    @Test fun aQuickDragTurnsTheCameraNeverMovesTheBody() {
        assertEquals(MapTouchKind.ORBIT, judge(true, 0.12, 40f, lifted = false))
        assertEquals(MapTouchKind.ORBIT, judge(false, 0.05, 60f, lifted = false))
        assertEquals("held on empty space then moved: still the camera", MapTouchKind.ORBIT, judge(false, 0.5, 30f, lifted = false))
    }

    @Test fun heldAQuarterSecondThenMovedIsPickedUp() {
        assertEquals(MapTouchKind.DRAG, judge(true, 0.3, 25f, lifted = false))
        assertEquals("exactly at the hold", MapTouchKind.DRAG, judge(true, MapTouchRules.HOLD_TO_DRAG, 11f, lifted = false))
        assertEquals("just before it", MapTouchKind.ORBIT, judge(true, 0.2499, 11f, lifted = false))
    }

    @Test fun heldStillOnABodyGivesItsOptions() {
        assertEquals(MapTouchKind.MENU, judge(true, 0.6, 3f, lifted = false))
        assertEquals("exactly at 0.55 s", MapTouchKind.MENU, judge(true, MapTouchRules.HOLD_FOR_MENU, 3f, lifted = false))
        assertEquals("still deciding before it", MapTouchKind.PENDING, judge(true, 0.3, 2f, lifted = false))
        assertEquals("never on empty space", MapTouchKind.PENDING, judge(false, 0.9, 2f, lifted = false))
    }

    @Test fun aSlowTapIsStillATapAndAWobbleWithinTheSlopToo() {
        assertEquals(MapTouchKind.TAP, judge(true, 0.4, 2f))
        assertEquals(MapTouchKind.TAP, judge(true, 0.1, slop))
        assertEquals("just past the slop: the camera", MapTouchKind.ORBIT, judge(true, 0.1, slop + 0.01f, lifted = false))
        assertEquals("a slow lift on empty space is nothing", MapTouchKind.NOTHING, judge(false, 0.4, 2f))
    }

    @Test fun theThresholdsAreIOSs() {
        assertEquals(0.25, MapTouchRules.HOLD_TO_DRAG, 0.0)
        assertEquals(0.55, MapTouchRules.HOLD_FOR_MENU, 0.0)
        assertTrue(MapTouchRules.HOLD_FOR_MENU > 0.4)
        assertEquals(0.30, MapTouchRules.DOUBLE_TAP_GAP, 0.0)
        assertEquals(40f, MapTouchRules.DOUBLE_TAP_REACH_DP, 0f)
        assertEquals(0.6, MapTouchRules.OPEN_REPEAT, 0.0)
    }

    @Test fun aDoubleTapIsCloseInTimeAndPlace() {
        assertTrue(MapTouchRules.pairs(0.2, 12f))
        assertFalse(MapTouchRules.pairs(0.5, 12f))
        assertFalse(MapTouchRules.pairs(0.2, 90f))
        assertTrue("at the edges", MapTouchRules.pairs(MapTouchRules.DOUBLE_TAP_GAP, MapTouchRules.DOUBLE_TAP_REACH_DP))
        assertFalse(MapTouchRules.pairs(0.3001, 12f))
        assertFalse(MapTouchRules.pairs(0.2, 40.01f))
        assertFalse("never backwards in time", MapTouchRules.pairs(-0.01, 0f))
        assertTrue("in pixels, with the reach in pixels", MapTouchRules.pairs(0.1, 100f, reach = 40f * 2.75f))
    }

    @Test fun aLongerTouchAndHoldSettingStretchesTheHolds() {
        assertEquals(1.0, MapTouchRules.holdScale(400), 0.0)
        assertEquals("never shorter than iOS's", 1.0, MapTouchRules.holdScale(200), 0.0)
        assertEquals(2.5, MapTouchRules.holdScale(1000), 1e-9)
        val scale = MapTouchRules.holdScale(1000)
        assertEquals(MapTouchKind.PENDING, judge(true, 0.6, 2f, lifted = false, scale = scale))
        assertEquals(MapTouchKind.MENU, judge(true, 0.55 * 2.5, 2f, lifted = false, scale = scale))
        assertEquals("moved before the stretched hold: the camera", MapTouchKind.ORBIT, judge(true, 0.3, 25f, lifted = false, scale = scale))
        assertEquals(MapTouchKind.DRAG, judge(true, 0.25 * 2.5, 25f, lifted = false, scale = scale))
    }

    @Test fun theS05bTouchesAreJudgedTheSameWay() {
        // MapTouch.judge(120, 3, 8, false, 400) was TAP; HOLD at the hold; MOVE when moved
        assertEquals(MapTouchKind.TAP, MapTouchRules.judge(MapTouch(true, 0.12, 3f, lifted = true), 8f))
        assertEquals(MapTouchKind.MENU, MapTouchRules.judge(MapTouch(true, 0.55, 3f, lifted = false), 8f))
        assertEquals(MapTouchKind.ORBIT, MapTouchRules.judge(MapTouch(false, 0.9, 9f, lifted = false), 8f))
        assertEquals(MapTouchKind.DRAG, MapTouchRules.judge(MapTouch(true, 0.9, 9f, lifted = false), 8f))
        // MapTouch.judge(100, 0, 8, multi = true, 400) was MOVE: two fingers are the camera's, even still and lifted
        assertEquals(MapTouchKind.ORBIT, MapTouchRules.judge(MapTouch(true, 0.1, 0f, lifted = true, fingers = 2), 8f))
        assertEquals(MapTouchKind.ORBIT, MapTouchRules.judge(MapTouch(true, 0.6, 0f, lifted = false, fingers = 2), 8f))
    }

    // T2: the selection

    @Test fun theSelectionFollowsIOSsStory() {
        val s = MapSelection()
        assertFalse("nothing chosen: no card", s.cardShown)
        assertEquals("tap a body: chosen, card up, eases towards it", listOf(Select("a", false)), s.handle(TapBody("a"), 0.0))
        assertTrue(s.cardShown); assertEquals("a", s.selected)
        assertEquals("the double tap's second tap does not open twice", emptyList<MapSelectionEffect>(), s.handle(TapBody("a"), 0.2))
        assertEquals("the double tap opens", listOf(Open("a")), s.handle(DoubleTap("a"), 0.21))
        assertFalse(s.cardShown); assertEquals("a", s.opened)
        assertEquals("closing comes back to the map, still chosen", emptyList<MapSelectionEffect>(), s.handle(Closed, 3.0))
        assertEquals("a", s.selected); assertTrue(s.cardShown); assertNull(s.opened)
        assertEquals("tap it again: open", listOf(Open("a")), s.handle(TapBody("a"), 4.0))
        s.handle(Closed, 5.0)
        assertEquals("an open asked for twice at once opens once", listOf(Open("a")), s.handle(OpenCard, 6.0))
        assertEquals(emptyList<MapSelectionEffect>(), s.handle(OpenCard, 6.1))
        s.handle(Closed, 7.0)
        assertEquals("Show links dims round it", listOf(Links("a")), s.handle(ShowLinks, 8.0))
        assertTrue(s.linksShown)
        assertEquals("a chip chooses that body, the camera glides, the neighbourhood follows",
            listOf(Select("b", true), Links("b")), s.handle(Chip("b"), 9.0))
        assertEquals("b", s.selected)
        assertEquals("Show links again: everything back", listOf(Links(null)), s.handle(ShowLinks, 10.0))
        assertFalse(s.linksShown)
        assertEquals("tap empty space: let go, the card hides", listOf<MapSelectionEffect>(Clear), s.handle(TapEmpty, 11.0))
        assertFalse(s.cardShown)
        assertEquals("again: nothing more", emptyList<MapSelectionEffect>(), s.handle(TapEmpty, 12.0))
        assertEquals("a double tap on a body not chosen chooses and opens it",
            listOf(Select("a", false), Open("a")), s.handle(DoubleTap("a"), 13.0))
        s.handle(Closed, 14.0)
        assertEquals("hold: its options, choosing it first", listOf(Select("b", false), Menu("b")), s.handle(Hold("b"), 15.0))
        s.handle(ShowLinks, 16.0)
        assertEquals("a deleted body lets go, and the map comes back", listOf(Links(null), Clear), s.handle(Gone("b"), 17.0))
        assertNull(s.selected)
        assertEquals("another body going changes nothing", emptyList<MapSelectionEffect>(), s.handle(Gone("a"), 18.0))
        assertEquals("Open with nothing chosen does nothing", emptyList<MapSelectionEffect>(), s.handle(OpenCard, 19.0))
    }

    @Test fun aTapOnAnotherBodyChoosesItAndOnTheChosenOneOpensItWhateverTheCase() {
        val s = MapSelection()
        assertEquals("nothing chosen: a tap chooses", listOf(Select("a", false)), s.handle(TapBody("a"), 0.0))
        assertEquals("another body: a tap chooses it", listOf(Select("b", false)), s.handle(TapBody("b"), 1.0))
        assertEquals("the chosen body, later: a tap opens it", listOf(Open("B")), s.handle(TapBody("B"), 2.0))
    }

    @Test fun aHoldOnTheChosenBodyOnlyGivesItsOptions() {
        val s = MapSelection()
        s.handle(TapBody("a"), 0.0)
        assertEquals(listOf(Menu("A")), s.handle(Hold("A"), 1.0))
        assertEquals("a", s.selected)
    }

    @Test fun aDoubleTapWorksWhicheverWayItArrives() {
        // the first tap chooses, then the pair arrives as a double tap: one open
        val first = MapSelection()
        assertEquals(listOf(Select("a", false)), first.handle(TapBody("a"), 0.0))
        assertEquals(listOf(Open("a")), first.handle(DoubleTap("a"), 0.25))
        // or the second tap lands as a tap first, then the double tap: still one open
        val second = MapSelection()
        second.handle(TapBody("a"), 0.0)
        assertEquals(emptyList<MapSelectionEffect>(), second.handle(TapBody("a"), 0.25))
        assertEquals(listOf(Open("a")), second.handle(DoubleTap("a"), 0.26))
        assertEquals(emptyList<MapSelectionEffect>(), second.handle(DoubleTap("a"), 0.5))
    }

    @Test fun showLinksFollowsAChoiceAndALetGo() {
        val s = MapSelection()
        assertEquals("nothing chosen: nothing to show round", emptyList<MapSelectionEffect>(), s.handle(ShowLinks, 0.0))
        s.handle(TapBody("a"), 1.0)
        s.handle(ShowLinks, 2.0)
        assertEquals("a tap on another body moves the links round it", listOf(Select("b", false), Links("b")), s.handle(TapBody("b"), 3.0))
        assertEquals(listOf(Links(null), Clear), s.handle(TapEmpty, 4.0))
        assertFalse(s.linksShown)
    }

    @Test fun anOpenedBodyGoingClosesItsPage() {
        val s = MapSelection()
        s.handle(DoubleTap("a"), 0.0)
        assertEquals("a", s.opened)
        assertEquals(listOf<MapSelectionEffect>(Clear), s.handle(Gone("A"), 1.0))
        assertNull(s.opened); assertNull(s.selected)
        assertEquals("gone again: nothing more", emptyList<MapSelectionEffect>(), s.handle(Gone("a"), 2.0))
    }
}
