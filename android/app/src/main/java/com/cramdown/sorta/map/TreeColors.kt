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

    init {
        val tones = IntArray(NestNodes.GROUPS) { g -> if (g < NestNodes.TONES) TONE[g] else GREY }
        val barks = IntArray(NestNodes.GROUPS) { g ->
            when (g) { NestNodes.TRUNK -> bark; NestNodes.LOOSE -> mix(bark, GREY, 0.2); else -> mix(bark, tones[g], 0.3) }
        }
        barkRamp = Array(NestNodes.GROUPS) { g -> ramp(barks[g]) }
        leafRamp = Array(NestNodes.GROUPS) { g -> ramp(tones[g]) }
    }

    private fun ramp(c: Int): IntArray = IntArray(9) { s -> mix(sky, c, DIM + (1 - DIM) * s / 8.0) }

    companion object {
        /** How bright the parts outside the one in view stay. */
        const val DIM = 0.18
        /** NoteTone's seven, in its order (ideas/IdeaBook.kt). */
        val TONE = intArrayOf(0x14B8A6, 0x6366F1, 0xFF9F7A, 0xA78BFA, 0xA3E635, 0xF472B6, 0x3B82F6)
        /** NoteTone's grey. */
        const val GREY = 0x8E8E93

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
