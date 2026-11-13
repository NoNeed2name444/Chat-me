package com.cramdown.sorta.ui.map

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.cramdown.sorta.map.ChunkCache
import com.cramdown.sorta.map.CameraFlight
import com.cramdown.sorta.map.MapFly
import com.cramdown.sorta.map.MapInsets
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniverseLooks
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.UniverseRole
import com.cramdown.sorta.map.FrameBudget
import com.cramdown.sorta.map.FilterLinkGrowth
import com.cramdown.sorta.map.FilterPop
import com.cramdown.sorta.map.LinkWant
import com.cramdown.sorta.map.FilterSlice
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.FramePlan
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.FrameStats
import com.cramdown.sorta.map.GuardBand
import com.cramdown.sorta.map.LinkGrowth
import com.cramdown.sorta.map.LinkRecall
import com.cramdown.sorta.map.LodSelector
import com.cramdown.sorta.map.MapChoiceMotion
import com.cramdown.sorta.map.MapDrag
import com.cramdown.sorta.map.MapDragPath
import com.cramdown.sorta.map.MapFocus
import com.cramdown.sorta.map.MapMark
import com.cramdown.sorta.map.MapPalette
import com.cramdown.sorta.map.MapPop
import com.cramdown.sorta.map.NeuronDepth
import com.cramdown.sorta.map.NeuronPalette
import com.cramdown.sorta.map.NeuronScene
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.SpaceBodies
import com.cramdown.sorta.map.SpaceDepth
import com.cramdown.sorta.map.SpaceDepthBand
import com.cramdown.sorta.map.SpaceLinkGrowth
import com.cramdown.sorta.map.SpaceLod
import com.cramdown.sorta.map.SpaceOptics
import com.cramdown.sorta.map.SpacePalette
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.map.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** What the frame readout shows, published a few times a second. */
data class MapReadout(
    val fps: Double = 0.0,
    val displayMaxHz: Float = 60f,
    val displayHz: Float = 60f,
    val frameMs: Double = 0.0,
    val workMs: Double = 0.0,
    val nodes: Int = 0,
    val links: Int = 0,
    val drawnNodes: Int = 0,
    val chunks: Int = 0,
    val summaries: Int = 0,
    val bundles: Int = 0,
    val resident: Int = 0,
    val detail: Float = 1f,
    /** Notes drawn as Space bodies this frame (0 in the plain theme). */
    val bodies: Int = 0,
)

/**
 * The chosen body as the page hands it to the renderer (S05g): its scene index, −1 for none, and how
 * many choices the page has cued (MapChoiceCue.tick), a new count popping it and growing its ring in.
 */
data class MapChoice(val chosen: Int, val cues: Int)

/**
 * What the map has finished drawing on its GL context, for tests: Map3DView puts it on the
 * map's node as [MapDrawnKey], which accessibility services do not read. The counts stop at
 * [ENOUGH], so the record changes a few times per context, never every frame. A frame counts
 * once its draw calls are issued; the next frame starts only after it was handed to the screen.
 * The rest says what this GL draws with: its renderer, the widest point it allows (summary
 * stars and the plain theme's nodes are points), the surface's size, and how many of the six
 * Space styles have their body shader (0 in the plain theme).
 *
 * The links' fields (S05e) say which scene is drawn ([build], MapScene.build), how many links the
 * latest frame drew as strips, how many samples a strip has, and whether they were drawn straight
 * (Lines); the record changes whenever one of them does, however many frames have been counted.
 */
data class MapDrawn(
    val frames: Int = 0,
    /** Frames that drew Space bodies, and how many bodies the latest of them drew. */
    val bodyFrames: Int = 0,
    val bodies: Int = 0,
    /** Milliseconds from the surface's start to the first frame, and to the first with bodies; -1 before. */
    val firstFrameMs: Long = -1,
    val firstBodyFrameMs: Long = -1,
    val width: Int = 0,
    val height: Int = 0,
    val renderer: String = "",
    val maxPointSize: Float = 0f,
    val bodyStyles: Int = 0,
    /**
     * Added by the map's own view (Map3DView), for the emulator's tests (S05d): the drawn bodies'
     * screen box (left, top, right, bottom, in the view's pixels; empty before any is drawn) and the
     * camera's pose (target x, y, z, distance, yaw, pitch).
     */
    val box: List<Float> = emptyList(),
    val pose: List<Float> = emptyList(),
    val build: Int = 0,
    val links: Int = 0,
    val samples: Int = 0,
    val straight: Boolean = false,
    /**
     * Added by Map3DView too (S05f): the names on show in Space and Neurons, and [spot], a body a test
     * can put a finger on, in every theme (Performance's since S05g): the biggest crisp body clear of
     * the chrome, as its index, centre x and y in the view's pixels (empty when none is). No words.
     */
    val names: List<MapDrawnName> = emptyList(),
    val spot: List<Float> = emptyList(),
    /**
     * The chosen body as the renderer drew it (S05g): its scene index, −1 for none; whether its mark
     * (Space's orbit ring, the Neurons' hot halo, Performance's white ring) was drawn in the latest
     * frame; how many links were drawn lit; and under Show links ([focus]) how many links were drawn
     * ([focusLinks]: beams, bridges, and the chosen body's own links in the chunks drawn) and how
     * many bodies, points and summaries were drawn at 0.15 ([faded]). No words.
     */
    val chosen: Int = -1,
    val marked: Boolean = false,
    val lit: Int = 0,
    val focus: Boolean = false,
    val focusLinks: Int = 0,
    val faded: Int = 0,
    /**
     * A body held (S05g2): its scene index, −1 for none; its centre on screen (x, y in the view's
     * pixels, whole; empty when none is held); the bodies off home; and those of them other than the
     * one held (pulled by its links, or carried inside it); the held body's own offset from its home
     * and the chosen body's, in the world (x, y, z; empty when that body is at home or there is none),
     * so a carry is read whatever each body's depth. Since the last let-go: the frames drawn until every
     * body was home ([letGoFrames], not capped as [frames] is), the most iOS's spring gives it
     * ([restFrames], MapDrag.framesToRest), and the latest frame's interval while a body moved
     * ([frameMs]), so a slow device is judged by its frames. No words.
     */
    val grabbed: Int = -1,
    val dragged: List<Float> = emptyList(),
    val moving: Int = 0,
    val carried: Int = 0,
    val heldOffset: List<Float> = emptyList(),
    val chosenOffset: List<Float> = emptyList(),
    val letGoFrames: Int = 0,
    val restFrames: Int = 0,
    val frameMs: Float = 0f,
    /**
     * Space's Universe (S05h): its bodies by role (counts in UniverseRole's order; empty elsewhere); the
     * links drawn in the near set (kinds 0 and 1), arched (kinds 1 and 2, none under Lines › Straight)
     * and in the far set (kind 2); the folder body flown in to (its scene index, −1 for none); and the
     * orbit clock in whole seconds with its rate to a hundredth, so a still map's record stays the same. No words.
     */
    val roles: List<Int> = emptyList(),
    val nearLinks: Int = 0,
    val archedLinks: Int = 0,
    val farLinks: Int = 0,
    val flown: Int = -1,
    val orbitSeconds: Int = 0,
    val orbitRate: Float = 0f,
    /**
     * The Universe's held body's members (S05h): each body that moves with it, as its scene index then
     * its own offset from home (x, y, z), four floats each; empty when nothing is held.
     */
    val memberOffsets: List<Float> = emptyList(),
    /**
     * Frames that stepped the Universe's lively orbit clock (S05h), never capped as [frames] is, so a
     * test can count the clock's own steps past a press (each steps it by the frame's interval, at most
     * 0.25 s). It moves only while the orbits turn, when the map changes every frame anyway; a still
     * map's record stays the same.
     */
    val orbitSteps: Int = 0,
    /**
     * The Universe's press (S05h), on the map's own clock: the seconds the lively orbit clock has
     * stepped while a body was pressed, every press together, never capped (MapDrag.pressedTime); and
     * for the latest press, the seconds of it stepped until the orbit rate was under 0.05, unrounded
     * (−1 till then; MapDrag.pressSlowedAfter). So a test judges a held finger's ease by the map's time
     * however late the record reaches it. They move only while a finger is on a body.
     */
    val pressedSeconds: Float = 0f,
    val pressSlowedSeconds: Float = -1f,
    /**
     * The device's uptime (SystemClock.uptimeMillis) at the renderer frame that first took the
     * current press (S05h repair 5), −1 while no press is taken; reset when the press ends. A test
     * notes the uptime before its finger comes down, so the touch's way to the renderer is timed on
     * the same clock as the press's ease, however late the record reaches it.
     */
    val pressTakenAtMs: Long = -1,
    /**
     * The filter (S05i): whether one is in force; the bodies the latest frame drew and those it hid
     * (a body on its way out counts as drawn until it is gone; in the Neurons the hidden are counted as
     * drawn, elsewhere as the filter has them); the links the filter lets through; and in the Neurons
     * the ideas drawn in each cell state, in NeuronState's order (empty elsewhere). No words.
     */
    val filtered: Boolean = false,
    val shown: Int = 0,
    val hidden: Int = 0,
    val filterLinks: Int = 0,
    val states: List<Int> = emptyList(),
    /**
     * The level of detail the latest frame chose (S05i repair 3), so a view drawn without bodies says
     * why: the frame budget's [detail] (FrameBudget.detail, to 0.01), the chunks drawn node by node
     * ([chunks]), those of them fully open, where a Space body may be drawn ([openChunks],
     * SpaceBodies.OPEN), and the [summaries] drawn for the rest. They change as the budget steps or
     * a chunk opens or closes, never on a still map's frames.
     */
    val detail: Float = 0f,
    val chunks: Int = 0,
    val openChunks: Int = 0,
    val summaries: Int = 0,
) {
    /** This record with the latest frame's level of detail, [detailNow] to 0.01; the same record when nothing changed. */
    fun detailing(detailNow: Float, chunksNow: Int, openNow: Int, summariesNow: Int): MapDrawn {
        val rounded = Math.round(detailNow * 100f) / 100f
        return if (detail == rounded && chunks == chunksNow && openChunks == openNow && summaries == summariesNow) this
        else copy(detail = rounded, chunks = chunksNow, openChunks = openNow, summaries = summariesNow)
    }

    /** This record with the filter's counts as the latest frame had them; the same record when nothing changed. */
    fun filtering(filteredNow: Boolean, shownNow: Int, hiddenNow: Int, filterLinksNow: Int, statesNow: List<Int>): MapDrawn =
        if (filtered == filteredNow && shown == shownNow && hidden == hiddenNow && filterLinks == filterLinksNow && states == statesNow) this
        else copy(filtered = filteredNow, shown = shownNow, hidden = hiddenNow, filterLinks = filterLinksNow, states = statesNow)

    /** This record after one more step of the lively orbit clock ([orbitSteps]). */
    fun stepped(): MapDrawn = copy(orbitSteps = orbitSteps + 1)

    /**
     * This record with the press's seconds as the latest frame stepped them, and, when [takenNow]
     * (MapDrag.pressTaken), the uptime the press was first taken: kept from an earlier frame of the
     * same press, else [nowMs] read once, at this frame; −1 when no press is taken. The same record
     * when nothing changed.
     */
    fun pressing(secondsNow: Float, slowedNow: Float, takenNow: Boolean = false, nowMs: () -> Long = { -1L }): MapDrawn {
        val takenAt = if (!takenNow) -1L else if (pressTakenAtMs >= 0) pressTakenAtMs else nowMs()
        return if (pressedSeconds == secondsNow && pressSlowedSeconds == slowedNow && pressTakenAtMs == takenAt) this
        else copy(pressedSeconds = secondsNow, pressSlowedSeconds = slowedNow, pressTakenAtMs = takenAt)
    }

    /** This record with the Universe as the latest frame drew it; the same record when nothing changed. */
    fun universe(rolesNow: List<Int>, nearNow: Int, archedNow: Int, farNow: Int, secondsNow: Int, rateNow: Float): MapDrawn =
        if (roles == rolesNow && nearLinks == nearNow && archedLinks == archedNow && farLinks == farNow &&
            orbitSeconds == secondsNow && orbitRate == rateNow) this
        else copy(roles = rolesNow, nearLinks = nearNow, archedLinks = archedNow, farLinks = farNow,
            orbitSeconds = secondsNow, orbitRate = rateNow)

    /** This record with [flownNow] flown in to (−1 for none); the same record when it is already. */
    fun flying(flownNow: Int): MapDrawn = if (flown == flownNow) this else copy(flown = flownNow)

    /** This record with the held body as the latest frame drew it; the same record when nothing changed. */
    fun dragging(grabbedNow: Int, draggedNow: List<Float>, movingNow: Int, carriedNow: Int,
                 heldOffsetNow: List<Float> = emptyList(), chosenOffsetNow: List<Float> = emptyList(),
                 letGoFramesNow: Int = letGoFrames, restFramesNow: Int = restFrames, frameMsNow: Float = frameMs,
                 memberOffsetsNow: List<Float> = memberOffsets): MapDrawn =
        if (grabbed == grabbedNow && dragged == draggedNow && moving == movingNow && carried == carriedNow &&
            heldOffset == heldOffsetNow && chosenOffset == chosenOffsetNow && letGoFrames == letGoFramesNow &&
            restFrames == restFramesNow && frameMs == frameMsNow && memberOffsets == memberOffsetsNow) this
        else copy(grabbed = grabbedNow, dragged = draggedNow, moving = movingNow, carried = carriedNow,
            heldOffset = heldOffsetNow, chosenOffset = chosenOffsetNow, letGoFrames = letGoFramesNow,
            restFrames = restFramesNow, frameMs = frameMsNow, memberOffsets = memberOffsetsNow)

    /** This record with the chosen body's marks as the latest frame drew them; the same record when nothing changed. */
    fun marking(chosenNow: Int, markedNow: Boolean, litNow: Int, focusNow: Boolean, focusLinksNow: Int, fadedNow: Int): MapDrawn =
        if (chosen == chosenNow && marked == markedNow && lit == litNow && focus == focusNow && focusLinks == focusLinksNow && faded == fadedNow) this
        else copy(chosen = chosenNow, marked = markedNow, lit = litNow, focus = focusNow, focusLinks = focusLinksNow, faded = fadedNow)

    /**
     * This record after one more finished frame that drew [bodiesNow] bodies, [elapsedMs] after the
     * surface's start, and [linksNow] links of [samplesNow] samples each, straight or not ([straightNow]).
     */
    fun after(bodiesNow: Int, elapsedMs: Long, linksNow: Int = links, samplesNow: Int = samples, straightNow: Boolean = straight): MapDrawn {
        val withBodies = bodiesNow > 0
        val counted = if (frames >= ENOUGH && (bodyFrames >= ENOUGH || !withBodies)) this else copy(
            frames = min(frames + 1, ENOUGH),
            bodyFrames = if (withBodies) min(bodyFrames + 1, ENOUGH) else bodyFrames,
            bodies = if (withBodies) bodiesNow else bodies,
            firstFrameMs = if (firstFrameMs < 0) elapsedMs else firstFrameMs,
            firstBodyFrameMs = if (withBodies && firstBodyFrameMs < 0) elapsedMs else firstBodyFrameMs,
        )
        if (counted.links == linksNow && counted.samples == samplesNow && counted.straight == straightNow) return counted
        return counted.copy(links = linksNow, samples = samplesNow, straight = straightNow)
    }

    companion object {
        /** Finished frames worth counting: by the last of them, the ones before it were handed to the screen. */
        const val ENOUGH = 3
    }
}

