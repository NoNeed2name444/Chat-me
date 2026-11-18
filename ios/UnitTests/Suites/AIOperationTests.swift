import Foundation

// S08 A5 and A6 on Linux: consent's three conditions, each payload's least
// (ideas and characters counted), the ledger, zero requests with nothing
// connected, the verdicts on ai/fixtures/model (Android's AIOperationTest
// must reach the same), the prompts' SHA-256, and a planted fake credential
// that never leaves the vault.

let fakeKey = "fake-key-for-tests-1"
let prices = try! AIPrices(json: Data(contentsOf: aiRoot.appendingPathComponent("tables/prices.json")))

func ideas(_ j: AIJSON) -> [AIIdea] {
    j.array!.map { AIIdea(id: $0["id"]!.string!, title: $0["title"]!.string!, text: $0["text"]!.string!) }
}

// MARK: the model answers (A6)

let split = fixture("model/split.json")
let capture = split["capture"]!.string!
let folders = split["folders"]!.array!.map {
    AISplit.Folder(id: $0["id"]!.string!, name: $0["name"]!.string!, words: $0["words"]!.array!.map { $0.string! })
}
for c in split["cases"]!.array! {
    let (v, pieces) = AISplit.check(c["answer"]!.string!, capture: capture, folders: folders)
    let want = c["pieces"]?.array?.map { AISplit.Piece(text: $0["text"]!.string!, title: $0["title"]!.string!, folder: $0["folder"]!.string) } ?? []
    check("split: \(c["name"]!.string!)", v.rawValue == c["verdict"]!.string && pieces == want, "\(v) \(pieces)")
}

let why = fixture("model/why.json")
let whySent = ideas(why["candidates"]!)
for c in why["cases"]!.array! {
    let (v, reasons) = AIWhy.check(c["answer"]!.string!, sent: whySent)
    var want: [String: String] = [:]
    if case let .object(map, _)? = c["reasons"] { for (k, r) in map { want[k] = r.string! } }
    check("why: \(c["name"]!.string!)", v.rawValue == c["verdict"]!.string && reasons == want, "\(v) \(reasons)")
}

let ask = fixture("model/ask.json")
let askIdeas = ideas(ask["ideas"]!)
for c in ask["cases"]!.array! {
    let (v, answer) = AIAsk.check(c["answer"]!.string!, sent: askIdeas)
    let want = c["text"].map { AIAsk.Answer(text: $0.string!, cites: c["cites"]!.array!.map { $0.string! }) }
    check("ask: \(c["name"]!.string!)", v.rawValue == c["verdict"]!.string && answer == want, "\(v) \(String(describing: answer))")
}

let sumup = fixture("model/sumup.json")
let sumIdeas = ideas(sumup["ideas"]!)
let sumPart = sumIdeas.enumerated().map { (n: $0.offset + 1, idea: $0.element) }
for c in sumup["cases"]!.array! {
    let (v, answer) = AISumUp.check(c["answer"]!.string!, part: sumPart)
    let want = c["text"].map { AIAsk.Answer(text: $0.string!, cites: c["cites"]!.array!.map { $0.string! }) }
    check("sum up: \(c["name"]!.string!)", v.rawValue == c["verdict"]!.string && answer == want, "\(v) \(String(describing: answer))")
}

check("citations: markers in order, lists split", AICitations.markers("a [2] b [1, 3] c [x] [] [4") == [2, 1, 3])
check("answers: a ```json fence is taken off", AIJSON.answer("```json\n{\"a\": 1}\n```")?["a"]?.int == 1)

// MARK: sentences (split runs only on more than one)

check("sentences: one", AISplit.sentences("Paint the shed blue.") == 1 && !AISplit.needed("Paint the shed blue."))
check("sentences: two with a stop and a space", AISplit.sentences("Paint the shed. Plant beans.") == 2)
check("sentences: a line break ends one", AISplit.sentences("Paint the shed\nPlant beans") == 2)
check("sentences: a decimal point does not", AISplit.sentences("Version 2.5 is out") == 1)
check("sentences: Arabic", AISplit.sentences("اطلِ السقيفة. ازرع الفول.") == 2)
check("sentences: none in an empty capture", AISplit.sentences("  \n ") == 0)
check("sentences: the fixture's capture has three", AISplit.sentences(capture) == 3)

