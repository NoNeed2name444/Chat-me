package com.cramdown.sorta.map

import com.cramdown.sorta.map.NeuronHierarchyTest.Companion.fixedID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// iOS CosmicHierarchyTests.swift (T1-T14 and the owner's cases), its checks and bounds as they are,
// on Android's port of the Universe planner (UniversePlan.kt, S05h): the preview's roles, sizes and
// labels (T1), the size ladder over 200 random vaults (T2), the same notes giving the same plan
// (T3), nothing meeting over ten minutes (T4), the envelope (T5), the edge cases (T6-T10), the
// orbits (T11), 1000 notes in under 300 ms (T12), words (T13) and link kinds (T14). T1 and T14 use
// NeuronPreviewData's copy of iOS's preview store. Then the parity with iOS's own planner, body by
// body (ios-dump.json, written by tools/universe_dump.swift on CI), and the comets.
class UniversePlanTest {
    private val failures = ArrayList<String>()
    private fun check(label: String, ok: Boolean, detail: String = "") { if (!ok) failures.add("$label | $detail") }
    private fun done() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    // ---- helpers over a plan ----

    private fun bodyNamed(plan: UniversePlan, title: String): Int? = plan.bodies.indexOfFirst { it.title == title }.takeIf { it >= 0 }
    private fun titles(plan: UniversePlan, role: UniverseRole): Set<String> = plan.bodies.filter { it.role == role }.map { it.title }.toSet()
    private fun roleOf(plan: UniversePlan, title: String): UniverseRole? = bodyNamed(plan, title)?.let { plan.bodies[it].role }
    private fun near(a: Float, b: Float, tolerance: Float = 0.0006f) = abs(a - b) <= tolerance
    private fun distance(a: Vec3, b: Vec3): Float = (a - b).length()

    /** The radius two bodies must keep apart: the sphere, or a core's disk. */
    private fun keepOut(body: UniverseBody, grow: Float): Float {
        if (body.role == UniverseRole.GALAXY && !body.empty) return body.sphere * 2.5f
        val isNote = body.count == 0 && !body.role.isFolder
        return if (isNote) body.sphere * grow else body.sphere
    }

    /** Pairs of non-comet bodies that overlap at some time in 0..600 s. */
    private fun overlaps(plan: UniversePlan, grow: Float, step: Double = 5.0): List<String> {
        val bad = ArrayList<String>()
        val kept = plan.bodies.indices.filter { plan.bodies[it].role != UniverseRole.COMET && plan.bodies[it].role != UniverseRole.OORT }
        var t = 0.0
        while (t <= 600) {
            val at = kept.map { plan.position(it, t) }
            for (x in kept.indices) for (y in x + 1 until kept.size) {
                val a = plan.bodies[kept[x]]
                val b = plan.bodies[kept[y]]
                // a moon may pass through its own ringed planet's flat ring
                val family = a.parent == kept[y] || b.parent == kept[x]
                if (family && (a.ringed || b.ringed)) continue
                val limit = keepOut(a, grow) + keepOut(b, grow)
                val gap = distance(at[x], at[y])
                if (gap < limit - 1e-4f && bad.size < 5) bad.add("${a.title}/${b.title} t=$t d=$gap < $limit")
            }
            t += step
        }
        return bad
    }

    private fun envelopeHolds(plan: UniversePlan): List<String> {
        val first = plan.envelope.firstOrNull() ?: return emptyList()
        var lo = first
        var hi = first
        for (p in plan.envelope) {
            lo = Vec3(min(lo.x, p.x), min(lo.y, p.y), min(lo.z, p.z))
            hi = Vec3(max(hi.x, p.x), max(hi.y, p.y), max(hi.z, p.z))
        }
        val bad = ArrayList<String>()
        var t = 0.0
        while (t <= 600) {
            for ((i, body) in plan.bodies.withIndex()) {
                val p = plan.position(i, t)
                val r = body.sphere + 0.001f
                val inside = p.x >= lo.x - r && p.y >= lo.y - r && p.z >= lo.z - r && p.x <= hi.x + r && p.y <= hi.y + r && p.z <= hi.z + r
                if (!inside && bad.size < 3) bad.add("${body.title} at t=$t: $p not in $lo...$hi")
            }
            t += 10
        }
        return bad
    }

    private val preview: UniversePlan by lazy { UniversePlan.of(NeuronHierarchyTest.preview) }
    private val bigVault: UniversePlan by lazy { UniversePlan.of(randomVault(2024uL, 220, 19)) }

    // ---- T1 the design preview's roles ----

