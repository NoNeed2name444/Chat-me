package com.cramdown.sorta.ui.ideas

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.ImportRefusal
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

// The Sort screen (S05, direction A, the sorting desk): capture pinned at the top, then the
// ideas with no folder (To sort), then the folders; a folder opens its own ideas and folders
// with a row back up. Each idea is filed by hand with the folder chooser, and each filing and
// delete offers Undo. Copied from Stethoscore's Ideas screen
// (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/app/ui/ideas/
// IdeasView.kt): FolderPrompt (:71-74), the open note in place of the list with its Back
// (:98-102), captureIdea (:107-113), the deleted folder's way back to the top (:96), the list's
// up row, folder rows and note rows (:205-303, the note row cut to its first line), SwipeRow
// (:305-323), the naming sheets (:154-158) and the capture field (:349-363). Cut: the search,
// the bottom bar and FloatingSwitcher, the Add menu, New page, the title plate, Board and Space
// (S05b), the kind glyphs and folder colours, "n linked" and "Start typing". The words are in
// strings.xml (the ux-writing pass of 30 Oct).

/** The Sort screen's measures the kit has no entry for. */
object IdeasLayout {
    /** The readable column, at most: the kit's (WardDeck.kt:72), iOS's WindowSpan.broad. */
    const val column = 700.0
    /** The editor's details field, at least, as iOS's NoteEditorView. */
    const val bodyMinHeight = 140.0
    /** A sheet's list, at most, as MoveSheet's. */
    const val chooserMaxHeight = 480.0
    /** The editor's Write / Read, at least: the Lines picker's width (LINES_PICKER_WIDTH). */
    val segmentedWidth: Double = LINES_PICKER_WIDTH.toDouble()
}

/**
 * The identifiers the tests use: each is iOS's accessibilityIdentifier for the same control, but
 * [SCREEN], the screen's own column, which only Android's tests look inside.
 */
object IdeasTags {
    const val SCREEN = "sort-screen"
    /** The capture well: the field and Sort it. */
    const val CAPTURE = "capture"
    const val FIELD = "idea-capture"
    const val SORT_IT = "sort-it"
    const val TITLE = "ideas-title"
    const val MORE = "more"
    const val EMPTY = "ideas-empty"
    const val IDEA_LINE = "idea-row"
    /** The banner: Undo's and the store's notices. */
    const val BANNER = "notice"
}

/** Where the screen starts: the top level, or in a folder, with the editor or the chooser open (the renders' way in). */
data class IdeasStart(val folderId: String? = null, val editing: String? = null, val choosing: String? = null)

/** The naming sheet, for a new folder (filing [filing] in it, from the chooser) or a renamed one. */
private sealed class FolderPrompt {
    data class New(val parent: String?, val filing: String? = null) : FolderPrompt()
    data class Rename(val folder: NoteFolder) : FolderPrompt()
}

/** An idea's first line, as its row shows it: the title's, else the details', else "Untitled". */
@Composable internal fun ideaLine(note: Note): String {
    fun first(text: String) = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    return first(note.title) ?: first(note.body) ?: stringResource(R.string.idea_untitled)
}

/**
 * The Sort screen on [model]. [testImport] is the tests' file for the import (MainActivity's
 * launch extra): it goes to the same call the system picker's result does, once.
 */
