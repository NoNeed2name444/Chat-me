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
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Hive
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.cramdown.sorta.ideas.LegendContent
import com.cramdown.sorta.ideas.LegendRow
import com.cramdown.sorta.ideas.LegendTint
import com.cramdown.sorta.ideas.MapLegend
import com.cramdown.sorta.ui.sources.wardRowTile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.MapMenuItem
import com.cramdown.sorta.ideas.MapPeek
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.NoteTone
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
import com.cramdown.sorta.map.MapSelection
import com.cramdown.sorta.map.MapSelectionEffect
import com.cramdown.sorta.map.MapSelectionEvent
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.NeuronScene
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.map.UniverseEdge
import com.cramdown.sorta.map.UniverseFolder
import com.cramdown.sorta.map.UniverseInput
import com.cramdown.sorta.map.UniverseNote
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.map.LabelRgb
import com.cramdown.sorta.map.NoteLike
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.SyntheticGraph
import com.cramdown.sorta.ui.map.Map3DView
import com.cramdown.sorta.ui.map.MapEase
import com.cramdown.sorta.ui.map.MapFlyTo
import com.cramdown.sorta.ui.map.MapScene
import com.cramdown.sorta.ui.map.MapTheme
import com.cramdown.sorta.ui.sources.ReaderDoneButton
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.LocalWardLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.LocalSortaBars
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
// (GraphTouchModel.swift:3-20, S05g) with MapPanels' panels: one MapSelection decides, as iOS's
// GraphSelection, what each touch the map reports does. A tap chooses a body (marked, its links lit,
// the camera easing towards it) and raises its peek card, a second tap, a double tap or Open reads
// it, a finger held still on it opens its options before it lifts, Show links keeps its own links,
// and a tap on empty space lets it go.

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
    // what each touch does (GraphSelection, S05g); Show links' body; the camera's move for a choice
    val selection = remember(theme) { MapSelection() }
    var linksId by remember(theme) { mutableStateOf<String?>(null) }
    var ease by remember(theme) { mutableStateOf<MapEase?>(null) }
    // each Select's serial, never reused, so the map tells a new choice from one it has already cued (MapChoiceCue)
    var selects by remember { mutableIntStateOf(0) }
    var tuning by remember { mutableStateOf(false) }
    // the first-run card's height as last drawn, in pixels, kept while it steps aside for a peek
    // card or the Link length bar (S05d)
    var cardPx by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<MapBody?>(null) }
    var deletingFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var addingFolder by remember { mutableStateOf(false) }
    // each idea's role on the map as built, for a body chosen from a chip
    var roles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Neurons: the cells opened on the map, outermost first (Fly in opens one, as iOS's flying in
    // does, Graph3DView.swift:1070-1074; Recentre closes them all)
    var openPath by remember(theme) { mutableStateOf<List<String>>(emptyList()) }
    // Space's Universe: the folder Fly in last flew to, with a serial so the same folder flies again (S05h)
    var flyTo by remember(theme) { mutableStateOf<MapFlyTo?>(null) }
    LaunchedEffect(recenter) { openPath = emptyList(); flyTo = null }

    fun openBody(body: MapBody) {
        held = null
        when (body.kind) {
            ThemeBodyKind.NOTE -> book.notes.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { open(it.id) }
            ThemeBodyKind.FOLDER -> book.folders.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { openFolder(it.id) }
            ThemeBodyKind.HOME -> openFolder(null)
        }
    }
    /**
     * Hands [event] to the selection and acts on its effects in order, as iOS's perform does
     * (Graph3DView.swift:530-546). [body] is the body the touch found, [at] where it was held.
     */
    fun react(event: MapSelectionEvent, body: MapBody? = null, at: Offset = Offset.Zero) {
        for (effect in selection.handle(event, android.os.SystemClock.uptimeMillis() / 1000.0)) when (effect) {
            is MapSelectionEffect.Select -> {
                chosen = body?.takeIf { it.id.equals(effect.id, ignoreCase = true) }
                    ?: MapBody(effect.id, ThemeBodyKind.NOTE, roles[effect.id.uppercase()] ?: "")
                held = null
                selects++
                ease = MapEase(effect.id, effect.glide, selects)
            }
            MapSelectionEffect.Clear -> { chosen = null; held = null }
            is MapSelectionEffect.Open -> (chosen?.takeIf { it.id.equals(effect.id, ignoreCase = true) } ?: body)?.let { openBody(it) }
            is MapSelectionEffect.Links -> linksId = effect.id
            is MapSelectionEffect.Menu -> (chosen?.takeIf { it.id.equals(effect.id, ignoreCase = true) } ?: body)?.let { held = it to at }
        }
    }
    // back from what was opened over the map: still chosen (GraphSelection's closed)
    LaunchedEffect(covered) { if (!covered) react(MapSelectionEvent.Closed) }
    // a chosen idea or folder deleted: it goes, its card and Show links with it
    LaunchedEffect(book.notes, book.folders) {
        val body = chosen ?: return@LaunchedEffect
        val there = when (body.kind) {
            ThemeBodyKind.NOTE -> book.notes.any { it.id.equals(body.id, ignoreCase = true) }
            ThemeBodyKind.FOLDER -> book.folders.any { it.id.equals(body.id, ignoreCase = true) }
            ThemeBodyKind.HOME -> true
        }
        if (!there) react(MapSelectionEvent.Gone(body.id))
    }
    // a body's options (Graph3DView.choose, Graph3DView.swift:542-553)
    fun pick(item: MapMenuItem, body: MapBody) {
        held = null
        val note = book.notes.firstOrNull { body.kind == ThemeBodyKind.NOTE && it.id.equals(body.id, ignoreCase = true) }
        when (item) {
            MapMenuItem.OPEN -> openBody(body)
            MapMenuItem.RENAME -> renaming = body
            MapMenuItem.MOVE -> note?.let { choose(it.id) }
            MapMenuItem.DELETE -> if (note != null) { react(MapSelectionEvent.Gone(body.id)); delete(note.id) }
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
                // the first view keeps under the strip, or the status bar where there is none, above the
                // foot, and above the first-run card while it is not put away (S05d): by the card's own
                // rules, not the moments it steps aside, so those never move the camera
                else -> NotesMap(book, settings, theme, recenter, openPath, panels.linkLength, onRoles = { roles = it },
                    top = maxOf(top, WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()), hidden = hidden,
                    card = if (theme != GraphTheme.PERFORMANCE && panels.cardShown(theme) && cardPx > 0)
                        hidden + WardSpace.l.wardDp + with(LocalDensity.current) { cardPx.toDp() } else 0.dp,
                    onTap = { body -> if (body == null) react(MapSelectionEvent.TapEmpty) else react(MapSelectionEvent.TapBody(body.id), body) },
                    onDoubleTap = { body -> react(MapSelectionEvent.DoubleTap(body.id), body) },
                    onHold = { body, at -> react(MapSelectionEvent.Hold(body.id), body, at) },
                    chosenId = chosen?.id, heldId = held?.first?.id, linksId = linksId, ease = ease, flyTo = flyTo, shown = shown) { shown = it }
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
            // the first-run card, bottom-leading, clear of the round tools (Graph3DView.swift:302-318),
            // while its steps or its touch lines are unseen (hintShown, :497-499); kept below the chrome at the
            // top, as the no-folder hint is, so where it is too tall its lines scroll there (MapTouchCard)
            if (panelsShown && theme != GraphTheme.PERFORMANCE && panels.cardShown(theme) && peek == null && !tuning) Box(Modifier.align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = WardSpace.gutter.wardDp, end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp,
                    top = maxOf(top, WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()) + WardSpace.m.wardDp,
                    bottom = hidden + WardSpace.l.wardDp)) {
                // its height as drawn: the first view is framed above it (NotesMap's card)
                // put away, its height is let go: the next theme's card is measured afresh (S05d)
                val stepsUnseen = !panels.stepsSeen(theme)
                MapTouchCard(more = { cardPx = 0; panels.markCardSeen(theme); legend = true }, done = { cardPx = 0; panels.markCardSeen(theme) },
                    modifier = Modifier.onSizeChanged { cardPx = it.height },
                    title = if (stepsUnseen) theme.cardTitle else null, touch = !panels.touchSeen(theme),
                    steps = if (stepsUnseen) theme.cardSteps else emptyList())
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
                        MapPeekCard(content, folder = body.kind != ThemeBodyKind.NOTE, words = words, open = { react(MapSelectionEvent.OpenCard, body) },
                            flyIn = {
                                if (theme == GraphTheme.NEURONS) cellsOpenedBy(body.kind, body.id, book)?.let { openPath = it }
                                else flyTo = MapFlyTo(body.id, (flyTo?.serial ?: 0) + 1)
                            },
                            chip = { id -> react(MapSelectionEvent.Chip(id), MapBody(id, ThemeBodyKind.NOTE, roles[id.uppercase()] ?: "")) },
                            option = { pick(it, body) },
                            linksShown = linksId != null, showLinks = { react(MapSelectionEvent.ShowLinks, body) })
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
                        linkLength = panels.linkLength, tuneLinks = if (demo == null) ({ tuning = true; held = null; react(MapSelectionEvent.TapEmpty) }) else null)
                    // iOS's smallcircle.filled.circle, on every Recentre
                    IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { recenter++ }
                }
            }
        }
    }
    if (legend) LegendSheet(theme, readout = theme == GraphTheme.PERFORMANCE && settings.readout) { legend = false }
    if (folderLooks) FolderLooksSheet(book, settings) { folderLooks = false }
    renaming?.let { body ->
        val note = book.notes.firstOrNull { body.kind == ThemeBodyKind.NOTE && it.id.equals(body.id, ignoreCase = true) }
        val folder = book.folders.firstOrNull { body.kind == ThemeBodyKind.FOLDER && it.id.equals(body.id, ignoreCase = true) }
        when {
            note != null -> NameSheet(stringResource(R.string.map_rename_idea_title), stringResource(R.string.map_rename_idea_field), note.title,
                stringResource(R.string.name_rename), onDismiss = { renaming = null }, overMap = shown) { book.update(note.copy(title = it.trim())) }
            folder != null -> NameSheet(stringResource(R.string.name_rename_title), stringResource(R.string.name_field), folder.name,
                stringResource(R.string.name_rename), onDismiss = { renaming = null }, overMap = shown) { book.renameFolder(folder.id, it) }
            else -> renaming = null
        }
    }
    // Add folder on the no-folder hint: the naming sheet, then the folder (as Sort's New folder)
    if (addingFolder) NameSheet(stringResource(R.string.name_new_title), stringResource(R.string.name_field), "", stringResource(R.string.name_create),
        onDismiss = { addingFolder = false }, overMap = shown) { book.createFolder(it) }
    // a folder's Delete confirms with S05's words, as the Sort screen's (IdeasView.kt)
    deletingFolder?.let { folder ->
        val parent = book.folder(folder.parentId)?.name ?: stringResource(R.string.chooser_to_sort)
        AlertDialog(onDismissRequest = { deletingFolder = null }, containerColor = wardColor(WardToken.background),
            title = { Text(stringResource(R.string.folder_delete_title, folder.name), color = wardColor(WardToken.ink)) },
            text = { Text(stringResource(R.string.folder_delete_body, parent), color = wardColor(WardToken.ink)) },
            confirmButton = {
                TextButton({ deletingFolder = null; react(MapSelectionEvent.Gone(folder.id)); book.deleteFolder(folder.id) }) {
                    Text(stringResource(R.string.folder_delete_confirm), color = wardColor(WardToken.danger))
                }
            },
            dismissButton = { TextButton({ deletingFolder = null }) { Text(stringResource(R.string.sheet_cancel), color = wardColor(WardToken.primaryInk)) } })
    }
}

