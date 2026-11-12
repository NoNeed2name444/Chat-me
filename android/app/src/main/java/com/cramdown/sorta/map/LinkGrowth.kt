package com.cramdown.sorta.map

import kotlin.math.max
import kotlin.math.min

// How far each link has grown, a port of iOS GraphSim's linkGrow, linkWait and ghostGrow
// (GraphMotion.swift:796-818, stepLinks at 1910-1929, addRibbon's ease at 1876), for the GL map
// (S05e). A new link waits, then grows from its sending end over 0.7 s; a link whose notes are
// filtered away draws back into it over 0.45 s; a link gone since the last scene (a ghost) shrinks
// back over 0.6 s. Each is drawn eased by a smoothstep. With motion off every link is whole at once
// and none shrinks.
//
// iOS's wait is the later of its two notes' pop delays plus 0.25 s, counted from the scene's start.
// Android's notes do not pop: Neurons' cells are there from the first frame, and Space's bodies
// open out of their chunk (SpaceLinkGrowth). So here the 0.25 s wait is counted from the first frame
// the link is ready (both its ends there), which [tick] and [start] are told.

/**
 * The growth of [links] links and [ghosts] ghosts, one index each: links first (0 until [links]),
 * then the ghosts (from [links]). [fresh] says which links are new to the scene; [ghostEnds] holds
 * each ghost's two ends as the scene's indices (pairs, the sending end first).
 */
class LinkGrowth(fresh: BooleanArray, val ghostEnds: IntArray = IntArray(0), val lively: Boolean = true) {
    companion object {
        /** iOS's wait past the pop delays, here past the first frame both ends are there. */
        const val WAIT = 0.25f
        /** Growing in, drawing back, and a gone link's shrinking, in seconds (iOS stepLinks). */
        const val GROW = 0.7f
        const val DRAW_BACK = 0.45f
        const val FADE = 0.6f

        /** iOS addRibbon's ease. */
        fun ease(g: Float): Float = g * g * (3f - 2f * g)

        /**
         * The growth for a scene of notes [ids] and links [links] (pairs of indices into [ids]) given
         * what changed since the scene before ([recall], LinkMemory): fresh links grow, gone ones
         * between notes still here shrink (iOS GraphSim's links, GraphMotion.swift:796-818).
         */
        fun of(ids: List<String>, links: IntArray, recall: LinkRecall, lively: Boolean): LinkGrowth {
            val n = links.size / 2
            val fresh = BooleanArray(n) { e ->
                val a = links[2 * e]; val b = links[2 * e + 1]
                a in ids.indices && b in ids.indices && recall.isFresh(ids[a], ids[b])
            }
            val ends = IntList(8)
            if (lively && recall.gone.isNotEmpty()) {
                val index = HashMap<String, Int>(ids.size * 2)
                for (i in ids.indices) index.putIfAbsent(ids[i], i)
                for ((a, b) in recall.gone) {
                    val i = index[a] ?: continue
                    val j = index[b] ?: continue
                    ends.add(i); ends.add(j)
                }
            }
            return LinkGrowth(fresh, ends.toArray(), lively)
        }
    }

    val links = fresh.size
    val ghosts = if (lively) ghostEnds.size / 2 else 0
    val size: Int get() = links + ghosts

    /** iOS linkGrow then ghostGrow: 0 (none drawn) to 1 (whole). */
    val grow = FloatArray(links + ghosts) { if (it >= links) 1f else if (lively && fresh[it]) 0f else 1f }
    /** iOS linkWait: what is left of the wait once the link is ready. */
    val wait = FloatArray(links + ghosts) { WAIT }
    private val started = BooleanArray(links + ghosts) { !lively }

    /** True once the link's (or ghost's) clock has started. */
    fun started(i: Int): Boolean = started[i]

    /** True when link or ghost [i] will not change again on its own: a link whole, a ghost gone. */
    fun settled(i: Int): Boolean = if (i < links) grow[i] >= 1f else grow[i] <= 0f

    /**
     * One frame of [dt] seconds for link or ghost [i]: nothing until the first frame it is [ready],
     * which starts its clock; then a link waits and grows (iOS stepLinks with the link wanted) and a
     * ghost shrinks.
     */
    fun tick(i: Int, ready: Boolean, dt: Float) {
        if (!lively) return
        if (!started[i]) {
            if (ready) started[i] = true
            return
        }
        if (i < links) step(i, true, dt) else grow[i] = max(grow[i] - dt / FADE, 0f)
    }

    /**
     * Starts link or ghost [i]'s clock [since] seconds ago, as if each frame since had been ticked:
     * a link first drawn well after both its ends were there is already whole.
     */
    fun start(i: Int, since: Float) {
        if (started[i]) return
        started[i] = true
        if (!lively) return
        var left = max(since, 0f)
        if (i >= links) { grow[i] = max(grow[i] - left / FADE, 0f); return }
        if (grow[i] < 1f && wait[i] > 0f) {
            val w = min(wait[i], left)
            wait[i] -= w; left -= w
        }
        if (left > 0f) grow[i] = min(grow[i] + left / GROW, 1f)
    }

