package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Universe look (S05h), ported from iOS's planner (GraphUniverse.swift:122-1580): the ideas' own
// folders drawn as a universe, the biggest bodies for the biggest containers. A top-level folder is a
// black hole at the heart of a galaxy, a folder inside one a star beside its parent, a page a gas
// giant, an idea a rocky planet, a short idea linked only to the note it circles that note's moon, an
// idea linking two other folders its galaxy's pulsar, a note in no folder a comet; with no folders,
// one home star holds every note. Plain Kotlin, in iOS's units, so every number carries over: the
// parity dump (tools/universe_dump.swift) checks it body by body (UniversePlanTest).

enum class UniverseRole(val raw: String) {
    GALAXY("galaxy"), STAR("star"), HOME("home"), GAS_GIANT("gasGiant"), ROCKY("rocky"), MOON("moon"), PULSAR("pulsar"),
    COMET("comet"), OORT("oort");

    /** A folder body (a black hole, a star or the home): its card offers Fly in. */
    val isFolder: Boolean get() = this == GALAXY || this == STAR || this == HOME
}

data class UniverseBody(
    /** The note's id, the folder's id, or GraphUniverse.HOME_ID. */
    val id: String,
    val role: UniverseRole,
    /** The body it moves with, or −1. */
    val parent: Int,
    /** Who lights it: a body index, −1 the key light, −2 the nearest light. */
    val light: Int,
    val sphere: Float,
    /** Star colour: 1 yellow-white, 2 orange, 3 red; 0 for anything else. */
    val tier: Int,
    /** A folder with no notes anywhere inside it. */
    val empty: Boolean,
    /** A gas giant of 250 words or more. */
    val ringed: Boolean,
    val palette: Int,
    /** Its galaxy core's index, or −1. */
    val galaxy: Int,
    val count: Int,
    val links: Int,
    val shell: Int,
    val orbit: GraphOrbit,
    val home: Vec3,
    val title: String,
    val label: String,
)

data class UniverseShell(val owner: Int, val radius: Float, val u: Vec3, val v: Vec3)

/** kind: 0 same folder, 1 same galaxy, 2 across galaxies or to a loose note, 3 hidden (a moon and its own planet); centre the core a kind-1 link bows from, −1 the origin. */
data class UniverseLink(val a: Int, val b: Int, val kind: Int, val centre: Int)

class UniversePlan(
    /** Every body, each after its parent. */
    val bodies: List<UniverseBody>,
    val shells: List<UniverseShell>,
    val links: List<UniverseLink>,
    /** Points the whole-map framing must hold, at any time. */
    val envelope: List<Vec3>,
    /** For each folder body, the points its fly-in framing must hold, relative to it; empty for notes. */
    val systems: List<List<Vec3>>,
    /** The bodies that shine (non-empty folders and the home star). */
    val lights: List<Int>,
    /** "2 galaxies, 3 stars, 21 planets, 6 moons, 1 pulsar, 2 comets". */
    val summary: String,
) {
    /** Where body [i] is at [time], in the space's coordinates (GraphUniverse.swift:313-324). */
    fun position(i: Int, time: Double): Vec3 {
        var x = 0f; var y = 0f; var z = 0f
        var at = i
        var steps = 0
        while (at >= 0 && at < bodies.size && steps <= bodies.size) {
            val o = GraphUniverse.offset(bodies[at].orbit, time)
            x += o.x; y += o.y; z += o.z
            at = bodies[at].parent
            steps += 1
        }
        return Vec3(x, y, z)
    }

    companion object {
        val EMPTY = UniversePlan(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "")
        /** Loose notes past this many (the oldest) rest in the Oort cloud. */
        const val ACTIVE_COMETS = 12
        const val COMET_E = 0.45

        /** The plan for [input] (GraphUniverse.plan, :328-339). */
        fun of(input: UniverseInput): UniversePlan {
            if (input.notes.isEmpty() && input.folders.isEmpty()) return EMPTY
            val planner = UniversePlanner(input)
            if (input.tightness < 1) {
                // shorter links: every system and galaxy keeps the side it stands on at the standard length
                val standard = UniversePlanner(input.copy(linkScale = 1.0))
                standard.run()
                planner.forcedTurns = standard.chosenTurns
                planner.forcedGalaxyTurns = standard.chosenGalaxyTurns
            }
            return planner.run()
        }

        /** "Name · 3 ideas" (GraphUniverse.swift:1274-1279). */
        fun folderLabel(name: String, count: Int): String = UniverseTree.folderLabel(name, count)

        fun summary(bodies: List<UniverseBody>): String {
            var galaxies = 0; var stars = 0; var planets = 0; var moons = 0; var pulsars = 0; var comets = 0
            for (body in bodies) when (body.role) {
                UniverseRole.GALAXY -> galaxies++
                UniverseRole.STAR, UniverseRole.HOME -> stars++
                UniverseRole.GAS_GIANT, UniverseRole.ROCKY -> planets++
                UniverseRole.MOON -> moons++
                UniverseRole.PULSAR -> pulsars++
                UniverseRole.COMET, UniverseRole.OORT -> comets++
            }
            val parts = listOf(Triple(galaxies, "galaxy", "galaxies"), Triple(stars, "star", "stars"), Triple(planets, "planet", "planets"),
                Triple(moons, "moon", "moons"), Triple(pulsars, "pulsar", "pulsars"), Triple(comets, "comet", "comets"))
            return parts.filter { it.first > 0 }.joinToString(", ") { "${it.first} " + if (it.first == 1) it.second else it.third }
        }

        /** Points round a comet's ellipse (16) or the Oort ring (24) (GraphUniverse.swift:1531-1551). */
        fun trace(orbit: GraphOrbit): List<Vec3> = when (orbit) {
            is GraphOrbit.Kepler -> {
                val b = orbit.a * sqrt(1 - orbit.e * orbit.e)
                (0 until 16).map { k ->
                    val big = k * PI.toFloat() / 8
                    orbit.u * (orbit.a * (cos(big) - orbit.e)) + orbit.v * (b * sin(big))
                }
            }
            is GraphOrbit.Ring -> (0 until 24).map { k ->
                val angle = k * PI.toFloat() / 12
                Vec3(orbit.x * cos(angle), orbit.y * sin(angle), orbit.z)
            }
            else -> emptyList()
        }
    }
}

