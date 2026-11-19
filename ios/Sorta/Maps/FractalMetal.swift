// The 3D Fractal's renderer (S12, the owner's rule of 18 Nov: the Fractal is
// 3D): drawn by Metal as Performance draws its map, on Performance's night
// sky by day too, each dot a lit ball facing the camera with a lit hub's
// glow round it, and thin lines, at least FractalSight.lineWidth across, from
// each dot to its hub and between linked ideas, all added onto the sky. What is drawn is FractalSight's
// frame, which FractalHost works out each time the pose changes: at most the
// tier's budget of dots, so a frame costs the same for 100 ideas or 100,000.
// Two instanced draws a frame, lines then dots, from buffers made once (three
// of each, so the CPU never writes what the GPU still reads) and filled in
// place: a frame makes nothing. Android's FractalGl.kt draws the same with
// the same shaders.
#if canImport(Metal) && canImport(MetalKit) && canImport(UIKit)
import Foundation
import Metal
import MetalKit
import SwiftUI
import UIKit

/// Draws a FractalHost's frame into an MTKView, on the main thread, each
/// frame the view asks for (only while something moves).
@MainActor
final class FractalMetalRenderer: NSObject, @preconcurrency MTKViewDelegate {
    /// A dot: place (3), radius in pixels, colour (3), brightness, how much a lit hub, ring.
    static let dotFloats = 10
    /// A line: its two ends (3 and 3) and its colour (3), already as bright as it is drawn.
    static let lineFloats = 9
    static let inFlight = 3

    private let device: MTLDevice
    private let queue: MTLCommandQueue
    private let dotPipeline: MTLRenderPipelineState
    private let linePipeline: MTLRenderPipelineState
    private weak var host: FractalHost?
    private let dots: [MTLBuffer]
    private let lines: [MTLBuffer]
    private let lineCap = FractalRules.budget + FractalRules.linkBudget
    private var turn = 0
    private let gate = DispatchSemaphore(value: FractalMetalRenderer.inFlight)
    /// view-projection (16), then the viewport in pixels, the glow's reach and 0: 20 floats, written in place.
    private var uniforms = [Float](repeating: 0, count: 20)
    /// Why nothing is drawn, for the readout; nil while it draws.
    private(set) var failure: String?
    /// How many lines the last frame drew, links and tree lines.
    private(set) var lineCount = 0

