package com.cramdown.sorta.ideas

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The repository root, found from wherever the test runs (WardPaletteTest's way). */
internal fun repositoryRoot(): File {
    var root: File? = File(System.getProperty("user.dir")).absoluteFile
    while (root != null && !File(root, "ios/Sorta").exists()) root = root.parentFile
    return checkNotNull(root) { "No ios/Sorta above ${System.getProperty("user.dir")}" }
}

/** S06c's shared cases, the file iOS's IdeaShapeTests reads too. */
internal fun shapeCases(): JSONObject = JSONObject(File(repositoryRoot(), "ios/UnitTests/Suites/IdeaShapeCases.json").readText())

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

// S06c A1: the echoes matcher on the cases iOS reads (IdeaShapeCases.json), one stop-word list for both
// (read from IdeaEchoes.swift), and 5,000 made-up ideas against the clock.
class IdeaEchoesTest {
    private val cases = shapeCases()

    @Test fun wordsAsIosReadsThem() {
        for (c in cases.getJSONArray("words").objects()) {
            assertEquals(c.getString("text"), c.getJSONArray("want").strings(), IdeaEchoes.words(c.getString("text")))
        }
    }

    @Test fun oneStopWordListForBoth() {
        val swift = File(repositoryRoot(), "ios/Sorta/Ideas/IdeaEchoes.swift").readText()
        val block = swift.substringAfter("// STOP-WORDS-BEGIN").substringBefore("// STOP-WORDS-END")
        val ios = Regex("\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toSet()
        assertTrue("iOS's list is found", ios.size > 100)
        assertEquals("the same stop words as iOS", ios, IdeaEchoes.stopWords)
        assertTrue("no stop word under 3 letters", IdeaEchoes.stopWords.all { it.length >= 3 })
    }

    @Test fun echoesAsIosFindsThem() {
        for (c in cases.getJSONArray("echoes").objects()) {
            val ideas = c.getJSONArray("ideas").objects().map { IdeaEchoes.Idea(it.getString("id"), it.getString("text"), it.getDouble("made")) }
            val got = IdeaEchoes.Index(ideas).echoes(c.getString("text"), if (c.isNull("current")) null else c.getString("current"),
                c.getDouble("made"), c.getJSONArray("connected").strings().toSet())
            assertEquals(c.getString("name"), c.getJSONArray("want").strings(), got)
        }
    }

    @Test fun fiveThousandIdeasAnswerWhileTyping() {
        val syllables = listOf("ka", "lo", "mi", "ren", "tu", "sa", "vo", "pel", "dri", "nu", "gar", "fen", "bo", "lis", "tam")
        val random = Random(42)
        val ideas = (0 until 5000).map { i ->
            val words = (0 until 12).joinToString(" ") { syllables[random.nextInt(syllables.size)] + syllables[random.nextInt(syllables.size)] + syllables[random.nextInt(syllables.size)] }
            IdeaEchoes.Idea(i.toString(), "$words and the garden", i.toDouble())
        }
        val index = IdeaEchoes.Index(ideas)
        // the first asks warm the code up, as typing's first keystrokes would
        repeat(5) { index.echoes(ideas[it].text, null, 1e9) }
        var slowest = 0.0
        var found = 0
        for (k in 0 until 20) {
            val t0 = System.nanoTime()
            found += index.echoes(ideas[(k * 251) % 5000].text + " then a little more typed here", null, 1e9).size
            slowest = maxOf(slowest, (System.nanoTime() - t0) / 1e6)
        }
        println("5,000 ideas: slowest of 20 asks %.2f ms".format(slowest))
        assertTrue("each ask under 50 ms: $slowest ms", slowest < 50)
        assertTrue("the 5,000 find their echoes", found > 0)
    }
}
