package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05e: the board's curved link is one quadratic, as iOS IdeaBoardView.line draws it (move to p,
 * addQuadCurve to q through IdeaLinkShape.control), and the map's straight sampling into float
 * arrays matches the sampling into points. Made-up card centres.
 */
class IdeaBoardCurveTest {
    private val p = BoardPoint(0.0, 0.0)
    private val q = BoardPoint(100.0, 40.0)

    @Test fun aCurveRunsCentreToCentreThroughIosControlPoint() {
        val curve = IdeaLinkShape.curve(p, q)
        assertEquals(p, curve.start)
        assertEquals(q, curve.end)
        // iOS: the middle, moved 0.18 of the run to the left of p-to-q
        assertEquals(50.0 - 40.0 * 0.18, curve.control.x, 1e-9)
        assertEquals(20.0 + 100.0 * 0.18, curve.control.y, 1e-9)
    }

    @Test fun lanesBetweenOnePairFanOut() {
        val near = IdeaLinkShape.curve(p, q, lane = 0, lanes = 2)
        val far = IdeaLinkShape.curve(p, q, lane = 1, lanes = 2)
        // factors 0.11 and 0.25
        assertEquals(20.0 + 100.0 * 0.11, near.control.y, 1e-9)
        assertEquals(20.0 + 100.0 * 0.25, far.control.y, 1e-9)
        assertTrue("each lane bows its own way", near.control != far.control)
        assertEquals("both end on the same cards", near.start to near.end, far.start to far.end)
    }

    @Test fun theDrawnQuadraticIsTheShapeEverythingElseFollows() {
        for (lanes in 1..3) for (lane in 0 until lanes) {
            val curve = IdeaLinkShape.curve(p, q, lane, lanes)
            for (k in 0..10) {
                val t = k / 10.0
                val s = 1 - t
                val x = s * s * curve.start.x + 2 * s * t * curve.control.x + t * t * curve.end.x
                val y = s * s * curve.start.y + 2 * s * t * curve.control.y + t * t * curve.end.y
                val shape = IdeaLinkShape.point(GraphLineStyle.CURVED, p, q, lane, lanes, t)
                assertEquals(shape.x, x, 1e-9)
                assertEquals(shape.y, y, 1e-9)
            }
        }
    }

    @Test fun straightSamplingIntoFloatArraysMatchesSamplingIntoPoints() {
        val start = Vec3(1f, 2f, 3f); val end = Vec3(-4f, 6f, 9f)
        val buffers = GraphStraightLine.Buffers()
        val points = FloatArray(21 * 3); val lengths = FloatArray(21)
        for (grow in listOf(0f, 0.3f, 1f)) {
            val a = GraphStraightLine.sample(start, end, grow, 20, buffers)
            val b = GraphStraightLine.sample(start, end, grow, 20, points, lengths)
            assertEquals(a, b, 0f)
            for (k in 0..20) {
                assertEquals(buffers.points[k], Vec3(points[k * 3], points[k * 3 + 1], points[k * 3 + 2]))
                assertEquals(buffers.lengths[k], lengths[k], 0f)
            }
        }
    }
}
