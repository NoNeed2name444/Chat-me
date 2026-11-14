import Foundation
// CGRect's x/y/width/height initialiser and its minX/maxX are CoreGraphics' Swift overlay: the
// app gets it through SwiftUI, but the macOS-only backup suite builds this file with Foundation
// alone. Linux's Foundation has them itself.
#if canImport(CoreGraphics)
import CoreGraphics
#endif

// MARK: - Lines: Curved / Straight
//
// One choice for both Ideas modes: how a link between two ideas is drawn.
// Curved (the standard, today's look) keeps each 3D theme's own shape -
// the Space's arches and black-hole curls, the Neurons' axons - and bows
// the 2D board's connectors gently. Straight
// draws every link as one direct segment between its two trimmed ends, in
// every theme and on the board.
//
// Set in Settings > Look and feel and in the Look menu of the 3D map and the
// board; both read the same stored Bool (SpaceSettings.straightLinesKey).
//
// Foundation only: tested on Linux (Tests/LineStyleTests.swift).

/// How links are drawn in the Ideas map and on the board.
nonisolated enum GraphLineStyle: String, CaseIterable, Sendable, Identifiable {
    case curved
    case straight

    var id: String { rawValue }

    /// Where it is stored: a Bool, true for Straight.
    static let key: String = "vignette.ideas.straightLines"
    /// Until the owner chooses: Curved, today's look.
    static let standard: GraphLineStyle = .curved

    var title: String {
        switch self {
        case .curved: return "Curved"
        case .straight: return "Straight"
        }
    }

    var isStraight: Bool { self == .straight }

    /// The stored Bool read back (nothing stored: the standard).
    static func stored(_ straight: Bool?) -> GraphLineStyle {
        guard let straight else { return standard }
        return straight ? .straight : .curved
    }

    /// `-ideasLines straight` (or `curved`) at launch: that style for this
    /// run whatever is stored, for the design preview's pictures (the
    /// owner's stored choice is not read). Nil without it.
    static let launchOverride: GraphLineStyle? = parse(arguments: ProcessInfo.processInfo.arguments)

    static func parse(arguments: [String]) -> GraphLineStyle? {
        guard let at = arguments.firstIndex(of: "-ideasLines"), at + 1 < arguments.count else { return nil }
        return GraphLineStyle(rawValue: arguments[at + 1])
    }

    /// The style in force, given the stored Bool (as a view holds it).
    static func inForce(_ stored: Bool) -> GraphLineStyle {
        if let forced = launchOverride { return forced }
        return stored ? .straight : .curved
    }

    /// The choice in force now, read from the defaults.
    static var current: GraphLineStyle {
        if let forced = launchOverride { return forced }
        let raw: Any? = UserDefaults.standard.object(forKey: key)
        return stored(raw as? Bool)
    }
}

/// The choice as the render thread reads it, every frame, without touching
/// UserDefaults: cached here and refreshed whenever the defaults change (a
/// flip of the setting redraws the links on the next frame they are
/// written, with no rebuild of the scene).
nonisolated final class GraphLineStyleLive: @unchecked Sendable {
    static let shared = GraphLineStyleLive()

    private let lock = NSLock()
    private var straight: Bool
    private var token: NSObjectProtocol?

    private init() {
        straight = GraphLineStyle.current.isStraight
        let centre: NotificationCenter = NotificationCenter.default
        token = centre.addObserver(forName: UserDefaults.didChangeNotification, object: nil,
                                   queue: nil) { [weak self] _ in
            self?.reload()
        }
    }

    /// Whether links are drawn straight now.
    var isStraight: Bool {
        lock.lock()
        defer { lock.unlock() }
        return straight
    }

    /// Reads the stored choice again.
    func reload() {
        let now: Bool = GraphLineStyle.current.isStraight
        lock.lock()
        straight = now
        lock.unlock()
    }
}

// MARK: - The 3D map's straight segment

