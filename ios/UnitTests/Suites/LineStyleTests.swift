// Lines: Curved / Straight (GraphLineStyle.swift): the stored choice, the
// 3D map's straight segment - collinear, evenly spaced, exact at both ends,
// its length coordinate continuous as the link grows - and the 2D board's
// connectors - the bow symmetric and leaning one way, parallel links and
// fanned lanes never on top of one another, the arrowhead's direction and
// the hit test following the shape chosen.
//
// Compiled with GraphLineStyle.swift alone (Foundation only).
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "PASS " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

func near(_ a: Float, _ b: Float, _ tolerance: Float = 0.0001) -> Bool {
    abs(a - b) <= tolerance
}

func nearD(_ a: Double, _ b: Double, _ tolerance: Double = 0.0001) -> Bool {
    abs(a - b) <= tolerance
}

func size3(_ v: SIMD3<Float>) -> Float {
    GraphStraightLine.size(v)
}

func gap(_ a: CGPoint, _ b: CGPoint) -> Double {
    let dx: Double = Double(a.x - b.x)
    let dy: Double = Double(a.y - b.y)
    return (dx * dx + dy * dy).squareRoot()
}

// MARK: L1 the stored choice

check("L1 nothing stored: Curved, today's look", GraphLineStyle.stored(nil) == .curved && GraphLineStyle.standard == .curved)
check("L1 true is Straight, false Curved", GraphLineStyle.stored(true) == .straight && GraphLineStyle.stored(false) == .curved)
check("L1 the key", GraphLineStyle.key == "vignette.ideas.straightLines")
check("L1 the words", GraphLineStyle.allCases.map(\.title) == ["Curved", "Straight"])
check("L1 -ideasLines straight forces it for a run", GraphLineStyle.parse(arguments: ["app", "-ideasLines", "straight"]) == .straight
      && GraphLineStyle.parse(arguments: ["app", "-ideasLines", "curved"]) == .curved)
check("L1 no flag, or a bad one: the stored choice", GraphLineStyle.parse(arguments: ["app"]) == nil
      && GraphLineStyle.parse(arguments: ["app", "-ideasLines"]) == nil
      && GraphLineStyle.parse(arguments: ["app", "-ideasLines", "wiggly"]) == nil)
check("L1 in force: the stored Bool without the flag", GraphLineStyle.inForce(true) == .straight
      && GraphLineStyle.inForce(false) == .curved)

// MARK: L2 the 3D map's straight segment

let start = SIMD3<Float>(1, -2, 0.5)
let end = SIMD3<Float>(7, 3, -4)
var points: [SIMD3<Float>] = []
var lengths: [Float] = []
let n: Int = 40
let total: Float = GraphStraightLine.sample(from: start, to: end, grow: 1, count: n, points: &points, lengths: &lengths)
let whole: Float = size3(end - start)
check("L2 as many points as the curve's sampling", points.count == n + 1 && lengths.count == n + 1)
check("L2 the ends match exactly", points[0] == start && points[n] == end, "\(points[0]) \(points[n])")
check("L2 the length is the segment's", near(total, whole, 0.0005) && near(lengths[n], total, 0.0))

var worstOff: Float = 0
let axis: SIMD3<Float> = (end - start) / whole
for p in points {
    let rel: SIMD3<Float> = p - start
    let along: Float = rel.x * axis.x + rel.y * axis.y + rel.z * axis.z
    let off: Float = size3(rel - axis * along)
    worstOff = max(worstOff, off)
}
check("L2 every sample on the one line (collinear)", worstOff < 0.0005, "\(worstOff)")

var worstSpacing: Float = 0
let step: Float = whole / Float(n)
for k in 1...n {
    let piece: Float = size3(points[k] - points[k - 1])
    worstSpacing = max(worstSpacing, abs(piece - step))
}
check("L2 evenly spaced", worstSpacing < 0.0005, "\(worstSpacing)")

var coordinateOK: Bool = lengths[0] == 0
for k in 1...n {
    let measured: Float = size3(points[k] - points[0])
    if !near(lengths[k], measured, 0.0005) || lengths[k] < lengths[k - 1] { coordinateOK = false }
}
check("L2 the length coordinate is the true distance along, rising from 0", coordinateOK)

// a growing link: drawn from its start, the coordinate continuous with the
// whole link's (the shader's flow and impulses do not jump as it grows)
var grownPoints: [SIMD3<Float>] = []
var grownLengths: [Float] = []
let half: Float = GraphStraightLine.sample(from: start, to: end, grow: 0.5, count: n, points: &grownPoints, lengths: &grownLengths)
check("L2 half grown: half the length", near(half, whole * 0.5, 0.0005))
let middle: SIMD3<Float> = start + (end - start) * 0.5
check("L2 half grown: runs from the start to the middle", grownPoints[0] == start && size3(grownPoints[n] - middle) < 0.0005)
var sameLine: Bool = true
for k in 0...n {
    // the half-grown link's point at distance d is the whole link's point at d
    let d: Float = grownLengths[k]
    let onWhole: SIMD3<Float> = start + axis * d
    if size3(grownPoints[k] - onWhole) > 0.0005 { sameLine = false }
}
check("L2 growing keeps every point where the grown link will have it", sameLine)

