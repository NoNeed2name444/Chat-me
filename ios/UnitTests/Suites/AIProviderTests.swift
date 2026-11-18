import Foundation

// S08 A1 and A2 on Linux: each adapter's request against ai/fixtures/http
// (URL, header names, body JSON) and its parse; the error kinds; the main
// thread refused; Apple's model unavailable cleanly; PKCE's RFC 7636
// vector; the state check; the code exchange; connecting by pasted key or
// code. Android's AIProviderTest and AISignInTest read the same fixtures.

let key1 = "fake-key-for-tests-1"

func call(_ fx: AIJSON, schema: String?) -> AIRequest {
    let c = fx["call"]!
    let name = c["schema"]?.string
    return AIRequest(model: c["model"]!.string!, instructions: c["instructions"]!.string!, input: c["input"]!.string!,
                     schemaName: name, schema: name == nil ? nil : schema, maxOutput: c["maxOutput"]!.int!)
}

let splitSchema = try! String(contentsOf: aiRoot.appendingPathComponent("prompts/split.schema.json"), encoding: .utf8)
let given: () throws -> String = { key1 }

// MARK: requests and answers, per provider

for name in ["openrouter-chat", "openrouter-chat-free", "openai-chat", "compatible-chat", "anthropic-messages"] {
    let fx = fixture("http/\(name).json")
    let t = StandInTransport.replaying(fx)
    let provider: AIProvider
    switch fx["provider"]!.string! {
    case "anthropic": provider = AIAnthropic(transport: t, credential: given)
    case "openrouter": provider = AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: t, credential: given)
    case "openai": provider = AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: t, credential: given)
    default: provider = AIChatCompletions(.compatible, base: AIAccounts.compatibleBase(fx["base"]!.string!)!, transport: t, credential: given)
    }
    let answer = try? offMain { try provider.complete(call(fx, schema: splitSchema)) }
    let (same, why) = t.requests.count == 1 ? matches(t.requests[0], fx) : (false, "\(t.requests.count) requests")
    check("\(name): the request is the fixture's", same, why)
    let expect = fx["expect"]!
    check("\(name): the answer's text", answer?.text == expect["text"]!.string, String(describing: answer?.text))
    check("\(name): the reported use", answer?.usage.inputTokens == expect["inputTokens"]!.int
          && answer?.usage.outputTokens == expect["outputTokens"]!.int
          && answer?.usage.costPico == expect["costPico"]!.number.map { Int64($0) }, String(describing: answer?.usage))
}

for name in ["openrouter-key", "anthropic-models", "openai-models", "compatible-models"] {
    let fx = fixture("http/\(name).json")
    let request: AIHTTPRequest
    switch fx["provider"]!.string! {
    case "anthropic": request = AIAnthropic(transport: StandInTransport(), credential: given).checkRequest(key: key1)
    case "openrouter": request = AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: StandInTransport(), credential: given).checkRequest(key: key1)
    case "openai": request = AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: StandInTransport(), credential: given).checkRequest(key: key1)
    default: request = AIChatCompletions(.compatible, base: fx["base"]!.string!, transport: StandInTransport(), credential: given).checkRequest(key: key1)
    }
    let (same, why) = matches(request, fx)
    check("\(name): the cheap read on connect is the fixture's", same, why)
}

// MARK: errors map to a few plain kinds

let errors = fixture("http/errors.json")
if case let .object(byProvider, _)? = errors["cases"] {
    for (name, cases) in byProvider {
        let id = AIProviderID(rawValue: name)!
        for c in cases.array! {
            let status = c["status"]!.int!
            let kind = AIAccounts.kind(id, status: status, body: c["body"]!.data)
            check("errors: \(name) \(status) \(c["body"]!.text.prefix(60)) is \(c["kind"]!.string!)", kind.rawValue == c["kind"]!.string, kind.rawValue)
            let t = StandInTransport { _ in AIHTTPResponse(status: status, body: c["body"]!.data) }
            let provider: AIProvider = id == .anthropic ? AIAnthropic(transport: t, credential: given)
                : AIChatCompletions(id, base: "https://compatible.example/v1", transport: t, credential: given)
            var thrown: Error?
            do { _ = try offMain { try provider.complete(AIRequest(model: "m", instructions: "i", input: "x", maxOutput: 5)) } } catch { thrown = error }
            check("errors: \(name) \(status) is thrown as that kind", (thrown as? AIError) == .provider(kind), String(describing: thrown))
        }
    }
}
check("errors: every provider has its cases", errors["cases"]?["anthropic"]?.array?.count == 9
      && errors["cases"]?["openrouter"]?.array?.count == 9 && errors["cases"]?["openai"]?.array?.count == 6)

