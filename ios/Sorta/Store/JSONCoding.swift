import Foundation

// The store's coders, as Stethoscore wrote them (Persistence/Store.swift:1091),
// so its export reads the same: ISO 8601 dates, sorted keys.
extension JSONDecoder {
    static let sorta: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}

extension JSONEncoder {
    static let sorta: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }()
}
