import Foundation

// The Tree look's frame (S11), the approved concept's: the camera and its
// zoom (a spiral similarity, time based), which parts are drawn (the biggest
// on screen first, up to a budget, each fading in from about 4 points; a whole
// subtree off screen is skipped), their names (never overlapping, 8 apart side
// by side and 4 above and below; a bough's name on the bough itself), the
// dimming of all but the part in view, the vines, taps, pinches and Back. In
// the view's own units (points), drawing through a NestSink. Android's
// TreeScene.kt is the same line for line; suite treescene times it and counts
// what it draws. Nothing is allocated per frame: every array is made when the
// tree is. Foundation only.

final class TreeScene {
    let nodes: NestNodes
    let layout: TreeLayout
    let links: NestLinks
    private let n: Int
    private let k = TreeShape.k
    private let par: [Int], cc: [Int], dep: [Int], grp: [Int]
    private let lx: [Double], ly: [Double]

    // ---- what the platform sets
    /// Whether motion is reduced: a zoom is a cut.
    var reduced = false
    private(set) var viewW: Float = 1, viewH: Float = 1
    private(set) var insetTop: Float = 0, insetBottom: Float = 0, insetLeft: Float = 0, insetRight: Float = 0
    /// The least touch target, each way: 44 on iOS, 48 on Android.
    var target: Float = 44
    /// Each name's width at 100 units, upright ([0]) and bold ([1]), measured by the platform.
    var widths: [[Float]]
    var colors = TreeColors(dark: false)
    /// The idea chosen (its ring), or -1.
    var chosen = -1
    /// Show links: only this node's vines, bright; -1 for every vine, faint.
    var linksOf = -1
    /// What the filter and the lenses leave out: a hidden part, its subtree and its vines aren't drawn.
    var hidden: [Bool]

    // ---- the camera and where it is
    let cam = NestCamera()
    /// The part in view: Back leaves it for its parent.
    private(set) var focus = 0
    private var fromFocus = 0
    private var progress = 1.0
    private(set) var animating = false
    private var zMin = 1e-9
    private var zMax = 1e9

    // ---- one frame's state
    private var frame = 0
    private var tc: Float = 1, ts: Float = 0, tox: Float = 0, toy: Float = 0, tz: Float = 1
    private var cr: Float = 1, sr: Float = 0
    private var sx: [Float], sy: [Float], lp: [Float], al: [Float]
    private var stamp: [Int], open: [Bool]
    private var drawn: [Int], inFrame: [Int]
    /// How many parts the last frame drew.
    private(set) var drawnCount = 0
    private var heapNode: [Int], heapKey: [Float], heapSize = 0
    private var cand: [Int], candKey: [Float], candCount = 0
    private var rx: [Int]
    private var used = Array(repeating: 0, count: NestNodes.groups)
    private var hi = 0, hi2 = 0, hu: Float = 1, lhi = 0

    // ---- the names: where each went (a box), or an obstacle (node -1)
    private let maxLabels = 120
    private(set) var labelCount = 0
    private var labX: [Float], labY: [Float], lb0: [Float], lb1: [Float], lb2: [Float], lb3: [Float]
    private var labSize: [Float], labBold: [Bool], labAlign: [Int], labNode: [Int]

    // ---- the zoom in flight
    private var aT0 = 0.0, aDur = 0.0, aLz0 = 0.0, aLz1 = 0.0, aR0 = 0.0, aR1 = 0.0
    private var aC0x = 0.0, aC0y = 0.0, aC1x = 0.0, aC1y = 0.0
    private var aPx = 0.0, aPy = 0.0, aQx = 0.0, aQy = 0.0, aLin = false

    private var sized = false
    private let scratch = NestCamera()

    /// The point along a branch its anchor is at.
    static let anchor = 6
    /// A part shorter than this reads as texture, not as a part: left out, with its parts.
    static let minLp: Float = 4
    static let fadeLp: Float = 10
    /// A part opens its children from this long.
    static let openLp: Float = 9
    /// The most parts drawn in a frame.
    static let budget = 380
    /// The least length a part is named at.
    static let nameLp: Float = 36
    /// Between names: side by side, and above and below.
    static let gapX: Float = 8
    static let gapY: Float = 4
    /// Out along a bough, where boughs spread apart: where its name is tried.
    static let onBough = [5, 4, 6, 3, 7, 2]
    private static let kappa: Float = 0.5522848
    private static let tau = Float(Double.pi * 2)

