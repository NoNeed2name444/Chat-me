package com.cramdown.sorta.ui.ideas

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeaEchoes
import com.cramdown.sorta.ideas.IdeaReshape
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

// Ideas like this, Collide, Split and Join (S06c; the plugins plan's ideas 4, 5 and 12), on the screen:
// what the editor, the map and the board share (IdeaShapes, through LocalIdeaShapes), the editor's strip
// of ideas like this one, and the three sheets: the one-idea picker (Join with, Collide with), the split
// chooser and the collide card. The rules are IdeaEchoes' and IdeaReshape's; the store's are IdeaBook's.
// iOS's IdeaShapesView.swift is the same screens with the same words.

/** The test tags, as iOS's identifiers. */
object IdeaShapeTags {
    const val ECHOES = "idea-echoes"
    const val ECHO = "idea-echo"
    const val PICKER = "shape-picker"
    const val SPLIT = "split-chooser"
    const val SPLIT_CONFIRM = "split-confirm"
    const val COLLIDE = "collide-card"
    const val COLLIDE_FIELD = "collide-field"
    const val COLLIDE_SAVE = "collide-save"
    const val COLLIDE_ANOTHER = "collide-another"
    const val COLLIDE_TOOL = "collide-tool"
    const val MENU_SPLIT = "editor-split"
    const val MENU_JOIN = "editor-join"
    const val MENU_COLLIDE = "editor-collide"
}

/** A sheet of IdeaShapes' up: the one-idea picker for [id] (Join with, or Collide with), the split chooser, or the collide card. */
sealed interface ShapeSheet {
    data class Pick(val id: String, val join: Boolean) : ShapeSheet
    data class Split(val id: String) : ShapeSheet
    /** [random]: Collide chose the pair, so the card offers another. */
    data class Collide(val a: String, val b: String, val random: Boolean) : ShapeSheet
}

/**
 * What the editor, the map and the board share: the idea open in the editor ([editing]) and the ideas
 * like it ([glowing]), which the map rings and the board edges; the sheet up; and the actions, each told
 * in the banner with Undo through [say]. [random] picks Collide's pairs (a seeded one in tests).
 */
@Stable class IdeaShapes(
    private val book: IdeaBook,
    private val res: Resources,
    private val say: (String, (() -> Unit)?) -> Unit,
    private val random: Random = Random.Default,
) {
    var editing by mutableStateOf<String?>(null)
    var glowing by mutableStateOf<List<String>>(emptyList())
    var sheet by mutableStateOf<ShapeSheet?>(null)
    /** Counts each split or join and each Undo of one: the editor reads the idea afresh when it changes. */
    var reshaped by mutableIntStateOf(0)
    /** The last pair Collide chose: never the next one, either way round. */
    private var last: Pair<String, String>? = null

    /** Whether [id] is one of the ideas like the open one, by any case of its id (the map's ids are upper case). */
    fun glows(id: String): Boolean = glowing.any { it.equals(id, ignoreCase = true) }

    private fun titleOf(id: String): String = book.note(id)?.let { n ->
        sequenceOf(n.title, n.body).mapNotNull { t -> t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } }.firstOrNull()
    } ?: res.getString(R.string.idea_untitled)

    /** The open idea's first line, for the words that name it (the board's link action). */
    fun editingLine(): String? = editing?.let(::titleOf)

    /** Links the open idea to [to] by hand, with Undo; nothing when none is open, it is the same idea, or they are linked already. */
    fun link(to: String) {
        val from = editing ?: return
        val other = book.note(to)?.id ?: return
        if (other == from || book.isLinked(from, other)) return
        book.link(from, other)
        glowing = glowing.filterNot { it.equals(other, ignoreCase = true) }
        say(res.getString(R.string.echoes_linked, titleOf(other))) { book.unlink(from, other) }
    }

    /** The split chooser for [id], or, for an idea of one sentence, why not. */
    fun askSplit(id: String) {
        val note = book.note(id) ?: return
        if (IdeaReshape.sentences(note.title, note.body).size < 2) say(res.getString(R.string.split_one), null)
        else sheet = ShapeSheet.Split(note.id)
    }

    fun split(id: String, after: Int) {
        sheet = null
        val done = book.split(id, after) ?: return
        reshaped++
        say(res.getString(R.string.split_done)) { book.undo(done); reshaped++ }
    }

    fun askJoin(id: String) { book.note(id)?.let { sheet = ShapeSheet.Pick(it.id, join = true) } }

    /** Joins [b] into [a]: [a] keeps its title, folder and place. */
    fun join(a: String, b: String) {
        sheet = null
        val done = book.join(a, b) ?: return
        reshaped++
        say(res.getString(R.string.join_done)) { book.undo(done); reshaped++ }
    }

    /** Joins two ideas brought together on the map: the older keeps its title, as the plan says. */
    fun joinOlderFirst(x: String, y: String) {
        val a = book.note(x) ?: return
        val b = book.note(y) ?: return
        if (b.createdAt < a.createdAt) join(b.id, a.id) else join(a.id, b.id)
    }

    fun askCollide(id: String) { book.note(id)?.let { sheet = ShapeSheet.Pick(it.id, join = false) } }

    /** The collide card for these two. */
    fun collide(a: String, b: String) {
        val x = book.note(a)?.id ?: return
        val y = book.note(b)?.id ?: return
        if (x != y) sheet = ShapeSheet.Collide(x, y, random = false)
    }

    /** Collide: two far-apart ideas at random, never the last pair, or why there are none. */
    fun collideAny() {
        val edges = HashSet<Pair<String, String>>()
        for ((a, b) in book.allEdges()) { edges.add(a to b); edges.add(b to a) }
        val places = book.notes.map { IdeaReshape.Place(it.id, book.rootFolder(it.folderId)) }
        val pair = IdeaReshape.collide(places, { a, b -> (a to b) in edges }, last, random)
        if (pair == null) { sheet = null; say(res.getString(R.string.collide_none), null); return }
        last = pair
        sheet = ShapeSheet.Collide(pair.first, pair.second, random = true)
    }

    /** Saves what was written on the card: a new idea in To sort linked to both, with Undo. Nothing written, nothing made. */
    fun save(a: String, b: String, text: String) {
        val done = book.collide(a, b, text) ?: return
        sheet = null
        say(res.getString(R.string.collide_saved)) { book.undo(done) }
    }

    fun dismiss() { sheet = null }
}

