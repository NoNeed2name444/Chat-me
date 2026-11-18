package com.cramdown.sorta.ai

import android.content.SharedPreferences
import java.io.File
import java.time.Instant
import java.time.ZoneId

// The monthly cap (S08 A4): a true upper bound, enforced on the phone, as on iOS. Money is counted in picodollars
// (10^-12 USD). Before a call its worst case must fit in what is left of the month: every UTF-8 byte of the
// instructions, the input and any schema sent, plus 64 for framing, as input tokens, and the call's maximum output.
// After it, the provider's reported cost where it gives one (OpenRouter), else tokens times the table's price.

/** Where the cap, the spend, consent and settings are kept: SharedPreferences on the phone (never a credential). */
interface AIKeyValue {
    fun string(key: String): String?
    fun set(value: String?, key: String)
}

class AIMemoryKeyValue : AIKeyValue {
    private val values = HashMap<String, String>()
    @Synchronized override fun string(key: String) = values[key]
    @Synchronized override fun set(value: String?, key: String) { if (value == null) values.remove(key) else values[key] = value }
    val all: Map<String, String> @Synchronized get() = HashMap(values)
}

class AIPrefsKeyValue(private val prefs: SharedPreferences) : AIKeyValue {
    override fun string(key: String): String? = prefs.getString(key, null)
    override fun set(value: String?, key: String) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
    }
}

data class AIPrice(val input: Long, val output: Long, val longAbove: Int? = null, val longInput: Long = 0, val longOutput: Long = 0) {
    fun cost(input: Int, output: Int): Long {
        val long = longAbove?.let { input > it } ?: false
        return input.toLong() * (if (long) longInput else this.input) + output.toLong() * (if (long) longOutput else this.output)
    }
}

/** ai/tables/prices.json, in US dollars per million tokens. */
class AIPrices(json: ByteArray) {
    private val table = HashMap<AIProviderId, Map<String, AIPrice>>()

    init {
        val providers = AIJSON.parse(json)?.get("providers") as? AIJSON.Obj ?: throw AIError.of(AIError.Reason.UNKNOWN_PRICE)
        fun pico(v: AIJSON?): Long = Math.round((v?.number ?: 0.0) * 1_000_000)
        for ((name, entry) in providers.map) {
            val id = AIProviderId.of(name) ?: continue
            val models = entry["models"] as? AIJSON.Obj ?: continue
            table[id] = models.map.mapValues { (_, p) ->
                AIPrice(pico(p["input"]), pico(p["output"]), p["longAbove"]?.int, pico(p["longInput"]), pico(p["longOutput"]))
            }
        }
    }

    fun price(provider: AIProviderId, model: String): AIPrice? =
        if (isFree(provider, model)) AIPrice(0, 0) else table[provider]?.get(model)

    companion object {
        /** Free: the phone's own models and OpenRouter's ":free" ones. */
        fun isFree(provider: AIProviderId, model: String) = provider.onDevice || (provider == AIProviderId.OPENROUTER && model.endsWith(":free"))
    }
}

/** What a call holds against the cap until it is settled. */
data class AIReservation(val provider: AIProviderId, val model: String, val month: String, val held: Long, val price: AIPrice)

class AIMoney(
    private val prices: AIPrices,
    private val store: AIKeyValue,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
) {
    companion object {
        const val DOLLAR = 1_000_000_000_000L
        const val FLOOR = 1 * DOLLAR
        const val CEILING = 20 * DOLLAR
        const val STANDARD = 5 * DOLLAR
        /** Input tokens added for the provider's message framing. */
        const val FRAMING = 64

        /** The worst case: every byte sent as an input token, plus framing, and the whole maximum output. */
        fun worstCase(r: AIRequest, schemaSent: Boolean): Pair<Int, Int> {
            val schema = if (schemaSent) AIText.utf8(r.schema ?: "") else 0
            return Pair(AIText.utf8(r.instructions) + AIText.utf8(r.input) + schema + FRAMING, r.maxOutput)
        }
    }

    private val lock = Any()

    fun cap(provider: AIProviderId): Long = store.string("ai.cap." + provider.raw)?.toLongOrNull() ?: STANDARD

    /** Sets the cap, kept between $1 and $20: $20 is the ceiling for every account, the owner's own Claude key included. */
    fun setCap(pico: Long, provider: AIProviderId) = store.set(minOf(CEILING, maxOf(FLOOR, pico)).toString(), "ai.cap." + provider.raw)

    fun month(): String {
        val d = now().atZone(zone())
        return String.format(java.util.Locale.ROOT, "%04d-%02d", d.year, d.monthValue)
    }

    /** Spent this month (reserved calls included). */
    fun spent(provider: AIProviderId): Long = synchronized(lock) { spent(provider, month()) }

    private fun spent(provider: AIProviderId, month: String): Long {
        val parts = (store.string("ai.spent." + provider.raw) ?: return 0).split("=")
        if (parts.size != 2 || parts[0] != month) return 0
        return parts[1].toLongOrNull() ?: 0
    }

    private fun setSpent(provider: AIProviderId, month: String, value: Long) =
        store.set(month + "=" + maxOf(0, value), "ai.spent." + provider.raw)

    /** Holds the call's worst case, or refuses it: an unknown price, or a worst case that does not fit. */
    fun reserve(provider: AIProviderId, request: AIRequest, schemaSent: Boolean): AIReservation {
        val price = prices.price(provider, request.model) ?: throw AIError.of(AIError.Reason.UNKNOWN_PRICE)
        val (input, output) = worstCase(request, schemaSent)
        val held = price.cost(input, output)
        synchronized(lock) {
            val m = month()
            if (AIPrices.isFree(provider, request.model)) return AIReservation(provider, request.model, m, 0, price)
            val now = spent(provider, m)
            if (now + held > minOf(cap(provider), CEILING)) throw AIError.of(AIError.Reason.CAP_REACHED)
            setSpent(provider, m, now + held)
            return AIReservation(provider, request.model, m, held, price)
        }
    }

    /** The call's real cost in place of its worst case: the provider's reported cost, or tokens times the price. */
    fun settle(r: AIReservation, usage: AIUsage): Long {
        val actual = if (AIPrices.isFree(r.provider, r.model)) 0 else (usage.costPico ?: r.price.cost(usage.inputTokens, usage.outputTokens))
        synchronized(lock) {
            val m = month()
            if (r.month != m) setSpent(r.provider, m, spent(r.provider, m) + actual) // the month turned during the call
            else setSpent(r.provider, r.month, spent(r.provider, r.month) - r.held + actual)
        }
        return actual
    }

    /** A call the provider refused costs nothing. */
    fun release(r: AIReservation) = synchronized(lock) {
        if (r.month == month()) setSpent(r.provider, r.month, spent(r.provider, r.month) - r.held)
    }
}

