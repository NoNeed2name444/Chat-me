package com.cramdown.sorta

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ideas.MapPreview
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * The maps' renders on a real GL surface, as iOS's MapRenderTests take them: each theme on the
 * sample ideas (MapPreview), with the system by day and by night, one still each, named
 * map-<look>-<light|dark>-<device>.png in /sdcard/Download/sorta-renders. The 3D maps are always
 * night (iOS's are too); the board follows the system. The device's name comes from the
 * instrumentation's "device" argument (the render script in ci/public/android.yml). The waits
 * and the system-dialog handling are Stethoscore's ScreenshotTest's
 * (reference/stethoscore-aec2af1c/android/app/src/androidTest/java/com/stethoscore/app/ScreenshotTest.kt).
 */
@RunWith(AndroidJUnit4::class)
class MapRenderTest {
    @get:Rule val compose = createComposeRule()

    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"

    @Before fun clearChoices() {
        // Ideas' remembered choices start clear, so each map opens as a new user's would
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("ideas", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun spaceLight() = map("space", dark = false, bodies = true)
    @Test fun spaceDark() = map("space", dark = true, bodies = true)
    @Test fun neuronsLight() = map("neurons", dark = false, bodies = false)
    @Test fun neuronsDark() = map("neurons", dark = true, bodies = false)
    @Test fun performanceLight() = map("performance", dark = false, bodies = false)
    @Test fun performanceDark() = map("performance", dark = true, bodies = false)
    @Test fun boardLight() = board(dark = false)
    @Test fun boardDark() = board(dark = true)

    private fun map(look: String, dark: Boolean, bodies: Boolean) {
        compose.setContent { SortaTheme(dark = dark) { MapPreview(look) } }
        try { compose.waitUntil(60_000) { framed(mapDrawn(), bodies) } } catch (_: ComposeTimeoutException) {}
        val name = "map-$look-${if (dark) "dark" else "light"}-$device"
        shot(name) // kept even when the check below fails, to see why
        requireMapDrawn(name, bodies)
    }

    private fun board(dark: Boolean) {
        compose.setContent { SortaTheme(dark = dark) { MapPreview("board") } }
        val titles = SampleIdeas.book().notes.map { it.title }.filter { it.isNotBlank() }
        try {
            compose.waitUntil(20_000) { titles.any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
        } catch (_: ComposeTimeoutException) {}
        shot("map-board-${if (dark) "dark" else "light"}-$device")
        check(titles.any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }) {
            "The board shows none of the sample ideas' titles"
        }
    }

    private fun mapNode(): SemanticsNode? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()

    private fun mapDrawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun framed(drawn: MapDrawn?, bodies: Boolean) =
        drawn != null && drawn.frames >= MapDrawn.ENOUGH && (!bodies || drawn.bodyFrames >= MapDrawn.ENOUGH)

    /**
     * The GL surface really drew: the map reports finished frames (with its bodies, in Space), and
     * the middle of the map holds bright, coloured points. Labels are near-white text, so only
     * saturated colour counts. Stethoscore's requireMapDrawn, as strict.
     */
    private fun requireMapDrawn(name: String, bodies: Boolean) {
        val drawn = mapDrawn()
        check(framed(drawn, bodies)) {
            "The 3D map has not finished ${MapDrawn.ENOUGH} frames${if (bodies) " with its bodies" else ""} (waited up to a minute) for $name: $drawn"
        }
        val bounds = checkNotNull(mapNode()).boundsInRoot
        val screen = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "No screenshot for $name" }
        val left = (bounds.left + bounds.width * 0.08f).toInt().coerceIn(0, screen.width - 1)
        val right = (bounds.right - bounds.width * 0.08f).toInt().coerceIn(left + 1, screen.width)
        val top = (bounds.top + bounds.height * 0.3f).toInt().coerceIn(0, screen.height - 1)
        val bottom = (bounds.bottom - bounds.height * 0.2f).toInt().coerceIn(top + 1, screen.height)
        var coloured = 0; var samples = 0
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val c = screen.getPixel(x, y)
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            if (r + g + b > 220 && maxOf(r, g, b) - minOf(r, g, b) > 45) coloured++
            samples++
        }
        val seen = "$coloured coloured of $samples samples in x $left-$right, y $top-$bottom of the ${screen.width}x${screen.height} screen; the map reports $drawn"
        android.util.Log.i("MapRenderTest", "$name: $seen")
        check(coloured > 25) { "The 3D map drew nothing visible ($seen) for $name" }
    }

    /** One still of the screen as [name].png, never with a system dialog over it. */
    private fun shot(name: String) {
        compose.waitForIdle()
        require(name.matches(Regex("[a-z0-9-]+")))
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ui.waitForIdle()
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        for (attempt in 1..3) {
            dismissSystemDialogs(ui)
            SystemClock.sleep(600) // the compositor presents the settled frame
            if (systemDialog(ui)) continue
            ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
            if (!systemDialog(ui)) {
                val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
                check(size != null && size > 0) { "The still was not written: $name" }
                return
            }
        }
        ui.executeShellCommand("rm -f $DIRECTORY/$name.png")
        error("A system dialog covered the screen for $name")
    }

    private val dialogText = Pattern.compile(".*(isn.t responding|keeps stopping|has stopped).*", Pattern.CASE_INSENSITIVE)

    private fun systemDialog(ui: UiDevice) =
        ui.hasObject(By.res("android", "aerr_wait")) || ui.hasObject(By.res("android", "aerr_close")) ||
            ui.hasObject(By.text(dialogText))

    private fun dismissSystemDialogs(ui: UiDevice) {
        repeat(3) {
            if (!systemDialog(ui)) return
            val button = ui.findObject(By.res("android", "aerr_wait")) ?: ui.findObject(By.res("android", "aerr_close"))
            if (button != null) button.click() else ui.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            ui.wait(Until.gone(By.text(dialogText)), 3_000)
            ui.waitForIdle()
        }
    }

    companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
    }
}
