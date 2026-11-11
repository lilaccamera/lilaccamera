package com.hinnka.mycamera.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SteadiestGyroScoreIndexTest {
    @Test
    fun firstFrameStaysUnlessAnotherIsClearlySteadier() {
        assertEquals(0, steadiestGyroScoreIndex(listOf(1.0f, 0.6f, 0.8f), 0.5f, 4))
        assertEquals(1, steadiestGyroScoreIndex(listOf(1.0f, 0.4f, 0.8f), 0.5f, 4))
    }

    @Test
    fun uncoveredFirstFrameIsSkipped() {
        assertEquals(1, steadiestGyroScoreIndex(listOf(null, 1.01e-4f, 3.16e-4f, 3.68e-4f), 0.5f, 4))
    }

    @Test
    fun uncoveredFirstFrameGivesItsPlaceToTheFifth() {
        assertEquals(4, steadiestGyroScoreIndex(listOf(null, 3f, 2f, 4f, 1f), 0.5f, 4))
        assertEquals(2, steadiestGyroScoreIndex(listOf(1f, 3f, 0.4f, 4f, 0.1f), 0.5f, 4))
    }

    @Test
    fun uncoveredLaterFramesDoNotBlockTheChoice() {
        assertEquals(2, steadiestGyroScoreIndex(listOf(1.0f, null, 0.3f, null), 0.5f, 4))
    }

    @Test
    fun noScoresKeepsTheFirstFrame() {
        assertEquals(0, steadiestGyroScoreIndex(listOf(null, null), 0.5f, 4))
        assertEquals(0, steadiestGyroScoreIndex(emptyList(), 0.5f, 4))
    }
}
