// The 3D Tree's renderer (S11, the owner's decision of 18 Nov): the tree
// drawn by Metal as two instanced draws, every branch an instance of one
// tapered tube and every leaf one of a flattened ball, lit by one light and
// fading into the page's colour with depth. The draw lists are uploaded once
// per tree and mode (TreeModel3D), each node's state (hidden, chosen,
// linked) is one byte written only when it changes, and a frame makes
// nothing: its uniforms go in a buffer kept for it. Android's TreeGl.kt
// draws the same with the same shaders. TreeHost moves the camera; this
// file only draws what it is given.
#if canImport(Metal) && canImport(MetalKit) && canImport(UIKit)
import Foundation
import Metal
import MetalKit
import SwiftUI
import UIKit

/// Draws a TreeHost's tree into an MTKView, on the main thread, each frame
/// the view asks for (only while something moves).
@MainActor
final class TreeMetalRenderer: NSObject, @preconcurrency MTKViewDelegate {
    static let hidden = TreeState.hidden
    static let chosen = TreeState.chosen
    static let linked = TreeState.linked
    static let faded = TreeState.faded
    static let light = SIMD3<Float>(-0.42, 0.82, 0.39)

    private let device: MTLDevice
    private let queue: MTLCommandQueue
    private let tubePipeline: MTLRenderPipelineState
    private let leafPipeline: MTLRenderPipelineState
    private let depthState: MTLDepthStencilState
    private weak var host: TreeHost?
    private var uploaded: TreeModel3D?
    private var tube: (vertices: MTLBuffer, indices: MTLBuffer, count: Int)?
    private var ball: (vertices: MTLBuffer, indices: MTLBuffer, count: Int)?
    private var branches: MTLBuffer?
    private var leaves: MTLBuffer?
    private var state: MTLBuffer?
    private var stateVersion = -1
    /// view, projection, light, sky, fog (start, end, cap), accent, the twig's
    /// and leaf's floors and the crown's reach: 52 floats, written in place.
    private var uniforms = [Float](repeating: 0, count: 52)
    /// Why nothing is drawn, for the readout; nil while it draws.
    private(set) var failure: String?

