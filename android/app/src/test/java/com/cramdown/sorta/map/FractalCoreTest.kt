package com.cramdown.sorta.map

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

// S12: the Fractal map's shared core against tools/fractal_fixture.json, the file iOS's
// FractalCoreTests.swift reads too. The layout must give back what the approved concept's own code
// made of its sample tree; each frame (the dots drawn, the names and where they sit, where taps land)
// must give back what this file recorded, so the two platforms draw the same picture of the same data.
class FractalCoreTest {
    @Test fun layoutIsTheConcepts() {
        val fx = FractalFixture.read()
        val layout = FractalFixture.layout(fx)
        val want = fx.getJSONObject("layout")
        val n = layout.tree.count
        for ((name, got) in listOf("x" to layout.x, "y" to layout.y, "sp" to layout.spacing, "dr" to layout.dot,
            "rad" to layout.reach, "cx" to layout.boxX, "cy" to layout.boxY, "hw" to layout.halfW, "hh" to layout.halfH)) {
            val arr = want.getJSONArray(name)
            assertEquals(name, arr.length(), n)
            for (i in 0 until n) assertTrue("$name[$i] ${got[i]} against ${arr.getDouble(i)}", abs(got[i] - arr.getDouble(i)) <= 1e-9)
        }
    }

    @Test fun treeOrdersChildrenHeaviestFirst() {
        val fx = FractalFixture.read()
        val tree = FractalFixture.layout(fx).tree
        for (i in 0 until tree.count) for (k in 1 until tree.childCount[i]) {
            val a = tree.children[tree.childStart[i] + k - 1]; val b = tree.children[tree.childStart[i] + k]
            assertTrue(tree.weight[a] > tree.weight[b] || (tree.weight[a] == tree.weight[b] && a < b))
        }
        for (i in 1 until tree.count) assertTrue(tree.holds(tree.parent[i], i) && !tree.holds(i, tree.parent[i]))
    }

    @Test fun framesAreTheRecordedOnes() {
        val fx = FractalFixture.read()
        val frames = fx.getJSONArray("frames")
        assertTrue("the fixture records frames", frames.length() >= 5)
        for (k in 0 until frames.length()) {
            val case = frames.getJSONObject(k)
            val got = FractalFixture.run(fx, case)
            FractalFixture.compare(case.getString("name"), case.getJSONObject("expect"), got)
        }
    }

    @Test fun noNameWithoutItsDot() {
        // the reviewer's tablet note (BNP, PR depression): every name has its dot, drawn at least
        // 2.5 across and 0.6 strong, in every recorded frame
        val fx = FractalFixture.read()
        val frames = fx.getJSONArray("frames")
        var names = 0
        for (k in 0 until frames.length()) {
            val case = frames.getJSONObject(k)
            val frame = FractalFixture.prepared(fx, case)
            for (n in 0 until frame.nameCount) {
                val i = frame.nameNode[n]; if (i < 0) continue
                val slot = (0 until frame.drawnCount).firstOrNull { frame.drawn[it] == i }
                assertTrue("${case.getString("name")}: ${frame.tree.titles[i]} drawn", slot != null)
                assertTrue("${frame.tree.titles[i]} 2.5 across", 2 * frame.dotRadius[slot!!] >= 2.5)
                assertTrue("${frame.tree.titles[i]} alpha ${frame.dotAlpha[slot]}", frame.dotAlpha[slot] >= FractalRules.NAMED_ALPHA)
                // and no other name lies over it
                for (m in 0 until frame.nameCount) {
                    if (m == n || frame.nameNode[m] < 0) continue
                    val over = frame.sx[i] > frame.nameX0[m] && frame.sx[i] < frame.nameX1[m] && frame.sy[i] > frame.nameY0[m] && frame.sy[i] < frame.nameY1[m]
                    assertTrue("${case.getString("name")}: ${frame.tree.titles[i]} under ${frame.tree.titles[frame.nameNode[m]]}", !over)
                }
                names++
            }
        }
        assertTrue("names checked: $names", names > 20)
    }

