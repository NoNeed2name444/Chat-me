import Foundation
import XCTest
@testable import Sorta

/// Shared pieces of the add-on tests (S07b). Made-up ideas, nothing medical.
enum AddonTestFiles {
    private final class Marker {}

    /// addons/fixtures or addons/conformance, as the test bundle carries them.
    static func folder(_ name: String) throws -> URL {
        try XCTUnwrap(Bundle(for: Marker.self).url(forResource: name, withExtension: nil), "\(name)/ in the test bundle")
    }

    static func fixtures(_ path: String) throws -> URL { try folder("fixtures").appendingPathComponent(path) }

    static func temporaryFolder() throws -> URL {
        let u = FileManager.default.temporaryDirectory.appendingPathComponent("addons-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// A store of its own, in a temporary folder.
    @MainActor
    static func store(_ test: XCTestCase) throws -> NoteStore {
        let f = try temporaryFolder()
        test.addTeardownBlock { try? FileManager.default.removeItem(at: f) }
        return NoteStore(fileURL: f.appendingPathComponent("vignette-notes.json"))
    }
}

/// A NoteStore filled from the conformance store.json, as the cases read it.
@MainActor
final class AddonNoteStoreCases: AddonCaseStore {
    let store: NoteStore

    init(_ store: NoteStore, from snap: AddonSnapshot) {
        self.store = store
        let folders = snap.folders.map { f -> NoteFolder in
            var n = NoteFolder(name: f.name)
            n.id = UUID(uuidString: f.id)!
            n.parentId = f.parent.flatMap(UUID.init(uuidString:))
            n.words = f.words
            return n
        }
        let notes = snap.ideas.map { i -> Note in
            var n = Note(id: UUID(uuidString: i.id)!, title: i.title, body: i.text,
                         folderId: i.folder.flatMap(UUID.init(uuidString:)), kind: NoteKind(rawValue: i.kind) ?? .idea)
            n.tags = i.tags
            n.links = i.links.compactMap(UUID.init(uuidString:))
            n.createdAt = AddonStoreMap.date(i.createdAt)
            n.updatedAt = AddonStoreMap.date(i.updatedAt)
            n.boardX = i.x
            n.boardY = i.y
            return n
        }
        _ = store.restore(notes: notes, folders: folders)
    }

    func snapshot() -> AddonSnapshot { AddonStoreMap.snapshot(notes: store.notes, folders: store.folders) }

    func retitle(_ id: String, _ title: String) {
        guard let u = UUID(uuidString: id), var n = store.note(u) else { return }
        n.title = title
        store.update(n)
    }

    func remove(_ id: String) {
        if let u = UUID(uuidString: id) { _ = store.remove(u) }
    }

    func apply(_ ops: [AddonOp], now: Int64) -> AddonApplied {
        store.applyAddonBatch(ops, now: AddonStoreMap.date(now)).applied
    }

    func idea(_ id: String) -> AddonJSON {
        let snap = snapshot()
        let view = AddonView(snap, grants: ["ideas.read"])
        return snap.ideas.first { $0.id == id }.map(view.json) ?? .null
    }
}

/// A main-thread heartbeat (S07b A3): a timer every 4 ms on the main run
/// loop, keeping the largest gap between beats. The test's main thread
/// spins the run loop while the add-on works on its own thread.
@MainActor
final class AddonHeartbeat {
    private var last = DispatchTime.now().uptimeNanoseconds
    private(set) var largestGapMs = 0.0
    private var timer: Timer?

    func start() {
        last = DispatchTime.now().uptimeNanoseconds
        largestGapMs = 0
        timer = Timer.scheduledTimer(withTimeInterval: 0.004, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.beat() }
        }
    }

    private func beat() {
        let now = DispatchTime.now().uptimeNanoseconds
        largestGapMs = max(largestGapMs, Double(now - last) / 1_000_000)
        last = now
    }

    func stop() {
        beat()
        timer?.invalidate()
        timer = nil
    }

    /// Spins the main run loop until `done` or `seconds` pass.
    static func spin(until done: () -> Bool, seconds: Double) {
        let end = Date().addingTimeInterval(seconds)
        while !done() && Date() < end { RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.002)) }
    }

    /// The largest gap with no add-on running, over `seconds`.
    static func baseline(seconds: Double = 0.5) -> Double {
        let h = AddonHeartbeat()
        h.start()
        spin(until: { false }, seconds: seconds)
        h.stop()
        return h.largestGapMs
    }
}

/// The test-only zip writer: stored entries, names in UTF-8 byte order, DOS
/// time 0 and date 1980-01-01, Unix mode 0644. The same bytes as the Kotlin
/// tests' writer and as the generator of addons/fixtures/index/index.json.
enum AddonZipWriter {
    struct Entry {
        var name: String
        var data: Data
        var method: UInt16 = 0
        var flags: UInt16 = 0
        var mode: UInt32 = 0o100644
        var size: UInt32? = nil
    }

    static func folder(_ url: URL) -> Data {
        let files = AddonFiles.folder(url)
        return pack(zip(files.names, files.data).map { Entry(name: $0.0, data: $0.1) })
    }

    static func pack(_ entries: [Entry], sorted: Bool = true) -> Data {
        var local = Data(), central = Data()
        func u16(_ v: UInt16, _ d: inout Data) { d.append(UInt8(v & 0xFF)); d.append(UInt8(v >> 8)) }
        func u32(_ v: UInt32, _ d: inout Data) { u16(UInt16(v & 0xFFFF), &d); u16(UInt16(v >> 16), &d) }
        let list = sorted ? entries.sorted { Array($0.name.utf8).lexicographicallyPrecedes(Array($1.name.utf8)) } : entries
        for e in list {
            let name = Data(e.name.utf8)
            let crc = AddonCRC32.of(e.data)
            let off = UInt32(local.count)
            let size = e.size ?? UInt32(e.data.count)
            u32(0x0403_4b50, &local); u16(20, &local); u16(e.flags, &local); u16(e.method, &local)
            u16(0, &local); u16(0x21, &local); u32(crc, &local); u32(UInt32(e.data.count), &local)
            u32(size, &local); u16(UInt16(name.count), &local); u16(0, &local)
            local.append(name); local.append(e.data)
            u32(0x0201_4b50, &central); u16(0x0314, &central); u16(20, &central); u16(e.flags, &central)
            u16(e.method, &central); u16(0, &central); u16(0x21, &central); u32(crc, &central)
            u32(UInt32(e.data.count), &central); u32(size, &central); u16(UInt16(name.count), &central)
            u16(0, &central); u16(0, &central); u16(0, &central); u16(0, &central); u32(e.mode << 16, &central)
            u32(off, &central); central.append(name)
        }
        var end = Data()
        u32(0x0605_4b50, &end); u16(0, &end); u16(0, &end); u16(UInt16(list.count), &end); u16(UInt16(list.count), &end)
        u32(UInt32(central.count), &end); u32(UInt32(local.count), &end); u16(0, &end)
        return local + central + end
    }
}
