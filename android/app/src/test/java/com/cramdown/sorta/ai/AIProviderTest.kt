package com.cramdown.sorta.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// S08 A1 and A2 on the JVM: each adapter's request against ai/fixtures/http and its parse, the error kinds, the main
// thread refused, PKCE's RFC 7636 vector, the state check, the code exchange, and connecting by pasted key or code.
// iOS's AIProviderTests reads the same fixtures. No network, no real key.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AIProviderTest {
    private val key = AIRepo.KEY
    private val given = { key }
    private val splitSchema = AIRepo.text("prompts/split.schema.json")

    private fun adapter(provider: String, base: String?, t: AITransport): AIAccount = when (provider) {
        "anthropic" -> AIAnthropic(t, given)
        "openrouter" -> AIChatCompletions(AIProviderId.OPENROUTER, AIAccounts.OPEN_ROUTER_BASE, t, given)
        "openai" -> AIChatCompletions(AIProviderId.OPENAI, AIAccounts.OPEN_AI_BASE, t, given)
        else -> AIChatCompletions(AIProviderId.COMPATIBLE, AIAccounts.compatibleBase(base!!)!!, t, given)
    }

    @Test fun eachAdapterSendsTheFixturesRequestAndReadsItsAnswer() {
        for (name in listOf("openrouter-chat", "openrouter-chat-free", "openai-chat", "compatible-chat", "anthropic-messages")) {
            val fx = AIRepo.fixture("http/$name.json")
            val t = StandInTransport.replaying(fx)
            val provider = adapter(fx["provider"]!!.string!!, fx["base"]?.string, t)
            val answer = offMain { provider.complete(request(fx, splitSchema)) }
            assertEquals(name, 1, t.requests.size)
            assertNull(name, mismatch(t.requests[0], fx))
            val expect = fx["expect"]!!
            assertEquals(name, expect["text"]!!.string, answer.text)
            assertEquals(name, AIUsage(expect["inputTokens"]!!.int!!, expect["outputTokens"]!!.int!!, expect["costPico"]!!.number?.toLong()), answer.usage)
        }
    }

    @Test fun theCheapReadOnConnectIsTheFixtures() {
        for (name in listOf("openrouter-key", "anthropic-models", "openai-models", "compatible-models")) {
            val fx = AIRepo.fixture("http/$name.json")
            val provider = when (fx["provider"]!!.string) {
                "compatible" -> AIChatCompletions(AIProviderId.COMPATIBLE, fx["base"]!!.string!!, StandInTransport(), given)
                else -> adapter(fx["provider"]!!.string!!, null, StandInTransport())
            }
            assertNull(name, mismatch(provider.checkRequest(key), fx))
        }
    }

    @Test fun errorsMapToAFewPlainKinds() {
        val errors = AIRepo.fixture("http/errors.json")
        val cases = errors["cases"] as AIJSON.Obj
        for ((name, list) in cases.map) {
            val id = AIProviderId.of(name)!!
            for (c in list.array!!) {
                val status = c["status"]!!.int!!
                val kind = AIAccounts.kind(id, status, c["body"]!!.data)
                assertEquals("$name $status ${c["body"]!!.text}", c["kind"]!!.string, kind.raw)
                val t = StandInTransport { AIHttpResponse(status, c["body"]!!.data) }
                val provider: AIProvider = if (id == AIProviderId.ANTHROPIC) AIAnthropic(t, given)
                else AIChatCompletions(id, "https://compatible.example/v1", t, given)
                val e = thrown { offMain { provider.complete(AIRequest("m", "i", "x", maxOutput = 5)) } }
                assertEquals("$name $status is thrown as that kind", AIError.provider(kind), e)
            }
        }
        assertEquals(10, errors["cases"]!!["anthropic"]!!.array!!.size)
        assertEquals(10, errors["cases"]!!["openrouter"]!!.array!!.size)
        assertEquals(7, errors["cases"]!!["openai"]!!.array!!.size)
        assertEquals(5, errors["cases"]!!["compatible"]!!.array!!.size)

        val gone = StandInTransport { throw AIError.of(AIError.Reason.TRANSPORT) }
        val e = thrown { offMain { AIChatCompletions(AIProviderId.OPENAI, AIAccounts.OPEN_AI_BASE, gone, given).complete(AIRequest("m", "i", "x", maxOutput = 5)) } }
        assertEquals("no answer at all is a transport failure", AIError.of(AIError.Reason.TRANSPORT), e)
    }

    @Test fun theMainThreadIsRefusedAndNothingIsSent() {
        val t = StandInTransport.replaying(AIRepo.fixture("http/openai-chat.json"))
        val p = AIChatCompletions(AIProviderId.OPENAI, AIAccounts.OPEN_AI_BASE, t, given)
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { p.complete(AIRequest("m", "i", "x", maxOutput = 5)) })
        assertTrue(t.requests.isEmpty())
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { AIUrlTransport().send(AIHttpRequest("GET", "https://example.invalid", emptyList(), null)) })
    }

    /** A one-answer HTTP server on this machine: what it was sent, and the port. No internet. */
    private fun serve(status: String, body: ByteArray, seen: MutableList<String>, extra: String = ""): java.net.ServerSocket {
        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            try {
                server.accept().use { c ->
                    val input = c.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                    val length = Regex("(?i)content-length: (\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    val sent = ByteArray(length)
                    var at = 0
                    while (at < length) at += input.read(sent, at, length - at)
                    seen.add(head.toString())
                    seen.add(String(sent, Charsets.UTF_8))
                    c.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\n${extra}Content-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            } catch (_: Exception) { }
        }.start()
        return server
    }

    @Test fun theRealTransportCarriesARequestAndReadsAnswersAndErrors() {
        val fx = AIRepo.fixture("http/openrouter-chat.json")
        val seen = ArrayList<String>()
        val provider = { port: Int -> AIChatCompletions(AIProviderId.OPENROUTER, "http://127.0.0.1:$port", AIUrlTransport(), given) }
        serve("200 OK", fx["response"]!!["body"]!!.data, seen).use { server ->
            val answer = offMain { provider(server.localPort).complete(request(fx, splitSchema)) }
            assertEquals(fx["expect"]!!["text"]!!.string, answer.text)
            assertEquals(fx["expect"]!!["costPico"]!!.number!!.toLong(), answer.usage.costPico)
        }
        assertTrue(seen[0], seen[0].startsWith("POST /chat/completions HTTP/1.1\r\n"))
        assertTrue(seen[0].contains("Authorization: Bearer $key\r\n") && seen[0].contains("Content-Type: application/json\r\n"))
        assertEquals("the body, as JSON", fx["request"]!!["body"], AIJSON.parse(seen[1]))

        val refused = "{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}".toByteArray()
        serve("401 Unauthorized", refused, ArrayList()).use { server ->
            assertEquals("an error answer's body is read", AIError.provider(AIErrorKind.CREDENTIAL_REFUSED),
                thrown { offMain { provider(server.localPort).complete(AIRequest("m", "i", "x", maxOutput = 5)) } })
        }
        val closed = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).let { s -> s.localPort.also { s.close() } }
        assertEquals("nothing listening is a transport failure", AIError.of(AIError.Reason.TRANSPORT),
            thrown { offMain { provider(closed).complete(AIRequest("m", "i", "x", maxOutput = 5)) } })

        // a redirect is never followed: the 3xx answer comes back as it is, and the second address hears nothing
        val first = ArrayList<String>()
        val second = java.util.Collections.synchronizedList(ArrayList<String>())
        serve("200 OK", "{}".toByteArray(), second).use { elsewhere ->
            serve("302 Found", ByteArray(0), first, "Location: http://127.0.0.1:${elsewhere.localPort}/moved\r\n").use { server ->
                val moved = offMain {
                    AIUrlTransport().send(AIHttpRequest("POST", "http://127.0.0.1:${server.localPort}/chat/completions",
                        listOf("Authorization" to "Bearer $key"), "{}".toByteArray()))
                }
                assertEquals(302, moved.status)
            }
            assertTrue("the first address was asked, with the key", first[0].startsWith("POST /chat/completions HTTP/1.1\r\n") && first[0].contains("Bearer $key"))
            Thread.sleep(1500)
            assertTrue("no second request, so the key goes nowhere else: $second", second.isEmpty())
        }
        serve("307 Temporary Redirect", ByteArray(0), ArrayList(), "Location: https://elsewhere.invalid/\r\n").use { server ->
            assertEquals("to an adapter it is one more reason to try later", AIError.provider(AIErrorKind.TRY_LATER),
                thrown { offMain { provider(server.localPort).complete(AIRequest("m", "i", "x", maxOutput = 5)) } })
        }
    }

    @Test fun compatibleServicesAreHttpsOnly() {
        assertEquals("https://llm.example/v1", AIAccounts.compatibleBase("https://llm.example/v1/"))
        assertNull(AIAccounts.compatibleBase("http://192.168.1.20:11434/v1"))
        assertNull(AIAccounts.compatibleBase("llm.example"))
        assertNull(AIAccounts.compatibleBase("https://"))
    }

    @Test fun pkceFollowsRfc7636() {
        val rfc = intArrayOf(116, 24, 223, 180, 151, 153, 224, 37, 79, 250, 96, 125, 216, 173, 187, 186, 22, 212, 37, 77,
            105, 214, 191, 240, 91, 88, 5, 88, 83, 132, 141, 121).map { it.toByte() }.toByteArray()
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", AIPKCE.verifier(rfc))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", AIPKCE.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val v = AIPKCE.verifier()
        assertEquals(43, v.length)
        assertTrue(v.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(v, AIPKCE.verifier())
        assertNotEquals(AIPKCE.state(), AIPKCE.state())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AISHA256.hex("abc".toByteArray()))
    }

    @Test fun theSignInUrlTheStateAndTheExchange() {
        val signIn = AIOpenRouterSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "fake-state-1")
        assertEquals("https://openrouter.ai/auth?callback_url=http%3A%2F%2Flocalhost%3A51234%2Fcallback" +
            "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&state=fake-state-1", signIn.url(51234))
        assertEquals("https://openrouter.ai/auth?code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256", signIn.url(null))
        assertEquals("fake-code-for-tests-1", signIn.code("/callback?code=fake-code-for-tests-1&state=fake-state-1"))
        val refused = AIError.of(AIError.Reason.SIGN_IN_REFUSED)
        for (target in listOf("/callback?code=c&state=other", "/callback?code=c", "/callback?state=fake-state-1", "/elsewhere?code=c&state=fake-state-1")) {
            assertEquals(target, refused, thrown { signIn.code(target) })
        }

        val exchange = AIRepo.fixture("http/openrouter-exchange.json")
        assertNull(mismatch(signIn.exchangeRequest(exchange["code"]!!.string!!), exchange))
        assertEquals("fake-key-for-tests-2", AIOpenRouterSignIn.key(AIHttpResponse(200, exchange["response"]!!["body"]!!.data)))
        val no = exchange["refused"]!!
        assertEquals(AIError.provider(AIErrorKind.CREDENTIAL_REFUSED), thrown { AIOpenRouterSignIn.key(AIHttpResponse(no["status"]!!.int!!, no["body"]!!.data)) })
    }

    @Test fun connectingThenDisconnecting() {
        val exchange = AIRepo.fixture("http/openrouter-exchange.json")
        val signIn = AIOpenRouterSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "fake-state-1")
        val vault = AIMemoryVault()
        val t = StandInTransport { r ->
            when {
                r.url.endsWith("/auth/keys") -> AIHttpResponse(200, exchange["response"]!!["body"]!!.data)
                r.url.endsWith("/key") -> AIHttpResponse(if (r.header("Authorization") == "Bearer fake-key-for-tests-2") 200 else 401, "{}".toByteArray())
                else -> AIHttpResponse(404, ByteArray(0))
            }
        }
        val connect = AIConnect(vault, t)
        offMain { connect.openRouter(" fake-code-for-tests-1\n", signIn) }
        assertEquals("fake-key-for-tests-2", vault.read(AIProviderId.OPENROUTER))
        assertEquals(listOf("https://openrouter.ai/api/v1/auth/keys", "https://openrouter.ai/api/v1/key"), t.requests.map { it.url })
        assertEquals("never on the main thread", AIError.of(AIError.Reason.MAIN_THREAD), thrown { connect.disconnect(AIProviderId.OPENROUTER) })
        assertEquals("and refused, it changes nothing", "fake-key-for-tests-2", vault.read(AIProviderId.OPENROUTER))
        offMain { connect.disconnect(AIProviderId.OPENROUTER) }
        assertNull("Disconnect deletes it at once", vault.read(AIProviderId.OPENROUTER))

        val refusing = StandInTransport { AIHttpResponse(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"x\"}}".toByteArray()) }
        assertEquals(AIError.provider(AIErrorKind.CREDENTIAL_REFUSED), thrown { offMain { AIConnect(vault, refusing).connect(AIProviderId.ANTHROPIC, key) } })
        assertNull("a refused key is not kept", vault.read(AIProviderId.ANTHROPIC))

        val ok = StandInTransport { AIHttpResponse(200, "{}".toByteArray()) }
        offMain { AIConnect(vault, ok).connect(AIProviderId.ANTHROPIC, key) }
        assertEquals(key, vault.read(AIProviderId.ANTHROPIC))
        assertEquals("https://api.anthropic.com/v1/models?limit=1", ok.requests.first().url)
        assertEquals(AIError.of(AIError.Reason.INSECURE_ADDRESS), thrown { offMain { AIConnect(vault, ok).connect(AIProviderId.COMPATIBLE, key, "http://llm.example/v1") } })
        assertEquals("nothing sent over http", 1, ok.requests.size)
        offMain { AIConnect(vault, ok).connect(AIProviderId.COMPATIBLE, key, "https://compatible.example/v1") }
        assertEquals("https://compatible.example/v1/models", ok.requests.last().url)
    }

    @Test fun mlKitsModelReportsItselfUnavailableCleanly() {
        // Robolectric has no Gemini Nano: the client cannot be made, or says it is not available
        val model = AIMlKitOnDevice(org.robolectric.RuntimeEnvironment.getApplication())
        val r = AIRequest("on-device", "Sort.", "Paint the shed. Plant beans.", maxOutput = 50)
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { model.complete(r) })
        assertEquals(AIError.provider(AIErrorKind.MODEL_UNAVAILABLE), thrown { offMain { model.complete(r) } })
        assertEquals(false, offMain { model.isAvailable })
        assertEquals(false, model.sendsSchema)
    }
}