// MARK: each payload's least (A5)

let sp = AISplit.payload(capture: capture, folders: folders)
let spJSON = AIJSON.parse(Data(sp.input.utf8))!
if case let .object(map, keys)? = Optional(spJSON) {
    check("split sends: the capture and the folders, nothing else", keys == ["capture", "folders"] && map["capture"]?.string == capture)
}
check("split sends: each folder's name and words only, no id",
      spJSON["folders"]!.array!.allSatisfy { if case let .object(_, k) = $0 { return k == ["name", "words"] } else { return false } })
check("split sends: no other idea, and the characters counted",
      sp.ideas == 0 && sp.characters == AIText.count(capture) + 6 + 6 + 5 + 5 + 5 + 5 + 7, "\(sp.characters)")

let long = String(repeating: "مر😀a", count: 400)   // 1,600 scalars of mixed scripts
let manyCandidates = (1...8).map { AIIdea(id: "c\($0)", title: "Candidate \($0)", text: long) }
let (wp, wsent) = AIWhy.payload(idea: AIIdea(id: "n", title: "New idea", text: long), candidates: manyCandidates)
let wJSON = AIJSON.parse(Data(wp.input.utf8))!
check("why sends: the idea's first 500 characters", AIText.count(wJSON["idea"]!["text"]!.string!) == 500)
check("why sends: 5 candidates of 8", wsent.map(\.id) == ["c1", "c2", "c3", "c4", "c5"] && wJSON["candidates"]!.array!.count == 5)
check("why sends: each candidate's first 200 characters",
      wJSON["candidates"]!.array!.allSatisfy { AIText.count($0["text"]!.string!) == 200 && $0["title"] != nil && $0["id"] == nil })
check("why sends: 6 ideas and their characters counted", wp.ideas == 6 && wp.characters == 8 + 500 + 5 * (11 + 200), "\(wp.characters)")

let found = (1...15).map { AIIdea(id: "i\($0)", title: "Idea \($0)", text: long) }
let (ap, asent) = AIAsk.payload(question: "What fits?", found: found, onDevice: false)
let aJSON = AIJSON.parse(Data(ap.input.utf8))!
check("ask sends: 12 ideas of 15", asent.count == 12 && aJSON["ideas"]!.array!.count == 12 && ap.ideas == 12)
check("ask sends: up to 1,000 characters each", aJSON["ideas"]!.array!.allSatisfy { AIText.count($0["text"]!.string!) == 1000 })
check("ask sends: the characters counted",
      ap.characters == 10 + found.prefix(12).reduce(0) { $0 + AIText.count($1.title) } + 12 * 1000, "\(ap.characters)")
let (_, onPhone) = AIAsk.payload(question: "What fits?", found: found, onDevice: true)
check("ask sends: 6 to the phone's own model", onPhone.count == 6)

let folderIdeas = (1...40).map { AIIdea(id: "s\($0)", title: "Idea \($0)", text: String(long.unicodeScalars.prefix(990).map(Character.init))) }
let accountParts = AISumUp.parts(folderIdeas, partChars: AISumUp.accountPart)
let phoneParts = AISumUp.parts(folderIdeas, partChars: AISumUp.onDevicePart)
func total(_ parts: [[(n: Int, idea: AIIdea)]]) -> Int { parts.joined().reduce(0) { $0 + AIText.count($1.idea.title) + AIText.count($1.idea.text) } }
check("sum up sends: 24,000 characters at most", total(accountParts) == 24_000 && total(phoneParts) == 24_000, "\(total(accountParts)) \(total(phoneParts))")
check("sum up sends: one part on an account", accountParts.count == 1)
check("sum up sends: parts of 2,400 on the phone",
      phoneParts.count > 1 && phoneParts.allSatisfy { p in p.reduce(0) { $0 + AIText.count($1.idea.title) + AIText.count($1.idea.text) } <= 2400 })
check("sum up sends: numbered from 1 across the parts",
      phoneParts.joined().map(\.n) == Array(1...phoneParts.joined().count) && accountParts.joined().map(\.n) == phoneParts.joined().map(\.n))
