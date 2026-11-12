import XCTest
@testable import Sorta

/// A Neurons cell grows no process of its own: a process exists only where
/// it attaches to something (the owner, 29 Oct), so a cell is its soma and
/// its links, whose tubes leave it through mouths in its membrane
/// (GraphLinkBridge; 6ff9e10). The arbor lives in a SceneKit file, which
/// the Linux suites can't build, so it is checked here, in the app.
/// Android's NeuronLookTest asks the same.
@MainActor
final class NeuronArborTests: XCTestCase {
    func testNoCellGrowsAProcessOfItsOwn() {
        for kind in NeuronCellKind.allCases {
            for variant in 0..<3 {
                for rich in [false, true] {
                    let grown = NeuronArbor.branches(kind, variant: variant, rich: rich)
                    XCTAssertTrue(grown.isEmpty,
                                  "\(kind), variant \(variant)\(rich ? ", rich" : ""): \(grown.count) branches of its own")
                }
            }
        }
    }
}
