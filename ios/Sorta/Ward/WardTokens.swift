#if canImport(SwiftUI)
import SwiftUI
import UIKit
import CoreText

// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/Shared/
// Ward/WardTokens.swift), recoloured to Sorta's palette, with Sorta's type.
//
// Sorta's palette as SwiftUI colours, type, spacing and radii. Every
// colour comes from WardPalette's table through a dynamic UIColor, so light
// and dark have one source and no colour set can drift, and every one is
// drawn from the one look the app is at (WardLookStore), so a change of
// light or dark, or of section, shifts them all together. The soft UI light
// (WardRelief) is drawn by WardSurfaces.

extension Color {
    /// A palette colour, in the look the app is at when it is drawn
    /// (WardLookNow), never the one it was at when a view's body ran, so a
    /// colour made before a screen set its section still shows the
    /// section's. At rest it is one colour per token and section; while
    /// the look shifts (WardLookStore), a new one each frame, so a view
    /// redrawn on that frame draws it afresh and the whole app is at one
    /// point of the shift. Reading it in a view's body subscribes the view
    /// to the shift, and the start and end of every shift reach every
    /// colour through the traits (WardRestLookTrait), whether its view was
    /// redrawn or not.
    static func ward(_ token: WardToken) -> Color {
        let glide = WardLookStore.shared.glide
        if glide.settled { return WardRestColors.color(token, glide.to.section) }
        return Color(uiColor: WardRestColors.live(token))
    }

    /// A given section's accent ink (WardSection.accent), whatever section
    /// is on show: a mode's own glyph on home. Light and dark only.
    static func wardAccent(_ section: WardSection) -> Color {
        WardRestColors.accent(section)
    }

    static var wardBackground: Color { ward(.background) }
    static var wardSurface: Color { ward(.surface) }
    static var wardRaised: Color { ward(.raised) }
    static var wardWell: Color { ward(.well) }
    static var wardPrimary: Color { ward(.primary) }
    static var wardPrimaryInk: Color { ward(.primaryInk) }
    static var wardOnPrimary: Color { ward(.onPrimary) }
    /// The accent as a glyph or a stroke, never as words (3:1)
    static var wardGlyph: Color { ward(.accent) }
    static var wardDanger: Color { ward(.danger) }
    static var wardInk: Color { ward(.ink) }
    static var wardInkSecondary: Color { ward(.inkSecondary) }
    static var wardHairline: Color { ward(.hairline) }

    /// The ink of an edge and of the paper's frame: the section's own hue
    /// (WardSection.ink), drawn at a spec's edge strength.
    static var wardEdgeInk: Color {
        let glide = WardLookStore.shared.glide
        if glide.settled { return WardRestColors.ink(glide.to.section) }
        return Color(uiColor: WardRestColors.liveInk(edge: false))
    }

    /// A control's edge: the section's ink at WardRelief.edgeAlpha, at
    /// least 3:1 on the base, stronger under Increase Contrast.
    static var wardEdge: Color {
        let glide = WardLookStore.shared.glide
        if glide.settled { return WardRestColors.edge(glide.to.section) }
        return Color(uiColor: WardRestColors.liveInk(edge: true))
    }

    /// A given section's ink (WardSection.ink), whatever section is on
    /// show. Follows the light and dark
    /// shift without shifting its hue with the section on show.
    static func wardEdgeInk(_ section: WardSection) -> Color {
        WardRestColors.sectionInk(section)
    }

    /// The section's wash this frame (WardSection.wash, pinned to the
    /// earlier backgrounds), shifting with the section's glide.
    static var wardWash: Color {
        let glide = WardLookStore.shared.glide
        if glide.settled { return WardRestColors.wash(glide.to.section) }
        return Color(uiColor: WardRestColors.liveWash())
    }

    static func uiColor(_ hex: UInt32, alpha: Double = 1) -> UIColor {
        let (r, g, b) = WardPalette.rgb(hex)
        return UIColor(red: r, green: g, blue: b, alpha: alpha)
    }

    /// One relief light at its strength.
    init(relief light: WardReliefLight) {
        let (r, g, b) = WardPalette.rgb(light.hex)
        self.init(red: r, green: g, blue: b, opacity: light.alpha)
    }
}

extension WardToken {
    var index: Int { WardRestColors.tokens.firstIndex(of: self) ?? 0 }
}

