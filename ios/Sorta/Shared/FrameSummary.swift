import Foundation

/// Frame times summed up (perf, 15 Nov): how many, the 50th, 95th and 99th
/// percentiles (nearest rank), and the share of frames late (over `over`
/// milliseconds). For the frame meter (FrameMeter, debug builds) and the
/// frame tests (FramePerfUITests), as Android's FrameSummary.
nonisolated struct FrameSummary: Equatable, Sendable {
    let frames: Int
    let p50: Double
    let p95: Double
    let p99: Double
    let late: Double

    static let none = FrameSummary(frames: 0, p50: 0, p95: 0, p99: 0, late: 0)

    static func of(_ ms: [Double], over: Double) -> FrameSummary {
        guard !ms.isEmpty else { return .none }
        let sorted: [Double] = ms.sorted()
        let late: Int = ms.filter { $0 > over }.count
        return FrameSummary(frames: ms.count, p50: percentile(sorted, 0.50), p95: percentile(sorted, 0.95),
                            p99: percentile(sorted, 0.99), late: Double(late) / Double(ms.count))
    }

    /// The nearest-rank `p` percentile of `sorted`: the smallest value at
    /// least a `p` share of them reach.
    static func percentile(_ sorted: [Double], _ p: Double) -> Double {
        guard !sorted.isEmpty else { return 0 }
        let rank: Int = min(max(Int((p * Double(sorted.count)).rounded(.up)), 1), sorted.count)
        return sorted[rank - 1]
    }

    /// One line, as Android's: "<name> frames=… p50=… p95=… p99=… late=…%".
    func line(_ name: String) -> String {
        name + String(format: " frames=%d p50=%.1f p95=%.1f p99=%.1f late=%.1f%%", frames, p50, p95, p99, late * 100)
    }
}

/// The map's render-thread steps (GraphSim's renderer(_:updateAtTime:)): the
/// time between them and the work each took, recorded between `begin` and
/// `end`, only where the app was launched with `-SortaFrameMeter` in a debug
/// build (perf, 15 Nov). A released app never records.
nonisolated final class RenderFrames: @unchecked Sendable {
    static let shared = RenderFrames()
    #if DEBUG
    static let isOn: Bool = ProcessInfo.processInfo.arguments.contains("-SortaFrameMeter")
    #else
    static let isOn: Bool = false
    #endif

    private let lock = NSLock()
    private var on = false
    private var last: Double = 0
    private var intervals: [Double] = []
    private var work: [Double] = []

    /// One step: it began and ended at these media times (seconds), for a
    /// frame at `time`.
    func add(began: Double, ended: Double, at time: Double) {
        lock.lock()
        defer { lock.unlock() }
        guard on else { return }
        if last > 0, time > last { intervals.append((time - last) * 1000) }
        last = time
        work.append((ended - began) * 1000)
    }

    func begin() {
        lock.lock()
        defer { lock.unlock() }
        intervals.removeAll()
        work.removeAll()
        last = 0
        on = true
    }

    /// Stops recording: the intervals and the work, in milliseconds.
    func end() -> (intervals: [Double], work: [Double]) {
        lock.lock()
        defer { lock.unlock() }
        on = false
        return (intervals, work)
    }
}
