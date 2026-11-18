package com.cramdown.sorta.addons

import java.math.BigDecimal

// Add-ons (S07b): JSON as the add-on API moves it.
//
// The engine hands every call's arguments over as JSON text and takes JSON text back. Sorta writes it
// canonically (keys sorted by UTF-16 code unit, no spaces, as JavaScript's JSON.stringify writes each
// value), so iOS, Android and the kit's harness give the same bytes (addons/conformance). This file
// follows ios/Sorta/Addons/AddonJSON.swift line for line.

class AddonJsonError : Exception()

sealed class AddonJson {
    object Null : AddonJson()
    data class Bool(val value: Boolean) : AddonJson()
    data class Num(val value: Double) : AddonJson()
    data class Str(val value: String) : AddonJson()
    data class Arr(val items: List<AddonJson>) : AddonJson()
    data class Obj(val pairs: List<Pair<String, AddonJson>>) : AddonJson()

    operator fun get(key: String): AddonJson? = (this as? Obj)?.pairs?.lastOrNull { it.first == key }?.second
    operator fun get(index: Int): AddonJson? = (this as? Arr)?.items?.getOrNull(index)
    fun has(key: String) = get(key) != null

    val string: String? get() = (this as? Str)?.value
    val bool: Boolean? get() = (this as? Bool)?.value
    val number: Double? get() = (this as? Num)?.value
    val array: List<AddonJson>? get() = (this as? Arr)?.items
    val isObject: Boolean get() = this is Obj
    val isNull: Boolean get() = this is Null
    val keys: List<String> get() = (this as? Obj)?.pairs?.map { it.first } ?: emptyList()

    /** A whole number JavaScript holds exactly (Number.isSafeInteger). */
    val integer: Long? get() {
        val n = (this as? Num)?.value ?: return null
        if (Math.rint(n) != n || Math.abs(n) > 9_007_199_254_740_991.0) return null
        return n.toLong()
    }

    /** The strings of an array of strings, else null. */
    val strings: List<String>? get() {
        val items = array ?: return null
        val out = ArrayList<String>(items.size)
        for (item in items) out.add(item.string ?: return null)
        return out
    }

    /** Canonical JSON text: keys sorted by UTF-16 code unit, no spaces. */
    val canonical: String get() = StringBuilder().also { write(it) }.toString()

    fun write(out: StringBuilder) {
        when (this) {
            is Null -> out.append("null")
            is Bool -> out.append(if (value) "true" else "false")
            is Num -> out.append(number(value))
            is Str -> quote(value, out)
            is Arr -> {
                out.append('[')
                items.forEachIndexed { k, item -> if (k > 0) out.append(','); item.write(out) }
                out.append(']')
            }
            is Obj -> {
                out.append('{')
                pairs.sortedWith { a, b -> a.first.compareTo(b.first) }.forEachIndexed { k, pair ->
                    if (k > 0) out.append(',')
                    quote(pair.first, out)
                    out.append(':')
                    pair.second.write(out)
                }
                out.append('}')
            }
        }
    }

