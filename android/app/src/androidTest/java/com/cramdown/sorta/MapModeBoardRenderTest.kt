package com.cramdown.sorta

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.BOARD_CARD_TAG
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardToken
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The look chip's menu over the 2D board at font scale 2 (S05m item 7; S05b's Moved item 16, where
 * Robolectric drew the menu with no surface), by day and by night: Sorta itself (MainActivity) on a
 * board of made-up ideas, taken to Map mode and to 2D by the look chip, the menu opened, one still each,
 * named mapmode-board-looks-scale2-<light|dark>-<device>.png in /sdcard/Download/sorta-renders.
 * Font scale 2.0 is set through the shell before the launch and read back from the activity; 1.0
 * comes back after. The check: inside the menu (its rows' union and the menu's 8 dp above and below),
 * beside and between the rows, every sampled pixel is the menu's colour, WardToken.background; and
 * the same places held a card or a line before the menu opened, so a missing surface would show.
 * Launched as MapModeNavBarRenderTest launches it, which this follows.
 */
@RunWith(AndroidJUnit4::class)
class MapModeBoardRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clearChoices() {
        // Ideas' remembered choices start clear: the map opens on Space, and 2D is chosen from the chip
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun looksAtFontScaleTwoByDay() = render(dark = false)
    @Test fun looksAtFontScaleTwoByNight() = render(dark = true)

    private fun render(dark: Boolean) {
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 2.0").close()
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (ui.executeShellCommand("settings get system font_scale").trim() != "2.0" && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200)
        }
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "board-looks-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        var scale = 0f
        val readBy = SystemClock.uptimeMillis() + 10_000
        do {
            scenario!!.onActivity { scale = it.resources.configuration.fontScale }
            if (abs(scale - 2f) < 0.01f) break
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < readBy)
        check(abs(scale - 2f) < 0.01f) { "the app's font scale reads $scale, not 2" }

        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        // a phone's switch; a wider window shows the map beside Sort already
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapLook.TWO_D.identifier)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.TWO_D.identifier)).performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(BOARD_CARD_TAG)).fetchSemanticsNodes().isNotEmpty() }
        val before = screen("the board")

        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { MapLook.entries.all { compose.onAllNodes(hasTestTag(it.identifier)).fetchSemanticsNodes().isNotEmpty() } }
        val name = "mapmode-board-looks-scale2-${if (dark) "dark" else "light"}-$device"
        shot(name) // kept even when a check below fails, to see why
        val after = screen(name)

        // the menu's rows on the screen (the menu is a popup, a window of its own)
        val rows = MapLook.entries.map { look ->
            val node = compose.onNode(hasTestTag(look.identifier)).fetchSemanticsNode()
            val at = node.positionOnScreen
            Rect(at.x, at.y, at.x + node.size.width, at.y + node.size.height)
        }.sortedBy { it.top }
        val px = context.resources.displayMetrics.density
        val inset = 4 * px
        val menu = Rect(rows.minOf { it.left }, rows.first().top - 8 * px, rows.maxOf { it.right }, rows.last().bottom + 8 * px)
        // beside the rows: a column in each row's leading and trailing padding, top to bottom of the menu;
        // between them: a line across in the menu's top and bottom padding and at each pair of rows' seam
        val places = mutableListOf<Pair<Int, Int>>()
        var y = menu.top + inset
        while (y <= menu.bottom - inset) {
            places += (menu.left + inset).roundToInt() to y.roundToInt()
            places += (menu.right - inset).roundToInt() to y.roundToInt()
            y += 2 * px
        }
        val across = listOf(menu.top + inset, menu.bottom - inset) + rows.zipWithNext { a, b -> (a.bottom + b.top) / 2 }
        for (line in across) {
            var x = menu.left + inset
            while (x <= menu.right - inset) { places += x.roundToInt() to line.roundToInt(); x += 4 * px }
        }
        val inside = places.filter { (x, yy) -> x in 0 until after.width && yy in 0 until after.height }
        check(inside.size == places.size && inside.isNotEmpty()) { "The menu for $name lies off the screen: $menu on ${after.width}x${after.height}" }

        val page = WardPalette.hex(WardToken.background, dark)
        val covered = inside.count { (x, yy) -> !near(before.getPixel(x, yy), page) }
        check(covered > 0) { "Nothing lay under the menu's place before it opened for $name ($menu), so a missing surface would not show" }
        val off = inside.filter { (x, yy) -> !near(after.getPixel(x, yy), page) }
        check(off.isEmpty()) {
            val (x, yy) = off.first()
            "${off.size} of ${inside.size} places in the menu are not its colour for $name: #%06X at $x, $yy against #%06X (the menu $menu)"
                .format(after.getPixel(x, yy) and 0xFFFFFF, page)
        }
    }

    private fun near(argb: Int, rgb: Int) = listOf(16, 8, 0).all { abs(((argb shr it) and 255) - ((rgb shr it) and 255)) <= 6 }

    /** The screen now, as the device draws it. */
    private fun screen(what: String): Bitmap {
        compose.waitForIdle()
        ui.waitForIdle()
        SystemClock.sleep(600) // the compositor presents the settled frame
        return checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot of $what" }
    }

    /** One still of the screen as [name].png, as MapRenderTest takes them. */
    private fun shot(name: String) {
        compose.waitForIdle()
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.waitForIdle()
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        SystemClock.sleep(600) // the compositor presents the settled frame
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical: a garden's and a kitchen's jobs on a 4 by 6 grid, each linked to the
        // one beside it, so cards and lines lie under every part of the board the menu can cover
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
        val folders = listOf(garden, kitchen)
        private val titles = listOf(
            "Fix the gate latch", "Buy brushes", "Paint the shed green", "Turn the compost",
            "Plant tulips", "Try the seeded bread", "Order coffee beans", "Plan the weekend walk",
            "Sweep the path", "Mend the fence", "Sow the peas", "Clean the gutters",
            "Sharpen the knives", "Bake a rye loaf", "Label the jars", "Oil the hinges",
            "Prune the roses", "Wash the windows", "Stack the logs", "Hang the bird feeder",
            "Sort the seed packets", "Fold the tablecloths", "Water the herbs", "Mow the lawn",
        )
        val notes = titles.mapIndexed { i, title ->
            val col = i % 4; val row = i / 4
            Note(id = id(11 + i), title = title, folderId = if (i % 3 == 0) garden.id else if (i % 3 == 1) kitchen.id else null,
                links = if (col > 0) listOf(id(10 + i)) else emptyList(),
                boardX = -300.0 + 200.0 * col, boardY = -375.0 + 150.0 * row, createdAt = T + (24 - i) * 3_600_000L)
        }
    }
}
