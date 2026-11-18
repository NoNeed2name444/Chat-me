package com.cramdown.sorta.addons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// Add-ons (S07b): the real network, for net.fetch and the directory.
//
// Sections 7 and 9: no cookies, no ids, no cache, the plain user agent "Sorta", HTTPS only, and
// redirects never followed here: the add-on host follows them itself, within the grants. Every send runs
// on an add-on's own thread or a background caller, never the main thread. It follows
// ios/Sorta/Addons/AddonNet.swift.

object AddonHttp {
    const val USER_AGENT = "Sorta"

    /** A request as Sorta sends it: no cookies, no cache, the plain user agent, and only the headers given (never an id). */
    fun open(url: URL, method: String, headers: Map<String, String>, timeoutMs: Int): HttpsURLConnection {
        val c = url.openConnection() as? HttpsURLConnection ?: throw IOException("https only")
        c.requestMethod = method
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        for ((k, v) in headers) if (!k.equals("cookie", ignoreCase = true)) c.setRequestProperty(k, v)
        c.setRequestProperty("User-Agent", USER_AGENT)
        return c
    }
}

/** net.fetch over HttpsURLConnection. One request at a time per add-on; the caller is the add-on's thread, which waits here (the main thread never does). */
class AddonHttpsNetwork(private val limit: Int = AddonCall.NET_LIMIT) : AddonNetwork {
    override fun send(method: String, url: AddonUrl, headers: Map<String, String>, body: String, timeLeftMs: Int): AddonNetReply {
        val (reply, bytes) = perform(method, url, headers, body, timeLeftMs)
        return reply.copy(body = String(bytes, Charsets.UTF_8))
    }

    /** One request; the reply's body is left empty and the bytes come back as they are. */
    fun perform(method: String, url: AddonUrl, headers: Map<String, String>, body: String, timeLeftMs: Int): Pair<AddonNetReply, ByteArray> {
        val none = ByteArray(0)
        if (url.scheme != "https") return AddonNetReply(failure = "net.https") to none
        if (timeLeftMs <= 0) return AddonNetReply(failure = "net.time") to none
        val started = System.nanoTime()
        fun ms() = ((System.nanoTime() - started) / 1_000_000).toInt()
        var c: HttpsURLConnection? = null
        try {
            c = AddonHttp.open(URL(url.text), method, headers, timeLeftMs)
            val sent = body.toByteArray(Charsets.UTF_8)
            if (sent.isNotEmpty() && method != "GET" && method != "HEAD") {
                c.doOutput = true
                c.setFixedLengthStreamingMode(sent.size)
                c.outputStream.use { it.write(sent) }
            }
            val status = c.responseCode
            if (c.contentLengthLong > limit) return AddonNetReply(delayMs = ms(), failure = "net.size") to none
            val out = ByteArrayOutputStream()
            val stream = if (status >= 400) c.errorStream else c.inputStream
            stream?.use { s ->
                val buffer = ByteArray(16_384)
                while (true) {
                    val n = s.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > limit) return AddonNetReply(delayMs = ms(), failure = "net.size") to none
                    if (ms() > timeLeftMs) return AddonNetReply(delayMs = timeLeftMs + 1, failure = "net.time") to none
                }
            }
            val hs = LinkedHashMap<String, String>()
            for ((k, v) in c.headerFields) {
                val name = k?.let { AddonLower.of(it) } ?: continue
                if (name != "set-cookie" && v.isNotEmpty()) hs[name] = v.joinToString(", ")
            }
            return AddonNetReply(status, hs, delayMs = ms()) to out.toByteArray()
        } catch (e: SocketTimeoutException) {
            return AddonNetReply(delayMs = timeLeftMs + 1, failure = "net.time") to none
        } catch (e: IOException) {
            return AddonNetReply(delayMs = ms(), failure = "net.failed") to none
        } finally {
            c?.disconnect()
        }
    }
}

/** Reads one file over HTTPS for the directory client and installs from a link: at most [limit] bytes, the same rules. */
fun interface AddonFetcher {
    /** The bytes, or an AddonRefusal with a code: net.https, net.size, net.time, net.failed, net.status. */
    fun get(url: String, limit: Int): ByteArray
}

class AddonHttpsFetcher : AddonFetcher {
    override fun get(url: String, limit: Int): ByteArray {
        val u = AddonUrl.parse(url)
        if (u == null || u.scheme != "https") throw AddonRefusal("net.https")
        val (r, data) = AddonHttpsNetwork(limit).perform("GET", u, emptyMap(), "", AddonCall.NET_MS)
        r.failure?.let { throw AddonRefusal(it) }
        if (r.status != 200) throw AddonRefusal("net.status")
        return data
    }
}
