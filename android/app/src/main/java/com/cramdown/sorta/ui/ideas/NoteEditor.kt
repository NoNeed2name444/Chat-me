package com.cramdown.sorta.ui.ideas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteEditing
import com.cramdown.sorta.ideas.NoteMarkdown
import com.cramdown.sorta.ui.sources.ReaderDoneButton
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.delay

// The editor (S05): one idea, its title, its folder, its details (written, or read laid out as
// Markdown), what it links to and what links to it. Typing `[[` offers the ideas it could mean.
// Changes save half a second after the last key and on leaving. Shown in place of the list, with
// the system's Back; following a link opens that idea here, Back returning along the trail.
// Copied from Stethoscore (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/
// app/ui/ideas/NoteEditor.kt): NoteEditor and its trail (:68-81), the page (:84-237) with its save
// (:98-111), the title and details fields, the `[[` suggestions, the link sections and the delete
// confirm, LinkRow (:243-252), MarkdownReader and inline (:295-337) and LinkPicker (:339-373).
// Cut: the kind chip (:255-267) and Turn into cards with its alert (:214-224, :233-236), which
// were study's; the folder chip's menu (:270-283), which is the folder chooser now; the kind
// glyphs and folder colours; and every literal size. The words are in strings.xml.

/** The test tag on the editor's page. */
const val EDITOR_TAG = "idea-editor"
/** The test tag on the link picker's content. */
const val LINK_PICKER_TAG = "link-picker"

@Composable fun NoteEditor(model: IdeasModel, settings: IdeasSettings, noteId: String, bottom: Dp,
                           choose: (String) -> Unit, delete: (String) -> Unit, onClose: () -> Unit) {
    var trail by rememberSaveable { mutableStateOf(listOf<String>()) }
    var currentId by rememberSaveable { mutableStateOf(noteId) }
    val book = model.book
    val note = book.note(currentId)
    if (note == null) { LaunchedEffect(currentId) { if (trail.isEmpty()) onClose() else { currentId = trail.last(); trail = trail.dropLast(1) } }; return }

    key(currentId) {
        NoteEditorPage(model, settings, note, bottom, backTitle = trail.lastOrNull()?.let { book.note(it) }?.let { ideaLine(it) },
            go = { id -> if (id != currentId) { trail = trail + currentId; currentId = id } },
            back = { if (trail.isEmpty()) onClose() else { currentId = trail.last(); trail = trail.dropLast(1) } },
            choose = choose, delete = { delete(it); onClose() }, done = onClose)
    }
}