/** A Double 2-vector (iOS SIMD2<Double>). */
internal data class D2(val x: Double, val y: Double) {
    operator fun plus(o: D2) = D2(x + o.x, y + o.y)
    operator fun minus(o: D2) = D2(x - o.x, y - o.y)
    operator fun times(k: Double) = D2(x * k, y * k)
    operator fun div(k: Double) = D2(x / k, y / k)
    fun length() = sqrt(x * x + y * y)
    fun min(o: D2) = D2(min(x, o.x), min(y, o.y))
    fun max(o: D2) = D2(max(x, o.x), max(y, o.y))
    companion object { val ZERO = D2(0.0, 0.0) }
}

internal data class UCircle(val q: D2, val r: Double)
internal class UOrbitPlan(val radius: Double, val members: List<Int>, val foot: Double)
internal class UPlace(val child: Int, val offset: D2, val turn: Double)

/** (rounded size, rounded angle, direction), compared as Swift compares the tuple. */
private fun lessKey(a: Triple<Double, Double, Int>, b: Triple<Double, Double, Int>): Boolean =
    if (a.first != b.first) a.first < b.first else if (a.second != b.second) a.second < b.second else a.third < b.third

private fun rounded(x: Double, scale: Double): Double = GraphUniverse.roundHalfAway(x * scale) / scale

private fun rotX(angle: Double): UniverseFrame {
    val c = cos(angle); val s = sin(angle)
    return UniverseFrame(D3(1.0, 0.0, 0.0), D3(0.0, c, s), D3(0.0, -s, c))
}

private fun rotZ(angle: Double): UniverseFrame {
    val c = cos(angle); val s = sin(angle)
    return UniverseFrame(D3(c, s, 0.0), D3(-s, c, 0.0), D3(0.0, 0.0, 1.0))
}

private val IDENTITY = UniverseFrame(D3(1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), D3(0.0, 0.0, 1.0))

/** This turn after [inner] (the matrix product this × inner). */
private fun UniverseFrame.times(inner: UniverseFrame) = UniverseFrame(apply(inner.x), apply(inner.y), apply(inner.z))

/** A turn of [angle] about [axis]. */
private fun about(axis: D3, angle: Double) = UniverseFrame(GraphUniverse.rotate(D3(1.0, 0.0, 0.0), axis, angle),
    GraphUniverse.rotate(D3(0.0, 1.0, 0.0), axis, angle), GraphUniverse.rotate(D3(0.0, 0.0, 1.0), axis, angle))

/** The planner's working state (iOS UniversePlanner): notes 0 until n, containers 0 until c. */
internal class UniversePlanner(val input: UniverseInput) {
    private val tree = UniverseTree(input)
    private val noteList = tree.noteList
    private val homeC = tree.homeC
    private val tight = input.tightness
    private val stretch = input.stretch
    private var cCount = 0
    private val parent get() = tree.parent
    private val depth get() = tree.depth
    private val top get() = tree.top
    private val kids get() = tree.kids
    private val count get() = tree.count
    private val noteHome get() = tree.noteHome
    private val nbr get() = tree.nbr

    /** 0 gas, 1 rocky, 2 moon, 3 pulsar, 4 comet, 5 oort. */
    private var role = IntArray(0)
    private var moonsOf: List<MutableList<Int>> = emptyList()
    private var moonPlanet = IntArray(0)
    private var sphere = DoubleArray(0)
    private var ringed = BooleanArray(0)
    private var cSphere = DoubleArray(0)
    private var orbits: List<List<UOrbitPlan>> = emptyList()
    private var core = DoubleArray(0)
    private var comp: Array<List<UCircle>> = emptyArray()
    private var places: Array<List<UPlace>> = emptyArray()
    private var loose: List<Int> = emptyList()
    var chosenTurns = HashMap<Int, Int>()
    var chosenGalaxyTurns = HashMap<Int, Int>()
    var forcedTurns: Map<Int, Int> = emptyMap()
    var forcedGalaxyTurns: Map<Int, Int> = emptyMap()

    private val bodies = ArrayList<UniverseBody>()
    private val shells = ArrayList<UniverseShell>()
    private var noteBody = IntArray(0)
    private var cBody = IntArray(0)
    private var cFrame: Array<UniverseFrame> = emptyArray()
    private var cHome: Array<D3> = emptyArray()

