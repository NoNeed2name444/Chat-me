package com.cramdown.sorta.map

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

// A seeded synthetic vault for Performance (S05i2), a port of iOS's GraphPerfSynthetic
// (ios/Sorta/Maps/GraphPerfSynthetic.swift): 100,000 notes shaped like a real vault (a few big
// folders and many small ones, links gathering on hubs, most links inside a folder), for the tests
// and the tour. Pure Kotlin; the same seed gives iOS's vault, its structure number for number. Only
// the made-up titles' words differ: iOS's are medical, these are plain (the packet's fixtures are not
// medical).

/** What [PerfGraph] is built from: notes, folders and links by index (iOS GraphPerfInput). */
class PerfInput(
    val noteIds: List<String>,
    val isPage: BooleanArray,
    /** Each note's folder, an index into [folderIds]; −1 for a loose note. */
    val folderOfNote: IntArray,
    val folderIds: List<String>,
    /** Each folder's parent, an index into [folderIds]; −1 at the top. */
    val folderParent: IntArray,
    /** Links as pairs of note indices; duplicates, either direction, self links and unknown ends are dropped. */
    val edges: IntArray,
    /** The room between bodies (the map's link length); sizes do not scale. */
    val linkScale: Float = 1f,
    /** The names, in input order; empty for the synthetic vault, whose titles are made when shown. */
    val noteTitles: List<String> = emptyList(),
    val folderNames: List<String> = emptyList(),
) {
    companion object {
        /**
         * The input from ids, as the book holds them (iOS GraphPerfInput.make): each note's folder and each
         * folder's parent by id (null, or an id not among the folders: none), links as id pairs (an id not
         * among the notes is dropped). Ids match ignoring case.
         */
        fun of(noteIds: List<String>, isPage: BooleanArray, noteFolders: List<String?>, folderIds: List<String>,
               folderParents: List<String?>, links: List<Pair<String, String>>, linkScale: Float = 1f,
               noteTitles: List<String> = emptyList(), folderNames: List<String> = emptyList()): PerfInput {
            val folderIndex = HashMap<String, Int>(folderIds.size * 2)
            for ((k, id) in folderIds.withIndex()) folderIndex.putIfAbsent(id.uppercase(), k)
            val noteIndex = HashMap<String, Int>(noteIds.size * 2)
            for ((k, id) in noteIds.withIndex()) noteIndex.putIfAbsent(id.uppercase(), k)
            val folderOf = IntArray(noteIds.size) { k -> noteFolders.getOrNull(k)?.let { folderIndex[it.uppercase()] } ?: -1 }
            val parent = IntArray(folderIds.size) { k -> folderParents.getOrNull(k)?.let { folderIndex[it.uppercase()] } ?: -1 }
            val edges = IntArray(links.size * 2)
            var e = 0
            for ((a, b) in links) {
                val ia = noteIndex[a.uppercase()] ?: continue
                val ib = noteIndex[b.uppercase()] ?: continue
                edges[e++] = ia; edges[e++] = ib
            }
            return PerfInput(noteIds, isPage, folderOf, folderIds, parent, edges.copyOf(e), linkScale, noteTitles, folderNames)
        }
    }
}

object PerfSynthetic {
    /** The vault's shape (iOS Spec, GraphPerfSynthetic.swift:9-23). The defaults are the 100k design target. */
    data class Spec(
        val notes: Int = 100_000,
        val topFolders: Int = 24,
        val maxDepth: Int = 3,
        val meanSubfolders: Double = 3.0,
        val zipf: Double = 0.8,
        val pageShare: Double = 0.15,
        val looseShare: Double = 0.02,
        /** Links a note makes: 1 + Poisson(meanExtraLinks), at most maxLinks. */
        val meanExtraLinks: Double = 1.4,
        val maxLinks: Int = 12,
        /** Where a link goes: own folder, else own region, else anywhere. */
        val ownFolder: Double = 0.85,
        val ownRegion: Double = 0.12,
        val seed: Long = 0x5EED_0100_000AL,
    )

    /** The most folders made (iOS's 8,192 − 16). */
    const val FOLDER_LIMIT = 8_192 - 16

