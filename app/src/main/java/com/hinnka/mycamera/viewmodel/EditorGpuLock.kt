package com.hinnka.mycamera.viewmodel

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

/**
 * The photo editor's GPU work, one job at a time: the Adreno 540 faults ("gpu fault", then every later render
 * fails) when two GL jobs run together. A light render (the preview the screen waits for) goes before a heavy
 * job (a blur, AI denoise, noise reduction) that is waiting too; a heavy job that has started still finishes, as
 * GPU work can't stop halfway.
 */
internal class EditorGpuLock {
    @PublishedApi internal val mutex = Mutex()
    @PublishedApi internal val lightWaiting = AtomicInteger(0)

    suspend inline fun <T> light(block: () -> T): T {
        lightWaiting.incrementAndGet()
        try {
            mutex.lock()
        } finally {
            lightWaiting.decrementAndGet()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    suspend inline fun <T> heavy(block: () -> T): T {
        while (true) {
            while (lightWaiting.get() > 0) delay(16)
            mutex.lock()
            if (lightWaiting.get() == 0) break
            mutex.unlock()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
