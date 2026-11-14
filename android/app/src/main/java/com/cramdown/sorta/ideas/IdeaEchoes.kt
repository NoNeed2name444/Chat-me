package com.cramdown.sorta.ideas

// Echoes (S06c, the plugins plan's idea 4): from the words being typed, the older ideas that share an
// uncommon one. All on the phone: no AI, no network, nothing learnt. iOS's IdeaEchoes.swift is the same
// rule, read from the same cases (ios/UnitTests/Suites/IdeaShapeCases.json); IdeaEchoesTest reads the
// stop words from that file, so the two lists are one.
//
// A word is a run of letters or digits, lowercased, with apostrophes dropped ("don't" is "dont"), 3
// letters or more, not all digits and not a stop word. A word is uncommon when 1 to max(2, N / 20) of the
// other N ideas hold it. Each older idea holding one scores 1 / that count for each; the best come first,
// then the newer, then by id.
object IdeaEchoes {
    /** The most echoes offered at once. */
    const val LIMIT = 5

    /** The words too common to say anything: iOS's list, word for word (IdeaEchoesTest). */
    val stopWords: Set<String> = setOf(
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
    )

    /** The words of a text that can count, each once, in the order met. */
    fun words(text: String): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val word = StringBuilder()
        fun flush() {
            val w = word.toString()
            if (w.length >= 3 && !w.all { it.isDigit() } && w !in stopWords && seen.add(w)) out.add(w)
            word.setLength(0)
        }
        for (ch in text.lowercase()) {
            if (ch == '\'' || ch == '’') continue
            if (ch.isLetterOrDigit()) word.append(ch) else flush()
        }
        flush()
        return out
    }

    /** One idea as the matcher sees it: its id, its words, when it was made (for "older" and ties). */
    data class Idea(val id: String, val text: String, val made: Double)

    /** Every idea's words and, for each word, which ideas hold it: built once, asked many times while typing. */
    class Index(val ideas: List<Idea>) {
        private val words: List<Set<String>>
        private val holders = HashMap<String, MutableList<Int>>()
        private val slot = HashMap<String, Int>()

        init {
            val all = ArrayList<Set<String>>(ideas.size)
            ideas.forEachIndexed { i, idea ->
                val own = words(idea.text)
                all.add(own.toHashSet())
                for (w in own) holders.getOrPut(w) { ArrayList() }.add(i)
                slot[idea.id] = i
            }
            words = all
        }

        /**
         * The echoes of [text], typed in the idea [current] (null: a new one, made [made]): older ideas only,
         * never [current] or one in [connected]; at most [limit], best first.
         */
        fun echoes(text: String, current: String?, made: Double, connected: Set<String> = emptySet(), limit: Int = LIMIT): List<String> {
            val own = current?.let { slot[it] }
            val others = ideas.size - (if (own == null) 0 else 1)
            val ceiling = maxOf(2, others / 20)
            val score = HashMap<Int, Double>()
            for (w in words(text)) {
                val list = holders[w] ?: continue
                val count = list.size - (if (own != null && w in words[own]) 1 else 0)
                if (count < 1 || count > ceiling) continue
                val weight = 1.0 / count
                for (i in list) {
                    if (i == own) continue
                    val idea = ideas[i]
                    if (idea.made > made || idea.id in connected) continue
                    score[i] = (score[i] ?: 0.0) + weight
                }
            }
            return score.entries.sortedWith { a, b ->
                when {
                    kotlin.math.abs(a.value - b.value) > 1e-9 -> b.value.compareTo(a.value)
                    ideas[a.key].made != ideas[b.key].made -> ideas[b.key].made.compareTo(ideas[a.key].made)
                    else -> ideas[a.key].id.compareTo(ideas[b.key].id)
                }
            }.take(limit).map { ideas[it.key].id }
        }
    }
}
