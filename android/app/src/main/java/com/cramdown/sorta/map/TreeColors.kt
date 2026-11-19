package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

// The Tree look's colours (S11), the approved concept's tokens: a sky that is the page, bark, a
// vine for links and the names' ink, by day and by night; each folder's bark leans 30% to its
// NoteTone colour (ideas in no folder 20% to grey), and its leaves are the colour itself. A part
// outside the one in view is drawn in a solid mix toward the sky, in eight steps ([ramp]). Worked
// out once per mode, here, so both platforms draw the same pixels. iOS's TreeColors.swift is the same.

/** ARGB colours, mixed as the concept mixes them (each channel rounded). */
class TreeColors(val dark: Boolean) {
    val sky: Int = if (dark) 0x281E2A else 0xF3EADB
    val bark: Int = if (dark) 0xD3C5B5 else 0x5B4650
    val vine: Int = if (dark) 0xA4C283 else 0x5E8A3A
    val ink: Int = if (dark) 0xF3EADB else 0x281E2A
    /** The names' halo: the sky. */
    val halo: Int = sky
    /** The chosen part's ring: Sorta's accent (WardToken.primary). */
    val accent: Int = if (dark) 0xEE866D else 0xC4503A

    /** [NestNodes.GROUPS] groups by 9 steps: bark and leaves, from the dimmest (0) to full (8). */
    val barkRamp: Array<IntArray>
    val leafRamp: Array<IntArray>
    /** The 3D tree's (S11, the owner's review of 19 Nov: a believable tree): its bark a neutral brown,
     * each folder's leaning [WOOD_TINT] to its colour; a top folder's leaves in its colour's hue, at least
     * [LEAF_SAT_LEAST] and at most [LEAF_SAT_MOST] saturated, as light as the living green [foliage] (the
     * lead's review of 19 Nov: folders told apart at the whole view), so each keeps the green's contrast
     * with the sky; the trunk's and loose ideas' leaves the green itself ([hued]). */
    val wood: Int get() = if (dark) 0xB39880 else 0x6E5443
    val foliage: Int get() = if (dark) 0x8FC26C else 0x4D7A35
    val wood3d: IntArray
    val leaf3d: IntArray

    init {
        val tones = IntArray(NestNodes.GROUPS) { g -> if (g < NestNodes.TONES) TONE[g] else GREY }
        val barks = IntArray(NestNodes.GROUPS) { g ->
            when (g) { NestNodes.TRUNK -> bark; NestNodes.LOOSE -> mix(bark, GREY, 0.2); else -> mix(bark, tones[g], 0.3) }
        }
        barkRamp = Array(NestNodes.GROUPS) { g -> ramp(barks[g]) }
        leafRamp = Array(NestNodes.GROUPS) { g -> ramp(tones[g]) }
        wood3d = IntArray(NestNodes.GROUPS) { g ->
            when (g) { NestNodes.TRUNK -> wood; NestNodes.LOOSE -> mix(wood, GREY, WOOD_TINT); else -> mix(wood, tones[g], WOOD_TINT) }
        }
        leaf3d = IntArray(NestNodes.GROUPS) { g -> if (g == NestNodes.TRUNK || g == NestNodes.LOOSE) foliage else hued(tones[g], foliage) }
    }

    private fun ramp(c: Int): IntArray = IntArray(9) { s -> mix(sky, c, DIM + (1 - DIM) * s / 8.0) }

    companion object {
        /** How bright the parts outside the one in view stay. */
        const val DIM = 0.18
        /** NoteTone's seven, in its order (ideas/IdeaBook.kt). */
        val TONE = intArrayOf(0x14B8A6, 0x6366F1, 0xFF9F7A, 0xA78BFA, 0xA3E635, 0xF472B6, 0x3B82F6)
        /** NoteTone's grey. */
        const val GREY = 0x8E8E93
        /** How far the 3D bark leans to its folder's colour. */
        const val WOOD_TINT = 0.2
        /** A top folder's leaves' saturation, in its colour's hue. */
        const val LEAF_SAT_LEAST = 0.35
        const val LEAF_SAT_MOST = 0.6

        /**
         * [tone]'s hue, its saturation held between [LEAF_SAT_LEAST] and [LEAF_SAT_MOST], at the lightness that
         * gives it [like]'s luma (0.299 R + 0.587 G + 0.114 B), found by halving 40 times; each channel rounded
         * half up. iOS's TreeColors.hued is the same, step for step.
         */
        fun hued(tone: Int, like: Int): Int {
            val r = ((tone shr 16) and 255) / 255.0; val g = ((tone shr 8) and 255) / 255.0; val b = (tone and 255) / 255.0
            val most = maxOf(r, g, b); val least = minOf(r, g, b); val d = most - least
            val l0 = (most + least) / 2
            var h = 0.0; var s = 0.0
            if (d > 0) {
                s = d / (1 - Math.abs(2 * l0 - 1))
                h = when (most) { r -> 60 * ((g - b) / d); g -> 60 * ((b - r) / d + 2); else -> 60 * ((r - g) / d + 4) }
                if (h < 0) h += 360
            }
            s = s.coerceIn(LEAF_SAT_LEAST, LEAF_SAT_MOST)
            val target = luma(((like shr 16) and 255).toDouble(), ((like shr 8) and 255).toDouble(), (like and 255).toDouble())
            var lo = 0.0; var hi = 1.0
            repeat(40) {
                val mid = (lo + hi) / 2
                val v = fromHsl(h, s, mid)
                if (luma(v[0], v[1], v[2]) < target) lo = mid else hi = mid
            }
            val v = fromHsl(h, s, (lo + hi) / 2)
            fun ch(x: Double) = Math.floor(x + 0.5).toInt().coerceIn(0, 255)
            return (ch(v[0]) shl 16) or (ch(v[1]) shl 8) or ch(v[2])
        }

        private fun luma(r: Double, g: Double, b: Double) = 0.299 * r + 0.587 * g + 0.114 * b

        /** Hue [h] (degrees), saturation [s] and lightness [l] as red, green and blue from 0 to 255. */
        private fun fromHsl(h: Double, s: Double, l: Double): DoubleArray {
            val c = (1 - Math.abs(2 * l - 1)) * s
            val hp = h / 60
            val x = c * (1 - Math.abs(hp % 2 - 1))
            val m = l - c / 2
            val (r, g, b) = when (hp.toInt() % 6) {
                0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
                3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
            }
            return doubleArrayOf((r + m) * 255, (g + m) * 255, (b + m) * 255)
        }

        /** [a] toward [b] by [t], each channel rounded half up, as the concept's toHex(Math.round). */
        fun mix(a: Int, b: Int, t: Double): Int {
            fun ch(shift: Int): Int {
                val x = (a shr shift) and 255; val y = (b shr shift) and 255
                return Math.floor(x + (y - x) * t + 0.5).toInt().coerceIn(0, 255)
            }
            return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