/** A name on show (MapDrawn.names): its body's index, its pill's box (left, top, right, bottom) and its body's centre (x, y), in the view's pixels. */
data class MapDrawnName(val index: Int, val box: List<Float>, val body: List<Float>)

/**
 * When an idle map stops drawing (Map3DView's frame loop): once nothing has moved for a second
 * and it has finished [MapDrawn.ENOUGH] frames since, so the last of them shows it settled even
 * where one frame takes longer than that second (a software GL builds each shader on its first
 * draw, and a still Neurons map once stopped after two frames). Ten quiet seconds stop it
 * regardless, so a surface that never draws does not keep the loop running.
 */
object FrameQuiet {
    const val QUIET_NANOS = 1_000_000_000L
    const val GIVE_UP_NANOS = 10_000_000_000L

    /** [stillNanos] since the map last moved; [finishedSince] frames finished since then. */
    fun settled(stillNanos: Long, finishedSince: Int): Boolean =
        stillNanos >= QUIET_NANOS && (finishedSince >= MapDrawn.ENOUGH || stillNanos >= GIVE_UP_NANOS)
}

/** How the map draws its notes: plain coloured points, the Space theme's bodies, or the Neurons theme's cells (NeuronGl). */
enum class MapTheme { PLAIN, SPACE, NEURONS }

/**
 * A node's place on screen, for labels and taps (real notes only). [crisp] is false where the
 * Space theme's depth fade has begun: its name does not show there (iOS shows names only in
 * the crisp zone); a note past the band's end is not listed at all, so it cannot be picked.
 */
/** [perUnit] is how many pixels a world unit spans at its depth, which sizes its links' bands for the names. */
data class ScreenNode(val index: Int, val x: Float, val y: Float, val pixels: Float, val crisp: Boolean = true, val perUnit: Float = 0f)

/**
 * The screen box of the drawn [nodes], each its drawn size ([ScreenNode.pixels] across) round its
 * centre: left, top, right, bottom, in the view's pixels; empty when none is drawn bigger than a
 * speck (MapDrawn.box, S05d).
 */
fun drawnBox(nodes: List<ScreenNode>): List<Float> {
    val drawn = nodes.filter { it.pixels > 2f }
    if (drawn.isEmpty()) return emptyList()
    return listOf(drawn.minOf { it.x - it.pixels / 2 }, drawn.minOf { it.y - it.pixels / 2 },
        drawn.maxOf { it.x + it.pixels / 2 }, drawn.maxOf { it.y + it.pixels / 2 })
}

/**
 * Draws the 3D Ideas map with OpenGL ES 3.0, the baseline every Android 8+ phone has.
 * Every node (12 bytes: 16-bit position, chunk id, colour group) is resident in one GPU
 * buffer from the first frame, and each chunk's morph state is one texel of a float
 * texture, so changing the level of detail is choosing ranges and writing a few kilobytes,
 * never moving the million nodes. Link lists stream into a paged GPU pool through the LRU
 * [ChunkCache]. Draws: link bundles (instanced quads), links, summary points, nodes, all
 * additive, so detail changes by size and position, never by opacity.
 *
 * In the [MapTheme.SPACE] theme a note's colour group is its style (SpaceStyle), the near
 * notes become their bodies (SpaceBodies, drawn by SpaceGl over everything else, their points
 * hidden by a one-byte-per-node mask) and the links glow warm, tinted by their notes' styles.
 *
 * The chosen body ([chosen], S05g) is marked in each theme (Space's orbit ring, the Neurons' hot
 * halo, Performance's larger point with a white ring), pops while animations run, and lights its
 * links; under [linksShown] (Show links) the far layers learn it from uniforms and one more
 * one-byte-per-node mask (the neighbourhood), not from a rebuilt cache.
 */
