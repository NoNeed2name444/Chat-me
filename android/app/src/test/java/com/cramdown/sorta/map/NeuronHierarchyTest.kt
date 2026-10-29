package com.cramdown.sorta.map

import com.cramdown.sorta.ideas.GraphTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// iOS NeuronHierarchyTests.swift (T1-T10), its checks and bounds as they are: the ideas'
// folders as living cells. The design preview's roles closed and opened (T1), every note link
// carried once by the cells' processes and dyed by what it says (T2), the size ladder and
// nothing meeting while they drift over 160 random vaults (T3), the same notes giving the same
// picture and opening moving nothing (T4), the sheet's order and facing (T5), the edge cases
// (T6), 300 notes in under 2 s (T7), the theme choice (T8), drifting (T9) and the impulses (T10).
// T8's bodies and ground (GraphTheme.bodies, ground: which engine and backdrop a theme draws
// with) have no Android counterpart, so those four checks are not carried; the rest are.
class NeuronHierarchyTest {
    private val failures = ArrayList<String>()
    private fun check(label: String, ok: Boolean, detail: String = "") { if (!ok) failures.add("$label | $detail") }
    private fun done() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    // ---- helpers over a plan ----

    private fun bodyNamed(plan: ThemePlan, title: String): Int? = plan.bodies.indexOfFirst { it.title == title }.takeIf { it >= 0 }
    private fun titles(plan: ThemePlan, role: NeuronRole): Set<String> = plan.bodies.filter { it.role == role.code }.map { it.title }.toSet()
    private fun roleOf(body: ThemeBody): NeuronRole = NeuronRole.of(body.role) ?: NeuronRole.GRANULE
    private fun distance(a: Vec3, b: Vec3): Float = (a - b).length()
    private fun size(a: Vec3): Float = a.length()
    private fun planOpen(input: UniverseInput, open: List<String>): ThemePlan = GraphNeurons.plan(input.copy(open = open))
    private fun allOpen(input: UniverseInput): ThemePlan = planOpen(input, input.folders.map { it.id })
    private fun width(strength: Int): Float = (0.55 + 0.12 * strength).toFloat()

    private fun insides(plan: ThemePlan): List<List<Int>> {
        val kids = List(plan.bodies.size) { ArrayList<Int>() }
        for ((k, body) in plan.bodies.withIndex()) if (body.parent >= 0 && body.parent < plan.bodies.size) kids[body.parent].add(k)
        return kids
    }

    private fun holds(plan: ThemePlan, outer: Int, inner: Int): Boolean {
        var at = plan.bodies[inner].parent
        var steps = 0
        while (at >= 0 && steps <= plan.bodies.size) {
            if (at == outer) return true
            at = plan.bodies[at].parent
            steps++
        }
        return false
    }

    private fun ladderProblems(plan: ThemePlan): List<String> {
        val problems = ArrayList<String>()
        val bodies = plan.bodies
        for ((k, body) in bodies.withIndex()) {
            val role = roleOf(body)
            if (!(body.sphere > 0 && body.sphere.isFinite())) { problems.add("${body.title} has no size"); continue }
            when (body.kind) {
                ThemeBodyKind.NOTE -> if (role !in listOf(NeuronRole.VESICLE, NeuronRole.GRANULE, NeuronRole.RECEPTOR, NeuronRole.DRIFTER)) problems.add("${body.title} a note as $role")
                ThemeBodyKind.FOLDER -> if (role != NeuronRole.CELL && role != NeuronRole.PART) problems.add("${body.title} a folder as $role")
                ThemeBodyKind.HOME -> if (role != NeuronRole.HOME || body.id != GraphUniverse.HOME_ID) problems.add("${body.title} a home as $role")
            }
            val free = role == NeuronRole.CELL || role == NeuronRole.HOME || role.isFree
            if (free != (body.parent < 0)) problems.add("${body.title} as $role with parent ${body.parent}")
            if (body.parent < 0) continue
            if (body.parent >= k) { problems.add("${body.title} before what holds it"); continue }
            val holder = bodies[body.parent]
            if (!roleOf(holder).isContainer) problems.add("${body.title} inside ${holder.title}, a ${roleOf(holder)}")
            if (body.sphere >= holder.sphere * GraphNeurons.INNER.toFloat()) problems.add("${body.title} ${body.sphere} as big as the room in ${holder.title} ${holder.sphere}")
            if (body.kind != ThemeBodyKind.NOTE && body.depth != holder.depth + 1) problems.add("${body.title} at depth ${body.depth}")
        }
        for (inside in insides(plan)) {
            if (inside.isEmpty()) continue
            val parts = inside.filter { roleOf(bodies[it]) == NeuronRole.PART }.map { bodies[it].sphere }
            val notes = inside.filter { bodies[it].kind == ThemeBodyKind.NOTE }.map { bodies[it].sphere }
            val pages = inside.filter { roleOf(bodies[it]) == NeuronRole.VESICLE }.map { bodies[it].sphere }
            val ideas = inside.filter { roleOf(bodies[it]) == NeuronRole.GRANULE }.map { bodies[it].sphere }
            val note = notes.maxOrNull(); val part = parts.minOrNull()
            if (note != null && part != null && note >= part) problems.add("a note $note as big as a part $part in ${bodies[bodies[inside[0]].parent].title}")
            val idea = ideas.maxOrNull(); val page = pages.minOrNull()
            if (idea != null && page != null && idea >= page) problems.add("an idea $idea as big as a page $page in ${bodies[bodies[inside[0]].parent].title}")
        }
        val tops = bodies.filter { it.parent < 0 && roleOf(it).isContainer }
        for (a in tops) for (b in tops) if (a.count > b.count && a.sphere < b.sphere) problems.add("${a.title} (${a.count} notes) smaller than ${b.title} (${b.count})")
        val receptors = bodies.filter { roleOf(it) == NeuronRole.RECEPTOR }.map { it.sphere }
        val drifters = bodies.filter { roleOf(it) == NeuronRole.DRIFTER }.map { it.sphere }
        val drifter = drifters.maxOrNull(); val receptor = receptors.minOrNull()
        if (drifter != null && receptor != null && drifter >= receptor) problems.add("a drifting cell $drifter as big as a receptor $receptor")
        return problems
    }

    private fun insideProblems(plan: ThemePlan): List<String> {
        val problems = ArrayList<String>()
        for ((p, inside) in insides(plan).withIndex()) {
            if (inside.isEmpty()) continue
            val holder = plan.bodies[p]
            val zones = GraphNeurons.zones(holder.sphere.toDouble(), holder.depth == 0)
            val radii = inside.map { plan.bodies[it].sphere }
            for (t in looks) {
                if (problems.size >= 4) break
                val at = inside.map { GraphUniverse.offset(plan.bodies[it].orbit, t) }
                for (n in inside.indices) {
                    val name = plan.bodies[inside[n]].title
                    val far = size(at[n])
                    val part = plan.bodies[inside[n]].kind != ThemeBodyKind.NOTE
                    val room = (if (part) zones.parts else zones.room).toFloat()
                    if (far + radii[n] > room * 1.00001f) problems.add("$name out of ${if (part) "the nucleus of " else ""}${holder.title} at $t: ${far + radii[n]} > $room")
                    if (!part && zones.core > 0 && far - radii[n] < zones.core.toFloat() * 0.99999f) problems.add("$name in the nucleus of ${holder.title} at $t")
                    for (m in n + 1 until inside.size) if (distance(at[n], at[m]) < radii[n] + radii[m]) problems.add("$name meets ${plan.bodies[inside[m]].title} in ${holder.title} at $t")
                }
            }
        }
        return problems
    }

