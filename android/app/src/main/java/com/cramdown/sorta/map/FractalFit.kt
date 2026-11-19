package com.cramdown.sorta.map

import kotlin.math.max
import kotlin.math.min

// The Fractal's framing in 3D (S12, the lead's review of run 44408042504): the first view and a fly-in
// show what they frame as large as it fits in the room the chrome leaves, centred there. From the
// camera's own yaw and pitch, the eye comes as near as it can while every framed dot, its mark round
// it, stays inside that room less a margin ([FractalFit.MARGIN], about 14 dp on a phone); the room left over in the other
// direction is shared equally, so what is framed sits in the room's middle. Pure Kotlin, no Android;
// iOS's FractalFit in FractalNames3D.swift is the same.

object FractalFit {
    /** Room kept between what is framed and each edge of the room the chrome leaves, as a share of its narrower side. */
    const val MARGIN = 0.035f
    /** How many levels below All ideas the first view frames, and below a hub a fly-in frames. */
    const val WHOLE_LEVELS = 3
    const val IN_LEVELS = 2

    /**
     * [hub] and its parts down to [levels] below it, level by level, each hub's parts heaviest first, at
     * most [most] of them: what a view of [hub]'s cluster frames, never more than a frame draws.
     */
    fun nodes(tree: FractalTree, hub: Int, levels: Int, most: Int = FractalRules.BUDGET): IntArray {
        val out = IntArray(min(max(most, 1), tree.count))
        var n = 0
        out[n++] = hub
        var from = 0
        for (level in 0 until levels) {
            val to = n
            for (q in from until to) {
                val i = out[q]
                val s = tree.childStart[i]
                for (k in 0 until tree.childCount[i]) {
                    if (n >= out.size) return out.copyOf(n)
                    out[n++] = tree.children[s + k]
                }
            }
            if (n == to) break
            from = to
        }
        return out.copyOf(n)
    }

    /** How far a node's mark reaches round it, in the map's units: a hub's lit glow, an idea's dot. */
    fun mark(layout: FractalLayout3D, i: Int): Double =
        if (layout.tree.childCount[i] > 0) layout.dot[i] * (1 + FractalSight.GLOW_REACH) else layout.dot[i]

