import Foundation

// S11: the 3D Tree's draw lists, camera and names (TreeModel3D.swift), on the
// approved concept's sample (tree-fixture.json's "sample"), as Android's
// TreeModelTest and TreeNamesTest: two instanced lists within the tier's cap,
// whole meshes, and names that never overlap on a phone, a tablet and an open
// foldable, whole, turned 40 and 120 degrees, and flown in to Cardiology.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let fixtureURL = URL(fileURLWithPath: "android/app/src/test/resources/tree/tree-fixture.json")
let fixture = (try? JSONSerialization.jsonObject(with: Data(contentsOf: fixtureURL))) as? [String: Any] ?? [:]
let trees = fixture["trees"] as? [[String: Any]] ?? []
check("the fixture reads", trees.count == 4)

func nodesOf(_ t: [String: Any]) -> NestNodes {
    let parent = (t["parent"] as? [Int]) ?? [], title = (t["title"] as? [String]) ?? [], folder = (t["folder"] as? [Int]) ?? []
    return NestNodes(ids: parent.indices.map { "\($0)" }, parent: parent, titles: title, folder: folder.map { $0 == 1 },
                     tone: Array(repeating: -1, count: parent.count))
}

let sample = nodesOf(trees.first ?? [:])
let layout = TreeLayout3D(sample)
let light = TreeModel3D(sample, layout, colors: TreeColors(dark: false), tier: .high)
let leafCount = layout.leaf.filter { $0 > 0 }.count

// the draw lists
check("one branch a node, one leaf a leaf", light.branchCount == sample.size && light.leafCount == leafCount && !light.capped,
      "\(light.branchCount) \(light.leafCount)")
check("the lists' lengths", light.branches.count == light.branchCount * TreeModel3D.branchFloats
      && light.leaves.count == light.blades * TreeModel3D.leafFloats)
check("the trunk first, its node kept, its control point on its line", light.branches[14] == 0 && light.branches[4] == 1
      && abs(light.branches[7] - 0.5) < 1e-6)
check("every instance names its node", (0..<light.branchCount).allSatisfy { k in
    let i = Int(light.branches[k * TreeModel3D.branchFloats + 14]); return i >= 0 && i < sample.size })
/// The WCAG contrast of two colours.
func contrast(_ a: Int, _ b: Int) -> Double {
    func lin(_ c: Int) -> Double { let x = Double(c) / 255; return x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
    func y(_ c: Int) -> Double { 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255) }
    let p = y(a), q = y(b)
    return (max(p, q) + 0.05) / (min(p, q) + 0.05)
}

/// Whether branch instance `k`'s control point is its node's line's middle plus its bend.
func controlHolds(_ k: Int) -> Bool {
    let o: Int = k * TreeModel3D.branchFloats
    let i: Int = Int(light.branches[o + 14])
    for a in 0..<3 {
        let run: Double = layout.dir[3 * i + a] * layout.length[i] / 2
        let want: Double = layout.base[3 * i + a] + run + layout.bend[3 * i + a]
        if abs(Float(want) - light.branches[o + 6 + a]) >= 1e-5 { return false }
    }
    return true
}
check("every branch's control point is its line's middle plus its bend", (0..<light.branchCount).allSatisfy(controlHolds))
check("every leaf turned within the roll", (0..<light.leafCount).allSatisfy { abs(light.leaves[$0 * TreeModel3D.leafFloats + 7]) <= Float(TreeModel3D.roll) + 1e-6 })
let smooth = TreeModel3D(sample, layout, colors: TreeColors(dark: false), tier: .smooth)
check("High draws finer than Smooth", smooth.segments < light.segments && smooth.rings < light.rings && smooth.bladeRows < light.bladeRows)
check("colours are the 3D bark's", light.branches[11] == Float((light.colors.wood3d[sample.group[0]] >> 16) & 255) / 255)
let dark = TreeModel3D(sample, layout, colors: TreeColors(dark: true), tier: .high)
check("dark mode has its own bark", dark.branches[11] != light.branches[11])
// the owner's review of 19 Nov: the bark a brown leaning a fifth to its folder's colour, a leaf a green leaning nearly half to it
for k in [TreeColors(dark: false), TreeColors(dark: true)] {
    check("\(k.dark ? "dark" : "light"): the trunk's bark is the wood", k.wood3d[NestNodes.trunk] == k.wood)
    // a top folder's leaves in its colour's hue at the green's lightness (the lead's review of 19 Nov), pinned as Android's
    check("\(k.dark ? "dark" : "light"): a folder's bark leans to its colour, its leaves in its hue", k.wood3d[0] == TreeColors.mix(k.wood, TreeColors.tone[0], 0.2)
          && k.leaf3d[0] == (k.dark ? 0x4DD3C4 : 0x218379) && k.leaf3d[NestNodes.trunk] == k.foliage && k.leaf3d[NestNodes.loose] == k.foliage,
          String(k.leaf3d[0], radix: 16))
    for g in 0..<NestNodes.tones {
        let leaf = k.leaf3d[g]
        let ch = [(leaf >> 16) & 255, (leaf >> 8) & 255, leaf & 255]
        check("\(k.dark ? "dark" : "light") group \(g): coloured, against the sky, told apart",
              contrast(leaf, k.sky) >= 3.5 && ch.max()! - ch.min()! >= 60 && !(0..<g).contains { k.leaf3d[$0] == leaf }, String(leaf, radix: 16))
    }
    let r = (k.wood >> 16) & 255, g = (k.wood >> 8) & 255, b = k.wood & 255
    let fr = (k.foliage >> 16) & 255, fg = (k.foliage >> 8) & 255, fb = k.foliage & 255
    check("\(k.dark ? "dark" : "light"): the wood is brown and the foliage green", r > g && g > b && fg > fr && fg > fb)
}
// one leaf an idea, and the frame's cost, as Android's aLeafAnIdeaAndTheTrianglesAFrameDraws
let leafNodes = (0..<light.leafCount).map { Int(light.leaves[$0 * TreeModel3D.leafFloats + 11]) }
check("one leaf an idea, each once", Set(leafNodes).count == leafNodes.count
      && Set(leafNodes) == Set((1..<sample.size).filter { sample.childCount[$0] == 0 }))
