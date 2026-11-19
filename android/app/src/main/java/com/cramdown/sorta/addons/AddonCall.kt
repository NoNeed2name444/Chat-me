package com.cramdown.sorta.addons

import java.util.UUID

// Add-ons (S07b): one call's side of the API.
//
// Everything a running add-on asks of Sorta comes here as a name and its arguments in JSON, on the
// add-on's own thread, never the main thread. Reads answer from the snapshot taken when the call was
// dispatched; writes are checked against it and kept as plain values until the call returns, when the
// main thread applies them in one hop (IdeaBook.applyAddonBatch). It follows
// ios/Sorta/Addons/AddonCall.swift, and the conformance cases check both give the same bytes.

/** An add-on's own small storage: key to the JSON text it stored. */
class AddonStorage(values: Map<String, String> = emptyMap(), private val saved: (Map<String, String>) -> Unit = {}) {
    private var kept = HashMap(values)
    private var map = HashMap(values)
    private var changed = false
    /** What the add-on sees: what is kept, with this call's own changes. */
    val values: Map<String, String> get() = map
    fun set(key: String, text: String) { map[key] = text; changed = true }
    fun remove(key: String) { map.remove(key); changed = true }

    /** The call ended: its changes are kept and saved if it ended well, and dropped if it was stopped, refused or failed. */
    fun end(ok: Boolean) {
        if (!changed) return
        changed = false
        if (ok) { kept = HashMap(map); saved(kept) } else map = HashMap(kept)
    }
}

/** How a call ended, for the app: its result, and the writes to apply when it succeeded. */
data class AddonOutcome(val envelope: String, val ok: Boolean, val code: String?, val batch: List<AddonOp>,
    val log: List<String>, val activity: List<AddonActivity>)

class AddonSetup(
    val snapshot: AddonSnapshot,
    val grants: List<String>,
    /** The command ids the manifest declares. */
    val commands: List<String>,
    /** The build's net switch, and the switch for net in the add-on's settings. */
    val netOn: Boolean,
    /** The add-on's settings values, as canonical JSON text. */
    val settings: String = "{}",
    val storage: AddonStorage = AddonStorage(),
    val network: AddonNetwork? = null,
    /** Ask each time: called on the add-on's thread, which waits for the person's answer. Null when net is granted outright. */
    val consent: ((String) -> Boolean)? = null,
    val now: Long,
    val makeId: () -> String = { UUID.randomUUID().toString() },
    /** Where the n-th idea lands on the board, among the others. */
    val place: (Int, List<Pair<Double, Double>>) -> Pair<Double, Double> = { _, _ -> 0.0 to 0.0 },
)

class AddonCall(val setup: AddonSetup) {
    companion object {
        const val MAX_WRITES = 100
        const val MAX_CAPTURES = 20
        const val STORAGE_LIMIT = 262_144
        const val NET_LIMIT = 1_048_576
        const val NET_MS = 10_000
        const val REDIRECTS = 5
        const val LOG_LINES = 200
        private val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

        fun failure(code: String, message: String = code): String =
            AddonJson.obj("code" to AddonJson.str(code), "message" to AddonJson.str(message), "ok" to AddonJson.Bool(false)).canonical
    }

    val view = AddonView(setup.snapshot, setup.grants)
    /** "load" while the add-on's top level runs, then the call's kind. */
    @Volatile var phase = "load"
    val batch = ArrayList<AddonOp>()
    val log = ArrayList<String>()
    val activity = ArrayList<AddonActivity>()
    var poisoned: String? = null
        private set
    private var writes = 0
    private var captures = 0
    private val captured = HashSet<String>()
    private val spots = setup.snapshot.ideas.map { it.x to it.y }.toMutableList()

    /** What the error says besides its code (net: the refused host). */
    private class Refusal(val code: String, val text: String? = null) : Exception()

    private fun refuse(code: String, text: String? = null): Nothing = throw Refusal(code, text)

