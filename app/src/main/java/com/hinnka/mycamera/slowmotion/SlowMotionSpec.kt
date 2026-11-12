package com.hinnka.mycamera.slowmotion

import kotlin.math.ceil

/** One slow-motion recording: the video size and the burst length (91 frames is 0.095 s, 177 frames 0.18 s). */
data class SlowMotionSpec(val width: Int = 1280, val height: Int = 720, val frames: Int = 91) {
    /** Exposure cap for the capture rate (Sony's own value): each frame must fit in one 960th of a second. */
    val exposureLimitNs: Long get() = ceil(1e9 / FPS).toLong()

    companion object {
        const val FPS = 960

        /** Bursts fired in one recording. */
        const val MAX_BURSTS = 3

        /** Camera2 session shape the service accepts: always this viewfinder and this reader, whatever the video size. */
        const val VIEWFINDER_WIDTH = 1280
        const val VIEWFINDER_HEIGHT = 720

        /** Vendor session mode of Sony's own camera app. */
        const val SESSION_MODE = 0x8000

        /** The service waits for the viewfinder to settle; Sony's app does too. */
        const val VIEWFINDER_SETTLE_MS = 1_500L

        const val EXPOSURE_LIMIT_KEY = "com.sonymobile.control.exposureTimeLimit"
    }
}
