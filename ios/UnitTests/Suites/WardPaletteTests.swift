// Sorta's palette: every value of the table in docs/tasks/S02-look-kit.md
// pinned, every text colour keeps its promised contrast on the surface it
// sits on, in light and in dark, and the app's AccentColor and Page assets
// are the primary and the bases from the same table. Copied from Stethoscore
// (reference/stethoscore-aec2af1c/ios/RedPen/Tests/WardPaletteTests.swift)
// and re-pinned to Sorta's colours. Nothing is excused: Sorta has no list of
// accepted cases, so every check holds to its own bar.
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

// MARK: - The table, pinned

// S02's table, light and dark, exactly. A change to a colour changes the
// table, these pins and the packet's table together.
let pinned: [WardToken: (light: UInt32, dark: UInt32)] = [
    .background: (0xF3EADB, 0x281E2A), .surface: (0xF3EADB, 0x281E2A),
    .raised: (0xF3EADB, 0x281E2A), .well: (0xF3EADB, 0x281E2A),
    .primary: (0xC4503A, 0xEE866D), .onPrimary: (0xFFFFFF, 0x281E2A),
    .accent: (0xC4503A, 0xEE866D), .primaryInk: (0xB2402A, 0xEE866D),
    .danger: (0x932109, 0xFFAA90), .ink: (0x281E2A, 0xF3EADB),
    .inkSecondary: (0x5E545A, 0xB0A7A1), .hairline: (0xDCD3C7, 0x392F39),
]
check("the palette has exactly the table's twelve tokens",
      WardToken.allCases.count == 12 && Set(WardToken.allCases) == Set(pinned.keys), "\(WardToken.allCases)")
for token in WardToken.allCases {
    for dark in [false, true] {
        let want = dark ? pinned[token]?.dark : pinned[token]?.light
        check("\(dark ? "dark" : "light"): \(token) is the table's " + String(format: "#%06X", want ?? 0),
              WardPalette.hex(token, dark: dark) == want, String(format: "#%06X", WardPalette.hex(token, dark: dark)))
    }
}
// the promises S02 adds: the icon's coral on every tier at 3:1, primaryInk
// on every tier at 4.5:1, and the words on a fill at 4.5:1
for tier in WardPalette.tiers {
    check("promised: accent on the \(tier) at 3:1",
          WardPalette.promises.contains { $0.text == .accent && $0.on == tier && $0.ratio == 3 })
    check("promised: primaryInk on the \(tier) at 4.5:1",
          WardPalette.promises.contains { $0.text == .primaryInk && $0.on == tier && $0.ratio == 4.5 })
}
check("promised: onPrimary on primary at 4.5:1",
      WardPalette.promises.contains { $0.text == .onPrimary && $0.on == .primary && $0.ratio == 4.5 })

check("every token has a value", WardToken.allCases.allSatisfy { WardPalette.table[$0] != nil })
check("white on black is 21", abs(WardPalette.contrast(0xFFFFFF, 0x000000) - 21) < 0.01)
check("Theatre Blue on Clean Sheet is about 6.3",
      abs(WardPalette.contrast(0x1D5FB0, 0xFFFFFF) - 6.3) < 0.1,
      String(WardPalette.contrast(0x1D5FB0, 0xFFFFFF)))

for dark in [false, true] {
    for p in WardPalette.promises {
        let r = WardPalette.contrast(WardPalette.hex(p.text, dark: dark), WardPalette.hex(p.on, dark: dark))
        let label = "\(dark ? "dark" : "light"): \(p.text) on \(p.on) at least \(p.ratio)"
        check(label, r >= p.ratio, String(format: "%.2f", r))
    }
}
// The words of a chip or a pill sit on a well pressed into what is under
// it, with the well's inner shade over its edge (the review's B4). Every ink
// a chip can wear reads 4.5:1 on it, in light and dark, with and without
// Increase Contrast (which deepens the shade): the semantic tones' words,
// the secondary ink, the ink and primaryInk, and every section's accent.
check("the shade is there at a chip's edge and gone where its words start",
      WardRelief.shadeCoverage(.low, depth: 0) > 0.5 && WardRelief.shadeCoverage(.low, depth: 6) < 0.01)
