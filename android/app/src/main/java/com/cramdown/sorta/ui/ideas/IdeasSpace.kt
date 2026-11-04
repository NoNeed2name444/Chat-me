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
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.MapMenuItem
import com.cramdown.sorta.ideas.MapPeek
import com.cramdown.sorta.ideas.MapTouch
import com.cramdown.sorta.ideas.MapTouchKind
import com.cramdown.sorta.ideas.NoteFolder
import kotlin.math.roundToInt
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
// simulation, so bodies do not drift and cannot be held and moved. Touching it is iOS's model
// (GraphTouchModel.swift:3-20) with MapPanels' panels: a tap chooses a body and raises its peek
// card, a second tap or Open reads it, a hold (judged on lift: the map reports only where a touch
// ended) opens its options, and a tap on empty space lets it go.

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
fun perfDemoTitle(size: Int): String = "Demo: ${grouped(size)} ideas"

/**
 * The 3D map of [book]. [open] reads an idea; [openFolder] a folder, or To sort (null) for the
 * home; [choose] files an idea (Choose folder) and [delete] deletes one, with Undo, both on the
 * caller's side. [top] is what the chrome covers above the map, from the window's top, which the
 * no-folder hint keeps under.
 */
@Composable fun IdeasSpace(
    book: IdeaBook, settings: IdeasSettings, covered: Boolean, hidden: Dp,
    open: (String) -> Unit, openFolder: (String?) -> Unit,
    choose: (String) -> Unit = {}, delete: (String) -> Unit = {}, top: Dp = 0.dp,
) {
    val theme = GraphTheme.stored(settings.theme)
    // the demo maps are a developer's, as on iOS, where only the readout offers them
    val demo = if (theme == GraphTheme.PERFORMANCE && IdeasSettings.DEVELOPER) settings.perfDemo.takeIf { it in IdeasSettings.perfDemoSizes } else null
    var recenter by remember { mutableIntStateOf(0) }
    var legend by remember { mutableStateOf(false) }
    var folderLooks by remember { mutableStateOf(false) }
    val panels = rememberMapPanelSettings()
    val resources = LocalContext.current.resources
    val words = remember(resources) { mapPeekWords(resources) }
    // the chosen body (its card), the one held (its options, where it was held), and the Link length bar
    var chosen by remember(theme) { mutableStateOf<MapBody?>(null) }
    var held by remember(theme) { mutableStateOf<Pair<MapBody, Offset>?>(null) }
    var tuning by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MapBody?>(null) }
    var deletingFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var addingFolder by remember { mutableStateOf(false) }
    // each idea's role on the map as built, for a body chosen from a chip
    var roles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Neurons: the cells opened on the map, outermost first (Fly in opens one, as iOS's flying in
    // does, Graph3DView.swift:1070-1074; Recentre closes them all)
    var openPath by remember(theme) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(recenter) { openPath = emptyList() }

    fun openBody(body: MapBody) {
        held = null
        when (body.kind) {
            ThemeBodyKind.NOTE -> book.notes.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { open(it.id) }
            ThemeBodyKind.FOLDER -> book.folders.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { openFolder(it.id) }
            ThemeBodyKind.HOME -> openFolder(null)
        }
    }
    // a body's options (Graph3DView.choose, Graph3DView.swift:542-553)
    fun pick(item: MapMenuItem, body: MapBody) {
        held = null
        val note = book.notes.firstOrNull { body.kind == ThemeBodyKind.NOTE && it.id.equals(body.id, ignoreCase = true) }
        when (item) {
            MapMenuItem.OPEN -> openBody(body)
            MapMenuItem.RENAME -> renaming = body
            MapMenuItem.MOVE -> note?.let { choose(it.id) }
            MapMenuItem.DELETE -> if (note != null) { chosen = null; delete(note.id) }
                else book.folders.firstOrNull { body.kind == ThemeBodyKind.FOLDER && it.id.equals(body.id, ignoreCase = true) }?.let { deletingFolder = it }
        }
    }

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
                    WardEmptyState(Icons.Outlined.ViewInAr, stringResource(R.string.map_empty_title),
                        stringResource(R.string.map_empty_line))
                }
                // the first view keeps under the strip, or the status bar where there is none, and above the foot
                else -> NotesMap(book, settings, theme, recenter, openPath, panels.linkLength, onRoles = { roles = it },
                    top = maxOf(top, WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()), hidden = hidden,
                    onBody = { body, hold, at ->
                        when {
                            hold -> { chosen = body; held = body to at }
                            MapTouch.tapOpens(chosen?.id, body.id) -> openBody(body)
                            else -> { chosen = body; held = null }
                        }
                    },
                    onEmpty = { chosen = null; held = null }, shown = shown) { shown = it }
            }
            val panelsShown = !covered && shown && demo == null && book.notes.isNotEmpty()
            val peek = chosen?.let { body -> mapPeekInput(book, body, theme, words)?.let { body to MapPeek.content(it, words) } }
            // a map with no folders yet, under the chrome at the top (GraphEmptyHint's place, Graph3DView.swift:289-294)
            // [top] counts from the window's top, the status bar in it, so the bar is only cleared when there is no strip
            if (panelsShown && book.folders.isEmpty() && book.notes.size < 25) Box(Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = maxOf(top, WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()) + WardSpace.m.wardDp,
                    start = WardSpace.xl.wardDp, end = WardSpace.xl.wardDp)) {
                MapEmptyHint(theme, add = { addingFolder = true })
            }
            // the first-run card, bottom-leading, clear of the round tools (Graph3DView.swift:302-318)
            if (panelsShown && theme != GraphTheme.PERFORMANCE && !panels.touchSeen(theme) && peek == null && !tuning) Box(Modifier.align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = WardSpace.gutter.wardDp, end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp,
                    bottom = hidden + WardSpace.l.wardDp)) {
                MapTouchCard(more = { panels.markTouchSeen(theme); legend = true }, done = { panels.markTouchSeen(theme) })
            }
            // the Link length bar and the peek card, bottom-centred beside the tools (bottomPanels,
            // Graph3DView.swift:349-380); the card steps aside while a body's options are up
            if (panelsShown) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = WardSpace.gutter.wardDp, end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp,
                    bottom = hidden + WardSpace.l.wardDp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                if (tuning) MapLinkLengthBar(panels.linkLength, { panels.setLinkLength(it) }, done = { tuning = false })
                if (held == null && peek != null) {
                    val (body, content) = peek
                    androidx.compose.runtime.key(body.id) {
                        MapPeekCard(content, folder = body.kind != ThemeBodyKind.NOTE, words = words, open = { openBody(body) },
                            flyIn = { cellsOpenedBy(body.kind, body.id, book)?.let { openPath = it } },
                            chip = { id -> chosen = MapBody(id, ThemeBodyKind.NOTE, roles[id.uppercase()] ?: "") },
                            option = { pick(it, body) })
                    }
                }
            }
            // a body's options, where it was held (optionsLayer, Graph3DView.swift:384-417)
            val hold = held
            if (panelsShown && hold != null) {
                val (body, at) = hold
                val title = mapPeekInput(book, body, theme, words)?.let { MapPeek.content(it, words).title } ?: ""
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                    MapOptionsMenu(true, { held = null }, title,
                        MapPeek.menu(folder = body.kind == ThemeBodyKind.FOLDER, home = body.kind == ThemeBodyKind.HOME), words) { pick(it, body) }
                }
            }
            // above what the bottom bar hides, and clear of a cutout or a side navigation bar, as
            // iOS's overlay keeps to the safe area while the scene runs under the bars
            if (!covered && shown) Box(Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(bottom = hidden)) {
                IdeaToolCluster {
                    SpaceLookTool(book, settings, theme, showLegend = { legend = true }, showFolderLooks = { folderLooks = true }, openFolder = openFolder,
                        linkLength = panels.linkLength, tuneLinks = if (demo == null) ({ tuning = true; chosen = null; held = null }) else null)
                    // iOS's smallcircle.filled.circle, on every Recentre
                    IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { recenter++ }
                }
            }
        }
    }
    if (legend) LegendSheet(theme) { legend = false }
    if (folderLooks) FolderLooksSheet(book, settings) { folderLooks = false }
    renaming?.let { body ->
        val note = book.notes.firstOrNull { body.kind == ThemeBodyKind.NOTE && it.id.equals(body.id, ignoreCase = true) }
        val folder = book.folders.firstOrNull { body.kind == ThemeBodyKind.FOLDER && it.id.equals(body.id, ignoreCase = true) }
        when {
            note != null -> NameSheet(stringResource(R.string.map_rename_idea_title), stringResource(R.string.map_rename_idea_field), note.title,
                stringResource(R.string.name_rename), onDismiss = { renaming = null }) { book.update(note.copy(title = it.trim())) }
            folder != null -> NameSheet(stringResource(R.string.name_rename_title), stringResource(R.string.name_field), folder.name,
                stringResource(R.string.name_rename), onDismiss = { renaming = null }) { book.renameFolder(folder.id, it) }
            else -> renaming = null
        }
    }
    // Add folder on the no-folder hint: the naming sheet, then the folder (as Sort's New folder)
    if (addingFolder) NameSheet(stringResource(R.string.name_new_title), stringResource(R.string.name_field), "", stringResource(R.string.name_create),
        onDismiss = { addingFolder = false }) { book.createFolder(it) }
    // a folder's Delete confirms with S05's words, as the Sort screen's (IdeasView.kt)
    deletingFolder?.let { folder ->
        val parent = book.folder(folder.parentId)?.name ?: stringResource(R.string.chooser_to_sort)
        AlertDialog(onDismissRequest = { deletingFolder = null }, containerColor = wardColor(WardToken.background),
            title = { Text(stringResource(R.string.folder_delete_title, folder.name), color = wardColor(WardToken.ink)) },
            text = { Text(stringResource(R.string.folder_delete_body, parent), color = wardColor(WardToken.ink)) },
            confirmButton = {
                TextButton({ deletingFolder = null; chosen = null; book.deleteFolder(folder.id) }) {
                    Text(stringResource(R.string.folder_delete_confirm), color = wardColor(WardToken.danger))
                }
            },
            dismissButton = { TextButton({ deletingFolder = null }) { Text(stringResource(R.string.sheet_cancel), color = wardColor(WardToken.primaryInk)) } })
    }
}

