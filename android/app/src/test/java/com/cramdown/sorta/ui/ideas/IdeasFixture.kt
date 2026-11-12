package com.cramdown.sorta.ui.ideas

import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.SortaRun.Companion.id
import java.io.File
import java.util.UUID

/**
 * Made-up ideas for the target checks and the renders: three to sort (one long), two folders at
 * the top and one inside Garden, and an idea with a hand link, a link in its text and an idea
 * linking to it. None is medical, and the times are fixed so the renders don't move.
 */
object IdeasFixture {
    private const val T = 1_793_000_000_000L // 2026-10-26
    private const val HOUR = 3_600_000L

    val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
    val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
    val bulbs = NoteFolder(id = id(203), name = "Bulbs", parentId = garden.id, createdAt = T + 2)

    val gate = Note(id = id(11), title = "Fix the gate latch", createdAt = T + 9 * HOUR)
    val boiler = Note(id = id(12), title = "Ask the landlord when the boiler service is booked, and whether the hall light can be fixed the same day",
        createdAt = T + 8 * HOUR)
    val brushes = Note(id = id(13), title = "Buy brushes", createdAt = T + 7 * HOUR)
    val shed = Note(id = id(14), title = "Paint the shed green", body = "Two coats, after the rain. Check [[Fix the gate latch]] first.",
        folderId = garden.id, links = listOf(brushes.id), createdAt = T + 6 * HOUR)
    val compost = Note(id = id(15), title = "Turn the compost", folderId = garden.id, createdAt = T + 5 * HOUR)
    val tulips = Note(id = id(16), title = "Plant tulips before the frost", body = "Near [[Paint the shed green]].", folderId = bulbs.id,
        createdAt = T + 4 * HOUR)
    val bread = Note(id = id(17), title = "Try the seeded bread", folderId = kitchen.id, createdAt = T + 3 * HOUR)

    /** On the board where the app puts ideas made one after another (IdeaBook.create), not all on the centre. */
    val notes = IdeaBook(emptyList(), emptyList()).let { book ->
        listOf(gate, boiler, brushes, shed, compost, tulips, bread).map { note ->
            val spot = book.create(note.title)
            note.copy(boardX = spot.boardX, boardY = spot.boardY)
        }
    }
    val folders = listOf(garden, kitchen, bulbs)

    /**
     * The places of iOS's made-up export (MadeUpExport in IdeasUITestSupport.swift), set on a fresh
     * store: the shed clear, the seed swap at (0, 0) with the bell 33 below it, the rye starter
     * clear, and two more with no board place, at (0, 0), as an import leaves them (S05c).
     */
    val stacked = listOf(
        Note(id = id(21), title = "Paint the shed green", boardX = 120.0, boardY = -40.0, createdAt = T),
        Note(id = id(22), title = "Seed swap list", boardX = 0.0, boardY = 0.0, createdAt = T + 1),
        Note(id = id(23), title = "Try a rye starter", boardX = -75.5, boardY = 210.25, createdAt = T + 2),
        Note(id = id(24), title = "A bell for the bike", boardX = 0.0, boardY = 33.0, createdAt = T + 3),
        Note(id = id(25), title = "Oil the gate", createdAt = T + 4),
        Note(id = id(26), title = "Ask about the allotment waiting list", createdAt = T + 5),
    )

    /** A store in a new directory under [parent], holding [ideas] (the made-up ideas), or empty when [filled] is false. */
    fun store(parent: File, filled: Boolean = true, ideas: List<Note> = notes): IdeaStore {
        val dir = File(parent, "fixture-" + UUID.randomUUID()).also { it.mkdirs() }
        val store = IdeaStore(dir)
        if (filled) store.replaceIdeas(ideas, folders)
        return store
    }
}
