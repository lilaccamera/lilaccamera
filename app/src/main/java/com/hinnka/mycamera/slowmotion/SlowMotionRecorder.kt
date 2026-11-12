package com.hinnka.mycamera.slowmotion

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.video.VideoMediaStoreWriter
import com.hinnka.mycamera.video.VideoRecordingPath
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * One slow-motion recording: ordinary video, a 960 fps burst, ordinary video, with sound. Runs Sony's call sequence
 * through the companion's [com.mdomokos.lilaccamera.sonybridge.ISonyBridge] in the exact order the service needs,
 * with a timeout on every step. The caller owns the Camera2 session ([SlowMotionSpec] says what shape); this class
 * tells it when to open it ([Listener.onEntered]) and wants to hear when its viewfinder has settled ([startRecording]).
 *
 * Order: [enter] (open, mode change), the caller opens its camera session and waits [SlowMotionSpec.VIEWFINDER_SETTLE_MS],
 * [startRecording] (encoder, ordinary video), [trigger] (the burst), [stop]. Every step runs on this recorder's own
 * thread and reports through the [Listener] (called on that thread). After an error or [stop] the recorder is finished.
 */
class SlowMotionRecorder(
    context: Context,
    private val spec: SlowMotionSpec,
    private val recordingPath: VideoRecordingPath,
    private val recordingTreeUri: String?,
    private val channels: Int,
    private val orientationDegrees: Int,
    private val listener: Listener,
) {
    interface Listener {
        /** The mode change is done: open the camera session now, in the shape [SlowMotionSpec] describes. */
        fun onEntered()

        /** Ordinary video is being recorded; the trigger may be pressed. */
        fun onRecording()

        /** The burst's frames have arrived (false: not all of them within the timeout). */
        fun onBurstDone(arrived: Boolean)

        /** The recording is saved, or null when it could not be. */
        fun onFinished(uri: Uri?)

        /** Fatal: the recorder has released the service. [onFinished] does not follow. */
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val thread = HandlerThread("slow-motion").apply { start() }
    private val handler = Handler(thread.looper)
    private val client = SonyBridgeClient(appContext) { post { fail("The slow-motion service stopped") } }
    private val dir = File(appContext.cacheDir, "slowmotion").apply { mkdirs() }
    private val videoFile = File(dir, "video.mp4")
    private val audio = SlowMotionAudio(channels)
    private var state = State.NEW
    private var dateTakenMs = 0L
    private var hasSound = false
    private val triggerFrames = ArrayList<Int>() // frames the encoder had delivered when each burst was fired

    private enum class State { NEW, ENTERED, RECORDING, TRIGGERED, STOPPING, DONE }

    private fun post(block: () -> Unit) { if (thread.isAlive) handler.post(block) }

    /** Binds to the companion, opens Sony's session and changes to super slow motion. */
    fun enter() = post {
        if (state != State.NEW) return@post
        val bridge = if (client.connect()) client.bridge else null
        if (bridge == null) return@post fail("The slow-motion service is not available")
        if (bridge.open(0) != 0) return@post fail("Sony's photo service would not open")
        val began = SystemClock.elapsedRealtime()
        val result = bridge.enterSuperSlow(spec.width, spec.height, SlowMotionSpec.FPS, spec.frames)
        val took = SystemClock.elapsedRealtime() - began
        PLog.i(TAG, "Mode change: result=$result, ${took} ms")
        // A slow mode change means the service is about to abort the mode (it waited for a session that did not come).
        if (result != 0 || took > MODE_CHANGE_LIMIT_MS) return@post fail("Sony's photo service did not accept slow motion")
        state = State.ENTERED
        listener.onEntered()
    }

    /** Call once the camera session has run for [SlowMotionSpec.VIEWFINDER_SETTLE_MS]. */
    fun startRecording() = post {
        if (state != State.ENTERED) return@post
        val bridge = client.bridge ?: return@post fail("The slow-motion service stopped")
        dateTakenMs = System.currentTimeMillis()
        val file = ParcelFileDescriptor.open(
            videoFile,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        )
        val prepared = try { bridge.prepare(file) } finally { file.close() }
        if (prepared != 0) return@post fail("Sony's photo service did not take the video encoder")
        hasSound = audio.start()
        if (bridge.startVideo() != 0) return@post fail("Sony's photo service did not start recording")
        state = State.RECORDING
        listener.onRecording()
    }

    /** Fires a burst; only works while ordinary video is recording and no other burst is arriving. */
    fun trigger() = post {
        if (state != State.RECORDING || triggerFrames.size >= SlowMotionSpec.MAX_BURSTS) return@post
        val bridge = client.bridge ?: return@post fail("The slow-motion service stopped")
        val before = bridge.frameCount()
        val triggerNs = SystemClock.elapsedRealtimeNanos()
        if (bridge.trigger() != 0) return@post fail("Sony's photo service did not start the burst")
        triggerFrames += before
        state = State.TRIGGERED
        // The service reports no completion: the burst has arrived once the encoder has delivered its frames.
        var arrived = false
        while (!arrived && SystemClock.elapsedRealtimeNanos() - triggerNs < BURST_TIMEOUT_MS * 1_000_000L) {
            Thread.sleep(100)
            arrived = bridge.frameCount() >= before + spec.frames
        }
        PLog.i(TAG, "Burst ${triggerFrames.size}: arrived=$arrived after ${(SystemClock.elapsedRealtimeNanos() - triggerNs) / 1_000_000} ms, frames before=$before now=${bridge.frameCount()}")
        if (state == State.TRIGGERED) state = State.RECORDING
        listener.onBurstDone(arrived)
    }

    /** Ends the recording, lays the sound onto the video and saves it. */
    fun stop() = post {
        if (state != State.RECORDING && state != State.TRIGGERED) return@post
        state = State.STOPPING
        audio.stop()
        val bridge = client.bridge
        val stopped = bridge?.stop()
        val frameTimes = runCatching { bridge?.frameTimes() }.getOrNull() ?: LongArray(0)
        PLog.i(TAG, "Stop: result=$stopped, frames=${frameTimes.size / 2}")
        release()
        var uri: Uri? = null
        if (videoFile.length() > 0) {
            val finished = SlowMotionRemux.finish(
                videoFile, frameTimes, triggerFrames, spec.frames, audio.takeIf { hasSound }, orientationDegrees, File(dir, "final.mp4")
            )
            uri = runBlocking {
                VideoMediaStoreWriter.publishVideo(appContext, finished ?: videoFile, dateTakenMs, recordingPath, recordingTreeUri, "_960fps")
            }
        }
        cleanup()
        listener.onFinished(uri)
    }

    /** Gives everything back at once (camera closed, screen left). Safe at any time and more than once. */
    fun abort() = post {
        if (state == State.DONE) return@post
        audio.stop()
        release()
        cleanup()
    }

    private fun fail(message: String) {
        PLog.w(TAG, "Slow motion failed: $message")
        audio.stop()
        release()
        cleanup()
        listener.onError(message)
    }

    private fun release() {
        runCatching { client.bridge?.release() }
        client.disconnect()
    }

    private fun cleanup() {
        state = State.DONE
        dir.listFiles()?.forEach { it.delete() }
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "SlowMotionRecorder"
        const val MODE_CHANGE_LIMIT_MS = 2_000L
        const val BURST_TIMEOUT_MS = 8_000
    }
}
