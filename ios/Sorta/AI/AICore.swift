import Foundation

// MARK: - The AI core (S08): providers, the cap, consent and the operations
//
// No screen and no string a person sees (S08u holds the UI). Foundation only,
// so the Linux suites (tools/swift_suites.txt) build it as well; the parts
// that need Apple's frameworks sit behind canImport. Android's
// com.cramdown.sorta.ai is the same rules, read from the same fixtures
// (ai/fixtures).
//
// Every call is synchronous and refuses the main thread (AIThread): the
// frame is 8.3 ms, so callers run it on a background queue.

/// Where an answer comes from: the phone's own model, or an account.
enum AIProviderID: String, CaseIterable, Codable {
    case apple, mlkit, openrouter, anthropic, openai, compatible

    /// The phone's own models: free, and nothing leaves the phone.
    var onDevice: Bool { self == .apple || self == .mlkit }
}

/// The few plain kinds every provider's errors map to.
enum AIErrorKind: String, CaseIterable {
    case credentialRefused, rateLimited, outOfCredit, tryLater, modelUnavailable
}

enum AIError: Error, Equatable {
    /// The provider answered with an error, or the model is not on this phone.
    case provider(AIErrorKind)
    /// No answer reached the phone (offline, timed out); the provider may
    /// still have counted the call.
    case transport
    /// Called on the main thread.
    case mainThread
    /// No account connected, or no credential stored.
    case notConnected
    /// The feature is off, or the person has not agreed to what it sends.
    case notAllowed
    /// The call's worst case does not fit in what is left of the month.
    case capReached
    /// The model has no price in ai/tables/prices.json.
    case unknownPrice
    /// A compatible service's address that is not https.
    case insecureAddress
    /// The sign-in's state did not match, or its answer had no code.
    case signInRefused
    /// The sign-in was not finished in time.
    case cancelled
    /// The Keychain or Keystore refused.
    case vault
}

/// One call: instructions, the input, and maybe a JSON schema for the answer.
struct AIRequest {
    var model: String
    var instructions: String
    var input: String
    /// The schema's name and its JSON text, sent where the provider takes one.
    var schemaName: String? = nil
    var schema: String? = nil
    var maxOutput: Int
}

struct AIUsage: Equatable {
    var inputTokens: Int
    var outputTokens: Int
    /// What the provider says the call cost, in picodollars (OpenRouter).
    var costPico: Int64? = nil
}

struct AIResponse {
    var text: String
    var usage: AIUsage
}

/// One provider, on the phone or behind an account. `complete` blocks, and
/// throws AIError.mainThread on the main thread.
protocol AIProvider: AnyObject {
    var id: AIProviderID { get }
    /// Whether a JSON schema goes with the request (guided generation on
    /// Apple's model; response formats on the accounts that take them).
    var sendsSchema: Bool { get }
    func complete(_ request: AIRequest) throws -> AIResponse
}

enum AIThread {
    /// AI calls, embeddings and index work never run on the main thread.
    static func requireBackground() throws {
        if Thread.isMainThread { throw AIError.mainThread }
    }
}

// MARK: - Text, the same way on both platforms

enum AIText {
    /// The whitespace both apps trim and split on: Unicode's White_Space.
    static func isSpace(_ s: Unicode.Scalar) -> Bool {
        switch s.value {
        case 0x09...0x0D, 0x20, 0x85, 0xA0, 0x1680, 0x2000...0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000: return true
        default: return false
        }
    }

    /// Characters are counted as Unicode scalars (code points), as Kotlin's
    /// codePointCount does.
    static func count(_ s: String) -> Int { s.unicodeScalars.count }

    static func prefix(_ s: String, _ n: Int) -> String {
        var out = String.UnicodeScalarView()
        out.append(contentsOf: s.unicodeScalars.prefix(max(0, n)))
        return String(out)
    }

    static func trim(_ s: String) -> String {
        let scalars = Array(s.unicodeScalars)
        var a = 0, b = scalars.count
        while a < b, isSpace(scalars[a]) { a += 1 }
        while b > a, isSpace(scalars[b - 1]) { b -= 1 }
        var out = String.UnicodeScalarView()
        out.append(contentsOf: scalars[a..<b])
        return String(out)
    }

    static func utf8(_ s: String) -> Int { s.utf8.count }

