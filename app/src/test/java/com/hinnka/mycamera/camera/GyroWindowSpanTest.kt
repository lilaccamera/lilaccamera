package com.hinnka.mycamera.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class GyroWindowSpanTest {
    @Test
    fun reportedExposureWins() {
        assertEquals(10_000_000L, span(result = 10_000_000L, manual = 20_000_000L, frameDuration = 33_000_000L))
    }

    @Test
    fun manualRequestExposureStandsInForMissingResult() {
        assertEquals(20_000_000L, span(result = null, manual = 20_000_000L, frameDuration = 33_000_000L))
    }

    @Test
    fun frameDurationStandsInWithoutAnyExposure() {
        assertEquals(33_000_000L, span(result = 0L, manual = null, frameDuration = 33_000_000L))
    }

    @Test
    fun nearestBurstNeighbourGapStandsInWithoutExposureOrDuration() {
        val start = 1_000_000_000L
        assertEquals(
            33_000_000L,
            GyroWindowSpan.exposureNs(null, null, null, start, listOf(start - 33_000_000L, start + 50_000_000L)),
        )
        assertEquals(50_000_000L, GyroWindowSpan.exposureNs(null, null, null, start, listOf(null, start + 50_000_000L)))
    }

    @Test
    fun noInformationLeavesTheWindowUnavailable() {
        assertEquals(0L, GyroWindowSpan.exposureNs(null, null, null, 1L, listOf(null, null)))
        assertEquals(0L, GyroWindowSpan.exposureNs(null, null, null, 1L, listOf(1L)))
    }

    private fun span(result: Long?, manual: Long?, frameDuration: Long?) =
        GyroWindowSpan.exposureNs(result, manual, frameDuration, 1_000_000_000L, listOf(967_000_000L))
}
