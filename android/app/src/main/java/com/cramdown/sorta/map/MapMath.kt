package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Plain Kotlin geometry for the 3D Ideas map, so the culling and level-of-detail rules run
// in JVM unit tests. Matrices are column-major float[16], as OpenGL ES expects them.

object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun perspective(fovYRadians: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(fovYRadians / 2)
        return FloatArray(16).also {
            it[0] = f / aspect; it[5] = f
            it[10] = (far + near) / (near - far); it[11] = -1f
            it[14] = 2 * far * near / (near - far)
        }
    }

    fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3(0f, 1f, 0f)): FloatArray {
        val f = (target - eye).normalized()
        val s = f.cross(up).let { if (it.length() < 1e-6f) f.cross(Vec3(0f, 0f, 1f)) else it }.normalized()
        val u = s.cross(f)
        return FloatArray(16).also {
            it[0] = s.x; it[4] = s.y; it[8] = s.z
            it[1] = u.x; it[5] = u.y; it[9] = u.z
            it[2] = -f.x; it[6] = -f.y; it[10] = -f.z
            it[12] = -s.dot(eye); it[13] = -u.dot(eye); it[14] = f.dot(eye); it[15] = 1f
        }
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (c in 0 until 4) for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = sum
        }
        return out
    }

    /** Clip-space x, y, w of a point; the caller divides. */
    fun project(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
    }
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 { val l = length(); return if (l < 1e-12f) this else this * (1f / l) }
    companion object { val ZERO = Vec3(0f, 0f, 0f) }
}

/** The six planes of a view-projection matrix (Gribb and Hartmann), normals pointing inwards. */
class Frustum(viewProj: FloatArray) {
    val planes = FloatArray(24)

    init {
        val m = viewProj
        fun row(r: Int, i: Int) = m[i * 4 + r]
        for (p in 0 until 6) {
            val axis = p / 2
            val sign = if (p % 2 == 0) 1f else -1f
            for (i in 0 until 4) planes[p * 4 + i] = row(3, i) + sign * row(axis, i)
            val n = sqrt(planes[p * 4] * planes[p * 4] + planes[p * 4 + 1] * planes[p * 4 + 1] + planes[p * 4 + 2] * planes[p * 4 + 2])
            if (n > 0) for (i in 0 until 4) planes[p * 4 + i] /= n
        }
    }

    /** True when any part of the sphere, grown by [margin], is inside every plane. */
    fun intersectsSphere(x: Float, y: Float, z: Float, radius: Float, margin: Float = 0f): Boolean {
        for (p in 0 until 6) {
            val d = planes[p * 4] * x + planes[p * 4 + 1] * y + planes[p * 4 + 2] * z + planes[p * 4 + 3]
            if (d < -(radius + margin)) return false
        }
        return true
    }
}

/**
 * An orbit camera: it looks at [target] from [distance] along yaw and pitch. Its velocity
 * (world units a second, of the eye) feeds the guard band, so chunks ahead of a pan are
 * prepared before they reach the screen.
 */
