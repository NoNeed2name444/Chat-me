package com.cramdown.sorta.ai

import com.cramdown.sorta.ideas.IdeaEchoes

// The second brain's operations (S08 A5, A6): pure logic, the same as iOS's AIOperations.swift. Each payload builder
// sends section 4's least, and each answer is parsed and checked before anything changes. An answer that does not
// parse or does not check changes nothing. Cases: ai/fixtures/model.

enum class AIVerdict(val raw: String) {
    OK("ok"), UNREADABLE("unreadable"), NOT_A_SLICE("notASlice"), BAD_CITATION("badCitation"), EMPTY("empty"), NOTHING_FITS("nothingFits");

    companion object { fun of(raw: String?) = entries.firstOrNull { it.raw == raw } }
}

/** One idea as an operation sees it. */
data class AIIdea(val id: String, val title: String, val text: String)

/** The prompts and schemas in ai/prompts, the same bytes in both apps. */
data class AIPrompt(val name: String, val instructions: String, val schema: String) {
    companion object {
        val names = listOf("split", "why", "ask", "sumup")

        fun load(name: String, files: AIFiles) = AIPrompt(
            name,
            String(files.read("prompts/$name.txt"), Charsets.UTF_8),
            String(files.read("prompts/$name.schema.json"), Charsets.UTF_8),
        )
    }
}

/** What an operation sends, and how many ideas and characters of the person's own text it carries. */
data class AIPayload(val input: String, val ideas: Int, val characters: Int)

/** An idea numbered as it was sent. */
data class AINumbered(val n: Int, val idea: AIIdea)

object AISplit {
    const val TITLE_LIMIT = 60
    const val MAX_OUTPUT = 1200

    data class Folder(val id: String, val name: String, val words: List<String>)
    data class Piece(val text: String, val title: String, val folder: String?)

    /** More than one sentence: a line break, or ".", "!", "?" or "…" followed by a space, between two runs of letters or digits. */
    fun sentences(text: String): Int {
        var count = 0
        var ended = true
        var inside = false
        var stop = false
        for (s in text.codePoints().toArray()) {
            if (Character.isAlphabetic(s) || Character.getType(s) == Character.DECIMAL_DIGIT_NUMBER.toInt()) {
                if (ended) { count++; ended = false }
                inside = true
                stop = false
            } else if (s == '\n'.code || s == '\r'.code) {
                if (inside) { ended = true; inside = false }
                stop = false
            } else if (s == '.'.code || s == '!'.code || s == '?'.code || s == 0x2026) {
                stop = inside
            } else if (AIText.isSpace(s)) {
                if (stop) { ended = true; inside = false; stop = false }
            } else {
                stop = false
            }
        }
        return count
    }

    fun needed(capture: String) = sentences(capture) > 1

    /** The capture, and the folders' names with their words. No other idea. */
    fun payload(capture: String, folders: List<Folder>): AIPayload {
        val list = folders.map { f -> AIJSON.make("name" to AIJSON.Str(f.name), "words" to AIJSON.Arr(f.words.map { AIJSON.Str(it) })) }
        val chars = AIText.count(capture) + folders.sumOf { f -> AIText.count(f.name) + f.words.sumOf { AIText.count(it) } }
        return AIPayload(AIJSON.make("capture" to AIJSON.Str(capture), "folders" to AIJSON.Arr(list)).text, 0, chars)
    }

    fun check(answer: String, capture: String, folders: List<Folder>): Pair<AIVerdict, List<Piece>> {
        if (AIText.trim(answer).isEmpty()) return AIVerdict.EMPTY to emptyList()
        val items = AIJSON.answer(answer)?.get("ideas")?.array ?: return AIVerdict.UNREADABLE to emptyList()
        if (items.isEmpty()) return AIVerdict.EMPTY to emptyList()
        val pieces = ArrayList<Piece>()
        for (item in items) {
            val text = item["text"]?.string
            val rawTitle = item["title"]?.string
            if (text == null || rawTitle == null) return AIVerdict.UNREADABLE to emptyList()
            val f = item["folder"]
            if (f != null && f.string == null && !f.isNull) return AIVerdict.UNREADABLE to emptyList()
            val title = AIText.trim(rawTitle)
            if (title.isEmpty() || AIText.count(title) > TITLE_LIMIT) return AIVerdict.UNREADABLE to emptyList()
            val slice = AIText.trim(text)
            if (slice.isEmpty() || !AIText.contains(capture, slice)) return AIVerdict.NOT_A_SLICE to emptyList()
            val folder = f?.string?.let { name -> folders.firstOrNull { it.name == name }?.id }
            pieces.add(Piece(slice, title, folder))
        }
        return AIVerdict.OK to pieces
    }
}

