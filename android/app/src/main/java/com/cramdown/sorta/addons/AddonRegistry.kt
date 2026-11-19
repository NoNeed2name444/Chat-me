package com.cramdown.sorta.addons

import java.io.File

// Add-ons (S07b): what is installed, what it may do, and the kill switch.
//
// Kept in a folder of its own (the app's files, or a test's temporary folder): the installed packages,
// each add-on's grants, its on/off and paused state, its strikes, storage and activity, and the safe-start
// marker. Thread-safe: the main thread and add-on threads use it. It follows
// ios/Sorta/Addons/AddonRegistry.swift, and writes the same addons.json.

data class AddonInstalled(
    val id: String, val version: String, val sha256: String, val permissions: List<String>, val hosts: List<String>,
    val grants: List<String>, val on: Boolean = true, val paused: Boolean = false, val revoked: Boolean = false,
    val fromDirectory: Boolean = false,
    /** The manifest's author url: revoked.json can name an author (addons/spec/directory.md). */
    val authorUrl: String = "",
    /** net: hold each request until the person answers (section 4). */
    val askEachTime: Boolean = false,
    /** When each strike happened, in ms. */
    val strikes: List<Long> = emptyList(),
    val settings: String = "{}",
) {
    val json: AddonJson get() = AddonJson.obj(
        "askEachTime" to AddonJson.Bool(askEachTime), "authorUrl" to AddonJson.str(authorUrl), "fromDirectory" to AddonJson.Bool(fromDirectory),
        "grants" to AddonJson.strings(grants), "hosts" to AddonJson.strings(hosts), "id" to AddonJson.str(id),
        "on" to AddonJson.Bool(on), "paused" to AddonJson.Bool(paused), "permissions" to AddonJson.strings(permissions),
        "revoked" to AddonJson.Bool(revoked), "settings" to AddonJson.str(settings), "sha256" to AddonJson.str(sha256),
        "strikes" to AddonJson.Arr(strikes.map { AddonJson.num(it) }), "version" to AddonJson.str(version))

    companion object {
        fun read(j: AddonJson): AddonInstalled? {
            val id = j["id"]?.string ?: return null
            val version = j["version"]?.string ?: return null
            return AddonInstalled(id, version, j["sha256"]?.string ?: "", j["permissions"]?.strings ?: emptyList(),
                j["hosts"]?.strings ?: emptyList(), j["grants"]?.strings ?: emptyList(), j["on"]?.bool ?: true,
                j["paused"]?.bool ?: false, j["revoked"]?.bool ?: false, j["fromDirectory"]?.bool ?: false,
                j["authorUrl"]?.string ?: "", j["askEachTime"]?.bool ?: false, (j["strikes"]?.array ?: emptyList()).mapNotNull { it.number?.toLong() },
                j["settings"]?.string ?: "{}")
        }
    }
}

/** One logged request, kept 30 days: host and path, never the query or a header. */
data class AddonLogged(val at: Long, val activity: AddonActivity)

