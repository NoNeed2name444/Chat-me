package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Neurons theme's plan, ported from iOS (GraphNeurons, GraphNeuronImpulses): the ideas'
// folders drawn as living cells floating in a dark fluid.
//
// - a top-level folder is a cell on one sheet facing the camera, senders first;
// - a folder inside it is a part inside its nucleus, a folder inside that a smaller part;
// - a note is a vesicle (a page) or a granule (an idea) inside the folder that holds it;
// - insides show only while their folder is opened (UniverseInput.open);
// - a note in no folder is a free cell at the edge: a receptor with links, a drifter without;
// - with no folders, one home cell holds every note, always open.
//
// Links are the cells' processes: one per pair of ends, none dropped. Pure and deterministic.

/** What each body is in the Neurons theme (ThemeBody.role). */
enum class NeuronRole(val code: Int) {
    CELL(0), PART(1), HOME(2), VESICLE(3), GRANULE(4), DRIFTER(5), RECEPTOR(6);

    val rank: Int get() = when (this) {
        CELL, HOME -> 7
        PART, RECEPTOR -> 6
        VESICLE -> 5
        GRANULE -> 3
        DRIFTER -> 1
    }

    val isContainer: Boolean get() = this == CELL || this == PART || this == HOME
    val isFree: Boolean get() = this == DRIFTER || this == RECEPTOR

    companion object {
        fun of(code: Int): NeuronRole? = entries.firstOrNull { it.code == code }
    }
}

data class NeuronZones(val parts: Double, val core: Double, val room: Double)

data class NeuronNoteLink(val from: Int, val to: Int, val strength: Int, val kind: FiberKind)

data class NeuronItem(val container: Int, val note: Int, val radius: Double, val at: D3 = D3.ZERO) {
    val isPart: Boolean get() = container >= 0
}

object GraphNeurons {
    const val INNER = 0.8
    const val OPEN_NUCLEUS = 0.6
    const val NUCLEUS_ROOM = 0.9
    const val ROOM = 0.05
    const val WOBBLE = 0.015
    const val FILL = 0.22
    const val CELL_DRIFT = 0.012
    const val REACH = 1.9
    const val SHEET_GAP = 2.8
    const val WOBBLE_BOUND = 1.5653

    fun cellSphere(count: Int): Double = GraphUniverse.clamp(0.34 + 0.035 * log2(1 + count.toDouble()), 0.34, 0.6)

    fun partShare(count: Int): Double = GraphUniverse.clamp(0.1 + 0.03 * log2(1 + count.toDouble()), 0.1, 0.22)

    fun zones(radius: Double, cell: Boolean): NeuronZones {
        val room = INNER * radius
        if (!cell) return NeuronZones(room, 0.0, room)
        return NeuronZones(NUCLEUS_ROOM * OPEN_NUCLEUS * radius, OPEN_NUCLEUS * radius, room)
    }

    fun noteShare(page: Boolean, words: Int): Double {
        val level = min(GraphUniverse.level(words), 6).toDouble()
        return if (page) 0.07 + 0.003 * level else 0.05 + 0.002 * level
    }

    fun freeSphere(receptor: Boolean, page: Boolean): Double = (if (receptor) 0.16 else 0.12) + (if (page) 0.03 else 0.0)

    fun freeDrift(receptor: Boolean): Double = if (receptor) 0.03 else 0.06

    fun plan(input: UniverseInput): ThemePlan {
        if (input.notes.isEmpty() && input.folders.isEmpty()) return ThemePlan.EMPTY
        return NeuronPlanner(input).run()
    }

    /** "2 cells, 3 parts, 12 granules, ...". */
    fun summary(bodies: List<ThemeBody>): String {
        val counts = IntArray(NeuronRole.entries.size)
        for (body in bodies) if (body.role >= 0 && body.role < counts.size) counts[body.role] += 1
        counts[NeuronRole.CELL.code] += counts[NeuronRole.HOME.code]
        val words = listOf(
            Triple(NeuronRole.CELL, "cell", "cells"), Triple(NeuronRole.PART, "part", "parts"),
            Triple(NeuronRole.VESICLE, "vesicle", "vesicles"), Triple(NeuronRole.GRANULE, "granule", "granules"),
            Triple(NeuronRole.RECEPTOR, "receptor", "receptors"), Triple(NeuronRole.DRIFTER, "free cell", "free cells"),
        )
        val parts = ArrayList<String>()
        for ((role, one, many) in words) {
            val n = counts[role.code]
            if (n > 0) parts.add("$n " + if (n == 1) one else many)
        }
        return parts.joinToString(", ")
    }
}