check("the triangles a frame draws", light.triangles == light.branchCount * 12 * 7 * 2 + (light.leafCount + light.fillerCount) * 6 * 4
      && smooth.triangles == smooth.branchCount * 7 * 5 * 2 + (smooth.leafCount + smooth.fillerCount) * 4 * 4
      && light.triangles == TreeModel3D.tube(light.segments, rings: light.rings).indices.count / 3 * light.branchCount
         + TreeModel3D.blade(light.bladeRows).indices.count / 3 * light.blades)
print("Tree triangles, High: \(light.triangles) (\(light.branchCount) branches, \(light.leafCount) ideas' leaves, \(light.fillerCount) fillers); Smooth: \(smooth.triangles) (\(smooth.fillerCount) fillers); 2 draws a frame")
// the lead's review of 19 Nov ("a bare winter tree"): round each idea's leaf a few smaller filler leaves on its stalk,
// drawn only, after the ideas' leaves, marked, capped; the ideas still counted exactly. As Android's fillerLeavesAreDrawnOnlyAndCapped.
check("three fillers an idea on High, two on Smooth", light.fillerCount == light.leafCount * TreeModel3D.fillersHigh
      && smooth.fillerCount == smooth.leafCount * TreeModel3D.fillersSmooth, "\(light.fillerCount) \(smooth.fillerCount)")
check("every filler marked, an idea's, smaller, within its reach", (0..<light.fillerCount).allSatisfy { f in
    let o = (light.leafCount + f) * TreeModel3D.leafFloats
    let node = light.leaves[o + 11], i = Int(node)
    let reach = (0..<3).map { Double(light.leaves[o + $0]) - layout.base[3 * i + $0] }.map { $0 * $0 }.reduce(0, +).squareRoot()
    let axis = (3..<6).map { Double(light.leaves[o + $0] * light.leaves[o + $0]) }.reduce(0, +).squareRoot()
    return abs(node - Float(i) - TreeModel3D.fillerMark) < 1e-6 && sample.childCount[i] == 0 && layout.leaf[i] > 0
        && Double(light.leaves[o + 6]) < layout.leaf[i] && Double(light.leaves[o + 6]) >= layout.leaf[i] * TreeModel3D.fillerSizeLeast - 1e-6
        && reach <= layout.length[i] + Double(light.leaves[o + 6]) + 1e-5 && abs(light.leaves[o + 7]) <= Float(TreeModel3D.roll) + 1e-6
        && abs(axis - 1) < 1e-5 })
check("no filler stands in for an idea", (0..<light.leafCount).allSatisfy { let v = light.leaves[$0 * TreeModel3D.leafFloats + 11]; return v == v.rounded(.down) })
// Android's wide(30, 400): the first filler pinned to Android's numbers, and the caps on a big vault
func wide(_ folders: Int, _ ideas: Int) -> NestNodes {
    let f = (0..<folders).map { NestFolderIn(id: "f\($0)", parentId: $0 == 0 ? nil : "f\(($0 - 1) / 3)", name: "Folder \($0)") }
    let i = (0..<ideas).map { NestIdeaIn(id: "i\($0)", folderId: "f\($0 % folders)", title: "Idea \($0)", createdAt: Int64($0)) }
    return NestNodes.build(trunk: "All ideas", folders: f, ideas: i)
}
let w400 = wide(30, 400)
let m400 = TreeModel3D(w400, TreeLayout3D(w400), colors: TreeColors(dark: false), tier: .high)
let first = Array(m400.leaves[(m400.leafCount * TreeModel3D.leafFloats)..<(m400.leafCount * TreeModel3D.leafFloats + 12)])
let android: [Float] = [-0.257504, 1.550355, 0.024805, 0.298981, 0.913322, 0.276503, 0.024756, -0.453821, 0.112804, 0.402435, 0.365850, 5.25]
check("the first filler is Android's", m400.fillerCount == 1200 && zip(first, android).allSatisfy { abs($0 - $1) < 2e-6 }, "\(first)")
let big = wide(60, TreeModel3D.smoothCap + 500), bigLayout = TreeLayout3D(big)
check("the fillers capped by tier", TreeModel3D(big, bigLayout, colors: TreeColors(dark: true), tier: .smooth).fillerCount == TreeModel3D.fillerCapSmooth
      && TreeModel3D(big, bigLayout, colors: TreeColors(dark: true), tier: .high).fillerCount == TreeModel3D.fillerCapHigh)
// seeded the same as Android's (TreeModelTest.theSeededNumbersAreIosOwn)
check("the seeded numbers are Android's", TreeModel3D.hash(0, 0) == 0 && abs(TreeModel3D.hash(1, 1) - 0.9573098756) < 1e-9
      && abs(TreeModel3D.hash(7, 2) - 0.3039142611) < 1e-9 && abs(TreeModel3D.hash(12345, 3) - 0.4494442665) < 1e-9
      && (0..<1000).allSatisfy { TreeModel3D.hash($0, 1) >= 0 && TreeModel3D.hash($0, 1) < 1 })

// the meshes
let tube = TreeModel3D.tube(12, rings: 6), blade = TreeModel3D.blade(7)
check("the tube: seven rings and the closing one, two triangles a side each", tube.vertices.count == 13 * 8 * 3 && tube.indices.count == 12 * 7 * 6
      && tube.indices.allSatisfy { Int($0) < 13 * 8 })