    /**
     * The pose from [yaw] and [pitch] with a [fovY] field that shows every one of [nodes] with its mark,
     * as large as fits in the room [insets] leave in a [width] x [height] view less [margin] pixels each
     * side, centred in that room. The pose orbits the middle of what it frames. [marks] is a node's mark
     * (map units) as a frame draws it, in place of its own: a big hub's dot drawn at its most size, a
     * speck's at its least.
     */
    fun fit(layout: FractalLayout3D, nodes: IntArray, yaw: Float, pitch: Float, fovY: Float, width: Float, height: Float,
            insets: MapInsets, margin: Float, marks: Map<Int, Double> = emptyMap()): MapPose {
        val lens = OrbitCamera(yaw = yaw, pitch = pitch, fovY = fovY)
        val r = lens.right(); val u = lens.up(); val b = lens.forward() * -1f
        val f = lens.focalPixels(height).toDouble()
        // the room, from the view's middle, right and up positive; a room too small for the margin keeps none
        var m = margin.toDouble()
        if (width - insets.left - insets.right <= 4 * m || height - insets.top - insets.bottom <= 4 * m) m = 0.0
        val left = insets.left + m - width / 2.0; val right = width / 2.0 - insets.right - m
        val top = height / 2.0 - insets.top - m; val bottom = -(height / 2.0 - insets.bottom - m)
        val count = nodes.size
        val x = DoubleArray(count); val y = DoubleArray(count); val z = DoubleArray(count); val k = DoubleArray(count)
        var zLow = Double.POSITIVE_INFINITY; var zHigh = Double.NEGATIVE_INFINITY; var nearest = Double.NEGATIVE_INFINITY
        val pos = layout.pos
        for (q in 0 until count) {
            val i = nodes[q]
            val px = pos[3 * i]; val py = pos[3 * i + 1]; val pz = pos[3 * i + 2]
            x[q] = px * r.x + py * r.y + pz * r.z
            y[q] = px * u.x + py * u.y + pz * u.z
            z[q] = px * b.x + py * b.y + pz * b.z
            k[q] = marks[i] ?: mark(layout, i)
            zLow = min(zLow, z[q]); zHigh = max(zHigh, z[q]); nearest = max(nearest, z[q] + k[q])
        }
        if (count == 0) return MapPose(Vec3.ZERO, 1f, yaw, pitch)
        // for an eye at depth e along b, the eye's across and up that keep each mark in the room:
        // across >= x + k - right (e - z) / f and across <= x - k - left (e - z) / f, and so for up
        val span = DoubleArray(4)
        fun spans(e: Double): Boolean {
            var lowX = Double.NEGATIVE_INFINITY; var highX = Double.POSITIVE_INFINITY
            var lowY = Double.NEGATIVE_INFINITY; var highY = Double.POSITIVE_INFINITY
            for (q in 0 until count) {
                val d = e - z[q]
                lowX = max(lowX, x[q] + k[q] - right * d / f); highX = min(highX, x[q] - k[q] - left * d / f)
                lowY = max(lowY, y[q] + k[q] - top * d / f); highY = min(highY, y[q] - k[q] - bottom * d / f)
            }
            span[0] = lowX; span[1] = highX; span[2] = lowY; span[3] = highY
            return lowX <= highX && lowY <= highY
        }
        // the nearest eye that fits: a farther one always fits too, so halve the gap between them
        val reach = max(zHigh - zLow, 1e-6) + k.max()
        var near = nearest + 1e-9
        var far = nearest + reach
        while (!spans(far)) far = nearest + (far - nearest) * 2
        repeat(48) { val mid = (near + far) / 2; if (spans(mid)) far = mid else near = mid }
        spans(far)
        // then, within what fits, the across and up that put the marks' box in the room's middle: its middle
        // moves the other way as the eye moves, each mark at its own pace, so halve towards it
        fun middle(at: Double, c: DoubleArray, e: Double): Double {
            var lo = Double.POSITIVE_INFINITY; var hi = Double.NEGATIVE_INFINITY
            for (q in 0 until count) {
                val d = e - z[q]
                lo = min(lo, (c[q] - k[q] - at) * f / d); hi = max(hi, (c[q] + k[q] - at) * f / d)
            }
            return (lo + hi) / 2
        }
        fun centred(low: Double, high: Double, c: DoubleArray, goal: Double): Double {
            var a = low; var z1 = high
            repeat(48) { val mid = (a + z1) / 2; if (middle(mid, c, far) > goal) a = mid else z1 = mid }
            return (a + z1) / 2
        }
        val ex = centred(span[0], span[1], x, (left + right) / 2)
        val ey = centred(span[2], span[3], y, (top + bottom) / 2)
        // orbit the middle of the framed depth, on the line through the view's middle
        val zc = (zLow + zHigh) / 2
        val distance = (far - zc).toFloat()
        val target = r * ex.toFloat() + u * ey.toFloat() + b * zc.toFloat()
        return MapPose(target, distance, yaw, pitch)
    }

    /** The margin in pixels for the room [insets] leave in a [width] x [height] view. */
    fun margin(width: Float, height: Float, insets: MapInsets): Float =
        MARGIN * max(min(width - insets.left - insets.right, height - insets.top - insets.bottom), 0f)

