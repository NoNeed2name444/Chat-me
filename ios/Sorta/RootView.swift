import SwiftUI

/// The first screen. For now it is the page colour from edge to edge, the
/// same as the launch screen, so the hand-over from the launch screen is
/// invisible.
///
/// Launched with `-SortaGallery` (GalleryTests), it shows the look kit's
/// states gallery on the page instead: every control at rest, held and
/// disabled, for real renders of the kit. Launched with `-graphPreview`, it
/// shows the maps on sample ideas instead (GraphPreview).
struct RootView: View {
    static let showsGallery = ProcessInfo.processInfo.arguments.contains("-SortaGallery")

    var body: some View {
        if GraphPreview.isOn {
            // the maps on Stethoscore's example ideas, for the UI tests and
            // the renders (GraphPreview); no screen a user reaches opens it
            GraphPreviewRoot()
                .wardLookRoot()
                .wardControls()
                .mapSky()
        } else if Self.showsGallery {
            WardStatesGallery(section: .sorta)
                // the status bar's area solid in the page colour, so the
                // rows scrolled under it don't show through its time
                .wardMasthead()
                .background { WardBackground() }
                .wardLookRoot()
                // the accent as the tint, and the soft switch and progress
                // bar, as at Stethoscore's root (RedPenApp.swift:283)
                .wardControls()
        } else {
            Color("Page")
                .ignoresSafeArea()
        }
    }
}