    companion object {
        fun strings(list: List<String>): AddonJson = Arr(list.map { Str(it) })
        fun obj(vararg pairs: Pair<String, AddonJson>): AddonJson = Obj(pairs.toList())
        fun num(n: Number): AddonJson = Num(n.toDouble())
        fun str(s: String): AddonJson = Str(s)

        /** [keyTwice] false refuses an object that names a key twice (the package checker's strict JSON); otherwise the last one wins, as JSON.parse. */
        fun parse(text: String, keyTwice: Boolean = true): AddonJson {
            val p = Parser(text, keyTwice)
            p.space()
            val v = p.value(0)
            p.space()
            if (p.i != text.length) throw AddonJsonError()
            return v
        }

        fun parseOrNull(text: String): AddonJson? = try { parse(text) } catch (e: AddonJsonError) { null }

        /** A number as JavaScript writes it (Number::toString). */
        fun number(n: Double): String {
            if (n.isNaN() || n.isInfinite()) return "null"
            if (n == 0.0) return "0"
            if (Math.rint(n) == n && Math.abs(n) < 9.007199254740992E15) return BigDecimal(n).toBigInteger().toString()
            // the shortest digits that read back as the same number; Double.toString is not always the shortest
            val exact = BigDecimal(Math.abs(n))
            var d = exact
            for (p in 1..17) {
                d = exact.round(java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN))
                if (d.toDouble() == Math.abs(n)) break
            }
            d = d.stripTrailingZeros()
            val digits = d.unscaledValue().toString()
            val k = digits.length
            val e = k - d.scale() // the value is 0.digits x 10^e
            val sign = if (n < 0) "-" else ""
            return sign + when {
                e in k..21 -> digits + "0".repeat(e - k)
                e in 1..21 -> digits.substring(0, e) + "." + digits.substring(e)
                e in -5..0 -> "0." + "0".repeat(-e) + digits
                else -> {
                    val x = e - 1
                    val exp = (if (x < 0) "-" else "+") + Math.abs(x)
                    if (k == 1) digits + "e" + exp else digits.substring(0, 1) + "." + digits.substring(1) + "e" + exp
                }
            }
        }

        /** A string as JSON.stringify writes it. */
        fun quote(s: String, out: StringBuilder) {
            out.append('"')
            for (c in s) {
                when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (c.code < 0x20) out.append(String.format("\\u%04x", c.code)) else out.append(c)
                }
            }
            out.append('"')
        }

        fun quoted(s: String): String = StringBuilder().also { quote(s, it) }.toString()
    }

    private class Parser(val t: String, val keyTwice: Boolean) {
        var i = 0

        fun space() { while (i < t.length && (t[i] == ' ' || t[i] == '\t' || t[i] == '\n' || t[i] == '\r')) i++ }

        fun value(depth: Int): AddonJson {
            if (depth >= 512 || i >= t.length) throw AddonJsonError()
            when (t[i]) {
                '{' -> {
                    i++
                    val pairs = ArrayList<Pair<String, AddonJson>>()
                    space()
                    if (i < t.length && t[i] == '}') { i++; return Obj(pairs) }
                    while (true) {
                        space()
                        if (i >= t.length || t[i] != '"') throw AddonJsonError()
                        val key = string()
                        space()
                        if (i >= t.length || t[i] != ':') throw AddonJsonError()
                        i++
                        space()
                        val v = value(depth + 1)
                        if (!keyTwice && pairs.any { it.first == key }) throw AddonJsonError()
                        pairs.removeAll { it.first == key }
                        pairs.add(key to v)
                        space()
                        if (i >= t.length) throw AddonJsonError()
                        if (t[i] == ',') { i++; continue }
                        if (t[i] == '}') { i++; return Obj(pairs) }
                        throw AddonJsonError()
                    }
                }
                '[' -> {
                    i++
                    val items = ArrayList<AddonJson>()
                    space()
                    if (i < t.length && t[i] == ']') { i++; return Arr(items) }
                    while (true) {
                        space()
                        items.add(value(depth + 1))
                        space()
                        if (i >= t.length) throw AddonJsonError()
                        if (t[i] == ',') { i++; continue }
                        if (t[i] == ']') { i++; return Arr(items) }
                        throw AddonJsonError()
                    }
                }
                '"' -> return Str(string())
                't' -> { word("true"); return Bool(true) }
                'f' -> { word("false"); return Bool(false) }
                'n' -> { word("null"); return Null }
                else -> return Num(number())
            }
        }

        fun word(w: String) {
            if (!t.startsWith(w, i)) throw AddonJsonError()
            i += w.length
        }

        fun string(): String {
            i++
            val out = StringBuilder()
            while (true) {
                if (i >= t.length) throw AddonJsonError()
                val c = t[i++]
                if (c == '"') break
                if (c.code < 0x20) throw AddonJsonError()
                if (c != '\\') { out.append(c); continue }
                if (i >= t.length) throw AddonJsonError()
                when (t[i++]) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (i + 4 > t.length) throw AddonJsonError()
                        val hex = t.substring(i, i + 4)
                        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) throw AddonJsonError()
                        out.append(hex.toInt(16).toChar())
                        i += 4
                    }
                    else -> throw AddonJsonError()
                }
            }
            return out.toString()
        }

        fun digits(): Int {
            val s = i
            while (i < t.length && t[i] in '0'..'9') i++
            return i - s
        }

        fun number(): Double {
            val start = i
            if (i < t.length && t[i] == '-') i++
            if (i >= t.length) throw AddonJsonError()
            if (t[i] == '0') i++ else if (digits() == 0) throw AddonJsonError()
            if (i < t.length && t[i] == '.') {
                i++
                if (digits() == 0) throw AddonJsonError()
            }
            if (i < t.length && (t[i] == 'e' || t[i] == 'E')) {
                i++
                if (i < t.length && (t[i] == '+' || t[i] == '-')) i++
                if (digits() == 0) throw AddonJsonError()
            }
            return t.substring(start, i).toDoubleOrNull() ?: throw AddonJsonError()
        }
    }
}

