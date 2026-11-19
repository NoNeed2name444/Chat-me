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
    private fun entry(d: AddonDirectory, id: String, version: String, owner: String = "io.github.example.") = d.index().first { it.id == owner + id && it.version == version }
    private fun install(d: AddonDirectory, id: String, version: String, owner: String = "io.github.example.") {
        val (review, bytes) = d.review(entry(d, id, version, owner))
        registry.install(review, bytes, review.permissions.filter { it != "net" } + review.hosts.map { "net:$it" }, fromDirectory = true)
    }

    @Test fun aListedPackageIsCheckedAgainstTheIndexBeforeItIsOpened() {
        val d = directory()
        val listed = d.index()
        assertEquals("each version of each add-on is one entry", 9, listed.size)
        assertEquals(5, listed.map { it.id }.distinct().size)
        // the add-on's name and its author's url come with each of its versions
        val tagger = listed.filter { it.id == "io.github.example.tagger" }
        assertEquals(listOf("1.0.0", "1.5.0", "2.0.0"), tagger.map { it.version })
        assertTrue(tagger.all { it.name == "Tagger" && it.authorUrl == "https://github.com/example" })
        assertEquals("https://github.com/sample", listed.first { it.id == "io.github.sample.notes" }.authorUrl)
        val (review, bytes) = d.review(entry(d, "wordcount", "1.0.0"))
        assertEquals(AddonSha256.hex(bytes), review.sha256)
        assertEquals("index.checksum", try { d.review(entry(d, "badsum", "1.0.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("install.minSorta", try { d.review(entry(d, "tagger", "1.5.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("install.api", try { d.review(entry(d, "tagger", "2.0.0")); null } catch (r: AddonRefusal) { r.code })
        assertEquals("index.none", try { AddonDirectory("", fetcher, registry, "1.0.0").index(); null } catch (r: AddonRefusal) { r.code })
        fun refused(text: String) = try { AddonDirectory.parseIndex(text.toByteArray()); null } catch (r: AddonRefusal) { r.code }
        assertEquals("index.json", refused("{\"format\":\"sorta-addons-index\",\"formatVersion\":2,\"addons\":[]}"))
        assertEquals("index.json", refused("{\"formatVersion\":1,\"addons\":[]}"))
        assertNull("an empty directory, and a field the app doesn't know", refused("{\"format\":\"sorta-addons-index\",\"formatVersion\":1,\"addons\":[],\"signatures\":{}}"))
        // one entry for each version was the lane's own first guess at the format: it is not the directory's
        assertEquals("index.json", refused("{\"format\":\"sorta-addons-index\",\"formatVersion\":1,\"addons\":[{\"id\":\"io.github.example.a\",\"version\":\"1.0.0\"}]}"))
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

    @Test fun aFailedCheckIsTriedAgainAtTheNextChance() {
        val d = directory()
        install(d, "wordcount", "1.0.0")
        var down = true
        val flaky = AddonFetcher { url, limit -> if (down) throw AddonRefusal("net.failed") else fetcher.get(url, limit) }
        val f = AddonDirectory(base, flaky, registry, "1.0.0", clock = { clock })
        assertEquals("the check ran and got nothing", emptyList<AddonUpdateOffer>(), f.dailyCheck())
        assertEquals("a failed check does not start the day", 0L, registry.lastDirectoryCheck)
        down = false
        assertEquals(1, f.dailyCheck()!!.size)
        assertEquals(clock, registry.lastDirectoryCheck)
        assertNull("once a day, from the check that worked", f.dailyCheck())
    }

    /**
     * The fixture revoked.json has one entry of each kind: tagger 1.0.0 by version, linkcheck 1.2.0 by a
     * version nobody here has, badsum whole, and the author of io.github.sample.notes.
     */
    @Test fun aRevokedAddonIsTurnedOff() {
        val d = directory()
        install(d, "tagger", "1.0.0")
        install(d, "wordcount", "1.0.0")
        install(d, "linkcheck", "1.0.0")
        install(d, "notes", "1.0.0", owner = "io.github.sample.")
        assertEquals("https://github.com/sample", registry.addon("io.github.sample.notes")!!.authorUrl)
        val stopped = ArrayList<String>()
        registry.onStop = { stopped.add(it) }
        assertTrue(registry.canRun("io.github.example.tagger") && registry.canRun("io.github.sample.notes"))
        d.dailyCheck()
        val tagger = registry.addon("io.github.example.tagger")!!
        assertTrue(tagger.revoked && !tagger.on)
        assertFalse(registry.canRun("io.github.example.tagger"))
        assertFalse("its author is revoked", registry.canRun("io.github.sample.notes"))
        assertTrue("another author's add-on", registry.canRun("io.github.example.wordcount"))
        assertTrue("only version 1.2.0 of it is revoked, and 1.0.0 is installed", registry.canRun("io.github.example.linkcheck"))
        assertEquals(listOf("io.github.example.tagger", "io.github.sample.notes"), stopped)
        assertEquals(listOf(
            AddonRevoked(id = "io.github.example.tagger", versions = listOf("1.0.0")),
            AddonRevoked(id = "io.github.example.linkcheck", versions = listOf("1.2.0")),
            AddonRevoked(id = "io.github.example.badsum"),
            AddonRevoked(author = "https://github.com/sample"),
        ), AddonDirectory.parseRevoked(AddonTestFiles.file("index/revoked.json").readBytes()))
    }

    @Test fun revokedEntriesCoverWhatTheDirectorySaysAndNoMore() {
        fun parsed(entries: String) = AddonDirectory.parseRevoked("{\"format\":\"sorta-addons-revoked\",\"formatVersion\":1,\"revoked\":[$entries]}".toByteArray())
        fun installedAt(version: String, author: String = "https://github.com/example") =
            AddonInstalled("io.github.example.a", version, "", emptyList(), emptyList(), emptyList(), authorUrl = author)
        val versions = parsed("{\"id\":\"io.github.example.a\",\"versions\":[\"1.0.0\",\"1.1.0\"],\"reason\":\"r\",\"date\":\"2026-11-01\"}")
        assertTrue(versions[0].covers(installedAt("1.1.0")) && !versions[0].covers(installedAt("1.2.0")))
        val whole = parsed("{\"id\":\"io.github.example.a\",\"reason\":\"r\",\"date\":\"2026-11-01\"}")
        assertTrue(whole[0].covers(installedAt("9.9.9")))
        val author = parsed("{\"author\":\"https://github.com/example\",\"reason\":\"r\",\"date\":\"2026-11-01\"}")
        assertTrue(author[0].covers(installedAt("1.0.0")))
        assertFalse("the url matches exactly, or not at all", author[0].covers(installedAt("1.0.0", "https://github.com/example2")))
        assertFalse("an add-on kept from before the author was stored", author[0].covers(installedAt("1.0.0", "")))
        // an entry the app can't read revokes nothing: it never widens to every version
        assertTrue(parsed("{\"id\":\"io.github.example.a\",\"versions\":\"1.0.0\"}").isEmpty())
        assertTrue(parsed("{\"id\":\"io.github.example.a\",\"versions\":[]}").isEmpty())
        assertTrue(parsed("{\"id\":\"io.github.example.a\",\"versions\":[1]}").isEmpty())
        assertTrue(parsed("{\"id\":\"io.github.example.a\",\"author\":\"https://github.com/example\"}").isEmpty())
        assertTrue(parsed("{\"author\":\"http://github.com/example\"}").isEmpty())
        assertTrue(parsed("{\"version\":\"1.0.0\"}").isEmpty())
        assertEquals("index.json", try { AddonDirectory.parseRevoked("{\"revoked\":[]}".toByteArray()); null } catch (r: AddonRefusal) { r.code })
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