    private fun meetProblems(plan: ThemePlan): List<String> {
        val problems = ArrayList<String>()
        val outer = plan.bodies.indices.filter { plan.bodies[it].parent < 0 }
        for (t in looks) {
            if (problems.size >= 4) break
            val at = outer.map { plan.position(it, t) }
            for (n in outer.indices) {
                val a = plan.bodies[outer[n]]
                for (m in n + 1 until outer.size) {
                    val b = plan.bodies[outer[m]]
                    if (distance(at[n], at[m]) < a.sphere + b.sphere) problems.add("${a.title} meets ${b.title} at $t")
                }
            }
        }
        return problems
    }

    private fun envelopeProblems(plan: ThemePlan): List<String> {
        val first = plan.envelope.firstOrNull() ?: return if (plan.bodies.isEmpty()) emptyList() else listOf("no envelope")
        var lx = first.x; var ly = first.y; var lz = first.z; var hx = first.x; var hy = first.y; var hz = first.z
        for (p in plan.envelope) { lx = min(lx, p.x); ly = min(ly, p.y); lz = min(lz, p.z); hx = max(hx, p.x); hy = max(hy, p.y); hz = max(hz, p.z) }
        val problems = ArrayList<String>()
        for ((k, body) in plan.bodies.withIndex()) {
            if (body.parent >= 0) continue
            for (t in looks) {
                val p = plan.position(k, t)
                val r = body.sphere
                val inside = p.x - r >= lx - 0.001f && p.y - r >= ly - 0.001f && p.z - r >= lz - 0.001f &&
                    p.x + r <= hx + 0.001f && p.y + r <= hy + 0.001f && p.z + r <= hz + 0.001f
                if (!inside) { problems.add("${body.title} leaves the framing at $t"); break }
            }
        }
        return problems
    }

    private fun shownBodies(input: UniverseInput, plan: ThemePlan): Map<String, Int> {
        val byID = HashMap<String, Int>()
        for ((k, body) in plan.bodies.withIndex()) byID[body.id] = k
        val parentOf = HashMap<String, String>()
        for (folder in input.folders) folder.parent?.let { parentOf[folder.id] = it }
        val out = HashMap<String, Int>()
        for (note in input.notes) {
            byID[note.id]?.let { out[note.id] = it }
            if (out.containsKey(note.id)) continue
            if (input.folders.isEmpty()) { byID[GraphUniverse.HOME_ID]?.let { out[note.id] = it }; continue }
            var at = note.folder
            var steps = 0
            while (at != null && steps <= input.folders.size + 1) {
                val k = byID[at]
                if (k != null) { out[note.id] = k; break }
                at = parentOf[at]
                steps++
            }
        }
        return out
    }

    private fun outermost(plan: ThemePlan, x: Int): Int {
        var at = x
        var steps = 0
        while (plan.bodies[at].parent >= 0 && steps <= plan.bodies.size) { at = plan.bodies[at].parent; steps++ }
        return at
    }

    private fun processProblems(input: UniverseInput, plan: ThemePlan): List<String> {
        val problems = ArrayList<String>()
        val shown = shownBodies(input, plan)
        val notes = input.notes.map { it.id }.toSet()
        val ways = LinkedHashSet<Pair<String, String>>()
        for (edge in input.edges) if (edge.a != edge.b && edge.a in notes && edge.b in notes) ways.add(edge.a to edge.b)
        val up = HashMap<Pair<Int, Int>, Int>()
        val down = HashMap<Pair<Int, Int>, Int>()
        for ((wa, wb) in ways) {
            var x = shown[wa]; var y = shown[wb]
            if (x == null || y == null) { problems.add("a linked note shows nowhere"); continue }
            if (x == y) continue
            if (outermost(plan, x) != outermost(plan, y)) { x = outermost(plan, x); y = outermost(plan, y) }
            val pair = min(x, y) to max(x, y)
            if (x < y) up.merge(pair, 1, Int::plus) else down.merge(pair, 1, Int::plus)
        }
        val seen = HashMap<Pair<Int, Int>, Int>()
        for (link in plan.links) {
            val n = plan.bodies.size
            if (!(link.a >= 0 && link.b >= 0 && link.a < n && link.b < n && link.a != link.b)) { problems.add("a process with a bad end: ${link.a} ${link.b}"); continue }
            val a = plan.bodies[link.a]; val b = plan.bodies[link.b]
            val pair = min(link.a, link.b) to max(link.a, link.b)
            seen.merge(pair, 1, Int::plus)
            val same = a.region >= 0 && a.region == b.region
            if (link.kind != (if (same) 5 else 6)) problems.add("${a.title} - ${b.title}: kind ${link.kind}")
            if (link.centre != -1) problems.add("${a.title} - ${b.title}: centre ${link.centre}")
            if (FiberKind.entries.none { it.code == link.tag }) problems.add("${a.title} - ${b.title}: dye ${link.tag}")
            if (!(link.width >= 0.669f && link.width <= 1.751f)) problems.add("${a.title} - ${b.title}: width ${link.width}")
            if (holds(plan, link.a, link.b) || holds(plan, link.b, link.a)) problems.add("${a.title} - ${b.title}: one holds the other")
            if (outermost(plan, link.a) != outermost(plan, link.b) && (a.parent >= 0 || b.parent >= 0)) problems.add("${a.title} - ${b.title}: crosses a cell's membrane")
            val along = if (link.a < link.b) up[pair] ?: 0 else down[pair] ?: 0
            val against = if (link.a < link.b) down[pair] ?: 0 else up[pair] ?: 0
            if (along + against == 0) problems.add("${a.title} - ${b.title} carries no link")
            else if (along < against) problems.add("${a.title} -> ${b.title} runs against $against of its ${along + against} links")
            else if (along == against && laterName(a, b)) problems.add("${a.title} -> ${b.title}: a tie sent from the later name")
        }
        for (pair in up.keys + down.keys) if (seen[pair] != 1) problems.add("${plan.bodies[pair.first].title} - ${plan.bodies[pair.second].title}: ${seen[pair] ?: 0} processes")
        return problems
    }

    /** Swift's (a.title.lowercased(), a.id) > (b.title.lowercased(), b.id). */
    private fun laterName(a: ThemeBody, b: ThemeBody): Boolean {
        val t = a.title.lowercase().compareTo(b.title.lowercase())
        return if (t != 0) t > 0 else a.id > b.id
    }

    private fun movedProblems(small: ThemePlan, big: ThemePlan): List<String> {
        val byID = HashMap<String, ThemeBody>()
        for (body in big.bodies) byID[body.id] = body
        val problems = ArrayList<String>()
        for (body in small.bodies) {
            val other = byID[body.id]
            if (other == null) { problems.add("${body.title} is gone"); continue }
            if (!same(other.home, body.home) || other.sphere != body.sphere || other.orbit != body.orbit ||
                !same(other.axis, body.axis) || other.role != body.role || other.label != body.label) problems.add("${body.title} changed")
        }
        return problems
    }