/// A link drawn Straight in the 3D map: `count + 1` points evenly spaced on
/// the direct segment from its trimmed start towards its trimmed end, as far
/// as it has grown - the same thing GraphLinkCurve.sample gives the ribbon
/// writer for a curve, so the length coordinate (and the impulses, packets
/// and flow that run on it) follows the straight path unchanged.
nonisolated enum GraphStraightLine {
    /// Fills `points` and `lengths` (reused; grown as needed, never shrunk)
    /// and returns the drawn length. `grow` (0 to 1) is how far along the
    /// segment the link has grown from its start.
    @discardableResult
    static func sample(from start: SIMD3<Float>, to end: SIMD3<Float>, grow: Float, count: Int,
                       points: inout [SIMD3<Float>], lengths: inout [Float]) -> Float {
        let n: Int = max(count, 1)
        if points.count < n + 1 {
            points = [SIMD3<Float>](repeating: SIMD3<Float>(0, 0, 0), count: n + 1)
        }
        if lengths.count < n + 1 {
            lengths = [Float](repeating: 0, count: n + 1)
        }
        let grown: Float = min(max(grow, 0), 1)
        let run: SIMD3<Float> = (end - start) * grown
        let total: Float = size(run)
        let step: Float = 1 / Float(n)
        for k in 0...n {
            let f: Float = Float(k) * step
            points[k] = start + run * f
            lengths[k] = total * f
        }
        // the last point exactly on the end, free of rounding
        points[n] = start + run
        lengths[n] = total
        return total
    }

    static func size(_ v: SIMD3<Float>) -> Float {
        let squared: Float = v.x * v.x + v.y * v.y + v.z * v.z
        return squared.squareRoot()
    }
}

// MARK: - The 2D board's connectors

/// A connector's shape on the idea board, between two card centres `p` and
/// `q` (always passed in the same order for a pair, so every bow leans the
/// same way). Curved: a quadratic bow to the left of p-to-q, its control
/// `bow` of the length off the middle - symmetric end to end; several links
/// between one pair (lanes) fan out, each bowed a different amount, so they
/// never lie on one another. Straight: the direct segment, lanes set side
/// by side `laneGap` apart.
///
/// Drawing, any arrowhead (endDirection) and any hit test (distance) all go
/// through `point`, so they follow the shape chosen.
nonisolated enum IdeaLinkShape {
    /// Today's gentle bow: the control this much of the length off the
    /// middle (the curve's middle, half as far).
    static let bow: Double = 0.18
    /// How much more (or less) each further lane bows.
    static let fanStep: Double = 0.14
    /// Points between side-by-side straight lanes.
    static let laneGap: Double = 10

    /// Lane `lane` of `lanes`, centred on 0: -0.5 and 0.5 for two.
    static func laneOffset(_ lane: Int, of lanes: Int) -> Double {
        let count: Int = max(lanes, 1)
        let middle: Double = Double(count - 1) / 2
        return Double(lane) - middle
    }

    /// How much a curved lane bows (a fraction of the length).
    static func bowFactor(lane: Int, of lanes: Int) -> Double {
        let shift: Double = laneOffset(lane, of: lanes)
        return bow + fanStep * shift
    }

    /// The quadratic's control point for a curved link.
    static func control(from p: CGPoint, to q: CGPoint, lane: Int = 0, lanes: Int = 1) -> CGPoint {
        let factor: Double = bowFactor(lane: lane, of: lanes)
        let dx: Double = Double(q.x - p.x)
        let dy: Double = Double(q.y - p.y)
        let midX: Double = Double(p.x + q.x) / 2
        let midY: Double = Double(p.y + q.y) / 2
        let x: Double = midX - dy * factor
        let y: Double = midY + dx * factor
        return CGPoint(x: x, y: y)
    }

    /// A straight link's two ends, moved sideways for its lane.
    static func straightEnds(from p: CGPoint, to q: CGPoint, lane: Int = 0,
                             lanes: Int = 1) -> (CGPoint, CGPoint) {
        let shift: Double = laneOffset(lane, of: lanes) * laneGap
        let dx: Double = Double(q.x - p.x)
        let dy: Double = Double(q.y - p.y)
        let length: Double = (dx * dx + dy * dy).squareRoot()
        guard length > 0.0001, shift != 0 else { return (p, q) }
        let sideX: Double = -dy / length * shift
        let sideY: Double = dx / length * shift
        let a = CGPoint(x: Double(p.x) + sideX, y: Double(p.y) + sideY)
        let b = CGPoint(x: Double(q.x) + sideX, y: Double(q.y) + sideY)
        return (a, b)
    }

    /// The point `t` of the way along (0 to 1).
    static func point(_ style: GraphLineStyle, from p: CGPoint, to q: CGPoint, lane: Int = 0,
                      lanes: Int = 1, t: Double) -> CGPoint {
        if style.isStraight {
            let ends: (CGPoint, CGPoint) = straightEnds(from: p, to: q, lane: lane, lanes: lanes)
            return lerp(ends.0, ends.1, t)
        }
        let k: CGPoint = control(from: p, to: q, lane: lane, lanes: lanes)
        let s: Double = 1 - t
        let w0: Double = s * s
        let w1: Double = 2 * s * t
        let w2: Double = t * t
        let x: Double = Double(p.x) * w0 + Double(k.x) * w1 + Double(q.x) * w2
        let y: Double = Double(p.y) * w0 + Double(k.y) * w1 + Double(q.y) * w2
        return CGPoint(x: x, y: y)
    }

    /// `count + 1` points along it, end to end.
    static func samples(_ style: GraphLineStyle, from p: CGPoint, to q: CGPoint, lane: Int = 0,
                        lanes: Int = 1, count: Int = 24) -> [CGPoint] {
        let n: Int = max(count, 1)
        var result: [CGPoint] = []
        result.reserveCapacity(n + 1)
        for k in 0...n {
            let t: Double = Double(k) / Double(n)
            result.append(point(style, from: p, to: q, lane: lane, lanes: lanes, t: t))
        }
        return result
    }

    /// The way it runs into `q`, unit length (for an arrowhead there).
    static func endDirection(_ style: GraphLineStyle, from p: CGPoint, to q: CGPoint, lane: Int = 0,
                             lanes: Int = 1) -> CGPoint {
        let before: CGPoint = point(style, from: p, to: q, lane: lane, lanes: lanes, t: 0.98)
        let end: CGPoint = point(style, from: p, to: q, lane: lane, lanes: lanes, t: 1)
        return unit(CGPoint(x: end.x - before.x, y: end.y - before.y))
    }

    /// How far `spot` is from the link as drawn (for a hit test).
    static func distance(from spot: CGPoint, to style: GraphLineStyle, from p: CGPoint, to q: CGPoint,
                         lane: Int = 0, lanes: Int = 1) -> Double {
        let line: [CGPoint] = samples(style, from: p, to: q, lane: lane, lanes: lanes, count: style.isStraight ? 1 : 32)
        var best: Double = Double.greatestFiniteMagnitude
        for k in 1..<line.count {
            let d: Double = segmentDistance(spot, line[k - 1], line[k])
            best = min(best, d)
        }
        return best
    }

    /// Each link's lane among the links joining the same two cards: `keys`
    /// names each link's pair (in the order the links come); the result is
    /// (its lane, how many share the pair).
    static func lanes(for keys: [String]) -> [(lane: Int, lanes: Int)] {
        var counts: [String: Int] = [:]
        for key in keys { counts[key, default: 0] += 1 }
        var seen: [String: Int] = [:]
        var result: [(lane: Int, lanes: Int)] = []
        result.reserveCapacity(keys.count)
        for key in keys {
            let lane: Int = seen[key, default: 0]
            seen[key] = lane + 1
            result.append((lane: lane, lanes: counts[key] ?? 1))
        }
        return result
    }

    // MARK: helpers

    static func lerp(_ a: CGPoint, _ b: CGPoint, _ t: Double) -> CGPoint {
        let x: Double = Double(a.x) + Double(b.x - a.x) * t
        let y: Double = Double(a.y) + Double(b.y - a.y) * t
        return CGPoint(x: x, y: y)
    }

    static func unit(_ v: CGPoint) -> CGPoint {
        let x: Double = Double(v.x)
        let y: Double = Double(v.y)
        let length: Double = (x * x + y * y).squareRoot()
        guard length > 0.000001 else { return CGPoint(x: 1, y: 0) }
        return CGPoint(x: x / length, y: y / length)
    }

    /// The distance from `c` to the segment a-b.
    static func segmentDistance(_ c: CGPoint, _ a: CGPoint, _ b: CGPoint) -> Double {
        let abx: Double = Double(b.x - a.x)
        let aby: Double = Double(b.y - a.y)
        let acx: Double = Double(c.x - a.x)
        let acy: Double = Double(c.y - a.y)
        let span: Double = abx * abx + aby * aby
        var f: Double = 0
        if span > 0.0000001 { f = min(max((acx * abx + acy * aby) / span, 0), 1) }
        let dx: Double = acx - abx * f
        let dy: Double = acy - aby * f
        return (dx * dx + dy * dy).squareRoot()
    }
}

