package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapPeek
import com.cramdown.sorta.ideas.MapPeekInput
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WardControlSize
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.f2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// S05b task 4, A6: the map's panels (MapPanels.kt), drawn as composables, since Robolectric can't
// draw the GL map under them, in the style of SortTouchTargetTest. Each panel (an idea's peek card,
// a folder's, the Link length bar, the first-run card, the no-folder hint, and the options More
// opens) is drawn at font scale 1 and 2, on a phone and on a folded phone's outer screen. Every
// node on it that can be tapped and is wholly in view is measured:
//  - its touch area is at least 48 by 48 dp (WardControlSize.target);
//  - no two targets' 48 dp squares overlap, a panel and the controls inside it apart (the card is
//    tapped as a whole, round its own buttons);
// the slider is at least 48 dp tall; and every line of words lays out whole, apart from those that
// end in an ellipsis by design (the card's title and lines, as iOS's lineLimit). The naming sheet the
// options' Rename opens (MapPeekCardTest), with the map's words, is measured the same way: its
// field, Cancel and Rename (S03-result.md's checklist: the touch-target line covers the name field).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapPanelsTouchTargetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val least = WardControlSize.target
    private val words by lazy { mapPeekWords(compose.activity.resources) }

    private class Panel(val name: String, val tag: String, val expect: List<String>, val draw: @Composable () -> Unit)

    private val panels get() = listOf(
        Panel("an idea's peek card", MapPanelTags.PEEK, listOf("Open", "More", "Bulbs")) {
            val links = listOf("a" to "Bulbs", "b" to "Shed", "c" to "Gate")
            MapPeekCard(MapPeek.content(MapPeekInput("space", "gasGiant", folder = false, title = "Oil the gate before the frost comes in",
                body = "First a line of words that runs on past the card's width.\nThen another.\nAnd a third.\nNot this one.", links = links), words),
                folder = false, words = words, open = {}, flyIn = {}, chip = {}, option = {})
        },
        Panel("a folder's peek card", MapPanelTags.PEEK, listOf("Open folder", "Fly in")) {
            MapPeekCard(MapPeek.content(MapPeekInput("neurons", "0", folder = true, title = "Garden", notes = 4, subfolders = 2,
                recent = listOf("Bulbs", "Shed", "Gate"), flyIn = true), words),
                folder = true, words = words, open = {}, flyIn = {}, chip = {}, option = {})
        },
        Panel("the Link length bar", MapPanelTags.LINK_BAR, listOf("Done", "Shorter", "Longer")) {
            MapLinkLengthBar(1.4, {}, {})
        },
        Panel("the first-run card", MapPanelTags.HINT, listOf("Full list", "Got it")) { MapTouchCard({}, {}) },
        Panel("the no-folder hint", MapPanelTags.EMPTY_HINT, listOf("Add folder")) { MapEmptyHint(GraphTheme.NEURONS, {}) },
    )

    private var which by mutableIntStateOf(0)
    private var scale by mutableFloatStateOf(1f)

    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    @Test fun everyTargetAndEveryWordFitsOnAPhoneAtBothSizes() = all("phone")

    @Config(qualifiers = "w360dp-h748dp-port-xxhdpi")
    @Test fun everyTargetAndEveryWordFitsOnAFoldedPhoneAtBothSizes() = all("folded phone")

    /** More on an idea's card opens its options: each row a target, Delete last. */
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    @Test fun theOptionsAreTargetsAtBothSizes() = checks {
        compose.setContent { host { key(scale) { panels[0].draw() } } }
        for (s in listOf(1f, 2f)) {
            scale = s
            compose.waitForIdle()
            compose.onNode(hasClickAction() and hasContentDescription(words.more)).performClick()
            compose.waitForIdle()
            val what = "the options at font scale $s"
            val rows = measure(MapPanelTags.MENU, what)
            check("$what: Rename, Choose folder and Delete, in order", rows == listOf(words.rename, words.moveToFolder, words.delete), rows.joinToString(" | "))
        }
    }

    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    @Test fun theRenameSheetsFieldAndButtonsAreTargetsOnAPhoneAtBothSizes() = renameSheet("phone")

    @Config(qualifiers = "w360dp-h748dp-port-xxhdpi")
    @Test fun theRenameSheetsFieldAndButtonsAreTargetsOnAFoldedPhoneAtBothSizes() = renameSheet("folded phone")

    private fun renameSheet(device: String) = checks {
        val res = compose.activity.resources
        val field = res.getString(R.string.map_rename_idea_field)
        val expect = listOf(field, res.getString(R.string.sheet_cancel), res.getString(R.string.name_rename))
        compose.setContent {
            host {
                key(scale) {
                    NameSheet(res.getString(R.string.map_rename_idea_title), field, "Oil the gate before the frost comes in",
                        res.getString(R.string.name_rename), onDismiss = {}) {}
                }
            }
        }
        for (s in listOf(1f, 2f)) {
            scale = s
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000) // the sheet comes up
            compose.waitForIdle()
            val what = "the map's rename sheet on a $device at font scale $s"
            val seen = measure(NAME_SHEET_TAG, what)
            for (word in expect) check("$what: \"$word\" is a target in view", seen.any { it.contains(word) }, seen.joinToString(" | "))
        }
    }

    @Composable private fun host(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
            SortaTheme(dark = true) {
                WindowShapeHost {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) { content() }
                }
            }
        }
    }

    private fun all(device: String) = checks {
        val list = panels
        compose.setContent { host { key(which, scale) { list[which].draw() } } }
        for (s in listOf(1f, 2f)) for (i in list.indices) {
            scale = s
            which = i
            compose.waitForIdle()
            val what = "${list[i].name} on a $device at font scale $s"
            val seen = measure(list[i].tag, what)
            for (word in list[i].expect) check("$what: \"$word\" is a target in view", seen.any { it.contains(word) }, seen.joinToString(" | "))
            if (list[i].tag == MapPanelTags.LINK_BAR) {
                val slider = compose.onNode(hasTestTag(MapPanelTags.LINK_SLIDER)).fetchSemanticsNode()
                val tall = slider.touchBoundsInRoot.height / compose.density.density
                check("$what: the slider is at least ${least.toInt()} dp tall", tall >= least - 0.5, "${f2(tall.toDouble())} dp")
                check("$what: the slider says its value", slider.config.getOrNull(SemanticsProperties.StateDescription) == "Longer, 1.4×")
            }
        }
    }

    /** Checks the panel tagged [tag] as it is now; returns the labels of its targets wholly in view. */
    private fun WardChecks.measure(tag: String, what: String): List<String> {
        val density = compose.density.density
        val side = (least * density).toFloat()
        val panel = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
        check("$what: it is there", panel.size == 1, "${panel.size} found")
        if (panel.size != 1) return emptyList()
        val inPanel = hasAnyAncestor(hasTestTag(tag)) or hasTestTag(tag)
        val whole = compose.onAllNodes(hasClickAction() and inPanel).fetchSemanticsNodes().filter { wholly(it) }
        check("$what: found targets wholly in view", whole.isNotEmpty())
        val squares = mutableListOf<Triple<String, Rect, SemanticsNode>>()
        for (node in whole) {
            val label = describe(node)
            val touch = node.touchBoundsInRoot
            check("$what ($label): its touch area is at least ${least.toInt()} by ${least.toInt()} dp",
                touch.width >= side - 0.5f && touch.height >= side - 0.5f,
                "${f2(touch.width / density.toDouble())} x ${f2(touch.height / density.toDouble())} dp")
            val c = node.boundsInRoot.center
            squares += Triple(label, Rect(c.x - side / 2, c.y - side / 2, c.x + side / 2, c.y + side / 2), node)
        }
        for ((i, a) in squares.withIndex()) for (b in squares.drop(i + 1)) {
            if (inside(a.third, b.third) || inside(b.third, a.third)) continue
            val o = a.second.intersect(b.second)
            check("$what: ${a.first} and ${b.first} keep their own ${least.toInt()} dp", o.width <= 0.5f || o.height <= 0.5f,
                "${f2(o.width / density.toDouble())} x ${f2(o.height / density.toDouble())} dp shared")
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and inPanel, useUnmergedTree = true).fetchSemanticsNodes()
        check("$what: found words", texts.isNotEmpty())
        for (node in texts) {
            val words = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: continue
            val layout = layoutOf(node) ?: continue
            val designed = layout.layoutInput.overflow == TextOverflow.Ellipsis
            check("$what (\"$words\"): its words lay out whole", designed || !layout.didOverflowHeight, "${layout.lineCount} lines")
        }
        return whole.filter { it.config.getOrNull(SemanticsProperties.TestTag) != tag }.map { describe(it) }
    }

    /** Whether [inner] sits inside [outer] in the tree. */
    private fun inside(inner: SemanticsNode, outer: SemanticsNode): Boolean = generateSequence(inner.parent) { it.parent }.any { it.id == outer.id }

    private fun wholly(node: SemanticsNode): Boolean {
        val b = node.boundsInRoot
        return b.width >= node.size.width - 1f && b.height >= node.size.height - 1f
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val get = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val out = mutableListOf<TextLayoutResult>()
        return if (get(out)) out.firstOrNull() else null
    }

    private fun describe(node: SemanticsNode): String {
        val c = node.config
        return c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: c.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: c.getOrNull(SemanticsProperties.TestTag)
            ?: "node ${node.id}"
    }
}