let onePart = AISumUp.payload(folder: "Garden", part: phoneParts[1])
check("sum up sends: a part counts its own ideas and characters",
      onePart.ideas == phoneParts[1].count && onePart.characters == total([phoneParts[1]]))

// MARK: consent's three conditions (A5)

let kv = AIMemoryKeyValue()
let consent = AIConsent(store: kv)
check("consent: everything off to start", AIFeature.allCases.allSatisfy { !consent.isOn($0) })
check("consent: off, not allowed", !consent.allows(.split, .openrouter, connected: true))
consent.setOn(.split, true)
check("consent: on but not agreed, not allowed", !consent.allows(.split, .openrouter, connected: true))
consent.agree(.split, .openrouter)
check("consent: on and agreed, allowed", consent.allows(.split, .openrouter, connected: true))
check("consent: not when the account is not connected", !consent.allows(.split, .openrouter, connected: false))
check("consent: agreed once per feature and account", !consent.allows(.split, .anthropic, connected: true)
      && !consent.allows(.ask, .openrouter, connected: true))
consent.withdraw(.split, .openrouter)
check("consent: withdrawn, not allowed", !consent.allows(.split, .openrouter, connected: true))
check("consent: the phone's own model needs only the feature on", consent.allows(.split, .apple, connected: true)
      && !consent.allows(.ask, .mlkit, connected: true))
consent.setOn(.links, true)
check("consent: links run on the phone and need no agreement", consent.allows(.links, .openrouter, connected: true))

// MARK: the runner: nothing goes out without all three (A5)

final class FakeOnDevice: AIProvider {
    let id: AIProviderID
    let sendsSchema = true
    var answer = ""
    var requests: [AIRequest] = []
    init(_ id: AIProviderID) { self.id = id }
    func complete(_ r: AIRequest) throws -> AIResponse {
        try AIThread.requireBackground()
        requests.append(r)
        return AIResponse(text: answer, usage: AIUsage(inputTokens: 0, outputTokens: 0))
    }
}

func chat(_ content: String, cost: Double? = 0.0000214455) -> AIHTTPResponse {
    var usage: [(String, AIJSON)] = [("prompt_tokens", .number(231)), ("completion_tokens", .number(48))]
    if let cost { usage.append(("cost", .number(cost))) }
    let body = AIJSON.make([("choices", .array([.make([("message", .make([("role", .string("assistant")), ("content", .string(content))]))])])),
                            ("usage", .make(usage))])
    return AIHTTPResponse(status: 200, body: body.data)
}

var clock = Date(timeIntervalSince1970: 1_763_200_000)
let dir = scratch("runner")
let ledgerFile = dir.appendingPathComponent("ai-ledger.json")
let ledger = AILedger(file: ledgerFile, now: { clock })
let store = AIMemoryKeyValue()
let vault = AIMemoryVault()
let net = StandInTransport()
let runner = AIRunner(vault: vault, store: store, prices: prices, ledger: ledger, transport: net, onDevice: nil, root: aiRoot, now: { clock })

func isOff<V>(_ o: AIOutcome<V>) -> Bool { if case .off = o { return true } else { return false } }
func everyOperation() -> [Bool] {
    try! offMain {
        [isOff(runner.split(capture, folders: folders)),
         isOff(runner.why(AIIdea(id: "n", title: "Beans", text: "Beans up the fence."), candidates: whySent)),
         isOff(runner.ask("What did I plan?", found: askIdeas)),
         isOff(runner.sumUp(folder: "Garden", ideas: sumIdeas))]
    }
}

check("no AI: every operation reports itself off", everyOperation() == [true, true, true, true])
let settings = AISettings(store: store)
settings.provider = .openrouter
check("no account connected: every operation off", everyOperation() == [true, true, true, true])
try! vault.save(fakeKey, for: .openrouter)
check("connected, features off: every operation off", everyOperation() == [true, true, true, true])
let gate = AIConsent(store: store)
for f in [AIFeature.split, .why, .ask, .sumup] { gate.setOn(f, true) }
check("connected, on, not agreed: every operation off", everyOperation() == [true, true, true, true])
check("and the network stand-in recorded zero requests", net.requests.isEmpty, "\(net.requests.count)")
check("and the ledger is empty", ledger.entries().isEmpty)

