import Foundation

/// Echoes (S06c, the plugins plan's idea 4): from the words being typed,
/// the older ideas that share an uncommon one. All on the phone: no AI, no
/// network, nothing learnt. Android's IdeaEchoes.kt is the same rule, read
/// from the same cases (UnitTests/Suites/IdeaShapeCases.json), and its test
/// reads the stop words from this file.
///
/// A word is a run of letters or digits, lowercased, with apostrophes
/// dropped ("don't" is "dont"), 3 letters or more, not all digits and not a
/// stop word. A word is uncommon when 1 to max(2, N / 20) of the other N
/// ideas hold it. Each older idea holding one scores 1 / that count for
/// each; the best come first, then the newer, then by id.
nonisolated enum IdeaEchoes {
    /// The most echoes offered at once.
    static let limit = 5

    /// The words too common to say anything, one list for both platforms.
    static let stopWords: Set<String> = [
        // STOP-WORDS-BEGIN
        "about", "above", "after", "again", "against", "all", "also", "and", "any", "are", "around",
        "because", "been", "before", "being", "below", "between", "both", "but", "can", "cant",
        "could", "couldnt", "did", "didnt", "does", "doesnt", "doing", "done", "dont", "down",
        "during", "each", "even", "ever", "every", "few", "for", "from", "further", "get", "gets",
        "getting", "got", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "her", "here",
        "hers", "herself", "him", "himself", "his", "how", "into", "isnt", "its", "itself", "just",
        "let", "lets", "like", "made", "make", "many", "maybe", "more", "most", "much", "must",
        "myself", "need", "never", "new", "next", "nor", "not", "now", "off", "once", "one",
        "only", "other", "our", "ours", "ourselves", "out", "over", "own", "really", "same", "see",
        "she", "should", "shouldnt", "since", "some", "something", "still", "such", "than", "that",
        "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they",
        "thing", "things", "think", "this", "those", "though", "through", "too", "two", "under",
        "until", "use", "very", "want", "was", "wasnt", "way", "well", "were", "werent", "what",
        "whats", "when", "where", "which", "while", "who", "whom", "why", "will", "with", "wont",
        "would", "wouldnt", "yes", "yet", "you", "youd", "youll", "your", "youre", "yours",
        "yourself", "youve", "ive", "ill", "idea", "ideas",
        // STOP-WORDS-END
    ]

    /// The words of a text that can count, each once, in the order met.
    static func words(_ text: String) -> [String] {
        var out: [String] = []
        var seen = Set<String>()
        var word = ""
        func flush() {
            if word.count >= 3, !word.allSatisfy(\.isNumber), !stopWords.contains(word), seen.insert(word).inserted {
                out.append(word)
            }
            word = ""
        }
        for ch in text.lowercased() {
            if ch == "'" || ch == "\u{2019}" { continue }
            if ch.isLetter || ch.isNumber { word.append(ch) } else { flush() }
        }
        flush()
        return out
    }

    /// One idea as the matcher sees it.
    struct Idea<ID: Hashable> {
        let id: ID
        let text: String
        /// When it was made, for "older" and for ties.
        let made: Double
        /// The id as text, for the last tie.
        let key: String
    }

    /// Every idea's words and, for each word, which ideas hold it: built
    /// once per change to the ideas, asked many times while typing.
    struct Index<ID: Hashable> {
        let ideas: [Idea<ID>]
        let words: [Set<String>]
        let holders: [String: [Int]]
        let slot: [ID: Int]

        init(_ ideas: [Idea<ID>]) {
            self.ideas = ideas
            var words: [Set<String>] = []
            words.reserveCapacity(ideas.count)
            var holders: [String: [Int]] = [:]
            var slot: [ID: Int] = [:]
            for (i, idea) in ideas.enumerated() {
                let own = IdeaEchoes.words(idea.text)
                words.append(Set(own))
                for w in own { holders[w, default: []].append(i) }
                slot[idea.id] = i
            }
            self.words = words
            self.holders = holders
            self.slot = slot
        }

        /// The echoes of `text`, typed in the idea `current` (nil: a new
        /// one, made `made`): older ideas only, never `current` or one in
        /// `connected`; at most `limit`, best first.
        func echoes(of text: String, current: ID?, made: Double, connected: Set<ID> = [],
                    limit: Int = IdeaEchoes.limit) -> [ID] {
            let own: Int? = current.flatMap { slot[$0] }
            let others: Int = ideas.count - (own == nil ? 0 : 1)
            let ceiling: Int = Swift.max(2, others / 20)
            var score: [Int: Double] = [:]
            for w in IdeaEchoes.words(text) {
                guard let list = holders[w] else { continue }
                let count: Int = list.count - ((own.map { words[$0].contains(w) } ?? false) ? 1 : 0)
                guard count >= 1, count <= ceiling else { continue }
                let weight: Double = 1 / Double(count)
                for i in list where i != own {
                    let idea = ideas[i]
                    guard idea.made <= made, !connected.contains(idea.id) else { continue }
                    score[i, default: 0] += weight
                }
            }
            let ranked = score.sorted { a, b in
                if abs(a.value - b.value) > 1e-9 { return a.value > b.value }
                let x = ideas[a.key], y = ideas[b.key]
                if x.made != y.made { return x.made > y.made }
                return x.key < y.key
            }
            return ranked.prefix(limit).map { ideas[$0.key].id }
        }
    }
}
