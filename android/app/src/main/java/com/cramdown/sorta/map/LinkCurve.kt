package com.cramdown.sorta.map

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// A port of ios/Sorta/Maps/GraphLinkCurve.swift (S05e), line for line.
//
// The path a link takes, worked out as a continuous function of s (0 at its sending end, 1 at
// its receiving end) so it can be sampled as finely as the tier allows (40 samples at HIGH, 20
// at SMOOTH) and always reads as one unbroken curve: a straight run between the two trims,
// bowed by a smooth parabola when it arches, and bent by a smoothstep near a black hole (pulled
// into its disk's plane and swung round its axis) or a gas giant (pulled towards its ring's
// plane). Every piece has a continuous tangent, so no sample count shows a kink.
//
// The points are written into float arrays, three floats a point, so a frame's links make no
// garbage. Pure Kotlin: LinkCurveTest carries over iOS's GraphicsTests G4 to G6.

/** How a link is pulled in near one end: towards the plane square to [axis] through [centre] by [pull] (0 to 1), and swung round the axis by up to [swing] radians. */
class LinkBend(val centre: Vec3, val axis: Vec3, val pull: Float, val swing: Float) {
    companion object {
        /** A black hole's (style code 0) or a gas giant's (code 2); null for any other style, which leaves the link straight. */
        fun forStyle(code: Int, centre: Vec3, axis: Vec3): LinkBend? {
            val n = LinkCurve.unit(axis, Vec3(0f, 1f, 0f))
            if (code == 0) return LinkBend(centre, n, 0.7f, 0.55f)
            if (code == 2) return LinkBend(centre, n, 0.35f, 0f)
            return null
        }
    }
}

/** Everything that fixes one link's path this frame. */
class LinkPath(
    /** Where it leaves the sending note and reaches the receiving one (already trimmed). */
    var start: Vec3,
    var end: Vec3,
    /** Which way it bows, unit length, and how far at the middle. */
    var arch: Vec3 = Vec3.ZERO,
    var archSize: Float = 0f,
    /** How far it has grown from its sending end, 0 to 1. */
    var grow: Float = 1f,
    /** Bends near the sending (A) and receiving (B) ends. */
    var bendA: LinkBend? = null,
    var bendB: LinkBend? = null,
)

object LinkCurve {
    /** The longest distance along a link the shader is given (as iOS's, where the texture coordinate packs more above it). */
    const val ALONG_CAP = 60f

    /** The point [t] of the way along the grown part of the link (0 to 1), into [out] at [at]. */
    fun point(path: LinkPath, t: Float, out: FloatArray, at: Int = 0) {
        val s = t * path.grow
        val a = path.start; val b = path.end
        var x = a.x + (b.x - a.x) * s
        var y = a.y + (b.y - a.y) * s
        var z = a.z + (b.z - a.z) * s
        if (path.archSize > 0f) {
            val hump = 4f * s * (1f - s) * path.archSize
            x += path.arch.x * hump; y += path.arch.y * hump; z += path.arch.z * hump
        }
        out[at] = x; out[at + 1] = y; out[at + 2] = z
        path.bendB?.let { bend(out, at, s, it) }
        path.bendA?.let { bend(out, at, 1f - s, it) }
    }

    /** The point [t] of the way along, as a vector (for tests and one-off use). */
    fun point(path: LinkPath, t: Float): Vec3 {
        val p = FloatArray(3)
        point(path, t, p)
        return Vec3(p[0], p[1], p[2])
    }

    /**
     * `count + 1` points evenly spread in t from 0 to 1, into [points] (three floats each) with the
     * distance along each into [lengths]; returns the whole length. Both must hold `count + 1` entries.
     */
    fun sample(path: LinkPath, count: Int, points: FloatArray, lengths: FloatArray): Float {
        val n = max(count, 1)
        require(points.size >= (n + 1) * 3 && lengths.size >= n + 1) { "room for ${n + 1} points" }
        val step = 1f / n
        for (k in 0..n) point(path, k * step, points, k * 3)
        var total = 0f
        lengths[0] = 0f
        for (k in 1..n) {
            val o = k * 3
            val dx = points[o] - points[o - 3]; val dy = points[o + 1] - points[o - 2]; val dz = points[o + 2] - points[o - 1]
            total += sqrt(dx * dx + dy * dy + dz * dz)
            lengths[k] = total
        }
        return total
    }