    init?(view: MTKView, host: TreeHost) {
        guard let device = view.device ?? MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { return nil }
        self.device = device
        self.queue = queue
        self.host = host
        do {
            let library = try device.makeLibrary(source: Self.shaders, options: nil)
            func pipeline(_ vertex: String) throws -> MTLRenderPipelineState {
                let d = MTLRenderPipelineDescriptor()
                d.vertexFunction = library.makeFunction(name: vertex)
                d.fragmentFunction = library.makeFunction(name: "tree_lit")
                d.colorAttachments[0].pixelFormat = view.colorPixelFormat
                d.depthAttachmentPixelFormat = view.depthStencilPixelFormat
                d.rasterSampleCount = view.sampleCount
                return try device.makeRenderPipelineState(descriptor: d)
            }
            tubePipeline = try pipeline("tree_tube")
            leafPipeline = try pipeline("tree_leaf")
        } catch {
            NSLog("Tree: the shaders did not build: %@", String(describing: error))
            return nil
        }
        let depth = MTLDepthStencilDescriptor()
        depth.depthCompareFunction = .lessEqual
        depth.isDepthWriteEnabled = true
        guard let depthState = device.makeDepthStencilState(descriptor: depth) else { return nil }
        self.depthState = depthState
        super.init()
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let host else { return }
        host.advance(now: CACurrentMediaTime())
        guard let model = host.model else { return }
        if model !== uploaded { upload(model) }
        if host.stateVersion != stateVersion { writeState(host) }
        let sky = TreeModel3D.rgb(model.colors.sky)
        view.clearColor = MTLClearColor(red: Double(sky.x), green: Double(sky.y), blue: Double(sky.z), alpha: 1)
        guard let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
              let buffer = queue.makeCommandBuffer(), let encoder = buffer.makeRenderCommandEncoder(descriptor: pass) else {
            failure = "no drawable"
            return
        }
        failure = nil
        let size = view.drawableSize
        if size.width > 0, size.height > 0, let tube, let ball, let state {
            fill(host.camera, model: model, focusReach: host.focusReach, contrast: host.contrast,
                 width: Float(size.width), height: Float(size.height), scale: Float(view.contentScaleFactor))
            encoder.setDepthStencilState(depthState)
            encoder.setCullMode(.none)
            uniforms.withUnsafeBytes { raw in
                encoder.setVertexBytes(raw.baseAddress!, length: raw.count, index: 2)
                encoder.setFragmentBytes(raw.baseAddress!, length: raw.count, index: 0)
            }
            encoder.setVertexBuffer(state, offset: 0, index: 3)
            if let branches, model.branchCount > 0 {
                encoder.setRenderPipelineState(tubePipeline)
                encoder.setVertexBuffer(tube.vertices, offset: 0, index: 0)
                encoder.setVertexBuffer(branches, offset: 0, index: 1)
                encoder.drawIndexedPrimitives(type: .triangle, indexCount: tube.count, indexType: .uint16,
                                              indexBuffer: tube.indices, indexBufferOffset: 0, instanceCount: model.branchCount)
            }
            if let leaves, model.leafCount > 0 {
                encoder.setRenderPipelineState(leafPipeline)
                encoder.setVertexBuffer(ball.vertices, offset: 0, index: 0)
                encoder.setVertexBuffer(leaves, offset: 0, index: 1)
                encoder.drawIndexedPrimitives(type: .triangle, indexCount: ball.count, indexType: .uint16,
                                              indexBuffer: ball.indices, indexBufferOffset: 0, instanceCount: model.leafCount)
            }
        }
        encoder.endEncoding()
        buffer.present(drawable)
        buffer.commit()
        host.drawn(model: model)
        // still again: the next frame is asked for (setNeedsDisplay) when something changes
        if !host.moving, !view.isPaused {
            view.isPaused = true
            view.enableSetNeedsDisplay = true
        }
    }

