import Foundation
import SortaAddonsC

// MARK: - Add-ons (S07b): the engine, on threads of its own
//
// Each add-on's runtime lives on one Thread that Sorta makes with a 4 MB
// stack (S07, section 6). Work reaches it through that thread's own lock and
// condition: no GCD queue, serial queue or task executor ever runs engine
// code, so a runtime never hops threads. The C host (addons/engine/host)
// checks it is on its own thread, not the main thread, at every entry.

/// The files of one add-on, by package path, and the command ids its manifest declares.
struct AddonFiles {
    var names: [String]
    var data: [Data]
    var main: String
    var commands: [String] = []

    static func commands(of manifest: AddonJSON) -> [String] {
        (manifest["contributes"]?["commands"]?.array ?? []).compactMap { $0["id"]?.string }
    }

    /// A package folder, two levels deep at most (the kit's and the tests').
    static func folder(_ url: URL) -> AddonFiles {
        var names: [String] = [], data: [Data] = []
        let fm = FileManager.default
        func walk(_ dir: URL, _ prefix: String, _ depth: Int) {
            let items = ((try? fm.contentsOfDirectory(atPath: dir.path)) ?? []).sorted()
            for item in items where !item.hasPrefix(".") {
                let full = dir.appendingPathComponent(item)
                var isDir: ObjCBool = false
                fm.fileExists(atPath: full.path, isDirectory: &isDir)
                if isDir.boolValue {
                    if depth < 2, item != "tests" { walk(full, prefix + item + "/", depth + 1) }
                } else if let d = try? Data(contentsOf: full) {
                    names.append(prefix + item)
                    data.append(d)
                }
            }
        }
        walk(url, "", 1)
        var main = "main.js"
        var commands: [String] = []
        if let i = names.firstIndex(of: "manifest.json"), let m = try? AddonJSON.parse(data: data[i]) {
            if let given = m["main"]?.string { main = given }
            commands = AddonFiles.commands(of: m)
        }
        return AddonFiles(names: names, data: data, main: main, commands: commands)
    }

    /// The same files, with one replaced or added.
    func replacing(_ name: String, with bytes: Data) -> AddonFiles {
        var copy = self
        if let i = names.firstIndex(of: name) { copy.data[i] = bytes } else { copy.names.append(name); copy.data.append(bytes) }
        return copy
    }
}

/// A thread with a 4 MB stack and a queue of its own.
final class AddonThread {
    static let stackSize = Int(SA_THREAD_STACK)

    private let condition = NSCondition()
    private var jobs: [() -> Void] = []
    private var finished = false

    init(name: String) {
        let thread = Thread { [self] in loop() }
        thread.stackSize = AddonThread.stackSize
        thread.name = name
        thread.start()
    }

    func run(_ job: @escaping () -> Void) {
        condition.lock()
        jobs.append(job)
        condition.signal()
        condition.unlock()
    }

    /// Ends the thread once the jobs queued so far have run.
    func finish() {
        condition.lock()
        finished = true
        condition.signal()
        condition.unlock()
    }

    private func loop() {
        while true {
            condition.lock()
            while jobs.isEmpty && !finished { condition.wait() }
            if jobs.isEmpty {
                condition.unlock()
                return
            }
            let job = jobs.removeFirst()
            condition.unlock()
            job()
        }
    }
}

/// The current call's handler, for the C host's callback.
private final class AddonHostBox {
    var call: AddonCall?
}

private let addonHostFunction: @convention(c) (UnsafeMutableRawPointer?, OpaquePointer?, UnsafePointer<CChar>?,
                                                UnsafePointer<CChar>?, Int) -> Void = { opaque, reply, name, args, length in
    guard let opaque, let name else { return }
    let box = Unmanaged<AddonHostBox>.fromOpaque(opaque).takeUnretainedValue()
    let text = args.map { String(decoding: UnsafeRawBufferPointer(start: $0, count: length), as: UTF8.self) } ?? "[]"
    let answer = box.call?.handle(String(cString: name), text) ?? "!api.unknown"
    if answer.hasPrefix("!") {
        sa_reply_error(reply, String(answer.dropFirst()))
    } else {
        sa_reply_ok(reply, answer, answer.utf8.count)
    }
}

