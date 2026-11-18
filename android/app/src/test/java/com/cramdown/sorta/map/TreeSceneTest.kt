package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max

// S11: the Tree's frame on the concept's sample (tree-fixture.json) in a phone's and a tablet's
// view: names never overlap and keep 8 apart side by side and 4 above and below, each bough's name
// sits on its own bough, a tap zooms until the part is the whole tree and Back, Reduce Motion and
// settling after a pinch work; and the guard on cost: at most a budget of parts, about 4 ms of work
// a frame with room to spare, and nothing allocated per frame.
class TreeSceneTest {
    private val sample: NestNodes by lazy {
        val t = JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
            .getJSONArray("trees").getJSONObject(0)
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title"); val folder = t.getJSONArray("folder")
        val n = parent.length()
        // tones as the store gives them: each top-level folder's place, everything under it the same
        val tone = IntArray(n) { -1 }
        var tops = 0
        for (i in 1 until n) tone[i] = if (parent.getInt(i) == 0) (if (folder.getInt(i) == 1) tops++ else -1) else tone[parent.getInt(i)]
        NestNodes(Array(n) { "$it" }, IntArray(n) { parent.getInt(it) }, Array(n) { title.getString(it) },
            BooleanArray(n) { folder.getInt(it) == 1 }, tone)
    }

