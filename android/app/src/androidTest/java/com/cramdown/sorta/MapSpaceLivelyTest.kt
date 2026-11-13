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
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.MapDeath
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.SpaceTrails
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.MapPanelTags
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
import kotlin.math.abs
import kotlin.math.ceil

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
 *
 * S05h2 A3: deathsAndSparksBy<Day|Night>, on the fixture and a folder inside Garden, a star: sparks from the star dragged fast
 * under the Universe and from an idea under Look › Comets, each frozen for a still
 * (mapmode-lively-plasma-... and mapmode-lively-dust-...), and the most linked idea deleted with the
 * death's clock held half way through its links drawing back (mapmode-lively-death-...), then gone
 * within its plan's length + 0.5 s once let go. The test clocks are reset after every test.
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
        MapDeath.testHold = null
        SpaceTrails.testFrozen = null
        ui.executeShellCommand("settings put global animator_duration_scale 0")
        scenario?.close()
        activity = null
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun orbitingByDay() = orbit(dark = false)
    @Test fun orbitingByNight() = orbit(dark = true)
    @Test fun deathsAndSparksByDay() = deathsAndSparks(dark = false)
    @Test fun deathsAndSparksByNight() = deathsAndSparks(dark = true)

    /**
     * Sorta on a fixture ([notes] and [folders], the companion's by default) with animations on, its map
     * open on Space's Universe and drawn; the plan IdeasSpace builds for the fixture, and the map's
     * first full record.
     */
    private fun open(dark: Boolean, name: String, notes: List<Note> = Companion.notes,
                     folders: List<NoteFolder> = Companion.folders): Pair<UniversePlan, MapDrawn> {
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
        return plan to start
    }

    private fun orbit(dark: Boolean) {
        val name = "mapmode-lively-universe-${if (dark) "dark" else "light"}-$device"
        val (plan, start) = open(dark, name)
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

    /**
     * S05h2 A3 and A7, animations on, by renderer steps as orbit's hold is judged, on the fixture with a
     * star ([starFolders], [starNotes]). Plasma: its star (a folder inside a folder; a top folder is a
     * black hole, which throws none, GraphStyleAnimator.swift:199-202), chosen and dragged fast to
     * and fro under the Universe, throws sparks (MapDrawn.sparks above 0); frozen by
     * SpaceTrails.testFrozen for a still, mapmode-lively-plasma-<light|dark>-<device>.png. A death:
     * the fixture's idea with the most links the map draws (kinds 0-2), deleted from its card's
     * options with the death's clock held (MapDeath.testHold) at half its plan's linksGone, is one body
     * dying with its links drawn half way back (dyingReach 0.5 ± 0.05; mapmode-lively-death-...);
     * released, none is dying within the plan's length + 0.5 s of steps. Dust: Look › Comets, an idea
     * dragged fast throws sparks, frozen for mapmode-lively-dust-.... The frames a second at rest,
     * over the plasma fling and over the death are logged; the dust fling's are not, as Comets has
     * no orbit clock.
     */
    private fun deathsAndSparks(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val death = "mapmode-lively-death-$mode-$device"
        val notes = starNotes
        val (plan, start) = open(dark, death, starNotes, starFolders)
        // A7: the orbit at rest, the reference the fling and the death are logged against
        val t0 = SystemClock.uptimeMillis()
        settle(2_000)
        val rest = rate(start, checkNotNull(drawn()), t0)
        // plasma: the star dragged fast under the Universe
        val plasma = "mapmode-lively-plasma-$mode-$device"
        val star = plan.bodies.indexOfFirst { it.role == UniverseRole.STAR }
        check(star >= 0 && plan.bodies[star].id.equals(seeds.id, ignoreCase = true)) {
            "The fixture's folder ${seeds.name} is not a star to drag for $plasma: ${plan.bodies.map { it.id to it.role }}"
        }
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $plasma" }
        check(tap.ids.getOrNull(star).equals(plan.bodies[star].id, ignoreCase = true)) { "The star $star is not the plan's on the map for $plasma: ${tap.ids}" }
        val flung = throwSparks(tap, star, plasma)
        // a death, the clock held half way through its links drawing back
        fun linksOf(i: Int) = plan.links.count { it.kind != 3 && (it.a == i || it.b == i) }
        val ideas = notes.mapNotNull { n -> tap.ids.indexOfFirst { it.equals(n.id, ignoreCase = true) }.takeIf { it >= 0 } }
        val idea = checkNotNull(ideas.maxByOrNull(::linksOf)) { "No idea of the fixture is on the map for $death: ${tap.ids}" }
        val ideaId = tap.ids[idea]
        val links = linksOf(idea)
        check(links > 0 && plan.bodies[idea].id.equals(ideaId, ignoreCase = true)) { "The most linked idea $ideaId ($idea) has no drawn link in the plan for $death: ${plan.links}" }
        val body = plan.bodies[idea]
        val dies = MapDeath.plan(MapDeath.kind(body.role, body.count), smooth = false)
        val hold = 0.5 * dies.linksGone
        onMain { tap.tap(idea) }
        val more = hasContentDescription(instrumentation.targetContext.getString(R.string.map_peek_more)) and hasClickAction() and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        try { compose.waitUntil(10_000) { drawn()?.chosen == idea && compose.onAllNodes(more).fetchSemanticsNodes().isNotEmpty() } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.chosen == idea && compose.onAllNodes(more).fetchSemanticsNodes().isNotEmpty()) { "Idea $idea ($ideaId) is not chosen with its card's More for $death: ${drawn()}" }
        settle(1_000) // the camera's 0.45 s ease, done
        tapOnScreen(compose.onNode(more).fetchSemanticsNode(), "More on idea $ideaId's card")
        val delete = hasText(instrumentation.targetContext.getString(R.string.map_menu_delete)) and hasAnyAncestor(hasTestTag(MapPanelTags.MENU))
        try { compose.waitUntil(10_000) { compose.onAllNodes(delete).fetchSemanticsNodes().isNotEmpty() } } catch (_: ComposeTimeoutException) {}
        check(compose.onAllNodes(delete).fetchSemanticsNodes().isNotEmpty()) { "More on idea $ideaId's card offered no Delete for $death" }
        val before = checkNotNull(drawn())
        MapDeath.testHold = hold
        tapOnScreen(compose.onNode(delete).fetchSemanticsNode(), "Delete in idea $ideaId's options")
        try {
            compose.waitUntil(30_000) { drawn()?.let { it.diedTotal > before.diedTotal && it.dying == 1 && it.dyingLinks > 0 } == true }
        } catch (_: ComposeTimeoutException) {}
        settle(500) // the held clock, drawn
        val held = checkNotNull(drawn())
        check(held.diedTotal == before.diedTotal + 1 && held.dyingLinksTotal == before.dyingLinksTotal + links) {
            "Deleting idea $ideaId counted ${held.diedTotal - before.diedTotal} deaths and ${held.dyingLinksTotal - before.dyingLinksTotal} dying links, " +
                "not 1 and its $links links in the plan, for $death: before $before, then $held"
        }
        check(held.dying == 1 && held.dyingLinks > 0 && abs(held.dyingReach - 0.5f) <= 0.05f) {
            "With the death's clock held at $hold s (half of ${dies.linksGone} s), the map drew ${held.dying} dying and ${held.dyingLinks} links " +
                "reaching ${held.dyingReach}, not 1 and 0.5 ± 0.05, for $death: $held"
        }
        shot(death)
        // released: gone within the plan's length + 0.5 s, judged by the orbit clock's steps (MapDrawn.orbitSteps,
        // never capped; the lively Universe steps it every frame, each step at most 0.25 s)
        val left = dies.duration - hold + 0.5
        val f0 = held.orbitSteps
        val let = SystemClock.uptimeMillis()
        MapDeath.testHold = null
        fun stepped(d: MapDrawn) = SystemClock.uptimeMillis() - let >= left * 1000 && d.orbitSteps - f0 >= ceil(left / 0.25).toInt()
        try { compose.waitUntil(20_000) { drawn()?.let { it.dying == 0 || stepped(it) } == true } } catch (_: ComposeTimeoutException) {}
        val gone = checkNotNull(drawn())
        check(gone.dying == 0) {
            "A body was still dying ${SystemClock.uptimeMillis() - let} ms and ${gone.orbitSteps - f0} steps of the orbit clock after the clock was let go " +
                "(the plan's ${dies.duration} s from $hold s, + 0.5 s) for $death: $gone"
        }
        val dying = rate(held, gone, let)
        // dust: Look › Comets, an idea dragged fast
        val dust = "mapmode-lively-dust-$mode-$device"
        chooseStyle(SpaceStyle.COMET.label, idle = false)
        try { compose.waitUntil(60_000) { drawn()?.let(::framed) == true } } catch (_: ComposeTimeoutException) {}
        val comets = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $dust" }
        val comet = notes.firstNotNullOfOrNull { n -> comets.ids.indexOfFirst { it.equals(n.id, ignoreCase = true) }.takeIf { it >= 0 } }
        checkNotNull(comet) { "No idea of the fixture is on the map under Comets for $dust: ${comets.ids}" }
        throwSparks(comets, comet, dust) // its rate is not measured: Comets has no orbit clock
        android.util.Log.i("MapSpaceLivelyTest", "$death: frames a second at rest $rest, over the plasma fling $flung, over the death $dying, over the dust fling not measured (no orbit clock under Comets)")
    }

    /**
     * Body [index], chosen through [tap], held past the pick-up and dragged fast to and fro, its
     * sparks seen (MapDrawn.sparks above 0 and sparksTotal risen); frozen for the still [name], then
     * let go and thawed. The frames a second over the fling.
     */
    private fun throwSparks(tap: com.cramdown.sorta.ui.map.MapTap, index: Int, name: String): Float {
        onMain { tap.tap(index) }
        try { compose.waitUntil(10_000) { drawn()?.chosen == index && named(index) != null } } catch (_: ComposeTimeoutException) {}
        settle(1_000) // the camera's 0.45 s ease, done
        val at = checkNotNull(named(index)) { "Body $index shows no name to put a finger on for $name: ${drawn()}" }
        val node = checkNotNull(mapNode())
        val origin = node.positionOnScreen
        val width = node.size.width.toFloat()
        val x = origin.x + at.x; val y = origin.y + at.y
        // to and fro about the middle of the map, a quarter of its width each way
        val middle = origin.x + width / 2
        val reach = width / 4
        val before = checkNotNull(drawn())
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y, down)
        var seen = 0
        var start: MapDrawn? = null
        var t0 = 0L
        try {
            // still past the pick-up (0.25 s, by the events' own times), then moving fast. The pick-up's own
            // timer runs on Compose's test clock, which a real wait does not move, and Compose hands on no
            // move whose place is unchanged; so the finger moves 1 px, inside the touch slop, at an event
            // time 300 ms or more past the down, as MapModeSpaceRenderTest's drag does, and the map picks
            // the body up by that event's time (S05h2 repair 3)
            SystemClock.sleep(350)
            touch(MotionEvent.ACTION_MOVE, down, x + 1f, y, maxOf(SystemClock.uptimeMillis(), down + 300))
            // 5 s only guards against a hang
            try { compose.waitUntil(5_000) { drawn()?.grabbed == index } } catch (_: ComposeTimeoutException) {}
            val picked = checkNotNull(drawn())
            check(picked.grabbed == index) { "A finger held 350 ms on body $index did not pick it up for $name: $picked" }
            start = picked
            t0 = SystemClock.uptimeMillis()
            for (step in 0 until 60) {
                val fx = middle + reach * kotlin.math.sin(step * 0.7f)
                touch(MotionEvent.ACTION_MOVE, down, fx, y)
                settle(30)
                seen = maxOf(seen, drawn()?.sparks ?: 0)
                if (step >= 20 && seen > 0) break
            }
            SpaceTrails.testFrozen = true
            settle(300) // a frame or two drawn frozen
            val frozen = checkNotNull(drawn())
            check(seen > 0 && frozen.sparks > 0 && frozen.sparksTotal > before.sparksTotal) {
                "Body $index dragged fast threw no sparks (at most $seen alive; ${frozen.sparks} now, ${frozen.sparksTotal - before.sparksTotal} thrown) for $name: $frozen"
            }
            shot(name)
        } finally {
            touch(MotionEvent.ACTION_UP, down, middle, y)
            SpaceTrails.testFrozen = null
        }
        return rate(checkNotNull(start), checkNotNull(drawn()), t0)
    }

    /**
     * Frames a second between two records, [t0] the first's uptime, counted by the orbit clock's steps
     * (MapDrawn.orbitSteps, never capped; MapDrawn.frames stops at ENOUGH). Only the lively Universe steps it.
     */
    private fun rate(from: MapDrawn, to: MapDrawn, t0: Long): Float {
        val seconds = (SystemClock.uptimeMillis() - t0) / 1000f
        return if (seconds > 0f) (to.orbitSteps - from.orbitSteps) / seconds else 0f
    }

    /** One touch event on the screen at event time [at], through UiAutomation: no wait for Compose to be idle. */
    private fun touch(action: Int, down: Long, x: Float, y: Float, at: Long = SystemClock.uptimeMillis()) {
        val event = MotionEvent.obtain(down, at, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
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

        // deathsAndSparks' fixture (S05h2 repair 2): the same, and a folder inside Garden, which the
        // Universe plans as a star (UniversePlan: a folder below the top), with one idea, unlinked
        val seeds = NoteFolder(id = id(203), name = "Seeds", parentId = garden.id, createdAt = T + 2)
        val starFolders = folders + seeds
        val starNotes = notes + Note(id = id(19), title = "Sort the seed packets", folderId = seeds.id, createdAt = T + 3_600_000L)
    }
}
