import XCTest
@testable import Sorta

/// S06c: echoes, collide, split, join and the two-finger rules on the cases
/// Android's IdeaEchoesTest and IdeaReshapeTest read too
/// (UnitTests/Suites/IdeaShapeCases.json), and 5,000 made-up ideas against
/// the clock. The store's reshapes are IdeasReshapeStoreTests'.
final class IdeaShapeTests: XCTestCase {
    private static let cases: [String: Any] = {
        let here = URL(fileURLWithPath: #filePath)
        let url = here.deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Suites/IdeaShapeCases.json")
        guard let data = try? Data(contentsOf: url),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return [:] }
        return object
    }()

    private func list(_ key: String) -> [[String: Any]] { Self.cases[key] as? [[String: Any]] ?? [] }
    private func strings(_ any: Any?) -> [String] { (any as? [Any] ?? []).compactMap { $0 as? String } }
    private func number(_ any: Any?) -> Double { (any as? NSNumber)?.doubleValue ?? 0 }

    /// SplitMix64: the same numbers every run.
    struct Seeded: RandomNumberGenerator {
        var state: UInt64
        mutating func next() -> UInt64 {
            state &+= 0x9E37_79B9_7F4A_7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
            z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
            return z ^ (z >> 31)
        }
    }

    func testTheCasesAreRead() {
        XCTAssertFalse(list("echoes").isEmpty, "IdeaShapeCases.json is found beside the suites")
    }

    func testWordsAsAndroidReadsThem() {
        for c in list("words") {
            XCTAssertEqual(IdeaEchoes.words(c["text"] as? String ?? ""), strings(c["want"]), c["text"] as? String ?? "")
        }
        XCTAssertTrue(IdeaEchoes.stopWords.allSatisfy { $0.count >= 3 }, "no stop word under 3 letters")
    }

    func testEchoesAsAndroidFindsThem() {
        for c in list("echoes") {
            let ideas: [IdeaEchoes.Idea<String>] = (c["ideas"] as? [[String: Any]] ?? []).map {
                let id = $0["id"] as? String ?? ""
                return IdeaEchoes.Idea(id: id, text: $0["text"] as? String ?? "", made: number($0["made"]), key: id)
            }
            let got = IdeaEchoes.Index(ideas).echoes(of: c["text"] as? String ?? "", current: c["current"] as? String,
                                                    made: number(c["made"]), connected: Set(strings(c["connected"])))
            XCTAssertEqual(got, strings(c["want"]), c["name"] as? String ?? "")
        }
    }

    func testFiveThousandIdeasAnswerWhileTyping() {
        let syllables = ["ka", "lo", "mi", "ren", "tu", "sa", "vo", "pel", "dri", "nu", "gar", "fen", "bo", "lis", "tam"]
        var gen = Seeded(state: 42)
        var made: [IdeaEchoes.Idea<Int>] = []
        for i in 0..<5000 {
            let words: [String] = (0..<12).map { _ in
                (0..<3).map { _ in syllables[Int.random(in: 0..<syllables.count, using: &gen)] }.joined()
            }
            made.append(IdeaEchoes.Idea(id: i, text: words.joined(separator: " ") + " and the garden", made: Double(i), key: String(i)))
        }
        let index = IdeaEchoes.Index(made)
        for k in 0..<5 { _ = index.echoes(of: made[k].text, current: nil, made: 1e9) }
        var slowest = 0.0
        var found = 0
        for k in 0..<20 {
            let t0 = Date()
            found += index.echoes(of: made[(k * 251) % 5000].text + " then a little more typed here", current: nil, made: 1e9).count
            slowest = max(slowest, Date().timeIntervalSince(t0) * 1000)
        }
        XCTAssertLessThan(slowest, 50, "each ask under 50 ms")
        XCTAssertGreaterThan(found, 0, "the 5,000 find their echoes")
    }

    func testCollidePicksOnlyFarPairsAndNeverTheLast() {
        for c in list("collide") {
            let name = c["name"] as? String ?? ""
            let ideas: [IdeaReshape.Place<String>] = (c["ideas"] as? [[Any]] ?? []).map {
                IdeaReshape.Place(id: $0[0] as? String ?? "", top: $0[1] as? String)
            }
            let links: [[String]] = (c["links"] as? [[Any]] ?? []).map { strings($0) }
            let pair = strings(c["last"])
            let last: (String, String)? = pair.count == 2 ? (pair[0], pair[1]) : nil
            let allowed = Set((c["allowed"] as? [[Any]] ?? []).map { strings($0).sorted().joined(separator: "+") })
            var rng = Seeded(state: 7)
            var seen = Set<String>()
            for _ in 0..<200 {
                let got = IdeaReshape.collide(ideas, linked: { a, b in links.contains { $0 == [a, b] || $0 == [b, a] } },
                                              last: last, using: &rng)
                if let got {
                    let key = [got.0, got.1].sorted().joined(separator: "+")
                    XCTAssertTrue(allowed.contains(key), "\(name): \(key) is not allowed")
                    seen.insert(key)
                } else {
                    XCTAssertTrue(allowed.isEmpty, "\(name): none, though pairs are allowed")
                }
            }
            XCTAssertEqual(seen, allowed, "\(name): every allowed pair comes up")
        }
    }

