package com.cramdown.sorta.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// The map's filter (S05i), a port of iOS's GraphFilter and what GraphSim does with it
// (GraphMotion.swift:7-27, :1290-1380, :1913-1985) and of GraphPerfGraph.mask (GraphPerfGraph.swift:327-361,
// notes only: Android's Performance has no hubs yet). Pure: the renderers and the page read it.

/** Which notes the map shows (iOS GraphFilter). Not stored: the page holds it while it is open. */
sealed class MapFilter {
    data object All : MapFilter()
    data object Pages : MapFilter()
    data object Ideas : MapFilter()
    /** Only notes joined to at least one other. */
    data object Linked : MapFilter()
    /** Only notes somewhere inside this top-level folder (in the Universe, this galaxy). */
    data class Folder(val id: String) : MapFilter()

    val isFiltering: Boolean get() = this != All

    /** Whether a note of this kind ([page] or an idea), top-level folder [root] and [degree] links shows (iOS admits). */
    fun admits(page: Boolean, root: String?, degree: Int): Boolean = when (this) {
        All -> true
        Pages -> page
        Ideas -> !page
        Linked -> degree > 0
        is Folder -> root != null && root.equals(id, ignoreCase = true)
    }

    companion object {
        /**
         * What the camera does as the filter goes from [before] (null: the page's first) to [now]
         * (iOS apply, Graph3DView.swift:2185-2193): only where [universe] is (the Universe and the
         * Neurons), a folder flies to its galaxy or cell and Everything after a filter frames the map
         * again. Framing again only moves the camera: unlike Recentre, it closes no Neurons cell.
         */
        fun move(before: MapFilter?, now: MapFilter, universe: Boolean): FilterMove = when {
            before == null || !universe || before == now -> FilterMove.NONE
            now is Folder -> FilterMove.FLY
            now == All -> FilterMove.REFRAME
            else -> FilterMove.NONE
        }

        /**
         * The folders the filter offers (iOS topFolders, Graph3DView.swift:847-851): in the Universe the
         * plan's galaxies that are folders, by name ([galaxies], null elsewhere); else [tops], the book's top level.
         */
        fun <F> topFolders(tops: List<F>, galaxies: List<F>?, name: (F) -> String, order: Comparator<String>): List<F> =
            galaxies?.sortedWith(compareBy(order, name)) ?: tops

        /**
         * Performance's mask (GraphPerfGraph.mask over its notes): null under Everything, else each note's
         * [MapFilter.admits]. A folder filter no note is in hides every note.
         */
        fun perfMask(filter: MapFilter, pages: BooleanArray, roots: Array<String?>, degree: IntArray): BooleanArray? =
            if (filter == All) null else BooleanArray(pages.size) { filter.admits(pages[it], roots[it], degree[it]) }
    }
}

/** The camera's move for a new filter ([MapFilter.move]). */
enum class FilterMove { NONE, FLY, REFRAME }

/**
 * Every note in the book as the filter reads it, for "Nothing here for this filter." (iOS shownCount,
 * Graph3DView.swift:854-868): counted from the stored notes, not the map's bodies, so a note the map
 * draws no body for (one inside a closed Neurons cell) still counts. [roots] are top-level folders
 * (in the Universe, galaxies); [degree] counts the book's links at both ends.
 */
class MapFilterNotes(val pages: BooleanArray, val roots: Array<String?>, val degree: IntArray) {
    /** How many notes [filter] lets through. */
    fun shown(filter: MapFilter): Int {
        var n = 0
        for (i in pages.indices) if (filter.admits(pages[i], roots[i], degree[i])) n++
        return n
    }

    companion object {
        /** The notes [ids], each a page or not and in a root by index, with the book's [edges] by note id. */
        fun of(ids: List<String>, page: (Int) -> Boolean, root: (Int) -> String?, edges: List<Pair<String, String>>): MapFilterNotes {
            val at = HashMap<String, Int>(ids.size * 2)
            ids.forEachIndexed { i, id -> at[id.uppercase()] = i }
            val degree = IntArray(ids.size)
            for ((a, b) in edges) {
                at[a.uppercase()]?.let { degree[it]++ }
                at[b.uppercase()]?.let { degree[it]++ }
            }
            return MapFilterNotes(BooleanArray(ids.size) { page(it) }, Array(ids.size) { root(it) }, degree)
        }
    }
}

