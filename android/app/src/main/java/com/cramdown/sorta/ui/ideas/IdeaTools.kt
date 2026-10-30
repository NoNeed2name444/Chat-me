package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.res.stringResource
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.GraphLineStyle
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.sources.wardRowTile
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// The canvas tools (IdeaTools.swift, fed0105): the Board's (Look, Connect,
// Recentre) and the Space's, a vertical cluster of 44-point circles at the
// bottom-trailing corner (the board's on the trailing edge, centred, in a broad
// window, as iOS's IdeaToolsPlacement), each raised off the base on the floating
// plane, pressed in while held or while the tool is on, its glyph the section's
// accent then and Biro Grey at rest. Also the naming sheet (NameSheet), the board's
// "Move to" (MoveSheet), and the Sort screen's folder chooser (FolderChooser).

/** A vertical cluster of tool circles, 16 apart, each with its own relief. */
@Composable fun IdeaToolCluster(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(WardSpace.gutter.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.l.wardDp), content = content)
}

/** The Lines picker's width in a menu, on both platforms (iOS IdeaLinesPicker.segmentedWidth). */
const val LINES_PICKER_WIDTH = 200

/** A tool's glyph ink: the accent while on, Biro Grey at rest. */
@Composable fun ideaToolInk(active: Boolean) = wardColor(if (active) WardToken.primaryInk else WardToken.inkSecondary)

/** One round tool: [active] presses it in and turns its glyph the accent. */
@Composable fun IdeaToolButton(icon: ImageVector, label: String, active: Boolean = false, modifier: Modifier = Modifier,
                               value: String? = null, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    // a 44 circle (WardControlSize.buttonSmall, iOS's tool) in Android's 48 target, a control's face
    // as iOS's (WardPressFace): on its small rim with the section's ink edge. The reference's
    // Android drew the wide shade with no edge, which read as a gradient and, at night, as the page.
    // The kit's focus ring, as WardCircleButton's
    Box(modifier.minimumInteractiveComponentSize().size(WardControlSize.buttonSmall.wardDp).capPop(held)
        .wardFocusRing(CircleShape, focused, wardColor(WardControlStates.focusInk))
        .wardRelief(pressed = active || held, radius = WardControlSize.buttonSmall / 2, lift = WardLift.high, fill = WardToken.surface)
        .clickable(source, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; selected = active; if (value != null) stateDescription = value },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = ideaToolInk(active), modifier = Modifier.size(wardGlyph(20.0)))
    }
}

/**
 * "Lines: Curved / Straight" (IdeaLinesPicker): two segments, the same stored choice as the
 * 3D map's Look menu.
 */
@Composable fun IdeaLinesPicker(settings: IdeasSettings, modifier: Modifier = Modifier) {
    WardSegmented(settings.lineStyle, GraphLineStyle.entries, { settings.setStraightLines(it.isStraight) },
        modifier.semantics { contentDescription = "Lines" }) { style, text, ink ->
        Text(style.title, style = text, color = ink, modifier = Modifier.semantics { testTag = "lookLines-" + style.rawValue })
    }
}

/** The board's Look tool: a small popover beside the tools with Lines in it. */
@Composable fun IdeaBoardLookTool(settings: IdeasSettings) {
    var showing by remember { mutableStateOf(false) }
    val straight = settings.lineStyle.isStraight
    Box {
        IdeaToolButton(Icons.Outlined.Palette, "Look", active = straight, value = if (straight) "Straight lines" else "Curved lines") { showing = true }
        DropdownMenu(showing, { showing = false }, containerColor = wardColor(WardToken.background)) {
            // the picker 200 wide inside its 12 around, as iOS's popover
            IdeaLinesPicker(settings, Modifier.padding(WardSpace.m.wardDp).width(LINES_PICKER_WIDTH.dp))
        }
    }
}

