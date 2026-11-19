import Foundation

// MARK: - Running an operation (S08 A4, A5, A6)
//
// Ties the parts together: the chosen provider, consent's three conditions,
// the cap's check before the call and count after it, and the ledger. With
// no account connected and no model on the phone, every operation runs in
// its no-AI form or reports itself off, and nothing is sent.

enum AIOutcome<Value> {
    /// AI is off, the feature is off, or not agreed to: the no-AI form.
    case off
    /// Nothing to do (a capture of one sentence; no candidates).
    case notNeeded
    /// The call failed: the cap, an unknown price, or the provider's error.
    case failed(AIError)
    /// The answer, checked: `ok` carries the value, anything else changes nothing.
    case answered(AIVerdict, Value?)
}

final class AIRunner {
    let vault: AIVault
    let consent: AIConsent
    let settings: AISettings
    let money: AIMoney
    let ledger: AILedger
    let transport: AITransport
    let onDevice: AIProvider?
    let promptRoot: URL
    let suggested: [AIProviderID: String]
    private let now: () -> Date

    init(vault: AIVault, store: AIKeyValue, prices: AIPrices, ledger: AILedger, transport: AITransport,
         onDevice: AIProvider?, root: URL, now: @escaping () -> Date = Date.init) {
        self.vault = vault
        consent = AIConsent(store: store)
        settings = AISettings(store: store)
        money = AIMoney(prices: prices, store: store, now: now)
        self.ledger = ledger
        self.transport = transport
        self.onDevice = onDevice
        promptRoot = root
        self.now = now
        var suggested: [AIProviderID: String] = [:]
        if let data = try? Data(contentsOf: root.appendingPathComponent("tables/models.json")),
           case let .object(map, _)? = AIJSON.parse(data)?["providers"] {
            for (k, v) in map { if let id = AIProviderID(rawValue: k), let m = v["suggested"]?.string { suggested[id] = m } }
        }
        self.suggested = suggested
    }

