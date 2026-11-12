package com.cramdown.sorta.ideas

import com.cramdown.sorta.data.Note

// The note editor's rules (NoteEditorView, fed0105), apart from its view so they are
// tested: the `[[` completion and the links as typed. The editor's words come with
// the editor (step 4); Turn into cards is study code and stays out.

object NoteEditing {
    const val READ_KEY = "notesReadAsMarkdown"

    /** Whatever has been typed after an unclosed `[[` at the end of the text. */
    fun openFragment(text: String): String? {
        val start = text.lastIndexOf("[[")
        if (start < 0) return null
        val after = text.substring(start + 2)
        if (']' in after || '\n' in after) return null
        return after
    }

    /** The notes a half-typed `[[` could mean: the eight most recent whose title fits. */
    fun suggestions(text: String, notes: List<Note>, current: String): List<Note> {
        val fragment = openFragment(text)?.lowercase() ?: return emptyList()
        return notes.filter { note ->
            note.id != current && note.title.isNotEmpty() && (fragment.isEmpty() || note.title.lowercase().contains(fragment))
        }.sortedByDescending { it.updatedAt }.take(8)
    }

    /** The text with the open `[[` completed as `[[name]]`. */
    fun complete(text: String, name: String): String {
        val start = text.lastIndexOf("[[")
        if (start < 0) return text
        return text.substring(0, start + 2) + name + "]]"
    }

    /** Hand-made links, then the `[[Title]]`s in the text as it is now, each once and known. */
    fun outgoing(book: IdeaBook, current: String, text: String): List<String> {
        val explicit = book.note(current)?.links ?: emptyList()
        val seen = hashSetOf(current)
        return (explicit + book.resolve(text)).filter { book.note(it) != null && seen.add(it) }
    }

    /** The notes linking here that are not already listed as linked from here. */
    fun backlinks(book: IdeaBook, current: String, text: String): List<String> {
        val shown = outgoing(book, current, text).toSet()
        return book.backlinks(current).filter { it !in shown }
    }

    /** `[[Title]]`s that name no note yet, each once. */
    fun unresolved(book: IdeaBook, text: String): List<String> {
        val index = book.titleIndex()
        val seen = HashSet<String>()
        return IdeaBook.wikiTitles(text).filter { index[it.lowercase()] == null && seen.add(it.lowercase()) }
    }
}
