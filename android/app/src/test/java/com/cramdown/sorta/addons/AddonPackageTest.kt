package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * S07b A5, A6: the Kotlin package checker's codes (addons/spec/rules.md), on packages packed at test time
 * with the test-only zip writer, and the review before install. The kit's whole fixture set
 * (addons/fixtures/packages and zips) is S07a's and joins at the rebase.
 */
class AddonPackageTest {
    private fun code(manifest: String = AddonTestFiles.manifest(), files: Map<String, String> = emptyMap()) =
        AddonPackageCheck.verdict(AddonTestFiles.addon(manifest, files))

    private fun entry(name: String, text: String = "x") = AddonZipWriter.Entry(name.toByteArray(), text.toByteArray())
    private val base get() = listOf(entry("LICENSE", "MIT\n"), entry("main.js", "//\n"), entry("manifest.json", AddonTestFiles.manifest()))
    private fun zip(vararg more: AddonZipWriter.Entry) = AddonPackageCheck.verdict(AddonZipWriter.write(base + more))

    private fun deflated(name: String, data: ByteArray): AddonZipWriter.Entry {
        val d = Deflater(9, true)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!d.finished()) out.write(buffer, 0, d.deflate(buffer))
        d.end()
        return AddonZipWriter.Entry(name.toByteArray(), data, method = 8, packed = out.toByteArray())
    }

    @Test fun aGoodPackageIsAcceptedAndReviewedWithoutRunning() {
        val bytes = AddonTestFiles.addon(AddonTestFiles.manifest(permissions = listOf("ideas.read", "commands", "net"), hosts = listOf("api.example.com")))
        val review = AddonReview.make(bytes, "1.0.0")
        assertEquals("io.github.example.tests", review.pack.id)
        assertEquals(listOf("ideas.read", "commands", "net"), review.permissions)
        assertEquals(listOf("api.example.com"), review.hosts)
        assertEquals(bytes.size, review.size)
        assertEquals(AddonSha256.hex(bytes), review.sha256)
        assertEquals(64, review.sha256.length)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AddonSha256.hex(ByteArray(0)))
        assertTrue(review.allows("net:api.example.com") && review.allows("ideas.read"))
        assertTrue(!review.allows("net") && !review.allows("net:other.example.org") && !review.allows("ideas.write"))
    }

    @Test fun theAppsOwnInstallRulesRefuseANewerApiOrANewerSorta() {
        fun refusal(manifest: String, app: String = "1.0.0") = try { AddonReview.make(AddonTestFiles.addon(manifest), app); null } catch (r: AddonRefusal) { r.code }
        assertEquals("install.api", refusal(AddonTestFiles.manifest(api = 2)))
        assertEquals("install.minSorta", refusal(AddonTestFiles.manifest(minSorta = "1.0.1")))
        assertEquals("install.minSorta", refusal(AddonTestFiles.manifest(minSorta = "1.0.0"), app = "1.0.0-beta.2"))
        assertNull(refusal(AddonTestFiles.manifest(minSorta = "1.0.0-rc.1")))
        assertEquals(listOf(-1, 1, -1, -1, 0), listOf(AddonPackageCheck.compare("1.0.0-alpha", "1.0.0-alpha.1"), AddonPackageCheck.compare("1.0.0-beta.11", "1.0.0-beta.2"),
            AddonPackageCheck.compare("1.0.0-1", "1.0.0-alpha"), AddonPackageCheck.compare("1.9.0", "1.10.0"), AddonPackageCheck.compare("2.0.0", "2.0.0")))
    }

    @Test fun theArchiveRulesGiveTheirCodes() {
        assertNull(zip())
        assertNull("a deflated entry unpacks", zip(deflated("data.json", "{\"a\":[1,2,3,1,2,3,1,2,3,1,2,3]}".toByteArray())))
        assertEquals("zip.unreadable", AddonPackageCheck.verdict("not a zip at all, only words".toByteArray()))
        assertEquals("zip.size", AddonPackageCheck.verdict(ByteArray(AddonPackageCheck.MAX_ZIPPED + 1)))
        assertEquals("zip.unreadable", AddonPackageCheck.verdict(AddonZipWriter.write(base, comment = "hello".toByteArray())))
        assertEquals("zip.entries", AddonPackageCheck.verdict(AddonZipWriter.write(base + (1..198).map { entry("f$it.js") })))
        assertEquals("zip.encrypted", zip(AddonZipWriter.Entry("a.js".toByteArray(), "x".toByteArray(), flags = 1)))
        assertEquals("zip.unreadable", zip(AddonZipWriter.Entry("a.js".toByteArray(), "x".toByteArray(), method = 12)))
        for (path in listOf("../a.js", "/a.js", "a\\b.js", "C:a.js", "a//b.js", "./a.js", "a b.js", "é.js")) assertEquals(path, "zip.path", zip(entry(path)))
        assertEquals("zip.link", zip(AddonZipWriter.Entry("a.js".toByteArray(), "main.js".toByteArray(), external = 0xA1FFL shl 16)))
        assertEquals("zip.duplicate", zip(entry("a.js"), entry("a.js")))
        assertEquals("zip.case", zip(entry("a.js"), entry("A.js")))
        assertEquals("zip.ratio", zip(deflated("zeros.js", ByteArray(200_000) { 32 })))
        assertEquals("zip.unreadable", zip(AddonZipWriter.Entry("a.js".toByteArray(), "x".toByteArray(), crc = CRC32().also { it.update(1) }.value)))
        // bytes no entry accounts for, before the directory
        val whole = AddonZipWriter.write(base)
        assertEquals("zip.unreadable", AddonPackageCheck.verdict(byteArrayOf(0) + whole))
    }

    @Test fun theFileRulesGiveTheirCodes() {
        assertEquals("file.type", code(files = mapOf("picture.png" to "x")))
        assertEquals("file.type", code(files = mapOf(".hidden.js" to "x")))
        assertEquals("file.type", code(files = mapOf("lib/README.md" to "x")))
        assertEquals("file.depth", code(files = mapOf("a/b/c/d.js" to "x")))
        assertNull(code(files = mapOf("a/b/d.js" to "x", "README.md" to "Words")))
        assertEquals("file.text", zip(AddonZipWriter.Entry("a.js".toByteArray(), byteArrayOf(0x2F, 0x2F, 0))))
        assertEquals("file.text", zip(AddonZipWriter.Entry("a.js".toByteArray(), byteArrayOf(0x2F, 0x2F, 0xC0.toByte(), 0xAF.toByte()))))
        assertEquals("file.picture", code(files = mapOf("a.js" to "const p = '<SVG width=\"1\">';")))
        assertEquals("file.picture", code(files = mapOf("a.json" to "{\"p\":\"Data:Image/png;base64,AAAA\"}")))
        assertEquals("manifest.missing", AddonPackageCheck.verdict(AddonZipWriter.stored(mapOf("main.js" to "//"))))
        assertEquals("manifest.json", code(manifest = "[1]"))
        assertEquals("manifest.json", code(manifest = "﻿" + AddonTestFiles.manifest()))
        assertEquals("manifest.json", code(manifest = AddonTestFiles.manifest().replaceFirst("{", "{\"api\":1,")))
        assertEquals("manifest.json", code(manifest = AddonTestFiles.manifest(more = listOf("extra" to AddonJson.Arr(listOf(AddonJson.parse("[".repeat(32) + "]".repeat(32))))))))
    }

    @Test fun theManifestRulesGiveTheirCodes() {
        fun changed(key: String, value: String) = code(manifest = AddonJson.Obj((AddonJson.parse(AddonTestFiles.manifest()) as AddonJson.Obj).pairs
            .filter { it.first != key } + (key to AddonJson.parse(value))).canonical)
        assertEquals("format", changed("format", "\"other\""))
        assertEquals("formatVersion", changed("formatVersion", "2"))
        assertEquals("manifest.json", changed("surprise", "1"))
        assertEquals("id", changed("id", "\"NoDots\""))
        assertEquals("id.reserved", changed("id", "\"com.cramdown.sorta\""))
        assertEquals("name", changed("name", "\" padded\""))
        assertEquals("name", changed("name", "\"zero\\u200bwidth\""))
        assertEquals("name", changed("name", "\"line\\u2028break\""))
        assertEquals("version", changed("version", "\"1.0\""))
        assertEquals("version", changed("version", "\"1.0.0+build\""))
        assertEquals("minSorta", changed("minSorta", "\"01.0.0\""))
        assertEquals("api", changed("api", "1.5"))
        assertEquals("author", changed("author", "{\"name\":\"A\",\"url\":\"http://example.com\"}"))
        assertEquals("author", changed("author", "{\"name\":\"someone at example\",\"url\":\"https://exa mple.com\"}"))
        assertEquals("license", changed("license", "\"Proprietary\""))
        assertEquals("main.missing", changed("main", "\"other.js\""))
        assertEquals("permissions", changed("permissions", "[\"ideas.read\",\"ideas.read\",\"commands\"]"))
        assertEquals("permissions", changed("permissions", "[\"ideas.read\",\"ideas.read.folder\",\"commands\"]"))
        assertEquals("permissions", changed("permissions", "[\"camera\",\"commands\"]"))
        assertEquals("hosts", changed("hosts", "[\"api.example.com\"]"))
        assertEquals("hosts", code(manifest = AddonTestFiles.manifest(permissions = listOf("commands", "net"))))
        assertEquals("hosts", code(manifest = AddonTestFiles.manifest(permissions = listOf("commands", "net"), hosts = listOf("192.168.0.1"))))
        assertEquals("hosts", code(manifest = AddonTestFiles.manifest(permissions = listOf("commands", "net"), hosts = listOf("*.example.com"))))
        assertEquals("commands", changed("contributes", "{\"commands\":[{\"id\":\"Run It\",\"title\":\"Run\"}]}"))
        assertEquals("contributes", changed("contributes", "{}"))
        assertEquals("contributes", changed("contributes", "{\"commands\":[{\"id\":\"run\",\"title\":\"Run\"}],\"cardView\":{\"id\":\"v\",\"title\":\"V\"}}"))
        assertEquals("cards.read", code(manifest = AddonTestFiles.manifest(permissions = listOf("commands", "cards"))))
        assertNull(code(manifest = AddonTestFiles.manifest(permissions = listOf("ideas.read.folder", "commands", "cards"))))
        assertEquals("main.size", code(files = mapOf("main.js" to "/".repeat(AddonPackageCheck.MAX_MAIN + 1))))
    }

    @Test fun settingsAndPalettesAreCheckedAsData() {
        fun withSettings(json: String) = code(manifest = AddonJson.Obj((AddonJson.parse(AddonTestFiles.manifest()) as AddonJson.Obj).pairs.filter { it.first != "contributes" } +
            ("contributes" to AddonJson.parse("{\"commands\":[{\"id\":\"run\",\"title\":\"Run\"}],\"settings\":\"settings.json\"}"))).canonical,
            files = mapOf("settings.json" to json))
        assertNull(withSettings("{\"settings\":[{\"key\":\"titles\",\"type\":\"toggle\",\"title\":\"Count titles\",\"default\":false}," +
            "{\"key\":\"unit\",\"type\":\"choice\",\"title\":\"Unit\",\"default\":\"words\",\"choices\":[{\"value\":\"words\",\"title\":\"Words\"},{\"value\":\"letters\",\"title\":\"Letters\"}]}," +
            "{\"key\":\"label\",\"type\":\"text\",\"title\":\"Label\",\"default\":\"n\",\"maxLength\":5}]}"))
        assertEquals("contributes", withSettings("{\"settings\":[]}"))
        assertEquals("contributes", withSettings("{\"settings\":[{\"key\":\"Titles\",\"type\":\"toggle\",\"title\":\"T\",\"default\":false}]}"))
        assertEquals("contributes", withSettings("{\"settings\":[{\"key\":\"t\",\"type\":\"toggle\",\"title\":\"T\",\"default\":\"no\"}]}"))
        assertEquals("contributes", withSettings("{\"settings\":[{\"key\":\"t\",\"type\":\"text\",\"title\":\"T\",\"default\":\"too long\",\"maxLength\":3}]}"))
        assertEquals("contributes", withSettings("{\"settings\":[{\"key\":\"t\",\"type\":\"choice\",\"title\":\"T\",\"default\":\"c\",\"choices\":[{\"value\":\"a\",\"title\":\"A\"},{\"value\":\"b\",\"title\":\"B\"}]}]}"))
        assertEquals("contributes", withSettings("{\"settings\":[{\"key\":\"t\",\"type\":\"toggle\",\"title\":\"T\",\"default\":false,\"maxLength\":3}]}"))
        assertEquals(21.0, AddonPackageCheck.contrast("#000000", "#FFFFFF"), 1e-9)
        assertEquals(1.0, AddonPackageCheck.contrast("#777777", "#777777"), 1e-9)
    }

    @Test fun theLicenceListIsTheKits() {
        assertEquals(141, AddonLicenses.osi.size)
        assertTrue(listOf("MIT", "Apache-2.0", "GPL-3.0-only", "0BSD").all { it in AddonLicenses.osi })
        assertTrue("Proprietary" !in AddonLicenses.osi && "mit" !in AddonLicenses.osi)
    }
}
