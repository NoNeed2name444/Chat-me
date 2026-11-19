import Foundation

// The Tree look's colours (S11), the approved concept's tokens, as Android's
// TreeColors.kt: a sky that is the page, bark, a vine for links and the
// names' ink, by day and by night; each folder's bark leans 30% to its
// NoteTone colour (ideas in no folder 20% to grey) and its leaves are the
// colour itself. A part outside the one in view is drawn in a solid mix toward
// the sky, in eight steps. Foundation only.

/// RGB colours, mixed as the concept mixes them (each channel rounded).
struct TreeColors {
    let dark: Bool
    let sky: Int, bark: Int, vine: Int, ink: Int, halo: Int, accent: Int
    /// `NestNodes.groups` groups by 9 steps: bark and leaves, from the dimmest (0) to full (8).
    let barkRamp: [[Int]]
    let leafRamp: [[Int]]
    /// The 3D tree's (S11, the owner's review of 19 Nov: a believable tree):
    /// its bark a neutral brown, each folder's leaning `woodTint` to its
    /// colour; a top folder's leaves in its colour's hue, between `leafSatLeast`
    /// and `leafSatMost` saturated, as light as the living green `foliage` (the
    /// lead's review of 19 Nov: folders told apart at the whole view), so each
    /// keeps the green's contrast with the sky; the trunk's and loose ideas'
    /// leaves the green itself (`hued`).
    let wood: Int, foliage: Int
    let wood3d: [Int]
    let leaf3d: [Int]
    static let woodTint = 0.2, leafSatLeast = 0.35, leafSatMost = 0.6

    /// How bright the parts outside the one in view stay.
    static let dim = 0.18
    /// NoteTone's seven, in its order, and its grey.
    static let tone = [0x14B8A6, 0x6366F1, 0xFF9F7A, 0xA78BFA, 0xA3E635, 0xF472B6, 0x3B82F6]
    static let grey = 0x8E8E93

    init(dark: Bool) {
        self.dark = dark
        let sky = dark ? 0x281E2A : 0xF3EADB
        let bark = dark ? 0xD3C5B5 : 0x5B4650
        self.sky = sky
        self.bark = bark
        vine = dark ? 0xA4C283 : 0x5E8A3A
        ink = dark ? 0xF3EADB : 0x281E2A
        halo = sky
        // the chosen part's ring: Sorta's accent
        accent = dark ? 0xEE866D : 0xC4503A
        let tones = (0..<NestNodes.groups).map { $0 < NestNodes.tones ? Self.tone[$0] : Self.grey }
        let barks = (0..<NestNodes.groups).map { g -> Int in
            g == NestNodes.trunk ? bark : (g == NestNodes.loose ? Self.mix(bark, Self.grey, 0.2) : Self.mix(bark, tones[g], 0.3))
        }
        func ramp(_ c: Int) -> [Int] { (0..<9).map { s in Self.mix(sky, c, Self.dim + (1 - Self.dim) * Double(s) / 8.0) } }
        barkRamp = barks.map(ramp)
        leafRamp = tones.map(ramp)
        let wood = dark ? 0xB39880 : 0x6E5443, foliage = dark ? 0x8FC26C : 0x4D7A35
        self.wood = wood; self.foliage = foliage
        wood3d = (0..<NestNodes.groups).map { g -> Int in
            g == NestNodes.trunk ? wood : (g == NestNodes.loose ? Self.mix(wood, Self.grey, Self.woodTint) : Self.mix(wood, tones[g], Self.woodTint))
        }
        leaf3d = (0..<NestNodes.groups).map { g in
            g == NestNodes.trunk || g == NestNodes.loose ? foliage : Self.hued(tones[g], like: foliage)
        }
    }

    /// `tone`'s hue, its saturation held between `leafSatLeast` and
    /// `leafSatMost`, at the lightness that gives it `like`'s luma (0.299 R +
    /// 0.587 G + 0.114 B), found by halving 40 times; each channel rounded half
    /// up. Android's TreeColors.hued is the same, step for step.
    static func hued(_ tone: Int, like: Int) -> Int {
        let r = Double((tone >> 16) & 255) / 255, g = Double((tone >> 8) & 255) / 255, b = Double(tone & 255) / 255
        let most = max(r, g, b), least = min(r, g, b), d = most - least
        let l0 = (most + least) / 2
        var h = 0.0, s = 0.0
        if d > 0 {
            s = d / (1 - abs(2 * l0 - 1))
            if most == r { h = 60 * ((g - b) / d) } else if most == g { h = 60 * ((b - r) / d + 2) } else { h = 60 * ((r - g) / d + 4) }
            if h < 0 { h += 360 }
        }
        s = min(max(s, leafSatLeast), leafSatMost)
        let target = luma(Double((like >> 16) & 255), Double((like >> 8) & 255), Double(like & 255))
        var lo = 0.0, hi = 1.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            let v = fromHsl(h, s, mid)
            if luma(v[0], v[1], v[2]) < target { lo = mid } else { hi = mid }
        }
        let v = fromHsl(h, s, (lo + hi) / 2)
        func ch(_ x: Double) -> Int { min(255, max(0, Int(floor(x + 0.5)))) }
        return (ch(v[0]) << 16) | (ch(v[1]) << 8) | ch(v[2])
    }

    private static func luma(_ r: Double, _ g: Double, _ b: Double) -> Double { 0.299 * r + 0.587 * g + 0.114 * b }

    /// Hue `h` (degrees), saturation `s` and lightness `l` as red, green and blue from 0 to 255.
    private static func fromHsl(_ h: Double, _ s: Double, _ l: Double) -> [Double] {
        let c = (1 - abs(2 * l - 1)) * s
        let hp = h / 60
        let x = c * (1 - abs(hp.truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let rgb: (Double, Double, Double)
        switch Int(hp) % 6 {
        case 0: rgb = (c, x, 0)
        case 1: rgb = (x, c, 0)
        case 2: rgb = (0, c, x)
        case 3: rgb = (0, x, c)
        case 4: rgb = (x, 0, c)
        default: rgb = (c, 0, x)
        }
        return [(rgb.0 + m) * 255, (rgb.1 + m) * 255, (rgb.2 + m) * 255]
    }

    /// `a` toward `b` by `t`, each channel rounded half up, as the concept's toHex(Math.round).
    static func mix(_ a: Int, _ b: Int, _ t: Double) -> Int {
        func ch(_ shift: Int) -> Int {
            let x = Double((a >> shift) & 255), y = Double((b >> shift) & 255)
            return min(255, max(0, Int(floor(x + (y - x) * t + 0.5))))
        }
        return (ch(16) << 16) | (ch(8) << 8) | ch(0)
    }
}
