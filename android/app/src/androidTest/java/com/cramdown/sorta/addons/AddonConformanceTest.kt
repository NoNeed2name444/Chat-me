package com.cramdown.sorta.addons

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S07b A2: the conformance cases through the real engine on the emulator, byte for byte what the kit's
 * harness prints on Linux and iOS prints in its simulator (addons/conformance/expected.txt). One test
 * per group of 15 cases, so no test is long (A8).
 */
@RunWith(AndroidJUnit4::class)
class AddonConformanceTest {
    private val suite by lazy { assertTrue("the engine is in this build", AddonNative.loaded); AddonCases("") }

    /** expected.txt, cut into its blocks: each starts with "== name". */
    private val expected: List<String> by lazy {
        val text = AddonAssets.text("expected.txt")
        val starts = Regex("(?m)^== ").findAll(text).map { it.range.first }.toList()
        starts.mapIndexed { k, at -> text.substring(at, if (k + 1 < starts.size) starts[k + 1] else text.length) }
    }

    private fun group(n: Int) {
        val from = n * GROUP
        val to = minOf(suite.cases.size, from + GROUP)
        assertTrue("group $n holds cases", from < to)
        for (i in from until to) assertEquals(expected[i], suite.block(suite.cases[i]).text)
    }

    @Test fun everyCaseIsInAGroupAndHasItsExpectedBlock() {
        assertTrue("at least 40 cases", suite.cases.size >= 40)
        assertEquals(suite.cases.size, expected.size)
        assertTrue("the groups cover every case", suite.cases.size <= GROUPS * GROUP && suite.cases.size > (GROUPS - 1) * GROUP)
        assertEquals("0.18.0", AddonNative.engineVersion())
    }

    @Test fun cases1To15() = group(0)
    @Test fun cases16To30() = group(1)
    @Test fun cases31To45() = group(2)
    @Test fun cases46To60() = group(3)
    @Test fun cases61To75() = group(4)
    @Test fun cases76On() = group(5)

    private companion object {
        const val GROUP = 15
        const val GROUPS = 6
    }
}
