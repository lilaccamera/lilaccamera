package com.hinnka.mycamera.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LilacLensShadingTest {
    private fun gain(channel: Int, column: Int, row: Int): Float =
        LilacLensShading.GAINS[(row * LilacLensShading.COLUMNS + column) * 4 + channel]

    @Test
    fun mapHasOneGainPerChannelAndNode() {
        assertEquals(
            LilacLensShading.COLUMNS * LilacLensShading.ROWS * 4,
            LilacLensShading.GAINS.size,
        )
    }

    @Test
    fun everyGainBrightensAndTheCentreIsLeftAlone() {
        assertTrue(LilacLensShading.GAINS.all { it.isFinite() && it >= 1f })
        for (channel in 0..3) {
            assertEquals(1f, gain(channel, 8, 6), 0.03f)
        }
    }

    @Test
    fun gainsGrowTowardsTheCorners() {
        for (channel in 0..3) {
            val corners = listOf(
                gain(channel, 0, 0), gain(channel, 16, 0), gain(channel, 0, 12), gain(channel, 16, 12),
            )
            // The measured falloff leaves the corners at about 0.22 of the centre.
            assertTrue(corners.all { it in 4f..5.2f })
            assertTrue(gain(channel, 0, 6) < corners.min())
            assertTrue(gain(channel, 8, 0) < corners.min())
        }
    }

    @Test
    fun dngGridPutsNodesOnTheEdgesOfTheFrame() {
        assertArrayEquals(
            floatArrayOf(0f, 0f, 1f / 16f, 1f / 12f, 0f, 0f, 5056f, 3792f),
            LilacLensShading.dngGrid(5056, 3792),
            0f,
        )
    }
}
