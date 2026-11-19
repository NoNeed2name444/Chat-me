import Foundation
#if canImport(Glibc)
import Glibc
#elseif canImport(Darwin)
import Darwin
#endif

// Shared by the AI suites (S08): the check line, the fixtures in ai/fixtures,
// a stand-in transport that replays them and counts requests, and running
// work off the main thread, as every AI call must. The suites run from the
// repository's root (tools/swift_suites.py).

var failures: [String] = []
var checks = 0

func check(_ label: String, _ ok: Bool, _ detail: @autoclosure () -> String = "") {
    checks += 1
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail()))
    if !ok { failures.append(label) }
}

func finish(_ name: String) -> Never {
    print(failures.isEmpty ? "\nALL \(name) TESTS PASS (\(checks))" : "\n\(failures.count) \(name) TEST FAILURE(S)")
    exit(failures.isEmpty ? 0 : 1)
}

let aiRoot = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("ai")

func fixture(_ path: String) -> AIJSON {
    let url = aiRoot.appendingPathComponent("fixtures").appendingPathComponent(path)
    guard let data = try? Data(contentsOf: url), let json = AIJSON.parse(data) else {
        print("FAIL cannot read fixture \(path)")
        exit(1)
    }
    return json
}

/// Runs `work` on a thread of its own and waits: AI calls refuse the main thread.
final class ResultBox<T>: @unchecked Sendable { var result: Result<T, Error>? }

func offMain<T>(_ work: @escaping () throws -> T) throws -> T {
    let box = ResultBox<T>()
    let done = DispatchSemaphore(value: 0)
    let thread = Thread {
        do { box.result = .success(try work()) } catch { box.result = .failure(error) }
        done.signal()
    }
    thread.stackSize = 8 << 20
    thread.start()
    done.wait()
    return try box.result!.get()
}

/// The network stand-in: answers each request from a handler and keeps
/// every request it saw.
final class StandInTransport: AITransport {
    var requests: [AIHTTPRequest] = []
    var handler: (AIHTTPRequest) throws -> AIHTTPResponse
    private let lock = NSLock()

    init(_ handler: @escaping (AIHTTPRequest) throws -> AIHTTPResponse = { _ in AIHTTPResponse(status: 500, body: Data()) }) {
        self.handler = handler
    }

    func send(_ request: AIHTTPRequest) throws -> AIHTTPResponse {
        try AIThread.requireBackground()
        lock.lock()
        requests.append(request)
        lock.unlock()
        return try handler(request)
    }

    /// Answers with a fixture's response.
    static func replaying(_ fx: AIJSON) -> StandInTransport {
        StandInTransport { _ in
            let r = fx["response"]!
            return AIHTTPResponse(status: r["status"]!.int!, body: r["body"]!.data)
        }
    }
}

/// The request a fixture expects, against one built: method, URL, header
/// names and values, and the body as JSON.
func matches(_ request: AIHTTPRequest, _ fx: AIJSON) -> (Bool, String) {
    let want = fx["request"]!
    if request.method != want["method"]!.string { return (false, "method \(request.method)") }
    if request.url != want["url"]!.string { return (false, "url \(request.url)") }
    guard case let .object(headers, _)? = want["headers"] else { return (false, "no headers") }
    let names = Set(request.headers.map { $0.0.lowercased() })
    if names != Set(headers.keys.map { $0.lowercased() }) { return (false, "headers \(names.sorted())") }
    for (k, v) in headers where request.header(k) != v.string { return (false, "header \(k)") }
    let body = want["body"]!
    if body.isNull { return (request.body == nil, "a body was sent") }
    guard let sent = request.body.flatMap(AIJSON.parse) else { return (false, "no JSON body") }
    return (sent == body, "body " + sent.text)
}

/// Everything written to stdout and stderr while `work` runs.
func captured(_ work: () -> Void) -> String {
    let file = scratch("log").appendingPathComponent("out.txt")
    FileManager.default.createFile(atPath: file.path, contents: nil)
    fflush(stdout); fflush(stderr)
    let fd = open(file.path, O_WRONLY)
    let out = dup(1), err = dup(2)
    dup2(fd, 1); dup2(fd, 2)
    work()
    fflush(stdout); fflush(stderr)
    dup2(out, 1); dup2(err, 2)
    close(fd); close(out); close(err)
    return (try? String(contentsOf: file, encoding: .utf8)) ?? ""
}

/// A fresh scratch folder for one suite.
func scratch(_ name: String) -> URL {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent("ai-\(name)-\(UUID().uuidString)", isDirectory: true)
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir
}

/// A listener on 127.0.0.1 alone, for the real transport: it takes one
/// request, answers it with `answer`, and gives back what it was sent, or nil
/// when nothing comes in `wait` seconds. (The app's own listener also needs
/// ::1, which not every Linux machine has.)
final class OneAnswerServer {
    let port: UInt16
    private let socketFD: Int32

    init?() {
        #if canImport(Glibc)
        let s = socket(AF_INET, Int32(SOCK_STREAM.rawValue), 0)
        #else
        let s = socket(AF_INET, SOCK_STREAM, 0)
        #endif
        guard s >= 0 else { return nil }
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = 0
        addr.sin_addr.s_addr = UInt32(0x7F00_0001).bigEndian
        var size = socklen_t(MemoryLayout<sockaddr_in>.size)
        let bound = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, size) }
        } == 0 && listen(s, 4) == 0
        let named = withUnsafeMutablePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(s, $0, &size) }
        } == 0
        guard bound, named else { close(s); return nil }
        port = UInt16(bigEndian: addr.sin_port)
        socketFD = s
    }

    deinit { close(socketFD) }

    func serve(_ answer: String, wait: Double) -> String? {
        var fd = pollfd(fd: socketFD, events: Int16(POLLIN), revents: 0)
        guard poll(&fd, 1, Int32(wait * 1000)) > 0 else { return nil }
        let c = accept(socketFD, nil, nil)
        guard c >= 0 else { return nil }
        defer { close(c) }
        var tv = timeval(tv_sec: 5, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var data = [UInt8]()
        var buffer = [UInt8](repeating: 0, count: 2048)
        while data.count < 16384, !String(decoding: data, as: UTF8.self).contains("\r\n\r\n") {
            let n = recv(c, &buffer, buffer.count, 0)
            guard n > 0 else { break }
            data += buffer[0..<n]
        }
        let bytes = Array(answer.utf8)
        _ = bytes.withUnsafeBytes { send(c, $0.baseAddress, bytes.count, 0) }
        return String(decoding: data, as: UTF8.self)
    }
}
