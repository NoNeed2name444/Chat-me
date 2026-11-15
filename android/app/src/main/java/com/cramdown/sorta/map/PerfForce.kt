package com.cramdown.sorta.map

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// The Performance map's incremental layout (S05i2), a whole port of iOS's GraphPerfForce
// (ios/Sorta/Maps/GraphPerfForce.swift): a short-range force refinement of the seed done a slice of
// notes at a time, so no frame waits on it. Links inside a folder pull a note towards their averaged
// rest point, links out of it lean it a little; notes closer than the cutoff push apart, found through
// a hashed grid rebuilt when each sweep begins; a weak pull to the system's centre; the note capped,
// then clamped into its system's ball and clear of its hub. Hubs never move. Notes go in a Morton
// order through the seed, Gauss-Seidel, and a step never runs past a sweep's end, so the result does
// not depend on how the sweeps were sliced. Pure Kotlin: GlMapRenderer steps it on the GL thread.

class PerfForce(graph: PerfGraph, seed: PerfSeed, val tuning: Tuning = Tuning()) {
    /** GraphPerfForce.Tuning, iOS's numbers. */
    data class Tuning(
        val spring: Float = 0.3f,
        val springCap: Float = 0.08f,
        val lean: Float = 0.03f,
        val cutoff: Float = 0.9f,
        val repel: Float = 1f,
        val centre: Float = 0.002f,
        val maxStep: Float = 0.3f,
        val cooling: Float = 0.85f,
        val sweeps: Int = 16,
        val still: Float = 0.004f,
    )

    val noteCount: Int = graph.noteCount
    val nodeCount: Int = graph.nodeCount
    val spacing: Float = seed.spacing
    /** x, y, z, radius per body: notes (graph slots), then one hub per folder. */
    val xyzr: FloatArray = FloatArray(4 * graph.nodeCount).also { p ->
        for (i in 0 until graph.nodeCount) {
            p[4 * i] = seed.positions[3 * i]; p[4 * i + 1] = seed.positions[3 * i + 1]
            p[4 * i + 2] = seed.positions[3 * i + 2]; p[4 * i + 3] = seed.radius[i]
        }
    }
    /** The next place in the visiting order a step takes, and the sweep it is in. */
    var cursor = 0; private set
    var sweep = 0; private set
    /** The largest move in the sweep so far, and in the last whole sweep (world units). */
    var sweepLargest = 0f; private set
    var lastSweepLargest = Float.POSITIVE_INFINITY; private set
    /** Bumped by every step that moved something (the renderer's upload key). */
    var version = 0; private set
    private var settledEarly = false

    private val adjStart = graph.adjStart
    private val adjacency = graph.adjacency
    private val system = graph.systemOfNote
    /** Per note: its links' rest length, in spacings (more round a well-linked note: 4πr² ≥ 1.1 D). */
    private val reach = FloatArray(graph.noteCount) { max(1f, 0.3f * sqrt(graph.degree(it).toFloat())) }
    /** Per system: centre x, y, z, and how near and far a note's centre may be from it. */
    private val ball = FloatArray(5 * graph.systemCount).also { b ->
        for (s in 0 until graph.systemCount) {
            b[5 * s] = seed.systemCentre[3 * s]; b[5 * s + 1] = seed.systemCentre[3 * s + 1]; b[5 * s + 2] = seed.systemCentre[3 * s + 2]
            b[5 * s + 3] = if (s < graph.folderCount) seed.radius[graph.noteCount + s] + 0.01f else 0f
            b[5 * s + 4] = seed.coreRadius[s] - 0.001f
        }
    }
    private val cell = max(2 * tuning.cutoff * seed.spacing, 1e-3f)
    /** The notes in visiting order: along a Morton curve through the seed. */
    val order: IntArray = mortonOrder(seed.positions, graph.noteCount, cell)
    private val tableBits: Int = run { var bits = 1; while ((1 shl bits) < 2 * max(graph.noteCount, 1)) bits++; bits }
    private val bucketStart = IntArray((1 shl tableBits) + 1)
    private val entryNote = IntArray(graph.noteCount)
    private val entryKey = LongArray(graph.noteCount)
    private val entryXYZ = FloatArray(3 * graph.noteCount)
    private val noteKey = LongArray(graph.noteCount)

