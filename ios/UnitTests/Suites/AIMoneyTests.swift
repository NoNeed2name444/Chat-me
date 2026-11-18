import Foundation

// S08 A4 on Linux: the cap from $1 to $20 ($5 to start), the check before a
// call and the count after it, the monthly reset in the phone's time zone,
// unknown prices refused, the phone's own models and ":free" free, the $20
// ceiling, and the worst case against reported use in English, Arabic, CJK
// and emoji (ai/fixtures/bounds). Android's AIMoneyTest is the same.

let dollar = AIMoney.dollar
let prices = try! AIPrices(json: Data(contentsOf: aiRoot.appendingPathComponent("tables/prices.json")))
let splitSchema = try! String(contentsOf: aiRoot.appendingPathComponent("prompts/split.schema.json"), encoding: .utf8)

check("prices: Haiku 5.5 is $0.10 and $0.50 a million, $0.50 and $2.50 past 100,000",
      prices.price(.anthropic, "claude-haiku-5-5") == AIPrice(input: 100_000, output: 500_000, longAbove: 100_000, longInput: 500_000, longOutput: 2_500_000))
check("prices: DeepSeek's flash on OpenRouter, to the picodollar", prices.price(.openrouter, "deepseek/deepseek-v4.1-flash") == AIPrice(input: 50_700, output: 202_800))
check("prices: an OpenRouter :free model is free", prices.price(.openrouter, "inclusionai/ling-3.2-flash:free") == AIPrice(input: 0, output: 0))
check("prices: the phone's own models are free", prices.price(.apple, "on-device") == AIPrice(input: 0, output: 0)
      && prices.price(.mlkit, "on-device") == AIPrice(input: 0, output: 0))
check("prices: an unknown model has none", prices.price(.openai, "fake-model-1") == nil && prices.price(.compatible, "fake-model-1") == nil)
check("prices: ':free' counts only on OpenRouter", prices.price(.openai, "gpt-6-luna:free") == nil)

// MARK: the cap

var store = AIMemoryKeyValue()
var clock = Date(timeIntervalSince1970: 1_763_200_000)   // 15 Nov 2025, about 11:46 AM in Cairo
var zone = TimeZone(identifier: "Africa/Cairo")!
var money = AIMoney(prices: prices, store: store, zone: { zone }, now: { clock })

check("cap: $5 to start", money.cap(.anthropic) == 5 * dollar)
money.setCap(12 * dollar, for: .anthropic)
check("cap: the person picks it", money.cap(.anthropic) == 12 * dollar && money.cap(.openai) == 5 * dollar)
money.setCap(500 * dollar, for: .anthropic)
check("cap: $20 is the ceiling, the owner's own Claude key included", money.cap(.anthropic) == 20 * dollar)
money.setCap(0, for: .openai)
check("cap: $1 is the floor", money.cap(.openai) == 1 * dollar)
store.set(String(90 * dollar), "ai.cap.anthropic")
check("cap: a stored cap above $20 still stops at $20", {
    let big = AIRequest(model: "claude-opus-5-5", instructions: "", input: "", maxOutput: 1_000_000)   // $20.00128 at most
    var refused = false
    do { _ = try money.reserve(.anthropic, big, schemaSent: false) } catch { refused = (error as? AIError) == .capReached }
    return refused
}())
store.set(nil, "ai.cap.anthropic")

// MARK: the worst case

let plain = AIRequest(model: "deepseek/deepseek-v4.1-flash", instructions: "Sort.", input: "Paint the shed.", maxOutput: 300)
check("worst case: every byte of the instructions and the input, plus 64", AIMoney.worstCase(plain, schemaSent: false) == (5 + 15 + 64, 300))
var schemed = plain
schemed.schemaName = "split"
schemed.schema = splitSchema
check("worst case: a schema sent counts its bytes", AIMoney.worstCase(schemed, schemaSent: true).input == 5 + 15 + 64 + splitSchema.utf8.count)
check("worst case: a schema not sent does not", AIMoney.worstCase(schemed, schemaSent: false).input == 5 + 15 + 64)

let bounds = fixture("bounds/scripts.json")
for c in bounds["cases"]!.array! {
    let r = AIRequest(model: "deepseek/deepseek-v4.1-flash", instructions: c["instructions"]!.string!, input: c["input"]!.string!, maxOutput: c["maxOutput"]!.int!)
    let parsed = try! AIChatCompletions(.openrouter, base: AIAccounts.openRouterBase, transport: StandInTransport(), credential: { "" })
        .parse(AIHTTPResponse(status: 200, body: c["response"]!["body"]!.data))
    let worst = AIMoney.worstCase(r, schemaSent: false)
    check("bound: \(c["name"]!.string!): the input's UTF-8 bytes are the fixture's", r.input.utf8.count == c["inputBytes"]!.int)
    check("bound: \(c["name"]!.string!): the worst case is at least the reported input tokens",
          worst.input >= parsed.usage.inputTokens && parsed.usage.inputTokens > 0, "\(worst.input) < \(parsed.usage.inputTokens)")
    check("bound: \(c["name"]!.string!): and the output at least the reported output", worst.output >= parsed.usage.outputTokens)
    let price = prices.price(.openrouter, "deepseek/deepseek-v4.1-flash")!
    check("bound: \(c["name"]!.string!): so the worst cost covers the real one",
          price.cost(input: worst.input, output: worst.output) >= price.cost(input: parsed.usage.inputTokens, output: parsed.usage.outputTokens))
}

// MARK: before and after a call

