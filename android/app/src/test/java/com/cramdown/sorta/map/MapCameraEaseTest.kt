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

    // S05h: Fly in (MapFly, iOS's fly(to:), Graph3DView.swift:2363-2420)

    /** A made-up system round a star resting at (3, 1, -2): planets out to 4 units, one nearer the camera. */
    private val star = Vec3(3f, 1f, -2f)
    private val system = listOf(star, star + Vec3(4f, 0f, 0f), star + Vec3(-2f, 1.5f, 1f), star + Vec3(0f, -1f, 3f), star + Vec3(1f, 2f, -3f))
    private val insets = MapInsets(top = 280f, bottom = 420f)

    @Test fun flyInCentresOnTheFolderAndFillsTheWindow() {
        val c = camera()
        val fit = MapFly.fit(c, star, system, w, h, insets)!!
        val flown = OrbitCamera(fit.target, fit.distance, c.yaw, c.pitch, c.fovY)
        // the turn is kept
        assertEquals(c.yaw, flown.yaw, 0f)
        assertEquals(c.pitch, flown.pitch, 0f)
        val window = MapFly.Window.of(w, h, insets)
        val seenHeight = window.share * h
        val seenWidth = window.aspect * seenHeight
        val middleX = w / 2 + window.across * h
        val middleY = h / 2 - window.up * h
        // the star sits in the window's middle
        val (sx, sy) = flown.project(star, w, h)!!
        assertEquals(middleX, sx, 0.01f * seenWidth)
        assertEquals(middleY, sy, 0.01f * seenHeight)
        // every point, padded, inside FILL of the window; the widest at its edge, within 1 %
        val f = flown.focalPixels(h)
        var most = 0f
        for (p in system) {
            val rel = p - flown.eye()
            val depth = rel.dot(flown.forward())
            val ox = rel.dot(flown.right()) / depth * f + w / 2 - middleX
            val oy = h / 2 - rel.dot(flown.up()) / depth * f - middleY
            // the padded extent at the pad's nearer depth, as a share of half the window
            val across = (kotlin.math.abs(ox) / f * depth + MapFly.PAD) / (depth - MapFly.PAD) * f / (seenWidth / 2)
            val upDown = (kotlin.math.abs(oy) / f * depth + MapFly.PAD) / (depth - MapFly.PAD) * f / (seenHeight / 2)
            most = maxOf(most, across, upDown)
            val (px, py) = flown.project(p, w, h)!!
            assertTrue("$p inside the window", kotlin.math.abs(px - middleX) <= seenWidth / 2 && kotlin.math.abs(py - middleY) <= seenHeight / 2)
        }
        assertEquals(MapFly.FILL, most, 0.01f)
    }

    @Test fun flyInIsHalfwayAtHalfItsTimeAndAtOnceWithAnimationsOff() {
        val c = camera()
        val fit = MapFly.fit(c, star, system, w, h, insets)!!
        assertEquals(0.8f, MapFly.seconds(lively = true), 0f)
        val flight = CameraFlight(c.target, c.distance, fit, MapFly.seconds(lively = true))
        flight.step(c, 0.4f)
        assertEquals(0.5f, flight.progress, 0.01f)
        assertEquals((20f + fit.distance) / 2, c.distance, 0.01f * kotlin.math.abs(fit.distance - 20f))
        val half = (fit.target - Vec3.ZERO) * 0.5f
        assertTrue((c.target - half).length() <= 0.01f * fit.target.length())
        flight.step(c, 0.4f)
        assertTrue(flight.finished)
        assertEquals(fit.distance, c.distance, 1e-5f)
        // animations off: there in the first frame
        val still = camera()
        assertEquals(0f, MapFly.seconds(lively = false), 0f)
        val at = CameraFlight(still.target, still.distance, fit, MapFly.seconds(lively = false))
        at.step(still, 0f)
        assertTrue(at.finished)
        assertEquals(fit.distance, still.distance, 0f)
        assertTrue((still.target - fit.target).length() < 1e-6f)
        assertNull(MapFly.fit(c, star, emptyList(), w, h))
    }
}
