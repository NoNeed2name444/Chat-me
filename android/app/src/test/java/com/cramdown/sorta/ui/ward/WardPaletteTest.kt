package com.cramdown.sorta.ui.ward

import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

// A port of ios/RedPen/Tests/WardPaletteTests.swift, same cases and
// numbers: every text colour keeps its promised contrast on the surface it
// sits on, in light and in dark, and the iOS AccentColor and Page assets are
// the primary and the bases from the same table (docs/tasks/S02-look-kit.md).
class WardPaletteTest {

    // the nearest a chip's words come to its edge: its vertical padding (4)
    // and the line's own leading above a capital (2 at least)
    private val chipTextDepth = 6.0
    private val modes = listOf(false, true)
    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    private fun distance(x: Int, y: Int): Double {
        val p = oklab(x)
        val q = oklab(y)
        return sqrt((p.L - q.L) * (p.L - q.L) + (p.a - q.a) * (p.a - q.a) + (p.b - q.b) * (p.b - q.b))
    }

    @Test fun promisesHold() = checks {
        check("every token has a value", WardToken.entries.all { WardPalette.table[it] != null })
        check("white on black is 21", abs(WardPalette.contrast(0xFFFFFF, 0x000000) - 21) < 0.01)
        check("Theatre Blue on Clean Sheet is about 6.3",
            abs(WardPalette.contrast(0x1D5FB0, 0xFFFFFF) - 6.3) < 0.1, WardPalette.contrast(0x1D5FB0, 0xFFFFFF).toString())
        for (dark in modes) {
            for (p in WardPalette.promises) {
                val r = WardPalette.contrast(WardPalette.hex(p.text, dark), WardPalette.hex(p.on, dark))
                check("${mode(dark)}: ${p.text} on ${p.on} at least ${p.ratio}", r >= p.ratio, f2(r))
            }
        }
    }

    // The words of a chip or a pill sit on a well pressed into what is under
    // it, with the well's inner shade over its edge. Every ink a chip can wear
    // reads 4.5:1 on it, in light and dark, with and without Increase Contrast.
    // The well is drawn in the well's fill, the page colour (the owner, 22 Oct:
    // wells and sheets one tone).
    @Test fun chipInksReadOnTheirWell() = checks {
        check("the shade is there at a chip's edge and gone where its words start",
            WardRelief.shadeCoverage(WardLift.low, depth = 0.0) > 0.5 && WardRelief.shadeCoverage(WardLift.low, depth = 6.0) < 0.01)
        check("a chip's well is drawn in the page colour", WardControlStates.wellFill == WardToken.background)
        val chipInks = listOf(WardToken.primaryInk, WardToken.danger, WardToken.inkSecondary, WardToken.ink)
        for (dark in modes) {
            for (hc in listOf(false, true)) {
                val m = "${mode(dark)}${if (hc) " with Increase Contrast" else ""}"
                val well = WardPalette.hex(WardControlStates.wellFill, dark)
                val ground = WardRelief.insetGround(well, WardLift.low, dark, hc, chipTextDepth)
                for (token in chipInks) {
                    val r = WardPalette.contrast(WardPalette.hex(token, dark), ground)
                    check("$m: $token reads 4.5:1 on a chip's well", r >= 4.5, f3(r))
                }
                for (section in WardSection.entries) {
                    val r = WardPalette.contrast(section.accent(dark), ground)
                    check("$m: $section's accent reads 4.5:1 on a chip's well", r >= 4.5, f3(r))
                }
            }
        }
    }

