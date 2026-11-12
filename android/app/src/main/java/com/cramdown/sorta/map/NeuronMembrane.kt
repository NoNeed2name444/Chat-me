package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// A Neurons cell's membrane: its soma and dendrites as one closed surface (ported from iOS
// GraphNeuronMembrane). The surface is the zero set of one distance field - the unit soma
// smoothly united with each dendrite, a chain of round cones along its curve - meshed by
// surface nets on a regular grid, every vertex pulled onto the surface with the field's
// gradient as its normal. Plain floats, so it runs and is tested on the JVM.

/** One tapered tube of an arbor, in the soma's unit frame: a quadratic curve start-bend-end. */
data class NeuronBranch(
    val start: Vec3, val bend: Vec3, val end: Vec3, val r0: Float, val r1: Float, val s0: Float, val s1: Float,
    val flare: Float = 0f,
) {
    fun point(t: Float): Vec3 {
        val u = 1 - t
        return start * (u * u) + bend * (2 * u * t) + end * (t * t)
    }
}

/** A growable float array. */
class FloatList(capacity: Int = 1024) {
    var data = FloatArray(capacity); private set
    var size = 0; private set
    fun add(v: Float) { if (size == data.size) data = data.copyOf(max(16, size * 2)); data[size++] = v }
    fun toArray(): FloatArray = data.copyOf(size)
}

/* IntList is shared with Octree.kt. */

/**
 * A cell's soma and dendrites as one closed surface: [points] and unit [normals] (xyz each),
 * [coords] per vertex (x the share along its dendrite, y how much is soma) and triangles
 * wound counter-clockwise seen from outside.
 */
class NeuronMembrane(val points: FloatArray, val normals: FloatArray, val coords: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = points.size / 3

    companion object {
        const val BLEND = 0.3f
        const val FLARE = 0.15f
        const val PIECES = 12
        const val STEP = 0.075f

        fun make(branches: List<NeuronBranch>, step: Float = STEP): NeuronMembrane {
            val grid = NeuronMembraneGrid(NeuronMembraneField(branches), step)
            grid.fill()
            return grid.mesh()
        }
    }
}

