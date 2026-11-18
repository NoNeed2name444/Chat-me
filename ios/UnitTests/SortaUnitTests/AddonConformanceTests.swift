import XCTest
@testable import Sorta

/// S07b A2, A4, A5: the conformance cases through the real engine, the C
/// host and NoteStore, byte for byte with addons/conformance/expected.txt
/// (the kit's harness writes it on Linux; Android's emulator tests compare
/// with the same file). One test per group of cases, so each stays short.
@MainActor
final class AddonConformanceTests: XCTestCase {
    private static let group = 15

    private func check(group n: Int) throws {
        let dir = try AddonTestFiles.folder("conformance")
        let suite = try AddonConformance(dir: dir)
        let expected = try String(contentsOf: dir.appendingPathComponent("expected.txt"), encoding: .utf8)
        let blocks = AddonConformanceTests.blocks(expected)
        XCTAssertEqual(blocks.count, suite.count, "expected.txt has a block per case")
        XCTAssertGreaterThanOrEqual(suite.count, 40)
        let start = n * AddonConformanceTests.group
        let range = start..<min(suite.count, start + AddonConformanceTests.group)
        guard !range.isEmpty else { return XCTFail("no cases in group \(n)") }
        var stores: [NoteStore] = []
        let got = suite.run(range) { snap in
            let s = try! AddonTestFiles.store(self)
            stores.append(s)
            return AddonNoteStoreCases(s, from: snap)
        }
        let want = blocks[range].joined()
        if got != want {
            let g = got.components(separatedBy: "\n"), w = want.components(separatedBy: "\n")
            let i = zip(g, w).firstIndex { $0 != $1 } ?? min(g.count, w.count)
            XCTFail("first difference at line \(i):\n got: \(i < g.count ? g[i] : "<end>")\nwant: \(i < w.count ? w[i] : "<end>")")
        }
    }

    /// Each "== name" block, with its lines.
    static func blocks(_ text: String) -> [String] {
        var out: [String] = []
        let lines = text.components(separatedBy: "\n")
        for (i, line) in lines.enumerated() {
            if i == lines.count - 1, line.isEmpty { break }
            if line.hasPrefix("== ") { out.append("") }
            if !out.isEmpty { out[out.count - 1] += line + "\n" }
        }
        return out
    }

    func testCases01to15() throws { try check(group: 0) }
    func testCases16to30() throws { try check(group: 1) }
    func testCases31to45() throws { try check(group: 2) }
    func testCases46to60() throws { try check(group: 3) }
    func testCases61to75() throws { try check(group: 4) }
    func testCases76to90() throws { try check(group: 5) }

    func testEveryCaseIsInAGroup() throws {
        let suite = try AddonConformance(dir: try AddonTestFiles.folder("conformance"))
        XCTAssertLessThanOrEqual(suite.count, 6 * AddonConformanceTests.group, "add a group test for the new cases")
    }

    // MARK: the permission table (A4), shared with the harness and Android

    func testThePermissionTable() throws {
        let url = try AddonTestFiles.folder("conformance").appendingPathComponent("permissions.json")
        let rows = try XCTUnwrap(AddonJSON.parse(data: Data(contentsOf: url))["rows"]?.array)
        XCTAssertGreaterThanOrEqual(rows.count, 600)
        var failed: [String] = []
        for row in rows {
            let grants = row["grants"]?.strings ?? []
            let netOn = row["net"]?.bool ?? true
            let want = row["allowed"]?.bool ?? false
            let got: Bool
            if let call = row["call"]?.string {
                got = AddonPermissions.allows(grants, call: call, host: row["host"]?.string, netOn: netOn)
            } else {
                got = AddonPermissions.has(grants, name: row["has"]?.string ?? "", netOn: netOn)
            }
            if got != want { failed.append(row.canonical) }
        }
        XCTAssertEqual(failed, [], "rows the table and the app disagree on")
    }
}