    func testSentencesCutsAndHeadingsAsAndroidMakesThem() {
        for c in list("sentences") {
            let got = IdeaReshape.sentences(title: c["title"] as? String ?? "", body: c["body"] as? String ?? "")
            let want: [String] = (c["want"] as? [[Any]] ?? []).map { "\($0[0] as? String ?? "")|\(($0[1] as? Bool) ?? false)" }
            XCTAssertEqual(got.map { "\($0.text)|\($0.inTitle)" }, want, c["body"] as? String ?? "")
        }
        for c in list("cuts") {
            let got = IdeaReshape.cut(title: c["title"] as? String ?? "", body: c["body"] as? String ?? "",
                                      after: c["after"] as? Int ?? -1)
            if let w = c["want"] as? [String: Any] {
                XCTAssertEqual(got, IdeaReshape.Halves(firstTitle: w["firstTitle"] as? String ?? "", firstBody: w["firstBody"] as? String ?? "",
                                                       secondTitle: w["secondTitle"] as? String ?? "", secondBody: w["secondBody"] as? String ?? "",
                                                       titleChanged: w["titleChanged"] as? Bool ?? false,
                                                       titleFromBody: w["titleFromBody"] as? Bool ?? false))
            } else {
                XCTAssertNil(got)
            }
        }
        for c in list("headings") {
            XCTAssertEqual(IdeaReshape.heading(c["line"] as? String ?? ""), c["want"] as? String)
        }
    }

    func testJoinAndLinksAsAndroidMakesThem() {
        for c in list("joined") {
            let a = strings(c["first"]), b = strings(c["second"]), w = strings(c["want"])
            let got = IdeaReshape.joined(firstTitle: a[0], firstBody: a[1], secondTitle: b[0], secondBody: b[1])
            XCTAssertEqual([got.title, got.body], w)
        }
        for c in list("splitLinks") {
            let got = IdeaReshape.splitLinks(own: strings(c["own"]), heldBy: strings(c["heldBy"]), namedBy: strings(c["namedBy"]),
                                             titleChanged: c["titleChanged"] as? Bool ?? false,
                                             secondTitleNames: strings(c["secondTitleNames"]),
                                             first: c["first"] as? String ?? "", second: c["second"] as? String ?? "")
            XCTAssertEqual([got.first, got.second], (c["want"] as? [Any] ?? []).map { strings($0) })
        }
        for c in list("joinLinks") {
            let got = IdeaReshape.joinLinks(first: c["first"] as? String ?? "", second: c["second"] as? String ?? "",
                                            firstOwn: strings(c["firstOwn"]), secondOwn: strings(c["secondOwn"]),
                                            pointedAtSecond: strings(c["pointedAtSecond"]), connected: Set(strings(c["connected"])))
            XCTAssertEqual(got, strings(c["want"]), "no link twice, never the two")
        }
    }

    func testTwoFingersAsAndroidJudgesThem() {
        func describe(_ s: GraphReshapeTouch.Start) -> String {
            switch s {
            case .split(let i): return "split \(i)"
            case .join(let a, let b): return "join \(a) \(b)"
            case .none: return "none"
            }
        }
        for c in list("touchStart") {
            let got = GraphReshapeTouch.start(first: c["first"] as? Int, second: c["second"] as? Int,
                                              middle: c["middle"] as? Int, apart: number(c["apart"]))
            XCTAssertEqual(describe(got), c["want"] as? String)
        }
        for c in list("touchJudge") {
            let kind = c["start"] as? String ?? ""
            let start: GraphReshapeTouch.Start = kind == "split" ? .split(0) : kind == "join" ? .join(0, 1) : .none
            let got = GraphReshapeTouch.judge(start, from: number(c["from"]), now: number(c["now"]))
            let name: String = got == .waiting ? "waiting" : got == .done ? "done" : "failed"
            XCTAssertEqual(name, c["want"] as? String, "\(kind) from \(c["from"] ?? "") to \(c["now"] ?? "")")
        }
    }
}
