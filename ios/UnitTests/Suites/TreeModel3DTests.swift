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
      && light.leaves.count == light.leafCount * TreeModel3D.leafFloats)
check("the trunk first, its node kept", light.branches[11] == 0 && light.branches[4] == 1)
check("every instance names its node", (0..<light.branchCount).allSatisfy { k in
    let i = Int(light.branches[k * TreeModel3D.branchFloats + 11]); return i >= 0 && i < sample.size })
check("High draws finer than Smooth", TreeModel3D(sample, layout, colors: TreeColors(dark: false), tier: .smooth).segments < light.segments)
check("colours are the ramps' full step", light.branches[8] == Float((light.colors.barkRamp[sample.group[0]][8] >> 16) & 255) / 255)
let dark = TreeModel3D(sample, layout, colors: TreeColors(dark: true), tier: .high)
check("dark mode has its own bark", dark.branches[8] != light.branches[8])

// the meshes
let tube = TreeModel3D.tube(12), ball = TreeModel3D.ball(rings: 8, segments: 12)
check("the tube: two rings, two triangles a side", tube.vertices.count == 13 * 2 * 3 && tube.indices.count == 12 * 6
      && tube.indices.allSatisfy { Int($0) < 26 })
check("the ball: every index a vertex", ball.indices.allSatisfy { Int($0) < ball.vertices.count / 3 })
check("the ball is a unit ball", stride(from: 0, to: ball.vertices.count, by: 3).allSatisfy { o in
    let x = ball.vertices[o], y = ball.vertices[o + 1], z = ball.vertices[o + 2]
    return abs((x * x + y * y + z * z).squareRoot() - 1) < 1e-5 })

// the names: a phone, a tablet and an open foldable, in points
struct Window { let name: String; let width: Float; let height: Float; let top: Float; let bottom: Float }
let windows = [Window(name: "phone", width: 390, height: 844, top: 100, bottom: 160),
               Window(name: "tablet", width: 1194, height: 834, top: 80, bottom: 80),
               Window(name: "foldable", width: 884, height: 736, top: 80, bottom: 100)]
let style = TreeNames3D.Style()
func measure(_ i: Int, _ bold: Bool, _ size: Float) -> (width: Float, height: Float) {
    (Float(TreeNames3D.label(sample.titles[i]).count) * size * (bold ? 0.6 : 0.56), size * 1.3)
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

func view(_ w: Window, yaw: Float, into: Int) -> GraphPerfCamera {
    let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
    var c = TreeCamera3D.whole(light, width: w.width, height: w.height, insets: insets)
    c.orbit(yaw: yaw, pitch: 0)
    if into > 0 {
        let room = TreeCamera3D.around(insets, clear: chrome(w, flown: true), height: w.height)
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
func storeMeasure(_ i: Int, _ bold: Bool, _ size: Float) -> (width: Float, height: Float) {
    (Float(TreeNames3D.label(store.titles[i]).count) * size * (bold ? 0.6 : 0.56), size * 1.3)
}
let storeCardiology = store.titles.firstIndex(of: "Cardiology") ?? -1
// a phone and an iPad with the card up: the chrome's foot, the card's cover from the view's foot
let carded: [(String, Window, Float)] = [("phone", Window(name: "phone", width: 390, height: 700, top: 56, bottom: 60), 300),
                                         ("iPad", Window(name: "iPad", width: 1194, height: 760, top: 56, bottom: 60), 260)]
for (m, tree, measureOf, sampleOf) in [(light, "concept", measure, sample), (storeModel, "store", storeMeasure, store)]
        as [(TreeModel3D, String, (Int, Bool, Float) -> (width: Float, height: Float), NestNodes)] {
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
              placed.allSatisfy { sample.depth[$0.index] <= 1 || $0.spot <= TreeNames3D.aboveTipSpot })
        check("\(w.name) \(yaw): no leader runs under a name", placed.allSatisfy { p in
            guard let tip = p.lead else { return true }
            let line = TreeNames3D.Segment(a: SIMD2(tip.x < p.x ? p.left : p.right, p.y), b: tip, half: 1)
            return placed.allSatisfy { $0.index == p.index || !line.crosses($0.x, $0.y, $0.width, $0.height, gap: 0) }
        })
    }
}

// the first view fills the room the chrome and the card leave, with a small margin
func everyPoint(_ m: TreeModel3D) -> [SIMD3<Float>] {
    let l = m.layout
    var out: [SIMD3<Float>] = []
    for i in 0..<m.nodes.size {
        for t in [0, l.length[i]] {
            out.append(SIMD3(Float(l.base[3 * i] + l.dir[3 * i] * t), Float(l.base[3 * i + 1] + l.dir[3 * i + 1] * t),
                             Float(l.base[3 * i + 2] + l.dir[3 * i + 2] * t)))
        }
        out.append(SIMD3(Float(l.anchor[3 * i]), Float(l.anchor[3 * i + 1]), Float(l.anchor[3 * i + 2])))
    }
    return out
}
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
        check("\(tree) \(name): the first view fills the room", max(wide, tall) >= 0.86, "\(wide) x \(tall)")
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
    check("\(tree): the first view's yaw is Android's, 10 degrees left of front", abs(yaw * 180 / Float.pi + 10) < 1e-3, "\(yaw * 180 / Float.pi)")
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

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
