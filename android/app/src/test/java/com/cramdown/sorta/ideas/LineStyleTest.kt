package com.cramdown.sorta.ideas

import com.cramdown.sorta.map.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lines: Curved / Straight, mirroring iOS LineStyleTests (L1 to L6, the same numbers): the stored
 * choice, the map's straight segment, the board's curve, parallel links and lanes, straight on
 * the board and what follows the shape, and the board's first view.
 */
class LineStyleTest {
    private fun near(a: Float, b: Float, tolerance: Float = 0.0001f) = abs(a - b) <= tolerance
    private fun nearD(a: Double, b: Double, tolerance: Double = 0.0001) = abs(a - b) <= tolerance
    private fun size3(v: Vec3) = GraphStraightLine.size(v)
    private fun gap(a: BoardPoint, b: BoardPoint): Double { val dx = a.x - b.x; val dy = a.y - b.y; return sqrt(dx * dx + dy * dy) }

    // ---- L1 the stored choice ----

    @Test fun l1StoredChoice() {
        assertTrue("L1 nothing stored: Curved", GraphLineStyle.stored(null) == GraphLineStyle.CURVED && GraphLineStyle.standard == GraphLineStyle.CURVED)
        assertTrue("L1 true is Straight, false Curved", GraphLineStyle.stored(true) == GraphLineStyle.STRAIGHT && GraphLineStyle.stored(false) == GraphLineStyle.CURVED)
        assertEquals("L1 the key", "vignette.ideas.straightLines", GraphLineStyle.KEY)
        assertEquals("L1 the words", listOf("Curved", "Straight"), GraphLineStyle.entries.map { it.title })
        assertTrue("L1 -ideasLines straight forces it for a run",
            GraphLineStyle.parse(listOf("app", "-ideasLines", "straight")) == GraphLineStyle.STRAIGHT
                && GraphLineStyle.parse(listOf("app", "-ideasLines", "curved")) == GraphLineStyle.CURVED)
        assertNull("L1 no flag", GraphLineStyle.parse(listOf("app")))
        assertNull("L1 a flag with nothing after it", GraphLineStyle.parse(listOf("app", "-ideasLines")))
        assertNull("L1 a bad one", GraphLineStyle.parse(listOf("app", "-ideasLines", "wiggly")))
        assertTrue("L1 in force: the stored Boolean without the flag",
            GraphLineStyle.inForce(true) == GraphLineStyle.STRAIGHT && GraphLineStyle.inForce(false) == GraphLineStyle.CURVED)
    }

    // ---- L2 the 3D map's straight segment ----

    private val start = Vec3(1f, -2f, 0.5f)
    private val end = Vec3(7f, 3f, -4f)
    private val n = 40

