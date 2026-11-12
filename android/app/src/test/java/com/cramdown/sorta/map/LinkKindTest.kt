package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Universe's links (S05h), against iOS: archDirection (GraphRibbons.swift:527-537), the bow and
// its hump (:480-482, GraphMotion.swift:1893-1899), the near and far sets, and the beams' widths and
// trims (GraphUniverseScene.swift:42, :205-207). Made-up places.
class LinkKindTest {
    private fun close(expected: Vec3, got: Vec3, tolerance: Float = 1e-5f) {
        assertArrayEquals("$got not $expected", floatArrayOf(expected.x, expected.y, expected.z), floatArrayOf(got.x, got.y, got.z), tolerance)
    }

    @Test fun anArchBowsAwayFromItsCentre() {
        val dir = Vec3(1f, 0f, 0f)
        close(Vec3(0f, 1f, 0f), LinkCurve.archDirection(Vec3(-2f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(0f, -3f, 0f), dir))
        // the centre's part along the link is dropped: still square to it
        close(Vec3(0f, 0f, -1f), LinkCurve.archDirection(Vec3(-2f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(5f, 0f, 2f), dir))
    }

    @Test fun withTheCentreOnTheLineItTakesTheSideThenAnySquareDirection() {
        // first fallback: square to the link and z, cross(dir, z)
        close(Vec3(0f, -1f, 0f), LinkCurve.archDirection(Vec3(-2f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(1f, 0f, 0f)))
        // second: a link along z, any direction square to it
        val up = Vec3(0f, 0f, 1f)
        val any = LinkCurve.archDirection(Vec3(0f, 0f, -2f), Vec3(0f, 0f, 2f), Vec3.ZERO, up)
        close(LinkCurve.perpendicular(up), any)
        assertEquals(0f, any.dot(up), 1e-6f)
        assertEquals(1f, any.length(), 1e-6f)
    }

    /** One beam from (−2, 0, 0) to (2, 0, 0), both rocky, trimmed alike, of [kind], bowing from [centre]. */
    private fun beam(kind: Int, centre: Vec3 = Vec3(0f, -3f, 0f)): Pair<FloatArray, FloatArray> {
        val b = FloatArray(SpaceBodies.BEAM_STRIDE)
        val trim = 0.3f
        b[0] = -2f + trim; b[3] = 3f; b[4] = 2f - trim; b[7] = 3f; b[10] = 1f
        b[11] = -2f; b[17] = 2f
        b[23] = kind.toFloat(); b[24] = UniverseBeams.bow(kind); b[25] = if (kind == 1) 0f else -1f
        return b to floatArrayOf(centre.x, centre.y, centre.z)
    }

    /** The middle sample of the first strip written. */
    private fun middle(strips: LinkStrips): Vec3 {
        val o = strips.samples / 2 * 2 * LinkStrips.STRIDE
        return Vec3(strips.data[o], strips.data[o + 1], strips.data[o + 2])
    }

    @Test fun theHumpAtTheMiddleIsTheBowTimesTheLength() {
        for ((kind, bow) in listOf(1 to 0.10f, 2 to 0.15f)) {
            assertEquals(bow, UniverseBeams.bow(kind), 0f)
            val strips = LinkStrips(40)
            val (b, centres) = beam(kind)
            strips.write(b, 1, 1f, straight = false, centres = centres)
            // length 4 between the bodies' centres, bowed away from (0, −3, 0)
            close(Vec3(0f, bow * 4f, 0f), middle(strips))
        }
        // kind 0 (and every link outside the Universe) is straight
        val strips = LinkStrips(40)
        val (b, centres) = beam(0)
        strips.write(b, 1, 1f, straight = false, centres = centres)
        close(Vec3.ZERO, middle(strips))
    }

    @Test fun linesStraightDropsTheArch() {
        val strips = LinkStrips(40)
        val (b, centres) = beam(1)
        strips.write(b, 1, 1f, straight = true, centres = centres)
        close(Vec3.ZERO, middle(strips))
    }

    @Test fun theNearAndFarSets() {
        val beams = FloatArray(3 * SpaceBodies.BEAM_STRIDE)
        for ((k, kind) in listOf(0, 1, 2).withIndex()) beam(kind).first.copyInto(beams, k * SpaceBodies.BEAM_STRIDE)
        val centres = FloatArray(9)
        val strips = LinkStrips(20)
        strips.write(beams, 3, 1f, false, centres, set = 0)
        assertEquals("near: kinds 0 and 1", 2, strips.count)
        strips.write(beams, 3, 1f, false, centres, set = 1)
        assertEquals("far: kind 2", 1, strips.count)
        strips.write(beams, 3, 1f, false, centres)
        assertEquals("all", 3, strips.count)
        assertTrue(UniverseBeams.far(2))
        assertFalse(UniverseBeams.far(1))
        assertEquals(0.55f, UniverseBeams.farEnergy(bold = false), 0f)
        assertEquals(0.825f, UniverseBeams.farEnergy(bold = true), 0f)
    }

    @Test fun aMoonsLinkToItsPlanetIsNotDrawn() {
        assertFalse(UniverseBeams.drawn(3))
        for (kind in 0..2) assertTrue(UniverseBeams.drawn(kind))
        // packed both ways round
        val packed = UniverseBeams.pack(1, 7)
        assertEquals(1, UniverseBeams.kindOf(packed))
        assertEquals(7, UniverseBeams.centreOf(packed))
        assertEquals(-1, UniverseBeams.centreOf(UniverseBeams.pack(2, -1)))
        assertEquals(UniverseBeams.key(3, 9), UniverseBeams.key(9, 3))
    }

    @Test fun widthsTrimsAndSizes() {
        assertEquals(0.07f, UniverseBeams.HALF_WIDTH, 0f)
        assertArrayEquals(floatArrayOf(1.0f, 0.72f, 0.72f, 1.0f, 1.0f, 0.75f), UniverseBeams.TRIMS, 0f)
        for ((code, trim) in UniverseBeams.TRIMS.withIndex()) assertEquals(trim, UniverseBeams.trim(code), 0f)
        fun body(role: UniverseRole, ringed: Boolean = false) = UniverseBody("id", role, -1, -1, 0.25f, 0, false, ringed, 0, -1, 0, 0, -1,
            GraphOrbit.Fixed(Vec3.ZERO), Vec3.ZERO, "t", "t")
        // stars and unringed planets a fifth past their sphere; the rest as planned
        for (role in listOf(UniverseRole.STAR, UniverseRole.HOME, UniverseRole.GAS_GIANT, UniverseRole.ROCKY)) {
            assertEquals("$role", 0.3f, UniverseBeams.radius(body(role)), 1e-6f)
        }
        for (role in listOf(UniverseRole.GALAXY, UniverseRole.MOON, UniverseRole.PULSAR, UniverseRole.COMET, UniverseRole.OORT)) {
            assertEquals("$role", 0.25f, UniverseBeams.radius(body(role)), 0f)
        }
        assertEquals("a ringed giant keeps its size", 0.25f, UniverseBeams.radius(body(UniverseRole.GAS_GIANT, ringed = true)), 0f)
    }
}
