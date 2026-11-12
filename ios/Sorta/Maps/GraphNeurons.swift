import Foundation

// The Neurons theme: the ideas' folders drawn as living cells, after the
// owner's board (glowing cells floating in a dark fluid, each soma round
// its nucleus, its processes reaching the next cell).
//
// - a top-level folder is a cell: a soma round a nucleus, floating on one
//   sheet facing the camera, the cells ordered by which way their links
//   run (senders first, receivers after);
// - a folder inside it is a part of that cell INSIDE its nucleus (the
//   owner asked for the subfolders in the nucleus: opening the cell
//   swells it to hold them), and a folder inside that a smaller part
//   inside the part;
// - a note is the smallest thing: a page a vesicle, an idea a granule,
//   inside the folder that holds it (in a cell, in the cytoplasm round
//   the nucleus);
// - insides load only when opened (UniverseInput.open): a closed cell is
//   its soma alone, an opened one shows its parts and notes, and an
//   opened part its own;
// - the notes in no folder share one cell of their own, the home cell
//   (Unsorted), on the sheet with the others and opened as they are (the
//   owner, 29 Oct: no small free cells; they were receptors and drifting
//   cells at the sheet's edge);
// - with no folders at all, one home cell holds every note, always open.
//
// Links are the cells' own processes: one per pair of ends, gathered from
// every note link between them, none dropped. Inside one cell each end is
// the deepest shown body holding its note; between cells each end is the
// cell itself, opened or not, so a link joins the two membranes and never
// crosses one to reach inside. Each grows out of the
// sending side (the one with more links) as one piece with it and ends on
// the other; its strength (GraphAnatomy) sets its width, and what it
// carries (excitatory, inhibitory, modulatory) its dye.
//
// Four rules: what a body is = what the item is; size = how much is in it
// (a part always smaller than what holds it, a note smaller than any part
// beside it); place = which folder holds it; opening never moves or
// resizes anything already shown.
//
// Pure and deterministic: Foundation only, the same notes always give the
// same picture, and nothing meets: what floats inside a container stays
// inside its membrane and clear of its siblings however it drifts (in a
// cell its parts inside the nucleus, its notes outside it), and no two
// cells touch. Tested on Linux
// (Tests/NeuronHierarchyTests).

/// What each body is in the Neurons theme (ThemeBody.role).
nonisolated enum NeuronRole: Int, Sendable, CaseIterable {
    /// A top-level folder: a whole cell.
    case cell = 0
    /// A folder inside a cell or inside a part: an organelle inside it.
    case part = 1
    /// The home cell: the one cell of a vault with no folders, or the one
    /// the notes in no folder share.
    case home = 2
    /// A page, inside the folder that holds it.
    case vesicle = 3
    /// An idea, inside the folder that holds it.
    case granule = 4

    /// Which end of a link sends when the ranks decide (ThemeBody.rank).
    var rank: Int {
        switch self {
        case .cell, .home: return 7
        case .part: return 6
        case .vesicle: return 5
        case .granule: return 3
        }
    }

    var isContainer: Bool { self == .cell || self == .part || self == .home }
}

