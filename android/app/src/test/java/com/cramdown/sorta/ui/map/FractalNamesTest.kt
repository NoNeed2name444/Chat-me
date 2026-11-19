package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.FractalFit
import com.cramdown.sorta.map.FractalFixture
import com.cramdown.sorta.map.FractalLayout3D
import com.cramdown.sorta.map.FractalModel
import com.cramdown.sorta.map.FractalSight
import com.cramdown.sorta.map.FractalTree
import com.cramdown.sorta.map.MapFly
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

// S12, the 3D Fractal's names: no two drawn name boxes ever intersect, halo and all, and no name covers
// a named dot or a dot as big as its own, on a phone, a tablet and an open foldable, at the first view, orbited 40 and
// 120 degrees, and flown in to Cardiology; All ideas, Examples and Cardiology are named at the first
// view, and Cardiology's own parts once flown in; each name's nearest dot is its own, a tap on a name
// lands on its part, and TalkBack reaches every hub drawn in the focus. The camera and the projection are
// the map's own (fractalScene's first view and the renderer's fly-in, both framed to what they draw,
// OrbitCamera, FractalSight, FractalNames.screen, the renderer's path).
class FractalNamesTest {
    private val fx by lazy { FractalFixture.read() }
    /** The concept's sample as the fixture holds it. */
    private val concept: FractalTree by lazy { FractalFixture.tree(fx) }
    /**
     * The same sample as the app grows it from a store (FractalTree.of: each folder's folders, then its
     * ideas, oldest first), as the emulator's FractalMapRenderTest holds it: its parts leave in another
     * order, so its names crowd differently.
     */
    private val store: FractalTree by lazy {
        val c = concept
        val folders = (1 until c.count).filter { c.childCount[it] > 0 }.map {
            FractalTree.Companion.Folder("p$it", if (c.parent[it] == 0) null else "p${c.parent[it]}", c.titles[it], T + it) }
        val ideas = (1 until c.count).filter { c.childCount[it] == 0 }.map {
            FractalTree.Companion.Idea("p$it", if (c.parent[it] == 0) null else "p${c.parent[it]}", c.titles[it], T + it * 60_000L) }
        FractalTree.of(folders, ideas, "All ideas")
    }
    private val trees by lazy { listOf("concept" to concept, "store" to store) }
    private val models by lazy { trees.mapIndexed { k, (_, t) -> FractalModel(t, FractalLayout3D(t), if (k == 0) FractalFixture.links(fx) else IntArray(0)) } }
    /** Which of [trees] the checks run on now. */
    private var tree = 0
    private val sample: FractalTree get() = trees[tree].second
    private val model: FractalModel get() = models[tree]
    private fun eachTree(check: () -> Unit) { for (t in trees.indices) { tree = t; check() } }

    /** Words as wide as a bold rounded face sets them. */
    private class Metrics(private val k: Float) : NameMetrics {
        override fun width(label: String, size: Float) = label.length * size * k
        override fun ascent(size: Float) = -0.95f * size
        override fun descent(size: Float) = 0.27f * size
    }

    /** A map [width] by [height] pixels under [top] and over [bottom] of chrome, naming at least [least] at each pose. */
    private class Window(val name: String, val width: Int, val height: Int, val density: Float, val top: Float, val bottom: Float,
                         val least: Int = 6, val bar: Float = bottom)
    private val windows get() = listOf(
        Window("${trees[tree].first} phone", 1080, 2400, 2.625f, 260f, 420f),
        Window("${trees[tree].first} phone, first visit", 1080, 2400, 2.625f, 270f, 900f, bar = 420f),
        Window("${trees[tree].first} tablet", 2560, 1600, 2f, 180f, 200f),
        Window("${trees[tree].first} foldable open", 2208, 1840, 2.5f, 220f, 260f),
        // the open foldable's map pane beside the Sort list, under the chips and over the first visit's card, as the
        // emulator's FractalMapRenderTest draws it (run 44398433580): only All ideas and its four top folders are crisp
        Window("${trees[tree].first} foldable pane, first visit", 1325, 1777, 2.625f, 280f, 740f, least = 5, bar = 400f))

    private fun style(d: Float) = TreeNames.Style(bough = 13f * d, leaf = 11.5f * d, halo = 2.5f * d, gapX = 6f * d, gapY = 3f * d,
        edge = 12f * d, leastLeaf = 5f * d)

    private val cardiology get() = sample.titles.indexOf("Cardiology")

