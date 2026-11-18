import Foundation

// The Fractal look in 3D (S12): what one frame draws. Clusters open as the
// camera nears them, the biggest on screen first, so the picture is the same
// at every depth: a hub, its parts round it, and their parts opening as they
// grow. At most the Graphics tier's budget of dots is drawn however many
// ideas there are (380, 300 or 220, FractalRules); the last quarter of them
// fades out. Every array is made once, for the tree, so a frame allocates
// nothing. Foundation only, so it is tested on Linux (FractalSightTests).
// Android's FractalSight.kt is the same.

/// The tree, its 3D layout and colours, its links by node (`linkEnds`,
/// pairs) and the `budget` of dots a frame draws.
nonisolated final class FractalModel {
    let tree: FractalTree
    let layout: FractalLayout3D
    let tones: FractalTones
    let budget: Int
    /// Each node's links: the other ends, linkStart[i] ..< linkStart[i + 1] in linkOther.
    let linkStart: [Int]
    let linkOther: [Int]
    /// The smallest dot: how near the camera may come.
    let smallest: Float

    init(_ tree: FractalTree, _ layout: FractalLayout3D, linkEnds: [Int] = [], budget: Int = FractalRules.budget) {
        self.tree = tree
        self.layout = layout
        self.budget = budget
        tones = FractalTones(tree)
        let n = tree.count
        let pairs = linkEnds.count / 2
        var start = [Int](repeating: 0, count: n + 1)
        func ok(_ a: Int, _ b: Int) -> Bool { a >= 0 && a < n && b >= 0 && b < n && a != b }
        for k in 0..<pairs {
            let a = linkEnds[2 * k], b = linkEnds[2 * k + 1]
            if ok(a, b) { start[a + 1] += 1; start[b + 1] += 1 }
        }
        for i in 0..<n { start[i + 1] += start[i] }
        var other = [Int](repeating: 0, count: start[n])
        var fill = Array(start.prefix(n))
        for k in 0..<pairs {
            let a = linkEnds[2 * k], b = linkEnds[2 * k + 1]
            if ok(a, b) { other[fill[a]] = b; fill[a] += 1; other[fill[b]] = a; fill[b] += 1 }
        }
        linkStart = start
        linkOther = other
        var least = Double.infinity
        for i in 0..<n where layout.dot[i] > 0 { least = min(least, layout.dot[i]) }
        smallest = Float(least.isFinite ? least : 0.01)
    }

    /// The dots a frame draws at a Graphics tier: High 380, Smooth 300, and 220 on a phone short of memory.
    static func budgetFor(_ tier: GraphicsTier, lowMemory: Bool = false) -> Int {
        if lowMemory { return FractalRules.budgetLow }
        return tier == .smooth ? FractalRules.budgetBalanced : FractalRules.budget
    }
}

