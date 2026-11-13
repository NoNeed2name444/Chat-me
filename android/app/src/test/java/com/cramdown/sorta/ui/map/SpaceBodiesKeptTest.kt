package com.cramdown.sorta.ui.map

import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.FrameBudget
import com.cramdown.sorta.map.FramePlan
import com.cramdown.sorta.map.GuardBand
import com.cramdown.sorta.map.LodSelector
import com.cramdown.sorta.map.DeathKind
import com.cramdown.sorta.map.FilterPop
import com.cramdown.sorta.map.MapDeathStage
import com.cramdown.sorta.map.MapDeparture
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.SpaceBodies
import com.cramdown.sorta.map.SpaceLod
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.SpaceTrails
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.universeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05h2 repair 5: the marked body (the held one, else the chosen one) is always drawn whole, as iOS's
 * depth stage keeps `grabbed ?? selected` (GraphSpaceDepthStage.swift:151-158), whatever its chunk's
 * openness. The tablet's red run (43383798070): MapSpaceLivelyTest's Universe in one chunk, at the
 * failing frame's pose on the 1536 x 1600 map with the frame budget at its floor, is part way to its
 * summary (openness under SpaceBodies.OPEN), so no body was drawn, the held star had no emitter and
 * threw no sparks. Repair 6: a dying body is drawn whole too, however small, as iOS's dying copy is a
 * plain node with no level of detail (GraphDeathScene.swift:226-242). Made-up fixture, as the lively test's.
 */
