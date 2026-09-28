import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            ThoraxView().tabItem { Label("Explore", systemImage: "view.3d") }
            LibraryView().tabItem { Label("Library", systemImage: "square.stack.3d.down.right") }
            QuizView().tabItem { Label("Quiz", systemImage: "graduationcap.fill") }
            SourcesView().tabItem { Label("Sources", systemImage: "checklist") }
        }
        .tint(.teal)
    }
}

struct LibraryView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    var body: some View {
        NavigationStack {
            List {
                Section("Continue studying") {
                    HStack(spacing: 14) {
                        Image(systemName: "lungs.fill").font(.title2).foregroundStyle(.teal).frame(width: 44, height: 44).background(.teal.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Thorax review pack").fontWeight(.semibold)
                            Text("\(library.thorax.structures.count) labelled structures · schematic review") .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                    }
                }
                Section("Region pack") {
                    LabeledContent("Download", value: library.thoraxInstalled ? "Available offline" : "\(library.thorax.sizeMB) MB estimated")
                    Button { library.installThorax() } label: {
                        Label(library.thoraxInstalled ? "Available offline" : "Make available offline", systemImage: library.thoraxInstalled ? "checkmark.icloud.fill" : "arrow.down.circle.fill")
                    }.disabled(library.thoraxInstalled)
                }
                Section("Next packs") {
                    lockedPack("Head & neck", detail: "Planned after thorax review")
                    lockedPack("Upper limb", detail: "Planned")
                    lockedPack("Abdomen & pelvis", detail: "Planned")
                }
                Section("Storage policy") { Text("Only the selected region is cached. Approved mesh LODs download on demand; the full body is never loaded into memory at once.") }
            }.navigationTitle("Study library")
        }
    }
    private func lockedPack(_ title: String, detail: String) -> some View {
        HStack { Image(systemName: "lock.fill").foregroundStyle(.secondary); VStack(alignment: .leading) { Text(title); Text(detail).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("Later").font(.caption).foregroundStyle(.secondary) }
    }
}

struct SourcesView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    var body: some View {
        NavigationStack {
            List {
                Section("Release status") {
                    Label("Educational schematic review build", systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    Text("No placeholder is represented as a scan-derived anatomical asset. Each production asset must pass source, licence, FMA, transform, LOD, normal, and reviewer checks before release.")
                }
                Section("Thorax provenance") {
                    ForEach(library.thorax.structures) { item in
                        VStack(alignment: .leading, spacing: 4) { Text(item.name).fontWeight(.semibold); Text("\(item.fmaID) · \(item.source)").font(.caption).foregroundStyle(.secondary); Text(item.license).font(.caption2).foregroundStyle(.orange) }
                    }
                }
                Section("Known limits") { Text("Peripheral nerves, small vessels, lymphatics, pleura, and fine cardiac anatomy need dedicated, verified detail packs. This build logs these limitations instead of implying false precision.") }
            }.navigationTitle("Sources & limits")
        }
    }
}
