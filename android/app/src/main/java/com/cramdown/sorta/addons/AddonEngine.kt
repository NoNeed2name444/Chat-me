package com.cramdown.sorta.addons

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

// Add-ons (S07b): the engine, on threads of its own.
//
// Each add-on's runtime lives on one Thread that Sorta makes with a 4 MB stack (S07, section 6). Work
// reaches it through that thread's own queue: no coroutine dispatcher, executor or handler ever runs
// engine code, so a runtime never hops threads. The C host (addons/engine/host) checks it is on its own
// thread, not the main thread, at every entry. It follows ios/Sorta/Addons/AddonEngine.swift.

/** The files of one add-on, by package path, and the command ids its manifest declares. */
class AddonFiles(val names: List<String>, val data: List<ByteArray>, val main: String, val commands: List<String> = emptyList()) {
    /** The same files, with one replaced or added. */
    fun replacing(name: String, bytes: ByteArray): AddonFiles {
        val i = names.indexOf(name)
        return if (i >= 0) AddonFiles(names, data.toMutableList().also { it[i] = bytes }, main, commands)
        else AddonFiles(names + name, data + bytes, main, commands)
    }

    companion object {
        fun of(names: List<String>, data: List<ByteArray>): AddonFiles {
            var main = "main.js"
            var commands = emptyList<String>()
            val i = names.indexOf("manifest.json")
            if (i >= 0) AddonJson.parseOrNull(String(data[i], Charsets.UTF_8))?.let { m ->
                m["main"]?.string?.let { main = it }
                commands = commandsOf(m)
            }
            return AddonFiles(names, data, main, commands)
        }

        fun commandsOf(manifest: AddonJson): List<String> =
            (manifest["contributes"]?.get("commands")?.array ?: emptyList()).mapNotNull { it["id"]?.string }
    }
}

/** One runtime. Everything but [stop] is called on the add-on's own thread only. */
interface AddonEngine {
    /** Loads the add-on; null when loaded, else the code. */
    fun load(files: AddonFiles): String?
    /** One call's envelope, or null when the engine had no memory for it. */
    fun call(kind: String, id: String, input: String, runMs: Int, wallMs: Int): String?
    /** Stops the running call, from any thread. */
    fun stop()
    /** Whether the runtime can't be used again (a stopped call leaves it so). */
    val spent: Boolean
    val lastRunMs: Double
    fun free()
}

/** Makes a runtime on the calling thread, which must be the add-on's own: the engine, or the code when there is none. */
fun interface AddonEngineMaker {
    fun make(host: (String, String) -> String): Pair<AddonEngine?, String>
}

/** A thread with a 4 MB stack and a queue of its own. */
class AddonThread(name: String) {
    companion object { const val STACK_SIZE = 4L shl 20 }

    private val jobs = LinkedBlockingQueue<() -> Unit>()
    private val end: () -> Unit = {}
    private val thread = Thread(null, ::loop, name, STACK_SIZE).also { it.isDaemon = true; it.start() }

    fun run(job: () -> Unit) { jobs.put(job) }

    /** Ends the thread once the jobs queued so far have run. */
    fun finish() { jobs.put(end) }

    val isCurrent: Boolean get() = Thread.currentThread() === thread

    private fun loop() {
        while (true) {
            val job = jobs.take()
            if (job === end) return
            // a job that throws must not end the thread: the calls queued behind it still get their answer
            try { job() } catch (t: Throwable) { /* the job's own caller hears of it (AddonRunner) */ }
        }
    }
}

/** Section 6's time limits, by kind of call: running time and wall time, in ms. */
object AddonLimits {
    fun of(kind: String): Pair<Int, Int> = when (kind) {
        "card" -> 20 to 20
        "cards" -> 20 to 250
        "settings" -> 100 to 100
        else -> 500 to 10_000
    }

    /** The codes that count as a strike: the call was stopped at a limit. */
    val strikes = setOf("limit.time", "limit.memory", "limit.stack", "limit.json", "limit.batch")
}

