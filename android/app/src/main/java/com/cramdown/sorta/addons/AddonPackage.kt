package com.cramdown.sorta.addons

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Add-ons (S07b): the package checker, and the review before install.
//
// The rules of addons/spec/rules.md, in its order, with the codes of addons/spec/reasons.json: the same
// code for the same package as the kit's check.py and the Swift checker
// (ios/Sorta/Addons/AddonPackage.swift, which this follows). Each check runs over every entry or field
// before the next starts, and the first failure decides the code. The archive is checked from its central
// directory before anything is unpacked.

class AddonRefusal(val code: String) : Exception(code)

/** An add-on's files, checked: the parsed manifest and the files by path. */
class AddonPackage(val manifest: AddonJson, val files: AddonFiles, val unpacked: Int) {
    val id: String get() = manifest["id"]?.string ?: ""
    val version: String get() = manifest["version"]?.string ?: ""
    val permissions: List<String> get() = manifest["permissions"]?.strings ?: emptyList()
    val hosts: List<String> get() = manifest["hosts"]?.strings ?: emptyList()
}

object AddonPackageCheck {
    const val MAX_ZIPPED = 524_288
    const val MAX_UNPACKED = 1_048_576
    const val MAX_ENTRIES = 200
    const val MAX_MAIN = 262_144
    const val MAX_RATIO = 100
    const val MAX_NAME_BYTES = 255
    const val MAX_FOLDERS = 2
    const val MAX_JSON_DEPTH = 32
    const val MAX_JSON_NUMBER = 32
    /** The app's own API major version (install.api). */
    const val APP_API = 1

    val rootFiles = setOf("manifest.json", "README.md", "LICENSE", "LICENSE.txt", "LICENSE.md", "CHANGELOG.md")
    val fields = setOf("format", "formatVersion", "id", "name", "version", "author", "license", "minSorta", "api", "main",
        "permissions", "hosts", "contributes")
    val manifestPermissions = setOf("ideas.read", "ideas.read.folder", "ideas.write", "commands", "cards", "board.palette", "net", "ai")
    val contributes = setOf("commands", "cardView", "settings", "boardPalette")
    val needs = listOf("commands" to "commands", "cardView" to "cards", "boardPalette" to "board.palette")

    private fun refuse(code: String): Nothing = throw AddonRefusal(code)

    /** A `.sortaaddon` file's bytes, checked and unpacked; an AddonRefusal says why not. */
    fun archive(data: ByteArray): AddonPackage {
        val (names, contents) = AddonZip.read(data)
        return pack(names, contents)
    }

    /** The code a file's bytes are refused with, or null. */
    fun verdict(data: ByteArray): String? = try { archive(data); null } catch (r: AddonRefusal) { r.code }

    // the files (rules.md, section 2)

    fun pack(names: List<String>, data: List<ByteArray>): AddonPackage {
        for (name in names) if (!allowedFile(name)) refuse("file.type")
        for (name in names) if (name.count { it == '/' } > MAX_FOLDERS) refuse("file.depth")
        val texts = data.map { bytes -> (if (bytes.contains(0)) null else strictUtf8(bytes)) ?: refuse("file.text") }
        for (bytes in data) if (hasPicture(bytes)) refuse("file.picture")
        val mi = names.indexOf("manifest.json")
        if (mi < 0) refuse("manifest.missing")
        val manifest = strictJson(texts[mi])
        if (manifest == null || !manifest.isObject) refuse("manifest.json")
        val files = HashMap<String, Int>()
        names.forEachIndexed { i, n -> files[n] = i }
        manifestRules(manifest, files, data, texts)
        return AddonPackage(manifest, AddonFiles(names, data, manifest["main"]?.string ?: ""), data.sumOf { it.size })
    }

    fun allowedFile(name: String): Boolean {
        if (name.endsWith("/")) return false
        val parts = name.split("/")
        if (parts.any { it.startsWith(".") }) return false
        if (parts.size == 1 && name in rootFiles) return true
        return name.endsWith(".js") || name.endsWith(".json")
    }

