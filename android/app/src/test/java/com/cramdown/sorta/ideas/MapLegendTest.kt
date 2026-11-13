package com.cramdown.sorta.ideas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The map's legends (MapLegend, S05f), against iOS's GraphLegendContent: each theme's ladder and
// rows in iOS's order with iOS's names, symbols and tints, only what Android's map draws (of S05g's
// work, Show links and the empty double tap), how-to lines that name controls Sorta has, and touch
// help the map's hint repeats.
class MapLegendTest {
    private val everyLine: List<String>
        get() = GraphTheme.entries.flatMap(::linesOf)

    /** Every line [theme]'s legend and card show. */
    private fun linesOf(theme: GraphTheme): List<String> {
        val c = MapLegend.of(theme, readout = true)
        return c.rows.flatMap { listOf(it.name, it.text, it.how) } + c.rungs.map { it.caption } + listOf(c.ladderCaption, c.ladderLabel, c.touch) + theme.cardSteps
    }

    /** Space's rows (S05h), pinned word for word: iOS's (GraphLegend.swift:228-252), the how-lines in Android's words. */
    private val spacePinned = listOf(
        LegendRow("circle.circle.fill", "Black hole",
            "A top-level folder: the heart of a galaxy. Ideas filed straight in it orbit close; its folders shine round it as stars. Bigger holds more ideas.",
            LegendTint(1.0, 0.68, 0.28), MapLegend.MAKE_FOLDER),
        LegendRow("sun.max.fill", "Star",
            "A folder inside a folder: yellow, orange one level deeper, red deeper still. Bigger holds more; a folder is always drawn a little smaller than the one it sits in. An empty folder is a dark star.",
            LegendTint(1.0, 0.8, 0.0), MapLegend.FOLDER_INSIDE),
        LegendRow("circle.lefthalf.filled", "Gas giant", "A page. Bigger is longer; rings mean over 250 words.", LegendTint(0.86, 0.70, 0.48)),
        LegendRow("globe.europe.africa.fill", "Rocky planet", "An idea. Brighter means more links.", LegendTint(0.35, 0.6, 1.0), MapLegend.ADD_IDEA),
        LegendRow("moon.fill", "Moon", "A short idea linked only to the one it circles.", LegendTint(0.7, 0.7, 0.7), "Link a short idea to one other idea."),
        LegendRow("dot.radiowaves.left.and.right", "Pulsar", "An idea linking ideas in two or more other folders. Its links pulse together.",
            LegendTint(0.7, 0.8, 1.0), "Link an idea to ideas in two other folders."),
        LegendRow("sparkle", "Comet",
            "An idea in no folder. It swings past the folder it links to most; file it and it settles into orbit. Older ones wait in the far cloud.",
            LegendTint(0.55, 1.0, 0.9), "Move an idea out of its folder: Choose folder › To sort."),
        LegendRow("point.3.connected.trianglepath.dotted", "Links",
            "Straight inside a folder, arched between folders, faint between galaxies. Links never pull.", LegendTint(1.0, 0.78, 0.5), MapLegend.LINK_IDEAS),
    )

    /** Space's touch help (S05h), pinned word for word: Fly in, Recentre, the planets following, inner orbits. */
    private val spaceTouch = "Tap a body to preview it on a card. Tap again or Open to read an idea, or Open folder for a folder. " +
        "On a star’s or black hole’s card, Fly in takes you into its system. Recentre brings back the whole map. " +
        "Show links on an idea’s card keeps only that idea’s links and dims the rest. All links brings everything back. " +
        "Hold a body, then drag to move it. Move a star and its planets follow. Let go and it springs back. " +
        "Hold a body still to see its options. Tap empty space to close the card, or twice for the whole map. " +
        "Inner orbits turn faster. Drag to turn, two fingers to slide, pinch to zoom."