class AddonRegistry(val folder: File, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val STRIKE_WINDOW_MS = 24 * 3_600_000L
        const val STRIKES_TO_PAUSE = 3
        const val ACTIVITY_DAYS = 30L
        /** The newest requests kept for each add-on, and the longest path kept (the add-on chooses its paths). */
        const val ACTIVITY_MAX = 200
        const val ACTIVITY_PATH_MAX = 200
    }

    private val lock = Object()
    private var dirty = false
    private var writing = false
    private var writer: Thread? = null
    private val items = HashMap<String, AddonInstalled>()
    private var allOn = true
    private var lastCheck = 0L
    private val logs = HashMap<String, List<AddonLogged>>()
    private val stores = HashMap<String, AddonStorage>()
    /** Called when a grant is taken away, or an add-on is turned off, updated, removed or revoked: the pool closes its runtime. */
    @Volatile var onStop: (String) -> Unit = {}
    /** Called on the thread that is about to read or write one of the registry's files, with which: "pack", "storage", "state" or "marker" (the tests check none is the main thread). */
    @Volatile var onFileWork: (String) -> Unit = {}

    private val stateFile get() = File(folder, "addons.json")
    private val markerFile get() = File(folder, "running.marker")
    private fun packageFile(id: String) = File(folder, "$id.sortaaddon")
    private fun storageFile(id: String) = File(folder, "$id.storage.json")

    init {
        folder.mkdirs()
        load()
    }

    /** Writes a file whole: to a neighbour first, then renamed over it. */
    private fun write(file: File, bytes: ByteArray) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: java.io.IOException) {
            // the state stays in memory; the next write tries again
        }
    }

    // state

    val addons: List<AddonInstalled> get() = synchronized(lock) { items.values.sortedBy { it.id } }

    fun addon(id: String): AddonInstalled? = synchronized(lock) { items[id] }

    /** The kill switch for every add-on. */
    var addonsOn: Boolean
        get() = synchronized(lock) { allOn }
        set(value) {
            val ids = synchronized(lock) { allOn = value; saveLocked(); items.keys.toList() }
            if (!value) ids.forEach(onStop)
        }

    var lastDirectoryCheck: Long
        get() = synchronized(lock) { lastCheck }
        set(value) = synchronized(lock) { lastCheck = value; saveLocked() }

    val hasDirectoryAddon: Boolean get() = addons.any { it.fromDirectory }

    /** Whether a call may start now. */
    fun canRun(id: String): Boolean = synchronized(lock) {
        val a = items[id]
        allOn && a != null && a.on && !a.paused && !a.revoked
    }

    // install, update, remove

    /** Installs (or updates) from a review, with the grants the person chose. */
    fun install(review: AddonReview, bytes: ByteArray, grants: List<String>, fromDirectory: Boolean = false): AddonInstalled {
        if (!grants.all(review::allows)) throw AddonRefusal("permissions")
        val p = review.pack
        var a = AddonInstalled(p.id, p.version, review.sha256, p.permissions, p.hosts, grants, fromDirectory = fromDirectory,
            authorUrl = p.manifest["author"]?.get("url")?.string ?: "")
        write(packageFile(p.id), bytes)
        synchronized(lock) {
            items[p.id]?.let { old ->
                a = a.copy(on = old.on, askEachTime = old.askEachTime, settings = old.settings, fromDirectory = old.fromDirectory || fromDirectory)
            }
            items[p.id] = a
            saveLocked()
        }
        onStop(p.id)
        return a
    }

    fun remove(id: String) {
        onStop(id)
        synchronized(lock) {
            items.remove(id)
            logs.remove(id)
            stores.remove(id)
            saveLocked()
        }
        packageFile(id).delete()
        storageFile(id).delete()
    }

    /**
     * The installed package, checked again as it is read: a disk read, a SHA-256 and the whole package check,
     * so never on the main thread (AddonCenter reads it on the add-on's own thread). [sha256] is the version
     * the caller means; null means the installed one.
     */
    fun pack(id: String, sha256: String? = null): AddonPackage? {
        val a = addon(id) ?: return null
        if (sha256 != null && sha256 != a.sha256) return null
        onFileWork("pack")
        val d = try { packageFile(id).readBytes() } catch (e: java.io.IOException) { return null }
        if (AddonSha256.hex(d) != a.sha256) return null
        return try { AddonPackageCheck.archive(d) } catch (r: AddonRefusal) { null }
    }

    // grants and switches

    fun grant(id: String, g: String) = synchronized(lock) {
        val a = items[id] ?: return@synchronized
        val allowed = when {
            g.startsWith("net:") -> "net" in a.permissions && g.substring(4) in a.hosts
            g.startsWith(AddonPermissions.FOLDER_GRANT) -> "ideas.read.folder" in a.permissions
            else -> g != "net" && g != "ideas.read.folder" && g in a.permissions
        }
        if (!allowed) throw AddonRefusal("permissions")
        if (g !in a.grants) items[id] = a.copy(grants = a.grants + g)
        saveLocked()
    }

    private fun change(id: String, how: (AddonInstalled) -> AddonInstalled) = synchronized(lock) {
        items[id]?.let { items[id] = how(it) }
        saveLocked()
    }

    /** Takes a grant away; a running call is stopped. */
    fun revoke(id: String, g: String) {
        change(id) { it.copy(grants = it.grants.filterNot { x -> x == g }) }
        onStop(id)
    }

    fun setOn(id: String, on: Boolean) {
        change(id) { if (on) it.copy(on = true, paused = false, strikes = emptyList()) else it.copy(on = false) }
        if (!on) onStop(id)
    }

    fun setAskEachTime(id: String, ask: Boolean) = change(id) { it.copy(askEachTime = ask) }

    fun setSettings(id: String, canonical: String) = change(id) { it.copy(settings = canonical) }

    /** revoked.json: each covered add-on is turned off, by id (every version, or the listed ones) or by its author's url. */
    fun applyRevoked(list: List<AddonRevoked>) {
        val stopped = ArrayList<String>()
        synchronized(lock) {
            for (a in items.values.toList()) {
                if (a.revoked || list.none { it.covers(a) }) continue
                items[a.id] = a.copy(revoked = true, on = false)
                stopped.add(a.id)
            }
            if (stopped.isNotEmpty()) saveLocked()
        }
        stopped.sorted().forEach(onStop)
    }

    // strikes (section 6)

    /** Counts a call's code; 3 strikes within 24 h pause the add-on. True if it paused now. */
    fun strike(id: String, code: String?): Boolean {
        if (code == null || code !in AddonLimits.strikes) return false
        val now = clock()
        return synchronized(lock) {
            val a = items[id] ?: return@synchronized false
            val strikes = a.strikes.filter { now - it < STRIKE_WINDOW_MS } + now
            val pausesNow = strikes.size >= STRIKES_TO_PAUSE && !a.paused
            items[id] = a.copy(strikes = strikes, paused = a.paused || pausesNow)
            saveLocked()
            pausesNow
        }
    }

    // safe start (section 6)

    /** Written before each call into the engine. */
    fun beginCall(id: String) { onFileWork("marker"); write(markerFile, id.toByteArray(Charsets.UTF_8)) }

    /** Cleared after it. */
    fun endCall() { markerFile.delete() }

    /** At app start: a marker left behind means a call never ended. Add-ons start off and the add-on it names is paused. Returns that id. */
    fun startUp(): String? {
        val id = try { markerFile.readText(Charsets.UTF_8) } catch (e: java.io.IOException) { return null }
        synchronized(lock) {
            allOn = false
            items[id]?.let { items[id] = it.copy(paused = true) }
            saveLocked()
        }
        // the marker goes only once what it said is on disk
        flush()
        endCall()
        return id
    }

    // activity (section 9) and storage

    fun log(id: String, activity: List<AddonActivity>) {
        if (activity.isEmpty()) return
        val now = clock()
        synchronized(lock) {
            val keep = now - ACTIVITY_DAYS * 86_400_000
            val added = activity.takeLast(ACTIVITY_MAX).map {
                AddonLogged(now, if (it.path.length > ACTIVITY_PATH_MAX) it.copy(path = it.path.take(ACTIVITY_PATH_MAX)) else it)
            }
            logs[id] = ((logs[id] ?: emptyList()).filter { it.at >= keep } + added).takeLast(ACTIVITY_MAX)
            saveLocked()
        }
    }

    fun activity(id: String): List<AddonLogged> {
        val keep = clock() - ACTIVITY_DAYS * 86_400_000
        return synchronized(lock) { (logs[id] ?: emptyList()).filter { it.at >= keep } }
    }

    /** The add-on's storage. Its file is read the first time, with the lock free: call it off the main thread. */
    fun storage(id: String): AddonStorage {
        synchronized(lock) { stores[id] }?.let { return it }
        val values = HashMap<String, String>()
        val file = storageFile(id)
        onFileWork("storage")
        val read = try { AddonJson.parseOrNull(file.readText(Charsets.UTF_8)) } catch (e: java.io.IOException) { null }
        (read as? AddonJson.Obj)?.pairs?.forEach { (k, v) -> v.string?.let { values[k] = it } }
        val s = AddonStorage(values) { all ->
            write(file, AddonJson.Obj(all.map { it.key to AddonJson.str(it.value) }).canonical.toByteArray(Charsets.UTF_8))
        }
        return synchronized(lock) { stores.getOrPut(id) { s } }
    }

    // the file

    private fun load() {
        val j = try { AddonJson.parseOrNull(stateFile.readText(Charsets.UTF_8)) } catch (e: java.io.IOException) { null } ?: return
        allOn = j["addonsOn"]?.bool ?: true
        lastCheck = j["lastCheck"]?.number?.toLong() ?: 0
        for (a in j["addons"]?.array ?: emptyList()) AddonInstalled.read(a)?.let { items[it.id] = it }
        (j["activity"] as? AddonJson.Obj)?.pairs?.forEach { (id, list) ->
            logs[id] = (list.array ?: emptyList()).takeLast(ACTIVITY_MAX).map { e ->
                AddonLogged(e["at"]?.number?.toLong() ?: 0, AddonActivity(e["host"]?.string ?: "", (e["path"]?.string ?: "").take(ACTIVITY_PATH_MAX),
                    (e["status"]?.integer ?: 0).toInt(), (e["bytes"]?.integer ?: 0).toInt(), (e["ms"]?.integer ?: 0).toInt()))
            }
        }
    }

    /**
     * Marks the state changed (under the lock). A thread of the registry's own writes it: it copies the state
     * under the lock, and turns it to text and writes the file with the lock free, so no caller of the
     * registry (the main thread's canRun among them) ever waits on a file.
     */
    private fun saveLocked() {
        dirty = true
        if (writer == null) writer = Thread(::writeLoop, "Sorta add-ons state").also { it.isDaemon = true; it.start() }
        lock.notifyAll()
    }

    private fun writeLoop() {
        while (true) {
            val snapshot = synchronized(lock) {
                while (!dirty) lock.wait()
                dirty = false
                writing = true
                Snapshot(HashMap(items), HashMap(logs), allOn, lastCheck)
            }
            try { onFileWork("state"); write(stateFile, snapshot.text.toByteArray(Charsets.UTF_8)) } catch (t: Throwable) { /* the next change writes again */ }
            synchronized(lock) { writing = false; lock.notifyAll() }
        }
    }

    /** Waits until every change so far is in the file (the tests, and the start after a crash). */
    fun flush() = synchronized(lock) { while (dirty || writing) lock.wait() }

    private class Snapshot(val items: Map<String, AddonInstalled>, val logs: Map<String, List<AddonLogged>>, val allOn: Boolean, val lastCheck: Long) {
        val text: String get() {
            val activity = AddonJson.Obj(logs.keys.sorted().map { id ->
                id to AddonJson.Arr((logs[id] ?: emptyList()).map { l ->
                    AddonJson.obj("at" to AddonJson.num(l.at), "bytes" to AddonJson.num(l.activity.bytes), "host" to AddonJson.str(l.activity.host),
                        "ms" to AddonJson.num(l.activity.ms), "path" to AddonJson.str(l.activity.path), "status" to AddonJson.num(l.activity.status))
                })
            })
            return AddonJson.obj("activity" to activity, "addons" to AddonJson.Arr(items.keys.sorted().mapNotNull { items[it]?.json }),
                "addonsOn" to AddonJson.Bool(allOn), "lastCheck" to AddonJson.num(lastCheck)).canonical
        }
    }
}

