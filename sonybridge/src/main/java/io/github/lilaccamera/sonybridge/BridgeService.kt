package io.github.lilaccamera.sonybridge

import android.app.Service
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Process
import android.util.Log
import android.view.Surface
import com.sonymobile.imageprocessor.bypasscamera2.BypassCamera
import java.util.concurrent.ConcurrentHashMap

/**
 * The privileged half of Lilac's Sony video features (see [ISonyBridge]). For 960 fps slow motion it owns Sony's native
 * session, the H.264 encoder and the muxer; for the other video modes it owns the reader Sony's service draws into
 * and hands each frame to Lilac. Both live here because the photo service may only call back into a privileged process.
 * Only Lilac may use it: the caller must be a Lilac package signed with a pinned certificate (see [LilacTrust]).
 */
class BridgeService : Service() {
    private val lock = Any()
    private val camera = BypassCamera()
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var drain: Thread? = null
    private var out: ParcelFileDescriptor? = null
    private var videoW = 0
    private var videoH = 0
    private var fps = 960
    @Volatile private var frames = 0
    private val times = ArrayList<Long>() // pairs: file time stamp in microseconds, arrival in elapsed-realtime nanoseconds

    // Video-mode frame hand-over (interface version 3).
    private var frameReader: ImageReader? = null
    private var frameThread: HandlerThread? = null
    @Volatile private var frameSink: IFrameSink? = null
    private val heldFrames = ConcurrentHashMap<Int, Image>()
    private var nextFrameId = 0
    private var framesSent = 0
    private var framesDropped = 0

    private fun log(message: String) = Log.i(TAG, message)

    /** Reader at the video size; each frame goes to the sink and stays open until Lilac releases it. */
    private fun startFrameReader(sink: IFrameSink): Surface {
        framesSent = 0; framesDropped = 0
        val thread = HandlerThread("bridge-frames").apply { start() }.also { frameThread = it }
        val reader = ImageReader.newInstance(videoW, videoH, ImageFormat.PRIVATE, READER_IMAGES,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_VIDEO_ENCODE).also { frameReader = it }
        frameSink = sink
        reader.setOnImageAvailableListener({ r ->
            while (true) {
                val image = try { r.acquireNextImage() } catch (_: IllegalStateException) { null } ?: break
                val target = frameSink
                if (target == null || heldFrames.size >= MAX_FRAMES_OUT) { image.close(); framesDropped++; continue }
                val id = ++nextFrameId
                heldFrames[id] = image
                var buffer: HardwareBuffer? = null
                try {
                    buffer = image.hardwareBuffer
                    target.onFrame(buffer, image.timestamp, id)
                    framesSent++
                } catch (e: Exception) {
                    heldFrames.remove(id)?.close(); framesDropped++
                } finally {
                    buffer?.close()
                }
            }
        }, Handler(thread.looper))
        return reader.surface
    }

    /** Sony's stop, then the frames still out get up to a second to come back before everything is closed. */
    private fun closeFrames(): Int {
        val reader = frameReader ?: return 0
        val result = camera.stopVideoAndAwait()
        val deadline = SystemClock.elapsedRealtime() + 1000
        while (heldFrames.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(10)
        frameSink = null
        frameThread?.quitSafely()
        frameThread = null
        heldFrames.values.forEach { runCatching { it.close() } }
        heldFrames.clear()
        reader.close()
        frameReader = null
        log("frames stopped: sent=$framesSent dropped=$framesDropped")
        return result
    }

    private fun enforceCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val ok = packageManager.getPackagesForUid(uid).orEmpty().any { LilacTrust.isTrusted(this, it) }
        if (!ok) throw SecurityException("uid $uid is not allowed to use the Sony bridge")
    }

    /** Runs one call on the caller's thread under the service lock; a failure is logged and returned as -99. */
    private fun call(name: String, block: () -> Int): Int {
        enforceCaller()
        val result = try {
            synchronized(lock, block)
        } catch (t: Throwable) {
            log("$name failed: $t")
            -99
        }
        log("$name -> $result")
        return result
    }

    private fun startEncoder(file: ParcelFileDescriptor): Surface {
        out = file
        frames = 0
        synchronized(times) { times.clear() }
        val format = MediaFormat.createVideoFormat("video/avc", videoW, videoH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 40_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
        }
        val enc = MediaCodec.createEncoderByType("video/avc").also { codec = it }
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val mux = MediaMuxer(file.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
        drain = Thread {
            var track = -1
            val info = MediaCodec.BufferInfo()
            while (true) {
                val i = try { enc.dequeueOutputBuffer(info, 20_000) } catch (_: IllegalStateException) { return@Thread }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = mux.addTrack(enc.outputFormat); mux.start()
                } else if (i >= 0) {
                    val data = enc.getOutputBuffer(i)
                    if (data != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && track >= 0) {
                        mux.writeSampleData(track, data, info); frames++
                        synchronized(times) { times.add(info.presentationTimeUs); times.add(SystemClock.elapsedRealtimeNanos()) }
                    }
                    enc.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return@Thread
                }
            }
        }.also { it.start() }
        return surface
    }

