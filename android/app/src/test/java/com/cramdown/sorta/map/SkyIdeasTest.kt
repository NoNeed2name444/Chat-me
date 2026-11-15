package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

// S06b's rules (SkyIdeas.kt), as iOS's SkyIdeasTests checks them: the tail's steps, the settle, a
// folder's words, leaning, the moons and a share's source. Made-up ideas only.
class SkyIdeasTest {
    private val day = CometTail.DAY_MS

    // A1
    @Test fun theTailGrowsInCappedSteps() {
        assertEquals(0.35f, CometTail.length(60_000L))
        assertEquals(0.35f, CometTail.length(day - 1))
        assertEquals(0.6f, CometTail.length(day))
        assertEquals(0.8f, CometTail.length(7 * day))
        assertEquals(1f, CometTail.length(30 * day))
        assertEquals("never past the tail Space always drew", 1f, CometTail.length(3_650 * day))
        assertEquals("a clock put back counts as new", 0.35f, CometTail.length(-day))
        val lengths = listOf(0L, day, 3 * day, 8 * day, 40 * day).map { CometTail.length(it) }
        assertEquals("older is never shorter", lengths.sorted(), lengths)
    }

    // A2
    @Test fun theSettleEasesFromWhereItWas() {
        val from = Vec3(10f, 0f, 0f)
        assertEquals(from, CometSettle.place(from, Vec3.ZERO, 0.0))
        assertEquals(Vec3.ZERO, CometSettle.place(from, Vec3.ZERO, CometSettle.DURATION))
        assertEquals(Vec3.ZERO, CometSettle.place(from, Vec3.ZERO, 5.0))
        assertTrue("fast, then slow", CometSettle.progress(0.3) > 0.5f)
        var last = 0f
        for (k in 0..18) {
            val p = CometSettle.progress(k * 0.05)
            assertTrue(p >= last)
            last = p
        }
    }

    // A3
    @Test fun aFolderKeepsFiveSingleWords() {
        assertEquals(listOf("garden", "soil", "compost"), FolderWords.parse("garden, soil  compost"))
        assertEquals(listOf("a", "b", "c", "d", "e"), FolderWords.parse("a b c d e f g"))
        assertEquals(listOf("Garden", "beds"), FolderWords.parse("Garden garden GARDÉN beds"))
        assertEquals(listOf("raised", "beds"), FolderWords.parse("raised-beds"))
        assertEquals(emptyList<String>(), FolderWords.parse("  "))
    }

    // A4
    @Test fun leaningMatchesWholeWordsWithoutCaseOrAccents() {
        val beds = Leaning.Folder("f1", "Raised beds", listOf("garden"))
        val cafe = Leaning.Folder("f2", "Corner shop", listOf("café"))
        assertEquals("f1", Leaning.choose("Garden path", "", listOf(beds), emptyMap())?.folder)
        assertEquals("café", Leaning.choose("Cafe sign", "", listOf(cafe), emptyMap())?.word)
        assertNull("a whole word only", Leaning.choose("Gardening gloves", "", listOf(beds), emptyMap()))
        assertEquals("the text counts too", "f1", Leaning.choose("", "Weed the GARDEN.", listOf(beds), emptyMap())?.folder)
    }

    @Test fun tiesGoToMoreWordsThenMoreLinks() {
        val one = Leaning.Folder("f1", "Bees", listOf("garden"))
        val two = Leaning.Folder("f2", "Raised beds", listOf("garden", "soil"))
        val three = Leaning.Folder("f3", "Allotment", listOf("garden"))
        assertEquals(Leaning.Lean("f2", "garden", 2), Leaning.choose("Garden soil", "", listOf(one, two), mapOf("f1" to 9)))
        assertEquals("f3", Leaning.choose("Garden", "", listOf(one, three), mapOf("f3" to 2))?.folder)
        assertEquals("then by name", "f3", Leaning.choose("Garden", "", listOf(one, three), emptyMap())?.folder)
    }

