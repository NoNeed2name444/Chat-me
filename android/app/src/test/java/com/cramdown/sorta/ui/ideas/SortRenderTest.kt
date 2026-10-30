package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cramdown.sorta.R
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardToken
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
import java.util.UUID

// A6: the Sort screens on made-up ideas (IdeasFixture), drawn on the four windows of
// WardGalleryRenderTest, light and dark, and written to build/outputs/roborazzi/ as
// sort_<screen>_<window>_<mode>.png: To sort with ideas, the empty state, the folder chooser, an
// open folder, the editor, and the import's message. A7's font scale 2.0 on a phone adds To sort
// and the editor (sort_*_phone_scale2_light.png). The whole screen is captured, sheets and alerts
// with it. Each PNG is read back: it is written at the window's size; the screens' ground is the
// page colour of the mode (under a sheet or an alert the scrim darkens it); and no pixel is the
// logo's coral (#C9553E), which no screen draws.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SortRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
    }

    /** A screen: its name in the file, whether its store is filled, where it opens, and whether a sheet or an alert is up. */
    private class Shot(val name: String, val filled: Boolean, val start: IdeasStart = IdeasStart(), val import: Boolean = false, val over: Boolean = false)

    private val shots = listOf(
        Shot("tosort", true),
        Shot("empty", false),
        Shot("chooser", true, IdeasStart(choosing = IdeasFixture.gate.id), over = true),
        Shot("folder", true, IdeasStart(folderId = IdeasFixture.garden.id)),
        Shot("editor", true, IdeasStart(editing = IdeasFixture.shed.id)),
        Shot("import", false, import = true, over = true),
    )

    private var which by mutableIntStateOf(0)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(window: String, dark: Boolean, names: List<String> = shots.map { it.name }, scale: Float = 1f) = checks {
        val list = shots.filter { it.name in names }
        val cache = compose.activity.cacheDir
        val models = list.map { IdeasModel.open(IdeasFixture.store(cache, it.filled)) }
        val export = File(cache, "export-" + UUID.randomUUID() + ".json").also { it.writeText(SortaRun.export) }
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                SortaTheme(dark = isSystemInDarkTheme()) {
                    WindowShapeHost {
                        key(which) {
                            val shot = list[which]
                            IdeasView(models[which], testImport = if (shot.import) export else null, start = shot.start)
                        }
                    }
                }
            }
        }
        for (i in list.indices) {
            which = i
            val shot = list[i]
            val name = "sort_${shot.name}_${window}_${if (dark) "dark" else "light"}"
            settle(this, name, shot)
            val file = File("build/outputs/roborazzi/$name.png")
            captureScreenRoboImage(file.path)
            look(this, name, file, dark, shot.over)
        }
    }

    /** Waits for the screen to be still: the sheet up, or the import read and its message shown. */
    private fun settle(c: WardChecks, name: String, shot: Shot) {
        val deadline = System.nanoTime() + 10_000_000_000L
        fun ready(): Boolean = when {
            shot.import -> compose.onAllNodes(hasText(compose.activity.getString(R.string.import_done_title))).fetchSemanticsNodes().isNotEmpty()
            shot.start.choosing != null -> compose.onAllNodes(hasTestTag(FOLDER_CHOOSER_TAG)).fetchSemanticsNodes().isNotEmpty()
            else -> true
        }
        while (true) {
            compose.waitForIdle()
            if (ready() || System.nanoTime() > deadline) break
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(2)
        }
        compose.mainClock.advanceTimeBy(1_000) // the sheet's and the alert's coming up, finished
        compose.waitForIdle()
        c.check("$name: the screen is up", ready())
    }

    private fun look(c: WardChecks, name: String, file: File, dark: Boolean, over: Boolean) {
        c.check("$name: the PNG is written", file.isFile && file.length() > 0, file.absolutePath)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        c.check("$name: the PNG reads back", image != null)
        if (image == null) return
        val metrics = compose.activity.resources.displayMetrics
        c.check("$name: the PNG is the window's size", image.width == metrics.widthPixels && image.height == metrics.heightPixels,
            "${image.width} x ${image.height} vs ${metrics.widthPixels} x ${metrics.heightPixels}")
        val page = WardPalette.hex(WardToken.background, dark)
        // a sheet comes up from the bottom, so under one the top corner shows the scrim
        val corner = (if (over) image.getRGB(image.width - 2, 2) else image.getRGB(image.width - 2, image.height - 2)) and 0xFFFFFF
        if (over) c.check("$name: under the sheet or alert, the scrim darkens the page", corner != page && luma(corner) < luma(page),
            "${hx(corner)} vs ${hx(page)}")
        else c.check("$name: the ground is the ${if (dark) "dark" else "light"} page", corner == page, "${hx(corner)} vs ${hx(page)}")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val coral = pixels.count { (it and 0xFFFFFF) == LOGO_CORAL }
        c.check("$name: no pixel is the logo's coral ${hx(LOGO_CORAL)}", coral == 0, "$coral pixels")
    }

    private fun luma(x: Int): Int = ((x shr 16) and 0xFF) * 299 + ((x shr 8) and 0xFF) * 587 + (x and 0xFF) * 114

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone", dark = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLight() = render("tablet", dark = false)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletDark() = render("tablet", dark = true)

    // a book-style foldable: closed, its outer screen; open, its inner screen (as WardGalleryRenderTest)
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun foldableClosedLight() = render("foldable_closed", dark = false)

    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-night-xxhdpi")
    @Test fun foldableClosedDark() = render("foldable_closed", dark = true)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun foldableOpenLight() = render("foldable_open", dark = false)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-night-xhdpi")
    @Test fun foldableOpenDark() = render("foldable_open", dark = true)

    // A7: font scale 2.0 on a phone, To sort and the editor
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneAtFontScaleTwo() = render("phone_scale2", dark = false, names = listOf("tosort", "editor"), scale = 2f)

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}
