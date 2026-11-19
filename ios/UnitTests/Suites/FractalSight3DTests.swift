import Foundation

// S12, the 3D Fractal's frame, camera and names (FractalSight.swift,
// FractalNames3D.swift), as Android's FractalSightTest and FractalNamesTest:
// a frame never draws more than the Graphics tier's budget, however big the
// tree, and looks at a bounded number of nodes; All ideas and its hubs show at
// the first view and flying in to Cardiology opens it and dims the rest; links
// are drawn once; the filter hides. No two names overlap and no name covers a
// named dot or one as big as its own, on a phone, a tablet and an open
// foldable, whole, turned 40 and 120 degrees, and flown in to Cardiology, on
// the concept's sample and on the same sample grown from a store.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let url = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("tools/fractal_fixture.json")
let fx = (try? JSONSerialization.jsonObject(with: Data(contentsOf: url))) as? [String: Any] ?? [:]
func num(_ a: Any) -> Int { (a as? NSNumber)?.intValue ?? -1 }
let t = fx["tree"] as? [String: Any] ?? [:]
let parents = (t["parent"] as? [Any] ?? []).map(num)
let kinds = (t["kind"] as? [Any] ?? []).map(num)
let titles = t["title"] as? [String] ?? []
check("the fixture reads", parents.count == 149 && titles.count == 149)
let linkEnds = (fx["links"] as? [Any] ?? []).flatMap { ($0 as? [Any] ?? []).map(num) }

let concept = FractalTree(parent: parents, kind: kinds.map { UInt8($0) }, ids: [String](repeating: "", count: parents.count),
                          titles: titles, region: [Int](repeating: -1, count: parents.count))
// the same sample as the app grows it from a store: each folder's folders, then its ideas, oldest first
let made: Int64 = 1_793_000_000_000
let store: FractalTree = {
    var folders: [FractalTree.Folder] = [], ideas: [FractalTree.Idea] = []
    for i in 1..<concept.count {
        let p: String? = concept.parent[i] == 0 ? nil : "p\(concept.parent[i])"
        if concept.childCount[i] > 0 {
            folders.append(FractalTree.Folder(id: "p\(i)", parentId: p, name: concept.titles[i], created: made + Int64(i)))
        } else {
            ideas.append(FractalTree.Idea(id: "p\(i)", folderId: p, title: concept.titles[i], created: made + Int64(i) * 60_000))
        }
    }
    return FractalTree.of(folders: folders, ideas: ideas, hubTitle: "All ideas")
}()
let models = [("concept", FractalModel(concept, FractalLayout3D(concept), linkEnds: linkEnds)),
              ("store", FractalModel(store, FractalLayout3D(store)))]

/// A map `width` by `height` points under `top` and over `bottom` of chrome, naming at least `least` at each pose.
struct Window { let name: String; let width: Float; let height: Float; let top: Float; let bottom: Float; var least = 6 }
let windows = [Window(name: "phone", width: 390, height: 844, top: 100, bottom: 160),
               Window(name: "phone, first visit", width: 390, height: 844, top: 100, bottom: 340),
               Window(name: "tablet", width: 1194, height: 834, top: 80, bottom: 80),
               Window(name: "foldable", width: 884, height: 736, top: 80, bottom: 100),
               // a foldable's map pane beside the Sort list, over the first visit's card, as Android's emulator drew it
               // (run 44398433580): only All ideas and its four top folders are crisp
               Window(name: "foldable pane, first visit", width: 505, height: 677, top: 107, bottom: 282, least: 5),
               // the iPhone's map under the strip and over the first visit's card, its whole view free of Back's row,
               // as FractalMapRenderTests draws it (run 44449703457 named 5 there with Back's row kept)
               Window(name: "iPhone, first visit", width: 402, height: 660, top: 22, bottom: 343)]
let style = TreeNames3D.Style()
let rad: (Float) -> Float = { $0 * Float.pi / 180 }