    @Test fun t1PreviewRoles() {
        check("T1 two galaxies", titles(preview, UniverseRole.GALAXY) == setOf("Cardiology", "Examples"), "${titles(preview, UniverseRole.GALAXY)}")
        check("T1 three stars", titles(preview, UniverseRole.STAR) == setOf("Inguinal", "Femoral", "Anatomy"), "${titles(preview, UniverseRole.STAR)}")
        val expectedGas = setOf("Heart failure", "Groin hernia", "Inguinal canal", "Femoral hernia", "Indirect inguinal hernia",
            "Direct inguinal hernia", "Acute coronary syndrome", "Atrial fibrillation", "Murmurs")
        check("T1 nine gas giants", titles(preview, UniverseRole.GAS_GIANT) == expectedGas, "${titles(preview, UniverseRole.GAS_GIANT)}")
        val expectedRocky = setOf("Inferior epigastric vessels", "Internal ring test", "Canal boundaries", "Spermatic cord coverings",
            "Loop diuretics", "ACE inhibitors", "Hypokalaemia", "NSTEMI", "Troponin", "STEMI", "Heart sounds", "Syncope")
        check("T1 twelve rocky planets", titles(preview, UniverseRole.ROCKY) == expectedRocky, "${titles(preview, UniverseRole.ROCKY)}")
        val expectedMoons = mapOf("Hernia repair" to "Groin hernia", "LA/PM mnemonic" to "Canal boundaries", "Femoral canal" to "Femoral hernia",
            "Saphena varix" to "Femoral hernia", "BNP" to "Heart failure", "CHA2DS2-VASc" to "Atrial fibrillation")
        val foundMoons = preview.bodies.filter { it.role == UniverseRole.MOON }.associate { it.title to preview.bodies[it.parent].title }
        check("T1 six moons round their planets", foundMoons == expectedMoons, "$foundMoons")
        check("T1 one pulsar", titles(preview, UniverseRole.PULSAR) == setOf("Expansile cough impulse"), "${titles(preview, UniverseRole.PULSAR)}")
        check("T1 two comets", titles(preview, UniverseRole.COMET) == setOf("Pericarditis", "Richter's hernia"), "${titles(preview, UniverseRole.COMET)}")
        val ringedTitles = preview.bodies.filter { it.ringed }.map { it.title }.toSet()
        check("T1 only Heart failure is ringed", ringedTitles == setOf("Heart failure"), "$ringedTitles")
        val sizes = listOf("Cardiology" to 0.459f, "Examples" to 0.459f, "Inguinal" to 0.332f, "Femoral" to 0.320f, "Anatomy" to 0.299f,
            // gas giants by their real word counts: 272, 63, 50, 44, 43, 29 and under 15
            "Heart failure" to 0.24f, "Inguinal canal" to 0.22f, "Groin hernia" to 0.21f, "Femoral hernia" to 0.21f,
            "Indirect inguinal hernia" to 0.21f, "Direct inguinal hernia" to 0.20f, "Acute coronary syndrome" to 0.19f,
            "Atrial fibrillation" to 0.19f, "Murmurs" to 0.19f, "Expansile cough impulse" to 0.07f)
        val wrongSizes = sizes.mapNotNull { (title, size) ->
            val got = bodyNamed(preview, title)?.let { preview.bodies[it].sphere } ?: -1f
            if (near(got, size)) null else "$title $got not $size"
        }
        check("T1 sizes on the ladder", wrongSizes.isEmpty(), "$wrongSizes")
        val tiers = listOf("Inguinal" to 1, "Femoral" to 1, "Anatomy" to 2)
        val gotTiers = tiers.map { pair -> bodyNamed(preview, pair.first)?.let { preview.bodies[it].tier } ?: -1 }
        check("T1 star tiers: Inguinal and Femoral yellow-white, Anatomy orange", gotTiers == tiers.map { it.second }, "$gotTiers")
        val labels = listOf("Examples · 14 ideas", "Inguinal · 8 ideas", "Anatomy · 3 ideas", "Femoral · 3 ideas", "Cardiology · 14 ideas")
        val shown = preview.bodies.map { it.label }.toSet()
        check("T1 folder labels with counts", labels.all { it in shown }, "${labels.filter { it !in shown }}")
        check("T1 summary", preview.summary == "2 galaxies, 3 stars, 21 planets, 6 moons, 1 pulsar, 2 comets", preview.summary)
        val anatomy = bodyNamed(preview, "Anatomy")
        val inguinal = bodyNamed(preview, "Inguinal")
        check("T1 Anatomy stands beside Inguinal", anatomy != null && preview.bodies[anatomy].parent == inguinal)
        val heart = NeuronPreviewData.drafts.first { it.title == "Heart failure" }
        val heartWords = GraphUniverse.wordCount(heart.body)
        check("T1 Heart failure has 250 words or more", heartWords >= 250, "$heartWords")
        done()
    }

    // ---- T2 the ladder, over 200 random vaults ----

    @Test fun t2Ladder() {
        val ladderBad = ArrayList<String>()
        val containmentBad = ArrayList<String>()
        for (k in 0 until 200) {
            val plan = UniversePlan.of(randomVault(k.toULong() + 77uL, 300, 40))
            val lowest = HashMap<UniverseRole, Float>()
            val highest = HashMap<UniverseRole, Float>()
            for (body in plan.bodies) {
                var role = body.role
                if (role == UniverseRole.HOME) role = UniverseRole.STAR
                if (role == UniverseRole.OORT || role == UniverseRole.COMET) continue
                lowest[role] = min(lowest[role] ?: 9f, body.sphere)
                highest[role] = max(highest[role] ?: 0f, body.sphere)
                // every container holds bodies smaller than itself
                if (body.parent < 0) continue
                val holder = plan.bodies[body.parent]
                if (!(body.sphere < holder.sphere) && containmentBad.size < 3) containmentBad.add("vault $k: ${body.title} in ${holder.title}")
            }
            val ladder = listOf(UniverseRole.GALAXY, UniverseRole.STAR, UniverseRole.GAS_GIANT, UniverseRole.ROCKY, UniverseRole.MOON, UniverseRole.PULSAR)
            for ((a, b) in ladder.zipWithNext()) {
                val small = lowest[a] ?: continue
                val big = highest[b] ?: continue
                if (small <= big && ladderBad.size < 3) ladderBad.add("vault $k: $a $small <= $b $big")
            }
        }
        check("T2 ladder: core > star > gas > rocky > moon > pulsar", ladderBad.isEmpty(), "$ladderBad")
        check("T2 every container is bigger than anything it holds (a star than its own, strictly)", containmentBad.isEmpty(), "$containmentBad")
        done()
    }

    // ---- T3 determinism ----

    @Test fun t3Determinism() {
        val again = UniversePlan.of(NeuronHierarchyTest.preview)
        check("T3 planning twice gives identical bodies", again.bodies == preview.bodies)
        val base = randomVault(4242uL, 120, 12)
        val random = Random(99)
        val shuffled = UniverseInput(base.notes.shuffled(random), base.folders.shuffled(random),
            base.edges.shuffled(random).map { UniverseEdge(it.b, it.a) }, seedByName = false)
        val planA = UniversePlan.of(base)
        val planB = UniversePlan.of(shuffled)
        check("T3 shuffled input gives the same plan", planA.bodies == planB.bodies && planA.links == planB.links)
        check("T3 same shells", planA.shells == planB.shells)
        done()
    }

