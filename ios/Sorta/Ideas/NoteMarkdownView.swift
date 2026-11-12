import SwiftUI

/// An idea read as Markdown: headings, bullet and numbered lists, quotes,
/// code blocks, rules, bold, italic, `code` and links - and `[[Title]]`
/// links to other ideas, which open that idea when tapped.
///
/// Copied from Stethoscore (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/NoteMarkdown.swift:1-71, 145-153), in Sorta's kit: the
/// type is WardFont's (title2, title3 and headline for the headings, body),
/// the steps are WardSpace's in place of the literals 10, 6, 2, 18 and 3,
/// and links take `primaryInk`. Its "Nothing written yet." is cut: an empty
/// body shows nothing.
struct NoteMarkdownView: View {
    let text: String
    /// Tapping a `[[Title]]`.
    let openNote: (String) -> Void

    var body: some View {
        let blocks: [NoteMarkdown.Block] = NoteMarkdown.blocks(in: text)
        VStack(alignment: .leading, spacing: WardSpace.s) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                blockView(block)
            }
        }
        .wardFont(.body)
        .foregroundStyle(Color.wardInk)
        .tint(Color.wardPrimaryInk)
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
        .environment(\.openURL, OpenURLAction { url in
            guard url.scheme == NoteMarkdown.scheme else { return .systemAction }
            openNote(NoteMarkdown.title(from: url))
            return .handled
        })
    }

    @ViewBuilder
    private func blockView(_ block: NoteMarkdown.Block) -> some View {
        switch block {
        case .heading(let level, let line):
            Text(NoteMarkdown.inline(line))
                .wardFont(Self.headingFont(level))
                .padding(.top, level == 1 ? WardSpace.s : WardSpace.xs)
                .accessibilityAddTraits(.isHeader)
        case .bullet(let depth, let marker, let line):
            HStack(alignment: .firstTextBaseline, spacing: WardSpace.s) {
                Text(marker).foregroundStyle(Color.wardInkSecondary)
                Text(NoteMarkdown.inline(line))
            }
            .padding(.leading, CGFloat(depth) * WardSpace.l)
        case .quote(let line):
            Text(NoteMarkdown.inline(line))
                .foregroundStyle(Color.wardInkSecondary)
                .padding(.leading, WardSpace.m)
                .overlay(alignment: .leading) {
                    Rectangle().fill(Color.wardPrimaryInk).frame(width: WardSpace.xs)
                }
        case .code(let body):
            // the kit's callout, in its fixed-width variant where the face
            // has one
            Text(body)
                .monospaced()
                .wardFont(.callout)
                .padding(WardSpace.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .wardInset(in: RoundedRectangle(cornerRadius: WardRadius.field, style: .continuous))
        case .rule:
            // hairline, the one line (S05 plan), as under the capture strip
            Rectangle()
                .fill(Color.wardHairline)
                .frame(height: CGFloat(WardPaper.edgeWidth))
                .accessibilityHidden(true)
        case .paragraph(let line):
            Text(NoteMarkdown.inline(line))
        }
    }

    private static func headingFont(_ level: Int) -> WardFont {
        switch level {
        case 1: return .title2
        case 2: return .title3
        default: return .headline
        }
    }
}

extension NoteMarkdown {
    /// Bold, italic, `code` and links; `[[Title]]` becomes a link to that idea.
    static func inline(_ line: String) -> AttributedString {
        let linked: String = wikiLinksAsMarkdown(line)
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        if let styled = try? AttributedString(markdown: linked, options: options) {
            return styled
        }
        return AttributedString(line)
    }
}
