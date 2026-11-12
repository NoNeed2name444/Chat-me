package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// Touching the map (S05g): what a touch was, the chosen body and what follows from it, and how the
// chosen body is marked, as iOS's GraphTouchModel.swift decides them (GraphTouchRules, judge, pairs,
// GraphSelection) with GraphMotion's pop and GraphStyleAnimator's orbit ring. Plain Kotlin, no
// Android: MapSelectionTest and MapMarkTest check it, as GraphTouchTests checks iOS's.

/** The thresholds a touch is judged by (GraphTouchRules, GraphTouchModel.swift:27-41). */
object MapTouchRules {
    /** Held this long before moving: a pick-up (S05g2's drag), not a turn. */
    const val HOLD_TO_DRAG = 0.25
    /** Held still this long on a body: its options. */
    const val HOLD_FOR_MENU = 0.55
    /** A second tap within this long and this far (dp) is a double tap. */
    const val DOUBLE_TAP_GAP = 0.30
    const val DOUBLE_TAP_REACH_DP = 40f
    /** Asking to open the same body again this soon is the same request. */
    const val OPEN_REPEAT = 0.6
    /** Android's own touch-and-hold delay by default, in milliseconds. */
    const val SYSTEM_HOLD_MILLIS = 400L

    /**
     * How much longer the person's touch-and-hold delay is than Android's default, never less than 1:
     * iOS's hold times are scaled by it, so a longer accessibility setting is kept (the packet's
     * question 1).
     */
    fun holdScale(longPressMillis: Long): Double = max(1.0, longPressMillis.toDouble() / SYSTEM_HOLD_MILLIS)

    /**
     * What [touch] is so far (judge, :73-84). [slop] is how far a finger may wander and still be a tap
     * or a hold, in the same units as [MapTouch.moved] (Android's own touch slop); [scale] lengthens
     * the hold times ([holdScale]).
     */
    fun judge(touch: MapTouch, slop: Float, scale: Double = 1.0): MapTouchKind {
        val drag = HOLD_TO_DRAG * scale
        val menu = HOLD_FOR_MENU * scale
        val still = touch.moved <= slop
        if (!still) {
            // moved before it was held long enough: the camera's
            if (touch.held < drag || !touch.onBody) return MapTouchKind.ORBIT
            return MapTouchKind.DRAG
        }
        if (touch.onBody && touch.held >= menu) return MapTouchKind.MENU
        if (!touch.lifted) return MapTouchKind.PENDING
        if (touch.held >= drag) return if (touch.onBody) MapTouchKind.TAP else MapTouchKind.NOTHING
        return if (touch.taps >= 2 && touch.onBody) MapTouchKind.DOUBLE_TAP else MapTouchKind.TAP
    }

    /** Whether a second tap, [gap] seconds and [distance] after the first, makes a double tap; [reach] in the same units as [distance] (pairs, :88-90). */
    fun pairs(gap: Double, distance: Float, reach: Float = DOUBLE_TAP_REACH_DP): Boolean =
        gap >= 0 && gap <= DOUBLE_TAP_GAP && distance <= reach
}

/** What a touch turned out to be (GraphTouchKind). [DRAG] is S05g2's: until it lands a moving hold turns the camera. */
enum class MapTouchKind { PENDING, TAP, DOUBLE_TAP, ORBIT, DRAG, MENU, NOTHING }

/** One touch as the map's hand sees it (GraphTouch): on a body or not, held and moved so far (seconds, pixels), lifted, taps in a row. */
data class MapTouch(val onBody: Boolean, val held: Double, val moved: Float, val lifted: Boolean, val taps: Int = 1)

/** What happens to the map and the card (GraphSelectionEffect). Ids as the map's bodies carry them. */
sealed class MapSelectionEffect {
    /** Choose this body: mark it, tick, show its card; [glide]: the camera brings it to the middle (a link chip), else eases a little towards it. */
    data class Select(val id: String, val glide: Boolean) : MapSelectionEffect()
    /** Nothing chosen: the card goes. */
    data object Clear : MapSelectionEffect()
    /** Read it: the idea, or the folder's list. */
    data class Open(val id: String) : MapSelectionEffect()
    /** Show links round this body (null: everything back). */
    data class Links(val id: String?) : MapSelectionEffect()
    /** Its options, where it is held. */
    data class Menu(val id: String) : MapSelectionEffect()
}

