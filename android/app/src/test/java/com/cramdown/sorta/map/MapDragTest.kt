package com.cramdown.sorta.map

import com.cramdown.sorta.ui.map.FrameQuiet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

// Hold to move (S05g2): the spring run, against iOS's GraphMotion (grab, drag, release, the frame
// step, integrate and integrateOrbits, GraphMotion.swift:1246-1283, :1617-1667, :1703-1798). The
// homes are made up: a few bodies on a line, a unit note radius.
class MapDragTest {
    private val frame = 1f / 60f

    private fun line(n: Int): (Int, Double) -> Vec3 = { i, _ -> Vec3(i.toFloat(), 0f, 0f) }

    private fun run(n: Int = 1, path: MapDragPath = MapDragPath.ORBITS, lively: Boolean = true, parents: IntArray? = null,
                    links: IntArray = IntArray(0)) = MapDrag(n, path, 1f, line(n), parents, links, lively)

    /** Body [i] held at offset [d] from home for a frame, then let go at rest; returns its x offset each frame for [seconds]. */
    private fun letGoAtRest(drag: MapDrag, d: Float, seconds: Float, i: Int = 0): FloatArray {
        drag.grab(i)
        drag.drag(drag.homeOf(i) + Vec3(d, 0f, 0f))
        drag.step(frame)
        assertEquals(d, drag.offset(i, 0), 1e-5f)
        drag.release(still = true)
        return FloatArray((seconds / frame).toInt()) { drag.step(frame); drag.offset(i, 0) }
    }

    @Test fun letGoAtRestItOvershootsHomeByIOSsNinePointFivePercent() {
        for (path in MapDragPath.entries) {
            val d = 2f
            val xs = letGoAtRest(run(path = path), d, 6f)
            // the first extreme past home, then the one after it, back on the first side
            var k = 0
            while (k + 1 < xs.size && xs[k + 1] < xs[k]) k++
            val first = -xs[k] / d
            var j = k
            while (j + 1 < xs.size && xs[j + 1] > xs[j]) j++
            val second = xs[j] / d
            assertEquals("$path: overshoot", 0.095f, first, 0.01f)
            assertTrue("$path: its second extreme, $second", second < 0.015f)
        }
    }

    @Test fun itSettlesWithinIOSsTimes() {
        val d = 2f
        val xs = letGoAtRest(run(), d, 6f)
        fun at(seconds: Float) = abs(xs[(seconds / frame).toInt() - 1]) / d
        assertTrue("2 s: ${at(2f)}", at(2f) < 0.07f)
        for (k in (2.1f / frame).toInt() - 1 until xs.size) assertTrue("from 2.1 s, frame $k", abs(xs[k]) / d < 0.06f)
        for (k in (5f / frame).toInt() - 1 until xs.size) assertTrue("by 5 s, frame $k", abs(xs[k]) / d < 0.001f)
    }

    @Test fun withAnimationsOffItNeverOvershoots() {
        for (path in MapDragPath.entries) {
            val d = 2f
            val xs = letGoAtRest(run(path = path, lively = false), d, 8f)
            assertTrue("$path: never past home", xs.all { it >= -1e-6f })
            assertTrue("$path: home at the end", abs(xs.last()) < 0.001f * d)
            assertTrue("$path: and falling all the way", (1 until xs.size).all { xs[it] <= xs[it - 1] + 1e-7f })
        }
    }

    @Test fun theFingersSpeedIsSmoothedOverFiftyMilliseconds() {
        for (path in MapDragPath.entries) {
            val drag = run(path = path)
            drag.grab(0)
            val speed = 3f
            val dt = 1f / 240f
            var x = 0f
            repeat(12) {
                x += speed * dt
                drag.drag(drag.homeOf(0) + Vec3(x, 0f, 0f))
                drag.step(dt)
            }
            // 12 frames of 1/240 s: 50 ms, so 1 − e^(−1) of the finger's speed
            assertEquals("$path", speed * (1f - exp(-1f)), drag.fingerSpeed.x, 1e-3f)
            assertEquals(0f, drag.fingerSpeed.y, 1e-6f)
        }
    }

