package com.cramdown.sorta.addons

// Add-ons (S07b): what an add-on may see and ask for. The snapshot a call reads (S07, section 6), the
// permission table (section 4), and the writes a call hands back. It follows
// ios/Sorta/Addons/AddonModel.swift; addons/kit/harness/standin.js is the reference for both.

/** One idea as the snapshot holds it. [x] and [y] are its board spot, which the add-on never sees. */
data class AddonIdea(
    val id: String, val title: String, val text: String, val kind: String, val folder: String?,
    val tags: List<String>, val links: List<String>, val createdAt: Long, val updatedAt: Long,
    val x: Double = 0.0, val y: Double = 0.0,
)

data class AddonFolder(val id: String, val name: String, val parent: String?, val words: List<String>)

/** An immutable copy of the ideas and folders, taken on the main thread when a call is dispatched. */
data class AddonSnapshot(val ideas: List<AddonIdea>, val folders: List<AddonFolder>) {
    companion object {
        /** The sample ideas' JSON (addons/conformance/store.json and the kit's). */
        fun read(json: AddonJson): AddonSnapshot {
            val folders = (json["folders"]?.array ?: emptyList()).map { f ->
                AddonFolder(f["id"]?.string ?: "", f["name"]?.string ?: "", f["parent"]?.string, f["words"]?.strings ?: emptyList())
            }
            val ideas = (json["ideas"]?.array ?: emptyList()).map { i ->
                AddonIdea(i["id"]?.string ?: "", i["title"]?.string ?: "", i["text"]?.string ?: "", i["kind"]?.string ?: "idea",
                    i["folder"]?.string, i["tags"]?.strings ?: emptyList(), i["links"]?.strings ?: emptyList(),
                    AddonText.parseIso(i["createdAt"]?.string ?: "") ?: 0, AddonText.parseIso(i["updatedAt"]?.string ?: "") ?: 0)
            }
            return AddonSnapshot(ideas, folders)
        }
    }
}

/** One write, kept on the add-on's thread until the call returns. */
sealed class AddonOp {
    data class Capture(val id: String, val title: String, val body: String, val x: Double, val y: Double) : AddonOp()
    data class Tag(val id: String, val tags: List<String>) : AddonOp()
    data class Untag(val id: String, val tags: List<String>) : AddonOp()
    data class Link(val a: String, val b: String) : AddonOp()
    data class Unlink(val a: String, val b: String) : AddonOp()
    data class File(val id: String, val folder: String?) : AddonOp()
}

/** What applying a call's writes did: ideas added, ideas changed, and the ids skipped because they had gone since the snapshot. */
data class AddonApplied(val added: List<String> = emptyList(), val changed: List<String> = emptyList(), val skipped: List<String> = emptyList()) {
    val json: AddonJson get() = AddonJson.obj("added" to AddonJson.strings(added), "changed" to AddonJson.strings(changed),
        "skipped" to AddonJson.strings(skipped))
}

/** One request in the add-on's activity log: the host and the path, never the query and never a header. */
data class AddonActivity(val host: String, val path: String, val status: Int, val bytes: Int, val ms: Int) {
    /** As the conformance cases print it (the time left out: it varies). */
    val line: String get() = "activity " + AddonJson.obj("bytes" to AddonJson.num(bytes), "host" to AddonJson.str(host),
        "path" to AddonJson.str(path), "status" to AddonJson.num(status)).canonical
}

/** The permission table (addons/conformance/permissions.json). */
object AddonPermissions {
    const val FOLDER_GRANT = "ideas.read.folder:"
    val reads = listOf("ideas.list", "ideas.get", "ideas.search", "folders.list")
    val writes = listOf("ideas.capture", "ideas.tag", "ideas.untag", "ideas.link", "ideas.unlink", "ideas.file")
    val calls = reads + writes + listOf("commands.on", "cards.on", "settings.get", "settings.on",
        "storage.get", "storage.set", "storage.remove", "net.fetch", "console.log")
    /** While an add-on loads, before its first call, only these answer. */
    val loadCalls = listOf("commands.on", "cards.on", "settings.on", "console.log")
    /** The permissions a manifest may ask for, besides net:<host>. */
    val known = listOf("ideas.read", "ideas.read.folder", "ideas.write", "commands", "cards", "board.palette")

    fun canRead(grants: List<String>) = "ideas.read" in grants || grants.any { it.startsWith(FOLDER_GRANT) }

    /** Whether a call may run with these grants ([host] for net.fetch). */
    fun allows(grants: List<String>, call: String, host: String?, netOn: Boolean): Boolean = when {
        call in reads -> canRead(grants)
        call in writes -> "ideas.write" in grants
        call == "commands.on" -> "commands" in grants
        call == "cards.on" -> "cards" in grants
        call == "net.fetch" -> netOn && host != null && ("net:$host") in grants
        call.startsWith("ai.") -> false
        else -> call in calls
    }

    /** sorta.has(name): a call or a permission. */
    fun has(grants: List<String>, name: String, netOn: Boolean): Boolean {
        if (name in calls) {
            if (name == "net.fetch") return netOn && grants.any { it.startsWith("net:") }
            return allows(grants, name, null, netOn)
        }
        if (name == "net") return netOn && grants.any { it.startsWith("net:") }
        if (name.startsWith("net:")) return netOn && name in grants
        if (name == "ideas.read.folder") return grants.any { it.startsWith(FOLDER_GRANT) }
        if (name in listOf("ideas.read", "ideas.write", "commands", "cards", "board.palette")) return name in grants
        return false
    }
}

/** The snapshot as one add-on may read it. */
class AddonView(snap: AddonSnapshot, grants: List<String>) {
    val folders: Set<String>
    val ideas: List<AddonIdea>
    val ids: Set<String>
    val links: Map<String, Set<String>>

    init {
        val all = "ideas.read" in grants
        val visible = HashSet<String>()
        if (all) {
            snap.folders.forEach { visible.add(it.id) }
        } else {
            val known = snap.folders.map { it.id }.toHashSet()
            for (g in grants) if (g.startsWith(AddonPermissions.FOLDER_GRANT)) {
                val root = g.substring(AddonPermissions.FOLDER_GRANT.length)
                if (root in known) visible.add(root)
            }
            var grew = true
            while (grew) {
                grew = false
                for (f in snap.folders) if (f.id !in visible && f.parent != null && f.parent in visible) { visible.add(f.id); grew = true }
            }
        }
        val shown = snap.ideas.filter { all || (it.folder != null && it.folder in visible) }
        val shownIds = shown.map { it.id }.toHashSet()
        val map = HashMap<String, HashSet<String>>()
        for (i in shown) map[i.id] = HashSet()
        for (i in shown) for (l in i.links) if (l in shownIds && l != i.id) {
            map[i.id]?.add(l)
            map[l]?.add(i.id)
        }
        folders = visible
        ideas = shown
        ids = shownIds
        links = map
    }

    fun json(i: AddonIdea): AddonJson = AddonJson.obj(
        "id" to AddonJson.str(i.id), "title" to AddonJson.str(i.title), "text" to AddonJson.str(i.text), "kind" to AddonJson.str(i.kind),
        "folder" to (if (i.folder != null && i.folder in folders) AddonJson.str(i.folder) else AddonJson.Null),
        "tags" to AddonJson.strings(i.tags), "links" to AddonJson.strings((links[i.id] ?: emptySet()).sorted()),
        "createdAt" to AddonJson.str(AddonText.iso(i.createdAt)), "updatedAt" to AddonJson.str(AddonText.iso(i.updatedAt)),
    )
}