    /** The vault as [PerfGraph]'s input (GraphPerfSynthetic.make). */
    fun make(spec: Spec = Spec()): PerfInput {
        val rng = PerfRandom(spec.seed)
        // folders: the top ones, then children breadth first, to maxDepth
        val parent = IntList(); val top = IntList(); val depth = IntList()
        for (t in 0 until spec.topFolders) { parent.add(-1); top.add(t); depth.add(0) }
        var f = 0
        while (f < parent.size && parent.size < FOLDER_LIMIT) {
            if (depth[f] < spec.maxDepth) {
                repeat(min(rng.poisson(spec.meanSubfolders), 8)) { parent.add(f); top.add(top[f]); depth.add(depth[f] + 1) }
            }
            f++
        }
        val fc = parent.size
        // Zipf weights over the folders in a shuffled order, as a running total
        val perm = IntArray(fc) { it }
        if (fc > 1) for (i in fc - 1 downTo 1) { val j = rng.below(i + 1); val t = perm[i]; perm[i] = perm[j]; perm[j] = t }
        val weight = DoubleArray(fc)
        for (r in 0 until fc) weight[perm[r]] = 1 / (r + 1).toDouble().pow(spec.zipf)
        var total = 0.0
        for (w in weight) total += w
        val cumulative = DoubleArray(fc)
        var running = 0.0
        for (i in 0 until fc) { running += weight[i] / total; cumulative[i] = running }

        // notes: kind, then loose or a folder drawn by weight
        val n = spec.notes
        val isPage = BooleanArray(n)
        val folderOf = IntArray(n) { -1 }
        for (i in 0 until n) {
            isPage[i] = rng.unit() < spec.pageShare
            if (rng.unit() < spec.looseShare || fc == 0) continue
            val u = rng.unit()
            var lo = 0; var hi = fc - 1
            while (lo < hi) { val mid = (lo + hi) / 2; if (cumulative[mid] < u) lo = mid + 1 else hi = mid }
            folderOf[i] = lo
        }

        // links: each note links to earlier notes drawn from endpoint pools (a note appears once per
        // link it has, so the linked get linked: preferential attachment, which grows hubs)
        val folderPool = Array(fc) { IntList(4) }
        val regionPool = Array(fc) { IntList(4) }
        val anywhere = IntList(n * 4)
        val edges = IntList(n * 5)
        val chosen = IntList(16)
        for (i in 0 until n) {
            val fi = folderOf[i]
            val ri = if (fi >= 0) top[fi] else -1
            val want = min(1 + rng.poisson(spec.meanExtraLinks), spec.maxLinks)
            chosen.clear()
            for (w in 0 until want) {
                val u = rng.unit()
                val a = if (u < spec.ownFolder && fi >= 0 && folderPool[fi].size > 0) folderPool[fi][rng.below(folderPool[fi].size)]
                    else if (u < spec.ownFolder + spec.ownRegion && ri >= 0 && regionPool[ri].size > 0) regionPool[ri][rng.below(regionPool[ri].size)]
                    else if (anywhere.size > 0) anywhere[rng.below(anywhere.size)]
                    else continue
                if (a == i || chosen.contains(a)) continue
                chosen.add(a)
                edges.add(i); edges.add(a)
                val fa = folderOf[a]
                if (fa >= 0) { folderPool[fa].add(a); regionPool[top[fa]].add(a) }
                anywhere.add(a)
            }
            for (k in 0..chosen.size) {
                if (fi >= 0) folderPool[fi].add(i)
                anywhere.add(i)
            }
            if (fi >= 0) regionPool[ri].add(i)
        }

        // ids last, from their own stream, so the structure above does not depend on them
        val ids = PerfRandom(spec.seed xor 0x1D5_1D5_1D5L)
        val noteIds = List(n) { ids.uuid() }
        val folderIds = List(fc) { ids.uuid() }
        return PerfInput(noteIds, isPage, folderOf, folderIds, parent.toArray(), edges.toArray(), 1f)
    }

    /** A made-up title for note [index], made when shown, never stored: a word and a five-digit number. */
    fun title(index: Int): String {
        val word = WORDS[index % WORDS.size]
        return word + " " + index.toString().padStart(5, '0')
    }

    /** Plain made-up words (iOS's are medical; Sorta's fixtures are not). */
    private val WORDS = listOf(
        "Harbour", "Lantern", "Orchard", "Meadow", "Compass", "Pebble", "Willow", "Ember",
        "Canyon", "Thistle", "Marble", "Saffron", "Glacier", "Velvet", "Juniper", "Quarry",
    )

    private fun IntList.contains(v: Int): Boolean { for (k in 0 until size) if (this[k] == v) return true; return false }
}

/** SplitMix64 as iOS's GraphPerfSynthetic.Random (same constants, unsigned shifts and modulo). */
class PerfRandom(seed: Long) {
    private var state = seed

    fun next(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** In [0, 1), from the top 53 bits. */
    fun unit(): Double = (next() ushr 11).toDouble() * (1.0 / 9_007_199_254_740_992.0)

    /** In 0 until [n] (0 when n is 0). */
    fun below(n: Int): Int = if (n > 0) java.lang.Long.remainderUnsigned(next(), n.toLong()).toInt() else 0

    /** Knuth's method; the means here are small. */
    fun poisson(mean: Double): Int {
        val limit = exp(-mean)
        var p = 1.0; var k = 0
        do { k++; p *= unit() } while (p > limit)
        return k - 1
    }

    /** A version 4 UUID from 128 random bits (the words' bytes little-endian, as iOS's raw store), upper case. */
    fun uuid(): String {
        val raw = ByteArray(16)
        val a = next(); val b = next()
        for (k in 0 until 8) { raw[k] = (a ushr (8 * k)).toByte(); raw[8 + k] = (b ushr (8 * k)).toByte() }
        raw[6] = ((raw[6].toInt() and 0x0F) or 0x40).toByte()
        raw[8] = ((raw[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = StringBuilder(36)
        for (k in 0 until 16) {
            if (k == 4 || k == 6 || k == 8 || k == 10) hex.append('-')
            val v = raw[k].toInt() and 0xFF
            hex.append(HEX[v ushr 4]); hex.append(HEX[v and 15])
        }
        return hex.toString()
    }

    companion object {
        private val GOLDEN = 0x9E37_79B9_7F4A_7C15uL.toLong()
        private val MIX1 = 0xBF58_476D_1CE4_E5B9uL.toLong()
        private val MIX2 = 0x94D0_49BB_1331_11EBuL.toLong()
        private const val HEX = "0123456789ABCDEF"
    }
}
