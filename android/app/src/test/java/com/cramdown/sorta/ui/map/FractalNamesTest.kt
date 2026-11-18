package com.cramdown.sorta.ui.map

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

// S12, the 3D Fractal's names: no two drawn name boxes ever intersect, halo and all, and no name covers
// a named dot or a dot as big as its own, on a phone, a tablet and an open foldable, at the first view, orbited 40 and
// 120 degrees, and flown in to Cardiology; All ideas, Examples and Cardiology are named at the first
// view, and Cardiology's own parts once flown in. The camera and the projection are the map's own
// (fractalScene's first view, OrbitCamera, FractalSight, FractalNames.screen, the renderer's path).
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
                         val least: Int = 6)
    private val windows get() = listOf(
        Window("${trees[tree].first} phone", 1080, 2400, 2.625f, 260f, 420f),
        Window("${trees[tree].first} phone, first visit", 1080, 2400, 2.625f, 270f, 900f),
        Window("${trees[tree].first} tablet", 2560, 1600, 2f, 180f, 200f),
        Window("${trees[tree].first} foldable open", 2208, 1840, 2.5f, 220f, 260f),
        // the open foldable's map pane beside the Sort list, under the chips and over the first visit's card, as the
        // emulator's FractalMapRenderTest draws it (run 44398433580): only All ideas and its four top folders are crisp
        Window("${trees[tree].first} foldable pane, first visit", 1325, 1777, 2.625f, 280f, 740f, least = 5))

    private fun style(d: Float) = TreeNames.Style(bough = 13f * d, leaf = 11.5f * d, halo = 2.5f * d, gapX = 6f * d, gapY = 3f * d,
        edge = 12f * d, leastLeaf = 5f * d)

    private val cardiology get() = sample.titles.indexOf("Cardiology")

    /** The dots on screen and the names at [w]'s first view turned by [yaw], or flown in to [into]'s cluster. */
    private fun names(w: Window, yaw: Float, into: Int = -1, held: Map<Int, Int> = emptyMap()): Pair<List<ScreenNode>, List<PlacedName>> {
        val scene = fractalScene(model)
        val camera = firstCamera(MapTheme.FRACTAL, Vec3.ZERO)
        val insets = MapInsets(top = w.top, bottom = w.bottom)
        HomeView.framer(scene)(w.width.toFloat(), w.height.toFloat(), insets).applyTo(camera)
        camera.orbit(yaw, 0f)
        if (into >= 0) {
            val l = model.layout
            val c = Vec3(l.centre[3 * into].toFloat(), l.centre[3 * into + 1].toFloat(), l.centre[3 * into + 2].toFloat())
            val fit = camera.framing(c, l.reach[into].toFloat(), w.width.toFloat(), w.height.toFloat(), insets, MapFly.FILL)
            camera.target = fit.target; camera.distance = fit.distance
        }
        val viewProj = Mat4.multiply(camera.projection(w.width.toFloat() / w.height), camera.view())
        val focal = camera.focalPixels(w.height.toFloat())
        val sight = FractalSight(model)
        sight.focus = into.coerceAtLeast(0)
        sight.step(viewProj, focal, w.width.toFloat(), w.height.toFloat(), w.density)
        val screen = FractalNames.screen(sight, focal)
        val placed = FractalNames.layout(screen, sample, Metrics(0.6f), Metrics(0.56f), style(w.density), w.width.toFloat(), w.height.toFloat(),
            w.top, w.bottom, held = held, focus = into.coerceAtLeast(0))
        return screen to placed
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

    @Test fun turningKeepsTheBigHubsNamed() = eachTree {
        // each pose places its names trying the last pose's spots first, as the map does: a name held
        // from the first view must give way to a hub turned under it
        for (w in windows) {
            var held = emptyMap<Int, Int>()
            for ((what, deg, want) in listOf(Triple("whole", 0f, listOf("All ideas", "Examples", "Cardiology")),
                    Triple("orbited 40", 40f, listOf("All ideas", "Cardiology")), Triple("orbited 120", 120f, listOf("All ideas")))) {
                val (screen, placed) = names(w, rad(deg), held = held)
                val labels = placed.map { it.label }.toSet()
                for (name in want) assertTrue("${w.name} $what: $name named, got $labels", name in labels)
                assertFalse("${w.name} $what: two names overlap", TreeNames.overlaps(placed))
                assertFalse("${w.name} $what: a name covers a named dot or a bigger one", FractalNames.coversADot(placed, screen))
                held = placed.associate { it.index to it.spot }
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

    private fun rad(d: Float) = (d * Math.PI / 180).toFloat()

    private companion object { const val T = 1_793_000_000_000L }
}
