package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// How the chosen body is marked (S05g), in iOS's numbers: Space's orbit ring (GraphStyleAnimator.
// placeOrbit, GraphNodeFactory.makeOrbit), a chosen black hole's hot ring and disk (GraphLook), the
// Neurons' hot halo (GraphNeuronLook.haloMaterial), Performance's chosen point (GraphPerfShaders) and
// the pop (GraphMotion.pop and stepPops), and that the shaders carry them.
class MapMarkTest {
    private val eps = 1e-5f

    // ---- the orbit ring ----

    @Test fun theRingClosesInFromOnePointThreeFiveTimesItsSize() {
        val reach = 0.5f
        val full = reach * 2.2f / 0.9f * 2f
        assertEquals("grown: 2.2 reaches out at the line, the line at 0.9 of the half width", full, MapMark.orbitSize(reach, 1f, 1f), eps)
        assertEquals("new: 1.35 times", full * 1.35f, MapMark.orbitSize(reach, 0f, 1f), eps)
        // eased out cubically: half way in time is seven eighths of the way in size
        assertEquals(full * (1.35f - 0.35f * 0.875f), MapMark.orbitSize(reach, 0.5f, 1f), eps)
        assertEquals("the pop scales it", full * 1.12f, MapMark.orbitSize(reach, 1f, 1.12f), eps)
        assertEquals("never below 0.05 of it", full * 0.05f, MapMark.orbitSize(reach, 1f, 0f), eps)
        assertEquals("grow is clamped", MapMark.orbitSize(reach, 1f, 1f), MapMark.orbitSize(reach, 3f, 1f), eps)
        var last = Float.MAX_VALUE
        for (k in 0..20) {
            val size = MapMark.orbitSize(reach, k / 20f, 1f)
            assertTrue("it only closes in", size <= last + eps)
            last = size
        }
    }

    @Test fun itGrowsOverAQuarterSecondOrAtOnceWithAnimationsOff() {
        assertEquals(0.25f, MapMark.ORBIT_GROW_SECONDS, 0f)
        assertEquals(0f, MapMark.orbitStart(lively = true), 0f)
        assertEquals(1f, MapMark.orbitStart(lively = false), 0f)
        assertEquals(0.5f, MapMark.orbitGrow(0f, 0.125f), eps)
        assertEquals(1f, MapMark.orbitGrow(0.9f, 0.1f), 0f)
        assertEquals("no time, no growth", 0.3f, MapMark.orbitGrow(0.3f, 0f), 0f)
        var g = MapMark.orbitStart(true)
        repeat(15) { g = MapMark.orbitGrow(g, 1f / 60) }
        assertEquals("at 60 Hz, grown in 15 frames", 1f, g, eps)
    }

    @Test fun itsPlaneIsTippedThenRolledAndScaled() {
        assertEquals(1.15f, MapMark.ORBIT_TIP, 0f)
        assertEquals(0.2f, MapMark.ORBIT_ROLL, 0f)
        val m = MapMark.orbitModel(1f, 2f, 3f, 4f)
        assertArrayEquals("placed at the body", floatArrayOf(1f, 2f, 3f, 1f), m.copyOfRange(12, 16), 0f)
        for (c in 0 until 3) {
            val len = sqrt(m[4 * c] * m[4 * c] + m[4 * c + 1] * m[4 * c + 1] + m[4 * c + 2] * m[4 * c + 2])
            assertEquals("column $c is the size long", 4f, len, 1e-4f)
            assertEquals(0f, m[4 * c + 3], 0f)
        }
        // its normal is Rz(0.2)·Rx(1.15) applied to z
        val ct = cos(1.15f); val st = sin(1.15f); val cr = cos(0.2f); val sr = sin(0.2f)
        assertArrayEquals(floatArrayOf(sr * st * 4f, -cr * st * 4f, ct * 4f), m.copyOfRange(8, 11), 1e-5f)
        // its x axis is only rolled
        assertArrayEquals(floatArrayOf(cr * 4f, sr * 4f, 0f), m.copyOfRange(0, 3), 1e-5f)
        // the columns are square to each other
        fun dot(a: Int, b: Int) = m[4 * a] * m[4 * b] + m[4 * a + 1] * m[4 * b + 1] + m[4 * a + 2] * m[4 * b + 2]
        assertEquals(0f, dot(0, 1), 1e-4f); assertEquals(0f, dot(0, 2), 1e-4f); assertEquals(0f, dot(1, 2), 1e-4f)
    }

    @Test fun theOrbitShaderDrawsItsFarAndNearHalvesAndFades() {
        val vs = SpaceShaders.orbitVertex
        val fs = SpaceShaders.orbit
        assertTrue(vs.contains("uModel") && vs.contains("uVP"))
        assertTrue("each half on its own side of the body", fs.contains("uSide") && fs.contains("uCentreDepth") && fs.contains("discard"))
        assertTrue("with the depth fade, added", fs.contains("rpFadedGlow") && fs.contains("rpDepthFade"))
        assertTrue("brighter by the pop's bloom", fs.contains("uGlow"))
    }

    // ---- a chosen black hole ----