/** The runtimes alive at once (section 6). Times are in ms. */
class AddonPool(private val maker: AddonEngineMaker = AddonNative.maker) {
    companion object {
        const val MAX_ALIVE = 3
        const val IDLE_MS = 30_000L
        const val MINUTE_BUDGET_MS = 3_000.0
    }

    private val lock = Any()
    private val runners = HashMap<String, AddonRunner>()
    private val used = HashMap<String, Long>()
    private val spent = HashMap<String, List<Pair<Long, Double>>>()

    val alive: Int get() = synchronized(lock) { runners.size }

    fun runner(id: String, files: AddonFiles, now: Long = System.currentTimeMillis()): AddonRunner? = runner(id, "", { files }, now)

    /**
     * The add-on's runner, made if need be. [stamp] names the installed files (their SHA-256): a runner made
     * for other files is closed and a new one made, so an update never runs the old code. [source] reads the
     * files, on the add-on's own thread. With 3 alive, the one used longest ago that is idle is freed first;
     * null if all 3 are busy.
     */
    fun runner(id: String, stamp: String, source: () -> AddonFiles?, now: Long = System.currentTimeMillis()): AddonRunner? = synchronized(lock) {
        runners[id]?.let {
            if (it.stamp == stamp) { used[id] = now; return@synchronized it }
            it.close()
            runners.remove(id)
            used.remove(id)
        }
        if (runners.size >= MAX_ALIVE) {
            val idle = runners.filter { it.value.idleSince != null }.minByOrNull { used[it.key] ?: Long.MIN_VALUE } ?: return@synchronized null
            idle.value.close()
            runners.remove(idle.key)
            used.remove(idle.key)
        }
        val r = AddonRunner(id, stamp, source, maker)
        runners[id] = r
        used[id] = now
        r
    }

    /** Frees each runtime idle for 30 s, with its thread. */
    fun sweep(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val old = runners.filter { e -> e.value.idleSince?.let { now - it >= IDLE_MS } ?: false }
        for ((id, r) in old) { r.close(); runners.remove(id); used.remove(id) }
    }

    /** Stops the add-on's calls and frees its runtime (a revoke, off, an update, the kill switch): its next call gets a new one. */
    fun close(id: String) { synchronized(lock) { used.remove(id); runners.remove(id) }?.close() }

    fun closeAll() {
        val all = synchronized(lock) { runners.values.toList().also { runners.clear(); used.clear() } }
        all.forEach { it.close() }
    }

    /** At most 3 s of running time per add-on per minute. */
    fun withinBudget(id: String, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val recent = (spent[id] ?: emptyList()).filter { now - it.first < 60_000 }
        spent[id] = recent
        recent.sumOf { it.second } < MINUTE_BUDGET_MS
    }

    fun record(id: String, ms: Double, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        spent[id] = (spent[id] ?: emptyList()) + (now to ms)
    }
}
