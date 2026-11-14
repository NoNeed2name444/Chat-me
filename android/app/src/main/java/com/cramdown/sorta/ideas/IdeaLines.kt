package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// A port of ios/RedPen/Features/Notes/GraphLineStyle.swift (fed0105).
//
// Lines: Curved / Straight. One choice for both Ideas modes: how a link
// between two ideas is drawn. Curved (the standard) keeps the 3D map's own
// shape and bows the board's connectors gently; Straight draws every link as
// one direct segment between its two ends, on the map and on the board.
// Pure Kotlin: LineStyleTest mirrors LineStyleTests L1 to L6.

/** How links are drawn in the Ideas map and on the board. */
enum class GraphLineStyle(val rawValue: String, val title: String) {
    CURVED("curved", "Curved"),
    STRAIGHT("straight", "Straight");

    val isStraight: Boolean get() = this == STRAIGHT

    companion object {
        /** Where it is stored: a Boolean, true for Straight. */
        const val KEY = "vignette.ideas.straightLines"
        /** Until the owner chooses: Curved. */
        val standard = CURVED

        fun of(raw: String): GraphLineStyle? = entries.firstOrNull { it.rawValue == raw }

        /** The stored Boolean read back (nothing stored: the standard). */
        fun stored(straight: Boolean?): GraphLineStyle = when (straight) {
            null -> standard
            true -> STRAIGHT
            false -> CURVED
        }

        /** `-ideasLines straight` (or `curved`): that style for a design-preview run; null without it. */
        fun parse(arguments: List<String>): GraphLineStyle? {
            val at = arguments.indexOf("-ideasLines")
            if (at < 0 || at + 1 >= arguments.size) return null
            return of(arguments[at + 1])
        }

        /** Android has no launch arguments for the app: never forced. */
        val launchOverride: GraphLineStyle? = null

        /** The style in force, given the stored Boolean. */
        fun inForce(stored: Boolean): GraphLineStyle = launchOverride ?: if (stored) STRAIGHT else CURVED
    }
}

/**
 * A link drawn Straight in the 3D map: `count + 1` points evenly spaced on the direct
 * segment from its start towards its end, as far as it has grown.
 */
object GraphStraightLine {
    /** The points and their length coordinates; reused, grown as needed, never shrunk. */
    class Buffers(var points: Array<Vec3> = emptyArray(), var lengths: FloatArray = FloatArray(0))

    /** Fills [into] and returns the drawn length. [grow] (0 to 1) is how far the link has grown. */
    fun sample(start: Vec3, end: Vec3, grow: Float, count: Int, into: Buffers): Float {
        val n = max(count, 1)
        if (into.points.size < n + 1) into.points = Array(n + 1) { Vec3.ZERO }
        if (into.lengths.size < n + 1) into.lengths = FloatArray(n + 1)
        val grown = min(max(grow, 0f), 1f)
        val run = (end - start) * grown
        val total = size(run)
        val step = 1f / n
        for (k in 0..n) {
            val f = k * step
            into.points[k] = start + run * f
            into.lengths[k] = total * f
        }
        // the last point exactly on the end, free of rounding
        into.points[n] = start + run
        into.lengths[n] = total
        return total
    }

    /**
     * The same into float arrays, three floats a point (as LinkCurve.sample writes them, so a
     * frame's links make no garbage): `count + 1` points into [points] and their length coordinates
     * into [lengths]. Returns the drawn length.
     */
    fun sample(start: Vec3, end: Vec3, grow: Float, count: Int, points: FloatArray, lengths: FloatArray): Float =
        sample(start.x, start.y, start.z, end.x, end.y, end.z, grow, count, points, lengths)

    /** The same with the ends as floats ([sx], [sy], [sz] to [ex], [ey], [ez]). */
    fun sample(sx: Float, sy: Float, sz: Float, ex: Float, ey: Float, ez: Float, grow: Float, count: Int,
               points: FloatArray, lengths: FloatArray): Float {
        val n = max(count, 1)
        require(points.size >= (n + 1) * 3 && lengths.size >= n + 1) { "room for ${n + 1} points" }
        val grown = min(max(grow, 0f), 1f)
        val rx = (ex - sx) * grown; val ry = (ey - sy) * grown; val rz = (ez - sz) * grown
        val total = sqrt(rx * rx + ry * ry + rz * rz)
        val step = 1f / n
        for (k in 0..n) {
            val f = k * step
            points[k * 3] = sx + rx * f; points[k * 3 + 1] = sy + ry * f; points[k * 3 + 2] = sz + rz * f
            lengths[k] = total * f
        }
        // the last point exactly on the end, free of rounding
        points[n * 3] = sx + rx; points[n * 3 + 1] = sy + ry; points[n * 3 + 2] = sz + rz
        lengths[n] = total
        return total
    }