/// The colours at rest, made once and resolved by the trait. The section's
/// colours (its fill, accent, ink and edge) are one per section, so a view
/// redrawn in a new section is handed a new colour, and each reads the look
/// the app is at as it is drawn (WardLookNow), so one handed out before the
/// section was set shows the section on show all the same.
enum WardRestColors {
    static let tokens: [WardToken] = [.background, .surface, .raised, .well, .primary, .primaryInk, .onPrimary,
                                      .accent, .danger, .ink, .inkSecondary, .hairline]
    static let palette: [Color] = tokens.map { token in
        Color(uiColor: UIColor { traits in
            Color.uiColor(WardPalette.hex(token, dark: traits.userInterfaceStyle == .dark))
        })
    }
    /// A token as it is drawn now, in the trait it resolves in.
    static func live(_ token: WardToken) -> UIColor {
        UIColor { traits in
            Color.uiColor(WardLookNow.shared.hex(token, dark: traits.userInterfaceStyle == .dark, held: traits.wardHeld))
        }
    }

    /// The section's ink as it is drawn now; with `edge`, at a control's
    /// edge strength.
    static func liveInk(edge: Bool) -> UIColor {
        UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            let hex = WardLookNow.shared.ink(dark: dark, held: traits.wardHeld)
            return Color.uiColor(hex, alpha: edge ? WardRelief.edgeAlpha(dark: dark, highContrast: traits.accessibilityContrast == .high) : 1)
        }
    }

    /// A given section's ink as it is drawn now; with `edge`, at a
    /// control's edge strength.
    static func liveInk(_ section: WardSection, edge: Bool) -> UIColor {
        UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            let hex = WardLookNow.shared.ink(section, dark: dark, held: traits.wardHeld)
            return Color.uiColor(hex, alpha: edge ? WardRelief.edgeAlpha(dark: dark, highContrast: traits.accessibilityContrast == .high) : 1)
        }
    }

    /// The section's accents at rest: its fill (.primary) and its accent
    /// ink (.primaryInk), WardSection.fill and .accent.
    private static let accents: [WardSection: (fill: Color, ink: Color)] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, (Color(uiColor: live(.primary)), Color(uiColor: live(.primaryInk))))
    })
    /// Each section's own accent ink, whatever section is on show.
    private static let fixedAccents: [WardSection: Color] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, Color(uiColor: UIColor { traits in
            Color.uiColor(section.accent(dark: traits.userInterfaceStyle == .dark))
        }))
    })
    static func accent(_ section: WardSection) -> Color { fixedAccents[section] ?? palette[WardToken.primaryInk.index] }
    static func color(_ token: WardToken, _ section: WardSection) -> Color {
        switch token {
        case .primary: return accents[section]?.fill ?? palette[token.index]
        case .primaryInk: return accents[section]?.ink ?? palette[token.index]
        default: return palette[token.index]
        }
    }
    private static let inks: [WardSection: Color] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, Color(uiColor: liveInk(edge: false)))
    })
    private static let edges: [WardSection: Color] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, Color(uiColor: liveInk(edge: true)))
    })
    static func ink(_ section: WardSection) -> Color { inks[section] ?? palette[WardToken.primaryInk.index] }
    static func edge(_ section: WardSection) -> Color { edges[section] ?? palette[WardToken.hairline.index] }

    /// Each section's own ink, whatever section is on show: a mode tile's
    /// ring.
    private static let sectionInks: [WardSection: Color] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, Color(uiColor: liveInk(section, edge: false)))
    })
    static func sectionInk(_ section: WardSection) -> Color { sectionInks[section] ?? palette[WardToken.primaryInk.index] }

    /// The section's wash at rest, and as it shifts.
    private static let washes: [WardSection: Color] = Dictionary(uniqueKeysWithValues: WardSection.allCases.map { section in
        (section, Color(uiColor: liveWash()))
    })
    static func wash(_ section: WardSection) -> Color { washes[section] ?? Color(uiColor: liveWash()) }
    static func liveWash() -> UIColor {
        UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            let hex = WardLookNow.shared.wash(dark: dark, held: traits.wardHeld)
            return Color.uiColor(hex)
        }
    }
}

// MARK: - The one animated value

