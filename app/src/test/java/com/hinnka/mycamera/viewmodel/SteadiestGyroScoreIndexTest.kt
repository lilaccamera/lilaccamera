package com.hinnka.mycamera.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SteadiestGyroScoreIndexTest {
    @Test
    fun firstFrameStaysUnlessAnotherIsClearlySteadier() {
        assertEquals(0, steadiestGyroScoreIndex(listOf(1.0f, 0.6f, 0.8f), 0.5f))
        assertEquals(1, steadiestGyroScoreIndex(listOf(1.0f, 0.4f, 0.8f), 0.5f))
    }

    @Test
    fun uncoveredFirstFrameIsSkipped() {
        assertEquals(1, steadiestGyroScoreIndex(listOf(null, 1.01e-4f, 3.16e-4f, 3.68e-4f), 0.5f))
    }

    @Test
    fun uncoveredLaterFramesDoNotBlockTheChoice() {
        assertEquals(2, steadiestGyroScoreIndex(listOf(1.0f, null, 0.3f, null), 0.5f))
    }

    @Test
    fun noScoresKeepsTheFirstFrame() {
        assertEquals(0, steadiestGyroScoreIndex(listOf(null, null), 0.5f))
        assertEquals(0, steadiestGyroScoreIndex(emptyList(), 0.5f))
    }
}