    @Test fun l2StraightSegment() {
        val b = GraphStraightLine.Buffers()
        val total = GraphStraightLine.sample(start, end, 1f, n, b)
        val points = b.points; val lengths = b.lengths
        val whole = size3(end - start)
        assertTrue("L2 as many points as the curve's sampling", points.size == n + 1 && lengths.size == n + 1)
        assertTrue("L2 the ends match exactly", points[0] == start && points[n] == end)
        assertTrue("L2 the length is the segment's", near(total, whole, 0.0005f) && lengths[n] == total)

        var worstOff = 0f
        val axis = (end - start) * (1f / whole)
        for (p in points) {
            val rel = p - start
            val along = rel.dot(axis)
            worstOff = max(worstOff, size3(rel - axis * along))
        }
        assertTrue("L2 every sample on the one line: $worstOff", worstOff < 0.0005f)

        var worstSpacing = 0f
        val step = whole / n
        for (k in 1..n) worstSpacing = max(worstSpacing, abs(size3(points[k] - points[k - 1]) - step))
        assertTrue("L2 evenly spaced: $worstSpacing", worstSpacing < 0.0005f)

        var coordinateOK = lengths[0] == 0f
        for (k in 1..n) {
            val measured = size3(points[k] - points[0])
            if (!near(lengths[k], measured, 0.0005f) || lengths[k] < lengths[k - 1]) coordinateOK = false
        }
        assertTrue("L2 the length coordinate is the true distance along, rising from 0", coordinateOK)

        val grown = GraphStraightLine.Buffers()
        val half = GraphStraightLine.sample(start, end, 0.5f, n, grown)
        assertTrue("L2 half grown: half the length", near(half, whole * 0.5f, 0.0005f))
        val middle = start + (end - start) * 0.5f
        assertTrue("L2 half grown: runs from the start to the middle", grown.points[0] == start && size3(grown.points[n] - middle) < 0.0005f)
        var sameLine = true
        for (k in 0..n) {
            val onWhole = start + axis * grown.lengths[k]
            if (size3(grown.points[k] - onWhole) > 0.0005f) sameLine = false
        }
        assertTrue("L2 growing keeps every point where the grown link will have it", sameLine)

        var continuous = true
        var previousTotal = 0f
        for (g in 1..20) {
            val t = GraphStraightLine.sample(start, end, g / 20f, n, GraphStraightLine.Buffers())
            if (t < previousTotal || abs(t - previousTotal) > whole / 20 + 0.001f) continuous = false
            previousTotal = t
        }
        assertTrue("L2 the length grows smoothly with the link", continuous && near(previousTotal, whole, 0.0005f))

        val reused = GraphStraightLine.Buffers(Array(64) { Vec3(9f, 9f, 9f) }, FloatArray(64) { 9f })
        GraphStraightLine.sample(start, end, 1f, 20, reused)
        assertTrue("L2 buffers are reused, never shrunk", reused.points.size == 64 && reused.points[20] == end && reused.lengths[0] == 0f)

        val zero = GraphStraightLine.Buffers()
        val none = GraphStraightLine.sample(start, start, 1f, 8, zero)
        assertTrue("L2 a zero-length link is a point, no NaN", none == 0f && zero.points.all { it == start })
        val clamped = GraphStraightLine.sample(start, end, 3f, 8, zero)
        assertTrue("L2 grow is clamped to the whole link", near(clamped, whole, 0.0005f) && zero.points[8] == end)
    }

    // ---- L3 the board's curve ----

    private val p = BoardPoint(100.0, 200.0)
    private val q = BoardPoint(400.0, 260.0)
    private val pq get() = gap(p, q)
    private val ux get() = (q.x - p.x) / pq
    private val uy get() = (q.y - p.y) / pq
    private val curved get() = IdeaLinkShape.samples(GraphLineStyle.CURVED, p, q, count = 40)

    @Test fun l3BoardCurve() {
        val curved = curved
        assertTrue("L3 the curve's ends are the card centres", gap(curved[0], p) < 1e-9 && gap(curved[40], q) < 1e-9)
        var symmetric = true
        val mx = (p.x + q.x) / 2
        val my = (p.y + q.y) / 2
        for (k in 0..40) {
            val a = curved[k]; val b = curved[40 - k]
            val alongA = (a.x - mx) * ux + (a.y - my) * uy
            val alongB = (b.x - mx) * ux + (b.y - my) * uy
            val acrossA = -(a.x - mx) * uy + (a.y - my) * ux
            val acrossB = -(b.x - mx) * uy + (b.y - my) * ux
            if (!nearD(alongA, -alongB, 1e-6) || !nearD(acrossA, acrossB, 1e-6)) symmetric = false
        }
        assertTrue("L3 the bow is symmetric end to end", symmetric)
        val top = IdeaLinkShape.point(GraphLineStyle.CURVED, p, q, t = 0.5)
        val across = -(top.x - mx) * uy + (top.y - my) * ux
        assertTrue("L3 a gentle bow: its middle a ninth of the length off the line: $across", nearD(across, pq * IdeaLinkShape.BOW / 2, 1e-6))
        assertTrue("L3 it leans to the left of p-to-q, always", across > 0)
        val far = BoardPoint(100.0, 900.0)
        val bent = IdeaLinkShape.point(GraphLineStyle.CURVED, p, far, t = 0.5)
        assertTrue("L3 a vertical link leans the same way, relative to its travel", bent.x < p.x)
    }

    // ---- L4 parallel links don't overlap ----

