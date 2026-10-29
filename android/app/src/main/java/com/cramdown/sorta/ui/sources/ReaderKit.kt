package com.cramdown.sorta.ui.sources

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ui.mock.StudyTitleBar
import com.cramdown.sorta.ui.ward.WardButtonKind
import com.cramdown.sorta.ui.ward.WardCircleButton
import com.cramdown.sorta.ui.ward.WardLift
import com.cramdown.sorta.ui.ward.WardRadius
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WardTextButton
import com.cramdown.sorta.ui.ward.wardDp
import com.cramdown.sorta.ui.ward.wardRelief

// The parts of Stethoscore's ui/sources/ReaderKit.kt that the Ward kit and
// Sorta's Ideas screens use: the navigation bar with its Back or Done, and a
// tiled row. The rest of the file (the study header, the lecture kinds, the
// reading card) belongs to study and stays with Stethoscore.

/** The navigation bar, StudyTitleBar: its title inline, Back or Done at the leading end, an action at the trailing one. */
@Composable internal fun ReaderNavBar(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = StudyTitleBar(title, null, leading = leading) { trailing?.invoke() }

/** Back, to the page before: a Ward circle, as iOS's toolbar buttons (wardCircle). */
@Composable internal fun ReaderBackButton(onBack: () -> Unit) {
    WardCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ward_back), onBack)
}

/** A sheet's Done (wardCompact). */
@Composable internal fun ReaderDoneButton(title: String? = null, onClick: () -> Unit) {
    WardTextButton(title ?: stringResource(R.string.ward_done), onClick, kind = WardButtonKind.compact, fills = false)
}

/**
 * A List or Form row as a soft tile raised low off the base, with a
 * control's ink edge (wardRowBackground, WardRowTile): 8 clear of the
 * cell's edges, the content 8 above and below and 12 at the sides inside
 * it. Pressed in ([inset]), it marks the chosen row.
 */
@Composable internal fun Modifier.wardRowTile(inset: Boolean = false): Modifier =
    this.padding(WardSpace.s.wardDp)
        .wardRelief(RoundedCornerShape(WardRadius.field.wardDp), WardLift.low, inset = inset, control = true)
        .padding(horizontal = 12.dp, vertical = 8.dp)