    /** One API call: JSON text back, "" for undefined, or "!" and a code (then a space and a message, when there is one). */
    fun handle(name: String, argsText: String): String {
        try {
            val args = AddonJson.parse(argsText).array ?: emptyList()
            if (name == "has") {
                val ok = args.firstOrNull()?.string?.let { AddonPermissions.has(setup.grants, it, setup.netOn) } ?: false
                return if (ok) "true" else "false"
            }
            if (name !in AddonPermissions.calls) refuse("api.unknown")
            if (phase == "load" && name !in AddonPermissions.loadCalls) refuse("api.load")
            // net.fetch checks its grant itself, after the URL
            if (name != "net.fetch" && !AddonPermissions.allows(setup.grants, name, null, setup.netOn)) refuse("permission")
            // only a command's writes are applied; a card view or settings handler only reads
            if (name in AddonPermissions.writes && phase != "command") refuse("permission")
            return answer(name, args)
        } catch (r: Refusal) {
            return "!" + r.code + (r.text?.let { " $it" } ?: "")
        } catch (e: AddonJsonError) {
            return "!api.args"
        }
    }

    private fun arg(args: List<AddonJson>, i: Int): AddonJson = args.getOrNull(i) ?: AddonJson.Null

    private fun answer(name: String, args: List<AddonJson>): String {
        when (name) {
            "ideas.list" -> return list(arg(args, 0)).canonical
            "ideas.get" -> {
                val id = arg(args, 0).string ?: refuse("api.args")
                return view.ideas.firstOrNull { it.id == id }?.let { view.json(it).canonical } ?: "null"
            }
            "ideas.search" -> return search(arg(args, 0), arg(args, 1)).canonical
            "folders.list" -> return folders().canonical
            "ideas.capture" -> return capture(arg(args, 0)).canonical
            "ideas.tag", "ideas.untag" -> {
                val id = target(arg(args, 0))
                val tags = tagList(arg(args, 1))
                write(if (name == "ideas.tag") AddonOp.Tag(id, tags) else AddonOp.Untag(id, tags))
                return ""
            }
            "ideas.link", "ideas.unlink" -> {
                val a = target(arg(args, 0))
                val b = target(arg(args, 1))
                if (a == b) refuse("api.args")
                write(if (name == "ideas.link") AddonOp.Link(a, b) else AddonOp.Unlink(a, b))
                return ""
            }
            "ideas.file" -> {
                val id = target(arg(args, 0))
                val folder = arg(args, 1)
                if (!folder.isNull && folder.string == null) refuse("api.args")
                folder.string?.let { if (it !in view.folders) refuse("api.notFound") }
                write(AddonOp.File(id, folder.string))
                return ""
            }
            "commands.on" -> {
                val id = arg(args, 0).string
                if (id == null || id !in setup.commands) refuse("api.args")
                return ""
            }
            "cards.on", "settings.on" -> return ""
            "settings.get" -> return setup.settings
            "storage.get" -> return setup.storage.values[storageKey(arg(args, 0))] ?: ""
            "storage.set" -> {
                val key = storageKey(arg(args, 0))
                val text = arg(args, 1).string ?: refuse("api.args")
                var total = 0
                for ((k, v) in setup.storage.values) if (k != key) total += AddonText.utf8Count(k) + AddonText.utf8Count(v)
                if (total + AddonText.utf8Count(key) + AddonText.utf8Count(text) > STORAGE_LIMIT) refuse("limit.storage")
                setup.storage.set(key, text)
                return ""
            }
            "storage.remove" -> { setup.storage.remove(storageKey(arg(args, 0))); return "" }
            "console.log" -> {
                log.add(arg(args, 0).string ?: "")
                if (log.size > LOG_LINES) log.removeAt(0)
                return ""
            }
            "net.fetch" -> return fetch(arg(args, 0), arg(args, 1)).canonical
            else -> refuse("api.unknown")
        }
    }

    // reads