    @Test fun l4ParallelLinks() {
        val p2 = BoardPoint(100.0, 230.0)
        val q2 = BoardPoint(400.0, 290.0)
        var closest = Double.MAX_VALUE
        for (a in curved) closest = min(closest, IdeaLinkShape.distance(a, GraphLineStyle.CURVED, p2, q2))
        val offset = 30 * ux
        assertTrue("L4 two parallel links 30 apart stay apart all along: $closest vs $offset", closest > offset * 0.66)

        val lanes = IdeaLinkShape.lanes(listOf("ab", "cd", "ab", "ab"))
        assertEquals("L4 lanes per pair", listOf(0 to 3, 0 to 1, 1 to 3, 2 to 3), lanes)
        assertTrue("L4 one link: today's bow", nearD(IdeaLinkShape.bowFactor(0, 1), IdeaLinkShape.BOW))

        val midpoints = (0 until 3).map { IdeaLinkShape.point(GraphLineStyle.CURVED, p, q, it, 3, 0.5) }
        var fanApart = true
        for (a in 0 until 3) for (b in a + 1 until 3) if (gap(midpoints[a], midpoints[b]) < 12) fanApart = false
        assertTrue("L4 three links between one pair fan out, their middles well apart: $midpoints", fanApart)

        var fanTouch = false
        for (lane in 0 until 2) {
            val mine = IdeaLinkShape.samples(GraphLineStyle.CURVED, p, q, lane, 2, 40)
            for (k in 4..36) if (IdeaLinkShape.distance(mine[k], GraphLineStyle.CURVED, p, q, 1 - lane, 2) < 2) fanTouch = true
        }
        assertTrue("L4 fanned curves meet only at the cards", !fanTouch)

        val s0 = IdeaLinkShape.straightEnds(p, q, 0, 2)
        val s1 = IdeaLinkShape.straightEnds(p, q, 1, 2)
        val apart = IdeaLinkShape.segmentDistance(s0.first, s1.first, s1.second)
        assertTrue("L4 straight lanes side by side, a lane's gap apart: $apart", nearD(apart, IdeaLinkShape.LANE_GAP, 1e-6))
        val single = IdeaLinkShape.straightEnds(p, q)
        assertTrue("L4 one straight link: exactly centre to centre", gap(single.first, p) < 1e-9 && gap(single.second, q) < 1e-9)
    }

    // ---- L5 straight on the board, and what follows the shape ----

    @Test fun l5StraightAndWhatFollows() {
        val straight = IdeaLinkShape.samples(GraphLineStyle.STRAIGHT, p, q, count = 10)
        var straightOK = true
        for (k in 0..10) if (gap(straight[k], IdeaLinkShape.lerp(p, q, k / 10.0)) > 1e-9) straightOK = false
        assertTrue("L5 straight: evenly along the segment", straightOK)

        val straightIn = IdeaLinkShape.endDirection(GraphLineStyle.STRAIGHT, p, q)
        assertTrue("L5 an arrowhead on a straight link points along it", nearD(straightIn.x, ux, 1e-6) && nearD(straightIn.y, uy, 1e-6))
        val curvedIn = IdeaLinkShape.endDirection(GraphLineStyle.CURVED, p, q)
        val control = IdeaLinkShape.control(p, q)
        val tangent = IdeaLinkShape.unit(BoardPoint(q.x - control.x, q.y - control.y))
        assertTrue("L5 an arrowhead on a curved link follows the curve in: $curvedIn $tangent",
            gap(curvedIn, tangent) < 0.02 && gap(curvedIn, straightIn) > 0.1)

        val onCurve = IdeaLinkShape.point(GraphLineStyle.CURVED, p, q, t = 0.5)
        val onLine = IdeaLinkShape.lerp(p, q, 0.5)
        assertTrue("L5 a hit test finds the curve where it is drawn",
            IdeaLinkShape.distance(onCurve, GraphLineStyle.CURVED, p, q) < 0.5 && IdeaLinkShape.distance(onLine, GraphLineStyle.CURVED, p, q) > 10)
        assertTrue("L5 and the straight line where it is drawn",
            IdeaLinkShape.distance(onLine, GraphLineStyle.STRAIGHT, p, q) < 1e-9 && IdeaLinkShape.distance(onCurve, GraphLineStyle.STRAIGHT, p, q) > 10)
    }

    // ---- L6 the board's first view fits every card ----

    private fun allInside(centres: List<BoardPoint>, cam: IdeaBoardFit.Camera, card: IdeaBoardFit.Size, view: IdeaBoardFit.Size,
                          hidden: Double, m: IdeaBoardFit.Margins): Boolean {
        val midY = (view.height - hidden) / 2
        for (c in centres) {
            val x = view.width / 2 + cam.panX + c.x * cam.zoom
            val y = midY + cam.panY + c.y * cam.zoom
            val halfW = card.width * cam.zoom / 2
            val halfH = card.height * cam.zoom / 2
            if (x - halfW < m.leading - 1e-6 || x + halfW > view.width - m.trailing + 1e-6) return false
            if (y - halfH < m.top - 1e-6 || y + halfH > view.height - hidden - m.bottom + 1e-6) return false
        }
        return true
    }

