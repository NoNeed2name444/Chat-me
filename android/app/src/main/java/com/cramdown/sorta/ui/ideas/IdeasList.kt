package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// The Sort screen's list (S05), shared by the Sort screen and Map mode's list sheet (S05b,
// question 1): the up row, the title row, the ideas, and Folders with their rows. Moved here
// unchanged from IdeasView.kt; copied from Stethoscore's Ideas screen
// (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/app/ui/ideas/
// IdeasView.kt): the list's up row, folder rows and note rows (:205-303, the note row cut to its
// first line) and SwipeRow (:305-323).

/** What one level of the list shows: [current] is the open folder (none at the top level). */
@Immutable data class IdeasListLevel(val current: NoteFolder?, val here: List<Note>, val subfolders: List<NoteFolder>, val count: Int)

/** The level at [folderId], read again whenever the book changes ([version] is what moves, the book being changed in place). */
@Composable fun rememberIdeasListLevel(book: IdeaBook, version: Int, folderId: String?): IdeasListLevel =
    remember(book, version, folderId) {
        val current = book.folder(folderId)
        val here = book.contents(folderId)
        // a folder's count takes in its nested folders' ideas, as its row's does; To sort's, the ideas without a folder
        IdeasListLevel(current, here, book.subfolders(folderId), current?.let { book.count(it.id) } ?: here.size)
    }

/**
 * The list at [level], as items of a LazyColumn whose content is inset by [inset]: the up row
 * inside a folder, the title row with [more] at its end, the ideas (or the line saying there are
 * none), then Folders.
 */
internal fun LazyListScope.ideasList(
    book: IdeaBook, level: IdeasListLevel, inset: Dp, more: @Composable () -> Unit,
    setFolder: (String?) -> Unit, open: (String) -> Unit, choose: (String) -> Unit, delete: (String) -> Unit,
    name: (FolderPrompt) -> Unit, deleteFolder: (NoteFolder) -> Unit,
) {
    val current = level.current
    if (current != null) item(key = "up") {
        val parent = book.folder(current.parentId)?.name ?: stringResource(R.string.sort_title)
        UpRow(parent) { setFolder(current.parentId) }
    }
    item(key = "title") { TitleRow(current?.name, level.count, more) }
    if (level.here.isEmpty()) item(key = "none") {
        Text(stringResource(if (current != null) R.string.folder_empty else R.string.sort_all_sorted),
            Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = WardSpace.s.wardDp),
            style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
    } else items(level.here, key = { it.id }) { note ->
        IdeaRow(note, open = { open(note.id) }, choose = { choose(note.id) }, delete = { delete(note.id) })
    }
    if (level.subfolders.isNotEmpty()) {
        item(key = "folders") {
            WardSectionLabel(stringResource(R.string.sort_folders),
                Modifier.padding(horizontal = inset).padding(top = WardSpace.l.wardDp, bottom = WardSpace.xs.wardDp))
        }
        items(level.subfolders, key = { "f-" + it.id }) { folder ->
            FolderRow(folder, book.count(folder.id), open = { setFolder(folder.id) },
                rename = { name(FolderPrompt.Rename(folder)) }, newInside = { name(FolderPrompt.New(folder.id)) },
                delete = { deleteFolder(folder) })
        }
    }
}

/** An idea's first line, as its row shows it: the title's, else the details', else "Untitled". */
@Composable internal fun ideaLine(note: Note): String {
    fun first(text: String) = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    return first(note.title) ?: first(note.body) ?: stringResource(R.string.idea_untitled)
}

/** "To sort 3", or the open folder's name and count, as a header; the More circle at its end. */
@Composable internal fun TitleRow(folder: String?, count: Int, more: @Composable () -> Unit) {
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
    // left mid-sentence (S06a): a dotted edge, and TalkBack says so after its words
    val marked = note.stoppedAt != null
    val unfinished = stringResource(R.string.deck_unfinished)
    SwipeRow(lead = chooseWords to choose, trail = deleteWords to delete) {
        Box {
            @Composable fun Words(modifier: Modifier) {
                Box(modifier.heightIn(min = WardControlSize.target.wardDp)
                    .combinedClickable(role = Role.Button, onClickLabel = hint, onLongClick = { menu = true }, onClick = open)
                    .semantics { customActions = actions; testTag = IdeasTags.IDEA_LINE; if (marked) stateDescription = unfinished },
                    contentAlignment = Alignment.CenterStart) {
                    Text(line, style = WardType.body.textStyle(), color = wardColor(WardToken.ink), maxLines = if (ax) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
            @Composable fun Choose(modifier: Modifier) {
                WardTextButton(chooseWords, choose, modifier.semantics { contentDescription = chooseSpoken }, kind = WardButtonKind.compact, fills = false)
            }
            if (ax) Column(Modifier.fillMaxWidth().unfinishedEdge(marked).wardRowTile(), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                Words(Modifier.fillMaxWidth())
                Choose(Modifier)
            } else Row(Modifier.fillMaxWidth().unfinishedEdge(marked).wardRowTile(), verticalAlignment = Alignment.CenterVertically,
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
