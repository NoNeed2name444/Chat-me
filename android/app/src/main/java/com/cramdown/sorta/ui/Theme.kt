package com.cramdown.sorta.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.ui.ward.*

// Every colour comes from the Ward core (ui/ward: WardPalette, WardSection,
// WardLook), the port of the iOS Ward look: the palette's tokens for the
// grounds, text and meanings, and the section's ink, accent and fill for the
// accents. Material 3 is fed a scheme built from them, so a screen that reads
// MaterialTheme.colorScheme gets the Ward colours; wardColor(token) and
// wardInk() give the rest.
//
// How the tokens land in Material: the tint (primary) is what iOS's
// AccentColor is, the section's fill by day and its accent at night (so text
// buttons and icons read 4.5:1 on every tier); primaryContainer is the fill
// under words, and onPrimary the words on it (white by day, aubergine at
// night, docs/scoping.md section 5). Material's secondary and tertiary are
// the same fill: Sorta has one coral. outline is the section's ink at a
// control's edge strength, as it lands on the base (WardRelief.edgeColor).
// Sheets, menus and dialogs stay Material's own and stand in the page colour
// (the owner, 22 Oct: one tone covers them): the slots their containers take
// are the base's colour, and Material's tonal elevation is off, since with a
// clear surfaceTint it would lay black over a sheet. Every touch area is at
// least WardControlSize.target (48 on Android): Material's controls and
// minimumInteractiveComponentSize read it from here.
val LocalWardDark = staticCompositionLocalOf { false }
val LocalWardLook = staticCompositionLocalOf { WardLook(dark = false, section = WardSection.outside) }

/** A 0xRRGGBB colour as an opaque Compose colour. */
fun wardRgb(hex: Int): Color = Color(0xFF000000.toInt() or hex)

/** The Material scheme for [look]: every slot from the Ward palette and the section. */
fun wardColorScheme(look: WardLook): ColorScheme {
    val dark = look.dark
    val glide = WardGlide.rest(look)
    fun c(token: WardToken) = wardRgb(glide.hex(token, dark))
    val tint = wardRgb(if (dark) look.section.accent(true) else look.section.fill(false))
    val onTint = c(WardToken.onPrimary)
    val edge = wardRgb(WardRelief.edgeColor(look.section, dark))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tint, onPrimary = onTint,
        primaryContainer = c(WardToken.primary), onPrimaryContainer = c(WardToken.onPrimary),
        inversePrimary = c(WardToken.primaryInk),
        secondary = c(WardToken.primary), onSecondary = c(WardToken.onPrimary),
        tertiary = c(WardToken.primary), onTertiary = c(WardToken.onPrimary),
        background = c(WardToken.background), onBackground = c(WardToken.ink),
        surface = c(WardToken.surface), onSurface = c(WardToken.ink),
        surfaceVariant = c(WardToken.well), onSurfaceVariant = c(WardToken.inkSecondary),
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = c(WardToken.well), surfaceContainerLow = c(WardToken.background),
        surfaceContainer = c(WardToken.surface), surfaceContainerHigh = c(WardToken.raised),
        surfaceContainerHighest = c(WardToken.raised),
        surfaceBright = c(WardToken.raised), surfaceDim = c(WardToken.well),
        error = c(WardToken.danger), onError = c(WardToken.onPrimary),
        outline = edge, outlineVariant = c(WardToken.hairline),
    )
}

/** A Ward palette colour for the look on show (the section's, for primary and primaryInk). */
@Composable fun wardColor(token: WardToken, held: Boolean = false): Color {
    val look = LocalWardLook.current
    val dark = if (held) true else look.dark
    // this frame of the section's glide (rememberWardGlide), or the look at rest
    val glide = LocalWardGlide.current ?: WardGlide.rest(look)
    return wardRgb(glide.hex(token, dark, held))
}

/** The section's ink on show: the grid's lines and the controls' edges. */
@Composable fun wardInk(): Color {
    val look = LocalWardLook.current
    val glide = LocalWardGlide.current ?: WardGlide.rest(look)
    return wardRgb(glide.ink(look.dark))
}

@Composable fun SortaTheme(dark:Boolean=false, section:WardSection=WardSection.outside, content:@Composable ()->Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        // the bars' icons for this look while it is up, and back as they were when it goes: a
        // theme inside another (the 3D maps' night, IdeasSpace) holds them while it is shown
        DisposableEffect(view, dark) {
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()
            val bars = activity?.let { WindowCompat.getInsetsController(it.window, view) }
            val before = bars?.let { it.isAppearanceLightStatusBars to it.isAppearanceLightNavigationBars }
            bars?.apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            onDispose {
                if (bars != null && before != null) {
                    bars.isAppearanceLightStatusBars = before.first
                    bars.isAppearanceLightNavigationBars = before.second
                }
            }
        }
    }
    val look = WardLook(dark = dark, section = section)
    // the one animated value: light and dark in one step, a section's hue glides (WardLookDriver)
    val glide = rememberWardGlide(look)
    SideEffect { WardLookNow.shared.set(glide) }
    // whether animations are off (Remove animations), watched once for every surface that moves (wardReduceMotion)
    val reduceMotion = rememberWardReduceMotion()
    CompositionLocalProvider(LocalWardDark provides dark, LocalWardLook provides look, LocalWardGlide provides glide,
        LocalWardReduceMotion provides reduceMotion,
        LocalTonalElevationEnabled provides false, LocalMinimumInteractiveComponentSize provides WardControlSize.target.wardDp) {
        MaterialTheme(colorScheme=wardColorScheme(look), typography=wardTypography()) {
            // a plain clickable sinks and shades as a Ward face does, never a ripple (iOS PressableRowStyle)
            CompositionLocalProvider(LocalIndication provides WardPressIndication, content=content)
        }
    }
}

/** A card (iOS wardCard): raised mid off the base by WardRelief's two lights, iOS's card radius and gutter (WardSurfaces). */
@Composable fun WardCard(modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit) = WardCardFace(modifier, content=content)
/** A Ward button (ui/ward/WardControls): a soft capsule raised off the base, its kind in the label's colour. */
@Composable fun WardButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,kind:WardButtonKind=WardButtonKind.secondary) {
    WardTextButton(text,onClick,modifier,kind=kind,enabled=enabled)
}
/** A section's small-caps label (WardSmallCaps). */
@Composable fun SectionLabel(text:String) { WardSmallCapsText(text) }
/** A page's top inside its own column: the one bar (StudyTitleBar, no inset: the column keeps the gutter), then its subtitle. */
@Composable fun ScreenHeader(title:String,subtitle:String,onBack:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom=WardSpace.s.wardDp)) {
        com.cramdown.sorta.ui.mock.StudyTitleBar(title, onBack, inset = 0.dp)
        if(subtitle.isNotBlank()) Text(subtitle, Modifier.fillMaxWidth(), style=WardTextStyle.caption.textStyle(), color=wardColor(WardToken.inkSecondary),
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
