package com.cramdown.sorta.ideas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.MapBody
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.MapPanelSettings
import com.cramdown.sorta.ui.ideas.MapTouchSeen
import com.cramdown.sorta.ui.ideas.mapPeekInput
import com.cramdown.sorta.ui.ideas.mapPeekWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The map's peek card, options and touches (S05b task 4), as iOS's GraphTouchTests checks
// GraphPeek: what the card says for an idea, a folder and the home, in strings.xml's words; the
// options Android offers; an idea's Show links (S05g); and the Link length and first-run card's
// stored values, under iOS's keys. How a touch is judged is MapSelectionTest's.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapPeekTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = mapPeekWords(context.resources)

    @Before fun clear() {
        context.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun anIdeasCardShowsItsTitleKindFirstLinesAndSixChips() {
        val links = listOf("i5" to "Link 5", "i2" to "link 2", "i8" to "Link 8", "i1" to "Link 1", "i7" to "Link 7",
            "i3" to "Link 3", "i6" to "Link 6", "i4" to "Link 4", "i0" to "")
        val card = MapPeek.content(MapPeekInput("space", "gasGiant", folder = false, title = "Oil the gate",
            body = "# Heading\n\n- [[Shed]] needs **oil**\n> A quote\nA fourth line", links = links), words)
        assertEquals("Oil the gate", card.title)
        assertEquals("Gas giant · Idea", card.kind)
        assertEquals(listOf("Heading", "Shed needs oil", "A quote"), card.lines)
        // by title, case aside; a blank title is Untitled
        assertEquals(listOf("i0", "i1", "i2", "i3", "i4", "i5"), card.chips.map { it.id })
        assertEquals(listOf("Untitled", "Link 1", "link 2", "Link 3", "Link 4", "Link 5"), card.chips.map { it.title })
        assertEquals(3, card.moreLinks)
        assertEquals("+3 more", words.moreLinks(card.moreLinks))
        assertEquals("Open", card.primary)
        assertEquals(listOf("Show links", "More"), card.secondary)
        assertEquals("Oil the gate, Gas giant · Idea, 9 links. Heading Shed needs oil A quote", card.spoken)
    }

    @Test fun aBlankPageSaysSo() {
        val card = MapPeek.content(MapPeekInput("performance", "", folder = false, title = "", page = true,
            links = listOf("a" to "Gate")), words)
        assertEquals("Untitled", card.title)
        assertEquals("Page", card.kind)
        assertEquals(listOf("Nothing written yet."), card.lines)
        assertEquals(0, card.moreLinks)
        assertEquals("Untitled, Page, 1 link. Nothing written yet.", card.spoken)
    }

    @Test fun aFoldersCardCountsAndFliesInWhereItCan() {
        val input = MapPeekInput("neurons", "0", folder = true, title = "Garden", notes = 3, subfolders = 1,
            recent = listOf("Bulbs", "", "Shed", "Gate"), flyIn = true)
        val card = MapPeek.content(input, words)
        assertEquals("Cell · Folder", card.kind)
        assertEquals(listOf("3 ideas · 1 subfolder", "Latest: Bulbs, Untitled, Shed"), card.lines)
        assertEquals(emptyList<MapPeekChip>(), card.chips)
        assertEquals("Open folder", card.primary)
        assertEquals(listOf("Fly in"), card.secondary)
        assertEquals(emptyList<String>(), MapPeek.content(input.copy(flyIn = false), words).secondary)
        assertEquals("Garden, Cell · Folder. 3 ideas · 1 subfolder. Latest: Bulbs, Untitled, Shed", card.spoken)
    }

    @Test fun theHomeIsToSortBesideFoldersAndAllIdeasWithout() {
        assertEquals("To sort", MapPeek.content(MapPeekInput("neurons", "", folder = false, home = true, unsorted = true, title = "To sort"), words).kind)
        assertEquals("All ideas", MapPeek.content(MapPeekInput("neurons", "", folder = false, home = true, title = "All ideas"), words).kind)
    }

    @Test fun aBookDescribesItsBodies() {
        var time = 0L
        val book = IdeaBook(emptyList(), emptyList(), now = { ++time })
        val garden = book.createFolder("Garden")
        book.createFolder("Bulbs", parentId = garden.id)
        val gate = book.create("Gate", body = "Oil it", folderId = garden.id)
        val shed = book.create("Shed", folderId = garden.id)
        book.create("Loose")
        book.link(gate.id, shed.id)
        val note = mapPeekInput(book, MapBody(gate.id.uppercase(), ThemeBodyKind.NOTE, "galaxy"), GraphTheme.SPACE, words)!!
        assertEquals(listOf(shed.id to "Shed"), note.links)
        assertEquals("Black hole · Idea", MapPeek.content(note, words).kind)
        val folder = mapPeekInput(book, MapBody(garden.id, ThemeBodyKind.FOLDER), GraphTheme.NEURONS, words)!!
        assertEquals(listOf("Shed", "Gate"), folder.recent)
        assertEquals(2, folder.notes)
        assertEquals(1, folder.subfolders)
        assertTrue("Neurons flies in to a cell", folder.flyIn)
        assertFalse("Space can't fly in", mapPeekInput(book, MapBody(garden.id, ThemeBodyKind.FOLDER), GraphTheme.SPACE, words)!!.flyIn)
        val home = mapPeekInput(book, MapBody("HOME", ThemeBodyKind.HOME), GraphTheme.NEURONS, words)!!
        assertEquals("To sort", home.title)
        assertEquals(1, home.notes)
        assertNull("a body the book no longer has", mapPeekInput(book, MapBody("gone", ThemeBodyKind.NOTE), GraphTheme.SPACE, words))
    }

    @Test fun firstLinesKeepsThreeAndCutsALongOne() {
        val long = "x".repeat(200)
        val lines = MapPeek.firstLines("**$long**\n\n\n## Two\n* three\nfour")
        assertEquals(3, lines.size)
        assertEquals("x".repeat(139) + "…", lines[0])
        assertEquals(listOf("Two", "three"), lines.drop(1))
    }

    @Test fun everyBodyHasItsThemesWord() {
        assertEquals(listOf("Black hole", "Rocky planet", "Gas giant", "Comet", "Pulsar", "Star"),
            SpaceStyle.entries.map { MapPeek.roleWord("space", MapPeek.spaceRole(it), words) })
        assertEquals(listOf("Cell", "Cell part", "Cell", "Vesicle", "Granule"), (0..4).map { MapPeek.roleWord("neurons", "$it", words) })
        assertNull(MapPeek.roleWord("performance", "", words))
    }

    @Test fun theOptionsAreAndroidsShareOfIOSs() {
        fun labels(folder: Boolean, home: Boolean) = MapPeek.menu(folder, home).map(words::item)
        assertEquals(listOf("Open", "Rename", "Choose folder", "Delete"), labels(folder = false, home = false))
        assertEquals(listOf("Open", "Rename", "Delete"), labels(folder = true, home = false))
        assertEquals(listOf("Open"), labels(folder = false, home = true))
    }

    @Test fun everyIdeaOffersShowLinksAndNoFolderOrHomeDoes() {
        for (theme in listOf("space", "neurons", "performance")) {
            val idea = MapPeek.content(MapPeekInput(theme, "", folder = false, title = "Gate"), words)
            assertEquals(theme, listOf("Show links", "More"), idea.secondary)
            val folder = MapPeek.content(MapPeekInput(theme, "0", folder = true, title = "Garden", flyIn = true), words)
            assertFalse(theme, "Show links" in folder.secondary)
        }
        assertFalse("Show links" in MapPeek.content(MapPeekInput("neurons", "", folder = false, home = true, title = "To sort"), words).secondary)
        assertEquals("Show links", words.showLinks)
        assertEquals("All links", words.allLinks)
    }

    @Test fun theLinkLengthIsIOSsAndKept() {
        assertEquals("vignette.space.linkLength", MapLinkLength.KEY)
        assertEquals(1.0, MapLinkLength.clamped(Double.NaN), 0.0)
        assertEquals(0.6, MapLinkLength.clamped(0.1), 0.0)
        assertEquals(1.8, MapLinkLength.clamped(4.0), 0.0)
        assertEquals(1.4, MapLinkLength.clamped(1.38), 0.0)
        assertEquals(12, Math.round((MapLinkLength.LONGEST - MapLinkLength.SHORTEST) / MapLinkLength.STEP).toInt())
        assertEquals("the slider's stops between its ends", 11, MapLinkLength.STEPS)
        val settings = MapPanelSettings(context)
        assertEquals(1.0, settings.linkLength, 0.0)
        settings.setLinkLength(0.7)
        assertEquals(0.7, MapPanelSettings(context).linkLength, 0.0)
        assertEquals("0.7", MapLinkLength.shown(0.7))
        assertEquals("Shorter, 0.7×", context.getString(com.cramdown.sorta.R.string.map_link_shorter_by, MapLinkLength.shown(0.7)))
    }

    @Test fun theTouchLinesShowOncePerTheme() {
        assertEquals("vignette.space.touchHintSeen", MapTouchSeen.KEY)
        assertEquals("space", MapTouchSeen.marking("", "space"))
        assertEquals("space,neurons", MapTouchSeen.marking("space", "neurons"))
        assertEquals("space,neurons", MapTouchSeen.marking("space,neurons", "space"))
        val settings = MapPanelSettings(context)
        assertFalse(settings.touchSeen(GraphTheme.NEURONS))
        settings.markTouchSeen(GraphTheme.NEURONS)
        assertTrue(MapPanelSettings(context).touchSeen(GraphTheme.NEURONS))
        assertFalse(MapPanelSettings(context).touchSeen(GraphTheme.SPACE))
    }
}
