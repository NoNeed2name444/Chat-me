package com.cramdown.sorta.ui.ward

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// A port of ios/RedPen/Shared/Ward/WardPalette.swift, value for value,
// recoloured to Sorta's palette (docs/tasks/S02-look-kit.md, its table).
//
// One table of light and dark values that every colour in the app is drawn
// from, and the contrast each text colour is promised against the surface it
// sits on. Soft UI in one tone per mode: cards (surface) and sheets, bars and
// panels (raised) are the page colour itself (background), so a card is told
// from the ground only by its relief (WardRelief), its edge and its ring.
// Day is bone (#F3EADB) with aubergine ink; night is aubergine (#281E2A) with
// bone ink: each mode's ink is the other mode's page. The coral is the icon's:
// primary is a fill that carries words, accent the icon's coral for icons and
// edges with no words, primaryInk coral words on the page.
//
// Pure Kotlin (no Android imports), so the promises are tested on the JVM
// (WardPaletteTest); ui/Theme.kt turns the table into Compose colours.
// Colours are Ints holding 0xRRGGBB.

/** The palette's tokens. Names match iOS's WardToken cases. */
@Suppress("EnumEntryName")
enum class WardToken {
    /** The matte base every screen sits on */
    background,
    /** Cards and rows: the base's own colour, raised by relief alone */
    surface,
    /** Sheets, floating bars, popovers: the base's own colour too */
    raised,
    /** Wells pressed into the base: fields, tracks, a chosen segment */
    well,
    /** The primary as a fill that carries words */
    primary,
    /** The primary as words and glyphs on the page: 4.5:1 on every tier */
    primaryInk,
    /** Words on a primary fill */
    onPrimary,
    /** The icon's coral, for icons and edges that carry no words: 3:1 on every tier */
    accent,
    /** A danger: the coral darker by day and lighter by night */
    danger,
    /** Ink: text */
    ink,
    /** Secondary text and small caps */
    inkSecondary,
    /** A groove: separators and the empty part of a track */
    hairline,
}

/** A (light, dark) pair of 0xRRGGBB colours. */
data class WardPair(val light: Int, val dark: Int) {
    fun of(dark: Boolean): Int = if (dark) this.dark else light
}

/** A text colour, what it sits on, and the contrast it is promised. */
data class WardPromise(val text: WardToken, val on: WardToken, val ratio: Double)

data class WardRgb(val r: Double, val g: Double, val b: Double)

object WardPalette {
    /** (light, dark) as 0xRRGGBB: Sorta's palette (docs/tasks/S02-look-kit.md). */
    val table: Map<WardToken, WardPair> = mapOf(
        WardToken.background to WardPair(0xF3EADB, 0x281E2A),
        WardToken.surface to WardPair(0xF3EADB, 0x281E2A),
        WardToken.raised to WardPair(0xF3EADB, 0x281E2A),
        WardToken.well to WardPair(0xF3EADB, 0x281E2A),
        WardToken.primary to WardPair(0xC4503A, 0xEE866D),
        WardToken.primaryInk to WardPair(0xB2402A, 0xEE866D),
        WardToken.onPrimary to WardPair(0xFFFFFF, 0x281E2A),
        WardToken.accent to WardPair(0xC4503A, 0xEE866D),
        WardToken.danger to WardPair(0x932109, 0xFFAA90),
        WardToken.ink to WardPair(0x281E2A, 0xF3EADB),
        WardToken.inkSecondary to WardPair(0x5E545A, 0xB0A7A1),
        WardToken.hairline to WardPair(0xDCD3C7, 0x392F39),
    )

    /**
     * Day's relief shade (WardRelief.lightShade): a grey from bone toward
     * aubergine, [dayShadeMix] of the way in OKLab, never past 0.7.
     * Stethoscore's #A3B1C6 was blue, a hue the icon does not have. Night's
     * shade is black, and both modes' highlight white.
     */
    const val dayShade: Int = 0xB0A7A1
    /** How far from bone toward aubergine [dayShade] was mixed, in OKLab. */
    const val dayShadeMix = 0.3

    /** The tiers from deepest to highest: a well, the base, a card, a sheet. The base, a card and a sheet are one colour (the owner, 22 Oct). */
    val tiers: List<WardToken> = listOf(WardToken.well, WardToken.background, WardToken.surface, WardToken.raised)

    fun hex(token: WardToken, dark: Boolean): Int = table.getValue(token).of(dark)

