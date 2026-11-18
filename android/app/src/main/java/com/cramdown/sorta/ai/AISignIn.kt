package com.cramdown.sorta.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// OpenRouter's sign-in (S08 A2): OAuth with PKCE, no app registration, as on iOS. The challenge is the verifier's
// SHA-256 in base64url (RFC 7636, S256). The sign-in URL carries callback_url=http://localhost:<port>/callback, where
// AILoopbackListener waits on both 127.0.0.1 and ::1; or, with no callback, OpenRouter shows a code the person pastes.

object AISHA256 {
    fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hex(bytes: ByteArray): String = digest(bytes).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
}

object AIPKCE {
    private val random = SecureRandom()

    fun base64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    /** 32 random bytes, 43 characters (RFC 7636, 4.1). */
    fun verifier(bytes: ByteArray = randomBytes(32)) = base64url(bytes)

    fun challenge(verifier: String) = base64url(AISHA256.digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun state() = base64url(randomBytes(16))

    /** Percent-encodes everything but RFC 3986's unreserved characters, the same way on both platforms. */
    fun encode(text: String): String {
        val out = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            if (b in 0x41..0x5A || b in 0x61..0x7A || b in 0x30..0x39 || b == 0x2D || b == 0x2E || b == 0x5F || b == 0x7E) out.append(b.toChar())
            else out.append(String.format(java.util.Locale.ROOT, "%%%02X", b))
        }
        return out.toString()
    }

    fun decode(text: String): String? = try { URLDecoder.decode(text, "UTF-8") } catch (_: IllegalArgumentException) { null }
}

class AIOpenRouterSignIn(val verifier: String = AIPKCE.verifier(), val state: String = AIPKCE.state()) {
    companion object {
        const val AUTH_URL = "https://openrouter.ai/auth"

        fun key(response: AIHttpResponse): String {
            if (response.status != 200) throw AIError.provider(when {
                response.status == 429 -> AIErrorKind.RATE_LIMITED
                response.status >= 500 -> AIErrorKind.TRY_LATER
                else -> AIErrorKind.CREDENTIAL_REFUSED
            })
            val key = AIJSON.parse(response.body)?.get("key")?.string
            if (key.isNullOrEmpty()) throw AIError.provider(AIErrorKind.TRY_LATER)
            return key
        }
    }

    /** The sign-in page: with a port, the callback comes to the listener; with none, OpenRouter shows a code to paste. */
    fun url(port: Int?): String {
        val query = ArrayList<Pair<String, String>>()
        if (port != null) query.add("callback_url" to "http://localhost:$port/callback")
        query.add("code_challenge" to AIPKCE.challenge(verifier))
        query.add("code_challenge_method" to "S256")
        if (port != null) query.add("state" to state)
        return AUTH_URL + "?" + query.joinToString("&") { it.first + "=" + AIPKCE.encode(it.second) }
    }

    /** The code in the callback's request target (/callback?code=…&state=…), once its state is checked. */
    fun code(target: String): String {
        val q = target.indexOf('?')
        if (q < 0 || target.substring(0, q) != "/callback") throw AIError.of(AIError.Reason.SIGN_IN_REFUSED)
        val fields = HashMap<String, String>()
        for (pair in target.substring(q + 1).split("&")) {
            if (pair.isEmpty()) continue
            val parts = pair.split("=", limit = 2)
            if (parts.size != 2) continue
            val k = AIPKCE.decode(parts[0]) ?: continue
            val v = AIPKCE.decode(parts[1]) ?: continue
            fields.putIfAbsent(k, v)
        }
        val code = fields["code"]
        if (fields["state"] != state || code.isNullOrEmpty()) throw AIError.of(AIError.Reason.SIGN_IN_REFUSED)
        return code
    }

    fun exchangeRequest(code: String): AIHttpRequest {
        val body = AIJSON.make("code" to AIJSON.Str(code), "code_verifier" to AIJSON.Str(verifier), "code_challenge_method" to AIJSON.Str("S256"))
        return AIHttpRequest("POST", AIAccounts.OPEN_ROUTER_BASE + "/auth/keys", listOf("Content-Type" to "application/json"), body.data)
    }
}

/** Opens the sign-in page: a Custom Tab on Android (AICustomTabsBrowser), a stand-in in tests. */
fun interface AIBrowser {
    fun open(url: String, callbackScheme: String)
}

/** The sign-in page in a Custom Tab, by intent alone (no browser library). The redirect to sorta:// brings Sorta back. */
class AICustomTabsBrowser(private val context: Context) : AIBrowser {
    override fun open(url: String, callbackScheme: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        // a Custom Tabs session extra with no session: the browser opens the page as a Custom Tab where it can
        intent.putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * Connecting an account: OpenRouter by sign-in or pasted code, the others by pasted key. Each checks the credential
 * with one cheap read before it is kept, and Disconnect deletes it at once.
 */
class AIConnect(private val vault: AIVault, private val transport: AITransport) {
    companion object {
        /** Where the browser goes once the listener has the code: Sorta itself. */
        const val RETURN_URL = "sorta://ai/connected"
    }

    /** The sign-in with a loopback callback. Blocks until the browser comes back or [timeoutMs] passes. */
    fun signInWithOpenRouter(browser: AIBrowser, timeoutMs: Long = 600_000) {
        AIThread.requireBackground()
        val listener = AILoopbackListener()
        try {
            val signIn = AIOpenRouterSignIn()
            browser.open(signIn.url(listener.port), "sorta")
            val deadline = System.currentTimeMillis() + timeoutMs
            var code: String? = null
            while (code == null) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) throw AIError.of(AIError.Reason.CANCELLED)
                val target = listener.next(left, RETURN_URL)
                code = try { signIn.code(target) } catch (_: AIError) { null }
            }
            finish(code, signIn)
        } finally {
            listener.close()
        }
    }