    private fun lessC(a: Int, b: Int) = tree.lessC(a, b)
    private fun lessN(a: Int, b: Int) = tree.lessN(a, b)
    private fun isGalaxy(c: Int) = c != homeC && depth[c] == 0
    private fun cmpN(a: Int, b: Int) = if (lessN(a, b)) -1 else if (lessN(b, a)) 1 else 0
    private fun cmpC(a: Int, b: Int) = if (lessC(a, b)) -1 else if (lessC(b, a)) 1 else 0

    fun run(): UniversePlan {
        tree.buildTree()
        cCount = tree.cCount
        tree.buildLinks()
        assignRoles()
        sizeBodies()
        buildOrbits()
        val tops = (0 until cCount).filter { parent[it] < 0 }
        for (t in tops) compose(t)
        val placed = placeGalaxies(tops)
        chosenGalaxyTurns = HashMap(placed.turns)
        emit(placed)
        emitComets(placed.half * stretch)
        return finish(placed)
    }

    // ---- roles (:734-799) ----

    private fun assignRoles() {
        val n = noteList.size
        role = IntArray(n) { -1 }
        val best = HashMap<Int, Triple<Int, Int, Int>>()
        for (i in 0 until n) {
            val h = noteHome[i]
            if (noteList[i].isPage || h < 0 || h == homeC) continue
            val others = ArrayList<Int>()
            for (m in nbr[i]) { val o = noteHome[m]; if (o >= 0 && o != homeC && o != h) others.add(o) }
            val distinct = others.toSet().size
            if (!(distinct >= 2 && others.size >= 3)) continue
            val g = top[h]
            val held = best[g]
            if (held == null || pulsarBeats(i, distinct, others.size, held)) best[g] = Triple(i, distinct, others.size)
        }
        for ((_, held) in best) role[held.first] = 3

        moonsOf = List(n) { ArrayList<Int>() }
        moonPlanet = IntArray(n) { -1 }
        val byLength = (0 until n).sortedWith { a, b ->
            val wa = noteList[a].words; val wb = noteList[b].words
            if (wa != wb) wa.compareTo(wb) else cmpN(a, b)
        }
        for (i in byLength) {
            val h = noteHome[i]
            if (role[i] >= 0 || noteList[i].isPage || h < 0) continue
            if (nbr[i].size != 1 || noteList[i].words >= 60) continue
            val p = nbr[i][0]
            if (noteHome[p] != h || role[p] == 3) continue
            if (!noteList[p].isPage && nbr[p].size < 2) continue
            val cap = if (noteList[p].isPage) 4 else 2
            if (moonsOf[p].size >= cap) continue
            moonsOf[p].add(i)
            moonPlanet[i] = p
            role[i] = 2
        }

        val free = (0 until n).filter { noteHome[it] < 0 }.sortedWith { a, b ->
            val ca = noteList[a].created; val cb = noteList[b].created
            if (ca != cb) cb.compareTo(ca) else cmpN(a, b)
        }
        loose = free
        for ((k, i) in free.withIndex()) role[i] = if (k < UniversePlan.ACTIVE_COMETS) 4 else 5
        for (i in 0 until n) if (role[i] < 0) role[i] = if (noteList[i].isPage) 0 else 1
    }

    private fun pulsarBeats(i: Int, distinct: Int, total: Int, held: Triple<Int, Int, Int>): Boolean {
        if (distinct != held.second) return distinct > held.second
        if (total != held.third) return total > held.third
        return lessN(i, held.first)
    }

    // ---- sizes (:803-884) ----

    private fun sizeBodies() {
        val n = noteList.size
        sphere = DoubleArray(n)
        ringed = BooleanArray(n)
        for (i in 0 until n) {
            val lv = GraphUniverse.level(noteList[i].words)
            when (role[i]) {
                0 -> { sphere[i] = 0.19 + 0.01 * min(lv, 7); ringed[i] = noteList[i].words >= 250 }
                1 -> sphere[i] = 0.12 + 0.006 * min(lv, 6)
                2 -> sphere[i] = 0.075 + 0.003 * min(lv, 2)
                3 -> sphere[i] = 0.07
                4 -> sphere[i] = 0.09
                else -> sphere[i] = 0.06
            }
        }
        cSphere = DoubleArray(cCount)
        val order = (0 until cCount).sortedWith { a, b -> if (depth[a] != depth[b]) depth[a].compareTo(depth[b]) else a.compareTo(b) }
        for (c in order) cSphere[c] = containerSphere(c)
    }

    private fun containerSphere(c: Int): Double {
        val size = log2(1.0 + count[c])
        if (isGalaxy(c)) {
            if (count[c] == 0) return 0.40
            return GraphUniverse.clamp(0.40 + 0.015 * size, 0.40, 0.52)
        }
        if (count[c] == 0) return starFloor(depth[c])
        val v = GraphUniverse.clamp(0.30 + 0.01 * size, 0.30, 0.38)
        if (c == homeC || depth[c] < 2) return v
        val under = cSphere[parent[c]] * 0.9
        return max(min(v, under), starFloor(depth[c]))
    }

