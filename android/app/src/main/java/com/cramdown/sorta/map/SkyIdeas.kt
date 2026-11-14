package com.cramdown.sorta.map

import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// S06b: what the Space map and the share sheet work out from the ideas alone, iOS's
// GraphSkyIdeas.swift with the same numbers: a comet's tail by how long it has waited, the settle
// when it is filed, a folder's words and the loose ideas that lean towards it, the moons of recent
// days, and where a shared idea came from.

/** A comet's tail grows with the time since the idea was caught, in four steps, the longest the tail Space always drew. */
object CometTail {
    const val DAY_MS = 86_400_000L
    /** Under a day, a week, a month: the share of the full tail; older is [LONGEST]. */
    val steps: List<Pair<Long, Float>> = listOf(DAY_MS to 0.35f, 7 * DAY_MS to 0.6f, 30 * DAY_MS to 0.8f)
    const val LONGEST = 1f

    /** The share of the full tail for an idea [ageMs] old; one caught in the future counts as new. */
    fun length(ageMs: Long): Float {
        for ((below, length) in steps) if (ageMs < below) return length
        return LONGEST
    }

    fun length(createdAt: Long, now: Long): Float = length(now - createdAt)
}

/** Filing a comet: it eases from where it was into its place in the folder's system, fast then slow. */
object CometSettle {
    const val DURATION = 0.9

    /** How far along, 0 to 1, after [elapsed] seconds (ease-out cubic). */
    fun progress(elapsed: Double): Float {
        val t = min(max(elapsed / DURATION, 0.0), 1.0)
        val rest = 1 - t
        return (1 - rest * rest * rest).toFloat()
    }

    fun place(from: Vec3, to: Vec3, elapsed: Double): Vec3 = from + (to - from) * progress(elapsed)
}

/** A folder's own words (NoteFolder.words): at most five, each one word. */
object FolderWords {
    const val MOST = 5

    /** The words kept from what was typed: split at anything not a letter or digit, repeats dropped (case and accents aside), the first five, as typed. */
    fun parse(text: String): List<String> {
        val kept = ArrayList<String>()
        val seen = HashSet<String>()
        for (word in split(text)) {
            if (kept.size >= MOST) break
            if (seen.add(fold(word))) kept.add(word)
        }
        return kept
    }

    /** The text's whole words, as typed. */
    fun split(text: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) current.appendCodePoint(cp)
            else if (current.isNotEmpty()) { words.add(current.toString()); current.setLength(0) }
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    /** A word with case and accents set aside: "Café" and "cafe" match. */
    fun fold(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    fun folded(text: String): Set<String> = split(text).mapTo(HashSet()) { fold(it) }
}

/** A loose idea leaning towards a folder whose words it shares (plugins plan, idea 3). Nothing is filed and nothing is learnt. */
object Leaning {
    /** How far of the way to the folder a leaning comet is drawn. */
    const val PULL = 0.3f
    /** The nearest a leaning comet comes to its folder, in the folder's radii: nearer, the folder's disc would take its taps. */
    const val KEEP = 3f

    /**
     * Where a comet at [own] leaning to a folder at [folder], [size] in radius, is drawn: [PULL] of the way there,
     * but never within [KEEP] radii of it, pushed out along the line it came in on (iOS Leaning.place).
     */
    fun place(own: Vec3, folder: Vec3, size: Float): Vec3 {
        val pulled = own + (folder - own) * PULL
        val least = maxOf(size, 0f) * KEEP
        val away = pulled - folder
        val gap = away.length()
        if (gap >= least) return pulled
        val from = own - folder
        val way = when {
            gap > 1e-5f -> away * (1f / gap)
            from.length() > 1e-5f -> from.normalized()
            else -> Vec3(1f, 0f, 0f)
        }
        return folder + way * least
    }

    data class Folder(val id: String, val name: String, val words: List<String>)
    /** [word]: the first of the folder's words the idea shares, as the folder has it. */
    data class Lean(val folder: String, val word: String, val shared: Int)
    data class LooseNote(val id: String, val title: String, val body: String, val folderId: String?)

