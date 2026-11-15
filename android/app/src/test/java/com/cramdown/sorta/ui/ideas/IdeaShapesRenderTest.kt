package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.MapLook
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

// S06c A7: the ideas like the open one, the collide card, the split chooser and a join's result, on
// ShapesFixture, on a phone and a tablet, light and dark, written to build/outputs/roborazzi/ as
// shapes_<shot>_<window>_<mode>.png. On the tablet the board beside the editor edges the three ideas like
// it in the accent (Robolectric can't draw the GL map, so the 3D rings and the drop target are the
// emulator's, IdeaShapesMapTest); on the phone the strip under the details is the way to them. Each still
// is read back: the window's size, the page's ground (the scrim's under a sheet), and none of the logo's coral.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdeaShapesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() { System.setProperty("roborazzi.test.record", "true") }

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(window: String, dark: Boolean) = checks {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, ideas = ShapesFixture.notes, folderList = ShapesFixture.folders))
        IdeasSettings(compose.activity).setLook(MapLook.TWO_D)
        compose.setContent {
            SortaTheme(dark = isSystemInDarkTheme()) {
                WindowShapeHost { IdeasView(model, start = IdeasStart(editing = ShapesFixture.barrel.id), undoStays = true) }
            }
        }
        fun shot(name: String, over: Boolean) {
            compose.mainClock.advanceTimeBy(1_000) // a sheet's coming up, finished
            compose.waitForIdle()
            val full = "shapes_${name}_${window}_${if (dark) "dark" else "light"}"
            val file = File("build/outputs/roborazzi/$full.png")
            captureScreenRoboImage(file.path)
            look(this, full, file, dark, over)
        }
        fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        fun more(tag: String) {
            compose.onNode(hasContentDescription(s(R.string.sort_more)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
            compose.onNode(hasTestTag(tag)).performClick()
            compose.waitForIdle()
        }

        // the ideas like it: the strip, and on the tablet the board's edges beside it
        waitFor(IdeaShapeTags.ECHOES)
        check("$window: three ideas like it", compose.onAllNodes(hasTestTag(IdeaShapeTags.ECHO)).fetchSemanticsNodes().size == 3)
        shot("echoes", over = false)
        // the split chooser, the second sentence chosen
        more(IdeaShapeTags.MENU_SPLIT)
        waitFor(IdeaShapeTags.SPLIT)
        compose.onNode(hasText("Catch the rain from the gutter.") and hasAnyAncestor(hasTestTag(IdeaShapeTags.SPLIT))).performClick()
        shot("split", over = true)
        compose.onNode(hasText(s(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(IdeaShapeTags.SPLIT))).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(IdeaShapeTags.SPLIT)).fetchSemanticsNodes().isEmpty() }
        // the collide card, with words written
        more(IdeaShapeTags.MENU_COLLIDE)
        waitFor(IdeaShapeTags.PICKER)
        compose.onNode(hasText("Book the train to the coast") and hasAnyAncestor(hasTestTag(IdeaShapeTags.PICKER))).performClick()
        waitFor(IdeaShapeTags.COLLIDE_FIELD)
        compose.onNode(hasTestTag(IdeaShapeTags.COLLIDE_FIELD)).performTextInput("A rain barrel at the station")
        shot("collide", over = true)
        compose.onNode(hasText(s(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(IdeaShapeTags.COLLIDE))).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(IdeaShapeTags.COLLIDE)).fetchSemanticsNodes().isEmpty() }
        // a join's result: the joined text and its notice with Undo
        more(IdeaShapeTags.MENU_JOIN)
        waitFor(IdeaShapeTags.PICKER)
        compose.onNode(hasText("Fix the gutter") and hasAnyAncestor(hasTestTag(IdeaShapeTags.PICKER))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(s(R.string.join_done))).fetchSemanticsNodes().isNotEmpty() }
        check("$window: the gutter joined the barrel", model.book.note(ShapesFixture.gutter.id) == null)
        shot("join", over = false)
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
        // the top-leading corner is the Sort side's page on both windows (on the tablet the board is trailing)
        val corner = image.getRGB(2, 2) and 0xFFFFFF
        if (over) c.check("$name: under the sheet, the scrim darkens the page", corner != page && luma(corner) < luma(page), "${hx(corner)} vs ${hx(page)}")
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

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}