/** The plan's working state, on the Universe planner's tree. */
class NeuronPlanner(input: UniverseInput) {
    val tree = UniverseTree(input)
    var shown = BooleanArray(0)
    var opened = BooleanArray(0)
    var noteLinks: List<NeuronNoteLink> = emptyList()
    var sends: List<HashMap<Int, Int>> = emptyList()
    var score = IntArray(0)
    var tops: List<Int> = emptyList()
    var biggest = 0.0
    var gap = 0.0
    var cSphere = DoubleArray(0)
    var cWorld: Array<D3> = emptyArray()
    var notesIn: List<ArrayList<Int>> = emptyList()
    val looseBalls = ArrayList<ThemeBall>()
    val bodies = ArrayList<ThemeBody>()
    var noteBody = IntArray(0)
    var cBody = IntArray(0)
    val systems = ArrayList<List<Vec3>>()

    private val cOrder = Comparator<Int> { a, b -> if (tree.lessC(a, b)) -1 else if (tree.lessC(b, a)) 1 else 0 }
    private val nOrder = Comparator<Int> { a, b -> if (tree.lessN(a, b)) -1 else if (tree.lessN(b, a)) 1 else 0 }

    fun run(): ThemePlan {
        tree.buildTree()
        tree.buildLinks()
        readLinks()
        markShown()
        readFlow()
        orderTops()
        placeTops()
        notesIn = List(tree.cCount) { ArrayList() }
        tree.noteHome.forEachIndexed { i, home -> if (home >= 0) notesIn[home].add(i) }
        noteBody = IntArray(tree.noteList.size) { -1 }
        cBody = IntArray(tree.cCount) { -1 }
        for (c in tops) emitTop(c)
        val regions = tops.map { cBody[it] }
        placeLoose()
        val balls = ArrayList<ThemeBall>()
        for (c in tops) balls.add(ThemeBall(cWorld[c], topReach(c) + 0.1))
        for (ball in looseBalls) balls.add(ThemeBall(ball.c, ball.r + 0.1))
        val envelope = ThemeLayout.points(balls, D3.ZERO)
        return ThemePlan(bodies.toList(), processes(), envelope, systems.toList(), regions, GraphNeurons.summary(bodies))
    }

    // reading

    fun readLinks() {
        val anatomy = HashMap<String, AnatomyNote>()
        tree.input.anatomy?.notes?.forEach { anatomy[it.id] = it }
        val n = tree.noteList.size.toLong()
        val index = HashMap<String, Int>()
        tree.noteList.forEachIndexed { i, note -> index[note.id] = i }
        val ways = HashSet<Long>()
        for (edge in tree.input.edges) {
            val i = index[edge.a] ?: continue
            val j = index[edge.b] ?: continue
            if (i == j) continue
            ways.add(i * n + j)
        }
        val out = ArrayList<NeuronNoteLink>()
        for ((i, j) in tree.edgePairs) {
            var read = 0
            val a = anatomy[tree.noteList[i].id]
            val b = anatomy[tree.noteList[j].id]
            if (a != null && b != null) {
                GraphAnatomy.link(a, b)?.let { out.add(NeuronNoteLink(i, j, it.strength, it.kind)); read += 1 }
                GraphAnatomy.link(b, a)?.let { out.add(NeuronNoteLink(j, i, it.strength, it.kind)); read += 1 }
            }
            if (read != 0) continue
            if (ways.contains(i * n + j)) out.add(NeuronNoteLink(i, j, 4, FiberKind.EXCITATORY))
            if (ways.contains(j * n + i)) out.add(NeuronNoteLink(j, i, 4, FiberKind.EXCITATORY))
        }
        noteLinks = out
    }

    fun markShown() {
        val wanted = tree.input.open.toHashSet()
        shown = BooleanArray(tree.cCount)
        opened = BooleanArray(tree.cCount)
        val outerFirst = (0 until tree.cCount).sortedBy { tree.depth[it] }
        for (c in outerFirst) {
            val p = tree.parent[c]
            shown[c] = p < 0 || (shown[p] && opened[p])
            opened[c] = shown[c] && (!tree.hasFolders || wanted.contains(tree.cID(c)))
        }
    }

