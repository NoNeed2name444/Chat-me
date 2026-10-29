#if canImport(SwiftUI)
import SwiftUI

// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// Theme.swift, the capPop extension and CapPop); the rest of that file is
// the study app's and stays out.

extension View {
    /// A control's face sinks a little while pressed and springs back,
    /// held a moment so a quick tap still shows (WardPress).
    func capPop(_ pressed: Bool) -> some View {
        modifier(CapPop(pressed: pressed))
    }
}

private struct CapPop: ViewModifier {
    let pressed: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pressedAt: Date?

    func body(content: Content) -> some View {
        let releaseDelay = WardPress.releaseHold(held: pressedAt.map { Date().timeIntervalSince($0) } ?? WardPress.press)
        return content
            .scaleEffect(pressed && !reduceMotion ? WardPress.sinkScale : 1)
            .animation(reduceMotion ? nil : Animation.wardClick(down: pressed)
                .delay(pressed ? 0 : releaseDelay), value: pressed)
            .onChange(of: pressed) { _, down in
                if down {
                    pressedAt = Date()
                }
            }
    }
}
#endif