    fun size(v: Vec3): Float = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
}

/** A point on the board, in points (dp). */
data class BoardPoint(val x: Double, val y: Double)

/**
 * A connector's shape on the idea board, between two card centres `p` and `q` (always passed
 * in the same order for a pair). Curved: a quadratic bow to the left of p-to-q, its control
 * [BOW] of the length off the middle; several links between one pair fan out. Straight: the
 * direct segment, lanes side by side [LANE_GAP] apart.
 */
object IdeaLinkShape {
    const val BOW = 0.18
    const val FAN_STEP = 0.14
    const val LANE_GAP = 10.0

    /** Lane [lane] of [lanes], centred on 0: -0.5 and 0.5 for two. */
    fun laneOffset(lane: Int, lanes: Int): Double {
        val count = max(lanes, 1)
        val middle = (count - 1) / 2.0
        return lane - middle
    }

    fun bowFactor(lane: Int, lanes: Int): Double = BOW + FAN_STEP * laneOffset(lane, lanes)

    /** The quadratic's control point for a curved link. */
    fun control(p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1): BoardPoint {
        val factor = bowFactor(lane, lanes)
        val dx = q.x - p.x
        val dy = q.y - p.y
        val midX = (p.x + q.x) / 2
        val midY = (p.y + q.y) / 2
        return BoardPoint(midX - dy * factor, midY + dx * factor)
    }

    /** A curved link as drawn: one quadratic from [start] to [end] through [control]. */
    data class Curve(val start: BoardPoint, val control: BoardPoint, val end: BoardPoint)

    /**
     * A curved link's path: one quadratic from card centre [p] to card centre [q] through [control]
     * (iOS IdeaBoardView.line: move(to: p), addQuadCurve(to: q, control:)).
     */
    fun curve(p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1): Curve = Curve(p, control(p, q, lane, lanes), q)

