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
 * [focus]), y 1 for the marked body, z its look (S05h) and w how far a dying rocky body is stretched
 * along its tidal axis (S05h2: B.xyz; 0 for every other body, which the shaders read as 1).
 *
 * The bodies dying since the scene before ([death], S05h2) are drawn among them, in the same order by
 * style, at their last places, shrinking and fading by their plans: records with no node (−1 in
 * [nodes]), so they are never picked, named or marked; their links draw back into them as beams
 * after the live ones ([dyingBeamStart]), from the dying end.
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
         * axis (a black hole's disk axis, a gas giant's spin axis; zero for the other styles); then
         * (S05h) the link's Universe kind (0 same folder, 1 same galaxy, 2 far; 0 outside the Universe),
         * its bow (UniverseBeams.bow) and the chunk-order node it bows from (−1 the origin).
         */
        const val BEAM_STRIDE = 26
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

    /**
     * The Universe (S05h): each node's live home this frame, chunk order, three floats each (the
     * plan's place at the orbit clock), set by the renderer; null keeps Octree.positions.
     */
    var homes: FloatArray? = null
    /**
     * The Universe: each node's radius as the shaders take it, chunk order (UniverseBeams.shaderRadius:
     * drawn ÷ its style's bodyScale, which the shaders multiply back); null draws every body at [nodeRadius].
     */
    var radii: FloatArray? = null
        set(value) { field = value; largestReach = largest() }
    /**
     * The Universe: where each node's links stop before their trim, chunk order (UniverseBeams.reach); null
     * uses [radii]. Each body is chosen, and culled, by it ([reachOf]): iOS's reachRadius, the radius its
     * depth stage takes (GraphNodeFactory.swift:206, :305; GraphSpaceDepthStage.update, `size = radius[i]`).
     */
    var reaches: FloatArray? = null
        set(value) { field = value; largestReach = largest() }
    /** The largest [reachOf] (else [nodeRadius]): a chunk is passed over only when even its largest body could not be one. */
    private var largestReach: Float = nodeRadius
    private fun largest(): Float {
        if (radii == null && reaches == null) return nodeRadius
        var most = 0f
        for (i in 0 until tree.nodeCount) most = max(most, reachOf(i))
        return if (tree.nodeCount == 0) nodeRadius else most
    }
    /** The Universe: each node's look within its style ([UniverseLooks]), chunk order; null for none. */
    var looks: FloatArray? = null
    /** The Universe: each link's kind and centre by node pair (UniverseBeams.key, .pack); null outside it. */
    var linkKinds: Map<Long, Long>? = null
    /** The centre each beam bows from, this frame (three floats a beam; the origin for −1). */
    val beamCentres = FloatArray(SpaceLod.MAX_BEAMS * 3)
    /** Beams built this frame by kind (S05h): straight (kind 0, and every link outside the Universe), arched (1) and far (2). */
    var nearBeams = 0; private set
    var archedBeams = 0; private set
    var farBeams = 0; private set

    /** The deaths playing (S05h2), stepped by the renderer; null for none. */
    var death: MapDeathStage? = null
    /** Bodies drawn this frame that are alive (the rest of [count] are dying). */
    var liveCount = 0; private set
    /** Dying bodies drawn this frame, the beams of their links ([dyingBeamStart] until [beamCount]) and the largest reach drawn. */
    var dyingDrawn = 0; private set
    var dyingBeamStart = 0; private set
    var dyingReach = 0f; private set
    private var dyingSlot = IntArray(0)
    private var dyingDepth = FloatArray(0)

    /** Node [i]'s place along [axis]: its home (Octree.positions, or its live home in the Universe) plus its own offset from a drag. */
    private fun at(i: Int, axis: Int): Float = (homes ?: tree.positions)[3 * i + axis] + (drag?.offset(i, axis) ?: 0f)
    /** Node [i]'s own radius. */
    private fun radiusOf(i: Int): Float = radii?.getOrNull(i) ?: nodeRadius
    /** Node [i]'s links' reach in the Universe (iOS's linkRadius). */
    private fun reachOf(i: Int): Float = reaches?.getOrNull(i) ?: radiusOf(i)
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
        // the marked body (the held one, else the chosen one) is always kept, as iOS's depth stage keeps
        // `grabbed ?? selected` (GraphSpaceDepthStage.swift:151-158, GraphMotion.swift:1561-1563): drawn
        // whole wherever it is, whatever its size, the band or its chunk's openness, so a frame budget
        // that leaves its chunk a summary never takes it (nor its sparks, SpaceTrails) away. Only a body
        // behind the eye, or of a style with no shader here, is not; it keeps a place of the limit
        val keep = marked.takeIf { it in 0 until tree.nodeCount && tree.groups[it].toInt().let { s -> s in 0..5 && enabled[s] } } ?: -1
        val keepDepth = if (keep < 0) 0f
            else viewProj[3] * at(keep, 0) + viewProj[7] * at(keep, 1) + viewProj[11] * at(keep, 2) + viewProj[15]
        val kept = keep >= 0 && keepDepth > 1e-3f && limit > 0
        val others = if (kept) limit - 1 else limit
        for (k in 0 until plan.detailLeaves.size) {
            val leaf = plan.detailLeaves[k]
            if (plan.leafState[leaf * 4 + 3] < OPEN) continue
            growth?.leafOpen(leaf)
            val cell = tree.leafCell[leaf]
            val dx = tree.cx[cell] - eye.x; val dy = tree.cy[cell] - eye.y; val dz = tree.cz[cell] - eye.z
            val nearest = sqrt(dx * dx + dy * dy + dz * dz) - tree.radius[cell]
            if (nearest > 0f && SpaceLod.pixels(largestReach, focal, nearest) < SpaceLod.BODY_PIXELS * KEEP) continue
            for (i in tree.start[cell] until tree.end[cell]) {
                if (kept && i == keep) continue
                val style = tree.groups[i].toInt()
                if (style !in 0..5 || !enabled[style]) continue
                val x = at(i, 0); val y = at(i, 1); val z = at(i, 2)
                val w = viewProj[3] * x + viewProj[7] * y + viewProj[11] * z + viewProj[15]
                if (w <= 1e-3f) continue
                if (band.gone(w)) continue
                if (level(masked[i], SpaceLod.pixels(reachOf(i), focal, w), band.fade(w)) != SpaceBodyLevel.BODY) continue
                val reach = max(SpaceDepth.reachOfLook(style), SpaceStyle.of(style).extent)
                if (!frustum.intersectsSphere(x, y, z, reach * reachOf(i))) continue
                offer(i, w, others)
            }
        }
        if (kept) offer(keep, keepDepth, limit)
        // the dying (S05h2), after the live bodies against the capacity, the biggest plan first
        val dying = chooseDying(viewProj, frustum, band, capacity - heapSize)
        // the chosen, by style, far to near (each style is drawn far to near over what is behind); a
        // dying body is −(k + 1)
        val order = ((0 until heapSize) + dying).sortedWith(compareBy<Int>({ styleOfEntry(it) }, { -depthOfEntry(it) }))
        for (k in 0 until previousCount) previous[k].let { if (it >= 0) slot[it] = -1 }
        count = order.size
        liveCount = heapSize
        styleStart.fill(0)
        fadedCount = 0
        for ((j, h) in order.withIndex()) {
            if (h < 0) {
                val k = -h - 1
                nodes[j] = -1
                dyingSlot[k] = j
                styleStart[styleOfEntry(h) + 1]++
                fillDying(j, k, dyingDepth[k], eye, right, up, focal, time)
                continue
            }
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
            if (node >= 0 && slot[node] < 0 && masked[node]) { masked[node] = false; maskChanges.add(-(node + 1)) }
        }
        for (j in 0 until count) {
            val node = nodes[j]
            if (node >= 0 && !masked[node]) { masked[node] = true; maskChanges.add(node + 1) }
        }
        System.arraycopy(nodes, 0, previous, 0, count)
        previousCount = count
        buildBeams(plan)
        return liveCount
    }

    private fun styleOfEntry(e: Int): Int = if (e >= 0) tree.groups[heapNode[e]].toInt() else death?.dying?.getOrNull(-e - 1)?.code ?: 0
    private fun depthOfEntry(e: Int): Float = if (e >= 0) heapDepth[e] else dyingDepth.getOrElse(-e - 1) { 0f }

    /**
     * The dying bodies drawn this frame, as −(k + 1), at most [room]: each before its own plan's end,
     * in front of the eye, inside the depth band and the frustum, the biggest first (S05h2). Each is
     * drawn whole however small it is on the screen (repair 6): iOS plays a dying body as its own copy,
     * a plain node with no level of detail (GraphDeathArt.play, GraphDeathScene.swift:226-242), so a
     * far one shrinks as a body; it is never a point, nor left out for its size.
     */
    private fun chooseDying(viewProj: FloatArray, frustum: Frustum, band: SpaceDepthBand, room: Int): List<Int> {
        dyingDrawn = 0
        firstDying = -1
        val stage = death ?: return emptyList()
        if (dyingSlot.size != stage.dying.size) { dyingSlot = IntArray(stage.dying.size); dyingDepth = FloatArray(stage.dying.size) }
        dyingSlot.fill(-1)
        if (stage.finished || room <= 0) return emptyList()
        val out = ArrayList<Int>()
        val biggest = stage.dying.indices.sortedByDescending { stage.dying[it].radius }
        for (k in biggest) {
            if (out.size >= room) break
            if (!stage.alive(k)) continue
            val d = stage.dying[k]
            if (d.code !in 0..5 || !enabled[d.code]) continue
            val p = d.place
            val w = viewProj[3] * p.x + viewProj[7] * p.y + viewProj[11] * p.z + viewProj[15]
            if (w <= 1e-3f || band.gone(w)) continue
            val reach = max(SpaceDepth.reachOfLook(d.code), SpaceStyle.of(d.code).extent)
            if (!frustum.intersectsSphere(p.x, p.y, p.z, reach * max(nodeRadius, d.shader))) continue
            dyingDepth[k] = w
            if (firstDying < 0) firstDying = k
            out.add(-(k + 1))
        }
        dyingDrawn = out.size
        return out
    }

    /** The biggest dying body drawn this frame (its slot in the stage), −1 for none. */
    private var firstDying = -1

    /**
     * The biggest dying body drawn this frame (S05h2 repair 6), as drawn: its centre in the world
     * (x, y, z) and how many pixels across it is now, its plan's size applied; null when none is drawn.
     */
    fun biggestDyingDrawn(): FloatArray? {
        val k = firstDying
        val j = dyingSlot.getOrElse(k) { -1 }
        if (k < 0 || j < 0) return null
        val o = j * STRIDE
        return floatArrayOf(data[o], data[o + 1], data[o + 2], data[o + 15])
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
        val chosenHere = node == marked
        val own = radiusOf(node)
        val fade = fadeOf(node)
        write(j, node, c, style, seed, if (chosenHere) own * chosenScale else own, own, depth, eye, right, up, focal, time,
            SpaceOptics.beamPhase(time, seed), fade, if (chosenHere) 1f else 0f, looks?.getOrNull(node) ?: 0f)
    }

    /**
     * Dying body [k]'s record (S05h2): at its last place, its radius × its plan's size now, faded by its
     * plan's opacity (and Show links'), a rocky body stretched along its tidal axis, a pulsar's beams
     * turned from where they were by its spin-down; no node, no mark.
     */
    private fun fillDying(j: Int, k: Int, depth: Float, eye: Vec3, right: Vec3, up: Vec3, focal: Float, time: Float) {
        val stage = death ?: return
        val d = stage.dying[k]
        val style = SpaceStyle.of(d.code)
        val seed = SpaceOptics.seed(d.index)
        val s = stage.size[k]
        val radius = max(d.shader * s.across, d.shader * 0.0005f)
        val fade = stage.opacity[k] * (if (focus == null) 1f else MapMark.FADED)
        val o = write(j, -1, d.place, style, seed, radius, radius, depth, eye, right, up, focal, time,
            stage.beamPhase(k, time, seed), fade, 0f, d.look)
        if (style == SpaceStyle.ROCKY && s.across > 1e-6f) {
            val axis = stage.axes[k]
            data[o + 12] = axis.x; data[o + 13] = axis.y; data[o + 14] = axis.z
            data[o + 19] = max(s.along / s.across, 1f)
        }
    }

    /** One body's record at slot [j]; [self] its node (−1 for a dying body), [phase] a pulsar's beams' turn. The record's offset. */
    private fun write(j: Int, self: Int, c: Vec3, style: SpaceStyle, seed: Float, radius: Float, own: Float, depth: Float,
                      eye: Vec3, right: Vec3, up: Vec3, focal: Float, time: Float, phase: Float, fade: Float, mark: Float,
                      look: Float): Int {
        val toEye = SpaceOptics.unit(eye - c, Vec3(0f, 0f, 1f))
        var light = if (style == SpaceStyle.SUN || style == SpaceStyle.BLACK_HOLE) KEY else SpaceOptics.light(c, nearestSuns(c, self), KEY)
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
                a = SpaceOptics.beam(n, e1, e2, phase)
                val (turn, edgeOn) = SpaceOptics.nebula(n, toEye, right, up)
                codeA = 2f + edgeOn
                light = Vec3(turn, 0f, 0f)
                b = n
            }
            SpaceStyle.COMET -> {
                val sun = nearestSuns(c, self).firstOrNull()?.let { Vec3(it[0], it[1], it[2]) }
                val (ion, dust) = SpaceOptics.cometTails(c, Vec3.ZERO, sun, KEY)
                a = ion; b = dust
            }
        }
        val o = j * STRIDE
        data[o] = c.x; data[o + 1] = c.y; data[o + 2] = c.z; data[o + 3] = radius
        data[o + 4] = light.x; data[o + 5] = light.y; data[o + 6] = light.z; data[o + 7] = seed
        data[o + 8] = a.x; data[o + 9] = a.y; data[o + 10] = a.z; data[o + 11] = codeA
        data[o + 12] = b.x; data[o + 13] = b.y; data[o + 14] = b.z; data[o + 15] = SpaceLod.pixels(own, focal, depth)
        if (fade < 1f) fadedCount++
        data[o + 16] = fade; data[o + 17] = mark; data[o + 18] = look; data[o + 19] = 0f
        return o
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
        // a body drawn whole is at its own place (a kept body's chunk may be part way to its summary)
        val t = if (slot[node] >= 0) 1f else plan.leafState[s + 3].coerceIn(0f, 1f)
        for (a in 0 until 3) out[a] = plan.leafState[s + a] + (at(node, a) - plan.leafState[s + a]) * t
    }

    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val ends = FloatArray(6)

    private fun buildBeams(plan: FramePlan) {
        beamCount = 0
        litCount = 0
        nearBeams = 0; archedBeams = 0; farBeams = 0
        val only = if (focus != null) chosen else -1
        seen.clear(); leafSeen.clear()
        val edges = tree.leafEdges
        val growth = growth
        for (j in 0 until count) {
            if (nodes[j] < 0) continue
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
        buildDyingBeams(plan)
    }

    /**
     * The dying bodies' links (S05h2), after the rest: each from its dying end (the stage's ribbon grows
     * from it, GraphDeathScene.swift:201) to a live body or another dying one, grown as far as the stage
     * says, straight and of no kind (GraphRibbonLink(a:b:seed:grow:)), drawn while either end is a body.
     */
    private fun buildDyingBeams(plan: FramePlan) {
        dyingBeamStart = beamCount
        dyingReach = 0f
        val stage = death ?: return
        if (stage.finished) return
        for (j in 0 until stage.linkCount) {
            if (beamCount >= SpaceLod.MAX_BEAMS) break
            val grow = stage.reach[j]
            if (grow <= 0.001f) continue
            val k = stage.linkDying[j]
            val other = stage.linkOther[j]; val mate = stage.linkMate[j]
            val aDrawn = dyingSlot.getOrElse(k) { -1 } >= 0
            val bDrawn = if (other >= 0) other in slot.indices && slot[other] >= 0 else mate >= 0 && dyingSlot.getOrElse(mate) { -1 } >= 0
            if (!aDrawn && !bDrawn) continue
            if (other < 0 && mate < 0) continue
            val d = stage.dying[k]
            val sa = d.code
            pa[0] = d.place.x; pa[1] = d.place.y; pa[2] = d.place.z
            val trimA = trimOf(sa, d.reach) * stage.linkRadius(k)
            val sb: Int; val trimB: Float
            if (other >= 0) {
                sb = tree.groups[other].toInt()
                placed(plan, other, pb)
                trimB = if (radii != null) UniverseBeams.trim(sb) * reachOf(other) else SpaceStyle.of(sb).linkRadius * nodeRadius * 0.85f
            } else {
                val m = stage.dying[mate]
                sb = m.code
                pb[0] = m.place.x; pb[1] = m.place.y; pb[2] = m.place.z
                trimB = trimOf(sb, m.reach) * stage.linkRadius(mate)
            }
            LinkStrips.trimmed(pa, 0, pb, 0, trimA, trimB, ends)
            val o = beamCount * BEAM_STRIDE
            beams[o] = ends[0]; beams[o + 1] = ends[1]; beams[o + 2] = ends[2]; beams[o + 3] = sa.toFloat()
            beams[o + 4] = ends[3]; beams[o + 5] = ends[4]; beams[o + 6] = ends[5]; beams[o + 7] = sb.toFloat()
            beams[o + 8] = stage.linkSeed[j]
            beams[o + 9] = 0f
            beams[o + 10] = grow
            beams[o + 11] = pa[0]; beams[o + 12] = pa[1]; beams[o + 13] = pa[2]
            axisOf(sa, d.index, beams, o + 14)
            beams[o + 17] = pb[0]; beams[o + 18] = pb[1]; beams[o + 19] = pb[2]
            if (other >= 0) axis(other, beams, o + 20) else axisOf(sb, stage.dying[mate].index, beams, o + 20)
            beams[o + 23] = 0f; beams[o + 24] = 0f; beams[o + 25] = -1f
            val c = beamCount * 3
            beamCentres[c] = 0f; beamCentres[c + 1] = 0f; beamCentres[c + 2] = 0f
            dyingReach = max(dyingReach, grow)
            beamCount++
        }
    }

    /** Where a link stops short of a body of style [code] whose links reach [reach]: the Universe's trim, or the style's link radius. */
    private fun trimOf(code: Int, reach: Float): Float =
        if (radii != null) UniverseBeams.trim(code) * reach else SpaceStyle.of(code).linkRadius * reach * 0.85f

    /** Dying beams drawn this frame. */
    val dyingBeams: Int get() = beamCount - dyingBeamStart

    /**
     * One beam between chunk-order nodes [u] and [v], given in the link's own order (iOS addRibbon):
     * the sending end is the higher style code (suns and pulsars send, black holes swallow), and of
     * two ends with the same code, the link's first. Each end is trimmed clear of its body, each trim
     * at most 0.45 of the length, so a short link is kept (iOS GraphRibbonWriter.path).
     */
    private fun addBeam(plan: FramePlan, u: Int, v: Int, grow: Float, ghost: Boolean = false) {
        // the Universe's kind: a moon's link to its own planet is never drawn (GraphMotion.swift:1827)
        val packed = linkKinds?.get(UniverseBeams.key(u, v))
        val kind = packed?.let { UniverseBeams.kindOf(it) } ?: 0
        if (!UniverseBeams.drawn(kind)) return
        val centre = packed?.let { UniverseBeams.centreOf(it) } ?: -1
        val su = tree.groups[u].toInt(); val sv = tree.groups[v].toInt()
        val aEnd = if (sv > su) v else u
        val bEnd = if (aEnd == u) v else u
        val sa = tree.groups[aEnd].toInt(); val sb = tree.groups[bEnd].toInt()
        placed(plan, aEnd, pa); placed(plan, bEnd, pb)
        // in the Universe iOS's trims by style, times each body's own reach (GraphUniverseScene.swift:207, GraphRibbons.swift:471-472)
        val trimA = if (radii != null) UniverseBeams.trim(sa) * reachOf(aEnd) else SpaceStyle.of(sa).linkRadius * nodeRadius * 0.85f
        val trimB = if (radii != null) UniverseBeams.trim(sb) * reachOf(bEnd) else SpaceStyle.of(sb).linkRadius * nodeRadius * 0.85f
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
        beams[o + 23] = kind.toFloat()
        beams[o + 24] = UniverseBeams.bow(kind)
        beams[o + 25] = centre.toFloat()
        val c = beamCount * 3
        if (centre in 0 until tree.nodeCount) for (a in 0 until 3) beamCentres[c + a] = at(centre, a)
        else { beamCentres[c] = 0f; beamCentres[c + 1] = 0f; beamCentres[c + 2] = 0f }
        when (kind) { 1 -> archedBeams++; 2 -> farBeams++; else -> nearBeams++ }
        beamCount++
    }

    /** The axis a link bends round near [node], as its body is drawn (fill): a black hole's disk, a gas giant's ring; zero for the rest. */
    private fun axis(node: Int, out: FloatArray, at: Int) = axisOf(tree.groups[node].toInt(), tree.order[node], out, at)

    /** The same for a body of style [code] whose seed is its scene index [index]. */
    private fun axisOf(code: Int, index: Int, out: FloatArray, at: Int) {
        val a = when (code) {
            SpaceStyle.BLACK_HOLE.code -> SpaceOptics.tiltedAxis(SpaceOptics.seed(index), 0.5f)
            SpaceStyle.GAS_GIANT.code -> SpaceOptics.tiltedAxis(SpaceOptics.seed(index), 0.45f)
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

/**
 * The Universe's links as beams (S05h), iOS's GraphUniverseScene.swift:195-207 and addRibbon
 * (GraphMotion.swift:1878-1905): a link inside a folder straight, one between folders of a galaxy
 * arched 0.10 of its length away from the galaxy's core, one between galaxies or to a comet arched
 * 0.15 from the origin and drawn apart at 0.55 of the brightness (0.825 bold); a moon's link to its own
 * planet not at all. Fine beams, 0.07 each side, starting inside each body by its style's trim.
 */
object UniverseBeams {
    /** Each style's trim, times the body's own radius: black hole, sun, gas giant, rocky, pulsar, comet. */
    val TRIMS = floatArrayOf(1.0f, 0.72f, 0.72f, 1.0f, 1.0f, 0.75f)
    /** The beams' half width, near and far, in the plan's units. */
    const val HALF_WIDTH = 0.07f
    const val FAR_ENERGY = 0.55f
    const val FAR_ENERGY_BOLD = 0.825f

    fun trim(code: Int): Float = TRIMS.getOrElse(code) { 1f }
    fun bow(kind: Int): Float = when (kind) { 1 -> 0.10f; 2 -> 0.15f; else -> 0f }
    fun drawn(kind: Int): Boolean = kind != 3
    fun far(kind: Int): Boolean = kind == 2
    fun farEnergy(bold: Boolean): Float = if (bold) FAR_ENERGY_BOLD else FAR_ENERGY

    /** A pair of chunk-order nodes, either way round. */
    fun key(u: Int, v: Int): Long = Octree.pairKey(min(u, v), max(u, v))
    fun pack(kind: Int, centre: Int): Long = kind.toLong() or ((centre + 1).toLong() shl 8)
    fun kindOf(packed: Long): Int = (packed and 0xFF).toInt()
    fun centreOf(packed: Long): Int = (packed shr 8).toInt() - 1

    /**
     * Each of [plan]'s links by its chunk-order pair ([chunkOf]: a plan index's node), packed with its
     * kind and its centre's node: what [SpaceBodies.linkKinds] reads to bow a beam or leave it out.
     */
    fun kinds(plan: UniversePlan, chunkOf: IntArray): Map<Long, Long> {
        val kinds = HashMap<Long, Long>(plan.links.size * 2)
        for (link in plan.links) {
            val a = chunkOf.getOrElse(link.a) { -1 }; val b = chunkOf.getOrElse(link.b) { -1 }
            if (a < 0 || b < 0) continue
            kinds[key(a, b)] = pack(link.kind, if (link.centre >= 0) chunkOf.getOrElse(link.centre) { -1 } else -1)
        }
        return kinds
    }

    /** The Space style a role is drawn in (iOS's dress, GraphUniverseScene.swift:265-305). */
    fun style(role: UniverseRole): SpaceStyle = when (role) {
        UniverseRole.GALAXY -> SpaceStyle.BLACK_HOLE
        UniverseRole.STAR, UniverseRole.HOME -> SpaceStyle.SUN
        UniverseRole.GAS_GIANT -> SpaceStyle.GAS_GIANT
        UniverseRole.ROCKY, UniverseRole.MOON -> SpaceStyle.ROCKY
        UniverseRole.PULSAR -> SpaceStyle.PULSAR
        UniverseRole.COMET, UniverseRole.OORT -> SpaceStyle.COMET
    }

    /** How much past its planned sphere a body is drawn: a star and an unringed planet 1.2 (GraphUniverseScene.swift:100-104), else 1. */
    fun grow(body: UniverseBody): Float {
        val grown = body.role == UniverseRole.STAR || body.role == UniverseRole.HOME ||
            ((body.role == UniverseRole.GAS_GIANT || body.role == UniverseRole.ROCKY) && !body.ringed)
        return if (grown) 1.2f else 1f
    }

    /** A body's drawn radius: its sphere × [grow]. */
    fun radius(body: UniverseBody): Float = body.sphere * grow(body)

    /**
     * The radius the shaders take for a body (SpaceBodies.radii, vCenter.w): drawn ÷ its style's bodyScale,
     * as iOS hands its kit sphere × grow ÷ bodyScale (GraphUniverseScene.swift:104) and the kit multiplies
     * back (GraphNodeFactory.swift:171), so the body is drawn at exactly sphere × grow.
     */
    fun shaderRadius(body: UniverseBody): Float = radius(body) / style(body.role).bodyScale

    /** Where a body's links stop before their trim (iOS's linkRadius, GraphNodeFactory.swift:390-395): a pulsar's and a comet's 0.45 of [shaderRadius], else the drawn radius. */
    fun reach(body: UniverseBody): Float = when (style(body.role)) {
        SpaceStyle.PULSAR, SpaceStyle.COMET -> shaderRadius(body) * 0.45f
        else -> radius(body)
    }
}

/**
 * A Universe body's look within its style (S05h; iOS's dress, GraphUniverseScene.swift:265-305, and
 * GraphNodeFactory.swift:365-372), the shaders' vMark.z: a star's tier colour, a dark (empty) star
 * with no corona, an empty galaxy's black hole with no disk, a gas giant with no ring unless ringed.
 * The moon's own look and a resting comet with no tail are named but not yet drawn apart.
 */
object UniverseLooks {
    const val PLAIN = 0f
    const val TIER_1 = 1f
    const val TIER_2 = 2f
    const val TIER_3 = 3f
    const val DARK_STAR = 4f
    const val EMPTY_GALAXY = 5f
    const val MOON = 6f
    const val RESTING_COMET = 7f
    const val UNRINGED = 8f

    /** A sun's colour by tier: 1 white, 2 (1.0, 0.74, 0.50), 3 (1.0, 0.52, 0.36); a dark star (0.42, 0.20, 0.14). */
    fun sunTint(look: Float): Vec3 = when (look) {
        TIER_2 -> Vec3(1.0f, 0.74f, 0.50f)
        TIER_3 -> Vec3(1.0f, 0.52f, 0.36f)
        DARK_STAR -> Vec3(0.42f, 0.20f, 0.14f)
        else -> Vec3(1f, 1f, 1f)
    }

    fun of(body: UniverseBody): Float = when (body.role) {
        UniverseRole.HOME -> if (body.empty) DARK_STAR else TIER_1
        UniverseRole.STAR -> if (body.empty) DARK_STAR else when (body.tier.coerceIn(1, 3)) { 1 -> TIER_1; 2 -> TIER_2; else -> TIER_3 }
        UniverseRole.GALAXY -> if (body.empty) EMPTY_GALAXY else PLAIN
        UniverseRole.GAS_GIANT -> if (body.ringed) PLAIN else UNRINGED
        UniverseRole.MOON -> MOON
        UniverseRole.OORT -> RESTING_COMET
        else -> PLAIN
    }
}
