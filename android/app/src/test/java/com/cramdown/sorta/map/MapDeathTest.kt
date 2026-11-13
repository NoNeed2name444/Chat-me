package com.cramdown.sorta.map

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * S05h2: how a body leaves Space's map (MapDeath, iOS GraphDeath.swift and GraphDeathScene's stage).
 * The first five are iOS's GraphDeathTests D1-D5 as they are; the rest the stage, the departures and
 * the test clock. Made-up ids.
 */
class MapDeathTest {
    @After fun clocksOff() { MapDeath.testHold = null }

    private fun departure(id: String, code: Int, kind: DeathKind, radius: Float, vararg partners: String) =
        MapDeparture(id, 0, Vec3.ZERO, radius, radius, radius, kind, 0, code, 0f, partners.map { it to 0f })

    @Test fun d1EachKindDiesItsOwnWayAndRolesAndStylesMapToKinds() {
        val expected = mapOf(
            DeathKind.ROCKY to DeathEffect.TIDAL_DISRUPTION, DeathKind.MOON to DeathEffect.TIDAL_DISRUPTION,
            DeathKind.GAS_GIANT to DeathEffect.ATMOSPHERE_STRIPPED, DeathKind.SMALL_STAR to DeathEffect.PLANETARY_NEBULA,
            DeathKind.BIG_STAR to DeathEffect.SUPERNOVA, DeathKind.BLACK_HOLE to DeathEffect.EVAPORATION,
            DeathKind.PULSAR to DeathEffect.SPIN_DOWN, DeathKind.COMET to DeathEffect.COMET_BREAKUP,
            DeathKind.CELL to DeathEffect.APOPTOSIS, DeathKind.PLAIN to DeathEffect.FADE)
        for ((kind, effect) in expected) assertEquals(kind.name, effect, MapDeath.plan(kind, false).effect)
        assertEquals(DeathKind.BLACK_HOLE, MapDeath.kind("galaxy", 9))
        assertEquals(DeathKind.SMALL_STAR, MapDeath.kind("star", 2))
        assertEquals(DeathKind.BIG_STAR, MapDeath.kind("star", 30))
        assertEquals(DeathKind.BIG_STAR, MapDeath.kind("home", 0))
        assertEquals(DeathKind.GAS_GIANT, MapDeath.kind("gasGiant", 0))
        assertEquals(DeathKind.MOON, MapDeath.kind("moon", 0))
        assertEquals(DeathKind.PULSAR, MapDeath.kind("pulsar", 0))
        assertEquals(DeathKind.COMET, MapDeath.kind("oort", 0))
        assertEquals(DeathKind.ROCKY, MapDeath.kind("rocky", 0))
        assertEquals("by Android's role", DeathKind.BIG_STAR, MapDeath.kind(UniverseRole.STAR, 8))
        assertEquals(DeathKind.SMALL_STAR, MapDeath.kind("sun"))
        assertEquals(DeathKind.BLACK_HOLE, MapDeath.kind("blackHole"))
        assertEquals(DeathKind.COMET, MapDeath.kind("comet"))
        assertEquals(DeathKind.PLAIN, MapDeath.kind("unknown"))
        assertEquals("by Android's style code", DeathKind.SMALL_STAR, MapDeath.kind(SpaceStyle.SUN.code))
        assertEquals(DeathKind.ROCKY, MapDeath.kind(SpaceStyle.ROCKY.code))
    }

