package com.cramdown.sorta.addons

import com.cramdown.sorta.ideas.IdeaBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * S07b A3, A4, A5: a whole call on the stand-in engine, from the caller's thread and back. The test's own
 * thread stands for the main thread: what the center posts waits in a queue until the test takes it.
 * iOS: AddonCenterTests.swift.
 */
class AddonCenterTest {
    private val addon = "io.github.example.tests"
    private val now = 1_793_523_600_000
    private fun id(n: Int) = AddonTestFiles.id(n)
    private fun s(v: String) = AddonJson.str(v)

    private var saves = 0
    private val book = AddonTestFiles.store().let { (notes, folders) -> IdeaBook(notes, folders, now = { now }, onChange = { _, _ -> saves++ }) }
    private val posted = LinkedBlockingQueue<() -> Unit>()
    private val calls = AtomicInteger()
    private var pool: AddonPool? = null
    private var maker: AddonStandInEngine.Companion.Maker? = null
    /** Each thread that did the registry's file work, by what it did. */
    private val fileWork = java.util.Collections.synchronizedList(ArrayList<Pair<String, Thread>>())

    @After fun close() { pool?.closeAll() }

    private fun center(grants: List<String>, permissions: List<String> = listOf("ideas.read", "ideas.write", "commands"), hosts: List<String> = emptyList(),
        load: AddonScriptRun.() -> Unit = {}, call: AddonScriptRun.() -> String): AddonCenter {
        val registry = AddonRegistry(AddonTestFiles.temporaryFolder())
        registry.onFileWork = { what -> fileWork.add(what to Thread.currentThread()) }
        val bytes = AddonTestFiles.addon(AddonTestFiles.manifest(permissions = permissions, hosts = hosts))
        registry.install(AddonReview.make(bytes, "1.0.0"), bytes, grants)
        val m = AddonStandInEngine.Companion.Maker(AddonScript(load) { calls.incrementAndGet(); call() })
        maker = m
        val p = AddonPool(m)
        pool = p
        return AddonCenter({ book }, registry, p, AddonRoutesNetwork(AddonTestFiles.json("net/routes.json")), netOn = true, post = { posted.put(it) })
    }

    /** The "main thread" takes the one hop back, and gives what the call finished with. */
    private fun finish(c: AddonCenter, kind: String = "command", cards: List<String> = emptyList(), between: () -> Unit = {}): AddonFinished {
        var out: AddonFinished? = null
        c.run(addon, kind, command = if (kind == "command") "run" else "", input = "null", cards = cards, now = now) { out = it }
        between()
        if (out == null) {
            val hop = posted.poll(10, TimeUnit.SECONDS)
            assertNotNull("the call came back", hop)
            hop!!()
        }
        assertTrue("one hop, no more", posted.isEmpty())
        return out!!
    }

