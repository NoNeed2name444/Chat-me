package com.cramdown.sorta.map

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

// iOS NeuronLookTests.swift (N1-N6), its checks as they are: the palette, the six cell states
// and the Look menu's stored choice, the bokeh and the far cells, and the shaders reading the
// state, the palette, the opened nucleus and the pacemaker's beat. The shader strings are GLSL
// here, so N5's float3(...) reads vec3(...). Then the GLSL's shape, which no JVM can compile:
// what Metal or SceneKit left behind, balanced brackets, every uniform and local declared before
// use and none twice in one scope, the vertex outputs feeding the fragment inputs, and no int
// literal where GLSL ES wants a float. Then the Neurons' depth band and the membranes' mouths.
class NeuronLookTest {
    private val failures = ArrayList<String>()
    private fun check(label: String, ok: Boolean, detail: String = "") { if (!ok) failures.add("$label | $detail") }
    private fun done() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    private fun onScreen(c: Vec3) = minOf(c.x, c.y, c.z) >= 0 && maxOf(c.x, c.y, c.z) <= 1
    private fun maxOf3(c: Vec3) = maxOf(c.x, c.y, c.z)
    private fun distance(a: Vec3, b: Vec3) = (a - b).length()
    private fun fixedID(n: Int): String = "00000000-0000-4000-8000-" + String.format("%012X", n)

    @Test fun n1Palette() {
        val mains = NeuronPalette.dyes.map { it.main }
        check("N1 every dye and accent on screen's scale", NeuronPalette.dyes.all { onScreen(it.main) && onScreen(it.accent) })
        val green = mains.any { it.y > 0.9f && it.x < 0.5f && it.z < 0.6f }
        val cyan = mains.any { it.y > 0.85f && it.z > 0.9f && it.x < 0.4f }
        val pink = mains.any { it.x > 0.9f && it.z > 0.6f && it.y < 0.5f }
        val amber = mains.any { it.x > 0.9f && it.y > 0.6f && it.z < 0.4f }
        check("N1 green, cyan, pink and amber dyes", green && cyan && pink && amber)
        val deep = NeuronPalette.deep
        check("N1 the fluid deep blue: dark, blue the strongest", deep.z > deep.y && deep.y > deep.x && maxOf3(deep) < 0.12f)
        val i = NeuronPalette.interior; val h = NeuronPalette.heart
        check("N1 the interior deep violet, the heart a brighter violet",
            i.z > i.x && i.x > i.y && h.z > h.x && h.x > h.y && maxOf3(h) > maxOf3(i))
        val first = NeuronPalette.dye(0)
        val firstIdea = NeuronPalette.dye(0, idea = true)
        val accent = NeuronPalette.dyes[0].accent
        check("N1 an idea leans to its cell's accent", distance(firstIdea, accent) < distance(first, accent) && firstIdea != first)
        check("N1 slots wrap round, 5 and 6 a receptor's and a drifter's",
            NeuronPalette.dye(5) == NeuronPalette.receptor && NeuronPalette.dye(6) == NeuronPalette.drifter
                && NeuronPalette.dye(7) == NeuronPalette.dye(2))
        check("N1 a cell's glow leans to the violet, a receptor's and a drifter's keep their own",
            (0 until 5).all { slot ->
                listOf(false, true).all { idea ->
                    val glow = NeuronPalette.glow(slot, idea)
                    glow.z > glow.y && onScreen(glow) && glow != NeuronPalette.dye(slot, idea)
                }
            } && NeuronPalette.glow(5) == NeuronPalette.receptor && NeuronPalette.glow(6) == NeuronPalette.drifter)
        val two = listOf(NeuronPalette.dyes[0].main, NeuronPalette.dyes[0].accent, NeuronPalette.dyes[1].main, NeuronPalette.dyes[1].accent)
        val hasAll = two.any { it.y > 0.9f && it.x < 0.5f } && two.any { it.z > 0.9f && it.x < 0.4f } &&
            two.any { it.x > 0.9f && it.z > 0.6f } && two.any { it.x > 0.9f && it.z < 0.4f }
        check("N1 two cells' dyes and accents give all four colours", hasAll)
        done()
    }