/** How one call went: its outcome, its running time in ms, and the code the add-on's load failed with, if it was loaded for this call and failed. */
class AddonRun(val outcome: AddonOutcome, val ranMs: Double, val loadCode: String?, val loadMs: Double = 0.0, val callMs: Double = 0.0)

/**
 * One add-on's runtime and its thread. [source] gives the add-on's files: it is called on the add-on's
 * own thread, once, the first time they are needed, so reading and checking an installed package never
 * runs on the main thread, and a runner keeps what it read for as long as it lives. [stamp] names the
 * version of the files it was made for (the package's SHA-256), so the pool can tell a runner made
 * before an update.
 */
class AddonRunner(val id: String, val stamp: String, private val source: () -> AddonFiles?, private val maker: AddonEngineMaker = AddonNative.maker) {
    constructor(id: String, files: AddonFiles, maker: AddonEngineMaker = AddonNative.maker) : this(id, "", { files }, maker)

    private val thread = AddonThread("Sorta add-on $id")
    private val lock = Any()
    private var engine: AddonEngine? = null   // made, used and freed on `thread` only
    private var files: AddonFiles? = null     // read on `thread` only
    private var read = false
    @Volatile private var current: AddonCall? = null
    private var busy = false
    private var pending = 0
    private var lastUsed = System.currentTimeMillis()
    private var closed = false
    /** A stop that came while a call was queued or running: that call ends "stopped", and its runtime is freed after it. */
    private var stopPending = false

    /** When it last finished a call, or null while one runs. */
    val idleSince: Long? get() = synchronized(lock) { if (busy) null else lastUsed }

    /**
     * One call, on the add-on's thread: the runtime is made and the add-on loaded first if need be (kind
     * "load" only loads). [before] and [done] run on that thread, around the engine work (the safe-start
     * marker). [setup] is worked out there too (the snapshot is mapped there). [input] is worked out from
     * the call's view; null means the call is not made and fails with "permission" (a card view with no
     * idea it may read).
     */
    fun call(kind: String, id: String = "", input: (AddonCall) -> String?, setup: () -> AddonSetup, before: () -> Unit = {},
        now: () -> Long = System::currentTimeMillis, done: (AddonRun) -> Unit) = callWithFiles(kind, id, input, { _ -> setup() }, before, now, done)

