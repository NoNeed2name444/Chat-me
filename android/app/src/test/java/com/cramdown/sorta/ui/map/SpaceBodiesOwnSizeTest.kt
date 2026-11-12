package com.cramdown.sorta.ui.map

import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.FramePlan
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.SpaceBodies
import com.cramdown.sorta.map.SpaceBodyLevel
import com.cramdown.sorta.map.SpaceDepth
import com.cramdown.sorta.map.SpaceLod
import com.cramdown.sorta.map.SpacePalette
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.universeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * Space chooses body or point by each body's own radius, as iOS's GraphSpaceDepthStage.update
 * (`size = radius[i]`), not by the scene's median node radius (S05h A3, the tablet's MapRenderTest).
 */
class SpaceBodiesOwnSizeTest {
    private fun openPlan(tree: Octree): FramePlan {
        val plan = FramePlan(tree.leafCount)
        for (leaf in 0 until tree.leafCount) { plan.leafState[leaf * 4 + 3] = 1f; plan.detailLeaves.add(leaf) }
        return plan
    }

    @Test fun thePreviewUniverseOnTheTabletDrawsBodies() {
        // MapPreview's map: the sample ideas' Universe, at its first view on the 2560 x 1600 tablet,
        // framed in what the top strip and the first-run card leave: as the red run had it (the
        // median radius's largest body 6.5 px), and at the window's floor, a third of the view
        val book = SampleIdeas.book()
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        val scene = MapScene.universe(plan)
        val tree = scene.tree
        val width = 2560; val height = 1600
        val radii = FloatArray(tree.nodeCount) { k -> UniverseBeams.shaderRadius(plan.bodies[tree.order[k]]) }
        for (insets in listOf(MapInsets(top = 250f, bottom = 662f), MapInsets(top = 250f, bottom = 1600f))) {
            val camera = OrbitCamera(target = Vec3(tree.cx[0], tree.cy[0], tree.cz[0]), pitch = 0.7f).apply {
                distance = max(fitDistance(scene.viewRadius, aspect = 0.75f), 4f)
            }
            HomeView(camera, scene.viewRadius).fit(camera, width, height, insets)
            val focal = camera.focalPixels(height.toFloat())
            assertEquals("the tablet's focal length", 1715f, focal, 1f)
            val bodies = SpaceBodies(tree, scene.nodeRadius).apply {
                homes = tree.positions.copyOf()
                this.radii = radii
                reaches = FloatArray(tree.nodeCount) { k -> UniverseBeams.reach(plan.bodies[tree.order[k]]) }
            }
            val eye = camera.eye()
            val vp = Mat4.multiply(camera.projection(width.toFloat() / height), camera.view())
            val focusDepth = SpaceDepth.depth(camera.target, eye, camera.forward())
            val band = SpaceDepth.band(focusDepth, SpaceDepth.reach(focusDepth, scene.viewRadius), GraphicsTier.HIGH)
            fun depth(i: Int) = vp[3] * tree.positions[3 * i] + vp[7] * tree.positions[3 * i + 1] + vp[11] * tree.positions[3 * i + 2] + vp[15]
            val nodes = (0 until tree.nodeCount).filter { depth(it) > 1e-3f && !band.gone(depth(it)) }
            assertTrue("bodies in front of the camera under $insets", nodes.isNotEmpty())
            // the median radius leaves every one of them a point here: the red run's bodies = 0
            val largestByMedian = nodes.maxOf { SpaceLod.pixels(scene.nodeRadius, focal, depth(it)) }
            assertTrue("by the median radius the largest is $largestByMedian px under $insets", largestByMedian < SpaceLod.BODY_PIXELS)
            val count = bodies.update(openPlan(tree), vp, eye, camera.right(), camera.up(), focal, SpaceLod.maxBodies(1f), 0f, band)
            assertTrue("the tablet's first view under $insets draws full bodies: $count", count > 0)
            for (j in 0 until count) {
                val node = bodies.nodes[j]
                assertEquals("body $node is chosen by its own radius ${radii[node]}", SpaceBodyLevel.BODY,
                    bodies.level(false, SpaceLod.pixels(radii[node], focal, depth(node)), band.fade(depth(node))))
            }
        }
    }

    @Test fun eachBodyIsChosenByItsOwnRadiusAndAFarChunkWithABigBodyIsLookedAt() {
        // one note a chunk: a big body far off, a small one near; the median radius between them
        val mine = floatArrayOf(1.0f, 0.05f, 0.2f)
        val positions = floatArrayOf(-3f, 0f, 0f, 0f, 0f, 60f, 3f, 0f, -40f)
        val graph = MapGraph(positions, ByteArray(3) { SpaceStyle.ROCKY.code.toByte() }, IntArray(0))
        val tree = Octree.build(graph, leafCapacity = 1, palette = SpacePalette.rgb)
        val chunk = IntArray(3).also { c -> for (k in 0 until tree.nodeCount) c[tree.order[k]] = k }
        val bodies = SpaceBodies(tree, nodeRadius = 0.2f).apply { radii = FloatArray(tree.nodeCount) { k -> mine[tree.order[k]] } }
        val camera = OrbitCamera(target = Vec3.ZERO, distance = 100f, yaw = 0f, pitch = 0f)
        val vp = Mat4.multiply(camera.projection(1f), camera.view())
        val focal = camera.focalPixels(1000f)
        // the big body (depth 100) is a point by the median radius, a body by its own; the small one (depth 40) the other way round
        assertTrue(SpaceLod.pixels(0.2f, focal, 100f) < SpaceLod.BODY_PIXELS * SpaceBodies.KEEP)
        assertTrue(SpaceLod.pixels(1.0f, focal, 100f) > SpaceLod.BODY_PIXELS)
        assertTrue(SpaceLod.pixels(0.2f, focal, 40f) > SpaceLod.BODY_PIXELS)
        assertTrue(SpaceLod.pixels(0.05f, focal, 40f) < SpaceLod.BODY_PIXELS)
        bodies.update(openPlan(tree), vp, camera.eye(), camera.right(), camera.up(), focal, 64, 0f)
        assertTrue("the big far body is a body", bodies.isBody(chunk[0]))
        assertFalse("the small near body stays a point", bodies.isBody(chunk[1]))
        assertFalse("the median-sized body at depth 140 stays a point", bodies.isBody(chunk[2]))
        assertEquals(1, bodies.count)
    }
}