/** The membrane's signed distance (negative inside), with a polynomial smooth union. */
class NeuronMembraneField(branches: List<NeuronBranch>, val blend: Float = NeuronMembrane.BLEND,
                          flare: Float = NeuronMembrane.FLARE, pieces: Int = NeuronMembrane.PIECES) {
    class Cone(a: Vec3, b: Vec3, val ra: Float, val rb: Float, val sa: Float, val sb: Float) {
        val ax = a.x; val ay = a.y; val az = a.z
        val bx = b.x; val by = b.y; val bz = b.z
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val l2 = max(ux * ux + uy * uy + uz * uz, 1e-12f)
        val rr = ra - rb
        val a2 = l2 - rr * rr
        val il2 = 1 / l2
        val cosine = sqrt(max(a2 * il2, 0f))
        val mx = (a.x + b.x) * 0.5f; val my = (a.y + b.y) * 0.5f; val mz = (a.z + b.z) * 0.5f
        val reach = sqrt(l2) * 0.5f + max(ra, rb)
    }

    class Limb(val first: Int, val end: Int, val x: Float, val y: Float, val z: Float, val radius: Float)

    class Sample(var d: Float, var gx: Float, var gy: Float, var gz: Float, var s: Float, var w: Float)

    val cones: List<Cone>
    val limbs: List<Limb>

    init {
        val cs = ArrayList<Cone>()
        val ls = ArrayList<Limb>()
        val steps = max(pieces, 1)
        for (branch in branches) {
            val first = cs.size
            val trumpet = if (branch.flare > 0) flare else 0f
            fun at(j: Int): Float { val even = j.toFloat() / steps; return if (branch.flare > 0) even.pow(1.6f) else even }
            fun radius(t: Float): Float { val rest = (1 - t) * (1 - t); return branch.r0 + (branch.r1 - branch.r0) * t + trumpet * rest * rest }
            var lox = Float.MAX_VALUE; var loy = Float.MAX_VALUE; var loz = Float.MAX_VALUE
            var hix = -Float.MAX_VALUE; var hiy = -Float.MAX_VALUE; var hiz = -Float.MAX_VALUE
            val ends = ArrayList<Pair<Vec3, Float>>()
            for (j in 0 until steps) {
                val t0 = at(j); val t1 = at(j + 1)
                val a = branch.point(t0); val b = branch.point(t1)
                val r0 = radius(t0); val r1 = radius(t1)
                cs.add(Cone(a, b, r0, r1, branch.s0 + (branch.s1 - branch.s0) * t0, branch.s0 + (branch.s1 - branch.s0) * t1))
                ends.add(Pair(a, r0)); ends.add(Pair(b, r1))
                lox = min(lox, min(a.x - r0, b.x - r1)); loy = min(loy, min(a.y - r0, b.y - r1)); loz = min(loz, min(a.z - r0, b.z - r1))
                hix = max(hix, max(a.x + r0, b.x + r1)); hiy = max(hiy, max(a.y + r0, b.y + r1)); hiz = max(hiz, max(a.z + r0, b.z + r1))
            }
            val cx = (lox + hix) * 0.5f; val cy = (loy + hiy) * 0.5f; val cz = (loz + hiz) * 0.5f
            var reach = 0f
            for ((p, r) in ends) {
                val ox = p.x - cx; val oy = p.y - cy; val oz = p.z - cz
                reach = max(reach, sqrt(ox * ox + oy * oy + oz * oz) + r)
            }
            ls.add(Limb(first, cs.size, cx, cy, cz, reach))
        }
        cones = cs
        limbs = ls
    }

    private val coneArray: Array<Cone> = cones.toTypedArray()
    private val limbArray: Array<Limb> = limbs.toTypedArray()

    fun sample(px: Float, py: Float, pz: Float, slope: Boolean = true): Sample {
        val r = sqrt(px * px + py * py + pz * pz)
        val out = Sample(r - 1, 0f, 0f, 1f, 0f, 1f)
        if (slope && r > 1e-12f) { out.gx = px / r; out.gy = py / r; out.gz = pz / r }
        val k = blend
        for (limb in limbArray) {
            val lx = px - limb.x; val ly = py - limb.y; val lz = pz - limb.z
            if (sqrt(lx * lx + ly * ly + lz * lz) - limb.radius >= out.d + k) continue
            var b = Float.MAX_VALUE
            var along = 0f
            var nearest = limb.first
            var part = 0
            var index = limb.first
            while (index < limb.end) {
                val cone = coneArray[index]
                index += 1
                val cx = px - cone.mx; val cy = py - cone.my; val cz = pz - cone.mz
                if (sqrt(cx * cx + cy * cy + cz * cz) - cone.reach >= b) continue
                val e = distance(px, py, pz, cone)
                if (e < b) {
                    b = e
                    along = cone.sa + (cone.sb - cone.sa) * lastT
                    nearest = index - 1
                    part = lastPart
                }
            }
            val mix = 0.5f + 0.5f * (b - out.d) / k
            val h = if (mix < 0) 0f else if (mix > 1) 1f else mix
            out.d = b + (out.d - b) * h - k * h * (1 - h)
            out.s = along + (out.s - along) * h
            out.w *= h
            if (slope && h < 1) {
                slope(px, py, pz, coneArray[nearest], part)
                out.gx = sx + (out.gx - sx) * h
                out.gy = sy + (out.gy - sy) * h
                out.gz = sz + (out.gz - sz) * h
            }
        }
        return out
    }

    fun distance(p: Vec3): Float = sample(p.x, p.y, p.z, slope = false).d

    // what the last distance() and slope() found, kept in fields so the inner loop makes no objects
    private var lastT = 0f
    private var lastPart = 0
    private var sx = 0f; private var sy = 0f; private var sz = 0f

    /** IQ's exact distance to a round cone; sets [lastT] (how far along) and [lastPart] (0 side, 1 a's end, 2 b's). */
    private fun distance(px: Float, py: Float, pz: Float, c: Cone): Float {
        val pax = px - c.ax; val pay = py - c.ay; val paz = pz - c.az
        val y = pax * c.ux + pay * c.uy + paz * c.uz
        val raw = y * c.il2
        val t = if (raw < 0) 0f else if (raw > 1) 1f else raw
        if (c.a2 <= 0) {
            if (c.ra >= c.rb) { lastT = 0f; lastPart = 1; return sqrt(pax * pax + pay * pay + paz * paz) - c.ra }
            val pbx = px - c.bx; val pby = py - c.by; val pbz = pz - c.bz
            lastT = 1f; lastPart = 2
            return sqrt(pbx * pbx + pby * pby + pbz * pbz) - c.rb
        }
        val z = y - c.l2
        val qx = pax * c.l2 - c.ux * y
        val qy = pay * c.l2 - c.uy * y
        val qz = paz * c.l2 - c.uz * y
        val x2 = qx * qx + qy * qy + qz * qz
        val y2 = y * y * c.l2
        val z2 = z * z * c.l2
        val k = sign(c.rr) * c.rr * c.rr * x2
        lastT = t
        if (sign(z) * c.a2 * z2 > k) { lastPart = 2; return sqrt(x2 + z2) * c.il2 - c.rb }
        if (sign(y) * c.a2 * y2 < k) { lastPart = 1; return sqrt(x2 + y2) * c.il2 - c.ra }
        lastPart = 0
        return (sqrt(x2 * c.a2 * c.il2) + y * c.rr) * c.il2 - c.ra
    }

    /** The gradient of that distance on `part` of the cone, into [sx], [sy], [sz]. */
    private fun slope(px: Float, py: Float, pz: Float, c: Cone, part: Int) {
        var x = px - c.ax; var y = py - c.ay; var z = pz - c.az
        if (part == 2) {
            x = px - c.bx; y = py - c.by; z = pz - c.bz
        } else if (part == 0) {
            val along = (x * c.ux + y * c.uy + z * c.uz) * c.il2
            val qx = x - c.ux * along; val qy = y - c.uy * along; val qz = z - c.uz * along
            val size = sqrt(qx * qx + qy * qy + qz * qz)
            val lean = c.rr * c.il2
            if (size < 1e-12f) { val s = sqrt(c.il2); sx = c.ux * s; sy = c.uy * s; sz = c.uz * s; return }
            val out = c.cosine / size
            sx = qx * out + c.ux * lean; sy = qy * out + c.uy * lean; sz = qz * out + c.uz * lean
            return
        }
        val size = sqrt(x * x + y * y + z * z)
        if (size > 1e-12f) { sx = x / size; sy = y / size; sz = z / size } else { sx = 0f; sy = 0f; sz = 1f }
    }

    companion object {
        fun sign(x: Float): Float = if (x > 0) 1f else if (x < 0) -1f else 0f
    }
}

