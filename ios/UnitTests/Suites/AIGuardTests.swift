import Foundation

// S08 A8 on Linux: no PRO_PAYS symbol in either app, no key-shaped constant
// in either app or in what ai/ bundles (the mirror guard's own patterns,
// read from tools/mirror_chatme.py), no Gemini API library in the iOS
// project, and no Gemini model in ai/tables/models.json. Android's
// AIGuardTest checks its Gradle files and resolved classpath.

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)

func files(_ path: String, _ extensions: Set<String>? = nil) -> [URL] {
    let base = root.appendingPathComponent(path)
    guard let walk = FileManager.default.enumerator(at: base, includingPropertiesForKeys: [.isRegularFileKey]) else { return [] }
    return walk.compactMap { $0 as? URL }.filter { url in
        (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
            && (extensions == nil || extensions!.contains(url.pathExtension))
    }
}

func text(_ url: URL) -> String { String(decoding: (try? Data(contentsOf: url)) ?? Data(), as: UTF8.self) }

// MARK: no PRO_PAYS

let iosSources = files("ios/Sorta")
let androidSources = files("android/app/src/main")
check("sources: both apps found", iosSources.count > 20 && androidSources.count > 20, "\(iosSources.count) \(androidSources.count)")
let proPays = (iosSources + androidSources).filter { text($0).contains("PRO_PAYS") }
check("PRO_PAYS: no such symbol in either app", proPays.isEmpty, proPays.map(\.lastPathComponent).joined(separator: ", "))

// MARK: no bundled key, by the mirror guard's patterns

/// The KEY patterns in tools/mirror_chatme.py: each `KEY = re.compile(...)`
/// statement's rb"..." pieces joined, a later one extending KEY.pattern.
func mirrorKeyPattern() -> String? {
    let lines = text(root.appendingPathComponent("tools/mirror_chatme.py")).components(separatedBy: "\n")
    var pattern: String?
    var i = 0
    while i < lines.count {
        guard lines[i].hasPrefix("KEY = re.compile(") else { i += 1; continue }
        var statement = lines[i]
        var depth = statement.filter { $0 == "(" }.count - statement.filter { $0 == ")" }.count
        while depth > 0, i + 1 < lines.count {
            i += 1
            statement += "\n" + lines[i]
            depth += lines[i].filter { $0 == "(" }.count - lines[i].filter { $0 == ")" }.count
        }
        var pieces = ""
        var rest = Substring(statement)
        while let start = rest.range(of: "rb\"") {
            let after = rest[start.upperBound...]
            guard let end = after.firstIndex(of: "\"") else { break }
            pieces += after[..<end]
            rest = after[after.index(after: end)...]
        }
        pattern = statement.contains("KEY.pattern") ? (pattern ?? "") + pieces : pieces
        i += 1
    }
    return pattern
}

let pattern = mirrorKeyPattern() ?? ""
let key = try? NSRegularExpression(pattern: pattern)
check("keys: the mirror guard's patterns read", key != nil && pattern.contains("sk-ant-"), pattern)

func matches(_ s: String) -> Bool {
    key?.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
}

// fakes built from halves, so no source file holds one whole
let hex64 = String(repeating: "0a", count: 32)
let planted = [
    ("an OpenRouter key", "sk-or-" + "v1-" + hex64),
    ("an OpenAI project key", "sk-" + "proj-" + String(repeating: "Ab3_", count: 8)),
    ("a Hugging Face token", "hf" + "_" + String(repeating: "aB3", count: 11)),
    ("an Anthropic key", "sk-" + "ant-" + "api03-" + String(repeating: "x", count: 20)),
]
for (name, fake) in planted {
    check("keys: the guard catches \(name)", matches("let k = \"" + fake + "\""))
}
check("keys: the tests' own fake credential is not key-shaped", !matches("fake-key-for-tests-1") && !matches("fake-key-for-tests-2"))

let bundled = iosSources + androidSources + files("ai/tables") + files("ai/prompts") + files("ai/embeddings/potion-base-4M")
let keyed = bundled.filter { matches(text($0)) }
check("keys: none in either app or in what ai/ bundles (\(bundled.count) files)", keyed.isEmpty,
      keyed.map(\.lastPathComponent).joined(separator: ", "))

// MARK: no Gemini API library, no Gemini model

let project = text(root.appendingPathComponent("ios/project.yml"))
check("iOS: project.yml found", project.contains("targets:"))
for banned in ["generative-ai-swift", "FirebaseAI", "firebase-ios-sdk", "GoogleGenerativeAI"] {
    check("iOS: no \(banned) in project.yml", !project.contains(banned))
}
let resolved = files("ios").filter { $0.lastPathComponent == "Package.resolved" }
check("iOS: no resolved package names a Gemini API library",
      resolved.allSatisfy { !text($0).contains("generative-ai-swift") && !text($0).contains("FirebaseAI") })
let models = text(aiRoot.appendingPathComponent("tables/models.json"))
check("models.json: read", models.contains("\"providers\""))
check("models.json: no Gemini model named", !models.lowercased().contains("gemini"))
let modelIDs = AIJSON.parse(Data(models.utf8))?["providers"]
check("models.json: an entry for every provider", AIProviderID.allCases.allSatisfy { modelIDs?[$0.rawValue] != nil })

finish("AI GUARD")