store = AIMemoryKeyValue()
money = AIMoney(prices: prices, store: store, zone: { zone }, now: { clock })
money.setCap(1 * dollar, for: .openrouter)
let worstOne = prices.price(.openrouter, "deepseek/deepseek-v4.1-flash")!.cost(input: 84, output: 300)
let held = try! money.reserve(.openrouter, plain, schemaSent: false)
check("before: the worst case is held", held.held == worstOne && money.spent(.openrouter) == worstOne)
money.settle(held, AIUsage(inputTokens: 30, outputTokens: 10, costPico: 9_000_000))
check("after: OpenRouter's reported cost takes its place", money.spent(.openrouter) == 9_000_000)
let held2 = try! money.reserve(.openrouter, plain, schemaSent: false)
money.settle(held2, AIUsage(inputTokens: 30, outputTokens: 10))
check("after: else tokens times the price", money.spent(.openrouter) == 9_000_000 + 30 * 50_700 + 10 * 202_800)
let held3 = try! money.reserve(.openrouter, plain, schemaSent: false)
money.release(held3)
check("after: a call the provider refused costs nothing", money.spent(.openrouter) == 9_000_000 + 30 * 50_700 + 10 * 202_800)

// refused when the worst case does not fit, even if the real use would have
store.set("2025-11=" + String(1 * dollar - worstOne + 1), "ai.spent.openrouter")
var refused: Error?
do { _ = try money.reserve(.openrouter, plain, schemaSent: false) } catch { refused = error }
check("cap: a call whose worst case does not fit is refused", (refused as? AIError) == .capReached, String(describing: refused))
check("cap: though its real use (about 9 microdollars) would have fitted", 9_000_000 < worstOne - 1)
store.set("2025-11=" + String(1 * dollar - worstOne), "ai.spent.openrouter")
check("cap: one that just fits goes", (try? money.reserve(.openrouter, plain, schemaSent: false)) != nil)

var unknown: Error?
do { _ = try money.reserve(.openai, AIRequest(model: "fake-model-1", instructions: "", input: "", maxOutput: 10), schemaSent: false) } catch { unknown = error }
check("unknown price: refused", (unknown as? AIError) == .unknownPrice)
var compatible: Error?
do { _ = try money.reserve(.compatible, AIRequest(model: "fake-model-1", instructions: "", input: "", maxOutput: 10), schemaSent: false) } catch { compatible = error }
check("unknown price: a compatible service's model too, until its price is listed", (compatible as? AIError) == .unknownPrice)
let free = try? money.reserve(.openrouter, AIRequest(model: "inclusionai/ling-3.2-flash:free", instructions: "", input: String(repeating: "x", count: 100_000), maxOutput: 100_000), schemaSent: false)
check("free: a :free model holds nothing, whatever its size", free?.held == 0)
let before = money.spent(.openrouter)
if let free { money.settle(free, AIUsage(inputTokens: 5, outputTokens: 5, costPico: 0)) }
check("free: and counts nothing", money.spent(.openrouter) == before)
let phone = try? money.reserve(.apple, AIRequest(model: "on-device", instructions: "", input: "x", maxOutput: 10), schemaSent: true)
check("on device: free, and not counted", phone?.held == 0 && money.spent(.apple) == 0)

// the long-context price applies past its threshold
let haiku = prices.price(.anthropic, "claude-haiku-5-5")!
check("long context: Haiku 5.5 at 100,000 input tokens is the short price", haiku.cost(input: 100_000, output: 0) == 100_000 * 100_000)
check("long context: past it, the long one", haiku.cost(input: 100_001, output: 10) == 100_001 * 500_000 + 10 * 2_500_000)

// MARK: the month, in the phone's time zone

store = AIMemoryKeyValue()
clock = ISO8601DateFormatter().date(from: "2026-11-30T21:30:00Z")!   // 11:30 PM on 30 Nov in Cairo
money = AIMoney(prices: prices, store: store, zone: { zone }, now: { clock })
check("month: 11:30 PM on 30 Nov in Cairo is November", money.month() == "2026-11")
let late = try! money.reserve(.openrouter, plain, schemaSent: false)
money.settle(late, AIUsage(inputTokens: 1, outputTokens: 1, costPico: 4_000_000))
check("month: spent in November", money.spent(.openrouter) == 4_000_000)
clock = ISO8601DateFormatter().date(from: "2026-11-30T22:30:00Z")!   // 12:30 AM on 1 Dec in Cairo
check("month: an hour later Cairo is in December, and the spend starts again", money.month() == "2026-12" && money.spent(.openrouter) == 0)
zone = TimeZone(identifier: "UTC")!
check("month: the same moment in UTC is still November", money.month() == "2026-11" && money.spent(.openrouter) == 4_000_000)
zone = TimeZone(identifier: "Africa/Cairo")!

// MARK: $20 is a true ceiling across many calls

store = AIMemoryKeyValue()
money = AIMoney(prices: prices, store: store, zone: { zone }, now: { clock })
money.setCap(20 * dollar, for: .anthropic)
let opus = AIRequest(model: "claude-opus-5-5", instructions: "Sum up.", input: String(repeating: "مرحبا ", count: 2000), maxOutput: 4000)
var calls = 0
while (try? money.reserve(.anthropic, opus, schemaSent: false)) != nil { calls += 1; if calls > 10_000 { break } }
check("ceiling: calls held at their worst case stop at $20", money.spent(.anthropic) <= 20 * dollar && calls > 0, "\(money.spent(.anthropic))")
check("ceiling: and the next would pass it", money.spent(.anthropic) + prices.price(.anthropic, "claude-opus-5-5")!
      .cost(input: AIMoney.worstCase(opus, schemaSent: false).input, output: 4000) > 20 * dollar)

finish("AI MONEY")
