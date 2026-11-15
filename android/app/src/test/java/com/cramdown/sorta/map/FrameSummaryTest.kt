package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frame tests' sums (FrameSummary, MapFrameProbe; perf, 15 Nov). */
class FrameSummaryTest {
    @Test fun nearestRankPercentiles() {
        val ms = DoubleArray(100) { (100 - it).toDouble() } // 100 down to 1
        val s = FrameSummary.of(ms, over = 16.7)
        assertEquals(100, s.frames)
        assertEquals(50.0, s.p50, 0.0)
        assertEquals(95.0, s.p95, 0.0)
        assertEquals(99.0, s.p99, 0.0)
        // 17 to 100 are over 16.7 ms: 84 of 100
        assertEquals(0.84, s.late, 1e-9)
        // the frames given are left as they were
        assertEquals(100.0, ms[0], 0.0)
    }

    @Test fun fewAndNone() {
        assertEquals(FrameSummary.NONE, FrameSummary.of(DoubleArray(0), over = 16.7))
        val one = FrameSummary.of(doubleArrayOf(8.0), over = 16.7)
        assertEquals(8.0, one.p50, 0.0); assertEquals(8.0, one.p99, 0.0); assertEquals(0.0, one.late, 0.0)
        assertEquals("map frames=1 p50=8.0 p95=8.0 p99=8.0 late=0.0%", one.line("map"))
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
