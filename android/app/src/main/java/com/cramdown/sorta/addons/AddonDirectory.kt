package com.cramdown.sorta.addons

// Add-ons (S07b): the public directory's client (section 7).
//
// Reads index.json and revoked.json from the directory's index branch (the Gradle property
// sorta.addonsIndexUrl; empty until the lead fills it in). Every package is checked against its SHA-256
// in the index before it is opened. Updates are offered, never applied silently. The daily check runs
// only while an add-on from the directory is installed. It follows ios/Sorta/Addons/AddonDirectory.swift.

data class AddonIndexEntry(val id: String, val version: String, val sha256: String, val size: Int, val permissions: List<String>,
    val hosts: List<String>, val minSorta: String, val api: Int, val file: String)

/** A newer listed version of an installed add-on. [newPermissions] are the grants the person is asked for, one by one; empty means one tap updates. */
data class AddonUpdateOffer(val id: String, val from: String, val to: String, val newPermissions: List<String>) {
    val oneTap: Boolean get() = newPermissions.isEmpty()
}

/** [base] ends with "/" and holds index.json, revoked.json and dist/. */
class AddonDirectory(base: String, private val fetcher: AddonFetcher, private val registry: AddonRegistry, private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis) {
    val base: String = if (base.endsWith("/") || base.isEmpty()) base else "$base/"

    companion object {
        const val MAX_PACKAGE = AddonPackageCheck.MAX_ZIPPED
        const val MAX_INDEX = 1_048_576
        const val DAY_MS = 86_400_000L

        fun parseIndex(data: ByteArray): List<AddonIndexEntry> {
            val j = AddonJson.parseOrNull(String(data, Charsets.UTF_8))
            val list = j?.get("addons")?.array
            if (j == null || j["formatVersion"]?.integer != 1L || list == null) throw AddonRefusal("index.json")
            return list.map { e ->
                val id = e["id"]?.string
                val version = e["version"]?.string
                val sha = e["sha256"]?.string
                val size = e["size"]?.integer
                val file = e["file"]?.string
                if (id == null || version == null || sha == null || sha.length != 64 || size == null || file == null ||
                    !AddonPackageCheck.validId(id) || AddonPackageCheck.semver(version) == null ||
                    file != "dist/$id-$version.sortaaddon") throw AddonRefusal("index.json")
                AddonIndexEntry(id, version, AddonLower.of(sha), size.toInt(), e["permissions"]?.strings ?: emptyList(),
                    e["hosts"]?.strings ?: emptyList(), e["minSorta"]?.string ?: "0.0.0", (e["api"]?.integer ?: 1).toInt(), file)
            }
        }

        fun parseRevoked(data: ByteArray): List<Pair<String, String?>> {
            val list = AddonJson.parseOrNull(String(data, Charsets.UTF_8))?.get("revoked")?.array ?: throw AddonRefusal("index.json")
            return list.mapNotNull { e -> e["id"]?.string?.let { it to e["version"]?.string } }
        }
    }

    /** The listed add-ons; an AddonRefusal says why not. */
    fun index(): List<AddonIndexEntry> {
        if (base.isEmpty()) throw AddonRefusal("index.none")
        return parseIndex(fetcher.get(base + "index.json", MAX_INDEX))
    }

    /** A listed package's bytes, refused unless they match the index's size and SHA-256. */
    fun download(e: AddonIndexEntry): ByteArray {
        if (e.size > MAX_PACKAGE) throw AddonRefusal("zip.size")
        val d = fetcher.get(base + e.file, MAX_PACKAGE)
        if (d.size != e.size || AddonSha256.hex(d) != e.sha256) throw AddonRefusal("index.checksum")
        return d
    }

    /** The review of a listed package: downloaded, checksum checked, then checked as any file is. */
    fun review(e: AddonIndexEntry): Pair<AddonReview, ByteArray> {
        val d = download(e)
        val rv = AddonReview.make(d, appVersion)
        if (rv.pack.id != e.id || rv.pack.version != e.version) throw AddonRefusal("index.checksum")
        return rv to d
    }

    /** For each installed directory add-on, the newest listed version this app can run, if it is newer; new permissions are listed one by one. */
    fun offers(entries: List<AddonIndexEntry>): List<AddonUpdateOffer> {
        val out = ArrayList<AddonUpdateOffer>()
        for (a in registry.addons) {
            if (!a.fromDirectory || a.revoked) continue
            val newer = entries.filter {
                it.id == a.id && AddonPackageCheck.compare(it.version, a.version) > 0 && it.api <= AddonPackageCheck.APP_API &&
                    AddonPackageCheck.compare(it.minSorta, appVersion) <= 0
            }
            val best = newer.maxWithOrNull { x, y -> AddonPackageCheck.compare(x.version, y.version) } ?: continue
            val had = (a.permissions + a.hosts.map { "net:$it" }).toSet()
            val wants = best.permissions.filter { it != "net" } + best.hosts.map { "net:$it" }
            out.add(AddonUpdateOffer(a.id, a.version, best.version, wants.filter { it !in had }))
        }
        return out.sortedBy { it.id }
    }

    /**
     * At most once a day, and only while a directory add-on is installed: revoked.json turns add-ons off,
     * and the index gives update offers. Null when it did not run. Never called on the main thread.
     */
    fun dailyCheck(): List<AddonUpdateOffer>? {
        val now = clock()
        if (base.isEmpty() || !registry.hasDirectoryAddon || now - registry.lastDirectoryCheck < DAY_MS) return null
        registry.lastDirectoryCheck = now
        try { registry.applyRevoked(parseRevoked(fetcher.get(base + "revoked.json", MAX_INDEX))) } catch (r: AddonRefusal) { /* the next day's check tries again */ }
        return try { offers(index()) } catch (r: AddonRefusal) { emptyList() }
    }
}
