import Foundation
#if canImport(os)
import os
#endif

// MARK: - Add-ons (S07b): one call, from the main thread and back
//
// The main thread takes an O(1) copy of the store's arrays and hands it to
// the add-on's own thread, which maps the snapshot, runs the call and never
// touches the store. When the call returns there is exactly one hop back to
// the main thread, which applies the whole batch through NoteStore as one
// change and one Undo step (NoteStoreReshape.swift). The main thread never
// waits on an add-on. No UI here.

@MainActor
final class AddonCenter {
    struct Finished {
        var outcome: AddonOutcome
        /// Set when the batch was applied; nil for a refusal or a failed call.
        var applied: AddonApplied?
        /// The Undo step, when the batch changed something.
        var reshape: NoteStore.Reshape?
        var ranMs: Double
        var applyNanos: UInt64
        /// Whether the main thread took the batch as the add-on's thread had
        /// worked it out (the store had not changed meanwhile).
        var prepared = false
        var code: String? { outcome.code }
    }

    let store: NoteStore
    let registry: AddonRegistry
    let pool: AddonPool
    let network: AddonNetwork
    let netOn: Bool
    /// "Ask each time": shows the request to the person and answers on the
    /// main thread. The add-on's thread waits for it; nothing else does.
    var ask: (_ addon: String, _ host: String, _ answer: @escaping (Bool) -> Void) -> Void = { _, _, answer in answer(false) }

    /// SORTA_ADDON_NET, through the Info.plist key SortaAddonNet.
    nonisolated static var netFromBundle: Bool {
        ((Bundle.main.object(forInfoDictionaryKey: "SortaAddonNet") as? String) ?? "YES").uppercased() != "NO"
    }

    init(store: NoteStore, registry: AddonRegistry, pool: AddonPool = AddonPool(),
         network: AddonNetwork = AddonSessionNetwork(), netOn: Bool = AddonCenter.netFromBundle) {
        self.store = store
        self.registry = registry
        self.pool = pool
        self.network = network
        self.netOn = netOn
        // off, a grant taken away, an update, a removal, a revocation: the
        // add-on's runtime goes, and the next call starts a fresh one
        registry.onStop = { [pool] id in pool.close(id) }
    }

    /// Runs one call of an installed add-on: a command (`input`), a card or a
    /// batch of cards (`cards`, idea ids: the add-on gets only those inside
    /// its read grant, and none means no call), or a settings change
    /// (`input`). `done` runs on the main thread once the batch is applied.
    func run(_ id: String, kind: String, command: String = "", input: String = "", cards: [String] = [],
             now: Date = Date(), done: @escaping (Finished) -> Void) {
        func refuse(_ code: String) {
            let env = AddonRunner.failure(code)
            done(Finished(outcome: AddonOutcome(envelope: env, ok: false, code: code, batch: [], log: [], activity: []),
                          applied: nil, reshape: nil, ranMs: 0, applyNanos: 0))
        }
        guard registry.canRun(id), let installed = registry.addon(id) else { return refuse("addon.off") }
        guard pool.withinBudget(id) else { return refuse("limit.minute") }
        // the package is read and checked on the add-on's own thread, once for
        // each runner (a call whose package is gone ends "addon.missing")
        let registry = self.registry
        let sha = installed.sha256
        guard let runner = pool.runner(id, stamp: sha, source: { registry.package(id, sha256: sha)?.files }) else {
            return refuse("engine.busy")
        }

        // O(1): the arrays are copied on write, and the store never writes
        // them from another thread.
        let notes = store.notes
        let folders = store.folders
        let grants = installed.grants
        let settings = installed.settings
        let network = self.network
        let netOn = self.netOn
        let pool = self.pool
        let askPerson = installed.askEachTime ? self.ask : nil
        let nowMs = AddonStoreMap.ms(now)

        let isCard = kind == "card" || kind == "cards"
        runner.call(kind: kind, id: command,
                    input: { call in isCard ? call.cardInput(cards, batch: kind == "cards") : input },
                    setupWith: { files in
                        // the storage's first read of its file is here too, off the main thread
                        AddonCenter.setup(notes: notes, folders: folders, grants: grants, commands: files.commands,
                                          netOn: netOn, settings: settings, storage: registry.storage(id), network: network,
                                          ask: askPerson.map { a in { host in AddonCenter.wait(for: a, id, host) } },
                                          now: nowMs)
                    },
                    before: { registry.beginCall(id) },
                    done: { run in
                        registry.endCall()
                        pool.record(id, ms: run.ranMs)
                        registry.strike(id, code: run.outcome.code)
                        registry.log(id, run.outcome.activity)
                        // the batch is worked out here, on the add-on's thread, against the arrays the call was given
                        var prepared: AddonApply.Prepared?
                        if run.outcome.ok, !run.outcome.batch.isEmpty {
                            prepared = AddonApply.prepare(run.outcome.batch, notes: notes, folders: folders, now: now)
                        }
                        let ready = prepared
                        DispatchQueue.main.async {
                            MainActor.assumeIsolated { self.finish(id, run, ready, now: now, done: done) }
                        }
                    })
    }

