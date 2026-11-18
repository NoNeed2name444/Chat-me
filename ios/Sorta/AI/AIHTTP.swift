import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

// MARK: - HTTP, straight from the phone to the provider (S08, section 4)
//
// URLSession only, with no HTTP library. The session is ephemeral, so no
// cookie, cache entry or answer is kept on disk. Tests swap the transport
// for a stand-in that replays ai/fixtures/http.

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

    init(configuration: URLSessionConfiguration = .ephemeral) {
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.timeoutIntervalForRequest = 60
        session = URLSession(configuration: configuration)
    }

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