    /** A straight link's two ends, moved sideways for its lane. */
    fun straightEnds(p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1): Pair<BoardPoint, BoardPoint> {
        val shift = laneOffset(lane, lanes) * LANE_GAP
        val dx = q.x - p.x
        val dy = q.y - p.y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.0001 || shift == 0.0) return p to q
        val sideX = -dy / length * shift
        val sideY = dx / length * shift
        return BoardPoint(p.x + sideX, p.y + sideY) to BoardPoint(q.x + sideX, q.y + sideY)
    }

    /** The point [t] of the way along (0 to 1). */
    fun point(style: GraphLineStyle, p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1, t: Double): BoardPoint {
        if (style.isStraight) {
            val ends = straightEnds(p, q, lane, lanes)
            return lerp(ends.first, ends.second, t)
        }
        val k = control(p, q, lane, lanes)
        val s = 1 - t
        val w0 = s * s
        val w1 = 2 * s * t
        val w2 = t * t
        return BoardPoint(p.x * w0 + k.x * w1 + q.x * w2, p.y * w0 + k.y * w1 + q.y * w2)
    }

    /** `count + 1` points along it, end to end. */
    fun samples(style: GraphLineStyle, p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1, count: Int = 24): List<BoardPoint> {
        val n = max(count, 1)
        return (0..n).map { k -> point(style, p, q, lane, lanes, k.toDouble() / n) }
    }

    /** The way it runs into [q], unit length (for an arrowhead there). */
    fun endDirection(style: GraphLineStyle, p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1): BoardPoint {
        val before = point(style, p, q, lane, lanes, 0.98)
        val end = point(style, p, q, lane, lanes, 1.0)
        return unit(BoardPoint(end.x - before.x, end.y - before.y))
    }

    /** How far [spot] is from the link as drawn (for a hit test). */
    fun distance(spot: BoardPoint, style: GraphLineStyle, p: BoardPoint, q: BoardPoint, lane: Int = 0, lanes: Int = 1): Double {
        val line = samples(style, p, q, lane, lanes, if (style.isStraight) 1 else 32)
        var best = Double.MAX_VALUE
        for (k in 1 until line.size) best = min(best, segmentDistance(spot, line[k - 1], line[k]))
        return best
    }

    /** Each link's (lane, how many share the pair), from each link's pair key in order. */
    fun lanes(keys: List<String>): List<Pair<Int, Int>> {
        val counts = HashMap<String, Int>()
        for (key in keys) counts[key] = (counts[key] ?: 0) + 1
        val seen = HashMap<String, Int>()
        return keys.map { key ->
            val lane = seen[key] ?: 0
            seen[key] = lane + 1
            lane to (counts[key] ?: 1)
        }
    }

    fun lerp(a: BoardPoint, b: BoardPoint, t: Double) = BoardPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    fun unit(v: BoardPoint): BoardPoint {
        val length = sqrt(v.x * v.x + v.y * v.y)
        if (length <= 0.000001) return BoardPoint(1.0, 0.0)
        return BoardPoint(v.x / length, v.y / length)
    }

    /** The distance from [c] to the segment a-b. */
    fun segmentDistance(c: BoardPoint, a: BoardPoint, b: BoardPoint): Double {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val acx = c.x - a.x
        val acy = c.y - a.y
        val span = abx * abx + aby * aby
        var f = 0.0
        if (span > 0.0000001) f = min(max((acx * abx + acy * aby) / span, 0.0), 1.0)
        val dx = acx - abx * f
        val dy = acy - aby * f
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * The board's opening view and Recentre: the zoom and pan that put every card on
 * screen, clear of the edges, the tools and the bottom bar. Never zoomed in past 1, nor out past
 * the board's floor. A card's centre is drawn at (width / 2 + panX + x * zoom, midY + panY + y * zoom),
 * midY the middle of the part not under the bar.
 */
object IdeaBoardFit {
    data class Camera(val zoom: Double, val panX: Double, val panY: Double)
    data class Margins(val top: Double, val leading: Double, val bottom: Double, val trailing: Double)
    data class Size(val width: Double, val height: Double)

    /**
     * [sizes], when given, are the cards' own sizes in [centres]' order (a card past its end is
     * [card]): at the largest text sizes a card is far taller than [card], and the box is fitted
     * to the cards as drawn (S05c). As iOS's.
     */
    fun camera(centres: List<BoardPoint>, card: Size, view: Size, hidden: Double, margins: Margins,
               zoomRange: ClosedFloatingPointRange<Double> = 0.3..1.0, sizes: List<Size> = emptyList()): Camera {
        if (centres.isEmpty()) return Camera(1.0, 0.0, 0.0)
        // the cards' outer edges
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        centres.forEachIndexed { k, c ->
            val s = sizes.getOrNull(k) ?: card
            minX = min(minX, c.x - s.width / 2); maxX = max(maxX, c.x + s.width / 2)
            minY = min(minY, c.y - s.height / 2); maxY = max(maxY, c.y + s.height / 2)
        }
        val width = view.width
        val seen = max(view.height - hidden, 0.0)
        val roomW = max(width - margins.leading - margins.trailing, 1.0)
        val roomH = max(seen - margins.top - margins.bottom, 1.0)
        val spanW = maxX - minX
        val spanH = maxY - minY
        val fit = min(roomW / spanW, roomH / spanH)
        val zoom = min(max(fit, zoomRange.start), zoomRange.endInclusive)
        val panX = margins.leading + roomW / 2 - width / 2 - (minX + maxX) / 2 * zoom
        val panY = margins.top + roomH / 2 - seen / 2 - (minY + maxY) / 2 * zoom
        return Camera(zoom, panX, panY)
    }

    /**
     * The first view's (and Recentre's) zoom range: never past 1, and never so far out that a card
     * is shorter than a touch target ([target]: 48 dp, 44 pt), so every card opens big enough to
     * tap (S05b task 5). A pinch still reaches the board's floor, 0.3. As iOS's.
     */
    fun firstRange(cardHeight: Double, target: Double): ClosedFloatingPointRange<Double> =
        min(max(target / cardHeight, 0.3), 1.0)..1.0

    /**
     * The board's tools at its trailing edge (S05m): the cluster's own box, gutter included; in the
     * bottom-trailing corner of the part not under the bar, or [centred] down that part in a broad
     * window. As iOS's.
     */
    data class Tools(val width: Double, val height: Double, val centred: Boolean)

    /** The rectangle [tools] take in the board's own view, in dp: left, top, right, bottom. */
    fun toolsRect(view: Size, hidden: Double, tools: Tools): DoubleArray {
        val seen = max(view.height - hidden, 0.0)
        val top = if (tools.centred) seen / 2 - tools.height / 2 else seen - tools.height
        return doubleArrayOf(view.width - tools.width, top, view.width, top + tools.height)
    }

    /**
     * The first view and Recentre (S05m item 1): every card on screen, 16 in from each edge and
     * below the top strip ([top]), the box as large as fits, never under the tools; the zoom within
     * [firstRange] of the shortest card. As iOS's.
     */
    fun firstView(centres: List<BoardPoint>, sizes: List<Size>, card: Size, view: Size, hidden: Double,
                  top: Double, tools: Tools, target: Double): Camera =
        camera(centres, card, view, hidden, Margins(16.0 + top, 16.0, 16.0, 16.0),
            firstRange(sizes.minOfOrNull { it.height } ?: card.height, target), sizes, tools)

    /**
     * [camera]'s fit to the whole room, kept if no card is under [tools]; else zoomed out in 2 %
     * steps, the box tried centred, at the leading edge, at the top and at the top-leading corner,
     * until no card is under them; and at the latest the room beside the tools (or above them, in
     * the corner, if that is larger). As iOS's.
     */
    fun camera(centres: List<BoardPoint>, card: Size, view: Size, hidden: Double, margins: Margins,
               zoomRange: ClosedFloatingPointRange<Double>, sizes: List<Size>, tools: Tools): Camera {
        val full = camera(centres, card, view, hidden, margins, zoomRange, sizes)
        if (centres.isEmpty()) return full
        val rect = toolsRect(view, hidden, tools)
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        centres.forEachIndexed { k, c ->
            val s = sizes.getOrNull(k) ?: card
            minX = min(minX, c.x - s.width / 2); maxX = max(maxX, c.x + s.width / 2)
            minY = min(minY, c.y - s.height / 2); maxY = max(maxY, c.y + s.height / 2)
        }
        val seen = max(view.height - hidden, 0.0)
        fun clear(cam: Camera): Boolean = centres.withIndex().none { (k, c) ->
            val s = sizes.getOrNull(k) ?: card
            val x = view.width / 2 + cam.panX + c.x * cam.zoom
            val y = seen / 2 + cam.panY + c.y * cam.zoom
            val hw = s.width * cam.zoom / 2; val hh = s.height * cam.zoom / 2
            x + hw > rect[0] && x - hw < rect[2] && y + hh > rect[1] && y - hh < rect[3]
        }
        if (clear(full)) return full
        val side = camera(centres, card, view, hidden, margins.copy(trailing = max(margins.trailing, tools.width)), zoomRange, sizes)
        val above = if (tools.centred) null
            else camera(centres, card, view, hidden, margins.copy(bottom = max(margins.bottom, tools.height)), zoomRange, sizes)
        val fallback = if (above != null && above.zoom > side.zoom) above else side
        val left = margins.leading; val right = view.width - margins.trailing
        val roomTop = margins.top; val roomBottom = seen - margins.bottom
        for (step in 0..60) {
            val z = full.zoom * (1 - 0.02 * step)
            if (step > 0 && (z <= fallback.zoom || z < zoomRange.start)) break
            // the box's screen edges at this zoom: centred in the room, or at its leading or top edge
            val centreX = (left + right) / 2 - view.width / 2 - (minX + maxX) / 2 * z
            val leadX = left - view.width / 2 - minX * z
            val centreY = (roomTop + roomBottom) / 2 - seen / 2 - (minY + maxY) / 2 * z
            val topY = roomTop - seen / 2 - minY * z
            for ((px, py) in listOf(centreX to centreY, leadX to centreY, centreX to topY, leadX to topY)) {
                val cam = Camera(z, px, py)
                if (clear(cam)) return cam
            }
        }
        return fallback
    }
}

/**
 * The whole of a long title, shown over its card on the board (S05m item 5): at least 240 wide (the
 * card's width on screen if wider), never wider than the board less 32; from the card's top-leading
 * corner, kept 16 inside the board's room and clear of its tools. In dp. As iOS's.
 */
object IdeaBoardPeek {
    fun width(card: Double, view: Double): Double = max(min(max(card, 240.0), view - 32), 1.0)

    /** The peek's top-leading corner, for a peek [width] by [height] over a card whose top-leading corner is ([x], [y]). */
    fun place(x: Double, y: Double, width: Double, height: Double, view: IdeaBoardFit.Size, hidden: Double, top: Double,
              tools: IdeaBoardFit.Tools): BoardPoint {
        val seen = max(view.height - hidden, 0.0)
        fun clampX(v: Double) = max(16.0, min(v, view.width - 16 - width))
        fun clampY(v: Double) = max(top + 16, min(v, seen - 16 - height))
        var px = clampX(x); var py = clampY(y)
        val r = IdeaBoardFit.toolsRect(view, hidden, tools)
        val under = px + width > r[0] && px < r[2] && py + height > r[1] && py < r[3]
        if (under) {
            // to the tools' leading side if it fits there, else above them
            if (r[0] - width >= 16) px = r[0] - width
            else py = max(top + 16, r[1] - height)
        }
        return BoardPoint(px, py)
    }
}

/**
 * Where a card let go of on the board settles (S05b task 5): where it was let go, unless it would
 * cover part of another card; then the nearest free place, looked for in rings [step] apart, 24
 * ways round each, with [gap] kept between cards. Centres, in board points. As iOS's.
 */
object IdeaBoardDrop {
    /** The 2D board's card, in board points: its width, and the height the board fits and drops by. */
    const val CARD_WIDTH = 150.0
    const val CARD_HEIGHT = 88.0
    /** The tallest card at the default text size, which a new idea is spaced by. */
    const val CARD_TALLEST = 128.0
    /** The room kept between cards on the board, drawn and dropped (S05m item 3). As iOS's. */
    const val GAP = 12.0
    /**
     * A title's widest line on the board is this sample's width in the title's font: 20 letters,
     * narrow and wide (S05m item 5). As iOS's.
     */
    const val TITLE_SAMPLE = "abcdefghijklmnopqrst"

    /** Two cards cover each other, or come closer than [gap]: centres nearer than half their widths (heights) and the gap, across and down. As iOS's. */
    fun covers(a: BoardPoint, aw: Double, ah: Double, b: BoardPoint, bw: Double, bh: Double, gap: Double): Boolean =
        abs(a.x - b.x) < (aw + bw) / 2 + gap && abs(a.y - b.y) < (ah + bh) / 2 + gap

    fun settle(p: BoardPoint, cardWidth: Double, cardHeight: Double, others: List<BoardPoint>, gap: Double = GAP,
               step: Double = 8.0, rings: Int = 200): BoardPoint {
        val w = cardWidth + gap
        val h = cardHeight + gap
        fun free(x: Double, y: Double) = others.all { abs(x - it.x) >= w || abs(y - it.y) >= h }
        if (free(p.x, p.y)) return p
        for (ring in 1..max(rings, 1)) {
            val r = ring * step
            for (k in 0 until 24) {
                val a = k * PI / 12
                val x = p.x + r * cos(a)
                val y = p.y + r * sin(a)
                if (free(x, y)) return BoardPoint(x, y)
            }
        }
        return p
    }
}

/**
 * Where the 2D board draws its cards (S05c, S05m item 2), on screen only: the stored places stay as
 * they are. Cards stored on one point are drawn round it as a block whose middle is that point (rows
 * of `ceil(sqrt(n))` in the store's order, [gap] apart); a block that would cover one drawn before it
 * moves whole to the nearest free place in [IdeaBoardDrop]'s rings. Then each other card, in the
 * store's order, keeps its place if it covers no drawn card; one that covers a block's card is drawn
 * on the side of that block it is stored on, as near its place as is free; one that covers only
 * other cards goes to the nearest free place in the rings. Each pair is kept apart by its own sizes
 * and [gap]. The same input gives the same places, the store's order breaking ties. As iOS's.
 */
object IdeaBoardSpread {
    data class Card(val id: String, val centre: BoardPoint, val width: Double, val height: Double)

    private data class Cell(val i: Int, val j: Int)
    private class Placed(val at: BoardPoint, val width: Double, val height: Double, val block: Int)
    private class Block(val point: BoardPoint, val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

    /** The offsets from a shared point of [group]'s cards, in its order: rows centred on the point, the whole block's middle on it. */
    private fun layout(group: List<Card>, gap: Double): List<BoardPoint> {
        val n = group.size
        val cols = kotlin.math.ceil(sqrt(n.toDouble())).toInt()
        val rows = group.chunked(cols)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val total = heights.sum() + gap * (rows.size - 1)
        val out = ArrayList<BoardPoint>(n)
        var y = -total / 2
        rows.forEachIndexed { r, row ->
            val rowW = row.sumOf { it.width } + gap * (row.size - 1)
            var x = -rowW / 2
            for (c in row) {
                out.add(BoardPoint(x + c.width / 2, y + heights[r] / 2))
                x += c.width + gap
            }
            y += heights[r] + gap
        }
        return out
    }

    fun places(cards: List<Card>, gap: Double = IdeaBoardDrop.GAP, step: Double = 8.0, rings: Int = 2000): Map<String, BoardPoint> {
        if (cards.isEmpty()) return emptyMap()
        // placed cards in cells the size of the largest card and the gap, so a place is checked
        // against the 3 by 3 cells round it
        val cellW = cards.maxOf { it.width } + gap
        val cellH = cards.maxOf { it.height } + gap
        val grid = HashMap<Cell, MutableList<Placed>>()
        var right = Double.NEGATIVE_INFINITY
        val result = LinkedHashMap<String, BoardPoint>()
        val blocks = ArrayList<Block>()
        fun cell(p: BoardPoint) = Cell(kotlin.math.floor(p.x / cellW).toInt(), kotlin.math.floor(p.y / cellH).toInt())
        // the drawn cards this one would cover, at p
        fun hits(p: BoardPoint, w: Double, h: Double): List<Placed> {
            val c = cell(p)
            val out = ArrayList<Placed>()
            for (di in -1..1) for (dj in -1..1) {
                for (o in grid[Cell(c.i + di, c.j + dj)] ?: continue) {
                    if (IdeaBoardDrop.covers(p, w, h, o.at, o.width, o.height, gap)) out.add(o)
                }
            }
            return out
        }
        fun free(p: BoardPoint, w: Double, h: Double): Boolean {
            val c = cell(p)
            for (di in -1..1) for (dj in -1..1) {
                for (o in grid[Cell(c.i + di, c.j + dj)] ?: continue) {
                    if (IdeaBoardDrop.covers(p, w, h, o.at, o.width, o.height, gap)) return false
                }
            }
            return true
        }
        fun put(card: Card, at: BoardPoint, block: Int) {
            grid.getOrPut(cell(at)) { mutableListOf() }.add(Placed(at, card.width, card.height, block))
            right = max(right, at.x + card.width / 2)
            result[card.id] = at
        }
        fun ring(p: BoardPoint, ring: Int, k: Int, from: Double = 0.0): BoardPoint {
            val a = from + k * PI / 12
            val r = ring * step
            return BoardPoint(p.x + r * cos(a), p.y + r * sin(a))
        }

        // the shared points, in the store's order of each one's first card; -0.0 + 0.0 is 0.0, so a
        // point at -0.0 shares 0.0's, as iOS's Key (GraphLineStyle.swift) has it
        fun key(p: BoardPoint) = (p.x + 0.0) to (p.y + 0.0)
        val byPoint = LinkedHashMap<Pair<Double, Double>, MutableList<Card>>()
        for (card in cards) byPoint.getOrPut(key(card.centre)) { mutableListOf() }.add(card)
        for (group in byPoint.values) {
            if (group.size < 2) continue
            val point = group[0].centre
            val offsets = layout(group, gap)
            fun fits(d: BoardPoint) = group.indices.all { k ->
                free(BoardPoint(point.x + d.x + offsets[k].x, point.y + d.y + offsets[k].y), group[k].width, group[k].height)
            }
            var shift: BoardPoint? = if (fits(BoardPoint(0.0, 0.0))) BoardPoint(0.0, 0.0) else null
            var r = 1
            while (shift == null && r <= max(rings, 1)) {
                for (k in 0 until 24) {
                    val d = ring(BoardPoint(0.0, 0.0), r, k)
                    if (fits(d)) { shift = d; break }
                }
                r++
            }
            // past the rings: beyond every placed card, at its own height
            val d = shift ?: BoardPoint(right + gap - (point.x + group.indices.minOf { offsets[it].x - group[it].width / 2 }), 0.0)
            var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
            group.forEachIndexed { k, card ->
                val at = BoardPoint(point.x + d.x + offsets[k].x, point.y + d.y + offsets[k].y)
                put(card, at, blocks.size)
                minX = min(minX, at.x - card.width / 2); maxX = max(maxX, at.x + card.width / 2)
                minY = min(minY, at.y - card.height / 2); maxY = max(maxY, at.y + card.height / 2)
            }
            blocks.add(Block(BoardPoint(point.x + d.x, point.y + d.y), minX, maxX, minY, maxY))
        }

        for (card in cards) {
            if (byPoint[key(card.centre)]!!.size >= 2) continue
            val p = card.centre
            val hit = hits(p, card.width, card.height)
            if (hit.isEmpty()) { put(card, p, -1); continue }
            // a block it covers: the nearest one's point, the store's order breaking a tie
            val near = hit.filter { it.block >= 0 }.map { it.block }.distinct().sorted().minByOrNull { b ->
                val q = blocks[b].point; (p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y)
            }
            var at: BoardPoint? = null
            if (near != null) {
                val b = blocks[near]
                val ox = p.x - b.point.x; val oy = p.y - b.point.y
                val halfW = max((b.maxX - b.minX) / 2, 1e-9); val halfH = max((b.maxY - b.minY) / 2, 1e-9)
                val down = abs(oy) / halfH >= abs(ox) / halfW
                val sign = if (down) (if (oy < 0) -1.0 else 1.0) else (if (ox < 0) -1.0 else 1.0)
                fun past(q: BoardPoint): Boolean = when {
                    down && sign > 0 -> q.y - card.height / 2 >= b.maxY + gap - 1e-9
                    down -> q.y + card.height / 2 <= b.minY - gap + 1e-9
                    sign > 0 -> q.x - card.width / 2 >= b.maxX + gap - 1e-9
                    else -> q.x + card.width / 2 <= b.minX - gap + 1e-9
                }
                // the rings, tried from that side's own direction, then 15 degrees each way
                val from = if (down) (if (sign > 0) PI / 2 else -PI / 2) else (if (sign > 0) 0.0 else PI)
                val order = listOf(0) + (1..11).flatMap { listOf(it, -it) } + 12
                var r = 1
                while (at == null && r <= max(rings, 1)) {
                    for (k in order) {
                        val q = ring(p, r, k, from)
                        if (past(q) && free(q, card.width, card.height)) { at = q; break }
                    }
                    r++
                }
            } else {
                var r = 1
                while (at == null && r <= max(rings, 1)) {
                    for (k in 0 until 24) {
                        val q = ring(p, r, k)
                        if (free(q, card.width, card.height)) { at = q; break }
                    }
                    r++
                }
            }
            // past the rings: beyond every placed card, at its own height
            put(card, at ?: BoardPoint(right + card.width / 2 + gap, p.y), -1)
        }
        // in the store's order, as before
        return cards.associateTo(LinkedHashMap()) { it.id to result.getValue(it.id) }
    }

    /** Where a card let go of at [p] settles among the drawn cards ([others]): [IdeaBoardDrop.settle]'s search, with each pair's own sizes. */
    fun settle(p: BoardPoint, width: Double, height: Double, others: List<Card>, gap: Double = IdeaBoardDrop.GAP,
               step: Double = 8.0, rings: Int = 200): BoardPoint {
        fun free(q: BoardPoint) = others.none { IdeaBoardDrop.covers(q, width, height, it.centre, it.width, it.height, gap) }
        if (free(p)) return p
        for (ring in 1..max(rings, 1)) {
            val r = ring * step
            for (k in 0 until 24) {
                val a = k * PI / 12
                val q = BoardPoint(p.x + r * cos(a), p.y + r * sin(a))
                if (free(q)) return q
            }
        }
        return p
    }
}
