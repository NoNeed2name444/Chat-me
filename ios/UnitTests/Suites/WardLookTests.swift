// One animated value for every Ward colour: the light/dark shift and the
// move between sections. Its ends are the palette's own colours, the middle
// is a linear-light mix, every token of a frame is taken at the same point
// (so no frame mixes two looks), a part held in the other mode stays put,
// each section's ink reads as an edge and sits apart (OKLab), and the relief,
// press and paper specs move with it. Copied from Stethoscore
// (reference/stethoscore-aec2af1c/ios/RedPen/Tests/WardLookTests.swift) and
// re-pinned to Sorta's one look (WardSection.sorta), with nothing excused.
// Where Stethoscore compared one section with another, Sorta has one look:
// docs/tasks/S02-result.md lists each such check for the owner.
import Foundation

var failures: [String] = []

func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

func f(_ x: Double) -> String { String(format: "%.3f", x) }
func h(_ x: UInt32) -> String { String(format: "#%06X", x) }

let tokens: [WardToken] = [.background, .surface, .raised, .well, .primary, .primaryInk, .onPrimary,
                           .accent, .danger, .ink, .inkSecondary, .hairline]
check("the tokens here are every token", Set(tokens) == Set(WardToken.allCases))
// the look outside any section, whose accents are the palette's own
let day = WardLook(dark: false, section: WardSection.outside)
let night = WardLook(dark: true, section: WardSection.outside)

// MARK: - Ends and rest

for look in [day, night] {
    let rest = WardGlide.rest(look)
    check("at rest (\(look.dark ? "dark" : "light")): settled", rest.settled)
    let same = tokens.allSatisfy { rest.hex($0, dark: look.dark) == WardPalette.hex($0, dark: look.dark) }
    check("at rest (\(look.dark ? "dark" : "light")): every token is the palette's", same)
    check("at rest: the ink is the section's", rest.ink(dark: look.dark) == look.section.ink(dark: look.dark))
}

let toNight = WardGlide(from: day, to: night, progress: 0)
check("a shift starts at the light palette",
      tokens.allSatisfy { toNight.hex($0, dark: true) == WardPalette.hex($0, dark: false) })
var end = toNight; end.progress = 1
check("and ends at the dark one",
      tokens.allSatisfy { end.hex($0, dark: true) == WardPalette.hex($0, dark: true) })
check("a finished shift is settled", end.settled && !toNight.settled)
check("the shift lasts about a third of a second", (0.3...0.4).contains(WardGlide.duration), f(WardGlide.duration))

// MARK: - Easing

var lastEase = -1.0
var smooth = true
for i in 0...100 {
    var g = toNight; g.progress = Double(i) / 100
    let e = g.eased
    if e < lastEase - 1e-12 { smooth = false }
    lastEase = e
}
check("the easing never runs backwards", smooth)
var mid = toNight; mid.progress = 0.5
check("the easing is symmetric, half way at half time", abs(mid.eased - 0.5) < 1e-12, f(mid.eased))
var early = toNight; early.progress = 0.05
check("the easing starts gently", early.eased < 0.05, f(early.eased))
var over = toNight; over.progress = 1.7
var under = toNight; under.progress = -0.4
check("progress past the ends is held to them", over.eased == 1 && under.eased == 0)

// MARK: - One point for every token (no frame mixes two looks)

for i in 1..<10 {
    var g = toNight; g.progress = Double(i) / 10
    let n = g.night(dark: true)
    let together = tokens.allSatisfy {
        g.hex($0, dark: true) == WardPalette.mix(WardPalette.hex($0, dark: false), WardPalette.hex($0, dark: true), n)
    }
    check("frame \(i)/10: every token sits at the same point of the shift", together)
}
var lastLum = WardPalette.luminance(WardPalette.hex(.background, dark: false))
var falling = true
for i in 1...20 {
    var g = toNight; g.progress = Double(i) / 20
    let l = WardPalette.luminance(g.hex(.background, dark: true))
    if l > lastLum + 1e-9 { falling = false }
    lastLum = l
}
check("the base darkens steadily, without a flash", falling)

// MARK: - Parts held in the other mode

check("a part held in the other mode stays put during a shift",
      mid.night(dark: false) == 0 && mid.hex(.surface, dark: false) == WardPalette.hex(.surface, dark: false))
