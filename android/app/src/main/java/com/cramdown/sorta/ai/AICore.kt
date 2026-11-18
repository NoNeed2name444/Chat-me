package com.cramdown.sorta.ai

import android.os.Looper

// The AI core (S08): providers, the cap, consent and the operations, with no screen and no string a person sees
// (S08u holds the UI). The same rules as iOS's ios/Sorta/AI, read from the same fixtures (ai/fixtures). Every call
// is synchronous and refuses the main thread (AIThread): the frame is 8.3 ms, so callers run it on a background thread.

/** Where an answer comes from: the phone's own model, or an account. */
enum class AIProviderId(val raw: String) {
    APPLE("apple"), MLKIT("mlkit"), OPENROUTER("openrouter"), ANTHROPIC("anthropic"), OPENAI("openai"), COMPATIBLE("compatible");

    /** The phone's own models: free, and nothing leaves the phone. */
    val onDevice: Boolean get() = this == APPLE || this == MLKIT

    companion object {
        fun of(raw: String?): AIProviderId? = entries.firstOrNull { it.raw == raw }
    }
}

/** The few plain kinds every provider's errors map to. */
enum class AIErrorKind(val raw: String) {
    CREDENTIAL_REFUSED("credentialRefused"), RATE_LIMITED("rateLimited"), OUT_OF_CREDIT("outOfCredit"),
    TRY_LATER("tryLater"), MODEL_UNAVAILABLE("modelUnavailable");

    companion object {
        fun of(raw: String?): AIErrorKind? = entries.firstOrNull { it.raw == raw }
    }
}

/** What went wrong, as iOS's AIError: [kind] is set for [Reason.PROVIDER]. Never carries a credential. */
class AIError(val reason: Reason, val kind: AIErrorKind? = null) : Exception(if (kind == null) reason.name else "${reason.name} ${kind.raw}") {
    enum class Reason {
        /** The provider answered with an error, or the model is not on this phone. */
        PROVIDER,
        /** No answer reached the phone; the provider may still have counted the call. */
        TRANSPORT,
        MAIN_THREAD,
        /** No account connected, or no credential stored. */
        NOT_CONNECTED,
        /** The feature is off, or the person has not agreed to what it sends. */
        NOT_ALLOWED,
        CAP_REACHED,
        UNKNOWN_PRICE,
        /** A compatible service's address that is not https. */
        INSECURE_ADDRESS,
        SIGN_IN_REFUSED,
        CANCELLED,
        /** The Keystore refused. */
        VAULT,
    }

    override fun equals(other: Any?) = other is AIError && other.reason == reason && other.kind == kind
    override fun hashCode() = reason.hashCode() * 31 + (kind?.hashCode() ?: 0)

    companion object {
        fun provider(kind: AIErrorKind) = AIError(Reason.PROVIDER, kind)
        fun of(reason: Reason) = AIError(reason)
    }
}

/** One call: instructions, the input, and maybe a JSON schema (its name and text) for the answer. */
data class AIRequest(
    val model: String,
    val instructions: String,
    val input: String,
    val schemaName: String? = null,
    val schema: String? = null,
    val maxOutput: Int,
)

/** Tokens used, and what the provider says the call cost in picodollars (OpenRouter). */
data class AIUsage(val inputTokens: Int, val outputTokens: Int, val costPico: Long? = null)

data class AIResponse(val text: String, val usage: AIUsage)

/** One provider, on the phone or behind an account. [complete] blocks, and throws MAIN_THREAD on the main thread. */
interface AIProvider {
    val id: AIProviderId
    /** Whether a JSON schema goes with the request (response formats on the accounts that take them). */
    val sendsSchema: Boolean
    fun complete(request: AIRequest): AIResponse
}

object AIThread {
    /** AI calls, embeddings and index work never run on the main thread. */
    fun requireBackground() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw AIError.of(AIError.Reason.MAIN_THREAD)
    }
}

/** Text, the same way on both platforms: counted, cut and compared as Unicode code points. */
object AIText {
    /** Unicode's White_Space, which both apps trim and split on. */
    fun isSpace(c: Int): Boolean = c in 0x09..0x0D || c == 0x20 || c == 0x85 || c == 0xA0 || c == 0x1680 || c in 0x2000..0x200A ||
        c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000

    fun count(s: String): Int = s.codePointCount(0, s.length)

    fun prefix(s: String, n: Int): String {
        if (n <= 0) return ""
        return if (n >= count(s)) s else s.substring(0, s.offsetByCodePoints(0, n))
    }

