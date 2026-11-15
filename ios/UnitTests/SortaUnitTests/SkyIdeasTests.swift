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

    /// A4: a leaning comet is drawn part of the way to its folder, never
    /// inside the folder's disc, where a tap on it would choose the folder
    /// (the ideas red on Chat-me run 43610223855).
    func testALeaningCometStopsOutsideItsFolder() {
        let folder = SIMD3<Float>(0, 0, 0)
        near(Leaning.place(own: SIMD3(10, 0, 0), folder: folder, size: 1), SIMD3(7, 0, 0), "far off: the pull alone")
        near(Leaning.place(own: SIMD3(0, 2, 0), folder: folder, size: 1), SIMD3(0, 3, 0),
             "close: kept three radii out, on its own side")
        near(Leaning.place(own: SIMD3(-0.5, 0, 0), folder: folder, size: 2), SIMD3(-6, 0, 0), "inside: pushed out")
        near(Leaning.place(own: folder, folder: folder, size: 1), SIMD3(3, 0, 0), "on top of it: out along x")
        let none = Leaning.place(own: SIMD3(1, 0, 0), folder: folder, size: 0)
        XCTAssertEqual(none.x, 0.7, accuracy: 1e-6, "no size, no keep-out")
        let centre = SIMD3<Float>(1, 1, 1)
        for k in 0..<50 {
            let own = SIMD3<Float>(Float(k % 7) - 3, Float(k % 5) - 2, Float(k % 3) - 1) * 0.4
            let at = Leaning.place(own: own, folder: centre, size: 0.5)
            XCTAssertGreaterThanOrEqual(simd_length(at - centre), 1.5 - 1e-4, "never within 3 radii: \(own) -> \(at)")
        }
    }

    /// A4, v14: three radii apart in space can still be one behind the
    /// other on screen, where the folder's disc took the comet's taps
    /// (Chat-me run 43676492822). Seen through a camera at the origin
    /// looking down -z, 1000 points per unit at depth 1, the comet's disc
    /// ends up at least the 44-point gap clear of the folder's, at its own
    /// depth, out along its lean on screen.
    func testALeaningCometIsClearOfItsFolderOnScreen() {
        let view = Leaning.View(eye: .zero, ahead: SIMD3(0, 0, -1), aside: SIMD3(1, 0, 0), focal: 1000)
        func clearance(_ comet: SIMD3<Float>, _ cr: Float, _ folder: SIMD3<Float>, _ fr: Float) -> Float {
            let c = SIMD2<Float>(comet.x, comet.y) * (view.focal / -comet.z)
            let f = SIMD2<Float>(folder.x, folder.y) * (view.focal / -folder.z)
            return simd_length(c - f) - fr * view.focal / -folder.z - cr * view.focal / -comet.z
        }
        let folder = SIMD3<Float>(0, 0, -20)
        // behind the folder, three radii away along the sight line, and a little up: on screen inside its disc
        let hidden = Leaning.place(own: SIMD3(0, 0.2, -30), folder: folder, size: 1)
        XCTAssertLessThan(clearance(hidden, 0.3, folder, 1), 0, "the case: hidden on screen first")
        let moved = Leaning.clearOnScreen(comet: hidden, cometRadius: 0.3, folder: folder, folderRadius: 1, view: view)
        XCTAssertGreaterThanOrEqual(clearance(moved, 0.3, folder, 1), 44 - 0.01, "clear by the gap")
        XCTAssertEqual(moved.z, hidden.z, accuracy: 1e-4, "at its own depth")
        XCTAssertGreaterThan(moved.y, hidden.y, "out along its lean on screen (up)")
        XCTAssertEqual(moved.x, 0, accuracy: 1e-4, "out along its lean on screen (up)")
        // exactly behind it: across the screen along the camera's aside
        let behind = Leaning.clearOnScreen(comet: SIMD3(0, 0, -26), cometRadius: 0.3, folder: folder, folderRadius: 1,
                                           view: view)
        XCTAssertGreaterThanOrEqual(clearance(behind, 0.3, folder, 1), 44 - 0.01, "right behind: clear")
        XCTAssertGreaterThan(behind.x, 0, "right behind: along the aside")
        XCTAssertEqual(behind.y, 0, accuracy: 1e-4, "right behind: along the aside")
        // already clear: left where it is
        let far = SIMD3<Float>(5, 0, -20)
        near(Leaning.clearOnScreen(comet: far, cometRadius: 0.3, folder: folder, folderRadius: 1, view: view), far,
             "clear already: unmoved")
        // behind the eye: unmoved
        let back = SIMD3<Float>(0, 0.1, 5)
        near(Leaning.clearOnScreen(comet: back, cometRadius: 0.3, folder: folder, folderRadius: 1, view: view), back,
             "behind the eye: unmoved")
        // every way round, near and far: never short of the gap
        for k in 0..<60 {
            let f = SIMD3<Float>(Float(k % 5) - 2, Float(k % 3) - 1, -10 - Float(k % 7) * 3)
            let c = f + SIMD3<Float>((Float(k % 4) - 1.5) * 0.2, (Float(k % 6) - 2.5) * 0.1, (Float(k % 9) - 4) * 1.5)
            guard -c.z > 1 else { continue }
            let at = Leaning.clearOnScreen(comet: c, cometRadius: 0.25, folder: f, folderRadius: 0.8, view: view)
            XCTAssertGreaterThanOrEqual(clearance(at, 0.25, f, 0.8), 44 - 0.01, "\(c) by \(f)")
        }
    }

    private func near(_ got: SIMD3<Float>, _ want: SIMD3<Float>, _ what: String,
                      file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertLessThan(simd_length(got - want), 1e-5, "\(what): want \(want), got \(got)", file: file, line: line)
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
        XCTAssertEqual(days.map(\.phase), [3, 1, 0, 2, 0])
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

    /// v14: a Space name keeps inside the map's free area. On a phone 390
    /// wide and 844 tall, with 180 points of glass at the top (the strip
    /// and the lens row) and 90 at the foot, a comet's name by the right
    /// edge slides in, never past the comet's middle; one that would run
    /// under the strip goes under its body; one in the open stays put.
    func testANameKeepsInsideTheMapsFreeArea() {
        let room = GraphNameRoom.Room(width: 390, height: 844, glass: GraphInsets(top: 180, left: 0, bottom: 90, right: 0),
                                      edge: GraphNameRoom.edge)
        XCTAssertEqual(room.maxX, 183)
        XCTAssertEqual(room.maxY, 230)
        XCTAssertEqual(room.minY, -320)
        let half = SIMD2<Float>(60, 14)
        // in the open: as it was
        let open = GraphNameRoom.keep(above: SIMD2(0, 40), under: SIMD2(0, -40), half: half, room: room)
        XCTAssertFalse(open.under)
        XCTAssertEqual(open.across, 0)
        // by the right edge: in, until its right side is 12 from the edge
        let right = GraphNameRoom.keep(above: SIMD2(160, 40), under: SIMD2(160, -40), half: half, room: room)
        XCTAssertEqual(right.across, -37)
        XCTAssertEqual(160 + right.across + half.x, room.maxX)
        // past the edge: no further than its own half width
        let past = GraphNameRoom.keep(above: SIMD2(260, 40), under: SIMD2(260, -40), half: half, room: room)
        XCTAssertEqual(past.across, -60)
        // by the left edge: out
        XCTAssertEqual(GraphNameRoom.keep(above: SIMD2(-170, 40), under: SIMD2(-170, -40), half: half, room: room).across, 47)
        // under the strip's glass: under its body instead
        let top = GraphNameRoom.keep(above: SIMD2(0, 225), under: SIMD2(0, 150), half: half, room: room)
        XCTAssertTrue(top.under)
        // with no room under it either, it stays above
        let tight = GraphNameRoom.Room(minX: -100, maxX: 100, minY: 0, maxY: 50)
        XCTAssertFalse(GraphNameRoom.keep(above: SIMD2(0, 45), under: SIMD2(0, 5), half: half, room: tight).under)
    }
}
