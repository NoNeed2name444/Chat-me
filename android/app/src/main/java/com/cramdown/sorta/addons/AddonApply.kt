package com.cramdown.sorta.addons

import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.KeptNote
import com.cramdown.sorta.ideas.NoteFolder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Add-ons (S07b): the store's side of a call. The snapshot a call reads, mapped from the book's lists on
// the add-on's thread, and the call's writes applied to the notes in one pass on the main thread
// (IdeaBook.applyAddonBatch). It follows ios/Sorta/Addons/AddonApply.swift.

object AddonStoreMap {
    /** The book's notes and folders as a snapshot. The lists are the book's own, which never change in place; this runs on the add-on's thread. */
    fun snapshot(notes: List<Note>, folders: List<NoteFolder>): AddonSnapshot = AddonSnapshot(
        notes.map { n -> AddonIdea(n.id, n.title, n.body, n.kind, n.folderId, n.tags, n.links, n.createdAt, n.updatedAt, n.boardX, n.boardY) },
        folders.map { f -> AddonFolder(f.id, f.name, f.parentId, f.words) })

    /** Where the n-th idea lands: the book's own golden-angle spiral, then the nearest spot where its card covers no other (IdeaBook.create). */
    fun place(n: Int, others: List<Pair<Double, Double>>): Pair<Double, Double> {
        val k = n.toDouble()
        val angle = k * 2.399963
        val radius = 90 * sqrt(k)
        val spot = IdeaBoardDrop.settle(BoardPoint(radius * cos(angle), radius * sin(angle)), IdeaBoardDrop.CARD_WIDTH,
            IdeaBoardDrop.CARD_TALLEST, others.map { BoardPoint(it.first, it.second) })
        return spot.x to spot.y
    }
}

/** A call's writes applied to the notes, as one change. */
object AddonApply {
    /** [before] holds each changed note as it was, and where it stood, for Undo. */
    class Result(val notes: List<Note>, val applied: AddonApplied, val before: List<KeptNote>, val added: List<String>)

    /**
     * A batch worked out ahead, on the add-on's own thread, from the book's lists as they were when the call
     * was dispatched. If the book still holds those very lists when the call comes back, the main thread
     * takes [result] as it is and does none of the work (IdeaBook.applyAddonBatch); if the person changed
     * the book meanwhile, the main thread applies the batch itself.
     */
    class Prepared(val notes: List<Note>, val folders: List<NoteFolder>, val time: Long, val result: Result) {
        /** The book never changes a list in place, so the same list is the same book. */
        fun fits(notes: List<Note>, folders: List<NoteFolder>, time: Long): Boolean =
            this.notes === notes && this.folders === folders && this.time == time
    }

    fun prepare(ops: List<AddonOp>, notes: List<Note>, folders: List<NoteFolder>, now: Long): Prepared =
        Prepared(notes, folders, now, apply(ops, notes, folders.mapTo(HashSet()) { it.id }, now))

    /**
     * Applies [ops] to [notes]. An idea or folder gone since the snapshot is skipped and listed; a note counts
     * as changed, and gets [now] as its updatedAt, only if it ends up different. Only the ideas the batch
     * names are looked up, so only they are indexed, and the list is copied only if the batch writes to it:
     * one pass over the notes to find them, then O(ops).
     */
    fun apply(ops: List<AddonOp>, notes: List<Note>, folders: Set<String>, now: Long): Result {
        val wanted = HashSet<String>(ops.size * 4)
        for (op in ops) {
            when (op) {
                is AddonOp.Capture -> {}
                is AddonOp.Link -> { wanted.add(op.a); wanted.add(op.b) }
                is AddonOp.Unlink -> { wanted.add(op.a); wanted.add(op.b) }
                is AddonOp.Tag -> wanted.add(op.id)
                is AddonOp.Untag -> wanted.add(op.id)
                is AddonOp.File -> wanted.add(op.id)
            }
        }
        val index = HashMap<String, Int>((wanted.size + ops.size) * 2)
        if (wanted.isNotEmpty()) for (i in notes.indices) { val id = notes[i].id; if (id in wanted) index[id] = i }
        // the book's list as it is, until the first write
        var list: List<Note> = notes
        var own: ArrayList<Note>? = null
        fun write(): ArrayList<Note> = own ?: ArrayList<Note>(notes.size + ops.size).also { it.addAll(notes); own = it; list = it }
        val before = LinkedHashMap<String, KeptNote>()
        val added = ArrayList<String>()
        val addedSet = HashSet<String>()
        val skipped = HashSet<String>()
        fun touch(i: Int) {
            val id = list[i].id
            if (id !in before && id !in addedSet) before[id] = KeptNote(i, list[i])
        }
        for (op in ops) {
            when (op) {
                is AddonOp.Capture -> {
                    index[op.id] = list.size
                    write().add(Note(id = op.id, title = op.title, body = op.body, boardX = op.x, boardY = op.y, createdAt = now, updatedAt = now))
                    added.add(op.id)
                    addedSet.add(op.id)
                }
                is AddonOp.Link, is AddonOp.Unlink -> {
                    val a = if (op is AddonOp.Link) op.a else (op as AddonOp.Unlink).a
                    val b = if (op is AddonOp.Link) op.b else (op as AddonOp.Unlink).b
                    val i = index[a]
                    val j = index[b]
                    if (i == null || j == null) {
                        if (i == null) skipped.add(a)
                        if (j == null) skipped.add(b)
                        continue
                    }
                    if (op is AddonOp.Link) {
                        if (b !in list[i].links && a !in list[j].links) { touch(i); write()[i] = list[i].copy(links = list[i].links + b) }
                    } else {
                        if (b in list[i].links) { touch(i); write()[i] = list[i].copy(links = list[i].links.filterNot { it == b }) }
                        if (a in list[j].links) { touch(j); write()[j] = list[j].copy(links = list[j].links.filterNot { it == a }) }
                    }
                }
                is AddonOp.Tag -> {
                    val i = index[op.id]
                    if (i == null) { skipped.add(op.id); continue }
                    val more = ArrayList<String>()
                    for (t in op.tags) if (t !in list[i].tags && t !in more) more.add(t)
                    if (more.isNotEmpty()) { touch(i); write()[i] = list[i].copy(tags = list[i].tags + more) }
                }
                is AddonOp.Untag -> {
                    val i = index[op.id]
                    if (i == null) { skipped.add(op.id); continue }
                    if (list[i].tags.any { it in op.tags }) { touch(i); write()[i] = list[i].copy(tags = list[i].tags.filterNot { it in op.tags }) }
                }
                is AddonOp.File -> {
                    val i = index[op.id]
                    if (i == null) { skipped.add(op.id); continue }
                    if (op.folder != null && op.folder !in folders) { skipped.add(op.folder); continue }
                    if (list[i].folderId != op.folder) { touch(i); write()[i] = list[i].copy(folderId = op.folder) }
                }
            }
        }
        val changed = ArrayList<KeptNote>()
        for (kept in before.values) {
            if (list[kept.index] != kept.note) {
                write()[kept.index] = list[kept.index].copy(updatedAt = now)
                changed.add(kept)
            }
        }
        changed.sortBy { it.index }
        return Result(list, AddonApplied(added.sorted(), changed.map { it.note.id }.sorted(), skipped.sorted()), changed, added)
    }
}