    /**
     * How much to shrink distances along a link so its whole length fits under [ALONG_CAP]: 1 for any
     * shorter link. The shader's flow then runs evenly all the way along a long link.
     */
    fun alongScale(total: Float): Float = if (total > ALONG_CAP) ALONG_CAP / total else 1f

    /** The direction along the curve at sample [k] of [n], from its neighbours (one-sided at the ends), into [out] at [at]. */
    fun tangent(points: FloatArray, k: Int, n: Int, fallback: Vec3, out: FloatArray, at: Int = 0) {
        val before = max(k - 1, 0) * 3
        val after = min(k + 1, n) * 3
        val dx = points[after] - points[before]; val dy = points[after + 1] - points[before + 1]; val dz = points[after + 2] - points[before + 2]
        val size = sqrt(dx * dx + dy * dy + dz * dz)
        if (size > 0.00001f) {
            out[at] = dx / size; out[at + 1] = dy / size; out[at + 2] = dz / size
        } else {
            out[at] = fallback.x; out[at + 1] = fallback.y; out[at + 2] = fallback.z
        }
    }

    /**
     * Bends the point at [at] in [p], [s] of the way from the far end towards an end (1 at that end):
     * from s 0.45 on, eased by a smoothstep, pulled towards the plane and swung round the axis. The
     * point keeps its distance from the centre, so the link still ends at the trim.
     */
    fun bend(p: FloatArray, at: Int, s: Float, b: LinkBend) {
        val t = ((s - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val w = t * t * (3f - 2f * t)
        if (w <= 0.0001f) return
        var rx = p[at] - b.centre.x; var ry = p[at + 1] - b.centre.y; var rz = p[at + 2] - b.centre.z
        val dist = sqrt(rx * rx + ry * ry + rz * rz)
        if (dist <= 0.0001f) return
        val n = b.axis
        val lift = (rx * n.x + ry * n.y + rz * n.z) * b.pull * w
        rx -= n.x * lift; ry -= n.y * lift; rz -= n.z * lift
        if (b.swing > 0f) {
            // Rodrigues, as rotate(_:about:by:)
            val angle = b.swing * w * w
            val c = cos(angle); val sn = sin(angle)
            val along = (n.x * rx + n.y * ry + n.z * rz) * (1f - c)
            val sx = n.y * rz - n.z * ry; val sy = n.z * rx - n.x * rz; val sz = n.x * ry - n.y * rx
            val nx = rx * c + sx * sn + n.x * along
            val ny = ry * c + sy * sn + n.y * along
            val nz = rz * c + sz * sn + n.z * along
            rx = nx; ry = ny; rz = nz
        }
        val now = sqrt(rx * rx + ry * ry + rz * rz)
        if (now <= 0.0001f) return
        val k = dist / now
        p[at] = b.centre.x + rx * k; p[at + 1] = b.centre.y + ry * k; p[at + 2] = b.centre.z + rz * k
    }

    // vector helpers, as iOS's

    fun dot(a: Vec3, b: Vec3): Float = a.x * b.x + a.y * b.y + a.z * b.z

    fun cross(a: Vec3, b: Vec3): Vec3 = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

    fun length(v: Vec3): Float = sqrt(dot(v, v))

    fun unit(v: Vec3, fallback: Vec3): Vec3 {
        val size = length(v)
        return if (size > 0.00001f) v * (1f / size) else fallback
    }

    /** Any unit direction square to [dir]. */
    fun perpendicular(dir: Vec3): Vec3 {
        val helper = if (kotlin.math.abs(dir.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
        return unit(cross(dir, helper), Vec3(0f, 0f, 1f))
    }

    /** [v] turned by [angle] radians about the unit [axis] (Rodrigues). */
    fun rotate(v: Vec3, axis: Vec3, angle: Float): Vec3 {
        val c = cos(angle); val s = sin(angle)
        val along = axis * (dot(axis, v) * (1f - c))
        val side = cross(axis, v) * s
        return v * c + side + along
    }
}
