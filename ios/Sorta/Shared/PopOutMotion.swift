// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// PopOutMotion.swift). The tilt comes from the motion sensor alone: the face
// tracking (ARKit and the camera) is left out, so Sorta never asks for the
// camera (docs/scoping.md section 1). Stethoscore's sky setting
// (SpaceQuality) and Graphics setting (GraphQuality) are not in Sorta yet:
// their inputs are read here directly.
import CoreMotion
import Foundation
import QuartzCore
import SwiftUI
import UIKit

// MARK: - The one motion source behind the pop-out
//
// One CMMotionManager with NO handler, read by one CADisplayLink on the main
// run loop: a pull model, so there are no handler closures on other queues,
// nothing Sendable to get wrong, and nothing runs while the effect is still.
//
// It publishes `eye`: where the viewer's eye sits against the screen's
// normal, in screen axes (+x right, +y down), -1...1. At rest it sits on
// PopOutTuning.restEye - the "held naturally" pose - and slowly re-centres on
// however the device is being held, so only a CHANGE of tilt moves things.
//
// Nothing else is observable except `style` and `gain`, and only the
// pop-out modifiers read `eye`, so a screen's body never re-renders with it.

/// The smoothed motion delta (eye - restEye), readable from any thread -
/// for SceneKit's render loop. Reads (0, 0) whenever the style is not live.
final class PopOutSnapshot: @unchecked Sendable {
    private let lock = NSLock()
    private var value = SIMD2<Float>(0, 0)

    func write(_ v: SIMD2<Float>) {
        lock.lock()
        value = v
        lock.unlock()
    }

    func read() -> SIMD2<Float> {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
}

/// The display link's target. Holds the engine weakly so the link never
/// keeps it alive, and hops onto the main actor the link already runs on.
final class PopOutTickTarget: NSObject {
    weak var owner: PopOutMotion?

    @objc func tick(_ link: CADisplayLink) {
        MainActor.assumeIsolated {
            owner?.tick(link)
        }
    }
}

@MainActor
@Observable
final class PopOutMotion {
    static let shared = PopOutMotion()

    enum Style: Equatable { case live, still, off }

    /// Where the viewer's eye sits vs the screen normal (+x right, +y down).
    private(set) var eye: CGPoint = PopOutTuning.restEye
    private(set) var style: Style = .still
    /// How far the eye's move is carried: 1, the motion sensor's own (the
    /// face tracking that carried it further is left out).
    let gain: CGFloat = 1

    /// Lock-protected copy of the delta for SceneKit's render thread.
    nonisolated static let snapshot = PopOutSnapshot()

    private let manager = CMMotionManager()
    @ObservationIgnored private var link: CADisplayLink?
    private let target = PopOutTickTarget()
    @ObservationIgnored private var observers: [NSObjectProtocol] = []

    // gates
    @ObservationIgnored private var appActive = false
    @ObservationIgnored private var keyboardUp = false
    @ObservationIgnored private var freezeTokens: Set<String> = []

    // filter state
    @ObservationIgnored private var lastTime: CFTimeInterval = 0
    @ObservationIgnored private var baseline = SIMD2<Double>(0, 0)
    @ObservationIgnored private var hasBaseline = false
    @ObservationIgnored private var smooth = SIMD2<Double>(0, 0)
    @ObservationIgnored private var orientation: UIInterfaceOrientation = .portrait
    @ObservationIgnored private var orientationCheckedAt: CFTimeInterval = 0
    @ObservationIgnored private var lastPublishAt: CFTimeInterval = 0
    @ObservationIgnored private var idle = false

    private init() {
        target.owner = self
        appActive = UIApplication.shared.applicationState == .active
        installObservers()
        // The first reader is usually a view body; starting (and animating
        // `style`) from inside a body update is avoided by waiting one turn.
        Task { @MainActor [weak self] in
            self?.refresh()
        }
    }

    // MARK: Gates