var continuous: Bool = true
var previousTotal: Float = 0
for g in 1...20 {
    var pts: [SIMD3<Float>] = []
    var lens: [Float] = []
    let grow: Float = Float(g) / 20
    let t: Float = GraphStraightLine.sample(from: start, to: end, grow: grow, count: n, points: &pts, lengths: &lens)
    if t < previousTotal || abs(t - previousTotal) > whole / 20 + 0.001 { continuous = false }
    previousTotal = t
}
check("L2 the length grows smoothly with the link", continuous && near(previousTotal, whole, 0.0005))

var reused: [SIMD3<Float>] = [SIMD3<Float>](repeating: SIMD3<Float>(9, 9, 9), count: 64)
var reusedLengths: [Float] = [Float](repeating: 9, count: 64)
_ = GraphStraightLine.sample(from: start, to: end, grow: 1, count: 20, points: &reused, lengths: &reusedLengths)
check("L2 buffers are reused, never shrunk", reused.count == 64 && reused[20] == end && reusedLengths[0] == 0)

var zeroPts: [SIMD3<Float>] = []
var zeroLens: [Float] = []
let none: Float = GraphStraightLine.sample(from: start, to: start, grow: 1, count: 8, points: &zeroPts, lengths: &zeroLens)
check("L2 a zero-length link is a point, no NaN", none == 0 && zeroPts.allSatisfy { $0 == start })
let clamped: Float = GraphStraightLine.sample(from: start, to: end, grow: 3, count: 8, points: &zeroPts, lengths: &zeroLens)
check("L2 grow is clamped to the whole link", near(clamped, whole, 0.0005) && zeroPts[8] == end)

// MARK: L3 the board's curve

let p = CGPoint(x: 100, y: 200)
let q = CGPoint(x: 400, y: 260)
let pq: Double = gap(p, q)
let curved: [CGPoint] = IdeaLinkShape.samples(.curved, from: p, to: q, count: 40)
check("L3 the curve's ends are the card centres", gap(curved[0], p) < 1e-9 && gap(curved[40], q) < 1e-9)

var symmetric: Bool = true
let mx: Double = Double(p.x + q.x) / 2
let my: Double = Double(p.y + q.y) / 2
let ux: Double = Double(q.x - p.x) / pq
let uy: Double = Double(q.y - p.y) / pq
for k in 0...40 {
    // mirror across the perpendicular bisector: along -> -along
    let a: CGPoint = curved[k]
    let b: CGPoint = curved[40 - k]
    let alongA: Double = (Double(a.x) - mx) * ux + (Double(a.y) - my) * uy
    let alongB: Double = (Double(b.x) - mx) * ux + (Double(b.y) - my) * uy
    let acrossA: Double = -(Double(a.x) - mx) * uy + (Double(a.y) - my) * ux
    let acrossB: Double = -(Double(b.x) - mx) * uy + (Double(b.y) - my) * ux
    if !nearD(alongA, -alongB, 1e-6) || !nearD(acrossA, acrossB, 1e-6) { symmetric = false }
}
check("L3 the bow is symmetric end to end", symmetric)

let top: CGPoint = IdeaLinkShape.point(.curved, from: p, to: q, t: 0.5)
let across: Double = -(Double(top.x) - mx) * uy + (Double(top.y) - my) * ux
check("L3 a gentle bow: its middle a ninth of the length off the line (today's)",
      nearD(across, pq * IdeaLinkShape.bow / 2, 1e-6), "\(across)")
check("L3 it leans to the left of p-to-q, always", across > 0)

// the same bow however long, and never back across the line
let far = CGPoint(x: 100, y: 900)
let bent: CGPoint = IdeaLinkShape.point(.curved, from: p, to: far, t: 0.5)
// travelling down the screen, the same side of travel is towards smaller x
check("L3 a vertical link leans the same way, relative to its travel", Double(bent.x) < Double(p.x))

// MARK: L4 parallel links don't overlap

let p2 = CGPoint(x: 100, y: 230)
let q2 = CGPoint(x: 400, y: 290)
let neighbour: [CGPoint] = IdeaLinkShape.samples(.curved, from: p2, to: q2, count: 40)
var closest: Double = Double.greatestFiniteMagnitude
for a in curved {
    closest = min(closest, IdeaLinkShape.distance(from: a, to: .curved, from: p2, to: q2))
}
_ = neighbour
let offset: Double = 30 * ux // how far apart the two lines are, square to them
check("L4 two parallel links 30 apart stay apart all along", closest > offset * 0.66, "\(closest) vs \(offset)")

