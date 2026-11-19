package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The 3D Tree's draw lists (S11): every branch an instance of one curved, tapering tube and every
// leaf an instance of one leaf blade, so the whole tree is two instanced draws, worked out once per
// tree and mode, never per frame. The Graphics tier sets the meshes' detail and how many instances
// are kept; [TreeLayout3D.order] is breadth first, so a cap drops the finest twigs and leaves first.
// The owner's review of 19 Nov ("the tree doesn't look realistic neither the leaves"): the bark is a
// brown leaning a fifth to its folder's colour, a leaf a living green leaning nearly half to it, each
// leaf a little lighter or darker and turned about its stem, seeded by its node so every run agrees.

/**
 * [nodes] grown by [layout], coloured by [colors] (TreeColors.wood3d and leaf3d), for [tier].
 * [branches] holds [branchCount] instances of [BRANCH_FLOATS] (base xyz, tip xyz, the curve's control
 * point xyz, base and tip radius, rgb, node), [leaves] [leafCount] of [LEAF_FLOATS] (centre xyz, axis
 * xyz, radius, roll about the axis, rgb, node).
 */
class TreeModel(val nodes: NestNodes, val layout: TreeLayout3D, val colors: TreeColors, val tier: GraphicsTier) {
    val branches: FloatArray
    val leaves: FloatArray
    val branchCount: Int
    val leafCount: Int
    /** Whether the tier's cap left any branch or leaf out. */
    val capped: Boolean
    /** The tube's sides round and rings along, and the leaf blade's rows, for the tier. */
    val segments: Int = if (tier == GraphicsTier.HIGH) 12 else 7
    val rings: Int = if (tier == GraphicsTier.HIGH) 6 else 4
    val bladeRows: Int = if (tier == GraphicsTier.HIGH) 7 else 5
    val sky: FloatArray = rgb(colors.sky)
    val accent: FloatArray = rgb(colors.accent)
    /** The smallest leaf's radius, or a twig's length where there is none: how close the camera may come. */
    val smallest: Float
    /** What a view pads round each framed point ([TreeFit.points] holds every leaf's tip): a blade's half width. */
    val margin: Float get() = smallest * BLADE_WIDE.toFloat()

    init {
        val n = nodes.size
        val cap = if (tier == GraphicsTier.HIGH) HIGH_CAP else SMOOTH_CAP
        val keptBranches = min(n, cap)
        var leafTotal = 0
        for (i in 0 until n) if (layout.leaf[i] > 0) leafTotal++
        val keptLeaves = min(leafTotal, cap)
        branches = FloatArray(keptBranches * BRANCH_FLOATS)
        leaves = FloatArray(keptLeaves * LEAF_FLOATS)
        var b = 0; var l = 0
        var least = Double.POSITIVE_INFINITY
        for (k in 0 until n) {
            val i = layout.order[k]
            val g = nodes.group[i]
            if (b < keptBranches) {
                val o = b * BRANCH_FLOATS
                for (a in 0..2) {
                    val base = layout.base[3 * i + a]; val run = layout.dir[3 * i + a] * layout.length[i]
                    branches[o + a] = base.toFloat()
                    branches[o + 3 + a] = (base + run).toFloat()
                    branches[o + 6 + a] = (base + run / 2 + layout.bend[3 * i + a]).toFloat()
                }
                branches[o + 9] = layout.r0[i].toFloat(); branches[o + 10] = layout.r1[i].toFloat()
                put(branches, o + 11, colors.wood3d[g])
                branches[o + 14] = i.toFloat()
                b++
            }
            if (layout.leaf[i] > 0) {
                least = minOf(least, layout.leaf[i])
                if (l < keptLeaves) {
                    val o = l * LEAF_FLOATS
                    for (a in 0..2) { leaves[o + a] = layout.anchor[3 * i + a].toFloat(); leaves[o + 3 + a] = layout.dir[3 * i + a].toFloat() }
                    leaves[o + 6] = layout.leaf[i].toFloat()
                    leaves[o + 7] = ((2 * hash(i, 1) - 1) * ROLL).toFloat()
                    // a little lighter or darker, and some a touch toward yellow-green
                    val tone = TreeColors.mix(colors.leaf3d[g], YELLOW, hash(i, 2) * YELLOW_MOST)
                    val light = 1 - LIGHT_VAR / 2 + LIGHT_VAR * hash(i, 3)
                    leaves[o + 8] = (((tone shr 16) and 255) / 255.0 * light).coerceAtMost(1.0).toFloat()
                    leaves[o + 9] = (((tone shr 8) and 255) / 255.0 * light).coerceAtMost(1.0).toFloat()
                    leaves[o + 10] = ((tone and 255) / 255.0 * light).coerceAtMost(1.0).toFloat()
                    leaves[o + 11] = i.toFloat()
                    l++
                }
            }
        }
        branchCount = b; leafCount = l
        capped = keptBranches < n || keptLeaves < leafTotal
        smallest = (if (least.isFinite()) least else layout.length.minOrNull() ?: 0.1).toFloat().coerceAtLeast(1e-4f)
    }

