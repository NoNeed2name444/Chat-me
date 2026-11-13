package com.cramdown.sorta.map

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Name pills that stay readable (S05f), ported from iOS's GraphLabelContrast.swift with its
// numbers: what lies behind a name's pill is estimated from the map's backdrop and its bodies'
// glows, never read back from the frame; the words are light or dark to reach WCAG's 4.5:1, the
// pill more opaque where the background is bright or busy, with an outline and a stronger shadow
// while the contrast is under 7:1; and a tracker eases between styles over 0.3 s, so a body moving
// behind a name never makes it flicker. Pure Kotlin, tested on the JVM (LabelContrastTest).
//
// Android's map knows its bodies as places on screen (ScreenNode), so its glows and the points
// across a pill are directions through screen points from the camera (LabelEstimate.toward):
// the same angles iOS works out from places in the scene. A dying body's flash (iOS's
// GraphShine.deathFlash) is not ported: Android's map has no dying bodies.

/** A colour, sRGB, each channel 0..1 (GraphRGB). */
data class LabelRgb(val r: Double, val g: Double, val b: Double) {
    /** This over [under], [alpha] of the way. */
    fun over(under: LabelRgb, alpha: Double): LabelRgb {
        val a = min(max(alpha, 0.0), 1.0)
        return LabelRgb(r * a + under.r * (1 - a), g * a + under.g * (1 - a), b * a + under.b * (1 - a))
    }

    /** [amount] of the way to [other]. */
    fun mixed(other: LabelRgb, amount: Double): LabelRgb = other.over(this, amount)

    /** Light added on top, each channel held at 1. */
    fun adding(other: LabelRgb, amount: Double): LabelRgb =
        LabelRgb(min(r + other.r * amount, 1.0), min(g + other.g * amount, 1.0), min(b + other.b * amount, 1.0))

    val clamped: LabelRgb get() = LabelRgb(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0))

    /** WCAG relative luminance. */
    val luminance: Double get() = LabelContrast.luminance(this)

    companion object {
        val BLACK = LabelRgb(0.0, 0.0, 0.0)
        val WHITE = LabelRgb(1.0, 1.0, 1.0)
    }
}

/** WCAG 2's contrast maths (GraphContrast). */
object LabelContrast {
    /** One sRGB channel, linearised. */
    fun linear(c: Double): Double {
        val v = min(max(c, 0.0), 1.0)
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance, 0 (black) to 1 (white). */
    fun luminance(c: LabelRgb): Double = linear(c.r) * 0.2126 + linear(c.g) * 0.7152 + linear(c.b) * 0.0722

    /** The contrast ratio between two luminances, 1 to 21. */
    fun ratio(a: Double, b: Double): Double = (max(a, b) + 0.05) / (min(a, b) + 0.05)

    fun ratio(a: LabelRgb, b: LabelRgb): Double = ratio(a.luminance, b.luminance)

    /** The contrast WCAG AA asks of body text. */
    const val TARGET = 4.5
}

/** A unit direction from the eye (iOS's SIMD3<Double>). */
data class LabelRay(val x: Double, val y: Double, val z: Double) {
    fun dot(o: LabelRay): Double = x * o.x + y * o.y + z * o.z
}

/**
 * One light in the picture, as seen from the camera (GraphGlow): its direction and angular radii,
 * the solid body's ([core]) and how far its glow reaches ([halo]), its colour at the core, and how
 * strong its halo is at the body's rim.
 */
data class LabelGlow(val direction: LabelRay, val core: Double, val halo: Double, val colour: LabelRgb, val haloStrength: Double)

/** What a pill's measure found behind it (GraphBackdrop): the mean colour, the brightest and darkest luminance. */
data class LabelBackdrop(val mean: LabelRgb, val brightest: Double, val darkest: Double) {
    /** How much it changes across the pill: 0 flat, 1 black to white. */
    val busy: Double get() = max(brightest - darkest, 0.0)

    companion object {
        fun flat(c: LabelRgb): LabelBackdrop { val l = c.luminance; return LabelBackdrop(c, l, l) }
    }
}

/** What is behind a pill (GraphBackdropEstimate). */
object LabelEstimate {
    /** The colour along [d]: the backdrop, plus each glow over it, its core fully, its halo fading with the square of the distance out. */
    fun colour(d: LabelRay, glows: List<LabelGlow>, backdrop: LabelRgb): LabelRgb {
        var out = backdrop
        for (g in glows) {
            val angle = acos(min(max(d.dot(g.direction), -1.0), 1.0))
            if (angle <= g.core) { out = g.colour.over(out, 1.0); continue }
            if (angle >= g.halo || g.halo <= g.core) continue
            val t = (angle - g.core) / (g.halo - g.core)
            out = out.adding(g.colour, (1 - t) * (1 - t) * g.haloStrength)
        }
        return out.clamped
    }