    private fun moonRing(p: Int): Double {
        val moons = moonsOf[p]
        if (moons.isEmpty()) return 0.0
        val biggest = moons.maxOf { sphere[it] }
        val k = if (ringed[p]) 2.5 else 1.8
        return k * sphere[p] + biggest + 0.03
    }

    private fun foot(i: Int): Double {
        if (role[i] == 3) return 0.22
        val moons = moonsOf[i]
        if (moons.isNotEmpty()) return moonRing(i) + moons.maxOf { sphere[it] } + 0.03
        val k = if (ringed[i]) 2.4 else 1.25
        return k * sphere[i] + 0.03
    }

    private fun clear(c: Int): Double {
        if (tight >= 1) return standardClear(c)
        val s = cSphere[c]
        if (isGalaxy(c)) {
            if (count[c] > 0) return 2.5 * s + 0.10 * tight
            return (1 + 0.6 * tight) * s
        }
        val full = count[c] > 0 || c == homeC
        val slack = if (full) 0.8 else 0.4
        return (1 + slack * tight) * s
    }

    private fun standardClear(c: Int): Double {
        val s = cSphere[c]
        if (isGalaxy(c)) return if (count[c] > 0) 2.5 * s + 0.10 else 1.6 * s
        return if (count[c] > 0 || c == homeC) 1.8 * s else 1.4 * s
    }

    private val orbitGap: Double get() = if (tight >= 1) 0.08 else 0.03 + 0.05 * tight

    // ---- orbits (:888-938) ----

    private fun buildOrbits() {
        val direct = List(cCount) { ArrayList<Int>() }
        for (i in noteList.indices) {
            val h = noteHome[i]
            if (h >= 0 && role[i] <= 1 || h >= 0 && role[i] == 3) direct[h].add(i)
        }
        val out = ArrayList<List<UOrbitPlan>>(cCount)
        core = DoubleArray(cCount)
        for (c in 0 until cCount) {
            val rock = direct[c].filter { role[it] == 1 }.sortedWith { a, b ->
                if (nbr[a].size != nbr[b].size) nbr[b].size.compareTo(nbr[a].size) else cmpN(a, b)
            }
            val gas = direct[c].filter { role[it] == 0 }.sortedWith { a, b ->
                if (sphere[a] != sphere[b]) sphere[b].compareTo(sphere[a]) else cmpN(a, b)
            }
            val pulsars = direct[c].filter { role[it] == 3 }.sortedWith(::cmpN)
            val groups = listOf(rock, gas + pulsars).filter { it.isNotEmpty() }
            val list = ArrayList<UOrbitPlan>()
            var lastR = -1.0; var lastRS = -1.0; var lastF = 0.0
            for (group in groups) {
                var rest = group
                while (rest.isNotEmpty()) {
                    val f = rest.maxOf { foot(it) }
                    val rs = if (lastRS < 0) standardClear(c) + f + 0.08 else lastRS + lastF + f + 0.08
                    val room = 2 * PI * rs / (2 * f + 0.10)
                    val cap = max(1, floor(room).toInt())
                    val take = rest.take(cap)
                    rest = rest.drop(cap)
                    val inner = if (lastR < 0) clear(c) + f + orbitGap else lastR + lastF + f + orbitGap
                    val side = take.size * (2 * f + 0.10) / (2 * PI)
                    val r = min(max(inner, side), rs)
                    list.add(UOrbitPlan(r, take, f))
                    lastR = r; lastRS = rs; lastF = f
                }
            }
            out.add(list)
            core[c] = if (lastR < 0) clear(c) else lastR + lastF
        }
        orbits = out
    }

    // ---- systems, packed bottom up (:942-1059) ----

    private fun extent(circles: List<UCircle>): Double { var most = 0.0; for (c in circles) most = max(most, c.q.length() + c.r); return most }

    private fun enclosing(circles: List<UCircle>): UCircle {
        val first = circles.firstOrNull() ?: return UCircle(D2.ZERO, 0.0)
        var lo = D2(first.q.x - first.r, first.q.y - first.r); var hi = D2(first.q.x + first.r, first.q.y + first.r)
        for (c in circles) { lo = lo.min(D2(c.q.x - c.r, c.q.y - c.r)); hi = hi.max(D2(c.q.x + c.r, c.q.y + c.r)) }
        val centre = (lo + hi) * 0.5
        var r = 0.0
        for (c in circles) r = max(r, (c.q - centre).length() + c.r)
        return UCircle(centre, r)
    }

    private fun slide(u: D2, moving: List<UCircle>, placed: List<UCircle>, gap: Double): Double {
        var best = 0.0
        for (m in moving) for (p in placed) {
            val w = m.q - p.q
            val b = u.x * w.x + u.y * w.y
            val reach = m.r + p.r + gap
            val cc = w.x * w.x + w.y * w.y - reach * reach
            val disc = b * b - cc
            if (!(disc > 0)) continue
            val d = sqrt(disc) - b
            if (d > best) best = d
        }
        return best
    }

    private fun turned(circles: List<UCircle>, a: Double): List<UCircle> {
        val c = cos(a); val s = sin(a)
        return circles.map { k -> UCircle(D2(c * k.q.x - s * k.q.y, s * k.q.x + c * k.q.y), k.r) }
    }

