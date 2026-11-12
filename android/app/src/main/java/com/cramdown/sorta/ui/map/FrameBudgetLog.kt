package com.cramdown.sorta.ui.map

import android.util.Log

/**
 * S05i A7's measurement copy, never landed: every readout the map makes (GlMapRenderer's, one each
 * 250 ms while it draws) is logged under [TAG] with the phase a test has named, and kept so the test
 * can take the medians of its own phase (MapFrameBudgetMeasureTest).
 */
object FrameBudgetLog {
    const val TAG = "SortaFrameBudget"

    class Sample(val phase: String, val fps: Double, val frameMs: Double, val workMs: Double, val detail: Float, val drawn: Int)

    @Volatile var phase: String = ""
    private val samples = ArrayList<Sample>()

    fun record(r: MapReadout) {
        val p = phase
        Log.i(TAG, "readout phase=$p fps=%.1f frame=%.1f work=%.1f detail=%.2f drawn=%d bodies=%d".format(
            java.util.Locale.ROOT, r.fps, r.frameMs, r.workMs, r.detail, r.drawnNodes, r.bodies))
        synchronized(samples) { samples.add(Sample(p, r.fps, r.frameMs, r.workMs, r.detail, r.drawnNodes)) }
    }

    /** Starts [name]: the readouts kept so far are dropped. */
    fun start(name: String) { synchronized(samples) { samples.clear() }; phase = name }

    /** The readouts of the phase [name] so far. */
    fun of(name: String): List<Sample> = synchronized(samples) { samples.filter { it.phase == name } }
}