/** The page's IdeaShapes, null where none is given (a map or editor shown on its own, as in some tests). */
val LocalIdeaShapes = compositionLocalOf<IdeaShapes?> { null }

/** Holds the echoes' index between asks: built once per change to the ideas (the model's version). */
private class EchoIndex { var version = -1; var index: IdeaEchoes.Index? = null }

/**
 * The ideas like the one being written, under its details (every layout: it is the plain way to them, and
 * what TalkBack reaches): asked 300 ms after the last change, off the main thread, and handed to the map
 * and the board through [shapes] so they glow there. A tap links one, with Undo.
 */
@Composable internal fun EchoesStrip(book: IdeaBook, version: Int, note: Note, title: String, body: String, shapes: IdeaShapes?) {
    val id = note.id
    var echoes by remember(id) { mutableStateOf(emptyList<String>()) }
    val cache = remember(id) { EchoIndex() }
    LaunchedEffect(id, title, body, version) {
        delay(300)
        val notes = book.notes
        val connected = book.connections(id) + (book.note(id)?.links ?: emptyList())
        val made = note.createdAt.toDouble()
        val found = withContext(Dispatchers.Default) {
            val index = cache.index?.takeIf { cache.version == version }
                ?: IdeaEchoes.Index(notes.map { IdeaEchoes.Idea(it.id, it.title + "\n" + it.body, it.createdAt.toDouble()) })
                    .also { cache.index = it; cache.version = version }
            index.echoes("$title\n$body", id, made, connected)
        }
        echoes = found
        if (shapes != null && shapes.editing == id) shapes.glowing = found
    }
    DisposableEffect(id, shapes) {
        shapes?.editing = id
        onDispose { if (shapes != null && shapes.editing == id) { shapes.editing = null; shapes.glowing = emptyList() } }
    }
    val shown = echoes.mapNotNull { book.note(it) }
    if (shown.isEmpty()) return
    Column(Modifier.fillMaxWidth().semantics { testTag = IdeaShapeTags.ECHOES }, verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
        WardSectionLabel(stringResource(R.string.echoes_title), Modifier.padding(top = WardSpace.s.wardDp))
        val hint = stringResource(R.string.echoes_link_hint)
        // the row reaches into the page's margin, its chips padded back, so a chip's edge scrolls out
        // under the margin, not cut at the padding (as the peek card's chips, MapPanels.kt)
        Row(Modifier.fillMaxWidth().intoMargin(WardSpace.gutter.wardDp).horizontalScroll(rememberScrollState())
            .padding(horizontal = WardSpace.gutter.wardDp), horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            shown.forEach { other ->
                val line = ideaLine(other)
                val act = {
                    if (shapes != null) { shapes.editing = id; shapes.link(other.id) } else book.link(id, other.id)
                    echoes = echoes - other.id
                }
                WardChipButton(line.take(40).let { if (it.length < line.length) "$it…" else it }, act,
                    Modifier.semantics { contentDescription = line; testTag = IdeaShapeTags.ECHO; onClick(label = hint) { act(); true } },
                    icon = Icons.Outlined.Link, role = Role.Button, iconTint = wardColor(WardToken.accent))
            }
        }
    }
}