let toDay = WardGlide(from: night, to: day, progress: 0.5)
check("a part marked held stays put, even in the mode the app is heading to",
      mid.night(dark: true, held: true) == 1 && mid.hex(.surface, dark: true, held: true) == WardPalette.hex(.surface, dark: true) &&
      mid.ink(dark: true, held: true) == WardSection.outside.ink.dark)
check("the app's own page moves while it turns light", toDay.night(dark: false) > 0 && toDay.night(dark: false) < 1)

// MARK: - Retargeting

let back = mid.retargeted(day)
check("turning back mid-way runs the same path in reverse from where it is",
      back.from == night && back.to == day && abs(back.progress - 0.5) < 1e-12 &&
      back.hex(.background, dark: false) == mid.hex(.background, dark: true))
var q = toNight; q.progress = 0.25
let backQ = q.retargeted(day)
check("and lands on the same colour, a quarter in",
      backQ.hex(.surface, dark: false) == q.hex(.surface, dark: true),
      h(backQ.hex(.surface, dark: false)) + " vs " + h(q.hex(.surface, dark: true)))
check("the same destination again changes nothing", mid.retargeted(night) == mid)
let fresh = WardGlide.rest(day).retargeted(night)
check("from rest a shift starts at the beginning", fresh.from == day && fresh.to == night && fresh.progress == 0)

// MARK: - Sections

let sections = WardSection.allCases
check("one look", sections == [.sorta])
// outside any section the look is Sorta's one look, and the default paper
// ink is its ink
check("outside any section the look is sorta", WardSection.outside == .sorta)
check("the default paper ink is the outside look's",
      WardPaper.ink(dark: false, highContrast: false).hex == WardSection.outside.ink(dark: false) &&
      WardPaper.ink(dark: true, highContrast: false).hex == WardSection.outside.ink(dark: true))
check("sorta: the corals' hue 33.5 and its fill, #C4503A by day and #EE866D by night",
      WardSection.sorta.hue == 33.5 && WardSection.sorta.fill.light == 0xC4503A &&
      WardSection.sorta.fill.dark == 0xEE866D)
for dark in [false, true] {
    check("\(dark ? "dark" : "light"): primary and primaryInk equal sorta's fill and accent, accent its ink",
          WardPalette.hex(.primary, dark: dark) == WardSection.sorta.fill(dark: dark) &&
          WardPalette.hex(.primaryInk, dark: dark) == WardSection.sorta.accent(dark: dark) &&
          WardPalette.hex(.accent, dark: dark) == WardSection.sorta.ink(dark: dark))
}

// OKLab (Ottosson), perceptual: equal distances look about equally
// different, and the hue is the one the eye sees (an HSL hue drifts with
// lightness: a dark yellow reads olive).
func oklab(_ x: UInt32) -> (L: Double, a: Double, b: Double) {
    func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
    let (r0, g0, b0) = WardPalette.rgb(x)
    let r = linear(r0), g = linear(g0), b = linear(b0)
    let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
}
func hue(_ x: UInt32) -> Double {
    let o = oklab(x)
    let d = atan2(o.b, o.a) * 180 / .pi
    return d < 0 ? d + 360 : d
}
func distance(_ x: UInt32, _ y: UInt32) -> Double {
    let p = oklab(x), q = oklab(y)
    return ((p.L - q.L) * (p.L - q.L) + (p.a - q.a) * (p.a - q.a) + (p.b - q.b) * (p.b - q.b)).squareRoot()
}
func apart(_ a: Double, _ b: Double) -> Double { let d = abs(a - b).truncatingRemainder(dividingBy: 360); return min(d, 360 - d) }

// How far apart (OKLab) two sections, or a section and a colour that
// means something, must sit to be told apart at a glance. 0.10 would be
// clearly apart; with seven sections and six meanings all at text
// contrast, 0.076 is the most these hue families give (tools/ward_accents.py).
let sectionsApart = 0.075

