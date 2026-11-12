package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Map mode on the emulator's phone with Android's three-button navigation bar (S05b, A6), by day and
 * by night: Sorta itself (MainActivity, edge to edge) on a store of made-up ideas, taken to Map mode
 * by its switch, on Space as a new user's map opens, one still each, named
 * navbar-map-<light|dark>-<device>.png in /sdcard/Download/sorta-renders. The render script turns the
 * three-button bar on before this runs, as for SortNavBarRenderTest, which this follows. Each run
 * checks the bar is the three-button one (it is tappable), that nothing to tap or read lies under it
 * or under the status bar, and that the map drew (MapModeSpaceRenderTest's check of its frames).
 */
@RunWith(AndroidJUnit4::class)
class MapModeNavBarRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clearChoices() {
        // Ideas' remembered choices start clear, so the map opens on Space as a new user's would
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun mapByDay() = render(dark = false)
    @Test fun mapByNight() = render(dark = true)

    private fun render(dark: Boolean) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "navbar-map-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        // a phone's switch; a wider window shows the map beside Sort already
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}

        var navBottom = 0
        var statusTop = 0
        var windowHeight = 0
        scenario!!.onActivity { activity ->
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView)) { "no window insets" }
            navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            check(tappable > 0) { "the navigation bar isn't the three-button one: its tappable inset is $tappable px" }
            windowHeight = activity.window.decorView.height
        }
        check(navBottom > 0 && windowHeight > 0) { "no navigation bar: $navBottom px of $windowHeight" }

        val name = "navbar-map-${if (dark) "dark" else "light"}-$device"
        shot(name) // kept even when a check below fails, to see why
        // nothing to tap or read under the bars (the map draws edge to edge, under them)
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes() +
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "nothing on the screen" }
        val limit = windowHeight - navBottom
        for (node in nodes) {
            val b = node.boundsInWindow
            if (b.height <= 0f) continue // scrolled out of view
            check(b.bottom <= limit + 0.5f) { "${node.config} lies under the navigation bar: bottom ${b.bottom} past $limit" }
            check(b.top >= statusTop - 0.5f) { "${node.config} lies under the status bar: top ${b.top} above $statusTop" }
        }
        check(framed(mapDrawn())) { "Map mode's map has not finished ${MapDrawn.ENOUGH} frames with its bodies (waited up to a minute) for $name: ${mapDrawn()}" }
    }

    private fun mapNode(): SemanticsNode? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()

    private fun mapDrawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun framed(drawn: MapDrawn?) = drawn != null && drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    /** One still of the screen as [name].png, as MapRenderTest takes them. */
    private fun shot(name: String) {
        compose.waitForIdle()
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.waitForIdle()
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        SystemClock.sleep(600) // the compositor presents the settled frame
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical: MapModeSpaceRenderTest's ideas, in To sort and in two folders, some linked
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
