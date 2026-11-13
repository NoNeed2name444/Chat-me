package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The name pills' contrast (LabelContrast.kt), mirroring iOS's GraphLabelTests (cases C1 to C5,
 * the same numbers), plus what only Android has: glows and pill points made from places on screen.
 */
class LabelContrastTest {
    private fun near(a: Double, b: Double, tolerance: Double = 0.01) = abs(a - b) <= tolerance

    private val eye = doubleArrayOf(0.0, 0.0, 10.0)
    private val star = LabelEstimate.glow(doubleArrayOf(0.0, 0.0, 0.0), 0.4, 2.6, LabelRgb(1.0, 0.97, 0.88), 0.9, eye)
    private val space = LabelRgb(0.03, 0.03, 0.06)
    private val pale = LabelRgb(0.80, 0.82, 0.86)
    private val plain = LabelSettings()

    private fun points(x: Double, y: Double) = LabelEstimate.pillPoints(doubleArrayOf(x, y, 0.0), doubleArrayOf(1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0), 0.3, 0.08, eye)

    private val glows get() = listOfNotNull(star)
    private val onStar get() = LabelEstimate.measure(points(0.0, 0.0), glows, space)
    private val inHalo get() = LabelEstimate.measure(points(0.0, 0.6), glows, space)
    private val empty get() = LabelEstimate.measure(points(4.0, 4.0), glows, space)
    private val nightLook get() = LabelChooser.choose(LabelBackdrop.flat(space), LabelRgb.WHITE, plain)

    private val backs get() = listOf(
        "the night sky" to LabelBackdrop.flat(space),
        "a bright star" to onStar,
        "a star's halo" to inHalo,
        "a supernova" to LabelBackdrop.flat(LabelRgb(1.0, 0.98, 0.94)),
        "a glowing cell" to LabelBackdrop.flat(LabelRgb(0.95, 0.62, 0.78)),
        "a white nucleus" to LabelBackdrop.flat(LabelRgb(0.95, 1.0, 0.85)),
        "the deep fluid" to LabelBackdrop.flat(LabelRgb(0.03, 0.06, 0.16)),
        "a pale nebula" to LabelBackdrop.flat(pale),
        "half star, half night" to LabelBackdrop(LabelRgb(0.5, 0.5, 0.5), 1.0, 0.0),
    )

    // C1 the WCAG maths

    @Test fun wcagMaths() {
        assertTrue("black is 0, white is 1", LabelRgb.BLACK.luminance == 0.0 && near(LabelRgb.WHITE.luminance, 1.0, 1e-9))
        assertTrue("black on white is 21:1", near(LabelContrast.ratio(LabelRgb.BLACK, LabelRgb.WHITE), 21.0, 1e-6))
        assertEquals("the ratio is symmetric", LabelContrast.ratio(0.2, 0.7), LabelContrast.ratio(0.7, 0.2), 0.0)
        val grey = LabelRgb(0.5, 0.5, 0.5)
        assertTrue("mid grey is 0.214: ${grey.luminance}", near(grey.luminance, 0.214, 0.001))
        val g76 = 0x76 / 255.0
        assertTrue("#767676 on white just passes 4.5", LabelContrast.ratio(LabelRgb(g76, g76, g76), LabelRgb.WHITE) >= 4.5)
        assertTrue("green is brighter than blue", LabelRgb(0.0, 1.0, 0.0).luminance > LabelRgb(0.0, 0.0, 1.0).luminance * 5)
        assertEquals("blending", LabelRgb(0.5, 0.5, 0.5), LabelRgb.WHITE.over(LabelRgb.BLACK, 0.5))
    }

    // C2 what is behind a label

    @Test fun whatIsBehind() {
        assertNotNull("a glow is made", star)
        assertTrue("over a bright star: bright, ${onStar.mean.luminance}", onStar.mean.luminance > 0.6)
        assertTrue("in its halo: lit, and busy, $inHalo", inHalo.mean.luminance > empty.mean.luminance + 0.02 && inHalo.busy > 0.01)
        assertTrue("far away: the backdrop", empty.mean == space && empty.busy == 0.0)
        assertEquals("five points across a pill", 5, points(0.0, 0.0).size)
        assertNull("a body behind the eye is ignored", LabelEstimate.glow(doubleArrayOf(0.0, 0.0, 10.0), 0.4, 2.0, LabelRgb.WHITE, 1.0, eye))
        assertTrue("a pale nebula backdrop is pale", LabelEstimate.measure(points(0.0, 0.0), emptyList(), pale).mean.luminance > 0.5)
    }

    // C3 choosing the style

