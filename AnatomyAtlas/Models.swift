import Foundation

struct AnatomyStructure: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let synonyms: [String]
    let system: String
    let region: String
    let parent: String?
    let fmaID: String
    let source: String
    let license: String
    let asset: String?
    let limitations: String?
}

struct RegionPack: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let status: String
    let sizeMB: Int
    let structures: [AnatomyStructure]
}

@MainActor
final class AnatomyLibrary: ObservableObject {
    @Published private(set) var thorax: RegionPack
    @Published var installedPacks: Set<String> = []
    @Published var selectedStructure: AnatomyStructure?

    init() {
        thorax = (try? Self.loadThorax()) ?? RegionPack(id: "thorax", title: "Thorax", status: "Review", sizeMB: 0, structures: [])
    }

    func installThorax() { installedPacks.insert(thorax.id) }
    var thoraxInstalled: Bool { installedPacks.contains(thorax.id) }

    private static func loadThorax() throws -> RegionPack {
        let url = Bundle.main.url(forResource: "thorax-manifest", withExtension: "json")!
        return try JSONDecoder().decode(RegionPack.self, from: Data(contentsOf: url))
    }
}
