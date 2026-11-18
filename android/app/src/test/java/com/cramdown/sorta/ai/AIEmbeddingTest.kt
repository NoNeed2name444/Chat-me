package com.cramdown.sorta.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// S08 A7 on the JVM: the 50 shared texts (token ids exact, vectors within the fixture's tolerance of
// ai/embeddings/reference.py), the top 5 neighbours in the fixture library, the Arabic fallback to IdeaEchoes, the
// index off the main thread and back from its file, and suggested links and resurfacing. iOS's AIEmbeddingTests
// checks the same fixtures.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AIEmbeddingTest {
    companion object {
        lateinit var embeddings: AIEmbeddings

        @BeforeClass @JvmStatic fun load() { embeddings = AIEmbeddings.load(AIRepo.files, "embeddings/potion-base-4M") }
    }

    @get:Rule val temp = TemporaryFolder()

    private fun floats(v: DoubleArray?) = v?.let { d -> FloatArray(d.size) { d[it].toFloat() } }
    private val spec = AIRepo.fixture("embeddings/texts.json")
    private val wanted = AIRepo.fixture("embeddings/library.json")
    private val library get() = spec["library"]!!.array!!.map { AINeighbour(it["id"]!!.string!!, it["text"]!!.string!!, 0.0, floats(embeddings.vector(it["text"]!!.string!!))) }

    @Test fun theTableAndItsLicence() {
        val dir = File(AIRepo.ai, "embeddings/potion-base-4M")
        assertTrue(File(dir, AIEmbeddings.TABLE).length() < 5_000_000)
        val licence = File(dir, "LICENSE").readText()
        assertTrue(licence.startsWith("MIT License") && licence.contains("Thomas van Dongen"))
        assertEquals(29_528, embeddings.rows)
        assertEquals(128, embeddings.dims)
    }

    @Test fun theFiftySharedTextsGiveTheReferencesIdsAndVectors() {
        val vectors = AIRepo.fixture("embeddings/vectors.json")
        val tolerance = vectors["tolerance"]!!.number!!
        val all = vectors["vectors"]!!.array!!
        assertEquals(50, all.size)
        for ((k, e) in all.withIndex()) {
            val text = e["text"]!!.string!!
            val ids = embeddings.tokenIds(text)
            assertEquals("text ${k + 1}", e["ids"]!!.array!!.map { it.int!! }, ids)
            assertEquals("text ${k + 1} covered", e["covered"]!!.bool!!, AIEmbeddings.covered(text))
            val v = embeddings.vector(ids)
            val w = e["vector"]?.array?.map { it.number!! }
            if (w != null && v != null) {
                assertEquals(128, v.size)
                val d = v.indices.maxOf { Math.abs(v[it] - w[it]) }
                assertTrue("text ${k + 1}: $d", d <= tolerance)
            } else {
                assertTrue("text ${k + 1}: no vector on both", v == null && e["vector"]!!.isNull)
            }
            if (!e["covered"]!!.bool!!) assertNull(embeddings.vector(text))
        }
    }

    @Test fun theTopFiveNeighboursInTheLibrary() {
        val lib = library
        assertTrue(lib.all { it.vector != null } && lib.size == 30)
        for (q in wanted["queries"]!!.array!!) {
            val text = q["text"]!!.string!!
            val qv = floats(embeddings.vector(text))!!
            val top = AILinks.byMeaning(qv, null, 1.0, lib, emptySet(), 5, -2.0)
            assertEquals(text, q["top5"]!!.array!!.map { it.string!! }, top)
            val scores = top.map { id -> AILinks.cosine(qv, lib.first { it.id == id }.vector!!) }
            val want = q["scores"]!!.array!!.map { it.number!! }
            assertTrue(text, scores.indices.all { Math.abs(scores[it] - want[it]) <= 1e-4 })
        }
    }

    private val arabic get() = spec["arabic"]!!
    private val arabicIdeas get() = arabic["ideas"]!!.array!!.map {
        AINeighbour(it["id"]!!.string!!, it["text"]!!.string!!, it["made"]!!.number!!, floats(embeddings.vector(it["text"]!!.string!!)))
    }

    @Test fun arabicFallsBackToIdeaEchoes() {
        val query = arabic["query"]!!.string!!
        assertFalse(AIEmbeddings.covered(query))
        assertNull(embeddings.vector(query))
        assertTrue(arabicIdeas.all { it.vector == null })
        val links = AILinks.suggest(query, null, null, arabic["made"]!!.number!!, arabicIdeas)
        assertEquals(wanted["arabic"]!!["echoes"]!!.array!!.map { it.string!! }, links)
        assertEquals(listOf("A3", "A4", "A1"), links)
    }

    @Test fun theIndexLinksAndResurfacing() {
        val ideas = spec["library"]!!.array!!.map { AIIdea(it["id"]!!.string!!, "", it["text"]!!.string!!) } +
            arabic["ideas"]!!.array!!.map { AIIdea(it["id"]!!.string!!, "", it["text"]!!.string!!) }
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { AIIndex.build(ideas, embeddings) })
        val index = AIIndex(File(temp.root, AIIndex.NAME))
        val built = offMain { index.rebuild(ideas, embeddings) }
        assertEquals(34, built.size)
        assertEquals(listOf("A1", "A2", "A3", "A4"), built.filter { it.vector == null }.map { it.id })
        assertEquals(built, index.load())
        assertEquals("SRTI", String(index.file.readBytes(), 0, 4, Charsets.US_ASCII))
        assertNull(AIIndex(File(temp.root, "x.bin")).load())

        val byId = built.associate { it.id to it.vector }
        var neighbours = library.mapIndexed { k, n -> n.copy(made = (100 + k).toDouble(), vector = byId[n.id]) }
        val seed = "seedlings for the vegetable patch"
        val sv = floats(embeddings.vector(seed))!!
        val links = AILinks.suggest(seed, sv, null, 1000.0, neighbours)
        assertEquals(listOf("L02", "L12"), links.take(2))
        assertTrue(links.size <= 5 && links.toSet().size == links.size)
        assertFalse(AILinks.byMeaning(sv, null, 1000.0, neighbours, emptySet(), 5).contains("L01"))
        val newer = AILinks.suggest(seed, sv, null, 110.0, neighbours)
        assertTrue("only ideas made before", !newer.contains("L12") && newer.first() == "L02")
        assertTrue(AILinks.suggest(seed, sv, "L02", 1000.0, neighbours, setOf("L12")).none { it == "L02" || it == "L12" })

        val now = 1_000_000.0
        neighbours = neighbours.map { it.copy(made = now - 3 * 86400 + it.made) }
        val recent = arrayListOf(AINeighbour("R1", seed, now - 3600, sv))
        assertEquals(listOf("L02", "L12"), AIResurface.pick(neighbours + recent, now))
        assertTrue(AIResurface.pick(neighbours, now).isEmpty())
        val olderArabic = arabicIdeas.map { it.copy(made = now - 2 * 86400) }
        recent.add(AINeighbour("R2", arabic["query"]!!.string!!, now - 60, null))
        val picked = AIResurface.pick(neighbours + olderArabic + recent, now)
        assertTrue("$picked", picked.size == 3 && picked.take(2) == listOf("L02", "L12") && picked[2] in listOf("A3", "A4", "A1"))
    }
}
