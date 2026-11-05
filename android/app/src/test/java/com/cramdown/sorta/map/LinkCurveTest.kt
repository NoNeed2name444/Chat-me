package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min

/**
 * S05e: a link is ONE smooth curve. iOS's GraphicsTests G4a to G4p, G5a to G5e and G6a to G6c,
 * number for number (the samples are iOS's: 40 at High, 20 at Smooth; the beam's half width
 * 0.10), then Android's own cases for the bends.
 */
class LinkCurveTest {
    private val high = 40
    private val smooth = 20
    private val beamHalf = 0.10f

    private fun near(a: Float, b: Float, eps: Float = 0.0005f) = abs(a - b) <= eps
    private fun near3(a: Vec3, b: Vec3, eps: Float = 0.0005f) = LinkCurve.length(a - b) <= eps
    private fun at(points: FloatArray, k: Int) = Vec3(points[k * 3], points[k * 3 + 1], points[k * 3 + 2])

    private fun sampled(path: LinkPath, n: Int): Triple<FloatArray, FloatArray, Float> {
        val points = FloatArray((n + 1) * 3)
        val lengths = FloatArray(n + 1)
        val total = LinkCurve.sample(path, n, points, lengths)
        return Triple(points, lengths, total)
    }

    /** The largest turn, in degrees, between neighbouring pieces of a sampled curve. */
    private fun sharpest(points: FloatArray, n: Int): Float {
        var worst = 0f
        if (n < 2) return 0f
        for (k in 1 until n) {
            val a = LinkCurve.unit(at(points, k) - at(points, k - 1), Vec3(1f, 0f, 0f))
            val b = LinkCurve.unit(at(points, k + 1) - at(points, k), Vec3(1f, 0f, 0f))
            val c = min(max(LinkCurve.dot(a, b), -1f), 1f)
            worst = max(worst, acos(c) * 180f / PI.toFloat())
        }
        return worst
    }

    /** How far a point is from the segment a-b. */
    private fun gap(p: Vec3, a: Vec3, b: Vec3): Float {
        val ab = b - a
        val squared = LinkCurve.dot(ab, ab)
        if (squared <= 1e-12f) return LinkCurve.length(p - a)
        val t = min(max(LinkCurve.dot(p - a, ab) / squared, 0f), 1f)
        return LinkCurve.length(p - (a + ab * t))
    }

    /** The furthest the strip drawn from [n] samples strays from the true curve (checked at 1024 points). */
    private fun stray(path: LinkPath, n: Int): Float {
        val (drawn, _, _) = sampled(path, n)
        var worst = 0f
        for (j in 0..1024) {
            val q = LinkCurve.point(path, j / 1024f)
            var best = Float.MAX_VALUE
            for (k in 0 until n) best = min(best, gap(q, at(drawn, k), at(drawn, k + 1)))
            worst = max(worst, best)
        }
        return worst
    }

    // arched and bent into a black hole at B and a gas giant at A: the Universe's hardest case
    private val centreA = Vec3(0f, 0f, 0f)
    private val centreB = Vec3(12f, 2f, -3f)
    private val axisA = Vec3(0f, 1f, 0f)
    private val axisB = Vec3(0.3f, 0.9f, 0.3f)
    private val dirAB = LinkCurve.unit(centreB - centreA, Vec3(1f, 0f, 0f))

    private fun hard() = LinkPath(start = centreA + dirAB * 1.2f, end = centreB - dirAB * 1.6f).apply {
        arch = LinkCurve.unit(LinkCurve.cross(dirAB, Vec3(0f, 0f, 1f)), Vec3(0f, 1f, 0f))
        archSize = 0.10f * LinkCurve.length(centreB - centreA)
        bendA = LinkBend.forStyle(2, centreA, axisA)
        bendB = LinkBend.forStyle(0, centreB, axisB)
    }

    @Test fun g4aToG4dAStraightLink() {
        val path = LinkPath(Vec3(0f, 0f, 0f), Vec3(10f, 0f, 0f))
        val (points, lengths, total) = sampled(path, 32)
        assertTrue("G4a a straight link is its length: $total", near(total, 10f, 0.001f))
        assertTrue("G4b it starts and ends at the trims", near3(at(points, 0), path.start) && near3(at(points, 32), path.end))
        for (k in 1..32) assertTrue("G4c its samples are evenly spaced", near(lengths[k] - lengths[k - 1], 10f / 32, 0.001f))
        assertTrue("G4d and it has no turn at all", sharpest(points, 32) < 0.01f)
    }

    @Test fun g4eABlackHoleAndAGasGiantBendASunDoesNot() {
        val path = hard()
        assertNotNull(path.bendA); assertNotNull(path.bendB)
        assertNull(LinkBend.forStyle(5, centreA, axisA))
    }

    @Test fun g4fToG4lTheHardestCaseIsSmooth() {
        val path = hard()
        val old = stray(path, 6)
        val smoothStray = stray(path, smooth)
        val highStray = stray(path, high)
        assertTrue("G4f six segments stray by more than the beam's half width: $old", old > beamHalf)
        assertTrue("G4g Smooth strays by under a quarter of the half width: $smoothStray", smoothStray < beamHalf * 0.25f)
        assertTrue("G4h High strays by under a tenth of it: $highStray", highStray < beamHalf * 0.1f)
        val oldTurn = sharpest(sampled(path, 6).first, 6)
        val (points, lengths, _) = sampled(path, high)
        val highTurn = sharpest(points, high)
        assertTrue("G4i High's sharpest joint turns under a quarter as much: $highTurn vs $oldTurn", highTurn < oldTurn / 4)
        val dA = LinkCurve.length(at(points, 0) - centreA)
        val dB = LinkCurve.length(at(points, high) - centreB)
        assertTrue("G4j the bent ends keep their distance from each body: $dA $dB", near(dA, 1.2f, 0.002f) && near(dB, 1.6f, 0.002f))
        for (k in 1..high) assertTrue("G4k the distance along it only ever grows", lengths[k] > lengths[k - 1])
        for (v in points) assertTrue("G4l every point is finite", v.isFinite())
    }