    /** Triangles a frame draws: every branch's tube and every leaf's blade, in two instanced draws. */
    val triangles: Int get() = branchCount * segments * (rings + 1) * 2 + leafCount * (bladeRows - 1) * 4

    companion object {
        const val BRANCH_FLOATS = 15
        const val LEAF_FLOATS = 12
        /** Instances kept of each kind: the High tier draws every one a vault this size has, Smooth fewer. */
        const val HIGH_CAP = 30_000
        const val SMOOTH_CAP = 8_000
        /** A leaf turned about its stem at most this far either way (radians). */
        const val ROLL = 0.7
        /** A leaf lighter or darker by at most half this, and at most this far toward [YELLOW]. */
        const val LIGHT_VAR = 0.2
        const val YELLOW_MOST = 0.15
        const val YELLOW = 0xB9C24A
        /** How far a trunk's base flares out and over what share of it; a branch's collar likewise. */
        const val FLARE = 0.55f
        const val FLARE_SPAN = 0.18f
        const val COLLAR = 0.18f
        const val COLLAR_SPAN = 0.12f
        /** The tube's closing ring's mark (past 1 along), and how far past the tip, by its radius, it closes. */
        const val CAP = 2f
        const val CAP_REACH = 0.9f

        fun rgb(c: Int): FloatArray = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

        private fun put(out: FloatArray, at: Int, c: Int) {
            out[at] = ((c shr 16) and 255) / 255f; out[at + 1] = ((c shr 8) and 255) / 255f; out[at + 2] = (c and 255) / 255f
        }

        /** Node [i]'s seeded number in 0 until 1 for [salt], in 32-bit integer steps, so iOS's is the same. */
        fun hash(i: Int, salt: Int): Double {
            var x = (i.toLong() * 2654435761L + salt.toLong() * 374761393L) and 0xffffffffL
            x = ((x xor (x ushr 15)) * 2246822519L) and 0xffffffffL
            x = x xor (x ushr 13)
            return x.toDouble() / 4294967296.0
        }

        /** How far along a branch ring [k] of [rings] sits: closer together near the base, where the trunk flares. */
        fun ringAt(k: Int, rings: Int): Float = Math.pow(k.toDouble() / rings, 1.4).toFloat()

        /**
         * A tube's [segments] round by [rings] along (cos, sin, along 0 to 1 per vertex), then one more
         * ring at [CAP] along that closes its tip (the shader draws it as a point just past the tip, so
         * no branch ends in an open cut), and its triangles.
         */
        fun tube(segments: Int, rings: Int): Pair<FloatArray, ShortArray> {
            val v = FloatArray((segments + 1) * (rings + 2) * 3)
            for (r in 0..rings + 1) for (s in 0..segments) {
                val a = 2 * Math.PI * s / segments
                val o = (r * (segments + 1) + s) * 3
                v[o] = cos(a).toFloat(); v[o + 1] = sin(a).toFloat(); v[o + 2] = if (r > rings) CAP else ringAt(r, rings)
            }
            val idx = ShortArray(segments * (rings + 1) * 6)
            var k = 0
            for (r in 0..rings) for (s in 0 until segments) {
                val a = r * (segments + 1) + s; val c = a + segments + 1
                idx[k++] = a.toShort(); idx[k++] = (a + 1).toShort(); idx[k++] = c.toShort()
                idx[k++] = c.toShort(); idx[k++] = (a + 1).toShort(); idx[k++] = (c + 1).toShort()
            }
            return v to idx
        }

        /** A leaf blade's half width at [y] from its stem (0) to its tip (1): a pointed oval, widest below its middle. */
        fun bladeWidth(y: Double): Double = BLADE_WIDE * Math.pow(sin(Math.PI * y), 0.75) * (1.15 - 0.4 * y)
        const val BLADE_WIDE = 0.44
        /** How far its edges rise off the midrib (by its width), and its tip curls down (by its length). */
        const val FOLD = 0.3
        const val CURL = 0.3

        /**
         * A leaf blade, [rows] from stem to tip by three across (edge, midrib, edge): each vertex its place
         * (x across, y along from −1 at the stem to 1 at the tip, z off the blade's face) and its normal,
         * six floats; folded along its midrib and curled down to its tip. Its triangles.
         */
        fun blade(rows: Int): Pair<FloatArray, ShortArray> {
            val v = FloatArray(rows * 3 * 6)
            for (r in 0 until rows) for (c in 0..2) {
                val y = r.toDouble() / (rows - 1); val w = bladeWidth(y); val side = (c - 1).toDouble()
                val o = (r * 3 + c) * 6
                v[o] = (side * w).toFloat(); v[o + 1] = (2 * y - 1).toFloat()
                v[o + 2] = (FOLD * kotlin.math.abs(side) * w - CURL * y * y).toFloat()
            }
            val idx = ShortArray((rows - 1) * 2 * 6)
            var k = 0
            for (r in 0 until rows - 1) for (c in 0..1) {
                val a = r * 3 + c; val d = a + 3
                idx[k++] = a.toShort(); idx[k++] = (a + 1).toShort(); idx[k++] = d.toShort()
                idx[k++] = d.toShort(); idx[k++] = (a + 1).toShort(); idx[k++] = (d + 1).toShort()
            }
            // each vertex's normal: its triangles' faces, by area, turned to the blade's face (+z)
            val nrm = DoubleArray(rows * 3 * 3)
            for (t in 0 until idx.size / 3) {
                val p = IntArray(3) { idx[3 * t + it].toInt() }
                val e1 = DoubleArray(3) { (v[p[1] * 6 + it] - v[p[0] * 6 + it]).toDouble() }
                val e2 = DoubleArray(3) { (v[p[2] * 6 + it] - v[p[0] * 6 + it]).toDouble() }
                var fx = e1[1] * e2[2] - e1[2] * e2[1]; var fy = e1[2] * e2[0] - e1[0] * e2[2]; var fz = e1[0] * e2[1] - e1[1] * e2[0]
                if (fz < 0) { fx = -fx; fy = -fy; fz = -fz }
                for (q in p) { nrm[3 * q] += fx; nrm[3 * q + 1] += fy; nrm[3 * q + 2] += fz }
            }
            for (q in 0 until rows * 3) {
                val m = Math.sqrt(nrm[3 * q] * nrm[3 * q] + nrm[3 * q + 1] * nrm[3 * q + 1] + nrm[3 * q + 2] * nrm[3 * q + 2])
                val o = q * 6
                if (m < 1e-12) { v[o + 5] = 1f; continue }
                for (a in 0..2) v[o + 3 + a] = (nrm[3 * q + a] / m).toFloat()
            }
            return v to idx
        }
    }
}