    /** Swift's == on a float vector: IEEE, so 0 and -0 agree. */
    private fun same(a: Vec3, b: Vec3) = a.x == b.x && a.y == b.y && a.z == b.z

    private fun few(problems: List<String>): String = problems.take(3).joinToString("; ")

    // ---- T1 the design preview, closed and opened ----

    @Test fun t1PreviewClosedAndOpened() {
        val closed = GraphNeurons.plan(preview)
        check("T1 closed: two cells and two receptors", closed.summary == "2 cells, 2 receptors", closed.summary)
        check("T1 closed: the top folders are the cells", titles(closed, NeuronRole.CELL) == setOf("Examples", "Cardiology"), "${titles(closed, NeuronRole.CELL)}")
        check("T1 closed: the linked loose notes are receptors", titles(closed, NeuronRole.RECEPTOR) == setOf("Richter's hernia", "Pericarditis"), "${titles(closed, NeuronRole.RECEPTOR)}")
        check("T1 closed: nothing shows inside a closed cell", closed.bodies.all { it.parent < 0 })
        check("T1 the cells are the regions", closed.regions.map { closed.bodies[it].title }.sorted() == listOf("Cardiology", "Examples")
            && closed.regions.all { closed.bodies[it].region == it })
        val openOne = planOpen(preview, listOf(EXAMPLES))
        check("T1 Examples opened: its two parts and three notes show", openOne.summary == "2 cells, 2 parts, 1 vesicle, 2 granules, 2 receptors", openOne.summary)
        check("T1 Examples opened: Inguinal and Femoral are parts", titles(openOne, NeuronRole.PART) == setOf("Inguinal", "Femoral"), "${titles(openOne, NeuronRole.PART)}")
        check("T1 Examples opened: Groin hernia a vesicle, its ideas granules",
            titles(openOne, NeuronRole.VESICLE) == setOf("Groin hernia") && titles(openOne, NeuronRole.GRANULE) == setOf("Hernia repair", "Expansile cough impulse"),
            "${titles(openOne, NeuronRole.VESICLE)} ${titles(openOne, NeuronRole.GRANULE)}")
        val examplesBody = bodyNamed(openOne, "Examples") ?: -1
        check("T1 Examples opened: all of it inside Examples",
            openOne.bodies.all { it.parent < 0 || it.parent == examplesBody } && openOne.bodies.count { it.parent == examplesBody } == 5)
        val openTwo = planOpen(preview, listOf(EXAMPLES, INGUINAL))
        check("T1 Inguinal opened too: Anatomy and Inguinal's notes show", openTwo.summary == "2 cells, 3 parts, 4 vesicles, 4 granules, 2 receptors", openTwo.summary)
        val anatomy = bodyNamed(openTwo, "Anatomy")
        check("T1 Anatomy is a part inside Inguinal", anatomy != null && openTwo.bodies[anatomy].parent == bodyNamed(openTwo, "Inguinal")
            && roleOf(openTwo.bodies[anatomy]) == NeuronRole.PART)
        check("T1 a part inside a closed cell stays shut", planOpen(preview, listOf(INGUINAL)).summary == "2 cells, 2 receptors")
        check("T1 the order folders are opened in changes nothing", planOpen(preview, listOf(INGUINAL, EXAMPLES)).bodies == openTwo.bodies)
        val openAll = allOpen(preview)
        check("T1 all opened: every note shows inside its folder", openAll.summary == "2 cells, 3 parts, 9 vesicles, 19 granules, 2 receptors", openAll.summary)
        val misplaced = ArrayList<String>()
        for (note in preview.notes) {
            val k = bodyNamed(openAll, note.title)
            if (k == null) { misplaced.add(note.title + " missing"); continue }
            val body = openAll.bodies[k]
            val holder = if (body.parent >= 0) openAll.bodies[body.parent].id else null
            if (holder != note.folder) misplaced.add(note.title)
            val want = if (note.folder == null) NeuronRole.RECEPTOR else if (note.isPage) NeuronRole.VESICLE else NeuronRole.GRANULE
            if (roleOf(body) != want) misplaced.add(note.title + " as ${roleOf(body)}")
        }
        check("T1 all opened: each note in the folder that holds it, a page a vesicle", misplaced.isEmpty(), few(misplaced))
        check("T1 each body after what holds it", openAll.bodies.withIndex().all { (k, b) -> b.parent < k })
        check("T1 a container's framing holds it; a note has none", openAll.systems.size == openAll.bodies.size
            && openAll.bodies.zip(openAll.systems).all { (b, s) -> if (b.kind == ThemeBodyKind.NOTE) s.isEmpty() else s.size == 6 })
        check("T1 the preview's ladder holds, open and closed",
            ladderProblems(closed).isEmpty() && ladderProblems(openOne).isEmpty() && ladderProblems(openAll).isEmpty(),
            few(ladderProblems(closed) + ladderProblems(openOne) + ladderProblems(openAll)))
        check("T1 nothing inside meets or leaves its cell", insideProblems(openAll).isEmpty(), few(insideProblems(openAll)))
        check("T1 no cells meet", meetProblems(openAll).isEmpty(), few(meetProblems(openAll)))
        check("T1 the framing holds every cell", envelopeProblems(openAll).isEmpty(), few(envelopeProblems(openAll)))
        done()
    }

    // ---- T2 the links as the cells' processes ----