/// The map's own poses: the first view and a fly-in framed to what they draw (FractalFit.drawn), as FractalHost frames them.
func camera(_ m: FractalModel, _ w: Window, yaw: Float, into: Int, clear: [CGRect] = []) -> GraphPerfCamera {
    let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
    let fitting = FractalSight(m)
    var c = FractalCamera3D.whole(m, width: w.width, height: w.height, insets: insets, sight: fitting)
    c.orbit(yaw: yaw, pitch: 0)
    if into > 0 {
        fitting.focus = into
        c = FractalCamera3D.flyIn(m, into, from: c, width: w.width, height: w.height,
                                  insets: TreeCamera3D.around(insets, clear: clear, height: w.height), sight: fitting)
    }
    return c
}

func frame(_ m: FractalModel, _ s: FractalSight, _ w: Window, yaw: Float, into: Int, clear: [CGRect] = []) {
    let c = camera(m, w, yaw: yaw, into: into, clear: clear)
    s.focus = max(into, 0)
    s.step(FractalCamera3D.viewProj(m, c, width: w.width, height: w.height), focal: c.focal(viewHeight: w.height),
           width: w.width, height: w.height)
}

func names(_ m: FractalModel, _ w: Window, yaw: Float, into: Int, held: [Int: Int] = [:],
           clear: [CGRect] = []) -> ([FractalScreenNode], [TreePlacedName]) {
    let s = FractalSight(m)
    frame(m, s, w, yaw: yaw, into: into, clear: clear)
    let screen = FractalCamera3D.screen(s)
    let measure: (Int, Bool, Float) -> (width: Float, height: Float) = { i, bold, size in
        (Float(TreeNames3D.label(m.tree.titles[i]).count) * size * (bold ? 0.6 : 0.56), size * 1.3)
    }
    let placed = FractalNames3D.layout(screen, m.tree, style: style, measure: measure, width: w.width, height: w.height,
                                       top: w.top, bottom: w.bottom, held: held, focus: max(into, 0), clear: clear)
    return (screen, placed)
}

/// Back's place under the top chrome (flown in) and the round tools' column, Filter over Recentre, over the foot, as FractalMapView keeps them.
func chrome(_ w: Window, back: Bool) -> [CGRect] {
    let g: CGFloat = 16, t: CGFloat = 44
    let width = CGFloat(w.width), foot = CGFloat(w.height - w.bottom)
    let tools = CGRect(x: width - t - 2 * g, y: foot - 2 * t - 3 * g, width: t + 2 * g, height: 2 * t + 3 * g)
    return back ? [CGRect(x: g, y: CGFloat(w.top) + g, width: t, height: t), tools] : [tools]
}

// the budget
check("budget: High 380, Smooth 300, short of memory 220",
      FractalModel.budgetFor(.high) == 380 && FractalModel.budgetFor(.smooth) == 300 && FractalModel.budgetFor(.high, lowMemory: true) == 220)

