package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Neurons theme's look as numbers, in plain Kotlin so the JVM tests check it: the palette,
// the six cell states and the Look menu's choice, the backdrop's bokeh and far cells (iOS
// GraphNeuronStates); what kind of cell each body is drawn as, its turns, its dendrites, its
// state's movement and its materials' values (iOS GraphNeuronLook); the links as one dendrite
// joining two cells (iOS GraphLinkBridge and GraphRibbonWriter's bridge strip); the fluid's
// glows; and the Neurons' depth band (iOS GraphDepthBand.neuronsHigh and neuronsSmooth).
// Colours are sRGB as on screen (the GL framebuffer is not sRGB, so iOS's pow(2.2) into linear
// light is left out, as SpaceShaders leaves it out).

private const val FPI = PI.toFloat()

/** The cells' colours (iOS NeuronPalette). */
object NeuronPalette {
    data class Dye(val main: Vec3, val accent: Vec3)

    val dyes = listOf(
        Dye(Vec3(0.35f, 1.0f, 0.5f), Vec3(1.0f, 0.74f, 0.28f)),
        Dye(Vec3(0.25f, 0.92f, 1.0f), Vec3(1.0f, 0.42f, 0.78f)),
        Dye(Vec3(1.0f, 0.42f, 0.78f), Vec3(0.25f, 0.92f, 1.0f)),
        Dye(Vec3(1.0f, 0.74f, 0.28f), Vec3(0.35f, 1.0f, 0.5f)),
        Dye(Vec3(0.66f, 0.5f, 1.0f), Vec3(0.25f, 0.92f, 1.0f)),
    )
    val receptor = Vec3(1.0f, 0.84f, 0.48f)
    val drifter = Vec3(0.62f, 0.8f, 0.98f)
    val interior = Vec3(0.2f, 0.07f, 0.46f)
    val heart = Vec3(0.52f, 0.24f, 0.98f)
    val nucleus = Vec3(0.82f, 0.36f, 1.0f)
    val nucleolus = Vec3(1.0f, 0.5f, 0.88f)
    val impulse = Vec3(1.0f, 0.72f, 0.30f)
    val impulseHalo = Vec3(1.0f, 0.58f, 0.22f)
    val deep = Vec3(0.02f, 0.05f, 0.085f)
    const val IDEA_LEAN = 0.45f
    val glowViolet = Vec3(0.78f, 0.34f, 0.95f)

    fun dye(slot: Int, idea: Boolean = false): Vec3 {
        if (slot == 5) return receptor
        if (slot == 6) return drifter
        val pair = dyes[max(slot, 0) % dyes.size]
        if (!idea) return pair.main
        return pair.main + (pair.accent - pair.main) * IDEA_LEAN
    }

    fun glow(slot: Int, idea: Boolean = false): Vec3 {
        val own = dye(slot, idea)
        if (slot == 5 || slot == 6) return own
        return own * 0.5f + glowViolet * 0.5f
    }
}

/** A cell's state: one biological process each, mirroring one space style (iOS NeuronState). */
enum class NeuronState(val rawValue: String, val code: Int, val style: SpaceStyle, val title: String,
                       val process: String, val symbol: String) {
    RESTING("resting", 0, SpaceStyle.ROCKY, "Resting", "at rest, slowly breathing", "globe.europe.africa.fill"),
    FIRING("firing", 1, SpaceStyle.SUN, "Firing", "a burst of spikes, calcium waves spreading", "sun.max.fill"),
    RELEASING("releasing", 2, SpaceStyle.GAS_GIANT, "Releasing", "a cloud of transmitter drifting out",
        "circle.lefthalf.filled"),
    PACEMAKER("pacemaker", 3, SpaceStyle.PULSAR, "Pacemaker", "a steady beat, its lobes sweeping round",
        "dot.radiowaves.left.and.right"),
    MIGRATING("migrating", 4, SpaceStyle.COMET, "Migrating", "crawling on, its growth cone leading", "sparkle"),
    ENGULFING("engulfing", 5, SpaceStyle.BLACK_HOLE, "Engulfing", "drawing debris into a dark phagosome",
        "circle.circle.fill");

    companion object {
        fun of(style: SpaceStyle): NeuronState = entries.first { it.style == style }
        fun raw(value: String): NeuronState? = entries.firstOrNull { it.rawValue == value }
        val menuOrder: List<NeuronState> get() = SpaceStyle.menuOrder.map { of(it) }
        fun natural(role: NeuronRole): NeuronState = when (role) {
            NeuronRole.RECEPTOR -> MIGRATING
            NeuronRole.DRIFTER -> ENGULFING
            else -> RESTING
        }
        val beatPeriod: Float get() = SpaceOptics.PULSAR_PERIOD
    }
}

/** What the Look menu chose for the Neurons: one state for every note, one per cell (iOS NeuronStateChoice). */
data class NeuronStateChoice(val main: String = NATURAL_VALUE, val folders: Map<String, NeuronState> = emptyMap()) {
    val isCustom: Boolean get() = main != NATURAL_VALUE || folders.isNotEmpty()

    /** The state body `i` of `plan` shows: its cell's, else the one for all, else its role's; a container rests. */
    fun state(i: Int, plan: ThemePlan): NeuronState {
        if (i < 0 || i >= plan.bodies.size) return NeuronState.RESTING
        val body = plan.bodies[i]
        if (body.kind != ThemeBodyKind.NOTE) return NeuronState.RESTING
        if (body.region >= 0 && body.region < plan.bodies.size) folders[plan.bodies[body.region].id]?.let { return it }
        NeuronState.raw(main)?.let { return it }
        return NeuronState.natural(NeuronRole.of(body.role) ?: NeuronRole.GRANULE)
    }