    @Test fun aFlingIsCappedAtIOSsEightInNoteRadiiAndNoneWithAnimationsOff() {
        assertEquals(43.0f, MapDrag.FLING_LIMIT_RADII, 0.05f)
        for (lively in listOf(true, false)) {
            val drag = MapDrag(1, MapDragPath.ORBITS, 0.5f, line(1), lively = lively)
            drag.grab(0)
            // a flick: 4 units a frame for half a second
            for (f in 1..30) { drag.drag(Vec3(4f * f, 0f, 0f)); drag.step(frame) }
            assertTrue(drag.fingerSpeed.length() > MapDrag.FLING_LIMIT_RADII * 0.5f)
            drag.release()
            val fling = drag.velocityOf(0).length()
            if (lively) assertEquals("capped at 43 note radii a second", MapDrag.FLING_LIMIT_RADII * 0.5f, fling, 1e-3f)
            else assertEquals("no fling", 0f, fling, 0f)
        }
        // a slow let-go keeps its speed, and one held still to the options keeps none
        val slow = run()
        slow.grab(0)
        for (f in 1..30) { slow.drag(Vec3(0.02f * f, 0f, 0f)); slow.step(frame) }
        val kept = slow.fingerSpeed.length()
        slow.release()
        assertEquals(kept, slow.velocityOf(0).length(), 1e-5f)
        val still = run()
        still.grab(0)
        for (f in 1..30) { still.drag(Vec3(0.02f * f, 0f, 0f)); still.step(frame) }
        still.release(still = true)
        assertEquals(0f, still.velocityOf(0).length(), 0f)
    }

    @Test fun framesAreSplitAtOneHundredTwentiethWithTheirStepClamped() {
        val drag = run()
        assertEquals(1, drag.piecesFor(0f))
        assertEquals(1, drag.piecesFor(1f / 120f))
        assertEquals(2, drag.piecesFor(1f / 119f))
        assertEquals(2, drag.piecesFor(1f / 60f))
        assertEquals(4, drag.piecesFor(1f / 30f))
        assertEquals("0.05 s at most: 6 pieces", 6, drag.piecesFor(0.2f))
        assertEquals(6, drag.piecesFor(0.05f))
        // a long frame moves the springs as far as a 0.05 s one, and a frame of nothing as far as 1 ms
        fun after(dt: Float): Float {
            val d = run()
            d.grab(0); d.drag(Vec3(1f, 0f, 0f)); d.step(frame); d.release(still = true)
            d.step(dt)
            return d.offset(0, 0)
        }
        assertEquals(after(0.05f), after(0.2f), 0f)
        assertEquals(after(0.001f), after(0f), 0f)
        assertTrue(after(0.05f) < after(0.001f))
    }

    @Test fun onASlowDeviceALetGoComesHomeInIOSsFramesAndAsksForFramesTillThen() {
        // S05g2 repair 2b, CI's tablet: animations off, a note radius of 0.3, a body let go still 10 radii off
        // home, frames 0.25 s apart, each stepped as iOS steps it, 0.05 s (GraphMotion.swift:1617). Critically
        // damped, ω = √6: x(t) = 10 (1 + ωt) e^(−ωt) radii, under the rest's 1e-3 at ωt ≈ 11.77, t ≈ 4.81 s,
        // 97 frames of 0.05 s; the speed there, 10 ω² t e^(−ωt) ≈ 0.002 radii a second, is under its 1e-2.
        // The 1/120 s pieces add a frame or two: at rest within 100 frames, not before 95
        assertEquals(97, ceil(4.81 / 0.05).toInt())
        for (path in MapDragPath.entries) {
            val drag = MapDrag(1, path, 0.3f, line(1), lively = false)
            drag.grab(0)
            drag.drag(drag.homeOf(0) + Vec3(3f, 0f, 0f))
            drag.step(frame)
            drag.release(still = true)
            assertEquals("$path: the bound published at the let-go", MapDrag.framesToRest(10f, lively = false) * 2, drag.restFrames)
            // the frame loop as Map3DView runs it: a frame while the renderer is animating (busy) or not yet quiet
            var clock = 0L
            var lastActive = 0L
            var finishedAtActive = 0
            var finished = 0
            var frames = 0
            while (drag.moving > 0) {
                if (drag.busy) { lastActive = clock; finishedAtActive = finished }
                assertFalse("$path: frames stopped while off home, after $frames frames",
                    FrameQuiet.settled(clock - lastActive, finished - finishedAtActive))
                drag.step(0.25f); finished++; frames++; clock += 250_000_000L
                assertEquals("$path: frames since the let-go", frames, drag.letGoFrames)
                assertTrue("$path: still off home after $frames frames: ${drag.offsetOf(0)}", frames <= 100)
            }
            assertTrue("$path: at rest in $frames frames, fewer than iOS's spring allows", frames >= 95)
            assertTrue("$path: within the published bound", frames <= drag.restFrames)
            // home: no longer busy, so the loop goes quiet after its second and three frames, and nothing moves again
            repeat(20) {
                drag.step(0.25f); finished++; clock += 250_000_000L
                assertFalse("$path: at rest, frames still asked for", drag.busy)
                assertEquals(0, drag.moving)
                assertEquals(0f, drag.offsetOf(0).length(), 0f)
                assertEquals("$path: the count stops at rest", frames, drag.letGoFrames)
            }
            assertTrue("$path: the loop goes quiet", FrameQuiet.settled(clock - lastActive, finished - finishedAtActive))
        }
        // the bound from iOS's spring, one body: 97 frames from 10 radii, more with animations on (less damped)
        assertTrue(MapDrag.framesToRest(10f, lively = false) in 95..100)
        assertTrue(MapDrag.framesToRest(10f, lively = true) > MapDrag.framesToRest(10f, lively = false))
        assertEquals(0, MapDrag.framesToRest(0f, lively = false))
    }