    private fun angleGap(a: Double, b: Double): Double {
        val full = 2 * PI
        var d = (a - b + PI) % full
        if (d < 0) d += full
        return abs(d - PI)
    }

    private fun compose(c: Int) {
        if (comp.isEmpty()) { comp = Array(cCount) { emptyList() }; places = Array(cCount) { emptyList() } }
        for (k in kids[c]) compose(k)
        val placed = ArrayList<UCircle>().apply { add(UCircle(D2.ZERO, core[c])) }
        var reach = core[c]
        val children = kids[c].sortedWith { a, b ->
            val ea = extent(comp[a]); val eb = extent(comp[b])
            if (ea != eb) eb.compareTo(ea) else cmpC(a, b)
        }
        val big = children.size > 12
        val random = UniverseRandom(tree.cSeed(c))
        val base = 2 * PI * random.unit()
        val out = ArrayList<UPlace>()
        for ((ki, k) in children.withIndex()) {
            val circles = if (big) listOf(enclosing(comp[k])) else comp[k]
            var centroid = D2.ZERO
            for (circle in circles) centroid += circle.q
            centroid /= circles.size.toDouble()
            val preferred = base + ki * GraphUniverse.GOLDEN
            var bestKey: Triple<Double, Double, Int>? = null
            var bestPlace: UPlace? = null
            var bestCircles: List<UCircle> = emptyList()
            var bestReach = 0.0
            val centred = centroid.length() <= 1e-4
            val facing = atan2(centroid.y, centroid.x)
            val tries = forcedTurns[k]?.let { listOf(it) } ?: (0 until 72).toList()
            var bestJ = 0
            for (j in tries) {
                val th = j * PI / 36
                val u = D2(cos(th), sin(th))
                val a = if (centred) 0.0 else th - facing
                val turnedCircles = turned(circles, a)
                val d = slide(u, turnedCircles, placed, 0.15 * tight)
                val shift = u * d
                val moved = turnedCircles.map { UCircle(it.q + shift, it.r) }
                val ext = max(reach, extent(moved))
                val dp = angleGap(th, preferred)
                val key = Triple(rounded(ext, 100.0), rounded(dp, 10000.0), j)
                val held = bestKey
                if (held != null && !lessKey(key, held)) continue
                bestKey = key; bestJ = j
                bestPlace = UPlace(k, shift, a)
                bestCircles = moved; bestReach = ext
            }
            val place = bestPlace ?: continue
            chosenTurns[k] = bestJ
            placed.addAll(bestCircles)
            reach = bestReach
            out.add(place)
        }
        comp[c] = placed
        places[c] = out
    }

    // ---- galaxies, the local group (:1063-1177) ----

    class GalaxyPlacement {
        val order = ArrayList<Int>()
        val centre = HashMap<Int, D2>()
        val frame = HashMap<Int, UniverseFrame>()
        var lo = D2.ZERO
        var hi = D2.ZERO
        var turn = false
        var half = D2.ZERO
        val turns = HashMap<Int, Int>()
    }

    private fun projected(g: Int, m: UniverseFrame): List<UCircle> {
        var circles = comp[g]
        if (circles.size > 12) circles = listOf(enclosing(circles))
        return circles.map { k -> val p = m.apply(D3(k.q.x, k.q.y, 0.0)); UCircle(D2(p.x, p.y), k.r) }
    }

    private fun box(circles: List<UCircle>): Pair<D2, D2> {
        val first = circles.firstOrNull() ?: return D2.ZERO to D2.ZERO
        var lo = D2(first.q.x - first.r, first.q.y - first.r); var hi = D2(first.q.x + first.r, first.q.y + first.r)
        for (c in circles) { lo = lo.min(D2(c.q.x - c.r, c.q.y - c.r)); hi = hi.max(D2(c.q.x + c.r, c.q.y + c.r)) }
        return lo to hi
    }