@Composable fun IdeasView(model: IdeasModel, testImport: File? = null, start: IdeasStart = IdeasStart()) {
    val context = LocalContext.current
    val res = context.resources
    val settings = remember(context) { IdeasSettings(context) }
    val book = model.book
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf(start.folderId) }
    var editing by rememberSaveable { mutableStateOf(start.editing) }
    var choosing by rememberSaveable { mutableStateOf(start.choosing) }
    var naming by remember { mutableStateOf<FolderPrompt?>(null) }
    var deletingFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var imported by remember { mutableStateOf<ImportOutcome?>(null) }
    var testImported by rememberSaveable { mutableStateOf(false) }
    val banner = remember { SnackbarHostState() }
    var bannerHeight by remember { mutableIntStateOf(0) }
    val capture = remember { FocusRequester() }

    /** Shows [message] in the banner, replacing the one there; [undo] gives it its Undo. */
    fun say(message: String, undo: (() -> Unit)? = null, action: String? = null) {
        scope.launch {
            banner.currentSnackbarData?.dismiss()
            val label = if (undo != null) res.getString(R.string.undo_action) else action
            val result = banner.showSnackbar(message, actionLabel = label, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    fun folderName(id: String?): String = model.book.folder(id)?.name ?: res.getString(R.string.sort_title)

    /** Files an idea where the person chose, with Undo back to where it was. */
    fun file(id: String, to: String?) {
        val note = model.book.note(id) ?: return
        val before = note.folderId
        if (before == to) return
        model.book.move(id, to)
        val message = if (to == null) res.getString(R.string.undo_to_sort) else res.getString(R.string.undo_moved, folderName(to))
        say(message, undo = { model.book.move(id, before) })
    }

    fun deleteIdea(id: String) {
        val removed = model.book.removal(id) ?: return
        model.book.delete(id)
        say(res.getString(R.string.undo_deleted), undo = { model.book.restore(removed) })
    }

    /** captureIdea: the words trimmed, nothing for an empty field, into the open folder; the field stays ready. */
    fun captureIdea(): Note? {
        val text = draft.trim()
        if (text.isEmpty()) return null
        val note = model.book.create(text, folderId = folderId)
        draft = ""
        runCatching { capture.requestFocus() }
        return note
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { imported = importFrom(model) { context.contentResolver.openInputStream(uri) } }
    }
    LaunchedEffect(testImport) {
        if (testImport != null && !testImported) {
            testImported = true
            imported = importFrom(model) { FileInputStream(testImport) }
        }
    }
    // the store's notice, once
    LaunchedEffect(model.notice) {
        val notice = model.notice ?: return@LaunchedEffect
        model.notice = null
        say(res.getString(if (notice == IdeasNotice.UNREADABLE) R.string.store_unreadable else R.string.store_save_failed),
            action = res.getString(R.string.store_notice_close))
    }
    // the folder being browsed was deleted: back to the top
    LaunchedEffect(book.folders) { if (folderId != null && book.folder(folderId) == null) folderId = null }

    val open = editing?.takeIf { book.note(it) != null }
    BackHandler(enabled = open == null && folderId != null) { folderId = book.folder(folderId)?.parentId }

    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background))) {
        val shape = currentWindowShape()
        WardHingePane(shape, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = IdeasLayout.column.dp).fillMaxSize()) {
                    val bottom = with(LocalDensity.current) { bannerHeight.toDp() } + WardSpace.l.wardDp
                    if (open != null) {
                        NoteEditor(model, settings, open, bottom = bottom, choose = { choosing = it }, delete = { id -> deleteIdea(id) }) {
                            editing = null
                        }
                    } else SortPage(
                        book = book, version = model.version, folderId = folderId, draft = draft, onDraft = { draft = it }, capture = capture, bottom = bottom,
                        onCapture = { captureIdea() },
                        onSortIt = { captureIdea()?.let { choosing = it.id } },
                        setFolder = { folderId = it }, open = { editing = it }, choose = { choosing = it }, delete = ::deleteIdea,
                        name = { naming = it }, deleteFolder = { deletingFolder = it },
                        onImport = { picker.launch(arrayOf("application/json")) },
                    )
                    SnackbarHost(banner, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp)
                        .onSizeChanged { bannerHeight = it.height }) { data ->
                        WardBanner(data.visuals.message, modifier = Modifier.semantics { testTag = IdeasTags.BANNER },
                            actionTitle = data.visuals.actionLabel, action = { data.performAction() })
                    }
                }
            }
        }
    }

    choosing?.let { id ->
        val note = book.note(id)
        if (note == null) choosing = null
        else FolderChooser(book, note.folderId, version = model.version, onDismiss = { choosing = null },
            onChoose = { to -> choosing = null; file(id, to) },
            onNewFolder = { choosing = null; naming = FolderPrompt.New(parent = folderId, filing = id) })
    }
    when (val prompt = naming) {
        is FolderPrompt.New -> NameSheet(stringResource(R.string.name_new_title), stringResource(R.string.name_field), "",
            stringResource(R.string.name_create), { naming = null }) { name ->
            val folder = model.book.createFolder(name, prompt.parent)
            prompt.filing?.let { file(it, folder.id) }
        }
        is FolderPrompt.Rename -> NameSheet(stringResource(R.string.name_rename_title), stringResource(R.string.name_field), prompt.folder.name,
            stringResource(R.string.name_rename), { naming = null }) { model.book.renameFolder(prompt.folder.id, it) }
        null -> {}
    }
    deletingFolder?.let { folder ->
        val parent = folderName(folder.parentId)
        AlertDialog(onDismissRequest = { deletingFolder = null }, containerColor = wardColor(WardToken.background),
            title = { Text(stringResource(R.string.folder_delete_title, folder.name), color = wardColor(WardToken.ink)) },
            text = { Text(stringResource(R.string.folder_delete_body, parent), color = wardColor(WardToken.ink)) },
            confirmButton = {
                TextButton({ deletingFolder = null; model.book.deleteFolder(folder.id) }) {
                    Text(stringResource(R.string.folder_delete_confirm), color = wardColor(WardToken.danger))
                }
            },
            dismissButton = { TextButton({ deletingFolder = null }) { Text(stringResource(R.string.sheet_cancel), color = wardColor(WardToken.primaryInk)) } })
    }
    imported?.let { outcome -> ImportAlert(outcome, model.unreadable) { imported = null } }
}

