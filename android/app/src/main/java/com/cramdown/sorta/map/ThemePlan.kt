package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// What a theme planner (Neurons) reads and hands over, in plain Kotlin so it runs in JVM
// tests: the vault as the map sees it, the Universe planner's folder tree, a body's moves,
// the plan itself, overlap-free placing, the tilted view's real-depth lift and the anatomy's
// link reading. Ported from iOS (GraphUniverse, GraphThemePlan, GraphTilt, GraphAnatomy);
// ids are uppercase UUID strings, as iOS's uuidString compares them.

/** A Double vector (iOS SIMD3<Double>). */
data class D3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: D3) = D3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: D3) = D3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = D3(x * k, y * k, z * k)
    operator fun div(k: Double) = D3(x / k, y / k, z / k)
    operator fun unaryMinus() = D3(-x, -y, -z)
    companion object { val ZERO = D3(0.0, 0.0, 0.0) }
}

data class UniverseNote(
    val id: String, val title: String, val isPage: Boolean, val folder: String?, val words: Int,
    val created: Double = 0.0,
)

data class UniverseFolder(val id: String, val name: String, val parent: String?)

data class UniverseEdge(val a: String, val b: String)

/** One note as the anatomy reads it: its body, tags, source, hand links and written links. */
data class AnatomyNote(
    val id: String, val title: String, val body: String, val isPage: Boolean, val folder: String?,
    val tags: List<String>, val source: String?, val hand: List<String>, val written: List<String>,
    val created: Double = 0.0,
)

data class AnatomyInput(val notes: List<AnatomyNote>, val folders: List<UniverseFolder>)

data class UniverseInput(
    val notes: List<UniverseNote>,
    val folders: List<UniverseFolder>,
    val edges: List<UniverseEdge>,
    val seedByName: Boolean = false,
    val linkScale: Double = 1.0,
    val anatomy: AnatomyInput? = null,
    /** The folders opened on the map, outermost first. */
    val open: List<String> = emptyList(),
) {
    val spacing: Double get() = if (!linkScale.isFinite()) 1.0 else min(max(linkScale, 0.6), 1.8)
    val tightness: Double get() = min(spacing, 1.0)
    val stretch: Double get() = max(spacing, 1.0)
}

/** How a body moves relative to its parent (iOS GraphOrbit: the cases Neurons uses). */
sealed class GraphOrbit {
    data class Fixed(val p: Vec3) : GraphOrbit()
    data class Drift(val base: Vec3, val amp: Float, val phase: Float, val rate: Float) : GraphOrbit()
}

object GraphUniverse {
    const val HOME_ID = "00000000-0000-0000-0000-00000000A11C"
    const val GOLDEN = 2.3999632297
    val levels = intArrayOf(15, 30, 60, 120, 250, 500, 1000, 2000)

    /** Runs of non-whitespace bytes in the UTF-8 (space, tab, LF, CR). */
    fun wordCount(text: String): Int {
        var count = 0
        var inWord = false
        for (b in text.toByteArray(Charsets.UTF_8)) {
            val blank = b.toInt() == 32 || b.toInt() == 9 || b.toInt() == 10 || b.toInt() == 13
            if (blank) inWord = false else if (!inWord) { inWord = true; count += 1 }
        }
        return count
    }

    fun level(words: Int): Int = levels.count { words >= it }

    fun offset(orbit: GraphOrbit, time: Double): Vec3 = when (orbit) {
        is GraphOrbit.Fixed -> orbit.p
        is GraphOrbit.Drift -> orbit.base + wobble(orbit.amp, orbit.phase, orbit.rate, time)
    }

    /** Three slow sines at unrelated rates, each argument wrapped first. */
    fun wobble(amp: Float, phase: Float, rate: Float, time: Double): Vec3 {
        if (!(amp > 0f)) return Vec3.ZERO
        val full = 2 * PI
        val t = rate.toDouble() * time
        val a = phase + (t % full).toFloat()
        val b = phase * 1.7f + ((t * 1.31) % full).toFloat()
        val c = phase * 0.6f + ((t * 0.77) % full).toFloat()
        return Vec3(sin(a) * amp, sin(b + 1.1f) * amp * 0.8f, cos(c) * amp * 0.9f)
    }