    @Test fun t2Processes() {
        val closed = GraphNeurons.plan(preview)
        val openOne = planOpen(preview, listOf(EXAMPLES))
        val openTwo = planOpen(preview, listOf(EXAMPLES, INGUINAL))
        val openAll = allOpen(preview)
        check("T2 closed: every link carried once", processProblems(preview, closed).isEmpty(), few(processProblems(preview, closed)))
        check("T2 closed: three processes into cells, one from each receptor and one from Examples to Cardiology",
            closed.links.size == 3 && closed.links.all { roleOf(closed.bodies[it.b]) == NeuronRole.CELL && it.kind == 6 }
                && closed.links.count { roleOf(closed.bodies[it.a]) == NeuronRole.RECEPTOR } == 2
                && closed.links.any { closed.bodies[it.a].title == "Examples" && closed.bodies[it.b].title == "Cardiology" }, "${closed.links}")
        check("T2 Examples opened: every link carried once", processProblems(preview, openOne).isEmpty(), few(processProblems(preview, openOne)))
        val examplesBody = bodyNamed(openOne, "Examples") ?: -1
        val richter = bodyNamed(openOne, "Richter's hernia") ?: -1
        val femoral = bodyNamed(openOne, "Femoral") ?: -1
        check("T2 a link from outside joins the opened cell's membrane, not the part inside",
            openOne.links.any { it.a == richter && it.b == examplesBody && it.kind == 6 }
                && openOne.links.none { (it.a == femoral || it.b == femoral) && it.kind == 6 },
            "${openOne.links.filter { it.kind == 6 }.map { openOne.bodies[it.a].title + ">" + openOne.bodies[it.b].title }}")
        val groin = bodyNamed(openOne, "Groin hernia") ?: -1
        val inguinalPart = bodyNamed(openOne, "Inguinal") ?: -1
        check("T2 three links from Groin hernia into Inguinal make one process",
            openOne.links.count { it.a == groin && it.b == inguinalPart } == 1
                && openOne.links.firstOrNull { it.a == groin && it.b == inguinalPart }?.width == width(6))
        check("T2 inside a cell kind 5, between cells 6", openOne.links.count { it.kind == 5 } == 6 && openOne.links.count { it.kind == 6 } == 3,
            "${openOne.links.map { it.kind }}")
        check("T2 Inguinal opened: every link carried once", processProblems(preview, openTwo).isEmpty(), few(processProblems(preview, openTwo)))
        check("T2 all opened: every link carried once", processProblems(preview, openAll).isEmpty(), few(processProblems(preview, openAll)))
        check("T2 all opened: inside a cell links join notes, between cells the cells themselves",
            openAll.links.all { link ->
                val a = openAll.bodies[link.a]; val b = openAll.bodies[link.b]
                if (link.kind == 5) a.kind == ThemeBodyKind.NOTE && b.kind == ThemeBodyKind.NOTE else a.parent < 0 && b.parent < 0
            } && openAll.links.any { it.kind == 6 && openAll.bodies[it.a].title == "Examples" && openAll.bodies[it.b].title == "Cardiology" },
            "${openAll.links.filter { it.kind == 6 }.map { openAll.bodies[it.a].title + ">" + openAll.bodies[it.b].title }}")
        check("T2 plain links are excitatory: strength 4 one way, 5 both ways",
            openAll.links.all { it.tag == FiberKind.EXCITATORY.code && (it.width == width(4) || it.width == width(5)) }
                && openAll.links.any { it.width == width(5) })
        // what a link carries, read from the notes (GraphAnatomy)
        val dyeFolders = listOf(UniverseFolder(fixedID(700), "Sender", null), UniverseFolder(fixedID(701), "Target", null))
        val dyeNotes = listOf(
            AnatomyNote(fixedID(710), "Benign mimic", "Unlike [[Strangulation]], it never needs surgery.", false, fixedID(700),
                emptyList(), null, listOf(fixedID(712)), listOf(fixedID(711)), 0.0),
            AnatomyNote(fixedID(711), "Strangulation", "Ischaemic bowel.", false, fixedID(701), emptyList(), null, emptyList(), emptyList(), 1.0),
            AnatomyNote(fixedID(712), "Obstruction", "Colicky pain.", false, fixedID(701), emptyList(), null, emptyList(), emptyList(), 2.0),
        )
        val dyeInput = UniverseInput(dyeNotes.map { UniverseNote(it.id, it.title, false, it.folder, 4, it.created) }, dyeFolders,
            listOf(UniverseEdge(fixedID(710), fixedID(711)), UniverseEdge(fixedID(710), fixedID(712))),
            seedByName = true, anatomy = AnatomyInput(dyeNotes, dyeFolders))
        val dyeShut = GraphNeurons.plan(dyeInput)
        check("T2 a contrast and a hand link: one process, dyed by the contrast",
            dyeShut.links.size == 1 && dyeShut.links[0].tag == FiberKind.INHIBITORY.code && dyeShut.links[0].width == width(5), "${dyeShut.links}")
        check("T2 it runs from the cell that sends", dyeShut.links.firstOrNull()?.let { dyeShut.bodies[it.a].title } == "Sender")
        val dyeOpen = allOpen(dyeInput)
        check("T2 both cells opened: still one process, cell to cell, dyed by the contrast",
            dyeOpen.links.size == 1 && dyeOpen.links[0].kind == 6 && dyeOpen.links[0].tag == FiberKind.INHIBITORY.code
                && dyeOpen.links.firstOrNull()?.let { dyeOpen.bodies[it.a].title + ">" + dyeOpen.bodies[it.b].title } == "Sender>Target", "${dyeOpen.links}")
        check("T2 opened: every link carried once", processProblems(dyeInput, dyeOpen).isEmpty(), few(processProblems(dyeInput, dyeOpen)))
        // with Target a part of Sender, both links run inside one cell, each its own
        val nestFolders = listOf(UniverseFolder(fixedID(700), "Sender", null), UniverseFolder(fixedID(701), "Target", fixedID(700)))
        val nestInput = UniverseInput(dyeInput.notes, nestFolders, dyeInput.edges, seedByName = true, anatomy = AnatomyInput(dyeNotes, nestFolders))
        val nestOpen = allOpen(nestInput)
        val dyeTags = nestOpen.links.associate { nestOpen.bodies[it.b].title to it.tag }
        check("T2 inside an opened cell: the contrast inhibitory, the hand link modulatory",
            nestOpen.links.size == dyeTags.size && dyeTags == mapOf("Strangulation" to FiberKind.INHIBITORY.code, "Obstruction" to FiberKind.MODULATORY.code)
                && nestOpen.links.all { it.kind == 5 }, "$dyeTags")
        check("T2 inside an opened cell: every link carried once", processProblems(nestInput, nestOpen).isEmpty(), few(processProblems(nestInput, nestOpen)))
        done()
    }

    // ---- T3 the ladder and nothing meeting, over random vaults ----

    @Test fun t3RandomVaults() {
        val ladderSeen = ArrayList<String>(); val insideSeen = ArrayList<String>(); val meetSeen = ArrayList<String>()
        val processSeen = ArrayList<String>(); val movedSeen = ArrayList<String>(); val envelopeSeen = ArrayList<String>()
        val shownSeen = ArrayList<String>()
        for (seed in 0 until 160) {
            val vault = randomVault(seed.toULong() * 7919uL + 13uL, 60, 12)
            val dice = Dice(seed.toULong() + 99uL)
            val some = vault.folders.map { it.id }.filter { dice.unit() < 0.5 }
            val shut = GraphNeurons.plan(vault)
            val half = planOpen(vault, some)
            val full = allOpen(vault)
            for ((name, plan) in listOf("closed" to shut, "half open" to half, "open" to full)) {
                val tag = "seed $seed $name: "
                if (ladderSeen.size < 3) ladderSeen += ladderProblems(plan).take(1).map { tag + it }
                if (insideSeen.size < 3) insideSeen += insideProblems(plan).take(1).map { tag + it }
                if (meetSeen.size < 3) meetSeen += meetProblems(plan).take(1).map { tag + it }
                if (processSeen.size < 3) processSeen += processProblems(vault, plan).take(1).map { tag + it }
                if (envelopeSeen.size < 3) envelopeSeen += envelopeProblems(plan).take(1).map { tag + it }
            }
            if (movedSeen.size < 3) movedSeen += (movedProblems(shut, half) + movedProblems(half, full)).take(1).map { "seed $seed: $it" }
            val notes = full.bodies.count { it.kind == ThemeBodyKind.NOTE }
            val folders = full.bodies.count { it.kind != ThemeBodyKind.NOTE }
            val wantFolders = if (vault.folders.isEmpty()) (if (vault.notes.isEmpty()) 0 else 1) else vault.folders.size
            if ((notes != vault.notes.size || folders != wantFolders) && shownSeen.size < 3)
                shownSeen.add("seed $seed: $notes of ${vault.notes.size} notes, $folders of $wantFolders folders")
        }
        check("T3 all opened, every note and folder shows", shownSeen.isEmpty(), few(shownSeen))
        check("T3 the ladder holds in 160 random vaults, closed, half open and open", ladderSeen.isEmpty(), few(ladderSeen))
        check("T3 nothing leaves its membrane, enters a nucleus or meets a sibling, drifting for two minutes", insideSeen.isEmpty(), few(insideSeen))
        check("T3 no two cells or free cells meet", meetSeen.isEmpty(), few(meetSeen))
        check("T3 every link carried by exactly one process", processSeen.isEmpty(), few(processSeen))
        check("T3 opening moves nothing already shown", movedSeen.isEmpty(), few(movedSeen))
        check("T3 the framing holds every cell", envelopeSeen.isEmpty(), few(envelopeSeen))
        done()
    }

