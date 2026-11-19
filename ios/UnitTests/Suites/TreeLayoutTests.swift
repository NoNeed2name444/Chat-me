import Foundation

// S11: the Tree's layout against the approved concept's own numbers
// (android/app/src/test/resources/tree/tree-fixture.json, written by
// tools/tree_fixture.mjs from the concept's layout), the file Android's
// TreeLayoutTest reads too: the same tree grows the same way on both
// platforms. And the tree the store feeds it: trunk, folders, ideas.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let fixtureURL = URL(fileURLWithPath: "android/app/src/test/resources/tree/tree-fixture.json")
let fixture = (try? JSONSerialization.jsonObject(with: Data(contentsOf: fixtureURL))) as? [String: Any] ?? [:]
let trees = fixture["trees"] as? [[String: Any]] ?? []
check("the fixture reads: 4 trees, K points a branch", trees.count == 4 && (fixture["k"] as? Int) == TreeShape.k)

func nodesOf(_ t: [String: Any]) -> NestNodes {
    let parent = (t["parent"] as? [Int]) ?? [], title = (t["title"] as? [String]) ?? [], folder = (t["folder"] as? [Int]) ?? []
    return NestNodes(ids: parent.indices.map { "\($0)" }, parent: parent, titles: title, folder: folder.map { $0 == 1 },
                     tone: Array(repeating: -1, count: parent.count))
}

for t in trees {
    let name = t["name"] as? String ?? "?"
    let layout = TreeLayout(nodesOf(t))
    let expect = t["expect"] as? [String: [Double]] ?? [:]
    let got: [(String, [Double])] = [("bx", layout.bx), ("by", layout.by), ("ang", layout.angle), ("len", layout.length), ("wid", layout.width),
        ("px", layout.px), ("py", layout.py), ("nx", layout.nx), ("ny", layout.ny), ("bcx", layout.bcx), ("bcy", layout.bcy),
        ("brad", layout.brad), ("u0", layout.u0), ("u1", layout.u1), ("v0", layout.v0), ("v1", layout.v1)]
    for (key, values) in got {
        let want = expect[key] ?? []
        var worst = 0.0, at = -1
        for i in values.indices where i < want.count { let d = abs(values[i] - want[i]); if d > worst { worst = d; at = i } }
        check("\(name) \(key): \(values.count) values, each within 2e-9 of the concept's", values.count == want.count && worst <= 2e-9,
              "count \(values.count) vs \(want.count), worst \(worst) at \(at)")
    }
}

// the same tree grows the same way every time
if let first = trees.first {
    let a = TreeLayout(nodesOf(first)), b = TreeLayout(nodesOf(first))
    check("the same tree grows the same every time", a.px == b.px && a.py == b.py && a.u1 == b.u1)
}

// the concept's first draws from its seed (Math.imul and >>> 0)
var r = NestRandom(seed: 11)
let draws = [r.next(), r.next(), r.next()]
check("the draw is the concept's", draws == [0.5115870486479253, 0.5299464082345366, 0.6081185641232878], "\(draws)")

// the store's tree: trunk, the top level in order, strays, then ideas in no folder, oldest first
let folders = [NestFolderIn(id: "F1", parentId: nil, name: "Garden"), NestFolderIn(id: "F2", parentId: "F1", name: "Beds"),
               NestFolderIn(id: "F3", parentId: nil, name: "Work"), NestFolderIn(id: "F4", parentId: "GONE", name: "Stray"),
               NestFolderIn(id: "L1", parentId: "L2", name: "Loop a"), NestFolderIn(id: "L2", parentId: "L1", name: "Loop b")]
let ideas = [NestIdeaIn(id: "i3", folderId: "F1", title: "Fence", createdAt: 30), NestIdeaIn(id: "i1", folderId: "F1", title: "Seeds", createdAt: 10),
             NestIdeaIn(id: "i2", folderId: "F2", title: "Tomatoes", createdAt: 5), NestIdeaIn(id: "i4", folderId: nil, title: "Call the plumber", createdAt: 1),
             NestIdeaIn(id: "i5", folderId: "MISSING", title: "Lost", createdAt: 2), NestIdeaIn(id: "i6", folderId: "F3", title: "Report", createdAt: 3)]
