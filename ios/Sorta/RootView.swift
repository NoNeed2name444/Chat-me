import SwiftUI

/// The first screen. For now it is the page colour from edge to edge, the
/// same as the launch screen, so the hand-over from the launch screen is
/// invisible.
///
/// Launched with `-SortaGallery` (GalleryTests), it shows the look kit's
/// states gallery on the page instead: every control at rest, held and
/// disabled, for real renders of the kit.
struct RootView: View {
    static let showsGallery = ProcessInfo.processInfo.arguments.contains("-SortaGallery")

    var body: some View {
        if Self.showsGallery {
            WardStatesGallery(section: .sorta)
                .background { WardBackground() }
                .wardLookRoot()
        } else {
            Color("Page")
                .ignoresSafeArea()
        }
    }
}