    @Test fun choosingTheStyle() {
        val low = ArrayList<String>()
        for ((name, back) in backs) for (tint in listOf(LabelRgb.WHITE, LabelRgb(1.0, 0.5, 0.2), LabelRgb(0.2, 0.9, 0.4), LabelRgb(0.3, 0.3, 1.0))) {
            val look = LabelChooser.choose(back, tint, plain)
            val worst = LabelChooser.worstContrast(look.text, look.pill, look.opacity, back)
            if (worst < 4.5) low.add("$name: $worst")
        }
        assertTrue("at least 4.5:1 over every background, whatever the body's colour: $low", low.isEmpty())
        assertTrue("on the night sky: light words on a see-through dark pill, $nightLook", !nightLook.darkText && nightLook.opacity < 0.9)
        val paleLook = LabelChooser.choose(LabelBackdrop.flat(pale), LabelRgb.WHITE, plain)
        assertTrue("on a pale nebula: dark words on a pale pill, $paleLook", paleLook.darkText)
        val starLook = LabelChooser.choose(onStar, LabelRgb.WHITE, plain)
        assertTrue("over a bright star the pill is more opaque", starLook.opacity > nightLook.opacity)
        val busyLook = LabelChooser.choose(backs[8].second, LabelRgb.WHITE, plain)
        assertTrue("busy backgrounds: more opaque, outlined, $busyLook", busyLook.opacity >= 0.9)
        val tintedA = LabelChooser.choose(LabelBackdrop.flat(space), LabelRgb(1.0, 0.2, 0.2), plain)
        val tintedB = LabelChooser.choose(LabelBackdrop.flat(space), LabelRgb(0.2, 0.2, 1.0), plain)
        assertTrue("the pill leans towards the body's colour", tintedA.pill.r > tintedB.pill.r && tintedB.pill.b > tintedA.pill.b)
        val strong = LabelSettings(increaseContrast = true)
        val notStrongest = backs.filter { (_, back) ->
            val look = LabelChooser.choose(back, LabelRgb.WHITE, strong)
            !(look.opacity == 1.0 && look.pill == LabelRgb.BLACK && look.text == LabelRgb.WHITE && look.outline)
        }.map { it.first }
        assertTrue("Increase Contrast: always the strongest style, $notStrongest", notStrongest.isEmpty())
        val solid = LabelSettings(reduceTransparency = true)
        assertTrue("Reduce Transparency: an opaque pill", backs.all { LabelChooser.choose(it.second, LabelRgb.WHITE, solid).opacity == 1.0 })
    }

    // C4 easing and hysteresis

    @Test fun easingWithoutFlicker() {
        val tracker = LabelTracker(nightLook)
        tracker.measure(LabelBackdrop.flat(pale), LabelRgb.WHITE, plain, 0.2)
        assertFalse("one bright measure does not switch the words", tracker.target.darkText)
        tracker.measure(LabelBackdrop.flat(pale), LabelRgb.WHITE, plain, 0.2)
        assertTrue("held bright for longer than the hold: it switches", tracker.target.darkText)
        var steps = 0
        while (tracker.step(1.0 / 60)) { steps++; if (steps > 1000) break }
        assertTrue("the change eases over about 0.3 s: $steps frames", steps in 16..20)
        assertEquals("it arrives", tracker.target, tracker.shown)

        // a planet passing behind, on and off every measure: never flickers
        val flicker = LabelTracker(nightLook)
        var switches = 0
        var wasDark = flicker.target.darkText
        for (k in 0 until 60) {
            flicker.measure(LabelBackdrop.flat(if (k % 2 == 0) pale else space), LabelRgb.WHITE, plain, 0.2)
            if (flicker.target.darkText != wasDark) { switches++; wasDark = flicker.target.darkText }
        }
        assertEquals("a body passing on and off behind it never flips the words", 0, switches)

        val fade = LabelTracker(nightLook)
        fade.measure(onStar, LabelRgb.WHITE, plain, 0.2)
        var rising = true
        var before = fade.shown.opacity
        while (fade.step(1.0 / 120)) {
            if (fade.shown.opacity < before - 1e-9) rising = false
            before = fade.shown.opacity
        }
        assertTrue("the pill's opacity eases smoothly (never back and forth)", rising && fade.shown.opacity > nightLook.opacity)

        val instant = LabelTracker(nightLook)
        instant.measure(onStar, LabelRgb.WHITE, plain, 0.2)
        instant.step(0.001, instant = true)
        assertEquals("animations off: at once", instant.target, instant.shown)
    }

    // C5 the budget and the bodies' light

    @Test fun budgetAndLight() {
        assertTrue("measured about 5 times a second, less on Smooth",
            LabelSampler.interval(smooth = false) <= 0.25 && LabelSampler.interval(smooth = true) > LabelSampler.interval(smooth = false))
        assertTrue("at most 10 names", LabelSampler.MOST <= 10)
        val bright = LabelShine.universe("star", empty = false, tier = 1).colour.luminance
        val dim = LabelShine.universe("rocky", empty = false, tier = 0).colour.luminance
        assertTrue("a star shines brighter than a rocky planet", bright > dim * 3)
        assertTrue("a cell glows", LabelShine.neurons(0).strength > 0.5)
        assertTrue("each theme's ground is dark", LabelShine.backdrop(neurons = false).luminance < 0.02 && LabelShine.backdrop(neurons = true).luminance < 0.02)
        // each Space look as the Universe body it looks like (Graph3DView.shineRole)
        assertEquals(LabelShine.universe("galaxy", false, 1), LabelShine.space(SpaceStyle.BLACK_HOLE))
        assertEquals(LabelShine.universe("star", false, 1), LabelShine.space(SpaceStyle.SUN))
        assertEquals(LabelShine.universe("comet", false, 1), LabelShine.space(SpaceStyle.COMET))
        assertTrue("a sun lights its names more than a rocky planet",
            LabelShine.space(SpaceStyle.SUN).colour.luminance > LabelShine.space(SpaceStyle.ROCKY).colour.luminance * 3)
    }