    val isSettled: Boolean get() = noteCount == 0 || sweep >= tuning.sweeps || settledEarly

    /** The step cap this sweep, in world units. */
    val stepCap: Float get() = tuning.maxStep * spacing * tuning.cooling.pow(sweep.toFloat())

    /** Body [i]'s position: x, y, z. */
    fun position(i: Int): FloatArray = floatArrayOf(xyzr[4 * i], xyzr[4 * i + 1], xyzr[4 * i + 2])

    /** Visits up to [budget] notes from the cursor, stopping at the sweep's end; how many it visited (0 once settled). */
    fun step(budget: Int): Int {
        if (isSettled || budget <= 0) return 0
        if (cursor == 0) rebuildGrid()
        val from = cursor
        val to = min(noteCount, cursor + budget)
        val largest = relax(from, to)
        sweepLargest = max(sweepLargest, largest)
        cursor = to
        version++
        if (cursor == noteCount) {
            cursor = 0
            sweep++
            lastSweepLargest = sweepLargest
            if (sweepLargest < tuning.still * spacing) settledEarly = true
            sweepLargest = 0f
        }
        return to - from
    }

    /** Runs whole sweeps until settled (tests, and a map shown still). */
    fun settle() { while (!isSettled) step(max(noteCount, 1)) }

    private fun rebuildGrid() {
        val n = noteCount; val bits = tableBits; val size = 1 shl bits; val c = cell
        val p = xyzr; val s = bucketStart
        s.fill(0)
        for (i in 0 until n) {
            val k = key(coordinate(p[4 * i], c), coordinate(p[4 * i + 1], c), coordinate(p[4 * i + 2], c))
            noteKey[i] = k
            s[bucket(k, bits) + 1]++
        }
        for (b in 0 until size) s[b + 1] += s[b]
        for (i in 0 until n) {
            val b = bucket(noteKey[i], bits)
            val q = s[b]
            entryNote[q] = i; entryKey[q] = noteKey[i]
            entryXYZ[3 * q] = p[4 * i]; entryXYZ[3 * q + 1] = p[4 * i + 1]; entryXYZ[3 * q + 2] = p[4 * i + 2]
            s[b]++
        }
        var b = size
        while (b > 0) { s[b] = s[b - 1]; b-- }
        s[0] = 0
    }

