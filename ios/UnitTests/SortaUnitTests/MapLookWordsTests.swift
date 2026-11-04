import XCTest
@testable import Sorta

/// Map mode's looks as the chip names them (S05b), as Android's
/// MapLookWordsTest checks them; their order and storage are the Linux
/// suite maplook's.
final class MapLookWordsTests: XCTestCase {
    func testTheLooksAreNamed() {
        XCTAssertEqual(MapLook.allCases.map(\.title), ["Space", "Neuron", "2D", "Performance"])
    }

    func testTheChipIsSpokenAsTheTheme() {
        XCTAssertEqual(IdeasWords.lookChipSpoken(MapLook.twoD.title), "Theme, 2D")
        XCTAssertEqual(IdeasWords.lookChipHint, "Changes how the map looks.")
        XCTAssertEqual(MapLook.chipIdentifier, "map-look")
    }
}
