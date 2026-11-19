package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// S11, the 3D Tree's draw lists: one instance a branch and a leaf, made once, in the layout's breadth
// first order, so the Smooth tier's cap keeps the trunk and the big branches and drops the finest twigs.
class TreeModelTest {
    private fun wide(folders: Int, ideas: Int): NestNodes {
        val f = (0 until folders).map { NestFolderIn("f$it", if (it == 0) null else "f${(it - 1) / 3}", "Folder $it") }
        val i = (0 until ideas).map { NestIdeaIn("i$it", "f${it % folders}", "Idea $it", it.toLong()) }
        return NestNodes.build("All ideas", f, i)
    }

    @Test fun everyBranchAndLeafIsOneInstance() {
        val nodes = wide(30, 400)
        val m = TreeModel(nodes, TreeLayout3D(nodes), TreeColors(false), GraphicsTier.HIGH)
        assertEquals(nodes.size, m.branchCount)
        assertEquals((1 until nodes.size).count { nodes.childCount[it] == 0 }, m.leafCount)
        assertFalse(m.capped)
        assertEquals(m.branchCount * TreeModel.BRANCH_FLOATS, m.branches.size)
        assertEquals((m.leafCount + m.fillerCount) * TreeModel.LEAF_FLOATS, m.leaves.size)
        // the trunk first, from the origin up, its curve's control point on its line (it stands straight)
        assertEquals(0f, m.branches[14], 0f)
        assertEquals(1f, m.branches[4], 1e-6f)
        assertEquals(0.5f, m.branches[7], 1e-6f)
        // every branch's control point is its line's middle plus its bend; a leaf's roll within ROLL
        val l = m.layout
        for (k in 0 until m.branchCount) {
            val o = k * TreeModel.BRANCH_FLOATS; val i = m.branches[o + 14].toInt()
            for (a in 0..2) assertEquals((l.base[3 * i + a] + l.dir[3 * i + a] * l.length[i] / 2 + l.bend[3 * i + a]).toFloat(), m.branches[o + 6 + a], 1e-5f)
        }
        for (k in 0 until m.leafCount) assertTrue(kotlin.math.abs(m.leaves[k * TreeModel.LEAF_FLOATS + 7]) <= TreeModel.ROLL + 1e-6)
    }

    @Test fun theSmoothTierKeepsTheBigBranches() {
        val nodes = wide(60, TreeModel.SMOOTH_CAP + 500)
        val l = TreeLayout3D(nodes)
        val m = TreeModel(nodes, l, TreeColors(true), GraphicsTier.SMOOTH)
        assertTrue(m.capped)
        assertEquals(TreeModel.SMOOTH_CAP, m.branchCount)
        val kept = (0 until m.branchCount).map { m.branches[it * TreeModel.BRANCH_FLOATS + 14].toInt() }.toSet()
        for (i in 0 until nodes.size) if (nodes.folder[i]) assertTrue("folder $i kept", i in kept)
        assertTrue(TreeModel(nodes, l, TreeColors(true), GraphicsTier.HIGH).branchCount > m.branchCount)
        val high = TreeModel(nodes, l, TreeColors(true), GraphicsTier.HIGH)
        assertTrue(m.segments < high.segments && m.rings < high.rings && m.bladeRows < high.bladeRows)
    }

    @Test fun theMeshesAreWhole() {
        val (tv, ti) = TreeModel.tube(12, 6)
        assertEquals(13 * 8 * 3, tv.size); assertEquals(12 * 7 * 6, ti.size)
        assertTrue(ti.all { it in 0 until tv.size / 3 })
        // rings from the base (0) to the tip (1), closer together at the base, where the trunk flares, then the closing ring
        assertEquals(0f, tv[2], 0f); assertEquals(1f, tv[(6 * 13 + 12) * 3 + 2], 0f); assertEquals(TreeModel.CAP, tv[tv.size - 1], 0f)
        assertTrue(TreeModel.ringAt(1, 6) < 1f / 6 && TreeModel.ringAt(5, 6) > 5f / 6 - 0.1f)
        for (k in 0 until tv.size / 3) assertEquals(1.0, Math.hypot(tv[3 * k].toDouble(), tv[3 * k + 1].toDouble()), 1e-6)
        // the blade: a pointed oval, folded along its midrib, curled down to its tip, each normal whole and off its face
        val (bv, bi) = TreeModel.blade(7)
        assertEquals(7 * 3 * 6, bv.size); assertEquals(6 * 2 * 6, bi.size)
        assertTrue(bi.all { it in 0 until bv.size / 6 })
        for (k in 0 until bv.size / 6) {
            val o = 6 * k
            val m = Math.sqrt((bv[o + 3] * bv[o + 3] + bv[o + 4] * bv[o + 4] + bv[o + 5] * bv[o + 5]).toDouble())
            assertEquals(1.0, m, 1e-5)
            assertTrue("normal $k faces off the blade", bv[o + 5] > 0.5f)
        }
        // pointed at both ends, widest below its middle, its edges above its midrib, its tip below its stem
        assertEquals(0.0, TreeModel.bladeWidth(0.0), 1e-12); assertEquals(0.0, TreeModel.bladeWidth(1.0), 1e-12)
        val widest = (0..100).maxByOrNull { TreeModel.bladeWidth(it / 100.0) }!!
        assertTrue("widest at $widest", widest in 30..50)
        val mid = 3 * 3 * 6
        assertTrue(bv[mid + 2] > bv[mid + 6 + 2]); assertTrue(bv[(6 * 3 + 1) * 6 + 2] < bv[1 * 6 + 2])
    }

