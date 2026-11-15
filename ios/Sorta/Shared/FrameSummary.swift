import Foundation

/// Frame times summed up (perf, 15 Nov): how many, the 50th, 95th and 99th
/// percentiles (nearest rank), and the shares over a 120 Hz refresh (`over8`,
/// over 8.3 ms) and over a 60 Hz one (`over16`, over 16.7 ms): the owner's bar
/// is 120 frames a second on a 120 Hz screen. For the frame meter
/// (FrameMeter, debug builds) and the frame tests (FramePerfUITests), as
/// Android's FrameSummary.
nonisolated struct FrameSummary: Equatable, Sendable {
    let frames: Int
    let p50: Double
    let p95: Double
    let p99: Double
    let over8: Double
    let over16: Double

    /// A 120 Hz refresh, and a 60 Hz one, in milliseconds.
    static let refresh120: Double = 1000.0 / 120
    static let refresh60: Double = 1000.0 / 60
    static let none = FrameSummary(frames: 0, p50: 0, p95: 0, p99: 0, over8: 0, over16: 0)

    static func of(_ ms: [Double]) -> FrameSummary {
        guard !ms.isEmpty else { return .none }
        let sorted: [Double] = ms.sorted()
        let count: Double = Double(ms.count)
        let over8: Int = ms.filter { $0 > refresh120 }.count
        let over16: Int = ms.filter { $0 > refresh60 }.count
        return FrameSummary(frames: ms.count, p50: percentile(sorted, 0.50), p95: percentile(sorted, 0.95),
                            p99: percentile(sorted, 0.99), over8: Double(over8) / count, over16: Double(over16) / count)
    }

    /// The nearest-rank `p` percentile of `sorted`: the smallest value at
    /// least a `p` share of them reach.
    static func percentile(_ sorted: [Double], _ p: Double) -> Double {
        guard !sorted.isEmpty else { return 0 }
        let rank: Int = min(max(Int((p * Double(sorted.count)).rounded(.up)), 1), sorted.count)
        return sorted[rank - 1]
    }

    /// One line, as Android's: "<name> frames=… p50=… p95=… p99=… over8.3=…%
    /// over16.7=…%".
    func line(_ name: String) -> String {
        name + String(format: " frames=%d p50=%.1f p95=%.1f p99=%.1f over8.3=%.1f%% over16.7=%.1f%%",
                      frames, p50, p95, p99, over8 * 100, over16 * 100)
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
