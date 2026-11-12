package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Which bodies show a name in Space and Neurons, and where (MapNameShow, S05f), as iOS's
 * updateLabels, wantLabel and placeLabel: none at rest; the pressed, held, chosen and hovered
 * bodies' only; the pop; the place above the body for a camera turned any way; the cut.
 */
class MapNameShowTest {
    private val bodies = listOf(
        NameBody(0, 100f, 200f, 40f),
        NameBody(1, 300f, 200f, 20f),
        NameBody(2, 500f, 400f, 30f, crisp = false),
        NameBody(3, 120f, 210f, 2f),
    )
    private val byIndex = bodies.associateBy { it.index }
    private val slop = 8f

    @Test fun noneAtRest() {
        assertEquals(emptyList<Int>(), MapNameShow.wanted(null, null, null, null, byIndex))
    }

    @Test fun thePressedBodyWhileTheFingerIsDown() {
        val press = MapNameShow.Press(slop)
        press.down(MapNameShow.pick(bodies, 104f, 196f, 40f)?.index)
        assertEquals(0, press.pressed)
        assertEquals(listOf(0), MapNameShow.wanted(null, press.pressed, null, null, byIndex))
        press.move(3f); press.move(4f)
        assertEquals("still within the slop", 0, press.pressed)
        press.up()
        assertNull("gone when it lifts", press.pressed)
        assertEquals(emptyList<Int>(), MapNameShow.wanted(null, press.pressed, null, null, byIndex))

        press.down(1)
        press.move(5f); press.move(5f)
        assertNull("gone once it moves past the slop", press.pressed)
        press.move(-20f)
        assertNull("and it does not come back", press.pressed)

        press.down(1)
        press.fingers(1)
        assertEquals(1, press.pressed)
        press.fingers(2)
        assertNull("gone when it becomes two fingers", press.pressed)

        press.down(0)
        press.cancel()
        assertNull("gone when the gesture is cancelled", press.pressed)

        press.down(MapNameShow.pick(bodies, 700f, 700f, 40f)?.index)
        assertNull("a finger on empty space presses nothing", press.pressed)
    }

    @Test fun theHeldAndChosenBodies() {
        assertEquals(listOf(1), MapNameShow.wanted(null, null, 1, null, byIndex))
        assertEquals(listOf(0), MapNameShow.wanted(null, null, null, 0, byIndex))
        // on Android a hold also chooses the body: one name, not two
        assertEquals(listOf(1), MapNameShow.wanted(null, null, 1, 1, byIndex))
        // iOS's order: hovered, pressed, held, chosen
        assertEquals(listOf(3, 1, 0), MapNameShow.wanted(3, 1, null, 0, byIndex))
        assertEquals("a body not on screen has none", emptyList<Int>(), MapNameShow.wanted(null, null, 9, 8, byIndex))
        aBodyPickedUpTakesTheChosenSlot()
    }

    /**
     * S05g2 repair 3 (the critique's finding on mapmode-drag-cell-neurons-*): a cell picked up to move,
     * a member inside it chosen. As on iOS, where grab sets `selected` to the held body
     * (GraphMotion.swift:1253) and updateLabels wants `selected`'s name (:2186), the held body takes the
     * chosen slot: the member's name gives way till the let-go, and no pill drawn lies over the held one.
     * Part of [theHeldAndChosenBodies], so CI's count of this class stays as it is.
     */
    private fun aBodyPickedUpTakesTheChosenSlot() {
        val density = 2.75f
        val cell = NameBody(0, 350f, 650f, 120f)
        val member = NameBody(1, 480f, 700f, 140f)
        val near = mapOf(0 to cell, 1 to member)
        val texts = mapOf(0 to NameText(1f, -40f, 380f, 11f), 1 to NameText(1f, -40f, 460f, 11f))
        fun pills(names: List<Int>) = names.map { i -> MapNameShow.inside(MapNameShow.place(near.getValue(i), texts.getValue(i), density, 1f), 1080f, MapNameShow.EDGE_DP * density) }
        fun overlaps(a: NamePill, b: NamePill) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        // the fixture is a real overlap: the two names, both up, cross
        val both = pills(listOf(0, 1))
        assertTrue("the fixture's two names overlap", overlaps(both[0], both[1]))

        // held (the map's held slot is the body picked up) and grabbed: the cell's name alone
        val shown = MapNameShow.wanted(null, null, 0, 1, near, grabbed = 0)
        assertEquals(listOf(0), shown)
        val drawn = pills(shown)
        val held = drawn.single { it.index == 0 }
        assertTrue("no other name lies over the held body's", drawn.none { it.index != 0 && overlaps(it, held) })
        // pressed at first too, then past the slop: still the cell's alone
        assertEquals(listOf(0), MapNameShow.wanted(null, 0, 0, 1, near, grabbed = 0))
        // let go: the chosen member's name is back
        assertEquals(listOf(1), MapNameShow.wanted(null, null, null, 1, near, grabbed = null))
        // the held body keeps its name outside the crisp zone, as iOS's selected does
        val faint = mapOf(0 to cell.copy(crisp = false), 1 to member)
        assertEquals(listOf(0), MapNameShow.wanted(null, null, 0, 1, faint, grabbed = 0))
        // picked up with nothing chosen, or the chosen body itself: one name
        assertEquals(listOf(0), MapNameShow.wanted(null, null, 0, null, near, grabbed = 0))
        assertEquals(listOf(1), MapNameShow.wanted(null, null, 1, 1, near, grabbed = 1))
    }