for dark in [false, true] {
    let mode = dark ? "dark" : "light"
    for s in sections {
        check("\(mode) \(s): light and dark inks share a hue",
              apart(hue(s.ink.light), hue(s.ink.dark)) <= 4, f(apart(hue(s.ink.light), hue(s.ink.dark))))
    }
    for s in sections {
        for tier in [WardToken.background, .surface] {
            let base = WardPalette.hex(tier, dark: dark)
            let c = WardPalette.contrast(WardRelief.edgeColor(s, dark: dark, on: base), base)
            let label = "\(mode) \(s): a control's edge reads 3:1 on the \(tier)"
            check(label, c >= WardRelief.edgeContrast, f(c))
        }
    }
}
let both = WardGlide(from: day, to: WardLook(dark: true, section: .sorta), progress: 0.5)
let n = both.night(dark: true)
let expect = WardPalette.mix(WardPalette.mix(WardSection.outside.ink.light, WardSection.outside.ink.dark, n),
                             WardPalette.mix(WardSection.sorta.ink.light, WardSection.sorta.ink.dark, n), both.eased)
check("mode and section move together, at the same point", both.ink(dark: true) == expect)

// MARK: - What moves, and what changes in one step

let swap = WardGlide.toward(night, from: .rest(day))
check("light to dark arrives in one step, every colour at once",
      swap.settled && swap.to == night && tokens.allSatisfy { swap.hex($0, dark: true) == WardPalette.hex($0, dark: true) })

// Text keeps its contrast on every frame the app can show: never below
// the lower of the two ends (a mix of light and dark falls to about 1.2:1).
func inkOnBase(_ g: WardGlide, dark: Bool) -> Double {
    WardPalette.contrast(g.hex(.ink, dark: dark), g.hex(.background, dark: dark))
}
var worstText = Double.infinity
for (from, to) in [(day, night), (night, day), (day, WardLook(dark: false, section: .sorta)),
                   (night, WardLook(dark: true, section: .sorta)), (day, WardLook(dark: true, section: .sorta))] {
    var g = WardGlide.toward(to, from: .rest(from))
    let floor = min(inkOnBase(.rest(from), dark: from.dark), inkOnBase(.rest(to), dark: to.dark))
    for i in 0...20 {
        if !g.settled { g.progress = Double(i) / 20 }
        let c = inkOnBase(g, dark: to.dark)
        worstText = min(worstText, c / floor)
    }
}
check("text never loses contrast in any shift the app makes", worstText >= 0.999, f(worstText))
var mixed = WardGlide(from: day, to: night, progress: 0.5)
check("(the mix it avoids: text half way between the palettes is grey on grey)",
      inkOnBase(mixed, dark: true) < 2, f(inkOnBase(mixed, dark: true)))
mixed.progress = 1

// MARK: - Relief, press and paper move with it

for lift in WardLift.allCases {
    check("\(lift): the relief's ends are light and dark",
          WardRelief.raised(lift, night: 0) == WardRelief.raised(lift, dark: false) &&
          WardRelief.raised(lift, night: 1) == WardRelief.raised(lift, dark: true) &&
          WardRelief.rim(lift, night: 0) == WardRelief.rim(lift, dark: false) &&
          WardRelief.rim(lift, night: 1) == WardRelief.rim(lift, dark: true) &&
          WardRelief.inset(lift, night: 1, control: true) == WardRelief.inset(lift, dark: true, control: true))
    let m = WardRelief.rim(lift, night: 0.5)
    let a = WardRelief.rim(lift, dark: false), b = WardRelief.rim(lift, dark: true)
    check("\(lift): half way, the rim's lights sit between",
          m.highlight.alpha >= min(a.highlight.alpha, b.highlight.alpha) &&
          m.highlight.alpha <= max(a.highlight.alpha, b.highlight.alpha) &&
          m.shade.hex == WardPalette.mix(a.shade.hex, b.shade.hex, 0.5))
    check("\(lift): the rim never reaches further half way",
          WardRelief.reach(m.highlight) <= WardRelief.rimReach + 1e-9 && WardRelief.reach(m.shade) <= WardRelief.rimReach + 1e-9)
    check("\(lift): the press's ends are light and dark",
          WardPress.spec(lift, depth: 0.4, night: 0) == WardPress.spec(lift, depth: 0.4, dark: false) &&
          WardPress.spec(lift, depth: 0.4, night: 1) == WardPress.spec(lift, depth: 0.4, dark: true))
}
for hc in [false, true] {
    let p0 = WardPaper.ink(night: 0, highContrast: hc, hex: 0x123456)
    let p1 = WardPaper.ink(night: 1, highContrast: hc, hex: 0x123456)
    let l = WardPaper.ink(dark: false, highContrast: hc), d = WardPaper.ink(dark: true, highContrast: hc)
    check("paper (contrast \(hc)): its edge strength runs from light to dark, in the section's ink",
          p0.edge == l.edge && p1.edge == d.edge && p0.hex == 0x123456)
}

