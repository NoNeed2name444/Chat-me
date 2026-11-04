import Foundation

// The map's looks (MapLook): the menu's order, the look the stored values
// make, and the theme each look draws.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

check("order: Space, Neuron, 2D, Performance",
      MapLook.allCases == [.space, .neuron, .twoD, .performance], "\(MapLook.allCases)")
check("identifiers: look-space, look-neuron, look-2d, look-performance",
      MapLook.allCases.map(\.identifier) == ["look-space", "look-neuron", "look-2d", "look-performance"])

// stored
check("stored: nothing stored is Space", MapLook.stored(board: false, theme: nil) == .space)
check("stored: the board is 2D, whatever the theme",
      [nil, "space", "neurons", "performance"].allSatisfy { MapLook.stored(board: true, theme: $0) == .twoD })
check("stored: each theme is its look",
      GraphTheme.allCases.map { MapLook.stored(board: false, theme: $0.rawValue) } == [.space, .neuron, .performance])
check("stored: an unknown theme is the standard", MapLook.stored(board: false, theme: "board") == .space)

// the theme each draws, and back
check("theme: 2D draws none", MapLook.twoD.theme == nil)
for look in MapLook.allCases where look != .twoD {
    check("theme: \(look) round-trips through its theme",
          look.theme.map { MapLook.stored(board: false, theme: $0.rawValue) } == look)
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