let chipInks: [WardToken] = [.primaryInk, .danger, .inkSecondary, .ink]
// the nearest a chip's words come to its edge: its vertical padding (4)
// and the line's own leading above a capital (2 at least)
let chipTextDepth = 6.0
for dark in [false, true] {
    for hc in [false, true] {
        let mode = "\(dark ? "dark" : "light")\(hc ? " with Increase Contrast" : "")"
        let well = WardPalette.hex(.well, dark: dark)
        let ground = WardRelief.insetGround(well, lift: .low, dark: dark, highContrast: hc, depth: chipTextDepth)
        for token in chipInks {
            let r = WardPalette.contrast(WardPalette.hex(token, dark: dark), ground)
            let label = "\(mode): \(token) reads 4.5:1 on a chip's well"
            check(label, r >= 4.5, String(format: "%.3f", r))
        }
        for section in WardSection.allCases {
            let r = WardPalette.contrast(section.accent(dark: dark), ground)
            let label = "\(mode): \(section)'s accent reads 4.5:1 on a chip's well"
            check(label, r >= 4.5, String(format: "%.3f", r))
        }
    }
}

// one tone (the owner, 22 Oct: "there shouldn't be 2 tones"; 21 Oct, of the
// quiz dock: "one color not two color toned"): a card and a sheet are the
// page colour exactly, so a card is told from the ground only by relief,
// edges and rings. And a well too (the owner, 22 Oct, asked whether wells
// and sheets take the one tone: yes): a field, a track or a chosen segment
// is the page colour, shown as a well by its inset shade alone (the shade
// at a chip's edge is checked above), no longer a soft step darker.
for dark in [false, true] {
    let mode = dark ? "dark" : "light"
    let base = WardPalette.hex(.background, dark: dark)
    check("\(mode): a card is the page colour (surface == background)",
          WardPalette.hex(.surface, dark: dark) == base, String(WardPalette.hex(.surface, dark: dark), radix: 16))
    check("\(mode): a sheet is the page colour (raised == background)",
          WardPalette.hex(.raised, dark: dark) == base, String(WardPalette.hex(.raised, dark: dark), radix: 16))
    check("\(mode): a card and a sheet are one tone (surface == raised)",
          WardPalette.hex(.surface, dark: dark) == WardPalette.hex(.raised, dark: dark))
    check("\(mode): the tiers are well, then the base, a card and a sheet in one colour",
          WardPalette.tiers == [.well, .background, .surface, .raised])
    let well = WardPalette.hex(.well, dark: dark)
    check("\(mode): a well is the page colour (well == background)", well == base, String(well, radix: 16))
    let groove = WardPalette.contrast(WardPalette.hex(.hairline, dark: dark), WardPalette.hex(.background, dark: dark))
    check("\(mode): a groove shows but stays soft", groove >= 1.1 && groove < 1.6,
          String(format: "%.2f", groove))
}
// the icon's grounds: the launch screen (the Page colour set) is drawn on them
check("the bases the launch screen is drawn on are kept",
      WardPalette.hex(.background, dark: false) == 0xF3EADB && WardPalette.hex(.background, dark: true) == 0x281E2A)