    companion object {
        const val KEY = "vignette.neurons.cellState"
        const val FOLDERS_KEY = "vignette.neurons.cellStateFolders"
        const val NATURAL_VALUE = "natural"
        val NATURAL = NeuronStateChoice()

        fun of(main: String, folderRaw: String) = NeuronStateChoice(main, parse(folderRaw))

        private val uuid = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

        /** "uuid=state;uuid=state" to a map (ids uppercase, as iOS's uuidString); anything unreadable skipped. */
        fun parse(raw: String): Map<String, NeuronState> {
            val found = LinkedHashMap<String, NeuronState>()
            for (part in raw.split(";")) {
                if (part.isEmpty()) continue
                val pieces = part.split("=").filter { it.isNotEmpty() }
                if (pieces.size != 2 || !uuid.matches(pieces[0])) continue
                val state = NeuronState.raw(pieces[1]) ?: continue
                found[pieces[0].uppercase()] = state
            }
            return found
        }

        fun encode(map: Map<String, NeuronState>): String =
            map.keys.sorted().joinToString(";") { it + "=" + map.getValue(it).rawValue }
    }
}

data class NeuronBokehDisc(val direction: Vec3, val size: Float, val colour: Vec3, val strength: Float)

data class NeuronFarCell(val direction: Vec3, val size: Float, val turn: Float, val colour: Vec3, val strength: Float)

/** The backdrop's bokeh and far cells (iOS NeuronBokeh): the same every time. */
object NeuronBokeh {
    const val COUNT = 84
    const val FAR_COUNT = 28
    val tones = listOf(Vec3(1.0f, 0.74f, 0.36f), Vec3(0.3f, 0.5f, 1.0f), Vec3(1.0f, 0.6f, 0.25f),
        Vec3(0.35f, 0.8f, 1.0f), Vec3(0.6f, 0.45f, 1.0f))
    private val farTones = listOf(Vec3(0.3f, 0.5f, 1.0f), Vec3(0.25f, 0.75f, 1.0f), Vec3(0.5f, 0.4f, 1.0f))

    private fun jittered(d: Vec3, random: UniverseRandom, by: Float): Vec3 {
        val jitter = Vec3(random.signed().toFloat(), random.signed().toFloat(), random.signed().toFloat())
        val v = d + jitter * by
        val length = sqrt(v.dot(v))
        return if (length > 0.0001f) v * (1f / length) else d
    }

    fun discs(n: Int = COUNT): List<NeuronBokehDisc> {
        val random = UniverseRandom(0xB0E4uL)
        return (0 until n).map { k ->
            val d = GraphUniverse.float3(ThemeLayout.fibonacci(k, n))
            val dir = jittered(d, random, 0.22f)
            val u = random.unit().toFloat()
            val size = 0.018f + 0.1f * u * u
            val strength = (0.35f + 0.65f * random.unit().toFloat()) * (1 - 0.6f * (size - 0.018f) / 0.1f)
            NeuronBokehDisc(dir, size, tones[k % tones.size], strength)
        }
    }

    fun farCells(n: Int = FAR_COUNT): List<NeuronFarCell> {
        val random = UniverseRandom(0xFA2CuL)
        return (0 until n).map { k ->
            val d = GraphUniverse.float3(ThemeLayout.fibonacci(k, n))
            val dir = jittered(d, random, 0.25f)
            val u = random.unit().toFloat()
            val size = 0.12f + 0.16f * u
            val turn = random.unit().toFloat() * 2 * FPI
            val strength = (0.5f + 0.5f * random.unit().toFloat()) * (1 - 0.4f * u)
            NeuronFarCell(dir, size, turn, farTones[k % farTones.size], strength)
        }
    }
}

/** What kind of cell a body is drawn as (iOS NeuronCellKind). */
enum class NeuronCellKind(val nucleus: Float, val wobble: Float, val fill: Float) {
    CELL(0.3f, 0.025f, 1f),
    PART(0f, 0.035f, 0.45f),
    VESICLE(0f, 0.04f, 0.2f),
    GRANULE(0.5f, 0.02f, 0.8f),
    RECEPTOR(0.4f, 0.04f, 1f),
    DRIFTER(0.3f, 0.06f, 1f),
    BIPOLAR(0.4f, 0.04f, 1f);

    val branches: Boolean get() = this == CELL || this == RECEPTOR || this == DRIFTER || this == BIPOLAR
    val holds: Boolean get() = this == CELL || this == PART

    companion object {
        fun of(role: NeuronRole): NeuronCellKind = when (role) {
            NeuronRole.CELL, NeuronRole.HOME -> CELL
            NeuronRole.PART -> PART
            NeuronRole.VESICLE -> VESICLE
            NeuronRole.GRANULE -> GRANULE
            NeuronRole.RECEPTOR -> RECEPTOR
            NeuronRole.DRIFTER -> DRIFTER
        }

        /** The shape a state gives a free note; a cell and what floats in one keep their own. */
        fun shape(kind: NeuronCellKind, state: NeuronState): NeuronCellKind {
            if (kind != RECEPTOR && kind != DRIFTER && kind != BIPOLAR) return kind
            return when (state) {
                NeuronState.PACEMAKER -> BIPOLAR
                NeuronState.MIGRATING -> RECEPTOR
                NeuronState.ENGULFING -> DRIFTER
                else -> kind
            }
        }
    }
}