    // Android's own: the estimate from places on screen

    @Test fun glowsFromTheScreen() {
        val focal = 1000.0
        val cx = 500.0; val cy = 1000.0
        val sun = LabelShine.space(SpaceStyle.SUN)
        val body = LabelEstimate.glow(500.0, 800.0, 60.0, sun, cx, cy, focal)
        assertNotNull(body)
        body!!
        assertTrue("the halo reaches past the core", body.halo > body.core)
        assertTrue("a body's core is about its radius over the focal length: ${body.core}", near(body.core, 60.0 / focal, 0.01))
        assertNull("a body of no size throws no light", LabelEstimate.glow(500.0, 800.0, 0.0, sun, cx, cy, focal))
        // a pill just above the sun sits in its halo; one far off is on the night sky
        val above = LabelEstimate.measure(LabelEstimate.pillPoints(500.0, 690.0, 80.0, 16.0, cx, cy, focal), listOf(body), space)
        val far = LabelEstimate.measure(LabelEstimate.pillPoints(100.0, 100.0, 80.0, 16.0, cx, cy, focal), listOf(body), space)
        val over = LabelEstimate.measure(LabelEstimate.pillPoints(500.0, 800.0, 30.0, 10.0, cx, cy, focal), listOf(body), space)
        assertTrue("in the halo: lit, ${above.mean.luminance}", above.mean.luminance > far.mean.luminance + 0.02)
        assertEquals("far off: the backdrop", space, far.mean)
        assertTrue("over the sun: bright, ${over.mean.luminance}", over.mean.luminance > 0.6)
        // and the style follows: light words on a see-through pill on the night, a more opaque pill over the sun
        val night = LabelChooser.choose(far, LabelRgb.WHITE, plain)
        val lit = LabelChooser.choose(over, sun.colour, plain)
        assertFalse(night.darkText)
        assertTrue("over the sun the pill is more opaque", lit.opacity > night.opacity)
        assertTrue("and the words still read: ${lit.contrast}", LabelChooser.worstContrast(lit.text, lit.pill, lit.opacity, over) >= LabelContrast.TARGET)
        // the rim as iOS draws it (GraphLabelParts.apply)
        assertEquals(1.0, LabelLook(false, LabelRgb.WHITE, LabelRgb.BLACK, 0.7, LabelRgb.WHITE, true, 0.6, 21.0).rimAlpha, 0.0)
        assertEquals(0.7, LabelLook(false, LabelRgb.WHITE, LabelRgb.BLACK, 0.7, LabelRgb.WHITE, false, 0.2, 21.0).rimAlpha, 1e-9)
        assertEquals(0.5, LabelLook(false, LabelRgb.WHITE, LabelRgb.BLACK, 0.3, LabelRgb.WHITE, false, 0.2, 21.0).rimAlpha, 1e-9)
        assertEquals(LabelRgb.WHITE.mixed(LabelRgb.BLACK, 0.18), LabelLook(false, LabelRgb.WHITE, LabelRgb.BLACK, 1.0, LabelRgb.WHITE, true, 0.6, 21.0).rimShade)
    }

    // S05h: a name in a Universe galaxy takes its tone, half and half with the usual rim (GraphUniverseScene.swift:333-339)
    @Test fun aGalaxyTonesItsNamesRim() {
        val tone = LabelRgb(0.9, 0.3, 0.1)
        val base = LabelChooser.RIM_FILL
        assertEquals(LabelRgb(0.40, 0.43, 0.55), base)
        val rim = LabelChooser.rim(tone)
        assertEquals((tone.r + base.r) / 2, rim.r, 1e-6)
        assertEquals((tone.g + base.g) / 2, rim.g, 1e-6)
        assertEquals((tone.b + base.b) / 2, rim.b, 1e-6)
        val look = LabelChooser.choose(LabelBackdrop.flat(space), LabelRgb.WHITE, plain, tone = tone)
        assertFalse(look.outline)
        assertEquals(rim, look.rim)
        // nothing else changes with the tone
        assertEquals(nightLook.copy(rim = rim), look)
        // Increase Contrast keeps its white rim
        assertEquals(LabelRgb.WHITE, LabelChooser.choose(LabelBackdrop.flat(space), LabelRgb.WHITE, LabelSettings(increaseContrast = true), tone = tone).rim)
        // and the tracker keeps the tone through a new measure
        val tracker = LabelTracker(look, tone)
        tracker.measure(LabelBackdrop.flat(space), LabelRgb.WHITE, plain, 0.4)
        assertEquals(rim, tracker.target.rim)
    }
}