class GlMapRenderer(
    val tree: Octree,
    val camera: OrbitCamera,
    private val nodeRadius: Float,
    /** Display refresh rate now, read every frame (it changes when the mode switches). */
    private val displayHz: () -> Float,
    private val displayMaxHz: Float,
    private val onReadout: (MapReadout) -> Unit,
    private val onScreenNodes: ((List<ScreenNode>) -> Unit)? = null,
    private val theme: MapTheme = MapTheme.PLAIN,
    /** Where the Space theme's Earth maps are read from; null draws the rocky planet from noise. */
    assets: AssetManager? = null,
    /** Pixels per dp: the depth fade's sizes on screen are iOS's points. */
    private val density: Float = 1f,
    /** How far the Space theme's depth fade reaches (the Graphics setting on iOS). */
    private val tier: GraphicsTier = GraphicsTier.HIGH,
    /** Increase Contrast: the fade starts later and stops short of the sky. */
    private val contrast: Boolean = false,
    /** Called on the GL thread each time the [MapDrawn] record changes (a few times per context). */
    private val onDrawn: ((MapDrawn) -> Unit)? = null,
    /** The ball the home view frames ([MapScene.viewRadius]): the depth fade's reach. */
    private val viewRadius: Float = tree.radius[0],
    /** The Neurons theme's cells and links ([MapTheme.NEURONS]); its notes are drawn by NeuronGl, not as points. */
    neurons: NeuronScene? = null,
    /**
     * Whether the map moves on its own (off when the system's animations are off): the Neurons'
     * states and impulses, the links' motion and their growing in (S05e).
     */
    lively: Boolean = true,
    /** The scene's build number (MapScene.build), for the record (MapDrawn.build). */
    private val build: Int = 0,
    /** Lines: Straight when the renderer is made; [straight] follows the setting from then on. */
    straight: Boolean = false,
    /**
     * The scene's links ([MapScene.links], pairs of its node indices), its notes' ids ([MapScene.ids])
     * and what changed since the scene before ([LinkMemory.recall]): which links grow in and which
     * shrink away (LinkGrowth). With none given every link is drawn whole.
     */
    links: IntArray = IntArray(0),
    ids: List<String> = emptyList(),
    recall: LinkRecall = LinkRecall.EVERYTHING,
    /**
     * Space's main look is the Universe (MapScene.universe, S05g2): a held body springs to its own
     * home with no link pulling (the orbit path); else, under one single style, its links pull its
     * neighbours (the link path).
     */
    universe: Boolean = true,
    /**
     * Space's Universe (S05h, MapScene.universePlan): its bodies move on the plan's orbits at the drag's
     * orbit clock, a held star carries its planets and moons, and its links arch by kind; null elsewhere.
     */
    universePlan: UniversePlan? = null,
) : GLSurfaceView.Renderer {
    private val linkGrowth = LinkGrowth.of(ids, links, recall, lively)
    private val neuronGl: NeuronGl? = if (theme == MapTheme.NEURONS && neurons != null)
        NeuronGl(neurons, lively, linkGrowth.takeIf { it.links == neurons.plan.links.size }
            ?: LinkGrowth(BooleanArray(neurons.plan.links.size), lively = lively)) else null
    private val neuronLively = lively
    private val lively = lively
    /** The scene's links (pairs of its body indices), for Show links' neighbourhood. */
    private val sceneLinks = links
    private val sceneCount = neurons?.plan?.bodies?.size ?: tree.nodeCount
    /** The Neurons' nesting, for the neighbourhood's members and the cells above (focusLinks); null elsewhere. */
    private val parents: IntArray? = neurons?.plan?.bodies?.let { b -> IntArray(b.size) { b[it].parent } }
        // the Universe's too (S05h): a folder's members and the folders above a body (focusLinks, GraphMotion.swift:1385-1398)
        ?: universePlan?.takeIf { theme == MapTheme.SPACE && it.bodies.size == tree.nodeCount }?.bodies?.let { b -> IntArray(b.size) { b[it].parent } }
    private val notes: BooleanArray? = neurons?.plan?.bodies?.let { b -> BooleanArray(b.size) { b[it].kind == ThemeBodyKind.NOTE } }
        ?: universePlan?.takeIf { theme == MapTheme.SPACE && it.bodies.size == tree.nodeCount }?.bodies?.let { b -> BooleanArray(b.size) { !b[it].role.isFolder } }
    /** Each scene index's node in chunk order (the inverse of Octree.order). */
    private val chunkOf = IntArray(tree.nodeCount) { -1 }.also { inv -> for (k in 0 until tree.nodeCount) tree.order[k].let { i -> if (i in inv.indices) inv[i] = k } }
    private val space = theme == MapTheme.SPACE
    /** The Universe's plan (S05h), Space only. */
    private val uplan: UniversePlan? = if (space) universePlan?.takeIf { it.bodies.size == tree.nodeCount } else null
    /**
     * The Universe's live homes, chunk order: each body where its orbit has it at the drag's orbit clock
     * (plan.position), refreshed once a frame before any pass reads it ([refreshLive]); null elsewhere.
     */
    private val live: FloatArray? = uplan?.let { tree.positions.copyOf() }
    /** The orbit clock [live] was last written at; NaN before the first. */
    private var liveTime = Double.NaN
    private val bodies: SpaceBodies? = if (space) SpaceBodies(tree, nodeRadius).also {
        if (lively && linkGrowth.links == tree.edgeCount) it.growth = SpaceLinkGrowth(tree, links, linkGrowth)
        val plan = uplan ?: return@also
        it.homes = live
        it.radii = FloatArray(tree.nodeCount) { k -> UniverseBeams.shaderRadius(plan.bodies[tree.order[k]]) }
        it.reaches = FloatArray(tree.nodeCount) { k -> UniverseBeams.reach(plan.bodies[tree.order[k]]) }
        it.looks = FloatArray(tree.nodeCount) { k -> UniverseLooks.of(plan.bodies[tree.order[k]]) }
        val kinds = HashMap<Long, Long>(plan.links.size * 2)
        for (link in plan.links) {
            val a = chunkOf.getOrElse(link.a) { -1 }; val b = chunkOf.getOrElse(link.b) { -1 }
            if (a < 0 || b < 0) continue
            kinds[UniverseBeams.key(a, b)] = UniverseBeams.pack(link.kind, if (link.centre >= 0) chunkOf.getOrElse(link.centre) { -1 } else -1)
        }
        it.linkKinds = kinds
    } else null
    /** The Universe's bodies by role, as drawn (MapDrawn.roles); empty elsewhere. */
    private val roleCounts: List<Int> = uplan?.let { p -> UniverseRole.values().map { r -> p.bodies.count { it.role == r } } } ?: emptyList()
    /**
     * Hold to move (S05g2): the spring run of a held body and what it pulls or carries, in Space by
     * chunk-order node (homes Octree.positions), in the Neurons by body (homes the plan's places at
     * the run's orbit clock); none in Performance.
     */
    private val drag: MapDrag? = when {
        neuronGl != null && neurons != null -> MapDrag(neurons.plan.bodies.size, MapDragPath.ORBITS, nodeRadius,
            { i, t -> neurons.plan.position(i, t) }, parents = parents, lively = lively)
        space && uplan != null -> {
            // parents first by the plan's order: rank[k] is node k's plan index, every body after its parent
            val chunkParents = IntArray(tree.nodeCount) { k -> uplan.bodies[tree.order[k]].parent.let { p -> if (p >= 0) chunkOf.getOrElse(p) { -1 } else -1 } }
            MapDrag(tree.nodeCount, MapDragPath.ORBITS, nodeRadius, { k, t -> uplan.position(tree.order[k], t) },
                parents = chunkParents, lively = lively, rank = tree.order.copyOf(tree.nodeCount))
        }
        space -> {
            val chunkLinks = if (universe) IntArray(0) else IntArray(links.size) { chunkOf.getOrElse(links[it]) { -1 } }
            MapDrag(tree.nodeCount, if (universe) MapDragPath.ORBITS else MapDragPath.LINKS, nodeRadius,
                { k, _ -> Vec3(tree.positions[3 * k], tree.positions[3 * k + 1], tree.positions[3 * k + 2]) },
                links = chunkLinks, lively = lively)
        }
        else -> null
    }.also { d -> bodies?.drag = d; neuronGl?.drag = d }
    /** The held body's offsets for the far layer's points and chunk links, a texel a chunk-order node (Space). */
    private var offsetTexture = 0
    private var offsetTexels = IntArray(0)
    // the offsets' floats as the texture holds them, made at the first move: the rows a frame touches go
    // up from here in one call, as the focus mask's do
    private var offsetData = FloatArray(0)
    // the latest frame's interval, ms (MapDrawn.frameMs)
    private var frameIntervalMs = 0f
    /** The held body this frame: the scene's index and, in Space, its chunk-order node; −1 for none. */
    private var grabbedScene = -1
    private var grabbedChunk = -1
    /** A strip's samples: 40 at the High tier, 20 at Smooth (iOS GraphicsQuality). */
    private val samples = if (tier == GraphicsTier.HIGH) 40 else 20
    private val spaceGl: SpaceGl? = if (space) SpaceGl(assets, samples) else null
    /** Lines: Straight, set from the page as the setting changes; the next frame draws it (Space only, as iOS's bridges ignore it). */
    @Volatile var straight: Boolean = straight
    private val palette = if (space) SpacePalette.rgb else MapPalette.rgb
    private val styleScale = if (space) SpacePalette.pointScale else FloatArray(6) { 1f }
    private val startNanos = System.nanoTime()
    private var maskTexture = 0
    private val maskHeight = if (space) max(1, (tree.nodeCount + 1023) / 1024) else 1
    /**
     * The filter (S05i), set from the page as it changes; the GL thread applies it at the next frame.
     * Its own texture, a texel a chunk-order node in every theme (255 hidden, 128 a ghost, whose links
     * are hidden), apart from Space's body mask and the offsets sized from it ([maskHeight]).
     */
    @Volatile var filter: MapFilterView? = null
    private var filterTexture = 0
    private val filterHeight = max(1, (tree.nodeCount + 1023) / 1024)
    private val filterMask = ByteArray(1024 * filterHeight)
    private var filterOn = false
    /** The filter last applied on the GL thread, and whether a new context needs it applied again. */
    private var appliedFilter: MapFilterView? = null
    private var filterStale = true
    /** Space's bodies on their filter springs, by scene index (Performance's go at once, the Neurons' are NeuronGl's). */
    private val filterPop: FilterPop? = if (space) FilterPop(tree.nodeCount, lively) else null
    private val filterLinks: FilterLinkGrowth? = if (space) FilterLinkGrowth(lively) else null
    /** Space, chunk order: each node's pop, and whether it is shown and not a ghost (its beams' ends). */
    private val chunkScale: FloatArray? = if (space) FloatArray(tree.nodeCount) { 1f } else null
    /** Space, chunk order: each node's bloom as it comes back (FilterPop.bloom), the body shader's vMark.w. */
    private val chunkBloom: FloatArray? = if (space) FloatArray(tree.nodeCount) else null
    /** A filter was applied: the springs' sizes are written again on the next frame, moving or not. */
    private var filterFillDue = true
    private var chunkWanted = BooleanArray(0)
    /** The latest frame's filter counts (MapDrawn): bodies shown and hidden, links shown. */
    private var shownNow = 0
    private var hiddenNow = 0
    private var filterLinksNow = 0
    /** One byte per node (chunk order): 255 while the node is drawn as its body. */
    private val mask = ByteArray(1024 * maskHeight)
    private val maskTexel: ByteBuffer = ByteBuffer.allocateDirect(1)
    /** The vsync time (Choreographer) of the frame being drawn; 0 when unknown. */
    @Volatile var vsyncNanos = 0L
    /** Camera velocity (world units a second), smoothed; read by the guard band. */
    private var velocity = Vec3.ZERO
    private var lastEye: Vec3? = null
    private var lastTarget: Vec3? = null

    val budget = FrameBudget(displayHz())
    private val stats = FrameStats()
    private val pageSize = 8192
    private fun newCache() = ChunkCache(
        pageCount = max(256, (tree.leafEdges.size.coerceAtMost(2_000_000) + pageSize - 1) / pageSize + 8)
            .coerceAtLeast((0 until tree.leafCount).maxOfOrNull { (tree.leafEdgeIndexCount(it) + pageSize - 1) / pageSize * 3 } ?: 1),
        pageSize = pageSize,
    ) { tree.leafEdgeIndexCount(it) }
    // A new GL context starts with an empty link pool, so both are made again in onSurfaceCreated.
    private var cache = newCache()
    private var lod = LodSelector(tree, cache)

    @Volatile var animating = true; private set
    /** Frames finished (their draw calls issued) on every context so far, for the frame loop's quiet rule (FrameQuiet). */
    @Volatile var finished = 0; private set
    private var frame = 0
    private var lastVsync = 0L
    private var lastReadout = 0L
    private var width = 1; private var height = 1
    private var maxPointSize = 64f
    /** This context's record of finished frames (MapDrawn), the one last handed out, and when the surface started. */
    private var record = MapDrawn()
    private var recordPublished: MapDrawn? = null
    private var surfaceNanos = System.nanoTime()

    private var nodeProgram = 0; private var edgeProgram = 0; private var summaryProgram = 0; private var bundleProgram = 0
    private var nodeVao = 0; private var edgeVao = 0; private var summaryVao = 0; private var bundleVao = 0
    private var nodeVbo = 0; private var edgeIbo = 0; private var summaryVbo = 0; private var bundleVbo = 0; private var cornerVbo = 0
    private var leafTexture = 0
    private val leafTexWidth = 1024
    private var leafTexHeight = 1
    private lateinit var leafStateBuffer: FloatBuffer
    private var summaryBuffer: FloatBuffer = floats(7 * 1024)
    private var bundleBuffer: FloatBuffer = floats(10 * 1024)
    private val center = FloatArray(3)
    private val half = FloatArray(3)
    private var viewProj = Mat4.identity()
    private var clock = 0f
    /** The Space theme's depth fade this frame (off in the plain theme). */
    @Volatile var band: SpaceDepthBand = SpaceDepthBand.OFF; private set
    private var bandUniform = band.uniform

    /**
     * The chosen body and its cue (S05g), set from the page in one write, so no frame sees a new
     * choice without its cue; the next frame draws it. A new renderer takes the cue count it is
     * made with as seen.
     */
    @Volatile var choice: MapChoice = MapChoice(-1, 0)
    /** Show links: only the chosen body's links, the rest faded (focusLinks). */
    @Volatile var linksShown: Boolean = false
    private var motion: MapChoiceMotion? = null
    private val pop get() = motion?.pop ?: restingPop
    private val restingPop = MapPop(false)
    private val orbitGrow get() = motion?.orbitGrow ?: 1f
    private var focusedChosen = -1
    private var focusedShown = false
    /** The chosen node in chunk order this frame, −1 for none; and the neighbourhood, chunk order, sorted, under Show links. */
    private var chosenChunk = -1
    private var focusChunks: IntArray? = null
    /** The chosen body's own chunk links, by leaf: how many of each leaf's link pairs are its (a link between two leaves is in both). */
    private val ownByLeaf = HashMap<Int, Int>()
    private var focusTexture = 0
    /** One byte per node (chunk order): 255 inside the chosen body's neighbourhood under Show links. */
    private val focusMask = ByteArray(1024 * maskHeight)
    private var focusTexels = IntArray(0)

    /**
     * Where body [index] (the scene's) is: its place in the octree, or a Neurons body where it has
     * drifted to now; null for no such body. For the camera's ease (MapCameraEase).
     */
    fun bodyAt(index: Int): Vec3? {
        if (neuronGl != null) {
            if (index !in 0 until neuronGl.bodyCount) return null
            return neuronGl.position(index, neuronTime())
        }
        val k = chunkOf.getOrElse(index) { -1 }
        if (k < 0) return null
        return Vec3(nodeAt(k, 0), nodeAt(k, 1), nodeAt(k, 2))
    }

    /** Chunk-order node [k]'s place along [axis]: its home plus its own offset from a drag (S05g2). */
    private fun nodeAt(k: Int, axis: Int): Float = (live ?: tree.positions)[3 * k + axis] + (drag?.offset(k, axis) ?: 0f)

    /**
     * The Universe's live homes at the drag's orbit clock (S05h): every body where its orbit has it now,
     * so every pass of the frame (near bodies, beams, points, chunk links, the ring, names, picks) reads
     * one place. Whether they moved since the last frame.
     */
    private fun refreshLive(): Boolean {
        val plan = uplan ?: return false
        val l = live ?: return false
        val t = drag?.orbitTime ?: 0.0
        if (t == liveTime) return false
        liveTime = t
        for (k in 0 until tree.nodeCount) {
            val p = plan.position(tree.order[k], t)
            l[3 * k] = p.x; l[3 * k + 1] = p.y; l[3 * k + 2] = p.z
        }
        return true
    }

    /** The Neurons' time: the spring run's orbit clock, which eases to a stop while a body is held (0 with animations off). */
    private fun neuronTime(): Double = drag?.orbitTime ?: if (neuronLively) (System.nanoTime() - startNanos) / 1e9 else 0.0

    /** Whether a held body can be moved here: Space and the Neurons, not Performance (S05g2). */
    val drags: Boolean get() = drag != null

    /** The body held, the scene's index, −1 for none. */
    val grabbed: Int get() {
        val d = drag ?: return -1
        val g = d.grabbed
        if (g < 0) return -1
        return if (neuronGl != null) g else tree.order[g]
    }

    /** Fly in under way (S05h), stepped on the GL thread. */
    @Volatile private var flight: CameraFlight? = null

    /** The folder body flown in to (the scene's index), −1 for none (MapDrawn.flown). */
    @Volatile var flown: Int = -1; private set

    /** Whether body [index] can be flown in to: a Universe folder or the home, with a system to frame. */
    fun canFlyTo(index: Int): Boolean {
        val plan = uplan ?: return false
        val body = plan.bodies.getOrNull(index) ?: return false
        return body.role.isFolder && plan.systems.getOrNull(index)?.isNotEmpty() == true
    }

    /**
     * Flies the camera in to Universe folder body [index] (the scene's; iOS's fly(to:)): centred on its
     * home, its system framed in the window [insets] leave, over 0.8 s, at once with animations off.
     * False where it cannot fly there.
     */
    fun flyTo(index: Int, viewWidth: Float, viewHeight: Float, insets: MapInsets): Boolean {
        neuronGl?.let { gl -> return flyToCell(gl, index, viewWidth, viewHeight, insets) }
        val plan = uplan ?: return false
        if (!canFlyTo(index)) return false
        val centre = plan.bodies[index].home
        val points = plan.systems[index].map { centre + it }
        val fit = synchronized(camera) {
            val f = MapFly.fit(camera, centre, points, viewWidth, viewHeight, insets) ?: return false
            flight = CameraFlight(camera.target, camera.distance, f, MapFly.seconds(lively))
            f
        }
        flown = index
        return fit.distance > 0f
    }

    /**
     * The Neurons' filter (S05i; iOS flies to the folder's slot where `universe` is set, which the
     * Neurons are, GraphThemeScene.swift:300): the camera flies to cell [index] (the scene's), framing
     * it and everything inside it, without opening it.
     */
    private fun flyToCell(gl: NeuronGl, index: Int, viewWidth: Float, viewHeight: Float, insets: MapInsets): Boolean {
        if (index !in 0 until gl.bodyCount) return false
        val p = parents ?: return false
        val time = neuronTime()
        val centre = gl.position(index, time)
        val points = ArrayList<Vec3>()
        for (i in 0 until gl.bodyCount) {
            var up = i; var steps = 0
            while (up >= 0 && up != index && steps <= gl.bodyCount) { up = p[up]; steps++ }
            if (up != index) continue
            val at = gl.position(i, time); val r = gl.size(i)
            points.add(at + Vec3(r, 0f, 0f)); points.add(at - Vec3(r, 0f, 0f))
            points.add(at + Vec3(0f, r, 0f)); points.add(at - Vec3(0f, r, 0f))
        }
        val fit = synchronized(camera) {
            val f = MapFly.fit(camera, centre, points, viewWidth, viewHeight, insets) ?: return false
            flight = CameraFlight(camera.target, camera.distance, f, MapFly.seconds(lively))
            f
        }
        flown = index
        return fit.distance > 0f
    }

    /** Recentre (or a rebuild): no folder is flown in to, and a flight under way stops. */
    fun leaveFlight() { flight = null; flown = -1 }

    /** A finger down on body [index] (the scene's), −1 for none: the orbit clock eases to a stop meanwhile (GraphMotion.swift:1752). */
    fun press(index: Int) { drag?.pressed = sceneToDrag(index) }

    /** A mouse or stylus over body [index] (the scene's) with no button down, −1 for none; as [press]. */
    fun hover(index: Int) { drag?.hovered = sceneToDrag(index) }

    private fun sceneToDrag(index: Int): Int = when {
        index < 0 -> -1
        neuronGl != null -> index
        else -> chunkOf.getOrElse(index) { -1 }
    }

    /** Picks body [index] (the scene's) up where it is (MapDrag.grab); false where it cannot be. */
    fun grab(index: Int): Boolean {
        val d = drag ?: return false
        val k = if (neuronGl != null) index.takeIf { it in 0 until d.count } ?: -1 else chunkOf.getOrElse(index) { -1 }
        if (k < 0) return false
        d.grab(k)
        return true
    }

    /** Where the finger wants the held body, in the world (MapDrag.drag). */
    fun dragTo(target: Vec3) { drag?.drag(target) }

    /** Lets the held body go (MapDrag.release): with the finger's speed, or at rest when [still]. */
    fun release(still: Boolean = false) { drag?.release(still) }

    init { require(tree.leafCount < 65_536) { "chunk ids are 16-bit" } }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        surfaceNanos = System.nanoTime()
        nodeProgram = program(NODE_VS, NODE_FS)
        edgeProgram = program(EDGE_VS, EDGE_FS)
        summaryProgram = program(SUMMARY_VS, SUMMARY_FS)
        bundleProgram = program(BUNDLE_VS, BUNDLE_FS)
        val range = FloatArray(2)
        GLES30.glGetFloatv(GLES30.GL_ALIASED_POINT_SIZE_RANGE, range, 0)
        maxPointSize = max(range[1], 1f)
        uploadNodes()
        uploadLeafTexture()
        uploadMask()
        uploadFilterMask()
        filterStale = true
        uploadFocusMask()
        uploadOffsets()
        val ids = IntArray(4)
        GLES30.glGenBuffers(4, ids, 0)
        edgeIbo = ids[0]; summaryVbo = ids[1]; bundleVbo = ids[2]; cornerVbo = ids[3]
        // the edge pool: pages of link indices, filled as chunks stream in
        GLES30.glGenVertexArrays(1, ids, 0); edgeVao = ids[0]
        GLES30.glBindVertexArray(edgeVao)
        bindNodeAttributes()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, edgeIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, cache.pageCount * pageSize * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindVertexArray(0)
        // summaries: x, y, z, diameter, r, g, b
        GLES30.glGenVertexArrays(1, ids, 0); summaryVao = ids[0]
        GLES30.glBindVertexArray(summaryVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, summaryVbo)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 28, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 28, 16)
        GLES30.glBindVertexArray(0)
        // bundles: one instanced quad each
        GLES30.glGenVertexArrays(1, ids, 0); bundleVao = ids[0]
        GLES30.glBindVertexArray(bundleVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cornerVbo)
        val corners = floatArrayOf(0f, -1f, 1f, -1f, 1f, 1f, 0f, -1f, 1f, 1f, 0f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, floats(corners.size).put(corners).flip(), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bundleVbo)
        for ((loc, comps, off) in listOf(Triple(1, 3, 0), Triple(2, 3, 12), Triple(3, 1, 24), Triple(4, 3, 28))) {
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, comps, GLES30.GL_FLOAT, false, 40, off)
            GLES30.glVertexAttribDivisor(loc, 1)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        // a new context has an empty pool: everything streams again
        cache = newCache()
        lod = LodSelector(tree, cache)
        if (bodies != null) spaceGl?.create(bodies, budget.detail)
        neuronGl?.create()
        // a new context: its frames are counted afresh
        record = MapDrawn(renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "", maxPointSize = maxPointSize,
            bodyStyles = bodies?.enabled?.count { it } ?: 0, build = build)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(w, 1); height = max(h, 1)
        GLES30.glViewport(0, 0, width, height)
        record = record.copy(width = width, height = height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val start = System.nanoTime()
        val vsync = vsyncNanos.takeIf { it > 0 } ?: start
        val intervalMs = if (lastVsync == 0L) 1000.0 / displayHz() else (vsync - lastVsync).coerceAtLeast(0L) / 1e6
        val dt = (intervalMs / 1000.0).toFloat().coerceIn(0f, 0.25f)
        lastVsync = vsync
        frameIntervalMs = intervalMs.toFloat()
        frame++
        budget.refreshHz = displayHz()

        val eye: Vec3; val target: Vec3; val view: FloatArray; val proj: FloatArray; val focal: Float; val forward: Vec3
        val aspect = width.toFloat() / height
        synchronized(camera) {
            eye = camera.eye(); target = camera.target
            view = camera.view(); proj = camera.projection(aspect); focal = camera.focalPixels(height.toFloat())
            forward = camera.forward()
        }
        drag?.step(dt)
        val orbiting = refreshLive()
        stepChoice(dt)
        val filterMoving = stepFilter(dt)
        flight?.let { f -> synchronized(camera) { f.step(camera, dt) }; if (f.finished) flight = null }
        // the depth fade follows what the camera looks at: its orbit target
        val focusDepth = SpaceDepth.depth(target, eye, forward)
        if (neuronGl != null) {
            drawNeurons(neuronGl, start, vsync, intervalMs, eye, view, proj, focal, focusDepth)
            return
        }
        band = if (space) SpaceDepth.band(focusDepth, SpaceDepth.reach(focusDepth, viewRadius), tier, contrast) else SpaceDepthBand.OFF
        bandUniform = band.uniform
        val moved = lastEye?.let { (eye - it).length() + (target - (lastTarget ?: target)).length() } ?: 0f
        if (dt > 0f) {
            val instant = lastEye?.let { (eye - it) * (1f / dt) } ?: Vec3.ZERO
            val k = 1f - exp(-dt * 8f)
            velocity = velocity + (instant - velocity) * k
        }
        lastEye = eye; lastTarget = target
        viewProj = Mat4.multiply(proj, view)
        val guardCamera = synchronized(camera) { OrbitCamera(camera.target, camera.distance, camera.yaw, camera.pitch, camera.fovY) }
        val guard = GuardBand.predict(guardCamera, aspect, velocity, lookaheadSeconds = 0.35f)
        lod.focus = if (space) focusChunks else null
        val plan = lod.select(eye, focal, guard.view, guard, budget.openPixels, budget.maxDetailNodes, dt, frame)
        cache.pump(frame, budget.uploadBudget) { chunk, pages -> uploadChunk(chunk, pages) }
        // the shaders' clock, wrapped so float time stays fine-grained (every period is whole there)
        clock = (((System.nanoTime() - startNanos) / 1e9) % SpaceOptics.CLOCK_PERIOD).toFloat()
        bodies?.growth?.tick(dt)
        val bodyCount = bodies?.let { b ->
            val right = Vec3(view[0], view[4], view[8]); val up = Vec3(view[1], view[5], view[9])
            b.update(plan, viewProj, eye, right, up, focal, SpaceLod.maxBodies(budget.detail), clock, band).also { applyMask(b) }
        } ?: 0
        // the marked body's orbit ring (the held body, else the chosen one), where its node is placed this frame (Space)
        var ring: FloatArray? = null
        var ringDepth = 0f
        val markChunk = if (grabbedChunk >= 0) grabbedChunk else chosenChunk
        if (space && markChunk >= 0) {
            val leaf = tree.leafOfNode[markChunk]
            val t = plan.leafState[leaf * 4 + 3]
            if (t > 0f) {
                val s = leaf * 4
                val p = Vec3(plan.leafState[s] + (nodeAt(markChunk, 0) - plan.leafState[s]) * t,
                    plan.leafState[s + 1] + (nodeAt(markChunk, 1) - plan.leafState[s + 1]) * t,
                    plan.leafState[s + 2] + (nodeAt(markChunk, 2) - plan.leafState[s + 2]) * t)
                ringDepth = (p - eye).dot(forward)
                if (ringDepth > 0f && !band.gone(ringDepth)) ring = MapMark.orbitModel(p.x, p.y, p.z, MapMark.orbitSize(nodeRadius, orbitGrow, pop.scale))
            }
        }
        animating = bodyCount > 0 || (bodies?.beamCount ?: 0) > 0 || moved > 1e-5f || plan.detailLeaves.size.let { n -> (0 until n).any { plan.leafState[plan.detailLeaves[it] * 4 + 3] < 1f } } || cache.uploadedThisFrame > 0
            || pop.moving || (markChunk >= 0 && orbitGrow < 1f) || (ring != null && lively) || drag?.busy == true
            // the Universe's orbits turn whenever animations run (the packet's question 1: as iOS)
            || orbiting || (uplan != null && lively) || flight != null || filterMoving

        if (space) GLES30.glClearColor(0.004f, 0.006f, 0.016f, 1f) else GLES30.glClearColor(0.055f, 0.106f, 0.173f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        uploadLeafState(plan)
        writeOffsets(orbiting)
        val focusOn = focusChunks != null && chosenChunk >= 0
        // Show links: Space draws no bundle (none carries the chosen body's link); Performance draws them at 0.15
        if (!(space && focusOn)) drawBundles(plan, if (focusOn) MapMark.FADED else 1f)
        drawEdges(plan, eye, focal, if (!focusOn || chosenChunk < 0) 0f else if (space) 1f else if (theme == MapTheme.PLAIN) 2f else 0f)
        drawSummaries(plan)
        val drawn = drawNodes(plan, eye, focal)
        val straightNow = straight
        var linksNow = 0
        var ringNow = false
        if (bodies != null && spaceGl != null && (bodyCount + bodies.beamCount > 0 || ring != null)) {
            spaceGl.draw(bodies, viewProj, floatArrayOf(eye.x, eye.y, eye.z), floatArrayOf(view[0], view[4], view[8]),
                floatArrayOf(view[1], view[5], view[9]), focal, clock, SpaceLod.shaderDetail(budget.detail),
                bandUniform, focal / max(density, 0.1f), floatArrayOf(forward.x, forward.y, forward.z),
                straightNow, lively, contrast, ring, ringDepth, pop.glow)
            linksNow = spaceGl.linksDrawn
            ringNow = spaceGl.ringDrawn
        }
        publishScreenNodes(plan)

        val workMs = (System.nanoTime() - start) / 1e6
        budget.onFrame(intervalMs, workMs)
        stats.add(vsync, intervalMs, workMs)
        if (vsync - lastReadout > 250_000_000L) {
            lastReadout = vsync
            onReadout(MapReadout(
                fps = stats.fps(vsync), displayMaxHz = displayMaxHz, displayHz = displayHz(),
                frameMs = stats.medianInterval(), workMs = stats.medianWork(),
                nodes = tree.nodeCount, links = tree.edgeCount, drawnNodes = drawn, chunks = plan.detailLeaves.size,
                summaries = plan.summaryCount, bundles = plan.bundleCount, resident = cache.residentCount, detail = budget.detail,
                bodies = bodyCount,
            ))
        }
        record = record.after(bodyCount, (System.nanoTime() - surfaceNanos) / 1_000_000, linksNow,
            if (space) samples else 0, space && straightNow)
        record = record.detailing(budget.detail, plan.detailLeaves.size, openLeaves(plan), plan.summaryCount)
        if (uplan != null && bodies != null) {
            record = record.flying(flown)
            val d = drag
            if (lively && d != null) record = record.stepped().pressing(d.pressedTime.toFloat(), d.pressSlowedAfter.toFloat(),
                d.pressTaken) { SystemClock.uptimeMillis() }
            record = record.universe(roleCounts, bodies.nearBeams + bodies.archedBeams, if (straightNow) 0 else bodies.archedBeams + bodies.farBeams,
                bodies.farBeams, (d?.orbitTime ?: 0.0).toInt(), Math.round((d?.orbitRate ?: 0f) * 100) / 100f)
        }
        // the chosen body's marks, and Show links' counts (S05g)
        val ownChunkLinks = if (focusOn) ownLinksDrawn(plan) else 0
        val marked = if (space) ringNow else chosenChunk >= 0 && chosenDrawn(plan)
        val faded = if (space && focusOn) fadedPoints(plan) + plan.fadedSummaries else 0
        record = record.marking(markedChosen(), marked, bodies?.litCount ?: 0, focusOn,
            if (!focusOn) 0 else linksNow + ownChunkLinks, faded)
        record = record.filtering(appliedFilter != null, shownNow, hiddenNow, filterLinksNow, emptyList())
        record = dragRecord(if (grabbedChunk >= 0) Vec3(nodeAt(grabbedChunk, 0), nodeAt(grabbedChunk, 1), nodeAt(grabbedChunk, 2)) else null,
            grabbedChunk, chosenChunk)
        if (record != recordPublished) { recordPublished = record; onDrawn?.invoke(record) }
        finished++
    }

    /** The chosen body as drawn: its scene index, −1 for none. */
    private fun markedChosen(): Int = focusedChosen

    /**
     * Each frame: a new cue kicks the pop and grows the orbit ring in from 1.35 (at once with
     * animations off), a new choice or Show links works out the neighbourhood again, and the pop and
     * the ring step [dt] seconds (MapChoiceMotion). A scene built again keeps the choice at rest.
     */
    private fun stepChoice(dt: Float) {
        val now = choice
        val wanted = now.chosen.takeIf { it in 0 until sceneCount } ?: -1
        val show = linksShown
        val cued = now.cues
        val m = motion ?: MapChoiceMotion(lively, lively && theme != MapTheme.PLAIN, cued).also { motion = it }
        // a held body is marked and popped in the chosen body's place, once per pick-up (grab, GraphMotion.swift:1246-1259)
        val held = drag?.grabbed ?: -1
        grabbedChunk = if (neuronGl == null) held else -1
        grabbedScene = if (held < 0) -1 else if (neuronGl != null) held else tree.order[held]
        m.step(if (grabbedScene >= 0) grabbedScene else wanted, cued + (drag?.grabs ?: 0), dt)
        if (wanted != focusedChosen || show != focusedShown) refocus(wanted, show)
        bodies?.grabbed = grabbedChunk
        neuronGl?.grabbed = grabbedScene
        bodies?.chosenScale = pop.scale
        neuronGl?.let { it.chosenScale = pop.scale; it.chosenGlow = pop.glow; it.chosenSwell = pop.swell }
    }

    /** The chosen body and Show links changed: who is marked, the neighbourhood, its mask texels, and the chosen body's own chunk links by leaf. */
    private fun refocus(wanted: Int, show: Boolean) {
        focusedChosen = wanted; focusedShown = show
        val near = if (show && wanted >= 0) MapFocus.neighbourhood(wanted, sceneCount, sceneLinks, parents, notes) else null
        neuronGl?.let { it.chosen = wanted; it.focus = near }
        chosenChunk = if (neuronGl != null) -1 else chunkOf.getOrElse(wanted) { -1 }
        val chunks = if (neuronGl != null || near == null) null
            else near.map { chunkOf.getOrElse(it) { -1 } }.filter { it >= 0 }.sorted().toIntArray()
        focusChunks = chunks
        bodies?.chosen = chosenChunk
        bodies?.focus = if (space) chunks else null
        ownByLeaf.clear()
        if (chosenChunk >= 0) {
            val home = tree.leafOfNode[chosenChunk]
            var e = tree.leafEdgeStart[home]
            val end = tree.leafEdgeStart[home + 1]
            while (e + 1 < end) {
                val u = tree.leafEdges[e]; val v = tree.leafEdges[e + 1]
                e += 2
                if (!MapFocus.own(chosenChunk, u, v)) continue
                ownByLeaf[home] = (ownByLeaf[home] ?: 0) + 1
                val other = tree.leafOfNode[if (u == chosenChunk) v else u]
                if (other != home) ownByLeaf[other] = (ownByLeaf[other] ?: 0) + 1
            }
        }
        if (space) writeFocusMask(if (chunks != null) chunks else IntArray(0))
    }

    /** The chosen body's own links in the chunks drawn this frame (their pages streamed in). */
    /** The chunks this frame drew fully open (MapDrawn.openChunks): where a Space body may be drawn. */
    private fun openLeaves(plan: FramePlan): Int {
        var open = 0
        for (k in 0 until plan.detailLeaves.size) if (plan.leafState[plan.detailLeaves[k] * 4 + 3] >= SpaceBodies.OPEN) open++
        return open
    }

    private fun ownLinksDrawn(plan: FramePlan): Int {
        if (ownByLeaf.isEmpty()) return 0
        var n = 0
        for (k in 0 until plan.detailLeaves.size) {
            val leaf = plan.detailLeaves[k]
            val own = ownByLeaf[leaf] ?: continue
            if (cache.pages(leaf) != null) n += own
        }
        return n
    }

    /** Whether the chosen node's chunk is drawn node by node this frame (Performance's ring). */
    private fun chosenDrawn(plan: FramePlan): Boolean {
        val leaf = tree.leafOfNode[chosenChunk]
        if (plan.leafState[leaf * 4 + 3] <= 0f) return false
        for (k in 0 until plan.detailLeaves.size) if (plan.detailLeaves[k] == leaf) return true
        return false
    }

    /** Under Show links in Space: the nodes in the chunks drawn node by node outside the neighbourhood (points and bodies alike). */
    private fun fadedPoints(plan: FramePlan): Int {
        val near = focusChunks ?: return 0
        var n = 0
        for (k in 0 until plan.detailLeaves.size) {
            val cell = tree.leafCell[plan.detailLeaves[k]]
            n += tree.count[cell] - MapFocus.countIn(near, tree.start[cell], tree.end[cell])
        }
        return n
    }

    /**
     * The Neurons theme's frame: no octree points, links or summaries, only NeuronGl's cells and
     * links over the fluid, faded by the Neurons band (NeuronDepth), always night.
     */
    private fun drawNeurons(gl: NeuronGl, start: Long, vsync: Long, intervalMs: Double, eye: Vec3, view: FloatArray,
                            proj: FloatArray, focal: Float, focusDepth: Float) {
        band = NeuronDepth.band(focusDepth, SpaceDepth.reach(focusDepth, viewRadius), tier, contrast)
        bandUniform = band.uniform
        viewProj = Mat4.multiply(proj, view)
        val seconds = (System.nanoTime() - startNanos) / 1e9
        clock = (seconds % SpaceOptics.CLOCK_PERIOD).toFloat()
        val moved = lastEye?.let { (eye - it).length() } ?: 0f
        lastEye = eye
        animating = neuronLively || moved > 1e-5f || pop.moving || drag?.busy == true || gl.popping || flight != null
        val time = neuronTime()
        val deep = NeuronPalette.deep
        GLES30.glClearColor(deep.x, deep.y, deep.z, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        gl.draw(view, proj, eye, clock, time, bandUniform)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        val count = gl.bodyCount
        onScreenNodes?.let { callback ->
            val out = ArrayList<ScreenNode>(count)
            val f = appliedFilter
            for (i in 0 until count) {
                if (f != null && !f.reachable(i)) continue
                val p = gl.position(i, time)
                Mat4.project(viewProj, p.x, p.y, p.z, clip)
                if (clip[3] <= 1e-4f || band.gone(clip[3])) continue
                val sx = (clip[0] / clip[3] * 0.5f + 0.5f) * width
                val sy = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height
                out.add(ScreenNode(i, sx, sy, gl.size(i) * focal / clip[3] * 2f, band.crisp(clip[3]) || band == SpaceDepthBand.OFF, focal / clip[3]))
            }
            callback(out)
        }
        val workMs = (System.nanoTime() - start) / 1e6
        budget.onFrame(intervalMs, workMs)
        stats.add(vsync, intervalMs, workMs)
        if (vsync - lastReadout > 250_000_000L) {
            lastReadout = vsync
            onReadout(MapReadout(fps = stats.fps(vsync), displayMaxHz = displayMaxHz, displayHz = displayHz(),
                frameMs = stats.medianInterval(), workMs = stats.medianWork(), nodes = count, links = gl.linkCount,
                drawnNodes = count, detail = budget.detail, bodies = count))
        }
        // the bridges ignore Lines, as iOS's (fillBridge returns before it reads straight)
        record = record.after(count, (System.nanoTime() - surfaceNanos) / 1_000_000, gl.linksDrawn, gl.samples, false)
        record = record.detailing(budget.detail, 0, 0, 0)
        val focusOn = gl.focus != null
        record = record.marking(focusedChosen, gl.hotDrawn, gl.litDrawn, focusOn, if (focusOn) gl.linksDrawn else 0, if (focusOn) gl.fadedDrawn else 0)
        record = record.filtering(appliedFilter != null, count - gl.hiddenDrawn, gl.hiddenDrawn, filterLinksNow, gl.statesDrawn)
        record = dragRecord(if (grabbedScene >= 0) gl.position(grabbedScene, time) else null, grabbedScene, record.chosen)
        if (record != recordPublished) { recordPublished = record; onDrawn?.invoke(record) }
        finished++
    }

    // ---- uploads ----

    private fun uploadNodes() {
        val n = tree.nodeCount
        val p = tree.positions
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, p[3 * i]); maxX = max(maxX, p[3 * i]); minY = min(minY, p[3 * i + 1]); maxY = max(maxY, p[3 * i + 1])
            minZ = min(minZ, p[3 * i + 2]); maxZ = max(maxZ, p[3 * i + 2])
        }
        center[0] = (minX + maxX) / 2; center[1] = (minY + maxY) / 2; center[2] = (minZ + maxZ) / 2
        half[0] = max((maxX - minX) / 2, 1e-3f); half[1] = max((maxY - minY) / 2, 1e-3f); half[2] = max((maxZ - minZ) / 2, 1e-3f)
        val buffer = ByteBuffer.allocateDirect(max(n, 1) * NODE_STRIDE).order(ByteOrder.nativeOrder())
        for (i in 0 until n) {
            for (a in 0 until 3) buffer.putShort(((p[3 * i + a] - center[a]) / half[a] * 32767f).toInt().coerceIn(-32767, 32767).toShort())
            buffer.putShort(tree.leafOfNode[i].toShort())
            buffer.put(tree.groups[i]); buffer.put(0); buffer.put(0); buffer.put(0)
        }
        buffer.flip()
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0); nodeVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, nodeVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buffer.capacity(), buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glGenVertexArrays(1, ids, 0); nodeVao = ids[0]
        GLES30.glBindVertexArray(nodeVao)
        bindNodeAttributes()
        GLES30.glBindVertexArray(0)
    }

    private fun bindNodeAttributes() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, nodeVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_SHORT, true, NODE_STRIDE, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribIPointer(1, 1, GLES30.GL_UNSIGNED_SHORT, NODE_STRIDE, 6)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribIPointer(2, 1, GLES30.GL_UNSIGNED_BYTE, NODE_STRIDE, 8)
    }

    private fun uploadLeafTexture() {
        leafTexHeight = max(1, (tree.leafCount + leafTexWidth - 1) / leafTexWidth)
        leafStateBuffer = floats(leafTexWidth * leafTexHeight * 4)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); leafTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leafTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA32F, leafTexWidth, leafTexHeight, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, null)
    }

    private fun uploadMask() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); maskTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 1024, maskHeight, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(mask))
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    private fun uploadFilterMask() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); filterTexture = ids[0]
        writeFilterMask()
    }

    private fun writeFilterMask() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 1024, filterHeight, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(filterMask))
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    /**
     * The filter on the GL thread (S05i): a new one is applied once (iOS apply), and each frame Space's
     * springs and its beams' drawing back step [dt] seconds. Performance's points and links go and come
     * back at once; the Neurons' bodies are NeuronGl's. Returns whether anything still moves.
     */
    private fun stepFilter(dt: Float): Boolean {
        val f = filter
        if (f !== appliedFilter || filterStale) applyFilter(f)
        var moving = false
        val pop = filterPop
        val b = bodies
        if (pop != null && b != null && chunkScale != null && chunkBloom != null) {
            // the springs' sizes and blooms, written again only on a frame they change (none once settled)
            val ran = pop.moving
            pop.step(dt)
            if (ran || filterFillDue) {
                var blooming = false
                for (k in 0 until tree.nodeCount) {
                    val i = tree.order[k]
                    chunkScale[k] = if (pop.gone(i)) 0f else pop.scale(i)
                    chunkBloom[k] = pop.bloom(i)
                    if (chunkBloom[k] > 0f) blooming = true
                }
                filterFillDue = false
                val whole = f == null && !pop.moving
                b.filterScale = if (whole) null else chunkScale
                b.filterBloom = if (whole || !blooming) null else chunkBloom
            }
            moving = pop.moving
            val links = filterLinks
            if (links != null) {
                moving = links.step(dt, linkWanted) || moving
                if (f == null && links.idle) b.filterWanted = null
            }
        }
        return moving
    }

    /** A beam's ends both wanted under the filter, or every end with none (chunk order, by its pair key). */
    private val linkWanted = LinkWant { key ->
        val wanted = chunkWanted
        val u = (key ushr 32).toInt(); val v = key.toInt()
        wanted.isEmpty() || (u < wanted.size && v < wanted.size && wanted[u] && wanted[v])
    }

    private fun applyFilter(f: MapFilterView?) {
        appliedFilter = f
        filterStale = false
        val n = tree.nodeCount
        val shownScene = f?.shown
        neuronGl?.let { gl ->
            gl.filter = f
            shownNow = 0; hiddenNow = 0
            filterLinksNow = f?.links?.count { it } ?: gl.linkCount
        }
        if (neuronGl != null) return
        // far points and chunk links, every theme: hidden, a ghost (its links hidden), or shown
        filterOn = f != null
        val wanted = BooleanArray(if (f == null) 0 else n)
        var shown = 0
        for (k in 0 until n) {
            val i = tree.order[k]
            val code: Byte = when {
                f == null || i !in f.shown.indices -> 0
                !f.shown[i] -> -1
                f.ghost[i] -> 0x80.toByte()
                else -> 0
            }
            filterMask[k] = code
            if (f != null && i in f.shown.indices && f.shown[i] && !f.ghost[i]) wanted[k] = true
            if (f == null || (i in f.shown.indices && f.shown[i])) shown++
        }
        shownNow = shown; hiddenNow = n - shown
        filterLinksNow = f?.links?.count { it } ?: (sceneLinks.size / 2)
        if (filterTexture != 0) writeFilterMask()
        // summaries and bundles count only what shows
        lod.filter = if (f == null) null else FilterSlice.of(tree, wanted)
        // Space: the bodies' springs, ghosts and beams; at once in Performance and with motion off
        chunkWanted = wanted
        bodies?.let { b ->
            filterPop?.apply(shownScene, f?.instant ?: false)
            filterFillDue = true
            b.filterGhost = if (f == null) null else BooleanArray(n) { k -> tree.order[k].let { i -> i in f.ghost.indices && f.ghost[i] } }
            // Everything again: links drawn back grow out once more (every end wanted) before the filter lets go
            b.filterWanted = if (f != null) wanted else if (filterLinks?.idle == false) BooleanArray(n) { true } else null
            b.filterLinks = if (f?.instant == true) null else filterLinks
        }
    }

    private fun uploadFocusMask() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); focusTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, focusTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 1024, maskHeight, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(focusMask))
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    /**
     * The record's held body (S05g2): which it is, its centre on screen at [at] (whole pixels), and
     * the bodies off home.
     */
    private fun dragRecord(at: Vec3?, held: Int, chosen: Int): MapDrawn {
        val d = drag ?: return record
        // a body's own offset in the drag's numbering (chunks in Space, bodies in the Neurons); empty at home
        fun own(i: Int): List<Float> {
            if (i !in 0 until d.count) return emptyList()
            val o = d.offsetOf(i)
            return if (o.x == 0f && o.y == 0f && o.z == 0f) emptyList() else listOf(o.x, o.y, o.z)
        }
        var spot = emptyList<Float>()
        if (at != null) {
            Mat4.project(viewProj, at.x, at.y, at.z, clip)
            if (clip[3] > 1e-4f) spot = listOf(
                Math.round((clip[0] / clip[3] * 0.5f + 0.5f) * width).toFloat(),
                Math.round((1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height).toFloat())
        }
        // the frame's interval only while something moves, so a still map's record stays the same
        val ms = if (d.busy) Math.round(frameIntervalMs * 10).toFloat() / 10 else record.frameMs
        // the Universe's held body's members, by scene index, each with its own offset (S05h)
        val members = if (uplan == null || !space || held < 0) emptyList() else ArrayList<Float>().also { out ->
            for (k in 0 until d.count) if (d.parentOf(k) == held) {
                val o = d.offsetOf(k)
                out.add(tree.order[k].toFloat()); out.add(o.x); out.add(o.y); out.add(o.z)
            }
        }
        return record.dragging(grabbedScene, spot, d.moving, d.carried, own(held), own(chosen), d.letGoFrames, d.restFrames, ms, members)
    }

    /** The far layer's offsets (S05g2): a float texel a chunk-order node in the masks' layout, all zero, then those off home now. */
    private fun uploadOffsets() {
        if (!space || drag == null) return
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); offsetTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, offsetTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA32F, 1024, maskHeight, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, floats(1024 * maskHeight * 4))
        // a new context starts with every texel at zero: the bodies off home are written again
        offsetTexels = IntArray(0)
        java.util.Arrays.fill(offsetData, 0f)
        writeOffsets(orbiting = uplan != null)
    }

    /**
     * Writes the floats of the bodies off home this frame and zeroes those that came home since the
     * last, then sends the rows they touch up in one call: the work follows the bodies off home,
     * never every node (S05g2).
     */
    private fun writeOffsets(orbiting: Boolean = false) {
        val d = drag ?: return
        if (offsetTexture == 0) return
        val l = live
        if (l != null) {
            // the Universe (S05h): while the orbits turn every body is off its tree home, so every texel
            // is written, live home minus tree home plus its own drag offset (a few hundred bodies)
            if (!orbiting && d.movingBodies().isEmpty() && offsetTexels.isEmpty()) return
            if (offsetData.isEmpty()) offsetData = FloatArray(1024 * maskHeight * 4)
            val data = offsetData
            val n = tree.nodeCount
            for (k in 0 until n) {
                val o = 4 * k
                if (o + 3 >= data.size) break
                for (a in 0 until 3) data[o + a] = l[3 * k + a] - tree.positions[3 * k + a] + d.offset(k, a)
                data[o + 3] = 0f
            }
            offsetTexels = d.movingBodies()
            val rows = (n + 1023) / 1024
            if (rows <= 0) return
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, offsetTexture)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, 1024, rows, GLES30.GL_RGBA, GLES30.GL_FLOAT,
                FloatBuffer.wrap(data, 0, rows * 1024 * 4))
            return
        }
        val now = d.movingBodies()
        if (now.isEmpty() && offsetTexels.isEmpty()) return
        if (offsetData.isEmpty()) offsetData = FloatArray(1024 * maskHeight * 4)
        val data = offsetData
        var low = Int.MAX_VALUE; var high = -1
        fun texel(node: Int, x: Float, y: Float, z: Float) {
            val k = 4 * node
            if (k + 3 >= data.size) return
            data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = 0f
            val row = node / 1024
            if (row < low) low = row
            if (row > high) high = row
        }
        for (node in offsetTexels) if (java.util.Arrays.binarySearch(now, node) < 0) texel(node, 0f, 0f, 0f)
        for (node in now) texel(node, d.offset(node, 0), d.offset(node, 1), d.offset(node, 2))
        offsetTexels = now
        if (high < 0) return
        val rows = high - low + 1
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, offsetTexture)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, low, 1024, rows, GLES30.GL_RGBA, GLES30.GL_FLOAT,
            FloatBuffer.wrap(data, low * 1024 * 4, rows * 1024 * 4))
    }

    /** Clears the old neighbourhood's texels and writes the new one's, a texel each (Space, Show links). */
    private fun writeFocusMask(near: IntArray) {
        // the bytes first, and the rows they touch; then those rows go up in one call, so a body with
        // thousands of links costs one upload, not one a link
        var low = Int.MAX_VALUE; var high = -1
        fun texel(node: Int, value: Byte) {
            if (node !in focusMask.indices) return
            focusMask[node] = value
            val row = node / 1024
            if (row < low) low = row
            if (row > high) high = row
        }
        for (node in focusTexels) texel(node, 0)
        for (node in near) texel(node, -1)
        focusTexels = near
        // no context yet: the bytes are kept and uploaded with the texture
        if (focusTexture == 0 || high < 0) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, focusTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val rows = high - low + 1
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, low, 1024, rows, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.wrap(focusMask, low * 1024, rows * 1024))
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    /** Hides the points of the nodes that became bodies and shows the ones that stopped, a texel each. */
    private fun applyMask(b: SpaceBodies) {
        if (b.maskChanges.size == 0) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        for (k in 0 until b.maskChanges.size) {
            val change = b.maskChanges[k]
            val node = kotlin.math.abs(change) - 1
            val value: Byte = if (change > 0) -1 else 0
            mask[node] = value
            maskTexel.clear(); maskTexel.put(value).flip()
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, node % 1024, node / 1024, 1, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, maskTexel)
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    }

    private fun uploadLeafState(plan: FramePlan) {
        leafStateBuffer.clear()
        leafStateBuffer.put(plan.leafState, 0, min(plan.leafState.size, leafStateBuffer.capacity()))
        leafStateBuffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leafTexture)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, leafTexWidth, leafTexHeight, GLES30.GL_RGBA, GLES30.GL_FLOAT, leafStateBuffer)
    }

    private var uploadScratch: IntBuffer = ByteBuffer.allocateDirect(8192 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()

    private fun uploadChunk(chunk: Int, pages: IntArray) {
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, edgeIbo)
        val from = tree.leafEdgeStart[chunk]
        val total = tree.leafEdgeIndexCount(chunk)
        for ((k, page) in pages.withIndex()) {
            val offset = k * pageSize
            val count = min(pageSize, total - offset)
            if (count <= 0) break
            uploadScratch.clear()
            uploadScratch.put(tree.leafEdges, from + offset, count)
            uploadScratch.flip()
            GLES30.glBufferSubData(GLES30.GL_ELEMENT_ARRAY_BUFFER, page * pageSize * 4, count * 4, uploadScratch)
        }
    }

    // ---- draws ----

    private fun setCommon(program: Int, focal: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVP"), 1, false, viewProj, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uCenter"), 1, center, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uHalf"), 1, half, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uPalette"), 6, palette, 0)
        GLES30.glUniform1fv(GLES30.glGetUniformLocation(program, "uStyleScale"), 6, styleScale, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSpace"), if (space) 1f else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "rpClock"), clock)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "rpFade"), 1, bandUniform, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uBodyMask"), 1)
        // the chosen body and Show links (S05g): Performance's ring, Space's faded points
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, focusTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uFocusMask"), 2)
        // a held body and what it pulls (S05g2): their offsets from home, read only while any is off home
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, offsetTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uOffsets"), 3)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMoved"), if (offsetTexture != 0 && offsetTexels.isNotEmpty()) 1f else 0f)
        // the filter (S05i): its own mask, read only while one is in force
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uFilterMask"), 4)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFiltered"), if (filterOn) 1f else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFocus"), if (space && focusChunks != null && chosenChunk >= 0) 1f else 0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uChosen"), if (theme == MapTheme.PLAIN) chosenChunk else -1)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uDensity"), density)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFocal"), focal)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uNodeRadius"), nodeRadius)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMaxPoint"), min(maxPointSize, 64f))
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leafTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLeafState"), 0)
    }

    private fun drawNodes(plan: FramePlan, eye: Vec3, focal: Float): Int {
        if (plan.detailLeaves.size == 0) return 0
        setCommon(nodeProgram, focal)
        GLES30.glBindVertexArray(nodeVao)
        var drawn = 0
        for (k in 0 until plan.detailLeaves.size) {
            val cell = tree.leafCell[plan.detailLeaves[k]]
            GLES30.glDrawArrays(GLES30.GL_POINTS, tree.start[cell], tree.count[cell])
            drawn += tree.count[cell]
        }
        GLES30.glBindVertexArray(0)
        return drawn
    }

    /** [focusMode]: 0 every link as it is, 1 Space's Show links (only the chosen body's), 2 Performance's (its ×3, the rest ×0.15). */
    private fun drawEdges(plan: FramePlan, eye: Vec3, focal: Float, focusMode: Float = 0f) {
        if (plan.detailLeaves.size == 0) return
        setCommon(edgeProgram, focal)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(edgeProgram, "uFocusNode"), chosenChunk)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edgeProgram, "uFocusMode"), focusMode)
        GLES30.glBindVertexArray(edgeVao)
        for (k in 0 until plan.detailLeaves.size) {
            val leaf = plan.detailLeaves[k]
            val pages = cache.pages(leaf) ?: continue
            val total = tree.leafEdgeIndexCount(leaf)
            for ((i, page) in pages.withIndex()) {
                val count = min(pageSize, total - i * pageSize)
                if (count <= 0) break
                GLES30.glDrawElements(GLES30.GL_LINES, count, GLES30.GL_UNSIGNED_INT, page * pageSize * 4)
            }
        }
        GLES30.glBindVertexArray(0)
    }

    private fun drawSummaries(plan: FramePlan) {
        if (plan.summaryCount == 0) return
        summaryBuffer = ensure(summaryBuffer, plan.summaries.size)
        summaryBuffer.put(plan.summaries.data, 0, plan.summaries.size).flip()
        GLES30.glUseProgram(summaryProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(summaryProgram, "uVP"), 1, false, viewProj, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(summaryProgram, "uMaxPoint"), maxPointSize)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(summaryProgram, "rpFade"), 1, bandUniform, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, summaryVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, plan.summaries.size * 4, summaryBuffer, GLES30.GL_STREAM_DRAW)
        GLES30.glBindVertexArray(summaryVao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, plan.summaryCount)
        GLES30.glBindVertexArray(0)
    }

    /** [fade]: each bundle's brightness, 0.15 under Performance's Show links. */
    private fun drawBundles(plan: FramePlan, fade: Float = 1f) {
        if (plan.bundleCount == 0) return
        bundleBuffer = ensure(bundleBuffer, plan.bundles.size)
        bundleBuffer.put(plan.bundles.data, 0, plan.bundles.size).flip()
        GLES30.glUseProgram(bundleProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(bundleProgram, "uVP"), 1, false, viewProj, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(bundleProgram, "uViewport"), width.toFloat(), height.toFloat())
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(bundleProgram, "rpFade"), 1, bandUniform, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(bundleProgram, "uBundleFade"), fade)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bundleVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, plan.bundles.size * 4, bundleBuffer, GLES30.GL_STREAM_DRAW)
        GLES30.glBindVertexArray(bundleVao)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, plan.bundleCount)
        GLES30.glBindVertexArray(0)
    }

    private val clip = FloatArray(4)

    /** Real notes only: where each node is on screen and how big, for labels and taps. */
    private fun publishScreenNodes(plan: FramePlan) {
        val callback = onScreenNodes ?: return
        val out = ArrayList<ScreenNode>(tree.nodeCount)
        val focal = camera.focalPixels(height.toFloat())
        val band = band
        val f = appliedFilter
        for (k in 0 until tree.nodeCount) {
            if (f != null && !f.reachable(tree.order[k])) continue
            val leaf = tree.leafOfNode[k]
            val t = plan.leafState[leaf * 4 + 3]
            if (t <= 0f) continue
            val s = leaf * 4
            val x = plan.leafState[s] + (nodeAt(k, 0) - plan.leafState[s]) * t
            val y = plan.leafState[s + 1] + (nodeAt(k, 1) - plan.leafState[s + 1]) * t
            val z = plan.leafState[s + 2] + (nodeAt(k, 2) - plan.leafState[s + 2]) * t
            Mat4.project(viewProj, x, y, z, clip)
            if (clip[3] <= 1e-4f || band.gone(clip[3])) continue
            val sx = (clip[0] / clip[3] * 0.5f + 0.5f) * width
            val sy = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * height
            out.add(ScreenNode(tree.order[k], sx, sy, nodeRadius * focal / clip[3] * 2f * t, band.crisp(clip[3]) || band == SpaceDepthBand.OFF, focal / clip[3]))
        }
        callback(out)
    }

    // ---- helpers ----

    private fun ensure(buffer: FloatBuffer, size: Int): FloatBuffer {
        if (buffer.capacity() >= size) { buffer.clear(); return buffer }
        return floats(max(size, buffer.capacity() * 2))
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, source: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, source); GLES30.glCompileShader(s)
            val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Map shader did not compile: " + GLES30.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Map shader did not link: " + GLES30.glGetProgramInfoLog(p) }
        return p
    }

    companion object {
        const val NODE_STRIDE = 12

        private fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        // The Space theme's depth fade for light (points, links, summaries, bundles): what is left
        // of it at view depth z (1 - f, SpaceDepthBand.light), and whether it is past the band.
        private const val LIGHT = """
float rpLight(float z) {
    float rp_dfW = rpFade.y - rpFade.x;
    if (rp_dfW <= 0.0001) return 1.0;
    return 1.0 - pow(clamp((z - rpFade.x) / rp_dfW, 0.0, 1.0), max(rpFade.z, 0.0001)) * rpFade.w;
}
bool rpGone(float z) { return rpFade.w >= 1.0 && rpFade.y - rpFade.x > 0.0001 && z >= rpFade.y; }
"""

        // Each node morphs between its chunk's summary point and its own place by the chunk's
        // openness t, and its point grows with t: a summary point grows into its nodes.
        private val NODE_HEADER = """#version 300 es
precision highp float;
precision highp int;
layout(location = 0) in vec3 aPos;
layout(location = 1) in uint aLeaf;
layout(location = 2) in uint aGroup;
uniform mat4 uVP;
uniform vec3 uCenter;
uniform vec3 uHalf;
uniform vec3 uPalette[6];
uniform float uFocal;
uniform float uNodeRadius;
uniform float uMaxPoint;
uniform highp sampler2D uLeafState;
uniform float uStyleScale[6];
uniform float uSpace;
uniform float rpClock;
uniform mediump sampler2D uBodyMask;
uniform vec4 rpFade;
// the chosen body (S05g): its chunk-order node or -1 (Performance's mark; the links' Show links),
// Show links in Space (uFocus 1) and its neighbourhood's mask, pixels per dp
uniform int uChosen;
uniform int uFocusNode;
uniform float uFocus;
uniform mediump sampler2D uFocusMask;
uniform float uDensity;
// hold to move (S05g2): each node's offset from home in world units, read while any is off home
uniform highp sampler2D uOffsets;
uniform float uMoved;
// the filter (S05i): 1 for a node it hides, 0.5 for a ghost, read while one is in force
uniform mediump sampler2D uFilterMask;
uniform float uFiltered;
""" + LIGHT + """// Space theme: 1 while this node is drawn as its body (its point hides)
float bodyMasked() {
    if (uSpace < 0.5) return 0.0;
    return texelFetch(uBodyMask, ivec2(gl_VertexID % 1024, gl_VertexID / 1024), 0).r;
}
// the filter (S05i): 1 for a node it hides (its point goes), 0.5 for a ghost (its links go)
float filterCode() {
    if (uFiltered < 0.5) return 0.0;
    return texelFetch(uFilterMask, ivec2(gl_VertexID % 1024, gl_VertexID / 1024), 0).r;
}
// Show links in Space: 0.15 for a node outside the chosen body's neighbourhood (focusLinks), else 1
float focusFade() {
    if (uFocus < 0.5) return 1.0;
    return texelFetch(uFocusMask, ivec2(gl_VertexID % 1024, gl_VertexID / 1024), 0).r > 0.5 ? 1.0 : ${MapMark.FADED};
}
vec4 leafState() { int l = int(aLeaf); return texelFetch(uLeafState, ivec2(l % 1024, l / 1024), 0); }
vec3 offsetOf() {
    if (uMoved < 0.5) return vec3(0.0);
    return texelFetch(uOffsets, ivec2(gl_VertexID % 1024, gl_VertexID / 1024), 0).xyz;
}
vec3 placed(vec4 st) { return mix(st.xyz, uCenter + aPos * uHalf + offsetOf(), st.w); }
"""

        private val NODE_VS = NODE_HEADER + """
out vec3 vColor;
out float vSize;
flat out float vRing;
void main() {
    vec4 st = leafState();
    gl_Position = uVP * vec4(placed(st), 1.0);
    float w = max(gl_Position.w, 1e-4);
    // detail follows the pixels a node covers: a plain point, then shading, then glow
    float px = 2.0 * uNodeRadius * uFocal / w * st.w * uStyleScale[int(aGroup) % 6];
    vSize = clamp(px, 1.0, uMaxPoint);
    // a node smaller than a pixel lights the pixel by the area it covers, so dense far
    // clusters stay coloured instead of adding up to white
    float cover = clamp(px, 0.35, 1.0);
    vRing = 0.0;
    // Performance's chosen point (GraphPerfShaders perf_body_vertex): 1.4 times as big, never under
    // 7 dp, after the clamp, whole, with a white ring
    if (gl_VertexID == uChosen) {
        vSize = max(vSize * ${MapMark.PERF_GROW}, ${MapMark.PERF_LEAST_DP} * uDensity);
        cover = 1.0;
        vRing = 1.0;
    }
    gl_PointSize = vSize;
    vColor = uPalette[int(aGroup) % 6] * cover * cover * rpLight(w) * focusFade();
    if (bodyMasked() > 0.5 || filterCode() > 0.75 || rpGone(w)) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}
"""

        private val NODE_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
in float vSize;
flat in float vRing;
out vec4 fragColor;
void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    if (r2 > 1.0) discard;
    float glow = smoothstep(6.0, 28.0, vSize);
    float coreR = mix(1.0, 0.5, glow);
    float r = sqrt(r2);
    float core = 1.0 - smoothstep(coreR - 0.12, coreR, r);
    float z = sqrt(max(0.0, 1.0 - r2 / (coreR * coreR)));
    float shade = mix(1.0, 0.45 + 0.55 * z + 0.35 * pow(max(0.0, dot(normalize(vec3(c, z)), normalize(vec3(-0.4, 0.5, 0.8)))), 12.0), smoothstep(3.0, 10.0, vSize));
    float halo = glow * 0.55 * exp(-r2 * 5.0);
    vec3 col = vColor * (core * shade * 0.9 + halo);
    // the chosen point's white band, over the whole disc's radius, as perf_body_fragment places it
    if (vRing > 0.5) {
        col = mix(col, vec3(1.0), smoothstep(0.66, 0.74, r) * (1.0 - smoothstep(0.86, 0.94, r)));
    }
    fragColor = vec4(col, 1.0);
}
"""

        private val EDGE_VS = NODE_HEADER + """