/** A unit quaternion (iOS simd_quatf), with what the look needs. */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(q: Quat) = Quat(
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w,
        w * q.w - x * q.x - y * q.y - z * q.z,
    )

    val inverse: Quat get() = Quat(-x, -y, -z, w)

    fun act(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        val t = u.cross(v) * 2f
        return v + t * w + u.cross(t)
    }

    /** Its turn as a column-major 3x3 matrix. */
    fun matrix(): FloatArray {
        val c0 = act(Vec3(1f, 0f, 0f)); val c1 = act(Vec3(0f, 1f, 0f)); val c2 = act(Vec3(0f, 0f, 1f))
        return floatArrayOf(c0.x, c0.y, c0.z, c1.x, c1.y, c1.z, c2.x, c2.y, c2.z)
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        fun angleAxis(angle: Float, axis: Vec3): Quat {
            val n = axis.normalized()
            val s = sin(angle / 2)
            return Quat(n.x * s, n.y * s, n.z * s, cos(angle / 2))
        }

        /** The shortest turn carrying unit `from` to unit `to` (simd_quatf(from:to:)). */
        fun fromTo(from: Vec3, to: Vec3): Quat {
            val d = from.dot(to)
            if (d < -0.999999f) return angleAxis(FPI, NeuronGeometry.perpendicular(from))
            val c = from.cross(to)
            val s = sqrt((1 + d) * 2)
            return Quat(c.x / s, c.y / s, c.z / s, s / 2)
        }
    }
}

/** iOS GraphLinkCurve's small vector helpers, as the look uses them. */
object NeuronGeometry {
    fun unit(v: Vec3, fallback: Vec3): Vec3 { val s = v.length(); return if (s > 0.00001f) v * (1f / s) else fallback }
    fun perpendicular(dir: Vec3): Vec3 =
        unit(dir.cross(if (abs(dir.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)), Vec3(0f, 0f, 1f))
    fun rotate(v: Vec3, axis: Vec3, angle: Float): Vec3 {
        val c = cos(angle); val s = sin(angle)
        return v * c + axis.cross(v) * s + axis * (axis.dot(v) * (1 - c))
    }
    fun curve(a: Vec3, b: Vec3, c: Vec3, t: Float): Vec3 { val u = 1 - t; return a * (u * u) + b * (2 * u * t) + c * (t * t) }
}

/** A tube mesh: positions and normals (xyz), coords (u along, v round) and triangles. */
class NeuronMesh(val points: FloatArray, val normals: FloatArray, val coords: FloatArray, val indices: IntArray)

/** The dendrites' shapes (iOS NeuronArbor). */
object NeuronArbor {
    /**
     * The branches a kind of cell grows of its own: none. A process exists only where it
     * attaches to something (the owner, 29 Oct), and a cell's links are already dendrites that
     * leave it through a mouth in its membrane, so a cell is its soma and its links, and a free
     * note its soma alone. Stethoscore's iOS crowned a cell with six or seven dendrites that led
     * nowhere, gave a receptor a fan, a drifter fine processes and a bipolar cell two long ones.
     */
    @Suppress("UNUSED_PARAMETER")
    fun branches(kind: NeuronCellKind, variant: Int, rich: Boolean): List<NeuronBranch> = emptyList()

    /** Every branch as a tube of `sides` round and `segments` along (u the share along, v round it). */
    fun mesh(branches: List<NeuronBranch>, sides: Int, segments: Int): NeuronMesh {
        val points = FloatList(); val normals = FloatList(); val coords = FloatList(); val indices = IntList()
        val ring = max(sides, 3)
        val steps = max(segments, 2)
        var count = 0
        for (branch in branches) {
            val first = count
            for (j in 0..steps) {
                val even = j.toFloat() / steps
                val t = if (branch.flare > 0) even.pow(1.6f) else even
                val p = NeuronGeometry.curve(branch.start, branch.bend, branch.end, t)
                val ahead = NeuronGeometry.curve(branch.start, branch.bend, branch.end, min(t + 0.02f, 1f))
                val behind = NeuronGeometry.curve(branch.start, branch.bend, branch.end, max(t - 0.02f, 0f))
                val along = NeuronGeometry.unit(ahead - behind, Vec3(0f, 1f, 0f))
                val n1 = NeuronGeometry.perpendicular(along)
                val n2 = along.cross(n1)
                val rest = 1 - t
                val bell = rest * rest
                val taper = branch.r0 + (branch.r1 - branch.r0) * t + branch.flare * bell * bell
                val s = branch.s0 + (branch.s1 - branch.s0) * t
                for (k in 0..ring) {
                    val a = k.toFloat() / ring * 2 * FPI
                    val n = n1 * cos(a) + n2 * sin(a)
                    val v = p + n * taper
                    points.add(v.x); points.add(v.y); points.add(v.z)
                    normals.add(n.x); normals.add(n.y); normals.add(n.z)
                    coords.add(s); coords.add(k.toFloat() / ring)
                    count++
                }
            }
            val row = ring + 1
            for (j in 0 until steps) for (k in 0 until ring) {
                val a = first + j * row + k
                val b = a + row
                indices.add(a); indices.add(b); indices.add(a + 1); indices.add(a + 1); indices.add(b); indices.add(b + 1)
            }
        }
        return NeuronMesh(points.toArray(), normals.toArray(), coords.toArray(), indices.toArray())
    }
}

/** A state's own movement (iOS GraphNeuronLook.move, SceneKit's actions as functions of time). */
object NeuronMotion {
    /** One step: to [to] over [d] seconds (easeOut 1, easeInEaseOut 2, linear 0), or a wait (to null). */
    class Step(val d: Float, val to: Float?, val ease: Int = 2)

    private fun eased(t: Float, ease: Int): Float = when (ease) {
        1 -> 1 - (1 - t) * (1 - t)
        2 -> t * t * (3 - 2 * t)
        else -> t
    }

    /** A loop of absolute-target steps, started at [start], run [t] seconds; the loop's value then. */
    fun loop(steps: List<Step>, start: Float, t: Float): Float {
        val total = steps.sumOf { it.d.toDouble() }.toFloat()
        if (!(total > 0) || t <= 0) return start
        // the value a loop ends on: its last target
        val end = steps.lastOrNull { it.to != null }?.to ?: start
        var at = t % total
        var from = if (t >= total) end else start
        for (s in steps) {
            if (at < s.d) return if (s.to == null) from else from + (s.to - from) * eased(at / s.d, s.ease)
            at -= s.d
            if (s.to != null) from = s.to
        }
        return from
    }

