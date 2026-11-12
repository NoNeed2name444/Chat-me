package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05d: the 3D map's home under the first-run card (Map3DView's HomeView). A camera still at its
 * first view follows the chrome and the card; when the card goes the home moves and the camera
 * stays, still counted at its first view, so a turn or a fold takes it as iOS's untouched camera is
 * taken. Once the person moved it, nothing but Recentre does.
 */
class HomeViewTest {
    private val centre = Vec3(2f, -1f, 3f)
    private val radius = 10f
    // a phone upright, 1080 x 2400, and turned; the strip's and the foot's chrome, and a card whose
    // top is 1150 pixels above the view's foot
    private val chrome = MapInsets(top = 320f, left = 12f, bottom = 410f, right = 18f)
    private val carded = chrome.coveringFoot(1150f)

    private fun camera() = OrbitCamera(target = centre, distance = 60f, pitch = 0.7f)
    private fun fitted(width: Int, height: Int, insets: MapInsets) =
        OrbitCamera(pitch = 0.7f).fitWindow(centre, radius, width.toFloat(), height.toFloat(), insets)

    private fun assertAt(width: Int, height: Int, insets: MapInsets, camera: OrbitCamera) {
        val fit = fitted(width, height, insets)
        assertEquals(fit.distance, camera.distance, 1e-4f)
        assertEquals(0f, (camera.target - fit.target).length(), 1e-4f)
    }

    @Test fun aCameraAtItsFirstViewFollowsTheChromeAndTheCard() {
        val camera = camera()
        val home = HomeView(camera, radius)
        assertTrue(home.fit(camera, 1080, 2400, chrome))
        assertAt(1080, 2400, chrome, camera)
        assertTrue("the card comes", home.fit(camera, 1080, 2400, carded))
        assertAt(1080, 2400, carded, camera)
    }

    @Test fun whenTheCardGoesTheCameraStaysAndATurnStillTakesIt() {
        val camera = camera()
        val home = HomeView(camera, radius)
        home.fit(camera, 1080, 2400, carded)
        val target = camera.target; val distance = camera.distance
        assertFalse("the card goes: the camera stays", home.fit(camera, 1080, 2400, chrome, follow = false))
        assertEquals(target, camera.target)
        assertEquals(distance, camera.distance, 0f)
        assertTrue("still its first view: a turn takes it", home.fit(camera, 2400, 1080, chrome))
        assertAt(2400, 1080, chrome, camera)
    }

    @Test fun aCameraThePersonMovedStaysWhereItIs() {
        val camera = camera()
        val home = HomeView(camera, radius)
        home.fit(camera, 1080, 2400, carded)
        home.fit(camera, 1080, 2400, chrome, follow = false)
        camera.zoom(1.5f)
        val target = camera.target; val distance = camera.distance
        assertFalse(home.fit(camera, 2400, 1080, chrome))
        assertEquals(target, camera.target)
        assertEquals(distance, camera.distance, 0f)
    }

    @Test fun recentreFramesWithoutTheCardOnceItIsGone() {
        val camera = camera()
        val home = HomeView(camera, radius)
        home.fit(camera, 1080, 2400, carded)
        home.fit(camera, 1080, 2400, chrome, follow = false)
        home.restore(camera)
        assertAt(1080, 2400, chrome, camera)
    }
}
