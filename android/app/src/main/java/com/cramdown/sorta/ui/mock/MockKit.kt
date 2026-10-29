package com.cramdown.sorta.ui.mock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ui.wardColor
import com.cramdown.sorta.ui.ward.WardCircleButton
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WardType
import com.cramdown.sorta.ui.ward.textStyle
import com.cramdown.sorta.ui.ward.wardDp

// StudyTitleBar from Stethoscore's ui/mock/MockKit.kt, the navigation bar
// every screen shares. The rest of that file (the mock exam's parts) belongs
// to study and stays with Stethoscore.

/**
 * The navigation bar, every screen's (iOS's inline title with Ward toolbar
 * buttons): back as a Ward circle (or [leading]), the title in the middle in
 * the headline face, [trailing] at the end. The title stays in the bar's
 * middle: it takes the room the wider end leaves it on both sides, with an
 * ellipsis when that is too little. [inset] is the room at the bar's sides,
 * none where the page's own column already keeps its gutter (ScreenHeader).
 * ReaderNavBar and ScreenHeader are this bar.
 */
@Composable internal fun StudyTitleBar(title: String, onBack: (() -> Unit)?, leading: (@Composable () -> Unit)? = null,
                                       inset: Dp = WardSpace.m.wardDp, trailing: @Composable RowScope.() -> Unit = {}) {
    // 52 tall: the back circle's touch area (the least target) fits inside it
    val back = stringResource(R.string.ward_back)
    Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) leading()
                else if (onBack != null) WardCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, back, onBack)
            }
            Text(title, Modifier.semantics { heading() }, style = WardType.headline.textStyle(), color = wardColor(WardToken.ink),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), content = trailing)
        },
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = inset),
    ) { (start, name, end), constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val ends = listOf(start.measure(free), end.measure(free))
        val widest = ends.maxOf { it.width }
        val side = if (widest == 0) 0 else widest + WardSpace.s.wardDp.roundToPx()
        val words = name.measure(free.copy(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)))
        val height = maxOf(constraints.minHeight, words.height, ends.maxOf { it.height })
        layout(constraints.maxWidth, height) {
            ends[0].place(0, (height - ends[0].height) / 2)
            words.place((constraints.maxWidth - words.width) / 2, (height - words.height) / 2)
            ends[1].place(constraints.maxWidth - ends[1].width, (height - ends[1].height) / 2)
        }
    }
}