/** What happened (GraphSelectionEvent). */
sealed class MapSelectionEvent {
    data class TapBody(val id: String) : MapSelectionEvent()
    data object TapEmpty : MapSelectionEvent()
    data class DoubleTap(val id: String) : MapSelectionEvent()
    /** Open pressed, or the card tapped. */
    data object OpenCard : MapSelectionEvent()
    /** A link chip on the card. */
    data class Chip(val id: String) : MapSelectionEvent()
    data object ShowLinks : MapSelectionEvent()
    data class Hold(val id: String) : MapSelectionEvent()
    /** The opened idea or list closed: back to the map, still chosen. */
    data object Closed : MapSelectionEvent()
    /** A body went (deleted, or a cell closed). */
    data class Gone(val id: String) : MapSelectionEvent()
}

/**
 * The chosen body and what follows from each touch (GraphSelection, GraphTouchModel.swift:127-208),
 * its effects in iOS's order. Ids are compared ignoring case, as the map's (an idea's id may come
 * upper case from the Neurons plan).
 */
class MapSelection {
    var selected: String? = null; private set
    var linksShown = false; private set
    /** The body opened over the map, if any. */
    var opened: String? = null; private set
    var selectedAt = -1.0; private set
    private var lastOpen: String? = null
    private var lastOpenAt = -10.0

    /** Whether the peek card shows. */
    val cardShown: Boolean get() = selected != null && opened == null

    private fun same(a: String?, b: String?) = a != null && b != null && a.equals(b, ignoreCase = true)

    /** Handles [event] at [time] (seconds); the effects, in order. */
    fun handle(event: MapSelectionEvent, time: Double): List<MapSelectionEffect> = when (event) {
        is MapSelectionEvent.TapBody -> when {
            // the second tap of a double tap: that one opens it
            same(selected, event.id) -> if (time - selectedAt < MapTouchRules.DOUBLE_TAP_GAP) emptyList() else open(event.id, time)
            else -> choose(event.id, glide = false, time = time)
        }
        MapSelectionEvent.TapEmpty -> letGo()
        is MapSelectionEvent.DoubleTap -> {
            val out = ArrayList<MapSelectionEffect>()
            if (!same(selected, event.id)) out += choose(event.id, glide = false, time = time)
            out + open(event.id, time)
        }
        MapSelectionEvent.OpenCard -> selected?.let { open(it, time) } ?: emptyList()
        is MapSelectionEvent.Chip -> choose(event.id, glide = true, time = time)
        MapSelectionEvent.ShowLinks -> selected?.let { id ->
            linksShown = !linksShown
            listOf(MapSelectionEffect.Links(if (linksShown) id else null))
        } ?: emptyList()
        is MapSelectionEvent.Hold -> {
            val out = ArrayList<MapSelectionEffect>()
            if (!same(selected, event.id)) out += choose(event.id, glide = false, time = time)
            out + MapSelectionEffect.Menu(event.id)
        }
        MapSelectionEvent.Closed -> { opened = null; emptyList() }
        is MapSelectionEvent.Gone -> {
            if (same(opened, event.id)) opened = null
            if (same(selected, event.id)) letGo() else emptyList()
        }
    }

    private fun letGo(): List<MapSelectionEffect> {
        if (selected == null && !linksShown) return emptyList()
        val out = ArrayList<MapSelectionEffect>()
        if (linksShown) out += MapSelectionEffect.Links(null)
        selected = null
        linksShown = false
        out += MapSelectionEffect.Clear
        return out
    }

    private fun choose(id: String, glide: Boolean, time: Double): List<MapSelectionEffect> {
        val out = ArrayList<MapSelectionEffect>()
        // links were shown round the old one: round the new one now
        if (linksShown) out += MapSelectionEffect.Links(id)
        selected = id
        selectedAt = time
        out.add(0, MapSelectionEffect.Select(id, glide))
        return out
    }

    private fun open(id: String, time: Double): List<MapSelectionEffect> {
        if (same(lastOpen, id) && time - lastOpenAt < MapTouchRules.OPEN_REPEAT) return emptyList()
        lastOpen = id
        lastOpenAt = time
        opened = id
        return listOf(MapSelectionEffect.Open(id))
    }
}

/**
 * How the chosen body is marked, in iOS's numbers: Space's orbit ring (GraphStyleAnimator.placeOrbit,
 * GraphStyleAnimator.swift:528-552; GraphNodeFactory.makeOrbit, :702-720), a chosen black hole's hot
 * ring and disk (GraphLook.swift:885-905), the Neurons' hot halo (GraphNeuronLook.haloMaterial,
 * :685-702), Performance's chosen point (GraphPerfShaders.swift:137-141, :165-168) and the pop
 * (GraphMotion.pop and stepPops, :857-865, :1935-1975).
 */
