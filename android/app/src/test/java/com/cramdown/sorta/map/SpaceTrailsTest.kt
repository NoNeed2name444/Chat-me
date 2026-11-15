package com.cramdown.sorta.map

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * S05h2: the sparks moving bodies throw (SpaceTrails, iOS's trail emitters: GraphMotion.swift:2001-2068,
 * GraphStyleAnimator.swift:203-246, SpaceOptics.trail). Bodies are made-up indices.
 */
class SpaceTrailsTest {
    @After fun clocksOff() { SpaceTrails.testFrozen = null }

    private val sun = SpaceStyle.SUN.code
    private val comet = SpaceStyle.COMET.code

    /** Universe sparks over [n] bodies, every body a sun unless [styles] says. */
    private fun trails(n: Int = 10, lively: Boolean = true, smooth: Boolean = false) =
        SpaceTrails.of(lively, universe = true, nodeRadius = 0.2f, smooth = smooth, bodies = n, seed = 7L)

    private fun step(t: SpaceTrails, speeds: FloatArray, held: Int = -1, dt: Float = 1f / 60, styles: IntArray? = null,
                     places: Array<Vec3>? = null, frozen: Boolean = false) {
        val drawn = IntArray(speeds.size) { it }
        t.step(dt, drawn, drawn.size, held, { i -> Vec3(speeds[i], 0f, 0f) }, { i -> places?.get(i) ?: Vec3(i.toFloat(), 0f, 0f) },
            { i -> styles?.get(i) ?: sun }, frozen)
    }

    @Test fun fourSlotsTheHeldBodyFirstThenTheFastestAboveTrailSpeed() {
        val t = trails()
        // 0 held and still; 1..6 at speeds over 0.9, 7 just under
        val speeds = floatArrayOf(0f, 1.0f, 3.0f, 2.0f, 1.5f, 4.0f, 0.95f, 0.89f, 0f, 0f)
        step(t, speeds, held = 0)
        assertEquals(4, t.emitters)
        assertEquals(0, t.owner[0])
        assertEquals("then the three fastest", setOf(5, 2, 3), t.owner.drop(1).toSet())
        // no body held: the four fastest
        val u = trails()
        step(u, speeds)
        assertEquals(setOf(5, 2, 3, 4), u.owner.toSet())
        // nothing over trail speed, nothing held: no slot taken
        val v = trails()
        step(v, floatArrayOf(0.5f, 0.89f, 0f))
        assertTrue(v.owner.all { it == -1 })
    }

    @Test fun aBodyKeepsItsSlotWhileWantedAndAFreedSlotIsReused() {
        val t = trails()
        step(t, floatArrayOf(2f, 3f, 4f, 5f, 0f, 0f))
        val first = t.owner.copyOf()
        // body 4 now fastest, body 0 slowed: 0's slot goes to 4; the rest keep theirs
        step(t, floatArrayOf(0f, 3f, 4f, 5f, 6f, 0f))
        val slotOf0 = first.indexOf(0)
        assertEquals(4, t.owner[slotOf0])
        for (s in 0 until 4) if (s != slotOf0) assertEquals(first[s], t.owner[s])
    }

    @Test fun afterATauAtASteadySpeedTheLevelIsOneLessOneOverE() {
        val t = trails(n = 1)
        val steps = 150
        repeat(steps) { step(t, floatArrayOf(1.25f), dt = 0.15f / steps) }
        val goal = 1.25f / SpaceTrails.FULL_SPEED
        assertEquals((1 - exp(-1.0)).toFloat() * goal, t.level[0], 0.01f * goal)
    }

