package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The camera's move when a body is chosen (S05g), as iOS's Graph3DView.ease and GraphDepthGlide: a
// tap eases the body 0.34 of the way to the goal (the middle across, 0.34 down, what the card leaves)
// and comes a tenth closer, over 0.45 s; a link chip's body glides onto the goal over 0.7 s, at its
// own depth or the focus's, whichever is nearer; both on iOS's easeInEaseOut.
class MapCameraEaseTest {
    private val w = 1080f
    private val h = 2340f

    private fun camera() = OrbitCamera(target = Vec3(0f, 0f, 0f), distance = 20f, yaw = 0.4f, pitch = 0.3f)

    /** The camera moved by [move]: target and eye together. */
    private fun moved(c: OrbitCamera, move: Vec3) = OrbitCamera(c.target + move, c.distance, c.yaw, c.pitch, c.fovY)

    /** Where [p] is on screen and how deep, from [c]. */
    private fun screen(c: OrbitCamera, p: Vec3): Triple<Float, Float, Float> {
        val rel = p - c.eye()
        val depth = rel.dot(c.forward())
        val f = c.focalPixels(h)
        return Triple(w / 2 + rel.dot(c.right()) / depth * f, h / 2 - rel.dot(c.up()) / depth * f, depth)
    }

    @Test fun theNumbersAreIOSs() {
        assertEquals(0.34f, MapCameraEase.SHARE, 0f)
        assertEquals(0.1f, MapCameraEase.CLOSER, 0f)
        assertEquals(0.45f, MapCameraEase.EASE_SECONDS, 0f)
        assertEquals(0.7f, MapCameraEase.GLIDE_SECONDS, 0f)
        val (gx, gy) = MapCameraEase.goal(w, h)
        assertEquals(540f, gx, 0f)
        assertEquals(h * 0.34f, gy, 1e-3f)
    }

    @Test fun aTapEasesTheBodyAThirdOfTheWayAndATenthCloser() {
        val c = camera()
        val body = c.target + c.right() * 3f + c.up() * -4f + c.forward() * 2f
        val (sx, sy, depth) = screen(c, body)
        val move = checkNotNull(MapCameraEase.ease(c, body, w, h))
        val (nx, ny, nd) = screen(moved(c, move), body)
        val (gx, gy) = MapCameraEase.goal(w, h)
        assertEquals("a tenth closer, along the line to it", depth * 0.9f, nd, 1e-3f)
        // iOS shifts it 0.34 of the way at its depth, then comes a tenth closer along the line to it:
        // on screen that is 0.34 / 0.9 of the way
        assertEquals((gx - sx) * 0.34f / 0.9f, nx - sx, 0.05f)
        assertEquals((gy - sy) * 0.34f / 0.9f, ny - sy, 0.05f)
        assertTrue("towards the goal", kotlin.math.abs(gx - nx) < kotlin.math.abs(gx - sx) && kotlin.math.abs(gy - ny) < kotlin.math.abs(gy - sy))
    }

    @Test fun noEaseForABodyBehindOrNoView() {
        val c = camera()
        assertNull("behind the eye", MapCameraEase.ease(c, c.eye() - c.forward() * 3f, w, h))
        assertNull("past the far plane", MapCameraEase.ease(c, c.eye() + c.forward() * (c.far() + 1f), w, h))
        assertNull("no view yet", MapCameraEase.ease(c, c.target, 0f, 0f))
        assertNull(MapCameraEase.glide(c, c.target, 1f, h))
    }

    @Test fun aChipsBodyGlidesOntoTheGoalAtTheNearerDepth() {
        val c = camera()
        val (gx, gy) = MapCameraEase.goal(w, h)
        val focus = (c.target - c.eye()).dot(c.forward())
        // nearer than what the camera looks at: lands at its own depth
        val near = c.target + c.right() * 6f + c.up() * 2f - c.forward() * 5f
        val ownDepth = (near - c.eye()).dot(c.forward())
        val m1 = checkNotNull(MapCameraEase.glide(c, near, w, h))
        val (x1, y1, d1) = screen(moved(c, m1), near)
        assertEquals(gx, x1, 0.5f); assertEquals(gy, y1, 0.5f)
        assertEquals(ownDepth, d1, 1e-3f)
        // farther: at the focus's depth
        val far = c.target + c.right() * -7f + c.forward() * 30f
        val m2 = checkNotNull(MapCameraEase.glide(c, far, w, h))
        val (x2, y2, d2) = screen(moved(c, m2), far)
        assertEquals(gx, x2, 0.5f); assertEquals(gy, y2, 0.5f)
        assertEquals(focus, d2, 1e-3f)
        // behind the eye: the focus's depth still stands
        val behind = c.eye() - c.forward() * 4f
        val m3 = checkNotNull(MapCameraEase.glide(c, behind, w, h))
        assertEquals(focus, screen(moved(c, m3), behind).third, 1e-3f)
    }

    @Test fun aDepthStandsOnlyPastTwiceTheNearest() {
        assertTrue(MapCameraEase.usable(0.11f, 0.05f))
        assertTrue(!MapCameraEase.usable(0.1f, 0.05f))
        assertTrue(!MapCameraEase.usable(Float.NaN))
        assertTrue(!MapCameraEase.usable(Float.POSITIVE_INFINITY))
    }

    @Test fun easeInEaseOutIsIOSsCurve() {
        assertEquals(0f, MapCameraEase.eased(0f), 0f)
        assertEquals(1f, MapCameraEase.eased(1f), 0f)
        assertEquals(0f, MapCameraEase.eased(-1f), 0f)
        assertEquals(1f, MapCameraEase.eased(2f), 0f)
        assertEquals("symmetric round the middle", 0.5f, MapCameraEase.eased(0.5f), 1e-4f)
        for (k in 1..9) {
            val t = k / 10f
            assertEquals(1f, MapCameraEase.eased(t) + MapCameraEase.eased(1f - t), 1e-4f)
            assertTrue("rises", MapCameraEase.eased(t) > MapCameraEase.eased(t - 0.1f))
        }
        assertTrue("slow out of the start", MapCameraEase.eased(0.1f) < 0.05f)
    }

    @Test fun aMoveIsSpreadOverItsTimeAndAddsUpWhole() {
        val move = Vec3(3f, -6f, 9f)
        val ease = CameraMove(move, MapCameraEase.EASE_SECONDS)
        var sum = Vec3.ZERO
        var frames = 0
        while (!ease.finished) { sum = sum + ease.step(1f / 60); frames++ }
        assertTrue("0.45 s: 27 frames at 60 Hz, or 28 with the float's rounding: $frames", frames in 27..28)
        assertEquals(move.x, sum.x, 1e-4f); assertEquals(move.y, sum.y, 1e-4f); assertEquals(move.z, sum.z, 1e-4f)
        assertEquals("then nothing more", Vec3.ZERO, ease.step(1f / 60))
        val half = CameraMove(move, 1f)
        val first = half.step(0.5f)
        assertEquals("half the time, half the way", 1.5f, first.x, 1e-3f)
        val now = CameraMove(move, 0f)
        assertEquals("no time: all at once", move, now.step(0f))
        assertTrue(now.finished)
    }
}
