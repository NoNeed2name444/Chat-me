package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07b A2: JSON as every host writes it, so iOS, Android and the harness give the same bytes. */
class AddonJsonTest {
    private fun canon(text: String) = AddonJson.parse(text).canonical

    @Test fun keysAreSortedByUtf16UnitAndNothingIsSpaced() {
        assertEquals("""{"A":1,"a":[true,false,null],"b":{"x":"y"},"é":2,"😀":3}""", canon("""{ "é": 2, "b": {"x": "y"}, "😀": 3, "a": [true, false, null], "A": 1 }"""))
        // a surrogate pair sorts by its first unit, before U+FFFD
        assertEquals(""""😀"""", AddonJson.quoted("\uD83D\uDE00"))
        assertTrue(AddonText.less("\uD83D\uDE00", "\uFFFD"))
    }

    @Test fun numbersAreWrittenAsJavaScriptWritesThem() {
        val cases = listOf("0" to "0", "-0" to "0", "1.0" to "1", "0.1" to "0.1", "1e21" to "1e+21", "1e-7" to "1e-7", "123456789012345680000" to "123456789012345680000",
            "9007199254740993" to "9007199254740992", "0.000001" to "0.000001", "1.5e-10" to "1.5e-10", "-2.50" to "-2.5", "1E3" to "1000", "123.456" to "123.456",
            "5e-324" to "5e-324", "1.7976931348623157e308" to "1.7976931348623157e+308")
        for ((given, written) in cases) assertEquals(given, written, canon(given))
    }

    @Test fun stringsAreEscapedAsJsonStringifyEscapesThem() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\\u001f/é\"", AddonJson.quoted("a\"b\\c\n\t\u0001\u001f/é"))
        assertEquals("سلام", AddonJson.parse("\"\\u0633\\u0644\\u0627\\u0645\"").string)
    }

    @Test fun whatIsNotJsonIsRefused() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "01", "1.", "+1", "'a'", "\"a\nb\"", "nul", "[1] 2", "{\"a\" 1}", "\"\\x\"", "\"\\u12G4\""))
            assertNull(bad, AddonJson.parseOrNull(bad))
        // the last of a key twice wins, as JSON.parse; the package checker's strict reading refuses it
        assertEquals("""{"a":2}""", canon("""{"a":1,"a":2}"""))
        assertNull(AddonPackageCheck.strictJson("""{"a":1,"a":2}"""))
    }

    @Test fun timesAreWrittenAsToIsoStringWritesThem() {
        assertEquals("2026-11-01T09:00:00.000Z", AddonText.iso(1_793_523_600_000))
        assertEquals(1_793_523_600_000, AddonText.parseIso("2026-11-01T09:00:00.000Z"))
        assertEquals("1970-01-01T00:00:00.000Z", AddonText.iso(0))
        assertEquals("1969-12-31T23:59:59.999Z", AddonText.iso(-1))
        assertEquals("2024-02-29T12:34:56.789Z", AddonText.iso(AddonText.parseIso("2024-02-29T12:34:56.789Z")!!))
        assertNull(AddonText.parseIso("2026-11-01 09:00:00"))
    }

    @Test fun trimmingAndWordsUseAsciiWhiteSpaceOnly() {
        assertEquals("a\u00A0", AddonText.trim(" \t\na\u00A0\r "))
        assertEquals(listOf("one", "two\u00A0three"), AddonText.words("  one \n two\u00A0three\t"))
    }
}