    fun dot(a: D3, b: D3): Double = a.x * b.x + a.y * b.y + a.z * b.z
    fun cross(a: D3, b: D3) = D3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    fun length(a: D3): Double = sqrt(dot(a, a))
    fun normalize(a: D3): D3 { val n = length(a); return if (n > 1e-12) a / n else D3.ZERO }

    /** `v` turned by `angle` about `axis` (Rodrigues). */
    fun rotate(v: D3, axis: D3, angle: Double): D3 {
        val k = normalize(axis)
        val c = cos(angle)
        val s = sin(angle)
        val along = k * (dot(k, v) * (1 - c))
        val turned = cross(k, v) * s
        return v * c + turned + along
    }

    /** FNV-1a 64 over UTF-8. */
    fun fnv(text: String): ULong {
        var h = 0xcbf29ce484222325uL
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toUByte().toULong())
            h *= 0x00000100000001b3uL
        }
        return h
    }

    fun clamp(x: Double, low: Double, high: Double): Double = max(low, min(high, x))

    fun float3(v: D3) = Vec3(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())

    /** Swift's `.rounded()`: halves away from zero. */
    fun roundHalfAway(x: Double): Double = if (x < 0) -kotlin.math.floor(-x + 0.5) else kotlin.math.floor(x + 0.5)
}

/** A turn, as its three columns. */
data class UniverseFrame(val x: D3, val y: D3, val z: D3) {
    fun apply(v: D3): D3 = x * v.x + y * v.y + z * v.z
}

/** SplitMix64; unit() in [0, 1). */
class UniverseRandom(seed: ULong) {
    private var state: ULong = seed

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    fun unit(): Double = (next() shr 11).toDouble() / 9007199254740992.0
    fun signed(): Double = unit() * 2 - 1
}

/** Lexicographic (String, String) order, as Swift's tuple comparison. */
internal fun lessPair(a: Pair<String, String>, b: Pair<String, String>): Boolean {
    val f = a.first.compareTo(b.first)
    return if (f != 0) f < 0 else a.second < b.second
}

/** The Universe planner's folder tree and links (iOS UniversePlanner: init, buildTree, buildLinks). */
class UniverseTree(val input: UniverseInput) {
    val noteList: List<UniverseNote>
    val folderList: List<UniverseFolder>
    val hasFolders: Boolean
    val homeC: Int
    var cCount = 0
    var parent = IntArray(0)
    var depth = IntArray(0)
    var top = IntArray(0)
    var kids: List<List<Int>> = emptyList()
    var count = IntArray(0)
    var noteHome = IntArray(0)
    var nbr: List<List<Int>> = emptyList()
    var edgePairs: List<Pair<Int, Int>> = emptyList()

    init {
        val seenNotes = HashSet<String>()
        noteList = input.notes.filter { seenNotes.add(it.id) }.sortedBy { it.id }
        val seenFolders = HashSet<String>()
        folderList = input.folders.filter { seenFolders.add(it.id) }.sortedBy { it.id }
        hasFolders = folderList.isNotEmpty()
        homeC = if (folderList.isEmpty()) folderList.size else -1
    }

    fun cName(c: Int): String = if (c < folderList.size) folderList[c].name else "Ideas"
    fun cID(c: Int): String = if (c < folderList.size) folderList[c].id else GraphUniverse.HOME_ID
    fun liftKey(c: Int): String = if (input.seedByName) cName(c) else cID(c)
    fun cSeed(c: Int): ULong = GraphUniverse.fnv(liftKey(c))
    fun nSeed(n: Int): ULong = GraphUniverse.fnv(if (input.seedByName) noteList[n].title else noteList[n].id)
    fun nKey(n: Int) = Pair(noteList[n].title.lowercase(), noteList[n].id)
    fun cKey(c: Int) = Pair(cName(c).lowercase(), cID(c))
    fun lessC(a: Int, b: Int) = lessPair(cKey(a), cKey(b))
    fun lessN(a: Int, b: Int) = lessPair(nKey(a), nKey(b))