    @Test fun aThousandPosesKeepEveryNameHonest() {
        // S12 A4: 1,000 seeded poses (phone, tablet, foldable; a cluster opened, pinched and slid; text up
        // to AX1; a chosen idea and its links). Every placed name has its dot drawn 2.5 across and 0.6
        // strong, no two names overlap, at most 120 names and the budget of parts. The poses come from
        // one 64-bit stream, the same on iOS (FractalCoreTests.swift)
        val fx = FractalFixture.read()
        val layout = FractalFixture.layout(fx)
        val tree = layout.tree
        val hubs = (0 until tree.count).filter { tree.childCount[it] > 0 }
        val ideas = (0 until tree.count).filter { tree.kind[it] == FractalTree.IDEA }
        val views = arrayOf(doubleArrayOf(411.0, 914.0, 104.0, 90.0), doubleArrayOf(1280.0, 800.0, 80.0, 90.0), doubleArrayOf(673.0, 841.0, 90.0, 90.0))
        val scales = doubleArrayOf(1.0, 1.18, 1.35)
        var x = 91L
        fun next(): Double { x = x * 6364136223846793005L + 1442695040888963407L; return (x ushr 11).toDouble() / (1L shl 53).toDouble() }
        var named = 0; var most = 0
        for (pose in 0 until 1000) {
            val v = views[pose % 3]
            val frame = FractalFrame(layout, FractalFixture.links(fx))
            frame.setView(v[0], v[1], v[2], 0.0, v[3], 0.0)
            for (i in 0 until tree.count) frame.nameWidth[i] = 55.0 * tree.titles[i].length
            frame.reach = 22.0
            frame.textScale = scales[(next() * 3).toInt()]
            frame.go(hubs[(next() * hubs.size).toInt()], 0.0, instant = true)
            frame.zoom(next() * v[0], next() * v[1], kotlin.math.exp(next() * 2.4 - 1.2))
            frame.pan((next() - 0.5) * v[0] * 0.6, (next() - 0.5) * v[1] * 0.6)
            if (next() < 0.3) { val c = ideas[(next() * ideas.size).toInt()]; frame.chosen = c; frame.linksOf = c }
            frame.step()
            assertTrue("pose $pose: ${frame.nameCount} names", frame.nameCount <= FractalRules.NAMES)
            assertTrue("pose $pose: ${frame.drawnCount} parts", frame.drawnCount <= FractalRules.BUDGET)
            for (n in 0 until frame.nameCount) {
                val i = frame.nameNode[n]; if (i < 0) continue
                val slot = (0 until frame.drawnCount).firstOrNull { frame.drawn[it] == i }
                assertTrue("pose $pose: ${tree.titles[i]} drawn", slot != null)
                assertTrue("pose $pose: ${tree.titles[i]} 2.5 across", 2 * frame.dotRadius[slot!!] >= 2.5)
                assertTrue("pose $pose: ${tree.titles[i]} alpha ${frame.dotAlpha[slot]}", frame.dotAlpha[slot] >= FractalRules.NAMED_ALPHA)
                for (m in n + 1 until frame.nameCount) {
                    if (frame.nameNode[m] < 0) continue
                    val overlap = frame.nameX0[n] < frame.nameX1[m] && frame.nameX0[m] < frame.nameX1[n] &&
                        frame.nameY0[n] < frame.nameY1[m] && frame.nameY0[m] < frame.nameY1[n]
                    assertTrue("pose $pose: ${tree.titles[i]} over ${tree.titles[frame.nameNode[m]]}", !overlap)
                }
                named++
            }
            most = maxOf(most, frame.nameCount)
        }
        assertTrue("names checked: $named, most in a pose $most", named > 2000 && most > 10)
    }

