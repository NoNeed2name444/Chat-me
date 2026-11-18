import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

// MARK: - OpenRouter's sign-in (S08 A2): OAuth with PKCE, no app registration
//
// The verifier and state are random; the challenge is the verifier's
// SHA-256 in base64url (RFC 7636, S256). The sign-in URL carries
// callback_url=http://localhost:<port>/callback, where AILoopbackListener
// waits on both 127.0.0.1 and ::1; or, with no callback, OpenRouter shows a
// code the person pastes (single use, 10 minutes). The code is exchanged at
// /api/v1/auth/keys, and the key goes to the vault.

enum AISHA256 {
    static func digest(_ bytes: [UInt8]) -> [UInt8] {
        #if canImport(CryptoKit)
        return Array(SHA256.hash(data: Data(bytes)))
        #else
        return Plain.digest(bytes)
        #endif
    }

    static func hex(_ data: Data) -> String {
        digest(Array(data)).map { String(format: "%02x", $0) }.joined()
    }

    /// FIPS 180-4, for the Linux suites, which have no CryptoKit.
    enum Plain {
        static let k: [UInt32] = [
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
        ]

        static func digest(_ message: [UInt8]) -> [UInt8] {
            var h: [UInt32] = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]
            var m = message
            let bits = UInt64(message.count) * 8
            m.append(0x80)
            while m.count % 64 != 56 { m.append(0) }
            for i in (0..<8).reversed() { m.append(UInt8((bits >> (UInt64(i) * 8)) & 0xff)) }
            func rotr(_ x: UInt32, _ n: UInt32) -> UInt32 { (x >> n) | (x << (32 - n)) }
            var w = [UInt32](repeating: 0, count: 64)
            for chunk in stride(from: 0, to: m.count, by: 64) {
                for t in 0..<16 {
                    w[t] = (0..<4).reduce(0) { ($0 << 8) | UInt32(m[chunk + t * 4 + $1]) }
                }
                for t in 16..<64 {
                    let s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >> 3)
                    let s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >> 10)
                    w[t] = w[t - 16] &+ s0 &+ w[t - 7] &+ s1
                }
                var (a, b, c, d, e, f, g, hh) = (h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7])
                for t in 0..<64 {
                    let s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
                    let ch = (e & f) ^ (~e & g)
                    let t1 = hh &+ s1 &+ ch &+ k[t] &+ w[t]
                    let s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
                    let maj = (a & b) ^ (a & c) ^ (b & c)
                    let t2 = s0 &+ maj
                    (hh, g, f, e, d, c, b, a) = (g, f, e, d &+ t1, c, b, a, t1 &+ t2)
                }
                for (i, v) in [a, b, c, d, e, f, g, hh].enumerated() { h[i] = h[i] &+ v }
            }
            return h.flatMap { v in (0..<4).map { UInt8((v >> (24 - UInt32($0) * 8)) & 0xff) } }
        }
    }
}

enum AIPKCE {
    static func base64url(_ bytes: [UInt8]) -> String {
        Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func randomBytes(_ n: Int) -> [UInt8] {
        var g = SystemRandomNumberGenerator()
        return (0..<n).map { _ in UInt8.random(in: 0...255, using: &g) }
    }

    /// 32 random bytes, 43 characters (RFC 7636, 4.1).
    static func verifier(_ bytes: [UInt8] = randomBytes(32)) -> String { base64url(bytes) }

    static func challenge(_ verifier: String) -> String { base64url(AISHA256.digest(Array(verifier.utf8))) }

    static func state() -> String { base64url(randomBytes(16)) }

    /// Percent-encodes everything but RFC 3986's unreserved characters, the
    /// same way on both platforms.
    static func encode(_ text: String) -> String {
        var out = ""
        for b in text.utf8 {
            switch b {
            case 0x41...0x5A, 0x61...0x7A, 0x30...0x39, 0x2D, 0x2E, 0x5F, 0x7E: out.unicodeScalars.append(Unicode.Scalar(b))
            default: out += String(format: "%%%02X", b)
            }
        }
        return out
    }

    static func decode(_ text: String) -> String? {
        text.replacingOccurrences(of: "+", with: " ").removingPercentEncoding
    }
}

struct AIOpenRouterSignIn {
    static let authURL = "https://openrouter.ai/auth"
    let verifier: String
    let state: String

    init(verifier: String = AIPKCE.verifier(), state: String = AIPKCE.state()) {
        self.verifier = verifier
        self.state = state
    }

    /// The sign-in page: with a port, the callback comes to the listener;
    /// with none, OpenRouter shows a code to paste.
    func url(port: Int?) -> String {
        var query: [(String, String)] = []
        if let port { query.append(("callback_url", "http://localhost:\(port)/callback")) }
        query.append(("code_challenge", AIPKCE.challenge(verifier)))
        query.append(("code_challenge_method", "S256"))
        if port != nil { query.append(("state", state)) }
        return Self.authURL + "?" + query.map { $0.0 + "=" + AIPKCE.encode($0.1) }.joined(separator: "&")
    }