    /** Strict UTF-8: overlong forms, surrogates and code points past U+10FFFF fail. */
    fun strictUtf8(d: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(d)).toString()
    } catch (e: CharacterCodingException) { null }

    /** `<svg` or `data:image/`, compared after ASCII lowercasing. */
    fun hasPicture(d: ByteArray): Boolean {
        for (needle in listOf("<svg".toByteArray(), "data:image/".toByteArray())) {
            var i = 0
            while (i + needle.size <= d.size) {
                var k = 0
                while (k < needle.size) {
                    val c = d[i + k].toInt()
                    if ((if (c in 65..90) c + 32 else c) != needle[k].toInt()) break
                    k++
                }
                if (k == needle.size) return true
                i++
            }
        }
        return false
    }

    /** RFC 8259 JSON with no byte order mark, no key twice in an object, at most 32 levels of lists and objects, and no number over 32 characters. */
    fun strictJson(text: String): AddonJson? {
        var depth = 0
        var run = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
                continue
            }
            val numeric = c in '0'..'9' || c == '+' || c == '-' || c == '.' || c == 'E' || c == 'e'
            run = if (numeric) run + 1 else 0
            if (run > MAX_JSON_NUMBER) return null
            if (c == '"') inString = true else if (c == '[' || c == '{') {
                depth++
                if (depth > MAX_JSON_DEPTH) return null
            } else if (c == ']' || c == '}') depth--
        }
        return try { AddonJson.parse(text, keyTwice = false) } catch (e: AddonJsonError) { null }
    }

    private fun keys(v: AddonJson?): Set<String>? = (v as? AddonJson.Obj)?.pairs?.map { it.first }?.toSet()

    /** A finite JSON number with no fraction, from low to high. */
    fun whole(v: AddonJson?, low: Double, high: Double): Boolean {
        val n = v?.number ?: return false
        return !n.isNaN() && !n.isInfinite() && n == Math.rint(n) && n >= low && n <= high
    }

    private fun points(s: String): List<Int> = s.codePoints().toArray().toList()

    /**
     * A string a person reads: 1 to [longest] code points, no space at either end, no control character, and
     * none of Unicode's format characters or line and paragraph separators (Cf, Zl, Zp).
     */
    fun label(v: AddonJson?, longest: Int): Boolean {
        val s = v?.string ?: return false
        val cps = points(s)
        if (cps.size !in 1..longest || cps.first() == 0x20 || cps.last() == 0x20) return false
        return cps.none { c ->
            c < 0x20 || c in 0x7F..0x9F || when (Character.getType(c).toByte()) {
                Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
                else -> false
            }
        }
    }

    private fun matches(s: String, first: (Char) -> Boolean, rest: (Char) -> Boolean, length: IntRange): Boolean =
        s.length in length && first(s[0]) && s.drop(1).all(rest)

    private fun lower(c: Char) = c in 'a'..'z'
    private fun digit(c: Char) = c in '0'..'9'
    private fun upper(c: Char) = c in 'A'..'Z'

    /** [a-z0-9][a-z0-9-]{0,31} */
    fun commandId(v: AddonJson?): Boolean {
        val s = v?.string ?: return false
        return matches(s, { lower(it) || digit(it) }, { lower(it) || digit(it) || it == '-' }, 1..32)
    }

    /** [a-z][A-Za-z0-9]{0,31} */
    fun settingKey(v: AddonJson?): Boolean {
        val s = v?.string ?: return false
        return matches(s, ::lower, { lower(it) || upper(it) || digit(it) }, 1..32)
    }

    /** #[0-9A-Fa-f]{6} */
    fun colour(v: AddonJson?): Boolean {
        val s = v?.string ?: return false
        return matches(s, { it == '#' }, { digit(it) || it in 'a'..'f' || it in 'A'..'F' }, 7..7)
    }

    // the manifest (rules.md, section 3)

    fun manifestRules(m: AddonJson, files: Map<String, Int>, data: List<ByteArray>, texts: List<String>) {
        if (m["format"]?.string != "sorta-addon") refuse("format")
        if (!whole(m["formatVersion"], 1.0, 1.0)) refuse("formatVersion")
        if (!m.keys.all { it in fields }) refuse("manifest.json")
        val id = m["id"]?.string
        if (id == null || !validId(id)) refuse("id")
        if (id == "com.cramdown" || id.startsWith("com.cramdown.")) refuse("id.reserved")
        if (!label(m["name"], 30)) refuse("name")
        if (m["version"]?.string?.let { semver(it) } == null) refuse("version")
        if (m["minSorta"]?.string?.let { semver(it) } == null) refuse("minSorta")
        if (!whole(m["api"], 1.0, 99.0)) refuse("api")
        authorRule(m["author"])
        val lic = m["license"]?.string
        if (lic == null || lic !in AddonLicenses.osi) refuse("license")
        val main = m["main"]?.string
        val mainIndex = main?.let { if (it.endsWith(".js")) files[it] else null } ?: refuse("main.missing")
        if (data[mainIndex].size > MAX_MAIN) refuse("main.size")
        val list = m["permissions"]?.array
        if (list == null || !list.all { it.string?.let { p -> p in manifestPermissions } ?: false }) refuse("permissions")
        val perms = list.mapNotNull { it.string }
        if (perms.toSet().size != perms.size) refuse("permissions")
        if ("ideas.read" in perms && "ideas.read.folder" in perms) refuse("permissions")
        hostsRule(m["hosts"] ?: AddonJson.Arr(emptyList()), "net" in perms)
        val c = m["contributes"] ?: AddonJson.Obj(emptyList())
        if (c.isObject) c["commands"]?.let { commandsRule(it) }
        contributesRule(c, perms.toSet(), files, texts)
    }

    /** [a-z0-9-]+(\.[a-z0-9-]+)+, 3 to 64 characters. */
    fun validId(id: String): Boolean {
        if (id.length !in 3..64 || !id.all { lower(it) || digit(it) || it == '.' || it == '-' }) return false
        val parts = id.split(".")
        return parts.size >= 2 && parts.all { it.isNotEmpty() }
    }

    /** Unicode's White_Space, and the controls. */
    private fun blank(c: Int) = c < 0x20 || c == 0x20 || c == 0x85 || c == 0xA0 || c == 0x1680 || c in 0x2000..0x200A ||
        c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000

    fun authorRule(author: AddonJson?) {
        val name = author?.get("name")?.string
        val url = author?.get("url")?.string
        if (keys(author) != setOf("name", "url") || !label(author?.get("name"), 60) || name == null || name.contains('@') ||
            url == null || !url.startsWith("https://")) refuse("author")
        val cps = points(url)
        if (cps.size > 200) refuse("author")
        val host = cps.drop(8).takeWhile { it != '/'.code && it != '?'.code && it != '#'.code }
        if (host.isEmpty() || host.contains('@'.code) || cps.any(::blank)) refuse("author")
    }

    fun hostsRule(hosts: AddonJson, wantsNet: Boolean) {
        val list = hosts.array ?: refuse("hosts")
        if (wantsNet && list.isEmpty()) refuse("hosts")
        if (list.isNotEmpty() && !wantsNet) refuse("hosts")
        for (h in list) {
            val host = h.string
            if (host == null || !validHost(host)) refuse("hosts")
        }
        if (list.mapNotNull { it.string }.toSet().size != list.size) refuse("hosts")
    }

    /** 2 or more labels of [a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?, at most 253 characters, the last label not all digits. */
    fun validHost(h: String): Boolean {
        val parts = h.split(".")
        if (parts.size < 2 || h.codePointCount(0, h.length) > 253) return false
        for (p in parts) {
            if (p.length !in 1..63 || !p.all { lower(it) || digit(it) || it == '-' } || p.first() == '-' || p.last() == '-') return false
        }
        return !parts.last().all(::digit)
    }

    fun commandsRule(commands: AddonJson) {
        val list = commands.array
        if (list == null || list.size !in 1..5) refuse("commands")
        val ids = HashSet<String>()
        for (c in list) {
            if (keys(c) != setOf("id", "title") || !commandId(c["id"]) || !label(c["title"], 30) || !ids.add(c["id"]?.string ?: "")) refuse("commands")
        }
    }

    fun contributesRule(c: AddonJson, perms: Set<String>, files: Map<String, Int>, texts: List<String>) {
        val have = keys(c)
        if (have == null || !have.all { it in contributes }) refuse("contributes")
        for ((field, permission) in needs) if ((field in have) != (permission in perms)) refuse("contributes")
        if ("cardView" in have && "ideas.read" !in perms && "ideas.read.folder" !in perms) refuse("cards.read")
        c["cardView"]?.let { view ->
            if (keys(view) != setOf("id", "title") || !commandId(view["id"]) || !label(view["title"], 30)) refuse("contributes")
        }
        fun dataFile(v: AddonJson?): AddonJson {
            val name = v?.string
            val i = name?.let { if (it.endsWith(".json")) files[it] else null } ?: refuse("contributes")
            return strictJson(texts[i]) ?: refuse("contributes")
        }
        if ("settings" in have) settingsRule(dataFile(c["settings"]))
        if ("boardPalette" in have) paletteRule(dataFile(c["boardPalette"]))
    }

    fun settingsRule(schema: AddonJson) {
        val bad = "contributes"
        val items = schema["settings"]?.array
        if (keys(schema) != setOf("settings") || items == null || items.size !in 1..20) refuse(bad)
        val seen = HashSet<String>()
        val common = setOf("key", "type", "title", "default")
        for (item in items) {
            val have = keys(item)
            val key = item["key"]?.string
            if (have == null || !settingKey(item["key"]) || key == null) refuse(bad)
            val kind = item["type"]?.string
            val extra = when (kind) {
                "toggle" -> null
                "text" -> "maxLength"
                "choice" -> "choices"
                else -> refuse(bad)
            }
            if (!have.containsAll(common) || !(have - common).all { it == extra }) refuse(bad)
            if (!label(item["title"], 40) || !seen.add(key)) refuse(bad)
            val def = item["default"]
            if (kind == "toggle" && def?.bool == null) refuse(bad)
            if (kind == "text") {
                val longest = item["maxLength"] ?: AddonJson.Num(200.0)
                val text = def?.string
                if (!whole(longest, 1.0, 200.0) || text == null || text.codePointCount(0, text.length) > (longest.number ?: 0.0)) refuse(bad)
            }
            if (kind == "choice") {
                val choices = item["choices"]?.array
                if (choices == null || choices.size !in 2..10) refuse(bad)
                for (ch in choices) {
                    if (keys(ch) != setOf("value", "title") || !settingKey(ch["value"]) || !label(ch["title"], 40)) refuse(bad)
                }
                val values = choices.mapNotNull { it["value"]?.string }
                val d = def?.string
                if (values.toSet().size != choices.size || d == null || d !in values) refuse(bad)
            }
        }
    }

    fun paletteRule(palette: AddonJson) {
        val bad = "contributes"
        if (keys(palette) != setOf("day", "night")) refuse(bad)
        for (time in listOf("day", "night")) {
            val tones = palette[time]
            val folders = tones?.get("folders")?.array
            if (tones == null || keys(tones) != setOf("page", "card", "cardText", "link", "folders") ||
                !listOf("page", "card", "cardText", "link").all { colour(tones[it]) } ||
                folders == null || folders.size > 6 || !folders.all { colour(it) }) refuse(bad)
            for ((front, back, least) in listOf(Triple("cardText", "card", 4.5), Triple("card", "page", 3.0), Triple("link", "page", 3.0))) {
                if (contrast(tones[front]?.string ?: "", tones[back]?.string ?: "") < least) refuse(bad)
            }
        }
    }

    /** WCAG 2's contrast ratio of two #RRGGBB colours. */
    fun contrast(one: String, two: String): Double {
        fun luminance(colour: String): Double {
            val ch = listOf(1, 3, 5).map { at ->
                val c = (colour.substring(at, at + 2).toIntOrNull(16) ?: 0) / 255.0
                if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2]
        }
        val a = luminance(one)
        val b = luminance(two)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    // semantic versions

    class Semver(val numbers: List<Int>, val pre: List<String>?)

    /** N.N.N or N.N.N-P(.P)*; no build metadata. */
    fun semver(s: String): Semver? {
        var core = s
        var pre: List<String>? = null
        val dash = s.indexOf('-')
        if (dash >= 0) {
            core = s.substring(0, dash)
            val ids = s.substring(dash + 1).split(".")
            for (id in ids) {
                if (id.isEmpty() || !id.all { digit(it) || lower(it) || upper(it) || it == '-' }) return null
                if (id.all(::digit) && !(id.length <= 9 && (id.length == 1 || id[0] != '0'))) return null
            }
            pre = ids
        }
        val nums = core.split(".")
        if (nums.size != 3) return null
        val out = ArrayList<Int>()
        for (n in nums) {
            if (n.length !in 1..9 || !n.all(::digit) || (n.length > 1 && n[0] == '0')) return null
            out.add(n.toInt())
        }
        return Semver(out, pre)
    }

    /** Precedence as semver.org has it: -1, 0 or 1. */
    fun compare(a: String, b: String): Int {
        val x = semver(a)
        val y = semver(b)
        if (x == null || y == null) return if (a == b) 0 else if (a < b) -1 else 1
        for (k in 0 until 3) if (x.numbers[k] != y.numbers[k]) return if (x.numbers[k] < y.numbers[k]) -1 else 1
        val ps = x.pre
        val qs = y.pre
        if (ps == null && qs == null) return 0
        if (ps == null) return 1
        if (qs == null) return -1
        for (k in 0 until min(ps.size, qs.size)) {
            val u = ps[k]
            val v = qs[k]
            if (u == v) continue
            val un = u.all(::digit)
            val vn = v.all(::digit)
            if (un && vn) return if (u.toInt() < v.toInt()) -1 else 1
            if (un != vn) return if (un) -1 else 1
            return if (u < v) -1 else 1
        }
        return if (ps.size == qs.size) 0 else if (ps.size < qs.size) -1 else 1
    }

    /** The rules only the app can apply: its API and its version. */
    fun install(p: AddonPackage, appVersion: String, appApi: Int = APP_API): String? {
        if ((p.manifest["api"]?.number ?: 0.0) > appApi) return "install.api"
        if (compare(p.manifest["minSorta"]?.string ?: "", appVersion) > 0) return "install.minSorta"
        return null
    }
}

