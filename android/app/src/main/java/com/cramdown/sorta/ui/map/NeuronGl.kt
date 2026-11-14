package com.cramdown.sorta.ui.map

import android.opengl.GLES30
import android.util.Log
import com.cramdown.sorta.map.FilterPop
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.LinkGrowth
import com.cramdown.sorta.map.MapDrag
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.MapFocus
import com.cramdown.sorta.map.MapMark
import com.cramdown.sorta.map.NeuronBodyLook
import com.cramdown.sorta.map.NeuronBridge
import com.cramdown.sorta.map.NeuronCellKind
import com.cramdown.sorta.map.NeuronLookPlan
import com.cramdown.sorta.map.NeuronMesh
import com.cramdown.sorta.map.NeuronMotion
import com.cramdown.sorta.map.NeuronMouths
import com.cramdown.sorta.map.NeuronPalette
import com.cramdown.sorta.map.NeuronPulses
import com.cramdown.sorta.map.NeuronScene
import com.cramdown.sorta.map.NeuronShaders
import com.cramdown.sorta.map.NeuronState
import com.cramdown.sorta.map.NeuronSphere
import com.cramdown.sorta.map.NeuronTissue
import com.cramdown.sorta.map.Quat
import com.cramdown.sorta.map.ThemeBodyKind
import com.cramdown.sorta.map.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the Neurons theme (iOS GraphNeuronLook's scene, NeuronImpulses' ticks and
 * NeuronMouths, GraphLinkBridge's strips) with OpenGL ES 3.0: the fluid behind (dome, far cells,
 * bokeh) round the camera, then each body's soma, membrane or arbor, its halo and the glow of
 * arriving impulses, and every link as a bridge. Every material adds its light, reads no depth
 * and writes none (iOS: blendMode add, writesToDepthBuffer false), so the draws need no order
 * beyond iOS's rendering order. Runs on the GL thread only.
 *
 * Links grow in from their first cell as iOS's do ([growth], LinkGrowth: every link when the map
 * first shows, then only the links new to a rebuilt scene; whole with motion off), their mouths
 * opening with them, and impulses land only on links fully grown (iOS GraphNeuronLook.swift:896,
 * 978). Links gone since the last scene shrink back into their first cell as ghosts.
 *
 * The chosen body (S05g): its halo drawn hot whether or not it has a standing one (iOS's hot ring:
 * rpGain 1.8 and its tints, at rest), its size scaled by the pop, its bridges lit; under Show links
 * ([focus]) only its own bridges, and every body outside its neighbourhood at 0.15 (focusLinks).
 */
