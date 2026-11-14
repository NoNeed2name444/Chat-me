package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WindowShapeHost
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S06a A1, A3, A5 and A7 by touch on a phone: a flick up keeps the card for later and Undo puts it
 * back; a drag onto a folder files it; the card's buttons and TalkBack's actions do the same; the
 * header's toggle is remembered; the offer opens and goes; the tide's Let go deletes with Undo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-notnight-xxhdpi")
class DeckTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun freshLaunch() {
        IdeasSession.reset()
        IdeaClock.fixed = null
    }

    private fun open(ideas: List<Note> = DeckIdeas.marked, deck: Boolean = true, now: Long = IdeasFixture.NOW, tideDaysAgo: Long = 0,
                     offerDone: Boolean = true): IdeasModel {
        IdeasSession.reset()
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, ideas = ideas))
        IdeaClock.fixed = now
        IdeasSession.offerDone = offerDone
        IdeasSettings(compose.activity).apply { setSortDeck(deck); setLastTide(now - tideDaysAgo * IdeasFixture.DAY) }
        compose.setContent { SortaTheme { WindowShapeHost { IdeasView(model, undoStays = true) } } }
        compose.waitForIdle()
        return model
    }

    private fun topCard(): String? = compose.onAllNodes(hasTestTag(DeckTags.CARD), useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    private fun string(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test fun aFlickUpKeepsItForLaterAndUndoPutsItBack() {
        open()
        val first = topCard()
        assertEquals(IdeasFixture.gate.title, first)
        compose.onNodeWithTag(DeckTags.CARD, useUnmergedTree = true).performTouchInput {
            down(center)
            for (k in 1..10) { advanceEventTime(16); moveTo(center + Offset(0f, -40f * k)) }
            up()
        }
        compose.waitForIdle()
        assertTrue("the next card is up", topCard() != first)
        compose.onNode(hasText(string(R.string.deck_later_done))).assertExists()
        compose.onNode(hasText(string(R.string.undo_action))).performClick()
        compose.waitForIdle()
        assertEquals("Undo puts it back on top", first, topCard())
    }

    @Test fun aDragOntoAFolderFilesIt() {
        val model = open()
        val target = compose.onNode(hasContentDescription(string(R.string.deck_file_in, IdeasFixture.garden.name)) and hasTestTag(DeckTags.TARGET),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val card = compose.onNodeWithTag(DeckTags.CARD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(DeckTags.CARD, useUnmergedTree = true).performTouchInput {
            val end = target - card.topLeft
            down(center)
            for (k in 1..12) { advanceEventTime(16); moveTo(center + (end - center) * (k / 12f)) }
            // held still a moment: no fling carries it past the folder
            advanceEventTime(120)
            moveTo(end + Offset(0.5f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals(IdeasFixture.garden.id, model.book.note(IdeasFixture.gate.id)?.folderId)
    }

    @Test fun theButtonsAndTalkBacksActionsDoTheSame() {
        val model = open()
        val first = topCard()
        compose.onNode(hasText(string(R.string.deck_keep_later)) and hasClickAction() and hasAnyAncestor(hasTestTag(DeckTags.CARD))).performClick()
        compose.waitForIdle()
        assertTrue("Later, by its button", topCard() != first)
        val next = topCard()
        val id = model.book.notes.first { it.title == next }.id
        val file = compose.onNodeWithTag(DeckTags.CARD, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == string(R.string.deck_file_in, IdeasFixture.kitchen.name) }
        compose.runOnUiThread { file.action() }
        compose.waitForIdle()
        assertEquals("File in Kitchen, by TalkBack", IdeasFixture.kitchen.id, model.book.note(id)?.folderId)
    }

    @Test fun theToggleIsRememberedAndShowsTheList() {
        open(deck = false)
        assertNull("the list first", topCard())
        compose.onNodeWithTag(DeckTags.TOGGLE, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNotNull("the deck", topCard())
        assertTrue("remembered", IdeasSettings(compose.activity).sortDeck)
    }

    @Test fun theOfferOpensTheIdeaOnceAndGoes() {
        open(deck = false, offerDone = false)
        compose.onNodeWithTag(DeckTags.OFFER, useUnmergedTree = true).assertExists()
        compose.onNode(hasText(string(R.string.offer_carry_on)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("taken, it is not offered again in this launch", IdeasSession.offerDone)
        assertTrue("no offer", compose.onAllNodes(hasTestTag(DeckTags.OFFER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun theTidesLetGoDeletesWithUndo() {
        val model = open(ideas = DeckIdeas.frosted, deck = false, now = DeckIdeas.LATER, tideDaysAgo = 8)
        assertEquals(IdeasFixture.bread.title, topCard())
        compose.onNode(hasText(string(R.string.tide_let_go)) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertNull("let go", model.book.note(IdeasFixture.bread.id))
        assertEquals("the next of the three", IdeasFixture.tulips.title, topCard())
        compose.onNode(hasText(string(R.string.undo_action))).performClick()
        compose.waitForIdle()
        assertNotNull("Undo brings it back", model.book.note(IdeasFixture.bread.id))
        compose.onNode(hasText(string(R.string.tide_keep)) and hasClickAction() and hasAnyAncestor(hasTestTag(DeckTags.CARD))).performClick()
        compose.waitForIdle()
        assertEquals("kept, it counts as opened", DeckIdeas.LATER, model.book.note(IdeasFixture.tulips.id)?.openedAt)
    }

    private fun hasClickAction() = androidx.compose.ui.test.hasClickAction()
}
