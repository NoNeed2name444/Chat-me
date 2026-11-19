import Foundation

// The 3D Fractal's camera moves and names (S12). Foundation only, so both are
// tested on Linux (FractalNames3DTests). Android's FractalNames.kt and the
// Fractal case of Map3DView's first view are the same.

/// One dot on screen: where, how big across (points), how far in front of
/// the eye, and whether it may carry a name (FractalSight.nameable).
nonisolated struct FractalScreenNode: Equatable {
    let index: Int
    let x: Float, y: Float
    let pixels: Float
    let crisp: Bool
    let depth: Float
}

/// The Fractal's camera moves, as GraphPerfCamera poses: the first view, a
/// fly-in and how near and far the camera may go.
nonisolated enum FractalCamera3D {
    /// The first view looks a little down on All ideas, as Android's FRACTAL_PITCH.
    static let pitch: Float = 22 * Float.pi / 180
    /// A fly's length, as the map's.
    static let seconds: Double = 0.8

    /// All ideas, its parts and theirs, from the first view's yaw and pitch, as large as fits in the
    /// room the chrome and the card leave, centred there (FractalFit). With `sight` (its focus 0), framed
    /// again to what it draws there (FractalFit.drawn).
    static func whole(_ m: FractalModel, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float), sight: FractalSight? = nil) -> GraphPerfCamera {
        var c = GraphPerfCamera()
        c.yaw = 0; c.pitch = pitch
        if m.tree.count == 0 { return c.framing(centre: .zero, radius: 1.2, width: width, height: height, insets: insets, fill: 0.9) }
        let nodes = FractalFit.nodes(m.tree, 0, levels: FractalFit.wholeLevels, most: m.budget)
        c = FractalFit.fit(m.layout, nodes, from: c, width: width, height: height, insets: insets)
        guard let sight else { return c }
        return FractalFit.drawn(m, sight, nodes, at: c, width: width, height: height, insets: insets)
    }

    /// Hub `index`'s cluster from where the camera looks now, as large as fits in the room the chrome leaves, centred
    /// there. With `sight` (its focus `index`), framed again to what it draws there (FractalFit.drawn).
    static func flyIn(_ m: FractalModel, _ index: Int, from camera: GraphPerfCamera, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float), sight: FractalSight? = nil) -> GraphPerfCamera {
        let nodes = FractalFit.nodes(m.tree, index, levels: FractalFit.inLevels, most: m.budget)
        let c = FractalFit.fit(m.layout, nodes, from: camera, width: width, height: height, insets: insets)
        guard let sight else { return c }
        return FractalFit.drawn(m, sight, nodes, at: c, width: width, height: height, insets: insets)
    }

    static func centre(_ m: FractalModel, _ i: Int) -> SIMD3<Float> {
        let c = m.layout.centre
        return SIMD3(Float(c[3 * i]), Float(c[3 * i + 1]), Float(c[3 * i + 2]))
    }

    /// How near and far a pinch may take the camera.
    static func limits(_ m: FractalModel) -> ClosedRange<Float> {
        let near = m.smallest * 4, far = max(Float(m.layout.reach.first ?? 1) * 8, near * 2)
        return near...far
    }

    /// The camera's projection times view, with near and far hugging the whole map's ball, as the renderer draws it.
    static func viewProj(_ m: FractalModel, _ camera: GraphPerfCamera, width: Float, height: Float) -> GraphPerfMatrix {
        let reach = Float(m.layout.reach.first ?? 1)
        let c = centre(m, 0) - camera.eye
        let toCentre = (c * c).sum().squareRoot()
        let near = max(max(toCentre - reach * 1.15, max(toCentre, 1e-3) * 1e-4), 1e-5)
        let far = max(toCentre + reach * 1.15, near * 2)
        return camera.projectionMatrix(aspect: width / max(height, 1), near: near, far: far) * camera.viewMatrix()
    }

    /// The dots `sight`'s last frame drew, for names and taps.
    static func screen(_ sight: FractalSight) -> [FractalScreenNode] {
        var out: [FractalScreenNode] = []
        out.reserveCapacity(sight.count)
        for k in 0..<sight.count {
            out.append(FractalScreenNode(index: sight.node[k], x: sight.x[k], y: sight.y[k], pixels: 2 * sight.radius[k],
                                         crisp: sight.nameable[k], depth: sight.depth[k]))
        }
        return out
    }
}

