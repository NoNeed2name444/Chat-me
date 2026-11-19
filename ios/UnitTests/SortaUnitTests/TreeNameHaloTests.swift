import CoreGraphics
import XCTest
@testable import Sorta

/// A Tree name's halo is as wide as asked at the size the name is drawn: the
/// third pass's stills showed halos several times too wide, blanking the tree
/// round every name, because the words were stroked at their 100-point size.
final class TreeNameHaloTests: XCTestCase {
    func testAHaloStaysRoundItsWords() throws {
        for size: Float in [11, 14, 18] {
            let (inside, outside) = try painted(size: size, halo: 2.5)
            XCTAssertGreaterThan(inside, 0, "size \(size): the halo is drawn")
            XCTAssertEqual(outside, 0, "size \(size): nothing is painted past the halo")
        }
    }

    /// Draws "Heart failure" centred in a bitmap, its halo red and its ink
    /// black, and counts the halo's pixels inside the words' box grown by the
    /// halo (and a point for smoothing) and every painted pixel outside it.
    private func painted(size: Float, halo: Float) throws -> (inside: Int, outside: Int) {
        let sink = TreeCGSink(titles: ["All ideas", "Heart failure"])
        let width = 400, height = 160
        let cg = try XCTUnwrap(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                                         space: CGColorSpaceCreateDeviceRGB(),
                                         bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        // y down, as the names' Canvas
        cg.translateBy(x: 0, y: CGFloat(height))
        cg.scaleBy(x: 1, y: -1)
        let cx: Float = 200, cy: Float = 80
        sink.cg = cg
        sink.text(1, cx, cy, size, true, 2, 0x000000, 0xFF0000, 2 * halo)
        sink.cg = nil
        let words = sink.box(1, bold: true, size: size)
        let grow = halo + 1
        let left = cx - words.width / 2 - grow, right = cx + words.width / 2 + grow
        let top = cy - words.height / 2 - grow, bottom = cy + words.height / 2 + grow
        let data = try XCTUnwrap(cg.data).bindMemory(to: UInt8.self, capacity: width * height * 4)
        var inside = 0, outside = 0
        for row in 0..<height {
            for col in 0..<width {
                let o = row * width * 4 + col * 4
                let alpha = data[o + 3]
                if alpha == 0 { continue }
                let x = Float(col) + 0.5, y = Float(row) + 0.5
                if x >= left, x <= right, y >= top, y <= bottom {
                    if data[o] > 128, data[o + 1] < 64 { inside += 1 }
                } else {
                    outside += 1
                }
            }
        }
        return (inside, outside)
    }
}
