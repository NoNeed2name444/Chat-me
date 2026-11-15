package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Capsule
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.ImportCounts
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.hx
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset

// The capsule on screen (S06d A4, A6): the quiet offer under the capture well (capsule_offer_<window>_<mode>.png),
// its words and two 48 dp actions, Not now putting it away and marking it offered; the More menu's two
// rows; and the page itself, written with the board's picture to build/outputs/capsule/capsule-android.html
// for a browser to render, then opened again through the store. Made-up ideas, none medical.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapsuleRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() { System.setProperty("roborazzi.test.record", "true") }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }

    private fun offer(window: String, dark: Boolean) = checks {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir))
        // two panes show the map beside Sort: the board, which Robolectric draws
        IdeasSettings(compose.activity).setLook(com.cramdown.sorta.ideas.MapLook.TWO_D)
        compose.setContent { SortaTheme(dark = isSystemInDarkTheme()) { WindowShapeHost { IdeasView(model, start = IdeasStart(capsuleOffer = true)) } } }
        settle()
        check("$window: the offer is up", shown(CapsuleTags.OFFER))
        check("$window: its words", compose.onAllNodes(hasText(compose.activity.getString(R.string.capsule_offer))).fetchSemanticsNodes().isNotEmpty())
        val density = compose.activity.resources.displayMetrics.density
        for (tag in listOf(CapsuleTags.OFFER_SAVE, CapsuleTags.OFFER_LATER)) {
            val b = compose.onNode(hasTestTag(tag)).fetchSemanticsNode().boundsInRoot
            check("$window: $tag is 48 dp at least", b.width >= 48 * density - 0.5f && b.height >= 48 * density - 0.5f, "${b.width} x ${b.height}")
        }
        val offered = IdeasSettings(compose.activity).capsuleOffered
        check("$window: shown, it is marked offered", offered != null)
        val name = "capsule_offer_${window}_${if (dark) "dark" else "light"}"
        val file = File("build/outputs/roborazzi/$name.png")
        captureScreenRoboImage(file.path)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        check("$name: the PNG reads back", image != null)
        if (image != null) {
            val page = WardPalette.hex(WardToken.background, dark)
            val corner = image.getRGB(image.width - 2, image.height - 2) and 0xFFFFFF
            check("$name: the ground is the page", corner == page, "${hx(corner)} vs ${hx(page)}")
            val coral = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).count { (it and 0xFFFFFF) == 0xC9553E }
            check("$name: no pixel is the logo's coral", coral == 0, "$coral pixels")
        }
        compose.onNode(hasTestTag(CapsuleTags.OFFER_LATER)).performClick(); settle()
        check("$window: Not now puts it away", !shown(CapsuleTags.OFFER))
        check("$window: and it stays marked offered", IdeasSettings(compose.activity).capsuleOffered == offered)
        // the More menu offers both
        compose.onNode(hasTestTag(IdeasTags.MORE)).performClick(); settle()
        for (words in listOf(R.string.more_save_capsule, R.string.more_open_capsule)) check("$window: More has ${compose.activity.getString(words)}",
            compose.onAllNodes(hasText(compose.activity.getString(words))).fetchSemanticsNodes().isNotEmpty())
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun offerPhoneLight() = offer("phone", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun offerPhoneDark() = offer("phone", dark = true)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun offerTabletLight() = offer("tablet", dark = false)

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun thePageWithItsPicture() = checks {
        var measurer: TextMeasurer? = null
        var density: Density? = null
        compose.setContent { measurer = rememberTextMeasurer(); density = LocalDensity.current }
        compose.waitForIdle()
        val book = IdeaBook(IdeasFixture.notes, IdeasFixture.folders)
        lateinit var html: String
        compose.runOnIdle { html = capsuleHtml(book, straight = false, measurer!!, density!!, now = 1_794_682_800_000L, zone = ZoneOffset.UTC) }
        val picture = compose.runOnIdle { capsulePicture(book, false, measurer!!, density!!) }
        check("the picture is a PNG", picture != null && picture.png.size > 1000 && picture.png[1] == 'P'.code.toByte())
        check("at most 960 across", picture != null && picture.width in 1..960 && picture.height in 1..960, "${picture?.width} x ${picture?.height}")
        val out = File("build/outputs/capsule").also { it.mkdirs() }
        File(out, "capsule-android.html").writeText(html)
        check("the page names every idea", IdeasFixture.notes.all { Capsule.escape(it.title) in html })
        check("its picture is in it", "data:image/png;base64," in html)
        check("no picture with no ideas", compose.runOnIdle { capsulePicture(IdeaBook(emptyList(), emptyList()), false, measurer!!, density!!) } == null)
        // and opened again: every idea and folder
        val store = IdeaStore(File(compose.activity.cacheDir, "capsule-open").also { it.deleteRecursively(); it.mkdirs() })
        check("opened again, everything comes in", store.importCapsule(html) == ImportCounts(ideas = 7, folders = 3, links = 1,
            placed = IdeasFixture.notes.count { it.boardX != 0.0 || it.boardY != 0.0 }))
        check("the same ideas", store.notes.map { it.id to it.title } == IdeasFixture.notes.map { it.id to it.title })
    }
}