/// The board's opening view and Recentre (IdeaBoardView): the
/// zoom and pan that put every card on screen, clear of the edges, the tool
/// circles and the bottom glass. Never zoomed in past 1, nor out past the
/// board's own floor. The board draws a card's centre at
/// (width / 2 + pan.x + x * zoom, midY + pan.y + y * zoom), midY being the
/// middle of the part not under glass.
nonisolated enum IdeaBoardFit {
    struct Camera: Equatable {
        var zoom: Double
        var panX: Double
        var panY: Double
    }

    /// Room kept clear at each edge of the part not under glass, in points.
    struct Margins: Equatable {
        var top: Double
        var leading: Double
        var bottom: Double
        var trailing: Double
    }

    /// `sizes`, when given, are the cards' own sizes in `centres`' order (a
    /// card past its end is `card`): at the largest text sizes a card is far
    /// taller than `card`, and the box is fitted to the cards as drawn (S05c).
    static func camera(centres: [CGPoint], card: CGSize, view: CGSize, hidden: Double,
                       margins: Margins, zoomRange: ClosedRange<Double> = 0.3...1,
                       sizes: [CGSize] = []) -> Camera {
        guard !centres.isEmpty else { return Camera(zoom: 1, panX: 0, panY: 0) }
        // the cards' outer edges
        var minX = Double.infinity, maxX = -Double.infinity, minY = Double.infinity, maxY = -Double.infinity
        for (k, c) in centres.enumerated() {
            let s: CGSize = k < sizes.count ? sizes[k] : card
            minX = min(minX, Double(c.x) - Double(s.width) / 2); maxX = max(maxX, Double(c.x) + Double(s.width) / 2)
            minY = min(minY, Double(c.y) - Double(s.height) / 2); maxY = max(maxY, Double(c.y) + Double(s.height) / 2)
        }
        let width: Double = Double(view.width)
        let seen: Double = max(Double(view.height) - hidden, 0)
        let roomW: Double = max(width - margins.leading - margins.trailing, 1)
        let roomH: Double = max(seen - margins.top - margins.bottom, 1)
        let spanW: Double = maxX - minX
        let spanH: Double = maxY - minY
        let fit: Double = min(roomW / spanW, roomH / spanH)
        let zoom: Double = min(max(fit, zoomRange.lowerBound), zoomRange.upperBound)
        // the box's middle to the middle of the room
        let panX: Double = margins.leading + roomW / 2 - width / 2 - (minX + maxX) / 2 * zoom
        let panY: Double = margins.top + roomH / 2 - seen / 2 - (minY + maxY) / 2 * zoom
        return Camera(zoom: zoom, panX: panX, panY: panY)
    }

    /// The first view's (and Recentre's) zoom range: never past 1, and never
    /// so far out that a card is shorter than a touch target (`target`: 44
    /// pt, 48 dp), so every card opens big enough to tap (S05b task 5; at
    /// 0.3 a phone's cards were about 45 by 26). A pinch still reaches the
    /// board's floor, 0.3. A board too wide for the room at this zoom opens
    /// on its middle.
    static func firstRange(cardHeight: Double, target: Double) -> ClosedRange<Double> {
        min(max(target / cardHeight, 0.3), 1)...1
    }

    /// The board's tools at its trailing edge (S05m): the cluster's own
    /// box, gutter included; in the bottom-trailing corner of the part not
    /// under glass, or `centred` down that part in a broad window.
    struct Tools: Equatable {
        var width: Double
        var height: Double
        var centred: Bool
    }

    /// The rectangle `tools` take in the board's own view, in points.
    static func toolsRect(view: CGSize, hidden: Double, tools: Tools) -> CGRect {
        let seen: Double = max(Double(view.height) - hidden, 0)
        let top: Double = tools.centred ? seen / 2 - tools.height / 2 : seen - tools.height
        // origin and size, not x/y/width/height: that initialiser is CoreGraphics' overlay, which
        // the macOS-only backup suite doesn't import (it builds this file with Foundation alone)
        return CGRect(origin: CGPoint(x: Double(view.width) - tools.width, y: top),
                      size: CGSize(width: tools.width, height: tools.height))
    }

    /// The first view and Recentre (S05m item 1): every card on screen, 16
    /// in from each edge and below the top strip (`top`), the box as large
    /// as fits, never under the tools; the zoom within `firstRange` of the
    /// shortest card.
    static func firstView(centres: [CGPoint], sizes: [CGSize], card: CGSize, view: CGSize, hidden: Double,
                          top: Double, tools: Tools, target: Double) -> Camera {
        let shortest: Double = sizes.map { Double($0.height) }.min() ?? Double(card.height)
        return camera(centres: centres, card: card, view: view, hidden: hidden,
                      margins: Margins(top: 16 + top, leading: 16, bottom: 16, trailing: 16),
                      zoomRange: firstRange(cardHeight: shortest, target: target), sizes: sizes, tools: tools)
    }

    /// `camera`'s fit to the whole room, kept if no card is under `tools`;
    /// else zoomed out in 2 % steps, the box tried centred, at the leading
    /// edge, at the top and at the top-leading corner, until no card is
    /// under them; and at the latest the room beside the tools (or above
    /// them, in the corner, if that is larger).
    static func camera(centres: [CGPoint], card: CGSize, view: CGSize, hidden: Double, margins: Margins,
                       zoomRange: ClosedRange<Double>, sizes: [CGSize], tools: Tools) -> Camera {
        let full = camera(centres: centres, card: card, view: view, hidden: hidden, margins: margins,
                          zoomRange: zoomRange, sizes: sizes)
        guard !centres.isEmpty else { return full }
        let rect = toolsRect(view: view, hidden: hidden, tools: tools)
        var minX = Double.infinity, maxX = -Double.infinity, minY = Double.infinity, maxY = -Double.infinity
        for (k, c) in centres.enumerated() {
            let s: CGSize = k < sizes.count ? sizes[k] : card
            minX = min(minX, Double(c.x) - Double(s.width) / 2); maxX = max(maxX, Double(c.x) + Double(s.width) / 2)
            minY = min(minY, Double(c.y) - Double(s.height) / 2); maxY = max(maxY, Double(c.y) + Double(s.height) / 2)
        }
        let width: Double = Double(view.width)
        let seen: Double = max(Double(view.height) - hidden, 0)
        // the tools' edges as Doubles, and the overlap in named parts: the
        // macOS-only backup suite builds this with Foundation alone, where one
        // four-part comparison is too slow to type-check
        let toolsMinX: Double = Double(rect.minX), toolsMaxX: Double = Double(rect.maxX)
        let toolsMinY: Double = Double(rect.minY), toolsMaxY: Double = Double(rect.maxY)
        func clear(_ cam: Camera) -> Bool {
            for (k, c) in centres.enumerated() {
                let s: CGSize = k < sizes.count ? sizes[k] : card
                let x: Double = width / 2 + cam.panX + Double(c.x) * cam.zoom
                let y: Double = seen / 2 + cam.panY + Double(c.y) * cam.zoom
                let hw: Double = Double(s.width) * cam.zoom / 2, hh: Double = Double(s.height) * cam.zoom / 2
                let acrossTools: Bool = x + hw > toolsMinX && x - hw < toolsMaxX
                let besideTools: Bool = y + hh > toolsMinY && y - hh < toolsMaxY
                if acrossTools && besideTools { return false }
            }
            return true
        }
        if clear(full) { return full }
        var besideMargins = margins
        besideMargins.trailing = max(margins.trailing, tools.width)
        let side = camera(centres: centres, card: card, view: view, hidden: hidden, margins: besideMargins,
                          zoomRange: zoomRange, sizes: sizes)
        var fallback = side
        if !tools.centred {
            var aboveMargins = margins
            aboveMargins.bottom = max(margins.bottom, tools.height)
            let above = camera(centres: centres, card: card, view: view, hidden: hidden, margins: aboveMargins,
                               zoomRange: zoomRange, sizes: sizes)
            if above.zoom > side.zoom { fallback = above }
        }
        let left: Double = margins.leading, right: Double = width - margins.trailing
        let roomTop: Double = margins.top, roomBottom: Double = seen - margins.bottom
        for step in 0...60 {
            let z: Double = full.zoom * (1 - 0.02 * Double(step))
            if step > 0 && (z <= fallback.zoom || z < zoomRange.lowerBound) { break }
            // the box at this zoom: centred in the room, or at its leading or top edge
            let centreX: Double = (left + right) / 2 - width / 2 - (minX + maxX) / 2 * z
            let leadX: Double = left - width / 2 - minX * z
            let centreY: Double = (roomTop + roomBottom) / 2 - seen / 2 - (minY + maxY) / 2 * z
            let topY: Double = roomTop - seen / 2 - minY * z
            for (px, py) in [(centreX, centreY), (leadX, centreY), (centreX, topY), (leadX, topY)] {
                let cam = Camera(zoom: z, panX: px, panY: py)
                if clear(cam) { return cam }
            }
        }
        return fallback
    }
}