    @Test fun tonesArePerformancesRegionColours() {
        val t = FractalTree.of(listOf(FractalTree.Companion.Folder("a", null, "A", 1), FractalTree.Companion.Folder("b", "a", "B", 2),
            FractalTree.Companion.Folder("c", null, "C", 3)),
            listOf(FractalTree.Companion.Idea("i", "a", "I", 1), FractalTree.Companion.Idea("j", "b", "J", 1),
                FractalTree.Companion.Idea("k", null, "K", 1), FractalTree.Companion.Idea("l", "c", "L", 1)), "All ideas")
        val tones = FractalTones(t)
        fun of(title: String) = t.titles.indexOf(title)
        assertEquals(0x5C9CFF, tones.dot[of("I")])             // the first hue, a top folder's idea
        assertEquals(0x69A4FF, tones.dot[of("J")])             // a level down: 0.08 to white
        assertEquals(0xFF5E7D, tones.dot[of("L")])             // the second top folder's hue
        assertEquals(0xC9D1E0, tones.dot[of("K")])             // in no folder: the loose grey
        assertEquals(0xE2E6EE, tones.hub[0])                   // the hub of all ideas: the loose grey lit
        assertEquals(0xA5C8FF, tones.hub[of("A")])             // a lit hub: 0.45 to white
        assertEquals(0x7CAFFF, tones.glow[of("A")])            // its glow: 0.2 to white
    }

    @Test fun budgetHoldsWithManyIdeas() {
        // a hub of 40 folders of 2,500 ideas: never more than the budget drawn, whatever the zoom
        val parents = ArrayList<Int>(); parents.add(-1)
        for (f in 0 until 40) { val me = parents.size; parents.add(0); repeat(2_500) { parents.add(me) } }
        val n = parents.size
        val tree = FractalTree(parents.toIntArray(), ByteArray(n) { if (it == 0) 0 else if (parents[it] == 0) 1 else 2 },
            Array(n) { "" }, Array(n) { "Idea $it" }, IntArray(n) { -1 })
        val frame = FractalFrame(FractalLayout(tree))
        frame.setView(390.0, 844.0, 120.0, 0.0, 90.0, 0.0)
        for (i in 0 until n) frame.nameWidth[i] = 55.0 * tree.titles[i].length
        // the graphics settings' budgets: Low and Balanced hold theirs, and none goes past High's
        for (b in listOf(FractalRules.BUDGET_LOW, FractalRules.BUDGET_BALANCED)) {
            frame.budget = b; frame.go(0, 0.0, instant = true); frame.zoom(195.0, 470.0, 4.0); frame.step()
            assertEquals(b, frame.drawnCount)
        }
        frame.budget = 10_000; assertEquals(FractalRules.BUDGET, frame.budget)
        for (focus in listOf(0, 1, 2502)) {
            frame.go(focus, 0.0, instant = true)
            for (z in listOf(1.0, 4.0, 40.0)) {
                frame.zoom(195.0, 470.0, z); frame.step()
                assertTrue("drawn ${frame.drawnCount}", frame.drawnCount in 1..FractalRules.BUDGET)
                assertTrue(frame.nameCount <= FractalRules.NAMES)
            }
        }
    }

    @Test fun storeTreeKeepsFoldersThenIdeasOldestFirst() {
        val folders = listOf(
            FractalTree.Companion.Folder("b", null, "Cardiology", 2), FractalTree.Companion.Folder("a", null, "Examples", 1),
            FractalTree.Companion.Folder("c", "a", "Inguinal", 3), FractalTree.Companion.Folder("x", "y", "Loop 1", 4),
            FractalTree.Companion.Folder("y", "x", "Loop 2", 5))
        val ideas = listOf(
            FractalTree.Companion.Idea("i2", "a", "Two", 20), FractalTree.Companion.Idea("i1", "a", "One", 10),
            FractalTree.Companion.Idea("i3", null, "Loose", 5), FractalTree.Companion.Idea("i4", "gone", "Lost folder", 6),
            FractalTree.Companion.Idea("i5", "c", "Deep", 7))
        val t = FractalTree.of(folders, ideas, "All ideas")
        // the ideas in no folder (or in a folder that is gone) hang from the hub, after the folders
        assertEquals(listOf("All ideas", "Examples", "Inguinal", "Deep", "One", "Two", "Cardiology", "Loop 1", "Loop 2", "Loose", "Lost folder"),
            t.titles.toList())
        assertEquals(listOf(-1, 0, 1, 2, 1, 1, 0, 0, 7, 0, 0), t.parent.toList())
        assertEquals(listOf(-1, 0, 0, 0, 0, 0, 1, 2, 2, -1, -1), t.region.toList())
        assertEquals(FractalTree.IDEA, t.kind[9])
        val flat = FractalTree.of(emptyList(), ideas.take(2), "All ideas")
        assertEquals(listOf("All ideas", "One", "Two"), flat.titles.toList())
        assertEquals(listOf(-1, 0, 0), flat.parent.toList())
    }

