package com.cramdown.sorta.map

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// How a body leaves Space's map (S05h2), a port of iOS GraphDeath.swift:42-307 whole, with
// GraphDeathScene.swift's stage (GraphDeathStage, :62-217) and its tidal axis and spin angle
// (:228-256, :306-311), and the departures GraphMotion.swift:1174-1200 makes.
//
// When a note or folder is deleted its body does not vanish: it shrinks and fades where it was,
// the way the thing it stands for dies, while its links draw back into it; nothing waits for it
// (the new map is live underneath, and the dying body can be neither picked, named nor dragged).
// This file is the plan (which effect for which body, how long, its phases, the curves the body
// follows) and the stage that steps it; SpaceBodies draws the dying bodies and their links. The
// death effects' own pieces (a supernova's flash, a nebula's ring, debris) are a later step, and a
// Neurons cell's apoptosis is ported here but unused until S05i.
//
// Animations off, as iOS under Reduce Motion: a quick fade only, the links whole until it ends and
// the set faded over the longest. Pure Kotlin: MapDeathTest.

/** What a body is, as far as its death goes (iOS GraphDeathKind). */
enum class DeathKind { ROCKY, MOON, GAS_GIANT, SMALL_STAR, BIG_STAR, BLACK_HOLE, PULSAR, COMET, CELL, PLAIN }

/** How it dies (iOS GraphDeathEffect). */
enum class DeathEffect { TIDAL_DISRUPTION, ATMOSPHERE_STRIPPED, PLANETARY_NEBULA, SUPERNOVA, EVAPORATION, SPIN_DOWN, COMET_BREAKUP, APOPTOSIS, FADE }

/** One stage of a death, in seconds from its start. */
data class DeathPhase(val name: String, val start: Double, val end: Double)

/**
 * A body's death: its effect, how long it all takes, its stages, how many particles its effects may
 * throw in all, and when its links have drawn back into it (iOS GraphDeathPlan).
 */
data class DeathPlan(val effect: DeathEffect, val duration: Double, val phases: List<DeathPhase>, val particles: Int, val linksGone: Double) {
    fun phase(name: String): DeathPhase? = phases.firstOrNull { it.name == name }

    /** How far through stage [name] it is at [t] (0 before, 1 after). */
    fun progress(name: String, t: Double): Double {
        val p = phase(name) ?: return 0.0
        val span = max(p.end - p.start, 0.0001)
        return ((t - p.start) / span).coerceIn(0.0, 1.0)
    }
}

/** One dying body as the plan sees it: what it is, its radius and how much it held. */
data class DeathInput(val kind: DeathKind, val radius: Float, val count: Int)

/** A dying body's size at a moment, times its size when it began: [along] its tidal axis and [across]. */
data class DeathSize(val along: Float, val across: Float)

/**
 * A body of the scene being replaced that the new one lacks (iOS GraphDeparture): its id, its index in
 * the old scene ([index], for its seed), where it was, its drawn radius × its pop ([radius]), the radius
 * its shader takes × the pop ([shader]) and where its links stopped × the pop ([reach]), its kind, what
 * it held, its style code and look, and its partners: each the other end's id and the seed its link
 * was drawn with.
 */
class MapDeparture(
    val id: String, val index: Int, val place: Vec3, val radius: Float, val shader: Float, val reach: Float,
    val kind: DeathKind, val count: Int, val code: Int, val look: Float, val partners: List<Pair<String, Float>>,
)

/** One body of the old scene, as the departures read it (null for a body not shown). */
class DepartureBody(val place: Vec3, val radius: Float, val shader: Float, val reach: Float, val pop: Float,
                    val kind: DeathKind, val count: Int, val code: Int, val look: Float)

object MapDeath {
    /** At most this many full effects at once (High, Smooth). */
    fun fullAllowed(smooth: Boolean): Int = if (smooth) 3 else 6

    /** A folder at least this full is a big star (a supernova). */
    const val BIG_STAR_COUNT = 8

