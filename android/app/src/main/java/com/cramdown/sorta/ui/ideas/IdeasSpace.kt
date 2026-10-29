package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.ideas.GraphStyleChoice
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.rawValue
import com.cramdown.sorta.ideas.spaceStyle
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.map.AnatomyInput
import com.cramdown.sorta.map.AnatomyNote
import com.cramdown.sorta.map.GraphUniverse
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.NeuronScene
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.map.UniverseEdge
import com.cramdown.sorta.map.UniverseFolder
import com.cramdown.sorta.map.UniverseInput
import com.cramdown.sorta.map.UniverseNote
import com.cramdown.sorta.map.NoteLike
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.SyntheticGraph
import com.cramdown.sorta.ui.map.Map3DView
import com.cramdown.sorta.ui.map.MapScene
import com.cramdown.sorta.ui.map.MapTheme
import com.cramdown.sorta.ui.sources.ReaderDoneButton
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.LocalWardLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

// Ideas' Space (IdeasView's .space, fed0105): every note a point in space joined to the notes
// it links to, on the existing GL map, in the theme the Look menu chose. Space draws the six
// bodies (GraphNodeStyle); Performance is the plain fast map with its readout, and offers the
// seeded demo maps only in a developer's build, as iOS's readout does. The tools sit at the
// bottom-trailing corner over the bottom bar, on a map only: Look and Recentre. The map runs
// edge to edge and the tools keep to the safe area, as iOS's do. Android's map has no force
// simulation, so bodies do not drift and cannot be held and moved; a tap opens the note.

/** A note as the map reads it: its links from the book's edges, its group its top-level folder. */
private class NoteNode(override val id: String, override val title: String, override val links: List<String>, override val tags: List<String>) : NoteLike

/** The demo maps are built once per size, off the main thread, and kept while memory allows. */
private object DemoScenes {
    private val kept = HashMap<Int, java.lang.ref.SoftReference<MapScene>>()
    @Synchronized fun get(size: Int): MapScene =
        kept[size]?.get() ?: MapScene.of(SyntheticGraph.make(size)).also { kept[size] = java.lang.ref.SoftReference(it) }
}

private fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** Each map's words: "Demo: 100,000 notes" and so on. */
fun perfDemoTitle(size: Int): String = "Demo: ${grouped(size)} notes"

@Composable fun IdeasSpace(
    book: IdeaBook, settings: IdeasSettings, covered: Boolean, hidden: Dp,
    open: (String) -> Unit, openFolder: (String) -> Unit,
) {
    val theme = GraphTheme.stored(settings.theme)
    // the demo maps are a developer's, as on iOS, where only the readout offers them
    val demo = if (theme == GraphTheme.PERFORMANCE && IdeasSettings.DEVELOPER) settings.perfDemo.takeIf { it in IdeasSettings.perfDemoSizes } else null
    var recenter by remember { mutableIntStateOf(0) }
    var legend by remember { mutableStateOf(false) }
    var folderLooks by remember { mutableStateOf(false) }

    // the space is always night, whatever the phone's setting: the map, its tools and the bars'
    // icons (iOS Graph3DView's colorScheme .dark, on the drawn map only); the empty page, the
    // wait and the sheets follow the phone, as there, and only a drawn map has the tools
    val outer = LocalWardLook.current
    var shown by remember { mutableStateOf(false) }
    SortaTheme(dark = shown || outer.dark, section = outer.section) {
        Box(Modifier.fillMaxSize()) {
            when {
                demo != null -> DemoMap(demo, recenter, settings.readout) { shown = it }
                book.notes.isEmpty() -> Box(Modifier.fillMaxSize().background(wardColor(WardToken.background)).padding(bottom = hidden), contentAlignment = Alignment.Center) {
                    WardEmptyState(Icons.Outlined.Lightbulb, "Dump your first idea",
                        "Every note appears here as a point in space, joined to the notes it links to. Type one in the bar below.")
                }
                else -> NotesMap(book, settings, theme, recenter, open) { shown = it }
            }
            // above what the bottom bar hides, and clear of a cutout or a side navigation bar, as
            // iOS's overlay keeps to the safe area while the scene runs under the bars
            if (!covered && shown) Box(Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(bottom = hidden)) {
                IdeaToolCluster {
                    SpaceLookTool(book, settings, theme, showLegend = { legend = true }, showFolderLooks = { folderLooks = true }, openFolder = openFolder)
                    // iOS's smallcircle.filled.circle, on every Recentre
                    IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { recenter++ }
                }
            }
        }
    }
    if (legend) LegendSheet(theme) { legend = false }
    if (folderLooks) FolderLooksSheet(book, settings) { folderLooks = false }
}