    /// The code in the callback's request target (/callback?code=…&state=…),
    /// once its state is checked.
    func code(fromCallback target: String) throws -> String {
        guard let q = target.firstIndex(of: "?"), target[..<q] == "/callback" else { throw AIError.signInRefused }
        var fields: [String: String] = [:]
        for pair in target[target.index(after: q)...].split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard parts.count == 2, let k = AIPKCE.decode(String(parts[0])), let v = AIPKCE.decode(String(parts[1])) else { continue }
            if fields[k] == nil { fields[k] = v }
        }
        guard fields["state"] == state, let code = fields["code"], !code.isEmpty else { throw AIError.signInRefused }
        return code
    }

    func exchangeRequest(code: String) -> AIHTTPRequest {
        let body = AIJSON.make([("code", .string(code)), ("code_verifier", .string(verifier)),
                                ("code_challenge_method", .string("S256"))])
        return AIHTTPRequest(method: "POST", url: AIAccounts.openRouterBase + "/auth/keys",
                             headers: [("Content-Type", "application/json")], body: body.data)
    }

    static func key(from response: AIHTTPResponse) throws -> String {
        guard response.status == 200 else {
            throw AIError.provider(response.status == 429 ? .rateLimited : response.status >= 500 ? .tryLater : .credentialRefused)
        }
        guard let key = AIJSON.parse(response.body)?["key"]?.string, !key.isEmpty else { throw AIError.provider(.tryLater) }
        return key
    }
}

/// Opens the sign-in page: ASWebAuthenticationSession on iOS
/// (AIWebAuthBrowser), a stand-in in tests.
protocol AIBrowser: AnyObject {
    func open(_ url: String, callbackScheme: String)
}

/// Connecting an account: OpenRouter by sign-in or pasted code, the others
/// by pasted key. Each checks the credential with one cheap read before it
/// is kept, and Disconnect deletes it at once.
final class AIConnect {
    /// Where the browser goes once the listener has the code: Sorta itself,
    /// which ends ASWebAuthenticationSession (callback scheme "sorta").
    static let returnURL = "sorta://ai/connected"

    private let vault: AIVault
    private let transport: AITransport

    init(vault: AIVault, transport: AITransport) {
        self.vault = vault
        self.transport = transport
    }

    /// The sign-in with a loopback callback. Blocks until the browser comes
    /// back or `timeout` passes.
    func signInWithOpenRouter(browser: AIBrowser, timeout: TimeInterval = 600) throws {
        try AIThread.requireBackground()
        let listener = try AILoopbackListener()
        defer { listener.close() }
        let signIn = AIOpenRouterSignIn()
        browser.open(signIn.url(port: Int(listener.port)), callbackScheme: "sorta")
        var code: String?
        let deadline = Date().addingTimeInterval(timeout)
        while code == nil {
            let left = deadline.timeIntervalSinceNow
            guard left > 0 else { throw AIError.cancelled }
            let target = try listener.next(timeout: left, redirect: Self.returnURL)
            code = try? signIn.code(fromCallback: target)
        }
        try finish(code: code!, signIn: signIn)
    }

    /// The headless way: the page showed a code, and the person pasted it.
    func openRouter(pastedCode: String, signIn: AIOpenRouterSignIn) throws {
        try AIThread.requireBackground()
        let code = AIText.trim(pastedCode)
        guard !code.isEmpty else { throw AIError.signInRefused }
        try finish(code: code, signIn: signIn)
    }

    private func finish(code: String, signIn: AIOpenRouterSignIn) throws {
        let key = try AIOpenRouterSignIn.key(from: transport.send(signIn.exchangeRequest(code: code)))
        try check(.openrouter, key: key)
        try vault.save(key, for: .openrouter)
    }

    /// A pasted key (Anthropic, OpenAI, or a compatible service at `base`).
    func connect(_ provider: AIProviderID, key pasted: String, base: String? = nil) throws {
        try AIThread.requireBackground()
        let key = AIText.trim(pasted)
        guard !key.isEmpty, !provider.onDevice else { throw AIError.notConnected }
        try check(provider, key: key, base: base)
        try vault.save(key, for: provider)
    }

    func disconnect(_ provider: AIProviderID) throws {
        try vault.delete(provider)
    }

    private func check(_ provider: AIProviderID, key: String, base: String? = nil) throws {
        let given: () throws -> String = { key }
        switch provider {
        case .anthropic:
            try AIAnthropic(transport: transport, credential: given).check()
        case .openrouter:
            try AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: transport, credential: given).check()
        case .openai:
            try AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: transport, credential: given).check()
        case .compatible:
            guard let base = base.flatMap(AIAccounts.compatibleBase) else { throw AIError.insecureAddress }
            try AIChatCompletions(.compatible, base: base, transport: transport, credential: given).check()
        case .apple, .mlkit:
            throw AIError.notConnected
        }
    }
}
