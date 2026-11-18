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
func tapers(_ l: TreeLayout3D, _ i: Int) -> Bool {
    let tip = abs(l.r1[i] - l.r0[i] * TreeShape3D.taper) < 1e-12
    let joins = l.r0[i] <= l.r0[l.nodes.parent[i]] * TreeShape3D.join + 1e-12
    return tip && joins
}
func holds(_ l: TreeLayout3D, _ i: Int) -> Bool {
    let p = l.nodes.parent[i]
    let d = [l.centre[3 * i] - l.centre[3 * p], l.centre[3 * i + 1] - l.centre[3 * p + 1], l.centre[3 * i + 2] - l.centre[3 * p + 2]]
    return norm3(d, 0) <= l.reach[p] + 1e-9
}

// the 3D layout (TreeLayout3D) against the fixture's expect3d, the numbers Android's TreeLayout3DTest reads
for t in trees {
    let name = t["name"] as? String ?? "?"
    let l = TreeLayout3D(nodesOf(t))
    let expect = t["expect3d"] as? [String: [Double]] ?? [:]
    let got: [(String, [Double])] = [("base", l.base), ("dir", l.dir), ("len", l.length), ("r0", l.r0), ("r1", l.r1),
        ("anchor", l.anchor), ("leaf", l.leaf), ("sc", l.centre), ("sr", l.reach)]
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
    check("\(name) 3D: each branch tapers, no child thicker than where it joins", (1..<n).allSatisfy { i in tapers(l, i) })
    var seen = Set<Int>()
    let parentFirst = l.order.allSatisfy { i in defer { seen.insert(i) }; return i == 0 || seen.contains(l.nodes.parent[i]) }
    check("\(name) 3D: breadth first, every part once, a parent before its children", Set(l.order).count == n && parentFirst)
    check("\(name) 3D: each subtree's ball holds its children's", (1..<n).allSatisfy { i in holds(l, i) })
}
// a crown, not a pole with twigs (the lead's review of 18 Nov), as Android's theCrownIsAboutAsWideAsItIsTall
if let first = trees.first {
    let l = TreeLayout3D(nodesOf(first)), nodes = l.nodes
    let kids = (0..<nodes.childCount[0]).map { nodes.child(0, $0) }
    let offs = kids.map { acos(l.dir[3 * $0 + 1]) * 180 / Double.pi }
    check("3D: the trunk's branches leave it 35 to 60 degrees off upright", offs.allSatisfy { $0 > 34.999 && $0 < 60.001 }, "\(offs)")
    let lowest = kids.map { l.base[3 * $0 + 1] }.min() ?? 0
    var lo = [Double](repeating: .infinity, count: 3), hi = [Double](repeating: -.infinity, count: 3)
    for i in 1..<nodes.size { for t in [0, l.length[i]] { for a in 0..<3 {
        let v = l.base[3 * i + a] + l.dir[3 * i + a] * t
        lo[a] = min(lo[a], v); hi[a] = max(hi[a], v)
    } } }
    let wide = max(hi[0] - lo[0], hi[2] - lo[2]), tall = hi[1] - lowest
    check("3D: the crown is about as wide as it is tall", wide / tall >= 0.8 && wide / tall <= 1.4, "\(wide) x \(tall)")
    check("3D: the trunk is solid", 2 * l.r0[0] >= 0.14 * l.length[0])
}
if let first = trees.first {
    let a = TreeLayout3D(nodesOf(first)), b = TreeLayout3D(nodesOf(first))
    check("3D: the same tree grows the same every time", a.base == b.base && a.dir == b.dir && a.reach == b.reach)
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
