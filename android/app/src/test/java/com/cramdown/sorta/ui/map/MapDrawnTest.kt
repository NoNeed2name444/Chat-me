package com.cramdown.sorta.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The record ScreenshotTest waits on before it looks at the map (MapDrawn, Map3DView's MapDrawnKey). */
class MapDrawnTest {
    @Test fun countsFramesAndFramesWithBodiesThenStopsChanging() {
        // the Space map of four notes: the summary star alone, then its bodies, the first of
        // them seconds later where the GL builds each shader on its first draw
        var d = MapDrawn().after(0, 40)
        assertEquals(1, d.frames); assertEquals(0, d.bodyFrames); assertEquals(40L, d.firstFrameMs); assertEquals(-1L, d.firstBodyFrameMs)
        d = d.after(4, 5_200)
        assertEquals(2, d.frames); assertEquals(1, d.bodyFrames); assertEquals(4, d.bodies); assertEquals(5_200L, d.firstBodyFrameMs)
        d = d.after(4, 5_260).after(4, 5_300)
        assertEquals(MapDrawn.ENOUGH, d.frames); assertEquals(MapDrawn.ENOUGH, d.bodyFrames)
        // settled: the same record, so nothing more is handed to the main thread
        assertSame(d, d.after(5, 9_000))
        assertSame(d, d.after(0, 9_100))
        assertEquals(40L, d.firstFrameMs); assertEquals(5_200L, d.firstBodyFrameMs); assertEquals(4, d.bodies)
    }

    @Test fun aMapWithoutBodiesSettlesButStillCountsBodiesThatComeLater() {
        var d = MapDrawn()
        repeat(MapDrawn.ENOUGH) { d = d.after(0, 16L * (it + 1)) }
        assertEquals(MapDrawn.ENOUGH, d.frames); assertEquals(16L, d.firstFrameMs)
        assertSame(d, d.after(0, 1_000))
        // the camera came close to a note: its body counts from then on
        val later = d.after(2, 2_000)
        assertEquals(MapDrawn.ENOUGH, later.frames); assertEquals(1, later.bodyFrames); assertEquals(2, later.bodies); assertEquals(2_000L, later.firstBodyFrameMs)
    }

    @Test fun changesOnlyAFewTimesOverAThousandFrames() {
        var d = MapDrawn(width = 1080, height = 1950, renderer = "SwiftShader", maxPointSize = 1f, bodyStyles = 6)
        var changes = 0
        for (f in 1..1000) {
            val next = d.after(if (f == 1) 0 else 3 + f % 4, f * 16L)
            if (next != d) changes++
            d = next
        }
        assertEquals(MapDrawn.ENOUGH + 1, changes)
        assertEquals(MapDrawn(MapDrawn.ENOUGH, MapDrawn.ENOUGH, 3, 16, 32, 1080, 1950, "SwiftShader", 1f, 6), d)
    }

    @Test fun theLinksFieldsAreHandedOnWheneverTheyChangeEvenOnceSettled() {
        // S05e: Space's first view, its links drawn as strips of 40 samples, Curved
        var d = MapDrawn(build = 7)
        repeat(MapDrawn.ENOUGH) { d = d.after(4, 16L * (it + 1), 5, 40, false) }
        assertEquals(MapDrawn.ENOUGH, d.frames); assertEquals(5, d.links); assertEquals(40, d.samples); assertFalse(d.straight)
        // settled and unchanged: the same record
        assertSame(d, d.after(4, 1_000, 5, 40, false))
        assertSame("left as they were", d, d.after(4, 1_016))
        // Lines set to Straight: the same scene, its counts kept, and handed on at once
        val straight = d.after(4, 2_000, 5, 40, true)
        assertTrue(straight.straight)
        assertEquals(7, straight.build); assertEquals(MapDrawn.ENOUGH, straight.frames); assertEquals(MapDrawn.ENOUGH, straight.bodyFrames)
        assertSame(straight, straight.after(4, 2_016, 5, 40, true))
        // the camera brought more links in
        assertEquals(8, straight.after(4, 3_000, 8, 40, true).links)
    }

    @Test fun theHeldBodyIsHandedOnWhenItChanges() {
        // S05g2: nothing held at first, and the record says so
        val start = MapDrawn(build = 3)
        assertEquals(-1, start.grabbed); assertTrue(start.dragged.isEmpty()); assertEquals(0, start.moving); assertEquals(0, start.carried)
        assertSame("nothing held, nothing moving: the same record", start, start.dragging(-1, emptyList(), 0, 0))
        // body 7 picked up at (420, 980), nothing else off home yet
        val held = start.dragging(7, listOf(420f, 980f), 1, 0)
        assertEquals(7, held.grabbed); assertEquals(listOf(420f, 980f), held.dragged); assertEquals(1, held.moving); assertEquals(0, held.carried)
        assertEquals("the rest kept", 3, held.build)
        assertSame(held, held.dragging(7, listOf(420f, 980f), 1, 0))
        // moved: its spot follows, and a neighbour is pulled
        val moved = held.dragging(7, listOf(500f, 940f), 2, 1)
        assertEquals(listOf(500f, 940f), moved.dragged); assertEquals(2, moved.moving); assertEquals(1, moved.carried)
        // let go: none held, both still off home until they spring back
        val free = moved.dragging(-1, emptyList(), 2, 2)
        assertEquals(-1, free.grabbed); assertTrue(free.dragged.isEmpty()); assertEquals(2, free.carried)
        val home = free.dragging(-1, emptyList(), 0, 0)
        assertEquals(0, home.moving)
        assertEquals("frames still count the same way", home.frames, home.after(4, 100).frames - 1)
        // the marks and the held body are separate records: one never clears the other
        val both = home.marking(5, true, 2, false, 0, 0).dragging(7, listOf(1f, 2f), 1, 0)
        assertEquals(5, both.chosen); assertEquals(7, both.grabbed)
        assertEquals(5, both.dragging(-1, emptyList(), 0, 0).chosen)
        // the held body's own offset and the chosen one's, in the world: a carry read whatever the depth
        val carrying = both.dragging(7, listOf(1f, 2f), 2, 1, listOf(0.5f, 0f, -0.25f), listOf(0.45f, 0f, -0.2f))
        assertEquals(listOf(0.5f, 0f, -0.25f), carrying.heldOffset); assertEquals(listOf(0.45f, 0f, -0.2f), carrying.chosenOffset)
        assertSame(carrying, carrying.dragging(7, listOf(1f, 2f), 2, 1, listOf(0.5f, 0f, -0.25f), listOf(0.45f, 0f, -0.2f)))
        val homeAgain = carrying.dragging(-1, emptyList(), 0, 0)
        assertTrue("home, no offsets", homeAgain.heldOffset.isEmpty() && homeAgain.chosenOffset.isEmpty())
        // the let-go judged by frames: counted past ENOUGH, its bound and the frame's interval kept
        val settling = homeAgain.dragging(-1, emptyList(), 1, 0, letGoFramesNow = 140, restFramesNow = 196, frameMsNow = 333.3f)
        assertEquals(140, settling.letGoFrames); assertEquals(196, settling.restFrames); assertEquals(333.3f, settling.frameMs, 0f)
        assertTrue("not capped as frames is", settling.letGoFrames > MapDrawn.ENOUGH)
        assertSame(settling, settling.dragging(-1, emptyList(), 1, 0, letGoFramesNow = 140, restFramesNow = 196, frameMsNow = 333.3f))
        assertEquals("left out, they are kept", 140, settling.dragging(-1, emptyList(), 0, 0).letGoFrames)
    }

    @Test fun anIdleMapStopsAfterAQuietSecondWithThreeFramesFinished() {
        val second = 1_000_000_000L
        // a phone: three frames inside the first 50 ms, so the quiet second decides
        assertFalse(FrameQuiet.settled(second - 1, 60))
        assertTrue(FrameQuiet.settled(second, MapDrawn.ENOUGH))
        // the emulator's software GL, whose first frame took 1.4 s: a quiet second, but too few frames
        assertFalse(FrameQuiet.settled(1_500_000_000L, 1))
        assertFalse(FrameQuiet.settled(4 * second, MapDrawn.ENOUGH - 1))
        assertTrue(FrameQuiet.settled(4 * second, MapDrawn.ENOUGH))
        // a surface that never draws stops after ten quiet seconds
        assertFalse(FrameQuiet.settled(10 * second - 1, 0))
        assertTrue(FrameQuiet.settled(10 * second, 0))
    }

    // S05h: the Universe's fields, no words
    @Test fun theUniverseFieldsChangeOnlyWithWhatIsDrawn() {
        val d = MapDrawn()
        assertEquals(emptyList<Int>(), d.roles)
        assertEquals(-1, d.flown)
        assertEquals(0, d.nearLinks + d.archedLinks + d.farLinks + d.orbitSeconds)
        val roles = listOf(2, 3, 0, 21, 2, 6, 1, 2, 0)
        val u = d.universe(roles, 12, 9, 4, 0, 1f)
        assertEquals(roles, u.roles)
        assertEquals(12, u.nearLinks); assertEquals(9, u.archedLinks); assertEquals(4, u.farLinks)
        assertEquals(0, u.orbitSeconds); assertEquals(1f, u.orbitRate)
        // the same frame again: the same record, so nothing more goes to the main thread
        assertSame(u, u.universe(roles, 12, 9, 4, 0, 1f))
        // the clock in whole seconds and the rate to a hundredth: a new second or rate is a new record
        val later = u.universe(roles, 12, 9, 4, 3, 0.04f)
        assertEquals(3, later.orbitSeconds); assertEquals(0.04f, later.orbitRate)
        // Fly in, and Recentre back
        val flown = later.flying(5)
        assertEquals(5, flown.flown)
        assertSame(flown, flown.flying(5))
        assertEquals(-1, flown.flying(-1).flown)
        // the fields leave the rest of the record alone
        assertEquals(later.copy(flown = 5), flown)
        // a held body's members, each its scene index and its own offset; the same members, the same record
        assertEquals(emptyList<Float>(), d.memberOffsets)
        val members = listOf(4f, 0.5f, 0f, 0f, 7f, 0.49f, 0.01f, 0f)
        val held = flown.dragging(3, listOf(10f, 20f), 3, 2, listOf(0.5f, 0f, 0f), memberOffsetsNow = members)
        assertEquals(members, held.memberOffsets)
        assertSame(held, held.dragging(3, listOf(10f, 20f), 3, 2, listOf(0.5f, 0f, 0f), memberOffsetsNow = members))
        assertEquals(emptyList<Float>(), held.dragging(-1, emptyList(), 0, 0, memberOffsetsNow = emptyList()).memberOffsets)
    }

    // S05h: the orbit clock's steps, counted past ENOUGH as frames is not, so a held finger's ease is judged by them
    @Test fun theOrbitClocksStepsAreNeverCapped() {
        var d = MapDrawn()
        assertEquals(0, d.orbitSteps)
        repeat(1_000) { d = d.after(5, 16L * (it + 1)).stepped() }
        assertEquals(1_000, d.orbitSteps)
        assertEquals("frames still stop at ENOUGH", MapDrawn.ENOUGH, d.frames)
        assertEquals("a step is a new record", 1_001, d.stepped().orbitSteps)
        // without a step (a still map) the record stays the same
        val still = d.after(5, 20_000)
        assertSame(d, still)
        assertEquals(d.copy(orbitSteps = 0), d.stepped().copy(orbitSteps = 0))
    }

    // S05h repair 4: a press's seconds on the map's own clock, never capped, and when its ease got under 0.05
    @Test fun thePressedSecondsAreTheMapsOwnAndNeverCapped() {
        var d = MapDrawn()
        assertEquals(0f, d.pressedSeconds, 0f)
        assertEquals("none till a press slows the clock", -1f, d.pressSlowedSeconds, 0f)
        repeat(1_000) { d = d.after(5, 16L * (it + 1)).stepped().pressing((it + 1) * 0.25f, if (it >= 1) 0.3f else -1f) }
        assertEquals(250f, d.pressedSeconds, 1e-3f)
        assertEquals(0.3f, d.pressSlowedSeconds, 0f)
        assertEquals("frames still stop at ENOUGH", MapDrawn.ENOUGH, d.frames)
        // unchanged: the same record, so a still map hands nothing on
        assertSame(d, d.pressing(d.pressedSeconds, 0.3f))
        // either moving is a new record, with nothing else changed
        val more = d.pressing(250.25f, 0.3f)
        assertEquals(250.25f, more.pressedSeconds, 0f)
        assertEquals(d, more.copy(pressedSeconds = d.pressedSeconds))
        val next = d.pressing(d.pressedSeconds, -1f)
        assertEquals(-1f, next.pressSlowedSeconds, 0f)
        assertEquals(d, next.copy(pressSlowedSeconds = 0.3f))
    }

    // S05h repair 5: the uptime of the frame that first took the press, kept while it lasts, −1 after
    @Test fun thePressTakenAtIsTheFirstPressedFramesUptime() {
        var clock = 1_000L
        var reads = 0
        val now = { reads++; clock }
        var d = MapDrawn().after(5, 16).stepped().pressing(0f, -1f, false, nowMs = now)
        assertEquals("none before a press", -1L, d.pressTakenAtMs)
        assertEquals("the clock is not read without a press", 0, reads)
        d = d.stepped().pressing(1f / 60, -1f, true, nowMs = now)
        assertEquals(1_000L, d.pressTakenAtMs)
        repeat(30) {
            clock += 16
            d = d.stepped().pressing(d.pressedSeconds + 1f / 60, if (it >= 16) 0.3f else -1f, true, nowMs = now)
        }
        assertEquals("kept from the first pressed frame", 1_000L, d.pressTakenAtMs)
        assertEquals("the clock read once, at that frame", 1, reads)
        // unchanged: the same record
        assertSame(d, d.pressing(d.pressedSeconds, d.pressSlowedSeconds, true, nowMs = now))
        // the lift: −1, nothing else changed
        val lifted = d.pressing(d.pressedSeconds, d.pressSlowedSeconds, false, nowMs = now)
        assertEquals(-1L, lifted.pressTakenAtMs)
        assertEquals(d, lifted.copy(pressTakenAtMs = 1_000L))
        // a new press takes the uptime of its own first frame
        clock = 9_000L
        val again = lifted.pressing(lifted.pressedSeconds + 1f / 60, -1f, true, nowMs = now)
        assertEquals(9_000L, again.pressTakenAtMs)
        assertEquals(2, reads)
    }

    // S05h repair 6: the uptime of the frame where the press first slowed the rate under 0.05, kept while
    // the press lasts, −1 before that and after it
    @Test fun thePressSlowedAtIsTheFirstSlowFramesUptime() {
        var clock = 1_000L
        var reads = 0
        val now = { reads++; clock }
        var d = MapDrawn().after(5, 16).stepped().pressing(0f, -1f, takenNow = false, slowNow = true, nowMs = now)
        assertEquals("none without a press, whatever the rate", -1L, d.pressSlowedAtMs)
        assertEquals(0, reads)
        d = d.stepped().pressing(1f / 60, -1f, takenNow = true, slowNow = false, nowMs = now)
        assertEquals(1_000L, d.pressTakenAtMs)
        assertEquals("not slowed yet", -1L, d.pressSlowedAtMs)
        repeat(17) {
            clock += 16
            d = d.stepped().pressing(d.pressedSeconds + 1f / 60, -1f, takenNow = true, slowNow = false, nowMs = now)
        }
        assertEquals(-1L, d.pressSlowedAtMs)
        assertEquals("the clock read only when the press was taken", 1, reads)
        clock += 16
        d = d.stepped().pressing(d.pressedSeconds + 1f / 60, 0.3f, takenNow = true, slowNow = true, nowMs = now)
        assertEquals("the first slow frame's uptime", 1_288L, d.pressSlowedAtMs)
        assertEquals("the press's own time kept", 1_000L, d.pressTakenAtMs)
        assertEquals(2, reads)
        repeat(30) {
            clock += 16
            d = d.stepped().pressing(d.pressedSeconds + 1f / 60, 0.3f, takenNow = true, slowNow = true, nowMs = now)
        }
        assertEquals("kept from the first slow frame", 1_288L, d.pressSlowedAtMs)
        assertEquals("the clock read once for it", 2, reads)
        assertSame(d, d.pressing(d.pressedSeconds, d.pressSlowedSeconds, takenNow = true, slowNow = true, nowMs = now))
        // the lift: both −1, nothing else changed
        val lifted = d.pressing(d.pressedSeconds, d.pressSlowedSeconds, takenNow = false, slowNow = false, nowMs = now)
        assertEquals(-1L, lifted.pressSlowedAtMs)
        assertEquals(-1L, lifted.pressTakenAtMs)
        assertEquals(d, lifted.copy(pressTakenAtMs = 1_000L, pressSlowedAtMs = 1_288L))
        // a press taken and slowed at one frame reads the clock once for both
        clock = 9_000L
        val both = lifted.pressing(lifted.pressedSeconds + 0.25f, 0.25f, takenNow = true, slowNow = true, nowMs = now)
        assertEquals(9_000L, both.pressTakenAtMs)
        assertEquals(9_000L, both.pressSlowedAtMs)
        assertEquals(3, reads)
    }

    // S05h repair 7: at the frame that takes the press, when it reached the map and when the frame
    // before began, kept while the press lasts, −1 before and after; no clock read of their own
    @Test fun thePressArrivalAndTheFrameBeforeAreNotedAtTheTakingFrame() {
        var clock = 1_000L
        var reads = 0
        val now = { reads++; clock }
        var d = MapDrawn().after(5, 16).stepped().pressing(0f, -1f, takenNow = false, arrivedMs = 980L, prevFrameMs = 960L, nowMs = now)
        assertEquals("none before the press is taken", -1L, d.pressArrivedAtMs)
        assertEquals(-1L, d.pressPrevFrameAtMs)
        d = d.stepped().pressing(1f / 60, -1f, takenNow = true, arrivedMs = 990L, prevFrameMs = 984L, nowMs = now)
        assertEquals(990L, d.pressArrivedAtMs)
        assertEquals(984L, d.pressPrevFrameAtMs)
        assertEquals(1_000L, d.pressTakenAtMs)
        assertTrue("no frame between the arrival and the taking",
            d.pressPrevFrameAtMs <= d.pressArrivedAtMs && d.pressArrivedAtMs <= d.pressTakenAtMs)
        repeat(30) {
            clock += 16
            d = d.stepped().pressing(d.pressedSeconds + 1f / 60, -1f, takenNow = true, arrivedMs = 990L, prevFrameMs = clock - 16, nowMs = now)
        }
        assertEquals("kept from the taking frame", 990L, d.pressArrivedAtMs)
        assertEquals(984L, d.pressPrevFrameAtMs)
        assertEquals("the clock read once, for the taking", 1, reads)
        assertSame(d, d.pressing(d.pressedSeconds, d.pressSlowedSeconds, takenNow = true, arrivedMs = 990L, prevFrameMs = 5_000L, nowMs = now))
        // the lift: both −1, nothing else changed
        val lifted = d.pressing(d.pressedSeconds, d.pressSlowedSeconds, takenNow = false, arrivedMs = -1L, prevFrameMs = 2_000L, nowMs = now)
        assertEquals(-1L, lifted.pressArrivedAtMs)
        assertEquals(-1L, lifted.pressPrevFrameAtMs)
        assertEquals(d, lifted.copy(pressTakenAtMs = 1_000L, pressArrivedAtMs = 990L, pressPrevFrameAtMs = 984L))
        // a new press: its own arrival and frame before
        clock = 9_000L
        val again = lifted.pressing(lifted.pressedSeconds + 1f / 60, -1f, takenNow = true, arrivedMs = 8_950L, prevFrameMs = 8_700L, nowMs = now)
        assertEquals(8_950L, again.pressArrivedAtMs)
        assertEquals(8_700L, again.pressPrevFrameAtMs)
        assertEquals(9_000L, again.pressTakenAtMs)
    }
}
