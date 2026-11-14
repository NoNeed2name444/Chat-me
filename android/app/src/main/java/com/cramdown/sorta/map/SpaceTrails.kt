package com.cramdown.sorta.map

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Sparks when a body moves fast (S05h2), a port of iOS's trail emitters: GraphMotion.swift's
// levels and slots (:2001-2068, fullSpeed and trailSpeed :549-551), GraphStyleAnimator's dress and
// aim (:203-246) from SpaceOptics.trail, and GraphLook.trail's damping (:947), born in the world
// and drawn additive (:951-955): a sun's plasma, a comet's icy dust, the others' dust, nothing from
// a black hole (its rate is 0).
//
// A few emitters, only while animations run: the held body has one first, then the fastest bodies
// over [trailSpeed], and a body keeps the one it has. Each throws its style's rate × how fast its
// body is going ([level], eased towards speed / [fullSpeed] over 0.15 s) × 1 at High or 0.4 at
// Smooth. Each spark lives its style's life ± 35 %, starts at its size ± 50 % and its speed ± 60 %,
// slows by e^(−1.5 dt), shrinks 1, 0.55, 0 over its life and cools through its style's colours to
// black (gone, under additive blending); it never fades otherwise. Pure Kotlin: SpaceTrailsTest.

/**
 * The sparks of one Space scene: [emitters] of them (0 with animations off: nothing at all), each
 * spark's size and speed times [scale], bodies counted as moving from [trailSpeed] and fully from
 * [fullSpeed] (world units a second), [smooth] the Smooth tier. Bodies are indices below [bodies].
 */