out vec3 vColor;
// 1 at the chosen body's end of a link, 0 at the other: above 0 along a link only where it is the chosen body's own
out highp float vTouch;
// above 0 along a link with an end the filter hides or makes a ghost: the whole link is cut (S05i)
out highp float vCut;
void main() {
    vTouch = gl_VertexID == uFocusNode ? 1.0 : 0.0;
    vCut = filterCode() > 0.25 ? 1.0 : 0.0;
    vec4 st = leafState();
    gl_Position = uVP * vec4(placed(st), 1.0);
    vColor = uPalette[int(aGroup) % 6];
    if (uSpace > 0.5) {
        // Space: warm glowing links, tinted by the note's style (sun whiter, black hole ember,
        // comet icy, rocky bluish, gas giant warm, pulsar beating with its beams); a link
        // fades into a body, whose beam carries it from there
        int s = int(aGroup) % 6;
        vec3 warm = vec3(1.0, 0.72, 0.42);
        vec3 tint = s == 5 ? vec3(1.0, 0.93, 0.8) : s == 0 ? vec3(1.0, 0.5, 0.2) : s == 3 ? vec3(0.72, 0.9, 1.0)
            : s == 1 ? vec3(0.78, 0.84, 1.0) : s == 2 ? vec3(1.0, 0.8, 0.55) : vec3(0.85, 0.85, 1.0);
        float beat = s == 4 ? 0.7 + 0.3 * cos(6.2831853 * rpClock * 1.3333333) : 1.0;
        vColor = mix(warm, tint, 0.5) * 1.6 * beat * (1.0 - bodyMasked());
    }
    vColor = vColor * rpLight(gl_Position.w) * focusFade();
}
"""

        // Show links (uFocusMode): in Space only the chosen body's own links are drawn; in Performance
        // they are three times as bright and the rest at 0.15 (GraphPerfShaders perf_link_fragment)
        private val EDGE_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
in highp float vTouch;
in highp float vCut;
uniform float uFocusMode;
out vec4 fragColor;
void main() {
    if (vCut > 0.0) discard;
    vec3 c = vColor * 0.32;
    if (uFocusMode > 0.5 && uFocusMode < 1.5 && vTouch <= 0.0) discard;
    if (uFocusMode > 1.5) c = min(c * (vTouch > 0.0 ? ${MapMark.PERF_FOCUS} : ${MapMark.FADED}), vec3(1.0));
    fragColor = vec4(c, 1.0);
}
"""

        private val SUMMARY_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in float aSize;
