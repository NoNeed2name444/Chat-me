package com.cramdown.sorta.ideas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// The rules under the sorting deck, "You were saying…" and tides (S06a), pure, as iOS's
// Ideas/SortDeck.swift, case for case (SortDeckTest, iOS SortDeckTests).

/** "You were saying…": an idea whose details stop mid-sentence (S06a A4). */
object IdeaStops {
    /** Marks that close a sentence: . ! ? … and their full-width and Arabic forms. */
    val closers: Set<Int> = ".!?…。！？؟".codePoints().toArray().toSet()
    /** What may follow a sentence's close and still leave it closed: spaces, closing quotes and closing brackets. */
    val trailing: Set<Int> = " \t\n\r \"'”’»)]}」』".codePoints().toArray().toSet()

    /**
     * Where the person stopped, as a UTF-16 offset into [body]: null when the text is empty or ends a
     * sentence; else [caret] held inside the text, or the text's end when no caret is known.
     */
    fun stoppedAt(body: String, caret: Int?): Int? {
        val last = body.codePoints().toArray().lastOrNull { it !in trailing } ?: return null
        if (last in closers) return null
        return caret?.let { min(max(it, 0), body.length) } ?: body.length
    }

    /** One idea as the offer reads it. */
    data class Mark<ID>(val id: ID, val stoppedAt: Int?, val updatedAt: Long)

    /** The idea to offer: the marked one changed most recently (ties: the first given), or null when none is marked. */
    fun <ID> offer(ideas: List<Mark<ID>>): ID? {
        var best: Mark<ID>? = null
        for (idea in ideas) if (idea.stoppedAt != null && (best == null || idea.updatedAt > best.updatedAt)) best = idea
        return best?.id
    }
}

/** How deep an idea's frost is (S06a A6): from its last opening, or its making when it was never opened. */
object IdeaFrost {
    /** The days at which each step begins. */
    val steps: List<Double> = listOf(30.0, 90.0, 180.0)
    const val DAY: Long = 86_400_000L

    /** 0 (none) to 3. */
    fun level(openedAt: Long?, createdAt: Long, now: Long): Int {
        val days = (now - (openedAt ?: createdAt)).toDouble() / DAY
        return steps.count { days >= it }
    }
}

/** The deck's order: its top is the card under the thumb (S06a A1). */
class SortDeck<ID> {
    var order: List<ID> = emptyList(); private set

    val top: ID? get() = order.firstOrNull()

    /** Follows To sort ([ids], its order): the cards still there keep their order, new ones go on top in To sort's order, gone ones leave. */
    fun sync(ids: List<ID>) {
        val here = ids.toSet()
        val known = order.toSet()
        order = ids.filter { it !in known } + order.filter { it in here }
    }

    /** Kept for later: to the back. */
    fun later(id: ID) {
        if (id !in order) return
        order = order.filter { it != id } + id
    }

    /** Back on top (Undo of Later). */
    fun putBack(id: ID) {
        order = listOf(id) + order.filter { it != id }
    }
}

/** Where a flick of the card lands (S06a A1). Points are in one space, y down. */
object DeckFlick {
    /** A drag shorter than this puts the card back. */
    const val LEAST = 64.0
    /** A target counts this far outside its frame. */
    const val SLACK = 12.0
    /** Within this of straight up, the card goes to Later. */
    const val UP_ANGLE = 35.0
    /** How far the velocity carries the release point, in seconds. */
    const val CARRY = 0.15

    data class Target<ID>(val id: ID, val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

    sealed class Outcome<out ID> {
        data object Back : Outcome<Nothing>()
        data object Later : Outcome<Nothing>()
        data class Folder<ID>(val id: ID) : Outcome<ID>()
    }

    /** The point the card is flung to: where the finger let go, carried on by its velocity for [CARRY] seconds. */
    fun landing(x: Double, y: Double, vx: Double, vy: Double): Pair<Double, Double> = Pair(x + vx * CARRY, y + vy * CARRY)

    /** From the card's start ([fromX], [fromY]) to where it lands. */
    fun <ID> outcome(fromX: Double, fromY: Double, toX: Double, toY: Double, targets: List<Target<ID>>): Outcome<ID> {
        val dx = toX - fromX
        val dy = toY - fromY
        if (hypot(dx, dy) < LEAST) return Outcome.Back
        var best: Target<ID>? = null
        var bestDistance = Double.MAX_VALUE
        for (t in targets) {
            val outX = maxOf(t.minX - toX, 0.0, toX - t.maxX)
            val outY = maxOf(t.minY - toY, 0.0, toY - t.maxY)
            if (hypot(outX, outY) > SLACK) continue
            val distance = hypot(toX - (t.minX + t.maxX) / 2, toY - (t.minY + t.maxY) / 2)
            if (best == null || distance < bestDistance) { best = t; bestDistance = distance }
        }
        if (best != null) return Outcome.Folder(best.id)
        // the angle from straight up (0, -1)
        val angle = Math.toDegrees(atan2(abs(dx), -dy))
        return if (dy < 0 && angle <= UP_ANGLE) Outcome.Later else Outcome.Back
    }
}

/** The folders round the deck (S06a A1, A2). */
object DeckFolders {
    data class Use<ID>(val id: ID, val name: String, val count: Int)

    /** The most-used folders first: most ideas filed directly in them, then by name, then in the store's order; at most [slots]. */
    fun <ID> pick(folders: List<Use<ID>>, slots: Int): List<ID> =
        folders.withIndex().sortedWith(compareByDescending<IndexedValue<Use<ID>>> { it.value.count }
            .thenComparator { a, b -> NaturalOrder.compare(a.value.name, b.value.name) }
            .thenBy { it.index })
            .take(max(slots, 0)).map { it.value.id }

    /** How many targets fit: [columns] columns of [height], a target and its gap being [row] tall, at most [cap]. */
    fun slots(height: Double, row: Double, columns: Int, cap: Int): Int {
        if (row <= 0.0 || height <= 0.0) return 0
        return min(floor(height / row).toInt() * max(columns, 0), cap)
    }
}

/** The weekly low tide (S06a A7). */
object LowTide {
    const val WEEK: Long = 7 * 86_400_000L
    const val SIZE = 3

    /** Due on the first open 7 days or more after the last tide. With none yet it is not due: the first open starts the clock. */
    fun due(last: Long?, now: Long): Boolean = last != null && now - last >= WEEK

    data class Idea<ID>(val id: ID, val openedAt: Long?, val createdAt: Long)

    /** The tide: up to three frosted ideas, the longest unopened first (ties: the first given). */
    fun <ID> pick(ideas: List<Idea<ID>>, now: Long): List<ID> =
        ideas.withIndex()
            .filter { IdeaFrost.level(it.value.openedAt, it.value.createdAt, now) > 0 }
            .sortedWith(compareBy<IndexedValue<Idea<ID>>> { it.value.openedAt ?: it.value.createdAt }.thenBy { it.index })
            .take(SIZE).map { it.value.id }
}