    /** A loop of relative moves (steps' `to` as an amount): how far it has gone in all at [t]. */
    fun moves(steps: List<Step>, t: Float): Float {
        val total = steps.sumOf { it.d.toDouble() }.toFloat()
        if (!(total > 0) || t <= 0) return 0f
        var at = t % total
        var sum = 0f
        for (s in steps) {
            val by = s.to ?: 0f
            if (at < s.d) return sum + by * eased(at / s.d, s.ease)
            at -= s.d
            sum += by
        }
        return sum
    }

    private fun pulse(to: Float, from: Float, rise: Float, fall: Float) = listOf(Step(rise, to, 1), Step(fall, from, 2))

    /** What a body's state does at clock [t]: the soma's scale share, a move along its up, the arbor's. */
    data class Pose(val soma: Float, val arbor: Float, val somaMove: Float, val arborMove: Float, val arborTurn: Float)

    fun pose(state: NeuronState, inner: Boolean, hasArbor: Boolean, lag: Float, time: Float): Pose {
        val t = time - lag
        if (t <= 0) return Pose(1f, 1f, 0f, 0f, 0f)
        val period = NeuronState.beatPeriod
        return when (state) {
            NeuronState.RESTING -> Pose(1f, 1f, 0f, 0f, 0f)
            NeuronState.FIRING -> {
                val spike = pulse(1.07f, 1f, 0.05f, 0.16f)
                Pose(loop(spike + spike + spike + listOf(Step(1.2f, null)), 1f, t), 1f, 0f, 0f, 0f)
            }
            NeuronState.RELEASING -> Pose(loop(pulse(1.06f, 1f, 1.6f, 2.2f), 1f, t),
                if (hasArbor) loop(pulse(1.03f, 1f, 1.6f, 2.2f), 1f, t) else 1f, 0f, 0f, 0f)
            NeuronState.PACEMAKER -> {
                val kick = pulse(1.05f, 1f, 0.06f, 0.3f) + listOf(Step(max(period / 2 - 0.36f, 0.05f), null))
                Pose(loop(kick, 1f, t), 1f, 0f, 0f, if (hasArbor) 2 * FPI * ((t / (period * 4)) % 1f) else 0f)
            }
            NeuronState.MIGRATING -> if (inner) Pose(loop(pulse(1.04f, 1f, 1.4f, 2.2f), 1f, t), 1f, 0f, 0f, 0f)
            else Pose(1f, 1f, moves(listOf(Step(1.4f, 0.35f), Step(0.4f, null), Step(2.2f, -0.35f)), t),
                moves(listOf(Step(1.8f, 0.175f), Step(2.2f, -0.175f)), t), 0f)
            NeuronState.ENGULFING -> if (!hasArbor) Pose(loop(listOf(Step(1.8f, 0.9f), Step(2.4f, 1.04f)), 1f, t), 1f, 0f, 0f, 0f)
            else Pose(1f, loop(listOf(Step(1.8f, 0.86f), Step(2.4f, 1.04f)), 1f, t), 0f, 0f,
                (FPI / 3) * (t / 4.2f) % (2 * FPI))
        }
    }
}

/** Everything one body's drawing needs, worked out once a plan (iOS GraphNeuronLook.makeBody). */
data class NeuronBodyLook(
    val index: Int,
    val kind: NeuronCellKind,
    val state: NeuronState,
    val slot: Int,
    val idea: Boolean,
    val inner: Boolean,
    val open: Boolean,
    val size: Float,
    /** A whole cell: its soma and dendrites one membrane. */
    val whole: Boolean,
    val variant: Int,
    val somaTurn: Quat,
    /** The arbor's own turn (a smaller cell's tubes), or null for none. */
    val arborTurn: Quat?,
    val standingHalo: Boolean,
    val haloTint: Vec3,
    val membraneColour: Vec3,
    /** The gold inside the dendrites and the membrane: (1, 0.74, 0.36) * 0.88 + dye * 0.12, times 0.95. */
    val gold: Vec3,
    val lag: Float,
    /** The arbor's +y (or the soma's), the way a migrating cell steps. */
    val up: Vec3,
)

/** The Neurons theme's whole look for one plan, as numbers. */
class NeuronLookPlan(val plan: ThemePlan, val cells: NeuronStateChoice = NeuronStateChoice.NATURAL,
                     val tier: GraphicsTier = GraphicsTier.HIGH) {
    val slotOf: Map<Int, Int>
    val holding: Set<Int>
    val bodies: List<NeuronBodyLook>

    init {
        slotOf = plan.regions.withIndex().associate { (k, r) -> r to k % NeuronPalette.dyes.size }
        holding = plan.bodies.filter { it.parent >= 0 }.map { it.parent }.toSet()
        bodies = plan.bodies.indices.map { look(it) }
    }

    fun slot(body: ThemeBody): Int {
        if (body.region >= 0) return slotOf[body.region] ?: 0
        return if (body.role == NeuronRole.DRIFTER.code) 6 else 5
    }

    private fun look(index: Int): NeuronBodyLook {
        val body = plan.bodies[index]
        val role = NeuronRole.of(body.role) ?: NeuronRole.GRANULE
        val state = cells.state(index, plan)
        val kind = NeuronCellKind.shape(NeuronCellKind.of(role), state)
        val idea = kind == NeuronCellKind.PART || kind == NeuronCellKind.GRANULE
        val inner = body.parent >= 0
        val open = kind.holds && holding.contains(index)
        val dyeSlot = slot(body)
        val random = UniverseRandom(body.seed xor 0xCE11uL)
        var somaTurn = randomTurn(random)
        var arborTurn: Quat? = null
        val whole = kind == NeuronCellKind.CELL
        val variant = (body.seed % 3uL).toInt()
        if (kind.branches) {
            val turn = arborTurn(kind, body.axis, random)
            if (whole) somaTurn = turn
            else if (NeuronArbor.branches(kind, variant, tier == GraphicsTier.HIGH).isNotEmpty()) arborTurn = turn
        }
        val lag = (random.unit() * 2).toFloat()
        val standing = (tier == GraphicsTier.HIGH && !inner) || state != NeuronState.RESTING
        val membrane = NeuronPalette.dye(dyeSlot, idea)
        val golden = Vec3(1.0f, 0.74f, 0.36f) * 0.88f + membrane * 0.12f
        val haloTint = Vec3(1.0f, 0.74f, 0.36f) * 0.7f + NeuronPalette.glow(dyeSlot, idea) * 0.3f
        val up = (arborTurn ?: somaTurn).act(Vec3(0f, 1f, 0f))
        return NeuronBodyLook(index, kind, state, dyeSlot, idea, inner, open, body.sphere, whole, variant, somaTurn,
            arborTurn, standing, haloTint, membrane, golden * 0.95f, lag, up)
    }

    companion object {
        val sparkle = Vec3(1.0f, 0.88f, 0.6f)
        val fibre = Vec3(1.0f, 0.8f, 0.45f)
        val tract = Vec3(1.0f, 0.72f, 0.38f)
        /** An arriving impulse's glow: its amber, a quarter of the way to its halo's orange. */
        val arrivalGlow: Vec3 get() = NeuronPalette.impulse + (NeuronPalette.impulseHalo - NeuronPalette.impulse) * 0.25f
        /** The bridges' half widths: inside an opened cell, and between cells. */
        const val NEAR_BRIDGE = 0.09f
        const val FAR_BRIDGE = 0.12f

        /** The impulses' chance a slot fires and a firing is a burst, by tier (held still: none). */
        fun rate(tier: GraphicsTier, lively: Boolean): Float = if (!lively) 0f else if (tier == GraphicsTier.HIGH) 0.55f else 0.3f
        fun burst(tier: GraphicsTier, lively: Boolean): Float = if (lively && tier == GraphicsTier.HIGH) 0.18f else 0f
        fun detail(tier: GraphicsTier): Float = if (tier == GraphicsTier.HIGH) 1f else 0f

        fun randomTurn(random: UniverseRandom): Quat {
            val y = random.signed().toFloat()
            val angle = random.unit().toFloat() * 2 * FPI
            val ring = sqrt(max(1 - y * y, 0f))
            val axis = Vec3(cos(angle) * ring, y, sin(angle) * ring)
            val spin = random.unit().toFloat() * 2 * FPI
            return Quat.angleAxis(spin, axis)
        }

        fun arborTurn(kind: NeuronCellKind, axis: Vec3, random: UniverseRandom): Quat {
            if (kind == NeuronCellKind.CELL) {
                val spin = random.unit().toFloat() * 2 * FPI
                val tip = 0.2f * random.signed().toFloat()
                return Quat.angleAxis(tip, Vec3(1f, 0f, 0f)) * Quat.angleAxis(spin, Vec3(0f, 0f, 1f))
            }
            if (kind != NeuronCellKind.RECEPTOR && kind != NeuronCellKind.BIPOLAR) return randomTurn(random)
            val up = Vec3(0f, 1f, 0f)
            val size = axis.length()
            val to = if (size > 0.0001f) axis * (1f / size) else up
            val twist = random.unit().toFloat() * 2 * FPI
            return Quat.angleAxis(twist, to) * Quat.fromTo(up, to)
        }
    }
}

/** Lights each cell as impulses reach it and eases openings (iOS NeuronImpulses), on the render thread. */
class NeuronPulses(count: Int, private val rate: Float, private val bursts: Float) {
    val level = FloatArray(count)
    private var last = -1f