    @Test fun tapOpensTheClusterAndBackGoesUp() {
        val fx = FractalFixture.read()
        val frame = FractalFixture.frame(fx, 390.0, 844.0, doubleArrayOf(120.0, 0.0, 90.0, 0.0))
        val tree = frame.tree
        val cardiology = tree.titles.indexOf("Cardiology")
        val heartFailure = tree.titles.indexOf("Heart failure")
        frame.go(0, 0.0, instant = true); frame.step()
        assertEquals(cardiology, frame.clusterFor(cardiology))
        // an idea with no parts opens the cluster it sits in
        val bnpChild = tree.titles.indexOf("Raised in heart failure")
        assertEquals(tree.parent[bnpChild], frame.clusterFor(bnpChild))
        frame.go(cardiology, 0.0, instant = true)
        assertEquals(-1, frame.clusterFor(cardiology))
        assertEquals(heartFailure, frame.clusterFor(heartFailure))
        assertTrue(frame.back(0.0, instant = true)); assertEquals(0, frame.focus)
        assertTrue(!frame.back(0.0, instant = true))
        // a glide ends exactly on the cluster's frame, and Reduce Motion's is at once
        val end = DoubleArray(3); frame.frameOf(cardiology, end)
        val length = frame.go(cardiology, 10.0, instant = false)
        assertTrue(length in 0.42..1.25 && frame.gliding)
        frame.advance(10.0 + length / 2); assertTrue(frame.gliding)
        frame.advance(10.0 + length + 0.001); assertTrue(!frame.gliding)
        assertEquals(end[0], frame.camX, 1e-12); assertEquals(end[1], frame.camY, 1e-12); assertEquals(end[2], frame.camZ, 1e-9)
        // a pinch in on a cluster and settling makes it the one Back leaves
        frame.go(0, 0.0, instant = true)
        val at = DoubleArray(3); frame.frameOf(cardiology, at)
        frame.camX = at[0]; frame.camY = at[1]; frame.camZ = at[2]; frame.settle()
        assertEquals(cardiology, frame.focus)
    }
}

