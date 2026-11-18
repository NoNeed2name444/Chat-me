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