    /** As above, with [setup] given the add-on's files (the manifest's commands), which are read on the add-on's thread. */
    fun callWithFiles(kind: String, id: String = "", input: (AddonCall) -> String?, setup: (AddonFiles) -> AddonSetup, before: () -> Unit = {},
        now: () -> Long = System::currentTimeMillis, done: (AddonRun) -> Unit) {
        // a runner closed between the pool handing it out and this call: its thread takes no more work
        val gone = synchronized(lock) { if (closed) true else { busy = true; pending++; false } }
        if (gone) return done(AddonRun(AddonOutcome(AddonCall.failure("stopped"), false, "stopped", emptyList(), emptyList(), emptyList()), 0.0, null))
        thread.run {
            var made: AddonCall? = null
            val run = try {
                before()
                // stopped while it waited its turn: it never starts
                if (synchronized(lock) { stopPending }) {
                    synchronized(lock) { ended(now()) }
                    done(AddonRun(AddonOutcome(AddonCall.failure("stopped"), false, "stopped", emptyList(), emptyList(), emptyList()), 0.0, null))
                    return@run
                }
                if (!read) { files = source(); read = true }
                val f = files
                if (f == null) {
                    synchronized(lock) { ended(now()) }
                    done(AddonRun(AddonOutcome(AddonCall.failure("addon.missing"), false, "addon.missing", emptyList(), emptyList(), emptyList()), 0.0, null))
                    return@run
                }
                val call = AddonCall(setup(f))
                made = call
                current = call
                var envelope: String
                var ran = 0.0
                var callMs = 0.0
                val loadStart = System.nanoTime()
                val loadCode = ensureLoaded(f)
                val loadMs = (System.nanoTime() - loadStart) / 1e6
                val e = engine
                if (loadCode != null) {
                    envelope = AddonCall.failure(loadCode)
                } else if (kind == "load") {
                    envelope = ""
                } else if (e != null) {
                    val text = input(call)
                    if (text == null) {
                        envelope = AddonCall.failure("permission")
                    } else {
                        call.phase = kind
                        val (runMs, wallMs) = AddonLimits.of(kind)
                        val callStart = System.nanoTime()
                        envelope = e.call(kind, id, text, runMs, wallMs) ?: AddonCall.failure("limit.memory")
                        callMs = (System.nanoTime() - callStart) / 1e6
                        ran = e.lastRunMs
                        if (e.spent) release()
                    }
                } else {
                    envelope = AddonCall.failure("engine.spent")
                }
                current = null
                // a runtime that was told to stop stays stopped, so it goes with the call it stopped
                val stopped = synchronized(lock) { stopPending.also { ended(now()) } }
                if (stopped) release()
                var outcome = call.outcome(envelope)
                // a stopped call gives nothing to apply, even if the engine finished it before the stop reached it
                if (stopped && kind != "load" && outcome.ok) outcome = call.outcome(AddonCall.failure("stopped"))
                // what the call stored is kept only when it ended well
                call.setup.storage.end(outcome.ok)
                AddonRun(outcome, ran, loadCode, loadMs, callMs)
            } catch (t: Throwable) {
                // the host's own fault, never the add-on's: the caller still hears, and nothing is applied
                current = null
                synchronized(lock) { ended(now()) }
                made?.setup?.storage?.end(false)
                release()
                AddonRun(AddonOutcome(AddonCall.failure("engine.failed"), false, "engine.failed", emptyList(), emptyList(), emptyList()), 0.0, null)
            }
            done(run)
        }
    }

    /** A call ended (under the lock): the runner is idle once none is queued behind it, and a stop was for the calls there were. */
    private fun ended(at: Long) {
        pending--
        if (pending == 0) { busy = false; if (!closed) stopPending = false }
        lastUsed = at
    }

    /**
     * Stops the call that is queued or running, from any thread. With no call there is nothing to stop,
     * and nothing is done: a stop left on an idle runtime would refuse every later call.
     */
    fun stop() { synchronized(lock) { if (busy) { stopPending = true; engine?.stop() } } }

    /** Stops the calls queued or running (each ends "stopped"), frees the runtime and ends the thread. */
    fun close() {
        synchronized(lock) { if (closed) return; closed = true; if (busy) stopPending = true; engine?.stop() }
        thread.run { release() }
        thread.finish()
    }