@Composable private fun NoteEditorPage(
    model: IdeasModel, settings: IdeasSettings, note: Note, bottom: Dp, backTitle: String?,
    go: (String) -> Unit, back: () -> Unit, choose: (String) -> Unit, delete: (String) -> Unit, done: () -> Unit,
) {
    val id = note.id
    var title by rememberSaveable(id) { mutableStateOf(note.title) }
    var body by rememberSaveable(id) { mutableStateOf(note.body) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val book = model.book
    val reading = settings.reading
    val ax = wardAccessibilitySize()

    fun save() {
        val now = model.book.note(id) ?: return
        model.book.update(now.copy(title = title, body = body))
    }
    // half a second after the last key, and whenever the page goes
    LaunchedEffect(title, body) { delay(500); save() }
    DisposableEffect(id) { onDispose { save() } }
    fun leave(then: () -> Unit) { save(); then() }
    fun openTitle(name: String) {
        save()
        val found = model.book.resolve("[[$name]]").firstOrNull()
        go(found ?: model.book.create(name, folderId = note.folderId).id)
    }
    BackHandler { leave(back) }

    Column(Modifier.fillMaxSize().semantics { testTag = EDITOR_TAG }) {
        ReaderNavBar("",
            leading = {
                if (backTitle != null) {
                    val spoken = stringResource(R.string.up_row_spoken, backTitle)
                    Row(Modifier.heightIn(min = WardControlSize.target.wardDp).clickable(role = Role.Button) { leave(back) }
                        .padding(horizontal = WardSpace.s.wardDp).semantics(mergeDescendants = true) { contentDescription = spoken },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
                        Text(backTitle, Modifier.widthIn(max = IdeasLayout.trailMaxWidth.dp), style = WardType.body.textStyle(), color = wardColor(WardToken.primaryInk),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            trailing = {
                Box {
                    WardCircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.sort_more), { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = wardColor(WardToken.background)) {
                        MenuItem(stringResource(R.string.editor_delete), destructive = true) { menu = false; deleting = true }
                    }
                }
                ReaderDoneButton { leave(done) }
            })

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = WardSpace.gutter.wardDp),
            verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            // the title, in a well
            EditorField(title, { title = it }, stringResource(R.string.editor_title), WardType.title, Modifier.fillMaxWidth(), well = true)
            FolderChip(book, note) { save(); choose(id) }

            EditorSection(stringResource(R.string.editor_body))
            if (reading) MarkdownReader(body, Modifier.fillMaxWidth().wardRowTile(), openTitle = ::openTitle)
            else {
                Box(Modifier.fillMaxWidth().wardRowTile()) {
                    EditorField(body, { body = it }, stringResource(R.string.editor_body), WardType.body,
                        Modifier.fillMaxWidth().heightIn(min = IdeasLayout.bodyMinHeight.dp), well = false)
                }
                val suggestions = NoteEditing.suggestions(body, book.notes, id)
                if (suggestions.isNotEmpty()) FlowRowOf(suggestions.map { it.title }) { name -> body = NoteEditing.complete(body, name) }
            }
            Text(stringResource(R.string.editor_hint), style = WardType.caption.textStyle(), color = wardColor(WardToken.inkSecondary))

            EditorSection(stringResource(R.string.editor_linked))
            val outgoing = NoteEditing.outgoing(book, id, body)
            val missing = NoteEditing.unresolved(book, body)
            val byHand = book.note(id)?.links?.toSet() ?: emptySet()
            val hand = stringResource(R.string.editor_how_hand)
            val outText = stringResource(R.string.editor_how_text_out)
            outgoing.forEach { other ->
                book.note(other)?.let {
                    LinkRow(it, if (other in byHand) hand else outText, unlink = if (other in byHand) ({ book.unlink(id, other) }) else null) { save(); go(other) }
                }
            }
            missing.forEach { name ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { openTitle(name) }.wardRowTile(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = WardType.body.textStyle(), color = wardColor(WardToken.ink))
                        Text(stringResource(R.string.editor_missing), style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary))
                    }
                }
            }
            if (outgoing.isEmpty() && missing.isEmpty()) {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { save(); picking = true }.wardRowTile(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                    Icon(Icons.Rounded.Link, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
                    Text(stringResource(R.string.editor_link_to), style = WardType.body.textStyle(), color = wardColor(WardToken.primaryInk))
                }
            }

            val incoming = NoteEditing.backlinks(book, id, body)
            if (incoming.isNotEmpty()) {
                EditorSection(stringResource(R.string.editor_linked_here))
                val inText = stringResource(R.string.editor_how_text_in)
                incoming.forEach { other ->
                    book.note(other)?.let { from ->
                        val manual = id in from.links
                        LinkRow(from, if (manual) hand else inText, unlink = if (manual) ({ book.unlink(id, other) }) else null) { save(); go(other) }
                    }
                }
            }
            Spacer(Modifier.height(WardSpace.l.wardDp))
        }

        // the action bar, above the navigation bar and the keyboard: Write / Read, and Link to an idea…
        // [segment] sizes Write / Read: the full width when stacked, else its width, given up before the Link button is squeezed
        val bar: @Composable (segment: Modifier) -> Unit = { segment ->
            val write = stringResource(R.string.editor_write)
            val read = stringResource(R.string.editor_read)
            WardSegmented(reading, listOf(false, true), { save(); settings.setReading(it) }, segment) { r, text, ink ->
                Text(if (r) read else write, style = text, color = ink)
            }
            WardCircleButton(Icons.Rounded.Link, stringResource(R.string.editor_link_to), { save(); picking = true })
        }
        if (ax) Column(Modifier.fillMaxWidth().padding(horizontal = WardSpace.gutter.wardDp).padding(top = WardSpace.s.wardDp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), horizontalAlignment = Alignment.CenterHorizontally) { bar(Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth().padding(horizontal = WardSpace.gutter.wardDp).padding(top = WardSpace.s.wardDp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            bar(Modifier.weight(1f, fill = false).widthIn(min = IdeasLayout.segmentedWidth.dp))
        }
    }

    if (picking) LinkPicker(book, model.version, id) { picking = false }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, containerColor = wardColor(WardToken.background),
        title = { Text(stringResource(R.string.editor_delete_title), color = wardColor(WardToken.ink)) },
        text = { Text(stringResource(R.string.editor_delete_body), color = wardColor(WardToken.ink)) },
        confirmButton = { TextButton(onClick = { deleting = false; delete(id) }) { Text(stringResource(R.string.editor_delete_confirm), color = wardColor(WardToken.danger)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.sheet_cancel), color = wardColor(WardToken.primaryInk)) } })
}

