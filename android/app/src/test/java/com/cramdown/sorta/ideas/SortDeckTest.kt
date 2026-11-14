package com.cramdown.sorta.ideas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sorting deck's, "You were saying…"'s and the tides' rules (SortDeck.kt, S06a), with the same cases as iOS's SortDeckTests. */
class SortDeckTest {
    private val now = 1_793_000_000_000L
    private fun daysAgo(d: Double): Long = now - (d * IdeaFrost.DAY).toLong()

    @Test fun stoppedAtTheSharedCases() {
        val cases = listOf<Triple<String, Int?, Int?>>(
            Triple("", null, null),
            Triple("   \n", null, null),
            Triple("Fix the gate latch.", null, null),
            Triple("Fix the gate latch", null, 18),
            Triple("Fix the gate latch", 4, 4),
            Triple("Really?", null, null),
            Triple("Wait…", null, null),
            Triple("He said “go.”", null, null),
            Triple("(see the shed.)", null, null),
            Triple("Two coats, after the rain. Then", null, 31),
            Triple("Done!  \n", null, null),
            Triple("Buy brushes", 99, 11),
            Triple("Buy brushes", -3, 0),
            Triple("パンを焼く。", null, null),
            Triple("متى؟", null, null),
            Triple("Plant tulips 🌷", null, 15),
            Triple("Bread rises,", 5, 5),
        )
        for ((body, caret, want) in cases) assertEquals("\"$body\" caret $caret", want, IdeaStops.stoppedAt(body, caret))
    }

    @Test fun theOfferIsTheMarkedIdeaChangedLast() {
        assertNull(IdeaStops.offer(listOf(IdeaStops.Mark("a", null, now))))
        assertEquals("c", IdeaStops.offer(listOf(IdeaStops.Mark("a", 3, now), IdeaStops.Mark("b", null, now + 60_000),
            IdeaStops.Mark("c", 0, now + 30_000))))
        assertEquals("a", IdeaStops.offer(listOf(IdeaStops.Mark("a", 3, now), IdeaStops.Mark("b", 1, now))))
    }

    @Test fun frostSteps() {
        val cases = listOf<Triple<Double?, Double, Int>>(
            Triple(null, 0.0, 0), Triple(null, 29.9, 0), Triple(null, 30.0, 1), Triple(null, 89.0, 1), Triple(null, 90.0, 2),
            Triple(null, 179.0, 2), Triple(null, 180.0, 3), Triple(null, 400.0, 3), Triple(10.0, 200.0, 0), Triple(95.0, 300.0, 2),
        )
        for ((opened, made, want) in cases) assertEquals("opened $opened, made $made", want, IdeaFrost.level(opened?.let(::daysAgo), daysAgo(made), now))
    }

    @Test fun theDecksOrder() {
        val deck = SortDeck<String>()
        deck.sync(listOf("c", "b", "a"))
        assertEquals(listOf("c", "b", "a"), deck.order)
        deck.later("c")
        assertEquals(listOf("b", "a", "c"), deck.order)
        deck.sync(listOf("d", "c", "b", "a"))
        assertEquals(listOf("d", "b", "a", "c"), deck.order)
        deck.sync(listOf("d", "c", "a"))
        assertEquals(listOf("d", "a", "c"), deck.order)
        deck.sync(listOf("d", "c", "b", "a"))
        assertEquals(listOf("b", "d", "a", "c"), deck.order)
        deck.putBack("c")
        assertEquals(listOf("c", "b", "d", "a"), deck.order)
        assertEquals("c", deck.top)
        deck.later("zz")
        assertEquals(listOf("c", "b", "d", "a"), deck.order)
    }

    @Test fun whereAFlickLands() {
        val targets = listOf(DeckFlick.Target("Garden", 0.0, 100.0, 120.0, 150.0), DeckFlick.Target("Kitchen", 270.0, 100.0, 390.0, 150.0))
        fun flick(x: Double, y: Double) = DeckFlick.outcome(195.0, 600.0, x, y, targets)
        assertEquals(DeckFlick.Outcome.Back, flick(225.0, 560.0))
        assertEquals(DeckFlick.Outcome.Folder("Garden"), flick(60.0, 120.0))
        assertEquals(DeckFlick.Outcome.Folder("Kitchen"), flick(390.0, 160.0))
        assertEquals(DeckFlick.Outcome.Back, flick(195.0, 900.0))
        assertEquals(DeckFlick.Outcome.Later, flick(195.0, 300.0))
        assertEquals(DeckFlick.Outcome.Later, flick(345.0, 300.0))
        assertEquals(DeckFlick.Outcome.Back, flick(495.0, 400.0))
        assertEquals(Pair(190.0, 200.0), DeckFlick.landing(190.0, 500.0, 0.0, -2000.0))
    }

    @Test fun theMostUsedFolders() {
        val folders = listOf(DeckFolders.Use("k", "Kitchen", 2), DeckFolders.Use("g", "Garden", 5), DeckFolders.Use("b", "bulbs", 2),
            DeckFolders.Use("s", "Shed", 0), DeckFolders.Use("a", "attic", 2))
        assertEquals(listOf("g", "a", "b", "k"), DeckFolders.pick(folders, 4))
        assertTrue(DeckFolders.pick(folders, 0).isEmpty())
        assertEquals(5, DeckFolders.pick(folders, 9).size)
        assertEquals(6, DeckFolders.slots(200.0, 52.0, 2, 8))
        assertEquals(8, DeckFolders.slots(900.0, 52.0, 2, 8))
        assertEquals(0, DeckFolders.slots(40.0, 52.0, 2, 8))
    }

    @Test fun theLowTide() {
        assertFalse(LowTide.due(null, now))
        assertFalse(LowTide.due(daysAgo(6.9), now))
        assertTrue(LowTide.due(daysAgo(7.0), now))
        val tide = LowTide.pick(listOf(
            LowTide.Idea("fresh", null, daysAgo(3.0)),
            LowTide.Idea("old", null, daysAgo(200.0)),
            LowTide.Idea("opened", daysAgo(5.0), daysAgo(300.0)),
            LowTide.Idea("mid", daysAgo(100.0), daysAgo(400.0)),
            LowTide.Idea("edge", null, daysAgo(31.0)),
            LowTide.Idea("older", null, daysAgo(250.0)),
        ), now)
        assertEquals(listOf("older", "old", "mid"), tide)
        assertEquals(listOf("x"), LowTide.pick(listOf(LowTide.Idea("x", null, daysAgo(40.0)), LowTide.Idea("y", null, daysAgo(2.0))), now))
        assertTrue(LowTide.pick(listOf(LowTide.Idea("y", null, daysAgo(2.0))), now).isEmpty())
    }
}
