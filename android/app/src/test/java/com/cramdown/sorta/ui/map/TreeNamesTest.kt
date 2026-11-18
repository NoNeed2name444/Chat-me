package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.TreeColors
import com.cramdown.sorta.map.TreeLayout3D
import com.cramdown.sorta.map.TreeModel
import com.cramdown.sorta.map.nest.NestNodes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// S11, the 3D Tree's names: no two drawn name boxes ever intersect, halo and all, on a phone, a tablet
// and an open foldable, at the first view, orbited 40 and 120 degrees, and flown in to Cardiology; and
// they are placed branches first, then by weight. The camera and the projection are the map's own
// (MapScene.tree's first view, OrbitCamera, TreeScreen.project, the renderer's per-frame path).
class TreeNamesTest {
    private val sample: NestNodes by lazy {
        val t = JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
            .getJSONArray("trees").getJSONObject(0)
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title"); val folder = t.getJSONArray("folder")
        val n = parent.length()
        NestNodes(Array(n) { "$it" }, IntArray(n) { parent.getInt(it) }, Array(n) { title.getString(it) },
            BooleanArray(n) { folder.getInt(it) == 1 }, IntArray(n) { -1 })
    }
    private val model by lazy { TreeModel(sample, TreeLayout3D(sample), TreeColors(false), GraphicsTier.HIGH) }

    /** Words as wide as a bold rounded face sets them: 0.56 of the size a character. */
    private class Metrics(private val k: Float) : NameMetrics {
        override fun width(label: String, size: Float) = label.length * size * k
        override fun ascent(size: Float) = -0.95f * size
        override fun descent(size: Float) = 0.27f * size
    }

    private class Window(val name: String, val width: Int, val height: Int, val density: Float, val top: Float, val bottom: Float)
    private val windows = listOf(
        Window("phone", 1080, 2400, 2.625f, 260f, 420f),
        Window("tablet", 2560, 1600, 2f, 180f, 200f),
        Window("foldable open", 2208, 1840, 2.5f, 220f, 260f))

    private fun style(d: Float) = TreeNames.Style(bough = 13f * d, leaf = 11.5f * d, halo = 2.5f * d, gapX = 6f * d, gapY = 3f * d,
        edge = 12f * d, leastLeaf = 5f * d)

    /** The names at [w]'s first view turned by [yaw], or flown in to [into]'s branch. */
    private fun names(w: Window, yaw: Float, into: Int = -1): Pair<List<ScreenNode>, List<PlacedName>> {
        val scene = MapScene.tree(model)
        val camera = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
        val insets = MapInsets(top = w.top, bottom = w.bottom)
        HomeView.framer(scene)(w.width.toFloat(), w.height.toFloat(), insets).applyTo(camera)
        camera.orbit(yaw, 0f)
        if (into >= 0) {
            val l = model.layout
            val c = com.cramdown.sorta.map.Vec3(l.centre[3 * into].toFloat(), l.centre[3 * into + 1].toFloat(), l.centre[3 * into + 2].toFloat())
            val fit = camera.framing(c, l.reach[into].toFloat(), w.width.toFloat(), w.height.toFloat(), insets, com.cramdown.sorta.map.MapFly.FILL)
            camera.target = fit.target; camera.distance = fit.distance
        }
        val viewProj = Mat4.multiply(camera.projection(w.width.toFloat() / w.height), camera.view())
        val screen = TreeScreen.project(model, viewProj, camera.focalPixels(w.height.toFloat()), w.width, w.height, null, FloatArray(4))
        val placed = TreeNames.layout(screen, sample, Metrics(0.6f), Metrics(0.56f), style(w.density), w.width.toFloat(), w.height.toFloat(), w.top, w.bottom,
            focus = into.coerceAtLeast(0))
        return screen to placed
    }

    private val cardiology get() = sample.titles.indexOf("Cardiology")

    @Test fun noTwoNamesEverOverlap() {
        for (w in windows) for ((what, yaw, into) in listOf(Triple("whole", 0f, -1), Triple("orbited 40", rad(40f), -1),
                Triple("orbited 120", rad(120f), -1), Triple("Cardiology", 0f, cardiology), Triple("Cardiology orbited 40", rad(40f), cardiology))) {
            val (screen, placed) = names(w, yaw, into)
            assertTrue("${w.name} $what: bodies on screen", screen.size > 20)
            assertTrue("${w.name} $what: names placed (${placed.size})", placed.size >= 8)
            assertFalse("${w.name} $what: two names overlap", TreeNames.overlaps(placed))
            for (p in placed) {
                val b = p.box
                assertTrue("${w.name} $what: ${p.label} inside the view", b.x - b.width / 2 >= 0f && b.x + b.width / 2 <= w.width)
                assertTrue("${w.name} $what: ${p.label} clear of the chrome", b.y - b.height / 2 >= w.top && b.y + b.height / 2 <= w.height - w.bottom)
            }
        }
    }

    @Test fun theBigBranchesAreNamedAtTheFirstView() {
        for (w in windows) {
            val (_, placed) = names(w, 0f)
            val labels = placed.map { it.label }.toSet()
            for (want in listOf("All ideas", "Examples", "Cardiology")) assertTrue("${w.name}: $want named, got $labels", want in labels)
        }
    }

    @Test fun flownInToCardiologyItsOwnPartsAreNamed() {
        for (w in windows) {
            val (_, placed) = names(w, 0f, cardiology)
            val inside = placed.count { sample.within(cardiology, it.index) }
            assertTrue("${w.name}: Cardiology's own parts named ($inside)", inside >= 8)
        }
    }

    @Test fun branchesComeFirstThenWeight() {
        val (screen, _) = names(windows[0], 0f)
        val order = TreeNames.order(screen, sample)
        val firstLeaf = order.indexOfFirst { !TreeNames.bough(sample, it.index) }
        assertTrue(firstLeaf > 0)
        for (k in firstLeaf until order.size) assertFalse(TreeNames.bough(sample, order[k].index))
        for (k in 1 until firstLeaf) assertTrue(sample.weight[order[k - 1].index] >= sample.weight[order[k].index])
        assertEquals(0, order[0].index)
    }

    @Test fun aNameThatCannotFitIsDropped() {
        // every body at one point: only the first name gets a clear box at each of its spots
        val pile = (0 until sample.size).map { ScreenNode(it, 500f, 900f, 10f) }
        val placed = TreeNames.layout(pile, sample, Metrics(0.6f), Metrics(0.56f), style(2.625f), 1080f, 2400f, 0f, 0f)
        assertTrue(placed.size in 1..5)
        assertFalse(TreeNames.overlaps(placed))
        assertEquals(0, placed[0].index)
    }

    private fun rad(d: Float) = (d * Math.PI / 180).toFloat()
}