    /** Moves the glows on to clock [time], [step] seconds after the last frame; `links` (b, seed) land. */
    fun tick(time: Float, step: Float, ends: IntArray, seeds: IntArray) {
        if (!(last >= 0 && time > last && time - last < 1)) { last = time; return }
        val fade = exp(-step / 0.35f)
        for (i in level.indices) if (level[i] > 0) level[i] *= fade
        if (rate > 0) for (k in ends.indices) {
            val b = ends[k]
            if (b < 0 || b >= level.size) continue
            if (NeuronImpulse.lands(seeds[k], last, time, rate, bursts)) level[b] = 1f
        }
        last = time
    }

    /** What the glow of body [i] shows: nothing below 0.02. */
    fun shown(i: Int): Float = if (level[i] < 0.02f) 0f else level[i]

    companion object {
        /** A container's opening (0 to 1) [since] seconds after it started: smoothstepped over 0.9 s. */
        fun opening(from: Float, to: Float, since: Float): Float {
            val t = min(max(since / 0.9f, 0f), 1f)
            return from + (to - from) * (t * t * (3 - 2 * t))
        }
    }
}

/** A Neurons link as one dendrite joining two cells (iOS GraphLinkBridge). */
data class NeuronBridge(val membrane: Float = 1.25f, val blend: Float = NeuronMembrane.BLEND) {
    data class End(val centre: Vec3, val radius: Float, val view: Vec3, val gate: Float, val trim: Float)

    fun join(r: Float, h: Float): Float { val edge = h + blend * r; return sqrt(max(r * r - edge * edge, 0f)) }
    fun flare(r: Float, h: Float): Float { val reach = r + blend * r; return sqrt(max(reach * reach - h * h, 0f)) }

    fun halfWidth(rho: Float, r: Float, h: Float): Float {
        if (!(r > 0.0001f && h > 0)) return h
        val x = max(rho, join(r, h))
        if (x >= flare(r, h)) return h
        val k = blend * r
        var low = 0f
        var high = max(h, r) + k
        for (n in 0 until 22) {
            val mid = (low + high) * 0.5f
            val disc = sqrt(x * x + mid * mid) - r
            if (smin(disc, mid - h, k) < 0) low = mid else high = mid
        }
        return max((low + high) * 0.5f, h)
    }