    /** 0...1 components. */
    fun rgb(hex: Int): WardRgb =
        WardRgb(((hex shr 16) and 0xFF) / 255.0, ((hex shr 8) and 0xFF) / 255.0, (hex and 0xFF) / 255.0)

    internal fun linear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    internal fun gamma(c: Double): Double = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

    /** Swift's `.rounded()`: to nearest, halves away from zero. */
    internal fun round(x: Double): Int = if (x >= 0) kotlin.math.floor(x + 0.5).toInt() else -kotlin.math.floor(-x + 0.5).toInt()

    /**
     * A colour part way from [a] to [b] ([t] 0 to 1), mixed in linear light so
     * the middle of a light-to-dark shift is not muddy.
     */
    fun mix(a: Int, b: Int, t: Double): Int {
        val tt = min(1.0, max(0.0, t))
        val p = rgb(a)
        val q = rgb(b)
        fun channel(x: Double, y: Double): Int {
            val v = gamma(linear(x) + (linear(y) - linear(x)) * tt)
            return round(min(1.0, max(0.0, v)) * 255)
        }
        return (channel(p.r, q.r) shl 16) or (channel(p.g, q.g) shl 8) or channel(p.b, q.b)
    }

    /** WCAG 2 relative luminance. */
    fun luminance(hex: Int): Double {
        val c = rgb(hex)
        return 0.2126 * linear(c.r) + 0.7152 * linear(c.g) + 0.0722 * linear(c.b)
    }

    /** WCAG 2 contrast ratio, 1...21. */
    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /**
     * Each text colour, what it sits on, and the ratio it is promised: 4.5 for
     * words, 3 for icons and edges that carry none (accent).
     */
    val promises: List<WardPromise> = listOf(
        WardPromise(WardToken.ink, WardToken.surface, 4.5), WardPromise(WardToken.ink, WardToken.background, 4.5),
        WardPromise(WardToken.ink, WardToken.raised, 4.5), WardPromise(WardToken.ink, WardToken.well, 4.5),
        WardPromise(WardToken.inkSecondary, WardToken.raised, 4.5), WardPromise(WardToken.inkSecondary, WardToken.well, 4.5),
        WardPromise(WardToken.primaryInk, WardToken.raised, 4.5), WardPromise(WardToken.primaryInk, WardToken.well, 4.5),
        WardPromise(WardToken.inkSecondary, WardToken.surface, 4.5), WardPromise(WardToken.inkSecondary, WardToken.background, 4.5),
        WardPromise(WardToken.primaryInk, WardToken.surface, 4.5), WardPromise(WardToken.primaryInk, WardToken.background, 4.5),
        WardPromise(WardToken.onPrimary, WardToken.primary, 4.5),
        WardPromise(WardToken.danger, WardToken.surface, 4.5),
        // a chip or a pill is pressed into a well, so every ink they use is promised on it too
        WardPromise(WardToken.danger, WardToken.well, 4.5),
        // soft UI tints labels and glyphs, not fills, so the accents sit on the ground too
        WardPromise(WardToken.danger, WardToken.background, 4.5),
        // the icon's coral, for icons and edges without words, on every tier
        WardPromise(WardToken.accent, WardToken.well, 3.0), WardPromise(WardToken.accent, WardToken.background, 3.0),
        WardPromise(WardToken.accent, WardToken.surface, 3.0), WardPromise(WardToken.accent, WardToken.raised, 3.0),
    )
}

/**
 * The one spacing scale: every gap, padding and radius in the soft UI is a
 * step of it (dp on Android, points on iOS).
 */
object WardScale {
    val steps: List<Double> = listOf(4.0, 8.0, 12.0, 16.0, 24.0, 32.0)
    const val xs = 4.0
    const val s = 8.0
    const val m = 12.0
    const val l = 16.0
    const val xl = 24.0
    const val xxl = 32.0
    /** Extra room between the lines of a paragraph, on top of the style's own leading. */
    const val leading = 4.0

    fun onScale(value: Double): Boolean = steps.contains(value)
}

/** Light to dark and back: the colours move to the other palette over about a third of a second. */
object WardSchemeGlide {
    const val duration = 0.35

    /** One frame of the shift, [t] of the way from light to dark. */
    fun frame(token: WardToken, t: Double): Int =
        WardPalette.mix(WardPalette.hex(token, dark = false), WardPalette.hex(token, dark = true), t)
}
