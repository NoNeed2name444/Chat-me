import Foundation
#if canImport(Sorta)
@testable import Sorta
#endif

/// The conformance cases (addons/conformance, S07b A2) run through the real
/// engine and this platform's host, printed as the kit's harness prints them
/// (addons/kit/harness/harness.c), so the text can be compared byte for byte
/// with expected.txt. The store each case writes to is the platform's own.
protocol AddonCaseStore: AnyObject {
    /// What a call would read now: the whole store.
    func snapshot() -> AddonSnapshot
    func retitle(_ id: String, _ title: String)
    func remove(_ id: String)
    func apply(_ ops: [AddonOp], now: Int64) -> AddonApplied
    /// An idea as an add-on with ideas.read would get it.
    func idea(_ id: String) -> AddonJSON
}

struct AddonConformance {
    let dir: URL
    let cases: [AddonJSON]
    let store: AddonJSON
    let routes: AddonJSON
    let now: Int64
    let addon: AddonFiles
    /// The hostile fixtures' cases.json: each case is the folder of its name.
    let perFolder: Bool

    init(dir: URL) throws {
        self.dir = dir
        let doc = try AddonJSON.parse(data: Data(contentsOf: dir.appendingPathComponent("cases.json")))
        perFolder = doc["fixtures"] != nil
        cases = doc["cases"]?.array ?? doc["fixtures"]?.array ?? []
        store = try AddonJSON.parse(data: Data(contentsOf: dir.appendingPathComponent(doc["store"]?.string ?? "store.json")))
        routes = try doc["routes"]?.string.map { try AddonJSON.parse(data: Data(contentsOf: dir.appendingPathComponent($0))) }
            ?? .object([])
        now = AddonText.parseISO(doc["now"]?.string ?? "2026-11-01T09:00:00.000Z") ?? 0
        addon = AddonFiles.folder(dir.appendingPathComponent(doc["addon"]?.string ?? "addon"))
    }

    var count: Int { cases.count }

    /// The output of cases `range`, each block as the harness prints it.
    func run(_ range: Range<Int>, makeStore: (AddonSnapshot) -> AddonCaseStore) -> String {
        var out = ""
        for c in cases[range] { out += block(c, makeStore: makeStore) }
        return out
    }

    private static func hex(_ s: String) -> Data {
        var d = Data()
        var it = s.makeIterator()
        while let a = it.next(), let b = it.next() { d.append(UInt8(String([a, b]), radix: 16) ?? 0) }
        return d
    }

    func block(_ c: AddonJSON, makeStore: (AddonSnapshot) -> AddonCaseStore) -> String {
        blockRun(c, makeStore: makeStore).text
    }

    /// One case's block, and how its call went (nil if it never finished).
    func blockRun(_ c: AddonJSON, makeStore: (AddonSnapshot) -> AddonCaseStore) -> (text: String, run: AddonRun?) {
        let name = c["name"]?.string ?? "?"
        let caseNow = c["now"]?.string.flatMap(AddonText.parseISO) ?? now
        let base = AddonSnapshot.read(store)
        let live = makeStore(base)
        var counter = 0
        let lock = NSLock()
        var answers = c["ask"]?.strings
        var setup = AddonCall.Setup(snapshot: live.snapshot(), grants: c["grants"]?.strings ?? [],
                                    commands: c["commands"]?.strings ?? [], netOn: c["netOff"]?.bool != true,
                                    settings: (c["settings"] ?? .object([])).canonical, storage: AddonStorage(),
                                    network: AddonRoutesNetwork(routes: routes), now: caseNow)
        setup.makeId = {
            lock.lock()
            defer { lock.unlock() }
            counter += 1
            let n = String(counter)
            return "00000000-0000-4000-8000-" + String(repeating: "0", count: 12 - n.count) + n
        }
        if answers != nil {
            setup.consent = { _ in
                lock.lock()
                defer { lock.unlock() }
                guard let first = answers?.first else { return false }
                answers?.removeFirst()
                return first == "yes"
            }
        }
        for ch in c["changeAfterSnapshot"]?.array ?? [] {
            live.retitle(ch["id"]?.string ?? "", ch["title"]?.string ?? "")
        }
        for id in c["goneAfterSnapshot"]?.strings ?? [] { live.remove(id) }

        var files = perFolder ? AddonFiles.folder(dir.appendingPathComponent(name)) : addon
        if case let .object(pairs)? = c["files"] {
            for (file, spec) in pairs.sorted(by: { AddonText.less($0.0, $1.0) }) {
                if let text = spec["text"]?.string {
                    files = files.replacing(file, with: Data(text.utf8))
                } else if let hex = spec["hex"]?.string {
                    files = files.replacing(file, with: AddonConformance.hex(hex))
                } else if let src = spec["bytecodeOf"]?.string, let bytes = AddonEngineInfo.bytecode(of: src) {
                    files = files.replacing(file, with: bytes)
                }
            }
        }

        let kind = c["kind"]?.string ?? "command"
        let fixed: String
        switch kind {
        case "settings": fixed = setup.settings
        case "load": fixed = ""
        default: fixed = (c["input"] ?? .object([("ideas", .array([]))])).canonical
        }
        let cardIds = c["card"]?.string.map { [$0] } ?? c["cards"]?.strings ?? []
        // a card view's input comes from the add-on's own view, on its thread
        let input: (AddonCall) -> String? = { call in
            kind == "card" || kind == "cards" ? call.cardInput(cardIds, batch: kind == "cards") : fixed
        }
        let runner = AddonRunner(id: "conformance", files: files)
        let finished = DispatchSemaphore(value: 0)
        var result: AddonRun?
        runner.call(kind: kind, id: kind == "command" ? (c["command"]?.string ?? "case") : "", input: input,
                    setup: setup) { run in
            result = run
            finished.signal()
        }
        if let ms = c["stopAfterMs"]?.integer {
            // sa_stop only sets the runtime's flag: no engine code runs here
            DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(ms)) { runner.stop() }
        }
        // the waiting thread keeps its run loop turning (the main thread's heartbeat)
        while finished.wait(timeout: .now()) == .timedOut {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.002))
        }
        runner.close()
        guard let run = result else { return ("== \(name)\nno result\n", nil) }

        var lines = ["load " + (run.loadCode ?? "ok")]
        lines += run.outcome.log.map { "log " + $0 }
        if !(kind == "load" && run.loadCode == nil) {
            lines += run.outcome.activity.map(\.line)
            lines.append("result " + run.outcome.envelope)
            if run.outcome.ok {
                let applied = live.apply(run.outcome.batch, now: caseNow)
                lines.append("applied " + applied.json.canonical)
                for id in (applied.added + applied.changed).sorted(by: AddonText.less) {
                    lines.append("after " + live.idea(id).canonical)
                }
            } else {
                lines.append("applied none")
            }
        }
        return ("== \(name)\n" + lines.joined(separator: "\n") + "\n", run)
    }
}
