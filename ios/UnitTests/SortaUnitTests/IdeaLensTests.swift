import XCTest
@testable import Sorta

/// The lenses (S06d A1), case by case as Android's IdeaLensTest: the
/// sentence rule, each lens's predicate, two lenses at once and the chips'
/// counts; and the Performance map's mask with lenses on (A2), as
/// Android's IdeaLensVisibilityTest asks of its filter. The ideas are made
/// up, none medical.
final class IdeaLensTests: XCTestCase {
    private let utc = TimeZone(identifier: "UTC")!

    private func at(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }

    private func id(_ n: String) -> UUID { UUID(uuidString: "00000000-0000-4000-8000-" + String(repeating: "0", count: 12 - n.count) + n)! }

    private func note(_ n: String, _ title: String, _ body: String = "", created: String = "2026-11-02T12:00:00Z") -> Note {
        Note(id: id(n), title: title, body: body, createdAt: at(created))
    }

    func testSentence() {
        let cases: [(String, Bool)] = [
            ("", false), ("   ", false), ("Buy milk", true), ("Buy milk.", false), ("Really?", false), ("Wow!", false),
            ("And then\u{2026}", false), ("And then...", false), ("Buy milk  \n", true), ("Ends with a comma,", true),
            ("(see the list)", true), ("Tea? Later", true), ("Two lines.\nAnd a third", true),
        ]
        for (text, mid) in cases { XCTAssertEqual(IdeaSentence.endsMidSentence(text), mid, "\"\(text)\"") }
    }

    func testQuestions() {
        let cases: [(String, String, Bool)] = [
            ("Why is the sky blue", "", false), ("Why?", "", true), ("Plan", "is it? maybe", true),
            ("", "", false), ("Shed", "Two coats.", false),
        ]
        for (title, body, yes) in cases { XCTAssertEqual(IdeaLens.isQuestion(title: title, body: body), yes, "\(title) / \(body)") }
    }

    func testUnfinished() {
        let cases: [(String, String, Bool)] = [
            ("Paint the shed", "", true), ("Paint the shed.", "", false), ("Done.", "Two coats, after", true),
            ("Plan", "Two coats.", false), ("", "", false), ("x", "   ", true),
        ]
        for (title, body, yes) in cases { XCTAssertEqual(IdeaLens.isUnfinished(title: title, body: body), yes, "\(title) / \(body)") }
    }

    func testLateNight() {
        let cases: [(String, Bool)] = [
            ("2026-11-02T22:59:59Z", false), ("2026-11-02T23:00:00Z", true), ("2026-11-03T00:30:00Z", true),
            ("2026-11-03T03:59:59Z", true), ("2026-11-03T04:00:00Z", false), ("2026-11-03T12:00:00Z", false),
        ]
        for (iso, yes) in cases { XCTAssertEqual(IdeaLens.isLateNight(at(iso), in: utc), yes, iso) }
        // the phone's zone decides: 9:30 PM in London is 11:30 PM in Cairo
        XCTAssertTrue(IdeaLens.isLateNight(at("2026-11-14T21:30:00Z"), in: TimeZone(identifier: "Africa/Cairo")!), "Cairo")
        XCTAssertFalse(IdeaLens.isLateNight(at("2026-11-14T21:30:00Z"), in: utc), "UTC")
    }

    private var shed: Note { note("A", "Paint the shed?", created: "2026-11-02T23:30:00Z") }
    private var gate: Note { note("B", "Fix the gate latch.", created: "2026-11-02T10:00:00Z") }
    private var seeds: Note { note("C", "Order seeds for", created: "2026-11-03T01:00:00Z") }
    private var bread: Note { note("D", "Bake bread?", "Rye or wheat", created: "2026-11-03T09:00:00Z") }
    private var notes: [Note] { [shed, gate, seeds, bread] }
    private var edges: [(UUID, UUID)] { [(id("A"), id("B"))] }

