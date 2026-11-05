package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.GraphLineStyle
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapPanelTags
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
    }

    /**
     * The first-run card shows, and every body the map drew is above its top: the drawn bodies' box
     * (in the map's own pixels, from its top left) lies over the card's top edge (S05d A3).
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
