import Foundation

// S11: the Tree's frame, as Android's TreeSceneTest checks it, on the
// concept's sample (tree-fixture.json): names never overlap and keep 8 apart
// side by side and 4 above and below; a bough's name sits on its own bough; a
// tap zooms until the part is the whole tree and Back comes out; an idea opens
// the part it grows on; Reduce Motion cuts; a pinch settles; vines and Show
// links; the filter; the budget; and the cost of a layout and a frame (held
// in -O builds, as CI compiles the suites).

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let fixtureURL = URL(fileURLWithPath: "android/app/src/test/resources/tree/tree-fixture.json")
let fixture = (try? JSONSerialization.jsonObject(with: Data(contentsOf: fixtureURL))) as? [String: Any] ?? [:]
let t0 = (fixture["trees"] as? [[String: Any]] ?? []).first ?? [:]
let parent0 = (t0["parent"] as? [Int]) ?? [], title0 = (t0["title"] as? [String]) ?? [], folder0 = (t0["folder"] as? [Int]) ?? []
var tone0 = Array(repeating: -1, count: parent0.count)
var tops = 0
for i in 1..<max(1, parent0.count) where i < parent0.count {
    tone0[i] = parent0[i] == 0 ? (folder0[i] == 1 ? { tops += 1; return tops - 1 }() : -1) : tone0[parent0[i]]
}
let sample = NestNodes(ids: parent0.indices.map { "\($0)" }, parent: parent0, titles: title0, folder: folder0.map { $0 == 1 }, tone: tone0)
check("the sample reads", sample.size > 100, "\(sample.size)")
func idx(_ title: String) -> Int { sample.titles.firstIndex(of: title) ?? -1 }

/// A sink that only counts.
final class Count: NestSink {
    var points = 0, fills = 0, strokes = 0, texts = 0
    func moveTo(_ x: Float, _ y: Float) { points += 1 }
    func lineTo(_ x: Float, _ y: Float) { points += 1 }
    func cubicTo(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x: Float, _ y: Float) { points += 1 }
    func close() {}
    func fill(_ color: Int, _ alpha: Float) { fills += 1 }
    func stroke(_ color: Int, _ width: Float, _ alpha: Float, _ round: Bool) { strokes += 1 }
    func text(_ node: Int, _ x: Float, _ y: Float, _ size: Float, _ bold: Bool, _ align: Int, _ ink: Int, _ halo: Int, _ haloWidth: Float) { texts += 1 }
}

func scene(_ nodes: NestNodes, _ w: Float = 390, _ h: Float = 844, links: NestLinks = .none) -> TreeScene {
    let s = TreeScene(nodes, TreeLayout(nodes), links)
    // Nunito's bold is about 0.6 of its size a letter
    for i in 0..<nodes.size { s.widths[0][i] = Float(nodes.titles[i].count) * 58; s.widths[1][i] = Float(nodes.titles[i].count) * 62 }
    s.target = 44
    s.setView(w, h, top: 120, bottom: 90)
    return s
}

func settleZoom(_ s: TreeScene, _ start: Double) -> Double {
    var t = start
    while s.animating { t += 8; s.draw(nil, now: t) }
    return t
}

func checkNames(_ s: TreeScene, _ what: String) {
    let b = (0..<s.labelCount).filter { s.labelNode($0) >= 0 }.map { (s.labelNode($0), s.labelBox($0)) }
    check("\(what): some names", !b.isEmpty)
    var close = 0
    for p in b.indices { for q in (p + 1)..<max(p + 1, b.count) where q < b.count {
        let a = b[p].1, c = b[q].1
        let apartX = max(c.0 - a.2, a.0 - c.2), apartY = max(c.1 - a.3, a.1 - c.3)
        if !(apartX >= TreeScene.gapX - 1e-3 || apartY >= TreeScene.gapY - 1e-3) { close += 1 }
    } }
    check("\(what): no two names closer than 8 side by side and 4 above and below", close == 0, "\(close) pairs")
    check("\(what): inside the view, below the strip", b.allSatisfy { $0.1.0 >= 4 && $0.1.2 <= s.viewW - 4 && $0.1.1 >= s.insetTop + 2 && $0.1.3 <= s.viewH - s.insetBottom - 4 })
    check("\(what): each part named once", Set(b.map(\.0)).count == b.count)
}

