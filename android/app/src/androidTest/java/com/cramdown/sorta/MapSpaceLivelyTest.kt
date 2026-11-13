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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
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
import com.cramdown.sorta.ui.ideas.MapStepsSeen
import com.cramdown.sorta.ui.ideas.MapTouchSeen
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
 * the frame that takes the press, on the device's uptime: its record reaches the test through Compose's state, on the
 * test's frame clock, late, so the test times nothing by when the record arrives. The renderer notes
 * the uptime of the frame that first took the press (MapDrawn.pressTakenAtMs) and of the frame where
 * the rate first fell under 0.05 (MapDrawn.pressSlowedAtMs). Under the owner's 13 Nov decision ("Map's
 * own clock") the 0.5 s is timed from the frame that took the press, not from the down, since the
 * emulator's software GL paces when that frame comes (run 43380866654: 350 ms). In order: the down,
 * the frame before the taking one began (pressPrevFrameAtMs), the press reached the map
 * (pressArrivedAtMs), it was taken (pressTakenAtMs) and slowed (pressSlowedAtMs), at most 500 ms
 * after it was taken, and under 0.5 s of the map's own orbit clock (pressSlowedSeconds). So no frame
 * passed between the press's arrival and its taking. The hold is not polled, since a
 * semantics read takes the main thread that also paces the map's frames: the test thread sleeps
 * HOLD_MS, then reads the record, with 10 s only a guard against a hang. The frames drawn over the
 * 3 s (the clock's steps) are logged, a first look at A7's
 * frame budget.
 *
 * S05h2 A3: deathsAndSparksBy<Day|Night>, on the fixture and a folder inside Garden, a star: sparks from the star dragged fast
 * under the Universe and from an idea under Look › Comets, each frozen for a still
 * (mapmode-lively-plasma-... and mapmode-lively-dust-...), and the most linked idea deleted with the
 * death's clock held half way through its links drawing back (mapmode-lively-death-...), then let go:
 * the test waits until a record shows dying 0, or its own clock (MapDrawn.dyingSeconds, the map's own
 * clock, not the wall's) the plan's remaining length + 0.5 s past the hold, or 20 s of the wall, and
 * requires dying 0 (which a death that plays to its plan on that clock meets by construction; it fails on
 * a death outliving its plan or a clock stalled for 20 s). On the clock itself (repair 8), over every
 * record seen after the let-go: it never runs ahead of the wall (its rise since the hold at most the
 * wall's since the let-go + 0.25 s, one frame's step), and it keeps moving while a body is dying (it has
 * risen between clocks taken at least two frames and 1 s of the wall apart, from the release on; repair 9:
 * judged at the frames the clocks were taken, MapDrawn.dyingClockAtMs, since a record reaches the test
 * frames after it was drawn). The test clocks are reset after every test.
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
                     folders: List<NoteFolder> = Companion.folders, cardSeen: Boolean = false): Pair<UniversePlan, MapDrawn> {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000) // the system takes the new mode
        val context = instrumentation.targetContext
        // Space's first-run card already put away (S05h2 repair 6): both its steps and its touch lines seen,
        // under the keys Got it marks (MapPanelSettings.markCardSeen), so it covers no part of the map
        // (iOS's preview renders show none) and the first view is framed without it
        if (cardSeen) check(context.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString(MapTouchSeen.KEY, GraphTheme.SPACE.rawValue).putBoolean(MapStepsSeen.SPACE_KEY, true).commit()) {
            "The first-run card could not be marked seen for $name"
        }
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
        // the uptime read before the down is injected: the earliest bound at or before the real down, so
        // the touch's way to the slow-down (pressSlowedAtMs − down) can only be counted long, never short
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y)
        try {
            // iOS's rule (GraphMotion.swift:1750-1757, MapDrag.advanceClock): while a body is pressed the rate
            // eases to 0 with tau 0.1 s, so after 0.3 s of steps it is under 0.05. The map takes the press from
            // the touch itself (Map3DView's gestures) and notes the uptime of the frame where the rate first
            // fell under 0.05 (MapDrawn.pressSlowedAtMs). The hold is not polled: reading the semantics takes
            // the main thread, which also paces the map's frames, so polling would slow the frames it judges.
            // The test thread only sleeps, a bounded HOLD_MS, then reads the record
            SystemClock.sleep(HOLD_MS)
            // the read: Compose's test clock brings the latest record into the semantics; 10 s at most
            // against a hang. The times it judges were stamped by the renderer, so a late read changes nothing
            try { compose.waitUntil(10_000) { drawn()?.let { it.pressSlowedAtMs >= 0 } == true } } catch (_: ComposeTimeoutException) {}
            val held = checkNotNull(drawn()) { "No map record to read after the hold for $name" }
            val took = SystemClock.uptimeMillis() - down
            val times = "down at uptime $down ms, the frame before the taking one began at ${held.pressPrevFrameAtMs} ms, " +
                "the press reached the map at ${held.pressArrivedAtMs} ms, was taken at ${held.pressTakenAtMs} ms, rate under 0.05 at " +
                "${held.pressSlowedAtMs} ms (−1: not taken, or not slowed), ${held.pressSlowedSeconds} s of the map's own clock, " +
                "frames ${held.frameMs} ms apart, read $took ms after the down"
            check(held.pressTakenAtMs >= down && held.pressSlowedAtMs >= held.pressTakenAtMs) {
                "A finger held on planet $planet at ($x, $y) was not taken as one press that slowed the orbits " +
                    "and is still held: $times for $name: $held"
            }
            // taken at the first frame after it reached the map: the frame before began before it came
            check(held.pressPrevFrameAtMs >= 0 && held.pressPrevFrameAtMs <= held.pressArrivedAtMs &&
                held.pressArrivedAtMs >= down && held.pressArrivedAtMs <= held.pressTakenAtMs) {
                "A finger held on planet $planet at ($x, $y) was not taken at the first frame after it reached the map: " +
                    "$times for $name: $held"
            }
            check(held.orbitRate < 0.05f) {
                "A finger held on planet $planet at ($x, $y) left the orbit rate at ${held.orbitRate}, not below 0.05: " +
                    "$times for $name: $held"
            }
            // within 0.5 s of the frame that took the press, on the device's uptime (the owner, 13 Nov)
            check(held.pressSlowedAtMs - held.pressTakenAtMs <= 500) {
                "A finger held on planet $planet at ($x, $y) slowed the orbits under 0.05 only " +
                    "${held.pressSlowedAtMs - held.pressTakenAtMs} ms after the frame that took it, not within 500 ms: " +
                    "$times for $name: $held"
            }
            // and within 0.5 s of the map's own orbit clock
            check(held.pressSlowedSeconds >= 0f && held.pressSlowedSeconds <= 0.5f) {
                "A finger held on planet $planet at ($x, $y) slowed the orbits under 0.05 only after " +
                    "${held.pressSlowedSeconds} s of the map's own clock, not within 0.5 s: $times for $name: $held"
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
     * repair 6: Space's first-run card is seen before the map opens, the idea's galaxy is flown in to
     * before the deletion, and the still is taken once the death is drawn whole, a body's size at least,
     * in the uncovered window (deathInView); each spark still once its held body is at rest with its
     * name on it (heldAtRest). Released, none is dying once the death's own clock has run the plan's length + 0.5 s. Dust: Look › Comets, an idea
     * dragged fast throws sparks, frozen for mapmode-lively-dust-.... The frames a second at rest,
     * over the plasma fling and over the death are logged; the dust fling's are not, as Comets has
     * no orbit clock.
     */
    private fun deathsAndSparks(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val death = "mapmode-lively-death-$mode-$device"
        val notes = starNotes
        val (plan, start) = open(dark, death, starNotes, starFolders, cardSeen = true)
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
        // framed close (S05h2 repair 6, A6): the idea's galaxy flown in to by Fly in on its card, so the
        // dying body is large in the window, whole, among bodies drawn whole. The page keeps its Fly in,
        // so the scene built after the deletion flies in to the same galaxy (Map3DView's flyTo, per renderer)
        var top = idea
        while (plan.bodies[top].role != UniverseRole.GALAXY && plan.bodies[top].parent >= 0) top = plan.bodies[top].parent
        val galaxy = top
        val galaxyId = plan.bodies[galaxy].id
        check(plan.bodies[galaxy].role == UniverseRole.GALAXY && tap.ids.getOrNull(galaxy).equals(galaxyId, ignoreCase = true)) {
            "The most linked idea $ideaId ($idea) is in no galaxy on the map to fly in to for $death: ${plan.bodies.map { it.role to it.parent }}"
        }
        onMain { tap.tap(galaxy) }
        val flyIn = hasText(instrumentation.targetContext.getString(R.string.map_peek_fly_in)) and hasAnyAncestor(hasTestTag(MapPanelTags.PEEK))
        try { compose.waitUntil(10_000) { drawn()?.chosen == galaxy && compose.onAllNodes(flyIn).fetchSemanticsNodes().isNotEmpty() } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.chosen == galaxy && compose.onAllNodes(flyIn).fetchSemanticsNodes().isNotEmpty()) {
            "The galaxy $galaxyId ($galaxy) is not chosen with its card's Fly in for $death: ${drawn()}"
        }
        tapOnScreen(compose.onNode(flyIn).fetchSemanticsNode(), "Fly in on galaxy $galaxyId's card")
        try { compose.waitUntil(10_000) { drawn()?.flown == galaxy } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.flown == galaxy) { "Fly in on galaxy $galaxyId ($galaxy) did not fly in to it for $death: ${drawn()}" }
        settle(1_500) // the flight's 0.8 s
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
        // in the still (repair 6, A6): the new scene flown in to the same galaxy, the camera come to rest, and
        // the dying body drawn whole, at least a body's size across (SpaceLod.BODY_PIXELS), its centre in the
        // part of the map no chrome covers
        val framedDeath = deathInView(galaxyId, death)
        shot(death)
        val inView = checkNotNull(drawn())
        check(inView.dying == 1 && inView.dyingDrawn == 1 && inView.dyingAt.size == 3 && abs(inView.dyingReach - 0.5f) <= 0.05f &&
            abs(inView.dyingAt[0] - framedDeath.dyingAt[0]) <= 2f && abs(inView.dyingAt[1] - framedDeath.dyingAt[1]) <= 2f) {
            "The death moved on or out of the window while its still was taken for $death: before $framedDeath, after $inView"
        }
        // released: gone once the death's own clock has run the plan's length + 0.5 s past the hold (S05h2
        // repair 7, the owner's 13 Nov word: sub-second timing on the map's own clock). The death steps by the
        // map's frame step, at most 0.25 s a frame (MapDeath.MAX_STEP), so on frames slower than that its
        // clock runs slower than the wall's; MapDrawn.dyingSeconds is that clock, in the same record as
        // dying. A map that stalls still fails: 20 s of the wall at most
        val left = dies.duration - hold + 0.5
        val atLet = checkNotNull(drawn())
        val let = SystemClock.uptimeMillis()
        MapDeath.testHold = null
        // the release itself (repair 9): a frame begun after this reads the clock let go
        val released = SystemClock.uptimeMillis()
        fun run(d: MapDrawn) = d.dyingSeconds - hold
        // every record seen after the let-go, with the uptime it was read at (repair 8)
        val seen = ArrayList<Pair<MapDrawn, Long>>()
        seen.add(atLet to let)
        fun note(d: MapDrawn?): MapDrawn? { if (d != null && d !== seen.last().first) seen.add(d to SystemClock.uptimeMillis()); return d }
        try { compose.waitUntil(20_000) { note(drawn())?.let { it.dying == 0 || run(it) >= left } == true } } catch (_: ComposeTimeoutException) {}
        val gone = checkNotNull(note(drawn()))
        check(gone.dying == 0) {
            "A body was still dying with the death's own clock ${"%.3f".format(run(gone))} s past the hold at $hold s " +
                "(the plan's ${dies.duration} s, so gone by ${"%.3f".format(left)} s), ${SystemClock.uptimeMillis() - let} ms and " +
                "${gone.orbitSteps - atLet.orbitSteps} steps of the orbit clock after the clock was let go" +
                (if (run(gone) < left) " (the clock stalled: the wall's 20 s ran out first)" else "") + " for $death: $gone"
        }
        // the death's own clock (repair 8), checks that can fail on it: never ahead of the wall. Each frame
        // steps it by that frame's interval, at most 0.25 s (MapDeath.MAX_STEP), the first after the let-go
        // included, so its rise since the hold is at most the wall's since the let-go + 0.25 s
        for ((d, at) in seen) {
            if (d.dyingSeconds < 0) continue
            val wall = (at - let) / 1000.0
            check(run(d) <= wall + 0.25 + 1e-9) {
                "The death's own clock ran ahead of the wall: ${"%.3f".format(run(d))} s past the hold ${"%.3f".format(wall)} s " +
                    "after the let-go (at most 0.25 s more) for $death: ${seen.map { (r, t) -> "%.3f@%d".format(r.dyingSeconds, t - let) }}"
            }
        }
        // and it keeps moving while a body dies: from the release, the clock exactly at the hold, and from
        // each clock taken after it, the next clock taken at least two frames (orbit steps) and 1 s of the
        // wall later, the earlier still dying, has risen. Judged at the frames the clocks were taken
        // (repair 9, MapDrawn.dyingClockAtMs, the start of the frame that handed the clock on), not when
        // the records were read: a record reaches the test frames after it was drawn (the page's semantics
        // on the main thread; CI's tablet first showed a record two orbit steps on 1.3 s after the let-go,
        // its clock still the hold's, from frames drawn before the let-go), and the record hands the clock
        // on only once it has moved a tenth (MapDrawn.DYING_CLOCK_STEP) or something else of the death
        // changed, so a pair is judged over 1 s of the wall, not one frame. Only a clock taken in a frame
        // begun after the release counts, and that frame read the clock let go
        var aSeconds = hold
        var aAt = released
        var aSteps = atLet.orbitSteps
        var aDying = atLet.dying
        var pairs = 0
        for ((d, at) in seen) {
            if (d.dyingClockAtMs <= released) continue
            if (aDying < 1) break
            if (d.orbitSteps < aSteps + 2 || d.dyingClockAtMs - aAt < 1_000) continue
            check(d.dyingSeconds > aSeconds || d.dyingSeconds < 0 && d.dying == 0) {
                "The death's own clock stood at ${"%.3f".format(aSeconds)} s from a frame at ${aAt - released} ms to one at " +
                    "${d.dyingClockAtMs - released} ms after the release (read at ${at - released} ms), over ${d.orbitSteps - aSteps} " +
                    "frames, while a body was dying for $death: $d; every record seen: " +
                    seen.map { (r, t) -> "%.3f/%d/%d@%d,read@%d".format(r.dyingSeconds, r.dying, r.orbitSteps, r.dyingClockAtMs - released, t - released) }
            }
            pairs++
            aSeconds = d.dyingSeconds; aAt = d.dyingClockAtMs; aSteps = d.orbitSteps; aDying = d.dying
        }
        check(pairs >= 1) {
            "No two clocks taken at least two frames and 1 s apart while the death played, so its clock's moving was not judged, for $death: " +
                seen.map { (r, t) -> "%.3f/%d/%d@%d,read@%d".format(r.dyingSeconds, r.dying, r.orbitSteps, r.dyingClockAtMs - released, t - released) }
        }
        val dying = rate(atLet, gone, let)
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
        clearOfItsCard(index, name)
        val at = checkNotNull(named(index)) { "Body $index shows no name to put a finger on for $name: ${drawn()}" }
        val node = checkNotNull(mapNode())
        val origin = node.positionOnScreen
        val width = node.size.width.toFloat()
        val x = origin.x + at.x; val y = origin.y + at.y
        // to and fro about the middle of the map, a quarter of its width each way
        val middle = origin.x + width / 2
        val reach = width / 4
        // the fling starts on the far side of the middle from the body, a quarter of the map's width
        // from it at least, so its first move is far past the touch slop
        val side = if (x <= middle) 1f else -1f
        fun fx(step: Int) = middle + side * reach * kotlin.math.cos(step * 0.7f)
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
            // the first fast move at once, with nothing read in between (S05h2 repair 4): the hold for the
            // options (MapTouchRules.HOLD_FOR_MENU, 0.55 s) also runs on Compose's test clock, and every
            // poll of the semantics moves that clock on, so a poll here could open the options instead of
            // a drag. Once the body is dragged, no hold is waited for
            touch(MotionEvent.ACTION_MOVE, down, fx(0), y)
            // the record read only now; 5 s only guards against a hang
            try { compose.waitUntil(5_000) { drawn()?.grabbed == index } } catch (_: ComposeTimeoutException) {}
            val picked = checkNotNull(drawn())
            // with the down's place and the record before it, a red tells a finger off the body (the
            // camera turned: pose's yaw moved), a rebuild (build moved) and a touch the map never had apart
            check(picked.grabbed == index) {
                "A finger held 350 ms on body $index, then moved fast, did not pick it up for $name: down at ($x, $y) on the screen, " +
                    "the name's body at $at in the map at $origin; before the down: build ${before.build}, pose ${before.pose}, " +
                    "chosen ${before.chosen}, names ${before.names}; now: $picked"
            }
            start = picked
            t0 = SystemClock.uptimeMillis()
            var last = picked
            var latest = picked
            for (step in 1 until 60) {
                touch(MotionEvent.ACTION_MOVE, down, fx(step), y)
                // paced by the device's time and the renderer's frames, never by a span of Compose's test
                // clock (settle): the test thread sleeps, then reads the record once a frame has drawn the
                // move (the orbit clock stepped, or the held body moved on the screen; Comets has no orbit
                // clock), as orbit's hold reads it once after its sleep; 2 s only guards against a hang.
                // The body is dragged by now, so no hold waits on the clock that read moves
                SystemClock.sleep(30)
                try {
                    compose.waitUntil(2_000) { drawn()?.let { it.orbitSteps > last.orbitSteps || it.dragged != last.dragged } == true }
                } catch (_: ComposeTimeoutException) {}
                latest = checkNotNull(drawn())
                last = latest
                seen = maxOf(seen, latest.sparks)
                // frozen only once sparks have been thrown and are alive on the latest frame drawn
                if (step >= 20 && latest.sparks > 0 && latest.sparksTotal > before.sparksTotal) break
            }
            if (latest.sparksTotal > before.sparksTotal) SpaceTrails.testFrozen = true
            // a frame or two drawn frozen: the device's time, then a frame past the freeze read
            SystemClock.sleep(300)
            try {
                compose.waitUntil(2_000) { drawn()?.let { it.orbitSteps > last.orbitSteps || it != last } == true }
            } catch (_: ComposeTimeoutException) {}
            val frozen = checkNotNull(drawn())
            check(seen > 0 && frozen.sparks > 0 && frozen.sparksTotal > before.sparksTotal) {
                "Body $index dragged fast threw no sparks (at most $seen alive; ${frozen.sparks} now, ${frozen.sparksTotal - before.sparksTotal} thrown) for $name: $frozen"
            }
            // the still once the held body has come to rest under the still finger, its name drawn on it
            // (S05h2 repair 6): the name's body centre within 2 px of where the renderer drew the body
            val still = heldAtRest(index, name)
            check(still.sparks > 0) { "The frozen sparks were gone by the time body $index was at rest for $name: $still" }
            shot(name)
        } finally {
            touch(MotionEvent.ACTION_UP, down, middle, y)
            SpaceTrails.testFrozen = null
        }
        return rate(checkNotNull(start), checkNotNull(drawn()), t0)
    }

    /**
     * The held body [index] at rest under a still finger, its name on it (S05h2 repair 6): read every
     * 400 ms of Compose's frames until two reads in a row, a renderer frame apart, put the body at the same place (within 1 px)
     * and the name's body centre is within 2 px of it, 10 s at most. The name is placed from the
     * renderer's screen nodes a frame behind the drawing (Map3DView's names), so a still of a body still
     * moving can show its name where it was; at rest the two agree.
     */
    private fun heldAtRest(index: Int, name: String): MapDrawn {
        fun nameOff(d: MapDrawn): Float {
            val n = d.names.firstOrNull { it.index == index } ?: return Float.MAX_VALUE
            if (d.dragged.size < 2) return Float.MAX_VALUE
            return kotlin.math.hypot(n.body[0] - d.dragged[0], n.body[1] - d.dragged[1])
        }
        var previous: MapDrawn? = null
        var now = checkNotNull(drawn())
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            settle(400)
            now = checkNotNull(drawn())
            val p = previous
            // a renderer frame between the two reads: the orbit clock stepped (the Universe), or there is
            // none (Comets, whose held body sits at the finger at once)
            if (p != null && (now.orbitSteps > p.orbitSteps || now.orbitSteps == 0) && now.grabbed == index && now.dragged.size == 2 &&
                p.dragged.size == 2 && abs(now.dragged[0] - p.dragged[0]) <= 1f && abs(now.dragged[1] - p.dragged[1]) <= 1f && nameOff(now) <= 2f) return now
            previous = now
        }
        error("Body $index did not come to rest with its name on it within 10 s for $name: drawn at ${now.dragged}, " +
            "its name's body at ${now.names.firstOrNull { it.index == index }?.body} (${nameOff(now)} px off), before ${previous?.dragged}; $now")
    }

    /**
     * The held death in the still (S05h2 repair 6, A6): the scene built after the deletion flown in to
     * the galaxy [galaxyId] (MapDrawn.flown, by the new map's ids), the camera at rest over two reads 500 ms
     * of Compose's frames apart with a renderer frame between them (MapDrawn.pose, orbitSteps), and the dying body drawn whole (MapDrawn.dyingDrawn), at
     * least SpaceLod.BODY_PIXELS across, its centre (MapDrawn.dyingAt) inside the map clear of its edges
     * by 48 dp, below the top strip and above the Undo banner where they cover the map. 30 s at most.
     */
    private fun deathInView(galaxyId: String, name: String): MapDrawn {
        val margin = 48f * instrumentation.targetContext.resources.displayMetrics.density
        fun covered(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        fun window(): FloatArray? {
            val map = mapNode() ?: return null
            val bounds = map.boundsInRoot
            var top = margin; var bottom = bounds.height - margin
            covered(MapPanelTags.TOP_STRIP)?.let { s -> if (s.right > bounds.left && s.left < bounds.right) top = maxOf(top, s.bottom - bounds.top + margin / 2) }
            covered(IdeasTags.BANNER)?.let { b -> if (b.right > bounds.left && b.left < bounds.right) bottom = minOf(bottom, b.top - bounds.top - margin / 2) }
            return floatArrayOf(margin, top, bounds.width - margin, bottom)
        }
        fun inside(d: MapDrawn, w: FloatArray?): Boolean = w != null && d.dyingAt.size == 3 &&
            d.dyingAt[0] in w[0]..w[2] && d.dyingAt[1] in w[1]..w[3] && d.dyingAt[2] >= com.cramdown.sorta.map.SpaceLod.BODY_PIXELS
        var previous: MapDrawn? = null
        var now = checkNotNull(drawn())
        var w: FloatArray? = null
        var galaxy = -1
        val until = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < until) {
            settle(500)
            now = checkNotNull(drawn())
            galaxy = mapNode()?.config?.getOrNull(MapTapKey)?.ids?.indexOfFirst { it.equals(galaxyId, ignoreCase = true) } ?: -1
            w = window()
            val p = previous
            // the same pose across a renderer frame (the orbit clock stepped between the reads): no flight moving it
            if (p != null && galaxy >= 0 && now.flown == galaxy && now.dying == 1 && now.dyingDrawn >= 1 && now.orbitSteps > p.orbitSteps &&
                same(p.pose, now.pose) && inside(now, w)) return now
            previous = now
        }
        error("The held death is not in the still for $name: flown ${now.flown} (galaxy $galaxyId at $galaxy), ${now.dyingDrawn} dying drawn " +
            "at ${now.dyingAt} (x, y, px across) in the window ${w?.toList()} (left, top, right, bottom), the camera ${previous?.pose} then ${now.pose}; $now")
    }

    /** Two camera poses alike to a thousandth. */
    private fun same(a: List<Float>, b: List<Float>) = a.size == 6 && b.size == 6 && a.indices.all { abs(a[it] - b[it]) <= 1e-3f }

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
    /**
     * The chosen body [index] moved clear of its card, as a person would see to it before putting a
     * finger on it: two fingers on the map, above the card and on the far side from the body, carry
     * the map up until the body's centre is 48 dp above the card's top. A tap's ease takes a body only
     * a third of the way to its goal (MapCameraEase.SHARE, iOS's 0.34), so it can stay under the card:
     * on Chat-me copy eec4320's tablet body 4's centre lay 21 px inside the card's top, the finger's down
     * went to the card, and the map recorded no press at all.
     */
    private fun clearOfItsCard(index: Int, name: String) {
        val px = instrumentation.targetContext.resources.displayMetrics.density
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        repeat(3) { attempt ->
            val card = compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().firstOrNull()
                ?: return check(attempt == 0 || drawn()?.chosen == index) { "Moving the map let body $index go for $name: ${drawn()}" }
            val node = checkNotNull(mapNode())
            val origin = node.positionOnScreen
            val body = checkNotNull(named(index)) { "Body $index shows no name for $name: ${drawn()}" }
            val cardTop = card.positionOnScreen.y
            val cardLeft = card.positionOnScreen.x
            val cardRight = cardLeft + card.size.width
            val clear = cardTop - 48 * px
            val bodyY = origin.y + body.y
            val bodyX = origin.x + body.x
            if (bodyY <= clear || bodyX < cardLeft - 24 * px || bodyX > cardRight + 24 * px) {
                if (attempt > 0) check(drawn()?.chosen == index) { "Moving the map let body $index go for $name: ${drawn()}" }
                return
            }
            // fingers on the far side of the map's middle from the body, starting just above the card
            val w = node.size.width.toFloat()
            val fingersX = if (body.x <= w / 2) 0.75f * w else 0.25f * w
            val from = Offset(fingersX, clear - origin.y)
            val to = from - Offset(0f, bodyY - clear + 24 * px)
            val apart = 40 * px
            compose.onNode(map).performTouchInput {
                pinch(from - Offset(apart, 0f), to - Offset(apart, 0f), from + Offset(apart, 0f), to + Offset(apart, 0f), 600)
            }
            compose.waitForIdle()
            try {
                compose.waitUntil(10_000) { named(index)?.let { origin.y + it.y < bodyY - 8 * px } == true }
            } catch (_: ComposeTimeoutException) {}
            android.util.Log.i("MapSpaceLivelyTest", "$name: body $index at y ${origin.y + (named(index)?.y ?: Float.NaN)}, " +
                "was $bodyY, its card's top $cardTop, after move ${attempt + 1}")
        }
        val card = compose.onAllNodes(hasTestTag(MapPanelTags.PEEK)).fetchSemanticsNodes().firstOrNull() ?: return
        val body = checkNotNull(named(index))
        check(checkNotNull(mapNode()).positionOnScreen.y + body.y <= card.positionOnScreen.y - 24 * px) {
            "Body $index stays under its card after three moves of the map for $name: body $body, card at ${card.positionOnScreen}; ${drawn()}"
        }
        check(drawn()?.chosen == index) { "Moving the map let body $index go for $name: ${drawn()}" }
    }

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
        /** How long A3's finger is held before the record is read, the test thread asleep: the 0.5 s bar and room. */
        const val HOLD_MS = 1_500L
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
