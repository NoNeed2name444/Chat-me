package com.cramdown.sorta.ui.map

import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.map.FramePlan
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.Mat4
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.map.OrbitCamera
import com.cramdown.sorta.map.SpaceBodies
import com.cramdown.sorta.map.SpacePalette
import com.cramdown.sorta.map.UniverseBeams
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.map.Vec3
import com.cramdown.sorta.ui.ideas.MapLinkLength
import com.cramdown.sorta.ui.ideas.universeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05h repair 7: a moon's link to its own planet (kind 3) is never drawn (GraphMotion.swift:1827), off
 * the device, since the emulator's fixture has no moon. From the sample ideas, which have moons: the
 * links the scene builder hands to drawing (MapScene.universe) and the beams SpaceBodies builds
 * (addBeam) leave out every kind-3 link the plan has, and keep every other.
 */
class UniverseDrawnLinksTest {
    private val plan: UniversePlan = SampleIdeas.book().let { UniversePlan.of(universeInput(it, it.allEdges(), MapLinkLength.STANDARD)) }

    private fun key(a: Int, b: Int) = Octree.pairKey(minOf(a, b), maxOf(a, b))

    @Test fun theSceneHandsOnEveryLinkButAMoonsToItsPlanet() {
        val moons = plan.links.filter { it.kind == 3 }
        assertTrue("the sample ideas' plan has a moon's link to its planet", moons.isNotEmpty())
        val scene = MapScene.universe(plan)
        val handed = (0 until scene.links.size / 2).map { key(scene.links[2 * it], scene.links[2 * it + 1]) }
        assertEquals("the plan's other links, in order", plan.links.filter { it.kind != 3 }.map { key(it.a, it.b) }, handed)
        for (moon in moons) assertTrue("${moon.a}–${moon.b} is not handed on", key(moon.a, moon.b) !in handed)
        assertEquals("nor drawn in the tree", plan.links.count { it.kind != 3 }, scene.tree.edgeCount)
    }

    @Test fun theBeamsLeaveOutAMoonsLinkEvenWhenTheTreeHasIt() {
        // the scene's own tree, and one that has every plan link, the moons' too: addBeam's own guard
        val scene = MapScene.universe(plan)
        val every = MapGraph(FloatArray(plan.bodies.size * 3) { plan.bodies[it / 3].home.let { h -> when (it % 3) { 0 -> h.x; 1 -> h.y; else -> h.z } } },
            ByteArray(plan.bodies.size) { MapScene.universeStyle(plan.bodies[it].role).code.toByte() },
            plan.links.flatMap { listOf(it.a, it.b) }.toIntArray())
        val withMoons = Octree.build(every, 4096, palette = SpacePalette.rgb)
        assertEquals(plan.links.map { key(it.a, it.b) }.distinct().size, withMoons.edgeCount)
        val others = plan.links.filter { it.kind != 3 }.map { key(it.a, it.b) }.distinct().size
        for (tree in listOf(scene.tree, withMoons)) {
            val bodies = beams(tree)
            assertEquals("every body drawn whole", tree.nodeCount, bodies.count)
            assertEquals("a beam for every link but the moons'", others, bodies.beamCount)
            assertEquals(others, bodies.nearBeams + bodies.archedBeams + bodies.farBeams)
            for (j in 0 until bodies.beamCount) {
                assertTrue("beam $j is not a moon's link", bodies.beams[j * SpaceBodies.BEAM_STRIDE + 23] != 3f)
            }
        }
    }

    /** [tree]'s bodies and beams as the renderer sets them up (GlMapRenderer), every body in view and large. */
    private fun beams(tree: Octree): SpaceBodies {
        val chunkOf = IntArray(tree.nodeCount) { -1 }.also { inv -> for (k in 0 until tree.nodeCount) inv[tree.order[k]] = k }
        val bodies = SpaceBodies(tree, nodeRadius = 0.35f, capacity = tree.nodeCount).apply {
            homes = tree.positions.copyOf()
            radii = FloatArray(tree.nodeCount) { k -> UniverseBeams.shaderRadius(plan.bodies[tree.order[k]]) }
            reaches = FloatArray(tree.nodeCount) { k -> UniverseBeams.reach(plan.bodies[tree.order[k]]) }
            linkKinds = UniverseBeams.kinds(plan, chunkOf)
        }
        val open = FramePlan(tree.leafCount)
        for (leaf in 0 until tree.leafCount) { open.leafState[leaf * 4 + 3] = 1f; open.detailLeaves.add(leaf) }
        val centre = Vec3(tree.cx[Octree.ROOT], tree.cy[Octree.ROOT], tree.cz[Octree.ROOT])
        val camera = OrbitCamera(target = centre, pitch = 0.7f).apply { distance = fitDistance(tree.radius[Octree.ROOT] + 2f, aspect = 1f) }
        val vp = Mat4.multiply(camera.projection(1f), camera.view())
        // a focal length so long that every body in view is drawn whole
        bodies.update(open, vp, camera.eye(), camera.right(), camera.up(), focal = 1e6f, maxBodies = tree.nodeCount, time = 0f)
        return bodies
    }
}
