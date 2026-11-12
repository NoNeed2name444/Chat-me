import SwiftUI
import UIKit
import XCTest
@testable import Sorta

/// The Ideas screens' own controls are at least 44 by 44 points, at the
/// default text size and at the largest (AX5), however little room they are
/// offered, as WardTouchTargetTests measures the kit's (S05, A7). The UI
/// tests measure them where they stand, and that no two overlap
/// (IdeasBehaviourTests.testTargetsAreLargeEnoughAndApart).
@MainActor
final class IdeasTouchTargetTests: XCTestCase {
    static let least: CGFloat = 44
    static let sizes: [DynamicTypeSize] = [.large, .accessibility5]
    /// Nothing at all, then an iPhone's width with the height left open.
    static let offers: [CGSize] = [.zero, CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)]

    private func size<V: View>(of view: V, at type: DynamicTypeSize, offered: CGSize) -> CGSize {
        let host = UIHostingController(rootView: view.environment(\.dynamicTypeSize, type))
        return host.sizeThatFits(in: offered)
    }

    private func assertTarget<V: View>(_ name: String, height: CGFloat = least, _ view: () -> V,
                                       file: StaticString = #filePath, line: UInt = #line) {
        let made = view()
        for type in Self.sizes {
            for offer in Self.offers {
                let got = size(of: made, at: type, offered: offer)
                XCTAssertGreaterThanOrEqual(got.width, Self.least, "\(name) at \(type), offered \(offer): \(got)",
                                            file: file, line: line)
                XCTAssertGreaterThanOrEqual(got.height, height, "\(name) at \(type), offered \(offer): \(got)",
                                            file: file, line: line)
            }
        }
    }

    func testSortItOnAndOff() {
        for enabled in [true, false] {
            assertTarget("Sort it, on \(enabled)") {
                Button(IdeasWords.sortIt) {}.buttonStyle(SortItButtonStyle()).disabled(!enabled)
            }
        }
    }

    func testTheCaptureWellIsAButtonTall() {
        assertTarget("the capture well", height: WardControlSize.button) { WellHost() }
    }

    func testTheRowAndEditorButtons() {
        assertTarget("Choose folder") { Button(IdeasWords.chooseFolder) {}.buttonStyle(.wardCompact) }
        assertTarget("the folder chip") {
            Button {} label: { Label(IdeasWords.toSort, systemImage: "folder") }
                .buttonStyle(WardChipButtonStyle())
        }
        assertTarget("More") {
            Button {} label: { Image(systemName: "ellipsis") }.buttonStyle(.wardCircle)
        }
        assertTarget("Done") { Button(IdeasWords.done) {}.buttonStyle(.wardCompact) }
        assertTarget("Link to an idea") { Button(IdeasWords.linkTo) {}.buttonStyle(.wardCompact) }
    }
}

/// The well with a field to type in, as the Sort screen holds it.
private struct WellHost: View {
    @State private var draft = ""
    @FocusState private var capturing: Bool

    var body: some View {
        CaptureWell(draft: $draft, capturing: $capturing, save: {}, sortIt: {})
    }
}
