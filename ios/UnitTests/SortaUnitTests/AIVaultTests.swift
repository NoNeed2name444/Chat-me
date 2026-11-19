import XCTest
import Security
@testable import Sorta

/// S08 A3 on the simulator: the Keychain vault (save, read, delete and
/// Disconnect), the wipe after a reinstall, and a planted fake credential
/// that shows up nowhere but the Keychain: not in the store file, a capsule,
/// UserDefaults, the output captured during the test, or the ledger.
/// The ad-hoc signed build (ci/public/ios.yml) must give no -34018.
final class AIVaultTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suite = ""

    override func setUpWithError() throws {
        suite = "ai-vault-tests-\(UUID().uuidString)"
        defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrService as String: AIKeychainVault.service] as CFDictionary)
    }

    override func tearDown() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrService as String: AIKeychainVault.service] as CFDictionary)
        defaults.removePersistentDomain(forName: suite)
    }

    private func planted(_ account: String) -> OSStatus {
        let item: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrService as String: AIKeychainVault.service,
                                   kSecAttrAccount as String: account,
                                   kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                                   kSecValueData as String: Data("fake-key-for-tests-9".utf8)]
        return SecItemAdd(item as CFDictionary, nil)
    }

    private func present(_ account: String) -> OSStatus {
        SecItemCopyMatching([kSecClass as String: kSecClassGenericPassword,
                             kSecAttrService as String: AIKeychainVault.service,
                             kSecAttrAccount as String: account] as CFDictionary, nil)
    }

    func testKeychainSavesReadsDeletesAndDisconnects() throws {
        let status = planted("probe")
        XCTAssertNotEqual(status, -34018, "the simulator build must be signed (ad hoc) for the Keychain")
        XCTAssertEqual(status, errSecSuccess)
        let vault = AIKeychainVault(defaults: defaults)
        XCTAssertNil(try vault.read(.openrouter))
        try vault.save(AIFiles.fakeKey, for: .openrouter)
        try vault.save("fake-key-for-tests-2", for: .anthropic)
        XCTAssertEqual(try vault.read(.openrouter), AIFiles.fakeKey)
        try vault.save("fake-key-for-tests-3", for: .openrouter)
        XCTAssertEqual(try vault.read(.openrouter), "fake-key-for-tests-3", "saving again replaces")

        var attributes: CFTypeRef?
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: AIKeychainVault.service,
                                    kSecAttrAccount as String: "openrouter",
                                    kSecReturnAttributes as String: true]
        XCTAssertEqual(SecItemCopyMatching(query as CFDictionary, &attributes), errSecSuccess)
        let found = try XCTUnwrap(attributes as? [String: Any])
        XCTAssertEqual(found[kSecAttrAccessible as String] as? String, kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String)
        XCTAssertNotEqual(found[kSecAttrSynchronizable as String] as? Bool, true, "never in iCloud Keychain")

        let connect = AIConnect(vault: vault, transport: AIStandInTransport())
        XCTAssertThrowsError(try connect.disconnect(.openrouter), "never on the main thread") { XCTAssertEqual($0 as? AIError, .mainThread) }
        XCTAssertEqual(try vault.read(.openrouter), "fake-key-for-tests-3", "and refused, it changes nothing")
        try aiOffMain { try connect.disconnect(.openrouter) }
        XCTAssertNil(try vault.read(.openrouter), "Disconnect deletes it at once")
        XCTAssertEqual(present("openrouter"), errSecItemNotFound)
        XCTAssertEqual(try vault.read(.anthropic), "fake-key-for-tests-2", "the other account stays")
        try vault.delete(.anthropic)
        try vault.delete(.anthropic)
        XCTAssertNil(try vault.read(.anthropic))
    }

    func testANewInstallWipesWhatAnEarlierOneLeft() throws {
        XCTAssertEqual(planted("openrouter"), errSecSuccess)
        XCTAssertEqual(planted("openai"), errSecSuccess)
        XCTAssertFalse(defaults.bool(forKey: AIKeychainVault.marker), "a new install has no marker")
        let vault = AIKeychainVault(defaults: defaults)
        XCTAssertNil(try vault.read(.openrouter), "a credential left from an earlier install is never used")
        XCTAssertEqual(present("openai"), errSecItemNotFound, "every item under the service went")
        XCTAssertTrue(defaults.bool(forKey: AIKeychainVault.marker))

        try vault.save(AIFiles.fakeKey, for: .openrouter)
        let again = AIKeychainVault(defaults: defaults)
        XCTAssertEqual(try again.read(.openrouter), AIFiles.fakeKey, "the same install keeps what it saved")
    }

    /// A first access on a worker writes the install marker, and that write
    /// can wait for the main thread. The vault's lock is let go before it, so
    /// the main thread, parked and then reading, is never held.
    func testAFirstAccessOnAWorkerNeverHoldsTheMainThread() throws {
        defaults.removeObject(forKey: AIKeychainVault.marker)
        let vault = AIKeychainVault(defaults: defaults)
        let box = AIBox<Void>()
        let done = DispatchSemaphore(value: 0)
        Thread {
            box.result = Result { try vault.save(AIFiles.fakeKey, for: .openrouter) }
            done.signal()
        }.start()
        Thread.sleep(forTimeInterval: 0.3)          // the main thread parked while the worker starts
        let started = Date()
        _ = try vault.read(.anthropic)              // must not wait for the worker's marker write
        XCTAssertLessThan(Date().timeIntervalSince(started), 5)
        let deadline = Date().addingTimeInterval(20)
        while done.wait(timeout: .now()) == .timedOut, Date() < deadline {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.01))
        }
        try XCTUnwrap(box.result, "the worker's save finished").get()
        XCTAssertEqual(try vault.read(.openrouter), AIFiles.fakeKey)
        XCTAssertTrue(defaults.bool(forKey: AIKeychainVault.marker))
        try aiOffMain { try vault.delete(.openrouter) }
    }

    @MainActor
    func testAPlantedCredentialIsNowhereButTheKeychain() throws {
        let folder = try MadeUpBackup.folder()
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let storeFile = folder.appendingPathComponent("vignette-notes.json")
        let ledgerFile = folder.appendingPathComponent("ai-ledger.json")
        let store = NoteStore(fileURL: storeFile)
        let garden = store.createFolder(name: "Garden")
        let fixture = try AIFiles.fixture("model/split.json")
        let capture = try XCTUnwrap(fixture["capture"]?.string)
        let made = store.create(title: "Weekend", body: capture)
        store.create(title: "Fence posts", body: "Runner beans along the south fence.", folderId: garden.id)

        let vault = AIKeychainVault(defaults: defaults)
        let settings = AIDefaultsKeyValue(defaults)
        let prices = try AIPrices(json: Data(contentsOf: AIFiles.ai.appendingPathComponent("tables/prices.json")))
        let keyCheck = try AIFiles.fixture("http/openrouter-key.json")
        let good = try XCTUnwrap(fixture["cases"]?.array?.first?["answer"]?.string)
        let net = AIStandInTransport { request in
            if request.url.hasSuffix("/key") { return AIStandInTransport.response(keyCheck) }
            return aiChat(good)
        }
        let runner = AIRunner(vault: vault, store: settings, prices: prices, ledger: AILedger(file: ledgerFile),
                              transport: net, onDevice: AIAppleOnDevice(), root: AIFiles.ai)
        let folders = [AISplit.Folder(id: garden.id.uuidString, name: "Garden", words: ["plants", "beans", "fence"])]

        var outcome: AIOutcome<[AISplit.Piece]>?
        let output = try aiCaptured {
            try aiOffMain {
                try AIConnect(vault: vault, transport: net).connect(.openrouter, key: AIFiles.fakeKey)
                AISettings(store: settings).provider = .openrouter
                AISettings(store: settings).setModel("deepseek/deepseek-v4.1-flash", for: .openrouter)
                AIConsent(store: settings).setOn(.split, true)
                AIConsent(store: settings).agree(.split, .openrouter)
                outcome = runner.split(capture, folders: folders)
            }
            print("capture-marker")
        }
        guard case let .answered(.ok, pieces?) = outcome else { return XCTFail("split: \(String(describing: outcome))") }
        let kept = try XCTUnwrap(store.keepSplit(pieces.map { ($0.title, $0.text, $0.folder.flatMap(UUID.init(uuidString:))) },
                                                 replacing: made.id))
        XCTAssertEqual(kept.added.count, 3)
        store.waitForSaves()

        XCTAssertTrue(output.contains("capture-marker"), "the capture works")
        XCTAssertEqual(try vault.read(.openrouter), AIFiles.fakeKey, "it is in the Keychain")
        let key = Data(AIFiles.fakeKey.utf8)
        XCTAssertFalse(output.contains(AIFiles.fakeKey), "not in anything printed")
        XCTAssertNil(try Data(contentsOf: storeFile).range(of: key), "not in the store file")
        let capsule = try SortaCapsule.data(notes: store.notes, folders: store.folders, savedAt: Date())
        XCTAssertNil(capsule.range(of: key), "not in a capsule's data")
        let page = try SortaCapsule.html(notes: store.notes, folders: store.folders, edges: store.allEdges(),
                                         savedAt: Date(), zone: .current, picture: nil)
        XCTAssertFalse(page.contains(AIFiles.fakeKey), "not in a capsule's page")
        for d in [defaults!, UserDefaults.standard] {
            let all = d.dictionaryRepresentation().map { "\($0.key)=\($0.value)" }.joined(separator: "\n")
            XCTAssertFalse(all.contains(AIFiles.fakeKey), "not in UserDefaults")
        }
        let ledger = try Data(contentsOf: ledgerFile)
        XCTAssertFalse(ledger.isEmpty, "the send was recorded")
        XCTAssertNil(ledger.range(of: key), "not in the ledger")
        XCTAssertTrue(net.requests.allSatisfy { !$0.url.contains(AIFiles.fakeKey) && $0.body.map { $0.range(of: key) == nil } ?? true },
                      "not in a request's address or body")

        // on the main thread every operation and the provider are refused, and nothing is sent
        let sentBefore = net.requests.count
        if case .failed(.mainThread) = runner.split(capture, folders: folders) {} else { XCTFail("split on the main thread") }
        if case .failed(.mainThread) = runner.ask("What fits?", found: []) {} else { XCTFail("ask on the main thread") }
        if case .failed(.mainThread) = runner.why(AIIdea(id: "n", title: "Beans", text: "Beans."), candidates: []) {} else { XCTFail("why on the main thread") }
        if case .failed(.mainThread) = runner.sumUp(folder: "Garden", ideas: []) {} else { XCTFail("sum up on the main thread") }
        XCTAssertThrowsError(try runner.provider()) { XCTAssertEqual($0 as? AIError, .mainThread) }
        XCTAssertEqual(net.requests.count, sentBefore)

        try aiOffMain { try AIConnect(vault: vault, transport: net).disconnect(.openrouter) }
        XCTAssertNil(try vault.read(.openrouter))
    }
}