    /** The folder a loose idea leans to: most shared words, then most links ([links], by folder), then by name; null for none. */
    fun choose(title: String, body: String, folders: List<Folder>, links: Map<String, Int>): Lean? {
        val mine = FolderWords.folded(title) + FolderWords.folded(body)
        if (mine.isEmpty()) return null
        var best: Lean? = null
        var bestLinks = 0
        var bestName = ""
        for (folder in folders) {
            var shared = 0
            var first: String? = null
            for (word in folder.words) if (FolderWords.fold(word) in mine) { shared++; if (first == null) first = word }
            val word = first ?: continue
            val here = Lean(folder.id, word, shared)
            val hereLinks = links[folder.id] ?: 0
            val hereName = FolderWords.fold(folder.name)
            val now = best
            val better = when {
                now == null -> true
                here.shared != now.shared -> here.shared > now.shared
                hereLinks != bestLinks -> hereLinks > bestLinks
                hereName != bestName -> hereName < bestName
                else -> here.folder.uppercase(Locale.ROOT) < now.folder.uppercase(Locale.ROOT)
            }
            if (better) { best = here; bestLinks = hereLinks; bestName = hereName }
        }
        return best
    }

    /** Every loose idea's lean, by id; a filed idea never leans. */
    fun leans(notes: List<LooseNote>, folders: List<Folder>, edges: List<Pair<String, String>>): Map<String, Lean> {
        val worded = folders.filter { it.words.isNotEmpty() }
        if (worded.isEmpty()) return emptyMap()
        val folderOf = HashMap<String, String>()
        for (n in notes) n.folderId?.let { folderOf[n.id] = it }
        val linksTo = HashMap<String, HashMap<String, Int>>()
        for ((a, b) in edges) {
            folderOf[b]?.let { f -> linksTo.getOrPut(a) { HashMap() }.merge(f, 1, Int::plus) }
            folderOf[a]?.let { f -> linksTo.getOrPut(b) { HashMap() }.merge(f, 1, Int::plus) }
        }
        val out = HashMap<String, Lean>()
        for (n in notes) if (n.folderId == null) choose(n.title, n.body, worded, linksTo[n.id] ?: emptyMap())?.let { out[n.id] = it }
        return out
    }
}

/** A night for each day (plugins plan, idea 7): recent days as moons, each as full as that day was busy next to the busiest day shown. */
object MoonDays {
    /** How far back the row scrolls. */
    const val MOST = 60

    /** [phase]: 0 none (no ideas), 1 some, 2 half, 3 the busiest: more ink, a busier day. */
    data class Day(val date: LocalDate, val count: Int, val fill: Double, val phase: Int)

    /** The last [count] days to [now], newest first, in [zone]. */
    fun days(created: List<Long>, now: Long, count: Int, zone: ZoneId): List<Day> {
        val shown = min(max(count, 1), MOST)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val counts = IntArray(shown)
        for (made in created) {
            val back = ChronoUnit.DAYS.between(Instant.ofEpochMilli(made).atZone(zone).toLocalDate(), today)
            if (back in 0 until shown) counts[back.toInt()]++
        }
        val busiest = max(counts.maxOrNull() ?: 0, 1)
        return (0 until shown).map { k ->
            val fill = counts[k].toDouble() / busiest
            Day(today.minusDays(k.toLong()), counts[k], fill, phase(counts[k], fill))
        }
    }

    fun phase(count: Int, fill: Double): Int = if (count <= 0) 0 else min(max((fill * 3).roundToInt(), 1), 3)

    /** The ideas caught on [day]. */
    fun lit(notes: List<Pair<String, Long>>, day: LocalDate, zone: ZoneId): Set<String> =
        notes.filter { Instant.ofEpochMilli(it.second).atZone(zone).toLocalDate() == day }.mapTo(HashSet()) { it.first }
}

/** Where a shared idea came from (plugins plan, idea 10): the link's host only, else the sending app's name. */
object SharedFrom {
    fun mark(text: String, app: String?): String? {
        host(text)?.let { return it }
        val name = app?.trim().orEmpty()
        return if (name.isEmpty()) null else name.take(40)
    }

    /** The first web link's host, without "www.". */
    fun host(text: String): String? {
        for (word in text.split(Regex("\\s+"))) {
            val lower = word.lowercase(Locale.ROOT)
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) continue
            val found = runCatching { URI(word).host }.getOrNull()
                ?: runCatching { URI(word.substringBefore('?').substringBefore('#')).host }.getOrNull()
            if (found.isNullOrEmpty()) continue
            return found.lowercase(Locale.ROOT).removePrefix("www.")
        }
        return null
    }