    init(_ nodes: NestNodes, _ layout: TreeLayout, _ links: NestLinks = .none) {
        self.nodes = nodes; self.layout = layout; self.links = links
        let n = nodes.size
        self.n = n
        par = nodes.parent; cc = nodes.childCount; dep = nodes.depth; grp = nodes.group
        lx = layout.px; ly = layout.py
        let f = Array(repeating: Float(0), count: n), i0 = Array(repeating: 0, count: n)
        widths = [f, f]
        hidden = Array(repeating: false, count: n)
        sx = f; sy = f; lp = f; al = f
        stamp = Array(repeating: -1, count: n); open = Array(repeating: false, count: n)
        drawn = i0; inFrame = Array(repeating: -1, count: n)
        heapNode = i0; heapKey = f; cand = i0; candKey = f; rx = i0
        let m = maxLabels, lf = Array(repeating: Float(0), count: m)
        labX = lf; labY = lf; lb0 = lf; lb1 = lf; lb2 = lf; lb3 = lf; labSize = lf
        labBold = Array(repeating: false, count: m); labAlign = Array(repeating: 0, count: m); labNode = Array(repeating: 0, count: m)
    }

    /// The view's size and insets; the first call frames the part in view, later ones keep it framed while still.
    func setView(_ w: Float, _ h: Float, top: Float, bottom: Float, left: Float = 0, right: Float = 0) {
        let first = !sized
        sized = true
        viewW = max(1, w); viewH = max(1, h); insetTop = top; insetBottom = bottom; insetLeft = left; insetRight = right
        _ = frameOf(0, scratch)
        zMin = scratch.z * 0.35; zMax = scratch.z * 1e5
        if first || !animating { _ = frameOf(focus, scratch); cam.set(scratch) }
    }

    // ---------------------------------------------------------------- the camera

    /// The camera that shows `i`'s whole subtree, upright along its own branch, inside the insets.
    @discardableResult
    func frameOf(_ i: Int, _ out: NestCamera) -> NestCamera {
        let padT = insetTop + 12, padB = insetBottom + 26, padL = insetLeft + 22, padR = insetRight + 22
        let aw = Double(max(40, viewW - padL - padR)), ah = Double(max(40, viewH - padT - padB))
        let a = layout.angle[i]
        var r = -Double.pi / 2 - a
        r = atan2(sin(r), cos(r))
        let du = layout.u1[i] - layout.u0[i], dv = layout.v1[i] - layout.v0[i]
        let z = min(aw / dv, ah / du)
        let uc = (layout.u0[i] + layout.u1[i]) / 2, vc = (layout.v0[i] + layout.v1[i]) / 2
        let ca = cos(a), sa = sin(a)
        let wx = layout.bx[i] + uc * ca - vc * sa, wy = layout.by[i] + uc * sa + vc * ca
        let ddx = Double(padL) + aw / 2 - Double(viewW) / 2, ddy = Double(padT) + ah / 2 - Double(viewH) / 2
        let c = cos(r), s = sin(r)
        out.x = wx - (ddx * c + ddy * s) / z; out.y = wy - (-ddx * s + ddy * c) / z; out.z = z; out.r = r
        return out
    }

    /// Opens `i`: the camera goes to it, eased unless motion is reduced or `instant`.
    func go(_ i: Int, now: Double, instant: Bool = false) {
        fromFocus = instant ? i : focus
        focus = i; progress = 0
        frameOf(i, scratch)
        if instant || reduced {
            cam.x = scratch.x; cam.y = scratch.y; cam.z = scratch.z; cam.r = cam.r + wrap(scratch.r - cam.r)
            animating = false; progress = 1; return
        }
        startAnim(scratch, now)
    }

    /// Back: the part in view's parent, if it isn't the whole tree. Whether it went.
    @discardableResult
    func back(now: Double) -> Bool {
        if focus <= 0 { return false }
        go(par[focus], now: now); return true
    }

    private func wrap(_ a: Double) -> Double { atan2(sin(a), cos(a)) }

    // a spiral similarity: zoom, turn and slide about the one point both views share
    private func startAnim(_ to: NestCamera, _ now: Double) {
        let dr = wrap(to.r - cam.r)
        aC0x = cam.x; aC0y = cam.y; aLz0 = log(cam.z); aR0 = cam.r; aC1x = to.x; aC1y = to.y; aLz1 = log(to.z); aR1 = aR0 + dr
        let a0r = cam.z * cos(aR0), a0i = cam.z * sin(aR0), a1r = to.z * cos(aR1), a1i = to.z * sin(aR1)
        let dRe = a0r - a1r, dIm = a0i - a1i, dd = dRe * dRe + dIm * dIm
        aLin = dd < 1e-6 * (a0r * a0r + a0i * a0i)
        if !aLin {
            let nr = aC0x * a0r - aC0y * a0i - (aC1x * a1r - aC1y * a1i), ni = aC0x * a0i + aC0y * a0r - (aC1x * a1i + aC1y * a1r)
            aPx = (nr * dRe + ni * dIm) / dd; aPy = (ni * dRe - nr * dIm) / dd
            let ux = aPx - aC0x, uy = aPy - aC0y
            aQx = ux * a0r - uy * a0i; aQy = ux * a0i + uy * a0r
        }
        let zoomed: Double = 150 * abs(aLz1 - aLz0) / log(2.0)
        let turned: Double = 160 * abs(dr)
        aDur = Self.clampD(460 + zoomed + turned, 420, 1250)
        aT0 = now; animating = true
    }