/** The sheet [shapes] has up, if any, over the page; [overMap] keeps the bar's icons light over a 3D look. */
@Composable fun IdeaShapeSheets(shapes: IdeaShapes, book: IdeaBook, version: Int, overMap: Boolean) {
    when (val sheet = shapes.sheet) {
        is ShapeSheet.Pick -> {
            val note = book.note(sheet.id)
            if (note == null) SideEffect { shapes.dismiss() }
            else IdeaPickerSheet(book, version, note.id, stringResource(if (sheet.join) R.string.shape_join_title else R.string.shape_collide_title),
                overMap, onDismiss = shapes::dismiss) { other -> if (sheet.join) shapes.join(note.id, other) else shapes.collide(note.id, other) }
        }
        is ShapeSheet.Split -> {
            val note = book.note(sheet.id)
            if (note == null) SideEffect { shapes.dismiss() }
            else SplitChooser(note, overMap, onDismiss = shapes::dismiss) { after -> shapes.split(note.id, after) }
        }
        is ShapeSheet.Collide -> {
            val a = book.note(sheet.a); val b = book.note(sheet.b)
            if (a == null || b == null) SideEffect { shapes.dismiss() }
            else CollideCard(book, a, b, overMap, another = if (sheet.random) shapes::collideAny else null,
                onDismiss = shapes::dismiss) { text -> shapes.save(a.id, b.id, text) }
        }
        null -> {}
    }
}

/** One other idea, found by title (as the link picker), for Join with and Collide with: a tap picks it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun IdeaPickerSheet(book: IdeaBook, version: Int, id: String, title: String, overMap: Boolean,
                                        onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val others = remember(book, version, id) { book.notes.filter { it.id != id }.sortedByDescending { it.updatedAt } }
    val shown = if (query.isBlank()) others else others.filter { it.title.contains(query.trim(), ignoreCase = true) || it.body.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        if (overMap) LightIconsOverTheMap()
        Column(Modifier.fillMaxWidth().semantics { testTag = IdeaShapeTags.PICKER }) {
            ReaderNavBar(title, leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, kind = WardButtonKind.quiet, fills = false) })
            val search = stringResource(R.string.picker_search)
            Box(Modifier.padding(horizontal = WardSpace.gutter.wardDp)) {
                WardTextField(query, { query = it }, Modifier.fillMaxWidth().semantics { contentDescription = search }, placeholder = search)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = IdeasLayout.chooserMaxHeight.dp), contentPadding = PaddingValues(WardSpace.s.wardDp)) {
                if (shown.isEmpty() && query.isNotBlank()) item {
                    Text(stringResource(R.string.picker_none, query.trim()), Modifier.fillMaxWidth().padding(WardSpace.s.wardDp),
                        style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
                }
                items(shown, key = { it.id }) { other ->
                    val line = ideaLine(other)
                    val folder = book.folder(other.folderId)?.name ?: stringResource(R.string.sort_title)
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(other.id) }.wardRowTile()
                        .semantics(mergeDescendants = true) {}) {
                        Text(line, style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(folder, style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary))
                    }
                }
            }
            Spacer(Modifier.height(WardSpace.l.wardDp))
        }
    }
}

/**
 * The split chooser: the idea's sentences, the title's first, each a row; the person taps the last sentence
 * of the first idea (the first sentence at the start) and sees both halves under their labels; Split idea
 * splits there. The last sentence can't end the first idea, so it is never a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SplitChooser(note: Note, overMap: Boolean, onDismiss: () -> Unit, onSplit: (Int) -> Unit) {
    val sentences = remember(note.id, note.title, note.body) { IdeaReshape.sentences(note.title, note.body) }
    var after by remember(note.id) { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        if (overMap) LightIconsOverTheMap()
        Column(Modifier.fillMaxWidth().semantics { testTag = IdeaShapeTags.SPLIT }) {
            ReaderNavBar(stringResource(R.string.split_title),
                leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, kind = WardButtonKind.quiet, fills = false) },
                trailing = {
                    WardTextButton(stringResource(R.string.split_confirm), { onSplit(after) },
                        Modifier.semantics { testTag = IdeaShapeTags.SPLIT_CONFIRM }, kind = WardButtonKind.compact, fills = false)
                })
            Text(stringResource(R.string.split_hint), Modifier.padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp),
                style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = IdeasLayout.chooserMaxHeight.dp),
                contentPadding = PaddingValues(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp)) {
                item { WardSectionLabel(stringResource(R.string.split_first)) }
                itemsIndexed(sentences) { i, sentence ->
                    val k = i + 1
                    if (k == after + 1) WardSectionLabel(stringResource(R.string.split_second), Modifier.padding(top = WardSpace.m.wardDp))
                    if (k < sentences.size) SentenceRow(sentence.text, ends = k == after) { after = k }
                    // the last sentence can't end the first idea, so it is words, not a row that looks tappable
                    else Text(sentence.text, Modifier.fillMaxWidth().padding(horizontal = (WardSpace.s + 12.0).wardDp, vertical = (WardSpace.s + 8.0).wardDp),
                        style = WardType.body.textStyle(), color = wardColor(WardToken.ink))
                }
            }
            Spacer(Modifier.height(WardSpace.l.wardDp))
        }
    }
}

/**
 * One sentence, a row tile (wardRowTile's, drawn here so the edge goes on the tile): [ends] (the first idea
 * ends with it) gives the tile the kit's chosen edge; a radio button for TalkBack.
 */