for f in [AIFeature.split, .why, .ask, .sumup] { gate.agree(f, .openrouter) }
settings.setModel("deepseek/deepseek-v4.1-flash", for: .openrouter)
let oneSentence = try! offMain { runner.split("Paint the shed blue.", folders: folders) }
if case .notNeeded = oneSentence { check("one sentence: nothing to split, nothing sent", net.requests.isEmpty) } else { check("one sentence: nothing to split", false) }

// all three hold: one request per operation, each recorded
let goodSplit = split["cases"]!.array![0]["answer"]!.string!
net.handler = { _ in chat(goodSplit) }
var splitDone = false
let splitOutcome = try! offMain { runner.split(capture, folders: folders) }
if case let .answered(.ok, pieces?) = splitOutcome { splitDone = pieces.count == 3 }
check("all three: split answered and checked", splitDone, "\(splitOutcome)")
check("all three: one request, to OpenRouter's chat", net.requests.count == 1 && net.requests[0].url == "https://openrouter.ai/api/v1/chat/completions")
let sentBody = AIJSON.parse(net.requests[0].body!)!
check("all three: the request carries exactly the split payload", sentBody["messages"]!.array![1]["content"]!.string == sp.input)
check("all three: the schema went with it", sentBody["response_format"]!["json_schema"]!["name"]!.string == "split")
var entries = ledger.entries()
check("ledger: the send recorded with its ideas, characters and reported cost",
      entries == [AILedgerEntry(time: clock.timeIntervalSince1970, provider: .openrouter, feature: "split", ideas: 0, characters: sp.characters, costPico: 21_445_500)],
      "\(entries)")
check("money: the reported cost counted", runner.money.spent(.openrouter) == 21_445_500)

net.handler = { _ in chat(why["cases"]!.array![0]["answer"]!.string!) }
let whyOutcome = try! offMain { runner.why(AIIdea(id: "n", title: why["idea"]!["title"]!.string!, text: why["idea"]!["text"]!.string!), candidates: whySent) }
if case let .answered(.ok, r?) = whyOutcome { check("all three: why answered", r == ["c1": "Both are about beans on the fence.", "c3": "Both are about the fence."]) }
else { check("all three: why answered", false, "\(whyOutcome)") }
net.handler = { _ in chat(ask["cases"]!.array![1]["answer"]!.string!) }
let askOutcome = try! offMain { runner.ask(ask["question"]!.string!, found: askIdeas) }
if case let .answered(.ok, a?) = askOutcome { check("all three: ask answered with its citations", a.cites == ["i3", "i1"]) }
else { check("all three: ask answered", false, "\(askOutcome)") }
net.handler = { _ in chat(sumup["cases"]!.array![0]["answer"]!.string!) }
let sumOutcome = try! offMain { runner.sumUp(folder: "Garden", ideas: sumIdeas) }
if case let .answered(.ok, a?) = sumOutcome { check("all three: sum up answered", a.count == 1) }
else { check("all three: sum up answered", false, "\(sumOutcome)") }
entries = ledger.entries()
check("ledger: one entry per send", entries.map(\.feature) == ["split", "why", "ask", "sumup"] && net.requests.count == 4)
check("ledger: ideas counted", entries.map(\.ideas) == [0, 1 + whySent.count, askIdeas.count, sumIdeas.count])

// an answer that does not check changes nothing, but was still sent and paid for
net.handler = { _ in chat(split["cases"]!.array![5]["answer"]!.string!) }
let badOutcome = try! offMain { runner.split(capture, folders: folders) }
if case .answered(.notASlice, nil) = badOutcome { check("not a slice: reported, no pieces", true) } else { check("not a slice: reported", false, "\(badOutcome)") }
check("not a slice: still recorded", ledger.entries().count == 5)

// the provider refuses: released, not recorded
let spentBefore = runner.money.spent(.openrouter)
net.handler = { _ in AIHTTPResponse(status: 429, body: Data("{\"error\":{\"code\":429,\"message\":\"Rate limit\"}}".utf8)) }
let refusedOutcome = try! offMain { runner.ask("What fits?", found: askIdeas) }
if case .failed(.provider(.rateLimited)) = refusedOutcome { check("refused: rate limited", true) } else { check("refused: rate limited", false, "\(refusedOutcome)") }
check("refused: the held worst case released, nothing recorded", runner.money.spent(.openrouter) == spentBefore && ledger.entries().count == 5)

