import SwiftUI

// Copied from Stethoscore (Features/Notes/IdeasView.swift:389,448), the two
// pieces the maps use before step 4 brings the Ideas screen.

/// A folder's name pushed in by its depth, for the folder menus.
enum IdeaIndent {
    static func name(_ name: String, depth: Int) -> String {
        let indent: String = String(repeating: "\u{2003}", count: depth)
        return indent + name
    }
}

/// The "Ideas" title on its own plate, pressed in as a label is.
struct IdeasTitlePill: View {
    var body: some View {
        Text("Ideas")
            .wardFont(.headline)
            .foregroundStyle(Color.wardInk)
            .padding(.horizontal, WardSpace.gutter)
            .padding(.vertical, WardSpace.s)
            .wardInset(in: Capsule())
            .accessibilityAddTraits(.isHeader)
    }
}