/** The import's result, or why it was refused, in an alert: four counts are read, not timed out. */
@Composable private fun ImportAlert(outcome: ImportOutcome, unreadable: Boolean, onDone: () -> Unit) {
    val (title, body) = when (outcome) {
        is ImportOutcome.Added -> {
            val c = outcome.counts
            if (c.ideas == 0 && c.folders == 0) stringResource(R.string.import_nothing_title) to stringResource(R.string.import_nothing_body)
            else {
                val added = stringResource(R.string.import_done_body,
                    pluralStringResource(R.plurals.import_ideas, c.ideas, c.ideas),
                    pluralStringResource(R.plurals.import_folders, c.folders, c.folders),
                    pluralStringResource(R.plurals.import_links, c.links, c.links))
                val placed = if (c.placed > 0) " " + pluralStringResource(R.plurals.import_placed, c.placed, c.placed) else ""
                stringResource(R.string.import_done_title) to added + placed
            }
        }
        is ImportOutcome.Refused -> stringResource(R.string.import_refused_title) to stringResource(when (outcome.refusal) {
            ImportRefusal.TOO_LARGE -> R.string.import_refused_too_large
            ImportRefusal.UNREADABLE -> R.string.import_refused_unreadable
            ImportRefusal.NOT_AN_EXPORT -> R.string.import_refused_not_export
            ImportRefusal.OTHER_VERSION -> R.string.import_refused_other_version
        })
        ImportOutcome.NotSaved -> stringResource(R.string.import_refused_title) to
            stringResource(if (unreadable) R.string.store_unreadable else R.string.store_save_failed)
    }
    AlertDialog(onDismissRequest = onDone, containerColor = wardColor(WardToken.background),
        title = { Text(title, color = wardColor(WardToken.ink)) },
        text = { Text(body, color = wardColor(WardToken.ink)) },
        confirmButton = { TextButton(onDone) { Text(stringResource(R.string.import_ok), color = wardColor(WardToken.primaryInk)) } })
}

