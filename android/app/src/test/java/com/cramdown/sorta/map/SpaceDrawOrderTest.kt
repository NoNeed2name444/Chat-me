package com.cramdown.sorta.map

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

/**
 * The bodies' draw order (SpaceBodies.drawOrder, perf, 15 Nov) is the order the sort it replaced
 * gave, a stable sortedWith(compareBy({ style }, { −depth })), with nothing made per frame.
 */
class SpaceDrawOrderTest {
    private fun both(styles: IntArray, depths: FloatArray) {
        val n = styles.size
        // the entries as SpaceBodies has them: the live by their place, the dying as −(k + 1)
        val entries = IntArray(n) { if (it % 7 == 6) -(it + 1) else it }
        val style = HashMap<Int, Int>(); val depth = HashMap<Int, Float>()
        for (i in 0 until n) { style[entries[i]] = styles[i]; depth[entries[i]] = depths[i] }
        val want = entries.toList().sortedWith(compareBy<Int>({ style[it] }, { -depth[it]!! })).toIntArray()
        val e = entries.copyOf(); val s = styles.copyOf(); val d = depths.copyOf()
        SpaceBodies.drawOrder(e, s, d, n)
        assertArrayEquals(want, e)
        assertArrayEquals(IntArray(n) { style[want[it]]!! }, s)
        assertArrayEquals(FloatArray(n) { depth[want[it]]!! }, d, 0f)
    }

    @Test fun theSameOrderAsTheSort() {
        val r = Random(15)
        repeat(400) {
            val n = r.nextInt(0, 97)
            // few styles and few depths, so ties are many: the stable order is tested too
            both(IntArray(n) { r.nextInt(0, 6) }, FloatArray(n) { r.nextInt(0, 5) * 0.5f + if (r.nextInt(9) == 0) 1e-3f else 0f })
        }
    }

    // the odd depths a frame can hold: zero both ways, the far and the near, keep the sort's own order
    @Test fun oddDepths() {
        both(intArrayOf(1, 1, 1, 1, 0, 0), floatArrayOf(0f, -0f, Float.MAX_VALUE, 1e-3f, -0f, 0f))
        both(intArrayOf(2, 2, 2), floatArrayOf(Float.NaN, 1f, Float.NaN))
    }

    // only the first n are put in order; the rest, a bigger frame's, are left as they are
    @Test fun onlyTheFirstN() {
        val e = intArrayOf(2, 1, 0, 9, 8); val s = intArrayOf(1, 0, 0, 5, 4); val d = floatArrayOf(1f, 1f, 2f, 0f, 0f)
        SpaceBodies.drawOrder(e, s, d, 3)
        assertArrayEquals(intArrayOf(0, 1, 2, 9, 8), e)
    }
}
