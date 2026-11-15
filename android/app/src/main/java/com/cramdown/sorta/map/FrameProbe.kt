package com.cramdown.sorta.map

import java.util.Locale
import kotlin.math.ceil

/**
 * Frame times summed up (perf, 15 Nov): how many, the 50th, 95th and 99th percentiles (nearest rank),
 * and the shares over a 120 Hz refresh ([over8], over 8.3 ms) and over a 60 Hz one ([over16], over
 * 16.7 ms): the owner's bar is 120 frames a second on a 120 Hz screen. For the frame tests
 * (FramePerfTest), and their unit test; iOS's FrameSummary is the same.
 */
data class FrameSummary(val frames: Int, val p50: Double, val p95: Double, val p99: Double, val over8: Double, val over16: Double) {
    /** One line, as the frame tests log it: "<name> frames=… p50=… p95=… p99=… over8.3=…% over16.7=…%". */
    fun line(name: String): String = String.format(Locale.ROOT, "%s frames=%d p50=%.1f p95=%.1f p99=%.1f over8.3=%.1f%% over16.7=%.1f%%",
        name, frames, p50, p95, p99, over8 * 100, over16 * 100)

    companion object {
        /** A 120 Hz refresh, and a 60 Hz one, in milliseconds. */
        const val REFRESH_120_MS = 1000.0 / 120
        const val REFRESH_60_MS = 1000.0 / 60
        val NONE = FrameSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /** [ms] summed up. */
        fun of(ms: DoubleArray): FrameSummary {
            if (ms.isEmpty()) return NONE
            val sorted = ms.copyOf().also { it.sort() }
            return FrameSummary(ms.size, percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 0.99),
                ms.count { it > REFRESH_120_MS }.toDouble() / ms.size, ms.count { it > REFRESH_60_MS }.toDouble() / ms.size)
        }

        /** The nearest-rank [p] percentile of [sorted]: the smallest value at least a [p] share of them reach. */
        fun percentile(sorted: DoubleArray, p: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}

/**
 * Debug builds' frame tests only (FramePerfTest): the GL map's frames, each one's interval and the work
 * it took on the renderer's thread, recorded while [on]. The renderer records only where its test hooks
 * are on (a debuggable build); a released app never does.
 */
object MapFrameProbe {
    const val CAP = 8192
    @Volatile var on: Boolean = false; private set
    private val interval = DoubleArray(CAP)
    private val work = DoubleArray(CAP)
    private var n = 0

    @Synchronized fun start() { n = 0; on = true }
    @Synchronized fun add(intervalMs: Double, workMs: Double) {
        if (!on || n >= CAP) return
        interval[n] = intervalMs; work[n] = workMs; n++
    }
    /** Stops recording; the intervals and the work, in milliseconds. */
    @Synchronized fun stop(): Pair<DoubleArray, DoubleArray> { on = false; return interval.copyOf(n) to work.copyOf(n) }
}

/**
 * Debug builds' frame tests only (FramePerfTest): on, a map made after hands its page only what a
 * released app's map does, rather than every frame the tests' records want, so its frames are timed as
 * a released app's are. Off everywhere else; a released app never reads it.
 */
object MapAsReleased {
    @Volatile var on: Boolean = false
}
