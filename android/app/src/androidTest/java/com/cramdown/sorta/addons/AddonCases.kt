package com.cramdown.sorta.addons

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The conformance cases and hostile fixtures (addons/conformance, addons/fixtures/hostile; S07b A2, A3)
// run through the real engine in libsorta_addons.so and Android's host, printed as the kit's harness
// prints them (addons/kit/harness/harness.c), so the text can be compared byte for byte with
// expected.txt. The store each case writes to is an IdeaBook. iOS: AddonConformanceRunner.swift.

/** The test APK's assets: addons/fixtures and addons/conformance, side by side at the root (app/build.gradle.kts). */
object AddonAssets {
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets

    fun bytes(path: String): ByteArray = assets.open(path).use { it.readBytes() }
    fun text(path: String): String = String(bytes(path), Charsets.UTF_8)
    fun json(path: String): AddonJson = AddonJson.parse(text(path))

    /** A package folder, two levels deep at most, as iOS AddonFiles.folder reads one. */
    fun folder(path: String): AddonFiles {
        val names = ArrayList<String>()
        val data = ArrayList<ByteArray>()
        fun walk(dir: String, prefix: String, depth: Int) {
            for (item in (assets.list(dir) ?: emptyArray()).sorted()) {
                if (item.startsWith(".")) continue
                val full = "$dir/$item"
                val inside = assets.list(full) ?: emptyArray()
                if (inside.isNotEmpty()) {
                    if (depth < 2 && item != "tests") walk(full, "$prefix$item/", depth + 1)
                } else {
                    names.add(prefix + item)
                    data.add(bytes(full))
                }
            }
        }
        walk(path, "", 1)
        return AddonFiles.of(names, data)
    }
}

/** The cases' store: an IdeaBook made from the snapshot, written to as the app writes. */
class AddonBookCases(snapshot: AddonSnapshot, private val now: Long) {
    var book = make(snapshot.ideas.map { i ->
        Note(id = i.id, title = i.title, body = i.text, folderId = i.folder, kind = i.kind, links = i.links, tags = i.tags,
            boardX = i.x, boardY = i.y, createdAt = i.createdAt, updatedAt = i.updatedAt)
    }, snapshot.folders.map { f -> NoteFolder(id = f.id, name = f.name, parentId = f.parent, words = f.words) })
        private set
    var saves = 0
        private set

    private fun make(notes: List<Note>, folders: List<NoteFolder>) = IdeaBook(notes, folders, now = { now }, onChange = { _, _ -> saves++ })

    fun snapshot(): AddonSnapshot = AddonStoreMap.snapshot(book.notes, book.folders)

    fun retitle(id: String, title: String) {
        book = make(book.notes.map { if (it.id == id) it.copy(title = title, updatedAt = now) else it }, book.folders)
    }

    fun remove(id: String) {
        book = make(book.notes.filter { it.id != id }.map { it.copy(links = it.links.filterNot { l -> l == id }) }, book.folders)
    }

    fun apply(ops: List<AddonOp>): AddonApplied = book.applyAddonBatch(ops, now).first

    /** An idea as an add-on with ideas.read would get it. */
    fun idea(id: String): AddonJson {
        val view = AddonView(snapshot(), listOf("ideas.read"))
        return view.ideas.firstOrNull { it.id == id }?.let(view::json) ?: AddonJson.Null
    }
}

/** [dir] is "" for the conformance cases (cases.json at the assets' root) or "hostile". */
class AddonCases(private val dir: String) {
    private val doc = AddonAssets.json(if (dir.isEmpty()) "cases.json" else "$dir/cases.json")
    /** The hostile fixtures' cases.json: each case is the folder of its name. */
    private val perFolder = doc["fixtures"] != null
    val cases: List<AddonJson> = doc["cases"]?.array ?: doc["fixtures"]?.array ?: emptyList()
    private val store = AddonAssets.json("store.json")
    private val routes = AddonAssets.json("net/routes.json")
    private val now = AddonText.parseIso(doc["now"]?.string ?: "2026-11-01T09:00:00.000Z") ?: 0
    private val addon = if (perFolder) null else AddonAssets.folder(doc["addon"]?.string ?: "addon")