    @Test fun aHoveredBody() {
        var hovered: Int? = null
        val over = MapNameShow.pick(bodies, 298f, 205f, 40f)?.index
        hovered = MapNameShow.hover(hovered, MapNameShow.HoverEvent.ENTER, buttonDown = false, picked = over)
        assertEquals(1, hovered)
        assertEquals(listOf(1), MapNameShow.wanted(hovered, null, null, null, byIndex))
        hovered = MapNameShow.hover(hovered, MapNameShow.HoverEvent.MOVE, buttonDown = false, picked = MapNameShow.pick(bodies, 101f, 199f, 40f)?.index)
        assertEquals("it follows the pointer", 0, hovered)
        hovered = MapNameShow.hover(hovered, MapNameShow.HoverEvent.MOVE, buttonDown = true, picked = null)
        assertEquals("a button down is a press, not a hover", 0, hovered)
        hovered = MapNameShow.hover(hovered, MapNameShow.HoverEvent.MOVE, buttonDown = false, picked = MapNameShow.pick(bodies, 800f, 800f, 40f)?.index)
        assertNull("over empty space, none", hovered)
        hovered = MapNameShow.hover(1, MapNameShow.HoverEvent.EXIT, buttonDown = false, picked = 1)
        assertNull("gone when it leaves", hovered)
        assertEquals(emptyList<Int>(), MapNameShow.wanted(hovered, null, null, null, byIndex))
    }

    @Test fun outsideTheCrispZoneOnlyTheChosen() {
        assertEquals(emptyList<Int>(), MapNameShow.wanted(2, 2, 2, null, byIndex))
        assertEquals(listOf(2), MapNameShow.wanted(null, null, null, 2, byIndex))
        assertEquals("hovered and chosen, outside the zone: the chosen one's name", listOf(2), MapNameShow.wanted(2, null, null, 2, byIndex))
    }

    @Test fun thePickIsTheTapsPick() {
        assertEquals("the nearest", 0, MapNameShow.pick(bodies, 108f, 204f, 40f)?.index)
        assertNull("a speck is never picked", MapNameShow.pick(listOf(NameBody(5, 10f, 10f, 2f)), 10f, 10f, 40f))
        assertNull("too far", MapNameShow.pick(bodies, 100f, 260f, 40f))
    }

    @Test fun thePop() {
        assertEquals(0.85f, MapNameShow.growth(0.0, lively = true), 1e-6f)
        assertEquals(1f, MapNameShow.growth(0.12, lively = true), 1e-6f)
        assertEquals(1f, MapNameShow.growth(5.0, lively = true), 1e-6f)
        val half = MapNameShow.growth(0.06, lively = true)
        assertEquals("eased out: 1 − (1 − t)²", 0.85f + 0.15f * 0.75f, half, 1e-5f)
        assertTrue("ahead of a straight line at half time", half > 0.925f)
        var last = 0f
        for (k in 0..12) { val g = MapNameShow.growth(k * 0.01, true); assertTrue(g >= last); last = g }
        assertEquals("animations off: at once", 1f, MapNameShow.growth(0.0, lively = false), 0f)
        // the board: a name goes up at 85 %, is full at 0.12 s, and goes the instant it is not wanted
        val board = MapNameShow.Board()
        board.keep(listOf(4), 10.0)
        assertEquals(0.85f, board.scale(4, 10.0, true), 1e-6f)
        assertEquals(1f, board.scale(4, 10.12, true), 1e-6f)
        assertEquals(1f, board.scale(4, 10.0, false), 0f)
        board.keep(emptyList(), 10.2)
        assertEquals(emptyList<Int>(), board.shown)
        board.keep(listOf(4), 11.0)
        assertEquals("up again: it pops again", 0.85f, board.scale(4, 11.0, true), 1e-6f)
    }