class SpaceTrails(val emitters: Int, val scale: Float, val fullSpeed: Float, val trailSpeed: Float, val smooth: Boolean,
                  bodies: Int, seed: Long = 1L) {
    companion object {
        const val EMITTERS = 4
        /** How fast a body's level follows its speed (seconds). */
        const val TAU = 0.15f
        /** The most sparks alive at once; the oldest give way. */
        const val CAPACITY = 1024
        /** The trail's damping a second (GraphLook.swift:947). */
        const val DAMPING = 1.5f
        /** Under the Universe, iOS's trail scale (GraphStyleAnimator.swift:160, GraphUniverseScene.swift:165). */
        const val UNIVERSE_SCALE = 1.2f
        /** iOS's speeds (GraphMotion.swift:549-551), in its units. */
        const val FULL_SPEED = 2.5f
        const val TRAIL_SPEED = 0.9f
        /** iOS's fallback page radius and an idea's share of it (Graph3DView.swift:1242). */
        const val PAGE_RADIUS = 0.3f
        const val IDEA_SHARE = 0.62f
        /** Floats a spark for the drawing: x, y, z, size now, r, g, b. */
        const val OUT_STRIDE = 7

        /**
         * The test clock (S05h2 item 3): when true, no spark is born, ages or moves. Inert (null) unless
         * an androidTest sets it, and the renderer reads it only in a debuggable app.
         */
        @Volatile var testFrozen: Boolean? = null

        /** 1 at High, 0.4 at Smooth (Shared/Space/GraphicsQuality.swift:128-133). */
        fun particleScale(smooth: Boolean): Float = if (smooth) 0.4f else 1f

        /**
         * One single style's scale: iOS's pageRadius / 0.2 (Graph3DView.swift:1333), its pageRadius
         * being an idea's radius / 0.62, so [nodeRadius] / 0.124; and as iOS holds pageRadius to
         * 0.2-0.6 (GraphFraming.swift:42), held to 1-3 (repair 8).
         */
        fun singleScale(nodeRadius: Float): Float = (nodeRadius / (IDEA_SHARE * 0.2f)).coerceIn(MIN_SINGLE_SCALE, MAX_SINGLE_SCALE)

        /** iOS's pageRadius bounds, 0.2 and 0.6 (GraphFraming.swift:42), over its 0.2. */
        const val MIN_SINGLE_SCALE = 1f
        const val MAX_SINGLE_SCALE = 3f

        /** A single style's speeds: iOS's taken in note radii at its fallback page radius, times [nodeRadius]. */
        fun singleSpeed(speed: Float, nodeRadius: Float): Float = speed / (IDEA_SHARE * PAGE_RADIUS) * nodeRadius

        /** The sparks for a Space scene: under the Universe ([universe]) or one single style. */
        fun of(lively: Boolean, universe: Boolean, nodeRadius: Float, smooth: Boolean, bodies: Int, seed: Long = 1L): SpaceTrails =
            if (universe) SpaceTrails(if (lively) EMITTERS else 0, UNIVERSE_SCALE, FULL_SPEED, TRAIL_SPEED, smooth, bodies, seed)
            else SpaceTrails(if (lively) EMITTERS else 0, singleScale(nodeRadius), singleSpeed(FULL_SPEED, nodeRadius),
                singleSpeed(TRAIL_SPEED, nodeRadius), smooth, bodies, seed)

        /** The colour [f] (0...1) through a spark's life: [colours] then black, evenly. */
        fun ramp(colours: List<FloatArray>, f: Float, out: FloatArray) {
            val n = colours.size // keys 0..n, the last black
            val x = f.coerceIn(0f, 1f) * n
            val i = min(floor(x).toInt(), n - 1).coerceAtLeast(0)
            val t = x - i
            val a = colours[i]
            val b = if (i + 1 < n) colours[i + 1] else null
            for (c in 0 until 3) out[c] = a[c] + ((b?.get(c) ?: 0f) - a[c]) * t
        }

        /** A spark's size through its life: 1, 0.55, 0 at 0, 0.5 and 1. */
        fun shrink(f: Float): Float {
            val x = f.coerceIn(0f, 1f)
            return if (x < 0.5f) 1f - 0.45f * (x / 0.5f) else 0.55f * (1f - (x - 0.5f) / 0.5f)
        }
    }

    private val random = Random(seed)
    private val particleScale = particleScale(smooth)
    /** Each body's level (0...1), how fully it counts as moving. */
    val level = FloatArray(bodies)
    /** Each emitter's body, −1 for none. */
    val owner = IntArray(emitters) { -1 }
    private val look = arrayOfNulls<SpaceOptics.Trail>(emitters)
    private val code = IntArray(emitters)
    private val direction = Array(emitters) { Vec3(0f, 1f, 0f) }
    private val owed = FloatArray(emitters)
    /** Each emitter's body's place at its last step (repair 8), null before its first. */
    private val last = arrayOfNulls<Vec3>(emitters)
    /** Each emitter's births a second this frame. */
    val rate = FloatArray(emitters)
    private val picks = IntArray(emitters); private val pickSpeed = FloatArray(emitters); private var pickCount = 0
    private val wanted = IntArray(emitters); private var wantedCount = 0

    // the sparks, a ring: a new one takes the oldest place
    private val px = FloatArray(CAPACITY); private val py = FloatArray(CAPACITY); private val pz = FloatArray(CAPACITY)
    private val vx = FloatArray(CAPACITY); private val vy = FloatArray(CAPACITY); private val vz = FloatArray(CAPACITY)
    private val age = FloatArray(CAPACITY); private val life = FloatArray(CAPACITY) // life 0: none
    private val size = FloatArray(CAPACITY)
    private val bornSpeed = FloatArray(CAPACITY)
    private val style = IntArray(CAPACITY)
    private var head = 0
    /** Sparks alive now. */
    var live = 0; private set
    /** Sparks thrown since it was made. */
    var thrownTotal = 0L; private set
    private val colour = FloatArray(3)
    private val trails = Array(6) { SpaceOptics.trail(it) }

    /** Each spark's life, size and speed as born, for the tests. */
    fun bornLife(i: Int) = life[i]
    fun bornSize(i: Int) = size[i]
    fun speedOf(i: Int) = sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i])
    /** Spark [i]'s speed as dressed, before any slowing. */
    fun speedAtBirth(i: Int) = bornSpeed[i]
    fun placeOf(i: Int) = Vec3(px[i], py[i], pz[i])
    fun ageOf(i: Int) = age[i]
    fun velocityOf(i: Int) = Vec3(vx[i], vy[i], vz[i])
    /** The last spark born's index, −1 for none. */
    val newest: Int get() = if (thrownTotal == 0L) -1 else (head + CAPACITY - 1) % CAPACITY
    /** The emitting direction of emitter [slot]. */
    fun directionOf(slot: Int): Vec3 = direction[slot]

    /**
     * One frame [dt] seconds on: the bodies drawn ([drawn], the first [drawnCount]) and the held one
     * ([held], −1 for none) ease their levels by their [velocity]; the emitters go to the held body and
     * the fastest; each throws along its body's way this step ([place] then and now), each spark aged by
     * its share of [dt]; the sparks age, slow and move. [frozen] (the test
     * clock) stops all of it. [styleOf] is a body's style code.
     */
    fun step(dt: Float, drawn: IntArray, drawnCount: Int, held: Int, velocity: (Int) -> Vec3, place: (Int) -> Vec3,
             styleOf: (Int) -> Int, frozen: Boolean = false) {
        if (emitters == 0 || frozen) return
        val h = dt.coerceAtLeast(0f)
        age(h)
        val ease = 1f - exp(-h / TAU)
        pickCount = 0
        val slots = if (held >= 0) emitters - 1 else emitters
        var heldDrawn = false
        for (k in 0 until drawnCount) {
            val i = drawn[k]
            if (i !in level.indices) continue
            if (i == held) heldDrawn = true
            val speed = velocity(i).length()
            ease(i, speed, ease)
            if (i != held && speed > trailSpeed && slots > 0) consider(i, speed, slots)
        }
        if (held in level.indices && !heldDrawn) ease(held, velocity(held).length(), ease)
        // the held body first if it is drawn, then the fastest
        wantedCount = 0
        if (held >= 0 && heldDrawn) wanted[wantedCount++] = held
        for (p in 0 until pickCount) if (wantedCount < emitters) wanted[wantedCount++] = picks[p]
        for (s in 0 until emitters) if (owner[s] >= 0 && !isWanted(owner[s])) owner[s] = -1
        for (w in 0 until wantedCount) {
            val body = wanted[w]
            if (owner.contains(body)) continue
            val free = owner.indexOf(-1)
            if (free < 0) continue
            owner[free] = body
            dress(free, styleOf(body))
        }
        for (s in 0 until emitters) {
            val body = owner[s]
            if (body < 0) { rate[s] = 0f; owed[s] = 0f; last[s] = null; continue }
            val v = velocity(body)
            val at = place(body)
            // where the body was a step ago: its last place, or for a slot just handed it, back along its velocity
            val from = last[s] ?: (at - v * h)
            last[s] = at
            val trail = look[s] ?: continue
            if (trail.backward) {
                val speed = v.length()
                if (speed > 0.01f) direction[s] = v * (-1f / speed)
            }
            rate[s] = trail.rate * level[body] * particleScale
            val owedBefore = owed[s]
            owed[s] += rate[s] * h
            val births = floor(owed[s]).toInt()
            owed[s] -= births
            // as a continuous emitter (repair 8): birth m comes when what is owed crosses m, at
            // τ = (m − owed before) / rate into the step, on the segment from the body's last place to
            // its place now, and is already h − τ old
            for (m in 1..births) {
                val tau = if (rate[s] > 0f) ((m - owedBefore) / rate[s]).coerceIn(0f, h) else h
                val f = if (h > 0f) tau / h else 1f
                birth(s, from + (at - from) * f, h - tau)
            }
        }
    }

    private fun ease(i: Int, speed: Float, ease: Float) {
        val goal = min(speed / fullSpeed, 1f)
        level[i] += (goal - level[i]) * ease
    }

    private fun isWanted(body: Int): Boolean {
        for (w in 0 until wantedCount) if (wanted[w] == body) return true
        return false
    }

    /** Keeps the fastest [limit] bodies seen so far, fastest first (iOS consider). */
    private fun consider(i: Int, speed: Float, limit: Int) {
        if (pickCount == limit) {
            if (speed <= pickSpeed[pickCount - 1]) return
            pickCount--
        }
        var at = pickCount
        while (at > 0 && pickSpeed[at - 1] < speed) { picks[at] = picks[at - 1]; pickSpeed[at] = pickSpeed[at - 1]; at-- }
        picks[at] = i; pickSpeed[at] = speed
        pickCount++
    }

    /** Dresses emitter [slot] in style [styleCode]'s sparks as it is handed a body (iOS dressTrail). */
    private fun dress(slot: Int, styleCode: Int) {
        look[slot] = trails[styleCode.coerceIn(0, 5)]
        code[slot] = styleCode
        direction[slot] = Vec3(0f, 1f, 0f)
        owed[slot] = 0f
        last[slot] = null
    }

    private fun spread() = random.nextFloat() * 2f - 1f

    /** A spark from emitter [slot] born at [at], already [old] seconds old: aged, slowed and moved by that as [age] would. */
    private fun birth(slot: Int, at: Vec3, old: Float = 0f) {
        val trail = look[slot] ?: return
        val i = head
        head = (head + 1) % CAPACITY
        px[i] = at.x; py[i] = at.y; pz[i] = at.z
        life[i] = trail.life * (1f + 0.35f * spread())
        size[i] = trail.size * scale * (1f + 0.5f * spread())
        val speed = trail.speed * scale * (1f + 0.6f * spread())
        bornSpeed[i] = speed
        val d = cone(direction[slot], trail.spread)
        vx[i] = d.x * speed; vy[i] = d.y * speed; vz[i] = d.z * speed
        age[i] = 0f
        if (old > 0f) {
            age[i] = old
            val slow = exp(-DAMPING * old)
            vx[i] *= slow; vy[i] *= slow; vz[i] *= slow
            px[i] += vx[i] * old; py[i] += vy[i] * old; pz[i] += vz[i] * old
        }
        style[i] = code[slot]
        thrownTotal++
    }

    /** A direction within [degrees] (the cone's half-angle; 180 all round) of [axis], evenly over its cap. */
    fun cone(axis: Vec3, degrees: Float): Vec3 {
        val half = (degrees.coerceIn(0f, 180f) * PI / 180.0).toFloat()
        val z = 1f - random.nextFloat() * (1f - cos(half))
        val r = sqrt((1f - z * z).coerceAtLeast(0f))
        val phi = random.nextFloat() * 2f * PI.toFloat()
        val n = axis.normalized()
        val helper = if (kotlin.math.abs(n.y) < 0.99f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val e1 = n.cross(helper).normalized()
        val e2 = n.cross(e1)
        return n * z + e1 * (r * cos(phi)) + e2 * (r * sin(phi))
    }

    /** The sparks [h] seconds on: older, slower by e^(−1.5 h), moved; the dead let go. */
    private fun age(h: Float) {
        val slow = exp(-DAMPING * h)
        var n = 0
        for (i in 0 until CAPACITY) {
            if (life[i] <= 0f) continue
            age[i] += h
            if (age[i] >= life[i]) { life[i] = 0f; continue }
            vx[i] *= slow; vy[i] *= slow; vz[i] *= slow
            px[i] += vx[i] * h; py[i] += vy[i] * h; pz[i] += vz[i] * h
            n++
        }
        live = n
    }

    /** Whether spark [i] is alive. */
    fun alive(i: Int): Boolean = life[i] > 0f && age[i] < life[i]

    /** Live sparks now (counted afresh, births included). */
    fun count(): Int {
        var n = 0
        for (i in 0 until CAPACITY) if (alive(i)) n++
        live = n
        return n
    }

    /** Spark [i]'s colour now, through its style's colours to black. */
    fun colourOf(i: Int, out: FloatArray) = ramp(trails[style[i].coerceIn(0, 5)].colours, age[i] / life[i], out)

    /** Spark [i]'s size now. */
    fun sizeNow(i: Int): Float = size[i] * shrink(age[i] / life[i])

    /** Writes every live spark into [out] ([OUT_STRIDE] floats each), growing it as needed; how many. */
    fun write(out: FloatArray): Int {
        var n = 0
        for (i in 0 until CAPACITY) {
            if (!alive(i)) continue
            val o = n * OUT_STRIDE
            if (o + OUT_STRIDE > out.size) break
            out[o] = px[i]; out[o + 1] = py[i]; out[o + 2] = pz[i]; out[o + 3] = sizeNow(i)
            colourOf(i, colour)
            out[o + 4] = colour[0]; out[o + 5] = colour[1]; out[o + 6] = colour[2]
            n++
        }
        live = n
        return n
    }
}
