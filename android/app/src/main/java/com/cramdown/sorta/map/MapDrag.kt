package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Hold to move (S05g2): a body held on the map is picked up, follows the finger and springs home
// when let go, as iOS's GraphMotion does it (grab, drag, release, the frame step, integrate and
// integrateOrbits, GraphMotion.swift:1246-1283, :1617-1667, :1703-1798). Plain Kotlin, no Android:
// MapDragTest checks it. Nothing is saved: every body's home stays where the scene put it.

/**
 * Which spring run a scene has (GraphMotion.swift:1638): [ORBITS], each body springing to its
 * parent's place plus its own orbit and carried by what its parent moved (the Neurons, Space's
 * Universe); [LINKS], each body springing home and each link holding its homes' distance, so a
 * moved body pulls its neighbours (Space under one single style).
 */
enum class MapDragPath { ORBITS, LINKS }

/**
 * The spring run for [count] bodies. A body's offset is from its own home, the place it is drawn at
 * with no drag ([home], at the orbit clock's [orbitTime]); a body is drawn at its home plus its own
 * offset only ([placed]), never its parents' too. Only bodies off home are stepped, so a map at rest
 * costs nothing (Android's own rule, for the frame budget: iOS steps every body until it rests with
 * animations off, :1667).
 *
 * [unit] is the scene's note radius, which scales the fling cap ([FLING_LIMIT_RADII]) and the rest
 * thresholds. [parents] (the orbit path) gives each body's parent, −1 for none, every parent below
 * its child as `p < i` needs (:1782). [links] (the link path) are pairs of body indices. [lively]:
 * animations run (else no fling, critical damping and a full carry, :578-580, :1279, :1773).
 *
 * The finger's calls ([grab], [drag], [release]) come from the main thread and [step] from the GL
 * thread, so they are synchronised; offsets are read on the GL thread, which alone writes them.
 */