    @Test fun d2OneToTwoAndAHalfSecondsWholeAtTheStartGoneAtTheEndLinksFirst() {
        val bad = ArrayList<String>()
        for (kind in DeathKind.values()) for (smooth in listOf(false, true)) {
            val plan = MapDeath.plan(kind, smooth)
            val name = "$kind${if (smooth) "/smooth" else ""}"
            if (plan.effect != DeathEffect.FADE && (plan.duration < 1 || plan.duration > 2.5)) bad.add("$name lasts ${plan.duration}")
            for (p in plan.phases) if (p.start < 0 || p.end > plan.duration + 1e-9 || p.end <= p.start) bad.add("$name phase ${p.name}")
            val start = MapDeath.scale(plan, 0.0)
            if (abs(start.across - 1) > 0.01 || abs(start.along - 1) > 0.01 || MapDeath.opacity(plan, 0.0) < 0.99) bad.add("$name not whole")
            val end = MapDeath.scale(plan, plan.duration)
            if (!(end.across < 0.25 || MapDeath.opacity(plan, plan.duration) < 0.01)) bad.add("$name still there")
            if (MapDeath.linkReach(plan, 0.0) < 0.99 || MapDeath.linkReach(plan, plan.linksGone) > 0.001) bad.add("$name links")
            if (plan.linksGone > plan.duration + 1e-9) bad.add("$name links outlast the body")
            var t = 0.0; var last = 2f
            while (t <= plan.duration) {
                val reach = MapDeath.linkReach(plan, t)
                if (reach > last + 1e-6) bad.add("$name links grow back at $t")
                last = reach
                val s = MapDeath.scale(plan, t); val o = MapDeath.opacity(plan, t)
                if (!(s.along.isFinite() && s.across.isFinite()) || s.along < 0 || s.across < 0 || o < 0 || o > 1) { bad.add("$name at $t"); break }
                t += 0.02
            }
        }
        assertTrue(bad.take(5).toString(), bad.isEmpty())
    }

    @Test fun d3EachDeathAsPhysicsHasIt() {
        val hole = MapDeath.plan(DeathKind.BLACK_HOLE, false)
        val early = 1 - MapDeath.scale(hole, 0.4).across
        val late = MapDeath.scale(hole, 1.0).across - MapDeath.scale(hole, 1.6).across
        assertTrue("Hawking, ever faster: $early $late", late > early * 0.9)
        assertTrue("a small star swells first", MapDeath.scale(MapDeath.plan(DeathKind.SMALL_STAR, false), 0.6).across > 1.3f)
        assertEquals("a supernova's star is gone at the flash", 0f, MapDeath.scale(MapDeath.plan(DeathKind.BIG_STAR, false), 0.5).across, 0f)
        val pulled = MapDeath.scale(MapDeath.plan(DeathKind.ROCKY, false), 0.45)
        assertTrue("tides stretch a planet: $pulled", pulled.along > pulled.across * 1.5f)
        val pulsar = MapDeath.plan(DeathKind.PULSAR, false)
        assertTrue(MapDeath.spin(pulsar, 0.0) == 1f && MapDeath.spin(pulsar, 0.8) < 0.5f && MapDeath.spin(pulsar, 1.6) == 0f)
        val cell = MapDeath.plan(DeathKind.CELL, false)
        assertEquals(listOf("shrink", "bleb", "condense", "fragment", "bodies"), cell.phases.map { it.name })
        assertTrue(cell.phases.zipWithNext().all { (a, b) -> a.start <= b.start })
        val rounded = MapDeath.scale(cell, 0.6).across
        assertTrue("a cell rounds up smaller: $rounded", rounded < 0.8f && rounded > 0.7f)
    }

    @Test fun d4AFolderOfFortyDeletedGetsSixFullEffectsAtHighAndThreeAtSmooth() {
        val many = ArrayList<DeathInput>()
        for (k in 0 until 40) many.add(DeathInput(if (k % 5 == 0) DeathKind.SMALL_STAR else DeathKind.ROCKY, (k % 7) * 0.05f + 0.05f, k))
        many.add(DeathInput(DeathKind.PLAIN, 9f, 0))
        val high = MapDeath.plans(many, still = false, smooth = false)
        val smooth = MapDeath.plans(many, still = false, smooth = true)
        assertEquals(6, high.count { it.effect != DeathEffect.FADE })
        assertEquals(3, smooth.count { it.effect != DeathEffect.FADE })
        val biggest = many.filter { it.kind != DeathKind.PLAIN }.maxOf { it.radius }
        val chosen = many.indices.filter { high[it].effect != DeathEffect.FADE }.map { many[it].radius }
        assertTrue("the biggest get them", chosen.all { it >= biggest - 0.1f })
        assertEquals("a plain body never does", DeathEffect.FADE, high.last().effect)
        val sparksHigh = high.sumOf { it.particles }; val sparksSmooth = smooth.sumOf { it.particles }
        assertTrue("$sparksHigh $sparksSmooth", sparksHigh <= 6 * 110 && sparksSmooth < sparksHigh)
    }