/**
 * Each node's state byte for the renderer (TreeGl's texture): hidden by the filter, chosen, lit as a
 * link of the chosen, faded toward the page (outside the part flown in to), or none, the first that
 * holds. A flown-in part and all under it are never faded: they draw in full colour. iOS's TreeState
 * is the same.
 */
object TreeState {
    const val NONE = 0
    const val HIDDEN = 1
    const val CHOSEN = 2
    const val LINKED = 3
    const val FADED = 4

    fun of(nodes: NestNodes, i: Int, hidden: Boolean, chosen: Int, linked: Boolean, focus: Int): Int = when {
        hidden -> HIDDEN
        i == chosen -> CHOSEN
        linked -> LINKED
        focus in 1 until nodes.size && !nodes.within(focus, i) -> FADED
        else -> NONE
    }
}

/**
 * The Tree's first view, fitted close (S11, the lead's review of 18 Nov): MapFirstView.framing bounds
 * each point on its own, which left the tree a third of the room; [refine] then projects every point
 * through the pose and scales and moves it until their box, [margin] round each, fills [fill] of the
 * window the insets leave on its binding side, centred in it. iOS's TreeFit is the same.
 */
object TreeFit {
    const val ROUNDS = 8
    /** How many yaws round widestYaw tries, every 5 degrees. */
    const val YAWS = 72

    /**
     * The yaw from which [points] stand widest across (S11): a tree has no front, so the first view shows
     * its crown spread, its limbs apart rather than one behind another. Tried from [prefer] outward, a
     * step each way in turn, so a tie (within a hair) keeps the nearer. iOS's TreeFit.widestYaw.
     */
    fun widestYaw(points: List<Vec3>, prefer: Float): Float {
        var best = prefer; var most = -1f
        for (k in 0 until YAWS) {
            val step = (k + 1) / 2 * (if (k % 2 == 1) 1 else -1)
            val yaw = prefer + step * (2 * Math.PI / YAWS).toFloat()
            val c = cos(yaw); val s = sin(yaw)
            var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
            for (p in points) { val x = p.x * c - p.z * s; lo = min(lo, x); hi = kotlin.math.max(hi, x) }
            if (hi - lo > most * (1 + 1e-4f)) { most = hi - lo; best = yaw }
        }
        return best
    }