    @Test fun g4mSmoothAndHighDrawTheSameCurve() {
        val fine = sampled(hard(), 40).first
        val coarse = sampled(hard(), 20).first
        for (k in 0..20) assertTrue("G4m sample $k", near3(at(coarse, k), at(fine, k * 2), 0.0001f))
    }

    @Test fun g4nG4oTheGrownPartIsTheStartOfTheFullCurve() {
        val half = hard().apply { grow = 0.5f }
        assertTrue("G4n a half-grown link reaches the full curve's middle", near3(LinkCurve.point(half, 1f), LinkCurve.point(hard(), 0.5f), 0.0001f))
        assertTrue("G4o and starts where the full one does", near3(LinkCurve.point(half, 0f), LinkCurve.point(hard(), 0f), 0.0001f))
    }

    @Test fun g4pALinkOfNoLength() {
        val path = LinkPath(Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
        val (points, _, total) = sampled(path, 14)
        assertTrue("G4p one point, length 0", total == 0f && near3(at(points, 14), path.start))
    }

    @Test fun g5TheDistanceAlongForTheShader() {
        assertEquals("G5a a short link keeps its distances", 1f, LinkCurve.alongScale(30f))
        assertEquals("G5b a link of exactly 60 keeps them", 1f, LinkCurve.alongScale(60f))
        assertTrue("G5c a 120-unit link is squeezed by half", near(LinkCurve.alongScale(120f), 0.5f))
        val (_, lengths, total) = sampled(LinkPath(Vec3(0f, 0f, 0f), Vec3(150f, 0f, 0f)), 32)
        val scale = LinkCurve.alongScale(total)
        val last = lengths[32] * scale
        val before = lengths[31] * scale
        assertTrue("G5d a 150-unit link's far end sits at 60: $last", near(last, 60f, 0.01f))
        assertTrue("G5e and its last stretch still flows: ${last - before}", last - before > 1f)
    }

    @Test fun g6TheHelpers() {
        assertTrue("G6a cross of x and y is z", near3(LinkCurve.cross(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f)), Vec3(0f, 0f, 1f)))
        assertTrue("G6b a quarter turn about z takes x to y", near3(LinkCurve.rotate(Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), PI.toFloat() / 2), Vec3(0f, 1f, 0f), 0.0001f))
        val square = LinkCurve.perpendicular(Vec3(1f, 0f, 0f))
        assertTrue("G6c a perpendicular is square and unit", near(LinkCurve.dot(square, Vec3(1f, 0f, 0f)), 0f) && near(LinkCurve.length(square), 1f))
    }

    // Android's own: what each bend does near its end

    /** How far [p] is from the plane square to [axis] through [centre]. */
    private fun offPlane(p: Vec3, centre: Vec3, axis: Vec3) = abs(LinkCurve.dot(p - centre, LinkCurve.unit(axis, axis)))

    private fun endNear(code: Int, axis: Vec3): Pair<Vec3, Vec3> {
        // a link coming down onto the body at B, from well above its plane
        val centre = Vec3(0f, 0f, 0f)
        val straight = LinkPath(start = Vec3(-6f, 8f, 1f), end = centre + LinkCurve.unit(Vec3(-6f, 8f, 1f), axis) * 1.5f)
        val bent = LinkPath(start = straight.start, end = straight.end, bendB = LinkBend.forStyle(code, centre, axis))
        return LinkCurve.point(straight, 0.9f) to LinkCurve.point(bent, 0.9f)
    }

    @Test fun aBlackHoleBendKeepsTheDistanceAndLiesNearerTheDiskPlane() {
        val axis = Vec3(0f, 1f, 0f)
        val (straight, bent) = endNear(0, axis)
        assertTrue("the bent point keeps its distance from the centre",
            near(LinkCurve.length(bent), LinkCurve.length(straight), 0.0005f))
        assertTrue("and lies nearer the disk's plane: ${offPlane(bent, Vec3.ZERO, axis)} vs ${offPlane(straight, Vec3.ZERO, axis)}",
            offPlane(bent, Vec3.ZERO, axis) < offPlane(straight, Vec3.ZERO, axis))
    }

    @Test fun aGasGiantPullsLessAndOtherStylesDoNotBend() {
        val axis = Vec3(0f, 1f, 0f)
        val (straight, hole) = endNear(0, axis)
        val (_, giant) = endNear(2, axis)
        val pulledHole = offPlane(straight, Vec3.ZERO, axis) - offPlane(hole, Vec3.ZERO, axis)
        val pulledGiant = offPlane(straight, Vec3.ZERO, axis) - offPlane(giant, Vec3.ZERO, axis)
        assertTrue("a gas giant pulls ($pulledGiant) less than a black hole ($pulledHole)", pulledGiant > 0f && pulledGiant < pulledHole)
        for (code in listOf(1, 3, 4, 5)) assertNull("style $code does not bend", LinkBend.forStyle(code, Vec3.ZERO, axis))
    }
}
