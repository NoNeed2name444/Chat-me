package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.IdeaBoardFit
import com.cramdown.sorta.ideas.IdeaBoardPeek
import com.cramdown.sorta.ideas.IdeaBoardSpread
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeaLinkShape
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlin.math.max

// A port of IdeaBoardView (Features/Notes, fed0105): every note a card on an open board,
// joined by its links. Drag the board to pan, pinch to zoom (0.3 to 2.5). Tap a card to
// open it; hold one, or tap Connect, to start a connection and tap another to join or part
// them. Drag a card to move it, saved where it lands. Double-tap anywhere to put an idea
// there. Board units are points, drawn here as dp.

private const val CARD_WIDTH = IdeaBoardDrop.CARD_WIDTH
private const val CARD_HEIGHT = IdeaBoardDrop.CARD_HEIGHT

/** The board's test tags (S05m): every card, its kind's symbol (`board-kind-idea`, `board-kind-page`) and the whole title over a card. */
const val BOARD_CARD_TAG = "board-card"
const val BOARD_KIND_TAG = "board-kind-"
const val BOARD_PEEK_TAG = "board-peek"
/** S06b: a card outside the lit day (iOS IdeaBoardView.unlit). */
const val UNLIT = 0.3f

/** The title's style on a card and over it. */
@Composable private fun boardTitleStyle() = WardTextStyle.subheadline.textStyle().copy(fontWeight = FontWeight.Medium)