    /** The dots on screen and the names at [w]'s first view turned by [yaw], or flown in to [into]'s cluster. */
    private fun names(w: Window, yaw: Float, into: Int = -1,
                      chrome: List<androidx.compose.ui.geometry.Rect> = emptyList()): Pair<List<ScreenNode>, List<PlacedName>> {
        val scene = fractalScene(model)
        val camera = firstCamera(MapTheme.FRACTAL, Vec3.ZERO)
        val insets = MapInsets(top = w.top, bottom = w.bottom)
        HomeView.framer(scene, w.density)(w.width.toFloat(), w.height.toFloat(), insets).applyTo(camera)
        camera.orbit(yaw, 0f)
        if (into >= 0) {
            // as the renderer's fly-in frames a hub (GlMapRenderer.flyToHub), with a sight of its own on the hub
            // in the room the chrome's boxes leave, as Map3DView's fly-in (MapClear.around)
            val room = MapClear.around(insets, chrome, w.height.toFloat())
            val fitting = FractalSight(model).also { it.focus = into }
            val pose = FractalFit.flyIn(model, into, camera.yaw, camera.pitch, camera.fovY, w.width.toFloat(), w.height.toFloat(), room,
                fitting, w.density)
            camera.target = pose.target; camera.distance = pose.distance
        }
        val viewProj = Mat4.multiply(camera.projection(w.width.toFloat() / w.height), camera.view())
        val focal = camera.focalPixels(w.height.toFloat())
        val sight = FractalSight(model)
        sight.focus = into.coerceAtLeast(0)
        sight.step(viewProj, focal, w.width.toFloat(), w.height.toFloat(), w.density)
        val screen = FractalNames.screen(sight, focal)
        val placed = FractalNames.layout(screen, sample, Metrics(0.6f), Metrics(0.56f), style(w.density), w.width.toFloat(), w.height.toFloat(),
            w.top, w.bottom, focus = into.coerceAtLeast(0), clear = MapClear.boxes(chrome))
        return screen to placed
    }

    /** Back's place under the top chrome and the two round tools over the bottom bar (beside a first visit's card), as IdeasFractal sets them. */
    private fun chrome(w: Window, back: Boolean): List<androidx.compose.ui.geometry.Rect> {
        val g = 16f * w.density; val t = 48f * w.density
        val tools = androidx.compose.ui.geometry.Rect(w.width - t - 2 * g, w.height - w.bar - 2 * t - 3 * g, w.width.toFloat(), w.height - w.bar)
        return if (back) listOf(androidx.compose.ui.geometry.Rect(g, w.top + g, g + t, w.top + g + t), tools) else listOf(tools)
    }

    // the lead's review of run 44449703726's stills: a name ran under Back and under the round tools
    @Test fun noNameGoesUnderBackOrTheTools() = eachTree {
        for (w in windows) for ((what, into) in listOf("whole" to -1, "Cardiology" to cardiology)) {
            val boxes = chrome(w, into > 0)
            val (_, placed) = names(w, 0f, into, chrome = boxes)
            for (p in placed) for (r in boxes) {
                val b = p.box
                val apart = b.x + b.width / 2 <= r.left || b.x - b.width / 2 >= r.right || b.y + b.height / 2 <= r.top || b.y - b.height / 2 >= r.bottom
                assertTrue("${w.name} $what: ${p.label} at $b under the chrome $r", apart)
            }
            if (into > 0) assertTrue("${w.name}: Cardiology's own parts still named", placed.count { sample.holds(cardiology, it.index) } >= 6)
        }
    }

    @Test fun noTwoNamesEverOverlapNorCoverADot() = eachTree {
        for (w in windows) for ((what, yaw, into) in listOf(Triple("whole", 0f, -1), Triple("orbited 40", rad(40f), -1),
                Triple("orbited 120", rad(120f), -1), Triple("Cardiology", 0f, cardiology), Triple("Cardiology orbited 40", rad(40f), cardiology))) {
            val (screen, placed) = names(w, yaw, into)
            assertTrue("${w.name} $what: dots on screen (${screen.size})", screen.size > 20)
            assertTrue("${w.name} $what: names placed (${placed.size})", placed.size >= w.least)
            assertFalse("${w.name} $what: two names overlap", TreeNames.overlaps(placed))
            assertFalse("${w.name} $what: a name covers a named dot or a bigger one", FractalNames.coversADot(placed, screen))
            for (p in placed) {
                val b = p.box
                assertTrue("${w.name} $what: ${p.label} inside the view", b.x - b.width / 2 >= 0f && b.x + b.width / 2 <= w.width)
                assertTrue("${w.name} $what: ${p.label} clear of the chrome", b.y - b.height / 2 >= w.top && b.y + b.height / 2 <= w.height - w.bottom)
                assertTrue("${w.name} $what: ${p.label} has its dot", screen.any { it.index == p.index && it.crisp })
            }
        }
    }

