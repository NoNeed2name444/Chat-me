package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.map.MapTap
import com.cramdown.sorta.ui.map.MapTapKey
import com.cramdown.sorta.ui.map.TreeTags
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// S11: the 3D Tree look in the real app, on the approved concept's sample (tree-fixture.json's "sample":
// a part with parts is a folder, the rest ideas): it opens from the look list with the first visit's
// card; a tap on a branch flies in (Back reads the level above, the card goes); a tap on empty space and
// Back fly out; a tap on a leaf shows the map's peek card. Robolectric draws no GL, so the taps go
// through the map's own hit test hand-off (MapTap), as MapPeekCardTest's do; the pixels are the
// emulator's (TreeMapRenderTest).
@RunWith(RobolectricTestRunner::class)
class TreeMapTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** The concept's sample as a store: folders and ideas, the ideas made one a minute in the concept's order. */
    private fun sample(): Pair<List<NoteFolder>, List<Note>> {
        val json = JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
        val t = json.getJSONArray("trees").getJSONObject(0)
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title")
        val n = parent.length()
        val kids = IntArray(n); for (i in 1 until n) kids[parent.getInt(i)]++
        fun id(i: Int) = "00000000-0000-4000-8000-%012x".format(Locale.ROOT, 0x7000 + i)
        val folders = ArrayList<NoteFolder>(); val notes = ArrayList<Note>()
        for (i in 1 until n) {
            val p = parent.getInt(i); val inside = if (p == 0) null else id(p)
            if (kids[i] > 0) folders.add(NoteFolder(id = id(i), name = title.getString(i), parentId = inside, createdAt = T + i))
            else notes.add(Note(id = id(i), title = title.getString(i), folderId = inside, createdAt = T + i * 60_000L))
        }
        return folders to notes
    }

    private fun run(window: String) = checks {
        val (folders, notes) = sample()
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, true, notes, folders))
        IdeasSettings(compose.activity).setLook(MapLook.TREE)
        compose.setContent { SortaTheme(dark = isSystemInDarkTheme()) { WindowShapeHost { IdeasView(model) } } }
        compose.waitForIdle()
        if (!shown(TreeTags.MAP) && shown(SortaMode.MAP.identifier)) compose.onNode(hasTestTag(SortaMode.MAP.identifier)).performClick()
        settle()
        check("$window: the Tree is up", shown(TreeTags.MAP))
        check("$window: the first visit's card", shown(TreeTags.FIRST_VISIT))
        check("$window: the map says it shows All ideas", spoken().contains("All ideas"), spoken())
        check("$window: no Back at the whole tree", !shown(TreeTags.BACK))

        val cardiology = folders.first { it.name == "Cardiology" }
        tap(cardiology.id)
        check("$window: flown in, Back is there", shown(TreeTags.BACK))
        check("$window: the card is put away", !shown(TreeTags.FIRST_VISIT))
        check("$window: the map says it shows Cardiology", spoken().contains("Cardiology"), spoken())

        val types = folders.first { it.name == "Types" }
        tap(types.id)
        check("$window: two levels in", spoken().contains("Types"), spoken())
        tapEmpty()
        check("$window: empty space flies out a level", spoken().contains("Heart failure"), spoken())

        val bnp = notes.first { it.title == "Raised in heart failure" }
        tap(bnp.id)
        check("$window: a leaf shows its card", shown(MapPanelTags.PEEK))
        tapEmpty()
        check("$window: empty space puts the card away first", !shown(MapPanelTags.PEEK) && spoken().contains("Heart failure"), spoken())

        compose.onNode(hasTestTag(TreeTags.BACK)).performClick(); settle()
        check("$window: Back flies out to Cardiology", spoken().contains("Cardiology"), spoken())
        compose.activity.onBackPressedDispatcher.onBackPressed(); settle()
        check("$window: the system's back flies out to the whole tree", spoken().contains("All ideas") && !shown(TreeTags.BACK), spoken())
    }

    private fun mapTap(): MapTap = checkNotNull(compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapTapKey)).fetchSemanticsNodes().first().config.getOrNull(MapTapKey))
    private fun tap(id: String) {
        val tap = mapTap()
        val i = tap.ids.indexOfFirst { it.equals(id, ignoreCase = true) }
        check(i >= 0) { "no body for $id" }
        compose.runOnUiThread { tap.tap(i) }
        settle()
    }
    private fun tapEmpty() { val tap = mapTap(); compose.runOnUiThread { tap.tap(-1) }; settle() }
    private fun spoken(): String = compose.onAllNodes(hasTestTag(TreeTags.MAP), useUnmergedTree = true).fetchSemanticsNodes()
        .flatMap { n -> listOf(n) + n.children }.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.joinToString(" | ") +
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(MapTapKey)).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.joinToString(" | ")
    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun settle() {
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phone() = run("phone")

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tablet() = run("tablet")

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun foldableOpen() = run("foldable_open")

    companion object {
        private const val T = 1_793_000_000_000L
    }
}