let tipAlong: Float = tube.vertices[(6 * 13 + 12) * 3 + 2], capAlong: Float = tube.vertices[tube.vertices.count - 1]
let sixth: Float = 1.0 / 6.0
check("the tube's rings from base to tip, closer at the base, then the closing ring", tube.vertices[2] == 0 && tipAlong == 1
      && capAlong == TreeModel3D.cap && TreeModel3D.ringAt(1, 6) < sixth && TreeModel3D.ringAt(5, 6) > 5 * sixth - 0.1)
/// Whether the tube's vertex at float `o` lies on the unit circle.
func onRing(_ o: Int) -> Bool {
    let x: Float = tube.vertices[o], y: Float = tube.vertices[o + 1]
    return abs((x * x + y * y).squareRoot() - 1) < 1e-6
}
check("the tube's rings are whole", stride(from: 0, to: tube.vertices.count, by: 3).allSatisfy(onRing))
check("the blade: seven rows of three, every index a vertex", blade.vertices.count == 7 * 3 * 6 && blade.indices.count == 6 * 2 * 6
      && blade.indices.allSatisfy { Int($0) < blade.vertices.count / 6 })
/// Whether the blade's vertex at float `o` has a whole normal off its face.
func faceHolds(_ o: Int) -> Bool {
    let x: Float = blade.vertices[o + 3], y: Float = blade.vertices[o + 4], z: Float = blade.vertices[o + 5]
    return abs((x * x + y * y + z * z).squareRoot() - 1) < 1e-5 && z > 0.5
}
check("the blade's normals are whole and off its face", stride(from: 0, to: blade.vertices.count, by: 6).allSatisfy(faceHolds))
let widest = (0...100).max { TreeModel3D.bladeWidth(Double($0) / 100) < TreeModel3D.bladeWidth(Double($1) / 100) } ?? 0
check("the blade: pointed at both ends, widest below its middle", TreeModel3D.bladeWidth(0) == 0 && abs(TreeModel3D.bladeWidth(1)) < 1e-12
      && widest >= 30 && widest <= 50, "\(widest)")
check("the blade: its edges above its midrib, its tip below its stem", blade.vertices[3 * 3 * 6 + 2] > blade.vertices[3 * 3 * 6 + 6 + 2]
      && blade.vertices[(6 * 3 + 1) * 6 + 2] < blade.vertices[1 * 6 + 2])

// the names: a phone, a tablet and an open foldable, in points
struct Window { let name: String; let width: Float; let height: Float; let top: Float; let bottom: Float }
let windows = [Window(name: "phone", width: 390, height: 844, top: 100, bottom: 160),
               Window(name: "tablet", width: 1194, height: 834, top: 80, bottom: 80),
               Window(name: "foldable", width: 884, height: 736, top: 80, bottom: 100)]
let style = TreeNames3D.Style()
func words(_ title: String, _ short: Bool) -> String { TreeNames3D.label(title, cut: short ? TreeNames3D.shortCut : TreeNames3D.cut) }
func measure(_ i: Int, _ short: Bool, _ bold: Bool, _ size: Float) -> (width: Float, height: Float) {
    (Float(words(sample.titles[i], short).count) * size * (bold ? 0.6 : 0.56), size * 1.3)
}
let cardiology = sample.titles.firstIndex(of: "Cardiology") ?? -1
check("the sample has Cardiology", cardiology > 0)

/// The chrome's boxes over `w`'s map, as the Tree's view measures them:
/// flown in, Back a gutter in from the top left; always the two round tools,
/// a gutter in from the foot's right. Android's TreeNamesTest.chrome.
func chrome(_ w: Window, flown: Bool) -> [CGRect] {
    let target: CGFloat = 44, gutter: CGFloat = 16
    let width = CGFloat(w.width), height = CGFloat(w.height)
    let tools = CGRect(x: width - gutter - target, y: height - gutter - 2 * target - 8, width: target, height: 2 * target + 8)
    let back = CGRect(x: gutter, y: gutter, width: target, height: target)
    return flown ? [back, tools] : [tools]
}

/// Half a touch target (44 pt), what TreeMapView's framed adds under the top.
let half: Float = 22

func view(_ w: Window, yaw: Float, into: Int) -> GraphPerfCamera {
    let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
    var c = TreeCamera3D.whole(light, width: w.width, height: w.height, insets: insets)
    c.orbit(yaw: yaw, pitch: 0)
    if into > 0 {
        // as the view flies in (TreeMapView.go): the fly-in's room, half a target under the top
        let room = TreeCamera3D.flyRoom(top: w.top, bottom: w.bottom, half: half, clear: chrome(w, flown: true), height: w.height)
        c = TreeCamera3D.flyIn(light, into, from: c, width: w.width, height: w.height, insets: room)
    }
    return c
}

func names(_ w: Window, yaw: Float, into: Int) -> ([TreeScreenNode], [TreePlacedName]) {
    let screen = TreeCamera3D.project(light, view(w, yaw: yaw, into: into), width: w.width, height: w.height, hidden: nil)
    let cap = TreeNames3D.flownCap(width: w.width, height: w.height - w.top - w.bottom)
    let placed = TreeNames3D.layout(screen, sample, style: style, measure: measure, width: w.width, height: w.height,
                                    top: w.top, bottom: w.bottom, focus: max(into, 0), clear: chrome(w, flown: into > 0), cap: cap)
    return (screen, placed)
}

let rad: (Float) -> Float = { $0 * Float.pi / 180 }
let views: [(String, Float, Int)] = [("whole", 0, -1), ("orbited 40", rad(40), -1), ("orbited 120", rad(120), -1),
                                     ("Cardiology", 0, cardiology), ("Cardiology orbited 40", rad(40), cardiology)]
