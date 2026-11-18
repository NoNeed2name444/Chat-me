import Foundation

// S08 A7 on Linux: the table's licence and size, the 50 shared texts (token
// ids exact, vectors within 1e-4 of ai/embeddings/reference.py), the top 5
// neighbours in the fixture library, the Arabic fallback to IdeaEchoes, the
// index off the main thread and back from its file, and suggested links and
// resurfacing on top. Android's AIEmbeddingTest checks the same fixtures.

let dir = aiRoot.appendingPathComponent("embeddings/potion-base-4M")
let tableFile = dir.appendingPathComponent("potion-base-4M-int8.bin")
let size = (try? FileManager.default.attributesOfItem(atPath: tableFile.path)[.size] as? Int) ?? nil
check("table: under 5 MB", (size ?? .max) < 5_000_000, "\(String(describing: size))")
let licence = (try? String(contentsOf: dir.appendingPathComponent("LICENSE"), encoding: .utf8)) ?? ""
check("table: its licence is MIT, recorded beside it", licence.hasPrefix("MIT License") && licence.contains("Thomas van Dongen"))
let sources = (try? String(contentsOf: aiRoot.appendingPathComponent("embeddings/SOURCES.md"), encoding: .utf8)) ?? ""
check("table: its source and licence are written down", sources.contains("Licence:** MIT") && sources.contains("minishlab/potion-base-4M"))

let started = Date()
let embeddings = try! AIEmbeddings.load(root: aiRoot.appendingPathComponent("embeddings"))
check("table: 29,528 rows of 128", embeddings.rows == 29_528 && embeddings.dims == 128)
print("     (table loaded in \(String(format: "%.2f", Date().timeIntervalSince(started))) s)")

func maxDiff(_ a: [Double], _ b: [Double]) -> Double { zip(a, b).map { abs($0 - $1) }.max() ?? .infinity }
func floats(_ v: [Double]?) -> [Float]? { v.map { $0.map(Float.init) } }

let vectors = fixture("embeddings/vectors.json")
let tolerance = vectors["tolerance"]!.number!
var worst = 0.0
var exact = 0
for (k, e) in vectors["vectors"]!.array!.enumerated() {
    let text = e["text"]!.string!
    let ids = embeddings.tokenIDs(text)
    let want = e["ids"]!.array!.map { $0.int! }
    if ids == want { exact += 1 } else { check("text \(k + 1): token ids", false, "\(ids) != \(want)") }
    check("text \(k + 1): covered as the reference says", AIEmbeddings.covered(text) == e["covered"]!.bool!)
    let v = embeddings.vector(ids: ids)
    if let w = e["vector"]?.array?.map({ $0.number! }), let v {
        let d = maxDiff(v, w)
        worst = max(worst, d)
        check("text \(k + 1): vector within 1e-4", v.count == 128 && d <= tolerance, "\(d)")
    } else {
        check("text \(k + 1): no vector on both", v == nil && e["vector"]!.isNull)
    }
    check("text \(k + 1): a text the table does not cover gets no vector", (embeddings.vector(text) == nil) == !e["covered"]!.bool! || v == nil)
}
check("texts: all 50 give the reference's token ids", exact == 50 && vectors["vectors"]!.array!.count == 50, "\(exact)")
print("     (largest difference from the reference: \(worst))")

// MARK: the top 5 neighbours in the fixture library

let spec = fixture("embeddings/texts.json")
let library: [AINeighbour] = spec["library"]!.array!.map {
    let text = $0["text"]!.string!
    return AINeighbour(id: $0["id"]!.string!, text: text, made: 0, vector: floats(embeddings.vector(text)))
}
check("library: every idea covered", library.allSatisfy { $0.vector != nil } && library.count == 30)
let wanted = fixture("embeddings/library.json")
for q in wanted["queries"]!.array! {
    let text = q["text"]!.string!
    let qv = floats(embeddings.vector(text))!
    let top = AILinks.byMeaning(qv, current: nil, made: 1, ideas: library, except: [], limit: 5, close: -2)
    let want = q["top5"]!.array!.map { $0.string! }
    check("library: \"\(text)\": the same top 5", top == want, "\(top) != \(want)")
    let scores = top.map { id in AILinks.cosine(qv, library.first { $0.id == id }!.vector!) }
    check("library: \"\(text)\": scores within 1e-4", maxDiff(scores, q["scores"]!.array!.map { $0.number! }) <= 1e-4)
}

// MARK: Arabic falls back to IdeaEchoes' match