    /// Moves a zoom in flight to `now` (ms); its last step lands exactly.
    func step(_ now: Double) {
        if !animating { return }
        var t = (now - aT0) / aDur
        if t >= 1 { t = 1; animating = false }
        if t < 0 { t = 0 }
        let u = t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
        progress = u
        let z = exp(aLz0 + (aLz1 - aLz0) * u), r = aR0 + (aR1 - aR0) * u
        cam.z = z; cam.r = r
        if aLin { cam.x = aC0x + (aC1x - aC0x) * u; cam.y = aC0y + (aC1y - aC0y) * u } else {
            let ar = z * cos(r), ai = z * sin(r), m = ar * ar + ai * ai
            cam.x = aPx - (aQx * ar + aQy * ai) / m; cam.y = aPy - (aQy * ar - aQx * ai) / m
        }
        if !animating { cam.x = aC1x; cam.y = aC1y; cam.z = exp(aLz1); cam.r = aR1; progress = 1 }
    }

    /// A pinch about `px`, `py` by `f`; it stops a zoom in flight.
    func zoomAbout(_ px: Float, _ py: Float, _ f: Float) {
        animating = false; progress = 1
        let c = cos(cam.r), s = sin(cam.r), dx = Double(px) - Double(viewW) / 2, dy = Double(py) - Double(viewH) / 2
        let wx = cam.x + (dx * c + dy * s) / cam.z, wy = cam.y + (-dx * s + dy * c) / cam.z
        cam.z = Self.clampD(cam.z * Double(f), zMin, zMax)
        cam.x = wx - (dx * c + dy * s) / cam.z; cam.y = wy - (-dx * s + dy * c) / cam.z
    }

    func panBy(_ dx: Float, _ dy: Float) {
        animating = false; progress = 1
        let c = cos(cam.r), s = sin(cam.r)
        cam.x -= (Double(dx) * c + Double(dy) * s) / cam.z; cam.y -= (-Double(dx) * s + Double(dy) * c) / cam.z
    }

    /// After a pinch or a drag: the deepest part that fills the view becomes the one Back leaves from.
    func settle() {
        let c = cos(cam.r), s = sin(cam.r)
        let padT = insetTop + 12, padB = insetBottom + 26, padL = insetLeft + 22, padR = insetRight + 22
        let midX: Float = padL + (viewW - padL - padR) / 2
        let midY: Float = padT + (viewH - padT - padB) / 2
        let dx: Double = Double(midX) - Double(viewW) / 2
        let dy: Double = Double(midY) - Double(viewH) / 2
        let wx = cam.x + (dx * c + dy * s) / cam.z, wy = cam.y + (-dx * s + dy * c) / cam.z
        var i = 0
        while true {
            var best = -1, bd = 1e18
            for q in 0..<cc[i] {
                let ch = nodes.child(i, q)
                if cc[ch] == 0 { continue }
                frameOf(ch, scratch)
                if scratch.z > cam.z * 1.3 { continue }
                let ddx = wx - layout.bcx[ch], ddy = wy - layout.bcy[ch], dd = ddx * ddx + ddy * ddy
                if dd > layout.brad[ch] * layout.brad[ch] { continue }
                if dd < bd { bd = dd; best = ch }
            }
            if best < 0 { break }
            i = best
        }
        focus = i; fromFocus = i; progress = 1
    }

    /// A part tapped or activated: a part with parts of its own fills the
    /// screen; an idea opens the part it grows on. Where it went, or -1.
    @discardableResult
    func activate(_ i: Int, now: Double) -> Int {
        if animating || i < 0 { return -1 }
        let t = cc[i] > 0 ? (i != focus ? i : -1) : (par[i] != focus ? par[i] : -1)
        if t < 0 { return -1 }
        go(t, now: now); return t
    }

    // ---------------------------------------------------------------- one frame

    private func xf() {
        tz = Float(cam.z); cr = Float(cos(cam.r)); sr = Float(sin(cam.r))
        tc = cr * tz; ts = sr * tz
        tox = Float(Double(viewW) / 2 - (cam.x * cam.z * cos(cam.r) - cam.y * cam.z * sin(cam.r)))
        toy = Float(Double(viewH) / 2 - (cam.x * cam.z * sin(cam.r) + cam.y * cam.z * cos(cam.r)))
    }
    @inline(__always) private func scrX(_ x: Double, _ y: Double) -> Float { Float(x * Double(tc) - y * Double(ts)) + tox }
    @inline(__always) private func scrY(_ x: Double, _ y: Double) -> Float { Float(x * Double(ts) + y * Double(tc)) + toy }

