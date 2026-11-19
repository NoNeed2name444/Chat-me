import CoreGraphics
import XCTest
@testable import Sorta

/// A Tree name's halo is as wide as asked at the size the name is drawn: the
/// third pass's stills showed halos several times too wide, blanking the tree
/// round every name, because the words were stroked at their 100-point size.
/// The Fractal's names are drawn by the same sink, so they are checked too.
final class TreeNameHaloTests: XCTestCase {
    func testAHaloStaysRoundItsWords() throws {
        for size: Float in [11, 14, 18] {
            let (inside, outside) = try painted(size: size, halo: 2.5, isHalo: { r, g, _, _ in r > 128 && g < 64 }) { sink, cx, cy in
                sink.text(1, cx, cy, size, true, 2, 0x000000, 0xFF0000, 2 * 2.5)
            }
            XCTAssertGreaterThan(inside, 0, "size \(size): the halo is drawn")
            XCTAssertEqual(outside, 0, "size \(size): nothing is painted past the halo")
        }
    }

    /// The Fractal draws its names as the map does (FractalNameInk), with the
    /// host's style: a halo of the sky `style.halo` points round the words, not
    /// the 19 that hid the dots and lines round a name in run 44449703457.
    func testAFractalNameHaloStaysRoundItsWords() throws {
        let style = TreeNames3D.Style()
        for size: Float in [11, 13, 18] {
            // the sky's halo is near black and opaque; the ink is white
            let (inside, outside) = try painted(size: size, halo: style.halo, isHalo: { r, g, b, a in a == 255 && r < 32 && g < 32 && b < 32 }) { sink, cx, cy in
                let words = sink.box(1, bold: true, size: size)
                let p = TreePlacedName(index: 1, size: size, bold: true, x: cx, y: cy, width: words.width, height: words.height, spot: 0)
                FractalNameInk.draw(p, sink: sink, style: style)
            }
            XCTAssertGreaterThan(inside, 0, "size \(size): the halo is drawn")
            XCTAssertEqual(outside, 0, "size \(size): nothing is painted past the halo")
        }
    }

    /// Draws "Heart failure" centred in a bitmap with `draw`, and counts the
    /// halo's pixels (`isHalo`, on premultiplied red, green, blue and alpha) inside
    /// the words' box grown by the halo (and a point for smoothing) and every
    /// painted pixel outside it.
    private func painted(size: Float, halo: Float, isHalo: (UInt8, UInt8, UInt8, UInt8) -> Bool,
                         draw: (TreeCGSink, Float, Float) -> Void) throws -> (inside: Int, outside: Int) {
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
        draw(sink, cx, cy)
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
                    if isHalo(data[o], data[o + 1], data[o + 2], alpha) { inside += 1 }
                } else {
                    outside += 1
                }
            }
        }
        return (inside, outside)
    }
}
