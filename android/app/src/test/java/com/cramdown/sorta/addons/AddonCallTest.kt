package com.cramdown.sorta.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07b A4, A5: one call's side of the API v1, on the conformance store and the recording stand-in network. */
class AddonCallTest {
    private val snap = AddonSnapshot.read(AddonTestFiles.json("store.json"))
    private val routes = AddonTestFiles.json("net/routes.json")
    private val net = listOf("commands", "net:api.example.com", "net:cdn.example.com")
    private fun s(v: String) = AddonJson.str(v)
    private fun o(vararg p: Pair<String, AddonJson>) = AddonJson.obj(*p)

    private fun call(grants: List<String>, phase: String = "command", netOn: Boolean = true, network: AddonNetwork? = AddonRoutesNetwork(routes),
        consent: ((String) -> Boolean)? = null): AddonCall {
        var made = 0
        return AddonCall(AddonSetup(snap, grants, listOf("run"), netOn, network = network, consent = consent, now = 1_793_523_600_000,
            makeId = { AddonTestFiles.id(++made) })).also { it.phase = phase }
    }

    private fun AddonCall.ask(name: String, vararg args: AddonJson) = handle(name, AddonJson.Arr(args.toList()).canonical)

    @Test fun whileItLoadsAnAddonOnlyRegistersAndLogs() {
        val c = call(listOf("ideas.read", "commands", "cards"), phase = "load")
        assertEquals("", c.ask("commands.on", s("run")))
        assertEquals("!api.args", c.ask("commands.on", s("undeclared")))
        assertEquals("", c.ask("cards.on"))
        assertEquals("!api.load", c.ask("ideas.list", o()))
        assertEquals("!api.load", c.ask("settings.get"))
        assertEquals("!api.unknown", c.ask("ideas.delete", s("x")))
        assertEquals("true", c.ask("has", s("ideas.read")))
        assertEquals("false", c.ask("has", s("ideas.write")))
    }

    @Test fun readsComeFromTheSnapshotInsideTheGrant() {
        val all = call(listOf("ideas.read"))
        val page = AddonJson.parse(all.ask("ideas.list", o("limit" to AddonJson.num(3))))
        assertEquals(3, page["ideas"]?.array?.size)
        assertEquals("3", page["cursor"]?.string)
        assertEquals("!api.args", all.ask("ideas.list", o("limit" to AddonJson.num(201))))
        assertEquals("null", all.ask("ideas.get", s(AddonTestFiles.id(999))))
        val folder = call(listOf(AddonPermissions.FOLDER_GRANT + AddonTestFiles.id(903)))
        val seen = AddonJson.parse(folder.ask("ideas.list", o()))["ideas"]?.array ?: emptyList()
        assertTrue(seen.isNotEmpty() && seen.all { it["folder"]?.string == AddonTestFiles.id(903) })
        assertEquals(1, AddonJson.parse(folder.ask("folders.list")).array?.size)
        assertEquals("!permission", call(listOf("commands", "cards")).ask("ideas.list", o()))
    }

    @Test fun aCardViewGetsOnlyIdeasInsideItsReadGrant() {
        val garden = AddonTestFiles.id(101)
        assertNull("cards alone renders nothing", call(listOf("cards"), phase = "card").cardInput(listOf(garden), false))
        assertEquals(garden, AddonJson.parse(call(listOf("ideas.read", "cards"), phase = "card").cardInput(listOf(garden), false)!!)["id"]?.string)
        val inFolder = call(listOf("cards", AddonPermissions.FOLDER_GRANT + AddonTestFiles.id(901)), phase = "cards")
        assertNull(inFolder.cardInput(listOf(garden), true))
        // a card view only reads
        assertEquals("!permission", call(listOf("ideas.read", "ideas.write", "cards"), phase = "card").ask("ideas.tag", s(garden), AddonJson.strings(listOf("x"))))
    }

