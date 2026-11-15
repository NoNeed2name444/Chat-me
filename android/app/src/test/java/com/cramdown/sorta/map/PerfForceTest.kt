package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The force refinement a slice a frame (S05i2), iOS's P9 (GraphPerfTests.swift:593-670) on the
// 100,000-note vault. Its time bounds are iOS's optimised ones widened ×25, as iOS's own debug build
// widens them (:47-52): a JVM unit test is not a phone. The frame time is printed for the result.
class PerfForceTest {
    private val g = PerfVault.graph
    private val l = PerfVault.seed
    private val n = g.noteCount

    private fun dist(f: PerfForce, a: Int, b: Int) = PerfVault.length(f.xyzr[4 * a] - f.xyzr[4 * b], f.xyzr[4 * a + 1] - f.xyzr[4 * b + 1], f.xyzr[4 * a + 2] - f.xyzr[4 * b + 2])

    /** The mean length of links inside one system. */
    private fun inside(f: PerfForce): Double {
        var sum = 0.0; var count = 0
        for (k in 0 until g.linkCount) {
            val a = g.edgeA[k]; val b = g.edgeB[k]
            if (g.systemOfNote[a] == g.systemOfNote[b]) { sum += dist(f, a, b); count++ }
        }
        return sum / maxOf(count, 1)
    }

    @Test fun aStepOfFiveThousandIsWithinAFrameAndItSettles() {
        val force = PerfForce(g, l)
        val seedInside = inside(force)
        val budget = 5_000
        val times = ArrayList<Double>(); var visited = 0; var steps = 0
        while (!force.isSettled && steps < 1_000) {
            val t0 = System.nanoTime()
            visited += force.step(budget)
            times.add((System.nanoTime() - t0) / 1e6)
            steps++
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val worst = sorted.last()
        val p95 = sorted[minOf(sorted.size - 1, (sorted.size * 0.95).toInt())]
        val settledInside = inside(force)
        println("PerfForceTest frame time: $steps steps of $budget notes over ${force.sweep} sweeps; a step " +
            "%.2f ms median, %.2f ms p95, %.2f ms worst; links inside a folder %.2f -> %.2f units".format(median, p95, worst, seedInside, settledInside))
        assertTrue("median $median ms, worst $worst ms", median < 8.0 * 25 && worst < 25.0 * 25)
        assertTrue(force.isSettled)
        assertEquals(force.sweep * n, visited)
        assertEquals(0, force.step(budget))
        assertTrue("$seedInside -> $settledInside", settledInside < seedInside * 0.9)
        // every note still inside its folder's ball and clear of its hub
        var outside = 0; var inHub = 0
        for (i in 0 until n) {
            val s = g.systemOfNote[i]
            val d = PerfVault.length(force.xyzr[4 * i] - l.systemCentre[3 * s], force.xyzr[4 * i + 1] - l.systemCentre[3 * s + 1], force.xyzr[4 * i + 2] - l.systemCentre[3 * s + 2])
            if (d + l.radius[i] > l.coreRadius[s] + 1e-3f) outside++
            if (s < g.folderCount && d < l.radius[n + s] + l.radius[i] - 1e-3f) inHub++
        }
        assertEquals(0, outside); assertEquals(0, inHub)
        for (f in 0 until g.folderCount) for (a in 0 until 3) assertEquals("hubs never move", l.positions[3 * (n + f) + a], force.xyzr[4 * (n + f) + a], 0f)
        val xyz = FloatArray(3 * n) { force.xyzr[4 * (it / 3) + it % 3] }
        assertTrue(xyz.all { it.isFinite() })
        val least = PerfVault.nearestSpacing(xyz, n, 2 * l.spacing)
        assertTrue("notes stay apart: $least", least >= 0.45f * l.spacing)
        // it keeps to the box the renderer quantises against
        val box = l.bounds()
        for (i in 0 until n) for (a in 0 until 3) assertTrue(force.xyzr[4 * i + a] >= box[a] - 1e-3f && force.xyzr[4 * i + a] <= box[3 + a] + 1e-3f)
    }

    @Test fun theSameHoweverTheSweepsAreSliced() {
        val g1 = PerfVault.smallGraph; val l1 = PerfVault.smallSeed
        val a = PerfForce(g1, l1); val b = PerfForce(g1, l1); val whole = PerfForce(g1, l1)
        while (!a.isSettled) a.step(997)
        while (!b.isSettled) b.step(3_331)
        whole.settle()
        assertArrayEquals(a.xyzr, b.xyzr, 0f)
        assertArrayEquals(a.xyzr, whole.xyzr, 0f)
        assertEquals(a.sweep, b.sweep)
    }

    @Test fun aStepStopsAtTheEndOfASweep() {
        val f = PerfForce(PerfVault.smallGraph, PerfVault.smallSeed)
        assertEquals(3_000, f.step(3_000))
        assertEquals(PerfVault.smallGraph.noteCount - 3_000, f.step(3_000))
        assertEquals(1, f.sweep); assertEquals(0, f.cursor)
    }

    @Test fun anEmptyMapIsSettledAtOnce() {
        val empty = PerfGraph(PerfInput(emptyList(), BooleanArray(0), IntArray(0), emptyList(), IntArray(0), IntArray(0)))
        val f = PerfForce(empty, PerfLayout.seed(empty)!!)
        assertTrue(f.isSettled); assertEquals(0, f.step(100))
    }

    @Test fun theSliceIsSizedToThreeMillisecondsAFrame() {
        assertEquals(4_000, PerfForce.FIRST_BUDGET)
        // 4,000 notes in 6 ms: halfway to the 2,000 that fit 3 ms
        assertEquals(3_000, PerfForce.nextBudget(4_000, 0.006))
        assertEquals(6_000, PerfForce.nextBudget(4_000, 0.0015))
        assertEquals(4_000, PerfForce.nextBudget(4_000, 0.003))
        assertEquals("never under 500", 500, PerfForce.nextBudget(500, 1.0))
        assertEquals("never over 60,000, however quick", 60_000, PerfForce.nextBudget(60_000, 0.0))
        // a slice timed at nothing counts as 50 µs
        assertEquals(PerfForce.nextBudget(1_000, 0.000_05), PerfForce.nextBudget(1_000, 0.0))
    }

    @Test fun positionsAreXyzAndRadiusPerBody() {
        val f = PerfForce(g, l)
        assertEquals(4 * g.nodeCount, f.xyzr.size)
        assertEquals(l.radius[0], f.xyzr[3], 0f)
        assertEquals(l.radius[n], f.xyzr[4 * n + 3], 0f)
        assertEquals(n, f.order.toSet().size)
    }
}