/** What the map's own touch handler can't say: how long a touch was held, and whether it found a body. */
private class MapPress {
    var kind = MapTouchKind.TAP
    var hit = false
}

/** The user's notes on the map, rebuilt off the main thread when they or the look change. */
/** A built map, and each idea's role on it by uppercased id (MapPeekInput.role). */
private class BuiltMap(val scene: MapScene, val roles: Map<String, String>, val styles: ByteArray?)

/**
 * The user's notes on the map, rebuilt off the main thread when they, the look or the Link length
 * change. [onBody] hears a body tapped or held (and where), [onEmpty] a tap on empty space. Its
 * first view fits what the chrome leaves: under [top] and above [hidden], as the 2D board's.
 * [onShown] hears that the map is drawn, once its first frame is; until [shown] comes back, with the
 * screen's night look and tools, the wait covers the map, so the two come in the same frame.
 */
@Composable private fun NotesMap(book: IdeaBook, settings: IdeasSettings, theme: GraphTheme, recenter: Int, openPath: List<String>,
                                  linkLength: Double, onRoles: (Map<String, String>) -> Unit, top: Dp, hidden: Dp,
                                  onBody: (MapBody, Boolean, Offset) -> Unit, onEmpty: () -> Unit,
                                  shown: Boolean, onShown: (Boolean) -> Unit) {
    val notes = book.notes
    val edges = remember(notes) { book.allEdges() }
    val main = settings.nodeStyle
    val folderRaw = settings.folderStyles
    val built by produceState<BuiltMap?>(null, notes, theme, main, folderRaw, openPath, linkLength) {
        value = builtOffMain {
            if (theme == GraphTheme.NEURONS) {
                val neurons = MapScene.neurons(NeuronScene.of(neuronsInput(book, edges, openPath, linkLength)))
                val roles = neurons.neurons?.plan?.bodies.orEmpty().filter { it.kind == ThemeBodyKind.NOTE }.associate { it.id.uppercase() to it.role.toString() }
                return@builtOffMain BuiltMap(neurons, roles, null)
            }
            val out = HashMap<String, MutableList<String>>()
            for ((a, b) in edges) out.getOrPut(a) { ArrayList() }.add(b)
            val performance = theme == GraphTheme.PERFORMANCE
            val nodes = notes.map { note ->
                NoteNode(note.id, note.title.ifEmpty { "Untitled" }, out[note.id] ?: emptyList(),
                    if (performance) listOfNotNull(book.rootFolder(note.folderId)) else emptyList())
            }
            // the Link length is the links' rest length (A5)
            val graph = MapGraph.fromNotes(nodes, rest = MapGraph.REST * linkLength.toFloat())
            if (performance) BuiltMap(MapScene.of(graph, theme = MapTheme.PLAIN), emptyMap(), null)
            else {
                val degree = IntArray(graph.nodeCount)
                for (k in graph.edges.indices) degree[graph.edges[k]]++
                val styles = GraphStyleChoice.codes(book, graph.ids, degree, main, folderRaw)
                BuiltMap(MapScene.of(graph, theme = MapTheme.SPACE, styles = styles),
                    graph.ids.indices.associate { graph.ids[it].uppercase() to MapPeek.spaceRole(SpaceStyle.of(styles[it].toInt())) }, styles)
            }
        }
    }
    val map = built
    // the first frame only: a rebuilt map (a new look or Link length) keeps its tools up meanwhile.
    // Told as soon as the map was built, the screen put its tools and night look on a frame before
    // the map's own first frame, over its blank night (S03-result, the last check)
    var drawnOnce by remember { mutableStateOf(false) }
    MapShown(map != null && drawnOnce, onShown)
    val latestRoles by rememberUpdatedState(onRoles)
    LaunchedEffect(map) { map?.let { latestRoles(it.roles) } }
    if (map == null) { Building(null); return }
    val ready = map.scene
    val description = "${theme.mapLabel}: ${notes.size} ${if (notes.size == 1) "idea" else "ideas"}, ${edges.size} ${if (edges.size == 1) "link" else "links"}. " +
        theme.hint
    // Map3DView reports only a tap's body, when the finger lifts. So the touch is watched on its way
    // in (the Initial pass, before the map's own handler) to judge it (MapTouch.judge), and on its
    // way out (the Final pass, after) to tell a tap on nothing from one on a body.
    val press = remember { MapPress() }
    val config = LocalViewConfiguration.current
    val latestEmpty by rememberUpdatedState(onEmpty)
    var at by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = 0f
            var multi = false
            var lifted = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) multi = true
                if (pressed.isEmpty()) { lifted = event.changes.maxOfOrNull { it.uptimeMillis } ?: lifted; break }
                val change = pressed.first()
                moved += (change.position - change.previousPosition).getDistance()
            }
            press.kind = MapTouch.judge(lifted - down.uptimeMillis, moved, config.touchSlop, multi, config.longPressTimeoutMillis)
            press.hit = false
            at = down.position
            // the same lift, after the map has looked for a body under it
            awaitPointerEvent(PointerEventPass.Final)
            if (!press.hit && press.kind == MapTouchKind.TAP) latestEmpty()
        }
    }) {
        Map3DView(ready, Modifier.fillMaxSize(), description = description,
            onOpenNode = { index ->
                press.hit = true
                val body = ready.neurons?.plan?.bodies?.getOrNull(index)
                val chosen = when {
                    body != null -> MapBody(body.id, body.kind, body.role.toString())
                    else -> ready.ids.getOrNull(index)?.let { id ->
                        MapBody(id, ThemeBodyKind.NOTE, map.styles?.getOrNull(index)?.let { MapPeek.spaceRole(SpaceStyle.of(it.toInt())) } ?: "")
                    }
                }
                if (chosen != null) onBody(chosen, press.kind == MapTouchKind.HOLD, at)
            },
            showReadout = theme == GraphTheme.PERFORMANCE && settings.readout, showControls = false, recenter = recenter, corner = 0.dp,
            onDrawn = { drawnOnce = true }, top = top, hidden = hidden)
        if (!shown) Building(null)
    }
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
private fun neuronsInput(book: IdeaBook, edges: List<Pair<String, String>>, open: List<String>, linkScale: Double): UniverseInput {
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
    // the Link length is the plan's linkScale, as iOS's universeInput passes it
    return UniverseInput(notes, folders, edges.map { UniverseEdge(it.first.uppercase(), it.second.uppercase()) },
        linkScale = linkScale, anatomy = anatomy, open = open)
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
    Map3DView(ready, Modifier.fillMaxSize(), description = "${GraphTheme.PERFORMANCE.mapLabel}: demo of ${grouped(size)} made-up ideas, not your ideas",
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
            Text(if (size == null) "Laying out your ideas" else "Building ${grouped(size)} ideas and their links",
                style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
        }
    }
}