for (name, m) in models {
    let tree = m.tree
    let cardiology = tree.titles.firstIndex(of: "Cardiology") ?? -1
    check("\(name): the sample has Cardiology", cardiology > 0)
    for w in windows {
        for (what, yaw, into) in [("whole", Float(0), -1), ("orbited 40", rad(40), -1), ("orbited 120", rad(120), -1),
                                  ("Cardiology", Float(0), cardiology), ("Cardiology orbited 40", rad(40), cardiology)] {
            let (screen, placed) = names(m, w, yaw: yaw, into: into)
            let label = "\(name) \(w.name) \(what)"
            check("\(label): dots on screen (\(screen.count))", screen.count > 20)
            check("\(label): names placed (\(placed.count))", placed.count >= w.least, placed.map { tree.titles[$0.index] }.joined(separator: ", "))
            check("\(label): no two names overlap", !TreeNames3D.overlaps(placed))
            check("\(label): no name covers a named dot or a bigger one", !FractalNames3D.coversADot(placed, screen))
            check("\(label): every name inside the view and off the chrome", placed.allSatisfy { p in
                p.left >= style.edge - 0.01 && p.right <= w.width - style.edge + 0.01 && p.top >= w.top - 0.01 && p.bottom <= w.height - w.bottom + 0.01 })
            check("\(label): every name has its dot", placed.allSatisfy { p in screen.contains { $0.index == p.index && $0.crisp } })
            // the review of S12's stills: a far row's name stood nearer another dot than its own, and read as that one's
            let near = FractalNames3D.nearest(placed, screen, tree, leastLeaf: style.leastLeaf)
            check("\(label): every name's nearest dot is its own", near == placed.map(\.index),
                  zip(placed, near).filter { $0.0.index != $0.1 }.map { "\(tree.titles[$0.0.index]) by \($0.1 >= 0 ? tree.titles[$0.1] : "none")" }.joined(separator: ", "))
            // a tap on a name lands on its part, before any dot under it
            check("\(label): a tap on a name lands on its part", placed.allSatisfy { p in
                FractalNames3D.hit(screen, placed, x: p.x, y: p.y, least: 44) == p.index
                    && FractalNames3D.hit(screen, placed, x: p.x + p.width / 2 - 1, y: p.y, least: 44) == p.index })
            // S12's iPad UI test: a tap in a name's touch target, off its words, fell to a dot by it. Each way
            // round a name, just inside its target, a tap lands on it, or on a name whose target holds it nearer
            check("\(label): a tap in a name's touch target lands on a name", placed.allSatisfy { p in
                let w = max(44, p.width) / 2 - 0.5, h = max(44, p.height) / 2 - 0.5
                return [(p.x, p.y + h), (p.x, p.y - h), (p.x + w, p.y), (p.x - w, p.y), (p.x + w, p.y + h)].allSatisfy { t in
                    let got = FractalNames3D.hit(screen, placed, x: t.0, y: t.1, least: 44)
                    let others = placed.filter { $0.index != p.index
                        && abs(t.0 - $0.x) * 2 <= max(44, $0.width) && abs(t.1 - $0.y) * 2 <= max(44, $0.height) }
                    return got == p.index || others.contains { $0.index == got }
                } })
            // VoiceOver reaches every hub drawn in the focus, named or not, each a touch target each way at least
            let targets = FractalNames3D.targets(screen, placed, tree, focus: max(into, 0), least: 44)
            let hubsDrawn = Set(screen.filter { FractalNames3D.hub(tree, $0.index) && (into <= 0 || tree.holds(into, $0.index)) }.map(\.index))
            check("\(label): VoiceOver reaches every hub drawn in the focus (\(hubsDrawn.count))",
                  hubsDrawn.isSubset(of: Set(targets.map(\.index))) && Set(placed.map(\.index)).isSubset(of: Set(targets.map(\.index))))
            check("\(label): every VoiceOver element a touch target", targets.allSatisfy { $0.width >= 44 && $0.height >= 44 })
        }
        // the chrome's boxes: no name under Back or the round tools, at the whole view and flown in
        for (what, into) in [("whole", -1), ("Cardiology", cardiology)] {
            let boxes = chrome(w, back: into > 0)
            let (_, placed) = names(m, w, yaw: 0, into: into, clear: boxes)
            let under = placed.filter { p in boxes.contains { CGRect(x: CGFloat(p.left), y: CGFloat(p.top), width: CGFloat(p.width), height: CGFloat(p.height)).intersects($0) } }
            check("\(name) \(w.name) \(what): no name under Back or the tools", under.isEmpty, under.map { tree.titles[$0.index] }.joined(separator: ", "))
        }
        // each pose names by itself, as the map does (no spot held from the last pose, so stills repeat). At 120
        // degrees Cardiology's cluster stands in front of All ideas, and its name, nearer Cardiology's dots than
        // its own wherever it could go, is left out rather than read as theirs (review item 9 of S12 repair 1)
        for (what, deg, want) in [("whole", Float(0), ["All ideas", "Examples", "Cardiology"]),
                                  ("orbited 40", Float(40), ["All ideas", "Cardiology"]), ("orbited 120", Float(120), [String]())] {
            let (screen, placed) = names(m, w, yaw: rad(deg), into: -1)
            let got = placed.map { tree.titles[$0.index] }
            let label = "\(name) \(w.name) turned to \(what)"
            check("\(label): \(want) named", want.allSatisfy { got.contains($0) }, "\(got)")
            check("\(label): no two names overlap", !TreeNames3D.overlaps(placed))
            check("\(label): no name covers a named dot or a bigger one", !FractalNames3D.coversADot(placed, screen))
            check("\(label): every name's nearest dot is its own",
                  FractalNames3D.nearest(placed, screen, tree, leastLeaf: style.leastLeaf) == placed.map(\.index))
            // the same pose names the same, whatever was named before it
            let again = names(m, w, yaw: rad(deg), into: -1).1
            check("\(label): the same pose names the same", again == placed)
        }
        let whole = names(m, w, yaw: 0, into: -1).1.map { tree.titles[$0.index] }
        check("\(name) \(w.name): All ideas, Examples and Cardiology named at the first view",
              ["All ideas", "Examples", "Cardiology"].allSatisfy { whole.contains($0) }, "\(whole)")
        let flown = names(m, w, yaw: 0, into: cardiology).1
        let inside = flown.filter { tree.holds(cardiology, $0.index) }
        check("\(name) \(w.name): flown in, Cardiology and its parts named (\(inside.count))",
              inside.count >= 6 && flown.contains { $0.index == cardiology })
    }

    // the frame
    let phone = windows[0]
    let s = FractalSight(m)
    frame(m, s, phone, yaw: 0, into: -1)
    let root = s.slotOf(0)
    check("\(name): All ideas drawn and lit", root >= 0 && s.hub[root] > 0.5 && s.nameable[root])
    check("\(name): a dot's line goes to its own hub", (0..<s.count).allSatisfy { k in
        let p = s.parentSlot(k); return p < 0 || tree.parent[s.node[k]] == s.node[p] })
    let before = (0..<s.count).filter { tree.holds(cardiology, s.node[$0]) && s.nameable[$0] }.count
    frame(m, s, phone, yaw: 0, into: cardiology)
    let after = (0..<s.count).filter { tree.holds(cardiology, s.node[$0]) && s.nameable[$0] }.count
    check("\(name): flying in opens Cardiology (\(before) whole, \(after) in)", after > before && after >= 8)
    // nothing outside it: no other hub's glow at the edge, no line running off
    check("\(name): nothing outside Cardiology drawn", (0..<s.count).allSatisfy { tree.holds(cardiology, s.node[$0]) }
          && (0..<s.linkCount).allSatisfy { tree.holds(cardiology, s.node[s.linkFrom[$0]]) && tree.holds(cardiology, s.node[s.linkTo[$0]]) })

    // no dot is a speck, and every hub reads as one at every depth (the lead's review, run 44408042504)
    let hubLeast = FractalSight.least + FractalSight.hubMore * FractalSight.hubLeast
    for (what, into) in [("whole", -1), ("Cardiology", cardiology)] {
        frame(m, s, phone, yaw: 0, into: into)
        for k in 0..<s.count {
            let i = s.node[k]
            let label = "\(name) \(what): \(tree.titles[i])"
            if tree.childCount[i] > 0 {
                if s.hub[k] < FractalSight.hubLeast - 1e-6 || s.radius[k] < hubLeast - 1e-3 {
                    check("\(label): a hub lit \(s.hub[k]), \(s.radius[k]) pt", false)
                }
            } else if s.radius[k] < FractalSight.least - 1e-3 || s.hub[k] != 0 {
                check("\(label): an idea \(s.radius[k]) pt, lit \(s.hub[k])", false)
            }
        }
        check("\(name) \(what): no speck, every hub a hub (\(s.count) dots)", s.count > 20)
    }
    // at the whole view, each top folder is a hub with its ring of parts, and its sub-hubs theirs
    frame(m, s, phone, yaw: 0, into: -1)
    let subHubs = (0..<s.count).filter { tree.depth[s.node[$0]] == 2 && tree.childCount[s.node[$0]] > 0 }
    let third = (0..<s.count).filter { tree.depth[s.node[$0]] == 3 && s.alpha[$0] >= 0.5 }
    check("\(name): sub-hubs drawn at the whole view, lit as hubs (\(subHubs.count))",
          subHubs.count >= 3 && subHubs.allSatisfy { s.hub[$0] >= FractalSight.hubLeast - 1e-6 })
    check("\(name): their parts drawn and seen (\(third.count)), each off its drawn hub",
          third.count >= 10 && third.allSatisfy { s.parentSlot($0) >= 0 })
    let examples = tree.titles.firstIndex(of: "Examples") ?? -1
    s.shown = (0..<tree.count).map { $0 != examples }
    frame(m, s, phone, yaw: 0, into: -1)
    check("\(name): the filter hides Examples and its parts", s.slotOf(examples) == -1
          && (0..<s.count).allSatisfy { !tree.holds(examples, s.node[$0]) })
}