    /// The tree's lists and meshes, once per tree and mode.
    private func upload(_ m: TreeModel3D) {
        uploaded = m
        func floats(_ a: [Float]) -> MTLBuffer? {
            a.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: max($0.count, 4), options: .storageModeShared) }
        }
        func shorts(_ a: [UInt16]) -> MTLBuffer? {
            a.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: max($0.count, 2), options: .storageModeShared) }
        }
        let t = TreeModel3D.tube(m.segments), b = TreeModel3D.ball(rings: m.rings, segments: m.segments)
        if let v = floats(t.vertices), let i = shorts(t.indices) { tube = (v, i, t.indices.count) }
        if let v = floats(b.vertices), let i = shorts(b.indices) { ball = (v, i, b.indices.count) }
        branches = m.branchCount > 0 ? floats(m.branches) : nil
        leaves = m.leafCount > 0 ? floats(m.leaves) : nil
        state = device.makeBuffer(length: max(m.nodes.size, 1), options: .storageModeShared)
        stateVersion = -1
    }

    /// Each node's state byte, only when the host's changed.
    private func writeState(_ host: TreeHost) {
        guard let state else { return }
        stateVersion = host.stateVersion
        let bytes = host.states
        let count = min(bytes.count, state.length)
        let out = state.contents().bindMemory(to: UInt8.self, capacity: state.length)
        for i in 0..<count { out[i] = bytes[i] }
    }

    /// The frame's uniforms, in place: the camera's matrices with near and far
    /// hugging the tree, the light, the page's colour and the fog.
    private func fill(_ camera: GraphPerfCamera, model m: TreeModel3D, focusReach: Float, contrast: Bool, width: Float, height: Float, scale: Float) {
        let reach = Float(m.layout.reach[0])
        let centre = TreeCamera3D.centre(m, 0)
        let toCentre = ((centre - camera.eye) * (centre - camera.eye)).sum().squareRoot()
        let near = max(toCentre - reach * 1.15, max(camera.distance, toCentre) * 0.005)
        let far = max(toCentre + reach * 1.15, near * 2)
        let view = camera.viewMatrix(), proj = camera.projectionMatrix(aspect: width / height, near: near, far: far)
        put(view.c0, 0); put(view.c1, 4); put(view.c2, 8); put(view.c3, 12)
        put(proj.c0, 16); put(proj.c1, 20); put(proj.c2, 24); put(proj.c3, 28)
        put(SIMD4(Self.light, 0), 32)
        put(SIMD4(TreeModel3D.rgb(m.colors.sky), 1), 36)
        put(SIMD4(camera.distance * 0.85, camera.distance + focusReach * 1.6, contrast ? 0.55 : 0.82, 0), 40)
        put(SIMD4(TreeModel3D.rgb(m.colors.accent), 1), 44)
        let focal = camera.focal(viewHeight: height)
        let twig: Float = TreeWeight.twigFloor(scale: scale, focal: focal)
        let leaf: Float = TreeWeight.leafFloor(scale: scale, focal: focal)
        let ceiling: Float = TreeWeight.leafCeiling(scale: scale, focal: focal)
        put(SIMD4<Float>(twig, leaf, m.crown, ceiling), 48)
    }

    private func put(_ v: SIMD4<Float>, _ at: Int) {
        uniforms[at] = v.x; uniforms[at + 1] = v.y; uniforms[at + 2] = v.z; uniforms[at + 3] = v.w
    }

    /// Android's TreeGl shaders, in Metal: the same tube, ball, light and fog.
    static let shaders = """
    #include <metal_stdlib>
    using namespace metal;

    struct TreeU {
        float4x4 view;
        float4x4 proj;
        float4 light;
        float4 sky;
        float4 fog;
        float4 accent;
        float4 extra;
    };

    struct TreeOut {
        float4 position [[position]];
        float3 normal;
        float3 color;
        float depth;
        float shade;
        float state [[flat]];
    };

    static TreeOut finish(float3 p, float3 n, float3 color, float state, constant TreeU& u) {
        TreeOut o;
        float4 eye = u.view * float4(p, 1.0);
        o.depth = -eye.z; o.normal = n; o.color = color; o.state = state;
        // deep in the crown, near the trunk's line, is darker: the crown's depth reads
        o.shade = 0.7 + 0.3 * clamp(length(p.xz) / max(u.extra.z, 1e-4), 0.0, 1.0);
        // a hidden node folds to one point outside the view: nothing is drawn
        o.position = (state > 0.5 && state < 1.5) ? float4(2.0, 2.0, 2.0, 1.0) : u.proj * eye;
        return o;
    }

    static void across(float3 d, thread float3& a, thread float3& b) {
        float3 helper = abs(d.y) < 0.9 ? float3(0.0, 1.0, 0.0) : float3(1.0, 0.0, 0.0);
        a = normalize(cross(helper, d));
        b = cross(d, a);
    }

    // a branch: the unit tube's ring (cos, sin, along) set round the base-to-tip axis, tapering
    vertex TreeOut tree_tube(uint vid [[vertex_id]], uint iid [[instance_id]],
                             const device float* ring [[buffer(0)]], const device float* inst [[buffer(1)]],
                             constant TreeU& u [[buffer(2)]], const device uchar* state [[buffer(3)]]) {
        float3 g = float3(ring[vid * 3], ring[vid * 3 + 1], ring[vid * 3 + 2]);
        const device float* b = inst + iid * 12;
        float3 base = float3(b[0], b[1], b[2]);
        float3 axis = float3(b[3], b[4], b[5]) - base;
        float len = max(length(axis), 1e-6);
        float3 d = axis / len;
        float3 a; float3 c2;
        across(d, a, c2);
        float3 n = a * g.x + c2 * g.y;
        float3 c = base + axis * g.z;
        float depth = max(-(u.view * float4(c, 1.0)).z, 1e-4);
        float r = max(mix(b[6], b[7], g.z), u.extra.x * depth);
        // past the tip by its radius, so a child's joint and a leaf's stalk close
        return finish(c + n * r + d * (g.z * r), n, float3(b[8], b[9], b[10]), float(state[uint(b[11] + 0.5)]), u);
    }

    // a leaf: the unit ball, long along its twig, narrower across and flat, round its centre
    vertex TreeOut tree_leaf(uint vid [[vertex_id]], uint iid [[instance_id]],
                             const device float* unit [[buffer(0)]], const device float* inst [[buffer(1)]],
                             constant TreeU& u [[buffer(2)]], const device uchar* state [[buffer(3)]]) {
        float3 g = float3(unit[vid * 3], unit[vid * 3 + 1], unit[vid * 3 + 2]);
        const device float* l = inst + iid * 11;
        float3 centre = float3(l[0], l[1], l[2]);
        float3 d = normalize(float3(l[3], l[4], l[5]));
        float3 a; float3 c2;
        across(d, a, c2);
        float depth = max(-(u.view * float4(centre, 1.0)).z, 1e-4);
        // never under the floor, nor over the ceiling: flown in, a leaf stays a leaf, not a wall
        float r = min(max(l[6], u.extra.y * depth), u.extra.w * depth);
        float3 shape = float3(0.62, 1.0, 0.34);
        float3 p = centre + (a * g.x * shape.x + d * g.y * shape.y + c2 * g.z * shape.z) * r;
        float3 n = normalize(a * g.x / shape.x + d * g.y / shape.y + c2 * g.z / shape.z);
        return finish(p, n, float3(l[7], l[8], l[9]), float(state[uint(l[10] + 0.5)]), u);
    }

    // Lambert from one light, both faces, darker deep in the crown, a chosen node toward the accent, then the page's colour with depth
    fragment float4 tree_lit(TreeOut in [[stage_in]], constant TreeU& u [[buffer(0)]]) {
        float3 n = normalize(in.normal);
        float lambert = abs(dot(n, normalize(u.light.xyz)));
        float3 c = in.color * (0.46 + 0.64 * lambert) * in.shade;
        if (in.state > 3.5) c = mix(c, u.sky.xyz, 0.72);
        else if (in.state > 2.5) c = mix(c, u.accent.xyz, 0.4);
        else if (in.state > 1.5) c = mix(c, u.accent.xyz, 0.7);
        float f = clamp((in.depth - u.fog.x) / max(u.fog.y - u.fog.x, 1e-3), 0.0, 1.0) * u.fog.z;
        return float4(mix(c, u.sky.xyz, f), 1.0);
    }
    """
}

/// The MTKView the Tree draws in: paused until something moves, at the
/// Graphics setting's frame rate (120 on a ProMotion screen at High).
struct TreeMetalView: UIViewRepresentable {
    let host: TreeHost

    final class Coordinator { var renderer: TreeMetalRenderer? }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.colorPixelFormat = .bgra8Unorm
        view.depthStencilPixelFormat = .depth32Float
        let high = GraphQuality.current.tier == .high
        view.sampleCount = high && (view.device?.supportsTextureSampleCount(4) ?? false) ? 4 : 1
        view.framebufferOnly = true
        view.isOpaque = true
        view.enableSetNeedsDisplay = true
        view.isPaused = true
        view.autoResizeDrawable = true
        view.preferredFramesPerSecond = GraphQuality.frameRate
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        context.coordinator.renderer = TreeMetalRenderer(view: view, host: host)
        view.delegate = context.coordinator.renderer
        // without a renderer every move lands at once (TreeHost.go)
        if context.coordinator.renderer != nil { host.metal = view }
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        view.preferredFramesPerSecond = GraphQuality.frameRate
        view.setNeedsDisplay()
    }
}
#endif
