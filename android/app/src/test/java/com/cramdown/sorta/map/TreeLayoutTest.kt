package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

// S11: the Tree's layout against the approved concept's own numbers (tree-fixture.json, written by
// tools/tree_fixture.mjs from the concept's layout), which iOS's suite treelayout reads too: the same
// tree grows the same way on both platforms. And the tree the store feeds it: trunk, folders, ideas.
class TreeLayoutTest {
    private val fixture: JSONObject by lazy {
        JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
    }

    private fun nodesOf(t: JSONObject): NestNodes {
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title"); val folder = t.getJSONArray("folder")
        val n = parent.length()
        return NestNodes(Array(n) { "$it" }, IntArray(n) { parent.getInt(it) }, Array(n) { title.getString(it) },
            BooleanArray(n) { folder.getInt(it) == 1 }, IntArray(n) { -1 })
    }

    @Test fun everyTreeMatchesTheConceptPointForPoint() {
        val trees = fixture.getJSONArray("trees")
        assertEquals(TreeShape.K, fixture.getInt("k"))
        assertEquals(4, trees.length())
        for (q in 0 until trees.length()) {
            val t = trees.getJSONObject(q)
            val layout = TreeLayout(nodesOf(t))
            val expect = t.getJSONObject("expect")
            val got = mapOf("bx" to layout.bx, "by" to layout.by, "ang" to layout.angle, "len" to layout.length, "wid" to layout.width,
                "px" to layout.px, "py" to layout.py, "nx" to layout.nx, "ny" to layout.ny, "bcx" to layout.bcx, "bcy" to layout.bcy,
                "brad" to layout.brad, "u0" to layout.u0, "u1" to layout.u1, "v0" to layout.v0, "v1" to layout.v1)
            for ((key, values) in got) {
                val want: JSONArray = expect.getJSONArray(key)
                assertEquals("${t.getString("name")} $key: count", want.length(), values.size)
                for (i in values.indices) {
                    val d = abs(values[i] - want.getDouble(i))
                    assertTrue("${t.getString("name")} $key[$i]: ${values[i]} vs ${want.getDouble(i)}", d <= 2e-9)
                }
            }
        }
    }

    @Test fun theSameTreeGrowsTheSameEveryTime() {
        val t = fixture.getJSONArray("trees").getJSONObject(0)
        val a = TreeLayout(nodesOf(t)); val b = TreeLayout(nodesOf(t))
        assertTrue(a.px.contentEquals(b.px) && a.py.contentEquals(b.py) && a.u1.contentEquals(b.u1))
    }

    @Test fun theDrawMatchesTheConcept() {
        // the concept's first draws from its seed (Math.imul and >>> 0)
        val r = NestRandom(11)
        val first = DoubleArray(3) { r.next() }
        val want = doubleArrayOf(0.5115870486479253, 0.5299464082345366, 0.6081185641232878)
        for (i in 0..2) assertEquals(want[i], first[i], 0.0)
    }

    @Test fun theStoreGrowsTrunkFoldersAndIdeas() {
        val folders = listOf(NestFolderIn("F1", null, "Garden"), NestFolderIn("F2", "F1", "Beds"), NestFolderIn("F3", null, "Work"),
            NestFolderIn("F4", "GONE", "Stray"), NestFolderIn("L1", "L2", "Loop a"), NestFolderIn("L2", "L1", "Loop b"))
        val ideas = listOf(NestIdeaIn("i3", "F1", "Fence", 30), NestIdeaIn("i1", "F1", "Seeds", 10), NestIdeaIn("i2", "F2", "Tomatoes", 5),
            NestIdeaIn("i4", null, "Call the plumber", 1), NestIdeaIn("i5", "MISSING", "Lost", 2), NestIdeaIn("i6", "F3", "Report", 3))
        val t = NestNodes.build("All ideas", folders, ideas)
        assertEquals(1 + folders.size + ideas.size, t.size)
        assertEquals("All ideas", t.titles[0])
        // the trunk's boughs: the top level in order, then the strays, then the ideas in no folder, oldest first
        val boughs = (0 until t.childCount[0]).map { t.titles[t.child(0, it)] }
        assertEquals(listOf("Garden", "Work", "Stray", "Loop a", "Call the plumber", "Lost"), boughs)
        val garden = t.index("F1")
        assertEquals(listOf("Beds", "Seeds", "Fence"), (0 until t.childCount[garden]).map { t.titles[t.child(garden, it)] })
        // tones: the top level's places; strays and loose ideas none; everything takes its bough's
        assertEquals(0, t.tone[garden]); assertEquals(1, t.tone[t.index("F3")]); assertEquals(0, t.tone[t.index("i2")])
        assertEquals(-1, t.tone[t.index("F4")]); assertEquals(-1, t.tone[t.index("i4")])
        assertEquals(NestNodes.LOOSE, t.group[t.index("i4")]); assertEquals(NestNodes.TRUNK, t.group[0])
        assertEquals(2, t.depth[t.index("F2")]); assertEquals(3, t.depth[t.index("i2")])
        // a parent always comes before its children, so the layout's one pass works
        for (i in 1 until t.size) assertTrue(t.parent[i] < i)
        assertTrue(t.folder[garden] && !t.folder[t.index("i1")])
    }

    @Test fun deeperNestingLaysOutTheSame() {
        // the store has folders and ideas today; a sub-idea is a node like any other
        val deep = fixture.getJSONArray("trees").getJSONObject(1)
        val nodes = nodesOf(deep)
        assertTrue(nodes.depth.max() >= 5)
        val layout = TreeLayout(nodes)
        for (i in 0 until nodes.size) assertTrue(layout.length[i] > 0 && layout.brad[i] > 0)
    }
}