// links: once each, between drawn dots
let (_, conceptModel) = models[0]
let ls = FractalSight(conceptModel)
frame(conceptModel, ls, windows[0], yaw: 0, into: -1)
var seen = Set<Int>()
var once = true
for e in 0..<ls.linkCount {
    let a = ls.node[ls.linkFrom[e]], b = ls.node[ls.linkTo[e]]
    if !seen.insert(min(a, b) * 1000 + max(a, b)).inserted { once = false }
}
check("links drawn once each (\(ls.linkCount))", ls.linkCount > 0 && ls.linkCount <= FractalRules.linkBudget && once)
// lines seen on the sky, not hairlines: at least a point wide, a tree line near half its dot's brightness, a link a quarter
check("lines: \(FractalSight.lineWidth) pt wide, tree line \(FractalSight.treeLine), link \(FractalSight.linkLine)",
      FractalSight.lineWidth >= 1 && FractalSight.treeLine >= 0.4 && FractalSight.linkLine >= 0.25
      && (0..<ls.linkCount).allSatisfy { e in
          abs(ls.linkAlpha[e] - min(ls.alpha[ls.linkFrom[e]], ls.alpha[ls.linkTo[e]]) * FractalSight.linkLine) < 1e-6 })

// the framing (FractalFit): the first view and a fly-in to Cardiology show every framed mark inside the
// room the chrome leaves, less the margin, as large as fits and in the room's middle, as Android's FractalFitTest
let fitLayout = conceptModel.layout
let fitTree = conceptModel.tree
let fitCardiology = fitTree.titles.firstIndex(of: "Cardiology") ?? -1
for w in windows {
    let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
    let margin = FractalFit.margin(width: w.width, height: w.height, insets: insets)
    for (what, hub, levels, yaw) in [("whole", 0, FractalFit.wholeLevels, Float(0)), ("whole at 40", 0, FractalFit.wholeLevels, rad(40)),
                                     ("Cardiology", fitCardiology, FractalFit.inLevels, Float(0)),
                                     ("Cardiology at 120", fitCardiology, FractalFit.inLevels, rad(120))] {
        var c = GraphPerfCamera()
        c.yaw = yaw; c.pitch = FractalCamera3D.pitch
        let nodes = FractalFit.nodes(fitTree, hub, levels: levels)
        c = FractalFit.fit(fitLayout, nodes, from: c, width: w.width, height: w.height, insets: insets)
        let f = c.focal(viewHeight: w.height)
        var lo = SIMD2<Float>(.infinity, .infinity), hi = SIMD2<Float>(-.infinity, -.infinity)
        var ahead = true
        let eye = SIMD3<Double>(c.eye), fw = SIMD3<Double>(c.forward), rt = SIMD3<Double>(c.right), upv = SIMD3<Double>(c.up)
        for i in nodes {
            let d = SIMD3<Double>(fitLayout.pos[3 * i], fitLayout.pos[3 * i + 1], fitLayout.pos[3 * i + 2]) - eye
            let depth = (d * fw).sum()
            if depth <= 0 { ahead = false; continue }
            let px = Float(Double(w.width) / 2 + (d * rt).sum() * Double(f) / depth)
            let py = Float(Double(w.height) / 2 - (d * upv).sum() * Double(f) / depth)
            let r = Float(FractalFit.mark(fitLayout, i) * Double(f) / depth)
            lo = SIMD2(min(lo.x, px - r), min(lo.y, py - r)); hi = SIMD2(max(hi.x, px + r), max(hi.y, py + r))
        }
        let left = margin, right = w.width - margin, top = w.top + margin, bottom = w.height - w.bottom - margin
        let label = "fit \(w.name) \(what): box \(lo) \(hi) in [\(left), \(top), \(right), \(bottom)]"
        let e: Float = 1.5
        check("\(label): inside", ahead && lo.x >= left - e && hi.x <= right + e && lo.y >= top - e && hi.y <= bottom + e)
        check("\(label): as large as fits", max((hi.x - lo.x) / (right - left), (hi.y - lo.y) / (bottom - top)) >= 0.98)
        check("\(label): centred", abs((lo.x + hi.x) / 2 - (left + right) / 2) <= 2 && abs((lo.y + hi.y) / 2 - (top + bottom) / 2) <= 2)
    }
}
// what is framed is what is drawn (FractalFit.drawn; the review of S12's stills: a third level's dots reached the
// phone's edge flown in, and the iPad's left dots its divider): on both trees of tools/fractal_fixture3d.json, after
// the first view and a fly-in to each top hub, from 0 and 120 degrees, every dot the frame draws, with its glow as
// drawn, is inside the room the chrome leaves less the margin. As Android's FractalFitTest.
let fixture3dURL = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("tools/fractal_fixture3d.json")
let fixture3d = (try? JSONSerialization.jsonObject(with: Data(contentsOf: fixture3dURL))) as? [String: Any] ?? [:]
let fixtureTrees: [(String, FractalTree)] = (fixture3d["trees"] as? [[String: Any]] ?? []).map { t in
    let parent = (t["parent"] as? [Any] ?? []).map(num)
    var kids = [Int](repeating: 0, count: parent.count)
    for i in 1..<max(parent.count, 1) { kids[parent[i]] += 1 }
    let kind: [UInt8] = parent.indices.map { $0 == 0 ? FractalTree.hub : (kids[$0] > 0 ? FractalTree.folder : FractalTree.idea) }
    return (t["name"] as? String ?? "?", FractalTree(parent: parent, kind: kind, ids: [String](repeating: "", count: parent.count),
                                                    titles: parent.indices.map { "Part \($0)" }, region: [Int](repeating: -1, count: parent.count)))
}
check("fit drawn: the 3D fixture's two trees read", fixtureTrees.count == 2)
for (name, tree) in fixtureTrees {
    let m = FractalModel(tree, FractalLayout3D(tree))
    let fitting = FractalSight(m), seen = FractalSight(m)
    let hubs = (1..<tree.count).filter { tree.parent[$0] == 0 && tree.childCount[$0] > 0 }
    for w in windows {
        let insets: (top: Float, left: Float, bottom: Float, right: Float) = (w.top, 0, w.bottom, 0)
        let margin = FractalFit.margin(width: w.width, height: w.height, insets: insets)
        let left = margin, right = w.width - margin, top = w.top + margin, bottom = w.height - w.bottom - margin
        var poses: [(String, Int, GraphPerfCamera)] = []
        fitting.focus = 0
        let whole = FractalCamera3D.whole(m, width: w.width, height: w.height, insets: insets, sight: fitting)
        poses.append(("whole", 0, whole))
        for deg in [Float(0), Float(120)] {
            var from = whole
            from.orbit(yaw: rad(deg), pitch: 0)
            for hub in hubs {
                fitting.focus = hub
                poses.append(("flown in to \(hub) at \(Int(deg))", hub,
                              FractalCamera3D.flyIn(m, hub, from: from, width: w.width, height: w.height, insets: insets, sight: fitting)))
            }
        }
        var bad: [String] = [], off: [String] = []
        // what FractalMapRenderTests asks of the app: the drawn box in the room's middle, within 3% of it or 4 points
        let slackX = max(4, (right - left) * 0.03), slackY = max(4, (bottom - top) * 0.03)
        for (what, focus, c) in poses {
            seen.focus = focus
            seen.step(FractalCamera3D.viewProj(m, c, width: w.width, height: w.height), focal: c.focal(viewHeight: w.height),
                      width: w.width, height: w.height)
            let e: Float = 1.5
            var lo = SIMD2<Float>(.infinity, .infinity), hi = -lo
            for k in 0..<seen.count {
                let g = seen.radius[k] * (1 + FractalSight.glowReach * seen.hub[k])
                let x = seen.x[k], y = seen.y[k]
                lo = pointwiseMin(lo, SIMD2(x - g, y - g)); hi = pointwiseMax(hi, SIMD2(x + g, y + g))
                if !(x - g >= left - e && x + g <= right + e && y - g >= top - e && y + g <= bottom + e) {
                    bad.append("\(what): node \(seen.node[k]) at (\(x), \(y)) glow \(g)")
                }
            }
            let mid = (lo + hi) / 2
            if abs(mid.x - (left + right) / 2) > slackX || abs(mid.y - (top + bottom) / 2) > slackY {
                off.append("\(what): box \(lo) to \(hi)")
            }
        }
        check("fit drawn \(name) \(w.name): every drawn dot and glow inside [\(left), \(top), \(right), \(bottom)] (\(poses.count) poses)",
              bad.isEmpty, bad.prefix(3).joined(separator: "; "))
        check("fit drawn \(name) \(w.name): what is drawn centred in the room (\(poses.count) poses)",
              off.isEmpty, off.prefix(3).joined(separator: "; "))
    }
}