    @Test fun onTheLinkPathADraggedBodyPullsItsNeighbourAndBothComeHome() {
        // three bodies on a line, 0–1 linked, 2 alone: 0 pulled up by the finger
        val drag = run(3, MapDragPath.LINKS, links = intArrayOf(0, 1))
        drag.grab(0)
        for (f in 1..60) { drag.drag(Vec3(0f, 0.02f * f, 0f)); drag.step(frame) }
        val neighbour = drag.offsetOf(1)
        assertTrue("towards the dragged body: $neighbour", neighbour.y > 0.05f)
        assertTrue("and in along the link: $neighbour", neighbour.x < 0f)
        assertEquals("the one with no link stays home", 0f, drag.offsetOf(2).length(), 0f)
        assertEquals(2, drag.moving)
        assertEquals(1, drag.carried)
        drag.release()
        repeat(600) { drag.step(frame) }
        assertEquals(0f, drag.offsetOf(0).length(), 0f)
        assertEquals(0f, drag.offsetOf(1).length(), 0f)
        assertEquals(0, drag.moving)
        assertFalse(drag.busy)
    }

    @Test fun onTheOrbitPathNoLinkPulls() {
        // Space's Universe: the same link, no parents, so its neighbour never moves
        val drag = run(2, MapDragPath.ORBITS, links = intArrayOf(0, 1))
        drag.grab(0)
        for (f in 1..60) { drag.drag(Vec3(0f, 0.02f * f, 0f)); drag.step(frame) }
        assertEquals(1.2f, drag.offset(0, 1), 1e-5f)
        assertEquals(0f, drag.offsetOf(1).length(), 0f)
        assertEquals(1, drag.moving)
        assertEquals(0, drag.carried)
    }

    @Test fun aChildIsCarriedByWhatItsParentMovedThenComesHome() {
        for (lively in listOf(true, false)) {
            val drag = run(2, lively = lively, parents = intArrayOf(-1, 0))
            val d = 1.5f
            drag.grab(0)
            drag.drag(drag.homeOf(0) + Vec3(d, 0f, 0f))
            drag.step(frame)
            val keep = if (lively) 0.85f else 1f
            assertEquals("lively $lively: carried ${keep}d in the step", keep * d, drag.offset(1, 0), 0.005f * d)
            assertEquals(1, drag.carried)
            drag.release()
            repeat(600) { drag.step(frame) }
            assertEquals(0f, drag.offsetOf(1).length(), 0f)
            assertEquals(0, drag.moving)
        }
    }

    @Test fun aParentHeldCarriesItsChildAndGrandchildOnceNeverTwice() {
        val homes: (Int, Double) -> Vec3 = { i, _ -> Vec3(0f, i * 0.5f, 0f) }
        val drag = MapDrag(3, MapDragPath.ORBITS, 1f, homes, parents = intArrayOf(-1, 0, 1))
        val d = Vec3(1.2f, 0f, -0.4f)
        val size = d.length()
        drag.grab(0)
        var highest = 0f
        repeat(300) {
            drag.drag(drag.homeOf(0) + d)
            drag.step(frame)
            highest = max(highest, max(drag.offsetOf(1).length(), drag.offsetOf(2).length()))
        }
        assertTrue("never above 1.05 d: ${highest / size}", highest <= 1.05f * size)
        for (i in 1..2) {
            assertEquals("body $i settles at d", 0f, (drag.offsetOf(i) - d).length(), 0.02f * size)
            // drawn at home plus its own offset only: about d from home, not 1.85 d or 2 d
            val drawn = drag.placed(i, drag.homeOf(i))
            assertEquals(0f, (drawn - (drag.homeOf(i) + drag.offsetOf(i))).length(), 0f)
            assertEquals(0f, (drawn - (drag.homeOf(i) + d)).length(), 0.02f * size)
            assertTrue((drawn - (drag.homeOf(i) + d * 1.85f)).length() > 0.5f * size)
            assertTrue((drawn - (drag.homeOf(i) + d * 2f)).length() > 0.5f * size)
        }
        assertEquals(3, drag.moving)
        assertEquals(2, drag.carried)
    }

