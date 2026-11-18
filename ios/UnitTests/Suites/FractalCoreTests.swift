import Foundation

// S12: the Fractal map's shared core against tools/fractal_fixture.json, the
// file Android's FractalCoreTest reads too. The layout must give back what the
// approved concept's own code made of its sample tree, and each frame (the dots
// drawn, the names and where they sit, where taps land) what the fixture
// recorded, so both platforms draw the same picture of the same data.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

func fixture() -> [String: Any] {
    let url = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("tools/fractal_fixture.json")
    guard let data = try? Data(contentsOf: url),
          let any = try? JSONSerialization.jsonObject(with: data), let fx = any as? [String: Any] else {
        check("fixture: tools/fractal_fixture.json reads", false, url.path)
        exit(1)
    }
    return fx
}

func num(_ a: Any?) -> Double { (a as? NSNumber)?.doubleValue ?? .nan }
func nums(_ a: Any?) -> [Double] { (a as? [Any] ?? []).map(num) }
func ints(_ a: Any?) -> [Int] { (a as? [Any] ?? []).map { Int(num($0)) } }

func makeTree(_ fx: [String: Any]) -> FractalTree {
    let t = fx["tree"] as! [String: Any]
    let p = ints(t["parent"]), k = ints(t["kind"]), titles = t["title"] as! [String]
    return FractalTree(parent: p, kind: k.map { UInt8($0) }, ids: [String](repeating: "", count: p.count),
                       titles: titles, region: [Int](repeating: -1, count: p.count))
}

func makeFrame(_ fx: [String: Any], _ w: Double, _ h: Double, _ insets: [Double]) -> FractalFrame {
    let links = (fx["links"] as? [Any] ?? []).flatMap { ints($0) }
    let frame = FractalFrame(FractalLayout(makeTree(fx)), linkEnds: links)
    frame.setView(width: w, height: h, top: insets[0], left: insets[1], bottom: insets[2], right: insets[3])
    for i in 0..<frame.tree.count { frame.nameWidth[i] = 55.0 * Double(frame.tree.titles[i].count) }
    frame.reach = 22
    return frame
}

/// One case's frame, stepped, as Android's FractalFixture.prepared.
func prepared(_ fx: [String: Any], _ c: [String: Any]) -> FractalFrame {
    let frame = makeFrame(fx, num(c["w"]), num(c["h"]), nums(c["insets"]))
    if let s = c["textScale"] { frame.textScale = num(s) }
    if let hide = c["hide"] {
        var shown = [Bool](repeating: true, count: frame.tree.count)
        for i in ints(hide) { for j in 0..<frame.tree.count where frame.tree.holds(i, j) { shown[j] = false } }
        frame.shown = shown
    }
    _ = frame.go(Int(num(c["focus"])), now: 0, instant: true)
    if let p = c["pinch"] { let v = nums(p); frame.zoom(v[0], v[1], v[2]) }
    frame.chosen = c["chosen"].map { Int(num($0)) } ?? -1
    frame.linksOf = c["linksOf"].map { Int(num($0)) } ?? -1
    frame.step()
    return frame
}

/// One case run as Android's FractalFixture.run runs it.
func run(_ fx: [String: Any], _ c: [String: Any]) -> [String: [[Double]]] {
    let frame = prepared(fx, c)
    var out: [String: [[Double]]] = [:]
    out["camera"] = [[frame.camX, frame.camY, frame.camZ]]
    out["drawn"] = [(0..<frame.drawnCount).map { Double(frame.drawn[$0]) }]
    var dots: [[Double]] = []
    for k in 0..<frame.drawnCount {
        dots.append([frame.dotRadius[k], frame.dotAlpha[k], frame.dotHub[k], frame.glowAlpha[k], frame.lineAlpha[k]])
    }
    out["dots"] = dots
    var names: [[Double]] = []
    for k in 0..<frame.nameCount where frame.nameNode[k] >= 0 {
        var row: [Double] = [Double(frame.nameNode[k]), frame.nameX0[k], frame.nameY0[k], frame.nameX1[k]]
        row += [frame.nameY1[k], frame.nameSize[k], Double(frame.nameSide[k]), frame.nameAlpha[k]]
        names.append(row)
    }
    out["names"] = names
    var links: [[Double]] = []
    for k in 0..<frame.linkCount { links.append([Double(frame.linkFrom[k]), Double(frame.linkTo[k]), frame.linkAlpha[k]]) }
    out["links"] = links
    out["hits"] = [(c["taps"] as? [Any] ?? []).map { let t = nums($0); return Double(frame.hit(t[0], t[1])) }]
    return out
}

