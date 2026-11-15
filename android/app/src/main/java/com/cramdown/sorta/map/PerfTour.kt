package com.cramdown.sorta.map

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// Performance's proof tour (S05i2), a port of iOS's GraphPerfTour (ios/Sorta/Maps/GraphPerfTour.swift)
// over Android's two levels. Under its launch extra only, the map flies this path by itself, the same
// on every run, and counts its frames:
//
//   warm      3 s      the map starts turning, eased in; not measured
//   measure   10 s     a steady turn and two zooms, in to about the map's edge and out again, measured;
//                      then 1 s easing to rest, one whole turn from where it began
//   front     8 s      still: the whole map from the usual angle
//   turn      1.2 s    half a turn, eased
//   back      8 s      still: the far side now in front
//   near      13 s     3 s round to the front and in to the hub, then still
//   chosen    8 s      still, the hub chosen
//   far       8 s      a cut to the whole map (the one jump), still
//   compare   2 x 4 s  high, then smooth: 1 s still while the switch settles, then 3 s of a slow eased
//                      turn, measured; the two turn over the same arc, one each way
//
// 68.2 s in all. iOS compares three levels, each with its depth fade off and on (84.2 s); Android has
// two levels and Performance has no depth fade, so each level runs once. A pose is a function of the
// time alone. Never shown to a person: its lines go to logcat and MapDrawn only. Pure Kotlin.

/** What the tour knows of the map (iOS GraphPerfTourMap): its ball, the view's size (pixels), the whole map's pose. */
data class PerfTourMap(
    val centre: Vec3,
    val radius: Float,
    val width: Float,
    val height: Float,
    val whole: MapPose,
    val fovY: Float,
    /** A well-linked body near the front, to fly to and choose; null: the centre, nothing chosen. */
    val hub: Int? = null,
    val hubPosition: Vec3? = null,
)

/** What the tour asks of one frame (iOS GraphPerfTourStep): the pose, the phase, a level to force, a body to choose, whether it counts. */
data class PerfTourStep(
    val pose: MapPose,
    val phase: String,
    val tier: GraphicsTier? = null,
    val select: Int? = null,
    val measuring: Boolean = false,
)

/** One drawn frame's numbers (iOS GraphPerfFrameReport, what Android measures): seconds since the frame before, and the frame's own work. */
data class PerfFrameReport(val interval: Double, val work: Double)

class PerfTour(val map: PerfTourMap) {
    /** One stretch of the tour: its name, length and measured part (seconds into it; null for none). */
    data class Phase(val name: String, val duration: Double, val measuredFrom: Double?, val measuredTo: Double?) {
        fun measures(u: Double): Boolean = measuredFrom != null && measuredTo != null && u >= measuredFrom && u < measuredTo
    }

    val whole: MapPose = map.whole.copy(distance = max(if (map.whole.distance.isFinite()) map.whole.distance else 1f, 1e-3f))
    /** The hub's framing, from the front: a ball [NEAR_SHARE] of the map's radius across. */
    val near: MapPose
    /** How deep the measure's zooms go: the log of the whole view's distance over the nearest. */
    val zoomDepth: Double

    private val tallies = Array(SCHEDULE.size) { Tally() }
    private var current = -1
    private var finished = false
    private var frames = 0
    private var seconds = 0.0

    init {
        val ball = max(map.radius * NEAR_SHARE, 1e-3f)
        val fit = camera(whole).framing(map.hubPosition ?: map.centre, ball, map.width, map.height, fill = 0.9f)
        near = whole.copy(target = fit.target, distance = fit.distance)
        val nearest = min(max(map.radius, 1e-3f), whole.distance * 0.8f)
        zoomDepth = ln((whole.distance / nearest).toDouble())
    }

    private fun camera(p: MapPose) = OrbitCamera(p.target, p.distance, p.yaw, p.pitch, map.fovY, p.roll)

    /** The step [seconds] into the tour; null when it is done. */
    @Synchronized
    fun step(seconds: Double): PerfTourStep? {
        val (k, u) = locate(if (seconds.isNaN()) 0.0 else seconds) ?: run { finished = true; return null }
        val phase = SCHEDULE[k]
        val run = k - COMPARE
        return PerfTourStep(pose(k, u), phase.name,
            tier = if (run >= 0) LEVELS[min(run, LEVELS.size - 1)] else null,
            select = if (k == CHOSEN) map.hub else null,
            measuring = phase.measures(u))
    }

