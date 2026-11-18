import Foundation

// The map's looks (MapLook): the menu's order, the look the stored values
// make, and the theme each look draws.

var failures: [String] = []
func check(_ label: String, _ ok: Bool, _ detail: String = "") {
    print((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "  | " + detail))
    if !ok { failures.append(label) }
}

check("order: Space, Neuron, Tree, 2D, Performance, Fractal",
      MapLook.allCases == [.space, .neuron, .tree, .twoD, .performance, .fractal], "\(MapLook.allCases)")
check("identifiers: look-space, look-neuron, look-tree, look-2d, look-performance, look-fractal",
      MapLook.allCases.map(\.identifier) == ["look-space", "look-neuron", "look-tree", "look-2d", "look-performance", "look-fractal"])

// stored
check("stored: nothing stored is Space", MapLook.stored(board: false, theme: nil) == .space)
check("stored: the board is 2D, whatever the theme",
      [nil, "space", "neurons", "performance"].allSatisfy { MapLook.stored(board: true, theme: $0) == .twoD })
check("stored: each theme is its look",
      GraphTheme.allCases.map { MapLook.stored(board: false, theme: $0.rawValue) } == [.space, .neuron, .performance])
check("stored: an unknown theme is the standard", MapLook.stored(board: false, theme: "board") == .space)

// S11: the Tree is kept under its own key; the board wins over it, and anything else there is no look
check("flat: its key", MapLook.flatKey == "vignette.ideas.mapFlat")
check("flat: a stored Tree is the Tree, whatever the theme",
      [nil, "space", "neurons", "performance"].allSatisfy { MapLook.stored(board: false, theme: $0, flat: "tree") == .tree })
check("flat: the board wins", MapLook.stored(board: true, theme: "space", flat: "tree") == .twoD)
check("flat: anything else is no look", ["", "twoD", "space", "Tree", "fern"].allSatisfy { MapLook.stored(board: false, theme: nil, flat: $0) == .space })
check("flat: only the Tree", MapLook.flatLook("tree") == .tree && MapLook.flatLook("twoD") == nil)
// S12: the Fractal is kept under the same key, by its own name
check("flat: a stored Fractal is the Fractal, whatever the theme",
      [nil, "space", "neurons", "performance"].allSatisfy { MapLook.stored(board: false, theme: $0, flat: "fractal") == .fractal })
check("flat: the board wins over the Fractal", MapLook.stored(board: true, theme: "space", flat: "fractal") == .twoD)
check("flat: the Fractal by its name only", MapLook.flatLook("fractal") == .fractal && MapLook.stored(board: false, theme: nil, flat: "Fractal") == .space)

// the theme each draws, and back
check("theme: 2D draws none", MapLook.twoD.theme == nil)
check("theme: the Tree draws none", MapLook.tree.theme == nil)
check("theme: the Fractal draws none", MapLook.fractal.theme == nil)
for look in MapLook.allCases where look != .twoD && look != .tree && look != .fractal {
    check("theme: \(look) round-trips through its theme",
          look.theme.map { MapLook.stored(board: false, theme: $0.rawValue) } == look)
}

// the first-run steps name controls Sorta has: no + menu, no bar, no New page (S05f item 4)
for theme in GraphTheme.allCases {
    for step in theme.cardSteps {
        check("steps: \(theme) names a control Sorta has: \(step)",
              !step.contains("+ \u{203A}") && !step.contains("the bar") && !step.contains("New page"))
    }
}
check("steps: Neurons starts with a new folder", GraphTheme.neurons.cardSteps.first == "Hold an idea \u{203A} Choose folder \u{203A} New folder: a new cell.")
// an idea typed at Sort's top level goes to To sort and is drawn as a comet,
// so Space's rocky planet comes from an idea typed in a folder
check("steps: Space's rocky planet is an idea typed in a folder",
      GraphTheme.space.cardSteps.dropFirst().first == "Type an idea in a folder in Sort: a new rocky planet.",
      "\(GraphTheme.space.cardSteps)")

// S05j 7d: each [[ step says what to do and what it makes, with no colon
// joining two clauses; the same words as Android's (IdeaLooks.kt cardSteps)
check("steps: Space's link step", GraphTheme.space.cardSteps.last == "Type [[ and another idea\u{2019}s title in an idea to join them with a link.")
check("steps: Neurons' link step", GraphTheme.neurons.cardSteps.last == "Type [[ and another idea\u{2019}s title in an idea to join them with an axon.")
check("steps: Performance's link step", GraphTheme.performance.cardSteps.last == "Type [[ and another idea\u{2019}s title in an idea to join them with a line.")
for theme in GraphTheme.allCases {
    for step in theme.cardSteps where step.contains("[[") {
        check("steps: \(theme)'s link step joins no clauses with a colon", !step.contains(":"), step)
    }
}

print(failures.isEmpty ? "ALL PASS" : "\(failures.count) FAILED")
exit(failures.isEmpty ? 0 : 1)