    @Test fun theBoardStylesEachName() {
        val board = MapNameShow.Board()
        val night = LabelBackdrop.flat(LabelRgb(0.03, 0.03, 0.06))
        val pale = LabelBackdrop.flat(LabelRgb(0.80, 0.82, 0.86))
        var behind = night
        val measure: (Int) -> Pair<LabelBackdrop, LabelRgb>? = { i -> if (i == 9) null else behind to LabelRgb.WHITE }
        board.keep(listOf(1, 9), 0.0)
        assertTrue("popping: draw the next frame", board.step(0.0, true, LabelSettings(), 0.2, measure))
        assertFalse(board.look(1, LabelSettings(), measure)!!.darkText)
        assertNull("a body not on screen has no style", board.look(9, LabelSettings(), measure))
        assertFalse("popped, and its style still: nothing to draw", board.step(0.15, true, LabelSettings(), 0.2, measure))
        // a pale backdrop, held: the words turn dark, easing
        behind = pale
        board.step(0.36, true, LabelSettings(), 0.2, measure)
        board.step(0.57, true, LabelSettings(), 0.2, measure)
        assertTrue("easing to dark words", board.step(0.6, true, LabelSettings(), 0.2, measure))
        var t = 0.6
        while (board.step(t, true, LabelSettings(), 0.2, measure) && t < 5) t += 1.0 / 60
        assertTrue(board.look(1, LabelSettings(), measure)!!.darkText)
        // Increase Contrast: the strongest style at once
        val strong = MapNameShow.Board()
        strong.keep(listOf(1), 0.0)
        val look = strong.look(1, LabelSettings(increaseContrast = true), measure)!!
        assertEquals(LabelRgb.BLACK, look.pill)
        assertEquals(LabelRgb.WHITE, look.rim)
        assertEquals(LabelRgb.WHITE, look.text)
    }

    /** A name over a body near the side stays on the screen: moved across, as little as it takes (S05f renders check). */
    @Test fun aNameStaysInsideTheView() {
        val density = 2.75f
        val text = NameText(1f, -38f, 381f, 11f)
        val width = 1080f
        val margin = MapNameShow.EDGE_DP * density
        // a body near the right edge, as Neurons' "Kitchen · 2 ideas" on the phone
        val right = MapNameShow.place(NameBody(0, 1000f, 900f, 120f), text, density, 1f)
        assertTrue("it would run off", right.right > width)
        val kept = MapNameShow.inside(right, width, margin)
        assertEquals("its rim ends the margin inside the right edge", width - margin, kept.right, 1e-3f)
        val dx = kept.right - right.right
        assertEquals("the pill, the rim and the words move together", right.pillLeft + dx, kept.pillLeft, 1e-3f)
        assertEquals(right.pillRight + dx, kept.pillRight, 1e-3f)
        assertEquals(right.left + dx, kept.left, 1e-3f)
        assertEquals(right.textX + dx, kept.textX, 1e-3f)
        assertEquals("only across", right.top, kept.top, 0f)
        assertEquals(right.baseline, kept.baseline, 0f)
        assertEquals("its body stays where it is", right.bodyX, kept.bodyX, 0f)
        // and the left edge
        val left = MapNameShow.place(NameBody(1, 40f, 900f, 120f), text, density, 1f)
        assertEquals(margin, MapNameShow.inside(left, width, margin).left, 1e-3f)
        // one with room is not moved at all
        val middle = MapNameShow.place(NameBody(2, 540f, 900f, 120f), text, density, 1f)
        assertEquals(middle, MapNameShow.inside(middle, width, margin))
        // one wider than the room is centred in the view
        val wide = MapNameShow.place(NameBody(3, 900f, 900f, 120f), NameText(0f, -38f, 1200f, 11f), density, 1f)
        val centred = MapNameShow.inside(wide, width, margin)
        assertEquals(width / 2, (centred.left + centred.right) / 2, 1e-3f)
    }