    /** The pose [u] seconds into phase [k]. */
    private fun pose(k: Int, u: Double): MapPose {
        val start = STARTS[k]
        val yaw0 = whole.yaw.toDouble()
        val turned = openingTurn(WARM + MEASURE + RAMP_OUT)
        fun yaw(angle: Double): Float = wrap((yaw0 + angle).toFloat())
        return when {
            k < TURN -> {
                val c = whole.copy(yaw = yaw(openingTurn(start + u)))
                if (SCHEDULE[k].name == "measure") c.copy(distance = whole.distance * exp(zoom(u, zoomDepth)).toFloat()) else c
            }
            k == TURN -> whole.copy(yaw = yaw(turned + PI * ease(u / TURN_TIME)))
            k == BACK -> whole.copy(yaw = yaw(turned + PI))
            k == NEAR -> {
                // round to the front over the first half, then in: the target reaches the hub before the zoom is close
                val v = min(u / FLY, 1.0)
                val along = ease((v - 0.2) / 0.6).toFloat()
                val closer = ease((v - 0.3) / 0.7).toFloat()
                MapPose(whole.target + (near.target - whole.target) * along,
                    exp(ln(whole.distance) * (1 - closer) + ln(max(near.distance, 1e-6f)) * closer),
                    yaw(turned + PI + PI * ease(v / 0.5)),
                    whole.pitch + (near.pitch - whole.pitch) * closer, whole.roll)
            }
            k == CHOSEN -> near.copy(yaw = yaw(turned + 2 * PI))
            k == FAR -> whole
            else -> {
                // compare: the run's slow turn, forth on the first and back on the second
                val run = k - COMPARE
                val e = ease((u - SETTLE) / SWEEP_TIME)
                whole.copy(yaw = yaw(SWEEP * if (run % 2 == 0) e else 1 - e))
            }
        }
    }

    /**
     * Counts one drawn frame towards its step's phase; gives the lines of the phases it ends (a phase is
     * over once a later one draws), for logcat and MapDrawn.
     */
    @Synchronized
    fun record(report: PerfFrameReport, step: PerfTourStep): List<String> {
        val k = INDEX[step.phase] ?: return emptyList()
        val ended = ArrayList<String>()
        if (k > current) {
            for (j in max(current, 0) until k) if (tallies[j].frames > 0) ended.add(line(j))
            current = k
        }
        tallies[k].add(report, step.measuring)
        frames++
        if (report.interval > 0 && report.interval.isFinite()) seconds += report.interval
        return ended
    }

    /** The last phase's line and the whole tour's, once [step] has said it is done; empty before, and after the first call. */
    @Synchronized
    fun finish(): List<String> {
        if (!finished || current < 0 || current >= SCHEDULE.size) return emptyList()
        val out = ArrayList<String>()
        if (tallies[current].frames > 0) out.add(line(current))
        out.add("done: " + tenths(seconds) + " s, " + grouped(frames) + " frames")
        current = SCHEDULE.size
        return out
    }

    /** A phase's line, from its frames. */
    private fun line(k: Int): String {
        val tally = tallies[k]
        val parts = ArrayList<String>()
        if (k == NEAR || k == CHOSEN) parts.add(map.hub?.let { "hub $it" } ?: "no hub, the centre")
        if (tally.measured.isEmpty()) {
            parts.add(grouped(tally.frames) + " frames in " + tenths(tally.seconds) + " s, not measured")
        } else {
            val (fps, p5, low) = rates(tally.measured.map { it.interval })
            val work = tally.measured.map { it.work }.filter { it.isFinite() && it >= 0 }.sorted()
            parts.add(tenths(fps) + " fps (p5 " + tenths(p5) + ", 1% low " + tenths(low) + ")")
            parts.add("work " + millis(median(work)) + " (p95 " + millis(percentile(work, 0.95)) + ") ms")
            val counted = tally.measured.size
            parts.add(grouped(counted) + (if (counted == tally.frames) "" else " of " + grouped(tally.frames)) + " frames")
        }
        return SCHEDULE[k].name + ": " + parts.joinToString(" · ")
    }

