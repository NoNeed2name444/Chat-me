package com.cramdown.sorta.ui.ideas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.DeckFlick
import com.cramdown.sorta.ideas.DeckFolders
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeaFrost
import com.cramdown.sorta.ideas.IdeaStops
import com.cramdown.sorta.ideas.LowTide
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.SortDeck
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// S06a: To sort as a deck of cards (plan idea 2), "You were saying…" (idea 9) and the low tide
// (idea 6), as iOS's Ideas/IdeasDeck.swift. The rules are in ideas/SortDeck.kt.

/** The clock frost and the tide read: the device's, or the tests' fixed time (MainActivity's [MainActivity.NOW] in a debuggable build). */
object IdeaClock {
    @Volatile var fixed: Long? = null
    fun now(): Long = fixed ?: System.currentTimeMillis()
}

/** What happens once per launch of the app (S06a): the offer of "You were saying…", and the low tide. */
object IdeasSession {
    /** The offer was dismissed or taken in this launch. */
    var offerDone by mutableStateOf(false)
    /** The tide in this launch: its ideas still to see, emptied or closed. */
    var tide by mutableStateOf<List<String>>(emptyList())
    private var tideAsked = false

    /** Asks once per launch whether the tide is due; it brings the frostiest ideas, and its time is kept. */
    fun startTide(settings: IdeasSettings, book: IdeaBook) {
        if (tideAsked) return
        tideAsked = true
        val now = IdeaClock.now()
        val last = settings.lastTide
        if (last == null) { settings.setLastTide(now); return }
        if (!LowTide.due(last, now)) return
        tide = LowTide.pick(book.notes.map { LowTide.Idea(it.id, it.openedAt, it.createdAt) }, now)
        settings.setLastTide(now)
    }

    /** A fresh launch, for the tests that run many in one process. */
    fun reset() { offerDone = false; tide = emptyList(); tideAsked = false }
}

/** The deck's order (SortDeck) as Compose state: [bump] moves whenever Later or Undo reorders it. */
class DeckState {
    val deck = SortDeck<String>()
    var bump by mutableIntStateOf(0); private set
    fun later(id: String) { deck.later(id); bump++ }
    fun putBack(id: String) { deck.putBack(id); bump++ }
}

/** The test tags, iOS's identifiers. */
object DeckTags {
    const val TOGGLE = "deck-toggle"
    const val CARD = "deck-card"
    const val TARGET = "deck-target"
    const val LATER = "deck-later"
    const val FOLDERS = "deck-folders"
    const val OFFER = "resume-offer"
    const val TIDE = "low-tide"
}

/** The frost's look (S06a A6): a cool wash over a card, deeper by step. The kit has no cool tone, so it is named here, as iOS's. */
object IdeaFrostLook {
    /** The ice by day: pale, over bone. */
    val dayIce = Color(0xFFD6E8F5)
    /** The ice by night: a deep blue that cools a card without lifting it, so a forgotten idea never outshines the rest, and every ink on it keeps its contrast. */
    val nightIce = Color(0xFF1A3048)
    /** The wash at each level, 0 to 3, by day and by night. */
    val dayOpacity = floatArrayOf(0f, 0.20f, 0.40f, 0.60f)
    val nightOpacity = floatArrayOf(0f, 0.30f, 0.50f, 0.70f)
    fun ice(dark: Boolean): Color = if (dark) nightIce else dayIce
    fun opacity(dark: Boolean): FloatArray = if (dark) nightOpacity else dayOpacity
}

/** The deck's measures the kit has no entry for. */
internal object DeckLayout {
    /** The card, at most this wide. */
    const val cardWidth = 360.0
    /** The card, at least this tall. */
    const val cardHeight = 168.0
    /** The Later well between the edge columns. */
    const val wellWidth = 96.0
    /** A target and the gap under it. */
    val row: Double = WardControlSize.target + WardSpace.s
    /** A target in the folders pane, at least this wide. */
    const val paneTarget = 200.0
    const val phoneCap = 8
    const val paneCap = 12
}

