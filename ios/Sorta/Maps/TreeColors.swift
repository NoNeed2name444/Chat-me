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
