package com.cramdown.sorta.map

// A port of iOS GraphMemory's links (GraphRibbons.swift:621-693), for the GL map (S05e).
//
// When a scene is built again (a note added, a link drawn), only the links new to it grow in, and
// the links gone from it shrink back into their sending note, as iOS's do. The memory is of the
// scene still on screen: Map3DView remembers each scene it shows and forgets it when it leaves the
// screen, so a map opened afresh grows every link (iOS keeps the last scene weakly, which ends the
// same way). Links are keyed by their notes' ids, so the key outlives the scene's indices.

/**
 * What changed since the scene still on screen: [fresh] the keys of the links new to this one (null
 * when there is none to compare with, so every link is fresh), and [gone] the links it no longer has
 * between notes it still has, as pairs of ids (iOS GraphRecall's freshLinks and ghosts).
 */
class LinkRecall(val fresh: Set<String>?, val gone: List<Pair<String, String>>) {
    /** True when the link between notes [a] and [b] is new to this scene. */
    fun isFresh(a: String, b: String): Boolean = fresh?.contains(LinkMemory.key(a, b)) ?: true

    companion object {
        /** Nothing to compare with: every link grows, none shrinks (iOS GraphRecall.everything). */
        val EVERYTHING = LinkRecall(null, emptyList())
    }
}

object LinkMemory {
    private var lastBuild = 0
    private var lastLinks: Set<String>? = null
    private var lastPairs: Map<String, Pair<String, String>> = emptyMap()

    /** One key for the link between notes [a] and [b], whichever way round (iOS GraphMemory.key). */
    fun key(a: String, b: String): String = if (a < b) a + "\n" + b else b + "\n" + a

    /**
     * What changed between the scene before ([lastLinks], keyed, and [lastPairs], each key's two ids;
     * null when there is none) and this one, whose notes are [ids] and links [links] (pairs of indices
     * into [ids]). Everything when there is none or nothing moves anyway ([lively] false). A gone link
     * is kept only while both its notes are still here (iOS GraphMemory.recall).
     */
    fun recall(lastLinks: Set<String>?, lastPairs: Map<String, Pair<String, String>>, ids: List<String>, links: IntArray,
               lively: Boolean): LinkRecall {
        if (!lively || lastLinks == null) return LinkRecall.EVERYTHING
        val now = keys(ids, links)
        val fresh = now.keys.filterTo(HashSet()) { it !in lastLinks }
        val here = ids.toHashSet()
        val gone = ArrayList<Pair<String, String>>()
        for (k in lastLinks) {
            if (k in now) continue
            val pair = lastPairs[k] ?: continue
            if (pair.first in here && pair.second in here) gone.add(pair)
        }
        return LinkRecall(fresh, gone)
    }

    /** Each link's key and its two ids, in the scene's order. */
    fun keys(ids: List<String>, links: IntArray): LinkedHashMap<String, Pair<String, String>> {
        val out = LinkedHashMap<String, Pair<String, String>>()
        var k = 0
        while (k + 1 < links.size) {
            val a = links[k]; val b = links[k + 1]
            k += 2
            if (a !in ids.indices || b !in ids.indices) continue
            out[key(ids[a], ids[b])] = ids[a] to ids[b]
        }
        return out
    }

    /** What changed since the scene on screen, for a new scene of notes [ids] and links [links]. */
    @Synchronized fun recall(ids: List<String>, links: IntArray, lively: Boolean): LinkRecall =
        recall(lastLinks, lastPairs, ids, links, lively)

    /** Remembers the scene now on screen, built as number [build]. */
    @Synchronized fun remember(build: Int, ids: List<String>, links: IntArray) {
        val pairs = keys(ids, links)
        lastBuild = build
        lastLinks = pairs.keys.toHashSet()
        lastPairs = pairs
    }

    /** Forgets scene [build] as it leaves the screen; a later scene remembered since is kept. */
    @Synchronized fun forget(build: Int) {
        if (build != lastBuild) return
        lastBuild = 0
        lastLinks = null
        lastPairs = emptyMap()
    }

    /** Forgets whatever is remembered. */
    @Synchronized fun clear() = forget(lastBuild)
}
