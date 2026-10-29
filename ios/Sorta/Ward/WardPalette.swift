import Foundation

/// Sorta's palette (docs/tasks/S02-look-kit.md, "Sorta's palette"; the
/// default, which the owner can change): one table of light and dark values
/// that every colour in the app is drawn from, and the contrast each text
/// colour is promised against the surface it sits on. Copied from
/// Stethoscore's Ward kit (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
/// Ward/WardPalette.swift) and recoloured.
///
/// The hues are the icon's only: bone, aubergine, coral, and greys mixed
/// between bone and aubergine (in OKLab), with white and black as relief
/// lights. Soft UI in one tone per mode, as Stethoscore had it: cards
/// (.surface), sheets, bars and panels (.raised) and wells (.well) are the
/// page colour itself (.background), told from the ground only by their
/// relief (WardRelief), their edge and their ring.
///
/// Words on coral (docs/scoping.md section 5): fills that carry words are
/// #C4503A by day under white, and the icon's #EE866D by night under
/// aubergine; icons and edges keep the icon's corals (`accent`), and coral
/// words on the page are darker by day (`primaryInk`). A danger action also
/// says what it does in words, so colour never carries it alone.
///
/// Foundation only, so the promises are tested on Linux (WardPaletteTests);
/// WardTokens turns the table into SwiftUI colours.
enum WardToken: String, CaseIterable {
    /// The matte base every screen sits on: bone by day, aubergine by night
    case background
    /// Cards and rows: the base's own colour, raised by relief alone
    case surface
    /// Sheets, floating bars, popovers: the base's own colour too
    case raised
    /// Wells pressed into the base: fields, tracks, a chosen segment; the
    /// base's own colour too, shown as a well by its inset shade alone
    case well
    /// The primary as a fill that carries words: a switch that is on, a
    /// chosen segment, the app's tint
    case primary
    /// Coral words and glyphs on the page
    case primaryInk
    /// Words on a primary fill
    case onPrimary
    /// The icon's coral for icons and edges that carry no words (3:1)
    case accent
    /// A destructive action or an error: the coral darker by day and
    /// lighter by night
    case danger
    /// Text: each mode's ink is the other mode's page
    case ink
    /// Secondary text and small caps
    case inkSecondary
    /// A groove: separators and the empty part of a track
    case hairline
}

enum WardPalette {
    /// (light, dark) as 0xRRGGBB: the table of S02, exactly.
    static let table: [WardToken: (light: UInt32, dark: UInt32)] = [
        .background:   (0xF3EADB, 0x281E2A),
        .surface:      (0xF3EADB, 0x281E2A),
        .raised:       (0xF3EADB, 0x281E2A),
        .well:         (0xF3EADB, 0x281E2A),
        .primary:      (0xC4503A, 0xEE866D),
        .primaryInk:   (0xB2402A, 0xEE866D),
        .onPrimary:    (0xFFFFFF, 0x281E2A),
        .accent:       (0xC4503A, 0xEE866D),
        .danger:       (0x932109, 0xFFAA90),
        .ink:          (0x281E2A, 0xF3EADB),
        .inkSecondary: (0x5E545A, 0xB0A7A1),
        .hairline:     (0xDCD3C7, 0x392F39),
    ]

    /// Day's relief shade (WardRelief.lightShade): a grey from bone toward
    /// aubergine, 0.3 of the way in OKLab, never past 0.7. Stethoscore's
    /// #A3B1C6 was blue, a hue the icon does not have. Night's shade is
    /// black, and both modes' highlight white.
    static let dayShade: UInt32 = 0xB0A7A1
    /// How far from bone toward aubergine `dayShade` was mixed, in OKLab.
    static let dayShadeMix = 0.3

    /// The tiers from deepest to highest: a well, the base, a card, a sheet.
    /// All four are one colour (the owner, 22 Oct), told apart by relief.
    static let tiers: [WardToken] = [.well, .background, .surface, .raised]

