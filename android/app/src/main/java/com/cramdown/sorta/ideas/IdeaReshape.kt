package com.cramdown.sorta.ideas

import kotlin.random.Random

// Collide, split and join (S06c, the plugins plan's ideas 5 and 12): the rules, with no store and no UI.
// IdeaBook's reshape section and the screens call them; iOS's IdeaReshape.swift is the same rule, read
// from the same cases (ios/UnitTests/Suites/IdeaShapeCases.json).
object IdeaReshape {

    // ---- collide ----

    /** One idea as a collision sees it: its id and its top folder (null: To sort, a place of its own). */
    data class Place(val id: String, val top: String?)

    /**
     * Two ideas far apart: different top folders and no link between them, chosen at random, never [last]
     * again (either way round). Null when no other pair is far apart. Up to 200 random tries, then every pair.
     */
    fun collide(ideas: List<Place>, linked: (String, String) -> Boolean, last: Pair<String, String>?, random: Random): Pair<String, String>? {
        val n = ideas.size
        if (n < 2) return null
        fun far(i: Int, j: Int): Boolean {
            val a = ideas[i]; val b = ideas[j]
            if (a.top == b.top || linked(a.id, b.id)) return false
            if (last != null && ((last.first == a.id && last.second == b.id) || (last.first == b.id && last.second == a.id))) return false
            return true
        }
        repeat(200) {
            val i = random.nextInt(n); val j = random.nextInt(n)
            if (i != j && far(i, j)) return ideas[i].id to ideas[j].id
        }
        val pairs = ArrayList<Pair<Int, Int>>()
        for (i in 0 until n) for (j in i + 1 until n) if (far(i, j)) pairs.add(i to j)
        if (pairs.isEmpty()) return null
        val (i, j) = pairs[random.nextInt(pairs.size)]
        return ideas[i].id to ideas[j].id
    }

    // ---- split ----

    /** One sentence of an idea: its words, whether it is in the title, and where it ends in its field. */
    data class Sentence(val text: String, val inTitle: Boolean, val end: Int)

    /**
     * The title's sentences, then the body's. A sentence ends after ".", "!", "?" or "…" (and any closing
     * quote or bracket) before a space or the end, and at a line break; blank ones are left out.
     */
    fun sentences(title: String, body: String): List<Sentence> = pieces(title, true) + pieces(body, false)

    private val enders = setOf('.', '!', '?', '…')
    private val closers = setOf('"', '\'', '”', '’', ')', ']')

    private fun pieces(chars: String, inTitle: Boolean): List<Sentence> {
        val out = ArrayList<Sentence>()
        var start = 0
        var i = 0
        fun close(end: Int) {
            val text = chars.substring(start, end).trim()
            if (text.isNotEmpty()) out.add(Sentence(text, inTitle, end))
            start = end
        }
        while (i < chars.length) {
            val c = chars[i]
            if (c == '\n') {
                close(i + 1)
                i++
            } else if (c in enders) {
                var j = i + 1
                while (j < chars.length && (chars[j] in enders || chars[j] in closers)) j++
                if (j == chars.length || chars[j] == ' ' || chars[j] == '\t' || chars[j] == '\n') {
                    while (j < chars.length && (chars[j] == ' ' || chars[j] == '\t')) j++
                    close(j)
                }
                i = j
            } else {
                i++
            }
        }
        if (start < chars.length) close(chars.length)
        return out
    }

    /** The two halves' words. [titleChanged]: the cut fell in the title; [titleFromBody]: the second's title came from the body. */
    data class Halves(
        val firstTitle: String, val firstBody: String, val secondTitle: String, val secondBody: String,
        val titleChanged: Boolean, val titleFromBody: Boolean,
    )

    /** The two halves, cut after sentence [after] (1 up to the count less one); null for any other cut. */
    fun cut(title: String, body: String, after: Int): Halves? {
        val all = sentences(title, body)
        if (after < 1 || after >= all.size) return null
        val inTitle = all.count { it.inTitle }
        if (after < inTitle) {
            val end = all[after - 1].end
            return Halves(title.substring(0, end).trim(), "", title.substring(end).trim(), body.trim(), titleChanged = true, titleFromBody = false)
        }
        val start = if (after == inTitle) 0 else all[after - 1].end
        val firstBody = if (after == inTitle) "" else body.substring(0, start).trim()
        val next = all[after]
        return Halves(title.trim(), firstBody, heading(next.text), body.substring(next.end).trim(), titleChanged = false, titleFromBody = true)
    }

    /** A line made a title: a leading "#", "-", "*", "+" or ">" run and its space dropped. */
    fun heading(line: String): String {
        val marks = setOf('#', '-', '*', '+', '>')
        val run = line.takeWhile { it in marks }.length
        val rest = if (run > 0 && line.getOrNull(run) == ' ') line.substring(run) else line
        return rest.trim { it == ' ' || it == '\t' }
    }

    /**
     * The hand links of the two halves: both keep the idea's own; the second also takes the ideas whose hand
     * links named the idea and the ideas its title names in `[[Title]]`s; when the title changed, the first
     * takes the ideas whose `[[Old title]]` named it. Each once, never a half itself.
     */
    fun splitLinks(own: List<String>, heldBy: List<String>, namedBy: List<String>, titleChanged: Boolean,
                   secondTitleNames: List<String>, first: String, second: String): Pair<List<String>, List<String>> {
        val one = unique(own + (if (titleChanged) namedBy else emptyList()), setOf(first, second))
        val two = unique(own + heldBy + secondTitleNames, setOf(first, second))
        return one to two
    }

    // ---- join ----

    /**
     * The joined idea's words: the first's title (the second's when the first has none), then its body, the
     * second's title and the second's body, blank parts left out, a blank line between.
     */
    fun joined(firstTitle: String, firstBody: String, secondTitle: String, secondBody: String): Pair<String, String> {
        val a = firstTitle.trim()
        val title = if (a.isEmpty()) secondTitle.trim() else a
        val parts = listOf(firstBody, if (a.isEmpty()) "" else secondTitle, secondBody).map { it.trim() }.filter { it.isNotEmpty() }
        return title to parts.joinToString("\n\n")
    }

    /**
     * The joined idea's hand links: the first's own, then the second's and the ideas that pointed at the
     * second, leaving out the two, any the first holds already and any already connected another way
     * ([connected]: the joined text's `[[Title]]`s and the first's backlinks). No link twice.
     */
    fun joinLinks(first: String, second: String, firstOwn: List<String>, secondOwn: List<String>,
                  pointedAtSecond: List<String>, connected: Set<String>): List<String> {
        val kept = unique(firstOwn, setOf(first, second))
        val have = kept.toHashSet()
        val out = ArrayList(kept)
        for (id in secondOwn + pointedAtSecond) {
            if (id == first || id == second || id in connected) continue
            if (have.add(id)) out.add(id)
        }
        return out
    }

    private fun unique(ids: List<String>, leaving: Set<String>): List<String> {
        val seen = HashSet(leaving)
        return ids.filter { seen.add(it) }
    }
}

/** One idea as a reshape found it, and where it stood in the list. */
data class KeptNote(val index: Int, val note: com.cramdown.sorta.data.Note)

/**
 * What a split, a join or a collision changed, for Undo (IdeaBook.undo): each idea it changed or removed as it
 * was, and the ideas it added. [result] is the idea to show after it: the first half, the joined idea, the new one.
 */
data class Reshaped(val before: List<KeptNote>, val added: List<String>, val result: String)