    @Test fun n2States() {
        val all = NeuronState.entries
        check("N2 six states, six codes 0...5", all.size == 6 && all.map { it.code }.toSet() == (0..5).toSet())
        check("N2 each mirrors one space style and back", all.all { NeuronState.of(it.style) == it } && all.map { it.style.code }.toSet().size == 6)
        check("N2 the menu in the space styles' order", NeuronState.menuOrder.map { it.style } == SpaceStyle.menuOrder)
        check("N2 each its own title, process and symbol", all.map { it.title }.toSet().size == 6 &&
            all.map { it.process }.toSet().size == 6 && all.map { it.symbol }.toSet().size == 6)
        val resting = listOf(NeuronRole.CELL, NeuronRole.PART, NeuronRole.HOME, NeuronRole.VESICLE, NeuronRole.GRANULE)
        check("N2 natural: a receptor migrates, a drifter engulfs, the rest rest",
            NeuronState.natural(NeuronRole.RECEPTOR) == NeuronState.MIGRATING && NeuronState.natural(NeuronRole.DRIFTER) == NeuronState.ENGULFING
                && resting.all { NeuronState.natural(it) == NeuronState.RESTING })
        check("N2 a pacemaker beats with the pulsar's period", NeuronState.beatPeriod == SpaceOptics.PULSAR_PERIOD)
        done()
    }

    @Test fun n3Choice() {
        val folderA = fixedID(900)
        val folderB = fixedID(901)
        val notes = (0 until 12).map { k ->
            val folder = if (k < 5) folderA else if (k < 10) folderB else null
            UniverseNote(fixedID(k + 1), "Note $k", k % 3 == 0, folder, 50 + k * 40, k.toDouble())
        }
        val edges = (0 until 11).map { k -> UniverseEdge(fixedID(k + 1), fixedID(((k * 5) % 12) + 1)) }
        val closed = UniverseInput(notes, listOf(UniverseFolder(folderA, "Examples", null), UniverseFolder(folderB, "Cardiology", null)),
            edges, seedByName = true)
        check("N3 a closed cell shows nothing inside", GraphNeurons.plan(closed).bodies.count { it.kind == ThemeBodyKind.NOTE } == 2)
        val plan = GraphNeurons.plan(closed.copy(open = listOf(folderA, folderB)))
        val noteIndices = plan.bodies.indices.filter { plan.bodies[it].kind == ThemeBodyKind.NOTE }
        val containers = plan.bodies.indices.filter { plan.bodies[it].kind != ThemeBodyKind.NOTE }
        val regionA = plan.bodies.indexOfFirst { it.id == folderA }
        val natural = NeuronStateChoice.NATURAL
        check("N3 natural: each note its role's state", noteIndices.all { i ->
            natural.state(i, plan) == NeuronState.natural(NeuronRole.of(plan.bodies[i].role) ?: NeuronRole.GRANULE)
        })
        val allFiring = NeuronStateChoice(main = NeuronState.FIRING.rawValue)
        check("N3 one state for every note", noteIndices.isNotEmpty() && noteIndices.all { allFiring.state(it, plan) == NeuronState.FIRING })
        check("N3 containers always rest", containers.isNotEmpty() && containers.all { allFiring.state(it, plan) == NeuronState.RESTING })
        val mixed = NeuronStateChoice(main = NeuronState.FIRING.rawValue, folders = mapOf(folderA to NeuronState.ENGULFING))
        val inA = noteIndices.filter { plan.bodies[it].region == regionA }
        val outA = noteIndices.filter { plan.bodies[it].region != regionA }
        check("N3 a cell's state wins for the notes inside", regionA >= 0 && inA.isNotEmpty()
            && inA.all { mixed.state(it, plan) == NeuronState.ENGULFING } && outA.all { mixed.state(it, plan) == NeuronState.FIRING },
            "A $regionA in ${inA.map { mixed.state(it, plan).rawValue }} out ${outA.map { mixed.state(it, plan).rawValue }}")
        check("N3 out of range: resting", mixed.state(-1, plan) == NeuronState.RESTING && mixed.state(plan.bodies.size, plan) == NeuronState.RESTING)
        val raw = NeuronStateChoice.encode(mapOf(folderA to NeuronState.ENGULFING, folderB to NeuronState.PACEMAKER))
        val back = NeuronStateChoice.of("migrating", raw)
        check("N3 stored strings read back", back.folders == mapOf(folderA to NeuronState.ENGULFING, folderB to NeuronState.PACEMAKER)
            && back.main == "migrating" && back.isCustom)
        check("N3 the stored form is stable", raw == NeuronStateChoice.encode(mapOf(folderB to NeuronState.PACEMAKER, folderA to NeuronState.ENGULFING)))
        check("N3 anything unreadable skipped",
            NeuronStateChoice.parse("junk;$folderA=nonsense;x=y;$folderB=firing") == mapOf(folderB to NeuronState.FIRING))
        check("N3 natural is not custom", !NeuronStateChoice.NATURAL.isCustom && !NeuronStateChoice.of("natural", "").isCustom)
        done()
    }