/** A dotted edge round a tile (wardRowTile's shape, [inset] in from the cell), for an idea left mid-sentence. */
internal fun Modifier.unfinishedEdge(marked: Boolean, inset: Dp = WardSpace.s.wardDp): Modifier = if (!marked) this else composed {
    val ink = wardColor(WardToken.inkSecondary)
    val radius = WardRadius.field.wardDp
    drawWithContent {
        drawContent()
        val stroke = 1.5.dp.toPx()
        val i = inset.toPx() + stroke / 2
        drawRoundRect(ink, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(radius.toPx()),
            style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()))))
    }
}

/** List or cards, at To sort's title row's end, beside More. */
@Composable internal fun DeckToggle(on: Boolean, set: (Boolean) -> Unit) {
    val words = stringResource(if (on) R.string.deck_show_list else R.string.deck_show_cards)
    WardCircleButton(if (on) Icons.AutoMirrored.Rounded.List else Icons.Outlined.ViewAgenda, words, { set(!on) },
        Modifier.semantics { testTag = DeckTags.TOGGLE })
}

/** "You were saying…": the marked idea changed last, offered at To sort's top until it is taken or put away (once per launch). */
@Composable internal fun ResumeOffer(note: Note, open: () -> Unit, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    val line = ideaLine(note)
    val carryOn = stringResource(R.string.offer_carry_on_spoken, line)
    val stop = note.stoppedAt?.coerceIn(0, note.body.length) ?: note.body.length
    // the last words before the caret, as the person left them
    val before = note.body.substring(0, stop).trimEnd().lines().lastOrNull { it.isNotBlank() }?.trim()?.takeLast(80)
    Row(modifier.fillMaxWidth().wardRowTile().semantics { testTag = DeckTags.OFFER },
        horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
            Text(stringResource(R.string.offer_label), Modifier.semantics { heading() }, style = WardTextStyle.footnote.textStyle(WardWeight.semibold),
                color = wardColor(WardToken.inkSecondary))
            Text(line, style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (before != null) Text("…$before", style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary),
                maxLines = 2, overflow = TextOverflow.StartEllipsis)
            WardTextButton(stringResource(R.string.offer_carry_on), open, Modifier.semantics {
                contentDescription = carryOn
            }.padding(top = WardSpace.xs.wardDp), kind = WardButtonKind.compact, fills = false)
        }
        WardCircleButton(Icons.Rounded.Close, stringResource(R.string.offer_not_now), dismiss)
    }
}

/** The tide's head: "Low tide" as the title, the line under it that says what it holds, and its close at the end. */
@Composable internal fun TideTitle(close: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = WardSpace.s.wardDp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
            Text(stringResource(R.string.tide_title), Modifier.semantics { heading(); testTag = DeckTags.TIDE },
                style = WardTextStyle.title.textStyle(WardWeight.bold), color = wardColor(WardToken.ink))
            Text(stringResource(R.string.tide_line), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
        }
        WardCircleButton(Icons.Rounded.Close, stringResource(R.string.tide_close), close)
    }
}

/** What the deck's card offers: To sort's (file, Later, Choose folder), or the tide's (Keep, Link to…, Let go). */
internal sealed class DeckActions {
    class Sort(val file: (String, String) -> Unit, val later: (String) -> Unit, val choose: (String) -> Unit) : DeckActions()
    class Tide(val keep: (String) -> Unit, val link: (String) -> Unit, val letGo: (String) -> Unit) : DeckActions()
}

/**
 * The deck: the top card of [order] low in the area, under the thumb, and the Later well above it.
 * With [paneTargets] null (a phone held upright), the most-used folders that fit stand in two
 * columns round the well, at its foot, a thumb's reach above the card; else they are the folders
 * pane's, beside it, only their frames come here, in [frames], which every target keeps in the
 * root's coordinates, and the card and its well stand in the middle. [hovered]: the target a
 * dragged card is over, wherever it stands. Where there is no room above the card (a phone on its
 * side, the keyboard up), the card stands alone: its buttons and its spoken actions still do everything.
 */
