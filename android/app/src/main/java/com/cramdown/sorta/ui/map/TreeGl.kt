package com.cramdown.sorta.ui.map

import android.opengl.GLES30
import android.util.Log
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.TreeModel
import com.cramdown.sorta.map.TreeState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max

/**
 * Draws the 3D Tree (S11) with OpenGL ES 3.0: every branch an instance of one curved tube, tapering,
 * the trunk flaring at its foot, and every leaf an instance of one folded, curled leaf blade
 * (TreeModel), lit by a key light from the upper left, a soft sky-and-ground ambient and a little rim,
 * both faces of a leaf lit, the light glowing through one seen from behind; fading into the page's
 * own colour with depth, by day and by night; what is deep in the crown is shaded darker, so its
 * depth reads. Two instanced draws a frame, from buffers made once; a frame only sets
 * uniforms. Which nodes are hidden (the filter) and chosen is one byte a node in a small texture,
 * written only when either changes. A twig is never drawn thinner than [TreeWeight.TWIG_DP] across
 * nor a leaf shorter than [TreeWeight.LEAF_DP], at [density] pixels a dp. Runs on the GL thread only.
 */
class TreeGl(private val model: TreeModel, private val contrast: Boolean, private val density: Float = 1f) {
    private var tubeProgram = 0
    private var leafProgram = 0
    private var tubeVao = 0; private var leafVao = 0
    private var tubeIndices = 0; private var leafIndices = 0
    private var stateTexture = 0
    private var failed = false

    /** The filter, set on the GL thread from the renderer's; the next frame writes the texture. */
    var filter: MapFilterView? = null
        set(value) { if (field !== value) { field = value; stale = true } }
    /** The chosen node, −1 for none. */
    var chosen: Int = -1
        set(value) { if (field != value) { field = value; stale = true } }
    /** Show links: the chosen idea's neighbourhood (MapFocus.neighbourhood), each lit; null for none. */
    var near: IntArray? = null
        set(value) { if (!(field contentEquals value)) { field = value; stale = true } }
    /** The part flown in to (0 or less for the whole tree): all outside its subtree is drawn faded toward the page. */
    var focus: Int = 0
        set(value) { if (field != value) { field = value; stale = true } }
    private var stale = true
    private val rows = max(1, (model.nodes.size + WIDTH - 1) / WIDTH)
    private val state = ByteBuffer.allocateDirect(WIDTH * rows).order(ByteOrder.nativeOrder())
    /** How many nodes the filter hides, as last written. */
    var hidden = 0; private set

    // uniforms' places and the frame's matrix, kept so a frame makes nothing
    private val tube = IntArray(U_COUNT)
    private val leaf = IntArray(U_COUNT)
    private val light = floatArrayOf(-0.42f, 0.82f, 0.39f)
    private val proj = FloatArray(16)
    // the crown's reach from the trunk's line, for the shade
    private val crown: Float = run {
        val l = model.layout
        var most = 1e-4
        for (i in 0 until model.nodes.size) {
            val x = l.anchor[3 * i]; val z = l.anchor[3 * i + 2]
            most = kotlin.math.max(most, kotlin.math.sqrt(x * x + z * z))
        }
        most.toFloat()
    }

