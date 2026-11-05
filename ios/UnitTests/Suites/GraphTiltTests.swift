// The tilted default view of the Neurons and Space maps (T0057,
// docs/design/depth-fade.md): the tilted fit against its closed form for a
// symmetric sheet and the face-on fit at 0, the least distance, every padded
// point inside; u(id) stable and order-free; the amplitude rule's choice;
// the discs' overlap; the band and shader text the tilt hands over to; and
// the first-run card's cover (S05d).
//
// Compiled with Features/Notes/GraphTilt.swift, GraphDepthFade.swift,
// GraphTheme.swift and Shared/Space/GraphicsQuality.swift (Foundation only).
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "PASS " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

func near(_ a: Float, _ b: Float, _ share: Float) -> Bool { abs(a - b) <= abs(b) * share }

// MARK: G1 - the fit

let centred = GraphWindow(aspect: 0.5, share: 1, across: 0, up: 0)
let fill: Float = 0.8
let t: Float = tan(27.5 * Float.pi / 180) * fill * 1
let h: Float = 3
var sheet: [SIMD3<Float>] = []
for y in stride(from: -h, through: h, by: h / 4) {
    for x: Float in [-0.2, 0, 0.2] { sheet.append(SIMD3<Float>(x, y, 0)) }
}
let noPads: [Float] = sheet.map { _ in 0 }
for degrees: Float in [35, 45] {
    let theta: Float = degrees * Float.pi / 180
    let fit: GraphTiltFit = GraphTilt.fit(points: sheet, pads: noPads, window: centred, fill: fill, tilt: theta)
    let d: Float = h * cos(theta) / t
    check("G1a \(degrees)°: D = h·cos θ / t", near(fit.distance, d, 0.01), "\(fit.distance) vs \(d)")
    check("G1b \(degrees)°: cy = -t·h·sin θ", near(fit.shift.y, -t * h * sin(theta), 0.01) && abs(fit.shift.x) < 1e-3,
          "\(fit.shift)")
    check("G1c \(degrees)°: the focus is D", near(fit.focus, d, 0.01))
    check("G1d \(degrees)°: the far edge at (1 + t·tan θ)·F", near(fit.deepest, (1 + t * tan(theta)) * fit.focus, 0.01),
          "\(fit.deepest)")
    check("G1e \(degrees)°: the reach is t·tan θ", near(fit.reach, t * tan(theta), 0.01), "\(fit.reach)")
    let shorter: Float = fit.distance * 0.99
    check("G1f \(degrees)°: D is the least (1% nearer, a point is outside)",
          GraphTilt.shift(points: sheet, pads: noPads, window: centred, fill: fill, tilt: theta, distance: shorter) == nil)
}
let flat: GraphTiltFit = GraphTilt.fit(points: sheet, pads: noPads, window: centred, fill: fill, tilt: 0)
check("G1g at 0 it is the face-on D = h/t", near(flat.distance, h / t, 0.01) && abs(flat.shift.y) < 1e-3,
      "\(flat.distance) vs \(h / t)")

// a cloud with pads, an off-centre window: every padded point inside, and
// the least distance
var seed: UInt64 = 0x9E37_79B9
func next() -> Float {
    seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
    return Float(seed >> 40) / Float(1 << 24)
}
var cloud: [SIMD3<Float>] = []
var pads: [Float] = []
for _ in 0..<60 {
    cloud.append(SIMD3<Float>(next() * 8 - 4, next() * 12 - 6, next() * 2 - 1))
    pads.append(0.1 + next() * 0.4)
}
let offCentre: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: GraphInsets(top: 118, bottom: 158))
for degrees: Float in [0, 35, 45, 75] {
    let theta: Float = degrees * Float.pi / 180
    let fit: GraphTiltFit = GraphTilt.fit(points: cloud, pads: pads, window: offCentre, fill: 0.88, tilt: theta)
    let tanUp: Float = GraphTilt.tanHalf * 0.88 * offCentre.share
    let tanSide: Float = tanUp * offCentre.aspect
    let wx: Float = offCentre.across * 2 * GraphTilt.tanHalf
    let wy: Float = offCentre.up * 2 * GraphTilt.tanHalf
    var inside = true
    var worst: Float = -1
    for (p, r) in zip(cloud, pads) {
        let q: SIMD3<Float> = GraphTilt.rotX(-theta, p)
        let depth: Float = fit.distance - q.z
        let ex: Float = abs(q.x - fit.shift.x - depth * wx) + r - (depth - r) * tanSide
        let ey: Float = abs(q.y - fit.shift.y - depth * wy) + r - (depth - r) * tanUp
        worst = max(worst, ex, ey)
        if ex > 1e-3 || ey > 1e-3 { inside = false }
    }
    check("G1h \(degrees)°: every padded point inside the window", inside, "\(worst)")
    check("G1i \(degrees)°: and 1% nearer, not",
          GraphTilt.shift(points: cloud, pads: pads, window: offCentre, fill: 0.88, tilt: theta,
                          distance: fit.distance * 0.99) == nil)
    let back: SIMD3<Float> = GraphTilt.offset(fit, tilt: theta)
    check("G1j \(degrees)°: the camera on the near side, above (south and up)",
          degrees == 0 ? abs(back.y - fit.shift.y) < 1e-4 : (back.z > 0 && back.y < 0), "\(back)")
}
check("G1k the themes' pitches: Neurons 35°, Space 45°, Performance 0",
      near(GraphTilt.angle(.neurons), 35 * Float.pi / 180, 1e-6) && near(GraphTilt.angle(.space), 45 * Float.pi / 180, 1e-6)
      && GraphTilt.angle(.performance) == 0 && GraphTilt.wideExtraDegrees == 0)