class OrbitCamera(
    var target: Vec3 = Vec3.ZERO,
    var distance: Float = 10f,
    var yaw: Float = 0.6f,
    var pitch: Float = 0.35f,
    val fovY: Float = (50.0 * Math.PI / 180).toFloat(),
) {
    var minDistance = 0.05f
    var maxDistance = 1e6f

    fun eye(): Vec3 {
        val cp = cos(pitch); return target + Vec3(cp * sin(yaw), sin(pitch), cp * cos(yaw)) * distance
    }

    /** Where it looks (eye to target), unit length; the screen's right and up in the world. */
    fun forward(): Vec3 { val cp = cos(pitch); return Vec3(-cp * sin(yaw), -sin(pitch), -cp * cos(yaw)) }
    fun right(): Vec3 = Vec3(cos(yaw), 0f, -sin(yaw))
    fun up(): Vec3 = right().cross(forward())

    /** Its view volume for a screen this shape (SpaceDepth's culling). */
    fun frustum(width: Float, height: Float, near: Float = near(), far: Float = far()): ViewFrustum =
        ViewFrustum.of(eye(), forward(), right(), up(), fovY, width, height, near, far)

    fun orbit(dYaw: Float, dPitch: Float) {
        yaw += dYaw; pitch = (pitch + dPitch).coerceIn(-1.5f, 1.5f)
    }

    fun zoom(factor: Float) { distance = (distance / factor).coerceIn(minDistance, maxDistance) }

    /** Moves the target in the screen plane by a fraction of the view height. */
    fun pan(dxFraction: Float, dyFraction: Float) {
        val eye = eye()
        val forward = (target - eye).normalized()
        val right = forward.cross(Vec3(0f, 1f, 0f)).normalized()
        val up = right.cross(forward)
        val worldPerScreen = 2 * distance * tan(fovY / 2)
        target = target + right * (-dxFraction * worldPerScreen) + up * (dyFraction * worldPerScreen)
    }

    fun near() = max(distance * 0.002f, 1e-4f)
    fun far() = distance * 4f + 1e4f
    fun view() = Mat4.lookAt(eye(), target)
    fun projection(aspect: Float) = Mat4.perspective(fovY, aspect, near(), far())

    /**
     * The distance at which a sphere of [radius] fits the view with [margin] to spare, on
     * whichever of the two axes is narrower (a portrait map is narrow across).
     */
    fun fitDistance(radius: Float, aspect: Float, margin: Float = 1.3f): Float {
        val halfV = fovY / 2
        val halfH = atan(tan(halfV) * aspect)
        return radius / sin(min(halfV, halfH)) * margin
    }

    /** Pixels per world unit at depth 1: half the viewport height over tan(fov / 2). */
    fun focalPixels(viewportHeight: Float) = viewportHeight / 2f / tan(fovY / 2)

    /**
     * The first view of a ball of [radius] round [centre], from this camera's yaw and pitch, fitted
     * to the part of a [width] x [height] view (pixels) the chrome leaves ([insets]), as iOS's
     * GraphPerfCamera.framing and GraphTilt.window (Graph3DView.swift:251-255): the ball fits that
     * window across its narrower side with [margin] to spare, and its centre lands in the window's
     * middle, so what the top strip or the bottom bar covers never holds the map at first. The
     * window is never less than a third of the view each way; the distance never under [least].
     */
    fun fitWindow(centre: Vec3, radius: Float, width: Float, height: Float, insets: MapInsets = MapInsets.NONE,
                  margin: Float = 1.3f, least: Float = 4f): MapFit {
        val wide = max(width, 1f)
        val tall = max(height, 1f)
        val seenWidth = max(wide - insets.left - insets.right, wide / 3)
        val seenHeight = max(tall - insets.top - insets.bottom, tall / 3)
        val focal = focalPixels(tall)
        // the window's half angles, about its own middle
        val half = min(atan(seenWidth / 2 / focal), atan(seenHeight / 2 / focal))
        val distance = max(radius / sin(half) * margin, least)
        // the window's middle from the view's, in pixels: rightwards and upwards
        val across = (insets.left - insets.right) / 2
        val up = (insets.bottom - insets.top) / 2
        val target = centre - (right() * across + up() * up) * (distance / focal)
        return MapFit(distance, target)
    }
}

/** How much of the map's view, on each side, the chrome covers, in pixels (iOS's GraphInsets). */
data class MapInsets(val top: Float = 0f, val left: Float = 0f, val bottom: Float = 0f, val right: Float = 0f) {
    companion object { val NONE = MapInsets() }
}

/** A first view ([OrbitCamera.fitWindow]): how far back the camera stands, and what it looks at. */
data class MapFit(val distance: Float, val target: Vec3)

/** A name's box on screen: its centre and size, in pixels. */
data class LabelBox(val x: Float, val y: Float, val width: Float, val height: Float)

/** A link's run on screen, from (ax, ay) to (bx, by), and how far its drawn band reaches each side of that line, in pixels. */
data class LabelLink(val ax: Float, val ay: Float, val bx: Float, val by: Float, val half: Float = 0f)

/** A body's disc on screen: its centre and radius, in pixels, and whose it is. */
data class LabelDisc(val x: Float, val y: Float, val radius: Float, val index: Int = -1)

