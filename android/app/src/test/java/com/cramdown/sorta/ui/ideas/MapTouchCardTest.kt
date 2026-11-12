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
import com.cramdown.sorta.ui.SortaTheme
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
// while they are unseen, else "Touching the map"; the three touch lines while they are unseen; Full
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
            assertTrue("$t: the touch lines", shown(MapPanelTags.TOUCH_HINT))
            for (line in listOf("Tap to preview", "Tap again or Open to read", "Hold to move or for options")) assertEquals("$t: $line", 1, count(line, MapPanelTags.TOUCH_HINT))
            assertEquals(0, count("Touching the map"))
        }
        steps = false
        compose.waitForIdle()
        assertEquals("steps seen: the touch title", 1, count("Touching the map"))
        assertFalse(shown(MapPanelTags.HINT_STEPS))
        assertTrue(shown(MapPanelTags.TOUCH_HINT))
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
}
