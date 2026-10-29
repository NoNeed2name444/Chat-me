package com.cramdown.sorta.map

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// iOS NeuronMembraneTests.swift (M1-M7), its checks and bounds as they are: a cell's soma and
// dendrites one closed surface - no holes or loose edges, wound alike and outwards, one piece,
// on the field's zero set, the soma round where no dendrite leaves it, every dendrite covered
// to its tip and apart from its neighbours, the shader's per-vertex numbers in range and in
// place, made in under a second and the same every time. The same three crowns ("six",
// "seven", "six b") from the same LCG.
class NeuronMembraneTest {
    private class Case(val name: String, val branches: List<NeuronBranch>) {
        val field = NeuronMembraneField(branches)
        val took: Double
        val membrane: NeuronMembrane
        init {
            val began = System.nanoTime()
            membrane = NeuronMembrane.make(branches)
            took = (System.nanoTime() - began) / 1e9
        }
        val points: List<Vec3> = List(membrane.vertexCount) { Vec3(membrane.points[3 * it], membrane.points[3 * it + 1], membrane.points[3 * it + 2]) }
        val normals: List<Vec3> = List(membrane.normals.size / 3) { Vec3(membrane.normals[3 * it], membrane.normals[3 * it + 1], membrane.normals[3 * it + 2]) }
        val tris = membrane.indices.size / 3
    }

    private val failures = ArrayList<String>()
    private fun check(label: String, ok: Boolean, detail: String = "") { if (!ok) failures.add("$label | $detail") }
    private fun done() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    @Test fun m1Closed() {
        for (c in cases) {
            val tris = c.tris
            val undirected = HashMap<Long, Int>()
            val directed = HashMap<Long, Int>()
            fun key(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFF_FFFFL)
            for (t in 0 until tris) {
                val v = IntArray(3) { c.membrane.indices[t * 3 + it] }
                for ((a, b) in listOf(v[0] to v[1], v[1] to v[2], v[2] to v[0])) {
                    undirected.merge(key(min(a, b), maxOf(a, b)), 1, Int::plus)
                    directed.merge(key(a, b), 1, Int::plus)
                }
            }
            val loose = undirected.values.count { it % 2 == 1 }
            val pairs = undirected.values.count { it == 2 }
            check("M1a ${c.name}: a real mesh", tris > 2000 && c.points.size > 1000, "$tris triangles")
            check("M1b ${c.name}: no loose edge (closed, no holes)", loose == 0, "$loose of ${undirected.size}")
            check("M1c ${c.name}: almost every edge between exactly two triangles", pairs.toFloat() >= 0.99f * undirected.size, "$pairs of ${undirected.size}")
            val unbalanced = directed.count { (k, n) -> (directed[key((k and 0xFFFF_FFFFL).toInt(), (k ushr 32).toInt())] ?: 0) != n }
            check("M1d ${c.name}: every edge crossed once each way (wound alike)", unbalanced == 0, "$unbalanced")
            check("M1e ${c.name}: no triangle uses a vertex twice", (0 until tris).all { t ->
                val a = c.membrane.indices[t * 3]; val b = c.membrane.indices[t * 3 + 1]; val d = c.membrane.indices[t * 3 + 2]
                a != b && b != d && a != d
            })
        }
        done()
    }