    // A4: a leaning comet is drawn part of the way to its folder, never inside the folder's disc,
    // where a tap on it would choose the folder (the iOS ideas red on Chat-me run 43610223855)
    @Test fun aLeaningCometStopsOutsideItsFolder() {
        val folder = Vec3(0f, 0f, 0f)
        near("far off: the pull alone", Vec3(7f, 0f, 0f), Leaning.place(Vec3(10f, 0f, 0f), folder, 1f))
        near("close: kept three radii out, on its own side", Vec3(0f, 3f, 0f), Leaning.place(Vec3(0f, 2f, 0f), folder, 1f))
        near("inside: pushed out", Vec3(-6f, 0f, 0f), Leaning.place(Vec3(-0.5f, 0f, 0f), folder, 2f))
        near("on top of it: out along x", Vec3(3f, 0f, 0f), Leaning.place(folder, folder, 1f))
        near("no size, no keep-out", Vec3(0.7f, 0f, 0f), Leaning.place(Vec3(1f, 0f, 0f), folder, 0f))
        for (k in 0 until 50) {
            val own = Vec3((k % 7) - 3f, (k % 5) - 2f, (k % 3) - 1f) * 0.4f
            val at = Leaning.place(own, Vec3(1f, 1f, 1f), 0.5f)
            assertTrue("never within 3 radii: $own -> $at", (at - Vec3(1f, 1f, 1f)).length() >= 1.5f - 1e-4f)
        }
    }

    private fun near(what: String, want: Vec3, got: Vec3) =
        assertTrue("$what: want $want, got $got", (got - want).length() < 1e-5f)

    @Test fun onlyLooseIdeasLeanAndNothingIsStored() {
        val folders = listOf(Leaning.Folder("f1", "Raised beds", listOf("garden")))
        val notes = listOf(
            Leaning.LooseNote("n10", "Garden gate", "", null),
            Leaning.LooseNote("n11", "Garden fence", "", "f1"),
            Leaning.LooseNote("n12", "Bread", "", null),
        )
        val leans = Leaning.leans(notes, folders, emptyList())
        assertEquals(setOf("n10"), leans.keys)
        assertEquals("the same every time", leans, Leaning.leans(notes, folders, emptyList()))
        // a link to an idea in a folder counts for that folder
        val two = folders + Leaning.Folder("f0", "Allotment", listOf("garden"))
        val linked = Leaning.leans(notes + Leaning.LooseNote("n13", "Shed", "", "f1"), two, listOf("n10" to "n13"))
        assertEquals("f1", linked["n10"]?.folder)
    }

    // A5, A6
    @Test fun moonsAreAsFullAsTheDayWasBusy() {
        val zone = ZoneId.of("UTC")
        val now = 1_793_000_000_000L
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val made = listOf(today + 60_000, today + 120_000, today + 180_000, today + 240_000,
            today - day + 60_000, today - 3 * day + 10_000, today - 3 * day + 20_000)
        val days = MoonDays.days(made, now, 5, zone)
        assertEquals(listOf(4, 1, 0, 2, 0), days.map { it.count })
        assertEquals(listOf(3, 1, 0, 2, 0), days.map { it.phase })
        assertEquals(listOf(0, 0, 0), MoonDays.days(emptyList(), now, 3, zone).map { it.phase })
        assertEquals(MoonDays.MOST, MoonDays.days(emptyList(), now, 500, zone).size)
        assertEquals(setOf("a"), MoonDays.lit(listOf("a" to today + 5, "b" to today - day), days[0].date, zone))
    }

    // A7, A8
    @Test fun aSharedIdeaKeepsOnlyTheHostOrTheApp() {
        assertEquals("example.org", SharedFrom.mark("Look https://www.Example.org/a/b?c=1 now", "Notes"))
        assertEquals("Notes", SharedFrom.mark("Plain words", " Notes "))
        assertNull(SharedFrom.mark("Plain words", null))
        assertEquals("Keep words", SharedFrom.kept(SharedFrom.words("Keep these words only"), setOf(0, 2)))
    }

    @Test fun sharedIdeasCircleTheFirstOfThem() {
        val groups = SharedCircle.groups(mapOf(3 to "example.org", 7 to "Example.org", 9 to "other.net", 12 to "example.org"))
        assertEquals(SharedCircle.Place(3, 0, 2), groups[7])
        assertEquals(SharedCircle.Place(3, 1, 2), groups[12])
        assertNull("the first stays on its own path", groups[3])
        assertNull("alone, it circles nothing", groups[9])
        val a = SharedCircle.offset(SharedCircle.Place(0, 0, 2), 2f, 0.0)
        val b = SharedCircle.offset(SharedCircle.Place(0, 1, 2), 2f, 0.0)
        assertEquals(2f, a.length(), 1e-4f)
        assertEquals("a pair sits opposite", 0f, (a + b).length(), 1e-4f)
    }
}
