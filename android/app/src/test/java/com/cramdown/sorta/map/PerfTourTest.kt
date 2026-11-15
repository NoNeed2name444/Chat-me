package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// The proof tour (S05i2), iOS's T1-T4 (GraphPerfTourTests.swift:1-276) at Android's two levels, each once
// (Performance has no depth fade): the interface, the phases in order with their lengths (68.2 s), what
// each asks of the map, and a path that is continuous and eased with one cut, the same for the same time.
class PerfTourTest {
    private val names = listOf("warm", "measure", "front", "turn", "back", "near", "chosen", "far", "compare high", "compare smooth")

    /** A million-sized map framed as iOS's test frames it: from the usual angle, inside the glass. */
    private fun makeMap(width: Float = 393f, height: Float = 852f, hub: Int? = 4242): PerfTourMap {
        val centre = Vec3(25.5f, -7.4f, 24.7f)
        val radius = 687.6f
        val fov = (PI / 3).toFloat()
        val from = OrbitCamera(Vec3.ZERO, 10f, 0.55f, 0.32f, fov)
        val fit = from.framing(centre, radius, width, height, MapInsets(top = 110f, bottom = 90f), 0.9f)
        val whole = MapPose(fit.target, fit.distance, 0.55f, 0.32f)
        val cam = OrbitCamera(whole.target, whole.distance, whole.yaw, whole.pitch, fov)
        val back = Vec3(cos(whole.pitch) * sin(whole.yaw), sin(whole.pitch), cos(whole.pitch) * cos(whole.yaw))
        val at = if (hub == null) null else centre + back * (radius * 0.7f) + cam.right() * 40f
        return PerfTourMap(centre, radius, width, height, whole, fov, hub, at)
    }

    private val phone = makeMap()
    private val tablet = makeMap(width = 1194f, height = 834f)
    private val tour = PerfTour(phone)
    private fun pose(t: Double) = tour.step(t)!!.pose

    private fun sampled(t: PerfTour, dt: Double): List<Pair<Double, PerfTourStep>> {
        val out = ArrayList<Pair<Double, PerfTourStep>>()
        var k = 0
        while (true) { val s = t.step(k * dt) ?: break; out.add(k * dt to s); k++ }
        return out
    }

    @Test fun t1AStepCarriesAPosePhaseLevelChoiceAndWhetherItCounts() {
        val plain = PerfTourStep(phone.whole, "front")
        assertTrue(plain.tier == null && plain.select == null && !plain.measuring)
        val full = PerfTourStep(phone.whole, "compare smooth", GraphicsTier.SMOOTH, 7, true)
        assertTrue(full.tier == GraphicsTier.SMOOTH && full.select == 7 && full.measuring && full != plain)
        val fresh = PerfTour(phone)
        assertEquals(phone.whole, fresh.step(0.0)!!.pose)
        assertEquals("warm", fresh.step(0.0)!!.phase)
        assertTrue("nothing to say before it ends", fresh.finish().isEmpty())
    }

    @Test fun t2ThePhasesInOrderAndHowLongEachLasts() {
        val fine = sampled(tour, 0.001)
        val order = ArrayList<String>(); val first = HashMap<String, Double>(); val last = HashMap<String, Double>()
        for ((t, s) in fine) {
            if (order.lastOrNull() != s.phase) order.add(s.phase)
            first.putIfAbsent(s.phase, t); last[s.phase] = t
        }
        assertEquals(names, order)
        val want = mapOf("warm" to 3.0, "measure" to 11.0, "front" to 8.0, "turn" to 1.2, "back" to 8.0, "near" to 13.0, "chosen" to 8.0, "far" to 8.0)
        for (name in names) assertEquals(name, want[name] ?: 4.0, last[name]!! - first[name]!! + 0.001, 0.0011)
        assertEquals(68.2, PerfTour.DURATION, 1e-9)
        assertEquals(names, PerfTour.SCHEDULE.map { it.name })
        assertNull(tour.step(PerfTour.DURATION)); assertNotNull(tour.step(PerfTour.DURATION - 0.0005))
        assertNull(tour.step(1e9)); assertNull(tour.step(Double.POSITIVE_INFINITY))
        assertEquals(tour.step(0.0), tour.step(-5.0)); assertEquals(tour.step(0.0), tour.step(Double.NaN))
    }