    // the owner's review of 19 Nov: a believable tree, one leaf an idea; the frame's cost reported
    @Test fun aLeafAnIdeaAndTheTrianglesAFrameDraws() {
        val nodes = wide(30, 400)
        val m = TreeModel(nodes, TreeLayout3D(nodes), TreeColors(false), GraphicsTier.HIGH)
        val leafNodes = (0 until m.leafCount).map { m.leaves[it * TreeModel.LEAF_FLOATS + 11].toInt() }
        assertEquals(leafNodes.size, leafNodes.toSet().size)
        assertEquals((1 until nodes.size).filter { nodes.childCount[it] == 0 }.toSet(), leafNodes.toSet())
        assertEquals(m.branchCount * 12 * 7 * 2 + (m.leafCount + m.fillerCount) * 6 * 4, m.triangles)
        assertEquals(m.triangles, TreeModel.tube(m.segments, m.rings).second.size / 3 * m.branchCount + TreeModel.blade(m.bladeRows).second.size / 3 * m.blades)
        val s = TreeModel(nodes, m.layout, TreeColors(false), GraphicsTier.SMOOTH)
        assertEquals(s.branchCount * 7 * 5 * 2 + (s.leafCount + s.fillerCount) * 4 * 4, s.triangles)
        println("Tree triangles, High: ${m.triangles} (${m.branchCount} branches, ${m.leafCount} ideas' leaves, ${m.fillerCount} fillers); " +
            "Smooth: ${s.triangles} (${s.fillerCount} fillers); 2 draws a frame")
    }

    // the lead's review of 19 Nov ("a bare winter tree"): round each idea's leaf a few smaller filler
    // leaves on its stalk, drawn only, after the ideas' leaves, marked, capped; the ideas still counted exactly
    @Test fun fillerLeavesAreDrawnOnlyAndCapped() {
        val nodes = wide(30, 400)
        val m = TreeModel(nodes, TreeLayout3D(nodes), TreeColors(false), GraphicsTier.HIGH)
        val l = m.layout
        assertEquals(m.leafCount * TreeModel.FILLERS_HIGH, m.fillerCount)
        for (f in 0 until m.fillerCount) {
            val o = (m.leafCount + f) * TreeModel.LEAF_FLOATS
            val node = m.leaves[o + 11]; val i = node.toInt()
            assertEquals("filler $f marked", TreeModel.FILLER_MARK, node - i, 1e-6f)
            assertEquals("filler $f is an idea's", 0, nodes.childCount[i]); assertTrue(l.leaf[i] > 0)
            // smaller than its idea's leaf, at most its stalk's length and its own blade from the stalk's base
            assertTrue(m.leaves[o + 6] < l.leaf[i] && m.leaves[o + 6] >= l.leaf[i] * TreeModel.FILLER_SIZE_LEAST - 1e-6)
            val reach = Math.sqrt((0..2).sumOf { a -> (m.leaves[o + a] - l.base[3 * i + a]).let { it * it } })
            assertTrue("filler $f within its idea's reach", reach <= l.length[i] + m.leaves[o + 6] + 1e-5)
            assertTrue(kotlin.math.abs(m.leaves[o + 7]) <= TreeModel.ROLL + 1e-6)
            val axis = Math.sqrt((3..5).sumOf { a -> (m.leaves[o + a] * m.leaves[o + a]).toDouble() })
            assertEquals(1.0, axis, 1e-5)
        }
        // no filler stands in for an idea: the ideas' leaves, read up to leafCount, are each idea once
        val ideas = (0 until m.leafCount).map { m.leaves[it * TreeModel.LEAF_FLOATS + 11] }
        assertTrue(ideas.all { it == kotlin.math.floor(it) })
        // a big vault: the fillers capped by tier
        val big = wide(60, TreeModel.SMOOTH_CAP + 500)
        val bl = TreeLayout3D(big)
        assertEquals(TreeModel.FILLER_CAP_SMOOTH, TreeModel(big, bl, TreeColors(true), GraphicsTier.SMOOTH).fillerCount)
        assertEquals(TreeModel.FILLER_CAP_HIGH, TreeModel(big, bl, TreeColors(true), GraphicsTier.HIGH).fillerCount)
        // seeded as iOS's: suite treemodel3d pins the first filler the same
        val o = m.leafCount * TreeModel.LEAF_FLOATS
        println("First filler: " + (0..11).joinToString(" ") { "%.6f".format(m.leaves[o + it]) })
    }