let keys: [String] = ["ab", "cd", "ab", "ab"]
let lanes: [(lane: Int, lanes: Int)] = IdeaLinkShape.lanes(for: keys)
check("L4 lanes per pair", lanes[0] == (0, 3) && lanes[1] == (0, 1) && lanes[2] == (1, 3) && lanes[3] == (2, 3))
check("L4 one link: today's bow", nearD(IdeaLinkShape.bowFactor(lane: 0, of: 1), IdeaLinkShape.bow))

var fanApart: Bool = true
var midpoints: [CGPoint] = []
for lane in 0..<3 {
    midpoints.append(IdeaLinkShape.point(.curved, from: p, to: q, lane: lane, lanes: 3, t: 0.5))
}
for a in 0..<3 {
    for b in (a + 1)..<3 where gap(midpoints[a], midpoints[b]) < 12 { fanApart = false }
}
check("L4 three links between one pair fan out, their middles well apart", fanApart,
      midpoints.map { "\($0)" }.joined(separator: " "))

var fanTouch: Bool = false
for lane in 0..<2 {
    let mine: [CGPoint] = IdeaLinkShape.samples(.curved, from: p, to: q, lane: lane, lanes: 2, count: 40)
    for k in 4...36 {
        let d: Double = IdeaLinkShape.distance(from: mine[k], to: .curved, from: p, to: q, lane: 1 - lane, lanes: 2)
        if d < 2 { fanTouch = true }
    }
}
check("L4 fanned curves meet only at the cards", !fanTouch)

let s0: (CGPoint, CGPoint) = IdeaLinkShape.straightEnds(from: p, to: q, lane: 0, lanes: 2)
let s1: (CGPoint, CGPoint) = IdeaLinkShape.straightEnds(from: p, to: q, lane: 1, lanes: 2)
let apart: Double = IdeaLinkShape.segmentDistance(s0.0, s1.0, s1.1)
check("L4 straight lanes side by side, a lane's gap apart", nearD(apart, IdeaLinkShape.laneGap, 1e-6), "\(apart)")
let single: (CGPoint, CGPoint) = IdeaLinkShape.straightEnds(from: p, to: q)
check("L4 one straight link: exactly centre to centre", gap(single.0, p) < 1e-9 && gap(single.1, q) < 1e-9)

// MARK: L5 straight on the board, and what follows the shape

let straight: [CGPoint] = IdeaLinkShape.samples(.straight, from: p, to: q, count: 10)
var straightOK: Bool = true
for k in 0...10 {
    let want: CGPoint = IdeaLinkShape.lerp(p, q, Double(k) / 10)
    if gap(straight[k], want) > 1e-9 { straightOK = false }
}
check("L5 straight: evenly along the segment", straightOK)

let straightIn: CGPoint = IdeaLinkShape.endDirection(.straight, from: p, to: q)
check("L5 an arrowhead on a straight link points along it",
      nearD(Double(straightIn.x), ux, 1e-6) && nearD(Double(straightIn.y), uy, 1e-6))
let curvedIn: CGPoint = IdeaLinkShape.endDirection(.curved, from: p, to: q)
let control: CGPoint = IdeaLinkShape.control(from: p, to: q)
let tangent: CGPoint = IdeaLinkShape.unit(CGPoint(x: q.x - control.x, y: q.y - control.y))
check("L5 an arrowhead on a curved link follows the curve in",
      gap(curvedIn, tangent) < 0.02 && gap(curvedIn, straightIn) > 0.1, "\(curvedIn) \(tangent)")

let onCurve: CGPoint = IdeaLinkShape.point(.curved, from: p, to: q, t: 0.5)
let onLine: CGPoint = IdeaLinkShape.lerp(p, q, 0.5)
check("L5 a hit test finds the curve where it is drawn",
      IdeaLinkShape.distance(from: onCurve, to: .curved, from: p, to: q) < 0.5
      && IdeaLinkShape.distance(from: onLine, to: .curved, from: p, to: q) > 10)
check("L5 and the straight line where it is drawn",
      IdeaLinkShape.distance(from: onLine, to: .straight, from: p, to: q) < 1e-9
      && IdeaLinkShape.distance(from: onCurve, to: .straight, from: p, to: q) > 10)

// MARK: L6 the board's first view fits every card

/// Every card, drawn as the board draws it, inside the room the margins leave.
func allInside(_ centres: [CGPoint], _ cam: IdeaBoardFit.Camera, card: CGSize, view: CGSize,
               hidden: Double, _ m: IdeaBoardFit.Margins) -> Bool {
    let midY: Double = (Double(view.height) - hidden) / 2
    for c in centres {
        let x: Double = Double(view.width) / 2 + cam.panX + Double(c.x) * cam.zoom
        let y: Double = midY + cam.panY + Double(c.y) * cam.zoom
        let halfW: Double = Double(card.width) * cam.zoom / 2
        let halfH: Double = Double(card.height) * cam.zoom / 2
        if x - halfW < m.leading - 1e-6 || x + halfW > Double(view.width) - m.trailing + 1e-6 { return false }
        if y - halfH < m.top - 1e-6 || y + halfH > Double(view.height) - hidden - m.bottom + 1e-6 { return false }
    }
    return true
}

