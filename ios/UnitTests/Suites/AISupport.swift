import Foundation

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
