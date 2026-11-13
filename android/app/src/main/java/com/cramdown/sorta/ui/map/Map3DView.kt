package com.cramdown.sorta.ui.map

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Choreographer
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cramdown.sorta.map.DisplayModeInfo
import com.cramdown.sorta.map.LabelBackdrop
import com.cramdown.sorta.map.LabelEstimate
import com.cramdown.sorta.map.LabelGlow
import com.cramdown.sorta.map.LabelLook
import com.cramdown.sorta.map.LabelRgb
import com.cramdown.sorta.map.LabelSampler
import com.cramdown.sorta.map.LabelSettings
import com.cramdown.sorta.map.LabelShine
import com.cramdown.sorta.map.MapNameShow
import com.cramdown.sorta.map.NameBody
import com.cramdown.sorta.map.NamePill
import com.cramdown.sorta.map.NameText
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.map.LinkMemory
import com.cramdown.sorta.map.LinkRecall
import com.cramdown.sorta.map.CameraMove
import com.cramdown.sorta.map.MapChoiceCue
import com.cramdown.sorta.map.MapCameraEase
import com.cramdown.sorta.map.MapCardCover
import com.cramdown.sorta.map.MapTouch
import com.cramdown.sorta.map.MapTouchKind
import com.cramdown.sorta.map.MapTouchRules
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.NeuronLookPlan
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.RefreshRate
import com.cramdown.sorta.map.SpacePalette
import com.cramdown.sorta.map.SpaceStyle
import com.cramdown.sorta.map.NeuronScene
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.map.MapFly
import com.cramdown.sorta.map.CameraFlight
import com.cramdown.sorta.map.grouped
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WardType
import com.cramdown.sorta.ui.ward.textStyle
import com.cramdown.sorta.ui.ward.wardDp
import com.cramdown.sorta.ui.wardColor
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlin.math.cbrt
import kotlin.math.max

/** A map ready to draw: its octree, how big a node is, and the notes behind the nodes. */
/** [viewRadius] holds 95% of the nodes: the home view frames those, not a few far outliers. */
/** [theme] says how its notes are drawn; in [MapTheme.SPACE] each node's group is its SpaceStyle code. */
/** [links] are its links as pairs of node indices, and [linkHalf] how far a drawn link reaches each side of its line (world units): names keep off them. */
class MapScene(val tree: Octree, val nodeRadius: Float, val titles: List<String>, val ids: List<String>, val viewRadius: Float = tree.radius[0], val theme: MapTheme = MapTheme.PLAIN,
               /** [MapTheme.NEURONS]: the cells and links NeuronGl draws; the tree then only holds their places, by body. */
               val neurons: NeuronScene? = null,
               val links: IntArray = IntArray(0), val linkHalf: Float = 0f,
               /**
                * [MapTheme.SPACE]: its main look is the Universe (S05g2; iOS's isUniverse, Graph3DView.swift:879-881),
                * so a held body springs home with no link pulling; false under one single style, where its links
                * pull its neighbours.
                */
               val universe: Boolean = true,
               /**
                * Space's Universe (S05h): the plan its bodies come from, one node a body (folders, the home and
                * notes), the tree holding their homes; null under one single style and in the other themes.
                */
               val universePlan: UniversePlan? = null,
               /** Each Universe galaxy's tone, by its body index, for its names' rims (S05h). */
               val galaxyTones: Map<Int, LabelRgb> = emptyMap()) {
    /** This scene's number, new for every scene built in the process (MapDrawn.build, S05e): a test can tell a redraw from a rebuild. */
    val build: Int = BUILDS.incrementAndGet()

    /** The body [id] stands for, ignoring case (an idea's id may come upper case); null for none or no such body (S05f). */
    fun indexOf(id: String?): Int? = id?.let { wanted -> ids.indexOfFirst { it.equals(wanted, ignoreCase = true) } }?.takeIf { it >= 0 }

    companion object {
        private val BUILDS = AtomicInteger()

        /**
         * The Neurons map: node i is the plan's body i at its home (labels, taps and the home view
         * read the tree; NeuronGl draws the bodies where they drift). [ids] and [titles] by body.
         */
        fun neurons(scene: NeuronScene): MapScene {
            val bodies = scene.plan.bodies
            val positions = FloatArray(bodies.size * 3)
            for (i in bodies.indices) {
                val p = scene.plan.position(i, 0.0)
                positions[3 * i] = p.x; positions[3 * i + 1] = p.y; positions[3 * i + 2] = p.z
            }
            val edges = IntArray(scene.plan.links.size * 2)
            for ((k, link) in scene.plan.links.withIndex()) { edges[2 * k] = link.a; edges[2 * k + 1] = link.b }
            val graph = MapGraph(positions, ByteArray(bodies.size), edges, bodies.map { it.label }, bodies.map { it.id })
            val tree = Octree.build(graph, 4096)
            val sizes = bodies.map { it.sphere }.sorted()
            val radius = (sizes.getOrNull(sizes.size / 2) ?: 0.35f).coerceIn(0.01f, 2f)
            // the bridges' half width: the wider of a tract's and a fibre's (NeuronGl.drawBridges)
            return MapScene(tree, radius, graph.titles, graph.ids, viewRadius(tree), MapTheme.NEURONS, scene,
                edges, max(NeuronLookPlan.FAR_BRIDGE, NeuronLookPlan.NEAR_BRIDGE))
        }

        /**
         * Space's Universe (S05h; iOS's GraphUniverseScene): node i is the plan's body i at its home, its
         * group the body's style (a galaxy a black hole, a star and the home suns, a gas giant, a rocky
         * planet and a moon rocky, a pulsar, a comet and a resting comet comets). A moon's link to its own
         * planet (kind 3) is left out, as iOS never draws it (GraphMotion.swift:1827). [ids] and
         * [titles] by body, its labels the names shown.
         */
        fun universe(plan: UniversePlan, tones: Map<Int, LabelRgb> = emptyMap(), leafCapacity: Int = 4096): MapScene {
            val bodies = plan.bodies
            val positions = FloatArray(bodies.size * 3)
            for ((i, body) in bodies.withIndex()) {
                positions[3 * i] = body.home.x; positions[3 * i + 1] = body.home.y; positions[3 * i + 2] = body.home.z
            }
            val codes = ByteArray(bodies.size) { universeStyle(bodies[it].role).code.toByte() }
            val drawn = plan.links.filter { UniverseBeams.drawn(it.kind) }
            val edges = IntArray(drawn.size * 2)
            for ((k, link) in drawn.withIndex()) { edges[2 * k] = link.a; edges[2 * k + 1] = link.b }
            val graph = MapGraph(positions, codes, edges, bodies.map { it.label }, bodies.map { it.id })
            val tree = Octree.build(graph, leafCapacity, palette = SpacePalette.rgb)
            val sizes = bodies.map { it.sphere }.sorted()
            val radius = (sizes.getOrNull(sizes.size / 2) ?: 0.35f).coerceIn(0.01f, 2f)
            // the first view holds the plan's envelope, every place a body can reach (GraphUniverse.swift:1475-1480)
            val centre = Vec3(tree.cx[Octree.ROOT], tree.cy[Octree.ROOT], tree.cz[Octree.ROOT])
            val reach = plan.envelope.maxOfOrNull { (it - centre).length() } ?: 0f
            return MapScene(tree, radius, graph.titles, graph.ids, if (reach > 1e-3f) reach else viewRadius(tree), MapTheme.SPACE,
                links = edges, linkHalf = UniverseBeams.HALF_WIDTH, universe = true, universePlan = plan, galaxyTones = tones)
        }

        /** A Universe body's Space style (S05h): what it is drawn as. */
        fun universeStyle(role: UniverseRole): SpaceStyle = UniverseBeams.style(role)

        fun viewRadius(tree: Octree): Float {
            val n = tree.nodeCount
            if (n < 50) return tree.radius[Octree.ROOT]
            val step = max(1, n / 20_000)
            val d = FloatArray((n + step - 1) / step) { k ->
                val i = k * step
                val dx = tree.positions[3 * i] - tree.cx[0]; val dy = tree.positions[3 * i + 1] - tree.cy[0]; val dz = tree.positions[3 * i + 2] - tree.cz[0]
                kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            }
            d.sort()
            return max(d[(d.size * 0.95f).toInt().coerceAtMost(d.size - 1)], 1e-3f)
        }

        /**
         * [styles], in Space, is each node's SpaceStyle code (the Look menu's choice); by default its links
         * decide. [universe]: Space's main look is the Universe ([MapScene.universe]).
         */
        fun of(graph: MapGraph, leafCapacity: Int = 4096, theme: MapTheme = MapTheme.PLAIN, styles: ByteArray? = null,
               universe: Boolean = true): MapScene {
            // Space: a note's style follows its links (SpaceStyle.forLinks) unless one is given
            val tree = if (theme == MapTheme.SPACE) {
                val codes = styles?.takeIf { it.size == graph.nodeCount } ?: SpaceStyle.assign(graph.nodeCount, graph.edges)
                val styled = MapGraph(graph.positions, codes, graph.edges, graph.titles, graph.ids)
                Octree.build(styled, leafCapacity, palette = SpacePalette.rgb)
            } else Octree.build(graph, leafCapacity)
            // a node is about a third of the typical spacing between neighbours in its chunk
            var spacing = 0.0; var weight = 0
            for (leaf in 0 until tree.leafCount) {
                val cell = tree.leafCell[leaf]
                if (tree.count[cell] < 2) continue
                spacing += 1.6 * tree.radius[cell] / cbrt(tree.count[cell].toDouble()) * tree.count[cell]
                weight += tree.count[cell]
            }
            val radius = if (weight == 0) 0.35f else (spacing / weight * 0.3).toFloat().coerceIn(0.01f, 0.45f)
            return MapScene(tree, radius, graph.titles, graph.ids, viewRadius(tree), theme, links = graph.edges, universe = universe)
        }
    }
}