let card = CGSize(width: 150, height: 88)
let room = IdeaBoardFit.Margins(top: 16, leading: 16, bottom: 16, trailing: 76)
let spread: [CGPoint] = [CGPoint(x: -330, y: -120), CGPoint(x: 360, y: 40),
                         CGPoint(x: 10, y: 300), CGPoint(x: -40, y: -260)]
let phone = CGSize(width: 402, height: 760)
let wide: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: spread, card: card, view: phone,
                                                    hidden: 220, margins: room)
check("L6 a wide board on a phone: zoomed out until every card is in, clear of the tools",
      wide.zoom < 1 && allInside(spread, wide, card: card, view: phone, hidden: 220, room), "\(wide)")

let pad = CGSize(width: 960, height: 1100)
let few: [CGPoint] = [CGPoint(x: 80, y: 40), CGPoint(x: 240, y: 120)]
let fitNear: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: few, card: card, view: pad,
                                                    hidden: 180, margins: room)
check("L6 a few cards on an iPad: never zoomed in past 1, centred in the room",
      fitNear.zoom == 1 && allInside(few, fitNear, card: card, view: pad, hidden: 180, room)
      && nearD(Double(pad.width) / 2 + fitNear.panX + 160, 16 + (960 - 92) / 2), "\(fitNear)")

let fitNone: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: [], card: card, view: phone,
                                                    hidden: 220, margins: room)
check("L6 an empty board: the middle at zoom 1", fitNone == IdeaBoardFit.Camera(zoom: 1, panX: 0, panY: 0))

let farApart: [CGPoint] = [CGPoint(x: -5000, y: 0), CGPoint(x: 5000, y: 0)]
let fitFloor: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: farApart, card: card, view: phone,
                                                     hidden: 220, margins: room)
check("L6 a vast board stops at the board's own floor, centred",
      fitFloor.zoom == 0.3 && nearD(fitFloor.panX, 16 + (402 - 92) / 2 - 201), "\(fitFloor)")

// at the largest text sizes a card is far taller than the least card: the
// fit takes each card's own size, so the tall one at the foot is in too (S05c)
func insideSized(_ points: [CGPoint], _ sizes: [CGSize], _ cam: IdeaBoardFit.Camera, view: CGSize,
                 hidden: Double, _ m: IdeaBoardFit.Margins) -> Bool {
    let midY: Double = (Double(view.height) - hidden) / 2
    for (p, s) in zip(points, sizes) {
        let x: Double = Double(view.width) / 2 + cam.panX + Double(p.x) * cam.zoom
        let y: Double = midY + cam.panY + Double(p.y) * cam.zoom
        let halfW: Double = Double(s.width) * cam.zoom / 2
        let halfH: Double = Double(s.height) * cam.zoom / 2
        if x - halfW < m.leading - 1e-6 || x + halfW > Double(view.width) - m.trailing + 1e-6 { return false }
        if y - halfH < m.top - 1e-6 || y + halfH > Double(view.height) - hidden - m.bottom + 1e-6 { return false }
    }
    return true
}
let tallAt: [CGPoint] = [CGPoint(x: 0, y: -200), CGPoint(x: 0, y: 0), CGPoint(x: 0, y: 300)]
let tallSizes: [CGSize] = [card, card, CGSize(width: 150, height: 400)]
let fitTall: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: tallAt, card: card, view: phone,
                                                    hidden: 220, margins: room, sizes: tallSizes)
let fitLeast: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: tallAt, card: card, view: phone,
                                                     hidden: 220, margins: room)
check("L6 a tall card at the foot is fitted by its own size, clear of the foot",
      insideSized(tallAt, tallSizes, fitTall, view: phone, hidden: 220, room)
      && !insideSized(tallAt, tallSizes, fitLeast, view: phone, hidden: 220, room)
      && IdeaBoardFit.camera(centres: spread, card: card, view: phone, hidden: 220, margins: room,
                             sizes: spread.map { _ in card }) == wide, "\(fitTall)")

