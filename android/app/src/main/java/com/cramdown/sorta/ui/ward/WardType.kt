package com.cramdown.sorta.ui.ward

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import android.os.Build
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import com.cramdown.sorta.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// The Compose side of WardTokens.swift: WardType's faces as TextStyles in sp
// (so Android's font size setting scales them, as Dynamic Type does on iOS),
// Material's Typography from them, the scale's steps as dp, the small caps,
// the animated glide (WardLookDriver's frame loop) every Ward colour is
// drawn from while the section's hue shifts, and the motion: WardMotion's
// specs and whether animations are off (wardReduceMotion), which every
// animated surface asks, as an iOS view asks accessibilityReduceMotion.

/** A step of the scale as dp. */
val Double.wardDp: Dp get() = this.toFloat().dp

/**
 * Sorta's faces: Nunito for every script it has, and Baloo Bhaijaan 2 for
 * Arabic (both SIL OFL 1.1, their licences in assets/licenses). Each weight
 * the kit uses is the variable font at that weight. On Android 10 and later
 * each weight is one typeface whose fallback after Nunito is Baloo Bhaijaan
 * 2 (Typeface.CustomFallbackBuilder), so Arabic words in the kit's type are
 * drawn in it; Android 8 and 9 have no custom fallback, so there Nunito
 * stands alone and Arabic falls back to the system's font.
 */
@OptIn(ExperimentalTextApi::class)
object WardFonts {
    /** The weights WardWeight names, as the variable fonts' wght axis. */
    val weights: List<Int> = WardWeight.entries.map { it.value }

    /** Nunito alone, upright and italic. */
    val nunito: FontFamily = FontFamily(weights.flatMap { w ->
        listOf(Font(R.font.nunito, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(R.font.nunito_italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    })

    /** Baloo Bhaijaan 2 alone. */
    val balooBhaijaan2: FontFamily = FontFamily(weights.map { w ->
        Font(R.font.baloo_bhaijaan_2, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })

    /** The kit's family: Nunito falling back to Baloo Bhaijaan 2 where it can, else Nunito alone. */
    val family: FontFamily = FontFamily(weights.flatMap { w ->
        listOf(WardScriptFont(w, italic = false), WardScriptFont(w, italic = true),
            Font(R.font.nunito, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(R.font.nunito_italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    })
}

/**
 * One weight of [WardFonts.family] on Android 10 and later: Nunito, then
 * Baloo Bhaijaan 2 for the glyphs Nunito lacks, then the system's fonts. It
 * is optional: where it cannot be built (Android 8 and 9), the family's
 * plain Nunito at the same weight is used.
 */
@OptIn(ExperimentalTextApi::class)
private class WardScriptFont(w: Int, private val italic: Boolean) :
    AndroidFont(FontLoadingStrategy.OptionalLocal, WardScriptLoader, FontVariation.Settings(FontVariation.weight(w))) {
    override val weight: FontWeight = FontWeight(w)
    override val style: FontStyle = if (italic) FontStyle.Italic else FontStyle.Normal

    fun build(context: Context): android.graphics.Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val res = context.resources
            val w = weight.weight
            val slant = if (italic) android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC else android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT
            val latin = android.graphics.fonts.Font.Builder(res, if (italic) R.font.nunito_italic else R.font.nunito)
                .setWeight(w).setSlant(slant).setFontVariationSettings("'wght' $w").build()
            val arabic = android.graphics.fonts.Font.Builder(res, R.font.baloo_bhaijaan_2)
                .setWeight(w).setFontVariationSettings("'wght' $w").build()
            android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(latin).build())
                .addCustomFallback(android.graphics.fonts.FontFamily.Builder(arabic).build())
                .setSystemFallback("sans-serif")
                .setStyle(android.graphics.fonts.FontStyle(w, slant))
                .build()
        }.getOrNull()
    }
}

/** Builds a [WardScriptFont]'s typeface where the platform can. */
private object WardScriptLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): android.graphics.Typeface? =
        (font as WardScriptFont).build(context)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): android.graphics.Typeface? = loadBlocking(context, font)
}

/** This face as a TextStyle: its size and line in sp, its weight, Sorta's faces (mono for observations). */
fun WardTypeSpec.textStyle(): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight.value),
    fontFamily = if (mono) FontFamily.Monospace else WardFonts.family,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** A text style at a weight, as a TextStyle. */
fun WardTextStyle.textStyle(weight: WardWeight = WardWeight.regular, mono: Boolean = false): TextStyle =
    WardTypeSpec(this, weight, mono).textStyle()

/**
 * Material's type roles from iOS's text styles, so a screen that reads
 * MaterialTheme.typography gets the Ward scale: display is largeTitle,
 * headline the titles, title the headline, body the body, label the small
 * words of a control.
 */
