package com.cramdown.sorta

import android.content.Intent
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.ideas.EDITOR_TAG
import com.cramdown.sorta.ui.ideas.IdeaShapeTags
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

/**
 * S06c A2, A4, A5 on the emulator's Space (the 3D look is drawn only on a real GL surface): Sorta itself on
 * made-up ideas, a new user's look. On the tablet the editor opens beside the map and the ideas like the
 * open one are ringed (MapDrawn.glows; still shapes-glow-space-tablet.png). On both, an idea held and moved
 * onto another rings that one twice (MapDrawn.drop; still shapes-drop-space-<device>.png) and, let go
 * there, opens the collide card with the two, which Cancel puts away; then the idea chosen with a tap and
 * pinched apart opens the split chooser (shapes-pinch-split-space-<device>.png). Hold-to-move elsewhere
 * and the camera's pinch are MapModeSpaceRenderTest's, which still pass.
 */
@RunWith(AndroidJUnit4::class)
class IdeaShapesMapTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context get() = instrumentation.targetContext

    @Before fun clearChoices() {
        context.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() { scenario?.close() }

    @Test fun ringsDropAndPinchOnSpace() {
        val dir = File(context.cacheDir, "shapes-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path)
            .putExtra(MainActivity.UNDO_STAYS, true))
        compose.waitUntil(20_000) { shown(IdeasTags.TITLE) }
        val tablet = !shown(SortaMode.MAP.identifier)
        if (!tablet) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        compose.waitUntil(20_000) { shown(MapLook.CHIP) }
        try { compose.waitUntil(60_000) { framed(mapDrawn()) } } catch (_: ComposeTimeoutException) {}
        check(framed(mapDrawn())) { "Space was not drawn: ${mapDrawn()}" }
        putCardAway()

        // A2: beside the map, the ideas like the open one are ringed
        if (tablet) {
            compose.onNode(hasText(barrel.title)).performClick()
            compose.waitUntil(10_000) { shown(EDITOR_TAG) }
            try { compose.waitUntil(15_000) { (mapDrawn()?.glows?.size ?: 0) >= 1 } } catch (_: ComposeTimeoutException) {}
            val drawn = checkNotNull(mapDrawn())
            shot("shapes-glow-space-$device")
            check(drawn.glows.isNotEmpty()) { "No idea like the barrel is ringed on the map: $drawn" }
            // put the editor away, so a tap on a ringed idea chooses it rather than linking it
            compose.onNode(hasText(context.getString(R.string.ward_done)) and hasAnyAncestor(hasTestTag(EDITOR_TAG))).performClick()
            compose.waitUntil(10_000) { !shown(EDITOR_TAG) }
            compose.waitUntil(10_000) { mapDrawn()?.glows?.isEmpty() == true }
        }

        // A4: an idea held and moved onto another rings it twice; let go there, the two collide
        val map = SemanticsMatcher.keyIsDefined(MapDrawnKey)
        try { compose.waitUntil(10_000) { mapDrawn()?.ideaSpot?.size == 3 } } catch (_: ComposeTimeoutException) {}
        val spot = checkNotNull(mapDrawn()?.ideaSpot?.takeIf { it.size == 3 }) { "No idea to hold on the map: ${mapDrawn()}" }
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        compose.onNode(map).performTouchInput { down(Offset(spot[1], spot[2])); advanceEventTime(300); repeat(3) { moveBy(Offset(slop, 0f)) } }
        var tries = 0
        while ((mapDrawn()?.drop ?: -1) < 0 && tries < 10) {
            tries++
            SystemClock.sleep(400)
            compose.waitForIdle()
            val near = mapDrawn()?.near?.takeIf { it.size == 3 } ?: continue
            compose.onNode(map).performTouchInput { moveTo(Offset(near[1], near[2])) }
            SystemClock.sleep(400)
            compose.waitForIdle()
        }
        val held = checkNotNull(mapDrawn())
        shot("shapes-drop-space-$device")
        check(held.drop >= 0) { "No drop target was ringed after $tries moves: $held" }
        compose.onNode(map).performTouchInput { up() }
        compose.waitUntil(10_000) { shown(IdeaShapeTags.COLLIDE) }
        compose.onNode(hasText(context.getString(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(IdeaShapeTags.COLLIDE))).performClick()
        compose.waitUntil(10_000) { !shown(IdeaShapeTags.COLLIDE) }
        SystemClock.sleep(1_500) // the held idea springs home
        compose.waitForIdle()

        // A5: the idea chosen with a tap, pinched apart, opens the split chooser
        try { compose.waitUntil(10_000) { mapDrawn()?.ideaSpot?.size == 3 } } catch (_: ComposeTimeoutException) {}
        val pick = checkNotNull(mapDrawn()?.ideaSpot?.takeIf { it.size == 3 }) { "No idea to choose: ${mapDrawn()}" }
        val index = pick[0].toInt()
        compose.onNode(map).performTouchInput { click(Offset(pick[1], pick[2])) }
        compose.waitUntil(10_000) { mapDrawn()?.chosen == index }
        SystemClock.sleep(1_500) // the camera's ease towards it, finished
        compose.waitForIdle()
        val body = mapDrawn()?.names?.firstOrNull { it.index == index }?.body ?: listOf(pick[1], pick[2])
        val at = Offset(body[0], body[1])
        val density = context.resources.displayMetrics.density
        val near = 15f * density
        val far = 80f * density
        compose.onNode(map).performTouchInput {
            pinch(at - Offset(near, 0f), at - Offset(far, 0f), at + Offset(near, 0f), at + Offset(far, 0f), 600)
        }
        try { compose.waitUntil(10_000) { shown(IdeaShapeTags.SPLIT) } } catch (_: ComposeTimeoutException) {}
        shot("shapes-pinch-split-space-$device")
        check(shown(IdeaShapeTags.SPLIT)) { "A pinch apart on chosen idea $index at $at opened no split chooser: ${mapDrawn()}" }
        compose.onNode(hasText(context.getString(R.string.sheet_cancel)) and hasAnyAncestor(hasTestTag(IdeaShapeTags.SPLIT))).performClick()
        compose.waitUntil(10_000) { !shown(IdeaShapeTags.SPLIT) }
    }

    private fun putCardAway() {
        if (!shown(MapPanelTags.HINT)) return
        compose.onNode(hasText(context.getString(R.string.map_touch_got_it)) and hasAnyAncestor(hasTestTag(MapPanelTags.HINT))).performClick()
        compose.waitUntil(10_000) { !shown(MapPanelTags.HINT) }
        SystemClock.sleep(1_000)
        compose.waitForIdle()
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun mapNode(): SemanticsNode? = compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapDrawnKey)).fetchSemanticsNodes().firstOrNull()
    private fun mapDrawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)
    private fun framed(drawn: MapDrawn?) = drawn != null && drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    /** One still of the screen as [name].png, as MapModeSpaceRenderTest takes them. */
    private fun shot(name: String) {
        compose.waitForIdle()
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.waitForIdle()
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        SystemClock.sleep(600)
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        private const val T = 1_793_000_000_000L
        private const val HOUR = 3_600_000L
        private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        // made up, not medical: the barrel, in To sort, shares a rare word with three older ideas; every idea is two sentences or more
        val garden = NoteFolder(id = id(401), name = "Garden", createdAt = T)
        val home = NoteFolder(id = id(402), name = "Home", createdAt = T + 1)
        val trips = NoteFolder(id = id(403), name = "Trips", createdAt = T + 2)
        val folders = listOf(garden, home, trips)
        val barrel = Note(id = id(41), title = "Rain barrel for the greenhouse", body = "Catch the rain from the gutter. Water the tomatoes with it.",
            createdAt = T + 9 * HOUR)
        val notes = listOf(
            barrel,
            Note(id = id(42), title = "Paint the shed green", body = "Two coats.", folderId = garden.id, createdAt = T + 6 * HOUR),
            Note(id = id(43), title = "Tomatoes in pots", body = "Cherry ones on the balcony.", folderId = garden.id, createdAt = T + 5 * HOUR),
            Note(id = id(44), title = "Fix the gutter", body = "It leaks over the back door.", folderId = home.id, createdAt = T + 4 * HOUR),
            Note(id = id(45), title = "Greenhouse heater", body = "A small one, for the frost.", folderId = garden.id, createdAt = T + 3 * HOUR),
            Note(id = id(46), title = "Book the train to the coast", body = "Saturday, early. Bring the kite.", folderId = trips.id, createdAt = T + 2 * HOUR),
            Note(id = id(47), title = "Plan the weekend walk", body = "Up the hill. Back by noon.", createdAt = T + 1 * HOUR),
        )
    }
}