    @Test fun d5StillGivesQuickFadesAndTheSameInputsTheSamePlans() {
        val many = (0 until 12).map { DeathInput(DeathKind.values()[it % 10], it * 0.1f, it) }
        val still = MapDeath.plans(many, still = true, smooth = false)
        assertTrue(still.all { it.effect == DeathEffect.FADE && it.duration <= 0.35 && it.particles == 0 })
        assertEquals(MapDeath.plans(many, false, false), MapDeath.plans(many, false, false))
    }

    @Test fun linkReachIsOneHalfAndNoneAtTheStartMiddleAndEndOfLinksGone() {
        for (kind in DeathKind.values()) {
            val plan = MapDeath.plan(kind, false)
            assertEquals(1f, MapDeath.linkReach(plan, 0.0), 1e-6f)
            assertEquals(0.5f, MapDeath.linkReach(plan, 0.5 * plan.linksGone), 1e-6f)
            assertEquals(0f, MapDeath.linkReach(plan, plan.linksGone), 1e-6f)
        }
    }

    @Test fun bothEndsDyingTakeTheSmallerReach() {
        // a rocky planet (links gone at 0.7 s) linked to a black hole (1.2 s), both deleted, and each to a live body
        val stage = MapDeathStage(listOf(departure("rock", 1, DeathKind.ROCKY, 0.3f, "hole", "live"), departure("hole", 0, DeathKind.BLACK_HOLE, 0.2f, "rock")),
            listOf(MapDeath.plan(DeathKind.ROCKY, false), MapDeath.plan(DeathKind.BLACK_HOLE, false)), lively = true)
        stage.resolve { if (it == "live") 7 else -1 }
        assertEquals("the pair once, and the live link", 2, stage.linkCount)
        stage.step(1.0 / 30); repeat(10) { stage.step(0.03) }
        val pair = (0 until stage.linkCount).first { stage.linkMate[it] >= 0 }
        val rock = MapDeath.linkReach(stage.plans[0], stage.elapsed); val hole = MapDeath.linkReach(stage.plans[1], stage.elapsed)
        assertTrue(rock < hole)
        assertEquals(minOf(rock, hole), stage.reach[pair], 1e-6f)
        val live = (0 until stage.linkCount).first { stage.linkOther[it] == 7 }
        assertEquals(rock, stage.reach[live], 1e-6f)
    }

    @Test fun stillTheLinksStayWholeUntilThePlansEndAndTheSetFades() {
        val plans = MapDeath.plans(listOf(DeathInput(DeathKind.ROCKY, 0.3f, 0)), still = true, smooth = false)
        val stage = MapDeathStage(listOf(departure("rock", 1, DeathKind.ROCKY, 0.3f, "live")), plans, lively = false)
        stage.resolve { if (it == "live") 3 else -1 }
        stage.step(1.0 / 30)
        var t = 1.0 / 30
        while (t < 0.29) {
            assertEquals("whole at $t", 1f, stage.reach[0], 0f)
            assertEquals("the set at 1 − t / longest", (1 - t / 0.3).toFloat(), stage.setOpacity, 1e-5f)
            stage.step(0.05); t += 0.05
        }
        while (stage.elapsed < 0.3) stage.step(0.05)
        assertEquals("gone at its end", 0f, stage.reach[0], 0f)
        assertEquals(0f, stage.setOpacity, 0f)
    }

    @Test fun theStageFinishesOnlyPastTheLongestPlusATenth() {
        val plans = listOf(MapDeath.plan(DeathKind.PULSAR, false), MapDeath.fade(0.8))
        val stage = MapDeathStage(listOf(departure("a", 4, DeathKind.PULSAR, 0.2f), departure("b", 1, DeathKind.PLAIN, 0.1f)), plans, lively = true)
        stage.resolve { -1 }
        assertEquals(1.9, stage.longest, 1e-9)
        stage.step(1.0 / 30)
        while (stage.elapsed < 1.95) { stage.step(0.05); assertTrue("not before ${stage.elapsed}", !stage.finished || stage.elapsed > 2.0) }
        stage.step(0.25)
        assertTrue("finished at ${stage.elapsed}", stage.finished)
        assertEquals(0, stage.aliveCount())
    }

