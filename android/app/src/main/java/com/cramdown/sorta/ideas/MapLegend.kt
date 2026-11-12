package com.cramdown.sorta.ideas

// A theme's legend (S05f), in iOS's form (GraphLegend.swift, GraphLegendContent): a size ladder,
// one row per kind of body (a tinted symbol, a name, one line and how to make one), and how to
// touch the map. iOS's names, symbols, tints and rungs; the words say what Android's map draws
// and does, so a row, a rung or a sentence about something it does not draw yet is left out.
// Pure Kotlin, tested in MapLegendTest; the sheet is IdeasSpace's LegendSheet.

/** A colour, sRGB, each channel 0..1, and its opacity (iOS's Color(red:green:blue:) and .opacity). */
data class LegendTint(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
    fun opacity(alpha: Double) = copy(a = alpha)

    companion object {
        fun white(w: Double) = LegendTint(w, w, w)
    }
}

/** One circle of the size ladder (GraphLegendRung): its caption, its size in dp, its fill and ring, a halo round it or a ring across it. */
data class LegendRung(val caption: String, val size: Int, val fill: LegendTint, val ring: LegendTint? = null, val halo: Boolean = false, val ringed: Boolean = false)

/** One row (GraphLegendRow): iOS's SF Symbol name (the sheet draws its Material stand-in), the name, one line, the tint, and how to make one ("" for none). */
data class LegendRow(val symbol: String, val name: String, val text: String, val tint: LegendTint, val how: String = "")

/** What a theme's legend says (GraphLegendContent): the ladder, the line under it and what TalkBack reads for it, the rows and the touch help. */
data class LegendContent(val rungs: List<LegendRung>, val ladderCaption: String, val ladderLabel: String, val rows: List<LegendRow>, val touch: String)

object MapLegend {
    /** The words of the how-to lines both platforms share (S05f item 4). */
    const val MAKE_FOLDER = "Make a folder: hold an idea on the map, then Choose folder › New folder."
    const val FOLDER_INSIDE = "Make a folder inside a folder: in Sort, hold a folder › New folder inside."
    const val ADD_IDEA = "Add an idea: type it in Sort."
    const val LINK_IDEAS = "Link two ideas: type [[ and one idea’s title in the other."

    /** The theme's legend; the Speed row only while the readout shows ([readout], Performance). */
    fun of(theme: GraphTheme, readout: Boolean = false): LegendContent = when (theme) {
        GraphTheme.SPACE -> LegendContent(emptyList(), "", "", spaceRows, touchHelp(theme))
        GraphTheme.NEURONS -> LegendContent(neuronRungs, "Bigger cells hold more.", "Size ladder: cell, part, page, idea", neuronRows, touchHelp(theme))
        GraphTheme.PERFORMANCE -> LegendContent(performanceRungs, "Nearer things show more detail.", "Size ladder: glow, idea, far idea",
            performanceRows.filter { it.name != "Speed" || readout }, touchHelp(theme))
    }

    /**
     * How to touch Android's map, under the legend's rows and read by TalkBack after the map's own
     * label (GraphTheme.hint): a tap previews a body on its card, a second tap or Open reads it (a
     * folder's card says Open folder); Show links on an idea's card keeps its own links (Space,
     * Neurons) or brightens them (Performance) and All links brings the rest back (S05g); a body
     * held still opens its options while the finger is down; a tap on empty space closes the card
     * and two bring back the whole map; in Neurons, Fly in on a cell's or part's card opens it and
     * Recentre closes it; one finger turns, two slide, a pinch zooms. One idea a sentence, in the
     * card's words (map_touch_again, map_peek_show_links, map_peek_all_links). No move: hold to
     * move is S05g2's.
     */
    fun touchHelp(theme: GraphTheme): String = when (theme) {
        GraphTheme.SPACE -> "Tap a body to preview its idea on a card. Tap again or Open to read it. " +
            "Show links on the card keeps only that idea’s links and dims the rest. All links brings everything back. " +
            "Hold a body still to see its options. Tap empty space to close the card, or twice for the whole map. " +
            "Drag to turn, two fingers to slide, pinch to zoom."
        GraphTheme.NEURONS -> "Tap a cell, a part or an idea to preview it on a card. Tap again or Open to read an idea, or Open folder for a folder. " +
            "On a cell’s or part’s card, Fly in opens it on the map. Recentre closes it again. " +
            "Show links on an idea’s card keeps only that idea’s links and dims the rest. All links brings everything back. " +
            "Hold a body still to see its options. Tap empty space to close the card, or twice for the whole map. " +
            "Drag to turn, two fingers to slide, pinch to zoom."
        GraphTheme.PERFORMANCE -> "Tap a point to preview its idea on a card. Tap again or Open to read it. " +
            "Show links on the card brightens that idea’s links and dims the others. All links brings everything back. " +
            "Hold a point still to see its options. Tap empty space to close the card, or twice for the whole map. " +
            "Drag to turn, two fingers to slide, pinch to zoom."
    }

    /**
     * What TalkBack reads for a row, as iOS's VoiceOver: "Name: text. How", the how line read on its
     * own (each starts with what to do, and not every row is a thing to add, To sort for one).
     */
    fun spoken(row: LegendRow): String {
        val said = row.name + ": " + lowered(row.text)
        return if (row.how.isEmpty()) said else "$said ${row.how}"
    }

    /** The row's text starting lower case, for "Name: text". */
    fun lowered(text: String): String = if (text.isEmpty()) text else text.substring(0, 1).lowercase() + text.substring(1)

