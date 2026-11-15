package com.cramdown.sorta.ui.map

import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.MapFirstView
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.PerfForce
import com.cramdown.sorta.map.PerfMap
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.perfInput
import com.cramdown.sorta.ui.ideas.universeInput
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

    // S05j repair 4: the emulator's Space fixture (MapModeSpaceRenderTest's eight ideas in Garden, Kitchen
    // and To sort) on its phone, 1080 x 2400, the strip's foot at 275 px and the first-run card's top at
    // 1328. The first view frames every place the Universe's bodies reach (iOS's envelope): over ten
    // minutes of their orbits the bodies' box is centred in that band within item 4's bars and never
    // leaves it. At the orbit clock's 0 (the emulator's, animations off) the three To sort comets sit
    // at their homes low in the map, so that one instant's box is 74 px below the band's middle.
    @Test fun theUniversesFirstViewCentresWhereItsBodiesGo() {
        val t = 1_793_000_000_000L
        fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = t)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = t + 1)
        val hour = 3_600_000L
        val notes = listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = t + 9 * hour),
            Note(id = id(12), title = "Buy brushes", createdAt = t + 8 * hour),
            Note(id = id(13), title = "Paint the shed green", body = "Check [[Fix the gate latch]] first.", folderId = garden.id,
                links = listOf(id(12)), createdAt = t + 7 * hour),
            Note(id = id(14), title = "Turn the compost", folderId = garden.id, links = listOf(id(13)), createdAt = t + 6 * hour),
            Note(id = id(15), title = "Plant tulips before the frost", folderId = garden.id, links = listOf(id(13), id(14)), createdAt = t + 5 * hour),
            Note(id = id(16), title = "Try the seeded bread", folderId = kitchen.id, createdAt = t + 4 * hour),
            Note(id = id(17), title = "Order more coffee beans", folderId = kitchen.id, links = listOf(id(16)), createdAt = t + 3 * hour),
            Note(id = id(18), title = "Plan the weekend walk", createdAt = t + 2 * hour),
        )
        val book = IdeaBook(notes, listOf(garden, kitchen))
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        val scene = MapScene.universe(plan)
        val w = 1080f; val h = 2400f; val foot = 275f; val cardTop = 1328f
        val pose = HomeView.framer(scene)(w, h, MapInsets(top = foot, bottom = h - cardTop))
        val camera = OrbitCamera(pose.target, pose.distance, pose.yaw, pose.pitch, MapFirstView.radians(55f), pose.roll)
        val focal = camera.focalPixels(h)
        fun box(seconds: Double): FloatArray {
            val r = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in plan.bodies.indices) {
                val p = plan.position(i, seconds)
                val (x, y) = checkNotNull(camera.project(p, w, h))
                val half = scene.nodeRadius * focal / camera.depthOf(p)
                r[0] = minOf(r[0], x - half); r[1] = minOf(r[1], y - half); r[2] = maxOf(r[2], x + half); r[3] = maxOf(r[3], y + half)
            }
            return r
        }
        val band = cardTop - foot
        val rest = box(0.0)
        // the emulator's run 43383283733 drew this box at the clock's 0: 519.414-1232.5205
        assertEquals(519.414f, rest[1], 1f); assertEquals(1232.52f, rest[3], 1f)
        var top = Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        var seconds = 0.0
        while (seconds <= 600.0) { val b = box(seconds); top = minOf(top, b[1]); bottom = maxOf(bottom, b[3]); seconds += 1.0 }
        assertTrue("inside the band as the map runs: $top-$bottom", top >= foot && bottom <= cardTop)
        val off = kotlin.math.abs((top + bottom) / 2 - (foot + cardTop) / 2)
        val gaps = kotlin.math.abs((top - foot) - (cardTop - bottom))
        assertTrue("off the band's middle by $off ($top-$bottom)", off <= 0.04f * h)
        assertTrue("gaps differ by $gaps, 8% of the band ${0.08f * band} ($top-$bottom)", gaps <= 0.08f * band)
    }

    // v13: Performance's first view on CI's stills was not framed in its window: names ran off the
    // phone's right edge ("Loop diuret…", "Atrial fibri…", "Try the s…"). iOS frames the bodies as they
    // are laid out with 0.6 of the layout's spacing round each, filling 0.9 of the window the chrome
    // leaves (GraphPerfMapView.swift:454-470, GraphPerfCamera.swift:192-229); the first view took
    // 0.36 (the layout's note radius read as 0.3 of a spacing). Every laid-out body, with that room
    // round it, now lies inside the window less 5% at each side, on a phone and a tablet; and the
    // bodies the force settles stay inside the window.
    private val windows = listOf(
        Triple(1080f, 2400f, MapInsets(top = 275f, bottom = 300f)),
        Triple(2560f, 1600f, MapInsets(top = 200f, bottom = 220f)),
    )

    private fun requirePerformanceFramed(book: IdeaBook, what: String) {
        val input = perfInput(book, book.allEdges(), MapLinkLength.STANDARD)
        val map = checkNotNull(PerfMap.build(input))
        val scene = MapScene.performance(map, input)
        val g = map.graph
        val room = map.seed.spacing * 0.6f
        val settled = PerfForce(g, map.seed).apply { settle() }
        for ((w, h, insets) in windows) {
            val pose = HomeView.framer(scene)(w, h, insets)
            val camera = OrbitCamera(pose.target, pose.distance, pose.yaw, pose.pitch, MapFirstView.radians(60f), pose.roll)
            val focal = camera.focalPixels(h)
            val seenW = w - insets.left - insets.right
            val seenH = h - insets.top - insets.bottom
            val left = insets.left + seenW * 0.05f; val right = w - insets.right - seenW * 0.05f
            val top = insets.top + seenH * 0.05f; val bottom = h - insets.bottom - seenH * 0.05f
            for (i in 0 until g.nodeCount) {
                val p = Vec3(map.seed.positions[3 * i], map.seed.positions[3 * i + 1], map.seed.positions[3 * i + 2])
                val (x, y) = checkNotNull(camera.project(p, w, h)) { "$what: body $i behind the camera on $w x $h" }
                val r = room * focal / camera.depthOf(p)
                assertTrue("$what: ${scene.titles[i]} at $x, $y with $r px round it, outside $left-$right x $top-$bottom on $w x $h",
                    x - r >= left - 0.5f && x + r <= right + 0.5f && y - r >= top - 0.5f && y + r <= bottom + 0.5f)
                val q = settled.position(i)
                val (sx, sy) = checkNotNull(camera.project(Vec3(q[0], q[1], q[2]), w, h))
                assertTrue("$what: ${scene.titles[i]} settles at $sx, $sy, outside the window on $w x $h",
                    sx in insets.left..(w - insets.right) && sy in insets.top..(h - insets.bottom))
            }
        }
    }

    @Test fun performancesFirstViewFramesTheEmulatorsIdeasWithIosRoom() {
        val t = 1_793_000_000_000L
        fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = t)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = t + 1)
        val hour = 3_600_000L
        val notes = listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = t + 9 * hour),
            Note(id = id(12), title = "Buy brushes", createdAt = t + 8 * hour),
            Note(id = id(13), title = "Paint the shed green", body = "Check [[Fix the gate latch]] first.", folderId = garden.id,
                links = listOf(id(12)), createdAt = t + 7 * hour),
            Note(id = id(14), title = "Turn the compost", folderId = garden.id, links = listOf(id(13)), createdAt = t + 6 * hour),
            Note(id = id(15), title = "Plant tulips before the frost", folderId = garden.id, links = listOf(id(13), id(14)), createdAt = t + 5 * hour),
            Note(id = id(16), title = "Try the seeded bread", folderId = kitchen.id, createdAt = t + 4 * hour),
            Note(id = id(17), title = "Order more coffee beans", folderId = kitchen.id, links = listOf(id(16)), createdAt = t + 3 * hour),
            Note(id = id(18), title = "Plan the weekend walk", createdAt = t + 2 * hour),
        )
        requirePerformanceFramed(IdeaBook(notes, listOf(garden, kitchen)), "the emulator's ideas")
    }

    @Test fun performancesFirstViewFramesTheSampleWithIosRoom() {
        requirePerformanceFramed(SampleIdeas.book(), "the sample")
    }
}