    fun buildTree() {
        val f = folderList.size
        cCount = if (hasFolders) f else 1
        val index = HashMap<String, Int>()
        folderList.forEachIndexed { i, folder -> index[folder.id] = i }
        parent = IntArray(cCount) { -1 }
        folderList.forEachIndexed { i, folder ->
            val at = folder.parent?.let { index[it] }
            if (at != null && at != i) parent[i] = at
        }
        // a cycle is cut at its member with the lowest index
        for (start in 0 until f) {
            val seen = ArrayList<Int>()
            val onWalk = HashSet<Int>()
            var cur = start
            while (cur >= 0 && !onWalk.contains(cur)) {
                seen.add(cur); onWalk.add(cur); cur = parent[cur]
            }
            if (cur < 0) continue
            val from = seen.indexOf(cur)
            if (from < 0) continue
            val cut = seen.subList(from, seen.size).minOrNull() ?: cur
            parent[cut] = -1
        }
        depth = IntArray(cCount)
        top = IntArray(cCount) { it }
        for (c in 0 until cCount) {
            var d = 0
            var cur = c
            while (parent[cur] >= 0) { cur = parent[cur]; d += 1 }
            depth[c] = d
            top[c] = cur
        }
        val children = List(cCount) { ArrayList<Int>() }
        for (c in 0 until cCount) if (parent[c] >= 0) children[parent[c]].add(c)
        kids = children.map { list -> list.sortedWith { a, b -> if (lessC(a, b)) -1 else if (lessC(b, a)) 1 else 0 } }
        noteHome = IntArray(noteList.size) { i ->
            val note = noteList[i]
            if (!hasFolders) homeC else note.folder?.let { index[it] } ?: -1
        }
        count = IntArray(cCount)
        for (h in noteHome) {
            if (h < 0) continue
            var cur = h
            while (cur >= 0) { count[cur] += 1; cur = parent[cur] }
        }
    }

    fun buildLinks() {
        val index = HashMap<String, Int>()
        noteList.forEachIndexed { i, note -> index[note.id] = i }
        val sets = List(noteList.size) { HashSet<Int>() }
        val pairs = HashSet<Long>()
        val n = noteList.size.toLong()
        val list = ArrayList<Pair<Int, Int>>()
        for (edge in input.edges) {
            val i = index[edge.a] ?: continue
            val j = index[edge.b] ?: continue
            if (i == j) continue
            val low = min(i, j)
            val high = max(i, j)
            if (!pairs.add(low * n + high)) continue
            sets[i].add(j); sets[j].add(i)
            list.add(Pair(low, high))
        }
        edgePairs = list.sortedWith(compareBy({ it.first }, { it.second }))
        nbr = sets.map { it.sorted() }
    }

    companion object {
        fun folderLabel(name: String, count: Int): String {
            val shown = if (name.length > 20) name.take(20) + "…" else name
            if (count == 0) return "$shown · empty"
            return "$shown · $count " + if (count == 1) "note" else "notes"
        }

        fun noteLabel(title: String): String = title.ifEmpty { "Untitled" }
    }
}

enum class ThemeBodyKind { NOTE, FOLDER, HOME }

data class ThemeBody(
    val id: String, val kind: ThemeBodyKind, val role: Int, val parent: Int, val sphere: Float,
    val depth: Int, val region: Int, val count: Int, val links: Int, val words: Int, val rank: Int,
    val seed: ULong, val orbit: GraphOrbit, val home: Vec3, val axis: Vec3, val title: String,
    val label: String,
)

/** A link between two bodies; for Neurons kind 5 inside a cell, 6 between, tag the FiberKind. */
data class ThemeLink(val a: Int, val b: Int, val kind: Int, val centre: Int, val tag: Int = 0, val width: Float = 1f)

