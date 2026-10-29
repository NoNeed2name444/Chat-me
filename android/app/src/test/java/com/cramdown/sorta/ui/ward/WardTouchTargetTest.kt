package com.cramdown.sorta.ui.ward

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.mock.StudyTitleBar
import com.cramdown.sorta.ui.sources.ReaderBackButton
import com.cramdown.sorta.ui.sources.ReaderDoneButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A6: every control the kit makes is at least 48 by 48 dp (Android's least
// target, WardControlSize.target), at the default text size and at font
// scale 2.0. Each control is drawn on its own, and every node it makes that
// can be tapped is measured three ways:
//  - its touch area (what touch and TalkBack use, as Material's own tests
//    measure it) is at least 48 by 48;
//  - the control lays out room for it: a 48 by 48 square on the node's
//    centre lies inside the control's own laid-out bounds, so the touch area
//    never reaches into a neighbour's room;
//  - no two of a control's 48-dp squares overlap, so none steals another's.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WardTouchTargetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val least = WardControlSize.target

    private enum class Pick { list, grid }

    private val controls: List<Pair<String, @Composable () -> Unit>> = listOf(
        "a primary button" to { WardTextButton("Start", {}, kind = WardButtonKind.primary) },
        "a secondary button" to { WardTextButton("Skip", {}, kind = WardButtonKind.secondary) },
        "a destructive button" to { WardTextButton("Delete", {}, kind = WardButtonKind.destructive) },
        "a compact button" to { WardTextButton("Done", {}, kind = WardButtonKind.compact, fills = false) },
        "a quiet button" to { WardTextButton("Cancel", {}, kind = WardButtonKind.quiet, fills = false) },
        "a glyph-only button" to { WardButtonBox({}, kind = WardButtonKind.secondary, fills = false) { Icon(Icons.Rounded.Add, "Add") } },
        "a chip with words" to { WardChipButton("Pinned", {}) },
        "a chip with a glyph alone" to { WardChipButton(null, {}, icon = Icons.Rounded.Add) },
        "a chosen chip" to { WardChipButton("Pinned", {}, on = true) },
        "a circle button" to { WardCircleButton(Icons.Rounded.Add, "Add", {}) },
        "a tile" to { Box(Modifier.width(120.dp)) { WardTile({}) { WardTileLabel("Add", Icons.Rounded.Lightbulb) } } },
        "a switch with words" to { WardToggle(true, {}, label = "Night sky") },
        "a switch alone" to { WardToggle(false, {}) },
        "a segmented control" to {
            WardSegmented(Pick.list, Pick.entries, {}, Modifier.fillMaxWidth()) { p, style, colour -> Text(p.name, style = style, color = colour) }
        },
        "a text field" to { WardTextField("", {}, Modifier.fillMaxWidth(), placeholder = "Search") },
        "a banner's action" to { WardBanner("Saved", actionTitle = "Undo", action = {}) },
        "a filter chip" to { WardFilterChip("Pinned", selected = false, onClick = {}) },
        "Back" to { ReaderBackButton {} },
        "Done" to { ReaderDoneButton {} },
        "the title bar's Back" to { StudyTitleBar("Ideas", onBack = {}) },
        "the switcher, open" to { switcher(collapsed = false) },
        "the switcher, folded" to { switcher(collapsed = true) },
        "the study deck, open with its fold" to { Column { StudyActionBar(folds = true) { WardTextButton("Start", {}) } } },
    )

    @Composable private fun switcher(collapsed: Boolean) {
        val items = listOf(SwitcherItem(Pick.list, "List", Icons.Rounded.ViewList, "pick.list"),
            SwitcherItem(Pick.grid, "Grid", Icons.Rounded.GridView, "pick.grid"))
        FloatingSwitcher(items, Pick.list, {}, collapsed, {}, "pick.toggle")
    }

    private var which by mutableIntStateOf(0)
    private var scale by mutableStateOf(1f)

    @Test fun everyControlIsTheLeastTargetAtBothSizes() = checks {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                SortaTheme {
                    Box(Modifier.fillMaxWidth()) { Box(Modifier.testTag("slot")) { controls[which].second() } }
                }
            }
        }
        var measured = 0
        for (s in listOf(1f, 2f)) {
            for (i in controls.indices) {
                scale = s
                which = i
                compose.waitForIdle()
                measured += measure("${controls[i].first} at font scale $s")
            }
            // the deck folded: the round button that opens it again
            which = controls.indexOfFirst { it.first.startsWith("the study deck") }
            compose.waitForIdle()
            compose.onNode(hasClickAction() and hasContentDescription(compose.activity.getString(com.cramdown.sorta.R.string.ward_deck_hide)))
                .performClick()
            compose.waitForIdle()
            measured += measure("the study deck, folded, at font scale $s")
        }
        check("every control made something to tap (${controls.size} controls and the folded deck, twice)",
            measured >= 2 * (controls.size + 1), "$measured nodes")
    }

    /** Checks every tappable node now on screen; returns how many it measured. */
    private fun WardChecks.measure(what: String): Int {
        val density = compose.density.density
        val side = (least * density).toFloat()
        val slot = compose.onNodeWithTag("slot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        check("$what: found something to tap", nodes.isNotEmpty())
        val squares = mutableListOf<Pair<String, Rect>>()
        for (node in nodes) {
            val label = describe(node)
            val touch = node.touchBoundsInRoot
            check("$what ($label): its touch area is at least ${least.toInt()} by ${least.toInt()} dp",
                touch.width >= side - 0.5f && touch.height >= side - 0.5f,
                "${f2(touch.width / density.toDouble())} x ${f2(touch.height / density.toDouble())} dp")
            val c = node.boundsInRoot.center
            val square = Rect(c.x - side / 2, c.y - side / 2, c.x + side / 2, c.y + side / 2)
            check("$what ($label): the control lays out ${least.toInt()} by ${least.toInt()} dp of room for it",
                square.left >= slot.left - 0.5f && square.top >= slot.top - 0.5f &&
                    square.right <= slot.right + 0.5f && square.bottom <= slot.bottom + 0.5f,
                "room ${f2(slot.width / density.toDouble())} x ${f2(slot.height / density.toDouble())} dp, " +
                    "node ${f2(node.size.width / density.toDouble())} x ${f2(node.size.height / density.toDouble())} dp")
            squares += label to square
        }
        for ((i, a) in squares.withIndex()) for (b in squares.drop(i + 1)) {
            val o = a.second.intersect(b.second)
            check("$what: ${a.first} and ${b.first} keep their own ${least.toInt()} dp", o.width <= 0.5f || o.height <= 0.5f)
        }
        return nodes.size
    }

    private fun describe(node: SemanticsNode): String {
        val c = node.config
        val words = c.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.joinToString()
            ?: c.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString()
            ?: c.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }
        return words ?: "node ${node.id}"
    }
}