    private func inside(_ i: Int) -> Bool { lhi <= 0 || nodes.within(lhi, i) }
    /// How bright `i` is: the part in view full, the rest dimmed, crossing over while the camera moves.
    func dimOf(_ i: Int) -> Float {
        if hi <= 0 && hi2 <= 0 { return 1 }
        let d = Float(TreeColors.dim)
        let b: Float = hi <= 0 || nodes.within(hi, i) ? 1 : d
        if hu >= 1 { return b }
        let a: Float = hi2 <= 0 || nodes.within(hi2, i) ? 1 : d
        return a + (b - a) * hu
    }
    // names: the part in view and two levels below it; deeper names wait for the next zoom
    private func nameOK(_ i: Int, _ minLp: Float) -> Bool {
        if i == 0 || al[i] < 0.6 || !inside(i) { return false }
        let rel = dep[i] - (lhi > 0 ? dep[lhi] : 0)
        return rel <= 1 ? lp[i] >= minLp : (rel == 2 ? lp[i] >= minLp * 1.5 : false)
    }
    private func res(_ i0: Int) -> Int { var i = i0; while stamp[i] != frame { i = par[i] }; return i }
    private func offscreen(_ x: Float, _ y: Float) -> Bool { x < -20 || x > viewW + 20 || y < -20 || y > viewH + 20 }

    private func hpush(_ i: Int, _ key: Float) {
        var q = heapSize; heapSize += 1
        while q > 0 { let p = (q - 1) >> 1; if heapKey[p] >= key { break }; heapNode[q] = heapNode[p]; heapKey[q] = heapKey[p]; q = p }
        heapNode[q] = i; heapKey[q] = key
    }
    private func hpop() -> Int {
        let top = heapNode[0]; heapSize -= 1
        let last = heapNode[heapSize], lk = heapKey[heapSize]
        var q = 0
        while true {
            var c = 2 * q + 1
            if c >= heapSize { break }
            if c + 1 < heapSize && heapKey[c + 1] > heapKey[c] { c += 1 }
            if heapKey[c] <= lk { break }
            heapNode[q] = heapNode[c]; heapKey[q] = heapKey[c]; q = c
        }
        if heapSize > 0 { heapNode[q] = last; heapKey[q] = lk }
        return top
    }
    // the part's anchor on screen, and its length there, or -1 when its whole subtree is off screen
    private func stampB(_ i: Int) -> Float {
        let o = i * k + Self.anchor
        sx[i] = scrX(lx[o], ly[o]); sy[i] = scrY(lx[o], ly[o]); stamp[i] = frame; open[i] = false
        let x = scrX(layout.bcx[i], layout.bcy[i]), y = scrY(layout.bcx[i], layout.bcy[i]), r = Float(layout.brad[i] * Double(tz))
        if x + r < -30 || x - r > viewW + 30 || y + r < -30 || y - r > viewH + 30 { return -1 }
        lp[i] = Float(layout.length[i] * Double(tz))
        return lp[i]
    }
    // the biggest parts on screen first, up to a fixed budget, so a deep tree costs no more than a shallow one
    private func visit() {
        heapSize = 0
        if stampB(0) >= Self.minLp { hpush(0, lp[0]) }
        let fade0 = Float(Self.budget) * 0.75
        while heapSize > 0 && drawnCount < Self.budget {
            let i = hpop(), l = lp[i]
            al[i] = Self.sm(Self.minLp, Self.fadeLp, l) * (Float(drawnCount) > fade0 ? 1 - Self.sm(fade0, Float(Self.budget), Float(drawnCount)) : 1)
            drawn[drawnCount] = i; drawnCount += 1; inFrame[i] = frame
            let count = cc[i]
            if count > 0 && l > Self.openLp {
                open[i] = true
                for q in 0..<count { let c = nodes.child(i, q); if !hidden[c] && stampB(c) >= Self.minLp { hpush(c, lp[c]) } }
            }
        }
    }

    /// Works out one frame at `now` (ms) and draws it into `sink`: the zoom
    /// moved on, the parts chosen, the names placed, the sky left to the
    /// caller. With `names` false, no names.
    func draw(_ sink: NestSink?, now: Double, names: Bool = true) {
        step(now)
        frame += 1; drawnCount = 0; labelCount = 0
        hi = focus; hi2 = fromFocus; hu = Float(progress); lhi = hu > 0.5 ? hi : hi2
        xf()
        visit()
        prepRx()
        if names { labels() }
        guard let sink else { return }
        vines(sink)
        for pass in 0...1 {
            fillParts(sink, pass)
            thinBatch(sink, pass)
            leaves(sink, pass)
        }
        let ch = chosen
        if ch >= 0 && ch < n && stamp[ch] == frame { ring(sink, sx[ch], sy[ch], 10) }
        if names {
            for q in 0..<labelCount {
                let node = labNode[q]
                if node < 0 { continue }
                sink.text(node, labX[q], labY[q], labSize[q], labBold[q], labAlign[q], colors.ink, colors.halo, 3.2)
            }
        }
    }