/// The whole of a long title, shown over its card on the board (S05m item
/// 5): at least 240 wide (the card's width on screen if wider), never wider
/// than the board less 32; from the card's top-leading corner, kept 16
/// inside the board's room and clear of its tools. In points.
nonisolated enum IdeaBoardPeek {
    static func width(card: Double, view: Double) -> Double {
        max(min(max(card, 240), view - 32), 1)
    }

    /// The peek's top-leading corner, for a peek `width` by `height` over a
    /// card whose top-leading corner is `corner`.
    static func place(corner: CGPoint, width: Double, height: Double, view: CGSize, hidden: Double, top: Double,
                      tools: IdeaBoardFit.Tools) -> CGPoint {
        let seen: Double = max(Double(view.height) - hidden, 0)
        func clampX(_ v: Double) -> Double { max(16, min(v, Double(view.width) - 16 - width)) }
        func clampY(_ v: Double) -> Double { max(top + 16, min(v, seen - 16 - height)) }
        var px: Double = clampX(Double(corner.x)), py: Double = clampY(Double(corner.y))
        let r = IdeaBoardFit.toolsRect(view: view, hidden: hidden, tools: tools)
        // in parts, as in IdeaBoardFit.camera: quick to type-check with Foundation alone
        let rMinX: Double = Double(r.minX), rMaxX: Double = Double(r.maxX)
        let rMinY: Double = Double(r.minY), rMaxY: Double = Double(r.maxY)
        let acrossTools: Bool = px + width > rMinX && px < rMaxX
        let besideTools: Bool = py + height > rMinY && py < rMaxY
        let under: Bool = acrossTools && besideTools
        if under {
            // to the tools' leading side if it fits there, else above them
            if Double(r.minX) - width >= 16 { px = Double(r.minX) - width } else { py = max(top + 16, Double(r.minY) - height) }
        }
        return CGPoint(x: px, y: py)
    }
}

