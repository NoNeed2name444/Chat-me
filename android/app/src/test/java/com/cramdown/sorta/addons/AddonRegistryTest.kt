package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S07b A3, A4, A6: what is installed and what it may do, kept per add-on in a folder; strikes, safe start, the pool. */
class AddonRegistryTest {
    private val folder = AddonTestFiles.temporaryFolder()
    private var clock = 1_000_000L
    private val id = "io.github.example.tests"
    private val bytes = AddonTestFiles.addon(AddonTestFiles.manifest(permissions = listOf("ideas.read", "ideas.write", "commands", "net"), hosts = listOf("api.example.com")))
    private val opened = ArrayList<AddonRegistry>()
    /** A registry on the folder, once the ones before it have written what they hold (their saves run on a thread of their own). */
    private fun registry(): AddonRegistry {
        opened.forEach { it.flush() }
        return AddonRegistry(folder, clock = { clock }).also { opened.add(it) }
    }
    private fun installed(r: AddonRegistry = registry(), grants: List<String> = listOf("ideas.read", "commands")) =
        r.also { it.install(AddonReview.make(bytes, "1.0.0"), bytes, grants) }

    @Test fun grantsAreKeptPerAddonAndCheckedAgainstTheManifest() {
        val r = installed()
        r.grant(id, "net:api.example.com")
        r.grant(id, "ideas.write")
        for (bad in listOf("net:other.example.org", "net", "cards", AddonPermissions.FOLDER_GRANT + "x"))
            assertEquals(bad, "permissions", try { r.grant(id, bad); null } catch (e: AddonRefusal) { e.code })
        r.revoke(id, "ideas.write")
        r.setAskEachTime(id, true)
        r.setSettings(id, "{\"titles\":true}")
        // a second registry on the same folder reads what the first kept
        val again = registry().addon(id)!!
        assertEquals(listOf("ideas.read", "commands", "net:api.example.com"), again.grants)
        assertTrue(again.askEachTime)
        assertEquals("{\"titles\":true}", again.settings)
        assertEquals(AddonSha256.hex(bytes), again.sha256)
        assertNotNull(registry().pack(id))
        assertEquals("permissions", try { r.install(AddonReview.make(bytes, "1.0.0"), bytes, listOf("cards")); null } catch (e: AddonRefusal) { e.code })
    }

    @Test fun aRevokeOrTurningOffStopsTheAddon() {
        val r = installed()
        val stopped = ArrayList<String>()
        r.onStop = { stopped.add(it) }
        r.revoke(id, "ideas.read")
        r.setOn(id, false)
        assertFalse(r.canRun(id))
        r.setOn(id, true)
        assertTrue(r.canRun(id))
        r.addonsOn = false
        assertFalse("the kill switch", r.canRun(id))
        assertFalse(registry().addonsOn)
        assertEquals(listOf(id, id, id), stopped)
        r.addonsOn = true
        r.remove(id)
        assertNull(r.addon(id))
        assertFalse(File(folder, "$id.sortaaddon").exists())
    }

    @Test fun aPackageChangedOnDiskIsNotRun() {
        val r = installed()
        File(folder, "$id.sortaaddon").writeBytes(AddonTestFiles.addon(files = mapOf("extra.js" to "//")))
        assertNull(r.pack(id))
    }

    @Test fun threeStrikesInADayPauseTheAddon() {
        val r = installed()
        assertFalse(r.strike(id, "js.error"))
        assertFalse(r.strike(id, null))
        assertFalse(r.strike(id, "limit.time"))
        clock += AddonRegistry.STRIKE_WINDOW_MS
        assertFalse("the first is a day old", r.strike(id, "limit.memory"))
        assertFalse(r.strike(id, "limit.stack"))
        assertTrue(r.canRun(id))
        assertTrue(r.strike(id, "limit.json"))
        assertFalse(r.canRun(id))
        assertTrue(registry().addon(id)!!.paused)
        r.setOn(id, true)
        assertTrue("turning it on again clears the strikes", r.canRun(id) && r.addon(id)!!.strikes.isEmpty())
        assertEquals(setOf("limit.time", "limit.memory", "limit.stack", "limit.json", "limit.batch"), AddonLimits.strikes)
    }

    @Test fun aMarkerLeftBehindStartsAddonsOffAndPausesTheOneItNames() {
        val r = installed()
        assertNull(r.startUp())
        r.beginCall(id)
        r.endCall()
        assertNull("a call that ended leaves no marker", registry().startUp())
        r.beginCall(id) // and the app dies here
        val next = registry()
        assertEquals(id, next.startUp())
        assertFalse(next.addonsOn)
        assertTrue(next.addon(id)!!.paused)
        assertFalse(next.canRun(id))
        assertNull("the marker is cleared", registry().startUp())
    }

