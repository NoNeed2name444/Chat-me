package com.cramdown.sorta.ui.ideas

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.ImportRefusal
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.LocalWardLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

// The Sort screen (S05, direction A, the sorting desk): capture pinned at the top, then the
// ideas with no folder (To sort), then the folders; a folder opens its own ideas and folders
// with a row back up. Each idea is filed by hand with the folder chooser, and each filing and
// delete offers Undo. Copied from Stethoscore's Ideas screen
// (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/app/ui/ideas/
// IdeasView.kt): FolderPrompt (:71-74), the open note in place of the list with its Back
// (:98-102), captureIdea (:107-113), the deleted folder's way back to the top (:96), the naming
// sheets (:154-158) and the capture field (:349-363); the list itself is in IdeasList.kt, which
// Map mode's list sheet shares. Cut: the search, the bottom bar and FloatingSwitcher, the Add
// menu, New page, the title plate, Board and Space (S05b), the kind glyphs and folder colours,
// "n linked" and "Start typing". The words are in strings.xml (the ux-writing pass of 30 Oct).

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
    /** Map mode with no ideas yet. */
    const val MAP_EMPTY = "map-empty"
}

/**
 * Sort and Map side by side (S05b checklist 9), as iOS's IdeasSplit: either side of an upright
 * separating fold, each on its side; else in a broad window (700 up, as iOS), the Sort screen
 * [SHARE] of the width and at least [LEAST_SORT], the map the rest. A fold lying flat (tabletop)
 * keeps one screen. [sort] and [gap] (the band) are dp; the map takes what is left.
 */
data class IdeasSplit(val sort: Double, val gap: Double) {
    companion object {
        const val SHARE = 0.4
        const val LEAST_SORT = 320.0
        /** The least a pane may be: one target across. */
        const val LEAST_PANE = 48.0

        fun of(shape: WindowShape): IdeasSplit? {
            val fold = shape.separatingFold
            if (fold != null) {
                if (fold.orientation == WardFold.Orientation.horizontal) return null
                val (sort, map) = shape.panes() ?: return null
                if (min(sort.width, sort.height) < LEAST_PANE || min(map.width, map.height) < LEAST_PANE) return null
                return IdeasSplit(sort.width, max(0.0, map.minX - sort.maxX))
            }
            if (!shape.isBroad) return null
            return IdeasSplit(max(shape.width * SHARE, LEAST_SORT), 0.0)
        }
    }
}

/** Where the screen starts: the top level, or in a folder, with the editor or the chooser open (the renders' way in). */
data class IdeasStart(val folderId: String? = null, val editing: String? = null, val choosing: String? = null)

/** The naming sheet, for a new folder (filing [filing] in it, from the chooser) or a renamed one. */
internal sealed class FolderPrompt {
    data class New(val parent: String?, val filing: String? = null) : FolderPrompt()
    data class Rename(val folder: NoteFolder) : FolderPrompt()
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
    /** Sort or Map: Sort at every launch (S05b checklist 1). */
    var mode by rememberSaveable { mutableStateOf(SortaMode.SORT) }
    val banner = remember { SnackbarHostState() }
    /** The foot's height: the banner and the switch, which the list and the map keep above. */
    var footHeight by remember { mutableIntStateOf(0) }
    var stripHeight by remember { mutableIntStateOf(0) }
    /** Map mode's list sheet is up, and the folder it is browsing (nil: the top level). */
    var listing by rememberSaveable { mutableStateOf(false) }
    var listFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = remember { FocusRequester() }
    val focus = LocalFocusManager.current

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
    LaunchedEffect(book.folders) {
        if (folderId != null && book.folder(folderId) == null) folderId = null
        if (listFolder != null && book.folder(listFolder) == null) listFolder = null
    }

    val open = editing?.takeIf { book.note(it) != null }
    val shape = currentWindowShape()
    BackHandler(enabled = (mode == SortaMode.SORT || IdeasSplit.of(shape) != null) && open == null && folderId != null) {
        folderId = book.folder(folderId)?.parentId
    }

    // the map is on screen: Map mode, with no idea open over it, or beside the Sort screen on two
    // panes; its 3D looks are night, and the chrome over them is too (iOS's IdeasMapView)
    val split = IdeasSplit.of(shape)
    val mapShown = mode == SortaMode.MAP && open == null
    val mapNight = book.notes.isNotEmpty() && settings.look != MapLook.TWO_D
    val night = split == null && mapShown && mapNight
    // the list sheet covers the map's foot, so the banner shows in it meanwhile
    val sheetUp = split == null && listing && mapShown && book.notes.isNotEmpty()
    val foot = with(LocalDensity.current) { footHeight.toDp() }
    // what the top strip covers, from the window's top, which the map's first view keeps under
    val stripTop = with(LocalDensity.current) { (if (book.notes.isNotEmpty()) stripHeight else 0).toDp() }
    val outer = LocalWardLook.current

