package com.cramdown.sorta.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map hands its bodies to the page at most every quarter second while nothing on screen follows
 * them (perf, 15 Nov): the page is no longer built again on every frame the map draws. These fail if
 * a frame of a still page posts again, or if the last frame could be lost.
 */
class NodesFeedTest {
    private val gap = NodesFeed.GAP_NANOS
    private val frame = 8_333_333L // 120 Hz
    private fun nodes(k: Int) = listOf(ScreenNode(k, k.toFloat(), 0f, 10f))

    @Test fun theRule() {
        assertEquals(NodesFeed.NOW, NodesFeed.wait(eager = true, since = 0, waiting = true, gap = gap))
        assertEquals(NodesFeed.LATER, NodesFeed.wait(eager = false, since = gap * 10, waiting = true, gap = gap))
        assertEquals(NodesFeed.NOW, NodesFeed.wait(eager = false, since = gap, waiting = false, gap = gap))
        assertEquals(gap - frame, NodesFeed.wait(eager = false, since = frame, waiting = false, gap = gap))
    }

    // a second of 120 Hz frames with nothing following the bodies: at most five posts (one each
    // quarter second, the first at once), where every frame posted before
    @Test fun aStillPageIsTakenAQuarterSecondApart() {
        val feed = NodesFeed(everyFrame = false)
        var posts = 0
        var due = Long.MAX_VALUE
        for (f in 0 until 120) {
            val now = f * frame
            if (now >= due) { feed.take(); due = Long.MAX_VALUE }
            when (val wait = feed.offer(nodes(f), emptyList(), now)) {
                NodesFeed.NOW -> { posts++; feed.take() }
                NodesFeed.LATER -> {}
                else -> { posts++; due = now + wait }
            }
        }
        assertTrue("posts $posts", posts <= 5)
    }

    // whatever the frames' timing, the page takes the last frame's bodies: a post waits whenever one is newer
    @Test fun theLastFrameIsAlwaysTaken() {
        val feed = NodesFeed(everyFrame = false)
        var shown = -1
        var due = Long.MAX_VALUE
        val times = longArrayOf(0, 1, 2, frame, 3 * frame, gap - 1, gap + 5, gap + 6, 3 * gap, 3 * gap + frame)
        for ((f, now) in times.withIndex()) {
            if (now >= due) { shown = feed.take().first.single().index; due = Long.MAX_VALUE }
            when (val wait = feed.offer(nodes(f), emptyList(), now)) {
                NodesFeed.NOW -> shown = feed.take().first.single().index
                NodesFeed.LATER -> assertTrue("a post waits", due != Long.MAX_VALUE)
                else -> due = now + wait
            }
        }
        if (due != Long.MAX_VALUE) shown = feed.take().first.single().index
        assertEquals(times.size - 1, shown)
    }

    // a name, a ring or the plain theme's names follow the bodies: every frame is taken, as the tests' records are
    @Test fun eagerAndTestsTakeEveryFrame() {
        for (feed in listOf(NodesFeed(everyFrame = true), NodesFeed(everyFrame = false).also { it.eager = true })) {
            for (f in 0 until 30) assertEquals(NodesFeed.NOW, feed.offer(nodes(f), emptyList(), f * frame))
        }
    }

    // a lively map changes its drawn record every frame; a released page takes it only as its counts
    // move, so a few times, and the last it takes has the counts the frame tests wait for
    @Test fun aReleasedPageTakesTheDrawnRecordAsItsCountsMove() {
        val take = NodesFeed.Drawn(everyFrame = false)
        var record = MapDrawn()
        var taken = 0
        var last: MapDrawn? = null
        for (f in 0 until 120) {
            // bodies from the second frame on, and the frame's own fields changing every frame
            record = record.after(bodiesNow = if (f == 0) 0 else 40, elapsedMs = f * 8L).copy(width = 1000 + f)
            if (take.takes(record)) { taken++; last = record }
        }
        assertTrue("taken $taken", taken in 1..2 * MapDrawn.ENOUGH + 1)
        assertEquals(MapDrawn.ENOUGH, last?.frames)
        assertEquals(MapDrawn.ENOUGH, last?.bodyFrames)
    }

    @Test fun theTestsTakeEveryDrawnRecord() {
        val take = NodesFeed.Drawn(everyFrame = true)
        var record = MapDrawn()
        for (f in 0 until 30) {
            record = record.after(bodiesNow = 40, elapsedMs = f * 8L).copy(width = 1000 + f)
            assertTrue("frame $f", take.takes(record))
        }
    }

    // the touches read the bodies the renderer drew last, whether or not the page has taken them
    @Test fun touchesReadTheLatest() {
        val feed = NodesFeed(everyFrame = false)
        feed.offer(nodes(1), listOf(1f), 0)
        feed.offer(nodes(2), listOf(2f), frame)
        assertEquals(2, feed.nodes.single().index)
        assertEquals(listOf(2f), feed.pose)
    }
}
