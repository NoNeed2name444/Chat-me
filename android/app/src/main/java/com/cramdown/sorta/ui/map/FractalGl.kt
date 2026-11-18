package com.cramdown.sorta.ui.map

import android.opengl.GLES30
import android.util.Log
import com.cramdown.sorta.map.FractalModel
import com.cramdown.sorta.map.FractalRules
import com.cramdown.sorta.map.FractalSight
import com.cramdown.sorta.map.PerfLook
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Draws the 3D Fractal (S12) with OpenGL ES 3.0, as Performance draws its map: Performance's night
 * sky (PerfLook.SKY, by day too), each dot a lit ball facing the camera with a lit hub's glow round it,
 * and hair-thin lines from each dot to its hub and between linked ideas, all added onto the sky. What
 * is drawn is [FractalSight]'s frame: at most the tier's budget of dots, so the cost of a frame is the
 * same for 100 ideas or 100,000. Two instanced draws and one line draw a frame, from buffers made once
 * and filled in place: a frame makes nothing. Runs on the GL thread only.
 */
class FractalGl(val model: FractalModel) {
    val sight = FractalSight(model)
    private var dotProgram = 0
    private var lineProgram = 0
    private var dotVao = 0; private var lineVao = 0
    private var dotVbo = 0; private var lineVbo = 0
    private var failed = false

    private val dots: FloatBuffer = ByteBuffer.allocateDirect(FractalRules.BUDGET * DOT_FLOATS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val lineCap = FractalRules.BUDGET + FractalRules.LINK_BUDGET
    private val lines: FloatBuffer = ByteBuffer.allocateDirect(lineCap * 2 * LINE_FLOATS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val dotU = IntArray(3)
    private val lineU = IntArray(1)
    private val sky = PerfLook.SKY

    /** How many lines the last frame drew, links and tree lines. */
    var lineCount = 0; private set

    fun create() {
        failed = false
        try {
            dotProgram = SpaceGl.buildProgram(DOT_VS, DOT_FS)
            lineProgram = SpaceGl.buildProgram(LINE_VS, LINE_FS)
        } catch (e: IllegalStateException) {
            Log.e("FractalGl", "Fractal shaders: ${e.message}")
            failed = true
            return
        }
        dotU[0] = GLES30.glGetUniformLocation(dotProgram, "uVP")
        dotU[1] = GLES30.glGetUniformLocation(dotProgram, "uViewport")
        dotU[2] = GLES30.glGetUniformLocation(dotProgram, "uGlow")
        lineU[0] = GLES30.glGetUniformLocation(lineProgram, "uVP")
        val ids = IntArray(3)
        // the dots: one quad's corners, and an instance a dot (place, radius in pixels, colour, brightness, hub, ring)
        GLES30.glGenVertexArrays(1, ids, 0); dotVao = ids[0]
        GLES30.glBindVertexArray(dotVao)
        GLES30.glGenBuffers(2, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val cb = ByteBuffer.allocateDirect(corners.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(corners).also { it.flip() }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, cb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        dotVbo = ids[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dotVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, FractalRules.BUDGET * DOT_FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        var offset = 0
        for ((k, size) in intArrayOf(3, 1, 3, 1, 1, 1).withIndex()) {
            GLES30.glEnableVertexAttribArray(k + 1)
            GLES30.glVertexAttribPointer(k + 1, size, GLES30.GL_FLOAT, false, DOT_FLOATS * 4, offset * 4)
            GLES30.glVertexAttribDivisor(k + 1, 1)
            offset += size
        }
        // the lines: two ends a line, each a place and a colour already as bright as it is drawn
        GLES30.glGenVertexArrays(1, ids, 0); lineVao = ids[0]
        GLES30.glBindVertexArray(lineVao)
        GLES30.glGenBuffers(1, ids, 0); lineVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lineVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, lineCap * 2 * LINE_FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, LINE_FLOATS * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, LINE_FLOATS * 4, 12)
        GLES30.glBindVertexArray(0)
    }

    /**
     * One frame: the sky, then [sight]'s frame for the camera's [view], field [fovY] and [aspect], in a
     * view [width] by [height] with [density] pixels a dp and [focal] pixels a unit at depth 1. The near
     * and far planes hug the whole map's ball ([toCentre] from the eye, [reach] round it).
     */
    fun draw(view: FloatArray, fovY: Float, aspect: Float, focal: Float, width: Int, height: Int, density: Float, toCentre: Float, reach: Float) {
        GLES30.glClearColor(sky[0], sky[1], sky[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val near = max(toCentre - reach * 1.15f, max(toCentre, 1e-3f) * 1e-4f).coerceAtLeast(1e-5f)
        val far = max(toCentre + reach * 1.15f, near * 2f)
        perspective(proj, fovY, aspect, near, far)
        multiply(viewProj, proj, view)
        sight.step(viewProj, focal, width.toFloat(), height.toFloat(), density)
        if (failed || dotProgram == 0) return
        fill()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        if (lineCount > 0) {
            GLES30.glUseProgram(lineProgram)
            GLES30.glUniformMatrix4fv(lineU[0], 1, false, viewProj, 0)
            GLES30.glBindVertexArray(lineVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lineVbo)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, lineCount * 2 * LINE_FLOATS * 4, lines)
            GLES30.glLineWidth(1f)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, lineCount * 2)
        }
        if (sight.count > 0) {
            GLES30.glUseProgram(dotProgram)
            GLES30.glUniformMatrix4fv(dotU[0], 1, false, viewProj, 0)
            GLES30.glUniform2f(dotU[1], width.toFloat(), height.toFloat())
            GLES30.glUniform1f(dotU[2], FractalSight.GLOW_REACH)
            GLES30.glBindVertexArray(dotVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dotVbo)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, sight.count * DOT_FLOATS * 4, dots)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, sight.count)
        }
        GLES30.glBindVertexArray(0)
    }

    /** The frame's dots and lines into their buffers, in place. */
    private fun fill() {
        val s = sight
        val pos = model.layout.pos
        val tones = model.tones
        dots.clear()
        for (k in 0 until s.count) {
            val i = s.node[k]
            dots.put(pos[3 * i].toFloat()); dots.put(pos[3 * i + 1].toFloat()); dots.put(pos[3 * i + 2].toFloat())
            dots.put(s.radius[k])
            // a lit hub's ball is its colour mixed toward white (Performance's 0.45), an idea's its own
            val h = s.hub[k]
            val c = tones.dot[i]; val b = tones.hub[i]
            dots.put(mixByte(c shr 16, b shr 16, h)); dots.put(mixByte(c shr 8, b shr 8, h)); dots.put(mixByte(c, b, h))
            dots.put(s.alpha[k]); dots.put(h); dots.put(if (i == s.chosen) 1f else 0f)
        }
        dots.flip()
        lines.clear()
        var count = 0
        for (k in 0 until s.count) {
            val p = s.parentSlot(k)
            if (p < 0) continue
            putLine(p, k, s.alpha[k] * FractalSight.TREE_LINE, tones.dot[s.node[k]])
            count++
        }
        for (e in 0 until s.linkCount) {
            putLine(s.linkFrom[e], s.linkTo[e], s.linkAlpha[e], tones.dot[s.node[s.linkFrom[e]]])
            count++
        }
        lines.flip()
        lineCount = count
    }

    private fun putLine(a: Int, b: Int, alpha: Float, rgb: Int) {
        val pos = model.layout.pos
        val r = ((rgb shr 16) and 255) / 255f * alpha; val g = ((rgb shr 8) and 255) / 255f * alpha; val bl = (rgb and 255) / 255f * alpha
        for (k in intArrayOfTwo(a, b)) {
            val i = sight.node[k]
            lines.put(pos[3 * i].toFloat()); lines.put(pos[3 * i + 1].toFloat()); lines.put(pos[3 * i + 2].toFloat())
            lines.put(r); lines.put(g); lines.put(bl)
        }
    }

    private val two = IntArray(2)
    private fun intArrayOfTwo(a: Int, b: Int): IntArray { two[0] = a; two[1] = b; return two }

    private fun mixByte(a: Int, b: Int, t: Float): Float = ((a and 255) + ((b and 255) - (a and 255)) * t) / 255f

    private fun perspective(out: FloatArray, fovY: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / kotlin.math.tan(fovY / 2)
        out.fill(0f)
        out[0] = f / aspect; out[5] = f
        out[10] = (far + near) / (near - far); out[11] = -1f
        out[14] = 2f * far * near / (near - far)
    }

    /** [a] times [b] into [out], column-major as GL's, making nothing. */
    private fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = sum
        }
    }