func close(_ a: Double, _ b: Double) -> Bool { abs(a - b) <= 1e-6 * max(1, abs(b)) }

let fx = fixture()

// the layout is the concept's, to 1e-9
do {
    let layout = FractalLayout(makeTree(fx))
    let want = fx["layout"] as! [String: Any]
    let n = layout.tree.count
    let got: [(String, [Double])] = [("x", layout.x), ("y", layout.y), ("sp", layout.spacing), ("dr", layout.dot),
        ("rad", layout.reach), ("cx", layout.boxX), ("cy", layout.boxY), ("hw", layout.halfW), ("hh", layout.halfH)]
    for (name, values) in got {
        let w = nums(want[name])
        var worst = 0.0
        for i in 0..<n where i < w.count { worst = max(worst, abs(values[i] - w[i])) }
        check("layout: \(name) is the concept's to 1e-9 (\(n) nodes)", w.count == n && worst <= 1e-9, "worst \(worst)")
    }
    // children heaviest first, ties in node order; every parent holds its child
    let t = layout.tree
    var ordered = true, holds = true
    for i in 0..<t.count where t.childCount[i] > 1 {
        for k in 1..<t.childCount[i] {
            let a = t.children[t.childStart[i] + k - 1], b = t.children[t.childStart[i] + k]
            if !(t.weight[a] > t.weight[b] || (t.weight[a] == t.weight[b] && a < b)) { ordered = false }
        }
    }
    for i in 1..<t.count where !(t.holds(t.parent[i], i) && !t.holds(i, t.parent[i])) { holds = false }
    check("tree: children heaviest first", ordered)
    check("tree: each parent holds its child, never the other way", holds)
}

// each recorded frame, as Android recorded it
for case let c as [String: Any] in fx["frames"] as? [Any] ?? [] {
    let name = c["name"] as? String ?? "?"
    let want = c["expect"] as! [String: Any]
    let got = run(fx, c)
    check("frame \(name): drawn", ints(want["drawn"]).map(Double.init) == got["drawn"]![0])
    check("frame \(name): hits", ints(want["hits"]).map(Double.init) == got["hits"]![0], "\(got["hits"]![0])")
    let cam = nums(want["camera"])
    check("frame \(name): camera", zip(cam, got["camera"]![0]).allSatisfy { close($1, $0) } && cam.count == 3)
    for key in ["dots", "names", "links"] {
        let rows: [Any] = want[key] as? [Any] ?? []
        let w: [[Double]] = rows.map(nums), g = got[key]!
        var bad = ""
        if w.count != g.count { bad = "count \(g.count) against \(w.count)" } else {
            for i in 0..<w.count where w[i].count != g[i].count || !zip(w[i], g[i]).allSatisfy({ close($1, $0) }) {
                bad = "[\(i)] \(g[i]) against \(w[i])"
                break
            }
        }
        check("frame \(name): \(key)", bad.isEmpty, bad)
    }
}
check("frames: the fixture records at least five", ((fx["frames"] as? [Any])?.count ?? 0) >= 5)