/** The list: the well pinned above it, then the up row, the title row, the ideas, and Folders. */
@Composable private fun SortPage(
    book: IdeaBook, version: Int, folderId: String?, draft: String, onDraft: (String) -> Unit, capture: FocusRequester, bottom: Dp,
    onCapture: () -> Unit, onSortIt: () -> Unit, setFolder: (String?) -> Unit, open: (String) -> Unit, choose: (String) -> Unit,
    delete: (String) -> Unit, name: (FolderPrompt) -> Unit, deleteFolder: (NoteFolder) -> Unit, onImport: () -> Unit,
) {
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val hairline = wardColor(WardToken.hairline)
    // read again whenever the book changes: the book is changed in place, so [version] is what moves
    val current = remember(book, version, folderId) { book.folder(folderId) }
    val here = remember(book, version, folderId) { book.contents(folderId) }
    val subfolders = remember(book, version, folderId) { book.subfolders(folderId) }
    val fresh = remember(book, version) { book.notes.isEmpty() && book.folders.isEmpty() }
    // a folder's count takes in its nested folders' ideas, as its row's does; To sort's, the ideas without a folder
    val count = remember(book, version, folderId) { current?.let { book.count(it.id) } ?: here.size }
    val inset = WardSpace.s.wardDp
    val more: @Composable () -> Unit = { MoreButton(onImport, onNewFolder = { name(FolderPrompt.New(folderId)) }) }
    Column(Modifier.fillMaxSize().semantics { testTag = IdeasTags.SCREEN }) {
        // the pinned strip: the list scrolls under it and is cut at its edge, with a hairline while it is scrolled
        Box(Modifier.fillMaxWidth().background(wardColor(WardToken.background)).drawBehind {
            val edge = WardPaper.edgeWidth.wardDp.toPx()
            if (scrolled) drawLine(hairline, Offset(0f, size.height - edge / 2), Offset(size.width, size.height - edge / 2), edge)
        }.padding(horizontal = WardSpace.gutter.wardDp).padding(top = WardSpace.s.wardDp, bottom = WardSpace.s.wardDp)) {
            CaptureWell(draft, onDraft, capture, onCapture, onSortIt)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(start = inset, end = inset, top = WardSpace.xs.wardDp, bottom = bottom)) {
            if (fresh) {
                // m under the well: the strip's s and the list's xs, with nothing more
                item(key = "empty") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = inset).semantics { testTag = IdeasTags.EMPTY },
                        horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
                            for (line in listOf(R.string.empty_line1, R.string.empty_line2)) {
                                Text(stringResource(line), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
                            }
                        }
                        more()
                    }
                }
                return@LazyColumn
            }
            if (current != null) item(key = "up") {
                val parent = book.folder(current.parentId)?.name ?: stringResource(R.string.sort_title)
                UpRow(parent) { setFolder(current.parentId) }
            }
            item(key = "title") { TitleRow(current?.name, count, more) }
            if (here.isEmpty()) item(key = "none") {
                Text(stringResource(if (current != null) R.string.folder_empty else R.string.sort_all_sorted),
                    Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = WardSpace.s.wardDp),
                    style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            } else items(here, key = { it.id }) { note ->
                IdeaRow(note, open = { open(note.id) }, choose = { choose(note.id) }, delete = { delete(note.id) })
            }
            if (subfolders.isNotEmpty()) {
                item(key = "folders") {
                    WardSectionLabel(stringResource(R.string.sort_folders),
                        Modifier.padding(horizontal = inset).padding(top = WardSpace.l.wardDp, bottom = WardSpace.xs.wardDp))
                }
                items(subfolders, key = { "f-" + it.id }) { folder ->
                    FolderRow(folder, book.count(folder.id), open = { setFolder(folder.id) },
                        rename = { name(FolderPrompt.Rename(folder)) }, newInside = { name(FolderPrompt.New(folder.id)) },
                        delete = { deleteFolder(folder) })
                }
            }
        }
    }
}

/**
 * The capture well: a capsule pressed into the base, the field and, at its trailing end, Sort it.
 * The field takes the whole well before Sort it, so one tap anywhere there focuses it. At the
 * accessibility sizes the well stacks: the field full width, Sort it under it.
 */
@Composable private fun CaptureWell(draft: String, onDraft: (String) -> Unit, focus: FocusRequester, onDone: () -> Unit, onSortIt: () -> Unit) {
    val ax = wardAccessibilitySize()
    val placeholder = stringResource(R.string.capture_placeholder)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    @Composable fun Field(modifier: Modifier) {
        // at the accessibility sizes the words wrap; Return still saves (a typed line break is taken as it)
        val change: (String) -> Unit = { text -> if (ax && '\n' in text) { onDraft(text.replace("\n", "")); onDone() } else onDraft(text) }
        BasicTextField(draft, change, modifier.focusRequester(focus).semantics { testTag = IdeasTags.FIELD; contentDescription = placeholder },
            singleLine = !ax, interactionSource = source,
            textStyle = WardType.body.textStyle().copy(color = wardColor(WardToken.ink)),
            cursorBrush = SolidColor(wardColor(WardToken.primaryInk)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = WardControlSize.button.wardDp).padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp),
                    contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) Text(placeholder, style = WardType.body.textStyle(), color = wardDisabledInk())
                    inner()
                }
            })
    }
    val ring = wardColor(WardControlStates.focusInk)
    if (!ax) {
        Row(Modifier.fillMaxWidth().heightIn(min = WardControlSize.button.wardDp).wardFocusRing(CircleShape, focused, ring)
            .wardInset(CircleShape, control = true).semantics { testTag = IdeasTags.CAPTURE },
            verticalAlignment = Alignment.CenterVertically) {
            Field(Modifier.weight(1f).padding(end = WardSpace.xs.wardDp))
            SortItButton(draft.isNotBlank(), onSortIt, Modifier.padding(end = WardSpace.xs.wardDp))
        }
    } else {
        val wellShape = RoundedCornerShape(WardRadius.field.wardDp)
        Column(Modifier.fillMaxWidth().semantics { testTag = IdeasTags.CAPTURE }, verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            Box(Modifier.fillMaxWidth().wardFocusRing(wellShape, focused, ring).wardInset(wellShape, control = true)) { Field(Modifier.fillMaxWidth()) }
            SortItButton(draft.isNotBlank(), onSortIt, Modifier.fillMaxWidth(), fill = true)
        }
    }
}