// MARK: - Each section's accents

// Every accent role in every section and mode keeps its ratio: words and
// glyphs 4.5:1 on each tier (the well to a sheet), 3:1 as a graphic or
// large text (a progress beam, an icon) on the same; the on-fill words 4.5:1 on
// the fill.
for dark in [false, true] {
    let mode = dark ? "dark" : "light"
    for s in sections {
        let accent = s.accent(dark: dark), fill = s.fill(dark: dark)
        for tier in WardPalette.tiers {
            let c = WardPalette.contrast(accent, WardPalette.hex(tier, dark: dark))
            let words = "\(mode) \(s): accent words read 4.5:1 on the \(tier)"
            check(words, c >= 4.5, "\(h(accent)) \(f(c))")
            let glyphs = "\(mode) \(s): accent glyphs read 3:1 on the \(tier)"
            check(glyphs, c >= 3, f(c))
        }
        let white = WardPalette.contrast(WardPalette.hex(.onPrimary, dark: dark), fill)
        let onFill = "\(mode) \(s): the on-fill words read 4.5:1 on the fill"
        check(onFill, white >= 4.5, "\(h(fill)) \(f(white))")
        // the glide hands out the section's accents for the palette's
        // accent tokens
        let rest = WardGlide.rest(WardLook(dark: dark, section: s))
        check("\(mode) \(s): at rest the accent tokens are the section's",
              rest.hex(.primaryInk, dark: dark) == accent && rest.hex(.primary, dark: dark) == fill)
        check("\(mode) \(s): the palette's own primaryInk stays the palette's",
              rest.paletteHex(.primaryInk, dark: dark) == WardPalette.hex(.primaryInk, dark: dark))
        // the hue is the section's, in all three roles
        for (role, c) in [("ink", s.ink(dark: dark)), ("accent", accent), ("fill", fill)] {
            check("\(mode) \(s): its \(role) is its hue", apart(hue(c), s.hue) <= 2, f(hue(c)))
        }
    }
    // the accent apart from what means something: danger, and (away from
    // the look outside any section, whose own colour it is) the primary
    for s in sections {
        var near = 9.0, what = ""
        for token in WardSection.semanticTokens {
            let d = distance(s.accent(dark: dark), WardPalette.hex(token, dark: dark))
            if d < near { near = d; what = "\(token)" }
        }
        if s != WardSection.outside {
            let d = distance(s.accent(dark: dark), WardPalette.hex(.primaryInk, dark: dark))
            if d < near { near = d; what = "the primary" }
        }
        let meaning = "\(mode) \(s): the accent is not mistaken for a colour that means something"
        check(meaning, near >= sectionsApart, "\(what) \(f(near))")
        // the same for every coral of the mode: the fill, the accent and
        // the ink each sit apart from danger
        for (role, c) in [("fill", s.fill(dark: dark)), ("ink", s.ink(dark: dark))] {
            let d = distance(c, WardPalette.hex(.danger, dark: dark))
            check("\(mode) \(s): its \(role) is not mistaken for danger", d >= sectionsApart, f(d))
        }
    }
}

let accentSwap = WardGlide.toward(WardLook(dark: true, section: .sorta), from: .rest(WardLook(dark: false, section: .sorta)))
check("light to dark swaps the accents in one step",
      accentSwap.settled && accentSwap.hex(.primaryInk, dark: true) == WardSection.sorta.accent.dark)

// A wash of the accent's fill on a well (WardWash), its words in the ink,
// which keeps 4.5:1 on the wash.
for dark in [false, true] {
    for s in sections {
        let well = WardPalette.hex(.well, dark: dark)
        let wash = WardRelief.composite(WardReliefLight(hex: s.fill(dark: dark), alpha: WardWash.alpha, x: 0, y: 0, radius: 0), on: well)
        let c = WardPalette.contrast(WardPalette.hex(.ink, dark: dark), wash)
        check("\(dark ? "dark" : "light") \(s): words on the fill's wash read 4.5:1", c >= 4.5, f(c))
    }
}