    @Test fun theRateIsTheStylesRateTimesLevelTimesOneOrPointFourAndABlackHoleThrowsNone() {
        for (smooth in listOf(false, true)) {
            val t = trails(n = 3, smooth = smooth)
            val styles = intArrayOf(sun, comet, SpaceStyle.BLACK_HOLE.code)
            repeat(30) { step(t, floatArrayOf(2f, 3f, 5f), styles = styles) }
            val scale = if (smooth) 0.4f else 1f
            for (s in 0 until 4) {
                val body = t.owner[s]
                if (body < 0) continue
                val rate = SpaceOptics.trail(styles[body]).rate
                assertEquals("slot $s, body $body", rate * t.level[body] * scale, t.rate[s], 1e-3f)
            }
            assertEquals(0f, t.rate[t.owner.indexOf(2)], 0f)
        }
        // a black hole alone throws nothing however fast
        val hole = trails(n = 1)
        repeat(120) { step(hole, floatArrayOf(9f), styles = intArrayOf(SpaceStyle.BLACK_HOLE.code)) }
        assertEquals(0L, hole.thrownTotal)
    }

    @Test fun theScaleIsOnePointTwoUnderTheUniverseAndRadiusOverPointOneTwoFourUnderOneStyle() {
        assertEquals(1.2f, trails().scale, 0f)
        assertEquals(2.5f, trails().fullSpeed, 0f); assertEquals(0.9f, trails().trailSpeed, 0f)
        val single = SpaceTrails.of(true, universe = false, nodeRadius = 0.186f, smooth = false, bodies = 1)
        assertEquals("iOS's pageRadius 0.3 gives 1.5", 1.5f, single.scale, 1e-5f)
        assertEquals(0.186f / 0.124f, single.scale, 1e-5f)
        assertEquals("speeds in note radii at iOS's fallback", 2.5f, single.fullSpeed, 1e-5f)
        assertEquals(0.9f, single.trailSpeed, 1e-5f)
        val crowded = SpaceTrails.of(true, universe = false, nodeRadius = 0.05f, smooth = false, bodies = 1)
        // repair 8: held to iOS's 1-3, as iOS holds pageRadius to 0.2-0.6 (GraphFraming.swift:42)
        assertEquals("a crowded map's sparks no smaller than iOS's smallest", 1f, crowded.scale, 0f)
        assertEquals(2.5f / (0.62f * 0.3f) * 0.05f, crowded.fullSpeed, 1e-5f)
    }

    @Test fun aSingleStylesScaleIsHeldToIosOneToThree() {
        // repair 8: iOS's pageRadius / 0.2 with pageRadius held to 0.2-0.6 (GraphFraming.swift:42, Graph3DView.swift:1341)
        for (r in listOf(0.001f, 0.01f, 0.08f, 0.124f, 0.2f, 0.3f, 0.372f, 0.45f, 2f)) {
            val k = SpaceTrails.singleScale(r)
            assertTrue("$r gives $k", k in 1f..3f)
            assertEquals("$r", (r / 0.124f).coerceIn(1f, 3f), k, 1e-5f)
        }
        assertEquals(1f, SpaceTrails.singleScale(0.124f * 0.5f), 0f)
        assertEquals(2f, SpaceTrails.singleScale(0.124f * 2f), 1e-5f)
        assertEquals(3f, SpaceTrails.singleScale(0.124f * 3.6f), 0f)
        assertEquals("the old span's top, 3.6, held to 3", 3f, SpaceTrails.of(true, universe = false, nodeRadius = 0.4464f, smooth = false, bodies = 1).scale, 0f)
    }