/** The user's notes on the map, rebuilt off the main thread when they or the look change. */
/** A built map, and each idea's role on it by uppercased id (MapPeekInput.role). */
private class BuiltMap(val scene: MapScene, val roles: Map<String, String>, val styles: ByteArray?)

/**
 * The user's notes on the map, rebuilt off the main thread when they, the look or the Link length
 * change. [onTap] hears a tap, on a body or on empty space (null), [onDoubleTap] a double tap on a
 * body and [onHold] a body held still (and where), each as the map decides it while the finger is
 * down (S05g); [chosenId] and [heldId] are the chosen and held bodies, whose names stay on show,
 * [linksId] the body Show links is on, and [ease] the camera's move for a choice. Its
 * first view fits what the chrome leaves: under [top] and above [hidden], as the 2D board's.
 * [onShown] hears that the map is drawn, once its first frame is; until [shown] comes back, with the
 * screen's night look and tools, the wait covers the map, so the two come in the same frame.
 */
@Composable private fun NotesMap(book: IdeaBook, settings: IdeasSettings, theme: GraphTheme, recenter: Int, openPath: List<String>,
                                  linkLength: Double, onRoles: (Map<String, String>) -> Unit, top: Dp, hidden: Dp,
                                  card: Dp,
                                  onTap: (MapBody?) -> Unit, onDoubleTap: (MapBody) -> Unit, onHold: (MapBody, Offset) -> Unit,
                                  chosenId: String?, heldId: String?, linksId: String?, ease: MapEase?, flyTo: MapFlyTo?,
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
                // the Universe, Space's default main look (S05h; iOS's isUniverse, Graph3DView.swift:879-881): its
                // folders black holes and stars, its notes planets, moons, a pulsar and comets, on orbits
                val universe = if (spaceStyle(main) == null) UniversePlan.of(universeInput(book, edges, linkLength)) else null
                if (universe != null && universe.bodies.isNotEmpty()) {
                    BuiltMap(MapScene.universe(universe, galaxyTones(universe, book)),
                        universe.bodies.associate { it.id.uppercase() to it.role.raw }, null)
                }
                // one single style: today's map (S05g2: a held body pulls its neighbours)
                else BuiltMap(MapScene.of(graph, theme = MapTheme.SPACE, styles = styles, universe = false),
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
    // each body the map reports, as the card and the selection take it (an idea's role from its look).
    // A value made per built map, not a local fun: the touch lambdas below are remembered by what they
    // capture, which leaves out a local fun's own captures, so after a new look they kept the old
    // map's bodies and a tap on a Neurons cell chose the Space idea at its index
    val bodyOf: (Int) -> MapBody? = remember(map) {
        { index ->
            val body = map.scene.neurons?.plan?.bodies?.getOrNull(index)
            val star = map.scene.universePlan?.bodies?.getOrNull(index)
            when {
                body != null -> MapBody(body.id, body.kind, body.role.toString())
                star != null -> MapBody(star.id, when (star.role) {
                    UniverseRole.HOME -> ThemeBodyKind.HOME
                    UniverseRole.GALAXY, UniverseRole.STAR -> ThemeBodyKind.FOLDER
                    else -> ThemeBodyKind.NOTE
                }, star.role.raw)
                else -> map.scene.ids.getOrNull(index)?.let { id ->
                    MapBody(id, ThemeBodyKind.NOTE, map.styles?.getOrNull(index)?.let { MapPeek.spaceRole(SpaceStyle.of(it.toInt())) } ?: "")
                }
            }
        }
    }
    val latestTap by rememberUpdatedState(onTap)
    val latestDouble by rememberUpdatedState(onDoubleTap)
    val latestHold by rememberUpdatedState(onHold)
    Box(Modifier.fillMaxSize()) {
        Map3DView(ready, Modifier.fillMaxSize(), description = description,
            // the map decides each touch while the finger is down (S05g); a body it can't name is empty space
            onTap = { index -> latestTap(index?.let(bodyOf)) },
            onDoubleTap = { index -> bodyOf(index)?.let { latestDouble(it) } },
            onHold = { index, at -> bodyOf(index)?.let { latestHold(it, at) } },
            showReadout = theme == GraphTheme.PERFORMANCE && settings.readout, showControls = false, recenter = recenter, corner = 0.dp,
            onDrawn = { drawnOnce = true }, top = top, hidden = hidden, card = card,
            // Lines: Straight reaches the map's renderer as it changes, with no new scene (S05e)
            straight = settings.lineStyle.isStraight,
            // the chosen and held bodies keep their names on show (S05f); Show links and the ease (S05g)
            chosenId = chosenId, heldId = heldId, linksId = linksId, ease = ease, flyTo = flyTo)
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
 * What Space's Universe plan reads (S05h; iOS's universeInput, Graph3DView.swift:1103-1122): every
 * note, folder and link, ids uppercased, words, kind and creation (seconds from 1 Jan 2001), and the
 * Link length as its linkScale. No anatomy, and no cells opened.
 */
internal fun universeInput(book: IdeaBook, edges: List<Pair<String, String>>, linkScale: Double): UniverseInput {
    val epoch2001 = 978_307_200.0
    val notes = book.notes.map { n ->
        UniverseNote(n.id.uppercase(), n.title, n.noteKind == NoteKind.PAGE, n.folderId?.uppercase(),
            GraphUniverse.wordCount(n.body), n.createdAt / 1000.0 - epoch2001)
    }
    val folders = book.folders.map { UniverseFolder(it.id.uppercase(), it.name, it.parentId?.uppercase()) }
    return UniverseInput(notes, folders, edges.map { UniverseEdge(it.first.uppercase(), it.second.uppercase()) }, linkScale = linkScale)
}

/**
 * Each galaxy's tone, by its body index (iOS's galaxyTones, GraphUniverseScene.swift:311-331): a
 * top-level folder's own NoteTone; a galaxy made of a folder whose parent is missing takes the
 * palette's next colours after them, in name order. Its names' rims take it (LabelChooser.rim).
 */
internal fun galaxyTones(plan: UniversePlan, book: IdeaBook): Map<Int, LabelRgb> {
    val tops = book.subfolders(null).map { it.id.uppercase() }
    val tones = HashMap<Int, LabelRgb>()
    val extra = ArrayList<Pair<String, Int>>()
    for ((i, body) in plan.bodies.withIndex()) {
        if (body.role != UniverseRole.GALAXY) continue
        val top = tops.indexOf(body.id.uppercase())
        if (top >= 0) tones[i] = NoteTone.at(top).let { LabelRgb(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) }
        else extra.add(body.title to i)
    }
    extra.sortWith(compareBy<Pair<String, Int>, String>(String.CASE_INSENSITIVE_ORDER) { it.first }.thenBy { it.second })
    for ((k, pair) in extra.withIndex()) tones[pair.second] = NoteTone.at(tops.size + k).let { LabelRgb(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) }
    return tones
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

/**
 * The theme's legend (GraphLegendSheet, S05f): a size ladder, one row per kind of body Android's map
 * draws, and how to touch the map, its words and rungs MapLegend's. Half or full height on a phone,
 * full height on a window 600 dp wide and up (GraphLegendPresentation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LegendSheet(theme: GraphTheme, readout: Boolean, onDone: () -> Unit) {
    val content = MapLegend.of(theme, readout)
    val wide = currentWindowShape().width >= WindowShape.compactMax
    // the sheet's top, its handle and Done below the status bar on every window, the dimmed map behind
    // the bar, as iOS's sheets (S05j item 2, 8d); on a wide window full height at Material3's 640 dp, the
    // iPad's page sheet's width not measured here (the plan's default)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = wide),
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars), sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) },
        containerColor = wardColor(WardToken.background)) {
        LightIconsOverTheMap()
        ReaderNavBar(theme.legendTitle, trailing = { ReaderDoneButton(onClick = onDone) })
        // the tiles' edges and the touch help on the gutter: the tile's own outer s, and the rest here
        Column(Modifier.fillMaxWidth().testTag(MapPanelTags.legend(theme)).verticalScroll(rememberScrollState())
            .padding(horizontal = (WardSpace.gutter - WardSpace.s).wardDp)) {
            if (content.rungs.isNotEmpty()) LegendLadder(content)
            content.rows.forEach { LegendRowTile(it) }
            Text(content.touch, Modifier.padding(horizontal = WardSpace.s.wardDp).padding(top = WardSpace.s.wardDp).testTag(MapPanelTags.LEGEND_TOUCH),
                style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary))
            // the last line clears the sheet's foot (S05g): the sheet itself keeps its content above the
            // navigation bar (Material3's ModalBottomSheet pads it by safeDrawing's bottom), so only the foot's room is added here
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}

/** The size ladder: circles biggest to smallest, each captioned, and the line under them; one item for TalkBack. */
@Composable private fun LegendLadder(content: LegendContent) {
    // on the gutter with the tiles: the sheet's column gives gutter − s, this the s
    Column(Modifier.fillMaxWidth().padding(horizontal = WardSpace.s.wardDp, vertical = WardSpace.s.wardDp).clearAndSetSemantics { contentDescription = content.ladderLabel },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            content.rungs.forEach { rung ->
                Column(Modifier.weight(1f).padding(horizontal = WardSpace.xs.wardDp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                    // the rungs' one height, not a gap, so their captions start level
                    Canvas(Modifier.fillMaxWidth().height(LEGEND_RUNG_HEIGHT.dp)) {
                        val r = rung.size.dp.toPx() / 2
                        drawCircle(rung.fill.color(), r)
                        val ring = rung.ring
                        if (ring != null && rung.ringed) {
                            val w = rung.size.dp.toPx() * 1.8f; val h = rung.size.dp.toPx() * 0.55f
                            drawOval(ring.color(), Offset(center.x - w / 2, center.y - h / 2), androidx.compose.ui.geometry.Size(w, h),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        } else if (ring != null && rung.halo) {
                            drawCircle(ring.color(), r + 1.5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        }
                    }
                    // two lines rather than cut short or shrunk
                    Text(rung.caption, style = WardType.caption.textStyle(), color = wardColor(WardToken.inkSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                }
            }
        }
        Text(content.ladderCaption, style = WardTextStyle.footnote.textStyle(WardWeight.semibold), color = wardColor(WardToken.ink))
    }
}

/** One kind of body: its tinted glyph in a column of its own, its name, its line and how to make one; one item for TalkBack. */
@Composable private fun LegendRowTile(row: LegendRow) {
    Row(Modifier.fillMaxWidth().wardRowTile().padding(vertical = WardSpace.xs.wardDp).clearAndSetSemantics { contentDescription = MapLegend.spoken(row) },
        horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
        // the column the glyphs line up in, not a gap
        Box(Modifier.width(LEGEND_GLYPH_COLUMN.dp), contentAlignment = Alignment.TopCenter) {
            Icon(legendGlyph(row.symbol), null, Modifier.size(wardGlyph(WardTextStyle.title3.size)), tint = row.tint.color())
        }
        // 2, under the scale's 4, so the lines read as one item (WardRow)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = WardType.headline.textStyle(), color = wardColor(WardToken.ink))
            Text(row.text, style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            if (row.how.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp), verticalAlignment = Alignment.Top) {
                // the words in ink, the colour on the glyph alone
                Icon(Icons.Rounded.AddCircleOutline, null, Modifier.padding(top = 2.dp).size(wardGlyph(WardTextStyle.footnote.size)), tint = row.tint.color())
                Text(row.how, style = WardTextStyle.footnote.textStyle(WardWeight.medium), color = wardColor(WardToken.ink))
            }
        }
    }
}

/** A rung's slot height and the rows' glyph column, in dp (GraphLegendSheet's 36 and 30). */
private const val LEGEND_RUNG_HEIGHT = 36
private const val LEGEND_GLYPH_COLUMN = 30

private fun LegendTint.color() = androidx.compose.ui.graphics.Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())

/** iOS's SF Symbol for a legend row, as the Material icon the app ships that stands in for it (S05f plan, Q2). */
internal fun legendGlyph(symbol: String): ImageVector = when (symbol) {
    "circle.circle.fill" -> Icons.Rounded.Adjust
    "sun.max.fill" -> Icons.Rounded.WbSunny
    "circle.lefthalf.filled" -> Icons.Rounded.Contrast
    "globe.europe.africa.fill" -> Icons.Rounded.Public
    "dot.radiowaves.left.and.right" -> Icons.Rounded.Sensors
    "sparkle" -> Icons.Rounded.Flare
    "point.3.connected.trianglepath.dotted" -> SHOW_LINKS_GLYPH
    "circle.hexagongrid.fill" -> Icons.Rounded.Hive
    "smallcircle.filled.circle" -> Icons.Rounded.RadioButtonChecked
    "circle.fill" -> Icons.Rounded.Circle
    "aqi.medium" -> Icons.Rounded.Grain
    "tray.fill" -> Icons.Rounded.Inbox
    "arrow.up.left.and.arrow.down.right" -> Icons.Rounded.OpenInFull
    "bolt.horizontal.fill" -> Icons.Rounded.Bolt
    "sun.min.fill" -> Icons.Rounded.LightMode
    "line.diagonal" -> Icons.Rounded.HorizontalRule
    "speedometer" -> Icons.Rounded.Speed
    else -> Icons.Rounded.Circle
}

/** Folder looks: each top-level folder's notes as one style, or "Same as all". */
/**
 * A sheet over the 3D map keeps the map's light status-bar icons: the map is dark by day too, and
 * Material's sheet gives its own window dark icons in light mode, so the clock went dark over the
 * dark map with the legend up (S05f's renders check). The sheet sets them once, as its window is
 * made, so they are set again here, on that window, from inside its content.
 */
@Composable internal fun LightIconsOverTheMap() {
    // on two panes the bar's row is the page's, and its icons follow the page (S05j item 1)
    if (!LocalSortaBars.current) return
    val view = LocalView.current
    SideEffect {
        var at: Any? = view
        while (at != null && at !is DialogWindowProvider) at = (at as? android.view.View)?.parent
        (at as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FolderLooksSheet(book: IdeaBook, settings: IdeasSettings, onDone: () -> Unit) {
    val looks = GraphStyleChoice.folders(settings.folderStyles)
    fun set(folder: String, style: SpaceStyle?) {
        val next = HashMap(looks)
        if (style == null) next.remove(folder) else next[folder] = style
        settings.setFolderStyles(GraphStyleChoice.encode(next))
    }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = wardColor(WardToken.background)) {
        LightIconsOverTheMap()
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
