package com.cramdown.sorta

import android.animation.ValueAnimator
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.BOARD_CARD_TAG
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.MapStepsSeen
import com.cramdown.sorta.ui.ideas.MapTouchSeen
import com.cramdown.sorta.ui.ideas.SkyTags
import com.cramdown.sorta.ui.ideas.SortaMode
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
 * S06b A9 on the emulator, by day and by night: Space's Universe on made-up ideas whose dates count
 * back from now, so the tails and the moons are the same on any day. Kept as stills in
 * /sdcard/Download/sorta-renders, each named sky-<what>-<light|dark>-<device>.png:
 * tails (comets caught today, 3, 10 and 40 days ago, and the pair shared from example.org circling),
 * lean-card (the leaning idea's card: "Shares ‘garden’ with Raised beds" and File there, 48 dp),
 * shared-card ("Shared from example.org"), moons (today read with its 3 ideas, yesterday with none),
 * lit-day-space (today picked: bodies outside it drawn faded, MapDrawn.faded), lit-day-board (the
 * same day on the 2D board), then Clear and Done; and last, File there with animations on, the
 * idea easing into the folder's system: settle, as the new scene's first frames are drawn, and
 * settled, with the notice "Moved to Raised beds.". Animations go back off after.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSkyRenderTest {
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

    @Test fun skyByDay() = sky(dark = false)
    @Test fun skyByNight() = sky(dark = true)

    private fun sky(dark: Boolean) {
        val suffix = "${if (dark) "dark" else "light"}-$device"
        open(dark, suffix)
        shot("sky-tails-$suffix")

        // A4: the leaning idea's card, its line and File there
        choose(LEANING, suffix)
        waitFor(SkyTags.LEAN, "the lean line on the card, $suffix")
        val lean = node(SkyTags.LEAN)
        check(spoken(lean) == "Shares ‘garden’ with Raised beds") { "The lean line reads ${spoken(lean)} for $suffix" }
        val fileThere = node(SkyTags.FILE_THERE)
        check(spoken(fileThere) == "File there, in Raised beds") { "File there reads ${spoken(fileThere)} for $suffix" }
        target(fileThere, "File there, $suffix")
        settle(1_000)
        shot("sky-lean-card-$suffix")

        // A8: a shared idea's card names where it came from
        choose(SHARED[1], suffix)
        waitFor(SkyTags.SHARED_FROM, "the source mark on the card, $suffix")
        check(spoken(node(SkyTags.SHARED_FROM)) == "Shared from example.org") { "The source mark reads ${spoken(node(SkyTags.SHARED_FROM))}" }
        settle(1_000)
        shot("sky-shared-card-$suffix")

        // A5: the moons, from the strip's Days chip
        waitFor(SkyTags.DAYS_CHIP, "the Days chip, $suffix")
        target(node(SkyTags.DAYS_CHIP), "Days, $suffix")
        tapOnScreen(node(SkyTags.DAYS_CHIP), "the Days chip")
        waitFor(SkyTags.moon(0), "today's moon, $suffix")
        val today = node(SkyTags.moon(0))
        target(today, "a moon, $suffix")
        check(spoken(today).endsWith(", 3 ideas")) { "Today's moon reads ${spoken(today)} for $suffix" }
        check(spoken(node(SkyTags.moon(1))).endsWith(", no ideas")) { "Yesterday's moon reads ${spoken(node(SkyTags.moon(1)))}" }
        settle(1_000)
        shot("sky-moons-$suffix")

        // A6: today lit, the rest faded; then on the board
        tapOnScreen(today, "today's moon")
        waitFor(SkyTags.MOONS_CLEAR, "Clear, with a day lit, $suffix")
        try { compose.waitUntil(20_000) { (drawn()?.faded ?: 0) > 0 } } catch (_: ComposeTimeoutException) {}
        check((drawn()?.faded ?: 0) > 0) { "No body was drawn faded outside the lit day for $suffix: ${drawn()}" }
        settle(1_000)
        shot("sky-lit-day-space-$suffix")
        chooseLook(MapLook.TWO_D)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(BOARD_CARD_TAG)).fetchSemanticsNodes().isNotEmpty() }
        waitFor(SkyTags.moon(0), "the moons stay open on the board, $suffix")
        settle(1_000)
        shot("sky-lit-day-board-$suffix")
        tapOnScreen(node(SkyTags.MOONS_CLEAR), "Clear")
        compose.waitUntil(10_000) { !shown(SkyTags.MOONS_CLEAR) }
        tapOnScreen(node(SkyTags.MOONS_DONE), "Done")
        compose.waitUntil(10_000) { !shown(SkyTags.moon(0)) }

        // A2: back in Space, File there with animations on: the idea settles from where it was
        chooseLook(MapLook.SPACE)
        compose.waitUntil(90_000) { drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true }
        choose(LEANING, suffix)
        waitFor(SkyTags.FILE_THERE, "File there, again, $suffix")
        val built = drawn()?.build
        ui.executeShellCommand("settings put global animator_duration_scale 1")
        try { compose.waitUntil(5_000) { ValueAnimator.areAnimatorsEnabled() } } catch (_: ComposeTimeoutException) {}
        check(ValueAnimator.areAnimatorsEnabled()) { "Animations stayed off 5 s after the scale was set to 1 for $suffix" }
        tapOnScreen(node(SkyTags.FILE_THERE), "File there")
        try { compose.waitUntil(30_000) { drawn()?.let { it.build != built && it.bodyFrames > 0 } == true } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.build != built) { "Filing built no new scene for $suffix: ${drawn()}" }
        shot("sky-settle-$suffix")
        settle(1_500)
        shot("sky-settled-$suffix")
        check(compose.onAllNodes(hasText("Moved to Raised beds.", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            "No notice of the filing for $suffix"
        }
    }

    /** Sorta on [ideas] with Space's first-run card put away, its map open on the Universe and drawn. */
    private fun open(dark: Boolean, name: String) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000)
        val context = instrumentation.targetContext
        check(context.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString(MapTouchSeen.KEY, GraphTheme.SPACE.rawValue).putBoolean(MapStepsSeen.SPACE_KEY, true).commit()) { "card seen, $name" }
        val dir = File(context.cacheDir, "sky-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(ideas(System.currentTimeMillis()), folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        checkNotNull(scenario).onActivity { activity = it }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        if (shown(SortaMode.MAP.identifier)) tapOnScreen(node(SortaMode.MAP.identifier), "Map mode's switch")
        try { compose.waitUntil(90_000) { drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.let { framed(it) && it.roles.isNotEmpty() } == true) { "Space's Universe was not drawn within 90 s for $name: ${drawn()}" }
        settle(1_500)
    }

    /** The idea titled [title] chosen through the map's hit test, as a tap on it is. */
    private fun choose(title: String, name: String) {
        val id = ideas(0).first { it.title == title }.id
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $name" }
        val index = tap.ids.indexOfFirst { it.equals(id, ignoreCase = true) }
        check(index >= 0) { "$title is not on the map for $name: ${tap.ids}" }
        onMain { tap.tap(index) }
        compose.waitUntil(10_000) { drawn()?.chosen == index }
    }

    private fun chooseLook(look: MapLook) {
        tapOnScreen(node(MapLook.CHIP), "the look chip")
        waitFor(look.identifier, "the look menu's ${look.identifier}")
        tapOnScreen(node(look.identifier), look.identifier)
    }

    // on the test thread: compose's finders wait for idle, which they refuse to do on the main thread
    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, what: String) {
        try { compose.waitUntil(15_000) { shown(tag) } } catch (_: ComposeTimeoutException) {}
        check(shown(tag)) { "Not on screen: $what" }
    }

    private fun node(tag: String): SemanticsNode = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().first()

    private fun spoken(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""

    /** Its touch area at least the least target, 48 dp, across and tall (as MapPanelsTouchTargetTest measures). */
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
        const val LEANING = "Water the garden path"
        val SHARED = listOf("Bike paths map", "Night ride route")
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical, as iOS's SkyExport: Raised beds in Garden, its words "garden" and "soil"
        val garden = NoteFolder(id = id(101), name = "Garden", createdAt = 0)
        val raisedBeds = NoteFolder(id = id(102), name = "Raised beds", parentId = garden.id, createdAt = 1, words = listOf("garden", "soil"))
        val kitchen = NoteFolder(id = id(103), name = "Kitchen", createdAt = 2)
        val folders = listOf(garden, raisedBeds, kitchen)

        /** The ideas, dated back from [now]: today's three spread over the part of today gone by. */
        fun ideas(now: Long): List<Note> {
            val zone = java.time.ZoneId.systemDefault()
            val midnight = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val today = (now - midnight).coerceAtLeast(4L)
            fun ago(ms: Long) = now - ms
            return listOf(
                Note(id = id(1), title = "Paint the shed green", folderId = garden.id, createdAt = ago(1_000 * HOUR)),
                Note(id = id(2), title = "Seed swap list", folderId = raisedBeds.id, createdAt = ago(900 * HOUR)),
                Note(id = id(3), title = "Try a rye starter", folderId = kitchen.id, createdAt = ago(800 * HOUR)),
                Note(id = id(4), title = LEANING, createdAt = ago(today / 2)),
                Note(id = id(5), title = SHARED[0], sharedFrom = "example.org", createdAt = ago(today * 4 / 5)),
                Note(id = id(6), title = SHARED[1], sharedFrom = "example.org", createdAt = ago(today / 5)),
                Note(id = id(7), title = "A bell for the bike", createdAt = ago(3 * 24 * HOUR)),
                Note(id = id(8), title = "Fix the shed door", createdAt = ago(10 * 24 * HOUR)),
                Note(id = id(9), title = "Oil the gate", createdAt = ago(40 * 24 * HOUR)),
            )
        }
    }
}
