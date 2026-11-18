package com.cramdown.sorta.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

// HTTP straight from the phone to the provider (S08, section 4): HttpURLConnection only, with no HTTP library and no
// cache. Tests swap the transport for a stand-in that replays ai/fixtures/http.

data class AIHttpRequest(val method: String, val url: String, val headers: List<Pair<String, String>>, val body: ByteArray?) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
}

class AIHttpResponse(val status: Int, val body: ByteArray)

fun interface AITransport {
    /** Blocks until the answer comes; throws TRANSPORT when none does. */
    fun send(request: AIHttpRequest): AIHttpResponse
}

class AIUrlTransport : AITransport {
    override fun send(request: AIHttpRequest): AIHttpResponse {
        AIThread.requireBackground()
        val connection = try { URL(request.url).openConnection() as HttpURLConnection } catch (_: Exception) {
            throw AIError.of(AIError.Reason.TRANSPORT)
        }
        try {
            connection.useCaches = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method
            for ((name, value) in request.headers) connection.setRequestProperty(name, value)
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            return AIHttpResponse(status, body)
        } catch (_: IOException) {
            throw AIError.of(AIError.Reason.TRANSPORT)
        } finally {
            connection.disconnect()
        }
    }
}

// The account adapters (S08 A1). OpenRouter, OpenAI and any OpenAI-compatible service share the chat completions
// shape; Anthropic has its own. Each reads the credential from the vault at each call, so Disconnect stops it at once.

object AIAccounts {
    const val OPEN_ROUTER_BASE = "https://openrouter.ai/api/v1"
    const val OPEN_AI_BASE = "https://api.openai.com/v1"
    const val ANTHROPIC_BASE = "https://api.anthropic.com/v1"
    const val ANTHROPIC_VERSION = "2026-10-21"

    /** A compatible service's base address: https only, so a key never travels in the clear. Null for anything else. */
    fun compatibleBase(text: String): String? {
        var base = AIText.trim(text)
        while (base.endsWith("/")) base = base.dropLast(1)
        if (!base.lowercase(java.util.Locale.ROOT).startsWith("https://") || AIText.count(base) <= 8) return null
        val uri = try { URI(base) } catch (_: Exception) { return null }
        if (uri.host == null || uri.rawQuery != null || uri.rawFragment != null) return null
        return base
    }

    /** The plain kind of an error answer (ai/fixtures/http/errors.json). */
    fun kind(provider: AIProviderId, status: Int, body: ByteArray): AIErrorKind {
        val error = AIJSON.parse(body)?.get("error")
        val code = error?.get("code")?.string ?: ""
        val type = error?.get("type")?.string ?: ""
        val message = (error?.get("message")?.string ?: "").lowercase(java.util.Locale.ROOT)
        return when (provider) {
            AIProviderId.ANTHROPIC -> when {
                type == "billing_error" || status == 402 || message.contains("credit balance") -> AIErrorKind.OUT_OF_CREDIT
                status == 401 || status == 403 -> AIErrorKind.CREDENTIAL_REFUSED
                status == 404 || status == 400 || status == 413 -> AIErrorKind.MODEL_UNAVAILABLE
                status == 429 -> AIErrorKind.RATE_LIMITED
                else -> AIErrorKind.TRY_LATER
            }
            AIProviderId.OPENROUTER -> when (status) {
                401 -> AIErrorKind.CREDENTIAL_REFUSED
                402, 403 -> AIErrorKind.OUT_OF_CREDIT
                400, 404 -> AIErrorKind.MODEL_UNAVAILABLE
                429 -> AIErrorKind.RATE_LIMITED
                else -> AIErrorKind.TRY_LATER
            }
            else -> when {
                code == "insufficient_quota" || type == "insufficient_quota" || status == 402 -> AIErrorKind.OUT_OF_CREDIT
                status == 401 || status == 403 -> AIErrorKind.CREDENTIAL_REFUSED
                status == 400 || status == 404 -> AIErrorKind.MODEL_UNAVAILABLE
                status == 429 -> AIErrorKind.RATE_LIMITED
                else -> AIErrorKind.TRY_LATER
            }
        }
    }
}

/** An account that can prove its credential with one cheap read. */
interface AIAccount : AIProvider {
    fun checkRequest(key: String): AIHttpRequest
    fun check()
}

