package com.cramdown.sorta

import android.animation.ValueAnimator
import android.content.Intent
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.GraphLineStyle
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.MapNameShow
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapPanelTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTapKey
import com.cramdown.sorta.ui.ward.WardSpace
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Map mode's Space on the emulator (S05b A6), by day and by night: Sorta itself (MainActivity) on a
 * store of made-up ideas, taken to Map mode by its switch on a phone (a tablet shows the map beside
 * Sort, with no switch), the look a new user's (Space), one still each, named
 * mapmode-space-<light|dark>-<device>.png in /sdcard/Download/sorta-renders. The 3D look is drawn
 * only on a real GL surface, which Robolectric has not, so these are the emulator's. Each run checks
 * the look chip names Space and that the map drew, as MapRenderTest's requireMapDrawn does; the
 * stills are taken as MapRenderTest's are.
 *
 * S05d: each run is a first run, so the first-run card shows. Every body the map drew is above the
 * card's top, Got it leaves the camera where it was, and Neurons, chosen after it, shows its own
 * card with every body above it too: a second still each, mapmode-neurons-<light|dark>-<device>.png.
 *
 * S05e: before Neurons, Space's first view draws its links as curved strips of 40 samples; Lines set
 * to Straight in the Look menu, with the map open, draws the same scene's links straight: a still
 * each, mapmode-straight-<light|dark>-<device>.png (mapmode-space is the Curved one). Curved is then
 * set again.
 *
 * S05f: the bodies' names, as the map reports them (MapDrawn.names). At rest none shows, in Space or
 * Neurons. In Space a finger held on a body shows its name in one pill above it, gone once the
 * finger moves on; in both, a tap chooses a body and its name stays while its card is up: a still
 * each, mapmode-named-<space|neurons>-<light|dark>-<device>.png.
 *
 * S05g: the first run's card shows Space's steps (mapmode-card-steps-<light|dark>-<device>.png); in
 * Space and Neurons the body requireNames chose is marked and Show links fades the rest, and in
 * Performance, chosen after Neurons, a point tapped at the map's spot is marked and Show links brings
 * out its own links, fading no point (stills mapmode-chosen-<look>-... and mapmode-links-<look>-...);
 * in each a hold opens the options with the finger down, and a double tap on empty space brings back
 * the first view, Recentre's (requireChoice). Each legend, scrolled to its end, ends its touch help
 * the sheet's foot above its bottom, which is above the navigation bar (requireLegendFoot, a still
 * mapmode-legend-foot-<theme>-...).
 */
@RunWith(AndroidJUnit4::class)
class MapModeSpaceRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clearChoices() {
        // Ideas' remembered choices start clear, so the map opens on Space as a new user's would
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun spaceByDay() = render(dark = false)
    @Test fun spaceByNight() = render(dark = true)

