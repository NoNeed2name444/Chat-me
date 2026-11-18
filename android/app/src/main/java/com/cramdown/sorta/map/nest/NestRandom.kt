package com.cramdown.sorta.map.nest

// The nested maps' seeded draw (S11), the concept's mulberry32. iOS's NestRandom.swift is the same.

/** The concept's seeded draw (a 32-bit mix), so both platforms draw the same numbers in the same order. */
class NestRandom(seed: Int) {
    private var s: Int = seed
    fun next(): Double {
        s += 0x6D2B79F5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}