    // the review of S12's stills: a far row's name stood nearer another dot than its own, and read as that one's
    @Test fun everyNamesNearestDotIsItsOwn() = eachTree {
        for (w in windows) for ((what, yaw, into) in poses()) {
            val (screen, placed) = names(w, yaw, into)
            val near = FractalNames.nearest(placed, screen, sample, style(w.density).leastLeaf)
            for ((p, n) in placed.zip(near)) assertEquals("${w.name} $what: ${p.label} by ${if (n >= 0) sample.titles[n] else "none"}", p.index, n)
        }
    }

    // the review: a tap on a long hub name went Back, as only dots were hit
    @Test fun aTapOnANameLandsOnItsPart() = eachTree {
        for (w in windows) for ((what, yaw, into) in poses()) {
            val (_, placed) = names(w, yaw, into)
            val least = 48f * w.density
            for (p in placed) {
                assertEquals("${w.name} $what: ${p.label} at its middle", p.index, FractalNames.nameAt(placed, p.box.x, p.box.y, least))
                assertEquals("${w.name} $what: ${p.label} at its end", p.index, FractalNames.nameAt(placed, p.box.x + p.box.width / 2 - 1f, p.box.y, least))
                // S12's iPad UI test: a tap in a name's touch target, off its words, fell to a dot by it. Each way
                // round a name, just inside its target, a tap lands on it, or on a name whose target holds it nearer
                val hw = max(least, p.box.width) / 2 - 0.5f; val hh = max(least, p.box.height) / 2 - 0.5f
                for ((tx, ty) in listOf(p.box.x to p.box.y + hh, p.box.x to p.box.y - hh, p.box.x + hw to p.box.y,
                        p.box.x - hw to p.box.y, p.box.x + hw to p.box.y + hh)) {
                    val got = FractalNames.nameAt(placed, tx, ty, least)
                    val others = placed.filter { it.index != p.index &&
                        abs(tx - it.box.x) * 2 <= max(least, it.box.width) && abs(ty - it.box.y) * 2 <= max(least, it.box.height) }
                    assertTrue("${w.name} $what: ${p.label}'s target at $tx, $ty lands on $got",
                        got == p.index || others.any { it.index == got })
                }
            }
        }
    }

    // the review: a hub with no name shown could not be reached by TalkBack
    @Test fun talkBackReachesEveryHubDrawnInTheFocus() = eachTree {
        for (w in windows) for ((what, yaw, into) in poses()) {
            val (screen, placed) = names(w, yaw, into)
            val least = 48f * w.density
            val targets = FractalNames.targets(screen, placed, sample, into.coerceAtLeast(0), least)
            val hubs = screen.filter { FractalNames.hub(sample, it.index) && (into <= 0 || sample.holds(into, it.index)) }.map { it.index }.toSet()
            val reached = targets.map { it.index }.toSet()
            assertTrue("${w.name} $what: every drawn hub reached (${hubs - reached})", reached.containsAll(hubs))
            assertTrue("${w.name} $what: every name reached", reached.containsAll(placed.map { it.index }))
            assertEquals("${w.name} $what: each part once", targets.size, reached.size)
            for (t in targets) assertTrue("${w.name} $what: ${sample.titles[t.index]} is a touch target", t.width >= least && t.height >= least)
        }
    }

    // the review: TalkBack said "All ideas, folder" where VoiceOver said "All ideas"
    @Test fun allIdeasIsReadAsItsNameAndEveryOtherHubAsAFolder() = eachTree {
        assertFalse(FractalNames.spokenAsFolder(sample, 0))
        assertTrue(FractalNames.spokenAsFolder(sample, cardiology))
        val idea = (1 until sample.count).first { !FractalNames.hub(sample, it) }
        assertFalse(FractalNames.spokenAsFolder(sample, idea))
    }