    // ---------------------------------------------------------------- the names

    private func fits(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) -> Bool {
        if x0 < 4 || x1 > viewW - 4 || y0 < insetTop + 2 || y1 > viewH - insetBottom - 4 { return false }
        for q in 0..<labelCount {   // 8 between names side by side and 4 above and below
            let gx: Float = labNode[q] < 0 ? 1 : Self.gapX, gy: Float = labNode[q] < 0 ? 1 : Self.gapY
            if x0 < lb2[q] + gx && x1 > lb0[q] - gx && y0 < lb3[q] + gy && y1 > lb1[q] - gy { return false }
        }
        return true
    }
    @discardableResult
    private func put(_ i: Int, _ x: Float, _ y: Float, _ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ size: Float, _ bold: Bool, _ align: Int) -> Bool {
        if labelCount >= maxLabels || !fits(x0, y0, x1, y1) { return false }
        let q = labelCount; labelCount += 1
        labX[q] = x; labY[q] = y; lb0[q] = x0; lb1[q] = y0; lb2[q] = x1; lb3[q] = y1; labSize[q] = size; labBold[q] = bold; labAlign[q] = align; labNode[q] = i
        return true
    }
    private func sortCand() {
        if candCount < 2 { return }
        for a in 1..<candCount {
            let ki = cand[a], kv = candKey[a]; var b = a - 1
            while b >= 0 && candKey[b] < kv { cand[b + 1] = cand[b]; candKey[b + 1] = candKey[b]; b -= 1 }
            cand[b + 1] = ki; candKey[b + 1] = kv
        }
    }
    // a bough's name sits on the bough itself, so it never reads as a neighbour's; a twig's at its leaf
    private func labels() {
        candCount = 0
        for q in 0..<drawnCount {
            let i = drawn[q]
            if !nameOK(i, Self.nameLp) { continue }
            cand[candCount] = i; candKey[candCount] = lp[i] - Float(dep[i]) * 1000; candCount += 1
        }
        sortCand()
        var c = 0
        while c < candCount && labelCount < maxLabels {
            let nd = cand[c]; c += 1
            let l = lp[nd], isOpen = open[nd] && cc[nd] > 0
            let size = Self.clampF(floor(10.5 + l * 0.02 + 0.5), 11, 18)
            let w = widths[isOpen ? 1 : 0][nd] * size / 100, h = size * 1.2
            let o = nd * k
            let bX = scrX(lx[o], ly[o]), tX = scrX(lx[o + k - 1], ly[o + k - 1])
            if isOpen {
                for t in Self.onBough {
                    let cX = scrX(lx[o + t], ly[o + t]), cY = scrY(lx[o + t], ly[o + t])
                    if put(nd, cX, cY, cX - w / 2, cY - h / 2, cX + w / 2, cY + h / 2, size, true, 2) { break }
                }
                continue
            }
            let j = k - 1
            var aX = scrX(lx[o + j], ly[o + j]), aY = scrY(lx[o + j], ly[o + j])
            if cc[nd] == 0 {
                let tY = scrY(lx[o + k - 1], ly[o + k - 1]), pX = scrX(lx[o + k - 2], ly[o + k - 2]), pY = scrY(lx[o + k - 2], ly[o + k - 2])
                let ang = atan2(tY - pY, tX - pX), ll = Self.clampF(Float(layout.length[nd] * TreeShape.leaf * Double(tz)), 2.2, 30)
                aX += cos(ang) * ll; aY += sin(ang) * ll
            }
            let gap: Float = 3 + 5
            if tX >= bX {
                if !put(nd, aX + gap, aY, aX + gap, aY - h / 2, aX + gap + w, aY + h / 2, size, false, 0) {
                    put(nd, aX - gap, aY, aX - gap - w, aY - h / 2, aX - gap, aY + h / 2, size, false, 1)
                }
            } else {
                if !put(nd, aX - gap, aY, aX - gap - w, aY - h / 2, aX - gap, aY + h / 2, size, false, 1) {
                    put(nd, aX + gap, aY, aX + gap, aY - h / 2, aX + gap + w, aY + h / 2, size, false, 0)
                }
            }
        }
    }

