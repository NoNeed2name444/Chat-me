import XCTest
@testable import Sorta

/// The capsule (S06d A4-A5), as Android's CapsuleTest and CapsuleOfferTest:
/// the two shared fixtures (one as iOS writes it, one as Android does, the
/// same files in both test folders) read to the same ideas; this writer's
/// data is its own fixture's; a capsule written and read back, and opened
/// into the store, gives every idea, folder, link and stored field again, a
/// second time adds nothing; each refusal changes nothing; the page escapes
/// what it shows and its data can't close its block; and the offer comes
/// once a month at most. Made-up ideas, none medical.
@MainActor
final class CapsuleTests: XCTestCase {
    private func fixture(_ name: String) throws -> String {
        // beside this file in the source tree, or in the test bundle
        let here = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("Fixtures").appendingPathComponent(name)
        if let text = try? String(contentsOf: here, encoding: .utf8) { return text }
        let bundled = try XCTUnwrap(Bundle(for: CapsuleTests.self).url(forResource: (name as NSString).deletingPathExtension,
                                                                     withExtension: "html"), "no fixture \(name)")
        return try String(contentsOf: bundled, encoding: .utf8)
    }

    private func at(_ iso: String) -> Date { try! XCTUnwrap(SortaCapsule.isoDate(iso), iso) }
    private func id(_ n: String) -> UUID {
        UUID(uuidString: "00000000-0000-4000-8000-" + String(repeating: "0", count: 12 - n.count) + n)!
    }

    // the fixtures' ideas, to the millisecond, as Android holds them
    private var garden: NoteFolder { NoteFolder(id: id("A1"), name: "Garden", parentId: nil, createdAt: at("2026-10-01T08:00:00.100Z")) }
    private var bulbs: NoteFolder { NoteFolder(id: id("A2"), name: "Bulbs", parentId: id("A1"), createdAt: at("2026-10-01T08:05:00.200Z")) }
    private var shed: Note {
        Note(id: id("1"), title: "Paint the shed green", body: "Two coats, after the rain. Check [[Buy brushes]] first.",
             folderId: id("A1"), kind: .idea, links: [id("2")], tags: ["outside"], boardX: 120.5, boardY: -40,
             createdAt: at("2026-10-03T23:15:00.250Z"), updatedAt: at("2026-10-04T07:00:00.750Z"))
    }
    private var brushes: Note {
        Note(id: id("2"), title: "Buy brushes", createdAt: at("2026-10-05T07:00:00.125Z"), updatedAt: at("2026-10-05T07:00:00.125Z"))
    }
    private var plan: Note {
        Note(id: id("3"), title: "Bulb planting plan", body: "Tulips first </script> then crocus & co?", folderId: id("A2"),
             kind: .page, tags: ["spring", "bulbs"], boardX: -75.5, boardY: 210.25,
             createdAt: at("2026-10-06T10:30:00.500Z"), updatedAt: at("2026-10-07T11:45:00.001Z"))
    }
    private var notes: [Note] { [shed, brushes, plan] }
    private var folders: [NoteFolder] { [garden, bulbs] }
    /// The source iOS's fixture gives the plan.
    private var lecture: NoteSource { NoteSource(kind: .lecture, setID: id("B1"), page: 3, setName: "Garden notes") }

    /// A note's stored fields, times to whole seconds when `seconds` (iOS
    /// writes whole seconds).
    private func fields(_ n: Note, seconds: Bool = false) -> [String] {
        func t(_ d: Date) -> String { seconds ? "\(Int(d.timeIntervalSince1970.rounded(.down)))" : "\(Int((d.timeIntervalSince1970 * 1000).rounded()))" }
        return [n.id.uuidString, n.title, n.body, n.folderId?.uuidString ?? "-", n.kind.rawValue,
                n.links.map(\.uuidString).joined(separator: ","), n.tags.joined(separator: ","),
                "\(n.boardX)", "\(n.boardY)", t(n.createdAt), t(n.updatedAt)]
    }
    private func fields(_ f: NoteFolder, seconds: Bool = false) -> [String] {
        let ms: Int = seconds ? Int(f.createdAt.timeIntervalSince1970.rounded(.down)) : Int((f.createdAt.timeIntervalSince1970 * 1000).rounded())
        return [f.id.uuidString, f.name, f.parentId?.uuidString ?? "-", "\(ms)"]
    }