    fun refine(points: List<Vec3>, margin: Float, pose: MapPose, width: Float, height: Float, insets: MapInsets,
               fill: Float, fovY: Float): MapPose {
        if (points.isEmpty() || width <= 0f || height <= 0f) return pose
        val lens = OrbitCamera(yaw = pose.yaw, pitch = pose.pitch, fovY = fovY, roll = pose.roll)
        val r = lens.right(); val u = lens.up(); val b = lens.forward() * -1f
        val f = lens.focalPixels(height)
        val w = kotlin.math.max(width - insets.left - insets.right, 1f)
        val h = kotlin.math.max(height - insets.top - insets.bottom, 1f)
        // the window's middle from the view's, x right and y up, and the half box to fill
        val wx = insets.left + w / 2 - width / 2; val wy = height / 2 - (insets.top + h / 2)
        val hw = w / 2 * fill; val hh = h / 2 * fill
        var target = pose.target; var d = pose.distance
        repeat(ROUNDS) {
            val box = box(points, margin, target + b * d, r, u, b, f) ?: run { d *= 1.5f; return@repeat }
            val k = kotlin.math.max((box[1] - box[0]) / 2 / hw, (box[3] - box[2]) / 2 / hh)
            if (!(k > 0f) || !k.isFinite()) return@repeat
            val cx = (box[0] + box[1]) / 2; val cy = (box[2] + box[3]) / 2
            val nd = d * k
            // the box's middle shrinks toward the view's with the distance; then it is moved onto the window's
            target = target + (r * (cx * d - wx * nd) + u * (cy * d - wy * nd)) * (1f / f)
            d = nd
        }
        return MapPose(target, d, pose.yaw, pose.pitch, pose.roll)
    }

    /** Every branch's ends and middle (on its curve), every body and every leaf's tip of part [index]'s subtree in [layout], what a view of it frames. */
    fun points(layout: TreeLayout3D, index: Int): List<Vec3> {
        val nodes = layout.nodes
        val out = ArrayList<Vec3>((nodes.tout[index] - nodes.tin[index]) * 4)
        val at = DoubleArray(3)
        for (q in nodes.tin[index] until nodes.tout[index]) {
            val i = nodes.order[q]
            for (t in doubleArrayOf(0.0, 0.5, 1.0)) {
                layout.curveAt(i, t, at)
                out.add(Vec3(at[0].toFloat(), at[1].toFloat(), at[2].toFloat()))
            }
            out.add(Vec3(layout.anchor[3 * i].toFloat(), layout.anchor[3 * i + 1].toFloat(), layout.anchor[3 * i + 2].toFloat()))
            val lr = layout.leaf[i]
            if (lr > 0) out.add(Vec3((layout.anchor[3 * i] + layout.dir[3 * i] * lr).toFloat(), (layout.anchor[3 * i + 1] + layout.dir[3 * i + 1] * lr).toFloat(),
                (layout.anchor[3 * i + 2] + layout.dir[3 * i + 2] * lr).toFloat()))
        }
        return out
    }

    /**
     * A fly-in to part [index] (S11): its subtree's own points fitted close, as the first view is, from
     * the camera's yaw and pitch (an orbit is kept), [fill] of the room the [insets] leave. Its ball
     * overstated a big part: with the first view fitted close, flying in to Cardiology came no nearer.
     */
    fun flyIn(model: TreeModel, index: Int, yaw: Float, pitch: Float, fovY: Float, width: Float, height: Float,
              insets: MapInsets, fill: Float): MapPose? {
        val points = points(model.layout, index)
        val first = MapFirstView.framing(points, model.margin, width, height, insets, fill, yaw, pitch,
            Math.toDegrees(fovY.toDouble()).toFloat()) ?: return null
        return refine(points, model.margin, first, width, height, insets, fill, fovY)
    }

    /** The points' box on screen from [eye] (left, right, bottom, top, in pixels from the view's middle, y up); null if one is behind the eye. */
    fun box(points: List<Vec3>, margin: Float, eye: Vec3, r: Vec3, u: Vec3, b: Vec3, f: Float): FloatArray? {
        var x0 = Float.POSITIVE_INFINITY; var x1 = Float.NEGATIVE_INFINITY; var y0 = Float.POSITIVE_INFINITY; var y1 = Float.NEGATIVE_INFINITY
        for (p in points) {
            val q = p - eye
            val z = -q.dot(b)
            if (z <= 1e-4f) return null
            val k = f / z; val sx = q.dot(r) * k; val sy = q.dot(u) * k; val e = margin * k
            x0 = kotlin.math.min(x0, sx - e); x1 = kotlin.math.max(x1, sx + e); y0 = kotlin.math.min(y0, sy - e); y1 = kotlin.math.max(y1, sy + e)
        }
        return floatArrayOf(x0, x1, y0, y1)
    }
}