for w in windows {
    for (what, yaw, into) in views {
        let (screen, placed) = names(w, yaw: yaw, into: into)
        check("\(w.name) \(what): bodies on screen (\(screen.count))", screen.count > 20)
        check("\(w.name) \(what): names placed (\(placed.count))", placed.count >= 8)
        check("\(w.name) \(what): no two names overlap", !TreeNames3D.overlaps(placed))
        check("\(w.name) \(what): every name inside the view and off the chrome", placed.allSatisfy { p in
            p.left >= style.edge - 0.01 && p.right <= w.width - style.edge + 0.01 && p.top >= w.top - 0.01 && p.bottom <= w.height - w.bottom + 0.01 })
    }
    let whole = names(w, yaw: 0, into: -1).1.map { sample.titles[$0.index] }
    check("\(w.name): All ideas, Examples and Cardiology named at the first view",
          ["All ideas", "Examples", "Cardiology"].allSatisfy { whole.contains($0) }, "\(whole)")
    let inside = names(w, yaw: 0, into: cardiology).1.filter { sample.within(cardiology, $0.index) }
    check("\(w.name): flown in, Cardiology's parts named (\(inside.count))", inside.count >= 8)
}

// flown in: only Cardiology, what grows straight off it and its ideas, up to
// the cap; never under the chrome; a leaf between its floor and ceiling.
// Android's flownInOnlyThePartItsChildrenAndItsIdeasAreNamedUpToTheCap,
// noNameOrLeaderGoesUnderTheChrome and aLeafIsHeldBetweenItsFloorAndCeilingOnScreen
for w in windows {
    let cap = TreeNames3D.flownCap(width: w.width, height: w.height - w.top - w.bottom)
    for yaw in [Float(0), rad(40)] {
        let placed = names(w, yaw: yaw, into: cardiology).1
        check("\(w.name): flown in, at most \(cap) names (\(placed.count))", placed.count <= cap)
        check("\(w.name): flown in, only Cardiology, its own parts and its ideas", placed.allSatisfy { p in
            sample.within(cardiology, p.index)
                && (p.index == cardiology || sample.parent[p.index] == cardiology || !TreeNames3D.bough(sample, p.index)) },
              "\(placed.map { sample.titles[$0.index] })")
    }
    for (yaw, into) in [(Float(0), -1), (rad(120), -1), (Float(0), cardiology), (rad(40), cardiology)] {
        let placed = names(w, yaw: yaw, into: into).1
        for r in chrome(w, flown: into > 0) {
            let x0 = Float(r.minX), x1 = Float(r.maxX), y0 = Float(r.minY), y1 = Float(r.maxY)
            check("\(w.name) \(into): no name under the chrome", placed.allSatisfy { p in
                p.right <= x0 || p.left >= x1 || p.bottom <= y0 || p.top >= y1 })
        }
    }
    let screen = names(w, yaw: 0, into: cardiology).0
    check("\(w.name): every leaf between its floor and ceiling on screen", screen.allSatisfy { n in
        light.layout.leaf[n.index] <= 0 || (n.pixels >= TreeWeight.leafPoints - 1e-3 && n.pixels <= TreeWeight.leafMostPoints + 1e-3) })
}
check("a phone's room holds about 12 to 14 names flown in", (12...14).contains(TreeNames3D.flownCap(width: 393, height: 852 - 100 - 160)))
let inguinal = sample.titles.firstIndex(of: "Inguinal") ?? -1
check("nameable: another branch's part never", inguinal > 0 && !TreeNames3D.nameable(sample, focus: cardiology, inguinal))
let room = TreeCamera3D.around((80, 0, 80, 0), clear: chrome(windows[1], flown: true), height: windows[1].height)
check("a fly-in's room is under Back and over the tools", room.top == 80 && abs(room.bottom - (2 * 44 + 8 + 16)) < 1e-3, "\(room)")

// The fly-in's one rule, Android's too (MapClear.flyRoom; CI, 19 Nov: iOS
// framed Cardiology above a first visit's card the tap put away, small and
// high, its names cut at the right edge): the room is the chrome's window,
// half a target under its top, past Back and the tools, never the card; the
// part fills the fly's fill of it on its binding side, centred in it.
// Android's aFlyInFillsTheChromesRoomCentred.
for w in windows {
    let tools = chrome(w, flown: true)
    let fly = TreeCamera3D.flyRoom(top: w.top, bottom: w.bottom, half: half, clear: tools, height: w.height)
    check("\(w.name): the fly-in's room is the chrome's window", abs(fly.top - max(w.top + half, Float(tools[0].maxY))) < 1e-3
          && abs(fly.bottom - max(w.bottom, w.height - Float(tools[1].minY))) < 1e-3 && fly.left == 0 && fly.right == 0, "\(fly)")
    let c = view(w, yaw: 0, into: cardiology)
    guard let box = TreeFit.box(TreeCamera3D.subtreePoints(light, cardiology), margin: light.margin, eye: c.target + c.back * c.distance,
                                r: c.right, u: c.up, b: c.back, f: c.focal(viewHeight: w.height)) else {
        check("\(w.name): Cardiology in front of the eye", false); continue
    }
    let rh: Float = w.height - fly.top - fly.bottom
    let across: Float = (box.x1 - box.x0) / w.width, tall: Float = (box.y1 - box.y0) / rh
    let cx: Float = w.width / 2 + (box.x0 + box.x1) / 2, cy: Float = w.height / 2 - (box.y0 + box.y1) / 2
    print(String(format: "fly %@: across %.4f tall %.4f off x %.4f off y %.4f", w.name, across, tall,
                 (cx - w.width / 2) / w.width, (cy - (fly.top + rh / 2)) / rh))
    check("\(w.name): Cardiology fills the room on its binding side", abs(max(across, tall) - TreeCamera3D.fill) <= 0.005, "\(across) \(tall)")
    check("\(w.name): and no more on the other", min(across, tall) <= TreeCamera3D.fill + 0.005, "\(across) \(tall)")
    check("\(w.name): centred across", abs(cx - w.width / 2) <= 0.005 * w.width, "\(cx)")
    check("\(w.name): centred between Back and the tools", abs(cy - (fly.top + rh / 2)) <= 0.005 * rh, "\(cy) in \(fly)")
}
check("a fly-in fills as Android's MapFly.FILL", TreeCamera3D.fill == 0.88)