    /** Moves the notes at places from until to of the visiting order; returns the largest move. */
    private fun relax(from: Int, to: Int): Float {
        val t = tuning
        val unit = spacing
        val cut = t.cutoff * spacing
        val cut2 = cut * cut
        val size = cell
        val cap = stepCap
        val lean = t.lean * spacing
        val springCap = t.springCap * spacing
        val bits = tableBits
        val p = xyzr; val r0 = reach; val sy = system; val bb = ball
        var largest = 0f
        for (place in from until to) {
            val i = order[place]
            val px = p[4 * i]; val py = p[4 * i + 1]; val pz = p[4 * i + 2]; val pr = p[4 * i + 3]
            val s = sy[i]
            var dx = 0f; var dy = 0f; var dz = 0f

            // links inside the folder: towards the mean of their rest points; out of it: a lean
            var tx = 0f; var ty = 0f; var tz = 0f; var tw = 0f
            var lx = 0f; var ly = 0f; var lz = 0f; var ln = 0f
            val mine = r0[i]
            for (e in adjStart[i] until adjStart[i + 1]) {
                val j = adjacency[e]
                val vx = p[4 * j] - px; val vy = p[4 * j + 1] - py; val vz = p[4 * j + 2] - pz
                val len = sqrt(vx * vx + vy * vy + vz * vz)
                if (len <= 1e-6f) continue
                if (sy[j] == s) {
                    val k = unit * max(mine, r0[j]) / len
                    tx += p[4 * j] - vx * k; ty += p[4 * j + 1] - vy * k; tz += p[4 * j + 2] - vz * k
                    tw += 1
                } else {
                    lx += vx / len; ly += vy / len; lz += vz / len
                    ln += 1
                }
            }
            if (tw > 0) {
                var ax = (tx / tw - px) * t.spring; var ay = (ty / tw - py) * t.spring; var az = (tz / tw - pz) * t.spring
                val am = sqrt(ax * ax + ay * ay + az * az)
                if (am > springCap) { val k = springCap / am; ax *= k; ay *= k; az *= k }
                dx += ax; dy += ay; dz += az
            }
            if (ln > 0) {
                val m = sqrt(lx * lx + ly * ly + lz * lz)
                if (m > 1e-6f) {
                    val k = lean * min(ln, 3f) / 3 / m
                    dx += lx * k; dy += ly * k; dz += lz * k
                }
            }

            // close neighbours: apart, from the 2 × 2 × 2 cells nearest
            val fx = px / size; val fy = py / size; val fz = pz / size
            val cx = floor(fx).toLong(); val cy = floor(fy).toLong(); val cz = floor(fz).toLong()
            val sx = if (fx - cx.toFloat() < 0.5f) -1L else 1L
            val sY = if (fy - cy.toFloat() < 0.5f) -1L else 1L
            val sz = if (fz - cz.toFloat() < 0.5f) -1L else 1L
            var rx = 0f; var ry = 0f; var rz = 0f
            for (corner in 0 until 8) {
                val key = key(cx + (if (corner and 1 == 0) 0L else sx), cy + (if (corner and 2 == 0) 0L else sY),
                    cz + (if (corner and 4 == 0) 0L else sz))
                val b = bucket(key, bits)
                var q = bucketStart[b]
                val qEnd = bucketStart[b + 1]
                while (q < qEnd) {
                    val k = entryNote[q]
                    val same = entryKey[q] == key
                    val ex = entryXYZ[3 * q]; val ey = entryXYZ[3 * q + 1]; val ez = entryXYZ[3 * q + 2]
                    q++
                    if (k == i || !same) continue
                    val vx = px - ex; val vy = py - ey; val vz = pz - ez
                    val d2 = vx * vx + vy * vy + vz * vz
                    if (d2 >= cut2) continue
                    if (d2 < 1e-12f) {
                        // on top of each other: the lower slot steps aside
                        if (i < k) rx += cut * 0.5f else rx -= cut * 0.5f
                        continue
                    }
                    val d = sqrt(d2)
                    val push = (cut - d) / d * 0.5f
                    rx += vx * push; ry += vy * push; rz += vz * push
                }
            }
            dx += rx * t.repel; dy += ry * t.repel; dz += rz * t.repel

            // the system's centre
            val bx = bb[5 * s]; val by = bb[5 * s + 1]; val bz = bb[5 * s + 2]
            dx += (bx - px) * t.centre; dy += (by - py) * t.centre; dz += (bz - pz) * t.centre

            // capped, then clamped into the ball and clear of the hub
            val m = sqrt(dx * dx + dy * dy + dz * dz)
            if (m > cap) { val k = cap / m; dx *= k; dy *= k; dz *= k }
            var nx = px + dx; var ny = py + dy; var nz = pz + dz
            val gx = nx - bx; val gy = ny - by; val gz = nz - bz
            val r = sqrt(gx * gx + gy * gy + gz * gz)
            val inner = if (bb[5 * s + 3] > 0) bb[5 * s + 3] + pr else 0f
            val outer = max(bb[5 * s + 4] - pr, inner)
            if (r > outer && r > 1e-6f) {
                val k = outer / r
                nx = bx + gx * k; ny = by + gy * k; nz = bz + gz * k
            } else if (r < inner) {
                if (r > 1e-6f) {
                    val k = inner / r
                    nx = bx + gx * k; ny = by + gy * k; nz = bz + gz * k
                } else { nx = bx + inner; ny = by; nz = bz }
            }
            val mx = nx - px; val my = ny - py; val mz = nz - pz
            largest = max(largest, sqrt(mx * mx + my * my + mz * mz))
            p[4 * i] = nx; p[4 * i + 1] = ny; p[4 * i + 2] = nz
        }
        return largest
    }