/** The field sampled on a grid round the cell and meshed by surface nets. */
class NeuronMembraneGrid(val field: NeuronMembraneField, val step: Float) {
    val ox: Float; val oy: Float; val oz: Float
    val nx: Int; val ny: Int; val nz: Int
    val values: FloatArray
    val exact: BooleanArray

    init {
        var lox = -1f; var loy = -1f; var loz = -1f
        var hix = 1f; var hiy = 1f; var hiz = 1f
        for (c in field.cones) {
            lox = min(lox, min(c.ax - c.ra, c.bx - c.rb)); loy = min(loy, min(c.ay - c.ra, c.by - c.rb)); loz = min(loz, min(c.az - c.ra, c.bz - c.rb))
            hix = max(hix, max(c.ax + c.ra, c.bx + c.rb)); hiy = max(hiy, max(c.ay + c.ra, c.by + c.rb)); hiz = max(hiz, max(c.az + c.ra, c.bz + c.rb))
        }
        val margin = field.blend / 4 + 2 * step
        lox -= margin; loy -= margin; loz -= margin
        hix += margin; hiy += margin; hiz += margin
        ox = lox; oy = loy; oz = loz
        nx = ceil((hix - lox) / step).toInt() + 1
        ny = ceil((hiy - loy) / step).toInt() + 1
        nz = ceil((hiz - loz) / step).toInt() + 1
        values = FloatArray(nx * ny * nz) { 1f }
        exact = BooleanArray(nx * ny * nz)
    }

    fun index(i: Int, j: Int, k: Int): Int = i + nx * (j + ny * k)

    fun fill() {
        for (k in 0 until nz) {
            val z = oz + k * step
            for (j in 0 until ny) {
                val y = oy + j * step
                var i = 0
                while (i < nx) {
                    val v = field.sample(ox + i * step, y, z, slope = false).d
                    val at = index(i, j, k)
                    values[at] = v
                    exact[at] = true
                    val skip = min((abs(v) / step).toInt() - 1, nx - 1 - i)
                    if (skip > 0) for (m in 1..skip) values[at + m] = if (v > 0) v - m * step else v + m * step
                    i += max(skip, 0) + 1
                }
            }
        }
    }

    fun value(i: Int, j: Int, k: Int): Float {
        val at = index(i, j, k)
        if (!exact[at]) {
            values[at] = field.sample(ox + i * step, oy + j * step, oz + k * step, slope = false).d
            exact[at] = true
        }
        return values[at]
    }

