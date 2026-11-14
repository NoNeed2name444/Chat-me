import Foundation

/// Two fingers on the map that reshape ideas (S06c): pinch one idea apart
/// to split it, push two together to join them. Pure: Graph3DView's
/// reshape recogniser asks it, and Android's MapReshape.kt is the same rule
/// (the same cases in UnitTests/Suites/IdeaShapeCases.json). Each touch is
/// in points (iOS) or dp (Android).
nonisolated enum GraphReshapeTouch {
    /// What two fingers coming down may become.
    enum Start: Equatable {
        /// Both within reach of one idea: a split, if they spread.
        case split(Int)
        /// Each on a different idea: a join, if they close.
        case join(Int, Int)
        /// Neither: the camera's pinch and pan.
        case none
    }

    /// How far a finger may be from an idea's centre and still be on it.
    static let reach: Double = 44

    /// A held idea is over another when the finger is within that one's
    /// drawn radius, or this many points of its centre for a small one
    /// (Android's MapReshape.DROP_DP).
    static let drop: Double = 16

    /// The start: `first` and `second` are the ideas under each finger,
    /// `middle` the idea under the point between them, `apart` how far
    /// apart the fingers are.
    static func start(first: Int?, second: Int?, middle: Int?, apart: Double) -> Start {
        if let first, let second, first != second { return .join(first, second) }
        if let middle, apart <= 2 * reach, first == nil || first == middle, second == nil || second == middle {
            return .split(middle)
        }
        return .none
    }

    /// Where a gesture in waiting stands.
    enum Judged: Equatable { case waiting, done, failed }

    /// A split is done once the fingers are `max(1.6 x start, start + 60)`
    /// apart, failed once they close below 0.85 x start; a join is done once
    /// they are at most `max(0.5 x start, 44)` apart and 20 closer than at
    /// the start, failed once past 1.15 x start.
    static func judge(_ start: Start, from: Double, now: Double) -> Judged {
        switch start {
        case .split:
            if now >= max(1.6 * from, from + 60) { return .done }
            if now < 0.85 * from { return .failed }
            return .waiting
        case .join:
            if now <= max(0.5 * from, 44) && now <= from - 20 { return .done }
            if now > 1.15 * from { return .failed }
            return .waiting
        case .none:
            return .failed
        }
    }
}
