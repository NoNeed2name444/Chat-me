package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.SpaceStyle
import kotlin.math.abs

// The map's themes and the Space's style choice: ports of GraphTheme.swift and
// GraphStyleChoice (GraphNodeStyles.swift), fed0105, with the style names of
// GraphNodeStyle (GraphSpaceOptics.swift). Pure Kotlin, tested in IdeaLooksTest.

/** The 3D Ideas map's themes (GraphTheme). */
enum class GraphTheme(val rawValue: String, val title: String) {
    SPACE("space", "Space"),
    NEURONS("neurons", "Neuron"),
    PERFORMANCE("performance", "Performance");

    /**
     * Whether it can be chosen: every theme, now Neurons is ported too (map/Neurons.kt and its
     * look, membrane and shaders, drawn by NeuronGl), so the Look menu offers all three.
     */
    val isReady: Boolean get() = true

    val legendTitle: String get() = when (this) {
        SPACE -> "How your universe is built"
        NEURONS -> "How your network is built"
        // S05j 8c: the old "How the Performance map is built" was cut beside Done on a 360 dp phone even
        // with the bar's leading room; it now names the look as the Look menu does, on both platforms
        PERFORMANCE -> "How Performance is built"
    }

    /** What TalkBack calls the map. */
    val mapLabel: String get() = when (this) {
        SPACE -> "Space of ideas"
        NEURONS -> "Network of ideas"
        PERFORMANCE -> "Performance map of ideas"
    }

    /**
     * How to touch Android's map, which TalkBack reads after [mapLabel] too: the legend's touch
     * help, word for word (MapLegend.touchHelp, S05f), so the two never disagree.
     */
    val hint: String get() = MapLegend.touchHelp(this)

    val cardTitle: String get() = when (this) {
        SPACE -> "Your ideas as a universe"
        NEURONS -> "Your ideas as a nervous system"
        PERFORMANCE -> "Your ideas, built for speed"
    }

    /** What the map is made of: Space's default look is iOS's Universe (S05h; GraphTheme.swift:118-122). */
    val cardText: String get() = when (this) {
        SPACE -> "Top-level folders are black holes, folders are stars, pages are gas giants " +
            "and ideas are rocky planets. Bigger holds more."
        NEURONS -> "Top-level folders are cells, folders are the parts inside them, " +
            "pages are vesicles and ideas granules. Ideas in no folder share one cell. " +
            "Links grow out of the cells as axons carrying impulses."
        PERFORMANCE -> "Every idea is a point and every link a line, drawn so 100,000 fit at once. " +
            "Everything in a top-level folder is drawn in one colour. Ideas too far away to show one by one share one soft light."
    }

    val cardSteps: List<String> get() = when (this) {
        // Space: iOS's three (GraphTheme.swift:136-141). The [[ step says what to do and what it makes
        // with no colon joining two clauses (S05j 7d); a colon after a control path stays
        SPACE -> listOf("Hold an idea › Choose folder › New folder: a new galaxy round its black hole.",
            "Type an idea in a folder in Sort: a new rocky planet.",
            "Type [[ and another idea’s title in an idea to join them with a link.")
        // Map mode has no + menu and no bar (S05f item 4): a top-level folder comes from Choose
        // folder on an idea held on the map (in a folder in Sort it would make one inside), an
        // idea from Sort, and nothing in Sorta makes a page
        NEURONS -> listOf("Hold an idea › Choose folder › New folder: a new cell.",
            "Type an idea in Sort: a new granule.",
            "Type [[ and another idea’s title in an idea to join them with an axon.")
        PERFORMANCE -> listOf("Hold an idea › Choose folder › New folder: a folder of points in one colour.",
            "Type an idea in Sort: a new point.",
            "Type [[ and another idea’s title in an idea to join them with a line.")
    }

    companion object {
        const val KEY = "vignette.space.theme"
        val standard = SPACE
        const val HINTS_KEY = "vignette.space.themeHintsSeen"

        /** The themes the Look menu offers, in its order. */
        val offered: List<GraphTheme> get() = entries.filter { it.isReady }

        /** A stored value, or the standard for anything unknown or not ready. */
        fun stored(raw: String?): GraphTheme = entries.firstOrNull { it.rawValue == raw && it.isReady } ?: standard
    }
}

