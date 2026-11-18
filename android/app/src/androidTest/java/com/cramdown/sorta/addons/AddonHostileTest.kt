package com.cramdown.sorta.addons

import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S07b A3: the hostile fixtures, each stopped within its limit plus 50 ms with its code and nothing
 * applied, while the main thread's heartbeat keeps within its baseline plus 50 ms; and the engine's
 * threads. iOS: AddonHostileTests.swift.
 */
@RunWith(AndroidJUnit4::class)
class AddonHostileTest {
    private val suite by lazy { assertTrue("the engine is in this build", AddonNative.loaded); AddonCases("hostile") }

    private fun run(names: List<String>) {
        val baseline = AddonHeartbeat.baseline()
        for (name in names) {
            val c = suite.cases.firstOrNull { it["name"]?.string == name }
            assertNotNull(name, c)
            val heart = AddonHeartbeat()
            heart.start()
            val ran = suite.block(c!!)
            heart.stop()
            val r = ran.run
            assertNotNull("$name finished", r)
            val limit = (c["limitMs"]?.integer ?: 0).toDouble()
            val kind = c["kind"]?.string ?: "command"
            val code = if (kind == "load") (r!!.loadCode ?: "ok") else (r!!.outcome.code ?: "ok")
            val ms = if (kind == "load") r.loadMs else r.callMs
            assertEquals(name, c["expect"]?.string, code)
            assertTrue("$name stopped within its limit plus 50 ms: $ms ms", ms <= limit + 50)
            assertTrue("$name applied nothing", ran.text.contains("applied none") || kind == "load")
            assertTrue("$name: main-thread gap ${heart.largestGapMs} ms, baseline $baseline ms", heart.largestGapMs <= baseline + 50)
            Log.i("SortaAddons", "hostile %s %s %.1f ms, beat %.1f ms (baseline %.1f ms)".format(name, code, ms, heart.largestGapMs, baseline))
        }
    }

    @Test fun everyFixtureIsInAGroup() {
        assertEquals(suite.cases.mapNotNull { it["name"]?.string }.toSet(), groups.flatten().toSet())
    }

    @Test fun loopMemoryRecursionNestingHuge() = run(groups[0])
    @Test fun floodEvalWritesCaptures() = run(groups[1])
    @Test fun revokeRegexCardTopLevel() = run(groups[2])

    // threads

    @Test fun eachRuntimeHasItsOwnFourMegabyteThread() {
        assertTrue(AddonNative.loaded)
        val runner = AddonRunner("stack", AddonFiles(listOf("main.js"), listOf(ByteArray(0)), "main.js"))
        try {
            val (size, main) = runner.onThread { AddonNative.currentStackSize() to AddonNative.isMainThread() }
            // the size given to Thread, and the host's report from inside it (ART adds its own guard to what it was asked for)
            assertEquals(4L * 1024 * 1024, AddonThread.STACK_SIZE)
            assertTrue("the host reports $size bytes", size >= AddonThread.STACK_SIZE && size < AddonThread.STACK_SIZE + (1L shl 20))
            assertFalse(main)
            Log.i("SortaAddons", "add-on thread stack $size bytes")
        } finally {
            runner.close()
        }
    }

    @Test fun theMainThreadNeverRunsTheEngine() {
        assertTrue(AddonNative.loaded)
        var onMain = false
        var code: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            onMain = Looper.myLooper() == Looper.getMainLooper() && AddonNative.isMainThread()
            code = AddonNative.tryRuntimeHere()
        }
        assertTrue(onMain)
        assertEquals("thread", code)
    }

    @Test fun aRuntimeIsUsedOnlyFromItsOwnThread() {
        assertTrue(AddonNative.loaded)
        val answer = AddonNative.callFromAnotherThread()
        assertTrue(answer, answer.contains("\"code\":\"thread\""))
    }

    private companion object {
        val groups = listOf(listOf("loop", "memory", "recursion", "json", "huge"), listOf("flood", "eval", "writes", "captures"),
            listOf("revoke", "regex", "card", "toplevel"))
    }
}