/** What the person sees before saying yes: nothing has run. [size] is the file's size in bytes, [sha256] its SHA-256 in lowercase hex. */
class AddonReview(val pack: AddonPackage, val size: Int, val sha256: String) {
    val manifest: AddonJson get() = pack.manifest
    val permissions: List<String> get() = pack.permissions
    val hosts: List<String> get() = pack.hosts

    /** The grants a person may choose from this review: each permission, net as one grant per declared host, and a folder grant per folder. */
    fun allows(grant: String): Boolean = when {
        grant.startsWith("net:") -> "net" in permissions && grant.substring(4) in hosts
        grant.startsWith(AddonPermissions.FOLDER_GRANT) -> "ideas.read.folder" in permissions
        else -> grant != "net" && grant != "ideas.read.folder" && grant in permissions
    }

    companion object {
        /** From a file's or a link's bytes: the archive checked, unpacked and checked, then the app's own install rules. An AddonRefusal says why not. */
        fun make(data: ByteArray, appVersion: String, appApi: Int = AddonPackageCheck.APP_API): AddonReview {
            val p = AddonPackageCheck.archive(data)
            AddonPackageCheck.install(p, appVersion, appApi)?.let { throw AddonRefusal(it) }
            return AddonReview(p, data.size, AddonSha256.hex(data))
        }
    }
}

