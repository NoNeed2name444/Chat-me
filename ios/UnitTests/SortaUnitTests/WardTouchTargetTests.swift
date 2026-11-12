import SwiftUI
import UIKit
import XCTest
@testable import Sorta

/// Every control the look kit makes is at least 44 by 44 points, at the
/// default text size and at the largest (AX5), however little room it is
/// offered: measured with nothing offered (its smallest) and with a
/// phone's width offered.
@MainActor
final class WardTouchTargetTests: XCTestCase {
    static let least: CGFloat = 44
    static let sizes: [DynamicTypeSize] = [.large, .accessibility5]
    /// Nothing at all, then an iPhone's width with the height left open.
    static let offers: [CGSize] = [.zero, CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)]

    private func size<V: View>(of view: V, at type: DynamicTypeSize, offered: CGSize) -> CGSize {
        let host = UIHostingController(rootView: view.environment(\.dynamicTypeSize, type))
        return host.sizeThatFits(in: offered)
    }

    /// Asserts `view` is at least `width` by `height` at every text size and
    /// every offer.
    private func assertTarget<V: View>(_ name: String, width: CGFloat = least, height: CGFloat = least,
                                       _ view: () -> V, file: StaticString = #filePath, line: UInt = #line) {
        let made = view()
        for type in Self.sizes {
            for offer in Self.offers {
                let got = size(of: made, at: type, offered: offer)
                XCTAssertGreaterThanOrEqual(got.width, width, "\(name) at \(type), offered \(offer): \(got)",
                                            file: file, line: line)
                XCTAssertGreaterThanOrEqual(got.height, height, "\(name) at \(type), offered \(offer): \(got)",
                                            file: file, line: line)
            }
        }
    }

    func testButtonKinds() {
        assertTarget("primary button") { Button("OK") {}.buttonStyle(.wardPrimary) }
        assertTarget("secondary button") { Button("OK") {}.buttonStyle(.wardSecondary) }
        assertTarget("destructive button") { Button("OK") {}.buttonStyle(.wardDestructive) }
        assertTarget("compact button") { Button("OK") {}.buttonStyle(.wardCompact) }
        assertTarget("quiet button") { Button("OK") {}.buttonStyle(.wardQuiet) }
        // a glyph alone, the narrowest label a button can have
        assertTarget("compact glyph button") {
            Button {} label: { Image(systemName: "plus") }.buttonStyle(.wardCompact)
        }
    }

    func testChipCircleItemPanelAndTile() {
        for on in [false, true] {
            assertTarget("chip button, chosen \(on)") { Button("A") {}.buttonStyle(WardChipButtonStyle(on: on)) }
            assertTarget("circle button, chosen \(on)") {
                Button {} label: { Image(systemName: "plus") }.buttonStyle(WardCircleButtonStyle(on: on))
            }
            assertTarget("item button, chosen \(on)") {
                Button {} label: { Image(systemName: "plus") }
                    .buttonStyle(WardItemButtonStyle(chosen: on, shape: Capsule()))
            }
            assertTarget("panel button, chosen \(on)") {
                Button {} label: { Image(systemName: "plus") }
                    .buttonStyle(WardPanelButtonStyle(chosen: on, shape: Capsule()))
            }
            assertTarget("tile, chosen \(on)") {
                Button {} label: { Image(systemName: "plus") }.buttonStyle(PopTileStyle(selected: on))
            }
            assertTarget("filter chip, chosen \(on)") { WardFilterChip(text: "A", selected: on) {} }
        }
    }

    func testToggleSegmentedAndFields() {
        for on in [false, true] {
            assertTarget("switch, on \(on)") {
                Toggle(isOn: .constant(on)) { EmptyView() }.toggleStyle(WardToggleStyle())
            }
        }
        // two segments, each at least 44 wide
        assertTarget("segmented control", width: 2 * Self.least) {
            WardSegmented(selection: .constant(0), options: [0, 1]) { option in
                Image(systemName: option == 0 ? "list.bullet" : "square.grid.2x2")
            }
        }
        assertTarget("field", width: 0) {
            TextField(text: .constant("")) { EmptyView() }.wardField()
        }
        assertTarget("pop field", width: 0) {
            TextField(text: .constant("")) { EmptyView() }.popField()
        }
    }

    /// The banner's action is its only control: the banner is the action's
    /// 44 points plus the well's padding above and below, at least.
    func testBannerAction() {
        assertTarget("banner with an action", height: Self.least + 2 * WardSpace.m) {
            WardBanner(text: "A", actionTitle: "B", action: {})
        }
    }
}
