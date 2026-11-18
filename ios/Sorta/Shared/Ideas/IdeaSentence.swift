import Foundation

/// Where a piece of writing stops: the one rule behind S06a's "You were
/// saying…" (IdeaStops.stoppedAt) and S06d's Unfinished lens
/// (IdeaLens.isUnfinished). Text ends mid-sentence when its last character,
/// past white space and closing quotes and brackets, is not a mark that
/// closes a sentence: . ! ? … or their full-width and Arabic forms. Empty
/// text, or white space alone, does not. Android's IdeaSentence is the same
/// function, case for case.
enum IdeaSentence {
    /// The marks that close a sentence.
    static let closers: Set<Character> = [".", "!", "?", "\u{2026}", "\u{3002}", "\u{FF01}", "\u{FF1F}", "\u{061F}"]
    /// What may follow a sentence's close and still leave it closed, besides
    /// white space: closing quotes and closing brackets.
    static let trailing: Set<Character> = ["\"", "'", "\u{201D}", "\u{2019}", "\u{00BB}", ")", "]", "}", "\u{300D}", "\u{300F}"]

    /// `text` stops mid-sentence: something is written, and the last of it
    /// closes nothing.
    static func endsMidSentence(_ text: String) -> Bool {
        guard let last = text.last(where: { !$0.isWhitespace && !trailing.contains($0) }) else { return false }
        return !closers.contains(last)
    }
}