object AIWhy {
    const val IDEA_CHARS = 500
    const val CANDIDATE_CHARS = 200
    const val CANDIDATES = 5
    const val REASON_LIMIT = 120
    const val MAX_OUTPUT = 600

    /** The new idea's title and first 500 characters, and up to 5 candidates' titles and first 200, numbered from 1. */
    fun payload(idea: AIIdea, candidates: List<AIIdea>): Pair<AIPayload, List<AIIdea>> {
        val sent = candidates.take(CANDIDATES)
        val text = AIText.prefix(idea.text, IDEA_CHARS)
        var chars = AIText.count(idea.title) + AIText.count(text)
        val list = sent.mapIndexed { k, c ->
            val t = AIText.prefix(c.text, CANDIDATE_CHARS)
            chars += AIText.count(c.title) + AIText.count(t)
            AIJSON.make("n" to AIJSON.Num((k + 1).toDouble()), "title" to AIJSON.Str(c.title), "text" to AIJSON.Str(t))
        }
        val input = AIJSON.make("idea" to AIJSON.make("title" to AIJSON.Str(idea.title), "text" to AIJSON.Str(text)),
            "candidates" to AIJSON.Arr(list)).text
        return AIPayload(input, 1 + sent.size, chars) to sent
    }

    /** Candidate id to reason. */
    fun check(answer: String, sent: List<AIIdea>): Pair<AIVerdict, Map<String, String>> {
        if (AIText.trim(answer).isEmpty()) return AIVerdict.EMPTY to emptyMap()
        val items = AIJSON.answer(answer)?.get("reasons")?.array ?: return AIVerdict.UNREADABLE to emptyMap()
        val out = LinkedHashMap<String, String>()
        for (item in items) {
            val n = item["n"]?.int
            val raw = item["why"]?.string
            if (n == null || raw == null) return AIVerdict.UNREADABLE to emptyMap()
            if (n < 1 || n > sent.size) return AIVerdict.BAD_CITATION to emptyMap()
            val why = AIText.trim(raw)
            if (why.isEmpty() || AIText.count(why) > REASON_LIMIT) return AIVerdict.UNREADABLE to emptyMap()
            out.putIfAbsent(sent[n - 1].id, why)
        }
        return AIVerdict.OK to out
    }
}

object AICitations {
    /** The numbers in square-bracket markers: "[2]", "[1, 3]". */
    fun markers(text: String): List<Int> {
        val s = text.codePoints().toArray()
        val out = ArrayList<Int>()
        var i = 0
        while (i < s.size) {
            if (s[i] != '['.code) { i++; continue }
            var j = i + 1
            val numbers = ArrayList<Int>()
            val digits = StringBuilder()
            var good = false
            while (j < s.size) {
                val c = s[j]
                if (c in '0'.code..'9'.code) digits.appendCodePoint(c)
                else if (c == ','.code && digits.isNotEmpty()) { numbers.add(digits.toString().toIntOrNull() ?: -1); digits.setLength(0) }
                else if (c == ' '.code) { }
                else if (c == ']'.code && digits.isNotEmpty()) { numbers.add(digits.toString().toIntOrNull() ?: -1); good = true; break }
                else break
                j++
            }
            if (good) { out.addAll(numbers); i = j + 1 } else i++
        }
        return out
    }

    /** The ideas cited, in the order the text first cites them and then the list; null when any number was not sent. */
    fun resolve(text: String, cites: List<Int>, sent: Map<Int, String>): List<String>? {
        val ids = ArrayList<String>()
        for (n in markers(text) + cites) {
            val id = sent[n] ?: return null
            if (id !in ids) ids.add(id)
        }
        return ids
    }
}

object AIAsk {
    const val IDEAS = 12
    const val ON_DEVICE_IDEAS = 6
    const val CHARS = 1000
    const val MAX_OUTPUT = 800