/**
 * Which kind of scene [scene] is for its deaths (S05h2): "universe" or "look" in Space, as iOS keys its
 * memory (GraphUniverseScene.swift:214-218, Graph3DView.swift:1393-1397), so changing between the
 * Universe and a single style kills nothing; null in the other themes.
 */
internal fun deathKey(scene: MapScene): String? = when {
    scene.theme != MapTheme.SPACE -> null
    scene.universePlan != null -> "universe"
    else -> "look"
}

/** The Neurons map's camera pitch: 35 degrees down (GraphTilt.angle for Neurons). */
private const val NEURONS_PITCH = (35.0 * Math.PI / 180.0).toFloat()

/** True when this device can run the OpenGL ES 3.0 map. */
fun supportsGles3(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun Display.Mode.info() = DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)

/**
 * The map's [MapDrawn] record on its node, for tests (ScreenshotTest waits on it before it looks
 * at the screen). A key of its own: accessibility services do not read it.
 */
val MapDrawnKey = SemanticsPropertyKey<MapDrawn>("MapDrawn")
var SemanticsPropertyReceiver.mapDrawn by MapDrawnKey

/**
 * The map's onTap on its node, for tests, with the ids its body indices stand for ([MapScene.ids]):
 * a tap on body i is tap(i), as the map's own hit test hands it on. Robolectric can't draw GL, so no
 * body is on screen to hit, and MapPeekCardTest taps through this. A key of its own, as
 * [MapDrawnKey]: accessibility services do not read it.
 */
class MapTap(val ids: List<String>, val tap: (Int) -> Unit, val build: Int = 0, val cue: MapChoiceCue? = null)
val MapTapKey = SemanticsPropertyKey<MapTap>("MapTap")
var SemanticsPropertyReceiver.mapTap by MapTapKey

/**
 * A move of the camera asked for when a body is chosen (S05g): [id]'s body eased towards the middle
 * (Graph3DView.ease), or with [glide] brought there (a link chip, GraphDepthGlide). A new [serial]
 * asks again for the same body.
 */
data class MapEase(val id: String, val glide: Boolean, val serial: Int)

/** Fly in to the Universe folder [id] (S05h): each new [serial] flies again. */
data class MapFlyTo(val id: String, val serial: Int)

/**
 * The 3D map on screen. Frames follow Choreographer's vsync; the window asks for the
 * display's highest refresh mode and the surface for its highest frame rate, so the map
 * runs at the screen's own rate (plan 4e). With [continuous] false the map stops drawing
 * once nothing moves, and any touch brings it straight back.
 */
