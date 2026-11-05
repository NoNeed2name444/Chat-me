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
        fun overlaps(a: BoardPoint, b: BoardPoint) = abs(a.x - b.x) < 150 + 8 - 1e-6 && abs(a.y - b.y) < 88 + 8 - 1e-6
        val moved = hypot(settled.x - onTop.x, settled.y - onTop.y)
        assertTrue("L8 let go on a card, it moves clear of every card, no further than a card's width and a gap: $settled",
            others.none { overlaps(settled, it) } && moved > 0 && moved <= 150 + 8 + 8)
    }

    // ---- L9 the board draws no card over another (S05c) ----

    private fun card(n: Int, x: Double, y: Double, h: Double = 88.0) = IdeaBoardSpread.Card("c$n", BoardPoint(x, y), 150.0, h)
    private fun noneCover(cards: List<IdeaBoardSpread.Card>, at: Map<String, BoardPoint>): Boolean {
        for (i in cards.indices) for (j in i + 1 until cards.size) {
            val a = cards[i]; val b = cards[j]
            val p = at[a.id] ?: return false
            val q = at[b.id] ?: return false
            if (IdeaBoardDrop.covers(p, a.width, a.height, q, b.width, b.height, 8.0 - 1e-6)) return false
        }
        return true
    }

    @Test fun l9SpreadDrawsNoCardOverAnother() {
        val apart = listOf(card(1, 0.0, 0.0), card(2, 200.0, 0.0), card(3, 0.0, 120.0))
        val apartAt = IdeaBoardSpread.places(apart)
        assertTrue("L9 cards that cover none stay where they are stored: $apartAt", apart.all { apartAt[it.id] == it.centre })
        val pair = listOf(card(1, 10.0, 10.0), card(2, 10.0, 10.0))
        val pairAt = IdeaBoardSpread.places(pair)
        assertTrue("L9 two on one place end apart, the first kept: $pairAt", pairAt[pair[0].id] == pair[0].centre && noneCover(pair, pairAt))
        // the second is drawn away from the first; the third covers the second's stored place but
        // neither drawn card, so it keeps its own
        val clear = listOf(card(1, 0.0, 0.0), card(2, 10.0, 0.0), card(3, 165.0, -50.0))
        val clearAt = IdeaBoardSpread.places(clear)
        assertTrue("L9 a card stored clear of every drawn card keeps its place, though it covers another's stored place: $clearAt",
            clearAt[clear[2].id] == clear[2].centre && clearAt[clear[1].id] != clear[1].centre && noneCover(clear, clearAt))
        val stack = (1..5).map { card(it, 0.0, 0.0) }
        val stackAt = IdeaBoardSpread.places(stack)
        assertTrue("L9 five at (0, 0) end apart, none covering another: $stackAt", stackAt.size == 5 && noneCover(stack, stackAt))
        // 96 down clears two 88 dp cards, not a 200 dp one
        val tall = listOf(card(1, 0.0, 0.0, h = 200.0), card(2, 0.0, 96.0))
        val tallAt = IdeaBoardSpread.places(tall)
        assertTrue("L9 a tall card and a short one are kept apart by their own sizes: $tallAt",
            tallAt[tall[0].id] == tall[0].centre && tallAt[tall[1].id] != tall[1].centre && noneCover(tall, tallAt))
        assertEquals("L9 the same cards give the same places", stackAt, IdeaBoardSpread.places(stack))
        assertEquals("L9 the store's order decides who stays", pair[1].centre, IdeaBoardSpread.places(listOf(pair[1], pair[0]))[pair[1].id])
        val crowd = (1..300).map { card(it, 0.0, 0.0) }
        val crowdAt = IdeaBoardSpread.places(crowd, rings = 40)
        assertTrue("L9 a crowd past the rings still ends with none covering another", crowdAt.size == 300 && noneCover(crowd, crowdAt))
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
}
