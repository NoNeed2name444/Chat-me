package com.cramdown.sorta.map

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Performance laid out by folders (S05i2), a port of iOS's GraphPerfGraph (the parts the seed, the
// force and the mask need) and GraphPerfLayout (ios/Sorta/Maps/GraphPerfGraph.swift,
// GraphPerfLayout.swift), with the look's numbers (PerfLook) and the folder lines (PerfLines).
// Pure Kotlin: NotesMap and the tour build it off the main thread; GlMapRenderer reads it.

/**
 * The built graph (iOS GraphPerfGraph). Notes are slots 0 until [noteCount] in id order; each folder
 * also has a hub, node noteCount + folder, so bodies run notes then hubs. A system is what the layout
 * places as one ball: every folder, then the loose-note groups ([folderCount] until [systemCount]).
 */
class PerfGraph(input: PerfInput) {
    val noteCount: Int = input.noteIds.size
    val folderCount: Int = input.folderIds.size
    val linkScale: Float = input.linkScale
    /** Slot to the caller's index, and back. */
    val inputOfNote: IntArray
    val noteOfInput: IntArray
    val inputOfFolder: IntArray
    val noteIds: List<String>
    val folderIds: List<String>
    val isPage: BooleanArray
    /** Each note's folder slot, −1 when loose. */
    val folder: IntArray
    /** After cycles are cut: −1 for a top folder. */
    val folderParent: IntArray
    val folderDepth: IntArray
    /** The top folder each folder sits under (its region). */
    val folderRegion: IntArray
    /** Children of folder f: folderChildren[folderChildStart[f] until folderChildStart[f + 1]]. */
    val folderChildStart: IntArray
    val folderChildren: IntArray
    /** Notes in a folder and all its subfolders (sizes the hub). */
    val folderSubtreeCount: IntArray
    val systemCount: Int
    val systemOfNote: IntArray
    val systemNoteStart: IntArray
    val systemNotes: IntArray
    /** Unique links, a < b, sorted. */
    val edgeA: IntArray
    val edgeB: IntArray
    /** CSR adjacency, each row ascending. */
    val adjStart: IntArray
    val adjacency: IntArray

    val linkCount: Int get() = edgeA.size
    val nodeCount: Int get() = noteCount + folderCount
    fun degree(i: Int): Int = adjStart[i + 1] - adjStart[i]
    fun hubNode(folder: Int): Int = noteCount + folder
    /** The region a note belongs to: its top folder, or folderCount + its loose group. */
    fun region(ofNote: Int): Int { val f = folder[ofNote]; return if (f >= 0) folderRegion[f] else systemOfNote[ofNote] }

    /**
     * Which bodies (notes, then hubs) [filter] lets through; null for all (GraphPerfGraph.mask,
     * GraphPerfGraph.swift:327-361). A folder's hub shows while any note under it does, and under a
     * folder filter every hub inside that folder shows; an unknown folder shows nothing.
     */
    fun mask(filter: MapFilter): BooleanArray? {
        val shown = BooleanArray(nodeCount)
        when (filter) {
            MapFilter.All -> return null
            MapFilter.Pages -> for (i in 0 until noteCount) shown[i] = isPage[i]
            MapFilter.Ideas -> for (i in 0 until noteCount) shown[i] = !isPage[i]
            MapFilter.Linked -> for (i in 0 until noteCount) shown[i] = degree(i) > 0
            is MapFilter.Folder -> {
                val wanted = folderIds.indexOfFirst { it.equals(filter.id, ignoreCase = true) }
                if (wanted < 0) return shown
                val inside = BooleanArray(folderCount)
                for (f in 0 until folderCount) {
                    var cur = f; var steps = 0
                    while (cur >= 0 && steps <= folderCount) {
                        if (cur == wanted) { inside[f] = true; break }
                        cur = folderParent[cur]; steps++
                    }
                    shown[noteCount + f] = inside[f]
                }
                for (i in 0 until noteCount) if (folder[i] >= 0) shown[i] = inside[folder[i]]
                return shown
            }
        }
        // hubs: any shown note in the subtree, children before parents
        val any = BooleanArray(folderCount)
        for (i in 0 until noteCount) if (shown[i] && folder[i] >= 0) any[folder[i]] = true
        for (f in deepestFirst(folderDepth)) if (any[f] && folderParent[f] >= 0) any[folderParent[f]] = true
        for (f in 0 until folderCount) shown[noteCount + f] = any[f]
        return shown
    }

