import SwiftUI

struct QuizView: View {
    @EnvironmentObject private var library: AnatomyLibrary
    @State private var prompt: AnatomyStructure?
    @State private var result: String?

    private var structures: [AnatomyStructure] { library.thorax.structures }
    private var options: [AnatomyStructure] {
        guard let prompt else { return [] }
        return Array(([prompt] + structures.filter { $0.id != prompt.id }.shuffled().prefix(3)).shuffled())
    }

    var body: some View {
        NavigationStack { List {
            Section("Identify the structure") {
                if let prompt {
                    Text("Which structure has FMA ID \(prompt.fmaID)?").font(.title3.bold())
                    ForEach(options) { option in
                        Button(option.name) { submit(option) }.disabled(result != nil)
                    }
                    if let result { Label(result, systemImage: result == "Correct" ? "checkmark.circle.fill" : "xmark.circle.fill").foregroundStyle(result == "Correct" ? .green : .red) }
                    Button("Next question") { begin() }.buttonStyle(.borderedProminent)
                } else {
                    Text("Download the thorax review pack, then start a four-choice identification question.")
                    Button("Start quiz") { begin() }.buttonStyle(.borderedProminent).disabled(structures.isEmpty)
                }
            }
            Section("Locate mode") {
                Text("Select a prompt, then use **Isolate in viewer** from its detail card to practise locating it. True mesh hit-testing activates only after approved USDZ assets replace this schematic build.")
            }
            Section("Bookmarks") {
                let bookmarks = structures.filter { library.bookmarkedIDs.contains($0.id) }
                if bookmarks.isEmpty { Text("Bookmarks you add from a structure card appear here.").foregroundStyle(.secondary) }
                ForEach(bookmarks) { Text($0.name) }
            }
        }.navigationTitle("Quiz & review") }
    }

    private func begin() { prompt = structures.randomElement(); result = nil }
    private func submit(_ option: AnatomyStructure) { result = option.id == prompt?.id ? "Correct" : "Not quite — \(prompt?.name ?? "")" }
}