layout(location = 2) in vec3 aColor;
uniform mat4 uVP;
uniform float uMaxPoint;
uniform vec4 rpFade;
out vec3 vColor;
""" + LIGHT + """void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    gl_PointSize = clamp(aSize, 1.0, uMaxPoint);
    vColor = aColor * rpLight(gl_Position.w);
    if (rpGone(gl_Position.w)) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}
"""

        // A summary is a soft star: a bright core and a glow, its size set on the CPU from its chunk's on-screen size.
        private const val SUMMARY_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
out vec4 fragColor;
void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    if (r2 > 1.0) discard;
    float core = 1.0 - smoothstep(0.08, 0.3, sqrt(r2));
    float glow = exp(-r2 * 5.0) * 0.35;
    fragColor = vec4(vColor * (core + glow), 1.0);
}
"""

        // A bundle is one quad between two chunk summary points, its width in pixels.
        private val BUNDLE_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec3 aA;
layout(location = 2) in vec3 aB;
layout(location = 3) in float aWidth;
layout(location = 4) in vec3 aColor;
uniform mat4 uVP;
uniform vec2 uViewport;
uniform vec4 rpFade;
uniform float uBundleFade;
out vec3 vColor;
out float vSide;
""" + LIGHT + """void main() {
    vec4 a = uVP * vec4(aA, 1.0);
    vec4 b = uVP * vec4(aB, 1.0);
    if (a.w <= 1e-4 || b.w <= 1e-4) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vColor = vec3(0.0); vSide = 0.0; return; }
    vec2 sa = a.xy / a.w * uViewport * 0.5;
    vec2 sb = b.xy / b.w * uViewport * 0.5;
    vec2 d = sb - sa;
    vec2 n = length(d) > 1e-3 ? normalize(vec2(-d.y, d.x)) : vec2(0.0, 1.0);
    vec4 p = mix(a, b, aCorner.x);
    vec2 offset = n * aCorner.y * max(aWidth, 1.0) * 0.5 / (uViewport * 0.5);
    gl_Position = vec4(p.xy + offset * p.w, p.z, p.w);
    // under a pixel, the quad stays a pixel wide and carries the coverage of its true width
    vColor = aColor * min(aWidth, 1.0) * rpLight(p.w) * uBundleFade;
    vSide = aCorner.y;
}
"""

        private const val BUNDLE_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
in float vSide;
out vec4 fragColor;
void main() { fragColor = vec4(vColor * (1.0 - vSide * vSide), 1.0); }
"""
    }
}
