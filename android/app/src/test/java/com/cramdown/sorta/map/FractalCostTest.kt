package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

// S12, 120 fps (the owner's word): about 4 ms of work a frame. The guard on the Fractal map's cost:
// the layout of 100,000 ideas, a frame's step well inside the frame, the dots drawn held to the
// budget however many ideas there are, and no allocation in the frame's step while the camera moves.
class FractalCostTest {
    private fun bigTree(): FractalTree {
        // 200 top folders, each with 4 folders of 125 ideas: 100,000 ideas
        val parents = ArrayList<Int>(); val kinds = ArrayList<Byte>()
        parents.add(-1); kinds.add(FractalTree.HUB)
        for (f in 0 until 200) {
            val top = parents.size; parents.add(0); kinds.add(FractalTree.FOLDER)
            for (s in 0 until 4) {
                val sub = parents.size; parents.add(top); kinds.add(FractalTree.FOLDER)
                repeat(125) { parents.add(sub); kinds.add(FractalTree.IDEA) }
            }
        }
        val n = parents.size
        return FractalTree(parents.toIntArray(), kinds.toByteArray(), Array(n) { "" }, Array(n) { "Idea number $it" }, IntArray(n) { -1 })
    }

    private fun frame(tree: FractalTree): FractalFrame {
        val links = IntArray(4_000) { (it * 7919L % (tree.count - 1)).toInt() + 1 }
        val frame = FractalFrame(FractalLayout(tree), links)
        frame.setView(390.0, 844.0, 120.0, 0.0, 90.0, 0.0)
        for (i in 0 until tree.count) frame.nameWidth[i] = 6.6 * tree.titles[i].length
        return frame
    }

    @Test fun layoutOf100kIdeasIsQuick() {
        val tree = bigTree()
        assertEquals(100_000, (0 until tree.count).count { tree.kind[it] == FractalTree.IDEA })
        FractalLayout(tree)
        val t0 = System.nanoTime()
        FractalLayout(tree)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("layout took $ms ms", ms < 2_000)
    }

    @Test fun aFrameStaysInsideItsBudget() {
        val tree = bigTree()
        val f = frame(tree)
        // the whole map, a top folder, one of its folders, and the zooms between
        val stops = intArrayOf(0, 1, 2, tree.parent[tree.count - 1])
        repeat(300) { k -> f.go(stops[k % stops.size], k * 0.016, instant = false); f.advance(k * 0.016 + 0.3); f.step() }
        val times = DoubleArray(400)
        for (k in 0 until 400) {
            val now = 10.0 + k * 0.004
            if (k % 100 == 0) f.go(stops[(k / 100) % stops.size], now, instant = false)
            f.advance(now)
            val t0 = System.nanoTime()
            f.step()
            times[k] = (System.nanoTime() - t0) / 1e6
            assertTrue("drawn ${f.drawnCount}", f.drawnCount <= FractalRules.BUDGET)
            assertTrue("links ${f.linkCount}", f.linkCount <= FractalRules.LINK_BUDGET)
        }
        times.sort()
        val median = times[times.size / 2]; val p95 = times[(times.size * 95) / 100]
        assertTrue("frame step median $median ms, 95th $p95 ms", median < 2.0 && p95 < 4.0)
    }

    @Test fun aFrameAllocatesNothing() {
        val tree = bigTree()
        val f = frame(tree)
        val stops = intArrayOf(0, 1, 2)
        repeat(200) { k -> f.go(stops[k % 3], k * 0.01, instant = false); f.advance(k * 0.01 + 0.2); f.step(); f.hit(195.0, 470.0) }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        // the glide is set up between frames (a tap); each frame then advances, steps and answers a touch
        f.go(1, 100.0, instant = false)
        val before = bean.getThreadAllocatedBytes(id)
        for (k in 0 until 200) { f.advance(100.0 + k * 0.004); f.step(); f.hit(195.0, 470.0) }
        f.zoom(195.0, 470.0, 1.01); f.pan(2.0, 1.0); f.step()
        val after = bean.getThreadAllocatedBytes(id)
        assertEquals("bytes allocated in 201 frames", 0L, after - before)
    }
}
