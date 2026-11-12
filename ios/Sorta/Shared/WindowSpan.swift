import SwiftUI

// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// Theme.swift, lines 271-334 and 365-366): WindowSpan, the windowSpan
// environment value the Ward kit reads (WardControls, PopOut), and the
// modifier that measures it once at the root.

/// How wide the app's WINDOW is - not the screen.
///
/// On iPad the two are rarely the same. The app can be a third of the screen in
/// Split View, a narrow Slide Over panel over something else, or a free window
/// the person has dragged to any size at all, and iPadOS 26 lets all three
/// change under the app while it is running. A size class only says "compact"
/// or "regular" and crosses that line late, which is how a 640-point window
/// ends up trying to show two columns and a sidebar of its own.
///
/// So the layout asks for the number. `windowSpan` is measured once at the root
/// and handed down; everything that has to decide between one column and two,
/// or how wide a line of text may run, reads it.
enum WindowSpan: Comparable {
    /// Slide Over, or a phone: one thing at a time, and nothing held back.
    case slim
    /// A half-and-half Split View: still one column, but text needs a measure.
    case middling
    /// Most of an iPad: a list can stay beside what is open.
    case broad

    init(width: CGFloat) {
        // 700 is where two columns stop being a squeeze: the sidebar's own
        // minimum is 300, and what is left has to hold a question at a
        // readable size rather than four words to the line.
        switch width {
        case ..<540: self = .slim
        case ..<700: self = .middling
        default: self = .broad
        }
    }

    /// Whether a list and what is open from it can share the window.
    var splits: Bool { self == .broad }
}

private struct WindowSpanKey: EnvironmentKey {
    static let defaultValue = WindowSpan.slim
}

extension EnvironmentValues {
    var windowSpan: WindowSpan {
        get { self[WindowSpanKey.self] }
        set { self[WindowSpanKey.self] = newValue }
    }
}

/// Measures the window the app is actually in and publishes it downwards.
struct MeasuringWindow: ViewModifier {
    @State private var span: WindowSpan = .slim

    func body(content: Content) -> some View {
        content
            .environment(\.windowSpan, span)
            .background {
                // A background rather than a GeometryReader around the content:
                // a reader would offer its children the whole space and flatten
                // the layout inside it.
                Color.clear
                    .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { w in
                        let now = WindowSpan(width: w)
                        if now != span { span = now }
                    }
            }
    }
}


extension View {
    /// Put this once, at the root of a screen that cares how big its window is.
    func measuringWindow() -> some View { modifier(MeasuringWindow()) }
}
