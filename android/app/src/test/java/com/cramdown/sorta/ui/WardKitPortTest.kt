package com.cramdown.sorta.ui

import com.cramdown.sorta.ui.ward.WardDeckEdge
import com.cramdown.sorta.ui.ward.WardEdge
import com.cramdown.sorta.ui.ward.WardIsland
import com.cramdown.sorta.ui.ward.WardPaper
import com.cramdown.sorta.ui.ward.WardPress
import com.cramdown.sorta.ui.ward.WardPressIndication
import com.cramdown.sorta.ui.ward.WardRect
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Wave 2 of the Android consistency report: the stand-ins written while the
// Ward kit was being ported are retired for the kit itself (ui/ward), one to
// one with iOS, and the iOS pieces that were missing are ported once.
class WardKitPortTest {
    private val ui = File("src/main/java/com/cramdown/sorta/ui")

    private fun sources(): List<File> = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun theQuizStandInsAreGone() {
        val gone = listOf("fun Modifier.quizFace(", "class QuizFace", "fun QuizTag(", "class QuizTone", "fun QuizChip(",
            "fun QuizCard(", "fun QuizSmallCaps(")
        val calls = Regex("(?<![A-Za-z])(quizFace|QuizChip|QuizTag|QuizCard|QuizSmallCaps)\\(")
        for (file in sources()) {
            val text = file.readText()
            for (piece in gone) assertTrue("${file.name} still defines $piece", !text.contains(piece))
            assertTrue("${file.name} still calls a quiz stand-in", !calls.containsMatchIn(text))
        }
    }

    @Test fun whatFloatsOverAPageIsUnlit() {
        // iOS WardSurfaces.swift WardUnlitKey, read by the faces: no lights round
        // a face, but a control keeps its rim and a well its hollow
        val faces = File(ui, "ward/WardFaces.kt").readText()
        assertTrue("an unlit local, off by default", faces.contains("val LocalWardUnlit = staticCompositionLocalOf { false }"))
        assertTrue("a press face keeps a control's rim", faces.contains("if ((control || !unlit) && !flat)"))
        assertTrue("a still face drops lights off its rim only", faces.contains("if (!spec.inner && !(unlit && !rim))"))
        assertTrue("the composable forms read it", faces.split("LocalWardUnlit.current").size - 1 == 2)
        val surfaces = File(ui, "ward/WardSurfaces.kt").readText()
        assertTrue("a raised card or slab drops its lights", surfaces.contains("LocalWardUnlit.current && !control"))
        assertTrue("a pressable tile drops its lights unless a control", surfaces.contains("val lit = control || !LocalWardUnlit.current"))
        // the owner, 9 Oct: "remove the glow around the deck and floating buttons"
        val decks = sources().map { it.readText() }.filter { it.contains("fun StudyActionBar(folds") }
        assertTrue("the folded study deck", decks.isNotEmpty() && decks.all { it.contains("WardUnlit {") })
    }

    @Test fun theStatesAreTheWardKitsAlone() {
        // iOS Shared/Ward/WardStates.swift: one empty state, banner, groove,
        // score bar and filter chip for every screen
        for (piece in listOf("fun WardEmptyState(", "fun WardBanner(", "fun WardGroove(", "fun WardProgressBar(", "fun WardFilterChip(")) {
            val homes = sources().filter { it.readText().contains(piece) }.map { "${it.parentFile.name}/${it.name}" }
            assertTrue("$piece is defined once, in ward/WardStates.kt: $homes", homes == listOf("ward/WardStates.kt"))
        }
        val copies = Regex("fun (LibraryEmptyState|LibraryBanner|LibraryGroove|EditorBanner|ReaderEmptyState)\\(|private fun EmptyState\\(")
        for (file in sources()) assertTrue("${file.name} keeps its own empty state or banner", !copies.containsMatchIn(file.readText()))
        // a groove and a banner are wells: the owner's rule, "wells take the page
        // colour and are shown by inset shadows only", so both fill with the
        // page-colour well (iOS WardStates.swift WardBanner, WardGroove: wardInset's .wardWell)
        val states = File(ui, "ward/WardStates.kt").readText()
        assertTrue("the groove and the banner fill with the page-colour well", states.split("fill = WardControlStates.wellFill").size - 1 == 2)
        assertTrue("no groove or banner fills with the old well token", !states.contains("fill = WardToken.well"))
    }