    @Test fun aCallsWritesAreAppliedInOneHopAsOneChangeAndOneUndoStep() {
        val before = book.notes
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            api("ideas.tag", s(id(101)), AddonJson.strings(listOf("seen")))
            api("ideas.link", s(id(105)), s(id(107)))
            val made = AddonJson.parse(api("ideas.capture", AddonJson.obj("text" to s("A title\nIts words"))))
            api("ideas.file", s(made["id"]!!.string!!), s(id(903)))
            "3"
        }
        val done = finish(c)
        assertEquals("""{"ok":true,"value":3}""", done.outcome.envelope)
        assertEquals("one save for the whole batch", 1, saves)
        assertTrue("the batch was worked out on the add-on's thread, and the caller's thread only took it", done.prepared)
        assertEquals(1, c.applyTimes.size)
        assertEquals(1, done.applied!!.added.size)
        assertTrue("seen" in book.note(id(101))!!.tags && book.isLinked(id(105), id(107)))
        assertEquals(id(903), book.note(done.applied!!.added[0])!!.folderId)
        assertNotNull(done.reshaped)
        book.undo(done.reshaped!!)
        assertEquals("one Undo puts the book back", before, book.notes)
    }

    @Test fun theCallerNeverWaitsAndTheAddonSeesTheBookAsItWasWhenItWasCalled() {
        val go = CountDownLatch(1)
        val seen = ArrayList<String>()
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            go.await(10, TimeUnit.SECONDS)
            seen.add(api("ideas.get", s(id(105))))
            api("ideas.tag", s(id(105)), AddonJson.strings(listOf("late")))
            "null"
        }
        val start = System.nanoTime()
        val done = finish(c) {
            val returned = (System.nanoTime() - start) / 1e6
            assertTrue("run returned in $returned ms while the add-on was still running", returned < 1_000)
            assertTrue("nothing came back yet", posted.isEmpty())
            // the person edits while the add-on runs
            book.applyAddonBatch(listOf(AddonOp.Tag(id(105), listOf("mine"))))
            go.countDown()
        }
        assertFalse("the book changed while the add-on ran, so the batch is applied to the book as it is now", done.prepared)
        assertFalse("the add-on read its snapshot", AddonJson.parse(seen[0])["tags"]!!.strings!!.contains("mine"))
        assertEquals("its write lands on the book as it is now", setOf("mine", "late"), book.note(id(105))!!.tags.toSet())
        assertTrue(done.outcome.ok)
    }

    @Test fun aRevokeStopsTheRunningCallAndNothingOfItIsApplied() {
        val running = CountDownLatch(1)
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            api("ideas.tag", s(id(101)), AddonJson.strings(listOf("never")))
            running.countDown()
            val end = System.nanoTime() + 10_000_000_000
            while (!stopped && System.nanoTime() < end) Thread.sleep(1)
            "null"
        }
        val before = book.notes
        val done = finish(c) {
            assertTrue(running.await(10, TimeUnit.SECONDS))
            c.registry.revoke(addon, "ideas.write")
        }
        assertEquals("stopped", done.code)
        assertNull(done.applied)
        assertEquals(before, book.notes)
        assertEquals(0, saves)
        // the add-on still runs, on a new runtime and without the grant
        val next = center(listOf("ideas.read", "commands")) { try { api("ideas.tag", s(id(101)), AddonJson.strings(listOf("x"))); "0" } catch (t: AddonScriptRun.Thrown) { "\"${t.code}\"" } }
        assertEquals("""{"ok":true,"value":"permission"}""", finish(next).outcome.envelope)
    }

    @Test fun aStopWithNoCallRunningDoesNotStick() {
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            try { api("ideas.tag", s(id(101)), AddonJson.strings(listOf("x"))); "\"tagged\"" } catch (t: AddonScriptRun.Thrown) { "\"${t.code}\"" }
        }
        assertEquals("""{"ok":true,"value":"tagged"}""", finish(c).outcome.envelope)
        assertEquals(1, c.pool.alive)
        // a grant taken away while the add-on is idle: its runtime goes, and the next call is answered, without the grant
        c.registry.revoke(addon, "ideas.write")
        assertEquals("the idle runtime is closed", 0, c.pool.alive)
        assertEquals("""{"ok":true,"value":"permission"}""", finish(c).outcome.envelope)
        // off and on again
        c.registry.setOn(addon, false)
        assertEquals(0, c.pool.alive)
        assertEquals("addon.off", finish(c).code)
        c.registry.setOn(addon, true)
        assertEquals("""{"ok":true,"value":"permission"}""", finish(c).outcome.envelope)
        // the kill switch, off and on
        c.registry.addonsOn = false
        c.registry.addonsOn = true
        assertEquals("""{"ok":true,"value":"permission"}""", finish(c).outcome.envelope)
        // revoked by the directory, by its author
        c.registry.applyRevoked(listOf(AddonRevoked(author = "https://github.com/example")))
        assertEquals(0, c.pool.alive)
        assertEquals("addon.off", finish(c).code)
        assertEquals("each stop gave the next call a runtime of its own", 4, maker!!.made)
        assertEquals(4, calls.get())
    }

    @Test fun anUpdateRunsTheNewFilesAtOnce() {
        val c = center(listOf("commands")) { "null" }
        assertTrue(finish(c).outcome.ok)
        val newer = AddonTestFiles.addon(AddonTestFiles.manifest(version = "1.1.0"), files = mapOf("lib/new.js" to "// new in 1.1.0\n"))
        c.registry.install(AddonReview.make(newer, "1.0.0"), newer, listOf("commands"))
        assertEquals("the old runtime is closed with the update", 0, c.pool.alive)
        assertTrue("no wait for the sweep, and no \"stopped\"", finish(c).outcome.ok)
        val loaded = maker!!.loaded
        assertEquals(2, loaded.size)
        assertFalse("lib/new.js" in loaded[0].names)
        assertTrue("lib/new.js" in loaded[1].names)
        assertTrue(String(loaded[1].data[loaded[1].names.indexOf("manifest.json")]).contains("\"version\":\"1.1.0\""))
    }

    @Test fun aCallQueuedBehindAStoppedOneIsStoppedTooAndTheNextOneRuns() {
        val running = CountDownLatch(1)
        val c = center(listOf("commands")) {
            running.countDown()
            val end = System.nanoTime() + 10_000_000_000
            while (!stopped && System.nanoTime() < end) Thread.sleep(1)
            "null"
        }
        val codes = java.util.Collections.synchronizedList(ArrayList<String?>())
        repeat(2) { c.run(addon, "command", command = "run", input = "null", now = now) { codes.add(it.code) } }
        assertTrue(running.await(10, TimeUnit.SECONDS))
        c.registry.revoke(addon, "ideas.read")
        repeat(2) { posted.poll(10, TimeUnit.SECONDS)!!() }
        assertEquals(listOf<String?>("stopped", "stopped"), codes)
        assertEquals("the second never reached the engine", 1, calls.get())
    }

    @Test fun whatAStoppedCallStoredIsNotKept() {
        val running = CountDownLatch(1)
        var wait = true
        val c = center(listOf("commands")) {
            api("storage.set", s("k"), s(if (wait) "\"stopped\"" else "\"kept\""))
            if (wait) {
                running.countDown()
                val end = System.nanoTime() + 10_000_000_000
                while (!stopped && System.nanoTime() < end) Thread.sleep(1)
            }
            "null"
        }
        assertEquals("stopped", finish(c) { assertTrue(running.await(10, TimeUnit.SECONDS)); c.registry.revoke(addon, "ideas.read") }.code)
        assertNull(c.registry.storage(addon).values["k"])
        wait = false
        assertTrue(finish(c).outcome.ok)
        assertEquals("\"kept\"", c.registry.storage(addon).values["k"])
    }

    @Test fun thePackageTheStorageAndTheStateFileAreNeverReadOrWrittenOnTheCallersThread() {
        // a package near the size limit, a full activity log, storage in use and a strike: all the registry's file work of a call
        val c = center(listOf("commands", "net:api.example.com"), listOf("commands", "net"), listOf("api.example.com")) {
            api("storage.set", s("k"), s("1"))
            api("net.fetch", s("https://api.example.com/v1/items"), AddonJson.obj())
            throw AddonScriptRun.Thrown("limit.time", "limit.time")
        }
        val big = AddonTestFiles.addon(AddonTestFiles.manifest(version = "1.1.0", permissions = listOf("commands", "net"), hosts = listOf("api.example.com")),
            files = (0 until 7).associate { "lib/f$it.js" to "// ${"filler words ".repeat(5_000)}\n" })
        assertTrue("near the 512 KB limit", big.size in 400_000..AddonPackageCheck.MAX_ZIPPED)
        c.registry.install(AddonReview.make(big, "1.0.0"), big, listOf("commands", "net:api.example.com"))
        repeat(12) { c.registry.log(addon, (0 until 20).map { AddonActivity("api.example.com", "/" + "p".repeat(300), 200, 1, 1) }) }
        c.registry.flush()
        fileWork.clear()
        val start = System.nanoTime()
        val done = finish(c) { assertTrue("run returned at once", (System.nanoTime() - start) / 1e6 < 1_000) }
        assertEquals("limit.time", done.code)
        c.registry.flush()
        val me = Thread.currentThread()
        assertEquals("all four kinds of file work happened", setOf("pack", "storage", "state", "marker"), fileWork.map { it.first }.toSet())
        assertTrue("none of it on the caller's thread: " + fileWork.filter { it.second === me }.map { it.first }, fileWork.none { it.second === me })
        assertTrue(fileWork.filter { it.first == "pack" || it.first == "storage" || it.first == "marker" }.all { it.second.name.startsWith("Sorta add-on ") })
        assertEquals("the package is read and checked once for a runner, not for each call", 1, fileWork.count { it.first == "pack" })
        assertEquals("limit.time", finish(c).code)
        assertEquals(1, fileWork.count { it.first == "pack" })
    }

    @Test fun turningTheAddonOffWhileItRunsAppliesNothing() {
        val running = CountDownLatch(1)
        val go = CountDownLatch(1)
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            api("ideas.tag", s(id(101)), AddonJson.strings(listOf("never")))
            running.countDown()
            go.await(10, TimeUnit.SECONDS)
            "null"
        }
        val done = finish(c) {
            assertTrue(running.await(10, TimeUnit.SECONDS))
            c.registry.addonsOn = false
            go.countDown()
        }
        assertEquals("stopped", done.code)
        assertNull(done.applied)
        assertFalse("never" in book.note(id(101))!!.tags)
        assertEquals("addon.off", finish(c).code)
    }

    @Test fun aCardViewGetsOnlyIdeasInsideTheReadGrantAndNoneMeansNoCall() {
        val inputs = ArrayList<String>()
        val perms = listOf("ideas.read.folder", "commands", "cards")
        val none = center(listOf("commands", "cards"), perms) { inputs.add(input); "null" }
        val refused = finish(none, kind = "card", cards = listOf(id(104)))
        assertEquals("permission", refused.code)
        assertEquals("the engine was not called", 0, calls.get())
        pool?.closeAll()

        val folder = center(listOf(AddonPermissions.FOLDER_GRANT + id(902), "commands", "cards"), perms) { inputs.add(input); "null" }
        assertTrue(finish(folder, kind = "cards", cards = listOf(id(101), id(104), id(105), id(107))).outcome.ok)
        assertEquals(listOf(id(104), id(107)), AddonJson.parse(inputs[0]).array!!.map { it["id"]!!.string })
        assertEquals("permission", finish(folder, kind = "card", cards = listOf(id(101))).code)
        assertEquals(1, calls.get())
    }

    @Test fun threeLimitFailuresInADayPauseTheAddon() {
        val c = center(listOf("commands")) { throw AddonScriptRun.Thrown("limit.time", "limit.time") }
        repeat(3) { assertEquals("limit.time", finish(c).code) }
        assertTrue(c.registry.addon(addon)!!.paused)
        assertEquals("addon.off", finish(c).code)
        assertEquals(3, calls.get())
    }

    @Test fun anAddonsOwnErrorIsNoStrikeAndAppliesNothing() {
        val c = center(listOf("ideas.read", "ideas.write", "commands")) {
            api("ideas.tag", s(id(101)), AddonJson.strings(listOf("never")))
            throw AddonScriptRun.Thrown("js.error", "it threw")
        }
        repeat(4) { assertEquals("js.error", finish(c).code) }
        assertTrue(c.registry.canRun(addon))
        assertEquals(0, saves)
    }

    @Test fun aRefusedHostIsNamedAndAllowedHostsAreLoggedByHostAndPath() {
        val c = center(listOf("commands", "net:api.example.com"), listOf("commands", "net"), listOf("api.example.com", "cdn.example.com")) {
            val codes = ArrayList<String>()
            try { api("net.fetch", s("https://cdn.example.com/a"), AddonJson.obj()) } catch (t: AddonScriptRun.Thrown) { codes.add(t.code + " " + t.text) }
            api("net.fetch", s("https://api.example.com/v1/items?secret=1"), AddonJson.obj())
            AddonJson.strings(codes).canonical
        }
        val done = finish(c)
        assertEquals("""{"ok":true,"value":["permission net:cdn.example.com"]}""", done.outcome.envelope)
        val logged = c.registry.activity(addon).map { it.activity }
        assertEquals(listOf("api.example.com" to "/v1/items"), logged.map { it.host to it.path })
    }

    @Test fun askEachTimeAsksOnTheMainThreadWhileOnlyTheAddonWaits() {
        val asked = ArrayList<String>()
        val c = center(listOf("commands", "net:api.example.com"), listOf("commands", "net"), listOf("api.example.com")) {
            try { api("net.fetch", s("https://api.example.com/v1/items"), AddonJson.obj()); "\"sent\"" } catch (t: AddonScriptRun.Thrown) { "\"${t.code}\"" }
        }
        c.registry.setAskEachTime(addon, true)
        c.ask = { a, host, answer -> asked.add("$a $host"); answer(asked.size > 1) }
        fun once(): AddonFinished {
            var out: AddonFinished? = null
            c.run(addon, "command", command = "run", input = "null", now = now) { out = it }
            repeat(2) { posted.poll(10, TimeUnit.SECONDS)!!() } // the question, then the hop back
            return out!!
        }
        assertEquals("""{"ok":true,"value":"net.declined"}""", once().outcome.envelope)
        assertEquals("""{"ok":true,"value":"sent"}""", once().outcome.envelope)
        assertEquals(listOf("$addon api.example.com", "$addon api.example.com"), asked)
    }

    @Test fun theMinuteBudgetAndAMissingPackageRefuseBeforeAnyCall() {
        val c = center(listOf("commands")) { "null" }
        c.pool.record(addon, 3_001.0)
        assertEquals("limit.minute", finish(c).code)
        val other = center(listOf("commands")) { "null" }
        java.io.File(other.registry.folder, "$addon.sortaaddon").delete()
        assertEquals("addon.missing", finish(other).code)
        assertEquals(0, calls.get())
    }
}