    @Test fun spaceHasIosLadderAndRowsWithTheMoon() {
        val c = MapLegend.of(GraphTheme.SPACE)
        // iOS's five rungs (GraphLegend.swift:220-226), caption and label (:257-258)
        assertEquals(listOf("Top folder" to 30, "Folder" to 24, "Page" to 18, "Idea" to 13, "Detail" to 8), c.rungs.map { it.caption to it.size })
        assertEquals(listOf(LegendTint(0.0, 0.0, 0.0), LegendTint(1.0, 0.95, 0.78), LegendTint(0.82, 0.66, 0.46), LegendTint(0.25, 0.48, 0.85),
            LegendTint(0.62, 0.62, 0.62)), c.rungs.map { it.fill })
        assertEquals(listOf(LegendTint(1.0, 0.68, 0.28), null, LegendTint(0.95, 0.86, 0.66), null, null), c.rungs.map { it.ring })
        assertEquals(listOf(true, false, false, false, false), c.rungs.map { it.halo })
        assertEquals(listOf(false, false, true, false, false), c.rungs.map { it.ringed })
        assertEquals("Bigger bodies hold more.", c.ladderCaption)
        assertEquals("Size ladder: top folder, folder, page, idea, detail", c.ladderLabel)
        // iOS's eight rows, the Moon among them, each word for word
        assertEquals(spacePinned, c.rows)
        assertEquals(spaceTouch, c.touch)
    }

    @Test fun neuronsHasIosLadderAndRows() {
        val c = MapLegend.of(GraphTheme.NEURONS)
        assertEquals(listOf("Cell" to 30, "Part" to 22, "Page" to 12, "Idea" to 9), c.rungs.map { it.caption to it.size })
        assertTrue("all haloed", c.rungs.all { it.halo && !it.ringed })
        assertEquals("Bigger cells hold more.", c.ladderCaption)
        assertEquals("Size ladder: cell, part, page, idea", c.ladderLabel)
        assertEquals(LegendTint(0.35, 1.0, 0.5, 0.22), c.rungs[0].fill)
        assertEquals(listOf("Cell", "Cell part", "Vesicle", "Granule", "To sort", "Opening a cell", "Cell states", "Axons and synapses"), c.rows.map { it.name })
        assertEquals(listOf("circle.hexagongrid.fill", "smallcircle.filled.circle", "circle.fill", "aqi.medium", "tray.fill",
            "arrow.up.left.and.arrow.down.right", "sparkles", "bolt.horizontal.fill"), c.rows.map { it.symbol })
        assertEquals(listOf(MapLegend.MAKE_FOLDER, MapLegend.FOLDER_INSIDE, "", MapLegend.ADD_IDEA, "Choose folder › To sort.",
            "Tap a cell, then Fly in on its card.", CELL_STATES_HOW, MapLegend.LINK_IDEAS), c.rows.map { it.how })
    }

    @Test fun performanceHasTheRungsAndRowsAndroidDraws() {
        val c = MapLegend.of(GraphTheme.PERFORMANCE)
        assertEquals(listOf("Glow" to 30, "Idea" to 11, "Far idea" to 5), c.rungs.map { it.caption to it.size })
        assertEquals("Nearer things show more detail.", c.ladderCaption)
        assertEquals("Size ladder: glow, idea, far idea", c.ladderLabel)
        assertEquals(listOf("Folder", "Idea", "Glow", "Lines"), c.rows.map { it.name })
        assertEquals("Speed only while the readout shows", listOf("Folder", "Idea", "Glow", "Lines", "Speed"),
            MapLegend.of(GraphTheme.PERFORMANCE, readout = true).rows.map { it.name })
        assertEquals(LegendTint(0.36, 0.61, 1.0), c.rows[1].tint)
        assertEquals(LegendTint(1.0, 0.71, 0.26, 0.35), c.rungs[0].fill)
    }

