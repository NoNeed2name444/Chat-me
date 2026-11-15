package com.cramdown.sorta

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.FrameSummary
import com.cramdown.sorta.map.MapAsReleased
import com.cramdown.sorta.map.MapFrameProbe
import com.cramdown.sorta.ui.ideas.DeckTags
import com.cramdown.sorta.ui.ideas.IdeasSession
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.ideas.BOARD_CARD_TAG
import com.cramdown.sorta.ui.ideas.EDITOR_TAG
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sorta's frames, timed on the emulator (perf, 15 Nov: the owner's 120 frames a second on a 120 Hz
 * screen). Each phase logs, under the tag SortaFrames, one line per measure: how many frames, the
 * 50th, 95th and 99th percentile in milliseconds, and the shares over a 120 Hz refresh (8.3 ms) and
 * over a 60 Hz one (16.7 ms) (FrameSummary).
 * - ui-total: each window frame's whole time (FrameMetrics.TOTAL_DURATION);
 * - ui-main: its main thread's part (input, animation, layout, draw);
 * - ui-render: its render thread's part (sync, commands, swap);
 * - gl-interval and gl-work: the GL map's frames (MapFrameProbe): the time between them, and the
 *   renderer thread's work in each.
 * The emulator draws GL in software (SwiftShader), so its numbers are read against each other, the
 * same phases before and after a change, and by the work a frame takes, never as a phone's.
 *
 * No Compose test rule: its test clock would pace the page's frames. The screens are found through
 * the activity's own semantics, touched through UiAutomation, and timed on the real clock. Animations
 * are on for the map (lively, as a person's phone has them) and off elsewhere, as CI has them. The map
 * hands its page what a released app's does (MapAsReleased). Every phase must draw frames, or fails.
 */
@RunWith(AndroidJUnit4::class)
class FramePerfTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val context get() = instrumentation.targetContext
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: MainActivity? = null

    @Before fun fresh() {
        context.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        MapAsReleased.on = true
    }

    @After fun back() {
        MapAsReleased.on = false
        if (MapFrameProbe.on) MapFrameProbe.stop()
        ui.executeShellCommand("settings put global animator_duration_scale 0")
        scenario?.close()
        activity = null
    }

    /** Space's map, lively (its default look): at rest, dragged round, and a body flown to. */
    @Test fun map() {
        open(lively = true)
        reachMap()
        waitFor(90_000, "the map drawn") { drawn()?.let { it.frames >= MapDrawn.ENOUGH && it.bodyFrames >= MapDrawn.ENOUGH } == true }
        SystemClock.sleep(1_000)
        val box = bounds(MapDrawnKey)
        phase("map-lively", 3_000, gl = true)
        phase("map-drag", 3_000, gl = true) { drag(box.centerX(), box.top + box.height() * 0.3f, box.width() * 0.25f, 3_000) }
        // the biggest body clear of the chrome, else the first
        val body = drawn()?.spot?.takeIf { it.size == 3 }?.get(0)?.toInt() ?: 0
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map takes no tap" }
        phase("map-fly", 2_000, gl = true) { onMain { tap.tap(body) }; SystemClock.sleep(2_000) }
    }

    /** The 2D board, panned round. */
    @Test fun board() {
        IdeasSettings(context).setLook(MapLook.TWO_D)
        open(lively = false)
        reachMap()
        waitFor(20_000, "the board's cards") { find(BOARD_CARD_TAG) != null }
        SystemClock.sleep(1_000)
        val root = bounds(null)
        // from below the cards, where the board itself takes the finger
        val lowest = all(BOARD_CARD_TAG).maxOf { it.boundsInWindow.bottom }
        val y = minOf(lowest + 40 * px(), root.bottom - root.height() * 0.2f)
        phase("board-pan", 3_000, gl = false) { drag(root.centerX(), y, root.width() * 0.2f, 3_000) }
    }

    /** To sort's deck: the top card carried round and let go where it started, so it springs back. */
    @Test fun deck() {
        IdeasSettings(context).apply { setSortDeck(true); setLastTide(System.currentTimeMillis()) }
        IdeasSession.offerDone = true
        open(lively = false)
        waitFor(20_000, "the deck's card") { find(DeckTags.CARD) != null }
        SystemClock.sleep(1_000)
        val card = checkNotNull(find(DeckTags.CARD)).boundsInWindow
        val origin = windowOrigin()
        val cx = origin[0] + (card.left + card.right) / 2; val cy = origin[1] + card.top + (card.bottom - card.top) * 0.3f
        phase("deck-drag", 2_000, gl = false) { drag(cx, cy, 60 * px(), 2_000, screen = true) }
    }

    /** An idea's editor, typed in at about ten keys a second. */
    @Test fun editor() {
        open(lively = false)
        waitFor(20_000, "the list") { find(IdeasTags.TITLE) != null }
        // the newest idea in To sort, at the list's top
        val idea = notes.first { it.folderId == null }
        val row = checkNotNull(ui.findObject(By.text(idea.title))) { "No row for ${idea.title}" }
        row.click()
        waitFor(10_000, "the editor") { find(EDITOR_TAG) != null }
        SystemClock.sleep(1_000)
        val field = ui.findObjects(By.clazz("android.widget.EditText")).maxByOrNull { it.visibleBounds.height() }
        checkNotNull(field) { "The editor has no field" }.click()
        SystemClock.sleep(500)
        val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        phase("editor-typing", 3_000, gl = false) {
            val words = " and the light over the shed"
            for (c in words) {
                keys.getEvents(charArrayOf(c))?.forEach { instrumentation.sendKeySync(it) }
                SystemClock.sleep(100)
            }
        }
    }

    // MARK: the phases

    /** Times [during] (by default [ms] of rest): the window's frames, and with [gl] the map's. */
    private fun phase(name: String, ms: Long, gl: Boolean, during: () -> Unit = { SystemClock.sleep(ms) }) {
        val window = checkNotNull(activity).window
        val hz = onMain { checkNotNull(activity).display?.refreshRate } ?: 60f
        val frames = WindowFrames(window)
        frames.start()
        if (gl) MapFrameProbe.start()
        val began = SystemClock.uptimeMillis()
        during()
        val seconds = (SystemClock.uptimeMillis() - began) / 1000.0
        val (interval, work) = if (gl) MapFrameProbe.stop() else DoubleArray(0) to DoubleArray(0)
        val (total, main, render) = frames.stop()
        val out = ArrayList<String>()
        out += String.format(java.util.Locale.ROOT, "%s-%s seconds=%.1f hz=%.0f", device, name, seconds, hz)
        out += FrameSummary.of(total).line("$device-$name ui-total")
        out += FrameSummary.of(main).line("$device-$name ui-main")
        out += FrameSummary.of(render).line("$device-$name ui-render")
        if (gl) {
            out += FrameSummary.of(interval).line("$device-$name gl-interval")
            out += FrameSummary.of(work).line("$device-$name gl-work")
        }
        for (line in out) Log.i(TAG, line)
        check(total.isNotEmpty() || interval.isNotEmpty()) { "$name drew no frame in ${seconds}s" }
        if (gl) check(interval.isNotEmpty()) { "$name: the map drew no frame in ${seconds}s" }
    }

    /** A window's frames, as FrameMetrics hands them over on a thread of their own. */
    private inner class WindowFrames(private val window: Window) : Window.OnFrameMetricsAvailableListener {
        private val thread = HandlerThread("frame-metrics").also { it.start() }
        private val total = ArrayList<Double>(); private val main = ArrayList<Double>(); private val render = ArrayList<Double>()

        fun start() = onMain { window.addOnFrameMetricsAvailableListener(this, Handler(thread.looper)) }

        override fun onFrameMetricsAvailable(window: Window, m: FrameMetrics, dropped: Int) {
            fun ms(vararg ids: Int) = ids.sumOf { m.getMetric(it) } / 1e6
            synchronized(this) {
                total += ms(FrameMetrics.TOTAL_DURATION)
                main += ms(FrameMetrics.INPUT_HANDLING_DURATION, FrameMetrics.ANIMATION_DURATION,
                    FrameMetrics.LAYOUT_MEASURE_DURATION, FrameMetrics.DRAW_DURATION)
                render += ms(FrameMetrics.SYNC_DURATION, FrameMetrics.COMMAND_ISSUE_DURATION, FrameMetrics.SWAP_BUFFERS_DURATION)
            }
        }

        fun stop(): Triple<DoubleArray, DoubleArray, DoubleArray> {
            onMain { window.removeOnFrameMetricsAvailableListener(this) }
            thread.quitSafely()
            thread.join(2_000)
            return synchronized(this) { Triple(total.toDoubleArray(), main.toDoubleArray(), render.toDoubleArray()) }
        }
    }

    // MARK: getting there

    private fun open(lively: Boolean) {
        val dir = File(context.cacheDir, "frames-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        checkNotNull(scenario).onActivity { activity = it }
        waitFor(20_000, "Sorta") { find(IdeasTags.TITLE) != null }
        if (lively) {
            ui.executeShellCommand("settings put global animator_duration_scale 1")
            waitFor(5_000, "animations on") { android.animation.ValueAnimator.areAnimatorsEnabled() }
        }
    }

    /** A phone's map behind its switch; a wider window's beside Sort already. */
    private fun reachMap() {
        val switch = find(SortaMode.MAP.identifier) ?: return
        val origin = windowOrigin()
        val b = switch.boundsInWindow
        check(ui.click((origin[0] + (b.left + b.right) / 2).toInt(), (origin[1] + (b.top + b.bottom) / 2).toInt())) { "Map mode's switch" }
    }

    /**
     * One finger down at ([x], [y]) (in the window, or on the screen with [screen]) carried round a
     * circle of [radius] for [ms], about every 8 ms, and lifted where it went down.
     */
    private fun drag(x: Float, y: Float, radius: Float, ms: Long, screen: Boolean = false) {
        val origin = if (screen) floatArrayOf(0f, 0f) else windowOrigin()
        val x0 = origin[0] + x; val y0 = origin[1] + y
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x0, y0)
        var t = 0L
        while (t < ms) {
            SystemClock.sleep(8)
            t = SystemClock.uptimeMillis() - down
            val a = 2 * Math.PI * t / ms
            touch(MotionEvent.ACTION_MOVE, down, x0 + radius * sin(a).toFloat(), y0 + radius * (1 - cos(a)).toFloat())
        }
        touch(MotionEvent.ACTION_UP, down, x0, y0)
    }

    private fun touch(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "The touch was not delivered" } } finally { event.recycle() }
    }

    private fun px() = context.resources.displayMetrics.density

    private fun waitFor(ms: Long, what: String, ready: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < until) {
            if (runCatching(ready).getOrDefault(false)) return
            SystemClock.sleep(100)
        }
        check(runCatching(ready).getOrDefault(false)) { "Not ready within ${ms / 1000} s: $what" }
    }

    // MARK: the activity's semantics, read on the main thread with no wait for idle

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return checkNotNull(out).getOrThrow()
    }

    private fun roots(): List<SemanticsNode> = onMain {
        val found = ArrayList<SemanticsNode>()
        fun views(v: View) {
            if (v is RootForTest) found.add(v.semanticsOwner.unmergedRootSemanticsNode)
            if (v is ViewGroup) for (k in 0 until v.childCount) views(v.getChildAt(k))
        }
        activity?.window?.decorView?.let(::views)
        found
    }

    private fun all(match: (SemanticsNode) -> Boolean): List<SemanticsNode> = onMain {
        val found = ArrayList<SemanticsNode>()
        fun walk(n: SemanticsNode) { if (match(n)) found.add(n); n.children.forEach(::walk) }
        roots().forEach(::walk)
        found
    }

    private fun all(tag: String) = all { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    private fun find(tag: String) = all(tag).firstOrNull()
    private fun mapNode() = all { it.config.getOrNull(MapDrawnKey) != null }.firstOrNull()
    private fun drawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    /** The window's place on the screen. */
    private fun windowOrigin(): FloatArray = onMain {
        val at = IntArray(2)
        checkNotNull(activity).window.decorView.getLocationOnScreen(at)
        floatArrayOf(at[0].toFloat(), at[1].toFloat())
    }

    /** The bounds, in the window, of the node carrying [key]; the whole window for null. */
    private fun bounds(key: androidx.compose.ui.semantics.SemanticsPropertyKey<*>?): android.graphics.RectF {
        val node = if (key == null) roots().first() else checkNotNull(all { it.config.getOrNull(key) != null }.firstOrNull()) { "No node with $key" }
        val b = node.boundsInWindow
        return android.graphics.RectF(b.left, b.top, b.right, b.bottom)
    }

    private companion object {
        const val TAG = "SortaFrames"
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical: 60 ideas in five folders and To sort, each linked to one or two before it
        val folders = (0 until 5).map { NoteFolder(id = id(900 + it), name = listOf("Garden", "Kitchen", "Trips", "Books", "Work")[it], createdAt = T + it) }
        val notes = (0 until 60).map { i ->
            Note(id = id(i + 1), title = "Idea ${i + 1} about ${listOf("tulips", "bread", "Lisbon", "novels", "the shed", "coffee")[i % 6]}",
                folderId = if (i % 6 == 5) null else folders[i % 5].id,
                links = listOfNotNull(if (i > 0) id(i) else null, if (i > 6 && i % 3 == 0) id(i - 6) else null),
                boardX = -400.0 + 160.0 * (i % 6), boardY = -500.0 + 120.0 * (i / 6), createdAt = T + (60 - i) * 3_600_000L)
        }
    }
}
