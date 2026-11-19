import XCTest
@testable import Sorta

/// S08 A1 and A2 on the simulator: URLSession's own transport against a
/// stand-in URLProtocol replaying ai/fixtures/http, the loopback listener on
/// 127.0.0.1 and ::1, the whole OpenRouter sign-in with a stand-in browser,
/// and Apple's model reporting itself unavailable cleanly. No network.
final class AINetworkTests: XCTestCase {
    override func tearDown() {
        AIStandInProtocol.seen = []
        AIStandInProtocol.answer = { _ in .failure(URLError(.notConnectedToInternet)) }
    }

    private func transport() -> AIURLSessionTransport {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AIStandInProtocol.self]
        return AIURLSessionTransport(configuration: configuration)
    }

    func testURLSessionCarriesTheFixtureRequestAndParsesItsAnswer() throws {
        let fx = try AIFiles.fixture("http/openrouter-chat.json")
        let call = try XCTUnwrap(fx["call"])
        AIStandInProtocol.answer = { _ in .success((fx["response"]!["status"]!.int!, fx["response"]!["body"]!.data)) }
        let provider = AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: transport(),
                                         credential: { try XCTUnwrap(fx["credential"]?.string) })
        let name = call["schema"]?.string
        let schema = try name.map { try String(contentsOf: AIFiles.ai.appendingPathComponent("prompts/\($0).schema.json"), encoding: .utf8) }
        let request = AIRequest(model: call["model"]!.string!, instructions: call["instructions"]!.string!,
                                input: call["input"]!.string!, schemaName: name, schema: schema, maxOutput: call["maxOutput"]!.int!)
        let answer = try aiOffMain { try provider.complete(request) }
        XCTAssertEqual(answer.text, fx["expect"]!["text"]!.string)
        XCTAssertEqual(answer.usage, AIUsage(inputTokens: fx["expect"]!["inputTokens"]!.int!, outputTokens: fx["expect"]!["outputTokens"]!.int!,
                                             costPico: Int64(fx["expect"]!["costPico"]!.number!)))
        let seen = try XCTUnwrap(AIStandInProtocol.seen.first)
        let want = fx["request"]!
        XCTAssertEqual(seen.request.httpMethod, want["method"]!.string)
        XCTAssertEqual(seen.request.url?.absoluteString, want["url"]!.string)
        if case let .object(headers, _)? = want["headers"] {
            for (name, value) in headers { XCTAssertEqual(seen.request.value(forHTTPHeaderField: name), value.string, name) }
        }
        XCTAssertEqual(seen.body.flatMap(AIJSON.parse), want["body"], "the body, as JSON")
    }

    func testURLSessionErrorsMapToThePlainKinds() throws {
        let provider = AIChatCompletions(.openai, base: AIAccounts.openAIBase, transport: transport(), credential: { AIFiles.fakeKey })
        let request = AIRequest(model: "gpt-6-luna", instructions: "Sort.", input: "Paint the shed.", maxOutput: 10)
        AIStandInProtocol.answer = { _ in .success((401, Data("{\"error\":{\"message\":\"Incorrect API key provided\"}}".utf8))) }
        XCTAssertThrowsError(try aiOffMain { try provider.complete(request) }) { XCTAssertEqual($0 as? AIError, .provider(.credentialRefused)) }
        AIStandInProtocol.answer = { _ in .success((429, Data("{\"error\":{\"code\":\"rate_limit_exceeded\"}}".utf8))) }
        XCTAssertThrowsError(try aiOffMain { try provider.complete(request) }) { XCTAssertEqual($0 as? AIError, .provider(.rateLimited)) }
        AIStandInProtocol.answer = { _ in .failure(URLError(.notConnectedToInternet)) }
        XCTAssertThrowsError(try aiOffMain { try provider.complete(request) }) { XCTAssertEqual($0 as? AIError, .transport) }
        XCTAssertThrowsError(try provider.complete(request), "the main thread is refused") { XCTAssertEqual($0 as? AIError, .mainThread) }
        XCTAssertEqual(AIStandInProtocol.seen.count, 3, "nothing sent from the main thread")
    }

    /// The real URLSession transport, with no stand-in protocol, against two
    /// listeners on this machine: the first answers 302 to the second.
    func testARedirectIsNotFollowedAndTheKeyGoesNowhereElse() throws {
        let first = try AILoopbackListener(), second = try AILoopbackListener()
        defer { first.close(); second.close() }
        let moved = "http://127.0.0.1:\(second.port)/callback?moved=1"
        let served = AIBox<String>()
        let ready = DispatchSemaphore(value: 0)
        Thread {
            served.result = Result { try first.next(timeout: 20, redirect: moved) }
            ready.signal()
        }.start()
        let transport = AIURLSessionTransport()
        let request = AIHTTPRequest(method: "GET", url: "http://127.0.0.1:\(first.port)/callback?first=1",
                                    headers: [("Authorization", "Bearer " + AIFiles.fakeKey)], body: nil)
        let answer = try aiOffMain { try transport.send(request) }
        XCTAssertEqual(ready.wait(timeout: .now() + 20), .success)
        XCTAssertEqual(try XCTUnwrap(served.result).get(), "/callback?first=1", "the first address was asked once")
        XCTAssertEqual(answer.status, 302, "the 3xx answer comes back as it is")
        XCTAssertThrowsError(try aiOffMain { try second.next(timeout: 1.5, redirect: AIConnect.returnURL) },
                             "no second request, so the key goes nowhere else") { XCTAssertEqual($0 as? AIError, .cancelled) }

        AIStandInProtocol.answer = { _ in .success((307, Data())) }
        let provider = AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: self.transport(), credential: { AIFiles.fakeKey })
        XCTAssertThrowsError(try aiOffMain { try provider.complete(AIRequest(model: "m", instructions: "i", input: "x", maxOutput: 5)) }) {
            XCTAssertEqual($0 as? AIError, .provider(.tryLater))
        }
    }

    func testTheListenerAnswersOnBothLoopbackAddresses() throws {
        let listener = try AILoopbackListener()
        defer { listener.close() }
        XCTAssertTrue((49152...65000).contains(listener.port))
        for host in ["127.0.0.1", "::1"] {
            let box = AIBox<String>()
            let done = DispatchSemaphore(value: 0)
            Thread {
                box.result = Result { try aiLoopbackGet(host, port: listener.port, target: "/favicon.ico") + "\u{0}"
                    + aiLoopbackGet(host, port: listener.port, target: "/callback?code=fake-code-for-tests-1&state=s") }
                done.signal()
            }.start()
            let target = try aiOffMain { try listener.next(timeout: 20, redirect: AIConnect.returnURL) }
            XCTAssertEqual(done.wait(timeout: .now() + 20), .success)
            let answers = try XCTUnwrap(box.result).get().components(separatedBy: "\u{0}")
            XCTAssertEqual(target, "/callback?code=fake-code-for-tests-1&state=s", host)
            XCTAssertTrue(answers[0].hasPrefix("HTTP/1.1 404 "), "\(host): another path gets a 404")
            XCTAssertTrue(answers[1].hasPrefix("HTTP/1.1 302 "), "\(host): the callback gets a redirect")
            XCTAssertTrue(answers[1].contains("\r\nLocation: sorta://ai/connected\r\n"), host)
            XCTAssertTrue(answers[1].hasSuffix("\r\n\r\n"), "\(host): no body")
        }
        XCTAssertThrowsError(try aiOffMain { try listener.next(timeout: 0.3, redirect: AIConnect.returnURL) }) {
            XCTAssertEqual($0 as? AIError, .cancelled, "nothing in time is cancelled")
        }
    }

    /// The browser a test drives: it reads the sign-in URL and comes back to
    /// the listener's callback, as OpenRouter's page would.
    private final class StandInBrowser: AIBrowser {
        let host: String
        let wrongStateFirst: Bool
        var opened: String?
        var answers: [String] = []
        init(host: String, wrongStateFirst: Bool = false) { self.host = host; self.wrongStateFirst = wrongStateFirst }

        func open(_ url: String, callbackScheme: String) {
            opened = url
            XCTAssertEqual(callbackScheme, "sorta")
            let query = Dictionary(uniqueKeysWithValues: (URLComponents(string: url)?.queryItems ?? []).map { ($0.name, $0.value ?? "") })
            guard let callback = query["callback_url"].flatMap(URLComponents.init(string:)), let port = callback.port,
                  let state = query["state"] else { return XCTFail("no callback in \(url)") }
            XCTAssertEqual(callback.host, "localhost")
            Thread {
                if self.wrongStateFirst {
                    self.answers.append((try? aiLoopbackGet(self.host, port: UInt16(port), target: "/callback?code=fake-code-for-tests-0&state=wrong")) ?? "")
                }
                self.answers.append((try? aiLoopbackGet(self.host, port: UInt16(port),
                                                        target: "/callback?code=fake-code-for-tests-1&state=" + AIPKCE.encode(state))) ?? "")
            }.start()
        }
    }

    func testSignInWithOpenRouterEndToEnd() throws {
        let exchange = try AIFiles.fixture("http/openrouter-exchange.json")
        let keyCheck = try AIFiles.fixture("http/openrouter-key.json")
        for (host, wrongFirst) in [("127.0.0.1", true), ("::1", false)] {
            let vault = AIMemoryVault()
            let net = AIStandInTransport { request in
                request.url.hasSuffix("/auth/keys") ? AIStandInTransport.response(exchange) : AIStandInTransport.response(keyCheck)
            }
            let browser = StandInBrowser(host: host, wrongStateFirst: wrongFirst)
            try aiOffMain { try AIConnect(vault: vault, transport: net).signInWithOpenRouter(browser: browser, timeout: 30) }
            XCTAssertEqual(try vault.read(.openrouter), exchange["response"]!["body"]!["key"]!.string, host)
            let page = try XCTUnwrap(browser.opened.flatMap(URLComponents.init(string:)))
            let query = Dictionary(uniqueKeysWithValues: (page.queryItems ?? []).map { ($0.name, $0.value ?? "") })
            XCTAssertEqual(page.host, "openrouter.ai")
            XCTAssertEqual(query["code_challenge_method"], "S256")
            XCTAssertEqual(net.requests.count, 2, "the exchange, then one cheap read")
            let sent = try XCTUnwrap(net.requests.first?.body.flatMap(AIJSON.parse))
            XCTAssertEqual(sent["code"]?.string, "fake-code-for-tests-1", "\(host): the code with the right state")
            let verifier = try XCTUnwrap(sent["code_verifier"]?.string)
            XCTAssertEqual(AIPKCE.challenge(verifier), query["code_challenge"], "the verifier matches the challenge")
            XCTAssertEqual(net.requests.last?.url, "https://openrouter.ai/api/v1/key")
            XCTAssertTrue(browser.answers.last?.contains("Location: sorta://ai/connected") ?? false, "back to Sorta")
        }
    }

    func testAPastedCodeWorksWithoutTheListener() throws {
        let exchange = try AIFiles.fixture("http/openrouter-exchange.json")
        let keyCheck = try AIFiles.fixture("http/openrouter-key.json")
        let vault = AIMemoryVault()
        let net = AIStandInTransport { $0.url.hasSuffix("/auth/keys") ? AIStandInTransport.response(exchange) : AIStandInTransport.response(keyCheck) }
        let signIn = AIOpenRouterSignIn()
        XCTAssertFalse(signIn.url(port: nil).contains("callback_url"))
        try aiOffMain { try AIConnect(vault: vault, transport: net).openRouter(pastedCode: " fake-code-for-tests-1 \n", signIn: signIn) }
        XCTAssertEqual(try vault.read(.openrouter), exchange["response"]!["body"]!["key"]!.string)
    }

    func testApplesModelReportsItselfUnavailableCleanly() throws {
        let model = AIAppleOnDevice()
        let request = AIRequest(model: "on-device", instructions: "Sort.", input: "Paint the shed. Plant beans.", maxOutput: 50)
        XCTAssertThrowsError(try model.complete(request)) { XCTAssertEqual($0 as? AIError, .mainThread) }
        if model.isAvailable {
            // a Mac with Apple Intelligence: the model answers, or fails as a provider would
            do { _ = try aiOffMain { try model.complete(request) } } catch { XCTAssertNotEqual(error as? AIError, .mainThread) }
        } else {
            XCTAssertThrowsError(try aiOffMain { try model.complete(request) }) {
                XCTAssertEqual($0 as? AIError, .provider(.modelUnavailable), "CI's simulators lack the model")
            }
        }
    }
}