    fun trim(s: String): String {
        val c = s.codePoints().toArray()
        var a = 0
        var b = c.size
        while (a < b && isSpace(c[a])) a++
        while (b > a && isSpace(c[b - 1])) b--
        return String(c, a, b - a)
    }

    fun utf8(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    /** Whether [part] occurs in [whole], code point for code point (no canonical equivalence). */
    fun contains(whole: String, part: String): Boolean = whole.indexOf(part) >= 0
}

/**
 * A JSON value, parsed strictly by hand and written compactly with its keys in order, so a model's answer gets the
 * same verdict here and on iOS (true is never a number, and nothing lenient is accepted).
 */
sealed class AIJSON {
    object Null : AIJSON()
    data class Bool(val value: Boolean) : AIJSON()
    data class Num(val value: Double) : AIJSON()
    data class Str(val value: String) : AIJSON()
    data class Arr(val items: List<AIJSON>) : AIJSON()
    class Obj(val map: Map<String, AIJSON>, val keys: List<String>) : AIJSON() {
        override fun equals(other: Any?) = other is Obj && other.map == map
        override fun hashCode() = map.hashCode()
    }

    operator fun get(key: String): AIJSON? = (this as? Obj)?.map?.get(key)
    val string: String? get() = (this as? Str)?.value
    val bool: Boolean? get() = (this as? Bool)?.value
    val number: Double? get() = (this as? Num)?.value
    val array: List<AIJSON>? get() = (this as? Arr)?.items
    val isNull: Boolean get() = this === Null
    /** A whole number, never a bool. */
    val int: Int? get() = (this as? Num)?.value?.let { if (it == Math.floor(it) && Math.abs(it) < 9.0e15) it.toLong().toInt() else null }

    /** Compact JSON, keys in the order they were given. */
    val text: String get() = StringBuilder().also { write(it) }.toString()
    val data: ByteArray get() = text.toByteArray(Charsets.UTF_8)

    private fun write(out: StringBuilder) {
        when (this) {
            Null -> out.append("null")
            is Bool -> out.append(if (value) "true" else "false")
            is Num -> if (value == Math.floor(value) && Math.abs(value) < 9.0e15) out.append(value.toLong()) else out.append(value)
            is Str -> quote(value, out)
            is Arr -> {
                out.append('[')
                items.forEachIndexed { k, v -> if (k > 0) out.append(','); v.write(out) }
                out.append(']')
            }
            is Obj -> {
                out.append('{')
                keys.forEachIndexed { k, key -> if (k > 0) out.append(','); quote(key, out); out.append(':'); map.getValue(key).write(out) }
                out.append('}')
            }
        }
    }

    companion object {
        /** An object with its keys in this order. */
        fun make(vararg pairs: Pair<String, AIJSON>): AIJSON = make(pairs.toList())

        fun make(pairs: List<Pair<String, AIJSON>>): AIJSON {
            val map = LinkedHashMap<String, AIJSON>()
            val keys = ArrayList<String>()
            for ((k, v) in pairs) {
                if (!map.containsKey(k)) keys.add(k)
                map[k] = v
            }
            return Obj(map, keys)
        }

        fun parse(text: String): AIJSON? {
            val p = Parser(text.codePoints().toArray())
            p.space()
            val v = p.value(0) ?: return null
            p.space()
            return if (p.done) v else null
        }

        fun parse(data: ByteArray): AIJSON? {
            val decoder = Charsets.UTF_8.newDecoder()
            val text = try { decoder.decode(java.nio.ByteBuffer.wrap(data)).toString() } catch (_: java.nio.charset.CharacterCodingException) { return null }
            return parse(text)
        }

        /** A model's answer, with a ```json fence around it taken off. */
        fun answer(raw: String): AIJSON? {
            var text = AIText.trim(raw)
            if (text.startsWith("```")) {
                val lines = text.split("\n").toMutableList()
                lines.removeAt(0)
                if (lines.isNotEmpty() && AIText.trim(lines.last()) == "```") lines.removeAt(lines.size - 1)
                text = lines.joinToString("\n")
            }
            return parse(text)
        }

        private fun quote(s: String, out: StringBuilder) {
            out.append('"')
            var i = 0
            while (i < s.length) {
                val c = s.codePointAt(i)
                when (c) {
                    '"'.code -> out.append("\\\"")
                    '\\'.code -> out.append("\\\\")
                    '\n'.code -> out.append("\\n")
                    '\r'.code -> out.append("\\r")
                    '\t'.code -> out.append("\\t")
                    else -> if (c < 0x20) out.append("\\u").append(Integer.toHexString(c).padStart(4, '0')) else out.appendCodePoint(c)
                }
                i += Character.charCount(c)
            }
            out.append('"')
        }
    }