@Composable private fun SentenceRow(text: String, ends: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(WardRadius.field.wardDp)
    val endsWord = stringResource(R.string.split_ends_here)
    Box(Modifier.fillMaxWidth().padding(vertical = WardSpace.xs.wardDp)
        .selectable(ends, role = Role.RadioButton, onClick = onClick)
        // what "selected" means here, for TalkBack (review N4)
        .then(if (ends) Modifier.semantics { stateDescription = endsWord } else Modifier)
        .heightIn(min = WardControlSize.target.wardDp)
        .padding(WardSpace.s.wardDp)
        .wardRelief(shape, WardLift.low, control = true)
        .wardChosenEdge(shape, ends, wardColor(WardToken.primaryInk))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, style = WardType.body.textStyle(), color = wardColor(WardToken.ink))
    }
}

/**
 * The collide card: the two ideas face to face, "What if both?" and an empty field. Save idea makes the new
 * idea (in To sort, linked to both); Cancel leaves nothing. When Collide chose the pair, [another] offers
 * another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CollideCard(book: IdeaBook, a: Note, b: Note, overMap: Boolean, another: (() -> Unit)?,
                                    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(a.id, b.id) { mutableStateOf("") }
    // while words are written, a swipe or a tap outside keeps the card: Cancel is the way out
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || text.isBlank() })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = wardColor(WardToken.background)) {
        if (overMap) LightIconsOverTheMap()
        Column(Modifier.fillMaxWidth().semantics { testTag = IdeaShapeTags.COLLIDE }) {
            ReaderNavBar(stringResource(R.string.collide_tool),
                leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, kind = WardButtonKind.quiet, fills = false) },
                trailing = {
                    WardTextButton(stringResource(R.string.collide_save), { onSave(text) },
                        Modifier.semantics { testTag = IdeaShapeTags.COLLIDE_SAVE }, kind = WardButtonKind.compact,
                        enabled = text.isNotBlank(), fills = false)
                })
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = WardSpace.gutter.wardDp)
                .padding(top = WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                    CollideFace(book, a, Modifier.weight(1f).fillMaxHeight())
                    CollideFace(book, b, Modifier.weight(1f).fillMaxHeight())
                }
                Text(stringResource(R.string.collide_title), Modifier.semantics { heading() },
                    style = WardTextStyle.title3.textStyle(WardWeight.bold), color = wardColor(WardToken.ink))
                val label = stringResource(R.string.collide_field)
                BasicTextField(text, { text = it },
                    Modifier.fillMaxWidth().heightIn(min = IdeasLayout.bodyMinHeight.dp)
                        .semantics { contentDescription = label; testTag = IdeaShapeTags.COLLIDE_FIELD },
                    textStyle = WardType.body.textStyle().copy(color = wardColor(WardToken.ink)),
                    cursorBrush = SolidColor(wardColor(WardToken.primaryInk)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().wardField(), contentAlignment = Alignment.TopStart) {
                            if (text.isEmpty()) Text(label, style = WardType.body.textStyle(), color = wardDisabledInk())
                            inner()
                        }
                    })
                if (another != null) WardTextButton(stringResource(R.string.collide_another), another,
                    Modifier.semantics { testTag = IdeaShapeTags.COLLIDE_ANOTHER }, kind = WardButtonKind.secondary)
            }
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}

/** One of the two ideas on the card: its first line, a few of its lines, and its folder. */
@Composable private fun CollideFace(book: IdeaBook, note: Note, modifier: Modifier) {
    WardCardFace(modifier.semantics(mergeDescendants = true) {}, lift = WardLift.low, spacing = WardSpace.xs.wardDp) {
        Text(ideaLine(note), style = WardType.headline.textStyle(), color = wardColor(WardToken.ink), maxLines = 3, overflow = TextOverflow.Ellipsis)
        val rest = note.body.trim()
        if (rest.isNotEmpty() && note.title.isNotBlank()) Text(rest, style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.ink),
            maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(book.folder(note.folderId)?.name ?: stringResource(R.string.sort_title), style = WardTextStyle.footnote.textStyle(),
            color = wardColor(WardToken.inkSecondary))
    }
}
