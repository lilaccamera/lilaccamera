package com.hinnka.mycamera.ui.gallery

import com.hinnka.mycamera.ui.components.lilacRulerLabelEvery
import com.hinnka.mycamera.ui.components.lilacRulerStepDp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The photo sizes on a 360 × 640 dp screen, and the ruler spacing, from the editor's design table. */
class EditorPhotoRectTest {
    private fun rect(aspect: Float, full: Boolean = false) = editorPhotoRect(aspect, 360f, 640f, full)

    private fun assertRect(expected: EditorRect, actual: EditorRect) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
        assertEquals(expected.w, actual.w, 0.5f)
        assertEquals(expected.h, actual.h, 0.5f)
    }

    @Test
    fun portraitPhotosFillTheAreaAboveTheBlock() {
        assertRect(EditorRect(0f, 0f, 360f, 480f), rect(3f / 4f))
        assertRect(EditorRect(20f, 0f, 320f, 480f), rect(2f / 3f))
        assertRect(EditorRect(45f, 0f, 270f, 480f), rect(9f / 16f))
    }

    @Test
    fun shortPhotosCentreBelowTheEdgeIcons() {
        assertRect(EditorRect(0f, 80f, 360f, 360f), rect(1f))
        assertRect(EditorRect(0f, 125f, 360f, 270f), rect(4f / 3f))
        assertRect(EditorRect(0f, 158.75f, 360f, 202.5f), rect(16f / 9f))
        assertRect(EditorRect(0f, 193.5f, 360f, 133f), rect(65f / 24f))
    }

    @Test
    fun fullViewUsesTheWholeScreen() {
        assertRect(EditorRect(0f, 80f, 360f, 480f), rect(3f / 4f, full = true))
        assertRect(EditorRect(0f, 0f, 360f, 640f), rect(9f / 16f, full = true))
        assertRect(EditorRect(0f, 140f, 360f, 360f), rect(1f, full = true))
    }

    @Test
    fun rulerSpacingAndLabels() {
        assertEquals(26f, lilacRulerStepDp(12, stops = true))
        assertEquals(15f, lilacRulerStepDp(41, stops = false))  // Intensity, 0-200 % in 5 % steps
        assertEquals(24f, lilacRulerStepDp(13, stops = false))  // Exposure in 1/3 stops
        assertEquals(6f, lilacRulerStepDp(181, stops = false))  // Straighten in 1/2 degrees
        assertEquals(4, lilacRulerLabelEvery(15f))
        assertEquals(2, lilacRulerLabelEvery(24f))
        assertEquals(10, lilacRulerLabelEvery(6f))
    }
}
