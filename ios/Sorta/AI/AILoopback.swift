import Foundation
#if canImport(Darwin)
import Darwin
#elseif canImport(Glibc)
import Glibc
#endif

// MARK: - The sign-in's loopback listener (S08 A2)
//
// The same random port on both 127.0.0.1 and ::1, so the callback to
// http://localhost:<port>/callback arrives whichever address localhost
// resolves to. It answers the callback with a redirect back to Sorta and an
// empty body: no page, so no string a person reads.

final class AILoopbackListener {
    let port: UInt16
    private var sockets: [Int32]

    init() throws {
        var g = SystemRandomNumberGenerator()
        for _ in 0..<40 {
            let port = UInt16.random(in: 49152...65000, using: &g)
            guard let v4 = Self.bind4(port) else { continue }
            guard let v6 = Self.bind6(port) else { _ = closeSocket(v4); continue }
            self.port = port
            sockets = [v4, v6]
            return
        }
        throw AIError.transport
    }

    deinit { close() }

    func close() {
        for s in sockets { _ = closeSocket(s) }
        sockets = []
    }

    private static func stream() -> Int32 {
        #if canImport(Glibc)
        return Int32(SOCK_STREAM.rawValue)
        #else
        return SOCK_STREAM
        #endif
    }

    private static func bind4(_ port: UInt16) -> Int32? {
        let s = socket(AF_INET, stream(), 0)
        guard s >= 0 else { return nil }
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        addr.sin_addr.s_addr = UInt32(0x7F00_0001).bigEndian
        let ok = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        } == 0 && listen(s, 4) == 0
        if !ok { _ = closeSocket(s); return nil }
        return s
    }

    private static func bind6(_ port: UInt16) -> Int32? {
        let s = socket(AF_INET6, stream(), 0)
        guard s >= 0 else { return nil }
        var only: Int32 = 1
        setsockopt(s, Int32(IPPROTO_IPV6), IPV6_V6ONLY, &only, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in6()
        addr.sin6_family = sa_family_t(AF_INET6)
        addr.sin6_port = port.bigEndian
        addr.sin6_addr = in6addr_loopback
        let ok = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in6>.size)) }
        } == 0 && listen(s, 4) == 0
        if !ok { _ = closeSocket(s); return nil }
        return s
    }

    /// The next request's target (for example "/callback?code=…&state=…"),
    /// answered with a 302 to `redirect`; other paths get a 404. Throws
    /// AIError.cancelled when nothing comes in `timeout`.
    func next(timeout: TimeInterval, redirect: String) throws -> String {
        try AIThread.requireBackground()
        let deadline = Date().addingTimeInterval(timeout)
        while true {
            let left = deadline.timeIntervalSinceNow
            guard left > 0 else { throw AIError.cancelled }
            var fds = sockets.map { pollfd(fd: $0, events: Int16(POLLIN), revents: 0) }
            let n = poll(&fds, nfds_t(fds.count), Int32(min(left, 3600) * 1000))
            guard n > 0 else { continue }
            for f in fds where f.revents & Int16(POLLIN) != 0 {
                let c = accept(f.fd, nil, nil)
                guard c >= 0 else { continue }
                defer { _ = closeSocket(c) }
                guard let target = Self.readTarget(c) else { continue }
                let path = target.split(separator: "?", maxSplits: 1).first.map(String.init) ?? target
                if path == "/callback" {
                    Self.write(c, "HTTP/1.1 302 Found\r\nLocation: \(redirect)\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    return target
                }
                Self.write(c, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            }
        }
    }

    private static func readTarget(_ c: Int32) -> String? {
        var tv = timeval(tv_sec: 5, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var data = [UInt8]()
        var buffer = [UInt8](repeating: 0, count: 2048)
        while data.count < 16384 {
            let n = recv(c, &buffer, buffer.count, 0)
            guard n > 0 else { break }
            data += buffer[0..<n]
            if data.count >= 4, String(decoding: data, as: UTF8.self).contains("\r\n\r\n") { break }
        }
        guard let line = String(decoding: data, as: UTF8.self).components(separatedBy: "\r\n").first else { return nil }
        let parts = line.split(separator: " ")
        guard parts.count == 3, parts[0] == "GET" else { return nil }
        return String(parts[1])
    }

    private static func write(_ c: Int32, _ text: String) {
        let bytes = Array(text.utf8)
        // a browser that has gone must not end the app with SIGPIPE
        #if canImport(Glibc)
        let flags = Int32(MSG_NOSIGNAL)
        #else
        var one: Int32 = 1
        setsockopt(c, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
        let flags: Int32 = 0
        #endif
        _ = bytes.withUnsafeBytes { send(c, $0.baseAddress, bytes.count, flags) }
    }
}

private func closeSocket(_ s: Int32) -> Int32 {
    #if canImport(Glibc)
    return Glibc.close(s)
    #else
    return Darwin.close(s)
    #endif
}