    fun trim(r: Float, h: Float, sine: Float): Float = join(r, h) / max(sine, 0.5f)

    fun end(centre: Vec3, radius: Float, eye: Vec3, dir: Vec3, h: Float, length: Float): End {
        val r = radius * membrane
        val toward = eye - centre
        val far = toward.length()
        val view = if (far > 0.000001f) toward * (1f / far) else Vec3(0f, 0f, 1f)
        val sine = sine(dir, view)
        return End(centre, r, view, gate(sine), min(trim(r, h, sine), max(length, 0f) * 0.45f))
    }

    fun halfWidth(p: Vec3, a: End, b: End, h: Float): Float {
        val wa = halfWidth(seen(p - a.centre, a.view), a.radius, h) - h
        val wb = halfWidth(seen(p - b.centre, b.view), b.radius, h) - h
        return h + max(wa * a.gate, wb * b.gate)
    }

    companion object {
        const val CROWD = 0.85f
        fun smin(a: Float, b: Float, k: Float): Float { val over = max(k - abs(a - b), 0f); return min(a, b) - over * over / (4 * k) }
        fun gate(sine: Float): Float { val t = min(max((sine - 0.3f) / 0.2f, 0f), 1f); return t * t * (3 - 2 * t) }
        fun sine(dir: Vec3, view: Vec3): Float { val c = dir.dot(view); return sqrt(max(1 - c * c, 0f)) }
        fun seen(rel: Vec3, view: Vec3): Float = (rel - view * rel.dot(view)).length()
        fun warp(u: Float): Float = u - CROWD * sin(2 * FPI * u) / (2 * FPI)

        /** iOS GraphRibbonWriter's width step (0...7) for a link whose smaller cell's radius is [r]. */
        fun widthStep(r: Float): Int {
            val w = min(max(r / 0.3f, 0.12f), 1f)
            val k = GraphUniverse.roundHalfAway((ln(w / 0.12f) / 0.300105f).toDouble())
            return min(max(k.toInt(), 0), 7)
        }

        fun stepWidth(k: Int): Float = 0.12f * exp(k * 0.300105f)

        fun themeSeed(seed: Int): Int { val w = seed % 32; return if (w < 0) w + 32 else w }

        const val ALONG_CAP = 60f
    }

    /**
     * Writes one link's strip in the bridge coding the bridge shader reads (iOS fillBridge):
     * `samples + 1` pairs of vertices into [pos] (xyz) and [uv] from vertex [first]. u is
     * (width step * 32 + seed) * 64 + 1 + the distance from the strip's start, v the length in
     * sixteenths doubled plus the lit bit, plus the position across.
     */
    fun fill(pa: Vec3, pb: Vec3, ra: Float, rb: Float, seed: Int, grow: Float, lit: Boolean, half: Float, eye: Vec3,
             samples: Int, pos: FloatArray, uv: FloatArray, first: Int) {
        val n = samples
        val delta = pb - pa
        val length = delta.length()
        val dir = if (length > 0.0001f) delta * (1f / length) else Vec3(1f, 0f, 0f)
        val step = widthStep(min(ra, rb))
        val h = half * stepWidth(step)
        val endA = end(pa, ra, eye, dir, h, length)
        val endB = end(pb, rb, eye, dir, h, length)
        val start = pa + dir * endA.trim
        val stop = pb - dir * endB.trim
        val points = Array(n + 1) { Vec3.ZERO }
        val lengths = FloatArray(n + 1)
        var total = 0f
        for (k in 0..n) {
            val p = start + (stop - start) * (warp(k.toFloat() / n) * grow)
            if (k > 0) total += (p - points[k - 1]).length()
            points[k] = p
            lengths[k] = total
        }
        val code = step * 32 + themeSeed(seed)
        val scale = if (total > ALONG_CAP) ALONG_CAP / total else 1f
        val coded = min(total * scale, ALONG_CAP)
        val band = (kotlin.math.floor(coded * 16).toInt() * 2 + if (lit) 1 else 0).toFloat()
        val u0 = (code * 64 + 1).toFloat()
        for (k in 0..n) {
            val tangent = NeuronGeometry.unit(points[min(k + 1, n)] - points[max(k - 1, 0)], dir)
            val p = points[k]
            val side = NeuronGeometry.unit(tangent.cross(eye - p), NeuronGeometry.perpendicular(tangent))
            val width = halfWidth(p, endA, endB, h)
            val v = first + k * 2
            val p0 = p - side * width
            val p1 = p + side * width
            pos[v * 3] = p0.x; pos[v * 3 + 1] = p0.y; pos[v * 3 + 2] = p0.z
            pos[v * 3 + 3] = p1.x; pos[v * 3 + 4] = p1.y; pos[v * 3 + 5] = p1.z
            val u = u0 + min(lengths[k] * scale, ALONG_CAP)
            uv[v * 2] = u; uv[v * 2 + 1] = band + 0.002f
            uv[v * 2 + 2] = u; uv[v * 2 + 3] = band + 0.998f
        }
    }
}

/** The fluid behind (iOS NeuronTissue): the dome's soft glows, and the bokeh and far cells' quads. */
object NeuronTissue {
    data class Glow(val direction: Vec3, val reach: Float, val hue: Vec3)

    fun glows(): List<Glow> {
        val random = UniverseRandom(0x7155uL)
        val hues = listOf(Vec3(0.02f, 0.05f, 0.08f), Vec3(0.015f, 0.06f, 0.07f), Vec3(0.035f, 0.03f, 0.08f),
            Vec3(0.015f, 0.045f, 0.08f))
        return (0 until 16).map { k ->
            val d = GraphUniverse.float3(ThemeLayout.fibonacci(k, 16))
            val jitter = Vec3(random.signed().toFloat(), random.signed().toFloat(), random.signed().toFloat())
            val dir = (d + jitter * 0.3f).normalized()
            Glow(dir, 0.35f + 0.45f * random.unit().toFloat(), hues[k % hues.size])
        }
    }