check("G1l a drag may pitch it within 10° to 75°", GraphTilt.lowestDegrees == 10 && GraphTilt.highestDegrees == 75)
let w: GraphWindow = GraphTilt.window(width: 400, height: 800, insets: GraphInsets(top: 100, left: 0, bottom: 200, right: 0))
check("G1m the window: what is not under glass", near(w.aspect, 400.0 / 500, 1e-6) && near(w.share, 500.0 / 800, 1e-6)
      && near(w.up, 50.0 / 800, 1e-6) && w.across == 0, "\(w)")

// MARK: G2 - u(id) and the amplitude

// pinned: FNV-1a 64 of the UTF-8, top 53 bits to [0, 1), then to [-1, 1)
let golden: [(String, Float)] = [("Cardiology", GraphTilt.u("Cardiology")), ("a", GraphTilt.u("a")), ("", GraphTilt.u(""))]
check("G2a golden values", near(golden[0].1, -0.1421105, 1e-5) && near(golden[1].1, 0.3702350, 1e-5)
      && near(golden[2].1, 0.5933415, 1e-5), "\(golden)")
var inRange = true
for k in 0..<500 where !(GraphTilt.u("id-\(k)") >= -1 && GraphTilt.u("id-\(k)") < 1) { inRange = false }
check("G2b u is in [-1, 1)", inRange)

func grid(_ n: Int, pitch: Float, radius: Float) -> [GraphTilt.Cluster] {
    let cols: Int = max(1, Int(Float(n).squareRoot().rounded(.up)))
    return (0..<n).map { k in
        let x: Float = (Float(k % cols) - Float(cols - 1) * 0.5) * pitch
        let y: Float = (Float(k / cols) - Float((n - 1) / cols) * 0.5) * pitch
        return (id: "cluster-\(k)", centre: SIMD2<Float>(x, y), radius: radius)
    }
}
for theme: GraphTheme in [.neurons, .space] {
    let clusters: [GraphTilt.Cluster] = grid(9, pitch: 4, radius: 1)
    let a: Float = GraphTilt.amplitude(clusters: clusters, theme: theme)
    let shuffled: [GraphTilt.Cluster] = clusters.reversed().shuffled()
    let b: Float = GraphTilt.amplitude(clusters: shuffled, theme: theme)
    var same = true
    var bounded = true
    for c in clusters {
        let z: Float = GraphTilt.z(c.id, amplitude: a)
        if z != GraphTilt.z(c.id, amplitude: b) { same = false }
        if abs(z) > a { bounded = false }
    }
    print("A \(theme.rawValue) 3x3 grid: \(a) (H \(GraphTilt.half(clusters)))")
    check("G2c \(theme.rawValue): shuffled, the same z per id", a == b && same)
    check("G2d \(theme.rawValue): |z| ≤ A", bounded)
    check("G2e \(theme.rawValue): A is one of the candidates times H",
          ([0] + GraphTilt.amplitudes).contains { near(a, $0 * GraphTilt.half(clusters), 1e-5) || (a == 0 && $0 == 0) })
}
// two clusters on top of each other: every candidate fails, so 0
let stacked: [GraphTilt.Cluster] = [(id: "one", centre: SIMD2<Float>(0, 0), radius: 1),
                                    (id: "two", centre: SIMD2<Float>(0.1, 0), radius: 1)]
