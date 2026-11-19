package com.cramdown.sorta.ui.map

import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.LabelBox
import com.cramdown.sorta.map.LabelLink
import com.cramdown.sorta.map.LabelRoom
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
    private val fixture by lazy {
        JSONObject(requireNotNull(javaClass.classLoader?.getResource("tree/tree-fixture.json")).readText())
            .getJSONArray("trees").getJSONObject(0)
    }
    /** The concept's sample as the fixture holds it. */
    private val concept: NestNodes by lazy {
        val t = fixture
        val parent = t.getJSONArray("parent"); val title = t.getJSONArray("title"); val folder = t.getJSONArray("folder")
        val n = parent.length()
        NestNodes(Array(n) { "$it" }, IntArray(n) { parent.getInt(it) }, Array(n) { title.getString(it) },
            BooleanArray(n) { folder.getInt(it) == 1 }, IntArray(n) { -1 })
    }
    /**
     * The same sample as the app grows it from a store (treeNodes: a part with parts is a folder, folders
     * in the folder list's order, ideas oldest first), as TreeMapTest and the emulator's TreeMapRenderTest
     * hold it: its branches leave in another order, so its names crowd differently.
     */
    private val store: NestNodes by lazy {
        val parent = fixture.getJSONArray("parent"); val title = fixture.getJSONArray("title")
        val n = parent.length()
        val kids = IntArray(n); for (i in 1 until n) kids[parent.getInt(i)]++
        fun id(i: Int) = "p$i"
        val folders = (1 until n).filter { kids[it] > 0 }.sortedWith(compareBy(com.cramdown.sorta.ideas.NaturalOrder) { title.getString(it) })
            .map { com.cramdown.sorta.map.nest.NestFolderIn(id(it), if (parent.getInt(it) == 0) null else id(parent.getInt(it)), title.getString(it)) }
        val ideas = (1 until n).filter { kids[it] == 0 }
            .map { com.cramdown.sorta.map.nest.NestIdeaIn(id(it), if (parent.getInt(it) == 0) null else id(parent.getInt(it)), title.getString(it), it * 60_000L) }
        NestNodes.build("All ideas", folders, ideas)
    }
    private val trees by lazy { listOf("concept" to concept, "store" to store) }
    private val models by lazy { trees.map { (_, t) -> TreeModel(t, TreeLayout3D(t), TreeColors(false), GraphicsTier.HIGH) } }
    /** Which of [trees] the checks run on now. */
    private var tree = 0
    private val sample: NestNodes get() = trees[tree].second
    private val model: TreeModel get() = models[tree]
    private fun eachTree(check: () -> Unit) { for (t in trees.indices) { tree = t; check() } }

    /** Words as wide as a bold rounded face sets them: 0.56 of the size a character. */
    private class Metrics(private val k: Float) : NameMetrics {
        override fun width(label: String, size: Float) = label.length * size * k
        override fun ascent(size: Float) = -0.95f * size
        override fun descent(size: Float) = 0.27f * size
    }

    private class Window(val name: String, val width: Int, val height: Int, val density: Float, val top: Float, val bottom: Float)
    private val windows get() = listOf(
        Window("${trees[tree].first} phone", 1080, 2400, 2.625f, 260f, 420f),
        // the phone with the first visit's card up: the strip above, the foot and the card below
        Window("${trees[tree].first} phone, first visit", 1080, 2400, 2.625f, 270f, 900f),
        Window("${trees[tree].first} tablet", 2560, 1600, 2f, 180f, 200f),
        Window("${trees[tree].first} foldable open", 2208, 1840, 2.5f, 220f, 260f))

    private fun style(d: Float) = TreeNames.Style(bough = 13f * d, leaf = 11.5f * d, halo = 2.5f * d, gapX = 6f * d, gapY = 3f * d,
        edge = 12f * d, leastLeaf = 5f * d)

    /** The names at [w]'s first view turned by [yaw], or flown in to [into]'s branch. */
    /**
     * The chrome's boxes over [w]'s map, as the Tree screen measures them: flown in, the Back button
     * (a 48 dp target a gutter in from the top strip's foot and the left); always the round tools (two
     * targets and a gap) a gutter in from the right, on the foot.
     */
    private fun chrome(w: Window, flown: Boolean): List<androidx.compose.ui.geometry.Rect> {
        val d = w.density; val target = 48f * d; val gutter = 16f * d
        val tools = androidx.compose.ui.geometry.Rect(w.width - gutter - target, w.height - w.bottom - 2 * target - 8f * d - gutter,
            w.width - gutter, w.height - w.bottom)
        val back = androidx.compose.ui.geometry.Rect(gutter, w.top + gutter, gutter + target, w.top + gutter + target)
        return if (flown) listOf(back, tools) else listOf(tools)
    }

    private fun names(w: Window, yaw: Float, into: Int = -1, framed: MapInsets? = null): Pair<List<ScreenNode>, List<PlacedName>> {
        val scene = MapScene.tree(model)
        val camera = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
        val insets = MapInsets(top = w.top, bottom = w.bottom)
        HomeView.framer(scene)(w.width.toFloat(), w.height.toFloat(), framed ?: insets).applyTo(camera)
        camera.orbit(yaw, 0f)
        val clear = chrome(w, into > 0)
        if (into >= 0) {
            // as the renderer flies in (flyToBranch): the subtree fitted close, clear of the chrome's boxes
            val fit = checkNotNull(com.cramdown.sorta.map.TreeFit.flyIn(model, into, camera.yaw, camera.pitch, camera.fovY,
                w.width.toFloat(), w.height.toFloat(), MapClear.around(insets, clear, w.height.toFloat()), com.cramdown.sorta.map.MapFly.FILL))
            camera.target = fit.target; camera.distance = fit.distance
        }
        val viewProj = Mat4.multiply(camera.projection(w.width.toFloat() / w.height), camera.view())
        val screen = TreeScreen.project(model, viewProj, camera.focalPixels(w.height.toFloat()), w.width, w.height, null, FloatArray(4), w.density)
        val placed = TreeNames.layout(screen, sample, Metrics(0.6f), Metrics(0.56f), style(w.density), w.width.toFloat(), w.height.toFloat(), w.top, w.bottom,
            focus = into.coerceAtLeast(0), clear = MapClear.boxes(clear), cap = TreeNames.flownCap(w.width.toFloat(), w.height - w.top - w.bottom, w.density))
        return screen to placed
    }

    private val cardiology get() = sample.titles.indexOf("Cardiology")

    @Test fun noTwoNamesEverOverlap() = eachTree {
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

    // the owner's review of 19 Nov ("the text overlaps a lot"): every 30 degrees round, the whole tree and
    // flown in to each top folder, on a phone, a tablet and an open foldable, no name box (its halo
    // included) meets another, nor leaves the view or goes under the chrome; and few names lie over
    // another branch's leaves. iOS's suite treemodel3d sweeps the same.
    @Test fun noNameMeetsAnotherAtAnyAngleOnAnyDevice() = eachTree {
        val tops = (0 until sample.childCount[0]).map { sample.child(0, it) }.filter { sample.childCount[it] > 0 }
        assertTrue("top folders (${tops.size})", tops.size >= 4)
        var views = 0; var named = 0; var overLeaves = 0
        for (w in listOf(windows[0], windows[2], windows[3])) for (k in 0 until 12) for (into in listOf(-1) + tops) {
            val (screen, placed) = names(w, rad(30f * k), into)
            val what = "${w.name} at ${30 * k} degrees, ${if (into < 0) "whole" else sample.titles[into]}"
            views++; named += placed.size
            assertTrue("$what: names placed", placed.isNotEmpty())
            for (a in placed.indices) for (b in a + 1 until placed.size) {
                val p = placed[a].box; val q = placed[b].box
                assertFalse("$what: ${placed[a].label} meets ${placed[b].label}",
                    kotlin.math.abs(p.x - q.x) * 2 < p.width + q.width && kotlin.math.abs(p.y - q.y) * 2 < p.height + q.height)
            }
            val clear = MapClear.boxes(chrome(w, into > 0))
            val d = w.density
            for (p in placed) {
                val b = p.box
                // the box holds the words and the halo round them
                val metrics = if (p.textSize == 13f * d) Metrics(0.6f) else Metrics(0.56f)
                assertTrue("$what: ${p.label}'s box holds its halo", b.width >= metrics.width(p.label, p.textSize) + 2 * 2.5f * d - 1e-3f)
                assertTrue("$what: ${p.label} inside the view", b.x - b.width / 2 >= 0f && b.x + b.width / 2 <= w.width)
                assertTrue("$what: ${p.label} clear of the chrome", b.y - b.height / 2 >= w.top && b.y + b.height / 2 <= w.height - w.bottom)
                for (c in clear) assertFalse("$what: ${p.label} under a tool",
                    kotlin.math.abs(b.x - c.x) * 2 < b.width + c.width && kotlin.math.abs(b.y - c.y) * 2 < b.height + c.height)
                val owner = TreeNames.owner(sample, into.coerceAtLeast(0), p.index)
                if (screen.any { s -> !TreeNames.bough(sample, s.index) && !sample.within(owner, s.index) &&
                        kotlin.math.max(kotlin.math.abs(s.x - b.x) - b.width / 2, 0f).let { it * it } +
                        kotlin.math.max(kotlin.math.abs(s.y - b.y) - b.height / 2, 0f).let { it * it } < s.pixels * s.pixels / 4 }) overLeaves++
            }
        }
        println("${trees[tree].first}: $views views, $named names, $overLeaves over another branch's leaves")
        assertEquals(3 * 12 * (1 + tops.size), views)
        // at most 8 in 100 (the lead's review of 19 Nov lowered it from 10: flown in, names stand beside their branches)
        assertTrue("$overLeaves of $named names over another branch's leaves", overLeaves * 100 <= 8 * named)
    }

    // a part flown in to before the tree shrank, past the new tree's end, is the whole tree (iOS's
    // TreeHostRegrowTests: the Fractal's names stopped the app so when Let go shrank its tree)
    @Test fun aFocusPastTheTreesEndIsTheWholeTree() = eachTree {
        val w = windows[0]
        val (screen, whole) = names(w, 0f)
        val args = { focus: Int -> TreeNames.layout(screen, sample, Metrics(0.6f), Metrics(0.56f), style(w.density), w.width.toFloat(),
            w.height.toFloat(), w.top, w.bottom, focus = focus, clear = MapClear.boxes(chrome(w, false))) }
        for (past in listOf(sample.size, sample.size + 40)) {
            val placed = args(past)
            assertEquals("focus $past: the whole tree's names", whole.map { it.index to it.label }, placed.map { it.index to it.label })
        }
    }

    @Test fun theBigBranchesAreNamedAtTheFirstView() = eachTree {
        for (w in windows) {
            val (_, placed) = names(w, 0f)
            val labels = placed.map { it.label }.toSet()
            for (want in listOf("All ideas", "Examples", "Cardiology")) assertTrue("${w.name}: $want named, got $labels", want in labels)
        }
    }

    @Test fun theFirstVisitCardLeavesTheBigBranchesTheirNames() = eachTree {
        // as Map3DView holds it while the first visit's card is up: the chrome's foot, and the card's
        // cover measured from the view's foot (IdeasTree: the foot, a gap and the card, so the foot
        // under the card is in it); the first view framed half a touch target above the cover, the
        // names kept above TreeNames.floor. Adding the foot to the cover again left the big
        // branches no spot on the emulators (run 44342720370)
        for ((base, cover) in listOf(Window("phone", 1080, 2400, 2.625f, 270f, 400f) to 1300f,
                Window("tablet", 2560, 1600, 2f, 180f, 260f) to 820f)) {
            val name = base.name
            val chrome = MapInsets(top = base.top, bottom = base.bottom)
            val floor = TreeNames.floor(chrome, cover)
            assertEquals("$name: the cover holds the chrome's foot, so the two are not added", cover, floor, 0f)
            assertEquals(chrome.bottom, TreeNames.floor(chrome, 0f), 0f)
            val w = Window("${trees[tree].first} $name, first visit as the app frames it", base.width, base.height, base.density, chrome.top, floor)
            val (_, placed) = names(w, 0f, framed = chrome.coveringFoot(cover + 24 * base.density))
            val labels = placed.map { it.label }.toSet()
            for (want in listOf("All ideas", "Examples", "Cardiology")) assertTrue("${w.name}: $want named, got $labels", want in labels)
            assertFalse("${w.name}: two names overlap", TreeNames.overlaps(placed))
            for (p in placed) assertTrue("${w.name}: ${p.label} above the card", p.box.y + p.box.height / 2 <= w.height - cover)
        }
    }

    // the lead's review of 18 Nov: from three quarters round, two limbs stood one behind the other
    @Test fun theFirstViewShowsTheCrownSpread() = eachTree {
        val points = com.cramdown.sorta.map.TreeFit.points(model.layout, 0)
        fun across(yaw: Float): Float {
            val xs = points.map { it.x * kotlin.math.cos(yaw) - it.z * kotlin.math.sin(yaw) }
            return xs.max() - xs.min()
        }
        val yaw = com.cramdown.sorta.map.TreeFit.widestYaw(points, TREE_YAW)
        // the sample's widest is 45 degrees from three quarters round; iOS's TreeModel3DTests holds the same
        // 5 degrees right of front for the grown tree (19 Nov); iOS's suite treemodel3d holds the same
        assertEquals(5.0, Math.toDegrees(yaw.toDouble()), 1e-3)
        for (k in 0 until 72) assertTrue(across(yaw) >= across(k * 5f * Math.PI.toFloat() / 180f) * (1 - 1e-4f))
        for (w in windows) {
            val (screen, _) = names(w, 0f)
            val xs = screen.map { it.x }; val ys = screen.map { it.y }
            val wide = xs.max() - xs.min(); val tall = ys.max() - ys.min()
            assertTrue("${w.name}: the tree is $wide wide and $tall tall", wide >= 0.85f * tall)
        }
    }

    // the lead's review of 18 Nov: flown in to Cardiology, about 45 names over a wall of leaves, some of Examples'
    @Test fun flownInOnlyThePartItsChildrenAndItsIdeasAreNamedUpToTheCap() = eachTree {
        for (w in windows) for (yaw in listOf(0f, rad(40f))) {
            val (_, placed) = names(w, yaw, cardiology)
            val cap = TreeNames.flownCap(w.width.toFloat(), w.height - w.top - w.bottom, w.density)
            assertTrue("${w.name}: ${placed.size} names, at most $cap", placed.size <= cap)
            for (p in placed) {
                assertTrue("${w.name}: ${p.label} is Cardiology's", sample.within(cardiology, p.index))
                assertTrue("${w.name}: ${p.label} is Cardiology, a part straight off it, or an idea",
                    p.index == cardiology || sample.parent[p.index] == cardiology || !TreeNames.bough(sample, p.index))
            }
        }
        // a phone's room holds about 12 to 14
        assertTrue(TreeNames.flownCap(1080f, 2400f - 260f - 420f, 2.625f) in 12..14)
        assertTrue(TreeNames.flownCap(2560f, 1600f - 380f, 2f) > 14)
    }

    @Test fun noNameOrLeaderGoesUnderTheChrome() = eachTree {
        for (w in windows) for ((yaw, into) in listOf(0f to -1, rad(120f) to -1, 0f to cardiology, rad(40f) to cardiology)) {
            val (_, placed) = names(w, yaw, into)
            for (r in chrome(w, into > 0)) for (p in placed) {
                val b = p.box
                val apart = b.x + b.width / 2 <= r.left || b.x - b.width / 2 >= r.right || b.y + b.height / 2 <= r.top || b.y - b.height / 2 >= r.bottom
                assertTrue("${w.name} $into: ${p.label} under the chrome at $r", apart)
                if (!p.leadX.isNaN()) assertFalse("${w.name}: ${p.label}'s leader under the chrome",
                    LabelRoom.crosses(LabelBox(r.center.x, r.center.y, r.width, r.height), LabelLink(p.box.x, p.box.y, p.leadX, p.leadY, 1f), 0f))
            }
        }
    }

    @Test fun aFlyInIsFramedClearOfTheChrome() = eachTree {
        for (w in windows) {
            val camera = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
            HomeView.framer(MapScene.tree(model))(w.width.toFloat(), w.height.toFloat(), MapInsets(top = w.top, bottom = w.bottom)).applyTo(camera)
            val room = MapClear.around(MapInsets(top = w.top, bottom = w.bottom), chrome(w, true), w.height.toFloat())
            val fit = checkNotNull(com.cramdown.sorta.map.TreeFit.flyIn(model, cardiology, camera.yaw, camera.pitch, camera.fovY,
                w.width.toFloat(), w.height.toFloat(), room, com.cramdown.sorta.map.MapFly.FILL))
            val lens = com.cramdown.sorta.map.OrbitCamera(yaw = camera.yaw, pitch = camera.pitch, fovY = camera.fovY)
            val b = lens.forward() * -1f
            val box = checkNotNull(com.cramdown.sorta.map.TreeFit.box(com.cramdown.sorta.map.TreeFit.points(model.layout, cardiology), 0f,
                fit.target + b * fit.distance, lens.right(), lens.up(), b, lens.focalPixels(w.height.toFloat())))
            val top = w.height / 2f - box[3]; val bottom = w.height / 2f - box[2]
            assertTrue("${w.name}: Cardiology's top $top under the Back button's foot ${room.top}", top >= room.top - 0.5f)
            assertTrue("${w.name}: its foot $bottom over the tools' top ${w.height - room.bottom}", bottom <= w.height - room.bottom + 0.5f)
        }
    }

    @Test fun aLeafIsHeldBetweenItsFloorAndCeilingOnScreen() = eachTree {
        for (w in windows) {
            val (screen, _) = names(w, 0f, cardiology)
            for (n in screen) if (model.layout.leaf[n.index] > 0)
                assertTrue("${w.name}: leaf ${n.index} is ${n.pixels} px", n.pixels in TreeWeight.LEAF_DP * w.density - 1e-3f..TreeWeight.LEAF_MOST_DP * w.density + 1e-3f)
        }
    }

    @Test fun aFlyInComesNearer() = eachTree {
        for (w in windows) {
            val whole = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
            HomeView.framer(MapScene.tree(model))(w.width.toFloat(), w.height.toFloat(), MapInsets(top = w.top, bottom = w.bottom)).applyTo(whole)
            for (part in listOf(cardiology, sample.titles.indexOf("Examples"), sample.titles.indexOf("Heart failure"))) {
                val fit = checkNotNull(com.cramdown.sorta.map.TreeFit.flyIn(model, part, whole.yaw, whole.pitch, whole.fovY,
                    w.width.toFloat(), w.height.toFloat(), MapInsets(top = w.top, bottom = w.bottom), com.cramdown.sorta.map.MapFly.FILL))
                assertTrue("${w.name}: flying in to ${sample.titles[part]} comes nearer (${fit.distance} < ${whole.distance})", fit.distance < whole.distance)
            }
        }
    }

    @Test fun flownInToCardiologyItsOwnPartsAreNamed() = eachTree {
        for (w in windows) {
            val (_, placed) = names(w, 0f, cardiology)
            val inside = placed.count { sample.within(cardiology, it.index) }
            assertTrue("${w.name}: Cardiology's own parts named ($inside)", inside >= 8)
        }
    }

    @Test fun branchesComeFirstThenWeight() = eachTree {
        val (screen, _) = names(windows[0], 0f)
        val order = TreeNames.order(screen, sample)
        val firstLeaf = order.indexOfFirst { !TreeNames.bough(sample, it.index) }
        assertTrue(firstLeaf > 0)
        for (k in firstLeaf until order.size) assertFalse(TreeNames.bough(sample, order[k].index))
        // the branches level by level, the shallower first (the lead's review of 18 Nov), each level by weight
        for (k in 1 until firstLeaf) {
            val a = order[k - 1].index; val b = order[k].index
            assertTrue(sample.depth[a] <= sample.depth[b])
            if (sample.depth[a] == sample.depth[b]) assertTrue(sample.weight[a] >= sample.weight[b])
        }
        assertEquals(0, order[0].index)
    }

    // the lead's review of 18 Nov: at the whole tree the names leave the trunk clear, a deeper part is
    // named only at its tip, and a leader never runs under another name
    @Test fun theWholeTreeKeepsItsTrunkClearAndNamesDeeperPartsAtTheirTips() = eachTree {
        for (w in windows) for (yaw in listOf(0f, rad(40f), rad(120f))) {
            val (screen, placed) = names(w, yaw)
            val trunk = screen.first { it.index == 0 }
            val a = com.cramdown.sorta.map.TreeShape3D.ANCHOR.toFloat()
            val stem = com.cramdown.sorta.map.LabelLink((trunk.x - a * trunk.tipX) / (1 - a), (trunk.y - a * trunk.tipY) / (1 - a), trunk.tipX, trunk.tipY, trunk.pixels / 2)
            val first = placed.filter { sample.depth[it.index] <= 1 }.map { it.index }.toSet()
            assertTrue("${w.name} $yaw: the trunk and its branches named", 0 in first && first.size >= 3)
            for (p in placed) {
                if (p.index != 0) assertFalse("${w.name} $yaw: ${p.label} covers the trunk", com.cramdown.sorta.map.LabelRoom.crosses(p.box, stem, 0f))
                if (sample.depth[p.index] > 1) {
                    assertTrue("${w.name} $yaw: ${p.label} at its tip (spot ${p.spot})", p.spot <= TreeNames.LAST_TIP)
                    // out past the crown, a leader runs back to the tip
                    val s = screen.first { it.index == p.index }
                    if (p.spot == TreeNames.OUTSIDE || p.spot >= TreeNames.PAST_ABOVE)
                        assertTrue("${w.name} $yaw: ${p.label}'s leader runs to its tip", p.leadX == s.tipX && p.leadY == s.tipY || p.leadX.isNaN() && p.spot == TreeNames.OUTSIDE)
                }
                if (!p.leadX.isNaN()) {
                    val near = if (p.leadX < p.box.x) p.box.x - p.box.width / 2 else p.box.x + p.box.width / 2
                    val lead = com.cramdown.sorta.map.LabelLink(near, p.box.y, p.leadX, p.leadY, 1f)
                    for (o in placed) if (o !== p) assertFalse("${w.name} $yaw: ${p.label}'s leader runs under ${o.label}", com.cramdown.sorta.map.LabelRoom.crosses(o.box, lead, 0f))
                }
            }
        }
    }

    // the lead's review of 18 Nov: the first view fills the room the chrome and the card leave, with a small margin
    @Test fun theFirstViewFillsTheRoom() = eachTree {
        for ((base, cover) in listOf(Window("phone", 1080, 2400, 2.625f, 270f, 400f) to 1300f, Window("phone", 1080, 2400, 2.625f, 270f, 400f) to 0f,
                Window("tablet", 2560, 1600, 2f, 180f, 260f) to 0f, Window("foldable", 2208, 1840, 2.5f, 220f, 260f) to 0f)) {
            val chrome = MapInsets(top = base.top, bottom = base.bottom)
            val room = if (cover > 0f) chrome.coveringFoot(cover + 24 * base.density) else chrome
            val camera = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
            HomeView.framer(MapScene.tree(model))(base.width.toFloat(), base.height.toFloat(), room).applyTo(camera)
            val l = model.layout
            // every branch's ends and middle on its curve, and every body: a leaf sits past its twig's tip
            val points = com.cramdown.sorta.map.TreeFit.points(l, 0)
            val f = camera.focalPixels(base.height.toFloat())
            val box = checkNotNull(com.cramdown.sorta.map.TreeFit.box(points, 0f, camera.eye(), camera.right(), camera.up(), camera.forward() * -1f, f))
            // from the view's middle, y up, to the view's pixels
            val left = base.width / 2 + box[0]; val right = base.width / 2 + box[1]
            val top = base.height / 2 - box[3]; val bottom = base.height / 2 - box[2]
            val what = "${trees[tree].first} ${base.name}${if (cover > 0f) " with the card" else ""}"
            assertTrue("$what: inside the room ($left, $top, $right, $bottom)", left >= room.left && right <= base.width - room.right &&
                top >= room.top && bottom <= base.height - room.bottom)
            val wide = (right - left) / (base.width - room.left - room.right); val tall = (bottom - top) / (base.height - room.top - room.bottom)
            assertTrue("$what: fills $wide across and $tall down", maxOf(wide, tall) >= 0.92f)
        }
    }

    // the lead's review of 18 Nov: the finest twig at least 1.5 dp across and a leaf at least 6 dp long, however far
    @Test fun theFinestTwigAndLeafKeepTheirSize() {
        for (density in listOf(1f, 2f, 2.625f, 3.5f)) for (focal in listOf(400f, 1200f, 3000f)) for (depth in listOf(0.5f, 3f, 40f)) {
            val twig = TreeWeight.twigFloor(density, focal) * depth * focal / depth * 2
            val leaf = TreeWeight.leafFloor(density, focal) * depth * focal / depth * 2
            assertEquals(TreeWeight.TWIG_DP * density, twig, 1e-3f * twig)
            assertEquals(TreeWeight.LEAF_DP * density, leaf, 1e-3f * leaf)
        }
        // and at the phone's first view the trunk and its branches are solid, not hairlines
        eachTree {
            val w = windows[0]
            val camera = firstCamera(MapTheme.TREE, com.cramdown.sorta.map.Vec3.ZERO)
            HomeView.framer(MapScene.tree(model))(w.width.toFloat(), w.height.toFloat(), MapInsets(top = w.top, bottom = w.bottom)).applyTo(camera)
            val f = camera.focalPixels(w.height.toFloat()); val eye = camera.eye(); val l = model.layout
            fun across(i: Int): Float {
                val dx = l.base[3 * i] - eye.x; val dy = l.base[3 * i + 1] - eye.y; val dz = l.base[3 * i + 2] - eye.z
                return (2 * l.r0[i] * f / kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)).toFloat() / w.density
            }
            assertTrue("${trees[tree].first}: the trunk is ${across(0)} dp across", across(0) >= 8f)
            for (k in 0 until sample.childCount[0]) {
                val c = sample.child(0, k)
                if (sample.weight[c] >= 8) assertTrue("${trees[tree].first}: ${sample.titles[c]} is ${across(c)} dp across", across(c) >= 3f)
            }
        }
    }

    @Test fun aNameThatCannotFitIsDropped() {
        // every body at one point: only the first name gets a clear box at each of its spots
        val pile = (0 until sample.size).map { ScreenNode(it, 500f, 900f, 10f) }
        val placed = TreeNames.layout(pile, sample, Metrics(0.6f), Metrics(0.56f), style(2.625f), 1080f, 2400f, 0f, 0f)
        assertTrue(placed.size in 1..5)
        assertFalse(TreeNames.overlaps(placed))
        assertEquals(0, placed[0].index)
    }

    @Test fun theCostGuardsHold() {
        // every body far apart on a huge view, each with room for its name: only MOST are drawn, the first in the order
        val grid = (0 until sample.size).map { ScreenNode(it, 400f + (it % 12) * 1600f, 400f + (it / 12) * 400f, 40f) }
        val placed = TreeNames.layout(grid, sample, Metrics(0.6f), Metrics(0.56f), style(2.625f), 20_000f, 20_000f, 0f, 0f)
        assertTrue("more bodies than names allowed (${sample.size})", sample.size > TreeNames.MOST)
        assertEquals(TreeNames.MOST, placed.size)
        assertFalse(TreeNames.overlaps(placed))
        assertEquals(TreeNames.order(grid, sample).take(TreeNames.MOST).map { it.index }, placed.map { it.index })
        assertTrue(TreeNames.BUDGET >= TreeNames.MOST)
    }

    private fun rad(d: Float) = (d * Math.PI / 180).toFloat()
}
