package com.hinnka.mycamera.camera

/**
 * Sensor capture only; image delivery, merging and saving are not part of this progress.
 * [estimatedEndElapsedNs] is when the last frame's result should arrive (elapsedRealtime clock), refined at every
 * frame; once [lastFrameArrived], the ring closes.
 */
data class PhotoCaptureProgress(
    val captureId: Long,
    val estimatedEndElapsedNs: Long,
    val lastFrameArrived: Boolean = false,
)