    /** The headless way: the page showed a code, and the person pasted it. */
    fun openRouter(pastedCode: String, signIn: AIOpenRouterSignIn) {
        AIThread.requireBackground()
        val code = AIText.trim(pastedCode)
        if (code.isEmpty()) throw AIError.of(AIError.Reason.SIGN_IN_REFUSED)
        finish(code, signIn)
    }

    private fun finish(code: String, signIn: AIOpenRouterSignIn) {
        val key = AIOpenRouterSignIn.key(transport.send(signIn.exchangeRequest(code)))
        check(AIProviderId.OPENROUTER, key)
        vault.save(key, AIProviderId.OPENROUTER)
    }

    /** A pasted key (Anthropic, OpenAI, or a compatible service at [base]). */
    fun connect(provider: AIProviderId, key: String, base: String? = null) {
        AIThread.requireBackground()
        val trimmed = AIText.trim(key)
        if (trimmed.isEmpty() || provider.onDevice) throw AIError.of(AIError.Reason.NOT_CONNECTED)
        check(provider, trimmed, base)
        vault.save(trimmed, provider)
    }

    fun disconnect(provider: AIProviderId) = vault.delete(provider)

    private fun check(provider: AIProviderId, key: String, base: String? = null) {
        val given = { key }
        when (provider) {
            AIProviderId.ANTHROPIC -> AIAnthropic(transport, given).check()
            AIProviderId.OPENROUTER -> AIChatCompletions(provider, AIAccounts.OPEN_ROUTER_BASE, transport, given).check()
            AIProviderId.OPENAI -> AIChatCompletions(provider, AIAccounts.OPEN_AI_BASE, transport, given).check()
            AIProviderId.COMPATIBLE -> {
                val b = base?.let { AIAccounts.compatibleBase(it) } ?: throw AIError.of(AIError.Reason.INSECURE_ADDRESS)
                AIChatCompletions(provider, b, transport, given).check()
            }
            else -> throw AIError.of(AIError.Reason.NOT_CONNECTED)
        }
    }
}

/**
 * The sign-in's loopback listener: the same random port on both 127.0.0.1 and ::1, so the callback to
 * http://localhost:<port>/callback arrives whichever address localhost resolves to. It answers the callback with a
 * redirect back to Sorta and an empty body: no page, so no string a person reads.
 */
class AILoopbackListener {
    val port: Int
    private val sockets: List<ServerSocket>

    init {
        val random = SecureRandom()
        var found: Pair<Int, List<ServerSocket>>? = null
        for (attempt in 0 until 40) {
            val p = 49152 + random.nextInt(65000 - 49152 + 1)
            val v4 = bind("127.0.0.1", p) ?: continue
            val v6 = bind("::1", p)
            if (v6 == null) { v4.close(); continue }
            found = p to listOf(v4, v6)
            break
        }
        val (p, s) = found ?: throw AIError.of(AIError.Reason.TRANSPORT)
        port = p
        sockets = s
    }

    private fun bind(host: String, port: Int): ServerSocket? = try {
        ServerSocket(port, 4, InetAddress.getByName(host)).apply { soTimeout = 50 }
    } catch (_: Exception) { null }

    fun close() { for (s in sockets) try { s.close() } catch (_: Exception) { } }

    /**
     * The next request's target (for example "/callback?code=…&state=…"), answered with a 302 to [redirect]; other
     * paths get a 404. Throws CANCELLED when nothing comes in [timeoutMs].
     */
    fun next(timeoutMs: Long, redirect: String): String {
        AIThread.requireBackground()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            for (server in sockets) {
                if (System.currentTimeMillis() >= deadline) throw AIError.of(AIError.Reason.CANCELLED)
                val client = try { server.accept() } catch (_: SocketTimeoutException) { continue } catch (_: Exception) {
                    throw AIError.of(AIError.Reason.CANCELLED)
                }
                client.use { c ->
                    val target = readTarget(c) ?: return@use
                    val path = target.substringBefore('?')
                    if (path == "/callback") {
                        write(c, "HTTP/1.1 302 Found\r\nLocation: $redirect\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        return target
                    }
                    write(c, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                }
            }
        }
    }

    private fun readTarget(c: Socket): String? = try {
        c.soTimeout = 5000
        val data = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        val input = c.getInputStream()
        while (data.size() < 16384) {
            val n = input.read(buffer)
            if (n <= 0) break
            data.write(buffer, 0, n)
            if (data.toString("UTF-8").contains("\r\n\r\n")) break
        }
        val parts = data.toString("UTF-8").split("\r\n").first().split(" ")
        if (parts.size == 3 && parts[0] == "GET") parts[1] else null
    } catch (_: Exception) { null }

    private fun write(c: Socket, text: String) {
        try { c.getOutputStream().apply { write(text.toByteArray(Charsets.UTF_8)); flush() } } catch (_: Exception) { }
    }
}