/// Where a card let go of on the board settles (S05b task 5): where it was
/// let go, unless it would cover part of another card; then the nearest
/// free place, looked for in rings `step` apart, 24 ways round each, with
/// `gap` kept between cards. Centres, in board points.
nonisolated enum IdeaBoardDrop {
    /// The 2D board's card, in board points: its width, and the height the
    /// board fits and drops by.
    static let card = CGSize(width: 150, height: 88)
    /// The tallest card at the default text size, which a new idea is
    /// spaced by.
    static let tallest = CGSize(width: 150, height: 128)
    /// The room kept between cards on the board, drawn and dropped (S05m
    /// item 3).
    static let gap: Double = 12
    /// A title's widest line on the board is this sample's width in the
    /// title's font: 20 letters, narrow and wide (S05m item 5).
    static let titleSample: String = "abcdefghijklmnopqrst"

    /// Two cards cover each other, or come closer than `gap`: their centres
    /// nearer than half their widths (heights) and the gap, across and down.
    static func covers(_ a: CGPoint, _ sa: CGSize, _ b: CGPoint, _ sb: CGSize, gap: Double) -> Bool {
        abs(Double(a.x - b.x)) < Double(sa.width + sb.width) / 2 + gap
            && abs(Double(a.y - b.y)) < Double(sa.height + sb.height) / 2 + gap
    }

    static func settle(_ p: CGPoint, card: CGSize, others: [CGPoint], gap: Double = IdeaBoardDrop.gap, step: Double = 8,
                       rings: Int = 200) -> CGPoint {
        let w: Double = Double(card.width) + gap
        let h: Double = Double(card.height) + gap
        func free(_ x: Double, _ y: Double) -> Bool {
            others.allSatisfy { abs(x - Double($0.x)) >= w || abs(y - Double($0.y)) >= h }
        }
        let x0: Double = Double(p.x), y0: Double = Double(p.y)
        if free(x0, y0) { return p }
        for ring in 1...max(rings, 1) {
            let r: Double = Double(ring) * step
            for k in 0..<24 {
                let a: Double = Double(k) * Double.pi / 12
                let x: Double = x0 + r * cos(a), y: Double = y0 + r * sin(a)
                if free(x, y) { return CGPoint(x: x, y: y) }
            }
        }
        return p
    }
}