class MapDrag(
    val count: Int,
    val path: MapDragPath,
    val unit: Float,
    private val home: (index: Int, time: Double) -> Vec3,
    parents: IntArray? = null,
    links: IntArray = IntArray(0),
    lively: Boolean = true,
    /**
     * Each body's place in a parents-first order (S05h: the Universe's chunk-order nodes, by their plan
     * index, every planet after its star); null keeps index order, as iOS's (:1782).
     */
    private val rank: IntArray? = null,
) {
    @Volatile var lively: Boolean = lively

    /** Body [i]'s parent here (−1 for none): the one it moves with. */
    fun parentOf(i: Int): Int = parentOf?.getOrElse(i) { -1 } ?: -1

    // a parent at or after its child is no parent here, as iOS's `hasParent` (:1783); with a [rank], after in it
    private val parentOf: IntArray? = parents?.let { p ->
        IntArray(count) { i ->
            val q = p.getOrElse(i) { -1 }
            val r = rank
            val before = if (r != null) q in 0 until count && r[q] < r[i] else q in 0 until i
            if (before) q else -1
        }
    }
    // the bodies in rank order (rank's inverse), for the parents-first sort
    private val byRank: IntArray? = rank?.let { r -> IntArray(count).also { inv -> for (i in 0 until count) inv[r[i]] = i } }
    // each body's children (orbit path) or neighbours with their rest lengths (link path), as CSR
    private val childStart: IntArray
    private val childList: IntArray
    private val linkStart: IntArray
    private val linkOther: IntArray
    private val linkRest: FloatArray

    init {
        val p = parentOf
        if (p != null) {
            childStart = IntArray(count + 1)
            for (i in 0 until count) if (p[i] >= 0) childStart[p[i] + 1]++
            for (i in 0 until count) childStart[i + 1] += childStart[i]
            childList = IntArray(childStart[count])
            val fill = childStart.copyOf()
            for (i in 0 until count) if (p[i] >= 0) childList[fill[p[i]]++] = i
        } else { childStart = IntArray(count + 1); childList = IntArray(0) }
        if (path == MapDragPath.LINKS && links.size >= 2) {
            linkStart = IntArray(count + 1)
            var e = 0
            while (e + 1 < links.size) {
                val a = links[e]; val b = links[e + 1]; e += 2
                if (a !in 0 until count || b !in 0 until count || a == b) continue
                linkStart[a + 1]++; linkStart[b + 1]++
            }
            for (i in 0 until count) linkStart[i + 1] += linkStart[i]
            linkOther = IntArray(linkStart[count]); linkRest = FloatArray(linkStart[count])
            val fill = linkStart.copyOf()
            e = 0
            while (e + 1 < links.size) {
                val a = links[e]; val b = links[e + 1]; e += 2
                if (a !in 0 until count || b !in 0 until count || a == b) continue
                // each link holds the distance between its ends' homes (:742-743)
                val rest = (home(b, 0.0) - home(a, 0.0)).length()
                linkOther[fill[a]] = b; linkRest[fill[a]++] = rest
                linkOther[fill[b]] = a; linkRest[fill[b]++] = rest
            }
        } else { linkStart = IntArray(count + 1); linkOther = IntArray(0); linkRest = FloatArray(0) }
    }

    // offsets, speeds, this piece's moves and forces: three floats a body, made at the first grab
    private var offset = FloatArray(0)
    private var velocity = FloatArray(0)
    private var moved = FloatArray(0)
    private var force = FloatArray(0)
    private var activeFlag = BooleanArray(0)
    // pulled this frame by a neighbour off home by the wake or more (link path): not settled, though still at home
    private var pulled = BooleanArray(0)
    // the bodies stepped, in index order after expand(): activeCount of them
    private var active = IntArray(16)
    private var activeCount = 0

    /** The body held, −1 for none. */
    @Volatile var grabbed: Int = -1; private set
    /** How many times a body has been picked up (the pop's cue). */
    @Volatile var grabs: Int = 0; private set
    private var target: Vec3? = null
    private var dragVelocity = Vec3.ZERO

    /** The orbit clock's rate (1 running, easing to 0 while a body is held) and its time, seconds. */
    @Volatile var orbitRate: Float = 1f; private set
    @Volatile var orbitTime: Double = 0.0; private set

    /** Bodies off home now (MapDrawn.moving), and how many the last step worked on. */
    @Volatile var moving: Int = 0; private set
    @Volatile var worked: Int = 0; private set

    /** Bodies off home other than the one held (MapDrawn.carried). */
    val carried: Int get() { val g = grabbed; val m = moving; return if (g >= 0 && g < activeFlag.size && activeFlag[g]) m - 1 else m }

    /**
     * Frames stepped since the last let-go, counted until every body is home again (0 before any let-go),
     * and the most frames iOS's spring gives that let-go to come home, set at the let-go: twice
     * [framesToRest] from the farthest body off home then, at the held body's fling, for a pull or a carry
     * lagging behind it. A slow device is judged by these, not by the clock (S05g2 repair 2b).
     */
    @Volatile var letGoFrames: Int = 0; private set
    @Volatile var restFrames: Int = 0; private set
    private var counting = false

    /** Whether frames are needed: a body held or one off home. */
    val busy: Boolean get() = grabbed >= 0 || moving > 0

    /** The finger's smoothed speed, world units a second (dragVelocity). */
    val fingerSpeed: Vec3 @Synchronized get() = dragVelocity

    private val stiffness get() = STIFFNESS
    private val damping get() = if (lively) DAMPING_RATIO * 2f * sqrt(STIFFNESS) else 2f * sqrt(STIFFNESS)

    private fun ensure() {
        if (offset.size == 3 * count) return
        offset = FloatArray(3 * count); velocity = FloatArray(3 * count)
        moved = FloatArray(3 * count); force = FloatArray(3 * count)
        activeFlag = BooleanArray(count)
        pulled = BooleanArray(count)
    }

    /** The bodies off home or held now, in index order. */
    @Synchronized fun movingBodies(): IntArray = active.copyOf(activeCount).also { it.sort() }

    /** Body [i]'s own offset from its home along [axis] (0 x, 1 y, 2 z); 0 for a body at home. */
    fun offset(i: Int, axis: Int): Float {
        val o = offset
        val k = 3 * i + axis
        return if (k in o.indices) o[k] else 0f
    }

    /** Body [i]'s speed, world units a second; zero for a body at home. */
    fun velocityOf(i: Int): Vec3 {
        val v = velocity
        val k = 3 * i
        return if (k + 2 < v.size) Vec3(v[k], v[k + 1], v[k + 2]) else Vec3.ZERO
    }

    /** Body [i]'s own offset from its home. */
    fun offsetOf(i: Int): Vec3 = Vec3(offset(i, 0), offset(i, 1), offset(i, 2))

    /** Where body [i] is drawn, its home being [home]: home plus its own offset, and nothing more. */
    fun placed(i: Int, home: Vec3): Vec3 = Vec3(home.x + offset(i, 0), home.y + offset(i, 1), home.z + offset(i, 2))

    /** Body [i]'s home now, at the orbit clock's time. */
    fun homeOf(i: Int): Vec3 = home(i, orbitTime)

    /** Picks body [i] up where it is (grab, :1246-1259): no speed, its target its place. */
    @Synchronized fun grab(i: Int) {
        if (i !in 0 until count) return
        ensure()
        grabbed = i
        grabs++
        target = placed(i, home(i, orbitTime))
        dragVelocity = Vec3.ZERO
        velocity[3 * i] = 0f; velocity[3 * i + 1] = 0f; velocity[3 * i + 2] = 0f
        activate(i)
    }

    /** Where the finger wants the held body, in the world (drag(to:), :1263-1267); the next step moves it. */
    @Synchronized fun drag(to: Vec3) {
        if (grabbed >= 0) target = to
    }

    /**
     * Lets go (release, :1269-1283): the body keeps the finger's smoothed speed, capped at
     * [FLING_LIMIT_RADII] note radii a second, none with animations off or when [still] (held still
     * until the options opened), and springs home.
     */
    @Synchronized fun release(still: Boolean = false) {
        val g = grabbed
        if (g < 0) return
        var fling = dragVelocity
        val speed = fling.length()
        val limit = FLING_LIMIT_RADII * unit
        if (speed > limit) fling = fling * (limit / speed)
        if (!lively || still) fling = Vec3.ZERO
        velocity[3 * g] = fling.x; velocity[3 * g + 1] = fling.y; velocity[3 * g + 2] = fling.z
        grabbed = -1
        target = null
        var farthest = 0f
        for (a in 0 until activeCount) farthest = max(farthest, lengthAt(offset, active[a]))
        restFrames = 2 * framesToRest(farthest / unit, lively, fling.length() / unit)
        letGoFrames = 0
        counting = true
    }

    /**
     * One frame [frameDt] seconds on (:1617-1667). The orbit clock moves by the frame itself; the
     * springs by it clamped to [MIN_DT]-[MAX_DT], split into pieces of at most [MAX_SUBSTEP].
     */
    @Synchronized fun step(frameDt: Float) {
        advanceClock(frameDt)
        val g = grabbed
        if (g >= 0) counting = false
        if (g < 0 && activeCount == 0) { worked = 0; counting = false; return }
        if (counting) letGoFrames++
        val dt = frameDt.coerceIn(MIN_DT, MAX_DT)
        var goal = Vec3.ZERO
        if (g >= 0) {
            val t = target ?: placed(g, home(g, orbitTime))
            goal = t - home(g, orbitTime)
            // the finger's speed, smoothed over about the last 50 ms
            val instant = (goal - offsetOf(g)) * (1f / dt)
            val blend = 1f - exp(-dt / SMOOTH)
            dragVelocity = dragVelocity + (instant - dragVelocity) * blend
            if (path == MapDragPath.LINKS) setOffset(g, goal)
            velocity[3 * g] = dragVelocity.x; velocity[3 * g + 1] = dragVelocity.y; velocity[3 * g + 2] = dragVelocity.z
        }
        expand()
        val pieces = max(1, ceil(dt / MAX_SUBSTEP - 1e-4f).toInt())
        val h = dt / pieces
        for (piece in 0 until pieces) {
            if (path == MapDragPath.ORBITS) integrateOrbits(h, piece == 0, g, goal) else integrate(h, g)
        }
        worked = activeCount
        settle(g)
    }

    /** How many pieces a frame of [frameDt] seconds is split into. */
    fun piecesFor(frameDt: Float): Int = max(1, ceil(frameDt.coerceIn(MIN_DT, MAX_DT) / MAX_SUBSTEP - 1e-4f).toInt())

    /** A body under a finger that has not moved yet (iOS pressedNote), −1 for none (S05h): the orbit clock eases to a stop. */
    @Volatile var pressed: Int = -1
    /** A body under a pointer with no button down (iOS hovered), −1 for none (S05h): the same. */
    @Volatile var hovered: Int = -1

    // the orbit clock eases to a stop within about 0.3 s while a body is pressed, hovered or held,
    // so it never slides from under the finger, and back over about 1.5 s after (advanceOrbits,
    // :1750-1765); still, the plan's own picture, time 0
    private fun advanceClock(dt: Float) {
        if (!lively) { orbitTime = 0.0; return }
        val busy = grabbed >= 0 || pressed >= 0 || hovered >= 0
        val tau = if (busy) TAU_BUSY else TAU_FREE
        val goal = if (busy) 0f else 1f
        orbitRate += (goal - orbitRate) * (1f - exp(-dt / tau))
        orbitTime += (dt * orbitRate).toDouble()
    }

    private fun activate(i: Int) {
        if (activeFlag[i]) return
        activeFlag[i] = true
        if (activeCount == active.size) active = active.copyOf(activeCount * 2)
        active[activeCount++] = i
    }

    private fun setOffset(i: Int, v: Vec3) { offset[3 * i] = v.x; offset[3 * i + 1] = v.y; offset[3 * i + 2] = v.z }

    // who is stepped this frame: the bodies off home, with (orbit path) everything inside them, or
    // (link path) the neighbours of a body off home by at least [WAKE_OFFSET]; then in index order,
    // parents first
    private fun expand() {
        if (path == MapDragPath.ORBITS) {
            var k = 0
            while (k < activeCount) {
                val i = active[k++]
                for (c in childStart[i] until childStart[i + 1]) activate(childList[c])
            }
        } else {
            val n = activeCount
            val near = WAKE_OFFSET * unit
            for (k in 0 until n) {
                val i = active[k]
                if (lengthAt(offset, i) < near) continue
                for (e in linkStart[i] until linkStart[i + 1]) { val j = linkOther[e]; activate(j); pulled[j] = true }
            }
        }
        val r = rank; val inv = byRank
        if (r != null && inv != null) {
            for (a in 0 until activeCount) active[a] = r[active[a]]
            active.sort(0, activeCount)
            for (a in 0 until activeCount) active[a] = inv[active[a]]
        } else active.sort(0, activeCount)
    }

    // one semi-implicit Euler piece on the link path (integrate, :1703-1741): forces first, the held body skipped
    private fun integrate(h: Float, g: Int) {
        val k = stiffness; val c = damping
        for (a in 0 until activeCount) {
            val i = active[a]
            val o = 3 * i
            var fx = -offset[o] * k - velocity[o] * c
            var fy = -offset[o + 1] * k - velocity[o + 1] * c
            var fz = -offset[o + 2] * k - velocity[o + 2] * c
            if (linkStart[i] != linkStart[i + 1]) {
                val hi = home(i, 0.0)
                val ix = hi.x + offset[o]; val iy = hi.y + offset[o + 1]; val iz = hi.z + offset[o + 2]
                for (e in linkStart[i] until linkStart[i + 1]) {
                    val j = linkOther[e]
                    val hj = home(j, 0.0)
                    val dx = hj.x + offset(j, 0) - ix; val dy = hj.y + offset(j, 1) - iy; val dz = hj.z + offset(j, 2) - iz
                    val length = sqrt(dx * dx + dy * dy + dz * dz)
                    if (length <= 1e-4f) continue
                    val scale = LINK_STIFFNESS * (length - linkRest[e]) / length
                    fx += dx * scale; fy += dy * scale; fz += dz * scale
                }
            }
            force[o] = fx; force[o + 1] = fy; force[o + 2] = fz
        }
        for (a in 0 until activeCount) {
            val i = active[a]
            if (i == g) continue
            val o = 3 * i
            for (x in 0 until 3) {
                velocity[o + x] += force[o + x] * h
                offset[o + x] += velocity[o + x] * h
            }
        }
    }

    // one piece on the orbit path (integrateOrbits, :1772-1798), in offsets from home: carried by what
    // the parent moved, then sprung to the parent's offset (its live place, less this body's home)
    private fun integrateOrbits(h: Float, first: Boolean, g: Int, goal: Vec3) {
        val keep = if (lively) CARRY else 1f
        val k = stiffness; val c = damping
        val parents = parentOf
        for (a in 0 until activeCount) {
            val i = active[a]
            val o = 3 * i
            if (i == g) {
                if (first) {
                    moved[o] = goal.x - offset[o]; moved[o + 1] = goal.y - offset[o + 1]; moved[o + 2] = goal.z - offset[o + 2]
                    setOffset(i, goal)
                } else { moved[o] = 0f; moved[o + 1] = 0f; moved[o + 2] = 0f }
                continue
            }
            val p = parents?.get(i) ?: -1
            val hasParent = p >= 0
            for (x in 0 until 3) {
                val carry = if (hasParent) moved[3 * p + x] * keep else 0f
                offset[o + x] += carry
                val base = if (hasParent) offset[3 * p + x] else 0f
                velocity[o + x] += ((base - offset[o + x]) * k - velocity[o + x] * c) * h
                val travel = velocity[o + x] * h
                offset[o + x] += travel
                moved[o + x] = carry + travel
            }
        }
    }

    // a body back home and still, not held and not pulled by a neighbour well off home, is dropped: it
    // costs nothing until moved again
    private fun settle(g: Int) {
        val near = REST_OFFSET * unit
        val slow = REST_SPEED * unit
        var w = 0
        for (a in 0 until activeCount) {
            val i = active[a]
            val kept = pulled[i]
            pulled[i] = false
            if (i != g && !kept && lengthAt(offset, i) < near && lengthAt(velocity, i) < slow) {
                activeFlag[i] = false
                for (x in 0 until 3) { offset[3 * i + x] = 0f; velocity[3 * i + x] = 0f; moved[3 * i + x] = 0f }
            } else active[w++] = i
        }
        activeCount = w
        moving = w
    }

    private fun lengthAt(a: FloatArray, i: Int): Float {
        val o = 3 * i
        return sqrt(a[o] * a[o] + a[o + 1] * a[o + 1] + a[o + 2] * a[o + 2])
    }

    companion object {
        /**
         * The frames iOS's spring takes to bring one body home from [offsetRadii] note radii off it, moving
         * away at [speedRadii] radii a second: its step, 0.05 s a frame at most (:1617) in pieces of 1/120 s,
         * to the rest thresholds here; animations off ([lively] false) critically damped. Not one frame more
         * than a frame rate of 20 or more needs; a slower device takes more seconds, as iOS's would.
         */
        fun framesToRest(offsetRadii: Float, lively: Boolean, speedRadii: Float = 0f): Int {
            var x = offsetRadii.toDouble(); var v = speedRadii.toDouble()
            val c = (if (lively) DAMPING_RATIO * 2.0 else 2.0) * sqrt(STIFFNESS.toDouble())
            val pieces = max(1, ceil(MAX_DT / MAX_SUBSTEP - 1e-4f).toInt())
            val h = MAX_DT.toDouble() / pieces
            var frames = 0
            while (!(abs(x) < REST_OFFSET && abs(v) < REST_SPEED) && frames < 100_000) {
                repeat(pieces) { v += (-x * STIFFNESS - v * c) * h; x += v * h }
                frames++
            }
            return frames
        }

        /** The spring home (:532) and each link's (:535). */
        const val STIFFNESS = 6f
        const val LINK_STIFFNESS = 4f
        /** Damping, as a share of critical, while animations run (:578-580). */
        const val DAMPING_RATIO = 0.6f
        /** The longest piece a frame is split into (:545), and the frame's own step, clamped (:1617). */
        const val MAX_SUBSTEP = 1f / 120f
        const val MIN_DT = 0.001f
        const val MAX_DT = 0.05f
        /** The finger's speed is smoothed over this long (:1624). */
        const val SMOOTH = 0.05f
        /**
         * iOS's fling cap of 8 (flingLimit, :547) in note radii: an idea's radius there is 0.62 of the
         * page radius (Graph3DView.swift:1242), 0.3 without links (GraphFraming.swift:32-43), so 8
         * scene units are 8 / (0.62 × 0.3) ≈ 43 note radii (S05g2-plan Q1).
         */
        const val FLING_LIMIT_RADII = 8f / (0.62f * 0.3f)
        /** What a child is carried of its parent's move while animations run (:1773). */
        const val CARRY = 0.85f
        /** The orbit clock's ease to a stop while a body is held, and back (:1755-1756). */
        const val TAU_BUSY = 0.1f
        const val TAU_FREE = 0.5f
        /**
         * How far off home, in note radii, a body pulls its neighbours into the run (Android's own: iOS
         * steps every body, :1703-1741). A link of 4 against a home spring of 6 hands a neighbour at
         * most 4 / (6 + 4) = 0.4 of a move, so a body under 0.05 radii off home would move the next by
         * under 0.02 radii, a fifth of a pixel on a 20 px body: nothing iOS would show. A wake at the rest
         * threshold (1e-3) instead woke twice as many: six links each way along a chain from a
         * half-radius drag, not three (MapDragTest), and more the farther the drag.
         */
        const val WAKE_OFFSET = 0.05f
        /** Home and still, in note radii and note radii a second (Android's own). */
        const val REST_OFFSET = 1e-3f
        const val REST_SPEED = 1e-2f
    }
}