/** [lit] (S06b): a lit day's ideas, the rest's cards dimmed to [UNLIT]; null for none. */
@Composable fun IdeaBoard(book: IdeaBook, settings: IdeasSettings, hidden: Dp, open: (String) -> Unit, top: Dp = 0.dp, version: Int = 0,
                         lit: Set<String>? = null) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableDoubleStateOf(1.0) }
    var panX by remember { mutableDoubleStateOf(0.0) }
    var panY by remember { mutableDoubleStateOf(0.0) }
    var moved by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableDoubleStateOf(0.0) }
    var dragY by remember { mutableDoubleStateOf(0.0) }
    var adding by remember { mutableStateOf<BoardPoint?>(null) }
    // the card whose whole title shows over it: a pointer over it, or a finger resting on it (S05m item 5)
    var peek by remember { mutableStateOf<String?>(null) }
    var peekHeight by remember { mutableDoubleStateOf(0.0) }
    // [version] is the book's (IdeasModel.version): the book changes in place, so a change reaches the
    // board by it, and its cards (a folder renamed beside them) with it
    val notes = book.notes
    val edges = remember(notes, version) { book.allEdges() }
    val style = settings.lineStyle
    val hiddenDp = hidden.value.toDouble()
    // a board of one kind keeps each card's symbol but drops the kind's word (S05m item 4)
    val oneKind = notes.map { it.noteKind }.toSet().size <= 1
    // a title's line is at most 20 letters of its font wide; every card is as wide as that holds,
    // and never under 150 (S05m item 5)
    val measurer = rememberTextMeasurer()
    val titleStyle = boardTitleStyle()
    val titleWidth = with(density) { measurer.measure(IdeaBoardDrop.TITLE_SAMPLE, titleStyle, maxLines = 1).size.width.toDp().value.toDouble() }
    val cardWidth = max(CARD_WIDTH, kotlin.math.ceil(titleWidth) + 2 * WardSpace.m)
    // the tool cluster's box: three targets and their spacing, in the gutter (IdeaToolCluster)
    val tools = IdeaBoardFit.Tools(WardControlSize.target + 2 * WardSpace.gutter,
        3 * WardControlSize.target + 2 * WardSpace.l + 2 * WardSpace.gutter, currentWindowShape().isBroad)

    val widthDp = size.width / density.density.toDouble()
    val heightDp = size.height / density.density.toDouble()
    val midY = (heightDp - hiddenDp) / 2

    // each card's drawn size as it lays out, in px; the places the cards are drawn at, worked out
    // again only when a stored place, a card or a size changes (S05c)
    val sizes = remember { mutableStateMapOf<String, IntSize>() }
    val cards = notes.map { note ->
        val seen = sizes[note.id]
        val w = if (seen == null) cardWidth else seen.width / density.density.toDouble()
        val h = if (seen == null) CARD_HEIGHT else seen.height / density.density.toDouble()
        IdeaBoardSpread.Card(note.id, BoardPoint(note.boardX, note.boardY), max(w, cardWidth), max(h, CARD_HEIGHT))
    }
    val drawn = remember(cards) { IdeaBoardSpread.places(cards) }
    // the drag's end runs in a gesture started earlier: it reads these as they are now
    val cardsNow by rememberUpdatedState(cards)
    val drawnNow by rememberUpdatedState(drawn)

    fun drawnAt(note: Note): BoardPoint = drawn[note.id] ?: BoardPoint(note.boardX, note.boardY)
    fun position(note: Note): BoardPoint {
        val at = drawnAt(note)
        return if (note.id == dragging) BoardPoint(at.x + dragX, at.y + dragY) else at
    }
    fun onScreen(p: BoardPoint) = BoardPoint(widthDp / 2 + panX + p.x * zoom, midY + panY + p.y * zoom)
    fun onBoard(x: Double, y: Double) = BoardPoint((x - widthDp / 2 - panX) / zoom, (y - midY - panY) / zoom)

    fun fitAll() {
        if (size == IntSize.Zero) return
        // the cards as drawn: their spread centres and their own sizes, which at the largest
        // text sizes are far taller than the least card; the whole room, 16 in, below the top
        // strip ([top]), with no card under the tools; never so far out that the shortest card is
        // shorter than a target (S05m item 1)
        val camera = IdeaBoardFit.firstView(notes.map { drawnAt(it) }, cards.map { IdeaBoardFit.Size(it.width, it.height) },
            IdeaBoardFit.Size(cardWidth, CARD_HEIGHT), IdeaBoardFit.Size(widthDp, heightDp), hiddenDp,
            top.value.toDouble(), tools, WardControlSize.target)
        zoom = camera.zoom; panX = camera.panX; panY = camera.panY
    }
    // the first view frames every card, until the board is moved by hand; again as the drawn
    // places change, since sizes arrive after the first layout
    LaunchedEffect(size, notes.size, hidden, top, drawn) { if (!moved) fitAll() }

    fun tapCard(id: String) {
        val from = source
        when {
            from == null && connecting -> source = id
            from == null -> open(id)
            from == id -> { source = null }
            else -> {
                if (book.isLinked(from, id)) book.unlink(from, id) else book.link(from, id)
                source = null
            }
        }
    }

    // clipped, as iOS's board is: a card moved past the edge goes under it, not over the bars
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }) {
        val ink = wardColor(WardToken.inkSecondary)
        val chosenInk = wardColor(WardToken.primaryInk)
        // the board itself: pan, pinch, double-tap
        Canvas(Modifier.fillMaxSize()
            .pointerInput(size, hidden) {
                detectTransformGestures { centroid, pan, gesture, _ ->
                    moved = true; peek = null
                    val d = density.density.toDouble()
                    val next = (zoom * gesture).coerceIn(0.3, 2.5)
                    val cx = centroid.x / d; val cy = centroid.y / d
                    // keep the point under the fingers where it is
                    val bx = (cx - widthDp / 2 - panX) / zoom; val by = (cy - midY - panY) / zoom
                    zoom = next
                    panX = cx - widthDp / 2 - bx * zoom + pan.x / d
                    panY = cy - midY - by * zoom + pan.y / d
                }
            }
            .pointerInput(size, hidden) {
                detectTapGestures(onDoubleTap = { at ->
                    val d = density.density.toDouble()
                    adding = onBoard(at.x / d, at.y / d)
                }, onTap = { if (source != null && !connecting) source = null })
            }) {
            val d = density.density
            val byId = notes.associateBy { it.id }
            val keys = edges.map { (a, b) -> "$a|$b" }
            val lanes = IdeaLinkShape.lanes(keys)
            edges.forEachIndexed { k, (a, b) ->
                val na = byId[a] ?: return@forEachIndexed
                val nb = byId[b] ?: return@forEachIndexed
                val p = onScreen(position(na)); val q = onScreen(position(nb))
                val (lane, count) = lanes[k]
                fun px(v: Double) = (v * d).toFloat()
                // as iOS's IdeaBoardView.line: Straight one segment, Curved one quadratic
                val path = Path()
                if (style.isStraight) {
                    val (from, to) = IdeaLinkShape.straightEnds(p, q, lane, count)
                    path.moveTo(px(from.x), px(from.y))
                    path.lineTo(px(to.x), px(to.y))
                } else {
                    val curve = IdeaLinkShape.curve(p, q, lane, count)
                    path.moveTo(px(curve.start.x), px(curve.start.y))
                    path.quadraticTo(px(curve.control.x), px(curve.control.y), px(curve.end.x), px(curve.end.y))
                }
                drawPath(path, ink, style = Stroke(width = max(1.0, 1.5 * zoom).toFloat() * d, cap = StrokeCap.Round))
            }
        }

        // the cards, each centred on its spot and scaled with the board
        Layout(content = {
            notes.forEach { note ->
                key(note.id) {
                    BoardCard(book, version, note, chosen = note.id == source, lifted = note.id == dragging, zoom = zoom, chosenInk = chosenInk,
                        width = cardWidth, oneKind = oneKind,
                        modifier = Modifier.layoutId(note.id)
                            // S06b: outside a lit day, dimmed as iOS's (IdeaBoardView.unlit)
                            .alpha(if (lit == null || note.id in lit) 1f else UNLIT)
                            // its size before the board's zoom (BoardCard's graphicsLayer), in px
                            .onSizeChanged { sizes[note.id] = it }
                            // the whole title: shown while a mouse or stylus is over the card, or a finger
                            // rests on it; gone as it leaves or lifts (read before the gestures below)
                            .pointerInput(note.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val touch = event.changes.firstOrNull()?.type == PointerType.Touch
                                        when (event.type) {
                                            PointerEventType.Enter -> if (!touch) peek = note.id
                                            PointerEventType.Exit -> if (!touch && peek == note.id) peek = null
                                            PointerEventType.Press -> if (touch) peek = note.id
                                            PointerEventType.Release -> if (touch && peek == note.id) peek = null
                                        }
                                    }
                                }
                            }
                            .pointerInput(note.id) {
                                detectTapGestures(onTap = { tapCard(note.id) }, onLongPress = { peek = null; source = note.id })
                            }
                            .pointerInput(note.id) {
                                detectDragGestures(
                                    onDragStart = { peek = null; dragging = note.id; dragX = 0.0; dragY = 0.0 },
                                    onDragEnd = {
                                        val id = dragging
                                        // let go on another card, it settles in the nearest free place, among the
                                        // cards as they are drawn
                                        if (id != null) book.note(id)?.let {
                                            val own = cardsNow.firstOrNull { c -> c.id == id }
                                            val from = drawnNow[id] ?: BoardPoint(it.boardX, it.boardY)
                                            val others = cardsNow.filter { c -> c.id != id }.map { c -> c.copy(centre = drawnNow[c.id] ?: c.centre) }
                                            val at = IdeaBoardSpread.settle(BoardPoint(from.x + dragX, from.y + dragY),
                                                own?.width ?: CARD_WIDTH, own?.height ?: CARD_HEIGHT, others)
                                            book.setPosition(id, at.x, at.y)
                                        }
                                        dragging = null; dragX = 0.0; dragY = 0.0
                                    },
                                    onDragCancel = { dragging = null; dragX = 0.0; dragY = 0.0 },
                                ) { change, amount ->
                                    change.consume()
                                    dragX += amount.x / density.density / zoom
                                    dragY += amount.y / density.density / zoom
                                }
                            }
                            .semantics {
                                role = Role.Button
                                selected = note.id == source
                                // what this tap does, as tapCard decides it
                                val from = source
                                val action = when {
                                    from == null -> if (connecting) "Start a connection" else "Open"
                                    from == note.id -> "Cancel"
                                    book.isLinked(from, note.id) -> "Disconnect"
                                    else -> "Connect"
                                }
                                onClick(action) { tapCard(note.id); true }
                                onLongClick("Start a connection") { source = note.id; true }
                            })
                }
            }
        }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it to it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { (m, placeable) ->
                    val note = book.note(m.layoutId as? String) ?: return@forEach
                    val c = onScreen(position(note))
                    placeable.place((c.x * density.density - placeable.width / 2.0).toInt(), (c.y * density.density - placeable.height / 2.0).toInt())
                }
            }
        }

        // the whole title over its card, above every card, clear of the tools; it takes no touch
        peek?.let { id -> book.note(id) }?.let { note ->
            val at = onScreen(position(note))
            val own = cards.firstOrNull { it.id == note.id }
            val w = own?.width ?: cardWidth; val h = own?.height ?: CARD_HEIGHT
            val peekWidth = IdeaBoardPeek.width(w * zoom, widthDp)
            val corner = IdeaBoardPeek.place(at.x - w * zoom / 2, at.y - h * zoom / 2, peekWidth, peekHeight,
                IdeaBoardFit.Size(widthDp, heightDp), hiddenDp, top.value.toDouble(), tools)
            Text(note.title.ifEmpty { "Untitled" }, style = titleStyle, color = wardColor(WardToken.ink),
                modifier = Modifier.offset(corner.x.dp, corner.y.dp).width(peekWidth.dp)
                    .onSizeChanged { peekHeight = it.height / density.density.toDouble() }
                    .semantics { testTag = BOARD_PEEK_TAG }
                    .wardRaised(WardRadius.card, WardLift.high, fill = WardToken.raised)
                    .padding(WardSpace.m.wardDp))
        }

        if (notes.isEmpty()) {
            // the kit's empty state's gap and message type (WardEmptyState); iOS's outlined hand.tap at Title 2
            Column(Modifier.align(Alignment.Center).padding(bottom = hidden).padding(WardSpace.xl.wardDp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                Icon(Icons.Outlined.TouchApp, null, tint = wardColor(WardToken.inkSecondary), modifier = Modifier.size(wardGlyph(26.0)))
                Text("Double-tap anywhere to put an idea there.", style = WardTextStyle.subheadline.textStyle(),
                    color = wardColor(WardToken.inkSecondary), textAlign = TextAlign.Center)
            }
        }

        if (connecting || source != null) {
            // one height with Cancel or without it, so the capsule doesn't jump when a card is picked;
            // Cancel drops the pick, and the Connect tool ends connecting (as on iOS)
            Row(Modifier.align(Alignment.TopCenter).padding(top = WardSpace.m.wardDp).padding(horizontal = WardSpace.gutter.wardDp)
                .wardRelief(RoundedCornerShape(50), WardLift.high, wardColor(WardToken.raised))
                .padding(start = WardSpace.gutter.wardDp, end = (if (source == null) WardSpace.gutter else WardSpace.xs).wardDp,
                    top = WardSpace.xs.wardDp, bottom = WardSpace.xs.wardDp)
                .heightIn(min = WardControlSize.target.wardDp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                Icon(Icons.Outlined.Route, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(18.0)))
                Text(if (source == null) "Tap a card to start a connection." else "Tap another card to connect or disconnect them.",
                    Modifier.weight(1f, fill = false), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.ink))
                if (source != null) WardTextButton("Cancel", { source = null }, kind = WardButtonKind.quiet, fills = false)
            }
        }

        // bottom-trailing; on the trailing edge, centred, in a broad window (iOS IdeaToolsPlacement)
        val toolsSpot = if (currentWindowShape().isBroad) Alignment.CenterEnd else Alignment.BottomEnd
        Box(Modifier.align(toolsSpot).padding(bottom = hidden)) {
            IdeaToolCluster {
                IdeaBoardLookTool(settings)
                IdeaToolButton(Icons.Outlined.Link, if (connecting) "Stop connecting" else "Connect", active = connecting) {
                    connecting = !connecting; source = null
                }
                // iOS's smallcircle.filled.circle, on every Recentre
                IdeaToolButton(Icons.Outlined.RadioButtonChecked, "Recentre") { fitAll() }
            }
        }
    }

    adding?.let { spot ->
        NameSheet("New idea", "The idea", "", "Add", { adding = null }) { name -> book.create(name.trim(), kind = NoteKind.IDEA, at = spot) }
    }
}