    @Test fun l6FirstViewFitsEveryCard() {
        val card = IdeaBoardFit.Size(150.0, 88.0)
        val room = IdeaBoardFit.Margins(16.0, 16.0, 16.0, 76.0)
        val spread = listOf(BoardPoint(-330.0, -120.0), BoardPoint(360.0, 40.0), BoardPoint(10.0, 300.0), BoardPoint(-40.0, -260.0))
        val phone = IdeaBoardFit.Size(402.0, 760.0)
        val wide = IdeaBoardFit.camera(spread, card, phone, 220.0, room)
        assertTrue("L6 a wide board on a phone: zoomed out until every card is in: $wide",
            wide.zoom < 1 && allInside(spread, wide, card, phone, 220.0, room))

        val pad = IdeaBoardFit.Size(960.0, 1100.0)
        val few = listOf(BoardPoint(80.0, 40.0), BoardPoint(240.0, 120.0))
        val fitNear = IdeaBoardFit.camera(few, card, pad, 180.0, room)
        assertTrue("L6 a few cards on an iPad: never zoomed in past 1, centred in the room: $fitNear",
            fitNear.zoom == 1.0 && allInside(few, fitNear, card, pad, 180.0, room)
                && nearD(pad.width / 2 + fitNear.panX + 160, 16 + (960 - 92) / 2.0))

        val fitNone = IdeaBoardFit.camera(emptyList(), card, phone, 220.0, room)
        assertEquals("L6 an empty board: the middle at zoom 1", IdeaBoardFit.Camera(1.0, 0.0, 0.0), fitNone)

        val farApart = listOf(BoardPoint(-5000.0, 0.0), BoardPoint(5000.0, 0.0))
        val fitFloor = IdeaBoardFit.camera(farApart, card, phone, 220.0, room)
        assertTrue("L6 a vast board stops at the board's own floor, centred: $fitFloor",
            fitFloor.zoom == 0.3 && nearD(fitFloor.panX, 16 + (402 - 92) / 2.0 - 201))

        // at the largest text sizes a card is far taller than the least card: the fit takes each
        // card's own size, so the tall one at the foot is in too (S05c)
        fun insideSized(points: List<BoardPoint>, sizes: List<IdeaBoardFit.Size>, cam: IdeaBoardFit.Camera): Boolean {
            val midY = (phone.height - 220.0) / 2
            return points.zip(sizes).all { (p, s) ->
                val x = phone.width / 2 + cam.panX + p.x * cam.zoom
                val y = midY + cam.panY + p.y * cam.zoom
                val halfW = s.width * cam.zoom / 2
                val halfH = s.height * cam.zoom / 2
                x - halfW >= room.leading - 1e-6 && x + halfW <= phone.width - room.trailing + 1e-6 &&
                    y - halfH >= room.top - 1e-6 && y + halfH <= phone.height - 220.0 - room.bottom + 1e-6
            }
        }
        val tallAt = listOf(BoardPoint(0.0, -200.0), BoardPoint(0.0, 0.0), BoardPoint(0.0, 300.0))
        val tallSizes = listOf(card, card, IdeaBoardFit.Size(150.0, 400.0))
        val fitTall = IdeaBoardFit.camera(tallAt, card, phone, 220.0, room, sizes = tallSizes)
        val fitLeast = IdeaBoardFit.camera(tallAt, card, phone, 220.0, room)
        assertTrue("L6 a tall card at the foot is fitted by its own size, clear of the foot: $fitTall",
            insideSized(tallAt, tallSizes, fitTall) && !insideSized(tallAt, tallSizes, fitLeast)
                && IdeaBoardFit.camera(spread, card, phone, 220.0, room, sizes = spread.map { card }) == wide)
    }

