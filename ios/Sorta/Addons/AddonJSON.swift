import Foundation

// MARK: - Add-ons (S07b): JSON as the add-on API moves it
//
// The engine hands every call's arguments over as JSON text and takes JSON
// text back. Sorta writes it canonically (keys sorted by UTF-16 code unit, no
// spaces, as JavaScript's JSON.stringify writes each value), so iOS, Android
// and the kit's harness give the same bytes (addons/conformance). Foundation
// only: the Linux suites build it too.

nonisolated enum AddonJSON: Equatable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([AddonJSON])
    case object([(String, AddonJSON)])

    static func == (a: AddonJSON, b: AddonJSON) -> Bool {
        switch (a, b) {
        case (.null, .null): return true
        case let (.bool(x), .bool(y)): return x == y
        case let (.number(x), .number(y)): return x == y
        case let (.string(x), .string(y)): return x == y
        case let (.array(x), .array(y)): return x == y
        case let (.object(x), .object(y)):
            return x.count == y.count && zip(x, y).allSatisfy { $0.0 == $1.0 && $0.1 == $1.1 }
        default: return false
        }
    }

    // MARK: reading

    subscript(key: String) -> AddonJSON? {
        if case let .object(pairs) = self { return pairs.last(where: { $0.0 == key })?.1 }
        return nil
    }

    subscript(index: Int) -> AddonJSON? {
        if case let .array(items) = self, index >= 0, index < items.count { return items[index] }
        return nil
    }

    func has(_ key: String) -> Bool { self[key] != nil }

    var string: String? { if case let .string(s) = self { return s }; return nil }
    var bool: Bool? { if case let .bool(b) = self { return b }; return nil }
    var number: Double? { if case let .number(n) = self { return n }; return nil }
    var array: [AddonJSON]? { if case let .array(a) = self { return a }; return nil }
    var isObject: Bool { if case .object = self { return true }; return false }
    var isNull: Bool { self == .null }
    var keys: [String] { if case let .object(p) = self { return p.map(\.0) }; return [] }

    /// A whole number JavaScript holds exactly (Number.isSafeInteger).
    var integer: Int? {
        guard case let .number(n) = self, n.rounded() == n, abs(n) <= 9_007_199_254_740_991 else { return nil }
        return Int(n)
    }

    /// The strings of an array of strings, else nil.
    var strings: [String]? {
        guard let items = array else { return nil }
        var out: [String] = []
        for item in items {
            guard let s = item.string else { return nil }
            out.append(s)
        }
        return out
    }

    // MARK: parsing

    struct ParseError: Error {}

    /// `keyTwice: false` refuses an object that names a key twice (the
    /// package checker's strict JSON); otherwise the last one wins, as JSON.parse.
    static func parse(_ text: String, keyTwice: Bool = true) throws -> AddonJSON {
        var p = Parser(units: Array(text.utf16), keyTwice: keyTwice)
        p.space()
        let v = try p.value(depth: 0)
        p.space()
        guard p.i == p.units.count else { throw ParseError() }
        return v
    }

    static func parse(data: Data) throws -> AddonJSON {
        guard let text = String(data: data, encoding: .utf8) else { throw ParseError() }
        return try parse(text)
    }

    private struct Parser {
        let units: [UInt16]
        var keyTwice = true
        var i = 0

        mutating func space() {
            while i < units.count, [0x20, 0x09, 0x0A, 0x0D].contains(units[i]) { i += 1 }
        }

        mutating func value(depth: Int) throws -> AddonJSON {
            guard depth < 512, i < units.count else { throw ParseError() }
            switch units[i] {
            case 0x7B: // {
                i += 1
                var pairs: [(String, AddonJSON)] = []
                space()
                if i < units.count, units[i] == 0x7D { i += 1; return .object(pairs) }
                while true {
                    space()
                    guard i < units.count, units[i] == 0x22 else { throw ParseError() }
                    let key = try string()
                    space()
                    guard i < units.count, units[i] == 0x3A else { throw ParseError() }
                    i += 1
                    space()
                    let v = try value(depth: depth + 1)
                    if !keyTwice, pairs.contains(where: { $0.0.utf16.elementsEqual(key.utf16) }) { throw ParseError() }
                    pairs.removeAll { $0.0 == key }
                    pairs.append((key, v))
                    space()
                    guard i < units.count else { throw ParseError() }
                    if units[i] == 0x2C { i += 1; continue }
                    if units[i] == 0x7D { i += 1; return .object(pairs) }
                    throw ParseError()
                }
            case 0x5B: // [
                i += 1
                var items: [AddonJSON] = []
                space()
                if i < units.count, units[i] == 0x5D { i += 1; return .array(items) }
                while true {
                    space()
                    items.append(try value(depth: depth + 1))
                    space()
                    guard i < units.count else { throw ParseError() }
                    if units[i] == 0x2C { i += 1; continue }
                    if units[i] == 0x5D { i += 1; return .array(items) }
                    throw ParseError()
                }
            case 0x22: return .string(try string())
            case 0x74: try word("true"); return .bool(true)
            case 0x66: try word("false"); return .bool(false)
            case 0x6E: try word("null"); return .null
            default: return .number(try number())
            }
        }

        mutating func word(_ w: String) throws {
            for u in w.utf16 {
                guard i < units.count, units[i] == u else { throw ParseError() }
                i += 1
            }
        }

        mutating func string() throws -> String {
            i += 1
            var out: [UInt16] = []
            while true {
                guard i < units.count else { throw ParseError() }
                let u = units[i]
                i += 1
                if u == 0x22 { break }
                if u < 0x20 { throw ParseError() }
                if u != 0x5C { out.append(u); continue }
                guard i < units.count else { throw ParseError() }
                let e = units[i]
                i += 1
                switch e {
                case 0x22: out.append(0x22)
                case 0x5C: out.append(0x5C)
                case 0x2F: out.append(0x2F)
                case 0x62: out.append(0x08)
                case 0x66: out.append(0x0C)
                case 0x6E: out.append(0x0A)
                case 0x72: out.append(0x0D)
                case 0x74: out.append(0x09)
                case 0x75:
                    guard i + 4 <= units.count,
                          let v = UInt16(String(utf16CodeUnits: Array(units[i..<i + 4]), count: 4), radix: 16)
                    else { throw ParseError() }
                    out.append(v)
                    i += 4
                default: throw ParseError()
                }
            }
            return String(decoding: out, as: UTF16.self)
        }

        mutating func number() throws -> Double {
            let start = i
            if i < units.count, units[i] == 0x2D { i += 1 }
            func digits() -> Int {
                let s = i
                while i < units.count, units[i] >= 0x30, units[i] <= 0x39 { i += 1 }
                return i - s
            }
            guard i < units.count else { throw ParseError() }
            if units[i] == 0x30 { i += 1 } else if digits() == 0 { throw ParseError() }
            if i < units.count, units[i] == 0x2E {
                i += 1
                guard digits() > 0 else { throw ParseError() }
            }
            if i < units.count, units[i] == 0x65 || units[i] == 0x45 {
                i += 1
                if i < units.count, units[i] == 0x2B || units[i] == 0x2D { i += 1 }
                guard digits() > 0 else { throw ParseError() }
            }
            let text = String(decoding: units[start..<i], as: UTF16.self)
            guard let d = Double(text) else { throw ParseError() }
            return d
        }
    }

    // MARK: writing

    /// Canonical JSON text: keys sorted by UTF-16 code unit, no spaces.
    var canonical: String {
        var out = ""
        write(into: &out)
        return out
    }

    func write(into out: inout String) {
        switch self {
        case .null: out += "null"
        case let .bool(b): out += b ? "true" : "false"
        case let .number(n): out += AddonJSON.number(n)
        case let .string(s): AddonJSON.quote(s, into: &out)
        case let .array(items):
            out += "["
            for (k, item) in items.enumerated() {
                if k > 0 { out += "," }
                item.write(into: &out)
            }
            out += "]"
        case let .object(pairs):
            out += "{"
            for (k, pair) in pairs.sorted(by: { AddonText.less($0.0, $1.0) }).enumerated() {
                if k > 0 { out += "," }
                AddonJSON.quote(pair.0, into: &out)
                out += ":"
                pair.1.write(into: &out)
            }
            out += "}"
        }
    }

    /// A number as JavaScript writes it, for the numbers the API carries.
    static func number(_ n: Double) -> String {
        guard n.isFinite else { return "null" }
        if n.rounded() == n, abs(n) < 1e21 { return String(Int64(n)) }
        var s = "\(n)"
        if let e = s.firstIndex(of: "e") {
            let mantissa = s[..<e]
            var exponent = String(s[s.index(after: e)...])
            if exponent.hasPrefix("-") {
                exponent = "-" + String(Int(exponent.dropFirst()) ?? 0)
            } else {
                exponent = "+" + String(Int(exponent.replacingOccurrences(of: "+", with: "")) ?? 0)
            }
            s = String(mantissa) + "e" + exponent
        }
        return s
    }

    /// A string as JSON.stringify writes it.
    static func quote(_ s: String, into out: inout String) {
        out += "\""
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\u{08}": out += "\\b"
            case "\u{0C}": out += "\\f"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        out += "\""
    }

    static func quoted(_ s: String) -> String {
        var out = ""
        quote(s, into: &out)
        return out
    }

    static func strings(_ list: [String]) -> AddonJSON { .array(list.map { .string($0) }) }
}