    private fun placeGalaxies(tops: List<Int>): GalaxyPlacement {
        val result = GalaxyPlacement()
        val gal = tops.sortedWith { a, b -> if (count[a] != count[b]) count[b].compareTo(count[a]) else cmpC(a, b) }
        result.order.addAll(gal)
        for (g in gal) {
            val random = UniverseRandom(tree.cSeed(g) xor 0x7A11uL)
            val inclination = (30 + 10 * random.unit()) * PI / 180
            val phi = 2 * PI * random.unit()
            result.frame[g] = rotZ(phi).times(rotX(inclination))
        }
        val shared = HashMap<Int, Int>()
        val span = cCount
        for ((i, j) in tree.edgePairs) {
            val hi = noteHome[i]; val hj = noteHome[j]
            if (hi < 0 || hj < 0) continue
            val gi = top[hi]; val gj = top[hj]
            if (gi == gj) continue
            shared[gi * span + gj] = (shared[gi * span + gj] ?: 0) + 1
            shared[gj * span + gi] = (shared[gj * span + gi] ?: 0) + 1
        }
        val first = gal.firstOrNull() ?: return result
        val firstFrame = result.frame[first] ?: return result
        result.centre[first] = D2.ZERO
        val placed = ArrayList(projected(first, firstFrame))
        val placedOrder = arrayListOf(first)
        var (lo, hi) = box(placed)
        for (g in gal.drop(1)) {
            val frame = result.frame[g] ?: continue
            val circles = projected(g, frame)
            var partner = placedOrder[0]
            var partnerLinks = shared[g * span + partner] ?: 0
            for (h in placedOrder.drop(1)) {
                val links = shared[g * span + h] ?: 0
                if (links > partnerLinks) { partner = h; partnerLinks = links }
            }
            val partnerAt = result.centre[partner] ?: D2.ZERO
            var bestKey: Triple<Double, Double, Int>? = null
            var bestShift = D2.ZERO
            var bestCircles: List<UCircle> = emptyList()
            var bestJ = 0
            val tries = forcedGalaxyTurns[g]?.let { listOf(it) } ?: (0 until 72).toList()
            for (j in tries) {
                val th = j * PI / 36
                val u = D2(cos(th), sin(th))
                val d = slide(u, circles, placed, 0.30 * tight)
                val shift = u * d
                val moved = circles.map { UCircle(it.q + shift, it.r) }
                val (mlo, mhi) = box(moved)
                val size = hi.max(mhi) - lo.min(mlo)
                val long = max(size.x, size.y)
                val fit = max(long, min(size.x, size.y) / 0.5)
                val apart = (shift - partnerAt).length()
                val key = Triple(rounded(fit, 100.0), rounded(apart, 100.0), j)
                val held = bestKey
                if (held != null && !lessKey(key, held)) continue
                bestKey = key; bestJ = j; bestShift = shift; bestCircles = moved
            }
            result.turns[g] = bestJ
            result.centre[g] = bestShift
            placed.addAll(bestCircles)
            placedOrder.add(g)
            val (mlo, mhi) = box(bestCircles)
            lo = lo.min(mlo); hi = hi.max(mhi)
        }
        result.lo = lo; result.hi = hi
        val size = hi - lo
        result.turn = size.x > size.y
        val half = size * 0.5
        result.half = if (result.turn) D2(half.y, half.x) else half
        return result
    }

    // ---- the bodies (:1181-1361) ----

    private fun emit(placed: GalaxyPlacement) {
        noteBody = IntArray(noteList.size) { -1 }
        cBody = IntArray(cCount) { -1 }
        cFrame = Array(cCount) { IDENTITY }
        cHome = Array(cCount) { D3.ZERO }
        val middle = (placed.lo + placed.hi) * 0.5
        val quarter = rotZ(PI / 2)
        val homes = ArrayList<Triple<Int, D3, UniverseFrame>>()
        for (g in placed.order) {
            val xy = ((placed.centre[g] ?: middle) - middle) * stretch
            var home = D3(xy.x, xy.y, 0.0)
            var frame = placed.frame[g] ?: IDENTITY
            if (placed.turn) { home = D3(-xy.y, xy.x, 0.0); frame = quarter.times(frame) }
            homes.add(Triple(g, home, frame))
        }
        // real depth (T0057): each galaxy lifted to A·u(id) at Space's pitch, its systems with it
        val clusters = homes.map { (g, home, _) -> GraphTilt.Cluster(tree.liftKey(g), home.x.toFloat(), home.y.toFloat(), galaxyReach(g).toFloat()) }
        val lift = GraphTilt.amplitude(clusters, GraphTilt.radians(GraphTilt.SPACE_DEGREES))
        for ((g, home, frame) in homes) {
            putContainer(g, D3(home.x, home.y, GraphTilt.z(tree.liftKey(g), lift).toDouble()), frame, -1)
        }
    }

    private fun galaxyReach(g: Int): Double {
        var reach = 0.0
        for (k in comp[g]) reach = max(reach, sqrt(k.q.x * k.q.x + k.q.y * k.q.y) + k.r)
        return max(reach * stretch, cSphere[g])
    }

    private fun putContainer(c: Int, home: D3, frame: UniverseFrame, parentC: Int) {
        cHome[c] = home
        cFrame[c] = frame
        val parentBody = if (parentC >= 0) cBody[parentC] else -1
        val galaxyBody = if (parentC >= 0) cBody[top[c]] else -1
        val orbit = GraphOrbit.Fixed(GraphUniverse.float3(if (parentC >= 0) home - cHome[parentC] else home))
        val me = bodies.size
        cBody[c] = me
        val isHome = c == homeC
        val kind = if (isHome) UniverseRole.HOME else if (isGalaxy(c)) UniverseRole.GALAXY else UniverseRole.STAR
        val tier = when (kind) { UniverseRole.HOME -> 1; UniverseRole.STAR -> min(depth[c], 3); else -> 0 }
        val title = tree.cName(c)
        bodies.add(UniverseBody(tree.cID(c), kind, parentBody, -1, cSphere[c].toFloat(), tier, count[c] == 0 && !isHome, false,
            tree.palette[c], if (kind == UniverseRole.GALAXY) me else galaxyBody, count[c], 0, -1, orbit, GraphUniverse.float3(home), title,
            UniverseTree.folderLabel(title, count[c])))
        val myGalaxy = if (kind == UniverseRole.GALAXY) me else galaxyBody
        putOrbits(c, home, frame, myGalaxy)
        for (place in places[c]) {
            val k = place.child
            val random = UniverseRandom(tree.cSeed(k) xor 0xC0DEuL)
            val beta = 10 * PI / 180 * random.signed()
            val psi = 2 * PI * random.unit()
            val axis = D3(cos(psi), sin(psi), 0.0)
            val lean = rotZ(place.turn).times(about(axis, beta))
            val childFrame = frame.times(lean)
            val spread = place.offset * stretch
            val step = frame.apply(D3(spread.x, spread.y, 0.0))
            putContainer(k, home + step, childFrame, c)
        }
    }