    // ---- T4 the same notes, the same picture ----

    @Test fun t4SamePicture() {
        val closed = GraphNeurons.plan(preview)
        val openOne = planOpen(preview, listOf(EXAMPLES))
        val openTwo = planOpen(preview, listOf(EXAMPLES, INGUINAL))
        val openAll = allOpen(preview)
        check("T4 planned twice, the same", allOpen(preview).bodies == openAll.bodies && allOpen(preview).links == openAll.links)
        val flippedInput = UniverseInput(preview.notes.reversed(), preview.folders.reversed(), preview.edges.reversed(), seedByName = true)
        val flipped = allOpen(flippedInput)
        check("T4 the order notes come in changes nothing", flipped.bodies == openAll.bodies && flipped.links == openAll.links)
        check("T4 opening Examples moves nothing shown", movedProblems(closed, openOne).isEmpty(), few(movedProblems(closed, openOne)))
        check("T4 opening Inguinal moves nothing shown", movedProblems(openOne, openTwo).isEmpty(), few(movedProblems(openOne, openTwo)))
        check("T4 opening everything moves nothing shown", movedProblems(openTwo, openAll).isEmpty(), few(movedProblems(openTwo, openAll)))
        check("T4 closing hides only what was inside", closed.bodies.map { it.id }.toSet() == openAll.bodies.filter { it.parent < 0 }.map { it.id }.toSet())
        check("T4 opening keeps the framing", closed.envelope == openAll.envelope)
        val unlinked = allOpen(UniverseInput(preview.notes, preview.folders, emptyList(), seedByName = true))
        check("T4 without links: no processes, the loose notes drift free",
            unlinked.links.isEmpty() && titles(unlinked, NeuronRole.DRIFTER) == setOf("Richter's hernia", "Pericarditis")
                && titles(unlinked, NeuronRole.RECEPTOR).isEmpty(), unlinked.summary)
        done()
    }

    // ---- T5 the sheet: senders first, each cell facing the one it sends to ----

    private fun flowInput(): UniverseInput {
        val names = listOf("Zeta", "Mid", "Alpha")
        val folders = ArrayList<UniverseFolder>(); val notes = ArrayList<UniverseNote>()
        for ((f, name) in names.withIndex()) {
            folders.add(UniverseFolder(fixedID(600 + f), name, null))
            for (k in 0 until 3) notes.add(UniverseNote(fixedID(610 + f * 10 + k), "$name note $k", k == 0, fixedID(600 + f), 80, (f * 10 + k).toDouble()))
        }
        val edges = ArrayList<UniverseEdge>()
        for (k in 0 until 3) {
            edges.add(UniverseEdge(fixedID(610 + k), fixedID(620 + k)))
            edges.add(UniverseEdge(fixedID(620 + k), fixedID(630 + k)))
        }
        return UniverseInput(notes, folders, edges, seedByName = true)
    }

    /** The lift the planner chose for the top cells (A, from any cell's z over its u), and whether every top cell sits at A·u(id). */
    private fun cellLift(plan: ThemePlan, byName: Boolean): Pair<Float, Boolean> {
        val tops = plan.bodies.filter { it.depth == 0 && it.kind != ThemeBodyKind.NOTE }
        fun key(body: ThemeBody) = if (byName) body.title else body.id
        val sorted = tops.sortedBy { GraphTilt.u(key(it)) }
        if (sorted.size <= 1) return 0f to true
        val low = sorted.first(); val high = sorted.last()
        val spread = GraphTilt.u(key(high)) - GraphTilt.u(key(low))
        val lift = if (spread > 0.0001f) (high.home.z - low.home.z) / spread else 0f
        val shift = low.home.z - GraphTilt.z(key(low), lift)
        val ok = lift >= -0.0001f && tops.all { abs(it.home.z - shift - GraphTilt.z(key(it), lift)) <= 0.001f + 0.001f * lift }
        return lift to ok
    }

    @Test fun t5Sheet() {
        val flow = GraphNeurons.plan(flowInput())
        val zeta = bodyNamed(flow, "Zeta"); val mid = bodyNamed(flow, "Mid"); val alpha = bodyNamed(flow, "Alpha")
        if (zeta != null && mid != null && alpha != null) {
            val z = flow.bodies[zeta]; val m = flow.bodies[mid]; val a = flow.bodies[alpha]
            check("T5 the sender first: Zeta left of Mid on the top row, Alpha below",
                z.home.x < m.home.x && z.home.y > a.home.y && m.home.y > a.home.y, "${z.home} ${m.home} ${a.home}")
            val toMid = (m.home - z.home) * (1f / distance(m.home, z.home))
            val toAlpha = (a.home - m.home) * (1f / distance(a.home, m.home))
            check("T5 Zeta faces Mid and Mid faces Alpha", z.axis.dot(toMid) > 0.95f && m.axis.dot(toAlpha) > 0.95f, "${z.axis} ${m.axis}")
            check("T5 Alpha, sending nowhere, faces down the sheet", same(a.axis, Vec3(0f, -1f, 0f)), "${a.axis}")
            val xs = listOf(z, m, a).map { it.home.x }; val ys = listOf(z, m, a).map { it.home.y }
            val reach = (listOf(z, m, a).maxOf { it.sphere }) * 2
            val half = max(xs.max() - xs.min(), ys.max() - ys.min()) * 0.5f + reach
            check("T5 the sheet faces the camera, each cell within its bounded depth (0.35 of the half extent)",
                listOf(z, m, a).all { abs(it.home.z) <= 0.35f * half + 0.02f }, "${listOf(z.home.z, m.home.z, a.home.z)}")
            check("T5 the processes run with the flow",
                flow.links.map { "${flow.bodies[it.a].title}>${flow.bodies[it.b].title}" }.toSet() == setOf("Zeta>Mid", "Mid>Alpha")
                    && flow.links.all { it.kind == 6 }, "${flow.links}")
        } else check("T5 the three cells", false, flow.summary)
        check("T5 nothing meets", meetProblems(flow).isEmpty(), few(meetProblems(flow)))
        val dyeFolders = listOf(UniverseFolder(fixedID(700), "Sender", null), UniverseFolder(fixedID(701), "Target", null))
        val dyeNotes = listOf(
            AnatomyNote(fixedID(710), "Benign mimic", "Unlike [[Strangulation]], it never needs surgery.", false, fixedID(700),
                emptyList(), null, listOf(fixedID(712)), listOf(fixedID(711)), 0.0),
            AnatomyNote(fixedID(711), "Strangulation", "Ischaemic bowel.", false, fixedID(701), emptyList(), null, emptyList(), emptyList(), 1.0),
            AnatomyNote(fixedID(712), "Obstruction", "Colicky pain.", false, fixedID(701), emptyList(), null, emptyList(), emptyList(), 2.0),
        )
        val dyeShut = GraphNeurons.plan(UniverseInput(dyeNotes.map { UniverseNote(it.id, it.title, false, it.folder, 4, it.created) }, dyeFolders,
            listOf(UniverseEdge(fixedID(710), fixedID(711)), UniverseEdge(fixedID(710), fixedID(712))),
            seedByName = true, anatomy = AnatomyInput(dyeNotes, dyeFolders)))
        for ((name, plan) in listOf("preview closed" to GraphNeurons.plan(preview), "preview all open" to allOpen(preview), "dye" to dyeShut)) {
            val (_, ok) = cellLift(plan, byName = true)
            check("T5 $name: every top cell at A·u(id), |z| <= A", ok)
        }
        done()
    }