// no name without its dot (the reviewer's BNP and PR depression note): drawn,
// at least 2.5 across and 0.6 strong, in every recorded frame
do {
    var names = 0, bad: [String] = []
    for case let c as [String: Any] in fx["frames"] as? [Any] ?? [] {
        let frame = prepared(fx, c)
        for n in 0..<frame.nameCount where frame.nameNode[n] >= 0 {
            let i = frame.nameNode[n]
            names += 1
            guard let k = (0..<frame.drawnCount).first(where: { frame.drawn[$0] == i }) else { bad.append(frame.tree.titles[i]); continue }
            if 2 * frame.dotRadius[k] < 2.5 || frame.dotAlpha[k] < FractalRules.namedAlpha { bad.append(frame.tree.titles[i]) }
            for m in 0..<frame.nameCount where m != n && frame.nameNode[m] >= 0 {
                if frame.sx[i] > frame.nameX0[m] && frame.sx[i] < frame.nameX1[m] && frame.sy[i] > frame.nameY0[m] && frame.sy[i] < frame.nameY1[m] {
                    bad.append(frame.tree.titles[i] + " under " + frame.tree.titles[frame.nameNode[m]])
                }
            }
        }
    }
    check("names: each of \(names) has its dot", names > 20 && bad.isEmpty, "\(bad)")
}

// the tones are Performance's region colours, as Android's
do {
    typealias F = FractalTree.Folder
    typealias I = FractalTree.Idea
    let t = FractalTree.of(folders: [F(id: "a", parentId: nil, name: "A", created: 1), F(id: "b", parentId: "a", name: "B", created: 2),
                                     F(id: "c", parentId: nil, name: "C", created: 3)],
                           ideas: [I(id: "i", folderId: "a", title: "I", created: 1), I(id: "j", folderId: "b", title: "J", created: 1),
                                   I(id: "k", folderId: nil, title: "K", created: 1), I(id: "l", folderId: "c", title: "L", created: 1)],
                           hubTitle: "All ideas")
    let tones = FractalTones(t)
    func of(_ s: String) -> Int { t.titles.firstIndex(of: s)! }
    let got: [Int] = [tones.dot[of("I")], tones.dot[of("J")], tones.dot[of("L")], tones.dot[of("K")],
                      tones.hub[0], tones.hub[of("A")], tones.glow[of("A")]]
    let want: [Int] = [0x5C9CFF, 0x69A4FF, 0xFF5E7D, 0xC9D1E0, 0xE2E6EE, 0xA5C8FF, 0x7CAFFF]
    check("tones: as Android's", got == want, "\(got.map { String($0, radix: 16) })")
}

// the budget holds with 100,000 ideas, and each graphics setting's budget is its own
do {
    var parents = [-1]
    for _ in 0..<40 {
        let me = parents.count
        parents.append(0)
        parents.append(contentsOf: [Int](repeating: me, count: 2_500))
    }
    let n = parents.count
    let tree = FractalTree(parent: parents, kind: (0..<n).map { $0 == 0 ? 0 : (parents[$0] == 0 ? 1 : 2) },
                           ids: [String](repeating: "", count: n), titles: (0..<n).map { "Idea \($0)" },
                           region: [Int](repeating: -1, count: n))
    let frame = FractalFrame(FractalLayout(tree))
    frame.setView(width: 390, height: 844, top: 120, left: 0, bottom: 90, right: 0)
    for i in 0..<n { frame.nameWidth[i] = 55.0 * Double(tree.titles[i].count) }
    for b in [FractalRules.budgetLow, FractalRules.budgetBalanced] {
        frame.budget = b
        _ = frame.go(0, now: 0, instant: true)
        frame.zoom(195, 470, 4)
        frame.step()
        check("budget: the \(b) setting draws \(b)", frame.drawnCount == b, "\(frame.drawnCount)")
    }
    frame.budget = 10_000
    check("budget: never past High's", frame.budget == FractalRules.budget)
    var within = true
    for focus in [0, 1, 2502] {
        _ = frame.go(focus, now: 0, instant: true)
        for z in [1.0, 4.0, 40.0] {
            frame.zoom(195, 470, z)
            frame.step()
            if !(1...FractalRules.budget).contains(frame.drawnCount) || frame.nameCount > FractalRules.names { within = false }
        }
    }
    check("budget: never more than \(FractalRules.budget) dots or \(FractalRules.names) names", within)
}