    init {
        val n = noteCount
        val fc = folderCount
        // deterministic order: by id (upper case, so a UUID compares as its bytes), then input index
        val noteOrder = order(input.noteIds)
        val folderOrder = order(input.folderIds)
        val noteOf = IntArray(n) { -1 }
        for ((slot, i) in noteOrder.withIndex()) noteOf[i] = slot
        val folderOf = IntArray(fc) { -1 }
        for ((slot, i) in folderOrder.withIndex()) folderOf[i] = slot
        inputOfNote = noteOrder
        noteOfInput = noteOf
        inputOfFolder = folderOrder
        noteIds = noteOrder.map { input.noteIds[it] }
        folderIds = folderOrder.map { input.folderIds[it] }
        isPage = BooleanArray(n) { noteOrder[it] < input.isPage.size && input.isPage[noteOrder[it]] }
        folder = IntArray(n) { s ->
            val i = noteOrder[s]
            val f = if (i < input.folderOfNote.size) input.folderOfNote[i] else -1
            if (f in 0 until fc) folderOf[f] else -1
        }
        // the folder tree: a missing parent, or one closing a cycle, makes the folder top level; a cycle
        // is cut at its lowest slot, so the cut does not depend on input order
        val parent = IntArray(fc) { s ->
            val i = folderOrder[s]
            val p = if (i < input.folderParent.size) input.folderParent[i] else -1
            if (p in 0 until fc) folderOf[p] else -1
        }
        for (f in 0 until fc) if (parent[f] == f) parent[f] = -1
        val state = ByteArray(fc)
        val walk = IntList()
        for (start in 0 until fc) {
            if (state[start].toInt() != 0) continue
            walk.clear()
            var cur = start
            while (cur >= 0 && state[cur].toInt() == 0) { state[cur] = 1; walk.add(cur); cur = parent[cur] }
            if (cur >= 0 && state[cur].toInt() == 1) {
                var from = -1
                for (k in 0 until walk.size) if (walk[k] == cur) { from = k; break }
                if (from >= 0) {
                    var cut = Int.MAX_VALUE
                    for (k in from until walk.size) cut = min(cut, walk[k])
                    parent[cut] = -1
                }
            }
            for (k in 0 until walk.size) state[walk[k]] = 2
        }
        folderParent = parent
        // depth and region, memoised walk up
        val depth = IntArray(fc) { -1 }
        val region = IntArray(fc) { -1 }
        for (start in 0 until fc) {
            if (depth[start] >= 0) continue
            walk.clear()
            var cur = start
            while (cur >= 0 && depth[cur] < 0) { walk.add(cur); cur = parent[cur] }
            var d = if (cur >= 0) depth[cur] else -1
            val r = if (cur >= 0) region[cur] else walk[walk.size - 1]
            for (k in walk.size - 1 downTo 0) { d++; depth[walk[k]] = d; region[walk[k]] = r }
        }
        folderDepth = depth
        folderRegion = region
        val kids = buckets(fc, parent)
        folderChildStart = kids.first
        folderChildren = kids.second

        // unique links: (low, high) packed in one Long, sorted, deduplicated
        val keys = LongArray(input.edges.size / 2)
        var kc = 0
        var e = 0
        while (e + 1 < input.edges.size) {
            val a = input.edges[e]; val b = input.edges[e + 1]
            e += 2
            if (a !in 0 until n || b !in 0 until n) continue
            val sa = noteOf[a].toLong(); val sb = noteOf[b].toLong()
            if (sa == sb) continue
            keys[kc++] = if (sa < sb) (sa shl 32) or sb else (sb shl 32) or sa
        }
        java.util.Arrays.sort(keys, 0, kc)
        val ea = IntList(kc); val eb = IntList(kc)
        var last = -1L
        for (k in 0 until kc) {
            val key = keys[k]
            if (key == last) continue
            last = key
            ea.add((key ushr 32).toInt()); eb.add((key and 0xFFFF_FFFFL).toInt())
        }
        edgeA = ea.toArray()
        edgeB = eb.toArray()
        // CSR: count, prefix sum, fill; links are (low, high) sorted, so every row is ascending
        val start = IntArray(n + 1)
        for (k in edgeA.indices) { start[edgeA[k] + 1]++; start[edgeB[k] + 1]++ }
        for (i in 0 until n) start[i + 1] += start[i]
        val fill = start.copyOf()
        val nbr = IntArray(edgeA.size * 2)
        for (k in edgeA.indices) {
            val a = edgeA[k]; val b = edgeB[k]
            nbr[fill[a]++] = b
            nbr[fill[b]++] = a
        }
        adjStart = start
        adjacency = nbr

        // subtree counts: direct notes, then added up the tree deepest first
        val subtree = IntArray(fc)
        for (f in folder) if (f >= 0) subtree[f]++
        for (f in deepestFirst(depth)) if (parent[f] >= 0) subtree[parent[f]] += subtree[f]
        folderSubtreeCount = subtree

        // loose notes: connected components over links between loose notes (union-find), largest
        // first, ties by first member; singletons pooled into one last group; past MAX_LOOSE_GROUPS
        // the rest join the pool too
        val uf = IntArray(n) { -1 }
        for (i in 0 until n) if (folder[i] < 0) uf[i] = i
        fun root(i: Int): Int {
            var r = i
            while (uf[r] != r) { uf[r] = uf[uf[r]]; r = uf[r] }
            return r
        }
        for (k in edgeA.indices) {
            if (uf[edgeA[k]] < 0 || uf[edgeB[k]] < 0) continue
            val ra = root(edgeA[k]); val rb = root(edgeB[k])
            if (ra != rb) uf[max(ra, rb)] = min(ra, rb)
        }
        val size = IntArray(n)
        for (i in 0 until n) if (uf[i] >= 0) size[root(i)]++
        var roots = (0 until n).filter { uf[it] == it && size[it] > 1 }
            .sortedWith(compareByDescending<Int> { size[it] }.thenBy { it })
        if (roots.size > MAX_LOOSE_GROUPS) roots = roots.subList(0, MAX_LOOSE_GROUPS)
        val groupOfRoot = IntArray(n) { -1 }
        for ((g, r) in roots.withIndex()) groupOfRoot[r] = g
        val pooled = roots.size
        var anySingle = false
        val system = IntArray(n)
        for (i in 0 until n) {
            if (folder[i] >= 0) { system[i] = folder[i]; continue }
            val g = groupOfRoot[root(i)]
            if (g >= 0) system[i] = fc + g else { system[i] = fc + pooled; anySingle = true }
        }
        systemCount = fc + pooled + if (anySingle) 1 else 0
        systemOfNote = system
        val members = buckets(systemCount, system)
        systemNoteStart = members.first
        systemNotes = members.second
    }