    /** The colour at each of [points] (directions across a pill), averaged, with the brightest and darkest. */
    fun measure(points: List<LabelRay>, glows: List<LabelGlow>, backdrop: LabelRgb): LabelBackdrop {
        if (points.isEmpty()) return LabelBackdrop.flat(backdrop)
        var r = 0.0; var g = 0.0; var b = 0.0
        var hi = 0.0; var lo = 1.0
        for (p in points) {
            val c = colour(p, glows, backdrop)
            r += c.r; g += c.g; b += c.b
            val l = c.luminance
            hi = max(hi, l); lo = min(lo, l)
        }
        val n = points.size.toDouble()
        return LabelBackdrop(LabelRgb(r / n, g / n, b / n), hi, lo)
    }

    /** The direction from the eye through the screen point ([x], [y]), for a view centred on ([cx], [cy]) with [focal] pixels of focal length. */
    fun toward(x: Double, y: Double, cx: Double, cy: Double, focal: Double): LabelRay {
        val dx = x - cx; val dy = cy - y
        val n = sqrt(dx * dx + dy * dy + focal * focal)
        return LabelRay(dx / n, dy / n, focal / n)
    }

    /**
     * Five points across a pill centred on ([x], [y]) on screen, [halfWidth] across and [halfHeight]
     * up and down: its middle and four points in from its corners (pillPoints: 0.8 of the half width,
     * 0.6 of the half height).
     */
    fun pillPoints(x: Double, y: Double, halfWidth: Double, halfHeight: Double, cx: Double, cy: Double, focal: Double): List<LabelRay> {
        val across = halfWidth * 0.8
        val over = halfHeight * 0.6
        return listOf(toward(x, y, cx, cy, focal), toward(x - across, y - over, cx, cy, focal), toward(x + across, y - over, cx, cy, focal),
            toward(x - across, y + over, cx, cy, focal), toward(x + across, y + over, cx, cy, focal))
    }

    /** The angle between two unit directions. */
    private fun angle(a: LabelRay, b: LabelRay): Double = acos(min(max(a.dot(b), -1.0), 1.0))

    /**
     * A body drawn at ([x], [y]) on screen, [radius] pixels across its solid part, as a glow: its core
     * that radius, its halo [LabelShine.reach] times it (at least the core), as the angles they span
     * from the eye (GraphBackdropEstimate.glow, from screen places).
     */
    fun glow(x: Double, y: Double, radius: Double, shine: LabelShine, cx: Double, cy: Double, focal: Double): LabelGlow? {
        if (radius <= 0.0 || focal <= 0.0) return null
        val at = toward(x, y, cx, cy, focal)
        val core = angle(at, toward(x + radius, y, cx, cy, focal))
        val halo = max(angle(at, toward(x + radius * max(shine.reach, 1.0), y, cx, cy, focal)), core)
        return LabelGlow(at, core, halo, shine.colour, shine.strength)
    }

    /** A body at [at] in a scene seen from [eye], [radius] across, its glow reaching [reach] times that (GraphBackdropEstimate.glow, iOS's own form). */
    fun glow(at: DoubleArray, radius: Double, reach: Double, colour: LabelRgb, strength: Double, eye: DoubleArray): LabelGlow? {
        val vx = at[0] - eye[0]; val vy = at[1] - eye[1]; val vz = at[2] - eye[2]
        val d = sqrt(vx * vx + vy * vy + vz * vz)
        if (d <= radius * 1.05 || d <= 1e-6) return null
        val core = asin(min(radius / d, 1.0))
        val halo = max(asin(min(radius * max(reach, 1.0) / d, 1.0)), core)
        return LabelGlow(LabelRay(vx / d, vy / d, vz / d), core, halo, colour, strength)
    }