    @Test fun aParentAfterItsChildIsNoParent() {
        // as iOS's hasParent (p < i): body 0's "parent" 1 carries nothing
        val drag = run(2, parents = intArrayOf(1, -1))
        drag.grab(1)
        drag.drag(drag.homeOf(1) + Vec3(1f, 0f, 0f))
        repeat(30) { drag.step(frame) }
        assertEquals(0f, drag.offsetOf(0).length(), 0f)
    }

    @Test fun theOrbitClockEasesToAStopWhileHeldAndBack() {
        val drag = run()
        repeat(60) { drag.step(frame) }
        assertEquals(1f, drag.orbitRate, 1e-6f)
        assertEquals(1.0, drag.orbitTime, 1e-4)
        drag.grab(0)
        repeat(18) { drag.step(frame) }
        // 0.3 s at τ 0.1 s
        assertEquals(exp(-3f), drag.orbitRate, 0.01f)
        val held = drag.orbitTime
        repeat(60) { drag.step(frame) }
        assertTrue("almost still while held", drag.orbitTime - held < 0.01)
        drag.release()
        repeat(90) { drag.step(frame) }
        // 1.5 s at τ 0.5 s
        assertEquals(1f - exp(-3f), drag.orbitRate, 0.02f)
        // with animations off the plan's own picture: time 0
        val still = run(lively = false)
        still.grab(0)
        repeat(60) { still.step(frame) }
        still.release()
        repeat(60) { still.step(frame) }
        assertEquals(0.0, still.orbitTime, 0.0)
    }

    @Test fun aPressedOrHoveredBodyStopsTheOrbitClockToo() {
        // S05h: iOS's busy is pressedNote, hovered or grabbed (advanceOrbits, GraphMotion.swift:1752)
        for (how in listOf("pressed", "hovered")) {
            val drag = run()
            repeat(60) { drag.step(frame) }
            if (how == "pressed") drag.pressed = 0 else drag.hovered = 0
            repeat(18) { drag.step(frame) }
            assertTrue("$how: within 5 % of 0 by 0.3 s, ${drag.orbitRate}", drag.orbitRate < 0.05f)
            if (how == "pressed") drag.pressed = -1 else drag.hovered = -1
            repeat(150) { drag.step(frame) }
            assertTrue("$how: within 5 % of 1 by 2.5 s after, ${drag.orbitRate}", drag.orbitRate > 0.95f)
        }
        val still = run(lively = false)
        still.pressed = 0
        still.hovered = 0
        repeat(60) { still.step(frame) }
        still.pressed = -1
        still.hovered = -1
        repeat(60) { still.step(frame) }
        assertEquals("animations off: the clock stays at 0", 0.0, still.orbitTime, 0.0)
    }

    @Test fun aHeldStarCarriesItsPlanetsAndMoons() {
        // a star (0), its planet (1) and the planet's moon (2): held at d, each settles at d within
        // 2 %, never above 1.05 d (S05h A2)
        for (lively in listOf(true, false)) {
            val drag = run(3, lively = lively, parents = intArrayOf(-1, 0, 1))
            val d = 3f
            drag.grab(0)
            drag.drag(drag.homeOf(0) + Vec3(d, 0f, 0f))
            var most = 0f
            repeat((4f / frame).toInt()) {
                drag.step(frame)
                most = max(most, max(drag.offset(1, 0), drag.offset(2, 0)))
            }
            for (i in 1..2) assertEquals("lively $lively: body $i settles at d", d, drag.offset(i, 0), d * 0.02f)
            assertTrue("lively $lively: never above 1.05 d, $most", most <= 1.05f * d)
        }
        // the same family out of index order (the Universe's chunk order): the moon 0, the star 1 and the
        // planet 2, ranked star, planet, moon, carry as above; without the rank the moon carries nothing
        for (ranked in listOf(true, false)) {
            val drag = MapDrag(3, MapDragPath.ORBITS, 1f, line(3), intArrayOf(2, -1, 1), rank = if (ranked) intArrayOf(2, 0, 1) else null)
            val d = 3f
            drag.grab(1)
            drag.drag(drag.homeOf(1) + Vec3(d, 0f, 0f))
            repeat((4f / frame).toInt()) { drag.step(frame) }
            assertEquals("ranked $ranked: the planet follows", d, drag.offset(2, 0), d * 0.02f)
            if (ranked) assertEquals("the moon follows its planet", d, drag.offset(0, 0), d * 0.02f)
            else assertEquals("unranked, a parent after its child carries nothing", 0f, drag.offset(0, 0), 0f)
        }
    }