@Composable internal fun DeckArea(book: IdeaBook, version: Int, order: List<String>, actions: DeckActions, paneTargets: List<NoteFolder>?,
                                  frames: SnapshotStateMap<String, Rect>, hovered: String?, hover: (String?) -> Unit, bottom: Dp,
                                  modifier: Modifier = Modifier) {
    val top = order.firstOrNull()?.let { book.note(it) }
    val sort = actions as? DeckActions.Sort
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = WardSpace.gutter.wardDp).padding(bottom = bottom)) {
        // the card's zone: half the area at most, but never less than the card and the cards under it
        val cardZone = max(min(maxHeight.value * 0.5f, (DeckLayout.cardHeight + WardSpace.l * 3).toFloat()),
            (DeckLayout.cardHeight + WardSpace.s * 2).toFloat()).dp
        val above = maxHeight - cardZone - WardSpace.m.wardDp
        val roomy = above >= (WardControlSize.target * 2).dp
        val centred = sort != null && paneTargets != null
        val edges = paneTargets == null && sort != null && roomy
        val slots = if (edges) DeckFolders.slots(above.value.toDouble(), DeckLayout.row, 2, DeckLayout.phoneCap) else 0
        val targets = paneTargets ?: remember(book, version, slots) { deckTargets(book, slots) }
        if (top == null) {
            Text(stringResource(R.string.sort_all_sorted), Modifier.padding(horizontal = WardSpace.s.wardDp),
                style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            return@BoxWithConstraints
        }
        val keep = (actions as? DeckActions.Tide)?.keep ?: sort?.later
        val wellWords = if (sort == null) R.string.tide_keep else R.string.deck_later
        val behind = min(order.size - 1, 2)
        val card: @Composable () -> Unit = {
            key(top.id) {
                // the cards under it show below it, so they have their room
                Box(Modifier.padding(bottom = (WardSpace.s * behind).dp)) {
                    DeckCard(book, version, top, behind, actions, if (sort != null) targets else emptyList(), frames, density.density, hover)
                }
            }
        }
        if (centred) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WardSpace.l.wardDp, Alignment.CenterVertically)) {
                if (roomy && keep != null) LaterWell(Modifier.width(DeckLayout.wellWidth.dp), wellWords) { keep(top.id) }
                card()
            }
            return@BoxWithConstraints
        }
        val sides = (maxWidth - DeckLayout.wellWidth.dp - WardSpace.m.wardDp * 2) / 2
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            if (edges && sort != null && targets.isNotEmpty()) {
                // the edge columns, leading and trailing, at the foot of the room above the card; the Later well between them at its top
                val (leading, trailing) = targets.withIndex().partition { it.index % 2 == 0 }
                Row(Modifier.fillMaxWidth().height(above), horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                    Column(Modifier.width(sides).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp, Alignment.Bottom)) {
                        leading.forEach { (_, f) -> DeckTarget(f, hovered == f.id, frames) { sort.file(top.id, f.id) } }
                    }
                    LaterWell(Modifier.width(DeckLayout.wellWidth.dp)) { sort.later(top.id) }
                    Column(Modifier.width(sides).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp, Alignment.Bottom)) {
                        trailing.forEach { (_, f) -> DeckTarget(f, hovered == f.id, frames) { sort.file(top.id, f.id) } }
                    }
                }
            } else if (roomy) {
                Box(Modifier.fillMaxWidth().height(above), contentAlignment = Alignment.TopCenter) {
                    if (keep != null) LaterWell(Modifier.width(DeckLayout.wellWidth.dp), wellWords) { keep(top.id) }
                }
            }
            Spacer(Modifier.weight(1f))
            card()
        }
    }
}