    /// The `q`th name's node and box (left, top, right, bottom), for the parts VoiceOver reaches.
    func labelNode(_ q: Int) -> Int { labNode[q] }
    func labelBox(_ q: Int) -> (Float, Float, Float, Float) { (lb0[q], lb1[q], lb2[q], lb3[q]) }
    func labelSize(_ q: Int) -> Float { labSize[q] }
    /// Whether the `q`th name is a bough's, bold and on the bough.
    func labelBold(_ q: Int) -> Bool { labBold[q] }
    /// Point `j` of `i`'s branch on screen, as the last frame placed it.
    func pointX(_ i: Int, _ j: Int) -> Float { scrX(lx[i * k + j], ly[i * k + j]) }
    func pointY(_ i: Int, _ j: Int) -> Float { scrY(lx[i * k + j], ly[i * k + j]) }
    /// Whether `i` was drawn in the last frame, and where its anchor was.
    func isDrawn(_ i: Int) -> Bool { i >= 0 && i < n && inFrame[i] == frame }
    func anchorX(_ i: Int) -> Float { sx[i] }
    func anchorY(_ i: Int) -> Float { sy[i] }

    // ---------------------------------------------------------------- touch

    /// The part at `px`, `py`: a name answers anywhere in a box `target` each
    /// way at least, the nearest winning; then the nearest branch within reach.
    func hitTest(_ px: Float, _ py: Float) -> Int {
        var bk = -1, bd: Float = 1e9
        let half = target / 2
        for q in 0..<labelCount {
            if labNode[q] < 0 { continue }
            let hx = max((lb2[q] - lb0[q]) / 2 + 4, half), hy = max((lb3[q] - lb1[q]) / 2 + 6, half)
            let ux = abs(px - (lb0[q] + lb2[q]) / 2) / hx, uy = abs(py - (lb1[q] + lb3[q]) / 2) / hy
            let dd = ux > uy ? ux : uy
            if dd <= 1 && dd < bd { bd = dd; bk = q }
        }
        if bk >= 0 { return labNode[bk] }
        var best = -1, bs: Float = 1e9
        for m in 0..<drawnCount {
            let i = drawn[m]
            if al[i] < 0.3 { continue }
            let o = i * k; var d: Float = 1e9
            for j in 0..<(k - 1) {
                let x0 = scrX(lx[o + j], ly[o + j]), y0 = scrY(lx[o + j], ly[o + j])
                let x1 = scrX(lx[o + j + 1], ly[o + j + 1]), y1 = scrY(lx[o + j + 1], ly[o + j + 1])
                let vx = x1 - x0, vy = y1 - y0, wl = vx * vx + vy * vy
                let t = wl > 0 ? Self.clampF(((px - x0) * vx + (py - y0) * vy) / wl, 0, 1) : 0
                let e = hypot(px - x0 - vx * t, py - y0 - vy * t)
                if e < d { d = e }
            }
            if cc[i] == 0 {   // a leaf answers on itself too
                let tip = layout.leafTip(i)
                let e = hypot(px - scrX(tip.0, tip.1), py - scrY(tip.0, tip.1))
                if e < d { d = e }
            }
            let thr = max(Float(layout.width[i] * Double(tz) * 0.5) + 6, half)
            if d > thr { continue }
            let s = d / thr - Float(dep[i]) * 0.02
            if s < bs { bs = s; best = i }
        }
        return best
    }

    // ---------------------------------------------------------------- drawing

    private func polyline(_ sink: NestSink, _ o: Int) {
        for j in 0..<k {
            let x = scrX(lx[o + j], ly[o + j]), y = scrY(lx[o + j], ly[o + j])
            if j == 0 { sink.moveTo(x, y) } else { sink.lineTo(x, y) }
        }
    }
    // a part's outline, from hw0 at its base down to hw0 * (1 - tp) at its tip
    private func taperPath(_ sink: NestSink, _ i: Int, _ hw0: Float, _ tp: Float) {
        let o = i * k
        for j in 0..<k {
            let p = o + j, x = scrX(lx[p], ly[p]), y = scrY(lx[p], ly[p])
            let nX = Float(layout.nx[p] * Double(cr) - layout.ny[p] * Double(sr)), nY = Float(layout.nx[p] * Double(sr) + layout.ny[p] * Double(cr))
            let hw = hw0 * (1 - tp * Float(j) / Float(k - 1))
            if j == 0 { sink.moveTo(x + nX * hw, y + nY * hw) } else { sink.lineTo(x + nX * hw, y + nY * hw) }
        }
        for j in stride(from: k - 1, through: 0, by: -1) {
            let p = o + j, x = scrX(lx[p], ly[p]), y = scrY(lx[p], ly[p])
            let nX = Float(layout.nx[p] * Double(cr) - layout.ny[p] * Double(sr)), nY = Float(layout.nx[p] * Double(sr) + layout.ny[p] * Double(cr))
            let hw = hw0 * (1 - tp * Float(j) / Float(k - 1))
            sink.lineTo(x - nX * hw, y - nY * hw)
        }
        sink.close()
    }
    private func ellipse(_ sink: NestSink, _ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ a: Float) {
        let ca = cos(a), sa = sin(a), kx = rx * Self.kappa, ky = ry * Self.kappa
        // the four quarter arcs, in the ellipse's own axes turned by a
        @inline(__always) func X(_ u: Float, _ v: Float) -> Float { cx + u * ca - v * sa }
        @inline(__always) func Y(_ u: Float, _ v: Float) -> Float { cy + u * sa + v * ca }
        sink.moveTo(X(rx, 0), Y(rx, 0))
        sink.cubicTo(X(rx, ky), Y(rx, ky), X(kx, ry), Y(kx, ry), X(0, ry), Y(0, ry))
        sink.cubicTo(X(-kx, ry), Y(-kx, ry), X(-rx, ky), Y(-rx, ky), X(-rx, 0), Y(-rx, 0))
        sink.cubicTo(X(-rx, -ky), Y(-rx, -ky), X(-kx, -ry), Y(-kx, -ry), X(0, -ry), Y(0, -ry))
        sink.cubicTo(X(kx, -ry), Y(kx, -ry), X(rx, -ky), Y(rx, -ky), X(rx, 0), Y(rx, 0))
        sink.close()
    }

