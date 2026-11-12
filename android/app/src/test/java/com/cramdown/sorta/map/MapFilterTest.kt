package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The map's filter (S05i): iOS's GraphFilter.admits, shown(by:) and linkShown (GraphMotion.swift:7-27,
// :1356-1380), GraphPerfGraph.mask's note cases (GraphPerfTests.swift:203-223), stepPops and stepLinks
// (:1913-1985), and the summaries' and bundles' counts. Made-up fixtures, nothing medical.
class MapFilterTest {
    private val N = ThemeBodyKind.NOTE
    private val F = ThemeBodyKind.FOLDER
    private val H = ThemeBodyKind.HOME

    // 0 home; 1 folder A (top), 2 folder B (top), 3 folder A1 inside A; notes:
    // 4 page in A, 5 idea in A1, 6 idea in B (unlinked), 7 page in B, 8 idea in A (moon of 4 in the Universe)
    private val kinds = arrayOf(H, F, F, F, N, N, N, N, N)
    private val pages = booleanArrayOf(false, false, false, false, true, false, false, true, false)
    private val roots = arrayOf<String?>(null, "a", "b", "a", "a", "a", "b", "b", "a")
    private val parents = intArrayOf(-1, 0, 0, 1, 1, 3, 2, 2, 4)
    private val links = intArrayOf(4, 5, 5, 7, 7, 8)
    private fun bodies(universe: Boolean = false) = MapFilterBodies(kinds, pages, roots, parents, links, universe)

    @Test fun admitsEachChoice() {
        assertTrue(MapFilter.All.admits(true, null, 0))
        assertTrue(MapFilter.Pages.admits(true, "x", 0)); assertFalse(MapFilter.Pages.admits(false, "x", 3))
        assertTrue(MapFilter.Ideas.admits(false, "x", 0)); assertFalse(MapFilter.Ideas.admits(true, "x", 3))
        assertTrue(MapFilter.Linked.admits(false, null, 1)); assertFalse(MapFilter.Linked.admits(false, null, 0))
        assertTrue("a folder id in any case", MapFilter.Folder("ABC").admits(false, "abc", 0))
        assertFalse("a note in another folder", MapFilter.Folder("abc").admits(false, "def", 5))
        assertFalse("a note in no folder", MapFilter.Folder("abc").admits(false, null, 5))
        assertFalse(MapFilter.All.isFiltering); assertTrue(MapFilter.Linked.isFiltering)
    }

    @Test fun pagesIdeasLinkedAndAFolder() {
        val b = bodies()
        assertEquals(listOf(0, 0, 0, 0, 1, 2, 0, 2, 1), b.degree.toList())
        assertEquals(listOf(4, 7), shownNotes(b.visibility(MapFilter.Pages)))
        assertEquals(listOf(5, 6, 8), shownNotes(b.visibility(MapFilter.Ideas)))
        assertEquals("the unlinked idea hides", listOf(4, 5, 7, 8), shownNotes(b.visibility(MapFilter.Linked)))
        assertEquals("folder A's notes, at any depth", listOf(4, 5, 8), shownNotes(b.visibility(MapFilter.Folder("A"))))
        assertEquals(2, b.shownNotes(MapFilter.Pages))
        assertEquals(5, b.visibility(MapFilter.All).notes)
        assertEquals(0, b.visibility(MapFilter.Folder("nowhere")).notes)
    }

    @Test fun aFolderShowsUnderEverythingOrWhileAMemberShows() {
        val b = bodies()
        assertTrue(b.visibility(MapFilter.All).shown.all { it })
        val pagesOnly = b.visibility(MapFilter.Pages)
        assertTrue("home: a page shows", pagesOnly.shown[0])
        assertTrue("A: its page shows", pagesOnly.shown[1])
        assertTrue("B: its page shows", pagesOnly.shown[2])
        assertFalse("A1: its only note is an idea", pagesOnly.shown[3])
        val inB = b.visibility(MapFilter.Folder("b"))
        assertFalse("A hides with none of its notes", inB.shown[1] || inB.shown[3])
        assertTrue(inB.shown[0] && inB.shown[2])
        val none = b.visibility(MapFilter.Folder("nowhere"))
        assertFalse("nothing passes: no folder, no home", none.shown.any { it })
    }

    @Test fun aLinkOnlyWithBothEndsShownAndNeitherAGhost() {
        val b = bodies()
        // links: 4-5, 5-7, 7-8
        assertArrayEquals(booleanArrayOf(true, true, true), b.visibility(MapFilter.All).links)
        assertArrayEquals("pages 4 and 7 share no link", booleanArrayOf(false, false, false), b.visibility(MapFilter.Pages).links)
        assertArrayEquals(booleanArrayOf(true, false, false), b.visibility(MapFilter.Folder("a")).links)
        // the Universe: idea 8 is page 4's moon, so a filter hiding 4 but showing 8 keeps 4 as a ghost
        val u = bodies(universe = true).visibility(MapFilter.Ideas)
        assertTrue("a hidden planet with a shown moon stays", u.shown[4] && u.ghost[4])
        assertFalse("a ghost is not reachable", u.reachable(4))
        assertTrue(u.reachable(5) && !u.reachable(7))
        assertArrayEquals("no link to a ghost", booleanArrayOf(false, false, false), u.links)
        assertEquals(0.3f, MapFilterView.GHOST_ALPHA)
        // elsewhere there are no ghosts
        val plain = bodies().visibility(MapFilter.Ideas)
        assertFalse(plain.ghost.any { it }); assertFalse(plain.shown[4])
    }

    @Test fun showLinksKeepsOnlyTheChosenBodysOwn() {
        val b = bodies()
        assertArrayEquals(booleanArrayOf(true, true, false), b.visibility(MapFilter.All, linksFor = 5).links)
        assertArrayEquals("its own, while both ends show", booleanArrayOf(true, false, false), b.visibility(MapFilter.Folder("a"), linksFor = 5).links)
        assertArrayEquals(booleanArrayOf(false, false, false), b.visibility(MapFilter.All, linksFor = 6).links)
    }

    @Test fun performancesMaskOverNotes() {
        // GraphPerfTests.swift:203-223, the note cases: pages, ideas, linked, a folder, and an unknown folder
        val p = booleanArrayOf(true, false, false, true)
        val r = arrayOf<String?>("f1", "f1", "f2", null)
        val d = intArrayOf(0, 2, 0, 1)
        assertNull("Everything: no mask", MapFilter.perfMask(MapFilter.All, p, r, d))
        assertArrayEquals(booleanArrayOf(true, false, false, true), MapFilter.perfMask(MapFilter.Pages, p, r, d))
        assertArrayEquals(booleanArrayOf(false, true, true, false), MapFilter.perfMask(MapFilter.Ideas, p, r, d))
        assertArrayEquals(booleanArrayOf(false, true, false, true), MapFilter.perfMask(MapFilter.Linked, p, r, d))
        assertArrayEquals(booleanArrayOf(true, true, false, false), MapFilter.perfMask(MapFilter.Folder("F1"), p, r, d))
        assertArrayEquals("an unknown folder hides everything", BooleanArray(4), MapFilter.perfMask(MapFilter.Folder("zz"), p, r, d))
    }

    @Test fun topFoldersAreTheGalaxiesByNameInTheUniverse() {
        val tops = listOf("Beta", "Alpha")
        assertEquals("elsewhere the book's top level, as given", tops, MapFilter.topFolders(tops, null, { it }, naturalOrder()))
        assertEquals(listOf("Folder 2", "Folder 10"), MapFilter.topFolders(tops, listOf("Folder 10", "Folder 2"), { it },
            com.cramdown.sorta.ideas.NaturalOrder))
    }

    @Test fun animationsOffEverythingAtOnce() {
        val pop = FilterPop(3, lively = false)
        pop.apply(booleanArrayOf(true, false, true))
        assertTrue(pop.gone(1)); assertEquals(0f, pop.scale(1)); assertFalse(pop.moving)
        assertFalse(pop.step(0.016f))
        pop.apply(null)
        assertFalse(pop.gone(1)); assertEquals(1f, pop.scale(1)); assertEquals(0f, pop.bloom(1))
        val links = FilterLinkGrowth(lively = false)
        assertEquals(0f, links.level(7L, false)); assertEquals(1f, links.level(7L, true))
        assertFalse(links.step(0.016f) { false })
        // instant (Performance) on a lively map: at once too
        val instant = FilterPop(2, lively = true)
        instant.apply(booleanArrayOf(false, true), instant = true)
        assertTrue(instant.gone(0)); assertFalse(instant.moving)
    }

    @Test fun livelyShrinksAwayAndBloomsBack() {
        val pop = FilterPop(2, lively = true)
        pop.apply(booleanArrayOf(false, true))
        assertTrue(pop.moving)
        var t = 0f
        var overshoot = false
        while (pop.step(1f / 60f) && t < 3f) {
            t += 1f / 60f
            if (pop.scale(0) < 0f) overshoot = true
        }
        assertTrue("gone within a second (ζ 1, ω 15): $t", pop.gone(0) && t < 1f)
        assertFalse("shrinking never overshoots", overshoot)
        assertEquals(1f, pop.scale(1))
        pop.apply(null)
        pop.step(1f / 60f)
        assertFalse(pop.gone(0))
        assertTrue("a bloom of light as it comes back", pop.bloom(0) > 0.9f)
        var peak = 0f
        t = 0f
        while (pop.step(1f / 60f) && t < 5f) { t += 1f / 60f; peak = maxOf(peak, pop.scale(0)) }
        assertTrue("it grows past whole and settles (ζ 0.42): $peak", peak > 1.05f && peak < 1.4f)
        assertEquals(1f, pop.scale(0), 1e-3f)
        assertEquals(0f, pop.bloom(0), 1e-3f)
        assertEquals(15f, FilterPop.OMEGA); assertEquals(0.42f, FilterPop.ZETA_GROW); assertEquals(1f, FilterPop.ZETA_SHRINK)
        assertEquals(0.35f, FilterPop.BLOOM_SECONDS); assertEquals(0.02f, FilterPop.HIDE_AT)
    }

    @Test fun livelyLinksDrawBackAndGrowAgain() {
        val links = FilterLinkGrowth(lively = true)
        assertEquals("a wanted link is whole and not kept", 1f, links.level(1L, true))
        assertEquals(1f, links.level(1L, false))
        var t = 0f
        while (links.step(0.05f) { false } && t < 2f) t += 0.05f
        assertEquals(0f, links.level(1L, false))
        assertTrue("drawn back in about 0.45 s: $t", t in 0.35f..0.5f)
        t = 0f
        while (links.step(0.05f) { true } && t < 2f) t += 0.05f
        assertTrue("grown again in about 0.7 s: $t", t in 0.6f..0.8f)
        assertEquals(1f, links.level(1L, true))
        assertTrue("whole again, nothing kept", links.idle)
    }

    @Test fun summariesAndBundlesCountOnlyWhatShows() {
        val n = 40
        val positions = FloatArray(n * 3) { (it * 37 % 101).toFloat() / 10f }
        val edges = IntArray(n * 2) { k -> if (k % 2 == 0) k / 2 else (k / 2 + 7) % n }
        val graph = MapGraph(positions, ByteArray(n), edges, List(n) { "n$it" }, List(n) { "id$it" })
        val tree = Octree.build(graph, 4)
        val all = FilterSlice.of(tree, BooleanArray(n) { true })
        for (c in 0 until tree.count.size) assertEquals("cell $c", tree.count[c], all.cellCount[c])
        assertEquals(tree.bundleCount.sum(), all.bundleCount.sum())
        val half = BooleanArray(n) { it % 2 == 0 }
        val some = FilterSlice.of(tree, half)
        assertEquals("the root holds the shown only", n / 2, some.cellCount[Octree.ROOT])
        val none = FilterSlice.of(tree, BooleanArray(n))
        assertTrue(none.cellCount.all { it == 0 } && none.bundleCount.all { it == 0 })
    }

    @Test fun theEmptyLineCountsTheStoredNotesAndAClosedCellHides() {
        // the Neurons with every cell closed (GraphNeurons.swift:430): home, cells A and B, no idea drawn
        val closed = MapFilterBodies(arrayOf(H, F, F), BooleanArray(3), arrayOf(null, "a", "b"), intArrayOf(-1, 0, 0), IntArray(0))
        // the book: two ideas in A (linked to each other), a page in B
        val stored = MapFilterNotes.of(listOf("n1", "n2", "n3"), { it == 2 }, { if (it < 2) "a" else "b" }, listOf("N1" to "n2"))
        assertEquals(listOf(1, 1, 0), stored.degree.toList())
        val inA = closed.visibility(MapFilter.Folder("A"), stored = stored)
        assertEquals("the empty line counts the book's notes, as iOS's shownCount", 2, inA.notes)
        assertFalse("a closed cell has no members, so it hides under a filter, as iOS's", inA.shown.any { it })
        assertEquals(0, closed.visibility(MapFilter.Folder("A")).notes)
        assertEquals(1, closed.visibility(MapFilter.Pages, stored = stored).notes)
        assertEquals(2, closed.visibility(MapFilter.Linked, stored = stored).notes)
        assertEquals(0, closed.visibility(MapFilter.Folder("zz"), stored = stored).notes)
        assertTrue("Everything shows the closed cells", closed.visibility(MapFilter.All, stored = stored).shown.all { it })
        assertEquals(3, stored.shown(MapFilter.All))
    }

    @Test fun neuronsCountEachIdeaByTheCellItIsDrawnIn() {
        // two cells, A and B, each with a page and two linked ideas (iOS shownCount's universe branch,
        // Graph3DView.swift:861-862, reading galaxyOf, GraphThemeScene.swift:191)
        fun id(n: Int) = "00000000-0000-4000-8000-" + String.format("%012X", n)
        val folders = listOf(UniverseFolder(id(900), "A", null), UniverseFolder(id(901), "B", null))
        val notes = ArrayList<UniverseNote>()
        for (f in 0 until 2) for (k in 0 until 3) notes.add(UniverseNote(id(910 + 10 * f + k), "n$f$k", k == 0, id(900 + f), 80, (10 * f + k).toDouble()))
        val edges = listOf(UniverseEdge(id(911), id(912)), UniverseEdge(id(921), id(922)))
        val input = UniverseInput(notes, folders, edges, seedByName = true)
        fun stored(plan: ThemePlan): MapFilterNotes {
            val drawn = MapFilterNotes.drawnIn(plan)
            return MapFilterNotes.of(notes.map { it.id }, { notes[it].isPage }, { drawn[notes[it].id.uppercase()] }, edges.map { it.a to it.b })
        }
        val closed = stored(GraphNeurons.plan(input))
        assertEquals("every cell closed: no idea is drawn in Garden's cell, so the empty line shows, as iOS's", 0, closed.shown(MapFilter.Folder(id(900))))
        assertEquals(0, closed.shown(MapFilter.Folder(id(901))))
        assertEquals("kind and links do not need a cell", 2, closed.shown(MapFilter.Pages))
        assertEquals(4, closed.shown(MapFilter.Linked))
        assertEquals(6, closed.shown(MapFilter.All))
        val openA = stored(GraphNeurons.plan(input.copy(open = listOf(id(900)))))
        assertEquals("A open: its three notes are drawn in its cell", 3, openA.shown(MapFilter.Folder(id(900))))
        assertEquals("matched without case", 3, openA.shown(MapFilter.Folder(id(900).lowercase())))
        assertEquals("B, still closed, counts none", 0, openA.shown(MapFilter.Folder(id(901))))
    }

    @Test fun everythingAfterAFilterReframesOnlyAndAFolderFlies() {
        assertEquals("the page's first filter moves nothing", FilterMove.NONE, MapFilter.move(null, MapFilter.All, universe = true))
        assertEquals(FilterMove.FLY, MapFilter.move(MapFilter.All, MapFilter.Folder("a"), universe = true))
        assertEquals(FilterMove.FLY, MapFilter.move(MapFilter.Folder("a"), MapFilter.Folder("b"), universe = true))
        assertEquals("Everything after a filter frames the map, not Recentre", FilterMove.REFRAME, MapFilter.move(MapFilter.Pages, MapFilter.All, universe = true))
        assertEquals(FilterMove.REFRAME, MapFilter.move(MapFilter.Folder("a"), MapFilter.All, universe = true))
        assertEquals(FilterMove.NONE, MapFilter.move(MapFilter.All, MapFilter.Pages, universe = true))
        assertEquals(FilterMove.NONE, MapFilter.move(MapFilter.All, MapFilter.All, universe = true))
        assertEquals("Space under one style and Performance: no camera move", FilterMove.NONE, MapFilter.move(MapFilter.All, MapFilter.Folder("a"), universe = false))
        assertEquals(FilterMove.NONE, MapFilter.move(MapFilter.Pages, MapFilter.All, universe = false))
    }

    @Test fun aSpaceBodyComingBackBloomsInItsGlows() {
        // the bloom reaches the body shader's vMark.w, chunk order
        val n = 3
        val positions = FloatArray(n * 3).also { for (i in 0 until n) it[3 * i] = i * 2f }
        val edges = intArrayOf(0, 1)
        val tree = Octree.build(MapGraph(positions, SpaceStyle.assign(n, edges), edges), leafCapacity = 4, palette = SpacePalette.rgb)
        val bodies = SpaceBodies(tree, nodeRadius = 0.5f)
        val plan = FramePlan(tree.leafCount)
        for (leaf in 0 until tree.leafCount) { plan.leafState[leaf * 4 + 3] = 1f; plan.detailLeaves.add(leaf) }
        val camera = OrbitCamera(target = Vec3(2f, 0f, 0f), distance = 10f, yaw = 0f, pitch = 0f)
        val vp = Mat4.multiply(camera.projection(1f), camera.view())
        fun frame() = bodies.update(plan, vp, camera.eye(), camera.right(), camera.up(), camera.focalPixels(1000f), 64, 0f)
        assertEquals(n, frame())
        for (j in 0 until bodies.count) assertEquals("no bloom with no filter", 0f, bodies.data[j * SpaceBodies.STRIDE + 19], 0f)
        // a body hidden, then shown again: the pop's bloom, read into the chunk order
        val pop = FilterPop(n, lively = true)
        pop.apply(booleanArrayOf(true, false, true), instant = true)
        pop.apply(null)
        pop.step(1f / 60f)
        val back = 1
        assertTrue(pop.bloom(back) > 0.9f)
        bodies.filterBloom = FloatArray(n) { k -> pop.bloom(tree.order[k]) }
        assertEquals(n, frame())
        for (j in 0 until bodies.count) {
            val scene = tree.order[bodies.nodes[j]]
            assertEquals("scene body $scene", pop.bloom(scene), bodies.data[j * SpaceBodies.STRIDE + 19], 0f)
        }
        // each style's shader brightens its glows to iOS's opacity, min(0.85 + 0.35 b, 1) over 0.85, once, and nothing else
        val line = "    rp_col = rp_col * (min(0.85 + 0.35 * vMark.w, 1.0) / 0.85);\n"
        assertEquals(line, SpaceBodies.BLOOM_LINE)
        for (style in SpaceStyle.entries) {
            val plain = SpaceShaders.fragment(style)
            val bloomed = SpaceBodies.bloomed(plain)
            assertEquals("$style: one line added", plain.lines().size + 1, bloomed.lines().size)
            val head = bloomed.indexOf(SpaceBodies.GLOW_HEAD)
            assertTrue("$style: inside every glow's way out", bloomed.startsWith(line, head + SpaceBodies.GLOW_HEAD.length))
            assertEquals(plain, bloomed.replace(line, ""))
        }
        // and swells each glow by iOS's 1 + 0.6 b (GraphMotion.swift:2128), its quad with it
        assertTrue(SpaceShaders.fragment(SpaceStyle.SUN).contains("float rpSwell() { return 1.0 + 0.6 * vMark.w; }"))
        assertTrue("the quad swells", SpaceShaders.bodyVertex.contains("(uExtent * aCenter.w * (1.0 + 0.6 * aMark.w))"))
        val swelled = mapOf(
            SpaceStyle.BLACK_HOLE to listOf("rpPlane(c, r * 2.1 * rpSwell())"),
            SpaceStyle.SUN to listOf("rpPlane(c, R * 4.0 * rpSwell())"),
            SpaceStyle.ROCKY to listOf("halo(rpPlane(c, R * 1.5 * rpSwell())"),
            SpaceStyle.GAS_GIANT to listOf("halo(rpPlane(c, R * 1.5 * rpSwell())"),
            SpaceStyle.PULSAR to listOf("rpPlane(c, r * 2.5 * rpSwell())"),
            SpaceStyle.COMET to listOf("rpPlane(c, r * 1.6 * rpSwell())"))
        assertEquals(SpaceStyle.entries.toSet(), swelled.keys)
        for ((style, glows) in swelled) {
            val main = SpaceShaders.fragment(style).substringAfter("void main() {")
            for (g in glows) assertTrue("$style swells $g", main.contains(g))
            // every glow's plane in main swells; the black hole's shadow keeps the body's size
            val planes = Regex("""rpPlane\(c, [^)]*\)""").findAll(main).map { it.value }.toList()
            for (p in planes) assertTrue("$style: $p", p.endsWith(" * rpSwell()") || (style == SpaceStyle.BLACK_HOLE && p == "rpPlane(c, r * 2.1)"))
        }
    }

    @Test fun manyLinksDrawBackEachOnItsOwn() {
        val links = FilterLinkGrowth(lively = true)
        val keys = longArrayOf(Octree.pairKey(9, 12), 3L, Octree.pairKey(0, 1), 7L, 1L shl 40)
        for (k in keys) assertEquals(1f, links.level(k, false))
        assertFalse(links.idle)
        // half a draw-back for all, then 7 and 3 wanted again: they grow, the rest go on drawing back
        links.step(LinkGrowth.DRAW_BACK / 2) { false }
        val half = links.level(7L, false)
        for (k in keys) assertEquals("key $k", half, links.level(k, false), 0f)
        assertTrue(half > 0f && half < 1f)
        while (links.step(0.02f) { it == 7L || it == 3L }) Unit
        assertEquals(1f, links.level(7L, true)); assertEquals(1f, links.level(3L, true))
        assertEquals(0f, links.level(Octree.pairKey(0, 1), false))
        assertEquals(0f, links.level(1L shl 40, false))
        // all wanted: each grows back and none is kept
        while (links.step(0.02f) { true }) Unit
        assertTrue(links.idle)
        for (k in keys) assertEquals(1f, links.level(k, true))
        assertTrue(links.idle)
    }

    private fun shownNotes(v: MapFilterView) = (0 until v.shown.size).filter { kinds[it] == N && v.shown[it] && !v.ghost[it] }
}
