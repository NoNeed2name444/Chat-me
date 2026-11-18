package com.cramdown.sorta.ai

import java.io.File

// Shared by the AI tests (S08): the repository's ai/ folder and its fixtures, a stand-in transport that replays them
// and keeps every request, and running work off the main thread, as every AI call must (under Robolectric the test
// thread is the main one). iOS's AISupport.swift does the same for the Linux suites.

object AIRepo {
    /** The repository's ai/: Gradle runs unit tests from android/app. */
    val ai: File = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "ai") }
        .first { File(it, "prompts/SHA256SUMS").exists() }
    val files = AIDirectoryFiles(ai)
    const val KEY = "fake-key-for-tests-1"

    fun fixture(path: String): AIJSON = AIJSON.parse(File(ai, "fixtures/$path").readBytes()) ?: error("cannot read fixture $path")
    fun text(path: String) = File(ai, path).readText(Charsets.UTF_8)
    fun prices() = AIPrices(File(ai, "tables/prices.json").readBytes())
}

/** Runs [work] on a thread of its own and waits. */
fun <T> offMain(work: () -> T): T {
    var result: Result<T>? = null
    val thread = Thread(null, { result = runCatching(work) }, "ai-test", 8L shl 20)
    thread.start()
    thread.join()
    return result!!.getOrThrow()
}

/** The error [work] throws, or null. */
fun thrown(work: () -> Unit): Throwable? = try { work(); null } catch (e: Throwable) { e }

class StandInTransport(var handler: (AIHttpRequest) -> AIHttpResponse = { AIHttpResponse(500, ByteArray(0)) }) : AITransport {
    val requests: MutableList<AIHttpRequest> = java.util.Collections.synchronizedList(ArrayList())

    override fun send(request: AIHttpRequest): AIHttpResponse {
        AIThread.requireBackground()
        requests.add(request)
        return handler(request)
    }

    companion object {
        fun response(fx: AIJSON) = AIHttpResponse(fx["response"]!!["status"]!!.int!!, fx["response"]!!["body"]!!.data)
        fun replaying(fx: AIJSON) = StandInTransport { response(fx) }
    }
}

/** Why [request] differs from the fixture's (method, URL, header names and values, body as JSON), or null. */
fun mismatch(request: AIHttpRequest, fx: AIJSON): String? {
    val want = fx["request"]!!
    if (request.method != want["method"]!!.string) return "method ${request.method}"
    if (request.url != want["url"]!!.string) return "url ${request.url}"
    val headers = want["headers"] as AIJSON.Obj
    val names = request.headers.map { it.first.lowercase() }.toSet()
    if (names != headers.keys.map { it.lowercase() }.toSet()) return "headers $names"
    for ((k, v) in headers.map) if (request.header(k) != v.string) return "header $k"
    val body = want["body"]!!
    if (body.isNull) return if (request.body == null) null else "a body was sent"
    val sent = request.body?.let { AIJSON.parse(it) } ?: return "no JSON body"
    return if (sent == body) null else "body " + sent.text
}

fun request(fx: AIJSON, schema: String?): AIRequest {
    val c = fx["call"]!!
    val name = c["schema"]?.string
    return AIRequest(c["model"]!!.string!!, c["instructions"]!!.string!!, c["input"]!!.string!!, name, if (name == null) null else schema, c["maxOutput"]!!.int!!)
}
