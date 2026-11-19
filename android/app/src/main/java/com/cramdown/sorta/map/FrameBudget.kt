package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The adaptive frame budget (plan 4c and 4e): the frame rate is the display's own, and a
 * device that cannot keep it draws less, never slower. [detail] scales how far each level
 * of detail reaches: a chunk opens into its nodes once it covers [openPixels] on screen, so
 * a slow phone sees summaries sooner and a fast tablet later. Changes are small steps, and
 * the map morphs between levels, so a budget change never pops.
 */
class FrameBudget(refreshHz: Float, initialDetail: Float = 0.6f) {
    var refreshHz = refreshHz
        set(value) { field = max(value, 1f) }
    val targetMs get() = 1000.0 / refreshHz

    /** 1 is the reference reach; lower opens fewer chunks. */
    var detail = initialDetail.coerceIn(MIN_DETAIL, MAX_DETAIL); private set

    /** On-screen radius (pixels) at which a chunk starts to open into its nodes. */
    val openPixels get() = BASE_OPEN_PIXELS / detail

    /** Most nodes drawn one by one in a frame, a backstop for the worst views. */
    val maxDetailNodes get() = (BASE_DETAIL_NODES * detail * detail).toInt().coerceIn(20_000, 4_000_000)

    /** Link indices streamed to the GPU per frame. */
    val uploadBudget get() = (120_000 * detail).toInt().coerceIn(16_384, 400_000)

    private var slowStreak = 0
    private var fastStreak = 0

    /**
     * One finished frame: [intervalMs] since the previous vsync this map drew on, and
     * [workMs], the time spent preparing and submitting it. A frame that missed a refresh,
     * or work over [HEAVY_SHARE] of the frame, cuts detail; a steady run of easy frames
     * (work under [EASY_SHARE]) raises it.
     */
    fun onFrame(intervalMs: Double, workMs: Double) {
        val target = targetMs
        val missed = intervalMs > target * 1.45
        val heavy = workMs > target * HEAVY_SHARE
        if (missed || heavy) {
            fastStreak = 0
            if (++slowStreak >= 2) { detail = max(MIN_DETAIL, detail * 0.88f); slowStreak = 0 }
        } else {
            slowStreak = 0
            val easy = intervalMs < target * 1.2 && workMs < target * EASY_SHARE
            if (easy && ++fastStreak >= 24) { detail = min(MAX_DETAIL, detail * 1.05f); fastStreak = 0 }
            if (!easy) fastStreak = 0
        }
    }

    companion object {
        /**
         * The floor keeps a chunk opening by 120 pixels, so the slowest device still sees the
         * map's structure (hundreds of summaries) and never one dot for the whole library.
         */
        const val MIN_DETAIL = 0.3f
        /**
         * The share of a refresh the map's work may take (perf, 15 Nov: the owner's 120 a
         * second): a little over half, 4.6 ms of 8.3 at 120 Hz, so the frame keeps room for
         * the page over the map and the system; as iOS's GraphPerfFrameBudget.share.
         */
        const val HEAVY_SHARE = 0.55
        /** Work under this share of a refresh is easy: detail may grow (2.5 ms at 120 Hz). */
        const val EASY_SHARE = 0.3
        const val MAX_DETAIL = 4f
        const val BASE_OPEN_PIXELS = 36f
        const val BASE_DETAIL_NODES = 600_000
    }
}

/** Frame times summed up for the on-screen readout. */
class FrameStats(private val capacity: Int = 240) {
    private val intervals = DoubleArray(capacity)
    private val work = DoubleArray(capacity)
    private val stamps = LongArray(capacity)
    private var next = 0
    var count = 0; private set

    fun add(timeNanos: Long, intervalMs: Double, workMs: Double) {
        stamps[next] = timeNanos; intervals[next] = intervalMs; work[next] = workMs
        next = (next + 1) % capacity; count = min(count + 1, capacity)
    }

    /** Frames a second over the frames drawn in the last [windowNanos] before [nowNanos]. */
    fun fps(nowNanos: Long, windowNanos: Long = 500_000_000L): Double {
        if (count == 0) return 0.0
        val newest = stamps[(next - 1 + capacity) % capacity]
        var frames = 0; var oldest = newest
        for (k in 0 until count) {
            val t = stamps[(next - 1 - k + capacity) % capacity]
            if (nowNanos - t > windowNanos) break
            frames++; oldest = t
        }
        if (frames < 2 || newest == oldest) return 0.0
        return (frames - 1) * 1e9 / (newest - oldest).toDouble()
    }