    @Test fun theDeckIsOneStudyActionBar() {
        // iOS Shared/Theme.swift StudyActionBar on WardDeckSlab (Shared/Ward/WardSurfaces.swift)
        val homes = sources().filter { it.readText().contains("fun StudyActionBar(") }.map { "${it.parentFile.name}/${it.name}" }
        assertTrue("StudyActionBar is defined once, in ward/WardDeck.kt: $homes", homes == listOf("ward/WardDeck.kt"))
        val copies = Regex("fun StudyBar\\(|import com\\.cramdown\\.sorta\\.ui\\.(mock|sources|editors)\\.StudyActionBar")
        for (file in sources()) assertTrue("${file.name} keeps its own study bar", !copies.containsMatchIn(file.readText()))
        val deck = File(ui, "ward/WardDeck.kt").readText()
        // the slab and the strip take the page's wash and the slab lies flat: it always holds a control
        assertTrue("the strip and the slab fill with the wash", deck.split("wardColor(WardToken.background)").size - 1 == 2)
        assertTrue("the slab casts no lights", !deck.contains("wardRaised("))
        assertTrue("the line joins the frame through WardEdge's arcs", deck.contains("wardIslandPath(edge.local"))
        assertTrue("the frame tells the decks where it is", File(ui, "ward/WardSurfaces.kt").readText().contains("WardEdgeStore.setWindow("))
    }

    @Test fun theDecksLineJoinsTheFrame() {
        // a phone, a small phone and a tablet, the deck at the foot of a page that spans the window
        for ((w, h, pad) in listOf(Triple(412.0, 915.0, false), Triple(360.0, 640.0, false), Triple(800.0, 1280.0, true))) {
            val panel = WardPaper.panel(w, h, pad = pad)!!
            val outline = WardEdge.outline(panel, pointsPerCurve = 12)
            val page = WardRect(0.0, 0.0, w, h)
            val host = WardRect(0.0, h - 48 - 84, w, 84.0)
            val edge = WardDeckEdge.solve(host, page, panel, outline)!!
            assertTrue("$w: both ends join the ring", edge.join.left != null && edge.join.right != null)
            assertTrue("$w: the line runs half its stroke under the bar's top", edge.join.y == host.minY + WardPaper.edgeWidth / 2)
            assertTrue("$w: the arcs meet the opening's sides",
                Math.abs(edge.local.left!!.join.x - panel.x) < 0.5 && Math.abs(edge.local.right!!.join.x - (panel.x + panel.width)) < 0.5)
            // the slab, 12 in from the bar's sides: on a phone its top corners are the arcs' circles;
            // the tablet's 18-point arcs are tighter than the slab's own 24
            val slab = WardRect(12.0, host.minY, w - 24, 76.0)
            val left = WardEdge.slabTopRadius(edge.join.left, slabSide = slab.minX, leading = true)
            val arc = edge.join.left!!.lineX - slab.minX
            assertTrue("$w: the slab's corner follows the arc", Math.abs(left - (if (pad) 24.0 else arc)) < 1e-9)
            val gap = WardIsland.foldClearance(slab, edge.join, WardIsland.foldInset(slab, edge.join))
            assertTrue("$w: the fold pill clears both curves", gap.slab >= 2 && gap.island >= 2)
            // a bar off the page's sides has no join; one at the trailing side joins there only
            assertTrue("$w: a bar inside the page", WardDeckEdge.solve(WardRect(24.0, host.y, w - 48, 84.0), page, panel, outline) == null)
            val trailing = WardDeckEdge.solve(WardRect(w / 2, host.y, w / 2, 84.0), page, panel, outline)!!
            assertTrue("$w: a trailing bar joins its own side", trailing.join.left == null && trailing.join.right != null)
        }
        assertTrue("no opening, no join", WardDeckEdge.solve(WardRect(0.0, 0.0, 40.0, 10.0), WardRect(0.0, 0.0, 40.0, 40.0), null, emptyList()) == null)
    }

    @Test fun aWaitIsTheEcgTrace() {
        // no spinner: Stethoscore's one loader was iOS EcgLoader (Shared/Ward/WardVitals.swift), which Sorta leaves out
        val spinner = Regex("(Circular|Linear)ProgressIndicator\\(")
        for (file in sources().filter { !it.path.contains("/ui/map/") })
            assertTrue("${file.name} still spins", !spinner.containsMatchIn(file.readText()))
    }

    @Test fun aPlainPressSinksAndShadesAndNeverRipples() {
        // iOS PressableRowStyle (Shared/Theme.swift): held, a plain row sinks (capPop) and shades (brightness -0.04)
        assertTrue("the theme gives every plain clickable the Ward press",
            File(ui, "Theme.kt").readText().contains("LocalIndication provides WardPressIndication"))
        assertTrue("at rest it is drawn as it is", WardPressIndication.scale(0f) == 1f && WardPressIndication.shade(0f) == 0f)
        assertTrue("held it sinks as capPop does", Math.abs(WardPressIndication.scale(1f) - WardPress.sinkScale) < 1e-6)
        assertTrue("and shades as iOS's brightness(-0.04)", Math.abs(WardPressIndication.shade(1f) - 0.04) < 1e-6)
        val ink = Regex("(?<![A-Za-z])(ripple|rememberRipple)\\(")
        for (file in sources().filter { !it.path.contains("/ui/map/") })
            assertTrue("${file.name} asks for Material's ripple", !ink.containsMatchIn(file.readText()))
    }


}