/** A body as the filter reads it: a note, or a folder or the home that holds notes (ThemeBodyKind). */
class MapFilterBodies(
    val kinds: Array<ThemeBodyKind>,
    /** A note that is a page; false for an idea and for every folder. */
    val pages: BooleanArray,
    /** Each body's top-level folder (in the Universe its galaxy, in the Neurons its cell), null for none. */
    val roots: Array<String?>,
    /** The body each moves with or sits in, −1 for none. */
    val parents: IntArray,
    /** The links, pairs of body indices. */
    val links: IntArray,
    /** The Universe: a hidden planet whose moon shows stays, faintly (GraphMotion.swift:1296-1300). */
    val universe: Boolean = false,
) {
    val count: Int get() = kinds.size
    /** Each body's links (iOS degree, counted over the scene's links). */
    val degree = IntArray(kinds.size).also { d ->
        for (k in 0 until links.size / 2) {
            val a = links[2 * k]; val b = links[2 * k + 1]
            if (a in d.indices && b in d.indices && a != b) { d[a]++; d[b]++ }
        }
    }
    /** A folder's or the home's notes: every note with it among its ancestors (GraphMotion.swift:647-659). */
    val members: Array<IntArray>
    /** A note's moons: notes whose parent it is. */
    val moons: Array<IntArray>

    init {
        val mem = Array(count) { ArrayList<Int>() }
        val mo = Array(count) { ArrayList<Int>() }
        for (i in 0 until count) {
            if (kinds[i] != ThemeBodyKind.NOTE) continue
            var up = parents[i]
            if (up in 0 until count && kinds[up] == ThemeBodyKind.NOTE) mo[up].add(i)
            var steps = 0
            while (up in 0 until count && steps <= count) {
                if (kinds[up] != ThemeBodyKind.NOTE) mem[up].add(i)
                up = parents[up]
                steps++
            }
        }
        members = Array(count) { mem[it].toIntArray() }
        moons = Array(count) { mo[it].toIntArray() }
    }

    /** The notes [filter] lets through (iOS shownCount, Graph3DView.swift:854-868). */
    fun shownNotes(filter: MapFilter): Int =
        (0 until count).count { kinds[it] == ThemeBodyKind.NOTE && filter.admits(pages[it], roots[it], degree[it]) }

    /**
     * What [filter] shows (iOS apply, shown(by:) and linkShown): a note when admitted; a folder or the
     * home under Everything or while any member shows; in the Universe a hidden note with a shown moon
     * as a ghost. A link shows while both ends show and neither is a ghost, and under Show links
     * ([linksFor] the chosen body, −1 for none) only the chosen body's own. The view's note count is
     * [stored]'s where given (the empty line counts the book's notes, as iOS's), else the bodies'.
     */
    fun visibility(filter: MapFilter, linksFor: Int = -1, stored: MapFilterNotes? = null): MapFilterView {
        val wanted = BooleanArray(count) { i ->
            if (kinds[i] == ThemeBodyKind.NOTE) filter.admits(pages[i], roots[i], degree[i]) else filter == MapFilter.All
        }
        if (filter != MapFilter.All) for (i in 0 until count) {
            if (kinds[i] != ThemeBodyKind.NOTE) wanted[i] = members[i].any { wanted[it] }
        }
        val ghost = BooleanArray(count) { i ->
            universe && !wanted[i] && kinds[i] == ThemeBodyKind.NOTE && moons[i].any { wanted[it] }
        }
        val shown = BooleanArray(count) { wanted[it] || ghost[it] }
        val linkShown = BooleanArray(links.size / 2) { e ->
            val a = links[2 * e]; val b = links[2 * e + 1]
            if (a !in 0 until count || b !in 0 until count) false
            else if (linksFor >= 0 && a != linksFor && b != linksFor) false
            else shown[a] && shown[b] && !ghost[a] && !ghost[b]
        }
        return MapFilterView(filter, shown, ghost, linkShown, stored?.shown(filter) ?: shownNotes(filter))
    }
}

/**
 * What the filter shows, by body (the scene's index) and by link (the scene's link order): [shown]
 * (a ghost included), [ghost] (drawn at [GHOST_ALPHA], never picked or named), [links]. [notes] is how
 * many notes pass. [instant]: no pop and no drawing back (Performance, or motion off).
 */
class MapFilterView(val filter: MapFilter, val shown: BooleanArray, val ghost: BooleanArray, val links: BooleanArray,
                    val notes: Int, val instant: Boolean = false) {
    /** Body [i] can be picked, named and chosen: shown and not a ghost. */
    fun reachable(i: Int): Boolean = i in shown.indices && shown[i] && !ghost[i]

    fun at(instantNow: Boolean): MapFilterView = if (instant == instantNow) this else MapFilterView(filter, shown, ghost, links, notes, instantNow)

    companion object {
        /** A ghost's opacity (GraphMotion.swift:1342). */
        const val GHOST_ALPHA = 0.3f
    }
}

/**
 * Each body's size spring under the filter (iOS stepPops, GraphMotion.swift:1935-1985): a body hidden
 * shrinks away without overshoot (ζ 1) and is gone at 0.02; one shown again grows from nothing with a
 * small overshoot (ζ 0.42, ω 15) and a bloom of light that fades over 0.35 s. Without [lively], at once.
 */
class FilterPop(val count: Int, val lively: Boolean) {
    private val scale = FloatArray(count) { 1f }
    private val velocity = FloatArray(count)
    private val goal = FloatArray(count) { 1f }
    private val bloom = FloatArray(count)
    private val hidden = BooleanArray(count)
    /** True while any body grows, shrinks or blooms. */
    var moving = false; private set

