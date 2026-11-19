import Foundation

// MARK: - The monthly cap (S08 A4): a true upper bound, enforced on the phone
//
// Money is counted in picodollars (10^-12 USD) as whole numbers. Before a
// call its worst case must fit in what is left of the month: every UTF-8 byte
// of the instructions, the input and any schema sent, plus 64 for the
// provider's framing, as input tokens (a token is never shorter than a
// byte), and the call's maximum output. After it, the provider's reported
// cost where it gives one (OpenRouter), else tokens times the table's price.
// The month is the calendar month in the phone's time zone.

/// Where the cap, the spend, consent and settings are kept: UserDefaults on
/// the phone (never a credential), memory in tests.
protocol AIKeyValue: AnyObject {
    func string(_ key: String) -> String?
    func set(_ value: String?, _ key: String)
}

final class AIMemoryKeyValue: AIKeyValue {
    private var values: [String: String] = [:]
    private let lock = NSLock()
    init() {}
    func string(_ key: String) -> String? { lock.lock(); defer { lock.unlock() }; return values[key] }
    func set(_ value: String?, _ key: String) { lock.lock(); defer { lock.unlock() }; values[key] = value }
    var all: [String: String] { lock.lock(); defer { lock.unlock() }; return values }
}

final class AIDefaultsKeyValue: AIKeyValue {
    private let defaults: UserDefaults
    init(_ defaults: UserDefaults = .standard) { self.defaults = defaults }
    func string(_ key: String) -> String? { defaults.string(forKey: key) }
    func set(_ value: String?, _ key: String) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}

struct AIPrice: Equatable {
    /// Picodollars per token.
    var input: Int64
    var output: Int64
    var longAbove: Int? = nil
    var longInput: Int64 = 0
    var longOutput: Int64 = 0

    func cost(input tokens: Int, output out: Int) -> Int64 {
        let long = longAbove.map { tokens > $0 } ?? false
        return Int64(tokens) * (long ? longInput : input) + Int64(out) * (long ? longOutput : output)
    }
}

/// ai/tables/prices.json, in US dollars per million tokens.
struct AIPrices {
    private var table: [AIProviderID: [String: AIPrice]] = [:]

    init(json: Data) throws {
        guard let root = AIJSON.parse(json), case let .object(providers, _)? = root["providers"] else { throw AIError.unknownPrice }
        func pico(_ v: AIJSON?) -> Int64 { Int64(((v?.number ?? 0) * 1_000_000).rounded()) }
        for (name, entry) in providers {
            guard let id = AIProviderID(rawValue: name), case let .object(models, _)? = entry["models"] else { continue }
            var prices: [String: AIPrice] = [:]
            for (model, p) in models {
                prices[model] = AIPrice(input: pico(p["input"]), output: pico(p["output"]), longAbove: p["longAbove"]?.int,
                                        longInput: pico(p["longInput"]), longOutput: pico(p["longOutput"]))
            }
            table[id] = prices
        }
    }

    /// Free: the phone's own models and OpenRouter's ":free" ones.
    static func isFree(_ provider: AIProviderID, _ model: String) -> Bool {
        provider.onDevice || (provider == .openrouter && model.hasSuffix(":free"))
    }

    func price(_ provider: AIProviderID, _ model: String) -> AIPrice? {
        if Self.isFree(provider, model) { return AIPrice(input: 0, output: 0) }
        return table[provider]?[model]
    }
}

/// What a call holds against the cap until it is settled.
struct AIReservation {
    let provider: AIProviderID
    let model: String
    let month: String
    let held: Int64
    let price: AIPrice
}

final class AIMoney {
    static let dollar: Int64 = 1_000_000_000_000
    static let floor: Int64 = 1 * dollar
    static let ceiling: Int64 = 20 * dollar
    static let standard: Int64 = 5 * dollar
    /// Input tokens added for the provider's message framing.
    static let framing = 64

    private let prices: AIPrices
    private let store: AIKeyValue
    private let zone: () -> TimeZone
    private let now: () -> Date
    /// One lock for the whole app: two runners over the same settings must
    /// not both pass the cap's check before either counts its call.
    private static let shared = NSLock()
    private var lock: NSLock { Self.shared }

