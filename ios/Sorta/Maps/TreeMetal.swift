// The 3D Tree's renderer (S11, the owner's decision of 18 Nov): the tree
// drawn by Metal as two instanced draws, every branch an instance of one
// curved tube, tapering, the trunk flaring at its foot, and every leaf one of
// a folded, curled leaf blade (the owner's review of 19 Nov), lit by a key
// light, a soft sky-and-ground ambient and a little rim, both faces of a leaf
// lit, and fading into the page's colour with depth. The draw lists are uploaded once
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
    private var blade: (vertices: MTLBuffer, indices: MTLBuffer, count: Int)?
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
        if size.width > 0, size.height > 0, let tube, let blade, let state {
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
                encoder.setVertexBuffer(blade.vertices, offset: 0, index: 0)
                encoder.setVertexBuffer(leaves, offset: 0, index: 1)
                encoder.drawIndexedPrimitives(type: .triangle, indexCount: blade.count, indexType: .uint16,
                                              indexBuffer: blade.indices, indexBufferOffset: 0, instanceCount: model.leafCount)
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
        let t = TreeModel3D.tube(m.segments, rings: m.rings), b = TreeModel3D.blade(m.bladeRows)
        if let v = floats(t.vertices), let i = shorts(t.indices) { tube = (v, i, t.indices.count) }
        if let v = floats(b.vertices), let i = shorts(b.indices) { blade = (v, i, b.indices.count) }
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

    /// Android's TreeGl shaders, in Metal: the same curved tube, leaf blade, light and fog.
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
        float3 eye;
        float3 key;
        float3 up;
        float3 color;
        float depth;
        float shade;
        float leaf;
        float state [[flat]];
    };

    // the light and the normal in the eye's space, where the rim and both faces are worked out
    static TreeOut finish(float3 p, float3 n, float3 color, float state, float leaf, constant TreeU& u) {
        TreeOut o;
        float4 eye = u.view * float4(p, 1.0);
        float3x3 turn = float3x3(u.view[0].xyz, u.view[1].xyz, u.view[2].xyz);
        o.depth = -eye.z; o.eye = eye.xyz; o.normal = turn * n; o.key = turn * u.light.xyz; o.up = turn * float3(0.0, 1.0, 0.0);
        o.color = color; o.state = state; o.leaf = leaf;
        // deep in the crown, near the trunk's line, is darker: the crown's depth reads
        o.shade = 0.76 + 0.24 * clamp(length(p.xz) / max(u.extra.z, 1e-4), 0.0, 1.0);
        // a hidden node folds to one point outside the view: nothing is drawn
        o.position = (state > 0.5 && state < 1.5) ? float4(2.0, 2.0, 2.0, 1.0) : u.proj * eye;
        return o;
    }

    // a branch: the unit tube's ring (cos, sin, along) set round its curve from base through the control point to tip,
    // square to the curve there, tapering; the trunk flares at its foot and every branch thickens where it leaves its parent
    vertex TreeOut tree_tube(uint vid [[vertex_id]], uint iid [[instance_id]],
                             const device float* ring [[buffer(0)]], const device float* inst [[buffer(1)]],
                             constant TreeU& u [[buffer(2)]], const device uchar* state [[buffer(3)]]) {
        float3 g = float3(ring[vid * 3], ring[vid * 3 + 1], ring[vid * 3 + 2]);
        const device float* b = inst + iid * 15;
        float3 base = float3(b[0], b[1], b[2]);
        float3 tip = float3(b[3], b[4], b[5]);
        float3 ctrl = float3(b[6], b[7], b[8]);
        // the closing ring (past 1 along) is one point just past the tip
        float cap = step(1.5, g.z);
        float t = min(g.z, 1.0), s = 1.0 - t;
        float3 c = s * s * base + 2.0 * s * t * ctrl + t * t * tip;
        float3 d = 2.0 * s * (ctrl - base) + 2.0 * t * (tip - ctrl);
        d = d / max(length(d), 1e-9);
        // one across for the whole branch, from its chord, turned square to the curve at each ring
        float3 chord = tip - base;
        chord = chord / max(length(chord), 1e-9);
        float3 helper = abs(chord.y) < 0.9 ? float3(0.0, 1.0, 0.0) : float3(1.0, 0.0, 0.0);
        float3 a = normalize(cross(helper, chord));
        float3 uu = normalize(a - d * dot(a, d));
        float3 vv = cross(d, uu);
        float3 side = uu * g.x + vv * g.y;
        float depth = max(-(u.view * float4(c, 1.0)).z, 1e-4);
        float swell = b[14] < 0.5 ? \(TreeModel3D.flare) * pow(max(0.0, 1.0 - t / \(TreeModel3D.flareSpan)), 2.0)
            : \(TreeModel3D.collar) * pow(max(0.0, 1.0 - t / \(TreeModel3D.collarSpan)), 2.0);
        float r = max(mix(b[9], b[10], t) * (1.0 + swell), u.extra.x * depth);
        // the tip closed by a short cone past it, so no branch ends in an open cut and a leaf's stalk joins
        float3 n = normalize(mix(side, d, cap * 0.7));
        return finish(c + side * r * (1.0 - cap) + d * (cap * \(TreeModel3D.capReach) * r), n, float3(b[11], b[12], b[13]),
                      float(state[uint(b[14] + 0.5)]), 0.0, u);
    }

    // a leaf: the blade (its place and normal) from its stem's tip out along its axis, its face turned up toward the sky,
    // then rolled about its axis by its own turn
    vertex TreeOut tree_leaf(uint vid [[vertex_id]], uint iid [[instance_id]],
                             const device float* mesh [[buffer(0)]], const device float* inst [[buffer(1)]],
                             constant TreeU& u [[buffer(2)]], const device uchar* state [[buffer(3)]]) {
        float3 place = float3(mesh[vid * 6], mesh[vid * 6 + 1], mesh[vid * 6 + 2]);
        float3 face = float3(mesh[vid * 6 + 3], mesh[vid * 6 + 4], mesh[vid * 6 + 5]);
        const device float* l = inst + iid * 12;
        float3 centre = float3(l[0], l[1], l[2]);
        float3 d = normalize(float3(l[3], l[4], l[5]));
        float3 helper = abs(d.y) < 0.95 ? float3(0.0, 1.0, 0.0) : float3(1.0, 0.0, 0.0);
        float3 across = normalize(cross(d, helper));
        float3 up = cross(across, d);
        float cr = cos(l[7]), sr = sin(l[7]);
        float3 x = across * cr + up * sr;
        float3 z = up * cr - across * sr;
        float depth = max(-(u.view * float4(centre, 1.0)).z, 1e-4);
        // never under the floor, nor over the ceiling: flown in, a leaf stays a leaf, not a wall
        float r = min(max(l[6], u.extra.y * depth), u.extra.w * depth);
        float3 p = centre + (x * place.x + d * place.y + z * place.z) * r;
        float3 n = normalize(x * face.x + d * face.y + z * face.z);
        return finish(p, n, float3(l[8], l[9], l[10]), float(state[uint(l[11] + 0.5)]), 1.0, u);
    }

    // a key light, a soft ambient brighter from the sky than the ground, and a little rim where a surface turns away;
    // the face toward the eye lit, so a leaf seen from below is lit too, and the key glowing through it then; a faint
    // waxy gloss on a leaf; then a chosen node toward the accent, a faded one toward the page, and the page's colour with depth
    fragment float4 tree_lit(TreeOut in [[stage_in]], constant TreeU& u [[buffer(0)]]) {
        float3 n = normalize(in.normal);
        float3 e = normalize(-in.eye);
        float3 l = normalize(in.key);
        float facing = dot(n, e);
        if (facing < 0.0) { n = -n; facing = -facing; }
        float key = max(dot(n, l), 0.0);
        float through = in.leaf * max(dot(-n, l), 0.0) * 0.45;
        float ambient = mix(0.34, 0.56, 0.5 + 0.5 * dot(n, normalize(in.up)));
        float rim = pow(1.0 - facing, 3.0) * 0.22;
        float gloss = in.leaf * pow(max(dot(n, normalize(l + e)), 0.0), 28.0) * 0.12;
        float3 c = in.color * (ambient + 0.72 * key + through) * in.shade + float3(rim + gloss);
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
