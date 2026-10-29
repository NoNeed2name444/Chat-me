package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.NoteFolder
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

// Copied from Stethoscore (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/app/
// data/StudyJson.kt:9-10,14-18, and from StudyJson :22-27,57-58; the folder lines from
// data/StudyStore.kt:108,133): the JSON the library file and its export write, read and written
// back the same way. Each note and folder keeps its rawJson, so fields Sorta doesn't know are
// written back as they came.

internal fun JSONObject.text(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key, fallback)
internal fun JSONObject.nullText(key: String): String? = if (isNull(key)) null else optString(key)
internal fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } } ?: emptyList()
internal fun <T> JSONObject.objects(key: String, decode: (JSONObject) -> T): List<T> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(decode) } } ?: emptyList()
internal fun raw(text: String): JSONObject = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
internal fun jsonArray(items: List<Any?>): JSONArray = JSONArray().also { a -> items.forEach { a.put(it) } }
internal fun JSONObject.putDate(key: String, value: Long?): JSONObject = put(key, value?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)

/** iOS normally exports ISO-8601; older JSONEncoder files use seconds from 2001-01-01. */
object IdeaJson {
    private const val SWIFT_EPOCH_MS = 978307200000L
    fun date(value: Any?, fallback: Long = System.currentTimeMillis()): Long = when (value) {
        is Number -> if (value.toDouble().isFinite()) (value.toDouble() * 1000).toLong() + SWIFT_EPOCH_MS else fallback
        is String -> runCatching { Instant.parse(value).toEpochMilli() }.recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(fallback)
        else -> fallback
    }
    internal fun note(o: JSONObject): Note { val created = date(o.opt("createdAt")); return Note(o.text("id", newId()), o.text("title"), o.text("body"), o.nullText("folderId"), o.text("kind", "idea"), o.strings("links"), o.strings("tags"), o.optDouble("boardX", 0.0), o.optDouble("boardY", 0.0), created, date(o.opt("updatedAt"), created), o.toString()) }
    internal fun encodeNote(n: Note): JSONObject = raw(n.rawJson).put("id", n.id).put("title", n.title).put("body", n.body).put("folderId", n.folderId ?: JSONObject.NULL).put("kind", n.kind).put("links", jsonArray(n.links)).put("tags", jsonArray(n.tags)).put("boardX", n.boardX).put("boardY", n.boardY).putDate("createdAt", n.createdAt).putDate("updatedAt", n.updatedAt)
    // "Folder": the name a folder without one is read as (iOS NoteStore.swift:90); step 4's strings pass words it
    internal fun folder(o: JSONObject): NoteFolder = NoteFolder(o.text("id", newId()), o.text("name", "Folder"), o.nullText("parentId"), date(o.opt("createdAt")), o.toString())
    internal fun encodeFolder(f: NoteFolder): JSONObject = raw(f.rawJson).put("id", f.id).put("name", f.name).put("parentId", f.parentId ?: JSONObject.NULL).putDate("createdAt", f.createdAt)
}