    init(prices: AIPrices, store: AIKeyValue, zone: @escaping () -> TimeZone = { TimeZone.current },
         now: @escaping () -> Date = Date.init) {
        self.prices = prices
        self.store = store
        self.zone = zone
        self.now = now
    }

    /// The worst case of a call: every byte sent as an input token, plus
    /// framing, and the whole of its maximum output.
    static func worstCase(_ r: AIRequest, schemaSent: Bool) -> (input: Int, output: Int) {
        let schema = schemaSent ? AIText.utf8(r.schema ?? "") : 0
        return (AIText.utf8(r.instructions) + AIText.utf8(r.input) + schema + framing, r.maxOutput)
    }

    // MARK: the cap, $1 to $20 a month, $5 to start

    func cap(_ provider: AIProviderID) -> Int64 {
        store.string("ai.cap." + provider.rawValue).flatMap { Int64($0) } ?? Self.standard
    }

    /// Sets the cap, kept between $1 and $20: $20 is the ceiling for every
    /// account, the owner's own Claude key included.
    func setCap(_ pico: Int64, for provider: AIProviderID) {
        store.set(String(min(Self.ceiling, max(Self.floor, pico))), "ai.cap." + provider.rawValue)
    }

    func month() -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone()
        let c = calendar.dateComponents([.year, .month], from: now())
        return String(format: "%04d-%02d", c.year ?? 0, c.month ?? 0)
    }

    /// Spent this month (reserved calls included).
    func spent(_ provider: AIProviderID) -> Int64 {
        lock.lock(); defer { lock.unlock() }
        return spent(provider, month())
    }

    private func spent(_ provider: AIProviderID, _ month: String) -> Int64 {
        guard let text = store.string("ai.spent." + provider.rawValue) else { return 0 }
        let parts = text.split(separator: "=")
        guard parts.count == 2, parts[0] == month, let v = Int64(parts[1]) else { return 0 }
        return v
    }

    private func setSpent(_ provider: AIProviderID, _ month: String, _ value: Int64) {
        store.set(month + "=" + String(max(0, value)), "ai.spent." + provider.rawValue)
    }

    /// Holds the call's worst case, or refuses it: an unknown price, or a
    /// worst case that does not fit, even if its real use would have.
    func reserve(_ provider: AIProviderID, _ request: AIRequest, schemaSent: Bool) throws -> AIReservation {
        guard let price = prices.price(provider, request.model) else { throw AIError.unknownPrice }
        let worst = Self.worstCase(request, schemaSent: schemaSent)
        let held = price.cost(input: worst.input, output: worst.output)
        lock.lock(); defer { lock.unlock() }
        let m = month()
        if AIPrices.isFree(provider, request.model) {
            return AIReservation(provider: provider, model: request.model, month: m, held: 0, price: price)
        }
        let now = spent(provider, m)
        guard now + held <= min(cap(provider), Self.ceiling) else { throw AIError.capReached }
        setSpent(provider, m, now + held)
        return AIReservation(provider: provider, model: request.model, month: m, held: held, price: price)
    }

    /// The call's real cost in place of its worst case: the provider's
    /// reported cost, or tokens times the price.
    @discardableResult
    func settle(_ r: AIReservation, _ usage: AIUsage) -> Int64 {
        let actual = AIPrices.isFree(r.provider, r.model) ? 0
            : (usage.costPico ?? r.price.cost(input: usage.inputTokens, output: usage.outputTokens))
        lock.lock(); defer { lock.unlock() }
        guard r.month == month() else {
            // the month turned during the call: it counts in the new one
            setSpent(r.provider, month(), spent(r.provider, month()) + actual)
            return actual
        }
        setSpent(r.provider, r.month, spent(r.provider, r.month) - r.held + actual)
        return actual
    }

    /// A call the provider refused costs nothing.
    func release(_ r: AIReservation) {
        lock.lock(); defer { lock.unlock() }
        guard r.month == month() else { return }
        setSpent(r.provider, r.month, spent(r.provider, r.month) - r.held)
    }
}