    /** The first step of a death's clock, at most: a long first frame still starts it at the start. */
    const val FIRST_STEP = 1.0 / 30.0
    /** Every later step, at most (GlMapRenderer's dt). */
    const val MAX_STEP = 0.25

    /**
     * The test clock (S05h2 item 3): when set, every death's clock is held at this many seconds. Inert
     * (null) unless an androidTest sets it, and the renderer reads it only in a debuggable app.
     */
    @Volatile var testHold: Double? = null

    /** The kind a Universe role makes, by its iOS name (GraphDeath.swift:120-131). */
    fun kind(universeRole: String, count: Int): DeathKind = when (universeRole) {
        "galaxy" -> DeathKind.BLACK_HOLE
        "home" -> DeathKind.BIG_STAR
        "star" -> if (count >= BIG_STAR_COUNT) DeathKind.BIG_STAR else DeathKind.SMALL_STAR
        "gasGiant" -> DeathKind.GAS_GIANT
        "moon" -> DeathKind.MOON
        "pulsar" -> DeathKind.PULSAR
        "comet", "oort" -> DeathKind.COMET
        else -> DeathKind.ROCKY
    }

    fun kind(role: UniverseRole, count: Int): DeathKind = kind(role.raw, count)

    /** The kind a single look's style makes, by its iOS name (GraphDeath.swift:133-143). */
    fun kind(style: String): DeathKind = when (style) {
        "blackHole" -> DeathKind.BLACK_HOLE
        "sun" -> DeathKind.SMALL_STAR
        "gasGiant" -> DeathKind.GAS_GIANT
        "pulsar" -> DeathKind.PULSAR
        "comet" -> DeathKind.COMET
        "rocky" -> DeathKind.ROCKY
        else -> DeathKind.PLAIN
    }

    /** The same by Android's style code (SpaceStyle). */
    fun kind(styleCode: Int): DeathKind = kind(when (styleCode) {
        SpaceStyle.BLACK_HOLE.code -> "blackHole"
        SpaceStyle.SUN.code -> "sun"
        SpaceStyle.GAS_GIANT.code -> "gasGiant"
        SpaceStyle.PULSAR.code -> "pulsar"
        SpaceStyle.COMET.code -> "comet"
        SpaceStyle.ROCKY.code -> "rocky"
        else -> ""
    })

    /**
     * Every dying body's plan: full effects for the biggest few (by radius, then what they held, then
     * their order), a shrink-and-fade for the rest; a quick fade for all when the space holds still.
     */
    fun plans(dying: List<DeathInput>, still: Boolean, smooth: Boolean): List<DeathPlan> {
        val order = dying.indices.sortedWith { a, b ->
            when {
                dying[a].radius != dying[b].radius -> dying[b].radius.compareTo(dying[a].radius)
                dying[a].count != dying[b].count -> dying[b].count.compareTo(dying[a].count)
                else -> a.compareTo(b)
            }
        }
        val full = HashSet<Int>()
        val allowed = fullAllowed(smooth)
        for (i in order) if (full.size < allowed && dying[i].kind != DeathKind.PLAIN) full.add(i)
        return dying.indices.map { i ->
            when {
                still -> quickFade()
                i in full -> plan(dying[i].kind, smooth)
                else -> fade(0.8)
            }
        }
    }

    /** Animations off, a still space: gone in a third of a second. */
    fun quickFade(): DeathPlan = DeathPlan(DeathEffect.FADE, 0.3, listOf(DeathPhase("fade", 0.0, 0.3)), 0, 0.3)

    fun fade(seconds: Double): DeathPlan =
        DeathPlan(DeathEffect.FADE, seconds, listOf(DeathPhase("fade", 0.0, seconds)), 0, seconds * 0.8)

