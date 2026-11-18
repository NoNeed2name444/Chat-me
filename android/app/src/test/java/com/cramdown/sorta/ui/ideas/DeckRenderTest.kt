package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChecks
import com.cramdown.sorta.ui.ward.WardPalette
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.ward.checks
import com.cramdown.sorta.ui.ward.hx
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// S06a A8: the sorting deck, "You were saying…", the frost and the low tide on made-up ideas
// (IdeasFixture), written to build/outputs/roborazzi/ as deck_<shot>_<window>_<mode>.png. Each PNG
// is read back as SortRenderTest's are (the window's size, the page's ground, no logo coral), and
// each shot's own parts are checked in the tree: the card and its targets (48 dp at least), the
// offer, a board card at each frost step, and the tide's card.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun record() {
        System.setProperty("roborazzi.test.record", "true")
    }

    @After fun freshLaunch() {
        IdeasSession.reset()
        IdeaClock.fixed = null
    }

    /** A shot: its name, the deck chosen, the clock, the last tide (days before the clock), and the offer already seen. */
    private class Shot(val name: String, val deck: Boolean, val now: Long, val tideDaysAgo: Long, val offerDone: Boolean, val ideas: List<Note>)

    private val shots = listOf(
        Shot("deck", deck = true, now = IdeasFixture.NOW, tideDaysAgo = 0, offerDone = true, ideas = DeckIdeas.marked),
        Shot("offer", deck = false, now = IdeasFixture.NOW, tideDaysAgo = 0, offerDone = false, ideas = DeckIdeas.marked),
        Shot("frost", deck = false, now = DeckIdeas.LATER, tideDaysAgo = 0, offerDone = true, ideas = DeckIdeas.frosted),
        Shot("tide", deck = false, now = DeckIdeas.LATER, tideDaysAgo = 8, offerDone = true, ideas = DeckIdeas.frosted),
    )

    private var which by mutableIntStateOf(-1)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(window: String, dark: Boolean, names: List<String>) = checks {
        val list = shots.filter { it.name in names }
        val cache = compose.activity.cacheDir
        val models = list.map { IdeasModel.open(IdeasFixture.store(cache, ideas = it.ideas)) }
        val settings = IdeasSettings(compose.activity)
        // Robolectric can't draw the GL map: the broad windows' map pane is the 2D look's board
        settings.setLook(MapLook.TWO_D)
        fun prepare(shot: Shot) {
            IdeasSession.reset()
            IdeaClock.fixed = shot.now
            IdeasSession.offerDone = shot.offerDone
            settings.setSortDeck(shot.deck)
            settings.setLastTide(shot.now - shot.tideDaysAgo * IdeasFixture.DAY)
        }
        prepare(list[0])
        which = 0
        compose.setContent {
            SortaTheme(dark = isSystemInDarkTheme()) {
                WindowShapeHost {
                    key(which) { IdeasView(models[which]) }
                }
            }
        }
        for (i in list.indices) {
            if (i > 0) { prepare(list[i]); which = i }
            val shot = list[i]
            val name = "deck_${shot.name}_${window}_${if (dark) "dark" else "light"}"
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            val file = File("build/outputs/roborazzi/$name.png")
            captureScreenRoboImage(file.path)
            look(this, name, file, dark)
            parts(this, name, shot, broad = !window.startsWith("phone"), short = window == "phone_short")
        }
    }

    private fun tagged(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()

    /** What each shot must hold, by its tags and words. */
    private fun parts(c: WardChecks, name: String, shot: Shot, broad: Boolean, short: Boolean) {
        val density = compose.activity.resources.displayMetrics.density
        fun tall(tag: String) = tagged(tag).all { it.boundsInRoot.height / density >= 47.5f && it.boundsInRoot.width / density >= 47.5f }
        when (shot.name) {
            "deck" -> {
                val card = tagged(DeckTags.CARD)
                c.check("$name: one card is up", card.size == 1, "${card.size}")
                val actions = card.firstOrNull()?.config?.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
                val garden = compose.activity.getString(R.string.deck_file_in, IdeasFixture.garden.name)
                c.check("$name: TalkBack offers File in Garden and Later", garden in actions && compose.activity.getString(R.string.deck_later) in actions, "$actions")
                c.check("$name: the top card is the unfinished one, and says so",
                    card.firstOrNull()?.config?.getOrNull(SemanticsProperties.StateDescription) == compose.activity.getString(R.string.deck_unfinished))
                val targets = tagged(DeckTags.TARGET)
                val cardTop = card.firstOrNull()?.boundsInRoot?.top ?: 0f
                c.check("$name: folders stand round the card", targets.size >= (if (short) 1 else 3), "${targets.size}")
                // round the card on a phone (the pane's stand beside it): above it, clear of it
                c.check("$name: ${if (broad) "the Later well stands" else "the targets and the Later well stand"} clear above the card",
                    ((if (broad) emptyList() else targets) + tagged(DeckTags.LATER)).all { it.boundsInRoot.bottom <= cardTop + 0.5f }, "card top $cardTop")
                c.check("$name: every target and the Later well is 48 dp or more", tall(DeckTags.TARGET) && tall(DeckTags.LATER))
                c.check("$name: ${if (broad) "the folders pane is beside the deck" else "a phone keeps the edge layout"}",
                    tagged(DeckTags.FOLDERS).isNotEmpty() == broad)
            }
            "offer" -> c.check("$name: To sort offers \"You were saying…\"", tagged(DeckTags.OFFER).size == 1)
            "frost" -> if (broad) {
                val words = compose.activity.getString(R.string.frost_spoken)
                val frosted = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(words, substring = true)).fetchSemanticsNodes()
                // the gate was opened 10 days ago (none); the boiler 40 (one), the brushes 100 (two), the rest never (three)
                c.check("$name: six board cards are frosted, and say so", frosted.size == 6, "${frosted.size}")
            }
            "tide" -> {
                c.check("$name: the low tide is up", tagged(DeckTags.TIDE).size == 1)
                val card = tagged(DeckTags.CARD).firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                c.check("$name: its first card is the idea left longest", card == IdeasFixture.bread.title, "$card")
            }
        }
    }

    private fun look(c: WardChecks, name: String, file: File, dark: Boolean) {
        c.check("$name: the PNG is written", file.isFile && file.length() > 0, file.absolutePath)
        val image = runCatching { javax.imageio.ImageIO.read(file) }.getOrNull()
        c.check("$name: the PNG reads back", image != null)
        if (image == null) return
        val metrics = compose.activity.resources.displayMetrics
        c.check("$name: the PNG is the window's size", image.width == metrics.widthPixels && image.height == metrics.heightPixels,
            "${image.width} x ${image.height} vs ${metrics.widthPixels} x ${metrics.heightPixels}")
        val page = WardPalette.hex(WardToken.background, dark)
        val corner = image.getRGB(image.width - 2, image.height - 2) and 0xFFFFFF
        c.check("$name: the ground is the ${if (dark) "dark" else "light"} page", corner == page, "${hx(corner)} vs ${hx(page)}")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val coral = pixels.count { (it and 0xFFFFFF) == LOGO_CORAL }
        c.check("$name: no pixel is the logo's coral ${hx(LOGO_CORAL)}", coral == 0, "$coral pixels")
    }

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
    @Test fun phoneLight() = render("phone", dark = false, names = listOf("deck", "offer", "tide"))

    @Config(sdk = [35], qualifiers = "w411dp-h914dp-port-night-xxhdpi")
    @Test fun phoneDark() = render("phone", dark = true, names = listOf("deck", "offer", "tide"))

    // a short window (half a phone's screen, or the keyboard up): the card keeps its room, and what fits above it stands clear
    @Config(sdk = [35], qualifiers = "w411dp-h560dp-port-notnight-xxhdpi")
    @Test fun phoneShortLight() = render("phone_short", dark = false, names = listOf("deck", "offer", "tide"))

    @Config(sdk = [35], qualifiers = "w841dp-h701dp-land-notnight-xhdpi")
    @Test fun foldableOpenLight() = render("foldable_open", dark = false, names = listOf("deck", "frost", "tide"))

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-notnight-xhdpi")
    @Test fun tabletLight() = render("tablet", dark = false, names = listOf("deck", "offer", "frost", "tide"))

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    @Test fun tabletDark() = render("tablet", dark = true, names = listOf("deck", "frost"))

    private companion object {
        /** The logo's coral (work/T0137: the light icon's fill). */
        const val LOGO_CORAL = 0xC9553E
    }
}

/** The made-up ideas for the deck's renders and checks (S06a): the fixture's, one left mid-sentence, or opened at set times. */
internal object DeckIdeas {
    /** 200 days after the clock of the renders: the frost's and the tide's. */
    const val LATER = IdeasFixture.NOW + 200 * IdeasFixture.DAY

    /** The gate's idea left mid-sentence, its caret after "which hinge". */
    val marked: List<Note> = IdeasFixture.notes.map {
        if (it.id == IdeasFixture.gate.id) it.copy(body = "Ask Sam which hinge fits, then", stoppedAt = 19) else it
    }

    /** At [LATER]: the gate opened 10 days before (no frost), the boiler 40 (one step), the brushes 100 (two), the rest never (three). */
    val frosted: List<Note> = IdeasFixture.notes.map {
        when (it.id) {
            IdeasFixture.gate.id -> it.copy(openedAt = LATER - 10 * IdeasFixture.DAY)
            IdeasFixture.boiler.id -> it.copy(openedAt = LATER - 40 * IdeasFixture.DAY)
            IdeasFixture.brushes.id -> it.copy(openedAt = LATER - 100 * IdeasFixture.DAY)
            else -> it
        }
    }
}