    /** A sink that only counts, allocating nothing. */
    private class Count : NestSink {
        var points = 0; var fills = 0; var strokes = 0; var texts = 0
        override fun moveTo(x: Float, y: Float) { points++ }
        override fun lineTo(x: Float, y: Float) { points++ }
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) { points++ }
        override fun close() {}
        override fun fill(color: Int, alpha: Float) { fills++ }
        override fun stroke(color: Int, width: Float, alpha: Float, round: Boolean) { strokes++ }
        override fun text(node: Int, x: Float, y: Float, size: Float, bold: Boolean, align: Int, ink: Int, halo: Int, haloWidth: Float) { texts++ }
    }

    private fun scene(nodes: NestNodes, w: Float = 390f, h: Float = 844f, links: NestLinks = NestLinks.NONE): TreeScene {
        val s = TreeScene(nodes, TreeLayout(nodes), links)
        // Nunito's bold is about 0.6 of its size a letter
        for (i in 0 until nodes.size) { s.widths[0][i] = nodes.titles[i].length * 58f; s.widths[1][i] = nodes.titles[i].length * 62f }
        s.target = 48f
        s.setView(w, h, top = 120f, bottom = 90f)
        return s
    }

    private fun boxes(s: TreeScene): List<Pair<Int, FloatArray>> =
        (0 until s.labelCount).filter { s.labelNode(it) >= 0 }.map { s.labelNode(it) to FloatArray(4).also { b -> s.labelBox(it, b) } }

    private fun checkNames(s: TreeScene, what: String) {
        val b = boxes(s)
        assertTrue("$what: some names", b.isNotEmpty())
        for (p in b.indices) for (q in p + 1 until b.size) {
            val (_, a) = b[p]; val (_, c) = b[q]
            val apartX = max(c[0] - a[2], a[0] - c[2]); val apartY = max(c[1] - a[3], a[1] - c[3])
            assertTrue("$what: ${s.nodes.titles[b[p].first]} and ${s.nodes.titles[b[q].first]} are ${apartX} and ${apartY} apart",
                apartX >= TreeScene.GAP_X - 1e-3f || apartY >= TreeScene.GAP_Y - 1e-3f)
        }
        for ((_, box) in b) assertTrue("$what: inside the view, below the strip", box[0] >= 4 && box[2] <= s.viewW - 4 && box[1] >= s.insetTop + 2 && box[3] <= s.viewH - s.insetBottom - 4)
        // each name once
        assertEquals("$what: each part named once", b.size, b.map { it.first }.toSet().size)
    }

    private fun settleZoom(s: TreeScene, start: Double): Double {
        var t = start
        while (s.animating) { t += 8.0; s.draw(null, t) }
        return t
    }

    @Test fun namesNeverOverlapOnAPhoneAndATablet() {
        for ((w, h) in listOf(390f to 844f, 1280f to 800f, 841f to 701f)) {
            val s = scene(sample, w, h)
            s.draw(Count(), 0.0)
            checkNames(s, "whole, ${w}x$h")
            val cardiology = sample.titles.indexOf("Cardiology")
            s.go(cardiology, 0.0)
            val t = settleZoom(s, 0.0)
            s.draw(Count(), t)
            checkNames(s, "Cardiology, ${w}x$h")
        }
    }

    @Test fun eachBoughsNameIsOnItsOwnBough() {
        for (folder in listOf(0, sample.titles.indexOf("Examples"))) {
            val s = scene(sample)
            s.go(folder, 0.0, instant = true)
            s.draw(Count(), 0.0)
            val box = FloatArray(4)
            var boughs = 0
            for (q in 0 until s.labelCount) {
                val i = s.labelNode(q)
                if (i < 0 || !s.labelBold(q)) continue
                s.labelBox(q, box)
                val cx = (box[0] + box[2]) / 2; val cy = (box[1] + box[3]) / 2
                // its middle is one of its own points, out along it
                val on = TreeScene.ON_BOUGH.any { j -> abs(s.pointX(i, j) - cx) < 0.01f && abs(s.pointY(i, j) - cy) < 0.01f }
                assertTrue("${sample.titles[i]}: named on its own bough", on)
                assertTrue("${sample.titles[i]}: a bough", sample.childCount[i] > 0)
                boughs++
            }
            assertTrue("some boughs named on themselves: $boughs", boughs >= 3)
        }
    }

    @Test fun aTapZoomsUntilThePartIsTheWholeTreeAndBackComesOut() {
        val s = scene(sample)
        s.draw(Count(), 0.0)
        val examples = sample.titles.indexOf("Examples")
        // tap its name
        val box = FloatArray(4)
        val q = (0 until s.labelCount).first { s.labelNode(it) == examples }
        s.labelBox(q, box)
        val hit = s.hitTest((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)
        assertEquals(examples, hit)
        // a name answers 24 dp out from its middle each way, the least target being 48
        assertEquals(examples, s.hitTest((box[0] + box[2]) / 2, (box[1] + box[3]) / 2 + 23f))
        assertEquals(examples, s.activate(hit, 0.0))
        assertTrue("it eases", s.animating)
        val t = settleZoom(s, 0.0)
        assertEquals(examples, s.focus)
        // now the part is framed as the whole was: its subtree fills the view inside the insets
        val want = s.frameOf(examples, NestCamera())
        assertEquals(want.z, s.cam.z, 1e-9); assertEquals(want.x, s.cam.x, 1e-9); assertEquals(want.r, s.cam.r, 1e-9)
        // an empty spot answers nothing, which the view takes as Back
        s.draw(Count(), t)
        val empty = (0 until 40).flatMap { gx -> (0 until 80).map { gy -> gx * s.viewW / 40 to s.insetTop + gy * (s.viewH - s.insetTop) / 80 } }
            .count { (x, y) -> s.hitTest(x, y) == -1 }
        assertTrue("room to tap that is no part: $empty spots", empty > 400)
        assertTrue(s.back(t))
        settleZoom(s, t)
        assertEquals(0, s.focus)
        assertFalse("the whole tree has no Back", s.back(t + 2000))
    }

    @Test fun anIdeaOpensThePartItGrowsOn() {
        val s = scene(sample)
        s.draw(Count(), 0.0)
        val idea = sample.titles.indexOf("Venous hum")
        assertEquals(0, sample.childCount[idea])
        val varix = sample.parent[idea]
        assertEquals("Saphena varix", sample.titles[varix])
        assertEquals(varix, s.activate(idea, 0.0))
        settleZoom(s, 0.0)
        // an idea in the part in view moves nothing (its card is the view's)
        assertEquals(-1, s.activate(idea, 5000.0))
    }

    @Test fun reduceMotionCutsStraightThere() {
        val s = scene(sample)
        s.reduced = true
        s.draw(Count(), 0.0)
        val folder = sample.titles.indexOf("Femoral")
        s.go(folder, 0.0)
        assertFalse(s.animating)
        val want = s.frameOf(folder, NestCamera())
        assertEquals(want.z, s.cam.z, 1e-9)
    }

    @Test fun aPinchSettlesOnThePartThatFillsTheView() {
        val s = scene(sample)
        s.draw(Count(), 0.0)
        val folder = sample.titles.indexOf("Cardiology")
        val at = s.frameOf(folder, NestCamera())
        s.cam.set(at)
        s.settle()
        assertEquals(folder, s.focus)
        s.zoomAbout(195f, 400f, 0.2f)
        s.settle()
        assertEquals(0, s.focus)
    }

    @Test fun linksAreVinesAndShowLinksKeepsOnlyOnes() {
        val a = sample.titles.indexOf("Heart failure"); val b = sample.titles.indexOf("Groin hernia"); val c = sample.titles.indexOf("Pericarditis")
        val s = scene(sample, links = NestLinks(intArrayOf(a, c), intArrayOf(b, b)))
        val all = Count(); s.draw(all, 0.0)
        s.linksOf = sample.titles.indexOf("Saphena varix")
        val none = Count(); s.draw(none, 0.0)
        assertEquals("no vine of its own: one stroke fewer", all.strokes - 1, none.strokes)
    }

    @Test fun whatTheFilterLeavesOutIsNotDrawnNorReached() {
        val a = sample.titles.indexOf("Heart failure"); val b = sample.titles.indexOf("Groin hernia")
        val s = scene(sample, links = NestLinks(intArrayOf(a), intArrayOf(b)))
        val all = Count(); s.draw(all, 0.0)
        val card = sample.titles.indexOf("Cardiology")
        for (i in 0 until sample.size) if (sample.within(card, i)) s.hidden[i] = true
        val some = Count(); s.draw(some, 0.0)
        for (i in 0 until sample.size) if (sample.within(card, i)) assertFalse("${sample.titles[i]} is hidden", s.isDrawn(i))
        assertTrue("the rest is still drawn", s.isDrawn(sample.titles.indexOf("Examples")))
        assertTrue("fewer parts drawn", some.points < all.points)
        for (q in 0 until s.labelCount) assertFalse("no name of a hidden part", s.labelNode(q) >= 0 && s.hidden[s.labelNode(q)])
        // its vine goes with it: with every vine shown, one stroke fewer
        assertEquals(all.strokes - 1, some.strokes)
    }

    // ---- the cost guard: the owner's 120 fps leaves about 8 ms a frame; the tree's own work gets 4

    /** The concept's ?stress: three more under every leaf, [levels] deep. */
    private fun stress(levels: Int): NestNodes {
        val parent = ArrayList<Int>(); val title = ArrayList<String>(); val folder = ArrayList<Boolean>()
        for (i in 0 until sample.size) { parent.add(sample.parent[i]); title.add(sample.titles[i]); folder.add(sample.folder[i]) }
        fun grow(p: Int, d: Int) { for (q in 1..3) { parent.add(p); title.add("Part $q"); folder.add(false); if (d > 1) grow(parent.size - 1, d - 1) } }
        for (j in 1 until sample.size) if (sample.childCount[j] == 0) grow(j, levels)
        // grown in place the parents are no longer first; renumber breadth first
        val n = parent.size
        val kids = Array(n) { ArrayList<Int>() }
        for (i in 1 until n) kids[parent[i]].add(i)
        val order = ArrayList<Int>().apply { add(0) }
        var h = 0
        while (h < order.size) { order.addAll(kids[order[h]]); h++ }
        val at = IntArray(n); order.forEachIndexed { q, i -> at[i] = q }
        return NestNodes(Array(n) { "${order[it]}" }, IntArray(n) { if (it == 0) -1 else at[parent[order[it]]] },
            Array(n) { title[order[it]] }, BooleanArray(n) { folder[order[it]] }, IntArray(n) { -1 })
    }

    @Test fun aDeepTreeDrawsNoMoreThanItsBudget() {
        val big = stress(3)
        assertTrue("${big.size} parts", big.size > 3000)
        val s = scene(big)
        s.draw(Count(), 0.0)
        assertTrue("${s.drawnCount} drawn", s.drawnCount in 1..TreeScene.BUDGET)
        s.zoomAbout(195f, 500f, 40f)
        s.draw(Count(), 0.0)
        assertTrue("${s.drawnCount} drawn", s.drawnCount in 1..TreeScene.BUDGET)
    }

    @Test fun layoutAndEachFrameStayCheap() {
        val big = stress(3)
        // the layout, once per change of the ideas: a 3,000-part tree well under a frame's worth of a second
        repeat(3) { TreeLayout(big) }
        val l0 = System.nanoTime(); repeat(5) { TreeLayout(big) }
        val layoutMs = (System.nanoTime() - l0) / 5 / 1e6
        assertTrue("layout of ${big.size} parts: $layoutMs ms", layoutMs < 120)
        // a zoom in flight, frame by frame, names and all
        val s = scene(big)
        val sink = Count()
        val target = big.childList[big.childStart[big.childList[0]]]
        val times = DoubleArray(400)
        var t = 0.0
        repeat(300) { s.draw(sink, t); t += 8.0; if (!s.animating) { s.go(if (s.focus == 0) target else 0, t) } }
        for (f in times.indices) {
            val a = System.nanoTime(); s.draw(sink, t); times[f] = (System.nanoTime() - a) / 1e6
            t += 8.0
            if (!s.animating) s.go(if (s.focus == 0) target else 0, t)
        }
        times.sort()
        val median = times[times.size / 2]; val p95 = times[(times.size * 95) / 100]
        assertTrue("a frame's work: median $median ms, 95th $p95 ms", median < 2.0 && p95 < 4.0)
    }

    @Test fun aFrameAllocatesNothing() {
        val big = stress(2)
        val s = scene(big)
        val sink = Count()
        var t = 0.0
        val target = big.childList[big.childStart[0]]
        // warm, so the JIT's own work is done
        repeat(500) { s.draw(sink, t); t += 8.0; if (!s.animating) s.go(if (s.focus == 0) target else 0, t) }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        repeat(200) { s.draw(sink, t); t += 8.0 }
        val after = bean.getThreadAllocatedBytes(id)
        // the bean's own reading costs a little; 200 frames of one list each would be megabytes
        assertTrue("200 frames allocated ${after - before} bytes", after - before < 16_000)
    }
}
