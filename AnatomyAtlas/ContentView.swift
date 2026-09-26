import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            ThoraxView()
                .tabItem { Label("Viewer", systemImage: "view.3d") }
            LibraryView()
                .tabItem { Label("Packs", systemImage: "square.stack.3d.down.right") }
            SourcesView()
                .tabItem { Label("Sources", systemImage: "checklist") }
        }
        .tint(.cyan)
    }
}

struct LibraryView: View {
    @EnvironmentObject private var library: AnatomyLibrary

    var body: some View {
        NavigationStack {
            List {
                Section("Region packs") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack { Label(library.thorax.title, systemImage: "lungs.fill"); Spacer(); Text(library.thorax.status).foregroundStyle(.orange) }
                        Text("Thoracic cage, lungs, heart & mediastinum")
                            .font(.subheadline).foregroundStyle(.secondary)
                        Label("\(library.thorax.structures.count) structures · \(library.thorax.sizeMB) MB estimated", systemImage: "arrow.down.circle")
                            .font(.footnote).foregroundStyle(.secondary)
                        Button(library.thoraxInstalled ? "Available offline" : "Download for review") { library.installThorax() }
                            .buttonStyle(.borderedProminent).disabled(library.thoraxInstalled)
                    }.padding(.vertical, 4)
                }
                Section("Download policy") { Text("Region packs download on demand and remain available offline. Approved USDZ assets are stored in Git LFS; no full-body bundle is shipped.") }
            }.navigationTitle("Study packs")
        }
    }
}

struct SourcesView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    var body: some View {
        NavigationStack {
            List {
                Section("Release gate") {
                    Label("Asset pending anatomical review", systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    Text("Every release asset needs a source URL, license, source hash, FMA identifier, transform, LOD counts, and smoothing log.")
                }
                Section("Thorax attribution") {
                    ForEach(library.thorax.structures) { item in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.name).fontWeight(.semibold)
                            Text("\(item.source) · \(item.license)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Section("Limits") { Text("Fine vessels, nerves, lymphatics, and membranes require dedicated detail views. Schematic content is labelled in the viewer and is never presented as scan-derived anatomy.") }
            }.navigationTitle("Sources & limits")
        }
    }
}
