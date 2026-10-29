import CoreText
import SwiftUI
import UIKit
import XCTest
@testable import Sorta

/// The kit's type: Nunito and Baloo Bhaijaan 2 ship in the app and load by
/// name, the kit's fonts are Nunito and grow with Dynamic Type, and Arabic
/// in the kit's type is drawn with Baloo Bhaijaan 2.
@MainActor
final class WardFontTests: XCTestCase {
    static let styles: [WardFont] = [
        .largeTitle, .title, .title2, .title3, .headline, .body, .callout, .subheadline, .footnote, .caption, .caption2,
    ]

    func testBothFamiliesLoadByName() {
        XCTAssertEqual(WardFont.family, "Nunito")
        XCTAssertEqual(WardFont.arabicFamily, "Baloo Bhaijaan 2")
        for family in [WardFont.family, WardFont.arabicFamily] {
            let names = UIFont.fontNames(forFamilyName: family)
            XCTAssertFalse(names.isEmpty, "\(family) is not registered (UIAppFonts)")
            for name in names {
                let font = UIFont(name: name, size: 17)
                XCTAssertNotNil(font, "\(name) does not load")
                XCTAssertEqual(font?.familyName, family)
            }
        }
    }

    func testTheFontFilesAndLicencesShip() {
        // the tests run inside the app, so the main bundle is Sorta's
        let bundle = Bundle.main
        XCTAssertEqual(bundle.bundleIdentifier, "com.cramdown.sorta")
        for file in ["Nunito[wght]", "Nunito-Italic[wght]", "BalooBhaijaan2[wght]"] {
            XCTAssertNotNil(bundle.url(forResource: file, withExtension: "ttf"), "\(file).ttf is not in the app")
        }
        for licence in ["Nunito-OFL", "BalooBhaijaan2-OFL"] {
            XCTAssertNotNil(bundle.url(forResource: licence, withExtension: "txt"), "\(licence).txt is not in the app")
        }
        let fonts = bundle.object(forInfoDictionaryKey: "UIAppFonts") as? [String] ?? []
        XCTAssertEqual(Set(fonts), ["Nunito[wght].ttf", "Nunito-Italic[wght].ttf", "BalooBhaijaan2[wght].ttf"])
    }

    func testEveryStyleIsNunito() {
        for style in Self.styles {
            for weight: UIFont.Weight in [.regular, .semibold, .bold] {
                let font = style.weight(weight).uiFont()
                XCTAssertEqual(font.familyName, WardFont.family, "\(style.style.rawValue) \(weight)")
            }
            XCTAssertEqual(style.monospacedDigit().uiFont().familyName, WardFont.family)
            XCTAssertEqual(style.uiFont(boldText: true).familyName, WardFont.family)
        }
    }

    /// The default size is the text style's own, and every style is larger at
    /// the largest accessibility size: the type keeps Dynamic Type.
    func testDynamicType() {
        let standard = UITraitCollection(preferredContentSizeCategory: .large)
        let largest = UITraitCollection(preferredContentSizeCategory: .accessibilityExtraExtraExtraLarge)
        let smallest = UITraitCollection(preferredContentSizeCategory: .extraSmall)
        for style in Self.styles {
            let atStandard = style.uiFont(compatibleWith: standard).pointSize
            let system = UIFont.preferredFont(forTextStyle: style.style, compatibleWith: standard).pointSize
            XCTAssertEqual(atStandard, system, accuracy: 0.5, "\(style.style.rawValue) at the default size")
            XCTAssertGreaterThan(style.uiFont(compatibleWith: largest).pointSize, atStandard,
                                 "\(style.style.rawValue) grows at AX5")
            XCTAssertLessThan(style.uiFont(compatibleWith: smallest).pointSize, atStandard,
                              "\(style.style.rawValue) shrinks at the smallest size")
        }
    }

    /// Arabic set in the kit's type is drawn with Baloo Bhaijaan 2 (Nunito
    /// has no Arabic letters), and Latin in the same line with Nunito.
    func testArabicIsDrawnWithBalooBhaijaan() {
        for style in [WardFont.body, .headline, .caption.weight(.semibold)] {
            let font = style.uiFont()
            let arabic = "فكرة جديدة"
            let mixed = "Idea فكرة"
            XCTAssertEqual(families(of: arabic, in: font), [WardFont.arabicFamily], "\(style.style.rawValue)")
            XCTAssertEqual(Set(families(of: mixed, in: font)), [WardFont.family, WardFont.arabicFamily],
                           "\(style.style.rawValue)")
        }
    }

    /// The families each run of `text` is drawn in, in order, spaces aside.
    private func families(of text: String, in font: UIFont) -> [String] {
        let attributed = NSAttributedString(string: text, attributes: [.font: font])
        let line = CTLineCreateWithAttributedString(attributed)
        let runs = CTLineGetGlyphRuns(line) as? [CTRun] ?? []
        var found: [String] = []
        for run in runs {
            let range = CTRunGetStringRange(run)
            let piece = (text as NSString).substring(with: NSRange(location: range.location, length: range.length))
            if piece.trimmingCharacters(in: .whitespaces).isEmpty { continue }
            let attributes = CTRunGetAttributes(run) as NSDictionary
            guard let drawn = attributes[kCTFontAttributeName as String] else { continue }
            let family = CTFontCopyFamilyName(drawn as! CTFont) as String
            if found.last != family { found.append(family) }
        }
        return found
    }
}