    fun readFlow() {
        sends = List(tree.cCount) { HashMap() }
        score = IntArray(tree.cCount)
        for (link in noteLinks) {
            val a = tree.noteHome[link.from]
            val b = tree.noteHome[link.to]
            if (a < 0 || b < 0) continue
            val ta = tree.top[a]
            val tb = tree.top[b]
            if (ta == tb) continue
            sends[ta][tb] = (sends[ta][tb] ?: 0) + link.strength
            score[ta] += link.strength
            score[tb] -= link.strength
        }
    }

    // the sheet

    fun orderTops() {
        tops = (0 until tree.cCount).filter { tree.parent[it] < 0 }.sortedWith { a, b ->
            when {
                score[a] != score[b] -> if (score[a] > score[b]) -1 else 1
                tree.count[a] != tree.count[b] -> if (tree.count[a] > tree.count[b]) -1 else 1
                else -> cOrder.compare(a, b)
            }
        }
    }

    fun placeTops() {
        cSphere = DoubleArray(tree.cCount)
        cWorld = Array(tree.cCount) { D3.ZERO }
        biggest = 0.0
        for (c in tops) {
            cSphere[c] = GraphNeurons.cellSphere(tree.count[c])
            biggest = max(biggest, cSphere[c])
        }
        gap = GraphNeurons.SHEET_GAP * biggest * tree.input.spacing
        val pitch = 2 * biggest + gap
        val n = tops.size
        val cols = if (n <= 2) 1 else if (n <= 6) 2 else max(3, ceil(sqrt(n * 0.5)).toInt())
        val rows = (n + cols - 1) / cols
        tops.forEachIndexed { k, c ->
            val row = k / cols
            val inRow = min(cols, n - row * cols)
            var col = k % cols
            if (row % 2 == 1) col = inRow - 1 - col
            val random = UniverseRandom(tree.cSeed(c) xor 0x5EE7uL)
            var x = col.toDouble() - (inRow - 1) * 0.5
            if (rows > 1) x += if (row % 2 == 0) 0.18 else -0.18
            x += random.signed() * 0.03
            val y = (rows - 1) * 0.5 - row + random.signed() * 0.03
            cWorld[c] = D3(x * pitch, y * pitch, 0.0)
        }
        // real depth: each cell lifted to A·u(id), judged at the standard link length
        val standard = (2 * biggest + GraphNeurons.SHEET_GAP * biggest) / max(pitch, 0.000001)
        val clusters = tops.map { c ->
            GraphTilt.Cluster(tree.liftKey(c), (cWorld[c].x * standard).toFloat(), (cWorld[c].y * standard).toFloat(),
                cSphere[c].toFloat())
        }
        val lift = GraphTilt.amplitude(clusters)
        for (c in tops) cWorld[c] = cWorld[c].copy(z = GraphTilt.z(tree.liftKey(c), lift).toDouble())
    }

    fun topReach(c: Int): Double = cSphere[c] * GraphNeurons.REACH + GraphNeurons.CELL_DRIFT * GraphNeurons.WOBBLE_BOUND

    private fun favourite(map: Map<Int, Int>): Int {
        var best = -1
        var most = 0
        for ((t, s) in map) {
            if (s > most || (s == most && best >= 0 && tree.lessC(t, best))) { best = t; most = s }
        }
        return best
    }

    fun topAxis(c: Int): D3 {
        val best = favourite(sends[c])
        if (best < 0) return D3(0.0, -1.0, 0.0)
        val d = GraphUniverse.normalize(cWorld[best] - cWorld[c])
        return if (GraphUniverse.length(d) > 0.5) d else D3(0.0, -1.0, 0.0)
    }

    // the bodies

    fun emitTop(c: Int) {
        val random = UniverseRandom(tree.cSeed(c) xor 0xD81FuL)
        val phase = (2 * PI * random.unit()).toFloat()
        val rate = (0.12 + 0.08 * random.unit()).toFloat()
        val orbit = GraphOrbit.Drift(GraphUniverse.float3(cWorld[c]), GraphNeurons.CELL_DRIFT.toFloat(), phase, rate)
        emitContainer(c, cSphere[c], -1, -1, orbit, topAxis(c))
    }

