import Foundation

// What a nested map (Tree, Fractal) draws through and looks from, shared by
// the themes (S11). Foundation only; Android's NestDraw.kt is the same.

/// Where the scene draws: paths in the view's units, a fill or a stroke ends each one.
protocol NestSink: AnyObject {
    func moveTo(_ x: Float, _ y: Float)
    func lineTo(_ x: Float, _ y: Float)
    func cubicTo(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float, _ y: Float)
    func close()
    /// Fills the path so far with `color` (RGB) at `alpha`, and starts a new one.
    func fill(_ color: Int, _ alpha: Float)
    /// Strokes it, with round ends and joins when `round`, and starts a new one.
    func stroke(_ color: Int, _ width: Float, _ alpha: Float, _ round: Bool)
    /// One name: `node`'s title at `x`, `y` (its middle line); `align` 0 from the left, 1 to the right, 2 centred.
    func text(_ node: Int, _ x: Float, _ y: Float, _ size: Float, _ bold: Bool, _ align: Int, _ ink: Int, _ halo: Int, _ haloWidth: Float)
}

/// A camera: the tree's point at the view's middle, its zoom (view units per tree unit) and its turn.
final class NestCamera {
    var x: Double, y: Double, z: Double, r: Double
    init(x: Double = 0, y: Double = 0, z: Double = 1, r: Double = 0) { self.x = x; self.y = y; self.z = z; self.r = r }
    func set(_ o: NestCamera) { x = o.x; y = o.y; z = o.z; r = o.r }
}

/// The links between nodes, by index.
struct NestLinks {
    let a: [Int]
    let b: [Int]
    var size: Int { a.count }
    static let none = NestLinks(a: [], b: [])
}
