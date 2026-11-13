package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.SpaceStyle

// What the map's peek card says: a port of iOS's GraphPeek, GraphPeekInput and GraphPeekContent
// (ios/Sorta/Maps/GraphTouchModel.swift:212-360). What a touch on the map was is map/MapSelection.kt's
// (S05g). The words come in as a [MapPeekWords], which MapPanels builds from strings.xml, so this file
// stays pure Kotlin (MapPeekTest). Android's own: none of Link to… or Add note here in the options.

/** What the card is told about a body (GraphPeekInput). */
data class MapPeekInput(
    /** "space", "neurons" or "performance" (GraphTheme.rawValue). */
    val theme: String,
    /** The theme's own role: a [MapPeek.spaceRole] key in Space, the ThemeBody's role in Neurons, or "". */
    val role: String,
    val folder: Boolean,
    /** The home: the Neurons cell the ideas in no folder share. */
    val home: Boolean = false,
    /** Whether the home is beside folders, holding only the ideas in none. */
    val unsorted: Boolean = false,
    val title: String,
    val page: Boolean = false,
    val body: String = "",
    /** Its links: id and title. */
    val links: List<Pair<String, String>> = emptyList(),
    val notes: Int = 0,
    val subfolders: Int = 0,
    /** Its latest ideas' titles, newest first. */
    val recent: List<String> = emptyList(),
    /** Whether Fly in is offered (a folder or the home, on a map that can fly in to it). */
    val flyIn: Boolean = false,
)

data class MapPeekChip(val id: String, val title: String)

/** What the card shows (GraphPeekContent). */
data class MapPeekContent(
    val title: String,
    /** "Gas giant · Idea". */
    val kind: String,
    /** An idea's first lines; a folder's counts and latest ideas. */
    val lines: List<String>,
    val chips: List<MapPeekChip>,
    /** Links not shown as chips. */
    val moreLinks: Int,
    /** "Open" or "Open folder". */
    val primary: String,
    /** "Show links" and "More" for an idea; "Fly in" for a folder that can fly in. */
    val secondary: List<String>,
    /** What TalkBack reads for the card. */
    val spoken: String,
)

/** A body's options (GraphPeek.menu), as far as Android does them. */
enum class MapMenuItem { OPEN, RENAME, MOVE, DELETE }

/** The card's and the options' words, from strings.xml (MapPanels' mapPeekWords). */
class MapPeekWords(
    val untitled: String,
    val idea: String,
    val page: String,
    val folder: String,
    val toSort: String,
    val allIdeas: String,
    val nothingYet: String,
    val open: String,
    val openFolder: String,
    val flyIn: String,
    /** An idea's Show links, and its words while the links are shown (GraphMapPanels.swift:160-170). */
    val showLinks: String,
    val allLinks: String,
    val more: String,
    val rename: String,
    val moveToFolder: String,
    val delete: String,
    /** Each theme's word for a role, keyed "theme:role" ("space:gasGiant", "neurons:0"). */
    val roles: Map<String, String>,
    val ideas: (Int) -> String,
    val subfolders: (Int) -> String,
    val links: (Int) -> String,
    /** "Latest: a, b". */
    val latest: (String) -> String,
    /** "+2 more". */
    val moreLinks: (Int) -> String,
) {
    fun item(item: MapMenuItem): String = when (item) {
        MapMenuItem.OPEN -> open
        MapMenuItem.RENAME -> rename
        MapMenuItem.MOVE -> moveToFolder
        MapMenuItem.DELETE -> delete
    }
}

object MapPeek {
    const val CHIP_LIMIT = 6
    const val LINE_LIMIT = 3
    private const val DOT = " · "

    /** The Space role key for a body's look (iOS UniverseRole's raw values, GraphTouchModel.swift:275-278). */
    fun spaceRole(style: SpaceStyle): String = when (style) {
        SpaceStyle.BLACK_HOLE -> "galaxy"
        SpaceStyle.SUN -> "star"
        SpaceStyle.GAS_GIANT -> "gasGiant"
        SpaceStyle.ROCKY -> "rocky"
        SpaceStyle.COMET -> "comet"
        SpaceStyle.PULSAR -> "pulsar"
    }

