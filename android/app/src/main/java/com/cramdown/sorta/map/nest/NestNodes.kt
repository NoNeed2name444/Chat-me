package com.cramdown.sorta.map.nest

// The nested maps' generic tree of nodes (S11), shared by Tree and Fractal. Node 0 is the trunk; every other node has a
// parent, a title, a colour group and its children, so the layout knows nothing of folders or
// ideas and a deeper store (sub-ideas, folders in ideas) lays out the day it comes. Today it is fed
// the trunk, the folders and the ideas (NestNodes.build). Pure Kotlin, the same as iOS's
// NestNodes.swift line for line, tested in TreeLayoutTest and TreeSceneTest.

/** One folder as the tree is fed it: its parent, or null at the top level. */
data class NestFolderIn(val id: String, val parentId: String?, val name: String)

/** One idea as the tree is fed it: its folder, or null in none, and when it was made (its order among its neighbours). */
data class NestIdeaIn(val id: String, val folderId: String?, val title: String, val createdAt: Long)

/**
 * A tree of [size] nodes in parent order (a parent always comes before its children, children in
 * their order): [ids] (the trunk's is ""), [parent] (the trunk's -1), [titles], [folder] (a part that
 * holds others or can) and [tone] (the NoteTone of the top-level folder it grows from, -1 for none).
 */
