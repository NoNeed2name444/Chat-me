import XCTest
import simd
@testable import Sorta

/// S06b's rules (GraphSkyIdeas.swift), as Android's SkyIdeasTest checks
/// them: the tail's steps, the settle, a folder's words, leaning, the moons
/// and a share's source. Made-up ideas only.
final class SkyIdeasTests: XCTestCase {
    private let day: TimeInterval = 86_400

    private func id(_ k: Int) -> UUID {
        UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", k))!
    }

    // A1
    func testTheTailGrowsInCappedSteps() {
        XCTAssertEqual(CometTail.length(age: 60), 0.35)
        XCTAssertEqual(CometTail.length(age: day - 1), 0.35)
        XCTAssertEqual(CometTail.length(age: day), 0.6)
        XCTAssertEqual(CometTail.length(age: 7 * day), 0.8)
        XCTAssertEqual(CometTail.length(age: 30 * day), 1)
        XCTAssertEqual(CometTail.length(age: 3_650 * day), 1, "never past the tail Space always drew")
        XCTAssertEqual(CometTail.length(age: -day), 0.35, "a clock put back counts as new")
        let lengths: [Float] = [0, day, 3 * day, 8 * day, 40 * day].map { CometTail.length(age: $0) }
        XCTAssertEqual(lengths, lengths.sorted(), "older is never shorter")
    }

    // A2
    func testTheSettleEasesFromWhereItWas() {
        let from = SIMD3<Float>(10, 0, 0)
        let to = SIMD3<Float>(0, 0, 0)
        XCTAssertEqual(CometSettle.place(from: from, to: to, elapsed: 0), from)
        XCTAssertEqual(CometSettle.place(from: from, to: to, elapsed: CometSettle.duration), to)
        XCTAssertEqual(CometSettle.place(from: from, to: to, elapsed: 5), to)
        XCTAssertGreaterThan(CometSettle.progress(0.3), 0.5, "fast, then slow")
        var last: Float = 0
        for k in 0...18 {
            let p: Float = CometSettle.progress(Double(k) * 0.05)
            XCTAssertGreaterThanOrEqual(p, last)
            last = p
        }
    }

    // A3
    func testAFolderKeepsFiveSingleWords() {
        XCTAssertEqual(FolderWords.parse("garden, soil  compost"), ["garden", "soil", "compost"])
        XCTAssertEqual(FolderWords.parse("a b c d e f g"), ["a", "b", "c", "d", "e"])
        XCTAssertEqual(FolderWords.parse("Garden garden GARDÉN beds"), ["Garden", "beds"])
        XCTAssertEqual(FolderWords.parse("raised-beds"), ["raised", "beds"])
        XCTAssertEqual(FolderWords.parse("  "), [])
    }

    // A4
    func testLeaningMatchesWholeWordsWithoutCaseOrAccents() {
        let beds = Leaning.Folder(id: id(1), name: "Raised beds", words: ["garden"])
        let cafe = Leaning.Folder(id: id(2), name: "Corner shop", words: ["café"])
        XCTAssertEqual(Leaning.choose(title: "Garden path", body: "", folders: [beds], links: [:])?.folder, id(1))
        XCTAssertEqual(Leaning.choose(title: "Cafe sign", body: "", folders: [cafe], links: [:])?.word, "café")
        XCTAssertNil(Leaning.choose(title: "Gardening gloves", body: "", folders: [beds], links: [:]),
                     "a whole word only")
        XCTAssertEqual(Leaning.choose(title: "", body: "Weed the GARDEN.", folders: [beds], links: [:])?.folder,
                       id(1), "the text counts too")
    }

    func testTiesGoToMoreWordsThenMoreLinks() {
        let one = Leaning.Folder(id: id(1), name: "Bees", words: ["garden"])
        let two = Leaning.Folder(id: id(2), name: "Raised beds", words: ["garden", "soil"])
        let three = Leaning.Folder(id: id(3), name: "Allotment", words: ["garden"])
        let lean = Leaning.choose(title: "Garden soil", body: "", folders: [one, two], links: [id(1): 9])
        XCTAssertEqual(lean, Leaning.Lean(folder: id(2), word: "garden", shared: 2))
        XCTAssertEqual(Leaning.choose(title: "Garden", body: "", folders: [one, three], links: [id(3): 2])?.folder,
                       id(3))
        XCTAssertEqual(Leaning.choose(title: "Garden", body: "", folders: [one, three], links: [:])?.folder,
                       id(3), "then by name")
    }