    /// Re-evaluates every gate. Idempotent; never restarts what is already
    /// running.
    func refresh() {
        guard PopOutSettings.isEnabled else {
            stopEngine()
            settle(.off, at: .zero)
            return
        }
        let hardStill: Bool = mustStayStill()
        let motionReady: Bool = manager.isDeviceMotionAvailable
        if hardStill || !motionReady {
            stopEngine()
            settle(.still, at: PopOutTuning.restEye)
            return
        }
        startEngine()
        if !idle { link?.preferredFrameRateRange = PopOutMotion.fullRate() }
        if style != .live {
            smooth = .zero
            withAnimation(.smooth(duration: 0.3)) {
                style = .live
                eye = PopOutTuning.restEye
            }
        }
    }

    func freeze(_ token: String) {
        freezeTokens.insert(token)
        refresh()
    }

    func unfreeze(_ token: String) {
        freezeTokens.remove(token)
        refresh()
    }

    /// From the root's scene phase (popOutLifecycle).
    func noteScene(active: Bool) {
        appActive = active
        refresh()
    }

    private func mustStayStill() -> Bool {
        if !appActive { return true }
        if UIAccessibility.isReduceMotionEnabled { return true }
        if UIAccessibility.isVoiceOverRunning { return true }
        if ProcessInfo.processInfo.isLowPowerModeEnabled { return true }
        let thermal = ProcessInfo.processInfo.thermalState
        if thermal == .serious || thermal == .critical { return true }
        if keyboardUp { return true }
        // what Stethoscore's sky setting (SpaceQuality) asked of the tilt: it
        // runs only at full quality, so a warm device, Reduce Transparency,
        // Increase Contrast and the UI tests' still launch hold still
        if thermal == .fair { return true }
        if UIAccessibility.isReduceTransparencyEnabled { return true }
        if UIAccessibility.isDarkerSystemColorsEnabled { return true }
        if ProcessInfo.processInfo.arguments.contains("-stillSky") { return true }
        return !freezeTokens.isEmpty
    }

    private func settle(_ now: Style, at rest: CGPoint) {
        PopOutMotion.snapshot.write(SIMD2<Float>(0, 0))
        guard style != now || eye != rest else { return }
        withAnimation(Motion.gentle(.smooth(duration: 0.3))) {
            style = now
            eye = rest
        }
    }

    // MARK: Engine