// S05m item 1: the first view on four windows and the two panes, on the
// stacked fixture's spread and on four cards apart. The box fills at least
// 85 % of the room (16 in from each edge, below the strip) along the side
// that binds, or the zoom is 1; no card is under the tools; every card is in
// the room and at least a target tall; never zoomed in past 1.
struct FitWindow {
    var name: String
    var view: CGSize
    var hidden: Double
    var top: Double
    var tools: IdeaBoardFit.Tools
    var target: Double
}
let fitWindows: [FitWindow] = [
    FitWindow(name: "Android phone", view: CGSize(width: 411, height: 914), hidden: 82, top: 56,
              tools: IdeaBoardFit.Tools(width: 80, height: 208, centred: false), target: 48),
    FitWindow(name: "Android tablet", view: CGSize(width: 1280, height: 800), hidden: 0, top: 56,
              tools: IdeaBoardFit.Tools(width: 80, height: 208, centred: true), target: 48),
    FitWindow(name: "Android tablet's map pane", view: CGSize(width: 768, height: 800), hidden: 0, top: 56,
              tools: IdeaBoardFit.Tools(width: 80, height: 208, centred: true), target: 48),
    FitWindow(name: "iPhone", view: CGSize(width: 402, height: 874), hidden: 94, top: 60,
              tools: IdeaBoardFit.Tools(width: 76, height: 196, centred: false), target: 44),
    FitWindow(name: "iPad", view: CGSize(width: 1032, height: 1376), hidden: 20, top: 60,
              tools: IdeaBoardFit.Tools(width: 76, height: 196, centred: false), target: 44),
    FitWindow(name: "iPad's map pane", view: CGSize(width: 619, height: 1300), hidden: 20, top: 60,
              tools: IdeaBoardFit.Tools(width: 76, height: 196, centred: false), target: 44),
]
func fitCard(_ n: Int, _ x: Double, _ y: Double, w: Double = 150) -> IdeaBoardSpread.Card {
    IdeaBoardSpread.Card(id: UUID(uuidString: String(format: "00000000-0000-0000-0001-%012d", n))!,
                         centre: CGPoint(x: x, y: y), size: CGSize(width: w, height: 88))
}
// the stacked fixture as both platforms store it (MadeUpExport stacked, IdeasFixture.stacked)
let fitStacked: [IdeaBoardSpread.Card] = [fitCard(1, 120, -40, w: 180), fitCard(2, 0, 0, w: 180), fitCard(3, -75.5, 210.25, w: 180),
                                          fitCard(4, 0, 33, w: 180), fitCard(5, 0, 0, w: 180), fitCard(6, 0, 0, w: 180),
                                          fitCard(7, 0, -230, w: 180)]
let fitFour: [IdeaBoardSpread.Card] = [fitCard(1, -330, -120), fitCard(2, 360, 40), fitCard(3, 10, 300), fitCard(4, -40, -260)]
let fitLeastCard = CGSize(width: 150, height: 88)
for w in fitWindows {
    for (name, cards) in [("stacked", fitStacked), ("four apart", fitFour)] {
        let at = IdeaBoardSpread.places(cards)
        let centres: [CGPoint] = cards.map { at[$0.id]! }
        let sizes: [CGSize] = cards.map(\.size)
        let cam = IdeaBoardFit.firstView(centres: centres, sizes: sizes, card: fitLeastCard, view: w.view, hidden: w.hidden,
                                         top: w.top, tools: w.tools, target: w.target)
        let seen: Double = Double(w.view.height) - w.hidden
        let roomW: Double = Double(w.view.width) - 32, roomH: Double = seen - w.top - 32
        let lefts = cards.indices.map { Double(centres[$0].x) - Double(sizes[$0].width) / 2 }
        let rights = cards.indices.map { Double(centres[$0].x) + Double(sizes[$0].width) / 2 }
        let tops = cards.indices.map { Double(centres[$0].y) - Double(sizes[$0].height) / 2 }
        let bottoms = cards.indices.map { Double(centres[$0].y) + Double(sizes[$0].height) / 2 }
        let spanW: Double = rights.max()! - lefts.min()!, spanH: Double = bottoms.max()! - tops.min()!
        let fill: Double = max(spanW * cam.zoom / roomW, spanH * cam.zoom / roomH)
        let tools = IdeaBoardFit.toolsRect(view: w.view, hidden: w.hidden, tools: w.tools)
        var under = false, outside = false
        for k in cards.indices {
            let x: Double = Double(w.view.width) / 2 + cam.panX + Double(centres[k].x) * cam.zoom
            let y: Double = seen / 2 + cam.panY + Double(centres[k].y) * cam.zoom
            let hw: Double = Double(sizes[k].width) * cam.zoom / 2, hh: Double = Double(sizes[k].height) * cam.zoom / 2
            if x + hw > Double(tools.minX) && x - hw < Double(tools.maxX) && y + hh > Double(tools.minY) && y - hh < Double(tools.maxY) { under = true }
            if x - hw < 16 - 1e-6 || x + hw > Double(w.view.width) - 16 + 1e-6 || y - hh < 16 + w.top - 1e-6 || y + hh > seen - 16 + 1e-6 { outside = true }
        }
        let label = "\(w.name), \(name): zoom \(cam.zoom), fill \(fill)"
        // a board too wide for the room at the floor opens on its middle, as before (L7)
        let shortest: Double = sizes.map { Double($0.height) }.min()!
        let tooWide: Bool = fill > 1 + 1e-9
            && cam.zoom <= IdeaBoardFit.firstRange(cardHeight: shortest, target: w.target).lowerBound + 1e-9
        check("L6 the box fills at least 85 % of the room, or the zoom is 1: \(label)", fill >= 0.85 - 1e-9 || cam.zoom >= 1 - 1e-9)
        check("L6 no card under the tools: \(label)", tooWide || !under)
        check("L6 every card in the room: \(label)", tooWide || !outside)
        check("L6 never past 1, every card a target tall: \(label)", cam.zoom <= 1 + 1e-9 && shortest * cam.zoom >= w.target - 1e-6)
    }
}
// the old trailing 76 down the whole height held the phone's box to 84 % across
let fitPhone = fitWindows[0]
let fitStackedAt = IdeaBoardSpread.places(fitStacked)
let fitOld = IdeaBoardFit.camera(centres: fitStacked.map { fitStackedAt[$0.id]! }, card: fitLeastCard, view: fitPhone.view,
                                 hidden: fitPhone.hidden, margins: IdeaBoardFit.Margins(top: 16 + fitPhone.top, leading: 16, bottom: 16, trailing: 76),
                                 zoomRange: IdeaBoardFit.firstRange(cardHeight: 88, target: 48), sizes: fitStacked.map(\.size))