/// Section 6's time limits, by kind of call: running time and wall time, in ms.
enum AddonLimits {
    static func of(_ kind: String) -> (run: Int32, wall: Int32) {
        switch kind {
        case "card": return (20, 20)
        case "cards": return (20, 250)
        case "settings": return (100, 100)
        default: return (500, 10_000)
        }
    }

    /// The codes that count as a strike: the call was stopped at a limit.
    static let strikes: Set<String> = ["limit.time", "limit.memory", "limit.stack", "limit.json", "limit.batch"]
}

/// How one call went: its outcome, its running time in ms, and the code
/// the add-on's load failed with, if it was loaded for this call and failed.
struct AddonRun {
    var outcome: AddonOutcome
    var ranMs: Double
    var loadCode: String?
    /// Wall time of loading the add-on for this call (0 if it was loaded), and of the call itself.
    var loadMs: Double = 0
    var callMs: Double = 0
}

/// One add-on's runtime and its thread. `source` gives the add-on's files: it
/// is called on the add-on's own thread, once, the first time they are
/// needed, so reading and checking an installed package never runs on the
/// main thread, and a runner keeps what it read for as long as it lives.
/// `stamp` names the version of the files it was made for (the package's
/// SHA-256), so the pool can tell a runner made before an update.
final class AddonRunner {
    let id: String
    let stamp: String
    private let source: () -> AddonFiles?
    private var files: AddonFiles?            // read on `thread` only
    private var read = false
    private let thread: AddonThread
    private let box = AddonHostBox()
    private let lock = NSLock()
    private var runtime: OpaquePointer?   // made, used and freed on `thread` only
    private var busy = false
    private var pending = 0
    private var lastUsed = Date()
    private var closed = false
    /// A stop that came while a call was queued or running: that call ends
    /// "stopped", and its runtime is freed after it.
    private var stopPending = false

    init(id: String, stamp: String, source: @escaping () -> AddonFiles?) {
        self.id = id
        self.stamp = stamp
        self.source = source
        thread = AddonThread(name: "Sorta add-on " + id)
    }

    convenience init(id: String, files: AddonFiles) {
        self.init(id: id, stamp: "", source: { files })
    }

    deinit { thread.finish() }

    var idleSince: Date? {
        lock.lock()
        defer { lock.unlock() }
        return busy ? nil : lastUsed
    }

    /// One call, on the add-on's thread: the runtime is made and the add-on
    /// loaded first if need be (kind "load" only loads). `before` and `done`
    /// run on that thread, around the engine work (the safe-start marker).
    /// `setup` is worked out on the add-on's thread (the snapshot is mapped there).
    func call(kind: String, id commandID: String = "", input: String, setup: @autoclosure @escaping () -> AddonCall.Setup,
              before: @escaping () -> Void = {}, done: @escaping (AddonRun) -> Void) {
        call(kind: kind, id: commandID, input: { _ in input }, setupWith: { _ in setup() }, before: before, done: done)
    }

    /// `input` is worked out on the add-on's thread from the call's view; nil
    /// means the call is not made and fails with "permission" (a card view
    /// with no idea it may read).
    func call(kind: String, id commandID: String = "", input makeInput: @escaping (AddonCall) -> String?,
              setup: @autoclosure @escaping () -> AddonCall.Setup,
              before: @escaping () -> Void = {}, done: @escaping (AddonRun) -> Void) {
        call(kind: kind, id: commandID, input: makeInput, setupWith: { _ in setup() }, before: before, done: done)
    }

    private static func ended(_ code: String) -> AddonRun {
        AddonRun(outcome: AddonOutcome(envelope: failure(code), ok: false, code: code, batch: [], log: [], activity: []),
                 ranMs: 0, loadCode: nil)
    }

    /// A call ended (under the lock): the runner is idle once none is queued
    /// behind it, and a stop was for the calls there were.
    private func endedLocked() {
        pending -= 1
        if pending == 0 {
            busy = false
            if !closed { stopPending = false }
        }
        lastUsed = Date()
    }

