#if canImport(SwiftUI)
import SwiftUI

// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// Ward/WardVitals.swift); the rest of that file is study-only and stays out.

/// A ring `width` wide just inside the frame's circle: a shape with a hole,
/// the two circles drawn in opposite directions, so a fill (and the lights a
/// groove is drawn with) covers the ring alone.
struct WardAnnulus: InsettableShape {
    var width: CGFloat
    var insetAmount: CGFloat = 0

    func path(in rect: CGRect) -> Path {
        let centre = CGPoint(x: rect.midX, y: rect.midY)
        let outer: CGFloat = min(rect.width, rect.height) / 2 - insetAmount
        let hole: CGFloat = min(rect.width, rect.height) / 2 - width + insetAmount
        var p = Path()
        guard outer > 0 else { return p }
        p.addArc(center: centre, radius: outer, startAngle: .degrees(0), endAngle: .degrees(360), clockwise: false)
        p.closeSubpath()
        if hole > 0 && hole < outer {
            p.move(to: CGPoint(x: centre.x + hole, y: centre.y))
            p.addArc(center: centre, radius: hole, startAngle: .degrees(360), endAngle: .degrees(0), clockwise: true)
            p.closeSubpath()
        }
        return p
    }

    func inset(by amount: CGFloat) -> WardAnnulus {
        var ring = self
        ring.insetAmount += amount
        return ring
    }
}
#endif