    companion object {
        /** The most loose groups kept as their own systems (GraphPerfGraph.swift:85). */
        const val MAX_LOOSE_GROUPS = 256

        /** Indices of [ids] in upper-case order, ties by index. */
        private fun order(ids: List<String>): IntArray {
            val upper = Array(ids.size) { ids[it].uppercase() }
            return (ids.indices).sortedWith { a, b -> upper[a].compareTo(upper[b]).let { if (it != 0) it else a.compareTo(b) } }.toIntArray()
        }

        /** Items 0 until keys.size bucketed by key (counting sort; −1 left out), in index order. */
        fun buckets(count: Int, keys: IntArray): Pair<IntArray, IntArray> {
            val start = IntArray(count + 1)
            for (k in keys) if (k >= 0) start[k + 1]++
            for (b in 0 until count) start[b + 1] += start[b]
            val fill = start.copyOf()
            val items = IntArray(start[count])
            for ((i, k) in keys.withIndex()) if (k >= 0) items[fill[k]++] = i
            return start to items
        }

        /** Folder slots by depth, deepest first (children before parents). */
        fun deepestFirst(depth: IntArray): IntArray {
            val deepest = depth.maxOrNull() ?: 0
            return buckets(deepest + 1, IntArray(depth.size) { deepest - depth[it] }).second
        }
    }
}

/**
 * The seed layout (iOS GraphPerfLayout): [positions] x, y, z per body (notes, then hubs), [radius] per
 * body, and per system its centre, its own notes' ball ([coreRadius]) and the ball holding it and every
 * subfolder ([systemRadius]); [spacing] the link scale.
 */