    /// As above, with `setupWith` given the add-on's files (the manifest's
    /// commands), which are read on the add-on's thread.
    func call(kind: String, id commandID: String = "", input makeInput: @escaping (AddonCall) -> String?,
              setupWith: @escaping (AddonFiles) -> AddonCall.Setup,
              before: @escaping () -> Void = {}, done: @escaping (AddonRun) -> Void) {
        lock.lock()
        // a runner closed between the pool handing it out and this call: its thread takes no more work
        let gone = closed
        if !gone {
            busy = true
            pending += 1
        }
        lock.unlock()
        if gone { return done(AddonRunner.ended("stopped")) }
        thread.run { [self] in
            before()
            // stopped while it waited its turn: it never starts
            lock.lock()
            let stoppedEarly = stopPending
            if stoppedEarly { endedLocked() }
            lock.unlock()
            if stoppedEarly { return done(AddonRunner.ended("stopped")) }
            if !read {
                files = source()
                read = true
            }
            guard let files else {
                lock.lock()
                endedLocked()
                lock.unlock()
                return done(AddonRunner.ended("addon.missing"))
            }
            let call = AddonCall(setupWith(files))
            box.call = call
            var envelope: String
            var ran = 0.0, callMs = 0.0
            let loadStart = DispatchTime.now().uptimeNanoseconds
            let loadCode = ensureLoaded(files)
            let loadMs = Double(DispatchTime.now().uptimeNanoseconds - loadStart) / 1e6
            if let loadCode {
                envelope = AddonRunner.failure(loadCode)
            } else if kind == "load" {
                envelope = ""
            } else if let rt = runtime, let input = makeInput(call) {
                call.phase = kind
                let limits = AddonLimits.of(kind)
                var length = 0
                let bytes = Array(input.utf8)
                let callStart = DispatchTime.now().uptimeNanoseconds
                let out = bytes.withUnsafeBufferPointer { buf in
                    buf.baseAddress!.withMemoryRebound(to: CChar.self, capacity: max(1, bytes.count)) { p in
                        sa_call(rt, kind, commandID, p, bytes.count, limits.run, limits.wall, &length)
                    }
                }
                callMs = Double(DispatchTime.now().uptimeNanoseconds - callStart) / 1e6
                if let out {
                    envelope = String(decoding: UnsafeRawBufferPointer(start: out, count: length), as: UTF8.self)
                    sa_string_free(out)
                } else {
                    envelope = AddonRunner.failure("limit.memory")
                }
                ran = sa_last_run_ms(rt)
                if sa_spent(rt) != 0 { release() }
            } else if runtime != nil {
                envelope = AddonRunner.failure("permission")
            } else {
                envelope = AddonRunner.failure("engine.spent")
            }
            box.call = nil
            lock.lock()
            let stopped = stopPending
            endedLocked()
            lock.unlock()
            // a runtime that was told to stop stays stopped, so it goes with the call it stopped
            if stopped { release() }
            var outcome = call.outcome(envelope)
            // a stopped call gives nothing to apply, even if the engine finished it before the stop reached it
            if stopped, kind != "load", outcome.ok { outcome = call.outcome(AddonRunner.failure("stopped")) }
            // what the call stored is kept only when it ended well
            call.setup.storage.end(ok: outcome.ok)
            done(AddonRun(outcome: outcome, ranMs: ran, loadCode: loadCode, loadMs: loadMs, callMs: callMs))
        }
    }

    /// Stops the call that is queued or running, from any thread. With no
    /// call there is nothing to stop, and nothing is done: a stop left on an
    /// idle runtime would refuse every later call.
    func stop() {
        lock.lock()
        if busy {
            stopPending = true
            if let rt = runtime { sa_stop(rt) }
        }
        lock.unlock()
    }

    /// Stops the calls queued or running (each ends "stopped"), frees the
    /// runtime and ends the thread.
    func close() {
        lock.lock()
        if closed {
            lock.unlock()
            return
        }
        closed = true
        if busy { stopPending = true }
        if let rt = runtime { sa_stop(rt) }
        lock.unlock()
        thread.run { [self] in release() }
        thread.finish()
    }