    fun medianInterval(): Double = percentile(intervals, 0.5)
    fun medianWork(): Double = percentile(work, 0.5)
    fun p95Work(): Double = percentile(work, 0.95)

    private fun percentile(source: DoubleArray, q: Double): Double {
        if (count == 0) return 0.0
        val sorted = DoubleArray(count) { k -> source[(next - 1 - k + capacity) % capacity] }.also { it.sort() }
        val rank = kotlin.math.ceil(q * count).toInt().coerceIn(1, count)
        return sorted[rank - 1]
    }
}

/** A display mode as Android reports it. */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshHz: Float)

object RefreshRate {
    fun maxHz(modes: List<DisplayModeInfo>, fallback: Float = 60f) = modes.maxOfOrNull { it.refreshHz } ?: fallback

    /**
     * The fastest mode the display can move to without a blink: at the current resolution and,
     * where the system names the rates it reaches seamlessly from the current mode ([seamless],
     * Android 12+), at one of those. Null when there is no choice to make (one mode, as an
     * emulator's), so a vote never writes the window for nothing.
     */
    fun fastest(modes: List<DisplayModeInfo>, current: DisplayModeInfo, seamless: Collection<Float>? = null): DisplayModeInfo? =
        modes.filter { m ->
            m.width == current.width && m.height == current.height &&
                (m.id == current.id || seamless == null || seamless.any { abs(it - m.refreshHz) < 0.5f })
        }.takeIf { it.size > 1 }?.maxByOrNull { it.refreshHz }

    /**
     * Something on screen moves (perf, 15 Nov): a map's frame loop runs, a finger is down, or one
     * lifted (or a wheel turned) under [LINGER_MS] ago, which covers the fling, the sheet or the
     * transition it set off.
     */
    fun moving(loops: Int, down: Boolean, sinceTouchMs: Long): Boolean =
        loops > 0 || down || sinceTouchMs in 0 until LINGER_MS

    /** The window's vote: the fastest mode while something moves, else 0, no preference (the system's own rate). */
    fun windowVote(moving: Boolean, fastest: DisplayModeInfo?): Int = if (moving) fastest?.id ?: 0 else 0

    /**
     * The map surface's vote: the display's top rate while its frame loop runs and someone uses the map,
     * [restHz] once it rests (it keeps drawing by itself: orbits, twinkle), else 0 (the system's choice).
     */
    fun surfaceVote(running: Boolean, maxHz: Float, resting: Boolean = false): Float =
        if (!running) 0f else if (resting) restHz(maxHz) else maxHz

    /**
     * The rest rule, as iOS's GraphPace (the review, 19 Nov: an idle map held the top rate forever): a
     * running map loop rests at once while [covered] (a sheet, dialog or menu has the window's focus),
     * held or not, as iOS's map under a sheet; else once nobody has touched or changed the map for
     * [REST_AFTER_MS] and no body is held. Resting, it draws at [restHz] and its window vote is let go
     * (MotionVote.loop).
     */
    fun resting(running: Boolean, held: Boolean, covered: Boolean, sinceWakeMs: Long): Boolean =
        running && (covered || (!held && sinceWakeMs >= REST_AFTER_MS))

    /** The rest rate: 60, or the display's top where that is lower. Never under 60 where the display has it. */
    fun restHz(maxHz: Float): Float = min(REST_HZ, maxHz)

    /**
     * Whether a resting loop draws on this vsync: when waiting for the next one would leave more than a
     * 60th of a second since its last drawn frame ([sinceDrawnNanos]; [vsyncNanos], the last vsync's
     * length, a little under it for the vsyncs' wobble). So a 120 Hz display draws every other vsync
     * (60), a 90 Hz one every vsync (90), a 144 Hz one every other (72), a 240 Hz one every fourth (60):
     * never under 60 where the display reaches it.
     */
    fun drawsAtRest(sinceDrawnNanos: Long, vsyncNanos: Long): Boolean =
        vsyncNanos <= 0L || sinceDrawnNanos + vsyncNanos * 0.95 >= 1e9 / REST_HZ

    const val LINGER_MS = 1_500L
    /** iOS's GraphPace.restAfter: this long after the last touch or change a map that moves by itself rests. */
    const val REST_AFTER_MS = 20_000L
    /** iOS's GraphPace.settled: the rate a resting map draws at. */
    const val REST_HZ = 60f
}

/** Thousands with commas, the same in every locale (the readout is a meter). */
fun grouped(n: Int): String {
    val digits = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    digits.forEachIndexed { k, ch -> if (k > 0 && (digits.length - k) % 3 == 0) out.append(','); out.append(ch) }
    return (if (n < 0) "-" else "") + out
}
