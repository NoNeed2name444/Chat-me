package com.cramdown.sorta.addons

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the kit and the engine join (S07, section 8), through the real engine on the emulator: the kit's
 * template gives what its own tests/expect.json says, as the harness's `expect` and iOS find too, and every
 * call of API v1 (addons/kit/api/v1.json) is there for an add-on to call.
 */
@RunWith(AndroidJUnit4::class)
class AddonJoinDeviceTest {
    private fun ready() = assertTrue("the engine is in this build", AddonNative.loaded)

    @Test fun theTemplateGivesWhatItsOwnTestsExpect() {
        ready()
        val manifest = AddonAssets.json("template/manifest.json")
        val expect = AddonAssets.json("template/tests/expect.json")
        assertEquals("api/sample-ideas.json", expect["ideas"]?.string)
        val suite = AddonCases("", AddonJson.obj("addon" to AddonJson.str("template"), "cases" to AddonJson.Arr(emptyList())), "api/sample-ideas.json")
        val grants = AddonJson.strings((manifest["permissions"]?.strings ?: emptyList()).filter { !it.startsWith("net") } +
            (manifest["hosts"]?.strings ?: emptyList()).map { "net:$it" })
        val commands = AddonJson.strings((manifest["contributes"]?.get("commands")?.array ?: emptyList()).mapNotNull { it["id"]?.string })
        val empty = AddonJson.Obj(emptyList())
        fun want(c: AddonJson) = "result " + AddonJson.obj("ok" to AddonJson.Bool(true), "value" to (c["reply"] ?: AddonJson.Null)).canonical
        fun got(c: AddonJson) = suite.block(c).text.lines().firstOrNull { it.startsWith("result ") } ?: "no result"
        var ran = 0
        for (c in expect["commands"]?.array ?: emptyList()) {
            val case = AddonJson.obj("name" to AddonJson.str("command"), "kind" to AddonJson.str("command"), "grants" to grants, "commands" to commands,
                "command" to (c["command"] ?: AddonJson.Null), "settings" to (c["settings"] ?: empty),
                "input" to AddonJson.obj("ideas" to (c["chosen"] ?: AddonJson.Arr(emptyList()))))
            assertEquals(c.canonical, want(c), got(case))
            ran++
        }
        for (c in expect["cards"]?.array ?: emptyList()) {
            val case = AddonJson.obj("name" to AddonJson.str("card"), "kind" to AddonJson.str("card"), "grants" to grants, "commands" to commands,
                "card" to (c["idea"] ?: AddonJson.Null), "settings" to (c["settings"] ?: empty))
            assertEquals(c.canonical, want(c), got(case))
            ran++
        }
        assertEquals("every case of the template's tests ran", 20, ran)
    }

    @Test fun everyCallOfApiV1IsAFunctionInTheEngine() {
        ready()
        val names = (AddonAssets.json("api/v1.json")["calls"] as AddonJson.Obj).pairs.map { it.first }
        assertEquals(20, names.size)
        val main = "const names = " + AddonJson.strings(names).canonical + ";\n" +
            "const missing = names.filter((n) => typeof n.split('.').reduce((o, k) => (o == null ? o : o[k]), globalThis) !== 'function');\n" +
            "console.log(names.length + ' calls, missing: ' + (missing.join(' ') || 'none'));\n"
        val case = AddonJson.obj("name" to AddonJson.str("api"), "kind" to AddonJson.str("load"),
            "grants" to AddonJson.strings(listOf("ideas.read", "ideas.write", "commands", "cards")), "commands" to AddonJson.strings(listOf("case")),
            "files" to AddonJson.obj("main.js" to AddonJson.obj("text" to AddonJson.str(main))))
        assertEquals("== api\nload ok\nlog 20 calls, missing: none\n", AddonCases("").block(case).text)
    }
}