    data class Answer(val text: String, val cites: List<String>)

    /** The question, and up to 12 ideas found on the phone (6 on the phone's own model), each its title and up to 1,000 characters. */
    fun payload(question: String, found: List<AIIdea>, onDevice: Boolean): Pair<AIPayload, List<AIIdea>> {
        val sent = found.take(if (onDevice) ON_DEVICE_IDEAS else IDEAS)
        var count = AIText.count(question)
        val list = sent.mapIndexed { k, idea ->
            val t = AIText.prefix(idea.text, CHARS)
            count += AIText.count(idea.title) + AIText.count(t)
            AIJSON.make("n" to AIJSON.Num((k + 1).toDouble()), "title" to AIJSON.Str(idea.title), "text" to AIJSON.Str(t))
        }
        val input = AIJSON.make("question" to AIJSON.Str(question), "ideas" to AIJSON.Arr(list)).text
        return AIPayload(input, sent.size, count) to sent
    }

    fun check(answer: String, sent: List<AIIdea>): Pair<AIVerdict, Answer?> {
        if (AIText.trim(answer).isEmpty()) return AIVerdict.EMPTY to null
        val json = AIJSON.answer(answer)
        val text = json?.get("answer")?.string
        val cites = json?.get("cites")?.array
        val fits = json?.get("nothingFits")?.bool
        if (text == null || cites == null || fits == null) return AIVerdict.UNREADABLE to null
        val numbers = cites.mapNotNull { it.int }
        if (numbers.size != cites.size) return AIVerdict.UNREADABLE to null
        if (fits) return AIVerdict.NOTHING_FITS to null
        val body = AIText.trim(text)
        if (body.isEmpty()) return AIVerdict.EMPTY to null
        val map = sent.mapIndexed { k, idea -> (k + 1) to idea.id }.toMap()
        val ids = AICitations.resolve(body, numbers, map) ?: return AIVerdict.BAD_CITATION to null
        return AIVerdict.OK to Answer(body, ids)
    }
}

object AISumUp {
    const val TOTAL = 24_000
    /** One part's characters: the whole on an account; on the phone's own model, what fits its window with room to answer. */
    const val ACCOUNT_PART = 24_000
    const val ON_DEVICE_PART = 2_400
    const val MAX_OUTPUT = 800

    /** Numbered ideas (from 1, across the parts) in parts of at most [partChars], up to 24,000 in all; the last idea that would pass the total is cut. */
    fun parts(ideas: List<AIIdea>, partChars: Int): List<List<AINumbered>> {
        var left = TOTAL
        val parts = ArrayList<List<AINumbered>>()
        var part = ArrayList<AINumbered>()
        var size = 0
        for ((k, idea) in ideas.withIndex()) {
            if (left <= 0) continue
            val titleChars = AIText.count(idea.title)
            if (titleChars >= left) break
            val one = idea.copy(text = AIText.prefix(idea.text, minOf(left - titleChars, partChars - titleChars)))
            val chars = titleChars + AIText.count(one.text)
            if (size + chars > partChars && part.isNotEmpty()) {
                parts.add(part)
                part = ArrayList()
                size = 0
            }
            part.add(AINumbered(k + 1, one))
            size += chars
            left -= chars
        }
        if (part.isNotEmpty()) parts.add(part)
        return parts
    }

    fun payload(folder: String, part: List<AINumbered>): AIPayload {
        var chars = 0
        val list = part.map { (n, idea) ->
            chars += AIText.count(idea.title) + AIText.count(idea.text)
            AIJSON.make("n" to AIJSON.Num(n.toDouble()), "title" to AIJSON.Str(idea.title), "text" to AIJSON.Str(idea.text))
        }
        return AIPayload(AIJSON.make("folder" to AIJSON.Str(folder), "ideas" to AIJSON.Arr(list)).text, part.size, chars)
    }

