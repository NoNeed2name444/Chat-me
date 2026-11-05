package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * S05e: Space's links as strips (LinkStrips, iOS GraphRibbonWriter.fill and path): two vertices a
 * sample, grown from the sending end, Straight on the segment between the trimmed ends, and short
 * links kept with their trims capped. Made-up notes.
 */
class LinkStripsTest {
    private val x = Vec3(1f, 0f, 0f)
    private val y = Vec3(0f, 1f, 0f)

    private fun point(s: LinkStrips, link: Int, vertex: Int): Vec3 {
        val o = (link * s.perLink + vertex) * LinkStrips.STRIDE
        return Vec3(s.data[o], s.data[o + 1], s.data[o + 2])
    }

    private fun value(s: LinkStrips, link: Int, vertex: Int, k: Int) = s.data[(link * s.perLink + vertex) * LinkStrips.STRIDE + k]

    /** How far [p] is from the segment a-b. */
    private fun gap(p: Vec3, a: Vec3, b: Vec3): Float {
        val ab = b - a
        val t = min(max((p - a).dot(ab) / ab.dot(ab), 0f), 1f)
        return (p - (a + ab * t)).length()
    }

    @Test fun eachLinkIsOneStripOfTwoVerticesASample() {
        for (samples in listOf(40, 20)) {
            val strips = LinkStrips(samples)
            strips.add(Vec3.ZERO, Vec3(10f, 0f, 0f), 5, 1, 3f, 0f, 1f, null, null, 1f, false)
            strips.add(Vec3(0f, 5f, 0f), Vec3(0f, 5f, 8f), 1, 1, 9f, 0f, 1f, null, null, 1f, false)
            assertEquals((samples + 1) * 2, strips.perLink)
            assertEquals(2 * (samples + 1) * 2, strips.vertices)
            for (v in 0 until strips.perLink) {
                // the pair at each sample: one each side, at the same point of the curve
                assertEquals(if (v % 2 == 0) -1f else 1f, value(strips, 0, v, 3))
                if (v % 2 == 1) assertEquals(point(strips, 0, v - 1), point(strips, 0, v))
                // the tangent is a unit vector along the link; the link's own inputs on every vertex
                assertEquals(1f, Vec3(value(strips, 0, v, 4), value(strips, 0, v, 5), value(strips, 0, v, 6)).length(), 1e-4f)
                assertEquals(listOf(5f, 1f, 3f, 0f, 10f), (8..12).map { value(strips, 0, v, it) })
            }
            assertEquals("along runs from the sending end", 0f, value(strips, 0, 0, 7))
            assertEquals(10f, value(strips, 0, strips.perLink - 1, 7), 1e-4f)
            assertEquals(Vec3(0f, 5f, 8f), point(strips, 1, strips.perLink - 1))
        }
    }

    @Test fun aGrownShareDrawsOnlyThatShareFromTheSendingEnd() {
        val strips = LinkStrips(40)
        strips.add(Vec3(2f, 0f, 0f), Vec3(12f, 0f, 0f), 1, 1, 0f, 0f, 0.5f, null, null, 0.5f, false)
        val last = strips.perLink - 1
        assertEquals(Vec3(2f, 0f, 0f), point(strips, 0, 0))
        assertEquals(7f, point(strips, 0, last).x, 1e-4f)
        // in note radii (0.5 here): the drawn length is the grown share's
        assertEquals(10f, value(strips, 0, last, 7), 1e-3f)
        assertEquals(10f, value(strips, 0, 0, 12), 1e-3f)
    }