    // ---- T6 edge cases ----

    @Test fun t6EdgeCases() {
        // no folders: one home cell holds every note, always open
        val homeNotes = listOf(UniverseNote(fixedID(801), "Murmur grading", true, null, 300, 0.0),
            UniverseNote(fixedID(802), "Splitting S2", false, null, 40, 1.0), UniverseNote(fixedID(803), "Opening snap", false, null, 20, 2.0))
        val homeInput = UniverseInput(homeNotes, emptyList(), listOf(UniverseEdge(fixedID(801), fixedID(802)), UniverseEdge(fixedID(802), fixedID(803))), seedByName = true)
        val homePlan = GraphNeurons.plan(homeInput)
        check("T6 no folders: one home cell first, holding every note",
            homePlan.bodies.firstOrNull()?.id == GraphUniverse.HOME_ID && homePlan.bodies.firstOrNull()?.role == NeuronRole.HOME.code
                && homePlan.bodies.size == 4 && homePlan.bodies.drop(1).all { it.parent == 0 } && homePlan.regions == listOf(0), homePlan.summary)
        check("T6 no folders: the summary", homePlan.summary == "1 cell, 1 vesicle, 2 granules", homePlan.summary)
        check("T6 no folders: links inside the cell, each carried",
            homePlan.links.size == 2 && homePlan.links.all { it.kind == 5 } && processProblems(homeInput, homePlan).isEmpty(), few(processProblems(homeInput, homePlan)))
        check("T6 no folders: the ladder holds, nothing meets", ladderProblems(homePlan).isEmpty() && insideProblems(homePlan).isEmpty(),
            few(ladderProblems(homePlan) + insideProblems(homePlan)))

        // a cycle, A inside B inside A, is cut: nothing lost
        val cycleInput = UniverseInput(
            listOf(UniverseNote(fixedID(811), "In A", false, fixedID(821), 30, 0.0), UniverseNote(fixedID(812), "In B", false, fixedID(822), 30, 1.0)),
            listOf(UniverseFolder(fixedID(821), "A", fixedID(822)), UniverseFolder(fixedID(822), "B", fixedID(821))),
            listOf(UniverseEdge(fixedID(811), fixedID(812))), seedByName = true)
        val cycleShut = GraphNeurons.plan(cycleInput)
        val cycleOpen = allOpen(cycleInput)
        check("T6 a cycle: one cell, one part inside it, both notes", cycleShut.summary == "1 cell" && cycleOpen.summary == "1 cell, 1 part, 2 granules", cycleOpen.summary)
        check("T6 a cycle: the ladder holds, nothing meets, every link carried",
            ladderProblems(cycleOpen).isEmpty() && insideProblems(cycleOpen).isEmpty() && processProblems(cycleInput, cycleOpen).isEmpty()
                && processProblems(cycleInput, cycleShut).isEmpty(),
            few(ladderProblems(cycleOpen) + insideProblems(cycleOpen) + processProblems(cycleInput, cycleOpen)))

        // ten folders deep, one note in each, linked down the chain
        val deepFolders = (0 until 10).map { k -> UniverseFolder(fixedID(830 + k), "D$k", if (k == 0) null else fixedID(829 + k)) }
        val deepNotes = (0 until 10).map { k -> UniverseNote(fixedID(850 + k), "Deep note $k", k % 2 == 0, fixedID(830 + k), 120, k.toDouble()) }
        val deepInput = UniverseInput(deepNotes, deepFolders, (0 until 9).map { UniverseEdge(fixedID(850 + it), fixedID(851 + it)) }, seedByName = true)
        val deep = allOpen(deepInput)
        check("T6 ten deep: one cell, nine parts each inside the last, ten notes",
            titles(deep, NeuronRole.CELL) == setOf("D0") && titles(deep, NeuronRole.PART).size == 9 && deep.bodies.size == 20, deep.summary)
        val smallest = deep.bodies.minOfOrNull { it.sphere } ?: 0f
        check("T6 ten deep: the smallest still has a size", smallest > 0 && smallest.isFinite(), "$smallest")
        check("T6 ten deep: the ladder holds, nothing leaves its membrane", ladderProblems(deep).isEmpty() && insideProblems(deep).isEmpty(),
            few(ladderProblems(deep) + insideProblems(deep)))
        check("T6 ten deep: every link carried", processProblems(deepInput, deep).isEmpty(), few(processProblems(deepInput, deep)))
        val deepFirst = planOpen(deepInput, listOf(fixedID(830)))
        check("T6 ten deep, only the cell opened: it, its part and its note", deepFirst.bodies.size == 3 && deepFirst.summary == "1 cell, 1 part, 1 vesicle", deepFirst.summary)
        check("T6 ten deep: a part opened inside a closed cell shows nothing more", planOpen(deepInput, listOf(fixedID(831))).bodies.size == 1)

        // empty folders: a bare cell, a bare part
        val emptyInput = UniverseInput(listOf(UniverseNote(fixedID(871), "Lonely fact", false, fixedID(861), 10, 0.0)),
            listOf(UniverseFolder(fixedID(860), "Empty", null), UniverseFolder(fixedID(861), "Full", null), UniverseFolder(fixedID(862), "Hollow", fixedID(861))),
            emptyList(), seedByName = true)
        val emptyOpen = allOpen(emptyInput)
        val bare = bodyNamed(emptyOpen, "Empty"); val hollow = bodyNamed(emptyOpen, "Hollow")
        if (bare != null && hollow != null) {
            check("T6 an empty folder: the smallest cell, nothing inside it when opened",
                emptyOpen.bodies[bare].sphere == GraphNeurons.cellSphere(0).toFloat() && emptyOpen.bodies.none { it.parent == bare }, "${emptyOpen.bodies[bare].sphere}")
            check("T6 an empty part: inside its cell, nothing inside it", emptyOpen.bodies[hollow].role == NeuronRole.PART.code && emptyOpen.bodies.none { it.parent == hollow })
        } else check("T6 empty folders show", false, emptyOpen.summary)
        check("T6 empty folders: the ladder holds", ladderProblems(emptyOpen).isEmpty() && insideProblems(emptyOpen).isEmpty(),
            few(ladderProblems(emptyOpen) + insideProblems(emptyOpen)))
        val nothing = GraphNeurons.plan(UniverseInput(emptyList(), emptyList(), emptyList(), seedByName = true))
        check("T6 nothing: an empty plan", nothing.bodies.isEmpty() && nothing.links.isEmpty() && nothing.summary.isEmpty())
        val bareInput = UniverseInput(emptyList(), emptyInput.folders, emptyList(), seedByName = true)
        check("T6 folders and no notes: cells and parts only",
            GraphNeurons.plan(bareInput).summary == "2 cells" && allOpen(bareInput).summary == "2 cells, 1 part", allOpen(bareInput).summary)

        // forty loose notes round one cell: twenty linked into it, twenty not
        val looseNotes = ArrayList<UniverseNote>(); val looseEdges = ArrayList<UniverseEdge>()
        for (k in 0 until 4) looseNotes.add(UniverseNote(fixedID(880 + k), "Hub note $k", false, fixedID(879), 50, k.toDouble()))
        for (k in 0 until 40) {
            looseNotes.add(UniverseNote(fixedID(1100 + k), "Loose $k", k % 5 == 0, null, 30, (10 + k).toDouble()))
            if (k < 20) looseEdges.add(UniverseEdge(fixedID(1100 + k), fixedID(880 + k % 4)))
        }
        val looseInput = UniverseInput(looseNotes, listOf(UniverseFolder(fixedID(879), "Hub", null)), looseEdges, seedByName = true)
        val loosePlan = GraphNeurons.plan(looseInput)
        check("T6 forty loose: twenty receptors, twenty free cells", loosePlan.summary == "1 cell, 20 receptors, 20 free cells", loosePlan.summary)
        check("T6 forty loose: no two meet", meetProblems(loosePlan).isEmpty(), few(meetProblems(loosePlan)))
        check("T6 forty loose: the framing holds them", envelopeProblems(loosePlan).isEmpty(), few(envelopeProblems(loosePlan)))
        val hub = bodyNamed(loosePlan, "Hub")
        if (hub != null) {
            val hubBody = loosePlan.bodies[hub]
            val turned = loosePlan.bodies.filter { roleOf(it) == NeuronRole.RECEPTOR }.mapNotNull { body ->
                val to = (hubBody.home - body.home) * (1f / distance(hubBody.home, body.home))
                if (body.axis.dot(to) > 0.9f) null else body.title
            }
            check("T6 forty loose: each receptor faces the cell it sends to", turned.isEmpty(), "$turned")
            val crowding = loosePlan.bodies.filter { roleOf(it).isFree }.mapNotNull { body ->
                if (distance(hubBody.home, body.home) >= hubBody.sphere * GraphNeurons.REACH.toFloat() + body.sphere) null else body.title
            }
            check("T6 forty loose: all clear of the cell's processes", crowding.isEmpty(), "$crowding")
            check("T6 forty loose: every process runs from a receptor into the cell",
                loosePlan.links.size == 20 && loosePlan.links.all { roleOf(loosePlan.bodies[it.a]) == NeuronRole.RECEPTOR && it.b == hub && it.kind == 6 },
                "${loosePlan.links.size}")
        } else check("T6 forty loose: the hub cell", false, loosePlan.summary)
        val looseOpen = allOpen(looseInput)
        check("T6 forty loose, opened: every link carried, nothing meets",
            processProblems(looseInput, looseOpen).isEmpty() && meetProblems(looseOpen).isEmpty() && insideProblems(looseOpen).isEmpty(),
            few(processProblems(looseInput, looseOpen) + meetProblems(looseOpen)))
        done()
    }