    fun emitContainer(c: Int, radius: Double, parent: Int, region: Int, orbit: GraphOrbit, axis: D3) {
        val role = if (!tree.hasFolders) NeuronRole.HOME else if (tree.depth[c] == 0) NeuronRole.CELL else NeuronRole.PART
        val index = bodies.size
        val base = if (parent >= 0) bodies[parent].home else Vec3.ZERO
        val mine = if (region >= 0) region else index
        bodies.add(ThemeBody(tree.cID(c), if (role == NeuronRole.HOME) ThemeBodyKind.HOME else ThemeBodyKind.FOLDER,
            role.code, parent, radius.toFloat(), tree.depth[c], mine, tree.count[c], 0, 0, role.rank, tree.cSeed(c),
            orbit, base + GraphUniverse.offset(orbit, 0.0), GraphUniverse.float3(axis), tree.cName(c),
            UniverseTree.folderLabel(tree.cName(c), tree.count[c])))
        cBody[c] = index
        cSphere[c] = radius
        systems.add(ThemeLayout.points(listOf(ThemeBall(D3.ZERO, radius * 1.1)), D3.ZERO))
        if (!opened[c]) return
        val (items, ampD) = pack(c, radius)
        val amp = ampD.toFloat()
        for (item in items) {
            if (!item.isPart) continue
            val k = item.container
            emitContainer(k, item.radius, index, mine, drift(item.at, amp, tree.cSeed(k)), outward(item.at))
        }
        for (item in items) {
            if (item.isPart) continue
            val i = item.note
            val r = if (tree.noteList[i].isPage) NeuronRole.VESICLE else NeuronRole.GRANULE
            emitNote(i, r, item.radius, index, mine, drift(item.at, amp, tree.nSeed(i)), outward(item.at))
        }
    }

    fun emitNote(i: Int, role: NeuronRole, radius: Double, parent: Int, region: Int, orbit: GraphOrbit, axis: D3) {
        val note = tree.noteList[i]
        val base = if (parent >= 0) bodies[parent].home else Vec3.ZERO
        noteBody[i] = bodies.size
        bodies.add(ThemeBody(note.id, ThemeBodyKind.NOTE, role.code, parent, radius.toFloat(), -1, region, 0,
            tree.nbr[i].size, note.words, role.rank, tree.nSeed(i), orbit, base + GraphUniverse.offset(orbit, 0.0),
            GraphUniverse.float3(axis), note.title, UniverseTree.noteLabel(note.title)))
        systems.add(emptyList())
    }

    fun drift(at: D3, amp: Float, seed: ULong): GraphOrbit {
        val random = UniverseRandom(seed xor 0xF1A7uL)
        val phase = (2 * PI * random.unit()).toFloat()
        val rate = (0.15 + 0.1 * random.unit()).toFloat()
        return GraphOrbit.Drift(GraphUniverse.float3(at), amp, phase, rate)
    }

    fun outward(at: D3): D3 = if (GraphUniverse.length(at) > 1e-12) GraphUniverse.normalize(at) else D3(0.0, 1.0, 0.0)

    // inside a container

    fun pack(c: Int, radius: Double): Pair<List<NeuronItem>, Double> {
        val parts = tree.kids[c].map { NeuronItem(it, -1, GraphNeurons.partShare(tree.count[it]) * radius) }
        val notes = notesIn[c].map { i ->
            val note = tree.noteList[i]
            NeuronItem(-1, i, GraphNeurons.noteShare(note.isPage, note.words) * radius)
        }
        val zones = GraphNeurons.zones(radius, tree.depth[c] == 0)
        val gap = GraphNeurons.ROOM * zones.room
        val amp = GraphNeurons.WOBBLE * zones.room
        val random = UniverseRandom(tree.cSeed(c) xor 0xC0FFEE11uL)
        if (!(zones.core > 0)) {
            val all = fit(biggestFirst(parts + notes), zones.room, 0.0, gap, amp, 1.0, random)
            return Pair(all.first, amp * all.second)
        }
        val inner = fit(biggestFirst(parts), zones.parts, 0.0, gap, amp, 1.0, random)
        val outer = fit(biggestFirst(notes), zones.room, zones.core, gap, amp, inner.second, random)
        return Pair(inner.first + outer.first, amp * min(inner.second, outer.second))
    }

    fun biggestFirst(items: List<NeuronItem>): List<NeuronItem> = items.sortedWith { a, b ->
        when {
            a.radius != b.radius -> if (a.radius > b.radius) -1 else 1
            a.isPart != b.isPart -> if (a.isPart) -1 else 1
            a.isPart -> cOrder.compare(a.container, b.container)
            else -> nOrder.compare(a.note, b.note)
        }
    }

