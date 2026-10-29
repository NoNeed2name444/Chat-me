package com.cramdown.sorta.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R

// A port of ios/RedPen/Shared/Ward/WardStatesGallery.swift: every
// Ward control in each of its states, for one section, drawn with the real
// controls (WardControls). Three columns: at rest, held (drawn held through
// LocalWardShowsPressed, with no finger on it) and disabled. A control that
// can be chosen has two rows, not chosen and chosen, so its six states
// (WardControlStates) are all on one screen. WardStatesTest checks the same
// states' colours for every section and mode on the JVM.

/** A column of the gallery: the state its controls are drawn in. */
enum class WardGalleryColumn(val title: Int) {
    rest(R.string.ward_gallery_rest), held(R.string.ward_gallery_held), disabled(R.string.ward_gallery_disabled)
}

@Composable fun WardStatesGallery(section: WardSection, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.s.wardDp),
    ) {
        WardSmallCapsText(stringResource(R.string.ward_gallery_heading, stringResource(section.galleryTitle)), Modifier.semantics { heading() }.padding(bottom = WardSpace.s.wardDp))
        Row(Modifier.fillMaxWidth().padding(bottom = WardSpace.m.wardDp), horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            for (column in WardGalleryColumn.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WardSmallCapsText(stringResource(column.title)) }
            }
        }
        Group {
            States { e -> WardTextButton(stringResource(R.string.ward_gallery_primary), {}, kind = WardButtonKind.primary, enabled = e) }
            States { e -> WardTextButton(stringResource(R.string.ward_gallery_secondary), {}, kind = WardButtonKind.secondary, enabled = e) }
            States { e -> WardTextButton(stringResource(R.string.ward_gallery_destructive), {}, kind = WardButtonKind.destructive, enabled = e) }
            States { e -> WardTextButton(stringResource(R.string.ward_done), {}, kind = WardButtonKind.compact, enabled = e) }
            States { e -> WardTextButton(stringResource(R.string.ward_gallery_quiet), {}, kind = WardButtonKind.quiet, enabled = e) }
        }
        Group {
            States { e -> ChipAndCircle(chosen = false, enabled = e) }
            States { e -> ChipAndCircle(chosen = true, enabled = e) }
        }
        Group {
            States { e -> val name = stringResource(R.string.ward_gallery_toggle)
                WardToggle(false, {}, enabled = e, modifier = Modifier.semantics { contentDescription = name }) }
            States { e -> val name = stringResource(R.string.ward_gallery_toggle)
                WardToggle(true, {}, enabled = e, modifier = Modifier.semantics { contentDescription = name }) }
            States { e ->
                WardSegmented(0, listOf(0, 1), {}, enabled = e) { option, _, color ->
                    Icon(if (option == 0) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
                        stringResource(if (option == 0) R.string.ward_gallery_list else R.string.ward_gallery_grid), tint = color)
                }
            }
        }
        Group(last = true) {
            States { e -> WardTile({}, chosen = false, enabled = e) { WardTileLabel(stringResource(section.galleryTitle), section.galleryIcon, e) } }
            States { e -> WardTile({}, chosen = true, enabled = e) { WardTileLabel(stringResource(section.galleryTitle), section.galleryIcon, e) } }
        }
    }
}

@Composable private fun Group(last: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = if (last) 0.dp else WardSpace.l.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
        content()
    }
}

/** One row: the control at rest, held and disabled, a column each. */
@Composable private fun States(control: @Composable (enabled: Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalAlignment = Alignment.CenterVertically) {
        for (column in WardGalleryColumn.entries) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalWardShowsPressed provides (column == WardGalleryColumn.held)) {
                    control(column != WardGalleryColumn.disabled)
                }
            }
        }
    }
}

/**
 * A chip that is a button and a round button, chosen or not. They flow, so
 * where a column is too narrow for both (a folded phone, 360 dp) the circle
 * takes the next line instead of being squeezed out of round.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ChipAndCircle(chosen: Boolean, enabled: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        WardChipButton(stringResource(R.string.ward_gallery_chip), {}, on = chosen, enabled = enabled)
        WardCircleButton(Icons.Rounded.Add, stringResource(R.string.ward_gallery_add), {}, on = chosen, enabled = enabled)
    }
}

/** The section's name, for the gallery's heading and its tile. */
val WardSection.galleryTitle: Int get() = when (this) {
    WardSection.sorta -> R.string.app_name
}

/** The section's glyph: a light bulb, for ideas. */
val WardSection.galleryIcon: ImageVector get() = when (this) {
    WardSection.sorta -> Icons.Rounded.Lightbulb
}
