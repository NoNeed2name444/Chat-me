import SwiftUI

@main
struct AnatomyAtlasApp: App {
    @StateObject private var library = AnatomyLibrary()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
        }
    }
}
