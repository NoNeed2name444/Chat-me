package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The hubs and the folder lines (S05i2): one line per folder with a parent, hub to parent hub, listed
// after the links (GraphPerfScene.swift:76-85), drawn while either end is in view and both show
// (GraphPerfClusters.swift:330-336), ×1.2 and ×0.15 under Show links (GraphPerfShaders.swift:216-221);
// a hub clamped 3-48 dp against a note's 1.25-28, never faded, its colour mixed 0.45 to white
// (GraphPerfLOD.swift:29-30, :50-51; GraphPerfScene.swift:66-71). Made-up fixtures.
class PerfFolderLinesTest {
    // folders: 0 "Top", 1 under 0, 2 under 1, 3 another top; notes: 0 in 2, 1 in 3, 2 loose; 0 linked to 1
    private val input = PerfInput(listOf("N0", "N1", "N2"), booleanArrayOf(false, true, false), intArrayOf(2, 3, -1),
        listOf("F0", "F1", "F2", "F3"), intArrayOf(-1, 0, 1, -1), intArrayOf(0, 1))
    private val map = PerfMap.build(input)!!
    private val g = map.graph

    @Test fun aLinePerFolderWithAParentHubToParentHub() {
        assertArrayEquals(intArrayOf(g.hubNode(1), g.hubNode(0), g.hubNode(2), g.hubNode(1)), map.lines)
        assertEquals(2 * (g.folderCount - 2), map.lines.size)
        val ends = PerfLines.ends(g)
        assertArrayEquals("links first, then the folder lines", intArrayOf(0, 1) + map.lines, ends)
    }

    @Test fun drawnWhileEitherEndIsInViewAndBothShow() {
        val lines = intArrayOf(3, 4)
        assertTrue(PerfLines.visible(lines, 0, { it == 3 }, null))
        assertTrue(PerfLines.visible(lines, 0, { it == 4 }, null))
        assertFalse(PerfLines.visible(lines, 0, { false }, null))
        val shown = BooleanArray(5) { true }
        assertTrue(PerfLines.visible(lines, 0, { true }, shown))
        shown[4] = false
        assertFalse("an end filtered away hides the line", PerfLines.visible(lines, 0, { true }, shown))
    }

    @Test fun aFolderLineIsBrighterThanALinkAndFadesUnderShowLinks() {
        assertEquals(1.2f, PerfLook.lineAlpha(focus = false), 0f)
        assertEquals(1.2f * 0.15f, PerfLook.lineAlpha(focus = true), 1e-7f)
    }

    @Test fun hubsClampedThreeToFortyEightNotesOnePointTwoFiveToTwentyEight() {
        assertEquals(3f, PerfLook.drawnRadius(0.5f, hub = true), 0f)
        assertEquals(48f, PerfLook.drawnRadius(90f, hub = true), 0f)
        assertEquals(20f, PerfLook.drawnRadius(20f, hub = true), 0f)
        assertEquals(1.25f, PerfLook.drawnRadius(0.5f, hub = false), 0f)
        assertEquals(28f, PerfLook.drawnRadius(90f, hub = false), 0f)
    }

    @Test fun aHubNeverFadesANoteFadesBelowAboutAPixel() {
        assertEquals(1f, PerfLook.alpha(0.1f, hub = true), 0f)
        assertEquals(0f, PerfLook.alpha(0.35f, hub = false), 0f)
        assertEquals(0.5f, PerfLook.alpha(0.8f, hub = false), 1e-6f)
        assertEquals(1f, PerfLook.alpha(1.25f, hub = false), 0f)
    }

    @Test fun aHubsColourIsItsRegionsMixedPointFourFiveToWhite() {
        assertArrayEquals(floatArrayOf(0.45f, 0.725f, 1f), PerfLook.hubColour(floatArrayOf(0f, 0.5f, 1f)), 1e-6f)
    }

    @Test fun eachBodyCarriesItsRegionsColourAndItsHubCode() {
        // F0's region (rank 0) holds F1, F2 and note 0; F3 is the second top (rank 1); the loose note group 0
        assertEquals(map.groups[g.noteOfInput[0]], map.groups[g.hubNode(g.inputOfFolder.indexOf(0))])
        assertEquals(map.groups[g.hubNode(g.inputOfFolder.indexOf(2))], map.groups[g.hubNode(g.inputOfFolder.indexOf(1))])
        assertEquals(0.toByte(), map.groups[g.noteOfInput[2]])
        assertTrue((0 until g.noteCount).all { map.hubCodes[it] == 0.toByte() })
        for (f in 0 until g.folderCount) {
            val code = map.hubCodes[g.hubNode(f)].toInt() and 0xFF
            assertEquals(PerfLook.hubCode(map.seed.radius[g.hubNode(f)]), code)
            assertEquals(map.seed.radius[g.hubNode(f)], PerfLook.hubRadiusOf(code), PerfLook.HUB_RADIUS_MAX / 255f)
        }
    }

    @Test fun onTheHundredThousandEveryFolderWithAParentHasALine() {
        val g = PerfVault.graph
        val lines = PerfLines.of(g)
        assertEquals(2 * (0 until g.folderCount).count { g.folderParent[it] >= 0 }, lines.size)
        for (k in 0 until lines.size / 2) assertEquals(g.folderParent[lines[2 * k] - g.noteCount] + g.noteCount, lines[2 * k + 1])
    }
}