object MapMark {
    // the orbit ring
    /** The ring's circle sits at 0.9 of its plane's half width (GraphSpaceShaders.orbit's rp_d). */
    const val ORBIT_LINE = 0.9f
    /** It closes in from 1.35 times its size over this long, eased out cubically. */
    const val ORBIT_GROW_SECONDS = 0.25f
    const val ORBIT_FROM = 1.35f
    /** Its plane is tipped this far about x, then rolled this far about z (radians). */
    const val ORBIT_TIP = 1.15f
    const val ORBIT_ROLL = 0.2f

    /** The ring's growth after [dt] more seconds from [grow]: 0 to 1 over a quarter second. */
    fun orbitGrow(grow: Float, dt: Float): Float = if (dt > 0f) min(1f, grow + dt / ORBIT_GROW_SECONDS) else grow

    /** Where a new ring's growth starts: 0 while animations run, else 1 (full size at once). */
    fun orbitStart(lively: Boolean): Float = if (lively) 0f else 1f

    /** The ring's plane across, round a body of [reach] (its note's base radius), at [grow] and [pop] (placeOrbit). */
    fun orbitSize(reach: Float, grow: Float, pop: Float): Float {
        val left = 1f - grow.coerceIn(0f, 1f)
        val eased = 1f - left * left * left
        val ring = reach * 2.2f / 0.9f * 2f
        return ring * (ORBIT_FROM - (ORBIT_FROM - 1f) * eased) * max(pop, 0.05f)
    }

    /**
     * The ring's model matrix (column-major): translate to [x], [y], [z], scale by [size], then
     * rolled × tipped (GraphNodeFactory.makeOrbit: rolled * tipped), the plane in its own xy.
     */
    fun orbitModel(x: Float, y: Float, z: Float, size: Float): FloatArray {
        val ct = kotlin.math.cos(ORBIT_TIP); val st = kotlin.math.sin(ORBIT_TIP)
        val cr = kotlin.math.cos(ORBIT_ROLL); val sr = kotlin.math.sin(ORBIT_ROLL)
        // Rz(roll) * Rx(tip), columns
        val c0 = floatArrayOf(cr, sr, 0f)
        val c1 = floatArrayOf(-sr * ct, cr * ct, st)
        val c2 = floatArrayOf(sr * st, -cr * st, ct)
        return floatArrayOf(
            c0[0] * size, c0[1] * size, c0[2] * size, 0f,
            c1[0] * size, c1[1] * size, c1[2] * size, 0f,
            c2[0] * size, c2[1] * size, c2[2] * size, 0f,
            x, y, z, 1f,
        )
    }

    // a chosen black hole
    /** Its ring and disk drawn hot: brighter by these (GraphLook.ring and disk, hot). */
    const val HOT_RING = 1.7f
    const val HOT_DISK = 1.45f

    // the Neurons' hot halo
    /** The hot halo's tint A, and with the halo shader its gain and ring tint C; the soft disc's gain without it. */
    val HOT_TINT_A = floatArrayOf(0.75f, 1.0f, 1.0f)
    val HOT_TINT_C = floatArrayOf(0.75f, 0.95f, 1.0f)
    const val HOT_GAIN = 1.8f
    const val HOT_SOFT_GAIN = 0.9f
    /** A body's own halo, for comparison: gain 1, no ring tint. */
    const val HALO_GAIN = 1.0f

    // Performance's chosen point
    /** Drawn this much bigger, and never under [PERF_LEAST_DP] across, after the size clamp. */
    const val PERF_GROW = 1.4f
    const val PERF_LEAST_DP = 7f
    /** Its white band, as a share of the disc's radius: rising over 0.66-0.74, falling over 0.86-0.94. */
    val PERF_RING = floatArrayOf(0.66f, 0.74f, 0.86f, 0.94f)

    /** The chosen point's size in pixels from its drawn size [px] (already clamped), [density] pixels per dp. */
    fun perfSize(px: Float, density: Float): Float = max(px * PERF_GROW, PERF_LEAST_DP * density)

    /** How white the chosen point's band is at [r], its distance from the centre over its radius. */
    fun perfRing(r: Float): Float = smoothstep(PERF_RING[0], PERF_RING[1], r) * (1f - smoothstep(PERF_RING[2], PERF_RING[3], r))

    // Show links
    /** What is not in the chosen body's neighbourhood is drawn at this (focusLinks's 0.15). */
    const val FADED = 0.15f
    /** Performance's own links are drawn this much brighter under Show links, the rest at [FADED]. */
    const val PERF_FOCUS = 3f
}

/**
 * The pop: a kick of [KICK] to the chosen body's size spring (ω [OMEGA], damping [ZETA], stepped at
 * [RATE] Hz) and a bloom of [BLOOM] fading by e^(−dt / [BLOOM_FADE]), as GraphMotion.pop and stepPops.
 * Only while animations run: [kick] does nothing when [lively] is false.
 */
