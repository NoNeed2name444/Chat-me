// The frame meter's sums (FrameSummary) and the map's render-thread record
// (RenderFrames; perf, 15 Nov), as Android's FrameSummaryTest.
//
// Compiled with Shared/FrameSummary.swift, which imports only Foundation.
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "PASS " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

// MARK: F1 - nearest-rank percentiles and the share late

do {
    let ms: [Double] = (1...100).reversed().map(Double.init)
    let s = FrameSummary.of(ms, over: 16.7)
    check("F1a every frame counted", s.frames == 100, "\(s.frames)")
    check("F1b p50, p95 and p99 by nearest rank", s.p50 == 50 && s.p95 == 95 && s.p99 == 99, "\(s)")
    check("F1c 17 to 100 ms are late over 16.7 ms: 84 of 100", abs(s.late - 0.84) < 1e-9, "\(s.late)")
}

// MARK: F2 - few and none

do {
    check("F2a none", FrameSummary.of([], over: 16.7) == .none)
    let one = FrameSummary.of([8], over: 16.7)
    check("F2b one frame is every percentile", one.p50 == 8 && one.p99 == 8 && one.late == 0, "\(one)")
    check("F2c the line, as Android's", one.line("map") == "map frames=1 p50=8.0 p95=8.0 p99=8.0 late=0.0%", one.line("map"))
}

// MARK: F3 - the render thread's record only between begin and end

do {
    let r = RenderFrames()
    r.add(began: 0, ended: 0.004, at: 1)
    r.begin()
    r.add(began: 1.000, ended: 1.004, at: 1.000)
    r.add(began: 1.017, ended: 1.026, at: 1.0165)
    let (intervals, work) = r.end()
    r.add(began: 2, ended: 2.02, at: 2)
    check("F3a one interval, from the second step", intervals.count == 1 && abs(intervals[0] - 16.5) < 1e-6, "\(intervals)")
    check("F3b each step's work, in ms", work.count == 2 && abs(work[0] - 4) < 1e-6 && abs(work[1] - 9) < 1e-6, "\(work)")
    check("F3c nothing recorded after the end", r.end().work.count == 2)
}

print(failures.isEmpty ? "ALL PASSED" : "\(failures.count) FAILED: \(failures.joined(separator: ", "))")
exit(failures.isEmpty ? 0 : 1)
