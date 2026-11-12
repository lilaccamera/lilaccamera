package com.mdomokos.lilaccamera.sonybridge

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Process
import android.util.Log
import android.view.Surface
import com.sonymobile.imageprocessor.bypasscamera2.BypassCamera

/**
 * The privileged half of Lilac's 960 fps slow motion (see [ISonyBridge]). It owns Sony's native session, the H.264
 * encoder and the muxer; the encoder lives here because the photo service may only call back into a privileged process.
 * Only Lilac may use it: the caller must be a Lilac package signed with the same key as this app.
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
    private var encoderSurface: Surface? = null
    @Volatile private var frames = 0
    private val times = ArrayList<Long>() // pairs: file time stamp in microseconds, arrival in elapsed-realtime nanoseconds

    private fun log(message: String) = Log.i(TAG, message)

    private fun enforceCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val names = packageManager.getPackagesForUid(uid)
        val ok = names != null && names.any { it == LILAC || it.startsWith("$LILAC.") } &&
            packageManager.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH
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
        val surface = enc.createInputSurface().also { encoderSurface = it }
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
        override fun version() = 1

        override fun getCapabilities(): Bundle {
            enforceCaller()
            val caps = synchronized(lock) { runCatching { camera.capabilities(0) }.getOrNull() }
            log("capabilities: $caps")
            return Bundle().apply {
                putBoolean("superSlow", caps != null && caps.superSlowInfo.isNotEmpty() && caps.superSlowFrameNum.isNotEmpty())
                putStringArrayList("info", ArrayList(caps?.superSlowInfo.orEmpty()))
                putIntegerArrayList("frameNums", ArrayList(caps?.superSlowFrameNum.orEmpty()))
            }
        }

        override fun open(camera: Int) = call("open") { this@BridgeService.camera.open(camera, this@BridgeService::log) }

        override fun enterSuperSlow(videoW: Int, videoH: Int, fps: Int, frames: Int) = call("enterSuperSlow") {
            this@BridgeService.videoW = videoW; this@BridgeService.videoH = videoH; this@BridgeService.fps = fps
            camera.enterSuperSlow(videoW, videoH, fps, frames)
        }

        override fun prepare(file: ParcelFileDescriptor) = call("prepare") { camera.prepare(startEncoder(file)) }

        override fun reprepare() = call("reprepare") { camera.prepare(encoderSurface ?: error("no encoder")) }

        override fun startVideo() = call("startVideo") { camera.startVideo() }

        override fun trigger() = call("trigger") { camera.trigger() }

        // Not under the lock: the caller polls it while the burst arrives.
        override fun frameCount(): Int { enforceCaller(); return frames }

        override fun frameTimes(): LongArray { enforceCaller(); return synchronized(times) { times.toLongArray() } }

        // Sony's order: wait, end the encoder and close the file, then stop the native recording.
        override fun stop() = call("stop") { Thread.sleep(500); finishEncoder(); camera.stopVideo() }

        override fun release() = call("release") { finishEncoder(); camera.release() }
    }

    override fun onBind(intent: Intent): IBinder = binder

    // The caller is gone or let go: never leave Sony's session open.
    override fun onUnbind(intent: Intent): Boolean {
        runCatching { synchronized(lock) { finishEncoder(); camera.release() } }
        log("client unbound, session released")
        return false
    }

    private companion object {
        const val TAG = "LilacSonyBridge"
        const val LILAC = "com.mdomokos.lilaccamera"
    }
}
