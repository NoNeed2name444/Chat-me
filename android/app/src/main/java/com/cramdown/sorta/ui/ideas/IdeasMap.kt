package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor

// Map mode (S05b checklist 1), as iOS's IdeasMapView: the ideas on a full-screen map in the
// look chosen (MapLook), edge to edge, with what the foot and the navigation bar cover as
// [hidden], which the map's tools keep above. The 3D looks are night (IdeasSpace); 2D is the
// board on the page, clear of the status bar and any cutout, as in MapPreview. With no ideas,
// the page's own empty state, with no tools and no night (checklist 8).

/**
 * Map mode's view of [book]: [open] opens an idea in S05's editor, [openFolder] a folder in Sort,
 * [choose] the folder chooser, [delete] deletes with S05's undo. [top] is what the top strip covers,
 * from the window's top: the first view and the no-folder hint keep under it. [version] is the
 * book's (IdeasModel.version): the book changes in place, so the map is redrawn by it, not by the book.
 * [lens]: the upper-case ids the lenses on let through (S06d), null with none on.
 */
@Composable fun IdeasMap(
    book: IdeaBook, settings: IdeasSettings, hidden: Dp, open: (String) -> Unit, openFolder: (String?) -> Unit,
    choose: (String) -> Unit = {}, delete: (String) -> Unit = {}, top: Dp = 0.dp, version: Int = 0,
    lens: Set<String>? = null,
) {
    when {
        book.notes.isEmpty() -> MapEmpty(hidden)
        settings.look == MapLook.TWO_D ->
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                // the board is already clear of the status bar, so only the strip below it is taken off
                val inset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
                IdeaBoard(book, settings, hidden = hidden, open = open, top = (top - inset).coerceAtLeast(0.dp), version = version, lens = lens)
            }
        else -> IdeasSpace(book, settings, covered = false, hidden = hidden, open = open, openFolder = openFolder,
            choose = choose, delete = delete, top = top, version = version, lens = lens)
    }
}

/** No ideas yet: the outlined cube and words saying ideas are added in Sort, on the page's colours. */
@Composable private fun MapEmpty(hidden: Dp) {
    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background))
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(bottom = hidden),
        contentAlignment = Alignment.Center) {
        WardEmptyState(Icons.Outlined.ViewInAr, stringResource(R.string.map_empty_title), stringResource(R.string.map_empty_line),
            modifier = Modifier.widthIn(max = IdeasLayout.column.dp).padding(horizontal = WardSpace.gutter.wardDp)
                .semantics(mergeDescendants = true) { testTag = IdeasTags.MAP_EMPTY })
    }
}