/**
 * One card: its kind and folder in small type, the title, washed in its folder's colour. [version] is
 * the book's, read by no line here: a folder renamed in place reaches the card by it.
 */
@Composable private fun BoardCard(book: IdeaBook, @Suppress("UNUSED_PARAMETER") version: Int, note: Note, chosen: Boolean, lifted: Boolean, zoom: Double,
                                  chosenInk: androidx.compose.ui.graphics.Color, width: Double, oneKind: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(WardRadius.card.wardDp)
    val tone = toneColor(note.folderId, book)
    val kind = note.noteKind
    val folder = book.folder(note.folderId)?.name
    // the kind's word only where the board holds both kinds; the folder's name where it has one
    val caption = folder ?: if (oneKind) null else kind.label
    val title = note.title.ifEmpty { "Untitled" }
    // read as drawn, the whole title first
    val spoken = listOfNotNull(title, if (oneKind) null else kind.label, folder).joinToString(", ")
    val scale = (zoom * if (lifted) 1.04 else 1.0).toFloat()
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .width(width.dp)
        // the height the first view fits by, so its floor keeps a one-line card a target tall
        .heightIn(min = CARD_HEIGHT.dp)
        .wardRaised(WardRadius.card, if (lifted) WardLift.high else WardLift.low, fill = WardToken.raised)
        .clip(shape).background(tone.copy(alpha = 0.14f))
        .then(if (chosen) Modifier.border(2.dp, chosenInk, shape) else Modifier)
        .padding(WardSpace.m.wardDp)
        .semantics { testTag = BOARD_CARD_TAG; contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
            // grows with the reader's font scale, as iOS's symbol follows the caption
            Icon(kind.icon, null, tint = tone, modifier = Modifier.size(wardGlyph(12.0)).semantics { testTag = BOARD_KIND_TAG + kind.rawValue })
            // a folder's name takes two lines before it ends in "…" (S05m item 8)
            if (caption != null) Text(caption, style = WardTextStyle.caption2.textStyle(), color = wardColor(WardToken.inkSecondary),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // one line, at most 20 letters wide, then "…"; the whole title shows over the card (S05m item 5)
        Text(title, style = boardTitleStyle(), color = wardColor(WardToken.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

