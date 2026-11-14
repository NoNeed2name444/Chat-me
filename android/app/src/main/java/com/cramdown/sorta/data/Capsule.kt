package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.NaturalOrder
import com.cramdown.sorta.ideas.NoteFolder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

// The capsule (S06d A4-A5, plugins-plan idea 11): every idea and folder in one .html file that any
// browser opens with no script to run, and that Sorta opens again. The page is for reading: each
// folder by its path, each idea with its words, kind, when it was caught and what it is linked to,
// and the map's picture. Its data is a JSON block the page never runs: every note and folder by the
// store's own coding (IdeaJson, which keeps each one's raw JSON, so a field only iOS writes, such as
// a note's source, comes back as it went). iOS's Capsule writes and reads the same file.

object Capsule {
    const val FORMAT = "sorta-capsule"
    /** The one version there is. A capsule of a later one is refused, as a newer backup is. */
    const val VERSION = 1
    /** The data block's id. */
    const val BLOCK_ID = "sorta-capsule"

    /** What a capsule holds. */
    class Contents(val notes: List<Note>, val folders: List<NoteFolder>)

    /** The map's picture, a PNG drawn at twice [width] by [height] CSS pixels. */
    class Picture(val png: ByteArray, val width: Int, val height: Int)

    /** The page's words (the S06d writing pass), the same on iOS. */
    object Words {
        const val TITLE = "Sorta capsule"
        const val TO_SORT = "To sort"
        const val UNTITLED = "Untitled"
        const val IDEA = "Idea"
        const val PAGE = "Page"
        const val LINKED_TO = "Linked to"
        const val EMPTY_FOLDER = "No ideas here."
        const val PICTURE = "Every idea on the board, joined by its links."
        const val RESTORE = "To bring these ideas back, open this file in Sorta with More, then Open a capsule."
        fun saved(date: String) = "Saved $date."
        fun counts(ideas: Int, folders: Int): String {
            val i = if (ideas == 1) "1 idea" else "$ideas ideas"
            return when (folders) {
                0 -> "$i."
                1 -> "$i in 1 folder."
                else -> "$i in $folders folders."
            }
        }
        fun caught(kind: String, date: String) = "$kind, caught $date"
    }