/** The user's notes on the map, rebuilt off the main thread when they or the look change. */
@Composable private fun NotesMap(book: IdeaBook, settings: IdeasSettings, theme: GraphTheme, recenter: Int, open: (String) -> Unit,
                                  onShown: (Boolean) -> Unit) {
    val notes = book.notes
    val edges = remember(notes) { book.allEdges() }
    val main = settings.nodeStyle
    val folderRaw = settings.folderStyles
    // Neurons: the cells opened on the map, outermost first (tapping a cell opens it; Recentre closes them all)
    var openPath by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(recenter) { openPath = emptyList() }
    val scene by produceState<MapScene?>(null, notes, theme, main, folderRaw, openPath) {
        value = builtOffMain {
            if (theme == GraphTheme.NEURONS) return@builtOffMain MapScene.neurons(NeuronScene.of(neuronsInput(book, edges, openPath)))
            val out = HashMap<String, MutableList<String>>()
            for ((a, b) in edges) out.getOrPut(a) { ArrayList() }.add(b)
            val performance = theme == GraphTheme.PERFORMANCE
            val nodes = notes.map { note ->
                NoteNode(note.id, note.title.ifEmpty { "Untitled" }, out[note.id] ?: emptyList(),
                    if (performance) listOfNotNull(book.rootFolder(note.folderId)) else emptyList())
            }
            val graph = MapGraph.fromNotes(nodes)
            if (performance) MapScene.of(graph, theme = MapTheme.PLAIN)
            else {
                val degree = IntArray(graph.nodeCount)
                for (k in graph.edges.indices) degree[graph.edges[k]]++
                MapScene.of(graph, theme = MapTheme.SPACE, styles = GraphStyleChoice.codes(book, graph.ids, degree, main, folderRaw))
            }
        }
    }
    val ready = scene
    MapShown(ready != null, onShown)
    if (ready == null) { Building(null); return }
    val description = "${theme.mapLabel}: ${notes.size} ${if (notes.size == 1) "note" else "notes"}, ${edges.size} ${if (edges.size == 1) "link" else "links"}. " +
        theme.hint
    Map3DView(ready, Modifier.fillMaxSize(), description = description,
        onOpenNode = { index ->
            val body = ready.neurons?.plan?.bodies?.getOrNull(index)
            when {
                body == null -> ready.ids.getOrNull(index)?.let(open)
                body.kind == ThemeBodyKind.NOTE -> notes.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { open(it.id) }
                else -> cellsOpenedBy(body.kind, body.id, book)?.let { openPath = it }
            }
        },
        showReadout = theme == GraphTheme.PERFORMANCE && settings.readout, showControls = false, recenter = recenter, corner = 0.dp)
}

/**
 * The cells a tap on a Neurons container opens, outermost first: a folder's path, or the
 * Unsorted cell (the home, which holds the notes in no folder and is in no folder itself).
 * Null for a note, or for a folder the book no longer has.
 */
internal fun cellsOpenedBy(kind: ThemeBodyKind, id: String, book: IdeaBook): List<String>? = when (kind) {
    ThemeBodyKind.HOME -> listOf(GraphUniverse.HOME_ID)
    ThemeBodyKind.FOLDER -> book.folders.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { folder ->
        book.path(folder.id).map { it.id.uppercase() }
    }
    ThemeBodyKind.NOTE -> null
}

/**
 * What the Neurons plan reads (iOS Graph3DView.universeInput and anatomyInput): every note, folder
 * and link, with ids uppercased as iOS's UUID strings are (the plan sorts and seeds by them), the
 * notes' words, kind and creation (seconds from 1 Jan 2001), and each note's text, tags and links.
 */