/**
 * Sort it: the one filled control, a capsule in primary with its words in onPrimary, on the kit's
 * press face, 44 drawn in a 48 target. Disabled while the field is blank, in the kit's disabled
 * look: the lowest step, Biro Grey words, no fill. [fill]: the full width, where the well stacks.
 */
@Composable private fun SortItButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val down = enabled && held
    val hint = stringResource(R.string.capture_sort_hint)
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(WardControlSize.buttonSmall.wardDp, WardControlSize.buttonSmall.wardDp)
            .capPop(down)
            .wardFocusRing(CircleShape, focused && enabled, wardColor(WardControlStates.focusInk))
            .wardPressFace(CircleShape, WardLift.low, wardDepth(down), fill = wardColor(if (enabled) WardToken.primary else WardToken.surface))
            .clickable(source, indication = null, enabled = enabled, onClickLabel = hint, role = Role.Button, onClick = onClick)
            .semantics { testTag = IdeasTags.SORT_IT }
            .padding(horizontal = WardSpace.m.wardDp, vertical = WardSpace.s.wardDp),
            contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.capture_sort), style = WardType.buttonPrimary.textStyle(),
                color = if (enabled) wardColor(WardToken.onPrimary) else wardDisabledInk(), maxLines = 1, softWrap = false)
        }
    }
}

/** "To sort 3", or the open folder's name and count, as a header; the More circle at its end. */
@Composable private fun TitleRow(folder: String?, count: Int, more: @Composable () -> Unit) {
    val title = folder ?: stringResource(R.string.sort_title)
    val spoken = if (folder == null) pluralStringResource(R.plurals.sort_title_spoken, count, count)
        else pluralStringResource(R.plurals.folder_title_spoken, count, folder, count)
    val countInk = wardColor(WardToken.primaryInk)
    Row(Modifier.fillMaxWidth().padding(start = WardSpace.s.wardDp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        Text(buildAnnotatedString {
            append(title)
            append(" ")
            withStyle(SpanStyle(color = countInk)) { append("$count") }
        }, Modifier.weight(1f).semantics { heading(); contentDescription = spoken; testTag = IdeasTags.TITLE },
            style = WardTextStyle.title.textStyle(WardWeight.bold), color = wardColor(WardToken.ink))
        more()
    }
}

/** The More circle: Import from Stethoscore… and New folder. */
@Composable private fun MoreButton(onImport: () -> Unit, onNewFolder: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        WardCircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.sort_more), { open = true },
            Modifier.semantics { testTag = IdeasTags.MORE })
        DropdownMenu(open, { open = false }, containerColor = wardColor(WardToken.background)) {
            MenuItem(stringResource(R.string.more_import)) { open = false; onImport() }
            MenuItem(stringResource(R.string.more_new_folder)) { open = false; onNewFolder() }
        }
    }
}

/** A menu's item: an action in the accent's words, a destructive one in danger. */
@Composable internal fun MenuItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem({ Text(text, style = WardType.body.textStyle(), color = wardColor(if (destructive) WardToken.danger else WardToken.primaryInk)) }, onClick)
}