/** The zip reader (rules.md, section 1). */
object AddonZip {
    private class Entry(val raw: ByteArray, val name: String, val flags: Int, val method: Int, val crc: Long, val packed: Long,
        val size: Long, val external: Long, val localAt: Long)

    private fun bad(): Nothing = throw AddonRefusal("zip.unreadable")

    private fun u16(d: ByteArray, i: Long): Int {
        if (i < 0 || i + 2 > d.size) bad()
        return (d[i.toInt()].toInt() and 0xFF) or ((d[i.toInt() + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(d: ByteArray, i: Long): Long = u16(d, i).toLong() or (u16(d, i + 2).toLong() shl 16)

    private fun latin(raw: ByteArray, fold: Boolean = false): String =
        String(CharArray(raw.size) { k -> val c = raw[k].toInt() and 0xFF; (if (fold && c in 65..90) c + 32 else c).toChar() })

    /** The names and contents, in the directory's order. */
    fun read(d: ByteArray): Pair<List<String>, List<ByteArray>> {
        if (d.size > AddonPackageCheck.MAX_ZIPPED) throw AddonRefusal("zip.size")
        // 2: the end record, searched backwards from the last possible place
        if (d.size < 22) bad()
        var endAt = -1L
        var at = d.size - 22
        val lowest = max(0, d.size - 22 - 0xFFFF)
        while (at >= lowest) {
            if (d[at].toInt() == 0x50 && d[at + 1].toInt() == 0x4B && d[at + 2].toInt() == 5 && d[at + 3].toInt() == 6 &&
                at + 22 + u16(d, at + 20L) == d.size) { endAt = at.toLong(); break }
            at--
        }
        if (endAt < 0) bad()
        val disk = u16(d, endAt + 4)
        val dirDisk = u16(d, endAt + 6)
        val here = u16(d, endAt + 8)
        val count = u16(d, endAt + 10)
        val dirSize = u32(d, endAt + 12)
        val dirAt = u32(d, endAt + 16)
        if (disk != 0 || dirDisk != 0 || here != count) bad()
        if (count == 0xFFFF || dirSize == 0xFFFF_FFFFL || dirAt == 0xFFFF_FFFFL) bad()
        if (endAt >= 20 && u32(d, endAt - 20) == 0x0706_4B50L) bad()
        if (u16(d, endAt + 20) != 0) bad()
        if (dirAt + dirSize != endAt) bad()
        // 3
        if (count > AddonPackageCheck.MAX_ENTRIES) throw AddonRefusal("zip.entries")
        // 4: the directory, filled exactly by its entries; no entry comment
        val entries = ArrayList<Entry>()
        var p = dirAt
        val stop = dirAt + dirSize
        repeat(count) {
            if (p + 46 > stop || u32(d, p) != 0x0201_4B50L) bad()
            val nameLen = u16(d, p + 28)
            val extraLen = u16(d, p + 30)
            val commentLen = u16(d, p + 32)
            val nameAt = p + 46
            val next = nameAt + nameLen + extraLen + commentLen
            if (next > stop) bad()
            if (commentLen != 0) bad()
            val raw = d.copyOfRange(nameAt.toInt(), (nameAt + nameLen).toInt())
            entries.add(Entry(raw, latin(raw), u16(d, p + 8), u16(d, p + 10), u32(d, p + 16), u32(d, p + 20), u32(d, p + 24),
                u32(d, p + 38), u32(d, p + 42)))
            p = next
        }
        if (p != stop) bad()
        // 5 to 12, each over every entry before the next
        for (e in entries) if (e.flags and 0x2041 != 0 || e.method == 99) throw AddonRefusal("zip.encrypted")
        for (e in entries) if (e.method != 0 && e.method != 8) bad()
        for (e in entries) pathRule(e.raw)
        for (e in entries) if ((e.external shr 16) and 0xF000 == 0xA000L) throw AddonRefusal("zip.link")
        val seen = HashSet<String>()
        for (e in entries) if (!seen.add(e.name)) throw AddonRefusal("zip.duplicate")
        val folded = HashSet<String>()
        for (e in entries) if (!folded.add(latin(e.raw, fold = true))) throw AddonRefusal("zip.case")
        for (e in entries) if (e.size > AddonPackageCheck.MAX_RATIO * e.packed) throw AddonRefusal("zip.ratio")
        val total = entries.sumOf { it.size }
        if (total > AddonPackageCheck.MAX_RATIO.toLong() * d.size) throw AddonRefusal("zip.ratio")
        if (total > AddonPackageCheck.MAX_UNPACKED) throw AddonRefusal("zip.size")
        // 13: every byte before the directory belongs to exactly one entry
        val spans = ArrayList<Pair<Long, Long>>()
        for (e in entries) {
            if (e.localAt + 30 > dirAt || u32(d, e.localAt) != 0x0403_4B50L) bad()
            spans.add(e.localAt to e.localAt + 30 + u16(d, e.localAt + 26) + u16(d, e.localAt + 28) + e.packed)
        }
        spans.sortWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
        for (i in 1 until spans.size) if (spans[i].first < spans[i - 1].second) bad()
        var reach = 0L
        for ((start, end) in spans + (dirAt to dirAt)) {
            if (start > reach) bad()
            reach = max(reach, end)
        }
        // 14: unpacking, in the directory's order
        val names = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        for (e in entries) {
            val nameLen = u16(d, e.localAt + 26)
            val extraLen = u16(d, e.localAt + 28)
            val start = e.localAt + 30 + nameLen + extraLen
            val end = start + e.packed
            if (e.localAt + 30 + nameLen > d.size || !d.copyOfRange((e.localAt + 30).toInt(), (e.localAt + 30 + nameLen).toInt()).contentEquals(e.raw) ||
                u16(d, e.localAt + 8) != e.method || end > dirAt) bad()
            val packed = d.copyOfRange(start.toInt(), end.toInt())
            val content = if (e.method == 0) {
                if (e.packed != e.size) bad()
                packed
            } else inflate(packed, e.size.toInt())
            if (CRC32().also { it.update(content) }.value != e.crc) bad()
            names.add(e.name)
            contents.add(content)
        }
        return names to contents
    }

    /** Raw deflate (RFC 1951), bounded by the entry's stated size, which it must give exactly. */
    private fun inflate(packed: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(packed)
            val out = ByteArray(size + 1)
            var n = 0
            while (!inflater.finished()) {
                val got = inflater.inflate(out, n, out.size - n)
                n += got
                if (n > size) bad()
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) bad()
            }
            if (n != size) bad()
            return out.copyOf(size)
        } catch (e: DataFormatException) {
            bad()
        } finally {
            inflater.end()
        }
    }

    /** Step 7: the entry name's rules. */
    fun pathRule(raw: ByteArray) {
        val refused = AddonRefusal("zip.path")
        if (raw.isEmpty() || raw.size > AddonPackageCheck.MAX_NAME_BYTES) throw refused
        val s = latin(raw)
        if (s.contains('\\') || s[0] == '/') throw refused
        if (s.length >= 2 && s[1] == ':' && (s[0] in 'A'..'Z' || s[0] in 'a'..'z')) throw refused
        val body = if (s.endsWith("/")) s.dropLast(1) else s
        for (part in body.split("/")) if (part.isEmpty() || part == "." || part == "..") throw refused
        if (!s.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' || it == '/' }) throw refused
    }
}

/** SHA-256, for the index's checksums and the review. */
object AddonSha256 {
    fun hex(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