    fun mesh(): NeuronMembrane {
        val points = FloatList(); val normals = FloatList(); val coords = FloatList(); val indices = IntList()
        val cx = nx - 1; val cy = ny - 1; val cz = nz - 1
        val vertex = IntArray(cx * cy * cz) { -1 }
        val plane = nx * ny
        val offsets = intArrayOf(0, 1, nx, nx + 1, plane, plane + 1, plane + nx, plane + nx + 1)
        val e0s = intArrayOf(0, 2, 4, 6, 0, 1, 4, 5, 0, 1, 2, 3)
        val e1s = intArrayOf(1, 3, 5, 7, 2, 3, 6, 7, 4, 5, 6, 7)
        val v = FloatArray(8)
        val clear = 1.75f * step
        var count = 0
        for (k in 0 until cz) for (j in 0 until cy) for (i in 0 until cx) {
            val base = index(i, j, k)
            val corner = values[base]
            if (corner > clear || corner < -clear) continue
            var inside = 0
            for (o in offsets) if (values[base + o] < 0) inside += 1
            if (inside == 0 || inside == 8) continue
            for (n in 0 until 8) v[n] = value(i + (n and 1), j + (n shr 1 and 1), k + (n shr 2))
            var sx = 0f; var sy = 0f; var sz = 0f; var crossings = 0f
            for (e in 0 until 12) {
                val e0 = e0s[e]; val e1 = e1s[e]
                if ((v[e0] < 0) == (v[e1] < 0)) continue
                val t = v[e0] / (v[e0] - v[e1])
                sx += (e0 and 1) + ((e1 and 1) - (e0 and 1)) * t
                sy += (e0 shr 1 and 1) + ((e1 shr 1 and 1) - (e0 shr 1 and 1)) * t
                sz += (e0 shr 2) + ((e1 shr 2) - (e0 shr 2)) * t
                crossings += 1
            }
            vertex[i + cx * (j + cy * k)] = count++
            val p = FloatArray(3)
            val at = settle(ox + (i + sx / crossings) * step, oy + (j + sy / crossings) * step, oz + (k + sz / crossings) * step, p)
            points.add(p[0]); points.add(p[1]); points.add(p[2])
            val size = sqrt(at.gx * at.gx + at.gy * at.gy + at.gz * at.gz)
            if (size > 1e-6f) { normals.add(at.gx / size); normals.add(at.gy / size); normals.add(at.gz / size) }
            else { normals.add(0f); normals.add(0f); normals.add(1f) }
            coords.add(min(max(at.s, 0f), 1f)); coords.add(min(max(at.w, 0f), 1f))
        }
        val pts = points.data
        fun cell(i: Int, j: Int, k: Int): Int = vertex[i + cx * (j + cy * k)]
        fun d2(a: Int, b: Int): Float {
            val x = pts[a * 3] - pts[b * 3]; val y = pts[a * 3 + 1] - pts[b * 3 + 1]; val z = pts[a * 3 + 2] - pts[b * 3 + 2]
            return x * x + y * y + z * z
        }
        fun quad(q0: Int, q1: Int, q2: Int, q3: Int, flip: Boolean) {
            if (q0 < 0 || q1 < 0 || q2 < 0 || q3 < 0) return
            val r1 = if (flip) q3 else q1
            val r3 = if (flip) q1 else q3
            if (d2(q0, q2) <= d2(r1, r3)) {
                indices.add(q0); indices.add(r1); indices.add(q2); indices.add(q0); indices.add(q2); indices.add(r3)
            } else {
                indices.add(q0); indices.add(r1); indices.add(r3); indices.add(r1); indices.add(q2); indices.add(r3)
            }
        }
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val value = values[index(i, j, k)]
            if (value > step || value < -step) continue
            val here = value < 0
            if (i + 1 < nx && j > 0 && k > 0 && j < cy && k < cz && here != (values[index(i + 1, j, k)] < 0)) {
                quad(cell(i, j - 1, k - 1), cell(i, j, k - 1), cell(i, j, k), cell(i, j - 1, k), !here)
            }
            if (j + 1 < ny && i > 0 && k > 0 && i < cx && k < cz && here != (values[index(i, j + 1, k)] < 0)) {
                quad(cell(i - 1, j, k - 1), cell(i - 1, j, k), cell(i, j, k), cell(i, j, k - 1), !here)
            }
            if (k + 1 < nz && i > 0 && j > 0 && i < cx && j < cy && here != (values[index(i, j, k + 1)] < 0)) {
                quad(cell(i - 1, j - 1, k), cell(i, j - 1, k), cell(i, j, k), cell(i - 1, j, k), !here)
            }
        }
        return NeuronMembrane(points.toArray(), normals.toArray(), coords.toArray(), indices.toArray())
    }

    /** Pulls a vertex onto the surface by two Newton steps, never more than a step away; writes it into [out]. */
    fun settle(x0: Float, y0: Float, z0: Float, out: FloatArray): NeuronMembraneField.Sample {
        var x = x0; var y = y0; var z = z0
        var at = field.sample(x, y, z)
        for (n in 0 until 2) {
            val g2 = at.gx * at.gx + at.gy * at.gy + at.gz * at.gz
            if (!(g2 > 1e-8f)) break
            val move = at.d / g2
            x -= at.gx * move; y -= at.gy * move; z -= at.gz * move
            val mx = x - x0; val my = y - y0; val mz = z - z0
            val far = sqrt(mx * mx + my * my + mz * mz)
            if (far > step) { x = x0 + mx * (step / far); y = y0 + my * (step / far); z = z0 + mz * (step / far) }
            at = field.sample(x, y, z)
        }
        out[0] = x; out[1] = y; out[2] = z
        return at
    }
}