/** The `[[` suggestions, as compact buttons that wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowRowOf(names: List<String>, pick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
        names.forEach { name -> WardTextButton(name, { pick(name) }, kind = WardButtonKind.compact, fills = false) }
    }
}

/**
 * A text field of the editor's, wrapping: the title in a well ([well], wardField) with the focus
 * ring, the details flat on their tile. The placeholder is Biro Grey, the caret the accent's ink.
 */
@Composable private fun EditorField(value: String, onValue: (String) -> Unit, label: String, type: WardTypeSpec, modifier: Modifier, well: Boolean) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(WardRadius.field.wardDp)
    BasicTextField(value, onValue,
        modifier.heightIn(min = WardControlSize.target.wardDp)
            .then(if (well) Modifier.wardFocusRing(shape, focused, wardColor(WardControlStates.focusInk)) else Modifier)
            .semantics { contentDescription = label },
        interactionSource = source,
        textStyle = type.textStyle().copy(color = wardColor(WardToken.ink)),
        cursorBrush = SolidColor(wardColor(WardToken.primaryInk)),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(if (well) Modifier.fillMaxWidth().wardField() else Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) Text(label, style = type.textStyle(), color = wardDisabledInk())
                inner()
            }
        })
}

@Composable private fun EditorSection(text: String) {
    WardSectionLabel(text, Modifier.padding(top = WardSpace.s.wardDp))
}

/** The idea's folder, as the kit's chip: its name, or To sort; it opens the folder chooser. */
@Composable private fun FolderChip(book: IdeaBook, note: Note, onClick: () -> Unit) {
    val name = book.folder(note.folderId)?.name ?: stringResource(R.string.sort_title)
    val spoken = stringResource(R.string.editor_chip_spoken, name)
    WardChipButton(name, onClick, Modifier.semantics { contentDescription = spoken }, icon = Icons.Outlined.Folder, role = Role.Button)
}

/** A linked idea: its first line, and how it is linked; a hand-made one can be unlinked (swipe, menu, action). */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun LinkRow(note: Note, caption: String, unlink: (() -> Unit)?, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val unlinkWords = stringResource(R.string.editor_unlink)
    val row: @Composable () -> Unit = {
        Box {
            Column(Modifier.fillMaxWidth()
                .combinedClickable(role = Role.Button, onLongClick = if (unlink != null) ({ menu = true }) else null, onClick = onClick)
                .wardRowTile()
                .semantics { if (unlink != null) customActions = listOf(CustomAccessibilityAction(unlinkWords) { unlink(); true }) }) {
                Text(ideaLine(note), style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(caption, style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary))
            }
            if (unlink != null) DropdownMenu(menu, { menu = false }, containerColor = wardColor(WardToken.background)) {
                MenuItem(unlinkWords, destructive = true) { menu = false; unlink() }
            }
        }
    }
    if (unlink != null) SwipeRow(lead = null, trail = unlinkWords to unlink) { row() } else row()
}

