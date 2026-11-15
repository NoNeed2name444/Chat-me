package com.cramdown.sorta.ui.map

import java.util.concurrent.atomic.AtomicBoolean

/**
 * How the bodies the map drew reach the page (perf, 15 Nov). The renderer hands them over on its own
 * thread every frame; the page used to take each frame's in a post to the main thread, and every one
 * built the whole map page again, 60 to 120 times a second, though while nothing follows the bodies
 * (no name, no ring, no drop) nothing on screen reads them.
 *
 * Now the latest are always here for a touch to read ([nodes]), and the page takes them:
 * - every frame while [eager]: a name, a ring or the plain theme's names follow the bodies;
 * - every frame where [everyFrame] (the tests' records read them, as before);
 * - else at most every [gapNanos], the last frame always handed on after a quiet [gapNanos], so the
 *   page is never more than that behind.
 *
 * [offer] runs on the renderer's thread and says when to [take]; [take] runs on the main thread.
 */
class NodesFeed(private val everyFrame: Boolean, private val gapNanos: Long = GAP_NANOS) {
    @Volatile var nodes: List<ScreenNode> = emptyList(); private set
    @Volatile var pose: List<Float> = emptyList(); private set
    /** Set by the page: something on screen follows the bodies. */
    @Volatile var eager: Boolean = false
    private val waiting = AtomicBoolean(false)
    // the renderer's thread only: when the page last took, or will take, the bodies
    private var last = Long.MIN_VALUE / 2

    /** Keeps this frame's [nodes] and [pose]; returns [NOW], a delay in nanoseconds, or [LATER] (one already waits). */
    fun offer(nodes: List<ScreenNode>, pose: List<Float>, now: Long): Long {
        this.nodes = nodes; this.pose = pose
        val wait = wait(everyFrame || eager, now - last, waiting.get(), gapNanos)
        if (wait != LATER) { last = now + wait; if (wait > 0) waiting.set(true) }
        return wait
    }

    /** On the main thread, when a post runs: what to show, the latest. The wait ends before they are read, so none is lost. */
    fun take(): Pair<List<ScreenNode>, List<Float>> {
        waiting.set(false)
        return nodes to pose
    }

    /**
     * When the page takes the map's drawn record ([MapDrawn]), which the renderer hands over whenever
     * any of its fields changes, every frame while the map moves. Where [everyFrame] (the tests'
     * records), every one; else each time its counts move: they stop at [MapDrawn.ENOUGH], so a few
     * times a context, enough for the page's first drawn frame and the frame tests' wait for the map.
     * The renderer's thread only.
     */
    class Drawn(private val everyFrame: Boolean) {
        private var counts = -1

        fun takes(d: MapDrawn): Boolean {
            val now = d.frames * (MapDrawn.ENOUGH + 1) + d.bodyFrames
            if (!everyFrame && now == counts) return false
            counts = now
            return true
        }
    }

    companion object {
        /** A quarter second: the readout's own pace, so a page that follows nothing is built again no oftener than it was. */
        const val GAP_NANOS = 250_000_000L
        const val NOW = 0L
        const val LATER = -1L

        /**
         * The rule: [NOW] every frame while [eager]; else [LATER] while a post waits; else [NOW] once
         * [gap] has passed since the last, or the rest of [gap].
         */
        fun wait(eager: Boolean, since: Long, waiting: Boolean, gap: Long): Long = when {
            eager -> NOW
            waiting -> LATER
            since >= gap -> NOW
            else -> gap - since
        }
    }
}
