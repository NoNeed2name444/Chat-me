package com.cramdown.sorta.ui.map

import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.universeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05h repair 7: Fly in ends at two taps on empty space as at Recentre (the plan's Q5). Both call
 * GlMapRenderer.goHome: the flight stops, no folder is flown in to (MapDrawn.flown), and the camera is
 * back at its first view however many frames step after. The gesture that calls it (Map3DView's
 * pointer input) and the record that shows `flown` need the GL thread, so they are not reached off
 * the device; the renderer is, without a GL context, since it makes nothing on the GL until a surface.
 */
class MapFlightTest {
    private val w = 1080
    private val h = 2340
    private val insets = MapInsets(top = 250f, bottom = 400f)

    @Test fun twoTapsOnEmptySpaceEndAFlightAsRecentreDoes() {
        val book = SampleIdeas.book()
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        val scene = MapScene.universe(plan)
        val tree = scene.tree
        val camera = OrbitCamera(target = Vec3(tree.cx[0], tree.cy[0], tree.cz[0]), pitch = 0.7f).apply {
            distance = fitDistance(scene.viewRadius, aspect = w.toFloat() / h)
        }
        val home = HomeView(camera, scene.viewRadius)
        home.fit(camera, w, h, insets)
        val firstTarget = camera.target; val firstDistance = camera.distance
        val renderer = GlMapRenderer(tree, camera, scene.nodeRadius, displayHz = { 60f }, displayMaxHz = 60f, onReadout = {},
            theme = MapTheme.SPACE, links = scene.links, ids = scene.ids, universe = true, universePlan = plan)
        val folder = plan.bodies.indices.first { renderer.canFlyTo(it) && plan.bodies[it].parent >= 0 }

        // mid-flight: two taps on empty space
        assertTrue(renderer.flyTo(folder, w.toFloat(), h.toFloat(), insets))
        assertEquals(folder, renderer.flown)
        renderer.stepFlight(0.2f)
        assertTrue("the camera is on its way", renderer.flying && (camera.target - firstTarget).length() > 1e-3f)
        renderer.goHome(home)
        assertEquals("no folder flown in to", -1, renderer.flown)
        assertFalse("the flight ended", renderer.flying)
        repeat(10) { renderer.stepFlight(1f / 60) }
        assertEquals("the first view, kept", 0f, (camera.target - firstTarget).length(), 1e-5f)
        assertEquals(firstDistance, camera.distance, 1e-5f)

        // a flight landed, then two taps: the same
        assertTrue(renderer.flyTo(folder, w.toFloat(), h.toFloat(), insets))
        renderer.stepFlight(0.25f); renderer.stepFlight(0.25f); renderer.stepFlight(0.25f); renderer.stepFlight(0.25f)
        assertFalse("landed", renderer.flying)
        assertEquals(folder, renderer.flown)
        renderer.goHome(home)
        assertEquals(-1, renderer.flown)
        assertEquals(0f, (camera.target - firstTarget).length(), 1e-5f)
        assertEquals(firstDistance, camera.distance, 1e-5f)
    }
}
