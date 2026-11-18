import XCTest
@testable import Sorta

/// S08 A6 and A7 on the simulator: keeping a split is one Undo step and
/// Undo restores the store exactly; the index rebuilds from the store off
/// the main thread, is kept out of backups and never in a capsule; and the
/// app's bundled prompts, tables and embedding table are the repository's
/// bytes and give the shared fixture vectors.
@MainActor
final class AIStoreTests: XCTestCase {
    private func freshStore() throws -> (NoteStore, URL) {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appendingPathComponent("vignette-notes.json")
        return (NoteStore(fileURL: file), file)
    }

    private func splitFixture(_ store: NoteStore) throws -> (capture: String, pieces: [(title: String, body: String, folderId: UUID?)], garden: UUID, house: UUID) {
        let fx = try AIFiles.fixture("model/split.json")
        let capture = try XCTUnwrap(fx["capture"]?.string)
        let garden = store.createFolder(name: "Garden"), house = store.createFolder(name: "House")
        let folders = [AISplit.Folder(id: garden.id.uuidString, name: "Garden", words: ["plants", "beans", "fence"]),
                       AISplit.Folder(id: house.id.uuidString, name: "House", words: ["paint", "repairs"])]
        let (verdict, pieces) = AISplit.check(try XCTUnwrap(fx["cases"]?.array?.first?["answer"]?.string), capture: capture, folders: folders)
        XCTAssertEqual(verdict, .ok)
        return (capture, pieces.map { ($0.title, $0.text, $0.folder.flatMap(UUID.init(uuidString:))) }, garden.id, house.id)
    }

    func testKeepingASplitCaptureIsOneUndoStep() throws {
        let (store, file) = try freshStore()
        let (capture, pieces, garden, house) = try splitFixture(store)
        let before1 = store.create(title: "Kite", body: "A red one.")
        let made = store.create(title: "Weekend", body: capture)
        store.update({ var n = made; n.tags = ["weekend"]; return n }())
        let after1 = store.create(title: "Bike bell", body: "")
        store.link(after1.id, before1.id)
        let before = store.notes
        store.waitForSaves()
        let savedBefore = try Data(contentsOf: file)

        let kept = try XCTUnwrap(store.keepSplit(pieces, replacing: made.id))
        XCTAssertNil(store.note(made.id), "the capture became its ideas")
        XCTAssertEqual(kept.added.count, 3)
        XCTAssertEqual(kept.result, kept.added[0])
        XCTAssertEqual(store.notes.map(\.id), [before1.id] + kept.added + [after1.id], "in the capture's place")
        let ideas = kept.added.compactMap { store.note($0) }
        XCTAssertEqual(ideas.map(\.title), ["Paint the shed blue", "Borrow a ladder", "Beans up the fence"])
        XCTAssertEqual(ideas.map(\.body), ["Paint the shed blue before spring.", "Ask the neighbours about sharing a ladder.",
                                           "Try growing beans up the fence."], "each idea's text is the person's own words")
        XCTAssertEqual(ideas.map(\.folderId), [house, nil, garden], "a folder picked, or To sort")
        XCTAssertTrue(ideas.allSatisfy { $0.tags == ["weekend"] })
        let spots = Set(ideas.map { "\($0.boardX),\($0.boardY)" })
        XCTAssertEqual(spots.count, 3, "each its own place on the board")

        store.undo(kept)
        XCTAssertEqual(store.notes, before, "one Undo puts the store back exactly")
        store.waitForSaves()
        XCTAssertEqual(try Data(contentsOf: file), savedBefore, "and the saved file too")
    }

    func testKeepingASplitOfTypedTextAddsTheIdeas() throws {
        let (store, _) = try freshStore()
        let (_, pieces, _, _) = try splitFixture(store)
        let kite = store.create(title: "Kite")
        let before = store.notes
        XCTAssertNil(store.keepSplit([]), "nothing to keep")
        XCTAssertNil(store.keepSplit(pieces, replacing: UUID()), "a capture that is not there")
        XCTAssertEqual(store.notes, before, "and nothing changed")
        let kept = try XCTUnwrap(store.keepSplit(pieces))
        XCTAssertEqual(store.notes.map(\.id), [kite.id] + kept.added)
        XCTAssertTrue(kept.before.isEmpty)
        store.undo(kept)
        XCTAssertEqual(store.notes, before)
    }

