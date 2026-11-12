package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.MapFirstView
import com.cramdown.sorta.map.MapGraph
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

    // S05j item 3: the first view is the theme's own, iOS's camera and fit, not the sphere

    /** A small map, twice as long as wide, off the origin. */
    private fun scene(theme: MapTheme): MapScene {
        val n = 40
        val positions = FloatArray(n * 3)
        for (i in 0 until n) {
            positions[3 * i] = 4f + (i % 5) * 1.5f; positions[3 * i + 1] = -2f + (i / 5) * 2f
            positions[3 * i + 2] = ((i * 7) % 5 - 2) * 0.4f
        }
        val graph = MapGraph(positions, ByteArray(n), IntArray(0), List(n) { "Idea $it" }, List(n) { "id-$it" })
        return MapScene.of(graph, theme = theme, universe = false)
    }

    private fun bodies(scene: MapScene) = List(scene.tree.nodeCount) { i ->
        Vec3(scene.tree.positions[3 * i], scene.tree.positions[3 * i + 1], scene.tree.positions[3 * i + 2])
    }

    @Test fun eachThemeTakesIosFieldYawAndPitch() {
        val target = Vec3(1f, 2f, 3f)
        for ((theme, want) in listOf(MapTheme.SPACE to Triple(55f, 0f, 45f), MapTheme.NEURONS to Triple(55f, 0f, 35f))) {
            val camera = firstCamera(theme, target)
            assertEquals("$theme's field", MapFirstView.radians(want.first), camera.fovY, 1e-6f)
            assertEquals("$theme's yaw", want.second, camera.yaw, 0f)
            assertEquals("$theme's pitch", MapFirstView.radians(want.third), camera.pitch, 1e-6f)
            assertEquals(target, camera.target)
        }
        val fast = firstCamera(MapTheme.PLAIN, target)
        assertEquals(MapFirstView.radians(60f), fast.fovY, 1e-6f)
        assertEquals(0.55f, fast.yaw, 0f)
        assertEquals(0.32f, fast.pitch, 0f)
    }

    @Test fun spacesHomeIsTheTiltedFitAndRecentreGoesBackToIt() {
        val scene = scene(MapTheme.SPACE)
        val camera = firstCamera(scene.theme, Vec3(scene.tree.cx[0], scene.tree.cy[0], scene.tree.cz[0])).apply { distance = 50f }
        val home = HomeView(camera, HomeView.framer(scene))
        assertTrue(home.fit(camera, 1080, 2400, carded))
        // one single style: every body, padded by its size, at the plain fit (GraphFit.plain)
        val want = MapFirstView.tilted(bodies(scene), scene.nodeRadius, 0.8f, 1f, MapFirstView.radians(45f), 1080f, 2400f, carded)
        assertEquals(want.distance, camera.distance, want.distance * 1e-3f)
        assertEquals(0f, (camera.target - want.target).length(), want.distance * 1e-3f)
        val sphere = OrbitCamera(pitch = camera.pitch).fitWindow(Vec3(scene.tree.cx[0], scene.tree.cy[0], scene.tree.cz[0]),
            scene.viewRadius, 1080f, 2400f, carded)
        assertTrue("not the old sphere: ${sphere.distance} against ${camera.distance}",
            kotlin.math.abs(sphere.distance - camera.distance) > 0.01f * camera.distance)
        // every body below the strip and above the card
        for (p in bodies(scene)) {
            val (_, y) = checkNotNull(camera.project(p, 1080f, 2400f))
            assertTrue("body at $y", y >= carded.top && y <= 2400f - carded.bottom)
        }
        // moved, then Recentre (and the double tap, the same restore) takes the same view
        camera.orbit(0.4f, 0.2f); camera.zoom(1.7f); camera.pan(0.1f, 0.05f)
        home.restore(camera)
        assertEquals(want.distance, camera.distance, want.distance * 1e-3f)
        assertEquals(0f, camera.yaw, 0f)
        // a turn to a wide window rolls the map's length across
        assertTrue(home.fit(camera, 2400, 1080, chrome))
        assertEquals((Math.PI / 2).toFloat(), camera.roll, 1e-6f)
        home.restore(camera)
        assertEquals((Math.PI / 2).toFloat(), camera.roll, 1e-6f)
    }

    @Test fun performancesHomeIsTheFramingOnItsBodies() {
        val scene = scene(MapTheme.PLAIN)
        val camera = firstCamera(scene.theme, Vec3(scene.tree.cx[0], scene.tree.cy[0], scene.tree.cz[0])).apply { distance = 50f }
        val home = HomeView(camera, HomeView.framer(scene))
        assertTrue(home.fit(camera, 1080, 2400, carded))
        val want = checkNotNull(MapFirstView.framing(bodies(scene), scene.nodeRadius / 0.3f * 0.6f, 1080f, 2400f, carded))
        assertEquals(want.distance, camera.distance, want.distance * 1e-3f)
        assertEquals(0f, (camera.target - want.target).length(), want.distance * 1e-3f)
        assertEquals(0.55f, camera.yaw, 0f); assertEquals(0.32f, camera.pitch, 0f); assertEquals(0f, camera.roll, 0f)
        val sphere = OrbitCamera(yaw = 0.55f, pitch = 0.32f, fovY = camera.fovY)
            .fitWindow(Vec3(scene.tree.cx[0], scene.tree.cy[0], scene.tree.cz[0]), scene.viewRadius, 1080f, 2400f, carded)
        assertTrue("not the old sphere: ${sphere.distance} against ${camera.distance}",
            kotlin.math.abs(sphere.distance - camera.distance) > 0.01f * camera.distance)
        for (p in bodies(scene)) {
            val (x, y) = checkNotNull(camera.project(p, 1080f, 2400f))
            assertTrue("body at $x, $y", x in 0f..1080f && y >= carded.top && y <= 2400f - carded.bottom)
        }
        // Performance never rolls: iOS's Metal map has no world turn
        assertTrue(home.fit(camera, 2400, 1080, chrome))
        assertEquals(0f, camera.roll, 0f)
    }
}