    @Test fun theActivityLogKeepsThirtyDaysOfHostsAndPaths() {
        val r = installed()
        r.log(id, listOf(AddonActivity("api.example.com", "/v1/items", 200, 15, 3)))
        clock += 29 * 86_400_000L
        r.log(id, listOf(AddonActivity("api.example.com", "/v1/old", 302, 0, 1)))
        assertEquals(2, registry().activity(id).size)
        clock += 2 * 86_400_000L
        assertEquals(listOf("/v1/old"), registry().activity(id).map { it.activity.path })
        r.flush()
        val kept = File(folder, "addons.json").readText()
        assertTrue(kept.contains("\"host\":\"api.example.com\"") && !kept.contains("?"))
    }

    @Test fun theActivityLogIsCappedAndSoIsEachPath() {
        val r = installed()
        // the add-on chooses its paths, and how many requests it makes
        repeat(30) { n -> r.log(id, (0 until 20).map { AddonActivity("api.example.com", "/" + "p".repeat(5_000) + (n * 20 + it), 200, 1, 1) }) }
        r.log(id, listOf(AddonActivity("api.example.com", "/last", 200, 1, 1)))
        val kept = registry().activity(id)
        assertEquals(AddonRegistry.ACTIVITY_MAX, kept.size)
        assertEquals("the newest are the ones kept", "/last", kept.last().activity.path)
        assertTrue(kept.all { it.activity.path.length <= AddonRegistry.ACTIVITY_PATH_MAX })
        assertTrue("the file stays small", File(folder, "addons.json").length() < 80_000)
    }

    @Test fun storageIsKeptPerAddon() {
        val r = installed()
        r.storage(id).set("k", "[1,2]")
        assertTrue("nothing is saved until the call ends", registry().storage(id).values.isEmpty())
        r.storage(id).end(true)
        // a call that is stopped, refused or fails keeps none of what it stored
        r.storage(id).set("k", "[3]")
        r.storage(id).set("other", "1")
        assertEquals("[3]", r.storage(id).values["k"])
        r.storage(id).end(false)
        assertEquals(mapOf("k" to "[1,2]"), r.storage(id).values)
        assertEquals("[1,2]", registry().storage(id).values["k"])
        assertTrue(registry().storage("io.github.example.other").values.isEmpty())
        r.remove(id)
        assertTrue(registry().storage(id).values.isEmpty())
    }

    @Test fun thePoolKeepsThreeRuntimesAndFreesTheIdle() {
        val pool = AddonPool(AddonStandInEngine.Companion.Maker(AddonScript()))
        val files = AddonFiles(listOf("main.js"), listOf(ByteArray(0)), "main.js")
        val t0 = System.currentTimeMillis()
        for (i in 0 until 4) assertNotNull(pool.runner("a$i", files, t0 + i))
        assertEquals("at most 3 runtimes", 3, pool.alive)
        pool.sweep(t0 + 10_000)
        assertEquals(3, pool.alive)
        pool.sweep(System.currentTimeMillis() + 31_000)
        assertEquals("idle 30 s: freed", 0, pool.alive)
    }

    @Test fun aRunnerMadeForOtherFilesIsReplaced() {
        val pool = AddonPool(AddonStandInEngine.Companion.Maker(AddonScript()))
        val files = AddonFiles(listOf("main.js"), listOf(ByteArray(0)), "main.js")
        val first = pool.runner("a", "one", { files })
        assertTrue("the same files: the same runner", first === pool.runner("a", "one", { files }))
        val second = pool.runner("a", "two", { files })
        assertTrue(second !== first && second!!.stamp == "two")
        assertEquals(1, pool.alive)
        pool.closeAll()
    }

    @Test fun anAddonRunsAtMostThreeSecondsAMinute() {
        val pool = AddonPool(AddonStandInEngine.Companion.Maker(AddonScript()))
        pool.record("a", 1_500.0, 0)
        assertTrue(pool.withinBudget("a", 1_000))
        pool.record("a", 1_500.0, 30_000)
        assertFalse(pool.withinBudget("a", 31_000))
        assertTrue("another add-on has its own minute", pool.withinBudget("b", 31_000))
        assertTrue("the first second and a half is a minute old", pool.withinBudget("a", 60_001))
    }
}