    /// Whether `part` occurs in `whole`, scalar for scalar (no canonical
    /// equivalence, so both platforms agree on what is a slice).
    static func contains(_ whole: String, _ part: String) -> Bool {
        let w = Array(whole.unicodeScalars), p = Array(part.unicodeScalars)
        guard !p.isEmpty, p.count <= w.count else { return p.isEmpty }
        for start in 0...(w.count - p.count) where w[start] == p[0] {
            var k = 1
            while k < p.count, w[start + k] == p[k] { k += 1 }
            if k == p.count { return true }
        }
        return false
    }
}

// MARK: - JSON, parsed strictly and written the same way on both platforms

/// A JSON value. Parsed by hand rather than with JSONSerialization, so a
/// model's answer gets the same verdict here and on Android (true is never
/// a number, and nothing lenient is accepted).
indirect enum AIJSON: Equatable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([AIJSON])
    case object([String: AIJSON], keys: [String])

    /// An object with its keys in this order.
    static func make(_ pairs: [(String, AIJSON)]) -> AIJSON {
        var map: [String: AIJSON] = [:]
        var keys: [String] = []
        for (k, v) in pairs {
            if map[k] == nil { keys.append(k) }
            map[k] = v
        }
        return .object(map, keys: keys)
    }

    static func == (a: AIJSON, b: AIJSON) -> Bool {
        switch (a, b) {
        case (.null, .null): return true
        case let (.bool(x), .bool(y)): return x == y
        case let (.number(x), .number(y)): return x == y
        case let (.string(x), .string(y)): return Array(x.unicodeScalars) == Array(y.unicodeScalars)
        case let (.array(x), .array(y)): return x == y
        case let (.object(x, _), .object(y, _)):
            guard x.count == y.count else { return false }
            for (k, v) in x { guard let w = y[k], v == w else { return false } }
            return true
        default: return false
        }
    }

    subscript(key: String) -> AIJSON? {
        if case let .object(map, _) = self { return map[key] }
        return nil
    }

    var string: String? { if case let .string(s) = self { return s }; return nil }
    var bool: Bool? { if case let .bool(b) = self { return b }; return nil }
    var number: Double? { if case let .number(d) = self { return d }; return nil }
    var array: [AIJSON]? { if case let .array(a) = self { return a }; return nil }
    var isNull: Bool { if case .null = self { return true }; return false }
    /// A whole number, never a bool.
    var int: Int? {
        guard case let .number(d) = self, d == d.rounded(), abs(d) < 9.0e15 else { return nil }
        return Int(d)
    }

    // MARK: parsing

    static func parse(_ text: String) -> AIJSON? {
        var p = Parser(Array(text.unicodeScalars))
        p.space()
        guard let v = p.value(depth: 0) else { return nil }
        p.space()
        return p.done ? v : nil
    }

    static func parse(_ data: Data) -> AIJSON? {
        guard let text = String(data: data, encoding: .utf8) else { return nil }
        return parse(text)
    }

    private struct Parser {
        let s: [Unicode.Scalar]
        var i = 0
        init(_ s: [Unicode.Scalar]) { self.s = s }
        var done: Bool { i == s.count }

        mutating func space() {
            while i < s.count, [0x20, 0x09, 0x0A, 0x0D].contains(s[i].value) { i += 1 }
        }

        mutating func eat(_ word: String) -> Bool {
            let w = Array(word.unicodeScalars)
            guard i + w.count <= s.count, Array(s[i..<(i + w.count)]) == w else { return false }
            i += w.count
            return true
        }

        mutating func value(depth: Int) -> AIJSON? {
            guard depth < 64, i < s.count else { return nil }
            switch s[i] {
            case "{": return object(depth: depth)
            case "[": return array(depth: depth)
            case "\"": return string().map(AIJSON.string)
            case "t": return eat("true") ? .bool(true) : nil
            case "f": return eat("false") ? .bool(false) : nil
            case "n": return eat("null") ? .null : nil
            default: return number()
            }
        }

        mutating func object(depth: Int) -> AIJSON? {
            i += 1
            var pairs: [(String, AIJSON)] = []
            space()
            if i < s.count, s[i] == "}" { i += 1; return .object([:], keys: []) }
            while true {
                space()
                guard i < s.count, s[i] == "\"", let key = string() else { return nil }
                space()
                guard i < s.count, s[i] == ":" else { return nil }
                i += 1
                space()
                guard let v = value(depth: depth + 1) else { return nil }
                pairs.append((key, v))
                space()
                guard i < s.count else { return nil }
                if s[i] == "," { i += 1; continue }
                if s[i] == "}" { i += 1; return AIJSON.make(pairs) }
                return nil
            }
        }

        mutating func array(depth: Int) -> AIJSON? {
            i += 1
            var items: [AIJSON] = []
            space()
            if i < s.count, s[i] == "]" { i += 1; return .array([]) }
            while true {
                space()
                guard let v = value(depth: depth + 1) else { return nil }
                items.append(v)
                space()
                guard i < s.count else { return nil }
                if s[i] == "," { i += 1; continue }
                if s[i] == "]" { i += 1; return .array(items) }
                return nil
            }
        }

        mutating func hex4() -> UInt32? {
            guard i + 4 <= s.count else { return nil }
            var v: UInt32 = 0
            for _ in 0..<4 {
                guard let d = Int(String(s[i]), radix: 16) else { return nil }
                v = v * 16 + UInt32(d)
                i += 1
            }
            return v
        }

        mutating func string() -> String? {
            i += 1
            var out = String.UnicodeScalarView()
            while i < s.count {
                let c = s[i]
                if c == "\"" { i += 1; return String(out) }
                if c.value < 0x20 { return nil }
                if c != "\\" { out.append(c); i += 1; continue }
                i += 1
                guard i < s.count else { return nil }
                let e = s[i]
                i += 1
                switch e {
                case "\"": out.append("\"")
                case "\\": out.append("\\")
                case "/": out.append("/")
                case "b": out.append(Unicode.Scalar(0x08))
                case "f": out.append(Unicode.Scalar(0x0C))
                case "n": out.append("\n")
                case "r": out.append("\r")
                case "t": out.append("\t")
                case "u":
                    guard let hi = hex4() else { return nil }
                    if (0xD800...0xDBFF).contains(hi) {
                        guard eat("\\u"), let lo = hex4(), (0xDC00...0xDFFF).contains(lo),
                              let u = Unicode.Scalar(0x10000 + ((hi - 0xD800) << 10) + (lo - 0xDC00)) else { return nil }
                        out.append(u)
                    } else {
                        guard let u = Unicode.Scalar(hi) else { return nil }
                        out.append(u)
                    }
                default: return nil
                }
            }
            return nil
        }

        mutating func number() -> AIJSON? {
            let start = i
            func digit(_ k: Int) -> Bool { k < s.count && ("0"..."9").contains(s[k]) }
            if i < s.count, s[i] == "-" { i += 1 }
            guard digit(i) else { return nil }
            if s[i] == "0" { i += 1 } else { while digit(i) { i += 1 } }
            if i < s.count, s[i] == "." {
                i += 1
                guard digit(i) else { return nil }
                while digit(i) { i += 1 }
            }
            if i < s.count, s[i] == "e" || s[i] == "E" {
                i += 1
                if i < s.count, s[i] == "+" || s[i] == "-" { i += 1 }
                guard digit(i) else { return nil }
                while digit(i) { i += 1 }
            }
            var text = String.UnicodeScalarView()
            text.append(contentsOf: s[start..<i])
            return Double(String(text)).map(AIJSON.number)
        }
    }

    // MARK: writing

    /// Compact JSON, keys in the order they were given.
    var text: String {
        var out = ""
        write(&out)
        return out
    }

    var data: Data { Data(text.utf8) }

    private func write(_ out: inout String) {
        switch self {
        case .null: out += "null"
        case let .bool(b): out += b ? "true" : "false"
        case let .number(d):
            if d == d.rounded(), abs(d) < 9.0e15 { out += String(Int64(d)) } else { out += String(d) }
        case let .string(s): AIJSON.quote(s, &out)
        case let .array(items):
            out += "["
            for (k, v) in items.enumerated() {
                if k > 0 { out += "," }
                v.write(&out)
            }
            out += "]"
        case let .object(map, keys):
            out += "{"
            for (k, key) in keys.enumerated() {
                if k > 0 { out += "," }
                AIJSON.quote(key, &out)
                out += ":"
                map[key]!.write(&out)
            }
            out += "}"
        }
    }

    private static func quote(_ s: String, _ out: inout String) {
        out += "\""
        for c in s.unicodeScalars {
            switch c {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if c.value < 0x20 {
                    let h = String(c.value, radix: 16)
                    out += "\\u" + String(repeating: "0", count: 4 - h.count) + h
                } else {
                    out.unicodeScalars.append(c)
                }
            }
        }
        out += "\""
    }

    /// A model's answer, with a ```json fence around it taken off.
    static func answer(_ raw: String) -> AIJSON? {
        var text = AIText.trim(raw)
        if text.hasPrefix("```") {
            var lines = text.components(separatedBy: "\n")
            lines.removeFirst()
            if let last = lines.last, AIText.trim(last) == "```" { lines.removeLast() }
            text = lines.joined(separator: "\n")
        }
        return parse(text)
    }
}