    init?(view: MTKView, host: FractalHost) {
        guard let device = view.device ?? MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { return nil }
        self.device = device
        self.queue = queue
        self.host = host
        do {
            let library = try device.makeLibrary(source: Self.shaders, options: nil)
            func pipeline(_ vertex: String, _ fragment: String) throws -> MTLRenderPipelineState {
                let d = MTLRenderPipelineDescriptor()
                d.vertexFunction = library.makeFunction(name: vertex)
                d.fragmentFunction = library.makeFunction(name: fragment)
                let c = d.colorAttachments[0]!
                c.pixelFormat = view.colorPixelFormat
                // everything adds onto the sky, as Performance's glow does (GL_ONE, GL_ONE)
                c.isBlendingEnabled = true
                c.rgbBlendOperation = .add
                c.alphaBlendOperation = .add
                c.sourceRGBBlendFactor = .one
                c.destinationRGBBlendFactor = .one
                c.sourceAlphaBlendFactor = .one
                c.destinationAlphaBlendFactor = .one
                d.rasterSampleCount = view.sampleCount
                return try device.makeRenderPipelineState(descriptor: d)
            }
            dotPipeline = try pipeline("fractal_dot", "fractal_dot_lit")
            linePipeline = try pipeline("fractal_line", "fractal_line_lit")
        } catch {
            NSLog("Fractal: the shaders did not build: %@", String(describing: error))
            return nil
        }
        var dots: [MTLBuffer] = [], lines: [MTLBuffer] = []
        for _ in 0..<Self.inFlight {
            guard let d = device.makeBuffer(length: FractalRules.budget * Self.dotFloats * 4, options: .storageModeShared),
                  let l = device.makeBuffer(length: (FractalRules.budget + FractalRules.linkBudget) * Self.lineFloats * 4,
                                            options: .storageModeShared) else { return nil }
            dots.append(d)
            lines.append(l)
        }
        self.dots = dots
        self.lines = lines
        super.init()
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let host else { return }
        host.advance(now: CACurrentMediaTime())
        let sky = FractalHost.rgb(FractalTones.sky)
        view.clearColor = MTLClearColor(red: Double(sky.x), green: Double(sky.y), blue: Double(sky.z), alpha: 1)
        guard let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable else {
            failure = "no drawable"
            return
        }
        gate.wait()
        guard let buffer = queue.makeCommandBuffer(), let encoder = buffer.makeRenderCommandEncoder(descriptor: pass) else {
            gate.signal()
            failure = "no command buffer"
            return
        }
        failure = nil
        let gate = self.gate
        buffer.addCompletedHandler { _ in gate.signal() }
        let size = view.drawableSize
        if size.width > 0, size.height > 0, let model = host.model, let sight = host.sight, host.sized {
            turn = (turn + 1) % Self.inFlight
            let dotBuffer = dots[turn], lineBuffer = lines[turn]
            // the sight works in points; the shader in the drawable's pixels
            let scale = Float(size.height) / max(Float(host.size.height), 1)
            fill(sight, model: model, dots: dotBuffer, lines: lineBuffer, scale: scale)
            put(host.viewProj, width: Float(size.width), height: Float(size.height), lineWidth: max(FractalSight.lineWidth * scale, 1))
            encoder.setCullMode(.none)
            uniforms.withUnsafeBytes { raw in encoder.setVertexBytes(raw.baseAddress!, length: raw.count, index: 1) }
            if lineCount > 0 {
                encoder.setRenderPipelineState(linePipeline)
                encoder.setVertexBuffer(lineBuffer, offset: 0, index: 0)
                encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4, instanceCount: lineCount)
            }
            if sight.count > 0 {
                encoder.setRenderPipelineState(dotPipeline)
                encoder.setVertexBuffer(dotBuffer, offset: 0, index: 0)
                encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4, instanceCount: sight.count)
            }
        }
        encoder.endEncoding()
        buffer.present(drawable)
        buffer.commit()
        host.drawn()
        // still again: the next frame is asked for (setNeedsDisplay) when something changes
        if !host.moving, !view.isPaused {
            view.isPaused = true
            view.enableSetNeedsDisplay = true
        }
    }

    /// The frame's dots and lines into this turn's buffers, in place.
    private func fill(_ s: FractalSight, model: FractalModel, dots: MTLBuffer, lines: MTLBuffer, scale: Float) {
        let pos = model.layout.pos
        let tones = model.tones
        let d = dots.contents().bindMemory(to: Float.self, capacity: FractalRules.budget * Self.dotFloats)
        let count = min(s.count, FractalRules.budget)
        for k in 0..<count {
            let i = s.node[k], o = k * Self.dotFloats
            d[o] = Float(pos[3 * i]); d[o + 1] = Float(pos[3 * i + 1]); d[o + 2] = Float(pos[3 * i + 2])
            d[o + 3] = s.radius[k] * scale
            // a lit hub's ball is its colour mixed toward white (Performance's 0.45), an idea's its own
            let h = s.hub[k]
            let c = tones.dot[i], b = tones.hub[i]
            d[o + 4] = Self.mix(c >> 16, b >> 16, h); d[o + 5] = Self.mix(c >> 8, b >> 8, h); d[o + 6] = Self.mix(c, b, h)
            d[o + 7] = s.alpha[k]; d[o + 8] = h; d[o + 9] = i == s.chosen ? 1 : 0
        }
        let l = lines.contents().bindMemory(to: Float.self, capacity: lineCap * Self.lineFloats)
        var n = 0
        for k in 0..<count {
            let p = s.parentSlot(k)
            if p < 0 || n >= lineCap { continue }
            line(l, n, s, pos, p, k, s.alpha[k] * FractalSight.treeLine, tones.dot[s.node[k]])
            n += 1
        }
        for e in 0..<s.linkCount where n < lineCap {
            line(l, n, s, pos, s.linkFrom[e], s.linkTo[e], s.linkAlpha[e], tones.dot[s.node[s.linkFrom[e]]])
            n += 1
        }
        lineCount = n
    }

    /// Line `n`: from dot slot `a` to slot `b`, its colour already as bright as it is drawn.
    private func line(_ l: UnsafeMutablePointer<Float>, _ n: Int, _ s: FractalSight, _ pos: [Double],
                      _ a: Int, _ b: Int, _ alpha: Float, _ rgb: Int) {
        let r = Float((rgb >> 16) & 255) / 255 * alpha, g = Float((rgb >> 8) & 255) / 255 * alpha, bl = Float(rgb & 255) / 255 * alpha
        let o = n * Self.lineFloats
        let i = s.node[a], j = s.node[b]
        l[o] = Float(pos[3 * i]); l[o + 1] = Float(pos[3 * i + 1]); l[o + 2] = Float(pos[3 * i + 2])
        l[o + 3] = Float(pos[3 * j]); l[o + 4] = Float(pos[3 * j + 1]); l[o + 5] = Float(pos[3 * j + 2])
        l[o + 6] = r; l[o + 7] = g; l[o + 8] = bl
    }

    private static func mix(_ a: Int, _ b: Int, _ t: Float) -> Float {
        let x = Float(a & 255), y = Float(b & 255)
        return (x + (y - x) * t) / 255
    }

    private func put(_ m: GraphPerfMatrix, width: Float, height: Float, lineWidth: Float) {
        for r in 0..<4 {
            uniforms[r] = m.c0[r]; uniforms[4 + r] = m.c1[r]; uniforms[8 + r] = m.c2[r]; uniforms[12 + r] = m.c3[r]
        }
        uniforms[16] = width; uniforms[17] = height; uniforms[18] = FractalSight.glowReach; uniforms[19] = lineWidth
    }

    /// Android's FractalGl shaders, in Metal: the same lit ball, glow, ring and lines.
    static let shaders = """
    #include <metal_stdlib>
    using namespace metal;

    struct FractalU {
        float4x4 vp;
        float4 extra; // viewport width, height (pixels), the glow's reach, a line's width (pixels)
    };

    struct DotOut {
        float4 position [[position]];
        float2 at;
        float3 color;
        float hub;
        float ring;
        float size;
    };

    // a dot: its quad set round its place on screen, its radius in pixels, room for a hub's glow
    vertex DotOut fractal_dot(uint vid [[vertex_id]], uint iid [[instance_id]],
                              const device float* inst [[buffer(0)]], constant FractalU& u [[buffer(1)]]) {
        float2 corner = float2((vid & 1u) ? 1.0 : -1.0, (vid & 2u) ? 1.0 : -1.0);
        const device float* d = inst + iid * 10;
        float4 c = u.vp * float4(d[0], d[1], d[2], 1.0);
        float hub = d[8];
        float reach = 1.0 + u.extra.z * hub;
        DotOut o;
        o.at = corner * reach;
        o.color = float3(d[4], d[5], d[6]) * d[7];
        o.hub = hub;
        o.ring = d[9];
        o.size = d[3];
        float2 offset = corner * d[3] * reach * 2.0 / u.extra.xy;
        o.position = c.w <= 1e-4 ? float4(2.0, 2.0, 0.5, 1.0) : float4(c.xy + offset * c.w, 0.5 * c.w, c.w);
        return o;
    }

    // Performance's lit point: a ball lit from the upper left, a soft glow round a lit hub, a white ring when chosen
    fragment float4 fractal_dot_lit(DotOut in [[stage_in]]) {
        float r2 = dot(in.at, in.at);
        float r = sqrt(r2);
        float core = 1.0 - smoothstep(0.86, 1.0, r);
        float z = sqrt(max(0.0, 1.0 - r2));
        float lit = mix(1.0, 0.45 + 0.55 * z + 0.35 * pow(max(0.0, dot(normalize(float3(in.at, z + 1e-3)), normalize(float3(-0.4, 0.5, 0.8)))), 12.0),
                        smoothstep(3.0, 10.0, in.size));
        float glow = in.hub * 0.55 * exp(-r2 * 1.6);
        float3 col = in.color * (core * lit * 0.9 + glow);
        if (in.ring > 0.5) col = mix(col, float3(1.0), smoothstep(0.66, 0.74, r) * (1.0 - smoothstep(0.86, 0.94, r)));
        if (core <= 0.0 && glow < 0.004) discard_fragment();
        return float4(col, 1.0);
    }

    struct LineOut {
        float4 position [[position]];
        float3 color;
    };

    // a line: a quad from one end to the other, a line's width across on screen, square to the line
    vertex LineOut fractal_line(uint vid [[vertex_id]], uint iid [[instance_id]],
                                const device float* v [[buffer(0)]], constant FractalU& u [[buffer(1)]]) {
        const device float* e = v + iid * 9;
        LineOut o;
        o.color = float3(e[6], e[7], e[8]);
        float4 a = u.vp * float4(e[0], e[1], e[2], 1.0);
        float4 b = u.vp * float4(e[3], e[4], e[5], 1.0);
        if (a.w <= 1e-4 || b.w <= 1e-4) { o.position = float4(2.0, 2.0, 0.5, 1.0); return o; }
        float2 sa = a.xy / a.w * u.extra.xy * 0.5;
        float2 sb = b.xy / b.w * u.extra.xy * 0.5;
        float2 d = sb - sa;
        float len = length(d);
        float2 across = len > 1e-4 ? float2(-d.y, d.x) / len : float2(0.0, 1.0);
        float along = (vid & 1u) ? 1.0 : 0.0;
        float side = (vid & 2u) ? 1.0 : -1.0;
        float4 c = mix(a, b, along);
        float2 offset = across * side * u.extra.w / u.extra.xy;
        o.position = float4(c.xy + offset * c.w, 0.5 * c.w, c.w);
        return o;
    }

    fragment float4 fractal_line_lit(LineOut in [[stage_in]]) { return float4(in.color, 1.0); }
    """
}

/// The MTKView the Fractal draws in: paused until something moves, at the
/// Graphics setting's frame rate (120 on a ProMotion screen at High).
struct FractalMetalView: UIViewRepresentable {
    let host: FractalHost

    final class Coordinator { var renderer: FractalMetalRenderer? }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.colorPixelFormat = .bgra8Unorm
        view.depthStencilPixelFormat = .invalid
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
        context.coordinator.renderer = FractalMetalRenderer(view: view, host: host)
        view.delegate = context.coordinator.renderer
        // without a renderer every move lands at once (FractalHost.go)
        if context.coordinator.renderer != nil { host.metal = view }
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        view.preferredFramesPerSecond = GraphQuality.frameRate
        view.setNeedsDisplay()
    }
}
#endif