    @Test fun thePlace() {
        val density = 2.5f
        val text = NameText(1f, -38f, 181f, 11f)
        val body = NameBody(0, 400f, 900f, 60f)
        val p = MapNameShow.place(body, text, density, 1f)
        val foot = 900f - (30f * 1.25f + 16f * density)
        assertEquals("the words' foot: 1.25 × its pick radius plus 16 dp above the body", foot, p.baseline + text.bottom, 1e-3f)
        val h = 21f * density
        assertEquals("words 21 dp tall", h, p.textSize, 1e-4f)
        assertEquals("padding across: 0.22 of the words' height each side", text.width + 2 * 0.22f * h, p.pillRight - p.pillLeft, 1e-3f)
        assertEquals("padding up and down: 0.10 each", text.height + 2 * 0.10f * h, p.pillBottom - p.pillTop, 1e-3f)
        assertEquals("the rim 0.12 bigger", (p.pillRight - p.pillLeft) + 0.12f * h, p.right - p.left, 1e-3f)
        assertEquals((p.pillBottom - p.pillTop) + 0.12f * h, p.bottom - p.top, 1e-3f)
        assertEquals("centred over the body", 400f, (p.left + p.right) / 2, 1e-3f)
        assertEquals("the words centred too", 400f, p.textX + text.left + text.width / 2, 1e-3f)
        assertEquals("the pill's middle half the words' height above their foot", foot - text.height / 2, (p.pillTop + p.pillBottom) / 2, 1e-3f)
        assertTrue("clear of the body", p.bottom < body.y - body.radius)
        // grown from 85 % about the words' foot, as iOS's label node about its origin
        val small = MapNameShow.place(body, text, density, 0.85f)
        assertEquals(foot, small.baseline + text.bottom * 0.85f, 1e-3f)
        assertEquals((p.right - p.left) * 0.85f, small.right - small.left, 1e-3f)
        assertEquals(h * 0.85f, small.textSize, 1e-4f)
    }

    @Test fun straightAboveTheProjectedBodyAtAnyTurn() {
        // place works on the screen alone, so on screen "up" is up whatever the camera does: what a
        // turn changes is where the body lands and how big it is drawn. Through the orbit camera at
        // many turns, the name sits straight above the projected body, clear of its projected radius
        val width = 1080f; val height = 2000f
        val text = NameText(0f, -40f, 200f, 10f)
        for (yaw in listOf(0f, 1f, 2.5f, -2f)) for (pitch in listOf(-1.2f, -0.4f, 0f, 0.7f, 1.4f)) {
            val camera = OrbitCamera(target = Vec3(0.3f, -0.2f, 0.1f), distance = 6f, yaw = yaw, pitch = pitch)
            val viewProj = Mat4.multiply(camera.projection(width / height), camera.view())
            val clip = FloatArray(4)
            Mat4.project(viewProj, 0.5f, 0.4f, -0.3f, clip)
            val x = (clip[0] / clip[3] * 0.5f + 0.5f) * width
            val y = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height
            val radius = 0.3f * camera.focalPixels(height) / clip[3]
            val p = MapNameShow.place(NameBody(0, x, y, radius * 2), text, 2.75f, 1f)
            assertTrue("yaw $yaw pitch $pitch: above", p.bottom < y - radius)
            assertEquals("yaw $yaw pitch $pitch: straight above", x, (p.left + p.right) / 2, 1e-2f)
            assertEquals(y - (radius * 1.25f + 16f * 2.75f), p.baseline + text.bottom, 1e-2f)
        }
    }

    @Test fun theCut() {
        val t28 = "a".repeat(28)
        assertEquals("28 characters kept whole", t28, MapNameShow.cut(t28))
        val t29 = "abcdefghijklmnopqrstuvwxyz" + "ABC"
        assertEquals("29 cut to 27 and an ellipsis", "abcdefghijklmnopqrstuvwxyzA…", MapNameShow.cut(t29))
        assertEquals(28, MapNameShow.cut(t29).length)
        // a folder's name: cut at 20 before its count, and not cut again
        val folder = UniverseTree.folderLabel("Weekend garden projects list", 3)
        assertEquals("Weekend garden proje… · 3 ideas", folder)
        assertEquals(folder, MapNameShow.cut(folder, folder = true))
        assertEquals("Twenty characters ok · empty", UniverseTree.folderLabel("Twenty characters ok", 0))
        assertTrue(abs(MapNameShow.cut("", false).length) == 0)
        // counted and cut by code point: an emoji across the 27th place stays whole
        val seedling = "\uD83C\uDF31"
        val emoji = "a".repeat(26) + seedling + seedling + "bc"
        assertEquals("a".repeat(26) + seedling + "…", MapNameShow.cut(emoji))
        val whole = "a".repeat(27) + seedling
        assertEquals("28 code points, 29 chars, kept whole", whole, MapNameShow.cut(whole))
        assertFalse("no lone surrogate", MapNameShow.cut("a".repeat(26) + seedling.repeat(3)).let { it.isNotEmpty() && Character.isHighSurrogate(it[it.length - 2]) })
    }

    // S05j 6c: a folder's name is counted and cut by code point too: an emoji across the 20th place stays whole
    @Test fun aFoldersEmojiAcrossTheCutStaysWhole() {
        val seedling = "🌱"
        assertEquals("a".repeat(19) + seedling + "… · 2 ideas", UniverseTree.folderLabel("a".repeat(19) + seedling + seedling + "x", 2))
        val whole = "a".repeat(19) + seedling
        assertEquals("20 code points, 21 chars, kept whole", "$whole · 1 idea", UniverseTree.folderLabel(whole, 1))
    }
}
