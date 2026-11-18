import Foundation

// MARK: - Consent, settings and the ledger (S08 A5)
//
// With the phone's own model, or no AI, nothing leaves the phone. With an
// account, a feature sends only when the account is connected, the person
// turned the feature on, and they agreed, once per feature and account, to
// what it sends. Every part is off until turned on.

enum AIFeature: String, CaseIterable, Codable {
    /// Sends: split a capture, the reasons for links, ask, sum up.
    case split, why, ask, sumup
    /// On the phone only: suggested links and resurfacing.
    case links, resurface

    var sends: Bool { self == .split || self == .why || self == .ask || self == .sumup }
}

final class AIConsent {
    private let store: AIKeyValue

    init(store: AIKeyValue) { self.store = store }

    func isOn(_ f: AIFeature) -> Bool { store.string("ai.on." + f.rawValue) == "1" }
    func setOn(_ f: AIFeature, _ on: Bool) { store.set(on ? "1" : nil, "ai.on." + f.rawValue) }

    func agreed(_ f: AIFeature, _ p: AIProviderID) -> Bool { store.string("ai.agreed." + f.rawValue + "." + p.rawValue) == "1" }
    func agree(_ f: AIFeature, _ p: AIProviderID) { store.set("1", "ai.agreed." + f.rawValue + "." + p.rawValue) }
    func withdraw(_ f: AIFeature, _ p: AIProviderID) { store.set(nil, "ai.agreed." + f.rawValue + "." + p.rawValue) }

    /// The three conditions: connected, on, agreed. The phone's own model
    /// needs only the feature on, because nothing leaves the phone.
    func allows(_ f: AIFeature, _ p: AIProviderID, connected: Bool) -> Bool {
        guard connected, isOn(f) else { return false }
        return p.onDevice || !f.sends || agreed(f, p)
    }
}

/// Which provider and model the person chose; nothing chosen is AI off.
final class AISettings {
    private let store: AIKeyValue

    init(store: AIKeyValue) { self.store = store }

    var provider: AIProviderID? {
        get { store.string("ai.provider").flatMap(AIProviderID.init(rawValue:)) }
        set { store.set(newValue?.rawValue, "ai.provider") }
    }

    func model(_ p: AIProviderID) -> String? { store.string("ai.model." + p.rawValue) }
    func setModel(_ model: String?, for p: AIProviderID) { store.set(model, "ai.model." + p.rawValue) }

    /// A compatible service's https address (AIAccounts.compatibleBase).
    var compatibleBase: String? {
        get { store.string("ai.compatible.base") }
        set { store.set(newValue.flatMap(AIAccounts.compatibleBase), "ai.compatible.base") }
    }
}

/// One send: when, to whom, for what, how many ideas and characters, and
/// the estimated cost. No text and no credential.
struct AILedgerEntry: Equatable {
    var time: Double
    var provider: AIProviderID
    var feature: String
    var ideas: Int
    var characters: Int
    var costPico: Int64

    var json: AIJSON {
        .make([("time", .number(time)), ("provider", .string(provider.rawValue)), ("feature", .string(feature)),
               ("ideas", .number(Double(ideas))), ("characters", .number(Double(characters))),
               ("costPico", .number(Double(costPico)))])
    }

    init(time: Double, provider: AIProviderID, feature: String, ideas: Int, characters: Int, costPico: Int64) {
        self.time = time
        self.provider = provider
        self.feature = feature
        self.ideas = ideas
        self.characters = characters
        self.costPico = costPico
    }

    init?(_ j: AIJSON) {
        guard let time = j["time"]?.number, let p = j["provider"]?.string.flatMap(AIProviderID.init(rawValue:)),
              let feature = j["feature"]?.string, let ideas = j["ideas"]?.int, let characters = j["characters"]?.int,
              let cost = j["costPico"]?.number else { return nil }
        self.init(time: time, provider: p, feature: feature, ideas: ideas, characters: characters, costPico: Int64(cost))
    }
}

/// The sends of the last 90 days, kept in a file on the phone. The person
/// can read it and clear it (S08u).
final class AILedger {
    static let days: Double = 90
    private let file: URL
    private let now: () -> Date
    private let lock = NSLock()

    init(file: URL, now: @escaping () -> Date = Date.init) {
        self.file = file
        self.now = now
    }

    func entries() -> [AILedgerEntry] {
        lock.lock(); defer { lock.unlock() }
        return read()
    }

    private func read() -> [AILedgerEntry] {
        guard let data = try? Data(contentsOf: file), let items = AIJSON.parse(data)?.array else { return [] }
        let since = now().timeIntervalSince1970 - Self.days * 86400
        return items.compactMap(AILedgerEntry.init).filter { $0.time >= since }
    }

    func record(_ entry: AILedgerEntry) {
        lock.lock(); defer { lock.unlock() }
        let all = read() + [entry]
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? AIJSON.array(all.map(\.json)).data.write(to: file, options: .atomic)
    }

    func clear() {
        lock.lock(); defer { lock.unlock() }
        try? FileManager.default.removeItem(at: file)
    }
}
