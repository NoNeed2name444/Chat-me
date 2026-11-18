package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Capsule
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.ImportCounts
import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.IdeaBoardSpread
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
// rows; and the page itself, written with the board's picture (the board's own drawing, CapsuleBoardShot)
// to build/outputs/capsule/capsule-android.html for a browser to render, then opened again through the
// store; and the picture at the board's size. Made-up ideas, none medical.
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

    /** One frame drawn, as a phone draws each: Robolectric draws the window only when asked, here by a capture of the screen. */
    private fun frame() = captureScreenRoboImage("build/tmp/capsule-shot-frame.png")

    /** The board's picture as Save a capsule draws it (CapsuleBoardShot), for [book] and then for no ideas, and the frame it was drawn to. */
    private class Shot(val picture: Capsule.Picture?, val frame: CapsuleFrame?, val cardWidth: Double, val empty: Capsule.Picture?,
                       val emptyDone: Boolean, val clickable: Int)

    private fun shoot(book: IdeaBook): Shot {
        val settings = IdeasSettings(compose.activity)
        var shown by mutableStateOf(book)
        var picture: Capsule.Picture? = null; var done = false
        var empty: Capsule.Picture? = null; var emptyDone = false
        var frame: CapsuleFrame? = null
        var cardWidth = 0.0
        compose.setContent {
            SortaTheme {
                key(shown) {
                    CapsuleBoardShot(shown, settings, straight = false) {
                        if (shown.notes.isEmpty()) { empty = it; emptyDone = true } else { picture = it; done = true }
                    }
                }
                // the frame as the shot works it out, at the picture's density
                CompositionLocalProvider(LocalDensity provides Density(PICTURE_SCALE, 1f)) {
                    val width = boardCardWidth().also { cardWidth = it }
                    frame = remember(width) { capsuleFrame(book, width, straight = false) }
                }
            }
        }
        compose.waitForIdle(); frame()
        compose.waitUntil(10_000) { done }
        // the shot adds nothing TalkBack reads or a finger reaches
        val clickable = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        shown = IdeaBook(emptyList(), emptyList())
        compose.waitForIdle(); frame()
        compose.waitUntil(10_000) { emptyDone }
        return Shot(picture, frame, cardWidth, empty, emptyDone, clickable)
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun thePageWithItsPicture() = checks {
        val book = IdeaBook(IdeasFixture.notes, IdeasFixture.folders)
        val shot = shoot(book)
        val picture = shot.picture
        val html = capsuleHtml(book, picture, now = 1_794_682_800_000L, zone = ZoneOffset.UTC)
        check("the picture is a PNG", picture != null && picture.png.size > 1000 && picture.png[1] == 'P'.code.toByte())
        check("at most 960 across", picture != null && picture.width in 1..960 && picture.height in 1..960, "${picture?.width} x ${picture?.height}")
        val out = File("build/outputs/capsule").also { it.mkdirs() }
        File(out, "capsule-android.html").writeText(html)
        check("the page names every idea", IdeasFixture.notes.all { Capsule.escape(it.title) in html })
        check("its picture is in it", "data:image/png;base64," in html)
        check("no picture with no ideas", shot.emptyDone && shot.empty == null)
        // and opened again: every idea and folder
        val store = IdeaStore(File(compose.activity.cacheDir, "capsule-open").also { it.deleteRecursively(); it.mkdirs() })
        check("opened again, everything comes in", store.importCapsule(html) == ImportCounts(ideas = 7, folders = 3, links = 1,
            placed = IdeasFixture.notes.count { it.boardX != 0.0 || it.boardY != 0.0 }))
        check("the same ideas", store.notes.map { it.id to it.title } == IdeasFixture.notes.map { it.id to it.title })
    }

    // The picture is the board's own drawing (IdeaBoard, held still), not a redraw: at the board's
    // size (every card's drawn place inside it, at twice the CSS pixels), on the day page in a dark
    // phone too, not blank, and out of TalkBack's reach (capsule_picture.png).
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun thePictureIsTheBoardAtItsSize() = checks {
        val book = IdeaBook(IdeasFixture.notes, IdeasFixture.folders)
        val shot = shoot(book)
        val picture = shot.picture; val frame = shot.frame
        check("a picture", picture != null && frame != null)
        if (picture == null || frame == null) return@checks
        check("as wide and tall as the board's frame", picture.width == frame.width && picture.height == frame.height,
            "${picture.width} x ${picture.height} vs ${frame.width} x ${frame.height}")
        check("the shot is out of TalkBack's and a finger's reach", shot.clickable == 0, "${shot.clickable} clickable nodes")
        val still = frame.still
        val card = IdeaBoardDrop.CARD_HEIGHT; val cardWidth = shot.cardWidth
        check("the title's 20 letters measured", cardWidth >= IdeaBoardDrop.CARD_WIDTH, "$cardWidth")
        val drawn = IdeaBoardSpread.places(book.notes.map { IdeaBoardSpread.Card(it.id, BoardPoint(it.boardX, it.boardY), cardWidth, card) })
        check("every idea has a drawn place", book.notes.all { drawn[it.id] != null })
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(picture.png))
        check("the PNG reads back", image != null)
        if (image == null) return@checks
        check("drawn at twice its CSS size", image.width == picture.width * 2 && image.height == picture.height * 2,
            "${image.width} x ${image.height} for ${picture.width} x ${picture.height}")
        File("build/outputs/roborazzi").mkdirs()
        javax.imageio.ImageIO.write(image, "png", File("build/outputs/roborazzi/capsule_picture.png"))
        val page = WardPalette.hex(WardToken.background, false)
        val corner = image.getRGB(1, 1) and 0xFFFFFF
        check("the ground is the day page, though the phone is dark", corner == page, "${hx(corner)} vs ${hx(page)}")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        fun off(rgb: Int) = kotlin.math.abs((rgb shr 16 and 0xFF) - (page shr 16 and 0xFF)) +
            kotlin.math.abs((rgb shr 8 and 0xFF) - (page shr 8 and 0xFF)) + kotlin.math.abs((rgb and 0xFF) - (page and 0xFF))
        val marked = pixels.count { off(it) > 24 }
        check("the cards and links are on it: not blank", marked > pixels.size / 100, "$marked of ${pixels.size}")
        // every card the board draws, wholly inside the picture
        for (note in book.notes) {
            val at = drawn[note.id] ?: continue
            val x = frame.width / 2.0 + still.panX + at.x * still.zoom
            val y = frame.height / 2.0 + still.panY + at.y * still.zoom
            check("${note.title}'s card is inside the picture", x - cardWidth / 2 * still.zoom >= -0.5 &&
                x + cardWidth / 2 * still.zoom <= frame.width + 0.5 && y - card / 2 * still.zoom >= -0.5 &&
                y + card / 2 * still.zoom <= frame.height + 0.5, "$x, $y in ${frame.width} x ${frame.height}")
        }
    }
}
