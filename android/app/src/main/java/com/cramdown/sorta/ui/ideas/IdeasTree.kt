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
import com.cramdown.sorta.ideas.NaturalOrder
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.MapFilter
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.TreeColors
import com.cramdown.sorta.map.TreeLayout3D
import com.cramdown.sorta.map.TreeModel
import com.cramdown.sorta.map.nest.NestFolderIn
import com.cramdown.sorta.map.nest.NestIdeaIn
import com.cramdown.sorta.map.nest.NestNodes
import com.cramdown.sorta.ui.LocalWardDark
import com.cramdown.sorta.ui.map.Map3DView
import com.cramdown.sorta.ui.map.MapFlyTo
import com.cramdown.sorta.ui.map.MapScene
import com.cramdown.sorta.ui.map.TreeTags
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// The Tree look (S11; in 3D by the owner's decision of 18 Nov): the ideas as one tree in the 3D map's
// own renderer and camera (Map3DView, MapTheme.TREE: TreeGl draws it, TreeNames names it). The trunk is
// All ideas, each folder a branch and each idea a leaf. A drag turns the tree, a pinch zooms, a tap on
// a branch flies in to it, a tap on a leaf shows the map's peek card, and a tap on empty space, Back or
// the system's back flies out a level. This file only feeds it the book and the touches.

/** The book as the Tree's nodes: folders in the folder list's order, so each bough takes its folder's colour. */
internal fun treeNodes(book: IdeaBook, trunk: String, untitled: String): NestNodes {
    val folders = book.folders.sortedWith(compareBy(NaturalOrder) { it.name }).map { NestFolderIn(it.id, it.parentId, it.name) }
    val ideas = book.notes.map { NestIdeaIn(it.id, it.folderId, it.title.ifBlank { untitled }, it.createdAt) }
    return NestNodes.build(trunk, folders, ideas)
}

/** The book's links between the tree's nodes, as pairs of node indices (Show links lights them). */
internal fun treeLinks(book: IdeaBook, nodes: NestNodes): IntArray {
    val out = ArrayList<Int>()
    for ((x, y) in book.allEdges()) {
        val i = nodes.index(x); val j = nodes.index(y)
        if (i > 0 && j > 0) { out.add(i); out.add(j) }
    }
    return out.toIntArray()
}

/**
 * What the filter and the lenses leave out of [nodes], by node: an idea they don't let through, and a
 * folder with ideas under it none of which shows. Null with nothing on.
 */
internal fun treeHidden(nodes: NestNodes, book: IdeaBook, filter: MapFilter, lens: Set<String>?): BooleanArray? {
    if (filter == MapFilter.All && lens == null) return null
    val hidden = BooleanArray(nodes.size)
    val degree = HashMap<String, Int>()
    for ((x, y) in book.allEdges()) { degree[x] = (degree[x] ?: 0) + 1; degree[y] = (degree[y] ?: 0) + 1 }
    val shows = IntArray(nodes.size)
    for (note in book.notes) {
        val i = nodes.index(note.id); if (i <= 0) continue
        val ok = filter.admits(note.noteKind == NoteKind.PAGE, book.rootFolder(note.folderId), degree[note.id] ?: 0) &&
            (lens == null || note.id.uppercase() in lens)
        hidden[i] = !ok
        shows[i] = if (ok) 1 else 0
    }
    // a parent comes before its children, so one pass from the end adds each subtree up
    for (i in nodes.size - 1 downTo 1) shows[nodes.parent[i]] += shows[i]
    for (i in 1 until nodes.size) if (nodes.folder[i] && (shows[i] == 0 || hidden[nodes.parent[i]])) hidden[i] = true
    return hidden
}

/** The filter as the map takes it: every node but the hidden ones shown, none a ghost. */
internal fun treeView(filter: MapFilter, hidden: BooleanArray, notes: Int): MapFilterView =
    MapFilterView(filter, BooleanArray(hidden.size) { !hidden[it] }, BooleanArray(hidden.size), BooleanArray(0), notes)