/** A sheet asking for one name (NameSheet): Cancel, the field, and the confirming word. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NameSheet(title: String, prompt: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val blank = name.isBlank()
    fun submit() { if (!blank) { onConfirm(name); onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        ReaderNavBar(title,
            leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, kind = WardButtonKind.quiet, fills = false) },
            trailing = { WardTextButton(confirm, ::submit, kind = WardButtonKind.compact, fills = false, enabled = !blank) })
        // the kit's field (WardTextField), which takes no keyboard action: its 48 target and focus ring
        Box(Modifier.padding(WardSpace.gutter.wardDp)) {
            androidx.compose.foundation.text.BasicTextField(name, { name = it },
                Modifier.fillMaxWidth().focusRequester(focus).minimumInteractiveComponentSize()
                    .wardFocusRing(RoundedCornerShape(WardRadius.field.wardDp), focused, wardColor(WardControlStates.focusInk))
                    .semantics { contentDescription = prompt },
                singleLine = true, interactionSource = source, textStyle = WardType.body.textStyle().copy(color = wardColor(WardToken.ink)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(wardColor(WardToken.primaryInk)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().wardField(), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text(prompt, style = WardType.body.textStyle(), color = wardDisabledInk())
                        inner()
                    }
                })
        }
        // room under the field so the keyboard doesn't cover it, not a gap between pieces
        Spacer(Modifier.height(120.dp))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** "Move to": No folder, then every folder indented by depth (IdeasView.moveChoices). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MoveSheet(book: IdeaBook, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = wardColor(WardToken.background)) {
        ReaderNavBar("Move to", leading = { WardTextButton("Cancel", onDismiss, kind = WardButtonKind.quiet, fills = false) })
        val choices: List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "No folder") +
            book.folderOutline().map { (folder, depth) -> folder.id to IdeaBook.indented(folder.name, depth) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(choices) { (id, name) ->
                Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { onMove(id); onDismiss() }.wardRowTile()) {
                    Text(name, Modifier.padding(vertical = WardSpace.s.wardDp), style = WardType.body.textStyle(), color = wardColor(WardToken.primaryInk),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(WardSpace.xl.wardDp))
    }
}

/** The test tag on the folder chooser's content. */
const val FOLDER_CHOOSER_TAG = "folder-chooser"

/**
 * The folder chooser (S05): Stethoscore's move choices (reference/stethoscore-aec2af1c/ios/RedPen/
 * Features/Notes/IdeasView.swift:380-392, moveChoices and indented; Android ui/ideas/IdeasView.kt:159
 * and MoveSheet) as a sheet. To sort first, which takes the idea out of its folder; then every folder,
 * nested, in the store's outline; then New folder, which [onNewFolder] names, makes and files the idea
 * in. The row the idea is in now ([current]) has a checkmark and is selected. The person picks: nothing is
 * suggested. MoveSheet stays as it is for the board. [version] is the book's (IdeasModel.version), so
 * the sheet follows its changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FolderChooser(book: IdeaBook, current: String?, version: Int = 0, onDismiss: () -> Unit, onChoose: (String?) -> Unit, onNewFolder: () -> Unit) {
    // all the way up, so a short window shows every row it can rather than half a sheet
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        Column(Modifier.fillMaxWidth().semantics { testTag = FOLDER_CHOOSER_TAG }) {
            ReaderNavBar(stringResource(R.string.chooser_title),
                leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, kind = WardButtonKind.quiet, fills = false) })
            val toSort = stringResource(R.string.chooser_to_sort)
            val toSortSpoken = stringResource(R.string.chooser_to_sort_spoken)
            val newFolder = stringResource(R.string.chooser_new_folder)
            val newHint = stringResource(R.string.chooser_new_folder_hint)
            val outline = remember(book, version) { book.folderOutline() }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = IdeasLayout.chooserMaxHeight.dp)) {
                item { ChooserRow(toSort, chosen = current == null, spoken = toSortSpoken) { onChoose(null) } }
                items(outline, key = { it.first.id }) { (folder, depth) ->
                    val inside = book.folder(folder.parentId)?.name?.let { stringResource(R.string.chooser_inside, it) }
                    ChooserRow(IdeaBook.indented(folder.name, depth), chosen = current == folder.id, spoken = folder.name, value = inside) {
                        onChoose(folder.id)
                    }
                }
                item { ChooserRow(newFolder, chosen = false, spoken = newFolder, hint = newHint, icon = Icons.Outlined.CreateNewFolder, onClick = onNewFolder) }
            }
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}

/**
 * One row of the chooser: a tile, with a checkmark in primaryInk when it is where the idea is now
 * (iOS FolderChooser's). An action row ([icon], New folder) has its glyph and words in primaryInk.
 */
@Composable private fun ChooserRow(text: String, chosen: Boolean, spoken: String, value: String? = null, hint: String? = null,
                                   icon: ImageVector? = null, onClick: () -> Unit) {
    val ink = wardColor(if (icon != null) WardToken.primaryInk else WardToken.ink)
    Row(Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
        .wardRowTile()
        .semantics(mergeDescendants = true) {
            contentDescription = spoken
            selected = chosen
            if (value != null) stateDescription = value
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(wardGlyph(20.0)))
        Text(text, Modifier.weight(1f), style = WardType.body.textStyle(), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (chosen) Icon(Icons.Rounded.Check, null, tint = wardColor(WardToken.primaryInk), modifier = Modifier.size(wardGlyph(20.0)))
    }
}
