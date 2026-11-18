import Foundation

// S12, the Fractal in 3D: the layout against tools/fractal_fixture3d.json (made
// by tools/fractal_fixture3d.mjs, read by Android's FractalLayout3DTest too), so
// both platforms put every dot in the same place; and its rules: every other
// hub's parts on a cap opening away from its own hub, the spacing and the dots
// shrinking each level down, each cluster's ball holding all of it.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

let url = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("tools/fractal_fixture3d.json")
let fixture = (try? JSONSerialization.jsonObject(with: Data(contentsOf: url))) as? [String: Any] ?? [:]
let trees = fixture["trees"] as? [[String: Any]] ?? []
check("the fixture holds the sample and the made-up tree", trees.count == 2)

func num(_ a: Any) -> Double { (a as? NSNumber)?.doubleValue ?? .nan }

func treeOf(_ parent: [Int]) -> FractalTree {
    let n = parent.count
    var kids = [Int](repeating: 0, count: n)
    for i in 1..<max(n, 1) { kids[parent[i]] += 1 }
    return FractalTree(parent: parent, kind: (0..<n).map { $0 == 0 ? FractalTree.hub : (kids[$0] > 0 ? FractalTree.folder : FractalTree.idea) },
                       ids: [String](repeating: "", count: n), titles: (0..<n).map { "Part \($0)" },
                       region: [Int](repeating: -1, count: n))
}

for t in trees {
    let name = t["name"] as? String ?? "?"
    let parent = (t["parent"] as? [Any] ?? []).map { Int(num($0)) }
    let tree = treeOf(parent)
    let l = FractalLayout3D(tree)
    let want = t["layout"] as? [String: Any] ?? [:]
    for (key, got) in [("pos", l.pos), ("spacing", l.spacing), ("dot", l.dot), ("centre", l.centre), ("reach", l.reach)] {
        let arr = (want[key] as? [Any] ?? []).map(num)
        var worst = 0.0, at = -1
        for i in got.indices where i < arr.count {
            let d = abs(got[i] - arr[i])
            if !(d <= worst) { worst = d; at = i }
        }
        check("\(name) \(key): the fixture's within 1e-9", arr.count == got.count && worst <= 1e-9, "\(arr.count) \(got.count), \(worst) at \(at)")
    }

    var onCaps = true, spaced = true, shrinking = true, held = true
    for i in 1..<tree.count {
        let p = tree.parent[i]
        let ex = l.pos[3 * i] - l.pos[3 * p], ey = l.pos[3 * i + 1] - l.pos[3 * p + 1], ez = l.pos[3 * i + 2] - l.pos[3 * p + 2]
        let d = (ex * ex + ey * ey + ez * ez).squareRoot()
        if d < 0.64 * l.spacing[p] - 1e-12 || d > 1.08 * l.spacing[p] + 1e-12 { spaced = false }
        if l.spacing[i] >= l.spacing[p] || l.dot[i] >= l.dot[p] || l.dot[i] <= 0 { shrinking = false }
        if p == 0 { continue }
        let cos = (ex * l.out[3 * p] + ey * l.out[3 * p + 1] + ez * l.out[3 * p + 2]) / d
        let half = min(Fractal3DRules.capMost, Fractal3DRules.capLeast + Double(tree.childCount[p]) * Fractal3DRules.capEach)
        if acos(min(max(cos, -1), 1)) > half + 1e-9 { onCaps = false }
    }
    for i in 0..<tree.count {
        for k in tree.tin[i]..<tree.tout[i] {
            let j = tree.order[k]
            let x = l.pos[3 * j] - l.centre[3 * i], y = l.pos[3 * j + 1] - l.centre[3 * i + 1], z = l.pos[3 * j + 2] - l.centre[3 * i + 2]
            if (x * x + y * y + z * z).squareRoot() > l.reach[i] + 1e-12 { held = false }
        }
    }
    check("\(name): each part 0.64 to 1.08 of its hub's spacing out", spaced)
    check("\(name): each hub's parts on a cap opening away from its own hub", onCaps)
    check("\(name): spacing and dots shrink each level down", shrinking)
    check("\(name): each cluster's ball holds all of it", held)
}

// a hundred thousand ideas lay out quickly
var parent: [Int] = [-1]
for _ in 0..<200 {
    let top = parent.count; parent.append(0)
    for _ in 0..<4 { let sub = parent.count; parent.append(top); for _ in 0..<125 { parent.append(sub) } }
}
let big = treeOf(parent)
let start = Date()
let bigLayout = FractalLayout3D(big)
let seconds = Date().timeIntervalSince(start)
check("100,000 ideas laid out in \(Int(seconds * 1000)) ms", seconds < 3 && bigLayout.reach[0] > 0)

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
