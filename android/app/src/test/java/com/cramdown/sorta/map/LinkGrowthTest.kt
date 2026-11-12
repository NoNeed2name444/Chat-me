package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05e: links grow in and shrink away as iOS's (LinkGrowth, GraphSim's stepLinks): the 0.25 s wait,
 * 0.7 s to grow, 0.45 s to draw back, 0.6 s for a gone link, eased by a smoothstep; and on Space's
 * octree (SpaceLinkGrowth) from the first frame both ends' chunks are open.
 */
class LinkGrowthTest {
    private val dt = 1f / 60f
    /** At 60 frames a second: the wait 15 frames, growing 42, drawing back 27, a gone link's shrinking 36. */
    private val wait = 15; private val grow = 42; private val back = 27; private val fade = 36

    /** Ticks [i] for [frames] frames, ready throughout. */
    private fun LinkGrowth.run(i: Int, frames: Int) {
        repeat(frames) { tick(i, true, dt) }
    }

    @Test fun aFreshLinkWaitsAQuarterSecondFromItsFirstReadyFrameThenGrowsIn() {
        val growth = LinkGrowth(booleanArrayOf(true))
        // its ends are not there yet: nothing moves
        repeat(120) { growth.tick(0, false, dt) }
        assertEquals(0f, growth.grow[0])
        assertEquals(0f, growth.shown(0))
        // the first frame both ends are there starts the wait
        growth.tick(0, true, dt)
        growth.run(0, wait - 2)
        assertEquals("still waiting", 0f, growth.grow[0])
        growth.run(0, 2)
        growth.run(0, grow / 2)
        assertTrue("half grown: ${growth.grow[0]}", growth.grow[0] in 0.4f..0.55f)
        assertEquals("shown eased", LinkGrowth.ease(growth.grow[0]), growth.shown(0), 1e-6f)
        growth.run(0, grow / 2 - 4)
        assertTrue("not whole before 0.7 s: ${growth.grow[0]}", growth.grow[0] < 1f)
        growth.run(0, 6)
        assertEquals("whole 0.7 s after the wait", 1f, growth.grow[0])
        assertEquals(1f, growth.shown(0))
        assertTrue(growth.settled(0))
    }

    @Test fun aLinkDrawsBackInAboutHalfASecondAndAGoneLinkShrinksAwayInPointSix() {
        val growth = LinkGrowth(booleanArrayOf(false), ghostEnds = intArrayOf(0, 1))
        assertEquals("a link not new to the scene is whole", 1f, growth.shown(0))
        repeat(back - 2) { growth.step(0, false, dt) }
        assertTrue("drawing back: ${growth.grow[0]}", growth.grow[0] > 0f)
        repeat(3) { growth.step(0, false, dt) }
        assertEquals("drawn back in 0.45 s", 0f, growth.grow[0])
        // the gone link, index links + 0
        val ghost = growth.links
        assertEquals(1, growth.ghosts)
        assertEquals("a gone link starts whole", 1f, growth.shown(ghost))
        growth.tick(ghost, true, dt)
        growth.run(ghost, fade - 2)
        assertTrue("shrinking: ${growth.grow[ghost]}", growth.grow[ghost] > 0f)
        growth.run(ghost, 3)
        assertEquals("gone in 0.6 s", 0f, growth.shown(ghost))
        assertTrue(growth.settled(ghost))
    }

    @Test fun motionOffDrawsEveryLinkWholeAtOnce() {
        val growth = LinkGrowth(booleanArrayOf(true, true), ghostEnds = intArrayOf(0, 1), lively = false)
        assertEquals(1f, growth.shown(0))
        assertEquals(1f, growth.shown(1))
        assertEquals("no link shrinks away", 0, growth.ghosts)
        growth.tick(0, true, dt)
        assertEquals(1f, growth.shown(0))
    }

    @Test fun aLinkStartedLateCatchesUp() {
        val growth = LinkGrowth(booleanArrayOf(true, true, true))
        growth.start(0, 0f)
        assertEquals(0f, growth.shown(0))
        growth.start(1, LinkGrowth.WAIT + LinkGrowth.GROW / 2)
        assertEquals(0.5f, growth.grow[1], 1e-5f)
        growth.start(2, 10f)
        assertEquals("whole long since", 1f, growth.shown(2))
    }

    // ---- on Space's octree ----

    /** Four notes in two linked pairs, one chunk each pair. */
    private fun tree(): Pair<Octree, IntArray> {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 50f, 0f, 0f, 51f, 0f, 0f)
        val edges = intArrayOf(0, 1, 2, 3)
        val graph = MapGraph(positions, SpaceStyle.assign(4, edges), edges)
        return Octree.build(graph, leafCapacity = 2, palette = SpacePalette.rgb) to edges
    }

    @Test fun spaceGrowsALinkFromWhenItsChunksOpenAndNeverAgain() {
        val (tree, edges) = tree()
        val chunk = IntArray(tree.nodeCount).also { for (k in it.indices) it[tree.order[k]] = k }
        val space = SpaceLinkGrowth(tree, edges, LinkGrowth(booleanArrayOf(true, true)))
        fun frame(open: Boolean) {
            space.tick(dt)
            if (open) for (leaf in 0 until tree.leafCount) space.leafOpen(leaf)
        }
        // the chunks open on the first frame; the first pair's link is drawn from then
        frame(true)
        assertEquals(0f, space.shown(chunk[0], chunk[1]))
        repeat(wait - 2) { frame(true); assertEquals("waiting, frame $it", 0f, space.shown(chunk[0], chunk[1])) }
        repeat(2 + grow - 2) { frame(true) }
        assertTrue("not whole before 0.95 s", space.shown(chunk[0], chunk[1]) < 1f)
        repeat(4) { frame(true) }
        assertEquals("whole 0.95 s after its chunks opened", 1f, space.shown(chunk[0], chunk[1]))
        // the second pair's link, first drawn seconds later (the camera came to it): whole at once
        repeat(120) { frame(true) }
        assertEquals(1f, space.shown(chunk[3], chunk[2]))
        // a link with no growth in the scene is drawn whole
        assertEquals(1f, space.shown(chunk[0], chunk[3]))
    }
}