    /// Runs `work` on the add-on's thread and waits for it (the tests).
    func onThread<T>(_ work: @escaping () -> T) -> T {
        let done = NSCondition()
        var result: T?
        thread.run {
            let value = work()
            done.lock()
            result = value
            done.signal()
            done.unlock()
        }
        done.lock()
        while result == nil { done.wait() }
        done.unlock()
        return result!
    }

    static func failure(_ code: String) -> String { "{\"code\":\"\(code)\",\"message\":\"\(code)\",\"ok\":false}" }

    private func release() {
        lock.lock()
        let rt = runtime
        runtime = nil
        lock.unlock()
        if let rt { sa_free(rt) }
    }

    /// Makes the runtime and loads the add-on; the code if it can't.
    private func ensureLoaded(_ files: AddonFiles) -> String? {
        if runtime != nil { return nil }
        var code = [CChar](repeating: 0, count: 64)
        guard let rt = sa_new(addonHostFunction, Unmanaged.passUnretained(box).toOpaque(), &code, 64) else {
            return String(cString: code)
        }
        lock.lock()
        runtime = rt
        let stopNow = closed || stopPending
        lock.unlock()
        if stopNow { sa_stop(rt) }
        let names = files.names.map { strdup($0) }
        let texts: [UnsafeMutablePointer<CChar>?] = files.data.map { d in
            let p = UnsafeMutablePointer<CChar>.allocate(capacity: d.count + 1)
            d.withUnsafeBytes { raw in
                if let base = raw.baseAddress { memcpy(p, base, d.count) }
            }
            p[d.count] = 0
            return p
        }
        let lens = files.data.map(\.count)
        defer {
            names.forEach { free($0) }
            texts.forEach { $0?.deallocate() }
        }
        let constNames = names.map { UnsafePointer<CChar>($0) }
        let constTexts = texts.map { $0.map { UnsafePointer<CChar>($0) } }
        let r = constNames.withUnsafeBufferPointer { n in
            constTexts.withUnsafeBufferPointer { t in
                lens.withUnsafeBufferPointer { l in
                    sa_load(rt, Int32(files.names.count), n.baseAddress, t.baseAddress, l.baseAddress, files.main, &code, 64)
                }
            }
        }
        if r != 0 {
            release()
            return String(cString: code)
        }
        return nil
    }
}

// MARK: - the engine's facts, for the tests

enum AddonEngineInfo {
    static var version: String { String(cString: sa_engine_version()) }
    static var currentStackSize: Int { sa_current_stack_size() }
    static var isMainThread: Bool { sa_is_main_thread() != 0 }

    /// The bytes JS_WriteObject gives for a module (the bytecode case).
    static func bytecode(of source: String) -> Data? {
        var length = 0
        guard let p = sa_bytecode_of(source, source.utf8.count, &length) else { return nil }
        defer { free(p) }
        return Data(bytes: p, count: length)
    }

    /// A runtime made on the calling thread and freed at once: nil when the
    /// host made one, else its code (the main thread gets "thread").
    static func tryRuntimeHere() -> String? {
        var code = [CChar](repeating: 0, count: 64)
        guard let rt = sa_new(addonHostFunction, nil, &code, 64) else { return String(cString: code) }
        sa_free(rt)
        return nil
    }

    /// A runtime made on its own thread, then a call made from another
    /// thread: the host's own-thread check refuses it.
    static func callFromAnotherThread() -> String {
        var made: OpaquePointer?
        let step = DispatchSemaphore(value: 0)
        let freeIt = DispatchSemaphore(value: 0)
        let owner = Thread {
            var code = [CChar](repeating: 0, count: 64)
            made = sa_new(addonHostFunction, nil, &code, 64)
            step.signal()
            freeIt.wait()
            if let rt = made { sa_free(rt) }
            step.signal()
        }
        owner.stackSize = AddonThread.stackSize
        owner.start()
        step.wait()
        var answer = "none"
        if let rt = made {
            let caller = Thread {
                var length = 0
                if let out = sa_call(rt, "command", "x", "{}", 2, 500, 500, &length) {
                    answer = String(cString: out)
                    sa_string_free(out)
                }
                step.signal()
            }
            caller.stackSize = AddonThread.stackSize
            caller.start()
            step.wait()
        }
        freeIt.signal()
        step.wait()
        return answer
    }
}
