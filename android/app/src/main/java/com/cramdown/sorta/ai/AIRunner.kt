package com.cramdown.sorta.ai

import java.time.Instant

// Running an operation (S08 A4, A5, A6), as iOS's AIRunner: the chosen provider, consent's three conditions, the
// cap's check before the call and count after it, and the ledger. With no account connected and no model on the
// phone, every operation runs in its no-AI form or reports itself off, and nothing is sent.

sealed class AIOutcome<out V> {
    /** AI is off, the feature is off, or not agreed to: the no-AI form. */
    object Off : AIOutcome<Nothing>() { override fun toString() = "Off" }
    /** Nothing to do (a capture of one sentence; no candidates). */
    object NotNeeded : AIOutcome<Nothing>() { override fun toString() = "NotNeeded" }
    /** The call failed: the cap, an unknown price, or the provider's error. */
    data class Failed(val error: AIError) : AIOutcome<Nothing>()
    /** The answer, checked: OK carries the value, anything else changes nothing. */
    data class Answered<V>(val verdict: AIVerdict, val value: V?) : AIOutcome<V>()
}

class AIRunner(
    val vault: AIVault,
    store: AIKeyValue,
    prices: AIPrices,
    val ledger: AILedger,
    val transport: AITransport,
    val onDevice: AIProvider?,
    val files: AIFiles,
    private val now: () -> Instant = { Instant.now() },
) {
    val consent = AIConsent(store)
    val settings = AISettings(store)
    val money = AIMoney(prices, store, now = now)
    val suggested: Map<AIProviderId, String> = try {
        val map = AIJSON.parse(files.read("tables/models.json"))?.get("providers") as? AIJSON.Obj
        map?.map?.mapNotNull { (k, v) -> AIProviderId.of(k)?.let { id -> v["suggested"]?.string?.let { id to it } } }?.toMap() ?: emptyMap()
    } catch (_: Exception) { emptyMap() }

    /** The provider an operation would use, or null: AI off, nothing on the phone, or no credential for the account. */
    fun provider(): AIProvider? {
        val id = settings.provider ?: return null
        if (id.onDevice) return if (onDevice?.id == id) onDevice else null
        if ((try { vault.read(id) } catch (_: AIError) { null }) == null) return null
        val credential = { vault.read(id) ?: throw AIError.of(AIError.Reason.NOT_CONNECTED) }
        return when (id) {
            AIProviderId.ANTHROPIC -> AIAnthropic(transport, credential)
            AIProviderId.OPENROUTER -> AIChatCompletions(id, AIAccounts.OPEN_ROUTER_BASE, transport, credential)
            AIProviderId.OPENAI -> AIChatCompletions(id, AIAccounts.OPEN_AI_BASE, transport, credential)
            AIProviderId.COMPATIBLE -> settings.compatibleBase?.let { AIAccounts.compatibleBase(it) }
                ?.let { AIChatCompletions(id, it, transport, credential) }
            else -> null
        }
    }

    /** One call for [feature], if consent allows it and the cap has room. */
    fun call(feature: AIFeature, name: String, payload: AIPayload, maxOutput: Int): String {
        AIThread.requireBackground()
        val provider = provider() ?: throw AIError.of(AIError.Reason.NOT_CONNECTED)
        val id = provider.id
        if (!consent.allows(feature, id, connected = true)) throw AIError.of(AIError.Reason.NOT_ALLOWED)
        val prompt = AIPrompt.load(name, files)
        val model = if (id.onDevice) "on-device" else (settings.model(id) ?: suggested[id] ?: "")
        val request = AIRequest(model, prompt.instructions, payload.input,
            if (provider.sendsSchema) prompt.name else null, if (provider.sendsSchema) prompt.schema else null, maxOutput)
        if (id.onDevice) return provider.complete(request).text
        val held = money.reserve(id, request, provider.sendsSchema)
        val response = try {
            provider.complete(request)
        } catch (e: AIError) {
            if (e.reason == AIError.Reason.TRANSPORT) {
                // no answer came: the provider may have counted it, so the worst case stays
                ledger.record(AILedgerEntry(seconds(), id, feature.raw, payload.ideas, payload.characters, held.held))
                throw AIError.provider(AIErrorKind.TRY_LATER)
            }
            money.release(held)
            throw e
        } catch (e: Exception) {
            money.release(held)
            throw e
        }
        val cost = money.settle(held, response.usage)
        ledger.record(AILedgerEntry(seconds(), id, feature.raw, payload.ideas, payload.characters, cost))
        return response.text
    }

    private fun seconds() = now().toEpochMilli() / 1000.0

    private fun <V> run(feature: AIFeature, prompt: String, payload: AIPayload, maxOutput: Int, check: (String) -> Pair<AIVerdict, V?>): AIOutcome<V> =
        try {
            val (verdict, value) = check(call(feature, prompt, payload, maxOutput))
            AIOutcome.Answered(verdict, if (verdict == AIVerdict.OK) value else null)
        } catch (e: AIError) {
            if (e.reason == AIError.Reason.NOT_CONNECTED || e.reason == AIError.Reason.NOT_ALLOWED) AIOutcome.Off else AIOutcome.Failed(e)
        } catch (_: Exception) {
            AIOutcome.Failed(AIError.provider(AIErrorKind.TRY_LATER))
        }

    private val onDeviceChosen: Boolean get() = settings.provider?.onDevice ?: false

    /** Split a capture into ideas; Off means "Sort it" as today. */
    fun split(capture: String, folders: List<AISplit.Folder>): AIOutcome<List<AISplit.Piece>> {
        if (provider() == null || !consent.isOn(AIFeature.SPLIT)) return AIOutcome.Off
        if (!AISplit.needed(capture)) return AIOutcome.NotNeeded
        return run(AIFeature.SPLIT, "split", AISplit.payload(capture, folders), AISplit.MAX_OUTPUT) { AISplit.check(it, capture, folders) }
    }

    /** The reasons behind suggested links (the links themselves are AILinks, on the phone). */
    fun why(idea: AIIdea, candidates: List<AIIdea>): AIOutcome<Map<String, String>> {
        if (provider() == null || !consent.isOn(AIFeature.WHY)) return AIOutcome.Off
        if (candidates.isEmpty()) return AIOutcome.NotNeeded
        val (payload, sent) = AIWhy.payload(idea, candidates)
        return run(AIFeature.WHY, "why", payload, AIWhy.MAX_OUTPUT) { AIWhy.check(it, sent) }
    }

    /** Ask across ideas already found on the phone. */
    fun ask(question: String, found: List<AIIdea>): AIOutcome<AIAsk.Answer> {
        if (provider() == null || !consent.isOn(AIFeature.ASK)) return AIOutcome.Off
        if (found.isEmpty()) return AIOutcome.Answered(AIVerdict.NOTHING_FITS, null)
        val (payload, sent) = AIAsk.payload(question, found, onDeviceChosen)
        return run(AIFeature.ASK, "ask", payload, AIAsk.MAX_OUTPUT) { AIAsk.check(it, sent) }
    }

    /** Sum up a folder in parts; any part that fails changes nothing. */
    fun sumUp(folder: String, ideas: List<AIIdea>): AIOutcome<List<AIAsk.Answer>> {
        if (provider() == null || !consent.isOn(AIFeature.SUMUP)) return AIOutcome.Off
        val parts = AISumUp.parts(ideas, if (onDeviceChosen) AISumUp.ON_DEVICE_PART else AISumUp.ACCOUNT_PART)
        if (parts.isEmpty()) return AIOutcome.NotNeeded
        val answers = ArrayList<AIAsk.Answer>()
        for (part in parts) {
            when (val o = run(AIFeature.SUMUP, "sumup", AISumUp.payload(folder, part), AISumUp.MAX_OUTPUT) { AISumUp.check(it, part) }) {
                is AIOutcome.Answered -> {
                    val answer = o.value
                    if (o.verdict == AIVerdict.OK && answer != null) answers.add(answer) else return AIOutcome.Answered(o.verdict, null)
                }
                is AIOutcome.Failed -> return o
                AIOutcome.Off -> return AIOutcome.Off
                AIOutcome.NotNeeded -> return AIOutcome.NotNeeded
            }
        }
        return AIOutcome.Answered(AIVerdict.OK, answers)
    }
}