    /**
     * S05m item 1: the first view on four windows and the two panes, on the stacked fixture's spread
     * and on four cards apart. The box fills at least 85 % of the room (16 in from each edge, below
     * the strip) along the side that binds, or the zoom is 1; no card is under the tools; every card
     * is in the room and at least a target tall; never zoomed in past 1.
     */
    @Test fun l6FirstViewFillsTheBoard() {
        data class Window(val name: String, val view: IdeaBoardFit.Size, val hidden: Double, val top: Double,
                          val tools: IdeaBoardFit.Tools, val target: Double)
        val windows = listOf(
            Window("Android phone", IdeaBoardFit.Size(411.0, 914.0), 82.0, 56.0, IdeaBoardFit.Tools(80.0, 208.0, false), 48.0),
            Window("Android tablet", IdeaBoardFit.Size(1280.0, 800.0), 0.0, 56.0, IdeaBoardFit.Tools(80.0, 208.0, true), 48.0),
            Window("Android tablet's map pane", IdeaBoardFit.Size(768.0, 800.0), 0.0, 56.0, IdeaBoardFit.Tools(80.0, 208.0, true), 48.0),
            Window("iPhone", IdeaBoardFit.Size(402.0, 874.0), 94.0, 60.0, IdeaBoardFit.Tools(76.0, 196.0, false), 44.0),
            Window("iPad", IdeaBoardFit.Size(1032.0, 1376.0), 20.0, 60.0, IdeaBoardFit.Tools(76.0, 196.0, false), 44.0),
            Window("iPad's map pane", IdeaBoardFit.Size(619.0, 1300.0), 20.0, 60.0, IdeaBoardFit.Tools(76.0, 196.0, false), 44.0))
        // the stacked fixture as both platforms store it (IdeasFixture.stacked, MadeUpExport stacked)
        val stacked = listOf(card(1, 120.0, -40.0), card(2, 0.0, 0.0), card(3, -75.5, 210.25), card(4, 0.0, 33.0),
            card(5, 0.0, 0.0), card(6, 0.0, 0.0), card(7, 0.0, -230.0)).map { it.copy(width = 180.0) }
        val four = listOf(card(1, -330.0, -120.0), card(2, 360.0, 40.0), card(3, 10.0, 300.0), card(4, -40.0, -260.0))
        val least = IdeaBoardFit.Size(150.0, 88.0)
        for (w in windows) for ((name, cards) in listOf("stacked" to stacked, "four apart" to four)) {
            val at = IdeaBoardSpread.places(cards)
            val centres = cards.map { at.getValue(it.id) }
            val sizes = cards.map { IdeaBoardFit.Size(it.width, it.height) }
            val cam = IdeaBoardFit.firstView(centres, sizes, least, w.view, w.hidden, w.top, w.tools, w.target)
            val seen = w.view.height - w.hidden
            val roomW = w.view.width - 32; val roomH = seen - w.top - 32
            val spanW = cards.indices.maxOf { centres[it].x + sizes[it].width / 2 } - cards.indices.minOf { centres[it].x - sizes[it].width / 2 }
            val spanH = cards.indices.maxOf { centres[it].y + sizes[it].height / 2 } - cards.indices.minOf { centres[it].y - sizes[it].height / 2 }
            val fill = max(spanW * cam.zoom / roomW, spanH * cam.zoom / roomH)
            val tools = IdeaBoardFit.toolsRect(w.view, w.hidden, w.tools)
            var under = false; var outside = false
            cards.indices.forEach { k ->
                val x = w.view.width / 2 + cam.panX + centres[k].x * cam.zoom
                val y = seen / 2 + cam.panY + centres[k].y * cam.zoom
                val hw = sizes[k].width * cam.zoom / 2; val hh = sizes[k].height * cam.zoom / 2
                if (x + hw > tools[0] && x - hw < tools[2] && y + hh > tools[1] && y - hh < tools[3]) under = true
                if (x - hw < 16 - 1e-6 || x + hw > w.view.width - 16 + 1e-6 || y - hh < 16 + w.top - 1e-6 || y + hh > seen - 16 + 1e-6) outside = true
            }
            val label = "${w.name}, $name: zoom ${cam.zoom}, fill $fill"
            assertTrue("L6 the box fills at least 85 % of the room, or the zoom is 1: $label", fill >= 0.85 - 1e-9 || cam.zoom >= 1 - 1e-9)
            // a board too wide for the room at the floor opens on its middle, as before (L7)
            val tooWide = fill > 1 + 1e-9 && cam.zoom <= IdeaBoardFit.firstRange(sizes.minOf { it.height }, w.target).start + 1e-9
            assertTrue("L6 no card under the tools: $label", tooWide || !under)
            assertTrue("L6 every card in the room: $label", tooWide || !outside)
            assertTrue("L6 never past 1, every card a target tall: $label",
                cam.zoom <= 1 + 1e-9 && sizes.minOf { it.height } * cam.zoom >= w.target - 1e-6)
        }
        // the old trailing 76 down the whole height held the phone's box to 84 % across
        val phone = windows[0]
        val at = IdeaBoardSpread.places(stacked)
        val old = IdeaBoardFit.camera(stacked.map { at.getValue(it.id) }, least, phone.view, phone.hidden,
            IdeaBoardFit.Margins(16.0 + phone.top, 16.0, 16.0, 76.0), IdeaBoardFit.firstRange(88.0, 48.0),
            stacked.map { IdeaBoardFit.Size(it.width, it.height) })
        val now = IdeaBoardFit.firstView(stacked.map { at.getValue(it.id) }, stacked.map { IdeaBoardFit.Size(it.width, it.height) },
            least, phone.view, phone.hidden, phone.top, phone.tools, phone.target)
        assertTrue("L6 the phone opens larger than with the old margins: ${now.zoom} > ${old.zoom}", now.zoom > old.zoom)
        // a board with a card where the tools are: it is moved or zoomed clear of them
        val corner = listOf(card(1, -150.0, -300.0), card(2, 150.0, 300.0))
        val cornerAt = corner.map { it.centre }
        val cornerCam = IdeaBoardFit.firstView(cornerAt, corner.map { IdeaBoardFit.Size(it.width, it.height) }, least,
            phone.view, phone.hidden, phone.top, phone.tools, phone.target)
        val r = IdeaBoardFit.toolsRect(phone.view, phone.hidden, phone.tools)
        val seen = phone.view.height - phone.hidden
        val bx = phone.view.width / 2 + cornerCam.panX + 150 * cornerCam.zoom
        val by = seen / 2 + cornerCam.panY + 300 * cornerCam.zoom
        assertTrue("L6 a card at the bottom-trailing corner is drawn clear of the tools: $cornerCam",
            !(bx + 75 * cornerCam.zoom > r[0] && by + 44 * cornerCam.zoom > r[1]))
    }

