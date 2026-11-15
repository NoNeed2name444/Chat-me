import SwiftUI

/// Sorta: catch ideas and sort them.
@main
struct SortaApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                // debug builds' frame meter, for the frame tests (FrameMeter); nothing in a released app
                .frameMeter()
        }
    }
}