    @Test fun aChosenBlackHolesRingAndDiskAreHot() {
        assertEquals(1.7f, MapMark.HOT_RING, 0f)
        assertEquals(1.45f, MapMark.HOT_DISK, 0f)
        val bh = SpaceShaders.bhRing
        assertTrue("hot only when the mark says chosen", bh.contains("float rpHot(float rp_gain) { return vMark.y > 0.5 ? rp_gain : 1.0; }"))
        assertEquals("the ring and its echo by 1.7", 1, Regex("""rpHot\(1\.7\)""").findAll(bh).count())
        assertEquals("the disk's three terms by 1.45", 3, Regex("""rpHot\(1\.45\)""").findAll(bh).count())
        assertTrue(bh.contains("(rp_ring * (1.1 + 0.6 * rp_m) + rp_echo * 0.5) * rpHot(1.7)"))
        assertTrue("its main reads it", SpaceShaders.fragment(SpaceStyle.BLACK_HOLE).contains("rpHot(1.7)"))
    }

    @Test fun everyBodyCarriesItsMarkAndFadesByIt() {
        assertEquals(20, SpaceBodies.STRIDE)
        assertTrue(SpaceShaders.bodyVertex.contains("layout(location = 5) in vec4 aMark;"))
        assertTrue(SpaceShaders.bodyVertex.contains("vMark = aMark;"))
        for (style in SpaceStyle.entries) {
            val fs = SpaceShaders.fragment(style)
            assertTrue("$style reads the mark", fs.contains("flat in vec4 vMark;"))
            assertTrue("$style fades by it under Show links", fs.contains("fragColor = fragColor * vMark.x;"))
        }
    }

    // ---- the Neurons' hot halo ----

    @Test fun theHotHaloIsIOSs() {
        assertEquals(1.8f, MapMark.HOT_GAIN, 0f)
        assertEquals(1.0f, MapMark.HALO_GAIN, 0f)
        assertEquals(0.9f, MapMark.HOT_SOFT_GAIN, 0f)
        assertArrayEquals(floatArrayOf(0.75f, 1.0f, 1.0f), MapMark.HOT_TINT_A, 0f)
        assertArrayEquals(floatArrayOf(0.75f, 0.95f, 1.0f), MapMark.HOT_TINT_C, 0f)
    }

    // ---- Performance's chosen point ----

    @Test fun thePerformanceChosenPointIsBiggerAndRinged() {
        assertEquals("1.4 times", 28f, MapMark.perfSize(20f, 2.75f), eps)
        assertEquals("never under 7 dp", 7f * 2.75f, MapMark.perfSize(4f, 2.75f), eps)
        assertEquals(0f, MapMark.perfRing(0.5f), 0f)
        assertEquals(0.5f, MapMark.perfRing(0.70f), 1e-4f)
        assertEquals(1f, MapMark.perfRing(0.8f), eps)
        assertEquals(0.5f, MapMark.perfRing(0.90f), 1e-4f)
        assertEquals(0f, MapMark.perfRing(0.95f), 0f)
        assertEquals(0.15f, MapMark.FADED, 0f)
        assertEquals(3f, MapMark.PERF_FOCUS, 0f)
    }

    // ---- the pop ----

    @Test fun thePopKicksOvershootsAndSettles() {
        assertEquals(3.2f, MapPop.KICK, 0f); assertEquals(15f, MapPop.OMEGA, 0f); assertEquals(0.42f, MapPop.ZETA, 0f)
        assertEquals(240f, MapPop.RATE, 0f); assertEquals(0.45f, MapPop.BLOOM, 0f); assertEquals(0.35f, MapPop.BLOOM_FADE, 0f)
        val pop = MapPop(lively = true)
        assertFalse(pop.moving)
        assertEquals(1f, pop.glow, 0f); assertEquals(1f, pop.swell, 0f)
        pop.kick()
        assertEquals(3.2f, pop.velocity, 0f)
        assertEquals(0.45f, pop.bloom, 0f)
        assertEquals("iOS's glow 0.85 + 0.35 × bloom, over its resting 0.85", (0.85f + 0.35f * 0.45f) / 0.85f, pop.glow, eps)
        assertEquals("and its swell 1 + 0.6 × bloom", 1.27f, pop.swell, eps)
        var peak = 1f; var low = 1f; var t = 0f
        while (t < 1.2f) { pop.step(1f / 60); t += 1f / 60; peak = maxOf(peak, pop.scale); low = minOf(low, pop.scale) }
        assertTrue("it swells about an eighth: $peak", peak in 1.10f..1.14f)
        assertTrue("and dips back under, damped at 0.42: $low", low in 0.96f..0.99f)
        assertEquals("settled in a second or so", 1f, pop.scale, 1e-3f)
        assertTrue("the bloom fades by e^(−t/0.35)", abs(pop.bloom - 0.45f * exp(-t / 0.35f)) < 1e-3f)
        repeat(240) { pop.step(1f / 60) }
        assertFalse("still at last", pop.moving)
    }

    @Test fun thePopStepsAlikeAtAnyFrameRate() {
        val a = MapPop(true); val b = MapPop(true)
        a.kick(); b.kick()
        repeat(6) { a.step(1f / 60) }
        repeat(12) { b.step(1f / 120) }
        assertEquals(a.scale, b.scale, 2e-3f)
    }

    @Test fun withAnimationsOffThereIsNoPop() {
        val pop = MapPop(lively = false)
        pop.kick()
        assertEquals(0f, pop.velocity, 0f)
        assertEquals(0f, pop.bloom, 0f)
        assertFalse(pop.moving)
        pop.step(0.1f)
        assertEquals(1f, pop.scale, 0f)
    }
}
