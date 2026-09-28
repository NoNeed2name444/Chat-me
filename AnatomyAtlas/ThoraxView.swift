import SwiftUI
import RealityKit
import UIKit

struct ThoraxView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @State private var search = ""
    @State private var selectedLayer: AnatomyLayer = .organs
    @State private var visibleSystems: Set<String> = ["Skeletal", "Respiratory", "Cardiovascular"]
    @State private var scene = ThoraxSchematicScene()
    @State private var zoom: CGFloat = 1

    private var results: [AnatomyStructure] {
        library.thorax.structures.filter { item in
            (search.isEmpty || item.name.localizedCaseInsensitiveContains(search) || item.synonyms.joined(separator: " ").localizedCaseInsensitiveContains(search)) &&
            !library.hiddenStructureIDs.contains(item.id) && (library.isolatedStructureID == nil || library.isolatedStructureID == item.id)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ZStack(alignment: .top) {
                        RealityView { content in content.add(scene.root) } update: { _ in
                            scene.apply(layer: selectedLayer, systems: visibleSystems, hidden: library.hiddenStructureIDs, isolated: library.isolatedStructureID, scale: zoom)
                        }
                        .frame(height: 330)
                        .background(LinearGradient(colors: [Color(red: 0.04, green: 0.13, blue: 0.18), Color(red: 0.08, green: 0.28, blue: 0.30)], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 24))
                        .clipShape(RoundedRectangle(cornerRadius: 24))
                        .gesture(DragGesture().onChanged { scene.rotate(x: $0.translation.width, y: $0.translation.height) })
                        .simultaneousGesture(MagnifyGesture().onChanged { zoom = min(max($0.magnification, 0.7), 1.65) })

                        HStack {
                            Label("SCHEMATIC REVIEW", systemImage: "exclamationmark.triangle.fill")
                                .font(.caption.bold()).foregroundStyle(.black).padding(.horizontal, 10).padding(.vertical, 7).background(.yellow, in: Capsule())
                            Spacer()
                            Button { scene.reset(); zoom = 1 } label: { Image(systemName: "arrow.counterclockwise").fontWeight(.semibold).padding(9).background(.black.opacity(0.35), in: Circle()) }.tint(.white)
                        }.padding(14)
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Thorax explorer").font(.title3.bold())
                        Text("Drag to rotate · pinch to zoom · choose a layer to peel inward").font(.subheadline).foregroundStyle(.secondary)
                        if let isolated = library.thorax.structures.first(where: { $0.id == library.isolatedStructureID }) {
                            Label("Isolating \(isolated.name)", systemImage: "scope").font(.caption).foregroundStyle(.teal)
                        }
                    }.padding(.top, 4)
                }.listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 8, trailing: 16)).listRowBackground(Color.clear)

                Section("Peel layers") {
                    Picker("Peel layer", selection: $selectedLayer) { ForEach(AnatomyLayer.allCases) { layer in Label(layer.rawValue, systemImage: layer.icon).tag(layer) } }
                        .pickerStyle(.palette)
                }
                Section("Systems") { systemControls }
                Section("Structures · \(results.count)") {
                    if results.isEmpty { ContentUnavailableView("No matching structures", systemImage: "magnifyingglass", description: Text("Clear the search or visibility filters.")) }
                    ForEach(results) { structure in
                        Button { library.selectedStructure = structure } label: { StructureRow(structure: structure, bookmarked: library.bookmarkedIDs.contains(structure.id), isHidden: library.hiddenStructureIDs.contains(structure.id)) }
                            .buttonStyle(.plain)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .searchable(text: $search, prompt: "Search names, Latin names, or synonyms")
            .navigationTitle("Explore")
            .toolbar { if library.isolatedStructureID != nil || !library.hiddenStructureIDs.isEmpty { ToolbarItem(placement: .topBarTrailing) { Button("Clear") { library.clearFilters() } } } }
            .sheet(item: $library.selectedStructure) { StructureCard(structure: $0) }
        }
    }

    private var systemControls: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(["Skeletal", "Respiratory", "Cardiovascular"], id: \.self) { system in
                    Toggle(system, isOn: Binding(get: { visibleSystems.contains(system) }, set: { enabled in enabled ? visibleSystems.insert(system) : visibleSystems.remove(system) }))
                        .toggleStyle(.button).tint(system == "Respiratory" ? .pink : system == "Cardiovascular" ? .red : .teal)
                }
            }
        }
    }
}

