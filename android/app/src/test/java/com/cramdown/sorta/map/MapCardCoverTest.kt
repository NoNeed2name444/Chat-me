package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05d: the 3D looks' first view under the first-run card. While the card is up, it covers the
 * view from its top edge down, so the first view is framed above it (MapInsets.coveringFoot). A
 * camera still at its first view follows when the card comes or grows, and never when it goes
 * (MapCardCover), as iOS's GraphTiltTests G6 check.
 */
class MapCardCoverTest {
    private val centre = Vec3(2f, -1f, 3f)
    private val radius = 10f
    // a phone upright, 1080 x 2400: the strip's and the foot's chrome, and a card whose top is
    // 1150 pixels above the view's foot
    private val width = 1080f
    private val height = 2400f
    private val chrome = MapInsets(top = 320f, left = 12f, bottom = 410f, right = 18f)
    private val cardTop = 1150f

    private fun at(fit: MapFit) = OrbitCamera(target = fit.target, distance = fit.distance, pitch = 0.7f)

    /** The lowest and highest a point of the ball lands on screen, in pixels from the top. */
    private fun reach(camera: OrbitCamera): Pair<Float, Float> {
        val out = FloatArray(4)
        val viewProj = Mat4.multiply(camera.projection(width / height), camera.view())
        var lowest = -Float.MAX_VALUE
        var highest = Float.MAX_VALUE
        for (i in 0..12) for (j in 0 until 24) {
            val lat = (-90 + i * 15) * Math.PI / 180
            val lon = j * 15 * Math.PI / 180
            val p = centre + Vec3((kotlin.math.cos(lat) * kotlin.math.cos(lon)).toFloat(), kotlin.math.sin(lat).toFloat(),
                (kotlin.math.cos(lat) * kotlin.math.sin(lon)).toFloat()) * radius
            Mat4.project(viewProj, p.x, p.y, p.z, out)
            val y = (1f - (out[1] / out[3] * 0.5f + 0.5f)) * height
            lowest = maxOf(lowest, y); highest = minOf(highest, y)
        }
        return lowest to highest
    }

    @Test fun noCardLeavesTheEdgesAndTheFirstViewAsTheyAre() {
        assertSame(chrome, chrome.coveringFoot(0f))
        val lens = OrbitCamera(pitch = 0.7f)
        assertEquals(lens.fitWindow(centre, radius, width, height, chrome),
            lens.fitWindow(centre, radius, width, height, chrome.coveringFoot(0f)))
    }

    @Test fun aCardRaisesTheFootAloneWhereItReachesHigher() {
        assertSame("lower than the foot's chrome: nothing changes", chrome, chrome.coveringFoot(300f))
        assertEquals(MapInsets(top = 320f, left = 12f, bottom = cardTop, right = 18f), chrome.coveringFoot(cardTop))
    }

    // the whole view's chrome alone puts the ball under the card; with the card's cover, every point
    // of it is drawn between the strip and the card's top
    @Test fun theFirstViewIsFramedAboveTheCard() {
        val lens = OrbitCamera(pitch = 0.7f)
        val (before, _) = reach(at(lens.fitWindow(centre, radius, width, height, chrome)))
        assertTrue("without the cover the ball reaches under the card: $before", before > height - cardTop)
        val (lowest, highest) = reach(at(lens.fitWindow(centre, radius, width, height, chrome.coveringFoot(cardTop))))
        assertTrue("every point above the card's top: $lowest", lowest <= height - cardTop)
        assertTrue("and below the strip: $highest", highest >= chrome.top)
    }

    @Test fun aCameraAtItsFirstViewFollowsTheCardWhenItComesOrGrowsAndNeverWhenItGoes() {
        assertTrue(MapCardCover.refits(0f, 900f))
        assertTrue(MapCardCover.refits(900f, 1150f))
        assertFalse(MapCardCover.refits(1150f, 900f))
        assertFalse(MapCardCover.refits(900f, 0f))
        assertFalse(MapCardCover.refits(900f, 900f))
        assertFalse(MapCardCover.refits(0f, 0f))
    }
}
