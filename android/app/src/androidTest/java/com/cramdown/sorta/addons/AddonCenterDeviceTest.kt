package com.cramdown.sorta.addons

import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S07b A3, on the whole path a call takes in the app: AddonCenter.run on the real main thread, the real
 * engine, and a registry on the phone's own disk. The package is near the 512 KB limit and the activity
 * log is full, so the reading, the SHA-256, the check and the state file are as heavy as they get; the
 * main thread's heartbeat keeps its beat, run returns within a frame, and none of the file work is on
 * the main thread. iOS: AddonCenterTests.swift.
 */
@RunWith(AndroidJUnit4::class)
class AddonCenterDeviceTest {
    private fun stored(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in files.toSortedMap()) {
                val e = ZipEntry(name)
                e.method = ZipEntry.STORED
                e.size = data.size.toLong()
                e.compressedSize = data.size.toLong()
                e.crc = CRC32().also { it.update(data) }.value
                zip.putNextEntry(e)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun theMainThreadKeepsItsBeatWhileACallReadsItsPackageAndWritesItsState() {
        assertTrue("the engine is in this build", AddonNative.loaded)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "addons-" + System.nanoTime()).also { it.mkdirs() }
        // the kit's template, with filler beside it
        val files = HashMap<String, ByteArray>()
        for (name in listOf("manifest.json", "main.js", "settings.json", "LICENSE")) files[name] = AddonAssets.bytes("template/$name")
        for (k in 0 until 7) files["lib/f$k.js"] = ("// " + "filler words ".repeat(5_000) + "\n").toByteArray()
        val bytes = stored(files)
        assertTrue("near the 512 KB limit: ${bytes.size}", bytes.size in 400_000..AddonPackageCheck.MAX_ZIPPED)
        val registry = AddonRegistry(folder)
        val id = registry.install(AddonReview.make(bytes, "1.0.0"), bytes, listOf("ideas.read", "commands", "cards")).id
        repeat(12) { registry.log(id, (0 until 20).map { AddonActivity("api.example.com", "/" + "p".repeat(300), 200, 1, 1) }) }
        registry.flush()
        val fileWork = Collections.synchronizedList(ArrayList<Pair<String, Thread>>())
        registry.onFileWork = { what -> fileWork.add(what to Thread.currentThread()) }
        val book = IdeaBook((1..3).map { n -> Note(id = "idea-$n", title = "Idea $n", body = "one two three", createdAt = n.toLong(), updatedAt = n.toLong()) }, emptyList())
        val pool = AddonPool()
        val center = AddonCenter({ book }, registry, pool)
        val main = Looper.getMainLooper().thread

        val baseline = AddonHeartbeat.baseline()
        val heart = AddonHeartbeat()
        heart.start()
        val onMain = ArrayList<Double>()
        try {
            // each round starts a fresh runtime, which reads and checks the package again
            repeat(ROUNDS) { round ->
                val finished = CountDownLatch(1)
                var done: AddonFinished? = null
                var doneOn: Thread? = null
                // the state file, with its full activity log, is being written as the main thread asks whether the add-on may run
                registry.log(id, (0 until 20).map { AddonActivity("api.example.com", "/" + "q".repeat(300), 200, 1, 1) })
                instrumentation.runOnMainSync {
                    val t = System.nanoTime()
                    center.run(id, "command", command = "count", input = """{"ideas":[]}""") { done = it; doneOn = Thread.currentThread(); finished.countDown() }
                    onMain.add((System.nanoTime() - t) / 1e6)
                }
                assertTrue("round $round came back", finished.await(30, TimeUnit.SECONDS))
                assertEquals("""{"ok":true,"value":{"message":"9 words in 3 ideas"}}""", done!!.outcome.envelope)
                assertTrue("the one hop back is to the main thread", doneOn === main)
                pool.close(id)
            }
            registry.flush()
        } finally {
            heart.stop()
            pool.closeAll()
            folder.deleteRecursively()
        }
        val median = onMain.sorted()[ROUNDS / 2]
        Log.i("SortaAddons", "center: run took %s ms of the main thread (median %.3f), beat %.1f ms (baseline %.1f ms), package %d bytes"
            .format(onMain.joinToString(" ") { "%.3f".format(it) }, median, heart.largestGapMs, baseline, bytes.size))
        assertEquals("the package was read and checked for each fresh runtime", ROUNDS, fileWork.count { it.first == "pack" })
        assertTrue("the state file was written, and the marker", fileWork.any { it.first == "state" } && fileWork.any { it.first == "marker" })
        assertTrue("no file work on the main thread: " + fileWork.filter { it.second === main }.map { it.first }, fileWork.none { it.second === main })
        assertTrue("run took $median ms of the main thread (median of $ROUNDS)", median <= 8.3)
        assertTrue("main-thread gap ${heart.largestGapMs} ms, baseline $baseline ms", heart.largestGapMs <= baseline + 50)
    }

    private companion object {
        const val ROUNDS = 5
    }
}