    @Test fun departuresLeaveOutMoonLinksAndSmallPopsAndKeepWhatTheBodyWas() {
        val ids = listOf("planet", "moon", "star", "tiny", "kept")
        // planet-moon (kind 3), planet-star, planet-kept, tiny-kept
        val links = intArrayOf(0, 1, 0, 2, 0, 4, 3, 4)
        val kinds = intArrayOf(3, 0, 1, 0)
        val deps = MapDeath.departures(ids, setOf("kept"), links, kinds, seed = { a, b -> (a * 10 + b).toFloat() }) { i ->
            DepartureBody(Vec3(i.toFloat(), 0f, 0f), radius = 0.5f, shader = 0.4f, reach = 0.3f, pop = if (i == 3) 0.05f else 2f,
                kind = DeathKind.ROCKY, count = i, code = 1, look = 6f)
        }
        assertEquals("a pop of 0.05 does not die", listOf("planet", "moon", "star"), deps.map { it.id })
        val planet = deps[0]
        assertEquals("no moon link", listOf("star" to 2f, "kept" to 4f), planet.partners)
        assertEquals("its moon's link is not taken either way", emptyList<Pair<String, Float>>(), deps[1].partners)
        assertEquals(listOf("planet" to 2f), deps[2].partners)
        assertEquals(1f, deps[1].place.x, 0f)
        assertEquals("radius × pop", 1f, planet.radius, 1e-6f)
        assertEquals(0.8f, planet.shader, 1e-6f); assertEquals(0.6f, planet.reach, 1e-6f)
        assertEquals(DeathKind.ROCKY, planet.kind); assertEquals(1, deps[1].count); assertEquals(1, planet.code); assertEquals(6f, planet.look, 0f)
        assertEquals(2, deps[2].index)
    }

    @Test fun aFirstStepOfTwoSecondsStartsTheDeathAtAThirtiethAndTheTotalsCountItOnce() {
        val stage = MapDeathStage.make(listOf(departure("a", 5, DeathKind.SMALL_STAR, 0.4f, "x", "y")), lively = true, smooth = false)!!
        stage.resolve { if (it == "x") 1 else if (it == "y") 2 else -1 }
        var died = 0; var links = 0
        stage.step(2.0)
        assertEquals(1.0 / 30, stage.elapsed, 1e-12)
        stage.tally()?.let { died += it[0]; links += it[1] }
        repeat(200) { stage.step(0.25); stage.tally()?.let { died += it[0]; links += it[1] } }
        assertEquals("counted once", 1, died)
        assertEquals(2, links)
        assertTrue(stage.finished)
    }

    /**
     * Repair 2, CI's tablet with animations off: a new scene's first frame (its bodies not yet drawn)
     * and then a second 2.5 s later. The death is posed at its start and not counted until a frame of
     * the scene was on the screen; then it starts at 1/30 s, is counted once, and at 10 frames a
     * second no body is dying within 0.5 s of the count.
     */
    @Test fun aDeathBeginsAndIsCountedOnlyOnceItsSceneIsOnTheScreen() {
        val plans = MapDeath.plans(listOf(DeathInput(DeathKind.ROCKY, 0.3f, 0)), still = true, smooth = false)
        val stage = MapDeathStage(listOf(departure("rock", 1, DeathKind.ROCKY, 0.3f, "live")), plans, lively = false)
        stage.resolve { if (it == "live") 3 else -1 }
        var died = 0; var links = 0
        fun count(t: IntArray?) { t?.let { died += it[0]; links += it[1] } }
        count(stage.frame(1.0 / 60, shown = false))
        count(stage.frame(2.5, shown = false))
        assertEquals("not begun", 0.0, stage.elapsed, 0.0)
        assertTrue(!stage.started)
        assertEquals("not counted", 0, died)
        assertEquals("drawn at its start", 1, stage.aliveCount())
        assertEquals(1f, stage.size[0].across, 0f); assertEquals(1f, stage.opacity[0], 0f)
        assertEquals("its link whole", 1f, stage.reach[0], 0f)
        assertEquals(1f, stage.setOpacity, 0f)
        // the first frame after its scene was shown, however long, begins it
        count(stage.frame(1.4, shown = true))
        assertTrue(stage.started)
        assertEquals(1.0 / 30, stage.elapsed, 1e-12)
        assertEquals("counted", 1, died); assertEquals(1, links)
        var t = 0.0
        while (stage.aliveCount() > 0 && t < 2.0) { count(stage.frame(0.1, shown = true)); t += 0.1 }
        assertTrue("gone ${t}s after the count", t <= 0.5)
        repeat(3) { count(stage.frame(0.1, shown = true)) }
        assertTrue(stage.finished)
        assertEquals("counted once", 1, died); assertEquals(1, links)
        // begun, a frame not shown (a new GL context) still steps it
        val again = MapDeathStage(listOf(departure("b", 1, DeathKind.PLAIN, 0.1f)), listOf(MapDeath.fade(0.8)), lively = true)
        again.resolve { -1 }
        again.frame(0.1, shown = true); again.frame(0.1, shown = false)
        assertEquals(1.0 / 30 + 0.1, again.elapsed, 1e-12)
    }

