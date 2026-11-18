import Foundation

// MARK: - The account adapters (S08 A1)
//
// OpenRouter, OpenAI and any OpenAI-compatible service share the chat
// completions shape; Anthropic has its own. Each builds its request and
// parses its answer as pure functions (checked against ai/fixtures/http),
// and reads the credential from the vault at each call, so Disconnect stops
// it at once.

enum AIAccounts {
    static let openRouterBase = "https://openrouter.ai/api/v1"
    static let openAIBase = "https://api.openai.com/v1"
    static let anthropicBase = "https://api.anthropic.com/v1"
    static let anthropicVersion = "2026-10-21"

    /// A compatible service's base address: https only, so a key never
    /// travels in the clear. Nil for anything else.
    static func compatibleBase(_ text: String) -> String? {
        var base = AIText.trim(text)
        while base.hasSuffix("/") { base.removeLast() }
        guard base.lowercased().hasPrefix("https://"), base.count > 8,
              let url = URL(string: base), url.host != nil, url.query == nil, url.fragment == nil else { return nil }
        return base
    }

    /// The plain kind of an error answer (ai/fixtures/http/errors.json).
    static func kind(_ provider: AIProviderID, status: Int, body: Data) -> AIErrorKind {
        let json = AIJSON.parse(body)
        let error = json?["error"]
        let code = error?["code"]?.string ?? ""
        let type = error?["type"]?.string ?? ""
        let message = (error?["message"]?.string ?? "").lowercased()
        switch provider {
        case .anthropic:
            if type == "billing_error" || status == 402 || message.contains("credit balance") { return .outOfCredit }
            switch status {
            case 401, 403: return .credentialRefused
            case 404, 400, 413: return .modelUnavailable
            case 429: return .rateLimited
            default: return .tryLater
            }
        case .openrouter:
            switch status {
            case 401: return .credentialRefused
            case 402, 403: return .outOfCredit
            case 400, 404: return .modelUnavailable
            case 429: return .rateLimited
            default: return .tryLater
            }
        default:
            if code == "insufficient_quota" || type == "insufficient_quota" || status == 402 { return .outOfCredit }
            switch status {
            case 401, 403: return .credentialRefused
            case 400, 404: return .modelUnavailable
            case 429: return .rateLimited
            default: return .tryLater
            }
        }
    }
}

/// OpenRouter, OpenAI, or a compatible service.
final class AIChatCompletions: AIProvider {
    let id: AIProviderID
    let base: String
    private let transport: AITransport
    private let credential: () throws -> String

    var sendsSchema: Bool { id != .compatible }

    init(_ id: AIProviderID, base: String, transport: AITransport, credential: @escaping () throws -> String) {
        self.id = id
        self.base = base
        self.transport = transport
        self.credential = credential
    }

    func request(for r: AIRequest, key: String) -> AIHTTPRequest {
        var body: [(String, AIJSON)] = [("model", .string(r.model))]
        // compatible services take max_tokens; OpenAI and OpenRouter want max_completion_tokens
        body.append((id == .compatible ? "max_tokens" : "max_completion_tokens", .number(Double(r.maxOutput))))
        body.append(("messages", .array([
            .make([("role", .string("system")), ("content", .string(r.instructions))]),
            .make([("role", .string("user")), ("content", .string(r.input))]),
        ])))
        if sendsSchema, let name = r.schemaName, let schema = r.schema.flatMap(AIJSON.parse) {
            body.append(("response_format", .make([
                ("type", .string("json_schema")),
                ("json_schema", .make([("name", .string(name)), ("strict", .bool(true)), ("schema", schema)])),
            ])))
        }
        return AIHTTPRequest(method: "POST", url: base + "/chat/completions",
                             headers: [("Authorization", "Bearer " + key), ("Content-Type", "application/json")],
                             body: AIJSON.make(body).data)
    }

    /// The cheap read on connect: the model list, or OpenRouter's current
    /// key (its model list needs no key, so it would prove nothing).
    func checkRequest(key: String) -> AIHTTPRequest {
        let url = id == .openrouter ? base + "/key" : base + "/models"
        return AIHTTPRequest(method: "GET", url: url, headers: [("Authorization", "Bearer " + key)], body: nil)
    }