    @Test fun pastOneHundredWritesTheCallFailsAndNothingIsApplied() {
        val c = call(listOf("ideas.read", "ideas.write", "commands"))
        val id = AddonTestFiles.id(101)
        repeat(100) { n -> assertEquals("", c.ask("ideas.tag", s(id), AddonJson.strings(listOf("t$n")))) }
        // captures are counted apart: 20 more are still inside the limits
        repeat(20) { n -> assertTrue(c.ask("ideas.capture", o("text" to s("Idea $n"))).startsWith("{\"id\":")) }
        assertEquals(120, c.outcome("""{"ok":true,"value":null}""").batch.size)
        assertEquals("!limit.writes", c.ask("ideas.tag", s(id), AddonJson.strings(listOf("one more"))))
        assertEquals("!limit.writes", c.ask("ideas.untag", s(id), AddonJson.strings(listOf("t1"))))
        val out = c.outcome("""{"ok":true,"value":null}""")
        assertFalse(out.ok)
        assertEquals("limit.writes", out.code)
        assertTrue(out.batch.isEmpty())
    }

    @Test fun pastTwentyCapturesTheCallFails() {
        val c = call(listOf("ideas.read", "ideas.write", "commands"))
        repeat(20) { n -> assertTrue(c.ask("ideas.capture", o("text" to s("Idea $n"))).startsWith("{")) }
        assertEquals("!limit.captures", c.ask("ideas.capture", o("text" to s("One more"))))
        assertEquals("limit.captures", c.outcome("""{"ok":true,"value":null}""").code)
    }

    @Test fun aCaptureTakesItsTitleFromItsFirstLineAndCanBeWrittenToAtOnce() {
        val c = call(listOf("ideas.read", "ideas.write", "commands"))
        val made = AddonJson.parse(c.ask("ideas.capture", o("text" to s("  A title \r\n and its words \n"))))["id"]?.string!!
        assertEquals("", c.ask("ideas.link", s(made), s(AddonTestFiles.id(101))))
        assertEquals("!api.notFound", c.ask("ideas.link", s(made), s(AddonTestFiles.id(999))))
        assertEquals("!api.args", c.ask("ideas.capture", o("text" to s("   "))))
        assertEquals(AddonOp.Capture(made, "A title", "and its words", 0.0, 0.0), c.batch[0])
    }

    @Test fun netIsHttpsOnlyAndGrantedHostsOnlyAndNamesTheRefusedHost() {
        val c = call(net)
        assertEquals("!net.https", c.ask("net.fetch", s("http://api.example.com/v1/items"), o()))
        assertEquals("!permission net:other.example.org", c.ask("net.fetch", s("https://other.example.org/x"), o()))
        assertEquals("!api.args", c.ask("net.fetch", s("https://user\u0040api.example.com/"), o()))
        assertEquals("!api.args", c.ask("net.fetch", s("https://api.example.com/v1/items"), o("headers" to o("Host" to s("x")))))
        val ok = AddonJson.parse(c.ask("net.fetch", s("https://api.example.com/v1/items"), o()))
        assertEquals(200L, ok["status"]?.integer)
        assertEquals("""{"items":[1,2]}""", ok["body"]?.string)
    }

    @Test fun redirectsStayInsideTheGrantsAndTheLimitsHold() {
        val c = call(net)
        assertEquals("from the cdn", AddonJson.parse(c.ask("net.fetch", s("https://api.example.com/v1/moved"), o()))["body"]?.string)
        assertEquals("!net.redirect", c.ask("net.fetch", s("https://api.example.com/v1/away"), o()))
        assertEquals("!net.redirect", c.ask("net.fetch", s("https://api.example.com/v1/plain"), o()))
        assertEquals("!net.redirect", c.ask("net.fetch", s("https://api.example.com/v1/loop"), o()))
        assertEquals("!net.size", c.ask("net.fetch", s("https://api.example.com/v1/big"), o()))
        assertEquals("!net.time", c.ask("net.fetch", s("https://api.example.com/v1/slow"), o()))
        // without the cdn's grant the same redirect is refused
        assertEquals("!net.redirect", call(listOf("net:api.example.com")).ask("net.fetch", s("https://api.example.com/v1/moved"), o()))
    }

    @Test fun cookiesAreNeverSentOrPassedBackAndTheLogKeepsNoSecret() {
        val network = AddonRoutesNetwork(routes)
        val c = call(net, network = network)
        val reply = c.ask("net.fetch", s("https://api.example.com/v1/items?token=q-token-7731#frag"),
            o("headers" to o("Cookie" to s("c-value-4419"), "X-Key" to s("h-value-5512"))))
        assertFalse("Set-Cookie is dropped", reply.contains("c-value-4419") || reply.contains("set-cookie"))
        val sent = network.sent.single()
        assertEquals(mapOf("x-key" to "h-value-5512"), sent.headers)
        assertEquals("https://api.example.com/v1/items?token=q-token-7731", sent.url)
        val logged = c.outcome("""{"ok":true,"value":null}""").activity.single()
        assertEquals("api.example.com" to "/v1/items", logged.host to logged.path)
        for (secret in listOf("q-token-7731", "h-value-5512", "c-value-4419")) assertFalse(secret, (logged.toString() + logged.line).contains(secret))
    }

