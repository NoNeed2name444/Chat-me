import XCTest
@testable import Sorta

/// The sorting deck's, "You were saying…"'s and the tides' rules
/// (SortDeck.swift, S06a), with the same cases as Android's SortDeckTest.
final class SortDeckTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_793_000_000)
    private func daysAgo(_ d: Double) -> Date { now.addingTimeInterval(-d * IdeaFrost.day) }

    func testStoppedAtTheSharedCases() {
        let cases: [(String, Int?, Int?)] = [
            ("", nil, nil),
            ("   \n", nil, nil),
            ("Fix the gate latch.", nil, nil),
            ("Fix the gate latch", nil, 18),
            ("Fix the gate latch", 4, 4),
            ("Really?", nil, nil),
            ("Wait…", nil, nil),
            ("He said \u{201C}go.\u{201D}", nil, nil),
            ("(see the shed.)", nil, nil),
            ("Two coats, after the rain. Then", nil, 31),
            ("Done!  \n", nil, nil),
            ("Buy brushes", 99, 11),
            ("Buy brushes", -3, 0),
            ("パンを焼く。", nil, nil),
            ("متى؟", nil, nil),
            ("Plant tulips 🌷", nil, 15),
            ("Bread rises,", 5, 5),
        ]
        for (body, caret, want) in cases {
            XCTAssertEqual(IdeaStops.stoppedAt(body: body, caret: caret), want, "\(body.debugDescription) caret \(String(describing: caret))")
        }
    }

    func testTheOfferIsTheMarkedIdeaChangedLast() {
        let a = UUID(), b = UUID(), c = UUID()
        XCTAssertNil(IdeaStops.offer([(id: a, stoppedAt: nil, updatedAt: now)]))
        XCTAssertEqual(IdeaStops.offer([(id: a, stoppedAt: 3, updatedAt: now), (id: b, stoppedAt: nil, updatedAt: now + 60),
                                        (id: c, stoppedAt: 0, updatedAt: now + 30)]), c)
        XCTAssertEqual(IdeaStops.offer([(id: a, stoppedAt: 3, updatedAt: now), (id: b, stoppedAt: 1, updatedAt: now)]), a)
    }

    func testFrostSteps() {
        let cases: [(Double?, Double, Int)] = [
            (nil, 0, 0), (nil, 29.9, 0), (nil, 30, 1), (nil, 89, 1), (nil, 90, 2), (nil, 179, 2), (nil, 180, 3), (nil, 400, 3),
            (10, 200, 0), (95, 300, 2),
        ]
        for (opened, made, want) in cases {
            XCTAssertEqual(IdeaFrost.level(openedAt: opened.map(daysAgo), createdAt: daysAgo(made), now: now), want,
                           "opened \(String(describing: opened)), made \(made)")
        }
    }

    func testTheDecksOrder() {
        var deck = SortDeck<String>()
        deck.sync(["c", "b", "a"])
        XCTAssertEqual(deck.order, ["c", "b", "a"])
        deck.later("c")
        XCTAssertEqual(deck.order, ["b", "a", "c"])
        deck.sync(["d", "c", "b", "a"])
        XCTAssertEqual(deck.order, ["d", "b", "a", "c"])
        deck.sync(["d", "c", "a"])
        XCTAssertEqual(deck.order, ["d", "a", "c"])
        deck.sync(["d", "c", "b", "a"])
        XCTAssertEqual(deck.order, ["b", "d", "a", "c"])
        deck.putBack("c")
        XCTAssertEqual(deck.order, ["c", "b", "d", "a"])
        XCTAssertEqual(deck.top, "c")
        deck.later("zz")
        XCTAssertEqual(deck.order, ["c", "b", "d", "a"])
    }

    func testWhereAFlickLands() {
        let targets = [DeckFlick.Target(id: "Garden", minX: 0, minY: 100, maxX: 120, maxY: 150),
                       DeckFlick.Target(id: "Kitchen", minX: 270, minY: 100, maxX: 390, maxY: 150)]
        func flick(_ x: Double, _ y: Double) -> DeckFlick.Outcome<String> {
            DeckFlick.outcome(fromX: 195, fromY: 600, toX: x, toY: y, targets: targets)
        }
        XCTAssertEqual(flick(225, 560), .back)
        XCTAssertEqual(flick(60, 120), .folder("Garden"))
        XCTAssertEqual(flick(390, 160), .folder("Kitchen"))
        XCTAssertEqual(flick(195, 900), .back)
        XCTAssertEqual(flick(195, 300), .later)
        XCTAssertEqual(flick(345, 300), .later)
        XCTAssertEqual(flick(495, 400), .back)
        let landing = DeckFlick.landing(x: 190, y: 500, vx: 0, vy: -2000)
        XCTAssertEqual(landing.x, 190)
        XCTAssertEqual(landing.y, 200)
    }

    func testTheMostUsedFolders() {
        let folders: [(id: String, name: String, count: Int)] = [
            ("k", "Kitchen", 2), ("g", "Garden", 5), ("b", "bulbs", 2), ("s", "Shed", 0), ("a", "attic", 2),
        ]
        XCTAssertEqual(DeckFolders.pick(folders, slots: 4), ["g", "a", "b", "k"])
        XCTAssertTrue(DeckFolders.pick(folders, slots: 0).isEmpty)
        XCTAssertEqual(DeckFolders.pick(folders, slots: 9).count, 5)
        XCTAssertEqual(DeckFolders.slots(height: 200, row: 52, columns: 2, cap: 8), 6)
        XCTAssertEqual(DeckFolders.slots(height: 900, row: 52, columns: 2, cap: 8), 8)
        XCTAssertEqual(DeckFolders.slots(height: 40, row: 52, columns: 2, cap: 8), 0)
    }

    func testTheLowTide() {
        XCTAssertFalse(LowTide.due(last: nil, now: now))
        XCTAssertFalse(LowTide.due(last: daysAgo(6.9), now: now))
        XCTAssertTrue(LowTide.due(last: daysAgo(7), now: now))
        let tide = LowTide.pick([
            (id: "fresh", openedAt: nil, createdAt: daysAgo(3)),
            (id: "old", openedAt: nil, createdAt: daysAgo(200)),
            (id: "opened", openedAt: daysAgo(5), createdAt: daysAgo(300)),
            (id: "mid", openedAt: daysAgo(100), createdAt: daysAgo(400)),
            (id: "edge", openedAt: nil, createdAt: daysAgo(31)),
            (id: "older", openedAt: nil, createdAt: daysAgo(250)),
        ], now: now)
        XCTAssertEqual(tide, ["older", "old", "mid"])
        XCTAssertEqual(LowTide.pick([(id: "x", openedAt: nil, createdAt: daysAgo(40)),
                                     (id: "y", openedAt: nil, createdAt: daysAgo(2))], now: now), ["x"])
        XCTAssertTrue(LowTide.pick([(id: "y", openedAt: nil, createdAt: daysAgo(2))], now: now).isEmpty)
    }
}
