package com.cramdown.sorta.ui.ideas

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapMenuItem
import com.cramdown.sorta.ideas.MapPeek
import com.cramdown.sorta.ideas.MapPeekInput
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.map.FractalLayout3D
import com.cramdown.sorta.map.FractalModel
import com.cramdown.sorta.map.FractalTree
import com.cramdown.sorta.map.MapFilter
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.PerfLook
import com.cramdown.sorta.ui.map.FractalTags
import com.cramdown.sorta.ui.map.Map3DView
import com.cramdown.sorta.ui.map.MapFlyTo
import com.cramdown.sorta.ui.map.fractalBudget
import com.cramdown.sorta.ui.map.fractalScene
import com.cramdown.sorta.ui.LocalWardLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// The Fractal look in 3D (S12; the owner's rule of 18 Nov: Fractal is 3D, the flat one never ships):
// the ideas as clusters in clusters in the 3D map's own renderer and camera (Map3DView,
// MapTheme.FRACTAL: FractalGl draws it, FractalNames names it), on Performance's night sky with its
// glowing dots and thin lines. All ideas is the middle hub, each folder a hub round it with what it holds
// round that, ideas in no folder round All ideas itself. A drag turns the map, a pinch zooms, a tap on
// a hub flies in to it, a tap on an idea shows the map's peek card, and a tap on empty space, Back or
// the system's back flies out a level. This file only feeds it the book and the touches.

/** The book as the Fractal's tree: top folders oldest first, each folder's folders then its ideas (FractalTree.of). */
internal fun fractalTree(book: IdeaBook, hub: String, untitled: String): FractalTree =
    FractalTree.of(book.folders.map { FractalTree.Companion.Folder(it.id, it.parentId, it.name, it.createdAt) },
        book.notes.map { FractalTree.Companion.Idea(it.id, it.folderId, it.title.ifBlank { untitled }, it.createdAt) }, hub)

/** Each node by its store id (upper case, as FractalTree keeps them); All ideas' "" left out. */
internal fun fractalIndex(tree: FractalTree): Map<String, Int> =
    HashMap<String, Int>(tree.count * 2).also { m -> for (i in 1 until tree.count) m[tree.ids[i]] = i }

/** The book's links between the tree's ideas, as pairs of node indices. */
internal fun fractalLinks(book: IdeaBook, index: Map<String, Int>): IntArray {
    val out = ArrayList<Int>()
    for ((x, y) in book.allEdges()) {
        val i = index[x.uppercase()] ?: continue; val j = index[y.uppercase()] ?: continue
        out.add(i); out.add(j)
    }
    return out.toIntArray()
}

/**
 * What the filter and the lenses leave out of [tree], by node: an idea they don't let through, and a
 * folder with ideas under it none of which shows. Null with nothing on.
 */
internal fun fractalHidden(tree: FractalTree, index: Map<String, Int>, book: IdeaBook, filter: MapFilter, lens: Set<String>?): BooleanArray? {
    if (filter == MapFilter.All && lens == null) return null
    val hidden = BooleanArray(tree.count)
    val degree = HashMap<String, Int>()
    for ((x, y) in book.allEdges()) { degree[x] = (degree[x] ?: 0) + 1; degree[y] = (degree[y] ?: 0) + 1 }
    val shows = IntArray(tree.count)
    for (note in book.notes) {
        val i = index[note.id.uppercase()] ?: continue
        val ok = filter.admits(note.noteKind == NoteKind.PAGE, book.rootFolder(note.folderId), degree[note.id] ?: 0) &&
            (lens == null || note.id.uppercase() in lens)
        hidden[i] = !ok
        shows[i] = if (ok) 1 else 0
    }
    // a parent comes before its parts, so one pass from the end adds each cluster up
    for (i in tree.count - 1 downTo 1) shows[tree.parent[i]] += shows[i]
    for (i in 1 until tree.count) if (tree.kind[i] != FractalTree.IDEA && (shows[i] == 0 || hidden[tree.parent[i]])) hidden[i] = true
    return hidden
}

/** Whether Android counts this device as short of memory: the Fractal then draws the Low budget (220 dots). */
internal fun fractalLowMemory(context: Context): Boolean =
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

