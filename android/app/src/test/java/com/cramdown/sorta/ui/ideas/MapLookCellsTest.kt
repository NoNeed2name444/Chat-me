package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.MapFilter
import com.cramdown.sorta.map.NeuronState
import com.cramdown.sorta.map.NeuronStateChoice
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardControlSize
import com.cramdown.sorta.ui.ward.WindowShapeHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// S05i: the Neurons' Look menu's Cells group and Cell by cell sheet (iOS GraphNodeStyles.swift:211-243),
// and the Filter tool's menu (Graph3DView.swift:824-841), each row a 48 dp target. Made-up folders.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-xxhdpi")
class MapLookCellsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val garden = NoteFolder(id = "3f2a8c1e-0b4d-4e6f-9a7b-1c2d3e4f5a6b", name = "Garden")
    private val shed = NoteFolder(id = "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4", name = "Shed")
    private val book get() = IdeaBook(emptyList(), listOf(shed, garden, NoteFolder(name = "Tools", parentId = shed.id)))

    @Before fun clear() {
        context.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Composable private fun host(content: @Composable () -> Unit) {
        SortaTheme(dark = false) {
            WindowShapeHost { Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) { content() } }
        }
    }

    private fun node(tag: String): SemanticsNode = compose.onNode(hasTestTag(tag)).fetchSemanticsNode()
    private fun chosen(tag: String): Boolean =
        node(tag).config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Chosen")
    private fun tall(tag: String): Float = node(tag).touchBoundsInRoot.let { minOf(it.width, it.height) } / compose.density.density
    private fun top(tag: String): Float = node(tag).boundsInRoot.top

    @Test fun theCellsRowsInIosOrderRestingCheckedWhileNatural() {
        val settings = IdeasSettings(context)
        var cellByCell = 0
        compose.setContent { host { SpaceLookTool(book, settings, GraphTheme.NEURONS, {}, {}, {}, showCellByCell = { cellByCell++ }) } }
        compose.onNode(hasClickAction() and hasContentDescription("Look")).performClick()
        compose.waitForIdle()
        val tags = NeuronState.menuOrder.map { "lookCell-" + it.rawValue }
        assertEquals("the space styles' order", listOf("engulfing", "firing", "resting", "releasing", "pacemaker", "migrating"),
            NeuronState.menuOrder.map { it.rawValue })
        val tops = tags.map { top(it) }
        assertEquals("one under another in that order", tops.sorted(), tops)
        assertEquals("Resting checked while natural", listOf("lookCell-resting"), tags.filter { chosen(it) })
        for (t in tags) assertTrue("$t is a 48 dp target: ${tall(t)}", tall(t) >= WardControlSize.target - 0.5)
        assertTrue("Cell by cell after the six", top(MapPanelTags.LOOK_CELL_BY_CELL) > tops.last())
        assertTrue(tall(MapPanelTags.LOOK_CELL_BY_CELL) >= WardControlSize.target - 0.5)
        // Firing for every idea
        compose.onNode(hasTestTag("lookCell-firing")).performClick()
        compose.waitForIdle()
        assertEquals("firing", IdeasSettings(context).cellState)
        assertTrue(IdeasSettings(context).cells.isCustom)
        // Resting stores natural
        compose.onNode(hasClickAction() and hasContentDescription("Look")).performClick()
        compose.waitForIdle()
        assertTrue(chosen("lookCell-firing"))
        compose.onNode(hasTestTag("lookCell-resting")).performClick()
        compose.waitForIdle()
        assertEquals(NeuronStateChoice.NATURAL_VALUE, IdeasSettings(context).cellState)
        compose.onNode(hasClickAction() and hasContentDescription("Look")).performClick()
        compose.waitForIdle()
        compose.onNode(hasTestTag(MapPanelTags.LOOK_CELL_BY_CELL)).performClick()
        compose.waitForIdle()
        assertEquals(1, cellByCell)
    }

    @Test fun noCellsGroupOutsideTheNeurons() {
        val settings = IdeasSettings(context)
        compose.setContent { host { SpaceLookTool(book, settings, GraphTheme.SPACE, {}, {}, {}) } }
        compose.onNode(hasClickAction() and hasContentDescription("Look")).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("lookCell-resting")).fetchSemanticsNodes().size)
    }

    @Test fun cellByCellHasARowPerTopLevelFolderStoredUpperCase() {
        val settings = IdeasSettings(context)
        settings.setCellState("firing")
        compose.setContent { host { CellByCellSheet(book, settings) {} } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val rows = listOf("cellByCell-" + garden.id, "cellByCell-" + shed.id)
        assertEquals("the top level only, by name", rows.map { top(it) }.sorted(), rows.map { top(it) })
        assertEquals(0, compose.onAllNodes(SemanticsMatcher("Tools") { n -> n.config.getOrNull(SemanticsProperties.TestTag).orEmpty().startsWith("cellByCell-") &&
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Tools" } == true }).fetchSemanticsNodes().size)
        for (r in rows) assertTrue("$r is a 48 dp target", tall(r) >= WardControlSize.target - 0.5)
        compose.onNode(hasTestTag(rows[0])).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // its picker: Same as all, then the six
        val picker = compose.onAllNodes(hasClickAction() and androidx.compose.ui.test.hasText("Engulfing")).fetchSemanticsNodes()
        assertEquals("its picker: Same as all and the six; ${compose.onAllNodes(androidx.compose.ui.test.hasText("Same as all")).fetchSemanticsNodes().size} Same as all",
            1, picker.size)
        compose.onNode(hasClickAction() and androidx.compose.ui.test.hasText("Engulfing")).performClick()
        compose.waitForIdle()
        assertEquals(garden.id.uppercase() + "=engulfing", IdeasSettings(context).cellFolders)
        assertEquals(mapOf(garden.id.uppercase() to NeuronState.ENGULFING), IdeasSettings(context).cells.folders)
    }

    @Test fun theFilterMenusRowsAndFolders() {
        var picked: MapFilter = MapFilter.All
        val folders = mapTopFolders(book, null)
        assertEquals("the book's top level, by name", listOf("Garden", "Shed"), folders.map { it.second })
        assertEquals("the Universe's galaxies that are folders, by name", listOf("Garden", "Shed"),
            mapTopFolders(book, listOf(shed.id, "home", garden.id)).map { it.second })
        compose.setContent {
            host {
                var filter by remember { mutableStateOf<MapFilter>(MapFilter.All) }
                MapFilterTool(filter, folders) { filter = it; picked = it }
            }
        }
        val tool = node(MapPanelTags.FILTER_TOOL)
        assertTrue("the tool is a 48 dp target", tall(MapPanelTags.FILTER_TOOL) >= WardControlSize.target - 0.5)
        assertEquals(false, tool.config.getOrNull(SemanticsProperties.Selected))
        compose.onNode(hasTestTag(MapPanelTags.FILTER_TOOL)).performClick()
        compose.waitForIdle()
        val show = listOf(MapFilter.All, MapFilter.Pages, MapFilter.Ideas, MapFilter.Linked).map { MapPanelTags.filterRow(it) }
        val rows = show + folders.map { MapPanelTags.filterRow(MapFilter.Folder(it.first)) }
        assertEquals("Show's four, then each folder", rows.map { top(it) }.sorted(), rows.map { top(it) })
        assertEquals("Everything checked", listOf(MapPanelTags.filterRow(MapFilter.All)), rows.filter { chosen(it) })
        for (r in rows) assertTrue("$r is a 48 dp target", tall(r) >= WardControlSize.target - 0.5)
        compose.onNode(hasTestTag(MapPanelTags.filterRow(MapFilter.Folder(shed.id)))).performClick()
        compose.waitForIdle()
        assertEquals(MapFilter.Folder(shed.id), picked)
        assertEquals("pressed in while filtering", true, node(MapPanelTags.FILTER_TOOL).config.getOrNull(SemanticsProperties.Selected))
        assertEquals("Shed", node(MapPanelTags.FILTER_TOOL).config.getOrNull(SemanticsProperties.StateDescription))
        compose.onNode(hasTestTag(MapPanelTags.FILTER_TOOL)).performClick()
        compose.waitForIdle()
        assertTrue(chosen(MapPanelTags.filterRow(MapFilter.Folder(shed.id))))
        compose.onNode(hasTestTag(MapPanelTags.filterRow(MapFilter.All))).performClick()
        compose.waitForIdle()
        assertEquals(MapFilter.All, picked)
    }
}
