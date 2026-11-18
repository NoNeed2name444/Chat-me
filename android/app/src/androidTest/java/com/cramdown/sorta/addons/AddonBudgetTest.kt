package com.cramdown.sorta.addons

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S07b A5: the largest batch (100 writes and 20 captures) applied on the main thread to a 2,000-idea
 * book, 20 times: the median is at most 4.2 ms, half of a 120 fps frame, the book's own save left out.
 * iOS: AddonBatchTests.swift.
 */
@RunWith(AndroidJUnit4::class)
class AddonBudgetTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

    private fun notes() = (1..2000).map { n ->
        Note(id = id(n), title = "Idea $n", body = "The words of idea $n", folderId = if (n % 3 == 0) id(9001) else null,
            links = if (n > 1) listOf(id(n - 1)) else emptyList(), tags = listOf("tag${n % 7}"), createdAt = n.toLong(), updatedAt = n.toLong())
    }

    private fun largest(): List<AddonOp> {
        val ops = ArrayList<AddonOp>()
        for (k in 1..20) ops.add(AddonOp.Capture(id(5000 + k), "Captured $k", "Its words", 10.0 * k, 5.0 * k))
        for (k in 1..40) ops.add(AddonOp.Tag(id(k * 40), listOf("added", "more")))
        for (k in 1..30) ops.add(AddonOp.Link(id(k * 50), id(2000 - k * 50 + 1)))
        for (k in 1..30) ops.add(AddonOp.File(id(k * 60 + 1), id(9001)))
        return ops
    }

    @Test fun theLargestBatchAppliesWithinHalfAFrameOnTheMainThread() {
        val ops = largest()
        assertEquals(120, ops.size)
        val folders = listOf(NoteFolder(id = id(9001), name = "Kept"))
        val start = notes()
        val times = ArrayList<Long>()
        var saves = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // a few applies first, as the app's own code is warm by the time a person runs a command
            repeat(5) { IdeaBook(start, folders).applyAddonBatch(ops, 5_000) }
            repeat(20) {
                val book = IdeaBook(start, folders, onChange = { _, _ -> saves++ })
                val (applied, reshaped, nanos) = book.applyAddonBatch(ops, 5_000)
                assertEquals(20, applied.added.size)
                assertTrue(applied.changed.size >= 90 && applied.skipped.isEmpty())
                assertEquals(2020, book.notes.size)
                book.undo(reshaped!!)
                assertEquals(start, book.notes)
                times.add(nanos)
            }
        }
        assertEquals("each batch saves once, and its Undo once", 40, saves)
        val median = times.sorted().let { (it[9] + it[10]) / 2.0 } / 1e6
        Log.i("SortaAddons", "apply median %.3f ms, slowest %.3f ms, on 2,000 ideas".format(median, times.max() / 1e6))
        assertTrue("the median apply is $median ms", median <= 4.2)
    }
}