    /// The provider an operation would use, or nil: AI off, nothing on the
    /// phone, or no credential for the account. It reads the vault, so it
    /// throws on the main thread.
    func provider() throws -> AIProvider? {
        try AIThread.requireBackground()
        guard let id = settings.provider else { return nil }
        if id.onDevice { return onDevice?.id == id ? onDevice : nil }
        guard ((try? vault.read(id)) ?? nil) != nil else { return nil }
        let vault = self.vault
        let credential: () throws -> String = {
            guard let key = try vault.read(id) else { throw AIError.notConnected }
            return key
        }
        switch id {
        case .anthropic: return AIAnthropic(transport: transport, credential: credential)
        case .openrouter: return AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: transport, credential: credential)
        case .openai: return AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: transport, credential: credential)
        case .compatible:
            guard let base = settings.compatibleBase.flatMap(AIAccounts.compatibleBase) else { return nil }
            return AIChatCompletions(.compatible, base: base, transport: transport, credential: credential)
        case .apple, .mlkit: return nil
        }
    }

    /// One call for `feature`, if consent allows it and the cap has room.
    func call(_ feature: AIFeature, prompt name: String, payload: AIPayload, maxOutput: Int) throws -> String {
        try AIThread.requireBackground()
        guard let provider = try provider() else { throw AIError.notConnected }
        let id = provider.id
        guard consent.allows(feature, id, connected: true) else { throw AIError.notAllowed }
        let prompt = try AIPrompt.load(name, root: promptRoot)
        let model = id.onDevice ? "on-device" : (settings.model(id) ?? suggested[id] ?? "")
        let request = AIRequest(model: model, instructions: prompt.instructions, input: payload.input,
                                schemaName: provider.sendsSchema ? prompt.name : nil,
                                schema: provider.sendsSchema ? prompt.schema : nil, maxOutput: maxOutput)
        if id.onDevice { return try provider.complete(request).text }
        let held = try money.reserve(id, request, schemaSent: provider.sendsSchema)
        let response: AIResponse
        do {
            response = try provider.complete(request)
        } catch AIError.transport {
            // no answer came: the provider may have counted it, so the worst case stays
            ledger.record(AILedgerEntry(time: now().timeIntervalSince1970, provider: id, feature: feature.rawValue,
                                        ideas: payload.ideas, characters: payload.characters, costPico: held.held))
            throw AIError.provider(.tryLater)
        } catch {
            money.release(held)
            throw error
        }
        let cost = money.settle(held, response.usage)
        ledger.record(AILedgerEntry(time: now().timeIntervalSince1970, provider: id, feature: feature.rawValue,
                                    ideas: payload.ideas, characters: payload.characters, costPico: cost))
        return response.text
    }

    private func run<V>(_ feature: AIFeature, _ prompt: String, _ payload: AIPayload, _ maxOutput: Int,
                        check: (String) -> (AIVerdict, V?)) -> AIOutcome<V> {
        do {
            let (verdict, value) = check(try call(feature, prompt: prompt, payload: payload, maxOutput: maxOutput))
            return .answered(verdict, verdict == .ok ? value : nil)
        } catch AIError.notConnected {
            return .off
        } catch AIError.notAllowed {
            return .off
        } catch let e as AIError {
            return .failed(e)
        } catch {
            return .failed(.provider(.tryLater))
        }
    }

    /// Whether there is a provider to use. Never asked on the main thread.
    private var connected: Bool { ((try? provider()) ?? nil) != nil }

    private var onDeviceChosen: Bool { settings.provider?.onDevice ?? false }

    // MARK: the operations

    /// Split a capture into ideas; .off means "Sort it" as today.
    func split(_ capture: String, folders: [AISplit.Folder]) -> AIOutcome<[AISplit.Piece]> {
        guard !AIThread.isMain else { return .failed(.mainThread) }
        guard connected, consent.isOn(.split) else { return .off }
        guard AISplit.needed(capture) else { return .notNeeded }
        return run(.split, "split", AISplit.payload(capture: capture, folders: folders), AISplit.maxOutput) {
            let (v, pieces) = AISplit.check($0, capture: capture, folders: folders)
            return (v, pieces)
        }
    }

    /// The reasons behind suggested links (the links themselves are AILinks,
    /// on the phone).
    func why(_ idea: AIIdea, candidates: [AIIdea]) -> AIOutcome<[String: String]> {
        guard !AIThread.isMain else { return .failed(.mainThread) }
        guard connected, consent.isOn(.why) else { return .off }
        guard !candidates.isEmpty else { return .notNeeded }
        let (payload, sent) = AIWhy.payload(idea: idea, candidates: candidates)
        return run(.why, "why", payload, AIWhy.maxOutput) { AIWhy.check($0, sent: sent) }
    }

    /// Ask across ideas already found on the phone.
    func ask(_ question: String, found: [AIIdea]) -> AIOutcome<AIAsk.Answer> {
        guard !AIThread.isMain else { return .failed(.mainThread) }
        guard connected, consent.isOn(.ask) else { return .off }
        guard !found.isEmpty else { return .answered(.nothingFits, nil) }
        let (payload, sent) = AIAsk.payload(question: question, found: found, onDevice: onDeviceChosen)
        return run(.ask, "ask", payload, AIAsk.maxOutput) { AIAsk.check($0, sent: sent) }
    }

    /// Sum up a folder in parts; any part that fails changes nothing.
    func sumUp(folder: String, ideas: [AIIdea]) -> AIOutcome<[AIAsk.Answer]> {
        guard !AIThread.isMain else { return .failed(.mainThread) }
        guard connected, consent.isOn(.sumup) else { return .off }
        let parts = AISumUp.parts(ideas, partChars: onDeviceChosen ? AISumUp.onDevicePart : AISumUp.accountPart)
        guard !parts.isEmpty else { return .notNeeded }
        var answers: [AIAsk.Answer] = []
        for part in parts {
            switch run(.sumup, "sumup", AISumUp.payload(folder: folder, part: part), AISumUp.maxOutput, check: { AISumUp.check($0, part: part) }) {
            case let .answered(.ok, answer?): answers.append(answer)
            case let .answered(verdict, _): return .answered(verdict, nil)
            case .off: return .off
            case .notNeeded: return .notNeeded
            case let .failed(e): return .failed(e)
            }
        }
        return .answered(.ok, answers)
    }
}