do {
    let t = StandInTransport { _ in throw AIError.transport }
    let p = AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: t, credential: given)
    var thrown: Error?
    do { _ = try offMain { try p.complete(AIRequest(model: "m", instructions: "i", input: "x", maxOutput: 5)) } } catch { thrown = error }
    check("errors: no answer at all is a transport failure", (thrown as? AIError) == .transport, String(describing: thrown))
}

// MARK: the main thread, and the phone's own model

do {
    let t = StandInTransport.replaying(fixture("http/openai-chat.json"))
    let p = AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: t, credential: given)
    var thrown: Error?
    do { _ = try p.complete(AIRequest(model: "m", instructions: "i", input: "x", maxOutput: 5)) } catch { thrown = error }
    check("main thread: a call on it is refused", (thrown as? AIError) == .mainThread && t.requests.isEmpty, String(describing: thrown))
    let apple = AIAppleOnDevice()
    check("on device: Apple's model is unavailable here", !apple.isAvailable)
    var unavailable: Error?
    do { _ = try offMain { try apple.complete(AIRequest(model: "on-device", instructions: "i", input: "x", maxOutput: 5)) } } catch { unavailable = error }
    check("on device: a call reports it cleanly", (unavailable as? AIError) == .provider(.modelUnavailable), String(describing: unavailable))
    var onMain: Error?
    do { _ = try apple.complete(AIRequest(model: "on-device", instructions: "i", input: "x", maxOutput: 5)) } catch { onMain = error }
    check("on device: and refuses the main thread first", (onMain as? AIError) == .mainThread, String(describing: onMain))
}

// MARK: compatible services: https only

check("compatible: an https address is taken, its slash dropped", AIAccounts.compatibleBase("https://llm.example/v1/") == "https://llm.example/v1")
check("compatible: http is refused", AIAccounts.compatibleBase("http://192.168.1.20:11434/v1") == nil)
check("compatible: nonsense is refused", AIAccounts.compatibleBase("llm.example") == nil && AIAccounts.compatibleBase("https://") == nil)

// MARK: PKCE (RFC 7636, Appendix B)

let rfcBytes: [UInt8] = [116, 24, 223, 180, 151, 153, 224, 37, 79, 250, 96, 125, 216, 173, 187, 186, 22, 212, 37, 77,
                         105, 214, 191, 240, 91, 88, 5, 88, 83, 132, 141, 121]