// the store's tree: folders oldest first, each folder's folders then ideas; loose ideas off the hub
do {
    typealias F = FractalTree.Folder
    typealias I = FractalTree.Idea
    let folders = [F(id: "b", parentId: nil, name: "Cardiology", created: 2), F(id: "a", parentId: nil, name: "Examples", created: 1),
                   F(id: "c", parentId: "a", name: "Inguinal", created: 3), F(id: "x", parentId: "y", name: "Loop 1", created: 4),
                   F(id: "y", parentId: "x", name: "Loop 2", created: 5)]
    let ideas = [I(id: "i2", folderId: "a", title: "Two", created: 20), I(id: "i1", folderId: "a", title: "One", created: 10),
                 I(id: "i3", folderId: nil, title: "Loose", created: 5), I(id: "i4", folderId: "gone", title: "Lost folder", created: 6),
                 I(id: "i5", folderId: "c", title: "Deep", created: 7)]
    let t = FractalTree.of(folders: folders, ideas: ideas, hubTitle: "All ideas")
    check("store: the order", t.titles == ["All ideas", "Examples", "Inguinal", "Deep", "One", "Two", "Cardiology", "Loop 1", "Loop 2", "Loose", "Lost folder"], "\(t.titles)")
    check("store: the parents", t.parent == [-1, 0, 1, 2, 1, 1, 0, 0, 7, 0, 0], "\(t.parent)")
    check("store: the regions", t.region == [-1, 0, 0, 0, 0, 0, 1, 2, 2, -1, -1], "\(t.region)")
    check("store: a loose idea is an idea", t.kind[9] == FractalTree.idea)
    let flat = FractalTree.of(folders: [], ideas: Array(ideas.prefix(2)), hubTitle: "All ideas")
    check("store: no folders, the ideas hang from the hub", flat.titles == ["All ideas", "One", "Two"] && flat.parent == [-1, 0, 0])
}

// tap opens the cluster, Back goes up, a glide lands on the frame, settle finds the cluster
do {
    let frame = makeFrame(fx, 390, 844, [120, 0, 90, 0])
    let t = frame.tree
    let cardiology = t.titles.firstIndex(of: "Cardiology")!
    let heartFailure = t.titles.firstIndex(of: "Heart failure")!
    let leaf = t.titles.firstIndex(of: "Raised in heart failure")!
    _ = frame.go(0, now: 0, instant: true)
    frame.step()
    check("tap: a folder opens itself", frame.clusterFor(cardiology) == cardiology)
    check("tap: an idea opens the cluster it sits in", frame.clusterFor(leaf) == t.parent[leaf])
    _ = frame.go(cardiology, now: 0, instant: true)
    check("tap: the cluster in view opens nothing", frame.clusterFor(cardiology) == -1)
    check("tap: a hub inside opens", frame.clusterFor(heartFailure) == heartFailure)
    check("back: goes up to the whole", frame.back(now: 0, instant: true) && frame.focus == 0)
    check("back: the whole has no back", !frame.back(now: 0, instant: true))
    let end = frame.frameOf(cardiology)
    let length = frame.go(cardiology, now: 10, instant: false)
    check("glide: its length", (0.42...1.25).contains(length) && frame.gliding, "\(length)")
    frame.advance(10 + length / 2)
    check("glide: still going halfway", frame.gliding)
    frame.advance(10 + length + 0.001)
    check("glide: ends on the cluster's frame", !frame.gliding && abs(frame.camX - end.x) <= 1e-12
          && abs(frame.camY - end.y) <= 1e-12 && abs(frame.camZ - end.z) <= 1e-9)
    _ = frame.go(0, now: 0, instant: true)
    frame.camX = end.x
    frame.camY = end.y
    frame.camZ = end.z
    frame.settle()
    check("settle: a pinch onto a cluster makes it the one in view", frame.focus == cardiology)
}