    // dimmed parts are drawn first, in a solid mix toward the sky, so overlaps leave no seams
    private func rix(_ i: Int) -> Int {
        let d = dimOf(i)
        if d >= 0.999 { return 8 }
        let dim = Float(TreeColors.dim)
        let s = Int(floor((d - dim) / (1 - dim) * 8 + 0.5))
        return s < 0 ? 0 : (s > 7 ? 7 : s)
    }
    private func prepRx() {
        for g in 0..<NestNodes.groups { used[g] = 0 }
        for q in 0..<drawnCount { let i = drawn[q], s = rix(i); rx[i] = s; used[grp[i]] |= 1 << s }
    }
    private func inPass(_ i: Int, _ pass: Int) -> Bool { (rx[i] == 8) == (pass == 1) }
    private func noDim() -> Bool { hi <= 0 && hi2 <= 0 }

    // every part of one colour goes into one path and one fill; parts still fading in go one by one
    private func fillParts(_ sink: NestSink, _ pass: Int) {
        if pass == 0 && noDim() { return }
        let lo = pass == 1 ? 8 : 0, up = pass == 1 ? 8 : 7
        for g in 0..<NestNodes.groups {
            for s in lo...up {
                if used[g] & (1 << s) == 0 { continue }
                var any = false
                for q in 0..<drawnCount {
                    let i = drawn[q]
                    if grp[i] != g || rx[i] != s || al[i] < 0.98 { continue }
                    let hw = Float(layout.width[i] * Double(tz) * 0.5); if hw < 0.6 { continue }
                    taperPath(sink, i, hw, 0.45); any = true
                }
                if !any { continue }
                sink.fill(colors.barkRamp[g][s], 1)
                any = false
                for q in 0..<drawnCount {
                    let i = drawn[q]
                    if grp[i] != g || rx[i] != s || al[i] < 0.98 { continue }
                    let hw = Float(layout.width[i] * Double(tz) * 0.5); if hw < 2.5 { continue }
                    let o = i * k
                    ellipse(sink, scrX(lx[o], ly[o]), scrY(lx[o], ly[o]), hw, hw, 0); any = true
                }
                if any { sink.fill(colors.barkRamp[g][s], 1) }
            }
        }
        for q in 0..<drawnCount {
            let i = drawn[q]
            if al[i] >= 0.98 || !inPass(i, pass) { continue }
            let hw = Float(layout.width[i] * Double(tz) * 0.5); if hw < 0.6 { continue }
            taperPath(sink, i, hw, 0.45)
            sink.fill(colors.barkRamp[grp[i]][rx[i]], al[i])
        }
    }
    // thin parts as hairlines, one path per colour
    private func thinBatch(_ sink: NestSink, _ pass: Int) {
        if pass == 0 && noDim() { return }
        for g in 0..<NestNodes.groups {
            var any = false
            for q in 0..<drawnCount {
                let i = drawn[q]
                if grp[i] != g || layout.width[i] * Double(tz) >= 1.2 || !inPass(i, pass) { continue }
                polyline(sink, i * k); any = true
            }
            if any { sink.stroke(colors.barkRamp[g][pass == 1 ? 8 : 0], 1, 0.85, false) }
        }
    }
    // leaves at the tips; a closed twig wears a small round of leaves
    private var lgx: Float = 0, lgy: Float = 0, lga: Float = 0, lgl: Float = 0
    private func leafGeom(_ i: Int) {
        let o = i * k + k - 1
        lgx = scrX(lx[o], ly[o]); lgy = scrY(lx[o], ly[o])
        lga = atan2(lgy - scrY(lx[o - 1], ly[o - 1]), lgx - scrX(lx[o - 1], ly[o - 1]))
        lgl = Self.clampF(Float(layout.length[i] * TreeShape.leaf * Double(tz)), 2.2, 30)
    }
    private func leafPath(_ sink: NestSink, _ i: Int, _ blob: Bool) {
        leafGeom(i)
        if !blob {
            let ca = cos(lga), sa = sin(lga)
            ellipse(sink, lgx + ca * lgl * 0.5, lgy + sa * lgl * 0.5, lgl * 0.5, lgl * 0.24, lga)
        } else {
            let r = Self.clampF(Float(layout.length[i] * 0.42 * Double(tz)), 1.6, 12)
            ellipse(sink, lgx, lgy, r, r, 0)
        }
    }
    private func leaves(_ sink: NestSink, _ pass: Int) {
        if pass == 0 && noDim() { return }
        let lo = pass == 1 ? 8 : 0, up = pass == 1 ? 8 : 7
        for b in 0...1 {
            let blob = b == 1
            for g in 0..<NestNodes.groups {
                for s in lo...up {
                    if used[g] & (1 << s) == 0 { continue }
                    var any = false
                    for q in 0..<drawnCount {
                        let i = drawn[q]
                        if grp[i] != g || rx[i] != s || al[i] < 0.98 { continue }
                        if blob ? !(cc[i] > 0 && !open[i]) : cc[i] > 0 { continue }
                        leafPath(sink, i, blob); any = true
                    }
                    if any { sink.fill(colors.leafRamp[g][s], blob ? 0.6 : 1) }
                }
            }
        }
        for q in 0..<drawnCount {
            let i = drawn[q]
            if al[i] >= 0.98 || !inPass(i, pass) || (cc[i] > 0 && open[i]) { continue }
            leafPath(sink, i, cc[i] > 0)
            sink.fill(colors.leafRamp[grp[i]][rx[i]], al[i] * (cc[i] > 0 ? 0.6 : 1))
        }
        if pass == 1 {   // a midrib on the big leaves
            var any = false
            for q in 0..<drawnCount {
                let i = drawn[q]
                if cc[i] > 0 || rx[i] != 8 || al[i] < 0.98 { continue }
                leafGeom(i); if lgl <= 12 { continue }
                let ca = cos(lga), sa = sin(lga)
                sink.moveTo(lgx + ca * lgl * 0.12, lgy + sa * lgl * 0.12); sink.lineTo(lgx + ca * lgl * 0.88, lgy + sa * lgl * 0.88); any = true
            }
            if any { sink.stroke(colors.halo, 1, 0.55, true) }
        }
    }