/// The look every Ward colour is drawn from (WardGlide): light or dark, and
/// which section is on show, and the shift between two looks. One value for
/// the whole app, so no frame can show two looks at once; views that read a
/// Ward colour or the glide are redrawn on each step of a shift, in one
/// update, with no animation transaction (so nothing cross-fades).
final class WardLookStore: Observable, @unchecked Sendable {
    static let shared = WardLookStore()

    private let registrar = ObservationRegistrar()
    /// The look itself, kept where the colours read it as they are drawn.
    private var now: WardLookNow { WardLookNow.shared }
    /// The sections asked for, newest last: a pushed screen's on top of
    /// home's, so going back returns to home's hue.
    private var sections: [(id: UUID, section: WardSection)] = []

    var glide: WardGlide {
        registrar.access(self, keyPath: \.glide)
        return now.glide
    }

    /// The look the app is at, or heading to.
    var target: WardLook { now.glide.to }

    func set(_ glide: WardGlide) {
        guard now.glide != glide else { return }
        registrar.withMutation(of: self, keyPath: \.glide) {
            now.set(glide)
        }
    }

    // sections

    @MainActor func push(_ section: WardSection, id: UUID) {
        if let i = sections.firstIndex(where: { $0.id == id }) {
            sections[i].section = section
        } else {
            sections.append((id, section))
        }
        settleSection()
    }

    @MainActor func pop(id: UUID) {
        sections.removeAll { $0.id == id }
        settleSection()
    }

    @MainActor private func settleSection() {
        let section = sections.last?.section ?? WardSection.outside
        WardLookDriver.shared.go(WardLook(dark: target.dark, section: section))
    }
}

/// Runs the shift: light and dark arrive in one step, every Ward colour in
/// the same frame; a new section's hue is stepped by a display link each
/// frame for WardGlide.duration, then left at rest. Under Reduce Motion a
/// new look arrives at once.
@MainActor
final class WardLookDriver: NSObject {
    static let shared = WardLookDriver()

    var reduceMotion = false
    private var link: CADisplayLink?
    private var start: CFTimeInterval = 0
    private var startProgress = 0.0