    func testOnlyLooseIdeasLeanAndNothingIsStored() {
        let folders = [Leaning.Folder(id: id(1), name: "Raised beds", words: ["garden"])]
        let notes: [(id: UUID, title: String, body: String, folder: UUID?)] = [
            (id(10), "Garden gate", "", nil),
            (id(11), "Garden fence", "", id(1)),
            (id(12), "Bread", "", nil),
        ]
        let leans = Leaning.leans(notes: notes, folders: folders, edges: [])
        XCTAssertEqual(Set(leans.keys), [id(10)])
        XCTAssertEqual(Leaning.leans(notes: notes, folders: folders, edges: []), leans, "the same every time")
    }

    // A5, A6
    func testMoonsAreAsFullAsTheDayWasBusy() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let now = Date(timeIntervalSince1970: 1_793_000_000)
        let today: Date = calendar.startOfDay(for: now)
        let made: [Date] = [today + 60, today + 120, today + 180, today + 240,
                            today - day + 60, today - 3 * day + 10, today - 3 * day + 20]
        let days = MoonDays.days(created: made, now: now, count: 5, calendar: calendar)
        XCTAssertEqual(days.map(\.count), [4, 1, 0, 2, 0])
        XCTAssertEqual(days.map(\.phase), [4, 1, 0, 2, 0])
        XCTAssertEqual(days[0].start, today)
        XCTAssertEqual(MoonDays.days(created: [], now: now, count: 3, calendar: calendar).map(\.phase), [0, 0, 0])
        XCTAssertEqual(MoonDays.days(created: [], now: now, count: 500, calendar: calendar).count, MoonDays.most)
        let lit = MoonDays.lit([(id(1), today + 5), (id(2), today - day)], day: today, calendar: calendar)
        XCTAssertEqual(lit, [id(1)])
    }

    // A7, A8
    func testASharedIdeaKeepsOnlyTheHostOrTheApp() {
        XCTAssertEqual(SharedFrom.mark(text: "Look https://www.Example.org/a/b?c=1 now", app: "Notes"), "example.org")
        XCTAssertEqual(SharedFrom.mark(text: "Plain words", app: " Notes "), "Notes")
        XCTAssertNil(SharedFrom.mark(text: "Plain words", app: nil))
        XCTAssertEqual(SharedFrom.kept(SharedFrom.words("Keep these words only"), keep: [0, 2]), "Keep words")
    }

    func testSharedIdeasCircleTheFirstOfThem() {
        let groups = SharedCircle.groups([3: "example.org", 7: "Example.org", 9: "other.net", 12: "example.org"])
        XCTAssertEqual(groups[7], SharedCircle.Place(leader: 3, slot: 0, of: 2))
        XCTAssertEqual(groups[12], SharedCircle.Place(leader: 3, slot: 1, of: 2))
        XCTAssertNil(groups[3], "the first stays on its own path")
        XCTAssertNil(groups[9], "alone, it circles nothing")
        let a = SharedCircle.offset(SharedCircle.Place(leader: 0, slot: 0, of: 2), radius: 2, time: 0)
        let b = SharedCircle.offset(SharedCircle.Place(leader: 0, slot: 1, of: 2), radius: 2, time: 0)
        XCTAssertEqual(simd_length(a), 2, accuracy: 0.0001)
        XCTAssertEqual(simd_length(a + b), 0, accuracy: 0.0001, "a pair sits opposite")
    }

    // the fields read from a file written before them
    func testOlderFilesStillRead() throws {
        let note = try JSONDecoder().decode(Note.self, from: Data(#"{"title":"Old idea"}"#.utf8))
        XCTAssertNil(note.sharedFrom)
        let folder = try JSONDecoder().decode(NoteFolder.self, from: Data(#"{"name":"Old folder"}"#.utf8))
        XCTAssertEqual(folder.words, [])
        var shared = Note(title: "Shared")
        shared.sharedFrom = "example.org"
        let back = try JSONDecoder().decode(Note.self, from: JSONEncoder().encode(shared))
        XCTAssertEqual(back.sharedFrom, "example.org")
        var worded = NoteFolder(name: "Raised beds")
        worded.words = ["garden"]
        let again = try JSONDecoder().decode(NoteFolder.self, from: JSONEncoder().encode(worded))
        XCTAssertEqual(again.words, ["garden"])
    }
}
