package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.sources.ReaderDoneButton
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// Map mode's chrome (S05b checklists 3-5), as iOS's IdeasMapChrome.swift: the top strip with the
// "To sort n" chip and the look chip, the look chip's menu, and the list as a sheet over the map.

/** The tags iOS's identifiers give the same controls. */
object MapTags {
    const val LIST_CHIP = "map-list-chip"
    const val LIST = "map-list"
    const val LIST_DONE = "map-list-done"
}

/**
 * The top strip: leading, the "To sort n" chip, which opens the list sheet ([list]; null on two
 * panes, where the Sort screen is beside the map); trailing, the look chip. A gutter in from the
 * edges and WardSpace.s under the status bar; when one row can't hold both, they stack.
 */
@Composable fun MapTopStrip(book: IdeaBook, version: Int, look: MapLook, choose: (MapLook) -> Unit, list: (() -> Unit)?, modifier: Modifier = Modifier) {
    val gap = WardSpace.s.wardDp
    Layout({
        if (list != null) MapListChip(book, version, list)
        MapLookChip(look, choose)
    }, modifier.fillMaxWidth().padding(horizontal = WardSpace.gutter.wardDp).padding(top = gap)) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val oneRow = placeables.size < 2 || placeables.sumOf { it.width } + gapPx <= constraints.maxWidth
        val height = if (oneRow) placeables.maxOf { it.height } else placeables.sumOf { it.height } + gapPx
        layout(constraints.maxWidth, height) {
            if (oneRow) {
                // the list chip at the start, the look chip at the end
                placeables.forEachIndexed { i, p ->
                    val last = i == placeables.lastIndex
                    p.placeRelative(if (last) constraints.maxWidth - p.width else 0, 0)
                }
            } else {
                var y = 0
                placeables.forEach { p -> p.placeRelative(0, y); y += p.height + gapPx }
            }
        }
    }
}

/** "To sort" and the count of ideas in no folder, the count in the accent's ink, as the Sort screen's title. */
@Composable private fun MapListChip(book: IdeaBook, version: Int, open: () -> Unit) {
    val count = remember(book, version) { book.contents(null).size }
    val spoken = pluralStringResource(R.plurals.sort_title_spoken, count, count)
    val hint = stringResource(R.string.map_list_hint)
    WardChipButton(stringResource(R.string.sort_title), open, count = count, role = Role.Button,
        modifier = Modifier.semantics { contentDescription = spoken; testTag = MapTags.LIST_CHIP; onClick(hint) { open(); true } })
}

/**
 * The look's name and a chevron: the name on the map and the control in one. A tap opens the menu
 * of the four looks, a check on the chosen one; choosing is a cut, with no morph or cross-fade.
 */
@Composable fun MapLookChip(look: MapLook, choose: (MapLook) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = stringResource(look.title)
    val spoken = stringResource(R.string.look_chip_spoken, name)
    val hint = stringResource(R.string.look_chip_hint)
    Box {
        WardChipButton(name, { open = true }, role = Role.Button, trailingIcon = Icons.Outlined.KeyboardArrowDown,
            modifier = Modifier.semantics { contentDescription = spoken; testTag = MapLook.CHIP; onClick(hint) { open = true; true } })
        DropdownMenu(open, { open = false }, containerColor = wardColor(WardToken.background)) {
            MapLook.entries.forEach { item ->
                val chosen = item == look
                DropdownMenuItem(
                    text = { Text(stringResource(item.title), style = WardType.body.textStyle(), color = wardColor(WardToken.ink)) },
                    trailingIcon = if (chosen) {
                        { Icon(Icons.Outlined.Check, null, tint = wardColor(WardToken.primaryInk)) }
                    } else null,
                    onClick = { open = false; choose(item) },
                    modifier = Modifier.semantics { selected = chosen; testTag = item.identifier })
            }
        }
    }
}

/**
 * The list as a sheet over the map: S05's top level, its rows and folders, a folder opening in the
 * sheet with its row back up. The words open the editor, Choose folder the chooser, as on the Sort
 * screen ([open], [choose], [delete], [name], [deleteFolder] are its). Full height, the page's
 * colour, the bar's Done; the banner shows here while the sheet covers the map's ([banner]). The
 * folder it browses ([folderId]) is the screen's, so a folder made from the chooser goes in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MapListSheet(
    book: IdeaBook, version: Int, banner: SnackbarHostState, folderId: String?, setFolder: (String?) -> Unit, onDone: () -> Unit,
    open: (String) -> Unit, choose: (String) -> Unit, delete: (String) -> Unit,
    name: (FolderPrompt) -> Unit, deleteFolder: (NoteFolder) -> Unit,
) {
    val level = rememberIdeasListLevel(book, version, folderId)
    val inset = WardSpace.s.wardDp
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        ReaderNavBar(stringResource(R.string.sort_title), trailing = {
            Box(Modifier.semantics { testTag = MapTags.LIST_DONE }) { ReaderDoneButton(onClick = onDone) }
        })
        Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = IdeasLayout.column.dp).fillMaxWidth().semantics { testTag = MapTags.LIST },
                contentPadding = PaddingValues(start = inset, end = inset, top = WardSpace.xs.wardDp, bottom = WardSpace.l.wardDp)) {
                ideasList(book, level, inset, more = {}, setFolder = setFolder, open = open, choose = choose, delete = delete,
                    name = name, deleteFolder = deleteFolder)
            }
        }
        SnackbarHost(banner, Modifier.fillMaxWidth().padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp)) { data ->
            WardBanner(data.visuals.message, modifier = Modifier.semantics { testTag = IdeasTags.BANNER },
                actionTitle = data.visuals.actionLabel, action = { data.performAction() })
        }
    }
}
