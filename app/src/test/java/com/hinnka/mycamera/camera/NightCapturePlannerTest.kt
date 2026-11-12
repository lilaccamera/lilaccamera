package com.hinnka.mycamera.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class NightCapturePlannerTest {
    @Test
    fun autoTakesGainSquaredFramesWithinItsLimits() {
        assertEquals(9, NightCapturePlanner.autoFrames(2.9f))   // 1 lux handheld: ISO 800 at 1/15 s needs 2.9x
        assertEquals(6, NightCapturePlanner.autoFrames(0.8f))   // bright enough: the fewest Auto takes
        assertEquals(NightCapturePlanner.AUTO_FRAMES, NightCapturePlanner.autoFrames(5f))
    }
}