    private fun octant(d: Vec3) = (if (d.x > 0) 1 else 0) + (if (d.y > 0) 2 else 0) + (if (d.z > 0) 4 else 0)

    @Test fun n4Bokeh() {
        val discs = NeuronBokeh.discs()
        check("N4 the same every time", discs == NeuronBokeh.discs() && discs.size == NeuronBokeh.COUNT)
        check("N4 on the unit sphere", discs.all { abs(it.direction.length() - 1) < 0.001f })
        check("N4 the palette's colours, on screen's scale", discs.all { onScreen(it.colour) && it.strength > 0 })
        val golds = discs.count { it.colour.x > 0.9f && it.colour.z < 0.45f }
        val blues = discs.count { it.colour.z > 0.9f && it.colour.x < 0.65f }
        check("N4 gold and blue, nothing else", golds > 0 && blues > 0 && golds + blues == discs.size)
        val big = discs.filter { it.size > 0.08f }
        val small = discs.filter { it.size < 0.03f }
        val meanBig = big.map { it.strength }.sum() / max(big.size, 1)
        val meanSmall = small.map { it.strength }.sum() / max(small.size, 1)
        check("N4 most small, the big ones fainter", small.size > big.size && big.isNotEmpty() && meanBig < meanSmall)
        check("N4 spread all round", discs.map { octant(it.direction) }.toSet().size == 8)
        done()
    }

    @Test fun n5ShadersReadTheLook() {
        val soma = NeuronShaders.soma
        val halo = NeuronShaders.halo
        check("N5 the soma and halo read the state", soma.contains("float rpState;") && halo.contains("float rpState;"))
        check("N5 the halo draws every state's process", (1..5).all { halo.contains("rpState > ${it - 0.5}") })
        check("N5 the soma's interior is the palette's",
            soma.contains("vec3(0.2, 0.07, 0.46)") && soma.contains("vec3(0.52, 0.24, 0.98)")
                && NeuronPalette.interior == Vec3(0.2f, 0.07f, 0.46f) && NeuronPalette.heart == Vec3(0.52f, 0.24f, 0.98f))
        check("N5 an opened nucleus is drawn as big as the layout keeps it for the parts",
            soma.contains("mix(0.36 + 0.25 * rpNucleus, ${GraphNeurons.OPEN_NUCLEUS}, rpOpen)"))
        check("N5 the pacemaker's beat is the pulsar's period in both",
            soma.contains("rp_t / 1.5") && halo.contains("rp_t / 1.5") && SpaceOptics.PULSAR_PERIOD == 1.5f)
        done()
    }