    func go(_ look: WardLook) {
        let store = WardLookStore.shared
        let now = store.glide
        guard look != now.to else { return }
        if reduceMotion {
            stop()
            WardLookDriver.still { store.set(.rest(look)) }
            return
        }
        // light and dark in one step; a section's hue shifts (WardGlide.toward)
        let next = WardGlide.toward(look, from: now)
        WardLookDriver.still { store.set(next) }
        if next.settled {
            stop()
            return
        }
        startProgress = next.progress
        start = CACurrentMediaTime()
        if link == nil {
            let link = CADisplayLink(target: self, selector: #selector(step))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
            link.add(to: .main, forMode: .common)
            self.link = link
        }
    }

    @objc private func step(_ link: CADisplayLink) {
        let store = WardLookStore.shared
        var glide = store.glide
        glide.progress = min(1, startProgress + (CACurrentMediaTime() - start) / WardGlide.duration)
        WardLookDriver.still {
            store.set(glide.progress >= 1 ? .rest(glide.to) : glide)
        }
        if glide.progress >= 1 { stop() }
    }

    /// A step of the shift is a new colour, never an animation of one: a
    /// change made inside someone's withAnimation (a dock tap) must not
    /// cross-fade the colours (the grey films of the first recording).
    static func still(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
    }

    private func stop() {
        link?.invalidate()
        link = nil
    }
}

// MARK: - Held in one mode

/// A part drawn in one mode whatever the app's is (the vitals monitor,
/// bedtime's dark room): it keeps its colours while the app shifts. Bridged
/// to the trait collection so the colours' providers can see it.
struct WardHeldTrait: UITraitDefinition {
    static let defaultValue = false
    static let affectsColorAppearance = true
}

extension UITraitCollection {
    var wardHeld: Bool { self[WardHeldTrait.self] }
}

extension UIMutableTraits {
    var wardHeld: Bool {
        get { self[WardHeldTrait.self] }
        set { self[WardHeldTrait.self] = newValue }
    }
}

struct WardHeldKey: EnvironmentKey, UITraitBridgedEnvironmentKey {
    static let defaultValue = false
    static func read(from traitCollection: UITraitCollection) -> Bool { traitCollection.wardHeld }
    static func write(to mutableTraits: inout any UIMutableTraits, value: Bool) { mutableTraits.wardHeld = value }
}

extension EnvironmentValues {
    var wardHeld: Bool {
        get { self[WardHeldKey.self] }
        set { self[WardHeldKey.self] = newValue }
    }
}

/// The look at rest, carried to every colour through the traits (set at
/// the root, WardLookRoot): when a shift starts and when it ends, every
/// Ward colour is resolved again and every face and the paper are redrawn,
/// in the views whose bodies ran again and in those that did not, so none
/// keeps the look it was made in. nil while the look shifts, and where the
/// root has not set it.
struct WardRestLookTrait: UITraitDefinition {
    static let defaultValue: WardLook? = nil
    static let affectsColorAppearance = true
}

extension UITraitCollection {
    var wardRestLook: WardLook? { self[WardRestLookTrait.self] }
}

extension UIMutableTraits {
    var wardRestLook: WardLook? {
        get { self[WardRestLookTrait.self] }
        set { self[WardRestLookTrait.self] = newValue }
    }
}

struct WardRestLookKey: EnvironmentKey, UITraitBridgedEnvironmentKey {
    static let defaultValue: WardLook? = nil
    static func read(from traitCollection: UITraitCollection) -> WardLook? { traitCollection.wardRestLook }
    static func write(to mutableTraits: inout any UIMutableTraits, value: WardLook?) { mutableTraits.wardRestLook = value }
}

extension EnvironmentValues {
    var wardRestLook: WardLook? {
        get { self[WardRestLookKey.self] }
        set { self[WardRestLookKey.self] = newValue }
    }
}

extension View {
    /// Drawn in `scheme` whatever the app's look, and kept out of the shift.
    func wardHeld(_ scheme: ColorScheme) -> some View {
        environment(\.colorScheme, scheme).environment(\.wardHeld, true)
    }
}

/// The type: Nunito, with Arabic drawn in Baloo Bhaijaan 2 (the fonts in
/// Sorta/Fonts, named in UIAppFonts). Every font is a text style's: its size
/// is the system's own for that style at the reader's text size, so it
/// follows Dynamic Type point for point (UIFontMetrics' scaling of the
/// default size drifted up to 6 points from the system's at AX5), and one
/// weight bolder under Bold Text.
/// Use it with `.wardFont(_:)`, which reads the view's text size.
struct WardFont: Equatable {
    /// The families' names as the font files give them.
    static let family = "Nunito"
    static let arabicFamily = "Baloo Bhaijaan 2"

    var style: UIFont.TextStyle
    var weight: UIFont.Weight
    var monospacedDigits = false

    static let largeTitle = WardFont(style: .largeTitle, weight: .bold)
    /// The kit's title, as Android's WardType.title: title 3 semibold, the
    /// heading of an empty state or a card. The system's Title 1 style is
    /// `title1`.
    static let title = WardFont(style: .title3, weight: .semibold)
    static let title1 = WardFont(style: .title1, weight: .bold)
    static let title2 = WardFont(style: .title2, weight: .bold)
    static let title3 = WardFont(style: .title3, weight: .semibold)
    /// Semibold, as Android's WardType.headline.
    static let headline = WardFont(style: .headline, weight: .semibold)
    static let body = WardFont(style: .body, weight: .regular)
    static let callout = WardFont(style: .callout, weight: .regular)
    static let subheadline = WardFont(style: .subheadline, weight: .regular)
    static let footnote = WardFont(style: .footnote, weight: .regular)
    static let caption = WardFont(style: .caption1, weight: .regular)
    static let caption2 = WardFont(style: .caption2, weight: .regular)

    // the controls' own faces (WardControls), as Android's WardType names them
    static let buttonPrimary = WardFont(style: .headline, weight: .bold)
    static let button = WardFont(style: .headline, weight: .semibold)
    static let buttonSmall = WardFont(style: .subheadline, weight: .semibold)
    static let chip = WardFont(style: .subheadline, weight: .semibold)
    static let statusChip = WardFont(style: .caption1, weight: .semibold)
    static let pill = WardFont(style: .subheadline, weight: .bold)
    /// Android sets the timer in the system monospace; iOS keeps Nunito
    /// with digits all one width.
    static let timer = WardFont(style: .subheadline, weight: .semibold, monospacedDigits: true)
    static let segment = WardFont(style: .subheadline, weight: .regular)
    static let segmentChosen = WardFont(style: .subheadline, weight: .semibold)
    static let circleGlyph = WardFont(style: .body, weight: .semibold)
    static let smallCaps = WardFont(style: .caption1, weight: .semibold)