let arabic = spec["arabic"]!
let arabicIdeas = arabic["ideas"]!.array!.map {
    AINeighbour(id: $0["id"]!.string!, text: $0["text"]!.string!, made: $0["made"]!.number!, vector: floats(embeddings.vector($0["text"]!.string!)))
}
let arabicQuery = arabic["query"]!.string!
check("arabic: the query is not covered", !AIEmbeddings.covered(arabicQuery) && embeddings.vector(arabicQuery) == nil)
check("arabic: nor are the ideas", arabicIdeas.allSatisfy { $0.vector == nil })
let arabicLinks = AILinks.suggest(text: arabicQuery, vector: nil, current: nil, made: arabic["made"]!.number!, ideas: arabicIdeas)
let wantEchoes = wanted["arabic"]!["echoes"]!.array!.map { $0.string! }
check("arabic: links come from IdeaEchoes, as the reference ranks them", arabicLinks == wantEchoes && wantEchoes == ["A3", "A4", "A1"], "\(arabicLinks)")

// MARK: the index, built off the main thread, and back from its file

let ideas = spec["library"]!.array!.map { AIIdea(id: $0["id"]!.string!, title: "", text: $0["text"]!.string!) }
    + arabic["ideas"]!.array!.map { AIIdea(id: $0["id"]!.string!, title: "", text: $0["text"]!.string!) }
var mainRefused = false
do { _ = try AIIndex.build(ideas, embeddings: embeddings) } catch { mainRefused = (error as? AIError) == .mainThread }
check("index: building on the main thread is refused", mainRefused)
let index = AIIndex(file: scratch("index").appendingPathComponent("ai-index.bin"))
let t0 = Date()
let built = try! offMain { try index.rebuild(ideas, embeddings: embeddings) }
print("     (34 ideas indexed in \(String(format: "%.3f", Date().timeIntervalSince(t0))) s)")
check("index: one entry per idea, the uncovered ones without a vector",
      built.count == 34 && built.filter { $0.vector == nil }.map(\.id) == ["A1", "A2", "A3", "A4"])
check("index: the file gives back exactly what was built", index.load() == built)
let headerOK = (try? Data(contentsOf: index.file))?.prefix(4) == Data("SRTI".utf8)
check("index: the file starts SRTI", headerOK)
check("index: a missing file reads as nothing", AIIndex(file: scratch("none").appendingPathComponent("x.bin")).load() == nil)

// MARK: suggested links: by meaning first, then shared words; older ideas only

let byID = Dictionary(uniqueKeysWithValues: built.map { ($0.id, $0.vector) })
var neighbours = library.enumerated().map { k, n in AINeighbour(id: n.id, text: n.text, made: Double(100 + k), vector: byID[n.id]!) }
let seed = "seedlings for the vegetable patch"
let seedVector = floats(embeddings.vector(seed))!
let links = AILinks.suggest(text: seed, vector: seedVector, current: nil, made: 1000, ideas: neighbours)
check("links: the close ones by meaning come first", Array(links.prefix(2)) == ["L02", "L12"], "\(links)")
check("links: at most 5, none twice", links.count <= 5 && Set(links).count == links.count)
check("links: under 0.5 is not close", !AILinks.byMeaning(seedVector, current: nil, made: 1000, ideas: neighbours, except: [], limit: 5).contains("L01"))
let newer = AILinks.suggest(text: seed, vector: seedVector, current: nil, made: 110, ideas: neighbours)
check("links: only ideas made before", !newer.contains("L12") && newer.first == "L02", "\(newer)")
check("links: not the idea itself, nor one already linked",
      !AILinks.suggest(text: seed, vector: seedVector, current: "L02", made: 1000, ideas: neighbours, connected: ["L12"]).contains { $0 == "L02" || $0 == "L12" })

// MARK: resurfacing: up to 3 older ideas close to the last day's captures

let now = 1_000_000.0
neighbours = neighbours.map { var n = $0; n.made = now - 3 * 86400 + n.made; return n }
var recent = [AINeighbour(id: "R1", text: seed, made: now - 3600, vector: seedVector)]
check("resurface: older ideas close to a new capture", AIResurface.pick(neighbours + recent, now: now) == ["L02", "L12"])
check("resurface: nothing new, nothing back", AIResurface.pick(neighbours, now: now).isEmpty)
let olderArabic = arabicIdeas.map { var n = $0; n.made = now - 2 * 86400; return n }
recent.append(AINeighbour(id: "R2", text: arabicQuery, made: now - 60, vector: nil))
let picked = AIResurface.pick(neighbours + olderArabic + recent, now: now)
check("resurface: at most 3, an uncovered capture by its words", picked.count == 3 && Array(picked.prefix(2)) == ["L02", "L12"]
      && ["A3", "A4", "A1"].contains(picked[2]), "\(picked)")

finish("AI EMBEDDING")