    @Test fun n6FarCells() {
        val far = NeuronBokeh.farCells()
        check("N6 the same every time", far == NeuronBokeh.farCells() && far.size == NeuronBokeh.FAR_COUNT)
        check("N6 on the unit sphere", far.all { abs(it.direction.length() - 1) < 0.001f })
        check("N6 on screen's scale, never full strength",
            far.all { onScreen(it.colour) && it.strength > 0 && it.strength < 1 && it.size > 0.1f && it.size < 0.3f })
        check("N6 blue, the strongest channel", far.all { it.colour.z >= it.colour.x && it.colour.z >= it.colour.y })
        val bigFar = far.filter { it.size > 0.24f }
        val smallFar = far.filter { it.size < 0.18f }
        val meanBig = bigFar.map { it.strength }.sum() / max(bigFar.size, 1)
        val meanSmall = smallFar.map { it.strength }.sum() / max(smallFar.size, 1)
        check("N6 the big ones fainter", bigFar.isNotEmpty() && smallFar.isNotEmpty() && meanBig < meanSmall)
        check("N6 spread over the sky", far.map { octant(it.direction) }.toSet().size >= 6)
        check("N6 each turned its own way", far.map { (it.turn * 100).toInt() }.toSet().size >= far.size - 1
            && far.all { it.turn >= 0 && it.turn < 2 * Math.PI.toFloat() })
        done()
    }

    // ---- the GLSL's shape ----

    /** Every program NeuronGl builds: its name, vertex source and fragment source. */
    private val programs: List<Triple<String, String, String>> get() = listOf(
        Triple("soma", NeuronShaders.somaVertex, NeuronShaders.soma),
        Triple("membrane", NeuronShaders.membraneVertex, NeuronShaders.membrane),
        Triple("arbor", NeuronShaders.arborVertex, NeuronShaders.arbor),
        Triple("halo", NeuronShaders.BILLBOARD_VS, NeuronShaders.halo),
        Triple("arrival", NeuronShaders.BILLBOARD_VS, NeuronShaders.arrival),
        Triple("bridge", NeuronShaders.STRIP_VS, NeuronShaders.bridge),
        Triple("dome", NeuronShaders.SKY_VS, NeuronShaders.DOME_FS),
        Triple("bokeh", NeuronShaders.SKY_VS, NeuronShaders.BOKEH_FS),
        Triple("far", NeuronShaders.SKY_VS, NeuronShaders.FAR_FS),
    )

    private val sources: List<Pair<String, String>> get() = programs.flatMap { (n, v, f) -> listOf("$n vertex" to v, "$n fragment" to f) }

    @Test fun glslHeadersAndMain() {
        for ((name, s) in sources) {
            check("S1 $name: GLSL ES 3.0 first", s.startsWith("#version 300 es\n"))
            check("S1 $name: highp floats", s.contains("precision highp float;"))
            check("S1 $name: one main", Regex("""void main\(\)""").findAll(s).count() == 1)
            if (name.endsWith("fragment")) check("S1 $name: writes its colour", s.contains("out vec4 fragColor;") && s.contains("fragColor ="))
            else check("S1 $name: places its vertex", s.contains("gl_Position ="))
        }
        done()
    }

    @Test fun glslNoMetalOrSceneKitLeft() {
        val left = listOf("float2", "float3", "float4", "atan2(", "_surface", "_geometry", "scn_node", "#pragma", "\\(", "&*", "&+")
        for ((name, s) in sources) for (word in left) check("S2 $name: no $word", !s.contains(word))
        for ((name, s) in sources) check("S2 $name: no Kotlin template left", !s.contains("$"))
        done()
    }