nonisolated enum GraphNeurons {
    /// What floats inside a container stays within this share of its
    /// radius (the membrane is the rest).
    static let inner: Double = 0.8
    /// An opened cell's nucleus, as a share of the cell's radius: opening
    /// swells it from its resting size to this, and it holds the cell's
    /// parts, its notes floating in the cytoplasm round it (zones).
    /// NeuronShaders.soma draws it at this size.
    static let openNucleus: Double = 0.6
    /// What floats in an opened nucleus stays within this share of its
    /// radius, clear of its envelope.
    static let nucleusRoom: Double = 0.9
    /// The room kept between things inside a container, as a share of the
    /// inner radius: more than two wobbles, so they never meet.
    static let room: Double = 0.05
    /// How far a thing inside a container wobbles, as a share of the inner
    /// radius.
    static let wobble: Double = 0.015
    /// The most of a container's room what floats inside it fills,
    /// counting the room between things: crowded insides shrink together
    /// (sizes, room and wobble alike) to this, so they always fit.
    static let fill: Double = 0.22
    /// How far a cell on the sheet wobbles.
    static let cellDrift: Double = 0.012
    /// How far a cell's processes reach out at rest, in its radii.
    static let reach: Double = 1.9
    /// The room between neighbouring cells on the sheet, in the biggest
    /// cell's radii at a link length of 1: wide enough that two cells'
    /// dendrites never meet, however the sheet is jittered, down to 0.8
    /// of the length (0.94 * (2 + 0.8 * sheetGap) >= 2 * reach + 0.11).
    static let sheetGap: Double = 2.8
    /// The most a drift moves its body: GraphUniverse.wobble's longest
    /// diagonal for an amplitude of 1.
    static let wobbleBound: Double = 1.5653

    /// A cell's soma: bigger with more notes inside.
    static func cellSphere(count: Int) -> Double {
        GraphUniverse.clamp(0.34 + 0.035 * log2(1 + Double(count)), 0.34, 0.6)
    }

    /// A part's radius as a share of what holds it: bigger with more notes,
    /// but a small part of it (the owner asked for smaller parts), and
    /// still bigger than any note beside it (noteShare at most 0.088).
    static func partShare(count: Int) -> Double {
        GraphUniverse.clamp(0.1 + 0.03 * log2(1 + Double(count)), 0.1, 0.22)
    }

    /// Where what floats inside an opened container goes: in a cell its
    /// parts inside the swollen nucleus and its notes in the cytoplasm
    /// round it, in a part everything anywhere within the room.
    static func zones(radius: Double, cell: Bool) -> NeuronZones {
        let room: Double = inner * radius
        guard cell else { return NeuronZones(parts: room, core: 0, room: room) }
        return NeuronZones(parts: nucleusRoom * openNucleus * radius, core: openNucleus * radius, room: room)
    }

    /// A note's radius as a share of its folder's: a page bigger than an
    /// idea, a longer one bigger.
    static func noteShare(page: Bool, words: Int) -> Double {
        let level: Double = Double(min(GraphUniverse.level(words: words), 6))
        return page ? 0.07 + 0.003 * level : 0.05 + 0.002 * level
    }

    // MARK: planning

    static func plan(_ input: UniverseInput) -> ThemePlan {
        if input.notes.isEmpty && input.folders.isEmpty { return .empty }
        var planner = NeuronPlanner(input)
        return planner.run()
    }

    /// "2 cells, 3 parts, 12 granules, ...".
    static func summary(_ bodies: [ThemeBody]) -> String {
        var counts = [Int](repeating: 0, count: NeuronRole.allCases.count)
        for body in bodies where body.role >= 0 && body.role < counts.count { counts[body.role] += 1 }
        counts[NeuronRole.cell.rawValue] += counts[NeuronRole.home.rawValue]
        let words: [(NeuronRole, String, String)] = [
            (.cell, "cell", "cells"), (.part, "part", "parts"), (.vesicle, "vesicle", "vesicles"),
            (.granule, "granule", "granules")
        ]
        var parts: [String] = []
        for (role, one, many) in words {
            let n: Int = counts[role.rawValue]
            if n > 0 { parts.append("\(n) " + (n == 1 ? one : many)) }
        }
        return parts.joined(separator: ", ")
    }
}

/// One note link as the cells read it: GraphAnatomy's reading, or a plain
/// one where there is no anatomy.
nonisolated struct NeuronNoteLink: Sendable, Equatable {
    let from: Int
    let to: Int
    let strength: Int
    let kind: FiberKind
}

/// Where what floats inside an opened container goes, as distances from
/// its centre (GraphNeurons.zones).
nonisolated struct NeuronZones: Sendable {
    /// The parts stay within this.
    let parts: Double
    /// The notes stay beyond this (0: anywhere)...
    let core: Double
    /// ...and within this.
    let room: Double
}

/// Something floating inside a container: a part (a container index) or a
/// note (a note index), its radius, and where it floats relative to the
/// container's centre.
nonisolated struct NeuronItem: Sendable {
    let container: Int
    let note: Int
    var radius: Double
    var at: SIMD3<Double> = SIMD3<Double>(0, 0, 0)

    var isPart: Bool { container >= 0 }
}