    @Test fun noneAboutWorkStillToCome() {
        // every line but Space's pinned rows and touch help (S05h's Moon, ladder, planets following and
        // inner orbits), which are checked word for word below; taken out of Space's lines only, so a line
        // another look shares is still checked there
        val space = MapLegend.of(GraphTheme.SPACE)
        val pinned = (space.rows.flatMap { listOf(it.name, it.text, it.how) } + space.touch).toSet()
        val all = GraphTheme.entries.flatMap { theme -> linesOf(theme).let { if (theme == GraphTheme.SPACE) it - pinned else it } }
            .joinToString("\n").lowercase()
        // S05g brought Show links and the empty double tap, S05g2 the move (theTouchHelpIsTheHint); the
        // rest waits
        // S05i brought the cell states: "cell states" and "breathing" left this list for the exact check of
        // Neurons' Cell states row below
        for (later in listOf("moon", "its planets", "planets follow", "folder lines", "thin grey lines", "hub with", "tour",
            "orbits")) {
            assertFalse("\"$later\" waits for later work", all.contains(later))
        }
        // Neurons' rows run …, Opening a cell, Cell states, Axons and synapses, the Cell states row iOS's word for word
        val neurons = MapLegend.of(GraphTheme.NEURONS).rows
        assertEquals(listOf("Opening a cell", "Cell states", "Axons and synapses"), neurons.map { it.name }.takeLast(3))
        val cells = neurons.single { it.name == "Cell states" }
        assertEquals(CELL_STATES_TEXT, cells.text)
        assertEquals(CELL_STATES_HOW, cells.how)
        assertEquals("sparkles", cells.symbol)
        assertEquals(LegendTint(0.82, 0.36, 1.0), cells.tint)
        // and only there: no other line of any look names a cell state's breathing
        val cellsPinned = setOf(cells.name, cells.text, cells.how)
        assertFalse("breathing only in the Cell states row", GraphTheme.entries.flatMap { linesOf(it) }.filter { it !in cellsPinned }
            .any { it.lowercase().contains("breathing") || it.lowercase().contains("cell states") })
        // the four allowed in Space only where pinned: every pinned Space row and the touch help exactly
        assertEquals(spacePinned, space.rows)
        assertEquals(spaceTouch, space.touch)
        // and no row says what Android does not draw: no larger page point in Performance
        assertFalse(MapLegend.of(GraphTheme.PERFORMANCE, true).rows.any { it.name == "Page" })
        // nothing of S05h2's or S05i's (deaths, sparks, cell states, the Look filter)
        // (Neurons' Cell states row, S05i's own, is pinned above and left out here)
        val words = everyLine.filter { it !in cellsPinned }.joinToString("\n").lowercase()
        for (later in listOf("death", "spark", "cell state", "look filter", "ghost")) assertFalse("\"$later\" is S05h2's or S05i's", words.contains(later))
    }

    @Test fun noLineNamesAPlusMenuOrTheBar() {
        for (line in everyLine) {
            assertFalse("names + ›: $line", line.contains("+ ›"))
            assertFalse("names the bar: $line", line.contains("the bar"))
            assertFalse("names New page: $line", line.contains("New page"))
        }
        assertEquals(listOf("Hold an idea › Choose folder › New folder: a new cell.", "Type an idea in Sort: a new granule."), GraphTheme.NEURONS.cardSteps.take(2))
        // Android's Performance draws no hub (S05g), so the step names the folder's colour
        assertEquals(listOf("Hold an idea › Choose folder › New folder: a folder of points in one colour.", "Type an idea in Sort: a new point."),
            GraphTheme.PERFORMANCE.cardSteps.take(2))
    }

    @Test fun theTouchHelpIsTheHint() {
        for (theme in GraphTheme.entries) {
            assertEquals(MapLegend.touchHelp(theme), theme.hint)
            assertEquals(theme.hint, MapLegend.of(theme).touch)
            val help = theme.hint
            assertTrue("$theme: a tap previews", help.contains("preview"))
            assertTrue("$theme: a hold for its options", help.contains("Hold"))
            assertTrue("$theme: turn, slide, zoom", help.contains("Drag to turn, two fingers to slide, pinch to zoom."))
            assertFalse("$theme: the stale words are gone", help.contains("no card") || help.contains("no hold"))
            assertFalse("$theme: one sentence a clause, no semicolons", help.contains(";"))
            assertTrue("$theme: the touch card's words", help.contains("Tap again or Open to read"))
            // S05g: Show links and All links as the card names them, the empty double tap
            assertTrue("$theme: Show links and All links", help.contains("Show links") && help.contains("All links"))
            assertTrue("$theme: two taps on empty space", help.contains("Tap empty space to close the card, or twice for the whole map."))
        }
        // S05g2: a body held, then dragged, moves and springs back, in Space and Neurons (GraphTheme.swift:99, :102),
        // before the still hold's options; Performance's hold is options only (:105)
        val move = "Hold a body, then drag to move it. "
        val back = "Let go and it springs back. "
        val still = "Hold a body still to see its options."
        // S05h: in Space a moved star's planets follow, as a moved cell's insides do
        assertTrue(MapLegend.touchHelp(GraphTheme.SPACE).contains(move + "Move a star and its planets follow. " + back + still))
        assertTrue(MapLegend.touchHelp(GraphTheme.NEURONS).contains(move + "Move a cell and everything inside it follows. " + back + still))
        // S05h: Fly in on a folder's card and Recentre, as the code does; never two taps to fly (iOS's footer, GraphLegend.swift:254)
        val space = MapLegend.touchHelp(GraphTheme.SPACE)
        assertTrue(space.contains("On a star’s or black hole’s card, Fly in takes you into its system. Recentre brings back the whole map."))
        assertFalse("no two taps to fly", space.contains("twice to fly") || space.contains("Tap a star"))
        for (theme in GraphTheme.entries) assertEquals("$theme: one move sentence", if (theme == GraphTheme.PERFORMANCE) 0 else 1,
            Regex("drag to move it").findAll(theme.hint).count())
        val perf = MapLegend.touchHelp(GraphTheme.PERFORMANCE)
        assertFalse("Performance moves nothing", perf.lowercase().contains("move") || perf.contains("springs back"))
        assertTrue(perf.contains("Hold a point still to see its options."))
        assertTrue(MapLegend.touchHelp(GraphTheme.NEURONS).contains("Fly in") && MapLegend.touchHelp(GraphTheme.NEURONS).contains("Recentre"))
    }

