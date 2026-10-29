package com.cramdown.sorta.ui.ward

import org.junit.Test
import java.io.File

// A port of ios/RedPen/Tests/WardLookTests.swift's "Each section's
// wash" and "The opening's ground is home's" checks, same cases. The owner,
// 21 Oct, on the launch's first frame and home: "the background color doesn't
// match the main menu". In Sorta the window's starting background and the
// Android 12 splash stand on the page colour (@color/page, light and night),
// which is home's ground for the section the app opens on. Sorta draws no
// paper grid (the owner, 29 Oct).
class WardGroundTest {

    private val modes = listOf(false, true)
    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    // The grounds are the plain base (light, dark): Sorta's page, bone and aubergine.
    private val plainBase = WardPair(0xF3EADB, 0x281E2A)

    @Test fun eachSectionsWash() = checks {
        for (dark in modes) {
            val chartInk = WardPalette.hex(WardToken.ink, dark)
            val biroGrey = WardPalette.hex(WardToken.inkSecondary, dark)
            for (s in WardSection.entries) {
                val wash = s.wash(dark)
                check("${mode(dark)} $s: wash is the plain base",
                    wash == WardPalette.hex(WardToken.background, dark) && wash == plainBase.of(dark) && s.wash.of(dark) == wash, hx(wash))
                val cChart = WardPalette.contrast(chartInk, wash)
                check("${mode(dark)} $s: Chart Ink keeps 4.5:1 on its wash", cChart >= 4.5, "${hx(wash)} ${f2(cChart)}")
                val cBiro = WardPalette.contrast(biroGrey, wash)
                check("${mode(dark)} $s: Biro Grey keeps 4.5:1 on its wash", cBiro >= 4.5, "${hx(wash)} ${f2(cBiro)}")
                val cAccent = WardPalette.contrast(s.accent(dark), wash)
                check("${mode(dark)} $s: its accent keeps 4.5:1 on its wash", cAccent >= 4.5, "${hx(wash)} ${f2(cAccent)}")
            }
            val washes = WardSection.entries.map { it.wash(dark) }
            check("${mode(dark)}: every section's wash is the same plain base", washes.toSet() == setOf(plainBase.of(dark)))
        }
        val now = WardLookNow()
        for (section in WardSection.entries) for (dark in modes) {
            now.set(WardGlide.rest(WardLook(dark, section)))
            check("drawn now: $section ${mode(dark)} wash", now.wash(dark) == section.wash(dark))
        }
    }

    // The backgrounds are the plain base, whatever the section and the fills:
    // every section's wash, the outside look's ground, home's ground and the
    // window's and the splash's (ward_ground); and the neutral tiers the
    // dock's panel and the bottom deck's slab stand on are as they were.
    @Test fun theGroundsAreThePlainBase() = checks {
        for (dark in modes) {
            val want = plainBase.of(dark)
            for (s in WardSection.entries) {
                check("${mode(dark)} $s: the wash is the plain base", s.wash(dark) == want, "${hx(s.wash(dark))} vs ${hx(want)}")
                check("${mode(dark)} $s: home's ground stands on it", WardGround.home(s, dark).base == want, hx(WardGround.home(s, dark).base))
            }
            val outside = WardGround.home(WardSection.outside, dark).base
            check("${mode(dark)}: the outside look's ground is the plain base", outside == want, hx(outside))
            val home = WardGround.home(dark = dark).base
            check("${mode(dark)}: home's ground (the opening section) is the plain base", home == want, hx(home))
            val res = colour(if (dark) "values-night" else "values", "page")
            check("${mode(dark)}: page, the window's and the splash's ground, is the plain base",
                res == want, res?.let(::hx) ?: "none")
        }
        // the base, cards and the dock's and deck's slabs (surface), sheets (raised) and wells;
        // all four are the page in Sorta's table (docs/tasks/S02-look-kit.md)
        val tiers = mapOf(
            WardToken.background to WardPair(0xF3EADB, 0x281E2A),
            WardToken.surface to WardPair(0xF3EADB, 0x281E2A),
            WardToken.raised to WardPair(0xF3EADB, 0x281E2A),
            WardToken.well to WardPair(0xF3EADB, 0x281E2A),
        )
        for ((token, pair) in tiers) for (dark in modes) {
            val hex = WardPalette.hex(token, dark)
            check("${mode(dark)} $token is as it was", hex == pair.of(dark), hx(hex))
            for (s in WardSection.entries) {
                check("${mode(dark)} $token stays put in $s", WardGlide.rest(WardLook(dark, s)).hex(token, dark) == pair.of(dark))
            }
        }
    }

    @Test fun theOpeningsGroundIsHomes() = checks {
        check("home opens on WardSection.opening: Sorta's one section", WardSection.opening == WardSection.sorta)
        for (section in WardSection.entries) for (dark in modes) {
            val ground = WardGround.home(section, dark)
            val look = WardGlide.rest(WardLook(dark, section))
            check("$section ${mode(dark)}: the ground's base is home's wash",
                ground.base == section.wash(dark) && ground.base == look.wash(dark), hx(ground.base))
        }
    }

    /** A colour resource's 0xRRGGBB, from res/<dir>/colors.xml. */
    private fun colour(dir: String, name: String): Int? {
        val file = File("src/main/res/$dir/colors.xml")
        require(file.exists()) { "missing ${file.absolutePath}" }
        val m = Regex("<color name=\"$name\">#FF([0-9A-Fa-f]{6})</color>").find(file.readText()) ?: return null
        return m.groupValues[1].toInt(16)
    }

    @Test fun theWindowStandsOnIt() = checks {
        for (dark in modes) {
            val res = colour(if (dark) "values-night" else "values", "page")
            val ground = WardGround.home(dark = dark)
            check("page (${mode(dark)}) is home's ground for the opening section", res == ground.base,
                "${res?.let(::hx) ?: "none"} vs ${hx(ground.base)}")
        }
        fun read(path: String): String {
            val file = File(path)
            require(file.exists()) { "missing ${file.absolutePath}" }
            return file.readText()
        }
        val themes = read("src/main/res/values/themes.xml")
        val night = read("src/main/res/values-night/themes.xml")
        val v31 = read("src/main/res/values-v31/themes.xml")
        check("the starting window's background is the page", themes.contains("<item name=\"android:windowBackground\">@color/page</item>"))
        check("and at night", night.contains("<item name=\"android:windowBackground\">@color/page</item>"))
        check("Android 12's splash screen stands on the page", v31.contains("<item name=\"android:windowSplashScreenBackground\">@color/page</item>"))
    }
}