    @Test fun m2OnePiece() {
        for (c in cases) {
            val parent = IntArray(c.points.size) { it }
            fun root(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
            for (t in 0 until c.tris) {
                val a = root(c.membrane.indices[t * 3]); val b = root(c.membrane.indices[t * 3 + 1])
                parent[b] = a
                parent[root(c.membrane.indices[t * 3 + 2])] = root(a)
            }
            val pieces = c.points.indices.map { root(it) }.toSet().size
            check("M2a ${c.name}: one piece (soma and dendrites one surface)", pieces == 1, "$pieces pieces")
        }
        done()
    }

    @Test fun m3OnTheSurface() {
        val h = NeuronMembrane.STEP
        for (c in cases) {
            val off = c.points.map { abs(c.field.distance(it)) }
            val worst = off.maxOrNull() ?: 0f
            val close = off.count { it < 0.1f * h }
            check("M3a ${c.name}: every vertex within a quarter step of the surface", worst < 0.25f * h, "$worst")
            check("M3b ${c.name}: 99% within a tenth of a step", close.toFloat() >= 0.99f * c.points.size, "$close of ${c.points.size}")
        }
        done()
    }

    @Test fun m4NormalsOut() {
        for (c in cases) {
            val units = c.normals.all { abs(it.length() - 1) < 0.001f }
            check("M4a ${c.name}: one unit normal per vertex", c.normals.size == c.points.size && units)
            var agree = 0
            var volume = 0f
            for (t in 0 until c.tris) {
                val i0 = c.membrane.indices[t * 3]; val i1 = c.membrane.indices[t * 3 + 1]; val i2 = c.membrane.indices[t * 3 + 2]
                val p = c.points
                val face = (p[i1] - p[i0]).cross(p[i2] - p[i0])
                val mean = c.normals[i0] + c.normals[i1] + c.normals[i2]
                if (face.dot(mean) > 0) agree++
                volume += p[i0].dot(p[i1].cross(p[i2])) / 6
            }
            check("M4b ${c.name}: triangles face the way their normals point", agree.toFloat() >= 0.995f * c.tris, "$agree of ${c.tris}")
            val sphere = 4f / 3f * PI.toFloat()
            check("M4c ${c.name}: wound outwards (positive volume, more than the soma's)", volume > sphere && volume < 4 * sphere, "$volume")
            val outward = c.points.indices.count { c.normals[it].dot(c.points[it]) > 0 }
            check("M4d ${c.name}: normals point away from the centre almost everywhere", outward > 0.9f * c.points.size, "$outward of ${c.points.size}")
        }
        done()
    }

    @Test fun m5TheShape() {
        for (c in cases) {
            check("M5a ${c.name}: the centre is inside", c.field.distance(Vec3(0f, 0f, 0f)) < -0.9f)
            val top = c.points.maxByOrNull { it.z } ?: Vec3.ZERO
            val bottom = c.points.minByOrNull { it.z } ?: Vec3.ZERO
            check("M5b ${c.name}: the soma round where no dendrite leaves it (top and bottom at its radius)",
                abs(top.length() - 1) < 0.05f && abs(bottom.length() - 1) < 0.05f, "${top.length()} ${bottom.length()}")
            for ((n, branch) in c.branches.withIndex()) {
                val reach = c.points.maxOf { it.dot(branch.end) / branch.end.length() }
                val tip = branch.end.length()
                check("M5c ${c.name}: dendrite $n covered to its rounded tip",
                    c.field.distance(branch.end) < 0 && abs(reach - (tip + branch.r1)) < 0.08f, "$reach for $tip")
            }
            val webbed = ArrayList<String>()
            var compared = 0
            for (n in c.branches.indices) {
                val a = c.branches[n].point(0.8f)
                val b = c.branches[(n + 1) % c.branches.size].point(0.8f)
                val mid = (a + b) * 0.5f
                if (!((a - b).length() > 0.7f && mid.length() > 1.25f)) continue
                compared++
                if (c.field.distance(mid) <= 0) webbed.add("$n: ${c.field.distance(mid)} at ${mid.length()}")
            }
            check("M5d ${c.name}: neighbouring dendrites stay apart out along them", compared >= 3 && webbed.isEmpty(), "$compared compared; $webbed")
        }
        done()
    }

    @Test fun m6WhatTheShaderReads() {
        for (c in cases) {
            val coords = c.membrane.coords
            val inRange = coords.all { it in 0f..1f }
            check("M6a ${c.name}: one (along, soma) pair per vertex, both 0 to 1", coords.size == c.points.size * 2 && inRange)
            val top = c.points.maxByOrNull { it.z } ?: Vec3.ZERO
            val topAt = c.points.indexOf(top).coerceAtLeast(0)
            check("M6b ${c.name}: the soma's top is all soma, at the start of nothing",
                coords[topAt * 2 + 1] > 0.95f && coords[topAt * 2] < 0.05f, "${coords[topAt * 2]}, ${coords[topAt * 2 + 1]}")
            var tipsOK = true
            for (branch in c.branches) {
                val near = c.points.indices.minByOrNull { (c.points[it] - branch.end).length() } ?: 0
                if (!(coords[near * 2 + 1] < 0.05f && coords[near * 2] > 0.9f)) tipsOK = false
            }
            check("M6c ${c.name}: at every tip, all dendrite and all the way along", tipsOK)
        }
        done()
    }

    @Test fun m7Cost() {
        for (c in cases) {
            check("M7a ${c.name}: made in under a second", c.took < 1, "${c.took} s")
            val again = NeuronMembrane.make(c.branches)
            check("M7b ${c.name}: the same every time", again.points.contentEquals(c.membrane.points) && again.indices.contentEquals(c.membrane.indices))
        }
        done()
    }

    companion object {
        /** iOS crown(count, seed): a soma's dendrites from an LCG, as the iOS test makes them. */
        fun crown(count: Int, seed: ULong): List<NeuronBranch> {
            var state = seed
            fun unit(): Float {
                state = state * 6_364_136_223_846_793_005uL + 1_442_695_040_888_963_407uL
                return (state shr 40).toFloat() / (1 shl 24).toFloat()
            }
            fun signed(): Float = unit() * 2 - 1
            val out = ArrayList<NeuronBranch>()
            for (k in 0 until count) {
                val a = 2 * PI.toFloat() * (k + 0.35f * signed()) / count
                val raw = Vec3(cos(a), sin(a), 0.35f * signed())
                val d = raw * (1f / raw.length())
                val reach = min(1.0f + 0.2f * unit(), 1.9f - 0.75f - 0.05f)
                val start = d * 0.75f
                val across = d.cross(Vec3(0f, 0f, 1f))
                val side = across * (1f / across.length())
                val wander = signed() * 0.3f * reach
                val bend = start + d * (reach * 0.5f) + side * wander
                val end = start + d * reach + side * (wander * 0.6f)
                out.add(NeuronBranch(start, bend, end, 0.3f, 0.3f * 0.7f, 0f, 1f, 0.5f))
            }
            return out
        }

        private val cases: List<Case> by lazy {
            listOf(Triple("six", 6, 7uL), Triple("seven", 7, 0x9E37_79B9uL + 7uL), Triple("six b", 6, 41uL))
                .map { (name, count, seed) -> Case(name, crown(count, seed)) }
        }
    }
}