    @Test fun eachRowReadsAsOneItem() {
        val cell = MapLegend.of(GraphTheme.NEURONS).rows.first()
        assertEquals("Cell: a top-level folder: a whole cell, glowing through its membrane round a bright nucleus. Its folders and ideas float " +
            "inside it. Bigger holds more. Make a folder: hold an idea on the map, then Choose folder › New folder.", MapLegend.spoken(cell))
        // the how line read on its own: To sort is not a thing to add
        val toSort = MapLegend.of(GraphTheme.NEURONS).rows[4]
        assertEquals("To sort: the ideas in no folder share one cell, opened like the others. Move an idea into a folder and it joins " +
            "that folder’s cell. Choose folder › To sort.", MapLegend.spoken(toSort))
        val comet = MapLegend.of(GraphTheme.SPACE).rows[6]
        assertEquals("Comet: an idea in no folder. It swings past the folder it links to most; file it and it settles into orbit. " +
            "Older ones wait in the far cloud. Move an idea out of its folder: Choose folder › To sort.", MapLegend.spoken(comet))
    }

    // S05j 7d: each [[ step says what to do and what it makes, with no colon joining two clauses;
    // the same words as iOS's (GraphTheme.swift:143, :147, :151; MapLookTests.swift)
    @Test fun theLinkStepsJoinNoClausesWithAColon() {
        assertEquals("Type [[ and another idea’s title in an idea to join them with a link.", GraphTheme.SPACE.cardSteps.last())
        assertEquals("Type [[ and another idea’s title in an idea to join them with an axon.", GraphTheme.NEURONS.cardSteps.last())
        assertEquals("Type [[ and another idea’s title in an idea to join them with a line.", GraphTheme.PERFORMANCE.cardSteps.last())
        for (theme in GraphTheme.entries) {
            val step = theme.cardSteps.single { it.contains("[[") }
            assertFalse("$theme: no colon in \"$step\"", step.contains(":"))
            // a colon after a control path stays
            for (other in theme.cardSteps - step) assertTrue("$theme: \"$other\"", other.indexOf(':') < 0 || other.startsWith("Hold an idea › Choose folder › New folder:") || other.startsWith("Type an idea in"))
        }
    }

    // S05j 7e: Space's touch help follows iOS's hint (GraphTheme.swift:97-99) where the two maps do the
    // same, and says more only where Android's map does: Fly in, Show links, the double tap, two fingers
    @Test fun spacesTouchHelpFollowsIosWords() {
        val space = MapLegend.touchHelp(GraphTheme.SPACE)
        assertTrue(space.startsWith("Tap a body to preview it on a card. "))
        assertFalse("iOS's words: preview it, not its idea", space.contains("preview its idea"))
        // iOS: "Hold a body to move it, or hold it still for its options." Android splits it, one idea a
        // sentence, and says what letting go does (S05g's ux-writing finding)
        assertTrue(space.contains("Hold a body, then drag to move it. "))
        assertTrue(space.contains("Hold a body still to see its options. "))
        assertTrue("iOS's last clause, the same", space.endsWith("pinch to zoom."))
        assertEquals("one text", space, GraphTheme.SPACE.hint)
    }

    private companion object {
        /** Neurons' Cell states row, iOS's words (GraphLegend.swift:302-304). */
        const val CELL_STATES_TEXT = "Each idea shows a state, as the space’s bodies have styles: resting, slowly breathing; firing, a burst of spikes with calcium waves spreading; releasing, a cloud of transmitter drifting out; pacemaker, a steady beat with two lobes sweeping round; migrating, crawling on behind its growth cone; engulfing, drawing debris into a dark phagosome. Left alone, every idea rests."
        const val CELL_STATES_HOW = "Look › Cells: one state for every idea, or Cell by cell for one folder."
    }
}