    @Test fun theDressSpreadsLifeSizeAndSpeedAsIos() {
        val t = trails(n = 1)
        val look = SpaceOptics.trail(sun)
        val lives = ArrayList<Float>(); val sizes = ArrayList<Float>(); val speeds = ArrayList<Float>()
        repeat(300) {
            val before = t.thrownTotal
            step(t, floatArrayOf(5f), dt = 1f / 60)
            if (t.thrownTotal > before) {
                val i = t.newest
                lives.add(t.bornLife(i)); sizes.add(t.bornSize(i))
            }
        }
        // a fresh spark's speed, before any slowing (repair 8: a birth is already its share of the step
        // old, so its speed now is its speed at birth slowed by that age)
        repeat(200) {
            val before = t.thrownTotal
            step(t, floatArrayOf(5f), dt = 1f / 60)
            if (t.thrownTotal > before) {
                val i = t.newest
                speeds.add(t.speedAtBirth(i))
                assertEquals(t.speedAtBirth(i) * exp(-1.5f * t.ageOf(i)), t.speedOf(i), 1e-4f)
            }
        }
        assertTrue("enough born: ${lives.size}", lives.size > 100 && speeds.size > 50)
        fun within(xs: List<Float>, base: Float, spread: Float) {
            assertTrue("${xs.min()} ${xs.max()} against $base ± $spread", xs.all { it >= base * (1 - spread) - 1e-5f && it <= base * (1 + spread) + 1e-5f })
            assertTrue("spread out: ${xs.min()} ${xs.max()}", xs.max() - xs.min() > base * spread)
        }
        within(lives, look.life, 0.35f)
        within(sizes, look.size * 1.2f, 0.5f)
        within(speeds, look.speed * 1.2f, 0.6f)
    }

    @Test fun theColourRampEndsBlackAndTheSizeGoesOneHalfNothing() {
        val out = FloatArray(3)
        val colours = SpaceOptics.trail(sun).colours
        SpaceTrails.ramp(colours, 0f, out); assertArrayEquals(colours[0], out, 1e-6f)
        SpaceTrails.ramp(colours, 0.5f, out); assertArrayEquals(colours[1], out, 1e-6f)
        SpaceTrails.ramp(colours, 1f, out); assertArrayEquals(floatArrayOf(0f, 0f, 0f), out, 1e-6f)
        SpaceTrails.ramp(SpaceOptics.trail(SpaceStyle.BLACK_HOLE.code).colours, 1f, out); assertArrayEquals(floatArrayOf(0f, 0f, 0f), out, 1e-6f)
        assertEquals(1f, SpaceTrails.shrink(0f), 1e-6f)
        assertEquals(0.55f, SpaceTrails.shrink(0.5f), 1e-6f)
        assertEquals(0f, SpaceTrails.shrink(1f), 1e-6f)
    }