    companion object {
        const val DOT_FLOATS = 10
        const val LINE_FLOATS = 6

        /** A dot: its quad set round its place on screen, its radius in pixels, room for a hub's glow. */
        const val DOT_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec3 aPos;
layout(location = 2) in float aRadius;
layout(location = 3) in vec3 aColor;
layout(location = 4) in float aAlpha;
layout(location = 5) in float aHub;
layout(location = 6) in float aRing;
uniform mat4 uVP;
uniform vec2 uViewport;
uniform float uGlow;
out vec2 vAt;
out vec3 vColor;
out float vHub;
out float vRing;
out float vSize;
void main() {
    vec4 c = uVP * vec4(aPos, 1.0);
    float reach = 1.0 + uGlow * aHub;
    vAt = aCorner * reach;
    vColor = aColor * aAlpha;
    vHub = aHub;
    vRing = aRing;
    vSize = aRadius;
    vec2 offset = aCorner * aRadius * reach * 2.0 / uViewport;
    gl_Position = c.w <= 1e-4 ? vec4(2.0, 2.0, 2.0, 1.0) : vec4(c.xy + offset * c.w, 0.0, c.w);
}
"""

        /** Performance's lit point: a ball lit from the upper left, a soft glow round a lit hub, a white ring when chosen. */
        const val DOT_FS = """#version 300 es
precision mediump float;
in vec2 vAt;
in vec3 vColor;
in float vHub;
in float vRing;
in float vSize;
out vec4 fragColor;
void main() {
    float r2 = dot(vAt, vAt);
    float r = sqrt(r2);
    float core = 1.0 - smoothstep(0.86, 1.0, r);
    float z = sqrt(max(0.0, 1.0 - r2));
    float lit = mix(1.0, 0.45 + 0.55 * z + 0.35 * pow(max(0.0, dot(normalize(vec3(vAt, z + 1e-3)), normalize(vec3(-0.4, 0.5, 0.8)))), 12.0),
        smoothstep(3.0, 10.0, vSize));
    float glow = vHub * 0.55 * exp(-r2 * 1.6);
    vec3 col = vColor * (core * lit * 0.9 + glow);
    if (vRing > 0.5) col = mix(col, vec3(1.0), smoothstep(0.66, 0.74, r) * (1.0 - smoothstep(0.86, 0.94, r)));
    if (core <= 0.0 && glow < 0.004) discard;
    fragColor = vec4(col, 1.0);
}
"""

        const val LINE_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aColor;
uniform mat4 uVP;
out vec3 vColor;
void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    vColor = aColor;
}
"""

        const val LINE_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
out vec4 fragColor;
void main() { fragColor = vec4(vColor, 1.0); }
"""
    }
}