    // ---- L7 the first view keeps every card big enough to tap ----

    @Test fun l7FirstViewKeepsCardsTappable() {
        val card = IdeaBoardFit.Size(150.0, 88.0)
        val room = IdeaBoardFit.Margins(16.0, 16.0, 16.0, 76.0)
        val phone = IdeaBoardFit.Size(402.0, 760.0)
        val range = IdeaBoardFit.firstRange(88.0, 48.0)
        val farApart = listOf(BoardPoint(-5000.0, 0.0), BoardPoint(5000.0, 0.0))
        val fit = IdeaBoardFit.camera(farApart, card, phone, 220.0, room, range)
        assertTrue("L7 a vast board opens with its cards 48 dp tall, on its middle: $fit",
            nearD(fit.zoom * 88, 48.0) && nearD(fit.panX, 16 + (402 - 92) / 2.0 - 201))
        assertTrue("L7 it never asks for more than 1, nor less than the board's floor",
            IdeaBoardFit.firstRange(20.0, 48.0) == 1.0..1.0 && IdeaBoardFit.firstRange(400.0, 48.0) == 0.3..1.0)
    }

    // ---- L8 a card let go of on another settles beside it ----

    @Test fun l8DropSettlesClear() {
        val others = listOf(BoardPoint(0.0, 0.0), BoardPoint(0.0, 96.0))
        val clear = BoardPoint(400.0, 400.0)
        assertEquals("L8 let go in a clear place, it stays", clear, IdeaBoardDrop.settle(clear, 150.0, 88.0, others))
        val onTop = BoardPoint(10.0, 4.0)
        val settled = IdeaBoardDrop.settle(onTop, 150.0, 88.0, others)
        val gap = IdeaBoardDrop.GAP
        assertEquals("L8 the board keeps 12 between cards (S05m)", 12.0, gap, 0.0)
        fun overlaps(a: BoardPoint, b: BoardPoint) = abs(a.x - b.x) < 150 + gap - 1e-6 && abs(a.y - b.y) < 88 + gap - 1e-6
        val moved = hypot(settled.x - onTop.x, settled.y - onTop.y)
        assertTrue("L8 let go on a card, it moves clear of every card, no further than a card's width and a gap: $settled",
            others.none { overlaps(settled, it) } && moved > 0 && moved <= 150 + gap + 8)
    }

    // ---- L9 the board draws no card over another (S05c) ----

