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
        assertEquals(m.leafCount * TreeModel.LEAF_FLOATS, m.leaves.size)
        // the trunk first, from the origin up
        assertEquals(0f, m.branches[11], 0f)
        assertEquals(1f, m.branches[4], 1e-6f)
    }

    @Test fun theSmoothTierKeepsTheBigBranches() {
        val nodes = wide(60, TreeModel.SMOOTH_CAP + 500)
        val l = TreeLayout3D(nodes)
        val m = TreeModel(nodes, l, TreeColors(true), GraphicsTier.SMOOTH)
        assertTrue(m.capped)
        assertEquals(TreeModel.SMOOTH_CAP, m.branchCount)
        val kept = (0 until m.branchCount).map { m.branches[it * TreeModel.BRANCH_FLOATS + 11].toInt() }.toSet()
        for (i in 0 until nodes.size) if (nodes.folder[i]) assertTrue("folder $i kept", i in kept)
        assertTrue(TreeModel(nodes, l, TreeColors(true), GraphicsTier.HIGH).branchCount > m.branchCount)
        assertTrue(m.segments < TreeModel(nodes, l, TreeColors(true), GraphicsTier.HIGH).segments)
    }

    @Test fun theMeshesAreWhole() {
        val (tv, ti) = TreeModel.tube(12)
        assertEquals(13 * 2 * 3, tv.size); assertEquals(12 * 6, ti.size)
        assertTrue(ti.all { it in 0 until tv.size / 3 })
        val (bv, bi) = TreeModel.ball(8, 12)
        assertTrue(bi.all { it in 0 until bv.size / 3 })
        for (k in 0 until bv.size / 3) assertEquals(1.0, Math.sqrt((bv[3 * k] * bv[3 * k] + bv[3 * k + 1] * bv[3 * k + 1] + bv[3 * k + 2] * bv[3 * k + 2]).toDouble()), 1e-5)
    }

    @Test fun coloursAreTheBarkAndLeafRamps() {
        val nodes = wide(3, 10)
        val c = TreeColors(false)
        val m = TreeModel(nodes, TreeLayout3D(nodes), c, GraphicsTier.HIGH)
        val trunk = c.barkRamp[NestNodes.TRUNK][8]
        assertEquals(((trunk shr 16) and 255) / 255f, m.branches[8], 1e-6f)
        assertEquals(((c.sky shr 8) and 255) / 255f, m.sky[1], 1e-6f)
    }
}
