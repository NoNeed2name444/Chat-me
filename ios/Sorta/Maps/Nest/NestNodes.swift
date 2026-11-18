import Foundation

// The nested maps' generic tree of nodes (S11), shared by Tree and Fractal.
// Node 0 is the trunk; every other node has a parent, a title, a colour group
// and its children, so the layout knows nothing of folders or ideas and a
// deeper store lays out the day it comes. Today it is fed the trunk, the
// folders and the ideas (NestNodes.build). Foundation only, the same as
// Android's NestNodes.kt line for line; suites treelayout and treescene.

/// One folder as the tree is fed it: its parent, or nil at the top level.
struct NestFolderIn {
    let id: String
    let parentId: String?
    let name: String
}

/// One idea as the tree is fed it: its folder, or nil in none, and when it was made (ms).
struct NestIdeaIn {
    let id: String
    let folderId: String?
    let title: String
    let createdAt: Int64
}

/// A tree of `size` nodes in parent order (a parent always comes before its
/// children): `ids` (the trunk's is ""), `parent` (the trunk's -1), `titles`,
/// `folder` and `tone` (the top-level folder's NoteTone place, -1 for none).
final class NestNodes {
    /// NoteTone's seven colours.
    static let tones = 7
    /// The grey of ideas in no folder.
    static let loose = 7
    /// The trunk's own bark.
    static let trunk = 8
    static let groups = 9

    let ids: [String]
    let parent: [Int]
    let titles: [String]
    let folder: [Bool]
    let tone: [Int]
    let size: Int
    /// Each node's children: `childCount`, from `childStart` in `childList`.
    private(set) var childCount: [Int]
    private(set) var childStart: [Int]
    private(set) var childList: [Int]
    private(set) var depth: [Int]
    /// Its colour group: a tone 0 to 6, `loose`, or `trunk`.
    private(set) var group: [Int]
    /// The leaves under it, and that to the 0.7.
    private(set) var weight: [Double]
    private(set) var weightPow: [Double]
    /// Depth-first order: a node's subtree is `tin` up to `tout` in `order`.
    private(set) var tin: [Int]
    private(set) var tout: [Int]
    private(set) var order: [Int]
    private var lookup: [String: Int] = [:]

    init(ids: [String], parent: [Int], titles: [String], folder: [Bool], tone: [Int]) {
        self.ids = ids; self.parent = parent; self.titles = titles; self.folder = folder; self.tone = tone
        let size = parent.count
        self.size = size
        childCount = Array(repeating: 0, count: size)
        childStart = Array(repeating: 0, count: size + 1)
        childList = Array(repeating: 0, count: size)
        depth = Array(repeating: 0, count: size)
        group = Array(repeating: 0, count: size)
        weight = Array(repeating: 0, count: size)
        weightPow = Array(repeating: 0, count: size)
        tin = Array(repeating: 0, count: size)
        tout = Array(repeating: 0, count: size)
        order = Array(repeating: 0, count: size)
        if size == 0 { return }
        for i in 1..<size { childCount[parent[i]] += 1 }
        for i in 0..<size { childStart[i + 1] = childStart[i] + childCount[i] }
        var fill = Array(childStart[0..<size])
        for i in 1..<size { childList[fill[parent[i]]] = i; fill[parent[i]] += 1 }
        for i in 1..<size { depth[i] = depth[parent[i]] + 1 }
        for i in 0..<size { group[i] = i == 0 ? Self.trunk : (tone[i] >= 0 ? tone[i] % Self.tones : Self.loose) }
        for i in stride(from: size - 1, through: 0, by: -1) {
            if childCount[i] == 0 { weight[i] = 1 }
            if i > 0 { weight[parent[i]] += weight[i] }
        }
        for i in 0..<size { weightPow[i] = pow(weight[i], 0.7) }
        // iterative, so a tall tree can't run out of stack
        var t = 0
        var stack = Array(repeating: 0, count: size + 1)
        var next = Array(repeating: 0, count: size)
        var top = 0
        stack[0] = 0; tin[0] = 0; order[t] = 0; t += 1
        while top >= 0 {
            let i = stack[top]
            if next[i] < childCount[i] {
                let c = childList[childStart[i] + next[i]]; next[i] += 1
                tin[c] = t; order[t] = c; t += 1; top += 1; stack[top] = c
            } else { tout[i] = t; top -= 1 }
        }
        for i in stride(from: size - 1, through: 0, by: -1) { lookup[ids[i]] = i }
    }