    fun fit(items: List<NeuronItem>, room: Double, core: Double, gap: Double, amp: Double, most: Double,
            random: UniverseRandom): Pair<List<NeuronItem>, Double> {
        if (items.isEmpty()) return Pair(emptyList(), 1.0)
        val sway = amp * GraphNeurons.WOBBLE_BOUND
        var need = 0.0
        for (item in items) need += (item.radius + gap * 0.5).pow(3)
        var scale = min(most, cbrt(GraphNeurons.FILL * (room.pow(3) - core.pow(3)) / need))
        val r1 = items[0].radius
        scale = min(scale, if (core > 0) (room - core) / (2 * r1 + gap + sway) else room / (r1 + sway))
        if (items.size > 1) scale = min(scale, 2 * room / (2 * (r1 + items[1].radius) + gap + 2 * sway))
        val m = max(600, 16 * items.size)
        val spots = ArrayList<D3>(m)
        while (spots.size < m) {
            val p = D3(random.signed(), random.signed(), random.signed())
            if (GraphUniverse.dot(p, p) <= 1) spots.add(p)
        }
        for (t in 0 until 80) {
            val placed = place(items, spots, room, core, gap * scale, sway * scale, scale)
            if (placed != null) return Pair(placed, scale)
            scale *= 0.85
        }
        return Pair(items.map { NeuronItem(it.container, it.note, 0.0) }, 0.0)
    }

    // free cells

    fun placeLoose() {
        val loose = (0 until tree.noteList.size).filter { tree.noteHome[it] < 0 }.sortedWith(nOrder)
        if (loose.isEmpty()) return
        val placed = ArrayList(tops.map { ThemeBall(cWorld[it], topReach(it)) })
        var lx = 0.0; var ly = 0.0; var hx = 0.0; var hy = 0.0
        placed.firstOrNull()?.let { f -> lx = f.c.x - f.r; ly = f.c.y - f.r; hx = f.c.x + f.r; hy = f.c.y + f.r }
        var lz = placed.firstOrNull()?.let { it.c.z - it.r } ?: 0.0
        var hz = placed.firstOrNull()?.let { it.c.z + it.r } ?: 0.0
        for (ball in placed) {
            lx = min(lx, ball.c.x - ball.r); ly = min(ly, ball.c.y - ball.r); lz = min(lz, ball.c.z - ball.r)
            hx = max(hx, ball.c.x + ball.r); hy = max(hy, ball.c.y + ball.r); hz = max(hz, ball.c.z + ball.r)
        }
        val centre = D3((lx + hx) * 0.5, (ly + hy) * 0.5, (lz + hz) * 0.5)
        val halfX = (hx - lx) * 0.5
        val halfY = (hy - ly) * 0.5
        val margin = 0.5 * max(biggest, GraphNeurons.cellSphere(0)) * tree.input.spacing
        val ax = halfX * sqrt(2.0) + margin
        val ay = halfY * sqrt(2.0) + margin
        val facing = HashMap<Int, Int>()
        loose.forEachIndexed { k, i ->
            val receptor = tree.nbr[i].isNotEmpty()
            var angle = 0.5 + GraphUniverse.GOLDEN * k
            val toward = if (receptor) favouriteTop(i) else -1
            if (toward >= 0) {
                val d = cWorld[toward] - centre
                if (d.x * d.x + d.y * d.y > 1e-6) {
                    val j = facing[toward] ?: 0
                    facing[toward] = j + 1
                    angle = atan2(d.y / ay, d.x / ax) + 0.35 * ((j + 1) / 2).toDouble() * (if (j % 2 == 0) 1 else -1)
                }
            }
            val sphere = GraphNeurons.freeSphere(receptor, tree.noteList[i].isPage)
            val amp = GraphNeurons.freeDrift(receptor)
            val reach = sphere * (if (receptor) GraphNeurons.REACH else 1.15) + amp * GraphNeurons.WOBBLE_BOUND
            val random = UniverseRandom(tree.nSeed(i) xor 0x10C5uL)
            val flat = D3(ax * cos(angle), ay * sin(angle), 0.0)
            val start = centre + flat + D3(0.0, 0.0, random.signed() * 0.15)
            var out = GraphUniverse.normalize(flat)
            if (GraphUniverse.length(out) < 0.5) out = D3(1.0, 0.0, 0.0)
            val s = ThemeLayout.slide(listOf(ThemeBall(start, reach)), out, 0.0, 0.04, placed, 0.04)
            val at = start + out * s
            placed.add(ThemeBall(at, reach))
            looseBalls.add(ThemeBall(at, reach))
            var axis = out
            if (toward >= 0) {
                val d = GraphUniverse.normalize(cWorld[toward] - at)
                if (GraphUniverse.length(d) > 0.5) axis = d
            }
            emitNote(i, if (receptor) NeuronRole.RECEPTOR else NeuronRole.DRIFTER, sphere, -1, -1,
                drift(at, amp.toFloat(), tree.nSeed(i)), axis)
        }
    }