class PerfSeed(val positions: FloatArray, val radius: FloatArray, val systemCentre: FloatArray,
               val coreRadius: FloatArray, val systemRadius: FloatArray, val spacing: Float) {
    /** The box every note keeps to whatever the force does (each stays in its system's core ball): min x, y, z, max x, y, z. */
    fun bounds(): FloatArray {
        val out = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (s in coreRadius.indices) for (a in 0 until 3) {
            out[a] = min(out[a], systemCentre[3 * s + a] - coreRadius[s])
            out[3 + a] = max(out[3 + a], systemCentre[3 * s + a] + coreRadius[s])
        }
        for (k in 0 until positions.size / 3) for (a in 0 until 3) {
            out[a] = min(out[a], positions[3 * k + a]); out[3 + a] = max(out[3 + a], positions[3 * k + a])
        }
        if (out[0] > out[3]) for (a in 0 until 6) out[a] = 0f
        return out
    }
}

object PerfLayout {
    const val NOTE_RADIUS = 0.18f
    const val PAGE_RADIUS = 0.28f
    /** Clearance past the outermost shell, more than the largest note. */
    const val MARGIN = 0.6f

    /** A hub's radius, 0.35 to 1.2, growing with the log of the notes under it (GraphPerfLayout.swift:36-38). */
    fun hubRadius(notes: Int): Float = min(1.2f, 0.35f + 0.085f * (ln(1.0 + notes) / ln(2.0)).toFloat())

    /** Points on a shell of [radius] spacings: about one per 1.1 square spacings of its surface. */
    fun shellPoints(radius: Float): Int = max(1, (4 * Math.PI.toFloat() * radius * radius / 1.1f).roundToInt())

    /** The radius (in spacings) of the last shell needed for [count] notes. */
    fun lastShell(count: Int, first: Float): Float {
        var placed = 0; var r = first
        while (true) {
            placed += shellPoints(r)
            if (placed >= count) return r
            r += 1
        }
    }

    /** The k-th of n points spread evenly over the unit sphere: x, y, z. */
    fun fibonacci(k: Int, n: Int): FloatArray {
        val y = 1 - (k + 0.5f) * 2 / max(n, 1).toFloat()
        val ring = sqrt(max(1 - y * y, 0f))
        val a = k * 2.399_963_2f
        return floatArrayOf(cos(a) * ring, y, sin(a) * ring)
    }

    /** The seed layout, or null once [isCancelled] says a newer one is wanted (checked per folder and per system). */
    fun seed(g: PerfGraph, isCancelled: () -> Boolean = { false }): PerfSeed? {
        val n = g.noteCount; val fc = g.folderCount; val sc = g.systemCount
        val s = if (g.linkScale > 0) g.linkScale else 1f
        val radius = FloatArray(n + fc)
        for (i in 0 until n) radius[i] = if (g.isPage[i]) PAGE_RADIUS else NOTE_RADIUS
        for (f in 0 until fc) radius[n + f] = hubRadius(g.folderSubtreeCount[f])
        // each system's own ball: the first shell clears the hub by half a spacing
        val firstShell = FloatArray(sc) { 0.5f }
        val core = FloatArray(sc)
        for (sys in 0 until sc) {
            val hub = if (sys < fc) radius[n + sys] else 0f
            firstShell[sys] = hub / s + 0.5f
            val count = g.systemNoteStart[sys + 1] - g.systemNoteStart[sys]
            core[sys] = (if (count > 0) lastShell(count, firstShell[sys]) * s else hub) + MARGIN
        }
        // children before parents: place each folder's subfolders round its ball
        val offset = FloatArray(sc * 3)
        val reach = core.copyOf()
        val packer = Packer()
        val deepest = PerfGraph.deepestFirst(g.folderDepth)
        for (f in deepest) {
            if (isCancelled()) return null
            val a = g.folderChildStart[f]; val b = g.folderChildStart[f + 1]
            if (b <= a) continue
            val kids = IntArray(b - a) { g.folderChildren[a + it] }
            packer.place(kids, core[f], s, reach, offset)
            for (c in kids) reach[f] = max(reach[f], length(offset, c) + reach[c])
        }
        // the top level: top folders and loose groups round the origin
        val tops = ((0 until fc).filter { g.folderParent[it] < 0 } + (fc until sc)).toIntArray()
        packer.place(tops, 0f, 2 * s, reach, offset)
        // parents before children: absolute centres
        val centre = offset.copyOf()
        for (k in deepest.indices.reversed()) {
            val f = deepest[k]
            val p = g.folderParent[f]
            if (p < 0) continue
            for (a in 0 until 3) centre[3 * f + a] = centre[3 * p + a] + offset[3 * f + a]
        }
        val positions = FloatArray((n + fc) * 3)
        for (f in 0 until fc) for (a in 0 until 3) positions[3 * (n + f) + a] = centre[3 * f + a]

        // notes: breadth first over the system's own links, each walk from the best unseen root
        // (pages first, then the most linked, then slot)
        val seen = BooleanArray(n)
        val queue = IntArray(n)
        for (sys in 0 until sc) {
            if (isCancelled()) return null
            val a = g.systemNoteStart[sys]; val b = g.systemNoteStart[sys + 1]
            if (b <= a) continue
            val roots = LongArray(b - a) { k ->
                val i = g.systemNotes[a + k]
                ((if (g.isPage[i]) 0L else 1L) shl 60) or ((0xFFF_FFFFL - min(g.degree(i), 0xFFF_FFFF)) shl 32) or i.toLong()
            }
            roots.sort()
            val shells = Shells(b - a, firstShell[sys], s, centre[3 * sys], centre[3 * sys + 1], centre[3 * sys + 2])
            var head = 0; var tail = 0
            for (r in roots) {
                val root = (r and 0xFFFF_FFFFL).toInt()
                if (seen[root]) continue
                seen[root] = true
                queue[tail++] = root
                while (head < tail) {
                    val i = queue[head++]
                    shells.next(positions, i)
                    for (e in g.adjStart[i] until g.adjStart[i + 1]) {
                        val j = g.adjacency[e]
                        if (seen[j] || g.systemOfNote[j] != sys) continue
                        seen[j] = true
                        queue[tail++] = j
                    }
                }
            }
        }
        return PerfSeed(positions, radius, centre, core, reach, s)
    }

