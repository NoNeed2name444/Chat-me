package com.cramdown.sorta.map

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S05e: the memory of the scene on screen (LinkMemory, iOS GraphMemory's links), which decides
 * which links of a rebuilt scene grow in and which shrink away. Made-up notes.
 */
class LinkMemoryTest {
    private val ids = listOf("seed", "root", "leaf", "bud")
    // seed-root, root-leaf, leaf-bud
    private val first = intArrayOf(0, 1, 1, 2, 2, 3)

    @Before fun forgetBefore() = LinkMemory.clear()
    @After fun forgetAfter() = LinkMemory.clear()

    @Test fun withNoSceneBeforeEveryLinkIsFresh() {
        val recall = LinkMemory.recall(ids, first, lively = true)
        assertNull("nothing to compare with", recall.fresh)
        assertTrue(recall.gone.isEmpty())
        assertTrue((0 until 3).all { recall.isFresh(ids[first[2 * it]], ids[first[2 * it + 1]]) })
        // the pure form says the same
        assertNull(LinkMemory.recall(null, emptyMap(), ids, first, true).fresh)
        val growth = LinkGrowth.of(ids, first, recall, lively = true)
        assertTrue("every link grows from nothing", (0 until growth.links).all { growth.grow[it] == 0f })
        assertEquals(0, growth.ghosts)
    }

    @Test fun oneLinkAddedAndOneRemovedAreExactlyFreshAndGone() {
        LinkMemory.remember(1, ids, first)
        // root-leaf removed, seed-bud added
        val second = intArrayOf(0, 1, 2, 3, 3, 0)
        val recall = LinkMemory.recall(ids, second, lively = true)
        assertEquals(setOf(LinkMemory.key("bud", "seed")), recall.fresh)
        assertEquals(listOf("root" to "leaf"), recall.gone)
        assertTrue("either way round", recall.isFresh("seed", "bud") && recall.isFresh("bud", "seed"))
        assertFalse(recall.isFresh("seed", "root"))
        assertFalse(recall.isFresh("leaf", "bud"))
        val growth = LinkGrowth.of(ids, second, recall, lively = true)
        assertEquals(listOf(1f, 1f, 0f), growth.grow.take(3))
        assertEquals("the gone link shrinks from whole", 1, growth.ghosts)
        assertEquals(1f, growth.grow[3])
        // its ends as this scene's indices, the sending end first
        assertEquals(listOf(1, 2), growth.ghostEnds.toList())
    }

    @Test fun aRemovedLinkToADeletedNoteIsNotKept() {
        LinkMemory.remember(1, ids, first)
        // leaf deleted, with its two links
        val left = listOf("seed", "root", "bud")
        val recall = LinkMemory.recall(left, intArrayOf(0, 1), lively = true)
        assertTrue("both gone links touched the deleted note: ${recall.gone}", recall.gone.isEmpty())
        assertEquals(emptySet<String>(), recall.fresh)
    }

    @Test fun motionOffDrawsEverythingWhole() {
        LinkMemory.remember(1, ids, first)
        val recall = LinkMemory.recall(ids, intArrayOf(0, 3), lively = false)
        assertNull(recall.fresh)
        assertTrue(recall.gone.isEmpty())
        val growth = LinkGrowth.of(ids, intArrayOf(0, 3), recall, lively = false)
        assertEquals(1f, growth.shown(0))
        assertEquals(0, growth.ghosts)
    }

    @Test fun leavingTheScreenForgetsOnlyTheSceneShown() {
        LinkMemory.remember(1, ids, first)
        LinkMemory.remember(2, ids, first)
        // the first scene's view leaving after the second was shown keeps the second
        LinkMemory.forget(1)
        assertEquals(emptySet<String>(), LinkMemory.recall(ids, first, true).fresh)
        // the map leaving the screen: a map opened again grows every link
        LinkMemory.forget(2)
        assertNull(LinkMemory.recall(ids, first, true).fresh)
    }
}