data class ThemePlan(
    val bodies: List<ThemeBody>,
    val links: List<ThemeLink>,
    val envelope: List<Vec3>,
    val systems: List<List<Vec3>>,
    val regions: List<Int>,
    val summary: String,
) {
    /** Where body `i` is at `time`. */
    fun position(i: Int, time: Double): Vec3 {
        var p = Vec3.ZERO
        var at = i
        var steps = 0
        while (at >= 0 && at < bodies.size && steps <= bodies.size) {
            p += GraphUniverse.offset(bodies[at].orbit, time)
            at = bodies[at].parent
            steps += 1
        }
        return p
    }

    companion object { val EMPTY = ThemePlan(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "") }
}

data class ThemeBall(val c: D3, val r: Double)

/** Deterministic placing without overlaps (iOS ThemeLayout). */
object ThemeLayout {
    fun fibonacci(k: Int, n: Int): D3 {
        val count = max(n, 1).toDouble()
        val y = 1 - (k + 0.5) * 2 / count
        val ring = sqrt(max(1 - y * y, 0.0))
        val angle = k * GraphUniverse.GOLDEN
        return D3(cos(angle) * ring, y, sin(angle) * ring)
    }

    fun clear(ball: ThemeBall, placed: List<ThemeBall>, gap: Double): Boolean {
        for (other in placed) {
            val reach = ball.r + other.r + gap
            val d = ball.c - other.c
            if (GraphUniverse.dot(d, d) < reach * reach) return false
        }
        return true
    }

    fun enclosing(balls: List<ThemeBall>): ThemeBall {
        if (balls.isEmpty()) return ThemeBall(D3.ZERO, 0.0)
        var sum = D3.ZERO
        for (b in balls) sum += b.c
        val middle = sum / balls.size.toDouble()
        var reach = 0.0
        for (b in balls) reach = max(reach, GraphUniverse.length(b.c - middle) + b.r)
        return ThemeBall(middle, reach)
    }

    fun slide(group: List<ThemeBall>, dir: D3, start: Double, step: Double, placed: List<ThemeBall>, gap: Double,
              limit: Int = 4000): Double {
        val whole = enclosing(group)
        var s = start
        var tries = 0
        while (tries < limit) {
            if (fits(group, whole, dir * s, placed, gap)) return s
            s += step
            tries += 1
        }
        return s
    }

    fun fits(group: List<ThemeBall>, whole: ThemeBall, shift: D3, placed: List<ThemeBall>, gap: Double): Boolean {
        val moved = ThemeBall(whole.c + shift, whole.r)
        for (other in placed) {
            val reach = moved.r + other.r + gap
            val d = moved.c - other.c
            if (GraphUniverse.dot(d, d) >= reach * reach) continue
            for (b in group) if (!clear(ThemeBall(b.c + shift, b.r), listOf(other), gap)) return false
        }
        return true
    }

    /** Six points round each ball (along the axes), for framing. */
    fun points(balls: List<ThemeBall>, origin: D3): List<Vec3> {
        val axes = listOf(D3(1.0, 0.0, 0.0), D3(-1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), D3(0.0, -1.0, 0.0),
            D3(0.0, 0.0, 1.0), D3(0.0, 0.0, -1.0))
        val out = ArrayList<Vec3>(balls.size * 6)
        for (b in balls) for (a in axes) out.add(GraphUniverse.float3(b.c + a * b.r - origin))
        return out
    }
}

data class GraphWindow(val aspect: Float, val share: Float, val across: Float, val up: Float)

data class GraphTiltFit(val distance: Float, val shiftX: Float, val shiftY: Float, val focus: Float,
                        val deepest: Float, val reach: Float)

/** The tilted default view's fit and the real-depth lift (iOS GraphTilt, the Neurons parts). */
object GraphTilt {
    const val FIELD_OF_VIEW = 55f
    const val NEURONS_DEGREES = 35f
    const val REACH_LOW = 0.15f
    const val REACH_HIGH = 1.0f
    const val CRISP_SHARE = 0.20f
    const val CANONICAL_FILL = 0.88f
    const val MOST_COVERED = 0.15f
    val amplitudes = floatArrayOf(0.35f, 0.25f, 0.15f)
    private const val FPI = PI.toFloat()

