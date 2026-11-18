package com.cramdown.sorta.ai

import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// S08 A5 and A6 on the JVM: the verdicts on ai/fixtures/model (iOS's AIOperationTests reaches the same), each
// payload's least, consent's three conditions, zero requests with nothing connected, the runner and its ledger, the
// phone's own model, and the prompts' SHA-256. Made-up ideas, nothing medical.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AIOperationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun ideas(j: AIJSON) = j.array!!.map { AIIdea(it["id"]!!.string!!, it["title"]!!.string!!, it["text"]!!.string!!) }
    private val split = AIRepo.fixture("model/split.json")
    private val capture = split["capture"]!!.string!!
    private val folders = split["folders"]!!.array!!.map { f -> AISplit.Folder(f["id"]!!.string!!, f["name"]!!.string!!, f["words"]!!.array!!.map { it.string!! }) }
    private val why = AIRepo.fixture("model/why.json")
    private val whySent = ideas(why["candidates"]!!)
    private val ask = AIRepo.fixture("model/ask.json")
    private val askIdeas = ideas(ask["ideas"]!!)
    private val sumup = AIRepo.fixture("model/sumup.json")
    private val sumIdeas = ideas(sumup["ideas"]!!)
    private val goodSplit = split["cases"]!!.array!![0]["answer"]!!.string!!
    private val long = "مر😀a".repeat(400)

    private fun answer(c: AIJSON) = c["text"]?.let { AIAsk.Answer(it.string!!, c["cites"]!!.array!!.map { x -> x.string!! }) }

    @Test fun theModelAnswersGetTheFixturesVerdicts() {
        for (c in split["cases"]!!.array!!) {
            val (v, pieces) = AISplit.check(c["answer"]!!.string!!, capture, folders)
            val want = c["pieces"]?.array?.map { AISplit.Piece(it["text"]!!.string!!, it["title"]!!.string!!, it["folder"]!!.string) } ?: emptyList()
            assertEquals("split: ${c["name"]!!.string}", c["verdict"]!!.string to want, v.raw to pieces)
        }
        for (c in why["cases"]!!.array!!) {
            val (v, reasons) = AIWhy.check(c["answer"]!!.string!!, whySent)
            val want = (c["reasons"] as? AIJSON.Obj)?.map?.mapValues { it.value.string!! } ?: emptyMap()
            assertEquals("why: ${c["name"]!!.string}", c["verdict"]!!.string to want, v.raw to reasons)
        }
        for (c in ask["cases"]!!.array!!) {
            val (v, a) = AIAsk.check(c["answer"]!!.string!!, askIdeas)
            assertEquals("ask: ${c["name"]!!.string}", c["verdict"]!!.string to answer(c), v.raw to a)
        }
        val part = sumIdeas.mapIndexed { k, idea -> AINumbered(k + 1, idea) }
        for (c in sumup["cases"]!!.array!!) {
            val (v, a) = AISumUp.check(c["answer"]!!.string!!, part)
            assertEquals("sum up: ${c["name"]!!.string}", c["verdict"]!!.string to answer(c), v.raw to a)
        }
        assertEquals(listOf(2, 1, 3), AICitations.markers("a [2] b [1, 3] c [x] [] [4"))
        assertEquals(1, AIJSON.answer("```json\n{\"a\": 1}\n```")?.get("a")?.int)
    }

    @Test fun splitRunsOnlyOnMoreThanOneSentence() {
        assertEquals(1, AISplit.sentences("Paint the shed blue."))
        assertFalse(AISplit.needed("Paint the shed blue."))
        assertEquals(2, AISplit.sentences("Paint the shed. Plant beans."))
        assertEquals(2, AISplit.sentences("Paint the shed\nPlant beans"))
        assertEquals("a decimal point does not end one", 1, AISplit.sentences("Version 2.5 is out"))
        assertEquals(2, AISplit.sentences("اطلِ السقيفة. ازرع الفول."))
        assertEquals(0, AISplit.sentences("  \n "))
        assertEquals(3, AISplit.sentences(capture))
    }

    @Test fun eachPayloadSendsItsLeast() {
        val sp = AISplit.payload(capture, folders)
        val spJson = AIJSON.parse(sp.input) as AIJSON.Obj
        assertEquals(listOf("capture", "folders"), spJson.keys)
        assertEquals(capture, spJson["capture"]!!.string)
        assertTrue("each folder's name and words only", spJson["folders"]!!.array!!.all { (it as AIJSON.Obj).keys == listOf("name", "words") })
        assertEquals(0, sp.ideas)
        assertEquals(AIText.count(capture) + 6 + 6 + 5 + 5 + 5 + 5 + 7, sp.characters)

        val many = (1..8).map { AIIdea("c$it", "Candidate $it", long) }
        val (wp, wsent) = AIWhy.payload(AIIdea("n", "New idea", long), many)
        val w = AIJSON.parse(wp.input)!!
        assertEquals(500, AIText.count(w["idea"]!!["text"]!!.string!!))
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), wsent.map { it.id })
        assertTrue(w["candidates"]!!.array!!.all { AIText.count(it["text"]!!.string!!) == 200 && it["title"] != null && it["id"] == null })
        assertEquals(6, wp.ideas)
        assertEquals(8 + 500 + 5 * (11 + 200), wp.characters)

        val found = (1..15).map { AIIdea("i$it", "Idea $it", long) }
        val (ap, asent) = AIAsk.payload("What fits?", found, false)
        val a = AIJSON.parse(ap.input)!!
        assertEquals(12, asent.size)
        assertEquals(12, ap.ideas)
        assertTrue(a["ideas"]!!.array!!.all { AIText.count(it["text"]!!.string!!) == 1000 })
        assertEquals(10 + found.take(12).sumOf { AIText.count(it.title) } + 12 * 1000, ap.characters)
        assertEquals(6, AIAsk.payload("What fits?", found, true).second.size)

        val folderIdeas = (1..40).map { AIIdea("s$it", "Idea $it", AIText.prefix(long, 990)) }
        val account = AISumUp.parts(folderIdeas, AISumUp.ACCOUNT_PART)
        val phone = AISumUp.parts(folderIdeas, AISumUp.ON_DEVICE_PART)
        fun total(parts: List<List<AINumbered>>) = parts.flatten().sumOf { AIText.count(it.idea.title) + AIText.count(it.idea.text) }
        assertEquals(24_000, total(account))
        assertEquals(24_000, total(phone))
        assertEquals(1, account.size)
        assertTrue(phone.size > 1 && phone.all { total(listOf(it)) <= 2400 })
        assertEquals((1..phone.flatten().size).toList(), phone.flatten().map { it.n })
        assertEquals(account.flatten().map { it.n }, phone.flatten().map { it.n })
        val one = AISumUp.payload("Garden", phone[1])
        assertEquals(phone[1].size, one.ideas)
        assertEquals(total(listOf(phone[1])), one.characters)
    }

    @Test fun consentNeedsAllThreeConditions() {
        val consent = AIConsent(AIMemoryKeyValue())
        val or = AIProviderId.OPENROUTER
        assertTrue(AIFeature.entries.none { consent.isOn(it) })
        assertFalse(consent.allows(AIFeature.SPLIT, or, true))
        consent.setOn(AIFeature.SPLIT, true)
        assertFalse("on but not agreed", consent.allows(AIFeature.SPLIT, or, true))
        consent.agree(AIFeature.SPLIT, or)
        assertTrue(consent.allows(AIFeature.SPLIT, or, true))
        assertFalse("not connected", consent.allows(AIFeature.SPLIT, or, false))
        assertFalse(consent.allows(AIFeature.SPLIT, AIProviderId.ANTHROPIC, true))
        assertFalse(consent.allows(AIFeature.ASK, or, true))
        consent.withdraw(AIFeature.SPLIT, or)
        assertFalse(consent.allows(AIFeature.SPLIT, or, true))
        assertTrue(consent.allows(AIFeature.SPLIT, AIProviderId.MLKIT, true))
        assertFalse(consent.allows(AIFeature.ASK, AIProviderId.MLKIT, true))
        consent.setOn(AIFeature.LINKS, true)
        assertTrue(consent.allows(AIFeature.LINKS, or, true))
    }

    private class FakeOnDevice(override val id: AIProviderId) : AIProvider {
        override val sendsSchema = false
        var answer = ""
        val requests = ArrayList<AIRequest>()
        override fun complete(request: AIRequest): AIResponse {
            AIThread.requireBackground()
            requests.add(request)
            return AIResponse(answer, AIUsage(0, 0))
        }
    }

    private var clock = Instant.ofEpochSecond(1_763_200_000)

    private fun runner(vault: AIVault, store: AIKeyValue, net: AITransport, ledger: AILedger, onDevice: AIProvider? = null) =
        AIRunner(vault, store, AIRepo.prices(), ledger, net, onDevice, AIRepo.files) { clock }

    private fun everyOperation(r: AIRunner) = offMain {
        listOf(r.split(capture, folders), r.why(AIIdea("n", "Beans", "Beans up the fence."), whySent),
            r.ask("What did I plan?", askIdeas), r.sumUp("Garden", sumIdeas))
    }.map { it == AIOutcome.Off }

    @Test fun theRunnerSendsNothingWithoutAllThreeAndCountsWhatItSends() {
        val ledgerFile = File(temp.root, "ai-ledger.json")
        val ledger = AILedger(ledgerFile) { clock }
        val store = AIMemoryKeyValue()
        val vault = AIMemoryVault()
        val net = StandInTransport()
        val r = runner(vault, store, net, ledger)
        val all = listOf(true, true, true, true)
        assertEquals("no AI", all, everyOperation(r))
        AISettings(store).provider = AIProviderId.OPENROUTER
        assertEquals("no account", all, everyOperation(r))
        vault.save(AIRepo.KEY, AIProviderId.OPENROUTER)
        assertEquals("features off", all, everyOperation(r))
        val gate = AIConsent(store)
        val sending = listOf(AIFeature.SPLIT, AIFeature.WHY, AIFeature.ASK, AIFeature.SUMUP)
        sending.forEach { gate.setOn(it, true) }
        assertEquals("not agreed", all, everyOperation(r))
        assertEquals("zero requests", 0, net.requests.size)
        assertTrue(ledger.entries().isEmpty())

        sending.forEach { gate.agree(it, AIProviderId.OPENROUTER) }
        AISettings(store).setModel("deepseek/deepseek-v4.1-flash", AIProviderId.OPENROUTER)
        assertEquals(AIOutcome.NotNeeded, offMain { r.split("Paint the shed blue.", folders) })
        assertEquals(0, net.requests.size)

        net.handler = { aiChat(goodSplit) }
        val done = offMain { r.split(capture, folders) } as AIOutcome.Answered
        assertEquals(AIVerdict.OK, done.verdict)
        assertEquals(3, done.value!!.size)
        assertEquals(listOf("https://openrouter.ai/api/v1/chat/completions"), net.requests.map { it.url })
        val sent = AIJSON.parse(net.requests[0].body!!)!!
        val sp = AISplit.payload(capture, folders)
        assertEquals("exactly the split payload", sp.input, sent["messages"]!!.array!![1]["content"]!!.string)
        assertEquals("split", sent["response_format"]!!["json_schema"]!!["name"]!!.string)
        assertEquals(listOf(AILedgerEntry(clock.epochSecond.toDouble(), AIProviderId.OPENROUTER, "split", 0, sp.characters, 21_445_500)), ledger.entries())
        assertEquals(21_445_500L, r.money.spent(AIProviderId.OPENROUTER))

        net.handler = { aiChat(why["cases"]!!.array!![0]["answer"]!!.string!!) }
        val w = offMain { r.why(AIIdea("n", why["idea"]!!["title"]!!.string!!, why["idea"]!!["text"]!!.string!!), whySent) }
        assertEquals(AIOutcome.Answered(AIVerdict.OK, mapOf("c1" to "Both are about beans on the fence.", "c3" to "Both are about the fence.")), w)
        net.handler = { aiChat(ask["cases"]!!.array!![1]["answer"]!!.string!!) }
        val a = offMain { r.ask(ask["question"]!!.string!!, askIdeas) } as AIOutcome.Answered
        assertEquals(listOf("i3", "i1"), a.value!!.cites)
        net.handler = { aiChat(sumup["cases"]!!.array!![0]["answer"]!!.string!!) }
        val s = offMain { r.sumUp("Garden", sumIdeas) } as AIOutcome.Answered
        assertEquals(1, s.value!!.size)
        assertEquals(listOf("split", "why", "ask", "sumup"), ledger.entries().map { it.feature })
        assertEquals(listOf(0, 1 + whySent.size, askIdeas.size, sumIdeas.size), ledger.entries().map { it.ideas })
        assertEquals(4, net.requests.size)

        net.handler = { aiChat(split["cases"]!!.array!![5]["answer"]!!.string!!) }
        assertEquals(AIOutcome.Answered<List<AISplit.Piece>>(AIVerdict.NOT_A_SLICE, null), offMain { r.split(capture, folders) })
        assertEquals("still recorded", 5, ledger.entries().size)

        val before = r.money.spent(AIProviderId.OPENROUTER)
        net.handler = { AIHttpResponse(429, "{\"error\":{\"code\":429,\"message\":\"Rate limit\"}}".toByteArray()) }
        assertEquals(AIOutcome.Failed(AIError.provider(AIErrorKind.RATE_LIMITED)), offMain { r.ask("What fits?", askIdeas) })
        assertEquals("released", before, r.money.spent(AIProviderId.OPENROUTER))
        assertEquals(5, ledger.entries().size)

        net.handler = { throw AIError.of(AIError.Reason.TRANSPORT) }
        assertEquals(AIOutcome.Failed(AIError.provider(AIErrorKind.TRY_LATER)), offMain { r.ask("What fits?", askIdeas) })
        val lost = ledger.entries().last()
        assertTrue(lost.costPico > 0)
        assertEquals("the worst case kept", before + lost.costPico, r.money.spent(AIProviderId.OPENROUTER))

        val sentSoFar = net.requests.size
        store.set(r.money.month() + "=" + 5 * AIMoney.DOLLAR, "ai.spent.openrouter")
        assertEquals(AIOutcome.Failed(AIError.of(AIError.Reason.CAP_REACHED)), offMain { r.ask("What fits?", askIdeas) })
        assertEquals("the call not made", sentSoFar, net.requests.size)

        val text = ledgerFile.readText()
        assertFalse(text.contains("Paint") || text.contains("fence") || text.contains("What fits"))
        clock = clock.plusSeconds(89 * 86400L)
        assertEquals(6, ledger.entries().size)
        clock = clock.plusSeconds(2 * 86400L)
        assertTrue("past 90 days, gone", ledger.entries().isEmpty())
        ledger.record(AILedgerEntry(clock.epochSecond.toDouble(), AIProviderId.ANTHROPIC, "ask", 2, 40, 1))
        assertEquals(1, ledger.entries().size)
        ledger.clear()
        assertTrue(ledger.entries().isEmpty() && !ledgerFile.exists())
    }

    @Test fun thePhonesOwnModelSendsNothing() {
        val model = FakeOnDevice(AIProviderId.MLKIT)
        val store = AIMemoryKeyValue()
        val net = StandInTransport()
        val ledgerFile = File(temp.root, "phone-ledger.json")
        val ledger = AILedger(ledgerFile) { clock }
        val r = runner(AIMemoryVault(), store, net, ledger, model)
        AISettings(store).provider = AIProviderId.MLKIT
        assertEquals(AIOutcome.Off, offMain { r.split(capture, folders) })
        assertTrue(model.requests.isEmpty())
        listOf(AIFeature.SPLIT, AIFeature.ASK).forEach { AIConsent(store).setOn(it, true) }
        model.answer = goodSplit
        assertEquals(AIVerdict.OK, (offMain { r.split(capture, folders) } as AIOutcome.Answered).verdict)
        assertEquals("on is enough", 1, model.requests.size)
        model.answer = ask["cases"]!!.array!![0]["answer"]!!.string!!
        offMain { r.ask("What fits?", (1..15).map { AIIdea("i$it", "Idea $it", long) }) }
        assertEquals("6 ideas", 6, AIJSON.parse(model.requests[1].input)!!["ideas"]!!.array!!.size)
        assertEquals("no schema: the Prompt API takes none", null, model.requests[1].schema)
        assertEquals("on-device", model.requests[1].model)
        assertTrue(net.requests.isEmpty() && ledger.entries().isEmpty() && !ledgerFile.exists())
        assertEquals(0L, r.money.spent(AIProviderId.MLKIT))
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { r.call(AIFeature.SPLIT, "split", AISplit.payload(capture, folders), 10) })
    }

    @Test fun thePromptsAreTheSameBytesInBothApps() {
        val lines = AIRepo.text("prompts/SHA256SUMS").split("\n").filter { it.isNotBlank() }.map { it.split(" ").filter { p -> p.isNotEmpty() } }
        assertEquals(8, lines.size)
        assertEquals(AIPrompt.names.flatMap { listOf("$it.txt", "$it.schema.json") }.toSet(), lines.map { it[1] }.toSet())
        for (l in lines) assertEquals(l[1], l[0], AISHA256.hex(File(AIRepo.ai, "prompts/" + l[1]).readBytes()))
        for (name in AIPrompt.names) {
            val p = AIPrompt.load(name, AIRepo.files)
            assertNotNull(AIJSON.parse(p.schema))
            assertTrue(p.instructions.isNotEmpty())
        }
    }
}

/** An OpenRouter chat answer with its usage and reported cost. */
fun aiChat(content: String, cost: Double? = 0.0000214455): AIHttpResponse {
    val usage = arrayListOf<Pair<String, AIJSON>>("prompt_tokens" to AIJSON.Num(231.0), "completion_tokens" to AIJSON.Num(48.0))
    if (cost != null) usage.add("cost" to AIJSON.Num(cost))
    val body = AIJSON.make(
        "choices" to AIJSON.Arr(listOf(AIJSON.make("message" to AIJSON.make("role" to AIJSON.Str("assistant"), "content" to AIJSON.Str(content))))),
        "usage" to AIJSON.make(usage),
    )
    return AIHttpResponse(200, body.data)
}