    func weight(_ weight: UIFont.Weight) -> WardFont {
        var font = self
        font.weight = weight
        return font
    }

    func bold() -> WardFont { weight(.bold) }

    /// Digits all one width, so a count or a time does not jiggle.
    func monospacedDigit() -> WardFont {
        var font = self
        font.monospacedDigits = true
        return font
    }

    /// The weight on the CSS scale Nunito's weight axis uses (200 to 1000).
    static func axisWeight(_ weight: UIFont.Weight) -> CGFloat {
        let steps: [(UIFont.Weight, CGFloat)] = [
            (.ultraLight, 200), (.thin, 200), (.light, 300), (.regular, 400), (.medium, 500),
            (.semibold, 600), (.bold, 700), (.heavy, 800), (.black, 900),
        ]
        return steps.min { abs($0.0.rawValue - weight.rawValue) < abs($1.0.rawValue - weight.rawValue) }?.1 ?? 400
    }

    /// Two steps bolder (regular to semibold), for Bold Text.
    static func bolder(_ weight: UIFont.Weight) -> UIFont.Weight {
        let order: [UIFont.Weight] = [.ultraLight, .thin, .light, .regular, .medium, .semibold, .bold, .heavy, .black]
        guard let i = order.firstIndex(where: { $0.rawValue >= weight.rawValue - 0.001 }) else { return .black }
        return order[min(order.count - 1, i + 2)]
    }

    /// The font at `traits`' text size (the default size when nil), scaled
    /// by Dynamic Type; bolder when `boldText`.
    func uiFont(compatibleWith traits: UITraitCollection? = nil, boldText: Bool = false) -> UIFont {
        let standard = UITraitCollection(preferredContentSizeCategory: .large)
        let size = UIFont.preferredFont(forTextStyle: style, compatibleWith: traits ?? standard).pointSize
        let drawn = boldText ? Self.bolder(weight) : weight
        let wght = 0x77676874 // 'wght'
        var attributes: [UIFontDescriptor.AttributeName: Any] = [
            .family: Self.family,
            .traits: [UIFontDescriptor.TraitKey.weight: drawn.rawValue],
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wght: Self.axisWeight(drawn)],
            // what Nunito has no glyphs for, Arabic above all, falls to Baloo
            // Bhaijaan 2 at the same weight
            .cascadeList: [UIFontDescriptor(fontAttributes: [
                .family: Self.arabicFamily,
                .traits: [UIFontDescriptor.TraitKey.weight: drawn.rawValue],
                UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wght: min(800, max(400, Self.axisWeight(drawn)))],
            ])],
        ]
        if monospacedDigits {
            attributes[.featureSettings] = [[
                UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector,
            ]]
        }
        return UIFont(descriptor: UIFontDescriptor(fontAttributes: attributes), size: size)
    }

    /// The SwiftUI font at a Dynamic Type size.
    func font(size: DynamicTypeSize, boldText: Bool = false) -> Font {
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(size))
        return Font(uiFont(compatibleWith: traits, boldText: boldText) as CTFont)
    }
}

/// Sets a WardFont at the view's own text size and Bold Text setting.
private struct WardFontModifier: ViewModifier {
    let font: WardFont
    @Environment(\.dynamicTypeSize) private var size
    @Environment(\.legibilityWeight) private var legibility

    func body(content: Content) -> some View {
        content.font(font.font(size: size, boldText: legibility == .bold))
    }
}

extension View {
    /// The kit's type (WardFont): Nunito, Arabic in Baloo Bhaijaan 2, with
    /// Dynamic Type.
    func wardFont(_ font: WardFont) -> some View { modifier(WardFontModifier(font: font)) }
}

/// Gaps and padding, every one a step of WardScale (4, 8, 12, 16, 24, 32).
enum WardSpace {
    static let xs = CGFloat(WardScale.xs), s = CGFloat(WardScale.s), m = CGFloat(WardScale.m)
    /// the screen's side gutter, and a card's padding
    static let gutter = CGFloat(WardScale.l)
    static let l = CGFloat(WardScale.l), xl = CGFloat(WardScale.xl), xxl = CGFloat(WardScale.xxl)
    /// between the sections of a page
    static let section = CGFloat(WardScale.xl)
    /// extra room between the lines of a paragraph
    static let leading = CGFloat(WardScale.leading)
}

