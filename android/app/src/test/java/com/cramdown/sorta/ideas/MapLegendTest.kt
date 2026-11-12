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
        get() = GraphTheme.entries.flatMap { theme ->
            val c = MapLegend.of(theme, readout = true)
            c.rows.flatMap { listOf(it.name, it.text, it.how) } + c.rungs.map { it.caption } + listOf(c.ladderCaption, c.ladderLabel, c.touch) + theme.cardSteps
        }

    @Test fun spaceHasNoLadderAndItsSixLooksAndLinks() {
        val c = MapLegend.of(GraphTheme.SPACE)
        assertTrue("no folder or page bodies, so no ladder", c.rungs.isEmpty() && c.ladderCaption.isEmpty() && c.ladderLabel.isEmpty())
        // iOS's order, less the Moon (not drawn)
        assertEquals(listOf("Black hole", "Star", "Gas giant", "Rocky planet", "Pulsar", "Comet", "Links"), c.rows.map { it.name })
        assertEquals(listOf("circle.circle.fill", "sun.max.fill", "circle.lefthalf.filled", "globe.europe.africa.fill",
            "dot.radiowaves.left.and.right", "sparkle", "point.3.connected.trianglepath.dotted"), c.rows.map { it.symbol })
        assertEquals(LegendTint(1.0, 0.68, 0.28), c.rows[0].tint)
        assertEquals(LegendTint(0.35, 0.6, 1.0), c.rows[3].tint)
        assertEquals(LegendTint(1.0, 0.78, 0.5), c.rows[6].tint)
        // each look's line is its link count, as the card says (GraphStyleChoice.codes)
        assertEquals(listOf("eight links or more", "five to seven links", "three or four links", "two links", "one link", "no links"),
            c.rows.take(6).map { it.text.removePrefix("An idea with ").removeSuffix(".") })
        assertEquals("only Links says how", listOf(MapLegend.LINK_IDEAS), c.rows.map { it.how }.filter { it.isNotEmpty() })
    }

    @Test fun neuronsHasIosLadderAndRows() {
        val c = MapLegend.of(GraphTheme.NEURONS)
        assertEquals(listOf("Cell" to 30, "Part" to 22, "Page" to 12, "Idea" to 9), c.rungs.map { it.caption to it.size })
        assertTrue("all haloed", c.rungs.all { it.halo && !it.ringed })
        assertEquals("Bigger cells hold more.", c.ladderCaption)
        assertEquals("Size ladder: cell, part, page, idea", c.ladderLabel)
        assertEquals(LegendTint(0.35, 1.0, 0.5, 0.22), c.rungs[0].fill)
        assertEquals(listOf("Cell", "Cell part", "Vesicle", "Granule", "To sort", "Opening a cell", "Axons and synapses"), c.rows.map { it.name })
        assertEquals(listOf("circle.hexagongrid.fill", "smallcircle.filled.circle", "circle.fill", "aqi.medium", "tray.fill",
            "arrow.up.left.and.arrow.down.right", "bolt.horizontal.fill"), c.rows.map { it.symbol })
        assertEquals(listOf(MapLegend.MAKE_FOLDER, MapLegend.FOLDER_INSIDE, "", MapLegend.ADD_IDEA, "Choose folder › To sort.",
            "Tap a cell, then Fly in on its card.", MapLegend.LINK_IDEAS), c.rows.map { it.how })
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
        val all = everyLine.joinToString("\n").lowercase()
        // S05g brought Show links and the empty double tap (theTouchHelpIsTheHint); the rest waits
        for (later in listOf("moon", "cell states", "hold to move", "move it", "folder lines", "thin grey lines", "hub with", "tour",
            "breathing", "orbits")) {
            assertFalse("\"$later\" waits for later work", all.contains(later))
        }
        // and no row says what Android does not draw: no page body in Space, no larger page point in Performance
        assertFalse(MapLegend.of(GraphTheme.PERFORMANCE, true).rows.any { it.name == "Page" })
        assertFalse(MapLegend.of(GraphTheme.SPACE).rows.any { it.text.contains("folder") || it.text.contains("page") })
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
            // S05g: Show links and All links as the card names them, the empty double tap, and no move yet (S05g2's)
            assertTrue("$theme: Show links and All links", help.contains("Show links") && help.contains("All links"))
            assertTrue("$theme: two taps on empty space", help.contains("Tap empty space to close the card, or twice for the whole map."))
            assertFalse("$theme: nothing moves a body yet", help.lowercase().contains("move"))
        }
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
        val comet = MapLegend.of(GraphTheme.SPACE).rows[5]
        assertEquals("Comet: an idea with no links.", MapLegend.spoken(comet))
    }
}