class SpaceBodiesKeptTest {
    private val book = run {
        val t = 1_793_000_000_000L
        fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = t)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = t + 1)
        val seeds = NoteFolder(id = id(203), name = "Seeds", parentId = garden.id, createdAt = t + 2)
        val h = 3_600_000L
        IdeaBook(listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = t + 9 * h),
            Note(id = id(12), title = "Buy brushes", createdAt = t + 8 * h),
            Note(id = id(13), title = "Paint the shed green", folderId = garden.id, links = listOf(id(12)), createdAt = t + 7 * h),
            Note(id = id(14), title = "Turn the compost", folderId = garden.id, links = listOf(id(13)), createdAt = t + 6 * h),
            Note(id = id(15), title = "Plant tulips before the frost", folderId = garden.id, links = listOf(id(13), id(14)), createdAt = t + 5 * h),
            Note(id = id(16), title = "Try the seeded bread", folderId = kitchen.id, createdAt = t + 4 * h),
            Note(id = id(17), title = "Order more coffee beans", folderId = kitchen.id, links = listOf(id(16)), createdAt = t + 3 * h),
            Note(id = id(18), title = "Plan the weekend walk", createdAt = t + 2 * h),
            Note(id = id(19), title = "Sort the seed packets", folderId = seeds.id, createdAt = t + h),
        ), listOf(garden, kitchen, seeds))
    }
    private val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
    private val scene = MapScene.universe(plan)
    private val tree = scene.tree
    private val star = plan.bodies.indexOfFirst { it.role == UniverseRole.STAR }.let { s -> (0 until tree.nodeCount).first { tree.order[it] == s } }

    private fun bodies() = SpaceBodies(tree, scene.nodeRadius).apply {
        homes = tree.positions.copyOf()
        radii = FloatArray(tree.nodeCount) { k -> UniverseBeams.shaderRadius(plan.bodies[tree.order[k]]) }
        reaches = FloatArray(tree.nodeCount) { k -> UniverseBeams.reach(plan.bodies[tree.order[k]]) }
    }

    /** The failing frame's camera (MapDrawn.pose) on the tablet's map, 1536 x 1600. */
    private val camera = OrbitCamera(target = Vec3(-0.00506151f, -5.701525f, 1.3602548f), distance = 29.404305f, yaw = 0.6f, pitch = 0.7f)
    private val aspect = 1536f / 1600f
    private val focal = camera.focalPixels(1600f)
    private val viewProj = Mat4.multiply(camera.projection(aspect), camera.view())

    /** The LOD as the renderer runs it at the budget's floor, steady after 3 s of frames. */
    private fun floorPlan(): FramePlan {
        val lod = LodSelector(tree, null)
        val open = FrameBudget(60f, initialDetail = FrameBudget.MIN_DETAIL).openPixels
        var out: FramePlan? = null
        for (f in 1..180) {
            val guard = GuardBand.predict(camera, aspect, Vec3.ZERO, 0.35f)
            out = lod.select(camera.eye(), focal, guard.view, guard, open, Int.MAX_VALUE, 1f / 60, f)
        }
        return out!!
    }

    private fun update(b: SpaceBodies, p: FramePlan, maxBodies: Int = SpaceLod.maxBodies(FrameBudget.MIN_DETAIL), vp: FloatArray = viewProj,
                       cam: OrbitCamera = camera): Int =
        b.update(p, vp, cam.eye(), cam.right(), cam.up(), focal, maxBodies, 0f)

    @Test fun theTabletsChunkPartWayToItsSummaryStillDrawsTheHeldStarAndItThrowsSparks() {
        assertEquals("the fixture's Universe is one chunk", 1, tree.leafCount)
        val p = floorPlan()
        val openness = p.leafState[3]
        assertTrue("the chunk part way to its summary at the floor: $openness", openness > 0f && openness < SpaceBodies.OPEN)
        // nothing marked: no body is drawn, as the red run's frames
        assertEquals(0, update(bodies(), p))
        // the star held: it alone is drawn, whole, and has the first emitter
        val b = bodies().apply { grabbed = star; chosen = star }
        assertEquals(1, update(b, p))
        assertEquals(star, b.nodes[0])
        assertTrue(b.isBody(star))
        val trails = SpaceTrails.of(true, universe = true, nodeRadius = scene.nodeRadius, smooth = false, bodies = tree.nodeCount, seed = 7L)
        repeat(30) {
            trails.step(1f / 30, b.nodes, b.count, star, { k -> if (k == star) Vec3(4f, 0f, 0f) else Vec3.ZERO },
                { k -> Vec3(tree.positions[3 * k], tree.positions[3 * k + 1], tree.positions[3 * k + 2]) }, { k -> tree.groups[k].toInt() })
        }
        assertTrue("the held star threw sparks: ${trails.thrownTotal}", trails.thrownTotal > 0)
        // only chosen: kept too
        val c = bodies().apply { chosen = star }
        assertEquals(1, update(c, p))
        assertEquals(star, c.nodes[0])
    }

    @Test fun theKeptBodyHasAPlaceOfTheLimitAndAChunkStillSummarisedKeepsItToo() {
        // every chunk a summary (openness 0, not a detail leaf): only the kept one
        val closed = FramePlan(tree.leafCount)
        val b = bodies().apply { chosen = star }
        assertEquals(1, update(b, closed))
        assertEquals(star, b.nodes[0])
        // the chunk open, a limit of two: the kept body is one of them, however many are nearer
        val open = FramePlan(tree.leafCount).apply { leafState[3] = 1f; detailLeaves.add(0) }
        val near = OrbitCamera(target = Vec3(tree.cx[0], tree.cy[0], tree.cz[0]), distance = 6f, yaw = 0.6f, pitch = 0.7f)
        val vp = Mat4.multiply(near.projection(aspect), near.view())
        val all = bodies()
        val drawn = update(all, open, maxBodies = tree.nodeCount, vp = vp, cam = near)
        assertTrue("more than one body drawn when open: $drawn", drawn > 1)
        val farthest = (0 until all.count).map { all.nodes[it] }.maxByOrNull { k ->
            vp[3] * tree.positions[3 * k] + vp[7] * tree.positions[3 * k + 1] + vp[11] * tree.positions[3 * k + 2] + vp[15]
        }!!
        assertTrue("the farthest is not among the nearest two", drawn > 2)
        val two = bodies().apply { chosen = farthest }
        assertEquals(2, update(two, open, maxBodies = 2, vp = vp, cam = near))
        assertTrue("the farthest kept in the limit of two", (0 until two.count).any { two.nodes[it] == farthest })
        // the held body before the chosen one
        val held = bodies().apply { chosen = farthest; grabbed = star }
        assertEquals(1, update(held, closed, maxBodies = 1, vp = vp, cam = near))
        assertEquals(star, held.nodes[0])
    }

    @Test fun aMarkedBodyTheFilterHidesIsNotKept() {
        // v13: S05h2 keeps the marked body whole; S05i's filter never draws a node it has hidden, even
        // in the frames before the page lets the chosen one go (MapSelectionEvent.Gone)
        val closed = FramePlan(tree.leafCount)
        val b = bodies().apply { chosen = star; grabbed = star }
        b.filterScale = FloatArray(tree.nodeCount) { 1f }.also { it[star] = FilterPop.HIDE_AT }
        assertEquals(0, update(b, closed))
        assertTrue(!b.isBody(star))
        // on its way back, above the line, it is kept again, at its pop's size
        b.filterScale = FloatArray(tree.nodeCount) { 1f }.also { it[star] = 0.5f }
        assertEquals(1, update(b, closed))
        assertEquals(star, b.nodes[0])
    }

    @Test fun aKeptBodyBehindTheEyeIsNotDrawn() {
        // the camera inside the scene, looking away from the star
        val at = Vec3(tree.positions[3 * star], tree.positions[3 * star + 1], tree.positions[3 * star + 2])
        val away = OrbitCamera(target = at + Vec3(0f, 0f, -0.5f), distance = 0.25f, yaw = 0f, pitch = 0f)
        // eye at target + (0, 0, 0.25): the star is 0.25 behind it along the view
        val vp = Mat4.multiply(away.projection(aspect), away.view())
        val w = vp[3] * at.x + vp[7] * at.y + vp[11] * at.z + vp[15]
        assertTrue("the star is behind the eye: $w", w <= 1e-3f)
        val b = bodies().apply { chosen = star }
        assertEquals(0, update(b, FramePlan(tree.leafCount), vp = vp, cam = away))
    }

    @Test fun aDyingBodyIsDrawnWholeHoweverSmallOnTheScreen() {
        // a rocky idea dying at the star's place, the camera far back: a few pixels across, under a body's size
        val at = Vec3(tree.positions[3 * star], tree.positions[3 * star + 1], tree.positions[3 * star + 2])
        val far = OrbitCamera(target = at, distance = 400f, yaw = 0.6f, pitch = 0.7f)
        val vp = Mat4.multiply(far.projection(aspect), far.view())
        val shader = 0.16f
        val pixels = SpaceLod.pixels(scene.nodeRadius, focal, far.distance)
        assertTrue("the dying body is under a body's size on the screen: $pixels px", pixels < SpaceLod.BODY_PIXELS)
        val departure = MapDeparture("gone", 0, at, shader, shader, shader, DeathKind.ROCKY, 0, SpaceStyle.ROCKY.code, 0f, emptyList())
        val stage = MapDeathStage.make(listOf(departure), lively = true, smooth = false)!!
        val b = bodies().apply { death = stage }
        // nothing live is a body this far; the dying one is drawn, whole, at its place
        assertEquals(0, update(b, FramePlan(tree.leafCount), vp = vp, cam = far))
        assertEquals(1, b.dyingDrawn)
        assertEquals(1, b.count)
        val drawn = assertNotNullOf(b.biggestDyingDrawn())
        assertEquals(at.x, drawn[0], 1e-6f); assertEquals(at.y, drawn[1], 1e-6f); assertEquals(at.z, drawn[2], 1e-6f)
        assertTrue("its size as drawn, a few pixels: ${drawn[3]}", drawn[3] > 0f && drawn[3] < SpaceLod.BODY_PIXELS)
        // once its plan is over it is not drawn
        stage.frame(0.0, shown = true)
        repeat(20) { stage.frame(0.25, shown = true) }
        assertEquals(0, update(b, FramePlan(tree.leafCount), vp = vp, cam = far))
        assertEquals(0, b.dyingDrawn)
        assertNull(b.biggestDyingDrawn())
    }

    private fun <T> assertNotNullOf(value: T?): T { assertNotNull(value); return value!! }
}
