import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

// MARK: - Add-ons (S07b): the real network, for net.fetch and the directory
//
// Sections 7 and 9: no cookies, no ids, no cache, the plain user agent
// "Sorta", HTTPS only (App Transport Security's default stays on), and
// redirects never followed by URLSession: the add-on host follows them
// itself, within the grants. Every send runs on an add-on's own thread or a
// background caller, never the main thread; it waits for its reply there.

enum AddonHTTP {
    static let userAgent = "Sorta"

    /// The one session everything add-on related goes out on.
    static func configuration() -> URLSessionConfiguration {
        let c = URLSessionConfiguration.ephemeral
        c.httpCookieAcceptPolicy = .never
        c.httpShouldSetCookies = false
        c.httpCookieStorage = nil
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpAdditionalHeaders = ["User-Agent": userAgent]
        return c
    }

    /// A request as Sorta sends it: no cookies, the plain user agent, and
    /// only the headers given (never an id).
    static func request(_ url: URL, method: String = "GET", headers: [String: String] = [:],
                        body: Data? = nil, timeoutMs: Int) -> URLRequest {
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData,
                           timeoutInterval: max(0.001, Double(timeoutMs) / 1000))
        r.httpMethod = method
        r.httpShouldHandleCookies = false
        for (k, v) in headers { r.setValue(v, forHTTPHeaderField: k) }
        r.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if let body, !body.isEmpty { r.httpBody = body }
        return r
    }
}

/// net.fetch over URLSession. One request at a time per add-on; the caller
/// is the add-on's thread, which waits here (the main thread never does).
final class AddonSessionNetwork: NSObject, AddonNetwork, URLSessionDataDelegate {
    private let limit: Int
    private let lock = NSLock()
    private var session: URLSession!
    private var received = Data()
    private var response: HTTPURLResponse?
    private var failure: String?
    private var finished: DispatchSemaphore?

    init(limit: Int = AddonCall.netLimit) {
        self.limit = limit
        super.init()
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.name = "Sorta add-on network"
        session = URLSession(configuration: AddonHTTP.configuration(), delegate: self, delegateQueue: queue)
    }

    deinit { session.invalidateAndCancel() }

    func send(method: String, url: AddonURL, headers: [String: String], body: String, timeLeftMs: Int) -> AddonNetReply {
        let (reply, data) = perform(method: method, url: url, headers: headers, body: body, timeLeftMs: timeLeftMs)
        var r = reply
        r.body = String(decoding: data, as: UTF8.self)
        return r
    }

    /// One request; the reply's body is left empty and the bytes come back as they are.
    func perform(method: String, url: AddonURL, headers: [String: String], body: String,
                 timeLeftMs: Int) -> (AddonNetReply, Data) {
        guard url.scheme == "https", let u = URL(string: url.text) else { return (AddonNetReply(failure: "net.https"), Data()) }
        guard timeLeftMs > 0 else { return (AddonNetReply(failure: "net.time"), Data()) }
        let done = DispatchSemaphore(value: 0)
        lock.lock()
        received = Data()
        response = nil
        failure = nil
        finished = done
        lock.unlock()
        let started = DispatchTime.now().uptimeNanoseconds
        let task = session.dataTask(with: AddonHTTP.request(u, method: method, headers: headers,
                                                            body: Data(body.utf8), timeoutMs: timeLeftMs))
        task.resume()
        if done.wait(timeout: .now() + .milliseconds(timeLeftMs)) == .timedOut {
            task.cancel()
            return (AddonNetReply(delayMs: timeLeftMs + 1, failure: "net.time"), Data())
        }
        let ms = Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000)
        lock.lock()
        defer { lock.unlock() }
        if let failure { return (AddonNetReply(delayMs: ms, failure: failure), Data()) }
        guard let r = response else { return (AddonNetReply(delayMs: ms, failure: "net.failed"), Data()) }
        var hs: [String: String] = [:]
        for (k, v) in r.allHeaderFields {
            if let k = k as? String, let v = v as? String {
                let name = k.lowercased()
                if name != "set-cookie" { hs[name] = v }
            }
        }
        return (AddonNetReply(status: r.statusCode, headers: hs, delayMs: ms), received)
    }

    // MARK: URLSession's delegate, on its own queue

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        lock.lock()
        self.response = response as? HTTPURLResponse
        let tooBig = response.expectedContentLength > Int64(limit)
        if tooBig { failure = "net.size" }
        lock.unlock()
        completionHandler(tooBig ? .cancel : .allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock()
        received.append(data)
        let tooBig = received.count > limit
        if tooBig { failure = "net.size" }
        lock.unlock()
        if tooBig { dataTask.cancel() }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        if let error, failure == nil {
            let e = error as NSError
            failure = e.code == NSURLErrorTimedOut ? "net.time" : "net.failed"
        }
        let done = finished
        finished = nil
        lock.unlock()
        done?.signal()
    }
}

/// Reads one file over HTTPS for the directory client and installs from a
/// link: at most `limit` bytes, the same session rules.
protocol AddonFetcher: AnyObject {
    /// The bytes, or a code: net.https, net.size, net.time, net.failed, net.status.
    func get(_ url: String, limit: Int) -> Result<Data, AddonFetchError>
}

struct AddonFetchError: Error, Equatable {
    var code: String
}

final class AddonSessionFetcher: AddonFetcher {
    func get(_ url: String, limit: Int) -> Result<Data, AddonFetchError> {
        guard let u = AddonURL(url), u.scheme == "https" else { return .failure(AddonFetchError(code: "net.https")) }
        let net = AddonSessionNetwork(limit: limit)
        let (r, data) = net.perform(method: "GET", url: u, headers: [:], body: "", timeLeftMs: AddonCall.netMs)
        if let f = r.failure { return .failure(AddonFetchError(code: f)) }
        guard r.status == 200 else { return .failure(AddonFetchError(code: "net.status")) }
        return .success(data)
    }
}
