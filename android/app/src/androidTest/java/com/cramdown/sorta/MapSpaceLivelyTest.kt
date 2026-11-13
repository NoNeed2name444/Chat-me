package com.cramdown.sorta

import android.animation.ValueAnimator
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
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
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.ideas.universeInput
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTapKey
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * S05h A3: Space's Universe with animations on, on the emulator, by day and by night. CI turns
 * animations off for every test, so this one turns them on for itself once Sorta is up (the
 * animator duration scale 1, through the shell) and polls up to 5 s for ValueAnimator to see it;
 * it fails, never skips, if they stay off, and puts the scale back to 0 after. A map's liveliness is
 * read once per scene, so the map opens after that: on a phone by Map mode's switch, and on a
 * tablet, whose map is beside Sort from the start, by Look › Suns and then Universe, built again.
 *
 * While the planets orbit the map sets Compose state every frame, so the screen is never idle:
 * nothing here waits for idle. MapDrawn is read off the map's own view (its semantics, on the main
 * thread) inside waitUntil, which moves Compose's frames on, and taps on the map go through the map's
 * hit test (MapTapKey) or UiAutomation. Over 3 s the orbit clock rises above 0 (MapDrawn.orbitSeconds)
 * and a chosen planet's place on the screen changes; a still, mapmode-lively-universe-<light|dark>-<device>.png.
 * A finger held on that planet drops the orbit rate (MapDrawn.orbitRate) below 0.05 within 0.5 s of
 * the finger, on the device's uptime: its record reaches the test through Compose's state, on the
 * test's frame clock, late, so the test times nothing by when the record arrives. The renderer notes
 * the uptime of the frame that first took the press (MapDrawn.pressTakenAtMs), and the press's own
 * seconds until the rate fell under 0.05 (MapDrawn.pressSlowedSeconds); the uptime noted just before
 * the finger came down, the touch's way to the renderer plus that ease must be 0.5 s at most, and the
 * rate under 0.05 once 0.5 s of the press has stepped. 5 s and 10 s are only guards against a hang
 * while waiting. The frames drawn over the 3 s (the clock's steps) are logged, a first look at A7's
 * frame budget.
 */
@RunWith(AndroidJUnit4::class)
class MapSpaceLivelyTest {
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
        ui.executeShellCommand("settings put global animator_duration_scale 0")
        scenario?.close()
        activity = null
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun orbitingByDay() = orbit(dark = false)
    @Test fun orbitingByNight() = orbit(dark = true)

    private fun orbit(dark: Boolean) {
        val name = "mapmode-lively-universe-${if (dark) "dark" else "light"}-$device"
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        val dir = File(context.cacheDir, "lively-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        checkNotNull(scenario).onActivity { activity = it }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        // animations on, for this test only
        ui.executeShellCommand("settings put global animator_duration_scale 1")
        try { compose.waitUntil(5_000) { ValueAnimator.areAnimatorsEnabled() } } catch (_: ComposeTimeoutException) {}
        check(ValueAnimator.areAnimatorsEnabled()) { "Animations stayed off 5 s after the animator duration scale was set to 1 for $name" }
        if (compose.onAllNodes(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNodes().isNotEmpty()) {
            // a phone: the map is built from here on, lively; tapped through UiAutomation, since once it
            // draws the screen is never idle
            tapOnScreen(compose.onNode(hasTestTag(SortaMode.MAP.identifier)).fetchSemanticsNode(), "Map mode's switch")
        } else {
            // a tablet: its map was built with animations off, so built again, as a person would choose it
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(MapLook.CHIP)).fetchSemanticsNodes().isNotEmpty() }
            chooseStyle(SpaceStyle.SUN.label, idle = true)
            chooseStyle("Universe", idle = false)
        }
        val book = IdeaBook(notes, folders)
        val plan = UniversePlan.of(universeInput(book, book.allEdges(), MapLinkLength.STANDARD))
        try { compose.waitUntil(90_000) { drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true } } catch (_: ComposeTimeoutException) {}
        val start = drawn()
        check(start != null && framed(start) && start.roles.isNotEmpty()) { "Space's Universe was not drawn within 90 s for $name: $start" }
        // over 3 s the orbit clock rises
        val t0 = SystemClock.uptimeMillis()
        settle(3_000)
        val later = checkNotNull(drawn())
        val seconds = (SystemClock.uptimeMillis() - t0) / 1000f
        val steps = later.orbitSteps - start.orbitSteps
        android.util.Log.i("MapSpaceLivelyTest", "$name: $steps frames in $seconds s " +
            "(${"%.1f".format(steps / seconds)} a second), the orbit clock ${start.orbitSeconds} s then ${later.orbitSeconds} s")
        check(later.orbitSeconds > 0 && later.orbitSeconds > start.orbitSeconds) {
            "The orbit clock did not rise over 3 s for $name: ${start.orbitSeconds} s, then ${later.orbitSeconds} s; $later"
        }
        // a planet, chosen through the map's hit test, moves on the screen
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $name" }
        val planets = plan.bodies.indices.filter { i ->
            val b = plan.bodies[i]
            (b.role == UniverseRole.GAS_GIANT || b.role == UniverseRole.ROCKY) && plan.bodies.getOrNull(b.parent)?.role?.isFolder == true
        }
        val planet = planets.firstOrNull { i -> tap.ids.getOrNull(i).equals(plan.bodies[i].id, ignoreCase = true) }
        checkNotNull(planet) { "No planet of the plan is on the map for $name: ${plan.bodies.map { it.role }}, ${tap.ids}" }
        onMain { tap.tap(planet) }
        try { compose.waitUntil(10_000) { drawn()?.chosen == planet && named(planet) != null } } catch (_: ComposeTimeoutException) {}
        settle(1_000) // the camera's 0.45 s ease, done
        val first = checkNotNull(named(planet)) { "The chosen planet $planet shows no name to find it by for $name: ${drawn()}" }
        settle(1_500)
        val second = checkNotNull(named(planet)) { "The chosen planet $planet lost its name for $name: ${drawn()}" }
        check((second - first).getDistance() > 1f) { "The chosen planet $planet stayed at $first on the screen over 1.5 s for $name: ${drawn()}" }
        shot(name)
        // a finger held on it: the orbit clock eases to a stop
        val before = checkNotNull(drawn())
        check(before.orbitRate >= 0.5f) { "The orbit clock is not running before the finger comes down for $name: $before" }
        val at = checkNotNull(named(planet)) { "The chosen planet $planet shows no name to put a finger on for $name" }
        val origin = checkNotNull(mapNode()).positionOnScreen
        val x = origin.x + at.x; val y = origin.y + at.y
        val p0 = checkNotNull(drawn()).pressedSeconds
        // the uptime read before the down is injected: the earliest bound at or before the real down, so
        // the touch's way to the renderer (pressTakenAtMs − down) can only be counted long, never short
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y)
        try {
            // iOS's rule (GraphMotion.swift:1750-1757, MapDrag.advanceClock): while a body is pressed the rate
            // eases to 0 with tau 0.1 s, each frame stepping the clock by its interval (at most 0.25 s,
            // GlMapRenderer's dt), so after 0.3 s of steps it is under 0.05. The map takes the press from the
            // touch itself (Map3DView's gestures), at its next frame (MapDrawn.pressTakenAtMs); 5 s is only a
            // guard against a hang while waiting for the record
            try { compose.waitUntil(5_000) { drawn()?.let { it.pressTakenAtMs >= 0 && it.pressedSeconds > p0 } == true } } catch (_: ComposeTimeoutException) {}
            val taken = checkNotNull(drawn())
            check(taken.pressTakenAtMs >= 0 && taken.pressedSeconds > p0) {
                "A finger held on planet $planet at ($x, $y) was not taken as a press within 5 s " +
                    "(${SystemClock.uptimeMillis() - down} ms) for $name: $taken"
            }
            val takenAt = taken.pressTakenAtMs
            // then judged once 0.5 s of the press has stepped on the map's clock, 10 s at most against a hang
            try { compose.waitUntil(10_000) { drawn()?.let { it.pressTakenAtMs == takenAt && it.pressedSeconds - p0 >= 0.5f } == true } } catch (_: ComposeTimeoutException) {}
            val held = checkNotNull(drawn())
            val pressed = held.pressedSeconds - p0
            val took = SystemClock.uptimeMillis() - down
            check(held.pressTakenAtMs == takenAt) {
                "A finger held on planet $planet at ($x, $y): the press taken at uptime $takenAt ms ended or was taken again " +
                    "(${held.pressTakenAtMs}) before 0.5 s of it stepped, after $took ms for $name: $held"
            }
            check(pressed >= 0.5f) {
                "A finger held on planet $planet at ($x, $y) was pressed only $pressed s on the map's clock after $took ms for $name: $held"
            }
            check(held.orbitRate < 0.05f) {
                "A finger held on planet $planet at ($x, $y) left the orbit rate at ${held.orbitRate} after $pressed s of the press " +
                    "on the map's clock ($took ms), not below 0.05 for $name: $held"
            }
            // within 0.5 s of the finger, both on the device's uptime: the touch's way to the renderer, then the ease
            val reached = (takenAt - down) / 1000f
            check(held.pressSlowedSeconds >= 0f && reached >= 0f && reached + held.pressSlowedSeconds <= 0.5f) {
                "A finger held on planet $planet at ($x, $y): the press was taken ${reached} s after the down (uptime $down to $takenAt ms) " +
                    "and the orbit rate fell under 0.05 after ${held.pressSlowedSeconds} s more of it (−1: not yet), " +
                    "${reached + held.pressSlowedSeconds} s in all, not within 0.5 s of the finger for $name: $held"
            }
        } finally {
            touch(MotionEvent.ACTION_UP, down, x, y)
        }
    }

    /** One touch event on the screen, through UiAutomation: no wait for Compose to be idle. */
    private fun touch(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "The touch ($action at $x, $y) was not delivered" }
        } finally {
            event.recycle()
        }
    }

    /**
     * Look's main style set to [label] in Space's Look menu. With [idle], through Compose, waiting
     * for the new scene's frames; without, the item tapped through UiAutomation, since the scene it
     * builds (the Universe) never lets the screen go idle.
     */
    private fun chooseStyle(label: String, idle: Boolean) {
        val built = drawn()?.build
        val look = hasContentDescription("Look") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, GraphTheme.SPACE.title)
        compose.waitUntil(30_000) { compose.onAllNodes(look).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(look).performClick()
        val item = hasText(label) and hasClickAction()
        compose.waitUntil(10_000) { compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
        if (idle) {
            compose.onNode(item).performClick()
            compose.waitUntil(60_000) { drawn()?.let { it.build != built && framed(it) } == true }
        } else {
            tapOnScreen(compose.onNode(item).fetchSemanticsNode(), "The Look menu's $label")
            compose.waitUntil(30_000) { drawn()?.build?.let { it != built } == true }
        }
    }

    /** A tap in the middle of [node], through UiAutomation: no wait for Compose to be idle. */
    private fun tapOnScreen(node: SemanticsNode, what: String) {
        val middle = node.positionOnScreen + Offset(node.size.width / 2f, node.size.height / 2f)
        check(ui.click(middle.x.toInt(), middle.y.toInt())) { "$what could not be tapped" }
    }

    /** Waits [ms] while Compose's frames move on, as waitUntil moves them. */
    private fun settle(ms: Long) {
        try { compose.waitUntil(ms) { false } } catch (_: ComposeTimeoutException) {}
    }

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return checkNotNull(out).getOrThrow()
    }

    /** The map's semantics node, read off the activity's own Compose view on the main thread: no wait for idle. */
    private fun mapNode(): SemanticsNode? = onMain {
        val found = ArrayList<SemanticsNode>()
        fun nodes(n: SemanticsNode) {
            if (n.config.getOrNull(MapDrawnKey) != null) found.add(n)
            n.children.forEach(::nodes)
        }
        fun views(v: View) {
            if (v is RootForTest) nodes(v.semanticsOwner.unmergedRootSemanticsNode)
            if (v is ViewGroup) for (k in 0 until v.childCount) views(v.getChildAt(k))
        }
        activity?.window?.decorView?.let(::views)
        found.firstOrNull()
    }

    private fun drawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun named(i: Int): Offset? = drawn()?.names?.firstOrNull { it.index == i }?.let { Offset(it.body[0], it.body[1]) }

    private fun framed(drawn: MapDrawn) = drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    /** One still of the screen as [name].png, with no wait for idle. */
    private fun shot(name: String) {
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        private const val T = 1_793_000_000_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical, as MapModeSpaceRenderTest's: ideas in To sort and in two folders, some linked
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