    /** The date as the page writes it: "14 November 2026 at 9:05 PM". */
    fun date(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mm a", Locale.ENGLISH).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The file's name: "Sorta capsule 2026-11-14.html". */
    fun fileName(millis: Long, zone: ZoneId): String =
        "${Words.TITLE} ${DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(millis).atZone(zone))}.html"

    /** The capsule's data: its format and version, when it was saved, and every note and folder as the store writes them. */
    fun data(notes: List<Note>, folders: List<NoteFolder>, savedAt: Long): JSONObject = JSONObject()
        .put("format", FORMAT).put("version", VERSION).put("app", "Sorta")
        .putDate("savedAt", savedAt)
        .put("notes", jsonArray(notes.map(IdeaJson::encodeNote)))
        .put("folders", jsonArray(folders.map(IdeaJson::encodeFolder)))

    /** JSON that can sit in a script block: nothing in it can close the block or start a comment. */
    fun scriptSafe(json: String): String = buildString(json.length) {
        for (c in json) when (c) {
            '<' -> append("\\u003c")
            '>' -> append("\\u003e")
            '&' -> append("\\u0026")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(c)
        }
    }

    /** Text as HTML. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /**
     * The page: [notes] under To sort and their folders ([folders], each by its path, in Finder's
     * order), each with what [edges] (IdeaBook.allEdges: hand-made links and `[[Title]]`s) link it to;
     * [picture] the map's; dates in [zone]; then the data block.
     */
    fun html(notes: List<Note>, folders: List<NoteFolder>, edges: List<Pair<String, String>>, savedAt: Long, zone: ZoneId,
             picture: Picture?): String {
        val byId = notes.associateBy { it.id.uppercase() }
        val folderById = folders.associateBy { it.id.uppercase() }
        val linked = HashMap<String, MutableList<String>>()
        for ((a, b) in edges) {
            linked.getOrPut(a.uppercase()) { ArrayList() }.add(b.uppercase())
            linked.getOrPut(b.uppercase()) { ArrayList() }.add(a.uppercase())
        }
        fun title(n: Note) = n.title.trim().ifEmpty { Words.UNTITLED }
        val order = compareBy<Note> { it.createdAt }.thenBy { it.id.uppercase() }
        fun inFolder(id: String?): List<Note> = notes.filter { n ->
            val f = n.folderId?.uppercase()?.takeIf { it in folderById }
            f == id?.uppercase()
        }.sortedWith(order)

        val out = StringBuilder()
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        out.append("<meta name=\"generator\" content=\"Sorta\">\n")
        out.append("<title>").append(escape(Words.TITLE)).append("</title>\n<style>\n").append(STYLE).append("</style>\n</head>\n<body>\n<main>\n")
        out.append("<header>\n<h1>").append(escape(Words.TITLE)).append("</h1>\n<p class=\"saved\">")
            .append(escape(Words.saved(date(savedAt, zone)) + " " + Words.counts(notes.size, folders.size))).append("</p>\n</header>\n")
        if (picture != null) {
            out.append("<figure><img src=\"data:image/png;base64,").append(Base64.getEncoder().encodeToString(picture.png))
                .append("\" width=\"").append(picture.width).append("\" height=\"").append(picture.height)
                .append("\" alt=\"").append(escape(Words.PICTURE)).append("\"></figure>\n")
        }
        fun section(heading: String, list: List<Note>, folder: Boolean) {
            out.append("<section>\n<h2>").append(escape(heading)).append("</h2>\n")
            if (list.isEmpty() && folder) out.append("<p class=\"none\">").append(escape(Words.EMPTY_FOLDER)).append("</p>\n")
            for (n in list) {
                val kind = if (n.kind == "page") Words.PAGE else Words.IDEA
                out.append("<article>\n<h3>").append(escape(title(n))).append("</h3>\n<p class=\"meta\">")
                    .append(escape(Words.caught(kind, date(n.createdAt, zone)))).append("</p>\n")
                if (n.body.isNotBlank()) out.append("<div class=\"words\">").append(escape(n.body.trim())).append("</div>\n")
                val names = linked[n.id.uppercase()].orEmpty().distinct().mapNotNull { byId[it]?.let(::title) }
                if (names.isNotEmpty()) out.append("<p class=\"links\">").append(escape(Words.LINKED_TO + " " + names.joinToString(", "))).append("</p>\n")
                out.append("</article>\n")
            }
            out.append("</section>\n")
        }
        section(Words.TO_SORT, inFolder(null), folder = false)
        for ((folder, path) in outline(folders)) section(path, inFolder(folder.id), folder = true)
        out.append("<footer><p>").append(escape(Words.RESTORE)).append("</p></footer>\n</main>\n")
        out.append("<script type=\"application/json\" id=\"").append(BLOCK_ID).append("\">")
            .append(scriptSafe(data(notes, folders, savedAt).toString(1))).append("</script>\n</body>\n</html>\n")
        return out.toString()
    }

    /** Every folder with its path ("Garden › Paths"), parents before children, each level in Finder's order. */
    fun outline(folders: List<NoteFolder>): List<Pair<NoteFolder, String>> {
        val ids = folders.map { it.id.uppercase() }.toSet()
        val children = folders.groupBy { f -> f.parentId?.uppercase()?.takeIf { it in ids && !it.equals(f.id, true) } }
        val out = ArrayList<Pair<NoteFolder, String>>()
        val seen = HashSet<String>()
        fun walk(parent: String?, prefix: String) {
            for (f in children[parent].orEmpty().sortedWith(compareBy(NaturalOrder) { it.name })) {
                if (!seen.add(f.id.uppercase())) continue
                val path = prefix + f.name
                out.add(f to path)
                walk(f.id.uppercase(), "$path › ")
            }
        }
        walk(null, "")
        // a folder in a loop of parents is shown at the top
        for (f in folders) if (f.id.uppercase() !in seen) { seen.add(f.id.uppercase()); out.add(f to f.name) }
        return out
    }

    /**
     * The notes and folders in a capsule's [text], checked as a backup is (iOS LibraryBackup.check):
     * no data block, or another format, is [ImportRefusal.NOT_A_CAPSULE]; a later version
     * [ImportRefusal.NEWER_CAPSULE]; a block that isn't JSON, or its notes or folders there but not a
     * list, [ImportRefusal.UNREADABLE]. Throws [ImportRefused].
     */
    fun read(text: String): Contents {
        val block = BLOCK.find(text)?.groupValues?.get(1) ?: throw ImportRefused(ImportRefusal.NOT_A_CAPSULE)
        val root = try { JSONObject(block) } catch (_: JSONException) { throw ImportRefused(ImportRefusal.UNREADABLE) }
        if (root.opt("format") != FORMAT) throw ImportRefused(ImportRefusal.NOT_A_CAPSULE)
        val version = (root.opt("version") as? Number)?.toInt() ?: throw ImportRefused(ImportRefusal.NOT_A_CAPSULE)
        if (version > VERSION) throw ImportRefused(ImportRefusal.NEWER_CAPSULE)
        for (key in listOf("notes", "folders")) if (root.has(key) && root.opt(key) !is JSONArray) throw ImportRefused(ImportRefusal.UNREADABLE)
        return Contents(root.objects("notes", IdeaJson::note), root.objects("folders", IdeaJson::folder))
    }

    private val BLOCK = Regex("<script\\b[^>]*\\bid\\s*=\\s*[\"']$BLOCK_ID[\"'][^>]*>(.*?)</script\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** The page's look: Sorta's bone and aubergine by day and by night, ink words, the coral on edges only. */
    private val STYLE = """
:root { color-scheme: light dark; --page: #F3EADB; --ink: #281E2A; --soft: #5E545A; --line: #DCD3C7; --accent: #C4503A; }
@media (prefers-color-scheme: dark) { :root { --page: #281E2A; --ink: #F3EADB; --soft: #B0A7A1; --line: #392F39; --accent: #EE866D; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--page); color: var(--ink); font: 17px/1.5 Nunito, ui-rounded, "SF Pro Rounded", system-ui, sans-serif; }
main { max-width: 44rem; margin: 0 auto; padding: 24px 16px 48px; }
h1 { font-size: 1.75rem; margin: 0 0 4px; }
h2 { font-size: 1.25rem; margin: 32px 0 8px; padding-bottom: 4px; border-bottom: 2px solid var(--accent); }
h3 { font-size: 1.0625rem; margin: 0; }
.saved, .meta, .links, .none, footer { color: var(--soft); font-size: 0.9375rem; }
.saved { margin: 0; }
figure { margin: 24px 0 0; }
figure img { display: block; max-width: 100%; height: auto; border: 1px solid var(--line); border-radius: 12px; }
article { padding: 12px 0; border-bottom: 1px solid var(--line); }
article:last-child { border-bottom: 0; }
.meta, .links { margin: 2px 0 0; }
.words { white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 6px; }
footer { margin-top: 40px; }
""".trimStart()
}