    fun check(answer: String, part: List<AINumbered>): Pair<AIVerdict, AIAsk.Answer?> {
        if (AIText.trim(answer).isEmpty()) return AIVerdict.EMPTY to null
        val json = AIJSON.answer(answer)
        val text = json?.get("summary")?.string
        val cites = json?.get("cites")?.array
        if (text == null || cites == null) return AIVerdict.UNREADABLE to null
        val numbers = cites.mapNotNull { it.int }
        if (numbers.size != cites.size) return AIVerdict.UNREADABLE to null
        val body = AIText.trim(text)
        if (body.isEmpty()) return AIVerdict.EMPTY to null
        val map = part.associate { it.n to it.idea.id }
        val ids = AICitations.resolve(body, numbers, map) ?: return AIVerdict.BAD_CITATION to null
        return AIVerdict.OK to AIAsk.Answer(body, ids)
    }
}

/** An idea with its place in the index: its vector when the table covers its words, else null (IdeaEchoes stands in). */
data class AINeighbour(val id: String, val text: String, val made: Double, val vector: FloatArray?) {
    override fun equals(other: Any?) = other is AINeighbour && other.id == id && other.text == text && other.made == made &&
        (if (vector == null) other.vector == null else other.vector != null && vector.contentEquals(other.vector))
    override fun hashCode() = id.hashCode()
}

object AILinks {
    const val LIMIT = 5
    /** How close in meaning a candidate must be. */
    const val CLOSE = 0.5

    fun cosine(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (k in 0 until minOf(a.size, b.size)) s += a[k].toDouble() * b[k].toDouble()
        return s
    }

    private val order = Comparator<Pair<Double, AINeighbour>> { a, b ->
        when {
            Math.abs(a.first - b.first) > 1e-9 -> b.first.compareTo(a.first)
            a.second.made != b.second.made -> b.second.made.compareTo(a.second.made)
            else -> a.second.id.compareTo(b.second.id)
        }
    }

    /** Older ideas close in meaning, best first (then newer, then by id). */
    fun byMeaning(vector: FloatArray, current: String?, made: Double, ideas: List<AINeighbour>, except: Set<String>, limit: Int, close: Double = CLOSE): List<String> =
        ideas.mapNotNull { n ->
            val v = n.vector
            if (n.id == current || n.id in except || n.made > made || v == null) return@mapNotNull null
            val s = cosine(vector, v)
            if (s >= close) s to n else null
        }.sortedWith(order).take(limit).map { it.second.id }

    /** By meaning first, then by uncommon shared words (IdeaEchoes); for a text the table does not cover, IdeaEchoes alone. */
    fun suggest(text: String, vector: FloatArray?, current: String?, made: Double, ideas: List<AINeighbour>,
                connected: Set<String> = emptySet(), limit: Int = LIMIT): List<String> {
        val out = ArrayList<String>()
        if (vector != null) out.addAll(byMeaning(vector, current, made, ideas, connected, limit))
        val echoes = IdeaEchoes.Index(ideas.map { IdeaEchoes.Idea(it.id, it.text, it.made) }).echoes(text, current, made, connected, limit)
        for (id in echoes) if (out.size < limit && id !in out) out.add(id)
        return out
    }

    internal fun ranked(scored: List<Pair<Double, AINeighbour>>) = scored.sortedWith(order)
}

object AIResurface {
    const val LIMIT = 3
    const val DAY = 86400.0

    /** Up to 3 older ideas close to the last day's captures (O5: off until turned on). */
    fun pick(ideas: List<AINeighbour>, now: Double, limit: Int = LIMIT): List<String> {
        val recent = ideas.filter { it.made > now - DAY }
        val older = ideas.filter { it.made <= now - DAY }
        val best = HashMap<String, Double>()
        for (r in recent) {
            val v = r.vector ?: continue
            for (o in older) {
                val w = o.vector ?: continue
                val s = AILinks.cosine(v, w)
                if (s >= AILinks.CLOSE && s > (best[o.id] ?: -2.0)) best[o.id] = s
            }
        }
        val out = AILinks.ranked(older.filter { best.containsKey(it.id) }.map { best.getValue(it.id) to it }).take(limit).map { it.second.id }.toMutableList()
        // a capture the table does not cover finds its older ideas by words
        val index = IdeaEchoes.Index(older.map { IdeaEchoes.Idea(it.id, it.text, it.made) })
        for (r in recent) {
            if (r.vector != null) continue
            for (id in index.echoes(r.text, null, now, emptySet(), limit)) if (out.size < limit && id !in out) out.add(id)
        }
        return out
    }
}