    // ---- T7 scale ----

    @Test fun t7Scale() {
        val started = System.nanoTime()
        val bigInput = randomVault(4242uL, 300, 40, exact = true)
        val big = allOpen(bigInput)
        val took = (System.nanoTime() - started) / 1e9
        check("T7 300 notes, all opened: every note planned", big.bodies.count { it.kind == ThemeBodyKind.NOTE } == 300, big.summary)
        check("T7 300 notes: planned in under 2 s", took < 2, "$took s")
        check("T7 300 notes: the ladder holds", ladderProblems(big).isEmpty(), few(ladderProblems(big)))
        check("T7 300 notes: nothing leaves its membrane or meets", insideProblems(big).isEmpty() && meetProblems(big).isEmpty(),
            few(insideProblems(big) + meetProblems(big)))
        check("T7 300 notes: every link carried once", processProblems(bigInput, big).isEmpty(), few(processProblems(bigInput, big)))
        check("T7 300 notes: the framing holds", envelopeProblems(big).isEmpty(), few(envelopeProblems(big)))
        val crowdNotes = (0 until 200).map { k -> UniverseNote(fixedID(2000 + k), "Crowd $k", k % 3 == 0, fixedID(1999), 20 + k * 7, k.toDouble()) }
        val crowd = allOpen(UniverseInput(crowdNotes, listOf(UniverseFolder(fixedID(1999), "Crowded", null)), emptyList(), seedByName = true))
        check("T7 two hundred notes in one cell: all inside, none meeting",
            crowd.bodies.size == 201 && insideProblems(crowd).isEmpty() && ladderProblems(crowd).isEmpty(), few(insideProblems(crowd) + ladderProblems(crowd)))
        done()
    }

    // ---- T8 the theme choice ----

    @Test fun t8ThemeChoice() {
        check("T8 Space is the default", GraphTheme.stored(null) == GraphTheme.SPACE && GraphTheme.stored("nonsense") == GraphTheme.SPACE)
        check("T8 Neurons is kept", GraphTheme.stored("neurons") == GraphTheme.NEURONS)
        check("T8 a removed theme falls back: a stored Circuit opens in Space", GraphTheme.stored("circuit") == GraphTheme.SPACE)
        check("T8 the menu offers only ready themes", GraphTheme.offered.all { it.isReady } && GraphTheme.offered.firstOrNull() == GraphTheme.SPACE)
        check("T8 the themes' words are their own", GraphTheme.entries.map { it.legendTitle }.toSet().size == GraphTheme.entries.size
            && GraphTheme.NEURONS.title == "Neurons" && GraphTheme.stored("space") == GraphTheme.SPACE)
        check("T8 the theme's key is the one the Look menu writes", GraphTheme.KEY == "vignette.space.theme")
        done()
    }

    // ---- T9 drifting ----

    @Test fun t9Drifting() {
        val still = GraphOrbit.Drift(Vec3(1f, 2f, 3f), 0f, 1f, 1f)
        check("T9 no wobble without amplitude", same(GraphUniverse.offset(still, 123.0), Vec3(1f, 2f, 3f)))
        val wob = GraphOrbit.Drift(Vec3(0f, 0f, 0f), 0.05f, 0.3f, 0.4f)
        var widest = 0f
        var moved = false
        var last = GraphUniverse.offset(wob, 0.0)
        for (k in 1..400) {
            val p = GraphUniverse.offset(wob, k * 0.25)
            widest = max(widest, p.length())
            if (distance(p, last) > 0.001f) moved = true
            last = p
        }
        check("T9 a drift stays within its amplitude", widest <= 0.05f * 1.6f, "$widest")
        check("T9 a drift moves", moved)
        val late = GraphUniverse.offset(wob, 2_999.5)
        check("T9 a drift is fine after 50 minutes", late.x.isFinite() && late.dot(late) < 0.01f)
        done()
    }

    // ---- T10 impulses at random times ----

