package com.cramdown.sorta.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

// S12, the Fractal in 3D: the layout against tools/fractal_fixture3d.json (made by
// tools/fractal_fixture3d.mjs, read by iOS's FractalLayout3DTests.swift too), so both platforms put
// every dot in the same place; and its rules: All ideas' parts all round it, every other hub's parts on
// a cap opening away from its own hub, the spacing and the dots shrinking each level down.
class FractalLayout3DTest {
    private fun fixture(): JSONObject {
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !File(root, "tools/fractal_fixture3d.json").exists()) root = root.parentFile
        return JSONObject(File(root ?: File("/"), "tools/fractal_fixture3d.json").readText())
    }

    private fun tree(parent: IntArray): FractalTree {
        val n = parent.size
        val kids = IntArray(n); for (i in 1 until n) kids[parent[i]]++
        return FractalTree(parent, ByteArray(n) { if (it == 0) FractalTree.HUB else if (kids[it] > 0) FractalTree.FOLDER else FractalTree.IDEA },
            Array(n) { "" }, Array(n) { "Part $it" }, IntArray(n) { -1 })
    }

    private fun trees(): List<Pair<String, FractalTree>> {
        val list = fixture().getJSONArray("trees")
        return (0 until list.length()).map { k ->
            val t = list.getJSONObject(k)
            val p = t.getJSONArray("parent")
            t.getString("name") to tree(IntArray(p.length()) { p.getInt(it) })
        }
    }

    @Test fun layoutIsTheFixtures() {
        val list = fixture().getJSONArray("trees")
        assertEquals("the fixture holds the sample and the made-up tree", 2, list.length())
        for (k in 0 until list.length()) {
            val t = list.getJSONObject(k)
            val p = t.getJSONArray("parent")
            val layout = FractalLayout3D(tree(IntArray(p.length()) { p.getInt(it) }))
            val want = t.getJSONObject("layout")
            for ((name, got) in listOf("pos" to layout.pos, "spacing" to layout.spacing, "dot" to layout.dot,
                    "centre" to layout.centre, "reach" to layout.reach)) {
                val arr = want.getJSONArray(name)
                assertEquals("${t.getString("name")} $name", arr.length(), got.size)
                for (i in got.indices) assertTrue("${t.getString("name")} $name[$i] ${got[i]} against ${arr.getDouble(i)}",
                    abs(got[i] - arr.getDouble(i)) <= 1e-9)
            }
        }
    }

    @Test fun eachHubsPartsOpenAwayFromItsOwnHubOnACap() {
        for ((name, t) in trees()) {
            val l = FractalLayout3D(t)
            for (i in 1 until t.count) {
                val p = t.parent[i]
                val ex = l.pos[3 * i] - l.pos[3 * p]; val ey = l.pos[3 * i + 1] - l.pos[3 * p + 1]; val ez = l.pos[3 * i + 2] - l.pos[3 * p + 2]
                val d = sqrt(ex * ex + ey * ey + ez * ez)
                assertTrue("$name: part $i lies 0.64 to 1.08 of its hub's spacing out", d >= 0.64 * l.spacing[p] - 1e-12 && d <= 1.08 * l.spacing[p] + 1e-12)
                if (p == 0) continue
                // the angle from the hub's own way out stays inside its cap
                val cos = (ex * l.out[3 * p] + ey * l.out[3 * p + 1] + ez * l.out[3 * p + 2]) / d
                val half = minOf(Fractal3DRules.CAP_MOST, Fractal3DRules.CAP_LEAST + t.childCount[p] * Fractal3DRules.CAP_EACH)
                assertTrue("$name: part $i at ${acos(cos.coerceIn(-1.0, 1.0))} inside the cap $half", acos(cos.coerceIn(-1.0, 1.0)) <= half + 1e-9)
            }
        }
    }

    @Test fun spacingAndDotsShrinkEachLevelDown() {
        for ((name, t) in trees()) {
            val l = FractalLayout3D(t)
            for (i in 1 until t.count) {
                val p = t.parent[i]
                assertTrue("$name: $i's spacing under its hub's", l.spacing[i] < l.spacing[p])
                assertTrue("$name: $i's dot under its hub's", l.dot[i] < l.dot[p])
                assertTrue("$name: $i's dot is drawn", l.dot[i] > 0)
            }
            // a cluster's ball holds every part of it
            for (i in 0 until t.count) for (k in t.tin[i] until t.tout[i]) {
                val j = t.order[k]
                val x = l.pos[3 * j] - l.centre[3 * i]; val y = l.pos[3 * j + 1] - l.centre[3 * i + 1]; val z = l.pos[3 * j + 2] - l.centre[3 * i + 2]
                assertTrue("$name: $j inside $i's ball", sqrt(x * x + y * y + z * z) <= l.reach[i] + 1e-12)
            }
        }
    }

    @Test fun theSameTreeLaysOutTheSameEveryTime() {
        val t = trees().first().second
        val a = FractalLayout3D(t); val b = FractalLayout3D(t)
        assertTrue(a.pos.contentEquals(b.pos) && a.reach.contentEquals(b.reach))
    }

    @Test fun aHundredThousandIdeasLayOutQuickly() {
        val parent = ArrayList<Int>().apply { add(-1) }
        for (f in 0 until 200) {
            val top = parent.size; parent.add(0)
            for (s in 0 until 4) { val sub = parent.size; parent.add(top); repeat(125) { parent.add(sub) } }
        }
        val t = tree(parent.toIntArray())
        FractalLayout3D(t)
        val start = System.nanoTime()
        FractalLayout3D(t)
        val ms = (System.nanoTime() - start) / 1e6
        assertTrue("laid out 100,000 ideas in $ms ms", ms < 1500)
    }
}
