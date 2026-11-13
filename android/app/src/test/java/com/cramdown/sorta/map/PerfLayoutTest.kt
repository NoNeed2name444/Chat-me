package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.max

// Performance laid out by folders (S05i2): iOS's P3, the seed layout on the 100,000-note vault
// (GraphPerfTests.swift:225-282), P2's graph cases and its shuffled copy on the 4,000-note one
// (:94-201), an empty vault, and the hub's radius. Made-up fixtures, nothing medical.
class PerfLayoutTest {
    private val g = PerfVault.graph
    private val l = PerfVault.seed
    private val n = g.noteCount
    private val p = l.positions

    private fun dist(i: Int, cx: Float, cy: Float, cz: Float) = PerfVault.length(p[3 * i] - cx, p[3 * i + 1] - cy, p[3 * i + 2] - cz)
    private fun centre(s: Int) = Triple(l.systemCentre[3 * s], l.systemCentre[3 * s + 1], l.systemCentre[3 * s + 2])

    @Test fun notesThenHubsAllFinite() {
        assertEquals(3 * (n + g.folderCount), p.size)
        assertTrue(p.all { it.isFinite() })
        assertEquals(n + g.folderCount, l.radius.size)
    }

    @Test fun everyNoteInsideItsFoldersBallAndItsRegionsSystem() {
        var outside = 0; var outsideRegion = 0
        for (i in 0 until n) {
            val s = g.systemOfNote[i]
            val (x, y, z) = centre(s)
            if (dist(i, x, y, z) + l.radius[i] > l.coreRadius[s] + 1e-3f) outside++
            val top = if (g.folder[i] >= 0) g.folderRegion[g.folder[i]] else s
            val (tx, ty, tz) = centre(top)
            if (dist(i, tx, ty, tz) > l.systemRadius[top] + 1e-2f) outsideRegion++
        }
        assertEquals(0, outside)
        assertEquals(0, outsideRegion)
    }

    @Test fun noSiblingSystemsOverlapNorTheirParentsBall() {
        var overlaps = 0
        fun apart(list: IntArray, core: Float, around: Triple<Float, Float, Float>) {
            for ((k, a) in list.withIndex()) {
                val (ax, ay, az) = centre(a)
                val ra = l.systemRadius[a]
                if (core > 0 && PerfVault.length(ax - around.first, ay - around.second, az - around.third) < core + ra - 1e-3f) overlaps++
                for (b in list.drop(k + 1)) {
                    val (bx, by, bz) = centre(b)
                    if (PerfVault.length(ax - bx, ay - by, az - bz) < ra + l.systemRadius[b] - 1e-3f) overlaps++
                }
            }
        }
        for (f in 0 until g.folderCount) {
            apart(IntArray(g.folderChildStart[f + 1] - g.folderChildStart[f]) { g.folderChildren[g.folderChildStart[f] + it] },
                l.coreRadius[f], centre(f))
        }
        apart(((0 until g.folderCount).filter { g.folderParent[it] < 0 } + (g.folderCount until g.systemCount)).toIntArray(), 0f, Triple(0f, 0f, 0f))
        assertEquals(0, overlaps)
    }