@Composable fun Map3DView(
    scene: MapScene,
    modifier: Modifier = Modifier,
    description: String,
    continuous: Boolean = false,
    autoOrbit: Boolean = false,
    /**
     * Touching the map (S05g; iOS's GraphTouchModel, decided while the finger is down): a still lift
     * before the hold is [onTap] with the body under it (null for empty space); a second one within
     * 0.30 s and 40 dp on a body is [onDoubleTap]; a finger still on a body for 0.55 s (scaled by the
     * person's touch-and-hold delay) is [onHold] there, before it lifts. Two taps on empty space bring
     * back the first view; a finger that moves turns the camera, two pan and zoom. In Space and the
     * Neurons a finger still on a body for 0.25 s (scaled the same way) picks it up (S05g2): moved, the
     * body follows it and springs home when let go, and the page hears nothing of it.
     */
    onTap: ((Int?) -> Unit)? = null,
    onDoubleTap: ((Int) -> Unit)? = null,
    onHold: ((Int, Offset) -> Unit)? = null,
    /** The speed readout at the top left (a debug tool: off unless asked for), and the zoom and recentre buttons at the bottom right. */
    showReadout: Boolean = false,
    showControls: Boolean = true,
    /** Each change puts the camera back to its first view (the Recentre tool). */
    recenter: Int = 0,
    corner: androidx.compose.ui.unit.Dp = 24.dp,
    /** Called once the map is first drawn: its first finished frame, or at once where it can't draw. */
    onDrawn: () -> Unit = {},
    /**
     * What the chrome covers over the map, from its top ([top]: the status bar and the top strip) and
     * its bottom ([hidden]: the foot and the navigation bar), as the 2D board takes them: the first
     * view fits the map to what they leave (HomeView.fit), as iOS's does (Graph3DView.swift:251-255).
     */
    top: androidx.compose.ui.unit.Dp = 0.dp,
    hidden: androidx.compose.ui.unit.Dp = 0.dp,
    /**
     * How far up from the map's foot the first-run card reaches, 0 once it is put away (S05d): the
     * first view is framed above it while it is up. When it grows, a camera still at its first view
     * follows; when it goes, the camera stays where it is and the next Recentre frames without it
     * (MapCardCover).
     */
    card: androidx.compose.ui.unit.Dp = 0.dp,
    /**
     * Lines: Straight (GraphLineStyle.inForce): the Space theme's links are drawn as straight
     * segments. It reaches the renderer as it changes and the next frame draws it, with no new
     * scene (S05e); the Neurons' bridges ignore it, as iOS's.
     */
    straight: Boolean = false,
    /**
     * The chosen body (its card is up) and the held one (its options are open), as [MapScene.ids]
     * ids, null for none (S05f): in Space and Neurons each keeps its name on show, as the pressed and
     * hovered bodies' do, and no other body has one.
     */
    chosenId: String? = null,
    heldId: String? = null,
    /** Show links round this body, null for every link (S05g): its own links only, the rest faded. */
    linksId: String? = null,
    /** The camera's move for a choice (S05g), null for none. */
    ease: MapEase? = null,
    /** Fly in (S05h): Space's Universe flies the camera in to this folder's system; Recentre comes back. */
    flyTo: MapFlyTo? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val display: Display? = remember(context) {
        // a context the system gave no display of its own throws here (Robolectric's activity does),
        // so the window manager's default display stands in
        (if (Build.VERSION.SDK_INT >= 30) runCatching { context.display }.getOrNull() else null)
            ?: @Suppress("DEPRECATION") (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }
    val modes = remember(display) { display?.supportedModes?.map { it.info() } ?: emptyList() }
    val maxHz = remember(modes) { RefreshRate.maxHz(modes, display?.refreshRate ?: 60f) }
    var readout by remember { mutableStateOf(MapReadout(displayMaxHz = maxHz)) }
    // per scene, as its renderer: another scene's bodies and counts are not this one's
    var screenNodes by remember(scene) { mutableStateOf<List<ScreenNode>>(emptyList()) }
    // the names drawn last frame, and the spot each took, which count a little bigger in this one (LabelRoom.HOLD)
    val named = remember(scene) { HashMap<Int, Int>() }
    var drawn by remember(scene) { mutableStateOf(MapDrawn()) }
    val hz = remember { floatArrayOf(display?.refreshRate ?: 60f) }
    val tree = scene.tree
    val camera = remember(scene) {
        // the whole map in view on a portrait screen (about 3:4), with room for the readout, until
        // the view's own shape is known (HomeView.fit)
        // the Neurons map looks down at 35 degrees (GraphTilt's Neurons angle)
        val pitch = if (scene.theme == MapTheme.NEURONS) NEURONS_PITCH else 0.7f
        OrbitCamera(target = Vec3(tree.cx[0], tree.cy[0], tree.cz[0]), pitch = pitch).apply {
            distance = max(fitDistance(scene.viewRadius, aspect = 0.75f), 4f)
            minDistance = scene.nodeRadius * 4; maxDistance = max(tree.radius[0], 1f) * 80
        }
    }
    val home = remember(scene) { HomeView(camera, scene.viewRadius) }
    val density = LocalDensity.current
    val insets = with(density) { MapInsets(top = top.toPx(), bottom = hidden.toPx()) }
    val cover = with(density) { card.toPx() }
    // the card's cover the home was last fitted to
    val framedCover = remember(home) { floatArrayOf(0f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // the choice's tick, pop and ease, once per Select: kept over every scene this map shows, so a
    // map built again with the same choice replays none of them (S05g)
    val cue = remember { MapChoiceCue(ease?.serial ?: 0) }
    // what the map counts over its life, across its scenes (S05h2); and whether the test clocks may be read
    val totals = remember { MapTotals() }
    val testHooks = remember(context) { context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val renderer = remember(scene) {
        val lively = android.animation.ValueAnimator.areAnimatorsEnabled()
        // only the links new since the scene on screen grow in; every link when there is none (LinkMemory);
        // in Space, the bodies it had and this scene lacks die where they were (S05h2)
        val recall = if (scene.theme == MapTheme.PLAIN) LinkRecall.EVERYTHING else LinkMemory.recall(scene.ids, scene.links, lively, deathKey(scene))
        GlMapRenderer(
            scene.tree, camera, scene.nodeRadius, displayHz = { hz[0] }, displayMaxHz = maxHz,
            onReadout = { r -> mainHandler.post { readout = r } },
            onScreenNodes = if (scene.titles.isEmpty()) null else { nodes -> mainHandler.post { screenNodes = nodes } },
            theme = scene.theme, assets = context.assets,
            density = context.resources.displayMetrics.density, contrast = increaseContrast(context),
            onDrawn = { d -> mainHandler.post { drawn = d } },
            viewRadius = scene.viewRadius,
            neurons = scene.neurons, lively = lively,
            build = scene.build, straight = straight, links = scene.links, ids = scene.ids, recall = recall,
            universe = scene.universe, universePlan = scene.universePlan,
            departures = recall.departures, testHooks = testHooks, totals = totals,
        ).also { it.choice = MapChoice(-1, cue.ticks) }
    }
    val gles3 = remember(context) { supportsGles3(context) }
    val loop = remember(renderer) { FrameLoop(renderer, continuous, autoOrbit, camera) { display?.refreshRate?.let { hz[0] = it } } }
    // Lines: the renderer reads it each frame, so a change only needs a frame drawn
    LaunchedEffect(renderer, straight) {
        if (renderer.straight != straight) { renderer.straight = straight; loop.moved() }
    }
    // the scene on screen is the one the next scene's links are compared with (LinkMemory); the
    // plain theme draws no growing links, so it leaves nothing to compare with
    DisposableEffect(scene) {
        if (scene.theme == MapTheme.PLAIN) LinkMemory.clear()
        else LinkMemory.remember(scene.build, scene.ids, scene.links, deathKey(scene), if (deathKey(scene) != null) renderer::departures else null)
        onDispose {}
    }
    // and once the map leaves the screen, a map opened again grows every link, as iOS's; a turn or
    // a fold rebuilds the activity, not the map, so the memory is kept for the map built again then
    val shownBuild by rememberUpdatedState(scene.build)
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) LinkMemory.forget(shownBuild) } }
    LaunchedEffect(recenter) {
        if (recenter != 0) { renderer.leaveFlight(); loop.touched(); synchronized(camera) { home.restore(camera) } }
    }
    // Fly in (S05h; iOS's fly(to:)): the folder's system framed in what the chrome leaves, over 0.8 s
    LaunchedEffect(renderer, flyTo) {
        val wanted = flyTo ?: return@LaunchedEffect
        val index = scene.indexOf(wanted.id) ?: return@LaunchedEffect
        if (renderer.flyTo(index, viewSize.width.toFloat(), viewSize.height.toFloat(), insets)) loop.touched()
    }
    // the chosen body and Show links reach the renderer as they change; the next frame draws them (S05g)
    val chosenIndex = remember(scene, chosenId) { scene.indexOf(chosenId) ?: -1 }
    val linksShown = linksId != null && chosenIndex >= 0
    val hostView = LocalView.current
    val serial = ease?.serial ?: 0
    LaunchedEffect(renderer, chosenIndex, linksShown, serial) {
        // a body chosen by a Select: iOS's light tick (syncChoice), Android's lightest, kept to the
        // person's haptics setting, and the pop; a scene built again with the choice kept does neither.
        // The body and its cue go to the renderer in one write, so no frame draws the one without the other
        val cued = cue.tick(serial, chosenIndex >= 0)
        renderer.choice = MapChoice(chosenIndex, cue.ticks)
        renderer.linksShown = linksShown
        if (cued) hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        loop.moved()
    }
    // the camera's ease towards a chosen body, frame by frame, until done or a finger comes down
    // (Graph3DView.ease): with animations off a tap moves nothing and a chip's glide lands at once
    val touches = remember(scene) { intArrayOf(0) }
    val easeLively = remember(scene) { android.animation.ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(renderer, ease) {
        // once per Select: a scene built again leaves the camera where it is
        if (!cue.ease(ease?.serial ?: 0)) return@LaunchedEffect
        val ask = ease ?: return@LaunchedEffect
        if (!ask.glide && !easeLively) return@LaunchedEffect
        val index = scene.indexOf(ask.id) ?: return@LaunchedEffect
        val body = renderer.bodyAt(index) ?: return@LaunchedEffect
        val w = viewSize.width.toFloat(); val h = viewSize.height.toFloat()
        val move = synchronized(camera) {
            if (ask.glide) MapCameraEase.glide(camera, body, w, h) else MapCameraEase.ease(camera, body, w, h)
        } ?: return@LaunchedEffect
        val seconds = if (!easeLively) 0f else if (ask.glide) MapCameraEase.GLIDE_SECONDS else MapCameraEase.EASE_SECONDS
        val run = CameraMove(move, seconds)
        val touched = touches[0]
        var last = withFrameNanos { it }
        while (!run.finished && touches[0] == touched) {
            val now = if (seconds <= 0f) last else withFrameNanos { it }
            val step = run.step((now - last) / 1e9f)
            last = now
            synchronized(camera) { camera.target = camera.target + step }
            loop.moved()
        }
    }
    // the first view fits the shape the map has (on its size, below) and what the chrome and the
    // first-run card leave of it, again when the chrome changes or the card comes or grows
    fun fitHome(size: IntSize, follow: Boolean = true) {
        if (synchronized(camera) { home.fit(camera, size.width, size.height, insets.coveringFoot(framedCover[0]), follow) }) loop.moved()
    }
    LaunchedEffect(home, insets) { fitHome(viewSize) }
    LaunchedEffect(home, cover) {
        val follow = MapCardCover.refits(framedCover[0], cover)
        framedCover[0] = cover
        fitHome(viewSize, follow)
    }

    // Ask the window for the highest refresh mode at this resolution while the map is shown.
    DisposableEffect(activity, modes) {
        val window = activity?.window
        val previous = window?.attributes?.preferredDisplayModeId ?: 0
        val current = display?.mode?.info()
        val best = current?.let { RefreshRate.bestMode(modes, it) }
        if (window != null && best != null) window.attributes = window.attributes.also { it.preferredDisplayModeId = best.id }
        onDispose { if (window != null && best != null) window.attributes = window.attributes.also { it.preferredDisplayModeId = previous } }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val drawnNow = drawn
    val latestDrawn by rememberUpdatedState(onDrawn)
    val firstDrawn = !gles3 || drawnNow.frames >= 1
    val box = remember(screenNodes) { drawnBox(screenNodes) }
    LaunchedEffect(firstDrawn) { if (firstDrawn) latestDrawn() }

    // The names (S05f): in Space and Neurons only the hovered, pressed, held and chosen bodies have
    // one, on iOS's pill, popping in, its words' style kept readable over what is behind it
    // (MapNameShow, LabelContrast); the plain theme keeps MapNames' layout, a name on every body.
    val pills = scene.theme != MapTheme.PLAIN && scene.titles.isNotEmpty()
    val lively = remember(scene) { android.animation.ValueAnimator.areAnimatorsEnabled() }
    val nameSettings = remember(scene) { LabelSettings(increaseContrast = increaseContrast(context)) }
    var pressedName by remember(scene) { mutableStateOf<Int?>(null) }
    var hoveredName by remember(scene) { mutableStateOf<Int?>(null) }
    var nameClock by remember(scene) { mutableLongStateOf(0L) }
    // in the Universe a name's rim takes its galaxy's tone (S05h, GraphUniverseScene.swift:111)
    val board = remember(scene) {
        val plan = scene.universePlan
        if (plan == null || scene.galaxyTones.isEmpty()) MapNameShow.Board()
        else MapNameShow.Board { i -> plan.bodies.getOrNull(i)?.galaxy?.let { scene.galaxyTones[it] } }
    }
    val chosenName = remember(scene, chosenId) { scene.indexOf(chosenId) }
    // a body hovered stops the Universe's orbit clock meanwhile (S05h, GraphMotion.swift:1752); a body
    // pressed does too, handed to the renderer by the touch itself (the gestures below), as iOS's is
    LaunchedEffect(renderer, hoveredName) { renderer.hover(hoveredName ?: -1) }
    // the held slot: the body whose options are open, else the one a finger holds to move (S05g2), which
    // the page never learns of (GraphMotion's wantLabel(grabbed), :2183); the options take it back
    var grabbedName by remember(scene) { mutableStateOf<Int?>(null) }
    val heldName = remember(scene, heldId) { scene.indexOf(heldId) } ?: grabbedName
    // each Space body's look by its index, for its size and its light (the tree keeps them by node)
    val spaceStyles = remember(scene) {
        if (scene.theme != MapTheme.SPACE) null
        else IntArray(scene.ids.size).also { for (k in 0 until tree.nodeCount) tree.order[k].takeIf { i -> i in it.indices }?.let { i -> it[i] = tree.groups[k].toInt() } }
    }
    // a body's pick radius: its own radius, a Space look's share of the note's (iOS's pickRadius)
    fun nameBody(n: ScreenNode): NameBody {
        val share = spaceStyles?.getOrNull(n.index)?.let { SpaceStyle.of(it).bodyScale } ?: 1f
        return NameBody(n.index, n.x, n.y, n.pixels, n.crisp, n.pixels / 2f * share)
    }
    fun shine(i: Int): LabelShine = spaceStyles?.getOrNull(i)?.let { LabelShine.space(SpaceStyle.of(it)) }
        ?: scene.neurons?.plan?.bodies?.getOrNull(i)?.let { LabelShine.neurons(it.role) } ?: LabelShine.DARK
    val namePaint = remember(density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = MapNameShow.WORDS_DP * density.density
        }
    }
    val drawPaint = remember(namePaint) { android.graphics.Paint(namePaint) }
    // each name's words, cut as iOS's, and their box at full size, measured the first time it shows
    val nameTexts = remember(scene, namePaint) { HashMap<Int, Pair<String, NameText>>() }
    fun nameText(i: Int): Pair<String, NameText> = nameTexts.getOrPut(i) {
        val folder = scene.neurons?.plan?.bodies?.getOrNull(i)?.let { it.kind != ThemeBodyKind.NOTE } ?: false
        val words = MapNameShow.cut(scene.titles.getOrElse(i) { "" }, folder)
        val r = android.graphics.Rect().also { namePaint.getTextBounds(words, 0, words.length, it) }
        words to NameText(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    }
    // what is behind a name's pill, estimated from the bodies on screen (LabelEstimate), and its body's tint
    fun nameMeasure(nodes: List<ScreenNode>): (Int) -> Pair<LabelBackdrop, LabelRgb>? {
        var glows: List<LabelGlow>? = null
        val cx = viewSize.width / 2.0; val cy = viewSize.height / 2.0
        val focal = camera.focalPixels(viewSize.height.toFloat()).toDouble()
        val backdrop = LabelShine.backdrop(neurons = scene.theme == MapTheme.NEURONS)
        return measure@{ i ->
            val node = nodes.firstOrNull { it.index == i } ?: return@measure null
            val p = MapNameShow.inside(MapNameShow.place(nameBody(node), nameText(i).second, density.density, 1f),
                viewSize.width.toFloat(), MapNameShow.EDGE_DP * density.density)
            val lights = glows ?: nodes.mapNotNull { n -> LabelEstimate.glow(n.x.toDouble(), n.y.toDouble(), nameBody(n).radius.toDouble(), shine(n.index), cx, cy, focal) }
                .also { glows = it }
            val points = LabelEstimate.pillPoints((p.left + p.right) / 2.0, (p.top + p.bottom) / 2.0, (p.right - p.left) / 2.0, (p.bottom - p.top) / 2.0, cx, cy, focal)
            LabelEstimate.measure(points, lights, backdrop) to shine(i).colour
        }
    }
    val nameWanted: List<Int> = if (!pills) emptyList() else {
        // a body picked up takes the chosen body's slot till the let-go, as iOS's grab does (MapNameShow.wanted)
        val asked = listOfNotNull(hoveredName, pressedName, heldName, grabbedName ?: chosenName)
        if (asked.isEmpty()) emptyList() else {
            val bodies = HashMap<Int, NameBody>()
            for (n in screenNodes) if (n.index in asked) bodies[n.index] = nameBody(n)
            MapNameShow.wanted(hoveredName, pressedName, heldName, chosenName, bodies, grabbedName)
        }
    }
    // a name goes up at once and pops in, its style eases, frame by frame while either moves; then it
    // waits for the bodies to move (a turn, a zoom) before it measures again
    LaunchedEffect(board, nameWanted) {
        board.keep(nameWanted, System.nanoTime() / 1e9)
        nameClock = System.nanoTime()
        if (nameWanted.isEmpty()) return@LaunchedEffect
        while (true) {
            val nodes = screenNodes
            val moving = withFrameNanos { board.step(System.nanoTime() / 1e9, lively, nameSettings, LabelSampler.interval(smooth = false), nameMeasure(nodes)) }
            nameClock = System.nanoTime()
            if (!moving) snapshotFlow { screenNodes }.first { it !== nodes }
        }
    }
    val namesNow: List<Pair<NamePill, LabelLook>> = if (nameWanted.isEmpty()) emptyList() else {
        val now = nameClock.let { System.nanoTime() / 1e9 }
        val measure = nameMeasure(screenNodes)
        nameWanted.mapNotNull { i ->
            val node = screenNodes.firstOrNull { it.index == i } ?: return@mapNotNull null
            val look = board.look(i, nameSettings, measure) ?: return@mapNotNull null
            MapNameShow.inside(MapNameShow.place(nameBody(node), nameText(i).second, density.density, board.scale(i, now, lively)),
                viewSize.width.toFloat(), MapNameShow.EDGE_DP * density.density) to look
        }
    }
    // a body a test can put a finger on: the biggest crisp one clear of the chrome and the card, in
    // every theme (Performance's too, S05g). Only the tests read it, so it is found where the
    // semantics are, in one pass with no lists made
    fun spot(): List<Float> {
        val margin = with(density) { 48.dp.toPx() }
        val right = viewSize.width - margin
        val high = insets.top + margin
        val low = viewSize.height - insets.bottom - cover - margin
        var best: ScreenNode? = null
        for (n in screenNodes) {
            if (!n.crisp || n.pixels <= 2f || n.x < margin || n.x > right || n.y < high || n.y > low) continue
            if (best == null || n.pixels > best.pixels) best = n
        }
        return best?.let { listOf(it.index.toFloat(), it.x, it.y) } ?: emptyList()
    }
    Box(modifier.background(Color(0xFF0E1B2C), RoundedCornerShape(corner)).semantics {
        contentDescription = description
        // the drawn bodies' box and the camera's pose, for the emulator's tests (S05d)
        mapDrawn = drawnNow.copy(box = box, pose = synchronized(camera) {
            listOf(camera.target.x, camera.target.y, camera.target.z, camera.distance, camera.yaw, camera.pitch)
        }, names = namesNow.map { (p, _) -> MapDrawnName(p.index, listOf(p.left, p.top, p.right, p.bottom), listOf(p.bodyX, p.bodyY)) }, spot = spot())
        val tapped = onTap
        if (tapped != null) mapTap = MapTap(scene.ids, { tapped(it) }, scene.build, cue)
    }
        .onSizeChanged { viewSize = it; fitHome(it) }) {
        val slop = LocalViewConfiguration.current.touchSlop
        // iOS's hold times, scaled by the person's touch-and-hold delay where it is longer (the packet's question 1)
        val holdScale = MapTouchRules.holdScale(LocalViewConfiguration.current.longPressTimeoutMillis)
        val latestTap by rememberUpdatedState(onTap)
        val latestDouble by rememberUpdatedState(onDoubleTap)
        val latestHold by rememberUpdatedState(onHold)
        // the last still lift: when, where and on what, for a double tap
        val lastLift = remember(scene) { LastLift() }
        // the map's touches (S05g), on the drawn map and, where GL can't draw it, on its note: there no body
        // is placed, so every touch is on empty space
        val gestures = Modifier.pointerInput(scene) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                loop.touched()
                touches[0]++
                var moved = 0f
                var multi = false
                var menu = false
                var liftedAt = down.uptimeMillis
                val reach = MapNameShow.PICK_DP.dp.toPx()
                val hit = MapNameShow.pick(screenNodes.map(::nameBody), down.position.x, down.position.y, reach)?.index
                val menuMillis = kotlin.math.ceil(MapTouchRules.HOLD_FOR_MENU * holdScale * 1000).toLong()
                // hold to move (S05g2): in Space and the Neurons a finger still on a body to the pick-up
                // time picks it up (held, Graph3DView.swift:2553-2610); not in Performance
                val dragMillis = kotlin.math.ceil(MapTouchRules.HOLD_TO_DRAG * holdScale * 1000).toLong()
                val canPick = hit != null && renderer.drags
                var picked = false
                var pickTried = false
                var dragging = false
                var pickedAt = Offset.Zero
                // the farthest the finger has gone from where the body was picked up: past the slop, it moves it
                var farthest = 0f
                var finger = down.position
                var depth = 0f
                var grabOffset = Vec3.ZERO
                // still on the body to the pick-up: it is picked up where it is, with a click, its depth and its
                // offset from the finger kept (Graph3DView.swift:2558-2581)
                fun pickUp() {
                    pickTried = true
                    val i = checkNotNull(hit)
                    val at = renderer.bodyAt(i) ?: return
                    if (!renderer.grab(i)) return
                    picked = true
                    pickedAt = finger
                    farthest = 0f
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    synchronized(camera) {
                        depth = camera.depthOf(at)
                        grabOffset = at - camera.unproject(finger.x, finger.y, depth, w, h)
                    }
                    grabbedName = i
                    hostView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    loop.moved()
                }
                fun letGo(still: Boolean) {
                    if (!picked) return
                    picked = false; dragging = false; farthest = 0f
                    renderer.release(still)
                    grabbedName = null
                    loop.moved()
                }
                // the pressed body keeps its name up while the finger stays on it (S05f); read, never consumed.
                // The renderer takes the press at once, from the touch, as iOS's at touch-down
                // (Graph3DView.swift:2922-2931): its orbit clock eases from this frame, not a recomposition later
                val press = MapNameShow.Press(slop)
                press.down(hit)
                var pressedNow = press.pressed ?: -1
                renderer.press(pressedNow)
                if (pills) pressedName = press.pressed
                try {
                    var now = down.uptimeMillis
                    while (true) {
                        // a finger still on a body waits for the pick-up and the hold, at most; then its options
                        // open, before it lifts. A body moved by the finger drops the options' wait
                        // once picked up, still until the finger goes past the slop from where it was picked up,
                        // whatever it wandered before (holdMoving, Graph3DView.swift:2584-2591)
                        val still = hit != null && !menu && !multi && MapTouchRules.heldStill(moved, slop, if (picked) farthest else null)
                        val pickWait = still && canPick && !picked && !pickTried
                        val menuWait = still && !dragging && latestHold != null
                        val deadline = when {
                            pickWait -> down.uptimeMillis + dragMillis
                            menuWait -> down.uptimeMillis + menuMillis
                            else -> -1L
                        }
                        val event = if (deadline < 0) awaitPointerEvent()
                            else withTimeoutOrNull(max(1L, deadline - now)) { awaitPointerEvent() }
                        if (event == null && pickWait) {
                            now = deadline
                            pickUp()
                            continue
                        }
                        if (event == null) {
                            // held still on the body to the hold's end: a hold, never a tap from here; a body
                            // picked up is put back where it is, at rest (holdStill, :2616-2623)
                            letGo(still = true)
                            menu = true
                            if (MapTouchRules.judge(MapTouch(onBody = true, held = menuMillis / 1000.0, moved = moved, lifted = false), slop, holdScale) == MapTouchKind.MENU) {
                                // iOS's light tick as the options open; Android gives a menu held open this one
                                hostView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                latestHold?.invoke(checkNotNull(hit), down.position)
                            }
                            continue
                        }
                        now = event.changes.maxOfOrNull { it.uptimeMillis } ?: now
                        // an event that comes at or past the pick-up time, the finger still till then, picks the body
                        // up first, as iOS's recogniser begins at 0.25 s whatever follows: by the events' own times,
                        // so a late frame never turns a hold that moved into a turn of the camera
                        if (pickWait && MapTouchRules.picksUp((now - down.uptimeMillis) / 1000.0, moved, slop, holdScale)) pickUp()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) { liftedAt = now; break }
                        loop.touched()
                        press.fingers(pressed.size)
                        if (pressed.size >= 2) {
                            // a second finger lets a held body go as a lift does; the fingers then pan and zoom
                            // (Android's own rule)
                            letGo(still = false)
                            multi = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            synchronized(camera) {
                                camera.zoom(zoom)
                                camera.pan(pan.x / size.height, pan.y / size.height)
                            }
                            moved += pan.getDistance() + kotlin.math.abs(zoom - 1f) * 100
                        } else {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            moved += delta.getDistance()
                            press.move(delta.getDistance())
                            finger = change.position
                            if (picked) {
                                // a body picked up follows the finger once it has moved past the slop from where it
                                // was picked up, keeping its offset, on the plane at its depth facing the camera;
                                // the camera stays (Graph3DView.swift:2584-2593)
                                farthest = max(farthest, (finger - pickedAt).getDistance())
                                if (!dragging && !MapTouchRules.heldStill(moved, slop, farthest)) dragging = true
                                if (dragging) {
                                    val w = size.width.toFloat(); val h = size.height.toFloat()
                                    val to = synchronized(camera) { camera.unproject(finger.x, finger.y, depth, w, h) } + grabOffset
                                    renderer.dragTo(to)
                                    loop.moved()
                                }
                            } else if ((moved > slop || multi) && !menu) {
                                // a finger that moves before the pick-up turns the camera, on a body or not
                                synchronized(camera) { camera.orbit(-delta.x / size.width * 3.2f, delta.y / size.height * 2.4f) }
                            }
                        }
                        if ((press.pressed ?: -1) != pressedNow) { pressedNow = press.pressed ?: -1; renderer.press(pressedNow) }
                        if (pills && pressedName != press.pressed) pressedName = press.pressed
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } finally {
                    // lifted, or the gesture cancelled: the press is over either way, and a body held is let
                    // go, with the finger's speed if it moved (release, GraphMotion.swift:1269-1283)
                    press.up()
                    renderer.press(-1)
                    if (pressedName != null) pressedName = null
                    letGo(still = !dragging)
                }
                val held = (liftedAt - down.uptimeMillis) / 1000.0
                val lift = MapTouchRules.judge(MapTouch(onBody = hit != null, held = held, moved = moved, lifted = true,
                    fingers = if (multi) 2 else 1), slop, holdScale, renderer.drags)
                if (menu || lift == MapTouchKind.ORBIT || lift == MapTouchKind.DRAG) { lastLift.clear(); return@awaitEachGesture }
                // a still lift: a tap, a double tap, or nothing (an empty touch held past the pick-up time)
                val paired = lastLift.at >= 0 && MapTouchRules.pairs((down.uptimeMillis - lastLift.at) / 1000.0,
                    (down.position - lastLift.where).getDistance(), MapTouchRules.DOUBLE_TAP_REACH_DP.dp.toPx())
                val touch = MapTouch(onBody = hit != null, held = held, moved = moved, lifted = true,
                    taps = if (paired && lastLift.hit == hit) 2 else 1)
                when (MapTouchRules.judge(touch, slop, holdScale)) {
                    MapTouchKind.DOUBLE_TAP -> { lastLift.clear(); latestDouble?.invoke(checkNotNull(hit)) ?: latestTap?.invoke(hit) }
                    MapTouchKind.TAP -> {
                        latestTap?.invoke(hit)
                        // two taps on empty space: the first view again (SceneKit's own double tap on iOS)
                        if (hit == null && paired && lastLift.hit == null) {
                            lastLift.clear()
                            synchronized(camera) { home.restore(camera) }
                            loop.moved()
                        } else lastLift.set(liftedAt, down.position, hit)
                    }
                    else -> lastLift.clear()
                }
            }
        }
        if (!gles3) {
            Box(Modifier.fillMaxSize().then(gestures))
            // the kit's secondary ink on the map's own night blue, as the map's other notes: its
            // callers keep it night (IdeasSpace's SortaTheme), so the ink is night's too
            Text("This device can’t draw the 3D map. Choose 2D in the theme menu.", Modifier.align(Alignment.Center).padding(WardSpace.xl.wardDp),
                style = WardType.body.textStyle(), color = wardColor(WardToken.inkSecondary), textAlign = TextAlign.Center)
            return@Box
        }
        // a GL view takes its renderer once, so a new scene (another look, a Neurons cell opened, a
        // new Link length) gets a view of its own; one kept would go on showing the old scene
        androidx.compose.runtime.key(renderer) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        preserveEGLContextOnPause = true
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) = requestFrameRate(h.surface, maxHz)
                            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) = requestFrameRate(h.surface, maxHz)
                            override fun surfaceDestroyed(h: SurfaceHolder) {}
                        })
                        loop.view = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        DisposableEffect(lifecycle, loop) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> { loop.view?.onResume(); loop.start() }
                    Lifecycle.Event.ON_PAUSE -> { loop.stop(); loop.view?.onPause() }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) loop.start()
            onDispose { lifecycle.removeObserver(observer); loop.stop() }
        }
        Canvas(
            Modifier.fillMaxSize().then(gestures).pointerInput(scene) {
                // a pointer with no button down (a mouse, a stylus) names the body under it (S05f)
                if (!pills) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val kind = when (event.type) {
                            PointerEventType.Enter -> MapNameShow.HoverEvent.ENTER
                            PointerEventType.Move -> MapNameShow.HoverEvent.MOVE
                            PointerEventType.Exit -> MapNameShow.HoverEvent.EXIT
                            else -> continue
                        }
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.type == PointerType.Touch) continue
                        val buttonDown = event.changes.any { it.pressed }
                        val picked = if (kind == MapNameShow.HoverEvent.EXIT || buttonDown) null
                            else MapNameShow.pick(screenNodes.map(::nameBody), change.position.x, change.position.y, MapNameShow.PICK_DP.dp.toPx())?.index
                        val next = MapNameShow.hover(hoveredName, kind, buttonDown, picked)
                        if (next != hoveredName) hoveredName = next
                    }
                }
            },
        ) {
            if (pills) {
                // the names on show (S05f): the rim, the pill over it, the words on top, in front of every body
                drawIntoCanvas { canvas ->
                    for ((p, look) in namesNow) {
                        val rimHeight = p.bottom - p.top
                        drawRoundRect(look.rimShade.color(look.rimAlpha), Offset(p.left, p.top), Size(p.right - p.left, rimHeight), CornerRadius(rimHeight / 2))
                        val pillHeight = p.pillBottom - p.pillTop
                        drawRoundRect(look.pill.color(look.opacity), Offset(p.pillLeft, p.pillTop), Size(p.pillRight - p.pillLeft, pillHeight), CornerRadius(pillHeight / 2))
                        drawPaint.textSize = p.textSize
                        drawPaint.color = look.text.color(1.0).toArgb()
                        canvas.nativeCanvas.drawText(nameText(p.index).first, p.textX, p.baseline, drawPaint)
                    }
                }
                return@Canvas
            }
            // the names (MapNames): biggest body first, each clear of the names placed, the links and
            // the other bodies, under its body or else beside it; one with no room waits
            if (scene.titles.isEmpty()) return@Canvas
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(225, 235, 249); textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                }
                val metrics = PaintMetrics(paint)
                // a word's room beside a name, so two in a row read as two; iOS's 2-point nudge
                // (GraphPerfLabelRoom.gap), doubled, above and below and round links and bodies
                val rules = MapNames.Rules(largest = 12.sp.toPx(), gapX = 8.dp.toPx(), gapY = 4.dp.toPx(), line = 1.dp.toPx())
                val names = MapNames.layout(screenNodes, scene.titles, scene.links, scene.linkHalf, HashMap(named), rules, metrics)
                named.clear()
                for (name in names) {
                    named[name.index] = name.spot
                    paint.textSize = name.textSize
                    canvas.nativeCanvas.drawText(name.label, name.box.x, name.baseline(metrics), paint)
                }
            }
        }
        if (showReadout) Readout(readout, Modifier.align(Alignment.TopStart).padding(10.dp))
        if (showControls) Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color(0xFF1D3047), RoundedCornerShape(24.dp))) {
            IconButton(onClick = { loop.touched(); synchronized(camera) { camera.zoom(1 / 1.25f) } }) { Icon(Icons.Rounded.Remove, "Zoom out", tint = Color.White) }
            IconButton(onClick = {
                loop.touched()
                synchronized(camera) { home.restore(camera) }
            }) { Icon(Icons.Rounded.RadioButtonChecked, "Recentre", tint = Color.White) }
            IconButton(onClick = { loop.touched(); synchronized(camera) { camera.zoom(1.25f) } }) { Icon(Icons.Rounded.Add, "Zoom in", tint = Color.White) }
        }
    }
}