/// Corner radii, on the same scale.
enum WardRadius {
    static let card = CGFloat(WardScale.l)
    static let tile = CGFloat(WardScale.xl)
    static let button = CGFloat(WardScale.l)
    static let field = CGFloat(WardScale.m)
    static let icon = CGFloat(WardScale.m)
    static let bar = CGFloat(WardScale.xl)
}

/// The controls' sizes, in points: WardControls', as Android's
/// WardControlSize names them.
enum WardControlSize {
    /// A button, a field's row: 56 tall; compact and quiet, 44.
    static let button: CGFloat = 56
    static let buttonSmall: CGFloat = 44
    /// The least tap target: 44 on iOS (Android's is 48, Android's own).
    static let target: CGFloat = 44
    /// A broad screen's widest button.
    static let buttonMaxWidth: CGFloat = 360
    /// A segment's least height, and the segmented capsule's padding.
    static let segment: CGFloat = 44
    static let segmentedPadding = WardSpace.xs
    /// A filter chip's capsule, in a full target.
    static let filterChip: CGFloat = 36
    /// The switch: its track and knob.
    static let toggleWidth: CGFloat = 52
    static let toggleHeight: CGFloat = 32
    static let knob: CGFloat = 24
    /// The switch's tint, inset 3 inside the track: off the scale on
    /// purpose, a point less than the knob's inset (WardSpace.xs), so the
    /// tint shows round the knob.
    static let trackInset: CGFloat = 3
    /// An icon square, and its glyph's share of it.
    static let iconSquare: CGFloat = 40
    static let iconGlyph: CGFloat = 0.42
    static let iconRadiusShare: CGFloat = 0.28
    /// A ring's arc.
    static let ringLine: CGFloat = 8
    static let ringNumeral: CGFloat = 26

    /// An icon square's corner: WardRadius.icon, or less on a small one.
    static func iconRadius(_ size: CGFloat) -> CGFloat { min(WardRadius.icon, size * iconRadiusShare) }

    /// An icon square stands at mid from 56 points up, low under it.
    static func iconLift(_ size: CGFloat) -> WardLift { size >= 56 ? .mid : .low }

    /// A ring's groove clearance either side of the arc.
    static func ringClear(_ lineWidth: CGFloat) -> CGFloat { min(2, lineWidth / 3) }
}

/// The root of the shift: the window's light or dark (the system's, or
/// the app's own) is handed to WardLookDriver the moment it changes,
/// both from UIKit's trait change on the window (before SwiftUI redraws)
/// and from SwiftUI's own scheme, whichever comes first. The window and
/// every Ward colour change together, in one step (WardGlide.toward). Set
/// once at the root, outside the paper and the base.
struct WardLookRoot: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        // the look at rest, to every colour and face below (WardRestLookTrait)
        let glide = WardLookStore.shared.glide
        content
            .environment(\.wardRestLook, glide.settled ? glide.to : nil)
            .background { WardWindowWatch().frame(width: 0, height: 0).accessibilityHidden(true) }
            .onAppear {
                WardLookDriver.shared.reduceMotion = reduceMotion
                let store = WardLookStore.shared
                if store.glide.settled && store.target.dark != (scheme == .dark) {
                    store.set(.rest(WardLook(dark: scheme == .dark, section: store.target.section)))
                }
            }
            .onChange(of: reduceMotion) { _, now in WardLookDriver.shared.reduceMotion = now }
            .onChange(of: scheme) { _, now in
                let store = WardLookStore.shared
                WardLookDriver.shared.go(WardLook(dark: now == .dark, section: store.target.section))
            }
    }
}

/// Hears the window's light or dark change in UIKit, ahead of SwiftUI's
/// redraw, so every Ward colour takes the new style in the same frame.
private struct WardWindowWatch: UIViewRepresentable {
    func makeUIView(context: Context) -> WatchView { WatchView() }
    func updateUIView(_ view: WatchView, context: Context) {}