    // Space: one body per idea, its look chosen by its links (GraphStyleChoice.codes, the card's
    // text); no folder or page bodies, so no ladder, no Moon, and no row's folder or page meaning

    private val gold = LegendTint(1.0, 0.68, 0.28)
    /** SwiftUI's .yellow. */
    private val yellow = LegendTint(1.0, 0.8, 0.0)
    private val gasTint = LegendTint(0.86, 0.70, 0.48)
    private val rockTint = LegendTint(0.35, 0.6, 1.0)
    private val pulsarTint = LegendTint(0.7, 0.8, 1.0)
    private val cometTint = LegendTint(0.55, 1.0, 0.9)
    private val beamTint = LegendTint(1.0, 0.78, 0.5)

    private val spaceRows = listOf(
        LegendRow("circle.circle.fill", "Black hole", "An idea with eight links or more.", gold),
        LegendRow("sun.max.fill", "Star", "An idea with five to seven links.", yellow),
        LegendRow("circle.lefthalf.filled", "Gas giant", "An idea with three or four links.", gasTint),
        LegendRow("globe.europe.africa.fill", "Rocky planet", "An idea with two links.", rockTint),
        LegendRow("dot.radiowaves.left.and.right", "Pulsar", "An idea with one link.", pulsarTint),
        LegendRow("sparkle", "Comet", "An idea with no links.", cometTint),
        LegendRow("point.3.connected.trianglepath.dotted", "Links",
            "A curve between two linked ideas, or a straight line with Look › Lines › Straight. A new link grows in.", beamTint, LINK_IDEAS),
    )

    // Neurons: iOS's structure (map/Neurons.kt); no cell states yet

    private val teal = LegendTint(0.35, 1.0, 0.5)
    private val green = LegendTint(0.25, 0.92, 1.0)
    private val amber = LegendTint(1.0, 0.72, 0.30)
    private val interTint = LegendTint(1.0, 0.42, 0.78)
    private val crossTint = LegendTint(0.75, 0.58, 1.0)

    private val neuronRungs = listOf(
        LegendRung("Cell", 30, teal.opacity(0.22), teal, halo = true),
        LegendRung("Part", 22, green.opacity(0.22), green, halo = true),
        LegendRung("Page", 12, teal.opacity(0.3), teal.opacity(0.8), halo = true),
        LegendRung("Idea", 9, teal.opacity(0.3), teal.opacity(0.6), halo = true),
    )

    private val neuronRows = listOf(
        LegendRow("circle.hexagongrid.fill", "Cell",
            "A top-level folder: a whole cell, glowing through its membrane round a bright nucleus. Its folders and ideas float inside it. Bigger holds more.",
            teal, MAKE_FOLDER),
        LegendRow("smallcircle.filled.circle", "Cell part",
            "A folder inside a folder: an organelle inside its cell, and a smaller one inside that for each folder deeper down. Each holds its own ideas.",
            green, FOLDER_INSIDE),
        LegendRow("circle.fill", "Vesicle",
            "A page: a bright-rimmed vesicle floating inside the folder that holds it. Longer pages are bigger.", teal),
        LegendRow("aqi.medium", "Granule", "An idea: a small bright granule inside the folder that holds it.", interTint, ADD_IDEA),
        LegendRow("tray.fill", "To sort",
            "The ideas in no folder share one cell, opened like the others. Move an idea into a folder and it joins that folder’s cell.",
            teal, "Choose folder › To sort."),
        LegendRow("arrow.up.left.and.arrow.down.right", "Opening a cell",
            "A cell shows its parts and ideas only when you open it. Recentre closes it again.", crossTint, "Tap a cell, then Fly in on its card."),
        LegendRow("bolt.horizontal.fill", "Axons and synapses",
            "Links. Each grows out of its cell as part of it, thick where it leaves and tapering to a synapse on the cell it reaches; impulses run along it. Inside an open cell, links between its ideas run inside it.",
            amber, LINK_IDEAS),
    )

    // Performance: every idea and page one point in its top-level folder's colour, far chunks one
    // glow, links coloured by their ends (MapTheme.PLAIN); no hubs, no larger page points, no folder lines yet

    private val hubWhite = LegendTint(0.86, 0.9, 1.0)
    private val pointBlue = LegendTint(0.36, 0.61, 1.0)
    private val glowAmber = LegendTint(1.0, 0.71, 0.26)
    private val lineGrey = LegendTint.white(0.75)

    private val performanceRungs = listOf(
        LegendRung("Glow", 30, glowAmber.opacity(0.35)),
        LegendRung("Idea", 11, pointBlue),
        LegendRung("Far idea", 5, lineGrey),
    )

    private val performanceRows = listOf(
        LegendRow("circle.hexagongrid.fill", "Folder", "Everything in a top-level folder is drawn in one colour.", hubWhite, MAKE_FOLDER),
        LegendRow("smallcircle.filled.circle", "Idea", "A point in its folder’s colour. A page is a point too.", pointBlue, ADD_IDEA),
        LegendRow("sun.min.fill", "Glow",
            "Ideas too far away to show one by one share one soft light. Links into it end at its middle. Come closer and it opens into its points.",
            glowAmber),
        LegendRow("line.diagonal", "Lines", "Links, coloured by the ideas at their ends, fainter in the distance.", lineGrey, LINK_IDEAS),
        LegendRow("speedometer", "Speed", "The readout at the top left counts the ideas, the links and the frames a second.", lineGrey),
    )
}