    fun radians(degrees: Float) = degrees * FPI / 180f
    val neuronsAngle: Float get() = radians(NEURONS_DEGREES)

    fun rotX(angle: Float, v: Vec3): Vec3 {
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }

    fun offset(fit: GraphTiltFit, tilt: Float): Vec3 = rotX(tilt, Vec3(fit.shiftX, fit.shiftY, fit.distance))

    val tanHalf: Float get() = tan(FIELD_OF_VIEW * FPI / 360f)

    fun window(width: Float, height: Float, top: Float = 0f, left: Float = 0f, bottom: Float = 0f,
               right: Float = 0f): GraphWindow {
        val wide = max(width, 1f)
        val tall = max(height, 1f)
        val seenWidth = max(wide - (left + right), wide / 3)
        val seenHeight = max(tall - (top + bottom), tall / 3)
        return GraphWindow(seenWidth / seenHeight, seenHeight / tall, (left - right) / 2 / tall, (bottom - top) / 2 / tall)
    }

    val canonical = listOf(window(393f, 852f, top = 118f, bottom = 158f), window(1194f, 834f, top = 86f, bottom = 112f))

    fun fit(points: List<Vec3>, pads: List<Float>, window: GraphWindow, fill: Float, tilt: Float): GraphTiltFit {
        val seen = points.map { rotX(-tilt, it) }
        val pad = points.indices.map { if (it < pads.size) max(pads[it], 0f) else 0f }
        if (seen.isEmpty()) return GraphTiltFit(1f, 0f, 0f, 1f, 1f, REACH_LOW)
        var low = 0f
        var high = 1f
        for (i in seen.indices) high = max(high, seen[i].z + pad[i] * 2 + 1)
        var tries = 0
        while (shift(seen, pad, window, fill, high) == null && tries < 80) { low = high; high *= 2; tries += 1 }
        for (k in 0 until 64) {
            val mid = (low + high) * 0.5f
            if (!(mid > low && mid < high)) break
            if (shift(seen, pad, window, fill, mid) != null) high = mid else low = mid
        }
        val d = high
        val s = shift(seen, pad, window, fill, d) ?: floatArrayOf(0f, 0f)
        var deepest = d
        for (p in seen) deepest = max(deepest, d - p.z)
        val reach = min(max(deepest / d - 1, REACH_LOW), REACH_HIGH)
        return GraphTiltFit(d, s[0], s[1], d, deepest, reach)
    }

    private fun shift(seen: List<Vec3>, pads: List<Float>, window: GraphWindow, fill: Float, distance: Float): FloatArray? {
        val tanUp = tanHalf * fill * window.share
        val tanSide = tanUp * max(window.aspect, 0.1f)
        val leanX = window.across * 2 * tanHalf
        val leanY = window.up * 2 * tanHalf
        var xLow = Float.NEGATIVE_INFINITY; var xHigh = Float.POSITIVE_INFINITY
        var yLow = Float.NEGATIVE_INFINITY; var yHigh = Float.POSITIVE_INFINITY
        for (i in seen.indices) {
            val p = seen[i]; val r = pads[i]
            val depth = distance - p.z
            val roomX = (depth - r) * tanSide - r
            val roomY = (depth - r) * tanUp - r
            if (!(roomX >= 0 && roomY >= 0)) return null
            val mx = p.x - depth * leanX
            val my = p.y - depth * leanY
            xLow = max(xLow, mx - roomX); xHigh = min(xHigh, mx + roomX)
            yLow = max(yLow, my - roomY); yHigh = min(yHigh, my + roomY)
        }
        if (!(xLow <= xHigh && yLow <= yHigh)) return null
        return floatArrayOf((xLow + xHigh) * 0.5f, (yLow + yHigh) * 0.5f)
    }