    /** One body's full effect (GraphDeath.swift:185-228). */
    fun plan(kind: DeathKind, smooth: Boolean): DeathPlan {
        val share = if (smooth) 0.4 else 1.0
        fun sparks(n: Int) = max((n * share).toInt(), 6)
        fun p(name: String, a: Double, b: Double) = DeathPhase(name, a, b)
        return when (kind) {
            DeathKind.ROCKY, DeathKind.MOON -> DeathPlan(DeathEffect.TIDAL_DISRUPTION, 1.9,
                listOf(p("stretch", 0.0, 0.6), p("shatter", 0.5, 0.85), p("ring", 0.5, 1.9)), sparks(70), 0.7)
            DeathKind.GAS_GIANT -> DeathPlan(DeathEffect.ATMOSPHERE_STRIPPED, 2.1,
                listOf(p("strip", 0.0, 1.4), p("core", 0.9, 2.1)), sparks(80), 0.9)
            DeathKind.SMALL_STAR -> DeathPlan(DeathEffect.PLANETARY_NEBULA, 2.4,
                listOf(p("swell", 0.0, 0.7), p("puff", 0.6, 2.4), p("dwarf", 0.7, 2.4)), sparks(60), 1.0)
            DeathKind.BIG_STAR -> DeathPlan(DeathEffect.SUPERNOVA, 2.3,
                listOf(p("collapse", 0.0, 0.25), p("flash", 0.25, 0.8), p("shock", 0.3, 2.3)), sparks(110), 0.5)
            DeathKind.BLACK_HOLE -> DeathPlan(DeathEffect.EVAPORATION, 2.0,
                listOf(p("shrink", 0.0, 1.7), p("pop", 1.7, 2.0)), sparks(40), 1.2)
            DeathKind.PULSAR -> DeathPlan(DeathEffect.SPIN_DOWN, 1.9,
                listOf(p("spin", 0.0, 1.6), p("fade", 1.1, 1.9)), 0, 1.2)
            DeathKind.COMET -> DeathPlan(DeathEffect.COMET_BREAKUP, 1.9,
                listOf(p("crack", 0.0, 0.4), p("fragments", 0.3, 1.9), p("gas", 0.2, 1.5)), sparks(50), 0.8)
            DeathKind.CELL -> DeathPlan(DeathEffect.APOPTOSIS, 2.3,
                listOf(p("shrink", 0.0, 0.6), p("bleb", 0.3, 1.2), p("condense", 0.5, 1.1), p("fragment", 1.0, 1.6), p("bodies", 1.2, 2.3)),
                sparks(40), 1.0)
            DeathKind.PLAIN -> fade(0.8)
        }
    }

    // ---- the body's own curves ----