let s = NestNodes.build(trunk: "All ideas", folders: folders, ideas: ideas)
check("store: every folder and idea, and the trunk", s.size == 1 + folders.count + ideas.count)
check("store: the trunk is All ideas", s.titles[0] == "All ideas")
let boughs = (0..<s.childCount[0]).map { s.titles[s.child(0, $0)] }
check("store: the trunk's boughs", boughs == ["Garden", "Work", "Stray", "Loop a", "Call the plumber", "Lost"], "\(boughs)")
let garden = s.index("F1")
check("store: a folder's folders, then its ideas oldest first",
      (0..<s.childCount[garden]).map { s.titles[s.child(garden, $0)] } == ["Beds", "Seeds", "Fence"])
check("store: tones are the top level's places", s.tone[garden] == 0 && s.tone[s.index("F3")] == 1 && s.tone[s.index("i2")] == 0)
check("store: strays and loose ideas have none", s.tone[s.index("F4")] == -1 && s.tone[s.index("i4")] == -1)
check("store: groups", s.group[s.index("i4")] == NestNodes.loose && s.group[0] == NestNodes.trunk)
check("store: depths", s.depth[s.index("F2")] == 2 && s.depth[s.index("i2")] == 3)
check("store: a parent before its children", (1..<s.size).allSatisfy { s.parent[$0] < $0 })

// deeper nesting lays out the same way
if trees.count > 1 {
    let deep = nodesOf(trees[1])
    let layout = TreeLayout(deep)
    check("deep: five levels or more", (deep.depth.max() ?? 0) >= 5)
    check("deep: every part has a length and bounds", (0..<deep.size).allSatisfy { layout.length[$0] > 0 && layout.brad[$0] > 0 })
}

func norm3(_ v: [Double], _ i: Int) -> Double {
    let x = v[3 * i], y = v[3 * i + 1], z = v[3 * i + 2]
    return (x * x + y * y + z * z).squareRoot()
}
func holds(_ l: TreeLayout3D, _ i: Int) -> Bool {
    let p = l.nodes.parent[i]
    let d = [l.centre[3 * i] - l.centre[3 * p], l.centre[3 * i + 1] - l.centre[3 * p + 1], l.centre[3 * i + 2] - l.centre[3 * p + 2]]
    return norm3(d, 0) <= l.reach[p] + 1e-9
}
func dist3(_ p: [Double], _ v: [Double], _ i: Int) -> Double {
    let x = p[0] - v[3 * i], y = p[1] - v[3 * i + 1], z = p[2] - v[3 * i + 2]
    return (x * x + y * y + z * z).squareRoot()
}
/// How near node `c`'s base comes to `p`'s curve, and where along it, in `steps` steps.
func onCurve(_ l: TreeLayout3D, _ p: Int, _ c: Int, _ steps: Int) -> (off: Double, at: Double) {
    var off = Double.infinity, at = 0.0
    for s in 0...steps {
        let d = dist3(l.curveAt(p, Double(s) / Double(steps)), l.base, c)
        if d < off { off = d; at = Double(s) / Double(steps) }
    }
    return (off, at)
}

