import UIKit
import UniformTypeIdentifiers

/// Sorta in the share sheet (S06b, plugins plan idea 10): the shared text
/// and its first web link, handed to the app as
/// `sorta://share?text=…`, where the share sheet (ShareSheet) keeps the
/// words chosen and saves them as a new idea in To sort. Nothing is sent
/// anywhere and nothing is kept here.
///
/// Its own target, SortaShare (ios/project.yml), with its own bundle id
/// and no signing change; on a device it needs one more App ID
/// (com.cramdown.sorta.share). The app's file is not shared with the
/// extension (no App Group), so the app does the saving.
@objc(ShareViewController)
final class ShareViewController: UIViewController {
    private var started = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task { @MainActor in
            let text: String = await sharedText()
            if let url = Self.link(text: text) { hand(url) }
            extensionContext?.completeRequest(returningItems: nil)
        }
    }

    /// The text and links shared, a space apart, in the order given.
    private func sharedText() async -> String {
        var parts: [String] = []
        let items: [NSExtensionItem] = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        for item in items {
            if let words = item.attributedContentText?.string, !words.isEmpty { parts.append(words) }
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                   let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                    parts.append(url.absoluteString)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                          let words = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier)
                              as? String {
                    parts.append(words)
                }
            }
        }
        var seen = Set<String>()
        return parts.filter { seen.insert($0).inserted }.joined(separator: " ")
    }

    /// The app's link for `text`; nil when there is nothing to keep.
    static func link(text: String) -> URL? {
        let trimmed: String = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        var parts = URLComponents()
        parts.scheme = "sorta"
        parts.host = "share"
        parts.queryItems = [URLQueryItem(name: "text", value: String(trimmed.prefix(4_000)))]
        return parts.url
    }

    /// Opens the app on `url`: through the application object up the
    /// responder chain, by its Objective-C method, since an extension can
    /// name no API that is unavailable to extensions.
    private func hand(_ url: URL) {
        let selector: Selector = NSSelectorFromString("openURL:options:completionHandler:")
        var responder: UIResponder? = self
        while let next = responder {
            if next.isKind(of: UIApplication.self), next.responds(to: selector) {
                typealias Open = @convention(c) (AnyObject, Selector, NSURL, NSDictionary, AnyObject?) -> Void
                let open: Open = unsafeBitCast(next.method(for: selector), to: Open.self)
                open(next, selector, url as NSURL, NSDictionary(), nil)
                return
            }
            responder = next.next
        }
    }
}