    private fun project(p: Vec3, fit: GraphTiltFit, tilt: Float): FloatArray {
        val q = rotX(-tilt, p)
        val depth = fit.distance - q.z
        val safe = max(depth, 0.000001f)
        return floatArrayOf((q.x - fit.shiftX) / safe, (q.y - fit.shiftY) / safe, depth)
    }

    /** A stable number in [-1, 1) from a cluster's id: FNV-1a 64 of its UTF-8. */
    fun u(id: String): Float {
        val unit = (GraphUniverse.fnv(id) shr 11).toDouble() / 9007199254740992.0
        return (unit * 2 - 1).toFloat()
    }

    data class Cluster(val id: String, val cx: Float, val cy: Float, val radius: Float)

    fun half(clusters: List<Cluster>): Float {
        if (clusters.isEmpty()) return 0f
        val f = clusters[0]
        var lx = f.cx - f.radius; var ly = f.cy - f.radius
        var hx = f.cx + f.radius; var hy = f.cy + f.radius
        for (c in clusters) {
            lx = min(lx, c.cx - c.radius); ly = min(ly, c.cy - c.radius)
            hx = max(hx, c.cx + c.radius); hy = max(hy, c.cy + c.radius)
        }
        return max(hx - lx, hy - ly) * 0.5f
    }

    /** The amplitude for the Neurons theme's top clusters. */
    fun amplitude(clusters: List<Cluster>): Float {
        val tilt = neuronsAngle
        if (clusters.isEmpty()) return 0f
        val h = half(clusters)
        for (share in amplitudes) if (legible(clusters, tilt, share * h)) return share * h
        return 0f
    }

    fun z(id: String, amplitude: Float): Float = amplitude * u(id)

    fun legible(clusters: List<Cluster>, tilt: Float, amplitude: Float): Boolean {
        for (window in canonical) {
            val wide = window.aspect > 1
            val points = clusters.map { c ->
                if (wide) Vec3(c.cy, -c.cx, z(c.id, amplitude)) else Vec3(c.cx, c.cy, z(c.id, amplitude))
            }
            if (!legible(points, clusters.map { it.radius }, window, tilt)) return false
        }
        return true
    }

    fun legible(points: List<Vec3>, radii: List<Float>, window: GraphWindow, tilt: Float): Boolean {
        if (points.size <= 1) return true
        val middle = box(points, radii)
        val around = points.map { it - middle }
        val whole = fit(around, radii, window, CANONICAL_FILL, tilt)
        val crispTo = whole.focus * (1 + CRISP_SHARE * whole.reach)
        val discs = around.indices.map { i ->
            val s = project(around[i], whole, tilt)
            floatArrayOf(s[0], s[1], radii[i] / max(s[2], 0.000001f), if (s[2] <= crispTo) 1f else 0f)
        }
        for (i in discs.indices) {
            if (discs[i][3] == 0f) continue
            for (j in i + 1 until discs.size) {
                if (discs[j][3] == 0f) continue
                val d = hypot(discs[i][0] - discs[j][0], discs[i][1] - discs[j][1])
                val shared = overlap(discs[i][2], discs[j][2], d)
                val m = min(discs[i][2], discs[j][2])
                if (shared > MOST_COVERED * (FPI * (m * m))) return false
            }
        }
        for (i in points.indices) {
            val flown = fit(listOf(Vec3.ZERO), listOf(radii[i]), window, CANONICAL_FILL, tilt)
            val eye = offset(flown, tilt)
            val forward = rotX(tilt, Vec3(0f, 0f, -1f))
            val toMe = Vec3.ZERO - eye
            val meDistance = toMe.length()
            val mine = asin(min(radii[i] / meDistance, 1f))
            val myDepth = toMe.dot(forward)
            var covered = 0f
            for (j in points.indices) {
                if (j == i) continue
                val v = points[j] - points[i] - eye
                val distance = v.length()
                if (distance <= radii[j]) return false
                val along = v.dot(forward)
                if (!(along < myDepth && along > -radii[j])) continue
                val apart = acos(min(max(v.dot(toMe) / (distance * meDistance), -1f), 1f))
                covered += overlap(mine, asin(min(radii[j] / distance, 1f)), apart)
            }
            if (covered > MOST_COVERED * FPI * mine * mine) return false
        }
        return true
    }

