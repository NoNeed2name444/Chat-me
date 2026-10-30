package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import com.cramdown.sorta.R
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

// A7: the Sort screens' targets and words, in the style of WardTouchTargetTest. Each screen (To
// sort with ideas and folders, an open folder, the editor, the folder chooser, and the empty
// state) is drawn at font scale 1 and 2, on a phone and on a folded phone's outer screen. Every
// node on it that can be tapped and is wholly in view is measured:
//  - its touch area is at least 48 by 48 dp (WardControlSize.target);
//  - a 48 dp square on its centre lies inside its window, so there is room for it;
//  - no two of the squares overlap, so no target steals another's.
// What lies further down a list is scrolled to and measured there. And no words are cut: every
// line of text lays out whole (none runs past its lines or its box),
// apart from an idea's own line, which ends in an ellipsis by design and is read whole by its
// row's label; and outside the scrolling lists, every piece of text is drawn whole in its box.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SortTouchTargetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val least = WardControlSize.target
    private fun s(id: Int) = compose.activity.getString(id)

    /** A screen: its name, where it opens, the tag its own window carries, and words it must show to tap. */
    private class Screen(val name: String, val filled: Boolean, val start: IdeasStart, val tag: String, val expect: List<String>)

    private val screens get() = listOf(
        Screen("To sort", true, IdeasStart(), IdeasTags.SCREEN,
            listOf(s(R.string.capture_sort), s(R.string.sort_more), "Garden", IdeasFixture.gate.title)),
        Screen("Garden, open", true, IdeasStart(folderId = IdeasFixture.garden.id), IdeasTags.SCREEN,
            listOf(s(R.string.capture_sort), "Bulbs", IdeasFixture.shed.title)),
        Screen("the editor", true, IdeasStart(editing = IdeasFixture.shed.id), EDITOR_TAG,
            listOf(s(R.string.editor_title), s(R.string.editor_write), s(R.string.editor_read), s(R.string.editor_link_to))),
        Screen("the folder chooser", true, IdeasStart(choosing = IdeasFixture.gate.id), FOLDER_CHOOSER_TAG,
            listOf(s(R.string.sheet_cancel), s(R.string.chooser_to_sort_spoken), "Garden", "Bulbs", s(R.string.chooser_new_folder))),
        Screen("the empty state", false, IdeasStart(), IdeasTags.SCREEN,
            listOf(s(R.string.capture_sort), s(R.string.sort_more))),
    )

    private var which by mutableIntStateOf(0)
    private var scale by mutableFloatStateOf(1f)

    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    @Test fun everyTargetAndEveryWordFitsOnAPhoneAtBothSizes() = all("phone")

    @Config(qualifiers = "w360dp-h748dp-port-xxhdpi")
    @Test fun everyTargetAndEveryWordFitsOnAFoldedPhoneAtBothSizes() = all("folded phone")

    private fun all(device: String) = checks {
        val list = screens
        val models = list.map { IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, it.filled)) }
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                SortaTheme {
                    WindowShapeHost {
                        key(which, scale) { IdeasView(models[which], start = list[which].start) }
                    }
                }
            }
        }
        for (s in listOf(1f, 2f)) for (i in list.indices) {
            scale = s
            which = i
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000) // the chooser's sheet comes up
            compose.waitForIdle()
            val what = "${list[i].name} on a $device at font scale $s"
            val seen = measure(list[i], what).toMutableList()
            // what lies further down the list: scrolled to, and measured there too
            for (word in list[i].expect.filter { w -> seen.none { it.contains(w) } }) {
                val target = hasClickAction() and (hasText(word, substring = true) or hasContentDescription(word, substring = true))
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag(list[i].tag)))
                    .onFirst().performScrollToNode(target)
                compose.waitForIdle()
                seen += measure(list[i], "$what, scrolled to \"$word\"")
            }
            for (word in list[i].expect) check("$what: \"$word\" is a target in view", seen.any { it.contains(word) }, seen.distinct().joinToString(" | "))
        }
    }

    /** Checks the screen as it is now; returns the labels of the targets wholly in view. */
    private fun WardChecks.measure(screen: Screen, what: String): List<String> {
        val density = compose.density.density
        val side = (least * density).toFloat()
        val inWindow = hasAnyAncestor(hasTestTag(screen.tag))
        val window = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            .firstOrNull { root -> under(root) { it.config.getOrNull(SemanticsProperties.TestTag) == screen.tag } }?.boundsInRoot
        check("$what: its window is there", window != null)
        if (window == null) return emptyList()

        // the targets
        val nodes = compose.onAllNodes(hasClickAction() and inWindow).fetchSemanticsNodes()
        val whole = nodes.filter { wholly(it) }
        check("$what: found targets wholly in view", whole.isNotEmpty(), "${nodes.size} targets, none wholly in view")
        val squares = mutableListOf<Pair<String, Rect>>()
        for (node in whole) {
            val label = describe(node)
            val touch = node.touchBoundsInRoot
            check("$what ($label): its touch area is at least ${least.toInt()} by ${least.toInt()} dp",
                touch.width >= side - 0.5f && touch.height >= side - 0.5f,
                "${f2(touch.width / density.toDouble())} x ${f2(touch.height / density.toDouble())} dp")
            val c = node.boundsInRoot.center
            val square = Rect(c.x - side / 2, c.y - side / 2, c.x + side / 2, c.y + side / 2)
            check("$what ($label): there is ${least.toInt()} by ${least.toInt()} dp of room for it",
                square.left >= window.left - 0.5f && square.top >= window.top - 0.5f &&
                    square.right <= window.right + 0.5f && square.bottom <= window.bottom + 0.5f,
                "node ${f2(node.size.width / density.toDouble())} x ${f2(node.size.height / density.toDouble())} dp")
            squares += label to square
        }
        for ((i, a) in squares.withIndex()) for (b in squares.drop(i + 1)) {
            val o = a.second.intersect(b.second)
            check("$what: ${a.first} and ${b.first} keep their own ${least.toInt()} dp", o.width <= 0.5f || o.height <= 0.5f,
                "${f2(o.width / density.toDouble())} x ${f2(o.height / density.toDouble())} dp shared")
        }

        // the words
        val scrolls = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and inWindow, useUnmergedTree = true)
            .fetchSemanticsNodes()
        check("$what: found words", texts.isNotEmpty())
        val ideaLines = compose.onAllNodes(hasTestTag(IdeasTags.IDEA_LINE) and inWindow, useUnmergedTree = true).fetchSemanticsNodes().map { it.id }.toSet()
        val scrolled = compose.onAllNodes(hasAnyAncestor(scrolls) and inWindow, useUnmergedTree = true).fetchSemanticsNodes().map { it.id }.toSet()
        for (node in texts) {
            val words = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: continue
            if (words.isEmpty()) continue
            val layout = layoutOf(node) ?: continue
            val anIdeaLine = generateSequence(node) { it.parent }.any { it.id in ideaLines }
            val designed = anIdeaLine && layout.layoutInput.overflow == TextOverflow.Ellipsis
            // the height says it: a line past maxLines, or lines past the box (the width, read through
            // semantics, is the line's room, not its box, so it isn't a sign)
            check("$what (\"$words\"): its words lay out whole", designed || !layout.didOverflowHeight, "${layout.lineCount} lines")
            if (node.id !in scrolled && generateSequence(node) { it.parent }.none { it.id in scrolled }) {
                check("$what (\"$words\"): its words are drawn whole in their box", wholly(node),
                    "drawn ${f2(node.boundsInRoot.height / density.toDouble())} of ${f2(node.size.height / density.toDouble())} dp")
            }
        }
        return whole.map { describe(it) }
    }

    /** Whether nothing cuts the node off: its bounds as drawn are its full size. */
    private fun wholly(node: SemanticsNode): Boolean {
        val b = node.boundsInRoot
        return b.width >= node.size.width - 1f && b.height >= node.size.height - 1f
    }

    private fun under(node: SemanticsNode, test: (SemanticsNode) -> Boolean): Boolean =
        test(node) || node.children.any { under(it, test) }

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
