import SwiftUI

struct QuizView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @State private var prompt: AnatomyStructure?
    @State private var options: [AnatomyStructure] = []
    @State private var selectedID: String?

    private var structures: [AnatomyStructure] { library.thorax.structures }
    private var answerState: String? {
        guard let selectedID, let prompt else { return nil }
        return selectedID == prompt.id ? "Correct — \(prompt.name)" : "Review the thorax explorer, then try another question."
    }

    var body: some View {
        NavigationStack { List {
            Section("Identification") {
                if let prompt {
                    Label("Review prompt", systemImage: "eye.fill").font(.caption).foregroundStyle(.secondary)
                    Text("Which structure matches this terminology identifier?").font(.headline)
                    Text(prompt.fmaID).font(.title2.monospaced().bold()).foregroundStyle(.teal)
                    ForEach(options) { option in
                        Button { selectedID = option.id } label: {
                            HStack { Text(option.name); Spacer(); if selectedID == option.id { Image(systemName: option.id == prompt.id ? "checkmark.circle.fill" : "xmark.circle.fill").foregroundStyle(option.id == prompt.id ? .green : .red) } }
                        }.disabled(selectedID != nil)
                    }
                    if let answerState { Text(answerState).font(.subheadline).foregroundStyle(selectedID == prompt.id ? .green : .orange) }
                    Button("Next question", action: begin).buttonStyle(.borderedProminent)
                } else {
                    ContentUnavailableView("Start a study question", systemImage: "graduationcap.fill", description: Text("Questions draw from the thorax review pack."))
                    Button("Start identification", action: begin).buttonStyle(.borderedProminent).disabled(structures.count < 4)
                }
            }
            Section("Locate mode") {
                Text("Choose a structure below, open its detail card, then select **Isolate in explorer**. Locate mode stays explicitly review-only until verified USDZ assets enable mesh hit testing.")
                ForEach(structures.prefix(5)) { structure in Button(structure.name) { library.selectedStructure = structure } }
            }
            Section("Bookmarked review") {
                if library.bookmarks.isEmpty { Text("Bookmark structures from their detail cards to create a focused review set.").foregroundStyle(.secondary) }
                ForEach(library.bookmarks) { Text($0.name) }
            }
        }.navigationTitle("Quiz & review").sheet(item: $library.selectedStructure) { StructureCard(structure: $0) } }
    }
    private func begin() { guard let newPrompt = structures.randomElement() else { return }; prompt = newPrompt; selectedID = nil; options = Array(([newPrompt] + structures.filter { $0.id != newPrompt.id }.shuffled().prefix(3)).shuffled()) }
}