    private fun box(points: List<Vec3>, radii: List<Float>): Vec3 {
        if (points.isEmpty()) return Vec3.ZERO
        var lx = points[0].x; var ly = points[0].y; var lz = points[0].z
        var hx = lx; var hy = ly; var hz = lz
        for (i in points.indices) {
            val p = points[i]; val r = radii[i]
            lx = min(lx, p.x - r); ly = min(ly, p.y - r); lz = min(lz, p.z - r)
            hx = max(hx, p.x + r); hy = max(hy, p.y + r); hz = max(hz, p.z + r)
        }
        return Vec3((lx + hx) * 0.5f, (ly + hy) * 0.5f, (lz + hz) * 0.5f)
    }

    fun overlap(a: Float, b: Float, d: Float): Float {
        val r1 = max(a, 0f)
        val r2 = max(b, 0f)
        if (!(d < r1 + r2)) return 0f
        if (d <= abs(r1 - r2)) { val m = min(r1, r2); return FPI * (m * m) }
        val one = r1 * r1 * acos(min(max((d * d + r1 * r1 - r2 * r2) / (2 * d * r1), -1f), 1f))
        val two = r2 * r2 * acos(min(max((d * d + r2 * r2 - r1 * r1) / (2 * d * r2), -1f), 1f))
        val k = (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)
        return one + two - 0.5f * sqrt(max(k, 0f))
    }
}

enum class FiberKind(val code: Int) { EXCITATORY(0), INHIBITORY(1), MODULATORY(2) }

data class AnatomyLink(val from: String, val to: String, val strength: Int, val kind: FiberKind, val twoWay: Boolean,
                       val hand: Boolean, val written: Boolean)

/** The anatomy's link reading (iOS GraphAnatomy.link and its helpers). */
object GraphAnatomy {
    val contrastCues = listOf(" not ", "n't ", " vs", "versus", "contraindicat", "unlike", "rather than",
        "instead of", "exclude", "rules out", "rule out", " never ", " no ", "differs from", "distinguish")

    fun tagKey(tag: String): String = tag.lowercase().filter {
        it.isLetter() || it.isDigit() || Character.getType(it) == Character.LETTER_NUMBER.toInt() ||
            Character.getType(it) == Character.OTHER_NUMBER.toInt() || it == '?'
    }

    fun mention(title: String, body: String): String? {
        val key = "[[" + title.lowercase()
        for (raw in body.split("\n")) {
            if (raw.isEmpty()) continue
            val line = raw.lowercase()
            val at = line.indexOf(key)
            if (at >= 0) {
                val tail = line.substring(at + key.length)
                if (tail.startsWith("]]") || tail.startsWith("|")) return raw
            }
        }
        return null
    }

    fun isContrast(line: String): Boolean {
        val low = " " + line.lowercase() + " "
        return contrastCues.any { low.contains(it) }
    }

    fun link(a: AnatomyNote, b: AnatomyNote): AnatomyLink? {
        val hand = a.hand.contains(b.id)
        val written = a.written.contains(b.id)
        if (!(hand || written) || a.id == b.id) return null
        val back = b.hand.contains(a.id) || b.written.contains(a.id)
        var strength = 1 + (if (hand) 3 else 0) + (if (written) 3 else 0) + (if (back) 2 else 0)
        if (a.source != null && a.source == b.source) strength += 1
        if (a.tags.isNotEmpty() && b.tags.isNotEmpty()) {
            val tagsA = a.tags.map(::tagKey).toSet()
            if (b.tags.any { tagsA.contains(tagKey(it)) }) strength += 1
        }
        val line = if (written) mention(b.title, a.body) else null
        var kind = FiberKind.EXCITATORY
        if (line != null && isContrast(line)) kind = FiberKind.INHIBITORY else if (hand && !written) kind = FiberKind.MODULATORY
        return AnatomyLink(a.id, b.id, min(max(strength, 1), 10), kind, back, hand, written)
    }
}