private fun neuronsInput(book: IdeaBook, edges: List<Pair<String, String>>, open: List<String>): UniverseInput {
    val epoch2001 = 978_307_200.0
    val notes = book.notes.map { n ->
        UniverseNote(n.id.uppercase(), n.title, n.noteKind == NoteKind.PAGE, n.folderId?.uppercase(),
            GraphUniverse.wordCount(n.body), n.createdAt / 1000.0 - epoch2001)
    }
    val folders = book.folders.map { UniverseFolder(it.id.uppercase(), it.name, it.parentId?.uppercase()) }
    val index = book.titleIndex()
    val anatomy = AnatomyInput(book.notes.map { n ->
        AnatomyNote(n.id.uppercase(), n.title, n.body, n.noteKind == NoteKind.PAGE, n.folderId?.uppercase(), n.tags,
            null, n.links.map { it.uppercase() }, book.resolve(n.body, index).map { it.uppercase() }, n.createdAt / 1000.0 - epoch2001)
    }, folders)
    return UniverseInput(notes, folders, edges.map { UniverseEdge(it.first.uppercase(), it.second.uppercase()) },
        anatomy = anatomy, open = open)
}

/**
 * Builds a scene on a worker thread and hands it back on the main one. Under a UI test's
 * unconfined dispatcher the coroutine would otherwise go on on the worker and recompose the map
 * there, which broke the composition when its screen closed (the tablet's MapRenderTest, c8186ce).
 * The worker's return is dispatched to the main thread by the outer context. Hopping back after it
 * is too late: the test's interceptor resumes the caller, and sends the snapshot's changes, on the
 * thread that returned (the phone's MapRenderTest.neuronsLight, d3fd525).
 */
private suspend fun <T> builtOffMain(build: () -> T): T =
    withContext(Dispatchers.Main.immediate) { withContext(Dispatchers.Default) { build() } }

/** A seeded demo map (GraphPerfDemo): synthetic notes, not the user's, turning by itself. */
@Composable private fun DemoMap(size: Int, recenter: Int, readout: Boolean, onShown: (Boolean) -> Unit) {
    val scene by produceState<MapScene?>(null, size) { value = builtOffMain { DemoScenes.get(size) } }
    val ready = scene
    MapShown(ready != null, onShown)
    if (ready == null) { Building(size); return }
    Map3DView(ready, Modifier.fillMaxSize(), description = "${GraphTheme.PERFORMANCE.mapLabel}: demo of ${grouped(size)} made-up notes, not your notes",
        continuous = true, autoOrbit = true, showReadout = readout, showControls = false, recenter = recenter, corner = 0.dp)
}

/** Tells [onShown] whether the map is drawn, not waiting on its layout, and that it isn't once it goes. */
@Composable private fun MapShown(shown: Boolean, onShown: (Boolean) -> Unit) {
    val latest by rememberUpdatedState(onShown)
    LaunchedEffect(shown) { latest(shown) }
    DisposableEffect(Unit) { onDispose { latest(false) } }
}

@Composable private fun Building(size: Int?) {
    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            Text(if (size == null) "Laying out your notes" else "Building ${grouped(size)} notes and their links",
                style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
        }
    }
}

/**
 * Space's Look menu (GraphLookMenu): the theme, then the theme's own choices - for Space the
 * Universe or one style for every note, and folder looks; for Performance the demo maps -
 * then Lines and the theme's legend.
 */
@Composable private fun SpaceLookTool(
    book: IdeaBook, settings: IdeasSettings, theme: GraphTheme,
    showLegend: () -> Unit, showFolderLooks: () -> Unit, openFolder: (String) -> Unit,
) {
    var showing by remember { mutableStateOf(false) }
    // pressed in, its glyph the accent, while the map is off the standard (Space's Universe, with no
    // folder looks), as iOS's GraphStyleTool
    val custom = theme != GraphTheme.SPACE || spaceStyle(settings.nodeStyle) != null ||
        GraphStyleChoice.folders(settings.folderStyles).isNotEmpty()
    Box {
        IdeaToolButton(Icons.Outlined.Palette, "Look", active = custom, value = theme.title) { showing = true }
        DropdownMenu(showing, { showing = false }, containerColor = wardColor(WardToken.background)) {
            MenuHeader("Theme")
            GraphTheme.offered.forEach { t ->
                Choice(t.title, t.icon, t == theme) { settings.setTheme(t.rawValue); showing = false }
            }
            HorizontalDivider()
            // each theme's own choices end on their own divider, so in Neurons, which has none here, one
            // divider stands between Theme and Lines
            if (theme == GraphTheme.SPACE) {
                MenuHeader("Bodies")
                // sparkles, as on iOS: the globe is the rocky planet's
                Choice("Universe", Icons.Rounded.AutoAwesome, spaceStyle(settings.nodeStyle) == null) { settings.setNodeStyle(GraphStyleChoice.AUTO); showing = false }
                SpaceStyle.menuOrder.forEach { s ->
                    Choice(s.label, s.icon, settings.nodeStyle == s.rawValue) { settings.setNodeStyle(s.rawValue); showing = false }
                }
                if (book.subfolders(null).isNotEmpty()) Choice("Folder looks", Icons.Outlined.Folder, false) { showing = false; showFolderLooks() }
                HorizontalDivider()
            } else if (theme == GraphTheme.PERFORMANCE && IdeasSettings.DEVELOPER) {
                MenuHeader("Map")
                Choice("Your notes", Icons.Outlined.Lightbulb, settings.perfDemo !in IdeasSettings.perfDemoSizes) { settings.setPerfDemo(0); showing = false }
                IdeasSettings.perfDemoSizes.forEach { n ->
                    Choice(perfDemoTitle(n), Icons.Rounded.Public, settings.perfDemo == n) { settings.setPerfDemo(n); showing = false }
                }
                HorizontalDivider()
            }
            MenuHeader("Lines")
            // the picker 200 wide inside its room, as on the board
            IdeaLinesPicker(settings, Modifier.padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.xs.wardDp).width(LINES_PICKER_WIDTH.dp))
            HorizontalDivider()
            Choice(theme.legendTitle, Icons.Outlined.Info, false) { showing = false; showLegend() }
        }
    }
}