    private fun putOrbits(c: Int, home: D3, frame: UniverseFrame, galaxy: Int) {
        val random = UniverseRandom(tree.cSeed(c) xor 0x5E11uL)
        val base = 2 * PI * random.unit()
        val owner = cBody[c]
        for ((k, orbit) in orbits[c].withIndex()) {
            val gamma = 4 * PI / 180 * random.signed()
            val psi = 2 * PI * random.unit()
            val axis = D3(cos(psi), sin(psi), 0.0)
            val plane = frame.times(about(axis, gamma))
            val r = orbit.radius * stretch
            val period = GraphUniverse.clamp((orbit.radius / 1.5).pow(1.5) * 90, 45.0, 480.0)
            val rate = 2 * PI / period
            val start = base + k * GraphUniverse.GOLDEN
            val shellIndex = shells.size
            shells.add(UniverseShell(owner, r.toFloat(), GraphUniverse.float3(plane.x), GraphUniverse.float3(plane.y)))
            val m = orbit.members.size.toDouble()
            for ((j, i) in orbit.members.withIndex()) {
                val phase = start + 2 * PI * j / m
                val path = GraphOrbit.Circle(r.toFloat(), GraphUniverse.float3(plane.x), GraphUniverse.float3(plane.y), phase.toFloat(), rate.toFloat())
                val at = home + ring(plane, r, phase)
                putNote(i, owner, owner, galaxy, shellIndex, path, at, tree.palette[c])
                putMoons(i, plane, at, owner, galaxy)
            }
        }
    }

    private fun ring(plane: UniverseFrame, r: Double, angle: Double): D3 = plane.x * (cos(angle) * r) + plane.y * (sin(angle) * r)

    private fun putMoons(p: Int, plane: UniverseFrame, planetHome: D3, light: Int, galaxy: Int) {
        val moons = moonsOf[p]
        if (moons.isEmpty()) return
        val random = UniverseRandom(tree.nSeed(p) xor 0x300uL)
        val delta = 10 * PI / 180 * random.signed()
        val psi = 2 * PI * random.unit()
        val axis = D3(cos(psi), sin(psi), 0.0)
        val tilted = plane.times(about(axis, delta))
        val am = moonRing(p)
        val period = GraphUniverse.clamp((am / 0.4).pow(1.5) * 24, 16.0, 40.0)
        val rate = 2 * PI / period
        val start = 2 * PI * random.unit()
        val m = moons.size.toDouble()
        val planetBody = noteBody[p]
        for ((j, i) in moons.withIndex()) {
            val phase = start + 2 * PI * j / m
            val path = GraphOrbit.Circle(am.toFloat(), GraphUniverse.float3(tilted.x), GraphUniverse.float3(tilted.y), phase.toFloat(), rate.toFloat())
            putNote(i, planetBody, light, galaxy, -1, path, planetHome + ring(tilted, am, phase), 0)
        }
    }

    private fun putNote(i: Int, parent: Int, light: Int, galaxy: Int, shell: Int, orbit: GraphOrbit, home: D3, palette: Int) {
        val note = noteList[i]
        noteBody[i] = bodies.size
        bodies.add(UniverseBody(note.id, NOTE_ROLES[role[i]], parent, light, sphere[i].toFloat(), 0, false, ringed[i], palette, galaxy,
            0, nbr[i].size, shell, orbit, GraphUniverse.float3(home), note.title, UniverseTree.noteLabel(note.title)))
    }

    // ---- comets (:1365-1463) ----

    private fun emitComets(half: D2) {
        val e = UniversePlan.COMET_E
        val galaxyRank = HashMap<Int, Int>()
        val gal = (0 until cCount).filter { parent[it] < 0 }.sortedWith { a, b -> if (count[a] != count[b]) count[b].compareTo(count[a]) else cmpC(a, b) }
        for ((k, g) in gal.withIndex()) galaxyRank[g] = k
        for ((k, i) in loose.withIndex()) {
            if (k >= UniversePlan.ACTIVE_COMETS) { putOort(i, k - UniversePlan.ACTIVE_COMETS, half); continue }
            val tally = LinkedHashMap<Int, Int>()
            for (m in nbr[i]) if (noteHome[m] >= 0) { val g = top[noteHome[m]]; tally[g] = (tally[g] ?: 0) + 1 }
            // the best by (links, size, rank): a strict order, so the walk's order does not matter
            var target = -1
            for ((g, n) in tally) {
                if (target < 0) { target = g; continue }
                val held = tally[target] ?: 0
                if (n != held) { if (n > held) target = g }
                else if (count[g] != count[target]) { if (count[g] > count[target]) target = g }
                else if ((galaxyRank[g] ?: 0) < (galaxyRank[target] ?: 0)) target = g
            }
            val random = UniverseRandom(tree.nSeed(i) xor 0xC0uL)
            var u = D2.ZERO
            var found = false
            if (target >= 0) {
                val at = cHome[target]
                val xy = D2(at.x, at.y)
                val size = xy.length()
                if (size > 0.5 * stretch) { u = xy / size; found = true }
            }
            if (!found) {
                val angle = 2 * PI * random.unit() + k * GraphUniverse.GOLDEN
                u = D2(cos(angle), sin(angle))
            }
            val v = D2(-u.y, u.x)
            val a = min(reach(u.x, v.x, half.x, e), reach(u.y, v.y, half.y, e))
            val beta = 12 * PI / 180 * random.signed()
            val big = D3(u.x, u.y, 0.0)
            val tilted = GraphUniverse.rotate(D3(v.x, v.y, 0.0), big, beta)
            val period = GraphUniverse.clamp((a / 5).pow(1.5) * 240, 150.0, 600.0)
            val spread = k * 0.618034 + random.unit()
            val frac = spread - floor(spread)
            val path = GraphOrbit.Kepler(a.toFloat(), e.toFloat(), GraphUniverse.float3(big), GraphUniverse.float3(tilted),
                (2 * PI * frac).toFloat(), (2 * PI / period).toFloat())
            putLoose(i, path, GraphUniverse.offset(path, 0.0), -2)
        }
    }

