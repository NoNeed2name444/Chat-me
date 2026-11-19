import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

// MARK: - HTTP, straight from the phone to the provider (S08, section 4)
//
// URLSession only, with no HTTP library. The session is ephemeral, so no
// cookie, cache entry or answer is kept on disk. A redirect is never followed:
// the 3xx answer comes back as it is, so the key's header goes to no second
// address. Tests swap the transport for a stand-in that replays ai/fixtures/http.

struct AIHTTPRequest {
    var method: String
    var url: String
    var headers: [(String, String)]
    var body: Data?

    func header(_ name: String) -> String? {
        headers.first(where: { $0.0.lowercased() == name.lowercased() })?.1
    }
}

struct AIHTTPResponse {
    var status: Int
    var body: Data
}

protocol AITransport: AnyObject {
    /// Blocks until the answer comes; throws AIError.transport when none does.
    func send(_ request: AIHTTPRequest) throws -> AIHTTPResponse
}

final class AIURLSessionTransport: AITransport {
    private let session: URLSession

    /// Refuses every redirect, so the task ends with the 3xx answer itself.
    private final class NoRedirects: NSObject, URLSessionTaskDelegate {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
            completionHandler(nil)
        }
    }

    init(configuration: URLSessionConfiguration = .ephemeral) {
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.timeoutIntervalForRequest = 60
        session = URLSession(configuration: configuration, delegate: NoRedirects(), delegateQueue: nil)
    }

    deinit { session.finishTasksAndInvalidate() }

    private final class Box: @unchecked Sendable {
        var response: AIHTTPResponse?
    }

    func send(_ request: AIHTTPRequest) throws -> AIHTTPResponse {
        try AIThread.requireBackground()
        guard let url = URL(string: request.url) else { throw AIError.transport }
        var r = URLRequest(url: url)
        r.httpMethod = request.method
        for (name, value) in request.headers { r.setValue(value, forHTTPHeaderField: name) }
        r.httpBody = request.body
        let done = DispatchSemaphore(value: 0)
        let box = Box()
        session.dataTask(with: r) { data, response, _ in
            if let http = response as? HTTPURLResponse {
                box.response = AIHTTPResponse(status: http.statusCode, body: data ?? Data())
            }
            done.signal()
        }.resume()
        done.wait()
        guard let response = box.response else { throw AIError.transport }
        return response
    }
}