// names, on a phone, a tablet and an open foldable, whole and one level in
for (w, h) in [(Float(390), Float(844)), (1280, 800), (841, 701)] {
    let s = scene(sample, w, h)
    s.draw(Count(), now: 0)
    checkNames(s, "whole, \(Int(w))x\(Int(h))")
    s.go(idx("Cardiology"), now: 0)
    let t = settleZoom(s, 0)
    s.draw(Count(), now: t)
    checkNames(s, "Cardiology, \(Int(w))x\(Int(h))")
}

// each bough's name is on its own bough
for folder in [0, idx("Examples")] {
    let s = scene(sample)
    s.go(folder, now: 0, instant: true)
    s.draw(Count(), now: 0)
    var boughs = 0, off = 0
    for q in 0..<s.labelCount {
        let i = s.labelNode(q)
        if i < 0 || !s.labelBold(q) { continue }
        let box = s.labelBox(q)
        let cx = (box.0 + box.2) / 2, cy = (box.1 + box.3) / 2
        let on = TreeScene.onBough.contains { j in abs(s.pointX(i, j) - cx) < 0.01 && abs(s.pointY(i, j) - cy) < 0.01 }
        if !on || sample.childCount[i] == 0 { off += 1 }
        boughs += 1
    }
    check("bough names on their own boughs (\(sample.titles[folder]))", boughs >= 3 && off == 0, "\(boughs) named, \(off) off")
}

// a tap zooms until the part is the whole tree, and Back comes out
do {
    let s = scene(sample)
    s.draw(Count(), now: 0)
    let examples = idx("Examples")
    if let q = (0..<s.labelCount).first(where: { s.labelNode($0) == examples }) {
        let box = s.labelBox(q)
        let hit = s.hitTest((box.0 + box.2) / 2, (box.1 + box.3) / 2)
        check("tap: its name answers", hit == examples)
        check("tap: 21 points out from its middle still answers (44 the least)", s.hitTest((box.0 + box.2) / 2, (box.1 + box.3) / 2 + 21) == examples)
        check("tap: it zooms, eased", s.activate(hit, now: 0) == examples && s.animating)
        let t = settleZoom(s, 0)
        let want = s.frameOf(examples, NestCamera())
        check("tap: framed as the whole was", s.focus == examples && abs(want.z - s.cam.z) < 1e-9 && abs(want.x - s.cam.x) < 1e-9 && abs(want.r - s.cam.r) < 1e-9)
        s.draw(Count(), now: t)
        var empty = 0
        for gx in 0..<40 { for gy in 0..<80 {
            if s.hitTest(Float(gx) * s.viewW / 40, s.insetTop + Float(gy) * (s.viewH - s.insetTop) / 80) == -1 { empty += 1 }
        } }
        check("tap: room to tap that is no part", empty > 400, "\(empty)")
        check("back: goes", s.back(now: t))
        _ = settleZoom(s, t)
        check("back: at the whole", s.focus == 0)
        check("back: the whole tree has no Back", !s.back(now: t + 2000))
    } else { check("tap: Examples is named", false) }
}

// an idea opens the part it grows on
do {
    let s = scene(sample)
    s.draw(Count(), now: 0)
    let idea = idx("Venous hum"), varix = sample.parent[max(0, idea)]
    check("idea: a leaf in Saphena varix", idea > 0 && sample.childCount[idea] == 0 && sample.titles[varix] == "Saphena varix")
    check("idea: opens its part", s.activate(idea, now: 0) == varix)
    _ = settleZoom(s, 0)
    check("idea: in the part in view, moves nothing", s.activate(idea, now: 5000) == -1)
}

// Reduce Motion cuts straight there
do {
    let s = scene(sample)
    s.reduced = true
    s.draw(Count(), now: 0)
    let f = idx("Femoral")
    s.go(f, now: 0)
    let want = s.frameOf(f, NestCamera())
    check("reduce motion: no zoom in flight, there at once", !s.animating && abs(want.z - s.cam.z) < 1e-9)
}

// a pinch settles on the part that fills the view
do {
    let s = scene(sample)
    s.draw(Count(), now: 0)
    let c = idx("Cardiology")
    s.cam.set(s.frameOf(c, NestCamera()))
    s.settle()
    check("pinch: settles on Cardiology", s.focus == c)
    s.zoomAbout(195, 400, 0.2)
    s.settle()
    check("pinch: out to the whole", s.focus == 0)
}

