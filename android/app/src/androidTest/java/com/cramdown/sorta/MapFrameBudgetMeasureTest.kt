package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.MapFilter
import com.cramdown.sorta.map.NeuronState
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapPanelTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.map.FrameBudgetLog
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * S05i A7, the measurement copy (branch s05i-measure, never landed): FrameBudget's readout, logged
 * every 250 ms under logcat tag [FrameBudgetLog.TAG], and the medians of 3 s per phase, logged as
 * "median ..." lines under the same tag and written to
 * /sdcard/Download/sorta-renders/framebudget-<light|dark>-<device>.txt. The phases: each theme under
 * Everything, then Pages; in Neurons, Garden opened, every idea resting, then every idea firing
 * (Look › Cells › Firing). It checks only that each phase ran; the numbers are read, not judged.
 */
@RunWith(AndroidJUnit4::class)
class MapFrameBudgetMeasureTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null
    private val lines = ArrayList<String>()

    @Before fun clearChoices() {
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        FrameBudgetLog.phase = ""
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun measureByDay() = measure(dark = false)
    @Test fun measureByNight() = measure(dark = true)

    private fun measure(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000)
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "measure-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
        // Space (its Universe), Everything then Pages
        everythingThenPages("space-$mode-$device")
        // Neurons: Everything then Pages, then Garden opened, resting against firing
        chooseLook(MapLook.NEURON, GraphTheme.NEURONS)
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
        everythingThenPages("neurons-$mode-$device")
        openGarden()
        phase("neurons-resting-$mode-$device")
        val build = checkNotNull(mapDrawn()).build
        press(hasContentDescription("Look") and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, GraphTheme.NEURONS.title))
        press(hasTestTag("lookCell-" + NeuronState.FIRING.rawValue))
        try { compose.waitUntil(60_000) { mapDrawn()?.let { it.build != build && framed(it) && (it.states.getOrNull(NeuronState.FIRING.code) ?: 0) > 0 } == true } } catch (_: ComposeTimeoutException) {}
        phase("neurons-firing-$mode-$device")
        // Performance, Everything then Pages
        chooseLook(MapLook.PERFORMANCE, GraphTheme.PERFORMANCE)
        try { compose.waitUntil(60_000) { (mapDrawn()?.frames ?: 0) >= MapDrawn.ENOUGH } } catch (_: ComposeTimeoutException) {}
        everythingThenPages("performance-$mode-$device")
        val out = File("/sdcard/Download/sorta-renders").also { it.mkdirs() }
        runCatching { File(out, "framebudget-$mode-$device.txt").writeText(lines.joinToString("\n", postfix = "\n")) }
            .onFailure { Log.w(FrameBudgetLog.TAG, "the medians file was not written: ${it.message}") }
        check(lines.size == 8) { "Not every phase ran: ${lines.joinToString(" | ")}" }
    }

    private fun everythingThenPages(name: String) {
        phase("$name-everything")
        chooseFilter(MapFilter.Pages)
        try { compose.waitUntil(30_000) { mapDrawn()?.filtered == true } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000) // the bodies' springs, done
        phase("$name-pages")
        chooseFilter(MapFilter.All)
        try { compose.waitUntil(30_000) { mapDrawn()?.filtered == false } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000)
    }

    /** 3 s of readouts as [name], then their medians, logged and kept. */
    private fun phase(name: String) {
        FrameBudgetLog.start(name)
        SystemClock.sleep(3_000)
        val s = FrameBudgetLog.of(name)
        val line = String.format(Locale.ROOT, "median phase=%s n=%d work=%.1f frame=%.1f fps=%.1f detail=%.2f drawn=%d",
            name, s.size, median(s.map { it.workMs }), median(s.map { it.frameMs }), median(s.map { it.fps }),
            median(s.map { it.detail.toDouble() }), median(s.map { it.drawn.toDouble() }).toInt())
        Log.i(FrameBudgetLog.TAG, line)
        lines += line
    }

    private fun median(v: List<Double>): Double {
        if (v.isEmpty()) return Double.NaN
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun chooseLook(look: MapLook, theme: GraphTheme) {
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(look.identifier)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(look.identifier)).performClick()
        compose.waitUntil(30_000) {
            mapNode()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(theme.mapLabel) } == true
        }
        SystemClock.sleep(1_500) // its scene, built off the main thread, on screen
    }

    private fun chooseFilter(filter: MapFilter) {
        press(hasTestTag(MapPanelTags.FILTER_TOOL))
        press(hasTestTag(MapPanelTags.filterRow(filter)))
    }

    private fun press(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Neurons: the Garden cell chosen and flown into, its ideas drawn. */
    private fun openGarden() {
        val res = instrumentation.targetContext.resources
        val closed = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        val cell = closed.ids.indexOfFirst { it.equals(garden.id, ignoreCase = true) }
        check(cell >= 0) { "No Garden cell" }
        compose.runOnIdle { closed.tap(cell) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(res.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        val first = notes.first { it.folderId == garden.id }
        compose.waitUntil(30_000) { mapNode()?.config?.getOrNull(MapTapKey)?.ids?.any { it.equals(first.id, ignoreCase = true) } == true }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
    }

    private fun mapNode(): SemanticsNode? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()

    private fun mapDrawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun framed(drawn: MapDrawn?) = drawn != null && drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    private companion object {
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // MapModeSpaceRenderTest's made-up ideas, nothing medical
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
        val folders = listOf(garden, kitchen)
        val notes = listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = T + 9 * 3_600_000L),
            Note(id = id(12), title = "Buy brushes", createdAt = T + 8 * 3_600_000L),
            Note(id = id(13), title = "Paint the shed green", body = "Check [[Fix the gate latch]] first.", folderId = garden.id,
                links = listOf(id(12)), createdAt = T + 7 * 3_600_000L),
            Note(id = id(14), title = "Turn the compost", folderId = garden.id, links = listOf(id(13)), createdAt = T + 6 * 3_600_000L),
            Note(id = id(15), title = "Plant tulips before the frost", folderId = garden.id, links = listOf(id(13), id(14)), createdAt = T + 5 * 3_600_000L),
            Note(id = id(16), title = "Try the seeded bread", folderId = kitchen.id, createdAt = T + 4 * 3_600_000L),
            Note(id = id(17), title = "Order more coffee beans", folderId = kitchen.id, links = listOf(id(16)), createdAt = T + 3 * 3_600_000L),
            Note(id = id(18), title = "Plan the weekend walk", createdAt = T + 2 * 3_600_000L),
        )
    }
}