class NestNodes(
    val ids: Array<String>, val parent: IntArray, val titles: Array<String>, val folder: BooleanArray, val tone: IntArray,
) {
    val size: Int = parent.size
    /** Each node's children: [childCount], from [childStart] in [childList]. */
    val childCount = IntArray(size)
    val childStart = IntArray(size + 1)
    val childList = IntArray(size)
    val depth = IntArray(size)
    /** Its colour group: a tone 0 to 6, [LOOSE], or [TRUNK]. */
    val group = IntArray(size)
    /** The leaves under it, and that to the 0.7: what its branch's length and place in a fan go by. */
    val weight = DoubleArray(size)
    val weightPow = DoubleArray(size)
    /** Depth-first order: a node's subtree is [tin] up to [tout] in [order]. */
    val tin = IntArray(size)
    val tout = IntArray(size)
    val order = IntArray(size)

    init {
        for (i in 1 until size) childCount[parent[i]]++
        for (i in 0 until size) childStart[i + 1] = childStart[i] + childCount[i]
        val fill = childStart.copyOf(size)
        for (i in 1 until size) childList[fill[parent[i]]++] = i
        for (i in 1 until size) depth[i] = depth[parent[i]] + 1
        for (i in 0 until size) group[i] = if (i == 0) TRUNK else if (tone[i] >= 0) tone[i] % TONES else LOOSE
        for (i in size - 1 downTo 0) {
            if (childCount[i] == 0) weight[i] = 1.0
            if (i > 0) weight[parent[i]] += weight[i]
        }
        for (i in 0 until size) weightPow[i] = Math.pow(weight[i], 0.7)
        // iterative, so a tall tree can't run out of stack
        var t = 0
        val stack = IntArray(size + 1)
        val next = IntArray(size)
        var top = 0
        stack[0] = 0; tin[0] = 0; order[t++] = 0
        while (top >= 0) {
            val i = stack[top]
            if (next[i] < childCount[i]) {
                val c = childList[childStart[i] + next[i]++]
                tin[c] = t; order[t++] = c; stack[++top] = c
            } else { tout[i] = t; top-- }
        }
    }

    fun child(i: Int, k: Int): Int = childList[childStart[i] + k]

    /** Whether [q] is [p] or grows from it. */
    fun within(p: Int, q: Int): Boolean = tin[p] <= tin[q] && tin[q] < tout[p]

    private val lookup: HashMap<String, Int> by lazy { HashMap<String, Int>(size * 2).also { m -> for (i in size - 1 downTo 0) m[ids[i]] = i } }

    /** The node of [id], or -1 (ids compared as given). */
    fun index(id: String): Int = lookup[id] ?: -1

    companion object {
        /** NoteTone's seven colours. */
        const val TONES = 7
        /** The grey of ideas in no folder. */
        const val LOOSE = 7
        /** The trunk's own bark. */
        const val TRUNK = 8
        const val GROUPS = 9

        /**
         * The trunk ([trunk], its title), the folders and the ideas: each top-level folder a bough
         * off the trunk in [folders]' order among its neighbours (the caller's, the Sort screen's
         * order), its folders and ideas on it; each idea in no folder a bough of its own, after the
         * folders. A folder's folders come before its ideas, and ideas oldest first. A folder whose
         * parent is missing, or in a loop, grows from the trunk with no colour; an idea whose folder
         * is missing is in none. Each top-level folder's tone is its place among them, as NoteTone's.
         */
        fun build(trunk: String, folders: List<NestFolderIn>, ideas: List<NestIdeaIn>): NestNodes {
            val known = HashMap<String, NestFolderIn>()
            for (f in folders) known[f.id] = f
            // each folder's folders in the order given; the trunk's are the top level
            val sub = HashMap<String, MutableList<NestFolderIn>>()
            val tops = ArrayList<NestFolderIn>()
            for (f in folders) {
                val p = f.parentId
                if (p == null) tops.add(f) else if (known.containsKey(p)) sub.getOrPut(p) { ArrayList() }.add(f)
            }
            val held = HashMap<String, MutableList<NestIdeaIn>>()
            val loose = ArrayList<NestIdeaIn>()
            for (idea in ideas) {
                val f = idea.folderId
                if (f != null && known.containsKey(f)) held.getOrPut(f) { ArrayList() }.add(idea) else loose.add(idea)
            }
            val oldestFirst = compareBy<NestIdeaIn> { it.createdAt }.thenBy { it.id.uppercase() }
            for (list in held.values) list.sortWith(oldestFirst)
            loose.sortWith(oldestFirst)
            // what grows from the trunk: the top level, then each folder no top-level folder reaches
            // (its parent gone, or a loop), from the outermost of its unreached line
            val reached = HashSet<String>()
            fun reach(f: NestFolderIn) {
                val stack = ArrayList<NestFolderIn>().apply { add(f) }
                while (stack.isNotEmpty()) {
                    val g = stack.removeAt(stack.size - 1)
                    if (!reached.add(g.id)) continue
                    for (c in sub[g.id].orEmpty()) stack.add(c)
                }
            }
            for (f in tops) reach(f)
            val strays = ArrayList<NestFolderIn>()
            for (f in folders) if (f.id !in reached) {
                var head = f
                val seen = HashSet<String>()
                while (true) {
                    val p = head.parentId ?: break
                    val up = known[p] ?: break
                    if (up.id in reached || !seen.add(head.id)) break
                    head = up
                }
                if (head.id in reached) continue
                strays.add(head); reach(head)
            }
            val ids = ArrayList<String>(); val parent = ArrayList<Int>(); val titles = ArrayList<String>()
            val folder = ArrayList<Boolean>(); val tone = ArrayList<Int>()
            fun add(id: String, p: Int, title: String, isFolder: Boolean, t: Int) {
                ids.add(id); parent.add(p); titles.add(title); folder.add(isFolder); tone.add(t)
            }
            add("", -1, trunk, true, -1)
            // breadth first, so a node's children sit together in their order: folders, then ideas
            val queue = ArrayDeque<Int>()
            val placed = HashSet<String>()
            tops.forEachIndexed { k, f -> if (placed.add(f.id)) { add(f.id, 0, f.name, true, k); queue.add(ids.size - 1) } }
            for (f in strays) if (placed.add(f.id)) { add(f.id, 0, f.name, true, -1); queue.add(ids.size - 1) }
            for (idea in loose) add(idea.id, 0, idea.title, false, -1)
            while (queue.isNotEmpty()) {
                val me = queue.removeFirst()
                val id = ids[me]
                for (c in sub[id].orEmpty()) if (placed.add(c.id)) { add(c.id, me, c.name, true, tone[me]); queue.add(ids.size - 1) }
                for (idea in held[id].orEmpty()) add(idea.id, me, idea.title, false, tone[me])
            }
            return NestNodes(ids.toTypedArray(), parent.toIntArray(), titles.toTypedArray(), folder.toBooleanArray(), tone.toIntArray())
        }
    }
}