check("G2f all candidates fail: 0", GraphTilt.amplitude(clusters: stacked, theme: .space) == 0
      && !GraphTilt.legible(clusters: stacked, tilt: GraphTilt.angle(.space), amplitude: 0))
// the choice itself: 0.35 failing and 0.25 passing picks 0.25; all failing, 0
check("G2g 0.35 fails, 0.25 passes: 0.25 is chosen", GraphTilt.choose(half: 4) { $0 < 1.2 } == 1)
check("G2i none passes: 0", GraphTilt.choose(half: 4) { _ in false } == 0
      && GraphTilt.choose(half: 4) { _ in true } == 1.4)
check("G2h Performance has no tilt, so no depth", GraphTilt.amplitude(clusters: grid(4, pitch: 4, radius: 1),
                                                                      theme: .performance) == 0)

// MARK: G3 - two discs' overlap

let pi: Float = Float.pi
check("G3a apart: none", GraphTilt.overlap(1, 1, apart: 2) == 0 && GraphTilt.overlap(1, 2, apart: 3.5) == 0)
check("G3b one inside the other: the smaller whole", near(GraphTilt.overlap(1, 3, apart: 0.5), pi, 1e-6)
      && near(GraphTilt.overlap(2, 2, apart: 0), 4 * pi, 1e-6))
check("G3c unit discs a radius apart: 2π/3 - √3/2", near(GraphTilt.overlap(1, 1, apart: 1), 2 * pi / 3 - Float(0.75).squareRoot(), 1e-4))
check("G3d unit discs √2 apart: π/2 - 1", near(GraphTilt.overlap(1, 1, apart: Float(2).squareRoot()), pi / 2 - 1, 1e-4))
check("G3e symmetric", near(GraphTilt.overlap(1, 1.5, apart: 1.2), GraphTilt.overlap(1.5, 1, apart: 1.2), 1e-6))

// MARK: G4 - what the tilt hands the depth fade

for theme: GraphTheme in [.neurons, .space] {
    for tier: GraphicsTier in [.high, .smooth] {
        let band: GraphDepthBand = GraphDepthBand.of(theme, tier: tier, contrast: false)!
        check("G4a \(theme.rawValue) \(tier): the crisp share is the band's a", band.a == GraphTilt.crispShare)
    }
}
let shader: String = GraphDepthShaderText.fragment(mode: .light, most: 1, k: 1)
check("G4b the shader text carries the marker, no pow, no matrix", shader.hasPrefix(GraphDepthShaderText.marker)
      && !shader.contains("pow") && !shader.contains("viewTransform") && shader.contains("rpDepthSpan"))

// MARK: G5 - camera controller settings for a tilt (T0062)

let zero = GraphTilt.cameraSettings(tilt: 0)
check("G5a θ = 0 gives worldUp (0,1,0) and limits 0 and 0",
      zero.worldUp == SIMD3<Float>(0, 1, 0)
      && zero.minimumVerticalAngle == 0
      && zero.maximumVerticalAngle == 0)

for theme: GraphTheme in [.neurons, .space] {
    let theta: Float = GraphTilt.angle(theme)
    let s = GraphTilt.cameraSettings(tilt: theta)
    check("G5b \(theme.rawValue) at θ gives worldUp (0,0,1) and limits 15 and 80",
          s.worldUp == SIMD3<Float>(0, 0, 1)
          && near(s.minimumVerticalAngle, 15, 1e-5)
          && near(s.maximumVerticalAngle, 80, 1e-5))
}

let neuronsSettings = GraphTilt.cameraSettings(tilt: GraphTilt.angle(.neurons))
check("G5c the limits keep every tilt between 10° and 75°",
      90 - neuronsSettings.maximumVerticalAngle == GraphTilt.lowestDegrees
      && 90 - neuronsSettings.minimumVerticalAngle == GraphTilt.highestDegrees
      && GraphTilt.lowestDegrees == 10
      && GraphTilt.highestDegrees == 75)

// MARK: G6 - the first-run card's cover (S05d)

