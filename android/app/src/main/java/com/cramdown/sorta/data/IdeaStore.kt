package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.NoteFolder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// The store's notes parts, copied from Stethoscore (reference/stethoscore-aec2af1c/android/app/src/main/
// java/com/stethoscore/app/data/StudyStore.kt): load (:79-88, with :96,108 for notes and noteFolders
// only), save through a temporary file and an atomic move with memory put back if it fails (:150-169),
// saving refused while a file it couldn't read is there (loadError, :85-86,153), replaceIdeas
// (:431-436), and the import (importJson and mergeBackup, :266-296) for the ideas alone. Every failure
// is a case with no words: step 4's screens word them.

/** Why the store didn't save. Nothing on disk changed, and memory is as it is on disk. */
enum class SaveFailure {
    /** The file is there, and couldn't be read when the store opened: it is kept, and never written over. */
    FILE_UNREAD,
    /** The write didn't land. */
    NOT_WRITTEN,
}

class SaveFailed(val failure: SaveFailure, cause: Throwable? = null) : Exception(null, cause)

/** Why an import was refused. Nothing changed. */
enum class ImportRefusal {
    /** Over 32 MB. */
    TOO_LARGE,
    /** Not a JSON object, or its notes or noteFolders there but not a list. */
    UNREADABLE,
    /** No format, or another app's. */
    NOT_AN_EXPORT,
    /** A Stethoscore Android export of a version other than the one there is. */
    OTHER_VERSION,
}

class ImportRefused(val refusal: ImportRefusal) : Exception(null, null)

/**
 * What an import added. The same on iOS (IdeaImport.Counts): the ideas and the folders added; the
 * hand-made links the added ideas hold (the entries in their links lists, a link to an idea already
 * here included; each link is kept once, on the idea it was drawn from, IdeaBook.link; `[[Title]]`s in
 * a body are not counted); and the added ideas with a board position other than (0, 0).
 */
data class ImportCounts(val ideas: Int = 0, val folders: Int = 0, val links: Int = 0, val placed: Int = 0)

/**
 * Every idea and folder, kept on this device in ideas.json in [directory] (the app's files directory).
 * An IdeaBook works on a copy and hands each change to [replaceIdeas] (its onChange); the app wires
 * one to it in step 4. Nothing opens the store at launch yet.
 */
class IdeaStore(private val directory: File) {
    private val file = File(directory, FILE_NAME)
    /** The file as last read: fields a later version writes beside the ideas are written back. */
    private var original = JSONObject()
    /** Set when the file is there but couldn't be read; saving is refused until a load reads it. */
    var loadFailed: Boolean = false
        private set
    var notes: List<Note> = emptyList()
        private set
    var folders: List<NoteFolder> = emptyList()
        private set

    /** Everything that is written to disk, as last saved. A failed save puts memory back to this. */
    private data class Saved(val original: JSONObject, val notes: List<Note>, val folders: List<NoteFolder>)
    private var committed = current()

    init { load() }

    @Synchronized fun load() {
        if (!file.exists()) return
        try {
            restore(parse(JSONObject(file.readText())))
            committed = current()
            loadFailed = false
        } catch (e: Exception) {
            // left where it is, as it is
            loadFailed = true
        }
    }

    private fun current() = Saved(original, notes, folders)
    private fun restore(s: Saved) { original = s.original; notes = s.notes; folders = s.folders }

    private fun snapshot(): JSONObject = raw(original.toString())
        .put("notes", jsonArray(notes.map(IdeaJson::encodeNote)))
        .put("noteFolders", jsonArray(folders.map(IdeaJson::encodeFolder)))

    /**
     * fsync then atomic rename: an interruption never replaces the file with a partial write. If the
     * write fails, memory goes back to the last saved state, so memory and disk agree, and [SaveFailed]
     * is thrown.
     */
    @Synchronized fun save() {
        val temporary = File(directory, "$FILE_NAME.tmp")
        try {
            if (loadFailed) throw SaveFailed(SaveFailure.FILE_UNREAD)
            if (!(directory.exists() || directory.mkdirs())) throw SaveFailed(SaveFailure.NOT_WRITTEN)
            val bytes = snapshot().toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Both files are in one directory; Android rename replaces atomically on its local filesystem.
                if (!temporary.renameTo(file)) throw SaveFailed(SaveFailure.NOT_WRITTEN)
            }
            committed = current()
        } catch (e: Exception) {
            restore(committed)
            temporary.delete()
            throw if (e is SaveFailed) e else SaveFailed(SaveFailure.NOT_WRITTEN, e)
        }
    }

    /** The idea dump after a change (IdeaBook's onChange): every note and folder, saved together. */
    @Synchronized fun replaceIdeas(notes: List<Note>, folders: List<NoteFolder>) {
        if (notes == this.notes && folders == this.folders) return
        this.notes = notes; this.folders = folders
        save()
    }

    /**
     * Stethoscore's ideas brought in once, by id: [text] is its Android library export (format
     * stethoscore-android-v1), and only its notes and noteFolders are read. The ideas and folders this
     * device doesn't have are added and saved once; one already here stays as it is, so a second import
     * adds nothing. Throws [ImportRefused] with nothing changed, or [SaveFailed] (memory put back) when
     * the save doesn't land.
     */
    @Synchronized fun importJson(text: String): ImportCounts {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) throw ImportRefused(ImportRefusal.TOO_LARGE)
        val root = try { JSONObject(text) } catch (_: JSONException) { throw ImportRefused(ImportRefusal.UNREADABLE) }
        val format = root.opt("format") as? String
        if (format != EXPORT_FORMAT) {
            throw ImportRefused(if (format?.startsWith(EXPORT_FAMILY) == true) ImportRefusal.OTHER_VERSION else ImportRefusal.NOT_AN_EXPORT)
        }
        val incoming = try { parse(root) } catch (_: JSONException) { throw ImportRefused(ImportRefusal.UNREADABLE) }
        // mergeBackup: adds what this device does not have yet; anything already here wins
        val noteIds = notes.map { it.id }.toSet()
        val added = incoming.notes.filter { it.id !in noteIds }
        val folderIds = folders.map { it.id }.toSet()
        val addedFolders = incoming.folders.filter { it.id !in folderIds }
        if (added.isEmpty() && addedFolders.isEmpty()) return ImportCounts()
        notes = notes + added
        folders = folders + addedFolders
        save()
        return ImportCounts(
            ideas = added.size,
            folders = addedFolders.size,
            links = added.sumOf { it.links.size },
            placed = added.count { it.boardX != 0.0 || it.boardY != 0.0 },
        )
    }

    /**
     * The ideas in a file of this store's or an export: notes and noteFolders, each missing read as none
     * and an entry that isn't an object skipped. Either there but not a list throws: read as none, it
     * would be written over with nothing.
     */
    private fun parse(root: JSONObject): Saved {
        for (key in listOf("notes", "noteFolders")) {
            if (root.has(key) && root.opt(key) !is JSONArray) throw JSONException(key)
        }
        return Saved(original = root, notes = root.objects("notes", IdeaJson::note), folders = root.objects("noteFolders", IdeaJson::folder))
    }

    companion object {
        const val FILE_NAME = "ideas.json"
        /** Stethoscore's Android export, the one version there is (StudyStore.kt:129). */
        const val EXPORT_FORMAT = "stethoscore-android-v1"
        private const val EXPORT_FAMILY = "stethoscore-android-"
        /** StudyStore.importJson's limit (:267). */
        const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
    }
}
