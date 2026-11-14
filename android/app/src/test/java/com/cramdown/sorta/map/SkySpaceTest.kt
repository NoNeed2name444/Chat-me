package com.cramdown.sorta.map

import com.cramdown.sorta.ui.ideas.ShareIntake
import com.cramdown.sorta.ui.ideas.SkyWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// S06b's Space wiring (SkySpace.of, LinkMemory's settle, the words footnote, a share's intake). Made-up ideas only.
class SkySpaceTest {
    private val day = CometTail.DAY_MS
    private val now = 1_000 * day

    private fun body(id: String, role: UniverseRole) =
        UniverseBody(id, role, -1, -1, 0.3f, 0, false, false, 0, -1, 0, 0, -1, GraphOrbit.Fixed(Vec3.ZERO), Vec3.ZERO, id, id)

    private val bodies = listOf(
        body("f1", UniverseRole.STAR),
        body("n1", UniverseRole.COMET),
        body("n2", UniverseRole.COMET),
        body("n3", UniverseRole.OORT),
        body("n4", UniverseRole.COMET),
    )

    private val ideas = listOf(
        SkySpace.Idea("N1", "Water the garden path", "", null, now - 60_000, null),
        SkySpace.Idea("N2", "Bike paths map", "", null, now - 3 * day, "example.org"),
        SkySpace.Idea("N3", "Night ride route", "", null, now - 40 * day, "Example.org"),
        SkySpace.Idea("N4", "Oil the gate", "", null, now - 10 * day, null),
    )

    private val sky = SkySpace.of(bodies, ideas, listOf(Leaning.Folder("F1", "Raised beds", listOf("garden"))), emptyList(), now)

    // A1
    @Test fun eachLooseIdeaGetsItsTailByAgeAndOnlyCometsAreComets() {
        assertEquals(listOf(0f, 0.35f, 0.6f, 1f, 0.8f), sky.tails.toList())
        assertEquals(setOf("N1", "N2", "N4"), sky.comets)
    }

    // A4
    @Test fun aLooseIdeaLeansToTheFolderWhoseWordItShares() {
        assertEquals(listOf(-1, 0, -1, -1, -1), sky.leanTo.toList())
        val none = SkySpace.of(bodies, ideas, listOf(Leaning.Folder("F1", "Raised beds", emptyList())), emptyList(), now)
        assertTrue("a folder with no words draws no one", none.leanTo.all { it == -1 })
    }

    // A8
    @Test fun ideasSharedFromOnePlaceCircleTheFirst() {
        assertEquals("the first leads, so only the second circles", setOf(3), sky.circles.keys)
        assertEquals(SharedCircle.Place(leader = 2, slot = 0, of = 1), sky.circles.getValue(3))
        assertNull("an unmarked idea circles nothing", sky.circles[4])
    }

    // A2
    @Test fun theSettleStartsOnlyCometsThatWereFiled() {
        val places = mapOf("A" to Vec3(1f, 2f, 3f), "B" to Vec3(4f, 5f, 6f))
        val settling = LinkMemory.settling(setOf("A", "B"), setOf("B"), listOf("A", "B", "C"), places::get)
        assertEquals(mapOf("A" to Vec3(1f, 2f, 3f)), settling)
        assertEquals("no scene before, nothing settles", emptyMap<String, Vec3>(),
            LinkMemory.settling(emptySet(), emptySet(), listOf("A"), places::get))
        assertEquals("a comet deleted, not filed, has no place", emptyMap<String, Vec3>(),
            LinkMemory.settling(setOf("A"), emptySet(), listOf("B"), places::get))
    }

    // A3
    @Test fun theWordsFootnoteSaysWhatItSaves() {
        val note = "In Space, ideas in To sort that use one of these words lean towards this folder."
        assertEquals(note, SkyWords.folderWordsFootnote("  ", note, "Saves: %1\$s"))
        assertEquals("$note\nSaves: garden, soil", SkyWords.folderWordsFootnote("garden soil garden", note, "Saves: %1\$s"))
    }

    // A7
    @Test fun aShareKeepsItsTextAndMarksWhereItCameFrom() {
        assertNull(ShareIntake.of("   ", "Notes"))
        assertEquals(ShareIntake("Bike paths https://www.example.org/paths", "example.org"),
            ShareIntake.of("  Bike paths https://www.example.org/paths ", "Notes"))
        assertEquals("Notes", ShareIntake.of("Bike paths", "Notes")?.mark)
        assertNull(ShareIntake.of("Bike paths", null)?.mark)
        assertEquals(4_000, ShareIntake.of("a".repeat(5_000), null)?.text?.length)
    }
}
