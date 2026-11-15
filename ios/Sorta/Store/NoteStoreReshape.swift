import Foundation

// MARK: - Reshaping (S06c): split, join and collide, and their Undo
//
// Apart from NoteStore.swift, so the stores' own suites (backup) build
// without the reshape rules; every change goes through reshapeNotes.

extension NoteStore {
    /// One idea as a reshape found it, and where it stood in the list.
    struct Kept: Hashable {
        let index: Int
        let note: Note
    }

    /// What a split, a join or a collision changed, for Undo: each idea it
    /// changed or removed as it was, and the ideas it added. `result` is the
    /// idea to show after it: the first half, the joined idea, the new one.
    struct Reshape: Hashable {
        let before: [Kept]
        let added: [UUID]
        let result: UUID
    }

    /// Splits an idea after its sentence `after` (IdeaReshape.cut): the first
    /// half keeps its id, place and links; the second is new, beside it, in
    /// the same folder, with the hand links too and the `[[Title]]`s its words
    /// hold (IdeaReshape.splitLinks). Nil when there is no such cut.
    @discardableResult
    func split(_ id: UUID, after k: Int) -> Reshape? {
        guard let index = notes.firstIndex(where: { $0.id == id }) else { return nil }
        let note = notes[index]
        guard let halves = IdeaReshape.cut(title: note.title, body: note.body, after: k) else { return nil }
        let titles = titleIndex()
        let heldBy: [UUID] = notes.filter { $0.id != id && $0.links.contains(id) }.map(\.id)
        let namedBy: [UUID] = halves.titleChanged
            ? notes.filter { $0.id != id && resolve(wikiLinksIn: $0.body, index: titles).contains(id) }.map(\.id) : []
        let named: [UUID] = halves.titleFromBody ? resolve(wikiLinksIn: halves.secondTitle, index: titles) : []
        let secondID: UUID = makeID()
        let links = IdeaReshape.splitLinks(own: note.links, heldBy: heldBy, namedBy: namedBy,
                                           titleChanged: halves.titleChanged, secondTitleNames: named,
                                           first: id, second: secondID)
        var first = note
        first.title = halves.firstTitle
        first.body = halves.firstBody
        first.links = links.first
        first.updatedAt = Date()
        var second = Note(id: secondID, title: halves.secondTitle, body: halves.secondBody,
                          folderId: note.folderId, kind: note.kind, links: links.second, tags: note.tags)
        let spot = IdeaBoardDrop.settle(CGPoint(x: note.boardX + 40, y: note.boardY + 40), card: IdeaBoardDrop.tallest,
                                        others: notes.map { CGPoint(x: $0.boardX, y: $0.boardY) })
        second.boardX = Double(spot.x)
        second.boardY = Double(spot.y)
        reshapeNotes { list in
            list[index] = first
            list.insert(second, at: index + 1)
        }
        return Reshape(before: [Kept(index: index, note: note)], added: [secondID], result: id)
    }

    /// Joins `b` into `a`: its title, its body after `a`'s, its links and the
    /// ideas that pointed at it go to `a` (IdeaReshape.joined, joinLinks), and
    /// `b` goes, with every hand link to it. Nil for one idea or a missing one.
    @discardableResult
    func join(_ a: UUID, with b: UUID) -> Reshape? {
        guard a != b, let ia = notes.firstIndex(where: { $0.id == a }),
              let ib = notes.firstIndex(where: { $0.id == b }) else { return nil }
        let first = notes[ia], second = notes[ib]
        let titles = titleIndex()
        let text = IdeaReshape.joined(firstTitle: first.title, firstBody: first.body,
                                      secondTitle: second.title, secondBody: second.body)
        var pointedAtSecond: [UUID] = []
        var connected = Set(resolve(wikiLinksIn: text.body, index: titles))
        var holders: [Kept] = []
        for (i, n) in notes.enumerated() where n.id != a && n.id != b {
            let wiki = resolve(wikiLinksIn: n.body, index: titles)
            if n.links.contains(b) || wiki.contains(b) { pointedAtSecond.append(n.id) }
            if n.links.contains(a) || wiki.contains(a) { connected.insert(n.id) }
            if n.links.contains(b) { holders.append(Kept(index: i, note: n)) }
        }
        var joined = first
        joined.title = text.title
        joined.body = text.body
        joined.links = IdeaReshape.joinLinks(first: a, second: b, firstOwn: first.links, secondOwn: second.links,
                                             pointedAtSecond: pointedAtSecond, connected: connected)
        joined.updatedAt = Date()
        reshapeNotes { list in
            list[ia] = joined
            for held in holders { list[held.index].links.removeAll { $0 == b } }
            list.remove(at: ib)
        }
        return Reshape(before: [Kept(index: ia, note: first), Kept(index: ib, note: second)] + holders,
                       added: [], result: a)
    }

    /// The idea written between two collided ones: in To sort, linked to
    /// both; its first line is its title, the rest its body. Nil when nothing
    /// was written.
    @discardableResult
    func collide(_ a: UUID, _ b: UUID, text: String) -> Reshape? {
        let words = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !words.isEmpty, note(a) != nil, note(b) != nil else { return nil }
        let lines = words.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
        let made = create(title: String(lines[0]), body: lines.count > 1 ? String(lines[1]).trimmingCharacters(in: .whitespacesAndNewlines) : "")
        guard let index = notes.firstIndex(where: { $0.id == made.id }) else { return nil }
        let links: [UUID] = [a, b].filter { $0 != made.id }
        reshapeNotes { list in list[index].links = links }
        return Reshape(before: [], added: [made.id], result: made.id)
    }

    /// Undo of a reshape: the ideas it added go (with any hand link to
    /// them), and each idea it changed or removed is back as it was, where
    /// it stood.
    func undo(_ reshape: Reshape) {
        let added = Set(reshape.added)
        let kept: [Kept] = reshape.before.sorted(by: { $0.index < $1.index })
        reshapeNotes { list in
            list.removeAll { added.contains($0.id) }
            for i in list.indices where list[i].links.contains(where: { added.contains($0) }) {
                list[i].links.removeAll { added.contains($0) }
            }
            for one in kept {
                if let i = list.firstIndex(where: { $0.id == one.note.id }) {
                    list[i] = one.note
                } else {
                    list.insert(one.note, at: min(one.index, list.count))
                }
            }
        }
    }
}