    /// The data block's JSON as a value: nulls dropped (iOS leaves a nil
    /// out, Android writes null), numbers as doubles, ids in upper case,
    /// and times to the second (iOS writes whole seconds).
    private func value(_ any: Any) -> AnyHashable {
        switch any {
        case let d as [String: Any]:
            var out: [String: AnyHashable] = [:]
            for (k, v) in d where !(v is NSNull) { out[k] = value(v) }
            return out
        case let a as [Any]: return a.map(value)
        case let s as String:
            if let date = SortaCapsule.isoDate(s) { return "t\(Int(date.timeIntervalSince1970.rounded(.down)))" }
            return UUID(uuidString: s)?.uuidString ?? s
        case let n as NSNumber: return n.doubleValue
        default: return "\(any)"
        }
    }
    private func block(_ html: String) throws -> Any {
        try JSONSerialization.jsonObject(with: Data(try XCTUnwrap(SortaCapsule.block(in: html)).utf8))
    }

    func testBothFixturesReadToTheSameIdeas() throws {
        let android = try SortaCapsule.read(fixture("capsule-from-android.html"))
        XCTAssertEqual(android.notes.map { fields($0) }, notes.map { fields($0) })
        XCTAssertEqual(android.folders.map { fields($0) }, folders.map { fields($0) })
        let ios = try SortaCapsule.read(fixture("capsule-from-ios.html"))
        XCTAssertEqual(ios.notes.map { fields($0, seconds: true) }, notes.map { fields($0, seconds: true) }, "iOS's: the same ideas, to the second")
        XCTAssertEqual(ios.folders.map { fields($0, seconds: true) }, folders.map { fields($0, seconds: true) })
        XCTAssertEqual(ios.notes[2].source, lecture, "a note's source, as written")
        XCTAssertNil(android.notes[2].source, "Android's writes none")
    }

    func testThisWritersDataIsItsFixtures() throws {
        var mine = notes
        mine[2].source = lecture
        let written = try JSONSerialization.jsonObject(with: SortaCapsule.data(notes: mine, folders: folders, savedAt: at("2026-11-14T19:05:00Z")))
        XCTAssertEqual(value(written), value(try block(fixture("capsule-from-ios.html"))))
        // and Android's holds the same keys
        let android = try XCTUnwrap(value(try block(fixture("capsule-from-android.html"))) as? [String: AnyHashable])
        XCTAssertEqual(Set(try XCTUnwrap(value(written) as? [String: AnyHashable]).keys), Set(android.keys))
    }

