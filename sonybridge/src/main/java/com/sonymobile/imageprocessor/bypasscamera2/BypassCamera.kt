package com.sonymobile.imageprocessor.bypasscamera2

import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The class names, native method names and signatures below must match what the ROM's libimageprocessorjni.so
// exports: JNI binds by name. They mirror names only; no Sony code is included, the ROM's own libraries are loaded.
class BypassCameraParameters {
    class Capability {
        val burst = mutableListOf<Int>()
        val climax = mutableListOf<Int>()
        val superSlow = mutableListOf<Int>()
        val videoHdr = mutableListOf<Int>()
        val videoStabilization = mutableListOf<Int>()
        val superSlowInfo = mutableListOf<String>()
        val superSlowFrameNum = mutableListOf<Int>()
        val otherInfo = mutableListOf<String>()

        fun setBurstMode(v: Int) { burst += v }
        fun setClimaxRecognitionMode(v: Int) { climax += v }
        fun setSuperSlowMode(v: Int) { superSlow += v }
        fun setVideoHdrMode(v: Int) { videoHdr += v }
        fun setVideoStabilizationMode(v: Int) { videoStabilization += v }
        fun addSuperSlowSupportedInfo(a: Int, b: Int, c: Int) { superSlowInfo += "$a/$b/$c" }
        fun addSuperSlowFrameNum(v: Int) { superSlowFrameNum += v }
        fun addHighFrameRateSupportedInfo(a: Int, b: Int, c: Int) { otherInfo += "highFps $a/$b/$c" }
        fun addIntelligentActiveSupportedInfo(a: Int, b: Int, c: Int) { otherInfo += "intelligentActive $a/$b/$c" }
        fun addSteadyShotSupportedInfo(a: Int, b: Int, c: Int) { otherInfo += "steadyShot $a/$b/$c" }

        override fun toString() = "burst=$burst climax=$climax superSlow=$superSlow superSlowInfo=$superSlowInfo " +
            "superSlowFrameNum=$superSlowFrameNum videoHdr=$videoHdr stabilization=$videoStabilization other=$otherInfo"
    }
}

class BypassCamera {
    companion object {
        init {
            System.load("/system/lib64/libcacao_client.so")
            System.load("/system/lib64/libimageprocessorjni.so")
        }
    }

    private external fun nativeIsDebugable(): Boolean
    private external fun nativeInitialize(facing: Int): Long
    private external fun nativeFinalize(handle: Long): Int
    private external fun nativeGetCaps(camera: Int, caps: BypassCameraParameters.Capability): Int

    @Suppress("unused")
    private fun callbackFromNative(a: Int, b: Int, c: Int, d: Boolean, e: Boolean, f: Boolean, g: Int, h: Int, i: Int) = Unit

    private external fun nativeChangeToSuperSlowMode(
        handle: Long, mode: Int, previewW: Int, previewH: Int, videoW: Int, videoH: Int, frameRate: Int, fps: Int, frameNum: Int
    ): Int
    private external fun nativeRequestPrepareSuperSlowRecording(handle: Long, surface: Surface, colorStandard: Int, colorTransfer: Int, colorRange: Int): Int
    private external fun nativeRequestStartSuperSlowRecording(handle: Long): Int
    private external fun nativeRequestStartVideoRecording(handle: Long): Int
    private external fun nativeRequestStopVideoRecording(handle: Long): Int

    // Callback types seen so far: 4 right after prepare, 0 after the video start, 3 and 1 at the stop; none for the trigger.
    @Volatile private var prepareLatch = CountDownLatch(1)
    @Volatile private var logger: (String) -> Unit = {}

    @Suppress("unused")
    private fun callbackFromNativeVideo(type: Int) {
        logger("native callback type=$type")
        if (type == 4) prepareLatch.countDown()
    }

    private var handle = -1L

    fun capabilities(camera: Int): BypassCameraParameters.Capability? {
        val caps = BypassCameraParameters.Capability()
        return if (nativeGetCaps(camera, caps) == 0) caps else null
    }

    fun open(camera: Int, log: (String) -> Unit): Int {
        logger = log
        release()
        handle = nativeInitialize(camera)
        return if (handle == -1L || handle == 0L) { handle = -1L; -1 } else 0
    }

    /** Mode 0 is SUPER_SLOW_MOTION, the only mode used: mode 1 (SUPER_SLOW_SHOT) leaves the service wedged. */
    fun enterSuperSlow(videoW: Int, videoH: Int, fps: Int, frames: Int): Int =
        nativeChangeToSuperSlowMode(handle, 0, 1280, 720, videoW, videoH, 30, fps, frames)

    fun prepare(surface: Surface): Int {
        prepareLatch = CountDownLatch(1)
        val result = nativeRequestPrepareSuperSlowRecording(handle, surface, 0, 0, 0)
        logger("prepare=$result callback=${prepareLatch.await(3, TimeUnit.SECONDS)}")
        return result
    }

    fun startVideo(): Int = nativeRequestStartVideoRecording(handle)

    fun trigger(): Int = nativeRequestStartSuperSlowRecording(handle)

    fun stopVideo(): Int = nativeRequestStopVideoRecording(handle)

    fun release(): Int {
        val h = handle
        handle = -1L
        return if (h == -1L || h == 0L) 0 else nativeFinalize(h)
    }
}