    // one tone (the owner, 22 Oct: "there shouldn't be 2 tones"; 21 Oct, of the
    // quiz dock: "one color not two color toned"): a card and a sheet are the
    // page colour exactly, so a card is told from the ground only by relief,
    // edges and rings; a well alone is pressed in a soft step darker (as iOS
    // WardPaletteTests)
    @Test fun tiersAreSoftSteps() = checks {
        for (dark in modes) {
            val m = mode(dark)
            val base = WardPalette.hex(WardToken.background, dark)
            check("$m: a card is the page colour (surface == background)", WardPalette.hex(WardToken.surface, dark) == base, hx(WardPalette.hex(WardToken.surface, dark)))
            check("$m: a sheet is the page colour (raised == background)", WardPalette.hex(WardToken.raised, dark) == base, hx(WardPalette.hex(WardToken.raised, dark)))
            check("$m: a card and a sheet are one tone (surface == raised)", WardPalette.hex(WardToken.surface, dark) == WardPalette.hex(WardToken.raised, dark))
            check("$m: the tiers are well, then the base, a card and a sheet in one colour",
                WardPalette.tiers == listOf(WardToken.well, WardToken.background, WardToken.surface, WardToken.raised))
            // Sorta's table (docs/tasks/S02-look-kit.md) puts wells in the page
            // colour too, as iOS's WardPaletteTests asks: a well is told from
            // the ground by its inset shade alone
            check("$m: a well is the page colour (well == background)", WardPalette.hex(WardToken.well, dark) == base, hx(WardPalette.hex(WardToken.well, dark)))
            val groove = WardPalette.contrast(WardPalette.hex(WardToken.hairline, dark), WardPalette.hex(WardToken.background, dark))
            check("$m: a groove shows but stays soft", groove >= 1.1 && groove < 1.6, f2(groove))
        }
        // the icon's grounds: the launch screen is drawn on them
        check("the bases the launch screen is drawn on are kept",
            WardPalette.hex(WardToken.background, false) == 0xF3EADB && WardPalette.hex(WardToken.background, true) == 0x281E2A)
    }

    // A3/A4: every value in Sorta's table (docs/tasks/S02-look-kit.md), pinned
    @Test fun tableIsSortas() = checks {
        val want = mapOf(
            WardToken.background to (0xF3EADB to 0x281E2A),
            WardToken.surface to (0xF3EADB to 0x281E2A),
            WardToken.raised to (0xF3EADB to 0x281E2A),
            WardToken.well to (0xF3EADB to 0x281E2A),
            WardToken.primary to (0xC4503A to 0xEE866D),
            WardToken.primaryInk to (0xB2402A to 0xEE866D),
            WardToken.onPrimary to (0xFFFFFF to 0x281E2A),
            WardToken.accent to (0xC4503A to 0xEE866D),
            WardToken.danger to (0x932109 to 0xFFAA90),
            WardToken.ink to (0x281E2A to 0xF3EADB),
            WardToken.inkSecondary to (0x5E545A to 0xB0A7A1),
            WardToken.hairline to (0xDCD3C7 to 0x392F39),
        )
        check("the table has exactly Sorta's tokens", WardToken.entries.toSet() == want.keys, WardToken.entries.toString())
        for ((token, pair) in want) {
            check("$token light is ${hx(pair.first)}", WardPalette.hex(token, false) == pair.first, hx(WardPalette.hex(token, false)))
            check("$token dark is ${hx(pair.second)}", WardPalette.hex(token, true) == pair.second, hx(WardPalette.hex(token, true)))
        }
        // day's relief shade: a grey from bone toward aubergine, at most 70% of the way
        check("day's relief shade is #B0A7A1", WardPalette.dayShade == 0xB0A7A1 && WardRelief.lightShade == WardPalette.dayShade,
            hx(WardRelief.lightShade))
        check("day's relief shade is mixed at most 70% of the way", WardPalette.dayShadeMix in 0.0..0.7)
        val bone = oklab(0xF3EADB)
        val aub = oklab(0x281E2A)
        val t = WardPalette.dayShadeMix
        val mixed = Triple(bone.L + (aub.L - bone.L) * t, bone.a + (aub.a - bone.a) * t, bone.b + (aub.b - bone.b) * t)
        val shade = oklab(WardPalette.dayShade)
        val off = sqrt((shade.L - mixed.first) * (shade.L - mixed.first) + (shade.a - mixed.second) * (shade.a - mixed.second) +
            (shade.b - mixed.third) * (shade.b - mixed.third))
        check("day's relief shade lies on the bone-to-aubergine line at its mix", off < 0.005, f3(off))
    }