// Consent, settings and the ledger (S08 A5). With the phone's own model, or no AI, nothing leaves the phone. With an
// account, a feature sends only when the account is connected, the feature is on, and the person agreed, once per
// feature and account, to what it sends. Every part is off until turned on.

enum class AIFeature(val raw: String) {
    SPLIT("split"), WHY("why"), ASK("ask"), SUMUP("sumup"), LINKS("links"), RESURFACE("resurface");

    val sends: Boolean get() = this == SPLIT || this == WHY || this == ASK || this == SUMUP
}

class AIConsent(private val store: AIKeyValue) {
    fun isOn(f: AIFeature) = store.string("ai.on." + f.raw) == "1"
    fun setOn(f: AIFeature, on: Boolean) = store.set(if (on) "1" else null, "ai.on." + f.raw)
    fun agreed(f: AIFeature, p: AIProviderId) = store.string("ai.agreed." + f.raw + "." + p.raw) == "1"
    fun agree(f: AIFeature, p: AIProviderId) = store.set("1", "ai.agreed." + f.raw + "." + p.raw)
    fun withdraw(f: AIFeature, p: AIProviderId) = store.set(null, "ai.agreed." + f.raw + "." + p.raw)

    /** Connected, on, agreed. The phone's own model needs only the feature on, because nothing leaves the phone. */
    fun allows(f: AIFeature, p: AIProviderId, connected: Boolean): Boolean {
        if (!connected || !isOn(f)) return false
        return p.onDevice || !f.sends || agreed(f, p)
    }
}

/** Which provider and model the person chose; nothing chosen is AI off. */
class AISettings(private val store: AIKeyValue) {
    var provider: AIProviderId?
        get() = AIProviderId.of(store.string("ai.provider"))
        set(value) = store.set(value?.raw, "ai.provider")

    fun model(p: AIProviderId): String? = store.string("ai.model." + p.raw)
    fun setModel(model: String?, p: AIProviderId) = store.set(model, "ai.model." + p.raw)

    var compatibleBase: String?
        get() = store.string("ai.compatible.base")
        set(value) = store.set(value?.let { AIAccounts.compatibleBase(it) }, "ai.compatible.base")
}

/** One send: when, to whom, for what, how many ideas and characters, and the estimated cost. No text, no credential. */
data class AILedgerEntry(val time: Double, val provider: AIProviderId, val feature: String, val ideas: Int, val characters: Int, val costPico: Long) {
    val json: AIJSON get() = AIJSON.make(
        "time" to AIJSON.Num(time), "provider" to AIJSON.Str(provider.raw), "feature" to AIJSON.Str(feature),
        "ideas" to AIJSON.Num(ideas.toDouble()), "characters" to AIJSON.Num(characters.toDouble()), "costPico" to AIJSON.Num(costPico.toDouble()),
    )

    companion object {
        fun of(j: AIJSON): AILedgerEntry? {
            val time = j["time"]?.number ?: return null
            val p = AIProviderId.of(j["provider"]?.string) ?: return null
            val feature = j["feature"]?.string ?: return null
            val ideas = j["ideas"]?.int ?: return null
            val characters = j["characters"]?.int ?: return null
            val cost = j["costPico"]?.number ?: return null
            return AILedgerEntry(time, p, feature, ideas, characters, cost.toLong())
        }
    }
}

/** The sends of the last 90 days, kept in a file on the phone. The person can read it and clear it (S08u). */
class AILedger(private val file: File, private val now: () -> Instant = { Instant.now() }) {
    companion object { const val DAYS = 90.0 }

    @Synchronized fun entries(): List<AILedgerEntry> = read()

    private fun read(): List<AILedgerEntry> {
        if (!file.exists()) return emptyList()
        val items = AIJSON.parse(file.readBytes())?.array ?: return emptyList()
        val since = now().toEpochMilli() / 1000.0 - DAYS * 86400
        return items.mapNotNull { AILedgerEntry.of(it) }.filter { it.time >= since }
    }

    @Synchronized fun record(entry: AILedgerEntry) {
        val all = read() + entry
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(AIJSON.Arr(all.map { it.json }).data)
        tmp.renameTo(file)
    }

    @Synchronized fun clear() { file.delete() }
}