    /** The dome, 64 by 32 on the unit sphere: positions and colours (sRGB) per vertex, triangles. */
    fun dome(columns: Int = 64, rows: Int = 32): NeuronMesh {
        val lights = glows()
        val positions = FloatList(); val colours = FloatList(); val indices = IntList()
        for (row in 0..rows) {
            val lat = FPI * (row.toFloat() / rows - 0.5f)
            for (column in 0..columns) {
                val lon = 2 * FPI * column / columns
                val flat = cos(lat)
                val dir = Vec3(flat * cos(lon), sin(lat), flat * sin(lon))
                positions.add(dir.x); positions.add(dir.y); positions.add(dir.z)
                var c = NeuronPalette.deep
                for (g in lights) {
                    val gap = (dir - g.direction).length() / g.reach
                    c += g.hue * exp(-gap * gap)
                }
                colours.add(c.x); colours.add(c.y); colours.add(c.z)
            }
        }
        val stride = columns + 1
        for (row in 0 until rows) for (column in 0 until columns) {
            val a = row * stride + column; val b = a + 1
            val c = (row + 1) * stride + column; val d = c + 1
            indices.add(a); indices.add(c); indices.add(b); indices.add(b); indices.add(c); indices.add(d)
        }
        return NeuronMesh(positions.toArray(), colours.toArray(), FloatArray(0), indices.toArray())
    }

    data class SkyQuad(val direction: Vec3, val size: Float, val turn: Float, val tint: Vec3)

    val bokehQuads: List<SkyQuad> get() = NeuronBokeh.discs().map { SkyQuad(it.direction, it.size, 0f, it.colour * (0.32f * it.strength)) }
    val farQuads: List<SkyQuad> get() = NeuronBokeh.farCells().map { SkyQuad(it.direction, it.size, it.turn, it.colour * (0.42f * it.strength)) }

    /** Quads facing the middle at [radius]: positions in points, (u, v) in normals' place, tints in coords' (rgb). */
    fun quads(list: List<SkyQuad>, radius: Float): NeuronMesh {
        val positions = FloatList(); val uv = FloatList(); val tints = FloatList(); val indices = IntList()
        var count = 0
        for (q in list) {
            val dir = q.direction
            val a = NeuronGeometry.perpendicular(dir)
            val b = dir.cross(a)
            val u = a * cos(q.turn) + b * sin(q.turn)
            val v = dir.cross(u)
            val first = count
            for ((x, y) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                val p = dir * radius + u * (x * q.size) + v * (y * q.size)
                positions.add(p.x); positions.add(p.y); positions.add(p.z)
                uv.add((x + 1) / 2); uv.add((y + 1) / 2)
                tints.add(q.tint.x); tints.add(q.tint.y); tints.add(q.tint.z)
                count++
            }
            indices.add(first); indices.add(first + 1); indices.add(first + 2)
            indices.add(first); indices.add(first + 2); indices.add(first + 3)
        }
        return NeuronMesh(positions.toArray(), uv.toArray(), tints.toArray(), indices.toArray())
    }
}

/** The Neurons' depth band (iOS GraphDepthBand.neuronsHigh, neuronsSmooth), as Space's SpaceDepthBand. */
object NeuronDepth {
    /** a and b, as shares of the framing's reach. */
    fun shares(tier: GraphicsTier): Pair<Float, Float> = when (tier) {
        GraphicsTier.HIGH -> 0.20f to 1.05f
        GraphicsTier.SMOOTH -> 0.20f to 0.90f
    }

    /** k: a straight ramp. */
    const val EXPONENT = 1f

    fun band(focusDepth: Float, reach: Float, tier: GraphicsTier, contrast: Boolean = false): SpaceDepthBand {
        if (focusDepth <= 0f) return SpaceDepthBand.OFF
        val d = max(if (focusDepth.isFinite()) focusDepth else SpaceDepth.NEAREST_FOCUS, SpaceDepth.NEAREST_FOCUS)
        val r = if (reach.isFinite()) reach.coerceIn(SpaceDepth.NEAREST_REACH, SpaceDepth.FURTHEST_REACH) else SpaceDepth.FURTHEST_REACH
        val (a0, b0) = shares(tier)
        val later = if (contrast) SpaceDepth.CONTRAST_LATER else 1f
        val start = d * (1f + a0 * later * r)
        val end = start + d * max((b0 - a0) * later, 0.00001f) * r
        return SpaceDepthBand(start, end, EXPONENT, if (contrast) SpaceDepth.CONTRAST_CAP else 1f)
    }
}

/**
 * Where links leave a cell's membrane, for its shader's rpMouth0...3 (iOS GraphLinkBridge.Mouth,
 * mouthWidth, open and mouths, GraphLinkBridge.swift lines 160-208; GraphLinkMouths, lines
 * 215-248; NeuronMouths.add, GraphNeuronLook.swift lines 999-1020). A mouth is the unit
 * direction along the link from the cell's centre times how open it is, and its half width
 * over the membrane's radius. Each cell keeps its four most open as seen, the most open first.
 */
class NeuronMouths(private val bridge: NeuronBridge = NeuronBridge()) {
    data class Mouth(val dir: Vec3, val width: Float)

    private val kept = HashMap<Int, MutableList<Pair<Mouth, Float>>>()

    fun clear() = kept.clear()

    /** A mouth's half width over membrane radius [r], for a tube of half width [h]. */
    fun width(r: Float, h: Float): Float = if (r > 0.0001f) h / r + bridge.blend else 0f

