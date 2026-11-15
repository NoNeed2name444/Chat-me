package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// S06c A3, A5, A6: the store's split, join and collision, their links, and Undo putting back exactly what was
// there (iOS's IdeasReshapeStoreTests asks the same of NoteStore). Made-up ideas, nothing medical.
class IdeaBookReshapeTest {
    private var clock = 1_000L
    private var count = 0
    private fun book(notes: List<Note>, folders: List<NoteFolder> = listOf(NoteFolder("garden", "Garden"), NoteFolder("trips", "Trips"))) =
        IdeaBook(notes, folders, now = { clock++ }, makeId = { "made-${++count}" })

    private fun note(id: String, title: String, body: String = "", folder: String? = null, links: List<String> = emptyList(), made: Long = 1) =
        Note(id = id, title = title, body = body, folderId = folder, links = links, createdAt = made, updatedAt = made)

    @Test fun splitInTheBodyKeepsTheFolderAndLinksFollowTheWords() {
        val b = book(listOf(
            note("plan", "Holiday plans", "Book the train to [[Harbour]]. Pack light.\nAsk [[Neighbour]] to water the beans.",
                folder = "trips", links = listOf("kite"), made = 1),
            note("harbour", "Harbour", made = 2),
            note("neighbour", "Neighbour", made = 3),
            note("kite", "Kite", made = 4),
            note("bike", "Bike bell", links = listOf("plan"), made = 5),
        ))
        val before = b.notes
        // sentences: Holiday plans | Book the train to [[Harbour]]. | Pack light. | Ask [[Neighbour]] to water the beans.
        val done = b.split("plan", 2)!!
        val first = b.note("plan")!!
        val second = b.note(done.added.single())!!
        assertEquals("Holiday plans", first.title)
        assertEquals("Book the train to [[Harbour]].", first.body)
        assertEquals("the next sentence is the second's title", "Pack light.", second.title)
        assertEquals("Ask [[Neighbour]] to water the beans.", second.body)
        assertEquals("each half keeps the folder", "trips", second.folderId)
        assertEquals("the second sits just after the first", b.notes.indexOf(first) + 1, b.notes.indexOf(second))
        assertTrue("the first's [[Harbour]] stays with it", "harbour" in b.outgoing("plan"))
        assertFalse("the first lost [[Neighbour]]", "neighbour" in b.outgoing("plan"))
        assertTrue("the second's [[Neighbour]] went with it", "neighbour" in b.outgoing(second.id))
        assertEquals("a hand link goes to both halves", listOf("kite"), first.links)
        assertTrue("both halves keep the hand link", "kite" in second.links)
        assertTrue("an idea that linked by hand links to both", b.isLinked("bike", "plan") && b.isLinked("bike", second.id))
        assertEquals("the result is the first half", "plan", done.result)
        b.undo(done)
        assertEquals("Undo puts it back as one, exactly", before, b.notes)
    }

    @Test fun splitInTheTitleKeepsWhatNamedIt() {
        val b = book(listOf(
            note("shed", "Paint the shed. Then the fence!", "Blue for both.", folder = "garden", made = 1),
            note("list", "Weekend list", "Start with [[Paint the shed. Then the fence!]]", made = 2),
        ))
        val before = b.notes
        val done = b.split("shed", 1)!!
        assertEquals("Paint the shed.", b.note("shed")!!.title)
        val second = b.note(done.added.single())!!
        assertEquals("Then the fence!", second.title)
        assertEquals("Blue for both.", second.body)
        assertTrue("the list that named the old title is still linked to the first half", b.isLinked("shed", "list"))
        b.undo(done)
        assertEquals("Undo puts it back exactly", before, b.notes)
    }

    @Test fun aCutOutsideTheSentencesIsRefused() {
        val b = book(listOf(note("one", "One line only")))
        assertNull(b.split("one", 1))
        assertNull(b.split("one", 0))
        assertNull(b.split("missing", 1))
    }