    companion object {
        /** The first frame's slice, in notes (GraphPerfRenderer.swift:67). */
        const val FIRST_BUDGET = 4_000
        /** A frame's share for the slice, seconds: about three milliseconds (GraphPerfRenderer.swift:13-14). */
        const val SLICE_SECONDS = 0.003

        /**
         * The next frame's slice after [budget] notes took [spentSeconds] (GraphPerfRenderer.swift:232-237):
         * halfway to what fits three milliseconds at that pace, kept within 500 and 60,000 notes.
         */
        fun nextBudget(budget: Int, spentSeconds: Double): Int {
            val spent = max(spentSeconds, 0.000_05)
            val scaled = budget * SLICE_SECONDS / spent
            return (scaled * 0.5 + budget * 0.5).toInt().coerceIn(500, 60_000)
        }

        private fun coordinate(v: Float, cell: Float): Long = floor(v / cell).toLong()

        /** Three 21-bit cell coordinates (offset to be positive) in one key. */
        private fun key(cx: Long, cy: Long, cz: Long): Long {
            val o = 1L shl 20
            return (((cx + o) and 0x1F_FFFF) shl 42) or (((cy + o) and 0x1F_FFFF) shl 21) or ((cz + o) and 0x1F_FFFF)
        }

        private fun bucket(key: Long, bits: Int): Int = ((key * GOLDEN) ushr (64 - bits)).toInt()

        private val GOLDEN = 0x9E37_79B9_7F4A_7C15uL.toLong()

        /** Spreads the low 21 bits of [v] to every third bit. */
        fun spread(v: Long): Long {
            var x = v and 0x1F_FFFF
            x = (x or (x shl 32)) and 0x1F_0000_0000_FFFFL
            x = (x or (x shl 16)) and 0x1F_0000_FF00_00FFL
            x = (x or (x shl 8)) and 0x100F_00F0_0F00_F00FL
            x = (x or (x shl 4)) and 0x10C3_0C30_C30C_30C3L
            x = (x or (x shl 2)) and 0x1249_2492_4924_9249L
            return x
        }

        /** Note slots sorted along a Morton curve through their cells (code, then slot). */
        fun mortonOrder(positions: FloatArray, count: Int, cell: Float): IntArray {
            if (count <= 0) return IntArray(0)
            var lx = positions[0]; var ly = positions[1]; var lz = positions[2]
            for (i in 0 until count) { lx = min(lx, positions[3 * i]); ly = min(ly, positions[3 * i + 1]); lz = min(lz, positions[3 * i + 2]) }
            val keys = LongArray(count)
            for (i in 0 until count) {
                val cx = max((positions[3 * i] - lx) / cell, 0f).toLong()
                val cy = max((positions[3 * i + 1] - ly) / cell, 0f).toLong()
                val cz = max((positions[3 * i + 2] - lz) / cell, 0f).toLong()
                keys[i] = spread(cx) or (spread(cy) shl 1) or (spread(cz) shl 2)
            }
            return (0 until count).sortedWith { a, b ->
                val c = java.lang.Long.compareUnsigned(keys[a], keys[b]); if (c != 0) c else a.compareTo(b)
            }.toIntArray()
        }
    }
}