    /**
     * iOS stepLinks for link [e]: while it is [want]ed and not whole it first waits out its wait,
     * then grows over 0.7 s; once not wanted it draws back over 0.45 s.
     */
    fun step(e: Int, want: Boolean, dt: Float) {
        if (want && grow[e] < 1f && wait[e] > 0f) {
            wait[e] -= dt
            return
        }
        val change = if (want) dt / GROW else -dt / DRAW_BACK
        grow[e] = min(max(grow[e] + change, 0f), 1f)
    }

    /** How much of link or ghost [i] is drawn, eased: whole with motion off (iOS updateLines). */
    fun shown(i: Int): Float {
        if (!lively) return if (i < links) 1f else 0f
        return ease(min(max(grow[i], 0f), 1f))
    }
}

/**
 * Space's link growth (LinkGrowth) on the octree, in chunk order, for SpaceBodies. [links] are the
 * scene's links as pairs of its own node indices (MapScene.links), [growth] their growth with the
 * ghosts' ends in the same indices.
 *
 * A link's clock starts on the first frame it is drawn, from the first frame both its ends' chunks
 * were open (or from now, for an end whose chunk is not open: out of view, or still opening). So when
 * the map opens, each link waits 0.25 s from the frame its bodies open and then grows; a link the
 * camera brings in later, between chunks open long since, is already whole; and a link that leaves
 * the drawn set and comes back keeps its growth (the level of detail never grows a link again).
 */
class SpaceLinkGrowth(private val tree: Octree, links: IntArray, val growth: LinkGrowth) {
    /** Each link's chunk-order pair (Octree.pairKey, the lower first), to its index + 1. */
    private val index = LongIntMap(max(16, growth.links))
    /** Each ghost's two ends in chunk order, the sending end first. */
    val ghostNodes = IntArray(growth.ghosts * 2)
    private val openedAt = FloatArray(tree.leafCount) { -1f }
    private var clock = 0f
    private var active = IntArray(64)
    private var activeCount = 0

    init {
        val chunk = IntArray(tree.nodeCount)
        for (k in 0 until tree.nodeCount) chunk[tree.order[k]] = k
        for (e in 0 until growth.links) {
            val a = links[2 * e]; val b = links[2 * e + 1]
            if (a !in chunk.indices || b !in chunk.indices) continue
            val u = chunk[a]; val v = chunk[b]
            val key = Octree.pairKey(min(u, v), max(u, v))
            if (index[key] == 0) index.add(key, e + 1)
        }
        for (g in 0 until growth.ghosts) {
            val a = growth.ghostEnds[2 * g]; val b = growth.ghostEnds[2 * g + 1]
            ghostNodes[2 * g] = if (a in chunk.indices) chunk[a] else -1
            ghostNodes[2 * g + 1] = if (b in chunk.indices) chunk[b] else -1
        }
    }

    val ghosts: Int get() = growth.ghosts

    /** A new frame, [dt] seconds after the last: the links and ghosts on their way move on. */
    fun tick(dt: Float) {
        clock += dt
        var kept = 0
        for (k in 0 until activeCount) {
            val i = active[k]
            growth.tick(i, true, dt)
            if (!growth.settled(i)) active[kept++] = i
        }
        activeCount = kept
    }

    /** Chunk [leaf] is open into its nodes this frame. */
    fun leafOpen(leaf: Int) {
        if (openedAt[leaf] < 0f) openedAt[leaf] = clock
    }

    /** How much of the link between chunk-order nodes [u] and [v] is drawn this frame (1 for one not in the scene). */
    fun shown(u: Int, v: Int): Float {
        val e = index[Octree.pairKey(min(u, v), max(u, v))] - 1
        return if (e < 0) 1f else shownAt(e, u, v)
    }

    /** How much of ghost [g] is drawn this frame. */
    fun ghostShown(g: Int): Float = shownAt(growth.links + g, ghostNodes[2 * g], ghostNodes[2 * g + 1])

    private fun shownAt(i: Int, u: Int, v: Int): Float {
        if (!growth.started(i)) {
            growth.start(i, clock - max(openedFor(u), openedFor(v)))
            if (!growth.settled(i)) {
                if (activeCount == active.size) active = active.copyOf(activeCount * 2)
                active[activeCount++] = i
            }
        }
        return growth.shown(i)
    }

    private fun openedFor(node: Int): Float {
        if (node !in tree.leafOfNode.indices) return clock
        val t = openedAt[tree.leafOfNode[node]]
        return if (t < 0f) clock else t
    }
}
