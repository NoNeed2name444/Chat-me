package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

// S11, the 3D Tree: its layout against tools/tree_fixture.mjs's layout3d (tree-fixture.json's
// expect3d), which iOS's suite treelayout reads too, so the same tree grows the same way in 3D on
// both platforms; and the shape the owner asked for: the trunk up, children round their parent.
class TreeLayout3DTest {
    private val fixture: JSONObject by lazy {
        JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
    }

    private fun nodesOf(t: JSONObject): NestNodes {
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title"); val folder = t.getJSONArray("folder")
        val n = parent.length()
        return NestNodes(Array(n) { "$it" }, IntArray(n) { parent.getInt(it) }, Array(n) { title.getString(it) },
            BooleanArray(n) { folder.getInt(it) == 1 }, IntArray(n) { -1 })
    }

    @Test fun everyTreeMatchesTheFixtureInThreeDimensions() {
        val trees = fixture.getJSONArray("trees")
        assertEquals(4, trees.length())
        for (q in 0 until trees.length()) {
            val t = trees.getJSONObject(q)
            val l = TreeLayout3D(nodesOf(t))
            val expect = t.getJSONObject("expect3d")
            val got = mapOf("base" to l.base, "dir" to l.dir, "len" to l.length, "r0" to l.r0, "r1" to l.r1,
                "anchor" to l.anchor, "leaf" to l.leaf, "sc" to l.centre, "sr" to l.reach)
            assertEquals(got.keys, expect.keys().asSequence().toSet())
            for ((key, values) in got) {
                val want = expect.getJSONArray(key)
                assertEquals("${t.getString("name")} $key: count", want.length(), values.size)
                for (i in values.indices) {
                    val d = abs(values[i] - want.getDouble(i))
                    assertTrue("${t.getString("name")} $key[$i]: ${values[i]} vs ${want.getDouble(i)}", d <= 2e-9)
                }
            }
        }
    }

    @Test fun theTrunkGoesUpAndChildrenSpreadRoundIt() {
        val l = TreeLayout3D(nodesOf(fixture.getJSONArray("trees").getJSONObject(0)))
        val nodes = l.nodes
        assertEquals(0.0, l.base[0], 0.0); assertEquals(0.0, l.base[1], 0.0); assertEquals(1.0, l.dir[1], 0.0)
        // the trunk's children lean out on every side, not in one plane: their across directions span round it
        var sx = 0.0; var sz = 0.0; var minDot = 1.0
        val kids = (0 until nodes.childCount[0]).map { nodes.child(0, it) }
        for (c in kids) {
            val x = l.dir[3 * c]; val z = l.dir[3 * c + 2]; val m = sqrt(x * x + z * z)
            if (m > 1e-6) { sx += x / m; sz += z / m }
            for (o in kids) if (o != c) {
                val ox = l.dir[3 * o]; val oz = l.dir[3 * o + 2]; val om = sqrt(ox * ox + oz * oz)
                if (m > 1e-6 && om > 1e-6) minDot = minOf(minDot, (x * ox + z * oz) / (m * om))
            }
        }
        assertTrue("children on opposite sides", minDot < -0.5)
        // every direction is a unit vector, every branch tapers, and a child is never thicker than its parent's base
        for (i in 0 until nodes.size) {
            val d = sqrt(l.dir[3 * i] * l.dir[3 * i] + l.dir[3 * i + 1] * l.dir[3 * i + 1] + l.dir[3 * i + 2] * l.dir[3 * i + 2])
            assertEquals(1.0, d, 1e-12)
            assertTrue(l.r1[i] < l.r0[i])
            if (i > 0) assertTrue(l.r0[i] < l.r0[nodes.parent[i]])
            assertEquals(nodes.childCount[i] == 0 && i > 0, l.leaf[i] > 0)
        }
        // breadth first: each level after the one above it
        for (k in 1 until nodes.size) assertTrue(nodes.depth[l.order[k]] >= nodes.depth[l.order[k - 1]])
        assertEquals((0 until nodes.size).toSet(), l.order.toSet())
    }

    // the lead's review of 18 Nov: a crown, not a pole with twigs
    @Test fun theCrownIsAboutAsWideAsItIsTall() {
        val l = TreeLayout3D(nodesOf(fixture.getJSONArray("trees").getJSONObject(0)))
        val nodes = l.nodes
        // the trunk's branches leave it 35 to 60 degrees off upright
        for (k in 0 until nodes.childCount[0]) {
            val c = nodes.child(0, k)
            val off = Math.toDegrees(kotlin.math.acos(l.dir[3 * c + 1]))
            assertTrue("${nodes.titles[c]} leaves the trunk $off degrees off upright", off in 34.999..60.001)
        }
        // the crown, from the trunk's lowest branch to the top, is about as wide as it is tall
        var lowest = Double.POSITIVE_INFINITY
        for (k in 0 until nodes.childCount[0]) lowest = minOf(lowest, l.base[3 * nodes.child(0, k) + 1])
        val lo = DoubleArray(3) { Double.POSITIVE_INFINITY }; val hi = DoubleArray(3) { Double.NEGATIVE_INFINITY }
        for (i in 1 until nodes.size) for (t in listOf(0.0, l.length[i])) for (a in 0..2) {
            val v = l.base[3 * i + a] + l.dir[3 * i + a] * t
            lo[a] = minOf(lo[a], v); hi[a] = maxOf(hi[a], v)
        }
        val wide = maxOf(hi[0] - lo[0], hi[2] - lo[2]); val tall = hi[1] - lowest
        assertTrue("the crown is $wide wide and $tall tall", wide / tall in 0.8..1.4)
        // the trunk is solid: about a seventh as thick as it is long
        assertTrue(2 * l.r0[0] >= 0.14 * l.length[0])
    }

    // the lead's review of 18 Nov: flown in to Cardiology, its long limb's leaves walled the view
    @Test fun noLeafIsBiggerThanATwigsMost() {
        val trees = fixture.getJSONArray("trees")
        for (t in 0 until trees.length()) {
            val l = TreeLayout3D(nodesOf(trees.getJSONObject(t)))
            for (i in 1 until l.nodes.size) assertTrue("leaf $i is ${l.leaf[i]} across", l.leaf[i] <= TreeShape3D.LEAF_MOST + 1e-12)
            assertTrue("some leaf is held to the most", (1 until l.nodes.size).any { l.leaf[it] == TreeShape3D.LEAF_MOST } || t > 0)
        }
    }

    @Test fun eachSubtreeBallHoldsItsBodies() {
        val l = TreeLayout3D(nodesOf(fixture.getJSONArray("trees").getJSONObject(0)))
        val nodes = l.nodes
        for (p in 0 until nodes.size) for (q in nodes.tin[p] until nodes.tout[p]) {
            val i = nodes.order[q]
            var s = 0.0
            for (a in 0..2) { val d = l.anchor[3 * i + a] - l.centre[3 * p + a]; s += d * d }
            assertTrue("$i in $p", sqrt(s) <= l.reach[p] + 1e-9)
        }
    }

    @Test fun theSameTreeGrowsTheSameEveryTime() {
        val t = fixture.getJSONArray("trees").getJSONObject(0)
        val a = TreeLayout3D(nodesOf(t)); val b = TreeLayout3D(nodesOf(t))
        assertTrue(a.base.contentEquals(b.base) && a.dir.contentEquals(b.dir) && a.anchor.contentEquals(b.anchor))
    }
}