    @Test fun atRestItDoesNoWork() {
        val drag = run(1000, MapDragPath.LINKS, links = IntArray(998) { it / 2 + it % 2 })
        repeat(120) { drag.step(frame) }
        assertEquals(0, drag.worked)
        assertEquals(0, drag.moving)
        assertFalse(drag.busy)
        assertTrue(drag.movingBodies().isEmpty())
        // a body moved and let go: the work is the bodies off home, and none once all are home
        drag.grab(500)
        drag.drag(drag.homeOf(500) + Vec3(0f, 0.5f, 0f))
        drag.step(frame)
        assertTrue("only the moved body and those its links reach: ${drag.worked}", drag.worked in 1..3)
        drag.release()
        var most = 0
        repeat(900) { drag.step(frame); most = max(most, drag.worked) }
        assertTrue("never every body: $most", most < 1000)
        assertEquals(0, drag.worked)
        assertEquals(0, drag.moving)
        assertFalse(drag.busy)
        for (i in 0 until 1000) assertEquals(0f, drag.offsetOf(i).length(), 0f)
    }

    @Test fun aHeldBodyWakesOnlyTheNeighboursItVisiblyPulls() {
        // a chain of 1001 ideas, each linked to the next: the middle one held half a radius along the chain for 3 s
        val drag = run(1001, MapDragPath.LINKS, links = IntArray(2000) { it / 2 + it % 2 })
        drag.grab(500)
        var most = 0
        repeat(180) { drag.drag(drag.homeOf(500) + Vec3(0.5f, 0f, 0f)); drag.step(frame); most = max(most, drag.worked) }
        val woken = drag.movingBodies()
        // each link on hands about 0.31 of a move (4 against 6, both sides pulling): 0.5, 0.157, 0.049, then
        // under the wake; at the rest threshold the run would reach six links each way, 13 bodies
        assertTrue("the held body and three links each way: $most stepped, ${woken.toList()}", most <= 7)
        assertEquals((497..503).toList(), woken.toList())
        // the run's edge is stepped though it moves too slowly to count as moving: a link pulls it
        for (e in listOf(497, 503)) {
            val off = drag.offsetOf(e).length()
            assertTrue("the edge, $e, is pulled and under the wake: $off", off > 0.005f && off < MapDrag.WAKE_OFFSET)
        }
        // what is left out would move under 0.4 × the wake, the most one link of 4 against a home of 6 hands on
        assertEquals(0f, drag.offsetOf(496).length(), 0f)
        assertEquals(0f, drag.offsetOf(504).length(), 0f)
        drag.release()
        repeat(900) { drag.step(frame) }
        assertEquals(0, drag.moving)
    }

    @Test fun theHeldBodyFollowsItsTargetEachFrame() {
        for (path in MapDragPath.entries) {
            val drag = run(1, path)
            drag.grab(0)
            assertEquals(0, drag.grabbed)
            assertEquals(1, drag.grabs)
            assertTrue(drag.busy)
            for (f in 1..20) {
                val to = Vec3(0.1f * f, -0.05f * f, 0.02f * f)
                drag.drag(to)
                drag.step(frame)
                assertEquals("$path frame $f", 0f, (drag.placed(0, drag.homeOf(0)) - to).length(), 1e-5f)
            }
            drag.release()
            assertEquals(-1, drag.grabbed)
            // nothing is kept: home again
            repeat(600) { drag.step(frame) }
            assertEquals(0f, drag.offsetOf(0).length(), 0f)
            assertEquals(0, drag.moving)
        }
    }
}
