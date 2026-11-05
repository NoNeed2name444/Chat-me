package com.cramdown.sorta.ui.map

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Choreographer
import android.view.Display
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.cramdown.sorta.map.LinkMemory
import com.cramdown.sorta.map.LinkRecall
import com.cramdown.sorta.map.MapCardCover
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
import com.cramdown.sorta.map.grouped
import com.cramdown.sorta.ui.ward.WardSpace
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WardType
import com.cramdown.sorta.ui.ward.textStyle
import com.cramdown.sorta.ui.ward.wardDp
import com.cramdown.sorta.ui.wardColor
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cbrt
import kotlin.math.max

/** A map ready to draw: its octree, how big a node is, and the notes behind the nodes. */
/** [viewRadius] holds 95% of the nodes: the home view frames those, not a few far outliers. */
/** [theme] says how its notes are drawn; in [MapTheme.SPACE] each node's group is its SpaceStyle code. */
/** [links] are its links as pairs of node indices, and [linkHalf] how far a drawn link reaches each side of its line (world units): names keep off them. */
class MapScene(val tree: Octree, val nodeRadius: Float, val titles: List<String>, val ids: List<String>, val viewRadius: Float = tree.radius[0], val theme: MapTheme = MapTheme.PLAIN,
               /** [MapTheme.NEURONS]: the cells and links NeuronGl draws; the tree then only holds their places, by body. */
               val neurons: NeuronScene? = null,
               val links: IntArray = IntArray(0), val linkHalf: Float = 0f) {
    /** This scene's number, new for every scene built in the process (MapDrawn.build, S05e): a test can tell a redraw from a rebuild. */
    val build: Int = BUILDS.incrementAndGet()

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

        /** [styles], in Space, is each node's SpaceStyle code (the Look menu's choice); by default its links decide. */
        fun of(graph: MapGraph, leafCapacity: Int = 4096, theme: MapTheme = MapTheme.PLAIN, styles: ByteArray? = null): MapScene {
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
            return MapScene(tree, radius, graph.titles, graph.ids, viewRadius(tree), theme, links = graph.edges)
        }
    }
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
 * The map's [onOpenNode] on its node, for tests, with the ids its body indices stand for
 * ([MapScene.ids]): a tap on body i is tap(i), as the map's own hit test hands it on. Robolectric
 * can't draw GL, so no body is on screen to hit, and MapPeekCardTest taps through this. A key of
 * its own, as [MapDrawnKey]: accessibility services do not read it.
 */
class MapTap(val ids: List<String>, val tap: (Int) -> Unit)
val MapTapKey = SemanticsPropertyKey<MapTap>("MapTap")
var SemanticsPropertyReceiver.mapTap by MapTapKey

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
    onOpenNode: ((Int) -> Unit)? = null,
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
    val renderer = remember(scene) {
        val lively = android.animation.ValueAnimator.areAnimatorsEnabled()
        // only the links new since the scene on screen grow in; every link when there is none (LinkMemory)
        val recall = if (scene.theme == MapTheme.PLAIN) LinkRecall.EVERYTHING else LinkMemory.recall(scene.ids, scene.links, lively)
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
        )
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
        else LinkMemory.remember(scene.build, scene.ids, scene.links)
        onDispose {}
    }
    // and once the map leaves the screen, a map opened again grows every link, as iOS's; a turn or
    // a fold rebuilds the activity, not the map, so the memory is kept for the map built again then
    val shownBuild by rememberUpdatedState(scene.build)
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) LinkMemory.forget(shownBuild) } }
    LaunchedEffect(recenter) {
        if (recenter != 0) { loop.touched(); synchronized(camera) { home.restore(camera) } }
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
    Box(modifier.background(Color(0xFF0E1B2C), RoundedCornerShape(corner)).semantics {
        contentDescription = description
        // the drawn bodies' box and the camera's pose, for the emulator's tests (S05d)
        mapDrawn = drawnNow.copy(box = box, pose = synchronized(camera) {
            listOf(camera.target.x, camera.target.y, camera.target.z, camera.distance, camera.yaw, camera.pitch)
        })
        if (onOpenNode != null) mapTap = MapTap(scene.ids, onOpenNode)
    }
        .onSizeChanged { viewSize = it; fitHome(it) }) {
        if (!gles3) {
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
        val slop = LocalViewConfiguration.current.touchSlop
        Canvas(
            Modifier.fillMaxSize().pointerInput(scene) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    loop.touched()
                    var moved = 0f
                    var multi = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        loop.touched()
                        if (pressed.size >= 2) {
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
                            if (moved > slop || multi) synchronized(camera) { camera.orbit(-delta.x / size.width * 3.2f, delta.y / size.height * 2.4f) }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    if (!multi && moved <= slop && onOpenNode != null) {
                        val hit = screenNodes.filter { it.pixels > 2f }.minByOrNull { (Offset(it.x, it.y) - down.position).getDistance() }
                        if (hit != null && (Offset(hit.x, hit.y) - down.position).getDistance() < 40.dp.toPx()) onOpenNode(hit.index)
                    }
                }
            },
        ) {
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