    private fun reach(ux: Double, vx: Double, half: Double, e: Double): Double {
        val squeeze = 1 - e * e
        val across = sqrt(ux * ux + squeeze * vx * vx)
        val need = e * abs(ux) + across
        if (!(need > 1e-9)) return 1e9
        return 0.95 * half / need
    }

    private fun putOort(i: Int, j: Int, half: D2) {
        val random = UniverseRandom(tree.nSeed(i))
        val z = 0.25 * random.signed() * stretch
        val ox = half.x * 1.06 + 0.3 * stretch
        val oy = half.y * 1.06 + 0.3 * stretch
        val path = GraphOrbit.Ring(ox.toFloat(), oy.toFloat(), z.toFloat(), (j * GraphUniverse.GOLDEN).toFloat(), (2 * PI / 1200).toFloat())
        putLoose(i, path, GraphUniverse.offset(path, 0.0), -1)
    }

    private fun putLoose(i: Int, orbit: GraphOrbit, home: Vec3, light: Int) {
        val note = noteList[i]
        noteBody[i] = bodies.size
        bodies.add(UniverseBody(note.id, if (role[i] == 4) UniverseRole.COMET else UniverseRole.OORT, -1, light, sphere[i].toFloat(), 0,
            false, false, 0, -1, 0, nbr[i].size, -1, orbit, home, note.title, UniverseTree.noteLabel(note.title)))
    }

    // ---- links, framing and the summary (:1467-1528) ----

    private fun finish(placed: GalaxyPlacement): UniversePlan {
        val links = ArrayList<UniverseLink>()
        for ((i, j) in tree.edgePairs) {
            val a = noteBody[i]; val b = noteBody[j]
            if (a < 0 || b < 0) continue
            links.add(UniverseLink(a, b, linkKind(i, j), linkCentre(i, j)))
        }
        val envelope = ArrayList<Vec3>()
        for (g in placed.order) envelope.addAll(sample(g, cHome[g]))
        for (body in bodies) if (body.role == UniverseRole.COMET || body.role == UniverseRole.OORT) envelope.addAll(UniversePlan.trace(body.orbit))
        val systems = MutableList<List<Vec3>>(bodies.size) { emptyList() }
        val lights = ArrayList<Int>()
        for (c in 0 until cCount) {
            val b = cBody[c]
            if (b < 0) continue
            systems[b] = sample(c, D3.ZERO)
            if (!bodies[b].empty) lights.add(b)
        }
        lights.sort()
        return UniversePlan(bodies.toList(), shells.toList(), links, envelope, systems, lights, UniversePlan.summary(bodies))
    }

    private fun linkKind(i: Int, j: Int): Int {
        if (moonPlanet[i] == j || moonPlanet[j] == i) return 3
        val hi = noteHome[i]; val hj = noteHome[j]
        if (hi < 0 || hj < 0) return 2
        if (hi == hj) return 0
        return if (top[hi] == top[hj]) 1 else 2
    }

    private fun linkCentre(i: Int, j: Int): Int = if (linkKind(i, j) != 1) -1 else cBody[top[noteHome[i]]]

    private fun sample(c: Int, origin: D3): List<Vec3> {
        val out = ArrayList<Vec3>()
        val frame = cFrame[c]
        for (circle in comp[c]) {
            val q = circle.q * stretch
            val r = circle.r * stretch
            for (k in 0 until 8) {
                val a = k * PI / 4
                out.add(GraphUniverse.float3(origin + frame.apply(D3(q.x + r * cos(a), q.y + r * sin(a), 0.0))))
            }
        }
        return out
    }

    companion object {
        private val NOTE_ROLES = listOf(UniverseRole.GAS_GIANT, UniverseRole.ROCKY, UniverseRole.MOON, UniverseRole.PULSAR,
            UniverseRole.COMET, UniverseRole.OORT)

        /** The smallest a star at depth [d] is drawn (GraphUniverse.swift:407-409). */
        fun starFloor(d: Int): Double = 0.262 + 0.016 / max(d, 1)
    }
}
