package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLegend
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.hx
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// S05f A6: each theme's legend (LegendSheet, its words MapLegend's), on a phone (the sheet at half
// height, as it opens) and a tablet (full height), light and dark, written to
// build/outputs/roborazzi/ as legend_<theme>_<window>_<mode>.png; Performance's with its Speed row,
// as while the readout shows. Each is read back as MapModeRenderTest's are: the window's size and no
// pixel the logo's coral. Its title and Done are there, Done a 48 dp target, the ladder and every row
// one item for TalkBack each, as MapLegend.spoken reads it, and none wider than the window; its touch
// help the map's hint, naming Show links (S05g); the ladder drawn whole (S05h: Space's five rungs).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapLegendRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
    }

    private var theme by mutableStateOf(GraphTheme.SPACE)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(window: String, dark: Boolean) = checks {
        compose.setContent {
            SortaTheme(dark = isSystemInDarkTheme()) {
                WindowShapeHost { key(theme) { LegendSheet(theme, readout = theme == GraphTheme.PERFORMANCE) {} } }
            }
        }
        for (t in GraphTheme.entries) {
            theme = t
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000) // the sheet's coming up, finished
            compose.waitForIdle()
            val name = "legend_${t.rawValue}_${window}_${if (dark) "dark" else "light"}"
            read(this, name, t)
            val file = File("build/outputs/roborazzi/$name.png")
            captureScreenRoboImage(file.path)
            look(this, name, file)
        }
    }

    private fun read(c: WardChecks, name: String, t: GraphTheme) {
        val content = MapLegend.of(t, readout = t == GraphTheme.PERFORMANCE)
        val width = compose.activity.resources.displayMetrics.widthPixels
        val density = compose.density.density
        c.check("$name: the legend is up", compose.onAllNodes(hasTestTag(MapPanelTags.legend(t))).fetchSemanticsNodes().isNotEmpty())
        c.check("$name: its title", compose.onAllNodes(hasText(t.legendTitle)).fetchSemanticsNodes().isNotEmpty())
        val done = compose.onAllNodes(hasText("Done") and hasClickAction()).fetchSemanticsNodes()
        c.check("$name: Done", done.size == 1, "${done.size}")
        // its touch area, what touch and TalkBack use, as WardTouchTargetTest measures it
        done.firstOrNull()?.touchBoundsInRoot?.let {
            c.check("$name: Done is a 48 dp target", it.height >= 48 * density - 0.5f && it.width >= 48 * density - 0.5f,
                "${it.width / density} x ${it.height / density} dp")
        }
        val ladder = compose.onAllNodes(hasContentDescription(content.ladderLabel)).fetchSemanticsNodes()
        if (content.rungs.isEmpty()) c.check("$name: no ladder", content.ladderLabel.isEmpty())
        else {
            c.check("$name: the ladder, one item", ladder.size == 1, "${ladder.size}")
            // S05h: drawn whole: inside the window's width, and tall enough for its biggest rung and a caption
            ladder.firstOrNull()?.boundsInWindow?.let {
                c.check("$name: the ladder inside the window's width", it.left >= 0f && it.right <= width + 0.5f, "$it of $width")
                val biggest = content.rungs.maxOf { r -> r.size }
                c.check("$name: the ladder holds its ${content.rungs.size} rungs whole", it.height >= biggest * density, "${it.height / density} dp")
            }
        }
        for (row in content.rows) {
            val nodes = compose.onAllNodes(hasContentDescription(MapLegend.spoken(row))).fetchSemanticsNodes()
            c.check("$name: the ${row.name} row, one item", nodes.size == 1, "${nodes.size}")
            nodes.firstOrNull()?.boundsInWindow?.let {
                c.check("$name: the ${row.name} row inside the window's width", it.left >= 0f && it.right <= width + 0.5f, "$it of $width")
            }
        }
        c.check("$name: the touch help", compose.onAllNodes(hasText(content.touch)).fetchSemanticsNodes().size == 1)
        // S05g: the map's hint word for word, Show links and All links as the card names them
        c.check("$name: the touch help is the map's hint, with Show links", content.touch == t.hint && content.touch.contains("Show links"))
    }

    private fun look(c: WardChecks, name: String, file: File) {
        c.check("$name: the PNG is written", file.isFile && file.length() > 0, file.absolutePath)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        c.check("$name: the PNG reads back", image != null)
        if (image == null) return
        val metrics = compose.activity.resources.displayMetrics
        c.check("$name: the PNG is the window's size", image.width == metrics.widthPixels && image.height == metrics.heightPixels,
            "${image.width} x ${image.height} vs ${metrics.widthPixels} x ${metrics.heightPixels}")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val coral = pixels.count { (it and 0xFFFFFF) == LOGO_CORAL }
        c.check("$name: no pixel is the logo's coral ${hx(LOGO_CORAL)}", coral == 0, "$coral pixels")
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone", dark = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLight() = render("tablet", dark = false)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletDark() = render("tablet", dark = true)

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}