    fun favouriteTop(i: Int): Int {
        val with = HashMap<Int, Int>()
        for (link in noteLinks) {
            if (link.from != i && link.to != i) continue
            val other = if (link.from == i) link.to else link.from
            val home = tree.noteHome[other]
            if (home < 0) continue
            with[tree.top[home]] = (with[tree.top[home]] ?: 0) + link.strength
        }
        return favourite(with)
    }

    // processes

    fun processes(): List<ThemeLink> {
        val forward = HashMap<Pair<Int, Int>, ArrayList<NeuronNoteLink>>()
        val backward = HashMap<Pair<Int, Int>, ArrayList<NeuronNoteLink>>()
        for (link in noteLinks) {
            var x = shownBody(link.from)
            var y = shownBody(link.to)
            if (x < 0 || y < 0 || x == y) continue
            if (bodies[x].region < 0 || bodies[x].region != bodies[y].region) { x = outermost(x); y = outermost(y) }
            if (x < y) forward.getOrPut(Pair(x, y)) { ArrayList() }.add(link)
            else backward.getOrPut(Pair(y, x)) { ArrayList() }.add(link)
        }
        val keys = (forward.keys + backward.keys).sortedWith(compareBy({ it.first }, { it.second }))
        val out = ArrayList<ThemeLink>(keys.size)
        for (key in keys) {
            val ab = forward[key] ?: emptyList<NeuronNoteLink>()
            val ba = backward[key] ?: emptyList<NeuronNoteLink>()
            val sends = if (ab.size != ba.size) ab.size > ba.size else lessBody(key.first, key.second)
            val all = ab + ba
            var total = 0
            val weight = IntArray(3)
            for (link in all) { total += link.strength; weight[link.kind.code] += link.strength }
            val mean = total.toDouble() / all.size
            val strength = GraphUniverse.clamp(GraphUniverse.roundHalfAway(mean + log2(all.size.toDouble())), 1.0, 10.0).toInt()
            var kind = FiberKind.EXCITATORY
            if (weight[1] > weight[0] && weight[1] >= weight[2]) kind = FiberKind.INHIBITORY
            else if (weight[2] > weight[0] && weight[2] > weight[1]) kind = FiberKind.MODULATORY
            val from = if (sends) key.first else key.second
            val to = if (sends) key.second else key.first
            val inside = bodies[from].region >= 0 && bodies[from].region == bodies[to].region
            out.add(ThemeLink(from, to, if (inside) 5 else 6, -1, kind.code, (0.55 + 0.12 * strength).toFloat()))
        }
        return out
    }

    fun shownBody(i: Int): Int {
        if (noteBody[i] >= 0) return noteBody[i]
        var c = tree.noteHome[i]
        var steps = 0
        while (c >= 0 && cBody[c] < 0 && steps <= tree.cCount) { c = tree.parent[c]; steps += 1 }
        return if (c >= 0) cBody[c] else -1
    }

    fun outermost(b: Int): Int = if (bodies[b].region >= 0) bodies[b].region else b

    fun lessBody(x: Int, y: Int): Boolean =
        lessPair(Pair(bodies[x].title.lowercase(), bodies[x].id), Pair(bodies[y].title.lowercase(), bodies[y].id))