/**
 * Map mode's Fractal: [book] in 3D, under the top strip ([top]) and above the foot ([hidden]). [open]
 * opens an idea in the editor, [choose] the folder chooser, [delete] deletes with undo; [lens] is the
 * lenses' ids, as IdeasMap passes them; [focusOn] flies in to that hub once the map is drawn.
 */
@Composable fun IdeasFractal(
    book: IdeaBook, settings: IdeasSettings, hidden: Dp, open: (String) -> Unit,
    choose: (String) -> Unit = {}, delete: (String) -> Unit = {}, top: Dp = 0.dp, version: Int = 0,
    lens: Set<String>? = null, focusOn: String? = null,
) {
    val context = LocalContext.current
    val hubTitle = stringResource(R.string.map_peek_all_ideas)
    val words = remember(context.resources) { mapPeekWords(context.resources) }
    val lowMemory = remember(context) { fractalLowMemory(context) }
    val tier = remember(context) { treeTier(context) }
    // the map is laid out again only when the book or the tier changes
    val tree = remember(book, version, hubTitle) { fractalTree(book, hubTitle, words.untitled) }
    val index = remember(tree) { fractalIndex(tree) }
    val scene = remember(tree, tier, lowMemory) {
        fractalScene(FractalModel(tree, FractalLayout3D(tree), fractalLinks(book, index), fractalBudget(tier, lowMemory)))
    }

    var filter by remember { mutableStateOf<MapFilter>(MapFilter.All) }
    val view = remember(tree, filter, lens) {
        fractalHidden(tree, index, book, filter, lens)?.let { h ->
            MapFilterView(filter, BooleanArray(h.size) { !h[it] }, BooleanArray(h.size), BooleanArray(0),
                (1 until tree.count).count { tree.kind[it] == FractalTree.IDEA && !h[it] })
        }
    }
    var chosenId by remember(tree) { mutableStateOf<String?>(null) }
    var linksShown by remember(tree) { mutableStateOf(false) }
    // the hub flown in to (0 the whole map), the fly-in asked for and Recentre's count
    var focus by remember(tree) { mutableIntStateOf(0) }
    var flyTo by remember(tree) { mutableStateOf<MapFlyTo?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableIntStateOf(0) }
    // the chrome's boxes over the map, Back's place and the tools, as the Tree's (IdeasTree): no name goes
    // under one, and a fly-in frames clear of them. Back's place is measured while Back is away too, so
    // the first fly-in already knows it
    var backBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var toolsBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var mapAt by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var drawn by remember(scene) { mutableStateOf(false) }
    var seen by remember { mutableStateOf(settings.fractalSeen) }
    var cardPx by remember { mutableIntStateOf(0) }

    fun idOf(i: Int): String? = book.notes.firstOrNull { it.id.equals(tree.ids[i], ignoreCase = true) }?.id
    fun pick(id: String?) { chosenId = id; if (id == null) linksShown = false }
    fun go(i: Int) {
        focus = i
        if (i <= 0) { flyTo = null; recenter++ } else flyTo = MapFlyTo(scene.ids[i], ++serial)
    }
    fun back() { pick(null); if (focus > 0) go(tree.parent[focus]) }
    fun tap(i: Int?) {
        if (!seen) { seen = true; settings.setFractalSeen() }
        when {
            i == null || i !in 0 until tree.count -> if (chosenId != null) pick(null) else back()
            i > 0 && tree.childCount[i] == 0 && tree.kind[i] == FractalTree.IDEA -> pick(idOf(i))
            tree.childCount[i] > 0 || i == 0 -> { pick(null); go(i) }
            else -> pick(null)
        }
    }
    // a test or a link can start on a hub, once the map is drawn and its view sized
    LaunchedEffect(scene, focusOn, drawn) {
        if (drawn && focusOn != null) { val i = index[focusOn.uppercase()] ?: -1; if (i > 0) go(i) }
    }

    BackHandler(enabled = focus > 0 || chosenId != null) { back() }

    val showing = tree.titles[focus.coerceIn(0, tree.count - 1)]
    val spoken = stringResource(R.string.fractal_map_spoken, showing)
    val density = LocalDensity.current
    // the Fractal's sky is night by day too, and so is everything over it, Back, the tools and the cards,
    // as Space and Performance put their tools in night (IdeasSpace) and the top strip over them is
    val outer = LocalWardLook.current
    SortaTheme(dark = true, section = outer.section) {
    Box(Modifier.fillMaxSize().background(Color(PerfLook.SKY[0], PerfLook.SKY[1], PerfLook.SKY[2])).testTag(FractalTags.MAP)
        .onGloballyPositioned { mapAt = it.positionInRoot() }) {
        Map3DView(scene, Modifier.fillMaxSize(), description = spoken, onTap = { tap(it) }, showControls = false, recenter = recenter,
            corner = 0.dp, onDrawn = { drawn = true }, top = top, hidden = hidden,
            // the first view keeps above the first-visit card while it shows, as the other looks' do
            card = if (!seen && chosenId == null && cardPx > 0) hidden + WardSpace.l.wardDp + with(density) { cardPx.toDp() } else 0.dp,
            chosenId = chosenId, linksId = if (linksShown) chosenId else null, flyTo = flyTo, onHome = { focus = 0; flyTo = null },
            filter = view, keepClear = listOfNotNull(if (focus > 0) backBox else null, toolsBox).map { it.translate(-mapAt) })

        Box(Modifier.align(Alignment.TopStart).padding(top = top)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(WardSpace.gutter.wardDp)) {
            Spacer(Modifier.size(WardControlSize.target.wardDp).onGloballyPositioned { backBox = it.boundsInRoot() })
            if (focus > 0) {
                val up = tree.titles[tree.parent[focus]]
                IdeaToolButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.fractal_back_spoken, up),
                    modifier = Modifier.testTag(FractalTags.BACK)) { back() }
            }
        }

        // the chosen idea's card, or the first visit's, bottom-centred beside the tools
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = WardSpace.gutter.wardDp, end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp,
                bottom = hidden + WardSpace.l.wardDp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val id = chosenId
            val note = id?.let { book.note(it) }
            if (note != null) {
                val links = remember(note, version) {
                    book.allEdges().mapNotNull { (a, b) -> if (a == note.id) b else if (b == note.id) a else null }.distinct()
                        .mapNotNull { other -> book.note(other)?.let { other to it.title } }
                }
                val content = MapPeek.content(MapPeekInput("fractal", "", folder = false, title = note.title,
                    page = note.noteKind == NoteKind.PAGE, body = note.body, links = links), words)
                androidx.compose.runtime.key(note.id) {
                    MapPeekCard(content, folder = false, words = words, open = { open(note.id) }, flyIn = {},
                        chip = { other -> index[other.uppercase()]?.let { tap(it) } },
                        option = { item ->
                            when (item) {
                                MapMenuItem.OPEN -> open(note.id)
                                MapMenuItem.MOVE -> choose(note.id)
                                MapMenuItem.DELETE -> { pick(null); delete(note.id) }
                                else -> {}
                            }
                        },
                        linksShown = linksShown, showLinks = { linksShown = !linksShown },
                        more = listOf(MapMenuItem.OPEN, MapMenuItem.MOVE, MapMenuItem.DELETE))
                }
            } else if (!seen) {
                WardCardFace(Modifier.widthIn(max = MapPanel.HINT_WIDTH.dp).testTag(FractalTags.FIRST_VISIT).onSizeChanged { cardPx = it.height },
                    lift = WardLift.high) {
                    Text(stringResource(R.string.look_fractal), style = WardType.headline.textStyle(), color = wardColor(WardToken.ink))
                    Text(stringResource(R.string.fractal_line), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.ink))
                    Text(stringResource(R.string.fractal_first_visit), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
                    WardTextButton(stringResource(R.string.map_touch_got_it), { seen = true; cardPx = 0; settings.setFractalSeen() },
                        kind = WardButtonKind.compact, fills = false)
                }
            }
        }

        // the tools: Filter, and Recentre to the whole map
        Box(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(bottom = hidden)
            .onGloballyPositioned { toolsBox = it.boundsInRoot() }) {
            IdeaToolCluster {
                MapFilterTool(filter, mapTopFolders(book, null)) { filter = it }
                IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { pick(null); go(0) }
            }
        }
    }
    }
}