private struct StructureRow: View {
    let structure: AnatomyStructure
    let bookmarked: Bool
    let isHidden: Bool
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: structure.layer.icon).foregroundStyle(color).frame(width: 26, height: 26).background(color.opacity(0.13), in: Circle())
            VStack(alignment: .leading, spacing: 2) { Text(structure.name).fontWeight(.medium); Text("\(structure.system) · \(structure.layer.rawValue) · \(structure.fmaID)").font(.caption).foregroundStyle(.secondary) }
            Spacer()
            if isHidden { Image(systemName: "eye.slash").foregroundStyle(.secondary) }
            if bookmarked { Image(systemName: "bookmark.fill").foregroundStyle(.yellow) }
            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }.contentShape(Rectangle())
    }
    private var color: Color { switch structure.system { case "Respiratory": .pink; case "Cardiovascular": .red; case "Skeletal": .teal; default: .orange } }
}

struct StructureCard: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @Environment(\.dismiss) private var dismiss
    let structure: AnatomyStructure
    @State private var note = ""
    var body: some View {
        NavigationStack { List {
            Section {
                VStack(alignment: .leading, spacing: 7) { Label(structure.system, systemImage: structure.layer.icon).foregroundStyle(.teal); Text(structure.studyNote).font(.subheadline) }
            }
            Section("Classification") { LabeledContent("Region", value: structure.region); LabeledContent("Layer", value: structure.layer.rawValue); LabeledContent("Terminology ID", value: structure.fmaID); if let parent = structure.parent { LabeledContent("Parent", value: parent) } }
            Section("Asset status") { LabeledContent("Source", value: structure.source); LabeledContent("Licence", value: structure.license); if let limitations = structure.limitations { Text(limitations).font(.footnote).foregroundStyle(.orange) } }
            Section("Study tools") {
                Button(library.bookmarkedIDs.contains(structure.id) ? "Remove bookmark" : "Add bookmark") { library.toggleBookmark(structure) }
                Button("Isolate in explorer") { library.isolate(structure); dismiss() }
                Button(library.hiddenStructureIDs.contains(structure.id) ? "Show structure" : "Hide structure") { library.toggleHidden(structure); dismiss() }
            }
            Section("Personal note") { TextEditor(text: $note).frame(minHeight: 110).accessibilityLabel("Personal note"); Button("Save note") { library.saveNote(note, for: structure) }.disabled(note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
        }.navigationTitle(structure.name).navigationBarTitleDisplayMode(.inline).onAppear { note = library.notes[structure.id] ?? "" } }
    }
}

@MainActor
final class ThoraxSchematicScene {
    let root = Entity()
    private var nodes: [String: ModelEntity] = [:]
    init() {
        add(id: "FMA_7480", mesh: .generateBox(size: 1.18), color: .systemTeal.withAlphaComponent(0.15), position: .zero)
        add(id: "FMA_7574", mesh: .generateSphere(radius: 0.31), color: .systemPink.withAlphaComponent(0.80), position: [-0.25, 0, 0])
        add(id: "FMA_7575", mesh: .generateSphere(radius: 0.29), color: .systemPink.withAlphaComponent(0.80), position: [0.24, 0, 0])
        add(id: "FMA_7088", mesh: .generateSphere(radius: 0.19), color: .systemRed, position: [0.04, -0.08, 0.30])
    }
    func apply(layer: AnatomyLayer, systems: Set<String>, hidden: Set<String>, isolated: String?, scale: CGFloat) {
        let mapping: [String: (AnatomyLayer, String)] = ["FMA_7480": (.bone, "Skeletal"), "FMA_7574": (.organs, "Respiratory"), "FMA_7575": (.organs, "Respiratory"), "FMA_7088": (.organs, "Cardiovascular")]
        for (id, node) in nodes { guard let detail = mapping[id] else { continue }; node.isEnabled = detail.0 == layer && systems.contains(detail.1) && !hidden.contains(id) && (isolated == nil || isolated == id) }
        root.scale = SIMD3(repeating: Float(scale))
    }
    func rotate(x: CGFloat, y: CGFloat) { root.orientation = simd_quatf(angle: Float(x + y) * 0.006, axis: [0, 1, 0]) }
    func reset() { root.orientation = simd_quatf(angle: 0, axis: [0, 1, 0]) }
    private func add(id: String, mesh: MeshResource, color: UIColor, position: SIMD3<Float>) { let node = ModelEntity(mesh: mesh, materials: [SimpleMaterial(color: color, isMetallic: false)]); node.position = position; root.addChild(node); nodes[id] = node }
}
