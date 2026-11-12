import XCTest
@testable import Sorta

/// Sorta's Sort / Map switch (S05b, task 1), as Android's SortaModeTest
/// checks it: two modes in this order, with the shared identifiers, the
/// glyphs list.bullet and cube.transparent, and their words.
final class SortaModeTests: XCTestCase {
    func testTheModesAreSortThenMap() {
        XCTAssertEqual(SortaMode.allCases, [.sort, .map])
        XCTAssertEqual(SortaMode.allCases.map(\.rawValue), ["sort", "map"])
    }

    func testTheIdentifiersAreShared() {
        XCTAssertEqual(SortaMode.allCases.map(\.identifier), ["mode-sort", "mode-map"])
        XCTAssertEqual(SortaMode.toggleIdentifier, "modeSwitcherToggle")
        XCTAssertEqual(SortaMode.collapsedKey, "vignette.ideas.switcherCollapsed")
    }

    func testTheGlyphsAreTheMapsOwn() {
        XCTAssertEqual(SortaMode.allCases.map(\.symbol), [IdeasMode.list.symbol, IdeasMode.space.symbol])
        XCTAssertEqual(SortaMode.allCases.map(\.symbol), ["list.bullet", "cube.transparent"])
    }

    func testTheWordsAndWhatIsSpoken() {
        XCTAssertEqual(SortaMode.allCases.map(\.title), ["Sort", "Map"])
        XCTAssertEqual(SortaMode.allCases.map(\.spoken), ["Sort ideas", "Map of ideas"])
        XCTAssertEqual(SortaMode.items.map(\.spoken), ["Sort ideas", "Map of ideas"])
    }
}