    func parse(_ response: AIHTTPResponse) throws -> AIResponse {
        guard response.status == 200 else {
            throw AIError.provider(AIAccounts.kind(id, status: response.status, body: response.body))
        }
        guard let json = AIJSON.parse(response.body), let choice = json["choices"]?.array?.first else {
            throw AIError.provider(.tryLater)
        }
        let text = choice["message"]?["content"]?.string ?? ""
        let usage = json["usage"]
        var cost: Int64? = nil
        if let c = usage?["cost"]?.number { cost = Int64((c * 1e12).rounded()) }
        return AIResponse(text: text, usage: AIUsage(inputTokens: usage?["prompt_tokens"]?.int ?? 0,
                                                     outputTokens: usage?["completion_tokens"]?.int ?? 0, costPico: cost))
    }

    func complete(_ r: AIRequest) throws -> AIResponse {
        try AIThread.requireBackground()
        let key = try credential()
        return try parse(transport.send(request(for: r, key: key)))
    }

    func check() throws {
        try AIThread.requireBackground()
        let response = try transport.send(checkRequest(key: credential()))
        guard response.status == 200 else {
            throw AIError.provider(AIAccounts.kind(id, status: response.status, body: response.body))
        }
    }
}

/// Anthropic's Messages API, by a key from Claude Console.
final class AIAnthropic: AIProvider {
    let id = AIProviderID.anthropic
    let sendsSchema = true
    private let transport: AITransport
    private let credential: () throws -> String

    init(transport: AITransport, credential: @escaping () throws -> String) {
        self.transport = transport
        self.credential = credential
    }

    func request(for r: AIRequest, key: String) -> AIHTTPRequest {
        var body: [(String, AIJSON)] = [
            ("model", .string(r.model)),
            ("max_tokens", .number(Double(r.maxOutput))),
            ("system", .string(r.instructions)),
            ("messages", .array([.make([("role", .string("user")), ("content", .string(r.input))])])),
        ]
        if let schema = r.schema.flatMap(AIJSON.parse) {
            body.append(("output_config", .make([("format", .make([("type", .string("json_schema")), ("schema", schema)]))])))
        }
        return AIHTTPRequest(method: "POST", url: AIAccounts.anthropicBase + "/messages",
                             headers: [("x-api-key", key), ("anthropic-version", AIAccounts.anthropicVersion),
                                       ("Content-Type", "application/json")],
                             body: AIJSON.make(body).data)
    }

    func checkRequest(key: String) -> AIHTTPRequest {
        AIHTTPRequest(method: "GET", url: AIAccounts.anthropicBase + "/models?limit=1",
                      headers: [("x-api-key", key), ("anthropic-version", AIAccounts.anthropicVersion)], body: nil)
    }

    func parse(_ response: AIHTTPResponse) throws -> AIResponse {
        guard response.status == 200 else {
            throw AIError.provider(AIAccounts.kind(.anthropic, status: response.status, body: response.body))
        }
        guard let json = AIJSON.parse(response.body), let content = json["content"]?.array else {
            throw AIError.provider(.tryLater)
        }
        let text = content.filter { $0["type"]?.string == "text" }.compactMap { $0["text"]?.string }.joined()
        let usage = json["usage"]
        return AIResponse(text: text, usage: AIUsage(inputTokens: usage?["input_tokens"]?.int ?? 0,
                                                     outputTokens: usage?["output_tokens"]?.int ?? 0))
    }

    func complete(_ r: AIRequest) throws -> AIResponse {
        try AIThread.requireBackground()
        let key = try credential()
        return try parse(transport.send(request(for: r, key: key)))
    }

    func check() throws {
        try AIThread.requireBackground()
        let response = try transport.send(checkRequest(key: credential()))
        guard response.status == 200 else {
            throw AIError.provider(AIAccounts.kind(.anthropic, status: response.status, body: response.body))
        }
    }
}