class NeuronGl(private val scene: NeuronScene, private val lively: Boolean,
               private val growth: LinkGrowth = LinkGrowth(BooleanArray(scene.plan.links.size), lively = lively)) {
    private class Mesh(val vao: Int, val count: Int)
    private class Program(val id: Int) {
        private val cache = HashMap<String, Int>()
        fun at(name: String): Int = cache.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }

    private val plan = scene.plan
    private val look: NeuronLookPlan = scene.look
    private val tier: GraphicsTier = scene.tier
    private val detail = NeuronLookPlan.detail(tier)
    private val motion = if (lively) 1f else 0f
    private val pulses = NeuronPulses(plan.bodies.size, NeuronLookPlan.rate(tier, lively), NeuronLookPlan.burst(tier, lively))
    private val mouths = NeuronMouths()
    private val ends = IntArray(plan.links.size) { plan.links[it].b }
    private val seeds = IntArray(plan.links.size) { NeuronBridge.themeSeed(it) }
    /** Each strip's samples: 40 at the High tier, 20 at Smooth (iOS GraphicsQuality). */
    val samples = if (tier == GraphicsTier.HIGH) 40 else 20
    private val bridge = NeuronBridge()
    /** Each link's growth this frame, eased (LinkGrowth.shown), then each ghost's. */
    private val shown = FloatArray(growth.size) { 1f }
    /** The links (ghosts included) drawn in the latest frame (MapDrawn.links). */
    @Volatile var linksDrawn = 0; private set

    /**
     * The filter (S05i), set on the GL thread; null shows everything. A body it hides shrinks away and
     * one it shows again grows back on iOS's spring (FilterPop), at once without motion; a link it
     * hides draws back into its first cell (LinkGrowth.step), at once without motion.
     */
    var filter: MapFilterView? = null
        set(value) {
            if (field === value) return
            field = value
            pop.apply(value?.shown, value?.instant ?: false)
        }
    private val pop = FilterPop(plan.bodies.size, lively)
    /** True while a body the filter hid or showed is still on its spring. */
    val popping: Boolean get() = pop.moving
    /** The ideas drawn in the latest frame, by state (NeuronState's order), and the bodies the filter left undrawn (MapDrawn). */
    @Volatile var statesDrawn: List<Int> = emptyList(); private set
    @Volatile var hiddenDrawn = 0; private set
    /**
     * The ideas whose own halo the latest frame drew, by the state it drew (NeuronState's order; S05i
     * repair 6): a resting idea inside a cell has none, any other state keeps one with its process
     * (GraphNeuronLook.swift:340); the chosen body's hot halo is not counted (MapDrawn.halos).
     */
    @Volatile var halosDrawn: List<Int> = emptyList(); private set
    /** Each frame's ideas by state and bodies' opacity, kept so a frame allocates neither. */
    private val stateCounts = IntArray(NeuronState.entries.size)
    private val haloCounts = IntArray(NeuronState.entries.size)
    private val opacityNow = FloatArray(plan.bodies.size)

    /** The chosen body (its index, the scene's), or −1; set on the GL thread before [draw]. */
    var chosen = -1
    /** The body held (S05g2), or −1: marked, popped and its links lit in the chosen body's place while held. */
    var grabbed = -1
    /** The spring run of a held body (S05g2): each body is drawn at its home plus its own offset; null keeps every body home. */
    var drag: MapDrag? = null
    /** The body marked: the one held, else the chosen one (focus = grabbed ?? selected, GraphMotion.swift:1840). */
    private val marked get() = if (grabbed >= 0) grabbed else chosen
    /** The pop this frame: the chosen body's size, and its halo's brightness and size (MapPop). */
    var chosenScale = 1f
    var chosenGlow = 1f
    var chosenSwell = 1f
    /** Under Show links, the chosen body's neighbourhood (sorted body indices); null when everything shows. */
    var focus: IntArray? = null
    /** In the latest frame: whether the hot halo was drawn, the bridges drawn lit, and the bodies drawn faded. */
    var hotDrawn = false; private set
    var litDrawn = 0; private set
    var fadedDrawn = 0; private set

    private var soma: Program? = null; private var arbor: Program? = null; private var membrane: Program? = null
    private var halo: Program? = null; private var arrival: Program? = null; private var strip: Program? = null
    private var dome: Program? = null; private var bokeh: Program? = null; private var far: Program? = null
    private var sphere: Mesh? = null; private var domeMesh: Mesh? = null; private var bokehMesh: Mesh? = null; private var farMesh: Mesh? = null
    private val membraneMeshes = HashMap<Int, Mesh>()
    private val arborMeshes = HashMap<Pair<NeuronCellKind, Int>, Mesh>()
    private var cornerVao = 0
    private var stripVao = 0; private var stripPos = 0; private var stripUv = 0
    private var stripPosData = FloatArray(0); private var stripUvData = FloatArray(0)
    private var stripPosBuffer: FloatBuffer = floats(1); private var stripUvBuffer: FloatBuffer = floats(1)
    private val farBranches = NeuronShaders.farBranches()
    /** The clock of the first frame: the containers open from it (smoothstepped over 0.9 s). */
    private var openedAt = -1f
    private var lastClock = -1f
    var failed = false; private set

    /** Builds the programs and uploads the meshes; on a new GL context, again. */
    fun create() {
        failed = false
        membraneMeshes.clear(); arborMeshes.clear()
        try {
            soma = Program(SpaceGl.buildProgram(NeuronShaders.somaVertex, NeuronShaders.soma))
            membrane = Program(SpaceGl.buildProgram(NeuronShaders.membraneVertex, NeuronShaders.membrane))
            arbor = Program(SpaceGl.buildProgram(NeuronShaders.arborVertex, NeuronShaders.arbor))
            halo = Program(SpaceGl.buildProgram(NeuronShaders.BILLBOARD_VS, NeuronShaders.halo))
            arrival = Program(SpaceGl.buildProgram(NeuronShaders.BILLBOARD_VS, NeuronShaders.arrival))
            strip = Program(SpaceGl.buildProgram(NeuronShaders.STRIP_VS, NeuronShaders.bridge))
            dome = Program(SpaceGl.buildProgram(NeuronShaders.SKY_VS, NeuronShaders.DOME_FS))
            bokeh = Program(SpaceGl.buildProgram(NeuronShaders.SKY_VS, NeuronShaders.BOKEH_FS))
            far = Program(SpaceGl.buildProgram(NeuronShaders.SKY_VS, NeuronShaders.FAR_FS))
        } catch (e: IllegalStateException) {
            Log.e("NeuronGl", "Neurons shaders: ${e.message}")
            failed = true
            return
        }
        sphere = upload(NeuronSphere.mesh(scene.sphereSegments))
        for ((variant, mesh) in scene.membranes) membraneMeshes[variant] = upload(mesh)
        for ((key, mesh) in scene.arbors) arborMeshes[key] = upload(mesh)
        domeMesh = upload(NeuronTissue.dome())
        bokehMesh = upload(NeuronTissue.quads(NeuronTissue.bokehQuads, 0.98f))
        farMesh = upload(NeuronTissue.quads(NeuronTissue.farQuads, 0.97f))
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0); cornerVao = ids[0]
        GLES30.glBindVertexArray(cornerVao)
        GLES30.glGenBuffers(1, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, floats(corners.size).put(corners).flip(), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glGenVertexArrays(1, ids, 0); stripVao = ids[0]
        GLES30.glBindVertexArray(stripVao)
        GLES30.glGenBuffers(2, ids, 0); stripPos = ids[0]; stripUv = ids[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stripPos)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stripUv)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        val vertices = (plan.links.size + growth.ghosts) * (samples + 1) * 2
        stripPosData = FloatArray(max(vertices, 1) * 3); stripUvData = FloatArray(max(vertices, 1) * 2)
        stripPosBuffer = floats(stripPosData.size); stripUvBuffer = floats(stripUvData.size)
    }

    /**
     * Where body [i] is at [time] seconds (labels and taps read this): its home, the plan's place
     * (ThemePlan.position, which sums the orbits up its parents), plus its own offset from a drag
     * (MapDrag.placed), never its parents' offsets too; and its size.
     */
    fun position(i: Int, time: Double): Vec3 = plan.position(i, time).let { home -> drag?.placed(i, home) ?: home }
    fun size(i: Int): Float = look.bodies[i].size
    val bodyCount: Int get() = plan.bodies.size
    val linkCount: Int get() = plan.links.size

    /**
     * One frame: [view] and [proj] the camera's, [eye] its place, [clock] the shaders' clock,
     * [time] seconds since the map started (the bodies' drift), [fade] the depth band (rpFade).
     */
    fun draw(view: FloatArray, proj: FloatArray, eye: Vec3, clock: Float, time: Double, fade: FloatArray) {
        if (failed || soma == null) return
        if (openedAt < 0 || clock < openedAt) openedAt = clock
        val step = if (lastClock >= 0 && clock > lastClock) clock - lastClock else 0f
        lastClock = clock
        pop.step(step)
        // every cell is there from the first frame, so each link's wait starts with it; a link the
        // filter takes away draws back (S05i)
        val kept = filter?.links
        val instant = filter?.instant ?: false
        for (i in 0 until growth.size) {
            if (kept != null && i < growth.links && i < kept.size && !kept[i]) {
                if (lively && !instant) { growth.step(i, false, step); shown[i] = growth.shown(i) } else shown[i] = 0f
                continue
            }
            growth.tick(i, true, step)
            shown[i] = growth.shown(i)
        }
        pulses.tick(clock, step, ends, seeds, shown)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // the fluid behind: the dome opaque, then the far cells and bokeh added on
        GLES30.glDisable(GLES30.GL_BLEND)
        drawSky(dome!!, domeMesh!!, view, proj)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        far!!.let { p ->
            GLES30.glUseProgram(p.id)
            GLES30.glUniform2fv(p.at("uBranch"), 6, farBranches, 0)
            drawSky(p, farMesh!!, view, proj)
        }
        drawSky(bokeh!!, bokehMesh!!, view, proj)

        val count = plan.bodies.size
        val places = Array(count) { position(it, time) }
        val radius = FloatArray(count) { look.bodies[it].size * 0.8f }

        // the links' mouths in the whole cells' membranes
        mouths.clear()
        for ((e, link) in plan.links.withIndex()) {
            if (link.a !in 0 until count || link.b !in 0 until count || shown[e] <= 0.001f) continue
            val half = if (link.kind == 5) NeuronLookPlan.NEAR_BRIDGE else NeuronLookPlan.FAR_BRIDGE
            mouths.addLink(link.a, link.b, places[link.a], places[link.b], radius[link.a], radius[link.b], half, shown[e], eye) { look.bodies[it].whole }
        }
        for (g in 0 until growth.ghosts) {
            val a = growth.ghostEnds[2 * g]; val b = growth.ghostEnds[2 * g + 1]
            val grow = shown[growth.links + g]
            if (a !in 0 until count || b !in 0 until count || grow <= 0.001f) continue
            mouths.addLink(a, b, places[a], places[b], radius[a], radius[b], NeuronLookPlan.FAR_BRIDGE, grow, eye) { look.bodies[it].whole }
        }

        // bodies (iOS rendering order 6), then halos (7), then arrival glows (8)
        val opening = NeuronPulses.opening(0f, 1f, clock - openedAt)
        val near = focus
        val opacity = opacityNow
        for (i in 0 until count) opacity[i] = if (near == null || MapFocus.holdsAny(near, i, i + 1)) 1f else MapMark.FADED
        fadedDrawn = opacity.count { it < 1f }
        hotDrawn = false
        // the filter: a body gone is not drawn, one on its spring at its size, a ghost at 0.3
        val ghosts = filter?.ghost
        for (i in 0 until count) if (ghosts != null && i < ghosts.size && ghosts[i]) opacity[i] *= MapFilterView.GHOST_ALPHA
        val states = stateCounts
        states.fill(0)
        var hidden = 0
        for (b in look.bodies) {
            if (pop.gone(b.index)) { hidden++; continue }
            if (plan.bodies[b.index].kind == ThemeBodyKind.NOTE) states[b.state.code]++
            drawBody(b, places[b.index], view, proj, clock, opening, fade,
                (if (b.index == marked) chosenScale else 1f) * pop.scale(b.index), opacity.getOrElse(b.index) { 1f })
        }
        // a new list only when a count changed: a settled frame makes none
        val last = statesDrawn
        var changed = last.size != states.size
        var k = 0
        while (!changed && k < states.size) { if (last[k] != states[k]) changed = true; k++ }
        if (changed) statesDrawn = states.toList()
        hiddenDrawn = hidden
        val halos = haloCounts
        halos.fill(0)
        for (b in look.bodies) {
            if (pop.gone(b.index)) continue
            val hot = b.index == marked
            if (b.standingHalo && !hot && plan.bodies[b.index].kind == ThemeBodyKind.NOTE) halos[b.state.code]++
            if (b.standingHalo || hot) drawHalo(b, places[b.index], view, proj, clock, fade, hot,
                opacity.getOrElse(b.index) { 1f } * min(pop.scale(b.index), 1f) * (1f + pop.bloom(b.index)))
        }
        val lastHalos = halosDrawn
        var halosChanged = lastHalos.size != halos.size
        var h = 0
        while (!halosChanged && h < halos.size) { if (lastHalos[h] != halos[h]) halosChanged = true; h++ }
        if (halosChanged) halosDrawn = halos.toList()
        for (b in look.bodies) {
            if (pop.gone(b.index)) continue
            val level = pulses.shown(b.index)
            if (level > 0f) drawArrival(b, places[b.index], level * opacity.getOrElse(b.index) { 1f } * min(pop.scale(b.index), 1f), view, proj, fade)
        }
        drawBridges(places, radius, view, proj, eye, clock, fade)
        GLES30.glBindVertexArray(0)
    }

    private fun drawSky(p: Program, mesh: Mesh, view: FloatArray, proj: FloatArray) {
        GLES30.glUseProgram(p.id)
        GLES30.glUniformMatrix4fv(p.at("uView"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(p.at("uProj"), 1, false, proj, 0)
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.count, GLES30.GL_UNSIGNED_INT, 0)
    }

    private val model = FloatArray(16)
    private val modelView = FloatArray(16)

    private fun setModel(at: Vec3, turn: Quat, scale: Float) {
        val r = turn.matrix()
        for (c in 0 until 3) {
            model[c * 4] = r[c * 3] * scale; model[c * 4 + 1] = r[c * 3 + 1] * scale; model[c * 4 + 2] = r[c * 3 + 2] * scale
            model[c * 4 + 3] = 0f
        }
        model[12] = at.x; model[13] = at.y; model[14] = at.z; model[15] = 1f
    }

    private fun common(p: Program, view: FloatArray, proj: FloatArray, at: Vec3, clock: Float, fade: FloatArray, opacity: Float = 1f) {
        GLES30.glUseProgram(p.id)
        GLES30.glUniformMatrix4fv(p.at("uView"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(p.at("uProj"), 1, false, proj, 0)
        GLES30.glUniform3f(p.at("uWorld"), at.x, at.y, at.z)
        GLES30.glUniform4fv(p.at("rpFade"), 1, fade, 0)
        GLES30.glUniform1f(p.at("rpClock"), clock)
        GLES30.glUniform1f(p.at("rpMotion"), motion)
        GLES30.glUniform1f(p.at("rpDetail"), detail)
        GLES30.glUniform1f(p.at("uOpacity"), opacity)
    }

    private fun mesh(p: Program, mesh: Mesh, view: FloatArray) {
        GLES30.glUniformMatrix4fv(p.at("uModel"), 1, false, model, 0)
        android.opengl.Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        GLES30.glUniformMatrix4fv(p.at("uMV"), 1, false, modelView, 0)
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.count, GLES30.GL_UNSIGNED_INT, 0)
    }

    private fun tint(p: Program, name: String, c: Vec3) = GLES30.glUniform3f(p.at(name), c.x, c.y, c.z)

    private fun drawBody(b: NeuronBodyLook, at: Vec3, view: FloatArray, proj: FloatArray, clock: Float, opening: Float, fade: FloatArray,
                         grow: Float = 1f, opacity: Float = 1f) {
        // the pop: the chosen body's size on its spring
        val size = b.size * grow
        val hasArbor = b.arborTurn != null
        val pose = if (lively) NeuronMotion.pose(b.state, b.inner, hasArbor, b.lag, clock) else NeuronMotion.Pose(1f, 1f, 0f, 0f, 0f)
        val somaAt = at + b.up * (pose.somaMove * size)
        // the soma (GraphNeuronLook.somaMaterial values)
        soma?.let { p ->
            common(p, view, proj, at, clock, fade, opacity)
            GLES30.glUniform1f(p.at("rpSway"), clock * motion)
            GLES30.glUniform1f(p.at("rpWobble"), if (lively) b.kind.wobble else 0f)
            GLES30.glUniform1f(p.at("rpNucleus"), b.kind.nucleus)
            GLES30.glUniform1f(p.at("rpState"), b.state.code.toFloat())
            GLES30.glUniform1f(p.at("rpOpen"), if (b.open) opening else 0f)
            GLES30.glUniform1f(p.at("rpFill"), b.kind.fill)
            GLES30.glUniform1f(p.at("rpMembrane"), if (b.whole) 1f else 0f)
            tint(p, "rpTintA", b.membraneColour); tint(p, "rpTintB", NeuronPalette.nucleus); tint(p, "rpTintC", NeuronLookPlan.sparkle)
            setModel(somaAt, b.somaTurn, size * pose.soma)
            mesh(p, sphere!!, view)
        }
        if (b.whole) {
            // the membrane, a child of the soma: its transform, and its mouths in the soma's frame
            val skin = membraneMeshes[b.variant] ?: return
            membrane?.let { p ->
                common(p, view, proj, at, clock, fade, opacity)
                GLES30.glUniform1f(p.at("rpSway"), clock * motion)
                GLES30.glUniform1f(p.at("rpWobble"), if (lively) b.kind.wobble else 0f)
                tint(p, "rpTintA", b.gold)
                val list = mouths.mouths(b.index)
                val unturn = b.somaTurn.inverse
                for (k in 0 until NeuronMouths.COUNT) {
                    val m = list.getOrNull(k)
                    if (m == null) GLES30.glUniform4f(p.at("rpMouth$k"), 0f, 0f, 0f, 0f)
                    else { val d = unturn.act(m.dir); GLES30.glUniform4f(p.at("rpMouth$k"), d.x, d.y, d.z, m.width) }
                }
                setModel(somaAt, b.somaTurn, size * pose.soma)
                mesh(p, skin, view)
            }
        } else if (b.arborTurn != null) {
            val tree = arborMeshes[b.kind to b.variant] ?: return
            arbor?.let { p ->
                common(p, view, proj, at, clock, fade, opacity)
                GLES30.glUniform1f(p.at("rpSway"), clock * motion)
                GLES30.glUniform1f(p.at("rpWobble"), 0.03f)
                tint(p, "rpTintA", b.gold)
                val turn = if (pose.arborTurn != 0f) Quat.angleAxis(pose.arborTurn, b.up) * b.arborTurn else b.arborTurn
                setModel(at + b.up * (pose.arborMove * size), turn, size * pose.arbor)
                mesh(p, tree, view)
            }
        }
    }

    private fun billboard(p: Program, at: Vec3, half: Float) {
        GLES30.glUniform3f(p.at("uCentre"), at.x, at.y, at.z)
        GLES30.glUniform1f(p.at("uHalf"), half)
        GLES30.glBindVertexArray(cornerVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /**
     * A body's halo; [hot] for the chosen body's: iOS's hot ring (GraphNeuronLook.swift:220-221,
     * :697-702), gain 1.8 with its tints and its state at rest, brighter and bigger by the pop's bloom.
     */
    private fun drawHalo(b: NeuronBodyLook, at: Vec3, view: FloatArray, proj: FloatArray, clock: Float, fade: FloatArray,
                         hot: Boolean = false, opacity: Float = 1f) {
        val p = halo ?: return
        common(p, view, proj, at, clock, fade, opacity)
        if (hot) {
            GLES30.glUniform1f(p.at("rpGain"), MapMark.HOT_GAIN * chosenGlow)
            GLES30.glUniform1f(p.at("rpState"), 0f)
            GLES30.glUniform3fv(p.at("rpTintA"), 1, MapMark.HOT_TINT_A, 0)
            GLES30.glUniform3fv(p.at("rpTintC"), 1, MapMark.HOT_TINT_C, 0)
            billboard(p, at, b.size * chosenScale * chosenSwell * 2.5f)
            hotDrawn = true
            return
        }
        GLES30.glUniform1f(p.at("rpGain"), MapMark.HALO_GAIN)
        GLES30.glUniform1f(p.at("rpState"), b.state.code.toFloat())
        tint(p, "rpTintA", b.haloTint); tint(p, "rpTintC", Vec3.ZERO)
        billboard(p, at, b.size * 2.5f)
    }

    private fun drawArrival(b: NeuronBodyLook, at: Vec3, level: Float, view: FloatArray, proj: FloatArray, fade: FloatArray) {
        val p = arrival ?: return
        common(p, view, proj, at, 0f, fade)
        GLES30.glUniform1f(p.at("uOpacity"), level)
        tint(p, "rpTintA", NeuronLookPlan.arrivalGlow); tint(p, "rpTintB", NeuronPalette.impulse)
        billboard(p, at, b.size * 3.5f)
    }

    private fun drawBridges(places: Array<Vec3>, radius: FloatArray, view: FloatArray, proj: FloatArray, eye: Vec3,
                            clock: Float, fade: FloatArray) {
        val p = strip ?: return
        linksDrawn = 0
        litDrawn = 0
        if (plan.links.isEmpty() && growth.ghosts == 0) return
        val perLink = (samples + 1) * 2
        val drawn = BooleanArray(plan.links.size + growth.ghosts)
        var drawnCount = 0
        var litCount = 0
        val only = if (focus != null) chosen else -1
        for ((e, link) in plan.links.withIndex()) {
            // a link not grown at all is not drawn (iOS updateLines)
            if (link.a !in places.indices || link.b !in places.indices || shown[e] <= 0.001f) continue
            // Show links: only the chosen body's own (focusLinks)
            if (focus != null && !MapFocus.own(only, link.a, link.b)) continue
            val half = if (link.kind == 5) NeuronLookPlan.NEAR_BRIDGE else NeuronLookPlan.FAR_BRIDGE
            // lit: the held body's links, else the chosen body's
            val lit = MapFocus.own(marked, link.a, link.b)
            if (lit) litCount++
            bridge.fill(places[link.a], places[link.b], radius[link.a], radius[link.b], e, shown[e], lit, half, eye,
                samples, stripPosData, stripUvData, e * perLink)
            drawn[e] = true
            drawnCount++
        }
        litDrawn = litCount
        // the links gone since the last scene, seeded as iOS's ghosts (g + 7), between cells: tracts
        for (g in 0 until growth.ghosts) {
            val a = growth.ghostEnds[2 * g]; val b = growth.ghostEnds[2 * g + 1]
            val slot = plan.links.size + g
            if (a !in places.indices || b !in places.indices || shown[slot] <= 0.001f) continue
            bridge.fill(places[a], places[b], radius[a], radius[b], g + 7, shown[slot], false, NeuronLookPlan.FAR_BRIDGE, eye,
                samples, stripPosData, stripUvData, slot * perLink)
            drawn[slot] = true
            drawnCount++
        }
        linksDrawn = drawnCount
        stripPosBuffer.clear(); stripPosBuffer.put(stripPosData).flip()
        stripUvBuffer.clear(); stripUvBuffer.put(stripUvData).flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stripPos)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, stripPosData.size * 4, stripPosBuffer, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stripUv)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, stripUvData.size * 4, stripUvBuffer, GLES30.GL_STREAM_DRAW)
        common(p, view, proj, Vec3.ZERO, clock, fade)
        GLES30.glUniform1f(p.at("rpRate"), NeuronLookPlan.rate(tier, lively))
        GLES30.glUniform1f(p.at("rpBurst"), NeuronLookPlan.burst(tier, lively))
        tint(p, "rpTintB", NeuronPalette.impulse); tint(p, "rpTintC", NeuronPalette.impulseHalo)
        GLES30.glBindVertexArray(stripVao)
        // near (inside an opened cell, fibre) and far (between cells, tract): their own half widths and tints
        for (near in listOf(true, false)) {
            GLES30.glUniform1f(p.at("rpHalf"), if (near) NeuronLookPlan.NEAR_BRIDGE else NeuronLookPlan.FAR_BRIDGE)
            tint(p, "rpTintA", if (near) NeuronLookPlan.fibre else NeuronLookPlan.tract)
            for ((e, link) in plan.links.withIndex()) {
                if (!drawn[e] || (link.kind == 5) != near) continue
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, e * perLink, perLink)
            }
            if (!near) for (g in 0 until growth.ghosts) {
                val slot = plan.links.size + g
                if (drawn[slot]) GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, slot * perLink, perLink)
            }
        }
    }

    private fun upload(mesh: NeuronMesh): Mesh {
        val ids = IntArray(4)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glGenBuffers(4, ids, 0)
        val vertices = mesh.points.size / 3
        fun attribute(buffer: Int, location: Int, data: FloatArray, size: Int) {
            if (data.size < vertices * size) return
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floats(data.size).put(data).flip(), GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, size * 4, 0)
        }
        attribute(ids[0], 0, mesh.points, 3)
        // a sky mesh carries its colours in the normals' place and its tints in the coords' (rgb)
        attribute(ids[1], 1, mesh.normals, 3)
        val uvSize = if (mesh.coords.size == vertices * 3) 3 else 2
        if (uvSize == 2) attribute(ids[2], 2, mesh.coords, 2)
        else {
            // quads: (u, v) are in normals' place and the tint in coords'; the sky shader reads aColour at 1, aUV at 2
            attribute(ids[1], 1, mesh.coords, 3)
            attribute(ids[2], 2, uvOf(mesh.normals, vertices), 2)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[3])
        val index: IntBuffer = ByteBuffer.allocateDirect(max(mesh.indices.size, 1) * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        index.put(mesh.indices).flip()
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, index, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
        return Mesh(vao, mesh.indices.size)
    }

    private fun uvOf(data: FloatArray, vertices: Int): FloatArray = FloatArray(vertices * 2) { if (it < data.size) data[it] else 0f }

    private companion object {
        fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(max(n, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
