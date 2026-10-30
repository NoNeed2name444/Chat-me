package com.cramdown.sorta.ui.ideas

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.ImportCounts
import com.cramdown.sorta.data.ImportRefusal
import com.cramdown.sorta.data.ImportRefused
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.data.SaveFailed
import com.cramdown.sorta.data.SaveFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.cramdown.sorta.ideas.GraphLineStyle
import com.cramdown.sorta.ideas.GraphStyleChoice
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeasMode
import com.cramdown.sorta.ideas.NoteEditing
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.NoteKind
import com.cramdown.sorta.ideas.NoteTone
import com.cramdown.sorta.map.SpaceStyle

/**
 * The idea dump as the screens hold it: the notes and folders (IdeaBook); reading [book]
 * subscribes to its changes. Opened on the store ([open]), every change is saved through it
 * (IdeaStore.replaceIdeas), as Stethoscore's model saved to its library after every change
 * (reference/stethoscore-aec2af1c/android/app/src/main/java/com/stethoscore/app/ui/ideas/
 * IdeasModel.kt:42-58). Made from ideas alone (the maps' previews), it keeps them in memory.
 *
 * Two failures, each said once through [notice]:
 *  - the store's file was there and couldn't be read: IdeaStore keeps it and refuses every save
 *    (S04), so nothing read is ever written over. Capture still works, in memory only, and no
 *    words claim a change was kept;
 *  - a save didn't land: memory goes back to what is on disk, as Stethoscore's did.
 */
class IdeasModel private constructor(notes: List<Note>, folders: List<NoteFolder>, private val store: IdeaStore?) {
    constructor(notes: List<Note>, folders: List<NoteFolder>) : this(notes, folders, null)

    private var revision by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    /** What the screen says once, then clears. */
    var notice by mutableStateOf(if (store?.loadFailed == true) IdeasNotice.UNREADABLE else null)
    private var held: IdeaBook = make(notes, folders)

    private fun make(notes: List<Note>, folders: List<NoteFolder>) = IdeaBook(notes, folders, onChange = ::persist)

    private fun persist(notes: List<Note>, folders: List<NoteFolder>) {
        val s = store
        if (s != null) try {
            s.replaceIdeas(notes, folders)
        } catch (e: SaveFailed) {
            // FILE_UNREAD: refused on purpose, and said at launch; the ideas stay here for now
            if (e.failure == SaveFailure.NOT_WRITTEN) {
                notice = IdeasNotice.NOT_SAVED
                held = make(s.notes, s.folders)
            }
        }
        revision++
    }

    val book: IdeaBook get() { revision; return held }

    /**
     * How many changes the book has had. The book is one object that changes in place, so a
     * composable handed it is skipped when it changes (Compose's strong skipping compares it by
     * identity): those that take it take this too.
     */
    val version: Int get() = revision

    /** Whether the store's file couldn't be read, so nothing is saved for now. */
    val unreadable: Boolean get() = store?.loadFailed == true

    /**
     * S04's import (IdeaStore.importJson) on [text]: the ideas and folders this device doesn't
     * have are added and saved, and the screen shows the store's copy.
     */
    fun import(text: String): ImportOutcome {
        val s = store ?: return ImportOutcome.NotSaved
        val outcome = try {
            ImportOutcome.Added(s.importJson(text))
        } catch (e: ImportRefused) {
            ImportOutcome.Refused(e.refusal)
        } catch (e: SaveFailed) {
            ImportOutcome.NotSaved
        }
        if (outcome is ImportOutcome.Added && outcome.counts != ImportCounts()) {
            held = make(s.notes, s.folders)
            revision++
        }
        return outcome
    }

    companion object {
        /** The model on [store], opened at launch with what it read. */
        fun open(store: IdeaStore): IdeasModel = IdeasModel(store.notes, store.folders, store)
    }
}

/** What the screen says once about the store. */
enum class IdeasNotice { UNREADABLE, NOT_SAVED }