// an iPhone upright: the strip's and the foot's glass, and a card whose top
// is 420 points above the view's foot
let glass = GraphInsets(top: 118, left: 4, bottom: 158, right: 6)
check("G6a no card: the edges as they are", glass.coveringFoot(0) == glass)
check("G6b a cover lower than the foot's glass changes nothing", glass.coveringFoot(120) == glass)
let carded: GraphInsets = glass.coveringFoot(420)
check("G6c a cover above it raises the foot alone",
      carded == GraphInsets(top: 118, left: 4, bottom: 420, right: 6), "\(carded)")
let wholeWindow: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: glass)
let cardWindow: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: carded)
check("G6d the window ends at the card's top",
      near(cardWindow.share, (852 - 118 - 420) / 852, 1e-5) && near(cardWindow.up, (420 - 118) / 2 / 852, 1e-5),
      "\(cardWindow)")
check("G6e no card: the window as before",
      GraphTilt.window(width: 393, height: 852, insets: glass.coveringFoot(0)) == wholeWindow)
// every padded point of a made-up map, framed in that window, is drawn
// between the strip and the card's top, face on and tilted
for degrees: Float in [0, 35, 45] {
    let theta: Float = degrees * Float.pi / 180
    let fit: GraphTiltFit = GraphTilt.fit(points: cloud, pads: pads, window: cardWindow, fill: 0.88, tilt: theta)
    var lowest: Float = 0
    var highest: Float = 852
    for (p, r) in zip(cloud, pads) {
        let q: SIMD3<Float> = GraphTilt.rotX(-theta, p)
        let front: Float = fit.distance - q.z - r
        // the padded point's lower and upper edges, in points from the view's top
        let low: Float = (1 - (q.y - fit.shift.y - r) / (front * GraphTilt.tanHalf)) / 2 * 852
        let high: Float = (1 - (q.y - fit.shift.y + r) / (front * GraphTilt.tanHalf)) / 2 * 852
        lowest = max(lowest, low)
        highest = min(highest, high)
    }
    check("G6f \(degrees)°: every point above the card's top", lowest <= 852 - 420, "lowest at \(lowest)")
    check("G6g \(degrees)°: and below the strip", highest >= 118, "highest at \(highest)")
}
check("G6h the cover comes or grows: a camera at its first view is framed again",
      GraphCardCover.refits(from: 0, to: 300) && GraphCardCover.refits(from: 300, to: 340))
check("G6i it shrinks, goes or stays: not",
      !GraphCardCover.refits(from: 340, to: 300) && !GraphCardCover.refits(from: 300, to: 0)
      && !GraphCardCover.refits(from: 300, to: 300) && !GraphCardCover.refits(from: 0, to: 0))

// MARK: G7 - the glass at the top, measured (S05d)

// The map's view ignores the safe area, so its reader's insets read 0 and
// the iPhone's first Neurons view was framed from the screen's top: its two
// top cells under the To sort and look chips (CI run 42071068784). The glass
// is measured instead: an iPhone upright, 393 x 852, the view 0...852, the
// map's place below the strip (the status bar's 59, 8 and a 44-point chip:
// 111) and above the switcher (754); the Neurons card's top at 415.4, its
// cover 852 - 415.4 + 22.
let measuredGlass: GraphInsets = GraphInsets.measured(viewTop: 0, viewBottom: 852, seenTop: 111, seenBottom: 754)
check("G7a the strip and the switcher, measured",
      measuredGlass == GraphInsets(top: 111, bottom: 98), "\(measuredGlass)")
check("G7b a place outside the view counts as no glass",
      GraphInsets.measured(viewTop: 20, viewBottom: 800, seenTop: 10, seenBottom: 810) == GraphInsets())
check("G7b2 the sides, measured: a landscape notch counts on both sides",
      GraphInsets.measured(viewTop: 0, viewBottom: 400, seenTop: 50, seenBottom: 380,
                           viewLeft: 0, viewRight: 900, seenLeft: 59, seenRight: 900)
        == GraphInsets(top: 50, left: 59, bottom: 20, right: 59),
      "\(GraphInsets.measured(viewTop: 0, viewBottom: 400, seenTop: 50, seenBottom: 380, viewLeft: 0, viewRight: 900, seenLeft: 59, seenRight: 900))")
check("G7b3 mirrored frames give the same sides",
      GraphInsets.measured(viewTop: 0, viewBottom: 400, seenTop: 0, seenBottom: 400,
                           viewLeft: 0, viewRight: 900, seenLeft: 0, seenRight: 841)
        == GraphInsets(left: 59, right: 59))
