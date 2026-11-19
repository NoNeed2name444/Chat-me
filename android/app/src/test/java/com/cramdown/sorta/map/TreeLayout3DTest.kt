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
            val got = mapOf("base" to l.base, "dir" to l.dir, "len" to l.length, "r0" to l.r0, "r1" to l.r1, "bend" to l.bend,
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

    // the owner's review of the third pass (19 Nov): a believable tree, not cramped
    @Test fun aBranchIsAsThickAsTheLeavesItCarries() {
        val trees = fixture.getJSONArray("trees")
        for (q in 0 until trees.length()) {
            val l = TreeLayout3D(nodesOf(trees.getJSONObject(q)))
            val nodes = l.nodes
            for (i in 0 until nodes.size) {
                if (nodes.childCount[i] == 0) continue
                // the pipe model: its children's areas add up to its own, less only where a join held one thinner
                var area = 0.0
                for (k in 0 until nodes.childCount[i]) area += l.r0[nodes.child(i, k)].let { it * it }
                val share = area / (l.r0[i] * l.r0[i])
                assertTrue("${nodes.titles[i]}: its children carry $share of its area", share in 0.6..1.0 + 1e-9)
                // it tapers to its biggest child's
                val most = (0 until nodes.childCount[i]).maxOf { l.r0[nodes.child(i, it)] }
                assertTrue("${nodes.titles[i]} tapers to ${l.r1[i]}, its biggest child is ${most}", l.r1[i] >= most - 1e-12 || l.r1[i] == l.r0[i] * TreeShape3D.TIP)
            }
        }
    }

    @Test fun siblingBranchesSpreadRoundTheirParentAndTheHeavierAreLonger() {
        val trees = fixture.getJSONArray("trees")
        for (q in 0 until trees.length()) {
            val l = TreeLayout3D(nodesOf(trees.getJSONObject(q)))
            val nodes = l.nodes
            for (i in 0 until nodes.size) {
                val kids = (0 until nodes.childCount[i]).map { nodes.child(i, it) }.filter { nodes.childCount[it] > 0 }
                for (a in kids) for (b in kids) if (a < b) {
                    var dot = 0.0
                    for (k in 0..2) dot += l.dir[3 * a + k] * l.dir[3 * b + k]
                    val apart = Math.toDegrees(kotlin.math.acos(dot.coerceIn(-1.0, 1.0)))
                    assertTrue("${nodes.titles[a]} and ${nodes.titles[b]} are $apart degrees apart", apart >= Math.toDegrees(TreeShape3D.GAP) - 1e-9)
                    if (nodes.weight[a] > nodes.weight[b]) assertTrue("${nodes.titles[a]} is heavier, not shorter", l.length[a] >= l.length[b] - 1e-12)
                }
            }
        }
        // round the trunk, not in a fan: the trunk's branches lean out on more than one side
        val l = TreeLayout3D(nodesOf(trees.getJSONObject(0)))
        val kids = (0 until l.nodes.childCount[0]).map { l.nodes.child(0, it) }
        val sides = kids.map { Math.toDegrees(kotlin.math.atan2(l.dir[3 * it + 2], l.dir[3 * it])) }
        assertTrue("the trunk's branches lean round it: $sides", sides.maxOrNull()!! - sides.minOrNull()!! > 120)
    }

    @Test fun aBranchCurvesUpAndAChildJoinsOnTheCurve() {
        val l = TreeLayout3D(nodesOf(fixture.getJSONArray("trees").getJSONObject(0)))
        val nodes = l.nodes
        val t0 = DoubleArray(3); val t1 = DoubleArray(3); val at = DoubleArray(3)
        var bent = 0
        for (i in 1 until nodes.size) {
            if (nodes.childCount[i] == 0) { for (a in 0..2) assertEquals(0.0, l.bend[3 * i + a], 0.0); continue }
            l.tangentAt(i, 0.0, t0); l.tangentAt(i, 1.0, t1)
            assertTrue("${nodes.titles[i]} turns up toward its tip", t1[1] >= t0[1] - 1e-12)
            if (t1[1] > t0[1] + 0.05) bent++
            // each child's base lies on this curve
            for (k in 0 until nodes.childCount[i]) {
                val c = nodes.child(i, k)
                var best = Double.POSITIVE_INFINITY
                for (s in 0..400) {
                    l.curveAt(i, s / 400.0, at)
                    var d = 0.0
                    for (a in 0..2) d += (at[a] - l.base[3 * c + a]).let { it * it }
                    best = minOf(best, sqrt(d))
                }
                assertTrue("${nodes.titles[c]} joins ${nodes.titles[i]} $best off its curve", best < 2e-3 * l.length[i])
            }
        }
        assertTrue("most branches bend ($bent)", bent > 10)
    }

    @Test fun anIdeaIsAShortStemInASprayAndSpraysKeepApart() {
        val trees = fixture.getJSONArray("trees")
        for (q in 0 until trees.length()) {
            val l = TreeLayout3D(nodesOf(trees.getJSONObject(q)))
            val nodes = l.nodes
            val leaves = (1 until nodes.size).filter { l.leaf[it] > 0 }
            for (i in leaves) {
                // a stem shorter than its leaf, on the outer part of its parent
                assertTrue(l.length[i] < 2 * l.leaf[i])
                val p = nodes.parent[i]
                if (p > 0) {
                    var best = 0.0; var off = Double.POSITIVE_INFINITY; val at = DoubleArray(3)
                    for (s in 0..1000) {
                        l.curveAt(p, s / 1000.0, at)
                        var d = 0.0
                        for (a in 0..2) d += (at[a] - l.base[3 * i + a]).let { it * it }
                        if (sqrt(d) < off) { off = sqrt(d); best = s / 1000.0 }
                    }
                    assertTrue("${nodes.titles[i]} is $off off its parent", off < 2e-3 * l.length[p])
                    assertTrue("${nodes.titles[i]} grows $best along its parent", best >= 1 - TreeShape3D.SPRAY - 0.01)
                }
            }
            // two leaves of different sprays never sit within half their lengths of each other
            for (a in leaves) for (b in leaves) if (a < b && nodes.parent[a] != nodes.parent[b]) {
                var d = 0.0
                for (k in 0..2) d += (l.anchor[3 * a + k] - l.anchor[3 * b + k]).let { it * it }
                assertTrue("${nodes.titles[a]} and ${nodes.titles[b]}", sqrt(d) >= 0.5 * (l.leaf[a] + l.leaf[b]))
            }
        }
    }

    @Test fun noLeafIsBiggerThanTheMost() {
        val trees = fixture.getJSONArray("trees")
        for (t in 0 until trees.length()) {
            val l = TreeLayout3D(nodesOf(trees.getJSONObject(t)))
            for (i in 1 until l.nodes.size) assertTrue("leaf $i is ${l.leaf[i]} across", l.leaf[i] <= TreeShape3D.LEAF_MOST + 1e-12)
        }
        // leaves vary in size, by at most LEAF_VAR
        val first = TreeLayout3D(nodesOf(trees.getJSONObject(0)))
        val sizes = first.leaf.filter { it > 0 }
        val spread = sizes.maxOrNull()!! / sizes.minOrNull()!!
        assertTrue("leaves from ${sizes.minOrNull()} to ${sizes.maxOrNull()}", spread > 1.1 && spread <= (1 + TreeShape3D.LEAF_VAR / 2) / (1 - TreeShape3D.LEAF_VAR / 2) + 1e-9)
        // the crown is about as wide as it is tall, and the trunk solid
        val l = TreeLayout3D(nodesOf(trees.getJSONObject(0)))
        val nodes = l.nodes
        var lowest = Double.POSITIVE_INFINITY
        for (k in 0 until nodes.childCount[0]) lowest = minOf(lowest, l.base[3 * nodes.child(0, k) + 1])
        val lo = DoubleArray(3) { Double.POSITIVE_INFINITY }; val hi = DoubleArray(3) { Double.NEGATIVE_INFINITY }
        for (i in 1 until nodes.size) for (a in 0..2) { lo[a] = minOf(lo[a], l.anchor[3 * i + a]); hi[a] = maxOf(hi[a], l.anchor[3 * i + a]) }
        val wide = maxOf(hi[0] - lo[0], hi[2] - lo[2]); val tall = hi[1] - lowest
        assertTrue("the crown is $wide wide and $tall tall", wide / tall in 0.8..1.6)
        assertTrue(2 * l.r0[0] >= 0.14 * l.length[0])
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
