import Foundation

/// The map's four looks, in the look chip's menu order (S05b checklist 4):
/// Space, Neuron, 2D and Performance. Three are the 3D map's themes
/// (GraphTheme); 2D is the flat board. The look is stored as two values, so
/// the 3D map keeps reading its theme where it always has: the theme under
/// GraphTheme.key, and whether the board is shown under `boardKey`. Choosing
/// 2D keeps the theme, so going back to 3D finds the one left. Foundation
/// only, so it is tested on Linux (tools/swift_suites.txt, suite maplook).
enum MapLook: String, CaseIterable, Identifiable {
    // S12: fractal, clusters in clusters in 3D, beside Performance, kept under flatKey as the Tree is
    case space, neuron, tree, twoD, performance, fractal

    var id: String { rawValue }

    /// Where the board's choice is kept, beside the theme's key.
    static let boardKey = "vignette.ideas.mapBoard"

    /// The look the two stored values make; an unknown theme is the
    /// standard one (GraphTheme.stored).
    static func stored(board: Bool, theme raw: String?, flat: String? = nil) -> MapLook {
        board ? .twoD : (flatLook(flat) ?? MapLook(GraphTheme.stored(raw)))
    }

    /// S11: where a flat nested map (Tree) is kept; empty when the look is the board or a theme.
    static let flatKey = "vignette.ideas.mapFlat"
    /// S11: the flat nested map `flat` names, or nil for the board, a theme or nothing.
    static func flatLook(_ flat: String?) -> MapLook? {
        allCases.first { $0.theme == nil && $0 != .twoD && $0.rawValue == flat }
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
        case .twoD, .tree, .fractal: return nil
        }
    }

    /// The menu item's accessibility identifier.
    var identifier: String {
        switch self {
        case .space: return "look-space"
        case .neuron: return "look-neuron"
        case .tree: return "look-tree"
        case .twoD: return "look-2d"
        case .performance: return "look-performance"
        case .fractal: return "look-fractal"
        }
    }

    /// The chip's accessibility identifier.
    static let chipIdentifier = "map-look"
}