// the order: branches first, then by weight
let phone = windows[0]
let screen = TreeCamera3D.project(light, view(phone, yaw: 0, into: -1), width: phone.width, height: phone.height, hidden: nil)
let ordered = TreeNames3D.order(screen, sample)
let firstLeaf = ordered.firstIndex { !TreeNames3D.bough(sample, $0.index) } ?? ordered.count
check("branches before leaves", ordered[firstLeaf...].allSatisfy { !TreeNames3D.bough(sample, $0.index) })
check("the trunk first", ordered.first?.index == 0)

// a pile of names in one place keeps a few and drops the rest, the trunk first
let pile = (0..<12).map { TreeScreenNode(index: $0, x: 195, y: 400, pixels: 10, depth: 1) }
let kept = TreeNames3D.layout(pile, sample, style: style, measure: measure, width: 390, height: 844, top: 100, bottom: 160)
check("a pile keeps 1 to 5 names, the trunk first", (1...5).contains(kept.count) && kept.first?.index == 0, "\(kept.count)")
check("a pile's names never overlap", !TreeNames3D.overlaps(kept))

// a hidden part has no body on screen
var hidden = [Bool](repeating: false, count: sample.size)
hidden[cardiology] = true
let hiddenScreen = TreeCamera3D.project(light, view(phone, yaw: 0, into: -1), width: phone.width, height: phone.height, hidden: hidden)
check("a hidden part is not on screen", !hiddenScreen.contains { $0.index == cardiology } && hiddenScreen.count == screen.count - 1)

// a tap: on a name, on a body, on empty space
let (phoneScreen, phoneNames) = names(phone, yaw: 0, into: -1)
if let named = phoneNames.first(where: { $0.index == cardiology }) {
    check("a tap on a name is its part", TreeNames3D.hit(phoneScreen, phoneNames, x: named.x, y: named.y, least: 44) == cardiology)
} else { check("Cardiology named on the phone", false) }
check("a tap far from the tree is empty space", TreeNames3D.hit(phoneScreen, phoneNames, x: 2, y: 2, least: 44) == -1)

// the camera: the whole tree above the card, a fly-in nearer
let whole = view(phone, yaw: 0, into: -1)
let foot = whole.project(SIMD3<Float>(0, 0, 0), width: phone.width, height: phone.height)
check("the trunk's foot above the card", foot.y <= phone.height - phone.bottom + 0.5, "\(foot.y)")
check("a fly-in comes nearer", view(phone, yaw: 0, into: cardiology).distance < whole.distance)
check("the limits hold the whole tree", TreeCamera3D.limits(light).contains(whole.distance))
let again = TreeModel3D(sample, TreeLayout3D(sample), colors: TreeColors(dark: false), tier: .high)
check("the same tree, the same lists", again.branches == light.branches && again.leaves == light.leaves)

// the first visit's card: the names keep above its cover, which holds the
// chrome's foot already, so the two are not added (Android's run 44342720370
// lost its big branches that way); the first view is framed half a target above
check("the floor is the cover where it is higher", TreeNames3D.floor(bottom: 60, cover: 300) == 300
      && TreeNames3D.floor(bottom: 60, cover: 0) == 60)

// the sample as the app grows it from a store (TreeBook.nodes: a part with
// parts is a folder, folders by name, ideas oldest first), as the UI test holds it
let kids: [Int] = { var k = [Int](repeating: 0, count: sample.size); for i in 1..<sample.size { k[sample.parent[i]] += 1 }; return k }()
let storeFolders: [NestFolderIn] = (1..<sample.size).filter { kids[$0] > 0 }
    .sorted { sample.titles[$0].localizedStandardCompare(sample.titles[$1]) == .orderedAscending }
    .map { NestFolderIn(id: "p\($0)", parentId: sample.parent[$0] == 0 ? nil : "p\(sample.parent[$0])", name: sample.titles[$0]) }
let storeIdeas: [NestIdeaIn] = (1..<sample.size).filter { kids[$0] == 0 }
    .map { NestIdeaIn(id: "p\($0)", folderId: sample.parent[$0] == 0 ? nil : "p\(sample.parent[$0])", title: sample.titles[$0],
                      createdAt: Int64($0) * 60_000) }
let store = NestNodes.build(trunk: "All ideas", folders: storeFolders, ideas: storeIdeas)
check("the store's tree has every part", store.size == sample.size)
let storeModel = TreeModel3D(store, TreeLayout3D(store), colors: TreeColors(dark: false), tier: .high)
func storeMeasure(_ i: Int, _ short: Bool, _ bold: Bool, _ size: Float) -> (width: Float, height: Float) {
    (Float(words(store.titles[i], short).count) * size * (bold ? 0.6 : 0.56), size * 1.3)
}
let storeCardiology = store.titles.firstIndex(of: "Cardiology") ?? -1
// a phone and an iPad with the card up: the chrome's foot, the card's cover from the view's foot
let carded: [(String, Window, Float)] = [("phone", Window(name: "phone", width: 390, height: 700, top: 56, bottom: 60), 300),
                                         ("iPad", Window(name: "iPad", width: 1194, height: 760, top: 56, bottom: 60), 260)]