    // seeded the same as iOS's (suite treemodel3d holds these numbers too)
    @Test fun theSeededNumbersAreIosOwn() {
        assertEquals(0.0, TreeModel.hash(0, 0), 0.0)
        val want = doubleArrayOf(TreeModel.hash(1, 1), TreeModel.hash(7, 2), TreeModel.hash(12345, 3))
        assertEquals(0.9573098756, want[0], 1e-9)
        assertEquals(0.3039142611, want[1], 1e-9)
        assertEquals(0.4494442665, want[2], 1e-9)
        for (i in 0 until 1000) assertTrue(TreeModel.hash(i, 1) in 0.0..1.0)
    }

    @Test fun coloursAreTheBarkAndLeafRamps() {
        val nodes = wide(3, 10)
        val c = TreeColors(false)
        val m = TreeModel(nodes, TreeLayout3D(nodes), c, GraphicsTier.HIGH)
        val trunk = c.wood3d[NestNodes.TRUNK]
        assertEquals(((trunk shr 16) and 255) / 255f, m.branches[11], 1e-6f)
        // the bark brown leaning a fifth to its folder's colour; a top folder's leaves in its colour's hue at
        // the green's lightness (the lead's review of 19 Nov), each with the green's contrast with the sky
        for (dark in listOf(false, true)) {
            val k = TreeColors(dark)
            assertEquals(k.wood, k.wood3d[NestNodes.TRUNK])
            assertEquals(TreeColors.mix(k.wood, TreeColors.TONE[0], 0.2), k.wood3d[0])
            // pinned, as iOS's suite treemodel3d pins the same
            assertEquals(if (dark) 0x4DD3C4 else 0x218379, k.leaf3d[0])
            assertEquals(k.foliage, k.leaf3d[NestNodes.TRUNK]); assertEquals(k.foliage, k.leaf3d[NestNodes.LOOSE])
            for (g in 0 until NestNodes.TONES) {
                val leaf = k.leaf3d[g]
                assertTrue("group $g: ${Integer.toHexString(leaf)} against the sky", contrast(leaf, k.sky) >= 3.5)
                val most = maxOf((leaf shr 16) and 255, (leaf shr 8) and 255, leaf and 255); val least = minOf((leaf shr 16) and 255, (leaf shr 8) and 255, leaf and 255)
                assertTrue("group $g: coloured, not grey", most - least >= 60)
                for (h in 0 until g) assertTrue("groups $h and $g told apart", k.leaf3d[h] != leaf)
            }
            // brown: red over green over blue; green: green the most
            assertTrue(((k.wood shr 16) and 255) > ((k.wood shr 8) and 255) && ((k.wood shr 8) and 255) > (k.wood and 255))
            assertTrue(((k.foliage shr 8) and 255) > ((k.foliage shr 16) and 255) && ((k.foliage shr 8) and 255) > (k.foliage and 255))
        }
        assertEquals(((c.sky shr 8) and 255) / 255f, m.sky[1], 1e-6f)
    }

    /** The WCAG contrast of two colours. */
    private fun contrast(a: Int, b: Int): Double {
        fun lin(c: Int): Double { val x = c / 255.0; return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        fun y(c: Int) = 0.2126 * lin((c shr 16) and 255) + 0.7152 * lin((c shr 8) and 255) + 0.0722 * lin(c and 255)
        val p = y(a); val q = y(b)
        return (maxOf(p, q) + 0.05) / (minOf(p, q) + 0.05)
    }

    // the lead's review of the third pass: flown in, the part and all under it draw in full colour and
    // only the rest fades; a chosen idea or a hidden one keeps its own state
    @Test fun flownInThePartsInstancesDrawInFullColourAndOnlyTheRestFades() {
        val nodes = wide(30, 400)
        val m = TreeModel(nodes, TreeLayout3D(nodes), TreeColors(false), GraphicsTier.HIGH)
        val focus = (0 until nodes.childCount[0]).map { nodes.child(0, it) }.first { nodes.folder[it] && nodes.childCount[it] > 2 }
        val chosen = (1 until nodes.size).first { !nodes.folder[it] && nodes.within(focus, it) }
        val gone = (1 until nodes.size).first { !nodes.folder[it] && nodes.within(focus, it) && it != chosen }
        fun state(i: Int) = TreeState.of(nodes, i, i == gone, chosen, false, focus)
        val drawn = (0 until m.branchCount).map { m.branches[it * TreeModel.BRANCH_FLOATS + 11].toInt() } +
            (0 until m.leafCount).map { m.leaves[it * TreeModel.LEAF_FLOATS + 11].toInt() }
        val inside = drawn.filter { nodes.within(focus, it) }
        assertTrue("the part has instances (${inside.size})", inside.size > 10)
        for (i in inside) {
            val want = when (i) { gone -> TreeState.HIDDEN; chosen -> TreeState.CHOSEN; else -> TreeState.NONE }
            assertEquals("node $i under the part", want, state(i))
        }
        assertEquals(TreeState.NONE, state(focus))
        for (i in drawn.filter { !nodes.within(focus, it) }) assertEquals("node $i outside the part", TreeState.FADED, state(i))
        // the whole tree fades nothing
        assertTrue(drawn.all { TreeState.of(nodes, it, false, -1, false, 0) == TreeState.NONE })
    }
}