/** The well a card is flicked up into: Later (Keep in the tide); a tap does the same. */
@Composable private fun LaterWell(modifier: Modifier, words: Int = R.string.deck_later, onClick: () -> Unit) {
    val ink = wardColor(WardToken.inkSecondary)
    val label = stringResource(words)
    Column(modifier.heightIn(min = WardControlSize.target.wardDp * 2)
        .drawWithContent {
            drawContent()
            val stroke = 1.5.dp.toPx()
            drawRoundRect(ink, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(WardRadius.card.dp.toPx()),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics(mergeDescendants = true) { testTag = DeckTags.LATER }
        .padding(WardSpace.s.wardDp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp, Alignment.CenterVertically)) {
        Icon(Icons.Rounded.ArrowUpward, null, tint = ink, modifier = Modifier.size(wardGlyph(20.0)))
        Text(label, style = WardTextStyle.footnote.textStyle(), color = ink, maxLines = 2)
    }
}

/** A folder to flick to, and to tap: its glyph and name, pressed in while a card is over it. Its frame goes to [frames]. */
@Composable internal fun DeckTarget(folder: NoteFolder, over: Boolean, frames: SnapshotStateMap<String, Rect>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(WardRadius.field.wardDp)
    val spoken = stringResource(R.string.deck_file_in, folder.name)
    DisposableEffect(folder.id) { onDispose { frames.remove(folder.id) } }
    Row(modifier.fillMaxWidth().minimumInteractiveComponentSize().heightIn(min = WardControlSize.target.wardDp)
        .onGloballyPositioned { frames[folder.id] = it.boundsInRoot() }
        .capPop(held || over)
        .wardPressFace(shape, WardLift.low, wardDepth(held || over))
        .clickable(source, indication = null, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = spoken; testTag = DeckTags.TARGET }
        .padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        Icon(Icons.Outlined.Folder, null, tint = wardColor(WardToken.accent), modifier = Modifier.size(wardGlyph(18.0)))
        Text(folder.name, style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The card under the thumb: its first line, and its buttons at its foot. A drag carries it; let go,
 * it files where the finger was flung (DeckFlick), goes to Later (or is kept, in the tide), or
 * springs back. Filed or put back, it flies off first, unless animations are off. TalkBack reads its
 * words, then offers each folder's "File in …", Later and Choose folder (the tide's Keep, Link to…
 * and Let go). [behind]: how many cards show under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DeckCard(book: IdeaBook, @Suppress("UNUSED_PARAMETER") version: Int, note: Note, behind: Int, actions: DeckActions,
                                 targets: List<NoteFolder>, frames: SnapshotStateMap<String, Rect>, density: Float, hover: (String?) -> Unit) {
    val line = ideaLine(note)
    val marked = note.stoppedAt != null
    val reduce = wardReduceMotion()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fade = remember { Animatable(1f) }
    // the card's corner in the root, and where the finger took hold of it there
    var origin by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var leaving by remember { mutableStateOf(false) }
    val unfinished = stringResource(R.string.deck_unfinished)
    val laterWords = stringResource(R.string.deck_later)
    val chooseWords = stringResource(R.string.row_choose_folder)
    val keepWords = stringResource(R.string.tide_keep)
    val linkWords = stringResource(R.string.editor_link_to)
    val letGoWords = stringResource(R.string.tide_let_go)
    val danger = wardColor(WardToken.danger)
    val fileIn = targets.map { it to stringResource(R.string.deck_file_in, it.name) }
    val spokenActions = when (actions) {
        is DeckActions.Sort -> fileIn.map { (f, words) -> CustomAccessibilityAction(words) { actions.file(note.id, f.id); true } } +
            listOf(CustomAccessibilityAction(laterWords) { actions.later(note.id); true },
                CustomAccessibilityAction(chooseWords) { actions.choose(note.id); true })
        is DeckActions.Tide -> listOf(CustomAccessibilityAction(keepWords) { actions.keep(note.id); true },
            CustomAccessibilityAction(linkWords) { actions.link(note.id); true },
            CustomAccessibilityAction(letGoWords) { actions.letGo(note.id); true })
    }
    // where the card goes, judged from the finger: from where it took hold to [at] past it
    fun landing(at: Offset): DeckFlick.Outcome<String> {
        val ts = if (actions is DeckActions.Sort) targets.mapNotNull { f -> frames[f.id]?.let { r ->
            DeckFlick.Target(f.id, (r.left / density).toDouble(), (r.top / density).toDouble(), (r.right / density).toDouble(), (r.bottom / density).toDouble()) } }
            else emptyList()
        return DeckFlick.outcome((grab.x / density).toDouble(), (grab.y / density).toDouble(),
            ((grab.x + at.x) / density).toDouble(), ((grab.y + at.y) / density).toDouble(), ts)
    }
    fun settle(outcome: DeckFlick.Outcome<String>, at: Offset) {
        fun act() {
            when (outcome) {
                is DeckFlick.Outcome.Folder -> (actions as? DeckActions.Sort)?.file?.invoke(note.id, outcome.id)
                DeckFlick.Outcome.Later -> when (actions) { is DeckActions.Sort -> actions.later(note.id); is DeckActions.Tide -> actions.keep(note.id) }
                DeckFlick.Outcome.Back -> {}
            }
        }
        scope.launch {
            when {
                outcome == DeckFlick.Outcome.Back ->
                    if (reduce) offset.snapTo(Offset.Zero) else offset.animateTo(Offset.Zero, wardSpring(WardMotion.travel, reduce))
                reduce -> { offset.snapTo(Offset.Zero); act() }
                else -> {
                    // off toward where it was flung, or up past the top for Later, fading as it goes
                    val place = if (outcome == DeckFlick.Outcome.Later) Offset(at.x, min(at.y, -(DeckLayout.cardHeight * 2 * density).toFloat())) else at
                    leaving = true
                    launch { fade.animateTo(0f, wardEaseOut(WardMotion.shortest, reduce)) }
                    offset.animateTo(place, wardEaseOut(WardMotion.shortest, reduce))
                    act()
                    // the same card still on top (the deck's only one, put back): back in its place
                    offset.snapTo(Offset.Zero); fade.snapTo(1f); leaving = false
                }
            }
        }
    }
    Box(Modifier.widthIn(max = DeckLayout.cardWidth.dp).fillMaxWidth()
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(note.id) {
            val tracker = VelocityTracker()
            detectDragGestures(
                onDragStart = { tracker.resetTracking(); grab = origin + it },
                onDragEnd = {
                    val v = tracker.calculateVelocity()
                    val (x, y) = DeckFlick.landing(offset.value.x.toDouble(), offset.value.y.toDouble(), v.x.toDouble(), v.y.toDouble())
                    val at = Offset(x.toFloat(), y.toFloat())
                    hover(null)
                    if (!leaving) settle(landing(at), at)
                },
                onDragCancel = { hover(null); scope.launch { offset.snapTo(Offset.Zero) } },
            ) { change, amount ->
                change.consume()
                if (leaving) return@detectDragGestures
                tracker.addPosition(change.uptimeMillis, change.position)
                val now = offset.value + amount
                scope.launch { offset.snapTo(now) }
                hover((landing(now) as? DeckFlick.Outcome.Folder)?.id)
            }
        }) {
        // the cards under it, a step down each, the top card's size: the deck's depth, not its count
        for (k in behind downTo 1) Box(Modifier.matchParentSize().padding(horizontal = (WardSpace.s * k).dp)
            .offset(y = (WardSpace.s * k).dp).wardRaised(WardRadius.card, WardLift.low, fill = WardToken.raised))
        Column(Modifier.fillMaxWidth().heightIn(min = DeckLayout.cardHeight.dp)
            .graphicsLayer {
                translationX = offset.value.x; translationY = offset.value.y
                rotationZ = if (reduce) 0f else (offset.value.x / density / 40f).coerceIn(-8f, 8f)
                alpha = fade.value
                val shrink = 0.6f + 0.4f * fade.value
                scaleX = shrink; scaleY = shrink
            }
            .wardRaised(WardRadius.card, WardLift.high, fill = WardToken.raised)
            .unfinishedEdge(marked, inset = 0.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = line
                if (marked) stateDescription = unfinished
                customActions = spokenActions
                testTag = DeckTags.CARD
            }
            .padding(WardSpace.l.wardDp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Text(line, Modifier.padding(bottom = WardSpace.m.wardDp), style = WardType.body.textStyle(), color = wardColor(WardToken.ink),
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            // the buttons at the card's foot, the room above them where a thumb grabs
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
                when (actions) {
                    is DeckActions.Sort -> {
                        WardTextButton(laterWords, { actions.later(note.id) }, kind = WardButtonKind.compact, fills = false)
                        WardTextButton(chooseWords, { actions.choose(note.id) }, kind = WardButtonKind.compact, fills = false)
                    }
                    is DeckActions.Tide -> {
                        WardTextButton(keepWords, { actions.keep(note.id) }, kind = WardButtonKind.compact, fills = false)
                        WardTextButton(linkWords, { actions.link(note.id) }, kind = WardButtonKind.compact, fills = false)
                        // compact as its neighbours, its words in the danger red, as the rows' Delete; Undo brings the idea back
                        WardButtonBox({ actions.letGo(note.id) }, kind = WardButtonKind.compact, fills = false) {
                            Text(letGoWords, textAlign = TextAlign.Center, color = danger)
                        }
                    }
                }
            }
        }
    }
}

/** The folders pane (S06a A2): beside the deck on two panes, the most-used folders as targets in a grid, in the pane's middle, as the card is in its own; each presses in while a card is over it ([hovered]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DeckFolderPane(targets: List<NoteFolder>, frames: SnapshotStateMap<String, Rect>, hovered: String?, file: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = WardSpace.gutter.wardDp).padding(vertical = WardSpace.l.wardDp).semantics { testTag = DeckTags.FOLDERS },
        verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp, Alignment.CenterVertically)) {
        WardSectionLabel(stringResource(R.string.sort_folders))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            targets.forEach { f -> DeckTarget(f, hovered == f.id, frames, Modifier.width(DeckLayout.paneTarget.dp)) { file(f.id) } }
        }
    }
}
/** The most-used folders, as many as [slots] (DeckFolders). */
internal fun deckTargets(book: IdeaBook, slots: Int): List<NoteFolder> {
    val counts = book.notes.groupingBy { it.folderId }.eachCount()
    val ids = DeckFolders.pick(book.folders.map { DeckFolders.Use(it.id, it.name, counts[it.id] ?: 0) }, slots)
    return ids.mapNotNull { book.folder(it) }
}

/** How many targets fit in the folders pane, [width] by [height]: a grid of [DeckLayout.paneTarget] wide targets under its label. */
internal fun paneSlots(width: Double, height: Double): Int =
    DeckFolders.slots(height - WardControlSize.target * 2, DeckLayout.row, max(1, (width / (DeckLayout.paneTarget + WardSpace.s)).toInt()), DeckLayout.paneCap)

/** The frost on an idea now (IdeaFrost), on the clock the tests may fix. */
internal fun frostOf(note: Note): Int = IdeaFrost.level(note.openedAt, note.createdAt, IdeaClock.now())

/** The idea "You were saying…" offers now, or null. */
internal fun offerOf(book: IdeaBook): Note? =
    IdeaStops.offer(book.notes.map { IdeaStops.Mark(it.id, it.stoppedAt, it.updatedAt) })?.let { book.note(it) }

/** The frost's snowflake, beside a board card's kind glyph. */
@Composable internal fun FrostGlyph(level: Int) {
    if (level > 0) Icon(Icons.Rounded.AcUnit, null, tint = wardColor(WardToken.inkSecondary), modifier = Modifier.size(wardGlyph(12.0)))
}
