package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.cramdown.sorta.ideas.GraphTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardControlSize
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.wardDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The first-run card (S05g; iOS's GraphUniverseHint and hintShown): the theme's title and its steps
// while they are unseen, else "Touching the map" and the three touch lines while they are unseen (S05j:
// not beside the steps, so the card fits a third of the phone); Full
// list and Got it each put it away for that theme, steps and touch lines both, Space's steps under
// its own old Boolean key and the other themes' in iOS's comma list.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTouchCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = compose.activity

    @Before fun clear() {
        compose.activity.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun count(text: String, inside: String = MapPanelTags.HINT) =
        compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(inside)), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun itShowsTheThemesTitleStepsAndTouchLines() {
        var theme by mutableStateOf(GraphTheme.SPACE)
        var steps by mutableStateOf(true)
        var touch by mutableStateOf(true)
        compose.setContent {
            SortaTheme(dark = true) {
                key(theme, steps, touch) {
                    MapTouchCard({}, {}, title = if (steps) theme.cardTitle else null, touch = touch,
                        steps = if (steps) theme.cardSteps else emptyList())
                }
            }
        }
        for (t in GraphTheme.entries) {
            theme = t; steps = true; touch = true
            compose.waitForIdle()
            assertEquals("$t: its title", 1, count(t.cardTitle))
            assertTrue("$t: its steps", shown(MapPanelTags.HINT_STEPS))
            for (step in t.cardSteps) assertEquals("$t: \"$step\"", 1, count(step, MapPanelTags.HINT_STEPS))
            // S05j item 4: with the steps, the touch lines are left to the legend, so the card fits a third
            assertFalse("$t: no touch lines beside the steps", shown(MapPanelTags.TOUCH_HINT))
            assertEquals(0, count("Touching the map"))
        }
        steps = false
        compose.waitForIdle()
        assertEquals("steps seen: the touch title", 1, count("Touching the map"))
        assertFalse(shown(MapPanelTags.HINT_STEPS))
        assertTrue(shown(MapPanelTags.TOUCH_HINT))
        for (line in listOf("Tap to preview", "Tap again or Open to read", "Hold to move or for options")) assertEquals(line, 1, count(line, MapPanelTags.TOUCH_HINT))
        steps = true; touch = false
        compose.waitForIdle()
        assertFalse("touch seen: no touch lines", shown(MapPanelTags.TOUCH_HINT))
        assertTrue(shown(MapPanelTags.HINT_STEPS))
    }

    @Test fun fullListAndGotItEachCallTheirs() {
        var more by mutableIntStateOf(0)
        var done by mutableIntStateOf(0)
        compose.setContent { SortaTheme(dark = true) { MapTouchCard({ more++ }, { done++ }) } }
        compose.onNode(hasText("Full list") and hasClickAction()).performClick()
        compose.onNode(hasText("Got it") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, more)
        assertEquals(1, done)
    }

    @Test fun itShowsUntilBothAreSeenAndEachThemeKeepsItsOwn() {
        val settings = MapPanelSettings(context)
        for (t in GraphTheme.entries) assertTrue("$t: a first run shows it", settings.cardShown(t))
        settings.markTouchSeen(GraphTheme.NEURONS)
        assertTrue("the steps still unseen", settings.cardShown(GraphTheme.NEURONS))
        settings.markStepsSeen(GraphTheme.NEURONS)
        assertFalse(settings.cardShown(GraphTheme.NEURONS))
        assertTrue(settings.cardShown(GraphTheme.SPACE))
        settings.markCardSeen(GraphTheme.SPACE)
        assertFalse(settings.cardShown(GraphTheme.SPACE))
        assertTrue(settings.cardShown(GraphTheme.PERFORMANCE))
        // kept, under iOS's keys
        val again = MapPanelSettings(context)
        assertFalse(again.cardShown(GraphTheme.NEURONS)); assertFalse(again.cardShown(GraphTheme.SPACE)); assertTrue(again.cardShown(GraphTheme.PERFORMANCE))
        val prefs = context.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE)
        assertEquals("vignette.space.universeHintSeen", MapStepsSeen.SPACE_KEY)
        assertEquals("vignette.space.themeHintsSeen", MapStepsSeen.KEY)
        assertTrue(prefs.getBoolean(MapStepsSeen.SPACE_KEY, false))
        assertEquals("neurons", prefs.getString(MapStepsSeen.KEY, ""))
        assertEquals("space and neurons' touch lines", setOf("neurons", "space"), prefs.getString(MapTouchSeen.KEY, "")!!.split(",").toSet())
        assertTrue(MapStepsSeen.seen("space,neurons", "neurons"))
        assertEquals("performance", MapStepsSeen.marking("", "performance"))
    }

    // S05j item 4 (question 2): at most a third of a phone's height at the default text size, with its
    // title, the touch lines while unseen and the theme's steps, placed as IdeasSpace places it
    @Test fun theCardIsAtMostAThirdOfAPhone() {
        var theme by mutableStateOf(GraphTheme.SPACE)
        compose.setContent {
            SortaTheme(dark = true) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(start = WardSpace.gutter.wardDp,
                    end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp)) {
                    key(theme) { MapTouchCard({}, {}, title = theme.cardTitle, touch = true, steps = theme.cardSteps) }
                }
            }
        }
        // the themes that show it (Performance's map shows no first-run card)
        for (t in listOf(GraphTheme.SPACE, GraphTheme.NEURONS)) {
            theme = t
            compose.waitForIdle()
            val card = compose.onNode(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNode().boundsInRoot
            val tall = card.height / compose.activity.resources.displayMetrics.density
            assertTrue("$t: $tall dp, over a third of 914", tall <= 914f / 3)
            // all of it shown: the steps first, the touch lines kept whole or left to the legend
            for (step in t.cardSteps) assertEquals("$t: \"$step\"", 1, count(step, MapPanelTags.HINT_STEPS))
        }
    }

    // S05j item 4: at large text the lines scroll inside a third of the phone and both buttons stay whole
    @Test fun atLargeTextTheLinesScrollAndTheButtonsStayWhole() {
        val third = (914f / 3).dp
        compose.setContent {
            SortaTheme(dark = true) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(density.density, 2f)) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.heightIn(max = third).padding(start = WardSpace.gutter.wardDp,
                        end = (WardSpace.gutter + WardControlSize.target + WardSpace.m).wardDp)) {
                        MapTouchCard({}, {}, title = GraphTheme.SPACE.cardTitle, touch = true, steps = GraphTheme.SPACE.cardSteps)
                    }
                }
            }
        }
        compose.waitForIdle()
        val scale = compose.activity.resources.displayMetrics.density
        val card = compose.onNode(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNode().boundsInRoot
        assertTrue("the card within a third: ${card.height / scale} dp", card.height / scale <= 914f / 3 + 0.5f)
        val scrolls = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag(MapPanelTags.HINT)))
            .fetchSemanticsNodes()
        val range = scrolls.singleOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        assertTrue("the lines scroll: more of them than the room", range != null && range.maxValue() > 0f)
        for (name in listOf("Full list", "Got it")) {
            val button = compose.onNode(hasText(name) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("$name inside the card", button.top >= card.top && button.bottom <= card.bottom + 0.5f)
            assertTrue("$name at least 48 dp tall: ${button.height / scale}", button.height / scale >= 47.5f)
        }
    }
}