    private func startEngine() {
        if manager.isDeviceMotionAvailable && !manager.isDeviceMotionActive {
            manager.deviceMotionUpdateInterval = 1.0 / 60
            manager.startDeviceMotionUpdates()
            hasBaseline = false
        }
        if link == nil {
            let made = CADisplayLink(target: target, selector: #selector(PopOutTickTarget.tick(_:)))
            made.preferredFrameRateRange = PopOutMotion.fullRate()
            made.add(to: .main, forMode: .common)
            link = made
            lastTime = 0
            idle = false
            lastPublishAt = CACurrentMediaTime()
        }
    }

    private func stopEngine() {
        link?.invalidate()
        link = nil
        if manager.isDeviceMotionActive { manager.stopDeviceMotionUpdates() }
        hasBaseline = false
        smooth = .zero
    }

    /// Up to 120 a second (the screen's own rate; a 60 Hz screen keeps 60).
    private static func fullRate() -> CAFrameRateRange {
        CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
    }
    private static let idleRate = CAFrameRateRange(minimum: 10, maximum: 15, preferred: 15)

    private func setIdle(_ on: Bool) {
        idle = on
        link?.preferredFrameRateRange = on ? PopOutMotion.idleRate : PopOutMotion.fullRate()
        let interval: Double = on ? 1.0 / 15 : 1.0 / 60
        manager.deviceMotionUpdateInterval = interval
    }

    fileprivate func tick(_ link: CADisplayLink) {
        guard style == .live else { return }
        let now: CFTimeInterval = link.targetTimestamp
        let raw: Double = lastTime == 0 ? 1.0 / 60 : now - lastTime
        lastTime = now
        let dt: Double = min(0.1, max(1.0 / 240, raw))
        checkOrientation(now)

        let delta: SIMD2<Double> = motionDelta(dt: dt)
        let k: Double = 1 - exp(-dt / 0.07)
        smooth += (delta - smooth) * k

        let moved = smooth
        PopOutMotion.snapshot.write(SIMD2<Float>(Float(moved.x), Float(moved.y)))

        let rest: CGPoint = PopOutTuning.restEye
        let tx: CGFloat = PopOutMotion.unit(rest.x + CGFloat(moved.x))
        let ty: CGFloat = PopOutMotion.unit(rest.y + CGFloat(moved.y))
        let step: CGFloat = PopOutTuning.publishStep
        let changed: Bool = abs(tx - eye.x) > step || abs(ty - eye.y) > step
        if changed {
            eye = CGPoint(x: tx, y: ty)
            lastPublishAt = now
            if idle { setIdle(false) }
        } else if !idle && now - lastPublishAt > 1 {
            setIdle(true)
        }
    }

    /// Tilt, as a change from however the device has lately been held.
    private func motionDelta(dt: Double) -> SIMD2<Double> {
        guard let gravity = manager.deviceMotion?.gravity else { return .zero }
        let device = SIMD2<Double>(gravity.x, gravity.y)
        let s: SIMD2<Double> = PopOutMotion.screenVector(device, orientation)
        if !hasBaseline {
            baseline = s
            hasBaseline = true
        }
        let k: Double = 1 - exp(-dt / 3.0)
        baseline += (s - baseline) * k
        let d: SIMD2<Double> = (s - baseline) / 0.35
        let x: Double = PopOutMotion.shaped(d.x)
        let y: Double = PopOutMotion.shaped(d.y)
        // tipping the right edge away puts the eye to the left; tilting the
        // top edge away puts it lower
        return SIMD2<Double>(-x, -y)
    }

    private func checkOrientation(_ now: CFTimeInterval) {
        guard now - orientationCheckedAt > 0.5 else { return }
        orientationCheckedAt = now
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let current: UIInterfaceOrientation = scenes.first?.effectiveGeometry.interfaceOrientation ?? .portrait
        guard current != orientation else { return }
        orientation = current
        // start again from the new grip; `smooth` eases back to 0 by itself
        hasBaseline = false
    }

    // MARK: Helpers

    /// A vector in device axes (+x right, +y up, as CoreMotion and a portrait
    /// device see it) turned into screen axes (+x right, +y down) for the
    /// current interface orientation.
    nonisolated static func screenVector(_ v: SIMD2<Double>, _ o: UIInterfaceOrientation) -> SIMD2<Double> {
        switch o {
        case .portraitUpsideDown: return SIMD2<Double>(-v.x, v.y)
        case .landscapeLeft: return SIMD2<Double>(v.y, v.x)
        case .landscapeRight: return SIMD2<Double>(-v.y, -v.x)
        default: return SIMD2<Double>(v.x, -v.y)
        }
    }

    /// A 0.01 dead zone, then clamped to -1...1.
    nonisolated static func shaped(_ v: Double) -> Double {
        if abs(v) < 0.01 { return 0 }
        return min(1, max(-1, v))
    }

    nonisolated static func unit(_ v: CGFloat) -> CGFloat {
        min(1, max(-1, v))
    }

    // MARK: Observers

    private func installObservers() {
        let center = NotificationCenter.default
        let refreshing: [Notification.Name] = [
            UIAccessibility.reduceMotionStatusDidChangeNotification,
            UIAccessibility.voiceOverStatusDidChangeNotification,
            Notification.Name.NSProcessInfoPowerStateDidChange,
            ProcessInfo.thermalStateDidChangeNotification,
            UIAccessibility.reduceTransparencyStatusDidChangeNotification,
            UIAccessibility.darkerSystemColorsStatusDidChangeNotification,
            UserDefaults.didChangeNotification,
        ]
        for name in refreshing {
            observe(center, name) { $0.refresh() }
        }
        observe(center, UIApplication.didBecomeActiveNotification) { $0.appActive = true; $0.refresh() }
        observe(center, UIApplication.willResignActiveNotification) { $0.appActive = false; $0.refresh() }
        observe(center, UIApplication.didEnterBackgroundNotification) { $0.appActive = false; $0.refresh() }
        observe(center, UIResponder.keyboardWillShowNotification) { $0.keyboardUp = true; $0.refresh() }
        observe(center, UIResponder.keyboardWillHideNotification) { $0.keyboardUp = false; $0.refresh() }
    }

    private func observe(_ center: NotificationCenter, _ name: Notification.Name,
                         _ act: @escaping @MainActor (PopOutMotion) -> Void) {
        let token = center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                act(self)
            }
        }
        observers.append(token)
    }
}