// links are vines; Show links keeps only the chosen idea's; the filter hides
do {
    let a = idx("Heart failure"), b = idx("Groin hernia"), c = idx("Pericarditis")
    let s = scene(sample, links: NestLinks(a: [a, c], b: [b, b]))
    let all = Count(); s.draw(all, now: 0)
    s.linksOf = idx("Saphena varix")
    let none = Count(); s.draw(none, now: 0)
    check("links: no vine of its own, one stroke fewer", none.strokes == all.strokes - 1)
    // the filter, on a tree with one vine, Heart failure's
    let f = scene(sample, links: NestLinks(a: [a], b: [b]))
    let whole = Count(); f.draw(whole, now: 0)
    let card = idx("Cardiology")
    for i in 0..<sample.size where sample.within(card, i) { f.hidden[i] = true }
    let some = Count(); f.draw(some, now: 0)
    check("filter: nothing hidden drawn", (0..<sample.size).allSatisfy { !sample.within(card, $0) || !f.isDrawn($0) })
    check("filter: the rest drawn", f.isDrawn(idx("Examples")) && some.points < whole.points)
    check("filter: no name of a hidden part", (0..<f.labelCount).allSatisfy { f.labelNode($0) < 0 || !f.hidden[f.labelNode($0)] })
    check("filter: its vine goes with it", some.strokes == whole.strokes - 1)
}

// the budget and the cost: the concept's ?stress, three more under every leaf
func stress(_ levels: Int) -> NestNodes {
    var parent: [Int] = [], title: [String] = [], folder: [Bool] = []
    for i in 0..<sample.size { parent.append(sample.parent[i]); title.append(sample.titles[i]); folder.append(sample.folder[i]) }
    func grow(_ p: Int, _ d: Int) { for q in 1...3 { parent.append(p); title.append("Part \(q)"); folder.append(false); if d > 1 { grow(parent.count - 1, d - 1) } } }
    for j in 1..<sample.size where sample.childCount[j] == 0 { grow(j, levels) }
    let n = parent.count
    var kids = Array(repeating: [Int](), count: n)
    for i in 1..<n { kids[parent[i]].append(i) }
    var order = [0], h = 0
    while h < order.count { order.append(contentsOf: kids[order[h]]); h += 1 }
    var at = Array(repeating: 0, count: n)
    for (q, i) in order.enumerated() { at[i] = q }
    return NestNodes(ids: (0..<n).map { "\(order[$0])" }, parent: (0..<n).map { $0 == 0 ? -1 : at[parent[order[$0]]] },
                     titles: (0..<n).map { title[order[$0]] }, folder: (0..<n).map { folder[order[$0]] }, tone: Array(repeating: -1, count: n))
}
let big = stress(3)
do {
    check("budget: a big tree", big.size > 3000, "\(big.size)")
    let s = scene(big)
    s.draw(Count(), now: 0)
    let whole = s.drawnCount
    s.zoomAbout(195, 500, 40)
    s.draw(Count(), now: 0)
    check("budget: never more than \(TreeScene.budget) parts drawn", (1...TreeScene.budget).contains(whole) && (1...TreeScene.budget).contains(s.drawnCount), "\(whole), \(s.drawnCount)")
}
do {
    func ms(_ a: UInt64, _ b: UInt64) -> Double { Double(b - a) / 1e6 }
    for _ in 0..<2 { _ = TreeLayout(big) }
    let l0 = DispatchTime.now().uptimeNanoseconds
    for _ in 0..<5 { _ = TreeLayout(big) }
    let layoutMs = ms(l0, DispatchTime.now().uptimeNanoseconds) / 5
    let s = scene(big)
    let sink = Count()
    let target = big.child(big.child(0, 0), 0)
    var t = 0.0
    for _ in 0..<300 { s.draw(sink, now: t); t += 8; if !s.animating { s.go(s.focus == 0 ? target : 0, now: t) } }
    var times: [Double] = []
    times.reserveCapacity(400)
    for _ in 0..<400 {
        let a = DispatchTime.now().uptimeNanoseconds
        s.draw(sink, now: t)
        times.append(ms(a, DispatchTime.now().uptimeNanoseconds))
        t += 8
        if !s.animating { s.go(s.focus == 0 ? target : 0, now: t) }
    }
    times.sort()
    let median = times[200], p95 = times[380]
    let line = "cost: layout of \(big.size) parts \(layoutMs) ms; a frame's work median \(median) ms, 95th \(p95) ms"
    if _isDebugAssertConfiguration() {
        print("note " + line + " (an unoptimised build: held only in -O, as CI runs)")
    } else {
        check(line, layoutMs < 120 && median < 2 && p95 < 4)
    }
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
