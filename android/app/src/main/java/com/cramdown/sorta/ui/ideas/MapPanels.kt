package com.cramdown.sorta.ui.ideas

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapMenuItem
import com.cramdown.sorta.ideas.MapPeek
import com.cramdown.sorta.ideas.MapPeekContent
import com.cramdown.sorta.ideas.MapPeekInput
import com.cramdown.sorta.ideas.MapPeekWords
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import java.util.Locale

// Map mode's own panels over Android's GL map (S05b checklist 6), ported from iOS's
// GraphMapPanels.swift and GraphLegend.swift so both platforms work alike: the peek card a tap
// raises (GraphPeekCardView, GraphMapPanels.swift:57-190), a body's options (GraphNodeMenuView,
// :205-260, here the menus family's DropdownMenu), the Link length bar (GraphLinkLengthBar,
// :313-389), the first-run card (GraphUniverseHint, GraphLegend.swift:368-418) and the hint over a
// map with no folders (GraphEmptyHint, :420-460). The test tags are iOS's identifiers. What the card
// says is ideas/MapPeek.kt's; IdeasSpace places the panels and does what they ask.

/** The widths of the panels over the map (GraphPanel, GraphMapPanels.swift:47-54). */
object MapPanel {
    /** The peek card and the Link length bar. */
    const val MAX_WIDTH = 520
    /** The first-run card, sized so it clears the round tools on a phone. */
    const val HINT_WIDTH = 320
    /** The hint over a map with no folders yet. */
    const val EMPTY_HINT_WIDTH = 340
}

/** iOS's accessibility identifiers, as test tags. */
object MapPanelTags {
    const val PEEK = "graphPeekCard"
    const val PEEK_TITLE = "graphPeekTitle"
    const val PEEK_OPEN = "graphPeekOpen"
    const val MENU = "graphNodeMenu"
    const val LINK_BAR = "linkLengthBar"
    const val LINK_SLIDER = "linkLengthSlider"
    const val LINK_DONE = "linkLengthDone"
    const val LOOK_LINK_LENGTH = "lookLinkLength"
    const val HINT = "universeHint"
    const val TOUCH_HINT = "touchHint"
    const val EMPTY_HINT = "graphEmptyHint"
    const val ADD_FOLDER = "graphAddFolder"
}

/** A body on the map: an idea, a folder or the home, with its theme's role ([MapPeekInput.role]). */
data class MapBody(val id: String, val kind: ThemeBodyKind, val role: String = "")

/** The card's and the options' words, from strings.xml. */
fun mapPeekWords(res: Resources): MapPeekWords = MapPeekWords(
    untitled = res.getString(R.string.idea_untitled),
    idea = res.getString(R.string.map_peek_idea),
    page = res.getString(R.string.map_peek_page),
    folder = res.getString(R.string.map_peek_folder),
    toSort = res.getString(R.string.chooser_to_sort),
    allIdeas = res.getString(R.string.map_peek_all_ideas),
    nothingYet = res.getString(R.string.map_peek_nothing),
    open = res.getString(R.string.map_peek_open),
    openFolder = res.getString(R.string.map_peek_open_folder),
    flyIn = res.getString(R.string.map_peek_fly_in),
    more = res.getString(R.string.map_peek_more),
    rename = res.getString(R.string.map_menu_rename),
    moveToFolder = res.getString(R.string.map_menu_move),
    delete = res.getString(R.string.map_menu_delete),
    // GraphPeek.roleWord (GraphTouchModel.swift:267-281): Space's bodies, and Neurons' roles by number
    roles = mapOf(
        "space:galaxy" to res.getString(R.string.map_role_black_hole),
        "space:star" to res.getString(R.string.map_role_star),
        "space:gasGiant" to res.getString(R.string.map_role_gas_giant),
        "space:rocky" to res.getString(R.string.map_role_rocky),
        "space:comet" to res.getString(R.string.map_role_comet),
        "space:pulsar" to res.getString(R.string.map_role_pulsar),
        "neurons:0" to res.getString(R.string.map_role_cell),
        "neurons:1" to res.getString(R.string.map_role_cell_part),
        "neurons:2" to res.getString(R.string.map_role_cell),
        "neurons:3" to res.getString(R.string.map_role_vesicle),
        "neurons:4" to res.getString(R.string.map_role_granule),
    ),
    ideas = { res.getQuantityString(R.plurals.map_peek_ideas, it, it) },
    subfolders = { res.getQuantityString(R.plurals.map_peek_subfolders, it, it) },
    links = { res.getQuantityString(R.plurals.map_peek_links, it, it) },
    latest = { res.getString(R.string.map_peek_latest, it) },
    moreLinks = { res.getString(R.string.map_peek_more_links, it) },
)

