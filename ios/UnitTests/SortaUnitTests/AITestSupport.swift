import XCTest
@testable import Sorta

/// Shared by the AI unit tests (S08): the repository's ai/ files, work off
/// the main thread, a stand-in transport, a stand-in URLProtocol, captured
/// output, and a plain socket client for the loopback listener. Made-up
/// data only, and no test needs a key or the internet.
enum AIFiles {
    /// The repository's root, from this file's place in it.
    static let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    static var ai: URL { repo.appendingPathComponent("ai") }

    static func fixture(_ path: String) throws -> AIJSON {
        let data = try Data(contentsOf: ai.appendingPathComponent("fixtures").appendingPathComponent(path))
        return try XCTUnwrap(AIJSON.parse(data), path)
    }

    static let fakeKey = "fake-key-for-tests-1"
}

final class AIBox<T>: @unchecked Sendable { var result: Result<T, Error>? }

/// Runs `work` on a thread of its own and waits, as every AI call must.
/// A test on the main thread keeps the run loop turning while it waits, as the
/// app's main thread does: a UserDefaults write on the worker waits for it.
func aiOffMain<T>(_ work: @escaping () throws -> T) throws -> T {
    let box = AIBox<T>()
    let done = DispatchSemaphore(value: 0)
    let thread = Thread {
        do { box.result = .success(try work()) } catch { box.result = .failure(error) }
        done.signal()
    }
    thread.stackSize = 8 << 20
    thread.start()
    if Thread.isMainThread {
        while done.wait(timeout: .now()) == .timedOut {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.01))
        }
    } else {
        done.wait()
    }
    return try box.result!.get()
}

/// Answers each request from a handler and keeps every request it saw.
final class AIStandInTransport: AITransport {
    private let lock = NSLock()
    private var seen: [AIHTTPRequest] = []
    var handler: (AIHTTPRequest) throws -> AIHTTPResponse

    init(_ handler: @escaping (AIHTTPRequest) throws -> AIHTTPResponse = { _ in AIHTTPResponse(status: 500, body: Data()) }) {
        self.handler = handler
    }

    var requests: [AIHTTPRequest] { lock.lock(); defer { lock.unlock() }; return seen }

    func send(_ request: AIHTTPRequest) throws -> AIHTTPResponse {
        try AIThread.requireBackground()
        lock.lock()
        seen.append(request)
        lock.unlock()
        return try handler(request)
    }

    static func response(_ fx: AIJSON) -> AIHTTPResponse {
        AIHTTPResponse(status: fx["response"]!["status"]!.int!, body: fx["response"]!["body"]!.data)
    }
}

/// An OpenRouter chat answer carrying `content`, with its reported use.
func aiChat(_ content: String, cost: Double = 0.0000214455) -> AIHTTPResponse {
    let message = AIJSON.make([("role", .string("assistant")), ("content", .string(content))])
    let body = AIJSON.make([("choices", .array([.make([("message", message)])])),
                            ("usage", .make([("prompt_tokens", .number(231)), ("completion_tokens", .number(48)),
                                             ("cost", .number(cost))]))])
    return AIHTTPResponse(status: 200, body: body.data)
}

/// The network stand-in under URLSession itself, for AIURLSessionTransport.
final class AIStandInProtocol: URLProtocol {
    struct Seen { let request: URLRequest; let body: Data? }
    nonisolated(unsafe) static var answer: (URLRequest) -> Result<(Int, Data), URLError> = { _ in .failure(URLError(.notConnectedToInternet)) }
    nonisolated(unsafe) static var seen: [Seen] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        var body = request.httpBody
        if body == nil, let stream = request.httpBodyStream {
            var data = Data()
            stream.open()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let n = stream.read(&buffer, maxLength: buffer.count)
                if n <= 0 { break }
                data.append(buffer, count: n)
            }
            stream.close()
            body = data
        }
        Self.seen.append(Seen(request: request, body: body))
        switch Self.answer(request) {
        case let .success((status, data)):
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: [:])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(error):
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

/// Everything written to stdout and stderr while `work` runs.
func aiCaptured(_ work: () throws -> Void) rethrows -> String {
    let file = FileManager.default.temporaryDirectory.appendingPathComponent("ai-log-\(UUID().uuidString).txt")
    FileManager.default.createFile(atPath: file.path, contents: nil)
    defer { try? FileManager.default.removeItem(at: file) }
    fflush(stdout); fflush(stderr)
    let fd = open(file.path, O_WRONLY)
    let out = dup(1), err = dup(2)
    dup2(fd, 1); dup2(fd, 2)
    defer {
        fflush(stdout); fflush(stderr)
        dup2(out, 1); dup2(err, 2)
        close(fd); close(out); close(err)
    }
    try work()
    fflush(stdout); fflush(stderr)
    return (try? String(contentsOf: file, encoding: .utf8)) ?? ""
}

/// One plain HTTP GET to the listener at a numeric address; the whole answer.
func aiLoopbackGet(_ host: String, port: UInt16, target: String) throws -> String {
    var hints = addrinfo()
    hints.ai_flags = AI_NUMERICHOST
    hints.ai_socktype = SOCK_STREAM
    var found: UnsafeMutablePointer<addrinfo>?
    guard getaddrinfo(host, String(port), &hints, &found) == 0, let info = found else { throw AIError.transport }
    defer { freeaddrinfo(found) }
    let s = socket(info.pointee.ai_family, SOCK_STREAM, 0)
    guard s >= 0 else { throw AIError.transport }
    defer { close(s) }
    var on: Int32 = 1
    setsockopt(s, SOL_SOCKET, SO_NOSIGPIPE, &on, socklen_t(MemoryLayout<Int32>.size))
    guard connect(s, info.pointee.ai_addr, info.pointee.ai_addrlen) == 0 else { throw AIError.transport }
    let request = Array("GET \(target) HTTP/1.1\r\nHost: localhost:\(port)\r\nConnection: close\r\n\r\n".utf8)
    guard write(s, request, request.count) == request.count else { throw AIError.transport }
    var answer = Data()
    var buffer = [UInt8](repeating: 0, count: 2048)
    while true {
        let n = read(s, &buffer, buffer.count)
        if n <= 0 { break }
        answer.append(buffer, count: n)
    }
    return String(decoding: answer, as: UTF8.self)
}
