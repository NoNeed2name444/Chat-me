package com.cramdown.sorta.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import com.cramdown.sorta.map.DisplayModeInfo
import com.cramdown.sorta.map.MotionVote
import com.cramdown.sorta.map.RefreshRate
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Each activity's window refresh vote (MotionVote; perf, 15 Nov): its touches (MainActivity) and
 * its maps' frame loops (Map3DView) ask for the display's fastest seamless mode while something
 * moves, and the window asks for nothing once all is still. Main thread only.
 */
object MotionRefresh {
    private val votes = WeakHashMap<Activity, MotionVote>()

    fun of(activity: Activity): MotionVote = votes.getOrPut(activity) {
        // the vote holds its activity weakly: a value that held its key would keep every activity
        // a turn or a fold replaced
        val held = WeakReference(activity)
        val handler = Handler(Looper.getMainLooper())
        lateinit var vote: MotionVote
        val check = Runnable { vote.check(SystemClock.uptimeMillis()) }
        vote = MotionVote(
            fastest = { held.get()?.let(::fastestMode) },
            apply = { id -> held.get()?.window?.let { w -> w.attributes = w.attributes.also { it.preferredDisplayModeId = id } } },
            later = { at -> handler.removeCallbacks(check); handler.postAtTime(check, at) },
        )
        vote
    }

    /** A touch on [activity]'s window, or a wheel's turn (ACTION_SCROLL). */
    fun touch(activity: Activity, event: MotionEvent) {
        val action = event.actionMasked
        val down = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL && action != MotionEvent.ACTION_SCROLL
        of(activity).touch(down, SystemClock.uptimeMillis())
    }

    private fun fastestMode(activity: Activity): DisplayModeInfo? {
        val display = displayOf(activity) ?: return null
        val current = display.mode
        val seamless = if (Build.VERSION.SDK_INT >= 31) current.alternativeRefreshRates.toList() else null
        return RefreshRate.fastest(display.supportedModes.map { it.info() }, current.info(), seamless)
    }
}

/**
 * The display [context] shows on; a context the system gave no display of its own throws for it
 * (Robolectric's activity does), so the window manager's default display stands in.
 */
fun displayOf(context: Context): Display? =
    (if (Build.VERSION.SDK_INT >= 30) runCatching { context.display }.getOrNull() else null)
        ?: @Suppress("DEPRECATION") (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay

fun Display.Mode.info() = DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)