    /** Five points across a pill in a scene, as unit directions from [eye] (GraphBackdropEstimate.pillPoints, iOS's own form). */
    fun pillPoints(centre: DoubleArray, right: DoubleArray, up: DoubleArray, halfWidth: Double, halfHeight: Double, eye: DoubleArray): List<LabelRay> {
        val a = halfWidth * 0.8; val o = halfHeight * 0.6
        val out = ArrayList<LabelRay>(5)
        for ((sa, so) in listOf(0.0 to 0.0, -1.0 to 1.0, 1.0 to 1.0, -1.0 to -1.0, 1.0 to -1.0)) {
            val vx = centre[0] + right[0] * a * sa + up[0] * o * so - eye[0]
            val vy = centre[1] + right[1] * a * sa + up[1] * o * so - eye[1]
            val vz = centre[2] + right[2] * a * sa + up[2] * o * so - eye[2]
            val n = sqrt(vx * vx + vy * vy + vz * vz)
            if (n > 1e-9) out.add(LabelRay(vx / n, vy / n, vz / n))
        }
        return out
    }
}

/**
 * How a name pill is drawn (GraphLabelLook): dark words on a pale pill ([darkText]) or light on a
 * dark one, the pill's [opacity], its rim, an [outline] and a stronger rim when the contrast is
 * marginal, the [shadow] (0..1), and the [contrast] the words get.
 */
data class LabelLook(
    val darkText: Boolean, val text: LabelRgb, val pill: LabelRgb, val opacity: Double, val rim: LabelRgb,
    val outline: Boolean, val shadow: Double, val contrast: Double,
) {
    /** The rim as drawn (GraphLabelParts.apply): its shade darkened, or lightened under dark words, by the shadow; solid when outlined. */
    val rimShade: LabelRgb get() = rim.mixed(if (darkText) LabelRgb.WHITE else LabelRgb.BLACK, shadow * 0.3)
    val rimAlpha: Double get() = if (outline) 1.0 else max(opacity, 0.5)
}

/** What a name's style depends on besides its background (GraphLabelSettings). */
data class LabelSettings(val increaseContrast: Boolean = false, val reduceTransparency: Boolean = false)

/** The style for a name over what is behind it (GraphLabelChooser). */
object LabelChooser {
    val LIGHT_TEXT = LabelRgb(0.97, 0.97, 0.99)
    val DARK_TEXT = LabelRgb(0.05, 0.06, 0.09)
    /** The slate pill (iOS's GraphLabelNode.pillFill) and its pale twin. */
    val DARK_PILL = LabelRgb(0.12, 0.13, 0.19)
    val PALE_PILL = LabelRgb(0.93, 0.94, 0.97)

    /** What the words sit on: the pill at [opacity] over the background's mean. */
    fun seen(pill: LabelRgb, opacity: Double, back: LabelBackdrop): LabelRgb = pill.over(back.mean, opacity)

    /** The worst contrast the words get: against the pill over the brightest, the darkest and the mean behind it. */
    fun worstContrast(text: LabelRgb, pill: LabelRgb, opacity: Double, back: LabelBackdrop): Double {
        val a = min(max(opacity, 0.0), 1.0)
        val pillL = pill.luminance
        val hi = pillL * a + back.brightest * (1 - a)
        val lo = pillL * a + back.darkest * (1 - a)
        val mid = seen(pill, a, back).luminance
        val t = text.luminance
        return min(LabelContrast.ratio(t, hi), min(LabelContrast.ratio(t, lo), LabelContrast.ratio(t, mid)))
    }

    /** The least opacity, in steps of 0.05 from [floor], at which [text] on [pill] reaches the target; 1 if none does. */
    fun opacity(text: LabelRgb, pill: LabelRgb, back: LabelBackdrop, floor: Double): Double {
        var a = floor
        while (a < 1) {
            if (worstContrast(text, pill, a, back) >= LabelContrast.TARGET) return a
            a += 0.05
        }
        return 1.0
    }

    /** The usual rim (iOS's GraphLabelNode.rimFill, Graph3DView.swift:1565). */
    val RIM_FILL = LabelRgb(0.40, 0.43, 0.55)

    /** A name's rim in a galaxy of [tone] (S05h): the usual rim mixed half with the tone, as iOS's rim(tone:) (GraphUniverseScene.swift:333-339). */
    fun rim(tone: LabelRgb): LabelRgb = LabelRgb((tone.r + RIM_FILL.r) / 2, (tone.g + RIM_FILL.g) / 2, (tone.b + RIM_FILL.b) / 2)