@Composable private fun Readout(r: MapReadout, modifier: Modifier) {
    val style = Modifier.background(Color(0xCC0A1422), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
    Column(modifier.then(style)) {
        val line1 = String.format(Locale.ROOT, "%.0f fps · %.0f Hz max · %.1f ms frame · %.1f ms work", r.fps, r.displayMaxHz, r.frameMs, r.workMs)
        Text(line1, color = Color(0xFFE1EBF9), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text("${grouped(r.nodes)} nodes · ${grouped(r.links)} links", color = Color(0xFFB8C8DE), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text("${grouped(r.drawnNodes)} drawn · ${r.chunks} chunks · ${grouped(r.summaries)} summaries · ${grouped(r.bundles)} bundles",
            color = Color(0xFF8FA6C4), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

/** A name pill's colour, at [alpha]. */
private fun LabelRgb.color(alpha: Double) = Color(r.toFloat(), g.toFloat(), b.toFloat(), alpha.toFloat().coerceIn(0f, 1f))

/** Increase Contrast: the system's contrast raised (Android 14+), as iOS's accessibility setting. */
private fun increaseContrast(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    val modes = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager ?: return false
    return runCatching { modes.contrast >= 0.5f }.getOrDefault(false)
}

private fun requestFrameRate(surface: Surface, hz: Float) {
    if (!surface.isValid) return
    runCatching {
        when {
            Build.VERSION.SDK_INT >= 31 -> surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS)
            Build.VERSION.SDK_INT >= 30 -> surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }
}

/**
 * Drives frames from Choreographer: each vsync hands its timestamp to the renderer and
 * asks for one frame. Idle maps stop after a quiet second with three frames finished in it
 * (FrameQuiet); a touch restarts at once.
 */
/** A name's words measured by the map's own paint, at each size asked. */
private class PaintMetrics(private val paint: android.graphics.Paint) : NameMetrics {
    private fun at(size: Float) = paint.also { it.textSize = size }
    override fun width(label: String, size: Float) = at(size).measureText(label)
    override fun ascent(size: Float) = at(size).fontMetrics.ascent
    override fun descent(size: Float) = at(size).fontMetrics.descent
}

/**
 * The camera's first view, which Recentre goes back to. [fit] sizes it to the view's own shape and
 * what the chrome leaves of it ([MapInsets]) once those are known, and again when they change (a
 * turn, a fold, the strip's height): the map's [radius] round its centre in view across the narrower
 * side of that window, its centre in the window's middle (OrbitCamera.fitWindow, as iOS's framing).
 * Sorta's: the reference kept a 3:4 portrait fit, which cut a phone's map off at the sides and left a
 * tablet's small, and fitted the whole view, so what the bottom bar hides was never in its sum.
 */
internal class HomeView(camera: OrbitCamera, private val radius: Float) {
    private val centre = camera.target
    private var target = camera.target
    private var distance = camera.distance
    private val yaw = camera.yaw
    private val pitch = camera.pitch
    private val lens = OrbitCamera(yaw = yaw, pitch = pitch, fovY = camera.fovY)
    // where a camera still at its first view was left when the home moved without it (the card put
    // away): it is still the first view, so the next change of the chrome takes it, as iOS's untouched
    private var left: Pair<Vec3, Float>? = null

    fun restore(camera: OrbitCamera) { camera.target = target; camera.distance = distance; camera.yaw = yaw; camera.pitch = pitch }

    private fun at(camera: OrbitCamera, target: Vec3, distance: Float) =
        camera.target == target && camera.distance == distance && camera.yaw == yaw && camera.pitch == pitch

    /**
     * Fits to a [width] x [height] view under [insets]; a camera still at its first view moves with
     * it when it [follow]s, and then this returns true. Without, the home moves and the camera stays
     * (the first-run card put away, S05d), still counted at its first view for the next fit.
     */
    fun fit(camera: OrbitCamera, width: Int, height: Int, insets: MapInsets, follow: Boolean = true): Boolean {
        if (width <= 0 || height <= 0) return false
        val first = at(camera, target, distance) || left?.let { at(camera, it.first, it.second) } == true
        left = if (first && !follow) camera.target to camera.distance else null
        val fit = lens.fitWindow(centre, radius, width.toFloat(), height.toFloat(), insets)
        distance = fit.distance; target = fit.target
        val there = first && follow
        if (there) restore(camera)
        return there
    }
}

private class FrameLoop(
    private val renderer: GlMapRenderer,
    private val continuous: Boolean,
    private var autoOrbit: Boolean,
    private val camera: OrbitCamera,
    private val sampleDisplay: () -> Unit,
) : Choreographer.FrameCallback {
    var view: GLSurfaceView? = null
    private var running = false
    private var lastActive = 0L
    /** The renderer's finished frames when the map last moved. */
    private var finishedAtActive = 0
    private var lastFrame = 0L
    private var frames = 0

    fun start() { if (!running) { running = true; active(System.nanoTime()); Choreographer.getInstance().postFrameCallback(this) } }
    fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }
    fun touched() { autoOrbit = false; moved() }
    /** The camera moved by itself (HomeView.fit): draw again, and keep any orbit going. */
    fun moved() { active(System.nanoTime()); if (!running && view != null) start() }

    private fun active(nanos: Long) { lastActive = nanos; finishedAtActive = renderer.finished }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (frames++ % 60 == 0) sampleDisplay()
        if (autoOrbit && lastFrame != 0L) synchronized(camera) { camera.orbit(((frameTimeNanos - lastFrame) / 1e9f) * 0.08f, 0f) }
        lastFrame = frameTimeNanos
        renderer.vsyncNanos = frameTimeNanos
        view?.requestRender()
        if (renderer.animating || autoOrbit) active(frameTimeNanos)
        if (continuous || !FrameQuiet.settled(frameTimeNanos - lastActive, renderer.finished - finishedAtActive)) {
            Choreographer.getInstance().postFrameCallback(this)
        } else running = false
    }
}

/** The last still lift on the map: when (uptime ms, −1 for none), where, and on which body; for a double tap. */
private class LastLift {
    var at = -1L; private set
    var where = Offset.Zero; private set
    var hit: Int? = null; private set
    fun set(time: Long, position: Offset, body: Int?) { at = time; where = position; hit = body }
    fun clear() { at = -1L; hit = null }
}