/**
 * Which names go on screen, as iOS's GraphPerfLabelRoom: tried biggest body first, each one whose
 * box is clear of every name placed before it by [gap], so the names in a crowd do not print over
 * one another. Sorta's rule: the reference drew every name.
 *
 * [place] adds the rest of Sorta's rule for a map at rest (S03-result.md, step 4's list): a name
 * keeps a word's room from a name beside it, so two never read as one ("Performance and Space names
 * collide"); and it lies on no link and no other body ("Neurons' link covers its label"). Where its
 * place under the body is taken, it tries above, then after, then before it, and waits if none is clear.
 */
object LabelRoom {
    /** A name shown last frame counts as this much bigger, so two close ones do not swap as the camera drifts. */
    const val HOLD = 1.05f

    fun fits(box: LabelBox, placed: List<LabelBox>, gap: Float): Boolean = fits(box, placed, gap, gap)

    /** Clear of every placed name by [gapX] beside it and [gapY] above or below it. */
    fun fits(box: LabelBox, placed: List<LabelBox>, gapX: Float, gapY: Float): Boolean = placed.all { p ->
        abs(box.x - p.x) * 2 >= box.width + p.width + 2 * gapX || abs(box.y - p.y) * 2 >= box.height + p.height + 2 * gapY
    }

    /** Whether [link]'s band comes within [gap] of [box]: the line clipped to the box grown by the band and the gap. */
    fun crosses(box: LabelBox, link: LabelLink, gap: Float): Boolean {
        val grow = link.half + gap
        val left = box.x - box.width / 2 - grow; val right = box.x + box.width / 2 + grow
        val top = box.y - box.height / 2 - grow; val bottom = box.y + box.height / 2 + grow
        // Liang-Barsky: the part of the segment inside the rectangle, if any
        val dx = link.bx - link.ax; val dy = link.by - link.ay
        var t0 = 0f; var t1 = 1f
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(link.ax - left, right - link.ax, link.ay - top, bottom - link.ay)
        for (i in 0 until 4) {
            if (p[i] == 0f) { if (q[i] < 0f) return false; continue }
            val t = q[i] / p[i]
            if (p[i] < 0f) { if (t > t1) return false; if (t > t0) t0 = t }
            else { if (t < t0) return false; if (t < t1) t1 = t }
        }
        return t0 <= t1
    }

    /** Whether [disc] comes within [gap] of [box]. */
    fun covers(box: LabelBox, disc: LabelDisc, gap: Float): Boolean {
        val nx = disc.x.coerceIn(box.x - box.width / 2, box.x + box.width / 2)
        val ny = disc.y.coerceIn(box.y - box.height / 2, box.y + box.height / 2)
        val r = disc.radius + gap
        val ex = disc.x - nx; val ey = disc.y - ny
        return ex * ex + ey * ey < r * r
    }

    /**
     * A name of [width] x [height] for a body at ([x], [y]) drawn [pixels] across: under it, where
     * the map has always put it, [below] pixels from its centre to the name's; then as far above;
     * then after it and before it, [beside] pixels clear of its edge. In that order.
     */
    fun spots(x: Float, y: Float, pixels: Float, width: Float, height: Float, below: Float, beside: Float): List<LabelBox> {
        val side = pixels / 2 + beside + width / 2
        return listOf(LabelBox(x, y + below, width, height), LabelBox(x, y - below, width, height),
            LabelBox(x + side, y, width, height), LabelBox(x - side, y, width, height))
    }

    /**
     * The first of [spots] clear of every name [placed] (by [gapX] beside, [gapY] above or below),
     * every link in [links] and every body in [discs] (by [gapY]) but the name's own ([self]); null
     * when none is, and the name waits.
     */
    fun place(spots: List<LabelBox>, placed: List<LabelBox>, links: List<LabelLink>, discs: List<LabelDisc>,
              gapX: Float, gapY: Float, self: Int = -1): LabelBox? = spots.firstOrNull { box ->
        fits(box, placed, gapX, gapY) && links.none { crosses(box, it, gapY) } &&
            discs.none { (self < 0 || it.index != self) && covers(box, it, gapY) }
    }
}

fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun approxEquals(a: Float, b: Float, eps: Float = 1e-4f) = abs(a - b) <= eps * max(1f, max(abs(a), abs(b)))

internal fun clampInt(v: Int, lo: Int, hi: Int) = min(max(v, lo), hi)