    /**
     * The style for a name over [back], on a body of colour [tint]; [preferDark] holds the words' kind (the tracker's).
     * In a Universe galaxy of [tone] the rim is [rim] of it, but for Increase Contrast's white and a marginal name's outline.
     */
    fun choose(back: LabelBackdrop, tint: LabelRgb, settings: LabelSettings, preferDark: Boolean? = null, tone: LabelRgb? = null): LabelLook {
        if (settings.increaseContrast) {
            // always the strongest: black pill, white words, white rim
            return LabelLook(false, LabelRgb.WHITE, LabelRgb.BLACK, 1.0, LabelRgb.WHITE, true, 0.6, LabelContrast.ratio(LIGHT_TEXT, LabelRgb.BLACK))
        }
        val floor = if (settings.reduceTransparency) 1.0 else 0.62
        // the pill leans a little towards the body's own colour
        val darkPillTinted = DARK_PILL.mixed(tint, 0.18)
        val palePillTinted = PALE_PILL.mixed(tint, 0.10)
        val lightNeed = opacity(LIGHT_TEXT, darkPillTinted, back, floor)
        val darkNeed = opacity(DARK_TEXT, palePillTinted, back, floor)
        val lightGets = worstContrast(LIGHT_TEXT, darkPillTinted, lightNeed, back)
        val darkGets = worstContrast(DARK_TEXT, palePillTinted, darkNeed, back)
        var dark = when {
            preferDark != null -> preferDark
            // whichever needs the lighter pill (shows more of the scene)
            lightNeed != darkNeed -> darkNeed < lightNeed
            else -> darkGets > lightGets
        }
        // the night look is light on dark; a pale pill only where the background itself is pale
        if (preferDark == null && dark && back.mean.luminance < 0.35) dark = false
        val text = if (dark) DARK_TEXT else LIGHT_TEXT
        val pill = if (dark) palePillTinted else darkPillTinted
        // a busy or bright background: more opaque still
        val glare = max(0.0, back.brightest - 0.25) * 0.3
        val alpha = min(1.0, (if (dark) darkNeed else lightNeed) + 0.25 * back.busy + glare)
        val gets = worstContrast(text, pill, alpha, back)
        val marginal = gets < 7
        val rimBase = if (dark) LabelRgb(0.35, 0.37, 0.45) else LabelRgb(0.40, 0.43, 0.55)
        val rim = when {
            marginal -> if (dark) LabelRgb(0.1, 0.1, 0.14) else LabelRgb(0.85, 0.87, 0.95)
            tone != null -> rim(tone)
            else -> rimBase.mixed(tint, 0.35)
        }
        val shadow = if (marginal) 0.45 else 0.15 + 0.2 * back.busy
        return LabelLook(dark, text, pill, alpha, rim, marginal, min(shadow, 0.6), gets)
    }
}

/**
 * One shown name's style over time (GraphLabelTracker). A new measure changes which words it shows,
 * light or dark, only when the other kind has been clearly better for [HOLD] seconds in a row
 * (past a band either side of the flip); everything else eases to the new measure over [EASE] seconds.
 */
class LabelTracker(look: LabelLook, private val tone: LabelRgb? = null) {
    var target: LabelLook = look; private set
    var shown: LabelLook = look; private set
    private var from: LabelLook = look
    private var progress = 1.0
    private var pending = 0.0

    /** Whether it is still easing. */
    val easing: Boolean get() = progress < 1

    /** A new measure, [elapsed] seconds after the last one. */
    fun measure(back: LabelBackdrop, tint: LabelRgb, settings: LabelSettings, elapsed: Double) {
        var next = LabelChooser.choose(back, tint, settings, preferDark = target.darkText, tone = tone)
        if (!settings.increaseContrast) {
            val free = LabelChooser.choose(back, tint, settings, tone = tone)
            val light = back.mean.luminance
            val clear = if (target.darkText) light <= DARK_BELOW else light >= PALE_ABOVE
            if (free.darkText != target.darkText && clear) {
                pending += max(elapsed, 0.0)
                if (pending >= HOLD - 1e-9) { next = free; pending = 0.0 }
            } else {
                pending = 0.0
            }
        }
        if (next == target) return
        from = shown
        target = next
        progress = 0.0
    }

    /** Moves [shown] on by [dt] seconds, at once when [instant] (animations off). Whether it changed. */
    fun step(dt: Double, instant: Boolean = false): Boolean {
        if (progress >= 1) return false
        progress = if (instant) 1.0 else min(1.0, progress + max(dt, 0.0) / EASE)
        if (progress >= 1) { shown = target; return true }
        val t = progress * progress * (3 - 2 * progress)
        shown = blend(from, target, t)
        return true
    }

