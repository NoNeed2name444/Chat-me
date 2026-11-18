package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07b A6: the directory client on fixture copies of the index branch (addons/fixtures/index), with a stand-in fetcher and clock. */
class AddonDirectoryTest {
    private val base = "https://addons.example.org/index/"
    private var clock = 5_000_000_000L
    private val fetched = ArrayList<String>()
    private val registry = AddonRegistry(AddonTestFiles.temporaryFolder(), clock = { clock })

    /** The index branch: its two files as they are, and each listed package packed from its folder. */
    private val fetcher = AddonFetcher { url, limit ->
        fetched.add(url)
        if (!url.startsWith(base)) throw AddonRefusal("net.failed")
        val path = url.removePrefix(base)
        val bytes = when {
            path == "index.json" || path == "revoked.json" -> AddonTestFiles.file("index/$path").readBytes()
            path.startsWith("dist/") -> AddonZipWriter.folder(AddonTestFiles.file("index/packages/" + path.removePrefix("dist/").removeSuffix(".sortaaddon") + "/manifest.json").parentFile!!)
            else -> throw AddonRefusal("net.status")
        }
        if (bytes.size > limit) throw AddonRefusal("net.size")
        bytes
    }
    private fun directory(appVersion: String = "1.0.0") = AddonDirectory(base, fetcher, registry, appVersion, clock = { clock })
    private fun entry(d: AddonDirectory, id: String, version: String) = d.index().first { it.id == "io.github.example.$id" && it.version == version }
    private fun install(d: AddonDirectory, id: String, version: String) {
        val (review, bytes) = d.review(entry(d, id, version))
        registry.install(review, bytes, review.permissions.filter { it != "net" } + review.hosts.map { "net:$it" }, fromDirectory = true)
    }

    @Test fun aListedPackageIsCheckedAgainstTheIndexBeforeItIsOpened() {
        val d = directory()
        assertEquals(8, d.index().size)
        val (review, bytes) = d.review(entry(d, "wordcount", "1.0.0"))
        assertEquals(AddonSha256.hex(bytes), review.sha256)
        assertEquals("index.checksum", try { d.review(entry(d, "badsum", "1.0.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("install.minSorta", try { d.review(entry(d, "tagger", "1.5.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("install.api", try { d.review(entry(d, "tagger", "2.0.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("index.none", try { AddonDirectory("", fetcher, registry, "1.0.0").index(); null } catch (r: AddonRefusal) { r.code })
        assertEquals("index.json", try { AddonDirectory.parseIndex("{\"formatVersion\":2,\"addons\":[]}".toByteArray()); null } catch (r: AddonRefusal) { r.code })
    }

    @Test fun updatesAreOfferedBySectionSevensRules() {
        val d = directory()
        install(d, "wordcount", "1.0.0")
        install(d, "linkcheck", "1.0.0")
        install(d, "tagger", "1.0.0")
        val offers = d.offers(d.index())
        // tagger's newer versions need a newer Sorta or a newer API, so none is offered
        assertEquals(listOf("io.github.example.linkcheck", "io.github.example.wordcount"), offers.map { it.id })
        val link = offers[0]
        assertEquals("1.0.0" to "1.2.0", link.from to link.to)
        assertEquals("new permissions are asked for one by one", listOf("ideas.write", "net:cdn.example.com"), link.newPermissions)
        assertFalse(link.oneTap)
        assertTrue("the same permissions: one tap", offers[1].oneTap && offers[1].to == "1.1.0")
        // an offer is never applied on its own
        assertEquals("1.0.0", registry.addon("io.github.example.wordcount")!!.version)
        install(d, "wordcount", "1.1.0")
        assertEquals(listOf("io.github.example.linkcheck"), d.offers(d.index()).map { it.id })
    }

    @Test fun theDailyCheckRunsOnlyWhileADirectoryAddonIsInstalled() {
        val d = directory()
        assertNull("nothing from the directory: no request", d.dailyCheck())
        val bytes = AddonTestFiles.addon()
        registry.install(AddonReview.make(bytes, "1.0.0"), bytes, emptyList())
        assertNull("an add-on from a file is not one", d.dailyCheck())
        assertTrue(fetched.isEmpty())
        install(d, "wordcount", "1.0.0")
        fetched.clear()
        assertEquals(listOf("io.github.example.wordcount"), d.dailyCheck()!!.map { it.id })
        assertEquals(listOf(base + "revoked.json", base + "index.json"), fetched)
        clock += AddonDirectory.DAY_MS - 1
        assertNull("once a day", d.dailyCheck())
        clock += 1
        assertEquals(1, d.dailyCheck()!!.size)
    }

    @Test fun aRevokedAddonIsTurnedOff() {
        val d = directory()
        install(d, "tagger", "1.0.0")
        install(d, "wordcount", "1.0.0")
        val stopped = ArrayList<String>()
        registry.onStop = { stopped.add(it) }
        assertTrue(registry.canRun("io.github.example.tagger"))
        d.dailyCheck()
        val tagger = registry.addon("io.github.example.tagger")!!
        assertTrue(tagger.revoked && !tagger.on)
        assertFalse(registry.canRun("io.github.example.tagger"))
        assertTrue(registry.canRun("io.github.example.wordcount"))
        assertEquals(listOf("io.github.example.tagger"), stopped)
        assertEquals(listOf("io.github.example.tagger" to "1.0.0"), AddonDirectory.parseRevoked(AddonTestFiles.file("index/revoked.json").readBytes()))
    }

    @Test fun requestsCarryNoCookiesNoIdsAndThePlainUserAgent() {
        val c = AddonHttp.open(java.net.URL("https://addons.example.org/index/index.json"), "GET", mapOf("Cookie" to "c-value-4419", "Accept" to "application/json"), 1_000)
        assertEquals("Sorta", c.getRequestProperty("User-Agent"))
        assertEquals(mapOf("Accept" to listOf("application/json"), "User-Agent" to listOf("Sorta")), c.requestProperties)
        assertFalse(c.instanceFollowRedirects)
        assertFalse(c.useCaches)
        assertNull("no cookie store is set for the app", java.net.CookieHandler.getDefault())
        assertEquals("net.https", try { AddonHttpsFetcher().get("http://addons.example.org/index.json", 10); null } catch (r: AddonRefusal) { r.code })
        assertEquals("net.https", AddonHttpsNetwork().perform("GET", AddonUrl.parse("http://addons.example.org/x")!!, emptyMap(), "", 1_000).first.failure)
    }
}