for (m, tree, measureOf, sampleOf) in [(light, "concept", measure, sample), (storeModel, "store", storeMeasure, store)]
        as [(TreeModel3D, String, (Int, Bool, Bool, Float) -> (width: Float, height: Float), NestNodes)] {
    for (name, w, cover) in carded {
        let half: Float = 22
        let framed: (top: Float, left: Float, bottom: Float, right: Float) = (w.top + half, 0, max(w.bottom, cover + half), 0)
        let floor = TreeNames3D.floor(bottom: w.bottom, cover: cover)
        for (what, yaw) in [("whole", Float(0)), ("orbited 40", rad(40)), ("orbited 120", rad(120))] {
            var c = TreeCamera3D.whole(m, width: w.width, height: w.height, insets: framed)
            c.orbit(yaw: yaw, pitch: 0)
            let screen = TreeCamera3D.project(m, c, width: w.width, height: w.height, hidden: nil)
            let placed = TreeNames3D.layout(screen, sampleOf, style: style, measure: measureOf, width: w.width, height: w.height,
                                            top: w.top, bottom: floor)
            let words = placed.map { sampleOf.titles[$0.index] }
            check("\(tree) \(name) with the card, \(what): no two names overlap", !TreeNames3D.overlaps(placed))
            check("\(tree) \(name) with the card, \(what): every name above the card", placed.allSatisfy { $0.bottom <= w.height - cover + 0.01 })
            if yaw == 0 {
                check("\(tree) \(name) with the card: All ideas, Examples and Cardiology named",
                      ["All ideas", "Examples", "Cardiology"].allSatisfy { words.contains($0) }, "\(words)")
                let foot = c.project(SIMD3<Float>(0, 0, 0), width: w.width, height: w.height)
                check("\(tree) \(name): the trunk's foot above the card", foot.y <= w.height - cover, "\(foot.y)")
            }
        }
    }
}
for w in windows {
    var c = TreeCamera3D.whole(storeModel, width: w.width, height: w.height, insets: (w.top, 0, w.bottom, 0))
    c = TreeCamera3D.flyIn(storeModel, storeCardiology, from: c, width: w.width, height: w.height, insets: (w.top, 0, w.bottom, 0))
    let placed = TreeNames3D.layout(TreeCamera3D.project(storeModel, c, width: w.width, height: w.height, hidden: nil), store,
                                    style: style, measure: storeMeasure, width: w.width, height: w.height, top: w.top,
                                    bottom: w.bottom, focus: storeCardiology)
    check("store \(w.name), flown in to Cardiology: its parts named, none overlapping",
          placed.filter { store.within(storeCardiology, $0.index) }.count >= 8 && !TreeNames3D.overlaps(placed))
}

// the cost guards: every body far apart on a huge view, each with room for
// its name, and only `most` drawn, the first in the order
let grid = (0..<sample.size).map { TreeScreenNode(index: $0, x: 200 + Float($0 % 12) * 700, y: 200 + Float($0 / 12) * 160, pixels: 16, depth: 1) }
let gridNames = TreeNames3D.layout(grid, sample, style: style, measure: measure, width: 9000, height: 9000, top: 0, bottom: 0)
check("more bodies than names allowed", sample.size > TreeNames3D.most)
check("at most \(TreeNames3D.most) names, the first in the order", gridNames.count == TreeNames3D.most
      && gridNames.map(\.index) == Array(TreeNames3D.order(grid, sample).prefix(TreeNames3D.most)).map(\.index), "\(gridNames.count)")
check("the budget is at least the most", TreeNames3D.budget >= TreeNames3D.most)

// the lead's review of 18 Nov, as Android's TreeNamesTest: the branches level
// by level, the shallower first, each level by weight
check("the branches level by level, each by weight", (1..<firstLeaf).allSatisfy { k in
    let a = ordered[k - 1].index, b = ordered[k].index
    return sample.depth[a] < sample.depth[b] || (sample.depth[a] == sample.depth[b] && sample.weight[a] >= sample.weight[b])
})
// at the whole tree the names leave the trunk clear, a deeper part is named
// only at its tip, and a leader never runs under another name
for w in windows {
    for yaw in [Float(0), rad(40), rad(120)] {
        let (screen, placed) = names(w, yaw: yaw, into: -1)
        guard let trunk = screen.first(where: { $0.index == 0 }), let top = trunk.tip else { check("\(w.name) \(yaw): the trunk on screen", false); continue }
        let a = Float(TreeShape3D.anchor)
        let stem = TreeNames3D.Segment(a: (SIMD2(trunk.x, trunk.y) - a * top) / (1 - a), b: top, half: trunk.pixels / 2)
        let first = Set(placed.filter { sample.depth[$0.index] <= 1 }.map(\.index))
        check("\(w.name) \(yaw): the trunk and its branches named", first.contains(0) && first.count >= 3, "\(first)")
        check("\(w.name) \(yaw): no name but the trunk's covers the trunk",
              placed.allSatisfy { $0.index == 0 || !stem.crosses($0.x, $0.y, $0.width, $0.height, gap: 0) })
        check("\(w.name) \(yaw): a deeper part named only at its tip",
              placed.allSatisfy { sample.depth[$0.index] <= 1 || $0.spot <= TreeNames3D.lastTipSpot })
        // out past the crown, a leader runs back to the tip
        check("\(w.name) \(yaw): a deeper part's name past the crown has a leader to its tip", placed.allSatisfy { p in
            guard sample.depth[p.index] > 1, p.spot >= TreeNames3D.pastAboveSpot else { return true }
            guard let s = screen.first(where: { $0.index == p.index }), let tip = s.tip, let lead = p.lead else { return false }
            return lead == tip
        })
        check("\(w.name) \(yaw): no leader runs under a name", placed.allSatisfy { p in
            guard let tip = p.lead else { return true }
            let line = TreeNames3D.Segment(a: SIMD2(tip.x < p.x ? p.left : p.right, p.y), b: tip, half: 1)
            return placed.allSatisfy { $0.index == p.index || !line.crosses($0.x, $0.y, $0.width, $0.height, gap: 0) }
        })
    }
}

