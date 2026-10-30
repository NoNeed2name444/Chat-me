import SwiftUI

/// The first screen: the sorting desk (IdeasView), on the store opened at
/// launch (IdeasRoot). Its page is the launch screen's colour, and nothing
/// crosses the screen's middle while the store is empty, so the hand-over
/// from the launch screen is invisible.
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
            IdeasRoot()
                .wardLookRoot()
                .wardControls()
                .measuringWindow()
        }
    }
}

/// The app's one store, opened once at launch and kept for the life of the
/// window: NoteStore on its file in Application Support, every change saved
/// through it. A UI test's `-SortaStore <name>` (IdeasDesk.storeURL) points
/// it at a file of its own in the temporary folder instead, new for a new
/// name; a Release build ignores the argument.
struct IdeasRoot: View {
    @StateObject private var notes = NoteStore(fileURL: IdeasRoot.storeURL)

    static var storeURL: URL? {
        #if DEBUG
        guard let url = IdeasDesk.storeURL(arguments: ProcessInfo.processInfo.arguments,
                                           temporary: FileManager.default.temporaryDirectory) else { return nil }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                 withIntermediateDirectories: true)
        return url
        #else
        return nil
        #endif
    }

    var body: some View {
        IdeasView()
            .environmentObject(notes)
    }
}
