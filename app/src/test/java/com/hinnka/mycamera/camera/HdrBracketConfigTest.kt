package com.hinnka.mycamera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class HdrBracketConfigTest {
    @Test
    fun yuvBracketUsesAsymmetricLongAndShortExposureOffsets() {
        assertEquals(2.2f, HdrBracketConfig.YUV_LONG_EV)
        assertEquals(-1.5f, HdrBracketConfig.YUV_SHORT_EV)
        assertEquals(10_000_000L, HdrBracketConfig.YUV_LONG_MAX_SHUTTER_NS)
        assertTrue(2f.pow(HdrBracketConfig.YUV_LONG_EV) > 1f)
        assertTrue(2f.pow(HdrBracketConfig.YUV_SHORT_EV) < 1f)
    }

    @Test
    fun longFrameCapsShutterAtOneHundredthAndUsesIsoForRemainingExposure() {
        val (iso, shutterNs) = HdrBracketConfig.planManualExposure(
            baseIso = 100,
            baseShutterNs = 5_000_000L,
            evOffset = HdrBracketConfig.YUV_LONG_EV,
            isoLower = 50,
            isoUpper = 12_800,
            shutterLowerNs = 100_000L,
            shutterUpperNs = 1_000_000_000L,
        )

        assertTrue(shutterNs <= 10_000_000L)
        assertTrue(iso > 100)
        val expectedExposureProduct = 100.0 * 5_000_000.0 *
            2.0.pow(HdrBracketConfig.YUV_LONG_EV.toDouble())
        val actualExposureProduct = iso.toDouble() * shutterNs.toDouble()
        assertEquals(expectedExposureProduct, actualExposureProduct, expectedExposureProduct * 0.005)
    }

    @Test
    fun shortFrameIsNotAffectedByLongFrameShutterCap() {
        val (_, shutterNs) = HdrBracketConfig.planManualExposure(
            baseIso = 100,
            baseShutterNs = 80_000_000L,
            evOffset = HdrBracketConfig.YUV_SHORT_EV,
            isoLower = 100,
            isoUpper = 12_800,
            shutterLowerNs = 100_000L,
            shutterUpperNs = 1_000_000_000L,
        )

        assertTrue(shutterNs > HdrBracketConfig.YUV_LONG_MAX_SHUTTER_NS)
    }

    private fun histogram(clipped: Float, dark: Float): IntArray = IntArray(256).also {
        it[255] = (1000 * clipped).toInt()
        it[5] = (1000 * dark).toInt()
        it[120] = 1000 - it[255] - it[5]
    }

    @Test
    fun adaptiveEvsFollowClippingAndShadows() {
        assertEquals(HdrBracketConfig.YUV_SHORT_EV to HdrBracketConfig.YUV_LONG_EV, HdrBracketConfig.adaptiveEvs(null))
        assertEquals(-1f to 1f, HdrBracketConfig.adaptiveEvs(histogram(clipped = 0f, dark = 0f)))
        // Monitor in a dark room: highlights clipped, most of the frame near black.
        assertEquals(-2.5f to 3f, HdrBracketConfig.adaptiveEvs(histogram(clipped = 0.1f, dark = 0.6f)))
        assertEquals(-1.5f to 2f, HdrBracketConfig.adaptiveEvs(histogram(clipped = 0.01f, dark = 0.1f)))
    }

    @Test
    fun longFrameUsesShutterBeforeIsoWhenSteady() {
        // Base ISO 286 at 1/50 s, +3 EV, steady enough for 1/4 s: all of it from the shutter.
        val (iso, shutterNs) = HdrBracketConfig.planManualExposure(
            baseIso = 286, baseShutterNs = 20_000_000L, evOffset = 3f,
            isoLower = 50, isoUpper = 3200, shutterLowerNs = 100_000L, shutterUpperNs = 1_000_000_000L,
            longShutterLimitNs = 250_000_000L,
        )
        assertEquals(286, iso)
        assertEquals(160_000_000L, shutterNs)
    }
}