    private fun length(v: FloatArray, i: Int): Float = sqrt(v[3 * i] * v[3 * i] + v[3 * i + 1] * v[3 * i + 1] + v[3 * i + 2] * v[3 * i + 2])

    /**
     * Hands out shell positions one by one (GraphPerfLayout.Shells): shell j has radius first + j
     * spacings, each shell twisted 0.7 more so neighbouring shells do not line up; the last shell
     * takes only what is left, spread over its whole sphere.
     */
    private class Shells(count: Int, first: Float, val spacing: Float, val cx: Float, val cy: Float, val cz: Float) {
        var left = count
        var r = first
        var shell = 0; var i = 0
        var m = min(shellPoints(first), count)
        var tc = 1f; var ts = 0f

        fun next(out: FloatArray, body: Int) {
            if (i == m) {
                left -= m
                shell++
                r += 1
                i = 0
                m = min(shellPoints(r), left)
                val twist = shell * 0.7f
                tc = cos(twist); ts = sin(twist)
            }
            val d = fibonacci(i, m)
            i++
            val k = r * spacing
            out[3 * body] = cx + (tc * d[0] - ts * d[2]) * k
            out[3 * body + 1] = cy + d[1] * k
            out[3 * body + 2] = cz + (ts * d[0] + tc * d[2]) * k
        }
    }

    /**
     * Exact greedy packing of sibling systems round a core ball (GraphPerfLayout.Packer): largest first,
     * each the nearest clear spot over 48 directions (24 above 100 siblings).
     */
    private class Packer {
        private var lo = FloatArray(64)
        private var hi = FloatArray(64)