    fun create() {
        failed = false
        try {
            tubeProgram = SpaceGl.buildProgram(TUBE_VS, LIT_FS)
            leafProgram = SpaceGl.buildProgram(LEAF_VS, LIT_FS)
        } catch (e: IllegalStateException) {
            Log.e("TreeGl", "Tree shaders: ${e.message}")
            failed = true
            return
        }
        for ((p, out) in listOf(tubeProgram to tube, leafProgram to leaf)) for (k in 0 until U_COUNT) out[k] = GLES30.glGetUniformLocation(p, UNIFORMS[k])
        val (tv, ti) = TreeModel.tube(model.segments, model.rings)
        val (bv, bi) = TreeModel.blade(model.bladeRows)
        tubeIndices = ti.size; leafIndices = bi.size
        tubeVao = vao(tv, ti, 3, model.branches, TreeModel.BRANCH_FLOATS, intArrayOf(3, 3, 3, 2, 3, 1))
        leafVao = vao(bv, bi, 6, model.leaves, TreeModel.LEAF_FLOATS, intArrayOf(3, 3, 1, 1, 3, 1))
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); stateTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stateTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, WIDTH, rows, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)
        stale = true
    }

    /**
     * One mesh's vertices ([vertex] floats each: a place, then a normal when 6) and triangles, and the
     * instances drawn with it, each attribute [sizes] floats; the instances' attributes follow the mesh's.
     */
    private fun vao(mesh: FloatArray, indices: ShortArray, vertex: Int, instances: FloatArray, stride: Int, sizes: IntArray): Int {
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glGenBuffers(3, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, floats(mesh), GLES30.GL_STATIC_DRAW)
        val first = vertex / 3
        for (k in 0 until first) {
            GLES30.glEnableVertexAttribArray(k)
            GLES30.glVertexAttribPointer(k, 3, GLES30.GL_FLOAT, false, vertex * 4, k * 12)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, max(instances.size, 1) * 4, floats(instances), GLES30.GL_STATIC_DRAW)
        var offset = 0
        for ((k, size) in sizes.withIndex()) {
            GLES30.glEnableVertexAttribArray(first + k)
            GLES30.glVertexAttribPointer(first + k, size, GLES30.GL_FLOAT, false, stride * 4, offset * 4)
            GLES30.glVertexAttribDivisor(first + k, 1)
            offset += size
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
        val ib: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(indices).also { it.flip() }
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
        return vao
    }

    private fun writeState() {
        stale = false
        val f = filter
        val n = model.nodes.size
        var h = 0
        val lit = near
        val fo = focus
        val nodes = model.nodes
        state.clear()
        for (i in 0 until WIDTH * rows) {
            val v = if (i >= n) TreeState.NONE else {
                val gone = f != null && !f.reachable(i)
                if (gone) h++
                TreeState.of(nodes, i, gone, chosen, lit != null && i in lit, fo)
            }
            state.put(v.toByte())
        }
        state.flip()
        hidden = h
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stateTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, WIDTH, rows, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, state)
    }

    /**
     * One frame: the page's colour, then the branches and leaves, lit and fading from the camera's
     * target ([distance] away) to [fogReach] past it. [fovY] and [aspect] are the camera's; its own near
     * and far planes hug the whole tree's ball ([toCentre] from the eye to its centre, [reach] round it),
     * so the depth buffer keeps its precision. [focal] is pixels per unit at depth 1.
     */
    fun draw(view: FloatArray, fovY: Float, aspect: Float, distance: Float, toCentre: Float, reach: Float, fogReach: Float, focal: Float) {
        val sky = model.sky
        GLES30.glClearColor(sky[0], sky[1], sky[2], 1f)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (failed || tubeProgram == 0) return
        if (stale) writeState()
        val near = max(toCentre - reach * 1.15f, max(distance, toCentre) * 0.005f)
        val far = toCentre + reach * 1.15f
        perspective(proj, fovY, aspect, near, far)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stateTexture)
        val fogStart = distance * 0.85f
        val fogEnd = distance + fogReach * 1.6f
        val cap = if (contrast) 0.55f else 0.82f
        for (k in 0..1) {
            val program = if (k == 0) tubeProgram else leafProgram
            val u = if (k == 0) tube else leaf
            val vao = if (k == 0) tubeVao else leafVao
            val count = if (k == 0) tubeIndices else leafIndices
            val instances = if (k == 0) model.branchCount else model.leafCount
            if (instances == 0) continue
            GLES30.glUseProgram(program)
            GLES30.glUniformMatrix4fv(u[0], 1, false, view, 0)
            GLES30.glUniformMatrix4fv(u[1], 1, false, proj, 0)
            GLES30.glUniform3f(u[2], TreeWeight.twigFloor(density, focal), TreeWeight.leafFloor(density, focal), TreeWeight.leafCeiling(density, focal))
            GLES30.glUniform3f(u[3], light[0], light[1], light[2])
            GLES30.glUniform3f(u[4], sky[0], sky[1], sky[2])
            GLES30.glUniform3f(u[5], fogStart, fogEnd, cap)
            GLES30.glUniform3f(u[6], model.accent[0], model.accent[1], model.accent[2])
            GLES30.glUniform1i(u[7], 0)
            GLES30.glUniform1f(u[8], crown)
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_SHORT, 0, instances)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
    }

    /** [Mat4.perspective] written into [out], so a frame makes no matrix. */
    private fun perspective(out: FloatArray, fovY: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / kotlin.math.tan(fovY / 2)
        out.fill(0f)
        out[0] = f / aspect; out[5] = f
        out[10] = (far + near) / (near - far); out[11] = -1f
        out[14] = 2f * far * near / (near - far)
    }

    companion object {
        /** The state texture's width: a texel a node, a row of 1024. */
        const val WIDTH = 1024
        const val HIDDEN = TreeState.HIDDEN
        const val CHOSEN = TreeState.CHOSEN
        const val LINKED = TreeState.LINKED
        const val FADED = TreeState.FADED
        private val UNIFORMS = arrayOf("uView", "uProj", "uPixel", "uLight", "uSky", "uFog", "uAccent", "uState", "uCrown")
        private const val U_COUNT = 9

        private fun floats(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(max(a.size, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.flip() }

        private const val HEAD = """#version 300 es
precision highp float;
uniform mat4 uView;
uniform mat4 uProj;
uniform vec3 uPixel;
uniform vec3 uLight;
uniform float uCrown;
uniform highp sampler2D uState;
out vec3 vNormal;
out vec3 vEye;
out vec3 vKey;
out vec3 vUp;
out vec3 vColor;
out float vDepth;
out float vState;
out float vShade;
out float vLeaf;
float stateOf(float node) {
    int i = int(node + 0.5);
    return texelFetch(uState, ivec2(i % 1024, i / 1024), 0).r * 255.0;
}
// the light and the normal in the eye's space, where the rim and both faces are worked out
void finish(vec3 p, vec3 n, vec3 color, float state, float leaf) {
    vec4 eye = uView * vec4(p, 1.0);
    mat3 turn = mat3(uView);
    vDepth = -eye.z; vEye = eye.xyz; vNormal = turn * n; vKey = turn * uLight; vUp = turn * vec3(0.0, 1.0, 0.0);
    vColor = color; vState = state; vLeaf = leaf;
    // deep in the crown, near the trunk's line, is darker: the crown's depth reads
    vShade = 0.76 + 0.24 * clamp(length(p.xz) / max(uCrown, 1e-4), 0.0, 1.0);
    // a hidden node folds to one point outside the view: nothing is drawn
    gl_Position = (state > 0.5 && state < 1.5) ? vec4(2.0, 2.0, 2.0, 1.0) : uProj * eye;
}
"""

        /**
         * A branch: the unit tube's ring (cos, sin, along) set round its curve from base through the
         * control point to tip, square to the curve there, tapering; the trunk flares at its foot and
         * every branch thickens a little where it leaves its parent (its collar).
         */
        val TUBE_VS = HEAD + """
layout(location = 0) in vec3 aRing;
layout(location = 1) in vec3 aBase;
layout(location = 2) in vec3 aTip;
layout(location = 3) in vec3 aCtrl;
layout(location = 4) in vec2 aRadius;
layout(location = 5) in vec3 aColor;
layout(location = 6) in float aNode;
void main() {
    // the closing ring (past 1 along) is one point just past the tip
    float cap = step(1.5, aRing.z);
    float t = min(aRing.z, 1.0), s = 1.0 - t;
    vec3 c = s * s * aBase + 2.0 * s * t * aCtrl + t * t * aTip;
    vec3 d = 2.0 * s * (aCtrl - aBase) + 2.0 * t * (aTip - aCtrl);
    d = d / max(length(d), 1e-9);
    // one across for the whole branch, from its chord, turned square to the curve at each ring
    vec3 chord = aTip - aBase;
    chord = chord / max(length(chord), 1e-9);
    vec3 helper = abs(chord.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 a = normalize(cross(helper, chord));
    vec3 u = normalize(a - d * dot(a, d));
    vec3 v = cross(d, u);
    vec3 side = u * aRing.x + v * aRing.y;
    float depth = max(-(uView * vec4(c, 1.0)).z, 1e-4);
    float swell = aNode < 0.5 ? """ + "${TreeModel.FLARE}" + """ * pow(max(0.0, 1.0 - t / """ + "${TreeModel.FLARE_SPAN}" + """), 2.0)
        : """ + "${TreeModel.COLLAR}" + """ * pow(max(0.0, 1.0 - t / """ + "${TreeModel.COLLAR_SPAN}" + """), 2.0);
    float r = max(mix(aRadius.x, aRadius.y, t) * (1.0 + swell), uPixel.x * depth);
    // the tip closed by a short cone past it, so no branch ends in an open cut and a leaf's stalk joins
    vec3 n = normalize(mix(side, d, cap * 0.7));
    finish(c + side * r * (1.0 - cap) + d * (cap * """ + "${TreeModel.CAP_REACH}" + """ * r), n, aColor, stateOf(aNode), 0.0);
}
"""

        /**
         * A leaf: the blade (its place and normal) from its stem's tip out along its axis, its face
         * turned up toward the sky, then rolled about its axis by its own turn.
         */
        const val LEAF_VS = HEAD + """
layout(location = 0) in vec3 aPlace;
layout(location = 1) in vec3 aFace;
layout(location = 2) in vec3 aCentre;
layout(location = 3) in vec3 aAxis;
layout(location = 4) in float aRadius;
layout(location = 5) in float aRoll;
layout(location = 6) in vec3 aColor;
layout(location = 7) in float aNode;
void main() {
    vec3 d = normalize(aAxis);
    vec3 helper = abs(d.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 across = normalize(cross(d, helper));
    vec3 up = cross(across, d);
    float cr = cos(aRoll), sr = sin(aRoll);
    vec3 x = across * cr + up * sr;
    vec3 z = up * cr - across * sr;
    float depth = max(-(uView * vec4(aCentre, 1.0)).z, 1e-4);
    // never under the floor, nor over the ceiling: flown in, a leaf stays a leaf, not a wall
    float r = min(max(aRadius, uPixel.y * depth), uPixel.z * depth);
    vec3 p = aCentre + (x * aPlace.x + d * aPlace.y + z * aPlace.z) * r;
    vec3 n = normalize(x * aFace.x + d * aFace.y + z * aFace.z);
    finish(p, n, aColor, stateOf(aNode), 1.0);
}
"""

        /**
         * A key light (Lambert), a soft ambient brighter from the sky than the ground, and a little rim
         * where a surface turns away; the face toward the eye lit, so a leaf seen from below is lit too,
         * and the key glowing through it then; a faint waxy gloss on a leaf. Then a chosen node toward
         * the accent, a faded one (outside the part flown in to) toward the page, and the page's colour
         * with depth.
         */
        const val LIT_FS = """#version 300 es
precision mediump float;
uniform vec3 uSky;
uniform vec3 uFog;
uniform vec3 uAccent;
in vec3 vNormal;
in vec3 vEye;
in vec3 vKey;
in vec3 vUp;
in vec3 vColor;
in float vDepth;
in float vState;
in float vShade;
in float vLeaf;
out vec4 outColor;
void main() {
    vec3 n = normalize(vNormal);
    vec3 e = normalize(-vEye);
    vec3 l = normalize(vKey);
    float facing = dot(n, e);
    if (facing < 0.0) { n = -n; facing = -facing; }
    float key = max(dot(n, l), 0.0);
    float through = vLeaf * max(dot(-n, l), 0.0) * 0.45;
    float ambient = mix(0.34, 0.56, 0.5 + 0.5 * dot(n, normalize(vUp)));
    float rim = pow(1.0 - facing, 3.0) * 0.22;
    float gloss = vLeaf * pow(max(dot(n, normalize(l + e)), 0.0), 28.0) * 0.12;
    vec3 c = vColor * (ambient + 0.72 * key + through) * vShade + vec3(rim + gloss);
    if (vState > 3.5) c = mix(c, uSky, 0.72);
    else if (vState > 2.5) c = mix(c, uAccent, 0.4);
    else if (vState > 1.5) c = mix(c, uAccent, 0.7);
    float f = clamp((vDepth - uFog.x) / max(uFog.y - uFog.x, 1e-3), 0.0, 1.0) * uFog.z;
    outColor = vec4(mix(c, uSky, f), 1.0);
}
"""
    }
}

/**
 * How thin the Tree may be drawn (S11, the lead's review of 18 Nov): the finest twig at least [TWIG_DP]
 * across and a leaf at least [LEAF_DP] long on screen, however far. Each floor is a radius per unit of
 * depth: a radius of floor x depth projects, at [focal] pixels per unit at depth 1, to half the width.
 * Nor is a leaf drawn longer than [LEAF_MOST_DP]: flown in close, leaves would wall the branches off.
 */
object TreeWeight {
    const val TWIG_DP = 1.5f
    const val LEAF_DP = 6f
    const val LEAF_MOST_DP = 22f

    fun twigFloor(density: Float, focal: Float): Float = TWIG_DP / 2 * density / max(focal, 1f)

    fun leafFloor(density: Float, focal: Float): Float = LEAF_DP / 2 * density / max(focal, 1f)

    fun leafCeiling(density: Float, focal: Float): Float = LEAF_MOST_DP / 2 * density / max(focal, 1f)
}