/** How an import ended. */
sealed class ImportOutcome {
    /** Added what the counts say; all zero when everything was here already. */
    data class Added(val counts: ImportCounts) : ImportOutcome()
    data class Refused(val refusal: ImportRefusal) : ImportOutcome()
    /** The import's save didn't land, or the store refuses saves: nothing changed. */
    object NotSaved : ImportOutcome()
}

/**
 * Reads what [open] gives, at most one byte past the import's limit (so a file too big is
 * still refused as too big, without reading all of it), then runs the import on it. The
 * system picker's result and the tests' launch extra both come here.
 */
suspend fun importFrom(model: IdeasModel, open: () -> InputStream?): ImportOutcome {
    val text = withContext(Dispatchers.IO) {
        runCatching { open()?.use { readCapped(it, IdeaStore.MAX_IMPORT_BYTES + 1) } }.getOrNull()
    } ?: return ImportOutcome.Refused(ImportRefusal.UNREADABLE)
    return model.import(text)
}

/** At most [limit] bytes of [input], as UTF-8. */
internal fun readCapped(input: InputStream, limit: Int): String {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = input.read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (n < 0) break
        out.write(chunk, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

/**
 * Ideas' remembered choices, kept as iOS keeps them in UserDefaults (the same keys), in this
 * device's preferences: the mode, the folded switcher, Lines, Read as Markdown, and the map's
 * theme, look, folder looks and demo.
 */
class IdeasSettings(context: Context) {
    private val prefs: SharedPreferences? = runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }.getOrNull()
    private fun str(key: String, fallback: String) = runCatching { prefs?.getString(key, fallback) }.getOrNull() ?: fallback
    private fun bool(key: String) = runCatching { prefs?.getBoolean(key, false) }.getOrNull() ?: false
    private fun put(block: SharedPreferences.Editor.() -> Unit) { runCatching { prefs?.edit()?.apply(block)?.apply() } }

    private var heldMode by mutableStateOf(IdeasMode.of(str(IdeasMode.KEY, IdeasMode.LIST.rawValue)))
    val mode: IdeasMode get() = heldMode
    fun setMode(value: IdeasMode) { heldMode = value; put { putString(IdeasMode.KEY, value.rawValue) } }

    private var heldSwitcherCollapsed by mutableStateOf(bool(SWITCHER_KEY))
    val switcherCollapsed: Boolean get() = heldSwitcherCollapsed
    fun setSwitcherCollapsed(value: Boolean) { heldSwitcherCollapsed = value; put { putBoolean(SWITCHER_KEY, value) } }

    private var heldStraightLines by mutableStateOf(bool(GraphLineStyle.KEY))
    val straightLines: Boolean get() = heldStraightLines
    fun setStraightLines(value: Boolean) { heldStraightLines = value; put { putBoolean(GraphLineStyle.KEY, value) } }
    val lineStyle: GraphLineStyle get() = GraphLineStyle.inForce(straightLines)

    private var heldReading by mutableStateOf(bool(NoteEditing.READ_KEY))
    val reading: Boolean get() = heldReading
    fun setReading(value: Boolean) { heldReading = value; put { putBoolean(NoteEditing.READ_KEY, value) } }

    private var heldTheme by mutableStateOf(str(GraphTheme.KEY, GraphTheme.standard.rawValue))
    val theme: String get() = heldTheme
    fun setTheme(value: String) { heldTheme = value; put { putString(GraphTheme.KEY, value) } }

    private var heldNodeStyle by mutableStateOf(str(GraphStyleChoice.KEY, GraphStyleChoice.STANDARD))
    val nodeStyle: String get() = heldNodeStyle
    fun setNodeStyle(value: String) { heldNodeStyle = value; put { putString(GraphStyleChoice.KEY, value) } }

    private var heldFolderStyles by mutableStateOf(str(GraphStyleChoice.FOLDERS_KEY, ""))
    val folderStyles: String get() = heldFolderStyles
    fun setFolderStyles(value: String) { heldFolderStyles = value; put { putString(GraphStyleChoice.FOLDERS_KEY, value) } }

    private var heldPerfDemo by mutableIntStateOf(runCatching { prefs?.getInt(PERF_DEMO_KEY, 0) }.getOrNull() ?: 0)
    val perfDemo: Int get() = heldPerfDemo
    fun setPerfDemo(value: Int) { heldPerfDemo = value; put { putInt(PERF_DEMO_KEY, value) } }

    /**
     * The map's speed readout (fps, node counts): Stethoscore's debug builds showed it. Sorta
     * has no developer build, as iOS's PersonalBuild is off, so it never shows.
     */
    val readout: Boolean get() = readoutShown(DEVELOPER, runCatching { prefs?.getBoolean(READOUT_KEY, true) }.getOrNull() ?: true)

    companion object {
        const val FILE = "ideas"
        /** Whether this is a developer's build: never, in Sorta. */
        const val DEVELOPER = false
        const val READOUT_KEY = "vignette.space.readout"
        const val SWITCHER_KEY = "vignette.ideas.switcherCollapsed"
        const val PERF_DEMO_KEY = "vignette.space.perfDemo"
        /**
         * The demo maps the readout offers (iOS GraphPerfDemo). Stethoscore's Android offered a
         * million too; Sorta offers it on neither platform until iOS's million map is traced
         * (docs/scoping.md, section 1).
         */
        val perfDemoSizes = listOf(100_000, 300_000)

        /** The readout shows only in a debug build, and there only while it is not turned off. */
        fun readoutShown(debug: Boolean, wanted: Boolean): Boolean = debug && wanted
    }
}

/** The modes' glyphs, Outlined as every control's is (the kit's own menu, WardSwitcher). */
val IdeasMode.icon: ImageVector get() = when (this) {
    IdeasMode.LIST -> Icons.AutoMirrored.Outlined.List         // list.bullet
    IdeasMode.BOARD -> Icons.Outlined.Dashboard                // rectangle.3.group
    IdeasMode.SPACE -> Icons.Outlined.ViewInAr                 // cube.transparent
}

val NoteKind.icon: ImageVector get() = when (this) {
    NoteKind.PAGE -> Icons.Outlined.Description                // doc.text
    NoteKind.IDEA -> Icons.Outlined.Lightbulb                  // lightbulb
}

val GraphTheme.icon: ImageVector get() = when (this) {
    GraphTheme.SPACE -> Icons.Rounded.AutoAwesome              // sparkles
    GraphTheme.NEURONS -> Icons.Rounded.Psychology            // brain.head.profile
    GraphTheme.PERFORMANCE -> Icons.Rounded.Speed              // speedometer
}

val SpaceStyle.icon: ImageVector get() = when (this) {
    SpaceStyle.BLACK_HOLE -> Icons.Rounded.Adjust              // circle.circle.fill
    SpaceStyle.SUN -> Icons.Rounded.WbSunny                    // sun.max.fill
    SpaceStyle.ROCKY -> Icons.Rounded.Public                   // globe.europe.africa.fill
    SpaceStyle.GAS_GIANT -> Icons.Rounded.Contrast             // circle.lefthalf.filled
    SpaceStyle.PULSAR -> Icons.Rounded.Sensors                 // dot.radiowaves.left.and.right
    SpaceStyle.COMET -> Icons.Rounded.Flare                    // sparkle; AutoAwesome (sparkles) is the Universe's
}

/** New page's glyph (doc.badge.plus). */
val newPageIcon: ImageVector get() = Icons.AutoMirrored.Outlined.NoteAdd

/** A folder's colour, as NoteTone gives it. */
fun toneColor(folderId: String?, book: IdeaBook): Color = NoteTone.rgb(folderId, book).let { Color(it[0], it[1], it[2]) }