    fun scale(i: Int): Float = if (i in 0 until count) scale[i] else 1f
    fun bloom(i: Int): Float = if (i in 0 until count) bloom[i] else 0f
    /** Gone: shrunk away (or hidden at once), so not drawn. */
    fun gone(i: Int): Boolean = i in 0 until count && hidden[i]

    /** The bodies [shown] (null: all); [instant] sets each at once, as without [lively]. */
    fun apply(shown: BooleanArray?, instant: Boolean = false) {
        for (i in 0 until count) {
            val want = shown == null || (i < shown.size && shown[i])
            val g = if (want) 1f else 0f
            if (!lively || instant) {
                scale[i] = g; goal[i] = g; velocity[i] = 0f; bloom[i] = 0f; hidden[i] = !want
                continue
            }
            goal[i] = g
        }
        moving = lively && !instant && (0 until count).any { goal[it] != scale[it] }
    }

    /** One frame of [dt] seconds; returns whether anything still moves. */
    fun step(dt: Float): Boolean {
        if (!lively || !moving) return false
        var still = true
        val fade = exp(-dt / BLOOM_SECONDS)
        for (i in 0 until count) {
            if (bloom[i] > 0.0005f) { bloom[i] *= fade; still = false } else bloom[i] = 0f
            var s = scale[i]; var v = velocity[i]
            val g = goal[i]
            if (g > 0.5f && hidden[i]) { hidden[i] = false; s = 0f; bloom[i] = 1f; still = false }
            if (abs(g - s) < 0.0005f && abs(v) < 0.0005f) { scale[i] = g; velocity[i] = 0f; continue }
            still = false
            val zeta = if (g > 0.5f) ZETA_GROW else ZETA_SHRINK
            val pieces = max(1, ceil(dt * 240f).toInt())
            val h = dt / pieces
            repeat(pieces) {
                val pull = (g - s) * OMEGA * OMEGA
                val brake = v * 2f * zeta * OMEGA
                v += (pull - brake) * h
                s += v * h
            }
            if (g < 0.5f && s <= HIDE_AT) { s = 0f; v = 0f; hidden[i] = true }
            scale[i] = max(s, 0f); velocity[i] = v
        }
        moving = !still
        return moving
    }

    companion object {
        const val OMEGA = 15f
        const val ZETA_GROW = 0.42f
        const val ZETA_SHRINK = 1f
        const val BLOOM_SECONDS = 0.35f
        const val HIDE_AT = 0.02f
    }
}

/** Whether the link [key] is wanted (a beam's pair key), with no boxing per call. */
fun interface LinkWant { fun wanted(key: Long): Boolean }

/**
 * Links the filter takes away and gives back where the scene's links are not one list (Space's
 * beams, found pair by pair): each a growth from 1 (whole) that draws back over 0.45 s while unwanted
 * and grows over 0.7 s when wanted again (iOS stepLinks); a link at 1 and wanted is not kept.
 * Without [lively], whole or gone at once. Kept in sorted primitive arrays, so a frame allocates
 * nothing once they are big enough.
 */
class FilterLinkGrowth(val lively: Boolean) {
    private var keys = LongArray(16)
    private var grow = FloatArray(16)
    private var size = 0

    private fun find(key: Long): Int {
        var lo = 0; var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val k = keys[mid]
            if (k < key) lo = mid + 1 else if (k > key) hi = mid - 1 else return mid
        }
        return -(lo + 1)
    }

    /** The link [key]'s eased growth this frame, given whether it is [want]ed. */
    fun level(key: Long, want: Boolean): Float {
        if (!lively) return if (want) 1f else 0f
        val at = find(key)
        if (at >= 0) return LinkGrowth.ease(grow[at])
        if (want) return 1f
        // a whole link starts drawing back: kept from now on, at 1
        val slot = -(at + 1)
        if (size == keys.size) { keys = keys.copyOf(size * 2); grow = grow.copyOf(size * 2) }
        System.arraycopy(keys, slot, keys, slot + 1, size - slot)
        System.arraycopy(grow, slot, grow, slot + 1, size - slot)
        keys[slot] = key; grow[slot] = 1f; size++
        return LinkGrowth.ease(1f)
    }

    /** One frame of [dt] seconds, each kept link judged by [want]; returns whether any still moves. */
    fun step(dt: Float, want: LinkWant): Boolean {
        if (!lively || size == 0) return false
        var moving = false
        var out = 0
        for (k in 0 until size) {
            val key = keys[k]
            val w = want.wanted(key)
            val g = min(max(grow[k] + if (w) dt / LinkGrowth.GROW else -dt / LinkGrowth.DRAW_BACK, 0f), 1f)
            if (w && g >= 1f) continue
            keys[out] = key; grow[out] = g; out++
            if (w || g > 0f) moving = true
        }
        size = out
        return moving
    }

    /** No link drawn back or growing again. */
    val idle: Boolean get() = size == 0

    fun clear() { size = 0 }
}
