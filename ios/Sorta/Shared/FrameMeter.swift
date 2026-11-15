import SwiftUI

extension View {
    /// The frame meter's mark over the whole app, in a debug build launched
    /// with `-SortaFrameMeter` (FrameMeter); nothing otherwise.
    @ViewBuilder
    func frameMeter() -> some View {
        #if DEBUG
        if FrameMeter.isOn {
            overlay(alignment: .topLeading) { FrameMeterMark() }
                .onAppear { FrameMeter.shared.start() }
        } else {
            self
        }
        #else
        self
        #endif
    }
}

#if DEBUG
import QuartzCore
import UIKit
import os

/// Debug builds only, launched with `-SortaFrameMeter` (FramePerfUITests;
/// perf, 15 Nov): Sorta's frames, timed while a UI test's phase runs. The
/// test posts the Darwin notification `com.cramdown.sorta.frames.begin`,
/// touches, then posts `….end`; the meter then sums up, per phase, each with
/// its shares over a 120 Hz refresh and a 60 Hz one (FrameSummary):
/// - main-interval: the main thread's display-link frames (CADisplayLink), the
///   time between them;
/// - main-work: the main thread's run-loop passes (from waking to sleeping
///   again);
/// - render-interval and render-work: the map's render-thread steps
///   (RenderFrames);
/// and puts the lines on its mark's accessibility value, for the test to
/// read, and in the log (subsystem com.cramdown.sorta, category frames),
/// each phase's work wrapped in a signpost.
@MainActor @Observable
final class FrameMeter {
    static let isOn: Bool = ProcessInfo.processInfo.arguments.contains("-SortaFrameMeter")
    static let shared = FrameMeter()
    static let beginName = "com.cramdown.sorta.frames.begin"
    static let endName = "com.cramdown.sorta.frames.end"

    /// The last phase's lines, numbered: the mark's value.
    private(set) var report: String = "none"

    @ObservationIgnored private var link: CADisplayLink?
    @ObservationIgnored private var observer: CFRunLoopObserver?
    @ObservationIgnored private var lastTick: CFTimeInterval = 0
    @ObservationIgnored private var woke: CFTimeInterval = 0
    @ObservationIgnored private var intervals: [Double] = []
    @ObservationIgnored private var busy: [Double] = []
    @ObservationIgnored private var recording = false
    @ObservationIgnored private var phases = 0
    @ObservationIgnored private var interval: OSSignpostIntervalState?
    @ObservationIgnored private let log = Logger(subsystem: "com.cramdown.sorta", category: "frames")
    @ObservationIgnored private let signposter = OSSignposter(subsystem: "com.cramdown.sorta", category: "frames")

    func start() {
        guard Self.isOn, link == nil else { return }
        let link = CADisplayLink(target: FrameMeterTicker(), selector: #selector(FrameMeterTicker.tick(_:)))
        // as fast as what it times may go: a link left at its default would tick at 60 on an iPhone
        link.preferredFrameRateRange = MotionRate.moving
        link.add(to: .main, forMode: .common)
        self.link = link
        let observer = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.afterWaiting.rawValue | CFRunLoopActivity.beforeWaiting.rawValue,
                                                          true, 0) { _, activity in
            MainActor.assumeIsolated { FrameMeter.shared.pass(activity) }
        }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
        self.observer = observer
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for name in [Self.beginName, Self.endName] {
            CFNotificationCenterAddObserver(center, nil, { _, _, name, _, _ in
                let heard: String = name?.rawValue as String? ?? ""
                DispatchQueue.main.async { MainActor.assumeIsolated { FrameMeter.shared.heard(heard) } }
            }, name as CFString, nil, .deliverImmediately)
        }
    }

    private func heard(_ name: String) {
        if name == Self.beginName { begin() } else if name == Self.endName { end() }
    }

    private func begin() {
        intervals.removeAll()
        busy.removeAll()
        lastTick = 0
        RenderFrames.shared.begin()
        interval = signposter.beginInterval("phase")
        recording = true
    }

    private func end() {
        guard recording else { return }
        recording = false
        if let interval { signposter.endInterval("phase", interval) }
        interval = nil
        let render = RenderFrames.shared.end()
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let hz: Int = scenes.first?.screen.maximumFramesPerSecond ?? 60
        phases += 1
        var lines: [String] = ["phase=\(phases) hz=\(hz)"]
        lines.append(FrameSummary.of(intervals).line("main-interval"))
        lines.append(FrameSummary.of(busy).line("main-work"))
        if !render.work.isEmpty {
            lines.append(FrameSummary.of(render.intervals).line("render-interval"))
            lines.append(FrameSummary.of(render.work).line("render-work"))
        }
        for line in lines { log.info("\(line, privacy: .public)") }
        report = lines.joined(separator: "\n")
    }

    fileprivate func tick(at timestamp: CFTimeInterval) {
        if recording, lastTick > 0 { intervals.append((timestamp - lastTick) * 1000) }
        lastTick = timestamp
    }

    private func pass(_ activity: CFRunLoopActivity) {
        let now: CFTimeInterval = CACurrentMediaTime()
        if activity == .afterWaiting {
            woke = now
        } else if activity == .beforeWaiting, woke > 0 {
            if recording { busy.append((now - woke) * 1000) }
            woke = 0
        }
    }
}

/// The display link's target: the meter is no Objective-C object.
final class FrameMeterTicker: NSObject {
    @MainActor @objc func tick(_ link: CADisplayLink) {
        FrameMeter.shared.tick(at: link.timestamp)
    }
}

/// The meter's mark: a speck the UI tests read (its value is the last
/// phase's lines), never touched and never drawn over anything. Its words
/// are verbatim, for tests only, so none reaches the String Catalog.
struct FrameMeterMark: View {
    var body: some View {
        Text(verbatim: " ")
            .font(.system(size: 2))
            .frame(width: 2, height: 2)
            .allowsHitTesting(false)
            .accessibilityLabel(Text(verbatim: "frame meter"))
            .accessibilityValue(FrameMeter.shared.report)
            .accessibilityIdentifier("frame-meter")
    }
}
#endif