    func testAlone() {
        let linked = IdeaLens.linked(edges)
        XCTAssertEqual(notes.map { IdeaLens.alone.passes($0, linked: linked, zone: utc) }, [false, false, true, true])
    }

    func testPassingAndCounts() {
        XCTAssertNil(IdeaLens.passing([], notes: notes, edges: edges, zone: utc), "no lens: everything")
        XCTAssertEqual(IdeaLens.passing([.questions], notes: notes, edges: edges, zone: utc), [id("A"), id("D")])
        XCTAssertEqual(IdeaLens.passing([.lateNight], notes: notes, edges: edges, zone: utc), [id("A"), id("C")])
        XCTAssertEqual(IdeaLens.passing([.unfinished], notes: notes, edges: edges, zone: utc), [id("C"), id("D")])
        XCTAssertEqual(IdeaLens.passing([.alone], notes: notes, edges: edges, zone: utc), [id("C"), id("D")])
        // two lenses: only ideas that pass both
        XCTAssertEqual(IdeaLens.passing([.questions, .lateNight], notes: notes, edges: edges, zone: utc), [id("A")])
        XCTAssertEqual(IdeaLens.passing([.lateNight, .unfinished, .questions], notes: notes, edges: edges, zone: utc), [])
        XCTAssertEqual(IdeaLens.counts([], notes: notes, edges: edges, zone: utc),
                       [.questions: 2, .lateNight: 2, .unfinished: 2, .alone: 2])
        // with Questions on, each chip counts what shows with it on as well
        XCTAssertEqual(IdeaLens.counts([.questions], notes: notes, edges: edges, zone: utc),
                       [.questions: 2, .lateNight: 1, .unfinished: 1, .alone: 1])
        XCTAssertEqual(IdeaLens.allCases.map(\.rawValue), ["questions", "late-night", "unfinished", "alone"])
    }

    /// The Performance map's mask: a note shows when the filter and the
    /// lenses both let it through; a folder's hub while a note under it
    /// still shows (and, under a folder filter, only inside that folder).
    func testThePerformanceMaskWithLenses() {
        // Garden (f1) holds Paths (f2); the shed and gate in Garden, the
        // seeds in Paths, the bread loose
        let f1 = id("F1"), f2 = id("F2")
        let input = GraphPerfInput.make(noteIDs: notes.map(\.id), isPage: [false, true, false, false],
                                        noteFolders: [f1, f1, f2, nil], folderIDs: [f1, f2], folderParents: [nil, f1],
                                        links: edges)
        let graph = GraphPerfGraph(input)
        func shown(_ mask: [Bool]?) -> Set<UUID> {
            guard let mask else { return Set(graph.noteIDs).union([f1, f2]) }
            var out = Set<UUID>()
            for i in 0..<graph.noteCount where mask[i] { out.insert(graph.noteIDs[i]) }
            if mask[graph.noteCount + 0] { out.insert(f1) }
            if mask[graph.noteCount + 1] { out.insert(f2) }
            return out
        }
        XCTAssertNil(graph.mask(.all, only: nil), "no filter, no lens: everything")
        XCTAssertEqual(shown(graph.mask(.all, only: [id("A"), id("D")])), [id("A"), id("D"), f1],
                       "the lens's notes, and Garden for the shed; Paths has none left")
        XCTAssertEqual(shown(graph.mask(.ideas, only: [id("A"), id("B"), id("C")])), [id("A"), id("C"), f1, f2],
                       "the gate is a page, so the ideas filter leaves it out")
        XCTAssertEqual(shown(graph.mask(.folder(f2), only: [id("A"), id("C")])), [id("C"), f2],
                       "inside Paths only; Garden's hub stays out under the folder filter")
        XCTAssertEqual(shown(graph.mask(.all, only: [])), [], "nothing passes: nothing shows")
        XCTAssertEqual(graph.mask(.pages, only: nil), graph.mask(.pages), "no lens: the filter's own mask")
    }
}