/// One frame of the 3D Fractal: the dots drawn, their lines and links, worked out from the camera.
nonisolated final class FractalSight {
    let model: FractalModel
    private let tree: FractalTree
    private let layout: FractalLayout3D
    private let n: Int

    /// A hub's glow reaches this many more of its radius when fully lit.
    static let glowReach: Float = 1.2
    /// A place nearer the eye than this (view depth) is not drawn.
    static let near: Float = 1e-4
    /// The most nodes a frame looks at, drawn or passed over: the frame's cost however big the tree.
    static let lookMost = 4 * FractalRules.budget
    /// The most nodes a frame queues.
    static let pushMost = 8 * FractalRules.budget
    /// A tree line's brightness beside its dot's (Performance's folder line, the flat look's 0.22 x 1.2).
    static let treeLine = Float(FractalRules.line * FractalRules.treeLine)

    /// The most dots this frame draws: the model's budget, never above FractalRules.budget.
    var budget: Int { didSet { budget = min(max(budget, 1), FractalRules.budget) } }
    /// The cluster flown in to (0 the whole map): what is outside it dims.
    var focus = 0
    /// The chosen node (its ring) and the idea whose links stand out (Show links), -1 for none.
    var chosen = -1
    var linksOf = -1
    /// Which nodes the filter shows, nil for all.
    var shown: [Bool]?

    // the dots drawn, in the order they opened: node, place on screen (points) and depth, radius
    // (points), brightness, how much a lit hub (0...1), and whether a name may stand beside it
    private(set) var node = [Int](repeating: 0, count: FractalRules.budget)
    private(set) var x = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var y = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var depth = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var radius = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var alpha = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var hub = [Float](repeating: 0, count: FractalRules.budget)
    private(set) var nameable = [Bool](repeating: false, count: FractalRules.budget)
    private(set) var count = 0

    /// The links drawn, by slot in the dots above, and their brightness.
    private(set) var linkFrom = [Int](repeating: 0, count: FractalRules.linkBudget)
    private(set) var linkTo = [Int](repeating: 0, count: FractalRules.linkBudget)
    private(set) var linkAlpha = [Float](repeating: 0, count: FractalRules.linkBudget)
    private(set) var linkCount = 0
    /// How many nodes were looked at this frame: the guard on the frame's cost.
    private(set) var looked = 0
    /// How many were put in the queue this frame.
    private(set) var pushed = 0

    // per node, made once: the frame it was drawn in, its slot, its brightness as it was reached
    private var stamp: [Int]
    private var slot: [Int]
    private var given: [Float]
    private var frameNo = 0
    private var heap: [Int]
    private var heapKey: [Float]
    private var heapSize = 0

    init(_ model: FractalModel) {
        self.model = model
        tree = model.tree
        layout = model.layout
        n = model.tree.count
        budget = min(max(model.budget, 1), FractalRules.budget)
        stamp = [Int](repeating: 0, count: n)
        slot = [Int](repeating: 0, count: n)
        given = [Float](repeating: 0, count: n)
        heap = [Int](repeating: 0, count: max(n, 1))
        heapKey = [Float](repeating: 0, count: max(n, 1))
    }

    /// A node of the drawn dots' slot `k`'s parent's slot, or -1 when it is not drawn.
    func parentSlot(_ k: Int) -> Int {
        let p = tree.parent[node[k]]
        return p >= 0 && stamp[p] == frameNo ? slot[p] : -1
    }

    /// The slot node `i` is drawn in this frame, or -1.
    func slotOf(_ i: Int) -> Int { i >= 0 && i < n && stamp[i] == frameNo ? slot[i] : -1 }

    private func isShown(_ i: Int) -> Bool {
        guard let s = shown else { return true }
        return i < s.count && s[i]
    }

    private func at(_ i: Int) -> SIMD4<Float> {
        SIMD4(Float(layout.pos[3 * i]), Float(layout.pos[3 * i + 1]), Float(layout.pos[3 * i + 2]), 1)
    }

    /// Works out the frame for a camera whose projection times view is
    /// `viewProj`, `focal` points per unit at depth 1, in a view `width` by
    /// `height` points with `density` pixels a point (1 here: iOS works in points).
    func step(_ viewProj: GraphPerfMatrix, focal: Float, width: Float, height: Float, density: Float = 1) {
        frameNo &+= 1
        count = 0; linkCount = 0; heapSize = 0; looked = 0; pushed = 0
        if n == 0 { return }
        let d = max(density, 0.1)
        given[0] = 1
        push(0, Float.greatestFiniteMagnitude)
        let fadeFrom = Float(budget) * 0.75
        let open = FractalRules.open, openFull = FractalRules.openFull
        while heapSize > 0 && count < budget && looked < Self.lookMost {
            let i = pop()
            looked += 1
            let clip = viewProj.apply(at(i))
            let w = clip.w
            let ahead = w > Self.near
            // its cluster's spacing on screen, in points: what opens it and lights it
            let onScreen: Float = ahead ? Float(layout.spacing[i] * Double(focal) / Double(w) / Double(d)) : Float.greatestFiniteMagnitude
            if ahead {
                let sx = (clip.x / w * 0.5 + 0.5) * width
                let sy = (1 - (clip.y / w * 0.5 + 0.5)) * height
                let h: Float = tree.childCount[i] > 0 ? Float(FractalRules.smooth(FractalRules.hubFrom, FractalRules.hubTo, Double(onScreen))) : 0
                let r = Float(FractalRules.clamp(layout.dot[i] * Double(focal) / Double(w) / Double(d), 1.25 + 1.75 * Double(h), 18 + 8 * Double(h))) * d
                let glow = r * (1 + Self.glowReach * h)
                if sx + glow >= 0 && sx - glow <= width && sy + glow >= 0 && sy - glow <= height {
                    var a = given[i]
                    if focus > 0 && !tree.holds(focus, i) { a *= Float(FractalRules.dim) }
                    if Float(count) >= fadeFrom { a *= (Float(budget) - Float(count)) / (Float(budget) - fadeFrom) }
                    if i == chosen { a = max(a, 1) }
                    let k = count
                    count += 1
                    node[k] = i; x[k] = sx; y[k] = sy; depth[k] = w; radius[k] = r; alpha[k] = a; hub[k] = h
                    nameable[k] = Double(a) >= FractalRules.namedAlpha && Double(r) >= FractalRules.namedDot * Double(d)
                    stamp[i] = frameNo; slot[i] = k
                }
            }
            // its parts, once its cluster is open enough on screen
            let parts = tree.childCount[i]
            if parts == 0 || Double(onScreen) < open { continue }
            let opening = Float(FractalRules.smooth(open, openFull, Double(onScreen)))
            let s = tree.childStart[i]
            // heaviest first, and never more than a frame could draw: a folder of 100,000 costs what one of 380 does
            for q in 0..<min(parts, budget) {
                if pushed >= Self.pushMost { break }
                let c = tree.children[s + q]
                if !isShown(c) || !inView(c, viewProj, focal: focal, width: width, height: height) { continue }
                given[c] = given[i] * opening
                let cc = viewProj.apply(at(c))
                push(c, cc.w > Self.near ? Float(layout.spacing[c] * Double(focal) / Double(cc.w)) : Float.greatestFiniteMagnitude / 2)
            }
        }
        links()
    }

    /// Whether any of `c`'s cluster's ball can be in the view.
    private func inView(_ c: Int, _ viewProj: GraphPerfMatrix, focal: Float, width: Float, height: Float) -> Bool {
        let clip = viewProj.apply(SIMD4(Float(layout.centre[3 * c]), Float(layout.centre[3 * c + 1]), Float(layout.centre[3 * c + 2]), 1))
        let w = clip.w
        let reach = Float(layout.reach[c])
        if w < -reach { return false }
        if w <= Self.near { return true }
        let r = reach * focal / w
        let sx = (clip.x / w * 0.5 + 0.5) * width
        let sy = (1 - (clip.y / w * 0.5 + 0.5)) * height
        return sx + r >= 0 && sx - r <= width && sy + r >= 0 && sy - r <= height
    }

    /// The links between drawn ideas, each once, at most FractalRules.linkBudget: Show links' own bright, the rest faint.
    private func links() {
        let base = Float(FractalRules.line * FractalRules.linkLine)
        for k in 0..<count {
            let i = node[k]
            var e = model.linkStart[i]
            let end = model.linkStart[i + 1]
            while e < end {
                let j = model.linkOther[e]
                e += 1
                if stamp[j] != frameNo || slot[j] >= k { continue }
                if linkCount >= FractalRules.linkBudget { return }
                let a = min(alpha[k], alpha[slot[j]]) * base
                let focusK: Float = linksOf < 0 ? 1 : (i == linksOf || j == linksOf ? Float(FractalRules.linksOn) : Float(FractalRules.linksOff))
                linkFrom[linkCount] = slot[j]; linkTo[linkCount] = k; linkAlpha[linkCount] = a * focusK
                linkCount += 1
            }
        }
    }

    private func push(_ i: Int, _ key: Float) {
        pushed += 1
        var k = heapSize
        heapSize += 1
        heap[k] = i; heapKey[k] = key
        while k > 0 {
            let p = (k - 1) / 2
            if heapKey[p] >= heapKey[k] { break }
            heap.swapAt(p, k); heapKey.swapAt(p, k); k = p
        }
    }

    private func pop() -> Int {
        let top = heap[0]
        heapSize -= 1
        if heapSize > 0 {
            heap[0] = heap[heapSize]; heapKey[0] = heapKey[heapSize]
            var k = 0
            while true {
                let a = 2 * k + 1, b = a + 1
                var m = k
                if a < heapSize && heapKey[a] > heapKey[m] { m = a }
                if b < heapSize && heapKey[b] > heapKey[m] { m = b }
                if m == k { break }
                heap.swapAt(m, k); heapKey.swapAt(m, k); k = m
            }
        }
        return top
    }
}