fun wardTypography(): Typography = Typography(
    displayLarge = WardType.display.textStyle(),
    displayMedium = WardType.display.textStyle(),
    displaySmall = WardTextStyle.title.textStyle(WardWeight.bold),
    headlineLarge = WardTextStyle.title.textStyle(WardWeight.bold),
    headlineMedium = WardTextStyle.title2.textStyle(WardWeight.semibold),
    headlineSmall = WardType.title.textStyle(),
    titleLarge = WardType.title.textStyle(),
    titleMedium = WardType.headline.textStyle(),
    titleSmall = WardTextStyle.subheadline.textStyle(WardWeight.semibold),
    bodyLarge = WardType.body.textStyle(),
    bodyMedium = WardTextStyle.subheadline.textStyle(),
    bodySmall = WardTextStyle.footnote.textStyle(),
    labelLarge = WardType.buttonSmall.textStyle(),
    labelMedium = WardType.smallCaps.textStyle(),
    labelSmall = WardTextStyle.caption2.textStyle(WardWeight.semibold),
)

/** Whether the reader's font scale is at iOS's accessibility sizes. */
@Composable fun wardAccessibilitySize(): Boolean = WardType.isAccessibilitySize(LocalDensity.current.fontScale.toDouble())

/** The small caps' style: caption semibold, letters spaced at the standard sizes only (WardSmallCaps). */
@Composable fun wardSmallCapsStyle(): TextStyle {
    val tracking = WardSmallCaps.tracking(LocalDensity.current.fontScale.toDouble())
    return WardSmallCaps.font.textStyle().copy(letterSpacing = tracking.sp)
}

/**
 * The shift this frame, provided by [rememberWardGlide] at the theme's root;
 * null where nothing drives one (a preview), and the look at rest is drawn.
 */
val LocalWardGlide = staticCompositionLocalOf<WardGlide?> { null }

/**
 * The glide toward [look], one value for the whole tree (WardLookDriver):
 * light and dark arrive in one step; a new section's hue is stepped every
 * frame for WardGlide.duration, then left at rest. With animations off
 * (animator duration scale 0) a new look arrives at once.
 */
@Composable fun rememberWardGlide(look: WardLook): WardGlide {
    val context = LocalContext.current
    var glide by remember { mutableStateOf(WardGlide.rest(look)) }
    LaunchedEffect(look) {
        val scale = wardAnimatorScale(context)
        val next = WardLookDriver.go(look, glide, reduceMotion = WardMotion.reduce(scale))
        glide = next
        if (next.settled) return@LaunchedEffect
        val startProgress = next.progress
        val start = withFrameNanos { it }
        while (!glide.settled) {
            val now = withFrameNanos { it }
            glide = WardLookDriver.step(next, startProgress, (now - start) / 1e9 / scale)
        }
    }
    return glide
}

// MARK: the motion (WardMotion)

/** The animator duration scale: 1 by default, 0 with Remove animations (Accessibility) or the developer scales off. */
fun wardAnimatorScale(context: Context): Float = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
} catch (_: Throwable) { 1f }

/**
 * Whether animations are off, provided at the theme's root
 * (SortaTheme, from [rememberWardReduceMotion]); null where nothing
 * provides it (a preview), and [wardReduceMotion] reads the setting itself.
 */
val LocalWardReduceMotion = staticCompositionLocalOf<Boolean?> { null }

/** Whether animations are off, read now and again whenever the setting changes. */
@Composable fun rememberWardReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(WardMotion.reduce(wardAnimatorScale(context))) }
    DisposableEffect(context) {
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduce = WardMotion.reduce(wardAnimatorScale(context)) }
        }
        val watching = runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, watcher)
        }.isSuccess
        onDispose { if (watching) context.contentResolver.unregisterContentObserver(watcher) }
    }
    return reduce
}

/**
 * Whether animations are off here, as an iOS view reads
 * accessibilityReduceMotion: every animated surface asks, and with it on a
 * change is simply there (no travel, no sink, no fill sweeping in).
 */
@Composable fun wardReduceMotion(): Boolean {
    val provided = LocalWardReduceMotion.current
    val context = LocalContext.current
    return provided ?: WardMotion.reduce(wardAnimatorScale(context))
}

/** A Ward curve as a Compose easing. */
fun WardPress.Curve.easing(): Easing = CubicBezierEasing(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())

/** iOS's .easeOut and .easeInOut (WardMotion). */
val WardEaseOut: Easing = WardMotion.easeOut.easing()
val WardEaseInOut: Easing = WardMotion.easeInOut.easing()

/** [seconds] as a tween's whole milliseconds. */
fun wardMillis(seconds: Double): Int = (seconds * 1000).roundToInt()

/** A change over [seconds] on iOS's ease-out; with animations off ([reduce]) it is simply there. */
fun <T> wardEaseOut(seconds: Double, reduce: Boolean): AnimationSpec<T> =
    if (reduce) snap() else tween(wardMillis(seconds), easing = WardEaseOut)

/**
 * A spring that takes about [seconds] and never passes its mark (iOS's
 * .snappy and .smooth of that duration, critically damped); with
 * animations off ([reduce]) it is simply there.
 */
fun <T> wardSpring(seconds: Double, reduce: Boolean): AnimationSpec<T> =
    if (reduce) snap() else spring(dampingRatio = WardMotion.damping.toFloat(), stiffness = WardMotion.stiffness(seconds).toFloat())