// A colour reads the look as it is drawn (WardLookNow), not as it was
// when a view's body ran: a colour made before the look was set shows the
// look set after it, in both modes, and so does every other Ward colour.
do {
    let now = WardLookNow()
    let outside = WardLook(dark: false, section: WardSection.outside)
    check("the look starts outside any section", now.glide == .rest(outside) && WardLookNow.shared.glide == .rest(outside))
    now.set(.rest(WardLook(dark: false, section: .sorta)))
    check("a colour made outside any section shows sorta's accent by day",
          now.hex(.primaryInk, dark: false) == 0xB2402A, h(now.hex(.primaryInk, dark: false)))
    check("and at night", now.hex(.primaryInk, dark: true) == 0xEE866D, h(now.hex(.primaryInk, dark: true)))
    check("and its edges sorta's ink", now.ink(dark: false) == 0xC4503A && now.ink(dark: true) == 0xEE866D)
    for section in WardSection.allCases {
        for dark in [false, true] {
            now.set(.rest(WardLook(dark: dark, section: section)))
            for token in WardToken.allCases {
                let want: UInt32 = token == .primaryInk ? section.accent(dark: dark)
                    : token == .primary ? section.fill(dark: dark) : WardPalette.hex(token, dark: dark)
                check("drawn now: \(section) \(dark ? "dark" : "light") \(token)", now.hex(token, dark: dark) == want,
                      h(now.hex(token, dark: dark)) + " vs " + h(want))
            }
            check("drawn now: \(section) \(dark ? "dark" : "light") ink", now.ink(dark: dark) == section.ink(dark: dark))
        }
    }
    let shift = WardGlide(from: outside, to: WardLook(dark: true, section: .sorta), progress: 0.5)
    now.set(shift)
    check("mid-shift, a colour is this frame's", now.hex(.primaryInk, dark: false) == shift.hex(.primaryInk, dark: false) &&
          now.ink(dark: false) == shift.ink(dark: false))
    check("a part held in one mode keeps it mid-shift",
          now.hex(.primaryInk, dark: true, held: true) == shift.hex(.primaryInk, dark: true, held: true))
    let rest = WardLook(dark: true, section: .sorta)
    check("a face or the paper draws the look at rest it was handed",
          WardGlide.drawn(rest: rest, live: shift) == .rest(rest))
    check("and the shift's frame where it was handed none", WardGlide.drawn(rest: nil, live: shift) == shift)
}

// MARK: - A given section's ink (mode tile ring)

// A given section's ink follows the light/dark shift (Stethoscore's mode
// tiles took it, each in its own section's ink).
for section in WardSection.allCases {
    // a glide from light to dark
    let shift = WardGlide(from: WardLook(dark: false, section: .sorta),
                          to: WardLook(dark: true, section: .sorta),
                          progress: 0)
    check("\(section): at night 0, ink is the section's light ink",
          shift.ink(section, dark: true) == section.ink.light &&
          shift.ink(section, dark: false) == section.ink.light)

    var shiftEnd = shift; shiftEnd.progress = 1
    check("\(section): at night 1, ink is the section's dark ink",
          shiftEnd.ink(section, dark: true) == section.ink.dark)

    for i in 1...9 {
        var mid = shift; mid.progress = Double(i) / 10
        let n = mid.night(dark: true)
        let expected = WardPalette.mix(section.ink.light, section.ink.dark, n)
        check("\(section): frame \(i)/10 is the light/dark mix without hue shift",
              mid.ink(section, dark: true) == expected)
    }

    // at rest
    let restLight = WardGlide.rest(WardLook(dark: false, section: .sorta))
    check("\(section): at rest (light), ink is section's light ink",
          restLight.ink(section, dark: false) == section.ink.light)
    let restDark = WardGlide.rest(WardLook(dark: true, section: .sorta))
    check("\(section): at rest (dark), ink is section's dark ink",
          restDark.ink(section, dark: true) == section.ink.dark)
}

do {
    let now = WardLookNow()
    now.set(.rest(WardLook(dark: false, section: .sorta)))
    check("WardLookNow: sorta's ink is sorta's light ink",
          now.ink(.sorta, dark: false) == WardSection.sorta.ink.light)
}