// two characters, not one palette inverted: every text colour differs
// between the modes. The primary is the look outside any section: Sorta's
// one look's fill and accent, and its ink the palette's accent.
for dark in [false, true] {
    check("\(dark ? "dark" : "light"): primary and primaryInk are the sorta look's fill and accent",
          WardPalette.hex(.primary, dark: dark) == WardSection.sorta.fill(dark: dark) &&
          WardPalette.hex(.primaryInk, dark: dark) == WardSection.sorta.accent(dark: dark) &&
          WardPalette.hex(.accent, dark: dark) == WardSection.sorta.ink(dark: dark) &&
          WardSection.outside == .sorta)
}
// (how far apart in OKLab the two modes' values sit)
func modesApart(_ token: WardToken) -> Double {
    func lab(_ x: UInt32) -> (Double, Double, Double) {
        func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let (r0, g0, b0) = WardPalette.rgb(x)
        let (r, g, b) = (linear(r0), linear(g0), linear(b0))
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
    }
    let p = lab(WardPalette.hex(token, dark: false)), q = lab(WardPalette.hex(token, dark: true))
    return ((p.0 - q.0) * (p.0 - q.0) + (p.1 - q.1) * (p.1 - q.1) + (p.2 - q.2) * (p.2 - q.2)).squareRoot()
}
for token in [WardToken.primaryInk, .accent, .danger, .ink, .inkSecondary] {
    let label = "\(token) has a value of its own in each mode"
    check(label, WardPalette.hex(token, dark: false) != WardPalette.hex(token, dark: true),
          String(format: "%.3f apart", modesApart(token)))
}
// night reads its secondary text more strongly than day: dim rooms need it
let daySecondary = WardPalette.contrast(WardPalette.hex(.inkSecondary, dark: false), WardPalette.hex(.background, dark: false))
let nightSecondary = WardPalette.contrast(WardPalette.hex(.inkSecondary, dark: true), WardPalette.hex(.background, dark: true))
check("night's secondary text has more contrast than day's", nightSecondary > daySecondary,
      String(format: "%.2f vs %.2f", nightSecondary, daySecondary))

// the contrast maths, against WCAG's own worked values
check("contrast is symmetric", WardPalette.contrast(0x1D5FB0, 0xE0E5EC) == WardPalette.contrast(0xE0E5EC, 0x1D5FB0))
check("a colour on itself is 1", WardPalette.contrast(0x2A2E35, 0x2A2E35) == 1)
check("mid grey #777 on white is about 4.48 (just under AA)",
      abs(WardPalette.contrast(0x777777, 0xFFFFFF) - 4.48) < 0.01, String(WardPalette.contrast(0x777777, 0xFFFFFF)))
check("#767676 on white is about 4.54 (just AA)",
      abs(WardPalette.contrast(0x767676, 0xFFFFFF) - 4.54) < 0.01, String(WardPalette.contrast(0x767676, 0xFFFFFF)))
check("luminance of white is 1 and black 0", WardPalette.luminance(0xFFFFFF) == 1 && WardPalette.luminance(0) == 0)

// the appearance glide: a colour shift through linear light, from one
// palette to the other, about a third of a second
check("the glide takes 0.3 to 0.4 s", (0.3...0.4).contains(WardSchemeGlide.duration))
for token in WardToken.allCases {
    check("glide \(token): starts at day and ends at night",
          WardSchemeGlide.frame(token, t: 0) == WardPalette.hex(token, dark: false) &&
          WardSchemeGlide.frame(token, t: 1) == WardPalette.hex(token, dark: true))
}
check("mix clamps", WardPalette.mix(0x000000, 0xFFFFFF, -1) == 0 && WardPalette.mix(0x000000, 0xFFFFFF, 2) == 0xFFFFFF)
check("halfway between black and white in linear light is #BCBCBC",
      WardPalette.mix(0x000000, 0xFFFFFF, 0.5) == 0xBCBCBC, String(WardPalette.mix(0, 0xFFFFFF, 0.5), radix: 16))
var lastLum = 2.0
var steady = true
for i in 0...20 {
    let l = WardPalette.luminance(WardSchemeGlide.frame(.background, t: Double(i) / 20))
    if l > lastLum + 1e-9 { steady = false }
    lastLum = l
}
check("the base darkens steadily through the glide, never flashing", steady)

// the spacing scale
check("the scale is 4, 8, 12, 16, 24, 32", WardScale.steps == [4, 8, 12, 16, 24, 32] &&
      [WardScale.xs, WardScale.s, WardScale.m, WardScale.l, WardScale.xl, WardScale.xxl] == WardScale.steps)
check("on the scale", WardScale.onScale(12) && !WardScale.onScale(10) && !WardScale.onScale(20))

