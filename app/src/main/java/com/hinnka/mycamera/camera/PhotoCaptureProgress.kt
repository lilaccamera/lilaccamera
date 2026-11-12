package com.hinnka.mycamera.camera

/**
 * Sensor capture only; image delivery, merging and saving are not part of this progress.
 * The ring runs from [startFraction] at [startedElapsedNs] to the end over [estimatedDurationNs], and stops at
 * [maxFraction] until the next frame starts.
 */
data class PhotoCaptureProgress(
    val captureId: Long,
    val startedElapsedNs: Long,
    val estimatedDurationNs: Long,
    val startFraction: Float = 0f,
    val maxFraction: Float = 1f,
) {
    fun fractionAt(elapsedRealtimeNs: Long): Float {
        val elapsedNs = (elapsedRealtimeNs - startedElapsedNs).coerceAtLeast(0L)
        val run = (elapsedNs.toDouble() / estimatedDurationNs.coerceAtLeast(1L)).coerceIn(0.0, 1.0).toFloat()
        return minOf(startFraction + (1f - startFraction) * run, maxFraction)
    }
}