/** What the card is told about [body] (iOS Graph3DView's peek), or null when the book no longer has it. */
fun mapPeekInput(book: IdeaBook, body: MapBody, theme: GraphTheme, words: MapPeekWords): MapPeekInput? = when (body.kind) {
    ThemeBodyKind.NOTE -> book.notes.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { note ->
        val others = book.allEdges().mapNotNull { (a, b) -> if (a == note.id) b else if (b == note.id) a else null }.distinct()
        MapPeekInput(theme.rawValue, body.role, folder = false, title = note.title, page = note.noteKind == NoteKind.PAGE,
            body = note.body, links = others.mapNotNull { id -> book.note(id)?.let { id to it.title } })
    }
    ThemeBodyKind.FOLDER -> book.folders.firstOrNull { it.id.equals(body.id, ignoreCase = true) }?.let { folder ->
        MapPeekInput(theme.rawValue, body.role, folder = true, title = folder.name, notes = book.count(folder.id),
            subfolders = book.subfolders(folder.id).size,
            recent = book.notes.filter { it.folderId == folder.id }.sortedByDescending { it.createdAt }.take(3).map { it.title },
            flyIn = theme == GraphTheme.NEURONS)
    }
    ThemeBodyKind.HOME -> {
        val unsorted = book.folders.isNotEmpty()
        val inside = if (unsorted) book.notes.filter { it.folderId == null } else book.notes
        MapPeekInput(theme.rawValue, body.role, folder = false, home = true, unsorted = unsorted,
            title = if (unsorted) words.toSort else words.allIdeas, notes = inside.size,
            recent = inside.sortedByDescending { it.createdAt }.take(3).map { it.title }, flyIn = theme == GraphTheme.NEURONS)
    }
}

// MARK: - What the panels keep

/**
 * The Link length (iOS GraphLinkLength, ios/Sorta/Shared/Space/GraphicsQuality.swift:271-310): how
 * far apart linked bodies sit, from Shorter (0.6) to Longer (1.8) in steps of 0.1, under iOS's key.
 * In Space and Performance it is MapGraph's rest length (MapGraph.REST times it); in Neurons the
 * plan's linkScale, as on iOS.
 */
object MapLinkLength {
    const val KEY = "vignette.space.linkLength"
    const val STANDARD = 1.0
    const val SHORTEST = 0.6
    const val LONGEST = 1.8
    const val STEP = 0.1
    /** The slider's stops between its ends. */
    const val STEPS = 11

    /** A stored or dragged value as the layout uses it: clamped, snapped to the step, the standard for anything unusable. */
    fun clamped(value: Double): Double {
        if (!value.isFinite() || value <= 0) return STANDARD
        val inside = value.coerceIn(SHORTEST, LONGEST)
        return Math.round(Math.round(inside / STEP) * STEP * 100) / 100.0
    }

    fun stored(raw: Double?): Double = raw?.let(::clamped) ?: STANDARD

    fun isStandard(value: Double): Boolean = kotlin.math.abs(clamped(value) - STANDARD) < 0.001

    /** "1.4", as the spoken value shows it. */
    fun shown(value: Double): String = String.format(Locale.ROOT, "%.1f", clamped(value))
}

/** Which themes' first-run card has shown the touch lines (iOS GraphMapTouch, GraphMapPanels.swift:391-403). */
object MapTouchSeen {
    const val KEY = "vignette.space.touchHintSeen"

    fun seen(raw: String, theme: String): Boolean = raw.split(",").any { it == theme }

    fun marking(raw: String, theme: String): String = when {
        seen(raw, theme) -> raw
        raw.isEmpty() -> theme
        else -> "$raw,$theme"
    }
}