        fun place(list: IntArray, core: Float, gap: Float, reach: FloatArray, offset: FloatArray) {
            val order = list.sortedWith { a, b -> if (reach[a] != reach[b]) reach[b].compareTo(reach[a]) else a.compareTo(b) }
            val dirs = if (order.size > 100) NARROW else WIDE
            val count = dirs.size / 3
            for ((k, c) in order.withIndex()) {
                var bestT = Float.POSITIVE_INFINITY
                var bx = 1f; var by = 0f; var bz = 0f
                for (q in 0 until count) {
                    val di = (q * 7 + k * 13) % count
                    val dx = dirs[3 * di]; val dy = dirs[3 * di + 1]; val dz = dirs[3 * di + 2]
                    var t = if (core > 0) core + reach[c] + gap else if (k == 0) 0f else reach[c] + gap
                    var spans = 0
                    for (j in 0 until k) {
                        val o = order[j]
                        val ox = offset[3 * o]; val oy = offset[3 * o + 1]; val oz = offset[3 * o + 2]
                        val r = reach[c] + reach[o] + gap
                        val b = dx * ox + dy * oy + dz * oz
                        val disc = b * b - (ox * ox + oy * oy + oz * oz) + r * r
                        if (disc <= 0) continue
                        val root = sqrt(disc)
                        if (b + root > t) {
                            if (spans == lo.size) { lo = lo.copyOf(spans * 2); hi = hi.copyOf(spans * 2) }
                            lo[spans] = b - root; hi[spans] = b + root; spans++
                        }
                    }
                    sortSpans(spans)
                    for (x in 0 until spans) {
                        if (lo[x] > t) break
                        if (hi[x] > t) t = hi[x] + 1e-4f
                    }
                    if (t < bestT) { bestT = t; bx = dx; by = dy; bz = dz }
                }
                offset[3 * c] = bx * bestT; offset[3 * c + 1] = by * bestT; offset[3 * c + 2] = bz * bestT
            }
        }

        /** Sorts the first [n] spans by their start (insertion sort: a few dozen at most per direction). */
        private fun sortSpans(n: Int) {
            for (a in 1 until n) {
                val l = lo[a]; val h = hi[a]
                var b = a - 1
                while (b >= 0 && lo[b] > l) { lo[b + 1] = lo[b]; hi[b + 1] = hi[b]; b-- }
                lo[b + 1] = l; hi[b + 1] = h
            }
        }

        companion object {
            val WIDE = FloatArray(48 * 3).also { out -> for (k in 0 until 48) fibonacci(k, 48).copyInto(out, 3 * k) }
            val NARROW = FloatArray(24 * 3).also { out -> for (k in 0 until 24) fibonacci(k, 24).copyInto(out, 3 * k) }
        }
    }
}

/**
 * Performance's look numbers (S05i2), iOS's: a body's drawn radius clamped 1.25-28 for a note and 3-48
 * for a hub, in points (dp here; GraphPerfLOD.swift:29-30), a note fading under about a pixel and a hub
 * never (:50-51), a hub's colour its region's mixed 0.45 to white (GraphPerfScene.swift:66-71), a folder
 * line ×1.2 and ×0.15 under Show links (GraphPerfShaders.swift:216-221). GlMapRenderer's shaders take
 * these numbers.
 */
object PerfLook {
    const val NOTE_MIN = 1.25f
    const val NOTE_MAX = 28f
    const val HUB_MIN = 3f
    const val HUB_MAX = 48f
    const val HUB_WHITE = 0.45f
    const val LINE_ALPHA = 1.2f
    const val LINE_FOCUS_FADE = 0.15f
    const val FADE_FROM = 0.35f
    const val FADE_TO = 1.25f
    /** The hub's largest radius, for the node record's one-byte hub code. */
    const val HUB_RADIUS_MAX = 1.2f

    /** The radius [rho] (dp) is drawn at: never so small it vanishes, never so big one body hides the map. */
    fun drawnRadius(rho: Float, hub: Boolean): Float = if (hub) rho.coerceIn(HUB_MIN, HUB_MAX) else rho.coerceIn(NOTE_MIN, NOTE_MAX)

    /** A body's opacity at radius [rho] (dp): a note fades out below about a pixel; a hub never fades. */
    fun alpha(rho: Float, hub: Boolean): Float = if (hub) 1f else smoothstep(FADE_FROM, FADE_TO, rho)

    /** A hub's colour from its region's (r, g, b): mixed 0.45 to white. */
    fun hubColour(rgb: FloatArray): FloatArray = FloatArray(3) { rgb[it] + (1f - rgb[it]) * HUB_WHITE }

    /** A folder line's brightness factor over a link's, under Show links ([focus]) or not. */
    fun lineAlpha(focus: Boolean): Float = LINE_ALPHA * if (focus) LINE_FOCUS_FADE else 1f