// no answer came: the worst case stays, and is recorded
net.handler = { _ in throw AIError.transport }
let lostOutcome = try! offMain { runner.ask("What fits?", found: askIdeas) }
if case .failed(.provider(.tryLater)) = lostOutcome { check("lost: try later", true) } else { check("lost: try later", false, "\(lostOutcome)") }
let lost = ledger.entries().last!
check("lost: the worst case kept and recorded", lost.costPico > 0 && runner.money.spent(.openrouter) == spentBefore + lost.costPico)

// the cap stops a call before anything is sent
let sentSoFar = net.requests.count
store.set(runner.money.month() + "=" + String(5 * AIMoney.dollar), "ai.spent.openrouter")
let capped = try! offMain { runner.ask("What fits?", found: askIdeas) }
if case .failed(.capReached) = capped { check("cap: reached, the call not made", net.requests.count == sentSoFar) } else { check("cap: reached", false, "\(capped)") }

// MARK: the phone's own model: nothing leaves the phone

let phoneModel = FakeOnDevice(.apple)
let phoneStore = AIMemoryKeyValue()
let phoneNet = StandInTransport()
let phoneLedger = AILedger(file: dir.appendingPathComponent("phone-ledger.json"), now: { clock })
let phoneRunner = AIRunner(vault: AIMemoryVault(), store: phoneStore, prices: prices, ledger: phoneLedger, transport: phoneNet,
                           onDevice: phoneModel, root: aiRoot, now: { clock })
AISettings(store: phoneStore).provider = .apple
check("on the phone: features off, off", isOff(try! offMain { phoneRunner.split(capture, folders: folders) }) && phoneModel.requests.isEmpty)
for f in [AIFeature.split, .ask] { AIConsent(store: phoneStore).setOn(f, true) }
phoneModel.answer = goodSplit
if case .answered(.ok, _?) = try! offMain({ phoneRunner.split(capture, folders: folders) }) {
    check("on the phone: on is enough, no agreement asked", phoneModel.requests.count == 1)
} else { check("on the phone: split answered", false) }
phoneModel.answer = ask["cases"]!.array![0]["answer"]!.string!
_ = try! offMain { phoneRunner.ask("What fits?", found: found) }
check("on the phone: ask sends 6 ideas", AIJSON.parse(Data(phoneModel.requests[1].input.utf8))!["ideas"]!.array!.count == 6)
check("on the phone: the schema goes to guided generation", phoneModel.requests[1].schema != nil && phoneModel.requests[1].model == "on-device")
check("on the phone: no request and no ledger entry", phoneNet.requests.isEmpty && phoneLedger.entries().isEmpty
      && !FileManager.default.fileExists(atPath: dir.appendingPathComponent("phone-ledger.json").path))
check("on the phone: not counted against a cap", phoneRunner.money.spent(.apple) == 0)
check("on the phone: the main thread is refused", { do { _ = try phoneRunner.call(.split, prompt: "split", payload: sp, maxOutput: 10); return false }
    catch { return (error as? AIError) == .mainThread } }())

// MARK: the ledger keeps 90 days and clears

let ledgerText = try! String(contentsOf: ledgerFile, encoding: .utf8)
check("ledger: no text of an idea in it", !ledgerText.contains("Paint") && !ledgerText.contains("fence") && !ledgerText.contains("What fits"))
clock = clock.addingTimeInterval(89 * 86400)
check("ledger: 89 days later, all still there", ledger.entries().count == 6)
clock = clock.addingTimeInterval(2 * 86400)
check("ledger: past 90 days, gone", ledger.entries().isEmpty)
ledger.record(AILedgerEntry(time: clock.timeIntervalSince1970, provider: .anthropic, feature: "ask", ideas: 2, characters: 40, costPico: 1))
check("ledger: a new send recorded", ledger.entries().count == 1)
ledger.clear()
check("ledger: cleared", ledger.entries().isEmpty && !FileManager.default.fileExists(atPath: ledgerFile.path))

// MARK: the prompts are the same bytes in both apps (A6)

