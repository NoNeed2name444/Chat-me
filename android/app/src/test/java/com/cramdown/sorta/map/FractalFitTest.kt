package com.cramdown.sorta.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// S12, the Fractal's framing (FractalFit; the lead's review of run 44408042504): the first view and a
// fly-in to Cardiology show every framed dot inside the room the chrome leaves, less the margin, and
// reach that margin on one side or the other's pair, so what is framed is as large as fits; and it sits
// in the middle of the room, on a phone, a phone's first visit, a tablet and a foldable's pane. Framed
// again to what the frame draws there (FractalFit.drawn), every drawn dot and its glow stays in that room,
// on both trees of tools/fractal_fixture3d.json.
class FractalFitTest {
    private val sample by lazy { FractalFixture.tree(FractalFixture.read()) }
    private val layout by lazy { FractalLayout3D(sample) }
    private val fov = (50.0 * PI / 180).toFloat()
    private val pitch = (22.0 * PI / 180).toFloat()

    private class Room(val name: String, val width: Float, val height: Float, val insets: MapInsets)
    private val rooms = listOf(
        Room("phone", 1080f, 2400f, MapInsets(top = 260f, bottom = 420f)),
        Room("phone, first visit", 1080f, 2400f, MapInsets(top = 270f, bottom = 900f)),
        Room("tablet", 2560f, 1600f, MapInsets(top = 180f, bottom = 200f)),
        Room("foldable pane, first visit", 1325f, 1777f, MapInsets(top = 280f, bottom = 740f)))

