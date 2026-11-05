package com.cramdown.sorta.map

import com.cramdown.sorta.ideas.GraphStraightLine
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Space's links as strips, a port of iOS GraphRibbonWriter.fill and path (GraphRibbons.swift:
// 296-368, 464-489) for the GL map (S05e), drawn by SpaceGl as NeuronGl draws its bridges: one
// buffer, one GL_TRIANGLE_STRIP per link.
//
// Each link is `samples + 1` pairs of vertices along LinkCurve (or, with Lines: Straight, along
// the segment between the same trimmed ends, GraphStraightLine), as far as it has grown from its
// sending end. iOS turns each pair to face the eye on the CPU; here each vertex carries its point,
// the curve's tangent there and its side (-1 or 1), and the vertex shader (SpaceShaders.linkVertex)
// turns it, keeping a link at least 1.5 px wide. Each vertex also carries what iOS packs into the
// texture coordinates: the distance along, the drawn length, the end styles, the seed and lit, with
// distances in note radii ([unit]) as the beam's shader reads them.
//
// Pure Kotlin: LinkStripsTest.

/**
 * Strips of [samples] + 1 vertex pairs a link, written into [data] ([STRIDE] floats a vertex):
 * the point (x, y, z) and side; the tangent (x, y, z) and the distance along; the A and B ends'
 * style codes, the seed and lit; the drawn length.
 */
class LinkStrips(val samples: Int) {
    companion object {
        const val STRIDE = 13
        /** Neither end's trim takes more than this share of the link (iOS GraphRibbonWriter.path). */
        const val TRIM_SHARE = 0.45f

        /**
         * A link from [a] to [b] (three floats each, from [ao] and [bo]) trimmed by [trimA] and
         * [trimB], each at most [TRIM_SHARE] of its length, so a link shorter than its two trims is
         * still drawn: its ends into [out] (six floats, the start then the end).
         */
        fun trimmed(a: FloatArray, ao: Int, b: FloatArray, bo: Int, trimA: Float, trimB: Float, out: FloatArray) {
            val dx = b[bo] - a[ao]; val dy = b[bo + 1] - a[ao + 1]; val dz = b[bo + 2] - a[ao + 2]
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            var ux = 1f; var uy = 0f; var uz = 0f
            if (length > 0.0001f) { ux = dx / length; uy = dy / length; uz = dz / length }
            val ta = min(trimA, length * TRIM_SHARE)
            val tb = min(trimB, length * TRIM_SHARE)
            out[0] = a[ao] + ux * ta; out[1] = a[ao + 1] + uy * ta; out[2] = a[ao + 2] + uz * ta
            out[3] = b[bo] - ux * tb; out[4] = b[bo + 1] - uy * tb; out[5] = b[bo + 2] - uz * tb
        }
    }

    val perLink = (samples + 1) * 2
    var data = FloatArray(16 * perLink * STRIDE); private set
    /** Strips written since [clear]. */
    var count = 0; private set
    val vertices: Int get() = count * perLink

    private val points = FloatArray((samples + 1) * 3)
    private val lengths = FloatArray(samples + 1)
    private val tangent = FloatArray(3)
    private val path = LinkPath(Vec3.ZERO, Vec3.ZERO)
    // one bend for each end, refilled for every link [write] draws, so a frame makes no garbage
    private val scratchA = LinkBend(Vec3.ZERO, Vec3(0f, 1f, 0f), 0f, 0f)
    private val scratchB = LinkBend(Vec3.ZERO, Vec3(0f, 1f, 0f), 0f, 0f)

    fun clear() { count = 0 }

    /**
     * Writes every beam of [beams] (SpaceBodies' beams, [SpaceBodies.BEAM_STRIDE] floats each, [beamCount]
     * of them) as a strip, after clearing; [unit] is a note's radius, [straight] the Lines setting.
     * It runs every frame, so it makes no garbage.
     */
    fun write(beams: FloatArray, beamCount: Int, unit: Float, straight: Boolean) {
        clear()
        for (k in 0 until beamCount) {
            val o = k * SpaceBodies.BEAM_STRIDE
            val sa = beams[o + 3].toInt(); val sb = beams[o + 7].toInt()
            add(beams[o], beams[o + 1], beams[o + 2], beams[o + 4], beams[o + 5], beams[o + 6], sa, sb,
                beams[o + 8], beams[o + 9], beams[o + 10],
                scratchA.set(sa, beams, o + 11), scratchB.set(sb, beams, o + 17),
                unit, straight)
        }
    }

    /**
     * One link's strip, from its trimmed [start] (the sending end) to its trimmed [end]: [grow] is
     * how far it has grown (eased), [bendA] and [bendB] its bends near each end (Straight ignores them).
     */
    fun add(start: Vec3, end: Vec3, styleA: Int, styleB: Int, seed: Float, lit: Float, grow: Float,
            bendA: LinkBend?, bendB: LinkBend?, unit: Float, straight: Boolean) =
        add(start.x, start.y, start.z, end.x, end.y, end.z, styleA, styleB, seed, lit, grow, bendA, bendB, unit, straight)

    /** The same with the ends as floats ([sx], [sy], [sz] to [ex], [ey], [ez]), making no garbage. */
    fun add(sx: Float, sy: Float, sz: Float, ex: Float, ey: Float, ez: Float, styleA: Int, styleB: Int,
            seed: Float, lit: Float, grow: Float, bendA: LinkBend?, bendB: LinkBend?, unit: Float, straight: Boolean) {
        val need = (count + 1) * perLink * STRIDE
        if (data.size < need) data = data.copyOf(max(need, data.size * 2))
        val n = samples
        path.sx = sx; path.sy = sy; path.sz = sz; path.ex = ex; path.ey = ey; path.ez = ez; path.grow = grow
        path.bendA = bendA; path.bendB = bendB
        val total = if (straight) GraphStraightLine.sample(sx, sy, sz, ex, ey, ez, grow, n, points, lengths)
                    else LinkCurve.sample(path, n, points, lengths)
        // the fallback direction for a sample with no length: the link's own, or x for a link of none
        var dx = ex - sx; var dy = ey - sy; var dz = ez - sz
        val size = sqrt(dx * dx + dy * dy + dz * dz)
        if (size > 0.00001f) { dx /= size; dy /= size; dz /= size } else { dx = 1f; dy = 0f; dz = 0f }
        // distances in note radii; a long link's are shrunk to fit the shader's cap, and its drawn
        // length is coded in sixteenths, as iOS packs them
        val u = max(unit, 1e-6f)
        val scale = LinkCurve.alongScale(total / u)
        val coded = min(total / u * scale, LinkCurve.ALONG_CAP)
        val len = floor(coded * 16f) / 16f
        var o = count * perLink * STRIDE
        for (k in 0..n) {
            LinkCurve.tangent(points, k, n, dx, dy, dz, tangent)
            val along = min(lengths[k] / u * scale, LinkCurve.ALONG_CAP)
            for (side in 0..1) {
                data[o] = points[k * 3]; data[o + 1] = points[k * 3 + 1]; data[o + 2] = points[k * 3 + 2]
                data[o + 3] = if (side == 0) -1f else 1f
                data[o + 4] = tangent[0]; data[o + 5] = tangent[1]; data[o + 6] = tangent[2]
                data[o + 7] = along
                data[o + 8] = styleA.toFloat(); data[o + 9] = styleB.toFloat(); data[o + 10] = seed; data[o + 11] = lit
                data[o + 12] = len
                o += STRIDE
            }
        }
        count++
    }
}
