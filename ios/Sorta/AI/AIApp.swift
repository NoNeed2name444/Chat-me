import Foundation
#if canImport(AuthenticationServices) && canImport(UIKit)
import AuthenticationServices
import UIKit
#endif

// MARK: - The AI core as the app builds it (S08)
//
// The Keychain, URLSession, Apple's own model and the bundled ai/ files
// (project.yml copies ai/prompts, ai/tables and the embedding table in as
// folders). S08u adds the screens that use it.

enum AIApp {
    /// Where prompts/, tables/ and potion-base-4M/ are in the app.
    static var root: URL { Bundle.main.resourceURL ?? Bundle.main.bundleURL }

    static func runner() throws -> AIRunner {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let prices = try AIPrices(json: Data(contentsOf: root.appendingPathComponent("tables/prices.json")))
        #if canImport(Security)
        let vault: AIVault = AIKeychainVault()
        #else
        let vault: AIVault = AIMemoryVault()
        #endif
        return AIRunner(vault: vault, store: AIDefaultsKeyValue(), prices: prices,
                        ledger: AILedger(file: support.appendingPathComponent("ai-ledger.json")),
                        transport: AIURLSessionTransport(), onDevice: AIAppleOnDevice(), root: root)
    }
}

#if canImport(AuthenticationServices) && canImport(UIKit)
/// The sign-in page in ASWebAuthenticationSession. The loopback listener
/// gets the code; the redirect back to sorta:// ends the session.
final class AIWebAuthBrowser: NSObject, AIBrowser, ASWebAuthenticationPresentationContextProviding {
    private let anchor: () -> ASPresentationAnchor
    private var session: ASWebAuthenticationSession?

    init(anchor: @escaping () -> ASPresentationAnchor) {
        self.anchor = anchor
    }

    func open(_ url: String, callbackScheme: String) {
        guard let page = URL(string: url) else { return }
        DispatchQueue.main.async {
            let session = ASWebAuthenticationSession(url: page, callback: .customScheme(callbackScheme)) { [weak self] _, _ in
                self?.session = nil
            }
            session.presentationContextProvider = self
            self.session = session
            session.start()
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        anchor()
    }
}
#endif
