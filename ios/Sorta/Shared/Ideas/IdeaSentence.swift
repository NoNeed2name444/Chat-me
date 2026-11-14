import Foundation

/// Where a piece of writing stops (S06d A1, the rule S06a's A4 states):
/// text that, trimmed of white space, is not empty and does not end in a
/// closing mark (. ! ? …) ends mid-sentence. Android's IdeaSentence is the
/// same function.
enum IdeaSentence {
    /// The marks that close a sentence.
    static let closers: Set<Character> = [".", "!", "?", "\u{2026}"]

    /// `text` stops mid-sentence: not empty once trimmed, and its last
    /// character closes nothing.
    static func endsMidSentence(_ text: String) -> Bool {
        let trimmed: String = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let last = trimmed.last else { return false }
        return !closers.contains(last)
    }
}