/** The iOS names of the six styles (GraphNodeStyle's raw values), for the stored choice. */
val SpaceStyle.rawValue: String get() = when (this) {
    SpaceStyle.BLACK_HOLE -> "blackHole"
    SpaceStyle.ROCKY -> "rocky"
    SpaceStyle.GAS_GIANT -> "gasGiant"
    SpaceStyle.COMET -> "comet"
    SpaceStyle.PULSAR -> "pulsar"
    SpaceStyle.SUN -> "sun"
}

fun spaceStyle(raw: String?): SpaceStyle? = SpaceStyle.entries.firstOrNull { it.rawValue == raw }

/**
 * Which look the notes take in Space (GraphStyleChoice): the Universe ([AUTO]) or one style for
 * every note, and optionally a style per top-level folder.
 */
object GraphStyleChoice {
    const val KEY = "vignette.space.nodeStyle"
    const val FOLDERS_KEY = "vignette.space.nodeStyleFolders"
    const val AUTO = "auto"
    const val STANDARD = AUTO

    /** "id=style;id=style" to a map; anything unreadable is skipped. */
    fun folders(raw: String): Map<String, SpaceStyle> {
        val found = LinkedHashMap<String, SpaceStyle>()
        for (part in raw.split(";")) {
            val pieces = part.split("=")
            if (pieces.size != 2 || !isId(pieces[0])) continue
            val style = spaceStyle(pieces[1]) ?: continue
            found[pieces[0]] = style
        }
        return found
    }

    fun encode(map: Map<String, SpaceStyle>): String =
        map.keys.sortedBy { it.uppercase() }.joinToString(";") { "$it=${map.getValue(it).rawValue}" }

    /**
     * Every note's style for the single looks. A main that is not a style falls back to pages
     * as gas giants and ideas as rocky planets; a folder look wins for that folder's notes.
     */
    fun resolve(book: IdeaBook, main: String, folderRaw: String): Map<String, SpaceStyle> {
        val single = spaceStyle(main)
        val result = LinkedHashMap<String, SpaceStyle>()
        for (note in book.notes) result[note.id] = single ?: if (note.noteKind == NoteKind.PAGE) SpaceStyle.GAS_GIANT else SpaceStyle.ROCKY
        result.putAll(overrides(book, folderRaw))
        return result
    }

    /** Each note's folder look, from its top-level folder. */
    fun overrides(book: IdeaBook, folderRaw: String): Map<String, SpaceStyle> {
        val byFolder = folders(folderRaw)
        if (byFolder.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, SpaceStyle>()
        for (note in book.notes) {
            val root = book.rootFolder(note.folderId) ?: continue
            result[note.id] = byFolder[root] ?: continue
        }
        return result
    }

    /** Folder palettes, so two folders of planets are not twins: 0, 1 or 2 from the folder's id. */
    fun palette(folder: String?): Int {
        if (folder == null) return 0
        var total = 0L
        for (c in folder.uppercase()) total = total * 31 + c.code
        return (abs(total) % 3).toInt()
    }

    /**
     * Android's map: every node's style code, in [ids]' order. The single looks as [resolve]
     * gives them; the Universe (not ported: GraphUniverse plans galaxies from the folders) is
     * Android's stand-in, a note's style by its links ([SpaceStyle.forLinks]), with the folder
     * looks over it.
     */
    fun codes(book: IdeaBook, ids: List<String>, linkCounts: IntArray, main: String, folderRaw: String): ByteArray {
        val chosen: Map<String, SpaceStyle> = if (spaceStyle(main) != null) resolve(book, main, folderRaw) else overrides(book, folderRaw)
        return ByteArray(ids.size) { i -> (chosen[ids[i]] ?: SpaceStyle.forLinks(linkCounts.getOrElse(i) { 0 })).code.toByte() }
    }

    /** A UUID, as iOS's UUID(uuidString:) reads one: 36 characters, hex in five groups. */
    private fun isId(s: String) = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}").matches(s)
}