// the owner's review of 19 Nov ("the text overlaps a lot"), as Android's
// TreeNamesTest: at 12 angles round, the whole tree and flown in to each top
// folder, on a phone, a tablet and an open foldable, no name box (its halo
// included) meets another, nor leaves the view or goes under the chrome; and
// few names lie over another branch's leaves
func sweep(_ tree: String, _ m: TreeModel3D, _ nodes: NestNodes, _ measureOf: (Int, Bool, Bool, Float) -> (width: Float, height: Float)) {
    let tops = (0..<nodes.size).filter { $0 != 0 && nodes.parent[$0] == 0 && nodes.childCount[$0] > 0 }
    check("\(tree): top folders (\(tops.count))", tops.count >= 4)
    var swept = 0, named = 0, over = 0
    for w in windows {
        let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
        let cap = TreeNames3D.flownCap(width: w.width, height: w.height - w.top - w.bottom)
        for k in 0..<12 {
            for into in [-1] + tops {
                var c = TreeCamera3D.whole(m, width: w.width, height: w.height, insets: insets)
                c.orbit(yaw: rad(Float(30 * k)), pitch: 0)
                if into > 0 {
                    let room = TreeCamera3D.around(insets, clear: chrome(w, flown: true), height: w.height)
                    c = TreeCamera3D.flyIn(m, into, from: c, width: w.width, height: w.height, insets: room)
                }
                let screen = TreeCamera3D.project(m, c, width: w.width, height: w.height, hidden: nil)
                let tools = chrome(w, flown: into > 0)
                let placed = TreeNames3D.layout(screen, nodes, style: style, measure: measureOf, width: w.width, height: w.height,
                                                top: w.top, bottom: w.bottom, focus: max(into, 0), clear: tools, cap: cap)
                let what = "\(tree) \(w.name) at \(30 * k) degrees, \(into < 0 ? "whole" : nodes.titles[into])"
                swept += 1; named += placed.count
                if placed.isEmpty { check("\(what): names placed", false) }
                if TreeNames3D.overlaps(placed) { check("\(what): no two names meet", false) }
                for p in placed {
                    // the box holds the words and the halo round them, inside the view and off the chrome
                    let words = measureOf(p.index, p.short, p.bold, p.size)
                    let box = CGRect(x: CGFloat(p.left), y: CGFloat(p.top), width: CGFloat(p.width), height: CGFloat(p.height))
                    let ok = p.width >= words.width + 2 * style.halo - 1e-3 && p.height >= words.height + 2 * style.halo - 1e-3
                        && p.left >= 0 && p.right <= w.width && p.top >= w.top && p.bottom <= w.height - w.bottom
                        && !tools.contains { $0.intersects(box) }
                    if !ok { check("\(what): \(nodes.titles[p.index]) holds its halo, inside the view and off the chrome", false) }
                    let own = TreeNames3D.owner(nodes, focus: max(into, 0), p.index)
                    if screen.contains(where: { s in
                        let dx = max(abs(s.x - p.x) - p.width / 2, 0), dy = max(abs(s.y - p.y) - p.height / 2, 0)
                        return !TreeNames3D.bough(nodes, s.index) && !nodes.within(own, s.index) && dx * dx + dy * dy < s.pixels * s.pixels / 4
                    }) { over += 1 }
                }
            }
        }
    }
    print("\(tree): \(swept) views, \(named) names, \(over) over another branch's leaves")
    check("\(tree): every view swept", swept == 3 * 12 * (1 + tops.count), "\(swept)")
    // at most 8 in 100 (the lead's review of 19 Nov lowered it from 10: flown in, names stand beside their branches)
    check("\(tree): \(over) of \(named) names over another branch's leaves", over * 100 <= 8 * named)
}
// a part flown in to before the tree shrank, past the new tree's end, is the whole tree (TreeHostRegrowTests)
do {
    let w = windows[0]
    let (screen, whole) = names(w, yaw: 0, into: -1)
    for past in [sample.size, sample.size + 40] {
        let placed = TreeNames3D.layout(screen, sample, style: style, measure: measure, width: w.width, height: w.height,
                                        top: w.top, bottom: w.bottom, focus: past, clear: chrome(w, flown: false))
        check("focus \(past): the whole tree's names", placed == whole, "\(placed.count) and \(whole.count)")
    }
}
sweep("concept", light, sample, measure)
sweep("store", storeModel, store, storeMeasure)

