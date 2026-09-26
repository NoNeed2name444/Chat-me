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
    let layer: AnatomyLayer
}

enum AnatomyLayer: String, Codable, CaseIterable, Identifiable {
    case skin = "Skin", muscle = "Muscle", organs = "Organs", bone = "Bone"
    var id: String { rawValue }
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
    @Published var hiddenStructureIDs: Set<String> = []
    @Published var isolatedStructureID: String?
    @Published private(set) var bookmarkedIDs: Set<String>
    @Published private(set) var notes: [String: String]

    init() {
        bookmarkedIDs = Set(UserDefaults.standard.stringArray(forKey: "bookmarkedStructureIDs") ?? [])
        notes = UserDefaults.standard.dictionary(forKey: "structureNotes") as? [String: String] ?? [:]
        thorax = (try? Self.loadThorax()) ?? RegionPack(id: "thorax", title: "Thorax", status: "Review", sizeMB: 0, structures: [])
    }

    func installThorax() { installedPacks.insert(thorax.id) }
    var thoraxInstalled: Bool { installedPacks.contains(thorax.id) }

    func toggleBookmark(_ structure: AnatomyStructure) {
        if bookmarkedIDs.contains(structure.id) { bookmarkedIDs.remove(structure.id) } else { bookmarkedIDs.insert(structure.id) }
        UserDefaults.standard.set(Array(bookmarkedIDs), forKey: "bookmarkedStructureIDs")
    }

    func saveNote(_ note: String, for structure: AnatomyStructure) {
        notes[structure.id] = note
        UserDefaults.standard.set(notes, forKey: "structureNotes")
    }

    func toggleHidden(_ structure: AnatomyStructure) {
        if hiddenStructureIDs.contains(structure.id) { hiddenStructureIDs.remove(structure.id) } else { hiddenStructureIDs.insert(structure.id) }
    }

    func isolate(_ structure: AnatomyStructure) { isolatedStructureID = isolatedStructureID == structure.id ? nil : structure.id }

    private static func loadThorax() throws -> RegionPack {
        let url = Bundle.main.url(forResource: "thorax-manifest", withExtension: "json")!
        return try JSONDecoder().decode(RegionPack.self, from: Data(contentsOf: url))
    }
}
