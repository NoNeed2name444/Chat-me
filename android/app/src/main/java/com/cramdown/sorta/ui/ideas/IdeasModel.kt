package com.cramdown.sorta.ui.ideas

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cramdown.sorta.data.Note
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
 * The idea dump as the screens hold it: the notes and folders (IdeaBook). Until step 3 brings
 * the store, it holds them in memory, from the ideas it is given; reading [book] subscribes to
 * its changes. (Stethoscore's saved them to its library file, StudyStore, after every change.)
 */
class IdeasModel(notes: List<Note>, folders: List<NoteFolder>) {
    private var revision by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    private var held: IdeaBook = IdeaBook(notes, folders, onChange = { _, _ -> revision++ })

    val book: IdeaBook get() { revision; return held }
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

val IdeasMode.icon: ImageVector get() = when (this) {
    IdeasMode.LIST -> Icons.AutoMirrored.Rounded.List          // list.bullet
    IdeasMode.BOARD -> Icons.Rounded.Dashboard                 // rectangle.3.group
    IdeasMode.SPACE -> Icons.Rounded.ViewInAr                  // cube.transparent
}

val NoteKind.icon: ImageVector get() = when (this) {
    NoteKind.PAGE -> Icons.Outlined.Description                // doc.text
    NoteKind.IDEA -> Icons.Outlined.Lightbulb                  // lightbulb
}

val GraphTheme.icon: ImageVector get() = when (this) {
    GraphTheme.SPACE -> Icons.Rounded.AutoAwesome              // sparkles
    GraphTheme.NEURONS -> Icons.Rounded.Public
    GraphTheme.PERFORMANCE -> Icons.Rounded.Speed              // speedometer
}

val SpaceStyle.icon: ImageVector get() = when (this) {
    SpaceStyle.BLACK_HOLE -> Icons.Rounded.Adjust              // circle.circle.fill
    SpaceStyle.SUN -> Icons.Rounded.WbSunny                    // sun.max.fill
    SpaceStyle.ROCKY -> Icons.Rounded.Public                   // globe.europe.africa.fill
    SpaceStyle.GAS_GIANT -> Icons.Rounded.Contrast             // circle.lefthalf.filled
    SpaceStyle.PULSAR -> Icons.Rounded.Sensors                 // dot.radiowaves.left.and.right
    SpaceStyle.COMET -> Icons.Rounded.AutoAwesome              // sparkle
}

/** New page's glyph (doc.badge.plus). */
val newPageIcon: ImageVector get() = Icons.AutoMirrored.Outlined.NoteAdd

/** A folder's colour, as NoteTone gives it. */
fun toneColor(folderId: String?, book: IdeaBook): Color = NoteTone.rgb(folderId, book).let { Color(it[0], it[1], it[2]) }
