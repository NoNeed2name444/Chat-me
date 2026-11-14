package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeaLens
import com.cramdown.sorta.ui.ward.WardChipButton
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.wardDp
import java.time.ZoneId

// The lenses (S06d A1-A3, plugins-plan idea 8), as iOS's MapLensRow: four chips under Map mode's top
// strip, each a question asked of every idea. A lens on shows only the ideas that pass; two on, only
// those that pass both. Held while the screen is, never stored, as the map's filter.

/** The row's and each chip's tags, as iOS's identifiers. */
object MapLensTags {
    const val ROW = "map-lens-row"
    fun chip(lens: IdeaLens) = "map-lens-${lens.id}"
}

/** A lens's name, and what a tap on it does while it is off. */
internal val IdeaLens.title: Int get() = when (this) {
    IdeaLens.QUESTIONS -> R.string.lens_questions
    IdeaLens.LATE_NIGHT -> R.string.lens_late_night
    IdeaLens.UNFINISHED -> R.string.lens_unfinished
    IdeaLens.ALONE -> R.string.lens_alone
}
internal val IdeaLens.hint: Int get() = when (this) {
    IdeaLens.QUESTIONS -> R.string.lens_questions_hint
    IdeaLens.LATE_NIGHT -> R.string.lens_late_night_hint
    IdeaLens.UNFINISHED -> R.string.lens_unfinished_hint
    IdeaLens.ALONE -> R.string.lens_alone_hint
}

/** The upper-case ids the lenses [on] let through in [book], null with none on ([IdeaLens.passing]). */
@Composable fun rememberLensPass(book: IdeaBook, version: Int, on: Set<IdeaLens>): Set<String>? =
    remember(book, version, book.notes, on) { IdeaLens.passing(on, book.notes, book.allEdges(), ZoneId.systemDefault()) }

/**
 * The four chips in a row a gutter in from the edges and WardSpace.s under the strip, scrolling
 * sideways where they don't fit. Each is the kit's chip with its chosen state and its count (how
 * many ideas show with it on); TalkBack reads its name, count and whether it is checked.
 */
@Composable fun MapLensRow(book: IdeaBook, version: Int, on: Set<IdeaLens>, toggle: (IdeaLens) -> Unit, modifier: Modifier = Modifier) {
    val counts = remember(book, version, book.notes, on) { IdeaLens.counts(on, book.notes, book.allEdges(), ZoneId.systemDefault()) }
    val name = stringResource(R.string.map_lenses)
    val gap = WardSpace.s.wardDp
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .semantics { isTraversalGroup = true; contentDescription = name; testTag = MapLensTags.ROW }
        .padding(horizontal = WardSpace.gutter.wardDp).padding(top = gap),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        IdeaLens.entries.forEach { lens ->
            val chosen = lens in on
            val count = counts[lens] ?: 0
            val title = stringResource(lens.title)
            val spoken = pluralStringResource(R.plurals.lens_spoken, count, title, count)
            val hint = stringResource(if (chosen) R.string.lens_off_hint else lens.hint)
            WardChipButton(title, { toggle(lens) }, on = chosen, count = count,
                modifier = Modifier.semantics { contentDescription = spoken; testTag = MapLensTags.chip(lens); onClick(hint) { toggle(lens); true } })
        }
    }
}
