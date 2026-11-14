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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLegend
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.hx
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DarkMode
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    // S05h repair 7: each row's iOS symbol has a stand-in of its own; Space's Moon (moon.fill) a moon,
    // and only the one row whose symbol is iOS's circle.fill (Neurons' Vesicle) the plain circle
    @Config(sdk = [35])
    @Test fun everyRowHasAGlyphOfItsOwn() {
        assertSame(Icons.Rounded.DarkMode, legendGlyph("moon.fill"))
        val symbols = GraphTheme.entries.flatMap { t -> MapLegend.of(t, readout = true).rows.map { it.symbol } }.distinct()
        assertTrue("moon.fill is a row's", "moon.fill" in symbols)
        for (symbol in symbols) if (symbol != "circle.fill") {
            assertNotSame("$symbol falls back to the plain circle", Icons.Rounded.Circle, legendGlyph(symbol))
        }
    }

    // ---- S05j item 2, 8c, 8d: the sheet below the status bar, at Material3's width on a wide window ----

    /** Every window's views, the sheet's dialog among them (WindowManagerGlobal's, read as Robolectric's shadows do). */
    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<android.view.View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        return global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>
    }

    /** A status bar [px] tall handed to every window, as the system would (Robolectric gives none). */
    private fun statusBar(px: Int) {
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, px, 0, 0))
            .setVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars(), true).build()
        compose.runOnUiThread { for (v in windows()) androidx.core.view.ViewCompat.dispatchApplyWindowInsets(v, insets) }
    }

    /**
     * [t]'s legend at full height with a 24 dp status bar: its top, its drag handle and Done below the
     * bar, and nothing of the sheet in the bar's row (the dimmed map behind it, 8d); on a wide window
     * its width Material3's 640 dp, centred. On 1015763 the full-height sheet's handle sat in the bar.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun underTheBar(name: String, t: GraphTheme, wide: Boolean) = checks {
        compose.setContent { SortaTheme(dark = isSystemInDarkTheme()) { WindowShapeHost { LegendSheet(t, readout = false) {} } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val density = compose.density.density
        val bar = (24 * density).toInt()
        statusBar(bar)
        compose.waitForIdle()
        if (!wide) {
            // the phone's sheet opens at half height: up to its full height, as a person drags it
            compose.onNode(hasTestTag(MapPanelTags.legend(t))).performTouchInput { swipeUp(startY = bottom, endY = top) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        val title = compose.onNode(hasText(t.legendTitle)).fetchSemanticsNode().boundsInWindow
        val done = compose.onNode(hasText("Done") and hasClickAction()).fetchSemanticsNode().boundsInWindow
        check("$name: the title below the bar", title.top >= bar - 0.5f, "${title.top} of $bar")
        check("$name: Done below the bar", done.top >= bar - 0.5f, "${done.top} of $bar")
        val handles = compose.onAllNodes(hasContentDescription("Drag handle", substring = true)).fetchSemanticsNodes()
        check("$name: the drag handle kept", handles.isNotEmpty())
        handles.firstOrNull()?.boundsInWindow?.let { check("$name: the drag handle below the bar", it.top >= bar - 0.5f, "${it.top} of $bar") }
        val list = compose.onNode(hasTestTag(MapPanelTags.legend(t))).fetchSemanticsNode().boundsInWindow
        val window = compose.activity.resources.displayMetrics.widthPixels
        if (wide) {
            check("$name: Material3's 640 dp on a wide window", list.width <= 640 * density + 0.5f, "${list.width / density} dp")
            check("$name: centred", kotlin.math.abs((list.left + list.right) / 2 - window / 2f) <= 1f, "$list of $window")
        }
        System.setProperty("roborazzi.test.record", "true")
        val file = File("build/outputs/roborazzi/legend_sheet_bar_$name.png")
        captureScreenRoboImage(file.path)
        val image = javax.imageio.ImageIO.read(file)
        // the sheet's colour, read inside it under its bar, against the bar's row over the sheet's middle
        val sheet = image.getRGB(window / 2, (done.bottom + 4 * density).toInt()) and 0xFFFFFF
        val row = image.getRGB(window / 2, bar / 2) and 0xFFFFFF
        check("$name: nothing of the sheet in the bar's row", row != sheet, "${hx(row)} and the sheet's ${hx(sheet)}")
    }

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun aWideSheetIsBelowTheBarLight() = underTheBar("tablet_light", GraphTheme.SPACE, wide = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun aWideSheetIsBelowTheBarDark() = underTheBar("tablet_dark", GraphTheme.NEURONS, wide = true)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun aPhonesSheetAtFullHeightIsBelowTheBarLight() = underTheBar("phone_light", GraphTheme.SPACE, wide = false)

    // 8c: the Performance legend's title beside Done alone takes the free leading room before it is cut,
    // as UIKit's inline title; on 1015763 it was cut between equal sides on a 360 dp phone
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun thePerformanceTitleIsWholeOnASmallPhone() {
        compose.setContent { SortaTheme(dark = false) { WindowShapeHost { LegendSheet(GraphTheme.PERFORMANCE, readout = false) {} } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val node = compose.onNode(hasText(GraphTheme.PERFORMANCE.legendTitle)).fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        org.junit.Assert.assertTrue("laid out", results.isNotEmpty())
        // one line, not cut: TextOverflow.Ellipsis would cut its line
        org.junit.Assert.assertFalse("\"${GraphTheme.PERFORMANCE.legendTitle}\" whole, no ellipsis: ${node.boundsInWindow}",
            results.first().isLineEllipsized(0) || results.first().lineCount != 1)
        val done = compose.onNode(hasText("Done") and hasClickAction()).fetchSemanticsNode().boundsInWindow
        org.junit.Assert.assertTrue("clear of Done", node.boundsInWindow.right <= done.left + 0.5f)
    }

    // S05i2: Performance's ladder gains the Folder rung (a hub, 22 dp) between Glow and Idea; on a 360 dp phone
    // its four rungs stay one item, inside the window, tall enough for the Glow, and the Folder row its own item
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun thePerformanceLadderHoldsItsFolderRungOnASmallPhone() {
        compose.setContent { SortaTheme(dark = false) { WindowShapeHost { LegendSheet(GraphTheme.PERFORMANCE, readout = false) {} } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val content = MapLegend.of(GraphTheme.PERFORMANCE)
        org.junit.Assert.assertEquals(listOf("Glow", "Folder", "Idea", "Far idea"), content.rungs.map { it.caption })
        val density = compose.density.density
        val width = compose.activity.resources.displayMetrics.widthPixels
        val ladder = compose.onAllNodes(hasContentDescription("Size ladder: glow, folder, idea, far idea")).fetchSemanticsNodes()
        org.junit.Assert.assertEquals("the ladder, one item", 1, ladder.size)
        val box = ladder.single().boundsInWindow
        org.junit.Assert.assertTrue("inside the window's width: $box of $width", box.left >= 0f && box.right <= width + 0.5f)
        org.junit.Assert.assertTrue("the Glow's 30 dp whole: ${box.height / density} dp", box.height >= 30 * density)
        val folder = content.rows.single { it.name == "Folder" }
        org.junit.Assert.assertEquals("the Folder row, one item", 1,
            compose.onAllNodes(hasContentDescription(MapLegend.spoken(folder))).fetchSemanticsNodes().size)
    }

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}