// VoiceOver reads a hub as a folder but All ideas as its name, as TalkBack (FractalNames.spokenAsFolder)
check("All ideas is read as its name, Cardiology as a folder, an idea as itself",
      !FractalNames3D.spokenAsFolder(fitTree, 0) && FractalNames3D.spokenAsFolder(fitTree, fitCardiology)
      && !FractalNames3D.spokenAsFolder(fitTree, (1..<fitTree.count).first { fitTree.childCount[$0] == 0 && fitTree.kind[$0] == FractalTree.idea } ?? 0))

let fitNodes = FractalFit.nodes(fitTree, fitCardiology, levels: 2)
check("fit: Cardiology's cluster two levels deep, level by level, within the budget",
      fitNodes.first == fitCardiology && fitNodes.allSatisfy { fitTree.holds(fitCardiology, $0) && fitTree.depth[$0] - fitTree.depth[fitCardiology] <= 2 }
      && fitNodes.count == (0..<fitTree.count).filter { fitTree.holds(fitCardiology, $0) && fitTree.depth[$0] - fitTree.depth[fitCardiology] <= 2 }.count
      && FractalFit.nodes(fitTree, 0, levels: 3, most: 7).count == 7
      && zip(fitNodes, fitNodes.dropFirst()).allSatisfy { fitTree.depth[$0] <= fitTree.depth[$1] })