    fun smooth(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** The body's size at [t], along its tidal axis and across, times its size when it began to die. */
    fun scale(plan: DeathPlan, t: Double): DeathSize {
        var along = 1.0
        val s: Double = when (plan.effect) {
            DeathEffect.TIDAL_DISRUPTION -> {
                val stretch = smooth(plan.progress("stretch", t))
                val gone = smooth(plan.progress("shatter", t))
                along = 1 + 0.8 * stretch
                (1 - 0.35 * stretch) * (1 - gone)
            }
            DeathEffect.ATMOSPHERE_STRIPPED -> {
                val strip = smooth(plan.progress("strip", t))
                val core = smooth(plan.progress("core", t))
                (1 - 0.55 * strip) * (1 - core)
            }
            DeathEffect.PLANETARY_NEBULA -> {
                val swell = smooth(plan.progress("swell", t))
                val collapse = smooth(plan.progress("dwarf", t) * 3)
                val giant = 1 + 0.5 * swell
                giant * (1 - collapse) + 0.2 * collapse
            }
            DeathEffect.SUPERNOVA -> {
                val crush = smooth(plan.progress("collapse", t))
                if (t < (plan.phase("flash")?.start ?: 0.25) + 0.05) 1 - 0.2 * crush else 0.0
            }
            // Hawking: the mass goes as (1 - t/T)^(1/3), faster at the end
            DeathEffect.EVAPORATION -> max(1 - plan.progress("shrink", t), 0.0).pow(1.0 / 3.0)
            DeathEffect.SPIN_DOWN -> 1 - smooth(plan.progress("fade", t))
            DeathEffect.COMET_BREAKUP -> 1 - smooth(plan.progress("fragments", t) * 2)
            DeathEffect.APOPTOSIS -> {
                val shrink = smooth(plan.progress("shrink", t))
                val apart = smooth(plan.progress("fragment", t))
                (1 - 0.25 * shrink) * (1 - apart)
            }
            DeathEffect.FADE -> 1 - 0.4 * smooth(plan.progress("fade", t))
        }
        return DeathSize((s * along).toFloat(), s.toFloat())
    }

    /** The body's opacity at [t] (0 once it is gone). */
    fun opacity(plan: DeathPlan, t: Double): Float {
        val o = when (plan.effect) {
            DeathEffect.PLANETARY_NEBULA -> 1 - smooth((t - 1.6) / max(plan.duration - 1.6, 0.01))
            DeathEffect.SPIN_DOWN -> 1 - 0.7 * smooth(plan.progress("spin", t))
            DeathEffect.FADE -> 1 - smooth(plan.progress("fade", t))
            else -> if (t >= plan.duration) 0.0 else 1.0
        }
        return o.coerceIn(0.0, 1.0).toFloat()
    }

    /** How far a dying body's links still reach out of it at [t]: 1 − smoothstep(t / linksGone). */
    fun linkReach(plan: DeathPlan, t: Double): Float {
        val x = (t / max(plan.linksGone, 0.01)).coerceIn(0.0, 1.0)
        return (1 - smooth(x)).toFloat()
    }

    /** A pulsar's spin rate at [t], as a share of its rate when it began to die. */
    fun spin(plan: DeathPlan, t: Double): Float {
        val x = plan.progress("spin", t)
        return (max(1 - x, 0.0) * max(1 - x, 0.0)).toFloat()
    }

    /** How far a spinning-down body has turned at [t], radians: the integral of (1 − x)² (GraphDeathScene.swift:253-256). */
    fun spinAngle(plan: DeathPlan, t: Double): Float {
        if (plan.effect != DeathEffect.SPIN_DOWN) return 0f
        val x = plan.progress("spin", t)
        val span = plan.phase("spin")?.end ?: 1.6
        return (9 * span * (1 - (1 - x).pow(3)) / 3).toFloat()
    }

    /**
     * The way the tides pull on body [id]: a fixed tilt from its id, as iOS's code has it (its comment's
     * "towards its first partner" is not what it does). Swift's uuidString is upper case.
     */
    fun tidalAxis(id: String): Vec3 {
        val seed = (GraphUniverse.fnv(id.uppercase()) % 1000uL).toFloat() / 1000f
        val a = seed * 6.2831853f
        return Vec3(cos(a), 0.35f, sin(a)).normalized()
    }

    /**
     * Each body of the old scene ([ids], read through [body]) that [keeping] lacks, shown and with its
     * pop above 0.05, with its partners along [links] (pairs of old scene indices), leaving out a moon's
     * link to its planet (kind 3 in [kinds], one a link, when given). [seed] is a link's seed by its two
     * old indices (GraphMotion.swift:1174-1200).
     */
    fun departures(ids: List<String>, keeping: Set<String>, links: IntArray, kinds: IntArray? = null,
                   seed: (Int, Int) -> Float = { _, _ -> 0f }, body: (Int) -> DepartureBody?): List<MapDeparture> {
        val gone = HashMap<Int, DepartureBody>()
        for (i in ids.indices) {
            if (ids[i] in keeping) continue
            val b = body(i) ?: continue
            if (b.pop <= 0.05f) continue
            gone[i] = b
        }
        if (gone.isEmpty()) return emptyList()
        val partners = HashMap<Int, ArrayList<Pair<String, Float>>>()
        var e = 0
        while (e + 1 < links.size) {
            val a = links[e]; val b = links[e + 1]
            val k = e / 2
            e += 2
            if (a !in ids.indices || b !in ids.indices) continue
            if (kinds != null && k < kinds.size && kinds[k] == 3) continue
            if (a in gone) partners.getOrPut(a) { ArrayList() }.add(ids[b] to seed(a, b))
            if (b in gone) partners.getOrPut(b) { ArrayList() }.add(ids[a] to seed(a, b))
        }
        return gone.keys.sorted().map { i ->
            val b = gone.getValue(i)
            MapDeparture(ids[i], i, b.place, b.radius * b.pop, b.shader * b.pop, b.reach * b.pop, b.kind, b.count, b.code, b.look,
                partners[i] ?: emptyList())
        }
    }
}

/**
 * The deaths playing in one scene (iOS GraphDeathStage): made by the new scene's renderer from the
 * departures the old one handed over, resolved against the new scene, and stepped once a frame on the
 * GL thread. [lively] false is a still space; [smooth] the Smooth tier.
 */
class MapDeathStage(val dying: List<MapDeparture>, val plans: List<DeathPlan>, val lively: Boolean) {
    companion object {
        /** The stage for [departures], or null for none. */
        fun make(departures: List<MapDeparture>, lively: Boolean, smooth: Boolean): MapDeathStage? {
            if (departures.isEmpty()) return null
            val inputs = departures.map { DeathInput(it.kind, it.radius, it.count) }
            return MapDeathStage(departures, MapDeath.plans(inputs, still = !lively, smooth = smooth), lively)
        }
    }

