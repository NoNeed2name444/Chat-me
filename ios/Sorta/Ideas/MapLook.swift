import Foundation

/// The map's four looks, in the look chip's menu order (S05b checklist 4):
/// Space, Neuron, 2D and Performance. Three are the 3D map's themes
/// (GraphTheme); 2D is the flat board. The look is stored as two values, so
/// the 3D map keeps reading its theme where it always has: the theme under
/// GraphTheme.key, and whether the board is shown under `boardKey`. Choosing
/// 2D keeps the theme, so going back to 3D finds the one left. Foundation
/// only, so it is tested on Linux (tools/swift_suites.txt, suite maplook).
enum MapLook: String, CaseIterable, Identifiable {
    case space, neuron, twoD, performance

    var id: String { rawValue }

    /// Where the board's choice is kept, beside the theme's key.
    static let boardKey = "vignette.ideas.mapBoard"

    /// The look the two stored values make; an unknown theme is the
    /// standard one (GraphTheme.stored).
    static func stored(board: Bool, theme raw: String?) -> MapLook {
        board ? .twoD : MapLook(GraphTheme.stored(raw))
    }

    init(_ theme: GraphTheme) {
        switch theme {
        case .space: self = .space
        case .neurons: self = .neuron
        case .performance: self = .performance
        }
    }

    /// The 3D theme it draws, nil for the board.
    var theme: GraphTheme? {
        switch self {
        case .space: return .space
        case .neuron: return .neurons
        case .performance: return .performance
        case .twoD: return nil
        }
    }

    /// The menu item's accessibility identifier.
    var identifier: String {
        switch self {
        case .space: return "look-space"
        case .neuron: return "look-neuron"
        case .twoD: return "look-2d"
        case .performance: return "look-performance"
        }
    }

    /// The chip's accessibility identifier.
    static let chipIdentifier = "map-look"
}
