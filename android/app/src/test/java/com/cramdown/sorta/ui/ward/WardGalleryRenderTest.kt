package com.cramdown.sorta.ui.ward

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.cramdown.sorta.ui.SortaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// The states gallery (WardStatesGallery: every control at rest, held and
// disabled) drawn on four windows, light and dark, and written as PNGs to
// build/outputs/roborazzi/. Each PNG is read back and checked: the ground is the
// page colour of its mode, and the held and disabled columns are drawn
// differently from the column at rest.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WardGalleryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
    }

    private fun render(name: String, dark: Boolean) = checks {
        compose.setContent {
            SortaTheme(dark = isSystemInDarkTheme()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    WardStatesGallery(WardSection.sorta, Modifier.safeDrawingPadding())
                }
            }
        }
        compose.waitForIdle()

        // every round button keeps its round face, however narrow the column
        // (on a folded phone a squeezed circle once drew as a tall oval)
        val circles = compose.onAllNodes(hasContentDescription("Add")).fetchSemanticsNodes()
        check("$name: the gallery has its six round buttons", circles.size == 6, "${circles.size}")
        val face = WardControlSize.buttonSmall * compose.density.density
        for ((i, node) in circles.withIndex()) {
            val (w, h) = node.size.width to node.size.height
            check("$name: round button ${i + 1} is round", kotlin.math.abs(w - h) <= 1, "$w x $h px")
            check("$name: round button ${i + 1} keeps its ${WardControlSize.buttonSmall.toInt()} dp face", w >= face - 1 && h >= face - 1, "$w x $h px")
        }

        val file = File("build/outputs/roborazzi/gallery_$name.png")
        compose.onRoot().captureRoboImage(file.path)
        check("$name: the PNG is written", file.isFile && file.length() > 0, file.absolutePath)

        // the checks read the PNG as written
        val image = javax.imageio.ImageIO.read(file)
        check("$name: the PNG reads back", image != null)
        if (image == null) return@checks
        val page = WardPalette.hex(WardToken.background, dark)
        val corner = image.getRGB(image.width - 2, image.height - 2) and 0xFFFFFF
        check("$name: the ground is the ${if (dark) "dark" else "light"} page", corner == page, "${hx(corner)} vs ${hx(page)}")

        // the gallery's three columns: the same controls at rest, held and disabled
        val density = compose.density.density
        val gutter = (WardSpace.gutter * density).toInt()
        val inner = image.width - 2 * gutter
        val column = inner / 3
        fun strip(i: Int): IntArray {
            val x = gutter + i * column + column / 4
            val w = column / 2
            return image.getRGB(x, 0, w, image.height, null, 0, w)
        }
        val rest = strip(0)
        val held = strip(1)
        val disabled = strip(2)
        fun differ(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }
        check("$name: held is drawn apart from rest", differ(rest, held) > rest.size / 100, "${differ(rest, held)} pixels differ")
        check("$name: disabled is drawn apart from rest", differ(rest, disabled) > rest.size / 100, "${differ(rest, disabled)} pixels differ")
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone_light", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone_dark", dark = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLandscapeLight() = render("tablet_landscape_light", dark = false)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletLandscapeDark() = render("tablet_landscape_dark", dark = true)

    // a book-style foldable: closed, its outer screen; open, its inner screen (as WindowShapeTest's 841 x 701)
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun foldableClosedLight() = render("foldable_closed_light", dark = false)

    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-night-xxhdpi")
    @Test fun foldableClosedDark() = render("foldable_closed_dark", dark = true)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun foldableOpenLight() = render("foldable_open_light", dark = false)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-night-xhdpi")
    @Test fun foldableOpenDark() = render("foldable_open_dark", dark = true)
}