// MARK: - text rules every platform shares

nonisolated enum AddonText {
    /// JavaScript's string order: by UTF-16 code unit.
    static func less(_ a: String, _ b: String) -> Bool {
        a.utf16.lexicographicallyPrecedes(b.utf16)
    }

    static func order(_ a: String, _ b: String) -> Int {
        less(a, b) ? -1 : (a == b ? 0 : 1)
    }

    static let space: Set<UInt16> = [0x20, 0x09, 0x0A, 0x0D, 0x0B, 0x0C]

    /// Trimmed of ASCII white space only.
    static func trim(_ s: String) -> String {
        let u = Array(s.utf16)
        var i = 0, j = u.count
        while i < j, space.contains(u[i]) { i += 1 }
        while j > i, space.contains(u[j - 1]) { j -= 1 }
        return String(decoding: u[i..<j], as: UTF16.self)
    }

    /// The words between runs of ASCII white space.
    static func words(_ s: String) -> [String] {
        var out: [String] = []
        var current: [UInt16] = []
        for u in s.utf16 {
            if space.contains(u) {
                if !current.isEmpty { out.append(String(decoding: current, as: UTF16.self)) }
                current = []
            } else {
                current.append(u)
            }
        }
        if !current.isEmpty { out.append(String(decoding: current, as: UTF16.self)) }
        return out
    }

    /// Whether `hay` holds `needle`, unit for unit (JavaScript's includes).
    static func contains(_ hay: [UInt16], _ needle: [UInt16]) -> Bool {
        if needle.isEmpty { return true }
        if needle.count > hay.count { return false }
        let first = needle[0]
        var i = 0
        let last = hay.count - needle.count
        while i <= last {
            if hay[i] == first {
                var k = 1
                while k < needle.count, hay[i + k] == needle[k] { k += 1 }
                if k == needle.count { return true }
            }
            i += 1
        }
        return false
    }

    static func utf8Count(_ s: String) -> Int { s.utf8.count }

    // MARK: times, as Date.prototype.toISOString writes them

    static func iso(_ ms: Int64) -> String {
        var days = ms / 86_400_000
        var rest = ms % 86_400_000
        if rest < 0 { rest += 86_400_000; days -= 1 }
        // days to a civil date (Howard Hinnant's algorithm)
        let z = days + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let day = doy - (153 * mp + 2) / 5 + 1
        let month = mp < 10 ? mp + 3 : mp - 9
        let year = yoe + era * 400 + (month <= 2 ? 1 : 0)
        func two(_ v: Int64) -> String { v < 10 ? "0\(v)" : "\(v)" }
        let ms3 = rest % 1000
        let msText = ms3 < 10 ? "00\(ms3)" : ms3 < 100 ? "0\(ms3)" : "\(ms3)"
        let y = year >= 0 && year <= 9999 ? String(repeating: "0", count: max(0, 4 - "\(year)".count)) + "\(year)"
            : (year < 0 ? "-" : "+") + String(repeating: "0", count: max(0, 6 - "\(abs(year))".count)) + "\(abs(year))"
        return "\(y)-\(two(month))-\(two(day))T\(two(rest / 3_600_000)):\(two(rest / 60000 % 60)):\(two(rest / 1000 % 60)).\(msText)Z"
    }

    /// "YYYY-MM-DDTHH:MM:SS.mmmZ" back to milliseconds; nil for any other form.
    static func parseISO(_ s: String) -> Int64? {
        let u = Array(s.utf8)
        guard u.count == 24, u[4] == 0x2D, u[7] == 0x2D, u[10] == 0x54, u[13] == 0x3A, u[16] == 0x3A,
              u[19] == 0x2E, u[23] == 0x5A else { return nil }
        func num(_ a: Int, _ b: Int) -> Int64? {
            var v: Int64 = 0
            for k in a..<b {
                guard u[k] >= 0x30, u[k] <= 0x39 else { return nil }
                v = v * 10 + Int64(u[k] - 0x30)
            }
            return v
        }
        guard let y = num(0, 4), let m = num(5, 7), let d = num(8, 10), let h = num(11, 13), let mi = num(14, 16),
              let sec = num(17, 19), let ms = num(20, 23), (1...12).contains(m), (1...31).contains(d) else { return nil }
        let yy = m <= 2 ? y - 1 : y
        let era = (yy >= 0 ? yy : yy - 399) / 400
        let yoe = yy - era * 400
        let doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        let days = era * 146_097 + doe - 719_468
        return ((days * 24 + h) * 60 + mi) * 60_000 + sec * 1000 + ms
    }
}