    @Test fun t3WhatEachPhaseAsks() {
        val fine = sampled(tour, 0.001)
        val measured = HashMap<String, Double>(); val tiers = HashMap<String, MutableSet<String>>(); val chosen = HashMap<String, MutableSet<String>>()
        for ((_, s) in fine) {
            if (s.measuring) measured[s.phase] = (measured[s.phase] ?: 0.0) + 0.001
            tiers.getOrPut(s.phase) { HashSet() }.add(s.tier?.name ?: "user's")
            chosen.getOrPut(s.phase) { HashSet() }.add(s.select?.toString() ?: "none")
        }
        val want = mapOf("warm" to 0.0, "measure" to 10.0, "front" to 8.0, "turn" to 1.2, "back" to 8.0, "near" to 10.0, "chosen" to 8.0, "far" to 8.0)
        for (name in names) assertEquals(name, want[name] ?: 3.0, measured[name] ?: 0.0, 0.0015)
        for (name in names.take(8)) assertEquals(setOf("user's"), tiers[name])
        assertEquals(listOf(setOf("HIGH"), setOf("SMOOTH")), names.drop(8).map { tiers[it] })
        assertEquals(setOf("4242"), chosen["chosen"])
        assertTrue(names.filter { it != "chosen" }.all { chosen[it] == setOf("none") })
        val inMeasure = fine.filter { it.second.phase == "measure" && it.second.measuring }.map { it.first }
        assertEquals(3.0, inMeasure.first(), 1e-9); assertEquals(12.999, inMeasure.last(), 1e-9)
        val noHub = sampled(PerfTour(makeMap(hub = null)), 0.01)
        assertTrue(noHub.all { it.second.select == null })
        assertEquals(makeMap(hub = null).centre, noHub.last { it.second.phase == "chosen" }.second.pose.target)
    }

    @Test fun t4OneJumpEasedTheHalfTurnFastest() {
        for ((label, map) in listOf("a phone" to phone, "a tablet on its side" to tablet)) {
            val dt = 1.0 / 600
            val steps = sampled(PerfTour(map), dt)
            val jumps = ArrayList<Pair<String, Double>>()
            var speed = DoubleArray(3); val accel = DoubleArray(3)
            var lastV: DoubleArray? = null
            for (k in 1 until steps.size) {
                val a = steps[k - 1].second.pose; val b = steps[k].second.pose
                val turn = abs(PerfTour.wrap(b.yaw - a.yaw)).toDouble()
                val zoom = abs(ln(b.distance) - ln(a.distance)).toDouble()
                val slide = ((b.target - a.target).length() / map.radius).toDouble()
                val tip = abs(b.pitch - a.pitch).toDouble()
                if (turn > 0.02 || zoom > 0.02 || slide > 0.005 || tip > 1e-4) {
                    jumps.add(steps[k - 1].second.phase + " to " + steps[k].second.phase to steps[k].first); lastV = null; continue
                }
                val v = doubleArrayOf(turn / dt, zoom / dt, slide / dt)
                speed = DoubleArray(3) { max(speed[it], v[it]) }
                lastV?.let { lv -> for (i in 0 until 3) accel[i] = max(accel[i], abs(v[i] - lv[i]) / dt) }
                lastV = v
            }
            assertEquals("$label: $jumps", 1, jumps.size)
            assertEquals(label, "chosen to far", jumps[0].first)
            assertEquals(label, 52.2, jumps[0].second, 0.002)
            assertTrue("$label: ${accel.toList()}", accel[0] < 30 && accel[1] < 30 && accel[2] < 10)
            assertTrue("$label: ${speed.toList()}", speed[0] > 3.8 && speed[0] < 4.0)
        }
    }

    private fun sameView(a: MapPose, b: MapPose, tolerance: Float = 1e-4f): Boolean =
        abs(PerfTour.wrap(a.yaw - b.yaw)) < tolerance && abs(a.pitch - b.pitch) < tolerance &&
            abs(a.distance / b.distance - 1) < tolerance && (a.target - b.target).length() < tolerance * max(a.distance, 1f)

    @Test fun t4TheMeasureTurnsOnceSteadilyAndZoomsInTwice() {
        val whole = phone.whole
        assertTrue(sameView(pose(14.0), whole) && sameView(pose(21.99), whole))
        var t = 3.0
        while (t < 13.0) {
            val rate = PerfTour.wrap(pose(t + 0.01).yaw - pose(t).yaw) / 0.01
            assertEquals("at $t", PerfTour.OPENING_SPEED, rate.toDouble(), 0.002)
            t += 0.25
        }
        val zoomed = (0..1000).map { pose(3.0 + it * 0.01).distance }
        val dips = (1 until zoomed.size - 1).count { zoomed[it] < zoomed[it - 1] && zoomed[it] < zoomed[it + 1] }
        assertEquals(2, dips)
        assertEquals(1f, zoomed.min() / phone.radius, 0.01f)
        assertEquals(1f, zoomed.last() / whole.distance, 1e-4f)
    }