    @Test fun askEachTimeHoldsEachRequestUntilItIsAnswered() {
        val asked = ArrayList<String>()
        val answers = ArrayList(listOf(true, false))
        val network = AddonRoutesNetwork(routes)
        val c = call(net, network = network, consent = { host -> asked.add(host); answers.removeAt(0) })
        assertTrue(c.ask("net.fetch", s("https://api.example.com/v1/items"), o()).startsWith("{"))
        assertEquals("!net.declined", c.ask("net.fetch", s("https://cdn.example.com/v1/items"), o()))
        assertEquals(listOf("api.example.com", "cdn.example.com"), asked)
        assertEquals("nothing is sent before the answer", 1, network.sent.size)
    }

    @Test fun askEachTimeAsksForEveryHostARequestReaches() {
        val asked = ArrayList<String>()
        val answers = ArrayList(listOf(true, false, true, true, true))
        val network = AddonRoutesNetwork(routes)
        val c = call(net, network = network, consent = { host -> asked.add(host); answers.removeAt(0) })
        // /v1/moved redirects to cdn.example.com: the person is asked about that host too, before anything is sent there
        assertEquals("!net.declined", c.ask("net.fetch", s("https://api.example.com/v1/moved"), o()))
        assertEquals(listOf("api.example.com", "cdn.example.com"), asked)
        assertEquals("only the first host was sent to", listOf("https://api.example.com/v1/moved"), network.sent.map { it.url })
        // a redirect on the same host is one question
        assertTrue(c.ask("net.fetch", s("https://api.example.com/v1/old"), o()).startsWith("{"))
        assertEquals(3, asked.size)
        // and each request asks again
        assertTrue(c.ask("net.fetch", s("https://api.example.com/v1/moved"), o()).startsWith("{"))
        assertEquals(listOf("api.example.com", "cdn.example.com", "api.example.com", "api.example.com", "cdn.example.com"), asked)
    }

    @Test fun withNetOffInTheBuildThereIsNoNet() {
        val network = AddonRoutesNetwork(routes)
        val c = call(net, netOn = false, network = network)
        assertEquals("false", c.ask("has", s("net")))
        assertEquals("false", c.ask("has", s("net.fetch")))
        assertEquals("false", c.ask("has", s("net:api.example.com")))
        assertEquals("!net.off", c.ask("net.fetch", s("https://api.example.com/v1/items"), o()))
        assertTrue(network.sent.isEmpty())
        assertEquals("true", call(net).ask("has", s("net")))
    }

    @Test fun storageKeepsJsonTextWithinItsLimit() {
        val saved = ArrayList<Int>()
        val storage = AddonStorage { saved.add(it.size) }
        val c = AddonCall(AddonSetup(snap, listOf("commands"), listOf("run"), true, storage = storage, now = 0)).also { it.phase = "command" }
        assertEquals("", c.ask("storage.get", s("k")))
        assertEquals("", c.ask("storage.set", s("k"), s("""{"n":1}""")))
        assertEquals("""{"n":1}""", c.ask("storage.get", s("k")))
        assertEquals("!limit.storage", c.ask("storage.set", s("big"), s("x".repeat(AddonCall.STORAGE_LIMIT))))
        assertEquals("!api.args", c.ask("storage.set", s(""), s("1")))
        assertEquals("", c.ask("storage.set", s("kept"), s("2")))
        assertEquals("", c.ask("storage.remove", s("k")))
        assertTrue("nothing is saved while the call runs", saved.isEmpty())
        storage.end(true)
        assertEquals("one save, when the call ended well", listOf(1), saved)
        assertEquals("", c.ask("storage.set", s("late"), s("3")))
        storage.end(false)
        assertEquals("a call that did not end well saves nothing", listOf(1), saved)
        assertEquals("", c.ask("storage.get", s("late")))
    }
}