    /**
     * Whether a folder's or the home's card offers Fly in: every Neurons container, and in Space a
     * Universe black hole, star or home (S05h; GraphMapPanels.swift:155-157, isFolder,
     * Graph3DView.swift:663-665). Under one single style Space has no folder bodies, so none.
     */
    fun fliesIn(theme: String, role: String): Boolean = when (theme) {
        "neurons" -> true
        "space" -> role == "galaxy" || role == "star" || role == "home"
        else -> false
    }

    /** The theme's own word for a body (roleWord), or null. */
    fun roleWord(theme: String, role: String, words: MapPeekWords): String? =
        if (role.isEmpty()) null else words.roles["$theme:$role"]

    /** The plain type (plainType): the home beside folders is To sort, as the folder chooser names it. */
    fun plainType(folder: Boolean, home: Boolean, unsorted: Boolean, page: Boolean, words: MapPeekWords): String = when {
        home -> if (unsorted) words.toSort else words.allIdeas
        folder -> words.folder
        page -> words.page
        else -> words.idea
    }

    /** Up to [LINE_LIMIT] lines of an idea's text, without its markup (firstLines, :292-307). */
    fun firstLines(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n", "\r")) {
            var line = raw.trim()
            while (line.isNotEmpty() && line[0] in "#-*>") line = line.drop(1).trim()
            line = line.replace("[[", "").replace("]]", "").replace("**", "")
            if (line.isEmpty()) continue
            // by code point, so an emoji across the cut stays whole (S05j 6c)
            if (line.codePointCount(0, line.length) > 140) line = line.substring(0, line.offsetByCodePoints(0, 139)) + "…"
            out.add(line)
            if (out.size == LINE_LIMIT) break
        }
        return out
    }

    /** The card for [input] (content, :314-344). */
    fun content(input: MapPeekInput, words: MapPeekWords): MapPeekContent {
        val plain = plainType(input.folder, input.home, input.unsorted, input.page, words)
        val own = roleWord(input.theme, input.role, words) ?: plain
        val kind = if (own == plain) plain else own + DOT + plain
        val title = input.title.ifEmpty { words.untitled }
        if (input.folder || input.home) {
            val lines = mutableListOf(words.ideas(input.notes) + DOT + words.subfolders(input.subfolders))
            val recent = input.recent.take(3).map { it.ifEmpty { words.untitled } }
            if (recent.isNotEmpty()) lines.add(words.latest(recent.joinToString(", ")))
            val spoken = "$title, $kind. " + lines.joinToString(". ")
            return MapPeekContent(title, kind, lines, emptyList(), 0, words.openFolder,
                if (input.flyIn) listOf(words.flyIn) else emptyList(), spoken)
        }
        val sorted = input.links.sortedWith(compareBy<Pair<String, String>> { it.second.lowercase() }.thenBy { it.first.uppercase() })
        val chips = sorted.take(CHIP_LIMIT).map { MapPeekChip(it.first, it.second.ifEmpty { words.untitled }) }
        val more = maxOf(sorted.size - chips.size, 0)
        val lines = firstLines(input.body).ifEmpty { listOf(words.nothingYet) }
        val spoken = "$title, $kind, ${words.links(input.links.size)}. " + lines.joinToString(" ")
        return MapPeekContent(title, kind, lines, chips, more, words.open, listOf(words.showLinks, words.more), spoken)
    }

    /**
     * The options held on a body, in order (menu, :355-359), minus what Android has no sheet or
     * action for: Link to… (the editor's link picker is its own) and Add note here (ideas are added
     * in Sort).
     */
    fun menu(folder: Boolean, home: Boolean): List<MapMenuItem> = when {
        home -> listOf(MapMenuItem.OPEN)
        folder -> listOf(MapMenuItem.OPEN, MapMenuItem.RENAME, MapMenuItem.DELETE)
        else -> listOf(MapMenuItem.OPEN, MapMenuItem.RENAME, MapMenuItem.MOVE, MapMenuItem.DELETE)
    }
}