let fitNow = IdeaBoardFit.firstView(centres: fitStacked.map { fitStackedAt[$0.id]! }, sizes: fitStacked.map(\.size), card: fitLeastCard,
                                    view: fitPhone.view, hidden: fitPhone.hidden, top: fitPhone.top, tools: fitPhone.tools,
                                    target: fitPhone.target)
check("L6 the phone opens larger than with the old margins", fitNow.zoom > fitOld.zoom, "\(fitNow.zoom) > \(fitOld.zoom)")
// a board with a card where the tools are: it is moved or zoomed clear of them
let fitCorner: [CGPoint] = [CGPoint(x: -150, y: -300), CGPoint(x: 150, y: 300)]
let fitCornerCam = IdeaBoardFit.firstView(centres: fitCorner, sizes: [fitLeastCard, fitLeastCard], card: fitLeastCard, view: fitPhone.view,
                                          hidden: fitPhone.hidden, top: fitPhone.top, tools: fitPhone.tools, target: fitPhone.target)
let fitR = IdeaBoardFit.toolsRect(view: fitPhone.view, hidden: fitPhone.hidden, tools: fitPhone.tools)
let fitBX: Double = Double(fitPhone.view.width) / 2 + fitCornerCam.panX + 150 * fitCornerCam.zoom
let fitBY: Double = (Double(fitPhone.view.height) - fitPhone.hidden) / 2 + fitCornerCam.panY + 300 * fitCornerCam.zoom
check("L6 a card at the bottom-trailing corner is drawn clear of the tools",
      !(fitBX + 75 * fitCornerCam.zoom > Double(fitR.minX) && fitBY + 44 * fitCornerCam.zoom > Double(fitR.minY)), "\(fitCornerCam)")

// MARK: L7 the first view keeps every card big enough to tap

let tapRange: ClosedRange<Double> = IdeaBoardFit.firstRange(cardHeight: 88, target: 44)
let tapFit: IdeaBoardFit.Camera = IdeaBoardFit.camera(centres: farApart, card: card, view: phone,
                                                    hidden: 220, margins: room, zoomRange: tapRange)
check("L7 a vast board opens with its cards 44 points tall, on its middle",
      tapRange == 0.5...1 && nearD(tapFit.zoom * 88, 44) && nearD(tapFit.panX, 16 + (402 - 92) / 2 - 201), "\(tapFit)")
check("L7 it never asks for more than 1, nor less than the board's floor",
      IdeaBoardFit.firstRange(cardHeight: 20, target: 44) == 1...1
      && IdeaBoardFit.firstRange(cardHeight: 400, target: 44) == 0.3...1)

// MARK: L8 a card let go of on another settles beside it

let others: [CGPoint] = [CGPoint(x: 0, y: 0), CGPoint(x: 0, y: 96)]
let clear = CGPoint(x: 400, y: 400)
check("L8 let go in a clear place, it stays", IdeaBoardDrop.settle(clear, card: card, others: others) == clear)
let onTop = CGPoint(x: 10, y: 4)
let settled: CGPoint = IdeaBoardDrop.settle(onTop, card: card, others: others)
check("L8 the board keeps 12 between cards (S05m)", IdeaBoardDrop.gap == 12)
func overlaps(_ a: CGPoint, _ b: CGPoint) -> Bool {
    abs(Double(a.x - b.x)) < 150 + IdeaBoardDrop.gap - 1e-6 && abs(Double(a.y - b.y)) < 88 + IdeaBoardDrop.gap - 1e-6
}
let moved: Double = hypot(Double(settled.x - onTop.x), Double(settled.y - onTop.y))
check("L8 let go on a card, it moves clear of every card, no further than a card's width and a gap",
      !others.contains { overlaps(settled, $0) } && moved > 0 && moved <= 150 + IdeaBoardDrop.gap + 8, "\(settled)")