    private fun list(o: AddonJson): AddonJson {
        if (!o.isObject) refuse("api.args")
        val folder = o["folder"]
        if (folder != null && !folder.isNull && folder.string == null) refuse("api.args")
        val kind = o["kind"]
        if (kind != null && kind.string == null) refuse("api.args")
        val limit = whole(o["limit"], 50, 1..200)
        var from = 0
        val c = o["cursor"]
        if (c != null && !c.isNull) {
            val text = c.string
            if (text == null || text.length !in 1..9 || !text.all { it in '0'..'9' }) refuse("api.args")
            from = text.toInt()
        }
        val found = view.ideas.filter { i -> (folder == null || i.folder == folder.string) && (kind == null || i.kind == kind.string) }
            .sortedWith(compareBy<AddonIdea> { it.createdAt }.thenBy { it.id })
        if (from > found.size) refuse("api.args")
        val page = found.subList(from, minOf(found.size, from + limit))
        val next = if (from + limit < found.size) AddonJson.str((from + limit).toString()) else AddonJson.Null
        return AddonJson.obj("ideas" to AddonJson.Arr(page.map(view::json)), "cursor" to next)
    }

    private fun search(text: AddonJson, o: AddonJson): AddonJson {
        val t = text.string
        if (t == null || !o.isObject) refuse("api.args")
        val limit = whole(o["limit"], 20, 1..50)
        val want = AddonText.words(AddonLower.of(t))
        val found = view.ideas.filter { i ->
            val hay = AddonLower.of((listOf(i.title, i.text) + i.tags).joinToString(" "))
            want.all { hay.contains(it) }
        }.sortedWith(compareByDescending<AddonIdea> { it.updatedAt }.thenBy { it.id })
        return AddonJson.Arr(found.take(limit).map(view::json))
    }

    private fun folders(): AddonJson {
        val shown = setup.snapshot.folders.filter { it.id in view.folders }.sortedWith(compareBy<AddonFolder> { it.name }.thenBy { it.id })
        return AddonJson.Arr(shown.map { f ->
            AddonJson.obj("id" to AddonJson.str(f.id), "name" to AddonJson.str(f.name),
                "parent" to (if (f.parent != null && f.parent in view.folders) AddonJson.str(f.parent) else AddonJson.Null),
                "words" to AddonJson.strings(f.words))
        })
    }

    private fun whole(v: AddonJson?, default: Int, range: IntRange): Int {
        if (v == null) return default
        val n = v.integer
        if (n == null || n < range.first || n > range.last) refuse("api.args")
        return n.toInt()
    }

    // writes

    private fun target(v: AddonJson): String {
        val id = v.string ?: refuse("api.args")
        if (id !in view.ids && id !in captured) refuse("api.notFound")
        return id
    }

    private fun tagList(v: AddonJson): List<String> {
        val items = v.array
        if (items == null || items.isEmpty()) refuse("api.args")
        return items.map { item ->
            val t = item.string?.let { AddonText.trim(it) }
            if (t == null || t.isEmpty()) refuse("api.args")
            t
        }
    }

    private fun write(op: AddonOp) {
        poisoned?.let { refuse(it) }
        if (op is AddonOp.Capture) {
            if (captures >= MAX_CAPTURES) { poisoned = "limit.captures"; refuse("limit.captures") }
            captures++
        } else {
            if (writes >= MAX_WRITES) { poisoned = "limit.writes"; refuse("limit.writes") }
            writes++
        }
        batch.add(op)
    }

    private fun capture(o: AddonJson): AddonJson {
        val text = o["text"]?.string
        if (!o.isObject || text == null || AddonText.trim(text).isEmpty()) refuse("api.args")
        val t = o["title"]
        if (t != null && !t.isNull && t.string == null) refuse("api.args")
        val title: String
        val body: String
        val given = t?.string?.let { AddonText.trim(it) }
        if (given != null && given.isNotEmpty()) {
            title = given
            body = AddonText.trim(text)
        } else {
            val whole = AddonText.trim(text)
            val nl = whole.indexOf('\n')
            if (nl >= 0) {
                title = AddonText.trim(whole.substring(0, nl))
                body = AddonText.trim(whole.substring(nl + 1))
            } else {
                title = whole
                body = ""
            }
        }
        poisoned?.let { refuse(it) }
        if (captures >= MAX_CAPTURES) { poisoned = "limit.captures"; refuse("limit.captures") }
        val id = setup.makeId()
        val spot = setup.place(spots.size, spots)
        spots.add(spot)
        write(AddonOp.Capture(id, title, body, spot.first, spot.second))
        captured.add(id)
        return AddonJson.obj("id" to AddonJson.str(id))
    }