    @Test fun notesAtLeastPointEightOfASpacingApartAndNoDiscsTouch() {
        val least = PerfVault.nearestSpacing(p, n, 2 * l.spacing)
        assertTrue("$least", least >= 0.8f)
        // every body against those in the cells round it, cells wider than any two radii
        val cell = 2.5f
        val grid = HashMap<Long, MutableList<Int>>()
        fun c(v: Float) = floor(v / cell).toLong()
        fun key(x: Long, y: Long, z: Long) = ((x + 1_000_000) * 2_000_003 + (y + 1_000_000)) * 2_000_003 + (z + 1_000_000)
        val count = n + g.folderCount
        for (i in 0 until count) grid.getOrPut(key(c(p[3 * i]), c(p[3 * i + 1]), c(p[3 * i + 2]))) { ArrayList() }.add(i)
        var touching = 0
        for (i in 0 until count) {
            val cx = c(p[3 * i]); val cy = c(p[3 * i + 1]); val cz = c(p[3 * i + 2])
            for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
                for (j in grid[key(cx + dx, cy + dy, cz + dz)] ?: continue) {
                    if (j != i && dist(i, p[3 * j], p[3 * j + 1], p[3 * j + 2]) < l.radius[i] + l.radius[j]) touching++
                }
            }
        }
        assertEquals(0, touching)
    }

    @Test fun deterministicAndACancelledLayoutStops() {
        assertArrayEquals(p, PerfLayout.seed(PerfGraph(PerfVault.input))!!.positions, 0f)
        var after = 3
        assertNull(PerfLayout.seed(g) { after--; after < 0 })
        assertNull(PerfMap.build(PerfVault.small) { true })
    }

    @Test fun aLongerLinkScaleMovesBodiesApartSizesStay() {
        val extents = ArrayList<Float>(); val radii = ArrayList<FloatArray>()
        for (scale in floatArrayOf(0.6f, 1f, 1.8f)) {
            val s = PerfVault.small
            val v = PerfInput(s.noteIds, s.isPage, s.folderOfNote, s.folderIds, s.folderParent, s.edges, scale)
            val seed = PerfLayout.seed(PerfGraph(v))!!
            var e = 0f
            for (k in 0 until seed.positions.size / 3) e = max(e, PerfVault.length(seed.positions[3 * k], seed.positions[3 * k + 1], seed.positions[3 * k + 2]))
            extents.add(e); radii.add(seed.radius)
        }
        assertTrue("$extents", extents[0] < extents[1] && extents[1] < extents[2])
        assertArrayEquals(radii[0], radii[1], 0f)
        assertArrayEquals(radii[1], radii[2], 0f)
    }

    @Test fun inputOrderLinkDirectionDuplicatesAndUnknownEndsDoNotMatter() {
        val small = PerfVault.small
        val rng = PerfRandom(7)
        fun shuffled(count: Int): IntArray {
            val q = IntArray(count) { it }
            for (i in count - 1 downTo 1) { val j = rng.below(i + 1); val t = q[i]; q[i] = q[j]; q[j] = t }
            return q
        }
        val np = shuffled(small.noteIds.size); val fp = shuffled(small.folderIds.size)
        val newNote = IntArray(np.size); val newFolder = IntArray(fp.size)
        for ((k, i) in np.withIndex()) newNote[i] = k
        for ((k, f) in fp.withIndex()) newFolder[f] = k
        val edges = ArrayList<Int>()
        var k = small.edges.size - 2
        while (k >= 0) {
            val a = newNote[small.edges[k]]; val b = newNote[small.edges[k + 1]]
            if (k % 4 == 0) edges.addAll(listOf(b, a, a, b)) else edges.addAll(listOf(a, b))
            k -= 2
        }
        edges.addAll(listOf(np.size, 0, 3, 3, 1, -1)) // unknown ends and a self link
        val mixed = PerfInput(np.map { small.noteIds[it] }, BooleanArray(np.size) { small.isPage[np[it]] },
            IntArray(np.size) { val f = small.folderOfNote[np[it]]; if (f < 0) -1 else newFolder[f] },
            fp.map { small.folderIds[it] }, IntArray(fp.size) { val f = small.folderParent[fp[it]]; if (f < 0) -1 else newFolder[f] },
            edges.toIntArray())
        val g1 = PerfVault.smallGraph; val g2 = PerfGraph(mixed)
        assertEquals(g1.noteIds, g2.noteIds)
        assertArrayEquals(g1.edgeA, g2.edgeA); assertArrayEquals(g1.edgeB, g2.edgeB)
        assertArrayEquals(g1.folder, g2.folder); assertArrayEquals(g1.systemOfNote, g2.systemOfNote)
        assertArrayEquals("and so the layout is the same", PerfVault.smallSeed.positions, PerfLayout.seed(g2)!!.positions, 0f)
        // the input as the book holds it: ids, folder and parent ids, id links (an unknown id dropped)
        val byIds = PerfInput.of(small.noteIds, small.isPage, small.folderOfNote.map { if (it < 0) null else small.folderIds[it].lowercase() },
            small.folderIds, small.folderParent.map { if (it < 0) null else small.folderIds[it] },
            (0 until small.edges.size / 2).map { small.noteIds[small.edges[2 * it]] to small.noteIds[small.edges[2 * it + 1]] } + ("nowhere" to small.noteIds[0]))
        assertEquals(small.edges.size, byIds.edges.size)
        val g3 = PerfGraph(byIds)
        assertArrayEquals(g1.edgeA, g3.edgeA); assertArrayEquals(g1.folder, g3.folder)
        assertArrayEquals(intArrayOf(-1), PerfInput.of(listOf("a"), booleanArrayOf(true), listOf("missing"), emptyList(), emptyList(), emptyList()).folderOfNote)
    }

    @Test fun theGraphLinksOnceSymmetricAscendingInIdOrder() {
        assertEquals(PerfVault.input.edges.size / 2, g.linkCount)
        assertEquals(2 * g.linkCount, g.adjacency.size)
        for (i in 0 until n step 7) {
            for (e in g.adjStart[i] until g.adjStart[i + 1]) {
                val j = g.adjacency[e]
                assertTrue(j != i && (e == g.adjStart[i] || g.adjacency[e - 1] < j))
                assertTrue((g.adjStart[j] until g.adjStart[j + 1]).any { g.adjacency[it] == i })
            }
        }
        assertTrue((1 until n).all { g.noteIds[it - 1] < g.noteIds[it] })
        assertTrue("hubs grow", (0 until n).maxOf { g.degree(it) } > 100)
    }

    @Test fun aFolderCycleIsCutAtItsLowestSlotAndLooseNotesGroup() {
        val rng = PerfRandom(7)
        val fid = List(6) { rng.uuid() }
        val cyc = PerfGraph(PerfInput(listOf(rng.uuid()), booleanArrayOf(false), intArrayOf(5), fid, intArrayOf(1, 2, 0, 3, 99, 2), IntArray(0)))
        fun slot(f: Int) = cyc.inputOfFolder.indexOf(f)
        val cycle = listOf(0, 1, 2).map(::slot)
        val cutAt = cycle.min()
        assertEquals(-1, cyc.folderParent[cutAt])
        assertEquals(1, cycle.count { cyc.folderParent[it] == -1 })
        assertEquals(-1, cyc.folderParent[slot(3)]); assertEquals(-1, cyc.folderParent[slot(4)])
        assertEquals(cutAt, cyc.folderRegion[slot(5)])
        assertEquals(cyc.folderDepth[slot(2)] + 1, cyc.folderDepth[slot(5)])
        assertEquals(1, cyc.folderSubtreeCount[cutAt])
        // a triangle, a pair and three singletons (one linked to a foldered note)
        val lid = List(8) { rng.uuid() }
        val lg = PerfGraph(PerfInput(lid, BooleanArray(8), intArrayOf(-1, -1, -1, -1, -1, -1, -1, 0), listOf(rng.uuid()), intArrayOf(-1),
            intArrayOf(0, 1, 1, 2, 2, 0, 3, 4, 5, 7)))
        assertEquals(listOf(3, 2, 2), (lg.folderCount until lg.systemCount).map { lg.systemNoteStart[it + 1] - lg.systemNoteStart[it] })
        assertEquals(lg.systemOfNote[lg.noteOfInput[5]], lg.systemOfNote[lg.noteOfInput[6]])
        // 1,000 loose pairs: the 256 largest groups stay, the rest join the pool
        val pairs = PerfGraph(PerfInput(List(2_000) { rng.uuid() }, BooleanArray(2_000), IntArray(2_000) { -1 }, emptyList(), IntArray(0), IntArray(2_000) { it }))
        assertEquals(PerfGraph.MAX_LOOSE_GROUPS + 1, pairs.systemCount)
        assertEquals(2_000 - 512, pairs.systemNoteStart[pairs.systemCount] - pairs.systemNoteStart[pairs.systemCount - 1])
        assertEquals(6_000, PerfLayout.seed(pairs)!!.positions.size)
    }

    @Test fun anEmptyVaultBuilds() {
        val empty = PerfGraph(PerfInput(emptyList(), BooleanArray(0), IntArray(0), emptyList(), IntArray(0), IntArray(0)))
        assertEquals(0, empty.noteCount); assertEquals(0, empty.systemCount)
        val seed = PerfLayout.seed(empty)
        assertNotNull(seed)
        assertEquals(0, seed!!.positions.size)
        assertEquals(0, PerfMap(empty, seed).lines.size)
    }

    @Test fun aHubsRadiusGrowsWithTheLogOfItsNotes() {
        assertEquals(0.435f, PerfLayout.hubRadius(1), 1e-6f)
        assertEquals(0.915948f, PerfLayout.hubRadius(100), 1e-6f)
        assertEquals(1.2f, PerfLayout.hubRadius(10_000), 1e-6f)
        for (f in 0 until g.folderCount) assertEquals(PerfLayout.hubRadius(g.folderSubtreeCount[f]), l.radius[n + f], 0f)
        assertTrue((0 until n).all { l.radius[it] == if (g.isPage[it]) 0.28f else 0.18f })
        // hubs sit at their folders' centres
        for (f in 0 until g.folderCount) for (a in 0 until 3) assertEquals(l.systemCentre[3 * f + a], p[3 * (n + f) + a], 0f)
    }
}