    class Ran(val text: String, val run: AddonRun?)

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** One case's block, and how its call went (null if it never finished). */
    fun block(c: AddonJson): Ran {
        val name = c["name"]?.string ?: "?"
        val caseNow = c["now"]?.string?.let(AddonText::parseIso) ?: now
        val live = AddonBookCases(AddonSnapshot.read(store), caseNow)
        var counter = 0
        val lock = Any()
        val answers = c["ask"]?.strings?.toMutableList()
        val setup = AddonSetup(live.snapshot(), c["grants"]?.strings ?: emptyList(), c["commands"]?.strings ?: emptyList(),
            netOn = c["netOff"]?.bool != true, settings = (c["settings"] ?: AddonJson.Obj(emptyList())).canonical,
            network = AddonRoutesNetwork(routes),
            consent = answers?.let { list -> { _: String -> synchronized(lock) { list.isNotEmpty() && list.removeAt(0) == "yes" } } },
            now = caseNow,
            makeId = { synchronized(lock) { "00000000-0000-4000-8000-" + (++counter).toString().padStart(12, '0') } })
        for (ch in c["changeAfterSnapshot"]?.array ?: emptyList()) live.retitle(ch["id"]?.string ?: "", ch["title"]?.string ?: "")
        for (id in c["goneAfterSnapshot"]?.strings ?: emptyList()) live.remove(id)

        var files = addon ?: AddonAssets.folder("$dir/$name")
        (c["files"] as? AddonJson.Obj)?.pairs?.sortedBy { it.first }?.forEach { (file, spec) ->
            val bytes = spec["text"]?.string?.toByteArray(Charsets.UTF_8) ?: spec["hex"]?.string?.let(::hex)
                ?: spec["bytecodeOf"]?.string?.let { AddonNative.bytecodeOf(it.toByteArray(Charsets.UTF_8)) }
            if (bytes != null) files = files.replacing(file, bytes)
        }

        val kind = c["kind"]?.string ?: "command"
        val fixed = when (kind) {
            "settings" -> setup.settings
            "load" -> ""
            else -> (c["input"] ?: AddonJson.obj("ideas" to AddonJson.Arr(emptyList()))).canonical
        }
        val cardIds = c["card"]?.string?.let { listOf(it) } ?: c["cards"]?.strings ?: emptyList()
        val runner = AddonRunner("conformance", files)
        val finished = CountDownLatch(1)
        var result: AddonRun? = null
        runner.call(kind, if (kind == "command") (c["command"]?.string ?: "case") else "",
            // a card view's input comes from the add-on's own view, on its thread
            input = { call -> if (kind == "card" || kind == "cards") call.cardInput(cardIds, kind == "cards") else fixed },
            setup = { setup }) { run -> result = run; finished.countDown() }
        c["stopAfterMs"]?.integer?.let { ms ->
            // stop only sets the runtime's flag: no engine code runs on this thread
            Thread { Thread.sleep(ms); runner.stop() }.start()
        }
        // this is the instrumentation's thread: the main thread is never the one that waits
        finished.await(60, TimeUnit.SECONDS)
        runner.close()
        val run = result ?: return Ran("== $name\nno result\n", null)

        val lines = ArrayList<String>()
        lines.add("load " + (run.loadCode ?: "ok"))
        run.outcome.log.forEach { lines.add("log $it") }
        if (!(kind == "load" && run.loadCode == null)) {
            run.outcome.activity.forEach { lines.add(it.line) }
            lines.add("result " + run.outcome.envelope)
            if (run.outcome.ok) {
                val applied = live.apply(run.outcome.batch)
                lines.add("applied " + applied.json.canonical)
                for (id in (applied.added + applied.changed).sorted()) lines.add("after " + live.idea(id).canonical)
            } else {
                lines.add("applied none")
            }
        }
        return Ran("== $name\n" + lines.joinToString("\n") + "\n", run)
    }
}

/** A heartbeat on the main thread: a 4 ms tick that records its largest gap, in ms (S07b A3). */
class AddonHeartbeat {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var last = 0L
    @Volatile private var running = false
    @Volatile var largestGapMs = 0.0
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtimeNanos()
            val gap = (now - last) / 1e6
            if (gap > largestGapMs) largestGapMs = gap
            last = now
            handler.postDelayed(this, 4)
        }
    }

    fun start() {
        largestGapMs = 0.0
        last = SystemClock.elapsedRealtimeNanos()
        running = true
        handler.post(tick)
    }

    /** Stops after one last beat, so a gap still open is counted. */
    fun stop() {
        val done = CountDownLatch(1)
        handler.post {
            val gap = (SystemClock.elapsedRealtimeNanos() - last) / 1e6
            if (gap > largestGapMs) largestGapMs = gap
            running = false
            done.countDown()
        }
        done.await(10, TimeUnit.SECONDS)
        handler.removeCallbacks(tick)
    }

    companion object {
        /** The largest gap with no add-on running: the same heartbeat, in the same process, for [ms]. */
        fun baseline(ms: Long = 600): Double {
            val heart = AddonHeartbeat()
            heart.start()
            Thread.sleep(ms)
            heart.stop()
            return heart.largestGapMs
        }
    }
}