    /** Runs [work] on the add-on's thread and waits for it (the tests). */
    fun <T> onThread(work: () -> T): T {
        val done = CountDownLatch(1)
        var result: T? = null
        var failed: Throwable? = null
        thread.run { try { result = work() } catch (t: Throwable) { failed = t } finally { done.countDown() } }
        done.await()
        failed?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun release() {
        val e = synchronized(lock) { engine.also { engine = null } }
        e?.free()
    }

    /** Makes the runtime and loads the add-on; the code if it can't. */
    private fun ensureLoaded(files: AddonFiles): String? {
        if (engine != null) return null
        val (made, code) = maker.make { name, args -> current?.handle(name, args) ?: "!api.unknown" }
        if (made == null) return code
        val stopNow = synchronized(lock) { engine = made; closed || stopPending }
        if (stopNow) made.stop()
        val failed = made.load(files)
        if (failed != null) release()
        return failed
    }
}

/** The engine itself: QuickJS-ng and the shared host in libsorta_addons.so (src/main/cpp). */
object AddonNative {
    /** What the C host calls for each API call: the answer as UTF-8. */
    class Host(private val handle: (String, String) -> String) {
        @Suppress("unused") // called from sorta_addons_jni.c
        fun ask(name: ByteArray, args: ByteArray): ByteArray {
            // a call's name is one of the API's own: lowercase ASCII letters and dots (the C host checks it first)
            if (name.isEmpty() || name.size > 40 || name.any { it != '.'.code.toByte() && (it < 'a'.code.toByte() || it > 'z'.code.toByte()) }) {
                return "!api.unknown".toByteArray(Charsets.US_ASCII)
            }
            return handle(String(name, Charsets.US_ASCII), String(args, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
        }
    }

    /** Whether the library is in this build (a build made with -Psorta.addonsEngine=standin has none). */
    val loaded: Boolean by lazy {
        try { System.loadLibrary("sorta_addons"); true } catch (e: UnsatisfiedLinkError) { false }
    }

    val maker = AddonEngineMaker { host ->
        if (!loaded) return@AddonEngineMaker null to "engine.missing"
        val code = ByteArray(64)
        val handle = make(Host(host), code)
        if (handle == 0L) null to ascii(code) else Runtime(handle) to ""
    }

    private fun ascii(code: ByteArray): String = String(code, 0, code.indexOf(0).let { if (it < 0) code.size else it }, Charsets.US_ASCII)

    private class Runtime(private var handle: Long) : AddonEngine {
        override fun load(files: AddonFiles): String? =
            load(handle, files.names.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(), files.data.toTypedArray(),
                files.main.toByteArray(Charsets.UTF_8))?.let { String(it, Charsets.US_ASCII) }

        override fun call(kind: String, id: String, input: String, runMs: Int, wallMs: Int): String? =
            call(handle, kind, id.toByteArray(Charsets.UTF_8), input.toByteArray(Charsets.UTF_8), runMs, wallMs)?.let { String(it, Charsets.UTF_8) }

        override fun stop() { if (handle != 0L) AddonNative.stop(handle) }
        override val spent: Boolean get() = spent(handle)
        override val lastRunMs: Double get() = lastRunMs(handle)
        override fun free() { val h = handle; handle = 0; if (h != 0L) AddonNative.free(h) }
    }

    /** A runtime made on the calling thread and freed at once: null when the host made one, else its code (the main thread gets "thread"). */
    fun tryRuntimeHere(): String? {
        val code = ByteArray(64)
        val handle = make(null, code)
        if (handle == 0L) return ascii(code)
        free(handle)
        return null
    }

    /** A runtime made on its own thread, then a call made from another thread: the host's own-thread check refuses it. */
    fun callFromAnotherThread(): String {
        var handle = 0L
        val made = CountDownLatch(1)
        val freeIt = CountDownLatch(1)
        val owner = Thread(null, {
            handle = make(null, ByteArray(64))
            made.countDown()
            freeIt.await()
            if (handle != 0L) free(handle)
        }, "Sorta add-on owner", AddonThread.STACK_SIZE)
        owner.start()
        made.await()
        var answer = "none"
        if (handle != 0L) {
            val caller = Thread(null, {
                call(handle, "command", "x".toByteArray(), "{}".toByteArray(), 500, 500)?.let { answer = String(it, Charsets.UTF_8) }
            }, "Sorta add-on caller", AddonThread.STACK_SIZE)
            caller.start()
            caller.join()
        }
        freeIt.countDown()
        owner.join()
        return answer
    }

    @JvmStatic private external fun make(host: Host?, code: ByteArray): Long
    @JvmStatic private external fun free(handle: Long)
    @JvmStatic private external fun load(handle: Long, names: Array<ByteArray>, texts: Array<ByteArray>, main: ByteArray): ByteArray?
    @JvmStatic private external fun call(handle: Long, kind: String, id: ByteArray, input: ByteArray, runMs: Int, wallMs: Int): ByteArray?
    @JvmStatic private external fun stop(handle: Long)
    @JvmStatic private external fun spent(handle: Long): Boolean
    @JvmStatic private external fun lastRunMs(handle: Long): Double
    @JvmStatic external fun engineVersion(): String
    @JvmStatic external fun currentStackSize(): Long
    @JvmStatic external fun isMainThread(): Boolean
    @JvmStatic external fun bytecodeOf(source: ByteArray): ByteArray?
}