    final class WatchView: UIView {
        private var registration: (any UITraitChangeRegistration)?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let registration, let old = oldWindow { old.unregisterForTraitChanges(registration) }
            registration = nil
            oldWindow = window
            guard let window else { return }
            registration = window.registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (window: UIWindow, _: UITraitCollection) in
                let dark = window.traitCollection.userInterfaceStyle == .dark
                let store = WardLookStore.shared
                WardLookDriver.shared.go(WardLook(dark: dark, section: store.target.section))
                // the status bar's words take the new style in this frame
                // too, and again once every bar has taken its traits
                WatchView.settleStatusBar(in: window)
                DispatchQueue.main.async { WatchView.settleStatusBar(in: window) }
            }
            let dark = window.traitCollection.userInterfaceStyle == .dark
            let store = WardLookStore.shared
            if store.glide.settled && store.target.dark != dark {
                store.set(.rest(WardLook(dark: dark, section: store.target.section)))
            }
        }

        private weak var oldWindow: UIWindow?

        /// Asks every screen in the window for its status bar style with
        /// no animation, so the clock and icons switch with the page
        /// rather than fading across from the old style.
        static func settleStatusBar(in window: UIWindow) {
            UIView.performWithoutAnimation {
                var stack: [UIViewController] = window.rootViewController.map { [$0] } ?? []
                while let controller = stack.popLast() {
                    controller.setNeedsStatusBarAppearanceUpdate()
                    stack.append(contentsOf: controller.children)
                    if let shown = controller.presentedViewController { stack.append(shown) }
                }
            }
        }
    }
}

extension UINavigationController {
    /// A pushed screen's bar (a navigation bar on show) has UIKit fade the
    /// status bar's words for a third of a second when the app turns light,
    /// leaving white words on the light page; nothing in the app hides or
    /// shows the status bar, so its changes can all be immediate.
    open override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .none }

    /// The status bar's words for the bar's own light or dark, named
    /// outright. Left to the default style, the status bar worked the
    /// colour out itself and faded to it (r8: still 22 frames of white
    /// words on the light page); a named style changes when asked, with
    /// the page. The bar's traits, not the controller's, so a bar held
    /// dark over a light app keeps light words.
    open override var preferredStatusBarStyle: UIStatusBarStyle {
        navigationBar.traitCollection.userInterfaceStyle == .dark ? .lightContent : .darkContent
    }
}

/// A screen's section: its hue tints the paper's grid and the controls'
/// edges while it is on show, and the hue shifts to the next screen's
/// with the same glide as light and dark.
private struct WardSectionMark: ViewModifier {
    let section: WardSection
    @State private var id = UUID()

    func body(content: Content) -> some View {
        content
            // system controls (toggles, sliders, links, plain buttons) in
            // the section's accent, moving with its hue
            .tint(Color.wardPrimaryInk)
            .onAppear { WardLookStore.shared.push(section, id: id) }
            .onDisappear { WardLookStore.shared.pop(id: id) }
            .onChange(of: section) { _, now in WardLookStore.shared.push(now, id: id) }
    }
}

extension View {
    /// The app's root: light and dark shift as one (WardLookRoot).
    func wardLookRoot() -> some View { modifier(WardLookRoot()) }

    /// This screen's section (WardSection): its hue on the grid and edges.
    func wardSection(_ section: WardSection) -> some View { modifier(WardSectionMark(section: section)) }
}

/// A small-caps label ("INBOX"): the source string stays in
/// mixed case so VoiceOver reads words, not letters. Its letters are spaced
/// at the standard sizes, where they are small; at the accessibility sizes
/// they are big enough to read apart, and a wrapped date with the spacing
/// ran past its frame (the AX5 audit's "Text clipped", 15 Oct).
struct WardSmallCaps: ViewModifier {
    @Environment(\.dynamicTypeSize) private var typeSize
    var font: WardFont
    var colour: Color

    init(font: WardFont = .smallCaps, colour: Color = .wardInkSecondary) {
        self.font = font
        self.colour = colour
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        let styled = content
            .wardFont(font)
            .textCase(.uppercase)
            .foregroundStyle(colour)
        // no tracking at all at the accessibility sizes, not a tracking of
        // 0: the audit read home's date, tracked 0, as only partly
        // following Dynamic Type
        if typeSize.isAccessibilitySize {
            styled
        } else {
            styled.tracking(0.8)
        }
    }
}

extension View {
    func wardSmallCaps() -> some View { modifier(WardSmallCaps()) }
}
#endif
