package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

// S05b A6: Map mode on made-up ideas (IdeasFixture), on the windows of SortRenderTest, light and
// dark, written to build/outputs/roborazzi/ as mapmode_<screen>_<window>_<mode>.png. Robolectric
// can't draw the GL map, so the map is the 2D look's board; the 3D looks' renders are the
// emulator's (androidTest MapModeSpaceRenderTest, MapRenderTest). Each window takes the board, an
// empty store's map, and the board with S05c's stacked places (cards on one place and at (0, 0),
// drawn apart): on a phone and a closed foldable, Map mode with its switch; on a tablet and an
// open foldable, the two panes, Sort beside the map. A7's font scale 2.0 on a phone takes the board
// with the switch, the look chip's menu open, the list sheet and the stacked board
// (mapmode_*_phone_scale2_light.png), by night too (S05m item 6). S05m item 5: on the phone and the
// tablet, the stacked board with a finger resting on the boiler's card, its whole title over it
// (mapmode_whole_title_*.png).
// Each PNG is read back as SortRenderTest's are: the window's size, the page's colour as its ground
// (under the sheet the scrim darkens it), and no pixel the logo's coral.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapModeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
    }

    /** A screen: its name in the file, whether its store is filled, and what is opened over the map. */
    private enum class Over { NONE, LOOKS, LIST, PEEK }
    private class Shot(val name: String, val filled: Boolean, val over: Over = Over.NONE, val stacked: Boolean = false,
                       val straight: Boolean = false)

    // "stacked": the board with S05c's places (IdeasFixture.stacked), cards on one place and at (0, 0)
    private val shots = listOf(Shot("board", true), Shot("empty", false), Shot("looks", true, Over.LOOKS), Shot("list", true, Over.LIST),
        Shot("stacked", true, stacked = true),
        // S05j item 5: the Board with Lines: Straight, beside iOS's 49-1-board-straight-lines
        Shot("board_straight", true, straight = true),
        // S05m item 5: a finger resting on the long title's card
        Shot("whole_title", true, Over.PEEK, stacked = true))

    private var which by mutableIntStateOf(0)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(window: String, dark: Boolean, names: List<String> = listOf("board", "empty", "stacked", "board_straight"), scale: Float = 1f) = checks {
        val list = shots.filter { it.name in names }
        val models = list.map {
            IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, it.filled, if (it.stacked) IdeasFixture.stacked else IdeasFixture.notes))
        }
        IdeasSettings(compose.activity).setLook(MapLook.TWO_D)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                SortaTheme(dark = isSystemInDarkTheme()) {
                    // each shot starts as Sorta does, on Sort, and is taken to Map mode as a person would
                    WindowShapeHost { key(which) { IdeasView(models[which]) } }
                }
            }
        }
        for (i in list.indices) {
            val shot = list[i]
            IdeasSettings(compose.activity).setStraightLines(shot.straight)
            which = i
            val name = "mapmode_${shot.name}_${window}_${if (dark) "dark" else "light"}"
            open(this, name, shot)
            val file = File("build/outputs/roborazzi/$name.png")
            captureScreenRoboImage(file.path)
            if (shot.over == Over.PEEK) lift()
            look(this, name, file, dark, shot.over == Over.LIST)
        }
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Map mode, where the switch is (one screen); then the look menu or the list sheet, when asked. */
    private fun open(c: WardChecks, name: String, shot: Shot) {
        compose.waitForIdle()
        // two panes show the map beside Sort from the start; one screen has the switch: its strip,
        // or at the accessibility sizes its disc, whose tap lists the modes in a sheet
        val paned = shown(IdeasTags.CAPTURE) && (shown(MapLook.CHIP) || shown(IdeasTags.MAP_EMPTY))
        if (!paned) {
            if (shown(SortaMode.MAP.identifier)) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
            else {
                compose.onNode(hasTestTag(SortaMode.TOGGLE)).performClick()
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNode(hasContentDescription(compose.activity.getString(SortaMode.MAP.spoken))).performClick()
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        when (shot.over) {
            Over.LOOKS -> compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
            Over.LIST -> compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
            Over.PEEK -> {
                // a finger down and resting, the clock held short of the hold that starts a connection
                compose.mainClock.autoAdvance = false
                boiler().performTouchInput { down(center) }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeByFrame()
                c.check("$name: the whole title over the card", shown(BOARD_PEEK_TAG))
                return
            }
            Over.NONE -> {}
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000) // the sheet's and the menu's coming up, finished
        compose.waitForIdle()
        c.check("$name: the map is up", if (shot.filled) shown(MapLook.CHIP) else shown(IdeasTags.MAP_EMPTY))
        when (shot.over) {
            Over.LOOKS -> c.check("$name: the look menu is open", MapLook.entries.all { shown(it.identifier) })
            Over.LIST -> c.check("$name: the list sheet is up", shown(MapTags.LIST))
            Over.NONE -> c.check("$name: ${if (paned) "two panes, Sort beside the map" else "the switch is there"}",
                if (paned) shown(IdeasTags.CAPTURE) else shown(SortaMode.SORT.identifier) || shown(SortaMode.TOGGLE))
        }
    }

    private fun boiler() = compose.onNode(hasTestTag(BOARD_CARD_TAG) and hasContentDescription(IdeasFixture.boiler.title, substring = true))

    /** The resting finger lifted, the clock running again. */
    private fun lift() {
        boiler().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun look(c: WardChecks, name: String, file: File, dark: Boolean, scrim: Boolean) {
        c.check("$name: the PNG is written", file.isFile && file.length() > 0, file.absolutePath)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        c.check("$name: the PNG reads back", image != null)
        if (image == null) return
        val metrics = compose.activity.resources.displayMetrics
        c.check("$name: the PNG is the window's size", image.width == metrics.widthPixels && image.height == metrics.heightPixels,
            "${image.width} x ${image.height} vs ${metrics.widthPixels} x ${metrics.heightPixels}")
        val page = WardPalette.hex(WardToken.background, dark)
        val corner = (if (scrim) image.getRGB(image.width - 2, 2) else image.getRGB(image.width - 2, image.height - 2)) and 0xFFFFFF
        if (scrim) c.check("$name: under the sheet, the scrim darkens the page", corner != page && luma(corner) < luma(page),
            "${hx(corner)} vs ${hx(page)}")
        else c.check("$name: the ground is the ${if (dark) "dark" else "light"} page", corner == page, "${hx(corner)} vs ${hx(page)}")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val coral = pixels.count { (it and 0xFFFFFF) == LOGO_CORAL }
        c.check("$name: no pixel is the logo's coral ${hx(LOGO_CORAL)}", coral == 0, "$coral pixels")
    }

    private fun luma(x: Int): Int = ((x shr 16) and 0xFF) * 299 + ((x shr 8) and 0xFF) * 587 + (x and 0xFF) * 114

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone", dark = false, names = listOf("board", "empty", "stacked", "board_straight", "whole_title"))

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone", dark = true, names = listOf("board", "empty", "stacked", "board_straight", "whole_title"))

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLight() = render("tablet", dark = false, names = listOf("board", "empty", "stacked", "board_straight", "whole_title"))

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletDark() = render("tablet", dark = true, names = listOf("board", "empty", "stacked", "board_straight", "whole_title"))

    // a book-style foldable: closed, its outer screen; open, its inner screen (as SortRenderTest)
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun foldableClosedLight() = render("foldable_closed", dark = false)

    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-night-xxhdpi")
    @Test fun foldableClosedDark() = render("foldable_closed", dark = true)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun foldableOpenLight() = render("foldable_open", dark = false)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-night-xhdpi")
    @Test fun foldableOpenDark() = render("foldable_open", dark = true)

    // A7: font scale 2.0 on a phone: the switch (on the board), the look menu open, the list sheet
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneAtFontScaleTwo() = render("phone_scale2", dark = false, names = listOf("board", "looks", "list", "stacked"), scale = 2f)

    // S05m item 6: the same by night
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneAtFontScaleTwoDark() = render("phone_scale2", dark = true, names = listOf("board", "looks", "list", "stacked"), scale = 2f)

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}