    // sheets, menus and dialogs are one tone too (the owner, 22 Oct), and stay
    // Material's own: the slots their containers take (a sheet's
    // surfaceContainerLow, a menu's surfaceContainer, a dialog's and the date
    // picker's surfaceContainerHigh) are the page colour, and the theme turns
    // Material's tonal elevation off, which would lay its tint over a sheet
    @Test fun sheetsMenusAndDialogsAreThePageColour() = checks {
        for (dark in modes) for (s in WardSection.entries) {
            val scheme = com.cramdown.sorta.ui.wardColorScheme(WardLook(dark, s))
            val page = com.cramdown.sorta.ui.wardRgb(WardPalette.hex(WardToken.background, dark))
            for ((what, slot) in listOf("a sheet" to scheme.surfaceContainerLow, "a menu" to scheme.surfaceContainer,
                "a dialog" to scheme.surfaceContainerHigh)) {
                check("${mode(dark)} $s: $what's container is the page colour", slot == page, slot.toString())
            }
        }
        val themeFile = File("src/main/java/com/cramdown/sorta/ui/Theme.kt")
        check("Theme.kt is found", themeFile.exists(), themeFile.absolutePath)
        val theme = themeFile.readText()
        check("the theme turns Material's tonal elevation off", theme.contains("LocalTonalElevationEnabled provides false"))
    }

    // two characters, not one palette inverted: every text colour differs
    // between the modes. The primary is the look outside any section: Sorta's.
    @Test fun twoCharacters() = checks {
        for (dark in modes) {
            check("${mode(dark)}: primary and primaryInk are Sorta's fill and accent",
                WardPalette.hex(WardToken.primary, dark) == WardSection.sorta.fill(dark) &&
                    WardPalette.hex(WardToken.primaryInk, dark) == WardSection.sorta.accent(dark) &&
                    WardSection.outside == WardSection.sorta)
        }
        for (token in listOf(WardToken.primaryInk, WardToken.danger, WardToken.ink, WardToken.inkSecondary)) {
            val day = WardPalette.hex(token, false)
            val night = WardPalette.hex(token, true)
            // how far apart the two values are (OKLab): 0 when one value serves both modes
            val apart = distance(day, night)
            check("$token has a value of its own in each mode", day != night, "${hx(day)} / ${hx(night)} ${f3(apart)}")
        }
        // night reads its secondary text more strongly than day: dim rooms need it
        val daySecondary = WardPalette.contrast(WardPalette.hex(WardToken.inkSecondary, false), WardPalette.hex(WardToken.background, false))
        val nightSecondary = WardPalette.contrast(WardPalette.hex(WardToken.inkSecondary, true), WardPalette.hex(WardToken.background, true))
        check("night's secondary text has more contrast than day's", nightSecondary > daySecondary,
            "${f2(nightSecondary)} vs ${f2(daySecondary)}")
    }

    // the contrast maths, against WCAG's own worked values
    @Test fun contrastMaths() = checks {
        check("contrast is symmetric", WardPalette.contrast(0x1D5FB0, 0xE0E5EC) == WardPalette.contrast(0xE0E5EC, 0x1D5FB0))
        check("a colour on itself is 1", WardPalette.contrast(0x2A2E35, 0x2A2E35) == 1.0)
        check("mid grey #777 on white is about 4.48 (just under AA)",
            abs(WardPalette.contrast(0x777777, 0xFFFFFF) - 4.48) < 0.01, WardPalette.contrast(0x777777, 0xFFFFFF).toString())
        check("#767676 on white is about 4.54 (just AA)",
            abs(WardPalette.contrast(0x767676, 0xFFFFFF) - 4.54) < 0.01, WardPalette.contrast(0x767676, 0xFFFFFF).toString())
        check("luminance of white is 1 and black 0", WardPalette.luminance(0xFFFFFF) == 1.0 && WardPalette.luminance(0) == 0.0)
    }

