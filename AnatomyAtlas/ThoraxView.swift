import SwiftUI
import RealityKit
import UIKit

struct ThoraxView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @State private var search = ""
    @State private var visibleSystems: Set<String> = ["Skeletal", "Respiratory", "Cardiovascular"]

    private var results: [AnatomyStructure] {
        library.thorax.structures.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) || $0.synonyms.joined(separator: " ").localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RealityView { content in
                    // A deliberately abstract placeholder until an approved USDZ pack exists.
                    let root = Entity()
                    let cage = ModelEntity(mesh: .generateBox(size: 1.15), materials: [SimpleMaterial(color: .systemTeal.withAlphaComponent(0.16), isMetallic: false)])
                    let left = ModelEntity(mesh: .generateSphere(radius: 0.32), materials: [SimpleMaterial(color: .systemPink.withAlphaComponent(0.72), isMetallic: false)])
                    let right = ModelEntity(mesh: .generateSphere(radius: 0.32), materials: [SimpleMaterial(color: .systemPink.withAlphaComponent(0.72), isMetallic: false)])
                    left.position = [-0.24, 0, 0]; right.position = [0.24, 0, 0]
                    root.addChild(cage); root.addChild(left); root.addChild(right)
                    content.add(root)
                }
                .frame(height: 330)
                .overlay(alignment: .topLeading) {
                    Label("SCHEMATIC · ASSET PENDING", systemImage: "exclamationmark.triangle.fill")
                        .font(.caption.bold()).padding(9).background(.yellow.opacity(0.9), in: Capsule()).padding()
                }
                .gesture(DragGesture())

                Picker("Layer", selection: .constant("Organs")) { Text("Skin").tag("Skin"); Text("Muscle").tag("Muscle"); Text("Organs").tag("Organs"); Text("Bone").tag("Bone") }
                    .pickerStyle(.segmented).padding(.horizontal)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack { ForEach(["Skeletal", "Respiratory", "Cardiovascular"], id: \.self) { system in
                        Toggle(system, isOn: Binding(get: { visibleSystems.contains(system) }, set: { $0 ? visibleSystems.insert(system) : visibleSystems.remove(system) }))
                            .toggleStyle(.button)
                    }}.padding()
                }
                List(results) { structure in
                    Button { library.selectedStructure = structure } label: {
                        HStack { Image(systemName: "circle.fill").foregroundStyle(.cyan); VStack(alignment: .leading) { Text(structure.name); Text(structure.system).font(.caption).foregroundStyle(.secondary) } }
                    }
                }
            }
            .searchable(text: $search, prompt: "Search names and synonyms")
            .navigationTitle("Thorax")
            .sheet(item: $library.selectedStructure) { StructureCard(structure: $0) }
        }
    }
}

struct StructureCard: View {
    let structure: AnatomyStructure
    var body: some View {
        NavigationStack { List {
            LabeledContent("System", value: structure.system); LabeledContent("FMA", value: structure.fmaID)
            LabeledContent("Source", value: structure.source); LabeledContent("License", value: structure.license)
            if let limits = structure.limitations { Section("Known limitation") { Text(limits) } }
        }.navigationTitle(structure.name).navigationBarTitleDisplayMode(.inline) }
    }
}