/** The idea laid out (NoteMarkdownView): headings, bullets, quotes, code, rules; `[[links]]` open ideas. */
@Composable private fun MarkdownReader(text: String, modifier: Modifier, openTitle: (String) -> Unit) {
    val blocks = NoteMarkdown.blocks(text)
    val ink = wardColor(WardToken.ink)
    val soft = wardColor(WardToken.inkSecondary)
    val link = wardColor(WardToken.primaryInk)
    val hairline = wardColor(WardToken.hairline)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        if (blocks.isEmpty()) Text(stringResource(R.string.editor_body), style = WardType.body.textStyle(), color = wardDisabledInk())
        blocks.forEach { block ->
            when (block) {
                is NoteMarkdown.Block.Heading -> Text(inline(block.text, link, openTitle), Modifier.semantics { heading() },
                    style = when (block.level) { 1 -> WardTextStyle.title2; 2 -> WardTextStyle.title3; else -> WardTextStyle.headline }.textStyle(WardWeight.bold), color = ink)
                is NoteMarkdown.Block.Bullet -> Row(Modifier.padding(start = (block.depth * WardSpace.l).dp), horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                    Text(if (block.marker.firstOrNull()?.isDigit() == true) block.marker else "•", style = WardType.body.textStyle(), color = soft)
                    Text(inline(block.text, link, openTitle), style = WardType.body.textStyle(), color = ink)
                }
                is NoteMarkdown.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                    Box(Modifier.width(WardSpace.xs.wardDp).fillMaxHeight().wardInset(CircleShape))
                    Text(inline(block.text, link, openTitle), style = WardType.body.textStyle().copy(fontStyle = FontStyle.Italic), color = soft)
                }
                is NoteMarkdown.Block.Code -> Text(block.text, Modifier.fillMaxWidth().wardField(), style = WardTextStyle.callout.textStyle(mono = true), color = ink)
                NoteMarkdown.Block.Rule -> HorizontalDivider(color = hairline)
                is NoteMarkdown.Block.Paragraph -> Text(inline(block.text, link, openTitle), style = WardType.body.textStyle(), color = ink)
            }
        }
    }
}

private fun inline(line: String, link: Color, openTitle: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    for (span in NoteMarkdown.inline(line)) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            textDecoration = if (span.strike) TextDecoration.LineThrough else null,
        )
        val links = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
        when {
            span.noteLink != null -> withLink(LinkAnnotation.Clickable(span.noteLink, links) { openTitle(span.noteLink) }) { withStyle(style) { append(span.text) } }
            span.url != null -> withLink(LinkAnnotation.Url(span.url, links)) { withStyle(style) { append(span.text) } }
            else -> withStyle(style) { append(span.text) }
        }
    }
}

/** Link to an idea (NoteLinkPicker): every other idea, found by title; a tick joins or parts. [version] as FolderChooser's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LinkPicker(book: IdeaBook, version: Int, id: String, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    // read again whenever the book changes: the book is changed in place, so [version] is what moves
    val others = remember(book, version, id) { book.notes.filter { it.id != id }.sortedByDescending { it.updatedAt } }
    val linkedIds = remember(book, version, id) { others.filter { book.isLinked(id, it.id) }.map { it.id }.toSet() }
    val shown = if (query.isBlank()) others else others.filter { it.title.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        Column(Modifier.fillMaxWidth().semantics { testTag = LINK_PICKER_TAG }) {
            // each tick joins or parts at once, so the sheet has Done and no Cancel, as the reference's
            ReaderNavBar(stringResource(R.string.picker_title), trailing = { ReaderDoneButton(onClick = onDone) })
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
                    val linked = other.id in linkedIds
                    val line = ideaLine(other)
                    Row(Modifier.fillMaxWidth().toggleable(linked, role = Role.Checkbox) { if (linked) book.unlink(id, other.id) else book.link(id, other.id) }
                        .wardRowTile().semantics(mergeDescendants = true) { contentDescription = line },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                        Text(line, Modifier.weight(1f), style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (linked) Icon(Icons.Rounded.Check, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
                    }
                }
            }
            Spacer(Modifier.height(WardSpace.l.wardDp))
        }
    }
}
