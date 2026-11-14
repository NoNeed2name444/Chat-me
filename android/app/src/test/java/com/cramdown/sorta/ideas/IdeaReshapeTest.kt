package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.MapReshape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// S06c A3-A6: collide's pick, split's sentences and cut, join, their links, and the two-finger rules, on
// the cases iOS's IdeaShapeTests reads (IdeaShapeCases.json).
class IdeaReshapeTest {
    private val cases = shapeCases()

    @Test fun collidePicksOnlyFarPairsAndNeverTheLast() {
        for (c in cases.getJSONArray("collide").objects()) {
            val name = c.getString("name")
            val ideas = (0 until c.getJSONArray("ideas").length()).map {
                val row = c.getJSONArray("ideas").getJSONArray(it)
                IdeaReshape.Place(row.getString(0), if (row.isNull(1)) null else row.getString(1))
            }
            val links = (0 until c.getJSONArray("links").length()).map { c.getJSONArray("links").getJSONArray(it).strings() }
            val last = if (c.isNull("last")) null else c.getJSONArray("last").strings().let { it[0] to it[1] }
            val allowed = (0 until c.getJSONArray("allowed").length()).map { c.getJSONArray("allowed").getJSONArray(it).strings().sorted().joinToString("+") }.toSet()
            val random = Random(7)
            val seen = HashSet<String>()
            repeat(200) {
                val got = IdeaReshape.collide(ideas, { a, b -> links.any { l -> l == listOf(a, b) || l == listOf(b, a) } }, last, random)
                if (got == null) assertTrue("$name: none, though pairs are allowed", allowed.isEmpty())
                else {
                    val key = listOf(got.first, got.second).sorted().joinToString("+")
                    assertTrue("$name: $key is not allowed", key in allowed)
                    seen.add(key)
                }
            }
            assertEquals("$name: every allowed pair comes up", allowed, seen)
        }
        val many = (0 until 5000).map { IdeaReshape.Place(it.toString(), if (it % 7 == 0) null else (it % 5).toString()) }
        val t0 = System.nanoTime()
        assertNotNull(IdeaReshape.collide(many, { _, _ -> false }, null, Random(3)))
        assertTrue("5,000 ideas pick at once", (System.nanoTime() - t0) / 1e6 < 50)
    }

    @Test fun sentencesAsIosReadsThem() {
        for (c in cases.getJSONArray("sentences").objects()) {
            val want = (0 until c.getJSONArray("want").length()).map { c.getJSONArray("want").getJSONArray(it).let { r -> r.getString(0) to r.getBoolean(1) } }
            val got = IdeaReshape.sentences(c.getString("title"), c.getString("body")).map { it.text to it.inTitle }
            assertEquals("${c.getString("title")} / ${c.getString("body")}", want, got)
        }
        for (c in cases.getJSONArray("headings").objects()) assertEquals(c.getString("line"), c.getString("want"), IdeaReshape.heading(c.getString("line")))
    }

    @Test fun cutsAsIosCutsThem() {
        for (c in cases.getJSONArray("cuts").objects()) {
            val got = IdeaReshape.cut(c.getString("title"), c.getString("body"), c.getInt("after"))
            val label = "cut after ${c.getInt("after")}: ${c.getString("title")}"
            if (c.isNull("want")) assertNull(label, got)
            else {
                val w = c.getJSONObject("want")
                assertEquals(label, IdeaReshape.Halves(w.getString("firstTitle"), w.getString("firstBody"), w.getString("secondTitle"),
                    w.getString("secondBody"), w.getBoolean("titleChanged"), w.getBoolean("titleFromBody")), got)
            }
        }
    }

    @Test fun joinAndLinksAsIosMakesThem() {
        for (c in cases.getJSONArray("joined").objects()) {
            val a = c.getJSONArray("first").strings(); val b = c.getJSONArray("second").strings(); val w = c.getJSONArray("want").strings()
            assertEquals("$a + $b", w[0] to w[1], IdeaReshape.joined(a[0], a[1], b[0], b[1]))
        }
        for (c in cases.getJSONArray("splitLinks").objects()) {
            val got = IdeaReshape.splitLinks(c.getJSONArray("own").strings(), c.getJSONArray("heldBy").strings(), c.getJSONArray("namedBy").strings(),
                c.getBoolean("titleChanged"), c.getJSONArray("secondTitleNames").strings(), c.getString("first"), c.getString("second"))
            val want = c.getJSONArray("want")
            assertEquals("split links", want.getJSONArray(0).strings() to want.getJSONArray(1).strings(), got)
        }
        for (c in cases.getJSONArray("joinLinks").objects()) {
            val got = IdeaReshape.joinLinks(c.getString("first"), c.getString("second"), c.getJSONArray("firstOwn").strings(),
                c.getJSONArray("secondOwn").strings(), c.getJSONArray("pointedAtSecond").strings(), c.getJSONArray("connected").strings().toSet())
            assertEquals("join links: no link twice, never the two", c.getJSONArray("want").strings(), got)
        }
    }

    @Test fun twoFingersAsIosJudgesThem() {
        fun describe(s: MapReshape.Start) = when (s) {
            is MapReshape.Start.Split -> "split ${s.a}"
            is MapReshape.Start.Join -> "join ${s.a} ${s.b}"
            MapReshape.Start.None -> "none"
        }
        fun opt(o: org.json.JSONObject, k: String) = if (o.isNull(k)) null else o.getInt(k)
        for (c in cases.getJSONArray("touchStart").objects()) {
            assertEquals("start at ${c.getDouble("apart")}", c.getString("want"),
                describe(MapReshape.start(opt(c, "first"), opt(c, "second"), opt(c, "middle"), c.getDouble("apart"))))
        }
        for (c in cases.getJSONArray("touchJudge").objects()) {
            val start = when (c.getString("start")) { "split" -> MapReshape.Start.Split(0); "join" -> MapReshape.Start.Join(0, 1); else -> MapReshape.Start.None }
            assertEquals("${c.getString("start")} from ${c.getDouble("from")} to ${c.getDouble("now")}", c.getString("want"),
                MapReshape.judge(start, c.getDouble("from"), c.getDouble("now")).name.lowercase())
        }
    }
}