    // ---- T4 no overlap over ten minutes ----

    @Test fun t4NoOverlap() {
        check("T4 preview: no overlaps", overlaps(preview, 1f).isEmpty(), "${overlaps(preview, 1f)}")
        check("T4 preview: none with note spheres x1.5", overlaps(preview, 1.5f).isEmpty(), "${overlaps(preview, 1.5f)}")
        check("T4 big vault: no overlaps", overlaps(bigVault, 1f, 10.0).isEmpty(), "${overlaps(bigVault, 1f, 10.0)}")
        check("T4 big vault: none with x1.5", overlaps(bigVault, 1.5f, 10.0).isEmpty(), "${overlaps(bigVault, 1.5f, 10.0)}")
        val randomOverlaps = ArrayList<String>()
        for (k in 0 until 12) randomOverlaps += overlaps(UniversePlan.of(randomVault(k.toULong() + 500uL, 150, 20)), 1f, 20.0)
        check("T4 random vaults: no overlaps", randomOverlaps.isEmpty(), "${randomOverlaps.take(3)}")
        done()
    }

    // ---- T5 the envelope holds everything ----

    @Test fun t5Envelope() {
        check("T5 preview inside its envelope", envelopeHolds(preview).isEmpty(), "${envelopeHolds(preview)}")
        check("T5 big vault inside its envelope", envelopeHolds(bigVault).isEmpty(), "${envelopeHolds(bigVault)}")
        done()
    }

    // ---- T6 structure: cycles, missing parents, missing folders ----

    @Test fun t6Structure() {
        val fa = fixedID(501); val fb = fixedID(502); val fc = fixedID(503); val gone = fixedID(599)
        val plan = UniversePlan.of(UniverseInput(
            listOf(UniverseNote(fixedID(1), "n1", false, fa, 5, 1.0), UniverseNote(fixedID(2), "n2", true, fb, 500, 2.0),
                UniverseNote(fixedID(3), "n3", false, gone, 5, 3.0), UniverseNote(fixedID(4), "n4", false, fc, 5, 4.0)),
            listOf(UniverseFolder(fa, "A", fb), UniverseFolder(fb, "B", fa), UniverseFolder(fc, "C", gone)),
            emptyList()))
        check("T6 a two-folder cycle is cut at the smaller uuid", roleOf(plan, "A") == UniverseRole.GALAXY && roleOf(plan, "B") == UniverseRole.STAR,
            "A ${roleOf(plan, "A")}")
        check("T6 a folder with a missing parent is top level", roleOf(plan, "C") == UniverseRole.GALAXY)
        check("T6 a note in a missing folder is a comet", roleOf(plan, "n3") == UniverseRole.COMET)
        check("T6 notes in the cycle stay planets", roleOf(plan, "n1") == UniverseRole.ROCKY && roleOf(plan, "n2") == UniverseRole.GAS_GIANT)
        check("T6 bodies come after their parents", plan.bodies.indices.all { plan.bodies[it].parent < it })
        done()
    }

    // ---- T7 no folders at all ----

    @Test fun t7NoFolders() {
        val notes = (0 until 20).map { k -> UniverseNote(fixedID(k + 1), "Idea $k", k % 3 == 0, null, 10 + 7 * k, k.toDouble()) }
        val edges = (0 until 18 step 3).map { k -> UniverseEdge(fixedID(k + 1), fixedID(k + 2)) }
        val flat = UniversePlan.of(UniverseInput(notes, emptyList(), edges))
        val homes = flat.bodies.filter { it.role == UniverseRole.HOME }
        check("T7 one home star", homes.size == 1 && homes.first().id == GraphUniverse.HOME_ID)
        check("T7 its label", homes.firstOrNull()?.label == "Ideas · 20 ideas", homes.firstOrNull()?.label ?: "")
        val banned = setOf(UniverseRole.COMET, UniverseRole.OORT, UniverseRole.PULSAR, UniverseRole.GALAXY)
        check("T7 no comets or pulsars", flat.bodies.none { it.role in banned })
        val orbitsHome = flat.bodies.drop(1).all { body -> body.parent == 0 || (body.role == UniverseRole.MOON && flat.bodies[body.parent].parent == 0) }
        check("T7 every note orbits it or its planets", orbitsHome)
        check("T7 summary", flat.summary.startsWith("1 star, "), flat.summary)
        done()
    }

    // ---- T8 comets ----

    private fun cometInput(): UniverseInput {
        val lone = fixedID(700)
        val notes = (0 until 40).map { k -> UniverseNote(fixedID(k + 1), String.format("Loose %02d", k), false, null, 20, k.toDouble()) } +
            UniverseNote(fixedID(99), "x", true, lone, 300, 99.0)
        return UniverseInput(notes, listOf(UniverseFolder(lone, "F", null)), emptyList())
    }

    @Test fun t8Comets() {
        val plan = UniversePlan.of(cometInput())
        val active = plan.bodies.filter { it.role == UniverseRole.COMET }
        val resting = plan.bodies.filter { it.role == UniverseRole.OORT }
        check("T8 twelve active comets", active.size == 12, "${active.size}")
        check("T8 twenty-eight in the Oort cloud", resting.size == 28, "${resting.size}")
        val newest = (28 until 40).map { String.format("Loose %02d", it) }.toSet()
        check("T8 the newest fly", active.map { it.title }.toSet() == newest)
        check("T8 summary", plan.summary == "1 galaxy, 1 planet, 40 comets", plan.summary)
        done()
    }

    // ---- T9 moons ----

