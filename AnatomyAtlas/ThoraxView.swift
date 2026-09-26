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
        library.thorax.structures.filter {
            (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) || $0.synonyms.joined(separator: " ").localizedCaseInsensitiveContains(search)) &&
            !library.hiddenStructureIDs.contains($0.id) &&
            (library.isolatedStructureID == nil || library.isolatedStructureID == $0.id)
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RealityView { content in content.add(scene.root) } update: { _ in
                    scene.apply(layer: selectedLayer, systems: visibleSystems, hidden: library.hiddenStructureIDs, isolated: library.isolatedStructureID, scale: zoom)
                }
                .frame(height: 310)
                .overlay(alignment: .topLeading) { schematicBadge }
                .gesture(DragGesture().onChanged { scene.rotate(x: $0.translation.width, y: $0.translation.height) })
                .simultaneousGesture(MagnifyGesture().onChanged { zoom = min(max($0.magnification, 0.65), 1.75) })

                Picker("Peel layer", selection: $selectedLayer) {
                    ForEach(AnatomyLayer.allCases) { Text($0.rawValue).tag($0) }
                }.pickerStyle(.segmented).padding(.horizontal)
                HStack {
                    Button { scene.reset(); zoom = 1 } label: { Label("Reset", systemImage: "arrow.counterclockwise") }
                    Spacer()
                    if library.isolatedStructureID != nil { Button("Show all") { library.isolatedStructureID = nil; library.hiddenStructureIDs = [] } }
                }.font(.footnote).padding(.horizontal).padding(.vertical, 7)
                systemToggles
                List(results) { structure in
                    Button { library.selectedStructure = structure } label: { StructureRow(structure: structure, bookmarked: library.bookmarkedIDs.contains(structure.id)) }
                }
            }
            .searchable(text: $search, prompt: "Search names and synonyms")
            .navigationTitle("Thorax")
            .sheet(item: $library.selectedStructure) { StructureCard(structure: $0) }
        }
    }

    private var schematicBadge: some View {
        Label("SCHEMATIC · ASSET PENDING", systemImage: "exclamationmark.triangle.fill")
            .font(.caption.bold()).padding(9).background(.yellow.opacity(0.9), in: Capsule()).padding()
    }

    private var systemToggles: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack { ForEach(["Skeletal", "Respiratory", "Cardiovascular"], id: \.self) { system in
                Toggle(system, isOn: Binding(get: { visibleSystems.contains(system) }, set: { $0 ? visibleSystems.insert(system) : visibleSystems.remove(system) }))
                    .toggleStyle(.button)
            }}.padding(.horizontal).padding(.bottom, 6)
        }
    }
}

private struct StructureRow: View {
    let structure: AnatomyStructure
    let bookmarked: Bool
    var body: some View {
        HStack {
            Image(systemName: "circle.fill").foregroundStyle(.cyan)
            VStack(alignment: .leading) { Text(structure.name); Text("\(structure.system) · \(structure.layer.rawValue)").font(.caption).foregroundStyle(.secondary) }
            Spacer(); if bookmarked { Image(systemName: "bookmark.fill").foregroundStyle(.yellow) }
        }
    }
}

struct StructureCard: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @Environment(\.dismiss) private var dismiss
    let structure: AnatomyStructure
    @State private var note = ""

    var body: some View {
        NavigationStack { List {
            Section { LabeledContent("System", value: structure.system); LabeledContent("Layer", value: structure.layer.rawValue); LabeledContent("FMA", value: structure.fmaID) }
            Section("Provenance") { LabeledContent("Source", value: structure.source); LabeledContent("License", value: structure.license) }
            if let limits = structure.limitations { Section("Known limitation") { Text(limits) } }
            Section("Study tools") {
                Button(library.bookmarkedIDs.contains(structure.id) ? "Remove bookmark" : "Bookmark") { library.toggleBookmark(structure) }
                Button("Isolate in viewer") { library.isolate(structure); dismiss() }
                Button(library.hiddenStructureIDs.contains(structure.id) ? "Show structure" : "Hide structure") { library.toggleHidden(structure); dismiss() }
                TextEditor(text: $note).frame(minHeight: 90).accessibilityLabel("Personal note")
                Button("Save note") { library.saveNote(note, for: structure) }
            }
        }.navigationTitle(structure.name).navigationBarTitleDisplayMode(.inline)
        .onAppear { note = library.notes[structure.id] ?? "" }
        }
    }
}

@MainActor
final class ThoraxSchematicScene {
    let root = Entity()
    private var nodes: [String: ModelEntity] = [:]

    init() {
        add(id: "FMA_7480", mesh: .generateBox(size: 1.15), color: .systemTeal.withAlphaComponent(0.18), position: .zero)
        add(id: "FMA_7574", mesh: .generateSphere(radius: 0.32), color: .systemPink.withAlphaComponent(0.72), position: [-0.24, 0, 0])
        add(id: "FMA_7575", mesh: .generateSphere(radius: 0.32), color: .systemPink.withAlphaComponent(0.72), position: [0.24, 0, 0])
        add(id: "FMA_7088", mesh: .generateSphere(radius: 0.20), color: .systemRed, position: [0.03, -0.06, 0.28])
    }

    func apply(layer: AnatomyLayer, systems: Set<String>, hidden: Set<String>, isolated: String?, scale: CGFloat) {
        let mapping: [String: (AnatomyLayer, String)] = ["FMA_7480": (.bone, "Skeletal"), "FMA_7574": (.organs, "Respiratory"), "FMA_7575": (.organs, "Respiratory"), "FMA_7088": (.organs, "Cardiovascular")]
        for (id, node) in nodes {
            let details = mapping[id]!
            node.isEnabled = details.0 == layer && systems.contains(details.1) && !hidden.contains(id) && (isolated == nil || isolated == id)
        }
        root.scale = SIMD3(repeating: Float(scale))
    }

    func rotate(x: CGFloat, y: CGFloat) { root.orientation = simd_quatf(angle: Float(x + y) * 0.006, axis: [0, 1, 0]) }
    func reset() { root.orientation = simd_quatf(angle: 0, axis: [0, 1, 0]) }

    private func add(id: String, mesh: MeshResource, color: UIColor, position: SIMD3<Float>) {
        let node = ModelEntity(mesh: mesh, materials: [SimpleMaterial(color: color, isMetallic: false)])
        node.position = position; root.addChild(node); nodes[id] = node
    }
}
