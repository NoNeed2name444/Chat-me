package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaLens
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapLensTags
import com.cramdown.sorta.ui.ideas.MapStepsSeen
import com.cramdown.sorta.ui.ideas.MapTouchSeen
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Space's frost (S06a A8) and a lens over Space (S06d A2) on the emulator, by day and by night, as
 * iOS's DeckRenderTests keeps deck-5-frost-space and deck-6-lens-space: the deck's made-up ideas
 * (as DeckSeed and DeckIdeas), the clock 200 days after they were made, the gate opened 10 days
 * before (no frost), the boiler 40 (one step), the brushes 100 (two), the rest never (three). Kept in
 * /sdcard/Download/sorta-renders as space-frost-<light|dark>-<device>.png, five of the six frosted
 * ideas drawn with a halo or, far enough off, as a point, the shed alone cut by the depth band, and a
 * halo of each level drawn, then space-lens-<light|dark>-<device>.png, Alone on: the ideas in no link
 * drawn, the rest faded. None of the ideas is medical.
 */
@RunWith(AndroidJUnit4::class)
class SpaceFrostLensRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: MainActivity? = null

    @Before fun clearChoices() {
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        activity = null
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun frostAndLensByDay() = frostAndLens(dark = false)
    @Test fun frostAndLensByNight() = frostAndLens(dark = true)

    private fun frostAndLens(dark: Boolean) {
        val suffix = "${if (dark) "dark" else "light"}-$device"
        open(dark, suffix)

        // the frost: the six ideas not opened in 30 days, by level, the boiler (one step), the brushes
        // (two) and the four never opened (three). Each one drawn is a body with its halo, or, far enough
        // off, a point with none: by level, halos plus points are 1, 1 and 3. The fourth never opened, the
        // shed, is not drawn at all, and exactly it: at the orbit clock's 0 (animations off) it lies at
        // the far edge of the first view, past the depth band's end (the renderer's own band, its view
        // depth against the band's end), so frostGone is 0, 0 and 1, six in all. Cutting the view's far
        // strip is the design on both platforms (iOS GraphDepthFade.spaceHigh and spaceSmooth, Android
        // SpaceDepth.band), the owner's fix of 28 Oct (owner-log,
        // testDepthGlide). The boiler and the brushes with their halos, and at least one never opened, so
        // the still shows every step. Any other idea lost, a halo missing or another idea cut fails
        fun steps(d: MapDrawn?): Boolean {
            if (d == null) return false
            val halos = List(3) { d.frosted.getOrElse(it) { 0 } }
            val points = List(3) { d.frostPoints.getOrElse(it) { 0 } }
            val cut = d.frostCut.singleOrNull()
            return d.frosted.size == 3 && halos[0] == 1 && halos[1] == 1 && halos[2] >= 1 &&
                (0 until 3).all { halos[it] + points[it] == DRAWN_BY_LEVEL[it] } &&
                d.frostGone == CUT_BY_LEVEL &&
                halos.sum() + points.sum() + d.frostGone.sum() == FROSTED &&
                cut != null && cut.id.equals(SHED, ignoreCase = true) && cut.level == NEVER && cut.bandEnd > 0f && cut.depth >= cut.bandEnd
        }
        try { compose.waitUntil(20_000) { steps(drawn()) } } catch (_: ComposeTimeoutException) {}
        check(steps(drawn())) {
            "Space drew frost halos ${drawn()?.frosted} and frosted points ${drawn()?.frostPoints} by level, not $DRAWN_BY_LEVEL, " +
                "and cut ${drawn()?.frostGone} ${drawn()?.frostCut}, not $CUT_BY_LEVEL (the shed, past the band's end), " +
                "$FROSTED in all with a halo of each level, for $suffix: ${drawn()}"
        }
        settle(1_000)
        shot("space-frost-$suffix")

        // a lens over Space: Alone on, its chip checked, the linked ideas faded
        val alone = MapLensTags.chip(IdeaLens.ALONE)
        waitFor(alone, "the Alone chip over Space, $suffix")
        target(node(alone), "Alone, $suffix")
        tapOnScreen(node(alone), "the Alone chip")
        try { compose.waitUntil(10_000) { node(alone).config.getOrNull(SemanticsProperties.Selected) == true } } catch (_: ComposeTimeoutException) {}
        check(node(alone).config.getOrNull(SemanticsProperties.Selected) == true) { "Alone is not checked after a tap for $suffix" }
        try { compose.waitUntil(20_000) { drawn()?.let { it.filtered && it.hidden > 0 && it.shown > 0 } == true } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.let { it.filtered && it.hidden > 0 && it.shown > 0 } == true) {
            "Space under Alone drew no ideas left out and some kept for $suffix: ${drawn()}"
        }
        settle(1_000)
        shot("space-lens-$suffix")
        tapOnScreen(node(alone), "the Alone chip, again")
        try { compose.waitUntil(20_000) { drawn()?.filtered == false } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.filtered == false) { "Alone off left Space filtered for $suffix: ${drawn()}" }
    }

    /** Sorta on the deck's ideas at [LATER], Space's first-run cards put away, its map open on Space and drawn. */
    private fun open(dark: Boolean, name: String) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000)
        val context = instrumentation.targetContext
        check(context.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString(MapTouchSeen.KEY, GraphTheme.SPACE.rawValue).putBoolean(MapStepsSeen.SPACE_KEY, true)
            .putLong(IdeasSettings.LAST_TIDE_KEY, LATER - DAY).commit()) { "cards seen, $name" }
        val dir = File(context.cacheDir, "frost-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(ideas, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.STORE_DIR, dir.path).putExtra(MainActivity.NOW, LATER))
        checkNotNull(scenario).onActivity { activity = it }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        if (shown(SortaMode.MAP.identifier)) tapOnScreen(node(SortaMode.MAP.identifier), "Map mode's switch")
        try { compose.waitUntil(90_000) { drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true) { "Space was not drawn within 90 s for $name: ${drawn()}" }
        settle(1_500)
    }

    // on the test thread: compose's finders wait for idle, which they refuse to do on the main thread
    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, what: String) {
        try { compose.waitUntil(15_000) { shown(tag) } } catch (_: ComposeTimeoutException) {}
        check(shown(tag)) { "Not on screen: $what" }
    }

    private fun node(tag: String): SemanticsNode = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().first()

    /** Its touch area at least the least target, 48 dp, across and tall. */
    private fun target(node: SemanticsNode, what: String) {
        val least = 48 * instrumentation.targetContext.resources.displayMetrics.density - 0.5f
        val touch = node.touchBoundsInRoot
        check(touch.width >= least && touch.height >= least) { "$what touches ${touch.width} x ${touch.height} px, under 48 dp" }
    }

    private fun tapOnScreen(node: SemanticsNode, what: String) {
        val middle = node.positionOnScreen + Offset(node.size.width / 2f, node.size.height / 2f)
        check(ui.click(middle.x.toInt(), middle.y.toInt())) { "$what could not be tapped" }
    }

    private fun settle(ms: Long) {
        try { compose.waitUntil(ms) { false } } catch (_: ComposeTimeoutException) {}
    }

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return checkNotNull(out).getOrThrow()
    }

    private fun drawn(): MapDrawn? = onMain {
        var found: MapDrawn? = null
        fun nodes(n: SemanticsNode) {
            if (found == null) n.config.getOrNull(MapDrawnKey)?.let { found = it }
            n.children.forEach(::nodes)
        }
        fun views(v: View) {
            if (v is RootForTest) nodes(v.semanticsOwner.unmergedRootSemanticsNode)
            if (v is ViewGroup) for (k in 0 until v.childCount) views(v.getChildAt(k))
        }
        activity?.window?.decorView?.let(::views)
        found
    }

    private fun framed(drawn: MapDrawn) = drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    private fun shot(name: String) {
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        /** When the ideas were made (26 Oct 2026), as IdeasFixture's; the clock is 201 days on, as DeckIdeas.LATER. */
        const val T = 1_793_000_000_000L
        const val LATER = T + 201 * DAY
        /** Never opened, so frosted three steps: the shed, the compost, the tulips and the bread. */
        const val NEVER_OPENED = 4
        /** The frost level of an idea never opened. */
        const val NEVER = 3
        /** Not opened in 30 days: the boiler, the brushes and the four never opened. */
        const val FROSTED = 6
        /**
         * Drawn in the first view, a halo or a point, by level: the boiler (one step), the brushes (two)
         * and three of the four never opened (three); the shed alone cut by the depth band, at three.
         */
        val DRAWN_BY_LEVEL = listOf(1, 1, NEVER_OPENED - 1)
        val CUT_BY_LEVEL = listOf(0, 0, 1)
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')
        /** The shed's id: the one idea the first view's depth band cuts. */
        val SHED = id(14)

        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
        val bulbs = NoteFolder(id = id(203), name = "Bulbs", parentId = garden.id, createdAt = T + 2)
        val folders = listOf(garden, kitchen, bulbs)

        val ideas = listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = T + 9 * HOUR, openedAt = LATER - 10 * DAY),
            Note(id = id(12), title = "Ask the landlord when the boiler service is booked, and whether the hall light can be fixed the same day",
                createdAt = T + 8 * HOUR, openedAt = LATER - 40 * DAY),
            Note(id = id(13), title = "Buy brushes", createdAt = T + 7 * HOUR, openedAt = LATER - 100 * DAY),
            Note(id = id(14), title = "Paint the shed green", body = "Two coats, after the rain. Check [[Fix the gate latch]] first.",
                folderId = garden.id, links = listOf(id(13)), createdAt = T + 6 * HOUR),
            Note(id = id(15), title = "Turn the compost", folderId = garden.id, createdAt = T + 5 * HOUR),
            Note(id = id(16), title = "Plant tulips before the frost", body = "Near [[Paint the shed green]].", folderId = bulbs.id,
                createdAt = T + 4 * HOUR),
            Note(id = id(17), title = "Try the seeded bread", folderId = kitchen.id, createdAt = T + 3 * HOUR),
        )
    }
}