    private class Tally {
        var frames = 0
        var seconds = 0.0
        val measured = ArrayList<PerfFrameReport>()
        fun add(report: PerfFrameReport, measuring: Boolean) {
            frames++
            if (report.interval > 0 && report.interval.isFinite()) seconds += report.interval
            if (measuring) measured.add(report)
        }
    }

    companion object {
        const val WARM = 3.0
        /** The opening turn's speed eases in over this much of the warm-up. */
        const val RAMP_IN = 1.5
        const val MEASURE = 10.0
        /** After the measure, the turn eases to rest over this long. */
        const val RAMP_OUT = 1.0
        const val ZOOM_CYCLES = 2.0
        const val STILL = 8.0
        const val TURN_TIME = 1.2
        /** The flight round to the front and in to the hub. */
        const val FLY = 3.0
        const val NEAR_STILL = 10.0
        /** Each comparison: still while the switch settles, then a slow turn of [SWEEP] radians. */
        const val SETTLE = 1.0
        const val SWEEP_TIME = 3.0
        const val SWEEP = PI / 4
        /** The hub is framed in a ball this share of the map's radius across (GraphPerfTour.swift:122). */
        const val NEAR_SHARE = 0.0075f
        /** Android's levels, each once (Performance has no depth fade to compare). */
        val LEVELS = listOf(GraphicsTier.HIGH, GraphicsTier.SMOOTH)

        fun compareName(tier: GraphicsTier): String = "compare " + tier.name.lowercase()

        /**
         * The tour's hub (iOS frontHub, GraphPerfMapView.swift:310-325): the most linked of [count] bodies
         * seen from [camera] on a [width] x [height] view in front of it, nearer than its target and within
         * the middle 60% each way; the first such on a tie; null when none is.
         */
        fun frontHub(count: Int, position: (Int) -> Vec3, degree: (Int) -> Int, camera: OrbitCamera, width: Float, height: Float): Int? {
            val eye = camera.eye(); val forward = camera.forward(); val right = camera.right(); val up = camera.up()
            val focal = camera.focalPixels(height)
            var best = -1; var bestDegree = 0
            for (i in 0 until count) {
                val d = position(i) - eye
                val depth = d.dot(forward)
                if (depth <= 0f || depth >= camera.distance) continue
                val x = d.dot(right) / depth * focal
                val y = d.dot(up) / depth * focal
                if (kotlin.math.abs(x) >= width * 0.3f || kotlin.math.abs(y) >= height * 0.3f) continue
                val links = degree(i)
                if (best < 0 || links > bestDegree) { best = i; bestDegree = links }
            }
            return best.takeIf { it >= 0 }
        }

        val SCHEDULE: List<Phase> = listOf(
            Phase("warm", WARM, null, null),
            Phase("measure", MEASURE + RAMP_OUT, 0.0, MEASURE),
            Phase("front", STILL, 0.0, STILL),
            Phase("turn", TURN_TIME, 0.0, TURN_TIME),
            Phase("back", STILL, 0.0, STILL),
            Phase("near", FLY + NEAR_STILL, FLY, FLY + NEAR_STILL),
            Phase("chosen", STILL, 0.0, STILL),
            Phase("far", STILL, 0.0, STILL),
        ) + LEVELS.map { Phase(compareName(it), SETTLE + SWEEP_TIME, SETTLE, SETTLE + SWEEP_TIME) }

        /** When each phase starts (seconds into the tour). */
        val STARTS: DoubleArray = DoubleArray(SCHEDULE.size).also { out ->
            var at = 0.0
            for ((k, p) in SCHEDULE.withIndex()) { out[k] = at; at += p.duration }
        }

        /** The whole tour (seconds). */
        val DURATION: Double = SCHEDULE.fold(0.0) { a, p -> a + p.duration }

        private val INDEX: Map<String, Int> = SCHEDULE.withIndex().associate { it.value.name to it.index }
        private const val TURN = 3
        private const val BACK = 4
        private const val NEAR = 5
        private const val CHOSEN = 6
        private const val FAR = 7
        private const val COMPARE = 8

        /** The phase at [t] seconds into the tour and how far into it; null once over. */
        fun locate(t: Double): Pair<Int, Double>? {
            if (!(t < DURATION)) return null
            val time = max(t, 0.0)
            var k = SCHEDULE.size - 1
            while (k > 0 && STARTS[k] > time) k--
            return k to time - STARTS[k]
        }

        /** The opening turn's steady speed (rad/s): eased in, steady and eased out, it comes to one whole turn. */
        val OPENING_SPEED: Double = 2 * PI / (RAMP_IN / 2 + (WARM + MEASURE - RAMP_IN) + RAMP_OUT / 2)

        /** Smoothstep: 0 below 0, 1 above 1, flat at both ends. */
        fun ease(x: Double): Double { val t = x.coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

        /** How far a speed eased in from rest over [ramp] seconds has gone [t] seconds in (1 after the ramp). */
        fun easedIn(t: Double, ramp: Double): Double {
            if (t <= 0) return 0.0
            if (t >= ramp) return ramp / 2 + (t - ramp)
            val x = t / ramp
            return ramp * (x * x * x - x * x * x * x / 2)
        }

        /** How far a speed of 1 eased to rest over [ramp] seconds has gone [t] seconds in. */
        fun easedOut(t: Double, ramp: Double): Double {
            if (t <= 0) return 0.0
            if (t >= ramp) return ramp / 2
            val x = t / ramp
            return t - ramp * (x * x * x - x * x * x * x / 2)
        }

        /** The opening turn [t] seconds into the tour (radians, 0 to one whole turn). */
        fun openingTurn(t: Double): Double {
            val steady = WARM + MEASURE
            if (t <= steady) return OPENING_SPEED * easedIn(t, RAMP_IN)
            return OPENING_SPEED * (easedIn(steady, RAMP_IN) + easedOut(t - steady, RAMP_OUT))
        }

        /** The measure's zoom [u] seconds in: the log of the distance's share of the whole view's. */
        fun zoom(u: Double, depth: Double): Double {
            if (!(u > 0 && u < MEASURE)) return 0.0
            return -depth * (1 - cos(2 * PI * ZOOM_CYCLES * u / MEASURE)) / 2
        }

        /** An angle in −π…π. */
        fun wrap(a: Float): Float {
            val tau = (2 * PI).toFloat()
            var x = a % tau
            if (x > PI.toFloat()) x -= tau
            if (x < -PI.toFloat()) x += tau
            return x
        }

        /** The frame rate at the median interval, at the 95th percentile's and at the 99th's (seconds; non-positive left out). */
        fun rates(intervals: List<Double>): Triple<Double, Double, Double> {
            val sorted = intervals.filter { it > 0 && it.isFinite() }.sorted()
            if (sorted.isEmpty()) return Triple(0.0, 0.0, 0.0)
            return Triple(1 / median(sorted), 1 / percentile(sorted, 0.95), 1 / percentile(sorted, 0.99))
        }

        /** The middle of sorted values, or the mean of the two middle ones (0 for none). */
        fun median(sorted: List<Double>): Double {
            if (sorted.isEmpty()) return 0.0
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        }

        /** Nearest rank: the smallest of sorted values with at least [q] of them at or under it (0 for none). */
        fun percentile(sorted: List<Double>, q: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val rank = ceil(q * sorted.size - 1e-9).toInt()
            return sorted[rank.coerceIn(1, sorted.size) - 1]
        }

        /** To one decimal, a dot in every locale: "59.9". */
        fun tenths(x: Double): String {
            val t = ((if (x.isFinite()) max(x, 0.0) else 0.0) * 10 + 0.5).toLong()
            return (t / 10).toString() + "." + (t % 10).toString()
        }

        fun millis(seconds: Double): String = tenths(seconds * 1_000)

        /** Thousands with commas: "412,331". */
        fun grouped(n: Int): String {
            val digits = kotlin.math.abs(n.toLong()).toString()
            val out = StringBuilder()
            for ((k, ch) in digits.withIndex()) {
                if (k > 0 && (digits.length - k) % 3 == 0) out.append(',')
                out.append(ch)
            }
            return (if (n < 0) "-" else "") + out
        }
    }
}