/**
 * Space's Look menu (GraphLookMenu): the theme's own choices - for Space the
 * Universe or one style for every note, and folder looks; for Performance the demo maps -
 * then Lines, Link length (GraphNodeStyles.swift:151-158, where the map is laid out) and the theme's legend.
 */
@Composable private fun SpaceLookTool(
    book: IdeaBook, settings: IdeasSettings, theme: GraphTheme,
    showLegend: () -> Unit, showFolderLooks: () -> Unit, openFolder: (String?) -> Unit,
    linkLength: Double = MapLinkLength.STANDARD, tuneLinks: (() -> Unit)? = null,
) {
    var showing by remember { mutableStateOf(false) }
    // pressed in, its glyph the accent, while the map is off the standard (Space's Universe, with no
    // folder looks), as iOS's GraphStyleTool
    val custom = theme != GraphTheme.SPACE || spaceStyle(settings.nodeStyle) != null ||
        GraphStyleChoice.folders(settings.folderStyles).isNotEmpty()
    Box {
        IdeaToolButton(Icons.Outlined.Palette, "Look", active = custom, value = theme.title) { showing = true }
        DropdownMenu(showing, { showing = false }, containerColor = wardColor(WardToken.background)) {
            // the theme is the look chip's (S05b checklist 4), so the menu starts with the theme's own
            // choices, each group ending on its divider; Neurons, which has none here, starts with Lines
            if (theme == GraphTheme.SPACE) {
                // iOS's word for the group (GraphNodeStyles.swift, spaceLooks' Picker)
                MenuHeader("Look")
                // sparkles, as on iOS: the globe is the rocky planet's
                Choice("Universe", Icons.Rounded.AutoAwesome, spaceStyle(settings.nodeStyle) == null) { settings.setNodeStyle(GraphStyleChoice.AUTO); showing = false }
                SpaceStyle.menuOrder.forEach { s ->
                    Choice(s.label, s.icon, settings.nodeStyle == s.rawValue) { settings.setNodeStyle(s.rawValue); showing = false }
                }
                if (book.subfolders(null).isNotEmpty()) Choice("Folder looks", Icons.Outlined.Folder, false) { showing = false; showFolderLooks() }
                HorizontalDivider()
            } else if (theme == GraphTheme.PERFORMANCE && IdeasSettings.DEVELOPER) {
                MenuHeader("Map")
                Choice("Your ideas", Icons.Outlined.Lightbulb, settings.perfDemo !in IdeasSettings.perfDemoSizes) { settings.setPerfDemo(0); showing = false }
                IdeasSettings.perfDemoSizes.forEach { n ->
                    Choice(perfDemoTitle(n), Icons.Rounded.Public, settings.perfDemo == n) { settings.setPerfDemo(n); showing = false }
                }
                HorizontalDivider()
            }
            MenuHeader("Lines")
            // the picker 200 wide inside its room, as on the board
            IdeaLinesPicker(settings, Modifier.padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.xs.wardDp).width(LINES_PICKER_WIDTH.dp))
            HorizontalDivider()
            if (tuneLinks != null) {
                // iOS's arrow.left.and.right
                Choice(stringResource(R.string.map_link_length_item, linkLengthSpoken(linkLength)), Icons.Outlined.SwapHoriz, false,
                    Modifier.testTag(MapPanelTags.LOOK_LINK_LENGTH)) { showing = false; tuneLinks() }
            }
            Choice(theme.legendTitle, Icons.Outlined.Info, false) { showing = false; showLegend() }
        }
    }
}

/** A group's name in a menu, lined up with the items' own 12 (DropdownMenuItem's padding). */
@Composable private fun MenuHeader(text: String) {
    WardSmallCapsText(text, Modifier.padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp).semantics { heading() })
}

/** A menu row; with no glyph (iOS's "Same as all"), the glyph's room stays, so its words line up with the rest. */
@Composable private fun Choice(title: String, icon: ImageVector?, chosen: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    DropdownMenuItem({ Text(title, color = wardColor(WardToken.ink)) }, onClick, modifier,
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