/** Reads tools/fractal_fixture.json and runs its frames, for FractalCoreTest and FractalCostTest. */
object FractalFixture {
    fun file(): File {
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !File(root, "tools/fractal_fixture.json").exists()) root = root.parentFile
        return File(root ?: File("/"), "tools/fractal_fixture.json")
    }

    fun read(): JSONObject = JSONObject(file().readText())

    fun tree(fx: JSONObject): FractalTree {
        val t = fx.getJSONObject("tree")
        val p = t.getJSONArray("parent"); val k = t.getJSONArray("kind"); val titles = t.getJSONArray("title")
        val n = p.length()
        return FractalTree(IntArray(n) { p.getInt(it) }, ByteArray(n) { k.getInt(it).toByte() }, Array(n) { "" },
            Array(n) { titles.getString(it) }, IntArray(n) { -1 })
    }

    fun layout(fx: JSONObject) = FractalLayout(tree(fx))

    fun links(fx: JSONObject): IntArray {
        val l = fx.getJSONArray("links")
        return IntArray(2 * l.length()) { l.getJSONArray(it / 2).getInt(it % 2) }
    }

    fun frame(fx: JSONObject, w: Double, h: Double, insets: DoubleArray): FractalFrame {
        val frame = FractalFrame(layout(fx), links(fx))
        frame.setView(w, h, insets[0], insets[1], insets[2], insets[3])
        for (i in 0 until frame.tree.count) frame.nameWidth[i] = 55.0 * frame.tree.titles[i].length
        frame.reach = 22.0
        return frame
    }

    private fun doubles(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }

    /** One case's frame, stepped. */
    fun prepared(fx: JSONObject, case: JSONObject): FractalFrame {
        val frame = frame(fx, case.getDouble("w"), case.getDouble("h"), doubles(case.getJSONArray("insets")))
        frame.textScale = case.optDouble("textScale", 1.0)
        val hide = case.optJSONArray("hide")
        if (hide != null) {
            val shown = BooleanArray(frame.tree.count) { true }
            for (k in 0 until hide.length()) { val i = hide.getInt(k); for (j in 0 until frame.tree.count) if (frame.tree.holds(i, j)) shown[j] = false }
            frame.shown = shown
        }
        frame.go(case.getInt("focus"), 0.0, instant = true)
        if (case.has("pinch")) { val p = doubles(case.getJSONArray("pinch")); frame.zoom(p[0], p[1], p[2]) }
        frame.chosen = case.optInt("chosen", -1)
        frame.linksOf = case.optInt("linksOf", -1)
        frame.step()
        return frame
    }

    /** One case: the view, the cluster in view, a pinch, what is chosen and hidden; and what came of it. */
    fun run(fx: JSONObject, case: JSONObject): JSONObject {
        val frame = prepared(fx, case)
        val out = JSONObject()
        out.put("camera", JSONArray(listOf(frame.camX, frame.camY, frame.camZ)))
        out.put("drawn", JSONArray((0 until frame.drawnCount).map { frame.drawn[it] }))
        out.put("dots", JSONArray((0 until frame.drawnCount).map { JSONArray(listOf(frame.dotRadius[it], frame.dotAlpha[it], frame.dotHub[it], frame.glowAlpha[it], frame.lineAlpha[it])) }))
        out.put("names", JSONArray((0 until frame.nameCount).filter { frame.nameNode[it] >= 0 }.map {
            JSONArray(listOf(frame.nameNode[it], frame.nameX0[it], frame.nameY0[it], frame.nameX1[it], frame.nameY1[it], frame.nameSize[it], frame.nameSide[it], frame.nameAlpha[it]))
        }))
        out.put("links", JSONArray((0 until frame.linkCount).map { JSONArray(listOf(frame.linkFrom[it], frame.linkTo[it], frame.linkAlpha[it])) }))
        val taps = case.optJSONArray("taps") ?: JSONArray()
        out.put("hits", JSONArray((0 until taps.length()).map { val t = taps.getJSONArray(it); frame.hit(t.getDouble(0), t.getDouble(1)) }))
        return out
    }

    private fun close(a: Double, b: Double) = abs(a - b) <= 1e-6 * max(1.0, abs(b))

    fun compare(name: String, want: JSONObject, got: JSONObject) {
        for (key in listOf("drawn", "hits")) assertEquals("$name $key", want.getJSONArray(key).toString(), got.getJSONArray(key).toString())
        for (key in listOf("camera")) {
            val a = want.getJSONArray(key); val b = got.getJSONArray(key)
            for (i in 0 until a.length()) assertTrue("$name $key[$i]", close(b.getDouble(i), a.getDouble(i)))
        }
        for (key in listOf("dots", "names", "links")) {
            val a = want.getJSONArray(key); val b = got.getJSONArray(key)
            assertEquals("$name $key count", a.length(), b.length())
            for (i in 0 until a.length()) {
                val x = a.getJSONArray(i); val y = b.getJSONArray(i)
                for (j in 0 until x.length()) assertTrue("$name $key[$i][$j] ${y.get(j)} against ${x.get(j)}", close(y.getDouble(j), x.getDouble(j)))
            }
        }
    }
}
