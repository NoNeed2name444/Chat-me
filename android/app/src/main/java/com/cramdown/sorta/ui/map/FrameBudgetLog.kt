package com.cramdown.sorta.ui.map

import android.util.Log

/**
 * S05h A7's measurement copy, never landed: every readout the map makes (GlMapRenderer's, one each
 * 250 ms while it draws) is logged under [TAG] with the phase a test has named, and kept so the test
 * can take the medians of its own phase (MapSpaceLivelyTest's measure tests).
 *
 * [keepDrawing] keeps Map3DView's frame loop drawing every vsync even once the map is still, so a
 * resting map has readouts too; [forced] counts the frames drawn only because of it, and [kick]
 * starts a loop that has already stopped. Off unless a measure test turns it on.
 */
object FrameBudgetLog {
    const val TAG = "SortaFrameBudget"

    class Sample(
        val phase: String, val fps: Double, val frameMs: Double, val workMs: Double, val detail: Float,
        val drawn: Int, val chunks: Int, val summaries: Int, val bodies: Int,
    )

    @Volatile var phase: String = ""
    @Volatile var keepDrawing: Boolean = false
    @Volatile var forced: Int = 0
    @Volatile var kick: (() -> Unit)? = null
    private val samples = ArrayList<Sample>()

    fun record(r: MapReadout) {
        val p = phase
        Log.i(TAG, "readout phase=$p fps=%.1f frame=%.1f work=%.1f detail=%.2f drawn=%d chunks=%d summaries=%d bodies=%d".format(
            java.util.Locale.ROOT, r.fps, r.frameMs, r.workMs, r.detail, r.drawnNodes, r.chunks, r.summaries, r.bodies))
        synchronized(samples) {
            samples.add(Sample(p, r.fps, r.frameMs, r.workMs, r.detail, r.drawnNodes, r.chunks, r.summaries, r.bodies))
        }
    }

    /** Starts [name]: the readouts kept so far are dropped. */
    fun start(name: String) { synchronized(samples) { samples.clear() }; phase = name }

    /** The readouts of the phase [name] so far. */
    fun of(name: String): List<Sample> = synchronized(samples) { samples.filter { it.phase == name } }
}