    private fun storageKey(v: AddonJson): String {
        val key = v.string
        if (key == null || key.length !in 1..100) refuse("api.args")
        return key
    }

    // net

    private fun fetch(urlValue: AddonJson, o: AddonJson): AddonJson {
        if (!setup.netOn) refuse("net.off")
        val text = urlValue.string
        if (text == null || !o.isObject) refuse("api.args")
        var url = AddonUrl.parse(text) ?: refuse("api.args")
        if (url.scheme != "https") refuse("net.https")
        if ("net:" + url.host !in setup.grants) refuse("permission", "net:" + url.host)
        var method = "GET"
        o["method"]?.let { m ->
            val s = m.string?.uppercase()
            if (s == null || s !in methods) refuse("api.args")
            method = s
        }
        val headers = LinkedHashMap<String, String>()
        o["headers"]?.let { h ->
            if (h !is AddonJson.Obj) refuse("api.args")
            for ((k, v) in h.pairs) {
                val name = AddonLower.of(k)
                val value = v.string
                if (value == null || name == "host") refuse("api.args")
                // a cookie is never sent
                if (name != "cookie") headers[name] = value
            }
        }
        var body = ""
        o["body"]?.let { b -> if (!b.isNull) body = b.string ?: refuse("api.args") }
        if (AddonText.utf8Count(body) > NET_LIMIT) refuse("net.size")
        // ask each time: this thread waits for the person's answer, for each host the request reaches (a
        // redirect to another host is asked about before anything is sent there)
        val asked = HashSet<String>()
        fun consent(host: String) { setup.consent?.let { ask -> if (asked.add(host) && !ask(host)) refuse("net.declined") } }
        consent(url.host)
        val network = setup.network ?: refuse("net.failed")
        var spent = 0
        var hop = 0
        while (true) {
            val started = System.nanoTime()
            val reply = network.send(method, url, headers, body, NET_MS - spent)
            val took = ((System.nanoTime() - started) / 1_000_000).toInt()
            spent += reply.delayMs
            if (reply.failure != null && reply.failure != "net.size") refuse(reply.failure)
            if (spent > NET_MS) refuse("net.time")
            val size = AddonText.utf8Count(reply.body)
            activity.add(AddonActivity(url.host, url.path, reply.status, AddonText.utf8Count(body) + size, maxOf(took, reply.delayMs)))
            if (reply.failure == "net.size" || size > NET_LIMIT) refuse("net.size")
            val location = reply.headers["location"]
            if (reply.status in listOf(301, 302, 303, 307, 308) && location != null) {
                if (hop >= REDIRECTS) refuse("net.redirect")
                val next = if (location.startsWith("/")) AddonUrl.parse("https://" + url.host + location) else AddonUrl.parse(location)
                if (next == null || next.scheme != "https" || "net:" + next.host !in setup.grants) refuse("net.redirect")
                if (reply.status == 303) { method = "GET"; body = "" }
                url = next
                consent(url.host)
                hop++
                continue
            }
            // nor is one passed back
            val shown = reply.headers.filter { AddonLower.of(it.key) !in listOf("set-cookie", "cookie") }.map { it.key to AddonJson.str(it.value) }
            return AddonJson.obj("status" to AddonJson.num(reply.status), "headers" to AddonJson.Obj(shown), "body" to AddonJson.str(reply.body))
        }
    }

    /** A card view's input as the add-on may read it: only ideas inside its read grant. Null when there is none to show, and then no call is made. */
    fun cardInput(ids: List<String>, batch: Boolean): String? {
        val shown = ids.mapNotNull { id -> if (id in view.ids) view.ideas.firstOrNull { it.id == id } else null }
        val first = shown.firstOrNull() ?: return null
        return if (batch) AddonJson.Arr(shown.map(view::json)).canonical else view.json(first).canonical
    }

