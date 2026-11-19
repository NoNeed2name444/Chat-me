package com.cramdown.sorta.ai

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// S08 A1, A2 and A3 on the emulator: the Keystore vault (save, read, replace, delete, Disconnect) with a planted fake
// credential that is in no file, preference or log line; the sign-in's listener on 127.0.0.1 and ::1 and the whole
// OpenRouter sign-in with a stand-in browser; and Gemini Nano reporting itself unavailable cleanly. No network, no
// real key. Made-up ideas, nothing medical.
@RunWith(AndroidJUnit4::class)
class AIDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = "fake-key-for-tests-1"
    private val folder get() = File(context.noBackupFilesDir, "ai-test")
    private val vault get() = AIKeystoreVault(folder)
    private val capture = "Paint the shed blue before spring. Ask the neighbours about sharing a ladder. Try growing beans up the fence."
    private val goodSplit = "{\"ideas\":[{\"text\":\"Paint the shed blue before spring.\",\"title\":\"Paint the shed blue\",\"folder\":null}," +
        "{\"text\":\"Try growing beans up the fence.\",\"title\":\"Beans up the fence\",\"folder\":\"Garden\"}]}"

    private class StandIn(var handler: (AIHttpRequest) -> AIHttpResponse) : AITransport {
        val requests: MutableList<AIHttpRequest> = java.util.Collections.synchronizedList(ArrayList())
        override fun send(request: AIHttpRequest): AIHttpResponse {
            AIThread.requireBackground()
            requests.add(request)
            return handler(request)
        }
    }

    private fun thrown(work: () -> Unit): Throwable? = try { work(); null } catch (e: Throwable) { e }

    private fun chat(content: String) = AIHttpResponse(200, AIJSON.make(
        "choices" to AIJSON.Arr(listOf(AIJSON.make("message" to AIJSON.make("role" to AIJSON.Str("assistant"), "content" to AIJSON.Str(content))))),
        "usage" to AIJSON.make("prompt_tokens" to AIJSON.Num(231.0), "completion_tokens" to AIJSON.Num(48.0), "cost" to AIJSON.Num(0.0000214455)),
    ).data)

    @After fun clean() {
        for (p in AIProviderId.entries) try { vault.delete(p) } catch (_: AIError) { }
        folder.deleteRecursively()
        context.getSharedPreferences("sorta.ai.test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun theKeystoreVaultSavesReadsDeletesAndDisconnects() {
        assertNotEquals("the test thread is not the main one", Looper.getMainLooper(), Looper.myLooper())
        val v = vault
        assertNull(v.read(AIProviderId.OPENROUTER))
        v.save(key, AIProviderId.OPENROUTER)
        v.save("fake-key-for-tests-2", AIProviderId.ANTHROPIC)
        assertEquals(key, v.read(AIProviderId.OPENROUTER))
        assertEquals("another vault object reads the same file and key", key, vault.read(AIProviderId.OPENROUTER))
        v.save("fake-key-for-tests-3", AIProviderId.OPENROUTER)
        assertEquals("saving again replaces", "fake-key-for-tests-3", v.read(AIProviderId.OPENROUTER))

        val file = File(folder, "openrouter.bin")
        assertTrue(file.exists() && file.path.startsWith(context.noBackupFilesDir.path))
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse("only ciphertext is on disk", bytes.contains("fake-key-for-tests"))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(store.containsAlias(AIKeystoreVault.ALIAS + "openrouter"))
        assertNull("the key never leaves the Keystore", store.getKey(AIKeystoreVault.ALIAS + "openrouter", null).encoded)

        var disconnectOnMain: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            disconnectOnMain = thrown { AIConnect(v, StandIn { AIHttpResponse(500, ByteArray(0)) }).disconnect(AIProviderId.OPENROUTER) }
        }
        assertEquals("never on the main thread", AIError.of(AIError.Reason.MAIN_THREAD), disconnectOnMain)
        AIConnect(v, StandIn { AIHttpResponse(500, ByteArray(0)) }).disconnect(AIProviderId.OPENROUTER)
        assertNull("Disconnect deletes it at once", v.read(AIProviderId.OPENROUTER))
        assertFalse(file.exists())
        assertFalse(store.containsAlias(AIKeystoreVault.ALIAS + "openrouter"))
        assertEquals("the other account stays", "fake-key-for-tests-2", v.read(AIProviderId.ANTHROPIC))
        v.delete(AIProviderId.ANTHROPIC)
        v.delete(AIProviderId.ANTHROPIC)
        assertNull(v.read(AIProviderId.ANTHROPIC))
    }

    @Test fun aPlantedCredentialIsNowhereButTheKeystore() {
        val marker = "ai-leak-marker-" + System.nanoTime()
        Log.i("SortaAITest", marker)
        val prefs = context.getSharedPreferences("sorta.ai.test", Context.MODE_PRIVATE)
        val settings = AIPrefsKeyValue(prefs)
        val v = vault
        val files = AIApp.files(context)
        val ledgerFile = File(folder, "ai-ledger.json")
        val net = StandIn { if (it.url.endsWith("/key")) AIHttpResponse(200, "{\"data\":{\"label\":\"fake\"}}".toByteArray()) else chat(goodSplit) }
        val runner = AIRunner(v, settings, AIPrices(files.read("tables/prices.json")), AILedger(ledgerFile), net, null, files)
        val folders = listOf(AISplit.Folder("g", "Garden", listOf("plants", "beans", "fence")))

        AIConnect(v, net).connect(AIProviderId.OPENROUTER, "  $key\n")
        AISettings(settings).provider = AIProviderId.OPENROUTER
        AISettings(settings).setModel("deepseek/deepseek-v4.1-flash", AIProviderId.OPENROUTER)
        AIConsent(settings).setOn(AIFeature.SPLIT, true)
        AIConsent(settings).agree(AIFeature.SPLIT, AIProviderId.OPENROUTER)
        val outcome = runner.split(capture, folders) as AIOutcome.Answered
        assertEquals(AIVerdict.OK, outcome.verdict)
        assertEquals(listOf(null, "g"), outcome.value!!.map { it.folder })
        net.handler = { AIHttpResponse(401, "{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}".toByteArray()) }
        val failed = runner.split(capture, folders) as AIOutcome.Failed
        Log.w("SortaAITest", "refused: ${failed.error}", failed.error)
        thrown { AIConnect(v, net).connect(AIProviderId.OPENAI, key) }?.let { Log.w("SortaAITest", "refused", it) }

        assertEquals("it is in the Keystore vault, trimmed", key, v.read(AIProviderId.OPENROUTER))
        val log = Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.bufferedReader().readText()
        assertTrue("the log was read", log.contains(marker))
        assertFalse("not in the log", log.contains(key))
        assertFalse("not in SharedPreferences", prefs.all.entries.any { it.key.contains(key) || it.value.toString().contains(key) })
        assertTrue("the send was recorded", ledgerFile.readText().contains("\"split\""))
        val needle = key.toByteArray()
        val roots = listOf(context.filesDir, context.noBackupFilesDir, context.cacheDir, File(context.applicationInfo.dataDir, "shared_prefs"))
        val holding = roots.flatMap { it.walkTopDown().filter { f -> f.isFile }.toList() }
            .filter { String(it.readBytes(), Charsets.ISO_8859_1).contains(String(needle, Charsets.ISO_8859_1)) }
        assertEquals("not in any file the app keeps", emptyList<String>(), holding.map { it.name })
        assertTrue(net.requests.size >= 4)
        assertTrue("not in a request's address or body", net.requests.all { !it.url.contains(key) && !(it.body?.toString(Charsets.UTF_8) ?: "").contains(key) })
        assertTrue("only in the one header the provider reads", net.requests.all { r -> r.headers.filter { it.second.contains(key) }.map { it.first } == listOf("Authorization") })

        AIConnect(v, net).disconnect(AIProviderId.OPENROUTER)
        assertNull(v.read(AIProviderId.OPENROUTER))
        assertEquals(AIOutcome.Off, runner.split(capture, folders))
    }

    /** One GET to the listener on [host], and the whole answer. */
    private fun get(host: String, port: Int, target: String): String = Socket(InetAddress.getByName(host), port).use { s ->
        s.soTimeout = 10_000
        s.getOutputStream().apply { write("GET $target HTTP/1.1\r\nHost: localhost:$port\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
        s.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test fun theListenerAnswersOnBothLoopbackAddresses() {
        val listener = AILoopbackListener()
        try {
            assertTrue(listener.port in 49152..65000)
            for (host in listOf("127.0.0.1", "::1")) {
                val answers = arrayOfNulls<String>(2)
                val client = Thread {
                    answers[0] = get(host, listener.port, "/favicon.ico")
                    answers[1] = get(host, listener.port, "/callback?code=fake-code-for-tests-1&state=s")
                }.apply { start() }
                val target = listener.next(20_000, AIConnect.RETURN_URL)
                client.join(20_000)
                assertEquals(host, "/callback?code=fake-code-for-tests-1&state=s", target)
                assertTrue("$host: another path gets a 404", answers[0]!!.startsWith("HTTP/1.1 404 "))
                assertTrue("$host: the callback gets a redirect", answers[1]!!.startsWith("HTTP/1.1 302 "))
                assertTrue(host, answers[1]!!.contains("\r\nLocation: sorta://ai/connected\r\n"))
                assertTrue("$host: no body", answers[1]!!.endsWith("\r\n\r\n"))
            }
            assertEquals("nothing in time is cancelled", AIError.of(AIError.Reason.CANCELLED), thrown { listener.next(300, AIConnect.RETURN_URL) })
            var onMain: Throwable? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { onMain = thrown { listener.next(300, AIConnect.RETURN_URL) } }
            assertEquals("the main thread is refused", AIError.of(AIError.Reason.MAIN_THREAD), onMain)
        } finally {
            listener.close()
        }
    }

    @Test fun signInWithOpenRouterEndToEnd() {
        for ((host, wrongFirst) in listOf("127.0.0.1" to true, "::1" to false)) {
            val v = AIMemoryVault()
            val net = StandIn { r ->
                if (r.url.endsWith("/auth/keys")) AIHttpResponse(200, "{\"key\":\"fake-key-for-tests-2\",\"user_id\":\"fake-user-1\"}".toByteArray())
                else AIHttpResponse(if (r.header("Authorization") == "Bearer fake-key-for-tests-2") 200 else 401, "{}".toByteArray())
            }
            var opened: String? = null
            val answers = java.util.Collections.synchronizedList(ArrayList<String>())
            val browser = AIBrowser { url, scheme ->
                opened = url
                assertEquals("sorta", scheme)
                val query = URI(url).rawQuery.split("&").associate { it.substringBefore("=") to AIPKCE.decode(it.substringAfter("="))!! }
                val callback = URI(query.getValue("callback_url"))
                assertEquals("localhost", callback.host)
                Thread {
                    if (wrongFirst) answers.add(get(host, callback.port, "/callback?code=fake-code-for-tests-0&state=wrong"))
                    answers.add(get(host, callback.port, "/callback?code=fake-code-for-tests-1&state=" + AIPKCE.encode(query.getValue("state"))))
                }.start()
            }
            AIConnect(v, net).signInWithOpenRouter(browser, 30_000)
            assertEquals(host, "fake-key-for-tests-2", v.read(AIProviderId.OPENROUTER))
            val page = URI(opened!!)
            val query = page.rawQuery.split("&").associate { it.substringBefore("=") to AIPKCE.decode(it.substringAfter("="))!! }
            assertEquals("openrouter.ai", page.host)
            assertEquals("S256", query["code_challenge_method"])
            assertEquals("the exchange, then one cheap read", 2, net.requests.size)
            val sent = AIJSON.parse(net.requests[0].body!!)!!
            assertEquals("$host: the code with the right state", "fake-code-for-tests-1", sent["code"]!!.string)
            assertEquals("the verifier matches the challenge", query["code_challenge"], AIPKCE.challenge(sent["code_verifier"]!!.string!!))
            assertEquals("https://openrouter.ai/api/v1/key", net.requests[1].url)
            assertTrue("back to Sorta", answers.last().contains("Location: sorta://ai/connected"))
        }
    }

    @Test fun theOnDeviceModelReportsItselfUnavailableCleanly() {
        // O1: ML Kit does not start with the app. The installed app has no startup provider of its, the process has
        // not started it, and it starts by hand at the model's first use.
        val providers = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertTrue("no ML Kit startup provider: ${providers.map { it.name }}", providers.none { it.name.contains("mlkit", ignoreCase = true) })
        val startedBefore = AIMlKitOnDevice.started
        val runner = AIApp.runner(context)
        assertEquals("building the runner does not start ML Kit", startedBefore, AIMlKitOnDevice.started)
        var splitOnMain: AIOutcome<*>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { splitOnMain = runner.split(capture, emptyList()) }
        assertEquals(AIOutcome.Failed(AIError.of(AIError.Reason.MAIN_THREAD)), splitOnMain)
        val model = AIMlKitOnDevice(context)
        val request = AIRequest("on-device", "Sort.", "Paint the shed. Plant beans.", maxOutput = 50)
        var onMain: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { onMain = thrown { model.complete(request) } }
        assertEquals(AIError.of(AIError.Reason.MAIN_THREAD), onMain)
        if (model.isAvailable) {
            // a phone with Gemini Nano: the model answers, or fails as a provider would
            assertNotEquals(AIError.of(AIError.Reason.MAIN_THREAD), thrown { model.complete(request) })
        } else {
            assertEquals("CI's emulators lack the model", AIError.provider(AIErrorKind.MODEL_UNAVAILABLE), thrown { model.complete(request) })
        }
        assertTrue("the model's first use started ML Kit by hand", AIMlKitOnDevice.started)
        assertEquals("the app's runner reads its bundled tables", "claude-haiku-5-5", runner.suggested[AIProviderId.ANTHROPIC])
        assertEquals("with nothing chosen, AI is off", AIOutcome.Off, runner.split(capture, emptyList()))
        assertEquals(29_528, AIEmbeddings.load(AIApp.files(context), AIApp.EMBEDDINGS).rows)
    }
}