    @Test fun t9Moons() {
        val moonBad = ArrayList<String>()
        for (k in 0 until 60) {
            val input = randomVault(k.toULong() + 3000uL, 200, 15)
            val plan = UniversePlan.of(input)
            val words = input.notes.associate { it.id to it.words }
            val folderOf = input.notes.associate { it.id to it.folder }
            val perPlanet = HashMap<Int, Int>()
            for (body in plan.bodies) {
                if (body.role != UniverseRole.MOON) continue
                val planet = plan.bodies[body.parent]
                perPlanet[body.parent] = (perPlanet[body.parent] ?: 0) + 1
                val short = (words[body.id] ?: 99) < 60
                val sameFolder = folderOf[body.id] == folderOf[planet.id]
                val strong = planet.role == UniverseRole.GAS_GIANT || (planet.role == UniverseRole.ROCKY && planet.links >= 2)
                if (!(short && body.links == 1 && sameFolder && strong) && moonBad.size < 3) moonBad.add("${body.title} round ${planet.title}")
            }
            for ((p, n) in perPlanet) {
                val cap = if (plan.bodies[p].role == UniverseRole.GAS_GIANT) 4 else 2
                if (n > cap && moonBad.size < 3) moonBad.add("${plan.bodies[p].title} has $n moons")
            }
        }
        check("T9 moons: short, one link, same folder, a strong planet, capped", moonBad.isEmpty(), "$moonBad")
        done()
    }

    // ---- T10 pulsars ----

    private val g1 = fixedID(801)
    private val s1 = fixedID(802)
    private val s2 = fixedID(803)
    private val s3 = fixedID(804)

    private fun bridge(targets: List<String?>, page: Boolean = false): UniversePlan {
        val notes = arrayListOf(UniverseNote(fixedID(1), "Bridge", page, g1, 20, 0.0))
        val edges = ArrayList<UniverseEdge>()
        for ((k, folder) in targets.withIndex()) {
            val id = fixedID(k + 10)
            notes.add(UniverseNote(id, "T$k", true, folder, 100, 1.0))
            edges.add(UniverseEdge(fixedID(1), id))
        }
        val folders = listOf(UniverseFolder(g1, "G", null), UniverseFolder(s1, "S1", g1), UniverseFolder(s2, "S2", g1), UniverseFolder(s3, "S3", null))
        return UniversePlan.of(UniverseInput(notes, folders, edges))
    }

    @Test fun t10Pulsars() {
        val pulsarBad = ArrayList<String>()
        for (k in 0 until 60) {
            val input = randomVault(k.toULong() + 5000uL, 200, 15)
            val plan = UniversePlan.of(input)
            val galaxies = HashMap<Int, Int>()
            val pageOf = input.notes.associate { it.id to it.isPage }
            for (body in plan.bodies) {
                if (body.role != UniverseRole.PULSAR) continue
                galaxies[body.galaxy] = (galaxies[body.galaxy] ?: 0) + 1
                if (pageOf[body.id] == true) pulsarBad.add("a page is a pulsar")
            }
            if (galaxies.values.any { it > 1 }) pulsarBad.add("vault $k: two pulsars in a galaxy")
        }
        check("T10 at most one pulsar per galaxy, ideas only", pulsarBad.isEmpty(), "${pulsarBad.take(3)}")
        check("T10 two folders, three links: a pulsar", roleOf(bridge(listOf(s1, s2, s2)), "Bridge") == UniverseRole.PULSAR)
        check("T10 one other folder: a planet", roleOf(bridge(listOf(s1, s1, s1)), "Bridge") == UniverseRole.ROCKY)
        check("T10 two cross links: a planet", roleOf(bridge(listOf(s1, s2)), "Bridge") != UniverseRole.PULSAR)
        check("T10 loose notes do not count", roleOf(bridge(listOf(s1, null, null)), "Bridge") != UniverseRole.PULSAR)
        check("T10 another galaxy counts", roleOf(bridge(listOf(s1, s3, s3)), "Bridge") == UniverseRole.PULSAR)
        check("T10 a bridging page stays a gas giant", roleOf(bridge(listOf(s1, s2, s2), page = true), "Bridge") == UniverseRole.GAS_GIANT)
        done()
    }

    // ---- T11 orbits ----