    private fun render(dark: Boolean) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "mapmode-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        // a phone's switch; a tablet's map is beside Sort already
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.CHIP)).assert(hasContentDescription(context.getString(R.string.look_chip_spoken, context.getString(R.string.look_space))))
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && mapDrawn()?.box?.size == 4 } } catch (_: ComposeTimeoutException) {}
        val name = "mapmode-space-${if (dark) "dark" else "light"}-$device"
        shot(name) // kept even when the check below fails, to see why
        requireMapDrawn(name)
        // S05d: at first run the card shows in its one form, every body of the first view is drawn
        // above it, and Got it puts it away without moving the camera
        requireClearOfTheCard(name)
        // S05g A3: a first run's card shows Space's steps too, every body still above it
        check(shown(MapPanelTags.HINT_STEPS)) { "The first-run card shows no steps for $name" }
        shot("mapmode-card-steps-${if (dark) "dark" else "light"}-$device")
        val pose = checkNotNull(mapDrawn()).pose
        compose.onNode(hasText(context.getString(R.string.map_touch_got_it)) and hasAnyAncestor(hasTestTag(MapPanelTags.HINT))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNodes().isEmpty() }
        SystemClock.sleep(1_500) // a glide or a jump would have shown by now
        compose.waitForIdle()
        val after = checkNotNull(mapDrawn()).pose
        check(pose.size == 6 && after.size == 6 && pose.indices.all { abs(pose[it] - after[it]) <= 1e-4f * max(1f, abs(pose[it])) }) {
            "Got it moved the camera for $name: $pose, then $after"
        }
        // S05e: Space's first view draws its links, each a strip of 40 samples, curved
        try { compose.waitUntil(30_000) { (mapDrawn()?.links ?: 0) > 0 } } catch (_: ComposeTimeoutException) {}
        val curved = checkNotNull(mapDrawn())
        check(curved.links > 0 && curved.samples == 40 && !curved.straight) { "Space's first view drew no curved links of 40 samples for $name: $curved" }
        // Lines: Straight, set while the map is open: the same scene, its links drawn straight
        chooseLines(GraphLineStyle.STRAIGHT)
        try { compose.waitUntil(30_000) { mapDrawn()?.straight == true } } catch (_: ComposeTimeoutException) {}
        val straight = checkNotNull(mapDrawn())
        check(straight.straight && straight.build == curved.build && straight.links > 0 && straight.samples == 40) {
            "Lines: Straight did not reach Space's scene for $name: before $curved, then $straight"
        }
        shot("mapmode-straight-${if (dark) "dark" else "light"}-$device")
        // and Curved again, so Neurons is reached as before
        chooseLines(GraphLineStyle.CURVED)
        try { compose.waitUntil(30_000) { mapDrawn()?.straight == false } } catch (_: ComposeTimeoutException) {}
        check(mapDrawn()?.straight == false) { "Lines: Curved did not reach Space's scene for $name: ${mapDrawn()}" }
        // S05f A6: Space's legend, kept for the renders check
        shotLegend(GraphTheme.SPACE, dark)
        // S05f: Space's names, at rest, under a finger and for a chosen body
        val (spaceChosen, spaceBefore) = requireNames("space", dark, press = true)
        // S05g A3: the chosen body's mark, Show links, the hold and the empty double tap
        requireChoice("space", dark, spaceChosen, spaceBefore, home = null)
        // Neurons, chosen after Space's Got it: the card is kept per look, so it shows there again
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapLook.NEURON.identifier)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.NEURON.identifier)).performClick()
        compose.waitUntil(30_000) {
            mapNode()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(GraphTheme.NEURONS.mapLabel) } == true
        }
        // the words name the look at once, while its scene is still built off the main thread: the
        // scene is Neuron's once its camera is, which looks down at its own angle
        compose.waitUntil(30_000) { mapDrawn()?.pose?.getOrNull(5)?.let { abs(it - pose[5]) > 1e-3f } == true }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && mapDrawn()?.box?.size == 4 } } catch (_: ComposeTimeoutException) {}
        val neurons = "mapmode-neurons-${if (dark) "dark" else "light"}-$device"
        shot(neurons)
        requireMapDrawn(neurons)
        requireClearOfTheCard(neurons)
        val neuronsHome = checkNotNull(mapDrawn()).pose
        // S05f A6: Neuron's legend, kept for the renders check
        shotLegend(GraphTheme.NEURONS, dark)
        // S05f: Neuron's names, at rest and for a chosen body
        val (neuronsChosen, neuronsBefore) = requireNames("neurons", dark, press = false)
        requireChoice("neurons", dark, neuronsChosen, neuronsBefore, home = neuronsHome)
        // S05g A3: Performance, chosen after Neurons, shows no first-run card; a tap on a point
        // chooses it, and the same marks, Show links, hold and double tap as the other looks
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapLook.PERFORMANCE.identifier)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.PERFORMANCE.identifier)).performClick()
        compose.waitUntil(30_000) {
            mapNode()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(GraphTheme.PERFORMANCE.mapLabel) } == true
        }
        // its scene is built off the main thread too: it is Performance's once the camera looks down at Space's angle again
        compose.waitUntil(30_000) { mapDrawn()?.pose?.getOrNull(5)?.let { abs(it - pose[5]) <= 1e-3f } == true }
        // Performance draws points, no Space bodies, so its frames count without them
        try { compose.waitUntil(60_000) { mapDrawn()?.let { it.frames >= MapDrawn.ENOUGH && it.box.size == 4 } == true } } catch (_: ComposeTimeoutException) {}
        val performance = "mapmode-chosen-performance-${if (dark) "dark" else "light"}-$device"
        val drawnPerformance = mapDrawn()
        check(drawnPerformance != null && drawnPerformance.frames >= MapDrawn.ENOUGH && drawnPerformance.box.size == 4) {
            "Map mode's Performance has not finished ${MapDrawn.ENOUGH} frames with its points (waited up to a minute) for $performance: $drawnPerformance"
        }
        check(!shown(MapPanelTags.HINT)) { "Performance shows a first-run card for $performance" }
        val performanceHome = drawnPerformance.pose
        val (performanceChosen, at) = spot(performance)
        compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(at) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK)) { "A tap on a point brought up no card for $performance" }
        requireChoice("performance", dark, performanceChosen, performanceHome, home = performanceHome)
    }

    /**
     * S05f A3: no name at rest; with [press], a finger held on a body (the map's spot, the biggest
     * crisp body clear of the chrome) shows its name, one pill above it, gone once the finger moves
     * past the slop, a turn and not a tap, so no card; then a tap on a body chooses it, its card comes
     * up and its name stays, one pill above it, still there a second later.
     */
    private fun requireNames(look: String, dark: Boolean, press: Boolean): Pair<Int, List<Float>> {
        val name = "mapmode-named-$look-${if (dark) "dark" else "light"}-$device"
        compose.waitForIdle()
        check(mapDrawn()?.names?.isEmpty() == true) { "A name shows at rest for $name: ${mapDrawn()?.names}" }
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        if (press) {
            val (index, at) = spot(name)
            compose.onNode(map).performTouchInput { down(at) }
            try { compose.waitUntil(5_000) { mapDrawn()?.names?.isNotEmpty() == true } } catch (_: ComposeTimeoutException) {}
            requireOnePill(index, "a finger held on a body for $name")
            val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop
            compose.onNode(map).performTouchInput { moveBy(Offset(slop * 3f, 0f)) }
            try { compose.waitUntil(5_000) { mapDrawn()?.names?.isEmpty() == true } } catch (_: ComposeTimeoutException) {}
            check(mapDrawn()?.names?.isEmpty() == true) { "The name stayed after the finger moved on for $name: ${mapDrawn()?.names}" }
            compose.onNode(map).performTouchInput { up() }
            compose.waitForIdle()
            check(!shown(MapPanelTags.PEEK)) { "A turn brought up a card for $name" }
            SystemClock.sleep(500) // the turn's last frame, so the spot is where the body now is
        }
        val (index, at) = spot(name)
        val before = checkNotNull(mapDrawn()).pose
        compose.onNode(map).performTouchInput { click(at) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.names?.isNotEmpty() == true } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK)) { "A tap on a body brought up no card for $name" }
        requireOnePill(index, "a chosen body, its card up, for $name")
        shot(name)
        SystemClock.sleep(1_000)
        requireOnePill(index, "a chosen body a second later, its card still up, for $name")
        return index to before
    }

    /**
     * S05g A3, after requireNames chose [index] with a tap, the camera then at [before]: the body is
     * marked (Space's orbit ring, the Neurons' hot halo) and nothing fades; the camera has eased
     * towards it while animations run, and stayed put with them off; a still,
     * mapmode-chosen-<look>-<light|dark>-<device>.png. Show links fades what is outside its
     * neighbourhood, keeps its lit links and reads All links: a still, mapmode-links-<look>-....
     * All links brings everything back. A finger held still past 0.55 s opens its options with the
     * finger still down; one held and moved before then turns the camera and opens nothing. A tap on
     * empty space lets it go, and the second of a double tap brings back the first view: the one
     * Recentre frames, and [home] where it is known (the first-run card still up, as in Neurons;
     * after Space's Got it the first view is framed without the card, S05d item 2, which no pose
     * taken before it shows).
     */
    private fun requireChoice(look: String, dark: Boolean, index: Int, before: List<Float>, home: List<Float>?) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-chosen-$look-$mode-$device"
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val res = instrumentation.targetContext.resources
        fun same(a: List<Float>, b: List<Float>) = a.size == 6 && b.size == 6 && a.indices.all { abs(a[it] - b[it]) <= 1e-3f * max(1f, abs(a[it])) }
        try { compose.waitUntil(10_000) { mapDrawn()?.let { it.chosen == index && it.marked } == true } } catch (_: ComposeTimeoutException) {}
        val chosen = checkNotNull(mapDrawn())
        check(chosen.chosen == index && chosen.marked) { "The chosen body ($index) is not marked for $name: $chosen" }
        check(!chosen.focus && chosen.faded == 0) { "Something fades with no Show links for $name: $chosen" }
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        val eased = checkNotNull(mapDrawn()).pose
        val lively = ValueAnimator.areAnimatorsEnabled()
        check(same(before, eased) != lively) {
            "The camera ${if (lively) "did not ease towards" else "moved with animations off for"} the chosen body for $name: $before, then $eased"
        }
        shot(name)
        // Show links
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).assert(hasText(res.getString(R.string.map_peek_show_links))).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.focus == true } } catch (_: ComposeTimeoutException) {}
        val focused = checkNotNull(mapDrawn())
        if (look == "performance") {
            // Performance steps the other links back and fades no point (GraphPerfShaders): its own links drawn, nothing counted faded
            val id = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)).ids[index]
            val linked = notes.any { n -> (n.id == id && n.links.isNotEmpty()) || id in n.links }
            check(focused.focus && focused.chosen == index && focused.faded == 0 && (!linked || focused.focusLinks > 0)) {
                "Show links did not bring out the chosen point's own links (linked: $linked) or faded a point for $name: $focused"
            }
        } else check(focused.focus && focused.chosen == index && focused.faded > 0) { "Show links faded nothing for $name: $focused" }
        check(focused.lit == chosen.lit && focused.focusLinks >= focused.lit) { "Show links lost the chosen body's lit links for $name: $chosen, then $focused" }
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).assert(hasText(res.getString(R.string.map_peek_all_links)))
        shot("mapmode-links-$look-$mode-$device")
        // All links
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.focus == false } } catch (_: ComposeTimeoutException) {}
        val back = checkNotNull(mapDrawn())
        check(!back.focus && back.faded == 0 && back.focusLinks == 0 && back.chosen == index) { "All links did not bring everything back for $name: $back" }
        // a finger held still past the hold: the options, the finger still down
        val (_, at) = spot(name)
        compose.onNode(map).performTouchInput { down(at) }
        try { compose.waitUntil(5_000) { shown(MapPanelTags.MENU) } } catch (_: ComposeTimeoutException) {}
        val opened = shown(MapPanelTags.MENU)
        compose.onNode(map).performTouchInput { up() }
        check(opened) { "A finger held still on a body opened no options for $name" }
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(MapPanelTags.MENU) }
        // held, then moved before the options: the camera turns, nothing opens
        val (_, from) = spot(name)
        val still = checkNotNull(mapDrawn()).pose
        val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop
        compose.onNode(map).performTouchInput { down(from) }
        SystemClock.sleep(300) // past the pick-up time, short of the options'
        compose.onNode(map).performTouchInput { moveBy(Offset(slop * 4f, 0f)); moveBy(Offset(slop * 4f, 0f)); up() }
        compose.waitForIdle()
        SystemClock.sleep(500)
        check(!shown(MapPanelTags.MENU)) { "A hold that moved opened the options for $name" }
        check(!same(still, checkNotNull(mapDrawn()).pose)) { "A hold that moved did not turn the camera for $name" }
        // empty space: a tap lets it go, two bring back the first view, the one Recentre frames: the
        // first-run card's cover while it is up, the view without it once it is put away (S05d item 2)
        val turned = checkNotNull(mapDrawn()).pose
        val empty = emptySpot(name)
        compose.onNode(map).performTouchInput { doubleClick(empty) }
        try { compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) && !same(turned, checkNotNull(mapDrawn()).pose) } } catch (_: ComposeTimeoutException) {}
        check(!shown(MapPanelTags.PEEK)) { "A tap on empty space left the card up for $name" }
        SystemClock.sleep(500)
        compose.waitForIdle()
        val doubled = checkNotNull(mapDrawn()).pose
        check(!same(turned, doubled)) { "A double tap on empty space left the turned camera where it was for $name: $turned" }
        if (home != null) check(same(home, doubled)) { "A double tap on empty space did not bring back the first view for $name: $home, now $doubled" }
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitForIdle()
        SystemClock.sleep(500)
        val recentred = checkNotNull(mapDrawn()).pose
        check(same(doubled, recentred)) { "A double tap on empty space did not bring back Recentre's first view for $name: $doubled, Recentre $recentred" }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == -1 } } catch (_: ComposeTimeoutException) {}
        check(mapDrawn()?.chosen == -1) { "The body is still marked after the empty tap for $name: ${mapDrawn()}" }
    }

    /** A point of the map clear of every drawn body and of the chrome: the widest margin beside the bodies' box, in the map's pixels. */
    private fun emptySpot(name: String): Offset {
        val drawn = checkNotNull(mapDrawn())
        check(drawn.box.size == 4) { "No bodies' box to keep clear of for $name: $drawn" }
        val map = checkNotNull(mapNode()).boundsInRoot
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val gap = 56f * density
        val middle = (drawn.box[1] + drawn.box[3]) / 2
        val stripBottom = strip.bottom - map.top
        val choices = listOf(
            Offset(drawn.box[0] / 2, middle) to drawn.box[0],
            Offset((drawn.box[2] + map.width) / 2, middle) to (map.width - drawn.box[2]),
            Offset(map.width / 2, (stripBottom + drawn.box[1]) / 2) to (drawn.box[1] - stripBottom),
        )
        val (point, room) = choices.maxBy { it.second }
        check(room >= gap) { "No empty space ${gap / density} dp wide beside the bodies for $name: $drawn" }
        return point
    }

    /**
     * The theme's legend, opened from its Look tool as a person would, kept as
     * mapmode-legend-<theme>-<light|dark>-<device>.png (S05f A6), then put away.
     */
    private fun shotLegend(theme: GraphTheme, dark: Boolean) {
        val look = hasContentDescription("Look") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, theme.title)
        compose.onNode(look).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(theme.legendTitle) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(theme.legendTitle) and hasClickAction()).performClick()
        val sheet = MapPanelTags.legend(theme)
        compose.waitUntil(10_000) { shown(sheet) }
        SystemClock.sleep(1_000) // the sheet comes to rest
        shot("mapmode-legend-${theme.rawValue}-${if (dark) "dark" else "light"}-$device")
        requireLegendFoot(theme, dark)
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(sheet) }
        compose.waitForIdle()
    }

    /**
     * S05g A2: the legend opened full and scrolled to its end, its last line (the touch help) ends
     * the sheet's foot (xl) above the sheet's bottom, and that bottom is above the navigation bar's
     * top: on a phone the bar, on a tablet the sheet's foot over the taskbar. The sheet keeps its
     * content above the bar itself (Material3 pads it by safeDrawing's bottom), so the legend adds
     * only the foot. A still, mapmode-legend-foot-<theme>-<light|dark>-<device>.png.
     */
    private fun requireLegendFoot(theme: GraphTheme, dark: Boolean) {
        val name = "mapmode-legend-foot-${theme.rawValue}-${if (dark) "dark" else "light"}-$device"
        val legend = compose.onNode(hasTestTag(MapPanelTags.legend(theme)))
        // a phone's sheet opens at half height: a swipe up takes it full, then the column to its end
        legend.performTouchInput { swipeUp() }
        SystemClock.sleep(800)
        if (SemanticsActions.ScrollBy in legend.fetchSemanticsNode().config) legend.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        SystemClock.sleep(800)
        val touch = compose.onNode(hasTestTag(MapPanelTags.LEGEND_TOUCH)).fetchSemanticsNode()
        val sheet = legend.fetchSemanticsNode()
        val lastLine = touch.positionOnScreen.y + touch.size.height
        val foot = sheet.positionOnScreen.y + sheet.size.height
        var barTop = Float.NaN
        var bar = 0
        checkNotNull(scenario).onActivity { activity ->
            val decor = activity.window.decorView
            val at = IntArray(2).also { decor.getLocationOnScreen(it) }
            bar = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            barTop = (at[1] + decor.height - bar).toFloat()
        }
        val xl = WardSpace.xl.toFloat() * instrumentation.targetContext.resources.displayMetrics.density
        android.util.Log.i("MapModeSpaceRenderTest", "$name: the last line ends at $lastLine, the sheet's content at $foot, the navigation bar ($bar px) starts at $barTop")
        shot(name)
        check(lastLine + xl <= foot + 1f) { "The legend's last line does not end the foot ($xl px) above the sheet's bottom for $name: it ends at $lastLine, the sheet at $foot" }
        check(foot <= barTop + 1f) { "The legend's sheet runs under the navigation bar for $name: its content ends at $foot, the bar starts at $barTop" }
    }

    /** The map's spot: a body's index and its centre, in the map's own pixels. */
    private fun spot(name: String): Pair<Int, Offset> {
        try { compose.waitUntil(10_000) { mapDrawn()?.spot?.size == 3 } } catch (_: ComposeTimeoutException) {}
        val spot = checkNotNull(mapDrawn()).spot
        check(spot.size == 3) { "No body clear of the chrome to put a finger on for $name: ${mapDrawn()}" }
        return spot[0].toInt() to Offset(spot[1], spot[2])
    }

    /** One name on show, [index]'s, its pill above its body and centred on it. */
    private fun requireOnePill(index: Int, what: String) {
        val names = checkNotNull(mapDrawn()).names
        check(names.size == 1 && names[0].index == index) { "Not one name, the body's ($index), for $what: $names" }
        val pill = names[0]
        val middle = (pill.box[0] + pill.box[2]) / 2
        // on the map whole, its rim inside the side margin; centred on its body unless that margin
        // moved it in (MapNameShow.inside: a name near the side ran off the phone's screen)
        val width = checkNotNull(mapDrawn()).width.toFloat()
        val margin = MapNameShow.EDGE_DP * instrumentation.targetContext.resources.displayMetrics.density
        check(pill.box[0] >= 0f && pill.box[2] <= width) { "The name runs off the map (0 to $width) for $what: $pill" }
        val movedIn = abs(pill.box[0] - margin) <= 1f || abs(pill.box[2] - (width - margin)) <= 1f
        check(pill.box[3] < pill.body[1] && (abs(middle - pill.body[0]) <= 2f || movedIn)) { "The name is not above its body for $what: $pill" }
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /**
     * The first-run card shows, and every body the map drew is above its top: the drawn bodies' box
     * (in the map's own pixels, from its top left) lies over the card's top edge (S05d A3), and
     * below the top strip's chips.
     */
    private fun requireClearOfTheCard(name: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNodes().isNotEmpty() }
        } catch (_: ComposeTimeoutException) { error("The first-run card is not showing for $name") }
        val card = compose.onNode(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNode().boundsInRoot
        val drawn = checkNotNull(mapDrawn())
        check(drawn.box.size == 4) { "The map reports no drawn bodies' box for $name: $drawn" }
        val map = checkNotNull(mapNode()).boundsInRoot
        val bottom = map.top + drawn.box[3]
        android.util.Log.i("MapModeSpaceRenderTest", "$name: the bodies reach $bottom, the card's top is ${card.top}")
        check(bottom <= card.top) { "A body is drawn under the first-run card for $name: the bodies reach $bottom, the card's top is ${card.top}; $drawn" }
        // and every body starts below the top strip's chips, as iOS's assertBelowStrip checks
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        val top = map.top + drawn.box[1]
        android.util.Log.i("MapModeSpaceRenderTest", "$name: the bodies start at $top, the strip's bottom is ${strip.bottom}")
        check(top >= strip.bottom) { "A body is drawn under the top strip's chips for $name: the bodies start at $top, the strip's bottom is ${strip.bottom}; $drawn" }
    }

    /**
     * Lines set to [style] in Space's Look menu (its Look tool, which names the theme), as a person
     * would while the map is open (S05e A3); then the menu is put away.
     */
    private fun chooseLines(style: GraphLineStyle) {
        // IdeaToolButton's label and value, the Space map's own Look tool (the board's names its lines)
        val look = hasContentDescription("Look") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, GraphTheme.SPACE.title)
        compose.onNode(look).performClick()
        // IdeaLinesPicker's segment words; the segment itself merges them
        val segment = hasTestTag("lookLines-" + style.rawValue)
        compose.waitUntil(10_000) { compose.onAllNodes(segment, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(segment, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        ui.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodes(segment, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun mapNode(): SemanticsNode? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()

    private fun mapDrawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun framed(drawn: MapDrawn?) = drawn != null && drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    /** The GL surface drew Space with its bodies, and the middle of the map holds bright, coloured points (MapRenderTest's check). */
    private fun requireMapDrawn(name: String) {
        val drawn = mapDrawn()
        check(framed(drawn)) { "Map mode's Space has not finished ${MapDrawn.ENOUGH} frames with its bodies (waited up to a minute) for $name: $drawn" }
        val bounds = checkNotNull(mapNode()).boundsInRoot
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot for $name" }
        val left = (bounds.left + bounds.width * 0.08f).toInt().coerceIn(0, screen.width - 1)
        val right = (bounds.right - bounds.width * 0.08f).toInt().coerceIn(left + 1, screen.width)
        val top = (bounds.top + bounds.height * 0.3f).toInt().coerceIn(0, screen.height - 1)
        val bottom = (bounds.bottom - bounds.height * 0.2f).toInt().coerceIn(top + 1, screen.height)
        var coloured = 0; var samples = 0
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val c = screen.getPixel(x, y)
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            if (r + g + b > 220 && maxOf(r, g, b) - minOf(r, g, b) > 45) coloured++
            samples++
        }
        val seen = "$coloured coloured of $samples samples in x $left-$right, y $top-$bottom of the ${screen.width}x${screen.height} screen; the map reports $drawn"
        android.util.Log.i("MapModeSpaceRenderTest", "$name: $seen")
        check(coloured > 25) { "Map mode's Space drew nothing visible ($seen) for $name" }
    }

    /** One still of the screen as [name].png, as MapRenderTest takes them. */
    private fun shot(name: String) {
        compose.waitForIdle()
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.waitForIdle()
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        SystemClock.sleep(600) // the compositor presents the settled frame
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical: ideas in To sort and in two folders, some linked, so Space has bodies of each kind
        val garden = NoteFolder(id = id(201), name = "Garden", createdAt = T)
        val kitchen = NoteFolder(id = id(202), name = "Kitchen", createdAt = T + 1)
        val folders = listOf(garden, kitchen)
        val notes = listOf(
            Note(id = id(11), title = "Fix the gate latch", createdAt = T + 9 * 3_600_000L),
            Note(id = id(12), title = "Buy brushes", createdAt = T + 8 * 3_600_000L),
            Note(id = id(13), title = "Paint the shed green", body = "Check [[Fix the gate latch]] first.", folderId = garden.id,
                links = listOf(id(12)), createdAt = T + 7 * 3_600_000L),
            Note(id = id(14), title = "Turn the compost", folderId = garden.id, links = listOf(id(13)), createdAt = T + 6 * 3_600_000L),
            Note(id = id(15), title = "Plant tulips before the frost", folderId = garden.id, links = listOf(id(13), id(14)), createdAt = T + 5 * 3_600_000L),
            Note(id = id(16), title = "Try the seeded bread", folderId = kitchen.id, createdAt = T + 4 * 3_600_000L),
            Note(id = id(17), title = "Order more coffee beans", folderId = kitchen.id, links = listOf(id(16)), createdAt = T + 3 * 3_600_000L),
            Note(id = id(18), title = "Plan the weekend walk", createdAt = T + 2 * 3_600_000L),
        )
    }
}