/** Text rules every platform shares. */
object AddonText {
    /** JavaScript's string order: by UTF-16 code unit, which is Kotlin's own. */
    fun less(a: String, b: String): Boolean = a < b

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C'

    /** Trimmed of ASCII white space only. */
    fun trim(s: String): String {
        var i = 0
        var j = s.length
        while (i < j && isSpace(s[i])) i++
        while (j > i && isSpace(s[j - 1])) j--
        return s.substring(i, j)
    }

    /** The words between runs of ASCII white space. */
    fun words(s: String): List<String> {
        val out = ArrayList<String>()
        var start = -1
        for (k in s.indices) {
            if (isSpace(s[k])) {
                if (start >= 0) out.add(s.substring(start, k))
                start = -1
            } else if (start < 0) start = k
        }
        if (start >= 0) out.add(s.substring(start))
        return out
    }

    fun utf8Count(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun pad(v: Long, n: Int) = v.toString().padStart(n, '0')

    /** A time as Date.prototype.toISOString writes it. */
    fun iso(ms: Long): String {
        var days = Math.floorDiv(ms, 86_400_000L)
        val rest = Math.floorMod(ms, 86_400_000L)
        val z = days + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + (if (month <= 2) 1 else 0)
        days = 0
        val y = if (year in 0..9999) pad(year, 4) else (if (year < 0) "-" else "+") + pad(Math.abs(year), 6)
        return "$y-${pad(month, 2)}-${pad(day, 2)}T${pad(rest / 3_600_000, 2)}:${pad(rest / 60000 % 60, 2)}:${pad(rest / 1000 % 60, 2)}.${pad(rest % 1000, 3)}Z"
    }

    /** "YYYY-MM-DDTHH:MM:SS.mmmZ" back to milliseconds; null for any other form. */
    fun parseIso(s: String): Long? {
        if (s.length != 24 || s[4] != '-' || s[7] != '-' || s[10] != 'T' || s[13] != ':' || s[16] != ':' || s[19] != '.' || s[23] != 'Z') return null
        fun num(a: Int, b: Int): Long? {
            var v = 0L
            for (k in a until b) {
                if (s[k] !in '0'..'9') return null
                v = v * 10 + (s[k] - '0')
            }
            return v
        }
        val y = num(0, 4) ?: return null
        val m = num(5, 7) ?: return null
        val d = num(8, 10) ?: return null
        val h = num(11, 13) ?: return null
        val mi = num(14, 16) ?: return null
        val sec = num(17, 19) ?: return null
        val ms = num(20, 23) ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146_097 + doe - 719_468
        return ((days * 24 + h) * 60 + mi) * 60_000 + sec * 1000 + ms
    }
}
