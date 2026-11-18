package com.cramdown.sorta.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

// Embeddings on the phone (S08 A7): one fixed table, the same file in both apps: Model2Vec potion-base-4M (MIT), int8
// with one scale per row (ai/embeddings). The tokeniser and pooling follow ai/embeddings/reference.py step for step,
// with no ML runtime, so the same text gives the same vector here and on iOS. Nothing is trained, and vectors never
// leave the phone.

class AIEmbeddings(table: ByteArray, vocab: String) {
    companion object {
        const val UNK = 1
        const val MAX_TOKENS = 512
        const val MAX_WORD = 100
        const val TABLE = "potion-base-4M-int8.bin"

        /** The table in [dir] of [files]: "potion-base-4M" in the app's assets, "embeddings/potion-base-4M" in the repository. */
        fun load(files: AIFiles, dir: String): AIEmbeddings =
            AIEmbeddings(files.read("$dir/$TABLE"), String(files.read("$dir/vocab.txt"), Charsets.UTF_8))

        private fun isControl(c: Int): Boolean {
            if (c == '\t'.code || c == '\n'.code || c == '\r'.code) return false
            return when (Character.getType(c).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.PRIVATE_USE, Character.SURROGATE -> true
                else -> false
            }
        }

        private fun isHan(v: Int) = v in 0x4E00..0x9FFF || v in 0x3400..0x4DBF || v in 0x20000..0x2A6DF || v in 0x2A700..0x2B73F ||
            v in 0x2B740..0x2B81F || v in 0x2B920..0x2CEAF || v in 0xF900..0xFAFF || v in 0x2F800..0x2FA1F

        private fun isPunctuation(v: Int): Boolean {
            if (v in 33..47 || v in 58..64 || v in 91..96 || v in 123..126) return true
            return when (Character.getType(v).toByte()) {
                Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.END_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.START_PUNCTUATION -> true
                else -> false
            }
        }

        fun normalize(text: String): IntArray {
            val cleaned = StringBuilder()
            for (c in text.codePoints().toArray()) {
                if (c == 0 || c == 0xFFFD || isControl(c)) continue
                if (AIText.isSpace(c)) { cleaned.append(' '); continue }
                if (isHan(c)) { cleaned.append(' ').appendCodePoint(c).append(' '); continue }
                cleaned.appendCodePoint(c)
            }
            val decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            val stripped = StringBuilder()
            for (c in decomposed.codePoints().toArray()) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) stripped.appendCodePoint(c)
            // Java lowers a final capital sigma to ς, as reference.py does; Swift gives σ (no fixture has one)
            return stripped.toString().lowercase(java.util.Locale.ROOT).codePoints().toArray()
        }

        fun preTokenize(scalars: IntArray): List<String> {
            val words = ArrayList<String>()
            val word = StringBuilder()
            for (c in scalars) {
                if (AIText.isSpace(c)) {
                    if (word.isNotEmpty()) { words.add(word.toString()); word.setLength(0) }
                } else if (isPunctuation(c)) {
                    if (word.isNotEmpty()) { words.add(word.toString()); word.setLength(0) }
                    words.add(String(Character.toChars(c)))
                } else {
                    word.appendCodePoint(c)
                }
            }
            if (word.isNotEmpty()) words.add(word.toString())
            return words
        }

