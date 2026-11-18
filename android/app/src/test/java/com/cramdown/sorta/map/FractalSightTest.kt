package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.math.PI

// S12, the 3D Fractal's frame (FractalSight): the dots a frame draws never pass the Graphics tier's
// budget (380, 300, 220) however big the tree; a frame looks at and queues a bounded number of nodes
// and makes nothing; All ideas and its hubs show at the whole view, and flying in to Cardiology opens
// its parts and dims the rest; links are drawn once each, between drawn dots; the filter hides.
class FractalSightTest {
    private val fx by lazy { FractalFixture.read() }
    private val sample by lazy { FractalFixture.tree(fx) }

    /** A tree of 100,000 ideas: 200 folders of 4 folders of 125 ideas. */
    private val big: FractalTree by lazy {
        val parent = ArrayList<Int>().apply { add(-1) }
        for (f in 0 until 200) {
            val top = parent.size; parent.add(0)
            for (s in 0 until 4) { val sub = parent.size; parent.add(top); repeat(125) { parent.add(sub) } }
        }
        val n = parent.size
        val kids = IntArray(n); for (i in 1 until n) kids[parent[i]]++
        FractalTree(parent.toIntArray(), ByteArray(n) { if (it == 0) FractalTree.HUB else if (kids[it] > 0) FractalTree.FOLDER else FractalTree.IDEA },
            Array(n) { "" }, Array(n) { "Part $it" }, IntArray(n) { -1 })
    }

    private val width = 1080f
    private val height = 2400f
    private val density = 2.625f

    /** The camera framing node [i]'s cluster ball from [yaw], tilted as the first view is, and its view-projection. */
    private fun camera(l: FractalLayout3D, i: Int, yaw: Float = 0f): Pair<FloatArray, Float> {
        val fov = (50.0 * PI / 180).toFloat()
        val cam = OrbitCamera(yaw = yaw, pitch = (22.0 * PI / 180).toFloat(), fovY = fov)
        // as the renderer's fly-in frames a hub (GlMapRenderer.flyToHub)
        val fit = cam.framing(Vec3(l.centre[3 * i].toFloat(), l.centre[3 * i + 1].toFloat(), l.centre[3 * i + 2].toFloat()),
            l.reach[i].toFloat(), width, height, MapInsets(top = 260f, bottom = 420f), MapFly.FILL)
        cam.target = fit.target; cam.distance = fit.distance
        return Mat4.multiply(cam.projection(width / height), cam.view()) to cam.focalPixels(height)
    }

    private fun sight(t: FractalTree, links: IntArray = IntArray(0), budget: Int = FractalRules.BUDGET): FractalSight =
        FractalSight(FractalModel(t, FractalLayout3D(t), links, budget))

    @Test fun theBudgetFollowsTheGraphicsTier() {
        assertEquals(380, FractalModel.budgetFor(GraphicsTier.HIGH, false))
        assertEquals(300, FractalModel.budgetFor(GraphicsTier.SMOOTH, false))
        assertEquals(220, FractalModel.budgetFor(GraphicsTier.HIGH, true))
        assertEquals(220, FractalModel.budgetFor(GraphicsTier.SMOOTH, true))
    }

    @Test fun aFrameNeverDrawsMoreThanItsBudget() {
        for (budget in listOf(FractalRules.BUDGET, FractalRules.BUDGET_BALANCED, FractalRules.BUDGET_LOW)) {
            val s = sight(big, budget = budget)
            val l = s.model.layout
            for ((what, at) in listOf("whole" to 0, "a folder" to big.children[big.childStart[0]],
                    "a deep folder" to big.children[big.childStart[big.children[big.childStart[0]]]])) {
                for (yaw in listOf(0f, 0.7f, 2.1f)) {
                    val (vp, focal) = camera(l, at, yaw)
                    s.focus = at
                    s.step(vp, focal, width, height, density)
                    assertTrue("$budget, $what: ${s.count} drawn", s.count in 1..budget)
                    assertTrue("$budget, $what: ${s.looked} looked at", s.looked <= FractalSight.LOOK_MOST)
                    assertTrue("$budget, $what: ${s.pushed} queued", s.pushed <= FractalSight.PUSH_MOST)
                    for (k in 0 until s.count) assertTrue(s.x[k] in -200f..width + 200f && s.y[k] in -200f..height + 200f && s.radius[k] > 0f)
                }
            }
        }
        val s = sight(sample, budget = 10_000)
        assertEquals("a budget above the most a frame draws is held to it", FractalRules.BUDGET, s.budget)
    }