// the 3D layout (TreeLayout3D) against the fixture's expect3d, the numbers Android's TreeLayout3DTest reads
for t in trees {
    let name = t["name"] as? String ?? "?"
    let l = TreeLayout3D(nodesOf(t))
    let expect = t["expect3d"] as? [String: [Double]] ?? [:]
    let got: [(String, [Double])] = [("base", l.base), ("dir", l.dir), ("len", l.length), ("r0", l.r0), ("r1", l.r1), ("bend", l.bend),
        ("anchor", l.anchor), ("leaf", l.leaf), ("sc", l.centre), ("sr", l.reach)]
    check("\(name) 3D: the fixture holds exactly these keys", Set(expect.keys) == Set(got.map { $0.0 }), "\(expect.keys.sorted())")
    for (key, values) in got {
        let want = expect[key] ?? []
        var worst = 0.0, at = -1
        for i in values.indices where i < want.count { let d = abs(values[i] - want[i]); if d > worst { worst = d; at = i } }
        check("\(name) 3D \(key): \(values.count) values, each within 2e-9 of the fixture's", !want.isEmpty && values.count == want.count && worst <= 2e-9,
              "count \(values.count) vs \(want.count), worst \(worst) at \(at)")
    }
    let n = l.nodes.size
    check("\(name) 3D: the trunk goes straight up", l.dir[0] == 0 && l.dir[1] == 1 && l.dir[2] == 0 && l.base[0] == 0 && l.base[1] == 0)
    check("\(name) 3D: every direction is whole", (0..<n).allSatisfy { i in abs(norm3(l.dir, i) - 1) < 1e-9 })
    check("\(name) 3D: each branch tapers, no child as thick as its parent's base", (0..<n).allSatisfy { i in
        l.r1[i] < l.r0[i] && (i == 0 || l.r0[i] < l.r0[l.nodes.parent[i]]) })
    check("\(name) 3D: a leaf on every idea, none on a folder", (0..<n).allSatisfy { i in (l.nodes.childCount[i] == 0 && i > 0) == (l.leaf[i] > 0) })
    var seen = Set<Int>()
    let parentFirst = l.order.allSatisfy { i in defer { seen.insert(i) }; return i == 0 || seen.contains(l.nodes.parent[i]) }
    check("\(name) 3D: breadth first, every part once, a parent before its children", Set(l.order).count == n && parentFirst)
    check("\(name) 3D: each subtree's ball holds its children's", (1..<n).allSatisfy { i in holds(l, i) })

    // the owner's review of the third pass (19 Nov): a believable tree, not cramped; as Android's aBranchIsAsThickAsTheLeavesItCarries
    let nodes = l.nodes
    for i in 0..<n where nodes.childCount[i] > 0 {
        let kids = (0..<nodes.childCount[i]).map { nodes.child(i, $0) }
        let share = kids.reduce(0.0) { $0 + l.r0[$1] * l.r0[$1] } / (l.r0[i] * l.r0[i])
        check("\(name) 3D pipe: \(nodes.titles[i])'s children carry 0.6 to 1 of its area", share >= 0.6 && share <= 1 + 1e-9, "\(share)")
        let most = kids.map { l.r0[$0] }.max() ?? 0
        check("\(name) 3D pipe: \(nodes.titles[i]) tapers to its biggest child's", l.r1[i] >= most - 1e-12 || l.r1[i] == l.r0[i] * TreeShape3D.tip)
    }
    // as Android's siblingBranchesSpreadRoundTheirParentAndTheHeavierAreLonger
    for i in 0..<n {
        let kids = (0..<nodes.childCount[i]).map { nodes.child(i, $0) }.filter { nodes.childCount[$0] > 0 }
        for a in kids { for b in kids where a < b {
            let dot = (0..<3).reduce(0.0) { $0 + l.dir[3 * a + $1] * l.dir[3 * b + $1] }
            let apart = acos(max(-1, min(1, dot))) * 180 / Double.pi
            check("\(name) 3D gap: \(nodes.titles[a]) and \(nodes.titles[b]) at least the gap apart", apart >= TreeShape3D.gap * 180 / Double.pi - 1e-9, "\(apart)")
            if nodes.weight[a] > nodes.weight[b] { check("\(name) 3D: \(nodes.titles[a]) is heavier, not shorter", l.length[a] >= l.length[b] - 1e-12) }
        } }
    }
    // as Android's anIdeaIsAShortStemInASprayAndSpraysKeepApart
    let leaves = (1..<n).filter { l.leaf[$0] > 0 }
    for i in leaves {
        check("\(name) 3D: \(nodes.titles[i])'s stem is shorter than its leaf", l.length[i] < 2 * l.leaf[i])
        let p = nodes.parent[i]
        if p > 0 {
            let (off, at) = onCurve(l, p, i, 1000)
            check("\(name) 3D: \(nodes.titles[i]) sits on its parent's curve", off < 2e-3 * l.length[p], "\(off)")
            check("\(name) 3D: \(nodes.titles[i]) grows in its parent's spray", at >= 1 - TreeShape3D.spray - 0.01, "\(at)")
        }
    }
    var near = Double.infinity
    for a in leaves { for b in leaves where a < b && nodes.parent[a] != nodes.parent[b] {
        let d = dist3([l.anchor[3 * a], l.anchor[3 * a + 1], l.anchor[3 * a + 2]], l.anchor, b)
        near = min(near, d / (0.5 * (l.leaf[a] + l.leaf[b])))
    } }
    check("\(name) 3D: two leaves of different sprays never within half their lengths", near >= 1, "\(near)")
    check("\(name) 3D: no leaf past the most", (1..<n).allSatisfy { l.leaf[$0] <= TreeShape3D.leafMost + 1e-12 })
}
// as Android's theTrunkGoesUpAndChildrenSpreadRoundIt, aBranchCurvesUpAndAChildJoinsOnTheCurve and noLeafIsBiggerThanTheMost
if let first = trees.first {
    let l = TreeLayout3D(nodesOf(first)), nodes = l.nodes
    let kids = (0..<nodes.childCount[0]).map { nodes.child(0, $0) }
    let sides = kids.map { atan2(l.dir[3 * $0 + 2], l.dir[3 * $0]) * 180 / Double.pi }
    check("3D: the trunk's branches lean round it", (sides.max() ?? 0) - (sides.min() ?? 0) > 120, "\(sides)")
    var bent = 0
    for i in 1..<nodes.size {
        if nodes.childCount[i] == 0 {
            check("3D: \(nodes.titles[i])'s stem is straight", (0..<3).allSatisfy { l.bend[3 * i + $0] == 0 })
            continue
        }
        let t0 = l.tangentAt(i, 0), t1 = l.tangentAt(i, 1)
        check("3D: \(nodes.titles[i]) turns up toward its tip", t1[1] >= t0[1] - 1e-12)
        if t1[1] > t0[1] + 0.05 { bent += 1 }
        for k in 0..<nodes.childCount[i] {
            let c = nodes.child(i, k), off = onCurve(l, i, c, 400).off
            check("3D: \(nodes.titles[c]) joins \(nodes.titles[i]) on its curve", off < 2e-3 * l.length[i], "\(off)")
        }
    }
    check("3D: most branches bend", bent > 10, "\(bent)")
    let lowest = kids.map { l.base[3 * $0 + 1] }.min() ?? 0
    var lo = [Double](repeating: .infinity, count: 3), hi = [Double](repeating: -.infinity, count: 3)
    for i in 1..<nodes.size { for a in 0..<3 { lo[a] = min(lo[a], l.anchor[3 * i + a]); hi[a] = max(hi[a], l.anchor[3 * i + a]) } }
    let wide = max(hi[0] - lo[0], hi[2] - lo[2]), tall = hi[1] - lowest
    check("3D: the crown is about as wide as it is tall", wide / tall >= 0.8 && wide / tall <= 1.6, "\(wide) x \(tall)")
    check("3D: the trunk is solid", 2 * l.r0[0] >= 0.14 * l.length[0])
    let sizes = (1..<nodes.size).map { l.leaf[$0] }.filter { $0 > 0 }
    check("3D: leaves vary in size, by at most leafVar", (sizes.max() ?? 0) / (sizes.min() ?? 1) > 1.1
          && (sizes.max() ?? 0) / (sizes.min() ?? 1) <= (1 + TreeShape3D.leafVar / 2) / (1 - TreeShape3D.leafVar / 2) + 1e-9, "\(sizes.min() ?? 0) to \(sizes.max() ?? 0)")
    for p in 0..<nodes.size { for q in nodes.tin[p]..<nodes.tout[p] {
        let i = nodes.order[q]
        if dist3([l.centre[3 * p], l.centre[3 * p + 1], l.centre[3 * p + 2]], l.anchor, i) > l.reach[p] + 1e-9 {
            check("3D: \(i)'s body in \(p)'s ball", false)
        }
    } }
}
if let first = trees.first {
    let a = TreeLayout3D(nodesOf(first)), b = TreeLayout3D(nodesOf(first))
    check("3D: the same tree grows the same every time", a.base == b.base && a.dir == b.dir && a.anchor == b.anchor && a.reach == b.reach)
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
