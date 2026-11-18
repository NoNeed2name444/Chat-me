#if canImport(CoreGraphics)
import CoreGraphics
#endif
import Foundation

// MARK: - Add-ons (S07b): the store's side of a call
//
// The snapshot a call reads, mapped from the store's arrays on the add-on's
// thread, and the call's writes applied to the notes in one pass on the
// main thread (NoteStore.applyAddonBatch, in NoteStoreReshape.swift).

enum AddonStoreMap {
    /// An id as add-ons see it: lowercase UUID text, as Android's.
    static func text(_ id: UUID) -> String { id.uuidString.lowercased() }

    static func ms(_ date: Date) -> Int64 { Int64((date.timeIntervalSince1970 * 1000).rounded()) }

    static func date(_ ms: Int64) -> Date { Date(timeIntervalSince1970: Double(ms) / 1000) }

    /// The store's notes and folders as a snapshot. The arrays are the
    /// store's own, copied on the main thread in O(1); this runs on the
    /// add-on's thread.
    static func snapshot(notes: [Note], folders: [NoteFolder]) -> AddonSnapshot {
        AddonSnapshot(
            ideas: notes.map { n in
                AddonIdea(id: text(n.id), title: n.title, text: n.body, kind: n.kind.rawValue,
                          folder: n.folderId.map(text), tags: n.tags, links: n.links.map(text),
                          createdAt: ms(n.createdAt), updatedAt: ms(n.updatedAt), x: n.boardX, y: n.boardY)
            },
            folders: folders.map { f in
                AddonFolder(id: text(f.id), name: f.name, parent: f.parentId.map(text), words: f.words)
            })
    }

    /// Where the n-th idea lands: the store's own golden-angle spiral, then
    /// the nearest spot where its card covers no other (NoteStore.create).
    static func place(_ n: Int, _ others: [(Double, Double)]) -> (Double, Double) {
        let k = Double(n)
        let angle = k * 2.399963
        let radius = 90 * k.squareRoot()
        let spot = IdeaBoardDrop.settle(CGPoint(x: radius * cos(angle), y: radius * sin(angle)),
                                        card: IdeaBoardDrop.tallest,
                                        others: others.map { CGPoint(x: $0.0, y: $0.1) })
        return (Double(spot.x), Double(spot.y))
    }
}

/// A call's writes applied to the notes, as one change.
enum AddonApply {
    struct Result {
        var notes: [Note]
        var applied: AddonApplied
        /// Each changed note as it was, and where it stood, for Undo.
        var before: [(index: Int, note: Note)]
        var added: [UUID]
    }

    /// Applies `ops` to `notes`. An idea or folder gone since the snapshot is
    /// skipped and listed; a note counts as changed, and gets `now` as its
    /// updatedAt, only if it ends up different. O(notes + ops).
    static func apply(_ ops: [AddonOp], to notes: [Note], folders: Set<UUID>, now: Date) -> Result {
        var list = notes
        var index: [UUID: Int] = [:]
        index.reserveCapacity(list.count)
        for (i, n) in list.enumerated() { index[n.id] = i }
        var before: [UUID: (Int, Note)] = [:]
        var added: [UUID] = []
        var addedSet = Set<UUID>()
        var skipped = Set<String>()
        func find(_ id: String) -> Int? { UUID(uuidString: id).flatMap { index[$0] } }
        func touch(_ i: Int) {
            let id = list[i].id
            if before[id] == nil, !addedSet.contains(id) { before[id] = (i, list[i]) }
        }
        for op in ops {
            switch op {
            case let .capture(id, title, body, x, y):
                guard let uuid = UUID(uuidString: id) else { continue }
                var note = Note(id: uuid, title: title, body: body, folderId: nil, kind: .idea)
                note.boardX = x
                note.boardY = y
                note.createdAt = now
                note.updatedAt = now
                index[uuid] = list.count
                list.append(note)
                added.append(uuid)
                addedSet.insert(uuid)
            case let .link(a, b), let .unlink(a, b):
                guard let i = find(a), let j = find(b) else {
                    if find(a) == nil { skipped.insert(a) }
                    if find(b) == nil { skipped.insert(b) }
                    continue
                }
                let ia = list[i].id, ib = list[j].id
                if case .link = op {
                    if !list[i].links.contains(ib), !list[j].links.contains(ia) {
                        touch(i)
                        list[i].links.append(ib)
                    }
                } else {
                    if list[i].links.contains(ib) { touch(i); list[i].links.removeAll { $0 == ib } }
                    if list[j].links.contains(ia) { touch(j); list[j].links.removeAll { $0 == ia } }
                }
            case let .tag(id, tags):
                guard let i = find(id) else { skipped.insert(id); continue }
                var more: [String] = []
                for t in tags where !list[i].tags.contains(t) && !more.contains(t) { more.append(t) }
                if !more.isEmpty { touch(i); list[i].tags += more }
            case let .untag(id, tags):
                guard let i = find(id) else { skipped.insert(id); continue }
                if list[i].tags.contains(where: { tags.contains($0) }) {
                    touch(i)
                    list[i].tags.removeAll { tags.contains($0) }
                }
            case let .file(id, folder):
                guard let i = find(id) else { skipped.insert(id); continue }
                var target: UUID?
                if let folder {
                    guard let f = UUID(uuidString: folder), folders.contains(f) else { skipped.insert(folder); continue }
                    target = f
                }
                if list[i].folderId != target { touch(i); list[i].folderId = target }
            }
        }
        var changed: [(index: Int, note: Note)] = []
        for (_, kept) in before {
            let i = kept.0
            if list[i] != kept.1 {
                list[i].updatedAt = now
                changed.append((index: i, note: kept.1))
            }
        }
        changed.sort { $0.index < $1.index }
        let applied = AddonApplied(
            added: added.map(AddonStoreMap.text).sorted(by: AddonText.less),
            changed: changed.map { AddonStoreMap.text($0.note.id) }.sorted(by: AddonText.less),
            skipped: skipped.sorted(by: AddonText.less))
        return Result(notes: list, applied: applied, before: changed, added: added)
    }
}
