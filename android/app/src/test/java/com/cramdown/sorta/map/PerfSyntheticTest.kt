package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

// The synthetic vault (S05i2), iOS's P1 (GraphPerfTests.swift:57-90): 100,000 notes in 24 top folders,
// shaped like a real vault, the same for the same seed. Made-up words, nothing medical.
class PerfSyntheticTest {
    private val input = PerfVault.input
    private val n = input.noteIds.size

    @Test fun aHundredThousandNotesEachWithAKindAndAFolder() {
        assertEquals(100_000, n)
        assertEquals(n, input.isPage.size)
        assertEquals(n, input.folderOfNote.size)
    }

    @Test fun twentyFourTopFoldersAmongMoreThanFiveHundred() {
        assertEquals(24, input.folderParent.count { it < 0 })
        assertTrue("${input.folderIds.size}", input.folderIds.size > 500)
    }

    @Test fun foldersAtMostThreeDeepEachParentMadeFirst() {
        for (f in input.folderParent.indices) {
            var d = 0; var cur = input.folderParent[f]
            while (cur >= 0) { d++; assertTrue("folder $f: parent $cur made after it", cur < f); cur = input.folderParent[cur] }
            assertTrue("folder $f is $d deep", d <= 3)
        }
    }

    @Test fun aboutFifteenPercentPagesAndTwoPercentLoose() {
        val pages = input.isPage.count { it } / n.toDouble()
        val loose = input.folderOfNote.count { it < 0 } / n.toDouble()
        assertTrue("$pages", abs(pages - 0.15) < 0.01)
        assertTrue("$loose", abs(loose - 0.02) < 0.005)
    }

    @Test fun aboutTwoPointThreeLinksANoteNoSelfLinksNoDuplicates() {
        val links = input.edges.size / 2
        assertTrue("$links", links / n.toDouble() in 2.0..2.5)
        val seen = HashSet<Long>()
        var bad = 0
        for (k in 0 until links) {
            val a = input.edges[2 * k].toLong(); val b = input.edges[2 * k + 1].toLong()
            if (a == b || !seen.add((min(a, b) shl 32) or maxOf(a, b))) bad++
        }
        assertEquals(0, bad)
    }

    @Test fun uniqueVersionFourIds() {
        assertEquals(n, input.noteIds.toHashSet().size)
        assertEquals(input.folderIds.size, input.folderIds.toHashSet().size)
        val shape = Regex("[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}")
        assertTrue(input.noteIds.take(1_000).all { shape.matches(it) })
    }

    @Test fun theSameSeedGivesTheSameVaultAnotherSeedAnother() {
        val again = PerfSynthetic.make()
        assertTrue(again.edges.contentEquals(input.edges))
        assertEquals(input.noteIds, again.noteIds)
        assertTrue(again.folderOfNote.contentEquals(input.folderOfNote))
        val other = PerfSynthetic.make(PerfSynthetic.Spec(seed = PerfSynthetic.Spec().seed + 1))
        assertTrue(!other.edges.contentEquals(input.edges))
    }

    @Test fun titlesAreMadeOnDemand() {
        assertTrue(PerfSynthetic.title(4821).endsWith(" 04821"))
        assertNotEquals(PerfSynthetic.title(1).substringBefore(' '), PerfSynthetic.title(2).substringBefore(' '))
    }

    @Test fun theRandomStreamIsSplitMix64() {
        // SplitMix64 from 0: its published first outputs
        val r = PerfRandom(0)
        assertEquals(0xE220_A839_7B1D_CDAFuL.toLong(), r.next())
        assertEquals(0x6E78_9E6A_A1B9_65F4uL.toLong(), r.next())
        val u = PerfRandom(3)
        repeat(1_000) { val x = u.unit(); assertTrue(x >= 0 && x < 1); assertTrue(u.below(7) in 0 until 7) }
    }
}

/** The vaults the Performance tests share: iOS's 100,000, its 4,000-note small one, and their layouts. */
internal object PerfVault {
    val input: PerfInput by lazy { PerfSynthetic.make() }
    val graph: PerfGraph by lazy { PerfGraph(input) }
    val seed: PerfSeed by lazy { PerfLayout.seed(graph)!! }
    val small: PerfInput by lazy { PerfSynthetic.make(PerfSynthetic.Spec(notes = 4_000, topFolders = 6, seed = 11)) }
    val smallGraph: PerfGraph by lazy { PerfGraph(small) }
    val smallSeed: PerfSeed by lazy { PerfLayout.seed(smallGraph)!! }

    fun length(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    /** Each point's nearest other point's distance, the least of them all (a hashed grid of [cell]). */
    fun nearestSpacing(xyz: FloatArray, count: Int, cell: Float, stride: Int = 3): Float {
        val grid = HashMap<Long, MutableList<Int>>()
        fun c(v: Float) = floor(v / cell).toLong()
        fun key(x: Long, y: Long, z: Long) = ((x + 1_000_000) * 2_000_003 + (y + 1_000_000)) * 2_000_003 + (z + 1_000_000)
        for (i in 0 until count) grid.getOrPut(key(c(xyz[stride * i]), c(xyz[stride * i + 1]), c(xyz[stride * i + 2]))) { ArrayList() }.add(i)
        var least = Float.POSITIVE_INFINITY
        for (i in 0 until count) {
            val x = xyz[stride * i]; val y = xyz[stride * i + 1]; val z = xyz[stride * i + 2]
            val cx = c(x); val cy = c(y); val cz = c(z)
            for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
                val list = grid[key(cx + dx, cy + dy, cz + dz)] ?: continue
                for (j in list) if (j != i) least = min(least, length(x - xyz[stride * j], y - xyz[stride * j + 1], z - xyz[stride * j + 2]))
            }
        }
        return least
    }
}
