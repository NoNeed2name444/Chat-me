import Foundation

/// The notes file inside a Stethoscore backup: its Ideas notes and folders.
/// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
/// LibraryBackupRunner.swift:34-37).
struct NoteBackup: Codable {
    var notes: [Note]
    var folders: [NoteFolder]
}

/// Stethoscore's ideas brought in once, by id: a library backup
/// (`.stethoscorebackup`, LibraryBackup) opened, its manifest checked, and
/// its notes.json merged into the store through `restore`, so an idea or a
/// folder already here stays as it is and a second import adds nothing.
/// Only manifest.json and notes.json are read; the rest of the backup is
/// left unread. Android's IdeaStore.importJson does the same with
/// Stethoscore's Android export.
enum IdeaImport {

    /// What an import added. The same on Android (ImportCounts).
    struct Counts: Equatable {
        /// The ideas added.
        var ideas = 0
        /// The folders added.
        var folders = 0
        /// The hand-made links the added ideas hold: the entries in their
        /// `links` lists, a link to an idea already here included. Each link
        /// is kept once, on the idea it was drawn from (NoteStore.link);
        /// `[[Title]]` links in a body are not counted.
        var links = 0
        /// The added ideas with a board position other than (0, 0).
        var placed = 0
    }

    /// Opens the backup at `url` and merges its ideas into `store`. Throws a
    /// LibraryBackup.Failure, with nothing changed: `.notABackup` for a file
    /// that isn't a zip, or has no readable manifest, or another app's;
    /// `.newerVersion` for a later version; `.unreadable` for a file that
    /// can't be read, or a backup whose notes.json is missing or can't be.
    @MainActor
    static func merge(backupAt url: URL, into store: NoteStore) throws -> Counts {
        let archive = try LibraryBackup.Archive(url: url)
        guard let backup = archive.decode(NoteBackup.self, LibraryBackup.Name.notes) else {
            throw LibraryBackup.Failure.unreadable
        }
        // the ideas and folders restore adds: those this store has no id for
        let haveNotes = Set(store.notes.map(\.id))
        let haveFolders = Set(store.folders.map(\.id))
        let added = backup.notes.filter { !haveNotes.contains($0.id) }
        var counts = Counts()
        counts.folders = backup.folders.filter { !haveFolders.contains($0.id) }.count
        counts.ideas = store.restore(notes: backup.notes, folders: backup.folders)
        counts.links = added.reduce(0) { $0 + $1.links.count }
        counts.placed = added.filter { $0.boardX != 0 || $0.boardY != 0 }.count
        return counts
    }
}
