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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
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
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapLegend
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.MapFirstView
import com.cramdown.sorta.map.MapNameShow
import com.cramdown.sorta.map.MapTouchRules
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.PerfMap
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.FOLDER_CHOOSER_TAG
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.MapPanelTags
import com.cramdown.sorta.ui.ideas.MapTags
import com.cramdown.sorta.map.MapFilter
import com.cramdown.sorta.map.NeuronState
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.ideas.perfInput
import com.cramdown.sorta.ui.ideas.universeInput
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnCell
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapScene
import com.cramdown.sorta.ui.map.MapTapKey
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WardToken
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
 * the first view, Recentre's (requireChoice). Each look's legend, scrolled to its end, ends its touch help
 * the sheet's foot above its bottom, which is above the navigation bar (requireLegendFoot, a still
 * mapmode-legend-foot-<theme>-...).
 *
 * S05g A6: in Space, with Look's Suns, the idea with the most links, a sun by the map's own styles, is
 * chosen with a tap, marked and its links lit (mapmode-chosen-sun-space-...); in Neurons the Garden
 * cell, whose idea bridges out of its folder, is chosen with its bridges lit
 * (mapmode-chosen-bridges-neurons-...), and that idea, flown into, has Show links
 * (mapmode-links-bridges-neurons-...).
 *
 * S05g2 A3: hold to move. In Space and Neurons a finger held 300 ms on the chosen body picks it up,
 * the camera still, and moved 8 slops in steps the body follows it, its offset kept; let go, no
 * options open, the card and the choice stay, and every body is home again within the frames iOS's spring
 * needs (MapDrawn.restFrames, so a slow emulator is judged by its frames, not the clock; requireDrag;
 * stills mapmode-drag-<space|neurons>-<light|dark>-<device>.png). Under Look's Suns the chosen sun
 * pulls its neighbours (mapmode-drag-sun-space-...), and in Neurons the open cell carries the idea
 * chosen inside it, once, not 1.85 times (mapmode-drag-cell-neurons-...). In every look a move made
 * with its down, before the pick-up, turns the camera and moves no body; in Performance a hold that
 * moves still turns it. requireNames' press: a quick move takes the name away, a held one keeps it
 * on the body it carries.
 *
 * S05h A3: Space's default look is iOS's Universe, so every check above from the first view to
 * requireSun's Suns runs on it. requireUniverse then checks the Universe itself: its bodies by role as
 * UniversePlan gives them for the fixture, no moon link drawn and arched and far links both drawn
 * (mapmode-universe-...); Fly in on the Garden black hole, its system inside the window
 * (mapmode-fly-space-...); still flown in, the black hole moved, carrying at least its planets, a planet
 * chosen inside it within 10 % of its move (mapmode-drag-star-space-...); and Recentre back to the
 * first view. Space's legend,
 * opened at its top, shows the ladder, and its Moon row is there (mapmode-legend-top-space-...).
 *
 * S05h2 A3: after requireUniverse, with animations off, a fling throws no sparks and the most linked
 * idea, deleted from its options, dies once with its links and is gone within 2 s; Undo brings it
 * back (requireStillDeath, no still).
 *
 * S05i A3 and A6: the Filter tool in each theme (requireFilter), with a still of its menu and of the
 * map under Pages and under Garden alone (mapmode-filtered-<pages|garden>-<theme>-...); in Neurons,
 * its first-run card put away first, the cell states (requireCells): the Look menu with its Cells
 * group (mapmode-cells-menu-neurons-...), the Cell by cell sheet (mapmode-cells-sheet-neurons-...),
 * Cell by cell's Engulfing over the map (mapmode-cells-neurons-...), and a close-up of one of the open
 * Garden cell's ideas and its halo under each of the six states, chosen in Look › Cells
 * (mapmode-cells-<state>-neurons-...), each told from resting's by numbers and by pixels.
 *
 * S05i2 A3: Performance on the sample ideas (MainActivity's map preview), its layout settled: a hub
 * for each of the 5 folders and 3 folder lines (mapmode-hubs-performance-...), its legend's ladder
 * whole (mapmode-legend-ladder-performance-...), a top folder's filter showing its hubs, and a hub's
 * tap raising its folder's card, whose Fly in frames its ball (requirePerformanceSample). Then the
 * proof tour under its launch extra reports "warm" then "measure" within 6 s of its map's first
 * showing, and is ended (requireTour, no still).
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
        // Undo's notice kept up till it is tapped (S05h2 repair 3): its timer runs on the test clock,
        // which a slow device's waits run past before the death check's Undo
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path)
            .putExtra(MainActivity.UNDO_STAYS, true))
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        // a phone's switch; a tablet's map is beside Sort already
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.CHIP)).assert(hasContentDescription(context.getString(R.string.look_chip_spoken, context.getString(R.string.look_space))))
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && mapDrawn()?.box?.size == 4 } } catch (_: ComposeTimeoutException) {}
        // S05j: checks whose failure should not cost the stills after them, checked at the end
        val later = ArrayList<String>()
        // S05j item 1: on the tablet's two panes the bar's row is the page's colour across the window
        if (device == "tablet") requirePanesBar(dark, later)
        val name = "mapmode-space-${if (dark) "dark" else "light"}-$device"
        shot(name) // kept even when the check below fails, to see why
        requireMapDrawn(name)
        // S05d: at first run the card shows in its one form, every body of the first view is drawn
        // above it, and Got it puts it away without moving the camera
        requireClearOfTheCard(name)
        // S05j items 3 and 4: the first view fills the band between the strip and the card, centred in it
        requireFirstView(dark, later)
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
        // S05g: Space's first view with the card put away (S05d item 2), as Recentre frames it before
        // any choice or ease: the reference the double tap on empty space must come back to
        val spaceHome = recentred(name)
        check(spaceHome.size == 6) { "No camera pose after Recentre for $name: ${mapDrawn()}" }
        // S05j item 6a: the list sheet (the phone's: two panes have none) and the folder chooser over Space,
        // with their status icons
        shotSheets(dark)
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
        requireChoice("space", dark, spaceChosen, spaceBefore, home = spaceHome)
        // S05g A6: a chosen sun, its mark and its links' plasma
        requireSun(dark)
        // S05h A3: the Universe at rest, Fly in and a moved black hole
        requireUniverse(dark)
        // S05h2: with animations off, a fling throws no sparks and a deleted idea fades at once, its links counted
        requireStillDeath(dark)
        // S05i A3: the filter in Space's Universe
        requireFilter(GraphTheme.SPACE, dark)
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
        // S05j item 5: the Look menu's Lines row with Straight on, then Neurons drawn with it, and Curved again
        chooseLines(GraphLineStyle.STRAIGHT, GraphTheme.NEURONS, still = "mapmode-look-lines-${if (dark) "dark" else "light"}-$device")
        SystemClock.sleep(1_000)
        shot("mapmode-straight-neurons-${if (dark) "dark" else "light"}-$device")
        chooseLines(GraphLineStyle.CURVED, GraphTheme.NEURONS)
        // S05f A6: Neuron's legend, kept for the renders check
        shotLegend(GraphTheme.NEURONS, dark)
        // S05f: Neuron's names, at rest and for a chosen body
        val (neuronsChosen, neuronsBefore) = requireNames("neurons", dark, press = false)
        requireChoice("neurons", dark, neuronsChosen, neuronsBefore, home = neuronsHome)
        // S05g A3: Neurons' Show links, on an idea inside the chosen cell, flown into
        requireNeuronLinks(dark, neuronsChosen)
        // S05g A6: a chosen cell with bridges, its lit bridges, and Show links on its idea that bridges out
        requireNeuronBridges(dark)
        // S05i A6: the Neurons' first-run card put away by its Got it, so the filtered map and the cells read whole
        putCardAway(neurons)
        // S05i A3: the filter in Neurons, the cell states chosen, and the legend's Cell states row
        requireFilter(GraphTheme.NEURONS, dark)
        requireCells(dark)
        shotLegendCells(dark)
        // S05g A3: Performance, chosen after Neurons, shows no first-run card; a tap on a point
        // chooses it, and the same marks, Show links, hold and double tap as the other looks
        compose.onNode(hasTestTag(MapLook.CHIP)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(MapLook.PERFORMANCE.identifier)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(MapLook.PERFORMANCE.identifier)).performClick()
        compose.waitUntil(30_000) {
            mapNode()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(GraphTheme.PERFORMANCE.mapLabel) } == true
        }
        // its scene is built off the main thread too: it is Performance's once the camera looks down at its own
        // angle, 0.32 (S05j item 3; before, Space's and Performance's were one)
        compose.waitUntil(30_000) { mapDrawn()?.pose?.getOrNull(5)?.let { abs(it - MapFirstView.FAST_PITCH) <= 1e-3f } == true }
        // Performance draws points, no Space bodies, so its frames count without them
        try { compose.waitUntil(60_000) { mapDrawn()?.let { it.frames >= MapDrawn.ENOUGH && it.box.size == 4 } == true } } catch (_: ComposeTimeoutException) {}
        val performance = "mapmode-chosen-performance-${if (dark) "dark" else "light"}-$device"
        val drawnPerformance = mapDrawn()
        check(drawnPerformance != null && drawnPerformance.frames >= MapDrawn.ENOUGH && drawnPerformance.box.size == 4) {
            "Map mode's Performance has not finished ${MapDrawn.ENOUGH} frames with its points (waited up to a minute) for $performance: $drawnPerformance"
        }
        check(!shown(MapPanelTags.HINT)) { "Performance shows a first-run card for $performance" }
        val performanceHome = drawnPerformance.pose
        // S05g A2: Performance's legend, its last line clear of the foot and the bar too
        shotLegend(GraphTheme.PERFORMANCE, dark)
        check(same(performanceHome, checkNotNull(mapDrawn()).pose)) { "The legend moved Performance's camera for $performance: $performanceHome, now ${mapDrawn()?.pose}" }
        val (performanceChosen, at) = spot(performance)
        compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(at) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK)) { "A tap on a point brought up no card for $performance" }
        requireChoice("performance", dark, performanceChosen, performanceHome, home = performanceHome)
        // S05i A3: the filter in Performance, at once, no camera move, no empty line, the choice kept
        requireFilter(GraphTheme.PERFORMANCE, dark)
        // S05i2 A3: Performance's hubs and folder lines on the sample, and the tour's first phases
        requirePerformanceSample(dark)
        requireTour()
        check(later.isEmpty()) { later.joinToString("\n") }
    }

    /** MainActivity again, on the sample ideas' [look] (its map-preview extra), the tour too if [tour]. */
    private fun launchPreview(look: String, tour: Boolean = false) {
        scenario?.close()
        val context = instrumentation.targetContext
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.MAP_PREVIEW, look)
            .putExtra(MainActivity.MAP_PREVIEW_TOUR, tour))
    }

    /**
     * S05i2 A3 on the sample (SampleIdeas: Examples with Inguinal, Femoral and Anatomy in Inguinal, and
     * Cardiology), read from MapDrawn once the force has settled: 5 hubs and 3 folder lines drawn
     * (mapmode-hubs-performance-<light|dark>-<device>.png); the legend's ladder whole on the screen inside
     * its sheet (mapmode-legend-ladder-performance-...); Examples' filter shows its 4 hubs and 3 lines, and
     * Everything all 5 again; a tap on Cardiology's hub raises a card with its name and Fly in, and Fly in
     * frames its ball: the ball, as the map lays it out, drawn whole inside the map and filling a good share of it.
     */
    private fun requirePerformanceSample(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-hubs-performance-$mode-$device"
        val book = SampleIdeas.book()
        check(book.folders.size == 5 && book.folders.count { it.parentId != null } == 3) { "The sample is not 5 folders, 3 inside another, for $name: ${book.folders}" }
        launchPreview("performance")
        try {
            compose.waitUntil(60_000) { mapDrawn()?.let { it.frames >= MapDrawn.ENOUGH && it.box.size == 4 && it.settled && it.hubs == 5 } == true }
        } catch (_: ComposeTimeoutException) {}
        val rest = checkNotNull(mapDrawn()) { "No map on the sample for $name" }
        check(rest.frames >= MapDrawn.ENOUGH && rest.settled) { "Performance on the sample has not drawn ${MapDrawn.ENOUGH} frames, settled, for $name: $rest" }
        check(rest.hubs == 5 && rest.folderLines == 3) { "Performance on the sample drew ${rest.hubs} hubs and ${rest.folderLines} folder lines, not 5 and 3, for $name: $rest" }
        check(!rest.filtered) { "The sample's map is filtered before any filter for $name: $rest" }
        shot(name)
        val home = rest.pose
        requireLegendLadder(dark)
        // a top folder's filter: Examples and the three folders inside it, their hubs and the lines between them
        val examples = book.folders.first { it.name == "Examples" && it.parentId == null }
        chooseFilter(MapFilter.Folder(examples.id))
        try { compose.waitUntil(30_000) { mapDrawn()?.let { it.filtered && it.hubs == 4 } == true } } catch (_: ComposeTimeoutException) {}
        val filtered = checkNotNull(mapDrawn())
        check(filtered.filtered && filtered.hubs == 4 && filtered.folderLines == 3) { "Examples' filter did not show its 4 hubs and 3 folder lines for $name: $filtered" }
        chooseFilter(MapFilter.All)
        try { compose.waitUntil(30_000) { mapDrawn()?.let { !it.filtered && it.hubs == 5 } == true } } catch (_: ComposeTimeoutException) {}
        val back = checkNotNull(mapDrawn())
        check(!back.filtered && back.hubs == 5 && back.folderLines == 3) { "Everything did not bring back the 5 hubs and 3 folder lines for $name: $back" }
        // a tap on Cardiology's hub, through the map's hit test: a folder's card with Fly in
        val res = instrumentation.targetContext.resources
        val cardiology = book.folders.first { it.name == "Cardiology" }
        val layout = checkNotNull(PerfMap.build(perfInput(book, book.allEdges(), MapLinkLength.STANDARD))) { "No layout for the sample for $name" }
        val g = layout.graph
        val f = g.folderIds.indexOfFirst { it.equals(cardiology.id, ignoreCase = true) }
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $name" }
        val hub = tap.ids.indexOfFirst { it.equals(cardiology.id, ignoreCase = true) }
        check(f >= 0 && hub == g.noteCount + f) { "Cardiology's hub is body $hub on the map, not the layout's ${g.noteCount + f}, for $name" }
        compose.runOnIdle { tap.tap(hub) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == hub } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == hub) { "A tap on Cardiology's hub ($hub) raised no card for $name: ${mapDrawn()}" }
        val inCard = hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        // its title, read in the unmerged tree as MapPeekCardTest's cardFor reads it: the card is one
        // clickable item, so in the merged tree its words are merged into the card's own node and no
        // node below the card holds them; only its buttons, clickable items of their own, stay apart
        val titles = compose.onAllNodes(hasTestTag(MapPanelTags.PEEK_TITLE) and inCard, useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
        check(titles == listOf(cardiology.name)) { "The hub's card does not name Cardiology for $name: its title reads $titles" }
        val flyIn = hasText(res.getString(R.string.map_peek_fly_in)) and inCard
        check(compose.onAllNodes(flyIn).fetchSemanticsNodes().size == 1) { "The hub's card has no Fly in for $name" }
        compose.onNode(flyIn).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.flown == hub } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_500) // the flight, or at once with animations off
        compose.waitForIdle()
        val flown = checkNotNull(mapDrawn())
        check(flown.flown == hub && flown.pose.size == 6 && !same(home, flown.pose)) { "Fly in on Cardiology's hub did not fly to it for $name: $home, then $flown" }
        // its ball as the map lays it out (the hub at its centre), seen from the pose Fly in left
        val p = flown.pose
        val camera = OrbitCamera(Vec3(p[0], p[1], p[2]), p[3], p[4], p[5], MapFirstView.radians(MapFirstView.FAST_FIELD_DEGREES))
        val c = layout.seed.systemCentre
        val centre = Vec3(c[3 * f], c[3 * f + 1], c[3 * f + 2])
        val reach = max(layout.seed.systemRadius[f], 1f)
        val w = flown.width.toFloat(); val h = flown.height.toFloat()
        val at = checkNotNull(camera.project(centre, w, h)) { "Cardiology's ball is behind the camera after Fly in for $name: $flown" }
        val apart = (camera.eye() - centre).length()
        check(apart > reach) { "The camera is inside Cardiology's ball after Fly in for $name: $apart, the ball $reach" }
        val across = reach * camera.focalPixels(h) / sqrt(apart * apart - reach * reach)
        android.util.Log.i("MapModeSpaceRenderTest", "$name: Cardiology's ball at $at, $across px round, in the ${w}x$h map")
        check(at.first - across >= -1f && at.first + across <= w + 1f && at.second - across >= -1f && at.second + across <= h + 1f) {
            "Fly in left Cardiology's ball ($at, $across px round) outside the ${w}x$h map for $name"
        }
        check(across >= 0.15f * min(w, h)) { "Fly in left Cardiology's ball only $across px round in the ${w}x$h map for $name" }
        scenario?.close(); scenario = null
    }

    /**
     * S05i2 A3: Performance's legend on the sample, at its top: its ladder (glow, folder, idea, far idea)
     * one item, drawn whole on the screen inside the sheet; a still, mapmode-legend-ladder-performance-...
     */
    private fun requireLegendLadder(dark: Boolean) {
        val name = "mapmode-legend-ladder-performance-${if (dark) "dark" else "light"}-$device"
        val theme = GraphTheme.PERFORMANCE
        val content = MapLegend.of(theme)
        press(hasContentDescription("Look") and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, theme.title))
        press(hasText(theme.legendTitle) and hasClickAction())
        val tag = MapPanelTags.legend(theme)
        compose.waitUntil(10_000) { shown(tag) }
        SystemClock.sleep(1_000) // the sheet comes to rest
        val sheet = compose.onNode(hasTestTag(tag)).fetchSemanticsNode().boundsInRoot
        val ladders = compose.onAllNodes(hasContentDescription(content.ladderLabel)).fetchSemanticsNodes()
        check(content.ladderLabel.isNotEmpty() && ladders.size == 1) { "Performance's legend shows ${ladders.size} ladders, not one, for $name" }
        val ladder = ladders.single().boundsInRoot
        val screen = instrumentation.targetContext.resources.displayMetrics
        check(ladder.top >= sheet.top && ladder.bottom <= minOf(sheet.bottom, screen.heightPixels.toFloat()) && ladder.left >= 0f && ladder.right <= screen.widthPixels) {
            "Performance's ladder ($ladder) is not whole on the screen inside its sheet ($sheet) for $name"
        }
        shot(name)
        ui.pressBack()
        try { compose.waitUntil(3_000) { !shown(tag) } } catch (_: ComposeTimeoutException) { ui.pressBack() }
        compose.waitUntil(10_000) { !shown(tag) }
        compose.waitForIdle()
    }

    /**
     * S05i2 A3: the proof tour, launched with the preview's tour extra: once its map shows (its
     * 100,000 made-up ideas built), it reports "warm" and then "measure" (MapDrawn.tourPhase) within
     * 6 s; the test then ends it. The map's first showing is read as its node's first appearance,
     * which comes with the map's MapShown. No still: the tour is never shown to a person.
     */
    private fun requireTour() {
        launchPreview("performance", tour = true)
        try { compose.waitUntil(240_000) { mapNode() != null } } catch (_: ComposeTimeoutException) {}
        check(mapNode() != null) { "The tour's map did not show within 4 minutes" }
        val shownAt = SystemClock.uptimeMillis()
        val phases = ArrayList<String>()
        try {
            compose.waitUntil(6_000) {
                val phase = mapDrawn()?.tourPhase ?: ""
                if (phase.isNotEmpty() && phases.lastOrNull() != phase) phases.add(phase)
                phase == "measure"
            }
        } catch (_: ComposeTimeoutException) {}
        val took = SystemClock.uptimeMillis() - shownAt
        android.util.Log.i("MapModeSpaceRenderTest", "the tour's phases $phases, measure ${took} ms after its map showed")
        check(phases.take(2) == listOf("warm", "measure") && took <= 6_000) {
            "The tour did not report warm then measure within 6 s of its map showing: $phases after $took ms; ${mapDrawn()}"
        }
        scenario?.close(); scenario = null
    }

    /**
     * S05j item 1 (tablet): two panes start the map below the status bar, so the bar's row shows the page
     * across the window, the map pane's half too; its icons follow the page. A still,
     * mapmode-panes-bar-<light|dark>-tablet.png.
     */
    private fun requirePanesBar(dark: Boolean, later: MutableList<String>) {
        val name = "mapmode-panes-bar-${if (dark) "dark" else "light"}-$device"
        shot(name)
        val res = instrumentation.targetContext.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (id != 0) res.getDimensionPixelSize(id) else 0
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot for $name" }
        val page = WardPalette.hex(WardToken.background, dark)
        // the map pane's half of the row, clear of the clock (start) and the icons (end)
        val x = (screen.width * 0.62f).toInt(); val y = (bar / 2).coerceAtLeast(1)
        val c = screen.getPixel(x, y) and 0xFFFFFF
        val near = listOf(16, 8, 0).all { abs(((c shr it) and 255) - ((page shr it) and 255)) <= 6 }
        if (!near) later.add("The status bar's row over the map pane is not the page for $name: #%06X at $x, $y against #%06X".format(c, page))
        val map = mapNode()?.boundsInRoot
        if (map == null || map.top < bar - 1f) later.add("The map pane starts under the status bar for $name: $map, the bar $bar px")
    }

    /**
     * S05j items 3 and 4: Space's first view, the card up. Every drawn body lies between the strip's foot
     * and the card's top (requireClearOfTheCard); on the phone the bodies' box is centred in that band: its
     * middle within 4% of the view's height of the band's middle, at rest and as the map runs, and the gaps
     * above and below the box the bodies fill as the map runs within 8% of the band (repair 4: the first
     * view frames every place the Universe's bodies reach, not one instant of their orbits). A still,
     * mapmode-first-view-<light|dark>-<device>.png.
     */
    private fun requireFirstView(dark: Boolean, later: MutableList<String>) {
        val name = "mapmode-first-view-${if (dark) "dark" else "light"}-$device"
        shot(name)
        if (device != "phone") return
        val card = compose.onNode(hasTestTag(MapPanelTags.HINT)).fetchSemanticsNode().boundsInRoot
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        val map = checkNotNull(mapNode()).boundsInRoot
        val drawn = checkNotNull(mapDrawn())
        val box = drawn.box
        val top = map.top + box[1]; val bottom = map.top + box[3]
        val band = card.top - strip.bottom
        val off = abs((top + bottom) / 2 - (strip.bottom + card.top) / 2)
        val gaps = abs((top - strip.bottom) - (card.top - bottom))
        android.util.Log.i("MapModeSpaceRenderTest", "$name: box $top-$bottom in the band ${strip.bottom}-${card.top}: off $off, gaps differ by $gaps")
        if (off > 0.04f * map.height) later.add("The first view's box is off the band's middle by $off px for $name (4% is ${0.04f * map.height})")
        // S05j repair 4: the Universe turns, and its first view frames every place its bodies reach (iOS's
        // envelope, GraphUniverse.swift:1475-1480; UniversePlan.kt:809-811), the To sort comets' long
        // orbits too. With animations off the orbit clock stays at 0, where those comets sit at their homes
        // low in the map, so the box above is one instant of a map that moves (here 519-1233 px in the band
        // 275-1328, 74 px low). The gaps are measured on the box the bodies fill as the map runs: each
        // body over ten minutes of its orbit, drawn as the renderer draws it (the scene's node radius
        // round it), seen from the first view's pose. The test's plan is first matched to the map's:
        // its bodies at the clock's 0, so drawn, give the box the map reports.
        val book = IdeaBook(notes, folders)
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        val radius = MapScene.universe(plan).nodeRadius
        val pose = drawn.pose
        check(pose.size == 6) { "No camera pose for $name: $drawn" }
        val camera = OrbitCamera(Vec3(pose[0], pose[1], pose[2]), pose[3], pose[4], pose[5],
            MapFirstView.radians(MapFirstView.TILTED_FIELD_DEGREES), MapFirstView.roll(map.width, map.height))
        val focal = camera.focalPixels(map.height)
        fun reach(seconds: Double): FloatArray {
            val r = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in plan.bodies.indices) {
                val p = plan.position(i, seconds)
                val (x, y) = camera.project(p, map.width, map.height) ?: continue
                val half = radius * focal / camera.depthOf(p)
                if (half * 2 <= 2f) continue
                r[0] = minOf(r[0], x - half); r[1] = minOf(r[1], y - half); r[2] = maxOf(r[2], x + half); r[3] = maxOf(r[3], y + half)
            }
            return r
        }
        val rest = reach(0.0)
        check(box.indices.all { abs(rest[it] - box[it]) <= 2f }) {
            "The test's Universe plan, seen from the map's pose, is not what the map drew for $name: ${rest.toList()} against $box"
        }
        var high = Float.MAX_VALUE; var low = -Float.MAX_VALUE
        var seconds = 0.0
        while (seconds <= 600.0) { val b = reach(seconds); high = minOf(high, b[1]); low = maxOf(low, b[3]); seconds += 1.0 }
        val runTop = map.top + high; val runBottom = map.top + low
        val runOff = abs((runTop + runBottom) / 2 - (strip.bottom + card.top) / 2)
        val runGaps = abs((runTop - strip.bottom) - (card.top - runBottom))
        android.util.Log.i("MapModeSpaceRenderTest", "$name: over ten minutes $runTop-$runBottom: off $runOff, gaps differ by $runGaps")
        if (runTop < strip.bottom - 0.5f || runBottom > card.top + 0.5f) later.add("The bodies reach outside the band ${strip.bottom}-${card.top} as the map runs for $name: $runTop-$runBottom")
        if (runOff > 0.04f * map.height) later.add("The first view's bodies, as the map runs, are off the band's middle by $runOff px for $name (4% is ${0.04f * map.height})")
        if (runGaps > 0.08f * band) later.add("The first view's gaps, as the map runs, differ by $runGaps px for $name (8% of the band is ${0.08f * band})")
    }

    /**
     * S05j item 6a, stills with their status icons: on the phone the list sheet over Space
     * (mapmode-list-sheet-<light|dark>-phone.png), then a row's Choose folder, the folder chooser over it;
     * on the tablet's two panes, which have no list sheet (IdeasView's panes: no list chip), the Sort
     * pane's row opens the chooser beside Space. Either way, mapmode-chooser-sheet-<light|dark>-<device>.png.
     */
    private fun shotSheets(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val idea = notes.first { it.folderId == null }.title
        val choose = hasContentDescription(instrumentation.targetContext.getString(R.string.row_choose_folder_spoken, idea)) and hasClickAction()
        val sheet = device == "phone"
        if (sheet) {
            compose.onNode(hasTestTag(MapTags.LIST_CHIP)).performClick()
            compose.waitUntil(10_000) { shown(MapTags.LIST) }
            SystemClock.sleep(1_000) // the sheet comes to rest
            shot("mapmode-list-sheet-$mode-$device")
        }
        compose.onNode(if (sheet) choose and hasAnyAncestor(hasTestTag(MapTags.LIST)) else choose).performClick()
        compose.waitUntil(10_000) { shown(FOLDER_CHOOSER_TAG) }
        SystemClock.sleep(1_000) // the chooser comes to rest
        shot("mapmode-chooser-sheet-$mode-$device")
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(FOLDER_CHOOSER_TAG) }
        if (sheet) {
            ui.pressBack()
            compose.waitUntil(10_000) { !shown(MapTags.LIST) }
        }
        compose.waitForIdle()
    }

    /** A click as a person's, by the node's own action: the card may sit over the tools on a phone. */
    private fun press(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** The first-run card put away by its Got it, as a person would, where it shows (none is up after it). */
    private fun putCardAway(name: String) {
        if (!shown(MapPanelTags.HINT)) { android.util.Log.i("MapModeSpaceRenderTest", "$name: no first-run card to put away"); return }
        press(hasText(instrumentation.targetContext.getString(R.string.map_touch_got_it)) and hasAnyAncestor(hasTestTag(MapPanelTags.HINT)))
        compose.waitUntil(10_000) { !shown(MapPanelTags.HINT) }
        SystemClock.sleep(1_000)
        compose.waitForIdle()
    }

    private fun chooseFilter(filter: MapFilter) {
        press(hasTestTag(MapPanelTags.FILTER_TOOL))
        press(hasTestTag(MapPanelTags.filterRow(filter)))
    }

    /** No card, the map at its first view; the pose it rests in. */
    private fun atHome(name: String): List<Float> {
        if (shown(MapPanelTags.PEEK)) {
            compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(emptySpot(name)) }
            compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        }
        press(hasContentDescription("Recentre") and hasClickAction())
        SystemClock.sleep(1_500)
        compose.waitForIdle()
        return checkNotNull(mapDrawn()).pose
    }

    /**
     * S05i A3, read from MapDrawn, in [theme]: the Filter's menu open (a still,
     * mapmode-filter-<theme>-<light|dark>-<device>.png); a body chosen, then Pages, of which the fixture
     * has none, so nothing shows, no link, "Nothing here for this filter." over Space and Neurons, and
     * the card let go (kept in Performance, as iOS); Garden alone, its bodies only, the camera flying
     * to it in the Universe and Neurons and still in Performance; Linked ideas hiding the unlinked;
     * Everything bringing all back, and the first view where it flew. In Neurons, first with every
     * cell closed: Garden hides each closed cell, as iOS's (a closed cell holds no idea,
     * GraphNeurons.swift:430), and the empty line shows, as iOS counts each idea by the cell it is
     * drawn in (Graph3DView.swift:861-862); then Garden opened
     * for the rest, and still open after Everything, which only frames the map again.
     */
    private fun requireFilter(theme: GraphTheme, dark: Boolean) {
        val look = theme.rawValue
        val name = "mapmode-filter-$look-${if (dark) "dark" else "light"}-$device"
        val perf = theme == GraphTheme.PERFORMANCE
        val neurons = theme == GraphTheme.NEURONS
        var home = atHome(name)
        try { compose.waitUntil(30_000) { framed(mapDrawn()) || (perf && (mapDrawn()?.frames ?: 0) >= MapDrawn.ENOUGH) } } catch (_: ComposeTimeoutException) {}
        if (neurons) {
            val closed = checkNotNull(mapDrawn())
            chooseFilter(MapFilter.Folder(garden.id))
            try { compose.waitUntil(30_000) { mapDrawn()?.let { it.filtered && it.hidden == closed.shown } == true } } catch (_: ComposeTimeoutException) {}
            val closedGarden = checkNotNull(mapDrawn())
            check(closedGarden.filtered && closedGarden.shown == 0 && closedGarden.hidden == closed.shown) {
                "Garden, every cell closed, did not hide each closed cell as iOS's for $name: $closedGarden (before $closed)"
            }
            // iOS counts each note by the cell the plan draws it in (Graph3DView.swift:861-862), so no idea passes
            try { compose.waitUntil(10_000) { shown(MapPanelTags.FILTER_EMPTY) } } catch (_: ComposeTimeoutException) {}
            check(shown(MapPanelTags.FILTER_EMPTY)) { "Garden, its cell closed, showed no empty line as iOS's for $name" }
            chooseFilter(MapFilter.All)
            try { compose.waitUntil(30_000) { mapDrawn()?.let { !it.filtered && it.hidden == 0 } == true } } catch (_: ComposeTimeoutException) {}
            try { compose.waitUntil(10_000) { mapDrawn()?.pose?.let { same(home, it) } == true } } catch (_: ComposeTimeoutException) {}
            check(same(home, checkNotNull(mapDrawn()).pose)) { "Everything, cells closed, did not bring back the first view for $name: ${mapDrawn()?.pose}, home $home" }
            openGarden(name)
            home = checkNotNull(mapDrawn()).pose
        }
        val all = checkNotNull(mapDrawn())
        check(!all.filtered && all.hidden == 0 && all.shown > 0) { "The map is filtered before any filter for $name: $all" }
        // a body chosen, as a person would
        val (_, at) = spot(name)
        compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(at) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK)) { "A tap on the spot brought up no card for $name" }
        // the menu open, Everything checked
        press(hasTestTag(MapPanelTags.FILTER_TOOL))
        compose.waitUntil(10_000) { shown(MapPanelTags.filterRow(MapFilter.Pages)) }
        shot(name)
        press(hasTestTag(MapPanelTags.filterRow(MapFilter.Pages)))
        try { compose.waitUntil(30_000) { mapDrawn()?.let { it.filtered && it.shown == 0 } == true } } catch (_: ComposeTimeoutException) {}
        val pages = checkNotNull(mapDrawn())
        check(pages.filtered && pages.shown == 0 && pages.hidden == all.shown && pages.filterLinks == 0) {
            "Pages, of which there are none, left something on the map for $name: $pages (before $all)"
        }
        check(shown(MapPanelTags.FILTER_EMPTY) == !perf) { "The empty line is ${if (perf) "over" else "missing from"} $name" }
        if (perf) check(shown(MapPanelTags.PEEK)) { "Performance let go of its choice under a filter for $name" }
        else {
            try { compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) } } catch (_: ComposeTimeoutException) {}
            check(!shown(MapPanelTags.PEEK)) { "The chosen body, filtered away, kept its card for $name" }
        }
        // S05i A6: the map under Pages, empty, its line "Nothing here for this filter." over Space and Neurons
        shot("mapmode-filtered-pages-$look-${if (dark) "dark" else "light"}-$device")
        // one folder: Garden's bodies only, the camera flying to it where iOS's does
        chooseFilter(MapFilter.Folder(garden.id))
        try { compose.waitUntil(30_000) { mapDrawn()?.let { it.filtered && it.shown > 0 } == true } } catch (_: ComposeTimeoutException) {}
        val others = notes.count { it.folderId != garden.id }
        val inGarden = checkNotNull(mapDrawn())
        check(inGarden.filtered && inGarden.shown > 0 && inGarden.hidden > 0 && !shown(MapPanelTags.FILTER_EMPTY)) { "Garden alone for $name: $inGarden" }
        // S05i2: Performance's bodies are its ideas and its folders' hubs, and a folder filter shows the hubs inside it
        if (perf) check(inGarden.shown == notes.size - others + 1 && inGarden.hidden == others + folders.size - 1) {
            "Performance's Garden is not its three ideas and its hub for $name: $inGarden"
        }
        if (theme == GraphTheme.SPACE) check(inGarden.hidden >= others) { "Space's Garden keeps another folder's idea for $name: $inGarden" }
        if (perf) {
            SystemClock.sleep(1_500)
            check(same(home, checkNotNull(mapDrawn()).pose)) { "Performance's camera moved for a folder filter for $name: ${mapDrawn()?.pose}" }
        } else {
            try { compose.waitUntil(10_000) { mapDrawn()?.pose?.let { !same(home, it) } == true } } catch (_: ComposeTimeoutException) {}
            check(!same(home, checkNotNull(mapDrawn()).pose)) { "The camera did not fly to Garden for $name" }
        }
        // S05i A6: the map under Garden alone, flown to where iOS's flies
        shot("mapmode-filtered-garden-$look-${if (dark) "dark" else "light"}-$device")
        // Linked ideas: the unlinked go (the closed Neurons draw no idea, so only Space and Performance can show it)
        chooseFilter(MapFilter.Linked)
        try { compose.waitUntil(30_000) { mapDrawn()?.let { it.filtered && it.shown > 0 && it.shown != inGarden.shown } == true } } catch (_: ComposeTimeoutException) {}
        val linked = checkNotNull(mapDrawn())
        val unlinked = notes.count { n -> n.links.isEmpty() && notes.none { n.id in it.links } && !notes.any { it.body.contains("[[" + n.title + "]]") } }
        check(linked.filtered && linked.shown > 0) { "Linked ideas showed nothing for $name: $linked" }
        if (theme != GraphTheme.NEURONS) check(linked.hidden >= unlinked && unlinked > 0) { "Linked ideas kept an unlinked idea for $name: $linked, $unlinked unlinked" }
        // Everything: all back, and the first view where the folder filter flew
        chooseFilter(MapFilter.All)
        try { compose.waitUntil(30_000) { mapDrawn()?.let { !it.filtered && it.hidden == 0 } == true } } catch (_: ComposeTimeoutException) {}
        val back = checkNotNull(mapDrawn())
        check(!back.filtered && back.hidden == 0 && back.shown == all.shown && back.filterLinks == all.filterLinks) { "Everything did not bring all back for $name: $back (before $all)" }
        if (neurons) check(back.build == all.build && gardenOpen()) { "Everything after a filter closed Garden's cell for $name: $back (before $all)" }
        if (!perf) {
            try { compose.waitUntil(10_000) { mapDrawn()?.pose?.let { same(home, it) } == true } } catch (_: ComposeTimeoutException) {}
            check(same(home, checkNotNull(mapDrawn()).pose)) { "Everything after a filter did not bring back the first view for $name: ${mapDrawn()?.pose}, home $home" }
        }
        if (shown(MapPanelTags.PEEK)) atHome(name)
    }

    /** Whether the Neurons' Garden cell is open: its first idea is a body on the map. */
    private fun gardenOpen(): Boolean {
        val first = notes.first { it.folderId == garden.id }
        return mapNode()?.config?.getOrNull(MapTapKey)?.ids?.any { it.equals(first.id, ignoreCase = true) } == true
    }

    /** Neurons, every cell closed: Garden chosen and flown into, its ideas drawn, then its card put away. */
    private fun openGarden(name: String) {
        val res = instrumentation.targetContext.resources
        val ideas = notes.count { it.folderId == garden.id }
        val closed = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        val cell = closed.ids.indexOfFirst { it.equals(garden.id, ignoreCase = true) }
        check(cell >= 0) { "No Garden cell for $name" }
        compose.runOnIdle { closed.tap(cell) }
        compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) }
        compose.onNode(hasText(res.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        compose.waitUntil(30_000) { gardenOpen() }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && mapDrawn()?.states?.sum() == ideas } } catch (_: ComposeTimeoutException) {}
        if (shown(MapPanelTags.PEEK)) {
            compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(emptySpot(name)) }
            compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        }
        compose.waitForIdle()
    }

    private fun openLook(theme: GraphTheme) =
        press(hasContentDescription("Look") and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, theme.title))

    /** The Neurons' ideas drawn, by state, once a new scene has drawn them all. */
    private fun statesAfter(build: Int, want: (List<Int>) -> Boolean): List<Int> {
        try { compose.waitUntil(60_000) { mapDrawn()?.let { it.build != build && framed(it) && want(it.states) } == true } } catch (_: ComposeTimeoutException) {}
        return checkNotNull(mapDrawn()).states
    }

    /**
     * S05i A3 in Neurons: Garden opened, its three ideas drawn resting; Look › Cells › Firing draws
     * each firing; Cell by cell gives Garden Engulfing, which wins inside it (a still,
     * mapmode-cells-neurons-<light|dark>-<device>.png); Same as all and Resting, none firing. A6: the
     * Look menu with its Cells group (mapmode-cells-menu-neurons-...) and the Cell by cell sheet
     * (mapmode-cells-sheet-neurons-...), as iOS's testNeuronsLookMenu shows the menu; then each of the
     * six states chosen in Look › Cells draws all of Garden's ideas in it, seen close up
     * (closeUpOnIdea; mapmode-cells-<state>-neurons-...), one idea framed with its halo, each still told
     * from resting's by MapDrawn.halos and by its pixels round the idea. The choices are left natural.
     */
    private fun requireCells(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-cells-neurons-$mode-$device"
        val res = instrumentation.targetContext.resources
        atHome(name)
        val ideas = notes.count { it.folderId == garden.id }
        openGarden(name)
        val natural = checkNotNull(mapDrawn())
        check(natural.states.size == 6 && natural.states[NeuronState.RESTING.code] == ideas && natural.states.sum() == ideas) {
            "Garden's $ideas ideas do not rest, left alone, for $name: ${natural.states}"
        }
        // Firing for every idea, the menu kept as a still with its Cells group first
        openLook(GraphTheme.NEURONS)
        compose.waitUntil(10_000) { shown("lookCell-" + NeuronState.FIRING.rawValue) }
        check(NeuronState.menuOrder.all { shown("lookCell-" + it.rawValue) } && shown(MapPanelTags.LOOK_CELL_BY_CELL)) {
            "The Look menu does not show the six cell states and Cell by cell for $name"
        }
        SystemClock.sleep(500) // the menu comes to rest
        shot("mapmode-cells-menu-neurons-$mode-$device")
        press(hasTestTag("lookCell-" + NeuronState.FIRING.rawValue))
        val firing = statesAfter(natural.build) { it.getOrNull(NeuronState.FIRING.code) == ideas }
        check(firing.getOrNull(NeuronState.FIRING.code) == ideas && firing.sum() == ideas) { "Look › Cells › Firing does not draw every idea firing for $name: $firing" }
        // Cell by cell: Garden Engulfing, which wins inside it; the sheet kept as a still
        val firingBuild = checkNotNull(mapDrawn()).build
        openLook(GraphTheme.NEURONS)
        press(hasTestTag(MapPanelTags.LOOK_CELL_BY_CELL))
        compose.waitUntil(10_000) { shown(MapPanelTags.CELL_BY_CELL) }
        SystemClock.sleep(800) // the sheet comes to rest
        check(folders.all { shown("cellByCell-" + it.id) }) { "The Cell by cell sheet has no row for each top-level folder for $name" }
        shot("mapmode-cells-sheet-neurons-$mode-$device")
        press(hasTestTag("cellByCell-" + garden.id))
        press(hasText(NeuronState.ENGULFING.title) and hasClickAction())
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(MapPanelTags.CELL_BY_CELL) }
        val engulfing = statesAfter(firingBuild) { it.getOrNull(NeuronState.ENGULFING.code) == ideas }
        shot(name)
        check(engulfing.getOrNull(NeuronState.ENGULFING.code) == ideas && engulfing.sum() == ideas) { "Cell by cell's Engulfing does not win in Garden for $name: $engulfing" }
        // Same as all, then Resting: none firing
        val engulfBuild = checkNotNull(mapDrawn()).build
        openLook(GraphTheme.NEURONS)
        press(hasTestTag(MapPanelTags.LOOK_CELL_BY_CELL))
        compose.waitUntil(10_000) { shown(MapPanelTags.CELL_BY_CELL) }
        SystemClock.sleep(800)
        press(hasTestTag("cellByCell-" + garden.id))
        press(hasText(res.getString(R.string.map_same_as_all)) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
            and SemanticsMatcher("not the row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("cellByCell-") != true })
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(MapPanelTags.CELL_BY_CELL) }
        val sameAsAll = statesAfter(engulfBuild) { it.getOrNull(NeuronState.FIRING.code) == ideas }
        check(sameAsAll.getOrNull(NeuronState.FIRING.code) == ideas && sameAsAll.getOrNull(NeuronState.ENGULFING.code) == 0 && sameAsAll.sum() == ideas) {
            "Same as all did not give Garden back to Cells' Firing for $name: $sameAsAll"
        }
        val sameBuild = checkNotNull(mapDrawn()).build
        openLook(GraphTheme.NEURONS)
        press(hasTestTag("lookCell-" + NeuronState.RESTING.rawValue))
        val resting = statesAfter(sameBuild) { it.getOrNull(NeuronState.RESTING.code) == ideas }
        check(resting.getOrNull(NeuronState.RESTING.code) == ideas && resting.getOrNull(NeuronState.FIRING.code) == 0) { "Resting left an idea firing for $name: $resting" }
        // A6: each state close up, Resting last, so every choice builds the cells again and the choice ends
        // natural. Each still is framed on one of Garden's ideas, its body and its own halo, where its state
        // is drawn (closeUpOnIdea); each still is told from resting's by MapDrawn's numbers and by its pixels
        val subject = checkNotNull(notes.firstOrNull { it.folderId == garden.id }) { "Garden has no idea for $name" }
        var plan: CloseUp? = null
        val crops = LinkedHashMap<NeuronState, IdeaCrop>()
        for (state in NeuronState.menuOrder.filter { it != NeuronState.RESTING } + NeuronState.RESTING) {
            val still = "mapmode-cells-${state.rawValue}-neurons-$mode-$device"
            val build = checkNotNull(mapDrawn()).build
            openLook(GraphTheme.NEURONS)
            press(hasTestTag("lookCell-" + state.rawValue))
            val drawn = statesAfter(build) { it.getOrNull(state.code) == ideas }
            check(drawn.getOrNull(state.code) == ideas && drawn.sum() == ideas) { "Look › Cells › ${state.title} does not draw every idea in it for $still: $drawn" }
            plan = closeUpOnIdea(still, subject.id, plan)
            // the state's own halo: every other state keeps one for each idea, resting none (GraphNeuronLook.swift:340)
            val halos = if (state == NeuronState.RESTING) 0 else ideas
            try { compose.waitUntil(10_000) { mapDrawn()?.halos?.let { it.getOrNull(state.code) == halos && it.sum() == halos } == true } } catch (_: ComposeTimeoutException) {}
            val close = checkNotNull(mapDrawn())
            check(close.states.getOrNull(state.code) == ideas && close.states.sum() == ideas && gardenOpen()) {
                "Garden, close up, does not draw its $ideas ideas ${state.title} for $still: $close"
            }
            check(close.halos.getOrNull(state.code) == halos && close.halos.sum() == halos) {
                "Garden's ideas, close up, do not draw ${state.title}'s halos ($halos, each in its state) for $still: ${close.halos}"
            }
            val cell = requireCloseUp(still, subject.id, close)
            check(cell.state == state.code) { "${subject.title}, close up, is not drawn ${state.title} for $still: $cell" }
            shot(still)
            crops[state] = ideaCrop(cell)
        }
        // each state's still differs from resting's round the idea, where its halo and soma are drawn
        val restingCrop = checkNotNull(crops[NeuronState.RESTING])
        for ((state, crop) in crops) {
            if (state == NeuronState.RESTING) continue
            val share = crop.differs(restingCrop)
            android.util.Log.i("MapModeSpaceRenderTest", "mapmode-cells-${state.rawValue}-neurons-$mode-$device: ${"%.3f".format(share)} of the pixels round ${subject.title} differ from resting's")
            check(share >= IdeaCrop.DIFFERENT) {
                "mapmode-cells-${state.rawValue}-neurons-$mode-$device looks like resting's round ${subject.title}: only $share of its pixels differ, under ${IdeaCrop.DIFFERENT}"
            }
        }
        atHome(name)
    }

    /** The two-finger moves that brought an idea close, and the camera they left: the same scene from its first view takes them again. */
    private class CloseUp(val moves: List<FloatArray>, val pose: List<Float>)

    /**
     * S05i A6 (repair 6): one of Garden's ideas, [id], brought to the middle of the map and close, as a
     * person would with two fingers (moved together to pan, spread to zoom; Map3DView's own rule), so its
     * state reads in a still: its body about [CLOSE_DP] across and its own halo, 2.5 times that, where each
     * state's process is drawn (NeuronGl.drawHalo; GraphNeuronShaders.swift:543-605). Where the idea is
     * drawn, and how big, is read from MapDrawn.cells, so nothing is chosen. It is panned to the middle,
     * then zoomed and panned in turn until it lies near the middle at about that size (it lies off the
     * camera's focal plane, so a zoom moves it). A new scene starts from the same first view, so [last]'s
     * moves, taken again, leave the camera where they did; where they do not, the close-up is measured
     * afresh from there (Recentre would close the cell). As CI runs it, with animations off, a tap moves no camera.
     */
    private fun closeUpOnIdea(name: String, id: String, last: CloseUp?): CloseUp {
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val size = checkNotNull(mapNode()).size
        val w = size.width.toFloat(); val h = size.height.toFloat()
        val middle = Offset(w / 2, h / 2)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val apart = 40f * density
        val widest = w / 2 - 24f * density
        val want = CLOSE_DP * density
        val moves = ArrayList<FloatArray>()
        fun fingers(from: Offset, to: Offset, near: Float, far: Float) {
            compose.onNode(map).performTouchInput {
                pinch(from - Offset(near, 0f), to - Offset(far, 0f), from + Offset(near, 0f), to + Offset(far, 0f), 600)
            }
            compose.waitForIdle()
            SystemClock.sleep(500)
            moves += floatArrayOf(from.x, from.y, to.x, to.y, near, far)
        }
        if (last != null) {
            for (m in last.moves) fingers(Offset(m[0], m[1]), Offset(m[2], m[3]), m[4], m[5])
            try { compose.waitUntil(10_000) { mapDrawn()?.pose?.let { same(last.pose, it) } == true } } catch (_: ComposeTimeoutException) {}
            if (same(last.pose, checkNotNull(mapDrawn()).pose)) return last
            android.util.Log.i("MapModeSpaceRenderTest", "$name: the close-up's moves left the camera at ${mapDrawn()?.pose}, not ${last.pose}; measured afresh")
            moves.clear()
        }
        val index = ideaIndex(name, id)
        fun cell(): MapDrawnCell {
            try { compose.waitUntil(10_000) { mapDrawn()?.cells?.any { it.index == index } == true } } catch (_: ComposeTimeoutException) {}
            return checkNotNull(mapDrawn()?.cells?.firstOrNull { it.index == index }) { "$id is not on the map for $name: ${mapDrawn()?.cells}" }
        }
        // fingers only move the map where they land on it, clear of its strip, bar and tools: a pan
        // starts at the idea's spot, or the nearest in the map's middle half where the idea is drawn
        // outside it, and carries the map the idea's way to the middle, as far as that half allows.
        // A replay that left the camera elsewhere can leave the idea off the map (on CI, 3b5f900's
        // pacemaker close-up drew it at x 1620 of a 1080 px map, and 14 moves from there moved nothing)
        val left = w / 4; val right = 3 * w / 4; val top = h / 4; val bottom = 3 * h / 4
        fun toMiddle(c: MapDrawnCell) {
            val from = Offset(c.x.coerceIn(left, right), c.y.coerceIn(top, bottom))
            val by = middle - Offset(c.x, c.y)
            fun room(at: Float, d: Float, low: Float, high: Float) = when {
                d > 0f -> (high - at) / d
                d < 0f -> (low - at) / d
                else -> 1f
            }
            val k = min(1f, min(room(from.x, by.x, left, right), room(from.y, by.y, top, bottom)))
            fingers(from, from + by * k, apart, apart)
        }
        val before = cell()
        repeat(2) { toMiddle(cell()) }
        val most = widest / apart
        for (step in 0 until 12) {
            val c = cell()
            val off = (Offset(c.x, c.y) - middle).getDistance()
            val zoom = want / max(c.across, 1f)
            if (off > 0.06f * min(w, h)) toMiddle(c)
            else if (zoom > 1.15f) fingers(middle, middle, apart, apart * min(zoom, most))
            else if (zoom < 0.87f) fingers(middle, middle, apart * min(1f / zoom, most), apart)
            else break
        }
        val close = cell()
        android.util.Log.i("MapModeSpaceRenderTest", "$name: $id is ${close.across} px across close up at (${close.x}, ${close.y}), ${before.across} px before; ${moves.size} moves")
        return CloseUp(moves, checkNotNull(mapDrawn()).pose)
    }

    /** The scene index of the idea [id], from the map's taps (MapTap.ids, ignoring case). */
    private fun ideaIndex(name: String, id: String): Int {
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map takes no taps for $name" }
        val i = tap.ids.indexOfFirst { it.equals(id, ignoreCase = true) }
        check(i >= 0) { "$id is not on the map for $name" }
        return i
    }

    /**
     * S05i A6 (repair 6): the idea [id] in [drawn], close up: at least [LEAST_DP] across, and its whole
     * halo (2.5 times that across, MapDrawnCell.HALO_ACROSS) inside the map, so the state drawn there is
     * in the still. No body is chosen, so no hot halo stands in for the state's own.
     */
    private fun requireCloseUp(name: String, id: String, drawn: MapDrawn): MapDrawnCell {
        val size = checkNotNull(mapNode()).size
        val w = size.width.toFloat(); val h = size.height.toFloat()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val index = ideaIndex(name, id)
        val cell = checkNotNull(drawn.cells.firstOrNull { it.index == index }) { "$id is not on the map close up for $name: ${drawn.cells}" }
        check(cell.across >= LEAST_DP * density) {
            "$id is not close for $name: ${cell.across} px across, under ${LEAST_DP * density} ($LEAST_DP dp), in $w x $h"
        }
        val half = cell.across * MapDrawnCell.HALO_ACROSS / 2
        check(cell.x - half >= 0f && cell.x + half <= w && cell.y - half >= 0f && cell.y + half <= h) {
            "$id's halo is not all on the map for $name: $cell, its halo $half px each side, in $w x $h"
        }
        check(drawn.chosen == -1 && !drawn.marked) { "A body is chosen in the close-up of $id for $name: ${drawn.chosen}" }
        return cell
    }

    /** The screen round an idea, its halo's square, as the latest still shows it. */
    private class IdeaCrop(val width: Int, val height: Int, val pixels: IntArray) {
        /**
         * The share of the pixels where a channel differs from [other]'s by more than [STEP], over the
         * square both hold round the idea (the smaller, centred on each); 0 when either is empty.
         */
        fun differs(other: IdeaCrop): Float {
            val cw = min(width, other.width); val ch = min(height, other.height)
            if (cw <= 0 || ch <= 0) return 0f
            val ax = (width - cw) / 2; val ay = (height - ch) / 2
            val bx = (other.width - cw) / 2; val by = (other.height - ch) / 2
            var count = 0
            for (y in 0 until ch) for (x in 0 until cw) {
                val a = pixels[(ay + y) * width + ax + x]; val b = other.pixels[(by + y) * other.width + bx + x]
                val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
                val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
                val db = abs((a and 0xFF) - (b and 0xFF))
                if (max(dr, max(dg, db)) > STEP) count++
            }
            return count.toFloat() / (cw * ch)
        }

        companion object {
            /** A channel's step, out of 255, that counts a pixel as different. */
            const val STEP = 8
            /**
             * The least share that tells a state's still from resting's. A halo's ring at its body's edge
             * alone, a resting idea's having none inside a cell, covers about 7% of the square.
             */
            const val DIFFERENT = 0.02f
        }
    }

    /** The screen round [cell], its halo's square, now: from the device's own screenshot, in the map's place on screen. */
    private fun ideaCrop(cell: MapDrawnCell): IdeaCrop {
        val at = checkNotNull(mapNode()).positionOnScreen
        val shot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot for the crop" }
        try {
            val half = (cell.across * MapDrawnCell.HALO_ACROSS / 2).toInt()
            val left = (at.x + cell.x).toInt() - half
            val top = (at.y + cell.y).toInt() - half
            val l = left.coerceIn(0, shot.width); val t = top.coerceIn(0, shot.height)
            val r = (left + 2 * half).coerceIn(l, shot.width); val b = (top + 2 * half).coerceIn(t, shot.height)
            val pixels = IntArray((r - l) * (b - t))
            if (pixels.isNotEmpty()) shot.getPixels(pixels, 0, r - l, l, t, r - l, b - t)
            return IdeaCrop(r - l, b - t, pixels)
        } finally {
            shot.recycle()
        }
    }

    /** S05i A6: Neurons' legend at its Cell states row (a still, mapmode-legend-cells-neurons-<light|dark>-<device>.png). */
    private fun shotLegendCells(dark: Boolean) {
        val theme = GraphTheme.NEURONS
        openLook(theme)
        press(hasText(theme.legendTitle) and hasClickAction())
        val sheet = MapPanelTags.legend(theme)
        compose.waitUntil(10_000) { shown(sheet) }
        SystemClock.sleep(1_000)
        compose.onNode(hasTestTag(sheet)).performTouchInput { swipeUp() }
        SystemClock.sleep(800)
        // a legend row is one item, its words its spoken description (LegendRowTile), as requireLegendTop finds Moon
        val name = "mapmode-legend-cells-neurons-${if (dark) "dark" else "light"}-$device"
        val cells = checkNotNull(MapLegend.of(theme).rows.firstOrNull { it.name == "Cell states" }) { "Neurons' legend has no Cell states row for $name" }
        val row = hasContentDescription(MapLegend.spoken(cells))
        check(compose.onAllNodes(row).fetchSemanticsNodes().size == 1) { "Neurons' legend shows no Cell states row, one item, for $name" }
        compose.onNode(row).performScrollTo()
        compose.waitForIdle()
        SystemClock.sleep(800)
        shot(name)
        ui.pressBack()
        try { compose.waitUntil(3_000) { !shown(sheet) } } catch (_: ComposeTimeoutException) { ui.pressBack() }
        compose.waitUntil(10_000) { !shown(sheet) }
        compose.waitForIdle()
    }

    /**
     * S05f A3: no name at rest; with [press], a finger on a body (the map's spot, the biggest crisp
     * body clear of the chrome) that moves past the slop at once turns the camera and takes the name
     * away, no card; one held there shows its name, one pill above it, and held past the pick-up then
     * moved it carries the body, its name kept on it, and no card comes (S05g2); then a tap on a body
     * chooses it, its card comes up and its name stays, one pill above it, still there a second later.
     */
    private fun requireNames(look: String, dark: Boolean, press: Boolean): Pair<Int, List<Float>> {
        val name = "mapmode-named-$look-${if (dark) "dark" else "light"}-$device"
        compose.waitForIdle()
        check(mapDrawn()?.names?.isEmpty() == true) { "A name shows at rest for $name: ${mapDrawn()?.names}" }
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        if (press) {
            val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop
            // moved past the slop with its down, before the pick-up: a turn, the name gone, no body moved
            val (_, quick) = spot(name)
            compose.onNode(map).performTouchInput { down(quick); moveBy(Offset(slop * 3f, 0f)) }
            try { compose.waitUntil(5_000) { mapDrawn()?.names?.isEmpty() == true } } catch (_: ComposeTimeoutException) {}
            val turning = mapDrawn()
            compose.onNode(map).performTouchInput { up() }
            check(turning?.names?.isEmpty() == true) { "The name stayed after the finger moved on for $name: ${turning?.names}" }
            check(turning?.grabbed == -1 && turning.moving == 0) { "A quick move picked a body up for $name: $turning" }
            compose.waitForIdle()
            check(!shown(MapPanelTags.PEEK)) { "A turn brought up a card for $name" }
            SystemClock.sleep(500) // the turn's last frame, so the spot is where the body now is
            // held: its name; past the pick-up and moved, the body goes with the finger and keeps its name (S05g2)
            val (index, at) = spot(name)
            var carried: MapDrawn? = null
            try {
                // the hold and the move in one go, 300 ms apart by the events' own times: past the pick-up, short of the options
                compose.onNode(map).performTouchInput { down(at); advanceEventTime(300); repeat(3) { moveBy(Offset(slop.toFloat(), 0f)) } }
                try { compose.waitUntil(5_000) { mapDrawn()?.let { it.names.isNotEmpty() && (it.dragged.getOrNull(0) ?: 0f) > at.x + slop } == true } } catch (_: ComposeTimeoutException) {}
                carried = mapDrawn()
                requireOnePill(index, "a finger held on a body, then moved, for $name")
            } finally {
                compose.onNode(map).performTouchInput { up() }
            }
            check(carried?.grabbed == index) { "A finger held on a body past the pick-up did not pick it up for $name: $carried" }
            compose.waitForIdle()
            check(!shown(MapPanelTags.PEEK)) { "A move brought up a card for $name" }
            requireHome(name)
            try { compose.waitUntil(5_000) { mapDrawn()?.names?.isEmpty() == true } } catch (_: ComposeTimeoutException) {}
            val home = checkNotNull(mapDrawn())
            check(home.names.isEmpty()) { "A name stayed after the carried body was let go for $name: ${home.names}" }
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
     * mapmode-chosen-<look>-<light|dark>-<device>.png. On an idea's card, Show links
     * (requireShowLinks); a Neurons cell's card is a folder's, Fly in and no Show links. A finger
     * held still past 0.55 s opens its options with the finger still down. Held and moved before then,
     * it picks the body up in Space and Neurons (requireDrag) and turns the camera in Performance,
     * opening nothing; moved with its down, before the pick-up, it turns the camera in every look and
     * moves no body (S05g2). A tap on
     * empty space lets it go, and the second of a double tap brings back the first view, [home]: in
     * Space the pose Recentre gave right after Got it, before any choice (the first view framed
     * without the card, S05d item 2); in Neurons the one framed above its card; in Performance the
     * one it opened on. It is Recentre's too.
     */
    private fun requireChoice(look: String, dark: Boolean, index: Int, before: List<Float>, home: List<Float>) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-chosen-$look-$mode-$device"
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val res = instrumentation.targetContext.resources
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
        // Show links is on an idea's card (a Space body, a Performance point); a Neurons cell's card is a
        // folder's, as iOS's (GraphTouchModel content): Open folder and Fly in, no Show links. The
        // Neurons' Show links is checked on an idea inside a cell, flown into (requireNeuronLinks)
        val chosenId = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)).ids[index]
        if (notes.any { it.id.equals(chosenId, ignoreCase = true) }) requireShowLinks(look, dark, index, chosen)
        else {
            check(look == "neurons") { "The chosen body ($chosenId) is no idea for $name" }
            check(!shown(MapPanelTags.PEEK_LINKS)) { "A cell's card offers Show links for $name" }
            compose.onNode(hasText(res.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).assertExists()
        }
        // a finger held still past the hold: the options, the finger still down. The hold's waits are the
        // map's touch coroutine's timeouts, which run on this rule's test clock, not the wall clock; a
        // wait polled on the wall clock moved that clock one frame a poll, so a slow emulator (the tablet
        // at about 3 frames a second) ran out its seconds short of 0.55 s. The clock is moved past the
        // hold itself (iOS's 0.55 s, scaled as the map scales it), then the options are waited for
        val holdMillis = ceil(MapTouchRules.HOLD_FOR_MENU * MapTouchRules.holdScale(ViewConfiguration.getLongPressTimeout().toLong()) * 1000).toLong()
        val (_, at) = spot(name)
        compose.onNode(map).performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(holdMillis)
        try { compose.waitUntil(5_000) { shown(MapPanelTags.MENU) } } catch (_: ComposeTimeoutException) {}
        val opened = shown(MapPanelTags.MENU)
        val holding = mapDrawn()
        compose.onNode(map).performTouchInput { up() }
        check(opened) { "A finger held still on a body opened no options for $name: held at $at for $holdMillis ms of the test clock, the map then $holding" }
        ui.pressBack()
        compose.waitUntil(10_000) { !shown(MapPanelTags.MENU) }
        val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop
        val menuClosed = checkNotNull(mapDrawn())
        check(menuClosed.grabbed == -1 && menuClosed.moving == 0) { "A body held still to its options was not put back for $name: $menuClosed" }
        if (look == "performance") {
            // held, then moved before the options: Performance's hold is options only, so the camera turns, nothing opens
            val (_, from) = spot(name)
            val still = checkNotNull(mapDrawn()).pose
            // past the pick-up time, short of the options', by the events' own times
            compose.onNode(map).performTouchInput { down(from); advanceEventTime(300); moveBy(Offset(slop * 4f, 0f)); moveBy(Offset(slop * 4f, 0f)); up() }
            compose.waitForIdle()
            SystemClock.sleep(500)
            check(!shown(MapPanelTags.MENU)) { "A hold that moved opened the options for $name" }
            val after = checkNotNull(mapDrawn())
            check(!same(still, after.pose)) { "A hold that moved did not turn the camera for $name" }
            check(after.grabbed == -1 && after.moving == 0) { "A hold that moved picked a point up in Performance for $name: $after" }
        } else {
            // held, then moved before the options: the body chosen now (the hold chose the one held) is picked up and moved (S05g2)
            val held = checkNotNull(mapDrawn()).chosen
            check(held >= 0) { "No body chosen to pick up for $name: ${mapDrawn()}" }
            requireDrag(held, "mapmode-drag-$look-$mode-$device", homes = look == "space" || !ValueAnimator.areAnimatorsEnabled())
        }
        // moved with its down, under the pick-up time: the camera turns in every look, and no body moves
        val (_, quick) = spot(name)
        val unturned = checkNotNull(mapDrawn()).pose
        compose.onNode(map).performTouchInput { down(quick); moveBy(Offset(slop * 4f, 0f)); moveBy(Offset(slop * 4f, 0f)); up() }
        compose.waitForIdle()
        SystemClock.sleep(500)
        check(!shown(MapPanelTags.MENU)) { "A quick move opened the options for $name" }
        val quickly = checkNotNull(mapDrawn())
        check(!same(unturned, quickly.pose)) { "A quick move did not turn the camera for $name" }
        check(quickly.grabbed == -1 && quickly.moving == 0) { "A quick move moved a body for $name: $quickly" }
        // empty space: a tap lets it go, two bring back the first view, the one Recentre frames: the
        // first-run card's cover while it is up, the view without it once it is put away (S05d item 2)
        val turnedDrawn = checkNotNull(mapDrawn())
        val turned = turnedDrawn.pose
        val empty = emptySpot(name)
        compose.onNode(map).performTouchInput { doubleClick(empty) }
        try {
            compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) && checkNotNull(mapDrawn()).let { it.homes > turnedDrawn.homes && !same(turned, it.pose) } }
        } catch (_: ComposeTimeoutException) {}
        check(!shown(MapPanelTags.PEEK)) { "A tap on empty space left the card up for $name" }
        SystemClock.sleep(500)
        compose.waitForIdle()
        val doubledDrawn = checkNotNull(mapDrawn())
        check(doubledDrawn.homes > turnedDrawn.homes) { "A double tap on empty space did not put the camera back at its first view for $name: $doubledDrawn" }
        val doubled = doubledDrawn.pose
        check(!same(turned, doubled)) { "A double tap on empty space left the turned camera where it was for $name: $turned" }
        check(same(home, doubled)) { "A double tap on empty space did not bring back the first view for $name: $home, now $doubled" }
        val recentred = recentred(name)
        check(same(doubled, recentred)) { "A double tap on empty space did not bring back Recentre's first view for $name: $doubled, Recentre $recentred" }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == -1 } } catch (_: ComposeTimeoutException) {}
        check(mapDrawn()?.chosen == -1) { "The body is still marked after the empty tap for $name: ${mapDrawn()}" }
    }

    /**
     * S05g2 A3, hold to move on body [index], where its name shows it (it is chosen) or at [at]: a finger
     * held 300 ms picks it up (MapDrawn.grabbed) and the camera stays; moved 8 slops in steps, the body
     * follows to within 2 dp of the finger's move (MapDrawn.dragged), its offset kept, the camera still
     * and no options; with [carries], a neighbour pulled or a member carried (carried ≥ 1); [member], a
     * body inside it, chosen, its name showing, off its home in the world within 10 % of the held body's
     * own offset once the finger has been still a moment (MapDrawn.chosenOffset against heldOffset), not
     * 1.85 times it. Mid-drag the held body's name shows and no other name lies over it; [member]'s gives
     * way while the cell is held, as on iOS. The hold and the moves go in one input, 300 ms apart by the
     * events' own times. A still, [still], mid-drag. Let go: no options, the card
     * and the choice as they were, nothing off home within the frames iOS's spring needs ([requireHome]),
     * and with [homes] (Space, or Neurons
     * with animations off, where a home stays put) the body within 1 dp of where it was picked up.
     * [carriedAtLeast]: how many bodies [carries] wants moved with it (S05h: a black hole's planets);
     * [members], each off its home in the world within 10 % of the held body's (MapDrawn.memberOffsets).
     */
    private fun requireDrag(index: Int, still: String, carries: Boolean = false, member: Int? = null, at: Offset? = null, homes: Boolean = true,
                            carriedAtLeast: Int = 1, members: List<Int> = emptyList()) {
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val slop = ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop.toFloat()
        fun named(i: Int) = mapDrawn()?.names?.firstOrNull { it.index == i }?.let { Offset(it.body[0], it.body[1]) }
        try { compose.waitUntil(10_000) { mapDrawn()?.let { it.grabbed == -1 && it.moving == 0 } == true && (at != null || named(index) != null) } } catch (_: ComposeTimeoutException) {}
        val from = at ?: checkNotNull(named(index)) { "Body $index shows no name to find it by for $still: ${mapDrawn()}" }
        val memberFrom = member?.let { m -> checkNotNull(named(m)) { "The member $m shows no name to find it by for $still: ${mapDrawn()}" } }
        val before = checkNotNull(mapDrawn())
        val card = shown(MapPanelTags.PEEK)
        val mapBounds = checkNotNull(mapNode()).boundsInRoot
        // a press on the card reaches the card, never the map: a body under it cannot be picked up
        val cardBounds = compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val fromInRoot = from + mapBounds.topLeft
        check(cardBounds == null || !cardBounds.contains(fromInRoot)) {
            "Body $index at $from (in the window $fromInRoot) lies under the card ($cardBounds), where a press reaches the card, not the map, for $still"
        }
        val width = mapBounds.width
        val way = if (from.x > width / 2) -1f else 1f
        val steps = 8
        val tolerance = 2f * density
        try {
            // the hold and the moves in one go, 300 ms apart by the events' own times: past the pick-up, short of
            // the options. Not picked up, the moves would turn the camera, which the pose check below catches
            compose.onNode(map).performTouchInput { down(from); advanceEventTime(300); repeat(steps) { moveBy(Offset(way * slop, 0f)) } }
            val start = from // the body is drawn where its name is found, and keeps its offset from the finger
            val want = Offset(start.x + way * slop * steps, start.y)
            fun near(d: List<Float>) = d.size == 2 && abs(d[0] - want.x) <= tolerance && abs(d[1] - want.y) <= tolerance
            try { compose.waitUntil(10_000) { mapDrawn()?.dragged?.let(::near) == true } } catch (_: ComposeTimeoutException) {}
            SystemClock.sleep(1_500) // the finger still: what it pulls or carries settles
            val moved = checkNotNull(mapDrawn())
            check(moved.grabbed == index) { "A finger held 300 ms on body $index did not pick it up for $still: $moved" }
            check(near(moved.dragged)) {
                "Body $index did not follow the finger to within 2 dp for $still: from $start, wanted $want, drawn ${moved.dragged}"
            }
            check(same(before.pose, moved.pose)) { "The drag turned the camera for $still: ${before.pose}, then ${moved.pose}" }
            check(!shown(MapPanelTags.MENU)) { "The drag opened the options for $still" }
            // the held body's name is up and no other name lies over it: a body held takes the chosen
            // slot, so a chosen member's name gives way till the let-go (iOS's grab, GraphMotion.swift:1253, :2186)
            val heldPill = checkNotNull(moved.names.firstOrNull { it.index == index }) { "Body $index shows no name while it is held for $still: ${moved.names}" }
            fun crosses(a: List<Float>, b: List<Float>) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
            val over = moved.names.filter { it.index != index && crosses(it.box, heldPill.box) }
            check(over.isEmpty()) { "A name lies over held body $index's (${heldPill.box}) for $still: $over" }
            if (member != null) check(moved.names.none { it.index == member }) { "The chosen member $member kept its name while the cell was held for $still: ${moved.names}" }
            if (carries) check(moved.carried >= carriedAtLeast) { "Body $index pulled or carried ${moved.carried}, not $carriedAtLeast or more, for $still: $moved" }
            // the carry in the world, each body's own offset from its home, so neither's depth on screen counts
            if (member != null) {
                check(moved.chosen == member) { "The member $member is not the chosen body mid-drag for $still: $moved" }
                val cell = moved.heldOffset; val inside = moved.chosenOffset
                check(cell.size == 3 && inside.size == 3) { "No offsets for the cell ($cell) or the member ($inside) mid-drag for $still" }
                val gap = sqrt(cell.indices.sumOf { ((inside[it] - cell[it]) * (inside[it] - cell[it])).toDouble() })
                val own = sqrt(cell.sumOf { (it * it).toDouble() })
                check(own > 0.0 && gap <= 0.1 * own) {
                    "The member $member is off home by $inside while the cell is by $cell (within 10 % wanted) for $still"
                }
            }
            // S05h: every member of the held body moves with it, each by its own offset in the world
            if (members.isNotEmpty()) {
                val held = moved.heldOffset
                check(held.size == 3) { "No offset for held body $index mid-drag for $still: $moved" }
                val own = sqrt(held.sumOf { (it * it).toDouble() })
                val offsets = moved.memberOffsets.chunked(4).associate { it[0].toInt() to it.drop(1) }
                for (m in members) {
                    val its = checkNotNull(offsets[m]) { "Member $m of held body $index is not among its members mid-drag for $still: ${moved.memberOffsets}" }
                    val gap = sqrt(held.indices.sumOf { ((its[it] - held[it]) * (its[it] - held[it])).toDouble() })
                    check(own > 0.0 && gap <= 0.1 * own) {
                        "Member $m is off home by $its while held body $index is by $held (within 10 % wanted) for $still"
                    }
                }
            }
            shot(still)
        } finally {
            compose.onNode(map).performTouchInput { up() }
        }
        compose.waitForIdle()
        SystemClock.sleep(500)
        check(!shown(MapPanelTags.MENU)) { "The let-go opened the options for $still" }
        check(shown(MapPanelTags.PEEK) == card) { "The let-go changed the card (up before: $card) for $still" }
        check(mapDrawn()?.chosen == before.chosen) { "The drag changed the choice for $still: ${before.chosen}, now ${mapDrawn()?.chosen}" }
        // nothing is kept: home again, with animations off as on CI critically damped
        val home = requireHome(still)
        check(home.carried == 0) { "A pulled or carried body is still off home for $still: $home" }
        check(same(before.pose, home.pose)) { "The let-go moved the camera for $still" }
        if (homes) {
            val back = if (at != null && member == null) null else named(index)
            if (back != null) check((back - from).getDistance() <= density) { "Body $index came back to $back, not within 1 dp of $from, for $still" }
            if (member != null && memberFrom != null) {
                val again = checkNotNull(named(member)) { "The member $member shows no name after the let-go for $still" }
                check((again - memberFrom).getDistance() <= density) { "The member $member came back to $again, not within 1 dp of $memberFrom, for $still" }
            }
        }
    }

    /**
     * S05g2 A3: after a let-go, every body home and none held, within the frames iOS's spring gives the
     * let-go (MapDrawn.restFrames, MapDrag.framesToRest: 0.05 s a frame, so a slow emulator gets as many
     * frames as a phone, not as many seconds); 180 s of the clock at most, against a hang. A red names the
     * frames counted since the let-go and the latest frame's interval, the frame rate read off directly.
     */
    private fun requireHome(name: String): MapDrawn {
        try {
            compose.waitUntil(180_000) { mapDrawn()?.let { (it.grabbed == -1 && it.moving == 0) || it.letGoFrames > it.restFrames } == true }
        } catch (_: ComposeTimeoutException) {}
        val home = checkNotNull(mapDrawn())
        check(home.restFrames > 0) { "No let-go was counted for $name: $home" }
        check(home.grabbed == -1 && home.moving == 0) {
            "Bodies are still off home ${home.letGoFrames} frames after the let-go, where iOS's spring needs ${home.restFrames} at most " +
                "(the latest frame ${home.frameMs} ms after the one before) for $name: $home"
        }
        return home
    }

    /**
     * S05g A3: Show links on the card of [index], chosen and drawn as [chosen]: the map fades what is
     * outside its neighbourhood (Performance fades no point, its own links drawn) and keeps its lit
     * links, the button reads All links, a still mapmode-links-<look>-...; All links brings everything back.
     */
    private fun requireShowLinks(look: String, dark: Boolean, index: Int, chosen: MapDrawn,
                                 still: String = "mapmode-links-$look-${if (dark) "dark" else "light"}-$device") {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-chosen-$look-$mode-$device"
        val res = instrumentation.targetContext.resources
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
        shot(still)
        // All links
        compose.onNode(hasTestTag(MapPanelTags.PEEK_LINKS)).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.focus == false } } catch (_: ComposeTimeoutException) {}
        val back = checkNotNull(mapDrawn())
        check(!back.focus && back.faded == 0 && back.focusLinks == 0 && back.chosen == index) { "All links did not bring everything back for $name: $back" }
    }

    /**
     * S05g A3 in Neurons: the cell requireNames chose ([cell]) is chosen again, its card's Fly in opens
     * it on the map, and an idea inside with a link, chosen, has Show links (requireShowLinks). A tap
     * on empty space then lets it go and Recentre closes the cell, the map as Performance finds it.
     * The choices are handed in as the map's hit test hands them on (MapTapKey), the cell's and the
     * ideas' places not being reported.
     */
    private fun requireNeuronLinks(dark: Boolean, cell: Int) {
        val name = "mapmode-chosen-neurons-${if (dark) "dark" else "light"}-$device"
        val res = instrumentation.targetContext.resources
        val closed = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        val cellId = closed.ids[cell]
        val folder = folders.firstOrNull { it.id.equals(cellId, ignoreCase = true) }
        val idea = checkNotNull(notes.firstOrNull { n -> n.folderId == folder?.id && (n.links.isNotEmpty() || notes.any { n.id in it.links }) }) {
            "No idea with a link in the chosen cell ($cellId) for $name"
        }
        compose.runOnIdle { closed.tap(cell) }
        compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) }
        compose.onNode(hasText(res.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        fun ideaAt(): Int = mapNode()?.config?.getOrNull(MapTapKey)?.ids?.indexOfFirst { it.equals(idea.id, ignoreCase = true) } ?: -1
        compose.waitUntil(30_000) { ideaAt() >= 0 }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
        val index = ideaAt()
        val open = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        compose.runOnIdle { open.tap(index) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == index } } catch (_: ComposeTimeoutException) {}
        val chosen = checkNotNull(mapDrawn())
        check(shown(MapPanelTags.PEEK) && chosen.chosen == index) { "The idea ${idea.title} ($index) inside the open cell is not chosen for $name: $chosen" }
        requireShowLinks("neurons", dark, index, chosen)
        // S05g2 A3: the open cell, moved, carries what is inside it, the idea chosen there among it. The cell
        // is chosen first, to find where it is drawn by its name, then the idea, whose name shows where it is
        val cellIndex = open.ids.indexOfFirst { it.equals(cellId, ignoreCase = true) }
        check(cellIndex >= 0) { "The open cell ($cellId) is not on the map for $name" }
        fun named(i: Int) = mapDrawn()?.names?.firstOrNull { it.index == i }?.let { Offset(it.body[0], it.body[1]) }
        compose.runOnIdle { open.tap(cellIndex) }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == cellIndex && named(cellIndex) != null } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        val cellAt = checkNotNull(named(cellIndex)) { "The open cell ($cellIndex) shows no name to find it by for $name: ${mapDrawn()}" }
        compose.runOnIdle { open.tap(index) }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == index && named(index) != null } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000)
        requireDrag(cellIndex, "mapmode-drag-cell-neurons-${if (dark) "dark" else "light"}-$device", carries = true, member = index,
            at = cellAt, homes = !ValueAnimator.areAnimatorsEnabled())
        compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(emptySpot(name)) }
        compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitUntil(30_000) { ideaAt() < 0 }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
    }

    /**
     * S05g A6 in Space: a chosen sun and its links' plasma. The fixture has no body with the five
     * links a sun takes by itself (SpaceStyle.forLinks), so Look's Suns, chosen as a person would,
     * makes the bodies suns; Recentre frames the new scene. The idea with the most links is handed
     * in as the map's hit test hands it on (MapTapKey), only to find where it is drawn and to read
     * its style off the map: its card's kind is the map's own (MapPeek.spaceRole of the scene's
     * styles), a Star. A tap on empty space lets it go, and a tap on that spot chooses it: marked,
     * its links lit, nothing faded, the camera put as requireChoice requires with animations off;
     * a still, mapmode-chosen-sun-space-<light|dark>-<device>.png. A tap on empty space lets it go
     * and Universe is chosen again, the map as Neurons finds it.
     */
    private fun requireSun(dark: Boolean) {
        val name = "mapmode-chosen-sun-space-${if (dark) "dark" else "light"}-$device"
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val star = instrumentation.targetContext.resources.getString(R.string.map_role_star)
        chooseSpaceStyle(SpaceStyle.SUN.label, name)
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitForIdle()
        SystemClock.sleep(500)
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        fun links(id: String) = notes.sumOf { n -> (if (n.id.equals(id, ignoreCase = true)) n.links.size else 0) + n.links.count { it.equals(id, ignoreCase = true) } }
        val index = tap.ids.indices.maxBy { links(tap.ids[it]) }
        check(links(tap.ids[index]) > 0) { "No idea with a link on the map for $name: ${tap.ids}" }
        fun kind() = compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        compose.runOnIdle { tap.tap(index) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.let { it.chosen == index && it.names.any { n -> n.index == index } } == true } } catch (_: ComposeTimeoutException) {}
        check(", $star · " in kind()) { "The body with the most links ($index) is not a sun by the map's styles for $name: its card reads \"${kind()}\"" }
        SystemClock.sleep(1_000) // the ease's 0.45 s, done: the body is where it stays
        val drawnAt = checkNotNull(mapDrawn()).names.firstOrNull { it.index == index }
        checkNotNull(drawnAt) { "The chosen sun ($index) shows no name to find it by for $name: ${mapDrawn()}" }
        val at = Offset(drawnAt.body[0], drawnAt.body[1])
        val bounds = checkNotNull(mapNode()).boundsInRoot
        val strip = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot
        check(at.x in 0f..bounds.width && at.y in (strip.bottom - bounds.top)..bounds.height) {
            "The sun ($index) is drawn off the map or under the top strip for $name: $at in ${bounds.width}x${bounds.height}, the strip's bottom ${strip.bottom - bounds.top}"
        }
        val empty = emptySpot(name)
        compose.onNode(map).performTouchInput { click(empty) }
        try { compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == -1 } } catch (_: ComposeTimeoutException) {}
        check(!shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == -1) { "A tap on empty space did not let the sun go for $name: ${mapDrawn()}" }
        SystemClock.sleep(500) // past a double tap's pairing
        // the tap
        val before = checkNotNull(mapDrawn()).pose
        compose.onNode(map).performTouchInput { click(at) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.let { it.chosen == index && it.marked && it.lit > 0 } == true } } catch (_: ComposeTimeoutException) {}
        val chosen = checkNotNull(mapDrawn())
        check(shown(MapPanelTags.PEEK) && chosen.chosen == index && chosen.marked) { "A tap on the sun ($index) at $at did not choose and mark it for $name: $chosen" }
        check(chosen.lit > 0) { "The chosen sun's links are not lit for $name: $chosen" }
        check(!chosen.focus && chosen.faded == 0) { "Something fades with no Show links for $name: $chosen" }
        check(", $star · " in kind()) { "The tapped body's card is not a sun's for $name: \"${kind()}\"" }
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        // with animations off the camera stays put; with them on it has eased to the sun already, at the hand-in
        if (!ValueAnimator.areAnimatorsEnabled()) {
            val eased = checkNotNull(mapDrawn()).pose
            check(same(before, eased)) { "The camera moved with animations off for the chosen sun for $name: $before, then $eased" }
        }
        shot(name)
        // S05g2 A3: under one single style the sun's links pull its neighbours as it moves
        requireDrag(index, "mapmode-drag-sun-space-${if (dark) "dark" else "light"}-$device", carries = true, homes = true)
        compose.onNode(map).performTouchInput { click(emptySpot(name)) }
        compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        chooseSpaceStyle("Universe", name)
    }

    /**
     * S05h A3: the Universe, Space's default look, read from MapDrawn with animations off. At rest its
     * bodies by role are UniversePlan's for the fixture (as IdeasSpace builds it: every note, folder and
     * link, the standard Link length), no moon's link to its planet is drawn (near and far links at most
     * the plan's links of other kinds) and arched and far links are both drawn: a still,
     * mapmode-universe-<light|dark>-<device>.png. The fixture's Garden folder is a black hole (no folder
     * here has a subfolder, so it has no star; iOS's sample flies into its Inguinal star): chosen, then
     * Fly in on its card, it is flown in to, every body of its system drawn inside the map below the top
     * strip (mapmode-fly-space-...). Still flown in, with a planet inside it chosen, the black hole held
     * 300 ms and moved 8 slops carries at least its planets, each planet off home within 10 % of the black
     * hole's move (requireDrag's members, and the chosen one as its member; mapmode-drag-star-space-...),
     * and it is still flown in to. Fly in frames the system above the foot, clear of the card; the first
     * view, iOS's tilted fit, can put the black hole under the chosen planet's card, which a press there
     * reaches instead of the map. Then Recentre brings back the first view, nothing flown in to, and a tap
     * on empty space lets it go, the map as Neurons finds it.
     */
    private fun requireUniverse(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-universe-$mode-$device"
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val book = IdeaBook(notes, folders)
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitForIdle()
        SystemClock.sleep(500)
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && (mapDrawn()?.roles?.size ?: 0) > 0 } } catch (_: ComposeTimeoutException) {}
        val rest = checkNotNull(mapDrawn())
        val home = rest.pose
        val roles = UniverseRole.values().map { r -> plan.bodies.count { it.role == r } }
        check(rest.roles == roles) { "The map's bodies by role (${rest.roles}) are not UniversePlan's ($roles, in ${UniverseRole.values().toList()}) for $name: $rest" }
        val drawable = plan.links.count { it.kind != 3 }
        check(rest.nearLinks + rest.farLinks <= drawable) {
            "More links drawn (${rest.nearLinks} near, ${rest.farLinks} far) than the plan has other than a moon's to its planet ($drawable) for $name: $rest"
        }
        check(rest.archedLinks > 0 && rest.farLinks > 0) { "No arched or no far link drawn for $name: $rest" }
        check(home.size == 6) { "No camera pose for $name: $rest" }
        shot(name)
        // Fly in on the Garden black hole, chosen as the map's hit test hands it on
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        val g = tap.ids.indexOfFirst { it.equals(garden.id, ignoreCase = true) }
        check(g >= 0 && plan.bodies.getOrNull(g)?.id.equals(garden.id, ignoreCase = true) && plan.bodies[g].role == UniverseRole.GALAXY) {
            "The Garden folder is not the plan's black hole $g on the map for $name: ${tap.ids}"
        }
        val planets = plan.bodies.indices.filter { plan.bodies[it].parent == g }
        check(planets.isNotEmpty()) { "The Garden black hole has no planets in the plan for $name" }
        fun named(i: Int) = mapDrawn()?.names?.firstOrNull { it.index == i }?.let { Offset(it.body[0], it.body[1]) }
        compose.runOnIdle { tap.tap(g) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == g && named(g) != null } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == g) { "The Garden black hole ($g) is not chosen with its card for $name: ${mapDrawn()}" }
        val fly = "mapmode-fly-space-$mode-$device"
        val flyIn = instrumentation.targetContext.resources.getString(R.string.map_peek_fly_in)
        compose.onNode(hasText(flyIn) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.flown == g } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_500) // the flight's 0.8 s, or at once with animations off
        val flown = checkNotNull(mapDrawn())
        check(flown.flown == g) { "Fly in on the Garden black hole ($g) did not fly in to it for $fly: $flown" }
        val pose = flown.pose
        check(pose.size == 6 && !same(home, pose)) { "Fly in did not move the camera for $fly: $home, then $pose" }
        val bounds = checkNotNull(mapNode()).boundsInRoot
        val stripBottom = compose.onNode(hasTestTag(MapPanelTags.TOP_STRIP)).fetchSemanticsNode().boundsInRoot.bottom - bounds.top
        // Space's field and, on a wide map, its quarter turn (S05j item 3): the pose carries neither
        val camera = OrbitCamera(Vec3(pose[0], pose[1], pose[2]), pose[3], pose[4], pose[5],
            MapFirstView.radians(MapFirstView.TILTED_FIELD_DEGREES), MapFirstView.roll(bounds.width, bounds.height))
        val centre = plan.bodies[g].home
        val outside = plan.systems[g].mapNotNull { p ->
            val at = camera.project(centre + p, bounds.width, bounds.height)
            if (at == null || at.first !in 0f..bounds.width || at.second !in stripBottom..bounds.height) (p to at) else null
        }
        check(plan.systems[g].isNotEmpty() && outside.isEmpty()) {
            "Bodies of the Garden black hole's system are drawn outside the map (${bounds.width}x${bounds.height}, the strip's bottom $stripBottom) for $fly: $outside"
        }
        shot(fly)
        // the black hole moved while flown in, a planet inside it chosen: found by their names, as Neurons'
        // cell and its idea are
        val drag = "mapmode-drag-star-space-$mode-$device"
        // still chosen after Fly in: a second tap on the chosen body would open its folder,
        // which on a phone takes the map's place (MapSelection, as iOS)
        if (mapDrawn()?.chosen != g) compose.runOnIdle { tap.tap(g) }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == g && named(g) != null } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        val holeAt = checkNotNull(named(g)) { "The Garden black hole ($g) shows no name to find it by for $drag: ${mapDrawn()}" }
        val planet = planets.maxBy { i -> named(i)?.let { (it - holeAt).getDistance() } ?: 0f }
        compose.runOnIdle { tap.tap(planet) }
        try { compose.waitUntil(10_000) { mapDrawn()?.chosen == planet && named(planet) != null } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(1_000)
        check(mapDrawn()?.chosen == planet) { "The planet $planet inside the Garden black hole is not chosen for $drag: ${mapDrawn()}" }
        check(mapDrawn()?.flown == g) { "Choosing the planet $planet left the Garden black hole ($g) not flown in to for $drag: ${mapDrawn()}" }
        requireDrag(g, drag, carries = true, member = planet, at = holeAt, homes = !ValueAnimator.areAnimatorsEnabled(),
            carriedAtLeast = planets.size, members = planets)
        check(mapDrawn()?.flown == g) { "The drag left the Garden black hole ($g) not flown in to for $drag: ${mapDrawn()}" }
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        try { compose.waitUntil(10_000) { mapDrawn()?.let { it.flown == -1 && same(home, it.pose) } == true } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(500)
        val back = checkNotNull(mapDrawn())
        check(back.flown == -1 && same(home, back.pose)) { "Recentre did not bring back the first view after Fly in for $fly: $home, now ${back.pose}; $back" }
        // the pose comes home at once, the bodies' box only with the next frame drawn (the tablet's came
        // 800 ms apart): the test thread sleeps, so the map can draw, until the box lies inside the map
        val homeBy = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < homeBy && mapDrawn()?.let { boxInside(it) } != true) SystemClock.sleep(250)
        check(mapDrawn()?.let { boxInside(it) } == true) { "No frame drawn at the first view within 10 s of Recentre for $drag: ${mapDrawn()}" }
        compose.onNode(map).performTouchInput { click(emptySpot(drag)) }
        compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
    }

    /**
     * S05h2 A3, with animations off as on CI: under the Universe a fling on empty space throws no
     * sparks (MapDrawn.sparksTotal stays 0). The fixture's idea with the most links in UniversePlan
     * (kinds 0-2; a moon's link to its planet is not drawn), chosen through the map's hit test and
     * deleted from its options, dies once (diedTotal up by 1) with each of those links
     * (dyingLinksTotal up by their count), and no body is dying within 2 s. Undo brings it back, so
     * the checks after this one find the fixture whole. It takes no still.
     */
    private fun requireStillDeath(dark: Boolean) {
        val name = "mapmode-death-${if (dark) "dark" else "light"}-$device"
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        val res = instrumentation.targetContext.resources
        val book = IdeaBook(notes, folders)
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitForIdle()
        try { compose.waitUntil(30_000) { framed(mapDrawn()) && mapDrawn()?.box?.size == 4 } } catch (_: ComposeTimeoutException) {}
        val before = checkNotNull(mapDrawn())
        check(before.sparksTotal == 0L && before.sparks == 0) { "Sparks were thrown with animations off before the fling for $name: $before" }
        // a fling across empty space, turning the camera fast
        val from = emptySpot(name)
        val width = checkNotNull(mapNode()).boundsInRoot.width
        val to = Offset(if (from.x > width / 2) from.x - width * 0.4f else from.x + width * 0.4f, from.y)
        compose.onNode(map).performTouchInput { swipe(from, to, 120) }
        compose.waitForIdle()
        SystemClock.sleep(1_000)
        val flung = checkNotNull(mapDrawn())
        check(flung.sparksTotal == 0L && flung.sparks == 0) { "A fling threw sparks with animations off for $name: $flung" }
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitForIdle()
        SystemClock.sleep(500)
        // the idea with the most links the map draws
        fun linksOf(i: Int) = plan.links.count { it.kind != 3 && (it.a == i || it.b == i) }
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $name" }
        val ideas = notes.mapNotNull { n -> tap.ids.indexOfFirst { it.equals(n.id, ignoreCase = true) }.takeIf { it >= 0 } }
        val idea = checkNotNull(ideas.maxByOrNull(::linksOf)) { "No idea of the fixture is on the map for $name: ${tap.ids}" }
        val ideaId = tap.ids[idea]
        check(plan.bodies.getOrNull(idea)?.id.equals(ideaId, ignoreCase = true)) { "Body $idea on the map ($ideaId) is not the plan's ${plan.bodies.getOrNull(idea)?.id} for $name" }
        val links = linksOf(idea)
        check(links > 0) { "The fixture's most linked idea $ideaId has no link the map draws for $name: ${plan.links}" }
        compose.runOnIdle { tap.tap(idea) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == idea } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.PEEK) && mapDrawn()?.chosen == idea) { "The idea $idea ($ideaId) is not chosen with its card for $name: ${mapDrawn()}" }
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        // its options, from its card's More, then Delete
        compose.onNode(hasContentDescription(res.getString(R.string.map_peek_more)) and hasClickAction() and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        try { compose.waitUntil(5_000) { shown(MapPanelTags.MENU) } } catch (_: ComposeTimeoutException) {}
        check(shown(MapPanelTags.MENU)) { "More on idea $idea's card ($ideaId) opened no options for $name: ${mapDrawn()}" }
        val totals = checkNotNull(mapDrawn())
        val delete = hasText(res.getString(R.string.map_menu_delete)) and hasAnyAncestor(hasTestTag(MapPanelTags.MENU))
        compose.onNode(delete).performClick()
        try { compose.waitUntil(30_000) { (mapDrawn()?.diedTotal ?: 0) > totals.diedTotal } } catch (_: ComposeTimeoutException) {}
        val seen = SystemClock.uptimeMillis()
        val died = checkNotNull(mapDrawn())
        check(died.diedTotal == totals.diedTotal + 1 && died.dyingLinksTotal == totals.dyingLinksTotal + links) {
            "Deleting idea $ideaId counted ${died.diedTotal - totals.diedTotal} deaths and ${died.dyingLinksTotal - totals.dyingLinksTotal} dying links, " +
                "not 1 and its $links links in the plan, for $name: before $totals, then $died"
        }
        try { compose.waitUntil(2_000) { mapDrawn()?.dying == 0 } } catch (_: ComposeTimeoutException) {}
        val after = checkNotNull(mapDrawn())
        check(after.dying == 0) { "A body was still dying ${SystemClock.uptimeMillis() - seen} ms after the death was counted, animations off, for $name: $after" }
        check(after.sparksTotal == 0L) { "The death threw sparks with animations off for $name: $after" }
        // Undo: the banner shows it (kept up by the launch's UNDO_STAYS), and it brings the idea back, on the map again
        val undo = hasText(res.getString(R.string.undo_action)) and hasClickAction()
        try { compose.waitUntil(5_000) { compose.onAllNodes(undo).fetchSemanticsNodes().size == 1 } } catch (_: ComposeTimeoutException) {}
        check(compose.onAllNodes(undo).fetchSemanticsNodes().size == 1) { "The banner shows no Undo after idea $ideaId was deleted for $name" }
        compose.onNode(undo).performClick()
        try {
            compose.waitUntil(30_000) { mapNode()?.config?.getOrNull(MapTapKey)?.ids?.any { it.equals(ideaId, ignoreCase = true) } == true && framed(mapDrawn()) }
        } catch (_: ComposeTimeoutException) {}
        check(mapNode()?.config?.getOrNull(MapTapKey)?.ids?.any { it.equals(ideaId, ignoreCase = true) } == true) { "Undo did not bring idea $ideaId back to the map for $name" }
        if (shown(MapPanelTags.PEEK)) {
            compose.onNode(map).performTouchInput { click(emptySpot(name)) }
            compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        }
    }

    /**
     * S05h A3: Space's legend at its top, as it opens: the ladder, one item, drawn whole on the screen
     * inside the sheet (mapmode-legend-top-space-<light|dark>-<device>.png); its Moon row is there and,
     * scrolled to, inside the sheet too.
     */
    private fun requireLegendTop(dark: Boolean) {
        val name = "mapmode-legend-top-space-${if (dark) "dark" else "light"}-$device"
        val content = MapLegend.of(GraphTheme.SPACE)
        val sheet = compose.onNode(hasTestTag(MapPanelTags.legend(GraphTheme.SPACE))).fetchSemanticsNode().boundsInRoot
        val ladders = compose.onAllNodes(hasContentDescription(content.ladderLabel)).fetchSemanticsNodes()
        check(content.ladderLabel.isNotEmpty() && ladders.size == 1) { "Space's legend shows ${ladders.size} ladders, not one, for $name" }
        val ladder = ladders.single().boundsInRoot
        val screen = instrumentation.targetContext.resources.displayMetrics
        check(ladder.top >= sheet.top && ladder.bottom <= minOf(sheet.bottom, screen.heightPixels.toFloat()) && ladder.left >= 0f && ladder.right <= screen.widthPixels) {
            "Space's ladder ($ladder) is not whole on the screen inside its sheet ($sheet) at the legend's top for $name"
        }
        shot(name)
        val moon = checkNotNull(content.rows.firstOrNull { it.name == "Moon" }) { "Space's legend has no Moon row for $name" }
        val row = hasContentDescription(MapLegend.spoken(moon))
        check(compose.onAllNodes(row).fetchSemanticsNodes().size == 1) { "Space's legend shows no Moon row, one item, for $name" }
        compose.onNode(row).performScrollTo()
        compose.waitForIdle()
        val at = compose.onNode(row).fetchSemanticsNode().boundsInRoot
        val now = compose.onNode(hasTestTag(MapPanelTags.legend(GraphTheme.SPACE))).fetchSemanticsNode().boundsInRoot
        check(at.top >= now.top - 1f && at.bottom <= now.bottom + 1f) { "Space's Moon row ($at) is not inside its sheet ($now), scrolled to, for $name" }
    }

    /**
     * Look's main style set to [label] in Space's Look menu, as a person would; the map builds its
     * scene again (a new MapDrawn.build) and draws it.
     */
    private fun chooseSpaceStyle(label: String, name: String) {
        val built = checkNotNull(mapDrawn()).build
        val look = hasContentDescription("Look") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, GraphTheme.SPACE.title)
        compose.onNode(look).performClick()
        val item = hasText(label) and hasClickAction()
        compose.waitUntil(10_000) { compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(item).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(item).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(30_000) { mapDrawn()?.build?.let { it != built } == true }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) && mapDrawn()?.box?.size == 4 } } catch (_: ComposeTimeoutException) {}
        val drawn = mapDrawn()
        check(framed(drawn) && drawn?.box?.size == 4) { "Space did not draw its scene again with $label for $name: $drawn" }
    }

    /**
     * S05g A6 in Neurons: a cell with bridges, Garden ('Paint the shed green' links to 'Buy brushes',
     * out of its folder), found by its folder's id among the map's (MapTapKey) and handed in as the
     * map's hit test hands it on. It is marked and its bridges lit; a still,
     * mapmode-chosen-bridges-neurons-<light|dark>-<device>.png. Its card is a folder's (Fly in, no
     * Show links, as requireChoice requires), so Fly in opens it and the idea that bridges out,
     * chosen, has Show links (requireShowLinks: faded > 0, its lit links kept), a still
     * mapmode-links-bridges-neurons-<light|dark>-<device>.png. A tap on empty space lets it go and
     * Recentre closes the cell, the map as Performance finds it.
     */
    private fun requireNeuronBridges(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val name = "mapmode-chosen-bridges-neurons-$mode-$device"
        val res = instrumentation.targetContext.resources
        val garden = folders.first { it.name == "Garden" }
        val idea = checkNotNull(notes.firstOrNull { n ->
            n.folderId == garden.id && n.links.any { l -> notes.any { it.id == l && it.folderId != garden.id } }
        }) { "No idea in Garden links out of it for $name" }
        fun cellAt(): Int = mapNode()?.config?.getOrNull(MapTapKey)?.ids?.indexOfFirst { it.equals(garden.id, ignoreCase = true) } ?: -1
        compose.waitUntil(30_000) { cellAt() >= 0 }
        val cell = cellAt()
        val closed = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        compose.runOnIdle { closed.tap(cell) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.let { it.chosen == cell && it.marked && it.lit > 0 } == true } } catch (_: ComposeTimeoutException) {}
        val chosen = checkNotNull(mapDrawn())
        check(shown(MapPanelTags.PEEK) && chosen.chosen == cell && chosen.marked) { "The Garden cell ($cell) is not chosen and marked for $name: $chosen" }
        check(chosen.lit > 0) { "The Garden cell's bridges are not lit for $name: $chosen" }
        check(!chosen.focus && chosen.faded == 0) { "Something fades with no Show links for $name: $chosen" }
        SystemClock.sleep(1_000) // the ease's 0.45 s, done
        shot(name)
        check(!shown(MapPanelTags.PEEK_LINKS)) { "A cell's card offers Show links for $name" }
        compose.onNode(hasText(res.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))).performClick()
        fun ideaAt(): Int = mapNode()?.config?.getOrNull(MapTapKey)?.ids?.indexOfFirst { it.equals(idea.id, ignoreCase = true) } ?: -1
        compose.waitUntil(30_000) { ideaAt() >= 0 }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
        val index = ideaAt()
        val open = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey))
        compose.runOnIdle { open.tap(index) }
        try { compose.waitUntil(10_000) { shown(MapPanelTags.PEEK) && mapDrawn()?.let { it.chosen == index && it.marked && it.lit > 0 } == true } } catch (_: ComposeTimeoutException) {}
        val chosenIdea = checkNotNull(mapDrawn())
        check(shown(MapPanelTags.PEEK) && chosenIdea.chosen == index && chosenIdea.marked && chosenIdea.lit > 0) {
            "The idea ${idea.title} ($index) inside Garden is not chosen, marked and lit for $name: $chosenIdea"
        }
        requireShowLinks("neurons", dark, index, chosenIdea, still = "mapmode-links-bridges-neurons-$mode-$device")
        compose.onNode(SemanticsMatcher.keyIsDefined(MapDrawnKey)).performTouchInput { click(emptySpot(name)) }
        compose.waitUntil(10_000) { !shown(MapPanelTags.PEEK) }
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        compose.waitUntil(30_000) { ideaAt() < 0 }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
    }

    /** Two camera poses alike, each value within a thousandth of its size (at least 1). */
    private fun same(a: List<Float>, b: List<Float>) = a.size == 6 && b.size == 6 && a.indices.all { abs(a[it] - b[it]) <= 1e-3f * max(1f, abs(a[it])) }

    /** The drawn bodies' box lies inside the map: a frame drawn at the first view, not flown in. */
    private fun boxInside(drawn: MapDrawn) = drawn.box.size == 4 && drawn.box[0] >= 0f && drawn.box[1] >= 0f &&
        drawn.box[2] <= drawn.width && drawn.box[3] <= drawn.height

    /**
     * Recentre pressed, and the camera's pose once the map's node shows it at its first view (S05i
     * repair 5): MapDrawn.homes counts each return home after the camera has moved, so the pose read
     * with the new count is the camera's there. A pose read at a set time after the press could be the
     * node's last record from before it, where the GL's next frame had not yet reached the page (the
     * tablet, at a few frames a second, read Space's first view with the card's cover so).
     */
    private fun recentred(name: String): List<Float> {
        val before = checkNotNull(mapDrawn()).homes
        compose.onNode(hasContentDescription("Recentre") and hasClickAction()).performClick()
        try { compose.waitUntil(10_000) { (mapDrawn()?.homes ?: before) > before } } catch (_: ComposeTimeoutException) {}
        SystemClock.sleep(500)
        compose.waitForIdle()
        val drawn = checkNotNull(mapDrawn())
        check(drawn.homes > before) { "Recentre did not put the camera back at its first view for $name: $drawn" }
        return drawn.pose
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
        if (theme == GraphTheme.SPACE) {
            // S05j item 2: the sheet whole, its top and Done below the status bar
            val name = "mapmode-legend-sheet-${if (dark) "dark" else "light"}-$device"
            shot(name)
            val res = instrumentation.targetContext.resources
            val id = res.getIdentifier("status_bar_height", "dimen", "android")
            val bar = if (id != 0) res.getDimensionPixelSize(id).toFloat() else 0f
            val top = compose.onNode(hasTestTag(sheet)).fetchSemanticsNode().boundsInRoot.top
            check(top >= bar - 1f) { "The legend's sheet starts under the status bar for $name: $top, the bar $bar" }
            requireLegendTop(dark)
        }
        requireLegendFoot(theme, dark)
        ui.pressBack()
        // a phone's sheet, taken full by requireLegendFoot's swipe, steps back to its half height at the
        // first back (Material3 settles an expanded sheet that has a half height there) and goes at the
        // second; a tablet's, which has none, goes at the first
        try { compose.waitUntil(3_000) { !shown(sheet) } } catch (_: ComposeTimeoutException) { ui.pressBack() }
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
    private fun chooseLines(style: GraphLineStyle, theme: GraphTheme = GraphTheme.SPACE, still: String? = null) {
        // IdeaToolButton's label and value, the map's own Look tool, which names its theme (the board's names its lines)
        val look = hasContentDescription("Look") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, theme.title)
        compose.onNode(look).performClick()
        // IdeaLinesPicker's segment words; the segment itself merges them
        val segment = hasTestTag("lookLines-" + style.rawValue)
        compose.waitUntil(10_000) { compose.onAllNodes(segment, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(segment, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        if (still != null) shot(still)
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
        /** S05i A6 (repair 6): an idea's size across in a cell state's close-up, and the least it may be, in dp. */
        const val CLOSE_DP = 48f
        const val LEAST_DP = 36f
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