check("light: the base is a light grey", WardPalette.luminance(WardPalette.hex(.background, dark: false)) > 0.7)
// 0.010, not 0.015 (Stethoscore's bar): aubergine #281E2A measures 0.0155
check("dark: the base is a dark grey, not black",
      (0.010...0.06).contains(WardPalette.luminance(WardPalette.hex(.background, dark: true))))

// Day's relief shade is a grey mixed from bone toward aubergine in OKLab,
// at most 70% of the way (S02, "Relief"): Stethoscore's #A3B1C6 was blue.
func lab(_ x: UInt32) -> (Double, Double, Double) {
    func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
    let (r0, g0, b0) = WardPalette.rgb(x)
    let (r, g, b) = (linear(r0), linear(g0), linear(b0))
    let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
}
do {
    let bone = lab(WardPalette.hex(.background, dark: false)), aubergine = lab(WardPalette.hex(.background, dark: true))
    let shade = lab(WardPalette.dayShade), t = WardPalette.dayShadeMix
    let on = (bone.0 + (aubergine.0 - bone.0) * t, bone.1 + (aubergine.1 - bone.1) * t, bone.2 + (aubergine.2 - bone.2) * t)
    let off = ((shade.0 - on.0) * (shade.0 - on.0) + (shade.1 - on.1) * (shade.1 - on.1) + (shade.2 - on.2) * (shade.2 - on.2)).squareRoot()
    check("day's shade is mixed from bone toward aubergine, no more than 70% of the way",
          t > 0 && t <= 0.7, String(t))
    // a byte's rounding in sRGB moves a colour less than 0.004 in OKLab
    check("day's shade is that mix, to a byte's rounding", off < 0.004, String(format: "%.4f off", off))
    check("day's shade is the relief's", WardRelief.lightShade == WardPalette.dayShade)
}

// the asset catalogue agrees with the table
// (the suites run from the repository root, or from anywhere under it)
var root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
while !FileManager.default.fileExists(atPath: root.appendingPathComponent("ios/Sorta").path), root.path != "/" {
    root.deleteLastPathComponent()
}
let accent = root.appendingPathComponent("ios/Sorta/Assets.xcassets/AccentColor.colorset/Contents.json")
check("the AccentColor asset is found", FileManager.default.fileExists(atPath: accent.path), accent.path)
func components(_ appearance: String?, in asset: URL = accent) -> UInt32? {
    guard let data = try? Data(contentsOf: asset),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let colors = json["colors"] as? [[String: Any]] else { return nil }
    for entry in colors {
        let appearances = entry["appearances"] as? [[String: String]] ?? []
        guard appearances.first?["value"] == appearance,
              let c = (entry["color"] as? [String: Any])?["components"] as? [String: String] else { continue }
        func byte(_ k: String) -> UInt32? { c[k].flatMap { UInt32($0.dropFirst(2), radix: 16) } }
        guard let r = byte("red"), let g = byte("green"), let b = byte("blue") else { return nil }
        return r << 16 | g << 8 | b
    }
    return nil
}
check("AccentColor (light) is the primary", components(nil) == WardPalette.hex(.primary, dark: false),
      String(components(nil) ?? 0, radix: 16))
check("AccentColor (dark) is the night primary ink", components("dark") == WardPalette.hex(.primaryInk, dark: true),
      String(components("dark") ?? 0, radix: 16))
// Sorta's launch screen and page are the Page colour set (UILaunchScreen)
let launch = root.appendingPathComponent("ios/Sorta/Assets.xcassets/Page.colorset/Contents.json")
check("the Page asset is found", FileManager.default.fileExists(atPath: launch.path), launch.path)
check("Page (light) is the base", components(nil, in: launch) == WardPalette.hex(.background, dark: false),
      String(components(nil, in: launch) ?? 0, radix: 16))
check("Page (dark) is the base", components("dark", in: launch) == WardPalette.hex(.background, dark: true),
      String(components("dark", in: launch) ?? 0, radix: 16))

print(failures.isEmpty ? "\nALL WARD PALETTE TESTS PASS"
                       : "\n\(failures.count) WARD PALETTE TEST FAILURE(S)")
exit(failures.isEmpty ? 0 : 1)