    @Test fun t10Impulses() {
        val rhythms = (0 until 64).map { NeuronImpulse.rhythm(it) }
        check("T10 slots 0.9 to 2.4 s, travel 0.45 to 0.95 s", rhythms.all { it.slot >= 0.9f && it.slot <= 2.4f && it.travel >= 0.45f && it.travel <= 0.95f })
        check("T10 links keep their own rhythm", rhythms.map { it.slot }.toSet().size > 40)
        val refractory = ArrayList<String>()
        var fired = 0
        var span = 0f
        for (seed in 0 until 200) {
            val times = NeuronImpulse.firings(seed, 0f, 600f, 0.6f)
            val r = NeuronImpulse.rhythm(seed)
            fired += times.size
            span += 600 / r.slot
            for (k in 1 until max(times.size, 1)) if (times[k] - times[k - 1] < 0.5f * r.slot - 0.001f && refractory.size < 3) refractory.add("seed $seed: ${times[k - 1]} then ${times[k]}")
        }
        check("T10 never two firings within half a slot (refractory)", refractory.isEmpty(), "$refractory")
        val share = fired / span
        check("T10 about the budget's share of slots fire", share > 0.5f && share < 0.7f, "$share")
        check("T10 rate 0 never fires", NeuronImpulse.firings(3, 0f, 600f, 0f).isEmpty() && NeuronImpulse.arrivals(3, 0f, 600f, 0f, 1f).isEmpty())
        val starts = (0 until 40).mapNotNull { NeuronImpulse.firings(it, 0f, 30f, 0.6f).firstOrNull() }
        check("T10 links do not fire in step", starts.map { round(it * 100) }.toSet().size > 30, "${starts.size}")
        // every firing lands one travel time later, seen frame by frame at 60 a second
        val missed = ArrayList<String>()
        for (seed in listOf(1, 7, 99, 512)) {
            val r = NeuronImpulse.rhythm(seed)
            val landed = ArrayList<Float>()
            var t = 5f
            val frame = 1.0f / 60.0f
            while (t + frame <= 61) { landed += NeuronImpulse.arrivals(seed, t, t + frame, 0.6f, 0f); t += frame }
            val due = NeuronImpulse.firings(seed, 0f, 61f, 0.6f).map { it + r.travel }.filter { it > 5.01f && it < t - 0.01f }
            for (d in due) if (landed.none { abs(it - d) < 0.002f } && missed.size < 3) missed.add("seed $seed due $d")
            if (landed.size > due.size + 1 && missed.size < 3) missed.add("seed $seed: ${landed.size} for ${due.size}")
        }
        check("T10 each impulse arrives once, a travel time after it fires", missed.isEmpty(), "$missed")
        var burstSeen = false
        for (seed in 0 until 100) {
            if (burstSeen) break
            val sorted = NeuronImpulse.arrivals(seed, 0f, 3000f, 1f, 0.2f).sorted()
            for (k in 1 until max(sorted.size, 1)) if (abs(sorted[k] - sorted[k - 1] - NeuronImpulse.BURST_GAP) < 0.001f) burstSeen = true
        }
        check("T10 bursts arrive 0.12 s apart", burstSeen)
        var agree = true
        for (seed in 0 until 50) {
            var t = 0f
            while (t < 30) {
                val many = NeuronImpulse.arrivals(seed, t, t + 0.05f, 0.6f, 0.2f).isNotEmpty()
                if (many != NeuronImpulse.lands(seed, t, t + 0.05f, 0.6f, 0.2f)) agree = false
                t += 0.05f
            }
        }
        check("T10 the per-frame check agrees with the full list", agree)
        check("T10 the rhythm follows the whole seed", NeuronImpulse.hash(5u) == NeuronImpulse.hash(5u) && NeuronImpulse.rhythm(1024 + 5) != NeuronImpulse.rhythm(5))
        done()
    }

    // ---- the vaults ----

    /** iOS Dice: a splitmix64 source for the random vaults. */
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

    /** A random vault: up to [maxNotes] notes, [maxFolders] folders nested up to depth 6, random links. */
    private fun randomVault(seed: ULong, maxNotes: Int, maxFolders: Int, exact: Boolean = false): UniverseInput {
        val dice = Dice(seed)
        val folderCount = dice.below(maxFolders + 1)
        val folders = ArrayList<UniverseFolder>()
        val depthOf = ArrayList<Int>()
        for (k in 0 until folderCount) {
            val id = fixedID(10_000 + k)
            var parent: String? = null
            var d = 0
            if (k > 0 && dice.unit() < 0.6) {
                val p = dice.below(k)
                if (depthOf[p] < 6) { parent = folders[p].id; d = depthOf[p] + 1 }
            }
            folders.add(UniverseFolder(id, "Folder $k", parent))
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
        val linkCount = noteCount * 13 / 10
        if (noteCount > 1) for (i in 0 until linkCount) {
            val a = dice.below(noteCount)
            val b = dice.below(noteCount)
            edges.add(UniverseEdge(fixedID(a + 1), fixedID(b + 1)))
        }
        return UniverseInput(notes, folders, edges, seedByName = false)
    }

    companion object {
        fun fixedID(n: Int): String = "00000000-0000-4000-8000-" + String.format("%012X", n)

        /** Times to look at drifting things: two minutes, at uneven steps. */
        val looks: List<Double> = (0 until 24).map { it * 5.3 + 0.17 }

        val EXAMPLES = fixedID(900)
        val INGUINAL = fixedID(901)

        /** `[[Title]]` in a body, as NoteStore.wikiTitles reads it. */
        fun wikiTitles(text: String): List<String> {
            val titles = ArrayList<String>()
            for (piece in text.split("[[").drop(1)) {
                val close = piece.indexOf("]]")
                if (close < 0) continue
                val inner = piece.substring(0, close)
                if (inner.contains("\n")) continue
                val target = inner.split("|").firstOrNull { it.isNotEmpty() } ?: ""
                val title = target.trim { it == ' ' || it == '\t' }
                if (title.isNotEmpty()) titles.add(title)
            }
            return titles
        }

        /** The design preview's store (iOS previewInput). */
        val preview: UniverseInput by lazy {
            val folderNames = listOf("Examples" to null, "Inguinal" to "Examples", "Femoral" to "Examples", "Cardiology" to null, "Anatomy" to "Inguinal")
            val folderID = HashMap<String, String>()
            for ((k, pair) in folderNames.withIndex()) folderID[pair.first] = fixedID(900 + k)
            val folders = folderNames.map { (name, parent) -> UniverseFolder(folderID.getValue(name), name, parent?.let { folderID[it] }) }
            val byTitle = HashMap<String, String>()
            val notes = ArrayList<UniverseNote>()
            for ((k, draft) in NeuronPreviewData.drafts.withIndex()) {
                val id = fixedID(k + 1)
                byTitle[draft.title.lowercase()] = id
                notes.add(UniverseNote(id, draft.title, draft.page, draft.folder?.let { folderID[it] }, GraphUniverse.wordCount(draft.body), k.toDouble()))
            }
            val edges = ArrayList<UniverseEdge>()
            for ((k, draft) in NeuronPreviewData.drafts.withIndex()) for (title in wikiTitles(draft.body)) {
                val to = byTitle[title.lowercase()] ?: continue
                edges.add(UniverseEdge(fixedID(k + 1), to))
            }
            for ((a, b) in NeuronPreviewData.handLinks) {
                val x = byTitle[a.lowercase()] ?: continue
                val y = byTitle[b.lowercase()] ?: continue
                edges.add(UniverseEdge(x, y))
            }
            UniverseInput(notes, folders, edges, seedByName = true)
        }
    }
}