/** The panels' two stored values, in IdeasSettings' file under iOS's keys. */
class MapPanelSettings(context: Context) {
    private val prefs: SharedPreferences? = runCatching { context.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE) }.getOrNull()
    private fun put(block: SharedPreferences.Editor.() -> Unit) { runCatching { prefs?.edit()?.apply(block)?.apply() } }

    private var heldLength by mutableStateOf(MapLinkLength.stored(
        runCatching { prefs?.takeIf { it.contains(MapLinkLength.KEY) }?.getFloat(MapLinkLength.KEY, 1f)?.toDouble() }.getOrNull()))
    val linkLength: Double get() = heldLength
    fun setLinkLength(value: Double) {
        heldLength = MapLinkLength.clamped(value)
        put { putFloat(MapLinkLength.KEY, heldLength.toFloat()) }
    }

    private var heldSeen by mutableStateOf(runCatching { prefs?.getString(MapTouchSeen.KEY, "") }.getOrNull() ?: "")
    fun touchSeen(theme: GraphTheme): Boolean = MapTouchSeen.seen(heldSeen, theme.rawValue)
    fun markTouchSeen(theme: GraphTheme) {
        heldSeen = MapTouchSeen.marking(heldSeen, theme.rawValue)
        put { putString(MapTouchSeen.KEY, heldSeen) }
    }
}

@Composable fun rememberMapPanelSettings(): MapPanelSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) { MapPanelSettings(context) }
}

/** "Standard", or "Longer, 1.4×" / "Shorter, 0.8×" (GraphLinkLength.spoken). */
@Composable fun linkLengthSpoken(value: Double): String = when {
    MapLinkLength.isStandard(value) -> stringResource(R.string.map_link_standard)
    value < MapLinkLength.STANDARD -> stringResource(R.string.map_link_shorter_by, MapLinkLength.shown(value))
    else -> stringResource(R.string.map_link_longer_by, MapLinkLength.shown(value))
}

// MARK: - The peek card

/**
 * A small card raised high at the bottom (GraphPeekCardView): what the chosen body is, its first
 * lines and its links (an idea), or its counts and latest ideas (a folder). Tapping it, swiping it
 * up or Open reads it; a chip selects that idea; More opens the same options a hold does. Fly in,
 * for a folder, shows only where the map can fly in ([MapPeekInput.flyIn]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MapPeekCard(
    content: MapPeekContent, folder: Boolean, words: MapPeekWords,
    open: () -> Unit, flyIn: () -> Unit, chip: (String) -> Unit, option: (MapMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    var swiped by remember { mutableFloatStateOf(0f) }
    WardCardFace(
        modifier.widthIn(max = MapPanel.MAX_WIDTH.dp).fillMaxWidth()
            .clickable(source, indication = null, onClickLabel = content.primary, role = Role.Button, onClick = open)
            // swiped up 40 or more, it opens, as iOS's swipeUp
            .pointerInput(open) {
                detectVerticalDragGestures(onDragStart = { swiped = 0f }, onDragEnd = { if (swiped < -40.dp.toPx()) open() }) { _, dy -> swiped += dy }
            }
            .semantics { contentDescription = content.spoken }
            .testTag(MapPanelTags.PEEK),
        lift = WardLift.high, spacing = WardSpace.s.wardDp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 2, under the scale's 4, so the lines read as one item (WardRow)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(content.title, Modifier.testTag(MapPanelTags.PEEK_TITLE), style = WardType.headline.textStyle(),
                    color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(content.kind, style = WardType.caption.textStyle(), color = wardColor(WardToken.inkSecondary))
            }
            Spacer(Modifier.width(WardSpace.s.wardDp))
            Icon(Icons.Outlined.KeyboardArrowUp, null, tint = wardColor(WardToken.inkSecondary), modifier = Modifier.size(wardGlyph(17.0)))
        }
        content.lines.forEach { line ->
            Text(line, style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (content.chips.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp),
                verticalAlignment = Alignment.CenterVertically) {
                content.chips.forEach { item ->
                    WardChipButton(item.title, { chip(item.id) }, role = Role.Button)
                }
                if (content.moreLinks > 0) {
                    Text(words.moreLinks(content.moreLinks), style = WardType.caption.textStyle(), color = wardColor(WardToken.inkSecondary))
                }
            }
        }
        // they flow, so at the largest text sizes a button takes the next line rather than cut its words
        FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            WardButtonBox(open, Modifier.testTag(MapPanelTags.PEEK_OPEN), kind = WardButtonKind.compact, fills = false) {
                Icon(if (folder) Icons.Outlined.Folder else Icons.Outlined.Description, null, Modifier.size(wardGlyph(17.0)))
                Text(content.primary, maxLines = 1)
            }
            if (words.flyIn in content.secondary) {
                // iOS's "scope"
                WardButtonBox(flyIn, kind = WardButtonKind.quiet, fills = false) {
                    Icon(Icons.Rounded.Adjust, null, Modifier.size(wardGlyph(17.0)))
                    Text(words.flyIn, maxLines = 1)
                }
            }
            if (words.more in content.secondary) {
                var showing by remember { mutableStateOf(false) }
                Box {
                    WardCircleButton(Icons.Rounded.MoreHoriz, words.more, { showing = true })
                    MapOptionsMenu(showing, { showing = false }, content.title, MapPeek.menu(folder = false, home = false).filter { it != MapMenuItem.OPEN }, words) {
                        showing = false
                        option(it)
                    }
                }
            }
        }
    }
}

// MARK: - A body's options

/**
 * A body's options (GraphNodeMenuView's items, GraphPeek.menu): the menus family's DropdownMenu on
 * the page's colour, headed by the body's name, Delete in the danger colour.
 */