// a hundred thousand ideas: the budget, the cost guards and the time a frame takes
var parent: [Int] = [-1]
for _ in 0..<200 {
    let top = parent.count; parent.append(0)
    for _ in 0..<4 { let sub = parent.count; parent.append(top); for _ in 0..<125 { parent.append(sub) } }
}
let n = parent.count
var kids = [Int](repeating: 0, count: n)
for i in 1..<n { kids[parent[i]] += 1 }
let big = FractalTree(parent: parent, kind: (0..<n).map { $0 == 0 ? FractalTree.hub : (kids[$0] > 0 ? FractalTree.folder : FractalTree.idea) },
                      ids: [String](repeating: "", count: n), titles: [String](repeating: "Part", count: n),
                      region: [Int](repeating: -1, count: n))
let bigLayout = FractalLayout3D(big)
for budget in [380, 300, 220] {
    let m = FractalModel(big, bigLayout, budget: budget)
    let s = FractalSight(m)
    var held = true
    for (yaw, into) in [(Float(0), -1), (Float(1.2), -1), (Float(0.4), big.children[big.childStart[0]])] {
        frame(m, s, windows[0], yaw: yaw, into: into)
        if !(1...budget).contains(s.count) || s.looked > FractalSight.lookMost || s.pushed > FractalSight.pushMost { held = false }
    }
    check("100,000 ideas at a budget of \(budget): within it, and the cost guards hold", held)
}
let m = FractalModel(big, bigLayout)
let s = FractalSight(m)
let c = camera(m, windows[0], yaw: 0.3, into: -1)
let vp = FractalCamera3D.viewProj(m, c, width: 390, height: 844)
let f = c.focal(viewHeight: 844)
for _ in 0..<50 { s.step(vp, focal: f, width: 390, height: 844) }
let start = Date()
for _ in 0..<200 { s.step(vp, focal: f, width: 390, height: 844) }
let ms = Date().timeIntervalSince(start) * 1000 / 200
// under 1 ms built as CI builds it (-O), as Android's; an unoptimised build here gets ten times that
let most = _isDebugAssertConfiguration() ? 10.0 : 1.0
check("a frame of 100,000 ideas takes \(String(format: "%.3f", ms)) ms, under \(Int(most))", ms < most)

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