    @Test fun straightPutsEverySampleOnTheSegmentBetweenTheTrimmedEnds() {
        // a black hole at the B end bends a curved link into its disk
        val start = Vec3(0f, 0f, 0f); val end = Vec3(6f, 0f, 0f)
        val hole = LinkBend.forStyle(0, Vec3(7f, 0f, 0f), Vec3(0.3f, 1f, 0.2f))
        val strips = LinkStrips(40)
        strips.add(start, end, 5, 0, 0f, 0f, 1f, null, hole, 1f, false)
        strips.add(start, end, 5, 0, 0f, 0f, 1f, null, hole, 1f, true)
        val bent = (0 until strips.perLink).maxOf { gap(point(strips, 0, it), start, end) }
        assertTrue("curved, the link bends near the black hole: $bent", bent > 0.05f)
        val straight = (0 until strips.perLink).maxOf { gap(point(strips, 1, it), start, end) }
        assertTrue("straight, every sample on the segment: $straight", straight < 1e-4f)
        assertEquals(end, point(strips, 1, strips.perLink - 1))
        // evenly spread
        assertEquals(6f / 40, point(strips, 1, 2).x, 1e-4f)
    }

    @Test fun aLinkShorterThanItsTwoTrimsIsKeptWithEachTrimCapped() {
        val ends = FloatArray(6)
        LinkStrips.trimmed(floatArrayOf(0f, 0f, 0f), 0, floatArrayOf(1f, 0f, 0f), 0, 0.8f, 0.9f, ends)
        assertEquals(listOf(0.45f, 0f, 0f, 0.55f, 0f, 0f), ends.toList())
        LinkStrips.trimmed(floatArrayOf(0f, 0f, 0f), 0, floatArrayOf(0f, 10f, 0f), 0, 0.8f, 0.9f, ends)
        assertEquals("a long link takes its trims whole", listOf(0f, 0.8f, 0f, 0f, 9.1f, 0f), ends.toList())
        // in the map: two linked notes closer than their trims are still a beam, sent in their own order
        val positions = floatArrayOf(0f, 0f, 0f, 0.5f, 0f, 0f)
        val edges = intArrayOf(1, 0)
        val tree = Octree.build(MapGraph(positions, byteArrayOf(1, 1), edges), leafCapacity = 8, palette = SpacePalette.rgb)
        val bodies = SpaceBodies(tree, nodeRadius = 0.5f)
        val plan = FramePlan(tree.leafCount)
        for (leaf in 0 until tree.leafCount) { plan.leafState[leaf * 4 + 3] = 1f; plan.detailLeaves.add(leaf) }
        val camera = OrbitCamera(target = Vec3(0.25f, 0f, 0f), distance = 6f, yaw = 0f, pitch = 0f)
        val vp = Mat4.multiply(camera.projection(1f), camera.view())
        bodies.update(plan, vp, camera.eye(), x, y, camera.focalPixels(1000f), 64, 0f)
        assertEquals("kept", 1, bodies.beamCount)
        val b = bodies.beams
        assertTrue("both rocky: the link's first note (at 0.5) sends", abs(b[0] - (0.5f - 0.45f * 0.5f)) < 1e-4f)
        assertEquals(0.45f * 0.5f, b[4], 1e-4f)
        assertEquals("whole, with no growth given", 1f, b[10])
        val strips = LinkStrips(40).apply { write(b, bodies.beamCount, bodies.nodeRadius, false) }
        assertEquals(1, strips.count)
        val drawn = point(strips, 0, strips.perLink - 1)
        assertTrue("it ends at the B trim", sqrt((drawn.x - b[4]) * (drawn.x - b[4]) + drawn.y * drawn.y + drawn.z * drawn.z) < 1e-4f)
    }

    @Test fun aLongLinksDistancesFitTheShadersCap() {
        val strips = LinkStrips(20)
        strips.add(Vec3.ZERO, Vec3(200f, 0f, 0f), 1, 1, 0f, 0f, 1f, null, null, 1f, false)
        val last = strips.perLink - 1
        assertEquals(LinkCurve.ALONG_CAP, value(strips, 0, last, 7), 1e-3f)
        assertEquals(LinkCurve.ALONG_CAP, value(strips, 0, 0, 12), 1e-3f)
        // and evenly spread along it
        assertEquals(LinkCurve.ALONG_CAP / 20, value(strips, 0, 2, 7), 1e-3f)
    }
}