check("PKCE: RFC 7636's verifier from its bytes", AIPKCE.verifier(rfcBytes) == "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
check("PKCE: and its S256 challenge", AIPKCE.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
check("PKCE: a new verifier is 43 characters of base64url", {
    let v = AIPKCE.verifier()
    return v.count == 43 && v.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" } && v != AIPKCE.verifier()
}())
check("SHA-256: the plain one agrees on FIPS 180's vectors",
      AISHA256.Plain.digest([]).map { String(format: "%02x", $0) }.joined() == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      && AISHA256.Plain.digest(Array("abc".utf8)).map { String(format: "%02x", $0) }.joined() == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
check("SHA-256: and over more than one block", AISHA256.Plain.digest(Array(String(repeating: "a", count: 1000).utf8))
      == AISHA256.digest(Array(String(repeating: "a", count: 1000).utf8)))

// MARK: the sign-in URL, the state, the exchange

let signIn = AIOpenRouterSignIn(verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", state: "fake-state-1")
check("sign-in: the URL carries the localhost callback, the challenge and the state",
      signIn.url(port: 51234) == "https://openrouter.ai/auth?callback_url=http%3A%2F%2Flocalhost%3A51234%2Fcallback"
        + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&state=fake-state-1",
      signIn.url(port: 51234))
check("sign-in: headless, no callback and no state: the page shows a code",
      signIn.url(port: nil) == "https://openrouter.ai/auth?code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")
check("sign-in: the callback's code once its state matches",
      (try? signIn.code(fromCallback: "/callback?code=fake-code-for-tests-1&state=fake-state-1")) == "fake-code-for-tests-1")
check("sign-in: a wrong state is refused", (try? signIn.code(fromCallback: "/callback?code=c&state=other")) == nil)
check("sign-in: a missing state is refused", (try? signIn.code(fromCallback: "/callback?code=c")) == nil)
check("sign-in: no code is refused", (try? signIn.code(fromCallback: "/callback?state=fake-state-1")) == nil)
check("sign-in: another path is refused", (try? signIn.code(fromCallback: "/elsewhere?code=c&state=fake-state-1")) == nil)
check("sign-in: a fresh state each time", AIPKCE.state() != AIPKCE.state())

let exchange = fixture("http/openrouter-exchange.json")
do {
    let (same, why) = matches(signIn.exchangeRequest(code: exchange["code"]!.string!), exchange)
    check("exchange: the request is the fixture's", same, why)
    let r = exchange["response"]!
    check("exchange: the key comes back", (try? AIOpenRouterSignIn.key(from: AIHTTPResponse(status: 200, body: r["body"]!.data))) == "fake-key-for-tests-2")
    let refused = exchange["refused"]!
    var thrown: Error?
    do { _ = try AIOpenRouterSignIn.key(from: AIHTTPResponse(status: refused["status"]!.int!, body: refused["body"]!.data)) } catch { thrown = error }
    check("exchange: a used or expired code is refused", (thrown as? AIError) == .provider(.credentialRefused), String(describing: thrown))
}

// MARK: connecting, then disconnecting

do {
    let vault = AIMemoryVault()
    let t = StandInTransport { r in
        if r.url.hasSuffix("/auth/keys") { return AIHTTPResponse(status: 200, body: exchange["response"]!["body"]!.data) }
        if r.url.hasSuffix("/key") { return AIHTTPResponse(status: r.header("Authorization") == "Bearer fake-key-for-tests-2" ? 200 : 401, body: Data("{}".utf8)) }
        return AIHTTPResponse(status: 404, body: Data())
    }
    let connect = AIConnect(vault: vault, transport: t)
    try? offMain { try connect.openRouter(pastedCode: " fake-code-for-tests-1\n", signIn: signIn) }
    check("connect: a pasted code is exchanged, checked and kept", (try? vault.read(.openrouter)) == "fake-key-for-tests-2"
          && t.requests.map(\.url) == ["https://openrouter.ai/api/v1/auth/keys", "https://openrouter.ai/api/v1/key"], "\(t.requests.map(\.url))")
    try? connect.disconnect(.openrouter)
    check("disconnect: the credential goes at once", ((try? vault.read(.openrouter)) ?? nil) == nil)

    let refusing = StandInTransport { _ in AIHTTPResponse(status: 401, body: Data("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"x\"}}".utf8)) }
    var thrown: Error?
    do { try offMain { try AIConnect(vault: vault, transport: refusing).connect(.anthropic, key: key1) } } catch { thrown = error }
    check("connect: a key the provider refuses is not kept", (thrown as? AIError) == .provider(.credentialRefused)
          && ((try? vault.read(.anthropic)) ?? nil) == nil, String(describing: thrown))

    let ok = StandInTransport { _ in AIHTTPResponse(status: 200, body: Data("{}".utf8)) }
    try? offMain { try AIConnect(vault: vault, transport: ok).connect(.anthropic, key: key1) }
    check("connect: a pasted key is checked with the model list, then kept", ((try? vault.read(.anthropic)) ?? nil) == key1
          && ok.requests.first?.url == "https://api.anthropic.com/v1/models?limit=1")
    var insecure: Error?
    do { try offMain { try AIConnect(vault: vault, transport: ok).connect(.compatible, key: key1, base: "http://llm.example/v1") } } catch { insecure = error }
    check("connect: a compatible service over http is refused before anything is sent", (insecure as? AIError) == .insecureAddress
          && ok.requests.count == 1, String(describing: insecure))
    try? offMain { try AIConnect(vault: vault, transport: ok).connect(.compatible, key: key1, base: "https://compatible.example/v1") }
    check("connect: a compatible service by https is checked at its own models", ok.requests.last?.url == "https://compatible.example/v1/models")
}

finish("AI PROVIDER")