    companion object {
        const val EASE = 0.3
        const val HOLD = 0.35
        /** The band round the flip: dark words once what is behind is paler than this, light again only once it is darker than that. */
        const val PALE_ABOVE = 0.40
        const val DARK_BELOW = 0.28

        fun blend(a: LabelLook, b: LabelLook, t: Double): LabelLook {
            val late = t >= 0.5
            return LabelLook(
                darkText = if (late) b.darkText else a.darkText, text = a.text.mixed(b.text, t), pill = a.pill.mixed(b.pill, t),
                opacity = a.opacity + (b.opacity - a.opacity) * t, rim = a.rim.mixed(b.rim, t),
                outline = if (late) b.outline else a.outline, shadow = a.shadow + (b.shadow - a.shadow) * t,
                contrast = a.contrast + (b.contrast - a.contrast) * t,
            )
        }
    }
}

/** How often the names are measured, and how many at most (GraphLabelSampler). */
object LabelSampler {
    /** Seconds between measures: about 5 a second, 3 when the frame budget has stepped down from HIGH. */
    fun interval(smooth: Boolean): Double = if (smooth) 0.3 else 0.2

    /** The most names measured at once. */
    const val MOST = 10
}

/**
 * A body's light for the estimate (GraphShine): its colour, how far its glow reaches in its own
 * radii, and how strong its halo is at its rim.
 */
data class LabelShine(val colour: LabelRgb, val reach: Double, val strength: Double) {
    companion object {
        val DARK = LabelShine(LabelRgb(0.04, 0.04, 0.05), 1.0, 0.0)

        /** iOS's Universe bodies, by UniverseRole's raw value (an empty folder is dark). */
        fun universe(role: String, empty: Boolean, tier: Int): LabelShine = when (role) {
            // a black hole: the sphere black, the disk round it bright
            "galaxy" -> if (empty) LabelShine(LabelRgb(0.05, 0.05, 0.06), 2.0, 0.15) else LabelShine(LabelRgb(0.10, 0.08, 0.06), 3.2, 0.85)
            "star", "home" -> if (empty) LabelShine(LabelRgb(0.18, 0.12, 0.10), 1.4, 0.2) else {
                val colours = listOf(LabelRgb(1.0, 0.97, 0.88), LabelRgb(1.0, 0.97, 0.88), LabelRgb(1.0, 0.78, 0.45), LabelRgb(1.0, 0.52, 0.35))
                LabelShine(colours[tier.coerceIn(0, 3)], 2.6, 0.9)
            }
            "gasGiant" -> LabelShine(LabelRgb(0.78, 0.62, 0.45), 1.3, 0.25)
            "rocky" -> LabelShine(LabelRgb(0.42, 0.40, 0.38), 1.1, 0.1)
            "moon" -> LabelShine(LabelRgb(0.62, 0.62, 0.64), 1.0, 0.0)
            "pulsar" -> LabelShine(LabelRgb(0.75, 0.88, 1.0), 3.5, 0.8)
            "comet", "oort" -> LabelShine(LabelRgb(0.80, 0.92, 1.0), 2.2, 0.5)
            else -> DARK
        }

        /** A Space body by its look, as the Universe body it looks like (Graph3DView.shineRole), at tier 1 and not empty. */
        fun space(style: SpaceStyle): LabelShine = universe(
            when (style) {
                SpaceStyle.BLACK_HOLE -> "galaxy"
                SpaceStyle.SUN -> "star"
                SpaceStyle.GAS_GIANT -> "gasGiant"
                SpaceStyle.ROCKY -> "rocky"
                SpaceStyle.PULSAR -> "pulsar"
                SpaceStyle.COMET -> "comet"
            },
            empty = false, tier = 1,
        )

        /** The Neurons, by NeuronRole's code: glowing cells, the parts and ideas inside them dimmer. */
        fun neurons(role: Int): LabelShine = when (role) {
            0, 2 -> LabelShine(LabelRgb(0.95, 0.62, 0.78), 2.2, 0.7)
            1 -> LabelShine(LabelRgb(0.82, 0.58, 0.95), 1.8, 0.55)
            3 -> LabelShine(LabelRgb(0.72, 0.60, 0.95), 1.6, 0.5)
            4 -> LabelShine(LabelRgb(0.55, 0.78, 0.95), 1.4, 0.4)
            else -> LabelShine(LabelRgb(0.60, 0.70, 0.60), 1.3, 0.2)
        }

        /** Each theme's ground behind everything: the night sky, the Neurons' deep blue fluid. */
        fun backdrop(neurons: Boolean): LabelRgb = if (neurons) LabelRgb(0.03, 0.06, 0.16) else LabelRgb(0.03, 0.03, 0.06)
    }
}