@Composable fun MapOptionsMenu(
    expanded: Boolean, onDismiss: () -> Unit, title: String, items: List<MapMenuItem>, words: MapPeekWords,
    pick: (MapMenuItem) -> Unit,
) {
    DropdownMenu(expanded, onDismiss, Modifier.testTag(MapPanelTags.MENU), containerColor = wardColor(WardToken.background)) {
        Text(title, Modifier.padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp).widthIn(max = 230.dp),
            style = WardTextStyle.footnote.textStyle(WardWeight.semibold), color = wardColor(WardToken.inkSecondary),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        items.forEach { item ->
            val ink = wardColor(if (item == MapMenuItem.DELETE) WardToken.danger else WardToken.ink)
            DropdownMenuItem({ Text(words.item(item), color = ink) }, { pick(item) },
                leadingIcon = { Icon(menuGlyph(item), null, tint = ink) })
        }
    }
}

/** GraphNodeMenuView.symbol's glyphs. */
private fun menuGlyph(item: MapMenuItem): ImageVector = when (item) {
    MapMenuItem.OPEN -> Icons.Outlined.Description
    MapMenuItem.RENAME -> Icons.Outlined.Edit
    MapMenuItem.MOVE -> Icons.Outlined.Folder
    MapMenuItem.DELETE -> Icons.Outlined.Delete
}

// MARK: - Link length