/** A group's name in a menu, lined up with the items' own 12 (DropdownMenuItem's padding). */
@Composable private fun MenuHeader(text: String) {
    WardSmallCapsText(text, Modifier.padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp).semantics { heading() })
}

/** A menu row; with no glyph (iOS's "Same as all"), the glyph's room stays, so its words line up with the rest. */
@Composable private fun Choice(title: String, icon: ImageVector?, chosen: Boolean, onClick: () -> Unit) {
    DropdownMenuItem({ Text(title, color = wardColor(WardToken.ink)) }, onClick,
        leadingIcon = { if (icon != null) Icon(icon, null, tint = wardColor(WardToken.inkSecondary)) else Spacer(Modifier.size(24.dp)) },
        trailingIcon = if (chosen) ({ Icon(Icons.Outlined.Check, "Chosen", tint = wardColor(WardToken.primaryInk)) }) else null)
}

/** The theme's legend (GraphThemeCard): what the map is made of, and how to grow it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LegendSheet(theme: GraphTheme, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, containerColor = wardColor(WardToken.background)) {
        ReaderNavBar(theme.legendTitle, trailing = { ReaderDoneButton(onClick = onDone) })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = WardSpace.gutter.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            Text(theme.cardTitle, Modifier.semantics { heading() }, style = WardType.headline.textStyle(), color = wardColor(WardToken.ink))
            Text(theme.cardText, style = WardType.body.textStyle(), color = wardColor(WardToken.ink))
            theme.cardSteps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            }
            Text(theme.hint, style = WardType.caption.textStyle(), color = wardColor(WardToken.inkSecondary))
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}

/** Folder looks: each top-level folder's notes as one style, or "Same as all". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FolderLooksSheet(book: IdeaBook, settings: IdeasSettings, onDone: () -> Unit) {
    val looks = GraphStyleChoice.folders(settings.folderStyles)
    fun set(folder: String, style: SpaceStyle?) {
        val next = HashMap(looks)
        if (style == null) next.remove(folder) else next[folder] = style
        settings.setFolderStyles(GraphStyleChoice.encode(next))
    }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = wardColor(WardToken.background)) {
        ReaderNavBar("Folder looks", trailing = { ReaderDoneButton(onClick = onDone) })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = WardSpace.m.wardDp)) {
            book.subfolders(null).forEach { folder ->
                var menu by remember(folder.id) { mutableStateOf(false) }
                val chosen = looks[folder.id]
                Box {
                    DropdownMenuItem({ Text(folder.name, color = wardColor(WardToken.ink)) }, { menu = true },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = toneColor(folder.id, book)) },
                        trailingIcon = { Text(chosen?.label ?: "Same as all", style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.primaryInk)) })
                    DropdownMenu(menu, { menu = false }, containerColor = wardColor(WardToken.background)) {
                        // no glyph, as on iOS: the globe is the rocky planet's
                        Choice("Same as all", null, chosen == null) { set(folder.id, null); menu = false }
                        SpaceStyle.menuOrder.forEach { s -> Choice(s.label, s.icon, chosen == s) { set(folder.id, s); menu = false } }
                    }
                }
            }
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}