        /** At least half of the text's letters are Latin (U+0041 to U+024F). */
        fun covered(text: String): Boolean {
            var letters = 0
            var latin = 0
            for (c in text.codePoints().toArray()) {
                when (Character.getType(c).toByte()) {
                    Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER -> {
                        letters++
                        if (c in 0x41..0x24F) latin++
                    }
                }
            }
            return letters > 0 && 2 * latin >= letters
        }
    }

    val rows: Int
    val dims: Int
    private val scales: FloatArray
    private val values: ByteArray
    private val vocab: HashMap<String, Int>

    init {
        val b = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        val bad = AIError.provider(AIErrorKind.MODEL_UNAVAILABLE)
        if (table.size < 16 || String(table, 0, 4, Charsets.US_ASCII) != "SRTE" || b.getInt(4) != 1) throw bad
        rows = b.getInt(8)
        dims = b.getInt(12)
        if (table.size.toLong() != 16L + rows * 4L + rows.toLong() * dims) throw bad
        scales = FloatArray(rows) { b.getFloat(16 + it * 4) }
        values = table.copyOfRange(16 + rows * 4, table.size)
        val lines = vocab.split("\n").let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        if (lines.size != rows) throw bad
        this.vocab = HashMap(rows * 2)
        lines.forEachIndexed { i, token -> this.vocab[token] = i }
    }

    fun wordPiece(word: String): List<Int> {
        val cps = word.codePoints().toArray()
        if (cps.size > MAX_WORD) return listOf(UNK)
        val ids = ArrayList<Int>()
        var start = 0
        while (start < cps.size) {
            var end = cps.size
            var found: Int? = null
            while (start < end) {
                val piece = String(cps, start, end - start)
                val id = vocab[if (start == 0) piece else "##$piece"]
                if (id != null) { found = id; break }
                end--
            }
            ids.add(found ?: return listOf(UNK))
            start = end
        }
        return ids
    }

    /** The ids pooled: [UNK] dropped, the first 512 kept. */
    fun tokenIds(text: String): List<Int> =
        preTokenize(normalize(text)).flatMap { wordPiece(it) }.filter { it != UNK }.take(MAX_TOKENS)

    /** The mean of the rows, scaled to length 1; null with no ids. */
    fun vector(ids: List<Int>): DoubleArray? {
        if (ids.isEmpty()) return null
        val total = DoubleArray(dims)
        for (id in ids) {
            val s = scales[id].toDouble()
            val base = id * dims
            for (d in 0 until dims) total[d] += values[base + d].toDouble() * s
        }
        val n = ids.size.toDouble()
        val mean = DoubleArray(dims) { total[it] / n }
        val length = Math.sqrt(mean.fold(0.0) { a, v -> a + v * v })
        if (length <= 0) return null
        return DoubleArray(dims) { mean[it] / length }
    }

    /** A text's vector, or null when the table does not cover it (then IdeaEchoes' match stands in). */
    fun vector(text: String): DoubleArray? = if (covered(text)) vector(tokenIds(text)) else null
}

/**
 * The index: derived from the ideas, rebuilt off the main thread. It lives in noBackupFilesDir, so it is never in a
 * backup, and never in a capsule (Capsule writes the ideas, not this file).
 */
class AIIndex(val file: File) {
    data class Entry(val id: String, val vector: FloatArray?) {
        override fun equals(other: Any?) = other is Entry && other.id == id &&
            (if (vector == null) other.vector == null else other.vector != null && vector.contentEquals(other.vector))
        override fun hashCode() = id.hashCode()
    }

    companion object {
        const val NAME = "ai-index.bin"

        /** Every idea's vector, from its title and text. */
        fun build(ideas: List<AIIdea>, embeddings: AIEmbeddings): List<Entry> {
            AIThread.requireBackground()
            return ideas.map { idea ->
                Entry(idea.id, embeddings.vector(idea.title + "\n" + idea.text)?.let { v -> FloatArray(v.size) { v[it].toFloat() } })
            }
        }
    }

    /** Rebuilds the file from a snapshot of the store's ideas. */
    fun rebuild(ideas: List<AIIdea>, embeddings: AIEmbeddings): List<Entry> {
        val entries = build(ideas, embeddings)
        var size = 8
        for (e in entries) size += 8 + e.id.toByteArray(Charsets.UTF_8).size + 4 * (e.vector?.size ?: 0)
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("SRTI".toByteArray(Charsets.US_ASCII)).putInt(entries.size)
        for (e in entries) {
            val id = e.id.toByteArray(Charsets.UTF_8)
            b.putInt(id.size).put(id).putInt(e.vector?.size ?: 0)
            e.vector?.forEach { b.putFloat(it) }
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(b.array())
        if (!tmp.renameTo(file)) throw AIError.of(AIError.Reason.TRANSPORT)
        return entries
    }

    fun load(): List<Entry>? {
        if (!file.exists()) return null
        val data = file.readBytes()
        if (data.size < 8 || String(data, 0, 4, Charsets.US_ASCII) != "SRTI") return null
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        b.position(4)
        return try {
            val count = b.getInt()
            List(count) {
                val id = ByteArray(b.getInt()).also { b.get(it) }
                val dims = b.getInt()
                val v = FloatArray(dims) { b.getFloat() }
                Entry(String(id, Charsets.UTF_8), if (dims == 0) null else v)
            }
        } catch (_: Exception) { null }
    }
}