    // the appearance glide: a colour shift through linear light, from one
    // palette to the other, about a third of a second
    @Test fun glideMixesInLinearLight() = checks {
        check("the glide takes 0.3 to 0.4 s", WardSchemeGlide.duration in 0.3..0.4)
        for (token in WardToken.entries) {
            check("glide $token: starts at day and ends at night",
                WardSchemeGlide.frame(token, 0.0) == WardPalette.hex(token, false) &&
                    WardSchemeGlide.frame(token, 1.0) == WardPalette.hex(token, true))
        }
        check("mix clamps", WardPalette.mix(0x000000, 0xFFFFFF, -1.0) == 0 && WardPalette.mix(0x000000, 0xFFFFFF, 2.0) == 0xFFFFFF)
        check("halfway between black and white in linear light is #BCBCBC",
            WardPalette.mix(0x000000, 0xFFFFFF, 0.5) == 0xBCBCBC, Integer.toHexString(WardPalette.mix(0, 0xFFFFFF, 0.5)))
        var lastLum = 2.0
        var steady = true
        for (i in 0..20) {
            val l = WardPalette.luminance(WardSchemeGlide.frame(WardToken.background, i / 20.0))
            if (l > lastLum + 1e-9) steady = false
            lastLum = l
        }
        check("the base darkens steadily through the glide, never flashing", steady)
    }

    @Test fun spacingScaleAndBases() = checks {
        check("the scale is 4, 8, 12, 16, 24, 32", WardScale.steps == listOf(4.0, 8.0, 12.0, 16.0, 24.0, 32.0) &&
            listOf(WardScale.xs, WardScale.s, WardScale.m, WardScale.l, WardScale.xl, WardScale.xxl) == WardScale.steps)
        check("on the scale", WardScale.onScale(12.0) && !WardScale.onScale(10.0) && !WardScale.onScale(20.0))
        check("light: the base is a light grey", WardPalette.luminance(WardPalette.hex(WardToken.background, false)) > 0.7)
        // 0.010, not 0.015: aubergine #281E2A measures about 0.015, still far from black
        check("dark: the base is a dark grey, not black",
            WardPalette.luminance(WardPalette.hex(WardToken.background, true)) in 0.010..0.06)
    }

    // the iOS asset catalogue agrees with the table (the test runs from the
    // module or the repository root, or anywhere under it)
    @Test fun iosAssetsAgreeWithTheTable() = checks {
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !File(root, "ios/Sorta").exists()) root = root.parentFile
        check("the repository root is found", root != null, System.getProperty("user.dir"))
        val base = File(root ?: File("/"), "ios/Sorta/Assets.xcassets")
        val accent = File(base, "AccentColor.colorset/Contents.json")
        check("the AccentColor asset is found", accent.exists(), accent.path)
        fun components(appearance: String?, asset: File = accent): Int? {
            val json = runCatching { JSONObject(asset.readText()) }.getOrNull() ?: return null
            val colors = json.optJSONArray("colors") ?: return null
            for (i in 0 until colors.length()) {
                val entry = colors.getJSONObject(i)
                val appearances = entry.optJSONArray("appearances")
                val value = appearances?.optJSONObject(0)?.optString("value")
                if (value != appearance) continue
                val c = entry.optJSONObject("color")?.optJSONObject("components") ?: continue
                fun byte(k: String): Int? = c.optString(k, "").removePrefix("0x").removePrefix("0X").toIntOrNull(16)
                val r = byte("red") ?: return null
                val g = byte("green") ?: return null
                val b = byte("blue") ?: return null
                return (r shl 16) or (g shl 8) or b
            }
            return null
        }
        check("AccentColor (light) is the primary, #C4503A", components(null) == WardPalette.hex(WardToken.primary, false),
            Integer.toHexString(components(null) ?: 0))
        check("AccentColor (dark) is the night primary ink, #EE866D", components("dark") == WardPalette.hex(WardToken.primaryInk, true),
            Integer.toHexString(components("dark") ?: 0))
        val launch = File(base, "Page.colorset/Contents.json")
        check("the Page asset is found", launch.exists(), launch.path)
        check("Page (light) is the base", components(null, launch) == WardPalette.hex(WardToken.background, false),
            Integer.toHexString(components(null, launch) ?: 0))
        check("Page (dark) is the base", components("dark", launch) == WardPalette.hex(WardToken.background, true),
            Integer.toHexString(components("dark", launch) ?: 0))
    }
}