    /** A link's two mouths, from the cell at [pa] (trim radius [ra]) to the one at [pb]; null for one place. */
    fun pair(pa: Vec3, pb: Vec3, ra: Float, rb: Float, h: Float, grow: Float): Pair<Mouth, Mouth>? {
        val delta = pb - pa
        val length = delta.length()
        if (!(length > 0.0001f)) return null
        val dir = delta * (1f / length)
        val (first, second) = open(grow)
        return Mouth(dir * first, width(ra * bridge.membrane, h)) to Mouth(dir * (-second), width(rb * bridge.membrane, h))
    }

    /** Adds [mouth] to cell [cell], centred at [centre], as seen from [eye]. */
    fun add(mouth: Mouth, cell: Int, centre: Vec3, eye: Vec3) {
        val d = mouth.dir
        val open = d.length()
        if (!(open > 0.001f)) return
        val toward = eye - centre
        val far = toward.length()
        val view = if (far > 0.000001f) toward * (1f / far) else Vec3(0f, 0f, 1f)
        val rank = open * NeuronBridge.gate(NeuronBridge.sine(d * (1f / open), view))
        if (!(rank > 0.001f)) return
        val list = kept.getOrPut(cell) { ArrayList() }
        if (list.size >= COUNT) {
            if (!(rank > list.last().second)) return
            list.removeAt(list.size - 1)
        }
        val at = list.indexOfFirst { it.second < rank }.let { if (it < 0) list.size else it }
        list.add(at, mouth to rank)
    }

    /** The cell's mouths, the most open first. */
    fun mouths(cell: Int): List<Mouth> = kept[cell]?.map { it.first } ?: emptyList()

    /** A link's mouths at the ends that [hasCell] (a membrane of their own), its tube [half] before the width step. */
    fun addLink(a: Int, b: Int, pa: Vec3, pb: Vec3, ra: Float, rb: Float, half: Float, grow: Float, eye: Vec3,
                hasCell: (Int) -> Boolean) {
        val first = hasCell(a)
        val second = hasCell(b)
        if (!first && !second) return
        val h = half * NeuronBridge.stepWidth(NeuronBridge.widthStep(min(ra, rb)))
        val pair = pair(pa, pb, ra, rb, h, grow) ?: return
        if (first) add(pair.first, a, pa, eye)
        if (second) add(pair.second, b, pb, eye)
    }

    companion object {
        const val COUNT = 4

        /** How open a link's two mouths are as it grows: the first over its first 5 percent, the second over the last 3. */
        fun open(grow: Float): Pair<Float, Float> = ease(grow / 0.05f) to ease((grow - 0.97f) / 0.03f)

        private fun ease(x: Float): Float { val t = min(max(x, 0f), 1f); return t * t * (3 - 2 * t) }
    }
}

/** A unit sphere (SCNSphere(radius: 1), [segments] round): positions, normals, (u, v), triangles. */
object NeuronSphere {
    fun mesh(segments: Int): NeuronMesh {
        val columns = max(segments, 3)
        val rows = max(segments / 2, 2)
        val points = FloatList(); val normals = FloatList(); val coords = FloatList(); val indices = IntList()
        for (row in 0..rows) {
            val lat = FPI * (0.5f - row.toFloat() / rows)
            for (column in 0..columns) {
                val lon = 2 * FPI * column / columns
                val x = cos(lat) * sin(lon); val y = sin(lat); val z = cos(lat) * cos(lon)
                points.add(x); points.add(y); points.add(z)
                normals.add(x); normals.add(y); normals.add(z)
                coords.add(column.toFloat() / columns); coords.add(row.toFloat() / rows)
            }
        }
        val stride = columns + 1
        for (row in 0 until rows) for (column in 0 until columns) {
            val a = row * stride + column; val b = a + 1
            val c = a + stride; val d = c + 1
            indices.add(a); indices.add(c); indices.add(b); indices.add(b); indices.add(c); indices.add(d)
        }
        return NeuronMesh(points.toArray(), normals.toArray(), coords.toArray(), indices.toArray())
    }
}

/**
 * Everything the Neurons map draws, worked out off the GL thread: the plan, each body's look,
 * the membranes of the whole cells (one per variant used) and the arbors of the rest (one per
 * kind and variant), with the sizes iOS builds them at (GraphNeuronLook.swift lines 230-231,
 * 590-598, 642-656).
 */
class NeuronScene(val plan: ThemePlan, val look: NeuronLookPlan, val membranes: Map<Int, NeuronMesh>,
                  val arbors: Map<Pair<NeuronCellKind, Int>, NeuronMesh>, val sphereSegments: Int) {
    val tier: GraphicsTier get() = look.tier

    companion object {
        fun of(input: UniverseInput, cells: NeuronStateChoice = NeuronStateChoice.NATURAL,
               tier: GraphicsTier = GraphicsTier.HIGH): NeuronScene {
            val plan = GraphNeurons.plan(input)
            val look = NeuronLookPlan(plan, cells, tier)
            val rich = tier == GraphicsTier.HIGH
            val membranes = HashMap<Int, NeuronMesh>()
            val arbors = HashMap<Pair<NeuronCellKind, Int>, NeuronMesh>()
            for (body in look.bodies) {
                if (body.whole) {
                    membranes.getOrPut(body.variant) {
                        val m = NeuronMembrane.make(NeuronArbor.branches(NeuronCellKind.CELL, body.variant, rich))
                        NeuronMesh(m.points, m.normals, m.coords, m.indices)
                    }
                } else if (body.arborTurn != null) {
                    arbors.getOrPut(body.kind to body.variant) {
                        val round = if (body.kind == NeuronCellKind.CELL) (if (rich) 10 else 7) else (if (rich) 6 else 4)
                        NeuronArbor.mesh(NeuronArbor.branches(body.kind, body.variant, rich), round, if (rich) 14 else 9)
                    }
                }
            }
            return NeuronScene(plan, look, membranes, arbors, if (rich) 40 else 24)
        }
    }
}