    /** The map, edge to edge: the tools and the empty page keep above the foot ([hidden]) and the navigation bar. */
    @Composable fun MapPane(paned: Boolean) {
        val hidden = (if (paned) 0.dp else foot) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        IdeasMap(book, settings, hidden = hidden, open = { editing = it },
            openFolder = { folderId = it; if (!paned) mode = SortaMode.SORT },
            choose = { choosing = it }, delete = ::deleteIdea, top = stripTop)
        // the top strip: the To sort chip (not on two panes) and the look chip, night over the 3D looks
        if (book.notes.isNotEmpty()) SortaTheme(dark = mapNight || outer.dark, section = outer.section) {
            MapTopStrip(book, model.version, settings.look, choose = { settings.setLook(it) }, list = if (paned) null else ({ listing = true }),
                Modifier.onSizeChanged { stripHeight = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
        }
    }

    /** The Sort screen, or the editor open from it, in its column. */
    @Composable fun SortSide() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = IdeasLayout.column.dp).fillMaxSize()) {
                val bottom = foot + WardSpace.l.wardDp
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
            }
        }
    }

    /** The foot: the banner, then the Sort / Map switch ([switches]; not on two panes), bottom centre. */
    @Composable fun BoxScope.Foot(switches: Boolean) {
        SortaTheme(dark = night || outer.dark, section = outer.section) {
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = IdeasLayout.column.dp).fillMaxWidth()
                .onSizeChanged { footHeight = it.height }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (!sheetUp) SnackbarHost(banner, Modifier.fillMaxWidth().padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp)) { data ->
                    WardBanner(data.visuals.message, modifier = Modifier.semantics { testTag = IdeasTags.BANNER },
                        actionTitle = data.visuals.actionLabel, action = { data.performAction() })
                }
                if (switches && open == null) ModeSwitch(mode, { mode = it }, settings) { focus.clearFocus() }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background))) {
        if (split != null) Row(Modifier.fillMaxSize()) {
            // two panes (S05b checklist 9): the Sort screen leading with the banner at its foot,
            // the band (the page shows through), the map trailing; no switch, no list chip
            Box(Modifier.width(split.sort.dp).fillMaxHeight()) {
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) { SortSide() }
                Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { Foot(switches = false) }
            }
            if (split.gap > 0) Spacer(Modifier.width(split.gap.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) { MapPane(paned = true) }
        } else {
            if (mapShown) WardHingePane(shape, Modifier.fillMaxSize()) { MapPane(paned = false) }
            else WardHingePane(shape, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) { SortSide() }
            // the foot, in both modes the same: the banner, then the Sort / Map switch, bottom centre,
            // WardSpace.s above the navigation bar and the keyboard, in the page's side of a hinge
            WardHingePane(shape, Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { Foot(switches = true) }
        }
    }

    // first, so the chooser, the naming sheet and the dialog it opens come over it
    if (sheetUp) MapListSheet(book, model.version, banner, folderId = listFolder, setFolder = { listFolder = it }, onDone = { listing = false },
        open = { editing = it }, choose = { choosing = it }, delete = ::deleteIdea, name = { naming = it }, deleteFolder = { deletingFolder = it })
    choosing?.let { id ->
        val note = book.note(id)
        if (note == null) choosing = null
        else FolderChooser(book, note.folderId, version = model.version, onDismiss = { choosing = null },
            onChoose = { to -> choosing = null; file(id, to) },
            // a new folder goes in the folder being browsed: the sheet's, while it is up
            onNewFolder = { choosing = null; naming = FolderPrompt.New(parent = if (sheetUp) listFolder else folderId, filing = id) })
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

/**
 * The Sort / Map switch (S05b checklist 2): the kit's FloatingSwitcher with iOS's items and
 * identifiers, WardSpace.s above what is under it. Folded while the keyboard is up, whatever was
 * stored, and opening it then puts the keyboard away ([letGo]), so the strip has room, as
 * Stethoscore's Ideas bar did (IdeasBottomBar.swift:69-80).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ModeSwitch(mode: SortaMode, choose: (SortaMode) -> Unit, settings: IdeasSettings, letGo: () -> Unit) {
    val typing = WindowInsets.isImeVisible
    val items = SortaMode.entries.map { SwitcherItem(it, stringResource(it.title), it.icon, it.identifier, stringResource(it.spoken)) }
    Box(Modifier.padding(horizontal = WardSpace.gutter.wardDp).padding(bottom = WardSpace.s.wardDp)) {
        FloatingSwitcher(items, mode, choose, collapsed = settings.switcherCollapsed || typing,
            setCollapsed = { now -> if (!now && typing) letGo(); settings.setSwitcherCollapsed(now) }, toggleTag = SortaMode.TOGGLE)
    }
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

/** The page: the well pinned above the list (IdeasList.kt), the up row, the title row, the ideas, and Folders. */
@Composable private fun SortPage(
    book: IdeaBook, version: Int, folderId: String?, draft: String, onDraft: (String) -> Unit, capture: FocusRequester, bottom: Dp,
    onCapture: () -> Unit, onSortIt: () -> Unit, setFolder: (String?) -> Unit, open: (String) -> Unit, choose: (String) -> Unit,
    delete: (String) -> Unit, name: (FolderPrompt) -> Unit, deleteFolder: (NoteFolder) -> Unit, onImport: () -> Unit,
) {
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val hairline = wardColor(WardToken.hairline)
    // read again whenever the book changes: the book is changed in place, so [version] is what moves
    val level = rememberIdeasListLevel(book, version, folderId)
    val fresh = remember(book, version) { book.notes.isEmpty() && book.folders.isEmpty() }
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
            ideasList(book, level, inset, more, setFolder = setFolder, open = open, choose = choose, delete = delete,
                name = name, deleteFolder = deleteFolder)
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
            // two lines at most where it wraps: past them a long draft scrolls in the field, so Sort it
            // under the well stays above the keyboard (as on iOS, 080365f)
            singleLine = !ax, maxLines = if (ax) 2 else 1, interactionSource = source,
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