    @Test fun joinPutsTheTextsInOrderAndMergesLinksOnce() {
        val b = book(listOf(
            note("beans", "Beans", "By the fence.", folder = "garden", links = listOf("rain", "canes"), made = 1),
            note("trellis", "Trellis", "Bamboo [[Canes]].", folder = "trips", links = listOf("rain", "beans", "pond"), made = 2),
            note("rain", "Rain barrel", made = 3),
            note("canes", "Canes", made = 4),
            note("pond", "Pond", made = 5),
            note("bike", "Bike bell", links = listOf("trellis", "beans"), made = 6),
            note("shed", "Shed", "See [[Trellis]]", made = 7),
        ))
        val before = b.notes
        val done = b.join("beans", "trellis")!!
        val joined = b.note("beans")!!
        assertNull("the second is gone", b.note("trellis"))
        assertEquals("the first's title stays", "Beans", joined.title)
        assertEquals("texts in order", "By the fence.\n\nTrellis\n\nBamboo [[Canes]].", joined.body)
        assertEquals("links merged, none twice, never itself; bike already links here; shed's [[Trellis]] becomes a hand link",
            listOf("rain", "canes", "pond", "shed"), joined.links)
        assertEquals("no link is drawn twice", b.allEdges().size, b.allEdges().toSet().size)
        assertFalse("hand links to the second went with it", b.notes.any { "trellis" in it.links })
        assertEquals("the folder stays the first's", "garden", joined.folderId)
        b.undo(done)
        assertEquals("Undo splits them back exactly", before, b.notes)
    }

    /** Where an idea stops is worked out again for each half and for the joined idea; Undo brings the old mark back. */
    @Test fun eachHalfAndTheJoinedIdeaStopWhereTheirOwnTextDoes() {
        val b = book(listOf(
            note("jobs", "Garden jobs", "Dig the bed. Plant the bulbs. Water the", made = 1).copy(stoppedAt = 3),
            note("path", "Path", "Weed the path", made = 2).copy(stoppedAt = 4),
            note("rake", "Rake", "Rake the leaves.", made = 3),
            note("beans", "Beans", "By the fence.", made = 4),
            note("canes", "Canes", "Bamboo canes and", made = 5),
        ))
        val before = b.notes
        val split = b.split("jobs", 2)!!
        assertEquals("Dig the bed.", b.note("jobs")!!.body)
        assertNull("the first half ends its sentence, so it stops nowhere", b.note("jobs")!!.stoppedAt)
        val second = b.note(split.added.single())!!
        assertEquals("Water the", second.body)
        assertEquals("the second half stops at its own end", "Water the".length, second.stoppedAt)
        b.undo(split)
        assertEquals("Undo puts the old mark back", before, b.notes)

        val ended = b.join("path", "rake")!!
        assertNull("joined, the text ends a sentence", b.note("path")!!.stoppedAt)
        b.undo(ended)
        val open = b.join("beans", "canes")!!
        val body = b.note("beans")!!.body
        assertTrue(body.endsWith("Bamboo canes and"))
        assertEquals("joined, it stops at the joined text's end", body.length, b.note("beans")!!.stoppedAt)
        b.undo(open)
        assertEquals(before, b.notes)
    }

    @Test fun collideMakesAnIdeaInToSortLinkedToBoth() {
        val b = book(listOf(note("kite", "Kite", folder = "garden"), note("bell", "Bike bell", folder = "trips")))
        val before = b.notes
        assertNull("nothing written, nothing made", b.collide("kite", "bell", "  \n "))
        val done = b.collide("kite", "bell", "A kite with a bell\nIt rings in the wind.")!!
        val made = b.note(done.result)!!
        assertEquals("A kite with a bell", made.title)
        assertEquals("It rings in the wind.", made.body)
        assertNull("in To sort", made.folderId)
        assertEquals("linked to both", listOf("kite", "bell"), made.links)
        b.undo(done)
        assertEquals("Undo leaves nothing", before, b.notes)
    }
}