    @Test fun turningKeepsTheBigHubsNamed() = eachTree {
        // the names come from the pose alone, as the map places them: none held from the last pose. At 120 degrees
        // Cardiology's parts stand round All ideas' dot, so a name of it there would read as theirs, and none is
        // drawn (the review of S12's stills, item 9: the orbit120 still named All ideas beside Cardiology's dots)
        for (w in windows) {
            for ((what, deg, want) in listOf(Triple("whole", 0f, listOf("All ideas", "Examples", "Cardiology")),
                    Triple("orbited 40", 40f, listOf("All ideas", "Cardiology")), Triple("orbited 120", 120f, listOf<String>()))) {
                val (screen, placed) = names(w, rad(deg))
                val labels = placed.map { it.label }.toSet()
                for (name in want) assertTrue("${w.name} $what: $name named, got $labels", name in labels)
                assertFalse("${w.name} $what: two names overlap", TreeNames.overlaps(placed))
                assertFalse("${w.name} $what: a name covers a named dot or a bigger one", FractalNames.coversADot(placed, screen))
                assertEquals("${w.name} $what: each name's nearest dot is its own", placed.map { it.index },
                    FractalNames.nearest(placed, screen, sample, style(w.density).leastLeaf))
                // the same pose again names the same, in the same spots
                assertEquals("${w.name} $what: the same pose names the same", placed, names(w, rad(deg)).second)
            }
        }
    }

    @Test fun theBigHubsAreNamedAtTheFirstView() = eachTree {
        for (w in windows) {
            val (_, placed) = names(w, 0f)
            val labels = placed.map { it.label }.toSet()
            for (want in listOf("All ideas", "Examples", "Cardiology")) assertTrue("${w.name}: $want named, got $labels", want in labels)
        }
    }

    @Test fun flownInToCardiologyItsOwnPartsAreNamed() = eachTree {
        for (w in windows) {
            val (_, placed) = names(w, 0f, cardiology)
            val inside = placed.count { sample.holds(cardiology, it.index) }
            assertTrue("${w.name}: Cardiology's own parts named ($inside)", inside >= 6)
            assertTrue("${w.name}: Cardiology itself named", placed.any { it.index == cardiology })
        }
    }

    @Test fun theFlownInClusterComesFirstThenHubsThenWeight() = eachTree {
        val (screen, _) = names(windows[0], 0f, cardiology)
        val order = FractalNames.order(screen, sample, cardiology)
        val firstOutside = order.indexOfFirst { !sample.holds(cardiology, it.index) }.let { if (it < 0) order.size else it }
        for (k in firstOutside until order.size) assertFalse(sample.holds(cardiology, order[k].index))
        val inside = order.take(firstOutside)
        val firstIdea = inside.indexOfFirst { !FractalNames.hub(sample, it.index) }.let { if (it < 0) inside.size else it }
        assertEquals("Cardiology leads", cardiology, inside[0].index)
        for (k in firstIdea until inside.size) assertFalse(FractalNames.hub(sample, inside[k].index))
        for (k in 1 until firstIdea) assertTrue(sample.weight[inside[k - 1].index] >= sample.weight[inside[k].index])
    }

    @Test fun theCostGuardsHold() {
        // every dot far apart on a huge view, each with room for its name: only MOST are drawn, the first in the order
        val grid = (0 until sample.count).map { ScreenNode(it, 400f + (it % 12) * 1600f, 400f + (it / 12) * 400f, 40f) }
        val placed = FractalNames.layout(grid, sample, Metrics(0.6f), Metrics(0.56f), style(2.625f), 20_000f, 20_000f, 0f, 0f)
        assertTrue("more dots than names allowed (${sample.count})", sample.count > TreeNames.MOST)
        assertEquals(TreeNames.MOST, placed.size)
        assertFalse(TreeNames.overlaps(placed))
        assertEquals(FractalNames.order(grid, sample, 0).take(TreeNames.MOST).map { it.index }, placed.map { it.index })
    }

    @Test fun aNameThatCannotFitIsDropped() {
        // every dot at one point: the dots cover every spot, so nothing is named over them
        val pile = (0 until sample.count).map { ScreenNode(it, 500f, 900f, 10f) }
        val placed = FractalNames.layout(pile, sample, Metrics(0.6f), Metrics(0.56f), style(2.625f), 1080f, 2400f, 0f, 0f)
        assertTrue(placed.size <= 6)
        assertFalse(TreeNames.overlaps(placed))
        assertFalse(FractalNames.coversADot(placed, pile))
    }

    private fun poses() = listOf(Triple("whole", 0f, -1), Triple("orbited 40", rad(40f), -1), Triple("orbited 120", rad(120f), -1),
        Triple("Cardiology", 0f, cardiology), Triple("Cardiology orbited 40", rad(40f), cardiology))

    private fun rad(d: Float) = (d * Math.PI / 180).toFloat()

    private companion object { const val T = 1_793_000_000_000L }
}
