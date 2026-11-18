package com.cramdown.sorta.addons

import com.cramdown.sorta.ideas.IdeaBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07b A5: a call's writes through IdeaBook, as one change and one Undo step. iOS: AddonBatchTests.swift. */
class AddonBatchTest {
    private val now = 1_793_523_600_000
    private var saves = 0
    private fun book(): IdeaBook {
        val (notes, folders) = AddonTestFiles.store()
        return IdeaBook(notes, folders, now = { now }, onChange = { _, _ -> saves++ })
    }
    private fun id(n: Int) = AddonTestFiles.id(n)

    private val ops = listOf(
        AddonOp.Capture(id(7001), "Captured", "Its words", 12.0, 34.0),
        AddonOp.Tag(id(101), listOf("new", "green", "new")),
        AddonOp.Untag(id(101), listOf("green")),
        AddonOp.Link(id(103), id(7001)),
        AddonOp.Unlink(id(101), id(102)),
        AddonOp.File(id(104), id(903)),
        AddonOp.File(id(105), null),
    )

    @Test fun theBatchIsOneChangeOneSaveAndOneUndoStepThatRestoresTheBookExactly() {
        val b = book()
        val before = b.notes
        val (applied, reshaped, _) = b.applyAddonBatch(ops)
        assertEquals("the batch saves once", 1, saves)
        assertEquals(listOf(id(7001)), applied.added)
        assertTrue(applied.changed.containsAll(listOf(id(101), id(103), id(104))))
        assertTrue(applied.skipped.isEmpty())
        val made = b.note(id(7001))!!
        assertEquals(listOf("Captured", "Its words", null, 12.0, 34.0, now, now), listOf(made.title, made.body, made.folderId, made.boardX, made.boardY, made.createdAt, made.updatedAt))
        assertEquals(listOf(id(7001)), b.note(id(103))!!.links.filter { it == id(7001) })
        assertTrue("new" in b.note(id(101))!!.tags && "green" !in b.note(id(101))!!.tags)
        assertEquals(id(903), b.note(id(104))!!.folderId)
        for (c in applied.changed) assertEquals(now, b.note(c)!!.updatedAt)
        // what did not change keeps its own time
        val untouched = before.first { it.id !in applied.changed }
        assertEquals(untouched, b.note(untouched.id))
        assertNotNull(reshaped)
        b.undo(reshaped!!)
        assertEquals("Undo restores the book exactly", before, b.notes)
        assertEquals(2, saves)
    }

    @Test fun anIdeaOrFolderGoneSinceTheSnapshotIsSkippedAndListed() {
        val b = book()
        val gone = id(102)
        b.delete(gone)
        saves = 0
        val (applied, _, _) = b.applyAddonBatch(listOf(AddonOp.Tag(gone, listOf("x")), AddonOp.Link(id(101), gone),
            AddonOp.File(id(101), id(999)), AddonOp.Tag(id(103), listOf("kept"))))
        assertEquals(listOf(gone, id(999)).sorted(), applied.skipped)
        assertEquals(listOf(id(103)), applied.changed)
        assertEquals(1, saves)
    }

    @Test fun aBatchThatChangesNothingSavesNothingAndHasNoUndoStep() {
        val b = book()
        val had = b.note(id(101))!!.tags
        val (applied, reshaped, _) = b.applyAddonBatch(listOf(AddonOp.Tag(id(101), had), AddonOp.Untag(id(101), listOf("absent")), AddonOp.File(id(105), b.note(id(105))!!.folderId)))
        assertEquals(AddonApplied(), applied)
        assertNull(reshaped)
        assertEquals(0, saves)
        assertTrue(b.applyAddonBatch(emptyList()).second == null)
    }

    @Test fun aLinkIsKeptOnceWhicheverWayItPoints() {
        val b = book()
        b.applyAddonBatch(listOf(AddonOp.Link(id(105), id(107))))
        val (again, _, _) = b.applyAddonBatch(listOf(AddonOp.Link(id(107), id(105)), AddonOp.Link(id(105), id(107))))
        assertTrue(again.changed.isEmpty())
        val (cut, _, _) = b.applyAddonBatch(listOf(AddonOp.Unlink(id(107), id(105))))
        assertEquals(listOf(id(105)), cut.changed)
        assertTrue(!b.isLinked(id(105), id(107)))
    }
}
