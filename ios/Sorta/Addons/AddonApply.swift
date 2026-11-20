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

    /// A batch worked out ahead, on the add-on's own thread, from the store's
    /// arrays as they were when the call was dispatched. If the store still
    /// holds those very arrays when the call comes back, the main thread
    /// takes `result` as it is and does none of the work
    /// (NoteStore.applyAddonBatch); if the person changed the store
    /// meanwhile, the main thread applies the batch itself.
    struct Prepared {
        var notes: [Note]
        var folders: [NoteFolder]
        var now: Date
        var result: Result

        func fits(_ notes: [Note], _ folders: [NoteFolder], now: Date) -> Bool {
            now == self.now && AddonApply.same(notes, self.notes) && AddonApply.same(folders, self.folders)
        }
    }

    /// Whether two arrays are one: the same storage and the same count. While
    /// `Prepared` holds its arrays, the store can't write to that storage in
    /// place (it is shared, so a write copies it), so the same storage means
    /// the same contents.
    static func same<T>(_ a: [T], _ b: [T]) -> Bool {
        guard a.count == b.count else { return false }
        if a.isEmpty { return true }
        return a.withUnsafeBufferPointer { x in b.withUnsafeBufferPointer { y in x.baseAddress == y.baseAddress } }
    }

    static func prepare(_ ops: [AddonOp], notes: [Note], folders: [NoteFolder], now: Date) -> Prepared {
        Prepared(notes: notes, folders: folders, now: now,
                 result: apply(ops, to: notes, folders: Set(folders.map(\.id)), now: now))
    }

    /// Applies `ops` to `notes`. An idea or folder gone since the snapshot is
    /// skipped and listed; a note counts as changed, and gets `now` as its
    /// updatedAt, only if it ends up different. O(notes + ops).
    static func apply(_ ops: [AddonOp], to notes: [Note], folders: Set<UUID>, now: Date) -> Result {
        var list = notes
        // only the ideas the batch names are looked up, so only they are indexed; reading the id in
        // place keeps an unoptimised build from copying every note
        // each id's text is read as a UUID once, here, and the ops below use what was read
        var wanted = Set<UUID>()
        var first: [UUID?] = [], second: [UUID?] = []
        first.reserveCapacity(ops.count)
        second.reserveCapacity(ops.count)
        for op in ops {
            switch op {
            case let .capture(id, _, _, _, _):
                first.append(UUID(uuidString: id))
                second.append(nil)
            case let .link(a, b), let .unlink(a, b):
                let u = UUID(uuidString: a), v = UUID(uuidString: b)
                if let u { wanted.insert(u) }
                if let v { wanted.insert(v) }
                first.append(u)
                second.append(v)
            case let .tag(id, _), let .untag(id, _), let .file(id, _):
                let u = UUID(uuidString: id)
                if let u { wanted.insert(u) }
                first.append(u)
                second.append(nil)
            }
        }
        var index: [UUID: Int] = [:]
        index.reserveCapacity(wanted.count + ops.count)
        if !wanted.isEmpty {
            for i in notes.indices where wanted.contains(notes[i].id) { index[notes[i].id] = i }
        }
        var before: [UUID: (Int, Note)] = [:]
        before.reserveCapacity(ops.count)
        var added: [UUID] = []
        var addedSet = Set<UUID>()
        var skipped = Set<String>()
        func find(_ uuid: UUID?) -> Int? { uuid.flatMap { index[$0] } }
        func touch(_ i: Int) {
            let id = list[i].id
            if !addedSet.isEmpty, addedSet.contains(id) { return }
            if before[id] == nil { before[id] = (i, list[i]) }
        }
        for (n, op) in ops.enumerated() {
            switch op {
            case let .capture(_, title, body, x, y):
                guard let uuid = first[n] else { continue }
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
                let i = find(first[n]), j = find(second[n])
                guard let i, let j else {
                    if i == nil { skipped.insert(a) }
                    if j == nil { skipped.insert(b) }
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
                guard let i = find(first[n]) else { skipped.insert(id); continue }
                var more: [String] = []
                for t in tags where !list[i].tags.contains(t) && !more.contains(t) { more.append(t) }
                if !more.isEmpty { touch(i); list[i].tags += more }
            case let .untag(id, tags):
                guard let i = find(first[n]) else { skipped.insert(id); continue }
                if list[i].tags.contains(where: { tags.contains($0) }) {
                    touch(i)
                    list[i].tags.removeAll { tags.contains($0) }
                }
            case let .file(id, folder):
                guard let i = find(first[n]) else { skipped.insert(id); continue }
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
            // lowercase UUID text is ASCII, where String's order is JavaScript's UTF-16 order
            added: added.map(AddonStoreMap.text).sorted(),
            changed: changed.map { AddonStoreMap.text($0.note.id) }.sorted(),
            skipped: skipped.sorted(by: AddonText.less))
        return Result(notes: list, applied: applied, before: changed, added: added)
    }
}