    /// The one hop back: a stopped, refused or failed call applies nothing,
    /// and nor does a call whose add-on was turned off while it ran.
    private func finish(_ id: String, _ run: AddonRun, _ prepared: AddonApply.Prepared?, now: Date, done: (Finished) -> Void) {
        guard run.outcome.ok, registry.canRun(id) else {
            var o = run.outcome
            if o.ok { o = AddonOutcome(envelope: AddonRunner.failure("stopped"), ok: false, code: "stopped", batch: [],
                                       log: o.log, activity: o.activity) }
            return done(Finished(outcome: o, applied: nil, reshape: nil, ranMs: run.ranMs, applyNanos: 0))
        }
        let fits = prepared?.fits(store.notes, store.folders, now: now) ?? false
        let r = store.applyAddonBatch(run.outcome.batch, now: now, prepared: prepared)
        logApply(id, r.applyNanos, writes: run.outcome.batch.count, prepared: fits)
        done(Finished(outcome: run.outcome, applied: r.applied, reshape: r.reshape, ranMs: run.ranMs, applyNanos: r.applyNanos,
                      prepared: fits))
    }

    /// Each apply's time on the main thread, to the log (category "addons")
    /// and kept for the last 50.
    private(set) var applyTimes: [UInt64] = []

    private func logApply(_ id: String, _ nanos: UInt64, writes: Int, prepared: Bool) {
        applyTimes.append(nanos)
        if applyTimes.count > 50 { applyTimes.removeFirst() }
        #if canImport(os)
        let ms = Double(nanos) / 1e6
        let how = prepared ? "worked out on the add-on's thread" : "worked out on the main thread"
        AddonCenter.log.info("apply \(id, privacy: .public): \(ms, format: .fixed(precision: 3)) ms on the main thread, \(writes) writes, \(how, privacy: .public)")
        #endif
    }

    #if canImport(os)
    private static let log = Logger(subsystem: "com.cramdown.app", category: "addons")
    #endif

    /// Worked out on the add-on's thread.
    nonisolated static func setup(notes: [Note], folders: [NoteFolder], grants: [String], commands: [String], netOn: Bool,
                                  settings: String, storage: AddonStorage, network: AddonNetwork,
                                  ask: ((String) -> Bool)?, now: Int64) -> AddonCall.Setup {
        var s = AddonCall.Setup(snapshot: AddonStoreMap.snapshot(notes: notes, folders: folders), grants: grants,
                                commands: commands, netOn: netOn, settings: settings, storage: storage,
                                network: netOn ? network : nil, now: now)
        s.consent = ask
        s.place = { n, others in AddonStoreMap.place(n, others) }
        return s
    }

    /// The add-on's thread holds the request until the person answers.
    nonisolated static func wait(for ask: @escaping (String, String, @escaping (Bool) -> Void) -> Void,
                                 _ id: String, _ host: String) -> Bool {
        let answered = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var yes = false
        DispatchQueue.main.async {
            ask(id, host) { answer in
                lock.lock()
                yes = answer
                lock.unlock()
                answered.signal()
            }
        }
        // The call's own 10 s wall limit: no answer by then is a no.
        if answered.wait(timeout: .now() + .milliseconds(AddonCall.netMs)) == .timedOut { return false }
        lock.lock()
        defer { lock.unlock() }
        return yes
    }
}
