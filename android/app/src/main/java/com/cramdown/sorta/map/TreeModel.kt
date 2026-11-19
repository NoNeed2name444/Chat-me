package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The 3D Tree's draw lists (S11): every branch an instance of one tapered cylinder and every leaf an
// instance of one flattened ball, so the whole tree is two instanced draws, worked out once per tree
// and mode, never per frame. The Graphics tier sets the meshes' detail and how many instances are
// kept; [TreeLayout3D.order] is breadth first, so a cap drops the finest twigs and leaves first.

/**
 * [nodes] grown by [layout], coloured by [colors] (the bark and leaf ramps' full step), for [tier].
 * [branches] holds [branchCount] instances of [BRANCH_FLOATS] (base xyz, tip xyz, base and tip radius,
 * rgb, node), [leaves] [leafCount] of [LEAF_FLOATS] (centre xyz, axis xyz, radius, rgb, node).
 */
class TreeModel(val nodes: NestNodes, val layout: TreeLayout3D, val colors: TreeColors, val tier: GraphicsTier) {
    val branches: FloatArray
    val leaves: FloatArray
    val branchCount: Int
    val leafCount: Int
    /** Whether the tier's cap left any branch or leaf out. */
    val capped: Boolean
    val segments: Int = if (tier == GraphicsTier.HIGH) 12 else 7
    val sky: FloatArray = rgb(colors.sky)
    val accent: FloatArray = rgb(colors.accent)
    /** The smallest leaf's radius, or a twig's length where there is none: how close the camera may come. */
    val smallest: Float

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
                    branches[o + a] = layout.base[3 * i + a].toFloat()
                    branches[o + 3 + a] = (layout.base[3 * i + a] + layout.dir[3 * i + a] * layout.length[i]).toFloat()
                }
                branches[o + 6] = layout.r0[i].toFloat(); branches[o + 7] = layout.r1[i].toFloat()
                put(branches, o + 8, colors.barkRamp[g][8])
                branches[o + 11] = i.toFloat()
                b++
            }
            if (layout.leaf[i] > 0) {
                least = minOf(least, layout.leaf[i])
                if (l < keptLeaves) {
                    val o = l * LEAF_FLOATS
                    for (a in 0..2) { leaves[o + a] = layout.anchor[3 * i + a].toFloat(); leaves[o + 3 + a] = layout.dir[3 * i + a].toFloat() }
                    leaves[o + 6] = layout.leaf[i].toFloat()
                    put(leaves, o + 7, colors.leafRamp[g][8])
                    leaves[o + 10] = i.toFloat()
                    l++
                }
            }
        }
        branchCount = b; leafCount = l
        capped = keptBranches < n || keptLeaves < leafTotal
        smallest = (if (least.isFinite()) least else layout.length.minOrNull() ?: 0.1).toFloat().coerceAtLeast(1e-4f)
    }

    companion object {
        const val BRANCH_FLOATS = 12
        const val LEAF_FLOATS = 11
        /** Instances kept of each kind: the High tier draws every one a vault this size has, Smooth fewer. */
        const val HIGH_CAP = 30_000
        const val SMOOTH_CAP = 8_000

        fun rgb(c: Int): FloatArray = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

        private fun put(out: FloatArray, at: Int, c: Int) {
            out[at] = ((c shr 16) and 255) / 255f; out[at + 1] = ((c shr 8) and 255) / 255f; out[at + 2] = (c and 255) / 255f
        }

        /** A tube's [segments] round (cos, sin, along 0 or 1 per vertex), and its triangles. */
        fun tube(segments: Int): Pair<FloatArray, ShortArray> {
            val v = FloatArray((segments + 1) * 2 * 3)
            for (s in 0..segments) for (h in 0..1) {
                val a = 2 * Math.PI * s / segments
                val o = (s * 2 + h) * 3
                v[o] = cos(a).toFloat(); v[o + 1] = sin(a).toFloat(); v[o + 2] = h.toFloat()
            }
            val idx = ShortArray(segments * 6)
            for (s in 0 until segments) {
                val a = (s * 2).toShort(); val b = (s * 2 + 1).toShort(); val c = (s * 2 + 2).toShort(); val d = (s * 2 + 3).toShort()
                val o = s * 6
                idx[o] = a; idx[o + 1] = c; idx[o + 2] = b; idx[o + 3] = b; idx[o + 4] = c; idx[o + 5] = d
            }
            return v to idx
        }

        /** A unit ball, [rings] from pole to pole and [segments] round (x, y, z per vertex, y the leaf's axis), and its triangles. */
        fun ball(rings: Int, segments: Int): Pair<FloatArray, ShortArray> {
            val v = FloatArray((rings + 1) * (segments + 1) * 3)
            for (r in 0..rings) for (s in 0..segments) {
                val t = Math.PI * r / rings; val p = 2 * Math.PI * s / segments
                val o = (r * (segments + 1) + s) * 3
                v[o] = (sin(t) * cos(p)).toFloat(); v[o + 1] = cos(t).toFloat(); v[o + 2] = (sin(t) * sin(p)).toFloat()
            }
            val idx = ShortArray(rings * segments * 6)
            var k = 0
            for (r in 0 until rings) for (s in 0 until segments) {
                val a = r * (segments + 1) + s; val b = a + segments + 1
                idx[k++] = a.toShort(); idx[k++] = b.toShort(); idx[k++] = (a + 1).toShort()
                idx[k++] = (a + 1).toShort(); idx[k++] = b.toShort(); idx[k++] = (b + 1).toShort()
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

    /** Every branch end and body of part [index]'s subtree in [layout], what a view of it frames. */
    fun points(layout: TreeLayout3D, index: Int): List<Vec3> {
        val nodes = layout.nodes
        val out = ArrayList<Vec3>((nodes.tout[index] - nodes.tin[index]) * 3)
        for (q in nodes.tin[index] until nodes.tout[index]) {
            val i = nodes.order[q]
            for (t in doubleArrayOf(0.0, layout.length[i])) out.add(Vec3((layout.base[3 * i] + layout.dir[3 * i] * t).toFloat(),
                (layout.base[3 * i + 1] + layout.dir[3 * i + 1] * t).toFloat(), (layout.base[3 * i + 2] + layout.dir[3 * i + 2] * t).toFloat()))
            out.add(Vec3(layout.anchor[3 * i].toFloat(), layout.anchor[3 * i + 1].toFloat(), layout.anchor[3 * i + 2].toFloat()))
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
        val first = MapFirstView.framing(points, model.smallest, width, height, insets, fill, yaw, pitch,
            Math.toDegrees(fovY.toDouble()).toFloat()) ?: return null
        return refine(points, model.smallest, first, width, height, insets, fill, fovY)
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
