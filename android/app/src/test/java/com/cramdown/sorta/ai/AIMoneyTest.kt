package com.cramdown.sorta.ai

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// S08 A4 on the JVM: the cap from $1 to $20 ($5 to start), the check before a call and the count after it, the
// monthly reset in the phone's time zone, unknown prices refused, the phone's own models and ":free" free, the $20
// ceiling, and the worst case against reported use in English, Arabic, CJK and emoji (ai/fixtures/bounds). iOS's
// AIMoneyTests is the same.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AIMoneyTest {
    private val dollar = AIMoney.DOLLAR
    private val prices = AIRepo.prices()
    private val or = AIProviderId.OPENROUTER
    private val flash = "deepseek/deepseek-v4.1-flash"
    private val plain = AIRequest(flash, "Sort.", "Paint the shed.", maxOutput = 300)
    private var clock = Instant.ofEpochSecond(1_763_200_000) // 15 Nov 2025, about 11:46 AM in Cairo
    private var zone = ZoneId.of("Africa/Cairo")
    private fun money(store: AIKeyValue) = AIMoney(prices, store, { zone }, { clock })
    private fun error(work: () -> Unit) = thrown(work) as? AIError

    @Test fun thePriceTable() {
        assertEquals(AIPrice(100_000, 500_000, 100_000, 500_000, 2_500_000), prices.price(AIProviderId.ANTHROPIC, "claude-haiku-5-5"))
        assertEquals(AIPrice(50_700, 202_800), prices.price(or, flash))
        assertEquals(AIPrice(0, 0), prices.price(or, "inclusionai/ling-3.2-flash:free"))
        assertEquals(AIPrice(0, 0), prices.price(AIProviderId.APPLE, "on-device"))
        assertEquals(AIPrice(0, 0), prices.price(AIProviderId.MLKIT, "on-device"))
        assertNull(prices.price(AIProviderId.OPENAI, "fake-model-1"))
        assertNull(prices.price(AIProviderId.COMPATIBLE, "fake-model-1"))
        assertNull("':free' counts only on OpenRouter", prices.price(AIProviderId.OPENAI, "gpt-6-luna:free"))
        val haiku = prices.price(AIProviderId.ANTHROPIC, "claude-haiku-5-5")!!
        assertEquals(100_000L * 100_000, haiku.cost(100_000, 0))
        assertEquals(100_001L * 500_000 + 10 * 2_500_000, haiku.cost(100_001, 10))
    }

    @Test fun theCapRunsFromOneToTwentyDollars() {
        val store = AIMemoryKeyValue()
        val m = money(store)
        assertEquals(5 * dollar, m.cap(AIProviderId.ANTHROPIC))
        m.setCap(12 * dollar, AIProviderId.ANTHROPIC)
        assertEquals(12 * dollar, m.cap(AIProviderId.ANTHROPIC))
        assertEquals(5 * dollar, m.cap(AIProviderId.OPENAI))
        m.setCap(500 * dollar, AIProviderId.ANTHROPIC)
        assertEquals("the owner's own Claude key included", 20 * dollar, m.cap(AIProviderId.ANTHROPIC))
        m.setCap(0, AIProviderId.OPENAI)
        assertEquals(1 * dollar, m.cap(AIProviderId.OPENAI))
        store.set((90 * dollar).toString(), "ai.cap.anthropic")
        val big = AIRequest("claude-opus-5-5", "", "", maxOutput = 1_000_000)
        assertEquals("a stored cap above $20 still stops at $20", AIError.of(AIError.Reason.CAP_REACHED),
            error { m.reserve(AIProviderId.ANTHROPIC, big, false) })
    }

    @Test fun theWorstCaseCoversRealUseInEveryScript() {
        val schema = AIRepo.text("prompts/split.schema.json")
        assertEquals(Pair(5 + 15 + 64, 300), AIMoney.worstCase(plain, false))
        val schemed = plain.copy(schemaName = "split", schema = schema)
        assertEquals(5 + 15 + 64 + schema.toByteArray().size, AIMoney.worstCase(schemed, true).first)
        assertEquals(5 + 15 + 64, AIMoney.worstCase(schemed, false).first)
        val cases = AIRepo.fixture("bounds/scripts.json")["cases"]!!.array!!
        assertTrue(cases.size >= 4)
        val price = prices.price(or, flash)!!
        for (c in cases) {
            val name = c["name"]!!.string
            val r = AIRequest(flash, c["instructions"]!!.string!!, c["input"]!!.string!!, maxOutput = c["maxOutput"]!!.int!!)
            val used = AIChatCompletions(or, AIAccounts.OPEN_ROUTER_BASE, StandInTransport()) { "" }
                .parse(AIHttpResponse(200, c["response"]!!["body"]!!.data)).usage
            val (input, output) = AIMoney.worstCase(r, false)
            assertEquals("$name: UTF-8 bytes", c["inputBytes"]!!.int, r.input.toByteArray(Charsets.UTF_8).size)
            assertTrue("$name: $input >= ${used.inputTokens}", input >= used.inputTokens && used.inputTokens > 0)
            assertTrue(name, output >= used.outputTokens)
            assertTrue(name, price.cost(input, output) >= price.cost(used.inputTokens, used.outputTokens))
        }
    }

    @Test fun beforeAndAfterACall() {
        val store = AIMemoryKeyValue()
        val m = money(store)
        m.setCap(1 * dollar, or)
        val worstOne = prices.price(or, flash)!!.cost(84, 300)
        val held = m.reserve(or, plain, false)
        assertEquals(worstOne, held.held)
        assertEquals(worstOne, m.spent(or))
        m.settle(held, AIUsage(30, 10, 9_000_000))
        assertEquals("the reported cost takes its place", 9_000_000L, m.spent(or))
        m.settle(m.reserve(or, plain, false), AIUsage(30, 10))
        val after = 9_000_000L + 30 * 50_700 + 10 * 202_800
        assertEquals("else tokens times the price", after, m.spent(or))
        m.release(m.reserve(or, plain, false))
        assertEquals("a refused call costs nothing", after, m.spent(or))

        store.set("2025-11=" + (1 * dollar - worstOne + 1), "ai.spent.openrouter")
        assertEquals("a worst case that does not fit is refused", AIError.of(AIError.Reason.CAP_REACHED), error { m.reserve(or, plain, false) })
        assertTrue("though its real use would have fitted", 9_000_000 < worstOne - 1)
        store.set("2025-11=" + (1 * dollar - worstOne), "ai.spent.openrouter")
        m.reserve(or, plain, false)

        val unknown = AIError.of(AIError.Reason.UNKNOWN_PRICE)
        assertEquals(unknown, error { m.reserve(AIProviderId.OPENAI, AIRequest("fake-model-1", "", "", maxOutput = 10), false) })
        assertEquals(unknown, error { m.reserve(AIProviderId.COMPATIBLE, AIRequest("fake-model-1", "", "", maxOutput = 10), false) })
        val free = m.reserve(or, AIRequest("inclusionai/ling-3.2-flash:free", "", "x".repeat(100_000), maxOutput = 100_000), false)
        assertEquals(0L, free.held)
        val before = m.spent(or)
        m.settle(free, AIUsage(5, 5, 0))
        assertEquals(before, m.spent(or))
        assertEquals(0L, m.reserve(AIProviderId.APPLE, AIRequest("on-device", "", "x", maxOutput = 10), true).held)
        assertEquals(0L, m.spent(AIProviderId.APPLE))
    }

    @Test fun theMonthIsThePhonesOwn() {
        val m = money(AIMemoryKeyValue())
        clock = Instant.parse("2026-11-30T21:30:00Z") // 11:30 PM on 30 Nov in Cairo
        assertEquals("2026-11", m.month())
        m.settle(m.reserve(or, plain, false), AIUsage(1, 1, 4_000_000))
        assertEquals(4_000_000L, m.spent(or))
        clock = Instant.parse("2026-11-30T22:30:00Z") // 12:30 AM on 1 Dec in Cairo
        assertEquals("2026-12", m.month())
        assertEquals(0L, m.spent(or))
        zone = ZoneId.of("UTC")
        assertEquals("2026-11", m.month())
        assertEquals(4_000_000L, m.spent(or))
    }

    @Test fun twentyDollarsIsATrueCeiling() {
        val m = money(AIMemoryKeyValue())
        m.setCap(20 * dollar, AIProviderId.ANTHROPIC)
        val opus = AIRequest("claude-opus-5-5", "Sum up.", "مرحبا ".repeat(2000), maxOutput = 4000)
        var calls = 0
        while (thrown { m.reserve(AIProviderId.ANTHROPIC, opus, false) } == null) { calls++; if (calls > 10_000) break }
        assertTrue(calls > 0 && m.spent(AIProviderId.ANTHROPIC) <= 20 * dollar)
        val next = prices.price(AIProviderId.ANTHROPIC, "claude-opus-5-5")!!.cost(AIMoney.worstCase(opus, false).first, 4000)
        assertTrue(m.spent(AIProviderId.ANTHROPIC) + next > 20 * dollar)
    }
}