    /**
     * Repair 7, CI's emulators at about 0.3-0.5 s a frame: a death let go from the test clock runs on the
     * map's own clock, each frame's step at most MapDeath.MAX_STEP (GlMapRenderer's dt), so on frames
     * slower than that it is not over by the wall's plan length + 0.5 s; it is over once its own clock has
     * run the plan's length past the hold, and never before.
     */
    @Test fun aDeathLetGoRunsOnTheMapsOwnClockEachStepAtMostAQuarter() {
        val stage = MapDeathStage.make(listOf(departure("a", 2, DeathKind.GAS_GIANT, 0.4f, "x")), lively = true, smooth = false)!!
        stage.resolve { if (it == "x") 1 else -1 }
        val plan = stage.plans[0]
        val hold = plan.linksGone * 0.5
        repeat(3) { stage.frame(0.4, shown = true, hold = hold) }
        assertEquals("held", hold, stage.elapsed, 0.0)
        assertEquals(1, stage.aliveCount())
        // let go: frames 0.4 s apart on the wall, each a quarter of a second on the map's clock
        var wall = 0.0
        while (wall < plan.duration - hold + 0.5 - 1e-9) {
            val before = stage.elapsed
            stage.frame(0.4, shown = true)
            wall += 0.4
            if (!stage.finished) assertEquals("a step of ${stage.elapsed - before} s at $wall s", MapDeath.MAX_STEP, stage.elapsed - before, 1e-12)
        }
        assertTrue("by the wall, the plan's length + 0.5 s on, it plays on: ${stage.elapsed} s of ${plan.duration} s", stage.aliveCount() == 1)
        // by its own clock: alive until the plan's length past the hold, gone at the first step past it
        while (stage.aliveCount() > 0) {
            assertTrue("alive at ${stage.elapsed} s of ${plan.duration} s", stage.elapsed < plan.duration)
            stage.frame(0.4, shown = true)
        }
        assertTrue("gone at ${stage.elapsed} s", stage.elapsed >= plan.duration && stage.elapsed - hold < plan.duration - hold + 0.5)
    }

    @Test fun theTestClockIsOffByDefault() {
        assertNull(MapDeath.testHold)
        assertNull(SpaceTrails.testFrozen)
        val stage = MapDeathStage.make(listOf(departure("a", 2, DeathKind.GAS_GIANT, 0.4f)), lively = true, smooth = false)!!
        stage.resolve { -1 }
        stage.step(0.01, MapDeath.testHold); stage.step(0.1, MapDeath.testHold); stage.step(0.2, MapDeath.testHold)
        assertEquals("the steps given", 0.31, stage.elapsed, 1e-12)
        // set, it holds the clock where it is told
        stage.step(0.2, 0.45)
        assertEquals(0.45, stage.elapsed, 0.0)
        stage.step(0.2, 0.45)
        assertEquals(0.45, stage.elapsed, 0.0)
    }

    @Test fun theTidalAxisIsIosTiltAndTheStretchIsDrawnLong() {
        val id = "0e6b1a2c-3d4f-4a5b-9c8d-7e6f5a4b3c2d"
        val seed = (GraphUniverse.fnv(id.uppercase()) % 1000uL).toFloat() / 1000f
        val a = seed * 6.2831853f
        val l = kotlin.math.sqrt(cos(a) * cos(a) + 0.35f * 0.35f + sin(a) * sin(a))
        val axis = MapDeath.tidalAxis(id)
        assertEquals(cos(a) / l, axis.x, 1e-6f); assertEquals(0.35f / l, axis.y, 1e-6f); assertEquals(sin(a) / l, axis.z, 1e-6f)
        assertEquals("the same, whatever the id's case", axis, MapDeath.tidalAxis(id.uppercase()))
        val s = MapDeath.scale(MapDeath.plan(DeathKind.MOON, false), 0.45)
        assertTrue("$s", s.along > 1.5f * s.across)
    }
}