    func testACapsuleWrittenAndReadBackHoldsEveryField() throws {
        let html = try SortaCapsule.html(notes: notes, folders: folders, edges: [(id("1"), id("2"))], savedAt: at("2026-11-14T19:05:00Z"),
                                    zone: TimeZone(identifier: "Africa/Cairo")!, picture: nil)
        // iOS writes whole seconds, as its store does
        let back = try SortaCapsule.read(html)
        XCTAssertEqual(back.notes.map { fields($0, seconds: true) }, notes.map { fields($0, seconds: true) })
        XCTAssertEqual(back.folders.map { fields($0, seconds: true) }, folders.map { fields($0, seconds: true) })

        // through the store, as Open a capsule does: everything comes in, once
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        XCTAssertEqual(try SortaCapsule.merge(Data(html.utf8), into: store), IdeaImport.Counts(ideas: 3, folders: 2, links: 1, placed: 2))
        XCTAssertEqual(store.notes.map { fields($0, seconds: true) }, notes.map { fields($0, seconds: true) })
        XCTAssertEqual(try SortaCapsule.merge(Data(html.utf8), into: store), IdeaImport.Counts(), "a second time adds nothing")
        // Android's capsule of the same ideas: its ids are lower case, and nothing is added twice
        XCTAssertEqual(try SortaCapsule.merge(Data(fixture("capsule-from-android.html").utf8), into: store), IdeaImport.Counts())
        XCTAssertEqual(store.notes.count, 3)

        // an empty store takes Android's, to the millisecond, its links and folders whole
        let other = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: other) }
        let fresh = NoteStore(fileURL: other.appendingPathComponent("vignette-notes.json"))
        XCTAssertEqual(try SortaCapsule.merge(Data(fixture("capsule-from-android.html").utf8), into: fresh),
                       IdeaImport.Counts(ideas: 3, folders: 2, links: 1, placed: 2))
        XCTAssertEqual(fresh.notes.map { fields($0) }, notes.map { fields($0) })
        XCTAssertEqual(fresh.folders.map { fields($0) }, folders.map { fields($0) })
        XCTAssertEqual(fresh.allEdges().count, 1)
        XCTAssertEqual(fresh.path(to: id("A2")).map(\.id), [id("A1"), id("A2")], "a folder inside a folder")
    }

    func testEachRefusalChangesNothing() throws {
        let folder = try MadeUpBackup.folder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let store = NoteStore(fileURL: folder.appendingPathComponent("vignette-notes.json"))
        func refused(_ text: String) -> SortaCapsule.Failure? {
            do { _ = try SortaCapsule.merge(Data(text.utf8), into: store); return nil } catch { return error as? SortaCapsule.Failure }
        }
        func page(_ json: String) -> String {
            "<!doctype html><p>Hello</p><script type=\"application/json\" id=\"sorta-capsule\">\(json)</script>"
        }
        XCTAssertEqual(refused("<!doctype html><title>Recipes</title><p>Soup</p>"), .notACapsule, "a page with no data")
        XCTAssertEqual(refused(page("{\"format\":\"stethoscore-android-v1\",\"notes\":[]}")), .notACapsule, "a Stethoscore export")
        XCTAssertEqual(refused(page("{\"format\":\"sorta-capsule\",\"notes\":[]}")), .notACapsule, "no version")
        XCTAssertEqual(refused(page("{\"format\":\"sorta-capsule\",\"version\":2,\"notes\":[]}")), .newerVersion, "a later one")
        XCTAssertEqual(refused(page("{\"format\": sorta")), .unreadable, "not JSON")
        XCTAssertEqual(refused(page("{\"format\":\"sorta-capsule\",\"version\":1,\"notes\":{}}")), .unreadable, "notes not a list")
        XCTAssertEqual(refused(page("{\"format\":\"sorta-capsule\",\"version\":1,\"folders\":7}")), .unreadable, "folders not a list")
        XCTAssertEqual(refused(String(repeating: "x", count: SortaCapsule.maxBytes + 1)), .tooLarge, "over 32 MB")
        XCTAssertTrue(store.notes.isEmpty && store.folders.isEmpty)
        // a capsule of an earlier shape, with no folders, is read
        XCTAssertEqual(try SortaCapsule.merge(Data(page("{\"format\":\"sorta-capsule\",\"version\":1,\"notes\":[{\"id\":\"\(id("9").uuidString.lowercased())\",\"title\":\"Fix the gate\"}]}").utf8),
                                         into: store).ideas, 1)
    }

    func testThePageShowsEveryIdeaAndEscapesIt() throws {
        var tricky = shed
        tricky.title = "Shed <b>now</b> & \"then\""
        let all = [tricky, brushes, plan]
        let html = try SortaCapsule.html(notes: all, folders: folders, edges: [(id("1"), id("2"))], savedAt: at("2026-11-14T19:05:00Z"),
                                    zone: TimeZone(identifier: "UTC")!, picture: SortaCapsule.Picture(png: Data([1, 2, 3]), width: 300, height: 200))
        XCTAssertTrue(html.contains("Shed &lt;b&gt;now&lt;/b&gt; &amp; &quot;then&quot;"), "its title escaped")
        XCTAssertFalse(html.contains("<b>now"), "no markup from an idea")
        XCTAssertEqual(html.components(separatedBy: "<script").count - 1, 1, "one script block, the data")
        XCTAssertEqual(html.components(separatedBy: "</script").count - 1, 1, "which closes once")
        for words in ["<h1>Sorta capsule</h1>", "Saved 14 November 2026 at 7:05 PM. 3 ideas in 2 folders.", "<h2>To sort</h2>",
                      "<h2>Garden</h2>", "<h2>Garden \u{203A} Bulbs</h2>", "Page, caught 6 October 2026 at 10:30 AM", "Linked to Buy brushes",
                      "Tulips first &lt;/script&gt; then crocus &amp; co?", "data:image/png;base64,AQID", "width=\"300\" height=\"200\"",
                      SortaCapsule.Words.restore] {
            XCTAssertTrue(html.contains(words), "the page has \(words)")
        }
        // the folder sections come after To sort, Garden before its Bulbs
        let toSort = try XCTUnwrap(html.range(of: "<h2>To sort</h2>")).lowerBound
        let gardenAt = try XCTUnwrap(html.range(of: "<h2>Garden</h2>")).lowerBound
        let bulbsAt = try XCTUnwrap(html.range(of: "Garden \u{203A} Bulbs")).lowerBound
        XCTAssertTrue(toSort < gardenAt && gardenAt < bulbsAt)
        XCTAssertEqual(SortaCapsule.fileName(at("2026-11-14T19:05:00Z"), zone: TimeZone(identifier: "UTC")!), "Sorta capsule 2026-11-14.html")
        XCTAssertEqual(SortaCapsule.Words.counts(ideas: 1, folders: 0), "1 idea.")
        XCTAssertEqual(SortaCapsule.Words.counts(ideas: 2, folders: 1), "2 ideas in 1 folder.")
        XCTAssertThrowsError(try SortaCapsule.read("<p>no data</p>")) { XCTAssertEqual($0 as? SortaCapsule.Failure, .notACapsule) }
    }

    func testTheDataCantCloseItsBlock() throws {
        XCTAssertEqual(SortaCapsule.scriptSafe("</script> & \u{2028}"), "\\u003c/script\\u003e \\u0026 \\u2028")
        var nasty = brushes
        nasty.title = "</script><script>alert(1)</script>"
        nasty.body = "<!-- & -->"
        let html = try SortaCapsule.html(notes: [nasty], folders: [], edges: [], savedAt: Date(timeIntervalSince1970: 0),
                                    zone: TimeZone(identifier: "UTC")!, picture: nil)
        XCTAssertEqual(html.components(separatedBy: "<script").count - 1, 1)
        let back = try XCTUnwrap(try SortaCapsule.read(html).notes.first)
        XCTAssertEqual(back.title, nasty.title)
        XCTAssertEqual(back.body, nasty.body)
    }

    // MARK: the offer, as Android's CapsuleOfferTest

    func testTheOfferIsDueAMonthAfterTheLatestOfFirstSeenOfferedAndSaved() {
        let day: TimeInterval = 24 * 60 * 60
        let t = Date(timeIntervalSince1970: 1_793_000_000)
        XCTAssertEqual(CapsuleOffer.interval, 30 * day)
        XCTAssertFalse(CapsuleOffer.due(now: t + 90 * day, ideas: 0, firstSeen: t, offered: nil, saved: nil), "no ideas")
        XCTAssertFalse(CapsuleOffer.due(now: t + 90 * day, ideas: 5, firstSeen: nil, offered: nil, saved: nil), "never seen with ideas")
        XCTAssertFalse(CapsuleOffer.due(now: t + 29 * day, ideas: 5, firstSeen: t, offered: nil, saved: nil), "the first month")
        XCTAssertTrue(CapsuleOffer.due(now: t + 30 * day, ideas: 5, firstSeen: t, offered: nil, saved: nil), "a month after the first launch with ideas")
        XCTAssertFalse(CapsuleOffer.due(now: t + 40 * day, ideas: 5, firstSeen: t, offered: t + 30 * day, saved: nil), "offered 10 days ago")
        XCTAssertTrue(CapsuleOffer.due(now: t + 60 * day, ideas: 5, firstSeen: t, offered: t + 30 * day, saved: nil), "offered a month ago")
        XCTAssertFalse(CapsuleOffer.due(now: t + 65 * day, ideas: 5, firstSeen: t, offered: t + 30 * day, saved: t + 60 * day), "saved 5 days ago")
        XCTAssertTrue(CapsuleOffer.due(now: t + 90 * day, ideas: 5, firstSeen: t, offered: t + 30 * day, saved: t + 60 * day), "saved a month ago")
    }

    func testTheOfferNeverComesTwiceInAMonth() {
        let day: TimeInterval = 24 * 60 * 60
        let t = Date(timeIntervalSince1970: 1_793_000_000)
        var offered: Date?
        var shown = 0
        for d in 30...89 {
            let now = t + Double(d) * day
            if CapsuleOffer.due(now: now, ideas: 3, firstSeen: t, offered: offered, saved: nil) {
                shown += 1
                offered = now
            }
        }
        XCTAssertEqual(shown, 2, "days 30 and 60")
    }
}