    /** The longest plan, seconds. */
    val longest: Double = plans.maxOfOrNull { it.duration } ?: 0.0
    /** Seconds since it began. */
    var elapsed = 0.0; private set
    var finished = false; private set
    /**
     * Whether its clock has begun (S05h2 repair 2): on the first frame after the new scene was handed
     * to the screen ([frame]'s shown). Until then it is posed at its start and not counted.
     */
    var started = false; private set
    private var stepped = false
    private var tallied = false

    /** Each dying link: its dying end (a slot), its other end in the new scene (or −1) or another dying slot (or −1), and its seed. */
    var linkDying = IntArray(0); private set
    var linkOther = IntArray(0); private set
    var linkMate = IntArray(0); private set
    var linkSeed = FloatArray(0); private set
    val linkCount: Int get() = linkDying.size

    /** This step: each slot's size and opacity, each link's reach (0 when not drawn), and the set's opacity. */
    val size = Array(dying.size) { DeathSize(1f, 1f) }
    val opacity = FloatArray(dying.size) { 1f }
    var reach = FloatArray(0); private set
    var setOpacity = 1f; private set
    /** The clock (SpaceOptics' shaders' clock) when a slot was first drawn, for a pulsar's beams; NaN before. */
    val startClock = FloatArray(dying.size) { Float.NaN }

    /** The tidal axis of each slot. */
    val axes: Array<Vec3> = Array(dying.size) { MapDeath.tidalAxis(dying[it].id) }

    /**
     * Finds each dying link's other end in the new scene by id ([index], −1 for none); one dying too
     * is joined to it, once a pair (GraphDeathScene.swift:148-167).
     */
    fun resolve(index: (String) -> Int) {
        val dyingAt = HashMap<String, Int>()
        for ((k, d) in dying.withIndex()) dyingAt[d.id] = k
        val seen = HashSet<String>()
        val a = ArrayList<Int>(); val o = ArrayList<Int>(); val m = ArrayList<Int>(); val s = ArrayList<Float>()
        for ((k, d) in dying.withIndex()) {
            for ((other, seed) in d.partners) {
                val j = index(other)
                if (j >= 0) { a.add(k); o.add(j); m.add(-1); s.add(seed); continue }
                val mate = dyingAt[other] ?: continue
                if (!seen.add(LinkMemory.key(d.id, other))) continue
                a.add(k); o.add(-1); m.add(mate); s.add(seed)
            }
        }
        linkDying = a.toIntArray(); linkOther = o.toIntArray(); linkMate = m.toIntArray(); linkSeed = s.toFloatArray()
        reach = FloatArray(linkDying.size)
    }

