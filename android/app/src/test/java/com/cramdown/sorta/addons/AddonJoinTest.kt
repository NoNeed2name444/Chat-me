package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Inflater

/**
 * Where the kit and the app join (S07, section 8; S07b A5): the Kotlin checker gives the kit's verdict on
 * every archive of the kit's fixtures (addons/conformance/archives.json, which archives.py packs from
 * addons/fixtures/packages and zips, with the verdict check.py gives), and the host answers every call of
 * API v1 (addons/kit/api/v1.json). iOS: AddonJoinTests.swift.
 */
class AddonJoinTest {
    private class Archive(val name: String, val bytes: ByteArray, val verdict: String, val reason: String?, val appSorta: String?, val appApi: Int) {
        val wanted get() = reason ?: verdict
    }

    private val doc = AddonTestFiles.json("archives.json")

    private val archives: List<Archive> by lazy {
        (doc["archives"]?.array ?: emptyList()).map { a ->
            val name = a["name"]?.string ?: "?"
            val out = ByteArray(a["size"]?.integer?.toInt() ?: 0)
            val inflater = Inflater(true)
            inflater.setInput(Base64.getDecoder().decode(a["deflated"]?.string ?: ""))
            var n = 0
            while (n < out.size) {
                val k = inflater.inflate(out, n, out.size - n)
                if (k == 0) break
                n += k
            }
            inflater.end()
            assertEquals("$name unpacks to its size", out.size, n)
            assertEquals("$name is the bytes archives.py packed", a["sha256"]?.string, AddonSha256.hex(out))
            Archive(name, out, a["verdict"]?.string ?: "?", a["reason"]?.string, a["app"]?.get("sorta")?.string, a["app"]?.get("api")?.integer?.toInt() ?: 1)
        }
    }

    /** "ok", or the code the app refuses the archive with: the checker alone, or with the install rules of the app the fixture names. */
    private fun verdict(a: Archive, install: Boolean): String {
        val sorta = a.appSorta
        if (!install || sorta == null) return AddonPackageCheck.verdict(a.bytes) ?: "ok"
        return try { AddonReview.make(a.bytes, sorta, a.appApi); "ok" } catch (r: AddonRefusal) { r.code }
    }

    @Test fun theFileHoldsEveryFixtureThatCanBeAnArchive() {
        assertEquals(95, archives.size)
        assertEquals(95L, doc["count"]?.integer)
        assertEquals(83, archives.count { it.verdict == "refused" })
        assertEquals(12, archives.count { it.verdict == "ok" })
        assertEquals(71, (doc["archives"]?.array ?: emptyList()).count { it["from"]?.string == "packages" })
        assertEquals(24, (doc["archives"]?.array ?: emptyList()).count { it["from"]?.string == "zips" })
        assertEquals(listOf("file-type-fifo"), doc["leftOut"]?.array?.map { it["name"]?.string })
        assertEquals("every name once", 95, archives.map { it.name }.toSet().size)
        assertTrue("a refused archive names its reason", archives.all { (it.verdict == "refused") == (it.reason != null) })
    }

    @Test fun everyArchiveGetsTheVerdictTheKitsCheckerGives() {
        val differ = archives.mapNotNull { a -> verdict(a, true).let { got -> if (got == a.wanted) null else "${a.name}: $got, and the kit says ${a.wanted}" } }
        assertEquals(emptyList<String>(), differ)
    }

    @Test fun theInstallCodesNeedTheAppAndEveryOtherVerdictIsTheCheckersOwn() {
        val withApp = archives.filter { it.appSorta != null }
        assertEquals(listOf("install-api", "install-min-sorta", "ok-install"), withApp.map { it.name })
        assertEquals(listOf("install.api", "install.minSorta", "ok"), withApp.map { verdict(it, true) })
        // the package itself is good: only the app it is installed on refuses it
        for (a in withApp) assertNull(a.name, AddonPackageCheck.verdict(a.bytes))
        for (a in archives.filter { it.appSorta == null }) assertEquals(a.name, a.wanted, verdict(a, false))
    }

    @Test fun everyCallOfApiV1IsOneTheHostAnswers() {
        val names = (AddonTestFiles.json("api/v1.json")["calls"] as AddonJson.Obj).pairs.map { it.first }
        assertEquals(20, names.size)
        val short = names.map { it.removePrefix("sorta.") }
        assertEquals("the host's table is the API's calls, and has() beside them", (AddonPermissions.calls + "has").sorted(), short.sorted())
        val snap = AddonSnapshot.read(AddonTestFiles.json("store.json"))
        val grants = listOf("ideas.read", "ideas.write", "commands", "cards", "net:api.example.com")
        for (name in short) {
            val call = AddonCall(AddonSetup(snap, grants, listOf("run"), true, network = null, now = 1_793_523_600_000)).also { it.phase = "command" }
            assertNotEquals(name, "!api.unknown", call.handle(name, "[]"))
        }
        val call = AddonCall(AddonSetup(snap, grants, listOf("run"), true, network = null, now = 1_793_523_600_000)).also { it.phase = "command" }
        assertEquals("!api.unknown", call.handle("ideas.delete", "[]"))
    }
}
