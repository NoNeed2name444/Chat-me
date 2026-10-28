import SwiftUI

/// The first screen. For now it is the page colour from edge to edge, the
/// same as the launch screen, so the hand-over from the launch screen is
/// invisible.
struct RootView: View {
    var body: some View {
        Color("Page")
            .ignoresSafeArea()
    }
}