// MARK: L9 the board draws no card over another (S05c)

func spBoardCard(_ n: Int, _ x: Double, _ y: Double, h: Double = 88) -> IdeaBoardSpread.Card {
    IdeaBoardSpread.Card(id: UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", n))!,
                         centre: CGPoint(x: x, y: y), size: CGSize(width: 150, height: h))
}
func spNoneCover(_ cards: [IdeaBoardSpread.Card], _ at: [UUID: CGPoint]) -> Bool {
    for (i, a) in cards.enumerated() {
        for b in cards[(i + 1)...] {
            guard let p = at[a.id], let q = at[b.id] else { return false }
            if IdeaBoardDrop.covers(p, a.size, q, b.size, gap: 12 - 1e-6) { return false }
        }
    }
    return true
}
let spApart: [IdeaBoardSpread.Card] = [spBoardCard(1, 0, 0), spBoardCard(2, 200, 0), spBoardCard(3, 0, 120)]
let spApartAt = IdeaBoardSpread.places(spApart)
check("L9 cards that cover none stay where they are stored",
      spApart.allSatisfy { spApartAt[$0.id] == $0.centre }, "\(spApartAt)")
let spPair: [IdeaBoardSpread.Card] = [spBoardCard(1, 10, 10), spBoardCard(2, 10, 10)]
let spPairAt = IdeaBoardSpread.places(spPair)
/// The middle of the box round `cards` as drawn at `at`.
func spMiddle(_ cards: [IdeaBoardSpread.Card], _ at: [UUID: CGPoint]) -> CGPoint {
    let minX: Double = cards.map { Double(at[$0.id]!.x) - Double($0.size.width) / 2 }.min()!
    let maxX: Double = cards.map { Double(at[$0.id]!.x) + Double($0.size.width) / 2 }.max()!
    let minY: Double = cards.map { Double(at[$0.id]!.y) - Double($0.size.height) / 2 }.min()!
    let maxY: Double = cards.map { Double(at[$0.id]!.y) + Double($0.size.height) / 2 }.max()!
    return CGPoint(x: (minX + maxX) / 2, y: (minY + maxY) / 2)
}
check("L9 two on one place are drawn round it, the first moved with them",
      spNoneCover(spPair, spPairAt) && spPairAt[spPair[0].id] != spPair[0].centre
      && gap(spMiddle(spPair, spPairAt), CGPoint(x: 10, y: 10)) <= 1, "\(spPairAt)")
// the second is drawn away from the first; the third covers the second's
// stored place but neither drawn card, so it keeps its own
let spClear: [IdeaBoardSpread.Card] = [spBoardCard(1, 0, 0), spBoardCard(2, 10, 0), spBoardCard(3, 165, -50)]
let spClearAt: [UUID: CGPoint] = IdeaBoardSpread.places(spClear)
check("L9 a card stored clear of every drawn card keeps its place, though it covers another's stored place",
      spClearAt[spClear[2].id] == spClear[2].centre && spClearAt[spClear[1].id] != spClear[1].centre && spNoneCover(spClear, spClearAt),
      "\(spClearAt)")
let spStack: [IdeaBoardSpread.Card] = (1...5).map { spBoardCard($0, 0, 0) }
let spStackAt = IdeaBoardSpread.places(spStack)
check("L9 five at (0, 0) end apart round it, none covering another",
      spStackAt.count == 5 && spNoneCover(spStack, spStackAt) && gap(spMiddle(spStack, spStackAt), CGPoint(x: 0, y: 0)) <= 1, "\(spStackAt)")
// 96 down clears two 88-point cards, not a 200-point one
let spTall: [IdeaBoardSpread.Card] = [spBoardCard(1, 0, 0, h: 200), spBoardCard(2, 0, 96)]
let spTallAt = IdeaBoardSpread.places(spTall)
check("L9 a tall card and a short one are kept apart by their own sizes",
      spTallAt[spTall[0].id] == spTall[0].centre && spTallAt[spTall[1].id] != spTall[1].centre && spNoneCover(spTall, spTallAt), "\(spTallAt)")