    private fun card(n: Int, x: Double, y: Double, h: Double = 88.0) = IdeaBoardSpread.Card("c$n", BoardPoint(x, y), 150.0, h)
    private fun noneCover(cards: List<IdeaBoardSpread.Card>, at: Map<String, BoardPoint>): Boolean {
        for (i in cards.indices) for (j in i + 1 until cards.size) {
            val a = cards[i]; val b = cards[j]
            val p = at[a.id] ?: return false
            val q = at[b.id] ?: return false
            if (IdeaBoardDrop.covers(p, a.width, a.height, q, b.width, b.height, 12.0 - 1e-6)) return false
        }
        return true
    }

    @Test fun l9SpreadDrawsNoCardOverAnother() {
        val apart = listOf(card(1, 0.0, 0.0), card(2, 200.0, 0.0), card(3, 0.0, 120.0))
        val apartAt = IdeaBoardSpread.places(apart)
        assertTrue("L9 cards that cover none stay where they are stored: $apartAt", apart.all { apartAt[it.id] == it.centre })
        val pair = listOf(card(1, 10.0, 10.0), card(2, 10.0, 10.0))
        val pairAt = IdeaBoardSpread.places(pair)
        assertTrue("L9 two on one place are drawn round it, the first moved with them: $pairAt",
            noneCover(pair, pairAt) && pairAt[pair[0].id] != pair[0].centre && nearD(middle(pair, pairAt).x, 10.0, 1.0) && nearD(middle(pair, pairAt).y, 10.0, 1.0))
        // the second is drawn away from the first; the third covers the second's stored place but
        // neither drawn card, so it keeps its own
        val clear = listOf(card(1, 0.0, 0.0), card(2, 10.0, 0.0), card(3, 165.0, -50.0))
        val clearAt = IdeaBoardSpread.places(clear)
        assertTrue("L9 a card stored clear of every drawn card keeps its place, though it covers another's stored place: $clearAt",
            clearAt[clear[2].id] == clear[2].centre && clearAt[clear[1].id] != clear[1].centre && noneCover(clear, clearAt))
        val stack = (1..5).map { card(it, 0.0, 0.0) }
        val stackAt = IdeaBoardSpread.places(stack)
        assertTrue("L9 five at (0, 0) end apart round it, none covering another: $stackAt",
            stackAt.size == 5 && noneCover(stack, stackAt) && nearD(middle(stack, stackAt).x, 0.0, 1.0) && nearD(middle(stack, stackAt).y, 0.0, 1.0))
        // 96 down clears two 88 dp cards, not a 200 dp one
        val tall = listOf(card(1, 0.0, 0.0, h = 200.0), card(2, 0.0, 96.0))
        val tallAt = IdeaBoardSpread.places(tall)
        assertTrue("L9 a tall card and a short one are kept apart by their own sizes: $tallAt",
            tallAt[tall[0].id] == tall[0].centre && tallAt[tall[1].id] != tall[1].centre && noneCover(tall, tallAt))
        assertEquals("L9 the same cards give the same places", stackAt, IdeaBoardSpread.places(stack))
        assertEquals("L9 the store's order decides who goes where", pairAt[pair[0].id], IdeaBoardSpread.places(listOf(pair[1], pair[0]))[pair[1].id])
        val crowd = (1..300).map { card(it, 0.0, 0.0) }
        val crowdAt = IdeaBoardSpread.places(crowd, rings = 40)
        assertTrue("L9 a crowd on one point ends round it, none covering another",
            crowdAt.size == 300 && noneCover(crowd, crowdAt) && nearD(middle(crowd, crowdAt).x, 0.0, 1.0) && nearD(middle(crowd, crowdAt).y, 0.0, 1.0))
        // a card let go of settles clear of the drawn cards, as the board does it
        val a = card(1, 0.0, 0.0); val b = card(2, 30.0, 0.0)
        val drawn = IdeaBoardSpread.places(listOf(a, b))
        val drop = IdeaBoardSpread.settle(BoardPoint(30.0, 0.0), 150.0, 88.0,
            listOf(a.copy(centre = drawn.getValue(a.id)), b.copy(centre = drawn.getValue(b.id))))
        val c = card(3, drop.x, drop.y)
        val after = IdeaBoardSpread.places(listOf(a, c, b))
        assertTrue("L9 a card let go of clear of the drawn cards is drawn where it settled, ahead of a moved one in the order: $after",
            after[c.id] == drop && noneCover(listOf(a, c, b), after))
        assertTrue("L9 no cards, no places", IdeaBoardSpread.places(emptyList()).isEmpty())
    }