class MapPop(private val lively: Boolean) {
    var scale = 1f; private set
    var velocity = 0f; private set
    var bloom = 0f; private set

    /** Whether it is still moving. */
    val moving: Boolean get() = abs(1f - scale) >= 0.0005f || abs(velocity) >= 0.0005f || bloom > 0.0005f

    fun kick() {
        if (!lively) return
        velocity += KICK
        bloom = max(bloom, BLOOM)
    }

    /** Steps [dt] seconds. */
    fun step(dt: Float) {
        if (dt <= 0f) return
        bloom = if (bloom > 0.0005f) bloom * exp(-dt / BLOOM_FADE) else 0f
        if (abs(1f - scale) < 0.0005f && abs(velocity) < 0.0005f) { scale = 1f; velocity = 0f; return }
        val pieces = max(1, kotlin.math.ceil(dt * RATE).toInt())
        val h = dt / pieces
        var s = scale; var v = velocity
        repeat(pieces) {
            val pull = (1f - s) * OMEGA * OMEGA
            val brake = v * 2f * ZETA * OMEGA
            v += (pull - brake) * h
            s += v * h
        }
        scale = max(s, 0f); velocity = v
    }

    fun reset() { scale = 1f; velocity = 0f; bloom = 0f }

    /**
     * The mark's brightness with the bloom: iOS's glow opacity 0.85 + 0.35 × bloom over its resting
     * 0.85 (GraphMotion.swift:2136), so 1 at rest.
     */
    val glow: Float get() = (0.85f + 0.35f * bloom) / 0.85f

    /** The mark's size with the bloom: iOS's 1 + 0.6 × bloom (GraphMotion.swift:2128). */
    val swell: Float get() = 1f + 0.6f * bloom

    companion object {
        const val KICK = 3.2f
        const val BLOOM = 0.45f
        const val BLOOM_FADE = 0.35f
        const val OMEGA = 15f
        const val ZETA = 0.42f
        const val RATE = 240f
    }
}

/**
 * Show links' neighbourhood (focusLinks, GraphMotion.swift:1385-1414): the chosen body, the bodies
 * it links to, its members (for a folder, every idea under it) and the folders above it. In Space no
 * body has a parent, so it is the body and those it links to. Indices are the scene's.
 */
object MapFocus {
    /**
     * [links] are pairs of body indices; [parents] each body's parent (−1 for none) and [notes]
     * whether each is an idea, both null where the bodies nest in nothing (Space, Performance).
     */
    fun neighbourhood(chosen: Int, count: Int, links: IntArray, parents: IntArray? = null, notes: BooleanArray? = null): IntArray {
        if (chosen !in 0 until count) return IntArray(0)
        val near = java.util.TreeSet<Int>()
        near.add(chosen)
        var k = 0
        while (k + 1 < links.size) {
            val a = links[k]; val b = links[k + 1]
            if (a == chosen && b in 0 until count) near.add(b)
            if (b == chosen && a in 0 until count) near.add(a)
            k += 2
        }
        if (parents != null) {
            // its members: every idea with it above, as iOS's members list (only an idea is a member)
            for (i in 0 until count) {
                if (notes != null && !notes[i]) continue
                var up = parents.getOrElse(i) { -1 }
                var steps = 0
                while (up in 0 until count && steps <= count) {
                    if (up == chosen && (notes == null || !notes[up])) { near.add(i); break }
                    up = parents[up]; steps++
                }
            }
            var up = parents.getOrElse(chosen) { -1 }
            var steps = 0
            while (up in 0 until count && steps <= count) { near.add(up); up = parents[up]; steps++ }
        }
        return near.toIntArray()
    }

    /** Whether [link] (two body indices) is one of [chosen]'s own: drawn under Show links. */
    fun own(chosen: Int, a: Int, b: Int): Boolean = chosen >= 0 && (a == chosen || b == chosen)

    /** How many of a sorted [near] fall in [from] until [to]. */
    fun countIn(near: IntArray, from: Int, to: Int): Int {
        if (near.isEmpty() || to <= from) return 0
        fun first(at: Int): Int { var lo = 0; var hi = near.size; while (lo < hi) { val mid = (lo + hi) ushr 1; if (near[mid] < at) lo = mid + 1 else hi = mid }; return lo }
        return first(to) - first(from)
    }

    /** Whether a sorted [near] holds any of [from] until [to] (a summary's cell holds a neighbour). */
    fun holdsAny(near: IntArray, from: Int, to: Int): Boolean {
        if (near.isEmpty() || to <= from) return false
        var lo = 0; var hi = near.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (near[mid] < from) lo = mid + 1 else hi = mid }
        return lo < near.size && near[lo] < to
    }
}
