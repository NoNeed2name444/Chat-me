package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frame tests' sums (FrameSummary, MapFrameProbe; perf, 15 Nov). */
class FrameSummaryTest {
    @Test fun nearestRankPercentiles() {
        val ms = DoubleArray(100) { (100 - it).toDouble() } // 100 down to 1
        val s = FrameSummary.of(ms)
        assertEquals(100, s.frames)
        assertEquals(50.0, s.p50, 0.0)
        assertEquals(95.0, s.p95, 0.0)
        assertEquals(99.0, s.p99, 0.0)
        // 9 to 100 are over a 120 Hz refresh (8.3 ms): 92 of 100; 17 to 100 over a 60 Hz one: 84
        assertEquals(0.92, s.over8, 1e-9)
        assertEquals(0.84, s.over16, 1e-9)
        // the frames given are left as they were
        assertEquals(100.0, ms[0], 0.0)
    }

    @Test fun fewAndNone() {
        assertEquals(FrameSummary.NONE, FrameSummary.of(DoubleArray(0)))
        val one = FrameSummary.of(doubleArrayOf(8.0))
        assertEquals(8.0, one.p50, 0.0); assertEquals(8.0, one.p99, 0.0); assertEquals(0.0, one.over8, 0.0)
        assertEquals("map frames=1 p50=8.0 p95=8.0 p99=8.0 over8.3=0.0% over16.7=0.0%", one.line("map"))
        // a frame of exactly a refresh is on time; a hair over is not
        val edge = FrameSummary.of(doubleArrayOf(FrameSummary.REFRESH_120_MS, FrameSummary.REFRESH_60_MS, 16.7))
        assertEquals(2.0 / 3, edge.over8, 1e-9)
        assertEquals(1.0 / 3, edge.over16, 1e-9)
    }

    @Test fun theProbeRecordsOnlyWhileOn() {
        MapFrameProbe.add(16.7, 4.0)
        MapFrameProbe.start()
        assertTrue(MapFrameProbe.on)
        MapFrameProbe.add(16.7, 4.0); MapFrameProbe.add(33.3, 9.0)
        val (interval, work) = MapFrameProbe.stop()
        assertFalse(MapFrameProbe.on)
        MapFrameProbe.add(50.0, 20.0)
        assertEquals(listOf(16.7, 33.3), interval.toList())
        assertEquals(listOf(4.0, 9.0), work.toList())
    }
}