    @Test fun backwardStylesAimAgainstTheVelocityAndTheOthersAboutUp() {
        val t = trails(n = 2)
        repeat(5) { step(t, floatArrayOf(3f, 3f), styles = intArrayOf(comet, sun)) }
        val cometSlot = t.owner.indexOf(0); val sunSlot = t.owner.indexOf(1)
        val back = t.directionOf(cometSlot); val up = t.directionOf(sunSlot)
        assertArrayEquals(floatArrayOf(-1f, 0f, 0f), floatArrayOf(back.x, back.y, back.z), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), floatArrayOf(up.x, up.y, up.z), 1e-6f)
        // a comet's sparks fly back within its 60 degrees
        repeat(60) { step(t, floatArrayOf(3f, 0f), styles = intArrayOf(comet, sun)) }
        val i = t.newest
        val v = t.velocityOf(i).normalized()
        assertTrue("$v", v.x <= -0.5f + 1e-4f)
    }

    @Test fun aSparkKeepsItsPlaceWhenItsBodyMovesOnAndSlowsByTheDamping() {
        val t = trails(n = 1)
        val at = arrayOf(Vec3(0f, 0f, 0f))
        while (t.thrownTotal == 0L) step(t, floatArrayOf(5f), places = at)
        val i = t.newest
        val born = t.placeOf(i); val v0 = t.velocityOf(i)
        // the body leaps away; the spark goes on from where it was born, slowing
        at[0] = Vec3(50f, 0f, 0f)
        val h = 0.01f
        step(t, floatArrayOf(5f), dt = h, places = at)
        val slow = exp(-1.5f * h)
        val v1 = t.velocityOf(i)
        assertEquals(v0.x * slow, v1.x, 1e-5f); assertEquals(v0.y * slow, v1.y, 1e-5f)
        assertEquals(born.x + v1.x * h, t.placeOf(i).x, 1e-5f)
        assertEquals(born.y + v1.y * h, t.placeOf(i).y, 1e-5f)
    }

    @Test fun birthsOverOneLongStepLieAlongTheBodysWayWithAgesSpreadOverTheStep() {
        // repair 8: at a low frame rate a step's births are spread along the segment from the body's last
        // place to its place now, each pre-aged by its share of the step, as a continuous emitter's
        val t = trails(n = 1)
        val at = arrayOf(Vec3(0f, 0f, 0f))
        repeat(60) { step(t, floatArrayOf(5f), places = at) } // its level up, at rest in place
        val from = Vec3(0f, 0f, 0f); val to = Vec3(4f, 0f, 0f)
        at[0] = to
        val dt = 0.25f
        val before = t.thrownTotal
        val owed = t.rate[t.owner.indexOf(0)] * dt
        step(t, floatArrayOf(5f), dt = dt, places = at)
        val born = (t.thrownTotal - before).toInt()
        assertTrue("several born over the step: $born (rate × dt $owed)", born >= 3 && born >= kotlin.math.floor(owed).toInt())
        val ages = ArrayList<Float>(); val alongs = ArrayList<Float>()
        for (b in 0 until born) {
            val i = (t.newest - b + SpaceTrails.CAPACITY) % SpaceTrails.CAPACITY
            val a = t.ageOf(i)
            assertTrue("age $a in [0, $dt]", a >= 0f && a <= dt + 1e-6f)
            // back where it was born: its place less its own motion since (one slowed step of age a)
            val p = t.placeOf(i) - t.velocityOf(i) * a
            val f = 1f - a / dt
            assertEquals("born on the segment at its share of the step", from.x + (to.x - from.x) * f, p.x, 1e-4f)
            assertEquals(0f, p.y - (from.y + (to.y - from.y) * f), 1e-4f)
            assertEquals(0f, p.z - (from.z + (to.z - from.z) * f), 1e-4f)
            ages.add(a); alongs.add(p.x)
        }
        // newest first: the youngest nearest the body now, the oldest nearest where it was
        for (k in 1 until ages.size) assertTrue("older further back: $ages", ages[k] > ages[k - 1])
        assertTrue("spread over the step: $ages", ages.max() - ages.min() >= dt * (born - 1) / (born + 1) - 1e-5f)
        assertTrue("spread along the way: $alongs", alongs.max() - alongs.min() > (to.x - from.x) * 0.5f)
        // the per-second rate is kept: births this step are the owed whole ones
        assertTrue(born <= kotlin.math.ceil(owed + 1).toInt())
    }

    @Test fun nothingIsBornWithAnimationsOff() {
        val t = trails(lively = false)
        assertEquals(0, t.emitters)
        repeat(100) { step(t, FloatArray(10) { 5f }, held = 0) }
        assertEquals(0L, t.thrownTotal)
        assertEquals(0, t.write(FloatArray(SpaceTrails.CAPACITY * SpaceTrails.OUT_STRIDE)))
    }

    @Test fun theTestClockIsOffByDefaultAndSetNothingIsBornAgesOrMoves() {
        assertNull(SpaceTrails.testFrozen)
        val t = trails(n = 1)
        repeat(30) { step(t, floatArrayOf(5f), frozen = SpaceTrails.testFrozen == true) }
        assertTrue("unset, it throws", t.thrownTotal > 0)
        SpaceTrails.testFrozen = true
        val thrown = t.thrownTotal
        val out = FloatArray(SpaceTrails.CAPACITY * SpaceTrails.OUT_STRIDE)
        val n = t.write(out)
        val before = out.copyOf(n * SpaceTrails.OUT_STRIDE)
        repeat(30) { step(t, floatArrayOf(5f), frozen = SpaceTrails.testFrozen == true) }
        assertEquals(thrown, t.thrownTotal)
        assertEquals(n, t.write(out))
        assertArrayEquals(before, out.copyOf(n * SpaceTrails.OUT_STRIDE), 0f)
    }
}