/// A window's top and bottom edges, in points from the view's top.
func edges(_ w: GraphWindow, height: Float) -> (top: Float, bottom: Float) {
    let middle: Float = height * (0.5 - w.up)
    return (middle - w.share * height / 2, middle + w.share * height / 2)
}
// room for both: the window runs from the strip's foot to the card's cover
let roomy: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: measuredGlass.coveringFoot(400))
let roomyEdges = edges(roomy, height: 852)
check("G7c with room, the window is the strip's foot to the card's cover",
      abs(roomyEdges.top - 111) < 0.01 && abs(roomyEdges.bottom - 452) < 0.01, "\(roomyEdges)")
// the iPhone's Neurons card leaves 282.4 points, under a third (284): the
// third is centred between the strip and the card, so neither wins and each
// is crossed by the same 0.8 points, as Android's MapMath.fitWindow does
let neuronCover: Float = 852 - 415.4 + 22
let tight: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: measuredGlass.coveringFoot(neuronCover))
let tightEdges = edges(tight, height: 852)
let spill: Float = (852.0 / 3 - (852 - 111 - neuronCover)) / 2
check("G7d short of a third: centred between them, crossing each by the same",
      near(tight.share, 1.0 / 3, 1e-5) && abs((111 - tightEdges.top) - spill) < 0.01
      && abs((tightEdges.bottom - (852 - neuronCover)) - spill) < 0.01 && spill < 1, "\(tightEdges) spill \(spill)")
// the reading of 0 at the top put the window's top at the screen's
let unread: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: GraphInsets().coveringFoot(neuronCover))
check("G7e read as 0, the window began at the screen's top (the defect)", abs(edges(unread, height: 852).top) < 0.01)
/// The highest and lowest edges, in points from the top of an 852-point
/// view, of `points` with their `pads`, framed in `window` at `fill`.
func drawnSpan(_ points: [SIMD3<Float>], _ radii: [Float], window: GraphWindow, fill: Float,
               tilt: Float) -> (highest: Float, lowest: Float) {
    let fit: GraphTiltFit = GraphTilt.fit(points: points, pads: radii, window: window, fill: fill, tilt: tilt)
    var highest: Float = 852
    var lowest: Float = 0
    for (p, r) in zip(points, radii) {
        let q: SIMD3<Float> = GraphTilt.rotX(-tilt, p)
        let front: Float = fit.distance - q.z - r
        lowest = max(lowest, (1 - (q.y - fit.shift.y - r) / (front * GraphTilt.tanHalf)) / 2 * 852)
        highest = min(highest, (1 - (q.y - fit.shift.y + r) / (front * GraphTilt.tanHalf)) / 2 * 852)
    }
    return (highest, lowest)
}
// the made-up cloud framed as the Neurons' first view (fill 1, pitched
// 35°) in that window: every point inside it, so below the strip but for
// the third's shared spill, and above the card's top
let neuronSpan = drawnSpan(cloud, [Float](repeating: 0, count: cloud.count), window: tight, fill: 1,
                           tilt: GraphTilt.angle(.neurons))
check("G7f Neurons: every point inside the window, so below the strip but for the spill",
      neuronSpan.highest >= tightEdges.top - 0.01, "highest at \(neuronSpan.highest), window from \(tightEdges.top)")
check("G7g and above the card's top", neuronSpan.lowest <= 415.4, "lowest at \(neuronSpan.lowest)")
// Space's card is taller (its top at 395.3): its third crosses each by
// 10.9 points, and its fill (0.88) keeps every padded point clear of both
let spaceCover: Float = 852 - 395.3 + 22
let spaceWindow: GraphWindow = GraphTilt.window(width: 393, height: 852, insets: measuredGlass.coveringFoot(spaceCover))
let spaceSpan = drawnSpan(cloud, pads, window: spaceWindow, fill: 0.88, tilt: GraphTilt.angle(.space))
check("G7h Space: every padded point below the strip", spaceSpan.highest >= 111, "highest at \(spaceSpan.highest)")
check("G7i and above the card's top", spaceSpan.lowest <= 395.3, "lowest at \(spaceSpan.lowest)")

print(failures.isEmpty ? "ALL PASS" : "FAILURES: \(failures.count)")
if !failures.isEmpty { exit(1) }