    /** The call's result as the app takes it: a call that went past its writes or captures fails with that code, and only an ok call's writes are applied. */
    fun outcome(envelope: String): AddonOutcome {
        val env = poisoned?.let { failure(it) } ?: envelope
        val parsed = AddonJson.parseOrNull(env)
        val ok = parsed?.get("ok")?.bool == true
        return AddonOutcome(env, ok, if (ok) null else (parsed?.get("code")?.string ?: "js.error"), if (ok) batch.toList() else emptyList(),
            log.toList(), activity.toList())
    }
}

/** Lower case as JavaScript's toLowerCase gives it, whatever the phone's language. */
object AddonLower {
    fun of(s: String): String = s.lowercase(java.util.Locale.ROOT)
}

/** A URL as the add-on host reads it: scheme://host[:port]/path?query#fragment; no user info, no empty host. */
data class AddonUrl(val scheme: String, val host: String, val path: String, val query: String) {
    /** What is sent: the fragment is not. */
    val text: String get() = "$scheme://$host$path$query"

    companion object {
        fun parse(s: String): AddonUrl? {
            val sep = s.indexOf("://")
            if (sep < 1) return null
            val scheme = s.substring(0, sep)
            fun letter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
            if (!letter(scheme[0]) || !scheme.all { letter(it) || it in '0'..'9' || it == '+' || it == '.' || it == '-' }) return null
            var rest = s.substring(sep + 3)
            val hash = rest.indexOf('#')
            if (hash >= 0) rest = rest.substring(0, hash)
            val end = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, end)
            if (authority.isEmpty() || authority.contains('@')) return null
            val after = rest.substring(end)
            val q = after.indexOf('?').let { if (it < 0) after.length else it }
            var host = AddonLower.of(authority)
            if (host.endsWith(":443")) host = host.dropLast(4)
            val path = after.substring(0, q)
            return AddonUrl(AddonLower.of(scheme), host, if (path.isEmpty()) "/" else path, after.substring(q))
        }
    }
}

/** One response, or why there was none: net.time, net.size or net.failed. */
data class AddonNetReply(val status: Int = 0, val headers: Map<String, String> = emptyMap(), val body: String = "",
    /** How long it took; the stand-in network's own delay. */
    val delayMs: Int = 0, val failure: String? = null)

/** The network an add-on's net.fetch goes out on: one request at a time, redirects not followed (the host follows them itself, within the grants). */
interface AddonNetwork {
    fun send(method: String, url: AddonUrl, headers: Map<String, String>, body: String, timeLeftMs: Int): AddonNetReply
}

/** The recording stand-in network (addons/fixtures/net/routes.json): what the tests send to, never the internet. */
class AddonRoutesNetwork(json: AddonJson) : AddonNetwork {
    data class Sent(val method: String, val url: String, val headers: Map<String, String>, val body: String)

    private val routes = json["routes"]?.array ?: emptyList()
    val sent = ArrayList<Sent>()

    override fun send(method: String, url: AddonUrl, headers: Map<String, String>, body: String, timeLeftMs: Int): AddonNetReply {
        sent.add(Sent(method, url.text, headers.toMap(), body))
        val key = "https://" + url.host + url.path
        val r = routes.firstOrNull { it["method"]?.string == method && it["url"]?.string == key } ?: return AddonNetReply(status = 404)
        var text = r["body"]?.string ?: ""
        r["bodyRepeat"]?.let { rep ->
            val unit = rep["text"]?.string
            val n = rep["count"]?.integer
            if (unit != null && n != null) text = unit.repeat(n.toInt())
        }
        if (r["echo"]?.bool == true) text = body
        val hs = LinkedHashMap<String, String>()
        (r["headers"] as? AddonJson.Obj)?.pairs?.forEach { (k, v) -> hs[k] = v.string ?: "" }
        return AddonNetReply((r["status"]?.integer ?: 200).toInt(), hs, text, (r["delayMs"]?.integer ?: 0).toInt())
    }
}