    static func hex(_ token: WardToken, dark: Bool) -> UInt32 {
        let pair = table[token]!
        return dark ? pair.dark : pair.light
    }

    /// 0...1 components.
    static func rgb(_ hex: UInt32) -> (r: Double, g: Double, b: Double) {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    /// A colour part way from `a` to `b` (t 0 to 1), mixed in linear light
    /// so the middle of a light-to-dark shift is not muddy: what one frame
    /// of the appearance glide shows (WardGlide).
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        let t = min(1, max(0, t))
        func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        func gamma(_ c: Double) -> Double { c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1 / 2.4) - 0.055 }
        let (r0, g0, b0) = rgb(a), (r1, g1, b1) = rgb(b)
        func channel(_ x: Double, _ y: Double) -> UInt32 {
            let v = gamma(linear(x) + (linear(y) - linear(x)) * t)
            return UInt32((min(1, max(0, v)) * 255).rounded())
        }
        return channel(r0, r1) << 16 | channel(g0, g1) << 8 | channel(b0, b1)
    }

    /// WCAG 2 relative luminance.
    static func luminance(_ hex: UInt32) -> Double {
        func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let (r, g, b) = rgb(hex)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /// WCAG 2 contrast ratio, 1...21.
    static func contrast(_ a: UInt32, _ b: UInt32) -> Double {
        let (x, y) = (luminance(a), luminance(b))
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /// Each text colour, what it sits on, and the ratio it is promised:
    /// 4.5 for words, 3 for icons and edges that carry none (`accent`).
    static let promises: [(text: WardToken, on: WardToken, ratio: Double)] = [
        (.ink, .surface, 4.5), (.ink, .background, 4.5),
        (.ink, .raised, 4.5), (.ink, .well, 4.5),
        (.inkSecondary, .raised, 4.5), (.inkSecondary, .well, 4.5),
        (.primaryInk, .raised, 4.5), (.primaryInk, .well, 4.5),
        (.inkSecondary, .surface, 4.5), (.inkSecondary, .background, 4.5),
        (.primaryInk, .surface, 4.5), (.primaryInk, .background, 4.5),
        (.onPrimary, .primary, 4.5),
        (.danger, .surface, 4.5),
        // a chip or a pill is pressed into a well (WardChip, WardPill), so
        // every ink they use is promised on it too
        (.danger, .well, 4.5),
        // soft UI tints labels and glyphs, not fills, so the accents sit on
        // the ground too
        (.danger, .background, 4.5),
        // the icon's coral, for icons and edges without words, on every tier
        (.accent, .well, 3), (.accent, .background, 3),
        (.accent, .surface, 3), (.accent, .raised, 3),
    ]
}

/// The one spacing scale: every gap, padding and radius in the soft UI is
/// a step of it (the owner, 14 Oct: "the iOS design is a bit cramped").
/// WardSpace and WardRadius (WardTokens) are drawn from it.
enum WardScale {
    static let steps: [Double] = [4, 8, 12, 16, 24, 32]
    static let xs = 4.0, s = 8.0, m = 12.0, l = 16.0, xl = 24.0, xxl = 32.0
    /// Extra room between the lines of a paragraph, on top of the text
    /// style's own leading.
    static let leading = 4.0

    static func onScale(_ value: Double) -> Bool { steps.contains(value) }
}

/// Light to dark and back: the colours move to the other palette over
/// about a third of a second, a colour shift and never a fade (WardGlide in
/// WardLook holds the shift, WardLookDriver in WardTokens runs it; under
/// Reduce Motion they switch at once).
enum WardSchemeGlide {
    static let duration = 0.35

    /// One frame of the shift, `t` of the way from light to dark.
    static func frame(_ token: WardToken, t: Double) -> UInt32 {
        WardPalette.mix(WardPalette.hex(token, dark: false), WardPalette.hex(token, dark: true), t)
    }
}
