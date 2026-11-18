package com.cramdown.sorta.map

import com.cramdown.sorta.map.nest.*

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The Tree look's frame (S11), the approved concept's: the camera and its zoom (a spiral
// similarity, time based), which parts are drawn (the biggest on screen first, up to a budget,
// each fading in from about 4 px; a whole subtree off screen is skipped), their names (never
// overlapping, 8 apart side by side and 4 above and below; a bough's name on the bough itself),
// the dimming of all but the part in view, the vines, taps, pinches and Back. Pure Kotlin, in the
// view's own units (dp on Android, points on iOS), drawing through a [NestSink], so iOS's
// TreeScene.swift is the same line for line, and the tests time it and count what it draws.
// Nothing is allocated per frame: every array is made when the tree is.

class TreeScene(val nodes: NestNodes, val layout: TreeLayout, val links: NestLinks = NestLinks.NONE) {
    private val n = nodes.size
    private val k = TreeShape.K
    private val par = nodes.parent
    private val cc = nodes.childCount
    private val dep = nodes.depth
    private val grp = nodes.group
    private val lx = layout.px
    private val ly = layout.py

    // ---- what the platform sets
    /** Whether motion is reduced: a zoom is a cut. */
    var reduced = false
    /** The view's size and what covers it (the strip above, the tools below), in its units. */
    var viewW = 1f; private set
    var viewH = 1f; private set
    var insetTop = 0f; private set
    var insetBottom = 0f; private set
    var insetLeft = 0f; private set
    var insetRight = 0f; private set
    /** The least touch target, each way: 44 on iOS, 48 on Android. */
    var target = 44f
    /** Each name's width at 100 units, upright ([widths][0]) and bold ([widths][1]), measured by the platform. */
    val widths = arrayOf(FloatArray(n), FloatArray(n))
    var colors = TreeColors(false)
    /** The idea chosen (its ring), or -1. */
    var chosen = -1
    /** Show links: only this node's vines, bright; -1 for every vine, faint. */
    var linksOf = -1
    /** What the filter and the lenses leave out: a hidden part, its subtree and its vines aren't drawn. */
    val hidden = BooleanArray(n)

    // ---- the camera and where it is
    val cam = NestCamera()
    /** The part in view: Back leaves it for its parent. */
    var focus = 0; private set
    private var fromFocus = 0
    private var progress = 1.0
    var animating = false; private set
    private var zMin = 1e-9
    private var zMax = 1e9

    // ---- one frame's state
    private var frame = 0
    private var tc = 1f; private var ts = 0f; private var tox = 0f; private var toy = 0f; private var tz = 1f
    private var cr = 1f; private var sr = 0f
    private val sx = FloatArray(n); private val sy = FloatArray(n); private val lp = FloatArray(n); private val al = FloatArray(n)
    private val stamp = IntArray(n); private val open = BooleanArray(n)
    private val drawn = IntArray(n)
    private val inFrame = IntArray(n)
    /** How many parts the last frame drew. */
    var drawnCount = 0; private set
    private val heapNode = IntArray(n); private val heapKey = FloatArray(n); private var heapSize = 0
    private val cand = IntArray(n); private val candKey = FloatArray(n); private var candCount = 0
    private val rx = ByteArray(n)
    private val used = IntArray(NestNodes.GROUPS)
    private var hi = 0; private var hi2 = 0; private var hu = 1f; private var lhi = 0

    // ---- the names: where each went (a box), or an obstacle (node -1)
    private val maxLabels = 120
    var labelCount = 0; private set
    private val labX = FloatArray(maxLabels); private val labY = FloatArray(maxLabels)
    private val lb0 = FloatArray(maxLabels); private val lb1 = FloatArray(maxLabels); private val lb2 = FloatArray(maxLabels); private val lb3 = FloatArray(maxLabels)
    private val labSize = FloatArray(maxLabels); private val labBold = BooleanArray(maxLabels); private val labAlign = IntArray(maxLabels)
    private val labNode = IntArray(maxLabels)

    // ---- the zoom in flight
    private var aT0 = 0.0; private var aDur = 0.0; private var aLz0 = 0.0; private var aLz1 = 0.0; private var aR0 = 0.0; private var aR1 = 0.0
    private var aC0x = 0.0; private var aC0y = 0.0; private var aC1x = 0.0; private var aC1y = 0.0
    private var aPx = 0.0; private var aPy = 0.0; private var aQx = 0.0; private var aQy = 0.0; private var aLin = false

    private var sized = false
    private val tip = DoubleArray(2)
    private val scratch = NestCamera()

    /** The view's size and insets; the first call frames the part in view, later ones keep it framed while still. */
    fun setView(w: Float, h: Float, top: Float, bottom: Float, left: Float = 0f, right: Float = 0f) {
        val first = !sized
        sized = true
        viewW = max(1f, w); viewH = max(1f, h); insetTop = top; insetBottom = bottom; insetLeft = left; insetRight = right
        frameOf(0, scratch)
        zMin = scratch.z * 0.35; zMax = scratch.z * 1e5
        if (first || !animating) { frameOf(focus, scratch); cam.set(scratch) }
    }

    // ---------------------------------------------------------------- the camera

    /** The camera that shows [i]'s whole subtree, upright along its own branch, inside the insets. */
    fun frameOf(i: Int, out: NestCamera): NestCamera {
        val padT = insetTop + 12f; val padB = insetBottom + 26f; val padL = insetLeft + 22f; val padR = insetRight + 22f
        val aw = max(40f, viewW - padL - padR).toDouble(); val ah = max(40f, viewH - padT - padB).toDouble()
        val a = layout.angle[i]
        var r = -Math.PI / 2 - a
        r = atan2(sin(r), cos(r))
        val du = layout.u1[i] - layout.u0[i]; val dv = layout.v1[i] - layout.v0[i]
        val z = min(aw / dv, ah / du)
        val uc = (layout.u0[i] + layout.u1[i]) / 2; val vc = (layout.v0[i] + layout.v1[i]) / 2
        val ca = cos(a); val sa = sin(a)
        val wx = layout.bx[i] + uc * ca - vc * sa; val wy = layout.by[i] + uc * sa + vc * ca
        val ddx = padL + aw / 2 - viewW / 2.0; val ddy = padT + ah / 2 - viewH / 2.0
        val c = cos(r); val s = sin(r)
        out.x = wx - (ddx * c + ddy * s) / z; out.y = wy - (-ddx * s + ddy * c) / z; out.z = z; out.r = r
        return out
    }

    /** Opens [i]: the camera goes to it, eased unless motion is reduced or [instant]. */
    fun go(i: Int, now: Double, instant: Boolean = false) {
        fromFocus = if (instant) i else focus
        focus = i; progress = 0.0
        frameOf(i, scratch)
        if (instant || reduced) { cam.x = scratch.x; cam.y = scratch.y; cam.z = scratch.z; cam.r = cam.r + wrap(scratch.r - cam.r); animating = false; progress = 1.0; return }
        startAnim(scratch, now)
    }

    /** Back: the part in view's parent, if it isn't the whole tree. Whether it went. */
    fun back(now: Double): Boolean {
        if (focus <= 0) return false
        go(par[focus], now); return true
    }

    private fun wrap(a: Double) = atan2(sin(a), cos(a))

    // a spiral similarity: zoom, turn and slide about the one point both views share
    private fun startAnim(to: NestCamera, now: Double) {
        val dr = wrap(to.r - cam.r)
        aC0x = cam.x; aC0y = cam.y; aLz0 = ln(cam.z); aR0 = cam.r; aC1x = to.x; aC1y = to.y; aLz1 = ln(to.z); aR1 = aR0 + dr
        val a0r = cam.z * cos(aR0); val a0i = cam.z * sin(aR0); val a1r = to.z * cos(aR1); val a1i = to.z * sin(aR1)
        val dRe = a0r - a1r; val dIm = a0i - a1i; val dd = dRe * dRe + dIm * dIm
        aLin = dd < 1e-6 * (a0r * a0r + a0i * a0i)
        if (!aLin) {
            val nr = aC0x * a0r - aC0y * a0i - (aC1x * a1r - aC1y * a1i); val ni = aC0x * a0i + aC0y * a0r - (aC1x * a1i + aC1y * a1r)
            aPx = (nr * dRe + ni * dIm) / dd; aPy = (ni * dRe - nr * dIm) / dd
            val ux = aPx - aC0x; val uy = aPy - aC0y
            aQx = ux * a0r - uy * a0i; aQy = ux * a0i + uy * a0r
        }
        aDur = clamp(460 + 150 * abs(aLz1 - aLz0) / ln(2.0) + 160 * abs(dr), 420.0, 1250.0)
        aT0 = now; animating = true
    }

    /** Moves a zoom in flight to [now] (ms); its last step lands exactly. */
    fun step(now: Double) {
        if (!animating) return
        var t = (now - aT0) / aDur
        if (t >= 1) { t = 1.0; animating = false }
        if (t < 0) t = 0.0
        val u = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        progress = u
        val z = exp(aLz0 + (aLz1 - aLz0) * u); val r = aR0 + (aR1 - aR0) * u
        cam.z = z; cam.r = r
        if (aLin) { cam.x = aC0x + (aC1x - aC0x) * u; cam.y = aC0y + (aC1y - aC0y) * u } else {
            val ar = z * cos(r); val ai = z * sin(r); val m = ar * ar + ai * ai
            cam.x = aPx - (aQx * ar + aQy * ai) / m; cam.y = aPy - (aQy * ar - aQx * ai) / m
        }
        if (!animating) { cam.x = aC1x; cam.y = aC1y; cam.z = exp(aLz1); cam.r = aR1; progress = 1.0 }
    }

    /** A pinch about [px], [py] by [f]; it stops a zoom in flight. */
    fun zoomAbout(px: Float, py: Float, f: Float) {
        animating = false; progress = 1.0
        val c = cos(cam.r); val s = sin(cam.r); val dx = px - viewW / 2.0; val dy = py - viewH / 2.0
        val wx = cam.x + (dx * c + dy * s) / cam.z; val wy = cam.y + (-dx * s + dy * c) / cam.z
        cam.z = clamp(cam.z * f, zMin, zMax)
        cam.x = wx - (dx * c + dy * s) / cam.z; cam.y = wy - (-dx * s + dy * c) / cam.z
    }

    fun panBy(dx: Float, dy: Float) {
        animating = false; progress = 1.0
        val c = cos(cam.r); val s = sin(cam.r)
        cam.x -= (dx * c + dy * s) / cam.z; cam.y -= (-dx * s + dy * c) / cam.z
    }

    /** After a pinch or a drag: the deepest part that fills the view becomes the one Back leaves from. */
    fun settle() {
        val c = cos(cam.r); val s = sin(cam.r)
        val padT = insetTop + 12f; val padB = insetBottom + 26f; val padL = insetLeft + 22f; val padR = insetRight + 22f
        val dx = padL + (viewW - padL - padR) / 2.0 - viewW / 2.0; val dy = padT + (viewH - padT - padB) / 2.0 - viewH / 2.0
        val wx = cam.x + (dx * c + dy * s) / cam.z; val wy = cam.y + (-dx * s + dy * c) / cam.z
        var i = 0
        while (true) {
            var best = -1; var bd = 1e18
            for (q in 0 until cc[i]) {
                val ch = nodes.child(i, q)
                if (cc[ch] == 0) continue
                frameOf(ch, scratch)
                if (scratch.z > cam.z * 1.3) continue
                val ddx = wx - layout.bcx[ch]; val ddy = wy - layout.bcy[ch]; val dd = ddx * ddx + ddy * ddy
                if (dd > layout.brad[ch] * layout.brad[ch]) continue
                if (dd < bd) { bd = dd; best = ch }
            }
            if (best < 0) break
            i = best
        }
        focus = i; fromFocus = i; progress = 1.0
    }

    /**
     * A part tapped or activated: a part with parts of its own fills the screen; an idea opens the
     * part it grows on. Returns where it went, or -1 when nothing moved.
     */
    fun activate(i: Int, now: Double): Int {
        if (animating || i < 0) return -1
        val t = if (cc[i] > 0) (if (i != focus) i else -1) else (if (par[i] != focus) par[i] else -1)
        if (t < 0) return -1
        go(t, now); return t
    }

    // ---------------------------------------------------------------- one frame

    private fun xf() {
        tz = cam.z.toFloat(); cr = cos(cam.r).toFloat(); sr = sin(cam.r).toFloat()
        tc = cr * tz; ts = sr * tz
        tox = (viewW / 2.0 - (cam.x * cam.z * cos(cam.r) - cam.y * cam.z * sin(cam.r))).toFloat()
        toy = (viewH / 2.0 - (cam.x * cam.z * sin(cam.r) + cam.y * cam.z * cos(cam.r))).toFloat()
    }
    private fun scrX(x: Double, y: Double): Float = (x * tc - y * ts).toFloat() + tox
    private fun scrY(x: Double, y: Double): Float = (x * ts + y * tc).toFloat() + toy

    private fun inside(i: Int): Boolean = lhi <= 0 || nodes.within(lhi, i)
    /** How bright [i] is: the part in view full, the rest dimmed, crossing over while the camera moves. */
    fun dimOf(i: Int): Float {
        if (hi <= 0 && hi2 <= 0) return 1f
        val b = if (hi <= 0 || nodes.within(hi, i)) 1f else TreeColors.DIM.toFloat()
        if (hu >= 1f) return b
        val a = if (hi2 <= 0 || nodes.within(hi2, i)) 1f else TreeColors.DIM.toFloat()
        return a + (b - a) * hu
    }
    // names: the part in view and two levels below it; deeper names wait for the next zoom
    private fun nameOK(i: Int, minLp: Float): Boolean {
        if (i == 0 || al[i] < 0.6f || !inside(i)) return false
        val rel = dep[i] - (if (lhi > 0) dep[lhi] else 0)
        return if (rel <= 1) lp[i] >= minLp else if (rel == 2) lp[i] >= minLp * 1.5f else false
    }
    private fun res(i0: Int): Int { var i = i0; while (stamp[i] != frame) i = par[i]; return i }
    private fun offscreen(x: Float, y: Float) = x < -20 || x > viewW + 20 || y < -20 || y > viewH + 20

    private fun hpush(i: Int, key: Float) {
        var q = heapSize++
        while (q > 0) { val p = (q - 1) shr 1; if (heapKey[p] >= key) break; heapNode[q] = heapNode[p]; heapKey[q] = heapKey[p]; q = p }
        heapNode[q] = i; heapKey[q] = key
    }
    private fun hpop(): Int {
        val top = heapNode[0]; heapSize--
        val last = heapNode[heapSize]; val lk = heapKey[heapSize]
        var q = 0
        while (true) {
            var c = 2 * q + 1
            if (c >= heapSize) break
            if (c + 1 < heapSize && heapKey[c + 1] > heapKey[c]) c++
            if (heapKey[c] <= lk) break
            heapNode[q] = heapNode[c]; heapKey[q] = heapKey[c]; q = c
        }
        if (heapSize > 0) { heapNode[q] = last; heapKey[q] = lk }
        return top
    }
    // the part's anchor on screen, and its length there, or -1 when its whole subtree is off screen
    private fun stampB(i: Int): Float {
        val o = i * k + ANCHOR
        sx[i] = scrX(lx[o], ly[o]); sy[i] = scrY(lx[o], ly[o]); stamp[i] = frame; open[i] = false
        val x = scrX(layout.bcx[i], layout.bcy[i]); val y = scrY(layout.bcx[i], layout.bcy[i]); val r = (layout.brad[i] * tz).toFloat()
        if (x + r < -30 || x - r > viewW + 30 || y + r < -30 || y - r > viewH + 30) return -1f
        lp[i] = (layout.length[i] * tz).toFloat()
        return lp[i]
    }
    // the biggest parts on screen first, up to a fixed budget, so a deep tree costs no more than a shallow one
    private fun visit() {
        heapSize = 0
        if (stampB(0) >= MIN_LP) hpush(0, lp[0])
        val fade0 = BUDGET * 0.75f
        while (heapSize > 0 && drawnCount < BUDGET) {
            val i = hpop(); val l = lp[i]
            al[i] = sm(MIN_LP, FADE_LP, l) * (if (drawnCount > fade0) 1 - sm(fade0, BUDGET.toFloat(), drawnCount.toFloat()) else 1f)
            drawn[drawnCount++] = i; inFrame[i] = frame
            val count = cc[i]
            if (count > 0 && l > OPEN_LP) {
                open[i] = true
                for (q in 0 until count) { val c = nodes.child(i, q); if (!hidden[c] && stampB(c) >= MIN_LP) hpush(c, lp[c]) }
            }
        }
    }

    /**
     * Works out one frame at [now] (ms) and draws it into [sink]: the zoom moved on, the parts
     * chosen, the names placed, the sky left to the caller. With [names] false, no names (a still
     * of the shape alone).
     */
    fun draw(sink: NestSink?, now: Double, names: Boolean = true) {
        step(now)
        frame++; drawnCount = 0; labelCount = 0
        hi = focus; hi2 = fromFocus; hu = progress.toFloat(); lhi = if (hu > 0.5f) hi else hi2
        xf()
        visit()
        prepRx()
        if (names) labels()
        if (sink == null) return
        vines(sink)
        for (pass in 0..1) {
            fillParts(sink, pass)
            thinBatch(sink, pass)
            leaves(sink, pass)
        }
        val ch = chosen
        if (ch >= 0 && ch < n && stamp[ch] == frame) ring(sink, sx[ch], sy[ch], 10f)
        if (names) for (q in 0 until labelCount) {
            val node = labNode[q]
            if (node < 0) continue
            sink.text(node, labX[q], labY[q], labSize[q], labBold[q], labAlign[q], colors.ink, colors.halo, 3.2f)
        }
    }

    // ---------------------------------------------------------------- the names

    private fun fits(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        if (x0 < 4 || x1 > viewW - 4 || y0 < insetTop + 2 || y1 > viewH - insetBottom - 4) return false
        for (q in 0 until labelCount) {   // 8 between names side by side and 4 above and below
            val gx = if (labNode[q] < 0) 1f else GAP_X; val gy = if (labNode[q] < 0) 1f else GAP_Y
            if (x0 < lb2[q] + gx && x1 > lb0[q] - gx && y0 < lb3[q] + gy && y1 > lb1[q] - gy) return false
        }
        return true
    }
    private fun put(i: Int, x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float, size: Float, bold: Boolean, align: Int): Boolean {
        if (labelCount >= maxLabels || !fits(x0, y0, x1, y1)) return false
        val q = labelCount++
        labX[q] = x; labY[q] = y; lb0[q] = x0; lb1[q] = y0; lb2[q] = x1; lb3[q] = y1; labSize[q] = size; labBold[q] = bold; labAlign[q] = align; labNode[q] = i
        return true
    }
    private fun sortCand() {
        for (a in 1 until candCount) {
            val ki = cand[a]; val kv = candKey[a]; var b = a - 1
            while (b >= 0 && candKey[b] < kv) { cand[b + 1] = cand[b]; candKey[b + 1] = candKey[b]; b-- }
            cand[b + 1] = ki; candKey[b + 1] = kv
        }
    }
    // a bough's name sits on the bough itself, so it never reads as a neighbour's; a twig's at its leaf
    private fun labels() {
        candCount = 0
        for (q in 0 until drawnCount) {
            val i = drawn[q]
            if (!nameOK(i, NAME_LP)) continue
            cand[candCount] = i; candKey[candCount] = lp[i] - dep[i] * 1000f; candCount++
        }
        sortCand()
        var c = 0
        while (c < candCount && labelCount < maxLabels) {
            val nd = cand[c++]; val l = lp[nd]; val isOpen = open[nd] && cc[nd] > 0
            val size = clamp(floor(10.5f + l * 0.02f + 0.5f), 11f, 18f)
            val w = widths[if (isOpen) 1 else 0][nd] * size / 100f; val h = size * 1.2f
            val o = nd * k
            val bX = scrX(lx[o], ly[o]); val tX = scrX(lx[o + k - 1], ly[o + k - 1])
            if (isOpen) {
                for (t in ON_BOUGH) {
                    val cX = scrX(lx[o + t], ly[o + t]); val cY = scrY(lx[o + t], ly[o + t])
                    if (put(nd, cX, cY, cX - w / 2, cY - h / 2, cX + w / 2, cY + h / 2, size, true, 2)) break
                }
                continue
            }
            val j = k - 1
            var aX = scrX(lx[o + j], ly[o + j]); var aY = scrY(lx[o + j], ly[o + j])
            if (cc[nd] == 0) {
                val tY = scrY(lx[o + k - 1], ly[o + k - 1]); val pX = scrX(lx[o + k - 2], ly[o + k - 2]); val pY = scrY(lx[o + k - 2], ly[o + k - 2])
                val ang = atan2(tY - pY, tX - pX); val ll = clamp((layout.length[nd] * TreeShape.LEAF * tz).toFloat(), 2.2f, 30f)
                aX += cos(ang) * ll; aY += sin(ang) * ll
            }
            val gap = 3f + 5f
            if (tX >= bX) {
                if (!put(nd, aX + gap, aY, aX + gap, aY - h / 2, aX + gap + w, aY + h / 2, size, false, 0))
                    put(nd, aX - gap, aY, aX - gap - w, aY - h / 2, aX - gap, aY + h / 2, size, false, 1)
            } else {
                if (!put(nd, aX - gap, aY, aX - gap - w, aY - h / 2, aX - gap, aY + h / 2, size, false, 1))
                    put(nd, aX + gap, aY, aX + gap, aY - h / 2, aX + gap + w, aY + h / 2, size, false, 0)
            }
        }
    }

    /** The [q]th name's node and box (left, top, right, bottom), for the parts TalkBack and VoiceOver reach. */
    fun labelNode(q: Int): Int = labNode[q]
    fun labelBox(q: Int, out: FloatArray) { out[0] = lb0[q]; out[1] = lb1[q]; out[2] = lb2[q]; out[3] = lb3[q] }
    /** Where the [q]th name is drawn from, and how: its anchor, size, weight and alignment, for the tests. */
    fun labelSize(q: Int): Float = labSize[q]
    /** Whether the [q]th name is a bough's, bold and on the bough. */
    fun labelBold(q: Int): Boolean = labBold[q]
    /** Point [j] of [i]'s branch on screen, as the last frame placed it. */
    fun pointX(i: Int, j: Int): Float = scrX(lx[i * k + j], ly[i * k + j])
    fun pointY(i: Int, j: Int): Float = scrY(lx[i * k + j], ly[i * k + j])

    /** Whether [i] was drawn in the last frame, and where its anchor was. */
    fun isDrawn(i: Int): Boolean = i in 0 until n && inFrame[i] == frame
    fun anchorX(i: Int): Float = sx[i]
    fun anchorY(i: Int): Float = sy[i]

    // ---------------------------------------------------------------- touch

    /** The part at [px], [py]: a name answers anywhere in a box [target] each way at least, the nearest winning; then the nearest branch within reach. */
    fun hitTest(px: Float, py: Float): Int {
        var bk = -1; var bd = 1e9f
        val half = target / 2
        for (q in 0 until labelCount) {
            if (labNode[q] < 0) continue
            val hx = max((lb2[q] - lb0[q]) / 2 + 4, half); val hy = max((lb3[q] - lb1[q]) / 2 + 6, half)
            val ux = abs(px - (lb0[q] + lb2[q]) / 2) / hx; val uy = abs(py - (lb1[q] + lb3[q]) / 2) / hy
            val dd = if (ux > uy) ux else uy
            if (dd <= 1 && dd < bd) { bd = dd; bk = q }
        }
        if (bk >= 0) return labNode[bk]
        var best = -1; var bs = 1e9f
        for (m in 0 until drawnCount) {
            val i = drawn[m]
            if (al[i] < 0.3f) continue
            val o = i * k; var d = 1e9f
            for (j in 0 until k - 1) {
                val x0 = scrX(lx[o + j], ly[o + j]); val y0 = scrY(lx[o + j], ly[o + j])
                val x1 = scrX(lx[o + j + 1], ly[o + j + 1]); val y1 = scrY(lx[o + j + 1], ly[o + j + 1])
                val vx = x1 - x0; val vy = y1 - y0; val wl = vx * vx + vy * vy
                val t = if (wl > 0) clamp(((px - x0) * vx + (py - y0) * vy) / wl, 0f, 1f) else 0f
                val e = hypot(px - x0 - vx * t, py - y0 - vy * t)
                if (e < d) d = e
            }
            if (cc[i] == 0) {   // a leaf answers on itself too
                layout.leafTip(i, tip)
                val e = hypot(px - scrX(tip[0], tip[1]), py - scrY(tip[0], tip[1]))
                if (e < d) d = e
            }
            val thr = max((layout.width[i] * tz * 0.5).toFloat() + 6, half)
            if (d > thr) continue
            val s = d / thr - dep[i] * 0.02f
            if (s < bs) { bs = s; best = i }
        }
        return best
    }

    // ---------------------------------------------------------------- drawing

    private fun polyline(sink: NestSink, o: Int) {
        for (j in 0 until k) {
            val x = scrX(lx[o + j], ly[o + j]); val y = scrY(lx[o + j], ly[o + j])
            if (j == 0) sink.moveTo(x, y) else sink.lineTo(x, y)
        }
    }
    // a part's outline, from hw0 at its base down to hw0 * (1 - tp) at its tip
    private fun taperPath(sink: NestSink, i: Int, hw0: Float, tp: Float) {
        val o = i * k
        for (j in 0 until k) {
            val p = o + j; val x = scrX(lx[p], ly[p]); val y = scrY(lx[p], ly[p])
            val nX = (layout.nx[p] * cr - layout.ny[p] * sr).toFloat(); val nY = (layout.nx[p] * sr + layout.ny[p] * cr).toFloat()
            val hw = hw0 * (1 - tp * j / (k - 1))
            if (j == 0) sink.moveTo(x + nX * hw, y + nY * hw) else sink.lineTo(x + nX * hw, y + nY * hw)
        }
        for (j in k - 1 downTo 0) {
            val p = o + j; val x = scrX(lx[p], ly[p]); val y = scrY(lx[p], ly[p])
            val nX = (layout.nx[p] * cr - layout.ny[p] * sr).toFloat(); val nY = (layout.nx[p] * sr + layout.ny[p] * cr).toFloat()
            val hw = hw0 * (1 - tp * j / (k - 1))
            sink.lineTo(x - nX * hw, y - nY * hw)
        }
        sink.close()
    }
    private fun ellipse(sink: NestSink, cx: Float, cy: Float, rx: Float, ry: Float, a: Float) {
        val ca = cos(a); val sa = sin(a); val kx = rx * KAPPA; val ky = ry * KAPPA
        // the four quarter arcs, in the ellipse's own axes turned by a
        fun X(u: Float, v: Float) = cx + u * ca - v * sa
        fun Y(u: Float, v: Float) = cy + u * sa + v * ca
        sink.moveTo(X(rx, 0f), Y(rx, 0f))
        sink.cubicTo(X(rx, ky), Y(rx, ky), X(kx, ry), Y(kx, ry), X(0f, ry), Y(0f, ry))
        sink.cubicTo(X(-kx, ry), Y(-kx, ry), X(-rx, ky), Y(-rx, ky), X(-rx, 0f), Y(-rx, 0f))
        sink.cubicTo(X(-rx, -ky), Y(-rx, -ky), X(-kx, -ry), Y(-kx, -ry), X(0f, -ry), Y(0f, -ry))
        sink.cubicTo(X(kx, -ry), Y(kx, -ry), X(rx, -ky), Y(rx, -ky), X(rx, 0f), Y(rx, 0f))
        sink.close()
    }

    // dimmed parts are drawn first, in a solid mix toward the sky, so overlaps leave no seams
    private fun rix(i: Int): Int {
        val d = dimOf(i)
        if (d >= 0.999f) return 8
        val s = floor((d - TreeColors.DIM.toFloat()) / (1 - TreeColors.DIM.toFloat()) * 8 + 0.5f).toInt()
        return if (s < 0) 0 else if (s > 7) 7 else s
    }
    private fun prepRx() {
        for (g in 0 until NestNodes.GROUPS) used[g] = 0
        for (q in 0 until drawnCount) { val i = drawn[q]; val s = rix(i); rx[i] = s.toByte(); used[grp[i]] = used[grp[i]] or (1 shl s) }
    }
    private fun inPass(i: Int, pass: Int) = (rx[i].toInt() == 8) == (pass == 1)
    private fun noDim() = hi <= 0 && hi2 <= 0

    // every part of one colour goes into one path and one fill; parts still fading in go one by one
    private fun fillParts(sink: NestSink, pass: Int) {
        if (pass == 0 && noDim()) return
        val ramps = colors.barkRamp
        for (g in 0 until NestNodes.GROUPS) for (s in (if (pass == 1) 8 else 0)..(if (pass == 1) 8 else 7)) {
            if (used[g] and (1 shl s) == 0) continue
            var any = false
            for (q in 0 until drawnCount) {
                val i = drawn[q]
                if (grp[i] != g || rx[i].toInt() != s || al[i] < 0.98f) continue
                val hw = (layout.width[i] * tz * 0.5).toFloat(); if (hw < 0.6f) continue
                taperPath(sink, i, hw, 0.45f); any = true
            }
            if (!any) continue
            sink.fill(ramps[g][s], 1f)
            any = false
            for (q in 0 until drawnCount) {
                val i = drawn[q]
                if (grp[i] != g || rx[i].toInt() != s || al[i] < 0.98f) continue
                val hw = (layout.width[i] * tz * 0.5).toFloat(); if (hw < 2.5f) continue
                val o = i * k
                ellipse(sink, scrX(lx[o], ly[o]), scrY(lx[o], ly[o]), hw, hw, 0f); any = true
            }
            if (any) sink.fill(ramps[g][s], 1f)
        }
        for (q in 0 until drawnCount) {
            val i = drawn[q]
            if (al[i] >= 0.98f || !inPass(i, pass)) continue
            val hw = (layout.width[i] * tz * 0.5).toFloat(); if (hw < 0.6f) continue
            taperPath(sink, i, hw, 0.45f)
            sink.fill(ramps[grp[i]][rx[i].toInt()], al[i])
        }
    }
    // thin parts as hairlines, one path per colour
    private fun thinBatch(sink: NestSink, pass: Int) {
        if (pass == 0 && noDim()) return
        for (g in 0 until NestNodes.GROUPS) {
            var any = false
            for (q in 0 until drawnCount) {
                val i = drawn[q]
                if (grp[i] != g || layout.width[i] * tz >= 1.2 || !inPass(i, pass)) continue
                polyline(sink, i * k); any = true
            }
            if (any) sink.stroke(colors.barkRamp[g][if (pass == 1) 8 else 0], 1f, 0.85f, false)
        }
    }
    // leaves at the tips; a closed twig wears a small round of leaves
    private var lgx = 0f; private var lgy = 0f; private var lga = 0f; private var lgl = 0f
    private fun leafGeom(i: Int) {
        val o = i * k + k - 1
        lgx = scrX(lx[o], ly[o]); lgy = scrY(lx[o], ly[o])
        lga = atan2(lgy - scrY(lx[o - 1], ly[o - 1]), lgx - scrX(lx[o - 1], ly[o - 1]))
        lgl = clamp((layout.length[i] * TreeShape.LEAF * tz).toFloat(), 2.2f, 30f)
    }
    private fun leafPath(sink: NestSink, i: Int, blob: Boolean) {
        leafGeom(i)
        if (!blob) {
            val ca = cos(lga); val sa = sin(lga)
            ellipse(sink, lgx + ca * lgl * 0.5f, lgy + sa * lgl * 0.5f, lgl * 0.5f, lgl * 0.24f, lga)
        } else {
            val r = clamp((layout.length[i] * 0.42 * tz).toFloat(), 1.6f, 12f)
            ellipse(sink, lgx, lgy, r, r, 0f)
        }
    }
    private fun leaves(sink: NestSink, pass: Int) {
        if (pass == 0 && noDim()) return
        val ramps = colors.leafRamp
        for (b in 0..1) {
            val blob = b == 1
            for (g in 0 until NestNodes.GROUPS) for (s in (if (pass == 1) 8 else 0)..(if (pass == 1) 8 else 7)) {
                if (used[g] and (1 shl s) == 0) continue
                var any = false
                for (q in 0 until drawnCount) {
                    val i = drawn[q]
                    if (grp[i] != g || rx[i].toInt() != s || al[i] < 0.98f) continue
                    if (if (blob) !(cc[i] > 0 && !open[i]) else cc[i] > 0) continue
                    leafPath(sink, i, blob); any = true
                }
                if (any) sink.fill(ramps[g][s], if (blob) 0.6f else 1f)
            }
        }
        for (q in 0 until drawnCount) {
            val i = drawn[q]
            if (al[i] >= 0.98f || !inPass(i, pass) || (cc[i] > 0 && open[i])) continue
            leafPath(sink, i, cc[i] > 0)
            sink.fill(ramps[grp[i]][rx[i].toInt()], al[i] * (if (cc[i] > 0) 0.6f else 1f))
        }
        if (pass == 1) {   // a midrib on the big leaves
            var any = false
            for (q in 0 until drawnCount) {
                val i = drawn[q]
                if (cc[i] > 0 || rx[i].toInt() != 8 || al[i] < 0.98f) continue
                leafGeom(i); if (lgl <= 12) continue
                val ca = cos(lga); val sa = sin(lga)
                sink.moveTo(lgx + ca * lgl * 0.12f, lgy + sa * lgl * 0.12f); sink.lineTo(lgx + ca * lgl * 0.88f, lgy + sa * lgl * 0.88f); any = true
            }
            if (any) sink.stroke(colors.halo, 1f, 0.55f, true)
        }
    }

    // a link is a faint vine, drawn only when both its ends are in the part in view; Show links
    // draws only the chosen idea's, brighter
    private fun linkLive(a: Int, b: Int) = a != b && !nodes.within(a, b) && !nodes.within(b, a) && !(offscreen(sx[a], sy[a]) && offscreen(sx[b], sy[b]))
    private fun vines(sink: NestSink) {
        val only = linksOf
        var any = false
        for (l in 0 until links.size) {
            if (only >= 0 && links.a[l] != only && links.b[l] != only) continue
            if (hidden[links.a[l]] || hidden[links.b[l]]) continue
            val a = res(links.a[l]); val b = res(links.b[l])
            if (!linkLive(a, b) || dimOf(a) <= 0.6f || dimOf(b) <= 0.6f) continue
            val x0 = sx[a]; val y0 = sy[a]; val x1 = sx[b]; val y1 = sy[b]; val dx = x1 - x0; val dy = y1 - y0
            val len = sqrt(dx * dx + dy * dy); if (len < 16) continue
            val nx = -dy / len; val ny = dx / len; val bow = len * 0.16f * (if (l and 1 == 1) 1 else -1)
            val steps = min(28, 6 + (len / 10).toInt())
            for (s in 0..steps) {
                val u = s.toFloat() / steps
                val wig = sin(u * len / 22 * TAU) * 1.9f * sm(0f, 0.08f, u) * sm(1f, 0.92f, u)
                val off = bow * 4 * u * (1 - u) + wig
                val x = x0 + dx * u + nx * off; val y = y0 + dy * u + ny * off
                if (s == 0) sink.moveTo(x, y) else sink.lineTo(x, y)
            }
            any = true
        }
        if (any) sink.stroke(colors.vine, if (only >= 0) 2f else 1.3f, if (only >= 0) 0.9f else 0.5f, false)
    }
    private fun ring(sink: NestSink, x: Float, y: Float, r: Float) {
        ellipse(sink, x, y, r, r, 0f)
        sink.stroke(colors.accent, 2f, 1f, true)
    }

    companion object {
        /** The point along a branch its anchor is at. */
        const val ANCHOR = 6
        /** A part shorter than this reads as texture, not as a part: left out, with its parts. */
        const val MIN_LP = 4f
        const val FADE_LP = 10f
        /** A part opens its children from this long. */
        const val OPEN_LP = 9f
        /** The most parts drawn in a frame. */
        const val BUDGET = 380
        /** The least length a part is named at. */
        const val NAME_LP = 36f
        /** Between names: side by side, and above and below. */
        const val GAP_X = 8f
        const val GAP_Y = 4f
        /** Out along a bough, where boughs spread apart: where its name is tried. */
        val ON_BOUGH = intArrayOf(5, 4, 6, 3, 7, 2)
        private const val KAPPA = 0.5522848f
        private const val TAU = (Math.PI * 2).toFloat()

        fun sm(a: Float, b: Float, x0: Float): Float {
            var x = (x0 - a) / (b - a)
            x = if (x < 0) 0f else if (x > 1) 1f else x
            return x * x * (3 - 2 * x)
        }
        fun clamp(x: Float, a: Float, b: Float) = if (x < a) a else if (x > b) b else x
        fun clamp(x: Double, a: Double, b: Double) = if (x < a) a else if (x > b) b else x
    }
}
