package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Show links and the chosen body's links (S05g), as iOS's focusLinks: the neighbourhood (the body,
// those it links to, an idea's or folder's members and the folders above it), Space's beams lit for
// the chosen body and, under Show links, only its own built while every body outside fades to 0.15,
// and the far summaries holding none of the neighbourhood dimmed the same.
class MapLinkFocusTest {

    // ---- the neighbourhood ----

    @Test fun inSpaceItIsTheBodyAndThoseItLinksTo() {
        val links = intArrayOf(0, 1, 2, 0, 3, 4, 1, 3)
        assertArrayEquals(intArrayOf(0, 1, 2), MapFocus.neighbourhood(0, 5, links))
        assertArrayEquals(intArrayOf(1, 3), MapFocus.neighbourhood(3, 5, intArrayOf(1, 3)))
        assertArrayEquals("one with no links: itself", intArrayOf(4), MapFocus.neighbourhood(4, 5, intArrayOf(0, 1)))
        assertArrayEquals("nothing chosen: none", IntArray(0), MapFocus.neighbourhood(-1, 5, links))
        assertArrayEquals("a body no longer there: none", IntArray(0), MapFocus.neighbourhood(5, 5, links))
        assertArrayEquals("a link to a body not there is passed over", intArrayOf(0), MapFocus.neighbourhood(0, 2, intArrayOf(0, 7)))
    }

    @Test fun inTheNeuronsAFolderBringsItsIdeasAndTheFoldersAboveIt() {
        // 0 a cell, 1 a part in it, 2 and 3 ideas in the part, 4 an idea in the cell, 5 another cell, 6 an idea in it
        val parents = intArrayOf(-1, 0, 1, 1, 0, -1, 5)
        val notes = booleanArrayOf(false, false, true, true, true, false, true)
        val links = intArrayOf(2, 6)
        assertArrayEquals("the part: its ideas and its cell", intArrayOf(0, 1, 2, 3), MapFocus.neighbourhood(1, 7, links, parents, notes))
        assertArrayEquals("the cell: every idea under it, not its parts", intArrayOf(0, 2, 3, 4), MapFocus.neighbourhood(0, 7, links, parents, notes))
        assertArrayEquals("an idea: its link, its part and cell", intArrayOf(0, 1, 2, 6), MapFocus.neighbourhood(2, 7, links, parents, notes))
        assertArrayEquals("an idea is no one's folder", intArrayOf(0, 4), MapFocus.neighbourhood(4, 7, links, parents, notes))
        // a loop in the parents ends
        val looped = intArrayOf(1, 0)
        assertArrayEquals(intArrayOf(0, 1), MapFocus.neighbourhood(0, 2, IntArray(0), looped, booleanArrayOf(true, true)))
    }

    @Test fun ownLinksAndCountsInARange() {
        assertTrue(MapFocus.own(3, 3, 9)); assertTrue(MapFocus.own(3, 9, 3))
        assertFalse(MapFocus.own(3, 4, 9)); assertFalse("nothing chosen", MapFocus.own(-1, -1, 2))
        val near = intArrayOf(2, 5, 6, 11)
        assertEquals(2, MapFocus.countIn(near, 5, 11))
        assertEquals(4, MapFocus.countIn(near, 0, 100))
        assertEquals(0, MapFocus.countIn(near, 7, 11))
        assertEquals(0, MapFocus.countIn(near, 6, 6))
        assertEquals(0, MapFocus.countIn(IntArray(0), 0, 9))
        assertTrue(MapFocus.holdsAny(near, 6, 7)); assertTrue(MapFocus.holdsAny(near, 11, 12))
        assertFalse(MapFocus.holdsAny(near, 7, 11)); assertFalse(MapFocus.holdsAny(near, 12, 40)); assertFalse(MapFocus.holdsAny(IntArray(0), 0, 9))
    }

    // ---- Space's bodies and beams ----

    /** Notes in a row along x: 0 links to 1, 2 and 3; 4 to 5; 6 alone. All near enough to be bodies. */
    private fun space(): Octree {
        val n = 7
        val positions = FloatArray(n * 3)
        for (i in 0 until n) positions[3 * i] = i * 2f
        val edges = intArrayOf(0, 1, 0, 2, 0, 3, 4, 5)
        return Octree.build(MapGraph(positions, SpaceStyle.assign(n, edges), edges), leafCapacity = 4, palette = SpacePalette.rgb)
    }

    private fun frame(tree: Octree, bodies: SpaceBodies): Int {
        val plan = FramePlan(tree.leafCount)
        for (leaf in 0 until tree.leafCount) { plan.leafState[leaf * 4 + 3] = 1f; plan.detailLeaves.add(leaf) }
        val camera = OrbitCamera(target = Vec3(6f, 0f, 0f), distance = 14f, yaw = 0f, pitch = 0f)
        val vp = Mat4.multiply(camera.projection(1f), camera.view())
        return bodies.update(plan, vp, camera.eye(), camera.right(), camera.up(), camera.focalPixels(1000f), 64, 0f)
    }

    private fun chunkOf(tree: Octree, scene: Int) = (0 until tree.nodeCount).first { tree.order[it] == scene }

    @Test fun theChosenBodysBeamsAreLitAndItsRadiusPops() {
        val tree = space()
        val bodies = SpaceBodies(tree, nodeRadius = 0.5f)
        assertEquals("every note is a body", 7, frame(tree, bodies))
        assertEquals(4, bodies.beamCount)
        assertEquals("nothing chosen: nothing lit", 0, bodies.litCount)
        assertEquals(0, bodies.fadedCount)
        val hub = chunkOf(tree, 0)
        bodies.chosen = hub
        bodies.chosenScale = 1.12f
        frame(tree, bodies)
        assertEquals(4, bodies.beamCount)
        assertEquals("its three links lit, the other not", 3, bodies.litCount)
        assertEquals(3, (0 until bodies.beamCount).count { bodies.beams[it * SpaceBodies.BEAM_STRIDE + 9] == 1f })
        for (j in 0 until bodies.count) {
            val o = j * SpaceBodies.STRIDE
            val chosen = bodies.nodes[j] == hub
            assertEquals("its radius by the pop", if (chosen) 0.56f else 0.5f, bodies.data[o + 3], 1e-6f)
            assertEquals("the mark says which", if (chosen) 1f else 0f, bodies.data[o + 17], 0f)
            assertEquals("no Show links: none faded", 1f, bodies.data[o + 16], 0f)
            assertEquals(0f, bodies.data[o + 18], 0f); assertEquals(0f, bodies.data[o + 19], 0f)
        }
    }

    @Test fun underShowLinksOnlyItsOwnBeamsAreBuiltAndTheRestFades() {
        val tree = space()
        val bodies = SpaceBodies(tree, nodeRadius = 0.5f)
        val hub = chunkOf(tree, 0)
        val near = intArrayOf(0, 1, 2, 3).map { chunkOf(tree, it) }.sorted().toIntArray()
        bodies.chosen = hub
        bodies.focus = near
        assertEquals(7, frame(tree, bodies))
        assertEquals("only the hub's three", 3, bodies.beamCount)
        assertEquals(3, bodies.litCount)
        assertEquals("4, 5 and 6 fade", 3, bodies.fadedCount)
        for (j in 0 until bodies.count) {
            val scene = tree.order[bodies.nodes[j]]
            assertEquals("scene node $scene", if (scene <= 3) 1f else MapMark.FADED, bodies.data[j * SpaceBodies.STRIDE + 16], 0f)
            assertEquals(if (scene <= 3) 1f else MapMark.FADED, bodies.fadeOf(bodies.nodes[j]), 0f)
        }
        // All links: everything back
        bodies.focus = null
        frame(tree, bodies)
        assertEquals(4, bodies.beamCount)
        assertEquals(0, bodies.fadedCount)
        assertEquals(1f, bodies.fadeOf(chunkOf(tree, 6)), 0f)
    }

    // ---- the far summaries ----

    private val graph = SyntheticGraph.make(nodes = 20_000)
    private val big = Octree.build(graph, leafCapacity = 512)

    private fun summaries(focus: IntArray?): FramePlan {
        val lod = LodSelector(big, null)
        lod.focus = focus
        val camera = OrbitCamera(target = Vec3(big.cx[0], big.cy[0], big.cz[0]), distance = big.radius[0] * 3f, yaw = 0.3f, pitch = 0.5f)
        var plan: FramePlan? = null
        for (f in 1..30) {
            val guard = GuardBand.predict(camera, 1f, Vec3.ZERO, 0.5f)
            plan = lod.select(camera.eye(), camera.focalPixels(1000f), guard.view, guard, 36f, 10_000_000, 1f / 60, f)
        }
        return plan!!
    }

    @Test fun summariesHoldingNoneOfItAreDimmedTo015() {
        val whole = summaries(null)
        assertTrue("several summaries: ${whole.summaryCount}", whole.summaryCount > 1)
        assertEquals(0, whole.fadedSummaries)
        val focused = summaries(intArrayOf(0))
        assertEquals(whole.summaryCount, focused.summaryCount)
        // only the summaries of cells holding node 0 stay whole: at most one a level (a cell
        // opening shows its own summary beside its children's), and at least one is drawn
        val holding = (0 until big.cellCount).count { big.start[it] <= 0 && 0 < big.end[it] }
        val whole0 = focused.summaryCount - focused.fadedSummaries
        assertTrue("the summaries holding it stay whole: $whole0 of ${focused.summaryCount}, $holding cells hold it",
            whole0 in 1..holding && focused.fadedSummaries > 0)
        var dimmed = 0
        for (s in 0 until whole.summaryCount) {
            val o = s * FramePlan.SUMMARY_STRIDE
            for (k in 0..3) assertEquals("place and size stay", whole.summaries.data[o + k], focused.summaries.data[o + k], 0f)
            // judged on its brightest channel (a vivid summary can have one at 0)
            val k0 = (4..6).maxBy { whole.summaries.data[o + it] }
            val top = whole.summaries.data[o + k0]
            val faded = top > 1e-4f && abs(focused.summaries.data[o + k0] - top * MapMark.FADED) < 1e-5f
            if (faded) {
                dimmed++
                for (k in 4..6) assertEquals(whole.summaries.data[o + k] * MapMark.FADED, focused.summaries.data[o + k], 1e-5f)
            } else for (k in 4..6) assertEquals(whole.summaries.data[o + k], focused.summaries.data[o + k], 0f)
        }
        assertEquals("each faded summary is 0.15 as bright", focused.fadedSummaries, dimmed)
    }

    private fun abs(v: Float) = kotlin.math.abs(v)

    // S05j 7a: Performance's names under Show links, as iOS's GraphPerfLOD.nameAlpha (question 3's default,
    // on both): a name outside the chosen point's own links at 0.15, its own and its neighbours' whole
    @Test fun underShowLinksTheNamesOutsideTheChosenPointsLinksDim() {
        val links = intArrayOf(0, 1, 2, 0, 3, 4, 1, 3)
        val near = MapFocus.neighbourhood(0, 5, links)
        assertEquals("its own", 1f, MapMark.nameAlpha(0, near), 0f)
        assertEquals("a neighbour", 1f, MapMark.nameAlpha(1, near), 0f)
        assertEquals("a neighbour", 1f, MapMark.nameAlpha(2, near), 0f)
        assertEquals("outside its links", 0.15f, MapMark.nameAlpha(3, near), 0f)
        assertEquals("outside its links", MapMark.FADED, MapMark.nameAlpha(4, near), 0f)
        for (i in 0 until 5) assertEquals("no focus: whole", 1f, MapMark.nameAlpha(i, null), 0f)
    }
}
