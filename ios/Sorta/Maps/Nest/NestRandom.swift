import Foundation

// The nested maps' seeded draw (S11), the concept's mulberry32: the same
// numbers in the same order as Android's NestRandom.kt. Foundation only.

/// The concept's seeded draw (a 32-bit mix), so both platforms draw the same numbers in the same order.
struct NestRandom {
    private var s: UInt32

    init(seed: UInt32) { s = seed }

    mutating func next() -> Double {
        s = s &+ 0x6D2B_79F5
        var t: UInt32 = s
        t = (t ^ (t >> 15)) &* (t | 1)
        t = t ^ (t &+ ((t ^ (t >> 7)) &* (t | 61)))
        return Double(t ^ (t >> 14)) / 4_294_967_296.0
    }
}
