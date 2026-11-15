package com.cramdown.sorta.ui.map

import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.FilterLinkGrowth
import com.cramdown.sorta.map.FilterPop
import com.cramdown.sorta.map.FilterSlice
import com.cramdown.sorta.map.FrameBudget
import com.cramdown.sorta.map.FramePlan
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.GuardBand
import com.cramdown.sorta.map.LodSelector
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.SpaceBodies
import com.cramdown.sorta.map.SpaceDepth
import com.cramdown.sorta.map.SpaceLod
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.universeInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * With no filter (Everything) the map chooses and draws as it did before S05i (S05i repair 3):
 * the tablet's MapRenderTest drew no bodies on a copy without S05h repair 3, and this pins that
 * the filter's inputs add nothing to the level of detail or to the bodies when no filter is set.
 */
class SpaceFilterOffTest {
    private class Tablet {
        val book = SampleIdeas.book()
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        val scene = MapScene.universe(plan)
        val tree = scene.tree
        val width = 2560; val height = 1600
        // MapPreview's first view on the tablet, as SpaceBodiesOwnSizeTest frames it
        val camera = OrbitCamera(target = Vec3(tree.cx[0], tree.cy[0], tree.cz[0]), pitch = 0.7f).apply {
            distance = max(fitDistance(scene.viewRadius, aspect = 0.75f), 4f)
        }.also { HomeView(it, scene.viewRadius).fit(it, width, height, MapInsets(top = 250f, bottom = 662f)) }
        val aspect = width.toFloat() / height
        val focal = camera.focalPixels(height.toFloat())
        val vp = Mat4.multiply(camera.projection(aspect), camera.view())

        fun bodies() = SpaceBodies(tree, scene.nodeRadius).apply {
            homes = tree.positions.copyOf()
            radii = FloatArray(tree.nodeCount) { k -> UniverseBeams.shaderRadius(plan.bodies[tree.order[k]]) }
            reaches = FloatArray(tree.nodeCount) { k -> UniverseBeams.reach(plan.bodies[tree.order[k]]) }
        }

        /** The level of detail after [frames] still frames at the budget's first detail, the filter's slice [slice]. */
        fun select(slice: FilterSlice?, frames: Int = 90): FramePlan {
            val lod = LodSelector(tree, null).apply { filter = slice }
            val budget = FrameBudget(60f)
            var out: FramePlan? = null
            for (f in 1..frames) {
                val guard = GuardBand.predict(camera, aspect, Vec3.ZERO, 0.35f)
                out = lod.select(camera.eye(), focal, guard.view, guard, budget.openPixels, budget.maxDetailNodes, 1f / 60, f)
            }
            return out!!
        }
    }

    @Test fun everythingChoosesTheSameDetailAndBodiesAsNoFilterAtAll() {
        val t = Tablet()
        val n = t.tree.nodeCount
        // the level of detail: with no slice, and with a slice that shows every node
        val none = t.select(null)
        val none2 = t.select(null)
        val all = t.select(FilterSlice.of(t.tree, BooleanArray(n) { true }))
        assertEquals("the same chunks open, frame for frame", none.detailLeaves.toArray().toList(), all.detailLeaves.toArray().toList())
        assertArrayEquals("each chunk as far open", none.leafState, all.leafState, 0f)
        assertEquals(none.summaryCount, all.summaryCount)
        assertEquals(none.bundleCount, all.bundleCount)
        assertArrayEquals(none.summaries.data.copyOf(none.summaries.size), all.summaries.data.copyOf(all.summaries.size), 0f)
        assertEquals("a second run with no filter is the same", none.detailLeaves.toArray().toList(), none2.detailLeaves.toArray().toList())

        // the bodies, on every chunk open: the filter's fields unset, and set to show everything
        val open = FramePlan(t.tree.leafCount).also { p -> for (leaf in 0 until t.tree.leafCount) { p.leafState[leaf * 4 + 3] = 1f; p.detailLeaves.add(leaf) } }
        val eye = t.camera.eye()
        val focusDepth = SpaceDepth.depth(t.camera.target, eye, t.camera.forward())
        val band = SpaceDepth.band(focusDepth, SpaceDepth.reach(focusDepth, t.scene.viewRadius), GraphicsTier.HIGH)
        val plain = t.bodies()
        val drawn = plain.update(open, t.vp, eye, t.camera.right(), t.camera.up(), t.focal, SpaceLod.maxBodies(1f), 0f, band)
        assertTrue("the tablet's first view draws bodies with no filter: $drawn", drawn > 0)
        val everything = t.bodies().apply {
            filterScale = FloatArray(n) { 1f }
            filterBloom = FloatArray(n)
            filterGhost = BooleanArray(n)
            filterWanted = BooleanArray(n) { true }
            filterLinks = FilterLinkGrowth(lively = true)
        }
        val drawnAll = everything.update(open, t.vp, eye, t.camera.right(), t.camera.up(), t.focal, SpaceLod.maxBodies(1f), 0f, band)
        assertEquals(drawn, drawnAll)
        assertArrayEquals("the same bodies", plain.nodes.copyOf(drawn), everything.nodes.copyOf(drawnAll))
        assertArrayEquals("each drawn the same, its bloom 0", plain.data, everything.data, 0f)
        assertEquals(plain.beamCount, everything.beamCount)
        assertArrayEquals("the same beams", plain.beams, everything.beams, 0f)
    }

    @Test fun noFilterMeansNoSpringOrLinkWorkAFrame() {
        for (lively in listOf(true, false)) {
            val pop = FilterPop(35, lively)
            pop.apply(null)
            assertFalse("no spring moves with no filter (lively $lively)", pop.moving)
            assertFalse("a frame steps nothing", pop.step(1f / 60))
            for (i in 0 until 35) {
                assertEquals(1f, pop.scale(i), 0f)
                assertEquals(0f, pop.bloom(i), 0f)
                assertFalse(pop.gone(i))
            }
            val links = FilterLinkGrowth(lively)
            assertTrue(links.idle)
            assertFalse("no link keeps the map drawing (lively $lively)", links.step(1f / 60) { true })
            assertEquals("a wanted link is whole, and not kept", 1f, links.level(7L, true), 0f)
            assertTrue(links.idle)
        }
    }
}
