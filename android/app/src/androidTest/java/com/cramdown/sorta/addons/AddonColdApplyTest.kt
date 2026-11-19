package com.cramdown.sorta.addons

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S07b repair 1, S2: the first apply after the app starts, with no warm-up. The workflow runs this class in
 * an instrumentation of its own (a new process), before any other add-on test, so nothing here has run
 * before: no apply, no IdeaBook. As in the app (AddonCenter), the largest batch is worked out on another
 * thread from the book's lists, and the main thread takes the result. The bound is the same half frame the
 * warm median is held to: 4.2 ms on the main thread, on 2,000 ideas, the book's own save left out.
 *
 * The other path, where the person changed the book while the add-on ran and the main thread applies the
 * batch itself, is timed too and logged, cold as it can be after one apply on another thread. It has no
 * bound of its own here: its warm median is AddonBudgetTest's.
 */
@RunWith(AndroidJUnit4::class)
class AddonColdApplyTest {
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

    @Test fun theFirstApplyAfterStartTakesAtMostHalfAFrameOfTheMainThread() {
        val ops = largest()
        val folders = listOf(NoteFolder(id = id(9001), name = "Kept"))
        val start = notes()
        val book = IdeaBook(start, folders)
        // the add-on's thread: the batch worked out from the book's own lists
        var prepared: AddonApply.Prepared? = null
        var offMain = 0L
        val worker = Thread {
            val t = System.nanoTime()
            prepared = AddonApply.prepare(ops, book.notes, book.folders, 5_000)
            offMain = System.nanoTime() - t
        }
        worker.start()
        worker.join()
        assertNotNull(prepared)
        var first = 0L
        var fallback = 0L
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(prepared!!.fits(book.notes, book.folders, 5_000))
            val (applied, reshaped, nanos) = book.applyAddonBatch(ops, 5_000, prepared)
            first = nanos
            assertEquals(20, applied.added.size)
            assertTrue(applied.changed.size >= 90 && applied.skipped.isEmpty())
            assertEquals(2020, book.notes.size)
            book.undo(reshaped!!)
            assertEquals(start, book.notes)
            // the book changed since: what was worked out no longer fits, and the main thread applies the batch itself
            val changed = IdeaBook(start, folders)
            changed.applyAddonBatch(listOf(AddonOp.Tag(id(7), listOf("mine"))), 5_000)
            assertTrue(!prepared!!.fits(changed.notes, changed.folders, 5_000))
            fallback = changed.applyAddonBatch(ops, 5_000, prepared).third
            assertEquals(2020, changed.notes.size)
        }
        Log.i("SortaAddons", "cold apply %.3f ms on the main thread (worked out in %.3f ms on another thread), on 2,000 ideas; the main thread applying it itself, second apply of the process: %.3f ms"
            .format(first / 1e6, offMain / 1e6, fallback / 1e6))
        assertTrue("the first apply took ${first / 1e6} ms of the main thread", first / 1e6 <= 4.2)
    }
}