check("L9 the same cards give the same places", IdeaBoardSpread.places(spStack) == spStackAt)
let spSwapped: [IdeaBoardSpread.Card] = [spPair[1], spPair[0]]
check("L9 the store's order decides who goes where", IdeaBoardSpread.places(spSwapped)[spPair[1].id] == spPairAt[spPair[0].id])
let spCrowd: [IdeaBoardSpread.Card] = (1...300).map { spBoardCard($0, 0, 0) }
let spCrowdAt = IdeaBoardSpread.places(spCrowd, rings: 40)
check("L9 a crowd on one point ends round it, none covering another",
      spCrowdAt.count == 300 && spNoneCover(spCrowd, spCrowdAt) && gap(spMiddle(spCrowd, spCrowdAt), CGPoint(x: 0, y: 0)) <= 1)
// a card let go of settles clear of the drawn cards, as the board does it
let spA = spBoardCard(1, 0, 0), spB = spBoardCard(2, 30, 0)
let spDrawn = IdeaBoardSpread.places([spA, spB])
let spDrop: CGPoint = IdeaBoardSpread.settle(CGPoint(x: 30, y: 0), size: CGSize(width: 150, height: 88),
                                             others: [spDrawn[spA.id]!, spDrawn[spB.id]!].map { ($0, CGSize(width: 150, height: 88)) })
var spC = spBoardCard(3, 0, 0); spC.centre = spDrop
let spAfter = IdeaBoardSpread.places([spA, spC, spB])
check("L9 a card let go of clear of the drawn cards is drawn where it settled, ahead of a moved one in the order",
      spAfter[spC.id] == spDrop && spNoneCover([spA, spC, spB], spAfter), "\(spAfter)")
check("L9 no cards, no places", IdeaBoardSpread.places([]).isEmpty)

// S05m item 2: cards on one point are drawn round it; a card stored off it on the side it is stored
let spThree: [IdeaBoardSpread.Card] = (1...3).map { spBoardCard($0, 0, 0) }
let spBell = spBoardCard(4, 0, 33)
let spWithBell = spThree + [spBell]
let spWithBellAt = IdeaBoardSpread.places(spWithBell)
check("L9 three on one point are drawn round it, the box's middle within 1",
      gap(spMiddle(spThree, spWithBellAt), CGPoint(x: 0, y: 0)) <= 1, "\(spMiddle(spThree, spWithBellAt))")
let spThreeBottom: Double = spThree.map { Double(spWithBellAt[$0.id]!.y) + 44 }.max()!
check("L9 the bell, stored 33 below, is drawn below them, a gap clear",
      Double(spWithBellAt[spBell.id]!.y) - 44 >= spThreeBottom + 12 - 1e-6 && spNoneCover(spWithBell, spWithBellAt), "\(spWithBellAt)")
let spAbove = IdeaBoardSpread.places(spThree + [spBoardCard(4, 10, -30)])
check("L9 a card stored above the point is drawn above the block",
      Double(spAbove[spBoardCard(4, 0, 0).id]!.y) + 44 <= spThree.map { Double(spAbove[$0.id]!.y) - 44 }.min()! - 12 + 1e-6)
let spLeft = IdeaBoardSpread.places(spThree + [spBoardCard(4, -120, 5)])
check("L9 a card stored left of it is drawn left of the block",
      Double(spLeft[spBoardCard(4, 0, 0).id]!.x) + 75 <= spThree.map { Double(spLeft[$0.id]!.x) - 75 }.min()! - 12 + 1e-6)
let spRight = IdeaBoardSpread.places(spThree + [spBoardCard(4, 120, -5)])
check("L9 a card stored right of it is drawn right of the block",
      Double(spRight[spBoardCard(4, 0, 0).id]!.x) - 75 >= spThree.map { Double(spRight[$0.id]!.x) + 75 }.max()! + 12 - 1e-6)
let spBellFirst = IdeaBoardSpread.places([spBell] + spThree)
check("L9 the bell is drawn below the block whatever its place in the order",
      Double(spBellFirst[spBell.id]!.y) - 44 >= spThree.map { Double(spBellFirst[$0.id]!.y) + 44 }.max()! + 12 - 1e-6)
let spLone = spBoardCard(5, 600, 0)
check("L9 a lone card clear of the block keeps its place", IdeaBoardSpread.places(spWithBell + [spLone])[spLone.id] == spLone.centre)
let spTwoPoints: [IdeaBoardSpread.Card] = (1...4).map { spBoardCard($0, 0, 0) } + (5...8).map { spBoardCard($0, 100, 0) }
let spTwoAt = IdeaBoardSpread.places(spTwoPoints)
check("L9 two blocks that would meet are drawn apart",
      spNoneCover(spTwoPoints, spTwoAt) && gap(spMiddle(Array(spTwoPoints.prefix(4)), spTwoAt), CGPoint(x: 0, y: 0)) <= 1, "\(spTwoAt)")
check("L9 the same input, the same places", IdeaBoardSpread.places(spWithBell) == spWithBellAt)

print(failures.isEmpty ? "all passed" : "\(failures.count) failed")
exit(failures.isEmpty ? 0 : 1)