    /**
     * One frame [dt] seconds on: the first step at most [MapDeath.FIRST_STEP], later ones at most
     * [MapDeath.MAX_STEP]; [hold], the test clock, holds it at that many seconds instead
     * (GraphDeathScene.swift:172-216).
     */
    fun step(dt: Double, hold: Double? = null) {
        if (finished) return
        elapsed = if (hold != null) hold else elapsed + max(dt, 0.0).coerceAtMost(if (stepped) MapDeath.MAX_STEP else MapDeath.FIRST_STEP)
        stepped = true
        started = true
        pose()
        if (elapsed > longest + 0.1) finished = true
    }

    /**
     * One renderer frame [dt] seconds on (S05h2 repair 2). A new scene's first frames come on a new GL
     * context, and a software GL takes seconds to hand them to the screen (CI's emulator: the second
     * frame 1.4 s after the first on the phone, over 2 s on the tablet). So until [shown], a frame of
     * this scene already on the screen, the death is drawn at its start (whole, its links whole) and
     * neither stepped nor counted, as iOS's copy plays on the scene's own clock, which moves only as
     * SceneKit draws the new scene (GraphDeathScene.swift:246-260, an SCNAction; the links stepped by frames,
     * GraphMotion.swift:1597-1600). From the first shown frame on it steps ([step], the first step at
     * most [MapDeath.FIRST_STEP]); that frame returns the totals to add ([tally]), once.
     */
    fun frame(dt: Double, shown: Boolean, hold: Double? = null): IntArray? {
        if (finished) return null
        if (!started && !shown) { pose(); return null }
        step(dt, hold)
        return tally()
    }

    /** Each slot's size and opacity, each link's reach and the set's opacity at [elapsed]. */
    private fun pose() {
        for (k in dying.indices) {
            size[k] = MapDeath.scale(plans[k], elapsed)
            opacity[k] = MapDeath.opacity(plans[k], elapsed)
        }
        for (j in 0 until linkCount) {
            val d = linkDying[j]
            var r = MapDeath.linkReach(plans[d], elapsed)
            if (!lively) r = if (elapsed < plans[d].duration) 1f else 0f
            val mate = linkMate[j]
            // both ends dying: the mate's own reach, whatever the motion setting (GraphDeathScene.swift:196-198)
            if (mate >= 0) r = min(r, MapDeath.linkReach(plans[mate], elapsed))
            reach[j] = if (r > 0.001f) r else 0f
        }
        setOpacity = if (lively) 1f else (1 - min(elapsed / max(longest, 0.01), 1.0)).toFloat()
    }

    /** Slot [k]'s radius for its links' ends: radius × max(scale, 0.05) (GraphDeathScene.swift:183). */
    fun linkRadius(k: Int): Float = max(size[k].across, 0.05f)

    /** Whether slot [k] is still before its own plan's end. */
    fun alive(k: Int): Boolean = !finished && elapsed < plans[k].duration

    /** Slots before their own plan's end now. */
    fun aliveCount(): Int = if (finished) 0 else dying.indices.count { alive(it) }

    /** The bodies and links to add to the totals, once: [died, links]; null after the first call. */
    fun tally(): IntArray? {
        if (tallied) return null
        tallied = true
        return intArrayOf(dying.size, linkCount)
    }

    /** Slot [k]'s pulsar beam turn since it began (radians). */
    fun spinAngle(k: Int): Float = MapDeath.spinAngle(plans[k], elapsed)

    /** The beams' phase for slot [k], from the clock when it began; and its seed. */
    fun beamPhase(k: Int, clock: Float, seed: Float): Float {
        if (startClock[k].isNaN()) startClock[k] = clock
        return SpaceOptics.beamPhase(startClock[k], seed) + spinAngle(k)
    }

}