let sums = try! String(contentsOf: aiRoot.appendingPathComponent("prompts/SHA256SUMS"), encoding: .utf8)
let lines = sums.split(separator: "\n").map { $0.split(separator: " ", omittingEmptySubsequences: true).map(String.init) }
check("prompts: eight files listed", lines.count == 8 && Set(lines.map { $0[1] }) == Set(AIPrompt.names.flatMap { [$0 + ".txt", $0 + ".schema.json"] }))
for l in lines {
    let data = try! Data(contentsOf: aiRoot.appendingPathComponent("prompts").appendingPathComponent(l[1]))
    check("prompts: \(l[1]) matches its SHA-256", AISHA256.hex(data) == l[0])
}
for name in AIPrompt.names {
    let p = try! AIPrompt.load(name, root: aiRoot)
    check("prompts: \(name)'s schema is JSON", AIJSON.parse(Data(p.schema.utf8)) != nil && !p.instructions.isEmpty)
}

// MARK: a planted fake credential never leaves the vault (A3)

let leakStore = AIMemoryKeyValue()
let leakVault = AIMemoryVault()
let leakNet = StandInTransport.replaying(fixture("http/openrouter-key.json"))
let leakLedger = AILedger(file: dir.appendingPathComponent("leak-ledger.json"), now: { clock })
let leakRunner = AIRunner(vault: leakVault, store: leakStore, prices: prices, ledger: leakLedger, transport: leakNet, onDevice: nil, root: aiRoot, now: { clock })
var errors: [String] = []
let output = captured {
    try! offMain {
        let connect = AIConnect(vault: leakVault, transport: leakNet)
        try connect.connect(.openrouter, key: "  " + fakeKey + "\n")
        AISettings(store: leakStore).provider = .openrouter
        AISettings(store: leakStore).setModel("deepseek/deepseek-v4.1-flash", for: .openrouter)
        let c = AIConsent(store: leakStore)
        for f in [AIFeature.split, .why, .ask, .sumup] { c.setOn(f, true); c.agree(f, .openrouter) }
        leakNet.handler = { _ in chat(goodSplit) }
        _ = leakRunner.split(capture, folders: folders)
        leakNet.handler = { _ in AIHTTPResponse(status: 401, body: Data("{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}".utf8)) }
        if case let .failed(e) = leakRunner.ask("What fits?", found: askIdeas) { errors.append(String(describing: e)) }
        do { try connect.connect(.openai, key: fakeKey) } catch { errors.append(String(describing: error)); errors.append("\(error)") }
        leakNet.handler = { _ in throw AIError.transport }
        if case let .failed(e) = leakRunner.sumUp(folder: "Garden", ideas: sumIdeas) { errors.append(String(describing: e)) }
    }
    print("capture-marker")
}
check("leak: the capture works", output.contains("capture-marker"))
check("leak: the key was stored, trimmed", (try? leakVault.read(.openrouter)) == fakeKey)
check("leak: not in anything printed", !output.contains(fakeKey))
check("leak: not in the settings store", !leakStore.all.contains { $0.key.contains(fakeKey) || $0.value.contains(fakeKey) })
let leakLedgerData = (try? Data(contentsOf: dir.appendingPathComponent("leak-ledger.json"))) ?? Data()
check("leak: not in the ledger", !leakLedgerData.isEmpty && String(decoding: leakLedgerData, as: UTF8.self).range(of: fakeKey) == nil)
check("leak: not in any error", errors.count >= 3 && !errors.contains { $0.contains(fakeKey) }, "\(errors)")
check("leak: not in any request's address or body", leakNet.requests.count >= 4 && leakNet.requests.allSatisfy {
    !$0.url.contains(fakeKey) && !String(decoding: $0.body ?? Data(), as: UTF8.self).contains(fakeKey)
})
check("leak: only in the one header the provider reads", leakNet.requests.allSatisfy { r in
    r.headers.filter { $0.1.contains(fakeKey) }.map { $0.0 } == ["Authorization"]
})
try! AIConnect(vault: leakVault, transport: leakNet).disconnect(.openrouter)
check("disconnect: the key is gone", (try? leakVault.read(.openrouter)) == .some(nil))
check("disconnect: and every operation is off again", isOff(try! offMain { leakRunner.split(capture, folders: folders) }))

finish("AI OPERATION")