    private class Parser(val s: IntArray) {
        var i = 0
        val done: Boolean get() = i == s.size

        fun space() {
            while (i < s.size && (s[i] == 0x20 || s[i] == 0x09 || s[i] == 0x0A || s[i] == 0x0D)) i++
        }

        fun eat(word: String): Boolean {
            val w = word.codePoints().toArray()
            if (i + w.size > s.size) return false
            for (k in w.indices) if (s[i + k] != w[k]) return false
            i += w.size
            return true
        }

        fun value(depth: Int): AIJSON? {
            if (depth >= 64 || i >= s.size) return null
            return when (s[i]) {
                '{'.code -> obj(depth)
                '['.code -> arr(depth)
                '"'.code -> str()?.let { Str(it) }
                't'.code -> if (eat("true")) Bool(true) else null
                'f'.code -> if (eat("false")) Bool(false) else null
                'n'.code -> if (eat("null")) Null else null
                else -> num()
            }
        }

        fun obj(depth: Int): AIJSON? {
            i++
            val pairs = ArrayList<Pair<String, AIJSON>>()
            space()
            if (i < s.size && s[i] == '}'.code) { i++; return Obj(emptyMap(), emptyList()) }
            while (true) {
                space()
                if (i >= s.size || s[i] != '"'.code) return null
                val key = str() ?: return null
                space()
                if (i >= s.size || s[i] != ':'.code) return null
                i++
                space()
                val v = value(depth + 1) ?: return null
                pairs.add(key to v)
                space()
                if (i >= s.size) return null
                if (s[i] == ','.code) { i++; continue }
                if (s[i] == '}'.code) { i++; return make(pairs) }
                return null
            }
        }

        fun arr(depth: Int): AIJSON? {
            i++
            val items = ArrayList<AIJSON>()
            space()
            if (i < s.size && s[i] == ']'.code) { i++; return Arr(emptyList()) }
            while (true) {
                space()
                items.add(value(depth + 1) ?: return null)
                space()
                if (i >= s.size) return null
                if (s[i] == ','.code) { i++; continue }
                if (s[i] == ']'.code) { i++; return Arr(items) }
                return null
            }
        }

        fun hex4(): Int? {
            if (i + 4 > s.size) return null
            var v = 0
            repeat(4) {
                val d = Character.digit(s[i], 16)
                if (d < 0 || s[i] > 0x7F) return null
                v = v * 16 + d
                i++
            }
            return v
        }

        fun str(): String? {
            i++
            val out = StringBuilder()
            while (i < s.size) {
                val c = s[i]
                if (c == '"'.code) { i++; return out.toString() }
                if (c < 0x20) return null
                if (c != '\\'.code) { out.appendCodePoint(c); i++; continue }
                i++
                if (i >= s.size) return null
                val e = s[i]
                i++
                when (e) {
                    '"'.code -> out.append('"')
                    '\\'.code -> out.append('\\')
                    '/'.code -> out.append('/')
                    'b'.code -> out.append('\b')
                    'f'.code -> out.append('\u000C')
                    'n'.code -> out.append('\n')
                    'r'.code -> out.append('\r')
                    't'.code -> out.append('\t')
                    'u'.code -> {
                        val hi = hex4() ?: return null
                        if (hi in 0xD800..0xDBFF) {
                            if (!eat("\\u")) return null
                            val lo = hex4() ?: return null
                            if (lo !in 0xDC00..0xDFFF) return null
                            out.appendCodePoint(0x10000 + ((hi - 0xD800) shl 10) + (lo - 0xDC00))
                        } else {
                            if (hi in 0xDC00..0xDFFF) return null
                            out.appendCodePoint(hi)
                        }
                    }
                    else -> return null
                }
            }
            return null
        }

        fun num(): AIJSON? {
            val start = i
            fun digit(k: Int) = k < s.size && s[k] in '0'.code..'9'.code
            if (i < s.size && s[i] == '-'.code) i++
            if (!digit(i)) return null
            if (s[i] == '0'.code) i++ else while (digit(i)) i++
            if (i < s.size && s[i] == '.'.code) {
                i++
                if (!digit(i)) return null
                while (digit(i)) i++
            }
            if (i < s.size && (s[i] == 'e'.code || s[i] == 'E'.code)) {
                i++
                if (i < s.size && (s[i] == '+'.code || s[i] == '-'.code)) i++
                if (!digit(i)) return null
                while (digit(i)) i++
            }
            return String(s, start, i - start).toDoubleOrNull()?.let { Num(it) }
        }
    }
}
