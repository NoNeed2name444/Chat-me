import Foundation

/// Markdown into blocks, and `[[Title]]` links as Markdown links, kept apart
/// from the view so it is tested on Linux (tools/swift_suites.txt, suite
/// notemarkdown). The view that draws the blocks is NoteMarkdownView.
///
/// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/NoteMarkdown.swift:73-181) unchanged but for two things:
/// `inline`, which needs AttributedString's Markdown parser, moved to the
/// view's file, and `wikiLinksAsMarkdown`, made internal for the suite.
enum NoteMarkdown {
    static let scheme = "vignette-note"

    enum Block: Equatable {
        case heading(Int, String)
        case bullet(Int, String, String)
        case quote(String)
        case code(String)
        case rule
        case paragraph(String)
    }

    static func blocks(in text: String) -> [Block] {
        var out: [Block] = []
        var paragraph: [String] = []
        var code: [String]? = nil
        func flush() {
            if !paragraph.isEmpty { out.append(.paragraph(paragraph.joined(separator: " "))) }
            paragraph = []
        }
        for raw in text.components(separatedBy: "\n") {
            let trimmed: String = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                if let open = code {
                    out.append(.code(open.joined(separator: "\n")))
                    code = nil
                } else {
                    flush()
                    code = []
                }
                continue
            }
            if code != nil { code?.append(raw); continue }
            if trimmed.isEmpty { flush(); continue }
            if let block = lineBlock(raw, trimmed: trimmed) {
                flush()
                out.append(block)
            } else {
                paragraph.append(trimmed)
            }
        }
        if let open = code { out.append(.code(open.joined(separator: "\n"))) }
        flush()
        return out
    }

    /// A heading, list item, quote or rule; nil for plain text.
    private static func lineBlock(_ raw: String, trimmed: String) -> Block? {
        if trimmed == "---" || trimmed == "***" || trimmed == "___" { return .rule }
        let hashes: Int = trimmed.prefix { $0 == "#" }.count
        if hashes > 0 && hashes <= 6 && trimmed.dropFirst(hashes).hasPrefix(" ") {
            let rest: String = String(trimmed.dropFirst(hashes + 1))
            return .heading(hashes, rest)
        }
        if trimmed.hasPrefix("> ") { return .quote(String(trimmed.dropFirst(2))) }
        let indent: Int = raw.prefix { $0 == " " || $0 == "\t" }.count
        let depth: Int = min(indent / 2, 4)
        for mark in ["- ", "* ", "+ ", "\u{2022} "] where trimmed.hasPrefix(mark) {
            return .bullet(depth, "\u{2022}", String(trimmed.dropFirst(mark.count)))
        }
        let digits: String = String(trimmed.prefix { $0.isNumber })
        if !digits.isEmpty && digits.count <= 3 {
            let after: Substring = trimmed.dropFirst(digits.count)
            if after.hasPrefix(". ") || after.hasPrefix(") ") {
                return .bullet(depth, digits + ".", String(after.dropFirst(2)))
            }
        }
        return nil
    }

    /// `[[Title]]` and `[[Title|shown as]]` as Markdown links to the note of
    /// that title.
    static func wikiLinksAsMarkdown(_ line: String) -> String {
        var out: String = ""
        var rest: Substring = Substring(line)
        while let open = rest.range(of: "[["), let close = rest.range(of: "]]", range: open.upperBound..<rest.endIndex) {
            out += rest[rest.startIndex..<open.lowerBound]
            // [[Title|shown as]]: the page is the part before the bar, the
            // words after it are what the reader sees (NoteStore reads the
            // links the same way); tapping goes to the page, not to a new
            // page named "Title|shown as"
            let inner: String = String(rest[open.upperBound..<close.lowerBound])
            let parts = inner.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false)
            let title: String = (parts.first.map(String.init) ?? inner).trimmingCharacters(in: .whitespaces)
            let shown: String = parts.count > 1 ? String(parts[1]).trimmingCharacters(in: .whitespaces) : title
            let allowed: CharacterSet = .urlPathAllowed.subtracting(CharacterSet(charactersIn: "()"))
            let encoded: String = title.addingPercentEncoding(withAllowedCharacters: allowed) ?? title
            out += "[\(shown.isEmpty ? title : shown)](\(scheme):///\(encoded))"
            rest = rest[close.upperBound...]
        }
        out += rest
        return out
    }

    static func title(from url: URL) -> String {
        let path: String = url.path.hasPrefix("/") ? String(url.path.dropFirst()) : url.path
        return path.removingPercentEncoding ?? path
    }
}