/** The first row in a folder: back to its parent ("To sort" at the top level). */
@Composable private fun UpRow(parent: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.up_row_spoken, parent)
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).wardRowTile()
        .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
        Text(parent, Modifier.weight(1f), style = WardType.body.textStyle(), color = wardColor(WardToken.primaryInk), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An idea: its first line, which opens the editor, and Choose folder, which opens the chooser; the
 * two never overlap. Held, its menu; swiped, Choose folder (leading) or Delete (trailing), which
 * TalkBack offers as its actions too. At the accessibility sizes Choose folder goes under the words.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun IdeaRow(note: Note, open: () -> Unit, choose: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val line = ideaLine(note)
    val ax = wardAccessibilitySize()
    val chooseWords = stringResource(R.string.row_choose_folder)
    val deleteWords = stringResource(R.string.row_delete)
    val chooseSpoken = stringResource(R.string.row_choose_folder_spoken, line)
    val hint = stringResource(R.string.row_hint)
    val actions = listOf(CustomAccessibilityAction(chooseWords) { choose(); true }, CustomAccessibilityAction(deleteWords) { delete(); true })
    SwipeRow(lead = chooseWords to choose, trail = deleteWords to delete) {
        Box {
            @Composable fun Words(modifier: Modifier) {
                Box(modifier.heightIn(min = WardControlSize.target.wardDp)
                    .combinedClickable(role = Role.Button, onClickLabel = hint, onLongClick = { menu = true }, onClick = open)
                    .semantics { customActions = actions; testTag = IdeasTags.IDEA_LINE },
                    contentAlignment = Alignment.CenterStart) {
                    Text(line, style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = if (ax) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
            @Composable fun Choose(modifier: Modifier) {
                WardTextButton(chooseWords, choose, modifier.semantics { contentDescription = chooseSpoken }, kind = WardButtonKind.compact, fills = false)
            }
            if (ax) Column(Modifier.fillMaxWidth().wardRowTile(), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                Words(Modifier.fillMaxWidth())
                Choose(Modifier)
            } else Row(Modifier.fillMaxWidth().wardRowTile(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                Words(Modifier.weight(1f))
                Choose(Modifier)
            }
            DropdownMenu(menu, { menu = false }, containerColor = wardColor(WardToken.background)) {
                MenuItem(chooseWords) { menu = false; choose() }
                MenuItem(deleteWords, destructive = true) { menu = false; delete() }
            }
        }
    }
}

/**
 * A folder: its glyph in the accent, its name, how many ideas are in it, a chevron; held, its menu;
 * swiped trailing, Delete folder, which asks first (the row springs back), as TalkBack's action does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FolderRow(folder: NoteFolder, count: Int, open: () -> Unit, rename: () -> Unit, newInside: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val spoken = pluralStringResource(R.plurals.folder_row_spoken, count, folder.name, count)
    val renameWords = stringResource(R.string.folder_rename)
    val insideWords = stringResource(R.string.folder_new_inside)
    val deleteWords = stringResource(R.string.folder_delete)
    SwipeRow(lead = null, trail = deleteWords to delete, trailGoes = false) { Box {
        Row(Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onLongClick = { menu = true }, onClick = open).wardRowTile()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                customActions = listOf(CustomAccessibilityAction(renameWords) { rename(); true },
                    CustomAccessibilityAction(insideWords) { newInside(); true }, CustomAccessibilityAction(deleteWords) { delete(); true })
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            Icon(Icons.Outlined.Folder, null, tint = wardColor(WardToken.accent), modifier = Modifier.size(wardGlyph(20.0)))
            Text(folder.name, Modifier.weight(1f), style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count", style = WardTextStyle.footnote.textStyle().copy(fontFeatureSettings = "tnum"), color = wardColor(WardToken.inkSecondary))
            WardChevron()
        }
        DropdownMenu(menu, { menu = false }, containerColor = wardColor(WardToken.background)) {
            MenuItem(renameWords) { menu = false; rename() }
            MenuItem(insideWords) { menu = false; newInside() }
            MenuItem(deleteWords, destructive = true) { menu = false; delete() }
        }
    } }
}

/**
 * A row's swipes (SwipeRow): leading to [lead] (it springs back), trailing to [trail] (the row
 * goes, or springs back when [trailGoes] is false, for an action that asks first). The words
 * underneath are ours: the accent's words and danger on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SwipeRow(lead: Pair<String, () -> Unit>?, trail: Pair<String, () -> Unit>, trailGoes: Boolean = true,
                                  content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.EndToStart -> { trail.second(); trailGoes }
            SwipeToDismissBoxValue.StartToEnd -> { lead?.second?.invoke(); false }
            else -> false
        }
    })
    SwipeToDismissBox(state, enableDismissFromStartToEnd = lead != null, backgroundContent = {
        val trailing = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
        Row(Modifier.fillMaxSize().padding(horizontal = WardSpace.l.wardDp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (trailing) Arrangement.End else Arrangement.Start) {
            if (trailing) Text(trail.first, style = WardType.buttonSmall.textStyle(), color = wardColor(WardToken.danger))
            else if (lead != null && state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                Text(lead.first, style = WardType.buttonSmall.textStyle(), color = wardColor(WardToken.primaryInk))
        }
    }) { content() }
}