    // a link is a faint vine, drawn only when both its ends are in the part in
    // view; Show links draws only the chosen idea's, brighter
    private func linkLive(_ a: Int, _ b: Int) -> Bool {
        a != b && !nodes.within(a, b) && !nodes.within(b, a) && !(offscreen(sx[a], sy[a]) && offscreen(sx[b], sy[b]))
    }
    private func vines(_ sink: NestSink) {
        let only = linksOf
        var any = false
        for l in 0..<links.size {
            if only >= 0 && links.a[l] != only && links.b[l] != only { continue }
            if hidden[links.a[l]] || hidden[links.b[l]] { continue }
            let a = res(links.a[l]), b = res(links.b[l])
            if !linkLive(a, b) || dimOf(a) <= 0.6 || dimOf(b) <= 0.6 { continue }
            let x0 = sx[a], y0 = sy[a], x1 = sx[b], y1 = sy[b], dx = x1 - x0, dy = y1 - y0
            let len = (dx * dx + dy * dy).squareRoot(); if len < 16 { continue }
            let nx = -dy / len, ny = dx / len, bow = len * 0.16 * (l & 1 == 1 ? 1 : -1)
            let steps = min(28, 6 + Int(len / 10))
            for s in 0...steps {
                let u = Float(s) / Float(steps)
                let wig = sin(u * len / 22 * Self.tau) * 1.9 * Self.sm(0, 0.08, u) * Self.sm(1, 0.92, u)
                let off = bow * 4 * u * (1 - u) + wig
                let x = x0 + dx * u + nx * off, y = y0 + dy * u + ny * off
                if s == 0 { sink.moveTo(x, y) } else { sink.lineTo(x, y) }
            }
            any = true
        }
        if any { sink.stroke(colors.vine, only >= 0 ? 2 : 1.3, only >= 0 ? 0.9 : 0.5, false) }
    }
    private func ring(_ sink: NestSink, _ x: Float, _ y: Float, _ r: Float) {
        ellipse(sink, x, y, r, r, 0)
        sink.stroke(colors.accent, 2, 1, true)
    }

    static func sm(_ a: Float, _ b: Float, _ x0: Float) -> Float {
        var x = (x0 - a) / (b - a)
        x = x < 0 ? 0 : (x > 1 ? 1 : x)
        return x * x * (3 - 2 * x)
    }
    static func clampF(_ x: Float, _ a: Float, _ b: Float) -> Float { x < a ? a : (x > b ? b : x) }
    static func clampD(_ x: Double, _ a: Double, _ b: Double) -> Double { x < a ? a : (x > b ? b : x) }
}