    /** The words of a share, in order; a link stays one word. */
    fun words(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** What the sheet saves: the kept words in their order, a space apart. */
    fun kept(words: List<String>, keep: Set<Int>): String = words.filterIndexed { i, _ -> i in keep }.joinToString(" ")
}

/** Ideas with the same source circle the first of them in Space: a pair or a small group. */
object SharedCircle {
    const val MOST = 6
    /** Turns a second, lively. */
    const val SPIN = 0.4f

    data class Place(val leader: Int, val slot: Int, val of: Int)

    /** For each body (by index) with a source mark, the body it circles: the lowest index with the same mark leads. */
    fun groups(marks: Map<Int, String>): Map<Int, Place> {
        val byMark = HashMap<String, MutableList<Int>>()
        for ((i, mark) in marks) byMark.getOrPut(FolderWords.fold(mark)) { ArrayList() }.add(i)
        val out = HashMap<Int, Place>()
        for (members in byMark.values) {
            if (members.size < 2) continue
            val sorted = members.sorted()
            val circling = sorted.drop(1).take(MOST - 1)
            circling.forEachIndexed { k, i -> out[i] = Place(sorted[0], k, circling.size) }
        }
        return out
    }

    /** Where a circling body sits from its leader at [time], [radius] away. */
    fun offset(place: Place, radius: Float, time: Double): Vec3 {
        val turn = (2 * Math.PI * place.slot / max(place.of, 1)).toFloat()
        val angle = turn + (time % 10_000.0).toFloat() * SPIN
        return Vec3(cos(angle) * radius, sin(angle) * radius, 0f)
    }
}

/**
 * S06b in Space's Universe, by the plan's body index (iOS GraphSkyBodies): the folder body each loose
 * idea leans towards (−1 for none), the shared ideas circling the first of their group, each comet's
 * tail by how long it has waited (0 for a body with none), and the comets' ids, so a rebuild can tell
 * one just filed (it settles, CometSettle). Worked out from the ideas at each build; nothing is kept.
 */
class SkySpace(val leanTo: IntArray, val circles: Map<Int, SharedCircle.Place>, val tails: FloatArray, val comets: Set<String>) {
    /** One idea as the Universe reads it: [id] as the plan has it, [created] in ms, [mark] its sharedFrom. */
    data class Idea(val id: String, val title: String, val body: String, val folderId: String?, val created: Long, val mark: String?)

    companion object {
        /** The circle's distance: three of the larger body's radii (iOS GraphSim's circleRadius). */
        const val CIRCLE_RADII = 3f

        fun of(bodies: List<UniverseBody>, ideas: List<Idea>, folders: List<Leaning.Folder>, edges: List<Pair<String, String>>,
               now: Long): SkySpace {
            val key = { id: String -> id.uppercase(Locale.ROOT) }
            val byId = HashMap<String, Idea>()
            for (idea in ideas) byId[key(idea.id)] = idea
            val folderBody = HashMap<String, Int>()
            val loose = ArrayList<Int>()
            for ((i, b) in bodies.withIndex()) when (b.role) {
                UniverseRole.GALAXY, UniverseRole.STAR -> folderBody[key(b.id)] = i
                UniverseRole.COMET, UniverseRole.OORT -> loose.add(i)
                else -> {}
            }
            val leanTo = IntArray(bodies.size) { -1 }
            val tails = FloatArray(bodies.size)
            val comets = HashSet<String>()
            val marks = HashMap<Int, String>()
            val leans = Leaning.leans(ideas.map { Leaning.LooseNote(key(it.id), it.title, it.body, it.folderId?.let(key)) },
                folders.map { it.copy(id = key(it.id)) }, edges.map { key(it.first) to key(it.second) })
            for (i in loose) {
                val id = key(bodies[i].id)
                if (bodies[i].role == UniverseRole.COMET) comets.add(id)
                val idea = byId[id] ?: continue
                tails[i] = CometTail.length(idea.created, now)
                leans[id]?.let { lean -> folderBody[lean.folder]?.let { leanTo[i] = it } }
                idea.mark?.takeIf { it.isNotBlank() }?.let { marks[i] = it }
            }
            return SkySpace(leanTo, SharedCircle.groups(marks), tails, comets)
        }
    }
}