/// The Fractal's framing in 3D (S12, the lead's review of run 44408042504): the first view and a
/// fly-in show what they frame as large as it fits in the room the chrome leaves, centred there. From
/// the camera's own yaw and pitch, the eye comes as near as it can while every framed dot, its mark
/// round it, stays inside that room less a margin (`margin`, about 14 points on a phone); within the
/// room left over the marks' box is put in the room's middle. Android's FractalFit.kt is the same.
nonisolated enum FractalFit {
    /// Room kept between what is framed and each edge of the room the chrome leaves, as a share of its narrower side.
    static let margin: Float = 0.035
    /// How many levels below All ideas the first view frames, and below a hub a fly-in frames.
    static let wholeLevels = 3
    static let inLevels = 2

    /// `hub` and its parts down to `levels` below it, level by level, each hub's parts heaviest first,
    /// at most `most` of them: what a view of `hub`'s cluster frames, never more than a frame draws.
    static func nodes(_ tree: FractalTree, _ hub: Int, levels: Int, most: Int = FractalRules.budget) -> [Int] {
        let cap = min(max(most, 1), tree.count)
        var out: [Int] = [hub]
        out.reserveCapacity(cap)
        var from = 0
        for _ in 0..<levels {
            let to = out.count
            for q in from..<to {
                let i = out[q], s = tree.childStart[i]
                for k in 0..<tree.childCount[i] {
                    if out.count >= cap { return out }
                    out.append(tree.children[s + k])
                }
            }
            if out.count == to { break }
            from = to
        }
        return out
    }

    /// How far a node's mark reaches round it, in the map's units: a hub's lit glow, an idea's dot.
    static func mark(_ layout: FractalLayout3D, _ i: Int) -> Double {
        layout.tree.childCount[i] > 0 ? layout.dot[i] * Double(1 + FractalSight.glowReach) : layout.dot[i]
    }

    /// The margin in points for the room `insets` leave in a `width` x `height` view.
    static func margin(width: Float, height: Float, insets: (top: Float, left: Float, bottom: Float, right: Float)) -> Float {
        margin * max(min(width - insets.left - insets.right, height - insets.top - insets.bottom), 0)
    }

    /// The most times `drawn` frames a pose again.
    static let passes = 5

    /// What a fit of `nodes` to `camera` draws, framed: the sight's frame at that pose (its focus and filter as set),
    /// then the pose fit once more to `nodes` and every dot drawn there, each with its glow as drawn. A deeper
    /// level's dot or a hub's glow at its least size then stays in the room, not at the view's edge (the review of
    /// S12's stills), and what is drawn is centred there, not what a nearer pose drew. A pose that draws other dots
    /// than the last (one turned into view as the camera stood back, a level closed as it shrank), or a glow
    /// grown, is framed again, at most `passes` times in all. Android's FractalFit.drawn is the same.
    static func drawn(_ m: FractalModel, _ sight: FractalSight, _ nodes: [Int], at camera: GraphPerfCamera, width: Float, height: Float,
                      insets: (top: Float, left: Float, bottom: Float, right: Float), margin given: Float? = nil) -> GraphPerfCamera {
        let shown = sight.shown
        let framed = nodes.filter { shown == nil || ($0 < shown!.count && shown![$0]) }
        var last: [Int: Double] = [:]
        var pose = camera
        for pass in 0..<passes {
            let f = pose.focal(viewHeight: height)
            sight.step(FractalCamera3D.viewProj(m, pose, width: width, height: height), focal: f, width: width, height: height)
            var marks: [Int: Double] = [:]
            for k in 0..<sight.count {
                // the glow's reach on screen, back in the map's units at the dot's depth
                marks[sight.node[k]] = Double(sight.radius[k] * (1 + FractalSight.glowReach * sight.hub[k])) * Double(sight.depth[k]) / Double(f)
            }
            // the same dots as the pose was fit to, none grown past rounding: it frames what it draws
            if pass > 0 && Set(marks.keys) == Set(last.keys) && marks.allSatisfy({ $0.value <= (last[$0.key] ?? 0) * (1 + grown) }) { break }
            var all = framed
            var seen = Set(framed)
            for i in marks.keys.sorted() where seen.insert(i).inserted { all.append(i) }
            if all.isEmpty { break }
            last = marks
            pose = fit(m.layout, all, from: camera, width: width, height: height, insets: insets, margin: given, marks: marks)
        }
        return pose
    }

    /// How much a glow may grow from the pose it was fit at (a share of it) and still count as framed.
    static let grown = 0.002

    /// `camera` moved to show every one of `nodes` with its mark from its own yaw and pitch, as large as fits in the
    /// room `insets` leave less the margin, centred in that room; it orbits the middle of what it frames. `marks`
    /// is a node's mark (map units) as a frame draws it, in place of its own: a big hub's dot drawn at its most
    /// size, a speck's at its least.
    static func fit(_ layout: FractalLayout3D, _ nodes: [Int], from camera: GraphPerfCamera, width: Float, height: Float,
                    insets: (top: Float, left: Float, bottom: Float, right: Float), margin given: Float? = nil,
                    marks: [Int: Double] = [:]) -> GraphPerfCamera {
        var out = camera
        let r = SIMD3<Double>(camera.right), u = SIMD3<Double>(camera.up), b = SIMD3<Double>(-camera.forward)
        let f = Double(camera.focal(viewHeight: height))
        let w = Double(width), h = Double(height)
        var m = Double(given ?? margin(width: width, height: height, insets: insets))
        if w - Double(insets.left + insets.right) <= 4 * m || h - Double(insets.top + insets.bottom) <= 4 * m { m = 0 }
        let left = Double(insets.left) + m - w / 2, right = w / 2 - Double(insets.right) - m
        let top = h / 2 - Double(insets.top) - m, bottom = -(h / 2 - Double(insets.bottom) - m)
        let count = nodes.count
        if count == 0 { return out }
        var x = [Double](repeating: 0, count: count), y = x, z = x, k = x
        var zLow = Double.infinity, zHigh = -Double.infinity, nearest = -Double.infinity
        for q in 0..<count {
            let i = nodes[q]
            let p = SIMD3<Double>(layout.pos[3 * i], layout.pos[3 * i + 1], layout.pos[3 * i + 2])
            x[q] = (p * r).sum(); y[q] = (p * u).sum(); z[q] = (p * b).sum(); k[q] = marks[i] ?? mark(layout, i)
            zLow = min(zLow, z[q]); zHigh = max(zHigh, z[q]); nearest = max(nearest, z[q] + k[q])
        }
        // for an eye at depth e along b, the eye's across and up that keep each mark in the room:
        // across >= x + k - right (e - z) / f and across <= x - k - left (e - z) / f, and so for up
        func spans(_ e: Double) -> (Double, Double, Double, Double) {
            var lowX = -Double.infinity, highX = Double.infinity, lowY = -Double.infinity, highY = Double.infinity
            for q in 0..<count {
                let d = e - z[q]
                lowX = max(lowX, x[q] + k[q] - right * d / f); highX = min(highX, x[q] - k[q] - left * d / f)
                lowY = max(lowY, y[q] + k[q] - top * d / f); highY = min(highY, y[q] - k[q] - bottom * d / f)
            }
            return (lowX, highX, lowY, highY)
        }
        func fits(_ e: Double) -> Bool { let s = spans(e); return s.0 <= s.1 && s.2 <= s.3 }
        // the nearest eye that fits: a farther one always fits too, so halve the gap between them
        let reach = max(zHigh - zLow, 1e-6) + (k.max() ?? 0)
        var near = nearest + 1e-9, far = nearest + reach
        while !fits(far) { far = nearest + (far - nearest) * 2 }
        for _ in 0..<48 { let mid = (near + far) / 2; if fits(mid) { far = mid } else { near = mid } }
        let s = spans(far)
        // then, within what fits, the across and up that put the marks' box in the room's middle: its middle
        // moves the other way as the eye moves, each mark at its own pace, so halve towards it
        func middle(_ at: Double, _ c: [Double]) -> Double {
            var lo = Double.infinity, hi = -Double.infinity
            for q in 0..<count {
                let d = far - z[q]
                lo = min(lo, (c[q] - k[q] - at) * f / d); hi = max(hi, (c[q] + k[q] - at) * f / d)
            }
            return (lo + hi) / 2
        }
        func centred(_ low: Double, _ high: Double, _ c: [Double], _ goal: Double) -> Double {
            var a = low, z1 = high
            for _ in 0..<48 { let mid = (a + z1) / 2; if middle(mid, c) > goal { a = mid } else { z1 = mid } }
            return (a + z1) / 2
        }
        let ex = centred(s.0, s.1, x, (left + right) / 2), ey = centred(s.2, s.3, y, (top + bottom) / 2)
        // orbit the middle of the framed depth, on the line through the view's middle
        let zc = (zLow + zHigh) / 2
        out.distance = Float(far - zc)
        out.target = SIMD3<Float>(r * ex + u * ey + b * zc)
        return out
    }
}