    @Test fun theWholeViewShowsAllIdeasAndItsHubs() {
        val s = sight(sample, FractalFixture.links(fx))
        val (vp, focal) = camera(s.model.layout, 0)
        s.step(vp, focal, width, height, density)
        val root = s.slotOf(0)
        assertTrue("All ideas drawn", root >= 0)
        assertTrue("All ideas lit as a hub", s.hub[root] > 0.5f && s.nameable[root])
        for (k in sample.childStart[0] until sample.childStart[1]) {
            val c = sample.children[k]
            if (sample.childCount[c] > 0) assertTrue("${sample.titles[c]} drawn", s.slotOf(c) >= 0)
        }
        val examples = sample.titles.indexOf("Examples")
        assertTrue("Examples may be named", s.nameable[s.slotOf(examples)])
        // a dot's line goes to its own hub's dot
        for (k in 0 until s.count) { val p = s.parentSlot(k); if (p >= 0) assertEquals(sample.parent[s.node[k]], s.node[p]) }
    }

    @Test fun flyingInOpensTheClusterAndDimsTheRest() {
        val s = sight(sample, FractalFixture.links(fx))
        val l = s.model.layout
        val cardiology = sample.titles.indexOf("Cardiology")
        val inside = { (0 until s.count).count { sample.holds(cardiology, s.node[it]) && s.nameable[it] } }
        val (vp0, f0) = camera(l, 0)
        s.step(vp0, f0, width, height, density)
        val before = inside()
        val (vp, focal) = camera(l, cardiology)
        s.focus = cardiology
        s.step(vp, focal, width, height, density)
        val after = inside()
        assertTrue("Cardiology's parts that may be named: $before whole, $after flown in", after > before && after >= 8)
        for (k in 0 until s.count) if (!sample.holds(cardiology, s.node[k]))
            assertTrue("${sample.titles[s.node[k]]} outside dims", s.alpha[k] <= FractalRules.DIM.toFloat() + 1e-6f)
        // the chosen dot is always full
        val heart = sample.titles.indexOf("Heart failure")
        s.chosen = heart
        s.step(vp, focal, width, height, density)
        assertTrue(s.alpha[s.slotOf(heart)] >= 1f)
    }

    @Test fun linksAreDrawnOnceBetweenDrawnDots() {
        val links = FractalFixture.links(fx)
        val s = sight(sample, links)
        val (vp, focal) = camera(s.model.layout, 0)
        s.step(vp, focal, width, height, density)
        assertTrue("links drawn (${s.linkCount})", s.linkCount > 0 && s.linkCount <= FractalRules.LINK_BUDGET)
        val seen = HashSet<Long>()
        for (e in 0 until s.linkCount) {
            val a = s.node[s.linkFrom[e]]; val b = s.node[s.linkTo[e]]
            assertTrue(s.linkFrom[e] in 0 until s.count && s.linkTo[e] in 0 until s.count)
            assertTrue("$a-$b once", seen.add(minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()))
        }
        // Show links: its own stand out, the rest fade
        val a0 = s.node[s.linkFrom[0]]
        s.linksOf = a0
        s.step(vp, focal, width, height, density)
        for (e in 0 until s.linkCount) {
            val mine = s.node[s.linkFrom[e]] == a0 || s.node[s.linkTo[e]] == a0
            val plain = minOf(s.alpha[s.linkFrom[e]], s.alpha[s.linkTo[e]]) * (FractalRules.LINE * FractalRules.LINK_LINE).toFloat()
            assertEquals(plain * (if (mine) FractalRules.LINKS_ON else FractalRules.LINKS_OFF).toFloat(), s.linkAlpha[e], 1e-6f)
        }
    }

    @Test fun theFilterHidesWhatItLeavesOut() {
        val s = sight(sample)
        val (vp, focal) = camera(s.model.layout, 0)
        val examples = sample.titles.indexOf("Examples")
        s.shown = BooleanArray(sample.count) { it != examples }
        s.step(vp, focal, width, height, density)
        assertEquals("Examples hidden", -1, s.slotOf(examples))
        for (k in 0 until s.count) assertTrue(!sample.holds(examples, s.node[k]))
    }

    @Test fun aFrameOfAHundredThousandIdeasIsCheapAndMakesNothing() {
        val s = sight(big)
        val l = s.model.layout
        val cams = listOf(camera(l, 0), camera(l, 0, 1.2f), camera(l, big.children[big.childStart[0]], 0.4f))
        repeat(600) { val (vp, f) = cams[it % cams.size]; s.step(vp, f, width, height, density) }
        val times = DoubleArray(300)
        for (k in times.indices) {
            val (vp, f) = cams[k % cams.size]
            val start = System.nanoTime()
            s.step(vp, f, width, height, density)
            times[k] = (System.nanoTime() - start) / 1e6
        }
        times.sort()
        assertTrue("a frame's work: median ${times[150]} ms", times[150] < 1.0)
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val (vp0, f0) = cams[0]; val (vp1, f1) = cams[1]
        val before = bean.getThreadAllocatedBytes(id)
        for (k in 0 until 200) if (k % 2 == 0) s.step(vp0, f0, width, height, density) else s.step(vp1, f1, width, height, density)
        val after = bean.getThreadAllocatedBytes(id)
        assertTrue("200 frames allocated ${after - before} bytes", after - before < 16_000)
    }
}
