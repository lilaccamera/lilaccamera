package com.hinnka.mycamera.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class ShadowCastFixTest {
    private val w = 400
    private val h = 300

    private fun encode(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (s * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    /** A scene from per-pixel linear grey, a channel multiplier (the cast) and an added floor. */
    private fun scene(grey: (x: Int, y: Int) -> Float, cast: FloatArray, floor: FloatArray) = IntArray(w * h) { i ->
        val v = grey(i % w, i / w)
        (0xFF shl 24) or (encode(v * cast[0] + floor[0]) shl 16) or (encode(v * cast[1] + floor[1]) shl 8) or
            encode(v * cast[2] + floor[2])
    }

    private fun run(pixels: IntArray): ShadowCastFix.Params {
        val p = checkNotNull(ShadowCastFix.estimate(w, h) { y, rows, out -> pixels.copyInto(out, 0, y * w, (y + rows) * w) })
        ShadowCastFix.correct(pixels, pixels.size, p)
        return p
    }

    private fun channel(px: Int, shift: Int) = (px shr shift) and 0xFF

    // Dim room: a third of the frame sits at the black floor, the rest is a smooth wall gradient.
    private val dimRoom = { x: Int, y: Int -> if (y < h / 3) 0f else 0.01f + 0.08f * x / w }

    @Test
    fun dimShotLosesFloorAndMagenta() {
        val pixels = scene(dimRoom, floatArrayOf(1.3f, 1f, 1.3f), floatArrayOf(0.004f, 0.002f, 0.003f))
        val p = run(pixels)
        assertEquals(1f, p.floorWeight, 1e-3f)
        assertTrue("tint ${p.tint}", p.tint > 0.2f)
        val black = pixels[10 * w + 200]
        for (shift in intArrayOf(16, 8, 0)) assertTrue("black ${channel(black, shift)}", channel(black, shift) <= 1)
        val wall = pixels[(h / 3 + 20) * w + 100] // dark wall, full correction strength
        val g = channel(wall, 8)
        assertTrue("wall R ${channel(wall, 16)} G $g", abs(channel(wall, 16) - g) <= 2)
        assertTrue("wall B ${channel(wall, 0)} G $g", abs(channel(wall, 0) - g) <= 2)
    }

    @Test
    fun litShotKeepsDarkColours() {
        // Lit room: dark tones spread out and are genuinely red, not a floor.
        val pixels = scene({ x, y -> 0.004f + 0.5f * (x + y * w).toFloat() / (w * h) }, floatArrayOf(2f, 1f, 0.6f), FloatArray(3))
        val before = pixels.copyOf()
        val p = run(pixels)
        assertEquals(0f, p.floorWeight, 1e-3f)
        assertTrue("tint ${p.tint}", p.tint < 0.02f) // a few quantised pixels pass as grey; at most a trace
        // Only the floor's colour share goes (capped at 0.004 linear): the darkest reds lose a few levels.
        val i = 5 * w + 10
        val lost = channel(before[i], 16) - channel(pixels[i], 16)
        assertTrue("red lost $lost", lost in 0..5)
        assertTrue(abs(channel(pixels[i], 8) - channel(before[i], 8)) <= 1)
    }

    @Test
    fun blueShadowsAreNeutralised() {
        // Deep shadows (30 % of the frame) whose blue sits above green on average but spreads widely, as in
        // the front camera's dim shots, so the floor step alone cannot remove it; above them a neutral wall.
        val lin = Array(w * h) { i ->
            val x = i % w
            val y = i / w
            if (y < h * 3 / 10) {
                val g = 0.002f + 0.004f * ((x * 7 + y * 13) % 100) / 100f
                val spread = 0.006f * (((x * 31 + y * 17) % 100) / 50f - 1f)
                floatArrayOf(g, g, max(g + 0.004f + spread, 0f))
            } else {
                val v = 0.02f + 0.08f * x / w
                floatArrayOf(v, v, v)
            }
        }
        val pixels = IntArray(w * h) { i ->
            val c = lin[i]
            (0xFF shl 24) or (encode(c[0]) shl 16) or (encode(c[1]) shl 8) or encode(c[2])
        }
        val p = run(pixels)
        assertEquals(1f, p.floorWeight, 1e-3f)
        assertTrue("shadow ${p.shadow.toList()}", p.shadow[2] > 0.003f)
        val diffs = (0 until w * h * 3 / 10).map { channel(pixels[it], 0) - channel(pixels[it], 8) }.sorted()
        assertTrue("shadow B-G median ${diffs[diffs.size / 2]}", abs(diffs[diffs.size / 2]) <= 2)
        val wall = pixels[(h * 3 / 4) * w + w / 2]
        assertTrue("wall ${channel(wall, 16)},${channel(wall, 8)},${channel(wall, 0)}",
            abs(channel(wall, 0) - channel(wall, 8)) <= 2 && abs(channel(wall, 16) - channel(wall, 8)) <= 2)
    }

    @Test
    fun dimShotIsLiftedButNotOneWithoutLight() {
        // A fifth of the frame at the floor (enough to read as dim), so the darkest quarter reaches the wall.
        val room = { x: Int, y: Int -> if (y < h * 22 / 100) 0f else 0.01f + 0.08f * x / w }
        val floor = floatArrayOf(0.002f, 0.001f, 0.002f)
        val dim = run(scene(room, floatArrayOf(1f, 1f, 1f), floor))
        assertEquals(1f, dim.floorWeight, 1e-3f)
        assertEquals(0.5f, dim.lift, 1e-3f)
        val noLight = run(scene({ x, y -> 0.1f * room(x, y) }, floatArrayOf(1f, 1f, 1f), floor))
        assertEquals(1f, noLight.floorWeight, 1e-3f)
        assertEquals(0f, noLight.lift, 1e-6f)
    }

    @Test
    fun brightPhotoLosesOnlyTheMagentaShareOfItsShadows() {
        // Spread-out tones (not a floor); the darker third has R and B raised, R more than B.
        val pixels = IntArray(w * h) { i ->
            val v = 0.004f + 0.5f * i.toFloat() / (w * h)
            val dark = v < 0.17f
            (0xFF shl 24) or (encode(v + if (dark) 0.02f else 0f) shl 16) or (encode(v) shl 8) or
                encode(v + if (dark) 0.012f else 0f)
        }
        val p = run(pixels)
        assertEquals(0f, p.floorWeight, 1e-3f)
        assertEquals(0f, p.lift, 1e-6f)
        assertTrue("shadow ${p.shadow.toList()}", p.shadow[0] > 0.002f)
        assertEquals(p.shadow[0], p.shadow[2], 1e-6f) // only the share both have; R keeps its extra red
    }

    @Test
    fun greenCastIsNeverMadeMagenta() {
        val pixels = scene(dimRoom, floatArrayOf(0.8f, 1f, 0.8f), FloatArray(3))
        val p = run(pixels)
        assertEquals(0f, p.tint, 1e-6f)
    }
}