    /** The box on screen, in pixels, of [nodes]' marks seen from [pose]: left, top, right, bottom. */
    private fun box(pose: MapPose, nodes: IntArray, room: Room): FloatArray {
        val cam = OrbitCamera(target = pose.target, distance = pose.distance, yaw = pose.yaw, pitch = pose.pitch, fovY = fov)
        val vp = Mat4.multiply(cam.projection(room.width / room.height), cam.view())
        val f = cam.focalPixels(room.height)
        val clip = FloatArray(4)
        val out = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in nodes) {
            Mat4.project(vp, layout.pos[3 * i].toFloat(), layout.pos[3 * i + 1].toFloat(), layout.pos[3 * i + 2].toFloat(), clip)
            val w = clip[3]
            assertTrue("${sample.titles[i]} in front of the eye", w > 0f)
            val x = (clip[0] / w * 0.5f + 0.5f) * room.width
            val y = (1f - (clip[1] / w * 0.5f + 0.5f)) * room.height
            val r = (FractalFit.mark(layout, i) * f / w).toFloat()
            out[0] = min(out[0], x - r); out[1] = min(out[1], y - r); out[2] = max(out[2], x + r); out[3] = max(out[3], y + r)
        }
        return out
    }

    private fun check(what: String, hub: Int, levels: Int, yaw: Float) {
        for (room in rooms) {
            val nodes = FractalFit.nodes(sample, hub, levels)
            val margin = FractalFit.margin(room.width, room.height, room.insets)
            val pose = FractalFit.fit(layout, nodes, yaw, pitch, fov, room.width, room.height, room.insets, margin)
            val b = box(pose, nodes, room)
            val left = room.insets.left + margin; val right = room.width - room.insets.right - margin
            val top = room.insets.top + margin; val bottom = room.height - room.insets.bottom - margin
            val label = "$what, ${room.name}: box ${b.toList()} in [$left, $top, $right, $bottom]"
            val e = 1.5f
            assertTrue("$label: inside", b[0] >= left - e && b[2] <= right + e && b[1] >= top - e && b[3] <= bottom + e)
            // as large as fits: it reaches the room's sides, or its top and bottom
            val wide = (b[2] - b[0]) / (right - left); val tall = (b[3] - b[1]) / (bottom - top)
            assertTrue("$label: fills ${"%.2f".format(wide)} wide, ${"%.2f".format(tall)} tall", max(wide, tall) >= 0.98f)
            // in the room's middle
            assertEquals("$label: centred across", (left + right) / 2, (b[0] + b[2]) / 2, 2f)
            assertEquals("$label: centred down", (top + bottom) / 2, (b[1] + b[3]) / 2, 2f)
            // the margin is small: about 14 dp on a phone
            assertTrue("$label: margin $margin", margin in 20f..60f)
        }
    }

    @Test fun theWholeViewFillsTheRoomTheChromeLeaves() = check("whole", 0, FractalFit.WHOLE_LEVELS, 0f)

    @Test fun theWholeViewTurnedStillFits() = check("whole at 40 degrees", 0, FractalFit.WHOLE_LEVELS, (40 * PI / 180).toFloat())

    @Test fun aFlyInFramesItsClusterInTheMiddle() {
        val cardiology = sample.titles.indexOf("Cardiology")
        check("Cardiology", cardiology, FractalFit.IN_LEVELS, 0f)
        check("Cardiology at 120 degrees", cardiology, FractalFit.IN_LEVELS, (120 * PI / 180).toFloat())
    }

    /** The trees of tools/fractal_fixture3d.json (the sample and the made-up tree that reaches every rule), as FractalLayout3DTest reads them. */
    private val fixture3d: List<Pair<String, FractalTree>> by lazy {
        var root: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !java.io.File(root, "tools/fractal_fixture3d.json").exists()) root = root.parentFile
        val list = org.json.JSONObject(java.io.File(root ?: java.io.File("/"), "tools/fractal_fixture3d.json").readText()).getJSONArray("trees")
        (0 until list.length()).map { k ->
            val t = list.getJSONObject(k)
            val p = t.getJSONArray("parent")
            val n = p.length()
            val parent = IntArray(n) { p.getInt(it) }
            val kids = IntArray(n); for (i in 1 until n) kids[parent[i]]++
            t.getString("name") to FractalTree(parent, ByteArray(n) { if (it == 0) FractalTree.HUB else if (kids[it] > 0) FractalTree.FOLDER else FractalTree.IDEA },
                Array(n) { "" }, Array(n) { "Part $it" }, IntArray(n) { -1 })
        }
    }

    /**
     * After [pose], every dot the frame draws there, with its glow as drawn ([FractalSight]'s own reach), is
     * inside the room [insets] leave less the margin: what is framed is what is drawn (the review of S12's
     * stills: a third level's dots reached the phone's edge flown in, and the tablet's lines its side). And
     * their box is in the room's middle, within 3% of it or 4 dp (the review: a fly-in sat high over a band).
     */
    private fun drawnInside(label: String, model: FractalModel, sight: FractalSight, pose: MapPose, room: Room, density: Float) {
        val cam = OrbitCamera(target = pose.target, distance = pose.distance, yaw = pose.yaw, pitch = pose.pitch, fovY = fov)
        val f = cam.focalPixels(room.height)
        sight.step(FractalFit.viewProj(model, cam, room.width / room.height), f, room.width, room.height, density)
        assertTrue("$label: dots drawn (${sight.count})", sight.count > 3)
        val margin = FractalFit.margin(room.width, room.height, room.insets)
        val left = room.insets.left + margin; val right = room.width - room.insets.right - margin
        val top = room.insets.top + margin; val bottom = room.height - room.insets.bottom - margin
        val e = 1.5f
        var lowX = Float.POSITIVE_INFINITY; var lowY = Float.POSITIVE_INFINITY
        var highX = Float.NEGATIVE_INFINITY; var highY = Float.NEGATIVE_INFINITY
        for (k in 0 until sight.count) {
            val g = sight.radius[k] * (1f + FractalSight.GLOW_REACH * sight.hub[k])
            val x = sight.x[k]; val y = sight.y[k]
            lowX = minOf(lowX, x - g); highX = maxOf(highX, x + g); lowY = minOf(lowY, y - g); highY = maxOf(highY, y + g)
            assertTrue("$label: node ${sight.node[k]} at ($x, $y) with its glow $g outside [$left, $top, $right, $bottom]",
                x - g >= left - e && x + g <= right + e && y - g >= top - e && y + g <= bottom + e)
        }
        val slackX = maxOf(4f * density, (right - left) * 0.03f); val slackY = maxOf(4f * density, (bottom - top) * 0.03f)
        assertEquals("$label: the drawn box [$lowX, $lowY, $highX, $highY] across the room's middle", (left + right) / 2, (lowX + highX) / 2, slackX)
        assertEquals("$label: the drawn box [$lowX, $lowY, $highX, $highY] down the room's middle", (top + bottom) / 2, (lowY + highY) / 2, slackY)
    }

    @Test fun aFlyInFramesEveryDotItDrawsAndItsGlow() {
        for ((name, tree) in fixture3d) {
            val model = FractalModel(tree, FractalLayout3D(tree), IntArray(0), FractalRules.BUDGET)
            val sight = FractalSight(model)
            val hubs = (1 until tree.count).filter { tree.parent[it] == 0 && tree.childCount[it] > 0 }
            assertTrue("$name: hubs to fly in to", hubs.isNotEmpty())
            for (room in rooms) for (deg in listOf(0f, 120f)) for (hub in hubs) {
                val density = if (room.width > 2000f) 2f else 2.625f
                sight.focus = hub
                val pose = FractalFit.flyIn(model, hub, (deg * PI / 180).toFloat(), pitch, fov, room.width, room.height, room.insets, sight, density)
                drawnInside("$name, ${room.name}, flown in to $hub at $deg degrees", model, sight, pose, room, density)
            }
        }
    }

    @Test fun theWholeViewFramesEveryDotItDrawsAndItsGlow() {
        for ((name, tree) in fixture3d) {
            val model = FractalModel(tree, FractalLayout3D(tree), IntArray(0), FractalRules.BUDGET)
            val sight = FractalSight(model)
            for (room in rooms) {
                val density = if (room.width > 2000f) 2f else 2.625f
                sight.focus = 0
                val pose = FractalFit.whole(model, 0f, pitch, fov, room.width, room.height, room.insets, sight, density)
                drawnInside("$name, ${room.name}, whole", model, sight, pose, room, density)
            }
        }
    }

    @Test fun whatIsFramedIsTheClusterLevelByLevelWithinTheBudget() {
        val cardiology = sample.titles.indexOf("Cardiology")
        val nodes = FractalFit.nodes(sample, cardiology, 2)
        assertEquals(cardiology, nodes[0])
        for (i in nodes) assertTrue(sample.holds(cardiology, i) && sample.depth[i] - sample.depth[cardiology] <= 2)
        val all = (0 until sample.count).count { sample.holds(cardiology, it) && sample.depth[it] - sample.depth[cardiology] <= 2 }
        assertEquals("all of them", all, nodes.size)
        assertEquals("at most the budget", 7, FractalFit.nodes(sample, 0, 3, most = 7).size)
        for (k in 1 until nodes.size) assertTrue("level by level", sample.depth[nodes[k]] >= sample.depth[nodes[k - 1]])
    }
}