// the first view fills the room the chrome and the card leave, with a small margin
/// Every branch's ends and middle on its curve, every body and every leaf's tip: what the first view frames.
func everyPoint(_ m: TreeModel3D) -> [SIMD3<Float>] { TreeCamera3D.subtreePoints(m, 0) }
for (m, tree) in [(light, "concept"), (storeModel, "store")] {
    let rooms: [(String, Window, (top: Float, left: Float, bottom: Float, right: Float))] = [
        ("phone with the card", carded[0].1, (carded[0].1.top + 22, 0, carded[0].2 + 22, 0)),
        ("phone", windows[0], (windows[0].top, 0, windows[0].bottom, 0)),
        ("tablet", windows[1], (windows[1].top, 0, windows[1].bottom, 0)),
        ("foldable", windows[2], (windows[2].top, 0, windows[2].bottom, 0))]
    for (name, w, room) in rooms {
        let c = TreeCamera3D.whole(m, width: w.width, height: w.height, insets: room)
        guard let box = TreeFit.box(everyPoint(m), margin: 0, eye: c.eye, r: c.right, u: c.up, b: c.back, f: c.focal(viewHeight: w.height)) else {
            check("\(tree) \(name): the tree in front of the eye", false); continue
        }
        let left = w.width / 2 + box.x0, right = w.width / 2 + box.x1, top = w.height / 2 - box.y1, bottom = w.height / 2 - box.y0
        check("\(tree) \(name): the first view inside the room", left >= -0.01 && right <= w.width + 0.01 && top >= room.top - 0.01
              && bottom <= w.height - room.bottom + 0.01, "\(left) \(top) \(right) \(bottom)")
        let wide = (right - left) / w.width, tall = (bottom - top) / (w.height - room.top - room.bottom)
        check("\(tree) \(name): the first view fills the room", max(wide, tall) >= 0.92, "\(wide) x \(tall)")
    }
}

// the first view from where the crown stands widest, as Android's theFirstViewShowsTheCrownSpread
for (m, tree) in [(light, "concept"), (storeModel, "store")] {
    let points = everyPoint(m)
    func across(_ yaw: Float) -> Float {
        let c: Float = cos(yaw), s: Float = sin(yaw)
        let xs: [Float] = points.map { (p: SIMD3<Float>) -> Float in p.x * c - p.z * s }
        return (xs.max() ?? 0) - (xs.min() ?? 0)
    }
    let w = windows[0]
    let yaw = TreeCamera3D.whole(m, width: w.width, height: w.height, insets: (w.top, 0, w.bottom, 0)).yaw
    check("\(tree): the first view's yaw is Android's, 5 degrees right of front (the grown tree of 19 Nov)", abs(yaw * 180 / Float.pi - 5) < 1e-3, "\(yaw * 180 / Float.pi)")
    check("\(tree): no yaw shows the crown wider", (0..<72).allSatisfy { across(yaw) >= across(Float($0) * 5 * Float.pi / 180) * (1 - 1e-4) })
}

// the finest twig at least 1.5 points across and a leaf at least 6 long, however far
var floorsHold = true
for scale: Float in [1, 2, 3] { for focal: Float in [400, 1200, 3000] { for depth: Float in [0.5, 3, 40] {
    let twig = TreeWeight.twigFloor(scale: scale, focal: focal) * depth * focal / depth * 2
    let leaf = TreeWeight.leafFloor(scale: scale, focal: focal) * depth * focal / depth * 2
    if abs(twig - TreeWeight.twigPoints * scale) > 1e-3 * twig || abs(leaf - TreeWeight.leafPoints * scale) > 1e-3 * leaf { floorsHold = false }
} } }
check("the twig's and leaf's floors hold at every scale, focal length and depth", floorsHold)
// and at the phone's first view the trunk and its big branches are solid, not hairlines
for (m, tree) in [(light, "concept"), (storeModel, "store")] {
    let w = windows[0]
    let c = TreeCamera3D.whole(m, width: w.width, height: w.height, insets: (w.top, 0, w.bottom, 0))
    let f = c.focal(viewHeight: w.height), l = m.layout, nodes = m.nodes
    func across(_ i: Int) -> Float {
        let p = SIMD3<Float>(Float(l.base[3 * i]), Float(l.base[3 * i + 1]), Float(l.base[3 * i + 2])) - c.eye
        return Float(2 * l.r0[i]) * f / (p * p).sum().squareRoot()
    }
    check("\(tree): the trunk is at least 8 points across (\(across(0)))", across(0) >= 8)
    let big = (0..<nodes.childCount[0]).map { nodes.child(0, $0) }.filter { nodes.weight[$0] >= 8 }
    check("\(tree): the trunk's big branches at least 3 points across", big.allSatisfy { across($0) >= 3 }, "\(big.map(across))")
}

// the lead's review of the third pass: flown in to Cardiology, its instances draw in full colour and
// only the rest fades; a chosen idea or a hidden one keeps its own state (Android's TreeModelTest)
do {
    let nodes = light.nodes
    let chosen = (1..<nodes.size).first { !nodes.folder[$0] && nodes.within(cardiology, $0) } ?? -1
    let gone = (1..<nodes.size).first { !nodes.folder[$0] && nodes.within(cardiology, $0) && $0 != chosen } ?? -1
    func state(_ i: Int) -> UInt8 { TreeState.of(nodes, i, hidden: i == gone, chosen: chosen, linked: false, focus: cardiology) }
    var drawn: [Int] = []
    for k in 0..<light.branchCount { drawn.append(Int(light.branches[k * TreeModel3D.branchFloats + 14])) }
    for k in 0..<light.leafCount { drawn.append(Int(light.leaves[k * TreeModel3D.leafFloats + 11])) }
    let inside = drawn.filter { nodes.within(cardiology, $0) }
    check("flown in: Cardiology has instances (\(inside.count))", inside.count > 10 && chosen > 0 && gone > 0)
    let full = inside.allSatisfy { i in
        let want: UInt8 = i == gone ? TreeState.hidden : (i == chosen ? TreeState.chosen : TreeState.none)
        return state(i) == want
    }
    check("flown in: every instance under Cardiology draws in full colour", full && state(cardiology) == TreeState.none)
    check("flown in: every instance outside Cardiology fades",
          drawn.filter { !nodes.within(cardiology, $0) }.allSatisfy { state($0) == TreeState.faded })
    check("the whole tree fades nothing",
          drawn.allSatisfy { TreeState.of(nodes, $0, hidden: false, chosen: -1, linked: false, focus: 0) == TreeState.none })
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