    @Test fun glslBalanced() {
        for ((name, s) in sources) {
            for ((open, close) in listOf('(' to ')', '{' to '}', '[' to ']')) {
                var depth = 0; var ok = true
                for (ch in s) { if (ch == open) depth++; if (ch == close) depth--; if (depth < 0) ok = false }
                check("S3 $name: $open$close balanced", ok && depth == 0, "depth $depth")
            }
            val statements = s.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            val loose = statements.filter { line ->
                !(line.endsWith(";") || line.endsWith("{") || line.endsWith("}") || line.startsWith("}"))
            }
            check("S3 $name: every statement ends", loose.isEmpty(), loose.take(3).toString())
        }
        done()
    }

    private val types = "float|int|uint|bool|vec2|vec3|vec4|mat3|mat4"
    private val functions = setOf("rpHash", "rpNoise", "rpDepthFade", "rpDissolve", "rpFadedBody", "rpFadedGlow")

    @Test fun glslUniformsDeclared() {
        for ((name, s) in sources) {
            val used = Regex("""\b(rp[A-Z]\w*|u[A-Z]\w*)\b""").findAll(s).map { it.value }.toSet() - functions
            val declared = Regex("""uniform\s+(?:$types)\s+(\w+)""").findAll(s).map { it.groupValues[1] }.toSet()
            val missing = used - declared
            check("S4 $name: every uniform declared", missing.isEmpty(), missing.toString())
        }
        done()
    }

    @Test fun glslLocalsDeclaredBeforeUse() {
        for ((name, s) in sources) {
            val bad = ArrayList<String>()
            for (m in Regex("""\brp_\w+\b""").findAll(s)) {
                val word = m.value
                val decl = Regex("""\b(?:$types)\s+$word\b""").find(s)
                if (decl == null || decl.range.first > m.range.first) { bad.add(word); }
            }
            check("S5 $name: every local declared before it is used", bad.isEmpty(), bad.distinct().take(5).toString())
        }
        done()
    }

    @Test fun glslNoLocalTwiceInOneScope() {
        for ((name, s) in sources) {
            val stack = ArrayList<HashSet<String>>().apply { add(HashSet()) }
            val twice = ArrayList<String>()
            val decl = Regex("""\b(?:$types)\s+(rp_\w+)\s*(=|;|,|\))""")
            for (line in s.lines()) {
                fun braces(skipFirstOpen: Boolean) {
                    var skip = skipFirstOpen
                    for (ch in line) {
                        if (ch == '{') { if (skip) skip = false else stack.add(HashSet()) }
                        if (ch == '}' && stack.size > 1) stack.removeAt(stack.size - 1)
                    }
                }
                // a function's parameters belong to its body, which the line opens
                if (Regex("""^(?:$types)\s+\w+\(.*\)\s*\{""").containsMatchIn(line.trim())) {
                    stack.add(HashSet())
                    val params = Regex("""\(([^)]*)\)""").find(line)?.groupValues?.get(1) ?: ""
                    stack.last().addAll(Regex("""(rp_\w+)""").findAll(params).map { it.value })
                    braces(skipFirstOpen = true)
                    continue
                }
                // a for loop's counter belongs to the loop's own scope, which the line opens
                val loop = line.trim().startsWith("for (")
                if (loop) stack.add(HashSet())
                for (m in decl.findAll(line)) if (!stack.last().add(m.groupValues[1])) twice.add(m.groupValues[1])
                braces(skipFirstOpen = loop)
            }
            check("S6 $name: no local declared twice in one scope", twice.isEmpty(), twice.toString())
        }
        done()
    }