    private fun finishEncoder() {
        runCatching { codec?.signalEndOfInputStream() }
        runCatching { drain?.join(5000) }
        runCatching { muxer?.stop(); muxer?.release() }
        runCatching { codec?.stop(); codec?.release() }
        runCatching { out?.close() }
        codec = null; muxer = null; drain = null; out = null
    }

    private val binder = object : ISonyBridge.Stub() {
        override fun version() = INTERFACE_VERSION

        override fun getCapabilities(): Bundle {
            enforceCaller()
            val caps = synchronized(lock) { runCatching { camera.capabilities(0) }.getOrNull() }
            log("capabilities: $caps")
            return Bundle().apply {
                putBoolean("superSlow", caps != null && caps.superSlowInfo.isNotEmpty() && caps.superSlowFrameNum.isNotEmpty())
                putStringArrayList("info", ArrayList(caps?.superSlowInfo.orEmpty()))
                putIntegerArrayList("frameNums", ArrayList(caps?.superSlowFrameNum.orEmpty()))
                putStringArrayList("highFps", ArrayList(caps?.highFrameRateInfo.orEmpty()))
                putStringArrayList("steadyShot", ArrayList(caps?.steadyShotInfo.orEmpty()))
                putStringArrayList("intelligentActive", ArrayList(caps?.intelligentActiveInfo.orEmpty()))
            }
        }

        override fun open(camera: Int) = call("open") { this@BridgeService.camera.open(camera, this@BridgeService::log) }

        override fun enterSuperSlow(videoW: Int, videoH: Int, fps: Int, frames: Int) = call("enterSuperSlow") {
            this@BridgeService.videoW = videoW; this@BridgeService.videoH = videoH; this@BridgeService.fps = fps
            camera.enterSuperSlow(videoW, videoH, fps, frames)
        }

        override fun prepare(file: ParcelFileDescriptor) = call("prepare") { camera.prepare(startEncoder(file)) }

        override fun startVideo() = call("startVideo") { camera.startVideo() }

        override fun trigger() = call("trigger") { camera.trigger() }

        // Not under the lock: the caller polls it while the burst arrives.
        override fun frameCount(): Int { enforceCaller(); return frames }

        override fun frameTimes(): LongArray { enforceCaller(); return synchronized(times) { times.toLongArray() } }

        // Sony's order: wait, end the encoder and close the file, then stop the native recording.
        override fun stop() = call("stop") { Thread.sleep(500); finishEncoder(); camera.stopVideo() }

        override fun release() = call("release") { closeFrames(); finishEncoder(); camera.release() }

        override fun enterVideoMode(mode: Int, previewW: Int, previewH: Int, videoW: Int, videoH: Int, fps: Int) =
            call("enterVideoMode($mode, ${previewW}x$previewH, ${videoW}x$videoH, $fps)") {
                closeFrames()
                this@BridgeService.videoW = videoW; this@BridgeService.videoH = videoH; this@BridgeService.fps = fps
                camera.enterVideo(mode, previewW, previewH, videoW, videoH, fps)
            }

        override fun startFrames(sink: IFrameSink) = call("startFrames") {
            closeFrames()
            val prepared = camera.prepareVideo(startFrameReader(sink))
            if (prepared != 0) { closeFrames(); return@call prepared }
            camera.startVideoAndAwait()
        }

        // One way and not under the lock: it arrives while stopFrames waits for the frames still out.
        override fun releaseFrame(frameId: Int) {
            enforceCaller()
            heldFrames.remove(frameId)?.close()
        }

        override fun stopFrames() = call("stopFrames") { closeFrames() }
    }

    override fun onBind(intent: Intent): IBinder {
        runCatching { CaptureForwardActivity.refresh(this) }.onFailure { log("forwarder check failed: $it") }
        return binder
    }

    // The caller is gone or let go: never leave Sony's session open.
    override fun onUnbind(intent: Intent): Boolean {
        runCatching { synchronized(lock) { closeFrames(); finishEncoder(); camera.release() } }
        log("client unbound, session released")
        return false
    }

    companion object {
        private const val TAG = "LilacSonyBridge"
        /** [ISonyBridge.version]; raise it with every new call. */
        const val INTERFACE_VERSION = 3
        /** Reader depth for the frame hand-over; frames beyond [MAX_FRAMES_OUT] held by Lilac are dropped. */
        private const val READER_IMAGES = 6
        private const val MAX_FRAMES_OUT = 4
    }
}