/// Where the 2D board draws its cards (S05c, S05m item 2), on screen only:
/// the stored places stay as they are. Cards stored on one point are drawn
/// round it as a block whose middle is that point (rows of `ceil(sqrt(n))`
/// in the store's order, `gap` apart); a block that would cover one drawn
/// before it moves whole to the nearest free place in IdeaBoardDrop's rings.
/// Then each other card, in the store's order, keeps its place if it covers
/// no drawn card; one that covers a block's card is drawn on the side of
/// that block it is stored on, as near its place as is free; one that
/// covers only other cards goes to the nearest free place in the rings.
/// Each pair is kept apart by its own sizes and `gap`. The same input gives
/// the same places, the store's order breaking ties.
nonisolated enum IdeaBoardSpread {
    struct Card: Equatable {
        var id: UUID
        var centre: CGPoint
        var size: CGSize

        // by hand: with Foundation alone, as the Mac's backup suite builds
        // this file, CGPoint and CGSize are not Equatable
        static func == (a: Card, b: Card) -> Bool {
            a.id == b.id && a.centre.x == b.centre.x && a.centre.y == b.centre.y
                && a.size.width == b.size.width && a.size.height == b.size.height
        }
    }

    private struct Placed {
        var at: CGPoint
        var size: CGSize
        var block: Int
    }

    private struct Block {
        var point: CGPoint
        var minX: Double
        var maxX: Double
        var minY: Double
        var maxY: Double
    }

    private struct Key: Hashable {
        var x: Double
        var y: Double
    }

    /// The offsets from a shared point of `group`'s cards, in its order:
    /// rows centred on the point, the whole block's middle on it.
    private static func layout(_ group: [Card], gap: Double) -> [CGPoint] {
        let n: Int = group.count
        let cols: Int = Int(Double(n).squareRoot().rounded(.up))
        var rows: [[Card]] = []
        var start: Int = 0
        while start < n {
            rows.append(Array(group[start..<min(start + cols, n)]))
            start += cols
        }
        let heights: [Double] = rows.map { row in row.map { Double($0.size.height) }.max() ?? 0 }
        let total: Double = heights.reduce(0, +) + gap * Double(rows.count - 1)
        var out: [CGPoint] = []
        var y: Double = -total / 2
        for (r, row) in rows.enumerated() {
            let rowW: Double = row.map { Double($0.size.width) }.reduce(0, +) + gap * Double(row.count - 1)
            var x: Double = -rowW / 2
            for c in row {
                out.append(CGPoint(x: x + Double(c.size.width) / 2, y: y + heights[r] / 2))
                x += Double(c.size.width) + gap
            }
            y += heights[r] + gap
        }
        return out
    }

    static func places(_ cards: [Card], gap: Double = IdeaBoardDrop.gap, step: Double = 8,
                       rings: Int = 2000) -> [UUID: CGPoint] {
        guard !cards.isEmpty else { return [:] }
        // placed cards in cells the size of the largest card and the gap, so
        // a place is checked against the 3 by 3 cells round it
        let cellW: Double = (cards.map { Double($0.size.width) }.max() ?? 1) + gap
        let cellH: Double = (cards.map { Double($0.size.height) }.max() ?? 1) + gap
        struct Cell: Hashable { var i: Int; var j: Int }
        var grid: [Cell: [Placed]] = [:]
        var right: Double = -Double.infinity
        var result: [UUID: CGPoint] = [:]
        var blocks: [Block] = []
        func cell(_ p: CGPoint) -> Cell {
            Cell(i: Int((Double(p.x) / cellW).rounded(.down)), j: Int((Double(p.y) / cellH).rounded(.down)))
        }
        // the drawn cards this one would cover, at p
        func hits(_ p: CGPoint, _ size: CGSize) -> [Placed] {
            let c = cell(p)
            var out: [Placed] = []
            for di in -1...1 {
                for dj in -1...1 {
                    for o in grid[Cell(i: c.i + di, j: c.j + dj)] ?? []
                    where IdeaBoardDrop.covers(p, size, o.at, o.size, gap: gap) { out.append(o) }
                }
            }
            return out
        }
        func free(_ p: CGPoint, _ size: CGSize) -> Bool {
            let c = cell(p)
            for di in -1...1 {
                for dj in -1...1 {
                    for o in grid[Cell(i: c.i + di, j: c.j + dj)] ?? []
                    where IdeaBoardDrop.covers(p, size, o.at, o.size, gap: gap) { return false }
                }
            }
            return true
        }
        func put(_ card: Card, _ at: CGPoint, _ block: Int) {
            grid[cell(at), default: []].append(Placed(at: at, size: card.size, block: block))
            right = max(right, Double(at.x) + Double(card.size.width) / 2)
            result[card.id] = at
        }
        func ring(_ p: CGPoint, _ ring: Int, _ k: Int, from: Double = 0) -> CGPoint {
            let a: Double = from + Double(k) * Double.pi / 12
            let r: Double = Double(ring) * step
            return CGPoint(x: Double(p.x) + r * cos(a), y: Double(p.y) + r * sin(a))
        }

        // the shared points, in the store's order of each one's first card
        var order: [Key] = []
        var byPoint: [Key: [Card]] = [:]
        for card in cards {
            let key = Key(x: Double(card.centre.x), y: Double(card.centre.y))
            if byPoint[key] == nil { order.append(key) }
            byPoint[key, default: []].append(card)
        }
        for key in order {
            guard let group = byPoint[key], group.count >= 2 else { continue }
            let point = group[0].centre
            let offsets = layout(group, gap: gap)
            func at(_ k: Int, _ d: CGPoint) -> CGPoint {
                CGPoint(x: Double(point.x) + Double(d.x) + Double(offsets[k].x),
                        y: Double(point.y) + Double(d.y) + Double(offsets[k].y))
            }
            func fits(_ d: CGPoint) -> Bool { group.indices.allSatisfy { free(at($0, d), group[$0].size) } }
            let zero = CGPoint(x: 0, y: 0)
            var shift: CGPoint? = fits(zero) ? zero : nil
            var r: Int = 1
            while shift == nil && r <= max(rings, 1) {
                for k in 0..<24 {
                    let d = ring(zero, r, k)
                    if fits(d) { shift = d; break }
                }
                r += 1
            }
            // past the rings: beyond every placed card, at its own height
            let leftmost: Double = group.indices.map { Double(offsets[$0].x) - Double(group[$0].size.width) / 2 }.min() ?? 0
            let d: CGPoint = shift ?? CGPoint(x: right + gap - (Double(point.x) + leftmost), y: 0)
            var minX = Double.infinity, maxX = -Double.infinity, minY = Double.infinity, maxY = -Double.infinity
            for (k, card) in group.enumerated() {
                let q = at(k, d)
                put(card, q, blocks.count)
                minX = min(minX, Double(q.x) - Double(card.size.width) / 2)
                maxX = max(maxX, Double(q.x) + Double(card.size.width) / 2)
                minY = min(minY, Double(q.y) - Double(card.size.height) / 2)
                maxY = max(maxY, Double(q.y) + Double(card.size.height) / 2)
            }
            blocks.append(Block(point: CGPoint(x: Double(point.x) + Double(d.x), y: Double(point.y) + Double(d.y)),
                                minX: minX, maxX: maxX, minY: minY, maxY: maxY))
        }

        for card in cards {
            if (byPoint[Key(x: Double(card.centre.x), y: Double(card.centre.y))]?.count ?? 0) >= 2 { continue }
            let p = card.centre
            let hit = hits(p, card.size)
            if hit.isEmpty { put(card, p, -1); continue }
            // a block it covers: the nearest one's point, the store's order breaking a tie
            let covered: [Int] = Array(Set(hit.map(\.block).filter { $0 >= 0 })).sorted()
            var near: Int? = nil
            var nearest: Double = Double.infinity
            for b in covered {
                let q = blocks[b].point
                let dx: Double = Double(p.x - q.x), dy: Double = Double(p.y - q.y)
                let d2: Double = dx * dx + dy * dy
                if d2 < nearest { nearest = d2; near = b }
            }
            var found: CGPoint? = nil
            let w: Double = Double(card.size.width), h: Double = Double(card.size.height)
            if let nb = near {
                let b = blocks[nb]
                let ox: Double = Double(p.x - b.point.x), oy: Double = Double(p.y - b.point.y)
                let halfW: Double = max((b.maxX - b.minX) / 2, 1e-9), halfH: Double = max((b.maxY - b.minY) / 2, 1e-9)
                let down: Bool = abs(oy) / halfH >= abs(ox) / halfW
                let sign: Double = down ? (oy < 0 ? -1 : 1) : (ox < 0 ? -1 : 1)
                func past(_ q: CGPoint) -> Bool {
                    if down && sign > 0 { return Double(q.y) - h / 2 >= b.maxY + gap - 1e-9 }
                    if down { return Double(q.y) + h / 2 <= b.minY - gap + 1e-9 }
                    if sign > 0 { return Double(q.x) - w / 2 >= b.maxX + gap - 1e-9 }
                    return Double(q.x) + w / 2 <= b.minX - gap + 1e-9
                }
                // the rings, tried from that side's own direction, then 15 degrees each way
                let from: Double = down ? (sign > 0 ? Double.pi / 2 : -Double.pi / 2) : (sign > 0 ? 0 : Double.pi)
                let turns: [Int] = [0] + (1...11).flatMap { [$0, -$0] } + [12]
                var r: Int = 1
                while found == nil && r <= max(rings, 1) {
                    for k in turns {
                        let q = ring(p, r, k, from: from)
                        if past(q) && free(q, card.size) { found = q; break }
                    }
                    r += 1
                }
            } else {
                var r: Int = 1
                while found == nil && r <= max(rings, 1) {
                    for k in 0..<24 {
                        let q = ring(p, r, k)
                        if free(q, card.size) { found = q; break }
                    }
                    r += 1
                }
            }
            // past the rings: beyond every placed card, at its own height
            put(card, found ?? CGPoint(x: right + w / 2 + gap, y: Double(p.y)), -1)
        }
        return result
    }

    /// Where a card let go of at `p` settles among the drawn cards (`others`,
    /// each with its size): IdeaBoardDrop.settle's search, with each pair's
    /// own sizes.
    static func settle(_ p: CGPoint, size: CGSize, others: [(CGPoint, CGSize)], gap: Double = IdeaBoardDrop.gap,
                       step: Double = 8, rings: Int = 200) -> CGPoint {
        func free(_ q: CGPoint) -> Bool {
            !others.contains { IdeaBoardDrop.covers(q, size, $0.0, $0.1, gap: gap) }
        }
        if free(p) { return p }
        for ring in 1...max(rings, 1) {
            let r: Double = Double(ring) * step
            for k in 0..<24 {
                let a: Double = Double(k) * Double.pi / 12
                let q = CGPoint(x: Double(p.x) + r * cos(a), y: Double(p.y) + r * sin(a))
                if free(q) { return q }
            }
        }
        return p
    }
}