/// The plan's working state. Reuses the Universe planner's tree: folders
/// (a cycle cut, a missing parent made top level), each note's container,
/// counts and links.
nonisolated struct NeuronPlanner: Sendable {
    var tree: UniversePlanner
    /// Whether each container shows (a top-level one, or one inside an
    /// opened one), and whether it is opened.
    var shown: [Bool] = []
    var opened: [Bool] = []
    /// Every note link, read once.
    var noteLinks: [NeuronNoteLink] = []
    /// How strongly each top-level container's notes link to each other
    /// one's, and how much more it sends than it receives.
    var sends: [[Int: Int]] = []
    var score: [Int] = []
    /// The top-level containers in the sheet's order.
    var tops: [Int] = []
    /// The biggest cell on the sheet, and the room between neighbours.
    var biggest: Double = 0
    var gap: Double = 0
    /// Each container's radius (once known) and a top cell's place.
    var cSphere: [Double] = []
    var cWorld: [SIMD3<Double>] = []
    /// The notes each container holds itself.
    var notesIn: [[Int]] = []
    // the output
    var bodies: [ThemeBody] = []
    var noteBody: [Int] = []
    var cBody: [Int] = []
    var systems: [[SIMD3<Float>]] = []

    init(_ input: UniverseInput) {
        tree = UniversePlanner(input, gatherLoose: true)
    }

    mutating func run() -> ThemePlan {
        tree.buildTree()
        tree.buildLinks()
        readLinks()
        markShown()
        readFlow()
        orderTops()
        placeTops()
        notesIn = [[Int]](repeating: [], count: tree.cCount)
        for (i, home) in tree.noteHome.enumerated() where home >= 0 { notesIn[home].append(i) }
        noteBody = [Int](repeating: -1, count: tree.noteList.count)
        cBody = [Int](repeating: -1, count: tree.cCount)
        for c in tops { emitTop(c) }
        let regions: [Int] = tops.map { cBody[$0] }
        var balls: [ThemeBall] = []
        for c in tops { balls.append(ThemeBall(c: cWorld[c], r: topReach(c) + 0.1)) }
        let envelope: [SIMD3<Float>] = ThemeLayout.points(balls, around: SIMD3<Double>(0, 0, 0))
        return ThemePlan(bodies: bodies, links: processes(), envelope: envelope, systems: systems,
                         regions: regions, summary: GraphNeurons.summary(bodies))
    }

    // MARK: reading

    /// Every link between two notes, each way it runs: as GraphAnatomy
    /// reads it when both notes are in the anatomy, else (or when it reads
    /// none) a plain excitatory link of strength 4 each way the vault has.
    mutating func readLinks() {
        var anatomy: [UUID: AnatomyNote] = [:]
        if let notes = tree.input.anatomy?.notes {
            for note in notes { anatomy[note.id] = note }
        }
        let n: Int = tree.noteList.count
        var index: [UUID: Int] = [:]
        for (i, note) in tree.noteList.enumerated() { index[note.id] = i }
        var ways = Set<Int>()
        for edge in tree.input.edges {
            guard let i = index[edge.a], let j = index[edge.b], i != j else { continue }
            ways.insert(i * n + j)
        }
        noteLinks = []
        for (i, j) in tree.edgePairs {
            var read: Int = 0
            if let a = anatomy[tree.noteList[i].id], let b = anatomy[tree.noteList[j].id] {
                if let link = GraphAnatomy.link(a, b) {
                    noteLinks.append(NeuronNoteLink(from: i, to: j, strength: link.strength, kind: link.kind))
                    read += 1
                }
                if let link = GraphAnatomy.link(b, a) {
                    noteLinks.append(NeuronNoteLink(from: j, to: i, strength: link.strength, kind: link.kind))
                    read += 1
                }
            }
            guard read == 0 else { continue }
            if ways.contains(i * n + j) {
                noteLinks.append(NeuronNoteLink(from: i, to: j, strength: 4, kind: .excitatory))
            }
            if ways.contains(j * n + i) {
                noteLinks.append(NeuronNoteLink(from: j, to: i, strength: 4, kind: .excitatory))
            }
        }
    }

    /// A container shows when it is top level or inside an opened one, and
    /// opens when it shows and its folder is in UniverseInput.open (the
    /// home cell of a vault with no folders is always open: it is the
    /// vault; the one the notes in no folder share opens by
    /// GraphUniverse.homeID, as a folder's cell does by its id).
    mutating func markShown() {
        let wanted = Set<UUID>(tree.input.open)
        shown = [Bool](repeating: false, count: tree.cCount)
        opened = [Bool](repeating: false, count: tree.cCount)
        let outerFirst: [Int] = (0..<tree.cCount).sorted { tree.depth[$0] < tree.depth[$1] }
        for c in outerFirst {
            let p: Int = tree.parent[c]
            shown[c] = p < 0 || (shown[p] && opened[p])
            opened[c] = shown[c] && (!tree.hasFolders || wanted.contains(tree.cID(c)))
        }
    }

    /// How the top-level containers link to each other, whatever is open.
    mutating func readFlow() {
        sends = [[Int: Int]](repeating: [:], count: tree.cCount)
        score = [Int](repeating: 0, count: tree.cCount)
        for link in noteLinks {
            let a: Int = tree.noteHome[link.from]
            let b: Int = tree.noteHome[link.to]
            guard a >= 0, b >= 0 else { continue }
            let ta: Int = tree.top[a]
            let tb: Int = tree.top[b]
            guard ta != tb else { continue }
            sends[ta][tb, default: 0] += link.strength
            score[ta] += link.strength
            score[tb] -= link.strength
        }
    }

    // MARK: the sheet

    /// Senders first, then by size, then by name.
    mutating func orderTops() {
        tops = (0..<tree.cCount).filter { tree.parent[$0] < 0 }
        tops.sort { a, b in
            if score[a] != score[b] { return score[a] > score[b] }
            if tree.count[a] != tree.count[b] { return tree.count[a] > tree.count[b] }
            return tree.lessC(a, b)
        }
    }

    /// The cells in rows facing the camera, read like text that snakes
    /// back on every other row, a partial last row centred, each row
    /// nudged sideways and each cell jittered a little (all in the pitch,
    /// so the whole sheet grows with the link length), then each lifted to its
    /// own bounded depth (GraphTilt).
    mutating func placeTops() {
        cSphere = [Double](repeating: 0, count: tree.cCount)
        cWorld = [SIMD3<Double>](repeating: SIMD3<Double>(0, 0, 0), count: tree.cCount)
        biggest = 0
        for c in tops {
            cSphere[c] = GraphNeurons.cellSphere(count: tree.count[c])
            biggest = max(biggest, cSphere[c])
        }
        gap = GraphNeurons.sheetGap * biggest * tree.input.spacing
        let pitch: Double = 2 * biggest + gap
        let n: Int = tops.count
        let cols: Int = n <= 2 ? 1 : (n <= 6 ? 2 : max(3, Int((Double(n) * 0.5).squareRoot().rounded(.up))))
        let rows: Int = (n + cols - 1) / cols
        for (k, c) in tops.enumerated() {
            let row: Int = k / cols
            let inRow: Int = min(cols, n - row * cols)
            var col: Int = k % cols
            if row % 2 == 1 { col = inRow - 1 - col }
            var random = UniverseRandom(tree.cSeed(c) ^ 0x5EE7)
            var x: Double = Double(col) - Double(inRow - 1) * 0.5
            if rows > 1 { x += row % 2 == 0 ? 0.18 : -0.18 }
            x += random.signed() * 0.03
            let y: Double = Double(rows - 1) * 0.5 - Double(row) + random.signed() * 0.03
            cWorld[c] = SIMD3<Double>(x * pitch, y * pitch, 0)
        }
        // real depth (T0057): each cell lifted to A·u(id) (GraphTilt), in
        // place of the old random jitter; its parts and notes are put from
        // its home (emitContainer's base) and drift with it
        // judged on the sheet at the standard link length, so the depth is
        // the map's own and the link-length setting only spreads the sheet
        let standard: Double = (2 * biggest + GraphNeurons.sheetGap * biggest) / max(pitch, 0.000_001)
        let clusters: [GraphTilt.Cluster] = tops.map { c in
            (id: tree.liftKey(c), centre: SIMD2<Float>(Float(cWorld[c].x * standard), Float(cWorld[c].y * standard)),
             radius: Float(cSphere[c]))
        }
        let lift: Float = GraphTilt.amplitude(clusters: clusters, theme: .neurons)
        for c in tops { cWorld[c].z = Double(GraphTilt.z(tree.liftKey(c), amplitude: lift)) }
    }

    /// How far a top cell's processes reach, drifting.
    func topReach(_ c: Int) -> Double {
        cSphere[c] * GraphNeurons.reach + GraphNeurons.cellDrift * GraphNeurons.wobbleBound
    }

    /// A top cell faces the cell it sends to most, else down the sheet.
    func topAxis(_ c: Int) -> SIMD3<Double> {
        var best: Int = -1
        var most: Int = 0
        for (t, s) in sends[c] where s > most || (s == most && best >= 0 && tree.lessC(t, best)) {
            best = t
            most = s
        }
        guard best >= 0 else { return SIMD3<Double>(0, -1, 0) }
        let d: SIMD3<Double> = GraphUniverse.normalize(cWorld[best] - cWorld[c])
        return GraphUniverse.length(d) > 0.5 ? d : SIMD3<Double>(0, -1, 0)
    }

    // MARK: the bodies

    /// A top-level container: a cell on the sheet, floating in place.
    mutating func emitTop(_ c: Int) {
        var random = UniverseRandom(tree.cSeed(c) ^ 0xD81F)
        let phase = Float(2 * Double.pi * random.unit())
        let rate = Float(0.12 + 0.08 * random.unit())
        let orbit: GraphOrbit = .drift(base: GraphUniverse.float3(cWorld[c]), amp: Float(GraphNeurons.cellDrift),
                                       phase: phase, rate: rate)
        emitContainer(c, radius: cSphere[c], parent: -1, region: -1, orbit: orbit, axis: topAxis(c))
    }

    /// A container's body, then (when it is opened) what floats inside it:
    /// its parts first, each with its own insides, then its notes.
    mutating func emitContainer(_ c: Int, radius: Double, parent: Int, region: Int, orbit: GraphOrbit,
                                axis: SIMD3<Double>) {
        let role: NeuronRole = c == tree.homeC ? .home : (tree.depth[c] == 0 ? .cell : .part)
        let index: Int = bodies.count
        let base: SIMD3<Float> = parent >= 0 ? bodies[parent].home : SIMD3<Float>(0, 0, 0)
        let mine: Int = region >= 0 ? region : index
        bodies.append(ThemeBody(id: tree.cID(c), kind: role == .home ? .home : .folder, role: role.rawValue,
                                parent: parent, sphere: Float(radius), depth: tree.depth[c], region: mine,
                                count: tree.count[c], links: 0, words: 0, rank: role.rank, seed: tree.cSeed(c),
                                orbit: orbit, home: base + GraphUniverse.offset(orbit, time: 0),
                                axis: GraphUniverse.float3(axis), title: tree.cName(c),
                                label: UniversePlanner.folderLabel(tree.cName(c), count: tree.count[c])))
        cBody[c] = index
        cSphere[c] = radius
        systems.append(ThemeLayout.points([ThemeBall(c: SIMD3<Double>(0, 0, 0), r: radius * 1.1)],
                                          around: SIMD3<Double>(0, 0, 0)))
        guard opened[c] else { return }
        let inside: (items: [NeuronItem], amp: Double) = pack(c, radius: radius)
        let amp = Float(inside.amp)
        for item in inside.items where item.isPart {
            let k: Int = item.container
            emitContainer(k, radius: item.radius, parent: index, region: mine,
                          orbit: drift(item.at, amp: amp, seed: tree.cSeed(k)), axis: outward(item.at))
        }
        for item in inside.items where !item.isPart {
            let i: Int = item.note
            let role: NeuronRole = tree.noteList[i].isPage ? .vesicle : .granule
            emitNote(i, role: role, radius: item.radius, parent: index, region: mine,
                     orbit: drift(item.at, amp: amp, seed: tree.nSeed(i)), axis: outward(item.at))
        }
    }

    /// A note's body, inside the container that holds it.
    mutating func emitNote(_ i: Int, role: NeuronRole, radius: Double, parent: Int, region: Int, orbit: GraphOrbit,
                           axis: SIMD3<Double>) {
        let note: UniverseNote = tree.noteList[i]
        let base: SIMD3<Float> = parent >= 0 ? bodies[parent].home : SIMD3<Float>(0, 0, 0)
        noteBody[i] = bodies.count
        bodies.append(ThemeBody(id: note.id, kind: .note, role: role.rawValue, parent: parent, sphere: Float(radius),
                                depth: -1, region: region, count: 0, links: tree.nbr[i].count, words: note.words,
                                rank: role.rank, seed: tree.nSeed(i), orbit: orbit,
                                home: base + GraphUniverse.offset(orbit, time: 0), axis: GraphUniverse.float3(axis),
                                title: note.title, label: UniversePlanner.noteLabel(note.title)))
        systems.append([])
    }

    /// A slow wobble round a place.
    func drift(_ at: SIMD3<Double>, amp: Float, seed: UInt64) -> GraphOrbit {
        var random = UniverseRandom(seed ^ 0xF1A7)
        let phase = Float(2 * Double.pi * random.unit())
        let rate = Float(0.15 + 0.1 * random.unit())
        return .drift(base: GraphUniverse.float3(at), amp: amp, phase: phase, rate: rate)
    }

    /// Away from a container's centre (up from the centre itself).
    func outward(_ at: SIMD3<Double>) -> SIMD3<Double> {
        GraphUniverse.length(at) > 1e-12 ? GraphUniverse.normalize(at) : SIMD3<Double>(0, 1, 0)
    }

    // MARK: inside a container

    /// Where everything inside a container floats: its parts and its notes,
    /// each in its zone (GraphNeurons.zones: in a cell its parts inside
    /// the swollen nucleus and its notes in the cytoplasm round it, in a
    /// part all together). Returns them and the wobble: a cell's notes
    /// shrink at least as much as its parts, so a note stays smaller than
    /// any part beside it.
    func pack(_ c: Int, radius: Double) -> (items: [NeuronItem], amp: Double) {
        var parts: [NeuronItem] = []
        for k in tree.kids[c] {
            parts.append(NeuronItem(container: k, note: -1,
                                    radius: GraphNeurons.partShare(count: tree.count[k]) * radius))
        }
        var notes: [NeuronItem] = []
        for i in notesIn[c] {
            let note: UniverseNote = tree.noteList[i]
            notes.append(NeuronItem(container: -1, note: i,
                                    radius: GraphNeurons.noteShare(page: note.isPage, words: note.words) * radius))
        }
        let zones: NeuronZones = GraphNeurons.zones(radius: radius, cell: tree.depth[c] == 0)
        let gap: Double = GraphNeurons.room * zones.room
        let amp: Double = GraphNeurons.wobble * zones.room
        var random = UniverseRandom(tree.cSeed(c) ^ 0xC0FF_EE11)
        guard zones.core > 0 else {
            let all = fit(biggestFirst(parts + notes), room: zones.room, core: 0, gap: gap, amp: amp, most: 1,
                          random: &random)
            return (all.items, amp * all.scale)
        }
        let inner = fit(biggestFirst(parts), room: zones.parts, core: 0, gap: gap, amp: amp, most: 1,
                        random: &random)
        let outer = fit(biggestFirst(notes), room: zones.room, core: zones.core, gap: gap, amp: amp,
                        most: inner.scale, random: &random)
        return (inner.items + outer.items, amp * min(inner.scale, outer.scale))
    }

    /// Biggest first, a part before a note of its size, then in the tree's
    /// order.
    func biggestFirst(_ items: [NeuronItem]) -> [NeuronItem] {
        items.sorted { a, b in
            if a.radius != b.radius { return a.radius > b.radius }
            if a.isPart != b.isPart { return a.isPart }
            return a.isPart ? tree.lessC(a.container, b.container) : tree.lessN(a.note, b.note)
        }
    }

    /// One zone's floats, biggest first, each at the first of a fixed run
    /// of random places in the room between `core` and `room` from the
    /// centre that keeps clear of everything already placed by the room
    /// between them (more than both wobbles, so they never meet). Crowded
    /// zones shrink together, sizes, room and wobble alike, until they fit
    /// (never past `most`). Returns them and the scale.
    func fit(_ items: [NeuronItem], room: Double, core: Double, gap: Double, amp: Double, most: Double,
             random: inout UniverseRandom) -> (items: [NeuronItem], scale: Double) {
        guard !items.isEmpty else { return ([], 1) }
        let sway: Double = amp * GraphNeurons.wobbleBound
        // start within the fill, with the biggest fitting beside the
        // nucleus (or alone) and the two biggest side by side
        var need: Double = 0
        for item in items { need += pow(item.radius + gap * 0.5, 3) }
        var scale: Double = min(most, cbrt(GraphNeurons.fill * (pow(room, 3) - pow(core, 3)) / need))
        let r1: Double = items[0].radius
        scale = min(scale, core > 0 ? (room - core) / (2 * r1 + gap + sway) : room / (r1 + sway))
        if items.count > 1 {
            scale = min(scale, 2 * room / (2 * (r1 + items[1].radius) + gap + 2 * sway))
        }
        let m: Int = max(600, 16 * items.count)
        var spots: [SIMD3<Double>] = []
        spots.reserveCapacity(m)
        while spots.count < m {
            let p = SIMD3<Double>(random.signed(), random.signed(), random.signed())
            if GraphUniverse.dot(p, p) <= 1 { spots.append(p) }
        }
        for _ in 0..<80 {
            if let placed = NeuronPlanner.place(items, spots: spots, room: room, core: core, gap: gap * scale,
                                                sway: sway * scale, scale: scale) {
                return (placed, scale)
            }
            scale *= 0.85
        }
        // not reached: long before this everything is small enough to fit
        return (items.map { NeuronItem(container: $0.container, note: $0.note, radius: 0) }, 0)
    }

    /// One try at placing `items` at `scale`: each at the first spot after
    /// the last one taken that is clear, the spots spread through the room
    /// between the core and the membrane; nil when one finds none.
    static func place(_ items: [NeuronItem], spots: [SIMD3<Double>], room: Double, core: Double, gap: Double,
                      sway: Double, scale: Double) -> [NeuronItem]? {
        var placed: [ThemeBall] = core > 0 ? [ThemeBall(c: SIMD3<Double>(0, 0, 0), r: core)] : []
        placed.reserveCapacity(items.count + 1)
        var out: [NeuronItem] = []
        out.reserveCapacity(items.count)
        var next: Int = 0
        for item in items {
            let r: Double = item.radius * scale
            let reach: Double = room - r - sway
            let low: Double = core > 0 ? core + gap + r : 0
            guard reach >= low else { return nil }
            var j: Int = next
            while j < spots.count && clearance(spread(spots[j], low: low, high: reach), r, placed, gap: gap) < 0 {
                j += 1
            }
            guard j < spots.count else { return nil }
            let at: SIMD3<Double> = spread(spots[j], low: low, high: reach)
            placed.append(ThemeBall(c: at, r: r))
            out.append(NeuronItem(container: item.container, note: item.note, radius: r, at: at))
            next = j + 1
        }
        return out
    }

    /// A spot in the unit ball moved out into the shell from `low` to
    /// `high` (the ball of radius `high` itself when `low` is 0).
    static func spread(_ p: SIMD3<Double>, low: Double, high: Double) -> SIMD3<Double> {
        guard low > 0 else { return p * high }
        let d: Double = GraphUniverse.length(p)
        let u: SIMD3<Double> = d > 1e-12 ? p / d : SIMD3<Double>(0, 1, 0)
        return u * (low + (high - low) * d)
    }

    /// How far a ball at p of radius r keeps clear of every placed ball
    /// beyond the gap: below 0 it is too close (it stops at the first).
    static func clearance(_ p: SIMD3<Double>, _ r: Double, _ placed: [ThemeBall], gap: Double) -> Double {
        var least: Double = .infinity
        for ball in placed {
            let d: SIMD3<Double> = p - ball.c
            let clear: Double = GraphUniverse.dot(d, d).squareRoot() - r - ball.r - gap
            if clear < 0 { return clear }
            least = min(least, clear)
        }
        return least
    }

    // MARK: processes

    /// The cells' processes: one per pair of ends with note links between
    /// them, gathered from every note link, none dropped. Inside one cell
    /// each end is the deepest shown body holding its note; a link that
    /// leaves a cell starts on the cell itself, so its tube joins the
    /// cell's membrane instead of crossing it to a part or note inside. It runs from the side with more links
    /// (ties by name); its strength is the links' mean strength plus a
    /// little for how many, and its dye the kind most of their strength
    /// carries.
    func processes() -> [ThemeLink] {
        struct Pair: Hashable {
            let a: Int
            let b: Int
        }
        var forward: [Pair: [NeuronNoteLink]] = [:]
        var backward: [Pair: [NeuronNoteLink]] = [:]
        for link in noteLinks {
            var x: Int = shownBody(link.from)
            var y: Int = shownBody(link.to)
            guard x >= 0, y >= 0, x != y else { continue }
            if bodies[x].region != bodies[y].region {
                x = outermost(x)
                y = outermost(y)
            }
            if x < y {
                forward[Pair(a: x, b: y), default: []].append(link)
            } else {
                backward[Pair(a: y, b: x), default: []].append(link)
            }
        }
        let keys: [Pair] = Set(forward.keys).union(backward.keys).sorted { $0.a != $1.a ? $0.a < $1.a : $0.b < $1.b }
        var out: [ThemeLink] = []
        out.reserveCapacity(keys.count)
        for key in keys {
            let ab: [NeuronNoteLink] = forward[key] ?? []
            let ba: [NeuronNoteLink] = backward[key] ?? []
            let sends: Bool = ab.count != ba.count ? ab.count > ba.count : lessBody(key.a, key.b)
            let all: [NeuronNoteLink] = ab + ba
            var total: Int = 0
            var weight: [Int] = [0, 0, 0]
            for link in all {
                total += link.strength
                weight[link.kind.rawValue] += link.strength
            }
            let mean: Double = Double(total) / Double(all.count)
            let strength = Int(GraphUniverse.clamp((mean + log2(Double(all.count))).rounded(), 1, 10))
            var kind: FiberKind = .excitatory
            if weight[1] > weight[0] && weight[1] >= weight[2] {
                kind = .inhibitory
            } else if weight[2] > weight[0] && weight[2] > weight[1] {
                kind = .modulatory
            }
            let from: Int = sends ? key.a : key.b
            let to: Int = sends ? key.b : key.a
            let inside: Bool = bodies[from].region == bodies[to].region
            out.append(ThemeLink(a: from, b: to, kind: inside ? 5 : 6, centre: -1, tag: kind.rawValue,
                                 width: Float(0.55 + 0.12 * Double(strength))))
        }
        return out
    }

    /// The deepest shown body holding note i: its own when its folder is
    /// opened, else its folder's (or the closed one round that).
    func shownBody(_ i: Int) -> Int {
        if noteBody[i] >= 0 { return noteBody[i] }
        var c: Int = tree.noteHome[i]
        var steps: Int = 0
        while c >= 0 && cBody[c] < 0 && steps <= tree.cCount {
            c = tree.parent[c]
            steps += 1
        }
        return c >= 0 ? cBody[c] : -1
    }

    /// The cell holding shown body b (a cell's region is its own body).
    func outermost(_ b: Int) -> Int {
        bodies[b].region
    }

    /// Bodies by name, then id.
    func lessBody(_ x: Int, _ y: Int) -> Bool {
        (bodies[x].title.lowercased(), bodies[x].id.uuidString) < (bodies[y].title.lowercased(), bodies[y].id.uuidString)
    }
}