    /** The middle of the box round [cards] as drawn at [at]. */
    private fun middle(cards: List<IdeaBoardSpread.Card>, at: Map<String, BoardPoint>): BoardPoint {
        val minX = cards.minOf { at.getValue(it.id).x - it.width / 2 }; val maxX = cards.maxOf { at.getValue(it.id).x + it.width / 2 }
        val minY = cards.minOf { at.getValue(it.id).y - it.height / 2 }; val maxY = cards.maxOf { at.getValue(it.id).y + it.height / 2 }
        return BoardPoint((minX + maxX) / 2, (minY + maxY) / 2)
    }

    // S05m item 2: cards on one point are drawn round it; a card stored off it on the side it is stored
    @Test fun l9SpreadDrawsAStackRoundItsPoint() {
        val three = (1..3).map { card(it, 0.0, 0.0) }
        val bell = card(4, 0.0, 33.0)
        val all = three + bell
        val at = IdeaBoardSpread.places(all)
        val mid = middle(three, at)
        assertTrue("L9 three on one point are drawn round it, the box's middle within 1: $mid", gap(mid, BoardPoint(0.0, 0.0)) <= 1)
        val bottom = three.maxOf { at.getValue(it.id).y + it.height / 2 }
        val bellTop = at.getValue(bell.id).y - bell.height / 2
        assertTrue("L9 the bell, stored 33 below, is drawn below them, a gap clear: $bellTop vs $bottom",
            bellTop >= bottom + 12 - 1e-6 && noneCover(all, at))
        // stored above, to the left and to the right: drawn on that side
        val above = IdeaBoardSpread.places(three + card(4, 10.0, -30.0))
        assertTrue("L9 a card stored above the point is drawn above the block",
            above.getValue("c4").y + 44 <= three.minOf { above.getValue(it.id).y - 44 } - 12 + 1e-6)
        val left = IdeaBoardSpread.places(three + card(4, -120.0, 5.0))
        assertTrue("L9 a card stored left of it is drawn left of the block",
            left.getValue("c4").x + 75 <= three.minOf { left.getValue(it.id).x - 75 } - 12 + 1e-6)
        val right = IdeaBoardSpread.places(three + card(4, 120.0, -5.0))
        assertTrue("L9 a card stored right of it is drawn right of the block",
            right.getValue("c4").x - 75 >= three.maxOf { right.getValue(it.id).x + 75 } + 12 - 1e-6)
        // the stored order puts the bell first: it is still drawn below the block
        val bellFirst = IdeaBoardSpread.places(listOf(bell) + three)
        assertTrue("L9 the bell is drawn below the block whatever its place in the order",
            bellFirst.getValue(bell.id).y - 44 >= three.maxOf { bellFirst.getValue(it.id).y + 44 } + 12 - 1e-6)
        // a lone card that covers nothing keeps its place
        val lone = card(5, 600.0, 0.0)
        assertEquals("L9 a lone card clear of the block keeps its place", lone.centre, IdeaBoardSpread.places(all + lone)[lone.id])
        // two points whose blocks would meet are drawn apart, each whole
        val twoPoints = (1..4).map { card(it, 0.0, 0.0) } + (5..8).map { card(it, 100.0, 0.0) }
        val twoAt = IdeaBoardSpread.places(twoPoints)
        assertTrue("L9 two blocks that would meet are drawn apart: $twoAt", noneCover(twoPoints, twoAt)
            && gap(middle(twoPoints.take(4), twoAt), BoardPoint(0.0, 0.0)) <= 1)
        assertEquals("L9 the same input, the same places", at, IdeaBoardSpread.places(all))
    }

    // v13 review: -0.0 is the point 0.0, as iOS's Key has it (Double's == there)
    @Test fun l9SpreadTakesMinusZeroAsZero() {
        val signed = listOf(card(1, 0.0, 0.0), card(2, -0.0, 0.0), card(3, 0.0, -0.0))
        val at = IdeaBoardSpread.places(signed)
        assertTrue("L9 cards at 0.0 and -0.0 are one block round the point: $at",
            noneCover(signed, at) && gap(middle(signed, at), BoardPoint(0.0, 0.0)) <= 1)
        assertEquals("L9 the same places as three at (0.0, 0.0)", IdeaBoardSpread.places((1..3).map { card(it, 0.0, 0.0) }), at)
    }
}