    /** A hub radius as the node record's byte (1…255; 0 is a note), and back. */
    fun hubCode(radius: Float): Int = (radius / HUB_RADIUS_MAX * 255f).roundToInt().coerceIn(1, 255)
    fun hubRadiusOf(code: Int): Float = code / 255f * HUB_RADIUS_MAX

    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/**
 * The folder lines (S05i2; GraphPerfScene.swift:76-85, GraphPerfClusters.swift:106-112): one per
 * folder with a parent, from its hub to its parent's hub, listed after the links, as body pairs.
 */
object PerfLines {
    /** Child hub and parent hub, by body (noteCount + folder), a pair a folder with a parent, in folder order. */
    fun of(g: PerfGraph): IntArray {
        val out = IntList(g.folderCount * 2)
        for (f in 0 until g.folderCount) {
            val p = g.folderParent[f]
            if (p < 0) continue
            out.add(g.hubNode(f)); out.add(g.hubNode(p))
        }
        return out.toArray()
    }

    /** Every line, links then folder lines, as body pairs: what iOS's lineEnds holds. */
    fun ends(g: PerfGraph): IntArray {
        val tree = of(g)
        val out = IntArray(2 * g.linkCount + tree.size)
        for (k in 0 until g.linkCount) { out[2 * k] = g.edgeA[k]; out[2 * k + 1] = g.edgeB[k] }
        tree.copyInto(out, 2 * g.linkCount)
        return out
    }

    /**
     * Whether line [k] of [lines] is drawn (GraphPerfClusters.swift:330-336): while either end is in view
     * and both show ([shown] by body, null for all).
     */
    fun visible(lines: IntArray, k: Int, inView: (Int) -> Boolean, shown: BooleanArray?): Boolean {
        val a = lines[2 * k]; val b = lines[2 * k + 1]
        if (shown != null && (a !in shown.indices || b !in shown.indices || !shown[a] || !shown[b])) return false
        return inView(a) || inView(b)
    }

    /** The hubs' index list (S05i2): each hub's chunk, in folder order, those with none left out. */
    fun hubIndices(hubChunks: IntArray): IntArray = hubChunks.filter { it >= 0 }.toIntArray()

    /**
     * An index buffer's first upload, as bytes and data: [indices] whole, or for none 4 bytes and no
     * data, as GL wants a store. Android's glBufferData throws when the data holds fewer bytes than
     * asked for, so the bytes are never more than the data's.
     */
    fun indexUpload(indices: IntArray): Pair<Int, IntArray?> =
        if (indices.isEmpty()) 4 to null else indices.size * 4 to indices
}

/**
 * The Performance map's parts (S05i2): the graph, the seed, the folder lines (child and parent hub by
 * body) and, for each body, its group (its region's palette slot) and hub code (0 a note).
 */
class PerfMap(val graph: PerfGraph, val seed: PerfSeed) {
    val lines: IntArray = PerfLines.of(graph)
    /** Each body's colour group: its region's top-folder slot among the tops, mod the palette; loose notes 0. */
    val groups: ByteArray
    val hubCodes: ByteArray

    init {
        val g = graph
        val topRank = IntArray(g.folderCount) { -1 }
        var r = 0
        for (f in 0 until g.folderCount) if (g.folderParent[f] < 0) topRank[f] = r++
        groups = ByteArray(g.nodeCount) { i ->
            val f = if (i < g.noteCount) g.folder[i] else i - g.noteCount
            if (f < 0) 0 else (topRank[g.folderRegion[f]].coerceAtLeast(0) % MapGraph.PALETTE_SIZE).toByte()
        }
        hubCodes = ByteArray(g.nodeCount) { i -> if (i < g.noteCount) 0 else PerfLook.hubCode(seed.radius[i]).toByte() }
    }

    /** The bodies as the octree's input: notes then hubs, at their seed places, with the links. */
    fun mapGraph(titles: List<String>, ids: List<String>): MapGraph {
        val edges = IntArray(graph.linkCount * 2)
        for (k in 0 until graph.linkCount) { edges[2 * k] = graph.edgeA[k]; edges[2 * k + 1] = graph.edgeB[k] }
        return MapGraph(seed.positions.copyOf(), groups, edges, titles, ids)
    }

    companion object {
        /** The graph and its seed, or null once [isCancelled] says so. */
        fun build(input: PerfInput, isCancelled: () -> Boolean = { false }): PerfMap? {
            val g = PerfGraph(input)
            if (isCancelled()) return null
            val seed = PerfLayout.seed(g, isCancelled) ?: return null
            return PerfMap(g, seed)
        }
    }
}
