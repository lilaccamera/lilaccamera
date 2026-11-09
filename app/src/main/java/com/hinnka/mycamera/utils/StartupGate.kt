package com.hinnka.mycamera.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds back start-up work the viewfinder does not need (gallery list, index recovery, warm-ups)
 * until the camera preview is running, so it does not compete with opening the camera.
 */
object StartupGate {
    // Fallback for starts where no preview comes (permission screen, camera error).
    private const val TIMEOUT_MS = 3_000L
    private val opened = CompletableDeferred<Unit>()

    fun open() {
        opened.complete(Unit)
    }

    suspend fun await() {
        withTimeoutOrNull(TIMEOUT_MS) { opened.await() }
    }
}
