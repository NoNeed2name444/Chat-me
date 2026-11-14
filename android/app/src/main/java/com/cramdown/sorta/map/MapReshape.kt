package com.cramdown.sorta.map

// Two fingers on the map that reshape ideas (S06c): pinch one idea apart to split it, push two together to
// join them. Pure: Map3DView's gesture loop asks it, and iOS's GraphReshapeTouch.swift is the same rule
// (the same cases in ios/UnitTests/Suites/IdeaShapeCases.json). Distances are in dp (points on iOS).
object MapReshape {
    /** What two fingers coming down may become: a split of [a], a join of [a] and [b], or nothing. */
    sealed interface Start {
        data class Split(val a: Int) : Start
        data class Join(val a: Int, val b: Int) : Start
        data object None : Start
    }

    /** How far a finger may be from an idea's centre and still be on it, dp. */
    const val REACH = 44.0

    /** How near a held body's finger must come to an idea, at least, for it to be let go there (its own radius when bigger), in dp. */
    const val DROP_DP = 16f

    /**
     * The start: [first] and [second] are the ideas under each finger, [middle] the idea under the point
     * between them, [apart] how far apart the fingers are.
     */
    fun start(first: Int?, second: Int?, middle: Int?, apart: Double): Start {
        if (first != null && second != null && first != second) return Start.Join(first, second)
        if (middle != null && apart <= 2 * REACH && (first == null || first == middle) && (second == null || second == middle)) {
            return Start.Split(middle)
        }
        return Start.None
    }

    enum class Judged { WAITING, DONE, FAILED }

    /**
     * A split is done once the fingers are max(1.6 x start, start + 60) apart, failed once they close below
     * 0.85 x start; a join is done once they are at most max(0.5 x start, 44) apart and 20 closer than at the
     * start, failed once past 1.15 x start.
     */
    fun judge(start: Start, from: Double, now: Double): Judged = when (start) {
        is Start.Split -> when {
            now >= maxOf(1.6 * from, from + 60) -> Judged.DONE
            now < 0.85 * from -> Judged.FAILED
            else -> Judged.WAITING
        }
        is Start.Join -> when {
            now <= maxOf(0.5 * from, 44.0) && now <= from - 20 -> Judged.DONE
            now > 1.15 * from -> Judged.FAILED
            else -> Judged.WAITING
        }
        Start.None -> Judged.FAILED
    }
}
