import Foundation
@testable import Sorta

/// A made-up Stethoscore backup for the store's and the import's tests: the
/// tests run in public, so none of it is a real idea, and none of it is
/// medical. It is written as Stethoscore wrote a backup: a stored zip
/// (MiniZip) with the manifest first, each part encoded with ISO 8601 dates
/// and sorted keys (LibraryBackup.swift:41-46 and LibraryBackupRunner.swift:
/// 103-146 in reference/stethoscore-aec2af1c/ios/RedPen/Shared/).
enum MadeUpBackup {
    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.sortedKeys]
        return e
    }()

    /// A count as an id, so a failing test names its idea plainly.
    static func id(_ n: Int) -> UUID {
        UUID(uuidString: String(format: "00000000-0000-4000-8000-%012X", n))!
    }

    static func date(_ seconds: TimeInterval) -> Date { Date(timeIntervalSince1970: 1_790_000_000 + seconds) }

    /// Two top-level folders, each with a folder inside it.
    static let folders: [NoteFolder] = [
        NoteFolder(id: id(101), name: "Garden", createdAt: date(100)),
        NoteFolder(id: id(102), name: "Raised beds", parentId: id(101), createdAt: date(200)),
        NoteFolder(id: id(103), name: "Kitchen", createdAt: date(300)),
        NoteFolder(id: id(104), name: "Bread", parentId: id(103), createdAt: date(400)),
    ]

    /// Four ideas: in a folder, in a folder inside one, and in none; linked
    /// by hand to each other and to an idea the backup doesn't hold (id 50);
    /// three placed on the board and one left at its centre; two saved from
    /// somewhere, with their sources.
    static let notes: [Note] = [
        Note(id: id(1), title: "Paint the shed green", body: "Two coats, after the rain.",
             folderId: id(101), kind: .idea, links: [], tags: ["weekend"], boardX: 120, boardY: -40,
             createdAt: date(1_000), updatedAt: date(1_500)),
        Note(id: id(2), title: "Seed swap list", body: "Beans and squash. See [[Paint the shed green]].",
             folderId: id(102), kind: .idea, links: [id(1)], tags: [], boardX: 0, boardY: 0,
             createdAt: date(2_000), updatedAt: date(2_000),
             source: NoteSource(kind: .card, setID: id(900), itemID: id(901), setName: "Spanish verbs")),
        Note(id: id(3), title: "Try a rye starter", body: "# Starter\n\nFeed it twice a day.",
             folderId: id(104), kind: .page, links: [id(1), id(2)], tags: ["kitchen", "slow"],
             boardX: -75.5, boardY: 210.25, createdAt: date(3_000), updatedAt: date(3_600),
             source: NoteSource(kind: .lecture, setID: id(902), page: 3, setName: "Baking at home")),
        Note(id: id(4), title: "A bell for the bike", body: "", folderId: nil, kind: .idea,
             links: [id(50)], tags: [], boardX: 0, boardY: 33, createdAt: date(4_000), updatedAt: date(4_000)),
    ]

    /// The links the four ideas hold, and how many of them sit off the centre.
    static let links = 4
    static let placed = 3

    static func manifest(format: String = LibraryBackup.format, version: Int = LibraryBackup.version) -> Data {
        var counts = LibraryBackup.Counts()
        counts.notes = notes.count
        var manifest = LibraryBackup.Manifest(createdAt: date(5_000), app: "Stethoscore", counts: counts)
        manifest.format = format
        manifest.version = version
        return try! encoder.encode(manifest)
    }

    static func notesFile(notes: [Note] = notes, folders: [NoteFolder] = folders) -> Data {
        try! encoder.encode(NoteBackup(notes: notes, folders: folders))
    }

    /// The parts a backup holds besides its ideas, none of them readable: an
    /// import that read any of them would fail.
    static let otherParts: [(String, Data)] = [
        ("library.json", Data("not a library".utf8)),
        ("progress.json", Data("{".utf8)),
        ("reviews.json", Data("[".utf8)),
        ("studylog.json", Data("?".utf8)),
        ("settings.plist", Data("not a plist".utf8)),
        ("files/sources/handout.pdf", Data(repeating: 7, count: 4_096)),
    ]

    /// The whole backup: the manifest first, the ideas, the other parts.
    static func parts() -> [(String, Data)] {
        [(LibraryBackup.Name.manifest, manifest()), (LibraryBackup.Name.notes, notesFile())] + otherParts
    }

    /// `parts` as a zip in `folder`, named `name`.
    static func write(_ parts: [(String, Data)], in folder: URL, named name: String = "Stethoscore Backup.stethoscorebackup") throws -> URL {
        let pieces = folder.appendingPathComponent("parts-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: pieces, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: pieces) }
        var entries: [(name: String, file: URL)] = []
        for (index, part) in parts.enumerated() {
            let file = pieces.appendingPathComponent("part\(index)")
            try part.1.write(to: file)
            entries.append((part.0, file))
        }
        let url = folder.appendingPathComponent(name)
        try MiniZip.write(files: entries, to: url)
        return url
    }

    /// A new, empty folder for one test.
    static func folder() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("sorta-store-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