/**
 * The Look menu's Link length (GraphLinkLengthBar): a slider from Shorter to Longer over the map,
 * with a step button at each end. While dragging only the value follows; the map is laid out again
 * when the finger lifts, or a step button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapLinkLengthBar(value: Double, onChange: (Double) -> Unit, done: () -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Double?>(null) }
    val shown = dragging ?: value
    val spoken = linkLengthSpoken(shown)
    fun commit(next: Double) {
        val clamped = MapLinkLength.clamped(next)
        if (clamped != value) onChange(clamped)
    }
    WardCardFace(modifier.widthIn(max = MapPanel.MAX_WIDTH.dp).fillMaxWidth().testTag(MapPanelTags.LINK_BAR),
        lift = WardLift.high, spacing = WardSpace.s.wardDp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            Text(stringResource(R.string.map_link_length), Modifier.weight(1f), style = WardTextStyle.subheadline.textStyle(WardWeight.semibold),
                color = wardColor(WardToken.ink))
            Text(spoken, style = WardTextStyle.footnote.textStyle().copy(fontFeatureSettings = "tnum"), color = wardColor(WardToken.inkSecondary))
            WardTextButton(stringResource(R.string.map_link_done), done, Modifier.testTag(MapPanelTags.LINK_DONE), kind = WardButtonKind.compact, fills = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            WardCircleButton(Icons.Rounded.Remove, stringResource(R.string.map_link_shorter), { commit(shown - MapLinkLength.STEP) })
            val colors = SliderDefaults.colors(thumbColor = wardColor(WardToken.primaryInk), activeTrackColor = wardColor(WardToken.primaryInk),
                inactiveTrackColor = wardColor(WardToken.well), activeTickColor = wardColor(WardToken.surface),
                inactiveTickColor = wardColor(WardToken.inkSecondary))
            val source = remember { MutableInteractionSource() }
            val name = stringResource(R.string.map_link_length)
            // the slider's ends, close by it as iOS's minimum and maximum labels; the round buttons say them aloud
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                Text(stringResource(R.string.map_link_shorter), Modifier.clearAndSetSemantics {}, style = WardTextStyle.caption2.textStyle(),
                    color = wardColor(WardToken.inkSecondary), maxLines = 1)
                Slider(value = shown.toFloat(), onValueChange = { dragging = it.toDouble() },
                    modifier = Modifier.weight(1f).testTag(MapPanelTags.LINK_SLIDER).semantics { contentDescription = name; stateDescription = spoken },
                    valueRange = MapLinkLength.SHORTEST.toFloat()..MapLinkLength.LONGEST.toFloat(), steps = MapLinkLength.STEPS,
                    onValueChangeFinished = { dragging?.let { held -> dragging = null; commit(held) } },
                    colors = colors, interactionSource = source,
                    // Material's thumb in a 48 stand, so the slider itself is 48 tall, the least target, not its 44
                    thumb = {
                        Box(Modifier.height(WardControlSize.target.wardDp), contentAlignment = Alignment.Center) {
                            SliderDefaults.Thumb(source, colors = colors)
                        }
                    })
                Text(stringResource(R.string.map_link_longer), Modifier.clearAndSetSemantics {}, style = WardTextStyle.caption2.textStyle(),
                    color = wardColor(WardToken.inkSecondary), maxLines = 1)
            }
            WardCircleButton(Icons.Rounded.Add, stringResource(R.string.map_link_longer), { commit(shown + MapLinkLength.STEP) })
        }
    }
}

// MARK: - The first-run card and the no-folder hint

/**
 * The first-run card (GraphUniverseHint, as it shows once its theme's steps are seen): how to touch
 * the map, in Android's own lines, since a body can't be held to move here. Full list opens the
 * theme's legend; either button puts it away for this theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MapTouchCard(more: () -> Unit, done: () -> Unit, modifier: Modifier = Modifier) {
    WardCardFace(modifier.widthIn(max = MapPanel.HINT_WIDTH.dp).testTag(MapPanelTags.HINT), lift = WardLift.high) {
        Text(stringResource(R.string.map_touch_title), style = WardType.headline.textStyle(), color = wardColor(WardToken.ink))
        Column(Modifier.testTag(MapPanelTags.TOUCH_HINT), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            // iOS's hand.tap, doc.text and a hand held still
            listOf(Icons.Outlined.TouchApp to R.string.map_touch_tap, Icons.Outlined.Description to R.string.map_touch_again,
                Icons.Outlined.PanTool to R.string.map_touch_hold).forEach { (icon, words) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                    Icon(icon, null, tint = wardColor(WardToken.ink), modifier = Modifier.size(wardGlyph(17.0)))
                    Text(stringResource(words), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.ink))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            WardTextButton(stringResource(R.string.map_touch_full_list), more, kind = WardButtonKind.quiet, fills = false)
            WardTextButton(stringResource(R.string.map_touch_got_it), done, kind = WardButtonKind.compact, fills = false)
        }
    }
}

/**
 * A gentle hint over a map with no folders yet (GraphEmptyHint): what a folder becomes in this
 * theme on Android's map, and Add folder, which names one.
 */
@Composable fun MapEmptyHint(theme: GraphTheme, add: () -> Unit, modifier: Modifier = Modifier) {
    val words = stringResource(when (theme) {
        GraphTheme.SPACE -> R.string.map_no_folder_space
        GraphTheme.NEURONS -> R.string.map_no_folder_neurons
        GraphTheme.PERFORMANCE -> R.string.map_no_folder_performance
    })
    WardCardFace(modifier.widthIn(max = MapPanel.EMPTY_HINT_WIDTH.dp).fillMaxWidth().testTag(MapPanelTags.EMPTY_HINT),
        lift = WardLift.high, spacing = WardSpace.s.wardDp) {
        Text(words, Modifier.fillMaxWidth(), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary),
            textAlign = TextAlign.Center)
        WardButtonBox(add, Modifier.align(Alignment.CenterHorizontally).testTag(MapPanelTags.ADD_FOLDER), kind = WardButtonKind.quiet, fills = false) {
            Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(wardGlyph(17.0)))
            Text(stringResource(R.string.map_add_folder), maxLines = 1)
        }
    }
}