/** The Graphics tier the Tree is drawn at: Smooth on a phone Android counts as low on memory. */
internal fun treeTier(context: Context): GraphicsTier =
    if ((context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true) GraphicsTier.SMOOTH else GraphicsTier.HIGH

/**
 * Map mode's Tree: [book] as a 3D tree, under the top strip ([top]) and above the foot ([hidden]).
 * [open] opens an idea in the editor, [choose] the folder chooser, [delete] deletes with undo;
 * [lens] is the lenses' ids, as IdeasMap passes them; [focusOn] flies in to that part once the tree is
 * drawn (a test's still).
 */
@Composable fun IdeasTree(
    book: IdeaBook, settings: IdeasSettings, hidden: Dp, open: (String) -> Unit,
    choose: (String) -> Unit = {}, delete: (String) -> Unit = {}, top: Dp = 0.dp, version: Int = 0,
    lens: Set<String>? = null, focusOn: String? = null,
) {
    val context = LocalContext.current
    val dark = LocalWardDark.current
    val trunk = stringResource(R.string.map_peek_all_ideas)
    val words = remember(context.resources) { mapPeekWords(context.resources) }
    val tier = remember(context) { treeTier(context) }
    // the tree is grown again only when the book, the mode or the tier changes
    val nodes = remember(book, version, trunk) { treeNodes(book, trunk, words.untitled) }
    val layout = remember(nodes) { TreeLayout3D(nodes) }
    val scene = remember(layout, dark, tier) { MapScene.tree(TreeModel(nodes, layout, TreeColors(dark), tier), treeLinks(book, nodes)) }

    var filter by remember { mutableStateOf<MapFilter>(MapFilter.All) }
    val view = remember(nodes, filter, lens) {
        treeHidden(nodes, book, filter, lens)?.let { h -> treeView(filter, h, (1 until nodes.size).count { !nodes.folder[it] && !h[it] }) }
    }
    var chosenId by remember(nodes) { mutableStateOf<String?>(null) }
    var linksShown by remember(nodes) { mutableStateOf(false) }
    // the part flown in to (0 the whole tree), the fly-in asked for and Recentre's count
    var focus by remember(nodes) { mutableIntStateOf(0) }
    var flyTo by remember(nodes) { mutableStateOf<MapFlyTo?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableIntStateOf(0) }
    var drawn by remember(scene) { mutableStateOf(false) }
    var seen by remember { mutableStateOf(settings.treeSeen) }
    var cardPx by remember { mutableIntStateOf(0) }
    // the Back button's and the round tools' boxes over the map, in its pixels: no name goes under them
    // and a fly-in is framed clear of them (S11)
    var backBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var toolsBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var mapAt by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    fun pick(id: String?) { chosenId = id; if (id == null) linksShown = false }
    fun go(i: Int) {
        focus = i
        if (i <= 0) { flyTo = null; recenter++ } else flyTo = MapFlyTo(nodes.ids[i], ++serial)
    }
    fun back() { pick(null); if (focus > 0) go(nodes.parent[focus]) }
    fun tap(i: Int?) {
        if (!seen) { seen = true; settings.setTreeSeen() }
        when {
            i == null || i !in 0 until nodes.size -> if (chosenId != null) pick(null) else back()
            i > 0 && nodes.childCount[i] == 0 && !nodes.folder[i] -> pick(nodes.ids[i])
            else -> { pick(null); go(i) }
        }
    }
    // a test or a link can start on a part, once the tree is drawn and its view sized
    LaunchedEffect(scene, focusOn, drawn) {
        if (drawn && focusOn != null) { val i = nodes.index(focusOn); if (i > 0) go(i) }
    }

    BackHandler(enabled = focus > 0 || chosenId != null) { back() }

    val showing = nodes.titles[focus.coerceIn(0, nodes.size - 1)]
    val spoken = stringResource(R.string.tree_map_spoken, showing)
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color(0xFF000000.toInt() or scene.treeModel!!.colors.sky)).testTag(TreeTags.MAP)
        .onGloballyPositioned { mapAt = it.positionInRoot() }) {
        Map3DView(scene, Modifier.fillMaxSize(), description = spoken, onTap = { tap(it) }, showControls = false, recenter = recenter,
            corner = 0.dp, onDrawn = { drawn = true }, top = top, hidden = hidden,
            keepClear = listOfNotNull(if (focus > 0) backBox else null, toolsBox).map { it.translate(-mapAt) },
            // the first view keeps above the first-visit card while it shows, as the other looks' do
            card = if (!seen && chosenId == null && cardPx > 0) hidden + WardSpace.l.wardDp + with(density) { cardPx.toDp() } else 0.dp,
            chosenId = chosenId, linksId = if (linksShown) chosenId else null, flyTo = flyTo, onHome = { focus = 0; flyTo = null },
            filter = view)

        if (focus > 0) {
            val up = nodes.titles[nodes.parent[focus]]
            Box(Modifier.align(Alignment.TopStart).padding(top = top)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(WardSpace.gutter.wardDp)) {
                IdeaToolButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.tree_back_spoken, up),
                    modifier = Modifier.testTag(TreeTags.BACK).onGloballyPositioned { backBox = it.boundsInRoot() }) { back() }
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
                val content = MapPeek.content(MapPeekInput("tree", "", folder = false, title = note.title,
                    page = note.noteKind == NoteKind.PAGE, body = note.body, links = links), words)
                androidx.compose.runtime.key(note.id) {
                    MapPeekCard(content, folder = false, words = words, open = { open(note.id) }, flyIn = {},
                        chip = { other -> nodes.index(other).let { if (it > 0) tap(it) } },
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
                WardCardFace(Modifier.widthIn(max = MapPanel.HINT_WIDTH.dp).testTag(TreeTags.FIRST_VISIT).onSizeChanged { cardPx = it.height },
                    lift = WardLift.high) {
                    Text(stringResource(R.string.look_tree), style = WardType.headline.textStyle(), color = wardColor(WardToken.ink))
                    Text(stringResource(R.string.tree_line), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.ink))
                    Text(stringResource(R.string.tree_first_visit), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
                    WardTextButton(stringResource(R.string.map_touch_got_it), { seen = true; cardPx = 0; settings.setTreeSeen() },
                        kind = WardButtonKind.compact, fills = false)
                }
            }
        }

        // the tools: Filter, and Recentre to the whole tree
        Box(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(bottom = hidden)
            .onGloballyPositioned { toolsBox = it.boundsInRoot() }) {
            IdeaToolCluster {
                MapFilterTool(filter, mapTopFolders(book, null)) { filter = it }
                IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { pick(null); go(0) }
            }
        }
    }
}