    companion object {
        fun place(items: List<NeuronItem>, spots: List<D3>, room: Double, core: Double, gap: Double, sway: Double,
                  scale: Double): List<NeuronItem>? {
            val placed = ArrayList<ThemeBall>(items.size + 1)
            if (core > 0) placed.add(ThemeBall(D3.ZERO, core))
            val out = ArrayList<NeuronItem>(items.size)
            var next = 0
            for (item in items) {
                val r = item.radius * scale
                val reach = room - r - sway
                val low = if (core > 0) core + gap + r else 0.0
                if (!(reach >= low)) return null
                var j = next
                while (j < spots.size && clearance(spread(spots[j], low, reach), r, placed, gap) < 0) j += 1
                if (j >= spots.size) return null
                val at = spread(spots[j], low, reach)
                placed.add(ThemeBall(at, r))
                out.add(NeuronItem(item.container, item.note, r, at))
                next = j + 1
            }
            return out
        }

        fun spread(p: D3, low: Double, high: Double): D3 {
            if (!(low > 0)) return p * high
            val d = GraphUniverse.length(p)
            val u = if (d > 1e-12) p / d else D3(0.0, 1.0, 0.0)
            return u * (low + (high - low) * d)
        }

        fun clearance(p: D3, r: Double, placed: List<ThemeBall>, gap: Double): Double {
            var least = Double.POSITIVE_INFINITY
            for (ball in placed) {
                val d = p - ball.c
                val clear = sqrt(GraphUniverse.dot(d, d)) - r - ball.r - gap
                if (clear < 0) return clear
                least = min(least, clear)
            }
            return least
        }
    }
}

/** When each axon fires (iOS NeuronImpulse): the shader and the render thread share this hash. */
object NeuronImpulse {
    const val BURST_GAP = 0.12f
    const val SLOTS_BACK = 3
    const val PULSES_PER_BURST = 3

    fun hash(x: UInt): UInt {
        var h = x * 747796405u + 2891336453u
        val shift = (h shr 28) + 4u
        h = ((h shr shift.toInt()) xor h) * 277803737u
        return (h shr 22) xor h
    }

    fun byte(h: UInt, at: Int): Float = ((h shr at) and 255u).toFloat() / 255f

    data class Rhythm(val slot: Float, val travel: Float, val phase: Float)

    fun rhythm(seed: Int): Rhythm {
        val h = hash(seed.toUInt())
        val slot = 0.9f + 1.5f * byte(h, 0)
        return Rhythm(slot, 0.45f + 0.5f * byte(h, 8), byte(h, 16) * slot)
    }

    fun slotHash(seed: Int, k: Float): UInt {
        val slot = max(k, 0f).toUInt()
        return hash(slot * 2654435761u + seed.toUInt() * 40503u + 17u)
    }

    fun arrivals(seed: Int, from: Float, to: Float, rate: Float, bursts: Float): List<Float> {
        if (!(to > from)) return emptyList()
        val r = rhythm(seed)
        val now = floor((to + r.phase) / r.slot)
        val found = ArrayList<Float>()
        for (j in 0 until SLOTS_BACK) {
            val k = now - j
            val g = slotHash(seed, k)
            if (!(byte(g, 0) < rate)) continue
            val fired = k * r.slot + 0.5f * r.slot * byte(g, 8)
            val many = if (byte(g, 16) < bursts) PULSES_PER_BURST else 1
            for (b in 0 until many) {
                val lands = fired + b * BURST_GAP + r.travel - r.phase
                if (lands > from && lands <= to) found.add(lands)
            }
        }
        return found
    }

    fun lands(seed: Int, from: Float, to: Float, rate: Float, bursts: Float): Boolean {
        if (!(to > from)) return false
        val r = rhythm(seed)
        val now = floor((to + r.phase) / r.slot)
        for (j in 0 until SLOTS_BACK) {
            val k = now - j
            val g = slotHash(seed, k)
            if (!(byte(g, 0) < rate)) continue
            val fired = k * r.slot + 0.5f * r.slot * byte(g, 8)
            val many = if (byte(g, 16) < bursts) PULSES_PER_BURST else 1
            for (b in 0 until many) {
                val at = fired + b * BURST_GAP + r.travel - r.phase
                if (at > from && at <= to) return true
            }
        }
        return false
    }

    fun firings(seed: Int, from: Float, to: Float, rate: Float): List<Float> {
        val r = rhythm(seed)
        val out = ArrayList<Float>()
        var k = floor((from + r.phase) / r.slot)
        while (k * r.slot - r.phase < to) {
            val g = slotHash(seed, k)
            if (byte(g, 0) < rate) {
                val at = k * r.slot + 0.5f * r.slot * byte(g, 8) - r.phase
                if (at >= from && at < to) out.add(at)
            }
            k += 1f
        }
        return out
    }
}
