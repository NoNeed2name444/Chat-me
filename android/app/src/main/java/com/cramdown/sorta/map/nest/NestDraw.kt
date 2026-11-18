package com.cramdown.sorta.map.nest

// What a nested map (Tree, Fractal) draws through and looks from, shared by the themes (S11).
// Pure Kotlin; iOS's NestDraw.swift is the same.

/** Where the scene draws: paths in the view's units, a fill or a stroke ends each one. */
interface NestSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float)
    fun close()
    /** Fills the path so far with [color] (RGB) at [alpha], and starts a new one. */
    fun fill(color: Int, alpha: Float)
    /** Strokes it, with round ends and joins when [round], and starts a new one. */
    fun stroke(color: Int, width: Float, alpha: Float, round: Boolean)
    /** One name: [node]'s title at [x], [y] (its middle line), [align] 0 from the left, 1 to the right, 2 centred. */
    fun text(node: Int, x: Float, y: Float, size: Float, bold: Boolean, align: Int, ink: Int, halo: Int, haloWidth: Float)
}

/** A camera: the tree's point at the view's middle, its zoom (view units per tree unit) and its turn. */
class NestCamera(var x: Double = 0.0, var y: Double = 0.0, var z: Double = 1.0, var r: Double = 0.0) {
    fun set(o: NestCamera) { x = o.x; y = o.y; z = o.z; r = o.r }
}

/** A link between two nodes, by index. */
class NestLinks(val a: IntArray, val b: IntArray) {
    val size: Int get() = a.size
    companion object { val NONE = NestLinks(IntArray(0), IntArray(0)) }
}