    func child(_ i: Int, _ k: Int) -> Int { childList[childStart[i] + k] }

    /// Whether `q` is `p` or grows from it.
    func within(_ p: Int, _ q: Int) -> Bool { tin[p] <= tin[q] && tin[q] < tout[p] }

    /// The node of `id`, or -1 (ids compared as given).
    func index(_ id: String) -> Int { lookup[id] ?? -1 }

    /// The trunk, the folders and the ideas, as Android's NestNodes.build:
    /// each top-level folder a bough in `folders`' order, its folders then its
    /// ideas (oldest first) on it; a folder whose parent is missing or in a
    /// loop grows from the trunk with no colour; ideas in no folder (or a
    /// missing one) hang from the trunk after the folders.
    static func build(trunk: String, folders: [NestFolderIn], ideas: [NestIdeaIn]) -> NestNodes {
        var known: [String: NestFolderIn] = [:]
        for f in folders { known[f.id] = f }
        var sub: [String: [NestFolderIn]] = [:]
        var tops: [NestFolderIn] = []
        for f in folders {
            if let p = f.parentId {
                if known[p] != nil { sub[p, default: []].append(f) }
            } else { tops.append(f) }
        }
        var held: [String: [NestIdeaIn]] = [:]
        var loose: [NestIdeaIn] = []
        for idea in ideas {
            if let f = idea.folderId, known[f] != nil { held[f, default: []].append(idea) } else { loose.append(idea) }
        }
        let oldestFirst: (NestIdeaIn, NestIdeaIn) -> Bool = { a, b in
            a.createdAt != b.createdAt ? a.createdAt < b.createdAt : a.id.uppercased() < b.id.uppercased()
        }
        for key in Array(held.keys) { held[key]?.sort(by: oldestFirst) }
        loose.sort(by: oldestFirst)
        var reached = Set<String>()
        func reach(_ f: NestFolderIn) {
            var stack = [f]
            while let g = stack.popLast() {
                if !reached.insert(g.id).inserted { continue }
                for c in sub[g.id] ?? [] { stack.append(c) }
            }
        }
        for f in tops { reach(f) }
        var strays: [NestFolderIn] = []
        for f in folders where !reached.contains(f.id) {
            var head = f
            var seen = Set<String>()
            while true {
                guard let p = head.parentId, let up = known[p] else { break }
                if reached.contains(up.id) || !seen.insert(head.id).inserted { break }
                head = up
            }
            if reached.contains(head.id) { continue }
            strays.append(head); reach(head)
        }
        var ids: [String] = [], parent: [Int] = [], titles: [String] = [], folder: [Bool] = [], tone: [Int] = []
        func add(_ id: String, _ p: Int, _ title: String, _ isFolder: Bool, _ t: Int) {
            ids.append(id); parent.append(p); titles.append(title); folder.append(isFolder); tone.append(t)
        }
        add("", -1, trunk, true, -1)
        // breadth first, so a node's children sit together: folders, then ideas
        var queue: [Int] = []
        var head = 0
        var placed = Set<String>()
        for (k, f) in tops.enumerated() where placed.insert(f.id).inserted { add(f.id, 0, f.name, true, k); queue.append(ids.count - 1) }
        for f in strays where placed.insert(f.id).inserted { add(f.id, 0, f.name, true, -1); queue.append(ids.count - 1) }
        for idea in loose { add(idea.id, 0, idea.title, false, -1) }
        while head < queue.count {
            let me = queue[head]; head += 1
            let id = ids[me]
            for c in sub[id] ?? [] where placed.insert(c.id).inserted { add(c.id, me, c.name, true, tone[me]); queue.append(ids.count - 1) }
            for idea in held[id] ?? [] { add(idea.id, me, idea.title, false, tone[me]) }
        }
        return NestNodes(ids: ids, parent: parent, titles: titles, folder: folder, tone: tone)
    }
}
