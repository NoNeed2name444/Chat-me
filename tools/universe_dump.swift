// The Universe dump (S05h): iOS's planner run on the JSON inputs Android's
// UniversePlanTest reads, written out body by body, so Android's port is held
// to iOS's numbers (android/app/src/test/resources/universe/ios-dump.json).
//
// Built as suite cosmic is (tools/swift_suites.txt), with GraphUniverse.swift,
// GraphTilt.swift and GraphTheme.swift:
//   swiftc -O -parse-as-library tools/universe_dump.swift ios/Sorta/Maps/GraphUniverse.swift \
//     ios/Sorta/Maps/GraphTilt.swift ios/Sorta/Maps/GraphTheme.swift -o dump
//   ./dump android/app/src/test/resources/universe/inputs ios-dump.json
// Every input file (by name, sorted) becomes one plan; numbers to 6 decimals.
// tools/universe_dump_check.py then adds the planner's blob hash and compares.
import Foundation

@main
struct UniverseDump {
    static func main() {
        let args: [String] = CommandLine.arguments
        guard args.count == 3 else {
            FileHandle.standardError.write("usage: dump <inputs directory> <out.json>\n".data(using: .utf8)!)
            exit(2)
        }
        let dir: String = args[1]
        let names: [String]
        do {
            names = try FileManager.default.contentsOfDirectory(atPath: dir).filter { $0.hasSuffix(".json") }.sorted()
        } catch {
            FileHandle.standardError.write("cannot list \(dir): \(error)\n".data(using: .utf8)!)
            exit(1)
        }
        var plans: [String] = []
        for name in names {
            let path: String = (dir as NSString).appendingPathComponent(name)
            guard let data = FileManager.default.contents(atPath: path),
                  let any = try? JSONSerialization.jsonObject(with: data),
                  let object = any as? [String: Any],
                  let input = readInput(object) else {
                FileHandle.standardError.write("cannot read \(path)\n".data(using: .utf8)!)
                exit(1)
            }
            let key: String = String(name.dropLast(5))
            plans.append("    " + text(key) + ": " + write(GraphUniverse.plan(input)))
        }
        let out: String = "{\n  \"plans\": {\n" + plans.joined(separator: ",\n") + "\n  }\n}\n"
        do {
            try out.write(toFile: args[2], atomically: true, encoding: .utf8)
        } catch {
            FileHandle.standardError.write("cannot write \(args[2]): \(error)\n".data(using: .utf8)!)
            exit(1)
        }
        print("wrote \(names.count) plans to \(args[2])")
    }

    // MARK: reading

    static func uuid(_ any: Any?) -> UUID? {
        guard let s = any as? String else { return nil }
        return UUID(uuidString: s)
    }

    static func readInput(_ o: [String: Any]) -> UniverseInput? {
        guard let rawNotes = o["notes"] as? [[String: Any]], let rawFolders = o["folders"] as? [[String: Any]],
              let rawEdges = o["edges"] as? [[String: Any]] else { return nil }
        var notes: [UniverseNote] = []
        for n in rawNotes {
            guard let id = uuid(n["id"]), let title = n["title"] as? String, let page = n["isPage"] as? Bool,
                  let words = (n["words"] as? NSNumber)?.intValue,
                  let created = (n["created"] as? NSNumber)?.doubleValue else { return nil }
            notes.append(UniverseNote(id: id, title: title, isPage: page, folder: uuid(n["folder"]), words: words,
                                      created: created))
        }
        var folders: [UniverseFolder] = []
        for f in rawFolders {
            guard let id = uuid(f["id"]), let name = f["name"] as? String else { return nil }
            folders.append(UniverseFolder(id: id, name: name, parent: uuid(f["parent"])))
        }
        var edges: [UniverseEdge] = []
        for e in rawEdges {
            guard let a = uuid(e["a"]), let b = uuid(e["b"]) else { return nil }
            edges.append(UniverseEdge(a: a, b: b))
        }
        let byName: Bool = (o["seedByName"] as? Bool) ?? false
        var input = UniverseInput(notes: notes, folders: folders, edges: edges, seedByName: byName)
        if let scale = (o["linkScale"] as? NSNumber)?.doubleValue { input.linkScale = scale }
        return input
    }

    // MARK: writing