/// The 3D Fractal's names (S12), placed by the Tree's rules (TreeNames3D's
/// sizes, its budget of 380 tried and 120 drawn, its foot): the cluster flown
/// in to first, then hubs, then the heavier, then the bigger on screen. Each
/// takes the first of its spots (under, above, after and before its dot,
/// then for a hub a row further under and above) that is clear of every name
/// placed, inside the view and off the chrome's boxes, covers no named dot and no
/// dot drawn as big as its own, and has no other dot nearer it than its own, so
/// a name is never read as a neighbour's. Where one placed name alone is in the
/// way, it may step to another of its clear spots first. A name with no such spot
/// is not drawn, so no two names ever overlap at any angle or zoom. Only a dot bright and big
/// enough (FractalSight.nameable) has a name. The names come from the pose
/// alone, never from what was named before, so the same pose names the same.
/// Android's FractalNames is the same.
nonisolated enum FractalNames3D {
    /// Dots this close in radius (points) count as the same size.
    static let same: Float = 0.5
    /// The grid's cell, in points.
    static let cell: Float = 64

    static func hub(_ tree: FractalTree, _ i: Int) -> Bool { i == 0 || tree.childCount[i] > 0 || tree.kind[i] != FractalTree.idea }

    static func order(_ screen: [FractalScreenNode], _ tree: FractalTree, focus: Int = 0) -> [FractalScreenNode] {
        let valid = screen.filter { $0.crisp && $0.index >= 0 && $0.index < tree.count }
        let inFocus: (Int) -> Bool = { i in focus < 0 || focus >= tree.count || tree.holds(focus, i) }
        return valid.sorted { a, b in
            let fa = inFocus(a.index), fb = inFocus(b.index)
            if fa != fb { return fa }
            let ha = hub(tree, a.index), hb = hub(tree, b.index)
            if ha != hb { return ha }
            let wa = tree.weight[a.index], wb = tree.weight[b.index]
            if wa != wb { return wa > wb }
            if a.pixels != b.pixels { return a.pixels > b.pixels }
            return a.index < b.index
        }
    }

    /// The names to draw for `screen` in a view `width` by `height` whose top
    /// `top` and bottom `bottom` points the chrome covers. `measure` gives a
    /// node's words' width and height at a size, bold for a hub. `held` is a
    /// spot by node to try first (the map passes none, so names follow the
    /// pose alone). `clear` are the chrome's boxes over the view (Back, the
    /// round tools), which no name goes under.
    static func layout(_ screen: [FractalScreenNode], _ tree: FractalTree, style: TreeNames3D.Style,
                       measure: (_ node: Int, _ bold: Bool, _ size: Float) -> (width: Float, height: Float),
                       width: Float, height: Float, top: Float, bottom: Float,
                       held: [Int: Int] = [:], focus: Int = 0, clear: [CGRect] = []) -> [TreePlacedName] {
        var out: [TreePlacedName] = []
        out.reserveCapacity(TreeNames3D.most)
        // the chrome's boxes, taken before any name
        let blocked: [TreePlacedName] = clear.map { r in
            TreePlacedName(index: -1, size: 0, bold: false, x: Float(r.midX), y: Float(r.midY), width: Float(r.width), height: Float(r.height), spot: -1)
        }
        let ordered = order(screen, tree, focus: focus)
        var dots = Grid(ordered, count: tree.count) { hub(tree, $0) }
        // the dots a name could be read as naming (marked)
        let near = Grid(screen.filter { marked(tree, $0, leastLeaf: style.leastLeaf) }, count: tree.count) { hub(tree, $0) }
        // each placed name's dot and spots, so one in a later name's way can step aside
        var options: [(node: FractalScreenNode, spots: [(Float, Float)?])] = []
        for node in ordered.prefix(TreeNames3D.budget) {
            if out.count >= TreeNames3D.most { break }
            let i = node.index
            let isHub = hub(tree, i)
            // a dot a name already covers is not named: its name would stand off a hidden dot
            if out.contains(where: { covers($0.x, $0.y, $0.width, $0.height, node.x, node.y, node.pixels / 2, style.gapY) })
                || blocked.contains(where: { covers($0.x, $0.y, $0.width, $0.height, node.x, node.y, node.pixels / 2, style.gapY) }) { continue }
            if !isHub && node.pixels < style.leastLeaf { continue }
            if TreeNames3D.label(tree.titles[i]).isEmpty { continue }
            let size = isHub ? style.bough : style.leaf
            let words = measure(i, isHub, size)
            let w = words.width + 2 * style.halo, h = words.height + 2 * style.halo
            let below = node.pixels / 2 + h / 2 + style.gapY
            let side = node.pixels / 2 + style.gapX + w / 2
            // under, above, after, before: the first two slide along their row to stay in, the others don't
            var all: [(Float, Float, Bool)] = [(node.x, node.y + below, true), (node.x, node.y - below, true),
                                               (node.x + side, node.y, false), (node.x - side, node.y, false)]
            if isHub {
                let further = below + h + style.gapY
                all += [(node.x, node.y + further, true), (node.x, node.y - further, true)]
            }
            var spots: [(Float, Float)?] = []
            for s in all {
                spots.append(inView((s.0, s.1), w: w, h: h, width: width, height: height, top: top, bottom: bottom,
                                    edge: style.edge, slides: s.2))
            }
            var tried: [Int] = Array(spots.indices)
            if let last = held[i], last >= 0, last < spots.count { tried.removeAll { $0 == last }; tried.insert(last, at: 0) }
            // a spot that hides no hub's dot first, so a name held from the last pose gives way to a hub turned under it
            var chosen = -1
            for k in tried {
                guard let s = spots[k] else { continue }
                if out.allSatisfy({ apart(s, w, h, $0, style) }) && blocked.allSatisfy({ apart(s, w, h, $0, style) })
                    && !dots.covered(s.0, s.1, w, h, gap: style.gapY, own: node.pixels / 2, not: i)
                    && !near.nearer(s.0, s.1, w, h, than: node, part: { ownPart(tree, i, $0) }) {
                    if chosen < 0 { chosen = k }
                    if !dots.coversHub(s.0, s.1, w, h, gap: style.gapY, own: i) { chosen = k; break }
                }
            }
            // no spot is clear: where one name alone is in the way, that name may step to another of its clear
            // spots (one that hides no hub, if its own hid none), so the first name's choice does not cost the next one
            if chosen < 0 {
                for k in tried {
                    guard let s = spots[k], blocked.allSatisfy({ apart(s, w, h, $0, style) }),
                          !dots.covered(s.0, s.1, w, h, gap: style.gapY, own: node.pixels / 2, not: i),
                          !near.nearer(s.0, s.1, w, h, than: node, part: { ownPart(tree, i, $0) }) else { continue }
                    let hits = out.indices.filter { !apart(s, w, h, out[$0], style) }
                    guard hits.count == 1 else { continue }
                    let j = hits[0], o = out[j], other = options[j]
                    let hid = dots.coversHub(o.x, o.y, o.width, o.height, gap: style.gapY, own: o.index)
                    let mine = TreePlacedName(index: i, size: size, bold: isHub, x: s.0, y: s.1, width: w, height: h, spot: k)
                    for q in other.spots.indices where q != o.spot {
                        guard let t = other.spots[q], apart(t, o.width, o.height, mine, style),
                              out.indices.allSatisfy({ $0 == j || apart(t, o.width, o.height, out[$0], style) }),
                              blocked.allSatisfy({ apart(t, o.width, o.height, $0, style) }),
                              !covers(t.0, t.1, o.width, o.height, node.x, node.y, node.pixels / 2, style.gapY),
                              !dots.covered(t.0, t.1, o.width, o.height, gap: style.gapY, own: other.node.pixels / 2, not: o.index),
                              !near.nearer(t.0, t.1, o.width, o.height, than: other.node, part: { ownPart(tree, o.index, $0) }),
                              hid || !dots.coversHub(t.0, t.1, o.width, o.height, gap: style.gapY, own: o.index) else { continue }
                        out[j] = TreePlacedName(index: o.index, size: o.size, bold: o.bold, x: t.0, y: t.1, width: o.width, height: o.height, spot: q)
                        chosen = k
                        break
                    }
                    if chosen >= 0 { break }
                }
            }
            if chosen >= 0, let s = spots[chosen] {
                out.append(TreePlacedName(index: i, size: size, bold: isHub, x: s.0, y: s.1, width: w, height: h, spot: chosen))
                options.append((node, spots))
                dots.named[i] = true
            }
        }
        return out
    }

    /// Whether the disc at `dx`, `dy` of radius `r` comes within `gap` of the box centred at `x`, `y`.
    static func covers(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ dx: Float, _ dy: Float, _ r: Float, _ gap: Float) -> Bool {
        let nx = min(max(dx, x - w / 2), x + w / 2), ny = min(max(dy, y - h / 2), y + h / 2)
        let ex = dx - nx, ey = dy - ny, rr = r + gap
        return ex * ex + ey * ey < rr * rr
    }

    /// The dots that may be named, by cell, so a box asks only the dots near it; `named` as they are.
    private struct Grid {
        private var cells: [Int64: [(x: Float, y: Float, r: Float, i: Int)]] = [:]
        private var most: Float = 0
        var named: [Bool]
        private let hubs: [Bool]

        init(_ nodes: [FractalScreenNode], count: Int, isHub: (Int) -> Bool) {
            named = [Bool](repeating: false, count: count)
            hubs = (0..<count).map(isHub)
            for n in nodes {
                let r = n.pixels / 2
                most = max(most, r)
                cells[Self.key(Self.at(n.x), Self.at(n.y)), default: []].append((n.x, n.y, r, n.index))
            }
        }

        private static func at(_ v: Float) -> Int { Int((v / FractalNames3D.cell).rounded(.down)) }
        private static func key(_ cx: Int, _ cy: Int) -> Int64 { Int64(cx) << 32 ^ (Int64(cy) & 0xFFFF_FFFF) }

        /// Whether the box comes within `gap` of a named dot or one at least `own` in radius, but for the named
        /// node's own (`not`): a spot stands just `gap` off its own dot, and rounding there must not reject it.
        func covered(_ x: Float, _ y: Float, _ w: Float, _ h: Float, gap: Float, own: Float, not: Int) -> Bool {
            let grow = most + gap
            for cx in Self.at(x - w / 2 - grow)...Self.at(x + w / 2 + grow) {
                for cy in Self.at(y - h / 2 - grow)...Self.at(y + h / 2 + grow) {
                    guard let list = cells[Self.key(cx, cy)] else { continue }
                    for d in list where d.i != not && (named[d.i] || d.r >= own - FractalNames3D.same)
                        && FractalNames3D.covers(x, y, w, h, d.x, d.y, d.r, gap) {
                        return true
                    }
                }
            }
            return false
        }

        /// Whether a dot other than `own`'s comes nearer the box, edge to edge, than `own`'s does; one `part` says is its own does not count.
        func nearer(_ x: Float, _ y: Float, _ w: Float, _ h: Float, than own: FractalScreenNode, part: (Int) -> Bool) -> Bool {
            let mine = FractalNames3D.apartFrom(x, y, w, h, own.x, own.y, own.pixels / 2)
            let grow = max(mine, 0) + most
            for cx in Self.at(x - w / 2 - grow)...Self.at(x + w / 2 + grow) {
                for cy in Self.at(y - h / 2 - grow)...Self.at(y + h / 2 + grow) {
                    guard let list = cells[Self.key(cx, cy)] else { continue }
                    for d in list where d.i != own.index && !part(d.i) && FractalNames3D.apartFrom(x, y, w, h, d.x, d.y, d.r) < mine { return true }
                }
            }
            return false
        }

        /// Whether the box comes within `gap` of a hub's dot other than `own`'s, of any size.
        func coversHub(_ x: Float, _ y: Float, _ w: Float, _ h: Float, gap: Float, own: Int) -> Bool {
            let grow = most + gap
            for cx in Self.at(x - w / 2 - grow)...Self.at(x + w / 2 + grow) {
                for cy in Self.at(y - h / 2 - grow)...Self.at(y + h / 2 + grow) {
                    guard let list = cells[Self.key(cx, cy)] else { continue }
                    for d in list where d.i != own && hubs[d.i] && FractalNames3D.covers(x, y, w, h, d.x, d.y, d.r, gap) {
                        return true
                    }
                }
            }
            return false
        }
    }

    /// How far the disc at `dx`, `dy` of radius `r` is from the box centred at `x`, `y`, edge to edge (below 0 inside it).
    static func apartFrom(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ dx: Float, _ dy: Float, _ r: Float) -> Float {
        let ex = max(abs(dx - x) - w / 2, 0), ey = max(abs(dy - y) - h / 2, 0)
        return (ex * ex + ey * ey).squareRoot() - r
    }

    /// The dot nearest each of `names`, edge to edge, among `screen`'s `marked` ones, but
    /// for the named hub's own parts (`ownPart`): each name's own, by node (-1 where none is drawn). What the
    /// rule above keeps true; the tests read it.
    static func nearest(_ names: [TreePlacedName], _ screen: [FractalScreenNode], _ tree: FractalTree, leastLeaf: Float) -> [Int] {
        names.map { p in
            var best = -1, bestAt = Float.infinity
            for s in screen where marked(tree, s, leastLeaf: leastLeaf)
                && !(s.index != p.index && ownPart(tree, p.index, s.index)) {
                let at = apartFrom(p.x, p.y, p.width, p.height, s.x, s.y, s.pixels / 2)
                if at < bestAt || (at == bestAt && s.index == p.index) { bestAt = at; best = s.index }
            }
            return best
        }
    }

    /// Whether a drawn dot is one a name could be read as naming: a hub's, of any size, or an idea's big and
    /// bright enough for a name of its own (`leastLeaf` across and crisp). A speck is not.
    static func marked(_ tree: FractalTree, _ s: FractalScreenNode, leastLeaf: Float) -> Bool {
        s.index >= 0 && s.index < tree.count && (hub(tree, s.index) || (s.crisp && s.pixels >= leastLeaf))
    }

    /// Whether dot `d` is part of `i`'s own cluster, so a name of `i` beside it still reads as `i`'s: a hub's
    /// name among its own parts is its cluster's. All ideas holds everything, so for it only its ideas are:
    /// its name beside a folder's hub would read as that folder's.
    static func ownPart(_ tree: FractalTree, _ i: Int, _ d: Int) -> Bool { tree.holds(i, d) && (i != 0 || tree.kind[d] == FractalTree.idea) }

    /// What VoiceOver reads (TalkBack's FractalNames.targets is the same): each name, over its words, then
    /// each hub drawn in `focus` with no name, over its dot, each at least `least` each way. A hub turned
    /// too small or too crowded for a name is still reached.
    static func targets(_ screen: [FractalScreenNode], _ names: [TreePlacedName], _ tree: FractalTree, focus: Int,
                        least: Float) -> [(index: Int, x: Float, y: Float, width: Float, height: Float)] {
        var out: [(index: Int, x: Float, y: Float, width: Float, height: Float)] = []
        var done = Set<Int>()
        for p in names where p.index >= 0 && p.index < tree.count && done.insert(p.index).inserted {
            out.append((p.index, p.x, p.y, max(least, p.width), max(least, p.height)))
        }
        let inFocus: (Int) -> Bool = { i in focus <= 0 || focus >= tree.count || tree.holds(focus, i) }
        for s in screen where s.index >= 0 && s.index < tree.count && hub(tree, s.index) && inFocus(s.index) && done.insert(s.index).inserted {
            let side = max(least, s.pixels)
            out.append((s.index, s.x, s.y, side, side))
        }
        return out
    }

    /// Whether a part is read as a folder ("Cardiology, folder"): every hub but All ideas, which is read as its name.
    static func spokenAsFolder(_ tree: FractalTree, _ i: Int) -> Bool { i > 0 && hub(tree, i) }

    private static func inView(_ s: (Float, Float), w: Float, h: Float, width: Float, height: Float,
                               top: Float, bottom: Float, edge: Float, slides: Bool) -> (Float, Float)? {
        var x = s.0
        let lo = edge + w / 2, hi = width - edge - w / 2
        if x < lo || x > hi {
            if !slides { return nil }
            x = lo > hi ? width / 2 : min(max(x, lo), hi)
        }
        if s.1 - h / 2 < top || s.1 + h / 2 > height - bottom { return nil }
        return (x, s.1)
    }

    private static func apart(_ s: (Float, Float), _ w: Float, _ h: Float, _ p: TreePlacedName, _ style: TreeNames3D.Style) -> Bool {
        abs(s.0 - p.x) * 2 >= w + p.width + 2 * style.gapX || abs(s.1 - p.y) * 2 >= h + p.height + 2 * style.gapY
    }

    /// Whether any of `names` covers a named dot on `screen`, or a dot that may be named as big as its own (none should).
    static func coversADot(_ names: [TreePlacedName], _ screen: [FractalScreenNode]) -> Bool {
        let named = Set(names.map(\.index))
        for p in names {
            let own = (screen.first { $0.index == p.index }?.pixels ?? 0) / 2
            for s in screen where (named.contains(s.index) || s.crisp && s.pixels / 2 >= own - same)
                && covers(p.x, p.y, p.width, p.height, s.x, s.y, s.pixels / 2, 0) {
                return true
            }
        }
        return false
    }

    /// What a tap at `x`, `y` lands on: the name whose words it is on, else a name's touch target (its box,
    /// at least `least` each way, as VoiceOver's: `targets`), the nearest for its size, then the dot whose
    /// disc (at least `least` across) holds it, the nearest for its size; -1 for empty space.
    static func hit(_ screen: [FractalScreenNode], _ names: [TreePlacedName], x: Float, y: Float, least: Float) -> Int {
        for p in names where abs(x - p.x) * 2 <= p.width && abs(y - p.y) * 2 <= p.height { return p.index }
        var best = -1, bestScore = Float.infinity
        for p in names {
            let score = max(abs(x - p.x) * 2 / max(least, p.width), abs(y - p.y) * 2 / max(least, p.height))
            if score <= 1, score < bestScore { bestScore = score; best = p.index }
        }
        if best >= 0 { return best }
        for s in screen {
            let r = max(s.pixels, least) / 2
            let d = ((s.x - x) * (s.x - x) + (s.y - y) * (s.y - y)).squareRoot()
            if d <= r, d / r < bestScore { bestScore = d / r; best = s.index }
        }
        return best
    }
}
