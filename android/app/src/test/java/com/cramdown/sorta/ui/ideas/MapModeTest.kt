package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.map.MapDrawnKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Map mode's switch and holder (S05b A4), as a person uses them, each test on a fresh store:
// the switch changes modes and back, an empty store's map shows the empty hint and the switch,
// and on the 2D look (the board, which Compose draws; Robolectric can't draw the GL map) the map
// shows a captured idea and opening it opens S05's editor, and a title edited there shows on the
// board. The "To sort n" chip opens the list as a sheet, To sort first, whose words open the editor
// and whose Choose folder files an idea through the chooser. Each of the four looks is one tap on
// the look chip and one in its menu away, the chip then naming it. A look chosen from the menu is
// kept, and a relaunch opens on Sort. iOS's UI tests ask the same. Performance's Look menu offers
// no Map group or demo maps, as no Sorta build, the release one among them, is a developer's. The
// ideas are made up (IdeasFixture), on a fresh store.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapModeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val run by lazy { SortaRun(compose) }
    private fun s(id: Int, vararg args: Any) = run.context.getString(id, *args)

    @After fun close() = run.close()

    private fun shown(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun switchTo(mode: SortaMode) {
        compose.onNode(hasTestTag(mode.identifier)).performClick()
        compose.waitForIdle()
    }

    private fun capture(words: String) {
        run.field.performTextInput(words)
        run.field.performImeAction()
        compose.waitForIdle()
    }

    /** The keyboard is up after a capture: the switch is its folded disc, which a tap opens. */
    private fun openSwitch() {
        if (!shown(SortaMode.SORT.identifier)) {
            compose.onNode(hasTestTag(SortaMode.TOGGLE)).performClick()
            compose.waitForIdle()
        }
    }

    private fun lookIs2D() {
        IdeasSettings(run.context).setLook(MapLook.TWO_D)
    }

    /** Opens the look chip's menu, finds all four looks in it with [now] checked, and chooses [look]. */
    private fun chooseLook(now: MapLook, look: MapLook) {
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitForIdle()
        MapLook.entries.forEach { assertTrue("the menu's ${it.identifier}", shown(it.identifier)) }
        compose.onNode(hasTestTag(now.identifier)).assertIsSelected()
        compose.onNode(hasTestTag(look.identifier)).performClick()
        compose.waitForIdle()
    }

    private fun p(id: Int, n: Int, vararg args: Any) = run.context.resources.getQuantityString(id, n, *args)

    private fun card(words: String) = compose.onNode(hasContentDescription("$words, ", substring = true))

    @Test fun theSwitchChangesModesAndBack() {
        run.launch()
        assertTrue("Sort at launch", shown(IdeasTags.CAPTURE))
        switchTo(SortaMode.MAP)
        assertFalse("capture is Sort's", shown(IdeasTags.CAPTURE))
        assertTrue("the map's empty hint", shown(IdeasTags.MAP_EMPTY))
        switchTo(SortaMode.SORT)
        assertTrue("back in Sort", shown(IdeasTags.CAPTURE))
    }

    @Test fun anEmptyStoresMapShowsTheHintAndTheSwitch() {
        run.launch()
        switchTo(SortaMode.MAP)
        compose.onNode(hasTestTag(IdeasTags.MAP_EMPTY)).assertIsDisplayed()
        assertTrue("its words", run.shows(s(R.string.map_empty_title)) && run.shows(s(R.string.map_empty_line)))
        compose.onNode(hasTestTag(SortaMode.SORT.identifier)).assertIsDisplayed()
    }

    @Test fun theMapShowsACapturedIdeaAndOpensItInTheEditor() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        val card = card("Paint the shed green")
        card.assertIsDisplayed()
        card.performTouchInput { click() }
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
        assertFalse("the switch is not over the editor", shown(SortaMode.MAP.identifier) || shown(SortaMode.TOGGLE))
    }

    // S05b task 5: a card is never shorter than the height the board's first view fits by, so the
    // first view's floor (a 48 dp target over that height) keeps even a one-line card a target tall.
    @Test fun aOneLineCardIsAsTallAsTheBoardFitsBy() {
        lookIs2D()
        run.launch()
        capture("Oil the gate")
        openSwitch()
        switchTo(SortaMode.MAP)
        val card = card("Oil the gate")
        card.assertIsDisplayed()
        // one card: the first view is at its ceiling, 1, so the card is drawn at its own height
        val tall = card.fetchSemanticsNode().boundsInRoot.height / run.context.resources.displayMetrics.density
        assertTrue("the card is $tall dp tall, under ${IdeaBoardDrop.CARD_HEIGHT}", tall >= IdeaBoardDrop.CARD_HEIGHT - 0.5)
    }

    @Test fun theListChipOpensTheListAndItsWordsOpenTheEditor() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        val chip = compose.onNode(hasTestTag(MapTags.LIST_CHIP))
        chip.assertIsDisplayed()
        chip.assert(hasContentDescription(run.context.resources.getQuantityString(R.plurals.sort_title_spoken, 1, 1)))
        chip.performClick()
        compose.waitForIdle()
        assertTrue("the list sheet", shown(MapTags.LIST))
        compose.onNode(hasTestTag(IdeasTags.IDEA_LINE) and hasAnyAncestor(hasTestTag(MapTags.LIST))).performClick()
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
    }

    // Robolectric can't draw the GL map, so this starts on 2D and chooses it again from the menu,
    // where iOS's test chooses 2D from Space; MapLookWordsTest stores each look.
    @Test fun aRelaunchOpensOnSortWithTheLookKept() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        chooseLook(now = MapLook.TWO_D, look = MapLook.TWO_D)
        card("Paint the shed green").assertIsDisplayed()
        run.relaunch()
        assertTrue("Sort at every launch", shown(IdeasTags.CAPTURE))
        switchTo(SortaMode.MAP)
        card("Paint the shed green").assertIsDisplayed()
        compose.onNode(hasTestTag(MapLook.CHIP)).assert(hasContentDescription(s(R.string.look_chip_spoken, s(R.string.look_2d))))
        assertEquals(MapLook.TWO_D, IdeasSettings(run.context).look)
    }

    // S05b A4: each look is reached by a tap on the chip and one in its menu, the chip then naming
    // it. The 3D looks are drawn as Robolectric can (no GL); MapLookWordsTest stores each look.
    @Test fun eachLookIsOneTapOnTheChipAndOneInItsMenuAway() {
        lookIs2D()
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        var now = MapLook.TWO_D
        for (look in listOf(MapLook.SPACE, MapLook.NEURON, MapLook.TWO_D, MapLook.PERFORMANCE)) {
            chooseLook(now = now, look = look)
            assertFalse("the menu closes on ${look.identifier}", MapLook.entries.any { shown(it.identifier) })
            val name = s(look.title)
            compose.onNode(hasTestTag(MapLook.CHIP)).assertIsDisplayed().assert(hasContentDescription(s(R.string.look_chip_spoken, name)))
            compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(MapLook.CHIP)), useUnmergedTree = true).assertIsDisplayed()
            assertEquals(look, IdeasSettings(run.context).look)
            now = look
        }
    }

    // S05b A4: the list sheet shows To sort first, its ideas, then the folders, and its Choose
    // folder files an idea through the chooser, the idea then gone from To sort.
    @Test fun theListSheetShowsToSortFirstAndFilesAnIdeaThroughTheChooser() {
        lookIs2D()
        IdeaStore(run.store).replaceIdeas(listOf(IdeasFixture.gate, IdeasFixture.compost), listOf(IdeasFixture.garden))
        run.launch()
        switchTo(SortaMode.MAP)
        compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
        compose.waitForIdle()
        val inList = hasAnyAncestor(hasTestTag(MapTags.LIST))
        val title = compose.onNode(hasTestTag(IdeasTags.TITLE) and inList)
        title.assert(hasContentDescription(p(R.plurals.sort_title_spoken, 1, 1)))
        val gate = compose.onNode(hasTestTag(IdeasTags.IDEA_LINE) and hasText(IdeasFixture.gate.title) and inList)
        val garden = compose.onNode(hasContentDescription(p(R.plurals.folder_row_spoken, 1, "Garden", 1)) and inList)
        val t = title.fetchSemanticsNode().boundsInRoot
        val g = gate.fetchSemanticsNode().boundsInRoot
        val f = garden.fetchSemanticsNode().boundsInRoot
        assertTrue("To sort's title, then its idea, then the folder: $t, $g, $f", t.bottom <= g.top + 0.5f && g.bottom <= f.top + 0.5f)

        compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, IdeasFixture.gate.title)) and inList).performClick()
        compose.waitForIdle()
        assertTrue("the chooser opens", run.chooserShown())
        run.inChooser("Garden").performClick()
        run.until { !run.chooserShown() }
        assertTrue("the sheet stays up", shown(MapTags.LIST))
        title.assert(hasContentDescription(p(R.plurals.sort_title_spoken, 0, 0)))
        assertEquals("gone from To sort", 0,
            compose.onAllNodes(hasTestTag(IdeasTags.IDEA_LINE) and hasText(IdeasFixture.gate.title) and inList).fetchSemanticsNodes().size)
        compose.onNode(hasContentDescription(p(R.plurals.folder_row_spoken, 2, "Garden", 2)) and inList).assertIsDisplayed()
        run.until { IdeaStore(run.store).notes.firstOrNull { it.id == IdeasFixture.gate.id }?.folderId == IdeasFixture.garden.id }
    }

    // S05b A4: an idea opened from the board, retitled in the editor and the editor closed, shows
    // its new title on the board.
    @Test fun anIdeaRetitledFromTheBoardShowsItsNewTitleThere() {
        lookIs2D()
        run.launch()
        capture("Paint the shed")
        openSwitch()
        switchTo(SortaMode.MAP)
        card("Paint the shed").performTouchInput { click() }
        compose.waitForIdle()
        run.until { shown(EDITOR_TAG) }
        val title = run.field(s(R.string.editor_title))
        title.performTextClearance()
        title.performTextInput("Paint the shed green")
        compose.onNode(hasText(s(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
        compose.waitForIdle()
        run.until { !shown(EDITOR_TAG) }
        assertFalse("back on the map, not Sort", shown(IdeasTags.CAPTURE))
        card("Paint the shed green").assertIsDisplayed()
        assertEquals("the old title is gone", 0, compose.onAllNodes(hasContentDescription("Paint the shed, ", substring = true)).fetchSemanticsNodes().size)
    }

    // S05b, step 4's list (S03-result.md: "the Map group and the demo maps are in a developer's build
    // only"): IdeasSettings.DEVELOPER is false in every Sorta build, debug and release alike, so what
    // this debug run shows is the release build's menu. Performance's Look menu offers its Lines and
    // legend, with no Map group, no Your ideas and no demo map; and a demo map stored from before
    // (setPerfDemo) is not shown: the map is the person's own ideas.
    @Test fun theReleaseLookMenuOffersNoMapGroupOrDemoMaps() {
        assertFalse("no Sorta build is a developer's", IdeasSettings.DEVELOPER)
        IdeasSettings(run.context).apply { setLook(MapLook.PERFORMANCE); setPerfDemo(IdeasSettings.perfDemoSizes.first()) }
        run.launch()
        capture("Paint the shed green")
        openSwitch()
        switchTo(SortaMode.MAP)
        val lookTool = hasContentDescription("Look") and hasClickAction()
        run.until { compose.onAllNodes(lookTool).fetchSemanticsNodes().isNotEmpty() }
        val map = compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().single()
        val said = map.config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue("the person's ideas, not a demo: $said", said.startsWith("${GraphTheme.PERFORMANCE.mapLabel}: 1 idea,") && !said.contains("demo"))
        compose.onNode(lookTool).performClick()
        compose.waitForIdle()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        assertTrue("the menu is open, its Lines group there", compose.onAllNodes(heading and hasText("Lines", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty())
        assertFalse("no Map group", compose.onAllNodes(heading and hasText("Map", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty())
        assertFalse("no Your ideas", run.shows("Your ideas"))
        for (n in IdeasSettings.perfDemoSizes) assertFalse("no ${perfDemoTitle(n)}", run.shows(perfDemoTitle(n)))
        assertFalse("no demo of any size", run.shows("Demo:"))
    }

    // ---- S05j item 1: the status bar on two panes; item 6a: light icons on every sheet over a 3D look ----

    /** A status bar this tall, as a phone's, handed to the window as the system would (Robolectric gives none). */
    private fun statusBar(px: Int) = run.onActivity { a ->
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, px, 0, 0))
            .setVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars(), true).build()
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(a.window.decorView, insets)
    }

    private fun lightStatusIcons(window: android.view.Window): Boolean =
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars

    /**
     * Two panes by [dark]'s light (question 1's default): the map pane starts below the status bar, the
     * bar's row is the page across the window, and its icons follow the page: the clock (the bar's
     * start) at 4.5:1 and the end icons at 3:1 against what is behind them. The system draws light
     * icons white and dark ones black at 60% (SystemUI's light_mode_icon_color_single_tone and
     * dark_mode_icon_color_single_tone); on 1015763 the map's night themes turned them white over the
     * bone Sort pane by day.
     */
    private fun twoPanesBar(name: String, dark: Boolean) {
        IdeaStore(run.store).replaceIdeas(listOf(IdeasFixture.gate, IdeasFixture.compost), listOf(IdeasFixture.garden))
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        run.launch()
        val density = run.context.resources.displayMetrics.density
        val bar = (24 * density).toInt()
        statusBar(bar)
        compose.waitForIdle()
        assertTrue("$name: two panes", shown(IdeasTags.MAP_PANE))
        val light = run.onActivity { lightStatusIcons(it.window) }
        assertEquals("$name: the icons follow the page", !dark, light)
        val pane = compose.onNode(hasTestTag(IdeasTags.MAP_PANE)).fetchSemanticsNode().boundsInRoot
        assertTrue("$name: the map pane starts below the status bar: ${pane.top} of $bar", pane.top >= bar - 0.5f)
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        assertTrue("$name: its strip below the bar too: ${strip.top}", strip.top >= bar - 0.5f)
        System.setProperty("roborazzi.test.record", "true")
        val file = java.io.File("build/outputs/roborazzi/mapmode_panes_bar_$name.png")
        com.github.takahirom.roborazzi.captureScreenRoboImage(file.path)
        val image = javax.imageio.ImageIO.read(file)
        val y = bar / 2
        val start = image.getRGB((16 * density).toInt(), y) and 0xFFFFFF
        val end = image.getRGB(image.width - (16 * density).toInt(), y) and 0xFFFFFF
        assertEquals("$name: the bar's row is the page across the window", start, end)
        fun icon(behind: Int): Int = if (light) blend(0x000000, behind, 0.6) else 0xFFFFFF
        val clock = com.cramdown.sorta.ui.ward.WardPalette.contrast(icon(start), start)
        val icons = com.cramdown.sorta.ui.ward.WardPalette.contrast(icon(end), end)
        assertTrue("$name: the clock at 4.5:1, ${"%.2f".format(clock)}", clock >= 4.5)
        assertTrue("$name: the end icons at 3:1, ${"%.2f".format(icons)}", icons >= 3.0)
    }

    private fun blend(top: Int, under: Int, alpha: Double): Int {
        fun ch(c: Int, s: Int) = ((c shr s and 0xFF) * alpha + (under shr s and 0xFF) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (ch(top, 16) shl 16) or (ch(top, 8) shl 8) or ch(top, 0)
    }

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun twoPanesOnTheTabletByDay() = twoPanesBar("tablet_light", dark = false)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun twoPanesOnTheTabletByNight() = twoPanesBar("tablet_dark", dark = true)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun twoPanesOnTheOpenFoldableByDay() = twoPanesBar("foldable_light", dark = false)

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-night-xhdpi")
    @Test fun twoPanesOnTheOpenFoldableByNight() = twoPanesBar("foldable_dark", dark = true)

    /**
     * The window of the sheet up now: a ModalBottomSheet's own dialog window, the last window added
     * (WindowManagerGlobal's views, read as Robolectric's own shadows do), checked to hold [tag].
     */
    private fun sheetWindow(tag: String): android.view.Window {
        compose.onNode(hasTestTag(tag)).assertExists()
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>
        fun find(v: android.view.View): androidx.compose.ui.window.DialogWindowProvider? {
            if (v is androidx.compose.ui.window.DialogWindowProvider) return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return views.asReversed().firstNotNullOfOrNull { find(it) }?.window ?: error("$tag is in no dialog window")
    }

    // Item 6a: in light mode a 3D look is night all the same, so a sheet over it keeps light status
    // icons: the list sheet, the chooser from Choose folder, and the naming sheet from its New folder.
    // On 1015763 the three left them dark.
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun everySheetOverA3DLookKeepsLightStatusIcons() {
        IdeaStore(run.store).replaceIdeas(listOf(IdeasFixture.gate, IdeasFixture.compost), listOf(IdeasFixture.garden))
        IdeasSettings(run.context).setLook(MapLook.SPACE)
        run.launch()
        switchTo(SortaMode.MAP)
        compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
        compose.waitForIdle()
        assertFalse("the list sheet's icons are light", lightStatusIcons(sheetWindow(MapTags.LIST)))
        val inList = hasAnyAncestor(hasTestTag(MapTags.LIST))
        compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, IdeasFixture.gate.title)) and inList).performClick()
        compose.waitForIdle()
        assertTrue("the chooser opens", run.chooserShown())
        assertFalse("the chooser's icons are light", lightStatusIcons(sheetWindow(FOLDER_CHOOSER_TAG)))
        run.inChooser(s(R.string.chooser_new_folder)).performClick()
        compose.waitForIdle()
        assertFalse("the naming sheet's icons are light", lightStatusIcons(sheetWindow(NAME_SHEET_TAG)))
    }

    // Item 6a: over the 2D board and over the Sort screen in light mode, the chooser and the naming
    // sheet keep dark icons, as the page under them
    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun overTheBoardAndSortTheSheetsKeepDarkStatusIcons() {
        lookIs2D()
        IdeaStore(run.store).replaceIdeas(listOf(IdeasFixture.gate, IdeasFixture.compost), listOf(IdeasFixture.garden))
        run.launch()
        for (mode in listOf(SortaMode.MAP, SortaMode.SORT)) {
            switchTo(mode)
            if (mode == SortaMode.MAP) {
                compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
                compose.waitForIdle()
                val inList = hasAnyAncestor(hasTestTag(MapTags.LIST))
                assertTrue("$mode: the list sheet over the board keeps dark icons", lightStatusIcons(sheetWindow(MapTags.LIST)))
                compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, IdeasFixture.gate.title)) and inList).performClick()
            } else {
                compose.onNode(hasContentDescription(s(R.string.row_choose_folder_spoken, IdeasFixture.gate.title))).performClick()
            }
            compose.waitForIdle()
            assertTrue("$mode: the chooser opens", run.chooserShown())
            assertTrue("$mode: the chooser keeps dark icons", lightStatusIcons(sheetWindow(FOLDER_CHOOSER_TAG)))
            run.inChooser(s(R.string.chooser_new_folder)).performClick()
            compose.waitForIdle()
            assertTrue("$mode: the naming sheet keeps dark icons", lightStatusIcons(sheetWindow(NAME_SHEET_TAG)))
            compose.onNode(hasText(s(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(NAME_SHEET_TAG))).performClick()
            compose.waitForIdle()
            if (mode == SortaMode.MAP) {
                compose.onNode(hasTestTag(MapTags.LIST_DONE)).performClick()
                compose.waitForIdle()
            }
        }
    }
}