    @Test fun glslVaryingsMatch() {
        for ((name, vs, fs) in programs) {
            val outs = Regex("""^\s*(?:flat\s+)?out\s+(\w+)\s+(\w+);""", RegexOption.MULTILINE).findAll(vs).map { it.groupValues[1] + " " + it.groupValues[2] }.toSet()
            val ins = Regex("""^\s*(?:flat\s+)?in\s+(\w+)\s+(\w+);""", RegexOption.MULTILINE).findAll(fs).map { it.groupValues[1] + " " + it.groupValues[2] }.toSet()
            check("S7 $name: the fragment's inputs are the vertex shader's outputs", outs.containsAll(ins), "${ins - outs}")
        }
        done()
    }

    @Test fun glslFloatLiterals() {
        // GLSL ES converts no int to a float: outside int contexts every number has a point
        val intContext = Regex("""for \(int|\bint\s|\buint\s|location = \d|#version|\[\d\]|== \d|\? \d : \d|\d+u\b|>> \d|\w\[\d\]""")
        for ((name, s) in sources) {
            val bad = ArrayList<String>()
            for (line in s.lines()) {
                if (intContext.containsMatchIn(line)) continue
                val stripped = line.replace(Regex("""\b[A-Za-z_]\w*"""), "x")
                if (Regex("""(?<![\w.])\d+(?![\d.\w])""").containsMatchIn(stripped)) bad.add(line.trim())
            }
            check("S8 $name: no int where a float goes", bad.isEmpty(), bad.take(3).toString())
        }
        done()
    }

    // ---- the depth band and the mouths ----

    @Test fun depthBand() {
        val d = 10f; val r = 1f
        val high = NeuronDepth.band(d, r, GraphicsTier.HIGH)
        val smooth = NeuronDepth.band(d, r, GraphicsTier.SMOOTH)
        check("D1 High: the fade starts a fifth of the reach past the focus, ends at 1.05", abs(high.start - 12f) < 1e-4f && abs(high.end - 20.5f) < 1e-4f)
        check("D1 Smooth: ends sooner, at 0.9", abs(smooth.start - 12f) < 1e-4f && abs(smooth.end - 19f) < 1e-4f)
        check("D1 a straight ramp to the sky", high.exponent == 1f && high.cap == 1f)
        val contrast = NeuronDepth.band(d, r, GraphicsTier.HIGH, contrast = true)
        check("D1 Increase Contrast: later and short of the sky", contrast.start > high.start && contrast.cap == SpaceDepth.CONTRAST_CAP)
        check("D1 no focus: no fade", NeuronDepth.band(0f, r, GraphicsTier.HIGH) == SpaceDepthBand.OFF)
        done()
    }

    @Test fun mouthsKeepTheFourMostOpen() {
        val mouths = NeuronMouths()
        val eye = Vec3(0f, 0f, 10f)
        // six links leaving cell 0 sideways (seen across), one straight at the camera (seen end on)
        for (k in 0 until 6) {
            val a = 2 * Math.PI.toFloat() * k / 6
            mouths.add(NeuronMouths.Mouth(Vec3(kotlin.math.cos(a), kotlin.math.sin(a), 0f) * (0.5f + 0.1f * k), 0.4f), 0, Vec3.ZERO, eye)
        }
        mouths.add(NeuronMouths.Mouth(Vec3(0f, 0f, 1f), 0.4f), 0, Vec3.ZERO, eye)
        val kept = mouths.mouths(0)
        check("M1 four at most", kept.size == NeuronMouths.COUNT, "${kept.size}")
        check("M1 the most open first", kept.zipWithNext().all { (p, q) -> p.dir.length() >= q.dir.length() })
        check("M1 one seen end on stays shut", kept.none { abs(it.dir.z) > 0.5f })
        val (first, second) = NeuronMouths.open(0f)
        check("M1 a link not grown opens neither", first == 0f && second == 0f)
        check("M1 grown, both open", NeuronMouths.open(1f) == (1f to 1f))
        val w = NeuronMouths().width(1f, 0.1f)
        check("M1 a mouth's half width: h over r, plus the blend", abs(w - (0.1f + NeuronMembrane.BLEND)) < 1e-6f)
        done()
    }
}