// S12 A4: 1,000 seeded poses, the same stream as Android's aThousandPosesKeepEveryNameHonest: every
// placed name has its dot drawn 2.5 across and 0.6 strong, no two names overlap, at most 120 names
// and the budget of parts
do {
    let layout = FractalLayout(makeTree(fx))
    let tree: FractalTree = layout.tree
    let links: [Int] = (fx["links"] as? [Any] ?? []).flatMap { ints($0) }
    var hubs: [Int] = []
    var ideas: [Int] = []
    for i in 0..<tree.count {
        if tree.childCount[i] > 0 { hubs.append(i) }
        if tree.kind[i] == FractalTree.idea { ideas.append(i) }
    }
    let views: [[Double]] = [[411, 914, 104, 90], [1280, 800, 80, 90], [673, 841, 90, 90]]
    let scales: [Double] = [1.0, 1.18, 1.35]
    var x: UInt64 = 91
    func next() -> Double {
        x = x &* 6364136223846793005 &+ 1442695040888963407
        return Double(x >> 11) / 9007199254740992.0
    }
    var named: Int = 0
    var most: Int = 0
    var bad: [String] = []
    for pose in 0..<1000 {
        let v: [Double] = views[pose % 3]
        let frame = FractalFrame(layout, linkEnds: links)
        frame.setView(width: v[0], height: v[1], top: v[2], left: 0, bottom: v[3], right: 0)
        for i in 0..<tree.count { frame.nameWidth[i] = 55.0 * Double(tree.titles[i].count) }
        frame.reach = 22
        frame.textScale = scales[Int(next() * 3)]
        _ = frame.go(hubs[Int(next() * Double(hubs.count))], now: 0, instant: true)
        let px: Double = next() * v[0]
        let py: Double = next() * v[1]
        frame.zoom(px, py, exp(next() * 2.4 - 1.2))
        let dx: Double = (next() - 0.5) * v[0] * 0.6
        let dy: Double = (next() - 0.5) * v[1] * 0.6
        frame.pan(dx, dy)
        if next() < 0.3 {
            let c: Int = ideas[Int(next() * Double(ideas.count))]
            frame.chosen = c
            frame.linksOf = c
        }
        frame.step()
        if frame.nameCount > FractalRules.names { bad.append("pose \(pose): \(frame.nameCount) names") }
        if frame.drawnCount > FractalRules.budget { bad.append("pose \(pose): \(frame.drawnCount) parts") }
        for n in 0..<frame.nameCount {
            let i: Int = frame.nameNode[n]
            if i < 0 { continue }
            var slot: Int = -1
            for k in 0..<frame.drawnCount where frame.drawn[k] == i { slot = k }
            if slot < 0 || 2 * frame.dotRadius[slot] < 2.5 || frame.dotAlpha[slot] < FractalRules.namedAlpha {
                bad.append("pose \(pose): \(tree.titles[i])'s dot")
            }
            for m in (n + 1)..<max(frame.nameCount, n + 1) where frame.nameNode[m] >= 0 {
                if frame.nameX0[n] < frame.nameX1[m] && frame.nameX0[m] < frame.nameX1[n]
                    && frame.nameY0[n] < frame.nameY1[m] && frame.nameY0[m] < frame.nameY1[n] {
                    bad.append("pose \(pose): \(tree.titles[i]) over \(tree.titles[frame.nameNode[m]])")
                }
            }
            named += 1
        }
        most = max(most, frame.nameCount)
    }
    check("poses: 1,000 keep every name honest", bad.isEmpty, bad.prefix(5).joined(separator: "; "))
    check("poses: enough names checked (\(named), most in a pose \(most))", named > 2000 && most > 10)
    print("poses: \(named) names checked, most in a pose \(most)")
}

print(failures.isEmpty ? "all passed" : "\(failures.count) failed")
exit(failures.isEmpty ? 0 : 1)
