import Foundation
#if canImport(Security)
import Security
#endif

// MARK: - The vault (S08 A3): credentials live in the Keychain only
//
// A generic password: service com.cramdown.sorta.ai, account = provider,
// this device only and never synchronised, so it is never in iCloud
// Keychain and never restored to another device. Keychain items outlive the
// app, so before the first read in each install the vault deletes every item
// under its service; it knows an install is new because its UserDefaults
// marker (which holds no credential) is missing.

protocol AIVault: AnyObject {
    func save(_ secret: String, for provider: AIProviderID) throws
    func read(_ provider: AIProviderID) throws -> String?
    func delete(_ provider: AIProviderID) throws
}

/// The stand-in for tests and the Linux suites.
final class AIMemoryVault: AIVault {
    private var items: [AIProviderID: String] = [:]
    private let lock = NSLock()

    init() {}

    func save(_ secret: String, for provider: AIProviderID) throws {
        lock.lock(); defer { lock.unlock() }
        items[provider] = secret
    }

    func read(_ provider: AIProviderID) throws -> String? {
        lock.lock(); defer { lock.unlock() }
        return items[provider]
    }

    func delete(_ provider: AIProviderID) throws {
        lock.lock(); defer { lock.unlock() }
        items[provider] = nil
    }
}

#if canImport(Security)
final class AIKeychainVault: AIVault {
    static let service = "com.cramdown.sorta.ai"
    /// Set once the vault has wiped what an earlier install left.
    static let marker = "ai.vault.install"

    private let defaults: UserDefaults
    private let lock = NSLock()
    private var checked = false

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private func base(_ provider: AIProviderID?) -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: Self.service,
                                kSecAttrSynchronizable as String: kCFBooleanFalse as Any]
        if let provider { q[kSecAttrAccount as String] = provider.rawValue }
        return q
    }

    /// The first access in an install deletes every item under the service.
    private func wipeIfNewInstall() {
        guard !checked else { return }
        checked = true
        guard !defaults.bool(forKey: Self.marker) else { return }
        SecItemDelete(base(nil) as CFDictionary)
        defaults.set(true, forKey: Self.marker)
    }

    func save(_ secret: String, for provider: AIProviderID) throws {
        lock.lock(); defer { lock.unlock() }
        wipeIfNewInstall()
        SecItemDelete(base(provider) as CFDictionary)
        var item = base(provider)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        item[kSecValueData as String] = Data(secret.utf8)
        guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw AIError.vault }
    }

    func read(_ provider: AIProviderID) throws -> String? {
        lock.lock(); defer { lock.unlock() }
        wipeIfNewInstall()
        var q = base(provider)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var found: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &found)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = found as? Data else { throw AIError.vault }
        return String(data: data, encoding: .utf8)
    }

    func delete(_ provider: AIProviderID) throws {
        lock.lock(); defer { lock.unlock() }
        wipeIfNewInstall()
        let status = SecItemDelete(base(provider) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw AIError.vault }
    }
}
#endif