    /**
     * What a fit of [nodes] ([first]) draws, framed: [sight]'s frame at that pose (its focus and filter as
     * set), in a view with [density] pixels a dp, then the pose fit once more to [nodes] and every dot drawn
     * there, each with its glow as drawn. A deeper level's dot or a hub's glow at its least size then stays
     * in the room, not at the view's edge (the review of S12's stills), and what is drawn is centred there,
     * not what a nearer pose drew. A pose that draws other dots than the last (one turned into view as the
     * camera stood back, a level closed as it shrank), or a glow grown, is framed again, at most [PASSES]
     * times in all. iOS's FractalFit.drawn is the same.
     */
    fun drawn(model: FractalModel, sight: FractalSight, nodes: IntArray, first: MapPose, fovY: Float, width: Float, height: Float,
              insets: MapInsets, margin: Float, density: Float): MapPose {
        val shown = sight.shown
        val framed = nodes.filter { shown == null || (it < shown.size && shown[it]) }
        var last: Map<Int, Double> = emptyMap()
        var pose = first
        for (pass in 0 until PASSES) {
            val cam = OrbitCamera(target = pose.target, distance = pose.distance, yaw = pose.yaw, pitch = pose.pitch, fovY = fovY)
            val f = cam.focalPixels(height)
            sight.step(viewProj(model, cam, width / height), f, width, height, density)
            val marks = HashMap<Int, Double>(sight.count * 2)
            for (k in 0 until sight.count) {
                // the glow's reach on screen, back in the map's units at the dot's depth
                marks[sight.node[k]] = (sight.radius[k] * (1f + FractalSight.GLOW_REACH * sight.hub[k])).toDouble() * sight.depth[k] / f
            }
            // the same dots as the pose was fit to, none grown past rounding: it frames what it draws
            if (pass > 0 && marks.keys == last.keys && marks.all { (i, v) -> v <= (last[i] ?: 0.0) * (1 + GROWN) }) break
            val all = LinkedHashSet<Int>(framed.size + marks.size)
            all.addAll(framed)
            all.addAll(marks.keys.sorted())
            if (all.isEmpty()) break
            last = marks
            pose = fit(model.layout, all.toIntArray(), first.yaw, first.pitch, fovY, width, height, insets, margin, marks)
        }
        return pose
    }

    /** How much a glow may grow from the pose it was fit at (a share of it) and still count as framed. */
    const val GROWN = 0.002

    /** The most times [drawn] frames a pose again. */
    const val PASSES = 5

    /** The camera's projection times view, with near and far hugging the whole map's ball, as FractalGl draws it. */
    fun viewProj(model: FractalModel, cam: OrbitCamera, aspect: Float): FloatArray {
        val l = model.layout
        val eye = cam.eye()
        val dx = eye.x - l.centre[0].toFloat(); val dy = eye.y - l.centre[1].toFloat(); val dz = eye.z - l.centre[2].toFloat()
        val toCentre = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val reach = l.reach[0].toFloat()
        val near = max(max(toCentre - reach * 1.15f, max(toCentre, 1e-3f) * 1e-4f), 1e-5f)
        val far = max(toCentre + reach * 1.15f, near * 2f)
        return Mat4.multiply(Mat4.perspective(cam.fovY, aspect, near, far), cam.view())
    }

    /**
     * The first view: All ideas' cluster [WHOLE_LEVELS] deep, from [yaw] and [pitch]; with [sight] (its
     * focus 0), framed again to what it draws there ([drawn]).
     */
    fun whole(model: FractalModel, yaw: Float, pitch: Float, fovY: Float, width: Float, height: Float, insets: MapInsets,
              sight: FractalSight? = null, density: Float = 1f): MapPose {
        val nodes = nodes(model.tree, 0, WHOLE_LEVELS, model.budget)
        val margin = margin(width, height, insets)
        val first = fit(model.layout, nodes, yaw, pitch, fovY, width, height, insets, margin)
        return if (sight == null) first else drawn(model, sight, nodes, first, fovY, width, height, insets, margin, density)
    }

    /**
     * A fly-in to [hub]: its cluster [IN_LEVELS] deep, from the camera's own [yaw] and [pitch]; with [sight]
     * (its focus [hub]), framed again to what it draws there ([drawn]).
     */
    fun flyIn(model: FractalModel, hub: Int, yaw: Float, pitch: Float, fovY: Float, width: Float, height: Float, insets: MapInsets,
              sight: FractalSight? = null, density: Float = 1f): MapPose {
        val nodes = nodes(model.tree, hub, IN_LEVELS, model.budget)
        val margin = margin(width, height, insets)
        val first = fit(model.layout, nodes, yaw, pitch, fovY, width, height, insets, margin)
        return if (sight == null) first else drawn(model, sight, nodes, first, fovY, width, height, insets, margin, density)
    }
}
