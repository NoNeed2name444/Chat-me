import Foundation

/// Stethoscore's library backup, as far as Sorta reads it: the file's
/// format, its manifest, and the archive opened to read one part at a time.
///
/// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
/// LibraryBackup.swift): the format, the decoder and the version (:33,36-40,
/// 47), the manifest's and the notes' names (:52-63), the failures without
/// their words (:65-74), the counts and the manifest (:76-92), the check
/// (:208-212) and the reading half of the archive (:417-450, 487-495).
///
/// A backup is a `.stethoscorebackup` file: a zip (MiniZip, stored) holding
/// manifest.json, library.json, progress.json, reviews.json, notes.json,
/// studylog.json, settings.plist and files (:11-20). Sorta takes its ideas
/// only (IdeaImport): it reads manifest.json and notes.json, and leaves every
/// other part unread.
enum LibraryBackup {

    static let format = "stethoscore-backup"

    /// Dates as ISO 8601, the way every file of the app writes them.
    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
    static let version = 1

    enum Name {
        static let manifest = "manifest.json"
        static let notes = "notes.json"
    }

    /// Why a backup was refused. Cases only: the words are step 4's.
    enum Failure: Error, Equatable {
        case notABackup, newerVersion, unreadable
    }

    struct Counts: Codable, Equatable {
        var sets = 0
        var cards = 0
        var questions = 0
        var notes = 0
        var scheduled = 0
        var studyDays = 0
        var files = 0
    }

    struct Manifest: Codable, Equatable {
        var format: String = LibraryBackup.format
        var version: Int = LibraryBackup.version
        var createdAt: Date
        var app: String
        var counts: Counts
    }

    /// Whether a manifest is one this version can restore.
    static func check(_ manifest: Manifest) throws {
        guard manifest.format == format else { throw Failure.notABackup }
        guard manifest.version <= version else { throw Failure.newerVersion }
    }

    // MARK: - the archive

    /// A backup, opened: its manifest checked, its parts read on request.
    struct Archive {
        let data: Data
        let entries: [String: Zip.Entry]
        let manifest: Manifest

        init(url: URL) throws {
            guard let data = try? Data(contentsOf: url, options: .alwaysMapped) else { throw Failure.unreadable }
            try self.init(data: data)
        }

        init(data: Data) throws {
            self.data = data
            var entries: [String: Zip.Entry] = [:]
            for entry in Zip.directory(of: data) where entries[entry.name] == nil { entries[entry.name] = entry }
            self.entries = entries
            guard let entry = entries[Name.manifest] else { throw Failure.notABackup }
            let body = Archive.read(entry, in: data, limit: 1 << 20)
            guard let body, let manifest = try? LibraryBackup.decoder.decode(Manifest.self, from: body) else {
                throw Failure.notABackup
            }
            try LibraryBackup.check(manifest)
            self.manifest = manifest
        }

        func part(_ name: String, limit: Int = 1 << 30) -> Data? {
            guard let entry = entries[name] else { return nil }
            return Archive.read(entry, in: data, limit: limit)
        }

        func decode<T: Decodable>(_ type: T.Type, _ name: String) -> T? {
            guard let body = part(name) else { return nil }
            return try? LibraryBackup.decoder.decode(type, from: body)
        }

        static func read(_ entry: Zip.Entry, in data: Data, limit: Int) -> Data? {
            guard entry.dataEnd <= data.count else { return nil }
            if entry.method == 0 {
                guard entry.compressed <= limit else { return nil }
                return data.subdata(in: entry.dataStart..<entry.dataEnd)
            }
            let limits = Zip.Limits(entryBytes: limit, totalBytes: limit, ratio: 1_100)
            return Zip.contents(of: entry, in: data, limits: limits, budget: limit).body
        }
    }
}
