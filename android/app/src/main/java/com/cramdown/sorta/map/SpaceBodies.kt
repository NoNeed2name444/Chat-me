package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Which notes the Space theme draws as bodies this frame, and what each body's shader needs
 * (SpaceShaders). It works on the octree's own arrays, in chunk order, where every node's
 * colour group is its style code (MapScene.of for the Space theme).
 *
 * Only chunks fully open into their nodes are looked at, and of those only the ones whose
 * nearest note could be [SpaceLod.BODY_PIXELS] across, so the million-node path pays nothing
 * for it. A note becomes its body once it covers more than that many pixels (SpaceLod), nearest
 * first, up to the frame budget's cap; the rest stay points. The bodies' links become glowing
 * beams, which SpaceGl draws as strips along their curves (LinkStrips), grown as far as [growth]
 * says (none: whole).
 *
 * The depth fade (SpaceDepth, iOS GraphSpaceDepthStage): a note past the band's end is gone
 * (neither a body nor animated), and the change between point and body has SpaceDepth.level's
 * gap, so a body hovering at the edge does not flicker: once a body, it stays one until it
 * drops under two thirds of that size; where it has mostly faded (f 0.6) both edges double.
 *
 * Per body ([STRIDE] floats, the body shader's four vec4 attributes): place and note radius;
 * light direction and seed; A (a direction and a number) and B (a direction and the note's
 * size in pixels). By style, A and B hold:
 * - black hole: A.xy the disk's plane's x as seen (screen right, up), A.w its code (SpaceOptics.holeCode);
 * - sun: A.w its corona's code (SpaceOptics.coronaCode);
 * - rocky planet: A its moon's offset in note radii;
 * - gas giant: A its spin axis (the ring lies square to it);
 * - pulsar: light.x its nebula's turn, A its beam, A.w 2 + how edge-on its nebula is, B its spin axis;
 * - comet: A its ion tail, B its dust tail (SpaceOptics.cometTails).
 * A fifth vec4 (S05g), the mark: x its fade under Show links (1, or [MapMark.FADED] outside
 * [focus]), y 1 for the marked body; z and w spare.
 *
 * The marked body, the one held ([grabbed], S05g2) or else the chosen one (S05g): its links' beams
 * are lit (beam float 9, as iOS's lit bit) and its radius is scaled by [chosenScale] (the pop).
 * Under Show links ([focus] set) only the chosen body's own beams are built, the links gone since
 * the last scene shrinking away, unlit, as before. A held body and what it pulls are read at their
 * homes plus their own offsets ([drag]).
 */
class SpaceBodies(val tree: Octree, val nodeRadius: Float, val capacity: Int = 96) {
    companion object {
        const val STRIDE = 20
        /**
         * Per beam: the A (sending) end trimmed (x, y, z) and its style, the B end trimmed and its
         * style, seed (0...15), lit, how far it has grown (eased, 0...1), then what bends it near
         * each end (iOS GraphRibbonWriter.path): the A body's centre and axis, the B body's centre and
         * axis (a black hole's disk axis, a gas giant's spin axis; zero for the other styles).
         */
        const val BEAM_STRIDE = 23
        /** The light when there is no sun, a world direction towards it (iOS rpKey). */
        val KEY: Vec3 = Vec3(-0.4f, 0.5f, 0.8f).normalized()
        /** A chunk counts as open into its nodes from this openness. */
        const val OPEN = 0.999f
        /** More suns than this and only the drawn ones light the others (the 1M maps). */
        const val MAX_LIGHTS = 4096
        /** A body stays one down to this share of [SpaceLod.BODY_PIXELS] (SpaceDepth's gap, 1.5 / 2.25). */
        const val KEEP = SpaceDepth.POINT_ENTER / SpaceDepth.POINT_LEAVE
    }

    val data = FloatArray(capacity * STRIDE)
    var count = 0; private set
    /** Bodies of style code s are styleStart[s] until styleStart[s + 1], far to near. */
    val styleStart = IntArray(7)
    /** The chunk-order node of each body, in the same order. */
    val nodes = IntArray(capacity)
    val beams = FloatArray(SpaceLod.MAX_BEAMS * BEAM_STRIDE)
    var beamCount = 0; private set

    /** Nodes whose point must hide (+node + 1) or show again (-(node + 1)) this frame. */
    val maskChanges = IntList(64)

    /** Styles whose shader could not be made on this device stay points. */
    val enabled = BooleanArray(6) { true }

    /** How far each link has grown (S05e); null draws every link whole. */
    var growth: SpaceLinkGrowth? = null

    /** The chosen body, a chunk-order node, or −1 (S05g). */
    var chosen: Int = -1
    /** The body held (S05g2), a chunk-order node, or −1: marked, popped and its beams lit in the chosen body's place while held. */
    var grabbed: Int = -1
    /** The spring run of a held body (S05g2), by chunk-order node: each body is read at its home plus its own offset; null keeps every body home. */
    var drag: MapDrag? = null
    /** The body marked: the one held, else the chosen one (focus = grabbed ?? selected, GraphMotion.swift:1840). */
    private val marked get() = if (grabbed >= 0) grabbed else chosen

    /** Node [i]'s place along [axis]: its home (Octree.positions) plus its own offset from a drag. */
    private fun at(i: Int, axis: Int): Float = tree.positions[3 * i + axis] + (drag?.offset(i, axis) ?: 0f)
    /** Its radius's scale this frame (MapPop.scale). */
    var chosenScale: Float = 1f
    /** Under Show links, the chosen body's neighbourhood in chunk order, sorted; null when every link shows. */
    var focus: IntArray? = null
    /** Bodies drawn faded this frame (MapDrawn.faded), and beams drawn lit (MapDrawn.lit). */
    var fadedCount = 0; private set
    var litCount = 0; private set

    private val slot = IntArray(tree.nodeCount) { -1 }
    private val masked = BooleanArray(tree.nodeCount)
    private val previous = IntArray(capacity)
    private var previousCount = 0
    private val sunNodes: IntArray = run {
        val suns = IntList(16)
        for (i in 0 until tree.nodeCount) if (tree.groups[i].toInt() == SpaceStyle.SUN.code) suns.add(i)
        if (suns.size > MAX_LIGHTS) IntArray(0) else suns.toArray()
    }
    private val heapNode = IntArray(capacity)
    private val heapDepth = FloatArray(capacity)
    private var heapSize = 0
    private val seen = HashSet<Long>()
    private val leafSeen = HashSet<Int>()

    /**
     * Chooses this frame's bodies and beams. [viewProj] is the frame's matrix, [right] and [up]
     * the camera's axes in the world, [time] the shaders' clock (rpClock * rpMotion).
     */
    fun update(plan: FramePlan, viewProj: FloatArray, eye: Vec3, right: Vec3, up: Vec3, focal: Float, maxBodies: Int, time: Float,
               band: SpaceDepthBand = SpaceDepthBand.OFF): Int {
        val frustum = Frustum(viewProj)
        val limit = min(maxBodies, capacity)
        heapSize = 0
        for (k in 0 until plan.detailLeaves.size) {
            val leaf = plan.detailLeaves[k]
            if (plan.leafState[leaf * 4 + 3] < OPEN) continue
            growth?.leafOpen(leaf)
            val cell = tree.leafCell[leaf]
            val dx = tree.cx[cell] - eye.x; val dy = tree.cy[cell] - eye.y; val dz = tree.cz[cell] - eye.z
            val nearest = sqrt(dx * dx + dy * dy + dz * dz) - tree.radius[cell]
            if (nearest > 0f && SpaceLod.pixels(nodeRadius, focal, nearest) < SpaceLod.BODY_PIXELS * KEEP) continue
            for (i in tree.start[cell] until tree.end[cell]) {
                val style = tree.groups[i].toInt()
                if (style !in 0..5 || !enabled[style]) continue
                val x = at(i, 0); val y = at(i, 1); val z = at(i, 2)
                val w = viewProj[3] * x + viewProj[7] * y + viewProj[11] * z + viewProj[15]
                if (w <= 1e-3f) continue
                if (band.gone(w)) continue
                if (level(masked[i], SpaceLod.pixels(nodeRadius, focal, w), band.fade(w)) != SpaceBodyLevel.BODY) continue
                val reach = max(SpaceDepth.reachOfLook(style), SpaceStyle.of(style).extent)
                if (!frustum.intersectsSphere(x, y, z, reach * nodeRadius)) continue
                offer(i, w, limit)
            }
        }
        // the chosen, by style, far to near (each style is drawn far to near over what is behind)
        val order = (0 until heapSize).sortedWith(compareBy<Int>({ tree.groups[heapNode[it]].toInt() }, { -heapDepth[it] }))
        for (k in 0 until previousCount) slot[previous[k]] = -1
        count = order.size
        styleStart.fill(0)
        fadedCount = 0
        for ((j, h) in order.withIndex()) {
            val node = heapNode[h]
            nodes[j] = node
            slot[node] = j
            styleStart[tree.groups[node] + 1]++
            fill(j, node, heapDepth[h], eye, right, up, focal, time)
        }
        for (s in 1..6) styleStart[s] += styleStart[s - 1]
        maskChanges.clear()
        for (k in 0 until previousCount) {
            val node = previous[k]
            if (slot[node] < 0 && masked[node]) { masked[node] = false; maskChanges.add(-(node + 1)) }
        }
        for (j in 0 until count) {
            val node = nodes[j]
            if (!masked[node]) { masked[node] = true; maskChanges.add(node + 1) }
        }
        System.arraycopy(nodes, 0, previous, 0, count)
        previousCount = count
        buildBeams(plan)
        return count
    }

    /**
     * Body or point for a note [pixels] across, given whether it was a body last frame:
     * SpaceDepth.level with its sizes (radii in points on iOS) scaled so a point becomes a body
     * just past [SpaceLod.BODY_PIXELS] across.
     */
    fun level(wasBody: Boolean, pixels: Float, fade: Float): SpaceBodyLevel =
        SpaceDepth.level(if (wasBody) SpaceBodyLevel.BODY else SpaceBodyLevel.POINT,
            pixelRadius = pixels * SpaceDepth.POINT_LEAVE / SpaceLod.BODY_PIXELS, fade = fade, gone = false, inView = true)

    /** True when [node] (chunk order) is drawn as its body this frame. */
    fun isBody(node: Int) = node in slot.indices && slot[node] >= 0

    // ---- choosing: a max-heap on depth keeps the nearest `limit` ----

    private fun offer(node: Int, depth: Float, limit: Int) {
        if (limit <= 0) return
        if (heapSize < limit) {
            var i = heapSize++
            heapNode[i] = node; heapDepth[i] = depth
            while (i > 0) {
                val parent = (i - 1) / 2
                if (heapDepth[parent] >= heapDepth[i]) break
                swap(i, parent); i = parent
            }
        } else if (depth < heapDepth[0]) {
            heapNode[0] = node; heapDepth[0] = depth
            var i = 0
            while (true) {
                val l = 2 * i + 1; val r = l + 1
                var big = i
                if (l < heapSize && heapDepth[l] > heapDepth[big]) big = l
                if (r < heapSize && heapDepth[r] > heapDepth[big]) big = r
                if (big == i) break
                swap(i, big); i = big
            }
        }
    }

    private fun swap(a: Int, b: Int) {
        val n = heapNode[a]; heapNode[a] = heapNode[b]; heapNode[b] = n
        val d = heapDepth[a]; heapDepth[a] = heapDepth[b]; heapDepth[b] = d
    }

    // ---- what each body's shader needs ----

    private fun fill(j: Int, node: Int, depth: Float, eye: Vec3, right: Vec3, up: Vec3, focal: Float, time: Float) {
        val c = Vec3(at(node, 0), at(node, 1), at(node, 2))
        val style = SpaceStyle.of(tree.groups[node].toInt())
        val seed = SpaceOptics.seed(tree.order[node])
        val toEye = SpaceOptics.unit(eye - c, Vec3(0f, 0f, 1f))
        var light = if (style == SpaceStyle.SUN || style == SpaceStyle.BLACK_HOLE) KEY else SpaceOptics.light(c, nearestSuns(c, node), KEY)
        var a = Vec3.ZERO; var codeA = 0f; var b = Vec3.ZERO
        when (style) {
            SpaceStyle.BLACK_HOLE -> {
                val axis = SpaceOptics.tiltedAxis(seed, 0.5f)
                val k = min(toEye.cross(axis).length(), 1f)
                codeA = SpaceOptics.holeCode(k, axis.dot(toEye) >= 0f, 0f)
                val ax = axis.dot(right); val ay = axis.dot(up)
                val l = sqrt(ax * ax + ay * ay)
                // the disk's plane is turned so its axis, as seen, points down its -y
                val yx = if (l > 1e-5f) -ax / l else 0f; val yy = if (l > 1e-5f) -ay / l else 1f
                a = Vec3(yy, -yx, 0f)
            }
            SpaceStyle.SUN -> codeA = SpaceOptics.coronaCode(seed, 0f)
            SpaceStyle.ROCKY -> a = SpaceOptics.moonOffset(time, seed)
            SpaceStyle.GAS_GIANT -> a = SpaceOptics.tiltedAxis(seed, 0.45f)
            SpaceStyle.PULSAR -> {
                val n = SpaceOptics.tiltedAxis(seed, 0.5f)
                val e1 = SpaceOptics.unit(n.cross(Vec3(0f, 0f, 1f)), Vec3(1f, 0f, 0f))
                val e2 = n.cross(e1)
                a = SpaceOptics.beam(n, e1, e2, SpaceOptics.beamPhase(time, seed))
                val (turn, edgeOn) = SpaceOptics.nebula(n, toEye, right, up)
                codeA = 2f + edgeOn
                light = Vec3(turn, 0f, 0f)
                b = n
            }
            SpaceStyle.COMET -> {
                val sun = nearestSuns(c, node).firstOrNull()?.let { Vec3(it[0], it[1], it[2]) }
                val (ion, dust) = SpaceOptics.cometTails(c, Vec3.ZERO, sun, KEY)
                a = ion; b = dust
            }
        }
        val o = j * STRIDE
        val chosenHere = node == marked
        data[o] = c.x; data[o + 1] = c.y; data[o + 2] = c.z; data[o + 3] = if (chosenHere) nodeRadius * chosenScale else nodeRadius
        data[o + 4] = light.x; data[o + 5] = light.y; data[o + 6] = light.z; data[o + 7] = seed
        data[o + 8] = a.x; data[o + 9] = a.y; data[o + 10] = a.z; data[o + 11] = codeA
        data[o + 12] = b.x; data[o + 13] = b.y; data[o + 14] = b.z; data[o + 15] = SpaceLod.pixels(nodeRadius, focal, depth)
        val fade = fadeOf(node)
        if (fade < 1f) fadedCount++
        data[o + 16] = fade; data[o + 17] = if (chosenHere) 1f else 0f; data[o + 18] = 0f; data[o + 19] = 0f
    }

    /** A node's fade under Show links: 1 inside the chosen body's neighbourhood (or with no Show links), else [MapMark.FADED]. */
    fun fadeOf(node: Int): Float {
        val near = focus ?: return 1f
        return if (MapFocus.holdsAny(near, node, node + 1)) 1f else MapMark.FADED
    }

    /** The four suns nearest [c] (not [self]), as SpaceOptics.light takes them. */
    private fun nearestSuns(c: Vec3, self: Int): List<FloatArray> {
        val best = IntArray(4) { -1 }; val dist = FloatArray(4) { Float.MAX_VALUE }
        fun consider(i: Int) {
            if (i == self) return
            val dx = at(i, 0) - c.x; val dy = at(i, 1) - c.y; val dz = at(i, 2) - c.z
            val d = dx * dx + dy * dy + dz * dz
            if (d < 1e-8f || d >= dist[3]) return
            var k = 3
            while (k > 0 && dist[k - 1] > d) { dist[k] = dist[k - 1]; best[k] = best[k - 1]; k-- }
            dist[k] = d; best[k] = i
        }
        if (sunNodes.isNotEmpty()) sunNodes.forEach(::consider)
        else for (k in 0 until heapSize) if (tree.groups[heapNode[k]].toInt() == SpaceStyle.SUN.code) consider(heapNode[k])
        return best.filter { it >= 0 }.map { floatArrayOf(at(it, 0), at(it, 1), at(it, 2), 1f) }
    }

    // ---- beams ----

    private fun placed(plan: FramePlan, node: Int, out: FloatArray) {
        val leaf = tree.leafOfNode[node]
        val s = leaf * 4
        val t = plan.leafState[s + 3].coerceIn(0f, 1f)
        for (a in 0 until 3) out[a] = plan.leafState[s + a] + (at(node, a) - plan.leafState[s + a]) * t
    }

    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val ends = FloatArray(6)

    private fun buildBeams(plan: FramePlan) {
        beamCount = 0
        litCount = 0
        val only = if (focus != null) chosen else -1
        seen.clear(); leafSeen.clear()
        val edges = tree.leafEdges
        val growth = growth
        for (j in 0 until count) {
            val leaf = tree.leafOfNode[nodes[j]]
            if (!leafSeen.add(leaf)) continue
            var e = tree.leafEdgeStart[leaf]
            val end = tree.leafEdgeStart[leaf + 1]
            while (e + 1 < end && beamCount < SpaceLod.MAX_BEAMS) {
                val u = edges[e]; val v = edges[e + 1]
                e += 2
                if (slot[u] < 0 && slot[v] < 0) continue
                // Show links: only the chosen body's own (focusLinks)
                if (focus != null && !MapFocus.own(only, u, v)) continue
                if (!seen.add(Octree.pairKey(min(u, v), maxOf(u, v)))) continue
                val grow = growth?.shown(u, v) ?: 1f
                if (grow <= 0.001f) continue
                addBeam(plan, u, v, grow)
            }
        }
        // the links gone since the last scene, shrinking back into their sending ends
        if (growth != null) for (g in 0 until growth.ghosts) {
            if (beamCount >= SpaceLod.MAX_BEAMS) break
            val u = growth.ghostNodes[2 * g]; val v = growth.ghostNodes[2 * g + 1]
            if (u !in slot.indices || v !in slot.indices || (slot[u] < 0 && slot[v] < 0)) continue
            val grow = growth.ghostShown(g)
            if (grow <= 0.001f) continue
            addBeam(plan, u, v, grow, ghost = true)
        }
    }

    /**
     * One beam between chunk-order nodes [u] and [v], given in the link's own order (iOS addRibbon):
     * the sending end is the higher style code (suns and pulsars send, black holes swallow), and of
     * two ends with the same code, the link's first. Each end is trimmed clear of its body, each trim
     * at most 0.45 of the length, so a short link is kept (iOS GraphRibbonWriter.path).
     */
    private fun addBeam(plan: FramePlan, u: Int, v: Int, grow: Float, ghost: Boolean = false) {
        val su = tree.groups[u].toInt(); val sv = tree.groups[v].toInt()
        val aEnd = if (sv > su) v else u
        val bEnd = if (aEnd == u) v else u
        val sa = tree.groups[aEnd].toInt(); val sb = tree.groups[bEnd].toInt()
        placed(plan, aEnd, pa); placed(plan, bEnd, pb)
        val trimA = SpaceStyle.of(sa).linkRadius * nodeRadius * 0.85f
        val trimB = SpaceStyle.of(sb).linkRadius * nodeRadius * 0.85f
        LinkStrips.trimmed(pa, 0, pb, 0, trimA, trimB, ends)
        val o = beamCount * BEAM_STRIDE
        beams[o] = ends[0]; beams[o + 1] = ends[1]; beams[o + 2] = ends[2]; beams[o + 3] = sa.toFloat()
        beams[o + 4] = ends[3]; beams[o + 5] = ends[4]; beams[o + 6] = ends[5]; beams[o + 7] = sb.toFloat()
        beams[o + 8] = linkSeed(tree.order[aEnd], tree.order[bEnd])
        // lit: the held body's links, else the chosen body's (iOS's lit bit)
        val mark = marked
        val lit = !ghost && mark >= 0 && (u == mark || v == mark)
        beams[o + 9] = if (lit) 1f else 0f
        if (lit) litCount++
        beams[o + 10] = grow
        beams[o + 11] = pa[0]; beams[o + 12] = pa[1]; beams[o + 13] = pa[2]
        axis(aEnd, beams, o + 14)
        beams[o + 17] = pb[0]; beams[o + 18] = pb[1]; beams[o + 19] = pb[2]
        axis(bEnd, beams, o + 20)
        beamCount++
    }

    /** The axis a link bends round near [node], as its body is drawn (fill): a black hole's disk, a gas giant's ring; zero for the rest. */
    private fun axis(node: Int, out: FloatArray, at: Int) {
        val a = when (tree.groups[node].toInt()) {
            SpaceStyle.BLACK_HOLE.code -> SpaceOptics.tiltedAxis(SpaceOptics.seed(tree.order[node]), 0.5f)
            SpaceStyle.GAS_GIANT.code -> SpaceOptics.tiltedAxis(SpaceOptics.seed(tree.order[node]), 0.45f)
            else -> Vec3.ZERO
        }
        out[at] = a.x; out[at + 1] = a.y; out[at + 2] = a.z
    }

    /** A link's own number, 0...15, as GraphRibbons gives each link one. */
    fun linkSeed(a: Int, b: Int): Float {
        val v = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
        return floor(abs(v - floor(v)) * 16f).coerceIn(0f, 15f)
    }
}
