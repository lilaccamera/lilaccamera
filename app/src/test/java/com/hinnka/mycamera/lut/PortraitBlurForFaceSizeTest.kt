package com.hinnka.mycamera.lut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitBlurForFaceSizeTest {
    @Test
    fun distantFacesGetNoBlurAndCloseFacesFullBlur() {
        assertEquals(0f, portraitBlurForFaceSize(0.05f))
        assertEquals(0f, portraitBlurForFaceSize(0.10f))
        assertEquals(0.5f, portraitBlurForFaceSize(0.175f), 1e-4f)
        assertEquals(1f, portraitBlurForFaceSize(0.25f))
        assertEquals(1f, portraitBlurForFaceSize(0.6f))
    }

    @Test
    fun blurGrowsWithFaceSize() {
        var previous = 0f
        for (step in 0..60) {
            val blur = portraitBlurForFaceSize(step / 100f)
            assertTrue(blur >= previous)
            previous = blur
        }
    }
}