    func testTheIndexRebuildsFromTheStoreOffTheMainThreadAndIsNotInACapsule() throws {
        let (store, _) = try freshStore()
        let texts = try AIFiles.fixture("embeddings/texts.json")
        for item in try XCTUnwrap(texts["library"]?.array) { store.create(title: "", body: item["text"]!.string!) }
        store.create(title: "", body: "سقي الحديقة كل مساء")
        let snapshot = store.notes.map { AIIdea(id: $0.id.uuidString, title: $0.title, text: $0.body) }
        let embeddings = try AIEmbeddings.load(root: AIApp.root)
        XCTAssertThrowsError(try AIIndex.build(snapshot, embeddings: embeddings)) { XCTAssertEqual($0 as? AIError, .mainThread) }

        let index = AIIndex.standard()
        addTeardownBlock { try? FileManager.default.removeItem(at: index.file) }
        let built = try aiOffMain { try index.rebuild(snapshot, embeddings: embeddings) }
        XCTAssertEqual(built.map(\.id), store.notes.map(\.id.uuidString), "one entry per idea, from the store")
        XCTAssertEqual(built.filter { $0.vector == nil }.count, 1, "the Arabic idea has none: IdeaEchoes stands in")
        XCTAssertEqual(index.load(), built)
        XCTAssertEqual(index.file.lastPathComponent, "ai-index.bin")
        XCTAssertTrue(index.file.path.contains("/Library/Application Support/"))
        XCTAssertEqual(try index.file.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true,
                       "kept out of backups")
        let capsule = try SortaCapsule.data(notes: store.notes, folders: store.folders, savedAt: Date())
        let page = try SortaCapsule.html(notes: store.notes, folders: store.folders, edges: store.allEdges(),
                                         savedAt: Date(), zone: .current, picture: nil)
        let indexBytes = try Data(contentsOf: index.file)
        XCTAssertNil(capsule.range(of: Data("SRTI".utf8)), "the capsule holds ideas, not the index")
        XCTAssertNil(capsule.range(of: indexBytes.subdata(in: 8..<min(40, indexBytes.count))))
        XCTAssertFalse(page.contains("SRTI") || page.contains("ai-index"))
    }

    func testTheBundledFilesAreTheRepositorysBytes() throws {
        let fm = FileManager.default
        for folder in ["prompts", "tables"] {
            let repo = AIFiles.ai.appendingPathComponent(folder)
            let names = try fm.contentsOfDirectory(atPath: repo.path).sorted()
            XCTAssertEqual(try fm.contentsOfDirectory(atPath: AIApp.root.appendingPathComponent(folder).path).sorted(), names, folder)
            for name in names {
                XCTAssertEqual(try Data(contentsOf: AIApp.root.appendingPathComponent(folder).appendingPathComponent(name)),
                               try Data(contentsOf: repo.appendingPathComponent(name)), "\(folder)/\(name)")
            }
        }
        let sums = try String(contentsOf: AIApp.root.appendingPathComponent("prompts/SHA256SUMS"), encoding: .utf8)
        var listed = 0
        for line in sums.split(separator: "\n") {
            let parts = line.split(separator: " ", omittingEmptySubsequences: true).map(String.init)
            let data = try Data(contentsOf: AIApp.root.appendingPathComponent("prompts").appendingPathComponent(parts[1]))
            XCTAssertEqual(AISHA256.hex(data), parts[0], "\(parts[1]): the same SHA-256 as Android's")
            listed += 1
        }
        XCTAssertEqual(listed, 8)
        let table = "potion-base-4M/potion-base-4M-int8.bin"
        XCTAssertEqual(try Data(contentsOf: AIApp.root.appendingPathComponent(table)),
                       try Data(contentsOf: AIFiles.ai.appendingPathComponent("embeddings").appendingPathComponent(table)))
    }

    func testTheBundledTableGivesTheSharedVectors() throws {
        let embeddings = try AIEmbeddings.load(root: AIApp.root)
        let fx = try AIFiles.fixture("embeddings/vectors.json")
        let tolerance = try XCTUnwrap(fx["tolerance"]?.number)
        let entries = try XCTUnwrap(fx["vectors"]?.array)
        XCTAssertEqual(entries.count, 50)
        for e in entries {
            let text = e["text"]!.string!
            let ids = embeddings.tokenIDs(text)
            XCTAssertEqual(ids, e["ids"]!.array!.map { $0.int! }, text)
            XCTAssertEqual(AIEmbeddings.covered(text), e["covered"]!.bool!, text)
            if let want = e["vector"]?.array?.map({ $0.number! }) {
                let got = try XCTUnwrap(embeddings.vector(ids: ids), text)
                XCTAssertLessThanOrEqual(zip(got, want).map { abs($0 - $1) }.max() ?? 1, tolerance, text)
            }
        }
    }
}
