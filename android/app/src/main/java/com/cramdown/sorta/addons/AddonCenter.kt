package com.cramdown.sorta.addons

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.Reshaped
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Add-ons (S07b): one call, from the main thread and back.
//
// The main thread hands the book's own lists (which never change in place) to the add-on's own thread,
// which maps the snapshot, runs the call and never touches the book. When the call returns there is
// exactly one hop back to the main thread, which applies the whole batch through IdeaBook as one change
// and one Undo step (IdeaBook.applyAddonBatch). The main thread never waits on an add-on. No UI here. It
// follows ios/Sorta/Addons/AddonCenter.swift.

/** [applied] is set when the batch was applied (null for a refusal or a failed call); [reshaped] is the Undo step, when the batch changed something. */
class AddonFinished(val outcome: AddonOutcome, val applied: AddonApplied?, val reshaped: Reshaped?, val ranMs: Double, val applyNanos: Long,
    /** Whether the main thread took the batch as the add-on's thread had worked it out (the book had not changed meanwhile). */
    val prepared: Boolean = false) {
    val code: String? get() = outcome.code
}

/** The build's switches (AndroidManifest.xml's meta-data, from Gradle properties). */
object AddonConfig {
    private fun meta(context: Context, key: String): String? = try {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.get(key)?.toString()
    } catch (e: PackageManager.NameNotFoundException) { null }

    /** SORTA_ADDON_NET: on unless the build says NO (-Psorta.addonNet=NO). */
    fun netOn(context: Context): Boolean = (meta(context, "sorta.addonNet") ?: "YES").uppercase() != "NO"

    /** The directory's index branch; empty means no directory yet. */
    fun indexUrl(context: Context): String = meta(context, "sorta.addonsIndexUrl") ?: ""
}

/**
 * [book] gives the app's IdeaBook, on the main thread. [post] runs a block on the main thread (the tests
 * give their own). [ask] serves "ask each time": it shows the request to the person and answers on the
 * main thread; the add-on's thread waits for it, and nothing else does.
 */
class AddonCenter(
    private val book: () -> IdeaBook,
    val registry: AddonRegistry,
    val pool: AddonPool = AddonPool(),
    private val network: AddonNetwork = AddonHttpsNetwork(),
    val netOn: Boolean = true,
    private val post: (() -> Unit) -> Unit = { block -> Handler(Looper.getMainLooper()).post(block) },
) {
    @Volatile var ask: (addon: String, host: String, answer: (Boolean) -> Unit) -> Unit = { _, _, answer -> answer(false) }

    init {
        // off, a grant taken away, an update, a removal, a revocation: the add-on's runtime goes, and the next call starts a fresh one
        registry.onStop = { id -> pool.close(id) }
    }

    /**
     * Runs one call of an installed add-on, from the main thread: a command ([input]), a card or a batch of
     * cards ([cards], idea ids: the add-on gets only those inside its read grant, and none means no call),
     * or a settings change ([input]). [done] runs on the main thread once the batch is applied.
     */
    fun run(id: String, kind: String, command: String = "", input: String = "", cards: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(), done: (AddonFinished) -> Unit) {
        fun refuse(code: String) = done(AddonFinished(AddonOutcome(AddonCall.failure(code), false, code, emptyList(), emptyList(), emptyList()),
            null, null, 0.0, 0))
        val installed = registry.addon(id)
        if (!registry.canRun(id) || installed == null) return refuse("addon.off")
        if (!pool.withinBudget(id)) return refuse("limit.minute")
        // the package is read and checked on the add-on's own thread, once for each runner (a call whose package is gone ends "addon.missing")
        val runner = pool.runner(id, installed.sha256, { registry.pack(id, installed.sha256)?.files }) ?: return refuse("engine.busy")

        // the lists are the book's own: it replaces them, and never changes one in place
        val b = book()
        val notes = b.notes
        val folders = b.folders
        val asking = if (installed.askEachTime) ask else null
        val isCard = kind == "card" || kind == "cards"
        runner.callWithFiles(kind, command,
            input = { call -> if (isCard) call.cardInput(cards, kind == "cards") else input },
            setup = { files: AddonFiles ->
                // the storage's first read of its file is here too, off the main thread
                AddonSetup(AddonStoreMap.snapshot(notes, folders), installed.grants, files.commands, netOn, installed.settings, registry.storage(id),
                    if (netOn) network else null, asking?.let { a -> { host: String -> wait(a, id, host) } }, now,
                    place = AddonStoreMap::place)
            },
            before = { registry.beginCall(id) },
            done = { run ->
                registry.endCall()
                pool.record(id, run.ranMs)
                registry.strike(id, run.outcome.code)
                registry.log(id, run.outcome.activity)
                // the batch is worked out here, on the add-on's thread, against the lists the call was given
                val prepared = if (run.outcome.ok && run.outcome.batch.isNotEmpty()) AddonApply.prepare(run.outcome.batch, notes, folders, now) else null
                post { finish(id, run, prepared, now, done) }
            })
    }

    /** The one hop back: a stopped, refused or failed call applies nothing, and nor does a call whose add-on was turned off while it ran. */
    private fun finish(id: String, run: AddonRun, prepared: AddonApply.Prepared?, now: Long, done: (AddonFinished) -> Unit) {
        if (!run.outcome.ok || !registry.canRun(id)) {
            val o = if (run.outcome.ok) AddonOutcome(AddonCall.failure("stopped"), false, "stopped", emptyList(), run.outcome.log, run.outcome.activity)
            else run.outcome
            return done(AddonFinished(o, null, null, run.ranMs, 0))
        }
        val b = book()
        val fits = prepared != null && prepared.fits(b.notes, b.folders, now)
        val (applied, reshaped, nanos) = b.applyAddonBatch(run.outcome.batch, now, prepared)
        logApply(id, nanos, run.outcome.batch.size, fits)
        done(AddonFinished(run.outcome, applied, reshaped, run.ranMs, nanos, fits))
    }

    /** Each apply's time on the main thread, to the log (tag SortaAddons) and kept for the last 50. */
    val applyTimes = java.util.ArrayDeque<Long>()

    private fun logApply(id: String, nanos: Long, writes: Int, prepared: Boolean) {
        applyTimes.addLast(nanos)
        if (applyTimes.size > 50) applyTimes.removeFirst()
        // android.util.Log is not there in the JVM tests
        try {
            android.util.Log.i("SortaAddons", "apply $id: %.3f ms on the main thread, %d writes, %s".format(nanos / 1e6, writes,
                if (prepared) "worked out on the add-on's thread" else "worked out on the main thread"))
        } catch (e: RuntimeException) {
        }
    }

    /** The add-on's thread holds the request until the person answers. The call's own 10 s wall limit: no answer by then is a no. */
    private fun wait(ask: (String, String, (Boolean) -> Unit) -> Unit, id: String, host: String): Boolean {
        val answered = CountDownLatch(1)
        var yes = false
        post { ask(id, host) { answer -> yes = answer; answered.countDown() } }
        return answered.await(AddonCall.NET_MS.toLong(), TimeUnit.MILLISECONDS) && yes
    }
}
