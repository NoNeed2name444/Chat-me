package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.IdeasTags
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * To sort on the emulator's phone with Android's three-button navigation bar (S05, A6), by day
 * and by night: Sorta itself (MainActivity, edge to edge) on a store of made-up ideas, one still
 * each, named sort-navbar-<light|dark>-<device>.png in /sdcard/Download/sorta-renders. The render
 * script turns the three-button bar on before this runs. Each run checks the bar is the
 * three-button one (it is tappable, so its inset is there to tap) and that nothing on the screen
 * lies under it or under the status bar. The stills are taken as MapRenderTest's are.
 */
@RunWith(AndroidJUnit4::class)
class SortNavBarRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test fun toSortByDay() = render(dark = false)
    @Test fun toSortByNight() = render(dark = true)

    @After fun back() {
        scenario?.close()
        ui.executeShellCommand("cmd uimode night no")
    }

    private fun render(dark: Boolean) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "navbar-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Fix the gate latch")).fetchSemanticsNodes().isNotEmpty() }

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

        // nothing to tap or read under the bars (the screen draws edge to edge, its page under them)
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes() +
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text)).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "nothing on the screen" }
        val limit = windowHeight - navBottom
        for (node in nodes) {
            val b = node.boundsInWindow
            if (b.height <= 0f) continue // scrolled out of view
            check(b.bottom <= limit + 0.5f) { "${node.config} lies under the navigation bar: bottom ${b.bottom} past $limit" }
            check(b.top >= statusTop - 0.5f) { "${node.config} lies under the status bar: top ${b.top} above $statusTop" }
        }
        shot("sort-navbar-${if (dark) "dark" else "light"}-$device")
    }

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

        // made up, not medical: enough ideas and folders that the list reaches down toward the bar
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
        val folders = listOf(garden, kitchen)
        val notes = listOf("Fix the gate latch", "Buy brushes", "Call about the boiler service", "Return the library books",
            "Book the bike in for a service", "Find the spare key", "Order more coffee beans", "Sort the photos from the trip",
            "Plan the weekend walk", "Mend the torn curtain").mapIndexed { i, title ->
            Note(id = id(11 + i), title = title, createdAt = T + (20 - i) * 3_600_000L)
        } + Note(id = id(40), title = "Paint the shed green", folderId = garden.id, createdAt = T)
    }
}