/** OpenRouter, OpenAI, or a compatible service. */
class AIChatCompletions(
    override val id: AIProviderId,
    val base: String,
    private val transport: AITransport,
    private val credential: () -> String,
) : AIAccount {
    override val sendsSchema: Boolean get() = id != AIProviderId.COMPATIBLE

    fun request(r: AIRequest, key: String): AIHttpRequest {
        val body = arrayListOf<Pair<String, AIJSON>>("model" to AIJSON.Str(r.model))
        // compatible services take max_tokens; OpenAI and OpenRouter want max_completion_tokens
        body.add((if (id == AIProviderId.COMPATIBLE) "max_tokens" else "max_completion_tokens") to AIJSON.Num(r.maxOutput.toDouble()))
        body.add("messages" to AIJSON.Arr(listOf(
            AIJSON.make("role" to AIJSON.Str("system"), "content" to AIJSON.Str(r.instructions)),
            AIJSON.make("role" to AIJSON.Str("user"), "content" to AIJSON.Str(r.input)),
        )))
        val schema = r.schema?.let { AIJSON.parse(it) }
        if (sendsSchema && r.schemaName != null && schema != null) {
            body.add("response_format" to AIJSON.make(
                "type" to AIJSON.Str("json_schema"),
                "json_schema" to AIJSON.make("name" to AIJSON.Str(r.schemaName), "strict" to AIJSON.Bool(true), "schema" to schema),
            ))
        }
        return AIHttpRequest("POST", "$base/chat/completions",
            listOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"), AIJSON.make(body).data)
    }

    /** The cheap read on connect: the model list, or OpenRouter's current key (its model list needs no key). */
    override fun checkRequest(key: String): AIHttpRequest =
        AIHttpRequest("GET", if (id == AIProviderId.OPENROUTER) "$base/key" else "$base/models", listOf("Authorization" to "Bearer $key"), null)

    fun parse(response: AIHttpResponse): AIResponse {
        if (response.status != 200) throw AIError.provider(AIAccounts.kind(id, response.status, response.body))
        val json = AIJSON.parse(response.body)
        val choice = json?.get("choices")?.array?.firstOrNull() ?: throw AIError.provider(AIErrorKind.TRY_LATER)
        val text = choice["message"]?.get("content")?.string ?: ""
        val usage = json["usage"]
        val cost = usage?.get("cost")?.number?.let { Math.round(it * 1e12) }
        return AIResponse(text, AIUsage(usage?.get("prompt_tokens")?.int ?: 0, usage?.get("completion_tokens")?.int ?: 0, cost))
    }

    override fun complete(request: AIRequest): AIResponse {
        AIThread.requireBackground()
        val key = credential()
        return parse(transport.send(request(request, key)))
    }

    override fun check() {
        AIThread.requireBackground()
        val response = transport.send(checkRequest(credential()))
        if (response.status != 200) throw AIError.provider(AIAccounts.kind(id, response.status, response.body))
    }
}

/** Anthropic's Messages API, by a key from Claude Console. */
class AIAnthropic(private val transport: AITransport, private val credential: () -> String) : AIAccount {
    override val id = AIProviderId.ANTHROPIC
    override val sendsSchema = true

    fun request(r: AIRequest, key: String): AIHttpRequest {
        val body = arrayListOf(
            "model" to AIJSON.Str(r.model),
            "max_tokens" to AIJSON.Num(r.maxOutput.toDouble()),
            "system" to AIJSON.Str(r.instructions),
            "messages" to AIJSON.Arr(listOf(AIJSON.make("role" to AIJSON.Str("user"), "content" to AIJSON.Str(r.input)))),
        )
        r.schema?.let { AIJSON.parse(it) }?.let { schema ->
            body.add("output_config" to AIJSON.make("format" to AIJSON.make("type" to AIJSON.Str("json_schema"), "schema" to schema)))
        }
        return AIHttpRequest("POST", AIAccounts.ANTHROPIC_BASE + "/messages",
            listOf("x-api-key" to key, "anthropic-version" to AIAccounts.ANTHROPIC_VERSION, "Content-Type" to "application/json"),
            AIJSON.make(body).data)
    }

    override fun checkRequest(key: String) = AIHttpRequest("GET", AIAccounts.ANTHROPIC_BASE + "/models?limit=1",
        listOf("x-api-key" to key, "anthropic-version" to AIAccounts.ANTHROPIC_VERSION), null)

    fun parse(response: AIHttpResponse): AIResponse {
        if (response.status != 200) throw AIError.provider(AIAccounts.kind(id, response.status, response.body))
        val json = AIJSON.parse(response.body)
        val content = json?.get("content")?.array ?: throw AIError.provider(AIErrorKind.TRY_LATER)
        val text = content.filter { it["type"]?.string == "text" }.mapNotNull { it["text"]?.string }.joinToString("")
        val usage = json["usage"]
        return AIResponse(text, AIUsage(usage?.get("input_tokens")?.int ?: 0, usage?.get("output_tokens")?.int ?: 0))
    }

    override fun complete(request: AIRequest): AIResponse {
        AIThread.requireBackground()
        val key = credential()
        return parse(transport.send(request(request, key)))
    }

    override fun check() {
        AIThread.requireBackground()
        val response = transport.send(checkRequest(credential()))
        if (response.status != 200) throw AIError.provider(AIAccounts.kind(id, response.status, response.body))
    }
}
