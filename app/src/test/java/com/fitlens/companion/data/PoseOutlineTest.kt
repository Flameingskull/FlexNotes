package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera's body outline (#230): tracing a segmentation mask into closed loops, on synthetic masks. */
class PoseOutlineTest {

    private fun mask(w: Int, h: Int, person: (Int, Int) -> Boolean) = BooleanArray(w * h) { person(it % w, it / w) }

    private fun xs(loop: FloatArray) = loop.filterIndexed { i, _ -> i % 2 == 0 }
    private fun ys(loop: FloatArray) = loop.filterIndexed { i, _ -> i % 2 == 1 }

    @Test
    fun aBlockGetsOneClosedLoopOnItsEdge() {
        // A 20 × 40 "body" in a 40 × 60 photo, plus a stray speck in the corner that must not get a line.
        val m = mask(40, 60) { x, y -> (x in 10..29 && y in 10..49) || (x == 2 && y == 2) }
        val o = BodyOutline.trace(m, 40, 60)
        assertTrue(o.found)
        assertEquals(1, o.loops.size)
        val loop = o.loops.single()
        assertEquals(0.25f, xs(loop).min(), 0.02f)
        assertEquals(0.75f, xs(loop).max(), 0.02f)
        assertEquals(10f / 60f, ys(loop).min(), 0.02f)
        assertEquals(50f / 60f, ys(loop).max(), 0.02f)
        // Every point lies on the block's edge (within the smoothing), none near the speck.
        loop.toList().chunked(2).forEach { (x, y) ->
            assertTrue(x in 0.24f..0.76f && y in 0.15f..0.85f)
        }
    }

    @Test
    fun aHoleInsideTheBodyKeepsItsOwnLoop() {
        // The gap between an arm and the body: a ring.
        val m = mask(50, 50) { x, y -> x in 5..44 && y in 5..44 && !(x in 18..31 && y in 18..31) }
        val o = BodyOutline.trace(m, 50, 50)
        assertEquals(2, o.loops.size)
    }

    @Test
    fun noPersonOrATinyOneIsNotFound() {
        assertFalse(BodyOutline.trace(BooleanArray(30 * 30), 30, 30).found)
        // 2 × 2 of 100 × 100 is far below the 2% minimum.
        assertFalse(BodyOutline.trace(mask(100, 100) { x, y -> x in 50..51 && y in 50..51 }, 100, 100).found)
    }

    @Test
    fun theLargestRegionWins() {
        val m = mask(60, 40) { x, y -> (x in 2..9 && y in 2..9) || (x in 20..55 && y in 5..35) }
        val o = BodyOutline.trace(m, 60, 40)
        assertEquals(1, o.loops.size)
        assertTrue(xs(o.loops.single()).min() > 0.3f)
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val o = BodyOutline.trace(mask(40, 40) { x, y -> x in 8..31 && y in 8..31 }, 40, 40)
        val back = BodyOutline.decode(o.encode())
        assertNotNull(back)
        assertEquals(40, back!!.width)
        assertEquals(o.loops.size, back.loops.size)
        assertEquals(o.loops[0].size, back.loops[0].size)
        assertEquals(o.loops[0][0], back.loops[0][0], 0.001f)
        // An outline with no person found still round-trips, and junk is refused.
        assertFalse(BodyOutline.decode(BodyOutline(10, 10, emptyList()).encode())!!.found)
        assertEquals(null, BodyOutline.decode("not an outline"))
    }
}
