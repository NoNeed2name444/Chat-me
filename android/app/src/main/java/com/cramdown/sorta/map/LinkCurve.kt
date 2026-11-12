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
class LinkBend(centre: Vec3, axis: Vec3, var pull: Float, var swing: Float) {
    // kept as floats, so a frame's links can refill one bend (set) without making garbage
    var cx = centre.x; var cy = centre.y; var cz = centre.z
    var ax = axis.x; var ay = axis.y; var az = axis.z
    val centre: Vec3 get() = Vec3(cx, cy, cz)
    val axis: Vec3 get() = Vec3(ax, ay, az)

    /**
     * Refills this bend as [forStyle] would make it from the centre at [src] [at] and the axis at
     * [at] + 3 (three floats each); returns it, or null for a style that does not bend.
     */
    fun set(code: Int, src: FloatArray, at: Int): LinkBend? {
        if (code != 0 && code != 2) return null
        cx = src[at]; cy = src[at + 1]; cz = src[at + 2]
        val x = src[at + 3]; val y = src[at + 4]; val z = src[at + 5]
        val size = kotlin.math.sqrt(x * x + y * y + z * z)
        if (size > 0.00001f) { ax = x / size; ay = y / size; az = z / size } else { ax = 0f; ay = 1f; az = 0f }
        if (code == 0) { pull = 0.7f; swing = 0.55f } else { pull = 0.35f; swing = 0f }
        return this
    }

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
    start: Vec3,
    end: Vec3,
    /** Which way it bows, unit length, and how far at the middle. */
    var arch: Vec3 = Vec3.ZERO,
    var archSize: Float = 0f,
    /** How far it has grown from its sending end, 0 to 1. */
    var grow: Float = 1f,
    /** Bends near the sending (A) and receiving (B) ends. */
    var bendA: LinkBend? = null,
    var bendB: LinkBend? = null,
) {
    // the ends as floats, so a frame's links can refill one path without making garbage
    var sx = start.x; var sy = start.y; var sz = start.z
    var ex = end.x; var ey = end.y; var ez = end.z

    /** Where it leaves the sending note and reaches the receiving one (already trimmed). */
    var start: Vec3
        get() = Vec3(sx, sy, sz)
        set(v) { sx = v.x; sy = v.y; sz = v.z }
    var end: Vec3
        get() = Vec3(ex, ey, ez)
        set(v) { ex = v.x; ey = v.y; ez = v.z }
}

object LinkCurve {
    /** The longest distance along a link the shader is given (as iOS's, where the texture coordinate packs more above it). */
    const val ALONG_CAP = 60f

    /** The point [t] of the way along the grown part of the link (0 to 1), into [out] at [at]. */
    fun point(path: LinkPath, t: Float, out: FloatArray, at: Int = 0) {
        val s = t * path.grow
        var x = path.sx + (path.ex - path.sx) * s
        var y = path.sy + (path.ey - path.sy) * s
        var z = path.sz + (path.ez - path.sz) * s
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
    fun tangent(points: FloatArray, k: Int, n: Int, fallback: Vec3, out: FloatArray, at: Int = 0) =
        tangent(points, k, n, fallback.x, fallback.y, fallback.z, out, at)

    /** The same with the fallback direction as three floats ([fx], [fy], [fz]), making no garbage. */
    fun tangent(points: FloatArray, k: Int, n: Int, fx: Float, fy: Float, fz: Float, out: FloatArray, at: Int = 0) {
        val before = max(k - 1, 0) * 3
        val after = min(k + 1, n) * 3
        val dx = points[after] - points[before]; val dy = points[after + 1] - points[before + 1]; val dz = points[after + 2] - points[before + 2]
        val size = sqrt(dx * dx + dy * dy + dz * dz)
        if (size > 0.00001f) {
            out[at] = dx / size; out[at + 1] = dy / size; out[at + 2] = dz / size
        } else {
            out[at] = fx; out[at + 1] = fy; out[at + 2] = fz
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
        var rx = p[at] - b.cx; var ry = p[at + 1] - b.cy; var rz = p[at + 2] - b.cz
        val dist = sqrt(rx * rx + ry * ry + rz * rz)
        if (dist <= 0.0001f) return
        val ax = b.ax; val ay = b.ay; val az = b.az
        val lift = (rx * ax + ry * ay + rz * az) * b.pull * w
        rx -= ax * lift; ry -= ay * lift; rz -= az * lift
        if (b.swing > 0f) {
            // Rodrigues, as rotate(_:about:by:)
            val angle = b.swing * w * w
            val c = cos(angle); val sn = sin(angle)
            val along = (ax * rx + ay * ry + az * rz) * (1f - c)
            val sx = ay * rz - az * ry; val sy = az * rx - ax * rz; val sz = ax * ry - ay * rx
            val nx = rx * c + sx * sn + ax * along
            val ny = ry * c + sy * sn + ay * along
            val nz = rz * c + sz * sn + az * along
            rx = nx; ry = ny; rz = nz
        }
        val now = sqrt(rx * rx + ry * ry + rz * rz)
        if (now <= 0.0001f) return
        val k = dist / now
        p[at] = b.cx + rx * k; p[at + 1] = b.cy + ry * k; p[at + 2] = b.cz + rz * k
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