    @Test fun t4TheTurnTheStillsTheFlightInAndTheCut() {
        assertEquals(PI.toFloat(), abs(PerfTour.wrap(pose(23.2).yaw - pose(22.0).yaw)), 1e-3f)
        assertEquals(pose(23.2), pose(31.19))
        for ((from, to) in listOf(14.0 to 22.0, 23.2 to 31.2, 34.2 to 44.2, 44.2 to 52.2, 52.2 to 60.2)) {
            var t = from + 0.001
            while (t < to - 0.001) { assertEquals("still at $t", pose(from + 0.001), pose(t)); t += 0.1 }
        }
        val flown = pose(44.2)
        val hub = phone.hubPosition!!
        assertEquals(hub, flown.target)
        assertTrue(abs(PerfTour.wrap(flown.yaw - phone.whole.yaw)) < 1e-3f)
        assertTrue("${flown.distance}", flown.distance < phone.radius * 0.05f && flown.distance > phone.radius * 0.01f)
        // the near ball is 0.0075 of the map's radius: the flight frames it filling 0.9 of the view
        val ball = phone.radius * PerfTour.NEAR_SHARE
        val f = OrbitCamera(fovY = phone.fovY).focalPixels(phone.height)
        val half = minOf(phone.width, phone.height) / 2 * 0.9f
        assertEquals(ball * sqrt(1 + (f / half) * (f / half)), flown.distance, 1e-3f)
        var t = 31.2
        while (t < 44.2) {
            val p = pose(t)
            val at = OrbitCamera(p.target, p.distance, p.yaw, p.pitch, phone.fovY).project(hub, phone.width, phone.height)
            assertNotNull("hub behind the eye at $t", at)
            val off = max(abs(at!!.first - phone.width / 2) / (phone.width / 2), abs(at.second - phone.height / 2) / (phone.height / 2))
            assertTrue("hub off screen at $t: $off", off <= 0.9f)
            t += 0.005
        }
        assertEquals(phone.whole, pose(52.21)); assertEquals(phone.whole, pose(60.19))
    }

    @Test fun t4TheTwoLevelsTurnOverTheSameArcOneEachWay() {
        fun turned(name: String, at: Double): Float {
            val k = PerfTour.SCHEDULE.indexOfFirst { it.name == name }
            return PerfTour.wrap(pose(PerfTour.STARTS[k] + at).yaw - phone.whole.yaw)
        }
        var x = 0.0
        while (x <= 2.99) {
            assertEquals(turned("compare high", 1.005 + x), turned("compare smooth", 3.995 - x), 1e-4f)
            x += 0.01
        }
        assertEquals((PI / 4).toFloat(), turned("compare high", 3.999), 1e-3f)
        assertEquals(0f, turned("compare high", 1.0), 1e-6f)
    }

    @Test fun t4TheSameTimeGivesTheSameStepWhateverWasRecorded() {
        val other = PerfTour(phone)
        var seed = 0x9E37_79B9_7F4A_7C15uL
        val times = List(2_000) {
            seed = seed * 6_364_136_223_846_793_005uL + 1_442_695_040_888_963_407uL
            (seed shr 11).toDouble() / (1L shl 53).toDouble() * (PerfTour.DURATION + 2) - 1
        }
        val report = PerfFrameReport(1.0 / 60, 0.002)
        for (t in times.take(500)) other.record(report, other.step(t) ?: PerfTourStep(phone.whole, "front"))
        assertTrue(times.all { tour.step(it) == tour.step(it) && tour.step(it) == other.step(it) })
    }

    @Test fun eachPhasesLineComesAsItEndsAndDoneAtTheEnd() {
        val run = PerfTour(phone)
        val lines = ArrayList<String>()
        var k = 0
        while (true) {
            val s = run.step(k / 60.0) ?: break
            lines += run.record(PerfFrameReport(1.0 / 60, 0.004), s)
            k++
        }
        lines += run.finish()
        assertEquals(names + "done", lines.map { it.substringBefore(':') })
        assertTrue(lines[0], lines[0].endsWith("not measured"))
        assertTrue(lines[1], lines[1].startsWith("measure: 60.0 fps (p5 60.0, 1% low 60.0) · work 4.0 (p95 4.0) ms · 600 of 660 frames"))
        assertTrue(lines[5], lines[5].startsWith("near: hub 4242 · "))
        assertTrue(run.finish().isEmpty())
    }
    @Test fun theHubIsTheMostLinkedBodyInTheMiddleInFront() {
        // looking down −z from z = 10 at the origin (yaw 0, pitch 0), a 600 x 800 view
        val cam = OrbitCamera(Vec3.ZERO, 10f, 0f, 0f, (PI / 3).toFloat())
        val at = listOf(
            Vec3(0f, 0f, 0f),     // 0: the middle, at the target: not nearer than it
            Vec3(0.5f, 0f, 2f),   // 1: in the middle, in front, 3 links
            Vec3(-0.5f, 0.5f, 3f),// 2: in the middle, in front, 5 links
            Vec3(8f, 0f, 3f),     // 3: off to the side, 9 links
            Vec3(0f, 0f, 12f),    // 4: behind the eye, 9 links
            Vec3(0.2f, 0f, 5f),   // 5: in the middle, in front, 5 links: a tie, the first kept
        )
        val degree = intArrayOf(9, 3, 5, 9, 9, 5)
        assertEquals(2, PerfTour.frontHub(at.size, { at[it] }, { degree[it] }, cam, 600f, 800f))
        assertNull(PerfTour.frontHub(3, { at[listOf(0, 3, 4)[it]] }, { 9 }, cam, 600f, 800f))
        assertNull(PerfTour.frontHub(0, { Vec3.ZERO }, { 0 }, cam, 600f, 800f))
    }
}