    @Test fun t11Orbits() {
        val rateBad = ArrayList<String>()
        for (plan in listOf(preview, bigVault)) {
            val byOwner = HashMap<Int, ArrayList<Pair<Float, Float>>>()
            for (body in plan.bodies) {
                val orbit = body.orbit
                if (body.shell >= 0 && orbit is GraphOrbit.Circle) byOwner.getOrPut(body.parent) { ArrayList() }.add(orbit.radius to orbit.rate)
            }
            for ((_, list) in byOwner) {
                val sorted = list.sortedBy { it.first }
                for ((a, b) in sorted.zipWithNext()) if (b.second > a.second + 1e-6f) rateBad.add("r ${a.first} rate ${a.second} then r ${b.first} rate ${b.second}")
            }
        }
        check("T11 rates fall with radius", rateBad.isEmpty(), "${rateBad.take(3)}")
        val kepler = GraphOrbit.Kepler(4f, 0.45f, Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), 0f, 0.02f)
        val peri = distance(GraphUniverse.offset(kepler, 0.0), Vec3.ZERO)
        // the packet's bound (A2), tighter than iOS's 1e-4
        check("T11 a comet at phase 0 is a(1 - e) out", near(peri, 4 * 0.55f, 1e-5f), "$peri")
        val ringBad = ArrayList<String>()
        for (plan in listOf(preview, bigVault)) for (body in plan.bodies) {
            if (body.role != UniverseRole.MOON) continue
            val planet = plan.bodies[body.parent]
            val orbit = body.orbit as? GraphOrbit.Circle ?: continue
            val k = if (planet.ringed) 2.5f else 1.8f
            val clearance = orbit.radius - body.sphere - k * planet.sphere
            if (clearance < 0.03f - 1e-4f) ringBad.add("${body.title}: $clearance")
        }
        check("T11 moon rings clear their planet by 0.03", ringBad.isEmpty(), "${ringBad.take(3)}")
        var still = true
        for ((i, body) in preview.bodies.withIndex()) {
            if (!body.role.isFolder) continue
            val p0 = preview.position(i, 0.0)
            val p1 = preview.position(i, 321.0)
            if (distance(p0, p1) > 1e-5f || distance(p0, body.home) > 1e-4f) still = false
        }
        check("T11 stars and cores never move", still)
        check("T11 each body's home is where it is at time 0", preview.bodies.indices.all { distance(preview.position(it, 0.0), preview.bodies[it].home) <= 1e-3f })
        done()
    }

    // ---- T12 performance ----

    @Test fun t12Performance() {
        val huge = randomVault(31337uL, 1000, 120)
        val notes = ArrayList(huge.notes)
        val dice = Dice(5uL)
        while (notes.size < 1000) {
            val k = notes.size
            val folder = if (huge.folders.isEmpty()) null else huge.folders[dice.below(huge.folders.size)].id
            notes.add(UniverseNote(fixedID(k + 1), "Note $k", k % 4 == 0, folder, 40 + k % 700, k.toDouble()))
        }
        val folders = ArrayList(huge.folders)
        while (folders.size < 120) {
            val k = folders.size
            val parent = if (k % 3 == 0) null else folders[k / 2].id
            folders.add(UniverseFolder(fixedID(20_000 + k), "Extra $k", parent))
        }
        val input = UniverseInput(notes, folders, huge.edges)
        val started = System.nanoTime()
        val plan = UniversePlan.of(input)
        val took = (System.nanoTime() - started) / 1e6
        check("T12 1000 notes and 120 folders plan in under 300 ms", took < 300, "${took.toInt()} ms")
        check("T12 every note has a body", plan.bodies.size == 1120, "${plan.bodies.size}")
        done()
    }

    // ---- T13 words and levels ----

    @Test fun t13Words() {
        check("T13 words", GraphUniverse.wordCount("  a b\n\nc ") == 3)
        check("T13 no words", GraphUniverse.wordCount(" \t\r\n") == 0)
        val cases = listOf(0 to 0, 14 to 0, 15 to 1, 249 to 4, 250 to 5, 2000 to 8, 99_999 to 8)
        val wrong = cases.filter { GraphUniverse.level(it.first) != it.second }.map { it.first }
        check("T13 levels: 0, 14 -> 0; 15 -> 1; 249 -> 4; 250 -> 5; 2000 and more -> 8", wrong.isEmpty(), "$wrong")
        done()
    }

    // ---- T14 link kinds ----

    private fun linkOf(plan: UniversePlan, a: String, b: String): UniverseLink? {
        val i = bodyNamed(plan, a) ?: return null
        val j = bodyNamed(plan, b) ?: return null
        return plan.links.firstOrNull { (it.a == i && it.b == j) || (it.a == j && it.b == i) }
    }

    @Test fun t14LinkKinds() {
        check("T14 a moon and its planet: hidden", linkOf(preview, "BNP", "Heart failure")?.kind == 3)
        check("T14 same folder: straight", linkOf(preview, "Loop diuretics", "Hypokalaemia")?.kind == 0)
        val sameGalaxy = linkOf(preview, "Femoral hernia", "Inguinal canal")
        val examplesCore = bodyNamed(preview, "Examples") ?: -9
        check("T14 same galaxy: arched round its core", sameGalaxy?.kind == 1 && sameGalaxy.centre == examplesCore, "$sameGalaxy")
        val toComet = linkOf(preview, "Pericarditis", "STEMI")
        check("T14 to a loose note: far", toComet?.kind == 2 && toComet.centre == -1, "$toComet")
        check("T14 every link joins two bodies", preview.links.all { it.a >= 0 && it.b >= 0 && it.a != it.b })
        done()
    }

    // ---- the owner's cases: deep nesting, empty and one-note folders, 300 notes, names, the lift ----

    /** The lift the planner chose for a map's galaxies: A from the two furthest apart in u, and whether each sits at A·u(id). */
    private fun galaxyLift(plan: UniversePlan, byName: Boolean): Pair<Float, Boolean> {
        val galaxies = plan.bodies.filter { it.role == UniverseRole.GALAXY && it.parent < 0 }
        fun key(body: UniverseBody) = if (byName) body.title else body.id
        val sorted = galaxies.sortedBy { GraphTilt.u(key(it)) }
        if (sorted.size <= 1) return 0f to true
        val low = sorted.first()
        val high = sorted.last()
        val spread = GraphTilt.u(key(high)) - GraphTilt.u(key(low))
        val lift = if (spread > 0.0001f) (high.home.z - low.home.z) / spread else 0f
        val shift = low.home.z - GraphTilt.z(key(low), lift)
        val ok = lift >= -0.0001f && galaxies.all { abs(it.home.z - shift - GraphTilt.z(key(it), lift)) <= 0.001f + 0.001f * lift }
        return lift to ok
    }

    @Test fun ownersCases() {
        val chain = ArrayList<UniverseFolder>()
        val chainNotes = ArrayList<UniverseNote>()
        for (k in 0 until 7) {
            chain.add(UniverseFolder(fixedID(600 + k), "Deep $k", if (k == 0) null else chain[k - 1].id))
            chainNotes.add(UniverseNote(fixedID(k + 1), "d$k", false, chain[k].id, 20, k.toDouble()))
        }
        val deep = UniversePlan.of(UniverseInput(chainNotes, chain, emptyList()))
        val deepTiers = (0 until 7).mapNotNull { k -> bodyNamed(deep, "Deep $k")?.let { deep.bodies[it].tier } }
        check("deep nesting: tiers 0, 1, 2, then red dwarfs", deepTiers == listOf(0, 1, 2, 3, 3, 3, 3), "$deepTiers")
        val deepSizes = (0 until 7).mapNotNull { k -> bodyNamed(deep, "Deep $k")?.let { deep.bodies[it].sphere } }
        check("deep nesting: always smaller than the parent, never down to a gas giant",
            deepSizes.zipWithNext().all { it.second < it.first } && deepSizes.all { it > 0.26f }, "$deepSizes")
        val floors = (1 until 8).map { UniversePlanner.starFloor(it) }
        check("the star floor falls with depth and stays above 0.26", floors.zipWithNext().all { it.second < it.first } && floors.all { it > 0.26 }, "$floors")
        check("deep nesting: nothing flattened", deep.bodies.count { it.role == UniverseRole.STAR } == 6)
        check("deep nesting: no overlaps", overlaps(deep, 1f).isEmpty(), "${overlaps(deep, 1f)}")

        val top1 = fixedID(650); val sub1 = fixedID(651); val emptyTop = fixedID(652); val emptySub = fixedID(653); val emptyDeeper = fixedID(654)
        val sparse = UniversePlan.of(UniverseInput(
            listOf(UniverseNote(fixedID(1), "Only", false, sub1, 8, 0.0)),
            listOf(UniverseFolder(top1, "Top", null), UniverseFolder(sub1, "One", top1), UniverseFolder(emptyTop, "Nothing", null),
                UniverseFolder(emptySub, "Hollow", top1), UniverseFolder(emptyDeeper, "Hollower", emptySub)),
            emptyList()))
        val oneNote = bodyNamed(sparse, "One")?.let { sparse.bodies[it] }
        check("a one-note folder: a star with one planet", oneNote?.role == UniverseRole.STAR && oneNote.label == "One · 1 idea")
        val only = bodyNamed(sparse, "Only")?.let { sparse.bodies[it] }
        check("its note orbits it", only?.let { sparse.bodies[it.parent].title } == "One")
        val dormant = bodyNamed(sparse, "Nothing")?.let { sparse.bodies[it] }
        check("an empty top folder: a dormant black hole", dormant?.role == UniverseRole.GALAXY && dormant.empty && near(dormant.sphere, 0.40f)
            && dormant.label == "Nothing · empty")
        val dark = bodyNamed(sparse, "Hollow")?.let { sparse.bodies[it] }
        check("an empty subfolder: a dark star", dark?.role == UniverseRole.STAR && dark.empty && near(dark.sphere, 0.278f))
        val darker = bodyNamed(sparse, "Hollower")?.let { sparse.bodies[it] }
        check("an empty folder in it: a smaller dark star", darker?.empty == true && darker.sphere < (dark?.sphere ?: 0f),
            "${darker?.sphere} vs ${dark?.sphere}")
        check("empty folders do not shine", sparse.lights.none { sparse.bodies[it].empty })

        val loosePlan = UniversePlan.of(UniverseInput(
            listOf(UniverseNote(fixedID(1), "Filed", true, top1, 80, 0.0), UniverseNote(fixedID(2), "Root note", false, null, 8, 1.0)),
            listOf(UniverseFolder(top1, "Top", null)), listOf(UniverseEdge(fixedID(1), fixedID(2)))))
        val rootNote = bodyNamed(loosePlan, "Root note")?.let { loosePlan.bodies[it] }
        check("a note in no folder is a comet with no parent", rootNote?.role == UniverseRole.COMET && rootNote.parent == -1)
        check("it lights from the nearest star", rootNote?.light == -2)

        val scale = UniversePlan.of(randomVault(300uL, 300, 30, exact = true))
        val notesPlanned = scale.bodies.count { it.count == 0 && !it.role.isFolder }
        check("300-note scale: every note planned", notesPlanned == 300, "$notesPlanned notes")
        check("300-note scale: no overlaps", overlaps(scale, 1f, 20.0).isEmpty(), "${overlaps(scale, 1f, 20.0)}")
        check("300-note scale: inside the envelope", envelopeHolds(scale).isEmpty(), "${envelopeHolds(scale)}")

        val longName = "Cardiothoracic ".repeat(3)
        val named = UniversePlan.of(UniverseInput(listOf(UniverseNote(fixedID(1), "", false, top1, 3, 0.0)),
            listOf(UniverseFolder(top1, longName, null)), emptyList()))
        val cut = longName.take(20) + "… · 1 idea"
        check("a long folder name is cut but the count shows", named.bodies.firstOrNull()?.label == cut, named.bodies.firstOrNull()?.label ?: "")
        check("an empty title shows Untitled", named.bodies.lastOrNull()?.label == "Untitled")

        for ((name, plan, byName) in listOf(Triple("preview", preview, true), Triple("big vault", bigVault, false), Triple("scale", scale, false))) {
            check("lift $name: every galaxy at A·u(id)", galaxyLift(plan, byName).second)
        }
        done()
    }

    // ---- comets (A2) ----

    @Test fun comets() {
        // a page in no folder is a comet like any loose note
        val withPage = UniversePlan.of(UniverseInput(
            listOf(UniverseNote(fixedID(1), "Filed", false, fixedID(650), 20, 0.0), UniverseNote(fixedID(2), "Loose page", true, null, 400, 1.0)),
            listOf(UniverseFolder(fixedID(650), "Top", null)), emptyList()))
        check("a page in no folder is a comet", roleOf(withPage, "Loose page") == UniverseRole.COMET, "${roleOf(withPage, "Loose page")}")
        val plan = UniversePlan.of(cometInput())
        // created 0..39: the 13th newest is "Loose 27"
        check("the 13th newest loose note rests in the Oort cloud", roleOf(plan, "Loose 27") == UniverseRole.OORT, "${roleOf(plan, "Loose 27")}")
        check("the 12th newest still flies", roleOf(plan, "Loose 28") == UniverseRole.COMET, "${roleOf(plan, "Loose 28")}")
        val oort = plan.bodies.first { it.role == UniverseRole.OORT }
        check("an Oort comet rides the ring, lit by the key light", oort.orbit is GraphOrbit.Ring && oort.light == -1)
        val comet = plan.bodies.first { it.role == UniverseRole.COMET }
        check("an active comet is on a Kepler ellipse of e 0.45", (comet.orbit as? GraphOrbit.Kepler)?.e == 0.45f)
        done()
    }

    // ---- parity with iOS's planner (ios-dump.json) ----

    private fun resource(path: String): String? = javaClass.classLoader?.getResource(path)?.readText()

    private fun readInput(o: JSONObject): UniverseInput {
        fun opt(o: JSONObject, key: String): String? = if (o.isNull(key)) null else o.getString(key)
        val notes = (0 until o.getJSONArray("notes").length()).map { k ->
            val n = o.getJSONArray("notes").getJSONObject(k)
            UniverseNote(n.getString("id"), n.getString("title"), n.getBoolean("isPage"), opt(n, "folder"), n.getInt("words"), n.getDouble("created"))
        }
        val folders = (0 until o.getJSONArray("folders").length()).map { k ->
            val f = o.getJSONArray("folders").getJSONObject(k)
            UniverseFolder(f.getString("id"), f.getString("name"), opt(f, "parent"))
        }
        val edges = (0 until o.getJSONArray("edges").length()).map { k ->
            val e = o.getJSONArray("edges").getJSONObject(k)
            UniverseEdge(e.getString("a"), e.getString("b"))
        }
        return UniverseInput(notes, folders, edges, o.optBoolean("seedByName", false), o.optDouble("linkScale", 1.0))
    }

    @Test fun previewInputIsNeuronPreviewData() {
        val text = resource("universe/inputs/preview.json")
        check("the preview input is committed", text != null)
        if (text != null) check("the dump's preview input is NeuronPreviewData's store", readInput(JSONObject(text)) == NeuronHierarchyTest.preview)
        done()
    }

    private fun num(a: JSONArray, k: Int) = a.getDouble(k).toFloat()
    private fun vec(a: JSONArray) = Vec3(num(a, 0), num(a, 1), num(a, 2))
    private fun closeVec(where: String, a: Vec3, b: JSONArray, bad: MutableList<String>) {
        val e = vec(b)
        if (!(abs(a.x - e.x) <= 1e-4f && abs(a.y - e.y) <= 1e-4f && abs(a.z - e.z) <= 1e-4f)) bad.add("$where: $a not $e")
    }
    private fun close(where: String, a: Float, b: Double, bad: MutableList<String>) { if (!(abs(a - b) <= 1e-4)) bad.add("$where: $a not $b") }

    private fun compareOrbit(where: String, orbit: GraphOrbit, o: JSONObject, bad: MutableList<String>) {
        val kind = o.getString("kind")
        when (orbit) {
            is GraphOrbit.Fixed -> if (kind != "fixed") bad.add("$where: fixed not $kind") else closeVec("$where.p", orbit.p, o.getJSONArray("p"), bad)
            is GraphOrbit.Circle -> if (kind != "circle") bad.add("$where: circle not $kind") else {
                close("$where.radius", orbit.radius, o.getDouble("radius"), bad)
                closeVec("$where.u", orbit.u, o.getJSONArray("u"), bad); closeVec("$where.v", orbit.v, o.getJSONArray("v"), bad)
                close("$where.phase", orbit.phase, o.getDouble("phase"), bad); close("$where.rate", orbit.rate, o.getDouble("rate"), bad)
            }
            is GraphOrbit.Kepler -> if (kind != "kepler") bad.add("$where: kepler not $kind") else {
                close("$where.a", orbit.a, o.getDouble("a"), bad); close("$where.e", orbit.e, o.getDouble("e"), bad)
                closeVec("$where.u", orbit.u, o.getJSONArray("u"), bad); closeVec("$where.v", orbit.v, o.getJSONArray("v"), bad)
                close("$where.phase", orbit.phase, o.getDouble("phase"), bad); close("$where.rate", orbit.rate, o.getDouble("rate"), bad)
            }
            is GraphOrbit.Ring -> if (kind != "ring") bad.add("$where: ring not $kind") else {
                close("$where.x", orbit.x, o.getDouble("x"), bad); close("$where.y", orbit.y, o.getDouble("y"), bad)
                close("$where.z", orbit.z, o.getDouble("z"), bad)
                close("$where.phase", orbit.phase, o.getDouble("phase"), bad); close("$where.rate", orbit.rate, o.getDouble("rate"), bad)
            }
            is GraphOrbit.Drift -> bad.add("$where: a drift in the Universe")
        }
    }

    private fun comparePlan(name: String, plan: UniversePlan, o: JSONObject): List<String> {
        val bad = ArrayList<String>()
        if (plan.summary != o.getString("summary")) bad.add("$name.summary: ${plan.summary} not ${o.getString("summary")}")
        val bodies = o.getJSONArray("bodies")
        if (bodies.length() != plan.bodies.size) return bad + "$name: ${plan.bodies.size} bodies not ${bodies.length()}"
        for ((k, b) in plan.bodies.withIndex()) {
            val e = bodies.getJSONObject(k)
            val w = "$name.bodies[$k] ${b.title}"
            val exact = listOf("id" to (b.id == e.getString("id")), "role" to (b.role.raw == e.getString("role")), "parent" to (b.parent == e.getInt("parent")),
                "light" to (b.light == e.getInt("light")), "tier" to (b.tier == e.getInt("tier")), "empty" to (b.empty == e.getBoolean("empty")),
                "ringed" to (b.ringed == e.getBoolean("ringed")), "palette" to (b.palette == e.getInt("palette")), "galaxy" to (b.galaxy == e.getInt("galaxy")),
                "count" to (b.count == e.getInt("count")), "links" to (b.links == e.getInt("links")), "shell" to (b.shell == e.getInt("shell")),
                "title" to (b.title == e.getString("title")), "label" to (b.label == e.getString("label")))
            for ((field, ok) in exact) if (!ok) bad.add("$w.$field: ${e.opt(field)}")
            close("$w.sphere", b.sphere, e.getDouble("sphere"), bad)
            closeVec("$w.home", b.home, e.getJSONArray("home"), bad)
            compareOrbit("$w.orbit", b.orbit, e.getJSONObject("orbit"), bad)
        }
        val shells = o.getJSONArray("shells")
        if (shells.length() != plan.shells.size) bad.add("$name: ${plan.shells.size} shells not ${shells.length()}")
        else for ((k, s) in plan.shells.withIndex()) {
            val e = shells.getJSONObject(k)
            if (s.owner != e.getInt("owner")) bad.add("$name.shells[$k].owner")
            close("$name.shells[$k].radius", s.radius, e.getDouble("radius"), bad)
            closeVec("$name.shells[$k].u", s.u, e.getJSONArray("u"), bad); closeVec("$name.shells[$k].v", s.v, e.getJSONArray("v"), bad)
        }
        val links = o.getJSONArray("links")
        if (links.length() != plan.links.size) bad.add("$name: ${plan.links.size} links not ${links.length()}")
        else for ((k, l) in plan.links.withIndex()) {
            val e = links.getJSONObject(k)
            if (l != UniverseLink(e.getInt("a"), e.getInt("b"), e.getInt("kind"), e.getInt("centre"))) bad.add("$name.links[$k]: $l not $e")
        }
        val envelope = o.getJSONArray("envelope")
        if (envelope.length() != plan.envelope.size) bad.add("$name: ${plan.envelope.size} envelope points not ${envelope.length()}")
        else for ((k, p) in plan.envelope.withIndex()) closeVec("$name.envelope[$k]", p, envelope.getJSONArray(k), bad)
        val systems = o.getJSONArray("systems")
        if (systems.length() != plan.systems.size) bad.add("$name: ${plan.systems.size} systems not ${systems.length()}")
        else for ((k, points) in plan.systems.withIndex()) {
            val e = systems.getJSONArray(k)
            if (e.length() != points.size) bad.add("$name.systems[$k]: ${points.size} points not ${e.length()}")
            else for ((j, p) in points.withIndex()) closeVec("$name.systems[$k][$j]", p, e.getJSONArray(j), bad)
        }
        val lights = o.getJSONArray("lights")
        val expected = (0 until lights.length()).map { lights.getInt(it) }
        if (plan.lights != expected) bad.add("$name.lights: ${plan.lights} not $expected")
        return bad
    }

    @Test fun parityWithIos() {
        val text = resource("universe/ios-dump.json")
        assertTrue("no committed dump: commit the uploaded universe-dump", text != null)
        val dump = JSONObject(text!!)
        check("the dump names its planner's hash", dump.optString("hash").matches(Regex("[0-9a-f]{40}")), dump.optString("hash"))
        val plans = dump.getJSONObject("plans")
        val names = listOf("flat", "loose", "preview")
        check("the dump holds the three inputs", plans.keys().asSequence().toSet() == names.toSet(), "${plans.keys().asSequence().toList()}")
        for (name in names) {
            val input = resource("universe/inputs/$name.json")
            if (input == null || !plans.has(name)) { check("$name: input and dump", false); continue }
            val bad = comparePlan(name, UniversePlan.of(readInput(JSONObject(input))), plans.getJSONObject(name))
            check("$name equals iOS's plan", bad.isEmpty(), bad.take(8).joinToString("; "))
        }
        done()
    }

    // ---- the vaults (iOS Dice and randomVault, as NeuronHierarchyTest has them) ----

    private class Dice(var state: ULong) {
        fun next(): ULong {
            state += 0x9E3779B97F4A7C15uL
            var z = state
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
            return z xor (z shr 31)
        }
        fun below(n: Int): Int = if (n <= 0) 0 else (next() % n.toULong()).toInt()
        fun unit(): Double = (next() shr 11).toDouble() / 9_007_199_254_740_992.0
    }

    private fun randomVault(seed: ULong, maxNotes: Int, maxFolders: Int, exact: Boolean = false): UniverseInput {
        val dice = Dice(seed)
        val folderCount = dice.below(maxFolders + 1)
        val folders = ArrayList<UniverseFolder>()
        val depthOf = ArrayList<Int>()
        for (k in 0 until folderCount) {
            var parent: String? = null
            var d = 0
            if (k > 0 && dice.unit() < 0.6) {
                val p = dice.below(k)
                if (depthOf[p] < 6) { parent = folders[p].id; d = depthOf[p] + 1 }
            }
            folders.add(UniverseFolder(fixedID(10_000 + k), "Folder $k", parent))
            depthOf.add(d)
        }
        val drawn = dice.below(maxNotes + 1)
        val noteCount = if (exact) maxNotes else drawn
        val notes = ArrayList<UniverseNote>()
        for (k in 0 until noteCount) {
            var folder: String? = null
            if (folders.isNotEmpty() && dice.unit() < 0.88) folder = folders[dice.below(folders.size)].id
            val words = Math.pow(2.0, dice.unit() * 11).toInt()
            notes.add(UniverseNote(fixedID(k + 1), "Note $k", dice.unit() < 0.3, folder, words, k.toDouble()))
        }
        val edges = ArrayList<UniverseEdge>()
        if (noteCount > 1) for (i in 0 until noteCount * 13 / 10) {
            val a = dice.below(noteCount)
            val b = dice.below(noteCount)
            edges.add(UniverseEdge(fixedID(a + 1), fixedID(b + 1)))
        }
        return UniverseInput(notes, folders, edges, seedByName = false)
    }
}