    static func text(_ s: String) -> String {
        var out: String = "\""
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\t": out += "\\t"
            case "\r": out += "\\r"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        return out + "\""
    }

    static func num(_ x: Float) -> String { num(Double(x)) }

    static func num(_ x: Double) -> String {
        let s: String = String(format: "%.6f", x)
        return s == "-0.000000" ? "0.000000" : s
    }

    static func vec(_ v: SIMD3<Float>) -> String {
        "[" + num(v.x) + ", " + num(v.y) + ", " + num(v.z) + "]"
    }

    static func orbit(_ o: GraphOrbit) -> String {
        switch o {
        case .fixed(let p):
            return "{\"kind\": \"fixed\", \"p\": " + vec(p) + "}"
        case .circle(let radius, let u, let v, let phase, let rate):
            return "{\"kind\": \"circle\", \"radius\": " + num(radius) + ", \"u\": " + vec(u) + ", \"v\": " + vec(v)
                + ", \"phase\": " + num(phase) + ", \"rate\": " + num(rate) + "}"
        case .kepler(let a, let e, let u, let v, let phase, let rate):
            return "{\"kind\": \"kepler\", \"a\": " + num(a) + ", \"e\": " + num(e) + ", \"u\": " + vec(u) + ", \"v\": "
                + vec(v) + ", \"phase\": " + num(phase) + ", \"rate\": " + num(rate) + "}"
        case .ring(let x, let y, let z, let phase, let rate):
            return "{\"kind\": \"ring\", \"x\": " + num(x) + ", \"y\": " + num(y) + ", \"z\": " + num(z)
                + ", \"phase\": " + num(phase) + ", \"rate\": " + num(rate) + "}"
        case .drift(let base, let amp, let phase, let rate):
            return "{\"kind\": \"drift\", \"base\": " + vec(base) + ", \"amp\": " + num(amp) + ", \"phase\": "
                + num(phase) + ", \"rate\": " + num(rate) + "}"
        }
    }

    static func body(_ b: UniverseBody) -> String {
        let fields: [String] = [
            "\"id\": " + text(b.id.uuidString), "\"role\": " + text(b.role.rawValue), "\"parent\": \(b.parent)",
            "\"light\": \(b.light)", "\"sphere\": " + num(b.sphere), "\"tier\": \(b.tier)", "\"empty\": \(b.empty)",
            "\"ringed\": \(b.ringed)", "\"palette\": \(b.palette)", "\"galaxy\": \(b.galaxy)", "\"count\": \(b.count)",
            "\"links\": \(b.links)", "\"shell\": \(b.shell)", "\"orbit\": " + orbit(b.orbit), "\"home\": " + vec(b.home),
            "\"title\": " + text(b.title), "\"label\": " + text(b.label)
        ]
        return "{" + fields.joined(separator: ", ") + "}"
    }

    static func list(_ items: [String], indent: String) -> String {
        if items.isEmpty { return "[]" }
        return "[\n" + items.map { indent + "  " + $0 }.joined(separator: ",\n") + "\n" + indent + "]"
    }

    static func write(_ plan: UniversePlan) -> String {
        let pad: String = "      "
        let shells: [String] = plan.shells.map { s in
            "{\"owner\": \(s.owner), \"radius\": " + num(s.radius) + ", \"u\": " + vec(s.u) + ", \"v\": " + vec(s.v) + "}"
        }
        let links: [String] = plan.links.map { l in
            "{\"a\": \(l.a), \"b\": \(l.b), \"kind\": \(l.kind), \"centre\": \(l.centre)}"
        }
        let systems: [String] = plan.systems.map { points in "[" + points.map(vec).joined(separator: ", ") + "]" }
        let fields: [String] = [
            "\"summary\": " + text(plan.summary),
            "\"bodies\": " + list(plan.bodies.map(body), indent: pad),
            "\"shells\": " + list(shells, indent: pad),
            "\"links\": " + list(links, indent: pad),
            "\"envelope\": " + list(plan.envelope.map(vec), indent: pad),
            "\"systems\": " + list(systems, indent: pad),
            "\"lights\": [" + plan.lights.map { "\($0)" }.joined(separator: ", ") + "]"
        ]
        return "{\n" + fields.map { pad + $0 }.joined(separator: ",\n") + "\n    }"
    }
}
