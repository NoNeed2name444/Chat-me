package com.cramdown.sorta.ui.ideas

import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
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

    val notes = listOf(gate, boiler, brushes, shed, compost, tulips, bread)
    val folders = listOf(garden, kitchen, bulbs)

    /** A store in a new directory under [parent], holding the made-up ideas, or empty when [filled] is false. */
    fun store(parent: File, filled: Boolean = true): IdeaStore {
        val dir = File(parent, "fixture-" + UUID.randomUUID()).also { it.mkdirs() }
        val store = IdeaStore(dir)
        if (filled) store.replaceIdeas(notes, folders)
        return store
    }
}
