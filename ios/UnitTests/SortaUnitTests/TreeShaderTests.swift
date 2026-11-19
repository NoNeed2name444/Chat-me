#if canImport(Metal)
import Metal
import XCTest
@testable import Sorta

/// The Tree's Metal shaders build (S11, the owner's review of 19 Nov): they
/// are compiled from source when the Tree first draws, so a mistake in them
/// would only show as an empty map. This builds them, and both pipelines, as
/// TreeMetalRenderer does, with the formats TreeMetalView draws into.
final class TreeShaderTests: XCTestCase {
    @MainActor
    func testTheTreeShadersBuild() throws {
        let device = try XCTUnwrap(MTLCreateSystemDefaultDevice(), "a Metal device")
        let library = try device.makeLibrary(source: TreeMetalRenderer.shaders, options: nil)
        for name in ["tree_tube", "tree_leaf", "tree_lit"] {
            XCTAssertNotNil(library.makeFunction(name: name), "\(name) is in the library")
        }
        for vertex in ["tree_tube", "tree_leaf"] {
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = library.makeFunction(name: vertex)
            d.fragmentFunction = library.makeFunction(name: "tree_lit")
            d.colorAttachments[0].pixelFormat = .bgra8Unorm
            d.depthAttachmentPixelFormat = .depth32Float
            XCTAssertNoThrow(try device.makeRenderPipelineState(descriptor: d), "\(vertex) with tree_lit")
        }
    }
}
#endif