// MARK: - Each section's wash

// The ground behind every page is pinned to the plain base, bone #F3EADB
// by day and aubergine #281E2A at night, whatever the fills are.
let plainGround: (light: UInt32, dark: UInt32) = (0xF3EADB, 0x281E2A)
let pinnedWashes: [WardSection: (light: UInt32, dark: UInt32)] = [
    .sorta: plainGround,
]
check("every section's wash is pinned", Set(pinnedWashes.keys) == Set(sections))
check("the plain ground is the palette's base",
      WardPalette.hex(.background, dark: false) == plainGround.light &&
      WardPalette.hex(.background, dark: true) == plainGround.dark)
for s in sections {
    for dark in [false, true] {
        let pinned = dark ? pinnedWashes[s]!.dark : pinnedWashes[s]!.light
        check("\(dark ? "dark" : "light") \(s): its wash is the plain base",
              s.wash(dark: dark) == pinned && WardGround.home(s, dark: dark).base == pinned,
              "\(h(s.wash(dark: dark))) vs \(h(pinned))")
    }
}
check("the look outside any section stands on the plain base too",
      WardSection.outside.wash.light == plainGround.light && WardSection.outside.wash.dark == plainGround.dark)

for dark in [false, true] {
    let mode = dark ? "dark" : "light"
    let chartInk = WardPalette.hex(.ink, dark: dark)
    let biroGrey = WardPalette.hex(.inkSecondary, dark: dark)
    for s in sections {
        let wash = s.wash(dark: dark)
        let cChart = WardPalette.contrast(chartInk, wash)
        check("\(mode) \(s): the ink keeps 4.5:1 on its wash", cChart >= 4.5, "\(h(wash)) \(f(cChart))")
        let cBiro = WardPalette.contrast(biroGrey, wash)
        check("\(mode) \(s): the secondary ink keeps 4.5:1 on its wash", cBiro >= 4.5, "\(h(wash)) \(f(cBiro))")
        let cAccent = WardPalette.contrast(s.accent(dark: dark), wash)
        let onWash = "\(mode) \(s): its accent keeps 4.5:1 on its wash"
        check(onWash, cAccent >= 4.5, "\(h(wash)) \(f(cAccent))")
    }
}

do {
    let now = WardLookNow()
    for section in WardSection.allCases {
        for dark in [false, true] {
            now.set(.rest(WardLook(dark: dark, section: section)))
            check("drawn now: \(section) \(dark ? "dark" : "light") wash",
                  now.wash(dark: dark) == section.wash(dark: dark),
                  h(now.wash(dark: dark)) + " vs " + h(section.wash(dark: dark)))
        }
    }
    let shift = WardGlide(from: day, to: night, progress: 0.5)
    now.set(shift)
    let midWash = now.wash(dark: true)
    let expectWash = WardPalette.mix(WardSection.sorta.wash(dark: false), WardSection.sorta.wash(dark: true),
                                     shift.night(dark: true))
    check("mid-shift, wash is linear-light mix", midWash == expectWash, "\(h(midWash)) vs \(h(expectWash))")
}

// MARK: - The ground

// The ground for a look at rest: the base with the section's wash, and the
// paper's ink at its strengths. (Sorta's launch screen is the Page colour
// alone, so no launch picture is drawn from it.)

do {
    check("the app opens on WardSection.opening: sorta", WardSection.opening == .sorta)
    for section in WardSection.allCases {
        for dark in [false, true] {
            let mode = dark ? "dark" : "light"
            let ground = WardGround.home(section, dark: dark)
            let look = WardGlide.rest(WardLook(dark: dark, section: section))
            check("\(section) \(mode): the ground's base is home's wash (WardBackground)",
                  ground.base == section.wash(dark: dark) && ground.base == look.wash(dark: dark), h(ground.base))
            check("\(section) \(mode): the ground's ink is home's paper ink",
                  ground.ink == WardPaper.ink(look: look, dark: dark, highContrast: false)
                  && ground.ink.hex == section.ink(dark: dark), h(ground.ink.hex))
        }
    }
}

print(failures.isEmpty ? "\nall passed" : "\n\(failures.count) failed")
exit(failures.isEmpty ? 0 : 1)
