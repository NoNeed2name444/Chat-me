package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaLens
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.hx
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.TimeZone

// The lenses on the board (S06d A2, A3, A6), as a person uses them: Map mode's lens row under the top
// strip, one lens on (Late night), then two (Late night and Questions), then a third that leaves
// nothing (Alone), each chip's name, count and checked state as TalkBack reads them and its 48 dp
// target, the board's cards that don't pass faded, untouchable and unread, and the empty line. Light
// and dark, on a phone and a tablet, written to build/outputs/roborazzi/ as lens_<one|two>_<window>_<mode>.png;
// the 3D looks' fade is the filter's own path (IdeaLensVisibilityTest). Made-up ideas, none medical,
// in UTC so the late ones are late wherever the test runs.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapLensRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var zone: TimeZone? = null

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun zoneBack() { zone?.let { TimeZone.setDefault(it) } }

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    /**
     * IdeasFixture's ideas, a few changed so each lens passes some: two questions (the gate, the bread),
     * two caught late (the compost at 2:10 AM, the bread at 11:30 PM), three unfinished (the boiler,
     * the brushes, the compost) and three alone (the boiler, the compost, the bread).
     */
    private val ideas = IdeasFixture.notes.map { n ->
        when (n.id) {
            IdeasFixture.gate.id -> n.copy(body = "Which screws?")
            IdeasFixture.compost.id -> n.copy(createdAt = ms("2026-10-27T02:10:00Z"))
            IdeasFixture.bread.id -> n.copy(title = "Seeded bread?", createdAt = ms("2026-10-26T23:30:00Z"))
            else -> n
        }
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun cards() = compose.onAllNodes(hasTestTag(BOARD_CARD_TAG)).fetchSemanticsNodes()
        .map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() }
    private fun chip(lens: IdeaLens) = compose.onNode(hasTestTag(MapLensTags.chip(lens)))
    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }

    private fun render(window: String, dark: Boolean) = checks {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, true, ideas))
        IdeasSettings(compose.activity).setLook(MapLook.TWO_D)
        compose.setContent { SortaTheme(dark = isSystemInDarkTheme()) { WindowShapeHost { IdeasView(model) } } }
        settle()
        if (shown(SortaMode.MAP.identifier)) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        settle()
        check("$window: the lens row is up", shown(MapLensTags.ROW))
        check("$window: all seven cards before a lens", cards().size == 7, cards().toString())
        val density = compose.activity.resources.displayMetrics.density
        fun read(lens: IdeaLens, count: Int, on: Boolean) {
            val node = chip(lens).fetchSemanticsNode()
            val name = compose.activity.getString(lens.title)
            val spoken = compose.activity.resources.getQuantityString(R.plurals.lens_spoken, count, name, count)
            check("$window: ${lens.id} reads \"$spoken\"", node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(spoken) == true,
                node.config.getOrNull(SemanticsProperties.ContentDescription).toString())
            check("$window: ${lens.id} is a checkbox", node.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox)
            check("$window: ${lens.id} is ${if (on) "selected" else "not selected"}",
                node.config.getOrNull(SemanticsProperties.Selected) == on)
            val b = node.boundsInRoot
            check("$window: ${lens.id} is 48 dp at least", b.width >= 48 * density - 0.5f && b.height >= 48 * density - 0.5f, "${b.width} x ${b.height}")
        }
        read(IdeaLens.QUESTIONS, 2, false); read(IdeaLens.LATE_NIGHT, 2, false); read(IdeaLens.UNFINISHED, 3, false); read(IdeaLens.ALONE, 3, false)
        // every chip whole on screen, the narrowest window too: the row wraps rather than hide one
        val width = compose.activity.resources.displayMetrics.widthPixels
        for (lens in IdeaLens.entries) {
            val b = chip(lens).fetchSemanticsNode().boundsInRoot
            check("$window: ${lens.id} is whole on screen", b.left >= 0f && b.right <= width + 0.5f && b.width > 0f, "$b of $width")
        }

        // one lens: the late ideas, the rest faded and unread
        chip(IdeaLens.LATE_NIGHT).performClick(); settle()
        check("$window: Late night keeps the compost and the bread", cards().map { it.substringBefore(",") }.sorted() ==
            listOf("Seeded bread?", "Turn the compost"), cards().toString())
        read(IdeaLens.LATE_NIGHT, 2, true); read(IdeaLens.QUESTIONS, 1, false); read(IdeaLens.UNFINISHED, 1, false); read(IdeaLens.ALONE, 2, false)
        shot(this, "lens_one_${window}_${if (dark) "dark" else "light"}", dark)

        // two lenses: only the ideas that pass both
        chip(IdeaLens.QUESTIONS).performClick(); settle()
        check("$window: Late night and Questions keep the bread", cards().map { it.substringBefore(",") } == listOf("Seeded bread?"),
            cards().toString())
        read(IdeaLens.QUESTIONS, 1, true); read(IdeaLens.LATE_NIGHT, 1, true)
        check("$window: no empty line while one passes", !shown(MapPanelTags.FILTER_EMPTY))
        shot(this, "lens_two_${window}_${if (dark) "dark" else "light"}", dark)

        // nothing passes three: the empty line; then off again, everything back
        chip(IdeaLens.UNFINISHED).performClick(); settle()
        check("$window: no card passes three", cards().isEmpty())
        check("$window: the empty line says so", shown(MapPanelTags.FILTER_EMPTY))
        for (lens in listOf(IdeaLens.UNFINISHED, IdeaLens.QUESTIONS, IdeaLens.LATE_NIGHT)) chip(lens).performClick()
        settle()
        check("$window: every card back with no lens on", cards().size == 7, cards().toString())
    }

    /** The row over Space's night chrome (the strip is night over every 3D look), Late night on. */
    private fun renderSpace(window: String) = checks {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, true, ideas))
        IdeasSettings(compose.activity).setLook(MapLook.SPACE)
        compose.setContent { SortaTheme(dark = isSystemInDarkTheme()) { WindowShapeHost { IdeasView(model) } } }
        settle()
        if (shown(SortaMode.MAP.identifier)) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        settle()
        check("$window: the lens row over Space", shown(MapLensTags.ROW))
        chip(IdeaLens.LATE_NIGHT).performClick(); settle()
        check("$window: Late night is on over Space", chip(IdeaLens.LATE_NIGHT).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true)
        // the ground is Space's sky, not the page
        shot(this, "lens_space_$window", dark = true, page = false)
    }

    private fun shot(c: WardChecks, name: String, dark: Boolean, page: Boolean = true) {
        val file = File("build/outputs/roborazzi/$name.png")
        captureScreenRoboImage(file.path)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        c.check("$name: the PNG reads back", image != null, file.absolutePath)
        if (image == null) return
        if (page) {
            val ground = WardPalette.hex(WardToken.background, dark)
            val corner = image.getRGB(image.width - 2, image.height - 2) and 0xFFFFFF
            c.check("$name: the ground is the page", corner == ground, "${hx(corner)} vs ${hx(ground)}")
        }
        val coral = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).count { (it and 0xFFFFFF) == 0xC9553E }
        c.check("$name: no pixel is the logo's coral", coral == 0, "$coral pixels")
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone", dark = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLight() = render("tablet", dark = false)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletDark() = render("tablet", dark = true)

    // a foldable, closed: the narrowest window, where the row is cut soonest
    @Config(sdk = [35], qualifiers = "w360dp-h748dp-port-notnight-xxhdpi")
    @Test fun foldableClosedLight() = render("foldable_closed", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneOverSpace() = renderSpace("phone")
}
