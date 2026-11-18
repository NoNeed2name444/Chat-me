package com.cramdown.sorta.ui.map

import android.opengl.GLES30
import android.util.Log
import com.cramdown.sorta.map.MapFilterView
import com.cramdown.sorta.map.TreeModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max

/**
 * Draws the 3D Tree (S11) with OpenGL ES 3.0: every branch an instance of one tapered tube and every
 * leaf an instance of one flattened ball (TreeModel), lit by one light from the upper left and fading
 * into the page's own colour with depth, by day and by night. Two instanced draws a frame, from
 * buffers made once; a frame only sets uniforms. Which nodes are hidden (the filter) and chosen is one
 * byte a node in a small texture, written only when either changes. A twig is never drawn thinner
 * than [MIN_PIXELS] on screen. Runs on the GL thread only.
 */
class TreeGl(private val model: TreeModel, private val contrast: Boolean) {
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
        val (tv, ti) = TreeModel.tube(model.segments)
        val (bv, bi) = TreeModel.ball(if (model.segments > 8) 8 else 5, model.segments)
        tubeIndices = ti.size; leafIndices = bi.size
        tubeVao = vao(tv, ti, model.branches, TreeModel.BRANCH_FLOATS, intArrayOf(3, 3, 2, 3, 1))
        leafVao = vao(bv, bi, model.leaves, TreeModel.LEAF_FLOATS, intArrayOf(3, 3, 1, 3, 1))
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); stateTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stateTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, WIDTH, rows, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)
        stale = true
    }

    /** One mesh's vertices and triangles, and the instances drawn with it, each attribute [sizes] floats. */
    private fun vao(mesh: FloatArray, indices: ShortArray, instances: FloatArray, stride: Int, sizes: IntArray): Int {
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glGenBuffers(3, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, floats(mesh), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, max(instances.size, 1) * 4, floats(instances), GLES30.GL_STATIC_DRAW)
        var offset = 0
        for ((k, size) in sizes.withIndex()) {
            GLES30.glEnableVertexAttribArray(k + 1)
            GLES30.glVertexAttribPointer(k + 1, size, GLES30.GL_FLOAT, false, stride * 4, offset * 4)
            GLES30.glVertexAttribDivisor(k + 1, 1)
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
        state.clear()
        for (i in 0 until WIDTH * rows) {
            val v = when {
                i >= n -> 0
                f != null && !f.reachable(i) -> { h++; HIDDEN }
                i == chosen -> CHOSEN
                lit != null && i in lit -> LINKED
                else -> 0
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
            GLES30.glUniform1f(u[2], MIN_PIXELS / max(focal, 1f))
            GLES30.glUniform3f(u[3], light[0], light[1], light[2])
            GLES30.glUniform3f(u[4], sky[0], sky[1], sky[2])
            GLES30.glUniform3f(u[5], fogStart, fogEnd, cap)
            GLES30.glUniform3f(u[6], model.accent[0], model.accent[1], model.accent[2])
            GLES30.glUniform1i(u[7], 0)
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
        const val HIDDEN = 1
        const val CHOSEN = 2
        const val LINKED = 3
        /** A branch is never drawn thinner than this many pixels across its radius. */
        const val MIN_PIXELS = 0.75f

        private val UNIFORMS = arrayOf("uView", "uProj", "uPixel", "uLight", "uSky", "uFog", "uAccent", "uState")
        private const val U_COUNT = 8

        private fun floats(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(max(a.size, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.flip() }

        private const val HEAD = """#version 300 es
precision highp float;
uniform mat4 uView;
uniform mat4 uProj;
uniform float uPixel;
uniform highp sampler2D uState;
out vec3 vNormal;
out vec3 vColor;
out float vDepth;
out float vState;
float stateOf(float node) {
    int i = int(node + 0.5);
    return texelFetch(uState, ivec2(i % 1024, i / 1024), 0).r * 255.0;
}
void finish(vec3 p, vec3 n, vec3 color, float state) {
    vec4 eye = uView * vec4(p, 1.0);
    vDepth = -eye.z; vNormal = n; vColor = color; vState = state;
    // a hidden node folds to one point outside the view: nothing is drawn
    gl_Position = (state > 0.5 && state < 1.5) ? vec4(2.0, 2.0, 2.0, 1.0) : uProj * eye;
}
"""

        /** A branch: the unit tube's ring (cos, sin, along) set round the base-to-tip axis, tapering. */
        const val TUBE_VS = HEAD + """
layout(location = 0) in vec3 aRing;
layout(location = 1) in vec3 aBase;
layout(location = 2) in vec3 aTip;
layout(location = 3) in vec2 aRadius;
layout(location = 4) in vec3 aColor;
layout(location = 5) in float aNode;
void main() {
    vec3 axis = aTip - aBase;
    float len = max(length(axis), 1e-6);
    vec3 d = axis / len;
    vec3 helper = abs(d.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 u = normalize(cross(helper, d));
    vec3 v = cross(d, u);
    vec3 n = u * aRing.x + v * aRing.y;
    vec3 c = aBase + axis * aRing.z;
    float depth = max(-(uView * vec4(c, 1.0)).z, 1e-4);
    float r = max(mix(aRadius.x, aRadius.y, aRing.z), uPixel * depth);
    // past the tip by its radius, so a child's joint and a leaf's stalk close
    finish(c + n * r + d * (aRing.z * r), n, aColor, stateOf(aNode));
}
"""

        /** A leaf: the unit ball, long along its twig, narrower across and flat, round its centre. */
        const val LEAF_VS = HEAD + """
layout(location = 0) in vec3 aBall;
layout(location = 1) in vec3 aCentre;
layout(location = 2) in vec3 aAxis;
layout(location = 3) in float aRadius;
layout(location = 4) in vec3 aColor;
layout(location = 5) in float aNode;
void main() {
    vec3 d = normalize(aAxis);
    vec3 helper = abs(d.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 u = normalize(cross(helper, d));
    vec3 v = cross(d, u);
    float depth = max(-(uView * vec4(aCentre, 1.0)).z, 1e-4);
    float r = max(aRadius, uPixel * depth * 2.5);
    vec3 shape = vec3(0.62, 1.0, 0.34);
    vec3 p = aCentre + (u * aBall.x * shape.x + d * aBall.y * shape.y + v * aBall.z * shape.z) * r;
    vec3 n = normalize(u * aBall.x / shape.x + d * aBall.y / shape.y + v * aBall.z / shape.z);
    finish(p, n, aColor, stateOf(aNode));
}
"""

        /** Lambert from one light, both faces, a chosen node toward the accent, then the page's colour with depth. */
        const val LIT_FS = """#version 300 es
precision mediump float;
uniform vec3 uLight;
uniform vec3 uSky;
uniform vec3 uFog;
uniform vec3 uAccent;
in vec3 vNormal;
in vec3 vColor;
in float vDepth;
in float vState;
out vec4 outColor;
void main() {
    vec3 n = normalize(vNormal);
    float lambert = abs(dot(n, normalize(uLight)));
    vec3 c = vColor * (0.52 + 0.58 * lambert);
    if (vState > 2.5) c = mix(c, uAccent, 0.4);
    else if (vState > 1.5) c = mix(c, uAccent, 0.7);
    float f = clamp((vDepth - uFog.x) / max(uFog.y - uFog.x, 1e-3), 0.0, 1.0) * uFog.z;
    outColor = vec4(mix(c, uSky, f), 1.0);
}
"""
    }
}
